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

final class ProtosFloatInteropTest {
    private final InteropLibrary interop = InteropLibrary.getUncached();

    @Test
    void finiteFloatExportsOnlyExactNumericConversions() throws Exception {
        ProtosFloatValue exactInteger = new ProtosFloatValue(42.0d);

        assertTrue(interop.isNumber(exactInteger));
        assertTrue(interop.fitsInByte(exactInteger));
        assertTrue(interop.fitsInShort(exactInteger));
        assertTrue(interop.fitsInInt(exactInteger));
        assertTrue(interop.fitsInLong(exactInteger));
        assertTrue(interop.fitsInBigInteger(exactInteger));
        assertTrue(interop.fitsInFloat(exactInteger));
        assertTrue(interop.fitsInDouble(exactInteger));

        assertEquals((byte) 42, interop.asByte(exactInteger));
        assertEquals((short) 42, interop.asShort(exactInteger));
        assertEquals(42, interop.asInt(exactInteger));
        assertEquals(42L, interop.asLong(exactInteger));
        assertEquals(BigInteger.valueOf(42), interop.asBigInteger(exactInteger));
        assertEquals(42.0f, interop.asFloat(exactInteger));
        assertEquals(42.0d, interop.asDouble(exactInteger));

        ProtosFloatValue fractional = new ProtosFloatValue(42.5d);
        assertFalse(interop.fitsInByte(fractional));
        assertFalse(interop.fitsInShort(fractional));
        assertFalse(interop.fitsInInt(fractional));
        assertFalse(interop.fitsInLong(fractional));
        assertFalse(interop.fitsInBigInteger(fractional));
        assertTrue(interop.fitsInFloat(fractional));
        assertTrue(interop.fitsInDouble(fractional));
        assertThrows(
                UnsupportedMessageException.class,
                () -> interop.asBigInteger(fractional));
    }

    @Test
    void negativeZeroPreservesSignAndNeverClaimsIntegralConversion() throws Exception {
        ProtosFloatValue negativeZero = new ProtosFloatValue(-0.0d);

        assertFalse(interop.fitsInByte(negativeZero));
        assertFalse(interop.fitsInShort(negativeZero));
        assertFalse(interop.fitsInInt(negativeZero));
        assertFalse(interop.fitsInLong(negativeZero));
        assertFalse(interop.fitsInBigInteger(negativeZero));
        assertTrue(interop.fitsInFloat(negativeZero));
        assertTrue(interop.fitsInDouble(negativeZero));

        assertThrows(
                UnsupportedMessageException.class,
                () -> interop.asInt(negativeZero));
        assertEquals(
                Float.floatToRawIntBits(-0.0f),
                Float.floatToRawIntBits(interop.asFloat(negativeZero)));
        assertEquals(
                Double.doubleToRawLongBits(-0.0d),
                Double.doubleToRawLongBits(interop.asDouble(negativeZero)));
        assertEquals("-0.0", interop.toDisplayString(negativeZero, false));

        ProtosFloatValue positiveZero = new ProtosFloatValue(0.0d);
        assertTrue(interop.fitsInByte(positiveZero));
        assertTrue(interop.fitsInBigInteger(positiveZero));
        assertEquals(BigInteger.ZERO, interop.asBigInteger(positiveZero));
        assertEquals("0.0", interop.toDisplayString(positiveZero, false));
    }

    @Test
    void nanAndInfinitiesRemainBinaryFloatingValuesOnly() throws Exception {
        ProtosFloatValue nan =
                new ProtosFloatValue(Double.longBitsToDouble(0x7ff8_0000_0000_0042L));
        assertTrue(interop.isNumber(nan));
        assertTrue(interop.fitsInFloat(nan));
        assertTrue(interop.fitsInDouble(nan));
        assertFalse(interop.fitsInByte(nan));
        assertFalse(interop.fitsInShort(nan));
        assertFalse(interop.fitsInInt(nan));
        assertFalse(interop.fitsInLong(nan));
        assertFalse(interop.fitsInBigInteger(nan));
        assertTrue(Float.isNaN(interop.asFloat(nan)));
        assertTrue(Double.isNaN(interop.asDouble(nan)));
        assertEquals("NaN", interop.toDisplayString(nan, false));

        for (double special : new double[] {
                Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY
        }) {
            ProtosFloatValue infinity = new ProtosFloatValue(special);
            assertTrue(interop.fitsInFloat(infinity));
            assertTrue(interop.fitsInDouble(infinity));
            assertFalse(interop.fitsInInt(infinity));
            assertFalse(interop.fitsInLong(infinity));
            assertFalse(interop.fitsInBigInteger(infinity));
            assertEquals(special, interop.asDouble(infinity));
            assertEquals((float) special, interop.asFloat(infinity));
        }

        assertEquals(
                "Infinity",
                interop.toDisplayString(
                        new ProtosFloatValue(Double.POSITIVE_INFINITY), false));
        assertEquals(
                "-Infinity",
                interop.toDisplayString(
                        new ProtosFloatValue(Double.NEGATIVE_INFINITY), false));
    }

    @Test
    void floatWidthRequiresExactBinary64ToBinary32Preservation() throws Exception {
        ProtosFloatValue exactlyRepresentable = new ProtosFloatValue(16_777_216.0d);
        ProtosFloatValue losesPrecision = new ProtosFloatValue(16_777_217.0d);

        assertTrue(interop.fitsInFloat(exactlyRepresentable));
        assertEquals(16_777_216.0f, interop.asFloat(exactlyRepresentable));

        assertFalse(interop.fitsInFloat(losesPrecision));
        assertThrows(
                UnsupportedMessageException.class,
                () -> interop.asFloat(losesPrecision));

        ProtosFloatValue overflowsBinary32 =
                new ProtosFloatValue(Double.MAX_VALUE);
        assertFalse(interop.fitsInFloat(overflowsBinary32));
        assertThrows(
                UnsupportedMessageException.class,
                () -> interop.asFloat(overflowsBinary32));
        assertTrue(interop.fitsInDouble(overflowsBinary32));
    }

    @Test
    void longBoundaryAvoidsJavaSaturatingCastFalsePositive() throws Exception {
        ProtosFloatValue roundedLongMax =
                new ProtosFloatValue((double) Long.MAX_VALUE);

        assertEquals(Math.scalb(1.0d, 63), roundedLongMax.value());
        assertFalse(interop.fitsInLong(roundedLongMax));
        assertThrows(
                UnsupportedMessageException.class,
                () -> interop.asLong(roundedLongMax));
        assertTrue(interop.fitsInBigInteger(roundedLongMax));
        assertEquals(
                BigInteger.ONE.shiftLeft(63),
                interop.asBigInteger(roundedLongMax));

        ProtosFloatValue longMin = new ProtosFloatValue((double) Long.MIN_VALUE);
        assertTrue(interop.fitsInLong(longMin));
        assertEquals(Long.MIN_VALUE, interop.asLong(longMin));
    }

    @Test
    void floatDisplayIsValueBasedHostOpaqueAndOtherFacetsStayAbsent() {
        ProtosFloatValue value = new ProtosFloatValue(1.25d);

        String display = String.valueOf(interop.toDisplayString(value, false));
        assertEquals("1.25", display);
        assertFalse(display.contains("ProtosFloatValue"));
        assertFalse(display.contains("@"));

        assertFalse(interop.isString(value));
        assertFalse(interop.isBoolean(value));
        assertFalse(interop.isNull(value));
        assertFalse(interop.hasMembers(value));
        assertFalse(interop.hasArrayElements(value));
        assertFalse(interop.isExecutable(value));
    }

    @Test
    void ordinaryObjectMemberReadPreservesExactFloatGuestValue() throws Exception {
        ProtosObjectValue object =
                new ProtosObjectValue(ProtosObjectValue.rootObject());
        ProtosFloatValue value = new ProtosFloatValue(-0.0d);
        object.createLocalSlot("float", value);

        Object read = interop.readMember(object, "float");

        assertSame(value, read);
        assertTrue(interop.isNumber(read));
        assertEquals(
                Double.doubleToRawLongBits(-0.0d),
                Double.doubleToRawLongBits(interop.asDouble(read)));
    }
}
