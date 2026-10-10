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
import com.guillermomolina.protos.runtime.ProtosNumericValueSupport;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import java.util.Objects;

public final class ProtosStandardNumberEqualityProtocol {
    private ProtosStandardNumberEqualityProtocol() {}

    public static void install(ProtosObjectValue numberPrototype) {
        Objects.requireNonNull(numberPrototype, "numberPrototype");

        if (numberPrototype.hasLocalSlot("==")) {
            throw new IllegalStateException("Core Number already defines a local == slot");
        }

        numberPrototype.createLocalSlot(
                "==",
                ProtosClosureValue.nativeClosure(
                        (activation, supplied) -> {
                            if (supplied.size() != 1) {
                                throw new ProtosSignalException(
                                        ProtosCoreErrors.newError(activation));
                            }

                            Object receiver = activation.receiver();
                            if (!ProtosNumericValueSupport.isCurrentNumber(receiver)) {
                                throw new ProtosSignalException(
                                        ProtosCoreErrors.newError(activation));
                            }

                            Object argument = supplied.get(0);
                            if (!ProtosNumericValueSupport.isCurrentNumber(argument)) {
                                return ProtosBooleanValue.FALSE;
                            }

                            return numericEquals(receiver, argument)
                                    ? ProtosBooleanValue.TRUE
                                    : ProtosBooleanValue.FALSE;
                        }));
    }

    static boolean numericEquals(Object left, Object right) {
        return ProtosCurrentNumericRelations.numericEquals(left, right);
    }
}
