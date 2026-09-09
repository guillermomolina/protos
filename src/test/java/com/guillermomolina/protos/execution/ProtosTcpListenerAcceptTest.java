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

final class ProtosTcpListenerAcceptTest {
    private static final Path CORE=Path.of("protos","lib","core");

    @Test void multipleAcceptsRemainIndependentAndMayCompleteOutOfOrder() throws Exception {
        Fixture x=fixture();
        ProtosFutureValue first=accept(x); ProtosFutureValue second=accept(x);
        assertEquals(2,x.backend.accepts.size());
        assertEquals(ProtosFutureValue.State.PENDING,first.state());
        assertEquals(ProtosFutureValue.State.PENDING,second.state());
        ProtosObjectValue local2=endpoint(x,0x7f000001L,51000), remote2=endpoint(x,0x08080808L,42002);
        ProtosObjectValue local1=endpoint(x,0x7f000001L,51000), remote1=endpoint(x,0x01010101L,42001);
        AtomicInteger release1=new AtomicInteger(), release2=new AtomicInteger();
        x.backend.accepts.get(1).completion.succeeded(new Object(),local2,remote2,new NoOpConnectionBackend(),release2::incrementAndGet);
        x.backend.accepts.get(0).completion.succeeded(new Object(),local1,remote1,new NoOpConnectionBackend(),release1::incrementAndGet);
        ProtosTcpConnectionValue c1=assertInstanceOf(ProtosTcpConnectionValue.class,first.resolvedValue().orElseThrow());
        ProtosTcpConnectionValue c2=assertInstanceOf(ProtosTcpConnectionValue.class,second.resolvedValue().orElseThrow());
        assertNotSame(c1,c2); assertEquals(0,release1.get()); assertEquals(0,release2.get());
        assertEndpointEquals(x,ProtosInvocation.invokeMessage(c1,"localEndpoint",List.of(),x.activation),local1);
        assertEndpointEquals(x,ProtosInvocation.invokeMessage(c1,"remoteEndpoint",List.of(),x.activation),remote1);
    }

    @Test void precommitCancellationAndDuplicateCompletionReleaseCustody() throws Exception {
        Fixture x=fixture(); ProtosFutureValue cancelled=accept(x); AcceptInvocation ci=x.backend.accepts.get(0);
        assertTrue(cancelled.cancelRequest()); assertEquals(ProtosFutureValue.State.CANCELLED,cancelled.state()); assertEquals(1,ci.cancels.get());
        AtomicInteger lateRelease=new AtomicInteger(); ci.completion.succeeded(new Object(),endpoint(x,0x7f000001L,51000),endpoint(x,0x01010101L,43000),new NoOpConnectionBackend(),lateRelease::incrementAndGet);
        assertEquals(1,lateRelease.get());

        ProtosFutureValue completed=accept(x); AcceptInvocation ok=x.backend.accepts.get(1); AtomicInteger heldRelease=new AtomicInteger();
        ok.completion.succeeded(new Object(),endpoint(x,0x7f000001L,51000),endpoint(x,0x01010101L,43001),new NoOpConnectionBackend(),heldRelease::incrementAndGet);
        assertEquals(ProtosFutureValue.State.RESOLVED,completed.state()); assertEquals(0,heldRelease.get());
        AtomicInteger duplicateRelease=new AtomicInteger();
        ok.completion.succeeded(new Object(),endpoint(x,0x7f000001L,51000),endpoint(x,0x01010101L,43002),new NoOpConnectionBackend(),duplicateRelease::incrementAndGet);
        assertEquals(1,duplicateRelease.get());
    }

    @Test void closeCutsOverPendingAcceptAndLateSuccessIsReleased() throws Exception {
        Fixture x=fixture(); ProtosFutureValue accepted=accept(x); AcceptInvocation ai=x.backend.accepts.get(0);
        ProtosFutureValue close=future(x.listener,"close",List.of(),x.activation);
        assertFailedAs(accepted,x.prelude,"IOLifecycleError"); assertEquals(1,ai.cancels.get()); assertEquals(1,x.backend.closeStarts);
        AtomicInteger lateRelease=new AtomicInteger();
        ai.completion.succeeded(new Object(),endpoint(x,0x7f000001L,51000),endpoint(x,0x08080808L,44000),new NoOpConnectionBackend(),lateRelease::incrementAndGet);
        assertEquals(1,lateRelease.get()); assertEquals(ProtosFutureValue.State.PENDING,close.state());
        x.backend.closeCompletion.succeeded(); assertEquals(ProtosFutureValue.State.RESOLVED,close.state());
    }

    @Test void actorTerminationRacingCancellationRegistrationStillCancelsBackend() throws Exception {
        ProtosPrelude p=core(); ProtosActivation a=p.newModuleActivation(); AtomicInteger cancels=new AtomicInteger();
        ProtosTcpListenerFlow.Backend b=new ProtosTcpListenerFlow.Backend(){
            @Override public void close(ProtosTcpListenerFlow.CloseCompletion completion){ completion.succeeded(); }
            @Override public ProtosTcpListenerFlow.Cancellation accept(ProtosTcpListenerFlow.AcceptCompletion completion){ a.executionDomain().actorTerminated(); return cancels::incrementAndGet; }
        };
        ProtosTcpListenerValue listener=new ProtosTcpListenerValue(p,new Object(),a,b,BigInteger.valueOf(51000),ProtosStandardTcpListenerProtocol::materializeAcceptedConnection);
        ProtosFutureValue f=future(listener,"accept",List.of(),a); assertEquals(ProtosFutureValue.State.CANCELLED,f.state()); assertEquals(1,cancels.get());
    }

    @Test void backendFailureExceptionAndInvalidDescriptorMapToIoErrorWithCustody() throws Exception {
        Fixture x=fixture(); ProtosFutureValue failed=accept(x); x.backend.accepts.get(0).completion.failed(); assertFailedAs(failed,x.prelude,"IOError");
        ProtosFutureValue malformed=accept(x); AtomicInteger release=new AtomicInteger();
        x.backend.accepts.get(1).completion.succeeded(new Object(),new ProtosObjectValue(ProtosObjectValue.rootObject()),endpoint(x,0x01010101L,45000),new NoOpConnectionBackend(),release::incrementAndGet);
        assertFailedAs(malformed,x.prelude,"IOError"); assertEquals(1,release.get());

        ProtosTcpListenerFlow.Backend throwing=new ProtosTcpListenerFlow.Backend(){
            @Override public void close(ProtosTcpListenerFlow.CloseCompletion completion){ completion.succeeded(); }
            @Override public ProtosTcpListenerFlow.Cancellation accept(ProtosTcpListenerFlow.AcceptCompletion completion){ throw new IllegalStateException("backend boom"); }
        };
        ProtosTcpListenerValue listener=new ProtosTcpListenerValue(x.prelude,new Object(),x.activation,throwing,BigInteger.valueOf(51000),ProtosStandardTcpListenerProtocol::materializeAcceptedConnection);
        assertFailedAs(future(listener,"accept",List.of(),x.activation),x.prelude,"IOError");
    }

    @Test void acceptRequiresActualAcceptEnabledFamilyAndZeroArguments() throws Exception {
        Fixture x=fixture(); ProtosObjectValue child=new ProtosObjectValue(x.listener);
        assertThrows(ProtosSignalException.class,()->ProtosInvocation.invokeMessage(child,"accept",List.of(),x.activation));
        assertThrows(ProtosSignalException.class,()->ProtosInvocation.invokeMessage(x.listener,"accept",List.of(new ProtosIntegerValue(BigInteger.ONE)),x.activation));
        ProtosTcpListenerValue lifecycleOnly=new ProtosTcpListenerValue(x.prelude,new Object(),x.activation,x.backend,BigInteger.valueOf(51000));
        assertThrows(ProtosSignalException.class,()->ProtosInvocation.invokeMessage(lifecycleOnly,"accept",List.of(),x.activation));
        assertTrue(x.backend.accepts.isEmpty());
    }

    private static Fixture fixture() throws Exception {
        ProtosPrelude p=core(); ProtosActivation a=p.newModuleActivation(); RecordingBackend b=new RecordingBackend();
        ProtosTcpListenerValue l=new ProtosTcpListenerValue(p,new Object(),a,b,BigInteger.valueOf(51000),ProtosStandardTcpListenerProtocol::materializeAcceptedConnection);
        return new Fixture(p,a,l,b);
    }
    private static ProtosPrelude core() throws Exception { return new ProtosCoreBootstrap().bootstrap(CORE); }
    private static ProtosFutureValue accept(Fixture x){ return future(x.listener,"accept",List.of(),x.activation); }
    private static ProtosFutureValue future(Object receiver,String selector,List<?> args,ProtosActivation a){ return assertInstanceOf(ProtosFutureValue.class,ProtosInvocation.invokeMessage(receiver,selector,args,a)); }
    private static ProtosObjectValue endpoint(Fixture x,long bits,long port){
        Object af=x.prelude.bindings().readLocalSlot("IpAddress").orElseThrow(); Object ef=x.prelude.bindings().readLocalSlot("IpEndpoint").orElseThrow();
        ProtosObjectValue addr=(ProtosObjectValue)ProtosInvocation.invoke(af,List.of(integer(4),new ProtosIntegerValue(BigInteger.valueOf(bits))),x.activation);
        return (ProtosObjectValue)ProtosInvocation.invoke(ef,List.of(addr,integer(port)),x.activation);
    }
    private static ProtosIntegerValue integer(long v){ return new ProtosIntegerValue(BigInteger.valueOf(v)); }
    private static void assertEndpointEquals(Fixture x,Object actual,Object expected){ assertSame(ProtosBooleanValue.TRUE,ProtosInvocation.invokeMessage(actual,"==",List.of(expected),x.activation)); }
    private static void assertFailedAs(ProtosFutureValue f,ProtosPrelude p,String name){ assertEquals(ProtosFutureValue.State.FAILED,f.state()); assertSame(p.bindings().readLocalSlot(name).orElseThrow(),f.failedError().orElseThrow().parent().orElseThrow()); }

    private record Fixture(ProtosPrelude prelude,ProtosActivation activation,ProtosTcpListenerValue listener,RecordingBackend backend) {}
    private static final class RecordingBackend implements ProtosTcpListenerFlow.Backend {
        final List<AcceptInvocation> accepts=new ArrayList<>(); int closeStarts; ProtosTcpListenerFlow.CloseCompletion closeCompletion;
        @Override public void close(ProtosTcpListenerFlow.CloseCompletion completion){ closeStarts++; closeCompletion=completion; }
        @Override public ProtosTcpListenerFlow.Cancellation accept(ProtosTcpListenerFlow.AcceptCompletion completion){ AcceptInvocation i=new AcceptInvocation(completion); accepts.add(i); return i.cancels::incrementAndGet; }
    }
    private static final class AcceptInvocation { final ProtosTcpListenerFlow.AcceptCompletion completion; final AtomicInteger cancels=new AtomicInteger(); AcceptInvocation(ProtosTcpListenerFlow.AcceptCompletion c){completion=c;} }
    private static final class NoOpConnectionBackend implements ProtosTcpConnectionFlow.Backend {
        @Override public ProtosByteIoFlow.Cancellation read(int maxBytes,ProtosByteIoFlow.ReadCompletion completion){ return ()->{}; }
        @Override public ProtosByteIoFlow.Cancellation write(byte[] bytes,ProtosByteIoFlow.WriteCompletion completion){ return ()->{}; }
        @Override public ProtosByteIoFlow.Cancellation shutdownRead(ProtosByteIoFlow.ShutdownCompletion completion){ return ()->{}; }
        @Override public ProtosByteIoFlow.Cancellation shutdownWrite(ProtosByteIoFlow.ShutdownCompletion completion){ return ()->{}; }
        @Override public void close(ProtosByteIoFlow.ReceiverCompletion completion){ completion.succeeded(); }
    }
}
