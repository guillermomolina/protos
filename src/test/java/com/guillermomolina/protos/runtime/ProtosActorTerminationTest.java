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
package com.guillermomolina.protos.runtime;

import static org.junit.jupiter.api.Assertions.*;

import com.guillermomolina.protos.execution.ProtosCoreBootstrap;
import com.guillermomolina.protos.execution.ProtosInvocation;
import com.guillermomolina.protos.execution.ProtosModuleResolver;
import com.guillermomolina.protos.execution.ProtosModuleRuntime;
import com.guillermomolina.protos.execution.ProtosStandardActorProtocol;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

final class ProtosActorTerminationTest {
    private static final Path CORE = Path.of("protos", "lib", "core");

    @Test
    void actorRefExposesSendRequestStopAndTerminationOnly() throws Exception {
        Fixture x = fixture();
        ProtosActor target = x.readyTarget(2, behavior());
        ProtosObjectValue prototype =
                assertInstanceOf(
                        ProtosObjectValue.class,
                        target.reference().representedDelegationParent(x.prelude));
        assertEquals(Set.of("send", "request", "stop", "termination"),
                prototype.localSlotsSnapshot().keySet());
        assertTrue(prototype.isFrozen());
    }

    @Test
    void stopIsNullIdempotentAndLosesAcceptedUndispatchedRequestAsUncertain() throws Exception {
        Fixture x = fixture();
        ProtosActor target = x.readyTarget(1, behavior());
        ProtosFutureValue request = x.request(target, "echo", new ProtosStringValue("value"));
        assertEquals(ProtosFutureValue.State.PENDING, request.state());
        assertEquals(1, target.mailboxForRuntime().size());

        assertSame(ProtosNullValue.INSTANCE,
                ProtosInvocation.invokeMessage(target.reference(), "stop", List.of(), x.creatorActivation));
        assertEquals(ProtosActor.LifecycleState.TERMINATED, target.lifecycleState());
        assertEquals(ProtosFutureValue.State.FAILED, request.state());
        assertSame(x.prelude.standardErrorPrototype("RequestOutcomeUncertain"),
                request.failedError().orElseThrow().parent().orElseThrow());
        assertSame(ProtosNullValue.INSTANCE,
                ProtosInvocation.invokeMessage(target.reference(), "stop", List.of(), x.creatorActivation));
    }

    @Test
    void terminationFuturesAreFreshIndependentAndResolveWithExactReceiver() throws Exception {
        Fixture x = fixture();
        ProtosActor target = x.readyTarget(1, behavior());
        ProtosActorRefValue reference = target.reference();
        ProtosFutureValue first = x.termination(reference);
        ProtosFutureValue second = x.termination(reference);
        assertNotSame(first, second);
        assertSame(x.creator.executionDomain(), first.domain());
        assertEquals(ProtosFutureValue.State.PENDING, first.state());
        assertEquals(ProtosFutureValue.State.PENDING, second.state());

        first.cancelRequest();
        assertEquals(ProtosFutureValue.State.CANCELLED, first.state());
        assertEquals(ProtosActor.LifecycleState.READY, target.lifecycleState());
        reference.requestStopForRuntime();

        assertEquals(ProtosFutureValue.State.CANCELLED, first.state());
        assertEquals(ProtosFutureValue.State.RESOLVED, second.state());
        assertSame(reference, second.resolvedValue().orElseThrow());
        ProtosFutureValue after = x.termination(reference);
        assertEquals(ProtosFutureValue.State.RESOLVED, after.state());
        assertSame(reference, after.resolvedValue().orElseThrow());
    }

    @Test
    void observationRegistrationAndTerminationRaceHasNoLostWakeup() throws Exception {
        Fixture x = fixture();
        for (int i = 0; i < 200; i++) {
            ProtosActor target = x.readyTarget(1, behavior());
            AtomicReference<ProtosFutureValue> observed = new AtomicReference<>();
            CountDownLatch start = new CountDownLatch(1);
            Thread register = new Thread(() -> {
                await(start);
                observed.set(target.reference().observeTerminationForRuntime(
                        x.prelude.futurePrototype(), x.creator.executionDomain()));
            });
            Thread stop = new Thread(() -> {
                await(start);
                target.reference().requestStopForRuntime();
            });
            register.start();
            stop.start();
            start.countDown();
            register.join();
            stop.join();
            ProtosFutureValue future = observed.get();
            assertNotNull(future);
            assertEquals(ProtosFutureValue.State.RESOLVED, future.state());
            assertSame(target.reference(), future.resolvedValue().orElseThrow());
        }
    }

    @Test
    void stopWhileInitializingSuppressesQueuedBootstrapControl() throws Exception {
        Fixture x = fixture();
        ManualExecutor carriers = new ManualExecutor();
        ProtosActorScheduler scheduler = new ProtosActorScheduler(carriers, 1);
        ProtosActor actor = new ProtosActor(x.actorRefPrototype);
        AtomicInteger ran = new AtomicInteger();
        scheduler.attach(actor);
        scheduler.submitControl(actor, ran::incrementAndGet);
        assertEquals(1, carriers.size());

        actor.reference().requestStopForRuntime();
        assertEquals(ProtosActor.LifecycleState.TERMINATED, actor.lifecycleState());
        carriers.runNext();
        assertEquals(0, ran.get());
    }

    @Test
    void gracefulStopCancelsLiveTasksAndWaitsForTheirCancellationUnwind() throws Exception {
        Fixture x = fixture();
        ManualExecutor carriers = new ManualExecutor();
        ProtosActorScheduler scheduler = new ProtosActorScheduler(carriers, 1);
        ProtosActor actor = new ProtosActor(x.actorRefPrototype);
        actor.bindMessageEnvironmentForRuntime(x.prelude, new ProtosModuleKey("tasks"));
        actor.completeInitialization(behavior());
        scheduler.attach(actor);
        Dependency wait = new Dependency();
        AtomicInteger suspendedSegments = new AtomicInteger();
        ProtosTask suspended = actor.executionDomain().createTask(null, current -> {
            suspendedSegments.incrementAndGet();
            if (current.cancellationRequested()) current.observeCancellation();
            else current.suspend(wait);
        });
        carriers.runNext();
        assertEquals(ProtosTask.State.SUSPENDED, suspended.state());
        AtomicInteger neverRanOrdinaryBody = new AtomicInteger();
        ProtosTask neverStarted = actor.executionDomain().createTask(null, current -> {
            neverRanOrdinaryBody.incrementAndGet();
            current.complete(ProtosNullValue.INSTANCE);
        });

        actor.reference().requestStopForRuntime();
        assertEquals(ProtosActor.LifecycleState.TERMINATING, actor.lifecycleState());
        assertTrue(suspended.cancellationRequested());
        assertTrue(neverStarted.cancellationRequested());
        assertEquals(2, actor.executionDomain().liveTaskCount());

        carriers.runNext();
        assertEquals(ProtosTask.State.CANCELLED, suspended.state());
        assertEquals(ProtosTask.State.CANCELLED, neverStarted.state());
        assertEquals(0, neverRanOrdinaryBody.get());
        assertEquals(ProtosActor.LifecycleState.TERMINATED, actor.lifecycleState());
    }

    @Test
    void runningDeliveryMayResumeDuringTerminationOnlyForCancellationCleanup() {
        ProtosActor actor = new ProtosActor(
                new ProtosObjectValue(ProtosObjectValue.rootObject()).freeze(),
                new ProtosActorExecutionDomain(), new ProtosActorModuleState(), 1);
        actor.markReady();
        ProtosActorDeliveryAttempt attempt =
                actor.beginDeliveryForRuntime(null, task -> task.complete(ProtosNullValue.INSTANCE));
        assertEquals(ProtosActorDeliveryAttempt.State.ACCEPTED, attempt.state());
        // Model the real scheduler handoff: once a handler turn has started, its accepted
        // mailbox entry has already been removed. Leaving it retained here would let graceful
        // stop correctly classify it as accepted-but-undispatched loss instead of RUNNING work.
        assertNotNull(actor.mailboxForRuntime().pollForDispatch());
        assertTrue(attempt.beginDispatchForRuntime());
        assertEquals(ProtosActorDeliveryAttempt.State.RUNNING, attempt.state());

        actor.beginTermination();

        assertTrue(attempt.beginDispatchForRuntime());
        assertEquals(ProtosActorDeliveryAttempt.State.RUNNING, attempt.state());
    }

    @Test
    void stoppingOriginActorCancelsItsPendingRequestWithoutUnsendingAcceptedWork() throws Exception {
        Fixture x = fixture();
        ProtosActor target = new ProtosActor(
                x.actorRefPrototype, new ProtosActorExecutionDomain(), new ProtosActorModuleState(), 1);
        target.bindMessageEnvironmentForRuntime(x.prelude, new ProtosModuleKey("target"));
        target.completeInitialization(behavior());
        ProtosFutureValue request = x.request(target, "echo", new ProtosStringValue("kept"));
        assertEquals(1, target.mailboxForRuntime().size());

        x.creator.requestTerminationForRuntime();

        assertEquals(ProtosFutureValue.State.CANCELLED, request.state());
        assertEquals(1, target.mailboxForRuntime().size(), "accepted request must not be unsent");
        assertEquals(ProtosActor.LifecycleState.TERMINATED, x.creator.lifecycleState());
    }

    private static ProtosObjectValue behavior() {
        ProtosObjectValue behavior = new ProtosObjectValue(ProtosObjectValue.rootObject());
        behavior.createLocalSlot("echo", ProtosClosureValue.nativeClosure(
                (activation, args) -> args.isEmpty() ? ProtosNullValue.INSTANCE : args.get(0)));
        return behavior;
    }

    private static Fixture fixture() throws Exception {
        ProtosModuleResolver resolver = new ProtosModuleResolver() {
            @Override public ProtosModuleKey resolve(String exact, Optional<ProtosModuleKey> importing) {
                return new ProtosModuleKey("canonical:" + exact);
            }
            @Override public String loadSource(ProtosModuleKey key) {
                return "boot: () => {\n    {}\n}";
            }
        };
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE, resolver);
        ManualExecutor bootstrap = new ManualExecutor();
        ProtosObjectValue protocolActorRefPrototype =
                new ProtosObjectValue(ProtosObjectValue.rootObject());
        ProtosObjectValue protocolSendOperationPrototype =
                new ProtosObjectValue(ProtosObjectValue.rootObject());
        ProtosStandardActorProtocol protocol = new ProtosStandardActorProtocol(
                new ProtosModuleRuntime(resolver),
                bootstrap,
                protocolActorRefPrototype,
                protocolSendOperationPrototype);
        ProtosObjectValue actorObject = protocol.installActorObject(
                new ProtosObjectValue(ProtosObjectValue.rootObject()));
        ProtosActor creator = new ProtosActor(
                new ProtosObjectValue(ProtosObjectValue.rootObject()).freeze());
        creator.markReady();
        ProtosActivation activation = prelude.newModuleActivation(
                creator.moduleState(), new ProtosModuleKey("creator"),
                prelude.newExecutionContext(), creator.executionDomain());
        ProtosActorRefValue seed = assertInstanceOf(ProtosActorRefValue.class,
                ProtosInvocation.invokeMessage(actorObject, "spawn",
                        List.of(new ProtosStringValue("seed"), new ProtosStringValue("boot")),
                        activation));
        ProtosObjectValue refPrototype = assertInstanceOf(ProtosObjectValue.class,
                seed.representedDelegationParent(prelude));
        bootstrap.runNext();
        return new Fixture(prelude, creator, activation, refPrototype);
    }

    private static final class Fixture {
        final ProtosPrelude prelude;
        final ProtosActor creator;
        final ProtosActivation creatorActivation;
        final ProtosObjectValue actorRefPrototype;
        Fixture(ProtosPrelude p, ProtosActor c, ProtosActivation a, ProtosObjectValue r) {
            prelude=p; creator=c; creatorActivation=a; actorRefPrototype=r;
        }
        ProtosActor readyTarget(int capacity, ProtosObjectValue behavior) {
            ProtosActor actor = new ProtosActor(actorRefPrototype,
                    new ProtosActorExecutionDomain(), new ProtosActorModuleState(), capacity);
            actor.bindMessageEnvironmentForRuntime(prelude, new ProtosModuleKey("target"));
            actor.completeInitialization(behavior);
            new ProtosActorScheduler(new ManualExecutor(), 1).attach(actor);
            return actor;
        }
        ProtosFutureValue request(ProtosActor target, String selector, Object... args) {
            java.util.ArrayList<Object> supplied = new java.util.ArrayList<>();
            supplied.add(new ProtosStringValue(selector)); supplied.addAll(List.of(args));
            return assertInstanceOf(ProtosFutureValue.class,
                    ProtosInvocation.invokeMessage(target.reference(), "request", supplied, creatorActivation));
        }
        ProtosFutureValue termination(ProtosActorRefValue reference) {
            return assertInstanceOf(ProtosFutureValue.class,
                    ProtosInvocation.invokeMessage(reference, "termination", List.of(), creatorActivation));
        }
    }

    private static final class Dependency implements ProtosTask.WaitDependency {}
    private static final class ManualExecutor implements Executor {
        private final ArrayDeque<Runnable> pending = new ArrayDeque<>();
        @Override public synchronized void execute(Runnable command) { pending.addLast(command); }
        synchronized int size() { return pending.size(); }
        void runNext() { Runnable r; synchronized(this){r=pending.pollFirst();} assertNotNull(r); r.run(); }
    }
    private static void await(CountDownLatch latch) {
        try { latch.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); }
    }
}
