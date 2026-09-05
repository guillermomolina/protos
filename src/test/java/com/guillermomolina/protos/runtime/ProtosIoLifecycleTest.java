/* APL-1.0 licensed work; see LICENSE.TXT. */
package com.guillermomolina.protos.runtime;

import static org.junit.jupiter.api.Assertions.*;
import com.guillermomolina.protos.execution.ProtosCoreBootstrap;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ProtosIoLifecycleTest {
    private static ProtosPrelude core() throws Exception { return new ProtosCoreBootstrap().bootstrap(Path.of("protos","lib","core")); }
    private static Fixture fixture() throws Exception {
        var p=core(); var d=new ProtosActorExecutionDomain();
        var a=p.newModuleActivation(new ProtosActorModuleState(),null,p.newExecutionContext(),d);
        var receiver=new ProtosObjectValue(ProtosObjectValue.rootObject());
        var starts=new AtomicInteger(); var completion=new AtomicReference<ProtosIoLifecycle.ReleaseCompletion>();
        var lifecycle=new ProtosIoLifecycle(receiver,p.futurePrototype(),d,c->{starts.incrementAndGet();completion.set(c);});
        return new Fixture(p,d,a,receiver,lifecycle,starts,completion);
    }
    @Test void producerCancellationWinsOnlyBeforeCommitment() throws Exception {
        var x=fixture(); var pre=x.lifecycle.beginOperation(x.activation); var requests=new AtomicInteger(); pre.onCancellation(requests::incrementAndGet);
        assertTrue(pre.future().cancelRequest()); assertEquals(1,requests.get()); assertFalse(pre.future().cancelRequest()); assertEquals(1,requests.get()); assertEquals(ProtosFutureValue.State.CANCELLED,pre.future().state()); assertFalse(pre.committed());
        var committed=x.lifecycle.beginOperation(x.activation); assertTrue(committed.commit()); assertTrue(committed.future().cancelRequest());
        assertEquals(ProtosFutureValue.State.PENDING,committed.future().state()); assertTrue(committed.resolve(ProtosNullValue.INSTANCE));
    }
    @Test void waitingTaskCancellationDoesNotCancelIoProducer() throws Exception {
        var x=fixture(); var op=x.lifecycle.beginOperation(x.activation); x.activation.context().createLocalSlot("f",op.future());
        var target=new com.guillermomolina.protos.execution.ProtosSourceCompiler().compile("f.value()");
        var task=x.domain.createTask(null,t->t.executeProtos(target,x.activation)); x.domain.dispatchOne(); assertEquals(ProtosTask.State.SUSPENDED,task.state());
        assertTrue(task.requestCancellation()); x.domain.dispatchOne(); assertEquals(ProtosTask.State.CANCELLED,task.state()); assertEquals(ProtosFutureValue.State.PENDING,op.future().state());
        assertTrue(op.commit()); assertTrue(op.resolve(ProtosNullValue.INSTANCE)); assertFalse(x.domain.dispatchOne());
    }
    @Test void closeCutoverFailsUncommittedWaitsForCommittedAndRejectsLaterWork() throws Exception {
        var x=fixture(); var reversible=x.lifecycle.beginOperation(x.activation); var committed=x.lifecycle.beginOperation(x.activation); assertTrue(committed.commit());
        var close=x.lifecycle.close(x.activation); assertEquals(ProtosFutureValue.State.FAILED,reversible.future().state());
        assertSame(x.prelude.standardErrorPrototype("IOLifecycleError"),reversible.future().failedError().orElseThrow().parent().orElseThrow());
        assertEquals(ProtosFutureValue.State.PENDING,close.state()); assertEquals(0,x.releaseStarts.get());
        var late=x.lifecycle.beginOperation(x.activation); assertEquals(ProtosFutureValue.State.FAILED,late.future().state());
        assertTrue(committed.resolve(ProtosNullValue.INSTANCE)); assertEquals(1,x.releaseStarts.get()); x.completion.get().succeeded();
        assertEquals(ProtosFutureValue.State.RESOLVED,close.state()); assertSame(x.receiver,close.resolvedValue().orElseThrow());
    }
    @Test void repeatedCloseUsesFreshFuturesOneReleaseAndStableFailureIdentity() throws Exception {
        var x=fixture(); var first=x.lifecycle.close(x.activation); var second=x.lifecycle.close(x.activation); assertNotSame(first,second); assertEquals(1,x.releaseStarts.get());
        var failure=ProtosCoreErrors.newOccurrence(x.activation,ProtosCoreErrors.StandardError.I_O_ERROR); x.completion.get().failed(failure);
        assertSame(failure,first.failedError().orElseThrow()); assertSame(failure,second.failedError().orElseThrow());
        var third=x.lifecycle.close(x.activation); assertNotSame(first,third); assertSame(failure,third.failedError().orElseThrow()); assertEquals(1,x.releaseStarts.get());
    }
    @Test void actorTerminationRequestsCancellationWithoutRevertingCommittedOperation() throws Exception {
        var x=fixture(); var pre=x.lifecycle.beginOperation(x.activation); var committed=x.lifecycle.beginOperation(x.activation); assertTrue(committed.commit());
        assertEquals(2,x.domain.actorIoOperationCountForTesting()); x.domain.actorTerminated(); assertEquals(ProtosFutureValue.State.CANCELLED,pre.future().state());
        assertEquals(ProtosFutureValue.State.PENDING,committed.future().state()); assertTrue(committed.resolve(ProtosNullValue.INSTANCE)); assertEquals(0,x.domain.actorIoOperationCountForTesting());
    }

    @Test
    void closeCutoverInvokesUncommittedCancellationHookBeforeRelease() throws Exception {
        var p=core();
        var d=new ProtosActorExecutionDomain();
        var a=p.newModuleActivation(new ProtosActorModuleState(),null,p.newExecutionContext(),d);
        var receiver=new ProtosObjectValue(ProtosObjectValue.rootObject());
        var cancelled=new java.util.concurrent.atomic.AtomicBoolean();
        var releases=new AtomicInteger();
        var lifecycle=new ProtosIoLifecycle(receiver,p.futurePrototype(),d,c->{
            assertTrue(cancelled.get());
            releases.incrementAndGet();
            c.succeeded();
        });
        var operation=lifecycle.beginOperation(a);
        operation.onCancellation(()->cancelled.set(true));

        var close=lifecycle.close(a);

        assertTrue(cancelled.get());
        assertEquals(ProtosFutureValue.State.FAILED,operation.future().state());
        assertEquals(1,releases.get());
        assertEquals(ProtosFutureValue.State.RESOLVED,close.state());
    }

    private record Fixture(ProtosPrelude prelude, ProtosActorExecutionDomain domain, ProtosActivation activation, ProtosObjectValue receiver,
            ProtosIoLifecycle lifecycle, AtomicInteger releaseStarts, AtomicReference<ProtosIoLifecycle.ReleaseCompletion> completion) {}
}
