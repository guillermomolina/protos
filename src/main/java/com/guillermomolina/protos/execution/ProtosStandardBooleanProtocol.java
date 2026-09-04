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

import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosCoreErrors;
import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import java.util.List;

public final class ProtosStandardBooleanProtocol {
    private ProtosStandardBooleanProtocol() {}

    public static void install() {
        ProtosObjectValue object = ProtosObjectValue.rootObject();

        install(object, "ifTrue", (receiver, callback, activation) -> {
            if (receiver == ProtosBooleanValue.FALSE) {
                return ProtosNullValue.INSTANCE;
            }
            return ProtosInvocation.invoke(callback, List.of(), activation);
        });

        install(object, "ifFalse", (receiver, callback, activation) -> {
            if (receiver == ProtosBooleanValue.TRUE) {
                return ProtosNullValue.INSTANCE;
            }
            return ProtosInvocation.invoke(callback, List.of(), activation);
        });

        install(object, "and", (receiver, callback, activation) -> {
            if (receiver == ProtosBooleanValue.FALSE) {
                return ProtosBooleanValue.FALSE;
            }
            return requireBooleanResult(
                    ProtosInvocation.invoke(callback, List.of(), activation),
                    activation);
        });

        install(object, "or", (receiver, callback, activation) -> {
            if (receiver == ProtosBooleanValue.TRUE) {
                return ProtosBooleanValue.TRUE;
            }
            return requireBooleanResult(
                    ProtosInvocation.invoke(callback, List.of(), activation),
                    activation);
        });
    }

    private static void install(
            ProtosObjectValue object,
            String selector,
            BooleanOperation operation) {
        if (object.hasLocalSlot(selector)) {
            return;
        }
        object.createLocalSlot(
                selector,
                ProtosClosureValue.nativeClosure(
                        (activation, supplied) -> {
                            if (supplied.size() != 1) {
                                throw new ProtosSignalException(
                                        ProtosCoreErrors.newError(activation));
                            }
                            Object receiver = activation.receiver();
                            if (receiver != ProtosBooleanValue.TRUE
                                    && receiver != ProtosBooleanValue.FALSE) {
                                throw new ProtosSignalException(
                                        ProtosCoreErrors.newError(activation));
                            }
                            return operation.apply(receiver, supplied.get(0), activation);
                        }));
    }

    private static Object requireBooleanResult(
            Object result,
            com.guillermomolina.protos.runtime.ProtosActivation activation) {
        if (result != ProtosBooleanValue.TRUE
                && result != ProtosBooleanValue.FALSE) {
            throw new ProtosSignalException(ProtosCoreErrors.newError(activation));
        }
        return result;
    }

    @FunctionalInterface
    private interface BooleanOperation {
        Object apply(
                Object receiver,
                Object callback,
                com.guillermomolina.protos.runtime.ProtosActivation activation);
    }
}
