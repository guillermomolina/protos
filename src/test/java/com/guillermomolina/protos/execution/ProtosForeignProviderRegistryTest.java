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
package com.guillermomolina.protos.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosProcessRuntime;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ProtosForeignProviderRegistryTest {
    private static final ProtosForeignProviderId FAKE = new ProtosForeignProviderId("fake");

    @Test
    void normalRuntimeHostOwnsEmptyRegistry() {
        try (ProtosPolyglotRuntimeHost host = ProtosPolyglotRuntimeHost.open()) {
            assertSame(ProtosForeignProviderRegistry.EMPTY, host.foreignProvidersForRuntime());
            assertTrue(host.foreignProvidersForRuntime().isEmpty());
            assertTrue(host.foreignProvidersForRuntime().lookup(FAKE).isEmpty());
        }
    }

    @Test
    void registryIsDetachedFromItsConstructionInput() {
        AtomicInteger factoryCalls = new AtomicInteger();
        List<ProtosForeignProviderDescriptor> input = new ArrayList<>();
        input.add(descriptor(FAKE, factoryCalls));
        ProtosForeignProviderRegistry registry = ProtosForeignProviderRegistry.of(input);

        input.clear();
        input.add(descriptor(new ProtosForeignProviderId("other"), factoryCalls));

        assertEquals(1, registry.size());
        assertTrue(registry.lookup(FAKE).isPresent());
        assertTrue(registry.lookup(new ProtosForeignProviderId("other")).isEmpty());
        assertEquals(0, factoryCalls.get());
    }

    @Test
    void exactIdentityLookupReturnsRegisteredDescriptor() {
        AtomicInteger factoryCalls = new AtomicInteger();
        ProtosForeignProviderDescriptor fake = descriptor(FAKE, factoryCalls);
        ProtosForeignProviderRegistry registry = ProtosForeignProviderRegistry.of(List.of(fake));

        assertSame(fake, registry.lookup(new ProtosForeignProviderId("fake")).orElseThrow());
        assertTrue(registry.lookup(new ProtosForeignProviderId("Fake")).isEmpty());
        assertTrue(registry.lookup(new ProtosForeignProviderId("fake2")).isEmpty());
        assertEquals(0, factoryCalls.get());
    }

    @Test
    void duplicateIdentityIsRejectedDeterministically() {
        AtomicInteger factoryCalls = new AtomicInteger();
        IllegalArgumentException failure =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                ProtosForeignProviderRegistry.of(
                                        List.of(
                                                descriptor(FAKE, factoryCalls),
                                                descriptor(
                                                        new ProtosForeignProviderId("fake"),
                                                        factoryCalls))));
        assertEquals("duplicate foreign provider identity: fake", failure.getMessage());
        assertEquals(0, factoryCalls.get());
    }

    @Test
    void emptyDescriptorListYieldsSharedEmptyRegistry() {
        assertSame(ProtosForeignProviderRegistry.EMPTY, ProtosForeignProviderRegistry.of(List.of()));
    }

    @Test
    void providerIdentityRejectsBlankOrPaddedValues() {
        assertThrows(IllegalArgumentException.class, () -> new ProtosForeignProviderId(""));
        assertThrows(IllegalArgumentException.class, () -> new ProtosForeignProviderId(" fake"));
        assertThrows(NullPointerException.class, () -> new ProtosForeignProviderId(null));
    }

    @Test
    void hostingProcessesNeverInvokesProviderFactories() {
        AtomicInteger factoryCalls = new AtomicInteger();
        ProtosForeignProviderRegistry registry =
                ProtosForeignProviderRegistry.of(List.of(descriptor(FAKE, factoryCalls)));
        ProtosProcessRuntime process =
                new ProtosProcessRuntime(new ProtosObjectValue(ProtosObjectValue.rootObject()));

        try (ProtosPolyglotRuntimeHost host =
                ProtosPolyglotRuntimeHost.openWithForeignProvidersForTesting(registry)) {
            assertSame(registry, host.foreignProvidersForRuntime());
            ProtosPolyglotProcessContext context =
                    host.hostProcess(
                            process,
                            InputStream.nullInputStream(),
                            OutputStream.nullOutputStream(),
                            OutputStream.nullOutputStream());
            assertSame(host.engineForTesting(), context.engineForTesting());
            assertEquals(1, host.activeProcessContextCountForTesting());

            process.requestTerminationForRuntime();

            assertTrue(context.isClosedForTesting());
            assertEquals(0, host.activeProcessContextCountForTesting());
            assertFalse(host.actorCarrierSubstrateInitializedForTesting());
            assertFalse(host.networkHostInitializedForTesting());
        }
        assertEquals(0, factoryCalls.get());
    }

    private static ProtosForeignProviderDescriptor descriptor(
            ProtosForeignProviderId id, AtomicInteger factoryCalls) {
        return new ProtosForeignProviderDescriptor(
                id,
                ProtosForeignProviderExecutionProfile.UNAVAILABLE,
                () -> {
                    factoryCalls.incrementAndGet();
                    throw new AssertionError("provider factory must not be invoked");
                });
    }
}
