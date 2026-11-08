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

import static org.junit.jupiter.api.Assertions.*;

import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosEntropyCapabilityValue;
import com.guillermomolina.protos.runtime.ProtosNetworkCapabilityValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosProcessRuntime;
import com.guillermomolina.protos.runtime.io.ProtosEntropyBackend;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** I157 / D243-C: explicit bootstrap-only Entropy authority. */
final class ProtosEntropyAuthorityTest {
    private static final Path CORE =
            Path.of("protos", "lib", "core");

    @Test
    void sourceBackedProtocolIsPrivateFrozenAndAuthorityFree()
            throws Exception {
        ProtosPrelude prelude = prelude();
        ProtosObjectValue entropy = prelude.entropyPrototypeForRuntime();

        assertTrue(entropy.isFrozen());
        assertSame(ProtosObjectValue.rootObject(),
                entropy.parent().orElseThrow());
        assertEquals(Set.of("bytes", "close"),
                entropy.localSlotsSnapshot().keySet());

        for (String selector : List.of("bytes", "close")) {
            ProtosClosureValue method = assertInstanceOf(
                    ProtosClosureValue.class,
                    entropy.readLocalSlot(selector).orElseThrow());
            assertTrue(method.nativeBody().isEmpty(), selector);
            assertNotNull(method.definition(), selector);
        }

        assertFalse(prelude.bindings().hasLocalSlot("Entropy"));
        assertFalse(prelude.bindings().hasLocalSlot("entropy"));
        assertFalse(prelude.bindings().hasLocalSlot(
                ProtosEntropyHostFacility.BOOTSTRAP_SLOT));
        assertFalse(prelude.isStandardModuleMemberForRuntime(entropy));
        assertNotNull(prelude.newExecutionContext());
        assertFalse(prelude.newExecutionContext().hasLocalSlot("entropy"));
    }

    @Test
    void explicitGrantIsIndependentOfNetworkAndNotInstalledByDefault()
            throws Exception {
        ProtosPrelude prelude = prelude();
        ProtosNetworkCapabilityValue network =
                new ProtosNetworkCapabilityValue(prelude, new Object());
        ProtosEntropyCapabilityValue grant =
                new ProtosEntropyCapabilityValue(prelude, new FakeBackend());

        ProtosStandaloneProcessBootstrap.Result absent =
                bootstrap(prelude, null, null);
        assertFalse(absent.activation().context().hasLocalSlot("entropy"));
        absent.process().requestTerminationForRuntime();

        ProtosStandaloneProcessBootstrap.Result networkOnly =
                bootstrap(prelude, network, null);
        assertTrue(networkOnly.activation().context().hasLocalSlot("network"));
        assertFalse(networkOnly.activation().context().hasLocalSlot("entropy"));
        networkOnly.process().requestTerminationForRuntime();

        ProtosStandaloneProcessBootstrap.Result entropyOnly =
                bootstrap(prelude, null, grant);
        assertFalse(entropyOnly.activation().context().hasLocalSlot("network"));
        assertSame(grant,
                entropyOnly.activation().context()
                        .readLocalSlot("entropy").orElseThrow());
        assertSame(prelude.entropyPrototypeForRuntime(),
                grant.representedDelegationParent(prelude));
        entropyOnly.process().requestTerminationForRuntime();
    }

    @Test
    void aGrantCannotBeBoundToTwoProcesses() throws Exception {
        ProtosPrelude prelude = prelude();
        FakeBackend backend = new FakeBackend();
        ProtosEntropyCapabilityValue grant =
                new ProtosEntropyCapabilityValue(prelude, backend);

        ProtosStandaloneProcessBootstrap.Result first =
                bootstrap(prelude, null, grant);
        assertSame(first.process(), grant.ownerForRuntime().orElseThrow());

        assertThrows(IllegalArgumentException.class,
                () -> bootstrap(prelude, null, grant));
        assertFalse(grant.retiredForRuntime());
        assertEquals(0, backend.closes);

        first.process().requestTerminationForRuntime();
        assertTrue(grant.retiredForRuntime());
        assertEquals(1, backend.closes);

        assertThrows(IllegalArgumentException.class,
                () -> bootstrap(prelude, null, grant));
        assertEquals(1, backend.closes);
    }

    @Test
    void foreignPreludeGrantIsRejectedAndRetiredAtBootstrapFailure()
            throws Exception {
        ProtosPrelude ownerPrelude = prelude();
        ProtosPrelude foreignPrelude = prelude();
        FakeBackend backend = new FakeBackend();

        ProtosEntropyCapabilityValue foreign =
                new ProtosEntropyCapabilityValue(foreignPrelude, backend);
        assertThrows(IllegalStateException.class,
                () -> bootstrap(ownerPrelude, null, foreign));

        assertTrue(foreign.ownerForRuntime().isPresent());
        assertTrue(foreign.retiredForRuntime());
        assertEquals(1, backend.closes);
    }

    @Test
    void processTerminationRetiresOnlyItsOwnEntropy() throws Exception {
        ProtosPrelude prelude = prelude();
        FakeBackend firstBackend = new FakeBackend();
        FakeBackend secondBackend = new FakeBackend();
        ProtosEntropyCapabilityValue first =
                new ProtosEntropyCapabilityValue(prelude, firstBackend);
        ProtosEntropyCapabilityValue second =
                new ProtosEntropyCapabilityValue(prelude, secondBackend);

        ProtosStandaloneProcessBootstrap.Result owned =
                bootstrap(prelude, null, first);
        assertFalse(second.retiredForRuntime());

        assertTrue(owned.process().requestTerminationForRuntime());
        assertTrue(first.retiredForRuntime());
        assertFalse(second.retiredForRuntime());
        assertEquals(1, firstBackend.closes);
        assertEquals(0, secondBackend.closes);

        owned.process().retireEntropyForRuntime();
        first.retireForRuntime();
        assertEquals(1, firstBackend.closes);

        ProtosStandaloneProcessBootstrap.Result other =
                bootstrap(prelude, null, second);
        assertNotSame(owned.process(), other.process());
        other.process().requestTerminationForRuntime();
        assertTrue(second.retiredForRuntime());
        assertEquals(1, secondBackend.closes);
    }

    @Test
    void runtimeHostClosesUnusedAndAlreadyUsedGrants() throws Exception {
        ProtosPrelude prelude = prelude();
        ProtosPolyglotRuntimeHost host =
                ProtosPolyglotRuntimeHost.open();

        ProtosEntropyCapabilityValue unused =
                host.provisionHostEntropy(prelude);
        ProtosEntropyCapabilityValue granted =
                host.provisionHostEntropy(prelude);

        assertEquals(2, host.provisionedEntropyCountForTesting());
        assertFalse(host.networkHostInitializedForTesting());

        ProtosStandaloneProcessBootstrap.Result process =
                bootstrap(prelude, null, granted);
        process.process().requestTerminationForRuntime();

        assertTrue(granted.retiredForRuntime());
        assertFalse(unused.retiredForRuntime());
        assertEquals(0, host.activeProcessContextCountForTesting());

        host.close();
        assertTrue(unused.retiredForRuntime());
        assertEquals(0, host.provisionedEntropyCountForTesting());
        host.close();

        assertThrows(IllegalStateException.class,
                () -> host.provisionHostEntropy(prelude));
        assertThrows(IllegalArgumentException.class,
                () -> bootstrap(prelude, null, unused));
    }

    private static ProtosPrelude prelude() throws Exception {
        return new ProtosCoreBootstrap().bootstrap(CORE);
    }

    private static ProtosStandaloneProcessBootstrap.Result bootstrap(
            ProtosPrelude prelude,
            ProtosNetworkCapabilityValue network,
            ProtosEntropyCapabilityValue entropy) {
        return ProtosStandaloneProcessBootstrap.create(
                prelude,
                List.of(),
                ProtosStandaloneHostedExecution.HOST_ENVIRONMENT_NAME_DOMAIN,
                List.of(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                network,
                null,
                entropy);
    }

    private static final class FakeBackend implements ProtosEntropyBackend {
        private int closes;

        @Override
        public Cancellation generate(int count, Completion completion) {
            completion.succeeded(new byte[count]);
            return () -> {};
        }

        @Override
        public void close() {
            closes++;
        }
    }
}
