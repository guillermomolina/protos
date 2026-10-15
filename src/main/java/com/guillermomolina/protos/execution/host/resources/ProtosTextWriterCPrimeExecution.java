/* APL-1.0 licensed work; see LICENSE.TXT. */
package com.guillermomolina.protos.execution;

import com.guillermomolina.protos.runtime.ProtosActivation;
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
 * Private PLAT029 execution plan for one active TextWriter target write/flush operation.
 *
 * <p>Queueing, encoder state, commitment and permanent writer failure remain owned by
 * {@code ProtosTextWriter}. This class owns only the C-prime extent:
 *
 * <pre>
 * target.write/flush ordinary guest send
 *     -> await the returned Future without a hidden Task
 *     -> return inert lower-outcome evidence to the writer
 * </pre>
 */
public final class ProtosTextWriterCPrimeExecution {
    public interface Completion {
        void lowerResolved();
        void lowerFailed(ProtosObjectValue error);
        void lowerCancelled();
        void invalidLowerFuture();
        void invocationFailed();
    }

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
                                                                "failed TextWriter lower Future has no Error")));
                case CANCELLED -> new LowerOutcome(Kind.CANCELLED, null);
                case PENDING ->
                        throw new IllegalStateException(
                                "TextWriter lower Future projected before terminalization");
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
        private final Object target;
        private final String selector;
        private final List<?> arguments;

        CallState(
                ProtosIoOperation operation,
                Object target,
                String selector,
                List<?> arguments) {
            this.operation = Objects.requireNonNull(operation, "operation");
            this.target = Objects.requireNonNull(target, "target");
            this.selector = Objects.requireNonNull(selector, "selector");
            this.arguments = List.copyOf(Objects.requireNonNull(arguments, "arguments"));
        }

        Object target() {
            return target;
        }

        String selector() {
            return selector;
        }

        List<?> arguments() {
            return arguments;
        }

        TargetInvocation invocationSucceeded(Object result) {
            return new TargetInvocation(
                    false,
                    Objects.requireNonNull(result, "TextWriter target invocation result"));
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
            return ProtosStandardFutureProtocol.awaitFutureForIoOperationContinuationForRuntime(
                    lower,
                    operation,
                    () -> LowerOutcome.fromFuture(lower));
        }
    }

    private ProtosTextWriterCPrimeExecution() {}

    public static Plan planForEnteredContext() {
        ProtosLanguageContext context =
                ProtosLanguageContext.currentIfEnteredForRuntime();
        if (context == null) {
            throw new IllegalStateException(
                    "TextWriter C-prime execution requires an entered Protos Context");
        }
        return context.textWriterCPrimePlanForRuntime();
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
                                    builder.createLocal("textWriterTargetCall", null);
                            BytecodeLocal child =
                                    builder.createLocal("textWriterTargetChild", null);
                            BytecodeLocal resume =
                                    builder.createLocal("textWriterTargetResume", null);
                            BytecodeLocal targetResult =
                                    builder.createLocal("textWriterTargetResult", null);
                            BytecodeLocal invocation =
                                    builder.createLocal("textWriterTargetInvocation", null);
                            BytecodeLocal lowerOutcome =
                                    builder.createLocal("textWriterLowerOutcome", null);
                            BytecodeLocal yielded =
                                    builder.createLocal("textWriterLowerYielded", null);

                            /*
                             * Map an ordinary guest Error/control transfer during target invocation
                             * to the writer's existing invocation-failure lane. The target Closure's
                             * ReturnHome is still closed exactly once by the normal composed-call
                             * TryFinally.
                             */
                            builder.beginTryCatch();

                            builder.beginBlock();
                            builder.beginStoreLocal(prepared);
                            builder.beginPrepareTextWriterTargetCall();
                            builder.emitLoadArgument(1);
                            builder.emitLoadArgument(0);
                            builder.endPrepareTextWriterTargetCall();
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
                            builder.beginWrapTextWriterTargetInvocation();
                            builder.emitLoadArgument(1);
                            builder.emitLoadLocal(targetResult);
                            builder.endWrapTextWriterTargetInvocation();
                            builder.endStoreLocal();
                            builder.endBlock();

                            builder.beginBlock();
                            builder.beginStoreLocal(invocation);
                            builder.beginTextWriterTargetInvocationFailed();
                            builder.emitLoadArgument(1);
                            builder.endTextWriterTargetInvocationFailed();
                            builder.endStoreLocal();
                            builder.endBlock();

                            builder.endTryCatch();

                            builder.beginStoreLocal(lowerOutcome);
                            builder.beginAwaitTextWriterTargetFuture();
                            builder.emitLoadArgument(1);
                            builder.emitLoadLocal(invocation);
                            builder.endAwaitTextWriterTargetFuture();
                            builder.endStoreLocal();

                            /*
                             * The lower Future wait is itself the operation-owned PLAT029 leaf.
                             * It is not another guest send to Future.value() and therefore cannot
                             * be redirected by Future instance mutation/override.
                             */
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
                            builder.beginResumeTextWriterTargetFutureWait();
                            builder.emitLoadArgument(0);
                            builder.emitLoadLocal(yielded);
                            builder.emitLoadLocal(resume);
                            builder.endResumeTextWriterTargetFutureWait();
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
            Object target,
            String selector,
            List<?> arguments,
            Completion completion) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(completion, "completion");

        CallState state = new CallState(operation, target, selector, arguments);
        ProtosBytecodeIoOperationExecution.installAndScheduleWithEntryState(
                operation,
                plan.target,
                operation.deferredCPrimeActivationForRuntime(),
                state,
                (current, result) -> {
                    if (!(result instanceof LowerOutcome outcome)) {
                        throw new IllegalStateException(
                                "TextWriter C-prime root returned an invalid outcome carrier");
                    }
                    switch (outcome.kind) {
                        case RESOLVED -> completion.lowerResolved();
                        case FAILED -> completion.lowerFailed(outcome.error);
                        case CANCELLED -> completion.lowerCancelled();
                        case INVALID -> completion.invalidLowerFuture();
                        case INVOCATION_FAILED -> completion.invocationFailed();
                    }
                },
                (current, error) -> completion.invocationFailed());
    }
}
