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
import com.guillermomolina.protos.runtime.ProtosFixedIntegerValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import java.math.BigInteger;
import java.util.Objects;
import java.util.function.BiFunction;

/**
 * Representation bridge for the Core fixed-width exact-integer arithmetic primitives.
 *
 * <p>Each installed closure is family-specific even though one Java construction site is reused
 * across all eight fixed-width prototypes and the three primitive binary selectors. Derived unary
 * negation remains source-backed on the individual Core prototypes.
 */
public final class ProtosStandardFixedIntegerProtocol {
    private ProtosStandardFixedIntegerProtocol() {}

    public static void install(
            ProtosObjectValue prototype,
            ProtosFixedIntegerValue.Family family) {
        Objects.requireNonNull(prototype, "prototype");
        Objects.requireNonNull(family, "family");

        requireSourceBackedClosure(prototype, "negated");
        installBinary(prototype, family, "+", BigInteger::add);
        installBinary(prototype, family, "-", BigInteger::subtract);
        installBinary(prototype, family, "*", BigInteger::multiply);
    }

    private static void installBinary(
            ProtosObjectValue prototype,
            ProtosFixedIntegerValue.Family family,
            String selector,
            BiFunction<BigInteger, BigInteger, BigInteger> operation) {
        if (prototype.hasLocalSlot(selector)) {
            throw new IllegalStateException(
                    "Core " + family.prototypeName() + " already defines a local " + selector + " slot");
        }

        prototype.createLocalSlot(
                selector,
                ProtosClosureValue.nativeClosure(
                        (activation, supplied) -> {
                            ProtosFixedIntegerValue receiver =
                                    requireReceiver(activation, family);
                            if (supplied.size() != 1
                                    || !(supplied.get(0)
                                            instanceof ProtosFixedIntegerValue argument)
                                    || argument.family() != family) {
                                throw error(activation);
                            }

                            BigInteger result =
                                    operation.apply(receiver.value(), argument.value());
                            if (!family.contains(result)) {
                                throw error(activation);
                            }
                            return new ProtosFixedIntegerValue(family, result);
                        }));
    }

    private static ProtosFixedIntegerValue requireReceiver(
            ProtosActivation activation,
            ProtosFixedIntegerValue.Family family) {
        if (!(activation.receiver() instanceof ProtosFixedIntegerValue receiver)
                || receiver.family() != family) {
            throw error(activation);
        }
        return receiver;
    }

    private static ProtosSignalException error(ProtosActivation activation) {
        return new ProtosSignalException(ProtosCoreErrors.newError(activation));
    }

    private static ProtosClosureValue requireSourceBackedClosure(
            ProtosObjectValue prototype,
            String selector) {
        Object value =
                prototype
                        .readLocalSlot(selector)
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "Core fixed-width source did not define " + selector));
        if (!(value instanceof ProtosClosureValue closure)
                || closure.definition() == null
                || closure.executionPlan().isEmpty()
                || closure.nativeBody().isPresent()) {
            throw new IllegalStateException(
                    "Core fixed-width " + selector + " must be installed from distributable Core source");
        }
        return closure;
    }
}
