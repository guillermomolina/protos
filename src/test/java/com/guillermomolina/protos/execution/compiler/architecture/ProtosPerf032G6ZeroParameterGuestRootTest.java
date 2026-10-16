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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosArrayValue;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosIntegerValue;
import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosTask;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.bytecode.Instruction;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.source.Source;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.Test;

/**
 * PERF032-G6 introduced an arity-rejection root for source Closures declaring no parameters.
 * D203/I105 retires it: ordinary Closure binding accepts every supplied argument count
 * ({@code CALLABLES.md}, Normative parameter-binding algorithm), so a zero-parameter Closure
 * always enters its single ordinary root, excess arguments are ignored, and an absent
 * parameter binds canonical null. These tests pin that contract on the compact, rich,
 * Task-owned, guest, DEBUG and host-embedding entry paths with the unchanged B5 header.
 */
final class ProtosPerf032G6ZeroParameterGuestRootTest {
    private static final Path CORE = Path.of("protos", "lib", "core");
    private static final String UPPER_BOUND_CHECK = "ArgumentUpperBound";

    @Test
    void normalRootHasNoArityCheckAndIsTheDirectSemanticTarget() throws Exception {
        withCore(module -> {
            ProtosClosureValue one = parsedClosure("() => { 1 }", "g6-root.protos", module);
            ProtosSemanticBytecodeRootNode root = planRoot(one);

            assertNone(instructionNames(root), UPPER_BOUND_CHECK);
            assertSame(root, target(one).getRootNode(), "no universal wrapper root");
            assertEquals(
                    2,
                    root.getRootNodes().count(),
                    "module and Closure roots only; no arity-rejection root is lowered");
        });
        System.out.println("I105_ZERO_PARAMETER_ROOT_WITHOUT_ARITY_CHECK=PASS");
    }

    @Test
    void validCallsStayCompactOnTheNormalTarget() throws Exception {
        withCore(module -> {
            ProtosClosureValue one = parsedClosure("() => { 1 }", "g6-valid.protos", module);
            for (int index = 0; index < 4; index++) {
                ProtosBytecodeRootNode.PreparedClosureCall prepared = fastDirect(one, module);
                assertSame(target(one), prepared.bodyTarget());
                assertEquals("1", integerValue(enter(prepared)));
                assertSame(one, prepared.targetArguments()[0], "no activation was materialized");
                assertNull(prepared.taskForRuntime(), "no Task was initialized");
            }
            assertTrue(module.task().isEmpty());
        });
        System.out.println("PERF032_G6_VALID_CALL_COMPACT=PASS");
    }

    @Test
    void suppliedArgumentsEnterTheBodyRootCompactly() throws Exception {
        withCore(module -> {
            ProtosClosureValue one = parsedClosure("() => { 1 }", "g6-excess.protos", module);

            ProtosIntegerValue supplied = integer(5);
            ProtosBytecodeRootNode.PreparedClosureCall single = fastDirect(one, module, supplied);
            assertSame(target(one), single.bodyTarget());
            assertEquals(ProtosFrameArguments.HEADER_LENGTH + 1, single.targetArguments().length);
            assertSame(supplied, single.targetArguments()[ProtosFrameArguments.HEADER_LENGTH]);
            assertEquals("1", integerValue(enter(single)));
            assertSame(one, single.targetArguments()[0], "excess arguments materialize nothing");

            ProtosBytecodeRootNode.PreparedClosureCall several =
                    fastDirect(one, module, integer(1), integer(2), integer(3));
            assertSame(target(one), several.bodyTarget());
            assertEquals("1", integerValue(enter(several)));
        });
        System.out.println("I105_EXCESS_ARGUMENTS_IGNORED_COMPACT=PASS");
    }

    @Test
    void richActivationAndPlanExecutionIgnoreExcessArguments() throws Exception {
        withCore(module -> {
            ProtosClosureValue one = parsedClosure("() => { 1 }", "g6-rich.protos", module);
            ProtosClosureExecutionPlan plan = one.executionPlan().orElseThrow();

            assertEquals("1", integerValue(plan.executeBytecodeActivationForTesting(richActivation(one, module))));
            assertEquals(
                    "1",
                    integerValue(plan.executeBytecodeActivationForTesting(
                            richActivation(one, module, integer(1)))));

            ProtosActivation rich = richActivation(one, module, integer(2));
            ProtosBytecodeRootNode.PreparedClosureCall prepared =
                    ProtosBytecodeRootNode.PreparedClosureCall.ordinary(target(one), rich);
            assertSame(target(one), prepared.bodyTarget());
            assertEquals("1", integerValue(enter(prepared)));
            assertSame(rich, prepared.targetArguments()[0], "no second activation");

            ProtosClosureValue pair =
                    parsedClosure("(x, y) => { [x, y] }", "g6-rich-pair.protos", module);
            List<Object> bound =
                    elements(pair.executionPlan().orElseThrow()
                            .executeBytecodeActivationForTesting(
                                    richActivation(pair, module, integer(7))));
            assertEquals("7", integerValue(bound.get(0)));
            assertSame(ProtosNullValue.INSTANCE, bound.get(1), "an absent argument binds null");
        });
        System.out.println("I105_RICH_ACTIVATION_FLEXIBLE_ARITY=PASS");
    }

    @Test
    void guestCallsAndSendsBindFlexiblyAndEvaluateEveryArgument() throws Exception {
        ProtosExecutionOutcome outcome =
                execute(
                        "g6-guest.protos",
                        """
                        n: 0
                        effects: 0
                        tick: () => {
                            effects = effects + 1
                            effects
                        }
                        bump: () => { n = n + 1 }
                        pair: (x, y) => { [x, y] }
                        o: Object {
                            m: () => { n = n + 1 }
                        }
                        bump(1)
                        bump(1, tick())
                        o.m(tick())
                        [n, effects, pair(), pair(7), pair(7, 8, tick())]
                        """);
        assertEquals(ProtosExecutionOutcome.State.COMPLETED, outcome.state(), outcome::toString);
        List<Object> results = elements(outcome.value());

        assertEquals("3", integerValue(results.get(0)), "every call ran its body");
        // Read before pair(7, 8, tick()) evaluates its own ignored argument.
        assertEquals("2", integerValue(results.get(1)), "ignored arguments were evaluated");
        assertEquals(
                List.of(ProtosNullValue.INSTANCE, ProtosNullValue.INSTANCE),
                elements(results.get(2)));
        assertEquals("7", integerValue(elements(results.get(3)).get(0)));
        assertSame(ProtosNullValue.INSTANCE, elements(results.get(3)).get(1));
        assertEquals("8", integerValue(elements(results.get(4)).get(1)));
        System.out.println("I105_GUEST_DIRECT_CALL_FLEXIBLE=PASS");
        System.out.println("I105_METHOD_SEND_FLEXIBLE=PASS");
        System.out.println("I105_EXCESS_ARGUMENT_EVALUATED=PASS");
    }

    @Test
    void debugAndNormalProfilesAgree() {
        String program =
                """
                effects: 0
                tick: () => {
                    effects = effects + 1
                    effects
                }
                f: (a: 10, b, c: 30, d) => { [a, b, c, d] }
                g: (x: tick()) => { x }
                tail: (a: 10, ...rest) => { rest }
                one: () => { 1 }
                full: f(1, 2, 3, 4, tick())
                none: f()
                defaulted: g()
                explicitNull: g(null)
                rest: tail(7, 8, 9)
                restFrozen: Error.handle(() => {
                    rest[0] = 0
                    false
                }, (caught) => { true })
                (none[0] == 10) && (none[1] === null) && (none[2] == 30) && (none[3] === null) &&
                    (full[3] == 4) && (effects == 2) && (defaulted == 2) &&
                    (explicitNull === null) && (rest.size() == 2) && restFrozen &&
                    (tail().size() == 0) && (one(1, 2) == 1)
                """;
        for (boolean debug : new boolean[] {false, true}) {
            Context.Builder builder =
                    Context.newBuilder(ProtosLanguage.ID)
                            .option("protos.CoreRoot", CORE.toAbsolutePath().toString());
            if (debug) {
                builder.allowExperimentalOptions(true).option("protos.Debug", "true");
            }
            try (Context context = builder.build()) {
                assertTrue(
                        context.eval(ProtosLanguage.ID, program).asBoolean(),
                        debug ? "DEBUG" : "NORMAL");
            }
        }
        System.out.println("I105_DEBUG_NORMAL_PARITY=PASS");
    }

    @Test
    void reparseKeepsTheSingleRoot() throws Exception {
        withCore(module -> {
            ProtosClosureValue one = parsedClosure("() => { 1 }", "g6-source.protos", module);
            ProtosSemanticBytecodeRootNode root = planRoot(one);

            // Materializing source information reparses the whole group.
            root.getRootNodes().ensureSourceInformation();
            assertNotNull(root.getSourceSection());
            assertSame(root, planRoot(one), "the normal root identity survives the reparse");
            assertEquals(2, root.getRootNodes().count(), "module and Closure roots only");
            assertNone(instructionNames(root), UPPER_BOUND_CHECK);
            assertEquals("1", integerValue(enter(fastDirect(one, module, integer(3)))));
        });
        System.out.println("I105_SOURCE_SECTION_AND_REPARSE=PASS");
    }

    @Test
    void taskOwnedEntryIgnoresExcessArguments() throws Exception {
        withCore(module -> {
            ProtosClosureValue one = parsedClosure("() => { 1 }", "g6-task.protos", module);

            ProtosTask valid = runTaskOwned(one, module, List.of(), new AtomicReference<>());
            assertEquals(ProtosTask.State.COMPLETED, valid.state());
            assertEquals("1", integerValue(valid.result().orElseThrow()));

            AtomicReference<ProtosBytecodeRootNode.PreparedClosureCall> prepared =
                    new AtomicReference<>();
            ProtosTask excess = runTaskOwned(one, module, List.of(integer(1)), prepared);
            assertEquals(ProtosTask.State.COMPLETED, excess.state());
            assertEquals("1", integerValue(excess.result().orElseThrow()));
            assertSame(target(one), prepared.get().bodyTarget());
            assertTrue(module.task().isEmpty());
        });
        System.out.println("I105_TASK_OWNED_CONTRACT=PASS");
    }

    @Test
    void closuresWithParametersEmitNoUpperBoundCheck() throws Exception {
        withCore(module -> {
            for (String characters :
                    List.of(
                            "(x) => { x }",
                            "(x, y) => { y }",
                            "(x: 1) => { x }",
                            "(x: 1, y) => { y }",
                            "(...r) => { r }")) {
                ProtosClosureValue closure = parsedClosure(characters, "g6-params.protos", module);
                assertNone(instructionNames(planRoot(closure)), UPPER_BOUND_CHECK);
            }
        });

        ProtosExecutionOutcome outcome =
                execute(
                        "g6-defaults.protos",
                        """
                        d: (a: 7) => { a }
                        r: (a: 1, ...rest) => { [a, rest] }
                        [d(), d(3), r(), r(10, 20), d(1, 2)]
                        """);
        assertEquals(ProtosExecutionOutcome.State.COMPLETED, outcome.state(), outcome::toString);
        List<Object> results = elements(outcome.value());
        assertEquals("7", integerValue(results.get(0)));
        assertEquals("3", integerValue(results.get(1)));
        assertEquals("1", integerValue(elements(results.get(2)).get(0)));
        assertTrue(elements(elements(results.get(2)).get(1)).isEmpty());
        assertEquals("10", integerValue(elements(results.get(3)).get(0)));
        assertEquals("20", integerValue(elements(elements(results.get(3)).get(1)).get(0)));
        assertEquals("1", integerValue(results.get(4)), "the excess argument is ignored");
        System.out.println("I105_PARAMETER_CLOSURES_WITHOUT_UPPER_BOUND=PASS");
        System.out.println("I105_DEFAULTS_AND_REST=PASS");
    }

    @Test
    void standardEmbeddingExecuteBindsFlexibly() {
        try (Context context =
                Context.newBuilder(ProtosLanguage.ID)
                        .option("protos.CoreRoot", CORE.toAbsolutePath().toString())
                        .build()) {
            context.eval(
                    ProtosLanguage.ID,
                    """
                    run: () => {
                        1
                    }
                    second: (a, b) => { b }
                    0
                    """);
            Value run = context.getBindings(ProtosLanguage.ID).getMember("run");
            Value second = context.getBindings(ProtosLanguage.ID).getMember("second");
            for (int index = 0; index < 3; index++) {
                assertEquals(1, run.execute().asInt());
                assertEquals(1, run.execute(index).asInt());
            }
            assertTrue(second.execute(5).isNull(), "an absent host argument binds null");
            assertEquals(6, second.execute(5, 6, 7).asInt());
            assertTrue(
                    languageContext(context).embeddedProcessOrNull().isLive(),
                    "flexible host calls signal no Error");
        }
        System.out.println("I105_STANDARD_EMBEDDING_FLEXIBLE=PASS");
    }

    private interface CoreTest {
        void run(ProtosActivation module) throws Exception;
    }

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

    private static ProtosLanguageContext languageContext(Context context) {
        context.enter();
        try {
            return ProtosLanguageContext.current();
        } finally {
            context.leave();
        }
    }

    private static ProtosExecutionOutcome execute(String name, String source) throws Exception {
        return ProtosTestExecutionSupport.execute(
                name, source, new ProtosCoreBootstrap().bootstrap(CORE).newModuleActivation());
    }

    private static ProtosTask runTaskOwned(
            ProtosClosureValue closure,
            ProtosActivation module,
            List<?> supplied,
            AtomicReference<ProtosBytecodeRootNode.PreparedClosureCall> preparedRef) {
        ProtosTask task =
                module.executionDomain()
                        .createTask(
                                null,
                                current -> {
                                    ProtosBytecodeRootNode.PreparedClosureCall prepared =
                                            ProtosBytecodeRootNode
                                                    .prepareTaskOwnedDirectClosureIfBytecode(
                                                            closure, supplied, module, current);
                                    preparedRef.set(prepared);
                                    ProtosBytecodeTaskExecution.executePreparedClosure(
                                            current, prepared);
                                });
        assertTrue(module.executionDomain().dispatchOne());
        return task;
    }

    private static ProtosActivation richActivation(
            ProtosClosureValue closure, ProtosActivation module, Object... supplied) {
        ProtosActivation activation =
                ProtosActivation.forClosureInvocation(
                        closure,
                        List.of(supplied),
                        module.prelude().orElseThrow(),
                        module.actorModuleState(),
                        module.currentModuleKey().orElse(null),
                        module.executionDomain());
        activation.inheritDynamicControlState(module);
        return activation;
    }

    private static ProtosSemanticBytecodeRootNode planRoot(ProtosClosureValue closure) {
        return closure.executionPlan().orElseThrow().bytecodeActivationRootForTesting();
    }

    private static RootCallTarget target(ProtosClosureValue closure) {
        return ProtosBytecodeRootNode.PrepareSendArguments.fastOrdinarySendTarget(
                closure, ProtosLanguageContext.currentIfEnteredForRuntime());
    }

    private static ProtosBytecodeRootNode.PreparedClosureCall fastDirect(
            ProtosClosureValue closure, ProtosActivation caller, Object... supplied) {
        ProtosLanguageContext entered = ProtosLanguageContext.currentIfEnteredForRuntime();
        ProtosClosureValue selected =
                ProtosBytecodeRootNode.directClosureCallSelectionOrNull(closure, caller);
        assertSame(closure, selected, "canonical direct Closure-call selection");
        return ProtosBytecodeRootNode.PrepareClosureCallArguments.fastDirect(
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

    private static Object enter(ProtosBytecodeRootNode.PreparedClosureCall prepared) {
        Object entered =
                ProtosBytecodeRootNode.EnterClosureCall.ordinaryIndirect(
                        assertInstanceOf(ProtosBytecodeRootNode.OrdinarySourceCall.class, prepared),
                        IndirectCallNode.create());
        return prepared.finish(entered);
    }

    private static Object evaluate(String characters, String name, ProtosActivation activation) {
        Source source =
                Source.newBuilder(ProtosLanguage.ID, characters, name)
                        .mimeType(ProtosLanguage.MIME_TYPE)
                        .build();
        CallTarget target = ProtosLanguageContext.current().parsePublic(source);
        return target.call(activation);
    }

    private static ProtosClosureValue parsedClosure(
            String characters, String name, ProtosActivation activation) {
        return assertInstanceOf(
                ProtosClosureValue.class, evaluate(characters, name, activation));
    }

    private static List<String> instructionNames(ProtosSemanticBytecodeRootNode root) {
        return root.getBytecodeNode().getInstructionsAsList().stream()
                .map(Instruction::getName)
                .toList();
    }

    private static void assertNone(List<String> names, String operation) {
        assertTrue(
                names.stream().noneMatch(name -> name.contains(operation)),
                () -> "unexpected " + operation + " in: " + names);
    }

    private static List<Object> elements(Object array) {
        return assertInstanceOf(ProtosArrayValue.class, array).indexedSnapshot();
    }

    private static ProtosIntegerValue integer(long value) {
        return new ProtosIntegerValue(value);
    }

    private static String integerValue(Object value) {
        return com.guillermomolina.protos.runtime.ProtosNumericValueSupport.integerDecimalText(value);
    }
}
