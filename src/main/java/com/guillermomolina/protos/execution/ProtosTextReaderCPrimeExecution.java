/* APL-1.0 licensed work; see LICENSE.TXT. */
package com.guillermomolina.protos.execution;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosFutureValue;
import com.guillermomolina.protos.runtime.ProtosIoOperation;
import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.guillermomolina.protos.runtime.ProtosTextReader;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeLocal;
import com.oracle.truffle.api.bytecode.BytecodeRootNodes;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import java.util.List;
import java.util.Objects;

/**
 * Private PLAT029 execution plan for one active TextReader readText/readLine operation.
 *
 * <p>The TextReader retains decoder/buffer/ordering semantics. This class owns only the
 * suspendible guest-control extent:
 *
 * <pre>
 * source.read(max)
 *     -> await returned Future
 *     -> apply inert lower outcome to TextReader leaf state
 *     -> repeat while the same outer read still needs input
 * </pre>
 *
 * <p>There is no hidden Task and no Java callback may invoke the next guest read.
 */
public final class ProtosTextReaderCPrimeExecution {
    public static final class Plan {
        private final RootCallTarget target;

        private Plan(RootCallTarget target) {
            this.target = Objects.requireNonNull(target, "target");
        }
    }

    static final class Advance {
        private static final Advance NEED_INPUT = new Advance(true);
        private static final Advance TERMINAL = new Advance(false);

        private final boolean needsInput;

        private Advance(boolean needsInput) {
            this.needsInput = needsInput;
        }

        static Advance needInput() {
            return NEED_INPUT;
        }

        static Advance terminal() {
            return TERMINAL;
        }

        boolean needsInput() {
            return needsInput;
        }
    }

    static final class SourceInvocation {
        private final boolean failed;
        private final Object result;
        private final ProtosObjectValue exactError;

        private SourceInvocation(boolean failed, Object result, ProtosObjectValue exactError) {
            this.failed = failed;
            this.result = result;
            this.exactError = exactError;
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
        private final ProtosObjectValue exactError;

        private LowerOutcome(Kind kind, ProtosFutureValue lower, ProtosObjectValue exactError) {
            this.kind = Objects.requireNonNull(kind, "kind");
            this.lower = lower;
            this.exactError = exactError;
        }

        static LowerOutcome fromFuture(ProtosFutureValue lower) {
            if (lower.state() == ProtosFutureValue.State.PENDING) {
                throw new IllegalStateException(
                        "TextReader lower Future projected before terminalization");
            }
            return new LowerOutcome(Kind.FUTURE_TERMINAL, lower, null);
        }

        static LowerOutcome invalid() {
            return new LowerOutcome(Kind.INVALID, null, null);
        }

        static LowerOutcome invocationFailed(ProtosObjectValue exactError) {
            return new LowerOutcome(Kind.INVOCATION_FAILED, null, exactError);
        }

        static LowerOutcome outerTerminal() {
            return new LowerOutcome(Kind.OUTER_TERMINAL, null, null);
        }
    }

    static final class CallState {
        private final ProtosIoOperation operation;
        private final ProtosTextReader reader;
        private final Object source;

        CallState(ProtosIoOperation operation, ProtosTextReader reader, Object source) {
            this.operation = Objects.requireNonNull(operation, "operation");
            this.reader = Objects.requireNonNull(reader, "reader");
            this.source = Objects.requireNonNull(source, "source");
        }

        Object source() {
            return source;
        }

        List<?> arguments() {
            return reader.sourceReadArgumentsForCPrimeRuntime();
        }

        SourceInvocation invocationSucceeded(Object result) {
            return new SourceInvocation(false, result, null);
        }

        SourceInvocation invocationFailed(AbstractTruffleException failure) {
            ProtosObjectValue exactError =
                    failure instanceof ProtosSignalException signalled
                            ? signalled.error()
                            : null;
            return new SourceInvocation(true, null, exactError);
        }

        Object awaitSourceFuture(SourceInvocation invocation) {
            Objects.requireNonNull(invocation, "invocation");
            if (invocation.failed) {
                return LowerOutcome.invocationFailed(invocation.exactError);
            }
            if (!(invocation.result instanceof ProtosFutureValue lower)) {
                return LowerOutcome.invalid();
            }
            if (!reader.observeLowerForCPrimeRuntime(operation, lower)) {
                return LowerOutcome.outerTerminal();
            }
            return ProtosStandardFutureProtocol.awaitFutureForIoOperationContinuationForRuntime(
                    lower,
                    operation,
                    () -> LowerOutcome.fromFuture(lower));
        }

        Advance applyLowerOutcome(LowerOutcome outcome) {
            Objects.requireNonNull(outcome, "outcome");
            if (operation.terminal() && outcome.kind != LowerOutcome.Kind.FUTURE_TERMINAL) {
                return Advance.terminal();
            }
            boolean needsInput =
                    switch (outcome.kind) {
                        case FUTURE_TERMINAL ->
                                reader.consumeLowerForCPrimeRuntime(operation, outcome.lower);
                        case INVALID -> reader.invalidLowerForCPrimeRuntime(operation);
                        case INVOCATION_FAILED ->
                                reader.invocationFailedForCPrimeRuntime(
                                        operation, outcome.exactError);
                        case OUTER_TERMINAL -> false;
                    };
            return needsInput ? Advance.needInput() : Advance.terminal();
        }
    }

    private ProtosTextReaderCPrimeExecution() {}

    public static Plan planForEnteredContext() {
        ProtosLanguageContext context = ProtosLanguageContext.currentIfEnteredForRuntime();
        if (context == null) {
            throw new IllegalStateException(
                    "TextReader C-prime execution requires an entered Protos Context");
        }
        return context.textReaderCPrimePlanForRuntime();
    }

    static Plan createPlan(ProtosLanguage language) {
        Objects.requireNonNull(language, "language");
        BytecodeRootNodes<ProtosBytecodeRootNode> roots =
                ProtosBytecodeRootNodeGen.create(
                        language,
                        BytecodeConfig.DEFAULT,
                        builder -> {
                            builder.beginRoot();

                            BytecodeLocal advance = builder.createLocal("textReaderAdvance", null);
                            BytecodeLocal prepared = builder.createLocal("textReaderSourceCall", null);
                            BytecodeLocal child = builder.createLocal("textReaderSourceChild", null);
                            BytecodeLocal resume = builder.createLocal("textReaderSourceResume", null);
                            BytecodeLocal sourceResult = builder.createLocal("textReaderSourceResult", null);
                            BytecodeLocal invocation = builder.createLocal("textReaderSourceInvocation", null);
                            BytecodeLocal lowerOutcome = builder.createLocal("textReaderLowerOutcome", null);
                            BytecodeLocal yielded = builder.createLocal("textReaderLowerYielded", null);

                            builder.beginStoreLocal(advance);
                            builder.emitTextReaderInitialNeedInput();
                            builder.endStoreLocal();

                            builder.beginWhile();
                            builder.beginIsTextReaderNeedInput();
                            builder.emitLoadLocal(advance);
                            builder.endIsTextReaderNeedInput();

                            builder.beginBlock();

                            /*
                             * Execute arbitrary source.read guest behavior under C-prime. Any
                             * guest Error is converted to inert invocation evidence so the
                             * TextReader can preserve its historical exact-Error behavior.
                             */
                            builder.beginTryCatch();

                            builder.beginBlock();
                            builder.beginStoreLocal(prepared);
                            builder.beginPrepareTextReaderSourceCall();
                            builder.emitLoadArgument(1);
                            builder.emitLoadArgument(0);
                            builder.endPrepareTextReaderSourceCall();
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

                            builder.beginStoreLocal(sourceResult);
                            builder.beginFinishClosureCall();
                            builder.emitLoadLocal(prepared);
                            builder.emitLoadLocal(child);
                            builder.endFinishClosureCall();
                            builder.endStoreLocal();

                            builder.endBlock();
                            builder.endTryFinally();

                            builder.beginStoreLocal(invocation);
                            builder.beginWrapTextReaderSourceInvocation();
                            builder.emitLoadArgument(1);
                            builder.emitLoadLocal(sourceResult);
                            builder.endWrapTextReaderSourceInvocation();
                            builder.endStoreLocal();
                            builder.endBlock();

                            builder.beginBlock();
                            builder.beginStoreLocal(invocation);
                            builder.beginTextReaderSourceInvocationFailed();
                            builder.emitLoadArgument(1);
                            builder.emitLoadException();
                            builder.endTextReaderSourceInvocationFailed();
                            builder.endStoreLocal();
                            builder.endBlock();

                            builder.endTryCatch();

                            builder.beginStoreLocal(lowerOutcome);
                            builder.beginAwaitTextReaderSourceFuture();
                            builder.emitLoadArgument(1);
                            builder.emitLoadLocal(invocation);
                            builder.endAwaitTextReaderSourceFuture();
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
                            builder.beginResumeTextReaderSourceFutureWait();
                            builder.emitLoadArgument(0);
                            builder.emitLoadLocal(yielded);
                            builder.emitLoadLocal(resume);
                            builder.endResumeTextReaderSourceFutureWait();
                            builder.endStoreLocal();
                            builder.endBlock();
                            builder.endWhile();

                            builder.beginStoreLocal(advance);
                            builder.beginApplyTextReaderLowerOutcome();
                            builder.emitLoadArgument(1);
                            builder.emitLoadLocal(lowerOutcome);
                            builder.endApplyTextReaderLowerOutcome();
                            builder.endStoreLocal();

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
            ProtosTextReader reader,
            Object source) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(reader, "reader");

        CallState state = new CallState(operation, reader, source);
        ProtosBytecodeIoOperationExecution.installAndScheduleWithEntryState(
                operation,
                plan.target,
                operation.deferredCPrimeActivationForRuntime(),
                state,
                (current, result) -> {
                    if (!current.terminal()) {
                        reader.unexpectedCPrimeCompletionForRuntime(current);
                    }
                },
                reader::cPrimeDriverFailedForRuntime);
    }
}
