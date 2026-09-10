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
import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosCoreErrors;
import com.guillermomolina.protos.runtime.ProtosDynamicControlState;
import com.guillermomolina.protos.runtime.ProtosEvaluatorContinuation;
import com.guillermomolina.protos.runtime.ProtosNonLocalReturnException;
import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.guillermomolina.protos.runtime.ProtosStringValue;
import com.guillermomolina.protos.runtime.ProtosTask;
import com.guillermomolina.protos.runtime.ProtosValueLookup;
import java.util.ArrayList;
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
        if (!object.hasLocalSlot("hasSlot")) {
            object.createLocalSlot(
                    "hasSlot",
                    ProtosClosureValue.nativeClosure(
                            ProtosStandardObjectProtocol::hasSlot));
        }
        if (!object.hasLocalSlot("slotValue")) {
            object.createLocalSlot(
                    "slotValue",
                    ProtosClosureValue.nativeClosure(
                            ProtosStandardObjectProtocol::slotValue));
        }
        if (!object.hasLocalSlot("slotNames")) {
            object.createLocalSlot(
                    "slotNames",
                    ProtosClosureValue.nativeClosure(
                            ProtosStandardObjectProtocol::slotNames));
        }
        if (!object.hasLocalSlot("removeSlot")) {
            object.createLocalSlot(
                    "removeSlot",
                    ProtosClosureValue.nativeClosure(
                            ProtosStandardObjectProtocol::removeSlot));
        }
        if (!object.hasLocalSlot("close")) {
            object.createLocalSlot(
                    "close",
                    ProtosClosureValue.nativeClosure(
                            ProtosStandardObjectProtocol::close));
        }
        if (!object.hasLocalSlot("without")) {
            object.createLocalSlot(
                    "without",
                    ProtosClosureValue.nativeClosure(
                            ProtosStandardObjectProtocol::without));
        }
        if (!object.hasLocalSlot("alias")) {
            object.createLocalSlot(
                    "alias",
                    ProtosClosureValue.nativeClosure(
                            ProtosStandardObjectProtocol::alias));
        }
        if (!object.hasLocalSlot("parent")) {
            object.createLocalSlot(
                    "parent",
                    ProtosClosureValue.nativeClosure(
                            (activation, supplied) -> {
                                if (!supplied.isEmpty()) {
                                    throw invalid(activation);
                                }
                                var parent =
                                        ProtosValueLookup.delegationParent(
                                                activation.receiver(),
                                                activation
                                                        .prelude()
                                                        .orElseThrow(
                                                                () ->
                                                                        new IllegalStateException(
                                                                                "Object.parent requires Core prelude")));
                                if (parent.isEmpty()) {
                                    throw invalid(activation);
                                }
                                return parent.orElseThrow();
                            }));
        }
        if (!object.hasLocalSlot("ensure")) {
            object.createLocalSlot(
                    "ensure",
                    ProtosClosureValue.nativeClosure(ProtosStandardObjectProtocol::ensure));
        }
        if (!object.hasLocalSlot("while")) {
            object.createLocalSlot(
                    "while",
                    ProtosClosureValue.nativeClosure(ProtosStandardObjectProtocol::whileLoop));
        }
    }

    private static Object hasSlot(ProtosActivation activation, List<?> supplied) {
        if (supplied.size() != 1
                || !(supplied.get(0) instanceof ProtosStringValue name)) {
            throw invalid(activation);
        }

        Object receiver = activation.receiver();
        if (receiver instanceof ProtosObjectValue ordinary) {
            return ProtosBooleanValue.of(ordinary.hasLocalSlot(name.value()));
        }
        return ProtosBooleanValue.FALSE;
    }

    private static Object slotNames(ProtosActivation activation, List<?> supplied) {
        if (!supplied.isEmpty()) {
            throw invalid(activation);
        }

        List<String> names = new ArrayList<>();
        Object receiver = activation.receiver();
        if (receiver instanceof ProtosObjectValue ordinary) {
            names.addAll(ordinary.localSlotsSnapshot().keySet());
            names.sort(ProtosStandardObjectProtocol::compareUnicodeScalarStrings);
        }

        List<ProtosStringValue> values = new ArrayList<>(names.size());
        for (String name : names) {
            values.add(new ProtosStringValue(name));
        }
        return activation
                .prelude()
                .orElseThrow(
                        () -> new IllegalStateException("Object.slotNames requires Core prelude"))
                .newArray(values);
    }

    private static int compareUnicodeScalarStrings(String left, String right) {
        int leftOffset = 0;
        int rightOffset = 0;
        while (leftOffset < left.length() && rightOffset < right.length()) {
            int leftCodePoint = left.codePointAt(leftOffset);
            int rightCodePoint = right.codePointAt(rightOffset);
            if (leftCodePoint != rightCodePoint) {
                return Integer.compare(leftCodePoint, rightCodePoint);
            }
            leftOffset += Character.charCount(leftCodePoint);
            rightOffset += Character.charCount(rightCodePoint);
        }
        if (leftOffset == left.length()) {
            return rightOffset == right.length() ? 0 : -1;
        }
        return 1;
    }

    private static Object slotValue(ProtosActivation activation, List<?> supplied) {
        if (supplied.size() != 1
                || !(supplied.get(0) instanceof ProtosStringValue name)) {
            throw invalid(activation);
        }

        Object receiver = activation.receiver();
        if (!(receiver instanceof ProtosObjectValue ordinary)) {
            throw invalid(activation);
        }

        var value = ordinary.readLocalSlot(name.value());
        if (value.isEmpty()) {
            throw invalid(activation);
        }
        return value.orElseThrow();
    }

    private static Object removeSlot(ProtosActivation activation, List<?> supplied) {
        if (supplied.size() != 1
                || !(supplied.get(0) instanceof ProtosStringValue name)) {
            throw invalid(activation);
        }
        if (!(activation.receiver() instanceof ProtosObjectValue receiver)) {
            throw invalid(activation);
        }

        try {
            return receiver.removeLocalSlot(name.value());
        } catch (IllegalStateException invalidRemoval) {
            throw invalid(activation);
        }
    }

    private static Object close(ProtosActivation activation, List<?> supplied) {
        if (!supplied.isEmpty()) {
            throw invalid(activation);
        }
        if (!(activation.receiver() instanceof ProtosObjectValue receiver)) {
            throw invalid(activation);
        }
        return receiver.close();
    }

    private static Object without(ProtosActivation activation, List<?> supplied) {
        if (supplied.size() != 1
                || !(supplied.get(0) instanceof ProtosStringValue name)) {
            throw invalid(activation);
        }
        if (!(activation.receiver() instanceof ProtosObjectValue receiver)) {
            throw invalid(activation);
        }
        if (!receiver.hasLocalSlot(name.value())) {
            throw invalid(activation);
        }
        return receiver.withoutLocalSlot(name.value());
    }

    private static Object alias(ProtosActivation activation, List<?> supplied) {
        if (supplied.size() != 2
                || !(supplied.get(0) instanceof ProtosStringValue sourceName)
                || !(supplied.get(1) instanceof ProtosStringValue aliasName)) {
            throw invalid(activation);
        }
        if (!(activation.receiver() instanceof ProtosObjectValue receiver)) {
            throw invalid(activation);
        }
        if (!receiver.hasLocalSlot(sourceName.value())
                || receiver.hasLocalSlot(aliasName.value())) {
            throw invalid(activation);
        }
        return receiver.aliasLocalSlot(sourceName.value(), aliasName.value());
    }

    private static Object whileLoop(ProtosActivation activation, List<?> supplied) {
        Object receiver = activation.receiver();
        if (!(receiver instanceof ProtosClosureValue condition)) {
            throw invalid(activation);
        }
        if (supplied.size() != 1) {
            throw invalid(activation);
        }
        Object bodyValue = supplied.get(0);
        if (!(bodyValue instanceof ProtosClosureValue body)) {
            throw invalid(activation);
        }

        ProtosDynamicControlState state = activation.dynamicControlState();
        ProtosDynamicControlState.Frame frame =
                state.enterFrame(activation, ProtosDynamicControlState.FrameKind.WHILE);
        int callbackCheckpoint =
                state.bindWhileCallbackCheckpoint(frame, replayCursor(activation));

        while (true) {
            if (frame.whilePhase() == ProtosDynamicControlState.WhilePhase.CONDITION) {
                Object conditionResult;
                try {
                    conditionResult = ProtosClosureInvoker.invoke(condition, List.of(), activation);
                } catch (ProtosEvaluatorSuspension suspension) {
                    throw suspension;
                } catch (RuntimeException transfer) {
                    state.leaveFrame(frame);
                    throw transfer;
                }

                compactCompletedWhileCallback(activation, callbackCheckpoint);
                if (conditionResult == ProtosBooleanValue.FALSE) {
                    state.leaveFrame(frame);
                    return ProtosNullValue.INSTANCE;
                }
                if (conditionResult != ProtosBooleanValue.TRUE) {
                    state.leaveFrame(frame);
                    throw invalid(activation);
                }
                state.completeWhileCondition(frame);
            }

            try {
                ProtosClosureInvoker.invoke(body, List.of(), activation);
            } catch (ProtosEvaluatorSuspension suspension) {
                throw suspension;
            } catch (RuntimeException transfer) {
                state.leaveFrame(frame);
                throw transfer;
            }
            compactCompletedWhileCallback(activation, callbackCheckpoint);
            state.completeWhileBody(frame);
        }
    }

    private static void compactCompletedWhileCallback(
            ProtosActivation activation, int callbackCheckpoint) {
        if (callbackCheckpoint < 0) {
            return;
        }
        ProtosEvaluatorContinuation continuation =
                activation.task()
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "while callback compaction requires a task"))
                        .evaluatorContinuation();
        // Ordinal zero belongs to the enclosing native Object.while activation itself.
        continuation.compactCompletedChildExecution(callbackCheckpoint, 1);
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
