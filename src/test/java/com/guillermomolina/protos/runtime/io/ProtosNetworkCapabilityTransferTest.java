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
package com.guillermomolina.protos.runtime.io;

import com.guillermomolina.protos.execution.ProtosParallelRuntime;
import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosActorValueTransfer;
import com.guillermomolina.protos.runtime.ProtosIntegerValue;
import com.guillermomolina.protos.runtime.ProtosNetworkCapabilityValue;
import com.guillermomolina.protos.runtime.ProtosNumericValueSupport;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosResolverCapabilityValue;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.execution.ProtosCoreBootstrap;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.IdentityHashMap;
import org.junit.jupiter.api.Test;

final class ProtosNetworkCapabilityTransferTest {
    private static final Path CORE = Path.of("protos", "lib", "core");

    @Test
    void representedCapabilityRetainsCanonicalPrototypeAndOpaqueHostAuthority() throws Exception {
        ProtosPrelude prelude = core();
        Object authority = new Object();
        ProtosNetworkCapabilityValue capability =
                new ProtosNetworkCapabilityValue(prelude, authority);

        assertSame(prelude.networkPrototype(), capability.representedDelegationParent(prelude));
        assertSame(authority, capability.authorityTargetForRuntime());
        assertSame(ProtosObjectValue.rootObject(), prelude.networkPrototype().parent().orElseThrow());
        assertTrue(prelude.networkPrototype().isFrozen());

        ProtosNetworkCapabilityValue second =
                new ProtosNetworkCapabilityValue(prelude, authority);
        assertNotSame(capability, second);
    }

    @Test
    void actorRejectsNetworkAuthorityAndAuthorityBearingDescendants() throws Exception {
        ProtosPrelude prelude = core();
        ProtosActivation source = prelude.newModuleActivation();
        ProtosNetworkCapabilityValue capability =
                new ProtosNetworkCapabilityValue(prelude, new Object());
        ProtosObjectValue descendant = new ProtosObjectValue(capability);

        assertNonTransferable(prelude, () -> ProtosActorValueTransfer.snapshotValue(capability, source));
        assertNonTransferable(prelude, () -> ProtosActorValueTransfer.snapshotValue(descendant, source));
    }

    @Test
    void pRejectsNetworkAuthorityAndAuthorityBearingDescendants() throws Exception {
        ProtosPrelude prelude = core();
        ProtosActivation activation = prelude.newModuleActivation();
        ProtosNetworkCapabilityValue capability =
                new ProtosNetworkCapabilityValue(prelude, new Object());
        ProtosObjectValue descendant = new ProtosObjectValue(capability);
        Method copy = pCopyMethod();

        assertNonParallel(copy, capability, activation);
        assertNonParallel(copy, descendant, activation);
    }

    @Test
    void ordinaryChildOfNetworkPrototypeRemainsAuthorityFreeTransferableData() throws Exception {
        ProtosPrelude prelude = core();
        ProtosActivation activation = prelude.newModuleActivation();
        ProtosObjectValue ordinary = new ProtosObjectValue(prelude.networkPrototype());
        ordinary.createLocalSlot("payload", new ProtosIntegerValue(7));
        ordinary.freeze();

        ProtosObjectValue actorCopy =
                assertInstanceOf(
                        ProtosObjectValue.class,
                        ProtosActorValueTransfer.snapshotValue(ordinary, activation));
        assertNotSame(ordinary, actorCopy);
        assertSame(prelude.networkPrototype(), actorCopy.parent().orElseThrow());
        assertEquals(Long.toString(7), com.guillermomolina.protos.runtime.ProtosNumericValueSupport.integerDecimalText(actorCopy.readLocalSlot("payload").orElseThrow()));
        assertTrue(actorCopy.isFrozen());

        Object copied =
                pCopyMethod()
                        .invoke(
                                null,
                                ordinary,
                                activation,
                                new IdentityHashMap<Object, Object>());
        ProtosObjectValue pCopy = assertInstanceOf(ProtosObjectValue.class, copied);
        assertNotSame(ordinary, pCopy);
        assertSame(prelude.networkPrototype(), pCopy.parent().orElseThrow());
        assertTrue(pCopy.isFrozen());
    }

    @Test
    void actorAndPRejectResolverAuthorityButTransferResolvedFrozenAddresses() throws Exception {
        ProtosPrelude prelude = core();
        ProtosActivation activation = prelude.newModuleActivation();
        ProtosResolverCapabilityValue resolver =
                new ProtosResolverCapabilityValue(prelude, new InertResolverBackend());
        ProtosObjectValue descendant = new ProtosObjectValue(resolver);
        ProtosObjectValue holder = new ProtosObjectValue(ProtosObjectValue.rootObject());
        holder.createLocalSlot("resolver", resolver);

        assertSame(prelude.resolverPrototypeForRuntime(), resolver.representedDelegationParent(prelude));
        assertNonTransferable(prelude, () -> ProtosActorValueTransfer.snapshotValue(resolver, activation));
        assertNonTransferable(prelude, () -> ProtosActorValueTransfer.snapshotValue(descendant, activation));
        assertNonTransferable(prelude, () -> ProtosActorValueTransfer.snapshotValue(holder, activation));
        Method copy = pCopyMethod();
        assertNonParallel(copy, resolver, activation);
        assertNonParallel(copy, descendant, activation);

        // A resolved address is ordinary frozen IpAddress data.
        ProtosObjectValue address = new ProtosObjectValue(prelude.ipAddressPrototypeForRuntime());
        address.createLocalSlot("version", ProtosNumericValueSupport.integer(4));
        address.createLocalSlot("bits", new ProtosIntegerValue(0xC0000201L));
        address.freeze();
        ProtosObjectValue actorCopy =
                assertInstanceOf(
                        ProtosObjectValue.class,
                        ProtosActorValueTransfer.snapshotValue(address, activation));
        assertSame(prelude.ipAddressPrototypeForRuntime(), actorCopy.parent().orElseThrow());
        assertTrue(actorCopy.isFrozen());
        ProtosObjectValue pCopy =
                assertInstanceOf(
                        ProtosObjectValue.class,
                        copy.invoke(null, address, activation, new IdentityHashMap<Object, Object>()));
        assertTrue(pCopy.isFrozen());
    }

    private static final class InertResolverBackend implements ProtosResolverBackend {
        @Override
        public Cancellation lookup(String absoluteName, Completion completion) {
            throw new AssertionError("transfer must never consult the backend");
        }

        @Override
        public void close() {}
    }

    private static void assertNonTransferable(ProtosPrelude prelude, ThrowingAction action) {
        ProtosSignalException failure = assertThrows(ProtosSignalException.class, action::run);
        assertSame(
                prelude.bindings().readLocalSlot("NonTransferableValue").orElseThrow(),
                failure.error().parent().orElseThrow());
    }

    private static void assertNonParallel(
            Method copy, Object value, ProtosActivation activation) {
        InvocationTargetException failure =
                assertThrows(
                        InvocationTargetException.class,
                        () ->
                                copy.invoke(
                                        null,
                                        value,
                                        activation,
                                        new IdentityHashMap<Object, Object>()));
        assertNotNull(failure.getCause());
        assertEquals("NonParallel", failure.getCause().getClass().getSimpleName());
    }

    private static Method pCopyMethod() throws Exception {
        Class<?> transfer =
                Class.forName(
                        "com.guillermomolina.protos.execution.ProtosParallelRuntime$Transfer");
        Method copy =
                transfer.getDeclaredMethod(
                        "copy", Object.class, ProtosActivation.class, IdentityHashMap.class);
        copy.setAccessible(true);
        return copy;
    }

    private static ProtosPrelude core() throws Exception {
        return new ProtosCoreBootstrap().bootstrap(CORE);
    }

    @FunctionalInterface
    private interface ThrowingAction {
        Object run();
    }
}
