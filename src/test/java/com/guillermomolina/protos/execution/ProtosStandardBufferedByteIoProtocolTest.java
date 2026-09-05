/* APL-1.0 licensed work; see LICENSE.TXT. */
package com.guillermomolina.protos.execution;

import static org.junit.jupiter.api.Assertions.*;

import com.guillermomolina.protos.runtime.*;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;

class ProtosStandardBufferedByteIoProtocolTest {
    private static ProtosPrelude core() throws Exception {
        return new ProtosCoreBootstrap().bootstrap(Path.of("protos", "lib", "core"));
    }

    private static ProtosIntegerValue i(long n) {
        return new ProtosIntegerValue(BigInteger.valueOf(n));
    }

    private static ProtosBytesValue bytes(ProtosObjectValue prototype, int... values) {
        ProtosBytesValue bytes = new ProtosBytesValue(prototype);
        for (int value : values) bytes.indexedAdd(i(value));
        return bytes;
    }

    private static Object call(
            ProtosObjectValue object,
            String message,
            List<?> arguments,
            ProtosActivation activation) {
        return ProtosInvocation.invokeMessage(object, message, arguments, activation);
    }

    @Test
    void factoriesAreFrozenAndCreateFreshBorrowingWrappers() throws Exception {
        ProtosPrelude prelude = core();
        ProtosActivation activation = prelude.newModuleActivation();
        ProtosObjectValue factory =
                (ProtosObjectValue)
                        prelude.bindings()
                                .readLocalSlot("BufferedReader")
                                .orElseThrow();
        assertTrue(factory.isFrozen());

        ProtosObjectValue source = source(activation);
        ProtosObjectValue first =
                (ProtosObjectValue)
                        call(factory, "call", List.of(source), activation);
        ProtosObjectValue second =
                (ProtosObjectValue)
                        call(factory, "call", List.of(source), activation);

        assertNotSame(first, second);
        assertTrue(first.hasLocalSlot("read"));
        assertTrue(first.hasLocalSlot("close"));
        assertFalse(first.hasLocalSlot("write"));
    }

    @Test
    void readerReturnsBufferedPrefixBeforeReenteringSourceAndFreshBytes()
            throws Exception {
        ProtosPrelude prelude = core();
        ProtosActivation activation = prelude.newModuleActivation();
        ProtosObjectValue source = source(activation);
        ProtosObjectValue factory =
                (ProtosObjectValue)
                        prelude.bindings()
                                .readLocalSlot("BufferedReader")
                                .orElseThrow();
        ProtosObjectValue reader =
                (ProtosObjectValue)
                        call(factory, "call", List.of(source), activation);

        ProtosFutureValue first =
                (ProtosFutureValue)
                        call(reader, "read", List.of(i(2)), activation);
        ProtosFutureValue second =
                (ProtosFutureValue)
                        call(reader, "read", List.of(i(2)), activation);

        assertEquals(
                List.of(1, 2),
                ints((ProtosBytesValue) first.resolvedValue().orElseThrow()));
        assertEquals(
                List.of(3, 4),
                ints((ProtosBytesValue) second.resolvedValue().orElseThrow()));
        assertEquals(
                1,
                ((Counter) source.readLocalSlot("counter").orElseThrow()).reads);
    }

    @Test
    void writerBuffersUntilFlushAndFlushesNestedFlushableTarget()
            throws Exception {
        ProtosPrelude prelude = core();
        ProtosActivation activation = prelude.newModuleActivation();
        ProtosObjectValue target = target(activation);
        ProtosObjectValue factory =
                (ProtosObjectValue)
                        prelude.bindings()
                                .readLocalSlot("BufferedWriter")
                                .orElseThrow();
        ProtosObjectValue writer =
                (ProtosObjectValue)
                        call(factory, "call", List.of(target), activation);

        ProtosFutureValue write =
                (ProtosFutureValue)
                        call(
                                writer,
                                "write",
                                List.of(
                                        bytes(
                                                (ProtosObjectValue)
                                                        target.readLocalSlot("bp")
                                                                .orElseThrow(),
                                                7,
                                                8)),
                                activation);
        assertSame(writer, write.resolvedValue().orElseThrow());

        Counter counter =
                (Counter) target.readLocalSlot("counter").orElseThrow();
        assertEquals(0, counter.writes);

        ProtosFutureValue flush =
                (ProtosFutureValue)
                        call(writer, "flush", List.of(), activation);
        assertSame(writer, flush.resolvedValue().orElseThrow());
        assertEquals(1, counter.writes);
        assertEquals(1, counter.flushes);
    }

    @Test
    void borrowingCloseDoesNotCloseTargetButOwningCloseDoes()
            throws Exception {
        ProtosPrelude prelude = core();
        ProtosActivation activation = prelude.newModuleActivation();
        ProtosObjectValue factory =
                (ProtosObjectValue)
                        prelude.bindings()
                                .readLocalSlot("BufferedWriter")
                                .orElseThrow();

        ProtosObjectValue firstTarget = target(activation);
        ProtosObjectValue borrowed =
                (ProtosObjectValue)
                        call(factory, "call", List.of(firstTarget), activation);
        call(borrowed, "close", List.of(), activation);
        assertEquals(
                0,
                ((Counter) firstTarget.readLocalSlot("counter").orElseThrow())
                        .closes);

        ProtosObjectValue secondTarget = target(activation);
        ProtosObjectValue owned =
                (ProtosObjectValue)
                        call(factory, "owning", List.of(secondTarget), activation);
        call(
                owned,
                "write",
                List.of(
                        bytes(
                                (ProtosObjectValue)
                                        secondTarget.readLocalSlot("bp")
                                                .orElseThrow(),
                                9)),
                activation);
        ProtosFutureValue close =
                (ProtosFutureValue)
                        call(owned, "close", List.of(), activation);

        assertSame(owned, close.resolvedValue().orElseThrow());
        Counter counter =
                (Counter) secondTarget.readLocalSlot("counter").orElseThrow();
        assertEquals(1, counter.writes);
        assertEquals(1, counter.flushes);
        assertEquals(1, counter.closes);
    }

    @Test
    void invalidArgumentsUseFailedFutureAfterDispatch() throws Exception {
        ProtosPrelude prelude = core();
        ProtosActivation activation = prelude.newModuleActivation();
        ProtosObjectValue factory =
                (ProtosObjectValue)
                        prelude.bindings()
                                .readLocalSlot("BufferedReader")
                                .orElseThrow();
        ProtosObjectValue reader =
                (ProtosObjectValue)
                        call(
                                factory,
                                "call",
                                List.of(source(activation)),
                                activation);
        ProtosFutureValue future =
                (ProtosFutureValue)
                        call(reader, "read", List.of(i(0)), activation);
        assertEquals(ProtosFutureValue.State.FAILED, future.state());
    }

    @Test
    void closeCutoverTerminatesAcceptedUncommittedReadsAndKeepsCloseIrreversible()
            throws Exception {
        ProtosPrelude prelude = core();
        ProtosActivation activation = prelude.newModuleActivation();
        PendingReadSource pending = pendingReadSource(activation, true);
        ProtosObjectValue factory =
                (ProtosObjectValue)
                        prelude.bindings()
                                .readLocalSlot("BufferedReader")
                                .orElseThrow();
        ProtosObjectValue reader =
                (ProtosObjectValue)
                        call(factory, "call", List.of(pending.source), activation);

        ProtosFutureValue first =
                (ProtosFutureValue)
                        call(reader, "read", List.of(i(1)), activation);
        ProtosFutureValue second =
                (ProtosFutureValue)
                        call(reader, "read", List.of(i(1)), activation);

        ProtosFutureValue close =
                (ProtosFutureValue)
                        call(reader, "close", List.of(), activation);

        assertEquals(ProtosFutureValue.State.FAILED, first.state());
        assertEquals(ProtosFutureValue.State.FAILED, second.state());
        assertEquals(ProtosFutureValue.State.PENDING, close.state());
        assertTrue(close.cancelRequest());
        assertEquals(ProtosFutureValue.State.PENDING, close.state());

        ProtosBytesValue result =
                bytes(new ProtosObjectValue(ProtosObjectValue.rootObject()), 41);
        pending.lower.resolve(result, activation);

        assertEquals(ProtosFutureValue.State.RESOLVED, close.state());
        assertSame(reader, close.resolvedValue().orElseThrow());
        assertEquals(1, pending.cancelRequests);
    }

    @Test
    void cancellingActiveReadPropagatesToLowerFutureAndPreservesCancelledOutcome()
            throws Exception {
        ProtosPrelude prelude = core();
        ProtosActivation activation = prelude.newModuleActivation();
        PendingReadSource pending = pendingReadSource(activation, false);
        ProtosObjectValue factory =
                (ProtosObjectValue)
                        prelude.bindings()
                                .readLocalSlot("BufferedReader")
                                .orElseThrow();
        ProtosObjectValue reader =
                (ProtosObjectValue)
                        call(factory, "call", List.of(pending.source), activation);

        ProtosFutureValue read =
                (ProtosFutureValue)
                        call(reader, "read", List.of(i(2)), activation);

        assertTrue(read.cancelRequest());
        assertEquals(1, pending.cancelRequests);
        assertEquals(ProtosFutureValue.State.CANCELLED, read.state());
    }

    @Test
    void closeCutoverDoesNotDuplicateAnInFlightBufferedFlush()
            throws Exception {
        ProtosPrelude prelude = core();
        ProtosActivation activation = prelude.newModuleActivation();
        PendingWriteTarget pending = pendingWriteTarget(activation);
        ProtosObjectValue factory =
                (ProtosObjectValue)
                        prelude.bindings()
                                .readLocalSlot("BufferedWriter")
                                .orElseThrow();
        ProtosObjectValue writer =
                (ProtosObjectValue)
                        call(factory, "call", List.of(pending.target), activation);

        call(
                writer,
                "write",
                List.of(bytes(pending.bytesPrototype, 1, 2, 3)),
                activation);
        ProtosFutureValue flush =
                (ProtosFutureValue)
                        call(writer, "flush", List.of(), activation);
        assertEquals(ProtosFutureValue.State.PENDING, flush.state());

        ProtosFutureValue close =
                (ProtosFutureValue)
                        call(writer, "close", List.of(), activation);
        assertEquals(ProtosFutureValue.State.FAILED, flush.state());
        assertEquals(ProtosFutureValue.State.PENDING, close.state());
        assertEquals(1, pending.counter.writes);

        pending.lowerWrite.resolve(pending.target, activation);

        assertEquals(ProtosFutureValue.State.RESOLVED, close.state());
        assertEquals(1, pending.counter.writes);
        assertEquals(1, pending.counter.flushes);
        assertEquals(1, pending.cancelRequests);
    }

    @Test
    void owningCloseWaitsForOwnedTargetCloseEvenWhenWrapperFinalizationFailed()
            throws Exception {
        ProtosPrelude prelude = core();
        ProtosActivation activation = prelude.newModuleActivation();
        FailingWritePendingCloseTarget target =
                failingWritePendingCloseTarget(activation);
        ProtosObjectValue factory =
                (ProtosObjectValue)
                        prelude.bindings()
                                .readLocalSlot("BufferedWriter")
                                .orElseThrow();
        ProtosObjectValue writer =
                (ProtosObjectValue)
                        call(
                                factory,
                                "owning",
                                List.of(target.target),
                                activation);

        call(
                writer,
                "write",
                List.of(bytes(target.bytesPrototype, 9, 8)),
                activation);
        ProtosFutureValue close =
                (ProtosFutureValue)
                        call(writer, "close", List.of(), activation);

        assertEquals(1, target.counter.writes);
        assertEquals(1, target.counter.closes);
        assertEquals(ProtosFutureValue.State.PENDING, close.state());

        target.lowerClose.resolve(target.target, activation);

        assertEquals(ProtosFutureValue.State.FAILED, close.state());
    }

    private static final class Counter {
        int reads;
        int writes;
        int flushes;
        int closes;
    }

    private static final class PendingReadSource {
        final ProtosObjectValue source;
        final ProtosFutureValue lower;
        int cancelRequests;

        PendingReadSource(ProtosObjectValue source, ProtosFutureValue lower) {
            this.source = source;
            this.lower = lower;
        }
    }

    private static PendingReadSource pendingReadSource(
            ProtosActivation activation, boolean ignoreCancellation) {
        ProtosObjectValue source =
                new ProtosObjectValue(ProtosObjectValue.rootObject());
        ProtosFutureValue lower =
                new ProtosFutureValue(
                        activation.prelude().orElseThrow().futurePrototype(),
                        activation.executionDomain());
        PendingReadSource pending = new PendingReadSource(source, lower);
        lower.attachCancellationProducer(
                () -> {
                    pending.cancelRequests++;
                    if (!ignoreCancellation) lower.cancelTerminal();
                });
        source.createLocalSlot(
                "read",
                ProtosClosureValue.nativeClosure((x, args) -> lower));
        return pending;
    }

    private static final class PendingWriteTarget {
        final ProtosObjectValue target;
        final ProtosObjectValue bytesPrototype;
        final Counter counter;
        ProtosFutureValue lowerWrite;
        int cancelRequests;

        PendingWriteTarget(
                ProtosObjectValue target,
                ProtosObjectValue bytesPrototype,
                Counter counter) {
            this.target = target;
            this.bytesPrototype = bytesPrototype;
            this.counter = counter;
        }
    }

    private static PendingWriteTarget pendingWriteTarget(
            ProtosActivation activation) {
        ProtosObjectValue target =
                new ProtosObjectValue(ProtosObjectValue.rootObject());
        ProtosObjectValue bytesPrototype =
                new ProtosObjectValue(ProtosObjectValue.rootObject());
        ProtosStandardBytesProtocol.install(bytesPrototype);
        Counter counter = new Counter();
        PendingWriteTarget pending =
                new PendingWriteTarget(target, bytesPrototype, counter);

        target.createLocalSlot(
                "write",
                ProtosClosureValue.nativeClosure(
                        (x, args) -> {
                            counter.writes++;
                            ProtosFutureValue future =
                                    new ProtosFutureValue(
                                            x.prelude()
                                                    .orElseThrow()
                                                    .futurePrototype(),
                                            x.executionDomain());
                            pending.lowerWrite = future;
                            future.attachCancellationProducer(
                                    () -> pending.cancelRequests++);
                            return future;
                        }));
        target.createLocalSlot(
                "flush",
                ProtosClosureValue.nativeClosure(
                        (x, args) -> {
                            counter.flushes++;
                            ProtosFutureValue future =
                                    new ProtosFutureValue(
                                            x.prelude()
                                                    .orElseThrow()
                                                    .futurePrototype(),
                                            x.executionDomain());
                            future.resolve(target, x);
                            return future;
                        }));
        return pending;
    }

    private static final class FailingWritePendingCloseTarget {
        final ProtosObjectValue target;
        final ProtosObjectValue bytesPrototype;
        final Counter counter;
        ProtosFutureValue lowerClose;

        FailingWritePendingCloseTarget(
                ProtosObjectValue target,
                ProtosObjectValue bytesPrototype,
                Counter counter) {
            this.target = target;
            this.bytesPrototype = bytesPrototype;
            this.counter = counter;
        }
    }

    private static FailingWritePendingCloseTarget failingWritePendingCloseTarget(
            ProtosActivation activation) {
        ProtosObjectValue target =
                new ProtosObjectValue(ProtosObjectValue.rootObject());
        ProtosObjectValue bytesPrototype =
                new ProtosObjectValue(ProtosObjectValue.rootObject());
        ProtosStandardBytesProtocol.install(bytesPrototype);
        Counter counter = new Counter();
        FailingWritePendingCloseTarget result =
                new FailingWritePendingCloseTarget(
                        target, bytesPrototype, counter);

        target.createLocalSlot(
                "write",
                ProtosClosureValue.nativeClosure(
                        (x, args) -> {
                            counter.writes++;
                            ProtosFutureValue future =
                                    new ProtosFutureValue(
                                            x.prelude()
                                                    .orElseThrow()
                                                    .futurePrototype(),
                                            x.executionDomain());
                            future.fail(
                                    ProtosCoreErrors.newOccurrence(
                                            x,
                                            ProtosCoreErrors.StandardError.I_O_ERROR));
                            return future;
                        }));
        target.createLocalSlot(
                "flush",
                ProtosClosureValue.nativeClosure(
                        (x, args) -> {
                            counter.flushes++;
                            ProtosFutureValue future =
                                    new ProtosFutureValue(
                                            x.prelude()
                                                    .orElseThrow()
                                                    .futurePrototype(),
                                            x.executionDomain());
                            future.resolve(target, x);
                            return future;
                        }));
        target.createLocalSlot(
                "close",
                ProtosClosureValue.nativeClosure(
                        (x, args) -> {
                            counter.closes++;
                            ProtosFutureValue future =
                                    new ProtosFutureValue(
                                            x.prelude()
                                                    .orElseThrow()
                                                    .futurePrototype(),
                                            x.executionDomain());
                            result.lowerClose = future;
                            future.attachCancellationProducer(() -> {});
                            return future;
                        }));
        return result;
    }

    private static ProtosObjectValue source(ProtosActivation activation) {
        ProtosObjectValue source =
                new ProtosObjectValue(ProtosObjectValue.rootObject());
        Counter counter = new Counter();
        source.createLocalSlot("counter", counter);
        source.createLocalSlot(
                "read",
                ProtosClosureValue.nativeClosure(
                        (x, args) -> {
                            counter.reads++;
                            ProtosFutureValue future =
                                    new ProtosFutureValue(
                                            x.prelude()
                                                    .orElseThrow()
                                                    .futurePrototype(),
                                            x.executionDomain());
                            ProtosBytesValue bytes =
                                    new ProtosBytesValue(
                                            new ProtosObjectValue(
                                                    ProtosObjectValue
                                                            .rootObject()));
                            for (int n : new int[] {1, 2, 3, 4}) {
                                bytes.indexedAdd(i(n));
                            }
                            future.resolve(bytes, x);
                            return future;
                        }));
        source.createLocalSlot(
                "close",
                ProtosClosureValue.nativeClosure(
                        (x, args) -> {
                            counter.closes++;
                            ProtosFutureValue future =
                                    new ProtosFutureValue(
                                            x.prelude()
                                                    .orElseThrow()
                                                    .futurePrototype(),
                                            x.executionDomain());
                            future.resolve(source, x);
                            return future;
                        }));
        return source;
    }

    private static ProtosObjectValue target(ProtosActivation activation) {
        ProtosObjectValue target =
                new ProtosObjectValue(ProtosObjectValue.rootObject());
        ProtosObjectValue bytesPrototype =
                new ProtosObjectValue(ProtosObjectValue.rootObject());
        ProtosStandardBytesProtocol.install(bytesPrototype);
        Counter counter = new Counter();
        target.createLocalSlot("bp", bytesPrototype);
        target.createLocalSlot("counter", counter);
        target.createLocalSlot(
                "write",
                ProtosClosureValue.nativeClosure(
                        (x, args) -> {
                            counter.writes++;
                            ProtosFutureValue future =
                                    new ProtosFutureValue(
                                            x.prelude()
                                                    .orElseThrow()
                                                    .futurePrototype(),
                                            x.executionDomain());
                            future.resolve(target, x);
                            return future;
                        }));
        target.createLocalSlot(
                "flush",
                ProtosClosureValue.nativeClosure(
                        (x, args) -> {
                            counter.flushes++;
                            ProtosFutureValue future =
                                    new ProtosFutureValue(
                                            x.prelude()
                                                    .orElseThrow()
                                                    .futurePrototype(),
                                            x.executionDomain());
                            future.resolve(target, x);
                            return future;
                        }));
        target.createLocalSlot(
                "close",
                ProtosClosureValue.nativeClosure(
                        (x, args) -> {
                            counter.closes++;
                            ProtosFutureValue future =
                                    new ProtosFutureValue(
                                            x.prelude()
                                                    .orElseThrow()
                                                    .futurePrototype(),
                                            x.executionDomain());
                            future.resolve(target, x);
                            return future;
                        }));
        return target;
    }

    private static List<Integer> ints(ProtosBytesValue bytes) {
        ArrayList<Integer> result = new ArrayList<>();
        for (int j = 0, n = bytes.indexedSize().intValueExact(); j < n; j++) {
            result.add(
                    ((ProtosIntegerValue) bytes.indexedAt(BigInteger.valueOf(j)))
                            .value()
                            .intValue());
        }
        return result;
    }
}