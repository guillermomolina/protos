/* APL-1.0 licensed work; see LICENSE.TXT. */
package com.guillermomolina.protos.execution;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosCoreErrors;
import com.guillermomolina.protos.runtime.ProtosFutureValue;
import com.guillermomolina.protos.runtime.ProtosIoReleaseExecution;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeLocal;
import com.oracle.truffle.api.bytecode.BytecodeRootNodes;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Generic PLAT030 Bytecode DSL sequence for lifecycle-release guest callbacks.
 *
 * <p>The semantic close owner remains {@link com.guillermomolina.protos.runtime.ProtosIoLifecycle}.
 * This class owns only one release execution's ordered guest sends and lower-Future waits.
 * It never creates a Task, Future, or {@code ProtosIoOperation}.
 */
public final class ProtosIoReleaseCPrimeExecution {
    public static final class Plan {
        private final RootCallTarget target;

        private Plan(RootCallTarget target) {
            this.target = Objects.requireNonNull(target, "target");
        }
    }

    public static final class Step {
        private final Object target;
        private final String selector;
        private final List<?> arguments;
        private final boolean runAfterFailure;
        private final Runnable resolvedAction;
        private final Consumer<ProtosObjectValue> failedAction;

        private Step(
                Object target,
                String selector,
                List<?> arguments,
                boolean runAfterFailure,
                Runnable resolvedAction,
                Consumer<ProtosObjectValue> failedAction) {
            this.target = Objects.requireNonNull(target, "target");
            this.selector = Objects.requireNonNull(selector, "selector");
            this.arguments = List.copyOf(Objects.requireNonNull(arguments, "arguments"));
            this.runAfterFailure = runAfterFailure;
            this.resolvedAction = Objects.requireNonNull(resolvedAction, "resolvedAction");
            this.failedAction = Objects.requireNonNull(failedAction, "failedAction");
        }

        public static Step ordinary(
                Object target,
                String selector,
                List<?> arguments,
                Runnable resolvedAction,
                Consumer<ProtosObjectValue> failedAction) {
            return new Step(
                    target, selector, arguments, false, resolvedAction, failedAction);
        }

        public static Step mandatoryFinalizer(
                Object target,
                String selector,
                List<?> arguments,
                Runnable resolvedAction,
                Consumer<ProtosObjectValue> failedAction) {
            return new Step(
                    target, selector, arguments, true, resolvedAction, failedAction);
        }
    }

    static final class TargetInvocation {
        private final boolean failed;
        private final Object result;

        private TargetInvocation(boolean failed, Object result) {
            this.failed = failed;
            this.result = result;
        }
    }

    static final class LowerOutcome {
        enum Kind {
            RESOLVED,
            FAILED,
            CANCELLED,
            INVALID,
            INVOCATION_FAILED
        }

        private final Kind kind;
        private final ProtosObjectValue error;

        private LowerOutcome(Kind kind, ProtosObjectValue error) {
            this.kind = Objects.requireNonNull(kind, "kind");
            this.error = error;
        }

        static LowerOutcome fromFuture(ProtosFutureValue future) {
            return switch (future.state()) {
                case RESOLVED -> new LowerOutcome(Kind.RESOLVED, null);
                case FAILED ->
                        new LowerOutcome(
                                Kind.FAILED,
                                future.failedError()
                                        .orElseThrow(
                                                () ->
                                                        new IllegalStateException(
                                                                "failed lifecycle-release lower Future has no Error")));
                case CANCELLED -> new LowerOutcome(Kind.CANCELLED, null);
                case PENDING ->
                        throw new IllegalStateException(
                                "lifecycle-release lower Future projected before terminalization");
            };
        }

        static LowerOutcome invalid() {
            return new LowerOutcome(Kind.INVALID, null);
        }

        static LowerOutcome invocationFailed() {
            return new LowerOutcome(Kind.INVOCATION_FAILED, null);
        }
    }

    public static final class Outcome {
        private final ProtosObjectValue error;

        private Outcome(ProtosObjectValue error) {
            this.error = error;
        }

        public ProtosObjectValue errorOrNull() {
            return error;
        }
    }

    public static final class Sequence {
        private final ProtosIoReleaseExecution release;
        private final List<Step> steps;
        private int index;
        private ProtosObjectValue primaryFailure;

        private Sequence(
                ProtosIoReleaseExecution release,
                ProtosObjectValue initialFailure,
                List<Step> steps) {
            this.release = Objects.requireNonNull(release, "release");
            this.primaryFailure = initialFailure;
            this.steps = List.copyOf(Objects.requireNonNull(steps, "steps"));
        }

        private void skipSuppressedSteps() {
            while (index < steps.size()
                    && primaryFailure != null
                    && !steps.get(index).runAfterFailure) {
                index++;
            }
        }

        boolean hasNextStep() {
            skipSuppressedSteps();
            return index < steps.size();
        }

        private Step currentStep() {
            if (!hasNextStep()) {
                throw new IllegalStateException(
                        "lifecycle release has no current guest step");
            }
            return steps.get(index);
        }

        Object target() {
            return currentStep().target;
        }

        String selector() {
            return currentStep().selector;
        }

        List<?> arguments() {
            return currentStep().arguments;
        }

        TargetInvocation invocationSucceeded(Object result) {
            return new TargetInvocation(
                    false,
                    Objects.requireNonNull(
                            result,
                            "lifecycle-release guest invocation result"));
        }

        TargetInvocation invocationFailed() {
            return new TargetInvocation(true, null);
        }

        Object awaitTargetFuture(TargetInvocation invocation) {
            Objects.requireNonNull(invocation, "invocation");
            if (invocation.failed) {
                return LowerOutcome.invocationFailed();
            }
            if (!(invocation.result instanceof ProtosFutureValue lower)) {
                return LowerOutcome.invalid();
            }
            return ProtosStandardFutureProtocol.awaitFutureForIoReleaseContinuationForRuntime(
                    lower,
                    release,
                    () -> LowerOutcome.fromFuture(lower));
        }

        void applyOutcome(LowerOutcome outcome) {
            Objects.requireNonNull(outcome, "outcome");
            Step step = currentStep();
            switch (outcome.kind) {
                case RESOLVED -> step.resolvedAction.run();
                case FAILED -> recordFailure(step, Objects.requireNonNull(outcome.error, "error"));
                case CANCELLED, INVALID, INVOCATION_FAILED ->
                        recordFailure(step, ioError());
            }
            index++;
        }

        Outcome finish() {
            skipSuppressedSteps();
            if (index != steps.size()) {
                throw new IllegalStateException(
                        "lifecycle release finished before exhausting guest steps");
            }
            return new Outcome(primaryFailure);
        }

        private void recordFailure(Step step, ProtosObjectValue error) {
            if (primaryFailure == null) {
                primaryFailure = error;
            }
            step.failedAction.accept(error);
        }

        private ProtosObjectValue ioError() {
            return ProtosCoreErrors.newOccurrence(
                    release.origin(),
                    ProtosCoreErrors.StandardError.I_O_ERROR);
        }
    }

    private ProtosIoReleaseCPrimeExecution() {}

    public static Plan planForEnteredContext() {
        ProtosLanguageContext context =
                ProtosLanguageContext.currentIfEnteredForRuntime();
        if (context == null) {
            throw new IllegalStateException(
                    "lifecycle-release C-prime execution requires an entered Protos Context");
        }
        return context.ioReleaseCPrimePlanForRuntime();
    }

    public static Plan planForEnteredContextIfAvailable() {
        ProtosLanguageContext context =
                ProtosLanguageContext.currentIfEnteredForRuntime();
        return context == null ? null : context.ioReleaseCPrimePlanForRuntime();
    }

    public static Sequence sequence(
            ProtosIoReleaseExecution release,
            ProtosObjectValue initialFailure,
            List<Step> steps) {
        return new Sequence(release, initialFailure, steps);
    }

    static Plan createPlan(ProtosLanguage language) {
        Objects.requireNonNull(language, "language");
        BytecodeRootNodes<ProtosBytecodeRootNode> roots =
                ProtosBytecodeRootNodeGen.create(
                        language,
                        BytecodeConfig.DEFAULT,
                        builder -> {
                            builder.beginRoot();

                            BytecodeLocal prepared =
                                    builder.createLocal("ioReleasePreparedCall", null);
                            BytecodeLocal child =
                                    builder.createLocal("ioReleaseChild", null);
                            BytecodeLocal resume =
                                    builder.createLocal("ioReleaseResume", null);
                            BytecodeLocal targetResult =
                                    builder.createLocal("ioReleaseTargetResult", null);
                            BytecodeLocal invocation =
                                    builder.createLocal("ioReleaseInvocation", null);
                            BytecodeLocal lowerOutcome =
                                    builder.createLocal("ioReleaseLowerOutcome", null);
                            BytecodeLocal yielded =
                                    builder.createLocal("ioReleaseYielded", null);

                            builder.beginWhile();
                            builder.beginIoReleaseHasNextStep();
                            builder.emitLoadArgument(1);
                            builder.endIoReleaseHasNextStep();

                            builder.beginBlock();

                            builder.beginTryCatch();

                            builder.beginBlock();
                            builder.beginStoreLocal(prepared);
                            builder.beginPrepareIoReleaseTargetCall();
                            builder.emitLoadArgument(1);
                            builder.emitLoadArgument(0);
                            builder.endPrepareIoReleaseTargetCall();
                            builder.endStoreLocal();

                            builder.beginTryFinally(
                                    () -> {
                                        builder.beginCompleteClosureCall();
                                        builder.emitLoadLocal(prepared);
                                        builder.endCompleteClosureCall();
                                    });
                            builder.beginBlock();

                            builder.beginStoreLocal(child);
                            builder.beginEnterClosureCall();
                            builder.emitLoadLocal(prepared);
                            builder.endEnterClosureCall();
                            builder.endStoreLocal();

                            builder.beginWhile();
                            builder.beginIsContinuation();
                            builder.emitLoadLocal(child);
                            builder.endIsContinuation();
                            builder.beginBlock();
                            builder.beginStoreLocal(resume);
                            builder.beginYield();
                            builder.emitLoadLocal(child);
                            builder.endYield();
                            builder.endStoreLocal();
                            builder.beginStoreLocal(child);
                            builder.beginResumeContinuation();
                            builder.emitLoadLocal(prepared);
                            builder.emitLoadLocal(child);
                            builder.emitLoadLocal(resume);
                            builder.endResumeContinuation();
                            builder.endStoreLocal();
                            builder.endBlock();
                            builder.endWhile();

                            builder.beginStoreLocal(targetResult);
                            builder.beginFinishClosureCall();
                            builder.emitLoadLocal(prepared);
                            builder.emitLoadLocal(child);
                            builder.endFinishClosureCall();
                            builder.endStoreLocal();

                            builder.endBlock();
                            builder.endTryFinally();

                            builder.beginStoreLocal(invocation);
                            builder.beginWrapIoReleaseTargetInvocation();
                            builder.emitLoadArgument(1);
                            builder.emitLoadLocal(targetResult);
                            builder.endWrapIoReleaseTargetInvocation();
                            builder.endStoreLocal();
                            builder.endBlock();

                            builder.beginBlock();
                            builder.beginStoreLocal(invocation);
                            builder.beginIoReleaseTargetInvocationFailed();
                            builder.emitLoadArgument(1);
                            builder.endIoReleaseTargetInvocationFailed();
                            builder.endStoreLocal();
                            builder.endBlock();

                            builder.endTryCatch();

                            builder.beginStoreLocal(lowerOutcome);
                            builder.beginAwaitIoReleaseTargetFuture();
                            builder.emitLoadArgument(1);
                            builder.emitLoadLocal(invocation);
                            builder.endAwaitIoReleaseTargetFuture();
                            builder.endStoreLocal();

                            builder.beginWhile();
                            builder.beginIsContinuation();
                            builder.emitLoadLocal(lowerOutcome);
                            builder.endIsContinuation();
                            builder.beginBlock();
                            builder.beginStoreLocal(yielded);
                            builder.emitLoadLocal(lowerOutcome);
                            builder.endStoreLocal();
                            builder.beginStoreLocal(resume);
                            builder.beginYield();
                            builder.emitLoadLocal(yielded);
                            builder.endYield();
                            builder.endStoreLocal();
                            builder.beginStoreLocal(lowerOutcome);
                            builder.beginResumeIoReleaseTargetFutureWait();
                            builder.emitLoadArgument(0);
                            builder.emitLoadLocal(yielded);
                            builder.emitLoadLocal(resume);
                            builder.endResumeIoReleaseTargetFutureWait();
                            builder.endStoreLocal();
                            builder.endBlock();
                            builder.endWhile();

                            builder.beginApplyIoReleaseTargetOutcome();
                            builder.emitLoadArgument(1);
                            builder.emitLoadLocal(lowerOutcome);
                            builder.endApplyIoReleaseTargetOutcome();

                            builder.endBlock();
                            builder.endWhile();

                            builder.beginReturn();
                            builder.beginFinishIoReleaseSequence();
                            builder.emitLoadArgument(1);
                            builder.endFinishIoReleaseSequence();
                            builder.endReturn();

                            builder.endRoot();
                        });
        return new Plan(roots.getNode(0).getCallTarget());
    }

    public static void schedule(
            Plan plan,
            ProtosIoReleaseExecution release,
            Sequence sequence) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(release, "release");
        Objects.requireNonNull(sequence, "sequence");

        ProtosBytecodeIoReleaseExecution.installAndScheduleWithEntryState(
                release,
                plan.target,
                release.deferredCPrimeActivationForRuntime(),
                sequence,
                (current, result) -> {
                    if (!(result instanceof Outcome outcome)) {
                        throw new IllegalStateException(
                                "lifecycle release C-prime root returned an invalid outcome carrier");
                    }
                    if (outcome.errorOrNull() == null) {
                        current.succeed();
                    } else {
                        current.fail(outcome.errorOrNull());
                    }
                },
                ProtosIoReleaseExecution::fail);
    }
}
