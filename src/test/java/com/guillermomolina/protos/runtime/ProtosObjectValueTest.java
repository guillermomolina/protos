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

import java.util.List;
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
    void anyProtosValueMayBeADelegationParent() {
        ProtosObjectValue stringChild = new ProtosObjectValue(new ProtosStringValue("parent"));
        ProtosObjectValue numberChild =
                new ProtosObjectValue(new ProtosIntegerValue(java.math.BigInteger.valueOf(42)));
        ProtosObjectValue booleanChild = new ProtosObjectValue(ProtosBooleanValue.TRUE);
        ProtosObjectValue nullChild = new ProtosObjectValue(ProtosNullValue.INSTANCE);

        assertTrue(stringChild.parent().orElseThrow() instanceof ProtosStringValue);
        assertTrue(numberChild.parent().orElseThrow() instanceof ProtosIntegerValue);
        assertSame(ProtosBooleanValue.TRUE, booleanChild.parent().orElseThrow());
        assertSame(ProtosNullValue.INSTANCE, nullChild.parent().orElseThrow());
    }

    @Test
    void delegatedLookupThroughUnbootstrappedValueParentIsNotSilentlyMisresolved() {
        ProtosObjectValue child = new ProtosObjectValue(new ProtosStringValue("parent"));

        assertThrows(UnsupportedOperationException.class, () -> child.readSlot("size"));
    }

    @Test
    void objectsStartOpenAndCloseAndFreezeAreIdempotent() {
        ProtosObjectValue object = new ProtosObjectValue(ProtosObjectValue.rootObject());

        assertTrue(object.isOpen());

        assertSame(object, object.close());
        assertTrue(object.isClosed());
        assertSame(object, object.close());
        assertTrue(object.isClosed());

        assertSame(object, object.freeze());
        assertTrue(object.isFrozen());
        assertSame(object, object.freeze());
        assertTrue(object.isFrozen());
        assertSame(object, object.close());
        assertTrue(object.isFrozen());
    }

    @Test
    void closedObjectsRejectStructuralCreationButAllowExistingSlotAssignment() {
        ProtosObjectValue object = new ProtosObjectValue(ProtosObjectValue.rootObject());
        object.createLocalSlot("existing", ProtosBooleanValue.TRUE);
        object.close();

        assertThrows(
                IllegalStateException.class,
                () -> object.createLocalSlot("newSlot", ProtosBooleanValue.TRUE));

        object.assignLocalSlot("existing", ProtosBooleanValue.FALSE);
        assertSame(ProtosBooleanValue.FALSE, object.readLocalSlot("existing").orElseThrow());
    }

    @Test
    void frozenObjectsRejectCreationAndAssignment() {
        ProtosObjectValue object = new ProtosObjectValue(ProtosObjectValue.rootObject());
        object.createLocalSlot("existing", ProtosBooleanValue.TRUE);
        object.freeze();

        assertThrows(
                IllegalStateException.class,
                () -> object.createLocalSlot("newSlot", ProtosBooleanValue.TRUE));
        assertThrows(
                IllegalStateException.class,
                () -> object.assignLocalSlot("existing", ProtosBooleanValue.FALSE));
        assertSame(ProtosBooleanValue.TRUE, object.readLocalSlot("existing").orElseThrow());
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

    @Test
    void openObjectRemovesOnlyLocalSlotAndReturnsExactValue() {
        ProtosObjectValue root = ProtosObjectValue.rootObject();
        ProtosObjectValue parent = new ProtosObjectValue(root);
        ProtosObjectValue child = new ProtosObjectValue(parent);
        Object inherited = new Object();
        Object local = new Object();

        parent.createLocalSlot("name", inherited);
        child.createLocalSlot("name", local);

        assertSame(local, child.removeLocalSlot("name"));
        assertSame(inherited, child.readSlot("name").orElseThrow());
        assertFalse(child.hasLocalSlot("name"));
    }

    @Test
    void removeSlotRejectsMissingClosedAndFrozenObjects() {
        ProtosObjectValue root = ProtosObjectValue.rootObject();

        ProtosObjectValue missing = new ProtosObjectValue(root);
        assertThrows(IllegalStateException.class, () -> missing.removeLocalSlot("x"));

        ProtosObjectValue closed = new ProtosObjectValue(root);
        closed.createLocalSlot("x", new Object());
        closed.close();
        assertThrows(IllegalStateException.class, () -> closed.removeLocalSlot("x"));
        assertTrue(closed.hasLocalSlot("x"));

        ProtosObjectValue frozen = new ProtosObjectValue(root);
        frozen.createLocalSlot("x", new Object());
        frozen.freeze();
        assertThrows(IllegalStateException.class, () -> frozen.removeLocalSlot("x"));
        assertTrue(frozen.hasLocalSlot("x"));
    }

    @Test
    void localSlotSnapshotIsDetachedReadOnlyAndPreservesExactBindings() {
        ProtosObjectValue object = new ProtosObjectValue(ProtosObjectValue.rootObject());
        Object first = new Object();
        Object second = new Object();

        object.createLocalSlot("first", first);
        object.createLocalSlot("second", second);

        java.util.Map<String, Object> snapshot = object.localSlotsSnapshot();

        assertEquals(List.of("first", "second"), new java.util.ArrayList<>(snapshot.keySet()));
        assertSame(first, snapshot.get("first"));
        assertSame(second, snapshot.get("second"));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.put("third", new Object()));

        object.removeLocalSlot("first");
        assertSame(first, snapshot.get("first"));
        assertFalse(object.hasLocalSlot("first"));
    }

    @Test
    void compositionViewsCopyBindingsWithoutMutatingReceiver() {
        ProtosObjectValue root = ProtosObjectValue.rootObject();
        ProtosObjectValue source = new ProtosObjectValue(root);
        Object move = new Object();
        Object state = new Object();

        source.createLocalSlot("move", move);
        source.createLocalSlot("state", state);

        ProtosObjectValue without =
                source.withoutLocalSlot("move", ProtosObjectValue.rootObject());
        assertFalse(without.hasLocalSlot("move"));
        assertSame(state, without.readLocalSlot("state").orElseThrow());
        assertTrue(source.hasLocalSlot("move"));

        ProtosObjectValue aliased =
                source.aliasLocalSlot(
                        "move",
                        "swimMove",
                        ProtosObjectValue.rootObject());
        assertSame(move, aliased.readLocalSlot("move").orElseThrow());
        assertSame(move, aliased.readLocalSlot("swimMove").orElseThrow());
        assertSame(state, aliased.readLocalSlot("state").orElseThrow());
        assertFalse(source.hasLocalSlot("swimMove"));
    }

    @Test
    void compositionViewsRejectNonLocalSourcesAndAliasConflicts() {
        ProtosObjectValue root = ProtosObjectValue.rootObject();
        ProtosObjectValue parent = new ProtosObjectValue(root);
        ProtosObjectValue source = new ProtosObjectValue(parent);

        parent.createLocalSlot("inherited", new Object());
        source.createLocalSlot("local", new Object());
        source.createLocalSlot("taken", new Object());

        assertThrows(
                IllegalStateException.class,
                () ->
                        source.withoutLocalSlot(
                                "inherited",
                                ProtosObjectValue.rootObject()));
        assertThrows(
                IllegalStateException.class,
                () ->
                        source.aliasLocalSlot(
                                "inherited",
                                "copy",
                                ProtosObjectValue.rootObject()));
        assertThrows(
                IllegalStateException.class,
                () ->
                        source.aliasLocalSlot(
                                "local",
                                "taken",
                                ProtosObjectValue.rootObject()));
    }

    @Test
    void compositionContributionIsAtomicAndHonorsReservations() {
        ProtosObjectValue root = ProtosObjectValue.rootObject();
        ProtosObjectValue target = new ProtosObjectValue(root);
        ProtosObjectValue source = new ProtosObjectValue(root);
        Object contributed = new Object();
        Object reserved = new Object();

        source.createLocalSlot("contributed", contributed);
        source.createLocalSlot("reserved", reserved);

        target.composeLocalSlotsFrom(source, java.util.Set.of("reserved"));

        assertSame(contributed, target.readLocalSlot("contributed").orElseThrow());
        assertFalse(target.hasLocalSlot("reserved"));
        assertSame(reserved, source.readLocalSlot("reserved").orElseThrow());

        ProtosObjectValue conflictingSource = new ProtosObjectValue(root);
        Object fresh = new Object();
        conflictingSource.createLocalSlot("fresh", fresh);
        conflictingSource.createLocalSlot("contributed", new Object());

        assertThrows(
                IllegalStateException.class,
                () -> target.composeLocalSlotsFrom(conflictingSource, java.util.Set.of()));
        assertFalse(target.hasLocalSlot("fresh"));
        assertSame(contributed, target.readLocalSlot("contributed").orElseThrow());
    }

    @Test
    void compositionContributionRejectsClosedAndFrozenTargetsWithoutPartialWrites() {
        ProtosObjectValue root = ProtosObjectValue.rootObject();
        ProtosObjectValue source = new ProtosObjectValue(root);
        source.createLocalSlot("x", new Object());

        ProtosObjectValue closed = new ProtosObjectValue(root);
        closed.close();
        assertThrows(
                IllegalStateException.class,
                () -> closed.composeLocalSlotsFrom(source, java.util.Set.of()));
        assertFalse(closed.hasLocalSlot("x"));

        ProtosObjectValue frozen = new ProtosObjectValue(root);
        frozen.freeze();
        assertThrows(
                IllegalStateException.class,
                () -> frozen.composeLocalSlotsFrom(source, java.util.Set.of()));
        assertFalse(frozen.hasLocalSlot("x"));
    }
}
