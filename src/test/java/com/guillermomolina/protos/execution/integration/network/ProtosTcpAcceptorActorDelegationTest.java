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
import com.guillermomolina.protos.runtime.io.ProtosHostIpEndpoint;
import com.guillermomolina.protos.runtime.io.ProtosTcpAcceptorValue;
import com.guillermomolina.protos.runtime.io.ProtosTcpConnectionValue;
import com.guillermomolina.protos.runtime.io.ProtosTcpListenerFlow;
import com.guillermomolina.protos.runtime.io.ProtosTcpListenerValue;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * I125-B: Actor.spawn-delegated TcpAcceptor capabilities accepting concurrently from one shared
 * listener flow, backend and lifecycle across Actors of one Process (D217).
 */
final class ProtosTcpAcceptorActorDelegationTest {
    private static final Path CORE = Path.of("protos", "lib", "core");

    @Test
    void listenerAndWorkerAcceptsCompeteInOneBackendAndDeliverOnce() throws Exception {
        Fixture x = fixture();
        ProtosTcpAcceptorValue d1 = x.delegate(x.acquire(), x.w1a), d2 = x.delegate(x.acquire(), x.w2a);
        ProtosFutureValue[] futures = {
            future(x.listener, x.owner), future(d1, x.w1a), future(d2, x.w2a)
        };
        assertEquals(3, x.backend.accepts.size());
        AtomicInteger[] releases = {new AtomicInteger(), new AtomicInteger(), new AtomicInteger()};

        for (int i : new int[] {2, 0, 1}) {
            x.succeed(i, 46000 + i, releases[i]);
            assertEquals(ProtosFutureValue.State.PENDING, futures[i].state());
            assertTrue(futures[i].domain().dispatchOne());
        }

        Set<Object> delivered = Collections.newSetFromMap(new IdentityHashMap<>());
        for (int i = 0; i < 3; i++) {
            assertTrue(delivered.add(connection(futures[i])));
            assertEquals(0, releases[i].get());
        }
        assertEquals(
                List.of(x.w2.executionDomain(), x.ownerDomain(), x.w1.executionDomain()),
                x.materializedDomains());
        for (ProtosActivation activation : List.of(x.owner, x.w1a, x.w2a)) {
            assertFalse(activation.executionDomain().dispatchOne(), "no forwarding Actor work remains");
        }
    }

    @Test
    void delegatedAcceptOperationsFuturesAndConnectionsBelongToTheAcceptingActor() throws Exception {
        Fixture x = fixture();
        ProtosTcpAcceptorValue d1 = x.delegate(x.acquire(), x.w1a);
        ProtosFutureValue accepted = future(d1, x.w1a);
        assertSame(x.w1.executionDomain(), accepted.domain());

        x.succeed(0, 46100, new AtomicInteger());
        assertFalse(x.owner.executionDomain().dispatchOne());
        assertTrue(x.w1a.executionDomain().dispatchOne());

        ProtosTcpConnectionValue c = connection(accepted);
        assertEquals(List.of(x.w1.executionDomain()), x.materializedDomains());
        assertNotNull(c.remoteEndpointForRuntime());
        assertEquals(ProtosFutureValue.State.PENDING, c.readForRuntime(x.w1a, new ProtosIntegerValue(1)).state());
        assertThrows(IllegalArgumentException.class, () -> c.readForRuntime(x.owner, new ProtosIntegerValue(1)));
        assertThrows(IllegalArgumentException.class, () -> c.readForRuntime(x.w2a, new ProtosIntegerValue(1)));
    }

    @Test
    void asynchronousCallbackIsMaterializedOnlyByTheAcceptingActorDispatch() throws Exception {
        Fixture x = fixture();
        ProtosFutureValue accepted = future(x.delegate(x.acquire(), x.w1a), x.w1a);
        AtomicInteger released = new AtomicInteger();

        CompletableFuture.runAsync(() -> x.succeed(0, 46200, released)).get();

        assertEquals(ProtosFutureValue.State.PENDING, accepted.state());
        assertTrue(x.materializedDomains().isEmpty(), "no materialization on the callback thread");
        assertFalse(x.owner.executionDomain().dispatchOne());
        assertEquals(ProtosFutureValue.State.PENDING, accepted.state());
        assertTrue(x.w1a.executionDomain().dispatchOne());
        connection(accepted);
        assertEquals(List.of(Thread.currentThread()), x.materializedThreads());
        assertEquals(0, released.get());
    }

    @Test
    void listenerCloseCutsOverEveryActorWithActorLocalLifecycleErrors() throws Exception {
        Fixture x = fixture();
        ProtosFutureValue f0 = future(x.listener, x.owner);
        ProtosFutureValue f1 = future(x.delegate(x.acquire(), x.w1a), x.w1a);
        ProtosFutureValue f2 = future(x.delegate(x.acquire(), x.w2a), x.w2a);

        ProtosFutureValue close = x.close();

        assertFailedAs(f0, x, "IOLifecycleError");
        for (int i = 0; i < 3; i++) assertEquals(1, x.backend.accepts.get(i).cancels.get());
        assertEquals(ProtosFutureValue.State.PENDING, f1.state(), "worker Error is built by the worker");
        assertEquals(ProtosFutureValue.State.PENDING, f2.state(), "worker Error is built by the worker");
        assertFalse(x.owner.executionDomain().dispatchOne());
        assertEquals(0, x.backend.closeStarts, "release waits for worker retirement");

        assertTrue(x.w1a.executionDomain().dispatchOne());
        assertFailedAs(f1, x, "IOLifecycleError");
        assertEquals(0, x.backend.closeStarts);
        assertTrue(x.w2a.executionDomain().dispatchOne());
        assertFailedAs(f2, x, "IOLifecycleError");
        assertEquals(1, x.backend.closeStarts);

        Set<Object> errors = Collections.newSetFromMap(new IdentityHashMap<>());
        for (ProtosFutureValue f : List.of(f0, f1, f2)) assertTrue(errors.add(f.failedError().orElseThrow()));
        assertTrue(x.materializedDomains().isEmpty());
        x.backend.closeCompletion.succeeded();
        assertEquals(ProtosFutureValue.State.PENDING, close.state(), "published by the listener owner");
        assertTrue(x.owner.executionDomain().dispatchOne());
        assertEquals(ProtosFutureValue.State.RESOLVED, close.state());
        assertEquals(1, x.backend.closeStarts);
    }

    @Test
    void committedDeliveredWorkerConnectionSurvivesListenerClose() throws Exception {
        Fixture x = fixture();
        ProtosFutureValue accepted = future(x.delegate(x.acquire(), x.w1a), x.w1a);
        AtomicInteger released = new AtomicInteger();
        x.succeed(0, 46300, released);
        assertTrue(x.w1a.executionDomain().dispatchOne());
        ProtosTcpConnectionValue c = connection(accepted);

        ProtosFutureValue close = x.close();
        assertEquals(1, x.backend.closeStarts);
        x.backend.closeCompletion.succeeded();
        assertEquals(ProtosFutureValue.State.PENDING, close.state());
        assertTrue(x.owner.executionDomain().dispatchOne());
        assertEquals(ProtosFutureValue.State.RESOLVED, close.state());

        assertSame(c, accepted.resolvedValue().orElseThrow());
        assertEquals(ProtosFutureValue.State.PENDING, c.readForRuntime(x.w1a, new ProtosIntegerValue(1)).state());
        assertThrows(IllegalArgumentException.class, () -> c.readForRuntime(x.owner, new ProtosIntegerValue(1)));
        assertEquals(0, released.get());
    }

    @Test
    void workerPrecommitCancellationReleasesLateSuccessAndRetiresRegistration() throws Exception {
        Fixture x = fixture();
        ProtosFutureValue cancelled = future(x.delegate(x.acquire(), x.w1a), x.w1a);
        assertTrue(cancelled.cancelRequest());
        assertEquals(ProtosFutureValue.State.CANCELLED, cancelled.state());
        assertEquals(1, x.backend.accepts.get(0).cancels.get());

        AtomicInteger released = new AtomicInteger();
        x.succeed(0, 46400, released);

        assertEquals(1, released.get());
        assertEquals(ProtosFutureValue.State.CANCELLED, cancelled.state());
        assertFalse(x.w1a.executionDomain().dispatchOne());
        assertTrue(x.materializedDomains().isEmpty());
        x.close();
        assertEquals(1, x.backend.closeStarts, "no lifecycle registration remains");
    }

    @Test
    void committedCompletionDiscardedByWorkerTerminationIsReleasedAndRetired() throws Exception {
        Fixture x = fixture();
        ProtosFutureValue accepted = future(x.delegate(x.acquire(), x.w1a), x.w1a);
        AtomicInteger released = new AtomicInteger();
        x.succeed(0, 46500, released);
        ProtosFutureValue close = x.close();
        assertEquals(0, x.backend.closeStarts, "a committed undelivered accept blocks release");

        x.w1.beginTermination();

        assertEquals(1, released.get());
        assertEquals(1, x.backend.closeStarts);
        assertFalse(x.w1a.executionDomain().dispatchOne());
        assertNotEquals(ProtosFutureValue.State.RESOLVED, accepted.state());
        assertTrue(x.materializedDomains().isEmpty());
        assertEquals(1, released.get());
        x.backend.closeCompletion.succeeded();
        assertEquals(ProtosFutureValue.State.PENDING, close.state(), "published by the listener owner");
        assertTrue(x.owner.executionDomain().dispatchOne());
        assertEquals(ProtosFutureValue.State.RESOLVED, close.state());
    }

    @Test
    void committedCompletionRejectedByTerminatingWorkerIsReleasedAndRetired() throws Exception {
        Fixture x = fixture();
        ProtosFutureValue accepted = future(x.delegate(x.acquire(), x.w1a), x.w1a);
        AtomicInteger released = new AtomicInteger();
        // Termination runs discarded completions after its cutover but before it cancels I/O
        // operations: the backend callback therefore commits and then meets a rejected enqueue.
        ProtosFutureValue carrier = new ProtosFutureValue(x.prelude.futurePrototype(), x.w1.executionDomain());
        x.w1.executionDomain().enqueueTargetedFutureCompletionForRuntime(
                carrier, () -> false, () -> x.succeed(0, 46600, released));

        x.w1.beginTermination();

        assertEquals(1, released.get());
        assertTrue(x.materializedDomains().isEmpty());
        assertNotEquals(ProtosFutureValue.State.RESOLVED, accepted.state());
        assertFalse(x.w1a.executionDomain().dispatchOne());
        ProtosFutureValue close = x.close();
        assertEquals(1, x.backend.closeStarts, "the committed operation was retired");
        x.backend.closeCompletion.succeeded();
        assertEquals(ProtosFutureValue.State.PENDING, close.state());
        assertTrue(x.owner.executionDomain().dispatchOne());
        assertEquals(ProtosFutureValue.State.RESOLVED, close.state());
        assertEquals(1, released.get());
    }

    @Test
    void closeStartsOneReleaseOnlyAfterOwnerAndForeignOperationsRetire() throws Exception {
        Fixture x = fixture();
        ProtosFutureValue f0 = future(x.listener, x.owner);
        ProtosFutureValue f1 = future(x.delegate(x.acquire(), x.w1a), x.w1a);
        ProtosFutureValue f2 = future(x.delegate(x.acquire(), x.w2a), x.w2a);
        AtomicInteger released = new AtomicInteger();
        x.succeed(2, 46700, released);

        ProtosFutureValue close = x.close();
        assertFailedAs(f0, x, "IOLifecycleError");
        assertEquals(ProtosFutureValue.State.PENDING, f1.state());
        assertEquals(0, x.backend.closeStarts);

        f1.cancelRequest();
        assertEquals(0, x.backend.closeStarts);
        assertTrue(x.w1a.executionDomain().dispatchOne());
        assertFailedAs(f1, x, "IOLifecycleError");
        assertEquals(0, x.backend.closeStarts, "the committed worker accept still holds release");

        x.w2.beginTermination();
        assertEquals(1, released.get());
        assertEquals(1, x.backend.closeStarts);
        assertFalse(x.w2a.executionDomain().dispatchOne());
        assertNotEquals(ProtosFutureValue.State.RESOLVED, f2.state());
        assertTrue(x.materializedDomains().isEmpty());
        x.backend.closeCompletion.succeeded();
        assertEquals(ProtosFutureValue.State.PENDING, close.state(), "published by the listener owner");
        assertTrue(x.owner.executionDomain().dispatchOne());
        assertEquals(ProtosFutureValue.State.RESOLVED, close.state());
        assertEquals(1, x.backend.closeStarts);
    }

    @Test
    void foreignRetirementStartsReleaseWhoseOutcomeTheOwnerPublishes() throws Exception {
        for (CloseMode mode : CloseMode.values()) {
            Fixture x = fixture();
            x.backend.closeMode = mode;
            ProtosFutureValue accepted = future(x.delegate(x.acquire(), x.w1a), x.w1a);
            ProtosFutureValue close = x.close();
            assertEquals(0, x.backend.closeStarts, mode.name());

            // The worker retires the final pending operation; that retirement starts the release.
            assertTrue(x.w1a.executionDomain().dispatchOne());
            assertFailedAs(accepted, x, "IOLifecycleError");
            assertEquals(1, x.backend.closeStarts, mode.name());
            assertEquals(ProtosFutureValue.State.PENDING, close.state(), mode.name());
            assertTrue(close.failedError().isEmpty(), "no Error is built in the worker: " + mode);

            if (mode == CloseMode.ASYNCHRONOUS_SUCCESS) x.backend.closeCompletion.succeeded();
            if (mode == CloseMode.ASYNCHRONOUS_FAILURE) x.backend.closeCompletion.failed();
            assertEquals(ProtosFutureValue.State.PENDING, close.state(), mode.name());
            assertTrue(close.failedError().isEmpty(), "no Error is built off the owner domain: " + mode);
            assertFalse(x.w1a.executionDomain().dispatchOne());

            assertTrue(x.owner.executionDomain().dispatchOne());
            boolean failure = mode == CloseMode.SYNCHRONOUS_FAILURE || mode == CloseMode.ASYNCHRONOUS_FAILURE;
            if (failure) {
                assertFailedAs(close, x, "IOError");
                assertEquals(ProtosIoLifecycle.State.CLOSED_FAILED, x.listener.lifecycleForRuntime().state());
            } else {
                assertEquals(ProtosFutureValue.State.RESOLVED, close.state(), mode.name());
                assertEquals(ProtosIoLifecycle.State.CLOSED_SUCCESS, x.listener.lifecycleForRuntime().state());
            }

            // One release only; a later close shares the permanent outcome and leaves no work.
            ProtosFutureValue again = x.close();
            assertEquals(close.state(), again.state(), mode.name());
            if (failure) assertSame(close.failedError().orElseThrow(), again.failedError().orElseThrow());
            assertEquals(1, x.backend.closeStarts, mode.name());
            assertFalse(x.owner.executionDomain().dispatchOne());
            assertFalse(x.w1a.executionDomain().dispatchOne());
        }
    }

    @Test
    void ownerStartedReleaseWithHostThreadCompletionPublishesOnlyInOwnerDomain() throws Exception {
        for (CloseMode mode : List.of(CloseMode.ASYNCHRONOUS_SUCCESS, CloseMode.ASYNCHRONOUS_FAILURE)) {
            Fixture x = fixture();
            x.backend.closeMode = mode;
            x.delegate(x.acquire(), x.w1a);
            ProtosFutureValue close = x.close();
            assertEquals(1, x.backend.closeStarts, "the owner close starts the release itself");

            CompletableFuture.runAsync(() -> {
                if (mode == CloseMode.ASYNCHRONOUS_SUCCESS) x.backend.closeCompletion.succeeded();
                else x.backend.closeCompletion.failed();
            }).get();

            assertEquals(ProtosFutureValue.State.PENDING, close.state(), mode.name());
            assertTrue(close.failedError().isEmpty(), "no Error is built on the host thread: " + mode);
            assertFalse(x.w1a.executionDomain().dispatchOne());
            assertFalse(x.w2a.executionDomain().dispatchOne());
            assertTrue(x.owner.executionDomain().dispatchOne());
            if (mode == CloseMode.ASYNCHRONOUS_FAILURE) {
                assertFailedAs(close, x, "IOError");
            } else {
                assertEquals(ProtosFutureValue.State.RESOLVED, close.state());
            }
            assertFalse(x.owner.executionDomain().dispatchOne());
            assertEquals(1, x.backend.closeStarts);
        }
    }

    @Test
    void terminatingOwnerDiscardsForeignStartedCloseOutcomeHostOnly() throws Exception {
        for (CloseMode mode : List.of(CloseMode.SYNCHRONOUS_SUCCESS, CloseMode.SYNCHRONOUS_FAILURE)) {
            Fixture x = fixture();
            x.backend.closeMode = mode;
            ProtosFutureValue accepted = future(x.delegate(x.acquire(), x.w1a), x.w1a);
            ProtosFutureValue close = x.close();
            assertTrue(x.w1a.executionDomain().dispatchOne());
            assertFailedAs(accepted, x, "IOLifecycleError");
            assertEquals(1, x.backend.closeStarts);

            x.owner.executionDomain().currentActorForRuntime().orElseThrow().beginTermination();

            assertFalse(x.owner.executionDomain().dispatchOne());
            assertTrue(close.failedError().isEmpty(), "no guest Error after the owner discarded it");
            assertNotEquals(ProtosFutureValue.State.RESOLVED, close.state());
            assertEquals(
                    mode == CloseMode.SYNCHRONOUS_SUCCESS
                            ? ProtosIoLifecycle.State.CLOSED_SUCCESS
                            : ProtosIoLifecycle.State.CLOSED_FAILED,
                    x.listener.lifecycleForRuntime().state());
            assertEquals(1, x.backend.closeStarts);
        }
    }

    @Test
    void delegatedAcceptAfterCloseCutoverAnswersFreshFailedFuturesWithoutBackend() throws Exception {
        Fixture x = fixture();
        ProtosTcpAcceptorValue d1 = x.delegate(x.acquire(), x.w1a);
        ProtosFutureValue close = x.close();

        ProtosFutureValue closing1 = future(d1, x.w1a), closing2 = future(d1, x.w1a);
        x.backend.closeCompletion.succeeded();
        assertEquals(ProtosFutureValue.State.PENDING, close.state());
        assertTrue(x.owner.executionDomain().dispatchOne());
        assertEquals(ProtosFutureValue.State.RESOLVED, close.state());
        ProtosFutureValue closed = future(d1, x.w1a);

        Set<Object> errors = Collections.newSetFromMap(new IdentityHashMap<>());
        for (ProtosFutureValue f : List.of(closing1, closing2, closed)) {
            assertSame(x.w1.executionDomain(), f.domain());
            assertFailedAs(f, x, "IOLifecycleError");
            assertTrue(errors.add(f.failedError().orElseThrow()));
        }
        assertNotSame(closing1, closing2);
        assertTrue(x.backend.accepts.isEmpty());
    }

    @Test
    void acceptorsOfDifferentListenersShareNoAcceptanceOrCloseDomain() throws Exception {
        Fixture x = fixture();
        RecordingBackend otherBackend = new RecordingBackend();
        ProtosTcpListenerValue other = x.listener(otherBackend);
        ProtosTcpAcceptorValue d1 = x.delegate(x.acquire(), x.w1a);
        ProtosTcpAcceptorValue e1 = x.delegate(acquire(other, x.owner), x.w1a);

        ProtosFutureValue f = future(d1, x.w1a), g = future(e1, x.w1a);
        assertEquals(1, x.backend.accepts.size());
        assertEquals(1, otherBackend.accepts.size());

        x.close();
        assertTrue(x.w1a.executionDomain().dispatchOne());
        assertFailedAs(f, x, "IOLifecycleError");
        assertEquals(ProtosFutureValue.State.PENDING, g.state());
        assertEquals(0, otherBackend.accepts.get(0).cancels.get());
        assertEquals(ProtosIoLifecycle.State.OPEN, other.lifecycleForRuntime().state());
        assertEquals(0, otherBackend.closeStarts);

        AtomicInteger released = new AtomicInteger();
        otherBackend.accepts.get(0).completion.succeeded(
                new Object(), endpoint(51000), endpoint(46800), new NoOpConnectionBackend(), released::incrementAndGet);
        assertTrue(x.w1a.executionDomain().dispatchOne());
        connection(g);
        assertEquals(0, released.get());
    }

    @Test
    void structuralCloseOrFreezeOfAcceptorWrappersRevokesNothing() throws Exception {
        Fixture x = fixture();
        ProtosTcpAcceptorValue source = x.acquire();
        ProtosTcpAcceptorValue d1 = x.delegate(source, x.w1a);
        source.freeze();
        ProtosInvocation.invokeMessage(d1, "close", List.of(), x.w1a);
        assertTrue(source.isFrozen());
        assertTrue(d1.isClosed());
        assertEquals(ProtosIoLifecycle.State.OPEN, x.listener.lifecycleForRuntime().state());
        assertEquals(0, x.backend.closeStarts);

        ProtosFutureValue fromSource = future(source, x.owner), fromDestination = future(d1, x.w1a);
        assertEquals(2, x.backend.accepts.size());
        assertEquals(ProtosFutureValue.State.PENDING, fromSource.state());
        assertEquals(ProtosFutureValue.State.PENDING, fromDestination.state());

        x.close();
        assertFailedAs(fromSource, x, "IOLifecycleError");
        assertTrue(x.w1a.executionDomain().dispatchOne());
        assertFailedAs(fromDestination, x, "IOLifecycleError");
    }

    @Test
    void concreteAcceptorsStayNonTransferableOutsideSpawnInitialization() throws Exception {
        Fixture x = fixture();
        ProtosTcpAcceptorValue source = x.acquire();
        ProtosTcpAcceptorValue d1 = x.delegate(source, x.w1a);
        ProtosObjectValue holder = new ProtosObjectValue(ProtosObjectValue.rootObject());
        holder.createLocalSlot("acceptor", d1);

        for (String selector : List.of("send", "request")) {
            for (Object argument : List.of(d1, holder)) {
                ProtosSignalException failure = assertThrows(
                        ProtosSignalException.class,
                        () -> ProtosInvocation.invokeMessage(
                                x.w2.reference(), selector, List.of(new ProtosStringValue("take"), argument), x.w1a));
                assertSame(
                        x.prelude.bindings().readLocalSlot("NonTransferableValue").orElseThrow(),
                        failure.error().parent().orElseThrow());
            }
        }
        assertThrows(ProtosSignalException.class, () -> ProtosActorValueTransfer.snapshotValue(d1, x.w1a));
        assertThrows(ProtosSignalException.class, () -> ProtosActorValueTransfer.snapshotValue(source, x.owner));

        Method copy = Class.forName("com.guillermomolina.protos.execution.ProtosParallelRuntime$Transfer")
                .getDeclaredMethod("copy", Object.class, ProtosActivation.class, IdentityHashMap.class);
        copy.setAccessible(true);
        for (Object value : List.of(source, d1)) {
            InvocationTargetException failure = assertThrows(
                    InvocationTargetException.class,
                    () -> copy.invoke(null, value, x.owner, new IdentityHashMap<Object, Object>()));
            assertEquals("NonParallel", failure.getCause().getClass().getSimpleName());
        }

        // The authorized route remains available: a delegated capability may be spawned onward.
        ProtosTcpAcceptorValue d2 = x.delegate(d1, x.w1a, x.w2a);
        assertEquals(ProtosFutureValue.State.PENDING, future(d2, x.w2a).state());
        assertEquals(1, x.backend.accepts.size());
    }

    private static Fixture fixture() throws Exception {
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
        ProtosProcessRuntime process = new ProtosProcessRuntime(prelude.actorRefPrototypeForRuntime());
        ProtosActivation owner = activation(prelude, process.rootActorForRuntime());
        ProtosActor w1 = process.createHostedActorForRuntime(prelude.actorRefPrototypeForRuntime());
        ProtosActor w2 = process.createHostedActorForRuntime(prelude.actorRefPrototypeForRuntime());
        Fixture x = new Fixture(
                prelude, owner, w1, activation(prelude, w1), w2, activation(prelude, w2),
                new RecordingBackend(), Collections.synchronizedList(new ArrayList<>()));
        x.listener = x.listener(x.backend);
        return x;
    }

    private static ProtosActivation activation(ProtosPrelude prelude, ProtosActor actor) {
        return prelude.newModuleActivation(
                actor.moduleState(), null, prelude.newExecutionContext(), actor.executionDomain());
    }

    private static ProtosTcpAcceptorValue acquire(ProtosTcpListenerValue listener, ProtosActivation owner) {
        return assertInstanceOf(
                ProtosTcpAcceptorValue.class,
                ProtosInvocation.invokeMessage(listener, "acceptCapability", List.of(), owner));
    }

    private static ProtosFutureValue future(Object receiver, ProtosActivation activation) {
        return assertInstanceOf(
                ProtosFutureValue.class, ProtosInvocation.invokeMessage(receiver, "accept", List.of(), activation));
    }

    private static ProtosTcpConnectionValue connection(ProtosFutureValue future) {
        assertEquals(ProtosFutureValue.State.RESOLVED, future.state());
        return assertInstanceOf(ProtosTcpConnectionValue.class, future.resolvedValue().orElseThrow());
    }

    private static ProtosHostIpEndpoint endpoint(int port) {
        return new ProtosHostIpEndpoint(4, new byte[] {127, 0, 0, 1}, port);
    }

    private static void assertFailedAs(ProtosFutureValue f, Fixture x, String name) {
        assertEquals(ProtosFutureValue.State.FAILED, f.state());
        assertSame(
                x.prelude.bindings().readLocalSlot(name).orElseThrow(),
                f.failedError().orElseThrow().parent().orElseThrow());
    }

    private record Materialization(ProtosActorExecutionDomain domain, Thread thread) {}

    private static final class Fixture {
        final ProtosPrelude prelude;
        final ProtosActivation owner;
        final ProtosActor w1;
        final ProtosActivation w1a;
        final ProtosActor w2;
        final ProtosActivation w2a;
        final RecordingBackend backend;
        final List<Materialization> materialized;
        ProtosTcpListenerValue listener;

        Fixture(
                ProtosPrelude prelude,
                ProtosActivation owner,
                ProtosActor w1,
                ProtosActivation w1a,
                ProtosActor w2,
                ProtosActivation w2a,
                RecordingBackend backend,
                List<Materialization> materialized) {
            this.prelude = prelude;
            this.owner = owner;
            this.w1 = w1;
            this.w1a = w1a;
            this.w2 = w2;
            this.w2a = w2a;
            this.backend = backend;
            this.materialized = materialized;
        }

        /** Owner-domain listener whose materializer records where each connection is built. */
        ProtosTcpListenerValue listener(RecordingBackend listenerBackend) {
            return new ProtosTcpListenerValue(
                    prelude, new Object(), owner, listenerBackend, 51000,
                    (activation, state, local, remote, connectionBackend) -> {
                        materialized.add(new Materialization(activation.executionDomain(), Thread.currentThread()));
                        return ProtosStandardTcpListenerProtocol.materializeAcceptedConnection(
                                activation, state, local, remote, connectionBackend);
                    });
        }

        ProtosTcpAcceptorValue acquire() {
            return ProtosTcpAcceptorActorDelegationTest.acquire(listener, owner);
        }

        ProtosTcpAcceptorValue delegate(ProtosTcpAcceptorValue acceptor, ProtosActivation destination) {
            return delegate(acceptor, owner, destination);
        }

        /** The Actor.spawn initialization transfer: spawn-scoped snapshot, destination materialization. */
        ProtosTcpAcceptorValue delegate(
                ProtosTcpAcceptorValue acceptor, ProtosActivation source, ProtosActivation destination) {
            List<Object> snapshot = ProtosActorValueTransfer.snapshotSpawnArguments(List.of(acceptor), source);
            return assertInstanceOf(
                    ProtosTcpAcceptorValue.class,
                    ProtosActorValueTransfer.materializeArguments(snapshot, destination).get(0));
        }

        void succeed(int index, int remotePort, AtomicInteger released) {
            backend.accepts.get(index).completion.succeeded(
                    new Object(), endpoint(51000), endpoint(remotePort),
                    new NoOpConnectionBackend(), released::incrementAndGet);
        }

        ProtosFutureValue close() {
            return assertInstanceOf(
                    ProtosFutureValue.class, ProtosInvocation.invokeMessage(listener, "close", List.of(), owner));
        }

        ProtosActorExecutionDomain ownerDomain() {
            return owner.executionDomain();
        }

        List<ProtosActorExecutionDomain> materializedDomains() {
            synchronized (materialized) {
                return materialized.stream().map(Materialization::domain).toList();
            }
        }

        List<Thread> materializedThreads() {
            synchronized (materialized) {
                return materialized.stream().map(Materialization::thread).toList();
            }
        }
    }

    private enum CloseMode { SYNCHRONOUS_SUCCESS, SYNCHRONOUS_FAILURE, ASYNCHRONOUS_SUCCESS, ASYNCHRONOUS_FAILURE }

    private static final class RecordingBackend implements ProtosTcpListenerFlow.Backend {
        final List<AcceptInvocation> accepts = Collections.synchronizedList(new ArrayList<>());
        int closeStarts;
        ProtosTcpListenerFlow.CloseCompletion closeCompletion;
        CloseMode closeMode = CloseMode.ASYNCHRONOUS_SUCCESS;

        @Override
        public void close(ProtosTcpListenerFlow.CloseCompletion completion) {
            closeStarts++;
            closeCompletion = completion;
            if (closeMode == CloseMode.SYNCHRONOUS_SUCCESS) completion.succeeded();
            if (closeMode == CloseMode.SYNCHRONOUS_FAILURE) completion.failed();
        }

        @Override
        public ProtosTcpListenerFlow.Cancellation accept(ProtosTcpListenerFlow.AcceptCompletion completion) {
            AcceptInvocation invocation = new AcceptInvocation(completion);
            accepts.add(invocation);
            return invocation.cancels::incrementAndGet;
        }
    }

    private static final class AcceptInvocation {
        final ProtosTcpListenerFlow.AcceptCompletion completion;
        final AtomicInteger cancels = new AtomicInteger();

        AcceptInvocation(ProtosTcpListenerFlow.AcceptCompletion completion) {
            this.completion = completion;
        }
    }

    private static final class NoOpConnectionBackend implements ProtosTcpConnectionFlow.Backend {
        @Override public ProtosByteIoFlow.Cancellation read(int maxBytes, ProtosByteIoFlow.ReadCompletion completion) { return () -> {}; }
        @Override public ProtosByteIoFlow.Cancellation write(byte[] bytes, ProtosByteIoFlow.WriteCompletion completion) { return () -> {}; }
        @Override public ProtosByteIoFlow.Cancellation shutdownRead(ProtosByteIoFlow.ShutdownCompletion completion) { return () -> {}; }
        @Override public ProtosByteIoFlow.Cancellation shutdownWrite(ProtosByteIoFlow.ShutdownCompletion completion) { return () -> {}; }
        @Override public void close(ProtosByteIoFlow.ReceiverCompletion completion) { completion.succeeded(); }
    }
}
