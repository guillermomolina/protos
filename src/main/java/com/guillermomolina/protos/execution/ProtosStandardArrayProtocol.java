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

import com.guillermomolina.protos.runtime.ProtosArrayValue;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosCoreErrors;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import java.util.Objects;

public final class ProtosStandardArrayProtocol {
    private ProtosStandardArrayProtocol() {}

    public static void install(ProtosObjectValue arrayPrototype) {
        Objects.requireNonNull(arrayPrototype, "arrayPrototype");

        if (arrayPrototype.hasLocalSlot("call")) {
            throw new IllegalStateException("Core Array already defines a local call slot");
        }

        arrayPrototype.createLocalSlot(
                "call",
                ProtosClosureValue.nativeClosure(
                        (activation, supplied) -> {
                            Object receiver = activation.receiver();
                            if (!(receiver instanceof ProtosObjectValue prototype)
                                    || !delegatesTo(prototype, arrayPrototype)) {
                                throw new ProtosSignalException(
                                        ProtosCoreErrors.newError(activation));
                            }
                            return new ProtosArrayValue(prototype, supplied);
                        }));

        arrayPrototype.createLocalSlot(
                "at",
                ProtosClosureValue.nativeClosure(
                        (activation, supplied) -> {
                            ProtosArrayValue array =
                                    requireArrayReceiver(activation);
                            if (supplied.size() != 1
                                    || !(supplied.get(0)
                                            instanceof com.guillermomolina.protos.runtime.ProtosIntegerValue index)) {
                                throw new ProtosSignalException(
                                        ProtosCoreErrors.newError(activation));
                            }
                            java.math.BigInteger value = index.value();
                            if (value.signum() < 0
                                    || value.compareTo(array.indexedSize()) >= 0) {
                                throw new ProtosSignalException(
                                        ProtosCoreErrors.newError(activation));
                            }
                            return array.indexedAt(value);
                        }));

        arrayPrototype.createLocalSlot(
                "atPut",
                ProtosClosureValue.nativeClosure(
                        (activation, supplied) -> {
                            ProtosArrayValue array =
                                    requireArrayReceiver(activation);
                            if (array.isFrozen()) {
                                throw new ProtosSignalException(
                                        ProtosCoreErrors.newError(activation));
                            }
                            if (supplied.size() != 2
                                    || !(supplied.get(0)
                                            instanceof com.guillermomolina.protos.runtime.ProtosIntegerValue index)) {
                                throw new ProtosSignalException(
                                        ProtosCoreErrors.newError(activation));
                            }
                            java.math.BigInteger value = index.value();
                            if (value.signum() < 0
                                    || value.compareTo(array.indexedSize()) >= 0) {
                                throw new ProtosSignalException(
                                        ProtosCoreErrors.newError(activation));
                            }
                            return array.indexedPut(value, supplied.get(1));
                        }));
    }

    private static ProtosArrayValue requireArrayReceiver(
            com.guillermomolina.protos.runtime.ProtosActivation activation) {
        if (!(activation.receiver() instanceof ProtosArrayValue array)) {
            throw new ProtosSignalException(ProtosCoreErrors.newError(activation));
        }
        return array;
    }

    private static boolean delegatesTo(
            ProtosObjectValue receiver,
            ProtosObjectValue expectedAncestor) {
        Object current = receiver;
        while (current instanceof ProtosObjectValue ordinary) {
            if (ordinary == expectedAncestor) {
                return true;
            }
            current = ordinary.parent().orElse(null);
        }
        return false;
    }
}
