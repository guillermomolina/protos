/* APL-1.0 licensed work; see LICENSE.TXT. */
package com.guillermomolina.protos.execution;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosBufferedByteIo;
import com.guillermomolina.protos.runtime.ProtosFutureValue;
import com.guillermomolina.protos.runtime.ProtosIoOperation;
import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeLocal;
import com.oracle.truffle.api.bytecode.BytecodeRootNodes;
import java.util.List;
import java.util.Objects;

/**
 * Private PLAT029/PLAT031 operation-owned C-prime plan for one ordinary
 * BufferedWriter.flush.
 *
 * <p>The complete guest callback extent is one C-prime execution:
 *
 * <pre>
 * optional target.write(pending)
 *     -> await lower Future
 *     -> D117 first-effect settlement
 *     -> optional target.flush()
 *     -> await lower Future
 *     -> committed aftermath
 * </pre>
 *
 * <p>The buffered wrapper remains the sole queue/output/poison authority and
 * {@link ProtosIoOperation} remains the sole commitment/cancellation authority.
 * No lower Future owns continuation state and no hidden Task is introduced.
 */
public final class ProtosBufferedByteWriterCPrimeExecution {
    public static final class Plan {
        private final RootCallTarget target;

        private Plan(RootCallTarget target) {
            this.target = Objects.requireNonNull(target, "target");
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
        enum Kind { RESOLVED, FAILED, CANCELLED, INVALID, INVOCATION_FAILED }

        private final Kind kind;
        private final ProtosObjectValue error;

        private LowerOutcome(Kind kind, ProtosObjectValue error) {
            this.kind = Objects.requireNonNull(kind, "kind");
            this.error = error;
        }

        static LowerOutcome fromFuture(ProtosFutureValue lower) {
            return switch (lower.state()) {
                case RESOLVED -> new LowerOutcome(Kind.RESOLVED, null);
                case FAILED ->
                        new LowerOutcome(
                                Kind.FAILED,
                                lower.failedError()
                                        .orElseThrow(
                                                () ->
                                                        new IllegalStateException(
                                                                "failed BufferedWriter lower Future has no Error")));
                case CANCELLED -> new LowerOutcome(Kind.CANCELLED, null);
                case PENDING ->
                        throw new IllegalStateException(
                                "BufferedWriter lower Future projected before terminalization");
            };
        }

        static LowerOutcome invalid() {
            return new LowerOutcome(Kind.INVALID, null);
        }

        static LowerOutcome invocationFailed() {
            return new LowerOutcome(Kind.INVOCATION_FAILED, null);
        }
    }

    static final class CallState {
        private final ProtosIoOperation operation;
        private final ProtosBufferedByteIo writer;
        private final Object target;
        private final boolean writeFirst;
        private final int pendingLength;
        private final List<?> firstArguments;

        CallState(
                ProtosIoOperation operation,
                ProtosBufferedByteIo writer,
                Object target,
                boolean writeFirst,
                int pendingLength,
                List<?> firstArguments) {
            this.operation = Objects.requireNonNull(operation, "operation");
            this.writer = Objects.requireNonNull(writer, "writer");
            this.target = Objects.requireNonNull(target, "target");
            this.writeFirst = writeFirst;
            this.pendingLength = pendingLength;
            this.firstArguments =
                    List.copyOf(Objects.requireNonNull(firstArguments, "firstArguments"));
        }

        Object target() {
            return target;
        }

        String firstSelector() {
            return writeFirst ? "write" : "flush";
        }

        List<?> firstArguments() {
            return firstArguments;
        }

        boolean beginFirstEffectAttempt() {
            return writer.beginFlushFirstEffectForCPrimeRuntime(operation);
        }

        TargetInvocation invocationSucceeded(Object result) {
            return new TargetInvocation(
                    false,
                    Objects.requireNonNull(
                            result, "BufferedWriter target invocation result"));
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

            writer.installFlushLowerForCPrimeRuntime(operation, lower);
            return ProtosStandardFutureProtocol.awaitFutureForIoOperationContinuationForRuntime(
                    lower,
                    operation,
                    () -> {
                        writer.clearFlushLowerForCPrimeRuntime(operation, lower);
                        return LowerOutcome.fromFuture(lower);
                    });
        }

        boolean applyFirstOutcome(LowerOutcome outcome) {
            Objects.requireNonNull(outcome, "outcome");
            return switch (outcome.kind) {
                case RESOLVED ->
                        writer.flushFirstLowerResolvedForCPrimeRuntime(
                                operation, writeFirst, pendingLength);
                case FAILED -> {
                    writer.flushFirstLowerFailedForCPrimeRuntime(
                            operation, outcome.error);
                    yield false;
                }
                case CANCELLED -> {
                    writer.flushFirstLowerCancelledForCPrimeRuntime(operation);
                    yield false;
                }
                case INVALID, INVOCATION_FAILED -> {
                    writer.flushFirstLowerUnknownFailureForCPrimeRuntime(operation);
                    yield false;
                }
            };
        }

        boolean applyFollowupFlushOutcome(LowerOutcome outcome) {
            Objects.requireNonNull(outcome, "outcome");
            switch (outcome.kind) {
                case RESOLVED ->
                        writer.flushFollowupResolvedForCPrimeRuntime(operation);
                case FAILED ->
                        writer.flushFollowupFailedForCPrimeRuntime(
                                operation, outcome.error);
                case CANCELLED, INVALID, INVOCATION_FAILED ->
                        writer.flushFollowupUnknownFailureForCPrimeRuntime(operation);
            }
            return false;
        }
    }

    private ProtosBufferedByteWriterCPrimeExecution() {}

    public static Plan planForEnteredContext() {
        ProtosLanguageContext context =
                ProtosLanguageContext.currentIfEnteredForRuntime();
        if (context == null) {
            throw new IllegalStateException(
                    "BufferedWriter C-prime execution requires an entered Protos Context");
        }
        return context.bufferedByteWriterCPrimePlanForRuntime();
    }

    static Plan createPlan(ProtosLanguage language) {
        Objects.requireNonNull(language, "language");
        BytecodeRootNodes<ProtosBytecodeRootNode> roots =
                ProtosBytecodeRootNodeGen.create(
                        language,
                        BytecodeConfig.DEFAULT,
                        builder -> {
                            builder.beginRoot();

                            BytecodeLocal runFirst =
                                    builder.createLocal("bufferedWriterRunFirst", null);
                            BytecodeLocal runFlush =
                                    builder.createLocal("bufferedWriterRunFlush", null);
                            BytecodeLocal prepared =
                                    builder.createLocal("bufferedWriterTargetCall", null);
                            BytecodeLocal child =
                                    builder.createLocal("bufferedWriterTargetChild", null);
                            BytecodeLocal resume =
                                    builder.createLocal("bufferedWriterTargetResume", null);
                            BytecodeLocal targetResult =
                                    builder.createLocal("bufferedWriterTargetResult", null);
                            BytecodeLocal invocation =
                                    builder.createLocal("bufferedWriterTargetInvocation", null);
                            BytecodeLocal lowerOutcome =
                                    builder.createLocal("bufferedWriterLowerOutcome", null);
                            BytecodeLocal yielded =
                                    builder.createLocal("bufferedWriterLowerYielded", null);

                            builder.beginStoreLocal(runFirst);
                            builder.beginBeginBufferedByteWriterFirstEffect();
                            builder.emitLoadArgument(1);
                            builder.endBeginBufferedByteWriterFirstEffect();
                            builder.endStoreLocal();

                            builder.beginWhile();
                            builder.beginIsBufferedByteWriterStepRequired();
                            builder.emitLoadLocal(runFirst);
                            builder.endIsBufferedByteWriterStepRequired();
                            builder.beginBlock();

                            builder.beginStoreLocal(runFirst);
                            builder.emitLoadConstant(false);
                            builder.endStoreLocal();

                            builder.beginTryCatch();

                            builder.beginBlock();
                            builder.beginStoreLocal(prepared);
                            builder.beginPrepareBufferedByteWriterFirstCall();
                            builder.emitLoadArgument(1);
                            builder.emitLoadArgument(0);
                            builder.endPrepareBufferedByteWriterFirstCall();
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
                            builder.beginWrapBufferedByteWriterTargetInvocation();
                            builder.emitLoadArgument(1);
                            builder.emitLoadLocal(targetResult);
                            builder.endWrapBufferedByteWriterTargetInvocation();
                            builder.endStoreLocal();
                            builder.endBlock();

                            builder.beginBlock();
                            builder.beginStoreLocal(invocation);
                            builder.beginBufferedByteWriterTargetInvocationFailed();
                            builder.emitLoadArgument(1);
                            builder.endBufferedByteWriterTargetInvocationFailed();
                            builder.endStoreLocal();
                            builder.endBlock();

                            builder.endTryCatch();

                            builder.beginStoreLocal(lowerOutcome);
                            builder.beginAwaitBufferedByteWriterTargetFuture();
                            builder.emitLoadArgument(1);
                            builder.emitLoadLocal(invocation);
                            builder.endAwaitBufferedByteWriterTargetFuture();
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
                            builder.beginResumeBufferedByteWriterTargetFutureWait();
                            builder.emitLoadArgument(0);
                            builder.emitLoadLocal(yielded);
                            builder.emitLoadLocal(resume);
                            builder.endResumeBufferedByteWriterTargetFutureWait();
                            builder.endStoreLocal();
                            builder.endBlock();
                            builder.endWhile();

                            builder.beginStoreLocal(runFlush);
                            builder.beginApplyBufferedByteWriterFirstOutcome();
                            builder.emitLoadArgument(1);
                            builder.emitLoadLocal(lowerOutcome);
                            builder.endApplyBufferedByteWriterFirstOutcome();
                            builder.endStoreLocal();

                            builder.beginWhile();
                            builder.beginIsBufferedByteWriterStepRequired();
                            builder.emitLoadLocal(runFlush);
                            builder.endIsBufferedByteWriterStepRequired();
                            builder.beginBlock();

                            builder.beginStoreLocal(runFlush);
                            builder.emitLoadConstant(false);
                            builder.endStoreLocal();

                            builder.beginTryCatch();

                            builder.beginBlock();
                            builder.beginStoreLocal(prepared);
                            builder.beginPrepareBufferedByteWriterFlushCall();
                            builder.emitLoadArgument(1);
                            builder.emitLoadArgument(0);
                            builder.endPrepareBufferedByteWriterFlushCall();
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
                            builder.beginWrapBufferedByteWriterTargetInvocation();
                            builder.emitLoadArgument(1);
                            builder.emitLoadLocal(targetResult);
                            builder.endWrapBufferedByteWriterTargetInvocation();
                            builder.endStoreLocal();
                            builder.endBlock();

                            builder.beginBlock();
                            builder.beginStoreLocal(invocation);
                            builder.beginBufferedByteWriterTargetInvocationFailed();
                            builder.emitLoadArgument(1);
                            builder.endBufferedByteWriterTargetInvocationFailed();
                            builder.endStoreLocal();
                            builder.endBlock();

                            builder.endTryCatch();

                            builder.beginStoreLocal(lowerOutcome);
                            builder.beginAwaitBufferedByteWriterTargetFuture();
                            builder.emitLoadArgument(1);
                            builder.emitLoadLocal(invocation);
                            builder.endAwaitBufferedByteWriterTargetFuture();
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
                            builder.beginResumeBufferedByteWriterTargetFutureWait();
                            builder.emitLoadArgument(0);
                            builder.emitLoadLocal(yielded);
                            builder.emitLoadLocal(resume);
                            builder.endResumeBufferedByteWriterTargetFutureWait();
                            builder.endStoreLocal();
                            builder.endBlock();
                            builder.endWhile();

                            builder.beginStoreLocal(runFlush);
                            builder.beginApplyBufferedByteWriterFollowupOutcome();
                            builder.emitLoadArgument(1);
                            builder.emitLoadLocal(lowerOutcome);
                            builder.endApplyBufferedByteWriterFollowupOutcome();
                            builder.endStoreLocal();

                            builder.endBlock();
                            builder.endWhile();

                            builder.endBlock();
                            builder.endWhile();

                            builder.beginReturn();
                            builder.emitLoadConstant(ProtosNullValue.INSTANCE);
                            builder.endReturn();
                            builder.endRoot();
                        });
        return new Plan(roots.getNode(0).getCallTarget());
    }

    public static void schedule(
            Plan plan,
            ProtosIoOperation operation,
            ProtosBufferedByteIo writer,
            Object target,
            byte[] pending,
            Object pendingPayload) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(writer, "writer");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(pending, "pending");

        boolean writeFirst = pending.length > 0;
        List<?> firstArguments =
                writeFirst
                        ? List.of(Objects.requireNonNull(pendingPayload, "pendingPayload"))
                        : List.of();

        CallState state =
                new CallState(
                        operation,
                        writer,
                        target,
                        writeFirst,
                        pending.length,
                        firstArguments);

        ProtosBytecodeIoOperationExecution.installAndScheduleWithEntryState(
                operation,
                plan.target,
                operation.deferredCPrimeActivationForRuntime(),
                state,
                (current, ignored) ->
                        writer.unexpectedFlushCPrimeCompletionForRuntime(current),
                writer::flushCPrimeDriverFailedForRuntime);
    }
}
