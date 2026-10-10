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

package com.guillermomolina.protos.runtime;

/**
 * Exact binary32/binary64 projection helpers for signed-64 and unsigned 64-bit integral
 * interop values. A large Integer measures its own magnitude ({@link ProtosLargeIntegerValue}).
 *
 * <p>An integer is exactly representable in a binary floating format when its magnitude is
 * finite in that format and its significant bits (from the highest set bit down to the lowest
 * set bit) fit the format precision. Both conditions are decided on the exact value, so no
 * rounded conversion is ever compared against the original.
 */
final class ProtosIntegralInteropSupport {
    private static final int FLOAT_PRECISION = 24;
    private static final int DOUBLE_PRECISION = 53;

    private ProtosIntegralInteropSupport() {}

    static boolean fitsInFloat(long value) {
        return exactSigned(value, FLOAT_PRECISION);
    }

    static boolean fitsInDouble(long value) {
        return exactSigned(value, DOUBLE_PRECISION);
    }

    /** Exact representability of the unsigned 64-bit integer denoted by {@code bits}. */
    static boolean unsignedFitsInFloat(long bits) {
        return significantBits(bits) <= FLOAT_PRECISION;
    }

    /** Exact representability of the unsigned 64-bit integer denoted by {@code bits}. */
    static boolean unsignedFitsInDouble(long bits) {
        return significantBits(bits) <= DOUBLE_PRECISION;
    }

    /**
     * Value of the unsigned 64-bit integer denoted by {@code bits}; exact whenever
     * {@link #unsignedFitsInDouble(long)} holds: a value at or above 2^63 has its highest bit
     * set, so at most 53 significant bits leave the lowest bit zero and halving is exact.
     */
    static double unsignedToDouble(long bits) {
        return bits >= 0L ? (double) bits : (double) (bits >>> 1) * 2.0;
    }

    /* Long.MIN_VALUE negates to itself; as unsigned bits it is 2^63, a single significant bit. */
    private static boolean exactSigned(long value, int precision) {
        return significantBits(value < 0L ? -value : value) <= precision;
    }

    private static int significantBits(long unsignedMagnitude) {
        if (unsignedMagnitude == 0L) {
            return 0;
        }
        return Long.SIZE
                - Long.numberOfLeadingZeros(unsignedMagnitude)
                - Long.numberOfTrailingZeros(unsignedMagnitude);
    }
}
