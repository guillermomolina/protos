/*
 * THE LICENSED WORK IS PROVIDED UNDER THE TERMS OF THE ADAPTIVE PUBLIC LICENSE
 * ("LICENSE") AS FIRST COMPLETED BY: Guillermo Adrián Molina. See LICENSE.TXT.
 */
package com.guillermomolina.protos.execution;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosEvaluatorContinuation;
import com.guillermomolina.protos.runtime.ProtosTask;
import com.oracle.truffle.api.frame.VirtualFrame;
import java.util.Objects;

/** Internal bridge between ordinary Truffle evaluation and Actor-local cooperative tasks. */
public final class ProtosEvaluatorBridge {
    private ProtosEvaluatorBridge() {}

    static Object execute(ProtosExpressionNode node, VirtualFrame frame) {
        if (frame == null) {
            return node.executeDirect(null);
        }
        Object[] arguments = frame.getArguments();
        if (arguments.length == 0 || !(arguments[0] instanceof ProtosActivation activation)) {
            return node.executeDirect(frame);
        }
        ProtosTask task = activation.task().orElse(null);
        if (task == null || !task.evaluatorContinuation().segmentActive()) {
            return node.executeDirect(frame);
        }

        ProtosEvaluatorContinuation continuation = task.evaluatorContinuation();
        ProtosEvaluatorContinuation.Entry entry = continuation.enter(node);
        if (entry.completed()) return entry.result();
        try {
            Object result = node.executeDirect(frame);
            continuation.complete(entry, result);
            return result;
        } catch (ProtosEvaluatorSuspension | ProtosTaskCancellationException transfer) {
            continuation.markControlUnwind();
            continuation.leaveIncomplete(entry);
            throw transfer;
        } catch (RuntimeException transfer) {
            continuation.leaveIncomplete(entry);
            throw transfer;
        }
    }

    /** Explicit suspension boundary used by Future.value() and controlled internal tests. */
    public static void await(ProtosActivation activation, ProtosTask.WaitDependency dependency) {
        Objects.requireNonNull(activation, "activation");
        Objects.requireNonNull(dependency, "dependency");
        ProtosTask task = activation.task().orElseThrow(
                () -> new IllegalStateException("suspension requires an Actor-local Protos task"));

        if (task.cancellationRequested()) {
            if (!task.observeCancellation()) {
                throw new IllegalStateException("pending cancellation was not observable");
            }
            task.evaluatorContinuation().markControlUnwind();
            throw new ProtosTaskCancellationException();
        }
        if (task.consumeResume(dependency)) return;

        if (!task.suspend(dependency)) return;
        task.evaluatorContinuation().markControlUnwind();
        throw new ProtosEvaluatorSuspension();
    }

    public static boolean isControlUnwindInProgress(ProtosActivation activation) {
        return activation.task().map(task -> task.evaluatorContinuation().controlUnwind()).orElse(false);
    }
}
