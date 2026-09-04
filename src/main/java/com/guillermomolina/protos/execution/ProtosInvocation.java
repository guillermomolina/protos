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
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.guillermomolina.protos.runtime.ProtosSlotLookupResult;
import java.util.List;
import java.util.Objects;

public final class ProtosInvocation {
    private ProtosInvocation() {}

    public static Object invoke(Object receiver, List<?> supplied, ProtosActivation caller) {
        Objects.requireNonNull(receiver, "receiver");
        Objects.requireNonNull(supplied, "supplied");
        Objects.requireNonNull(caller, "caller");
        if (!(receiver instanceof ProtosObjectValue objectReceiver)) {
            throw new UnsupportedOperationException(
                    "Polymorphic invocation through non-ordinary value prototypes requires standard prototype bootstrap");
        }
        return invokeSelected(
                receiver,
                objectReceiver.lookupSlot("call").orElseThrow(
                        () -> new ProtosSignalException(ProtosCoreErrors.newError(caller))),
                supplied,
                caller);
    }

    public static Object invokeMessage(
            ProtosObjectValue receiver, String selector, List<?> supplied, ProtosActivation caller) {
        Objects.requireNonNull(receiver, "receiver");
        Objects.requireNonNull(selector, "selector");
        Objects.requireNonNull(supplied, "supplied");
        Objects.requireNonNull(caller, "caller");
        return invokeSelected(
                receiver,
                receiver.lookupSlot(selector).orElseThrow(
                        () -> new ProtosSignalException(ProtosCoreErrors.newError(caller))),
                supplied,
                caller);
    }

    private static Object invokeSelected(
            Object receiver, ProtosSlotLookupResult selected, List<?> supplied, ProtosActivation caller) {
        if (!(selected.value() instanceof ProtosClosureValue closure)) {
            throw new ProtosSignalException(ProtosCoreErrors.newError(caller));
        }
        return ProtosClosureInvoker.invoke(closure.bindMethod(receiver, selected.home()), supplied, caller);
    }
}
