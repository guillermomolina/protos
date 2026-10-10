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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import org.junit.jupiter.api.Test;

/** I091 semantic Integer capabilities consumed by generic I/O and network clients. */
final class ProtosI091NumericBoundaryTest {
    @Test
    void intRangeRecognitionAndExtraction() {
        assertTrue(ProtosNumericValueSupport.isIntegerInIntRange(new ProtosIntegerValue(65535)));
        assertEquals(65535,
                ProtosNumericValueSupport.exactInt(new ProtosIntegerValue(65535)));
        assertFalse(ProtosNumericValueSupport.isIntegerInIntRange(
                new ProtosIntegerValue(1L + Integer.MAX_VALUE)));
        assertFalse(ProtosNumericValueSupport.isIntegerInIntRange(
                ProtosTestIntegers.integer(BigInteger.ONE.shiftLeft(80))));
        assertFalse(ProtosNumericValueSupport.isIntegerInIntRange(new ProtosFloatValue(1.0)));
        assertFalse(ProtosNumericValueSupport.isIntegerInIntRange(1));
        assertThrows(IllegalArgumentException.class,
                () -> ProtosNumericValueSupport.exactInt(new ProtosFloatValue(1.0)));
    }

    @Test
    void unsignedCodecPreservesExactAddressBits() {
        Object max128 = ProtosNumericValueSupport.integerFromUnsignedBigEndian(
                filled(16), ProtosObjectValue.rootObject());
        assertTrue(ProtosNumericValueSupport.isLargeInteger(max128));
        assertTrue(ProtosNumericValueSupport.isUnsignedIntegerWithin(max128, 128));
        assertFalse(ProtosNumericValueSupport.isUnsignedIntegerWithin(max128, 32));
        assertArrayEquals(filled(16),
                ProtosNumericValueSupport.unsignedBigEndianOrNull(max128, 16));
        assertNull(ProtosNumericValueSupport.unsignedBigEndianOrNull(max128, 4));
        assertNull(ProtosNumericValueSupport.unsignedBigEndianOrNull(
                new ProtosIntegerValue(-1), 4));
    }

    @Test
    void octetIntegersAreSharedValueEqualIntegers() {
        assertSame(ProtosNumericValueSupport.octet(255), ProtosNumericValueSupport.integer(255));
        assertEquals(255L,
                ((ProtosIntegerValue) ProtosNumericValueSupport.octet(255)).longValue());
        assertTrue(ProtosIdentity.identical(
                ProtosNumericValueSupport.octet(7), new ProtosIntegerValue(7)));
        assertEquals(256L,
                ((ProtosIntegerValue) ProtosNumericValueSupport.integer(256)).longValue());
        assertThrows(IllegalArgumentException.class, () -> ProtosNumericValueSupport.octet(256));
    }

    @Test
    void primitiveResultHomeIsAnUnobservableFrameMarker() {
        ProtosReturnHome marker =
                ProtosReturnHome.unobservableAcceptingPrimitiveResultForRuntime();
        assertFalse(marker.isMaterialized());
        assertFalse(marker.isActive());
        assertFalse(marker == ProtosReturnHome.unobservable());
    }

    private static byte[] filled(int width) {
        byte[] bytes = new byte[width];
        java.util.Arrays.fill(bytes, (byte) 0xff);
        return bytes;
    }
}
