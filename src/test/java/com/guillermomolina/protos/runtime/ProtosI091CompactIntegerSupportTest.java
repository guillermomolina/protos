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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import java.math.BigInteger;
import org.junit.jupiter.api.Test;

/** I091 compact-Integer support that must stay exact without arbitrary-precision allocation. */
final class ProtosI091CompactIntegerSupportTest {
    private final InteropLibrary interop = InteropLibrary.getUncached();

    @Test
    void unsignedBigEndianCodecIsExactAcrossRepresentations() {
        ProtosIntegerValue ipv4 =
                ProtosIntegerValue.fromUnsignedBigEndianForRuntime(
                        new byte[] {(byte) 192, (byte) 168, 0, 1});
        assertTrue(ipv4.isSmallForRuntime());
        assertEquals(BigInteger.valueOf(3232235521L), ipv4.value());
        assertArrayEquals(
                new byte[] {(byte) 192, (byte) 168, 0, 1}, ipv4.toUnsignedBigEndianForRuntime(4));

        byte[] allOnes = new byte[8];
        java.util.Arrays.fill(allOnes, (byte) 0xff);
        ProtosIntegerValue beyondLong = ProtosIntegerValue.fromUnsignedBigEndianForRuntime(allOnes);
        assertFalse(beyondLong.isSmallForRuntime());
        assertEquals(BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE), beyondLong.value());
        assertArrayEquals(allOnes, beyondLong.toUnsignedBigEndianForRuntime(8));

        byte[] ipv6 = new byte[16];
        ipv6[15] = 1;
        ProtosIntegerValue loopback = ProtosIntegerValue.fromUnsignedBigEndianForRuntime(ipv6);
        assertTrue(loopback.isSmallForRuntime());
        assertArrayEquals(ipv6, loopback.toUnsignedBigEndianForRuntime(16));

        byte[] ipv6Max = new byte[16];
        java.util.Arrays.fill(ipv6Max, (byte) 0xff);
        assertArrayEquals(
                ipv6Max,
                ProtosIntegerValue.fromUnsignedBigEndianForRuntime(ipv6Max)
                        .toUnsignedBigEndianForRuntime(16));

        assertThrows(
                ArithmeticException.class,
                () -> new ProtosIntegerValue(256L).toUnsignedBigEndianForRuntime(1));
        assertThrows(
                ArithmeticException.class,
                () -> new ProtosIntegerValue(-1L).toUnsignedBigEndianForRuntime(8));
    }

    @Test
    void exactHashCodeMatchesCanonicalArbitraryPrecisionHash() {
        long[] samples = {
            0L, 1L, -1L, 42L, -42L, 0xffffffffL, 0x100000000L, -0x100000000L,
            Long.MAX_VALUE, Long.MIN_VALUE, Long.MIN_VALUE + 1L
        };
        for (long sample : samples) {
            assertEquals(
                    BigInteger.valueOf(sample).hashCode(),
                    new ProtosIntegerValue(sample).exactHashCodeForRuntime(),
                    Long.toString(sample));
        }
        BigInteger huge = BigInteger.ONE.shiftLeft(100).negate();
        assertEquals(huge.hashCode(), new ProtosIntegerValue(huge).exactHashCodeForRuntime());
    }

    @Test
    void floatingInteropProjectionIsExactForCompactValues() throws UnsupportedMessageException {
        ProtosIntegerValue exact = new ProtosIntegerValue(1L << 53);
        assertTrue(interop.fitsInDouble(exact));
        assertEquals(0x1p53, interop.asDouble(exact));

        ProtosIntegerValue inexact = new ProtosIntegerValue((1L << 53) + 1L);
        assertFalse(interop.fitsInDouble(inexact));
        assertThrows(UnsupportedMessageException.class, () -> interop.asDouble(inexact));

        ProtosIntegerValue maximum = new ProtosIntegerValue(Long.MAX_VALUE);
        assertFalse(interop.fitsInDouble(maximum));
        assertFalse(interop.fitsInFloat(maximum));

        ProtosIntegerValue minimum = new ProtosIntegerValue(Long.MIN_VALUE);
        assertTrue(interop.fitsInDouble(minimum));
        assertTrue(interop.fitsInFloat(minimum));
        assertEquals(-0x1p63f, interop.asFloat(minimum));

        assertTrue(interop.fitsInFloat(new ProtosIntegerValue(1L << 24)));
        assertFalse(interop.fitsInFloat(new ProtosIntegerValue((1L << 24) + 1L)));

        ProtosIntegerValue twoPow64 = new ProtosIntegerValue(BigInteger.ONE.shiftLeft(64));
        assertFalse(interop.fitsInLong(twoPow64));
        assertTrue(interop.fitsInDouble(twoPow64));
        assertEquals(0x1p64, interop.asDouble(twoPow64));
        assertThrows(UnsupportedMessageException.class, () -> interop.asLong(twoPow64));
    }

    @Test
    void bytesOctetSnapshotCopiesTheOctetInvariant() {
        ProtosBytesValue bytes = new ProtosBytesValue(ProtosObjectValue.rootObject());
        bytes.indexedAdd(new ProtosIntegerValue(0L));
        bytes.indexedAdd(new ProtosIntegerValue(255L));
        assertArrayEquals(new byte[] {0, (byte) 255}, bytes.octetSnapshot());

        bytes.indexedAdd(new ProtosIntegerValue(256L));
        assertThrows(IllegalStateException.class, bytes::octetSnapshot);
    }

    @Test
    void integerLiteralsStayExactAtTheSignedLongBoundary() {
        assertLiteral(Long.MAX_VALUE, "9223372036854775807");
        assertLiteral(999_999_999_999_999_999L, "999999999999999999");
        assertLiteral(Long.MAX_VALUE, "0o777777777777777777777");
        assertLiteral(Long.MAX_VALUE, "0b" + "1".repeat(63));
        assertLiteral(0xfffffffffffffffL, "0xfffffffffffffff");

        ProtosIntegerValue beyond =
                (ProtosIntegerValue) ProtosNumberLiteral.materialize("0xffffffffffffffff");
        assertFalse(beyond.isSmallForRuntime());
        assertEquals(BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE), beyond.value());
    }

    private static void assertLiteral(long expected, String spelling) {
        ProtosIntegerValue value = (ProtosIntegerValue) ProtosNumberLiteral.materialize(spelling);
        assertTrue(value.isSmallForRuntime(), spelling);
        assertEquals(expected, value.smallValueForRuntime(), spelling);
    }
}
