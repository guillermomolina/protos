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
import com.guillermomolina.protos.runtime.ProtosIntegerValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.guillermomolina.protos.runtime.ProtosStringValue;
import java.math.BigInteger;
import java.util.Objects;

public final class ProtosStandardStringProtocol {
    private ProtosStandardStringProtocol() {}

    public static void install(ProtosObjectValue stringPrototype) {
        Objects.requireNonNull(stringPrototype, "stringPrototype");

        if (stringPrototype.hasLocalSlot("recognizes")
                || stringPrototype.hasLocalSlot("size")
                || stringPrototype.hasLocalSlot("at")
                || stringPrototype.hasLocalSlot("+")
                || stringPrototype.hasLocalSlot("concat")) {
            throw new IllegalStateException("Core String already defines a standard protocol slot");
        }

        stringPrototype.createLocalSlot(
                "recognizes",
                ProtosClosureValue.nativeClosure(
                        (activation, supplied) -> {
                            if (activation.receiver() != stringPrototype) {
                                throw new ProtosSignalException(
                                        ProtosCoreErrors.newError(activation));
                            }
                            requireArity(activation, supplied.size(), 1);
                            return ProtosBooleanValue.of(
                                    supplied.get(0) instanceof ProtosStringValue);
                        }));

        stringPrototype.createLocalSlot(
                "size",
                ProtosClosureValue.nativeClosure(
                        (activation, supplied) -> {
                            ProtosStringValue receiver = requireStringReceiver(activation);
                            requireArity(activation, supplied.size(), 0);
                            return new ProtosIntegerValue(
                                    BigInteger.valueOf(
                                            receiver.value().codePointCount(
                                                    0, receiver.value().length())));
                        }));

        stringPrototype.createLocalSlot(
                "at",
                ProtosClosureValue.nativeClosure(
                        (activation, supplied) -> {
                            ProtosStringValue receiver = requireStringReceiver(activation);
                            requireArity(activation, supplied.size(), 1);
                            BigInteger index = requireInteger(activation, supplied.get(0));
                            if (index.signum() < 0 || index.bitLength() > 31) {
                                throw new ProtosSignalException(ProtosCoreErrors.newError(activation));
                            }
                            String scalar = scalarAt(receiver.value(), index.intValue());
                            if (scalar == null) {
                                throw new ProtosSignalException(ProtosCoreErrors.newError(activation));
                            }
                            return new ProtosStringValue(scalar);
                        }));

        stringPrototype.createLocalSlot(
                "+",
                ProtosClosureValue.nativeClosure(
                        (activation, supplied) -> {
                            ProtosStringValue receiver = requireStringReceiver(activation);
                            requireArity(activation, supplied.size(), 1);
                            if (!(supplied.get(0) instanceof ProtosStringValue right)) {
                                throw new ProtosSignalException(ProtosCoreErrors.newError(activation));
                            }
                            return new ProtosStringValue(receiver.value() + right.value());
                        }));

        stringPrototype.createLocalSlot(
                "concat",
                ProtosClosureValue.nativeClosure(
                        (activation, supplied) -> {
                            ProtosStringValue receiver = requireStringReceiver(activation);

                            for (Object value : supplied) {
                                if (!(value instanceof ProtosStringValue)) {
                                    throw new ProtosSignalException(
                                            ProtosCoreErrors.newError(activation));
                                }
                            }

                            StringBuilder result = new StringBuilder(receiver.value());
                            for (Object value : supplied) {
                                result.append(((ProtosStringValue) value).value());
                            }
                            return new ProtosStringValue(result.toString());
                        }));
    }

    private static ProtosStringValue requireStringReceiver(
            com.guillermomolina.protos.runtime.ProtosActivation activation) {
        if (!(activation.receiver() instanceof ProtosStringValue string)) {
            throw new ProtosSignalException(ProtosCoreErrors.newError(activation));
        }
        return string;
    }

    private static void requireArity(
            com.guillermomolina.protos.runtime.ProtosActivation activation,
            int actual,
            int expected) {
        if (actual != expected) {
            throw new ProtosSignalException(ProtosCoreErrors.newError(activation));
        }
    }

    private static BigInteger requireInteger(
            com.guillermomolina.protos.runtime.ProtosActivation activation,
            Object value) {
        if (value instanceof ProtosIntegerValue integer) {
            return integer.value();
        }
        throw new ProtosSignalException(ProtosCoreErrors.newError(activation));
    }

    private static String scalarAt(String text, int wanted) {
        int index = 0;
        for (int start = 0; start < text.length(); ) {
            int codePoint = text.codePointAt(start);
            int end = start + Character.charCount(codePoint);
            if (index == wanted) {
                return text.substring(start, end);
            }
            index++;
            start = end;
        }
        return null;
    }

}
