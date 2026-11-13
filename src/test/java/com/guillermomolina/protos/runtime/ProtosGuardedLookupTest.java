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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.execution.ProtosCoreBootstrap;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class ProtosGuardedLookupTest {
    private static ProtosObjectValue object() {
        return new ProtosObjectValue(ProtosObjectValue.rootObject());
    }

    private static Object read(
            Object receiver, ProtosValueLookup.StructuralSelection selection) {
        assertTrue(selection.isValid());
        return ProtosValueLookup.extractMember(receiver, selection.home(), selection.read());
    }

    private static ProtosValueLookup.StructuralSelection selected(ProtosObjectValue receiver) {
        var result = ProtosValueLookup.selectStructural(receiver, "pick");
        assertNotNull(result);
        assertTrue(result.isValid());
        var generic = ProtosValueLookup.lookup(receiver, "pick", null).orElseThrow();
        assertSame(generic.value(), result.read());
        assertSame(generic.home(), result.home());
        return result;
    }

    private static boolean matchesShared(
            Object receiver, String name, ProtosValueLookup.StructuralSelection selection) {
        return ProtosValueLookup.isOrdinaryObject(receiver)
                && !((ProtosObjectValue) receiver).hasLocalSlot(name)
                && ProtosValueLookup.hasSelectionParent((ProtosObjectValue) receiver, selection)
                && selection.isValid();
    }

    @Test
    void replacementPreservesSelectionsAndReadsEveryReceiverFresh() {
        var parent = object();
        var first = new ProtosObjectValue(parent);
        var second = new ProtosObjectValue(parent);
        Object oldValue = new Object();
        Object newValue = new Object();
        parent.createLocalSlot("pick", oldValue);
        var firstLookup = selected(first);
        var secondLookup = selected(second);

        parent.assignLocalSlot("pick", newValue);

        assertTrue(firstLookup.isValid());
        assertTrue(secondLookup.isValid());
        assertSame(newValue, read(first, firstLookup));
        assertSame(newValue, read(second, secondLookup));
        assertSame(newValue, selected(first).read());
    }

    @Test
    void sharedInheritedLookupTracksParentChainButNotSiblingShadows() {
        var parent = object();
        Object inherited = new Object();
        Object replacement = new Object();
        parent.createLocalSlot("pick", inherited);

        var first = new ProtosObjectValue(parent);
        var second = new ProtosObjectValue(parent);
        var third = new ProtosObjectValue(parent);

        var shared =
                ProtosValueLookup.selectInheritedStructural(first, "pick");
        assertNotNull(shared);
        assertSame(parent, shared.origin());
        assertSame(inherited, shared.read());
        assertSame(parent, shared.home());
        assertTrue(shared.isValid());

        assertTrue(
                matchesShared(
                        second,
                        "pick",
                        shared));
        assertTrue(
                matchesShared(
                        third,
                        "pick",
                        shared));

        second.createLocalSlot("pick", new Object());
        assertFalse(
                matchesShared(
                        second,
                        "pick",
                        shared));
        assertTrue(
                shared.isValid(),
                "a sibling-local shadow is deliberately not a parent-chain dependency");

        second.removeLocalSlot("pick");
        assertTrue(
                matchesShared(
                        second,
                        "pick",
                        shared));
        assertTrue(shared.isValid());

        parent.assignLocalSlot("pick", replacement);
        assertTrue(shared.isValid());
        assertSame(replacement, read(first, shared));
        assertSame(replacement, read(third, shared));

        var refreshed =
                ProtosValueLookup.selectInheritedStructural(third, "pick");
        assertNotNull(refreshed);
        assertSame(replacement, refreshed.read());
        assertSame(parent, refreshed.home());
    }

    @Test
    void sharedInheritedLookupInvalidatesForLateIntermediateShadow() {
        var ancestor = object();
        Object inherited = new Object();
        Object nearer = new Object();
        ancestor.createLocalSlot("pick", inherited);

        var parent = new ProtosObjectValue(ancestor);
        var first = new ProtosObjectValue(parent);
        var second = new ProtosObjectValue(parent);

        var shared =
                ProtosValueLookup.selectInheritedStructural(first, "pick");
        assertNotNull(shared);
        assertSame(parent, shared.origin());
        assertSame(ancestor, shared.home());
        assertSame(inherited, shared.read());

        parent.createLocalSlot("pick", nearer);
        assertFalse(shared.isValid());

        var refreshed =
                ProtosValueLookup.selectInheritedStructural(second, "pick");
        assertNotNull(refreshed);
        assertSame(parent, refreshed.home());
        assertSame(nearer, refreshed.read());
    }

    @Test
    void sharedInheritedLookupRejectsOwnSlotsAndSubclassChains() {
        var parent = object();
        parent.createLocalSlot("pick", new Object());

        var own = new ProtosObjectValue(parent);
        own.createLocalSlot("pick", new Object());
        assertNull(
                ProtosValueLookup.selectInheritedStructural(own, "pick"));

        var context =
                new ProtosExecutionContextValue(
                        ProtosObjectValue.rootObject());
        context.createLocalSlot("pick", new Object());
        assertNull(
                ProtosValueLookup.selectInheritedStructural(context, "pick"));

        var childOfContext = new ProtosObjectValue(context);
        assertNull(
                ProtosValueLookup.selectInheritedStructural(childOfContext, "pick"));
    }

    @Test
    void removalRejectsStaleHomeAndRevealsInheritedSelection() {
        var parent = object();
        var receiver = new ProtosObjectValue(parent);
        Object inherited = new Object();
        parent.createLocalSlot("pick", inherited);
        receiver.createLocalSlot("pick", new Object());
        var before = selected(receiver);
        assertSame(receiver, before.home());

        receiver.removeLocalSlot("pick");

        assertFalse(before.isValid());
        var after = selected(receiver);
        assertSame(parent, after.home());
        assertSame(inherited, after.read());
        parent.removeLocalSlot("pick");
        assertFalse(after.isValid());
        assertNull(ProtosValueLookup.selectStructural(receiver, "pick"));
    }

    @Test
    void nearerAdditionInvalidatesEvenWhenClosureIdentityIsUnchanged() {
        var parent = object();
        var middle = new ProtosObjectValue(parent);
        var receiver = new ProtosObjectValue(middle);
        var closure = ProtosClosureValue.nativeClosure(
                (activation, supplied) -> ProtosNullValue.INSTANCE);
        parent.createLocalSlot("pick", closure);
        var before = selected(receiver);

        middle.createLocalSlot("pick", closure);

        assertFalse(before.isValid());
        var after = selected(receiver);
        assertSame(closure, after.read());
        assertSame(middle, after.home());
    }

    @Test
    void observedStructuralChangesInvalidateButIndependentChangesDoNot() {
        var parent = object();
        var receiver = new ProtosObjectValue(parent);
        parent.createLocalSlot("pick", new Object());
        var lookup = selected(receiver);
        object().createLocalSlot("pick", new Object());
        assertTrue(lookup.isValid());
        receiver.createLocalSlot("other", new Object());
        assertFalse(lookup.isValid());
        lookup = selected(receiver);
        receiver.assignLocalSlot("other", new Object());
        assertTrue(lookup.isValid());
        receiver.removeLocalSlot("other");
        assertFalse(lookup.isValid());
        lookup = selected(receiver);
        parent.createLocalSlot("other", new Object());
        assertFalse(lookup.isValid());
    }

    @Test
    void successfulCompositionInvalidatesOnlyContributedSelectors() {
        var parent = object();
        var receiver = new ProtosObjectValue(parent);
        var source = object();
        parent.createLocalSlot("pick", new Object());
        source.createLocalSlot("pick", new Object());
        var before = selected(receiver);

        receiver.composeLocalSlotsFrom(source, Set.of("pick"));
        assertTrue(before.isValid());

        receiver.composeLocalSlotsFrom(source, Set.of());
        assertFalse(before.isValid());
        assertSame(receiver, selected(receiver).home());
    }

    @Test
    void failedCompositionAndFrozenMutationPreserveSelection() {
        var receiver = object();
        var source = object();
        receiver.createLocalSlot("pick", new Object());
        source.createLocalSlot("pick", new Object());
        var lookup = selected(receiver);

        assertThrows(IllegalStateException.class,
                () -> receiver.composeLocalSlotsFrom(source, Set.of()));
        assertTrue(lookup.isValid());
        receiver.close();
        assertThrows(IllegalStateException.class,
                () -> receiver.removeLocalSlot("pick"));
        receiver.freeze();
        assertThrows(IllegalStateException.class,
                () -> receiver.assignLocalSlot("pick", new Object()));
        assertTrue(lookup.isValid());
        assertTrue(selected(receiver).isValid());
    }

    @Test
    void contextStorageRemainsOnGenericLookup() {
        var context = new ProtosExecutionContextValue(ProtosObjectValue.rootObject());
        Object value = new Object();
        context.createLocalSlot("pick", value);
        assertNull(ProtosValueLookup.selectStructural(context, "pick"));
        assertSame(value,
                ProtosValueLookup.lookup(context, "pick", null).orElseThrow().value());

        var child = new ProtosObjectValue(context);
        assertNull(ProtosValueLookup.selectStructural(child, "pick"));
    }

    @Test
    void representedParentChangesRemainVisibleThroughGenericFallback() {
        var first = object();
        var second = object();
        first.createLocalSlot("pick", new Object());
        second.createLocalSlot("pick", new Object());
        var represented = new MutableRepresentedValue(first);
        var receiver = new ProtosObjectValue(represented);

        assertNull(ProtosValueLookup.selectStructural(receiver, "pick"));
        assertSame(first,
                ProtosValueLookup.lookup(receiver, "pick", null).orElseThrow().home());
        represented.parent = second;
        assertNull(ProtosValueLookup.selectStructural(receiver, "pick"));
        assertSame(second,
                ProtosValueLookup.lookup(receiver, "pick", null).orElseThrow().home());
    }

    @Test
    void booleanCallbacksUseGenericPreludeLookup() throws IOException {
        var prelude = corePrelude();
        for (Object receiver : new Object[] {Boolean.TRUE, Boolean.FALSE}) {
            assertFalse(ProtosValueLookup.isOrdinaryObject(receiver));
            for (String name : List.of("and", "or")) {
                assertNull(ProtosValueLookup.selectStructural(receiver, name));
                var generic = ProtosValueLookup.lookup(receiver, name, prelude).orElseThrow();
                assertSame(ProtosObjectValue.rootObject(), generic.home());
                assertInstanceOf(ProtosClosureValue.class, generic.value());
            }
        }
    }

    @Test
    void rootStructuralSelectionInvalidatesOnNewSlot() {
        var root = object();
        root.createLocalSlot("pick", new Object());
        try {
            var selection = selected(root);
            root.createLocalSlot("other", new Object());
            assertFalse(selection.isValid());
            assertTrue(selected(root).isValid());
        } finally {
            root.removeLocalSlot("other");
            root.removeLocalSlot("pick");
        }
    }

    @Test
    void representedValuesAreNotOrdinaryObjects() {
        for (Object receiver : new Object[] {
                Boolean.TRUE, Boolean.FALSE, 1L, 1.5d,
                new MutableRepresentedValue(ProtosObjectValue.rootObject())}) {
            assertFalse(ProtosValueLookup.isOrdinaryObject(receiver));
            assertNull(ProtosValueLookup.selectStructural(receiver, "pick"));
        }
        assertTrue(ProtosValueLookup.isOrdinaryObject(object()));
    }

    @Test
    void slotSelectionSurvivesRepeatedValueReplacement() {
        var holder = object();
        Object initial = new Object();
        holder.createLocalSlot("pick", initial);

        var selection =
                ProtosValueLookup.selectStructural(holder, "pick");
        var secondSelection = selected(holder);
        assertNotNull(selection);
        assertSame(holder, selection.home());

        for (int index = 0; index < 200; index++) {
            Object replacement = new Object();
            holder.assignLocalSlot("pick", replacement);
            assertTrue(selection.isValid());
            assertSame(
                    replacement,
                    read(holder, selection));
        }

        assertTrue(secondSelection.isValid());
        assertSame(selection.read(), secondSelection.read());
    }

    /*
     * PERF037-B: a selection's stable location is the binding's single
     * authoritative store. It must stay invisible on every read surface,
     * keep insertion order, follow assignment, and never survive removal.
     */
    @Test
    void stableSlotLocationIsTheSingleInvisibleAuthority() {
        var holder = object();
        holder.createLocalSlot("a", "A");
        holder.createLocalSlot("pick", "first");
        holder.createLocalSlot("c", "C");

        var selection =
                ProtosValueLookup.selectStructural(holder, "pick");
        assertNotNull(selection);
        assertSame(holder.getShape().getProperty("pick"),
                selection.home().getShape().getProperty("pick"));
        assertEquals(List.of("a", "pick", "c"), List.copyOf(holder.localSlotsSnapshot().keySet()));
        assertEquals("first", holder.localSlotsSnapshot().get("pick"));
        assertEquals("first", holder.readLocalSlot("pick").orElseThrow());

        holder.assignLocalSlot("pick", "second");
        assertTrue(selection.isValid());
        assertEquals("second", read(holder, selection));
        var names = new java.util.ArrayList<String>();
        var values = new java.util.ArrayList<Object>();
        holder.appendLocalBindingsTo(names, values);
        assertEquals(List.of("a", "pick", "c"), names);
        assertEquals(List.of("A", "second", "C"), values);

        var copy = holder.withoutLocalSlot("a");
        assertEquals("second", copy.readLocalSlot("pick").orElseThrow());
        copy.assignLocalSlot("pick", "copy");
        assertEquals("second", read(holder, selection));

        assertEquals("second", holder.removeLocalSlot("pick"));
        assertFalse(selection.isValid());
        holder.createLocalSlot("pick", "recreated");
        assertEquals(List.of("a", "c", "pick"), List.copyOf(holder.localSlotsSnapshot().keySet()));
        var recreated =
                ProtosValueLookup.selectStructural(holder, "pick");
        assertNotNull(recreated);
        assertTrue(recreated.isValid());
        assertFalse(selection.isValid());
        assertEquals("recreated", read(holder, recreated));
    }

    @Test
    void stableSlotLocationFollowsDataAndClosureValues() {
        var receiver = object();
        var closure = ProtosClosureValue.nativeClosure(
                (activation, supplied) -> ProtosNullValue.INSTANCE);
        receiver.createLocalSlot("pick", ProtosNullValue.INSTANCE);
        var selection =
                ProtosValueLookup.selectStructural(receiver, "pick");
        assertNotNull(selection);
        assertSame(ProtosNullValue.INSTANCE, read(receiver, selection));

        receiver.assignLocalSlot("pick", closure);
        var first = (ProtosClosureValue) read(receiver, selection);
        var second = (ProtosClosureValue) read(receiver, selection);
        assertFalse(first == second);
        assertFalse(first == closure);
        assertSame(receiver, first.capturedReceiver());
        assertSame(receiver, first.methodHome().orElseThrow());
        assertSame(receiver, second.methodHome().orElseThrow());

        Object data = new Object();
        receiver.assignLocalSlot("pick", data);
        assertTrue(selection.isValid());
        assertSame(data, read(receiver, selection));
    }

    @Test
    void replacementKeepsIndependentSelectionsAndFreshBinding() {
        var first = object();
        var second = object();

        Object originalFirst = new Object();
        Object originalSecond = new Object();

        first.createLocalSlot("pick", originalFirst);
        second.createLocalSlot("pick", originalSecond);

        var firstSelection =
                ProtosValueLookup.selectStructural(first, "pick");
        var secondSelection =
                ProtosValueLookup.selectStructural(second, "pick");

        assertNotNull(firstSelection);
        assertNotNull(secondSelection);

        for (int index = 0; index < 20; index++) {
            Object replacement = new Object();
            first.assignLocalSlot("pick", replacement);
            assertSame(replacement, read(first, firstSelection));
            assertTrue(firstSelection.isValid());
            assertTrue(secondSelection.isValid());
            assertSame(originalSecond, read(second, secondSelection));
        }

        ProtosClosureValue closure =
                ProtosClosureValue.nativeClosure(
                        (activation, supplied) -> ProtosNullValue.INSTANCE);

        first.assignLocalSlot("pick", closure);

        assertTrue(firstSelection.isValid());

        ProtosClosureValue bound =
                assertInstanceOf(
                        ProtosClosureValue.class,
                        read(first, firstSelection));
        assertSame(first, bound.capturedReceiver());
        assertSame(first, bound.methodHome().orElseThrow());
        assertTrue(secondSelection.isValid());
        assertSame(originalSecond, read(second, secondSelection));

        Object restored = new Object();
        first.assignLocalSlot("pick", restored);

        assertSame(restored, read(first, firstSelection));
        assertSame(originalSecond, read(second, secondSelection));
    }

    @Test
    void emptyObjectsNeverAllocateSlotStorageForGuardedReads() {
        var empty = object();
        assertNull(ProtosValueLookup.selectStructural(empty, "pick"));
        assertFalse(empty.hasLocalBindingsForRuntime());
        assertTrue(empty.localSlotsSnapshot().isEmpty());
    }

    @Test
    void inheritedSelectionKeepsItsHomeAndIsolatesSiblings() {
        var ancestor = object();
        var parent = new ProtosObjectValue(ancestor);
        var first = new ProtosObjectValue(parent);
        var second = new ProtosObjectValue(parent);
        ancestor.createLocalSlot("pick", new Object());

        var selection =
                ProtosValueLookup.selectInheritedStructural(first, "pick");
        assertNotNull(selection);
        assertSame(ancestor, selection.home());
        assertTrue(
                matchesShared(
                        second, "pick", selection));

        Object updated = new Object();
        ancestor.assignLocalSlot("pick", updated);
        assertTrue(selection.isValid());
        assertSame(
                updated,
                read(second, selection));

        second.createLocalSlot("pick", new Object());
        assertTrue(selection.isValid());
        assertFalse(
                matchesShared(
                        second, "pick", selection));
        assertTrue(
                matchesShared(
                        first, "pick", selection));

        second.removeLocalSlot("pick");
        assertTrue(
                matchesShared(
                        second, "pick", selection));

        Object nearer = new Object();
        parent.createLocalSlot("pick", nearer);
        assertFalse(selection.isValid());

        var refreshed =
                ProtosValueLookup.selectInheritedStructural(first, "pick");
        assertNotNull(refreshed);
        assertSame(parent, refreshed.home());
        assertSame(
                nearer,
                read(first, refreshed));
    }

    @Test
    void localRemovalAndCompositionInvalidateSelection() {
        var parent = object();
        var receiver = new ProtosObjectValue(parent);
        parent.createLocalSlot("pick", new Object());
        receiver.createLocalSlot("pick", new Object());

        var local = ProtosValueLookup.selectStructural(receiver, "pick");
        assertNotNull(local);
        receiver.removeLocalSlot("pick");
        assertFalse(local.isValid());

        var inherited = ProtosValueLookup.selectStructural(receiver, "pick");
        assertNotNull(inherited);
        assertSame(parent, inherited.home());

        var source = object();
        source.createLocalSlot("pick", new Object());
        receiver.composeLocalSlotsFrom(source, Set.of());
        assertFalse(inherited.isValid());

        var composed = ProtosValueLookup.selectStructural(receiver, "pick");
        assertNotNull(composed);
        assertSame(receiver, composed.home());
    }

    @Test
    void sharedInheritedSelectionHandlesClosureAndShadowing() {
        var parent = object();
        var first = new ProtosObjectValue(parent);
        var second = new ProtosObjectValue(parent);

        Object originalValue = new Object();
        parent.createLocalSlot("pick", originalValue);

        var selection =
                ProtosValueLookup.selectInheritedStructural(first, "pick");
        assertNotNull(selection);

        assertSame(originalValue, read(first, selection));

        Object replacement = new Object();
        parent.assignLocalSlot("pick", replacement);

        assertTrue(selection.isValid());
        assertSame(replacement, read(first, selection));
        assertSame(replacement, read(second, selection));

        ProtosClosureValue closure =
                ProtosClosureValue.nativeClosure(
                        (activation, supplied) -> ProtosNullValue.INSTANCE);

        parent.assignLocalSlot("pick", closure);

        assertTrue(selection.isValid());

        ProtosClosureValue extractedA =
                assertInstanceOf(
                        ProtosClosureValue.class,
                        read(first, selection));
        ProtosClosureValue extractedB =
                assertInstanceOf(
                        ProtosClosureValue.class,
                        read(first, selection));

        assertFalse(extractedA == extractedB);
        assertSame(first, extractedA.capturedReceiver());
        assertSame(parent, extractedA.methodHome().orElseThrow());

        second.createLocalSlot("pick", new Object());

        assertFalse(
                matchesShared(
                        second, "pick", selection));

        assertTrue(
                matchesShared(
                        first, "pick", selection));

        Object restored = new Object();
        parent.assignLocalSlot("pick", restored);

        assertSame(restored, read(first, selection));
    }

    @Test
    void selectedClosureIsReadFreshAndBoundToOriginalReceiver() {
        var parent = object();
        var receiver = new ProtosObjectValue(parent);
        var first = ProtosClosureValue.nativeClosure(
                (activation, supplied) -> ProtosNullValue.INSTANCE);
        var replacement = ProtosClosureValue.nativeClosure(
                (activation, supplied) -> Boolean.TRUE);
        parent.createLocalSlot("pick", first);

        var selection = ProtosValueLookup.selectStructural(receiver, "pick");
        assertNotNull(selection);
        assertSame(parent, selection.home());

        var extractedA = (ProtosClosureValue)
                read(receiver, selection);
        var extractedB = (ProtosClosureValue)
                read(receiver, selection);
        assertFalse(extractedA == extractedB);
        assertSame(receiver, extractedA.capturedReceiver());
        assertSame(parent, extractedA.methodHome().orElseThrow());

        parent.assignLocalSlot("pick", replacement);
        assertTrue(selection.isValid());

        var extractedC = (ProtosClosureValue)
                read(receiver, selection);
        assertSame(receiver, extractedC.capturedReceiver());
        assertSame(parent, extractedC.methodHome().orElseThrow());
        assertSame(
                replacement.nativeBody().orElseThrow(),
                extractedC.nativeBody().orElseThrow());
        assertFalse(
                extractedA.nativeBody().orElseThrow()
                        == extractedC.nativeBody().orElseThrow());
    }

    @Test
    void closedAssignmentPreservesSelectionAndFrozenAssignmentFails() {
        var receiver = object();
        receiver.createLocalSlot("pick", new Object());
        var selection =
                ProtosValueLookup.selectStructural(receiver, "pick");
        assertNotNull(selection);

        receiver.close();
        Object updated = new Object();
        receiver.assignLocalSlot("pick", updated);
        assertTrue(selection.isValid());
        assertSame(
                updated,
                read(receiver, selection));

        receiver.freeze();
        assertThrows(IllegalStateException.class,
                () -> receiver.assignLocalSlot("pick", new Object()));
        assertTrue(selection.isValid());

        var context =
                new ProtosExecutionContextValue(ProtosObjectValue.rootObject());
        context.createLocalSlot("pick", new Object());
        assertNull(ProtosValueLookup.selectStructural(context, "pick"));
    }

    private static ProtosPrelude corePrelude() throws IOException {
        return new ProtosCoreBootstrap().bootstrap(Path.of("protos", "lib", "core"));
    }

    private static Long integer(long value) {
        return Long.valueOf(value);
    }

    @Test
    void integerFamiliesUseGenericLookupWithTheirRealPrototypes() throws IOException {
        var prelude = corePrelude();
        Object small = integer(10);
        Object big = ProtosNumericValueSupport.integerFromHost(BigInteger.TWO.pow(70), prelude);
        assertSame(prelude.smallIntegerPrototype(),
                ProtosValueLookup.delegationParent(small, prelude).orElseThrow());
        assertSame(prelude.bigIntegerPrototype(),
                ProtosValueLookup.delegationParent(big, prelude).orElseThrow());
        for (String selector : List.of("-", ">")) {
            var fromSmall = ProtosValueLookup.lookup(small, selector, prelude).orElseThrow();
            var fromBig = ProtosValueLookup.lookup(big, selector, prelude).orElseThrow();
            var home = selector.equals("-") ? prelude.integerPrototype() : prelude.numberPrototype();
            assertSame(home, fromSmall.home());
            assertSame(home, fromBig.home());
            assertSame(fromSmall.value(), fromBig.value());
            assertInstanceOf(ProtosClosureValue.class, fromSmall.value());
            for (Object receiver : new Object[] {small, big, 9L, 0L, -7L}) {
                assertFalse(ProtosValueLookup.isOrdinaryObject(receiver));
                assertNull(ProtosValueLookup.selectStructural(receiver, selector));
                var generic = ProtosValueLookup.lookup(receiver, selector, prelude).orElseThrow();
                assertSame(home, generic.home());
                assertSame(fromSmall.value(), generic.value());
            }
        }
        assertNotSame(
                ProtosValueLookup.lookup(small, "recognizes", prelude).orElseThrow().home(),
                ProtosValueLookup.lookup(big, "recognizes", prelude).orElseThrow().home());
        assertTrue(ProtosValueLookup.lookup(small, "perf016Absent", prelude).isEmpty());
        assertTrue(prelude.integerPrototype().isFrozen());
        assertTrue(prelude.smallIntegerPrototype().isFrozen());
        assertTrue(prelude.bigIntegerPrototype().isFrozen());
        assertTrue(prelude.numberPrototype().isFrozen());
        assertThrows(IllegalStateException.class,
                () -> prelude.numberPrototype().createLocalSlot(">", new Object()));
    }

    @Test
    void representedLargeIntegerRetainsItsOwnPreludeDelegation() throws IOException {
        var prelude = corePrelude();
        var other = corePrelude();
        Object foreign = ProtosNumericValueSupport.integerFromHost(BigInteger.TWO.pow(70), other);
        assertFalse(ProtosValueLookup.isOrdinaryObject(foreign));
        assertNull(ProtosValueLookup.selectStructural(foreign, "-"));
        assertSame(other.bigIntegerPrototype(),
                ProtosValueLookup.delegationParent(foreign, prelude).orElseThrow());
        assertSame(other.integerPrototype(),
                ProtosValueLookup.lookup(foreign, "-", prelude).orElseThrow().home());
        Object custom = ProtosNumericValueSupport.integerWithPrototype(
                BigInteger.TWO.pow(70), object());
        assertNull(ProtosValueLookup.selectStructural(custom, "-"));
        assertTrue(ProtosValueLookup.lookup(custom, "-", prelude).isEmpty());
    }

    private static final class MutableRepresentedValue implements ProtosRepresentedValue {
        private Object parent;

        MutableRepresentedValue(Object parent) {
            this.parent = parent;
        }

        @Override
        public Object representedDelegationParent(ProtosPrelude prelude) {
            return parent;
        }
    }
}
