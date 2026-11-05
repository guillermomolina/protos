/* APL-1.0 licensed work; see LICENSE.TXT. */
package com.guillermomolina.protos.execution;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosCoreErrors;
import com.guillermomolina.protos.runtime.ProtosFutureValue;
import com.guillermomolina.protos.runtime.ProtosIoReleaseExecution;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.execution.ProtosPreparedCalls.ImmediateResultCall;
import com.guillermomolina.protos.execution.ProtosPreparedCalls.ModuleInitializationCall;
import com.guillermomolina.protos.execution.ProtosPreparedCalls.NativeCall;
import com.guillermomolina.protos.execution.ProtosPreparedCalls.PreparedClosureCall;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.bytecode.ContinuationResult;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.nodes.ControlFlowException;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
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

    /** PLAT070 private result of a finalizer whose invocation was left by a held transfer. */
    private static final Object FINALIZER_TRANSFER_ESCAPED = new Object();

    /**
     * PLAT070: enters a mandatory finalizer during transfer cleanup exactly as {@code
     * EnterClosureCall} does, except that a control transfer escaping it is held by {@code
     * sequence} instead of abandoning the remaining finalizers.
     */
    static Object enterFinalizer(
            Sequence sequence, PreparedClosureCall prepared, IndirectCallNode node) {
        try {
            if (prepared instanceof ImmediateResultCall immediate) {
                return immediate.enterImmediate();
            }
            if (prepared instanceof NativeCall nativeCall) {
                return nativeCall.enterNative();
            }
            if (prepared instanceof ModuleInitializationCall module && module.isImmediate()) {
                return module.enterImmediate();
            }
            return node.call(prepared.bodyTarget(), prepared.targetArguments());
        } catch (ControlFlowException transfer) {
            return sequence.finalizerTransfer(prepared, transfer);
        } catch (AbstractTruffleException failure) {
            prepared.failIfModuleInitialization();
            throw failure;
        } catch (RuntimeException failure) {
            throw prepared.mapRuntimeFailure(failure);
        }
    }

    /**
     * PLAT070: resumes a suspended mandatory finalizer during transfer cleanup exactly as {@code
     * ResumeContinuation} does, with the same transfer holding as {@link #enterFinalizer}.
     */
    static Object resumeFinalizer(
            Sequence sequence, PreparedClosureCall prepared, Object child, Object resumeValue) {
        try {
            if (child instanceof ProtosTailCallContinuation
                    || child instanceof ContinuationResult) {
                return ProtosTailCallContinuation.resume(child, resumeValue);
            }
            if (child instanceof ProtosNativeSuspension suspension) {
                return ProtosBytecodeRootNode.ResumeContinuation.nativeSuspension(
                        prepared, suspension, resumeValue);
            }
            if (child instanceof ProtosIoOperationSuspension suspension) {
                return ProtosBytecodeRootNode.ResumeContinuation.ioOperationSuspension(
                        prepared, suspension, resumeValue);
            }
            throw new IllegalStateException(
                    "lifecycle release finalizer resumed an unsupported pending result");
        } catch (ControlFlowException transfer) {
            return sequence.finalizerTransfer(prepared, transfer);
        } catch (AbstractTruffleException failure) {
            prepared.failIfModuleInitialization();
            throw failure;
        } catch (RuntimeException failure) {
            throw prepared.mapRuntimeFailure(failure);
        }
    }

    public static final class Sequence {
        private final ProtosIoReleaseExecution release;
        private final List<Step> steps;
        private int index;
        private ProtosObjectValue primaryFailure;
        private boolean transferEscaped;
        private boolean transferCleanupAbandoned;
        /** PLAT070: the latest control transfer escaping a finalizer during transfer cleanup. */
        private ControlFlowException laterTransfer;

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
            if (invocation.failed || invocation.result == FINALIZER_TRANSFER_ESCAPED) {
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
                case RESOLVED -> runResolvedAction(step.resolvedAction);
                case FAILED -> recordFailure(step, Objects.requireNonNull(outcome.error, "error"));
                case CANCELLED, INVALID, INVOCATION_FAILED ->
                        recordFailure(step, ioError());
            }
            index++;
        }

        /**
         * PLAT070: decides, in the release body's finally handler, whether an internal control
         * transfer (non-local return or cancellation) is leaving the body. If so, the step it
         * interrupted is accounted exactly once with the I/O Error a failed invocation records;
         * that failure makes every later ordinary step suppressed, while pending mandatory
         * finalizers stay pending and are then run by the same C-prime step loop. Answers whether
         * that transfer cleanup must run. The transfer itself is neither observed nor replaced.
         */
        boolean beginTransferCleanup(boolean completedOrSignalled) {
            if (completedOrSignalled || transferEscaped) {
                return false;
            }
            transferEscaped = true;
            accountInterruptedStep();
            return true;
        }

        /**
         * PLAT070: every pending mandatory finalizer was invoked during transfer cleanup; the
         * release is terminalized through its ordinary failure with the primary failure. The
         * original transfer then resumes unwinding.
         */
        void finishTransferCleanup() {
            if (hasNextStep()) {
                throw new IllegalStateException(
                        "lifecycle release transfer cleanup left a mandatory finalizer pending");
            }
            terminalizeAfterTransfer();
            ControlFlowException superseding = laterTransfer;
            if (superseding != null) {
                laterTransfer = null;
                // The latest escaping transfer supersedes the pending one, as in ensure cleanup.
                throw superseding;
            }
        }

        /**
         * PLAT070: a control transfer left a mandatory finalizer during transfer cleanup. The
         * finalizer's own prepared call still consumes a return to its own home; any other
         * transfer is held, by identity, as the latest superseding transfer, and the finalizer is
         * accounted as a failed invocation so the remaining finalizers still run.
         */
        Object finalizerTransfer(PreparedClosureCall prepared, ControlFlowException transfer) {
            try {
                return prepared.handleControlTransfer(transfer);
            } catch (ControlFlowException escaping) {
                laterTransfer = escaping;
                return FINALIZER_TRANSFER_ESCAPED;
            }
        }

        /**
         * PLAT070: guards the transfer cleanup itself. Finalizer transfers never leave the cleanup
         * loop (see {@link #finalizerTransfer}), so this runs only when the cleanup is left by an
         * internal failure (a host failure of the release machinery, or the superseding transfer
         * thrown after terminalization, in which case it has no effect). It terminalizes a release
         * that would otherwise stay pending; it never decides the order of mandatory finalizers.
         * No effect when the cleanup completed.
         */
        void abandonTransferCleanup(boolean cleanupCompleted) {
            if (cleanupCompleted || transferCleanupAbandoned) {
                return;
            }
            transferCleanupAbandoned = true;
            if (release.terminal()) {
                return;
            }
            accountInterruptedStep();
            while (hasNextStep()) {
                recordFailure(steps.get(index), ioError());
                index++;
            }
            terminalizeAfterTransfer();
        }

        private void accountInterruptedStep() {
            if (index < steps.size()) {
                recordFailure(steps.get(index), ioError());
                index++;
            }
        }

        private void terminalizeAfterTransfer() {
            if (primaryFailure == null) {
                primaryFailure = ioError();
            }
            if (!release.terminal()) {
                release.fail(primaryFailure);
            }
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
            runFailedAction(step.failedAction, error);
        }

        @TruffleBoundary
        private static void runResolvedAction(Runnable action) {
            action.run();
        }

        @TruffleBoundary
        private static void runFailedAction(
                Consumer<ProtosObjectValue> action,
                ProtosObjectValue error) {
            action.accept(error);
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
                            StepLocals locals = new StepLocals(builder);

                            /*
                             * PLAT070: an internal control transfer (non-local return or
                             * cancellation) unwinds directly and never enters the step TryCatch.
                             * The release body therefore runs inside a TryFinally whose handler,
                             * only when the body neither completed nor left through a guest
                             * Error, accounts the interrupted step once and then runs every
                             * pending mandatory finalizer through this same C-prime step loop
                             * (suspension included) while the original transfer stays pending.
                             * The release is terminalized only after those finalizers ran.
                             */
                            BytecodeLocal completed =
                                    builder.createLocal("ioReleaseCompletedOrSignalled", null);
                            BytecodeLocal cleanupCompleted =
                                    builder.createLocal("ioReleaseTransferCleanupCompleted", null);
                            builder.beginStoreLocal(completed);
                            builder.emitLoadConstant(false);
                            builder.endStoreLocal();

                            builder.beginTryFinally(
                                    () -> emitTransferCleanup(builder, locals, completed, cleanupCompleted));
                            builder.beginTryCatch();
                            builder.beginBlock();

                            emitStepLoop(builder, locals, false);

                            builder.beginStoreLocal(completed);
                            builder.emitLoadConstant(true);
                            builder.endStoreLocal();
                            builder.beginReturn();
                            builder.beginStructuredFinish();
                            builder.emitLoadArgument(1);
                            builder.endStructuredFinish();
                            builder.endReturn();
                            builder.endBlock();

                            builder.beginBlock();
                            builder.beginStoreLocal(completed);
                            builder.emitLoadConstant(true);
                            builder.endStoreLocal();
                            builder.beginRethrowTruffleException();
                            builder.emitLoadException();
                            builder.endRethrowTruffleException();
                            builder.endBlock();

                            builder.endTryCatch();
                            builder.endTryFinally();

                            builder.endRoot();
                        });
        return new Plan(roots.getNode(0).getCallTarget());
    }

    /** The frame locals of one release step invocation, shared by both step loops of the root. */
    private static final class StepLocals {
        final BytecodeLocal prepared;
        final BytecodeLocal child;
        final BytecodeLocal resume;
        final BytecodeLocal targetResult;
        final BytecodeLocal invocation;
        final BytecodeLocal lowerOutcome;
        final BytecodeLocal yielded;

        StepLocals(ProtosBytecodeRootNodeGen.Builder builder) {
            prepared = builder.createLocal("ioReleasePreparedCall", null);
            child = builder.createLocal("ioReleaseChild", null);
            resume = builder.createLocal("ioReleaseResume", null);
            targetResult = builder.createLocal("ioReleaseTargetResult", null);
            invocation = builder.createLocal("ioReleaseInvocation", null);
            lowerOutcome = builder.createLocal("ioReleaseLowerOutcome", null);
            yielded = builder.createLocal("ioReleaseYielded", null);
        }
    }

    /**
     * Emits the release step loop: each remaining step is invoked as a prepared call, may suspend
     * on its own invocation or on its lower Future, and has its outcome accepted exactly once.
     */
    private static void emitStepLoop(
            ProtosBytecodeRootNodeGen.Builder b, StepLocals l, boolean transferCleanup) {
        b.beginWhile();
        b.beginStructuredHasNext(ProtosStructuredPhase.STEP);
        b.emitLoadArgument(1);
        b.endStructuredHasNext();

        b.beginBlock();

        b.beginTryCatch();

        b.beginBlock();
        b.beginStoreLocal(l.prepared);
        b.beginIoPrepareTargetCall(false);
        b.emitLoadArgument(1);
        b.emitLoadArgument(0);
        b.endIoPrepareTargetCall();
        b.endStoreLocal();

        b.beginTryFinally(
                () -> {
                    b.beginCompleteClosureCall();
                    b.emitLoadLocal(l.prepared);
                    b.endCompleteClosureCall();
                });
        b.beginBlock();

        b.beginStoreLocal(l.child);
        if (transferCleanup) {
            b.beginIoReleaseEnterFinalizer();
            b.emitLoadArgument(1);
            b.emitLoadLocal(l.prepared);
            b.endIoReleaseEnterFinalizer();
        } else {
            b.beginEnterClosureCall();
            b.emitLoadLocal(l.prepared);
            b.endEnterClosureCall();
        }
        b.endStoreLocal();

        b.beginWhile();
        b.beginIsResultKind(ProtosResultKind.CONTINUATION);
        b.emitLoadLocal(l.child);
        b.endIsResultKind();
        b.beginBlock();
        b.beginStoreLocal(l.resume);
        b.beginYield();
        b.emitLoadLocal(l.child);
        b.endYield();
        b.endStoreLocal();
        b.beginStoreLocal(l.child);
        if (transferCleanup) {
            b.beginIoReleaseResumeFinalizer();
            b.emitLoadArgument(1);
            b.emitLoadLocal(l.prepared);
            b.emitLoadLocal(l.child);
            b.emitLoadLocal(l.resume);
            b.endIoReleaseResumeFinalizer();
        } else {
            b.beginResumeContinuation();
            b.emitLoadLocal(l.prepared);
            b.emitLoadLocal(l.child);
            b.emitLoadLocal(l.resume);
            b.endResumeContinuation();
        }
        b.endStoreLocal();
        b.endBlock();
        b.endWhile();

        b.beginStoreLocal(l.targetResult);
        b.beginFinishClosureCall(true);
        b.emitLoadLocal(l.prepared);
        b.emitLoadLocal(l.child);
        b.endFinishClosureCall();
        b.endStoreLocal();

        b.endBlock();
        b.endTryFinally();

        b.beginStoreLocal(l.invocation);
        b.beginIoInvocationSucceeded(false);
        b.emitLoadArgument(1);
        b.emitLoadLocal(l.targetResult);
        b.endIoInvocationSucceeded();
        b.endStoreLocal();
        b.endBlock();

        b.beginBlock();
        b.beginStoreLocal(l.invocation);
        b.beginIoInvocationFailed();
        b.emitLoadArgument(1);
        b.endIoInvocationFailed();
        b.endStoreLocal();
        b.endBlock();

        b.endTryCatch();

        b.beginStoreLocal(l.lowerOutcome);
        b.beginIoInvocationSucceeded(true);
        b.emitLoadArgument(1);
        b.emitLoadLocal(l.invocation);
        b.endIoInvocationSucceeded();
        b.endStoreLocal();

        b.beginWhile();
        b.beginIsResultKind(ProtosResultKind.CONTINUATION);
        b.emitLoadLocal(l.lowerOutcome);
        b.endIsResultKind();
        b.beginBlock();
        b.beginStoreLocal(l.yielded);
        b.emitLoadLocal(l.lowerOutcome);
        b.endStoreLocal();
        b.beginStoreLocal(l.resume);
        b.beginYield();
        b.emitLoadLocal(l.yielded);
        b.endYield();
        b.endStoreLocal();
        b.beginStoreLocal(l.lowerOutcome);
        b.beginResumeIoReleaseTargetFutureWait();
        b.emitLoadArgument(0);
        b.emitLoadLocal(l.yielded);
        b.emitLoadLocal(l.resume);
        b.endResumeIoReleaseTargetFutureWait();
        b.endStoreLocal();
        b.endBlock();
        b.endWhile();

        b.beginStructuredAcceptChildOutcome(ProtosStructuredPhase.CHILD);
        b.emitLoadArgument(1);
        b.emitLoadLocal(l.lowerOutcome);
        b.endStructuredAcceptChildOutcome();

        b.endBlock();
        b.endWhile();
    }

    /**
     * PLAT070 transfer cleanup, emitted as the release body's finally handler. When the body was
     * left by an internal control transfer, the interrupted step is accounted and the pending
     * mandatory finalizers run through {@link #emitStepLoop}; then the release is terminalized.
     * In this loop a finalizer is entered and resumed through {@code IoReleaseEnterFinalizer} /
     * {@code IoReleaseResumeFinalizer}: a later transfer escaping one finalizer is held by the
     * {@link Sequence} so every remaining mandatory finalizer is still invoked, and the latest
     * such transfer is thrown, superseding the pending one as in ensure cleanup, only after the
     * release is terminalized. {@code IoReleaseAbandonTransferCleanup} only guards an internal
     * failure of the cleanup itself.
     */
    private static void emitTransferCleanup(
            ProtosBytecodeRootNodeGen.Builder b,
            StepLocals l,
            BytecodeLocal completed,
            BytecodeLocal cleanupCompleted) {
        b.beginIfThen();
        b.beginIoReleaseBeginTransferCleanup();
        b.emitLoadArgument(1);
        b.emitLoadLocal(completed);
        b.endIoReleaseBeginTransferCleanup();

        b.beginBlock();
        b.beginStoreLocal(cleanupCompleted);
        b.emitLoadConstant(false);
        b.endStoreLocal();
        b.beginTryFinally(
                () -> {
                    b.beginIoReleaseAbandonTransferCleanup();
                    b.emitLoadArgument(1);
                    b.emitLoadLocal(cleanupCompleted);
                    b.endIoReleaseAbandonTransferCleanup();
                });
        b.beginBlock();
        emitStepLoop(b, l, true);
        b.beginIoReleaseFinishTransferCleanup();
        b.emitLoadArgument(1);
        b.endIoReleaseFinishTransferCleanup();
        b.beginStoreLocal(cleanupCompleted);
        b.emitLoadConstant(true);
        b.endStoreLocal();
        b.endBlock();
        b.endTryFinally();
        b.endBlock();

        b.endIfThen();
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
