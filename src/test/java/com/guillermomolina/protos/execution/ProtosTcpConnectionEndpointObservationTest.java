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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosByteIoFlow;
import com.guillermomolina.protos.runtime.ProtosIntegerValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.guillermomolina.protos.runtime.ProtosTcpConnectionFlow;
import com.guillermomolina.protos.runtime.ProtosTcpConnectionValue;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ProtosTcpConnectionEndpointObservationTest {
    private static final Path CORE = Path.of("protos", "lib", "core");

    @Test
    void endpointSelectorsExposeRecognizedLogicalSnapshotsWithoutBackendWork() throws Exception {
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
        ProtosActivation activation = prelude.newModuleActivation();
        ProtosObjectValue local = endpoint(prelude, activation, 0x7f000001L, 41000);
        ProtosObjectValue remote = endpoint(prelude, activation, 0x01010101L, 443);
        ProtosObjectValue equalRemote = endpoint(prelude, activation, 0x01010101L, 443);
        RecordingBackend backend = new RecordingBackend();
        ProtosTcpConnectionValue connection =
                new ProtosTcpConnectionValue(
                        prelude, new Object(), activation, backend, local, remote);

        Object observedLocal =
                ProtosInvocation.invokeMessage(connection, "localEndpoint", List.of(), activation);
        Object observedRemote =
                ProtosInvocation.invokeMessage(connection, "remoteEndpoint", List.of(), activation);

        assertRecognizedEndpoint(prelude, observedLocal);
        assertRecognizedEndpoint(prelude, observedRemote);
        assertSame(
                ProtosBooleanValue.TRUE,
                ProtosInvocation.invokeMessage(
                        observedRemote, "==", List.of(equalRemote), activation));
        assertEquals(0, backend.calls);
    }

    @Test
    void endpointSelectorsRejectWrongReceiverArityAndMissingSnapshotsBeforeBackendWork()
            throws Exception {
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
        ProtosActivation activation = prelude.newModuleActivation();
        RecordingBackend backend = new RecordingBackend();
        ProtosObjectValue local = endpoint(prelude, activation, 0x7f000001L, 41000);
        ProtosObjectValue remote = endpoint(prelude, activation, 0x01010101L, 443);
        ProtosTcpConnectionValue connection =
                new ProtosTcpConnectionValue(
                        prelude, new Object(), activation, backend, local, remote);
        ProtosObjectValue descendant = new ProtosObjectValue(connection);
        ProtosTcpConnectionValue endpointless =
                new ProtosTcpConnectionValue(prelude, new Object(), activation, backend);

        assertThrows(
                ProtosSignalException.class,
                () -> ProtosInvocation.invokeMessage(
                        descendant, "localEndpoint", List.of(), activation));
        assertThrows(
                ProtosSignalException.class,
                () -> ProtosInvocation.invokeMessage(
                        connection, "remoteEndpoint", List.of(integer(1)), activation));
        assertThrows(
                ProtosSignalException.class,
                () -> ProtosInvocation.invokeMessage(
                        endpointless, "localEndpoint", List.of(), activation));
        assertEquals(0, backend.calls);
    }

    private static ProtosObjectValue endpoint(
            ProtosPrelude prelude, ProtosActivation activation, long bits, long port) {
        Object addressFactory = prelude.bindings().readLocalSlot("IpAddress").orElseThrow();
        ProtosObjectValue address =
                (ProtosObjectValue)
                        ProtosInvocation.invoke(
                                addressFactory,
                                List.of(integer(4), new ProtosIntegerValue(BigInteger.valueOf(bits))),
                                activation);
        Object endpointFactory = prelude.bindings().readLocalSlot("IpEndpoint").orElseThrow();
        return (ProtosObjectValue)
                ProtosInvocation.invoke(
                        endpointFactory, List.of(address, integer(port)), activation);
    }

    private static void assertRecognizedEndpoint(ProtosPrelude prelude, Object value) {
        ProtosObjectValue endpoint = (ProtosObjectValue) value;
        assertTrue(endpoint.isFrozen());
        assertSame(
                prelude.bindings().readLocalSlot("IpEndpoint").orElseThrow(),
                endpoint.parent().orElseThrow());
        assertEquals(java.util.Set.of("address", "port"), endpoint.localSlotsSnapshot().keySet());
    }

    private static ProtosIntegerValue integer(long value) {
        return new ProtosIntegerValue(BigInteger.valueOf(value));
    }

    private static final class RecordingBackend implements ProtosTcpConnectionFlow.Backend {
        int calls;
        @Override public ProtosByteIoFlow.Cancellation read(int n, ProtosByteIoFlow.ReadCompletion c) { calls++; return () -> {}; }
        @Override public ProtosByteIoFlow.Cancellation write(byte[] b, ProtosByteIoFlow.WriteCompletion c) { calls++; return () -> {}; }
        @Override public ProtosByteIoFlow.Cancellation shutdownRead(ProtosByteIoFlow.ShutdownCompletion c) { calls++; return () -> {}; }
        @Override public ProtosByteIoFlow.Cancellation shutdownWrite(ProtosByteIoFlow.ShutdownCompletion c) { calls++; return () -> {}; }
        @Override public void close(ProtosByteIoFlow.ReceiverCompletion c) { calls++; }
    }
}
