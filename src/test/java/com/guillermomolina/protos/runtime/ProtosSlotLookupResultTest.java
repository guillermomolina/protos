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

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ProtosSlotLookupResultTest {
    @Test
    void delegatedLookupReturnsValueAndPhysicalHome() {
        ProtosObjectValue root = ProtosObjectValue.rootObject();
        ProtosObjectValue animal = new ProtosObjectValue(root);
        ProtosObjectValue dog = new ProtosObjectValue(animal);
        ProtosObjectValue rex = new ProtosObjectValue(dog);

        Object value = new ProtosStringValue("speak");
        animal.createLocalSlot("speak", value);

        ProtosSlotLookupResult result = rex.lookupSlot("speak").orElseThrow();

        assertSame(value, result.value());
        assertSame(animal, result.home());
    }

    @Test
    void nearestLocalSlotDefinesHome() {
        ProtosObjectValue root = ProtosObjectValue.rootObject();
        ProtosObjectValue parent = new ProtosObjectValue(root);
        ProtosObjectValue child = new ProtosObjectValue(parent);

        Object parentValue = new ProtosStringValue("parent");
        Object childValue = new ProtosStringValue("child");
        parent.createLocalSlot("name", parentValue);
        child.createLocalSlot("name", childValue);

        ProtosSlotLookupResult result = child.lookupSlot("name").orElseThrow();

        assertSame(childValue, result.value());
        assertSame(child, result.home());
    }

    @Test
    void missingLookupReturnsEmptyWithoutFabricatingHome() {
        ProtosObjectValue root = ProtosObjectValue.rootObject();
        ProtosObjectValue child = new ProtosObjectValue(root);

        assertTrue(child.lookupSlot("missing").isEmpty());
    }
}
