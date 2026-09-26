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
import com.guillermomolina.protos.runtime.ProtosFloatValue;
import com.guillermomolina.protos.runtime.ProtosIntegerValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import java.util.Objects;

public final class ProtosStandardIntegerProtocol {
    private ProtosStandardIntegerProtocol() {}

    public static void install(ProtosObjectValue integerPrototype) {
        Objects.requireNonNull(integerPrototype, "integerPrototype");
        requireSourceBackedClosure(integerPrototype, "negated");

        if (integerPrototype.hasLocalSlot("recognizes")) {
            throw new IllegalStateException(
                    "Core Integer already defines a local recognizes slot");
        }
        integerPrototype.createLocalSlot(
                "recognizes",
                ProtosClosureValue.nativeClosure(
                        (activation, supplied) -> {
                            if (activation.receiver() != integerPrototype
                                    || supplied.size() != 1) {
                                throw new ProtosSignalException(
                                        ProtosCoreErrors.newError(activation));
                            }
                            return ProtosBooleanValue.of(
                                    supplied.get(0) instanceof ProtosIntegerValue);
                        }));

        installBinary(integerPrototype, "+", IntegerBinaryOperation.ADD);
        installBinary(integerPrototype, "-", IntegerBinaryOperation.SUBTRACT);
        installBinary(integerPrototype, "*", IntegerBinaryOperation.MULTIPLY);
        installDivision(integerPrototype);
        installQuotientRemainder(integerPrototype);
    }


    private enum IntegerBinaryOperation {
        ADD,
        SUBTRACT,
        MULTIPLY,
        DIVIDE,
        REMAINDER
    }

    private static java.math.BigInteger applyIntegerBinary(
            IntegerBinaryOperation operation,
            java.math.BigInteger left,
            java.math.BigInteger right) {
        return switch (operation) {
            case ADD -> left.add(right);
            case SUBTRACT -> left.subtract(right);
            case MULTIPLY -> left.multiply(right);
            case DIVIDE -> left.divide(right);
            case REMAINDER -> left.remainder(right);
        };
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
                IntegerBinaryOperation.DIVIDE);
        installExactIntegerBinary(
                integerPrototype,
                "mod",
                IntegerBinaryOperation.REMAINDER);
        installSourceBackedSelector(integerPrototype, "_coreIntegerPercent", "%");
    }

    private static void installExactIntegerBinary(
            ProtosObjectValue integerPrototype,
            String selector,
            IntegerBinaryOperation operation) {
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
                                    applyIntegerBinary(operation, receiver.value(), argument.value()));
                        }));
    }

    private static void installBinary(
            ProtosObjectValue integerPrototype,
            String selector,
            IntegerBinaryOperation operation) {
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
                                    applyIntegerBinary(operation, receiver.value(), argument.value()));
                        }));
    }

    private static void installSourceBackedSelector(
            ProtosObjectValue prototype, String sourceName, String selector) {
        if (prototype.hasLocalSlot(selector)) {
            throw new IllegalStateException(
                    "Core Integer already defines a local " + selector + " slot");
        }
        ProtosClosureValue closure = requireSourceBackedClosure(prototype, sourceName);
        prototype.removeLocalSlot(sourceName);
        prototype.createLocalSlot(selector, closure);
    }

    private static ProtosClosureValue requireSourceBackedClosure(
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
        return closure;
    }

    private static ProtosIntegerValue requireIntegerReceiver(
            com.guillermomolina.protos.runtime.ProtosActivation activation) {
        if (!(activation.receiver() instanceof ProtosIntegerValue integer)) {
            throw new ProtosSignalException(ProtosCoreErrors.newError(activation));
        }
        return integer;
    }
}
