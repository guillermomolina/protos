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

import com.guillermomolina.protos.runtime.ProtosClockCapabilityValue;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** I163: independent, private and lifetime-bounded Clock authority. */
final class ProtosClockAuthorityTest {
    static ProtosPrelude prelude() throws Exception {
        return new ProtosCoreBootstrap().bootstrap(Path.of("protos", "lib", "core"));
    }

    static ProtosStandaloneProcessBootstrap.Result bootstrap(
            ProtosPrelude prelude, ProtosClockCapabilityValue clock) {
        return ProtosStandaloneProcessBootstrap.create(prelude, List.of(),
                ProtosStandaloneHostedExecution.HOST_ENVIRONMENT_NAME_DOMAIN, List.of(),
                null, null, null, null, null, null, null, null, null, null, clock);
    }

    @Test
    void privateSourceBackedShapeDoesNotMintAuthority() throws Exception {
        ProtosPrelude prelude = prelude();
        ProtosObjectValue protocol = prelude.clockPrototypeForRuntime();
        assertTrue(protocol.isFrozen());
        assertSame(ProtosObjectValue.rootObject(), protocol.parent().orElseThrow());
        assertEquals(Set.of("epochNanoseconds"), protocol.localSlotsSnapshot().keySet());
        ProtosClosureValue method = assertInstanceOf(ProtosClosureValue.class,
                protocol.readLocalSlot("epochNanoseconds").orElseThrow());
        assertTrue(method.nativeBody().isEmpty());
        assertNotNull(method.definition());
        for (String name : Set.of("Clock", "clock", "_coreClockBridge")) {
            assertFalse(prelude.bindings().hasLocalSlot(name));
        }
        assertFalse(prelude.isStandardModuleMemberForRuntime(protocol));
        assertFalse(prelude.newExecutionContext().hasLocalSlot("clock"));
        var denied = bootstrap(prelude, null);
        try {
            assertFalse(denied.activation().context().hasLocalSlot("clock"));
        } finally {
            denied.process().requestTerminationForRuntime();
        }
    }

    @Test
    void onlyExplicitGrantIsInstalledAndTerminationRevokesIt() throws Exception {
        ProtosPrelude prelude = prelude();
        ProtosClockCapabilityValue clock = new ProtosClockCapabilityValue(prelude, () -> Instant.EPOCH);
        var granted = bootstrap(prelude, clock);
        try {
            assertSame(clock, granted.activation().context().readLocalSlot("clock").orElseThrow());
            assertSame(granted.process(), clock.ownerForRuntime().orElseThrow());
            assertTrue(granted.process().rootEntropyForRuntime().isEmpty());
            assertTrue(granted.process().rootNetworkForRuntime().isEmpty());
            assertFalse(clock.retiredForRuntime());
        } finally {
            granted.process().requestTerminationForRuntime();
        }
        assertTrue(clock.retiredForRuntime());
        clock.retireForRuntime();
        assertThrows(IllegalArgumentException.class, () -> bootstrap(prelude, clock));
    }

    @Test
    void foreignPreludeAndReuseAreRejectedAndFailedBootstrapRetiresClock() throws Exception {
        ProtosPrelude prelude = prelude();
        ProtosClockCapabilityValue foreign = new ProtosClockCapabilityValue(prelude(), () -> Instant.EPOCH);
        assertThrows(IllegalArgumentException.class, () -> bootstrap(prelude, foreign));
        assertTrue(foreign.retiredForRuntime());
        ProtosClockCapabilityValue clock = new ProtosClockCapabilityValue(prelude, () -> Instant.EPOCH);
        var first = bootstrap(prelude, clock);
        try {
            assertThrows(IllegalArgumentException.class, () -> bootstrap(prelude, clock));
            assertSame(first.process(), clock.ownerForRuntime().orElseThrow());
            assertFalse(clock.retiredForRuntime(), "failed reuse must preserve the existing owner");
            assertThrows(IllegalArgumentException.class, () -> bootstrap(prelude(), clock));
            assertFalse(clock.retiredForRuntime(), "foreign-Prelude reuse must preserve the existing owner");
        } finally {
            first.process().requestTerminationForRuntime();
        }
        ProtosClockCapabilityValue aborted = new ProtosClockCapabilityValue(prelude, () -> Instant.EPOCH);
        assertThrows(NullPointerException.class, () -> ProtosStandaloneProcessBootstrap.create(
                prelude, null, ProtosStandaloneHostedExecution.HOST_ENVIRONMENT_NAME_DOMAIN, List.of(),
                null, null, null, null, null, null, null, null, null, null, aborted));
        assertTrue(aborted.retiredForRuntime());
    }

    @Test
    void hostCloseRevokesOnlyItsOwnCustody() throws Exception {
        ProtosPrelude prelude = prelude();
        ProtosClockCapabilityValue independent = new ProtosClockCapabilityValue(prelude, () -> Instant.EPOCH);
        ProtosClockCapabilityValue owned;
        try (ProtosPolyglotRuntimeHost host = ProtosPolyglotRuntimeHost.open()) {
            owned = host.provisionHostClock(prelude);
            assertFalse(owned.retiredForRuntime());
            assertFalse(host.networkHostInitializedForTesting());
        }
        assertTrue(owned.retiredForRuntime());
        assertFalse(independent.retiredForRuntime());
    }
}
