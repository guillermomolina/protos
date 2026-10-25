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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosIntegerValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.bytecode.Instruction;
import com.oracle.truffle.api.bytecode.LocalVariable;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.source.Source;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.ArrayList;
import java.util.function.Consumer;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.Test;

/**
 * I115-N (EXPERIMENTAL, PLAT066-C / PERF041): admitted Boolean control
 * (ifTrue, ifFalse, ifTrueIfFalse, if, else) lowers inside the compact
 * root as structured IfThen/IfThenElse, without a callback root or
 * prepared Boolean dispatch. I115-H1-FIX: a numeric send in such a root is
 * the shared guarded numeric site, whose MISS branch physically holds the
 * ordinary send and its continuation machinery; those operations are
 * counted, not forbidden. A comparison is dispatched, so its result is no
 * proven canonical Boolean: such a control is guarded at run time. A
 * canonical receiver (HIT) runs the callback inline; any other (MISS)
 * receives the control selector ordinarily, with the callbacks as guest
 * Closures whose physical roots the lowering registers. DSL FULL counts
 * are recorded, not pinned.
 */
final class ProtosI115NCompactBooleanTest {

    private static final Path CORE = Path.of("protos", "lib", "core");

    private static final Path OUTPUT =
            Path.of("target", "perf039-while-once", "i115-n");

    private static final List<String> FORBIDDEN = List.of(
            "TryCompactCanonicalBooleanOne",
            "IsCanonicalBooleanClass",
            "PreparedBooleanCall",
            "TryDirectSendOne",
            "IsDirectSendMiss",
            "InstallFrameLexicalAuthority",
            "ReadRootFrameLocal",
            "AssignRootFrameLocal",
            "ExperimentalInteger");

    /* Present only on MISS paths; recorded for comparison. */
    private static final List<String> MISS_PATH = List.of(
            "TryPrimitiveNumeric",
            "IsCanonicalBooleanValue",
            "PromoteFrameLexicalAuthority",
            "MaterializeCurrentClosure",
            "PrepareSendOne",
            "PrepareStructuredCall",
            "EnterNestedStructuredDispatch",
            "ResumeContinuation",
            "CompleteClosureCall",
            "FinishClosureCall",
            "Yield");

    private record Census(
            int rootCount, int runCount, int total, boolean resultLocal) {}

    @Test
    void perf041PrimaryCase() throws Exception {
        runCase("perf041", """
                run: () => {
                    value: true
                    result: null
                    value.ifTrue() {
                        result = 1
                    }
                    result
                }
                """, result -> assertEquals(1, result.asInt()));
    }

    @Test
    void trueIfTrueRunsCallback() throws Exception {
        runCase("true-if-true", """
                run: () => {
                    flag: true
                    out: 0
                    flag.ifTrue() {
                        out = 1
                    }
                    out
                }
                """, result -> assertEquals(1, result.asInt()));
    }

    @Test
    void falseIfTrueSkipsCallback() throws Exception {
        runCase("false-if-true", """
                run: () => {
                    seen: 7
                    off: false
                    off.ifTrue() {
                        seen = 8
                    }
                }
                """, result -> assertTrue(result.isNull()));
    }

    @Test
    void trueIfFalseSkipsCallback() throws Exception {
        runCase("true-if-false", """
                run: () => {
                    mark: 3
                    yes: true
                    yes.ifFalse() {
                        mark = 4
                    }
                }
                """, result -> assertTrue(result.isNull()));
    }

    @Test
    void falseIfFalseRunsCallback() throws Exception {
        runCase("false-if-false", """
                run: () => {
                    no: false
                    count: 0
                    no.ifFalse() {
                        count = 2
                    }
                    count
                }
                """, result -> assertEquals(2, result.asInt()));
    }

    @Test
    void ifTrueIfFalseSelectsFirst() throws Exception {
        runCase("if-true-if-false-true", """
                run: () => {
                    pick: true
                    pick.ifTrueIfFalse(() => 10, () => 20)
                }
                """, result -> assertEquals(10, result.asInt()));
    }

    @Test
    void ifTrueIfFalseSelectsSecond() throws Exception {
        runCase("if-true-if-false-false", """
                run: () => {
                    side: 1
                    choice: false
                    choice.ifTrueIfFalse(() => {
                        side = 5
                        30
                    }, () => {
                        side = 6
                        40
                    }) + side
                }
                """, result -> assertEquals(46, result.asInt()));
    }

    /*
     * if and else are ordinary member sends (D211 keeps object.if(...)
     * ordinary), not NormalBooleanControl rows, so the compact subset never
     * admits them. VALUES_AND_COLLECTIONS.md: Core v0.1 publishes no
     * standard Object.if or Object.else behavior, so on a canonical Boolean
     * the send finds no slot and signals a guest Error.
     */
    @Test
    void ifAndElseAreOrdinaryUndefinedSends() throws Exception {
        runGeneralFailure("if-is-undefined", """
                run: () => {
                    hit: 0
                    gate: true
                    gate.if() {
                        hit = 9
                    }
                }
                """);
        runGeneralFailure("else-is-undefined", """
                run: () => {
                    miss: false
                    other: 0
                    miss.else() {
                        other = 11
                    }
                }
                """);
    }

    @Test
    void computedCondition() throws Exception {
        // I115-H1-FIX: guarded control; its callback root serves the MISS.
        runCase("computed-condition", 3, """
                run: () => {
                    acc: 0
                    value: 2 < 3
                    value.ifTrue() {
                        acc = acc + 5
                    }
                    acc
                }
                """, result -> assertEquals(5, result.asInt()));
    }

    @Test
    void consecutiveIfTrueGrowDsl() throws Exception {
        Census single = runCase("single-if-true", """
                run: () => {
                    t: true
                    sum: 1
                    t.ifTrue() {
                        sum = sum + 2
                    }
                    sum
                }
                """, result -> assertEquals(3, result.asInt()));
        Census consecutive = runCase("consecutive-if-true", """
                run: () => {
                    t: true
                    sum: 1
                    t.ifTrue() {
                        sum = sum + 2
                    }
                    t.ifTrue() {
                        sum = sum * 4
                    }
                    sum
                }
                """, result -> assertEquals(12, result.asInt()));
        assertTrue(consecutive.runCount() > single.runCount(),
                "A second ifTrue must add instructions");
    }

    @Test
    void ifTrueInsideWhileTrue() throws Exception {
        // I115-H1-FIX: guarded control; its callback root serves the MISS.
        runCase("if-true-in-while", 3, """
                run: () => {
                    k: 0
                    hits: 0
                    (() => k < 4).whileTrue() {
                        (k < 2).ifTrue() {
                            hits = hits + 1
                        }
                        k = k + 1
                    }
                    hits
                }
                """, result -> assertEquals(2, result.asInt()));
    }

    @Test
    void consumedIfTrueReturnsCallbackResult() throws Exception {
        runCase("consumed-if-true", """
                run: () => {
                    base: 6
                    on: true
                    on.ifTrue() {
                        base * 7
                    }
                }
                """, result -> assertEquals(42, result.asInt()));
    }

    @Test
    void discardedIfTrueUsesNoResultLocal() throws Exception {
        Census census = runCase("discarded-if-true", """
                run: () => {
                    z: 1
                    ready: true
                    ready.ifTrue() {
                        z = z + 1
                    }
                    z
                }
                """, result -> assertEquals(2, result.asInt()));
        assertEquals(2, census.rootCount());
        assertFalse(census.resultLocal(),
                "A discarded ifTrue must not allocate a result local");
    }

    /**
     * I115-H1-FIX: one guarded site, both paths. An Integer receiver makes
     * the comparison a HIT whose canonical Boolean runs the callback inline
     * (no Closure, no published activation). A receiver whose redefined '<'
     * returns a non-Boolean object makes the comparison and the control
     * MISS: that object's own ifTrue is selected dynamically and receives
     * the callback as a guest Closure, whose invocations mutate the compact
     * binding the root then returns.
     */
    @Test
    void redefinedComparisonDispatchesIfTrueToItsResult() throws Exception {
        withCore(module -> {
            ProtosClosureValue run = closure(
                    "(gate) => { hits: 0\n(gate < 2).ifTrue() {\nhits = hits + 1\n}\nhits }",
                    module);
            List<String> names = instructionNames(run);
            assertTrue(names.stream().anyMatch(n -> n.contains("TryPrimitiveNumericLess")));
            assertTrue(names.stream().anyMatch(n -> n.contains("IsCanonicalBooleanValue")));
            assertTrue(names.stream().anyMatch(n -> n.contains("MaterializeCurrentClosure")));
            assertTrue(names.stream().anyMatch(n -> n.contains("Experimental")),
                    "the root keeps the compact lowering");

            // HIT: canonical true, then canonical false, both inline.
            ProtosPreparedCalls.PreparedClosureCall taken =
                    fastDirect(run, module, new ProtosIntegerValue(1));
            assertEquals("1", integerValue(enter(taken)));
            assertSame(run, taken.targetArguments()[0],
                    "the inline path creates no Closure and publishes nothing");
            ProtosPreparedCalls.PreparedClosureCall skipped =
                    fastDirect(run, module, new ProtosIntegerValue(5));
            assertEquals("0", integerValue(enter(skipped)));
            assertSame(run, skipped.targetArguments()[0]);

            // MISS: the redefined '<' answers an object with its own ifTrue.
            List<Object> comparedWith = new ArrayList<>();
            List<Object> callbacks = new ArrayList<>();
            ProtosObjectValue decision =
                    new ProtosObjectValue(ProtosObjectValue.rootObject());
            decision.createLocalSlot("ifTrue", ProtosClosureValue.nativeClosure(
                    (activation, supplied) -> {
                        callbacks.addAll(supplied);
                        ProtosClosureValue callback = assertInstanceOf(
                                ProtosClosureValue.class, supplied.get(0));
                        ProtosClosureInvoker.invoke(callback, List.of(), activation);
                        ProtosClosureInvoker.invoke(callback, List.of(), activation);
                        return new ProtosIntegerValue(42);
                    }));
            ProtosObjectValue gate =
                    new ProtosObjectValue(ProtosObjectValue.rootObject());
            gate.createLocalSlot("<", ProtosClosureValue.nativeClosure(
                    (activation, supplied) -> {
                        comparedWith.addAll(supplied);
                        return decision;
                    }));

            assertEquals("2", integerValue(enter(fastDirect(run, module, gate))),
                    "each callback invocation updated the root's own binding");
            assertEquals(1, comparedWith.size());
            assertEquals("2", integerValue(comparedWith.get(0)));
            assertEquals(1, callbacks.size(), "ifTrue received exactly its callback");

            // The site still takes the inline path for a canonical result.
            assertEquals("1", integerValue(
                    enter(fastDirect(run, module, new ProtosIntegerValue(0)))));
        });
        System.out.println("I115_N_REDEFINED_COMPARISON_DISPATCH=PASS");
    }

    @Test
    void unadmittedCallbackKeepsWholeRootGeneral() throws Exception {
        String name = "unadmitted-callback";
        try (ProtosStandaloneHostedSession session = openSession(name, """
                step: () => 1
                run: () => {
                    v: 0
                    go: true
                    go.ifTrue() {
                        v = step()
                    }
                    v
                }
                """)) {
            ProtosStandaloneHostedSession.PreparedTopLevel prepared =
                    session.prepareTopLevel("run");
            ProtosBytecodeRootNode run = selectedRoot(prepared);
            run.getRootNodes().ensureSourceInformation();
            List<Instruction> instructions =
                    run.getBytecodeNode().getInstructionsAsList();
            System.out.println("I115_N_CASE=" + name);
            System.out.println("I115_N_RUN_FULL=" + instructions.size());
            // The whole root takes the general lowerer: no compact operation.
            for (Instruction instruction : instructions) {
                String instructionName = ProtosInstructionNames.of(instruction);
                assertFalse(instructionName.contains("Experimental"),
                        "Unadmitted root mixes compact " + instructionName);
            }
            assertEquals(1, prepared.executable().execute().asInt());
            System.out.println("I115_N_CORRECTNESS=PASS");
        }
    }

    private static Census runCase(
            String name, String source, Consumer<Value> expected)
            throws Exception {
        return runCase(name, 2, source, expected);
    }

    /*
     * {@code rootCount}: the module and run roots, plus one registered
     * callback root per callback of a guarded (dispatching) control.
     */
    private static Census runCase(
            String name, int rootCount, String source, Consumer<Value> expected)
            throws Exception {
        try (ProtosStandaloneHostedSession session = openSession(name, source)) {
            ProtosStandaloneHostedSession.PreparedTopLevel prepared =
                    session.prepareTopLevel("run");
            Census census = census(name, selectedRoot(prepared), rootCount);
            expected.accept(prepared.executable().execute());
            System.out.println("I115_N_CORRECTNESS=PASS");
            return census;
        }
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
                Source.newBuilder(ProtosLanguage.ID, characters, "i115-n-host.protos")
                        .mimeType(ProtosLanguage.MIME_TYPE)
                        .build();
        CallTarget target = ProtosLanguageContext.current().parsePublic(source);
        return assertInstanceOf(ProtosClosureValue.class, target.call(activation));
    }

    private static RootCallTarget target(ProtosClosureValue closure) {
        return ProtosBytecodeRootNode.PrepareSendArguments.fastOrdinarySendTarget(
                closure, ProtosLanguageContext.currentIfEnteredForRuntime());
    }

    private static ProtosPreparedCalls.PreparedClosureCall fastDirect(
            ProtosClosureValue closure, ProtosActivation caller, Object... supplied) {
        ProtosLanguageContext entered = ProtosLanguageContext.currentIfEnteredForRuntime();
        ProtosClosureValue selected =
                ProtosClosureCallPreparation.directClosureCallSelectionOrNull(closure, caller);
        assertSame(closure, selected, "canonical direct Closure-call selection");
        return ProtosBytecodeRootNode.PrepareClosureCallArguments.fastDirect(
                false,
                closure,
                caller,
                supplied,
                selected,
                selected.definition(),
                entered,
                selected.definition(),
                entered,
                target(closure));
    }

    private static Object enter(ProtosPreparedCalls.PreparedClosureCall prepared) {
        Object entered =
                ProtosBytecodeRootNode.EnterClosureCall.ordinaryIndirect(
                        assertInstanceOf(ProtosPreparedCalls.OrdinarySourceCall.class, prepared),
                        IndirectCallNode.create());
        return prepared.finish(entered);
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

    /* A root outside the compact subset whose execution signals a guest Error. */
    private static void runGeneralFailure(String name, String source)
            throws Exception {
        try (ProtosStandaloneHostedSession session = openSession(name, source)) {
            ProtosStandaloneHostedSession.PreparedTopLevel prepared =
                    session.prepareTopLevel("run");
            ProtosBytecodeRootNode run = selectedRoot(prepared);
            run.getRootNodes().ensureSourceInformation();
            List<Instruction> instructions =
                    run.getBytecodeNode().getInstructionsAsList();
            System.out.println("I115_N_CASE=" + name);
            System.out.println("I115_N_RUN_FULL=" + instructions.size());
            for (Instruction instruction : instructions) {
                String instructionName = ProtosInstructionNames.of(instruction);
                assertFalse(instructionName.contains("Experimental"),
                        "Unadmitted root mixes compact " + instructionName);
            }
            PolyglotException failure = assertThrows(
                    PolyglotException.class,
                    () -> prepared.executable().execute());
            assertTrue(failure.isGuestException());
            assertFalse(failure.isInternalError());
            System.out.println("I115_N_CORRECTNESS=PASS");
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

    private static Census census(
            String name, ProtosBytecodeRootNode run, int rootCount) {
        // DEFAULT bytecode with source information, not instrumented DEBUG.
        run.getRootNodes().ensureSourceInformation();
        List<ProtosBytecodeRootNode> roots =
                run.getRootNodes().getNodes().stream()
                        .map(ProtosBytecodeRootNode.class::cast)
                        .toList();
        System.out.println("I115_N_CASE=" + name);
        System.out.println("I115_N_ROOT_COUNT=" + roots.size());

        int total = 0;
        int runCount = 0;
        for (int index = 0; index < roots.size(); index++) {
            ProtosBytecodeRootNode current = roots.get(index);
            int count =
                    current.getBytecodeNode().getInstructionsAsList().size();
            total += count;
            if (current == run) {
                runCount = count;
            }
            System.out.println("I115_N_ROOT" + index + "_FULL=" + count);
        }
        System.out.println("I115_N_DSL_FULL_TOTAL=" + total);

        assertEquals(rootCount, roots.size(),
                "Only guarded controls register callback roots");
        boolean compact = false;
        int[] missPath = new int[MISS_PATH.size()];
        for (Instruction instruction
                : run.getBytecodeNode().getInstructionsAsList()) {
            String instructionName = ProtosInstructionNames.of(instruction);
            compact |= instructionName.contains("Experimental");
            for (String forbidden : FORBIDDEN) {
                assertFalse(instructionName.contains(forbidden),
                        name + " run root contains " + instructionName);
            }
            for (int index = 0; index < missPath.length; index++) {
                if (instructionName.contains(MISS_PATH.get(index))) {
                    missPath[index]++;
                }
            }
        }
        for (int index = 0; index < missPath.length; index++) {
            System.out.println(
                    "I115_N_" + MISS_PATH.get(index) + "_COUNT=" + missPath[index]);
        }
        assertTrue(compact, name + " run root must take the compact lowerer");
        // A discarded Boolean control stores its result in no local.
        boolean consumedLocal = false;
        for (LocalVariable local : run.getBytecodeNode().getLocals()) {
            consumedLocal |= "compactBooleanResult".equals(local.getName());
        }
        System.out.println("I115_N_RESULT_LOCAL=" + consumedLocal);
        return new Census(roots.size(), runCount, total, consumedLocal);
    }
}
