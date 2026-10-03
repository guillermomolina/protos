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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import org.junit.jupiter.api.Test;

final class ProtosIntegerValueRepresentationTest {
    private static final BigInteger LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);
    private static final BigInteger LONG_MIN = BigInteger.valueOf(Long.MIN_VALUE);
    private static final BigInteger ABOVE_LONG = LONG_MAX.add(BigInteger.ONE);
    private static final BigInteger BELOW_LONG = LONG_MIN.subtract(BigInteger.ONE);

    @Test
    void canonicalizesTheEntireSignedLongRangeToSmallRepresentation() {
        for (BigInteger value :
                new BigInteger[] {
                    BigInteger.ZERO,
                    BigInteger.ONE,
                    BigInteger.ONE.negate(),
                    LONG_MAX,
                    LONG_MIN
                }) {
            ProtosIntegerValue integer = new ProtosIntegerValue(value);
            assertTrue(integer.isSmallForRuntime(), value.toString());
            assertEquals(value, integer.value());
        }

        assertFalse(new ProtosIntegerValue(ABOVE_LONG).isSmallForRuntime());
        assertFalse(new ProtosIntegerValue(BELOW_LONG).isSmallForRuntime());
    }

    @Test
    void smallArithmeticStaysSmallAndOverflowPromotesExactly() {
        ProtosIntegerValue forty = new ProtosIntegerValue(40L);
        ProtosIntegerValue two = new ProtosIntegerValue(2L);

        assertSmall(42L, forty.addForRuntime(two));
        assertSmall(38L, forty.subtractForRuntime(two));
        assertSmall(80L, forty.multiplyForRuntime(two));
        assertSmall(20L, forty.divideForRuntime(two));
        assertSmall(0L, forty.remainderForRuntime(two));

        assertBig(
                ABOVE_LONG,
                new ProtosIntegerValue(Long.MAX_VALUE)
                        .addForRuntime(new ProtosIntegerValue(1L)));

        assertBig(
                BELOW_LONG,
                new ProtosIntegerValue(Long.MIN_VALUE)
                        .subtractForRuntime(new ProtosIntegerValue(1L)));

        ProtosIntegerValue multiplicand =
                new ProtosIntegerValue(3_037_000_500L);
        assertBig(
                BigInteger.valueOf(3_037_000_500L).pow(2),
                multiplicand.multiplyForRuntime(multiplicand));
    }

    @Test
    void bigArithmeticNormalizesBackToSmallWhenTheResultFits() {
        ProtosIntegerValue above = new ProtosIntegerValue(ABOVE_LONG);
        ProtosIntegerValue maximum = new ProtosIntegerValue(Long.MAX_VALUE);

        ProtosIntegerValue result = above.subtractForRuntime(maximum);

        assertSmall(1L, result);
    }

    @Test
    void quotientAndRemainderPreserveExactSignedSemantics() {
        assertBig(
                BigInteger.ONE.shiftLeft(63),
                new ProtosIntegerValue(Long.MIN_VALUE)
                        .divideForRuntime(new ProtosIntegerValue(-1L)));

        assertSmall(
                2L,
                new ProtosIntegerValue(7L)
                        .divideForRuntime(new ProtosIntegerValue(3L)));
        assertSmall(
                -2L,
                new ProtosIntegerValue(-7L)
                        .divideForRuntime(new ProtosIntegerValue(3L)));
        assertSmall(
                -2L,
                new ProtosIntegerValue(7L)
                        .divideForRuntime(new ProtosIntegerValue(-3L)));
        assertSmall(
                2L,
                new ProtosIntegerValue(-7L)
                        .divideForRuntime(new ProtosIntegerValue(-3L)));

        assertSmall(
                1L,
                new ProtosIntegerValue(7L)
                        .remainderForRuntime(new ProtosIntegerValue(3L)));
        assertSmall(
                -1L,
                new ProtosIntegerValue(-7L)
                        .remainderForRuntime(new ProtosIntegerValue(3L)));
        assertSmall(
                1L,
                new ProtosIntegerValue(7L)
                        .remainderForRuntime(new ProtosIntegerValue(-3L)));
        assertSmall(
                -1L,
                new ProtosIntegerValue(-7L)
                        .remainderForRuntime(new ProtosIntegerValue(-3L)));
    }

    @Test
    void comparisonAndSemanticIdentityDependOnlyOnTheMathematicalInteger() {
        ProtosIntegerValue smallA = new ProtosIntegerValue(42L);
        ProtosIntegerValue smallB =
                new ProtosIntegerValue(BigInteger.valueOf(42L));

        ProtosIntegerValue positiveBigA =
                new ProtosIntegerValue(BigInteger.ONE.shiftLeft(100));
        ProtosIntegerValue positiveBigB =
                new ProtosIntegerValue(BigInteger.ONE.shiftLeft(100));

        ProtosIntegerValue negativeBig =
                new ProtosIntegerValue(
                        BigInteger.ONE.shiftLeft(100).negate());

        assertTrue(smallA.sameIntegerForRuntime(smallB));
        assertTrue(ProtosIdentity.identical(smallA, smallB));
        assertEquals(0, smallA.compareToIntegerForRuntime(smallB));

        assertTrue(positiveBigA.sameIntegerForRuntime(positiveBigB));
        assertTrue(ProtosIdentity.identical(positiveBigA, positiveBigB));
        assertEquals(
                0,
                positiveBigA.compareToIntegerForRuntime(positiveBigB));

        assertTrue(smallA.compareToIntegerForRuntime(positiveBigA) < 0);
        assertTrue(positiveBigA.compareToIntegerForRuntime(smallA) > 0);

        assertTrue(negativeBig.compareToIntegerForRuntime(smallA) < 0);
        assertTrue(smallA.compareToIntegerForRuntime(negativeBig) > 0);
    }

    @Test
    void exactIntProjectionUsesTheSmallCarrierWithoutChangingIntegerSemantics() {
        ProtosIntegerValue minimum = new ProtosIntegerValue(Integer.MIN_VALUE);
        ProtosIntegerValue maximum = new ProtosIntegerValue(Integer.MAX_VALUE);
        ProtosIntegerValue above =
                new ProtosIntegerValue((long) Integer.MAX_VALUE + 1L);
        ProtosIntegerValue huge =
                new ProtosIntegerValue(BigInteger.ONE.shiftLeft(100));

        assertTrue(minimum.fitsInIntForRuntime());
        assertTrue(maximum.fitsInIntForRuntime());
        assertEquals(Integer.MIN_VALUE, minimum.intValueExactForRuntime());
        assertEquals(Integer.MAX_VALUE, maximum.intValueExactForRuntime());

        assertFalse(above.fitsInIntForRuntime());
        assertFalse(huge.fitsInIntForRuntime());
    }

    private static void assertSmall(
            long expected,
            ProtosIntegerValue actual) {
        assertTrue(actual.isSmallForRuntime());
        assertEquals(expected, actual.smallValueForRuntime());
        assertEquals(BigInteger.valueOf(expected), actual.value());
    }

    private static void assertBig(
            BigInteger expected,
            ProtosIntegerValue actual) {
        assertFalse(actual.isSmallForRuntime());
        assertEquals(expected, actual.value());
    }
}
