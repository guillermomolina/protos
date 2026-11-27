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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.runtime.ProtosBytesValue;
import com.guillermomolina.protos.runtime.ProtosFutureValue;
import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.guillermomolina.protos.runtime.ProtosTcpConnectionFlow;
import com.guillermomolina.protos.runtime.io.ProtosTcpConnectionValue;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ProtosTcpConnectionDuplexLifecycleTest {
    private static final Path CORE = Path.of("protos", "lib", "core");

    @Test
    void sharedProtocolIsInstalledOnceAndRejectsDelegationOnlyReceivers() throws Exception {
        ProtosPrelude prelude = core();
        var activation = prelude.newModuleActivation();
        RecordingBackend backend = new RecordingBackend();
        ProtosTcpConnectionValue first = connection(prelude, activation, backend);
        ProtosTcpConnectionValue second = connection(prelude, activation, new RecordingBackend());
        ProtosObjectValue prototype = prelude.tcpConnectionPrototypeForRuntime();

        assertEquals(
                java.util.Set.of("read", "write", "abort", "close", "shutdownRead", "shutdownWrite", "localEndpoint", "remoteEndpoint"),
                prototype.localSlotsSnapshot().keySet());
        assertTrue(prototype.isFrozen());
        assertTrue(first.localSlotsSnapshot().isEmpty());
        assertTrue(second.localSlotsSnapshot().isEmpty());
        assertSame(
                prototype.readLocalSlot("read").orElseThrow(),
                first.lookupSlot("read").orElseThrow().value());
        assertSame(
                prototype.readLocalSlot("read").orElseThrow(),
                second.lookupSlot("read").orElseThrow().value());

        ProtosObjectValue prototypeChild = new ProtosObjectValue(prototype);
        ProtosObjectValue capabilityChild = new ProtosObjectValue(first);
        ProtosTcpConnectionValue nonOperational =
                new ProtosTcpConnectionValue(prelude, new Object());
        assertThrows(
                ProtosSignalException.class,
                () -> ProtosInvocation.invokeMessage(
                        prototypeChild, "read", List.of(integer(1)), activation));
        assertThrows(
                ProtosSignalException.class,
                () -> ProtosInvocation.invokeMessage(
                        capabilityChild, "close", List.of(), activation));
        assertThrows(
                ProtosSignalException.class,
                () -> ProtosInvocation.invokeMessage(
                        first, "read", List.of(), activation));
        assertThrows(
                ProtosSignalException.class,
                () -> ProtosInvocation.invokeMessage(
                        nonOperational, "read", List.of(integer(1)), activation));
        assertEquals(0, backend.readStarts);
        assertEquals(0, backend.closeStarts);
    }

    @Test
    void pendingReadDoesNotBlockWriteAndEachDirectionKeepsItsOwnOrder() throws Exception {
        ProtosPrelude prelude = core();
        var activation = prelude.newModuleActivation();
        RecordingBackend backend = new RecordingBackend();
        ProtosTcpConnectionValue connection = connection(prelude, activation, backend);

        ProtosFutureValue read1 = future(connection, "read", List.of(integer(4)), activation);
        ProtosFutureValue read2 = future(connection, "read", List.of(integer(4)), activation);
        ProtosFutureValue write1 =
                future(connection, "write", List.of(bytes(prelude, 7, 8)), activation);
        ProtosFutureValue write2 =
                future(connection, "write", List.of(bytes(prelude, 3)), activation);

        assertEquals(1, backend.readStarts);
        assertEquals(1, backend.writeStarts);
        assertEquals(ProtosFutureValue.State.PENDING, read1.state());
        assertEquals(ProtosFutureValue.State.PENDING, write1.state());
        assertEquals(ProtosFutureValue.State.PENDING, read2.state());
        assertEquals(ProtosFutureValue.State.PENDING, write2.state());
        assertArrayEquals(new byte[] {7, 8}, backend.writePayloads.get(0));

        backend.writeCompletions.removeFirst().succeeded();
        drain(activation);
        assertSame(connection, write1.resolvedValue().orElseThrow());
        assertEquals(2, backend.writeStarts);
        assertArrayEquals(new byte[] {3}, backend.writePayloads.get(1));
        assertEquals(1, backend.readStarts);

        backend.readCompletions.removeFirst().data(new byte[] {1, 2});
        assertEquals(ProtosFutureValue.State.PENDING, read1.state());
        assertEquals(1, backend.readStarts);
        assertTrue(
                activation.executionDomain()
                        .dispatchTargetedRuntimeCompletionWithoutHandoffForRuntime());
        assertEquals(2, backend.readStarts);
        ProtosBytesValue result = (ProtosBytesValue) read1.resolvedValue().orElseThrow();
        assertEquals(String.valueOf("1"), com.guillermomolina.protos.runtime.ProtosNumericValueSupport.integerDecimalText(result.indexedAt(0)));
        assertEquals(String.valueOf("2"), com.guillermomolina.protos.runtime.ProtosNumericValueSupport.integerDecimalText(result.indexedAt(1)));

        backend.writeCompletions.removeFirst().succeeded();
        drain(activation);
        backend.readCompletions.removeFirst().eof();
        assertEquals(ProtosFutureValue.State.PENDING, read2.state());
        assertTrue(
                activation.executionDomain()
                        .dispatchTargetedRuntimeCompletionWithoutHandoffForRuntime());
        assertSame(connection, write2.resolvedValue().orElseThrow());
        assertSame(ProtosNullValue.INSTANCE, read2.resolvedValue().orElseThrow());
    }

    @Test
    void writeSnapshotAndReadCancellationUseExistingByteIoContracts() throws Exception {
        ProtosPrelude prelude = core();
        var activation = prelude.newModuleActivation();
        RecordingBackend backend = new RecordingBackend();
        ProtosTcpConnectionValue connection = connection(prelude, activation, backend);

        ProtosBytesValue payload = bytes(prelude, 9, 8);
        ProtosFutureValue write = future(connection, "write", List.of(payload), activation);
        payload.indexedPut(0, integer(1));
        assertArrayEquals(new byte[] {9, 8}, backend.writePayloads.get(0));

        ProtosFutureValue read = future(connection, "read", List.of(integer(2)), activation);
        assertTrue(read.cancelRequest());
        backend.readCompletions.removeFirst().data(new byte[] {4, 5});
        assertTrue(
                activation.executionDomain()
                        .dispatchTargetedRuntimeCompletionWithoutHandoffForRuntime());
        assertEquals(ProtosFutureValue.State.CANCELLED, read.state());

        ProtosFutureValue replay = future(connection, "read", List.of(integer(2)), activation);
        ProtosBytesValue replayed = (ProtosBytesValue) replay.resolvedValue().orElseThrow();
        assertEquals(String.valueOf("4"), com.guillermomolina.protos.runtime.ProtosNumericValueSupport.integerDecimalText(replayed.indexedAt(0)));
        assertEquals(String.valueOf("5"), com.guillermomolina.protos.runtime.ProtosNumericValueSupport.integerDecimalText(replayed.indexedAt(1)));

        backend.writeCompletions.removeFirst().succeeded();
        drain(activation);
        assertSame(connection, write.resolvedValue().orElseThrow());
    }

    @Test
    void closeCutsAcrossBothLanesAndDoesNotStructurallyCloseTheObject() throws Exception {
        ProtosPrelude prelude = core();
        var activation = prelude.newModuleActivation();
        RecordingBackend backend = new RecordingBackend();
        ProtosTcpConnectionValue connection = connection(prelude, activation, backend);

        ProtosFutureValue read = future(connection, "read", List.of(integer(8)), activation);
        ProtosFutureValue write = future(connection, "write", List.of(bytes(prelude, 6)), activation);
        ProtosFutureValue close = future(connection, "close", List.of(), activation);

        assertFailedAs(read, prelude, "IOLifecycleError");
        assertFailedAs(write, prelude, "IOLifecycleError");
        assertEquals(1, backend.readCancels);
        assertEquals(1, backend.writeCancels);
        assertEquals(1, backend.closeStarts);
        assertEquals(ProtosFutureValue.State.PENDING, close.state());

        backend.closeCompletion.succeeded();
        drain(activation);
        assertSame(connection, close.resolvedValue().orElseThrow());
        assertTrue(connection.isOpen(), "resource close must not perform structural Object.close()");
        connection.createLocalSlot("afterResourceClose", integer(17));
        assertEquals(String.valueOf("17"), com.guillermomolina.protos.runtime.ProtosNumericValueSupport.integerDecimalText(connection.readLocalSlot("afterResourceClose").orElseThrow()));

        assertFailedAs(future(connection, "read", List.of(integer(1)), activation), prelude, "IOLifecycleError");
        assertFailedAs(future(connection, "write", List.of(bytes(prelude, 1)), activation), prelude, "IOLifecycleError");

        ProtosFutureValue secondClose = future(connection, "close", List.of(), activation);
        assertNotSame(close, secondClose);
        assertSame(connection, secondClose.resolvedValue().orElseThrow());
        assertEquals(1, backend.closeStarts);
    }

    @Test
    void directionalShutdownsAreIndependentAndIdempotent() throws Exception {
        ProtosPrelude prelude = core();
        var readActivation = prelude.newModuleActivation();
        RecordingBackend readBackend = new RecordingBackend();
        ProtosTcpConnectionValue readConnection = connection(prelude, readActivation, readBackend);

        ProtosFutureValue pendingRead =
                future(readConnection, "read", List.of(integer(4)), readActivation);
        ProtosFutureValue shutdownRead =
                future(readConnection, "shutdownRead", List.of(), readActivation);
        assertSame(ProtosNullValue.INSTANCE, pendingRead.resolvedValue().orElseThrow());
        assertEquals(1, readBackend.readCancels);
        assertEquals(1, readBackend.shutdownReadStarts);
        readBackend.shutdownReadCompletion.succeeded();
        drain(readActivation);
        assertSame(readConnection, shutdownRead.resolvedValue().orElseThrow());
        ProtosFutureValue repeatedReadShutdown =
                future(readConnection, "shutdownRead", List.of(), readActivation);
        assertNotSame(shutdownRead, repeatedReadShutdown);
        assertSame(readConnection, repeatedReadShutdown.resolvedValue().orElseThrow());
        assertEquals(1, readBackend.shutdownReadStarts);
        assertSame(
                ProtosNullValue.INSTANCE,
                future(readConnection, "read", List.of(integer(1)), readActivation)
                        .resolvedValue()
                        .orElseThrow());
        ProtosFutureValue stillWritable =
                future(readConnection, "write", List.of(bytes(prelude, 2)), readActivation);
        assertEquals(1, readBackend.writeStarts);
        readBackend.writeCompletions.removeFirst().succeeded();
        drain(readActivation);
        assertSame(readConnection, stillWritable.resolvedValue().orElseThrow());

        var writeActivation = prelude.newModuleActivation();
        RecordingBackend writeBackend = new RecordingBackend();
        ProtosTcpConnectionValue writeConnection = connection(prelude, writeActivation, writeBackend);
        ProtosFutureValue first =
                future(writeConnection, "write", List.of(bytes(prelude, 3)), writeActivation);
        ProtosFutureValue second =
                future(writeConnection, "write", List.of(bytes(prelude, 4)), writeActivation);
        ProtosFutureValue shutdownWrite =
                future(writeConnection, "shutdownWrite", List.of(), writeActivation);
        ProtosFutureValue secondShutdownWrite =
                future(writeConnection, "shutdownWrite", List.of(), writeActivation);
        assertNotSame(shutdownWrite, secondShutdownWrite);
        assertEquals(ProtosFutureValue.State.PENDING, shutdownWrite.state());
        assertEquals(ProtosFutureValue.State.PENDING, secondShutdownWrite.state());
        assertEquals(0, writeBackend.shutdownWriteStarts);
        assertFailedAs(
                future(writeConnection, "write", List.of(bytes(prelude, 5)), writeActivation),
                prelude,
                "IOLifecycleError");

        writeBackend.writeCompletions.removeFirst().succeeded();
        drain(writeActivation);
        assertSame(writeConnection, first.resolvedValue().orElseThrow());
        assertEquals(2, writeBackend.writeStarts);
        assertEquals(0, writeBackend.shutdownWriteStarts);
        assertEquals(ProtosFutureValue.State.PENDING, shutdownWrite.state());
        assertEquals(ProtosFutureValue.State.PENDING, secondShutdownWrite.state());
        writeBackend.writeCompletions.removeFirst().succeeded();
        drain(writeActivation);
        assertSame(writeConnection, second.resolvedValue().orElseThrow());
        assertEquals(1, writeBackend.shutdownWriteStarts);
        assertEquals(ProtosFutureValue.State.PENDING, shutdownWrite.state());
        assertEquals(ProtosFutureValue.State.PENDING, secondShutdownWrite.state());
        writeBackend.shutdownWriteCompletion.succeeded();
        drain(writeActivation);
        assertSame(writeConnection, shutdownWrite.resolvedValue().orElseThrow());
        assertSame(writeConnection, secondShutdownWrite.resolvedValue().orElseThrow());
        assertEquals(1, writeBackend.shutdownWriteStarts);

        ProtosFutureValue repeatedWriteShutdown =
                future(writeConnection, "shutdownWrite", List.of(), writeActivation);
        assertNotSame(shutdownWrite, repeatedWriteShutdown);
        assertNotSame(secondShutdownWrite, repeatedWriteShutdown);
        assertSame(writeConnection, repeatedWriteShutdown.resolvedValue().orElseThrow());
        assertEquals(1, writeBackend.shutdownWriteStarts);

        ProtosFutureValue stillReadable =
                future(writeConnection, "read", List.of(integer(1)), writeActivation);
        assertEquals(1, writeBackend.readStarts);
        writeBackend.readCompletions.removeFirst().eof();
        assertTrue(
                writeActivation.executionDomain()
                        .dispatchTargetedRuntimeCompletionWithoutHandoffForRuntime());
        assertSame(ProtosNullValue.INSTANCE, stillReadable.resolvedValue().orElseThrow());
    }

    private record AbortFixture(ProtosPrelude prelude,
            com.guillermomolina.protos.runtime.ProtosActivation activation,
            RecordingBackend backend,ProtosTcpConnectionValue connection) {
        ProtosFutureValue call(String selector){return future(connection,selector,List.of(),activation);}
        ProtosFutureValue write(){return future(connection,"write",List.of(bytes(prelude,1,2)),activation);}
        com.guillermomolina.protos.runtime.ProtosByteIoFlow.FirstEffectWriteCompletion attempt(){
            return (com.guillermomolina.protos.runtime.ProtosByteIoFlow.FirstEffectWriteCompletion)backend.writeCompletions.getFirst();
        }
        void publish(){drain(activation);}
        void release(){backend.closeCompletion.succeeded();publish();}
    }

    private static AbortFixture abortFixture() throws Exception {
        var prelude=core();var activation=prelude.newModuleActivation();var backend=new RecordingBackend();
        return new AbortFixture(prelude,activation,backend,connection(prelude,activation,backend));
    }

    private static void drain(com.guillermomolina.protos.runtime.ProtosActivation activation){
        while(activation.executionDomain().dispatchTargetedRuntimeCompletionWithoutHandoffForRuntime()){}
    }

    @Test void abortWithoutPendingWork() throws Exception {
        var x=abortFixture();var abort=x.call("abort");
        assertEquals(1,x.backend.closeStarts);assertTrue(abort.isPending());
        x.release();assertSame(x.connection,abort.resolvedValue().orElseThrow());
    }
    @Test void oversizedWritesAfterAbortFailBeforeCapacityAdmission() throws Exception {
        var x=abortFixture();var abort=x.call("abort");
        var payload=new ProtosBytesValue(x.prelude.bytesPrototypeForRuntime(),new byte[1024*1024+1]);
        var pending=future(x.connection,"write",List.of(payload),x.activation);
        assertFailedAs(pending,x.prelude,"IOLifecycleError");
        assertTrue(abort.isPending());assertEquals(1,x.backend.closeStarts);
        assertEquals(0,x.backend.writeStarts);
        x.release();assertSame(x.connection,abort.resolvedValue().orElseThrow());
        var retired=future(x.connection,"write",List.of(payload),x.activation);
        assertFailedAs(retired,x.prelude,"IOLifecycleError");
        assertNotSame(pending.failedError().orElseThrow(),retired.failedError().orElseThrow());
        assertEquals(0,x.backend.writeStarts);
        var openBackend=new RecordingBackend();
        var open=connection(x.prelude,x.activation,openBackend);
        assertFailedAs(future(open,"write",List.of(payload),x.activation),x.prelude,"IOCapacityExhausted");
        assertEquals(0,openBackend.writeStarts);
        var close=future(open,"close",List.of(),x.activation);
        openBackend.closeCompletion.succeeded();x.publish();
        assertSame(open,close.resolvedValue().orElseThrow());
    }
    @Test void oversizedWriteOnOpenConnectionKeepsCapacityFailure() throws Exception {
        var x=abortFixture();
        var payload=new ProtosBytesValue(x.prelude.bytesPrototypeForRuntime(),new byte[1024*1024+1]);
        assertFailedAs(future(x.connection,"write",List.of(payload),x.activation),x.prelude,"IOCapacityExhausted");
        assertEquals(0,x.backend.writeStarts);
        var admitted=x.write();assertEquals(1,x.backend.writeStarts);
        x.backend.writeCompletions.removeFirst().succeeded();x.publish();
        assertSame(x.connection,admitted.resolvedValue().orElseThrow());
        x.call("close");x.release();
    }
    @Test void writesDuringPhysicalRetirementFailImmediatelyWithoutWaitingForClose() throws Exception {
        var x=abortFixture();var abort=x.call("abort");
        var write=x.write();assertFailedAs(write,x.prelude,"IOLifecycleError");
        assertFailedAs(future(x.connection,"write",List.of(integer(1)),x.activation),x.prelude,"InvalidIOArgument");
        assertTrue(abort.isPending());assertEquals(1,x.backend.closeStarts);
        assertEquals(0,x.backend.writeStarts);
        x.release();assertSame(x.connection,abort.resolvedValue().orElseThrow());
    }
    @Test void abortFailsUncommittedReadWithoutConsumingLateInput() throws Exception {
        var x=abortFixture();var read=future(x.connection,"read",List.of(integer(2)),x.activation);
        x.call("abort");assertFailedAs(read,x.prelude,"IOLifecycleError");
        x.backend.readCompletions.removeFirst().data(new byte[]{3});x.publish();x.release();
        assertFailedAs(future(x.connection,"read",List.of(integer(1)),x.activation),x.prelude,"IOLifecycleError");
    }
    @Test void abortFailsUncommittedWrite() throws Exception {
        var x=abortFixture();var write=x.write();x.call("abort");
        assertFailedAs(write,x.prelude,"IOLifecycleError");x.release();
    }
    @Test void abortStopsFurtherAttemptsAfterCommittedPrefix() throws Exception {
        var x=abortFixture();var write=x.write();var attempt=x.attempt();
        assertTrue(attempt.beginFirstEffectAttempt());assertTrue(attempt.finishFirstEffectAttempt(true));
        var abort=x.call("abort");assertTrue(!attempt.beginFirstEffectAttempt());
        attempt.failed(1);x.publish();assertFailedAs(write,x.prelude,"IOError");
        assertTrue(abort.isPending());x.release();assertSame(x.connection,abort.resolvedValue().orElseThrow());
    }
    @Test void abortEscalatesPendingClose() throws Exception {
        var x=abortFixture();var write=x.write();var attempt=x.attempt();
        assertTrue(attempt.beginFirstEffectAttempt());assertTrue(attempt.finishFirstEffectAttempt(true));
        var close=x.call("close");assertEquals(0,x.backend.closeStarts);
        var abort=x.call("abort");assertNotSame(close,abort);assertEquals(1,x.backend.closeStarts);
        attempt.failed(1);x.publish();x.release();assertSame(x.connection,close.resolvedValue().orElseThrow());
        assertFailedAs(write,x.prelude,"IOError");
    }
    @Test void closeDuringAbortObservesSameRetirement() throws Exception {
        var x=abortFixture();var abort=x.call("abort");var close=x.call("close");
        assertNotSame(abort,close);x.release();assertSame(abort.resolvedValue().orElseThrow(),close.resolvedValue().orElseThrow());
        assertEquals(1,x.backend.closeStarts);
    }
    @Test void abortAfterSuccessfulCloseDoesNotReleaseAgain() throws Exception {
        var x=abortFixture();x.call("close");x.release();
        assertSame(x.connection,x.call("abort").resolvedValue().orElseThrow());assertEquals(1,x.backend.closeStarts);
    }
    @Test void abortAfterFailedClosePreservesRecordedError() throws Exception {
        var x=abortFixture();var close=x.call("close");x.backend.closeCompletion.failed();x.publish();
        assertSame(close.failedError().orElseThrow(),x.call("abort").failedError().orElseThrow());assertEquals(1,x.backend.closeStarts);
    }
    @Test void repeatedAbortReturnsFreshFollowers() throws Exception {
        var x=abortFixture();var first=x.call("abort");var second=x.call("abort");
        assertNotSame(first,second);x.release();assertSame(first.resolvedValue().orElseThrow(),second.resolvedValue().orElseThrow());
    }
    @Test void failedRetirementSharesExactErrorAcrossAllFollowers() throws Exception {
        var x=abortFixture();var first=x.call("abort");var second=x.call("abort");var close=x.call("close");
        x.backend.closeCompletion.failed();x.publish();var error=first.failedError().orElseThrow();
        assertSame(error,second.failedError().orElseThrow());assertSame(error,close.failedError().orElseThrow());
    }
    @Test void cancellingRetirementFollowerCannotStopRelease() throws Exception {
        var x=abortFixture();var abort=x.call("abort");abort.cancelRequest();x.release();
        assertSame(x.connection,abort.resolvedValue().orElseThrow());assertEquals(1,x.backend.closeStarts);
    }
    @Test void validCancellationBeforeAbortKeepsCancelled() throws Exception {
        var x=abortFixture();var write=x.write();write.cancelRequest();x.call("abort");x.release();
        assertEquals(ProtosFutureValue.State.CANCELLED,write.state());
    }
    @Test void physicalSuccessCannotOvertakeAttemptAftermath() throws Exception {
        var x=abortFixture();var write=x.write();var attempt=x.attempt();assertTrue(attempt.beginFirstEffectAttempt());
        var abort=x.call("abort");x.release();assertTrue(abort.isPending());
        assertTrue(attempt.finishFirstEffectAttempt(true));attempt.succeeded();x.publish();
        assertSame(x.connection,write.resolvedValue().orElseThrow());assertSame(x.connection,abort.resolvedValue().orElseThrow());
    }
    @Test void reconciledAftermathStillWaitsForPhysicalRelease() throws Exception {
        var x=abortFixture();var write=x.write();var attempt=x.attempt();assertTrue(attempt.beginFirstEffectAttempt());
        var abort=x.call("abort");assertTrue(attempt.finishFirstEffectAttempt(true));attempt.failed(1);x.publish();
        assertFailedAs(write,x.prelude,"IOError");assertTrue(abort.isPending());x.release();
        assertSame(x.connection,abort.resolvedValue().orElseThrow());
    }
    @Test void committedReadResultSurvivesAbort() throws Exception {
        var x=abortFixture();var read=future(x.connection,"read",List.of(integer(2)),x.activation);
        x.backend.readCompletions.removeFirst().data(new byte[]{7});x.publish();
        var result=read.resolvedValue().orElseThrow();x.call("abort");x.release();assertSame(result,read.resolvedValue().orElseThrow());
    }
    @Test void completedHalfCloseSurvivesAbort() throws Exception {
        var x=abortFixture();var shutdown=x.call("shutdownRead");x.backend.shutdownReadCompletion.succeeded();x.publish();
        x.call("abort");x.release();assertSame(x.connection,shutdown.resolvedValue().orElseThrow());
    }
    @Test void pendingHalfCloseFailureMustPrecedeRetirementSuccess() throws Exception {
        var x=abortFixture();var shutdown=x.call("shutdownRead");var abort=x.call("abort");
        x.release();assertTrue(abort.isPending());x.backend.shutdownReadCompletion.failed();x.publish();
        assertFailedAs(shutdown,x.prelude,"IOError");assertSame(x.connection,abort.resolvedValue().orElseThrow());
    }
    @Test void repeatedPhysicalNotificationCannotRewriteRetirement() throws Exception {
        var x=abortFixture();var abort=x.call("abort");x.release();x.backend.closeCompletion.failed();x.publish();
        assertSame(x.connection,abort.resolvedValue().orElseThrow());assertEquals(1,x.backend.closeStarts);
    }

    @Test void cancellationDuringFirstAttemptKeepsCancelledWhenZeroEffectIsEstablished() throws Exception {
        var x=abortFixture();var write=x.write();var attempt=x.attempt();assertTrue(attempt.beginFirstEffectAttempt());
        write.cancelRequest();var abort=x.call("abort");x.release();assertTrue(abort.isPending());
        assertTrue(!attempt.finishFirstEffectAttempt(false));x.publish();
        assertEquals(ProtosFutureValue.State.CANCELLED,write.state());assertSame(x.connection,abort.resolvedValue().orElseThrow());
    }

    @Test void independentWriteFailureEstablishedBeforeAbortKeepsItsCategory() throws Exception {
        var x=abortFixture();var write=x.write();x.attempt().failed(0);
        var abort=x.call("abort");x.release();x.publish();
        assertFailedAs(write,x.prelude,"IOError");assertSame(x.connection,abort.resolvedValue().orElseThrow());
    }

    private static ProtosPrelude core() throws Exception {
        return new ProtosCoreBootstrap().bootstrap(CORE);
    }

    private static ProtosTcpConnectionValue connection(
            ProtosPrelude prelude,
            com.guillermomolina.protos.runtime.ProtosActivation activation,
            RecordingBackend backend) {
        return new ProtosTcpConnectionValue(prelude, new Object(), activation, backend);
    }

    private static ProtosFutureValue future(
            Object receiver,
            String selector,
            List<?> supplied,
            com.guillermomolina.protos.runtime.ProtosActivation activation) {
        return (ProtosFutureValue)
                ProtosInvocation.invokeMessage(receiver, selector, supplied, activation);
    }

    private static Long integer(long value) {
        return Long.valueOf(value);
    }

    private static ProtosBytesValue bytes(ProtosPrelude prelude, int... octets) {
        ProtosBytesValue bytes = new ProtosBytesValue(prelude.bytesPrototypeForRuntime());
        for (int octet : octets) {
            bytes.indexedAdd(integer(octet));
        }
        return bytes;
    }

    private static void assertFailedAs(
            ProtosFutureValue future, ProtosPrelude prelude, String prototypeName) {
        assertEquals(ProtosFutureValue.State.FAILED, future.state());
        assertSame(
                prelude.bindings().readLocalSlot(prototypeName).orElseThrow(),
                future.failedError().orElseThrow().parent().orElseThrow());
    }

    private static final class RecordingBackend implements ProtosTcpConnectionFlow.Backend {
        int readStarts;
        int writeStarts;
        int readCancels;
        int writeCancels;
        int shutdownReadStarts;
        int shutdownWriteStarts;
        int closeStarts;
        final Deque<com.guillermomolina.protos.runtime.ProtosByteIoFlow.ReadCompletion>
                readCompletions = new ArrayDeque<>();
        final Deque<com.guillermomolina.protos.runtime.ProtosByteIoFlow.WriteCompletion>
                writeCompletions = new ArrayDeque<>();
        final List<byte[]> writePayloads = new ArrayList<>();
        com.guillermomolina.protos.runtime.ProtosByteIoFlow.ShutdownCompletion
                shutdownReadCompletion;
        com.guillermomolina.protos.runtime.ProtosByteIoFlow.ShutdownCompletion
                shutdownWriteCompletion;
        com.guillermomolina.protos.runtime.ProtosByteIoFlow.ReceiverCompletion closeCompletion;

        @Override
        public com.guillermomolina.protos.runtime.ProtosByteIoFlow.Cancellation read(
                int maxBytes,
                com.guillermomolina.protos.runtime.ProtosByteIoFlow.ReadCompletion completion) {
            readStarts++;
            readCompletions.addLast(completion);
            return () -> readCancels++;
        }

        @Override
        public com.guillermomolina.protos.runtime.ProtosByteIoFlow.Cancellation write(
                byte[] bytes,
                com.guillermomolina.protos.runtime.ProtosByteIoFlow.WriteCompletion completion) {
            writeStarts++;
            writePayloads.add(bytes.clone());
            writeCompletions.addLast(completion);
            return () -> writeCancels++;
        }

        @Override
        public com.guillermomolina.protos.runtime.ProtosByteIoFlow.Cancellation shutdownRead(
                com.guillermomolina.protos.runtime.ProtosByteIoFlow.ShutdownCompletion completion) {
            shutdownReadStarts++;
            shutdownReadCompletion = completion;
            return () -> {};
        }

        @Override
        public com.guillermomolina.protos.runtime.ProtosByteIoFlow.Cancellation shutdownWrite(
                com.guillermomolina.protos.runtime.ProtosByteIoFlow.ShutdownCompletion completion) {
            shutdownWriteStarts++;
            shutdownWriteCompletion = completion;
            return () -> {};
        }

        @Override
        public void close(
                com.guillermomolina.protos.runtime.ProtosByteIoFlow.ReceiverCompletion completion) {
            closeStarts++;
            closeCompletion = completion;
        }
    }
}
