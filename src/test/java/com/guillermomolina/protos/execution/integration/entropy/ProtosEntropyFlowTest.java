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

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosBytesValue;
import com.guillermomolina.protos.runtime.ProtosEntropyCapabilityValue;
import com.guillermomolina.protos.runtime.ProtosFutureValue;
import com.guillermomolina.protos.runtime.ProtosNumericValueSupport;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.io.ProtosEntropyBackend;
import com.guillermomolina.protos.runtime.io.ProtosEntropyFlow;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** I157 / D243-C asynchronous generation and I/O lifecycle. */
final class ProtosEntropyFlowTest {
    private static final Path CORE =
            Path.of("protos", "lib", "core");

    @Test
    void zeroLengthIsFreshAndNeverInvokesTheBackend() throws Exception {
        Fixture x = fixture();

        ProtosFutureValue first = generate(x, 0);
        ProtosFutureValue second = generate(x, 0);

        ProtosBytesValue a = resolvedBytes(first);
        ProtosBytesValue b = resolvedBytes(second);

        assertNotSame(first, second);
        assertNotSame(a, b);
        assertEquals(0, a.indexedSize());
        assertEquals(0, b.indexedSize());
        assertFalse(a.isFrozen());
        assertFalse(b.isFrozen());
        assertSame(x.prelude.bytesPrototypeForRuntime(),
                a.parent().orElseThrow());
        assertEquals(0, x.backend.calls.size());
    }

    @Test
    void exactLengthsAndUnsignedOctetsAreMaterializedPerOperation()
            throws Exception {
        Fixture x = fixture();

        for (int count : List.of(1, 16, 32, 65536)) {
            ProtosFutureValue future = generate(x, count);
            byte[] delivered = new byte[count];
            for (int index = 0; index < count; index++) {
                delivered[index] = (byte) (index + 128);
            }
            x.backend.last().succeeded(delivered);
            drain(x);

            ProtosBytesValue bytes = resolvedBytes(future);
            assertEquals(count, bytes.indexedSize());
            assertFalse(bytes.isFrozen());
            assertSame(x.prelude.bytesPrototypeForRuntime(),
                    bytes.parent().orElseThrow());

            assertEquals(128L, octet(bytes, 0));
            if (count > 127) {
                assertEquals(255L, octet(bytes, 127));
                assertEquals(0L, octet(bytes, 128));
            }
        }
    }

    @Test
    void hostCallbackPublishesOnlyThroughTheActorDomain()
            throws Exception {
        Fixture x = fixture();
        ProtosFutureValue future = generate(x, 16);

        CompletableFuture.runAsync(
                () -> x.backend.last().succeeded(new byte[16])).get();

        assertEquals(ProtosFutureValue.State.PENDING, future.state());
        assertTrue(x.activation.executionDomain().dispatchOne());
        assertEquals(ProtosFutureValue.State.RESOLVED, future.state());
        assertEquals(16, resolvedBytes(future).indexedSize());
    }

    @Test
    void eachResultIsIndependentOfHostAndEarlierGuestBuffers()
            throws Exception {
        Fixture x = fixture();
        ProtosFutureValue one = generate(x, 16);

        byte[] submitted = new byte[16];
        submitted[0] = (byte) 254;
        x.backend.last().succeeded(submitted);
        drain(x);

        ProtosBytesValue a = resolvedBytes(one);
        assertEquals(254L, octet(a, 0));

        submitted[0] = 12;
        assertEquals(254L, octet(a, 0));

        ProtosFutureValue two = generate(x, 16);
        x.backend.last().succeeded(new byte[16]);
        drain(x);

        ProtosBytesValue b = resolvedBytes(two);
        assertNotSame(a, b);

        a.indexedPut(0, ProtosNumericValueSupport.integer(17));
        assertEquals(17L, octet(a, 0));
        assertEquals(0L, octet(b, 0));
    }

    @Test
    void incompleteAndNullResultsFailWithoutPartialBytes()
            throws Exception {
        Fixture x = fixture();

        ProtosFutureValue incomplete = generate(x, 16);
        x.backend.last().succeeded(new byte[15]);
        drain(x);
        assertFailedAs(x, incomplete, "IOError");

        ProtosFutureValue nullResult = generate(x, 16);
        x.backend.last().succeeded(null);
        drain(x);
        assertFailedAs(x, nullResult, "IOError");

        assertNotSame(incomplete.failedError().orElseThrow(),
                nullResult.failedError().orElseThrow());
    }

    @Test
    void backendFailureProducesFreshIoError() throws Exception {
        Fixture x = fixture();

        ProtosFutureValue first = generate(x, 16);
        x.backend.last().failed();
        drain(x);
        assertFailedAs(x, first, "IOError");

        ProtosFutureValue second = generate(x, 32);
        x.backend.last().failed();
        drain(x);
        assertFailedAs(x, second, "IOError");

        assertNotSame(first.failedError().orElseThrow(),
                second.failedError().orElseThrow());
    }

    @Test
    void cancellationBeforePublicationSuppressesLateResults()
            throws Exception {
        Fixture x = fixture();
        ProtosFutureValue future = generate(x, 16);
        Call call = x.backend.last();

        assertTrue(future.cancelRequest());
        assertEquals(ProtosFutureValue.State.CANCELLED,
                future.state());
        assertEquals(1, call.cancels.get());

        CompletableFuture.runAsync(
                () -> call.succeeded(new byte[16])).get();
        drain(x);

        assertEquals(ProtosFutureValue.State.CANCELLED,
                future.state());
        assertTrue(future.resolvedValue().isEmpty());
    }

    @Test
    void cancellationWinsAgainstQueuedPublication() throws Exception {
        Fixture x = fixture();
        ProtosFutureValue future = generate(x, 32);

        CompletableFuture.runAsync(
                () -> x.backend.last().succeeded(new byte[32])).get();

        assertEquals(ProtosFutureValue.State.PENDING, future.state());
        assertTrue(future.cancelRequest());
        drain(x);
        assertEquals(ProtosFutureValue.State.CANCELLED,
                future.state());
    }

    @Test
    void committedSuccessCannotBeRewrittenByCancellationOrDuplicate()
            throws Exception {
        Fixture x = fixture();
        ProtosFutureValue future = generate(x, 16);
        Call call = x.backend.last();

        call.succeeded(new byte[16]);
        drain(x);
        ProtosBytesValue resolved = resolvedBytes(future);

        assertFalse(future.cancelRequest());
        call.failed();
        call.succeeded(new byte[16]);
        drain(x);

        assertSame(resolved, resolvedBytes(future));
        assertEquals(ProtosFutureValue.State.RESOLVED,
                future.state());
    }

    @Test
    void closeSettlesPendingAndIsIdempotent() throws Exception {
        Fixture x = fixture();
        ProtosFutureValue pending = generate(x, 16);
        Call call = x.backend.last();

        ProtosFutureValue firstClose =
                x.entropy.lifecycleForRuntime(x.activation)
                        .close(x.activation);
        ProtosFutureValue secondClose =
                x.entropy.lifecycleForRuntime(x.activation)
                        .close(x.activation);

        assertNotSame(firstClose, secondClose);
        assertEquals(1, x.backend.closes.get());
        assertSame(x.entropy,
                firstClose.resolvedValue().orElseThrow());
        assertSame(x.entropy,
                secondClose.resolvedValue().orElseThrow());
        assertFailedAs(x, pending, "IOLifecycleError");

        call.succeeded(new byte[16]);
        drain(x);
        assertFailedAs(x, pending, "IOLifecycleError");

        int before = x.backend.calls.size();
        ProtosFutureValue denied = generate(x, 16);
        assertFailedAs(x, denied, "IOLifecycleError");
        assertEquals(before, x.backend.calls.size());
    }

    private record Fixture(
            ProtosPrelude prelude,
            ProtosActivation activation,
            RecordingBackend backend,
            ProtosEntropyCapabilityValue entropy) {}

    private static Fixture fixture() throws Exception {
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
        RecordingBackend backend = new RecordingBackend();
        return new Fixture(
                prelude,
                prelude.newModuleActivation(),
                backend,
                new ProtosEntropyCapabilityValue(prelude, backend));
    }

    private static ProtosFutureValue generate(Fixture x, int count) {
        return ProtosEntropyFlow.generate(
                x.entropy, x.activation, count);
    }

    private static void drain(Fixture x) {
        while (x.activation.executionDomain().dispatchOne()) {
            // Guest-domain targeted completions only.
        }
    }

    private static long octet(ProtosBytesValue value, int index) {
        return ProtosNumericValueSupport.exactLong(
                value.indexedAt(index));
    }

    private static ProtosBytesValue resolvedBytes(ProtosFutureValue future) {
        assertEquals(ProtosFutureValue.State.RESOLVED, future.state());
        return assertInstanceOf(
                ProtosBytesValue.class,
                future.resolvedValue().orElseThrow());
    }

    private static void assertFailedAs(
            Fixture x, ProtosFutureValue future, String category) {
        assertEquals(ProtosFutureValue.State.FAILED, future.state());
        ProtosObjectValue error = future.failedError().orElseThrow();
        assertSame(
                x.prelude.bindings().readLocalSlot(category).orElseThrow(),
                error.parent().orElseThrow());
        assertTrue(future.resolvedValue().isEmpty());
    }

    private static final class Call
            implements ProtosEntropyBackend.Completion {
        final ProtosEntropyBackend.Completion delegate;
        final AtomicInteger cancels = new AtomicInteger();

        Call(ProtosEntropyBackend.Completion delegate) {
            this.delegate = delegate;
        }

        @Override
        public void succeeded(byte[] bytes) {
            delegate.succeeded(bytes);
        }

        @Override
        public void failed() {
            delegate.failed();
        }
    }

    private static final class RecordingBackend
            implements ProtosEntropyBackend {
        final List<Call> calls = new ArrayList<>();
        final AtomicInteger closes = new AtomicInteger();

        @Override
        public Cancellation generate(int count, Completion completion) {
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
