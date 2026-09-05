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
import com.guillermomolina.protos.runtime.ProtosFloatValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import java.util.Objects;
import java.util.function.DoubleBinaryOperator;

public final class ProtosStandardFloatProtocol {
    private ProtosStandardFloatProtocol() {}

    public static void install(ProtosObjectValue floatPrototype) {
        Objects.requireNonNull(floatPrototype, "floatPrototype");
        requireSourceBackedClosure(floatPrototype, "negated");

        installBinary(floatPrototype, "+", (left, right) -> left + right);
        installBinary(floatPrototype, "-", (left, right) -> left - right);
        installBinary(floatPrototype, "*", (left, right) -> left * right);
        installBinary(floatPrototype, "/", (left, right) -> left / right);
    }

    private static void installBinary(
            ProtosObjectValue floatPrototype,
            String selector,
            DoubleBinaryOperator operation) {
        if (floatPrototype.hasLocalSlot(selector)) {
            throw new IllegalStateException(
                    "Core Float already defines a local " + selector + " slot");
        }
        floatPrototype.createLocalSlot(
                selector,
                ProtosClosureValue.nativeClosure(
                        (activation, supplied) -> {
                            ProtosFloatValue receiver = requireFloatReceiver(activation);
                            if (supplied.size() != 1
                                    || !(supplied.get(0) instanceof ProtosFloatValue argument)) {
                                throw new ProtosSignalException(
                                        ProtosCoreErrors.newError(activation));
                            }
                            return new ProtosFloatValue(
                                    operation.applyAsDouble(receiver.value(), argument.value()));
                        }));
    }

    private static void requireSourceBackedClosure(
            ProtosObjectValue prototype, String selector) {
        Object value =
                prototype
                        .readLocalSlot(selector)
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "Core Float source did not define " + selector));
        if (!(value instanceof ProtosClosureValue closure)
                || closure.definition() == null
                || closure.executionPlan().isEmpty()
                || closure.nativeBody().isPresent()) {
            throw new IllegalStateException(
                    "Core Float." + selector
                            + " must be installed from distributable Core source");
        }
    }

    private static ProtosFloatValue requireFloatReceiver(
            com.guillermomolina.protos.runtime.ProtosActivation activation) {
        if (!(activation.receiver() instanceof ProtosFloatValue floating)) {
            throw new ProtosSignalException(ProtosCoreErrors.newError(activation));
        }
        return floating;
    }
}
