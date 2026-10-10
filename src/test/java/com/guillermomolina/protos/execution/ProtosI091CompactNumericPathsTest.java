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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.execution.ProtosStandardNumberOrderingProtocol.Comparison;
import com.guillermomolina.protos.runtime.ProtosFloatValue;
import com.guillermomolina.protos.runtime.ProtosIntegerValue;
import java.math.BigInteger;
import org.junit.jupiter.api.Test;

/** I091 compact numeric paths must agree exactly with the arbitrary-precision semantics. */
final class ProtosI091CompactNumericPathsTest {
    @Test
    void exactQuotientRoundingAgreesAcrossCompactAndScaledPaths() {
        assertEquals(1.0 / 3.0, divide(1L, 3L));
        assertEquals(-2.5, divide(-5L, 2L));
        assertEquals(
                Double.doubleToRawLongBits(0.0d), Double.doubleToRawLongBits(divide(0L, -5L)));
        assertThrows(ArithmeticException.class, () -> divide(1L, 0L));

        // 2^53 + 1 lies halfway between two binary64 values and rounds to even.
        assertEquals(0x1p53, divide((1L << 53) + 1L, 1L));
        assertEquals(0x1p53 + 4.0, divide((1L << 53) + 3L, 1L));
        assertEquals(
                (double) Long.MAX_VALUE / 3.0,
                divide(Long.MAX_VALUE, 3L),
                Math.ulp((double) Long.MAX_VALUE / 3.0));
        assertEquals(
                0x1p64 / 3.0,
                ProtosBinary64Rounding.divideExactIntegers(
                        new ProtosIntegerValue(BigInteger.ONE.shiftLeft(64)),
                        new ProtosIntegerValue(3L)));
        assertEquals(
                0x1p63,
                ProtosBinary64Rounding.roundExactInteger(new ProtosIntegerValue(Long.MAX_VALUE)));
    }

    @Test
    void mixedOrderingIsExactForCompactIntegers() {
        assertEquals(Comparison.GREATER, compare(new ProtosIntegerValue(3L), 2.5));
        assertEquals(Comparison.LESS, compare(new ProtosIntegerValue(-2L), 2.5));
        assertEquals(Comparison.GREATER, compare(new ProtosIntegerValue(-2L), -2.5));
        assertEquals(Comparison.EQUAL, compare(new ProtosIntegerValue(0L), -0.0));
        assertEquals(Comparison.EQUAL, compare(new ProtosIntegerValue(Long.MIN_VALUE), -0x1p63));
        assertEquals(Comparison.LESS, compare(new ProtosIntegerValue(Long.MAX_VALUE), 0x1p63));
        assertEquals(
                Comparison.LESS,
                compare(new ProtosIntegerValue(Long.MIN_VALUE + 1L), -0x1p63 + 2048.0));
        assertEquals(
                Comparison.GREATER,
                compare(new ProtosIntegerValue(Long.MIN_VALUE), Double.NEGATIVE_INFINITY));
        assertEquals(Comparison.UNORDERED, compare(new ProtosIntegerValue(1L), Double.NaN));
        assertEquals(
                Comparison.LESS,
                compare(new ProtosIntegerValue(BigInteger.ONE.shiftLeft(64)), 0x1p65));
    }

    @Test
    void integralBinary64ExtractionSplitsAtTheSignedLongRange() {
        ProtosIntegerValue minimum =
                ProtosStandardNumericConversionProtocol.exactIntegralBinary64(-0x1p63);
        assertTrue(minimum.isSmallForRuntime());
        assertEquals(Long.MIN_VALUE, minimum.smallValueForRuntime());

        ProtosIntegerValue twoPow63 =
                ProtosStandardNumericConversionProtocol.exactIntegralBinary64(0x1p63);
        assertFalse(twoPow63.isSmallForRuntime());
        assertEquals(BigInteger.ONE.shiftLeft(63), twoPow63.value());
        assertEquals(
                BigInteger.ONE.shiftLeft(70).negate(),
                ProtosStandardNumericConversionProtocol.exactIntegralBinary64(-0x1p70).value());
        assertNull(ProtosStandardNumericConversionProtocol.exactIntegralBinary64(0x1p-1074));
        assertNull(ProtosStandardNumericConversionProtocol.exactIntegralBinary64(0.5));
    }

    @Test
    void mixedEqualityAndNormalHashAgreeForCompactIntegers() {
        ProtosIntegerValue integer = new ProtosIntegerValue(1L << 60);
        ProtosFloatValue floating = new ProtosFloatValue(0x1p60);
        assertTrue(ProtosCurrentNumericRelations.numericEquals(integer, floating));
        assertFalse(
                ProtosCurrentNumericRelations.numericEquals(
                        new ProtosIntegerValue((1L << 60) + 1L), floating));
        assertTrue(
                ProtosCurrentNumericRelations.normalHash(integer)
                        .sameIntegerForRuntime(
                                ProtosCurrentNumericRelations.normalHash(floating)));
        assertEquals(
                BigInteger.valueOf(Double.hashCode(0.5)),
                ProtosCurrentNumericRelations.normalHash(new ProtosFloatValue(0.5)).value());
    }

    private static double divide(long numerator, long denominator) {
        return ProtosBinary64Rounding.divideExactIntegers(
                new ProtosIntegerValue(numerator), new ProtosIntegerValue(denominator));
    }

    private static Comparison compare(ProtosIntegerValue integer, double floating) {
        return ProtosCurrentNumericRelations.compare(integer, new ProtosFloatValue(floating));
    }
}
