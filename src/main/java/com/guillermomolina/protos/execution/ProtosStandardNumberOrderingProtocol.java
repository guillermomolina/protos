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
import com.guillermomolina.protos.runtime.ProtosFixedIntegerValue;
import com.guillermomolina.protos.runtime.ProtosFloatValue;
import com.guillermomolina.protos.runtime.ProtosIntegerValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import java.math.BigInteger;
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
                                    || !isSemanticNumber(activation.receiver())
                                    || !isSemanticNumber(supplied.get(0))) {
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
        if (!isSemanticNumber(left) || !isSemanticNumber(right)) {
            throw new IllegalArgumentException("numeric comparison requires Number values");
        }

        if (left instanceof ProtosFloatValue leftFloat) {
            if (right instanceof ProtosFloatValue rightFloat) {
                return compareFloats(leftFloat.value(), rightFloat.value());
            }
            return compareFloatToInteger(leftFloat.value(), exactInteger(right));
        }
        if (right instanceof ProtosFloatValue rightFloat) {
            return reverse(compareFloatToInteger(rightFloat.value(), exactInteger(left)));
        }
        return fromSign(exactInteger(left).compareTo(exactInteger(right)));
    }

    private static Comparison compareFloats(double left, double right) {
        if (Double.isNaN(left) || Double.isNaN(right)) {
            return Comparison.UNORDERED;
        }
        if (left < right) return Comparison.LESS;
        if (left > right) return Comparison.GREATER;
        return Comparison.EQUAL;
    }

    private static Comparison compareFloatToInteger(double floating, BigInteger integer) {
        if (Double.isNaN(floating)) {
            return Comparison.UNORDERED;
        }
        if (floating == Double.POSITIVE_INFINITY) {
            return Comparison.GREATER;
        }
        if (floating == Double.NEGATIVE_INFINITY) {
            return Comparison.LESS;
        }

        long bits = Double.doubleToRawLongBits(floating);
        boolean negative = (bits & Long.MIN_VALUE) != 0;
        int encodedExponent = (int) ((bits >>> 52) & 0x7ffL);
        long fraction = bits & 0x000fffffffffffffL;

        BigInteger significand;
        int binaryShift;
        if (encodedExponent == 0) {
            significand = BigInteger.valueOf(fraction);
            binaryShift = -1074;
        } else {
            significand = BigInteger.valueOf(fraction | (1L << 52));
            binaryShift = encodedExponent - 1075;
        }
        if (negative) significand = significand.negate();

        if (binaryShift >= 0) {
            return fromSign(significand.shiftLeft(binaryShift).compareTo(integer));
        }
        return fromSign(significand.compareTo(integer.shiftLeft(-binaryShift)));
    }

    private static BigInteger exactInteger(Object value) {
        if (value instanceof ProtosIntegerValue integer) return integer.value();
        if (value instanceof ProtosFixedIntegerValue fixed) return fixed.value();
        throw new IllegalArgumentException("value is not an exact-integer family");
    }

    private static boolean isSemanticNumber(Object value) {
        return value instanceof ProtosIntegerValue
                || value instanceof ProtosFixedIntegerValue
                || value instanceof ProtosFloatValue;
    }

    private static Comparison fromSign(int sign) {
        if (sign < 0) return Comparison.LESS;
        if (sign > 0) return Comparison.GREATER;
        return Comparison.EQUAL;
    }

    private static Comparison reverse(Comparison comparison) {
        return switch (comparison) {
            case LESS -> Comparison.GREATER;
            case GREATER -> Comparison.LESS;
            case EQUAL -> Comparison.EQUAL;
            case UNORDERED -> Comparison.UNORDERED;
        };
    }

    enum Comparison { LESS, EQUAL, GREATER, UNORDERED }
    private enum Relation { LESS, LESS_EQUAL, GREATER, GREATER_EQUAL }
}
