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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosArrayValue;
import com.guillermomolina.protos.runtime.ProtosFutureValue;
import com.guillermomolina.protos.runtime.ProtosNumericValueSupport;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosResolverCapabilityValue;
import com.guillermomolina.protos.runtime.ProtosStringValue;
import com.guillermomolina.protos.runtime.io.ProtosResolverBackend;
import com.guillermomolina.protos.runtime.io.ProtosResolverBackend.AddressDescriptor;
import com.guillermomolina.protos.runtime.io.ProtosResolverBackend.FailureKind;
import com.guillermomolina.protos.runtime.io.ProtosResolverLookupFlow;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** D223-A1 lookup flow: results, errors, commitment, cancellation and close (NETWORK.md §14). */
final class ProtosResolverLookupFlowTest {
    private static final Path CORE = Path.of("protos", "lib", "core");

    @Test
    void ipv4Ipv6AndMixedResultsAreFreshFrozenCanonicalArraysInAdvisoryOrder() throws Exception {
        Fixture x = fixture();
        ProtosFutureValue v4 = lookup(x);
        x.backend.last().succeeded(List.of(v4(192, 0, 2, 1)));
        drain(x);
        ProtosFutureValue v6 = lookup(x);
        x.backend.last().succeeded(List.of(v6Loopback()));
        drain(x);
        ProtosFutureValue mixed = lookup(x);
        x.backend.last().succeeded(List.of(v6Loopback(), v4(10, 0, 0, 1), v4(192, 0, 2, 1)));
        drain(x);

        ProtosArrayValue only4 = resolvedArray(v4);
        assertAddress(x.prelude, only4.indexedAt(0), 4, 0xC0000201L);
        ProtosArrayValue only6 = resolvedArray(v6);
        assertAddress(x.prelude, only6.indexedAt(0), 6, 1L);
        ProtosArrayValue both = resolvedArray(mixed);
        assertEquals(3, both.indexedSize());
        assertAddress(x.prelude, both.indexedAt(0), 6, 1L);
        assertAddress(x.prelude, both.indexedAt(1), 4, 0x0A000001L);
        assertAddress(x.prelude, both.indexedAt(2), 4, 0xC0000201L);
        assertNotSame(only4, both);
        // No implicit Task: an ordinary operation Future without a producer Task.
        assertTrue(mixed.producerTask().isEmpty());
    }

    @Test
    void duplicatesAreRemovedStructurallyAndBoundsAreAtomic() throws Exception {
        Fixture x = fixture();
        ProtosFutureValue deduplicated = lookup(x);
        x.backend.last().succeeded(List.of(v4(1, 2, 3, 4), v4(1, 2, 3, 4), v4(5, 6, 7, 8)));
        drain(x);
        assertEquals(2, resolvedArray(deduplicated).indexedSize());

        ProtosFutureValue one = lookup(x);
        x.backend.last().succeeded(distinct(1));
        drain(x);
        assertEquals(1, resolvedArray(one).indexedSize());

        ProtosFutureValue sixtyFour = lookup(x);
        List<AddressDescriptor> withDuplicates = new ArrayList<>(distinct(64));
        withDuplicates.addAll(distinct(64));
        x.backend.last().succeeded(withDuplicates);
        drain(x);
        assertEquals(64, resolvedArray(sixtyFour).indexedSize());

        ProtosFutureValue sixtyFive = lookup(x);
        x.backend.last().succeeded(distinct(65));
        drain(x);
        assertResolutionError(x.prelude, sixtyFive, "limit");

        ProtosFutureValue raw = lookup(x);
        List<AddressDescriptor> oversized = new ArrayList<>();
        for (int i = 0; i <= ProtosResolverBackend.MAX_RAW_ADDRESSES; i++) {
            oversized.add(v4(1, 1, 1, 1));
        }
        x.backend.last().succeeded(oversized);
        drain(x);
        assertResolutionError(x.prelude, raw, "limit");

        ProtosFutureValue empty = lookup(x);
        x.backend.last().succeeded(List.of());
        drain(x);
        assertResolutionError(x.prelude, empty, "noAddress");
    }

    @Test
    void everyFailureKindIsAFreshResolutionErrorWithoutNativeDetail() throws Exception {
        Fixture x = fixture();
        List<ProtosObjectValue> errors = new ArrayList<>();
        for (FailureKind kind : FailureKind.values()) {
            ProtosFutureValue future = lookup(x);
            x.backend.last().failed(kind);
            drain(x);
            errors.add(assertResolutionError(x.prelude, future, kind.kind()));
        }
        assertEquals(
                List.of("noAddress", "unavailable", "denied", "limit"),
                List.of(FailureKind.values()).stream().map(FailureKind::kind).toList());
        for (int i = 0; i < errors.size(); i++) {
            assertEquals(List.of("kind"), List.copyOf(errors.get(i).localSlotsSnapshot().keySet()));
            for (int j = i + 1; j < errors.size(); j++) {
                assertNotSame(errors.get(i), errors.get(j));
            }
        }
        ProtosFutureValue again = lookup(x);
        x.backend.last().failed(FailureKind.LIMIT);
        drain(x);
        assertNotSame(errors.get(3), assertResolutionError(x.prelude, again, "limit"));
    }

    @Test
    void asynchronousCompletionIsPublishedOnlyByTheOwningActorDomain() throws Exception {
        Fixture x = fixture();
        AtomicReference<Thread> projectionThread = new AtomicReference<>();
        ProtosFutureValue future =
                ProtosResolverLookupFlow.lookup(
                        x.resolver,
                        x.activation,
                        "example.com.",
                        recording(projectionThread));
        ProtosResolverBackend.Completion completion = x.backend.last();
        CompletableFuture.runAsync(() -> completion.succeeded(List.of(v4(127, 0, 0, 1)))).get();

        assertEquals(ProtosFutureValue.State.PENDING, future.state());
        assertSame(null, projectionThread.get());
        assertTrue(x.activation.executionDomain().dispatchOne());
        assertSame(Thread.currentThread(), projectionThread.get());
        assertEquals(1, resolvedArray(future).indexedSize());
    }

    @Test
    void precommitCancellationSuppressesLateAndRacingResults() throws Exception {
        Fixture x = fixture();
        ProtosFutureValue cancelled = lookup(x);
        Call call = x.backend.calls.get(0);
        assertTrue(cancelled.cancelRequest());
        assertEquals(ProtosFutureValue.State.CANCELLED, cancelled.state());
        assertEquals(1, call.cancels.get());
        CompletableFuture.runAsync(() -> call.succeeded(List.of(v4(1, 1, 1, 1)))).get();
        x.activation.executionDomain().dispatchOne();
        assertEquals(ProtosFutureValue.State.CANCELLED, cancelled.state());

        // Publication queued but not yet dispatched: cancellation still wins.
        ProtosFutureValue racing = lookup(x);
        Call racingCall = x.backend.last();
        CompletableFuture.runAsync(() -> racingCall.succeeded(List.of(v4(2, 2, 2, 2)))).get();
        assertTrue(racing.cancelRequest());
        x.activation.executionDomain().dispatchOne();
        assertEquals(ProtosFutureValue.State.CANCELLED, racing.state());

        // After publication cancellation cannot rewrite the outcome; duplicates are discarded.
        ProtosFutureValue published = lookup(x);
        x.backend.last().succeeded(List.of(v4(3, 3, 3, 3)));
        drain(x);
        assertFalse(published.cancelRequest());
        x.backend.last().failed(FailureKind.UNAVAILABLE);
        drain(x);
        assertEquals(1, resolvedArray(published).indexedSize());
    }

    @Test
    void closeCutsOverPendingLookupsIsIdempotentAndRejectsLaterLookups() throws Exception {
        Fixture x = fixture();
        ProtosFutureValue blocked = lookup(x);
        Call blockedCall = x.backend.last();
        ProtosFutureValue queuedResult = lookup(x);
        Call queuedCall = x.backend.last();
        CompletableFuture.runAsync(() -> queuedCall.succeeded(List.of(v4(4, 4, 4, 4)))).get();

        ProtosFutureValue firstClose = close(x);
        ProtosFutureValue secondClose = close(x);
        assertSame(x.resolver, firstClose.resolvedValue().orElseThrow());
        assertSame(x.resolver, secondClose.resolvedValue().orElseThrow());
        assertNotSame(firstClose, secondClose);
        assertEquals(1, x.backend.closes.get());
        assertFailedAs(blocked, x.prelude, "IOLifecycleError");
        assertFailedAs(queuedResult, x.prelude, "IOLifecycleError");
        assertEquals(1, blockedCall.cancels.get());

        // Late callbacks after close cannot revive the capability or rewrite outcomes.
        CompletableFuture.runAsync(() -> blockedCall.succeeded(List.of(v4(5, 5, 5, 5)))).get();
        while (x.activation.executionDomain().dispatchOne()) {
            // drain discarded publications
        }
        assertFailedAs(blocked, x.prelude, "IOLifecycleError");
        assertFailedAs(queuedResult, x.prelude, "IOLifecycleError");

        int submitted = x.backend.calls.size();
        assertFailedAs(lookup(x), x.prelude, "IOLifecycleError");
        assertEquals(submitted, x.backend.calls.size());
    }

    @Test
    void separateResolverAuthoritiesShareNoResultState() throws Exception {
        Fixture x = fixture();
        RecordingBackend otherBackend = new RecordingBackend();
        ProtosResolverCapabilityValue other =
                new ProtosResolverCapabilityValue(x.prelude, otherBackend);
        ProtosFutureValue first = lookup(x);
        x.backend.last().succeeded(List.of(v4(1, 2, 3, 4)));
        drain(x);
        ProtosFutureValue second =
                ProtosResolverLookupFlow.lookup(
                        other, x.activation, "example.com.", ProtosResolverHostFacility.PROJECTION);
        assertEquals(1, otherBackend.calls.size());
        otherBackend.last().succeeded(List.of(v4(1, 2, 3, 4)));
        drain(x);
        ProtosArrayValue a = resolvedArray(first);
        ProtosArrayValue b = resolvedArray(second);
        assertNotSame(a, b);
        assertNotSame(a.indexedAt(0), b.indexedAt(0));
        close(x);
        assertEquals(0, otherBackend.closes.get());
    }

    // ------------------------------------------------------------ fixtures

    private record Fixture(
            ProtosPrelude prelude,
            ProtosActivation activation,
            RecordingBackend backend,
            ProtosResolverCapabilityValue resolver) {}

    private static Fixture fixture() throws Exception {
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
        RecordingBackend backend = new RecordingBackend();
        return new Fixture(
                prelude,
                prelude.newModuleActivation(),
                backend,
                new ProtosResolverCapabilityValue(prelude, backend));
    }

    private static ProtosFutureValue lookup(Fixture x) {
        return ProtosResolverLookupFlow.lookup(
                x.resolver, x.activation, "example.com.", ProtosResolverHostFacility.PROJECTION);
    }

    /**
     * Runs the owning Actor domain's queued publications. A completion reported after {@code
     * lookup} returned is published only there, never on the reporting thread.
     */
    private static void drain(Fixture x) {
        while (x.activation.executionDomain().dispatchOne()) {
            // publish queued completions
        }
    }

    private static ProtosFutureValue close(Fixture x) {
        return x.resolver.lifecycleForRuntime(x.activation).close(x.activation);
    }

    private static ProtosResolverLookupFlow.Projection recording(AtomicReference<Thread> thread) {
        return new ProtosResolverLookupFlow.Projection() {
            @Override
            public Object addresses(ProtosActivation activation, List<AddressDescriptor> distinct) {
                thread.set(Thread.currentThread());
                return ProtosResolverHostFacility.PROJECTION.addresses(activation, distinct);
            }

            @Override
            public ProtosObjectValue resolutionError(ProtosActivation activation, FailureKind kind) {
                thread.set(Thread.currentThread());
                return ProtosResolverHostFacility.PROJECTION.resolutionError(activation, kind);
            }
        };
    }

    private static AddressDescriptor v4(int a, int b, int c, int d) {
        return new AddressDescriptor(new byte[] {(byte) a, (byte) b, (byte) c, (byte) d});
    }

    private static AddressDescriptor v6Loopback() {
        byte[] octets = new byte[16];
        octets[15] = 1;
        return new AddressDescriptor(octets);
    }

    private static List<AddressDescriptor> distinct(int count) {
        List<AddressDescriptor> result = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            result.add(v4(10, 0, i / 256, i % 256));
        }
        return result;
    }

    private static ProtosArrayValue resolvedArray(ProtosFutureValue future) {
        assertEquals(ProtosFutureValue.State.RESOLVED, future.state());
        ProtosArrayValue array =
                assertInstanceOf(ProtosArrayValue.class, future.resolvedValue().orElseThrow());
        assertTrue(array.isFrozen());
        return array;
    }

    private static void assertAddress(ProtosPrelude prelude, Object value, int version, long bits) {
        ProtosObjectValue address = assertInstanceOf(ProtosObjectValue.class, value);
        assertTrue(address.isFrozen());
        assertSame(prelude.ipAddressPrototypeForRuntime(), address.parent().orElseThrow());
        assertTrue(
                ProtosStandardIpAddressProtocol.recognizesValue(
                        address, prelude.ipAddressPrototypeForRuntime()));
        assertEquals(
                version,
                ProtosNumericValueSupport.exactInt(address.readLocalSlot("version").orElseThrow()));
        assertEquals(
                java.math.BigInteger.valueOf(bits),
                new java.math.BigInteger(
                        1,
                        ProtosNumericValueSupport.unsignedBigEndianOrNull(
                                address.readLocalSlot("bits").orElseThrow(),
                                version == 4 ? 4 : 16)));
    }

    private static ProtosObjectValue assertResolutionError(
            ProtosPrelude prelude, ProtosFutureValue future, String kind) {
        assertEquals(ProtosFutureValue.State.FAILED, future.state());
        ProtosObjectValue error = future.failedError().orElseThrow();
        assertSame(prelude.resolutionErrorPrototypeForRuntime(), error.parent().orElseThrow());
        assertSame(prelude.errorPrototype(), prelude.resolutionErrorPrototypeForRuntime().parent().orElseThrow());
        assertEquals(
                kind,
                assertInstanceOf(ProtosStringValue.class, error.readLocalSlot("kind").orElseThrow())
                        .value());
        return error;
    }

    private static void assertFailedAs(ProtosFutureValue future, ProtosPrelude prelude, String name) {
        assertEquals(ProtosFutureValue.State.FAILED, future.state());
        assertSame(
                prelude.bindings().readLocalSlot(name).orElseThrow(),
                future.failedError().orElseThrow().parent().orElseThrow());
    }

    private static final class Call implements ProtosResolverBackend.Completion {
        private final ProtosResolverBackend.Completion delegate;
        private final AtomicInteger cancels = new AtomicInteger();

        Call(ProtosResolverBackend.Completion delegate) {
            this.delegate = delegate;
        }

        @Override
        public void succeeded(List<AddressDescriptor> addresses) {
            delegate.succeeded(addresses);
        }

        @Override
        public void failed(FailureKind kind) {
            delegate.failed(kind);
        }
    }

    private static final class RecordingBackend implements ProtosResolverBackend {
        private final List<Call> calls = new ArrayList<>();
        private final AtomicInteger closes = new AtomicInteger();

        @Override
        public Cancellation lookup(String absoluteName, Completion completion) {
            Call call = new Call(completion);
            calls.add(call);
            return call.cancels::incrementAndGet;
        }

        @Override
        public void close() {
            closes.incrementAndGet();
        }

        Call last() {
            return calls.get(calls.size() - 1);
        }
    }
}
