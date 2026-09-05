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

import com.guillermomolina.protos.runtime.*;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class ProtosStandardTextWriterProtocolTest {
    private static final Path CORE = Path.of("protos", "lib", "core");

    @Test
    void factoryIsSourceBackedFrozenFreshAndValidatesCapabilitiesSynchronously()
            throws Exception {
        ProtosPrelude prelude = core();
        ProtosActivation activation = prelude.newModuleActivation();
        ProtosObjectValue factory = factory(prelude);
        ProtosEncodingValue utf8 = encoding(prelude, "UTF8");

        assertSame(ProtosObjectValue.rootObject(), factory.parent().orElseThrow());
        assertTrue(factory.isFrozen());
        assertEquals(Set.of("call", "owning"), factory.localSlotsSnapshot().keySet());

        ScriptedTarget target = new ScriptedTarget(activation, true, true);
        ProtosObjectValue first =
                writer(prelude, activation, target.target, utf8, false);
        ProtosObjectValue second =
                writer(prelude, activation, target.target, utf8, false);
        assertNotSame(first, second);
        assertEquals(
                Set.of("writeText", "writeLine", "flush", "close"),
                first.localSlotsSnapshot().keySet());

        ScriptedTarget noClose = new ScriptedTarget(activation, false, false);
        assertThrows(
                ProtosSignalException.class,
                () -> writer(prelude, activation, noClose.target, utf8, true));

        ProtosObjectValue fakeEncoding =
                new ProtosObjectValue(prelude.encodingPrototype());
        assertThrows(
                ProtosSignalException.class,
                () ->
                        ProtosInvocation.invokeMessage(
                                factory,
                                "call",
                                List.of(target.target, fakeEncoding),
                                activation));
        assertEquals(0, target.writes);
    }

    @Test
    void writeTextAndWriteLinePreserveWholeOperationOrdering() throws Exception {
        ProtosPrelude prelude = core();
        ProtosActivation activation = prelude.newModuleActivation();
        ScriptedTarget target = new ScriptedTarget(activation, false, false);
        Pending firstLower = target.pendingWrite();

        ProtosObjectValue writer =
                writer(
                        prelude,
                        activation,
                        target.target,
                        encoding(prelude, "UTF8"),
                        false);

        ProtosFutureValue first = writeText(writer, activation, "A");
        ProtosFutureValue second = writeLine(writer, activation, "B");

        assertEquals(ProtosFutureValue.State.PENDING, first.state());
        assertEquals(ProtosFutureValue.State.PENDING, second.state());
        assertEquals(1, target.writes);
        assertArrayEquals(new byte[] {'A'}, target.payloads.get(0));

        firstLower.resolve(target.target, activation);

        assertEquals(ProtosFutureValue.State.RESOLVED, first.state());
        assertEquals(ProtosFutureValue.State.RESOLVED, second.state());
        assertEquals(2, target.writes);
        assertArrayEquals(new byte[] {'B', '\n'}, target.payloads.get(1));
    }

    @Test
    void emptyWriteDoesNoEncodingOrTargetIoButStaysBehindEarlierOperation()
            throws Exception {
        ProtosPrelude prelude = core();
        ProtosActivation activation = prelude.newModuleActivation();
        ScriptedTarget target = new ScriptedTarget(activation, false, false);
        Pending firstLower = target.pendingWrite();

        ProtosObjectValue writer =
                writer(
                        prelude,
                        activation,
                        target.target,
                        encoding(prelude, "UTF8"),
                        false);

        ProtosFutureValue first = writeText(writer, activation, "A");
        ProtosFutureValue empty = writeText(writer, activation, "");

        assertEquals(ProtosFutureValue.State.PENDING, empty.state());
        assertEquals(1, target.writes);

        firstLower.resolve(target.target, activation);

        assertEquals(ProtosFutureValue.State.RESOLVED, first.state());
        assertEquals(ProtosFutureValue.State.RESOLVED, empty.state());
        assertEquals(
                1,
                target.writes,
                "writeText(\"\") must contribute no target write");
    }

    @Test
    void encodingFailureIsAtomicAndDoesNotPoisonWriter() throws Exception {
        ProtosPrelude prelude = core();
        ProtosActivation activation = prelude.newModuleActivation();
        ScriptedTarget target = new ScriptedTarget(activation, false, false);

        ProtosObjectValue writer =
                writer(
                        prelude,
                        activation,
                        target.target,
                        encoding(prelude, "Latin1"),
                        false);

        ProtosFutureValue rejected = writeText(writer, activation, "€");
        assertEquals(ProtosFutureValue.State.FAILED, rejected.state());
        assertErrorParent(
                prelude, rejected.failedError().orElseThrow(), "EncodingError");
        assertEquals(0, target.writes);

        ProtosFutureValue valid = writeText(writer, activation, "A");
        assertEquals(ProtosFutureValue.State.RESOLVED, valid.state());
        assertEquals(1, target.writes);
        assertArrayEquals(new byte[] {'A'}, target.payloads.get(0));
    }

    @Test
    void downstreamWriteFailurePermanentlyPoisonsLaterOutput() throws Exception {
        ProtosPrelude prelude = core();
        ProtosActivation activation = prelude.newModuleActivation();
        ScriptedTarget target = new ScriptedTarget(activation, true, false);
        ProtosObjectValue ioError =
                ProtosCoreErrors.newOccurrence(
                        activation, ProtosCoreErrors.StandardError.I_O_ERROR);
        target.failWrite(ioError);

        ProtosObjectValue writer =
                writer(
                        prelude,
                        activation,
                        target.target,
                        encoding(prelude, "UTF8"),
                        false);

        ProtosFutureValue failed = writeText(writer, activation, "A");
        assertEquals(ProtosFutureValue.State.FAILED, failed.state());
        assertSame(ioError, failed.failedError().orElseThrow());

        ProtosFutureValue later = writeText(writer, activation, "B");
        assertEquals(ProtosFutureValue.State.FAILED, later.state());
        assertSame(ioError, later.failedError().orElseThrow());

        ProtosFutureValue flush = flush(writer, activation);
        assertEquals(ProtosFutureValue.State.FAILED, flush.state());
        assertSame(ioError, flush.failedError().orElseThrow());
        assertEquals(1, target.writes);
        assertEquals(0, target.flushes);
    }

    @Test
    void flushChainsOnlyWhenImmediateTargetExposesFlushable() throws Exception {
        ProtosPrelude prelude = core();
        ProtosActivation activation = prelude.newModuleActivation();

        ScriptedTarget flushable = new ScriptedTarget(activation, true, false);
        ProtosObjectValue writer =
                writer(
                        prelude,
                        activation,
                        flushable.target,
                        encoding(prelude, "UTF8"),
                        false);
        ProtosFutureValue flushed = flush(writer, activation);
        assertEquals(ProtosFutureValue.State.RESOLVED, flushed.state());
        assertEquals(1, flushable.flushes);

        ScriptedTarget plain = new ScriptedTarget(activation, false, false);
        ProtosObjectValue plainWriter =
                writer(
                        prelude,
                        activation,
                        plain.target,
                        encoding(prelude, "UTF8"),
                        false);
        ProtosFutureValue plainFlush = flush(plainWriter, activation);
        assertEquals(ProtosFutureValue.State.RESOLVED, plainFlush.state());
        assertEquals(0, plain.flushes);
    }

    @Test
    void cancellationBeforeAdmissionContributesNothingAndDoesNotPoison()
            throws Exception {
        ProtosPrelude prelude = core();
        ProtosActivation activation = prelude.newModuleActivation();
        ScriptedTarget target = new ScriptedTarget(activation, false, false);
        Pending firstLower = target.pendingWrite();

        ProtosObjectValue writer =
                writer(
                        prelude,
                        activation,
                        target.target,
                        encoding(prelude, "UTF8"),
                        false);

        ProtosFutureValue first = writeText(writer, activation, "A");
        ProtosFutureValue queued = writeText(writer, activation, "B");
        assertTrue(queued.cancelRequest());
        assertEquals(ProtosFutureValue.State.CANCELLED, queued.state());

        firstLower.resolve(target.target, activation);
        assertEquals(ProtosFutureValue.State.RESOLVED, first.state());
        assertEquals(1, target.writes);

        ProtosFutureValue later = writeText(writer, activation, "C");
        assertEquals(ProtosFutureValue.State.RESOLVED, later.state());
        assertEquals(2, target.writes);
        assertArrayEquals(new byte[] {'C'}, target.payloads.get(1));
    }

    @Test
    void closeCutsOverQueuedUncommittedWorkAndWaitsForCommittedWrite()
            throws Exception {
        ProtosPrelude prelude = core();
        ProtosActivation activation = prelude.newModuleActivation();
        ScriptedTarget target = new ScriptedTarget(activation, false, false);
        Pending firstLower = target.pendingWrite();

        ProtosObjectValue writer =
                writer(
                        prelude,
                        activation,
                        target.target,
                        encoding(prelude, "UTF8"),
                        false);

        ProtosFutureValue first = writeText(writer, activation, "A");
        ProtosFutureValue queued = writeText(writer, activation, "B");
        ProtosFutureValue close = close(writer, activation);

        assertEquals(ProtosFutureValue.State.PENDING, first.state());
        assertEquals(ProtosFutureValue.State.FAILED, queued.state());
        assertErrorParent(
                prelude, queued.failedError().orElseThrow(), "IOLifecycleError");
        assertEquals(ProtosFutureValue.State.PENDING, close.state());

        firstLower.resolve(target.target, activation);

        assertEquals(ProtosFutureValue.State.RESOLVED, first.state());
        assertEquals(ProtosFutureValue.State.RESOLVED, close.state());
        assertEquals(
                ProtosFutureValue.State.FAILED,
                writeText(writer, activation, "later").state());
        assertEquals(1, target.writes);
    }

    @Test
    void borrowingCloseLeavesTargetOpenAndOwningCloseClosesItExactlyOnce()
            throws Exception {
        ProtosPrelude prelude = core();
        ProtosActivation activation = prelude.newModuleActivation();

        ScriptedTarget borrowedTarget = new ScriptedTarget(activation, false, true);
        ProtosObjectValue borrowed =
                writer(
                        prelude,
                        activation,
                        borrowedTarget.target,
                        encoding(prelude, "UTF8"),
                        false);
        assertEquals(
                ProtosFutureValue.State.RESOLVED,
                close(borrowed, activation).state());
        assertEquals(0, borrowedTarget.closes);

        ScriptedTarget ownedTarget = new ScriptedTarget(activation, false, true);
        ProtosObjectValue owned =
                writer(
                        prelude,
                        activation,
                        ownedTarget.target,
                        encoding(prelude, "UTF8"),
                        true);

        ProtosFutureValue firstClose = close(owned, activation);
        ProtosFutureValue secondClose = close(owned, activation);
        assertNotSame(firstClose, secondClose);
        assertEquals(ProtosFutureValue.State.RESOLVED, firstClose.state());
        assertEquals(ProtosFutureValue.State.RESOLVED, secondClose.state());
        assertEquals(1, ownedTarget.closes);
    }

    @Test
    void owningClosePreservesEarlierWrapperFailureAsPrimaryButStillClosesTarget()
            throws Exception {
        ProtosPrelude prelude = core();
        ProtosActivation activation = prelude.newModuleActivation();
        ScriptedTarget target = new ScriptedTarget(activation, false, true);
        ProtosObjectValue writeError =
                ProtosCoreErrors.newOccurrence(
                        activation, ProtosCoreErrors.StandardError.I_O_ERROR);
        ProtosObjectValue closeError =
                ProtosCoreErrors.newOccurrence(
                        activation, ProtosCoreErrors.StandardError.ERROR);
        target.failWrite(writeError);
        target.failClose(closeError);

        ProtosObjectValue writer =
                writer(
                        prelude,
                        activation,
                        target.target,
                        encoding(prelude, "UTF8"),
                        true);

        ProtosFutureValue write = writeText(writer, activation, "A");
        assertSame(writeError, write.failedError().orElseThrow());

        ProtosFutureValue close = close(writer, activation);
        assertEquals(ProtosFutureValue.State.FAILED, close.state());
        assertSame(writeError, close.failedError().orElseThrow());
        assertEquals(1, target.closes);
    }

    @Test
    void invalidTextArgumentsReturnFailedFutureBeforeTargetIo() throws Exception {
        ProtosPrelude prelude = core();
        ProtosActivation activation = prelude.newModuleActivation();
        ScriptedTarget target = new ScriptedTarget(activation, false, false);

        ProtosObjectValue writer =
                writer(
                        prelude,
                        activation,
                        target.target,
                        encoding(prelude, "UTF8"),
                        false);

        ProtosFutureValue wrongType =
                assertInstanceOf(
                        ProtosFutureValue.class,
                        ProtosInvocation.invokeMessage(
                                writer,
                                "writeText",
                                List.of(new ProtosIntegerValue(BigInteger.ONE)),
                                activation));
        assertEquals(ProtosFutureValue.State.FAILED, wrongType.state());
        assertErrorParent(
                prelude,
                wrongType.failedError().orElseThrow(),
                "InvalidIOArgument");

        ProtosFutureValue wrongArity =
                assertInstanceOf(
                        ProtosFutureValue.class,
                        ProtosInvocation.invokeMessage(
                                writer,
                                "writeLine",
                                List.of(),
                                activation));
        assertEquals(ProtosFutureValue.State.FAILED, wrongArity.state());
        assertEquals(0, target.writes);
    }

    private static ProtosPrelude core() throws Exception {
        return new ProtosCoreBootstrap().bootstrap(CORE);
    }

    private static ProtosObjectValue factory(ProtosPrelude prelude) {
        return assertInstanceOf(
                ProtosObjectValue.class,
                prelude.bindings().readLocalSlot("TextWriter").orElseThrow());
    }

    private static ProtosEncodingValue encoding(ProtosPrelude prelude, String name) {
        return assertInstanceOf(
                ProtosEncodingValue.class,
                prelude.encodingPrototype().readLocalSlot(name).orElseThrow());
    }

    private static ProtosObjectValue writer(
            ProtosPrelude prelude,
            ProtosActivation activation,
            Object target,
            ProtosEncodingValue encoding,
            boolean owning) {
        return assertInstanceOf(
                ProtosObjectValue.class,
                ProtosInvocation.invokeMessage(
                        factory(prelude),
                        owning ? "owning" : "call",
                        List.of(target, encoding),
                        activation));
    }

    private static ProtosFutureValue writeText(
            ProtosObjectValue writer, ProtosActivation activation, String text) {
        return assertInstanceOf(
                ProtosFutureValue.class,
                ProtosInvocation.invokeMessage(
                        writer,
                        "writeText",
                        List.of(new ProtosStringValue(text)),
                        activation));
    }

    private static ProtosFutureValue writeLine(
            ProtosObjectValue writer, ProtosActivation activation, String text) {
        return assertInstanceOf(
                ProtosFutureValue.class,
                ProtosInvocation.invokeMessage(
                        writer,
                        "writeLine",
                        List.of(new ProtosStringValue(text)),
                        activation));
    }

    private static ProtosFutureValue flush(
            ProtosObjectValue writer, ProtosActivation activation) {
        return assertInstanceOf(
                ProtosFutureValue.class,
                ProtosInvocation.invokeMessage(
                        writer, "flush", List.of(), activation));
    }

    private static ProtosFutureValue close(
            ProtosObjectValue writer, ProtosActivation activation) {
        return assertInstanceOf(
                ProtosFutureValue.class,
                ProtosInvocation.invokeMessage(
                        writer, "close", List.of(), activation));
    }

    private static void assertErrorParent(
            ProtosPrelude prelude, ProtosObjectValue error, String parentName) {
        assertSame(
                prelude.bindings().readLocalSlot(parentName).orElseThrow(),
                error.parent().orElseThrow());
    }

    private static final class Pending {
        final ProtosFutureValue future;

        Pending(ProtosFutureValue future) {
            this.future = future;
        }

        void resolve(Object value, ProtosActivation activation) {
            future.resolve(value, activation);
        }
    }

    private static final class ScriptedTarget {
        private sealed interface WriteStep permits PendingWrite, FailedWrite {}
        private record PendingWrite(Pending pending) implements WriteStep {}
        private record FailedWrite(ProtosObjectValue error) implements WriteStep {}

        final ProtosObjectValue target =
                new ProtosObjectValue(ProtosObjectValue.rootObject());
        final ProtosActivation constructionActivation;
        final ArrayDeque<WriteStep> writeSteps = new ArrayDeque<>();
        final ArrayList<byte[]> payloads = new ArrayList<>();
        boolean failCloseConfigured;
        ProtosObjectValue closeFailure;
        int writes;
        int flushes;
        int closes;

        ScriptedTarget(
                ProtosActivation activation,
                boolean flushable,
                boolean closable) {
            constructionActivation = activation;
            target.createLocalSlot(
                    "write",
                    ProtosClosureValue.nativeClosure(
                            (callActivation, supplied) -> {
                                writes++;
                                ProtosBytesValue bytes =
                                        assertInstanceOf(
                                                ProtosBytesValue.class,
                                                supplied.get(0));
                                payloads.add(snapshot(bytes));

                                WriteStep step = writeSteps.pollFirst();
                                if (step instanceof PendingWrite pending) {
                                    return pending.pending().future;
                                }
                                ProtosFutureValue future = future(callActivation);
                                if (step instanceof FailedWrite failed) {
                                    future.fail(failed.error());
                                } else {
                                    future.resolve(target, callActivation);
                                }
                                return future;
                            }));

            if (flushable) {
                target.createLocalSlot(
                        "flush",
                        ProtosClosureValue.nativeClosure(
                                (callActivation, supplied) -> {
                                    flushes++;
                                    ProtosFutureValue future = future(callActivation);
                                    future.resolve(target, callActivation);
                                    return future;
                                }));
            }

            if (closable) {
                target.createLocalSlot(
                        "close",
                        ProtosClosureValue.nativeClosure(
                                (callActivation, supplied) -> {
                                    closes++;
                                    ProtosFutureValue future = future(callActivation);
                                    if (failCloseConfigured) {
                                        future.fail(closeFailure);
                                    } else {
                                        future.resolve(target, callActivation);
                                    }
                                    return future;
                                }));
            }
        }

        Pending pendingWrite() {
            Pending pending =
                    new Pending(future(constructionActivation));
            writeSteps.addLast(new PendingWrite(pending));
            return pending;
        }

        void failWrite(ProtosObjectValue error) {
            writeSteps.addLast(new FailedWrite(error));
        }

        void failClose(ProtosObjectValue error) {
            failCloseConfigured = true;
            closeFailure = error;
        }

        private ProtosFutureValue future(ProtosActivation activation) {
            return new ProtosFutureValue(
                    activation.prelude().orElseThrow().futurePrototype(),
                    activation.executionDomain());
        }

        private static byte[] snapshot(ProtosBytesValue bytes) {
            List<Object> values = bytes.indexedSnapshot();
            byte[] result = new byte[values.size()];
            for (int i = 0; i < values.size(); i++) {
                BigInteger value =
                        assertInstanceOf(
                                        ProtosIntegerValue.class,
                                        values.get(i))
                                .value();
                result[i] = (byte) value.intValueExact();
            }
            return result;
        }
    }
}
