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
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ProtosArrayInteropTest {
    private final InteropLibrary interop = InteropLibrary.getUncached();

    @Test
    void arrayExportsExactDenseIndexedState() throws Exception {
        ProtosStringValue first = new ProtosStringValue("first");
        ProtosIntegerValue second = new ProtosIntegerValue(BigInteger.valueOf(2));
        ProtosFloatValue third = new ProtosFloatValue(-0.0d);
        ProtosArrayValue array =
                new ProtosArrayValue(
                        ProtosObjectValue.rootObject(),
                        List.of(first, second, third));

        assertTrue(interop.hasArrayElements(array));
        assertEquals(3L, interop.getArraySize(array));

        assertTrue(interop.isArrayElementReadable(array, 0));
        assertTrue(interop.isArrayElementReadable(array, 1));
        assertTrue(interop.isArrayElementReadable(array, 2));
        assertFalse(interop.isArrayElementReadable(array, -1));
        assertFalse(interop.isArrayElementReadable(array, 3));

        assertSame(first, interop.readArrayElement(array, 0));
        assertSame(second, interop.readArrayElement(array, 1));
        assertSame(third, interop.readArrayElement(array, 2));

        assertThrows(
                InvalidArrayIndexException.class,
                () -> interop.readArrayElement(array, -1));
        assertThrows(
                InvalidArrayIndexException.class,
                () -> interop.readArrayElement(array, 3));
    }

    @Test
    void interopReadsCurrentIndexedStorageWithoutSnapshotOrConversion() throws Exception {
        ProtosStringValue first = new ProtosStringValue("before");
        ProtosStringValue replacement = new ProtosStringValue("after");
        ProtosArrayValue array =
                new ProtosArrayValue(
                        ProtosObjectValue.rootObject(),
                        List.of(first));

        assertSame(first, interop.readArrayElement(array, 0));

        array.indexedPut(BigInteger.ZERO, replacement);

        assertEquals(1L, interop.getArraySize(array));
        assertSame(replacement, interop.readArrayElement(array, 0));
        assertEquals("after", interop.asString(interop.readArrayElement(array, 0)));
    }

    @Test
    void cyclesReturnTheSameGuestArrayWithoutParallelIdentityGraph() throws Exception {
        ProtosArrayValue array =
                new ProtosArrayValue(
                        ProtosObjectValue.rootObject(),
                        List.of(ProtosNullValue.INSTANCE));
        array.indexedPut(BigInteger.ZERO, array);

        Object read = interop.readArrayElement(array, 0);

        assertSame(array, read);
        assertTrue(interop.hasArrayElements(read));
    }

    @Test
    void arrayInteropIsReadOnlyEvenWhenGuestArrayIsMutable() throws Exception {
        ProtosArrayValue array =
                new ProtosArrayValue(
                        ProtosObjectValue.rootObject(),
                        List.of(ProtosBooleanValue.TRUE));

        assertFalse(interop.isArrayElementModifiable(array, 0));
        assertFalse(interop.isArrayElementInsertable(array, 1));
        assertFalse(interop.isArrayElementRemovable(array, 0));
        assertFalse(interop.isArrayElementWritable(array, 0));

        assertThrows(
                UnsupportedMessageException.class,
                () -> interop.writeArrayElement(array, 0, ProtosBooleanValue.FALSE));
        assertThrows(
                UnsupportedMessageException.class,
                () -> interop.removeArrayElement(array, 0));

        assertSame(ProtosBooleanValue.TRUE, array.indexedAt(BigInteger.ZERO));
    }

    @Test
    void inheritedObjectMembersAndImplicitIteratorRemainDisabled() throws Exception {
        ProtosArrayValue array =
                new ProtosArrayValue(
                        ProtosObjectValue.rootObject(),
                        List.of(ProtosBooleanValue.TRUE));
        array.createLocalSlot("local", new ProtosStringValue("not-a-D5-member"));

        assertFalse(interop.hasMembers(array));
        assertThrows(
                UnsupportedMessageException.class,
                () -> interop.getMembers(array, false));

        assertFalse(interop.hasIterator(array));
        assertThrows(
                UnsupportedMessageException.class,
                () -> interop.getIterator(array));

        assertEquals("Array", interop.toDisplayString(array, false));
        assertEquals("Array", interop.toDisplayString(array, true));
    }

    @Test
    void accidentalHostOnlyElementsFailClosedInsteadOfLeaking() throws Exception {
        Object hostOnly = new Object();
        ProtosArrayValue array =
                new ProtosArrayValue(
                        ProtosObjectValue.rootObject(),
                        List.of(hostOnly));

        assertEquals(1L, interop.getArraySize(array));
        assertFalse(interop.isArrayElementReadable(array, 0));
        assertThrows(
                InvalidArrayIndexException.class,
                () -> interop.readArrayElement(array, 0));

        assertSame(hostOnly, array.indexedAt(BigInteger.ZERO));
    }

    @Test
    void openClosedAndFrozenArraysRemainReadableThroughSameObservationFacet()
            throws Exception {
        for (ProtosObjectValue.MutationState state : ProtosObjectValue.MutationState.values()) {
            ProtosStringValue element = new ProtosStringValue(state.name());
            ProtosArrayValue array =
                    new ProtosArrayValue(
                            ProtosObjectValue.rootObject(),
                            List.of(element));

            if (state == ProtosObjectValue.MutationState.CLOSED) {
                array.close();
            } else if (state == ProtosObjectValue.MutationState.FROZEN) {
                array.freeze();
            }

            assertTrue(interop.hasArrayElements(array));
            assertEquals(1L, interop.getArraySize(array));
            assertSame(element, interop.readArrayElement(array, 0));
        }
    }
}
