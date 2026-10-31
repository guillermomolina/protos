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

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ProtosLexicalBindingNamesTest {
    private static List<String> names(ProtosObjectValue object) {
        var names = new ArrayList<String>();
        object.appendLocalBindingNamesTo(names);
        return List.copyOf(names);
    }

    @Test
    void mapBackedNamesFollowPresenceAndEstablishmentOrder() {
        ProtosObjectValue object =
                new ProtosObjectValue(ProtosObjectValue.rootObject());

        assertEquals(List.of(), names(object));

        object.createLocalSlot("café", ProtosNullValue.INSTANCE);
        object.createLocalSlot("π", ProtosNullValue.INSTANCE);
        assertEquals(List.of("café", "π"), names(object));

        object.assignLocalSlot("café", ProtosNullValue.INSTANCE);
        assertEquals(List.of("café", "π"), names(object));

        object.removeLocalSlot("café");
        assertEquals(List.of("π"), names(object));

        object.createLocalSlot("café", ProtosNullValue.INSTANCE);
        assertEquals(List.of("π", "café"), names(object));

        object.removeLocalSlot("π");
        object.removeLocalSlot("café");
        assertEquals(List.of(), names(object));
    }

    @Test
    void emptyAuthorityDoesNotMaterializeStorage() {
        ProtosObjectValue object =
                new ProtosObjectValue(ProtosObjectValue.rootObject());

        var names = new ArrayList<String>();
        object.appendLocalBindingNamesTo(names);
        object.appendLocalBindingNamesTo(names);

        assertTrue(names.isEmpty());
        assertTrue(object.localSlotsSnapshot().isEmpty());
    }
}
