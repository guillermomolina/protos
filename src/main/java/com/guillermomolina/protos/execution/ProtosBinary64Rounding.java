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

import java.math.BigInteger;
import java.util.Objects;

final class ProtosBinary64Rounding {
    private static final BigInteger TWO_POW_52 = BigInteger.ONE.shiftLeft(52);
    private static final BigInteger TWO_POW_53 = BigInteger.ONE.shiftLeft(53);
    private static final long SIGN_BIT = 0x8000000000000000L;
    private static final long POSITIVE_INFINITY_BITS = 0x7ff0000000000000L;

    private ProtosBinary64Rounding() {}

    static double divideExactIntegers(BigInteger numerator, BigInteger denominator) {
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
            BigInteger significand = roundedScaledQuotient(numerator, denominator, 1074);
            return significand.longValueExact();
        }

        BigInteger significand =
                roundedScaledQuotient(numerator, denominator, 52 - exponent);

        if (significand.equals(TWO_POW_53)) {
            significand = significand.shiftRight(1);
            exponent++;
        }

        if (exponent > 1023) {
            return POSITIVE_INFINITY_BITS;
        }

        long exponentBits = ((long) exponent + 1023L) << 52;
        long fractionBits = significand.subtract(TWO_POW_52).longValueExact();
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

    private static BigInteger roundedScaledQuotient(
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
        BigInteger quotient = quotientAndRemainder[0];
        BigInteger remainder = quotientAndRemainder[1];

        int halfwayComparison = remainder.shiftLeft(1).compareTo(scaledDenominator);
        if (halfwayComparison > 0
                || (halfwayComparison == 0 && quotient.testBit(0))) {
            quotient = quotient.add(BigInteger.ONE);
        }
        return quotient;
    }
}
