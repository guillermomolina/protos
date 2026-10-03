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
