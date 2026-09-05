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

import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosCoreErrors;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosSignalException;

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
        if (!object.hasLocalSlot("==")) {
            object.createLocalSlot(
                    "==",
                    ProtosClosureValue.nativeClosure(
                            (activation, supplied) -> {
                                if (supplied.size() != 1) {
                                    throw new ProtosSignalException(
                                            ProtosCoreErrors.newError(activation));
                                }
                                return com.guillermomolina.protos.runtime.ProtosIdentity.identical(
                                                activation.receiver(), supplied.get(0))
                                        ? com.guillermomolina.protos.runtime.ProtosBooleanValue.TRUE
                                        : com.guillermomolina.protos.runtime.ProtosBooleanValue.FALSE;
                            }));
        }
        if (!object.hasLocalSlot("identityHash")) {
            object.createLocalSlot("identityHash", ProtosClosureValue.nativeClosure((activation, supplied) -> {
                if (!supplied.isEmpty()) throw new ProtosSignalException(ProtosCoreErrors.newError(activation));
                return new com.guillermomolina.protos.runtime.ProtosIntegerValue(com.guillermomolina.protos.runtime.ProtosIdentity.identityHash(activation.receiver()));
            }));
        }
    }
}
