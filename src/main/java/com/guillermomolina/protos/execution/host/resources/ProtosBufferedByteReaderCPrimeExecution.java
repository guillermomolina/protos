/* APL-1.0 licensed work; see LICENSE.TXT. */
package com.guillermomolina.protos.execution;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosBufferedByteIo;
import com.guillermomolina.protos.runtime.ProtosFutureValue;
import com.guillermomolina.protos.runtime.ProtosIoOperation;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeLocal;
import com.oracle.truffle.api.bytecode.BytecodeRootNodes;
import java.util.List;
import java.util.Objects;

/**
 * Private PLAT029 operation-owned C-prime plan for one active BufferedReader.read.
 *
 * <p>The buffered wrapper retains queueing, read-ahead, commitment, cancellation and
 * lifecycle semantics. This class owns only the suspendible guest-control extent:
 *
 * <pre>
 * target.read(max)
 *     -> await returned Future without a hidden Task
 *     -> return inert lower-outcome evidence to ProtosBufferedByteIo
 * </pre>
 *
 * <p>No Future terminal callback re-enters guest code.
 */
public final class ProtosBufferedByteReaderCPrimeExecution {
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
        enum Kind {
            FUTURE_TERMINAL,
            INVALID,
            INVOCATION_FAILED,
            OUTER_TERMINAL
        }

        private final Kind kind;
        private final ProtosFutureValue lower;

        private LowerOutcome(Kind kind, ProtosFutureValue lower) {
            this.kind = Objects.requireNonNull(kind, "kind");
            this.lower = lower;
        }

        static LowerOutcome fromFuture(ProtosFutureValue lower) {
            if (lower.state() == ProtosFutureValue.State.PENDING) {
                throw new IllegalStateException(
                        "BufferedReader lower Future projected before terminalization");
            }
            return new LowerOutcome(Kind.FUTURE_TERMINAL, lower);
        }

        static LowerOutcome invalid() {
            return new LowerOutcome(Kind.INVALID, null);
        }

        static LowerOutcome invocationFailed() {
            return new LowerOutcome(Kind.INVOCATION_FAILED, null);
        }

        static LowerOutcome outerTerminal() {
            return new LowerOutcome(Kind.OUTER_TERMINAL, null);
        }
    }

    static final class CallState {
        private final ProtosIoOperation operation;
        private final ProtosBufferedByteIo reader;
        private final Object target;
        private final List<?> arguments;

        CallState(
                ProtosIoOperation operation,
                ProtosBufferedByteIo reader,
                Object target,
                List<?> arguments) {
            this.operation = Objects.requireNonNull(operation, "operation");
            this.reader = Objects.requireNonNull(reader, "reader");
            this.target = Objects.requireNonNull(target, "target");
            this.arguments = List.copyOf(Objects.requireNonNull(arguments, "arguments"));
        }

        Object target() {
            return target;
        }

        List<?> arguments() {
            return arguments;
        }

        TargetInvocation invocationSucceeded(Object result) {
            return new TargetInvocation(
                    false,
                    Objects.requireNonNull(result, "BufferedReader target invocation result"));
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
            if (!reader.observeReadLowerForCPrimeRuntime(operation, lower)) {
                return LowerOutcome.outerTerminal();
            }
            return ProtosStandardFutureProtocol.awaitFutureForIoOperationContinuationForRuntime(
                    lower,
                    operation,
                    () -> LowerOutcome.fromFuture(lower));
        }
    }

    private ProtosBufferedByteReaderCPrimeExecution() {}

    public static Plan planForEnteredContext() {
        ProtosLanguageContext context =
                ProtosLanguageContext.currentIfEnteredForRuntime();
        if (context == null) {
            throw new IllegalStateException(
                    "BufferedReader C-prime execution requires an entered Protos Context");
        }
        return context.bufferedByteReaderCPrimePlanForRuntime();
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
                                    builder.createLocal("bufferedReaderTargetCall", null);
                            BytecodeLocal child =
                                    builder.createLocal("bufferedReaderTargetChild", null);
                            BytecodeLocal resume =
                                    builder.createLocal("bufferedReaderTargetResume", null);
                            BytecodeLocal targetResult =
                                    builder.createLocal("bufferedReaderTargetResult", null);
                            BytecodeLocal invocation =
                                    builder.createLocal("bufferedReaderTargetInvocation", null);
                            BytecodeLocal lowerOutcome =
                                    builder.createLocal("bufferedReaderLowerOutcome", null);
                            BytecodeLocal yielded =
                                    builder.createLocal("bufferedReaderLowerYielded", null);

                            builder.beginTryCatch();

                            builder.beginBlock();
                            builder.beginStoreLocal(prepared);
                            builder.beginPrepareBufferedByteReaderTargetCall();
                            builder.emitLoadArgument(1);
                            builder.emitLoadArgument(0);
                            builder.endPrepareBufferedByteReaderTargetCall();
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
                            builder.beginWrapBufferedByteReaderTargetInvocation();
                            builder.emitLoadArgument(1);
                            builder.emitLoadLocal(targetResult);
                            builder.endWrapBufferedByteReaderTargetInvocation();
                            builder.endStoreLocal();
                            builder.endBlock();

                            builder.beginBlock();
                            builder.beginStoreLocal(invocation);
                            builder.beginBufferedByteReaderTargetInvocationFailed();
                            builder.emitLoadArgument(1);
                            builder.endBufferedByteReaderTargetInvocationFailed();
                            builder.endStoreLocal();
                            builder.endBlock();

                            builder.endTryCatch();

                            builder.beginStoreLocal(lowerOutcome);
                            builder.beginAwaitBufferedByteReaderTargetFuture();
                            builder.emitLoadArgument(1);
                            builder.emitLoadLocal(invocation);
                            builder.endAwaitBufferedByteReaderTargetFuture();
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
                            builder.beginResumeBufferedByteReaderTargetFutureWait();
                            builder.emitLoadArgument(0);
                            builder.emitLoadLocal(yielded);
                            builder.emitLoadLocal(resume);
                            builder.endResumeBufferedByteReaderTargetFutureWait();
                            builder.endStoreLocal();
                            builder.endBlock();
                            builder.endWhile();

                            builder.beginReturn();
                            builder.emitLoadLocal(lowerOutcome);
                            builder.endReturn();
                            builder.endRoot();
                        });
        return new Plan(roots.getNode(0).getCallTarget());
    }

    public static void schedule(
            Plan plan,
            ProtosIoOperation operation,
            ProtosBufferedByteIo reader,
            Object target,
            List<?> arguments) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(reader, "reader");

        CallState state = new CallState(operation, reader, target, arguments);
        ProtosBytecodeIoOperationExecution.installAndScheduleWithEntryState(
                operation,
                plan.target,
                operation.deferredCPrimeActivationForRuntime(),
                state,
                (current, result) -> {
                    if (!(result instanceof LowerOutcome outcome)) {
                        reader.unexpectedReadCPrimeCompletionForRuntime(current);
                        return;
                    }
                    switch (outcome.kind) {
                        case FUTURE_TERMINAL ->
                                reader.consumeReadLowerForCPrimeRuntime(
                                        current, outcome.lower);
                        case INVALID ->
                                reader.invalidReadLowerForCPrimeRuntime(current);
                        case INVOCATION_FAILED ->
                                reader.readInvocationFailedForCPrimeRuntime(current);
                        case OUTER_TERMINAL -> { }
                    }
                },
                reader::readCPrimeDriverFailedForRuntime);
    }
}
