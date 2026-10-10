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

/** Standard exact cross-family Number ordering. */
public final class ProtosStandardNumberOrderingProtocol {
    private ProtosStandardNumberOrderingProtocol() {}

    public static void install(ProtosObjectValue numberPrototype) {
        Objects.requireNonNull(numberPrototype, "numberPrototype");
        install(numberPrototype, "<", Relation.LESS);
        install(numberPrototype, "<=", Relation.LESS_EQUAL);
        install(numberPrototype, ">", Relation.GREATER);
        install(numberPrototype, ">=", Relation.GREATER_EQUAL);
    }

    private static void install(
            ProtosObjectValue numberPrototype,
            String selector,
            Relation relation) {
        if (numberPrototype.hasLocalSlot(selector)) {
            throw new IllegalStateException(
                    "Core Number already defines a local " + selector + " slot");
        }
        numberPrototype.createLocalSlot(
                selector,
                ProtosClosureValue.nativeClosure(
                        (activation, supplied) -> {
                            if (supplied.size() != 1
                                    || !ProtosNumericValueSupport.isCurrentNumber(activation.receiver())
                                    || !ProtosNumericValueSupport.isCurrentNumber(supplied.get(0))) {
                                throw new ProtosSignalException(
                                        ProtosCoreErrors.newError(activation));
                            }
                            Comparison comparison =
                                    compare(activation.receiver(), supplied.get(0));
                            if (comparison == Comparison.UNORDERED) {
                                return ProtosBooleanValue.FALSE;
                            }
                            boolean result = switch (relation) {
                                case LESS -> comparison == Comparison.LESS;
                                case LESS_EQUAL -> comparison != Comparison.GREATER;
                                case GREATER -> comparison == Comparison.GREATER;
                                case GREATER_EQUAL -> comparison != Comparison.LESS;
                            };
                            return result ? ProtosBooleanValue.TRUE : ProtosBooleanValue.FALSE;
                        }));
    }

    static Comparison compare(Object left, Object right) {
        return ProtosCurrentNumericRelations.compare(left, right);
    }

    enum Comparison { LESS, EQUAL, GREATER, UNORDERED }
    private enum Relation { LESS, LESS_EQUAL, GREATER, GREATER_EQUAL }
}
