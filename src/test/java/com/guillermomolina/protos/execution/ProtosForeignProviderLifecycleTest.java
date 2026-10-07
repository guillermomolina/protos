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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.runtime.ProtosActor;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosProcessRuntime;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** I082-B Process-owned lazy provider compartments and Actor-isolated sessions (PLAT053). */
class ProtosForeignProviderLifecycleTest {
    private static final ProtosForeignProviderId X = new ProtosForeignProviderId("x");
    private static final ProtosForeignProviderId Y = new ProtosForeignProviderId("y");

    @Test
    void zeroUseRuntimeHostProcessAndActorsStayCompletelyLazy() {
        FakeProvider x = new FakeProvider(X);
        try (ProtosPolyglotRuntimeHost host = hostWith(x)) {
            ProtosProcessRuntime process = newProcess();
            ProtosPolyglotProcessContext context = host(host, process);
            for (int i = 0; i < 9; i++) {
                process.createHostedActorForRuntime(actorRefPrototype());
            }

            assertEquals(0, x.factoryCalls.get());
            assertEquals(0, context.foreignProviderCompartmentCountForTesting());
            assertEquals(0, context.foreignProviderSessionCountForTesting());

            process.requestTerminationForRuntime();
            context.awaitTerminalDispositionForRuntime();
            assertTrue(context.isClosedForTesting());
            assertEquals(0, host.activeProcessContextCountForTesting());
        }
        assertEquals(0, x.factoryCalls.get());
        assertEquals(0, x.sessionsOpened.get());
    }

    @Test
    void firstUseOpensOneCompartmentAndOneSessionAmongTenActors() {
        FakeProvider x = new FakeProvider(X);
        try (ProtosPolyglotRuntimeHost host = hostWith(x)) {
            ProtosProcessRuntime process = newProcess();
            ProtosPolyglotProcessContext context = host(host, process);
            List<ProtosActor> actors = new ArrayList<>();
            actors.add(process.rootActorForRuntime());
            for (int i = 0; i < 9; i++) {
                actors.add(process.createHostedActorForRuntime(actorRefPrototype()));
            }

            ProtosForeignProviderSessionBinding first =
                    context.foreignSessionForRuntime(actors.get(3), X);
            ProtosForeignProviderSessionBinding again =
                    context.foreignSessionForRuntime(actors.get(3), X);

            assertSame(first, again);
            assertTrue(first.isOpenForRuntime());
            assertSame(x.sessions.get(0), first.sessionForRuntime());
            assertEquals(X, first.providerId());
            assertEquals(1, x.factoryCalls.get());
            assertEquals(1, x.sessionsOpened.get());
            assertEquals(1, context.foreignProviderCompartmentCountForTesting());
            assertEquals(1, context.foreignProviderSessionCountForTesting());
            // No foreign Context is created: the only Context is the Protos Process Context.
            assertEquals(1, host.activeProcessContextCountForTesting());

            terminate(process, context);
        }
    }

    @Test
    void actorsOfOneProcessShareCompartmentButObtainDistinctSessions() {
        FakeProvider x = new FakeProvider(X);
        try (ProtosPolyglotRuntimeHost host = hostWith(x)) {
            ProtosProcessRuntime process = newProcess();
            ProtosPolyglotProcessContext context = host(host, process);
            ProtosActor root = process.rootActorForRuntime();
            ProtosActor child = process.createHostedActorForRuntime(actorRefPrototype());

            ProtosForeignProviderSessionBinding rootSession =
                    context.foreignSessionForRuntime(root, X);
            ProtosForeignProviderSessionBinding childSession =
                    context.foreignSessionForRuntime(child, X);

            assertNotSame(rootSession, childSession);
            assertNotSame(rootSession.sessionForRuntime(), childSession.sessionForRuntime());
            assertEquals(1, x.factoryCalls.get());
            assertEquals(1, x.compartments.size());
            assertEquals(2, x.compartments.get(0).sessionsOpened.get());
            assertEquals(2, context.foreignProviderSessionCountForTesting());

            terminate(process, context);
        }
    }

    @Test
    void processesOfOneRuntimeHostOwnDistinctCompartments() {
        FakeProvider x = new FakeProvider(X);
        try (ProtosPolyglotRuntimeHost host = hostWith(x)) {
            ProtosProcessRuntime first = newProcess();
            ProtosProcessRuntime second = newProcess();
            ProtosPolyglotProcessContext firstContext = host(host, first);
            ProtosPolyglotProcessContext secondContext = host(host, second);

            ProtosForeignProviderSessionBinding a =
                    firstContext.foreignSessionForRuntime(first.rootActorForRuntime(), X);
            ProtosForeignProviderSessionBinding b =
                    secondContext.foreignSessionForRuntime(second.rootActorForRuntime(), X);

            assertNotSame(a, b);
            assertEquals(2, x.factoryCalls.get());
            assertNotSame(x.compartments.get(0), x.compartments.get(1));

            terminate(first, firstContext);
            assertEquals(1, x.compartments.get(0).closeCalls.get());
            assertEquals(0, x.compartments.get(1).closeCalls.get());
            assertTrue(b.isOpenForRuntime());

            terminate(second, secondContext);
            assertEquals(1, x.compartments.get(1).closeCalls.get());
        }
    }

    @Test
    void differentProvidersRemainIndependent() {
        FakeProvider x = new FakeProvider(X);
        FakeProvider y = new FakeProvider(Y);
        try (ProtosPolyglotRuntimeHost host = hostWith(x, y)) {
            ProtosProcessRuntime process = newProcess();
            ProtosPolyglotProcessContext context = host(host, process);
            ProtosActor root = process.rootActorForRuntime();

            context.foreignSessionForRuntime(root, X);
            assertEquals(1, x.factoryCalls.get());
            assertEquals(0, y.factoryCalls.get());

            ProtosForeignProviderSessionBinding ySession =
                    context.foreignSessionForRuntime(root, Y);
            assertEquals(Y, ySession.providerId());
            assertEquals(1, y.factoryCalls.get());
            assertEquals(2, context.foreignProviderCompartmentCountForTesting());
            assertEquals(2, context.foreignProviderSessionCountForTesting());

            terminate(process, context);
            assertEquals(1, x.compartments.get(0).closeCalls.get());
            assertEquals(1, y.compartments.get(0).closeCalls.get());
        }
    }

    @Test
    void unknownProviderFailsWithoutInvokingAnyFactory() {
        FakeProvider x = new FakeProvider(X);
        try (ProtosPolyglotRuntimeHost host = hostWith(x)) {
            ProtosProcessRuntime process = newProcess();
            ProtosPolyglotProcessContext context = host(host, process);

            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            context.foreignSessionForRuntime(
                                    process.rootActorForRuntime(),
                                    new ProtosForeignProviderId("z")));
            assertEquals(0, x.factoryCalls.get());
            assertEquals(0, context.foreignProviderCompartmentCountForTesting());

            terminate(process, context);
        }
    }

    @Test
    void unavailableProviderFailsClosedWithoutInvokingFactory() {
        AtomicInteger factoryCalls = new AtomicInteger();
        ProtosForeignProviderRegistry registry =
                ProtosForeignProviderRegistry.of(
                        List.of(
                                new ProtosForeignProviderDescriptor(
                                        X,
                                        ProtosForeignProviderExecutionProfile.UNAVAILABLE,
                                        () -> {
                                            factoryCalls.incrementAndGet();
                                            throw new AssertionError("must not be invoked");
                                        })));
        try (ProtosPolyglotRuntimeHost host =
                ProtosPolyglotRuntimeHost.openWithForeignProvidersForTesting(registry)) {
            ProtosProcessRuntime process = newProcess();
            ProtosPolyglotProcessContext context = host(host, process);

            assertThrows(
                    IllegalStateException.class,
                    () -> context.foreignSessionForRuntime(process.rootActorForRuntime(), X));
            assertEquals(0, factoryCalls.get());
            assertEquals(0, context.foreignProviderCompartmentCountForTesting());

            terminate(process, context);
        }
    }

    @Test
    void actorOfAnotherProcessIsRejectedWithoutInvokingFactory() {
        FakeProvider x = new FakeProvider(X);
        try (ProtosPolyglotRuntimeHost host = hostWith(x)) {
            ProtosProcessRuntime first = newProcess();
            ProtosProcessRuntime second = newProcess();
            ProtosPolyglotProcessContext firstContext = host(host, first);
            ProtosPolyglotProcessContext secondContext = host(host, second);

            assertThrows(
                    IllegalArgumentException.class,
                    () -> firstContext.foreignSessionForRuntime(second.rootActorForRuntime(), X));
            assertEquals(0, x.factoryCalls.get());

            terminate(first, firstContext);
            terminate(second, secondContext);
        }
    }

    @Test
    void failedCompartmentConstructionIsNotCachedAndCanRetry() {
        FakeProvider x = new FakeProvider(X);
        x.failNextCompartments.set(1);
        try (ProtosPolyglotRuntimeHost host = hostWith(x)) {
            ProtosProcessRuntime process = newProcess();
            ProtosPolyglotProcessContext context = host(host, process);
            ProtosActor root = process.rootActorForRuntime();

            IllegalStateException failure =
                    assertThrows(
                            IllegalStateException.class,
                            () -> context.foreignSessionForRuntime(root, X));
            assertEquals("injected compartment failure", failure.getMessage());
            assertEquals(0, context.foreignProviderCompartmentCountForTesting());
            assertEquals(0, context.foreignProviderSessionCountForTesting());

            ProtosForeignProviderSessionBinding session = context.foreignSessionForRuntime(root, X);
            assertTrue(session.isOpenForRuntime());
            assertEquals(2, x.factoryCalls.get());
            assertEquals(1, context.foreignProviderCompartmentCountForTesting());

            terminate(process, context);
        }
    }

    @Test
    void failedSessionConstructionIsNotCachedAndCanRetry() {
        FakeProvider x = new FakeProvider(X);
        x.failNextSessions.set(1);
        try (ProtosPolyglotRuntimeHost host = hostWith(x)) {
            ProtosProcessRuntime process = newProcess();
            ProtosPolyglotProcessContext context = host(host, process);
            ProtosActor root = process.rootActorForRuntime();

            IllegalStateException failure =
                    assertThrows(
                            IllegalStateException.class,
                            () -> context.foreignSessionForRuntime(root, X));
            assertEquals("injected session failure", failure.getMessage());
            assertEquals(1, context.foreignProviderCompartmentCountForTesting());
            assertEquals(0, context.foreignProviderSessionCountForTesting());

            ProtosForeignProviderSessionBinding session = context.foreignSessionForRuntime(root, X);
            assertTrue(session.isOpenForRuntime());
            assertEquals(1, x.factoryCalls.get());
            assertEquals(2, x.compartments.get(0).sessionAttempts.get());
            assertEquals(1, context.foreignProviderSessionCountForTesting());

            terminate(process, context);
        }
    }

    @Test
    void actorTerminationClosesOnlyThatActorsSessionsAndRejectsLaterUse() {
        FakeProvider x = new FakeProvider(X);
        FakeProvider y = new FakeProvider(Y);
        try (ProtosPolyglotRuntimeHost host = hostWith(x, y)) {
            ProtosProcessRuntime process = newProcess();
            ProtosPolyglotProcessContext context = host(host, process);
            ProtosActor root = process.rootActorForRuntime();
            ProtosActor child = process.createHostedActorForRuntime(actorRefPrototype());
            ProtosForeignProviderSessionBinding rootX = context.foreignSessionForRuntime(root, X);
            ProtosForeignProviderSessionBinding childX = context.foreignSessionForRuntime(child, X);
            ProtosForeignProviderSessionBinding childY = context.foreignSessionForRuntime(child, Y);
            FakeSession childXSession = (FakeSession) childX.sessionForRuntime();
            FakeSession childYSession = (FakeSession) childY.sessionForRuntime();

            child.requestTerminationForRuntime();

            assertEquals(ProtosActor.LifecycleState.TERMINATED, child.lifecycleState());
            assertFalse(childX.isOpenForRuntime());
            assertFalse(childY.isOpenForRuntime());
            assertEquals(1, childXSession.closeCalls.get());
            assertEquals(1, childYSession.closeCalls.get());
            assertThrows(IllegalStateException.class, childX::sessionForRuntime);
            assertTrue(rootX.isOpenForRuntime());
            assertEquals(0, ((FakeSession) rootX.sessionForRuntime()).closeCalls.get());
            // Compartments are Process-owned and survive Actor termination.
            assertEquals(0, x.compartments.get(0).closeCalls.get());
            assertEquals(2, context.foreignProviderCompartmentCountForTesting());
            assertEquals(1, context.foreignProviderSessionCountForTesting());

            // No session resurrection for the terminated Actor.
            int sessionsOpened = x.sessionsOpened.get();
            assertThrows(
                    IllegalStateException.class, () -> context.foreignSessionForRuntime(child, X));
            assertEquals(sessionsOpened, x.sessionsOpened.get());

            terminate(process, context);
            assertFalse(rootX.isOpenForRuntime());
        }
    }

    @Test
    void processTerminationClosesSessionsThenCompartmentsExactlyOnceAndRejectsAdmission() {
        FakeProvider x = new FakeProvider(X);
        FakeProvider y = new FakeProvider(Y);
        try (ProtosPolyglotRuntimeHost host = hostWith(x, y)) {
            ProtosProcessRuntime process = newProcess();
            ProtosPolyglotProcessContext context = host(host, process);
            ProtosActor root = process.rootActorForRuntime();
            ProtosActor child = process.createHostedActorForRuntime(actorRefPrototype());
            context.foreignSessionForRuntime(root, X);
            context.foreignSessionForRuntime(child, X);
            context.foreignSessionForRuntime(child, Y);
            FakeCompartment xCompartment = x.compartments.get(0);

            terminate(process, context);

            assertEquals(1, xCompartment.closeCalls.get());
            assertEquals(1, y.compartments.get(0).closeCalls.get());
            assertTrue(xCompartment.allSessionsClosedBeforeCompartmentClose);
            for (FakeSession session : x.sessions) {
                assertEquals(1, session.closeCalls.get());
            }
            assertEquals(0, context.foreignProviderCompartmentCountForTesting());
            assertEquals(0, context.foreignProviderSessionCountForTesting());

            int factoryCalls = x.factoryCalls.get();
            assertThrows(
                    IllegalStateException.class, () -> context.foreignSessionForRuntime(root, X));
            assertEquals(factoryCalls, x.factoryCalls.get());

            // Repeated terminal notification never closes a compartment twice.
            context.processTerminatedForRuntime();
            assertEquals(1, xCompartment.closeCalls.get());
        }
        // The RuntimeHost closes normally: it does not own compartments.
    }

    @Test
    void acquisitionAfterProcessTerminationBeginsIsRejected() {
        FakeProvider x = new FakeProvider(X);
        try (ProtosPolyglotRuntimeHost host = hostWith(x)) {
            ProtosProcessRuntime process = newProcess();
            ProtosPolyglotProcessContext context = host(host, process);
            ProtosActor root = process.rootActorForRuntime();

            process.requestTerminationForRuntime();

            assertThrows(
                    IllegalStateException.class, () -> context.foreignSessionForRuntime(root, X));
            assertEquals(0, x.factoryCalls.get());
            context.awaitTerminalDispositionForRuntime();
        }
    }

    @Test
    void sessionCloseFailureDoesNotStopRemainingCleanup() {
        FakeProvider x = new FakeProvider(X);
        FakeProvider y = new FakeProvider(Y);
        try (ProtosPolyglotRuntimeHost host = hostWith(x, y)) {
            ProtosProcessRuntime process = newProcess();
            ProtosPolyglotProcessContext context = host(host, process);
            ProtosActor root = process.rootActorForRuntime();
            ProtosActor child = process.createHostedActorForRuntime(actorRefPrototype());
            FakeSession childX =
                    (FakeSession) context.foreignSessionForRuntime(child, X).sessionForRuntime();
            FakeSession childY =
                    (FakeSession) context.foreignSessionForRuntime(child, Y).sessionForRuntime();
            FakeSession rootX =
                    (FakeSession) context.foreignSessionForRuntime(root, X).sessionForRuntime();
            childX.closeFailure = new IllegalStateException("child x close failure");
            rootX.closeFailure = new IllegalStateException("root x close failure");

            child.requestTerminationForRuntime();

            assertEquals(ProtosActor.LifecycleState.TERMINATED, child.lifecycleState());
            assertEquals(1, childX.closeCalls.get());
            assertEquals(1, childY.closeCalls.get());

            process.requestTerminationForRuntime();
            assertEquals(ProtosProcessRuntime.LifecycleState.TERMINATED, process.lifecycleState());
            assertEquals(1, rootX.closeCalls.get());
            assertEquals(1, x.compartments.get(0).closeCalls.get());
            assertEquals(1, y.compartments.get(0).closeCalls.get());
            assertTrue(context.isClosedForTesting());

            IllegalStateException disposition =
                    assertThrows(
                            IllegalStateException.class,
                            context::awaitTerminalDispositionForRuntime);
            assertSame(childX.closeFailure, disposition.getCause());
            assertEquals(1, disposition.getCause().getSuppressed().length);
            assertSame(rootX.closeFailure, disposition.getCause().getSuppressed()[0]);
        }
    }

    @Test
    void compartmentCloseFailureReachesTerminalHostDisposition() {
        FakeProvider x = new FakeProvider(X);
        FakeProvider y = new FakeProvider(Y);
        try (ProtosPolyglotRuntimeHost host = hostWith(x, y)) {
            ProtosProcessRuntime process = newProcess();
            ProtosPolyglotProcessContext context = host(host, process);
            ProtosActor root = process.rootActorForRuntime();
            context.foreignSessionForRuntime(root, X);
            context.foreignSessionForRuntime(root, Y);
            x.compartments.get(0).closeFailure = new IllegalStateException("x close failure");

            process.requestTerminationForRuntime();

            assertEquals(1, y.compartments.get(0).closeCalls.get());
            assertTrue(context.isClosedForTesting());
            assertEquals(0, host.activeProcessContextCountForTesting());
            IllegalStateException disposition =
                    assertThrows(
                            IllegalStateException.class,
                            context::awaitTerminalDispositionForRuntime);
            assertSame(x.compartments.get(0).closeFailure, disposition.getCause());
            assertEquals(ProtosProcessRuntime.LifecycleState.TERMINATED, process.lifecycleState());
        }
    }

    @Test
    void racingFirstProcessAcquisitionPublishesExactlyOneCompartment() throws Exception {
        FakeProvider x = new FakeProvider(X);
        x.factoryGate = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try (ProtosPolyglotRuntimeHost host = hostWith(x)) {
            ProtosProcessRuntime process = newProcess();
            ProtosPolyglotProcessContext context = host(host, process);
            ProtosActor root = process.rootActorForRuntime();
            ProtosActor child = process.createHostedActorForRuntime(actorRefPrototype());

            Future<ProtosForeignProviderSessionBinding> a =
                    executor.submit(() -> context.foreignSessionForRuntime(root, X));
            assertTrue(x.factoryEntered.await(10, TimeUnit.SECONDS));
            Future<ProtosForeignProviderSessionBinding> b =
                    executor.submit(() -> context.foreignSessionForRuntime(child, X));
            x.factoryGate.countDown();

            ProtosForeignProviderSessionBinding rootSession = a.get(10, TimeUnit.SECONDS);
            ProtosForeignProviderSessionBinding childSession = b.get(10, TimeUnit.SECONDS);
            assertNotSame(rootSession, childSession);
            assertEquals(1, x.factoryCalls.get());
            assertEquals(1, context.foreignProviderCompartmentCountForTesting());
            assertEquals(2, x.compartments.get(0).sessionsOpened.get());

            terminate(process, context);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void racingSameActorAcquisitionPublishesExactlyOneSession() throws Exception {
        FakeProvider x = new FakeProvider(X);
        x.sessionGate = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try (ProtosPolyglotRuntimeHost host = hostWith(x)) {
            ProtosProcessRuntime process = newProcess();
            ProtosPolyglotProcessContext context = host(host, process);
            ProtosActor root = process.rootActorForRuntime();

            Future<ProtosForeignProviderSessionBinding> a =
                    executor.submit(() -> context.foreignSessionForRuntime(root, X));
            assertTrue(x.sessionEntered.await(10, TimeUnit.SECONDS));
            Future<ProtosForeignProviderSessionBinding> b =
                    executor.submit(() -> context.foreignSessionForRuntime(root, X));
            x.sessionGate.countDown();

            assertSame(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS));
            assertEquals(1, x.sessionsOpened.get());
            assertEquals(1, context.foreignProviderSessionCountForTesting());

            terminate(process, context);
        } finally {
            executor.shutdownNow();
        }
    }

    private static void terminate(
            ProtosProcessRuntime process, ProtosPolyglotProcessContext context) {
        process.requestTerminationForRuntime();
        assertEquals(ProtosProcessRuntime.LifecycleState.TERMINATED, process.lifecycleState());
        context.awaitTerminalDispositionForRuntime();
        assertTrue(context.isClosedForTesting());
        assertEquals(0, context.foreignProviderCompartmentCountForTesting());
        assertEquals(0, context.foreignProviderSessionCountForTesting());
    }

    private static ProtosPolyglotRuntimeHost hostWith(FakeProvider... providers) {
        List<ProtosForeignProviderDescriptor> descriptors = new ArrayList<>();
        for (FakeProvider provider : providers) {
            descriptors.add(
                    new ProtosForeignProviderDescriptor(
                            provider.id,
                            ProtosForeignProviderExecutionProfile.RESTRICTED_IN_PROCESS,
                            ProtosForeignInertRestriction.zeroAuthority(),
                            provider,
                            Optional.empty(),
                            ProtosForeignValueAdapter.opaque("unknown")));
        }
        return ProtosPolyglotRuntimeHost.openWithForeignProvidersForTesting(
                ProtosForeignProviderRegistry.of(descriptors));
    }

    private static ProtosPolyglotProcessContext host(
            ProtosPolyglotRuntimeHost host, ProtosProcessRuntime process) {
        return host.hostProcess(
                process,
                InputStream.nullInputStream(),
                OutputStream.nullOutputStream(),
                OutputStream.nullOutputStream());
    }

    private static ProtosProcessRuntime newProcess() {
        return new ProtosProcessRuntime(actorRefPrototype());
    }

    private static ProtosObjectValue actorRefPrototype() {
        return new ProtosObjectValue(ProtosObjectValue.rootObject());
    }

    /** Inert provider double: no guest language, Context, or host authority. */
    private static final class FakeProvider implements ProtosForeignProviderFactory {
        final ProtosForeignProviderId id;
        final AtomicInteger factoryCalls = new AtomicInteger();
        final AtomicInteger sessionsOpened = new AtomicInteger();
        final AtomicInteger failNextCompartments = new AtomicInteger();
        final AtomicInteger failNextSessions = new AtomicInteger();
        final List<FakeCompartment> compartments = new CopyOnWriteArrayList<>();
        final List<FakeSession> sessions = new CopyOnWriteArrayList<>();
        final CountDownLatch factoryEntered = new CountDownLatch(1);
        final CountDownLatch sessionEntered = new CountDownLatch(1);
        volatile CountDownLatch factoryGate;
        volatile CountDownLatch sessionGate;

        FakeProvider(ProtosForeignProviderId id) {
            this.id = id;
        }

        @Override
        public ProtosForeignProviderCompartment openCompartment() {
            factoryCalls.incrementAndGet();
            factoryEntered.countDown();
            awaitGate(factoryGate);
            if (failNextCompartments.getAndUpdate(n -> Math.max(0, n - 1)) > 0) {
                throw new IllegalStateException("injected compartment failure");
            }
            FakeCompartment compartment = new FakeCompartment(this);
            compartments.add(compartment);
            return compartment;
        }
    }

    private static final class FakeCompartment implements ProtosForeignProviderCompartment {
        final FakeProvider provider;
        final AtomicInteger sessionAttempts = new AtomicInteger();
        final AtomicInteger sessionsOpened = new AtomicInteger();
        final AtomicInteger closeCalls = new AtomicInteger();
        final List<FakeSession> sessions = new CopyOnWriteArrayList<>();
        volatile RuntimeException closeFailure;
        volatile boolean allSessionsClosedBeforeCompartmentClose;

        FakeCompartment(FakeProvider provider) {
            this.provider = provider;
        }

        @Override
        public ProtosForeignProviderId providerId() {
            return provider.id;
        }

        @Override
        public ProtosForeignProviderSession openSession() {
            sessionAttempts.incrementAndGet();
            provider.sessionEntered.countDown();
            awaitGate(provider.sessionGate);
            if (provider.failNextSessions.getAndUpdate(n -> Math.max(0, n - 1)) > 0) {
                throw new IllegalStateException("injected session failure");
            }
            FakeSession session = new FakeSession();
            sessions.add(session);
            provider.sessions.add(session);
            sessionsOpened.incrementAndGet();
            provider.sessionsOpened.incrementAndGet();
            return session;
        }

        @Override
        public void close() {
            closeCalls.incrementAndGet();
            allSessionsClosedBeforeCompartmentClose =
                    sessions.stream().allMatch(session -> session.closeCalls.get() > 0);
            if (closeFailure != null) {
                throw closeFailure;
            }
        }
    }

    private static final class FakeSession implements ProtosForeignProviderSession {
        final AtomicInteger closeCalls = new AtomicInteger();
        volatile RuntimeException closeFailure;

        @Override
        public void close() {
            closeCalls.incrementAndGet();
            if (closeFailure != null) {
                throw closeFailure;
            }
        }
    }

    private static void awaitGate(CountDownLatch gate) {
        if (gate == null) {
            return;
        }
        try {
            if (!gate.await(10, TimeUnit.SECONDS)) {
                throw new AssertionError("test gate timed out");
            }
        } catch (InterruptedException interruption) {
            Thread.currentThread().interrupt();
            throw new AssertionError(interruption);
        }
    }
}
