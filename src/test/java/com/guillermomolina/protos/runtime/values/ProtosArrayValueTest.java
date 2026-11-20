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

package com.guillermomolina.protos.runtime.values;

import com.guillermomolina.protos.runtime.ProtosArrayValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class ProtosArrayValueTest {
    @Test
    void arrayOwnsDenseIndexedStateWithExactElementReferences() {
        ProtosObjectValue parent =
                new ProtosObjectValue(ProtosObjectValue.rootObject());
        Object first = new Object();
        Object second = new Object();

        ProtosArrayValue array =
                new ProtosArrayValue(parent, List.of(first, second));

        assertSame(parent, array.parent().orElseThrow());
        assertEquals(2, array.indexedSize());
        assertSame(first, array.indexedAt(0));
        assertSame(second, array.indexedAt(1));
    }

    @Test
    void indexedUpdatePreservesLengthAndReturnsExactValue() {
        ProtosArrayValue array =
                new ProtosArrayValue(
                        ProtosObjectValue.rootObject(),
                        List.of(Boolean.TRUE));
        Object replacement = new Object();

        assertSame(
                replacement,
                array.indexedPut(0, replacement));
        assertSame(replacement, array.indexedAt(0));
        assertEquals(1, array.indexedSize());
    }

    @Test
    void closedArrayAllowsReplacementButFrozenArrayRejectsBeforeIndexValidation() {
        ProtosArrayValue closed =
                new ProtosArrayValue(
                        ProtosObjectValue.rootObject(),
                        List.of(Boolean.TRUE));
        closed.close();
        closed.indexedPut(0, Boolean.FALSE);
        assertSame(
                Boolean.FALSE,
                closed.indexedAt(0));

        ProtosArrayValue frozen =
                new ProtosArrayValue(
                        ProtosObjectValue.rootObject(),
                        List.of(Boolean.TRUE));
        frozen.freeze();

        assertThrows(
                IllegalStateException.class,
                () ->
                        frozen.indexedPut(
                                99,
                                Boolean.FALSE));
        assertSame(
                Boolean.TRUE,
                frozen.indexedAt(0));
    }

    @Test
    void indexedBoundsAreDenseAndSnapshotIsShallowDetachedAndReadOnly() {
        Object first = new Object();
        ProtosArrayValue array =
                new ProtosArrayValue(
                        ProtosObjectValue.rootObject(),
                        List.of(first));

        assertThrows(
                IndexOutOfBoundsException.class,
                () -> array.indexedAt(-1));
        assertThrows(
                IndexOutOfBoundsException.class,
                () -> array.indexedAt(1));

        List<Object> snapshot = array.indexedSnapshot();
        assertSame(first, snapshot.get(0));
        assertThrows(
                UnsupportedOperationException.class,
                () -> snapshot.add(new Object()));

        Object replacement = new Object();
        array.indexedPut(0, replacement);
        assertSame(first, snapshot.get(0));
        assertSame(replacement, array.indexedAt(0));
    }

    @Test
    void longStorageConstructionGivesEachArrayAPrivateBacking() {
        long[] source = {Long.MIN_VALUE, -1L, 0L, 7L, Long.MAX_VALUE};
        ProtosArrayValue first =
                new ProtosArrayValue(ProtosObjectValue.rootObject(), source);
        ProtosArrayValue second =
                new ProtosArrayValue(ProtosObjectValue.rootObject(), source);

        assertNotSame(first, second);
        assertTrue(first.hasLongStorageForRuntime());
        assertTrue(second.hasLongStorageForRuntime());
        assertLongElements(first, Long.MIN_VALUE, -1L, 0L, 7L, Long.MAX_VALUE);
        assertLongElements(second, Long.MIN_VALUE, -1L, 0L, 7L, Long.MAX_VALUE);

        source[0] = 42L;
        source[4] = 43L;
        assertLongElements(first, Long.MIN_VALUE, -1L, 0L, 7L, Long.MAX_VALUE);
        assertLongElements(second, Long.MIN_VALUE, -1L, 0L, 7L, Long.MAX_VALUE);

        assertEquals(99L, first.indexedLongPut(0, 99L));
        assertEquals(-99L, first.indexedLongPut(4, -99L));
        assertLongElements(first, 99L, -1L, 0L, 7L, -99L);
        assertLongElements(second, Long.MIN_VALUE, -1L, 0L, 7L, Long.MAX_VALUE);

        assertEquals(5, first.indexedSize());
        assertThrows(
                IndexOutOfBoundsException.class,
                () -> first.indexedLongPut(5, 1L));
        assertThrows(
                IndexOutOfBoundsException.class,
                () -> first.indexedLongPut(-1, 1L));
        assertEquals(5, first.indexedSize());

        List<Object> snapshot = second.indexedSnapshot();
        second.indexedLongPut(2, 123L);
        assertEquals(Long.valueOf(0L), snapshot.get(2));
        assertEquals(123L, second.indexedLongAt(2));

        second.close();
        assertEquals(-5L, second.indexedLongPut(1, -5L));
        assertEquals(-5L, second.indexedLongAt(1));
        assertEquals(5, second.indexedSize());

        first.freeze();
        assertThrows(
                IllegalStateException.class,
                () -> first.indexedLongPut(1, 1L));
        assertLongElements(first, 99L, -1L, 0L, 7L, -99L);
    }

    @Test
    void doubleStorageConstructionGivesEachArrayAPrivateBacking() {
        double[] source = {
            Double.NaN, -0.0d, Double.POSITIVE_INFINITY,
            Double.NEGATIVE_INFINITY, 1.5d
        };
        double[] expected = source.clone();
        ProtosArrayValue first =
                new ProtosArrayValue(ProtosObjectValue.rootObject(), source);
        ProtosArrayValue second =
                new ProtosArrayValue(ProtosObjectValue.rootObject(), source);

        assertNotSame(first, second);
        assertTrue(first.hasDoubleStorageForRuntime());
        assertTrue(second.hasDoubleStorageForRuntime());
        assertDoubleBits(first, expected);
        assertDoubleBits(second, expected);

        source[0] = 3.0d;
        source[1] = 0.0d;
        assertDoubleBits(first, expected);
        assertDoubleBits(second, expected);

        first.indexedDoublePut(0, 0.0d);
        first.indexedDoublePut(1, Double.NaN);
        assertDoubleBits(
                first,
                0.0d, Double.NaN, Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY, 1.5d);
        assertDoubleBits(second, expected);

        assertEquals(5, first.indexedSize());
        assertThrows(
                IndexOutOfBoundsException.class,
                () -> first.indexedDoublePut(5, 1.0d));

        List<Object> snapshot = second.indexedSnapshot();
        second.indexedDoublePut(1, 2.0d);
        assertEquals(
                Double.doubleToRawLongBits(-0.0d),
                Double.doubleToRawLongBits((Double) snapshot.get(1)));
        assertEquals(2.0d, second.indexedDoubleAt(1));

        second.close();
        second.indexedDoublePut(4, -0.0d);
        assertEquals(
                Double.doubleToRawLongBits(-0.0d),
                Double.doubleToRawLongBits(second.indexedDoubleAt(4)));
        assertEquals(5, second.indexedSize());

        first.freeze();
        assertThrows(
                IllegalStateException.class,
                () -> first.indexedDoublePut(2, 1.0d));
        assertEquals(Double.POSITIVE_INFINITY, first.indexedDoubleAt(2));
    }

    @Test
    void booleanStorageConstructionGivesEachArrayAPrivateBacking() {
        boolean[] source = {true, false, true};
        ProtosArrayValue first =
                new ProtosArrayValue(ProtosObjectValue.rootObject(), source);
        ProtosArrayValue second =
                new ProtosArrayValue(ProtosObjectValue.rootObject(), source);

        assertNotSame(first, second);
        assertTrue(first.hasBooleanStorageForRuntime());
        assertTrue(second.hasBooleanStorageForRuntime());
        assertBooleanElements(first, true, false, true);
        assertBooleanElements(second, true, false, true);

        source[0] = false;
        source[1] = true;
        assertBooleanElements(first, true, false, true);
        assertBooleanElements(second, true, false, true);

        for (int round = 0; round < 4; round++) {
            boolean value = round % 2 == 0;
            assertEquals(value, first.indexedBooleanPut(1, value));
            assertEquals(value, first.indexedBooleanAt(1));
            assertBooleanElements(second, true, false, true);
        }

        assertEquals(3, first.indexedSize());
        assertThrows(
                IndexOutOfBoundsException.class,
                () -> first.indexedBooleanPut(3, true));

        List<Object> snapshot = second.indexedSnapshot();
        second.indexedBooleanPut(0, false);
        assertEquals(Boolean.TRUE, snapshot.get(0));
        assertFalse(second.indexedBooleanAt(0));

        second.close();
        second.indexedBooleanPut(2, false);
        assertFalse(second.indexedBooleanAt(2));
        assertEquals(3, second.indexedSize());

        first.freeze();
        assertThrows(
                IllegalStateException.class,
                () -> first.indexedBooleanPut(0, false));
        assertTrue(first.indexedBooleanAt(0));
    }

    private static void assertLongElements(
            ProtosArrayValue array, long... expected) {
        assertEquals(expected.length, array.indexedSize());
        for (int index = 0; index < expected.length; index++) {
            assertEquals(expected[index], array.indexedLongAt(index));
        }
    }

    private static void assertDoubleBits(
            ProtosArrayValue array, double... expected) {
        assertEquals(expected.length, array.indexedSize());
        for (int index = 0; index < expected.length; index++) {
            assertEquals(
                    Double.doubleToRawLongBits(expected[index]),
                    Double.doubleToRawLongBits(array.indexedDoubleAt(index)));
        }
    }

    private static void assertBooleanElements(
            ProtosArrayValue array, boolean... expected) {
        assertEquals(expected.length, array.indexedSize());
        for (int index = 0; index < expected.length; index++) {
            assertEquals(expected[index], array.indexedBooleanAt(index));
        }
    }
}
