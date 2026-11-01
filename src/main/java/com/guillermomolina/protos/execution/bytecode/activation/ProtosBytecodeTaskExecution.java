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
import com.guillermomolina.protos.runtime.control.ProtosNonLocalReturnException;
import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.guillermomolina.protos.runtime.ProtosNumericValueSupport;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.guillermomolina.protos.runtime.ProtosTask;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.bytecode.ContinuationResult;
import java.util.Objects;

/**
 * PERF006-B3 adapter between one complete Bytecode C-prime chain and Task-local suspension
 * publication.
 *
 * <p>This class owns no scheduler state and no global continuation registry. The complete
 * top-level pending result, a {@link ContinuationResult} or the I112 terminal transport ({@link
 * ProtosTailCallContinuation}) of a root that ended in a suspended terminal call, is retained
 * only by the suspended {@link ProtosTask}. Nested continuation frames and transports remain
 * opaque state; this bridge walks their current result chain only far enough to find the
 * backend-private native suspension leaf and its dependency, and resumes the retained result
 * itself, never the call from its beginning.
 *
 * <p>A ready prerequisite is resumed synchronously without publishing a suspension. Otherwise
 * B3A's capture-pending/publication protocol atomically installs the complete top-level
 * continuation before the Task can become dispatchable.
 */
final class ProtosBytecodeTaskExecution {
    private ProtosBytecodeTaskExecution() {}

    static void execute(
            ProtosTask task,
            CallTarget target,
            ProtosActivation activation) {
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(activation, "activation");

        activation.attachTask(task);
        runSegment(
                task,
                activation,
                () -> target.call(activation));
    }

    /**
     * I115: a Task-owned entry made through the shared dispatcher. The
     * driver receives the dispatcher's outcome and resumes its continuation
     * without repeating the initial invocation; the callee roots own their
     * ReturnHomes.
     */
    static void executeRequest(ProtosTask task, ProtosCallRequest request) {
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(request, "request");
        runSegment(task, request.caller(), request::callUncached);
    }

    private static void resumePublished(
            ProtosTask task,
            ProtosActivation activation,
            Object continuation,
            ProtosTask.WaitDependency dependency) {
        runSegment(
                task,
                activation,
                () -> {
                    if (task.cancellationRequested()) {
                        return ProtosTailCallContinuation.resume(
                                continuation,
                                beginCancellationTransfer(
                                        task,
                                        "published C-prime resume"));
                    }
                    if (!task.consumeResume(dependency)) {
                        throw new IllegalStateException(
                                "published C-prime continuation resumed without its dependency");
                    }
                    return ProtosTailCallContinuation.resume(
                            continuation, ProtosNullValue.INSTANCE);
                });
    }

    private static void runSegment(
            ProtosTask task,
            ProtosActivation activation,
            java.util.function.Supplier<Object> segment) {
        try {
            Object outcome =
                    Objects.requireNonNull(
                            segment.get(),
                            "Bytecode C-prime segment returned null");
            driveOutcome(task, activation, outcome);
        } catch (ProtosBytecodeControlTransferException bridged) {
            if (bridged.transfer() instanceof ProtosTaskCancellationException) {
                finishCancellationUnwind(task);
                return;
            }
            if (bridged.transfer() instanceof ProtosNonLocalReturnException) {
                /*
                 * D177: the captured return home is not reachable from this Task's own
                 * activation chain. Treat it exactly like a completed home per
                 * CALLABLES.md §14: signal InvalidReturn as an ordinary Task failure
                 * instead of letting the raw escape propagate uncaught.
                 */
                task.fail(
                        ProtosCoreErrors.newInvalidReturn(activation),
                        ProtosDiagnosticTraceCapture.capture(bridged));
                return;
            }
            throw bridged;
        } catch (ProtosTaskCancellationException cancelled) {
            finishCancellationUnwind(task);
        } catch (ProtosNonLocalReturnException escaped) {
            /*
             * CLI008-C1: a raw ControlFlowException carries no guest stack and its frames have
             * already unwound; the frames still live here belong to the Task's host, so no trace
             * is attributed to this occurrence.
             */
            task.fail(ProtosCoreErrors.newInvalidReturn(activation));
        } catch (ProtosSignalException signalled) {
            task.fail(signalled.error(), ProtosDiagnosticTraceCapture.capture(signalled));
        }
    }

    private static void driveOutcome(
            ProtosTask task,
            ProtosActivation activation,
            Object initialOutcome) {
        Object outcome = initialOutcome;
        while (true) {
            if (!ProtosTailCallContinuation.isPending(outcome)) {
                task.complete(ProtosNumericValueSupport.guestValue(outcome));
                return;
            }
            Object continuation = outcome;

            ProtosNativeSuspension leaf =
                    nativeSuspensionLeaf(continuation);
            ProtosTask.WaitDependency dependency =
                    leaf.dependency();

            /*
             * The native implementation registered its wait relationship before
             * producing the leaf. If readiness/cancellation already won that
             * race, B3A declines capture and we either observe cancellation or
             * continue the already-ready C-prime chain immediately.
             */
            if (!task.beginSuspensionCapture(dependency)) {
                if (task.cancellationRequested()) {
                    outcome =
                            Objects.requireNonNull(
                                    ProtosTailCallContinuation.resume(
                                            continuation,
                                            beginCancellationTransfer(
                                                    task,
                                                    "pre-publication C-prime suspension")),
                                    "cancelled C-prime continuation returned null");
                    continue;
                }
                outcome =
                        Objects.requireNonNull(
                                ProtosTailCallContinuation.resume(
                                        continuation, ProtosNullValue.INSTANCE),
                                "ready C-prime continuation returned null");
                continue;
            }

            task.publishSuspensionContinuation(
                    dependency,
                    resumedTask ->
                            resumePublished(
                                    resumedTask,
                                    activation,
                                    continuation,
                                    dependency));
            return;
        }
    }

    /**
     * A synchronous foreign callback inside {@code task}: the request runs
     * through the shared dispatcher and any suspension is resolved in place
     * (or rejected with an Error) without publishing a continuation.
     */
    static Object invokeNestedSynchronous(
            ProtosTask task,
            ProtosCallRequest request,
            ProtosActivation errorSite) {
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(errorSite, "errorSite");
        task.enterSynchronousForeignCallbackForRuntime();
        try {
            Object outcome = request.callUncached();
            while (ProtosTailCallContinuation.isPending(outcome)) {
                outcome =
                        ProtosTailCallContinuation.resume(
                                outcome,
                                nestedResumeValue(
                                        task,
                                        nativeSuspensionLeaf(outcome).dependency(),
                                        errorSite));
            }
            Objects.requireNonNull(outcome, "nested synchronous C-prime call returned null");
            return ProtosNumericValueSupport.guestValue(outcome);
        } catch (ProtosBytecodeControlTransferException bridged) {
            throw bridged.transfer();
        } finally {
            task.exitSynchronousForeignCallbackForRuntime();
        }
    }

    private static Object nestedResumeValue(
            ProtosTask task, ProtosTask.WaitDependency dependency, ProtosActivation errorSite) {
        try {
            if (task.beginSuspensionCapture(dependency)) {
                throw new IllegalStateException(
                        "suspension capture began inside a synchronous foreign callback");
            }
        } catch (ProtosTask.SuspensionInForeignExtentRejected rejected) {
            return new ProtosSignalException(ProtosCoreErrors.newError(errorSite));
        }
        if (task.cancellationRequested()) {
            return beginCancellationTransfer(task, "synchronous foreign callback suspension");
        }
        return ProtosNullValue.INSTANCE;
    }

    /** Walks Truffle continuations and I112 terminal transports without consuming them. */
    private static ProtosNativeSuspension nativeSuspensionLeaf(Object top) {
        if (!(ProtosTailCallContinuation.suspensionLeaf(top)
                instanceof ProtosNativeSuspension suspension)) {
            throw new UnsupportedOperationException(
                    "PERF006-B3 Task-owned C-prime suspension requires a native suspension leaf");
        }
        return suspension;
    }

    private static ProtosTaskCancellationException beginCancellationTransfer(
            ProtosTask task,
            String boundary) {
        if (!task.beginContinuationCancellationUnwindForRuntime()) {
            throw new IllegalStateException(
                    boundary + " cancellation was not observable");
        }
        return new ProtosTaskCancellationException();
    }

    private static void finishCancellationUnwind(ProtosTask task) {
        if (!task.finishCancellationUnwind()) {
            throw new IllegalStateException(
                    "C-prime cancellation transfer escaped without an active cancellation unwind");
        }
    }
}
