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
        var fourth=x.lifecycle.close(x.activation); assertNotSame(first,fourth); assertNotSame(second,fourth); assertEquals(1,x.releaseStarts.get());
        assertEquals(ProtosFutureValue.State.PENDING,first.state()); assertEquals(ProtosFutureValue.State.PENDING,second.state()); assertEquals(ProtosFutureValue.State.PENDING,fourth.state());
        var failure=ProtosCoreErrors.newOccurrence(x.activation,ProtosCoreErrors.StandardError.I_O_ERROR); x.completion.get().failed(failure);
        assertSame(failure,first.failedError().orElseThrow()); assertSame(failure,second.failedError().orElseThrow()); assertSame(failure,fourth.failedError().orElseThrow());
        assertEquals(1,x.releaseStarts.get());
        var third=x.lifecycle.close(x.activation); assertNotSame(first,third); assertSame(failure,third.failedError().orElseThrow()); assertEquals(1,x.releaseStarts.get());
    }
    @Test void actorTerminationRequestsCancellationWithoutRevertingCommittedOperation() throws Exception {
        var x=fixture(); var pre=x.lifecycle.beginOperation(x.activation); var committed=x.lifecycle.beginOperation(x.activation); assertTrue(committed.commit());
        assertEquals(2,x.domain.actorIoOperationCountForTesting()); x.domain.actorTerminated(); assertEquals(ProtosFutureValue.State.CANCELLED,pre.future().state());
        assertEquals(ProtosFutureValue.State.PENDING,committed.future().state()); assertTrue(committed.resolve(ProtosNullValue.INSTANCE)); assertEquals(0,x.domain.actorIoOperationCountForTesting());
    }

    @Test
    void cancellationDuringFirstEffectAttemptWaitsForZeroEffectClassification() throws Exception {
        var x=fixture();
        var operation=x.lifecycle.beginOperation(x.activation);
        var cancellations=new AtomicInteger();
        operation.onCancellation(cancellations::incrementAndGet);

        assertTrue(operation.beginFirstEffectAttempt());
        assertTrue(operation.future().cancelRequest());
        assertEquals(ProtosFutureValue.State.PENDING,operation.future().state());
        assertEquals(1,cancellations.get());

        assertFalse(operation.finishFirstEffectAttempt(false));
        assertEquals(ProtosFutureValue.State.CANCELLED,operation.future().state());
        assertFalse(operation.committed());
        assertTrue(operation.terminal());
    }

    @Test
    void positiveFirstEffectCommitsDespiteCancellationRequestedDuringAttempt() throws Exception {
        var x=fixture();
        var operation=x.lifecycle.beginOperation(x.activation);
        var cancellations=new AtomicInteger();
        operation.onCancellation(cancellations::incrementAndGet);

        assertTrue(operation.beginFirstEffectAttempt());
        assertTrue(operation.future().cancelRequest());
        assertEquals(ProtosFutureValue.State.PENDING,operation.future().state());

        assertTrue(operation.finishFirstEffectAttempt(true));
        assertTrue(operation.committed());
        assertEquals(ProtosFutureValue.State.PENDING,operation.future().state());
        assertEquals(1,cancellations.get());
        assertTrue(operation.resolve(ProtosNullValue.INSTANCE));
        assertEquals(ProtosFutureValue.State.RESOLVED,operation.future().state());
    }

    @Test
    void closeDuringFirstEffectAttemptWaitsForZeroEffectThenUsesExistingLifecycleError() throws Exception {
        var x=fixture();
        var operation=x.lifecycle.beginOperation(x.activation);
        var cancellations=new AtomicInteger();
        operation.onCancellation(cancellations::incrementAndGet);
        assertTrue(operation.beginFirstEffectAttempt());

        var close=x.lifecycle.close(x.activation);
        assertEquals(ProtosFutureValue.State.PENDING,operation.future().state());
        assertEquals(ProtosFutureValue.State.PENDING,close.state());
        assertEquals(0,x.releaseStarts.get());
        assertEquals(1,cancellations.get());

        assertFalse(operation.finishFirstEffectAttempt(false));
        assertEquals(ProtosFutureValue.State.FAILED,operation.future().state());
        assertSame(
                x.prelude.standardErrorPrototype("IOLifecycleError"),
                operation.future().failedError().orElseThrow().parent().orElseThrow());
        assertEquals(1,x.releaseStarts.get());

        x.completion.get().succeeded();
        assertEquals(ProtosFutureValue.State.RESOLVED,close.state());
    }
    @Test
    void positiveFirstEffectAfterCloseCommitsAndCloseWaitsForTerminalOperation() throws Exception {
        var x=fixture();
        var operation=x.lifecycle.beginOperation(x.activation);
        assertTrue(operation.beginFirstEffectAttempt());

        var close=x.lifecycle.close(x.activation);
        assertEquals(0,x.releaseStarts.get());

        assertTrue(operation.finishFirstEffectAttempt(true));
        assertTrue(operation.committed());
        assertEquals(ProtosFutureValue.State.PENDING,close.state());
        assertEquals(0,x.releaseStarts.get());

        assertTrue(operation.resolve(ProtosNullValue.INSTANCE));
        assertEquals(1,x.releaseStarts.get());
        x.completion.get().succeeded();
        assertEquals(ProtosFutureValue.State.RESOLVED,close.state());
    }

    @Test
    void unknownEffectFailureBeatsCancellationRequestedDuringFirstEffectAttempt() throws Exception {
        var x=fixture();
        var operation=x.lifecycle.beginOperation(x.activation);
        var cancellations=new AtomicInteger();
        operation.onCancellation(cancellations::incrementAndGet);
        var failure=ProtosCoreErrors.newOccurrence(
                x.activation,ProtosCoreErrors.StandardError.I_O_ERROR);

        assertTrue(operation.beginFirstEffectAttempt());
        assertTrue(operation.future().cancelRequest());
        assertEquals(1,cancellations.get());
        assertEquals(ProtosFutureValue.State.PENDING,operation.future().state());

        assertTrue(operation.failFirstEffectAttemptWithUnknownEffect(failure));
        assertEquals(ProtosFutureValue.State.FAILED,operation.future().state());
        assertSame(failure,operation.future().failedError().orElseThrow());
        assertTrue(operation.terminal());
        assertFalse(operation.committed());
    }

    @Test
    void unknownEffectFailureBeatsDeferredCloseAndThenReleasesLifecycle() throws Exception {
        var x=fixture();
        var operation=x.lifecycle.beginOperation(x.activation);
        var cancellations=new AtomicInteger();
        operation.onCancellation(cancellations::incrementAndGet);
        var failure=ProtosCoreErrors.newOccurrence(
                x.activation,ProtosCoreErrors.StandardError.I_O_ERROR);

        assertTrue(operation.beginFirstEffectAttempt());
        var close=x.lifecycle.close(x.activation);
        assertEquals(1,cancellations.get());
        assertEquals(ProtosFutureValue.State.PENDING,operation.future().state());
        assertEquals(ProtosFutureValue.State.PENDING,close.state());

        assertTrue(operation.failFirstEffectAttemptWithUnknownEffect(failure));
        assertEquals(ProtosFutureValue.State.FAILED,operation.future().state());
        assertSame(failure,operation.future().failedError().orElseThrow());
        assertEquals(1,x.releaseStarts.get());

        x.completion.get().succeeded();
        assertEquals(ProtosFutureValue.State.RESOLVED,close.state());
    }

    @Test
    void firstCutoverArrivalDuringAttemptPreservesCancellationVersusClosePrecedence() throws Exception {
        var cancellationFirst=fixture();
        var cancelled=cancellationFirst.lifecycle.beginOperation(cancellationFirst.activation);
        assertTrue(cancelled.beginFirstEffectAttempt());
        assertTrue(cancelled.future().cancelRequest());
        var closeAfterCancellation=cancellationFirst.lifecycle.close(cancellationFirst.activation);
        assertFalse(cancelled.finishFirstEffectAttempt(false));
        assertEquals(ProtosFutureValue.State.CANCELLED,cancelled.future().state());
        cancellationFirst.completion.get().succeeded();
        assertEquals(ProtosFutureValue.State.RESOLVED,closeAfterCancellation.state());

        var closeFirst=fixture();
        var failed=closeFirst.lifecycle.beginOperation(closeFirst.activation);
        assertTrue(failed.beginFirstEffectAttempt());
        var closeBeforeCancellation=closeFirst.lifecycle.close(closeFirst.activation);
        assertTrue(failed.future().cancelRequest());
        assertEquals(ProtosFutureValue.State.PENDING,failed.future().state());
        assertFalse(failed.finishFirstEffectAttempt(false));
        assertEquals(ProtosFutureValue.State.FAILED,failed.future().state());
        assertSame(
                closeFirst.prelude.standardErrorPrototype("IOLifecycleError"),
                failed.future().failedError().orElseThrow().parent().orElseThrow());
        closeFirst.completion.get().succeeded();
        assertEquals(ProtosFutureValue.State.RESOLVED,closeBeforeCancellation.state());
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
        var hooks=new java.util.ArrayList<String>();
        var operation=lifecycle.beginOperation(a);
        operation.onCancellation(()->{cancelled.set(true);hooks.add("first");});
        var second=lifecycle.beginOperation(a);
        second.onCancellation(()->hooks.add("second"));
        var third=lifecycle.beginOperation(a);
        third.onCancellation(()->hooks.add("third"));

        var close=lifecycle.close(a);

        assertTrue(cancelled.get());
        assertEquals(java.util.List.of("first","second","third"),hooks);
        assertEquals(ProtosFutureValue.State.FAILED,operation.future().state());
        assertEquals(ProtosFutureValue.State.FAILED,second.future().state());
        assertEquals(ProtosFutureValue.State.FAILED,third.future().state());
        assertEquals(0,d.actorIoOperationCountForTesting());
        assertEquals(1,releases.get());
        assertEquals(ProtosFutureValue.State.RESOLVED,close.state());

        assertFalse(operation.future().cancelRequest());
        assertFalse(third.future().cancelRequest());
        var repeated=lifecycle.close(a);
        assertNotSame(close,repeated);
        assertEquals(ProtosFutureValue.State.RESOLVED,repeated.state());
        assertEquals(java.util.List.of("first","second","third"),hooks);
        assertEquals(1,releases.get());
    }

    private record Fixture(ProtosPrelude prelude, ProtosActorExecutionDomain domain, ProtosActivation activation, ProtosObjectValue receiver,
            ProtosIoLifecycle lifecycle, AtomicInteger releaseStarts, AtomicReference<ProtosIoLifecycle.ReleaseCompletion> completion) {}

    @Test
    void retiringLastCommittedOperationStartsReleaseExactlyOnce() throws Exception {
        var x=fixture();
        var first=x.lifecycle.beginOperation(x.activation); assertTrue(first.commit());
        var second=x.lifecycle.beginOperation(x.activation); assertTrue(second.commit());
        var close=x.lifecycle.close(x.activation);
        assertTrue(first.resolve(ProtosNullValue.INSTANCE));
        assertEquals(0,x.releaseStarts.get());
        assertEquals(ProtosFutureValue.State.PENDING,close.state());
        assertEquals(1,x.domain.actorIoOperationCountForTesting());

        assertTrue(second.resolve(ProtosNullValue.INSTANCE));
        assertEquals(1,x.releaseStarts.get());
        assertEquals(0,x.domain.actorIoOperationCountForTesting());
        assertEquals(ProtosFutureValue.State.PENDING,close.state());

        var concurrentClose=x.lifecycle.close(x.activation);
        assertNotSame(close,concurrentClose);
        assertEquals(1,x.releaseStarts.get());
        assertFalse(second.resolve(ProtosNullValue.INSTANCE));
        assertEquals(1,x.releaseStarts.get());

        x.completion.get().succeeded();
        assertEquals(ProtosFutureValue.State.RESOLVED,close.state());
        assertEquals(ProtosFutureValue.State.RESOLVED,concurrentClose.state());
        assertSame(x.receiver,close.resolvedValue().orElseThrow());
    }

    @Test
    void terminalObserversRunOnceInRegistrationOrderAndLateObserversRunImmediately() throws Exception {
        var x=fixture();
        var operation=x.lifecycle.beginOperation(x.activation);
        var future=operation.future();
        var events=new java.util.ArrayList<String>();
        ProtosFutureValue.Observer first=f->events.add("first");
        ProtosFutureValue.Observer removed=f->events.add("removed");
        ProtosFutureValue.Observer nested=f->events.add("nested");
        ProtosFutureValue.Observer third=f->{events.add("third");f.observe(nested);};
        future.observe(first); future.observe(removed); future.observe(third);
        assertEquals(3,future.observerCountForTesting());
        future.removeObserver(removed);
        assertEquals(2,future.observerCountForTesting());

        assertTrue(operation.beginFirstEffectAttempt());
        assertTrue(future.cancelRequest());
        assertTrue(events.isEmpty());
        assertFalse(operation.finishFirstEffectAttempt(false));

        assertEquals(ProtosFutureValue.State.CANCELLED,future.state());
        assertEquals(java.util.List.of("first","third","nested"),events);
        assertEquals(0,future.observerCountForTesting());

        future.observe(f->events.add("late"));
        assertEquals(java.util.List.of("first","third","nested","late"),events);
        assertEquals(0,future.observerCountForTesting());
        assertFalse(future.cancelRequest());
        assertEquals(java.util.List.of("first","third","nested","late"),events);
    }
}
