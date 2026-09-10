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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ProtosSimpleScalarInteropTest {
    private final InteropLibrary interop = InteropLibrary.getUncached();

    @Test
    void stringExportsOnlyItsExactStringFacet() throws Exception {
        ProtosStringValue value = new ProtosStringValue("héllo 🐇");

        assertTrue(InteropLibrary.isValidValue(value));
        assertTrue(interop.isString(value));
        assertEquals("héllo 🐇", interop.asString(value));
        assertFalse(interop.isBoolean(value));
        assertFalse(interop.isNull(value));
        assertFalse(interop.isNumber(value));
        assertFalse(interop.hasMembers(value));
        assertFalse(interop.hasArrayElements(value));
        assertFalse(interop.isExecutable(value));
    }

    @Test
    void booleanExportsOnlyItsExactBooleanFacet() throws Exception {
        assertTrue(InteropLibrary.isValidValue(ProtosBooleanValue.TRUE));
        assertTrue(interop.isBoolean(ProtosBooleanValue.TRUE));
        assertTrue(interop.asBoolean(ProtosBooleanValue.TRUE));
        assertFalse(interop.asBoolean(ProtosBooleanValue.FALSE));
        assertFalse(interop.isString(ProtosBooleanValue.TRUE));
        assertFalse(interop.isNull(ProtosBooleanValue.TRUE));
        assertFalse(interop.isNumber(ProtosBooleanValue.TRUE));
        assertFalse(interop.hasMembers(ProtosBooleanValue.TRUE));
        assertFalse(interop.isExecutable(ProtosBooleanValue.TRUE));
    }

    @Test
    void nullExportsOnlyTheNullFacet() {
        ProtosNullValue value = ProtosNullValue.INSTANCE;

        assertTrue(InteropLibrary.isValidValue(value));
        assertTrue(interop.isNull(value));
        assertFalse(interop.isBoolean(value));
        assertFalse(interop.isString(value));
        assertFalse(interop.isNumber(value));
        assertFalse(interop.hasMembers(value));
        assertFalse(interop.hasArrayElements(value));
        assertFalse(interop.isExecutable(value));
    }

    @Test
    void ordinaryObjectMemberReadsPreserveScalarGuestValuesAndExposeTheirFacets()
            throws Exception {
        ProtosObjectValue object =
                new ProtosObjectValue(ProtosObjectValue.rootObject());
        ProtosStringValue text = new ProtosStringValue("value");
        object.createLocalSlot("text", text);
        object.createLocalSlot("flag", ProtosBooleanValue.TRUE);
        object.createLocalSlot("nothing", ProtosNullValue.INSTANCE);

        Object readText = interop.readMember(object, "text");
        Object readFlag = interop.readMember(object, "flag");
        Object readNull = interop.readMember(object, "nothing");

        assertSame(text, readText);
        assertSame(ProtosBooleanValue.TRUE, readFlag);
        assertSame(ProtosNullValue.INSTANCE, readNull);

        assertEquals("value", interop.asString(readText));
        assertTrue(interop.asBoolean(readFlag));
        assertTrue(interop.isNull(readNull));
    }
}
