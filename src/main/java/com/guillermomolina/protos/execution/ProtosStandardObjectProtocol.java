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
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosCoreErrors;
import com.guillermomolina.protos.runtime.ProtosDynamicControlState;
import com.guillermomolina.protos.runtime.ProtosEvaluatorContinuation;
import com.guillermomolina.protos.runtime.ProtosNonLocalReturnException;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.guillermomolina.protos.runtime.ProtosTask;
import java.util.List;

public final class ProtosStandardObjectProtocol {
    private ProtosStandardObjectProtocol() {}

    public static void install() {
        ProtosObjectValue object = ProtosObjectValue.rootObject();
        ProtosStandardBooleanProtocol.install();
        if (!object.hasLocalSlot("call")) {
            object.createLocalSlot(
                    "call",
                    ProtosClosureValue.nativeClosure(
                            (activation, supplied) -> {
                                Object receiver = activation.receiver();
                                if (receiver instanceof ProtosClosureValue closure) {
                                    return ProtosClosureInvoker.invoke(closure, supplied, activation);
                                }
                                if (!(receiver instanceof ProtosObjectValue prototype)) {
                                    throw new ProtosSignalException(ProtosCoreErrors.newError(activation));
                                }
                                ProtosObjectValue instance = new ProtosObjectValue(prototype);
                                ProtosInvocation.invokeMessage(instance, "init", supplied, activation);
                                return instance;
                            }));
        }
        if (!object.hasLocalSlot("identityHash")) {
            object.createLocalSlot("identityHash", ProtosClosureValue.nativeClosure((activation, supplied) -> {
                if (!supplied.isEmpty()) throw new ProtosSignalException(ProtosCoreErrors.newError(activation));
                return new com.guillermomolina.protos.runtime.ProtosIntegerValue(com.guillermomolina.protos.runtime.ProtosIdentity.identityHash(activation.receiver()));
            }));
        }
        if (!object.hasLocalSlot("ensure")) {
            object.createLocalSlot(
                    "ensure",
                    ProtosClosureValue.nativeClosure(ProtosStandardObjectProtocol::ensure));
        }
    }

    private static Object ensure(ProtosActivation activation, List<?> supplied) {
        Object receiver = activation.receiver();
        if (!(receiver instanceof ProtosClosureValue body)) {
            throw invalid(activation);
        }
        if (supplied.size() != 1) {
            throw invalid(activation);
        }
        Object cleanupValue = supplied.get(0);
        if (!(cleanupValue instanceof ProtosClosureValue cleanup)) {
            throw invalid(activation);
        }

        ProtosDynamicControlState state = activation.dynamicControlState();
        ProtosDynamicControlState.Frame frame =
                state.enterFrame(activation, ProtosDynamicControlState.FrameKind.ENSURE);

        boolean replayingCleanup =
                frame.ensurePhase() == ProtosDynamicControlState.EnsurePhase.CLEANUP;

        if (!replayingCleanup) {
            try {
                Object result = ProtosClosureInvoker.invoke(body, List.of(), activation);
                state.beginEnsureCleanup(
                        frame,
                        ProtosDynamicControlState.EnsureExitKind.NORMAL,
                        result,
                        replayCursor(activation));
            } catch (ProtosEvaluatorSuspension suspension) {
                throw suspension;
            } catch (ProtosSignalException pending) {
                state.beginEnsureCleanup(
                        frame,
                        ProtosDynamicControlState.EnsureExitKind.ERROR,
                        pending,
                        replayCursor(activation));
            } catch (ProtosNonLocalReturnException pending) {
                state.beginEnsureCleanup(
                        frame,
                        ProtosDynamicControlState.EnsureExitKind.RETURN,
                        pending,
                        replayCursor(activation));
            } catch (ProtosTaskCancellationException pending) {
                state.beginEnsureCleanup(
                        frame,
                        ProtosDynamicControlState.EnsureExitKind.CANCELLATION,
                        pending,
                        replayCursor(activation));
            } catch (RuntimeException hostFailure) {
                state.leaveFrame(frame);
                throw hostFailure;
            }
        } else {
            resumeEnsureCleanupReplay(frame, activation);
        }

        return finishEnsure(state, frame, cleanup, activation);
    }

    private static Object finishEnsure(
            ProtosDynamicControlState state,
            ProtosDynamicControlState.Frame frame,
            ProtosClosureValue cleanup,
            ProtosActivation activation) {
        ProtosDynamicControlState.EnsureExitKind exitKind =
                frame.ensureExitKind().orElseThrow(
                        () -> new IllegalStateException("ensure cleanup has no pending exit"));
        Object outcome =
                frame.ensureOutcome().orElseThrow(
                        () -> new IllegalStateException("ensure cleanup has no pending outcome"));

        runCleanup(state, frame, cleanup, activation);

        return switch (exitKind) {
            case NORMAL -> outcome;
            case ERROR -> throw (ProtosSignalException) outcome;
            case RETURN -> throw (ProtosNonLocalReturnException) outcome;
            case CANCELLATION -> throw (ProtosTaskCancellationException) outcome;
        };
    }

    private static void runCleanup(
            ProtosDynamicControlState state,
            ProtosDynamicControlState.Frame frame,
            ProtosClosureValue cleanup,
            ProtosActivation activation) {
        try {
            ProtosClosureInvoker.invoke(cleanup, List.of(), activation);
            state.leaveFrame(frame);
        } catch (ProtosEvaluatorSuspension suspension) {
            // The frame remains in CLEANUP phase so replay resumes cleanup, never the body.
            throw suspension;
        } catch (RuntimeException laterTransfer) {
            if (frame.ensureExitKind().orElse(null)
                    == ProtosDynamicControlState.EnsureExitKind.CANCELLATION) {
                ProtosTask task =
                        activation.task()
                                .orElseThrow(
                                        () ->
                                                new IllegalStateException(
                                                        "cancellation cleanup requires a task"));
                if (!task.supersedeCancellationUnwind()) {
                    throw new IllegalStateException(
                            "cleanup transfer could not supersede cancellation unwind");
                }
            }
            state.leaveFrame(frame);
            throw laterTransfer;
        }
    }

    private static int replayCursor(ProtosActivation activation) {
        if (activation.task().isEmpty()) {
            return -1;
        }
        ProtosEvaluatorContinuation continuation =
                activation.task().orElseThrow().evaluatorContinuation();
        return continuation.segmentActive() ? continuation.cursorPosition() : -1;
    }

    private static void resumeEnsureCleanupReplay(
            ProtosDynamicControlState.Frame frame,
            ProtosActivation activation) {
        int targetCursor = frame.ensureBodyReplayCursor();
        if (targetCursor < 0) {
            throw new IllegalStateException(
                    "suspended ensure cleanup requires a replay cursor checkpoint");
        }
        ProtosEvaluatorContinuation continuation =
                activation.task()
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "suspended ensure cleanup requires a task"))
                        .evaluatorContinuation();
        continuation.skipInvocationReplayTo(targetCursor);
    }

    private static ProtosSignalException invalid(ProtosActivation activation) {
        return ProtosCoreErrors.signal(activation, ProtosCoreErrors.newError(activation));
    }

}
