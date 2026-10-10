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

import static org.junit.jupiter.api.Assertions.*;

import com.guillermomolina.protos.runtime.ProtosFloatValue;
import com.guillermomolina.protos.runtime.ProtosIntegerValue;
import java.math.BigInteger;
import org.junit.jupiter.api.Test;

final class ProtosCurrentNumericRelationsTest {

    @Test
    void equalityUsesExactMathematicalValue() {
        ProtosIntegerValue beyond =
                new ProtosIntegerValue(
                        BigInteger.ONE.shiftLeft(53).add(BigInteger.ONE));
        ProtosFloatValue rounded =
                new ProtosFloatValue((double) (1L << 53));

        assertFalse(ProtosStandardNumberEqualityProtocol.numericEquals(
                beyond, rounded));

        ProtosIntegerValue integer = new ProtosIntegerValue(42L);
        ProtosFloatValue floating = new ProtosFloatValue(42.0);

        assertTrue(ProtosStandardNumberEqualityProtocol.numericEquals(
                integer, floating));
        assertTrue(ProtosStandardNumberEqualityProtocol.numericEquals(
                floating, integer));

        assertEquals(
                ProtosCurrentNumericRelations.normalHash(integer).value(),
                ProtosCurrentNumericRelations.normalHash(floating).value());
    }

    @Test
    void signedZeroNanAndHashPreserveCurrentSemantics() {
        ProtosFloatValue positive = new ProtosFloatValue(0.0);
        ProtosFloatValue negative = new ProtosFloatValue(-0.0);
        ProtosFloatValue nan = new ProtosFloatValue(Double.NaN);

        assertTrue(ProtosCurrentNumericRelations.numericEquals(
                positive, negative));
        assertFalse(ProtosCurrentNumericRelations.numericEquals(nan, nan));

        assertEquals(
                ProtosCurrentNumericRelations.normalHash(positive).value(),
                ProtosCurrentNumericRelations.normalHash(negative).value());
        assertEquals(
                BigInteger.valueOf(2146959360L),
                ProtosCurrentNumericRelations.normalHash(nan).value());

        assertEquals(
                ProtosStandardNumberOrderingProtocol.Comparison.UNORDERED,
                ProtosStandardNumberOrderingProtocol.compare(nan, positive));
    }

    @Test
    void orderingIsExactAcrossBinary64Boundaries() {
        ProtosIntegerValue max =
                new ProtosIntegerValue(Long.MAX_VALUE);
        ProtosFloatValue rounded =
                new ProtosFloatValue((double) Long.MAX_VALUE);

        assertEquals(
                ProtosStandardNumberOrderingProtocol.Comparison.LESS,
                ProtosStandardNumberOrderingProtocol.compare(max, rounded));

        assertEquals(
                ProtosStandardNumberOrderingProtocol.Comparison.GREATER,
                ProtosStandardNumberOrderingProtocol.compare(rounded, max));

        ProtosIntegerValue huge =
                new ProtosIntegerValue(BigInteger.ONE.shiftLeft(130));

        assertEquals(
                ProtosStandardNumberOrderingProtocol.Comparison.GREATER,
                ProtosStandardNumberOrderingProtocol.compare(
                        new ProtosFloatValue(Double.POSITIVE_INFINITY),
                        huge));

        ProtosFloatValue exact =
                new ProtosFloatValue(Math.scalb(1.0, 130));

        assertTrue(ProtosCurrentNumericRelations.numericEquals(huge, exact));
        assertEquals(
                ProtosCurrentNumericRelations.normalHash(huge).value(),
                ProtosCurrentNumericRelations.normalHash(exact).value());
    }

    @Test
    void javaHostNumbersRemainOutsideGuestNumericDomain() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ProtosCurrentNumericRelations.compare(
                        Long.valueOf(1), new ProtosIntegerValue(1L)));

        assertThrows(
                IllegalArgumentException.class,
                () -> ProtosCurrentNumericRelations.normalHash(BigInteger.ONE));
    }
}
