/* APL-1.0 licensed work; see LICENSE.TXT. */
package com.guillermomolina.protos.execution;

import static org.junit.jupiter.api.Assertions.*;

import com.guillermomolina.protos.runtime.*;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ProtosFileAppendConformanceTest {
    private static ProtosPrelude core() throws Exception {
        return new ProtosCoreBootstrap().bootstrap(Path.of("protos", "lib", "core"));
    }

    private static ProtosIntegerValue integer(long value) {
        return new ProtosIntegerValue(BigInteger.valueOf(value));
    }

    private static ProtosBytesValue bytes(ProtosObjectValue prototype, int... octets) {
        ProtosBytesValue value = new ProtosBytesValue(prototype);
        for (int octet : octets) {
            value.indexedAdd(integer(octet));
        }
        return value;
    }

    private static Fixture fixture() throws Exception {
        ProtosPrelude prelude = core();
        ProtosActivation activation = prelude.newModuleActivation();
        ProtosObjectValue bytesPrototype =
                new ProtosObjectValue(ProtosObjectValue.rootObject());
        ProtosStandardBytesProtocol.install(bytesPrototype);

        SharedAppendCoordinator coordinator = new SharedAppendCoordinator(BigInteger.valueOf(5));
        AliasResource resourceA = new AliasResource("A", coordinator);
        AliasResource resourceB = new AliasResource("B", coordinator);
        ProtosFileFlow.Capabilities capabilities =
                new ProtosFileFlow.Capabilities(
                        false, true, true, false, false, false, true);
        ProtosObjectValue fileA =
                ProtosStandardFileProtocol.createAppend(
                        bytesPrototype, activation, resourceA, capabilities);
        ProtosObjectValue fileB =
                ProtosStandardFileProtocol.createAppend(
                        bytesPrototype, activation, resourceB, capabilities);
        return new Fixture(
                prelude,
                activation,
                bytesPrototype,
                coordinator,
                resourceA,
                resourceB,
                fileA,
                fileB);
    }

    @Test
    void appendSelectsEofAtContributionTimeAcrossAliasesAndKeepsIndependentCursors()
            throws Exception {
        Fixture fixture = fixture();

        assertEquals(BigInteger.ZERO, position(fixture.fileA, fixture.activation));
        assertEquals(BigInteger.ZERO, position(fixture.fileB, fixture.activation));

        ProtosFutureValue seek =
                future(
                        fixture.fileA,
                        "seek",
                        List.of(integer(99)),
                        fixture.activation);
        assertEquals(
                BigInteger.valueOf(99),
                ((ProtosIntegerValue) seek.resolvedValue().orElseThrow()).value());

        ProtosFutureValue writeA =
                future(
                        fixture.fileA,
                        "write",
                        List.of(bytes(fixture.bytesPrototype, 1, 2)),
                        fixture.activation);
        ProtosFutureValue writeB =
                future(
                        fixture.fileB,
                        "write",
                        List.of(bytes(fixture.bytesPrototype, 3, 4, 5)),
                        fixture.activation);

        ProtosFutureValue observedA =
                future(fixture.fileA, "position", List.of(), fixture.activation);
        ProtosFutureValue observedB =
                future(fixture.fileB, "position", List.of(), fixture.activation);
        assertEquals(ProtosFutureValue.State.PENDING, observedA.state());
        assertEquals(ProtosFutureValue.State.PENDING, observedB.state());

        // Concurrent aliases have no predetermined relative append order. Complete B first.
        fixture.coordinator.succeed(1);
        assertSame(fixture.fileB, writeB.resolvedValue().orElseThrow());
        assertEquals(
                BigInteger.valueOf(8),
                ((ProtosIntegerValue) observedB.resolvedValue().orElseThrow()).value());
        assertEquals(ProtosFutureValue.State.PENDING, observedA.state());

        fixture.coordinator.succeed(0);
        assertSame(fixture.fileA, writeA.resolvedValue().orElseThrow());
        assertEquals(
                BigInteger.TEN,
                ((ProtosIntegerValue) observedA.resolvedValue().orElseThrow()).value());

        assertEquals(
                List.of("B@5+3", "A@8+2"),
                fixture.coordinator.contributions);
        assertEquals(BigInteger.TEN, fixture.coordinator.eof);
        // A's earlier seek(99) affected its logical cursor until append, but not append placement.
        assertEquals(BigInteger.TEN, position(fixture.fileA, fixture.activation));
        assertEquals(BigInteger.valueOf(8), position(fixture.fileB, fixture.activation));
    }

    @Test
    void emptyAppendDoesNotConsultEofOrMoveLogicalPosition() throws Exception {
        Fixture fixture = fixture();
        future(
                        fixture.fileA,
                        "seek",
                        List.of(integer(17)),
                        fixture.activation)
                .resolvedValue()
                .orElseThrow();

        int callsBefore = fixture.coordinator.appendCalls.get();
        ProtosFutureValue empty =
                future(
                        fixture.fileA,
                        "write",
                        List.of(bytes(fixture.bytesPrototype)),
                        fixture.activation);

        assertSame(fixture.fileA, empty.resolvedValue().orElseThrow());
        assertEquals(callsBefore, fixture.coordinator.appendCalls.get());
        assertEquals(BigInteger.valueOf(17), position(fixture.fileA, fixture.activation));
        assertEquals(BigInteger.valueOf(5), fixture.coordinator.eof);
    }

    @Test
    void failedAppendPrefixMovesCursorExactlyAndZeroPrefixDoesNot() throws Exception {
        Fixture fixture = fixture();

        ProtosFutureValue partial =
                future(
                        fixture.fileA,
                        "write",
                        List.of(bytes(fixture.bytesPrototype, 10, 11, 12, 13)),
                        fixture.activation);
        fixture.coordinator.fail(0, 2);

        assertEquals(ProtosFutureValue.State.FAILED, partial.state());
        assertEquals(BigInteger.valueOf(7), position(fixture.fileA, fixture.activation));
        assertEquals(BigInteger.valueOf(7), fixture.coordinator.eof);

        ProtosFutureValue next =
                future(
                        fixture.fileB,
                        "write",
                        List.of(bytes(fixture.bytesPrototype, 20, 21)),
                        fixture.activation);
        fixture.coordinator.succeed(0);
        assertSame(fixture.fileB, next.resolvedValue().orElseThrow());
        assertEquals(BigInteger.valueOf(9), position(fixture.fileB, fixture.activation));

        future(
                        fixture.fileA,
                        "seek",
                        List.of(integer(42)),
                        fixture.activation)
                .resolvedValue()
                .orElseThrow();
        ProtosFutureValue zero =
                future(
                        fixture.fileA,
                        "write",
                        List.of(bytes(fixture.bytesPrototype, 30, 31, 32)),
                        fixture.activation);
        fixture.coordinator.fail(0, 0);

        assertEquals(ProtosFutureValue.State.FAILED, zero.state());
        assertEquals(BigInteger.valueOf(42), position(fixture.fileA, fixture.activation));
        assertEquals(BigInteger.valueOf(9), fixture.coordinator.eof);
        assertEquals(
                List.of("A@5+2", "B@7+2"),
                fixture.coordinator.contributions);
    }

    @Test
    void cancellationBeforeAppendCommitContributesNothingAndActorTerminationUsesSamePath()
            throws Exception {
        Fixture fixture = fixture();

        ProtosFutureValue cancelled =
                future(
                        fixture.fileA,
                        "write",
                        List.of(bytes(fixture.bytesPrototype, 1, 2)),
                        fixture.activation);
        assertTrue(cancelled.cancelRequest());
        assertEquals(ProtosFutureValue.State.CANCELLED, cancelled.state());
        assertEquals(1, fixture.coordinator.cancellations.get());
        fixture.coordinator.succeed(0);
        assertEquals(BigInteger.valueOf(5), fixture.coordinator.eof);
        assertEquals(BigInteger.ZERO, position(fixture.fileA, fixture.activation));

        ProtosFutureValue terminated =
                future(
                        fixture.fileB,
                        "write",
                        List.of(bytes(fixture.bytesPrototype, 3, 4)),
                        fixture.activation);
        fixture.activation.executionDomain().actorTerminated();
        assertEquals(ProtosFutureValue.State.CANCELLED, terminated.state());
        assertEquals(2, fixture.coordinator.cancellations.get());
        fixture.coordinator.succeed(0);
        assertEquals(BigInteger.valueOf(5), fixture.coordinator.eof);
        assertTrue(fixture.coordinator.contributions.isEmpty());
    }

    @Test
    void appendCapabilityShapeMustBeDeclaredHonestly() throws Exception {
        ProtosPrelude prelude = core();
        ProtosActivation activation = prelude.newModuleActivation();
        ProtosObjectValue bytesPrototype =
                new ProtosObjectValue(ProtosObjectValue.rootObject());
        ProtosStandardBytesProtocol.install(bytesPrototype);

        ProtosFileFlow.Capabilities append =
                new ProtosFileFlow.Capabilities(
                        false, true, false, false, false, false, true);

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ProtosStandardFileProtocol.createPositioned(
                                bytesPrototype,
                                activation,
                                new PositionedOnlyResource(),
                                append));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ProtosStandardFileProtocol.createAppend(
                                bytesPrototype,
                                activation,
                                new PositionedOnlyResource(),
                                append));
    }

    private static ProtosFutureValue future(
            ProtosObjectValue receiver,
            String selector,
            List<Object> arguments,
            ProtosActivation activation) {
        return (ProtosFutureValue)
                ProtosInvocation.invokeMessage(receiver, selector, arguments, activation);
    }

    private static BigInteger position(
            ProtosObjectValue file, ProtosActivation activation) {
        return ((ProtosIntegerValue)
                        future(file, "position", List.of(), activation)
                                .resolvedValue()
                                .orElseThrow())
                .value();
    }

    private record Fixture(
            ProtosPrelude prelude,
            ProtosActivation activation,
            ProtosObjectValue bytesPrototype,
            SharedAppendCoordinator coordinator,
            AliasResource resourceA,
            AliasResource resourceB,
            ProtosObjectValue fileA,
            ProtosObjectValue fileB) {}

    private static final class PendingAppend {
        private final String alias;
        private final byte[] bytes;
        private final ProtosFileFlow.AppendCompletion completion;
        private boolean cancelled;

        private PendingAppend(
                String alias,
                byte[] bytes,
                ProtosFileFlow.AppendCompletion completion) {
            this.alias = alias;
            this.bytes = bytes;
            this.completion = completion;
        }
    }

    private static final class SharedAppendCoordinator {
        private BigInteger eof;
        private final List<PendingAppend> pending = new ArrayList<>();
        private final List<String> contributions = new ArrayList<>();
        private final AtomicInteger appendCalls = new AtomicInteger();
        private final AtomicInteger cancellations = new AtomicInteger();

        private SharedAppendCoordinator(BigInteger initialEof) {
            eof = initialEof;
        }

        private ProtosFileFlow.Cancellation append(
                String alias,
                byte[] bytes,
                ProtosFileFlow.AppendCompletion completion) {
            appendCalls.incrementAndGet();
            PendingAppend pendingAppend =
                    new PendingAppend(alias, bytes.clone(), completion);
            pending.add(pendingAppend);
            return () -> {
                if (!pendingAppend.cancelled) {
                    pendingAppend.cancelled = true;
                    cancellations.incrementAndGet();
                }
            };
        }

        private void succeed(int index) {
            PendingAppend operation = pending.remove(index);
            if (operation.cancelled) {
                return;
            }
            BigInteger start = eof;
            if (!operation.completion.commitFirstContribution(start)) {
                operation.completion.failed(0);
                return;
            }
            eof = eof.add(BigInteger.valueOf(operation.bytes.length));
            contributions.add(
                    operation.alias
                            + "@"
                            + start
                            + "+"
                            + operation.bytes.length);
            operation.completion.succeeded();
        }

        private void fail(int index, int contributedPrefix) {
            PendingAppend operation = pending.remove(index);
            if (operation.cancelled) {
                return;
            }
            if (contributedPrefix < 0 || contributedPrefix > operation.bytes.length) {
                throw new IllegalArgumentException("bad test prefix");
            }
            if (contributedPrefix == 0) {
                operation.completion.failed(0);
                return;
            }

            BigInteger start = eof;
            if (!operation.completion.commitFirstContribution(start)) {
                operation.completion.failed(0);
                return;
            }
            eof = eof.add(BigInteger.valueOf(contributedPrefix));
            contributions.add(
                    operation.alias
                            + "@"
                            + start
                            + "+"
                            + contributedPrefix);
            operation.completion.failed(contributedPrefix);
        }
    }

    private static final class AliasResource
            implements ProtosFileFlow.AppendWritableResource, ProtosFileFlow.SeekableResource {
        private final String alias;
        private final SharedAppendCoordinator coordinator;

        private AliasResource(String alias, SharedAppendCoordinator coordinator) {
            this.alias = alias;
            this.coordinator = coordinator;
        }

        @Override
        public ProtosFileFlow.Cancellation append(
                byte[] bytes, ProtosFileFlow.AppendCompletion completion) {
            return coordinator.append(alias, bytes, completion);
        }

        @Override
        public ProtosFileFlow.Cancellation endPosition(
                ProtosFileFlow.IntegerCompletion completion) {
            completion.succeeded(coordinator.eof);
            return () -> {};
        }

        @Override
        public void close(ProtosFileFlow.CloseCompletion completion) {
            completion.succeeded();
        }
    }

    private static final class PositionedOnlyResource
            implements ProtosFileFlow.WritableResource {
        @Override
        public ProtosFileFlow.Cancellation writeAt(
                BigInteger position,
                byte[] bytes,
                ProtosFileFlow.WriteCompletion completion) {
            return () -> {};
        }

        @Override
        public void close(ProtosFileFlow.CloseCompletion completion) {
            completion.succeeded();
        }
    }
}
