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
import com.guillermomolina.protos.runtime.ProtosStringValue;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ProtosObjectInteropTest {
    private final InteropLibrary interop = InteropLibrary.getUncached();

    @Test
    void ordinaryObjectExportsOnlyExactLocalSlotsInStableSnapshotOrder() throws Exception {
        ProtosObjectValue parent = new ProtosObjectValue(ProtosObjectValue.rootObject());
        parent.createLocalSlot("delegated", new ProtosStringValue("must-not-be-visible"));

        ProtosObjectValue value = new ProtosObjectValue(parent);
        Long first = Long.valueOf(1L);
        value.createLocalSlot("first", first);
        value.createLocalSlot("self", value);

        assertTrue(interop.hasMembers(value));
        Object members = interop.getMembers(value, false);
        InteropLibrary memberInterop = InteropLibrary.getUncached(members);

        assertTrue(memberInterop.hasArrayElements(members));
        assertEquals(2L, memberInterop.getArraySize(members));
        assertEquals("first", memberInterop.readArrayElement(members, 0));
        assertEquals("self", memberInterop.readArrayElement(members, 1));

        assertTrue(interop.isMemberReadable(value, "first"));
        assertFalse(interop.isMemberReadable(value, "delegated"));
        Object firstRead = interop.readMember(value, "first");
        assertSame(first, firstRead);
        assertTrue(InteropLibrary.isValidValue(firstRead));
        assertTrue(interop.isNumber(firstRead));
        assertEquals(BigInteger.ONE, interop.asBigInteger(firstRead));
        assertSame(value, interop.readMember(value, "self"));
        assertThrows(
                UnknownIdentifierException.class,
                () -> interop.readMember(value, "delegated"));

        value.createLocalSlot("later", Long.valueOf(2L));
        assertEquals(2L, memberInterop.getArraySize(members));

        Object freshMembers = interop.getMembers(value, false);
        InteropLibrary freshInterop = InteropLibrary.getUncached(freshMembers);
        assertEquals(3L, freshInterop.getArraySize(freshMembers));
    }

    @Test
    void memberNameSnapshotPreservesOrderAndValidatesIndexes() throws Exception {
        ProtosObjectValue value = new ProtosObjectValue(ProtosObjectValue.rootObject());
        value.createLocalSlot("alpha", Long.valueOf(1L));
        value.createLocalSlot("beta", Long.valueOf(2L));
        value.createLocalSlot("gamma", Long.valueOf(3L));

        Object members = interop.getMembers(value, false);
        InteropLibrary memberInterop = InteropLibrary.getUncached(members);
        assertEquals(3L, memberInterop.getArraySize(members));
        assertEquals("alpha", memberInterop.readArrayElement(members, 0));
        assertEquals("beta", memberInterop.readArrayElement(members, 1));
        assertEquals("gamma", memberInterop.readArrayElement(members, 2));

        assertTrue(memberInterop.isArrayElementReadable(members, 0));
        assertTrue(memberInterop.isArrayElementReadable(members, 2));
        assertFalse(memberInterop.isArrayElementReadable(members, -1));
        assertFalse(memberInterop.isArrayElementReadable(members, 3));
        assertFalse(memberInterop.isArrayElementReadable(members, Long.MAX_VALUE));
        assertThrows(
                InvalidArrayIndexException.class,
                () -> memberInterop.readArrayElement(members, -1));
        assertThrows(
                InvalidArrayIndexException.class,
                () -> memberInterop.readArrayElement(members, 3));

        value.createLocalSlot("delta", Long.valueOf(4L));
        assertEquals(3L, memberInterop.getArraySize(members));
        assertFalse(memberInterop.isArrayElementReadable(members, 3));
        assertEquals("gamma", memberInterop.readArrayElement(members, 2));
    }

    @Test
    void inheritedRuntimeFamiliesDoNotAccidentallyReceiveObjectMemberProjection() {
        ProtosArrayValue array =
                new ProtosArrayValue(
                        ProtosObjectValue.rootObject(),
                        List.of(Long.valueOf(1L)));
        array.createLocalSlot(
                "host-visible-by-accident",
                Long.valueOf(2L));

        assertFalse(interop.hasMembers(array));
        assertThrows(
                UnsupportedMessageException.class,
                () -> interop.getMembers(array, false));
    }

    @Test
    void objectInteropIsReadOnlyAndRejectsHostOnlySlotValues() throws Exception {
        ProtosObjectValue value = new ProtosObjectValue(ProtosObjectValue.rootObject());
        Long answer = Long.valueOf(42);
        value.createLocalSlot("answer", answer);
        value.createLocalSlot("hostOnly", BigInteger.valueOf(99));

        assertTrue(interop.isMemberReadable(value, "answer"));
        assertFalse(interop.isMemberReadable(value, "hostOnly"));
        assertThrows(
                UnknownIdentifierException.class,
                () -> interop.readMember(value, "hostOnly"));

        Long replacement = Long.valueOf(0L);
        assertFalse(interop.isMemberModifiable(value, "answer"));
        assertFalse(interop.isMemberInsertable(value, "newSlot"));
        assertFalse(interop.isMemberRemovable(value, "answer"));
        assertThrows(
                UnsupportedMessageException.class,
                () -> interop.writeMember(value, "answer", replacement));

        assertEquals("Object", interop.toDisplayString(value, true));
        assertSame(answer, value.readLocalSlot("answer").orElseThrow());
    }

    /*
     * D262-A guest reflective createSlot/updateSlot enter the existing
     * structural authority (createLocalSlot/assignLocalSlot); the host member
     * projection of such slots is unchanged and stays read-only.
     */
    @Test
    void structurallyCreatedAndUpdatedSlotsKeepReadOnlyMemberProjection() throws Exception {
        ProtosObjectValue value = new ProtosObjectValue(ProtosObjectValue.rootObject());
        Long created = Long.valueOf(1L);
        Long updated = Long.valueOf(2L);
        value.createLocalSlot("", created);
        value.createLocalSlot("a b", created);
        value.assignLocalSlot("a b", updated);

        Object members = interop.getMembers(value, false);
        InteropLibrary memberInterop = InteropLibrary.getUncached(members);
        assertEquals(2L, memberInterop.getArraySize(members));
        assertEquals("", memberInterop.readArrayElement(members, 0));
        assertEquals("a b", memberInterop.readArrayElement(members, 1));

        assertTrue(interop.isMemberReadable(value, "a b"));
        assertSame(updated, interop.readMember(value, "a b"));
        assertFalse(interop.isMemberModifiable(value, "a b"));
        assertFalse(interop.isMemberInsertable(value, "c"));
        assertFalse(interop.isMemberRemovable(value, "a b"));
        assertThrows(
                UnsupportedMessageException.class,
                () -> interop.writeMember(value, "a b", created));
        assertThrows(
                UnsupportedMessageException.class,
                () -> interop.removeMember(value, "a b"));
        assertSame(updated, value.readLocalSlot("a b").orElseThrow());
    }
}
