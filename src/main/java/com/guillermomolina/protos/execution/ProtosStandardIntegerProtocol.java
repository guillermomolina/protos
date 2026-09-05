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
import com.guillermomolina.protos.runtime.ProtosIntegerValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import java.util.Objects;
import java.util.function.BiFunction;

public final class ProtosStandardIntegerProtocol {
    private ProtosStandardIntegerProtocol() {}

    public static void install(ProtosObjectValue integerPrototype) {
        Objects.requireNonNull(integerPrototype, "integerPrototype");
        requireSourceBackedClosure(integerPrototype, "negated");

        installBinary(integerPrototype, "+", java.math.BigInteger::add);
        installBinary(integerPrototype, "-", java.math.BigInteger::subtract);
        installBinary(integerPrototype, "*", java.math.BigInteger::multiply);
        installDivision(integerPrototype);
        installQuotientRemainder(integerPrototype);
    }

    private static void installDivision(ProtosObjectValue integerPrototype) {
        if (integerPrototype.hasLocalSlot("/")) {
            throw new IllegalStateException("Core Integer already defines a local / slot");
        }
        integerPrototype.createLocalSlot(
                "/",
                ProtosClosureValue.nativeClosure(
                        (activation, supplied) -> {
                            ProtosIntegerValue receiver = requireIntegerReceiver(activation);
                            if (supplied.size() != 1
                                    || !(supplied.get(0) instanceof ProtosIntegerValue argument)
                                    || argument.value().signum() == 0) {
                                throw new ProtosSignalException(
                                        ProtosCoreErrors.newError(activation));
                            }
                            return new ProtosFloatValue(
                                    ProtosBinary64Rounding.divideExactIntegers(
                                            receiver.value(), argument.value()));
                        }));
    }

    private static void installQuotientRemainder(ProtosObjectValue integerPrototype) {
        installExactIntegerBinary(
                integerPrototype,
                "div",
                (left, right) -> left.divide(right));
        installExactIntegerBinary(
                integerPrototype,
                "mod",
                (left, right) -> left.remainder(right));
        installExactIntegerBinary(
                integerPrototype,
                "%",
                (left, right) -> left.remainder(right));
    }

    private static void installExactIntegerBinary(
            ProtosObjectValue integerPrototype,
            String selector,
            BiFunction<java.math.BigInteger, java.math.BigInteger, java.math.BigInteger> operation) {
        if (integerPrototype.hasLocalSlot(selector)) {
            throw new IllegalStateException(
                    "Core Integer already defines a local " + selector + " slot");
        }
        integerPrototype.createLocalSlot(
                selector,
                ProtosClosureValue.nativeClosure(
                        (activation, supplied) -> {
                            ProtosIntegerValue receiver = requireIntegerReceiver(activation);
                            if (supplied.size() != 1
                                    || !(supplied.get(0) instanceof ProtosIntegerValue argument)
                                    || argument.value().signum() == 0) {
                                throw new ProtosSignalException(
                                        ProtosCoreErrors.newError(activation));
                            }
                            return new ProtosIntegerValue(
                                    operation.apply(receiver.value(), argument.value()));
                        }));
    }

    private static void installBinary(
            ProtosObjectValue integerPrototype,
            String selector,
            BiFunction<java.math.BigInteger, java.math.BigInteger, java.math.BigInteger> operation) {
        if (integerPrototype.hasLocalSlot(selector)) {
            throw new IllegalStateException(
                    "Core Integer already defines a local " + selector + " slot");
        }
        integerPrototype.createLocalSlot(
                selector,
                ProtosClosureValue.nativeClosure(
                        (activation, supplied) -> {
                            ProtosIntegerValue receiver = requireIntegerReceiver(activation);
                            if (supplied.size() != 1
                                    || !(supplied.get(0) instanceof ProtosIntegerValue argument)) {
                                throw new ProtosSignalException(
                                        ProtosCoreErrors.newError(activation));
                            }
                            return new ProtosIntegerValue(
                                    operation.apply(receiver.value(), argument.value()));
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
                                                "Core Integer source did not define " + selector));
        if (!(value instanceof ProtosClosureValue closure)
                || closure.definition() == null
                || closure.executionPlan().isEmpty()
                || closure.nativeBody().isPresent()) {
            throw new IllegalStateException(
                    "Core Integer." + selector + " must be installed from distributable Core source");
        }
    }

    private static ProtosIntegerValue requireIntegerReceiver(
            com.guillermomolina.protos.runtime.ProtosActivation activation) {
        if (!(activation.receiver() instanceof ProtosIntegerValue integer)) {
            throw new ProtosSignalException(ProtosCoreErrors.newError(activation));
        }
        return integer;
    }
}
