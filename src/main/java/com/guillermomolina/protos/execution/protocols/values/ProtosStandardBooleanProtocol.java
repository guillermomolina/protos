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
import com.guillermomolina.protos.runtime.callables.ProtosNativeClosureBody;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import java.util.List;

public final class ProtosStandardBooleanProtocol {
    enum StructuredCallbackKind {
        AND(1),
        OR(1);

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

    private ProtosStandardBooleanProtocol() {}

    /*
     * Keep the audited Core native-provider boundary at one lexical provider. The
     * remaining native standard Boolean selectors (not, and, or) keep stable Closure/body
     * identities so C-prime recognizes the structured callback operations without
     * expanding that boundary. ifTrue, ifFalse and ifTrueIfFalse are I115 Bytecode DSL
     * control flow, not native methods.
     */
    private static ProtosClosureValue standardNative(ProtosNativeClosureBody body) {
        return ProtosClosureValue.nativeClosure(body);
    }

    static StructuredCallbackKind structuredCallbackKindForImplementation(
            ProtosClosureValue closure) {
        ProtosNativeClosureBody body = closure.nativeBody().orElse(null);
        if (body == STANDARD_AND_BODY) return StructuredCallbackKind.AND;
        if (body == STANDARD_OR_BODY) return StructuredCallbackKind.OR;
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

    public static void install() {
        ProtosObjectValue object = ProtosObjectValue.rootObject();
        install(object, "not", STANDARD_NOT);
        install(object, "and", STANDARD_AND);
        install(object, "or", STANDARD_OR);
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
        return receiver == ProtosBooleanValue.TRUE
                ? ProtosBooleanValue.FALSE
                : ProtosBooleanValue.TRUE;
    }

    private static Object and(ProtosActivation activation, List<?> supplied) {
        Object receiver = requireReceiverAndArity(activation, supplied, 1);
        if (receiver == ProtosBooleanValue.FALSE) return ProtosBooleanValue.FALSE;
        return requireBooleanResult(
                ProtosInvocation.invoke(supplied.get(0), List.of(), activation),
                activation);
    }

    private static Object or(ProtosActivation activation, List<?> supplied) {
        Object receiver = requireReceiverAndArity(activation, supplied, 1);
        if (receiver == ProtosBooleanValue.TRUE) return ProtosBooleanValue.TRUE;
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
        if (receiver != ProtosBooleanValue.TRUE && receiver != ProtosBooleanValue.FALSE) {
            throw invalid(activation);
        }
        return receiver;
    }

    static Object requireBooleanResult(Object result, ProtosActivation activation) {
        if (result != ProtosBooleanValue.TRUE && result != ProtosBooleanValue.FALSE) {
            throw invalid(activation);
        }
        return result;
    }

    private static ProtosSignalException invalid(ProtosActivation activation) {
        return new ProtosSignalException(ProtosCoreErrors.newError(activation));
    }
}