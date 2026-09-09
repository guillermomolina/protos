/* APL-1.0 licensed work; see LICENSE.TXT. */
package com.guillermomolina.protos.runtime;

import static org.junit.jupiter.api.Assertions.*;

import com.guillermomolina.protos.execution.ProtosCoreBootstrap;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** Focused executable evidence for the PLAT009 ByteWritable first-effect attempt bridge. */
class ProtosByteIoFirstEffectGateTest {
    private static final Path CORE = Path.of("protos", "lib", "core");

    @Test
    void zeroEffectAttemptLetsConcurrentCancellationWin() throws Exception {
        Fixture x = fixture();
        ProtosFutureValue future = x.flow.write(x.activation, bytes(x.bytesPrototype, 1, 2, 3));
        ProtosByteIoFlow.FirstEffectWriteCompletion completion = x.backend.firstEffectCompletion();

        assertTrue(completion.beginFirstEffectAttempt());
        assertTrue(future.cancelRequest());
        assertEquals(ProtosFutureValue.State.PENDING, future.state());
        assertEquals(1, x.backend.cancellations.get());

        assertFalse(completion.finishFirstEffectAttempt(false));
        assertEquals(ProtosFutureValue.State.CANCELLED, future.state());

        ProtosFutureValue later = x.flow.write(x.activation, bytes(x.bytesPrototype, 9));
        ProtosByteIoFlow.FirstEffectWriteCompletion laterCompletion =
                x.backend.firstEffectCompletion();
        assertTrue(laterCompletion.beginFirstEffectAttempt());
        assertTrue(laterCompletion.finishFirstEffectAttempt(true));
        laterCompletion.succeeded();
        assertEquals(ProtosFutureValue.State.RESOLVED, later.state());
        assertSame(x.receiver, later.resolvedValue().orElseThrow());
    }

    @Test
    void positiveFirstEffectBeatsConcurrentCancellationAndWriteCompletes() throws Exception {
        Fixture x = fixture();
        ProtosFutureValue future = x.flow.write(x.activation, bytes(x.bytesPrototype, 4, 5));
        ProtosByteIoFlow.FirstEffectWriteCompletion completion = x.backend.firstEffectCompletion();

        assertTrue(completion.beginFirstEffectAttempt());
        assertTrue(future.cancelRequest());
        assertEquals(ProtosFutureValue.State.PENDING, future.state());
        assertEquals(1, x.backend.cancellations.get());

        assertTrue(completion.finishFirstEffectAttempt(true));
        assertEquals(ProtosFutureValue.State.PENDING, future.state());

        completion.succeeded();
        assertEquals(ProtosFutureValue.State.RESOLVED, future.state());
        assertSame(x.receiver, future.resolvedValue().orElseThrow());
    }

    @Test
    void classifiedZeroEffectFailureStillFailsWhenNoCutoverWon() throws Exception {
        Fixture x = fixture();
        ProtosFutureValue future = x.flow.write(x.activation, bytes(x.bytesPrototype, 7));
        ProtosByteIoFlow.FirstEffectWriteCompletion completion = x.backend.firstEffectCompletion();

        assertTrue(completion.beginFirstEffectAttempt());
        assertTrue(completion.finishFirstEffectAttempt(false));
        completion.failed(0);

        assertEquals(ProtosFutureValue.State.FAILED, future.state());
        assertSame(
                x.prelude.standardErrorPrototype("IOError"),
                future.failedError().orElseThrow().parent().orElseThrow());
    }

    private static Fixture fixture() throws Exception {
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
        ProtosActivation activation = prelude.newModuleActivation();
        ProtosObjectValue bytesPrototype = new ProtosObjectValue(ProtosObjectValue.rootObject());
        ProtosObjectValue receiver = new ProtosObjectValue(ProtosObjectValue.rootObject());
        ControlledBackend backend = new ControlledBackend();
        ProtosByteIoFlow flow =
                new ProtosByteIoFlow(receiver, bytesPrototype, activation, backend);
        return new Fixture(prelude, activation, bytesPrototype, receiver, backend, flow);
    }

    private static ProtosBytesValue bytes(ProtosObjectValue prototype, int... values) {
        ProtosBytesValue bytes = new ProtosBytesValue(prototype);
        for (int value : values) {
            bytes.indexedAdd(new ProtosIntegerValue(BigInteger.valueOf(value)));
        }
        return bytes;
    }

    private static final class ControlledBackend implements ProtosByteIoFlow.Backend {
        private final AtomicReference<ProtosByteIoFlow.WriteCompletion> write =
                new AtomicReference<>();
        private final AtomicInteger cancellations = new AtomicInteger();

        @Override
        public ProtosByteIoFlow.Cancellation read(
                int maxBytes, ProtosByteIoFlow.ReadCompletion completion) {
            return () -> {};
        }

        @Override
        public ProtosByteIoFlow.Cancellation write(
                byte[] bytes, ProtosByteIoFlow.WriteCompletion completion) {
            if (!write.compareAndSet(null, completion)) {
                throw new IllegalStateException("previous controlled write not consumed");
            }
            return cancellations::incrementAndGet;
        }

        private ProtosByteIoFlow.FirstEffectWriteCompletion firstEffectCompletion() {
            ProtosByteIoFlow.WriteCompletion completion = write.getAndSet(null);
            assertNotNull(completion);
            return assertInstanceOf(
                    ProtosByteIoFlow.FirstEffectWriteCompletion.class, completion);
        }
    }

    private record Fixture(
            ProtosPrelude prelude,
            ProtosActivation activation,
            ProtosObjectValue bytesPrototype,
            ProtosObjectValue receiver,
            ControlledBackend backend,
            ProtosByteIoFlow flow) {}
}
