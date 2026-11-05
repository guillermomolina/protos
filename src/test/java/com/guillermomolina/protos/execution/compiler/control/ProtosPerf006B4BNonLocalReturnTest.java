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
 * the specific language governing rights and limitations under the LICENSE.
 */

package com.guillermomolina.protos.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.parser.ProtosParser;
import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosActorExecutionDomain;
import com.guillermomolina.protos.runtime.ProtosActorModuleState;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosFutureValue;
import com.guillermomolina.protos.runtime.ProtosIntegerValue;
import com.guillermomolina.protos.runtime.control.ProtosNonLocalReturnException;
import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.control.ProtosReturnHome;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.guillermomolina.protos.runtime.ProtosTask;
import com.guillermomolina.protos.semantic.Canonicalizer;
import com.guillermomolina.protos.semantic.ast.CanonicalClosure;
import com.guillermomolina.protos.semantic.ast.CanonicalSequence;
import com.oracle.truffle.api.TruffleLanguage.LanguageReference;
import com.oracle.truffle.api.source.Source;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.graalvm.polyglot.Context;
import org.junit.jupiter.api.Test;

final class ProtosPerf006B4BNonLocalReturnTest {
    private static final LanguageReference<ProtosLanguage> LANGUAGE_REF =
            LanguageReference.create(ProtosLanguage.class);

    @Test
    void ownedBytecodeClosureConsumesDirectCanonicalReturn() throws Exception {
        try (LanguageScope scope = languageScope()) {
            ProtosPrelude prelude = core();
            ProtosActorExecutionDomain domain = new ProtosActorExecutionDomain();
            ProtosActivation module = activation(prelude, domain);

            String closureCharacters = "() => ^42";
            CanonicalClosure definition = closureDefinition(closureCharacters);
            ProtosClosureExecutionPlan plan =
                    ProtosClosureExecutionPlan.bytecode(
                            definition,
                            scope.language(),
                            source(closureCharacters, "perf006-b4b-direct-closure.protos"));
            module.context().createLocalSlot(
                    "entry",
                    semanticClosure(definition, plan, module));

            Object result =
                    lowerRoot(
                                    scope.language(),
                                    "entry()",
                                    "perf006-b4b-direct-top.protos")
                            .getCallTarget()
                            .call(module);

            ProtosIntegerValue integer =
                    assertInstanceOf(ProtosIntegerValue.class, result);
            assertEquals(String.valueOf("42"), com.guillermomolina.protos.runtime.ProtosNumericValueSupport.integerDecimalText(integer));
        }

        System.out.println("PERF006_B4B_DIRECT_OWNER_CONSUMPTION=PASS");
    }

    @Test
    void capturedHomeReturnPropagatesExactTargetAndPayload() throws Exception {
        try (LanguageScope scope = languageScope()) {
            ProtosPrelude prelude = core();
            ProtosActorExecutionDomain domain = new ProtosActorExecutionDomain();
            ProtosActivation creator = activation(prelude, domain);
            ProtosObjectValue payload =
                    new ProtosObjectValue(ProtosObjectValue.rootObject());
            creator.context().createLocalSlot("payload", payload);

            String closureCharacters = "() => ^payload";
            CanonicalClosure definition = closureDefinition(closureCharacters);
            ProtosClosureExecutionPlan plan =
                    ProtosClosureExecutionPlan.bytecode(
                            definition,
                            scope.language(),
                            source(closureCharacters, "perf006-b4b-captured-home.protos"));
            ProtosReturnHome capturedHome = new ProtosReturnHome();
            ProtosClosureValue closure =
                    semanticClosureWithHome(
                            definition,
                            plan,
                            creator,
                            capturedHome);
            ProtosActivation invocation =
                    ProtosActivation.forClosureInvocation(
                            closure,
                            List.of(),
                            prelude,
                            creator.actorModuleState(),
                            creator.currentModuleKey().orElse(null),
                            domain);

            ProtosNonLocalReturnException transfer =
                    assertThrows(
                            ProtosNonLocalReturnException.class,
                            () -> plan.executeBody(invocation));

            assertSame(capturedHome, transfer.target());
            assertSame(payload, transfer.value());
            assertTrue(capturedHome.isActive());
            assertFalse(invocation.ownsReturnHome());
        }

        System.out.println("PERF006_B4B_EXACT_RETURN_HOME=PASS");
        System.out.println("PERF006_B4B_EXACT_RETURN_PAYLOAD=PASS");
        System.out.println("PERF006_B4B_MISMATCH_PROPAGATION=PASS");
    }

    /**
     * PLAT070: a Task-free invocation returning to a Task-free home keeps the synchronous
     * non-local-return semantics: the original transfer, carrying only its target and value.
     */
    @Test
    void taskFreeTransferToATaskFreeHomeIsThePlainOriginalTransfer() throws Exception {
        try (LanguageScope scope = languageScope()) {
            ProtosPrelude prelude = core();
            ProtosActorExecutionDomain domain = new ProtosActorExecutionDomain();
            ProtosActivation creator = activation(prelude, domain);

            String closureCharacters = "() => ^42";
            CanonicalClosure definition = closureDefinition(closureCharacters);
            ProtosClosureExecutionPlan plan =
                    ProtosClosureExecutionPlan.bytecode(
                            definition,
                            scope.language(),
                            source(closureCharacters, "plat070-no-task-closure.protos"));
            ProtosReturnHome capturedHome = new ProtosReturnHome();
            ProtosActivation invocation =
                    ProtosActivation.forClosureInvocation(
                            semanticClosureWithHome(definition, plan, creator, capturedHome),
                            List.of(),
                            prelude,
                            creator.actorModuleState(),
                            creator.currentModuleKey().orElse(null),
                            domain);

            ProtosNonLocalReturnException transfer =
                    assertThrows(
                            ProtosNonLocalReturnException.class,
                            () -> plan.executeBody(invocation));
            assertSame(capturedHome, transfer.target());
            assertNull(capturedHome.ownerTaskForRuntime(), "a captured home is never rebound");
            assertTrue(
                    java.util.Arrays.stream(
                                    ProtosNonLocalReturnException.class.getDeclaredFields())
                            .filter(field -> !java.lang.reflect.Modifier.isStatic(
                                    field.getModifiers()))
                            .map(java.lang.reflect.Field::getName)
                            .allMatch(name -> name.equals("target") || name.equals("value")),
                    "the transfer carries only its semantic state");
        }

        System.out.println("PLAT070_TASK_FREE_NLR=ORIGINAL_TRANSFER");
    }

    /**
     * PLAT070 / D177: in a Task, a ^ whose home is not owned by that Task signals the standard
     * InvalidReturn at the ^ itself, so the Task fails with an ordinary guest Error whose trace
     * starts at the raising site.
     */
    @Test
    void unreachableHomeFailsTheTaskWithInvalidReturnAndSourceSiteTrace() throws Exception {
        try (LanguageScope scope = languageScope()) {
            ProtosPrelude prelude = core();
            ProtosActorExecutionDomain domain = new ProtosActorExecutionDomain();
            ProtosActivation module = activation(prelude, domain);

            String closureCharacters = "() => ^42";
            String closureName = "plat070-unreachable-home-closure.protos";
            CanonicalClosure definition = closureDefinition(closureCharacters);
            ProtosClosureExecutionPlan plan =
                    ProtosClosureExecutionPlan.bytecode(
                            definition,
                            scope.language(),
                            source(closureCharacters, closureName));
            ProtosReturnHome foreignHome = new ProtosReturnHome();
            module.context().createLocalSlot(
                    "entry",
                    semanticClosureWithHome(definition, plan, module, foreignHome));

            ProtosBytecodeRootNode root =
                    lowerRoot(scope.language(), "entry()", "plat070-unreachable-home-top.protos");
            ProtosTask task =
                    domain.createTask(
                            null,
                            current ->
                                    ProtosBytecodeTaskExecution.execute(
                                            current, root.getCallTarget(), module));
            assertTrue(domain.dispatchOne());

            assertEquals(ProtosTask.State.FAILED, task.state());
            ProtosObjectValue failure =
                    assertInstanceOf(ProtosObjectValue.class, task.failure().orElseThrow());
            assertSame(prelude.invalidReturnPrototype(), failure.parent().orElseThrow());
            assertTrue(foreignHome.isActive(), "a home owned by no frame is never completed");
            com.guillermomolina.protos.runtime.diagnostics.ProtosDiagnosticTrace trace =
                    task.failureDiagnosticTraceForRuntime().orElseThrow();
            assertFalse(trace.frames().isEmpty());
            assertEquals(closureName, trace.frames().get(0).sourceName());
            assertEquals(1, trace.frames().get(0).line());
        }

        System.out.println("PLAT070_TASK_ESCAPED_NLR_INVALID_RETURN=PASS");
        System.out.println("PLAT070_TASK_ESCAPED_NLR_SOURCE_SITE_TRACE=PASS");
    }

    /** PLAT070: many valid Task-backed returns complete with exact values and no failure. */
    @Test
    void manyValidTaskBackedNonLocalReturnsCompleteWithExactValues() throws Exception {
        int iterations = 5000;
        List<Long> values = new java.util.ArrayList<>();
        int[] remaining = {iterations};
        ProtosActivation module =
                new ProtosCoreBootstrap()
                        .bootstrap(Path.of("protos", "lib", "core"))
                        .newModuleActivation();
        module.context().createLocalSlot(
                "more",
                nativeClosure(
                        (activation, supplied) ->
                                remaining[0]-- > 0
                                        ? com.guillermomolina.protos.runtime.ProtosBooleanValue.TRUE
                                        : com.guillermomolina.protos.runtime.ProtosBooleanValue.FALSE));
        module.context().createLocalSlot(
                "record",
                nativeClosure(
                        (activation, supplied) -> {
                            values.add(Long.parseLong(
                                    com.guillermomolina.protos.runtime.ProtosNumericValueSupport
                                            .integerDecimalText(supplied.get(0))));
                            return ProtosNullValue.INSTANCE;
                        }));

        ProtosExecutionOutcome outcome =
                ProtosTestExecutionSupport.execute(
                        "plat070-many-valid-returns.protos",
                        """
                        hit: () => {
                            (() => { ^3 })()
                            0
                        }
                        more.whileTrue(() => {
                            record(hit())
                        })
                        """,
                        module);

        assertEquals(
                ProtosExecutionOutcome.State.COMPLETED,
                outcome.state(),
                () -> "error=" + outcome.error());
        assertEquals(iterations, values.size());
        assertTrue(values.stream().allMatch(value -> value == 3L));
        System.out.println("PLAT070_MANY_VALID_TASK_RETURNS=PASS");
    }

    /**
     * PLAT070 architecture: a valid ^ performs no stack inspection. RaiseNonLocalReturn has no
     * path to TruffleStackTrace, frame iteration or diagnostic capture, and the removed
     * escaped-return carriers no longer exist.
     */
    @Test
    void validRaiseNonLocalReturnHasNoStackInspectionPath() throws Exception {
        String root =
                java.nio.file.Files.readString(
                        Path.of(
                                "src/main/java/com/guillermomolina/protos/execution/bytecode/"
                                        + "activation/ProtosBytecodeRootNode.java"),
                        java.nio.charset.StandardCharsets.UTF_8);
        int start = root.indexOf("public static final class RaiseNonLocalReturn {");
        assertTrue(start > 0);
        int end = root.indexOf("\n    }\n", start);
        String operation = root.substring(start, end);
        for (String forbidden :
                List.of(
                        "TruffleStackTrace",
                        "fillIn",
                        "iterateFrames",
                        "ProtosDiagnosticTraceCapture",
                        "BytecodeLocation",
                        "EscapedReturn")) {
            assertFalse(operation.contains(forbidden), forbidden);
        }
        assertTrue(operation.contains("ownerTaskForRuntime()"));
        for (String removed :
                List.of(
                        "com.guillermomolina.protos.runtime.diagnostics.ProtosEscapedReturnOrigin",
                        "com.guillermomolina.protos.runtime.diagnostics.ProtosEscapedReturnTrace")) {
            assertThrows(ClassNotFoundException.class, () -> Class.forName(removed));
        }
        System.out.println("PLAT070_VALID_TASK_NONLOCAL_RETURN_STACK_WALK=NONE");
    }

    @Test
    void completedCapturedHomeStillSignalsInvalidReturn() throws Exception {
        try (LanguageScope scope = languageScope()) {
            ProtosPrelude prelude = core();
            ProtosActorExecutionDomain domain = new ProtosActorExecutionDomain();
            ProtosActivation creator = activation(prelude, domain);

            String closureCharacters = "() => ^42";
            CanonicalClosure definition = closureDefinition(closureCharacters);
            ProtosClosureExecutionPlan plan =
                    ProtosClosureExecutionPlan.bytecode(
                            definition,
                            scope.language(),
                            source(closureCharacters, "perf006-b4b-invalid-return.protos"));
            ProtosReturnHome completedHome = new ProtosReturnHome();
            completedHome.complete();
            ProtosClosureValue closure =
                    semanticClosureWithHome(
                            definition,
                            plan,
                            creator,
                            completedHome);
            ProtosActivation invocation =
                    ProtosActivation.forClosureInvocation(
                            closure,
                            List.of(),
                            prelude,
                            creator.actorModuleState(),
                            creator.currentModuleKey().orElse(null),
                            domain);

            ProtosSignalException signal =
                    assertThrows(
                            ProtosSignalException.class,
                            () -> plan.executeBody(invocation));

            assertSame(
                    prelude.invalidReturnPrototype(),
                    signal.error().parent().orElseThrow());
        }

        System.out.println("PERF006_B4B_INVALID_RETURN=PASS");
    }

    @Test
    void canonicalReturnAfterFutureSuspensionResumesToOwningPreparedCallWithoutReplay()
            throws Exception {
        try (LanguageScope scope = languageScope()) {
            ProtosPrelude prelude = core();
            ProtosActorExecutionDomain domain = new ProtosActorExecutionDomain();
            ProtosActivation creator = activation(prelude, domain);
            ProtosFutureValue future =
                    new ProtosFutureValue(prelude.futurePrototype(), domain);
            AtomicInteger prefixExecutions = new AtomicInteger();
            ProtosClosureValue probe =
                    ProtosClosureValue.suspensionCapableNativeClosure(
                            (activation, supplied) -> {
                                prefixExecutions.incrementAndGet();
                                return ProtosNullValue.INSTANCE;
                            },
                            (activation, supplied) -> {
                                prefixExecutions.incrementAndGet();
                                return ProtosNullValue.INSTANCE;
                            });
            creator.context().createLocalSlot("f", future);
            creator.context().createLocalSlot("probe", probe);

            String closureCharacters = "() => { probe()\n^f.value() }";
            CanonicalClosure definition = closureDefinition(closureCharacters);
            ProtosClosureExecutionPlan plan =
                    ProtosClosureExecutionPlan.bytecode(
                            definition,
                            scope.language(),
                            source(closureCharacters, "perf006-b4b-resume-closure.protos"));
            creator.context().createLocalSlot(
                    "entry",
                    semanticClosure(definition, plan, creator));

            ProtosBytecodeRootNode top =
                    lowerRoot(
                            scope.language(),
                            "entry()",
                            "perf006-b4b-resume-top.protos");
            ProtosTask task =
                    domain.createTask(
                            null,
                            current ->
                                    ProtosBytecodeTaskExecution.execute(
                                            current,
                                            top.getCallTarget(),
                                            creator));

            assertTrue(domain.dispatchOne());
            assertEquals(ProtosTask.State.SUSPENDED, task.state());
            assertEquals(1, prefixExecutions.get());

            ProtosIntegerValue resolved =
                    new ProtosIntegerValue(73);
            assertTrue(future.resolve(resolved, creator));
            assertEquals(ProtosTask.State.RUNNABLE, task.state());

            assertTrue(domain.dispatchOne());
            assertEquals(ProtosTask.State.COMPLETED, task.state());
            assertSame(resolved, task.result().orElseThrow());
            assertEquals(
                    1,
                    prefixExecutions.get(),
                    "completed prefix before the Future suspension must not replay");
        }

        System.out.println("PERF006_B4B_RETURN_AFTER_CPRIME_RESUME=PASS");
        System.out.println("PERF006_B4B_COMPLETED_PREFIX_REPLAY=NO");
    }

    @Test
    void defaultExpressionReturnUsesTheInvocationReturnHome() throws Exception {
        try (LanguageScope scope = languageScope()) {
            ProtosPrelude prelude = core();
            ProtosActorExecutionDomain domain = new ProtosActorExecutionDomain();
            ProtosActivation module = activation(prelude, domain);

            String closureCharacters = "(x: ^41) => 99";
            CanonicalClosure definition = closureDefinition(closureCharacters);
            ProtosClosureExecutionPlan plan =
                    ProtosClosureExecutionPlan.bytecode(
                            definition,
                            scope.language(),
                            source(closureCharacters, "perf006-b4b-default-return.protos"));
            module.context().createLocalSlot(
                    "entry",
                    semanticClosure(definition, plan, module));

            Object result =
                    lowerRoot(
                                    scope.language(),
                                    "entry()",
                                    "perf006-b4b-default-return-top.protos")
                            .getCallTarget()
                            .call(module);

            ProtosIntegerValue integer =
                    assertInstanceOf(ProtosIntegerValue.class, result);
            assertEquals(String.valueOf("41"), com.guillermomolina.protos.runtime.ProtosNumericValueSupport.integerDecimalText(integer));
        }

        System.out.println("PERF006_B4B_DEFAULT_RETURN_HOME=PASS");
    }

    private static ProtosClosureValue nativeClosure(
            com.guillermomolina.protos.runtime.callables.ProtosNativeClosureBody body) {
        return ProtosClosureValue.suspensionCapableNativeClosure(body, body);
    }

    private static LanguageScope languageScope() {
        Context context = Context.newBuilder(ProtosLanguage.ID).build();
        context.initialize(ProtosLanguage.ID);
        context.enter();
        return new LanguageScope(context, LANGUAGE_REF.get(null));
    }

    private static ProtosPrelude core() throws Exception {
        return new ProtosCoreBootstrap()
                .bootstrap(Path.of("protos", "lib", "core"));
    }

    private static ProtosActivation activation(
            ProtosPrelude prelude,
            ProtosActorExecutionDomain domain) {
        return prelude.newModuleActivation(
                new ProtosActorModuleState(),
                null,
                prelude.newExecutionContext(),
                domain);
    }

    private static ProtosClosureValue semanticClosure(
            CanonicalClosure definition,
            ProtosClosureExecutionPlan plan,
            ProtosActivation creator) {
        return new ProtosClosureValue(
                definition,
                creator.lexicalContextsForClosureCapture(),
                creator.receiver(),
                creator.methodHome().orElse(null),
                creator.returnHome().orElse(null),
                creator.prelude().orElseThrow(),
                plan);
    }

    private static ProtosClosureValue semanticClosureWithHome(
            CanonicalClosure definition,
            ProtosClosureExecutionPlan plan,
            ProtosActivation creator,
            ProtosReturnHome returnHome) {
        return new ProtosClosureValue(
                definition,
                creator.lexicalContextsForClosureCapture(),
                creator.receiver(),
                creator.methodHome().orElse(null),
                returnHome,
                creator.prelude().orElseThrow(),
                plan);
    }

    private static CanonicalClosure closureDefinition(String characters) {
        CanonicalSequence sequence = canonicalize(characters);
        assertEquals(1, sequence.expressions().size());
        return assertInstanceOf(
                CanonicalClosure.class,
                sequence.expressions().get(0));
    }

    private static CanonicalSequence canonicalize(String characters) {
        return (CanonicalSequence)
                new Canonicalizer()
                        .canonicalize(
                                new ProtosParser(characters)
                                        .parseProgram());
    }

    private static Source source(String characters, String name) throws Exception {
        return Source.newBuilder(ProtosLanguage.ID, characters, name).build();
    }

    private static ProtosBytecodeRootNode lowerRoot(
            ProtosLanguage language,
            String characters,
            String sourceName)
            throws Exception {
        Source source = source(characters, sourceName);
        return new CanonicalToBytecodeLowerer(language, source)
                .lowerRoot(canonicalize(characters));
    }

    private record LanguageScope(
            Context context,
            ProtosLanguage language)
            implements AutoCloseable {
        @Override
        public void close() {
            context.leave();
            context.close();
        }
    }
}
