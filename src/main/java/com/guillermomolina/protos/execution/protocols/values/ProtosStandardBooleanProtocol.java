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
import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.guillermomolina.protos.runtime.callables.ProtosNativeClosureBody;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import java.util.List;

public final class ProtosStandardBooleanProtocol {
    enum StructuredCallbackKind {
        AND(1),
        OR(1),
        IF_TRUE(1),
        IF_FALSE(1),
        IF_TRUE_IF_FALSE(2);

        private final int arity;

        StructuredCallbackKind(int arity) {
            this.arity = arity;
        }

        int arity() {
            return arity;
        }
    }

    private static final ProtosNativeClosureBody STANDARD_NOT_BODY =
            ProtosStandardBooleanProtocol::not;
    private static final ProtosNativeClosureBody STANDARD_AND_BODY =
            ProtosStandardBooleanProtocol::and;
    private static final ProtosNativeClosureBody STANDARD_OR_BODY =
            ProtosStandardBooleanProtocol::or;

    private static final ProtosClosureValue STANDARD_NOT =
            standardNative(STANDARD_NOT_BODY);
    private static final ProtosClosureValue STANDARD_AND =
            standardNative(STANDARD_AND_BODY);
    private static final ProtosClosureValue STANDARD_OR =
            standardNative(STANDARD_OR_BODY);

    /*
     * The standard conditional behavior reached by ordinary dispatch: a
     * spread or named argument list, an extracted method, a reflective send,
     * or a receiver that only delegates to a Boolean. A lowered conditional
     * site whose receiver is canonical true or false selects internally and
     * never sends these selectors.
     */
    private static final ProtosNativeClosureBody STANDARD_IF_TRUE_BODY =
            ProtosStandardBooleanProtocol::ifTrue;
    private static final ProtosNativeClosureBody STANDARD_IF_FALSE_BODY =
            ProtosStandardBooleanProtocol::ifFalse;
    private static final ProtosNativeClosureBody STANDARD_IF_TRUE_IF_FALSE_BODY =
            ProtosStandardBooleanProtocol::ifTrueIfFalse;
    private static final ProtosClosureValue STANDARD_IF_TRUE =
            standardNative(STANDARD_IF_TRUE_BODY);
    private static final ProtosClosureValue STANDARD_IF_FALSE =
            standardNative(STANDARD_IF_FALSE_BODY);
    private static final ProtosClosureValue STANDARD_IF_TRUE_IF_FALSE =
            standardNative(STANDARD_IF_TRUE_IF_FALSE_BODY);

    private ProtosStandardBooleanProtocol() {}

    /*
     * Keep the audited Core native-provider boundary at one lexical provider. Every
     * standard Boolean selector keeps a stable Closure/body identity, so the bootstrap
     * can verify that the frozen root publishes exactly this behavior.
     */
    private static ProtosClosureValue standardNative(ProtosNativeClosureBody body) {
        return ProtosClosureValue.nativeClosure(body);
    }

    static StructuredCallbackKind structuredCallbackKindForImplementation(
            ProtosClosureValue closure) {
        ProtosNativeClosureBody body = closure.nativeBody().orElse(null);
        if (body == STANDARD_AND_BODY) return StructuredCallbackKind.AND;
        if (body == STANDARD_OR_BODY) return StructuredCallbackKind.OR;
        // Ordinary selected conditionals may invoke suspendible callbacks too.
        // Canonical lowered sites still use their internal conditional operations.
        if (body == STANDARD_IF_TRUE_BODY) return StructuredCallbackKind.IF_TRUE;
        if (body == STANDARD_IF_FALSE_BODY) return StructuredCallbackKind.IF_FALSE;
        if (body == STANDARD_IF_TRUE_IF_FALSE_BODY) {
            return StructuredCallbackKind.IF_TRUE_IF_FALSE;
        }
        return null;
    }

    static StructuredCallbackKind structuredCallbackKindForCanonicalSelection(
            Object behavior,
            ProtosObjectValue home) {
        if (!home.isRootObject()) return null;
        if (behavior == STANDARD_AND) return StructuredCallbackKind.AND;
        if (behavior == STANDARD_OR) return StructuredCallbackKind.OR;
        return null;
    }

    /** I095-D: not has no callback, so it is not a StructuredCallbackKind. */
    static boolean isStandardNotSelection(Object behavior, ProtosObjectValue home) {
        return home.isRootObject() && behavior == STANDARD_NOT;
    }

    /** Whether {@code behavior} is the standard conditional behavior of {@code selector}. */
    static boolean isStandardConditionalSelection(
            String selector, Object behavior, ProtosObjectValue home) {
        if (!home.isRootObject()) return false;
        return switch (selector) {
            case "ifTrue" -> behavior == STANDARD_IF_TRUE;
            case "ifFalse" -> behavior == STANDARD_IF_FALSE;
            case "ifTrueIfFalse" -> behavior == STANDARD_IF_TRUE_IF_FALSE;
            default -> false;
        };
    }

    public static void install() {
        ProtosObjectValue object = ProtosObjectValue.rootObject();
        install(object, "not", STANDARD_NOT);
        install(object, "and", STANDARD_AND);
        install(object, "or", STANDARD_OR);
        install(object, "ifTrue", STANDARD_IF_TRUE);
        install(object, "ifFalse", STANDARD_IF_FALSE);
        install(object, "ifTrueIfFalse", STANDARD_IF_TRUE_IF_FALSE);
    }

    private static void install(
            ProtosObjectValue object,
            String selector,
            ProtosClosureValue implementation) {
        if (!object.hasLocalSlot(selector)) {
            object.createLocalSlot(selector, implementation);
        }
    }

    private static Object not(ProtosActivation activation, List<?> supplied) {
        Object receiver = requireReceiverAndArity(activation, supplied, 0);
        return receiver == Boolean.TRUE
                ? Boolean.FALSE
                : Boolean.TRUE;
    }

    /*
     * VALUES_AND_COLLECTIONS: the selected callback is invoked exactly once
     * with no arguments and its normal result is returned unchanged; the
     * unselected callback is neither validated nor invoked.
     */
    private static Object ifTrue(ProtosActivation activation, List<?> supplied) {
        Object receiver = requireReceiverAndArity(activation, supplied, 1);
        if (receiver == Boolean.FALSE) return ProtosNullValue.INSTANCE;
        return ProtosInvocation.invoke(supplied.get(0), List.of(), activation);
    }

    private static Object ifFalse(ProtosActivation activation, List<?> supplied) {
        Object receiver = requireReceiverAndArity(activation, supplied, 1);
        if (receiver == Boolean.TRUE) return ProtosNullValue.INSTANCE;
        return ProtosInvocation.invoke(supplied.get(0), List.of(), activation);
    }

    private static Object ifTrueIfFalse(ProtosActivation activation, List<?> supplied) {
        Object receiver = requireReceiverAndArity(activation, supplied, 2);
        Object selected =
                receiver == Boolean.TRUE ? supplied.get(0) : supplied.get(1);
        return ProtosInvocation.invoke(selected, List.of(), activation);
    }

    private static Object and(ProtosActivation activation, List<?> supplied) {
        Object receiver = requireReceiverAndArity(activation, supplied, 1);
        if (receiver == Boolean.FALSE) return Boolean.FALSE;
        return requireBooleanResult(
                ProtosInvocation.invoke(supplied.get(0), List.of(), activation),
                activation);
    }

    private static Object or(ProtosActivation activation, List<?> supplied) {
        Object receiver = requireReceiverAndArity(activation, supplied, 1);
        if (receiver == Boolean.TRUE) return Boolean.TRUE;
        return requireBooleanResult(
                ProtosInvocation.invoke(supplied.get(0), List.of(), activation),
                activation);
    }

    private static Object requireReceiverAndArity(
            ProtosActivation activation,
            List<?> supplied,
            int arity) {
        if (supplied.size() != arity) throw invalid(activation);
        Object receiver = activation.receiver();
        if (receiver != Boolean.TRUE && receiver != Boolean.FALSE) {
            throw invalid(activation);
        }
        return receiver;
    }

    static Object requireBooleanResult(Object result, ProtosActivation activation) {
        if (result != Boolean.TRUE && result != Boolean.FALSE) {
            throw invalid(activation);
        }
        return result;
    }

    private static ProtosSignalException invalid(ProtosActivation activation) {
        return new ProtosSignalException(ProtosCoreErrors.newError(activation));
    }
}