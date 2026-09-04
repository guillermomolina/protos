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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ProtosObjectValueTest {
    @Test
    void objectRootIsUniqueAndHasNoParent() {
        ProtosObjectValue root = ProtosObjectValue.rootObject();

        assertSame(root, ProtosObjectValue.rootObject());
        assertTrue(root.isRootObject());
        assertTrue(root.parent().isEmpty());
    }

    @Test
    void everyConstructedObjectHasExactlyOneFixedParent() {
        ProtosObjectValue root = ProtosObjectValue.rootObject();
        ProtosObjectValue parent = new ProtosObjectValue(root);
        ProtosObjectValue child = new ProtosObjectValue(parent);

        assertSame(parent, child.parent().orElseThrow());
        assertFalse(child.isRootObject());
    }

    @Test
    void readsDelegateToNearestSlot() {
        ProtosObjectValue root = ProtosObjectValue.rootObject();
        ProtosObjectValue parent = new ProtosObjectValue(root);
        ProtosObjectValue child = new ProtosObjectValue(parent);

        parent.createLocalSlot("name", new ProtosStringValue("parent"));
        assertEquals(
                "parent",
                ((ProtosStringValue) child.readSlot("name").orElseThrow()).value());

        child.createLocalSlot("name", new ProtosStringValue("child"));
        assertEquals(
                "child",
                ((ProtosStringValue) child.readSlot("name").orElseThrow()).value());
    }

    @Test
    void writesOperateOnlyOnLocalSlots() {
        ProtosObjectValue root = ProtosObjectValue.rootObject();
        ProtosObjectValue parent = new ProtosObjectValue(root);
        ProtosObjectValue child = new ProtosObjectValue(parent);

        parent.createLocalSlot("alive", ProtosBooleanValue.TRUE);

        assertThrows(
                IllegalStateException.class,
                () -> child.assignLocalSlot("alive", ProtosBooleanValue.FALSE));

        child.createLocalSlot("alive", ProtosBooleanValue.FALSE);
        child.assignLocalSlot("alive", ProtosBooleanValue.TRUE);

        assertSame(ProtosBooleanValue.TRUE, child.readLocalSlot("alive").orElseThrow());
        assertSame(ProtosBooleanValue.TRUE, parent.readLocalSlot("alive").orElseThrow());
    }

    @Test
    void duplicateLocalCreationAndMissingLookupAreDistinctFailures() {
        ProtosObjectValue object = new ProtosObjectValue(ProtosObjectValue.rootObject());
        object.createLocalSlot("x", ProtosNullValue.INSTANCE);

        assertThrows(
                IllegalStateException.class,
                () -> object.createLocalSlot("x", ProtosNullValue.INSTANCE));
        assertTrue(object.readSlot("missing").isEmpty());
    }
}
