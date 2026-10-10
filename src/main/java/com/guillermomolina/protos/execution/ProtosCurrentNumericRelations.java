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

import com.guillermomolina.protos.runtime.ProtosFloatValue;
import com.guillermomolina.protos.runtime.ProtosIntegerValue;
import com.guillermomolina.protos.runtime.ProtosNumericValueSupport;
import com.guillermomolina.protos.execution.ProtosStandardNumberOrderingProtocol.Comparison;
import java.math.BigInteger;

/**
 * Current Integer/Float mathematical equality, ordering and normal hash.
 *
 * Preserves published numeric semantics. D197's additional families are
 * deliberately not admitted until their normative implementation is ready.
 * D013 ordinary overridable dispatch remains owned by the protocols.
 */
final class ProtosCurrentNumericRelations {
    private ProtosCurrentNumericRelations() {}

    static boolean numericEquals(Object left, Object right) {
        if (left instanceof ProtosIntegerValue leftInteger
                && right instanceof ProtosIntegerValue rightInteger) {
            return leftInteger.sameIntegerForRuntime(rightInteger);
        }

        if (left instanceof ProtosFloatValue leftFloat) {
            if (right instanceof ProtosFloatValue rightFloat) {
                return leftFloat.value() == rightFloat.value();
            }
            return floatEqualsExactInteger(
                    leftFloat.value(),
                    ProtosNumericValueSupport.requireCurrentInteger(right));
        }

        if (right instanceof ProtosFloatValue rightFloat) {
            return floatEqualsExactInteger(
                    rightFloat.value(),
                    ProtosNumericValueSupport.requireCurrentInteger(left));
        }

        return ProtosNumericValueSupport.requireCurrentInteger(left)
                .sameIntegerForRuntime(ProtosNumericValueSupport.requireCurrentInteger(right));
    }

    private static boolean floatEqualsExactInteger(double floating, ProtosIntegerValue integer) {
        ProtosIntegerValue exact =
                ProtosStandardNumericConversionProtocol.exactIntegralBinary64(floating);
        return exact != null && exact.sameIntegerForRuntime(integer);
    }

    static Comparison compare(Object left, Object right) {
        if (!ProtosNumericValueSupport.isCurrentNumber(left)
                || !ProtosNumericValueSupport.isCurrentNumber(right)) {
            throw new IllegalArgumentException("numeric comparison requires Number values");
        }

        if (left instanceof ProtosIntegerValue leftInteger
                && right instanceof ProtosIntegerValue rightInteger) {
            return fromSign(leftInteger.compareToIntegerForRuntime(rightInteger));
        }

        if (left instanceof ProtosFloatValue leftFloat) {
            if (right instanceof ProtosFloatValue rightFloat) {
                return compareFloats(leftFloat.value(), rightFloat.value());
            }
            return compareFloatToInteger(
                    leftFloat.value(),
                    ProtosNumericValueSupport.requireCurrentInteger(right));
        }
        if (right instanceof ProtosFloatValue rightFloat) {
            return reverse(compareFloatToInteger(
                    rightFloat.value(),
                    ProtosNumericValueSupport.requireCurrentInteger(left)));
        }
        return fromSign(
                ProtosNumericValueSupport.requireCurrentInteger(left)
                        .compareToIntegerForRuntime(
                                ProtosNumericValueSupport.requireCurrentInteger(right)));
    }

    /* I091 primitive carriers share the exact algorithms used for represented values. */
    static Comparison compareLongs(long left, long right) {
        return fromSign(Long.compare(left, right));
    }

    static Comparison compareFloats(double left, double right) {
        if (Double.isNaN(left) || Double.isNaN(right)) {
            return Comparison.UNORDERED;
        }
        if (left < right) return Comparison.LESS;
        if (left > right) return Comparison.GREATER;
        return Comparison.EQUAL;
    }

    private static Comparison compareFloatToInteger(double floating, ProtosIntegerValue integer) {
        if (Double.isNaN(floating)) {
            return Comparison.UNORDERED;
        }
        if (integer.isSmallForRuntime()) {
            return compareFloatToLong(floating, integer.smallValueForRuntime());
        }
        return compareFloatToBigInteger(floating, integer.value());
    }

    /*
     * Exact for every non-NaN binary64: outside [-2^63, 2^63) the Float lies beyond every long;
     * inside, truncation toward zero is exact and only the fractional part can still decide.
     */
    static Comparison compareFloatToLong(double floating, long integer) {
        if (Double.isNaN(floating)) {
            return Comparison.UNORDERED;
        }
        if (floating >= 0x1p63) {
            return Comparison.GREATER;
        }
        if (floating < -0x1p63) {
            return Comparison.LESS;
        }
        long truncated = (long) floating;
        if (truncated != integer) {
            return fromSign(Long.compare(truncated, integer));
        }
        return compareFloats(floating, (double) truncated);
    }

    private static Comparison compareFloatToBigInteger(double floating, BigInteger integer) {
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


    private static Comparison fromSign(int sign) {
        if (sign < 0) return Comparison.LESS;
        if (sign > 0) return Comparison.GREATER;
        return Comparison.EQUAL;
    }

    static Comparison reverse(Comparison comparison) {
        return switch (comparison) {
            case LESS -> Comparison.GREATER;
            case GREATER -> Comparison.LESS;
            case EQUAL -> Comparison.EQUAL;
            case UNORDERED -> Comparison.UNORDERED;
        };
    }


    private static final ProtosIntegerValue NAN_NORMAL_HASH = new ProtosIntegerValue(2146959360L);

    static ProtosIntegerValue normalHash(Object value) {
        if (value instanceof ProtosIntegerValue integer) {
            return integer;
        }

        if (ProtosNumericValueSupport.isCurrentFloat(value)) {
            double number =
                    ProtosNumericValueSupport.currentFloatValue(value);

            if (Double.isNaN(number)) {
                return NAN_NORMAL_HASH;
            }

            ProtosIntegerValue integral =
                    ProtosStandardNumericConversionProtocol
                            .exactIntegralBinary64(number);

            return integral != null
                    ? integral
                    : new ProtosIntegerValue(
                            Double.hashCode(
                                    number == 0.0 ? 0.0 : number));
        }

        throw new IllegalArgumentException(
                "normal numeric hash requires a current Number value");
    }
}
