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
                new ProtosIntegerValue(Byte.MAX_VALUE);
        ProtosIntegerValue byteOverflow =
                new ProtosIntegerValue(Byte.MAX_VALUE + 1L);

        assertTrue(interop.isNumber(byteMax));
        assertTrue(interop.fitsInByte(byteMax));
        assertEquals(Byte.MAX_VALUE, interop.asByte(byteMax));

        assertFalse(interop.fitsInByte(byteOverflow));
        assertTrue(interop.fitsInShort(byteOverflow));
        assertThrows(
                UnsupportedMessageException.class,
                () -> interop.asByte(byteOverflow));

        BigInteger huge = BigInteger.ONE.shiftLeft(200).add(BigInteger.ONE);
        Object hugeValue = ProtosTestIntegers.integer(huge);
        assertTrue(interop.fitsInBigInteger(hugeValue));
        assertEquals(huge, interop.asBigInteger(hugeValue));
        assertFalse(interop.fitsInLong(hugeValue));
        assertThrows(
                UnsupportedMessageException.class,
                () -> interop.asLong(hugeValue));
    }

    @Test
    void floatingWidthClaimsRequireExactMathematicalRoundTrip() throws Exception {
        Object floatExact =
                ProtosTestIntegers.integer(BigInteger.ONE.shiftLeft(24));
        Object floatRounded =
                ProtosTestIntegers.integer(BigInteger.ONE.shiftLeft(24).add(BigInteger.ONE));
        assertTrue(interop.fitsInFloat(floatExact));
        assertEquals((float) (1 << 24), interop.asFloat(floatExact));
        assertFalse(interop.fitsInFloat(floatRounded));
        assertThrows(
                UnsupportedMessageException.class,
                () -> interop.asFloat(floatRounded));

        BigInteger doubleBoundary = BigInteger.ONE.shiftLeft(53);
        Object doubleExact = ProtosTestIntegers.integer(doubleBoundary);
        Object doubleRounded =
                ProtosTestIntegers.integer(doubleBoundary.add(BigInteger.ONE));
        assertTrue(interop.fitsInDouble(doubleExact));
        assertEquals(Math.scalb(1.0, 53), interop.asDouble(doubleExact));
        assertFalse(interop.fitsInDouble(doubleRounded));
        assertThrows(
                UnsupportedMessageException.class,
                () -> interop.asDouble(doubleRounded));

        Object largeExact =
                ProtosTestIntegers.integer(BigInteger.ONE.shiftLeft(80));
        Object largeRounded =
                ProtosTestIntegers.integer(BigInteger.ONE.shiftLeft(80).add(BigInteger.ONE));
        assertTrue(interop.fitsInDouble(largeExact));
        assertFalse(interop.fitsInDouble(largeRounded));

        Object decimalTrap =
                ProtosTestIntegers.integer(new BigInteger("100000000000000000000000"));
        assertFalse(interop.fitsInDouble(decimalTrap));
    }

    @Test
    void fixedInteropCarrierUsesSameExactProjectionWithoutGuestNumericSemantics()
            throws Exception {
        BigInteger uint64Max = BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE);
        ProtosFixedIntegerInteropValue value =
                new ProtosFixedIntegerInteropValue(
                        ProtosFixedIntegerInteropValue.Kind.UINT64,
                        uint64Max);

        assertTrue(interop.isNumber(value));
        assertTrue(interop.fitsInBigInteger(value));
        assertEquals(uint64Max, interop.asBigInteger(value));
        assertFalse(interop.fitsInLong(value));
        assertFalse(interop.fitsInDouble(value));
        assertThrows(
                UnsupportedMessageException.class,
                () -> interop.asLong(value));
        assertSame(ProtosFixedIntegerInteropValue.Kind.UINT64, value.kind());
        assertEquals(uint64Max, value.value());

        ProtosFixedIntegerInteropValue min =
                new ProtosFixedIntegerInteropValue(
                        ProtosFixedIntegerInteropValue.Kind.INT64,
                        BigInteger.valueOf(Long.MIN_VALUE));
        assertTrue(interop.fitsInLong(min));
        assertEquals(Long.MIN_VALUE, interop.asLong(min));

        ProtosFixedIntegerInteropValue exactAboveLong =
                new ProtosFixedIntegerInteropValue(
                        ProtosFixedIntegerInteropValue.Kind.UINT64,
                        BigInteger.ONE.shiftLeft(63));
        assertFalse(interop.fitsInLong(exactAboveLong));
        assertTrue(interop.fitsInDouble(exactAboveLong));
        assertEquals(Math.scalb(1.0, 63), interop.asDouble(exactAboveLong));
    }

    @Test
    void allFixedInteropKindsRemainExactBigIntegerInteropNumbers() throws Exception {
        for (ProtosFixedIntegerInteropValue.Kind family
                : ProtosFixedIntegerInteropValue.Kind.values()) {
            ProtosFixedIntegerInteropValue minimum =
                    new ProtosFixedIntegerInteropValue(family, family.minimum());
            ProtosFixedIntegerInteropValue maximum =
                    new ProtosFixedIntegerInteropValue(family, family.maximum());

            assertTrue(interop.isNumber(minimum));
            assertTrue(interop.isNumber(maximum));
            assertTrue(interop.fitsInBigInteger(minimum));
            assertTrue(interop.fitsInBigInteger(maximum));
            assertEquals(family.minimum(), interop.asBigInteger(minimum));
            assertEquals(family.maximum(), interop.asBigInteger(maximum));
            assertSame(family, minimum.kind());
            assertSame(family, maximum.kind());
        }
    }

    @Test
    void fixedInteropKindRangesAreExactFromWidthAndSignedness() throws Exception {
        for (ProtosFixedIntegerInteropValue.Kind family
                : ProtosFixedIntegerInteropValue.Kind.values()) {
            int width = family.width();
            BigInteger minimum = family.signed()
                    ? BigInteger.ONE.shiftLeft(width - 1).negate()
                    : BigInteger.ZERO;
            BigInteger maximum = family.signed()
                    ? BigInteger.ONE.shiftLeft(width - 1).subtract(BigInteger.ONE)
                    : BigInteger.ONE.shiftLeft(width).subtract(BigInteger.ONE);
            assertEquals(minimum, family.minimum(), family.name());
            assertEquals(maximum, family.maximum(), family.name());
            assertTrue(family.contains(minimum), family.name());
            assertTrue(family.contains(maximum), family.name());
            assertFalse(family.contains(minimum.subtract(BigInteger.ONE)), family.name());
            assertFalse(family.contains(maximum.add(BigInteger.ONE)), family.name());
            assertThrows(IllegalArgumentException.class,
                    () -> new ProtosFixedIntegerInteropValue(family, maximum.add(BigInteger.ONE)));

            ProtosFixedIntegerInteropValue top = new ProtosFixedIntegerInteropValue(family, maximum);
            ProtosFixedIntegerInteropValue bottom =
                    new ProtosFixedIntegerInteropValue(family, minimum);
            assertEquals(maximum, top.value(), family.name());
            assertEquals(minimum, bottom.value(), family.name());
            assertEquals(maximum.toString(), interop.toDisplayString(top, false), family.name());
            assertEquals(maximum.bitLength() < Long.SIZE, interop.fitsInLong(top), family.name());
            assertEquals(maximum.bitLength() < Integer.SIZE, interop.fitsInInt(top), family.name());
            assertEquals(maximum.bitLength() < Byte.SIZE, interop.fitsInByte(top), family.name());
        }
    }

    @Test
    void fixedUnsigned64ProjectsExactlyAboveTheSignedLongRange() throws Exception {
        BigInteger exactDouble = BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE.shiftLeft(11));
        ProtosFixedIntegerInteropValue high =
                new ProtosFixedIntegerInteropValue(
                        ProtosFixedIntegerInteropValue.Kind.UINT64, exactDouble);
        assertTrue(interop.fitsInDouble(high));
        assertEquals(exactDouble.doubleValue(), interop.asDouble(high));
        assertFalse(interop.fitsInFloat(high));
        assertFalse(interop.fitsInLong(high));
        assertFalse(interop.fitsInByte(high));
        assertEquals(exactDouble, interop.asBigInteger(high));

        BigInteger exactFloat = BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE.shiftLeft(40));
        ProtosFixedIntegerInteropValue floatHigh =
                new ProtosFixedIntegerInteropValue(
                        ProtosFixedIntegerInteropValue.Kind.UINT64, exactFloat);
        assertTrue(interop.fitsInFloat(floatHigh));
        assertEquals(exactFloat.floatValue(), interop.asFloat(floatHigh));

        BigInteger odd = BigInteger.ONE.shiftLeft(63).add(BigInteger.ONE);
        ProtosFixedIntegerInteropValue oddHigh =
                new ProtosFixedIntegerInteropValue(ProtosFixedIntegerInteropValue.Kind.UINT64, odd);
        assertFalse(interop.fitsInDouble(oddHigh));
        assertThrows(UnsupportedMessageException.class, () -> interop.asDouble(oddHigh));
        assertEquals(odd, oddHigh.value());
        assertEquals(odd.toString(), interop.toDisplayString(oddHigh, false));

        ProtosFixedIntegerInteropValue negative =
                new ProtosFixedIntegerInteropValue(
                        ProtosFixedIntegerInteropValue.Kind.INT8, BigInteger.valueOf(-128));
        assertEquals((byte) -128, interop.asByte(negative));
        assertEquals(-128.0f, interop.asFloat(negative));
    }

    @Test
    void bigFloatingClaimsUseExactSignificandAndRange() throws Exception {
        BigInteger floatMax = BigInteger.ONE.shiftLeft(24).subtract(BigInteger.ONE).shiftLeft(104);
        assertTrue(interop.fitsInFloat(ProtosTestIntegers.integer(floatMax)));
        assertEquals(Float.MAX_VALUE, interop.asFloat(ProtosTestIntegers.integer(floatMax)));
        assertTrue(interop.fitsInFloat(ProtosTestIntegers.integer(floatMax.negate())));
        assertFalse(interop.fitsInFloat(ProtosTestIntegers.integer(BigInteger.ONE.shiftLeft(128))));

        BigInteger doubleMax = BigInteger.ONE.shiftLeft(53).subtract(BigInteger.ONE).shiftLeft(971);
        assertTrue(interop.fitsInDouble(ProtosTestIntegers.integer(doubleMax)));
        assertEquals(Double.MAX_VALUE, interop.asDouble(ProtosTestIntegers.integer(doubleMax)));
        assertFalse(interop.fitsInDouble(ProtosTestIntegers.integer(BigInteger.ONE.shiftLeft(1024))));
        assertTrue(interop.fitsInDouble(
                ProtosTestIntegers.integer(BigInteger.ONE.shiftLeft(1023).negate())));
    }

    @Test
    void integralDisplayIsValueBasedHostOpaqueAndOtherFacetsStayAbsent()
            throws Exception {
        BigInteger integer = BigInteger.ONE.shiftLeft(100).add(BigInteger.valueOf(7));
        Object value = ProtosTestIntegers.integer(integer);

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
        Object integer =
                ProtosTestIntegers.integer(BigInteger.ONE.shiftLeft(90).add(BigInteger.valueOf(3)));
        object.createLocalSlot("number", integer);

        Object read = interop.readMember(object, "number");

        assertSame(integer, read);
        assertTrue(interop.isNumber(read));
        assertEquals(ProtosTestIntegers.exact(integer), interop.asBigInteger(read));
    }
}
