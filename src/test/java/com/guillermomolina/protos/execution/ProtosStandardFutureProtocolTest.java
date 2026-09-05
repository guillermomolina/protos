/* APL-1.0 licensed work; see LICENSE.TXT. */
package com.guillermomolina.protos.execution;

import static org.junit.jupiter.api.Assertions.*;
import com.guillermomolina.protos.runtime.*;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ProtosStandardFutureProtocolTest {
    private static ProtosPrelude core() throws Exception { return new ProtosCoreBootstrap().bootstrap(Path.of("protos","lib","core")); }
    private static Object eval(ProtosPrelude p, ProtosActivation a, String source){return new ProtosSourceCompiler().compile(source).call(a);}

    @Test void closureFutureSchedulesAndPreservesIdentityNullAndFutureIdentity() throws Exception {
        var p=core(); var a=p.newModuleActivation();
        var f=(ProtosFutureValue)eval(p,a,"(() => null).future()");
        assertEquals(ProtosFutureValue.State.PENDING,f.state());
        assertSame(p.futurePrototype(),f.parent().orElseThrow());
        a.executionDomain().dispatchUntilIdle();
        assertEquals(ProtosFutureValue.State.RESOLVED,f.state());
        assertSame(ProtosNullValue.INSTANCE,f.resolvedValue().orElseThrow());
        var f2=(ProtosFutureValue)eval(p,a,"(() => null).future()"); assertNotSame(f,f2);
    }

    @Test void valueSuspendsRealEvaluatorLetsPeerProgressAndResumesExactlyOnce() throws Exception {
        var p=core(); var domain=new ProtosActorExecutionDomain(); var a=p.newModuleActivation(new ProtosActorModuleState(),null,p.newExecutionContext(),domain);
        AtomicInteger before=new AtomicInteger(),after=new AtomicInteger(),peer=new AtomicInteger();
        a.context().createLocalSlot("before",ProtosClosureValue.nativeClosure((x,y)->{before.incrementAndGet();return ProtosNullValue.INSTANCE;}));
        a.context().createLocalSlot("after",ProtosClosureValue.nativeClosure((x,y)->{after.incrementAndGet();return y.get(0);}));
        ProtosFutureValue pending=new ProtosFutureValue(p.futurePrototype(),domain); a.context().createLocalSlot("f",pending);
        var target=new ProtosSourceCompiler().compile("before()\nx: f.value()\nafter(x)");
        var consumer=domain.createTask(null,t->t.executeProtos(target,a));
        domain.createTask(null,t->{peer.incrementAndGet();pending.resolve(new ProtosIntegerValue(BigInteger.valueOf(7)),a);t.complete(ProtosNullValue.INSTANCE);});
        domain.dispatchOne(); assertEquals(ProtosTask.State.SUSPENDED,consumer.state()); assertEquals(1,before.get());
        domain.dispatchOne(); assertEquals(1,peer.get());
        domain.dispatchOne(); assertEquals(ProtosTask.State.COMPLETED,consumer.state()); assertEquals(1,before.get()); assertEquals(1,after.get());
    }

    @Test void cancellationOfWaitingTaskDoesNotCancelObservedFuture() throws Exception {
        var p=core(); var d=new ProtosActorExecutionDomain(); var a=p.newModuleActivation(new ProtosActorModuleState(),null,p.newExecutionContext(),d);
        var f=new ProtosFutureValue(p.futurePrototype(),d); a.context().createLocalSlot("f",f);
        var target=new ProtosSourceCompiler().compile("f.value()"); var t=d.createTask(null,x->x.executeProtos(target,a));
        d.dispatchOne(); assertEquals(ProtosTask.State.SUSPENDED,t.state()); assertTrue(t.requestCancellation()); d.dispatchOne();
        assertEquals(ProtosTask.State.CANCELLED,t.state()); assertEquals(ProtosFutureValue.State.PENDING,f.state());
        f.resolve(ProtosNullValue.INSTANCE,a); assertFalse(d.dispatchOne());
    }

    @Test void failedAndCancelledObservationHaveRequiredIdentity() throws Exception {
        var p=core(); var d=new ProtosActorExecutionDomain(); var a=p.newModuleActivation(new ProtosActorModuleState(),null,p.newExecutionContext(),d);
        var failed=new ProtosFutureValue(p.futurePrototype(),d); var e=ProtosCoreErrors.newError(a); failed.fail(e); a.context().createLocalSlot("f",failed);
        var ex=assertThrows(ProtosSignalException.class,()->eval(p,a,"f.value()")); assertSame(e,ex.error());
        var c=new ProtosFutureValue(p.futurePrototype(),d); c.cancelTerminal(); a.context().createLocalSlot("c",c);
        var c1=assertThrows(ProtosSignalException.class,()->eval(p,a,"c.value()")).error(); var c2=assertThrows(ProtosSignalException.class,()->eval(p,a,"c.value()")).error(); assertNotSame(c1,c2);
    }

    @Test void thenIsNeverInlineAndFlattens() throws Exception {
        var p=core(); var d=new ProtosActorExecutionDomain(); var a=p.newModuleActivation(new ProtosActorModuleState(),null,p.newExecutionContext(),d); AtomicInteger calls=new AtomicInteger();
        var source=new ProtosFutureValue(p.futurePrototype(),d); a.context().createLocalSlot("s",source);
        a.context().createLocalSlot("tx",ProtosClosureValue.nativeClosure((x,y)->{calls.incrementAndGet();return y.get(0);}));
        var dest=(ProtosFutureValue)eval(p,a,"s.then(tx)"); source.resolve(new ProtosIntegerValue(BigInteger.ONE),a); assertEquals(0,calls.get()); d.dispatchUntilIdle(); assertEquals(1,calls.get()); assertEquals(ProtosFutureValue.State.RESOLVED,dest.state());
        var inner=new ProtosFutureValue(p.futurePrototype(),d); a.context().createLocalSlot("inner",inner); a.context().createLocalSlot("retInner",ProtosClosureValue.nativeClosure((x,y)->inner));
        var flat=(ProtosFutureValue)eval(p,a,"s.then(retInner)"); d.dispatchUntilIdle(); assertEquals(ProtosFutureValue.State.PENDING,flat.state()); inner.resolve(ProtosNullValue.INSTANCE,a); assertEquals(ProtosFutureValue.State.RESOLVED,flat.state());
    }

    @Test void futureAllUsesInputOrderFrontierAndEmptyArray() throws Exception {
        var p=core(); var d=new ProtosActorExecutionDomain(); var a=p.newModuleActivation(new ProtosActorModuleState(),null,p.newExecutionContext(),d);
        var f0=new ProtosFutureValue(p.futurePrototype(),d); var f1=new ProtosFutureValue(p.futurePrototype(),d); a.context().createLocalSlot("f0",f0);a.context().createLocalSlot("f1",f1);
        var all=(ProtosFutureValue)eval(p,a,"Future.all(f0,f1)"); var later=ProtosCoreErrors.newError(a); f1.fail(later); assertEquals(ProtosFutureValue.State.PENDING,all.state()); f0.resolve(new ProtosIntegerValue(BigInteger.TEN),a); assertEquals(ProtosFutureValue.State.FAILED,all.state()); assertSame(later,all.failedError().orElseThrow());
        var empty=(ProtosFutureValue)eval(p,a,"Future.all()"); assertEquals(ProtosFutureValue.State.RESOLVED,empty.state()); assertEquals(BigInteger.ZERO,((ProtosArrayValue)empty.resolvedValue().orElseThrow()).indexedSize());
    }

    @Test void firstTerminalWinsUnderRace() throws Exception {
        var p=core(); var a=p.newModuleActivation(); var f=new ProtosFutureValue(p.futurePrototype(),a.executionDomain()); CountDownLatch go=new CountDownLatch(1); Object v=new ProtosObjectValue(ProtosObjectValue.rootObject());
        Thread r=new Thread(()->await(go,()->f.resolve(v,a))); Thread c=new Thread(()->await(go,f::cancelTerminal)); r.start();c.start();go.countDown();r.join();c.join(); assertNotEquals(ProtosFutureValue.State.PENDING,f.state());
    }
    private static void await(CountDownLatch l,Runnable r){try{l.await();r.run();}catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError(e);}}
}
