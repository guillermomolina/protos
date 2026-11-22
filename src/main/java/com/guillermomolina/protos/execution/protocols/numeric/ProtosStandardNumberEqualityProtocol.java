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
import com.guillermomolina.protos.runtime.ProtosNumericValueSupport;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.guillermomolina.protos.runtime.callables.ProtosNativeClosureBody;
import java.util.Objects;

public final class ProtosStandardNumberEqualityProtocol {
    private static final ProtosNativeClosureBody STANDARD_EQUALITY_BODY =
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
                    return Boolean.FALSE;
                }

                return numericEquals(receiver, argument)
                        ? Boolean.TRUE
                        : Boolean.FALSE;
            };

    private ProtosStandardNumberEqualityProtocol() {}

    public static void install(ProtosObjectValue numberPrototype) {
        Objects.requireNonNull(numberPrototype, "numberPrototype");

        if (numberPrototype.hasLocalSlot("==")) {
            throw new IllegalStateException("Core Number already defines a local == slot");
        }

        numberPrototype.createLocalSlot(
                "==",
                ProtosClosureValue.nativeClosure(STANDARD_EQUALITY_BODY));
    }

    /**
     * Whether the selected Number equality carries the installed standard body
     * at the admitted Prelude's Number prototype. Observes identity only;
     * callers must separately protect the lookup chain against mutation.
     */
    static boolean isCanonicalStandardEqualitySelection(
            Object behavior, ProtosObjectValue home, ProtosPrelude prelude) {
        return prelude != null
                && home == prelude.numberPrototype()
                && behavior instanceof ProtosClosureValue closure
                && closure.nativeBodyOrNullForRuntime() == STANDARD_EQUALITY_BODY;
    }

    static boolean numericEquals(Object left, Object right) {
        return ProtosCurrentNumericRelations.numericEquals(left, right);
    }
}
