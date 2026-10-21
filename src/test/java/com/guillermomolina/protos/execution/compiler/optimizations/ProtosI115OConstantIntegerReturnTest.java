/*
 * THE LICENSED WORK IS PROVIDED UNDER THE TERMS OF THE ADAPTIVE PUBLIC LICENSE
 * ("LICENSE") AS FIRST COMPLETED BY: Guillermo Adrián Molina. ANY USE, PUBLIC
 * DISPLAY, PUBLIC PERFORMANCE, REPRODUCTION OR DISTRIBUTION OF, OR PREPARATION OF
 * DERIVATIVE WORKS BASED ON, THE LICENSED WORK CONSTITUTES RECIPIENT'S ACCEPTANCE
 * OF THIS LICENSE AND ITS TERMS, WHETHER OR NOT SUCH RECIPIENT READS THE TERMS OF
 * THE LICENSE. "LICENSED WORK" AND "RECIPIENT" ARE DEFINED IN THE LICENSE. A COPY
 * OF THE LICENSE IS LOCATED IN THE TEXT FILE ENTITLED "LICENSE.TXT" ACCOMPANYING
 * THE CONTENTS OF THIS FILE. IF A COPY OF THE LICENSE DOES NOT ACCOMPANY THIS
 * FILE, A COPY OF THE LICENSE MAY ALSO BE OBTAINED AT THE FOLLOWING WEB SITE:
 * https://github.com/guillermomolina/protos
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License for
 * the specific language governing rights and limitations under the License.
 */
package com.guillermomolina.protos.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.runtime.ProtosIntegerValue;
import com.oracle.truffle.api.bytecode.BytecodeRootNodes;
import com.oracle.truffle.api.bytecode.BytecodeTier;
import com.oracle.truffle.api.bytecode.Instruction;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.Test;

/**
 * I115-O (EXPERIMENTAL, PLAT066-C / PERF041): a compact root returning a
 * binding whose every store is an Integer, null or Boolean literal stores
 * prepared guest Integer constants and skips the return-carrier projection.
 * Unadmitted roots keep their previous lowering.
 */
final class ProtosI115OConstantIntegerReturnTest {

    private static final Path CORE = Path.of("protos", "lib", "core");

    private static final Path OUTPUT =
            Path.of("target", "perf039-while-once", "i115-o");

    private static final String PERF041V = """
            run: () => {
                value: true
                result: null
                value.ifTrue() {
                    result = 1
                }
                result
            }
            """;

    /* Constant census of the run root's real CONSTANT arguments. */
    private record Census(
            String name,
            int rootCount,
            int runFull,
            int moduleFull,
            List<Long> guestIntegers,
            List<Long> carrierIntegers,
            boolean compact,
            boolean projectsBefore,
            boolean projectsAfter) {

        boolean admitted() {
            return !guestIntegers.isEmpty();
        }
    }

    @Test
    void perf041PrimaryCase() throws Exception {
        try (ProtosStandaloneHostedSession session = openSession("perf041v", PERF041V)) {
            ProtosStandaloneHostedSession.PreparedTopLevel prepared =
                    session.prepareTopLevel("run");
            ProtosBytecodeRootNode run = selectedRoot(prepared);
            Census before = census("perf041v", run);
            assertEquals(2, before.rootCount());
            assertEquals(13, before.runFull());
            assertEquals(11, before.moduleFull());
            assertEquals(24, before.runFull() + before.moduleFull());
            assertEquals(List.of(1L), before.guestIntegers());
            assertTrue(before.carrierIntegers().isEmpty());
            assertTrue(before.admitted());
            assertNoReturnProjection(before);
            assertFalse(run.epilogProjectsReturnCarrier());
            assertTrue(guestStoreConstants(run) >= 1,
                    "The guest Integer must feed a store, not a return materialization");

            for (int call = 0; call < 16; call++) {
                Value result = prepared.executable().execute();
                assertTrue(result.fitsInLong());
                assertEquals(1L, result.asLong());
            }
            // The representation does not depend on the invocation count.
            Census after = census("perf041v-after-calls", run);
            assertEquals(before.guestIntegers(), after.guestIntegers());
            assertEquals(before.runFull(), after.runFull());
            report(before, true);
        }
    }

    @Test
    void integerVariants() throws Exception {
        for (String literal : List.of(
                "0", "1", "-1", "42", "255", "256",
                "4294967297", "9223372036854775807")) {
            long expected = Long.parseLong(literal);
            Census census = runCase("variant" + literal, """
                    run: () => {
                        value: true
                        result: null
                        value.ifTrue() {
                            result = %s
                        }
                        result
                    }
                    """.formatted(literal),
                    result -> assertEquals(expected, result.asLong()));
            if (literal.startsWith("-")) {
                // -1 is a negation send, outside the compact subset: the root
                // keeps the general lowerer, so only correctness is pinned.
                continue;
            }
            assertTrue(census.admitted(), literal);
            assertNoReturnProjection(census);
            assertEquals(List.of(expected), census.guestIntegers());
        }
    }

    @Test
    void sameIntegerInTwoIndependentStores() throws Exception {
        Census census = runCase("same-integer-twice", """
                run: () => {
                    r: 7
                    t: true
                    t.ifTrue() {
                        r = 7
                    }
                    r
                }
                """, result -> assertEquals(7, result.asInt()));
        assertTrue(census.admitted());
        assertNoReturnProjection(census);
        // Admission is per store provenance, one constant per store.
        assertEquals(List.of(7L, 7L), census.guestIntegers());
    }

    @Test
    void falseSkipsCallback() throws Exception {
        Census census = runCase("false-skips", """
                run: () => {
                    r: 1
                    off: false
                    off.ifTrue() {
                        r = 2
                    }
                    r
                }
                """, result -> assertEquals(1, result.asInt()));
        assertTrue(census.admitted());
        assertNoReturnProjection(census);
    }

    @Test
    void twoBranchesAssignDifferentIntegers() throws Exception {
        for (String flag : List.of("true", "false")) {
            int expected = flag.equals("true") ? 10 : 20;
            Census census = runCase("two-branches-" + flag, """
                    run: () => {
                        r: null
                        c: %s
                        c.ifTrueIfFalse(() => {
                            r = 10
                        }, () => {
                            r = 20
                        })
                        r
                    }
                    """.formatted(flag),
                    result -> assertEquals(expected, result.asInt()));
            assertTrue(census.admitted());
            assertNoReturnProjection(census);
            assertEquals(List.of(10L, 20L), census.guestIntegers());
        }
    }

    @Test
    void branchLeavesNull() throws Exception {
        Census census = runCase("branch-leaves-null", """
                run: () => {
                    r: null
                    c: false
                    c.ifTrue() {
                        r = 5
                    }
                    r
                }
                """, result -> assertTrue(result.isNull()));
        assertTrue(census.admitted());
        assertNoReturnProjection(census);
    }

    @Test
    void repeatedLiteralStores() throws Exception {
        Census census = runCase("repeated-stores", """
                run: () => {
                    r: 3
                    r = 3
                    r = 4
                    r
                }
                """, result -> assertEquals(4, result.asInt()));
        assertTrue(census.admitted());
        assertNoReturnProjection(census);
        assertEquals(List.of(3L, 3L, 4L), census.guestIntegers());
    }

    @Test
    void inlineCallbackStoreInsideLoop() throws Exception {
        Census census = runCase("callback-in-loop", """
                run: () => {
                    k: 0
                    r: null
                    (() => k < 3).whileTrue() {
                        (k < 2).ifTrue() {
                            r = 9
                        }
                        k = k + 1
                    }
                    r
                }
                """, result -> assertEquals(9, result.asInt()));
        assertTrue(census.admitted());
        assertNoReturnProjection(census);
        assertEquals(List.of(9L), census.guestIntegers());
    }

    @Test
    void computedReturnIsNotAdmitted() throws Exception {
        Census census = runCase("computed", """
                run: () => {
                    r: 1 + 2
                    r
                }
                """, result -> assertEquals(3, result.asInt()));
        assertNotAdmittedCompact(census);
    }

    @Test
    void literalThenDynamicIsNotAdmitted() throws Exception {
        Census census = runCase("literal-then-dynamic", """
                run: () => {
                    r: 5
                    t: true
                    t.ifTrue() {
                        r = 2 * 3
                    }
                    r
                }
                """, result -> assertEquals(6, result.asInt()));
        assertNotAdmittedCompact(census);
    }

    @Test
    void arithmeticOperandIsNotAdmitted() throws Exception {
        Census census = runCase("arithmetic-operand", """
                run: () => {
                    x: 4
                    y: x + 1
                    x
                }
                """, result -> assertEquals(4, result.asInt()));
        assertNotAdmittedCompact(census);
    }

    @Test
    void uncertifiedReturnIsNotAdmitted() throws Exception {
        Census census = runCase("uncertified-return", """
                run: () => {
                    r: 1
                    s: 2
                    r + s
                }
                """, result -> assertEquals(3, result.asInt()));
        assertNotAdmittedCompact(census);
    }

    @Test
    void valuePositionStoreIsNotAdmitted() throws Exception {
        Census census = runCase("value-position-store", """
                run: () => {
                    r: null
                    c: true
                    s: c.ifTrueIfFalse(() => r = 1, () => r = 2)
                    r
                }
                """, result -> assertEquals(1, result.asInt()));
        assertNotAdmittedCompact(census);
    }

    @Test
    void generalLoweringCasesKeepPreviousPath() throws Exception {
        // Parameters, Float, large Integer and non-inline writes are outside
        // the compact subset; only correctness is pinned.
        runGeneral("parameter", """
                run: (n) => {
                    r: 1
                    r
                }
                call: () => run(5)
                """, "call", 1, Value::asInt);
        runGeneral("float", """
                run: () => {
                    r: 1.5
                    r
                }
                """, "run", 1.5, Value::asDouble);
        runGeneral("large-integer", """
                run: () => {
                    r: 9223372036854775808
                    r
                }
                """, "run", "9223372036854775808", Value::toString);
        runGeneral("non-inline-write", """
                step: () => 1
                run: () => {
                    v: 0
                    go: true
                    go.ifTrue() {
                        v = step()
                    }
                    v
                }
                """, "run", 1, Value::asInt);
    }

    @Test
    void representationAcrossCallersAndContexts() throws Exception {
        String source = PERF041V + """
                viaClosure: () => run()
                plusOne: () => run() + 1
                equal: () => run() == 1
                same: () => run() === run()
                """;
        for (int contextIndex = 0; contextIndex < 2; contextIndex++) {
            try (ProtosStandaloneHostedSession session =
                    openSession("representation" + contextIndex, source)) {
                Value run = session.prepareTopLevel("run").executable();
                assertEquals(1L, run.execute().asLong());
                assertEquals(1L, run.execute().asLong());
                Value host = run.execute();
                assertTrue(host.isNumber());
                assertTrue(host.fitsInInt());
                assertEquals(1, host.asInt());
                assertEquals(1L, session.prepareTopLevel("viaClosure")
                        .executable().execute().asLong());
                assertEquals(2L, session.prepareTopLevel("plusOne")
                        .executable().execute().asLong());
                assertTrue(session.prepareTopLevel("equal")
                        .executable().execute().asBoolean());
                // Protos identity by value; Java identity is not required.
                assertTrue(session.prepareTopLevel("same")
                        .executable().execute().asBoolean());
            }
        }
        System.out.println("I115_O_CASE=representation");
        System.out.println("I115_O_CORRECTNESS=PASS");
    }

    @Test
    void reparseAndInterpreterTiers() throws Exception {
        try (ProtosStandaloneHostedSession session =
                openSession("reparse-tiers", PERF041V)) {
            ProtosStandaloneHostedSession.PreparedTopLevel prepared =
                    session.prepareTopLevel("run");
            ProtosBytecodeRootNode run = selectedRoot(prepared);
            List<Long> initial = guestIntegers(run);
            assertEquals(BytecodeTier.UNCACHED, run.getBytecodeNode().getTier());
            assertEquals(1L, prepared.executable().execute().asLong());

            // Rematerialize with source information (BytecodeConfig.DEFAULT tags).
            BytecodeRootNodes<?> nodes = run.getRootNodes();
            nodes.ensureSourceInformation();
            assertEquals(initial, guestIntegers(run));

            int calls = 0;
            while (calls < 128
                    && run.getBytecodeNode().getTier() != BytecodeTier.CACHED) {
                assertEquals(1L, prepared.executable().execute().asLong());
                calls++;
            }
            assertEquals(BytecodeTier.CACHED, run.getBytecodeNode().getTier());
            assertEquals(1L, prepared.executable().execute().asLong());
            assertEquals(initial, guestIntegers(run));
            System.out.println("I115_O_CASE=reparse-tiers");
            System.out.println("I115_O_CORRECTNESS=PASS");
        }
    }

    private static void assertNotAdmittedCompact(Census census) {
        assertTrue(census.compact(), census.name() + " must stay compact");
        assertTrue(census.guestIntegers().isEmpty(),
                census.name() + " must keep primitive Integer literals");
        assertFalse(census.carrierIntegers().isEmpty());
        // The I115-M/N compact path keeps projecting the return carrier.
        assertTrue(census.projectsBefore(), census.name());
        assertTrue(census.projectsAfter(), census.name());
    }

    /* I115-O: the epilog projection stays off across ensureSourceInformation. */
    private static void assertNoReturnProjection(Census census) {
        assertFalse(census.projectsBefore(), census.name());
        assertFalse(census.projectsAfter(), census.name());
    }

    private static <T> void runGeneral(
            String name,
            String source,
            String binding,
            T expected,
            java.util.function.Function<Value, T> read) throws Exception {
        try (ProtosStandaloneHostedSession session = openSession(name, source)) {
            assertEquals(expected,
                    read.apply(session.prepareTopLevel(binding).executable().execute()));
            System.out.println("I115_O_CASE=" + name);
            System.out.println("I115_O_CONSTANT_RETURN_ADMITTED=false");
            System.out.println("I115_O_CORRECTNESS=PASS");
        }
    }

    private static Census runCase(
            String name, String source, Consumer<Value> expected)
            throws Exception {
        try (ProtosStandaloneHostedSession session = openSession(name, source)) {
            ProtosStandaloneHostedSession.PreparedTopLevel prepared =
                    session.prepareTopLevel("run");
            Census census = census(name, selectedRoot(prepared));
            expected.accept(prepared.executable().execute());
            report(census, true);
            return census;
        }
    }

    private static void report(Census census, boolean correct) {
        System.out.println("I115_O_CASE=" + census.name());
        System.out.println("I115_O_ROOT_COUNT=" + census.rootCount());
        System.out.println("I115_O_RUN_DSL_FULL=" + census.runFull());
        System.out.println("I115_O_GUEST_INTEGER_CONSTANTS="
                + census.guestIntegers().size());
        System.out.println("I115_O_CARRIER_INTEGER_CONSTANTS="
                + census.carrierIntegers().size());
        System.out.println("I115_O_CONSTANT_RETURN_ADMITTED=" + census.admitted());
        if (correct) {
            System.out.println("I115_O_CORRECTNESS=PASS");
        }
    }

    private static ProtosStandaloneHostedSession openSession(
            String name, String source) throws IOException {
        Files.createDirectories(OUTPUT);
        Path sourceFile = OUTPUT.resolve(name + ".protos");
        Files.writeString(sourceFile, source, StandardCharsets.UTF_8);

        ProtosStandaloneHostedSession session =
                ProtosStandaloneHostedSession.open(CORE, sourceFile);
        if (session.initialOutcome().state()
                != ProtosExecutionOutcome.State.COMPLETED) {
            session.close();
            throw new AssertionError("Module initialization failed: " + name);
        }
        return session;
    }

    private static ProtosBytecodeRootNode selectedRoot(
            ProtosStandaloneHostedSession.PreparedTopLevel prepared) {
        return assertInstanceOf(ProtosBytecodeRootNode.class,
                prepared.hostExecutableForTesting().target.getRootNode());
    }

    private static Census census(String name, ProtosBytecodeRootNode run) {
        boolean projectsBefore = run.epilogProjectsReturnCarrier();
        // DEFAULT bytecode with source information, not instrumented DEBUG.
        run.getRootNodes().ensureSourceInformation();
        boolean projectsAfter = run.epilogProjectsReturnCarrier();
        List<ProtosBytecodeRootNode> roots =
                run.getRootNodes().getNodes().stream()
                        .map(ProtosBytecodeRootNode.class::cast)
                        .toList();
        int moduleFull = roots.get(0).getBytecodeNode().getInstructionsAsList().size();
        List<Instruction> instructions = run.getBytecodeNode().getInstructionsAsList();
        boolean compact = false;
        List<Long> carriers = new ArrayList<>();
        for (Instruction instruction : instructions) {
            compact |= ProtosInstructionNames.of(instruction).contains("Experimental");
            for (Object constant : constants(instruction)) {
                if (constant instanceof Long carrier) {
                    carriers.add(carrier);
                }
            }
        }
        List<Long> guests = guestIntegers(run);
        return new Census(name, roots.size(), instructions.size(), moduleFull,
                guests, carriers, compact, projectsBefore, projectsAfter);
    }

    private static List<Long> guestIntegers(ProtosBytecodeRootNode run) {
        List<Long> guests = new ArrayList<>();
        for (Instruction instruction : run.getBytecodeNode().getInstructionsAsList()) {
            if (!"load.constant".equals(ProtosInstructionNames.of(instruction))) {
                continue;
            }
            for (Object constant : constants(instruction)) {
                if (constant instanceof ProtosIntegerValue integer) {
                    guests.add(integer.longValue());
                }
            }
        }
        return guests;
    }

    /* Guest Integer constants loaded directly as the value of a store.local. */
    private static int guestStoreConstants(ProtosBytecodeRootNode run) {
        List<Instruction> instructions = run.getBytecodeNode().getInstructionsAsList();
        int count = 0;
        for (int index = 0; index + 1 < instructions.size(); index++) {
            boolean guest = constants(instructions.get(index)).stream()
                    .anyMatch(ProtosIntegerValue.class::isInstance);
            if (guest && ProtosInstructionNames.of(instructions.get(index + 1))
                    .startsWith("store.local")) {
                count++;
            }
        }
        return count;
    }

    private static List<Object> constants(Instruction instruction) {
        List<Object> constants = new ArrayList<>();
        for (Instruction.Argument argument : instruction.getArguments()) {
            if (argument.getKind() == Instruction.Argument.Kind.CONSTANT) {
                constants.add(argument.asConstant());
            }
        }
        return constants;
    }
}
