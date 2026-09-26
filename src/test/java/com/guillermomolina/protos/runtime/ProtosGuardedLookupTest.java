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
