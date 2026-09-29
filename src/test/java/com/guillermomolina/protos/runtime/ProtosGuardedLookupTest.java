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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import com.guillermomolina.protos.execution.ProtosCoreBootstrap;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class ProtosGuardedLookupTest {
    private static ProtosObjectValue object() {
        return new ProtosObjectValue(ProtosObjectValue.rootObject());
    }

    private static ProtosValueLookup.GuardedLookup selected(ProtosObjectValue receiver) {
        var result = ProtosValueLookup.lookupGuarded(receiver, "pick", null);
        assertNotNull(result);
        assertTrue(result.stability().isValid());
        var generic = ProtosValueLookup.lookup(receiver, "pick", null).orElseThrow();
        assertSame(generic.value(), result.selected().value());
        assertSame(generic.home(), result.selected().home());
        return result;
    }

    @Test
    void replacementInvalidatesEveryDependentReceiver() {
        var parent = object();
        var first = new ProtosObjectValue(parent);
        var second = new ProtosObjectValue(parent);
        Object oldValue = new Object();
        Object newValue = new Object();
        parent.createLocalSlot("pick", oldValue);
        var firstLookup = selected(first);
        var secondLookup = selected(second);

        parent.assignLocalSlot("pick", newValue);

        assertFalse(firstLookup.stability().isValid());
        assertFalse(secondLookup.stability().isValid());
        assertSame(oldValue, firstLookup.selected().value());
        assertSame(newValue, selected(first).selected().value());
    }

    @Test
    void removalRejectsStaleHomeAndRevealsInheritedSelection() {
        var parent = object();
        var receiver = new ProtosObjectValue(parent);
        Object inherited = new Object();
        parent.createLocalSlot("pick", inherited);
        receiver.createLocalSlot("pick", new Object());
        var before = selected(receiver);
        assertSame(receiver, before.selected().home());

        receiver.removeLocalSlot("pick");

        assertFalse(before.stability().isValid());
        var after = selected(receiver);
        assertSame(parent, after.selected().home());
        assertSame(inherited, after.selected().value());
        parent.removeLocalSlot("pick");
        assertFalse(after.stability().isValid());
        assertNull(ProtosValueLookup.lookupGuarded(receiver, "pick", null));
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

        assertFalse(before.stability().isValid());
        var after = selected(receiver);
        assertSame(closure, after.selected().value());
        assertSame(middle, after.selected().home());
    }

    @Test
    void unrelatedNamesAndObjectsDoNotInvalidate() {
        var parent = object();
        var receiver = new ProtosObjectValue(parent);
        parent.createLocalSlot("pick", new Object());
        var lookup = selected(receiver);

        receiver.createLocalSlot("other", new Object());
        receiver.assignLocalSlot("other", new Object());
        receiver.removeLocalSlot("other");
        parent.createLocalSlot("other", new Object());
        object().createLocalSlot("pick", new Object());

        assertTrue(lookup.stability().isValid());
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
        assertTrue(before.stability().isValid());

        receiver.composeLocalSlotsFrom(source, Set.of());
        assertFalse(before.stability().isValid());
        assertSame(receiver, selected(receiver).selected().home());
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
        assertTrue(lookup.stability().isValid());
        receiver.close();
        assertThrows(IllegalStateException.class,
                () -> receiver.removeLocalSlot("pick"));
        receiver.freeze();
        assertThrows(IllegalStateException.class,
                () -> receiver.assignLocalSlot("pick", new Object()));
        assertTrue(lookup.stability().isValid());
        assertTrue(selected(receiver).stability().isValid());
    }

    @Test
    void contextStorageRemainsOnGenericLookup() {
        var context = new ProtosExecutionContextValue(ProtosObjectValue.rootObject());
        Object value = new Object();
        context.createLocalSlot("pick", value);
        assertNull(ProtosValueLookup.lookupGuarded(context, "pick", null));
        assertSame(value,
                ProtosValueLookup.lookup(context, "pick", null).orElseThrow().value());

        var child = new ProtosObjectValue(context);
        assertNull(ProtosValueLookup.lookupGuarded(child, "pick", null));
    }

    @Test
    void representedParentChangesRemainVisibleThroughGenericFallback() {
        var first = object();
        var second = object();
        first.createLocalSlot("pick", new Object());
        second.createLocalSlot("pick", new Object());
        var represented = new MutableRepresentedValue(first);
        var receiver = new ProtosObjectValue(represented);

        assertNull(ProtosValueLookup.lookupGuarded(receiver, "pick", null));
        assertSame(first,
                ProtosValueLookup.lookup(receiver, "pick", null).orElseThrow().home());
        represented.parent = second;
        assertNull(ProtosValueLookup.lookupGuarded(receiver, "pick", null));
        assertSame(second,
                ProtosValueLookup.lookup(receiver, "pick", null).orElseThrow().home());
    }

    @Test
    void canonicalBooleanGuardedSelectionMatchesGenericLookup() {
        var root = ProtosObjectValue.rootObject();
        for (Object receiver : new Object[] {ProtosBooleanValue.TRUE, ProtosBooleanValue.FALSE}) {
            assertNull(ProtosValueLookup.lookupGuardedCanonicalBoolean(
                    receiver, "perf015Absent", null));
            // The ordinary guarded entry point stays generic for represented values.
            assertNull(ProtosValueLookup.lookupGuarded(receiver, "ifTrue", null));

            // Present only once Core bootstrap has installed the standard protocol.
            if (root.hasLocalSlot("ifTrue")) {
                var guarded = ProtosValueLookup.lookupGuardedCanonicalBoolean(
                        receiver, "ifTrue", null);
                assertNotNull(guarded);
                assertTrue(guarded.stability().isValid());
                var generic = ProtosValueLookup.lookup(receiver, "ifTrue", null).orElseThrow();
                assertSame(generic.value(), guarded.selected().value());
                assertSame(generic.home(), guarded.selected().home());
                assertSame(root, guarded.selected().home());
            }
        }
    }

    /**
     * Root Object is frozen by Core bootstrap, so this mutation evidence is
     * only reachable while it is still open (JVM-order dependent).
     */
    @Test
    void canonicalBooleanGuardedSelectionInvalidatesOnRootObjectMutation() {
        var root = ProtosObjectValue.rootObject();
        assumeFalse(root.isFrozen(), "root Object already frozen by Core bootstrap");
        for (Object receiver : new Object[] {ProtosBooleanValue.TRUE, ProtosBooleanValue.FALSE}) {
            Object first = new Object();
            Object second = new Object();
            root.createLocalSlot("perf015Probe", first);
            try {
                var guarded = ProtosValueLookup.lookupGuardedCanonicalBoolean(
                        receiver, "perf015Probe", null);
                assertNotNull(guarded);
                assertTrue(guarded.stability().isValid());
                assertSame(first, guarded.selected().value());
                root.assignLocalSlot("perf015Probe", second);
                assertFalse(guarded.stability().isValid());
            } finally {
                root.removeLocalSlot("perf015Probe");
            }
        }
    }

    @Test
    void canonicalBooleanGuardedSelectionRejectsOtherReceivers() {
        var represented = new MutableRepresentedValue(ProtosObjectValue.rootObject());
        assertNull(ProtosValueLookup.lookupGuardedCanonicalBoolean(
                represented, "perf015Probe", null));
        assertNull(ProtosValueLookup.lookupGuardedCanonicalBoolean(
                object(), "perf015Probe", null));
        assertFalse(ProtosValueLookup.isCanonicalBoolean(represented));
        assertTrue(ProtosValueLookup.isCanonicalBoolean(ProtosBooleanValue.TRUE));
        assertTrue(ProtosValueLookup.isCanonicalBoolean(ProtosBooleanValue.FALSE));
    }

    private static ProtosPrelude corePrelude() throws IOException {
        return new ProtosCoreBootstrap().bootstrap(Path.of("protos", "lib", "core"));
    }

    private static ProtosIntegerValue integer(long value) {
        return new ProtosIntegerValue(BigInteger.valueOf(value));
    }

    /** PERF016: admission is by representation family, never by value or identity. */
    @Test
    void integerFamilyAdmissionIsByRepresentationNotIdentity() throws IOException {
        var prelude = corePrelude();
        var first = integer(3);
        var second = integer(3);
        var huge = new ProtosIntegerValue(BigInteger.TWO.pow(200));
        for (Object receiver : new Object[] {first, second, huge}) {
            assertTrue(ProtosValueLookup.isInteger(receiver));
            assertNotNull(ProtosValueLookup.lookupGuardedInteger(receiver, "-", prelude));
        }
        assertFalse(ProtosValueLookup.isInteger(ProtosBooleanValue.TRUE));
        assertFalse(ProtosValueLookup.isInteger(new ProtosFloatValue(1.5d)));
        assertFalse(ProtosValueLookup.isInteger(
                new MutableRepresentedValue(prelude.integerPrototype())));
        assertFalse(ProtosValueLookup.isInteger(object()));
    }

    @Test
    void integerMinusSelectsIntegerPrototypeAndMatchesGenericLookup() throws IOException {
        var prelude = corePrelude();
        var guarded = ProtosValueLookup.lookupGuardedInteger(integer(10), "-", prelude);
        assertNotNull(guarded);
        assertTrue(guarded.stability().isValid());
        assertSame(prelude.integerPrototype(), guarded.selected().home());
        assertTrue(guarded.selected().value() instanceof ProtosClosureValue closure
                && closure.nativeBody().isPresent());

        // The selection made from one Integer is exactly the generic selection
        // for every other Integer, so it is reusable across fresh receivers.
        for (long value : new long[] {10, 9, 0, -7}) {
            var generic = ProtosValueLookup.lookup(integer(value), "-", prelude).orElseThrow();
            assertSame(generic.value(), guarded.selected().value());
            assertSame(generic.home(), guarded.selected().home());
        }
        assertTrue(guarded.stability().isValid());
    }

    @Test
    void integerGreaterSelectsNumberPrototypeAndMatchesGenericLookup() throws IOException {
        var prelude = corePrelude();
        var guarded = ProtosValueLookup.lookupGuardedInteger(integer(10), ">", prelude);
        assertNotNull(guarded);
        assertTrue(guarded.stability().isValid());
        assertSame(prelude.numberPrototype(), guarded.selected().home());
        assertTrue(guarded.selected().value() instanceof ProtosClosureValue closure
                && closure.nativeBody().isPresent());

        for (long value : new long[] {10, 9, 0, -7}) {
            var generic = ProtosValueLookup.lookup(integer(value), ">", prelude).orElseThrow();
            assertSame(generic.value(), guarded.selected().value());
            assertSame(generic.home(), guarded.selected().home());
        }
    }

    /**
     * The published standard graph is frozen by Core bootstrap, so selection
     * dependencies on Integer/Number/root Object cannot be mutated here; the
     * ordinary-chain invalidation contract itself is covered by the
     * lookupGuarded tests above, which share the same lookup loop.
     */
    @Test
    void integerGuardedSelectionDependsOnFrozenStandardGraph() throws IOException {
        var prelude = corePrelude();
        assertTrue(prelude.integerPrototype().isFrozen());
        assertTrue(prelude.numberPrototype().isFrozen());
        assertThrows(IllegalStateException.class,
                () -> prelude.numberPrototype().createLocalSlot(">", new Object()));
    }

    @Test
    void integerGuardedSelectionFallsBackForUnsupportedReceiversAndSelectors()
            throws IOException {
        var prelude = corePrelude();
        assertNull(ProtosValueLookup.lookupGuardedInteger(integer(1), "perf016Absent", prelude));
        assertNull(ProtosValueLookup.lookupGuardedInteger(integer(1), "-", null));
        assertNull(ProtosValueLookup.lookupGuardedInteger(new ProtosFloatValue(1.5d), "-", prelude));
        assertNull(ProtosValueLookup.lookupGuardedInteger(ProtosBooleanValue.TRUE, "-", prelude));
        assertNull(ProtosValueLookup.lookupGuardedInteger(object(), "-", prelude));
        assertNull(ProtosValueLookup.lookupGuardedInteger(
                new MutableRepresentedValue(prelude.integerPrototype()), "-", prelude));
        // The ordinary entry point and the Boolean entry point stay generic for Integer.
        assertNull(ProtosValueLookup.lookupGuarded(integer(1), "-", prelude));
        assertNull(ProtosValueLookup.lookupGuardedCanonicalBoolean(integer(1), "-", prelude));
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
