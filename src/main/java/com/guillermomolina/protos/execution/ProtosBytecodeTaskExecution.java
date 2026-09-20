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
import com.guillermomolina.protos.runtime.ProtosNonLocalReturnException;
import com.guillermomolina.protos.runtime.ProtosNullValue;
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
 * top-level {@link ContinuationResult} is retained only by the suspended {@link ProtosTask}.
 * Nested continuation frames remain opaque Truffle state; this bridge walks their current result
 * chain only far enough to find the backend-private native suspension leaf and its dependency.
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

    static void executePreparedClosure(
            ProtosTask task,
            ProtosBytecodeRootNode.PreparedClosureCall prepared) {
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(prepared, "prepared");
        if (prepared.isNative() || prepared.isImmediate()) {
            throw new IllegalArgumentException(
                    "Task-owned C-prime Closure entry requires a source-backed Bytecode call");
        }
        runPreparedSegment(
                task,
                prepared,
                () -> prepared.bodyTarget().call(prepared.activation()));
    }

    static void executePreparedEntry(
            ProtosTask task,
            CallTarget target,
            ProtosBytecodeRootNode.PreparedClosureCall prepared) {
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(prepared, "prepared");

        if (!prepared.isImmediate()
                && prepared.activation().task().orElse(null) != task) {
            throw new IllegalArgumentException(
                    "Task C-prime entry prepared call belongs to another Task");
        }

        runSegment(
                task,
                prepared.activation(),
                () -> target.call(
                        prepared.activation(),
                        prepared));
    }

    private static void resumePreparedPublished(
            ProtosTask task,
            ProtosBytecodeRootNode.PreparedClosureCall prepared,
            ContinuationResult continuation,
            ProtosTask.WaitDependency dependency) {
        runPreparedSegment(
                task,
                prepared,
                () -> {
                    if (task.cancellationRequested()) {
                        return continuation.continueWith(
                                beginCancellationTransfer(
                                        task,
                                        "published Task-owned Closure C-prime resume"));
                    }
                    if (!task.consumeResume(dependency)) {
                        throw new IllegalStateException(
                                "published Task-owned Closure continuation resumed without its dependency");
                    }
                    return continuation.continueWith(ProtosNullValue.INSTANCE);
                });
    }

    private static void runPreparedSegment(
            ProtosTask task,
            ProtosBytecodeRootNode.PreparedClosureCall prepared,
            java.util.function.Supplier<Object> segment) {
        try {
            Object outcome =
                    Objects.requireNonNull(
                            segment.get(),
                            "Task-owned Closure C-prime segment returned null");
            drivePreparedOutcome(task, prepared, outcome);
        } catch (ProtosBytecodeControlTransferException bridged) {
            try {
                Object handled =
                        prepared.handleControlTransfer(
                                bridged.transfer());
                drivePreparedOutcome(task, prepared, handled);
            } catch (ProtosTaskCancellationException cancelled) {
                prepared.complete();
                finishCancellationUnwind(task);
            } catch (ProtosNonLocalReturnException escaped) {
                /*
                 * D177: the captured return home is not reachable from this Task's own
                 * activation chain (it belongs to a different, currently-suspended Task).
                 * Treat it exactly like a completed home per CALLABLES.md §14: signal
                 * InvalidReturn as an ordinary Task failure instead of letting the raw
                 * escape propagate uncaught.
                 */
                prepared.complete();
                task.fail(ProtosCoreErrors.newInvalidReturn(prepared.activation()));
            }
        } catch (ProtosTaskCancellationException cancelled) {
            prepared.complete();
            finishCancellationUnwind(task);
        } catch (ProtosSignalException signalled) {
            prepared.complete();
            task.fail(signalled.error());
        } catch (RuntimeException failure) {
            prepared.complete();
            throw failure;
        } catch (Error failure) {
            prepared.complete();
            throw failure;
        }
    }

    private static void drivePreparedOutcome(
            ProtosTask task,
            ProtosBytecodeRootNode.PreparedClosureCall prepared,
            Object initialOutcome) {
        Object outcome = initialOutcome;
        while (true) {
            if (!(outcome instanceof ContinuationResult continuation)) {
                task.complete(prepared.finish(outcome));
                return;
            }

            ProtosNativeSuspension leaf =
                    nativeSuspensionLeaf(continuation);
            ProtosTask.WaitDependency dependency =
                    leaf.dependency();

            if (!task.beginSuspensionCapture(dependency)) {
                if (task.cancellationRequested()) {
                    outcome =
                            Objects.requireNonNull(
                                    continuation.continueWith(
                                            beginCancellationTransfer(
                                                    task,
                                                    "pre-publication Task-owned Closure C-prime suspension")),
                                    "cancelled Task-owned Closure continuation returned null");
                    continue;
                }
                outcome =
                        Objects.requireNonNull(
                                continuation.continueWith(ProtosNullValue.INSTANCE),
                                "ready Task-owned Closure continuation returned null");
                continue;
            }

            task.publishSuspensionContinuation(
                    dependency,
                    resumedTask ->
                            resumePreparedPublished(
                                    resumedTask,
                                    prepared,
                                    continuation,
                                    dependency));
            return;
        }
    }

    private static void resumePublished(
            ProtosTask task,
            ProtosActivation activation,
            ContinuationResult continuation,
            ProtosTask.WaitDependency dependency) {
        runSegment(
                task,
                activation,
                () -> {
                    if (task.cancellationRequested()) {
                        return continuation.continueWith(
                                beginCancellationTransfer(
                                        task,
                                        "published C-prime resume"));
                    }
                    if (!task.consumeResume(dependency)) {
                        throw new IllegalStateException(
                                "published C-prime continuation resumed without its dependency");
                    }
                    return continuation.continueWith(
                            ProtosNullValue.INSTANCE);
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
                task.fail(ProtosCoreErrors.newInvalidReturn(activation));
                return;
            }
            throw bridged;
        } catch (ProtosTaskCancellationException cancelled) {
            finishCancellationUnwind(task);
        } catch (ProtosNonLocalReturnException escaped) {
            task.fail(ProtosCoreErrors.newInvalidReturn(activation));
        } catch (ProtosSignalException signalled) {
            task.fail(signalled.error());
        }
    }

    private static void driveOutcome(
            ProtosTask task,
            ProtosActivation activation,
            Object initialOutcome) {
        Object outcome = initialOutcome;
        while (true) {
            if (!(outcome instanceof ContinuationResult continuation)) {
                task.complete(outcome);
                return;
            }

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
                                    continuation.continueWith(
                                            beginCancellationTransfer(
                                                    task,
                                                    "pre-publication C-prime suspension")),
                                    "cancelled C-prime continuation returned null");
                    continue;
                }
                outcome =
                        Objects.requireNonNull(
                                continuation.continueWith(
                                        ProtosNullValue.INSTANCE),
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

    private static ProtosNativeSuspension nativeSuspensionLeaf(
            ContinuationResult top) {
        Object current = top;
        while (current instanceof ContinuationResult continuation) {
            current = continuation.getResult();
        }
        if (!(current instanceof ProtosNativeSuspension suspension)) {
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
