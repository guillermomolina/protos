/*
 * THE LICENSED WORK IS PROVIDED UNDER THE TERMS OF THE ADAPTIVE PUBLIC LICENSE
 * ("LICENSE") AS FIRST COMPLETED BY: Guillermo Adrián Molina. See LICENSE.TXT.
 */
package com.guillermomolina.protos.runtime;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

final class ProtosActorIdentityLifecycleTest {
    @Test
    void newActorStartsInitializingWithStableIdentity() {
        ProtosActor actor = new ProtosActor(actorRefPrototype());

        long identity = actor.incarnationIdentityForRuntime();
        assertTrue(identity > 0);
        assertEquals(identity, actor.incarnationIdentityForRuntime());
        assertEquals(ProtosActor.LifecycleState.INITIALIZING, actor.lifecycleState());
    }

    @Test
    void distinctActorIncarnationsHaveDistinctIdentity() {
        ProtosObjectValue prototype = actorRefPrototype();
        ProtosActor first = new ProtosActor(prototype);
        ProtosActor second = new ProtosActor(prototype);

        assertNotEquals(
                first.incarnationIdentityForRuntime(),
                second.incarnationIdentityForRuntime());
    }

    @Test
    void actorRefsRematerializedForSameIncarnationShareSemanticIdentity() {
        ProtosObjectValue prototype = actorRefPrototype();
        ProtosActor actor = new ProtosActor(prototype);
        ProtosActorRefValue canonical = actor.reference();
        ProtosActorRefValue rematerialized = new ProtosActorRefValue(prototype, actor);

        assertNotSame(canonical, rematerialized);
        assertTrue(ProtosIdentity.identical(canonical, rematerialized));
        assertEquals(
                ProtosIdentity.identityHash(canonical),
                ProtosIdentity.identityHash(rematerialized));
        assertSame(actor, canonical.localActorForRuntime());
        assertSame(actor, rematerialized.localActorForRuntime());
    }

    @Test
    void refsToDistinctIncarnationsNeverCompareIdentical() {
        ProtosObjectValue prototype = actorRefPrototype();
        ProtosActor first = new ProtosActor(prototype);
        ProtosActor second = new ProtosActor(prototype);

        assertFalse(ProtosIdentity.identical(first.reference(), second.reference()));
    }

    @Test
    void initializingCanBecomeReadyExactlyOnce() {
        ProtosActor actor = new ProtosActor(actorRefPrototype());

        assertTrue(actor.markReady());
        assertFalse(actor.markReady());
        assertEquals(ProtosActor.LifecycleState.READY, actor.lifecycleState());
    }

    @Test
    void readyActorTerminatesThroughRequiredStates() {
        ProtosActor actor = new ProtosActor(actorRefPrototype());
        actor.markReady();

        assertTrue(actor.beginTermination());
        assertEquals(ProtosActor.LifecycleState.TERMINATING, actor.lifecycleState());
        assertFalse(actor.markReady());
        assertTrue(actor.markTerminated());
        assertEquals(ProtosActor.LifecycleState.TERMINATED, actor.lifecycleState());
    }

    @Test
    void initializingActorMayTerminateWithoutBecomingReady() {
        ProtosActor actor = new ProtosActor(actorRefPrototype());

        assertTrue(actor.beginTermination());
        assertTrue(actor.markTerminated());
        assertEquals(ProtosActor.LifecycleState.TERMINATED, actor.lifecycleState());
    }

    @Test
    void terminatedIsTerminalAndActorRefNeverRetargets() {
        ProtosActor actor = new ProtosActor(actorRefPrototype());
        ProtosActorRefValue before = actor.reference();
        long identity = actor.incarnationIdentityForRuntime();

        actor.beginTermination();
        actor.markTerminated();

        assertFalse(actor.beginTermination());
        assertFalse(actor.markTerminated());
        assertFalse(actor.markReady());
        assertSame(before, actor.reference());
        assertEquals(identity, actor.incarnationIdentityForRuntime());
        assertTrue(ProtosIdentity.identical(before, actor.reference()));
    }

    @Test
    void conceptualReplacementGetsNewIdentityAndReference() {
        ProtosObjectValue prototype = actorRefPrototype();
        ProtosActor original = new ProtosActor(prototype);
        original.beginTermination();
        original.markTerminated();

        ProtosActor replacement = new ProtosActor(prototype);

        assertNotEquals(
                original.incarnationIdentityForRuntime(),
                replacement.incarnationIdentityForRuntime());
        assertFalse(ProtosIdentity.identical(original.reference(), replacement.reference()));
    }

    @Test
    void readinessAndTerminationRaceCannotReopenOrProduceImpossibleState() throws Exception {
        ProtosActor actor = new ProtosActor(actorRefPrototype());
        AtomicInteger readyTransitions = new AtomicInteger();
        AtomicInteger terminatingTransitions = new AtomicInteger();

        runConcurrently(
                () -> {
                    if (actor.markReady()) {
                        readyTransitions.incrementAndGet();
                    }
                },
                () -> {
                    if (actor.beginTermination()) {
                        terminatingTransitions.incrementAndGet();
                    }
                });

        assertTrue(readyTransitions.get() == 0 || readyTransitions.get() == 1);
        assertEquals(1, terminatingTransitions.get());
        assertEquals(ProtosActor.LifecycleState.TERMINATING, actor.lifecycleState());
        assertFalse(actor.markReady());
    }

    @Test
    void concurrentLifecycleCutoversProduceExactlyOneTransition() throws Exception {
        ProtosActor actor = new ProtosActor(actorRefPrototype());

        AtomicInteger readyTransitions = new AtomicInteger();
        runConcurrently(
                () -> {
                    if (actor.markReady()) {
                        readyTransitions.incrementAndGet();
                    }
                },
                () -> {
                    if (actor.markReady()) {
                        readyTransitions.incrementAndGet();
                    }
                });
        assertEquals(1, readyTransitions.get());
        assertEquals(ProtosActor.LifecycleState.READY, actor.lifecycleState());

        AtomicInteger terminatingTransitions = new AtomicInteger();
        runConcurrently(
                () -> {
                    if (actor.beginTermination()) {
                        terminatingTransitions.incrementAndGet();
                    }
                },
                () -> {
                    if (actor.beginTermination()) {
                        terminatingTransitions.incrementAndGet();
                    }
                });
        assertEquals(1, terminatingTransitions.get());
        assertEquals(ProtosActor.LifecycleState.TERMINATING, actor.lifecycleState());

        AtomicInteger terminatedTransitions = new AtomicInteger();
        runConcurrently(
                () -> {
                    if (actor.markTerminated()) {
                        terminatedTransitions.incrementAndGet();
                    }
                },
                () -> {
                    if (actor.markTerminated()) {
                        terminatedTransitions.incrementAndGet();
                    }
                });
        assertEquals(1, terminatedTransitions.get());
        assertEquals(ProtosActor.LifecycleState.TERMINATED, actor.lifecycleState());
    }

    @Test
    void actorGroupsExistingExecutionDomainAndModuleStateWithoutReplacingThem() {
        ProtosObjectValue prototype = actorRefPrototype();
        ProtosActorExecutionDomain domain = new ProtosActorExecutionDomain();
        ProtosActorModuleState moduleState = new ProtosActorModuleState();
        ProtosActor actor = new ProtosActor(prototype, domain, moduleState);
        AtomicInteger ran = new AtomicInteger();

        assertSame(domain, actor.executionDomain());
        assertSame(moduleState, actor.moduleState());

        ProtosTask task =
                actor.executionDomain()
                        .createTask(
                                null,
                                current -> {
                                    ran.incrementAndGet();
                                    current.complete("done");
                                });
        assertTrue(actor.executionDomain().dispatchOne());
        assertEquals(1, ran.get());
        assertEquals(ProtosTask.State.COMPLETED, task.state());
    }

    private static ProtosObjectValue actorRefPrototype() {
        return new ProtosObjectValue(ProtosObjectValue.rootObject()).freeze();
    }

    private static void runConcurrently(Runnable first, Runnable second) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Thread left = racer("i011-1-left", ready, start, first, failure);
        Thread right = racer("i011-1-right", ready, start, second, failure);
        left.start();
        right.start();
        ready.await();
        start.countDown();
        left.join();
        right.join();

        Throwable observed = failure.get();
        if (observed != null) {
            throw new AssertionError("concurrent lifecycle transition failed", observed);
        }
    }

    private static Thread racer(
            String name,
            CountDownLatch ready,
            CountDownLatch start,
            Runnable action,
            AtomicReference<Throwable> failure) {
        return new Thread(
                () -> {
                    ready.countDown();
                    try {
                        start.await();
                        action.run();
                    } catch (InterruptedException error) {
                        Thread.currentThread().interrupt();
                        failure.compareAndSet(null, error);
                    } catch (Throwable error) {
                        failure.compareAndSet(null, error);
                    }
                },
                name);
    }
}
