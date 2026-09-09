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

final class ProtosNetworkListenAcquisitionTest {
    private static final Path CORE=Path.of("protos","lib","core");

    @Test void exactRequestIsCapturedBeforeBackendEffectAndMaterializesAcceptEnabledListener() throws Exception {
        Fixture x=fixture(); ProtosObjectValue address=ipAddress(x,4,0x7f000001L);
        ProtosObjectValue request=request(integer(4),address,integer(51000));
        ProtosFutureValue future=listen(x,request); assertEquals(1,x.backend.invocations.size());
        ListenInvocation invocation=x.backend.invocations.get(0);
        assertEquals(4,invocation.request.ipVersion()); assertSame(address,invocation.request.addressConstraint()); assertEquals(BigInteger.valueOf(51000),invocation.request.portConstraint());
        request.createLocalSlot("afterDispatch",integer(1));
        assertEquals(BigInteger.valueOf(51000),invocation.request.portConstraint());
        RecordingListenerBackend listenerBackend=new RecordingListenerBackend(); AtomicInteger release=new AtomicInteger();
        invocation.completion.succeeded(new Object(),BigInteger.valueOf(51000),listenerBackend,release::incrementAndGet);
        ProtosTcpListenerValue listener=assertInstanceOf(ProtosTcpListenerValue.class,future.resolvedValue().orElseThrow());
        assertEquals(0,release.get()); assertTrue(listener.hasAcceptForRuntime());
        assertEquals(BigInteger.valueOf(51000),assertInstanceOf(ProtosIntegerValue.class,ProtosInvocation.invokeMessage(listener,"localPort",List.of(),x.activation)).value());
        ProtosFutureValue accept=assertInstanceOf(ProtosFutureValue.class,ProtosInvocation.invokeMessage(listener,"accept",List.of(),x.activation));
        assertEquals(ProtosFutureValue.State.PENDING,accept.state()); assertEquals(1,listenerBackend.accepts.size());
    }

    @Test void canonicalNullAddressAndPortRequestBackendSelectionWithoutMagicEndpointValues() throws Exception {
        Fixture x=fixture(); ProtosObjectValue request=request(integer(6),ProtosNullValue.INSTANCE,ProtosNullValue.INSTANCE);
        ProtosFutureValue future=listen(x,request); ListenInvocation invocation=x.backend.invocations.get(0);
        assertEquals(6,invocation.request.ipVersion()); assertNull(invocation.request.addressConstraint()); assertNull(invocation.request.portConstraint());
        invocation.completion.succeeded(new Object(),BigInteger.valueOf(54000),new RecordingListenerBackend(),()->{});
        ProtosTcpListenerValue listener=assertInstanceOf(ProtosTcpListenerValue.class,future.resolvedValue().orElseThrow());
        assertEquals(BigInteger.valueOf(54000),listener.localPortForRuntime());
    }

    @Test void invalidShapeVersionAddressOrPortFailsBeforeBackendEffect() throws Exception {
        Fixture x=fixture();
        ProtosObjectValue extra=request(integer(4),ProtosNullValue.INSTANCE,integer(51000)); extra.createLocalSlot("backlog",integer(5));
        assertFailedAs(listen(x,extra),x.prelude,"InvalidIOArgument");
        ProtosObjectValue parent=request(integer(4),ProtosNullValue.INSTANCE,integer(51000)); ProtosObjectValue delegated=new ProtosObjectValue(parent);
        assertFailedAs(listen(x,delegated),x.prelude,"InvalidIOArgument");
        ProtosObjectValue missing=new ProtosObjectValue(ProtosObjectValue.rootObject()); missing.createLocalSlot("ipVersion",integer(4)); missing.createLocalSlot("address",ProtosNullValue.INSTANCE);
        assertFailedAs(listen(x,missing),x.prelude,"InvalidIOArgument");
        assertFailedAs(listen(x,request(integer(5),ProtosNullValue.INSTANCE,integer(51000))),x.prelude,"InvalidIOArgument");
        assertFailedAs(listen(x,request(integer(4),ipAddress(x,6,BigInteger.ONE),integer(51000))),x.prelude,"InvalidIOArgument");
        assertFailedAs(listen(x,request(integer(4),ProtosNullValue.INSTANCE,integer(0))),x.prelude,"InvalidIOArgument");
        assertEquals(0,x.backend.invocations.size());
    }

    @Test void wrongArityOrNonAuthorityReceiverFailsBeforeBackendEffect() throws Exception {
        Fixture x=fixture();
        assertFailedAs(future(x.network,"listenTcp",List.of(),x.activation),x.prelude,"InvalidIOArgument");
        ProtosObjectValue child=new ProtosObjectValue(x.prelude.networkPrototype());
        assertFailedAs(future(child,"listenTcp",List.of(request(integer(4),ProtosNullValue.INSTANCE,integer(51000))),x.activation),x.prelude,"InvalidIOArgument");
        assertEquals(0,x.backend.invocations.size());
    }

    @Test void nonListenAuthorityTargetFailsPortablyWithoutSelectingBackend() throws Exception {
        ProtosPrelude p=core(); ProtosActivation a=p.newModuleActivation(); ProtosNetworkCapabilityValue network=new ProtosNetworkCapabilityValue(p,new Object());
        assertFailedAs(future(network,"listenTcp",List.of(request(integer(4),ProtosNullValue.INSTANCE,integer(51000))),a),p,"IOError");
    }

    @Test void precommitCancellationAndDuplicateOrLateListenersReleaseCustody() throws Exception {
        Fixture x=fixture(); ProtosObjectValue req=request(integer(4),ProtosNullValue.INSTANCE,integer(51000));
        ProtosFutureValue cancelled=listen(x,req); ListenInvocation ci=x.backend.invocations.get(0); assertTrue(cancelled.cancelRequest()); assertEquals(1,ci.cancels.get());
        AtomicInteger lateRelease=new AtomicInteger(); ci.completion.succeeded(new Object(),BigInteger.valueOf(51000),new RecordingListenerBackend(),lateRelease::incrementAndGet); assertEquals(1,lateRelease.get());
        ProtosFutureValue completed=listen(x,req); ListenInvocation ok=x.backend.invocations.get(1); AtomicInteger heldRelease=new AtomicInteger();
        ok.completion.succeeded(new Object(),BigInteger.valueOf(51000),new RecordingListenerBackend(),heldRelease::incrementAndGet); assertEquals(ProtosFutureValue.State.RESOLVED,completed.state()); assertEquals(0,heldRelease.get());
        AtomicInteger duplicateRelease=new AtomicInteger(); ok.completion.succeeded(new Object(),BigInteger.valueOf(51000),new RecordingListenerBackend(),duplicateRelease::incrementAndGet); assertEquals(1,duplicateRelease.get());
    }

    @Test void actorTerminationRacingCancellationRegistrationStillCancelsBackend() throws Exception {
        ProtosPrelude p=core(); ProtosActivation a=p.newModuleActivation(); AtomicInteger cancels=new AtomicInteger();
        ProtosNetworkListenFlow.Backend backend=(request,completion)->{ a.executionDomain().actorTerminated(); return cancels::incrementAndGet; };
        ProtosNetworkCapabilityValue network=new ProtosNetworkCapabilityValue(p,backend);
        ProtosFutureValue f=future(network,"listenTcp",List.of(request(integer(4),ProtosNullValue.INSTANCE,integer(51000))),a);
        assertEquals(ProtosFutureValue.State.CANCELLED,f.state()); assertEquals(1,cancels.get());
    }

    @Test void backendFailureMalformedDescriptorAndFixedPortMismatchMapToIoErrorWithCustody() throws Exception {
        Fixture x=fixture(); ProtosObjectValue req=request(integer(4),ProtosNullValue.INSTANCE,integer(51000));
        ProtosFutureValue failed=listen(x,req); x.backend.invocations.get(0).completion.failed(); assertFailedAs(failed,x.prelude,"IOError");
        ProtosFutureValue badPort=listen(x,req); AtomicInteger release1=new AtomicInteger(); x.backend.invocations.get(1).completion.succeeded(new Object(),BigInteger.ZERO,new RecordingListenerBackend(),release1::incrementAndGet); assertFailedAs(badPort,x.prelude,"IOError"); assertEquals(1,release1.get());
        ProtosFutureValue mismatch=listen(x,req); AtomicInteger release2=new AtomicInteger(); x.backend.invocations.get(2).completion.succeeded(new Object(),BigInteger.valueOf(51001),new RecordingListenerBackend(),release2::incrementAndGet); assertFailedAs(mismatch,x.prelude,"IOError"); assertEquals(1,release2.get());
        ProtosNetworkListenFlow.Backend throwing=(request,completion)->{ throw new IllegalStateException("backend boom"); };
        ProtosNetworkCapabilityValue network=new ProtosNetworkCapabilityValue(x.prelude,throwing); assertFailedAs(future(network,"listenTcp",List.of(req),x.activation),x.prelude,"IOError");
    }

    private static Fixture fixture() throws Exception { ProtosPrelude p=core(); ProtosActivation a=p.newModuleActivation(); RecordingNetworkBackend b=new RecordingNetworkBackend(); return new Fixture(p,a,b,new ProtosNetworkCapabilityValue(p,b)); }
    private static ProtosPrelude core() throws Exception { return new ProtosCoreBootstrap().bootstrap(CORE); }
    private static ProtosFutureValue listen(Fixture x,ProtosObjectValue r){ return future(x.network,"listenTcp",List.of(r),x.activation); }
    private static ProtosFutureValue future(Object receiver,String selector,List<?> args,ProtosActivation a){ return assertInstanceOf(ProtosFutureValue.class,ProtosInvocation.invokeMessage(receiver,selector,args,a)); }
    private static ProtosObjectValue request(Object version,Object address,Object port){ ProtosObjectValue r=new ProtosObjectValue(ProtosObjectValue.rootObject()); r.createLocalSlot("ipVersion",version); r.createLocalSlot("address",address); r.createLocalSlot("port",port); return r; }
    private static ProtosObjectValue ipAddress(Fixture x,int version,long bits){ return ipAddress(x,version,BigInteger.valueOf(bits)); }
    private static ProtosObjectValue ipAddress(Fixture x,int version,BigInteger bits){ Object factory=x.prelude.bindings().readLocalSlot("IpAddress").orElseThrow(); return (ProtosObjectValue)ProtosInvocation.invoke(factory,List.of(integer(version),new ProtosIntegerValue(bits)),x.activation); }
    private static ProtosIntegerValue integer(long v){ return new ProtosIntegerValue(BigInteger.valueOf(v)); }
    private static void assertFailedAs(ProtosFutureValue f,ProtosPrelude p,String name){ assertEquals(ProtosFutureValue.State.FAILED,f.state()); assertSame(p.bindings().readLocalSlot(name).orElseThrow(),f.failedError().orElseThrow().parent().orElseThrow()); }

    private record Fixture(ProtosPrelude prelude,ProtosActivation activation,RecordingNetworkBackend backend,ProtosNetworkCapabilityValue network) {}
    private static final class RecordingNetworkBackend implements ProtosNetworkListenFlow.Backend {
        final List<ListenInvocation> invocations=new ArrayList<>();
        @Override public ProtosNetworkListenFlow.Cancellation listen(ProtosNetworkListenFlow.ListenRequest request,ProtosNetworkListenFlow.ListenCompletion completion){ ListenInvocation i=new ListenInvocation(request,completion); invocations.add(i); return i.cancels::incrementAndGet; }
    }
    private static final class ListenInvocation { final ProtosNetworkListenFlow.ListenRequest request; final ProtosNetworkListenFlow.ListenCompletion completion; final AtomicInteger cancels=new AtomicInteger(); ListenInvocation(ProtosNetworkListenFlow.ListenRequest r,ProtosNetworkListenFlow.ListenCompletion c){request=r;completion=c;} }
    private static final class RecordingListenerBackend implements ProtosTcpListenerFlow.Backend {
        final List<ProtosTcpListenerFlow.AcceptCompletion> accepts=new ArrayList<>();
        @Override public void close(ProtosTcpListenerFlow.CloseCompletion completion){ completion.succeeded(); }
        @Override public ProtosTcpListenerFlow.Cancellation accept(ProtosTcpListenerFlow.AcceptCompletion completion){ accepts.add(completion); return ()->{}; }
    }
}
