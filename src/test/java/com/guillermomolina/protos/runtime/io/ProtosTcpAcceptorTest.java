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

package com.guillermomolina.protos.runtime.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.execution.ProtosCoreBootstrap;
import com.guillermomolina.protos.execution.ProtosInvocation;
import com.guillermomolina.protos.execution.ProtosStandardTcpListenerProtocol;
import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosFutureValue;
import com.guillermomolina.protos.runtime.ProtosIntegerValue;
import com.guillermomolina.protos.runtime.ProtosIoLifecycle;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class ProtosTcpAcceptorTest {
    private static final Path CORE = Path.of("protos", "lib", "core");

    @Test
    void acquisitionsAreDistinctOpenSlotlessCanonicalChildren() throws Exception {
        Fixture x = fixture();
        ProtosTcpAcceptorValue first = acquire(x);
        ProtosTcpAcceptorValue second = acquire(x);

        assertNotSame(first, second);
        for (ProtosTcpAcceptorValue acceptor : List.of(first, second)) {
            assertSame(x.prelude.tcpAcceptorPrototypeForRuntime(), acceptor.parent().orElseThrow());
            assertTrue(acceptor.isOpen());
            assertTrue(acceptor.localSlotsSnapshot().isEmpty());
            assertSame(x.listenerFlow(), acceptor.flowForRuntime());
        }

        first.createLocalSlot("label", new ProtosIntegerValue(3));
        assertTrue(first.readLocalSlot("label").isPresent());
        assertTrue(second.localSlotsSnapshot().isEmpty());
        assertEquals(0, x.backend.accepts.get());
    }

    @Test
    void structuralCloseNeitherClosesListenerNorRevokesAcceptAuthority() throws Exception {
        Fixture x = fixture();
        ProtosTcpAcceptorValue acceptor = acquire(x);

        ProtosInvocation.invokeMessage(acceptor, "close", List.of(), x.activation);
        assertFalse(acceptor.isOpen());
        assertEquals(ProtosIoLifecycle.State.OPEN, x.listener.lifecycleForRuntime().state());
        assertEquals(0, x.backend.closes.get());

        ProtosFutureValue accepted = accept(acceptor, x.activation);
        assertEquals(ProtosFutureValue.State.PENDING, accepted.state());
        assertEquals(1, x.backend.accepts.get());
    }

    @Test
    void ordinaryDescendantsAndForeignDomainsHaveNoAcceptAuthority() throws Exception {
        Fixture x = fixture();
        ProtosTcpAcceptorValue acceptor = acquire(x);
        ProtosObjectValue protocolChild = new ProtosObjectValue(x.prelude.tcpAcceptorPrototypeForRuntime());
        ProtosObjectValue capabilityChild = new ProtosObjectValue(acceptor);
        ProtosActivation foreign = x.prelude.newModuleActivation();

        assertThrows(ProtosSignalException.class,
                () -> ProtosInvocation.invokeMessage(protocolChild, "accept", List.of(), x.activation));
        assertThrows(ProtosSignalException.class,
                () -> ProtosInvocation.invokeMessage(capabilityChild, "accept", List.of(), x.activation));
        assertThrows(ProtosSignalException.class,
                () -> ProtosInvocation.invokeMessage(acceptor, "accept", List.of(new ProtosIntegerValue(1)), x.activation));
        assertThrows(ProtosSignalException.class,
                () -> ProtosInvocation.invokeMessage(acceptor, "accept", List.of(), foreign));
        assertFalse(acceptor.ownedByForRuntime(foreign));
        assertThrows(IllegalArgumentException.class, () -> acceptor.acceptForRuntime(foreign));
        assertEquals(0, x.backend.accepts.get());
    }

    @Test
    void postCutoverAcquisitionSignalsFreshLifecycleErrorsWithoutFuture() throws Exception {
        Fixture x = fixture();
        ProtosInvocation.invokeMessage(x.listener, "close", List.of(), x.activation);

        ProtosSignalException first = assertThrows(ProtosSignalException.class,
                () -> ProtosInvocation.invokeMessage(x.listener, "acceptCapability", List.of(), x.activation));
        ProtosSignalException second = assertThrows(ProtosSignalException.class,
                () -> ProtosInvocation.invokeMessage(x.listener, "acceptCapability", List.of(), x.activation));
        assertLifecycleError(x, first.error());
        assertLifecycleError(x, second.error());
        assertNotSame(first.error(), second.error());
        assertEquals(0, x.backend.accepts.get());
    }

    @Test
    void postCutoverAcceptsAnswerDistinctFailedFuturesWithoutBackend() throws Exception {
        Fixture x = fixture();
        ProtosTcpAcceptorValue acceptor = acquire(x);
        ProtosInvocation.invokeMessage(x.listener, "close", List.of(), x.activation);

        ProtosFutureValue first = accept(acceptor, x.activation);
        ProtosFutureValue second = accept(acceptor, x.activation);
        assertNotSame(first, second);
        for (ProtosFutureValue future : List.of(first, second)) {
            assertEquals(ProtosFutureValue.State.FAILED, future.state());
            assertLifecycleError(x, future.failedError().orElseThrow());
        }
        assertNotSame(first.failedError().orElseThrow(), second.failedError().orElseThrow());
        assertEquals(0, x.backend.accepts.get());
    }

    private static void assertLifecycleError(Fixture x, ProtosObjectValue error) {
        assertSame(
                x.prelude.bindings().readLocalSlot("IOLifecycleError").orElseThrow(),
                error.parent().orElseThrow());
    }

    private static ProtosTcpAcceptorValue acquire(Fixture x) {
        return assertInstanceOf(
                ProtosTcpAcceptorValue.class,
                ProtosInvocation.invokeMessage(x.listener, "acceptCapability", List.of(), x.activation));
    }

    private static ProtosFutureValue accept(ProtosTcpAcceptorValue acceptor, ProtosActivation activation) {
        return assertInstanceOf(
                ProtosFutureValue.class,
                ProtosInvocation.invokeMessage(acceptor, "accept", List.of(), activation));
    }

    private static Fixture fixture() throws Exception {
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
        ProtosActivation activation = prelude.newModuleActivation();
        CountingBackend backend = new CountingBackend();
        ProtosTcpListenerValue listener =
                new ProtosTcpListenerValue(
                        prelude,
                        new Object(),
                        activation,
                        backend,
                        51000,
                        ProtosStandardTcpListenerProtocol::materializeAcceptedConnection);
        return new Fixture(prelude, activation, listener, backend);
    }

    private record Fixture(
            ProtosPrelude prelude,
            ProtosActivation activation,
            ProtosTcpListenerValue listener,
            CountingBackend backend) {
        ProtosTcpListenerFlow listenerFlow() {
            return acquire(this).flowForRuntime();
        }
    }

    private static final class CountingBackend implements ProtosTcpListenerFlow.Backend {
        final AtomicInteger accepts = new AtomicInteger();
        final AtomicInteger closes = new AtomicInteger();

        @Override
        public void close(ProtosTcpListenerFlow.CloseCompletion completion) {
            closes.incrementAndGet();
        }

        @Override
        public ProtosTcpListenerFlow.Cancellation accept(ProtosTcpListenerFlow.AcceptCompletion completion) {
            accepts.incrementAndGet();
            return () -> {};
        }
    }
}
