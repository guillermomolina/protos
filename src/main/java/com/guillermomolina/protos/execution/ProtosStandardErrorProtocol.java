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
import com.guillermomolina.protos.runtime.ProtosNativeClosureBody;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import java.util.List;

final class ProtosStandardErrorProtocol {
    private static final ProtosNativeClosureBody STANDARD_SIGNAL_BODY =
            ProtosStandardErrorProtocol::signal;
    private static final ProtosNativeClosureBody STANDARD_HANDLE_BODY =
            ProtosStandardErrorProtocol::handle;

    private ProtosStandardErrorProtocol() {}

    static boolean isStandardSignalImplementation(ProtosClosureValue closure) {
        return closure.nativeBody().orElse(null) == STANDARD_SIGNAL_BODY;
    }

    static boolean isStandardHandleImplementation(ProtosClosureValue closure) {
        return closure.nativeBody().orElse(null) == STANDARD_HANDLE_BODY;
    }

    static boolean isCanonicalStandardSignalSelection(
            ProtosClosureValue closure,
            ProtosObjectValue home,
            ProtosActivation caller) {
        return isStandardSignalImplementation(closure)
                && caller.prelude().map(prelude -> prelude.errorPrototype()).orElse(null) == home;
    }

    static boolean isCanonicalStandardHandleSelection(
            ProtosClosureValue closure,
            ProtosObjectValue home,
            ProtosActivation caller) {
        return isStandardHandleImplementation(closure)
                && caller.prelude().map(prelude -> prelude.errorPrototype()).orElse(null) == home;
    }

    static void install(ProtosObjectValue errorPrototype) {
        if (errorPrototype.hasLocalSlot("signal") || errorPrototype.hasLocalSlot("handle")) {
            throw new IllegalStateException("Core Error protocol slots already installed");
        }
        errorPrototype.createLocalSlot(
                "signal",
                ProtosClosureValue.nativeClosure(STANDARD_SIGNAL_BODY));
        errorPrototype.createLocalSlot(
                "handle",
                ProtosClosureValue.nativeClosure(STANDARD_HANDLE_BODY));
    }

    private static Object signal(ProtosActivation activation, List<?> supplied) {
        if (!supplied.isEmpty()) {
            throw invalid(activation);
        }
        Object receiver = activation.receiver();
        if (!(receiver instanceof ProtosObjectValue error)
                || !ProtosCoreErrors.isError(activation, error)) {
            throw invalid(activation);
        }
        throw ProtosCoreErrors.signal(activation, error);
    }

    private static Object handle(ProtosActivation activation, List<?> supplied) {
        Object receiver = activation.receiver();
        if (!(receiver instanceof ProtosObjectValue matchPrototype)
                || !ProtosCoreErrors.isError(activation, matchPrototype)) {
            throw invalid(activation);
        }
        if (supplied.size() != 2) {
            throw invalid(activation);
        }

        Object bodyValue = supplied.get(0);
        if (!(bodyValue instanceof ProtosClosureValue body)) {
            throw invalid(activation);
        }
        Object handlerValue = supplied.get(1);
        if (!(handlerValue instanceof ProtosClosureValue handler)) {
            throw invalid(activation);
        }

        ProtosDynamicControlState state = activation.dynamicControlState();
        ProtosDynamicControlState.Frame frame =
                state.enterHandlerFrame(activation, matchPrototype);

        try {
            Object result = ProtosClosureInvoker.invoke(body, List.of(), activation);
            state.leaveFrame(frame);
            return result;
        } catch (ProtosEvaluatorSuspension suspension) {
            throw suspension;
        } catch (ProtosSignalException transfer) {
            if (transfer.selectedHandlerFrame().orElse(null) == frame) {
                state.leaveFrame(frame);
                return ProtosClosureInvoker.invoke(
                        handler, List.of(transfer.error()), activation);
            }
            state.leaveFrame(frame);
            throw transfer;
        } catch (RuntimeException transfer) {
            state.leaveFrame(frame);
            throw transfer;
        }
    }

    private static ProtosSignalException invalid(ProtosActivation activation) {
        return ProtosCoreErrors.signal(activation, ProtosCoreErrors.newError(activation));
    }
}
