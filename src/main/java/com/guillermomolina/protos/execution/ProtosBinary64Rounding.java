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

import com.guillermomolina.protos.runtime.ProtosIntegerValue;
import com.guillermomolina.protos.runtime.ProtosNumericValueSupport;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import java.math.BigInteger;
import java.util.Objects;

final class ProtosBinary64Rounding {
    private static final long TWO_POW_52 = 1L << 52;
    private static final long TWO_POW_53 = 1L << 53;
    private static final long SIGN_BIT = 0x8000000000000000L;
    private static final long POSITIVE_INFINITY_BITS = 0x7ff0000000000000L;
    private static final long EXACT_BINARY64_INTEGER_BOUND = 1L << 53;

    private ProtosBinary64Rounding() {}

    /** The binary64 value nearest to an exact Integer, ties to even. */
    static double roundExactInteger(Object integer) {
        Objects.requireNonNull(integer, "integer");
        if (integer instanceof ProtosIntegerValue small) {
            return (double) small.longValue();
        }
        return roundBigInteger(ProtosNumericValueSupport.exactBigInteger(integer));
    }

    /*
     * BigInteger.doubleValue() is correctly rounded to nearest, ties to even, and overflows to
     * infinity; a big Integer is never zero, so no signed-zero case arises.
     */
    @TruffleBoundary
    private static double roundBigInteger(BigInteger value) {
        return value.doubleValue();
    }

    /**
     * The binary64 value nearest to the exact rational quotient, ties to even. When both
     * operands are exact binary64 integers, one IEEE division already rounds that exact
     * quotient once, so only other operands need arbitrary-precision scaling.
     */
    static double divideExactIntegers(Object numerator, Object denominator) {
        Objects.requireNonNull(numerator, "numerator");
        Objects.requireNonNull(denominator, "denominator");
        if (numerator instanceof ProtosIntegerValue smallNumerator
                && denominator instanceof ProtosIntegerValue smallDenominator) {
            long exactNumerator = smallNumerator.longValue();
            long exactDenominator = smallDenominator.longValue();
            if (exactDenominator == 0L) {
                throw new ArithmeticException("division by zero");
            }
            if (primitiveQuotientAdmitted(exactNumerator, exactDenominator)) {
                return dividePrimitiveIntegers(exactNumerator, exactDenominator);
            }
        }
        return divideExactIntegers(
                ProtosNumericValueSupport.exactBigInteger(numerator),
                ProtosNumericValueSupport.exactBigInteger(denominator));
    }

    /**
     * I091 primitive carriers: true when {@link #dividePrimitiveIntegers} yields the exact
     * correctly rounded quotient without arbitrary precision. A zero divisor is never admitted.
     */
    static boolean primitiveQuotientAdmitted(long numerator, long denominator) {
        return denominator != 0L
                && (numerator == 0L
                        || (exactBinary64Integer(numerator) && exactBinary64Integer(denominator)));
    }

    /* Requires primitiveQuotientAdmitted; an exact zero quotient is +0.0. */
    static double dividePrimitiveIntegers(long numerator, long denominator) {
        if (numerator == 0L) {
            return 0.0d;
        }
        return (double) numerator / (double) denominator;
    }

    private static boolean exactBinary64Integer(long value) {
        return value >= -EXACT_BINARY64_INTEGER_BOUND && value <= EXACT_BINARY64_INTEGER_BOUND;
    }

    @TruffleBoundary
    private static double divideExactIntegers(BigInteger numerator, BigInteger denominator) {
        Objects.requireNonNull(numerator, "numerator");
        Objects.requireNonNull(denominator, "denominator");
        if (denominator.signum() == 0) {
            throw new ArithmeticException("division by zero");
        }
        if (numerator.signum() == 0) {
            return 0.0d;
        }

        boolean negative = numerator.signum() != denominator.signum();
        BigInteger absoluteNumerator = numerator.abs();
        BigInteger absoluteDenominator = denominator.abs();

        long magnitudeBits = roundedMagnitudeBits(absoluteNumerator, absoluteDenominator);
        long rawBits = negative ? magnitudeBits | SIGN_BIT : magnitudeBits;
        return Double.longBitsToDouble(rawBits);
    }

    private static long roundedMagnitudeBits(
            BigInteger numerator,
            BigInteger denominator) {
        int roughExponent = numerator.bitLength() - denominator.bitLength();

        if (roughExponent > 1024) {
            return POSITIVE_INFINITY_BITS;
        }
        if (roughExponent < -1075) {
            return 0L;
        }

        int exponent = floorBinaryExponent(numerator, denominator, roughExponent);
        if (exponent < -1075) {
            return 0L;
        }

        if (exponent < -1022) {
            return roundedScaledQuotient(numerator, denominator, 1074);
        }

        long significand = roundedScaledQuotient(numerator, denominator, 52 - exponent);

        if (significand == TWO_POW_53) {
            significand >>>= 1;
            exponent++;
        }

        if (exponent > 1023) {
            return POSITIVE_INFINITY_BITS;
        }

        long exponentBits = ((long) exponent + 1023L) << 52;
        long fractionBits = significand - TWO_POW_52;
        return exponentBits | fractionBits;
    }

    private static int floorBinaryExponent(
            BigInteger numerator,
            BigInteger denominator,
            int roughExponent) {
        if (roughExponent >= 0) {
            return numerator.compareTo(denominator.shiftLeft(roughExponent)) < 0
                    ? roughExponent - 1
                    : roughExponent;
        }
        return numerator.shiftLeft(-roughExponent).compareTo(denominator) < 0
                ? roughExponent - 1
                : roughExponent;
    }

    /*
     * The scale is chosen so the truncated quotient is below 2^53 (in [2^52, 2^53) for normal
     * results, below 2^52 for subnormal ones); rounding then carries to at most 2^53, so the
     * rounded significand is exact in a long.
     */
    private static long roundedScaledQuotient(
            BigInteger numerator,
            BigInteger denominator,
            int binaryShift) {
        BigInteger scaledNumerator = numerator;
        BigInteger scaledDenominator = denominator;
        if (binaryShift >= 0) {
            scaledNumerator = numerator.shiftLeft(binaryShift);
        } else {
            scaledDenominator = denominator.shiftLeft(-binaryShift);
        }

        BigInteger[] quotientAndRemainder =
                scaledNumerator.divideAndRemainder(scaledDenominator);
        long quotient = quotientAndRemainder[0].longValueExact();
        BigInteger remainder = quotientAndRemainder[1];

        int halfwayComparison = remainder.shiftLeft(1).compareTo(scaledDenominator);
        if (halfwayComparison > 0
                || (halfwayComparison == 0 && (quotient & 1L) != 0L)) {
            quotient++;
        }
        return quotient;
    }
}
