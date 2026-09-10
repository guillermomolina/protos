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

final class ProtosSimpleScalarInteropDisplayTest {
    private final InteropLibrary interop = InteropLibrary.getUncached();

    @Test
    void stringDisplayUsesTheExistingGuestPayloadWithoutHostIdentity() {
        ProtosStringValue value = new ProtosStringValue("héllo 🐇");

        String display = String.valueOf(interop.toDisplayString(value, false));

        assertEquals("héllo 🐇", display);
        assertFalse(display.contains("ProtosStringValue"));
        assertFalse(display.contains("@"));
    }

    @Test
    void booleanDisplayIsCanonicalAndHostOpaque() {
        String trueDisplay =
                String.valueOf(interop.toDisplayString(ProtosBooleanValue.TRUE, false));
        String falseDisplay =
                String.valueOf(interop.toDisplayString(ProtosBooleanValue.FALSE, false));

        assertEquals("true", trueDisplay);
        assertEquals("false", falseDisplay);
        assertFalse(trueDisplay.contains("ProtosBooleanValue"));
        assertFalse(falseDisplay.contains("ProtosBooleanValue"));
    }

    @Test
    void nullDisplayIsCanonicalAndHostOpaque() {
        String display =
                String.valueOf(interop.toDisplayString(ProtosNullValue.INSTANCE, false));

        assertEquals("null", display);
        assertFalse(display.contains("ProtosNullValue"));
        assertFalse(display.contains("@"));
    }

    @Test
    void displayDoesNotChangePreviouslyPublishedScalarFacets() throws Exception {
        ProtosStringValue string = new ProtosStringValue("x");

        assertEquals("x", interop.asString(string));
        assertEquals("x", interop.toDisplayString(string, true));
        assertEquals(true, interop.asBoolean(ProtosBooleanValue.TRUE));
        assertEquals("true", interop.toDisplayString(ProtosBooleanValue.TRUE, true));
        assertEquals(true, interop.isNull(ProtosNullValue.INSTANCE));
        assertEquals("null", interop.toDisplayString(ProtosNullValue.INSTANCE, true));
    }
}
