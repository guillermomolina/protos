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
        ProtosIntegerValue first = new ProtosIntegerValue(BigInteger.ONE);
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

        value.createLocalSlot("later", new ProtosIntegerValue(BigInteger.TWO));
        assertEquals(2L, memberInterop.getArraySize(members));

        Object freshMembers = interop.getMembers(value, false);
        InteropLibrary freshInterop = InteropLibrary.getUncached(freshMembers);
        assertEquals(3L, freshInterop.getArraySize(freshMembers));
    }

    @Test
    void inheritedRuntimeFamiliesDoNotAccidentallyReceiveObjectMemberProjection() {
        ProtosArrayValue array =
                new ProtosArrayValue(
                        ProtosObjectValue.rootObject(),
                        List.of(new ProtosIntegerValue(BigInteger.ONE)));
        array.createLocalSlot(
                "host-visible-by-accident",
                new ProtosIntegerValue(BigInteger.TWO));

        assertFalse(interop.hasMembers(array));
        assertThrows(
                UnsupportedMessageException.class,
                () -> interop.getMembers(array, false));
    }

    @Test
    void objectInteropIsReadOnlyAndRejectsHostOnlySlotValues() throws Exception {
        ProtosObjectValue value = new ProtosObjectValue(ProtosObjectValue.rootObject());
        ProtosIntegerValue answer = new ProtosIntegerValue(BigInteger.valueOf(42));
        value.createLocalSlot("answer", answer);
        value.createLocalSlot("hostOnly", BigInteger.valueOf(99));

        assertTrue(interop.isMemberReadable(value, "answer"));
        assertFalse(interop.isMemberReadable(value, "hostOnly"));
        assertThrows(
                UnknownIdentifierException.class,
                () -> interop.readMember(value, "hostOnly"));

        ProtosIntegerValue replacement = new ProtosIntegerValue(BigInteger.ZERO);
        assertFalse(interop.isMemberModifiable(value, "answer"));
        assertFalse(interop.isMemberInsertable(value, "newSlot"));
        assertFalse(interop.isMemberRemovable(value, "answer"));
        assertThrows(
                UnsupportedMessageException.class,
                () -> interop.writeMember(value, "answer", replacement));

        assertEquals("Object", interop.toDisplayString(value, true));
        assertSame(answer, value.readLocalSlot("answer").orElseThrow());
    }
}
