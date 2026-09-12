/*
 * THE LICENSED WORK IS PROVIDED UNDER THE TERMS OF THE ADAPTIVE PUBLIC LICENSE
 * ("LICENSE") AS FIRST COMPLETED BY: Guillermo Adrián Molina. See LICENSE.TXT.
 */
package com.guillermomolina.protos.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosFutureValue;
import com.guillermomolina.protos.runtime.ProtosIntegerValue;
import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosTask;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.source.Source;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.junit.jupiter.api.Test;

final class ProtosPerf006B6A6A1TaskOwnedClosureDispatchTest {
    private static final Path CORE = Path.of("protos", "lib", "core");

    private static final class Dependency implements ProtosTask.WaitDependency {
        private volatile ProtosTask task;
        private volatile boolean ready;

        void register(ProtosTask owner) {
            if (task != null) {
                throw new IllegalStateException("dependency registered twice");
            }
            task = owner;
        }

        @Override
        public boolean isReady() {
            return ready;
        }

        boolean complete() {
            ProtosTask owner = task;
            if (owner == null) {
                throw new IllegalStateException("dependency was never registered");
            }
            ready = true;
            return owner.resume(this);
        }
    }

    @Test
    void astTemplateClosureProjectsToContextLocalCPrimeTaskAndResumesWithoutReplay()
            throws Exception {
        try (Context context = Context.newBuilder(ProtosLanguage.ID).build()) {
            context.initialize(ProtosLanguage.ID);
            context.enter();
            try {
                ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
                ProtosActivation module = prelude.newModuleActivation();
                Dependency dependency = new Dependency();
                AtomicInteger probes = new AtomicInteger();

                module.context().createLocalSlot(
                        "probe",
                        ProtosClosureValue.nativeClosure(
                                (activation, supplied) -> {
                                    if (!supplied.isEmpty()) {
                                        throw new AssertionError("probe arity");
                                    }
                                    probes.incrementAndGet();
                                    return ProtosNullValue.INSTANCE;
                                }));
                module.context().createLocalSlot(
                        "pause",
                        ProtosClosureValue.suspensionCapableNativeClosure(
                                (activation, supplied) -> {
                                    throw new AssertionError(
                                            "projected C-prime pause used ordinary native body");
                                },
                                (activation, supplied) -> {
                                    if (!supplied.isEmpty()) {
                                        throw new AssertionError("pause arity");
                                    }
                                    ProtosTask task =
                                            activation.task()
                                                    .orElseThrow(
                                                            () ->
                                                                    new IllegalStateException(
                                                                            "pause requires owning task"));
                                    dependency.register(task);
                                    return ProtosNativeSuspension.pending(
                                            dependency,
                                            () ->
                                                    new ProtosIntegerValue(
                                                            BigInteger.valueOf(7)));
                                }));

                ProtosClosureValue closure =
                        parsedClosure(
                                "() => { probe()\npause()\n99 }",
                                "perf006-b6a6a1-task-projection.protos",
                                module);
                assertFalse(
                        closure.executionPlan().orElseThrow().isBytecodeBackendForRuntime(),
                        "public parse remains AST before B6A6");

                ProtosTask task =
                        module.executionDomain()
                                .createTask(
                                        null,
                                        current ->
                                                ProtosClosureInvoker.executeInTaskForRuntime(
                                                        closure,
                                                        List.of(),
                                                        module,
                                                        current));

                assertTrue(module.executionDomain().dispatchOne());
                assertEquals(ProtosTask.State.SUSPENDED, task.state());
                assertEquals(1, probes.get());
                assertEquals(
                        1,
                        ProtosLanguageContext.current()
                                .projectedBytecodeExecutionPlanCountForTesting());

                assertTrue(dependency.complete());
                assertTrue(module.executionDomain().dispatchOne());

                assertEquals(ProtosTask.State.COMPLETED, task.state());
                assertEquals(
                        BigInteger.valueOf(99),
                        assertInstanceOf(
                                        ProtosIntegerValue.class,
                                        task.result().orElseThrow())
                                .value());
                assertEquals(
                        1,
                        probes.get(),
                        "completed prefix must not replay after C-prime resume");
                assertFalse(
                        closure.executionPlan().orElseThrow().isBytecodeBackendForRuntime(),
                        "semantic/template Closure must remain unchanged by Context projection");
            } finally {
                context.leave();
            }
        }

        System.out.println("PERF006_B6A6A1_AST_TEMPLATE_BYTECODE_PROJECTION=PASS");
        System.out.println("PERF006_B6A6A1_TASK_CPRIME_SUSPENSION=PASS");
        System.out.println("PERF006_B6A6A1_COMPLETED_PREFIX_REPLAY=NO");
    }

    @Test
    void noEnteredContextLegacyFallbackIsReplayStableButTaskLocal()
            throws Exception {
        final ProtosClosureValue[] captured = new ProtosClosureValue[1];
        try (Context context = Context.newBuilder(ProtosLanguage.ID).build()) {
            context.initialize(ProtosLanguage.ID);
            context.enter();
            try {
                ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
                ProtosActivation module = prelude.newModuleActivation();
                captured[0] =
                        parsedClosure(
                                "() => { 40 + 2 }",
                                "perf006-b6a6a1r-no-context-fallback.protos",
                                module);
            } finally {
                context.leave();
            }
        }

        ProtosClosureValue closure = captured[0];
        ProtosClosureExecutionPlan template =
                closure.executionPlan().orElseThrow();

        com.guillermomolina.protos.runtime.ProtosEvaluatorContinuation firstTask =
                new com.guillermomolina.protos.runtime.ProtosEvaluatorContinuation();
        firstTask.beginSegment();
        ProtosClosureExecutionPlan firstPlan;
        try {
            firstPlan =
                    firstTask.replayStableLegacyAstPlan(
                            template,
                            () ->
                                    template.rebuildAstForLegacyFallback(
                                            closure.definition()));
        } finally {
            firstTask.endSegment();
        }

        firstTask.beginSegment();
        ProtosClosureExecutionPlan resumedPlan;
        try {
            resumedPlan =
                    firstTask.replayStableLegacyAstPlan(
                            template,
                            () ->
                                    template.rebuildAstForLegacyFallback(
                                            closure.definition()));
        } finally {
            firstTask.endSegment();
        }

        com.guillermomolina.protos.runtime.ProtosEvaluatorContinuation secondTask =
                new com.guillermomolina.protos.runtime.ProtosEvaluatorContinuation();
        secondTask.beginSegment();
        ProtosClosureExecutionPlan otherTaskPlan;
        try {
            otherTaskPlan =
                    secondTask.replayStableLegacyAstPlan(
                            template,
                            () ->
                                    template.rebuildAstForLegacyFallback(
                                            closure.definition()));
        } finally {
            secondTask.endSegment();
        }

        assertSame(
                firstPlan,
                resumedPlan,
                "one Task must replay through identical fallback AST nodes");
        assertNotSame(
                firstPlan,
                otherTaskPlan,
                "fallback AST must never be shared across Tasks");
        assertTrue(firstPlan.language().isEmpty());
        assertTrue(otherTaskPlan.language().isEmpty());
        assertEquals(1, firstTask.retainedLegacyAstFallbackPlanCountForTesting());
        assertEquals(1, secondTask.retainedLegacyAstFallbackPlanCountForTesting());

        System.out.println(
                "PERF006_B6A6A1R_NO_CONTEXT_AST_REPLAY_STABLE=PASS");
        System.out.println(
                "PERF006_B6A6A1R_NO_CONTEXT_AST_CROSS_TASK_SHARING=NO");
    }

    @Test
    void foreignContextAstTemplateReprojectsBeforeLegacyInvocation() throws Exception {
        try (Engine engine = Engine.create(ProtosLanguage.ID);
                ProtosPolyglotExecutionContext firstContext =
                        ProtosPolyglotExecutionContext.open(
                                engine,
                                java.io.InputStream.nullInputStream(),
                                java.io.OutputStream.nullOutputStream(),
                                java.io.OutputStream.nullOutputStream(),
                                () -> {});
                ProtosPolyglotExecutionContext secondContext =
                        ProtosPolyglotExecutionContext.open(
                                engine,
                                java.io.InputStream.nullInputStream(),
                                java.io.OutputStream.nullOutputStream(),
                                java.io.OutputStream.nullOutputStream(),
                                () -> {})) {
            final ProtosClosureValue[] firstClosure = new ProtosClosureValue[1];
            final ProtosClosureExecutionPlan[] foreignTemplate =
                    new ProtosClosureExecutionPlan[1];

            firstContext.callEntered(
                    () -> {
                        ProtosPrelude prelude;
                        try {
                            prelude = new ProtosCoreBootstrap().bootstrap(CORE);
                        } catch (java.io.IOException failure) {
                            throw new java.io.UncheckedIOException(failure);
                        }
                        ProtosActivation module = prelude.newModuleActivation();
                        ProtosClosureValue closure =
                                parsedClosure(
                                        "() => { 40 + 2 }",
                                        "perf006-b6a6a1r-foreign-ast-template.protos",
                                        module);
                        ProtosClosureExecutionPlan template =
                                closure.executionPlan().orElseThrow();
                        assertFalse(template.isBytecodeBackendForRuntime());

                        assertEquals(
                                BigInteger.valueOf(42),
                                assertInstanceOf(
                                                ProtosIntegerValue.class,
                                                ProtosClosureInvoker.invoke(
                                                        closure,
                                                        List.of(),
                                                        module))
                                        .value());
                        firstClosure[0] = closure;
                        foreignTemplate[0] = template;
                        return null;
                    });

            secondContext.callEntered(
                    () -> {
                        ProtosPrelude prelude;
                        try {
                            prelude = new ProtosCoreBootstrap().bootstrap(CORE);
                        } catch (java.io.IOException failure) {
                            throw new java.io.UncheckedIOException(failure);
                        }
                        ProtosActivation module = prelude.newModuleActivation();
                        ProtosClosureValue source = firstClosure[0];
                        ProtosClosureExecutionPlan template = foreignTemplate[0];
                        ProtosClosureValue rebound =
                                new ProtosClosureValue(
                                        source.definition(),
                                        module.lexicalContextsForClosureCapture(),
                                        module.receiver(),
                                        module.methodHome().orElse(null),
                                        module.returnHome().orElse(null),
                                        prelude,
                                        template);
                        rebound.requireContextLocalExecutionProjectionForRuntime();

                        assertEquals(
                                BigInteger.valueOf(42),
                                assertInstanceOf(
                                                ProtosIntegerValue.class,
                                                ProtosClosureInvoker.invoke(
                                                        rebound,
                                                        List.of(),
                                                        module))
                                        .value());

                        ProtosClosureExecutionPlan projected =
                                ProtosLanguageContext.current()
                                        .projectedExecutionPlanForTesting(rebound);
                        assertNotSame(template, projected);
                        assertSame(
                                ProtosLanguageContext.current().languageForTesting(),
                                projected.language().orElseThrow(),
                                "entered-Context AST fallback must be owned by that Context language");
                        assertSame(
                                template.source().orElse(null),
                                projected.source().orElse(null),
                                "entered-Context AST fallback must preserve exact Source identity");
                        return null;
                    });
        }

        System.out.println(
                "PERF006_B6A6A1R_CONTEXT_OWNED_AST_PROJECTION=PASS");
        System.out.println(
                "PERF006_B6A6A1R_TRUFFLE_SHARING_LAYER_REUSE=NO");
    }

    @Test
    void bytecodeTemplateUsesEnteredContextLegacyAstFallback() throws Exception {
        Source source =
                Source.newBuilder(
                                ProtosLanguage.ID,
                                "() => { 40 + 2 }",
                                "perf006-b6a6a1r-bytecode-template.protos")
                        .mimeType(ProtosLanguage.MIME_TYPE)
                        .build();
        try (ProtosPolyglotExecutionContext context =
                ProtosPolyglotExecutionContext.open(
                        java.io.InputStream.nullInputStream(),
                        java.io.OutputStream.nullOutputStream(),
                        java.io.OutputStream.nullOutputStream())) {
            context.callEntered(
                    () -> {
                        ProtosPrelude prelude;
                        try {
                            prelude = new ProtosCoreBootstrap().bootstrap(CORE);
                        } catch (java.io.IOException failure) {
                            throw new java.io.UncheckedIOException(failure);
                        }
                        ProtosActivation module = prelude.newModuleActivation();
                        CallTarget bytecodeRoot =
                                new ProtosSourceCompiler()
                                        .compileBytecode(
                                                source,
                                                ProtosLanguageContext.current()
                                                        .languageForTesting());
                        ProtosClosureValue closure =
                                assertInstanceOf(
                                        ProtosClosureValue.class,
                                        bytecodeRoot.call(module));
                        assertTrue(
                                closure.executionPlan()
                                        .orElseThrow()
                                        .isBytecodeBackendForRuntime());

                        Object value =
                                ProtosClosureInvoker.invoke(
                                        closure,
                                        List.of(),
                                        module);

                        assertEquals(
                                BigInteger.valueOf(42),
                                assertInstanceOf(ProtosIntegerValue.class, value).value());
                        ProtosClosureExecutionPlan projected =
                                ProtosLanguageContext.current()
                                        .projectedExecutionPlanForTesting(closure);
                        assertTrue(projected != null);
                        assertFalse(
                                projected.isBytecodeBackendForRuntime(),
                                "legacy synchronous caller must receive the temporary AST fallback");
                        assertTrue(
                                closure.executionPlan()
                                        .orElseThrow()
                                        .isBytecodeBackendForRuntime(),
                                "semantic/template Closure stays Bytecode-backed");
                        return null;
                    });
        }

        System.out.println("PERF006_B6A6A1R_LEGACY_AST_FALLBACK=PASS");
    }

    @Test
    void standardClosureFutureUsesSameTaskOwnedCPrimeDispatch() throws Exception {
        try (Context context = Context.newBuilder(ProtosLanguage.ID).build()) {
            context.initialize(ProtosLanguage.ID);
            context.enter();
            try {
                ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
                ProtosActivation module = prelude.newModuleActivation();
                AtomicInteger executions = new AtomicInteger();
                module.context().createLocalSlot(
                        "probe",
                        ProtosClosureValue.nativeClosure(
                                (activation, supplied) -> {
                                    executions.incrementAndGet();
                                    return ProtosNullValue.INSTANCE;
                                }));

                ProtosClosureValue closure =
                        parsedClosure(
                                "() => { probe()\n41 + 1 }",
                                "perf006-b6a6a1-future.protos",
                                module);

                ProtosFutureValue future =
                        assertInstanceOf(
                                ProtosFutureValue.class,
                                ProtosInvocation.invokeMessage(
                                        closure,
                                        "future",
                                        List.of(),
                                        module));

                while (future.isPending() && module.executionDomain().dispatchOne()) {}

                assertEquals(ProtosFutureValue.State.RESOLVED, future.state());
                assertEquals(
                        BigInteger.valueOf(42),
                        assertInstanceOf(
                                        ProtosIntegerValue.class,
                                        future.resolvedValue().orElseThrow())
                                .value());
                assertEquals(1, executions.get());
            } finally {
                context.leave();
            }
        }

        System.out.println("PERF006_B6A6A1_OBJECT_FUTURE_CPRIME_DISPATCH=PASS");
    }

    @Test
    void futureThenTransformUsesTaskOwnedPolymorphicCPrimeDispatch() throws Exception {
        try (Context context = Context.newBuilder(ProtosLanguage.ID).build()) {
            context.initialize(ProtosLanguage.ID);
            context.enter();
            try {
                ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
                ProtosActivation module = prelude.newModuleActivation();
                ProtosFutureValue source =
                        new ProtosFutureValue(
                                prelude.futurePrototype(),
                                module.executionDomain());
                assertTrue(
                        source.resolve(
                                new ProtosIntegerValue(BigInteger.valueOf(41)),
                                module));

                ProtosClosureValue transform =
                        parsedClosure(
                                "(x) => { x + 1 }",
                                "perf006-b6a6a1-then.protos",
                                module);

                ProtosFutureValue destination =
                        assertInstanceOf(
                                ProtosFutureValue.class,
                                ProtosInvocation.invokeMessage(
                                        source,
                                        "then",
                                        List.of(transform),
                                        module));

                while (destination.isPending()
                        && module.executionDomain().dispatchOne()) {}

                assertEquals(ProtosFutureValue.State.RESOLVED, destination.state());
                assertEquals(
                        BigInteger.valueOf(42),
                        assertInstanceOf(
                                        ProtosIntegerValue.class,
                                        destination.resolvedValue().orElseThrow())
                                .value());
            } finally {
                context.leave();
            }
        }

        System.out.println("PERF006_B6A6A1_FUTURE_THEN_CPRIME_DISPATCH=PASS");
    }

    private static ProtosClosureValue parsedClosure(
            String characters,
            String name,
            ProtosActivation activation) {
        Source source =
                Source.newBuilder(ProtosLanguage.ID, characters, name)
                        .mimeType(ProtosLanguage.MIME_TYPE)
                        .build();
        CallTarget target = ProtosLanguageContext.current().parsePublic(source);
        return assertInstanceOf(
                ProtosClosureValue.class,
                target.call(activation));
    }
}
