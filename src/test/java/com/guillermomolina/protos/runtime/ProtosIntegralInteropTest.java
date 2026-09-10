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

import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ProtosIntegralInteropTest {
    private final InteropLibrary interop = InteropLibrary.getUncached();

    @Test
    void arbitraryIntegerExportsExactIntegralWidthsAndBigInteger() throws Exception {
        ProtosIntegerValue byteMax =
                new ProtosIntegerValue(BigInteger.valueOf(Byte.MAX_VALUE));
        ProtosIntegerValue byteOverflow =
                new ProtosIntegerValue(BigInteger.valueOf(Byte.MAX_VALUE + 1L));

        assertTrue(interop.isNumber(byteMax));
        assertTrue(interop.fitsInByte(byteMax));
        assertEquals(Byte.MAX_VALUE, interop.asByte(byteMax));

        assertFalse(interop.fitsInByte(byteOverflow));
        assertTrue(interop.fitsInShort(byteOverflow));
        assertThrows(
                UnsupportedMessageException.class,
                () -> interop.asByte(byteOverflow));

        BigInteger huge = BigInteger.ONE.shiftLeft(200).add(BigInteger.ONE);
        ProtosIntegerValue hugeValue = new ProtosIntegerValue(huge);
        assertTrue(interop.fitsInBigInteger(hugeValue));
        assertEquals(huge, interop.asBigInteger(hugeValue));
        assertFalse(interop.fitsInLong(hugeValue));
        assertThrows(
                UnsupportedMessageException.class,
                () -> interop.asLong(hugeValue));
    }

    @Test
    void floatingWidthClaimsRequireExactMathematicalRoundTrip() throws Exception {
        ProtosIntegerValue floatExact =
                new ProtosIntegerValue(BigInteger.ONE.shiftLeft(24));
        ProtosIntegerValue floatRounded =
                new ProtosIntegerValue(BigInteger.ONE.shiftLeft(24).add(BigInteger.ONE));
        assertTrue(interop.fitsInFloat(floatExact));
        assertEquals((float) (1 << 24), interop.asFloat(floatExact));
        assertFalse(interop.fitsInFloat(floatRounded));
        assertThrows(
                UnsupportedMessageException.class,
                () -> interop.asFloat(floatRounded));

        BigInteger doubleBoundary = BigInteger.ONE.shiftLeft(53);
        ProtosIntegerValue doubleExact = new ProtosIntegerValue(doubleBoundary);
        ProtosIntegerValue doubleRounded =
                new ProtosIntegerValue(doubleBoundary.add(BigInteger.ONE));
        assertTrue(interop.fitsInDouble(doubleExact));
        assertEquals(Math.scalb(1.0, 53), interop.asDouble(doubleExact));
        assertFalse(interop.fitsInDouble(doubleRounded));
        assertThrows(
                UnsupportedMessageException.class,
                () -> interop.asDouble(doubleRounded));

        ProtosIntegerValue largeExact =
                new ProtosIntegerValue(BigInteger.ONE.shiftLeft(80));
        ProtosIntegerValue largeRounded =
                new ProtosIntegerValue(BigInteger.ONE.shiftLeft(80).add(BigInteger.ONE));
        assertTrue(interop.fitsInDouble(largeExact));
        assertFalse(interop.fitsInDouble(largeRounded));

        ProtosIntegerValue decimalTrap =
                new ProtosIntegerValue(new BigInteger("100000000000000000000000"));
        assertFalse(interop.fitsInDouble(decimalTrap));
    }

    @Test
    void fixedIntegerUsesSameExactProjectionWithoutLosingItsProtosFamily()
            throws Exception {
        BigInteger uint64Max = BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE);
        ProtosFixedIntegerValue value =
                new ProtosFixedIntegerValue(
                        ProtosFixedIntegerValue.Family.UINT64,
                        uint64Max);

        assertTrue(interop.isNumber(value));
        assertTrue(interop.fitsInBigInteger(value));
        assertEquals(uint64Max, interop.asBigInteger(value));
        assertFalse(interop.fitsInLong(value));
        assertFalse(interop.fitsInDouble(value));
        assertThrows(
                UnsupportedMessageException.class,
                () -> interop.asLong(value));
        assertSame(ProtosFixedIntegerValue.Family.UINT64, value.family());
        assertEquals(uint64Max, value.value());

        ProtosFixedIntegerValue min =
                new ProtosFixedIntegerValue(
                        ProtosFixedIntegerValue.Family.INT64,
                        BigInteger.valueOf(Long.MIN_VALUE));
        assertTrue(interop.fitsInLong(min));
        assertEquals(Long.MIN_VALUE, interop.asLong(min));

        ProtosFixedIntegerValue exactAboveLong =
                new ProtosFixedIntegerValue(
                        ProtosFixedIntegerValue.Family.UINT64,
                        BigInteger.ONE.shiftLeft(63));
        assertFalse(interop.fitsInLong(exactAboveLong));
        assertTrue(interop.fitsInDouble(exactAboveLong));
        assertEquals(Math.scalb(1.0, 63), interop.asDouble(exactAboveLong));
    }

    @Test
    void allFixedFamiliesRemainExactBigIntegerInteropNumbers() throws Exception {
        for (ProtosFixedIntegerValue.Family family
                : ProtosFixedIntegerValue.Family.values()) {
            ProtosFixedIntegerValue minimum =
                    new ProtosFixedIntegerValue(family, family.minimum());
            ProtosFixedIntegerValue maximum =
                    new ProtosFixedIntegerValue(family, family.maximum());

            assertTrue(interop.isNumber(minimum));
            assertTrue(interop.isNumber(maximum));
            assertTrue(interop.fitsInBigInteger(minimum));
            assertTrue(interop.fitsInBigInteger(maximum));
            assertEquals(family.minimum(), interop.asBigInteger(minimum));
            assertEquals(family.maximum(), interop.asBigInteger(maximum));
            assertSame(family, minimum.family());
            assertSame(family, maximum.family());
        }
    }

    @Test
    void integralDisplayIsValueBasedHostOpaqueAndOtherFacetsStayAbsent()
            throws Exception {
        BigInteger integer = BigInteger.ONE.shiftLeft(100).add(BigInteger.valueOf(7));
        ProtosIntegerValue value = new ProtosIntegerValue(integer);

        String display = String.valueOf(interop.toDisplayString(value, false));
        assertEquals(integer.toString(), display);
        assertFalse(display.contains("ProtosIntegerValue"));
        assertFalse(display.contains("@"));

        assertFalse(interop.isString(value));
        assertFalse(interop.isBoolean(value));
        assertFalse(interop.isNull(value));
        assertFalse(interop.hasMembers(value));
        assertFalse(interop.hasArrayElements(value));
        assertFalse(interop.isExecutable(value));
    }

    @Test
    void ordinaryObjectMemberReadPreservesTheExactIntegralGuestValue()
            throws Exception {
        ProtosObjectValue object =
                new ProtosObjectValue(ProtosObjectValue.rootObject());
        ProtosIntegerValue integer =
                new ProtosIntegerValue(BigInteger.ONE.shiftLeft(90).add(BigInteger.valueOf(3)));
        object.createLocalSlot("number", integer);

        Object read = interop.readMember(object, "number");

        assertSame(integer, read);
        assertTrue(interop.isNumber(read));
        assertEquals(integer.value(), interop.asBigInteger(read));
    }
}
