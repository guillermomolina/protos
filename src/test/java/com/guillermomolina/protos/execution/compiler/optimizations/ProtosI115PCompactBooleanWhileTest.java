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

import com.guillermomolina.protos.execution.ProtosDispatchTestSupport.DirectEntry;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosCoreErrors;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.bytecode.BytecodeTier;
import com.oracle.truffle.api.bytecode.Instruction;
import com.oracle.truffle.api.bytecode.LocalVariable;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.source.Source;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.Test;

/**
 * I115-P (EXPERIMENTAL, PLAT066-C / PERF039): compact whileTrue
 * accepts conditions admitted by the existing abstract-kind analysis,
 * rather than only Integer comparison expressions. I115-H1-FIX: a
 * comparison condition is the shared guarded numeric site, whose result is
 * validated as exact canonical true or false by the condition conversion;
 * its MISS branch physically holds the ordinary send and continuation
 * machinery, counted rather than forbidden.
 *
 * Tests measure physical Bytecode DSL instructions without pinning
 * newly admitted workloads to an invented instruction budget.
 */
final class ProtosI115PCompactBooleanWhileTest {

    private static final Path CORE =
            Path.of("protos", "lib", "core");

    private static final Path OUTPUT =
            Path.of("target", "perf039-while-zero", "i115-p");

    private static final List<String> FORBIDDEN = List.of(
            "PreparedBooleanCall",
            "TryDirectSendOne",
            "IsDirectSendMiss",
            "InstallFrameLexicalAuthority",
            "CreateCurrentIndexedLocalSlot",
            "ReadRootFrameLocal",
            "AssignRootFrameLocal",
            "ExperimentalInteger");

    /* Present only on the numeric MISS path; recorded for comparison. */
    private static final List<String> MISS_PATH = List.of(
            "TryPrimitiveNumeric",
            "PromoteFrameLexicalAuthority",
            "PrepareSendOne",
            "PrepareStructuredCall",
            "EnterNestedStructuredDispatch",
            "ResumeContinuation",
            "CompleteClosureCall",
            "FinishClosureCall",
            "Yield");

    private record Census(
            int rootCount,
            int moduleFull,
            int runFull,
            int totalFull,
            List<String> names) {}

    @Test
    void primitiveWhileZeroExactSource() throws Exception {
        Census census = runEliminatedLoop(
                "primitive-while-zero",
                """
                run: () => {
                    (() => false).whileTrue() { 99 }
                    1
                }
                """,
                result -> assertEquals(1, result.asInt()));

        assertEquals(2, census.rootCount());
        assertStructuredWhile(census);
        // A literal false condition is decided at lowering: neither the
        // condition nor the body is emitted, and the result is the same.
        assertFalse(census.names().stream()
                        .anyMatch(name -> name.contains("TryPrimitiveNumeric")),
                "A literal Boolean condition must not become a numeric comparison");

        System.out.println("I115_P_PRIMITIVE_WHILE_ZERO=PASS");
    }

    @Test
    void falseConditionNeverExecutesObservableBody() throws Exception {
        runEliminatedLoop(
                "false-body",
                """
                run: () => {
                    result: 1
                    (() => false).whileTrue() {
                        result = 99
                    }
                    result
                }
                """,
                result -> assertEquals(1, result.asInt()));
    }

    @Test
    void dynamicBooleanLocalZeroIterations() throws Exception {
        Census census = runCompact(
                "boolean-local-zero",
                """
                run: () => {
                    active: false
                    result: 7
                    (() => active).whileTrue() {
                        result = 99
                    }
                    result
                }
                """,
                result -> assertEquals(7, result.asInt()));

        assertBooleanConversion(census);
    }

    @Test
    void dynamicBooleanLocalOneIteration() throws Exception {
        Census census = runCompact(
                "boolean-local-one",
                """
                run: () => {
                    active: true
                    iterations: 0
                    (() => active).whileTrue() {
                        iterations = iterations + 1
                        active = false
                    }
                    iterations
                }
                """,
                result -> assertEquals(1, result.asInt()));

        assertBooleanConversion(census);
    }

    @Test
    void booleanBindingChangesAcrossIterations() throws Exception {
        Census census = runCompact(
                "boolean-changing",
                """
                run: () => {
                    active: true
                    iterations: 0
                    (() => active).whileTrue() {
                        iterations = iterations + 1
                        active = iterations < 3
                    }
                    iterations
                }
                """,
                result -> assertEquals(3, result.asInt()));

        assertBooleanConversion(census);
    }

    @Test
    void integerComparisonUsesSharedNumericSite() throws Exception {
        Census census = runCompact(
                "integer-comparison",
                """
                run: () => {
                    i: 0
                    (() => i < 1).whileTrue() {
                        i = i + 1
                    }
                    i
                }
                """,
                result -> assertEquals(1, result.asInt()));

        /*
         * I115-H7: the condition comparison is exactly one Lt whose Java
         * boolean the While branch consumes directly; the pre-H7 numeric
         * site and its separate Boolean conversion are gone from this site.
         * Lt validates a non-primitive (send) result itself; see
         * comparisonMissNonBooleanConditionSignalsStandardError.
         */
        assertLtCondition(census.names());

        System.out.println(
                "I115_P_INTEGER_COMPARISON_RUN_FULL=" + census.runFull());
    }

    @Test
    void consumedZeroIterationLoopReturnsCanonicalNull() throws Exception {
        runEliminatedLoop(
                "consumed-zero",
                """
                run: () => {
                    (() => false).whileTrue() {
                        99
                    }
                }
                """,
                result -> assertTrue(result.isNull()));
    }

    @Test
    void nonBooleanConditionRetainsGeneralError() throws Exception {
        try (ProtosStandaloneHostedSession session = openSession(
                "non-boolean-condition",
                """
                run: () => {
                    (() => 0).whileTrue() {
                        99
                    }
                    1
                }
                """)) {
            ProtosStandaloneHostedSession.PreparedTopLevel prepared =
                    session.prepareTopLevel("run");

            assertGeneralLowering(selectedRoot(prepared));

            PolyglotException failure = assertThrows(
                    PolyglotException.class,
                    () -> prepared.executable().execute());

            assertTrue(failure.isGuestException());
            assertFalse(failure.isInternalError());

            System.out.println(
                    "I115_P_NON_BOOLEAN_FALLBACK=PASS");
        }
    }

    @Test
    void unknownBooleanCallRetainsGeneralLowering() throws Exception {
        try (ProtosStandaloneHostedSession session = openSession(
                "unknown-boolean-call",
                """
                condition: () => false
                run: () => {
                    result: 1
                    (() => condition()).whileTrue() {
                        result = 99
                    }
                    result
                }
                """)) {
            ProtosStandaloneHostedSession.PreparedTopLevel prepared =
                    session.prepareTopLevel("run");

            assertGeneralLowering(selectedRoot(prepared));

            assertEquals(
                    1,
                    prepared.executable().execute().asInt());

            System.out.println(
                    "I115_P_UNKNOWN_BOOLEAN_FALLBACK=PASS");
        }
    }

    @Test
    void consecutiveBooleanLoopsAreIndependent() throws Exception {
        Census census = runCompact(
                "consecutive-boolean-loops",
                """
                run: () => {
                    first: false
                    second: true
                    result: 0

                    (() => first).whileTrue() {
                        result = 99
                    }

                    (() => second).whileTrue() {
                        result = result + 2
                        second = false
                    }

                    result
                }
                """,
                result -> assertEquals(2, result.asInt()));

        assertBooleanConversion(census);
    }

    @Test
    void reparseAndCachedInterpreterPreserveWhile() throws Exception {
        try (ProtosStandaloneHostedSession session = openSession(
                "reparse",
                """
                run: () => {
                    active: true
                    count: 0
                    (() => active).whileTrue() {
                        count = count + 1
                        active = false
                    }
                    count
                }
                """)) {
            ProtosStandaloneHostedSession.PreparedTopLevel prepared =
                    session.prepareTopLevel("run");

            ProtosBytecodeRootNode run = selectedRoot(prepared);

            assertEquals(
                    BytecodeTier.UNCACHED,
                    run.getBytecodeNode().getTier());

            Census before = census("reparse-before", run, true, true);

            assertEquals(1, prepared.executable().execute().asInt());

            run.getRootNodes().ensureSourceInformation();

            Census after = census("reparse-after", run, true, true);

            assertEquals(before.runFull(), after.runFull());
            assertEquals(before.moduleFull(), after.moduleFull());

            int calls = 0;

            while (calls < 128
                    && run.getBytecodeNode().getTier()
                            != BytecodeTier.CACHED) {
                assertEquals(
                        1,
                        prepared.executable().execute().asInt());
                calls++;
            }

            assertEquals(
                    BytecodeTier.CACHED,
                    run.getBytecodeNode().getTier());

            assertEquals(1, prepared.executable().execute().asInt());

            Census cached = census("reparse-cached", run, true, true);

            assertStructuredWhile(cached);
            assertBooleanConversion(cached);

            System.out.println("I115_P_REPARSE_CACHED=PASS");
        }
    }

    @Test
    void conditionPrefixEvaluatesOnEveryTest() throws Exception {
        Census census = runCompact(
                "condition-prefix",
                """
                run: () => {
                    active: true
                    checks: 0

                    (() => {
                        checks = checks + 1
                        active
                    }).whileTrue() {
                        active = false
                    }

                    checks
                }
                """,
                result -> assertEquals(2, result.asInt()));

        assertBooleanConversion(census);
    }

    /**
     * I115-H1-FIX: a compact whileTrue condition is the shared comparison
     * site. Integer receivers HIT and yield canonical false or true, which
     * the validating conversion turns into the loop branch. A receiver
     * whose redefined '<' returns a non-Boolean object MISSes, and that
     * condition result signals the standard Error of the standard
     * whileTrue behavior: no truthiness, the body never runs.
     */
    @Test
    void comparisonMissNonBooleanConditionSignalsStandardError() throws Exception {
        withCore(module -> {
            ProtosClosureValue run = closure(
                    "(gate) => { body: 0\n(() => gate < 1).whileTrue() {\nbody = body + 1\n}\nbody }",
                    module);
            List<String> names = instructionNames(run);
            // I115: one RichCompareLt is the condition site.
            assertLtCondition(names);

            /*
             * Canonical true and false conditions, and no truthiness, by real
             * guest execution: the condition value is supplied directly and
             * the body records every iteration through a native probe.
             */
            int[] iterations = new int[1];
            ProtosClosureValue mark = ProtosClosureValue.nativeClosure(
                    (activation, supplied) -> {
                        iterations[0]++;
                        return ProtosBooleanValue.FALSE;
                    });
            ProtosClosureValue gated = closure(
                    "(condition, mark) => { (() => mark(condition)).whileTrue() { 0 }\n0 }",
                    module);
            ProtosClosureValue direct = closure(
                    "(condition) => { body: 0\n(() => condition).whileTrue() {\n"
                            + "body = body + 1\ncondition = false\n}\nbody }",
                    module);
            assertEquals("1", integerValue(
                    enter(fastDirect(direct, module, ProtosBooleanValue.TRUE))),
                    "canonical true runs the body");
            assertEquals("0", integerValue(
                    enter(fastDirect(direct, module, ProtosBooleanValue.FALSE))),
                    "canonical false runs no body");
            assertEquals("0", integerValue(enter(fastDirect(gated, module, 0L, mark))));
            assertEquals(1, iterations[0], "the condition prefix ran once");
            ProtosSignalException truthy = assertThrows(
                    ProtosSignalException.class,
                    () -> enter(fastDirect(direct, module, Long.valueOf(1))),
                    "whileTrue accepts no truthiness");
            assertSame(
                    ProtosCoreErrors.prototype(module, ProtosCoreErrors.StandardError.ERROR),
                    truthy.error().parent().orElse(null));

            // Canonical false: zero iterations.
            assertEquals("0", integerValue(
                    enter(fastDirect(run, module, Long.valueOf(5)))));

            ProtosClosureValue counted = closure(
                    "(limit) => { n: 0\n(() => n < limit).whileTrue() {\nn = n + 1\n}\nn }",
                    module);
            // Canonical true until the bound: three iterations.
            assertEquals("3", integerValue(
                    enter(fastDirect(counted, module, Long.valueOf(3)))));

            ProtosObjectValue notBoolean =
                    new ProtosObjectValue(ProtosObjectValue.rootObject());
            ProtosObjectValue gate =
                    new ProtosObjectValue(ProtosObjectValue.rootObject());
            int[] comparisons = new int[1];
            gate.createLocalSlot("<", ProtosClosureValue.nativeClosure(
                    (activation, supplied) -> {
                        comparisons[0]++;
                        return notBoolean;
                    }));
            ProtosClosureValue observed = closure(
                    "(gate, mark) => { (() => gate < 1).whileTrue() {\nmark(0)\n}\n0 }",
                    module);
            iterations[0] = 0;
            ProtosSignalException signal = assertThrows(
                    ProtosSignalException.class,
                    () -> enter(fastDirect(observed, module, gate, mark)));
            assertSame(
                    ProtosCoreErrors.prototype(module, ProtosCoreErrors.StandardError.ERROR),
                    signal.error().parent().orElse(null),
                    "a non-Boolean condition result is the standard Error");
            assertEquals(1, comparisons[0], "the condition ran once");
            assertEquals(0, iterations[0], "no body runs after an invalid condition");
            ProtosSignalException again = assertThrows(
                    ProtosSignalException.class,
                    () -> enter(fastDirect(run, module, gate)));
            assertSame(
                    ProtosCoreErrors.prototype(module, ProtosCoreErrors.StandardError.ERROR),
                    again.error().parent().orElse(null));
        });
        System.out.println("I115_P_NON_BOOLEAN_MISS_CONDITION=PASS");
    }

    private interface CoreTest {
        void run(ProtosActivation module) throws Exception;
    }

    // The PERF025 host-object harness (ProtosPerf025CompactCalleeExecutionTest).
    private static void withCore(CoreTest test) throws Exception {
        try (Context context = Context.newBuilder(ProtosLanguage.ID).build()) {
            context.initialize(ProtosLanguage.ID);
            context.enter();
            try {
                ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
                test.run(prelude.newModuleActivation());
            } finally {
                context.leave();
            }
        }
    }

    private static ProtosClosureValue closure(String characters, ProtosActivation activation) {
        Source source =
                Source.newBuilder(ProtosLanguage.ID, characters, "i115-p-host.protos")
                        .mimeType(ProtosLanguage.MIME_TYPE)
                        .build();
        CallTarget target = ProtosLanguageContext.current().parsePublic(source);
        return assertInstanceOf(ProtosClosureValue.class, target.call(activation));
    }

    private static RootCallTarget target(ProtosClosureValue closure) {
        return ProtosDispatchTestSupport.target(closure);
    }

    private static DirectEntry fastDirect(
            ProtosClosureValue closure, ProtosActivation caller, Object... supplied) {
        // I115: the B5 header the shared dispatcher writes for a function call.
        return ProtosDispatchTestSupport.direct(closure, caller, supplied);
    }

    private static Object enter(DirectEntry prepared) {
        return ProtosDispatchTestSupport.enter(prepared);
    }

    private static List<String> instructionNames(ProtosClosureValue closure) {
        return closure.executionPlan()
                .orElseThrow()
                .bytecodeActivationRootForTesting()
                .getBytecodeNode()
                .getInstructionsAsList()
                .stream()
                .map(ProtosInstructionNames::of)
                .toList();
    }

    private static String integerValue(Object value) {
        return com.guillermomolina.protos.runtime.ProtosNumericValueSupport
                .integerDecimalText(value);
    }

    private static Census runCompact(
            String name,
            String source,
            Consumer<Value> assertion) throws Exception {
        try (ProtosStandaloneHostedSession session =
                     openSession(name, source)) {
            ProtosStandaloneHostedSession.PreparedTopLevel prepared =
                    session.prepareTopLevel("run");

            ProtosBytecodeRootNode run = selectedRoot(prepared);

            Census result = census(name, run, true, true);

            assertion.accept(prepared.executable().execute());

            System.out.println(
                    "I115_P_CASE[" + name + "]_CORRECTNESS=PASS");

            return result;
        }
    }

    /*
     * A loop whose literal condition is false is decided at lowering: the run
     * root contains neither the condition validation nor a loop test, and
     * executes to the same result as the loop.
     */
    private static Census runEliminatedLoop(
            String name,
            String source,
            Consumer<Value> assertion) throws Exception {
        try (ProtosStandaloneHostedSession session =
                     openSession(name, source)) {
            ProtosStandaloneHostedSession.PreparedTopLevel prepared =
                    session.prepareTopLevel("run");
            ProtosBytecodeRootNode run = selectedRoot(prepared);
            Census result = census(name, run, true, false);
            assertFalse(result.names().stream()
                            .anyMatch(n -> n.contains("CanonicalControlCondition")),
                    name + " tests an eliminated condition: " + result.names());
            assertFalse(result.names().stream().anyMatch(n -> n.startsWith("branch.false")),
                    name + " keeps a loop test: " + result.names());
            assertion.accept(prepared.executable().execute());
            System.out.println(
                    "I115_P_CASE[" + name + "]_CORRECTNESS=PASS");
            return result;
        }
    }

    private static ProtosStandaloneHostedSession openSession(
            String name,
            String source) throws IOException {

        Files.createDirectories(OUTPUT);

        Path sourceFile = OUTPUT.resolve(name + ".protos");

        Files.writeString(
                sourceFile, source, StandardCharsets.UTF_8);

        ProtosStandaloneHostedSession session =
                ProtosStandaloneHostedSession.open(CORE, sourceFile);

        if (session.initialOutcome().state()
                != ProtosExecutionOutcome.State.COMPLETED) {
            session.close();
            throw new AssertionError(
                    "Module initialization failed: " + name);
        }

        return session;
    }

    private static ProtosBytecodeRootNode selectedRoot(
            ProtosStandaloneHostedSession.PreparedTopLevel prepared) {

        return assertInstanceOf(
                ProtosBytecodeRootNode.class,
                prepared.hostExecutableForTesting()
                        .target.getRootNode());
    }

    private static Census census(
            String name,
            ProtosBytecodeRootNode run,
            boolean admitted,
            boolean loopEmitted) {

        run.getRootNodes().ensureSourceInformation();

        List<ProtosBytecodeRootNode> roots =
                run.getRootNodes().getNodes().stream()
                        .map(ProtosBytecodeRootNode.class::cast)
                        .toList();

        int moduleFull = 0;
        int runFull = 0;
        int totalFull = 0;

        for (ProtosBytecodeRootNode root : roots) {
            int count = root.getBytecodeNode()
                    .getInstructionsAsList().size();

            totalFull += count;

            if (root == run) {
                runFull = count;
            } else {
                moduleFull += count;
            }
        }

        List<Instruction> instructions =
                run.getBytecodeNode().getInstructionsAsList();

        List<String> names = instructions.stream()
                .map(ProtosInstructionNames::of)
                .toList();

        String prefix = "I115_P_CASE[" + name + "]_";

        System.out.println(prefix + "ROOT_COUNT=" + roots.size());
        System.out.println(prefix + "MODULE_FULL=" + moduleFull);
        System.out.println(prefix + "RUN_FULL=" + runFull);
        System.out.println(prefix + "DSL_FULL_TOTAL=" + totalFull);
        for (String operation : MISS_PATH) {
            System.out.println(prefix + operation + "_COUNT="
                    + names.stream().filter(n -> n.contains(operation)).count());
        }

        if (admitted) {
            assertEquals(
                    2, roots.size(),
                    "Compact loop callbacks must not add physical roots");

            for (String instructionName : names) {
                for (String forbidden : FORBIDDEN) {
                    assertFalse(
                            instructionName.contains(forbidden),
                            name + " contains " + instructionName);
                }
            }

            if (loopEmitted) {
                assertStructuredWhileInstructions(instructions);
            }
        }

        return new Census(
                roots.size(),
                moduleFull,
                runFull,
                totalFull,
                names);
    }

    private static void assertStructuredWhile(Census census) {
        assertEquals(2, census.rootCount());
        assertTrue(census.runFull() > 0);
    }

    private static void assertStructuredWhileInstructions(
            List<Instruction> instructions) {

        boolean conditionalBranch = instructions.stream()
                .anyMatch(instruction ->
                        instruction.getName().startsWith("branch.false"));

        boolean backwardEdge = false;

        for (Instruction instruction : instructions) {
            int current = instruction.getBytecodeIndex();

            for (Instruction.Argument argument :
                    instruction.getArguments()) {

                if (argument.getKind()
                        == Instruction.Argument.Kind.BYTECODE_INDEX
                        && argument.asBytecodeIndex() < current) {
                    backwardEdge = true;
                }
            }
        }

        assertTrue(
                conditionalBranch,
                "Structured While must test its Boolean condition");

        assertTrue(
                backwardEdge,
                "Structured While must contain a backward control-flow edge");
    }

    private static void assertBooleanConversion(Census census) {
        assertTrue(
                census.names().stream().anyMatch(
                        name -> name.contains(
                                "CanonicalControlCondition")),
                "A non-literal condition must use the validating conversion");
    }

    /*
     * I115 unified control: one RichCompareLt site, then (possibly after
     * continuation composition) one
     * canonical Boolean validation, then the While's branch.false. The
     * comparison and the branch need not be adjacent. The retired numeric
     * MISS operations stay absent.
     */
    private static void assertLtCondition(List<String> names) {
        assertEquals(1, names.stream().filter(name -> name.startsWith("c.RichCompareLt")).count(),
                "The condition comparison is exactly one RichCompareLt: " + names);
        assertEquals(1, names.stream().filter(
                        name -> name.startsWith("c.CanonicalControlCondition")).count(),
                "One canonical Boolean validation of the condition: " + names);
        // The primitive Boolean carrier of the comparison feeds the
        // validation directly; it is never materialized as a guest value.
        int compare = firstIndex(names, "c.RichCompareLt", 0);
        int condition = firstIndex(names, "c.CanonicalControlCondition", compare + 1);
        int branch = firstIndex(names, "branch.false", condition + 1);
        assertTrue(compare >= 0 && condition > compare && branch > condition,
                "comparison, validation, branch: " + names);
        assertFalse(names.stream().anyMatch(name -> name.startsWith("c.Lt")),
                "The retired Lt operation remains: " + names);
        for (String retired : List.of("TryPrimitiveNumeric", "IsCanonicalBooleanValue",
                "IsPrimitiveNumericSendMiss")) {
            assertFalse(names.stream().anyMatch(name -> name.contains(retired)),
                    retired + " remains: " + names);
        }
    }

    private static int firstIndex(List<String> names, String prefix, int from) {
        if (from < 0) {
            return -1;
        }
        for (int index = from; index < names.size(); index++) {
            if (names.get(index).startsWith(prefix)) {
                return index;
            }
        }
        return -1;
    }

    private static void assertGeneralLowering(
            ProtosBytecodeRootNode run) {

        run.getRootNodes().ensureSourceInformation();

        List<String> instructions =
                run.getBytecodeNode().getInstructionsAsList().stream()
                        .map(ProtosInstructionNames::of)
                        .toList();

        /*
         * I115: one unified control-flow lowering. Current operations whose
         * names contain "Experimental" (for example the canonical Boolean
         * condition) are allowed; only retired control-flow and compact-root
         * operations are forbidden.
         */
        for (String retired : List.of(
                "PreparedBooleanCall",
                "AdmitsInlineLiteralWhile",
                "StructuredWhileCondition",
                "TryCompactCanonicalBoolean",
                "IsCanonicalBooleanValue",
                "TryPrimitiveNumeric",
                "ExperimentalInteger",
                "IsCompactLocalFrame",
                "InstallFrameLexicalAuthority",
                "PromoteFrameLexicalAuthority",
                "CreateCurrentFrameLocal",
                "ReadRootFrameLocal")) {
            for (String name : instructions) {
                assertFalse(name.contains(retired), "Retired operation " + name);
            }
        }
        // The unified While: a native conditional branch and a backward edge.
        assertStructuredWhileInstructions(run.getBytecodeNode().getInstructionsAsList());
    }
}
