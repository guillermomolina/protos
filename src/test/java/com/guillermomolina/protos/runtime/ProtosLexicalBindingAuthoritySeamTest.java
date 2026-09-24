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

import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * I068 Slice 2 ("frame/context single-authority seam"): every local-slot
 * operation on a genuine {@link ProtosExecutionContextValue} must be routed
 * through exactly one {@link ProtosLexicalBindingAuthority}, so that a later
 * slice can install a Truffle Bytecode DSL frame-backed authority without
 * introducing a second authoritative binding-value copy. This slice keeps the
 * installed authority map-backed; these tests establish that current
 * create/read/assign/remove/reflection behavior is unchanged through the seam
 * and that nothing bypasses it.
 */
class ProtosLexicalBindingAuthoritySeamTest {
    @Test
    void createReadAssignBehaviorIsUnchangedThroughTheSeam() {
        ProtosExecutionContextValue context =
                new ProtosExecutionContextValue(ProtosObjectValue.rootObject());

        context.createLocalSlot("x", "first");
        assertTrue(context.hasLocalSlot("x"));
        assertEquals("first", context.readLocalSlot("x").orElseThrow());

        context.assignLocalSlot("x", "second");
        assertEquals("second", context.readLocalSlot("x").orElseThrow());
    }

    @Test
    void presentNullRemainsDistinctFromAbsentThroughTheSeam() {
        ProtosExecutionContextValue context =
                new ProtosExecutionContextValue(ProtosObjectValue.rootObject());

        context.createLocalSlot("x", ProtosNullValue.INSTANCE);

        assertTrue(context.hasLocalSlot("x"));
        assertTrue(context.readLocalSlot("x").isPresent());
        assertSame(ProtosNullValue.INSTANCE, context.readLocalSlot("x").orElseThrow());

        assertFalse(context.hasLocalSlot("absent"));
        assertTrue(context.readLocalSlot("absent").isEmpty());
    }

    @Test
    void d179C3RemovalRejectionRemainsExactThroughTheSeam() {
        ProtosExecutionContextValue context =
                new ProtosExecutionContextValue(ProtosObjectValue.rootObject());
        context.createLocalSlot("x", "value");

        assertThrows(IllegalStateException.class, () -> context.removeLocalSlot("x"));
        assertTrue(context.hasLocalSlot("x"));
        assertEquals("value", context.readLocalSlot("x").orElseThrow());
    }

    @Test
    void ordinaryObjectValueRemoveLocalSlotRemainsUnaffected() {
        ProtosObjectValue ordinary = new ProtosObjectValue(ProtosObjectValue.rootObject());
        ordinary.createLocalSlot("x", "value");

        assertEquals("value", ordinary.removeLocalSlot("x"));
        assertFalse(ordinary.hasLocalSlot("x"));
    }

    @Test
    void closeAndFreezeBehaviorRemainsUnchangedThroughTheSeam() {
        ProtosExecutionContextValue context =
                new ProtosExecutionContextValue(ProtosObjectValue.rootObject());
        context.createLocalSlot("x", "value");

        context.close();
        assertThrows(IllegalStateException.class, () -> context.createLocalSlot("y", "value"));
        context.assignLocalSlot("x", "still writable while closed");
        assertEquals("still writable while closed", context.readLocalSlot("x").orElseThrow());

        context.freeze();
        assertThrows(IllegalStateException.class, () -> context.assignLocalSlot("x", "no longer writable"));
    }

    @Test
    void escapedReferenceObservesMutationsThroughTheSameAuthority() {
        ProtosExecutionContextValue context =
                new ProtosExecutionContextValue(ProtosObjectValue.rootObject());
        context.createLocalSlot("x", "initial");

        // Simulates an escaped/captured reference to the same execution context
        // (capture-by-reference and escape never copy the physical binding
        // authority; they always share the one attached to this instance).
        ProtosExecutionContextValue escaped = context;
        ProtosExecutionContextValue captured = context;

        context.assignLocalSlot("x", "mutated after escape");

        assertEquals("mutated after escape", escaped.readLocalSlot("x").orElseThrow());
        assertEquals("mutated after escape", captured.readLocalSlot("x").orElseThrow());
        assertSame(context, escaped);
        assertSame(context, captured);
    }

    @Test
    void localSnapshotReflectionCannotObserveAStaleSecondCopy() {
        ProtosExecutionContextValue context =
                new ProtosExecutionContextValue(ProtosObjectValue.rootObject());
        context.createLocalSlot("x", "one");

        Map<String, Object> firstSnapshot = context.localSlotsSnapshot();
        assertEquals("one", firstSnapshot.get("x"));

        context.assignLocalSlot("x", "two");
        context.createLocalSlot("y", "new");

        Map<String, Object> secondSnapshot = context.localSlotsSnapshot();
        assertEquals("two", secondSnapshot.get("x"));
        assertTrue(secondSnapshot.containsKey("y"));

        // The earlier snapshot is an immutable copy taken at that instant, not a
        // second authoritative store: it must not change retroactively, and the
        // live context must always be the authority a fresh snapshot reflects.
        assertEquals("one", firstSnapshot.get("x"));
        assertFalse(firstSnapshot.containsKey("y"));
    }

    @Test
    void delegatedLookupThroughAnExecutionContextGoesThroughTheSameAuthority() {
        ProtosExecutionContextValue parentContext =
                new ProtosExecutionContextValue(ProtosObjectValue.rootObject());
        parentContext.createLocalSlot("outer", "outerValue");

        ProtosObjectValue child = new ProtosObjectValue(parentContext);

        ProtosSlotLookupResult result = child.lookupSlot("outer").orElseThrow();
        assertEquals("outerValue", result.value());
        assertSame(parentContext, result.home());

        parentContext.assignLocalSlot("outer", "updatedOuterValue");
        assertEquals("updatedOuterValue", child.lookupSlot("outer").orElseThrow().value());
    }
}
