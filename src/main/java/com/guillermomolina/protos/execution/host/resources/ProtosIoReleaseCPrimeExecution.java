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

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosCoreErrors;
import com.guillermomolina.protos.runtime.ProtosFutureValue;
import com.guillermomolina.protos.runtime.ProtosIoReleaseExecution;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.nodes.ControlFlowException;
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
         * Holds only a later transfer from a mandatory finalizer while the original
         * transfer is pending in Bytecode TryFinally. Ordinary body transfers unwind
         * directly; no guest Error is intercepted here. The invocation is accounted
         * by the same step loop, and the latest transfer is rethrown after all remaining
         * mandatory finalizers have run, even when one of them suspends.
         */
        Object finalizerTransfer(ControlFlowException transfer) {
            if (!transferEscaped) {
                throw transfer;
            }
            laterTransfer = transfer;
            return FINALIZER_TRANSFER_ESCAPED;
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

                            BytecodeLocal completed =
                                    builder.createLocal("ioReleaseCompletedOrSignalled", null);
                            builder.beginStoreLocal(completed);
                            builder.emitLoadConstant(false);
                            builder.endStoreLocal();
                            builder.beginTryFinally(
                                    () -> emitTransferCleanup(builder, locals, completed));
                            builder.beginTryCatch();
                            builder.beginBlock();

                            emitStepLoop(builder, locals);

                            ProtosIoCPrimeTransferAccounting.markCompleted(builder, completed);
                            builder.beginReturn();
                            builder.beginStructuredFinish();
                            builder.emitLoadArgument(1);
                            builder.endStructuredFinish();
                            builder.endReturn();

                            builder.endBlock();
                            builder.beginBlock();
                            ProtosIoCPrimeTransferAccounting.markCompleted(builder, completed);
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

    /**
     * A suspension retains the pending transfer and the exact step index in the
     * Bytecode frame. Only a semantic exit starts cleanup; completed steps are
     * never replayed. The nested finally terminalizes an abandoned cleanup.
     */
    private static void emitTransferCleanup(
            ProtosBytecodeRootNodeGen.Builder builder,
            StepLocals locals,
            BytecodeLocal completed) {
        builder.beginIfThen();
        builder.beginIoReleaseBeginTransferCleanup();
        builder.emitLoadArgument(1);
        builder.emitLoadLocal(completed);
        builder.endIoReleaseBeginTransferCleanup();
        builder.beginBlock();
        BytecodeLocal cleanupCompleted = builder.createLocal("ioReleaseCleanupCompleted", null);
        builder.beginStoreLocal(cleanupCompleted);
        builder.emitLoadConstant(false);
        builder.endStoreLocal();
        builder.beginTryFinally(
                () -> {
                    builder.beginIoReleaseAbandonTransferCleanup();
                    builder.emitLoadArgument(1);
                    builder.emitLoadLocal(cleanupCompleted);
                    builder.endIoReleaseAbandonTransferCleanup();
                });
        builder.beginBlock();
        emitStepLoop(builder, locals);
        ProtosIoCPrimeTransferAccounting.markCompleted(builder, cleanupCompleted);
        builder.beginIoReleaseFinishTransferCleanup();
        builder.emitLoadArgument(1);
        builder.endIoReleaseFinishTransferCleanup();
        builder.endBlock();
        builder.endTryFinally();
        builder.endBlock();
        builder.endIfThen();
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
            ProtosBytecodeRootNodeGen.Builder b, StepLocals l) {
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

        b.beginStoreLocal(l.child);
        b.beginIoReleaseCallRequest();
        b.emitLoadArgument(1);
        b.emitLoadLocal(l.prepared);
        b.endIoReleaseCallRequest();
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
        b.beginIoReleaseResumeChild();
        b.emitLoadArgument(1);
        b.emitLoadLocal(l.child);
        b.emitLoadLocal(l.resume);
        b.endIoReleaseResumeChild();
        b.endStoreLocal();

        b.endBlock();
        b.endWhile();

        b.beginStoreLocal(l.targetResult);
        b.beginMaterializePrimitiveNumericCarrier();
        b.emitLoadLocal(l.child);
        b.emitLoadConstant(true);
        b.endMaterializePrimitiveNumericCarrier();
        b.endStoreLocal();

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
