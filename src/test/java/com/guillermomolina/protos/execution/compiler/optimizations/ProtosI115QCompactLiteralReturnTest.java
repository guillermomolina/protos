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
 * I115-Q (EXPERIMENTAL, PLAT066-C / PERF039): a compact root whose terminal
 * is a signed-64 Integer literal returns a prepared guest constant and skips
 * the return-carrier projection; true/false literal conditions of structured
 * control are consumed directly as Java booleans. I115-H1-FIX: a numeric
 * send is the shared guarded numeric site; only roots without one are
 * free of ordinary send machinery. I115-H7: {@code +} and the {@code <}
 * ending a whileTrue condition are NumberAdd/NumberInPlaceAdd and Lt, whose
 * ordinary send route stays inside the node.
 */
final class ProtosI115QCompactLiteralReturnTest {

    private static final Path CORE = Path.of("protos", "lib", "core");

    private static final Path OUTPUT =
            Path.of("target", "perf039-while-once", "i115-q");

    private static final String BOOLEAN_CONDITION =
            "ExperimentalCanonicalBooleanCondition";

    private static final List<String> FORBIDDEN = List.of(
            "PrepareSendOne",
            "PreparedBooleanCall",
            "PrepareStructuredCall",
            "TryDirectSendOne",
            "IsDirectSendMiss",
            "EnterNestedStructuredDispatch");

    private static final String LITERAL_RETURN = """
            run: () => { 1 }
            """;

    /* Census of the run root taken with source information. */
    private record Census(
            String name,
            int rootCount,
            int runFull,
            int moduleFull,
            List<String> names,
            List<Long> guestIntegers,
            List<Long> carrierIntegers,
            List<Integer> intCarriers,
            boolean projectsBefore,
            boolean projectsAfter) {

        boolean has(String fragment) {
            return names.stream().anyMatch(name -> name.contains(fragment));
        }

        /* Literal-only compact roots may contain no Experimental operation. */
        boolean compact() {
            return has("Experimental");
        }
    }

    @Test
    void literalIntegerReturnIsGuestConstant() throws Exception {
        Census census = runCase("literal-return", LITERAL_RETURN,
                result -> assertEquals(1L, result.asLong()));
        assertEquals(2, census.rootCount());
        assertNoGenericDispatch(census);
        assertEquals(List.of(1L), census.guestIntegers());
        assertTrue(census.carrierIntegers().isEmpty(),
                "The terminal literal must not be a Long carrier");
        assertNoReturnProjection(census);
    }

    @Test
    void signed64IntegerVariants() throws Exception {
        for (String literal : List.of(
                "0", "1", "42", "4294967297", "9223372036854775807")) {
            long expected = Long.parseLong(literal);
            Census census = runCase("variant" + literal,
                    "run: () => { %s }\n".formatted(literal),
                    result -> assertEquals(expected, result.asLong()));
            assertEquals(List.of(expected), census.guestIntegers(), literal);
            assertTrue(census.carrierIntegers().isEmpty(), literal);
            assertNoReturnProjection(census);
        }
    }

    /*
     * I115-H7: a computed terminal is not a literal return. {@code 1 + 2} is
     * one NumberAdd over two int carriers (no long carrier, no prepared
     * guest Integer); the return epilogue projects the computed carrier, so
     * the host receives the guest Integer 3. The literal-return policy above
     * (prepared guest constant, no projection) does not apply.
     */
    @Test
    void numericArithmeticStillUsesCarrier() throws Exception {
        Census census = runCase("arithmetic", """
                run: () => { 1 + 2 }
                """, result -> {
                    assertTrue(result.isNumber());
                    assertTrue(result.fitsInInt());
                    assertEquals(3L, result.asLong());
                });
        assertEquals(List.of(
                        "c.EnterRoot",
                        "load.constant",
                        "load.constant",
                        "c.NumberAdd",
                        "c.EpilogForReturn",
                        "return"),
                census.names().stream().map(ProtosI115QCompactLiteralReturnTest::unquickened)
                        .toList(),
                "the computed terminal is one NumberAdd");
        assertFalse(census.has("TryPrimitiveNumeric"));
        assertFalse(census.has("ExperimentalInteger"));
        assertNoGenericDispatch(census);
        assertTrue(census.guestIntegers().isEmpty());
        assertTrue(census.carrierIntegers().isEmpty(),
                "signed-32 literals are int carriers, not long carriers");
        assertEquals(List.of(1, 2), census.intCarriers());
        assertTrue(census.projectsBefore());
        assertTrue(census.projectsAfter());
    }

    @Test
    void otherTerminalFamiliesUnchanged() throws Exception {
        Census yes = runCase("terminal-true", "run: () => { true }\n",
                result -> assertTrue(result.asBoolean()));
        Census no = runCase("terminal-false", "run: () => { false }\n",
                result -> assertFalse(result.asBoolean()));
        Census none = runCase("terminal-null", "run: () => { null }\n",
                result -> assertTrue(result.isNull()));
        for (Census census : List.of(yes, no, none)) {
            assertTrue(census.guestIntegers().isEmpty(), census.name());
            assertEquals(census.projectsBefore(), census.projectsAfter(),
                    census.name());
            if (census.compact()) {
                // No I115-O/Q certification: the compact policy still projects.
                assertTrue(census.projectsBefore(), census.name());
            }
        }
    }

    @Test
    void literalFalseWhileCondition() throws Exception {
        Census census = runCase("literal-false-while", """
                run: () => {
                    (() => false).whileTrue() { 99 }
                    1
                }
                """, result -> assertEquals(1L, result.asLong()));
        assertEquals(2, census.rootCount(), "No callback roots");
        assertFalse(census.has(BOOLEAN_CONDITION));
        assertNoGenericDispatch(census);
        assertNoReturnProjection(census);
        System.out.println("I115_Q_HISTORICAL_RUN_DSL_FULL=10");
    }

    @Test
    void literalTrueBooleanControl() throws Exception {
        Census taken = runCase("literal-true-iftrue", """
                run: () => {
                    true.ifTrue() { 1 }
                }
                """, result -> assertEquals(1L, result.asLong()));
        assertNoGenericDispatch(taken);
        assertFalse(taken.has(BOOLEAN_CONDITION));

        Census skipped = runCase("literal-false-iftrue", """
                run: () => {
                    r: 1
                    false.ifTrue() {
                        r = 2
                    }
                    r
                }
                """, result -> assertEquals(1L, result.asLong()));
        assertNoGenericDispatch(skipped);
        assertFalse(skipped.has(BOOLEAN_CONDITION));
    }

    @Test
    void consumedFalseWhileReturnsNull() throws Exception {
        Census census = runCase("consumed-false-while", """
                run: () => {
                    (() => false).whileTrue() { 99 }
                }
                """, result -> assertTrue(result.isNull()));
        assertNoGenericDispatch(census);
        assertFalse(census.has(BOOLEAN_CONDITION));
        assertTrue(census.guestIntegers().isEmpty());
    }

    @Test
    void booleanLocalRetainsConversion() throws Exception {
        Census census = runCase("boolean-local", """
                run: () => {
                    flag: false
                    (() => flag).whileTrue() { 99 }
                    1
                }
                """, result -> assertEquals(1L, result.asLong()));
        assertTrue(census.compact());
        assertTrue(census.has(BOOLEAN_CONDITION),
                "A Boolean local must keep the existing conversion");
    }

    @Test
    void integerComparisonUsesSharedNumericSite() throws Exception {
        Census census = runCase("integer-comparison", """
                run: () => {
                    i: 0
                    (() => i < 1).whileTrue() {
                        i = i + 1
                    }
                    i
                }
                """, result -> assertEquals(1L, result.asLong()));
        /*
         * I115-H7: one Lt whose Java boolean the conditional branch consumes
         * directly; a non-primitive comparison result is validated inside
         * Lt (exactly canonical true/false, otherwise the standard Error),
         * so no separate Boolean conversion is emitted.
         */
        List<String> names = census.names();
        assertEquals(1, names.stream().filter(name -> name.startsWith("c.Lt")).count());
        int lt = names.indexOf(names.stream()
                .filter(name -> name.startsWith("c.Lt")).findFirst().orElseThrow());
        assertTrue(names.get(lt + 1).startsWith("branch.false"),
                "the branch consumes Lt's boolean: " + names);
        assertFalse(census.has("TryPrimitiveNumeric"));
        assertFalse(census.has(BOOLEAN_CONDITION));
        assertFalse(census.has("ExperimentalInteger"));
        assertTrue(census.has("c.NumberInPlaceAdd"));
        assertNoGenericDispatch(census);
        assertFalse(census.has("PromoteFrameLexicalAuthority"));
    }

    @Test
    void conditionPrefixStillExecutes() throws Exception {
        Census census = runCase("condition-prefix", """
                run: () => {
                    n: 0
                    (() => {
                        n = n + 1
                        false
                    }).whileTrue() {
                        n = 100
                    }
                    n
                }
                """, result -> assertEquals(1L, result.asLong()));
        // Compact: the literal callbacks add no roots.
        assertEquals(2, census.rootCount(), "No callback roots");
        assertFalse(census.has(BOOLEAN_CONDITION));
        /*
         * I115-H7: the prefix n = n + 1 is the single NumberInPlaceAdd and
         * stays inside the While condition, before the literal test, so it
         * runs exactly once although the body never runs (result 1).
         */
        List<String> names = census.names();
        assertEquals(1, names.stream()
                .filter(name -> name.startsWith("c.NumberInPlaceAdd")).count());
        int prefix = names.indexOf(names.stream()
                .filter(name -> name.startsWith("c.NumberInPlaceAdd")).findFirst().orElseThrow());
        int exit = names.indexOf(names.stream()
                .filter(name -> name.startsWith("branch.false")).findFirst().orElseThrow());
        assertTrue(prefix < exit, "the prefix is evaluated before the loop test: " + names);
        assertFalse(census.has("TryPrimitiveNumeric"));
        assertFalse(census.has("ExperimentalInteger"));
        assertNoGenericDispatch(census);
    }

    @Test
    void guestValueAcrossCallersAndContexts() throws Exception {
        String source = LITERAL_RETURN + """
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
        System.out.println("I115_Q_CASE=representation");
        System.out.println("I115_Q_CORRECTNESS=PASS");
    }

    @Test
    void reparseAndCached() throws Exception {
        try (ProtosStandaloneHostedSession session =
                openSession("reparse-cached", LITERAL_RETURN)) {
            ProtosStandaloneHostedSession.PreparedTopLevel prepared =
                    session.prepareTopLevel("run");
            ProtosBytecodeRootNode run = selectedRoot(prepared);
            List<Long> initialGuests = guestIntegers(run);
            List<String> initialNames = names(run);
            assertEquals(List.of(1L), initialGuests);
            assertFalse(run.epilogProjectsReturnCarrier());
            assertEquals(1L, prepared.executable().execute().asLong());

            run.getRootNodes().ensureSourceInformation();
            assertEquals(initialGuests, guestIntegers(run));
            assertFalse(run.epilogProjectsReturnCarrier());
            List<String> reparsedNames = names(run);
            assertTrue(reparsedNames.containsAll(initialNames));

            int calls = 0;
            while (calls < 128
                    && run.getBytecodeNode().getTier() != BytecodeTier.CACHED) {
                assertEquals(1L, prepared.executable().execute().asLong());
                calls++;
            }
            assertEquals(BytecodeTier.CACHED, run.getBytecodeNode().getTier());
            Value result = prepared.executable().execute();
            assertTrue(result.fitsInLong());
            assertEquals(1L, result.asLong());
            assertEquals(initialGuests, guestIntegers(run));
            assertFalse(run.epilogProjectsReturnCarrier());
            System.out.println("I115_Q_CASE=reparse-cached");
            System.out.println("I115_Q_CORRECTNESS=PASS");
        }
    }

    /* I115-Q: the epilog projection stays off across ensureSourceInformation. */
    private static void assertNoReturnProjection(Census census) {
        assertFalse(census.projectsBefore(), census.name());
        assertFalse(census.projectsAfter(), census.name());
    }

    private static void assertNoGenericDispatch(Census census) {
        for (String forbidden : FORBIDDEN) {
            assertFalse(census.has(forbidden),
                    census.name() + " contains " + forbidden);
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
            report(census);
            return census;
        }
    }

    private static void report(Census census) {
        System.out.println("I115_Q_CASE=" + census.name());
        System.out.println("I115_Q_ROOT_COUNT=" + census.rootCount());
        System.out.println("I115_Q_RUN_DSL_FULL=" + census.runFull());
        System.out.println("I115_Q_MODULE_DSL_FULL=" + census.moduleFull());
        System.out.println("I115_Q_GUEST_INTEGER_CONSTANTS="
                + census.guestIntegers().size());
        System.out.println("I115_Q_EPILOG_PROJECTS=" + census.projectsAfter());
        System.out.println("I115_Q_CORRECTNESS=PASS");
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
        List<Long> carriers = new ArrayList<>();
        List<Integer> intCarriers = new ArrayList<>();
        for (Instruction instruction : instructions) {
            for (Object constant : constants(instruction)) {
                if (constant instanceof Long carrier) {
                    carriers.add(carrier);
                } else if (constant instanceof Integer small) {
                    intCarriers.add(small);
                }
            }
        }
        return new Census(name, roots.size(), instructions.size(), moduleFull,
                names(run), guestIntegers(run), carriers, intCarriers,
                projectsBefore, projectsAfter);
    }

    /* An instruction name without its quickening suffix. */
    private static String unquickened(String name) {
        int quickened = name.indexOf('$');
        return quickened < 0 ? name : name.substring(0, quickened);
    }

    private static List<String> names(ProtosBytecodeRootNode run) {
        return run.getBytecodeNode().getInstructionsAsList().stream()
                .map(ProtosInstructionNames::of)
                .toList();
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
