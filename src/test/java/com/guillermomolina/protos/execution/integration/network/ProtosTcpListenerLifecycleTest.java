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

import com.guillermomolina.protos.runtime.io.ProtosTcpListenerFlow;
import com.guillermomolina.protos.runtime.io.ProtosTcpListenerValue;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ProtosTcpListenerLifecycleTest {
    private static final Path CORE=Path.of("protos","lib","core");

    @Test void localPortIsSynchronousSnapshotWithoutBackendEffect() throws Exception {
        Fixture x=fixture(43210); Object value=ProtosInvocation.invokeMessage(x.listener,"localPort",List.of(),x.activation);
        assertEquals(String.valueOf("43210"), com.guillermomolina.protos.runtime.ProtosNumericValueSupport.integerDecimalText(value));
        assertEquals(0,x.backend.closeStarts); assertTrue(x.listener.isOpen());
    }

    @Test void closeUsesOneSharedLifecycleAndDoesNotStructurallyCloseObject() throws Exception {
        Fixture x=fixture(51000); ProtosFutureValue first=assertInstanceOf(ProtosFutureValue.class,ProtosInvocation.invokeMessage(x.listener,"close",List.of(),x.activation));
        assertEquals(1,x.backend.closeStarts); assertEquals(ProtosFutureValue.State.PENDING,first.state()); assertTrue(x.listener.isOpen());
        x.backend.completion.succeeded(); assertEquals(ProtosFutureValue.State.PENDING,first.state());
        assertTrue(x.activation.executionDomain().dispatchOne()); assertEquals(ProtosFutureValue.State.RESOLVED,first.state()); assertSame(x.listener,first.resolvedValue().orElseThrow()); assertTrue(x.listener.isOpen());
        ProtosFutureValue second=assertInstanceOf(ProtosFutureValue.class,ProtosInvocation.invokeMessage(x.listener,"close",List.of(),x.activation));
        assertNotSame(first,second); assertEquals(ProtosFutureValue.State.RESOLVED,second.state()); assertSame(x.listener,second.resolvedValue().orElseThrow()); assertEquals(1,x.backend.closeStarts);
    }

    @Test void closeBackendFailureMapsToPortableIoError() throws Exception {
        Fixture x=fixture(52000); ProtosFutureValue future=assertInstanceOf(ProtosFutureValue.class,ProtosInvocation.invokeMessage(x.listener,"close",List.of(),x.activation));
        // A callback reported after close() returned is deferred to the listener owner's domain.
        x.backend.completion.failed(); assertEquals(ProtosFutureValue.State.PENDING,future.state()); assertTrue(future.failedError().isEmpty());
        assertTrue(x.activation.executionDomain().dispatchOne()); assertEquals(ProtosFutureValue.State.FAILED,future.state());
        assertSame(x.prelude.bindings().readLocalSlot("IOError").orElseThrow(),future.failedError().orElseThrow().parent().orElseThrow());
    }

    @Test void synchronousOwnerCloseCompletionSettlesDirectly() throws Exception {
        for (boolean succeed : new boolean[] {true, false}) {
            Fixture x=fixture(52100); x.backend.synchronousOutcome=succeed ? Boolean.TRUE : Boolean.FALSE;
            ProtosFutureValue future=assertInstanceOf(ProtosFutureValue.class,ProtosInvocation.invokeMessage(x.listener,"close",List.of(),x.activation));
            if (succeed) { assertEquals(ProtosFutureValue.State.RESOLVED,future.state()); assertSame(x.listener,future.resolvedValue().orElseThrow()); }
            else { assertEquals(ProtosFutureValue.State.FAILED,future.state()); assertSame(x.prelude.bindings().readLocalSlot("IOError").orElseThrow(),future.failedError().orElseThrow().parent().orElseThrow()); }
            assertFalse(x.activation.executionDomain().dispatchOne()); assertEquals(1,x.backend.closeStarts);
        }
    }

    @Test void hostThreadCloseCompletionIsPublishedOnlyByOwnerDispatch() throws Exception {
        for (boolean succeed : new boolean[] {true, false}) {
            Fixture x=fixture(52200);
            ProtosFutureValue future=assertInstanceOf(ProtosFutureValue.class,ProtosInvocation.invokeMessage(x.listener,"close",List.of(),x.activation));
            java.util.concurrent.CompletableFuture.runAsync(() -> { if (succeed) x.backend.completion.succeeded(); else x.backend.completion.failed(); }).get();
            assertEquals(ProtosFutureValue.State.PENDING,future.state()); assertTrue(future.failedError().isEmpty());
            assertTrue(x.activation.executionDomain().dispatchOne());
            if (succeed) { assertEquals(ProtosFutureValue.State.RESOLVED,future.state()); assertSame(x.listener,future.resolvedValue().orElseThrow()); }
            else { assertEquals(ProtosFutureValue.State.FAILED,future.state()); assertSame(x.prelude.bindings().readLocalSlot("IOError").orElseThrow(),future.failedError().orElseThrow().parent().orElseThrow()); }
            assertFalse(x.activation.executionDomain().dispatchOne()); assertEquals(1,x.backend.closeStarts);
        }
    }

    @Test void inheritedBehaviorCannotManufactureListenerAuthority() throws Exception {
        Fixture x=fixture(53000); ProtosObjectValue child=new ProtosObjectValue(x.listener);
        assertThrows(ProtosSignalException.class,()->ProtosInvocation.invokeMessage(child,"localPort",List.of(),x.activation));
        assertThrows(ProtosSignalException.class,()->ProtosInvocation.invokeMessage(child,"close",List.of(),x.activation));
        assertEquals(0,x.backend.closeStarts);
        ProtosTcpListenerValue inert=new ProtosTcpListenerValue(x.prelude,new Object());
        assertThrows(ProtosSignalException.class,()->ProtosInvocation.invokeMessage(inert,"localPort",List.of(),x.activation));
        assertThrows(ProtosSignalException.class,()->ProtosInvocation.invokeMessage(inert,"close",List.of(),x.activation));
    }

    @Test void localPortAndCloseRequireZeroArguments() throws Exception {
        Fixture x=fixture(54000); Object extra=new ProtosIntegerValue(1L);
        assertThrows(ProtosSignalException.class,()->ProtosInvocation.invokeMessage(x.listener,"localPort",List.of(extra),x.activation));
        assertThrows(ProtosSignalException.class,()->ProtosInvocation.invokeMessage(x.listener,"close",List.of(extra),x.activation));
        assertEquals(0,x.backend.closeStarts);
    }

    private static Fixture fixture(int port) throws Exception {
        ProtosPrelude p=new ProtosCoreBootstrap().bootstrap(CORE); ProtosActivation a=p.newModuleActivation(); RecordingBackend b=new RecordingBackend();
        ProtosTcpListenerValue l=new ProtosTcpListenerValue(p,new Object(),a,b,port); return new Fixture(p,a,l,b);
    }
    private record Fixture(ProtosPrelude prelude,ProtosActivation activation,ProtosTcpListenerValue listener,RecordingBackend backend) {}
    private static final class RecordingBackend implements ProtosTcpListenerFlow.Backend {
        int closeStarts; ProtosTcpListenerFlow.CloseCompletion completion; Boolean synchronousOutcome;
        @Override public void close(ProtosTcpListenerFlow.CloseCompletion completion){
            closeStarts++; this.completion=completion;
            if (synchronousOutcome != null) { if (synchronousOutcome) completion.succeeded(); else completion.failed(); }
        }
    }
}
