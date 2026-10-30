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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * PERF025 structural evidence for the ordinary object-model pay-as-you-grow
 * representation. These assertions verify physical representation invariants;
 * they do not measure performance.
 */
final class ProtosPerf025ObjectModelPayAsYouGrowTest {
    @Test
    void emptyOrdinaryObjectsShareStorageFreeAuthorityUntilFirstBinding()
            throws Exception {
        ProtosObjectValue first =
                new ProtosObjectValue(ProtosObjectValue.rootObject());
        ProtosObjectValue second =
                new ProtosObjectValue(ProtosObjectValue.rootObject());

        Object firstEmpty = lexicalAuthority(first);
        Object secondEmpty = lexicalAuthority(second);

        assertSame(firstEmpty, secondEmpty);
        assertFalse(first.hasLocalBindingsForRuntime());
        assertFalse(second.hasLocalBindingsForRuntime());
        assertTrue(first.localSlotsSnapshot().isEmpty());

        Object value = new Object();
        first.createLocalSlot("value", value);

        Object promoted = lexicalAuthority(first);
        ProtosMapBackedLexicalBindingAuthority mapAuthority =
                assertInstanceOf(
                        ProtosMapBackedLexicalBindingAuthority.class,
                        promoted);
        assertNotSame(firstEmpty, promoted);
        assertSame(secondEmpty, lexicalAuthority(second));
        assertTrue(first.hasLocalBindingsForRuntime());
        assertFalse(second.hasLocalBindingsForRuntime());
        assertSame(value, first.readLocalSlot("value").orElseThrow());
        assertTrue(privateField(mapAuthority, "bindings") instanceof java.util.LinkedHashMap);

        assertSame(value, first.removeLocalSlot("value"));
        assertFalse(first.hasLocalBindingsForRuntime());
        assertNull(
                privateField(mapAuthority, "bindings"),
                "empty map-backed authority releases its optional backing map");
    }

    /*
     * PERF037-B: only a binding a guarded member-read PIC selects is promoted
     * to a stable location, in place, inside the same single map.
     */
    @Test
    void bindingsStayUncelledUntilAGuardedReadSelectsThem() throws Exception {
        ProtosObjectValue object =
                new ProtosObjectValue(ProtosObjectValue.rootObject());
        Object unread = new Object();
        Object selected = new Object();
        object.createLocalSlot("unread", unread);
        object.createLocalSlot("selected", selected);

        java.util.Map<?, ?> bindings =
                (java.util.Map<?, ?>) privateField(lexicalAuthority(object), "bindings");
        assertNull(bindings.get("unread"));
        assertNull(bindings.get("selected"));
        assertSame(unread, object.readLocalSlot("unread").orElseThrow());
        assertSame(selected, object.readLocalSlot("selected").orElseThrow());

        var guarded =
                ProtosValueLookup.lookupGuardedSlotSelection(
                        object, "selected", null);
        assertTrue(guarded != null);
        assertTrue(
                guarded.nonClosureContinuity() != null
                        && guarded.nonClosureContinuity().isValid());
        assertSame(bindings, privateField(lexicalAuthority(object), "bindings"));
        assertNull(bindings.get("unread"));
        assertInstanceOf(
                ProtosMapBackedLexicalBindingAuthority.SlotCell.class,
                bindings.get("selected"));
        assertEquals(List.of("unread", "selected"), List.copyOf(bindings.keySet()));
        assertSame(selected, object.readLocalSlot("selected").orElseThrow());
        assertSame(selected, object.localSlotsSnapshot().get("selected"));
    }


    /**
     * I115-H10-A: the first insertion must establish a genuine
     * primitive long Shape location, before any GetAttribute PIC
     * is selected. Reads and snapshots remain guest-facing.
     */

    private static long checkedLongLocation(
            ProtosValueLookup.GuardedSlotSelection selection,
            String phase) {
        var storage = selection.storage();
        var getter = selection.propertyGetter();
        var property = storage.getShape().getProperty(
                selection.propertyKey());

        Object raw = getter.get(storage);

        String type = raw == null
                ? "null"
                : raw.getClass().getName();

        String location = property == null
                ? "MISSING"
                : property.getLocation().getClass().getName();

        System.out.println(
                "I115_H10_LONG_PHASE=" + phase
                + " VALUE_TYPE=" + type
                + " LOCATION=" + location);

        try {
            return getter.getLong(storage);
        } catch (com.oracle.truffle.api.nodes.UnexpectedResultException e) {
            throw new AssertionError(
                    "I115_H10_LONG_FAILURE phase=" + phase
                    + " valueType=" + type
                    + " location=" + location,
                    e);
        }
    }

    @Test
    void i115LongStorageStartsPrimitiveAndSurvivesUpdates() throws Exception {
        ProtosObjectValue object =
                new ProtosObjectValue(ProtosObjectValue.rootObject());

        object.createLocalSlot(
                "value", ProtosNumericValueSupport.integer(1L));

        var selected = ProtosValueLookup.lookupGuardedSlotSelection(
                object, "value", null);

        assertNotNull(selected);
        assertEquals(
                1L,
                checkedLongLocation(selected, "INITIAL"));
        assertEquals(
                1L,
                assertInstanceOf(
                        ProtosIntegerValue.class,
                        object.readLocalSlot("value").orElseThrow())
                        .longValue());

        long wide = 1L << 42;
        object.assignLocalSlot(
                "value", ProtosNumericValueSupport.integer(wide));

        assertEquals(
                wide,
                checkedLongLocation(selected, "WIDE_UPDATE"));
        assertEquals(
                wide,
                assertInstanceOf(
                        ProtosIntegerValue.class,
                        object.localSlotsSnapshot().get("value"))
                        .longValue());

        object.assignLocalSlot(
                "value", ProtosNumericValueSupport.integer(Long.MIN_VALUE));

        assertEquals(
                Long.MIN_VALUE,
                checkedLongLocation(selected, "MIN_VALUE"));

        Object other = new Object();
        object.assignLocalSlot("value", other);
        assertSame(other, object.readLocalSlot("value").orElseThrow());

        assertSame(other, object.removeLocalSlot("value"));
        assertFalse(selected.stability().isValid());

        object.createLocalSlot(
                "value", ProtosNumericValueSupport.integer(Long.MAX_VALUE));

        var recreated = ProtosValueLookup.lookupGuardedSlotSelection(
                object, "value", null);

        assertNotNull(recreated);
        /*
         * A previously generalized Shape may retain ObjectLocation
         * after removal and recreation. The typed GetAttribute path
         * must then fall back to Object without truncating the value.
         */
        Object recreatedRaw =
                recreated.propertyGetter().get(recreated.storage());

        assertEquals(
                Long.MAX_VALUE,
                assertInstanceOf(Long.class, recreatedRaw).longValue());

        assertEquals(
                Long.MAX_VALUE,
                assertInstanceOf(
                        ProtosIntegerValue.class,
                        object.readLocalSlot("value").orElseThrow())
                        .longValue());

        System.out.println(
                "I115_H10_RECREATED_FALLBACK=PASS LOCATION="
                + recreated.storage().getShape()
                        .getProperty(recreated.propertyKey())
                        .getLocation().getClass().getName());
    }

    @Test
    void singleOrderedMapPreservesCreateAssignRemoveRecreateHistory() {
        ProtosObjectValue object =
                new ProtosObjectValue(ProtosObjectValue.rootObject());
        Object first = new Object();
        Object replacement = new Object();
        Object second = new Object();
        Object recreated = new Object();

        object.createLocalSlot("first", first);
        object.createLocalSlot("second", second);
        object.assignLocalSlot("first", replacement);

        assertEquals(
                List.of("first", "second"),
                List.copyOf(object.localSlotsSnapshot().keySet()));
        assertSame(
                replacement,
                object.localSlotsSnapshot().get("first"));

        assertSame(replacement, object.removeLocalSlot("first"));
        object.createLocalSlot("first", recreated);

        assertEquals(
                List.of("second", "first"),
                List.copyOf(object.localSlotsSnapshot().keySet()));
        assertSame(
                recreated,
                object.localSlotsSnapshot().get("first"));
    }

    @Test
    void directExecutionContextConstructionCanAttachDefinitiveAuthority()
            throws Exception {
        ProtosMapBackedLexicalBindingAuthority authority =
                new ProtosMapBackedLexicalBindingAuthority();
        ProtosExecutionContextValue context =
                new ProtosExecutionContextValue(
                        ProtosObjectValue.rootObject(),
                        authority);

        assertSame(authority, context.lexicalBindingAuthorityForRuntime());
        assertNull(privateField(authority, "bindings"));

        Object value = new Object();
        context.createLocalSlot("x", value);

        assertSame(authority, context.lexicalBindingAuthorityForRuntime());
        assertSame(value, context.readLocalSlot("x").orElseThrow());
    }

    @Test
    void emptyClosureBindingKeepsFreshIdentityWithoutLocalSlotStorage()
            throws Exception {
        ProtosClosureValue stored =
                ProtosClosureValue.nativeClosure(
                        (activation, supplied) -> ProtosNullValue.INSTANCE);
        ProtosObjectValue receiver =
                new ProtosObjectValue(ProtosObjectValue.rootObject());

        assertFalse(stored.hasLocalBindingsForRuntime());

        ProtosClosureValue first =
                stored.bindMethod(receiver, receiver);
        ProtosClosureValue second =
                stored.bindMethod(receiver, receiver);

        assertNotSame(stored, first);
        assertNotSame(first, second);
        assertSame(receiver, first.capturedReceiver());
        assertSame(receiver, second.capturedReceiver());
        assertSame(receiver, first.methodHome().orElseThrow());
        assertSame(receiver, second.methodHome().orElseThrow());
        assertFalse(first.hasLocalBindingsForRuntime());
        assertFalse(second.hasLocalBindingsForRuntime());
        assertSame(
                lexicalAuthority(first),
                lexicalAuthority(second),
                "empty bound Closures keep the shared storage-free authority");
    }

    /*
     * I115-A: local call presence is a derived bit of the map-backed
     * authority and must always agree with the authoritative hasLocalSlot.
     */
    @Test
    void localCallPresenceTracksCreateAssignRemoveRecreate() {
        ProtosClosureValue closure = nativeClosure();
        assertCallPresence(closure, false);

        closure.createLocalSlot("alpha", 1L);
        closure.createLocalSlot("beta", 2L);
        closure.createLocalSlot("gamma", 3L);
        assertCallPresence(closure, false);

        closure.createLocalSlot("call", 10L);
        assertCallPresence(closure, true);

        closure.assignLocalSlot("call", 11L);
        assertCallPresence(closure, true);
        closure.assignLocalSlot("call", nativeClosure());
        assertCallPresence(closure, true);
        closure.assignLocalSlot("call", 12L);
        assertCallPresence(closure, true);

        closure.removeLocalSlot("call");
        assertCallPresence(closure, false);
        assertTrue(closure.hasLocalSlot("alpha"));
        assertTrue(closure.hasLocalSlot("beta"));
        assertTrue(closure.hasLocalSlot("gamma"));

        closure.createLocalSlot("call", 13L);
        assertCallPresence(closure, true);

        closure.removeLocalSlot("alpha");
        closure.removeLocalSlot("gamma");
        assertCallPresence(closure, true);
        closure.removeLocalSlot("call");
        closure.removeLocalSlot("beta");
        assertCallPresence(closure, false);
    }

    @Test
    void localCallPresenceSurvivesSlotCellPromotionAndReplacement() {
        ProtosObjectValue object =
                new ProtosObjectValue(ProtosObjectValue.rootObject());
        object.createLocalSlot("call", 1L);
        assertNotNull(object.slotCellForGuardedRead("call"));
        assertCallPresence(object, true);
        object.assignLocalSlot("call", 2L);
        assertCallPresence(object, true);
        object.removeLocalSlot("call");
        assertCallPresence(object, false);
        object.createLocalSlot("call", 3L);
        assertCallPresence(object, true);
    }

    @Test
    void localCallPresenceFollowsCompositionAndReservedNames() {
        ProtosObjectValue withCall =
                new ProtosObjectValue(ProtosObjectValue.rootObject());
        withCall.createLocalSlot("alpha", 1L);
        withCall.createLocalSlot("call", 2L);
        ProtosObjectValue withoutCall =
                new ProtosObjectValue(ProtosObjectValue.rootObject());
        withoutCall.createLocalSlot("beta", 3L);

        ProtosClosureValue composedWithout = nativeClosure();
        composedWithout.composeLocalSlotsFrom(withoutCall, List.of());
        assertCallPresence(composedWithout, false);

        ProtosClosureValue composedWith = nativeClosure();
        composedWith.composeLocalSlotsFrom(withCall, List.of());
        assertCallPresence(composedWith, true);

        ProtosClosureValue composedReserved = nativeClosure();
        composedReserved.composeLocalSlotsFrom(withCall, List.of("call"));
        assertCallPresence(composedReserved, false);
        assertTrue(composedReserved.hasLocalSlot("alpha"));

        ProtosObjectValue without = withCall.withoutLocalSlot("call");
        assertCallPresence(without, false);
        assertCallPresence(withCall, true);
    }

    @Test
    void bindMethodCopiesLocalCallPresenceWithoutSharingStorage()
            throws Exception {
        ProtosClosureValue stored = nativeClosure();
        stored.createLocalSlot("call", 1L);
        stored.createLocalSlot("alpha", 2L);
        ProtosObjectValue receiver =
                new ProtosObjectValue(ProtosObjectValue.rootObject());

        ProtosClosureValue bound = stored.bindMethod(receiver, receiver);
        assertNotSame(stored, bound);
        assertNotSame(lexicalAuthority(stored), lexicalAuthority(bound));
        assertCallPresence(bound, true);

        bound.removeLocalSlot("call");
        assertCallPresence(bound, false);
        assertCallPresence(stored, true);

        ProtosClosureValue withoutCall = nativeClosure();
        withoutCall.createLocalSlot("alpha", 3L);
        assertCallPresence(withoutCall.bindMethod(receiver, receiver), false);
    }

    @Test
    void authorityTransferCarriesLocalCallPresenceOfTransferredBindings() {
        ProtosMapBackedLexicalBindingAuthority source =
                new ProtosMapBackedLexicalBindingAuthority();
        source.putBinding("alpha", 1L);
        source.putBinding("call", 2L);
        ProtosMapBackedLexicalBindingAuthority target =
                new ProtosMapBackedLexicalBindingAuthority();
        assertFalse(target.hasLocalCallBindingForRuntime());

        ProtosLexicalBindingAuthorityCalls.transferAll(source, target);
        assertTrue(target.hasLocalCallBindingForRuntime());
        assertTrue(target.containsBinding("call"));

        ProtosMapBackedLexicalBindingAuthority withoutCall =
                new ProtosMapBackedLexicalBindingAuthority();
        withoutCall.putBinding("alpha", 1L);
        ProtosMapBackedLexicalBindingAuthority otherTarget =
                new ProtosMapBackedLexicalBindingAuthority();
        ProtosLexicalBindingAuthorityCalls.transferAll(withoutCall, otherTarget);
        assertFalse(otherTarget.hasLocalCallBindingForRuntime());
        assertFalse(otherTarget.containsBinding("call"));
    }

    private static ProtosClosureValue nativeClosure() {
        return ProtosClosureValue.nativeClosure(
                (activation, supplied) -> ProtosNullValue.INSTANCE);
    }

    private static void assertCallPresence(
            ProtosObjectValue object, boolean expected) {
        assertEquals(expected, object.hasLocalCallSlotForRuntime());
        assertEquals(expected, object.hasLocalSlot("call"));
    }

    private static Object lexicalAuthority(ProtosObjectValue object)
            throws ReflectiveOperationException {
        Field field =
                ProtosObjectValue.class.getDeclaredField(
                        "lexicalBindingAuthority");
        field.setAccessible(true);
        return field.get(object);
    }

    private static Object privateField(Object target, String name)
            throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }
}
