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
import com.guillermomolina.protos.runtime.ProtosArrayValue;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosCoreErrors;
import com.guillermomolina.protos.runtime.ProtosDynamicControlState;
import com.guillermomolina.protos.runtime.control.ProtosNonLocalReturnException;
import com.guillermomolina.protos.runtime.callables.ProtosNativeClosureBody;
import com.guillermomolina.protos.runtime.ProtosMapValue;
import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.guillermomolina.protos.runtime.ProtosNumericValueSupport;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.guillermomolina.protos.runtime.ProtosStringValue;
import com.guillermomolina.protos.runtime.ProtosTask;
import com.guillermomolina.protos.runtime.ProtosValueLookup;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.Property;
import com.oracle.truffle.api.object.Shape;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ProtosStandardObjectProtocol {
    private static final ProtosNativeClosureBody STANDARD_CALL_BODY =
            ProtosStandardObjectProtocol::call;
    private static final ProtosClosureValue STANDARD_CALL =
            ProtosClosureValue.nativeClosure(STANDARD_CALL_BODY);
    private static final ProtosNativeClosureBody STANDARD_CONSTRUCT_BODY =
            ProtosStandardObjectProtocol::construct;
    private static final ProtosClosureValue STANDARD_CONSTRUCT =
            ProtosClosureValue.nativeClosure(STANDARD_CONSTRUCT_BODY);
    private static final ProtosNativeClosureBody STANDARD_ENSURE_BODY =
            ProtosStandardObjectProtocol::ensure;
    private static final ProtosClosureValue STANDARD_ENSURE =
            ProtosClosureValue.nativeClosure(STANDARD_ENSURE_BODY);

    /*
     * EXECUTION_AND_CONTROL: the four standard Closure loops reached by
     * ordinary dispatch (an extracted method, a reflective send, a receiver
     * that is not a Closure literal). A lowered loop whose operands are
     * Closures runs the same algorithm internally and never sends these
     * selectors.
     */
    /**
     * The four standard Closure loops: a pre-test loop's receiver is its
     * condition, a post-test loop's receiver is its body; the loop ends when
     * a completed condition is exactly {@code exitValue}.
     */
    enum StandardLoop {
        WHILE_TRUE(false, false),
        WHILE_FALSE(false, true),
        UNTIL_TRUE(true, true),
        UNTIL_FALSE(true, false);

        private final boolean postTest;
        private final Boolean exitValue;

        StandardLoop(boolean postTest, boolean exitValue) {
            this.postTest = postTest;
            this.exitValue = exitValue ? Boolean.TRUE : Boolean.FALSE;
        }

        boolean postTest() {
            return postTest;
        }

        Boolean exitValue() {
            return exitValue;
        }
    }

    private static final ProtosNativeClosureBody STANDARD_WHILE_TRUE_BODY =
            (activation, supplied) -> loop(StandardLoop.WHILE_TRUE, activation, supplied);
    private static final ProtosNativeClosureBody STANDARD_WHILE_FALSE_BODY =
            (activation, supplied) -> loop(StandardLoop.WHILE_FALSE, activation, supplied);
    private static final ProtosNativeClosureBody STANDARD_UNTIL_TRUE_BODY =
            (activation, supplied) -> loop(StandardLoop.UNTIL_TRUE, activation, supplied);
    private static final ProtosNativeClosureBody STANDARD_UNTIL_FALSE_BODY =
            (activation, supplied) -> loop(StandardLoop.UNTIL_FALSE, activation, supplied);
    private static final ProtosClosureValue STANDARD_WHILE_TRUE =
            ProtosClosureValue.nativeClosure(STANDARD_WHILE_TRUE_BODY);
    private static final ProtosClosureValue STANDARD_WHILE_FALSE =
            ProtosClosureValue.nativeClosure(STANDARD_WHILE_FALSE_BODY);
    private static final ProtosClosureValue STANDARD_UNTIL_TRUE =
            ProtosClosureValue.nativeClosure(STANDARD_UNTIL_TRUE_BODY);
    private static final ProtosClosureValue STANDARD_UNTIL_FALSE =
            ProtosClosureValue.nativeClosure(STANDARD_UNTIL_FALSE_BODY);
    private static final class StandardCaseOfBody implements ProtosNativeClosureBody {
        @Override
        public Object execute(ProtosActivation activation, List<?> arguments) {
            return caseOf(activation, arguments);
        }
    }

    private static final ProtosNativeClosureBody STANDARD_CASE_OF_BODY =
            new StandardCaseOfBody();
    private static final ProtosClosureValue STANDARD_CASE_OF =
            ProtosClosureValue.nativeClosure(STANDARD_CASE_OF_BODY);

    private ProtosStandardObjectProtocol() {}

    static boolean isCanonicalStandardCallSelection(
            Object behavior,
            ProtosObjectValue home) {
        return behavior == STANDARD_CALL
                && home.isRootObject();
    }

    /**
     * I114/I115 structural selection of canonical {@code Object.call} on the
     * root Object: the root's observed {@code Shape}
     * protects the structure the property was selected in (removal,
     * recreation and any other structural change are {@code Shape}
     * transitions), and the root selector's independently invalidated Assumption protects
     * its value against rewrites, including writes that retain the same Shape. A consumer must check both; when either fails it
     * takes the generic path. No value is held here.
     */
    public record CanonicalRootCallSelection(Shape rootShape, Assumption callSelectionStable) {
        /** Whether the root still has the observed Shape and the call location was not rewritten. */
        public boolean isValid() {
            return rootShape.check(ProtosObjectValue.rootObject())
                    && callSelectionStable.isValid();
        }
    }

    /**
     * I114: whether the root Object's inherited {@code call} selection is
     * currently canonical {@code Object.call}. A receiver's local {@code call}
     * slot always takes precedence and must be checked by the caller.
     */
    public static boolean isCanonicalRootCallSelectionForRuntime() {
        return canonicalRootCallSelectionOrNullForRuntime() != null;
    }

    /**
     * The current canonical {@code Object.call} selection of the root Object,
     * or {@code null} when the root's {@code call} property is absent, is not
     * exactly {@link #STANDARD_CALL}, or its selector-stability assumption is invalid. A location rewritten once may stay non-final forever;
     * that is not retried here, and callers then use generic selection.
     * Specialization-time only.
     */
    @TruffleBoundary
    public static CanonicalRootCallSelection canonicalRootCallSelectionOrNullForRuntime() {
        ProtosObjectValue root = ProtosObjectValue.rootObject();
        synchronized (root) {
            Shape shape = root.getShape();
            Property call = shape.getProperty("call");
            if (call == null
                    || DynamicObject.GetNode.getUncached().execute(root, "call", null)
                            != STANDARD_CALL) {
                return null;
            }
            Assumption stability =
                    ProtosObjectValue.rootCallSelectionAssumptionForRuntime();
            return new CanonicalRootCallSelection(shape, stability);
        }
    }

    public static boolean isStandardCallImplementation(
            ProtosClosureValue closure) {
        return closure.nativeBody().orElse(null) == STANDARD_CALL_BODY;
    }

    /**
     * D239-C1: whether {@code closure} carries the actual standard
     * {@code Object.construct} executable body. Copied or extracted standard
     * construct behaviors keep this identity wherever they are installed, so
     * transparent P,K forwarding never depends on selector spelling or on the
     * method home.
     */
    static boolean isStandardConstructImplementation(
            ProtosClosureValue closure) {
        return closure.nativeBody().orElse(null) == STANDARD_CONSTRUCT_BODY;
    }

    static boolean isStandardConstructImplementation(
            ProtosNativeClosureBody body) {
        return body == STANDARD_CONSTRUCT_BODY;
    }

    static boolean isCanonicalStandardConstructSelection(
            Object behavior,
            ProtosObjectValue home) {
        return behavior == STANDARD_CONSTRUCT
                && home.isRootObject();
    }

    /**
     * D239-C1: the ordinary receiver domain admitted by the non-Closure
     * branch of standard {@code Object.call} and by standard
     * {@code Object.construct}.
     */
    static boolean isOrdinaryConstructionReceiver(Object receiver) {
        return receiver instanceof ProtosObjectValue && ordinaryReceiver(receiver);
    }

    /**
     * The standard loop a native body implements, or {@code null}; inside a
     * Task the loop runs through structured dispatch, whose callback
     * activations may suspend.
     */
    static StandardLoop standardLoopForImplementation(ProtosClosureValue closure) {
        ProtosNativeClosureBody body = closure.nativeBody().orElse(null);
        if (body == STANDARD_WHILE_TRUE_BODY) return StandardLoop.WHILE_TRUE;
        if (body == STANDARD_WHILE_FALSE_BODY) return StandardLoop.WHILE_FALSE;
        if (body == STANDARD_UNTIL_TRUE_BODY) return StandardLoop.UNTIL_TRUE;
        if (body == STANDARD_UNTIL_FALSE_BODY) return StandardLoop.UNTIL_FALSE;
        return null;
    }

    /** Whether {@code behavior} is the standard loop behavior of {@code selector}. */
    static boolean isStandardLoopSelection(
            String selector, Object behavior, ProtosObjectValue home) {
        if (!home.isRootObject()) {
            return false;
        }
        return switch (selector) {
            case "whileTrue" -> behavior == STANDARD_WHILE_TRUE;
            case "whileFalse" -> behavior == STANDARD_WHILE_FALSE;
            case "untilTrue" -> behavior == STANDARD_UNTIL_TRUE;
            case "untilFalse" -> behavior == STANDARD_UNTIL_FALSE;
            default -> false;
        };
    }

    static boolean isCanonicalStandardEnsureSelection(
            Object behavior,
            ProtosObjectValue home) {
        return behavior == STANDARD_ENSURE
                && home.isRootObject();
    }

    static boolean isStandardEnsureImplementation(
            ProtosClosureValue closure) {
        return closure.nativeBody().orElse(null) == STANDARD_ENSURE_BODY;
    }

    static boolean isStandardCaseOfImplementation(
            ProtosNativeClosureBody body) {
        return body instanceof StandardCaseOfBody;
    }

    static boolean isCanonicalStandardCaseOfSelection(
            Object behavior,
            ProtosObjectValue home) {
        return behavior == STANDARD_CASE_OF
                && home.isRootObject();
    }

    public static void install() {
        ProtosObjectValue object = ProtosObjectValue.rootObject();
        ProtosStandardBooleanProtocol.install();
        if (!object.hasLocalSlot("call")) {
            object.createLocalSlot(
                    "call",
                    STANDARD_CALL);
        }
        if (!object.hasLocalSlot("construct")) {
            object.createLocalSlot(
                    "construct",
                    STANDARD_CONSTRUCT);
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
        if (!object.hasLocalSlot("freeze")) {
            object.createLocalSlot(
                    "freeze",
                    ProtosClosureValue.nativeClosure(
                            ProtosStandardObjectProtocol::freeze));
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
                    STANDARD_ENSURE);
        }
        installLoop(object, "whileTrue", STANDARD_WHILE_TRUE);
        installLoop(object, "whileFalse", STANDARD_WHILE_FALSE);
        installLoop(object, "untilTrue", STANDARD_UNTIL_TRUE);
        installLoop(object, "untilFalse", STANDARD_UNTIL_FALSE);
        if (!object.hasLocalSlot("caseOf")) {
            object.createLocalSlot(
                    "caseOf",
                    STANDARD_CASE_OF);
        }
    }

    /**
     * D013 / D239-C1 standard {@code Object.call}: a Closure receiver executes
     * under the D013 rules; any other ordinary receiver forwards the exact
     * evaluated packet to an ordinary {@code construct} send on the original
     * receiver and returns that send's exact normal result. No child is
     * allocated here.
     */
    private static Object call(
            ProtosActivation activation,
            List<?> supplied) {
        Object receiver = activation.receiver();
        if (receiver instanceof ProtosClosureValue closure) {
            // The Closure branch is terminal: it executes the Closure's own
            // body and never looks up `call` again (CALLABLES.md).
            return ProtosClosureInvoker.invokeSelected(
                    closure,
                    supplied,
                    activation);
        }
        if (!isOrdinaryConstructionReceiver(receiver)) {
            throw new ProtosSignalException(
                    ProtosCoreErrors.newError(activation));
        }
        return ProtosInvocation.invokeMessage(
                receiver,
                "construct",
                supplied,
                activation);
    }

    /**
     * D239-C1 standard {@code Object.construct}: allocate a fresh child whose
     * immediate parent is the invocation receiver, send {@code init} to it
     * with the forwarded packet, ignore init's normal result and return the
     * child. Errors and control transfers from init propagate unchanged.
     */
    private static Object construct(
            ProtosActivation activation,
            List<?> supplied) {
        Object receiver = activation.receiver();
        if (!(receiver instanceof ProtosObjectValue prototype) || !ordinaryReceiver(receiver)) {
            throw new ProtosSignalException(
                    ProtosCoreErrors.newError(activation));
        }
        ProtosObjectValue instance =
                new ProtosObjectValue(prototype);
        ProtosCallRequest initializer =
                ProtosCallRequest.send(
                        instance,
                        "init",
                        activation,
                        supplied);

        Object outcome = initializer.callUncached();

        // D239-C1: init's normal value is ignored, but its
        // suspension, Error and control transfers are preserved.
        return ProtosTailCallContinuation.afterSuccessfulChild(
                outcome, instance);
    }

    /*
     * I091 / PLAT056-C: physically object-backed semantic Numbers
     * (BigInteger, Fraction and Complex) are not ordinary object
     * receivers for construction, mutation or structural reflection.
     * An ordinary object delegating to a Number remains ordinary.
     */
    private static boolean ordinaryReceiver(Object receiver) {
        return !ProtosNumericValueSupport.isCurrentNumber(receiver);
    }

    private static Object hasSlot(ProtosActivation activation, List<?> supplied) {
        if (supplied.size() != 1
                || !(supplied.get(0) instanceof ProtosStringValue name)) {
            throw invalid(activation);
        }

        Object receiver = activation.receiver();
        if (receiver instanceof ProtosObjectValue ordinary && ordinaryReceiver(receiver)) {
            return Boolean.valueOf(ordinary.hasLocalSlot(name.value()));
        }
        return Boolean.FALSE;
    }

    private static Object slotNames(ProtosActivation activation, List<?> supplied) {
        if (!supplied.isEmpty()) {
            throw invalid(activation);
        }

        ArrayList<String> names = new ArrayList<>();
        Object receiver = activation.receiver();
        if (receiver instanceof ProtosObjectValue ordinary && ordinaryReceiver(receiver)) {
            ArrayList<Object> ignoredValues = new ArrayList<>();
            ordinary.appendLocalBindingsTo(names, ignoredValues);
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
        if (!(receiver instanceof ProtosObjectValue ordinary) || !ordinaryReceiver(receiver)) {
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
        if (!(activation.receiver() instanceof ProtosObjectValue receiver)
                || !ordinaryReceiver(receiver)) {
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
        Object receiver = activation.receiver();

        if (!(receiver instanceof ProtosObjectValue ordinary)
                || !ordinaryReceiver(receiver)) {
            throw invalid(activation);
        }
        return ordinary.close();
    }

    private static Object freeze(ProtosActivation activation, List<?> supplied) {
        if (!supplied.isEmpty()) {
            throw invalid(activation);
        }
        Object receiver = activation.receiver();

        if (!(receiver instanceof ProtosObjectValue ordinary)
                || !ordinaryReceiver(receiver)) {
            throw invalid(activation);
        }
        return ordinary.freeze();
    }

    private static Object without(ProtosActivation activation, List<?> supplied) {
        if (supplied.size() != 1
                || !(supplied.get(0) instanceof ProtosStringValue name)) {
            throw invalid(activation);
        }
        if (!(activation.receiver() instanceof ProtosObjectValue receiver)
                || !ordinaryReceiver(receiver)) {
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
        if (!(activation.receiver() instanceof ProtosObjectValue receiver)
                || !ordinaryReceiver(receiver)) {
            throw invalid(activation);
        }
        if (!receiver.hasLocalSlot(sourceName.value())
                || receiver.hasLocalSlot(aliasName.value())) {
            throw invalid(activation);
        }
        return receiver.aliasLocalSlot(sourceName.value(), aliasName.value());
    }

    private static Object caseOf(
            ProtosActivation activation,
            List<?> supplied) {
        if (supplied.size() != 1
                || !(supplied.get(0) instanceof ProtosMapValue cases)) {
            throw invalid(activation);
        }

        Object subject = activation.receiver();
        List<Map.Entry<Object, Object>> observed = cases.associationSnapshot();

        for (Map.Entry<Object, Object> association : observed) {
            Object outcome =
                    ProtosInvocation.invokeMessage(
                            association.getKey(),
                            "match",
                            List.of(subject),
                            activation);

            if (outcome == Boolean.FALSE) {
                continue;
            }

            if (outcome == Boolean.TRUE) {
                return ProtosInvocation.invoke(
                        association.getValue(),
                        List.of(),
                        activation);
            }

            if (outcome instanceof ProtosArrayValue captures) {
                List<Object> snapshot = captures.indexedSnapshot();
                if (!snapshot.isEmpty()) {
                    return ProtosInvocation.invoke(
                            association.getValue(),
                            snapshot,
                            activation);
                }
            }

            throw invalid(activation);
        }

        throw invalid(activation);
    }

    private static Object ensure(ProtosActivation activation, List<?> supplied) {
        Object receiver = activation.receiver();
        if (!(receiver instanceof ProtosClosureValue body)) {
            throw invalid(activation);
        }
        if (supplied.size() != 1
                || !(supplied.get(0) instanceof ProtosClosureValue cleanup)) {
            throw invalid(activation);
        }

        ProtosDynamicControlState state = activation.dynamicControlState();
        ProtosDynamicControlState.Frame frame =
                state.enterFrame(activation, ProtosDynamicControlState.FrameKind.ENSURE);

        try {
            Object result = ProtosClosureInvoker.invoke(body, List.of(), activation);
            state.beginEnsureCleanup(
                    frame,
                    ProtosDynamicControlState.EnsureExitKind.NORMAL,
                    result);
        } catch (ProtosSignalException pending) {
            state.beginEnsureCleanup(
                    frame,
                    ProtosDynamicControlState.EnsureExitKind.ERROR,
                    pending);
        } catch (ProtosNonLocalReturnException pending) {
            state.beginEnsureCleanup(
                    frame,
                    ProtosDynamicControlState.EnsureExitKind.RETURN,
                    pending);
        } catch (ProtosTaskCancellationException pending) {
            state.beginEnsureCleanup(
                    frame,
                    ProtosDynamicControlState.EnsureExitKind.CANCELLATION,
                    pending);
        } catch (RuntimeException hostFailure) {
            state.leaveFrame(frame);
            throw hostFailure;
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

    private static void installLoop(
            ProtosObjectValue object, String selector, ProtosClosureValue behavior) {
        if (!object.hasLocalSlot(selector)) {
            object.createLocalSlot(selector, behavior);
        }
    }

    /*
     * The receiver is the condition of a pre-test loop and the body of a
     * post-test loop. After validating, in order, a semantic Closure
     * receiver, exactly one argument and a semantic Closure argument, and
     * before activating either, the loop activates each Closure with no
     * arguments, ends normally when a condition result is exactly
     * {@code exitValue}, continues on the other canonical Boolean, signals a
     * fresh Error for any other result, and answers the last normal body
     * result or canonical null.
     */
    private static Object loop(
            StandardLoop kind,
            ProtosActivation activation,
            List<?> supplied) {
        boolean postTest = kind.postTest();
        Boolean exitValue = kind.exitValue();
        if (!(activation.receiver() instanceof ProtosClosureValue receiver)) {
            throw invalid(activation);
        }
        if (supplied.size() != 1
                || !(supplied.get(0) instanceof ProtosClosureValue argument)) {
            throw invalid(activation);
        }
        ProtosClosureValue condition = postTest ? argument : receiver;
        ProtosClosureValue body = postTest ? receiver : argument;
        Object lastBodyResult = ProtosNullValue.INSTANCE;
        if (postTest) {
            lastBodyResult = ProtosClosureInvoker.invoke(body, List.of(), activation);
        }
        while (true) {
            Object conditionResult = ProtosClosureInvoker.invoke(condition, List.of(), activation);
            if (conditionResult == exitValue) {
                return lastBodyResult;
            }
            if (conditionResult != Boolean.TRUE
                    && conditionResult != Boolean.FALSE) {
                throw invalid(activation);
            }
            lastBodyResult = ProtosClosureInvoker.invoke(body, List.of(), activation);
        }
    }

    private static ProtosSignalException invalid(ProtosActivation activation) {
        return ProtosCoreErrors.signal(activation, ProtosCoreErrors.newError(activation));
    }

}
