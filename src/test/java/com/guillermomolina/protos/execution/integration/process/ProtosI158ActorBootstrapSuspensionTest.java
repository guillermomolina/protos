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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosActor;
import com.guillermomolina.protos.runtime.ProtosActorRefValue;
import com.guillermomolina.protos.runtime.ProtosFutureValue;
import com.guillermomolina.protos.runtime.ProtosNumericValueSupport;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosProcessRuntime;
import com.guillermomolina.protos.runtime.ProtosStringValue;
import com.guillermomolina.protos.runtime.io.ProtosTcpAcceptorValue;
import com.guillermomolina.protos.runtime.io.ProtosTcpListenerFlow;
import com.guillermomolina.protos.runtime.io.ProtosTcpListenerValue;
import com.guillermomolina.protos.runtime.modules.ProtosModuleKey;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.Isolated;

/**
 * I158 / D251-A1: deterministic Task-free suspension of a spawned Actor's initialization.
 *
 * <p>The awaited Future is a transferred TcpAcceptor's {@code accept()} whose backend completion is
 * retained by the test, so it is truly pending when {@code value()} runs and becomes terminal only
 * when the test decides, through the Actor-private targeted runtime-completion path. Scheduler
 * carriers run only when the test drains a manual executor; no sleep decides any race.
 */
@Isolated("I158 tests install process-wide bootstrap probes")
@Timeout(value = 10, unit = TimeUnit.SECONDS)
final class ProtosI158ActorBootstrapSuspensionTest {
    private static final Path CORE = Path.of("protos", "lib", "core");

    /** SOURCE: effects before the await happen once; a Task and a message stay excluded. */
    private static final String SOURCE_BOOT =
            """
            effects: 0
            boot: (acceptor) => {
                effects = effects + 1
                (() => { effects = effects + 100 }).future()
                outcome: Error.handle(
                    () => {
                        acceptor.accept().value()
                        "accepted"
                    },
                    (error) => { "failed" })
                {
                    outcome: outcome
                    bump: () => {
                        effects = effects + 1000
                        null
                    }
                }
            }
            """;

    @Test
    void spawnReturnsTheRefAndTheSuspendedInitializationResumesExactlyOnce() throws Exception {
        Harness x = Harness.create("app", ProtosModuleKey.source("canonical:app"), SOURCE_BOOT);
        ProtosTcpAcceptorValue acceptor = x.acquire();

        ProtosActorRefValue reference = x.invokeSpawn(acceptor);
        ProtosActor spawned = x.local(reference);
        assertEquals(ProtosActor.LifecycleState.INITIALIZING, spawned.lifecycleState());

        x.executor.drain();
        // Suspended: still INITIALIZING, no behavior, carrier released, effects exactly once.
        assertEquals(ProtosActor.LifecycleState.INITIALIZING, spawned.lifecycleState());
        assertTrue(spawned.currentBehavior().isEmpty());
        assertEquals(1, x.backend.pending.size());
        assertEquals(0, x.executor.size(), "excluded Task must not reschedule the Actor");
        assertEquals(1L, x.sourceSlot(spawned, "effects"));

        // A message accepted before READY is retained, not dispatched.
        ProtosInvocation.invokeMessage(
                reference, "send", List.of(new ProtosStringValue("bump")), x.activation);
        x.executor.drain();
        assertEquals(1L, x.sourceSlot(spawned, "effects"));
        assertTrue(spawned.currentBehavior().isEmpty());

        x.backend.pending.pollFirst().failed();
        x.executor.drain();

        assertEquals(ProtosActor.LifecycleState.READY, spawned.lifecycleState());
        ProtosObjectValue behavior = spawned.currentBehavior().orElseThrow();
        assertEquals("failed", ((ProtosStringValue) behavior.readLocalSlot("outcome").orElseThrow()).value());
        // The pre-await effect ran once; the excluded Task and the retained message ran after READY.
        assertEquals(1101L, x.sourceSlot(spawned, "effects"));
        assertSame(behavior, spawned.currentBehavior().orElseThrow());
    }

    @Test
    void moduleBindingSuspendsAndKeepsItsPublicReceiver() throws Exception {
        Harness x =
                Harness.create(
                        "mod:app",
                        ProtosModuleKey.module("canonical:mod:app"),
                        """
                        privateValue: 7
                        this.boot: (acceptor) => {
                            owner: this
                            outcome: Error.handle(
                                () => { acceptor.accept().value() },
                                (error) => { privateValue })
                            { owner: owner; outcome: outcome }
                        }
                        """);
        ProtosActor spawned = x.local(x.invokeSpawn(x.acquire()));
        x.executor.drain();
        assertEquals(ProtosActor.LifecycleState.INITIALIZING, spawned.lifecycleState());

        x.backend.pending.pollFirst().failed();
        x.executor.drain();

        ProtosObjectValue behavior = spawned.currentBehavior().orElseThrow();
        ProtosObjectValue publicModule =
                spawned.moduleState()
                        .lookup(ProtosModuleKey.module("canonical:mod:app"))
                        .orElseThrow()
                        .instance();
        assertSame(publicModule, behavior.readLocalSlot("owner").orElseThrow());
        assertEquals(
                "7",
                ProtosNumericValueSupport.integerDecimalText(
                        behavior.readLocalSlot("outcome").orElseThrow()));
    }

    @Test
    void twoSequentialSuspensionsBelongToTheSameInitialization() throws Exception {
        Harness x =
                Harness.create(
                        "app",
                        ProtosModuleKey.source("canonical:app"),
                        """
                        starts: 0
                        boot: (acceptor) => {
                            starts = starts + 1
                            first: Error.handle(() => { acceptor.accept().value() }, (error) => { 1 })
                            second: Error.handle(() => { acceptor.accept().value() }, (error) => { 2 })
                            { total: first + second }
                        }
                        """);
        ProtosActor spawned = x.local(x.invokeSpawn(x.acquire()));
        x.executor.drain();
        x.backend.pending.pollFirst().failed();
        x.executor.drain();
        assertEquals(ProtosActor.LifecycleState.INITIALIZING, spawned.lifecycleState());
        assertEquals(1, x.backend.pending.size());

        x.backend.pending.pollFirst().failed();
        x.executor.drain();

        assertEquals(ProtosActor.LifecycleState.READY, spawned.lifecycleState());
        assertEquals(1L, x.sourceSlot(spawned, "starts"));
        assertEquals(
                "3",
                ProtosNumericValueSupport.integerDecimalText(
                        spawned.currentBehavior().orElseThrow().readLocalSlot("total").orElseThrow()));
    }

    @Test
    void anUnhandledErrorObservedOnResumptionIsFatalAndNeverPublishesReady() throws Exception {
        Harness x =
                Harness.create(
                        "app",
                        ProtosModuleKey.source("canonical:app"),
                        "boot: (acceptor) => {\n    acceptor.accept().value()\n    {}\n}\n");
        ProtosActor spawned = x.local(x.invokeSpawn(x.acquire()));
        x.executor.drain();
        assertEquals(ProtosActor.LifecycleState.INITIALIZING, spawned.lifecycleState());

        x.backend.pending.pollFirst().failed();
        x.executor.drain();

        assertEquals(ProtosActor.LifecycleState.TERMINATED, spawned.lifecycleState());
        assertTrue(spawned.currentBehavior().isEmpty());
    }

    @Test
    void terminationWhileSuspendedInvalidatesTheContinuation() throws Exception {
        Harness x =
                Harness.create(
                        "app",
                        ProtosModuleKey.source("canonical:app"),
                        """
                        after: 0
                        boot: (acceptor) => {
                            Error.handle(() => { acceptor.accept().value() }, (error) => { null })
                            after = after + 1
                            {}
                        }
                        """);
        ProtosActor spawned = x.local(x.invokeSpawn(x.acquire()));
        x.executor.drain();
        assertEquals(ProtosActor.LifecycleState.INITIALIZING, spawned.lifecycleState());

        spawned.requestTerminationForRuntime();
        x.executor.drain();

        // The deferred abandon ran once under domain ownership and let termination complete.
        assertEquals(ProtosActor.LifecycleState.TERMINATED, spawned.lifecycleState());
        assertTrue(spawned.currentBehavior().isEmpty());
        assertEquals(0, x.executor.size());

        // A late completion cannot revive the invalidated initialization.
        try {
            x.backend.pending.pollFirst().failed();
        } catch (IllegalStateException terminated) {
            // A terminated domain may refuse the late completion outright.
        }
        x.executor.drain();
        assertEquals(ProtosActor.LifecycleState.TERMINATED, spawned.lifecycleState());
        assertTrue(spawned.currentBehavior().isEmpty());
    }

    @Test
    void aFutureTerminalBeforeRegistrationNeverSuspends() throws Exception {
        Harness x = Harness.create("app", ProtosModuleKey.source("canonical:app"), SOURCE_BOOT);
        x.backend.failImmediately = true;
        ProtosActor spawned = x.local(x.invokeSpawn(x.acquire()));

        x.executor.drain();

        assertEquals(ProtosActor.LifecycleState.READY, spawned.lifecycleState());
        ProtosObjectValue behavior = spawned.currentBehavior().orElseThrow();
        assertEquals(
                "failed", ((ProtosStringValue) behavior.readLocalSlot("outcome").orElseThrow()).value());
    }

    @Test
    void aTaskOfTheSameActorAsOnlyProducerMakesNoProgressUntilAnExternalStop() throws Exception {
        Harness x =
                Harness.create(
                        "app",
                        ProtosModuleKey.source("canonical:app"),
                        """
                        ran: 0
                        boot: (acceptor) => {
                            produced: (() => {
                                ran = ran + 1
                                1
                            }).future()
                            produced.value()
                            {}
                        }
                        """);
        ProtosActor spawned = x.local(x.invokeSpawn(x.acquire()));
        x.executor.drain();

        // No implicit reentry: the producer Task stays excluded and nothing busy-loops.
        assertEquals(ProtosActor.LifecycleState.INITIALIZING, spawned.lifecycleState());
        assertEquals(0L, x.sourceSlot(spawned, "ran"));
        assertEquals(0, x.executor.size());
        // The producer is the only Task: the initialization itself owns none.
        assertEquals(1, spawned.executionDomain().liveTaskCount());

        spawned.requestTerminationForRuntime();
        x.executor.drain();
        assertTrue(spawned.currentBehavior().isEmpty());
        assertTrue(spawned.lifecycleState() != ProtosActor.LifecycleState.READY);
    }

    @Test
    void circularImportReturnsThePartialModuleAndResumesWithoutReexecution() throws Exception {
        Harness x =
                Harness.create(
                        "app",
                        ProtosModuleKey.source("canonical:app"),
                        """
                        starts: 0
                        starts = starts + 1
                        published: "early"
                        b: import("b")
                        boot: (acceptor) => {
                            Error.handle(() => { acceptor.accept().value() }, (error) => { null })
                            { sawSelf: b.sawSelf; starts: starts }
                        }
                        """,
                        Map.of(
                                "canonical:b",
                                """
                                a: import("app")
                                sawSelf: a.published
                                """));
        ProtosActor spawned = x.local(x.invokeSpawn(x.acquire()));
        x.executor.drain();
        assertEquals(ProtosActor.LifecycleState.INITIALIZING, spawned.lifecycleState());

        x.backend.pending.pollFirst().failed();
        x.executor.drain();

        ProtosObjectValue behavior = spawned.currentBehavior().orElseThrow();
        assertEquals(
                "early", ((ProtosStringValue) behavior.readLocalSlot("sawSelf").orElseThrow()).value());
        assertEquals(1L, x.sourceSlot(spawned, "starts"));
        assertEquals(
                com.guillermomolina.protos.runtime.ProtosActorModuleState.InitializationState.READY,
                spawned.moduleState().lookup(x.key()).orElseThrow().state());
    }

    @Test
    void synchronousInitializeSuspendsInOneContextAndResumesOnTheHostThread() throws Exception {
        Harness x = Harness.create("app", ProtosModuleKey.source("canonical:app"), SOURCE_BOOT);
        ProtosTcpAcceptorValue acceptor = x.acquire();
        // The delegated acceptor is Process-bound (D217): the destination must be hosted by the
        // same Process, or argument materialization fails before any guest code runs.
        ProtosActor target =
                x.process()
                        .rootActorForRuntime()
                        .createHostedActorForRuntime(
                                new ProtosObjectValue(ProtosObjectValue.rootObject()).freeze());
        List<?> transferred =
                com.guillermomolina.protos.runtime.ProtosActorValueTransfer.snapshotSpawnArguments(
                        List.of(acceptor), x.activation());
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        Thread host =
                new Thread(
                        () -> {
                            try {
                                result.complete(
                                        new ProtosActorBootstrap(x.moduleRuntime())
                                                .initialize(
                                                        target, x.prelude(), x.key(), "boot",
                                                        transferred));
                            } catch (Throwable failure) {
                                result.completeExceptionally(failure);
                            }
                        },
                        "i158-sync-initialize");
        host.setDaemon(true);
        host.start();

        assertTrue(x.backend.accepted.await(5, TimeUnit.SECONDS));
        assertEquals(ProtosActor.LifecycleState.INITIALIZING, target.lifecycleState());
        assertTrue(!result.isDone());

        x.backend.pending.pollFirst().failed();

        assertTrue(result.get(5, TimeUnit.SECONDS));
        assertEquals(ProtosActor.LifecycleState.READY, target.lifecycleState());
        assertEquals(
                "failed",
                ((ProtosStringValue)
                                target.currentBehavior().orElseThrow().readLocalSlot("outcome").orElseThrow())
                        .value());
    }

    // ---- harness -------------------------------------------------------------------------

    record Harness(
            ProtosPrelude prelude,
            ProtosProcessRuntime process,
            ProtosActivation activation,
            ProtosObjectValue actorObject,
            ManualExecutor executor,
            ProtosTcpListenerValue listener,
            CapturingListenerBackend backend,
            String specifier,
            ProtosModuleKey key,
            ProtosModuleRuntime moduleRuntime) {
        static Harness create(String specifier, ProtosModuleKey key, String source)
                throws Exception {
            return create(specifier, key, source, Map.of());
        }

        static Harness create(
                String specifier, ProtosModuleKey key, String source, Map<String, String> others)
                throws Exception {
            ProtosModuleResolver resolver =
                    new ProtosModuleResolver() {
                        @Override
                        public ProtosModuleKey resolve(
                                String exactSpecifier, Optional<ProtosModuleKey> importingModule) {
                            return exactSpecifier.equals(specifier)
                                    ? key
                                    : ProtosModuleKey.source("canonical:" + exactSpecifier);
                        }

                        @Override
                        public ProtosModuleSource loadSource(ProtosModuleKey requested) {
                            String other = others.get(requested.canonicalId());
                            return ProtosModuleSource.fromCharacters(
                                    requested, other != null ? other : source);
                        }
                    };
            ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE, resolver);
            ManualExecutor executor = new ManualExecutor();
            ProtosObjectValue actorRefPrototype =
                    new ProtosObjectValue(ProtosObjectValue.rootObject());
            ProtosObjectValue actorObject = new ProtosObjectValue(ProtosObjectValue.rootObject());
            ProtosModuleRuntime moduleRuntime = new ProtosModuleRuntime(resolver);
            new ProtosStandardActorProtocol(
                            moduleRuntime,
                            executor,
                            actorRefPrototype,
                            new ProtosObjectValue(ProtosObjectValue.rootObject()))
                    .installActorObject(actorObject);
            ProtosProcessRuntime process = new ProtosProcessRuntime(actorRefPrototype);
            ProtosActor root = process.rootActorForRuntime();
            ProtosActivation activation =
                    prelude.newModuleActivation(
                            root.moduleState(),
                            ProtosModuleKey.source("creator"),
                            prelude.newExecutionContext(),
                            root.executionDomain());
            CapturingListenerBackend backend = new CapturingListenerBackend();
            ProtosTcpListenerValue listener =
                    new ProtosTcpListenerValue(
                            prelude,
                            new Object(),
                            activation,
                            backend,
                            51158,
                            ProtosStandardTcpListenerProtocol::materializeAcceptedConnection);
            return new Harness(
                    prelude, process, activation, actorObject, executor, listener, backend,
                    specifier, key, moduleRuntime);
        }

        ProtosTcpAcceptorValue acquire() {
            return assertInstanceOf(
                    ProtosTcpAcceptorValue.class,
                    ProtosInvocation.invokeMessage(
                            listener, "acceptCapability", List.of(), activation));
        }

        ProtosActorRefValue invokeSpawn(Object... initialization) {
            ArrayList<Object> arguments = new ArrayList<>();
            arguments.add(new ProtosStringValue(specifier));
            arguments.add(new ProtosStringValue("boot"));
            arguments.addAll(List.of(initialization));
            return assertInstanceOf(
                    ProtosActorRefValue.class,
                    ProtosInvocation.invokeMessage(actorObject, "spawn", arguments, activation));
        }

        ProtosActor local(ProtosActorRefValue reference) throws ReflectiveOperationException {
            // The ActorRef-to-incarnation bridge is runtime-package private; tests reach it only.
            java.lang.reflect.Method local =
                    ProtosActorRefValue.class.getDeclaredMethod("localActorForRuntime");
            local.setAccessible(true);
            return (ProtosActor) local.invoke(reference);
        }

        /**
         * Binds a test execution host to this harness's Process. Its Actor scheduler runs on this
         * harness's manual executor, so Actors spawned by guest code through the Core global
         * {@code Actor} are scheduled as deterministically as the harness's own spawns.
         */
        Harness hosted() {
            com.guillermomolina.protos.runtime.ProtosActorScheduler scheduler =
                    new com.guillermomolina.protos.runtime.ProtosActorScheduler(executor, 1);
            process.bindExecutionHostForRuntime(
                    new com.guillermomolina.protos.runtime.process.ProtosProcessExecutionHost() {
                        @Override
                        public <T> T callForRuntime(java.util.function.Supplier<T> action) {
                            return ProtosTestExecutionSupport.callEntered(action);
                        }

                        @Override
                        public Optional<com.guillermomolina.protos.runtime.ProtosActorScheduler>
                                actorSchedulerForRuntime() {
                            return Optional.of(scheduler);
                        }

                        @Override
                        public void processTerminatedForRuntime() {}
                    });
            return this;
        }

        long sourceSlot(ProtosActor actor, String name) {
            ProtosObjectValue context = actor.moduleState().lookup(key).orElseThrow().instance();
            return Long.parseLong(
                    ProtosNumericValueSupport.integerDecimalText(
                            context.readLocalSlot(name).orElseThrow()));
        }
    }

    static final class ManualExecutor implements Executor {
        private final ArrayDeque<Runnable> queued = new ArrayDeque<>();

        @Override
        public synchronized void execute(Runnable command) {
            queued.addLast(command);
        }

        synchronized int size() {
            return queued.size();
        }

        void drain() {
            while (true) {
                Runnable command;
                synchronized (this) {
                    command = queued.pollFirst();
                }
                if (command == null) return;
                ProtosTestExecutionSupport.runEntered(command);
            }
        }

        /** Runs exactly one queued scheduler command; false when none is queued. */
        boolean runOne() {
            Runnable command;
            synchronized (this) {
                command = queued.pollFirst();
            }
            if (command == null) return false;
            ProtosTestExecutionSupport.runEntered(command);
            return true;
        }

        /** Drains the queued commands without entering any Context (a HOSTED placement). */
        void drainUnentered() {
            while (true) {
                Runnable command;
                synchronized (this) {
                    command = queued.pollFirst();
                }
                if (command == null) return;
                command.run();
            }
        }

        /** Drains the queued commands entered in {@code context} instead of the shared one. */
        void drainIn(ProtosPolyglotExecutionContext context) {
            while (true) {
                Runnable command;
                synchronized (this) {
                    command = queued.pollFirst();
                }
                if (command == null) return;
                context.callEntered(
                        () -> {
                            command.run();
                            return null;
                        });
            }
        }
    }

    /** Retains every accept completion so the test alone decides when the Future terminalizes. */
    static final class CapturingListenerBackend implements ProtosTcpListenerFlow.Backend {
        final ConcurrentLinkedDeque<ProtosTcpListenerFlow.AcceptCompletion> pending =
                new ConcurrentLinkedDeque<>();
        final CountDownLatch accepted = new CountDownLatch(1);
        volatile boolean failImmediately;

        @Override
        public void close(ProtosTcpListenerFlow.CloseCompletion completion) {}

        @Override
        public ProtosTcpListenerFlow.Cancellation accept(
                ProtosTcpListenerFlow.AcceptCompletion completion) {
            if (failImmediately) {
                completion.failed();
                return () -> {};
            }
            pending.addLast(completion);
            accepted.countDown();
            return () -> {};
        }
    }

    @Test
    void aSuspendedActorDoesNotBlockAnIndependentActor() throws Exception {
        Harness x = Harness.create("app", ProtosModuleKey.source("canonical:app"), SOURCE_BOOT);
        x.backend.failImmediately = false;
        ProtosActor suspended = x.local(x.invokeSpawn(x.acquire()));
        x.executor.drain();
        assertEquals(ProtosActor.LifecycleState.INITIALIZING, suspended.lifecycleState());

        // The second Actor's accept fails immediately, so it never suspends.
        x.backend.failImmediately = true;
        ProtosActor independent = x.local(x.invokeSpawn(x.acquire()));
        x.executor.drain();

        assertEquals(ProtosActor.LifecycleState.READY, independent.lifecycleState());
        assertEquals(ProtosActor.LifecycleState.INITIALIZING, suspended.lifecycleState());

        x.backend.pending.pollFirst().failed();
        x.executor.drain();
        assertEquals(ProtosActor.LifecycleState.READY, suspended.lifecycleState());
    }

    @Test
    void aTargetedCompletionWithAReadyTaskWaiterNeverHandsOffDuringBootstrap() throws Exception {
        Harness x =
                Harness.create(
                        "app",
                        ProtosModuleKey.source("canonical:app"),
                        """
                        waiterRan: 0
                        boot: (acceptor) => {
                            accepted: acceptor.accept()
                            (() => {
                                Error.handle(() => { accepted.value() }, (error) => { null })
                                waiterRan = waiterRan + 1
                            }).future()
                            Error.handle(() => { accepted.value() }, (error) => { null })
                            { ran: waiterRan }
                        }
                        """);
        ProtosActor spawned = x.local(x.invokeSpawn(x.acquire()));
        x.executor.drain();
        assertEquals(ProtosActor.LifecycleState.INITIALIZING, spawned.lifecycleState());

        // The completion has two waiters: the excluded Task (a handoff candidate) and the
        // bootstrap observer. Only the bootstrap may proceed before READY.
        x.backend.pending.pollFirst().failed();
        x.executor.drain();

        ProtosObjectValue behavior = spawned.currentBehavior().orElseThrow();
        assertEquals(
                "0",
                ProtosNumericValueSupport.integerDecimalText(behavior.readLocalSlot("ran").orElseThrow()),
                "the Task waiter did not run before READY");
        assertEquals(1L, x.sourceSlot(spawned, "waiterRan"), "it ran after READY");
    }

    // ---- interleavings of registration, retention, resumption and termination -------------

    /** Two sequential awaits; {@code after} counts the guest steps that ran past each await. */
    private static final String TWO_STEP_BOOT =
            """
            after: 0
            boot: (acceptor) => {
                Error.handle(() => { acceptor.accept().value() }, (error) => { null })
                after = after + 1
                Error.handle(() => { acceptor.accept().value() }, (error) => { null })
                after = after + 10
                {}
            }
            """;

    @AfterEach
    void removeProbe() {
        ProtosActorBootstrapSuspension.probeForTesting = null;
        ProtosHostEntrySuspension.suspendedForTesting = null;
        ProtosActorBootstrap.ownedContextClosedForTesting = null;
    }

    @Test
    void twoObserversOfOnePendingFutureAreEachNotifiedExactlyOnce() throws Exception {
        Harness x = Harness.create("app", ProtosModuleKey.source("canonical:app"), SOURCE_BOOT);
        ProtosActor spawned = x.local(x.invokeSpawn(x.acquire()));
        x.executor.drain();
        ProtosFutureValue awaited = ProtosActorBootstrapSuspension.awaitedFutureForTesting(spawned);
        assertNotNull(awaited);
        assertEquals(ProtosFutureValue.State.PENDING, awaited.state());

        AtomicInteger hostNotified = new AtomicInteger();
        awaited.observe(
                future -> {
                    assertSame(awaited, future);
                    hostNotified.incrementAndGet();
                });
        assertEquals(2, awaited.observerCountForTesting(), "bootstrap and host observers");

        x.backend.pending.pollFirst().failed();
        x.executor.drain();

        assertEquals(1, hostNotified.get());
        assertEquals(ProtosActor.LifecycleState.READY, spawned.lifecycleState());
        assertEquals(
                "failed",
                ((ProtosStringValue)
                                spawned.currentBehavior().orElseThrow().readLocalSlot("outcome").orElseThrow())
                        .value());
        // The bootstrap observer resumed once: the pre-await effect ran once, the Task after READY.
        assertEquals(101L, x.sourceSlot(spawned, "effects"));
    }

    @Test
    void aFutureTerminalAfterRegistrationButBeforeRetentionResumesOnce() throws Exception {
        Harness x = Harness.create("app", ProtosModuleKey.source("canonical:app"), SOURCE_BOOT);
        ProtosActor spawned = x.local(x.invokeSpawn(x.acquire()));
        AtomicInteger retentions = new AtomicInteger();
        AtomicReference<ProtosFutureValue.State> stateAtRetention = new AtomicReference<>();
        ProtosActorBootstrapSuspension.probeForTesting =
                (point, future) -> {
                    if (point != ProtosActorBootstrapSuspension.ProbePoint.BEFORE_RETENTION
                            || future.domain() != spawned.executionDomain()
                            || retentions.getAndIncrement() != 0) {
                        return;
                    }
                    // The segment owner admits the completion after registration, before custody
                    // retains the continuation: the registered observer must not lose it.
                    x.backend.pending.pollFirst().failed();
                    assertTrue(
                            spawned.executionDomain()
                                    .dispatchTargetedRuntimeCompletionWithoutHandoffForRuntime());
                    stateAtRetention.set(future.state());
                };

        x.executor.drain();

        assertEquals(1, retentions.get());
        assertEquals(ProtosFutureValue.State.FAILED, stateAtRetention.get());
        assertEquals(ProtosActor.LifecycleState.READY, spawned.lifecycleState());
        assertEquals(
                "failed",
                ((ProtosStringValue)
                                spawned.currentBehavior().orElseThrow().readLocalSlot("outcome").orElseThrow())
                        .value());
        assertEquals(101L, x.sourceSlot(spawned, "effects"));
    }

    @Test
    void registrationRacingAHostCompletionLosesNoWakeup() throws Exception {
        Harness x = Harness.create("app", ProtosModuleKey.source("canonical:app"), SOURCE_BOOT);
        ProtosActor spawned = x.local(x.invokeSpawn(x.acquire()));
        CyclicBarrier registration = new CyclicBarrier(2);
        AtomicReference<Throwable> hostFailure = new AtomicReference<>();
        Thread host =
                new Thread(
                        () -> {
                            try {
                                registration.await(5, TimeUnit.SECONDS);
                                // Published from a host thread while the owner registers.
                                x.backend.pending.pollFirst().failed();
                            } catch (Throwable failure) {
                                hostFailure.set(failure);
                            }
                        },
                        "i158-racing-completion");
        host.setDaemon(true);
        host.start();
        ProtosActorBootstrapSuspension.probeForTesting =
                (point, future) -> {
                    if (point == ProtosActorBootstrapSuspension.ProbePoint.BEFORE_REGISTRATION) {
                        try {
                            registration.await(5, TimeUnit.SECONDS);
                        } catch (Exception broken) {
                            throw new IllegalStateException(broken);
                        }
                    }
                };

        x.executor.drain();
        host.join(TimeUnit.SECONDS.toMillis(5));
        assertFalse(host.isAlive());
        assertEquals(null, hostFailure.get());
        // Whichever order won, the published completion is admitted and resumes exactly once.
        x.executor.drain();

        assertEquals(ProtosActor.LifecycleState.READY, spawned.lifecycleState());
        assertEquals(101L, x.sourceSlot(spawned, "effects"));
    }

    @Test
    void aPreparedResumptionLosesToTerminationWithoutGuestCode() throws Exception {
        Harness x = Harness.create("app", ProtosModuleKey.source("canonical:app"), TWO_STEP_BOOT);
        ProtosActor spawned = x.local(x.invokeSpawn(x.acquire()));
        x.executor.drain();
        ProtosObjectValue context = moduleContext(x, spawned);

        // The awaited Future becomes terminal under the owner; the resumption is now prepared.
        x.backend.pending.pollFirst().failed();
        assertTrue(
                ProtosTestExecutionSupport.callEntered(
                        () ->
                                spawned.executionDomain()
                                        .dispatchTargetedRuntimeCompletionWithoutHandoffForRuntime()));
        ProtosActorBootstrapSuspension custody = custody(spawned);
        assertTrue(custody.resumeReady());

        spawned.requestTerminationForRuntime();
        assertFalse(custody.resumeReady(), "termination invalidates the prepared resumption");
        x.executor.drain();

        assertEquals(ProtosActor.LifecycleState.TERMINATED, spawned.lifecycleState());
        assertEquals(0L, slot(context, "after"), "no guest code ran after the await");
        assertTrue(spawned.currentBehavior().isEmpty());
        assertTrue(spawned.bootstrapCustodyForRuntime().isEmpty());
        assertEquals(0, spawned.executionDomain().activeBootstrapSegmentCountForRuntime());
    }

    @Test
    void terminationRacingTheDeferredCleanupAbandonsOnce() throws Exception {
        Harness x = Harness.create("app", ProtosModuleKey.source("canonical:app"), TWO_STEP_BOOT);
        ProtosActor spawned = x.local(x.invokeSpawn(x.acquire()));
        x.executor.drain();
        ProtosObjectValue context = moduleContext(x, spawned);
        ProtosActorBootstrapSuspension custody = custody(spawned);

        spawned.requestTerminationForRuntime();
        assertTrue(custody.cleanupPending());
        CyclicBarrier start = new CyclicBarrier(2);
        AtomicReference<Throwable> racerFailure = new AtomicReference<>();
        Thread racer =
                new Thread(
                        () -> {
                            try {
                                start.await(5, TimeUnit.SECONDS);
                                // A second termination request and an explicit cleanup attempt
                                // race the owner's deferred cleanup.
                                spawned.requestTerminationForRuntime();
                                custody.terminationBegun();
                            } catch (Throwable failure) {
                                racerFailure.set(failure);
                            }
                        },
                        "i158-cleanup-racer");
        racer.setDaemon(true);
        racer.start();
        start.await(5, TimeUnit.SECONDS);
        x.executor.drain();
        racer.join(TimeUnit.SECONDS.toMillis(5));
        assertFalse(racer.isAlive());
        x.executor.drain();

        // A double abandon or a cleanup accounting underflow would have thrown.
        assertEquals(null, racerFailure.get());
        assertFalse(custody.cleanupPending());
        assertEquals(ProtosActor.LifecycleState.TERMINATED, spawned.lifecycleState());
        assertEquals(0L, slot(context, "after"));
        assertEquals(0, spawned.executionDomain().activeBootstrapSegmentCountForRuntime());
    }

    @Test
    void terminationDuringTheFirstSegmentWaitsForThatSegment() throws Exception {
        Harness x = Harness.create("app", ProtosModuleKey.source("canonical:app"), TWO_STEP_BOOT);
        ProtosActor spawned = x.local(x.invokeSpawn(x.acquire()));
        AtomicReference<ProtosActor.LifecycleState> stateInSegment = new AtomicReference<>();
        AtomicInteger segmentsInSegment = new AtomicInteger(-1);
        AtomicInteger probes = new AtomicInteger();
        ProtosActorBootstrapSuspension.probeForTesting =
                (point, future) -> {
                    if (point != ProtosActorBootstrapSuspension.ProbePoint.BEFORE_REGISTRATION
                            || probes.getAndIncrement() != 0) {
                        return;
                    }
                    // The first segment is executing and has produced no continuation yet.
                    terminateOnAnotherThread(spawned);
                    stateInSegment.set(spawned.lifecycleState());
                    segmentsInSegment.set(
                            spawned.executionDomain().activeBootstrapSegmentCountForRuntime());
                };

        x.executor.drain();

        assertEquals(ProtosActor.LifecycleState.TERMINATING, stateInSegment.get());
        assertEquals(1, segmentsInSegment.get(), "the executing segment holds its reservation");
        assertEquals(ProtosActor.LifecycleState.TERMINATED, spawned.lifecycleState());
        assertTrue(spawned.currentBehavior().isEmpty(), "READY is never published");
        assertTrue(spawned.bootstrapCustodyForRuntime().isEmpty(), "no continuation is retained");
        assertEquals(0, spawned.executionDomain().activeBootstrapSegmentCountForRuntime());
    }

    @Test
    void terminationDuringAResumedSegmentKeepsCustodyUntilItExits() throws Exception {
        Harness x = Harness.create("app", ProtosModuleKey.source("canonical:app"), TWO_STEP_BOOT);
        ProtosActor spawned = x.local(x.invokeSpawn(x.acquire()));
        x.executor.drain();
        ProtosObjectValue context = moduleContext(x, spawned);
        AtomicReference<ProtosActor.LifecycleState> stateInSegment = new AtomicReference<>();
        AtomicInteger segmentsInSegment = new AtomicInteger(-1);
        AtomicReference<Boolean> custodyInSegment = new AtomicReference<>();
        ProtosActorBootstrapSuspension.probeForTesting =
                (point, future) -> {
                    if (point != ProtosActorBootstrapSuspension.ProbePoint.BEFORE_REGISTRATION
                            || stateInSegment.get() != null) {
                        return;
                    }
                    // The resumed segment runs; its continuation was taken by takeForResume().
                    terminateOnAnotherThread(spawned);
                    stateInSegment.set(spawned.lifecycleState());
                    segmentsInSegment.set(
                            spawned.executionDomain().activeBootstrapSegmentCountForRuntime());
                    custodyInSegment.set(spawned.bootstrapCustodyForRuntime().isPresent());
                };

        x.backend.pending.pollFirst().failed();
        x.executor.drain();

        assertEquals(ProtosActor.LifecycleState.TERMINATING, stateInSegment.get());
        assertEquals(1, segmentsInSegment.get());
        assertEquals(Boolean.TRUE, custodyInSegment.get(), "custody outlives the invalidation");
        // The resumed segment is not interrupted: it ran once from its suspension point to its
        // exit (1 if it suspended again, 11 if termination already cancelled the second accept).
        long after = slot(context, "after");
        assertTrue(after == 1L || after == 11L, "after = " + after);
        assertEquals(ProtosActor.LifecycleState.TERMINATED, spawned.lifecycleState());
        assertTrue(spawned.currentBehavior().isEmpty());
        assertTrue(spawned.bootstrapCustodyForRuntime().isEmpty());
        assertEquals(0, spawned.executionDomain().activeBootstrapSegmentCountForRuntime());
    }

    @Test
    void aResumptionUnderAnotherContextIsRejectedWithoutGuestCode() throws Exception {
        Harness x = Harness.create("app", ProtosModuleKey.source("canonical:app"), TWO_STEP_BOOT);
        ProtosActor spawned = x.local(x.invokeSpawn(x.acquire()));
        x.executor.drain();
        ProtosObjectValue context = moduleContext(x, spawned);

        x.backend.pending.pollFirst().failed();
        ProtosPolyglotExecutionContext other =
                ProtosPolyglotExecutionContext.open(
                        java.io.InputStream.nullInputStream(),
                        java.io.OutputStream.nullOutputStream(),
                        java.io.OutputStream.nullOutputStream());
        try {
            // A valid Context, but not the one that materialized the continuation.
            x.executor.drainIn(other);
        } finally {
            other.close();
        }
        x.executor.drain();

        assertEquals(0L, slot(context, "after"), "no guest code ran in the wrong Context");
        assertTrue(spawned.currentBehavior().isEmpty());
        assertEquals(ProtosActor.LifecycleState.TERMINATED, spawned.lifecycleState());
        assertTrue(spawned.bootstrapCustodyForRuntime().isEmpty());
        assertEquals(0, spawned.executionDomain().activeBootstrapSegmentCountForRuntime());
    }

    @Test
    void processTerminationWithAPendingContinuationNeitherReentersNorPublishesReady()
            throws Exception {
        Harness x = Harness.create("app", ProtosModuleKey.source("canonical:app"), TWO_STEP_BOOT);
        ProtosActor spawned = x.local(x.invokeSpawn(x.acquire()));
        x.executor.drain();
        ProtosObjectValue context = moduleContext(x, spawned);
        ProtosActorBootstrapSuspension custody = custody(spawned);

        x.process().requestTerminationForRuntime();
        x.executor.drain();

        assertFalse(custody.retainsContinuationForRuntime(), "no owner is leaked");
        assertEquals(0L, slot(context, "after"));
        assertTrue(spawned.currentBehavior().isEmpty());
        assertEquals(ProtosActor.LifecycleState.TERMINATED, spawned.lifecycleState());
        assertEquals(0, spawned.executionDomain().activeBootstrapSegmentCountForRuntime());
    }

    @Test
    void noGuestCodeRunsAfterTerminated() throws Exception {
        Harness x = Harness.create("app", ProtosModuleKey.source("canonical:app"), SOURCE_BOOT);
        ProtosActorRefValue reference = x.invokeSpawn(x.acquire());
        ProtosActor spawned = x.local(reference);
        x.executor.drain();
        ProtosObjectValue context = moduleContext(x, spawned);
        assertEquals(1L, slot(context, "effects"));

        spawned.requestTerminationForRuntime();
        x.executor.drain();
        assertEquals(ProtosActor.LifecycleState.TERMINATED, spawned.lifecycleState());

        try {
            x.backend.pending.pollFirst().failed();
        } catch (IllegalStateException refused) {
            // A terminated domain may refuse the late completion outright.
        }
        try {
            ProtosInvocation.invokeMessage(
                    reference, "send", List.of(new ProtosStringValue("bump")), x.activation());
        } catch (RuntimeException refused) {
            // Delivery to a terminated Actor may be refused; it never runs its behavior.
        }
        x.executor.drain();

        // Neither the excluded Task (+100), the message (+1000) nor the continuation ran.
        assertEquals(1L, slot(context, "effects"));
        assertTrue(spawned.currentBehavior().isEmpty());
        assertEquals(ProtosActor.LifecycleState.TERMINATED, spawned.lifecycleState());
    }

    private static ProtosObjectValue moduleContext(Harness x, ProtosActor actor) {
        return actor.moduleState().lookup(x.key()).orElseThrow().instance();
    }

    private static long slot(ProtosObjectValue context, String name) {
        return Long.parseLong(
                ProtosNumericValueSupport.integerDecimalText(
                        context.readLocalSlot(name).orElseThrow()));
    }

    private static ProtosActorBootstrapSuspension custody(ProtosActor actor) {
        return (ProtosActorBootstrapSuspension) actor.bootstrapCustodyForRuntime().orElseThrow();
    }

    /** Begins termination from a host thread and waits for that call to return. */
    private static void terminateOnAnotherThread(ProtosActor actor) {
        Thread terminator =
                new Thread(actor::requestTerminationForRuntime, "i158-terminator");
        terminator.setDaemon(true);
        terminator.start();
        try {
            terminator.join(TimeUnit.SECONDS.toMillis(5));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
        if (terminator.isAlive()) {
            throw new IllegalStateException("termination did not return while a segment ran");
        }
    }

    // ---- HOST-FUT-1 regression: direct-entry RootActor, independent of I158 ----------------

    /**
     * A real Polyglot host entry into a direct-entry RootActor suspends in {@code Future.value()}
     * under HOST-FUT-1, not under the I158 initialization custody. The pending Future is resolved
     * from an independent source (the test thread); a later wait is ended by Process termination.
     */
    @Test
    void hostFut1DirectEntryKeepsItsRulesAndNeverUsesInitializationCustody() throws Exception {
        try (org.graalvm.polyglot.Context context =
                org.graalvm.polyglot.Context.newBuilder(ProtosLanguage.ID)
                        .option("protos.CoreRoot", CORE.toAbsolutePath().toString())
                        .build()) {
            context.eval(
                    ProtosLanguage.ID,
                    """
                    sample: (() => 1).future()
                    sample.value()
                    gate: 0
                    before: 0
                    after: 0
                    run: () => {
                        before = before + 1
                        v: gate.value()
                        after = after + 1
                        v + 1
                    }
                    0
                    """);
            ProtosEmbeddedProcess process;
            context.enter();
            try {
                process = ProtosLanguageContext.current().embeddedProcessOrNull();
            } finally {
                context.leave();
            }
            ProtosObjectValue module = process.selectedModuleContext().orElseThrow();
            ProtosFutureValue sample = (ProtosFutureValue) module.readLocalSlot("sample").orElseThrow();
            ProtosFutureValue gate =
                    new ProtosFutureValue((ProtosObjectValue) sample.parent().orElseThrow(), sample.domain());
            module.assignLocalSlot("gate", gate);
            ProtosActor root = process.processForTesting().rootActorForRuntime();
            org.graalvm.polyglot.Value run = context.getBindings(ProtosLanguage.ID).getMember("run");

            CountDownLatch firstSuspended = suspensionSignal(gate);
            HostEntry first = new HostEntry(run);
            first.awaitSuspended(firstSuspended);
            assertEquals(1L, slot(module, "before"));
            // HOST-FUT-1 owns this wait: the I158 custody and bootstrap extent take no part.
            assertTrue(root.bootstrapCustodyForRuntime().isEmpty());
            assertEquals(null, ProtosActorBootstrapSuspension.awaitedFutureForTesting(root));
            assertEquals(1, gate.observerCountForTesting());
            assertEquals(0L, slot(module, "after"));

            assertTrue(gate.resolve(new com.guillermomolina.protos.runtime.ProtosIntegerValue(41), null));
            assertEquals(42L, first.await(), "synchronous from the host's point of view");
            assertEquals(1L, slot(module, "before"), "no effect before the await is repeated");
            assertEquals(1L, slot(module, "after"));
            assertEquals(
                    com.guillermomolina.protos.runtime.ProtosActorExecutionDomain.HostEntryExtent.NONE,
                    root.executionDomain().hostEntryExtentForRuntime());

            // A second wait is ended by Process termination without resuming guest code.
            ProtosFutureValue second =
                    new ProtosFutureValue((ProtosObjectValue) sample.parent().orElseThrow(), sample.domain());
            module.assignLocalSlot("gate", second);
            CountDownLatch secondSuspended = suspensionSignal(second);
            HostEntry waiting = new HostEntry(run);
            waiting.awaitSuspended(secondSuspended);
            assertEquals(2L, slot(module, "before"));
            process.processForTesting().requestTerminationForRuntime();
            assertInstanceOf(org.graalvm.polyglot.PolyglotException.class, waiting.await());
            assertEquals(0, second.observerCountForTesting());
            assertEquals(1L, slot(module, "after"), "the abandoned wait never resumed");
        }
    }

    /** Signals once a HOST-FUT-1 entry suspended on {@code awaited}; replaces any earlier signal. */
    private static CountDownLatch suspensionSignal(ProtosFutureValue awaited) {
        CountDownLatch suspended = new CountDownLatch(1);
        ProtosHostEntrySuspension.suspendedForTesting =
                future -> {
                    if (future == awaited) {
                        suspended.countDown();
                    }
                };
        return suspended;
    }

    /** One host entry on its own thread; the test thread observes and terminalizes. */
    private static final class HostEntry {
        private final CompletableFuture<Object> outcome = new CompletableFuture<>();
        private final Thread thread;

        HostEntry(org.graalvm.polyglot.Value executable) {
            thread =
                    new Thread(
                            () -> {
                                try {
                                    org.graalvm.polyglot.Value result = executable.execute();
                                    outcome.complete(result.fitsInLong() ? (Object) result.asLong() : result);
                                } catch (Throwable failure) {
                                    outcome.complete(failure);
                                }
                            },
                            "i158-host-fut-1-entry");
            thread.setDaemon(true);
            thread.start();
        }

        /** Bounded wait for the driver's signal that this entry holds its suspended continuation. */
        void awaitSuspended(CountDownLatch suspended) throws InterruptedException {
            assertTrue(suspended.await(5, TimeUnit.SECONDS), "the host entry never suspended");
            assertFalse(outcome.isDone(), "a suspended entry has not returned to the host");
        }

        Object await() throws Exception {
            try {
                return outcome.get(5, TimeUnit.SECONDS);
            } finally {
                thread.join(TimeUnit.SECONDS.toMillis(5));
            }
        }
    }

    // ---- real bootstrap cleanup under competing terminations ------------------------------

    /**
     * The synchronous engine with its own Context (OWNED placement) holds a real suspended C-prime
     * continuation. Actor termination, Process termination and a late terminalization of the
     * awaited Future then race; the Context close happens on the initializing thread when the
     * initialization ends.
     */
    @Test
    void competingTerminationsReleaseTheRealSuspendedBootstrapExactlyOnce() throws Exception {
        Harness x = Harness.create("app", ProtosModuleKey.source("canonical:app"), TWO_STEP_BOOT);
        ProtosActor target =
                x.process()
                        .rootActorForRuntime()
                        .createHostedActorForRuntime(
                                new ProtosObjectValue(ProtosObjectValue.rootObject()).freeze());
        List<?> transferred =
                com.guillermomolina.protos.runtime.ProtosActorValueTransfer.snapshotSpawnArguments(
                        List.of(x.acquire()), x.activation());
        AtomicInteger contextCloses = new AtomicInteger();
        ProtosActorBootstrap.ownedContextClosedForTesting =
                actor -> {
                    if (actor == target) {
                        contextCloses.incrementAndGet();
                    }
                };
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        Thread initializer =
                new Thread(
                        () -> {
                            try {
                                result.complete(
                                        new ProtosActorBootstrap(x.moduleRuntime())
                                                .initialize(target, x.prelude(), x.key(), "boot", transferred));
                            } catch (Throwable failure) {
                                result.completeExceptionally(failure);
                            }
                        },
                        "i158-owned-initializer");
        initializer.setDaemon(true);
        try {
            initializer.start();
            ProtosFutureValue awaited =
                    ProtosActorBootstrapSuspension.awaitSuspendedFutureForTesting(target, null, 5000);
            assertNotNull(awaited, "the real initialization suspended");
            ProtosActorBootstrapSuspension custody = custody(target);
            ProtosObjectValue context = moduleContext(x, target);
            assertEquals(1, awaited.observerCountForTesting());
            assertEquals(0, contextCloses.get(), "the Context stays open while suspended");

            // Actor and Process termination race each other; neither runs guest code.
            CyclicBarrier start = new CyclicBarrier(3);
            List<Throwable> failures = java.util.Collections.synchronizedList(new ArrayList<>());
            List<Thread> racers = new ArrayList<>();
            racers.add(racer(start, failures, target::requestTerminationForRuntime, "actor-stop"));
            racers.add(racer(start, failures, () -> x.process().requestTerminationForRuntime(), "process-stop"));
            start.await(5, TimeUnit.SECONDS);
            for (Thread racer : racers) {
                racer.join(TimeUnit.SECONDS.toMillis(5));
                assertFalse(racer.isAlive());
            }
            assertTrue(failures.isEmpty(), () -> "racer failed: " + failures);
            // The awaited Future terminalizes only after the invalidation (late completion).
            try {
                x.backend.pending.pollFirst().failed();
            } catch (IllegalStateException refused) {
                // A terminating owner may refuse the late completion outright.
            }

            // Invalidation happened first: no resumption can be admitted any more.
            assertFalse(custody.resumeReady());
            assertEquals(Boolean.FALSE, result.get(5, TimeUnit.SECONDS), "the real disposition");
            initializer.join(TimeUnit.SECONDS.toMillis(5));

            assertFalse(custody.retainsContinuationForRuntime(), "transports abandoned");
            assertFalse(custody.cleanupPending(), "no pending bootstrap cleanup");
            assertEquals(0, awaited.observerCountForTesting(), "observer released");
            assertEquals(0, target.executionDomain().activeBootstrapSegmentCountForRuntime());
            assertTrue(target.bootstrapCustodyForRuntime().isEmpty());
            assertEquals(ProtosActor.LifecycleState.TERMINATED, target.lifecycleState());
            assertTrue(target.currentBehavior().isEmpty(), "no late READY");
            assertEquals(0L, slot(context, "after"), "no guest code ran after termination");
            assertEquals(1, contextCloses.get(), "the owned Context closed exactly once");
        } finally {
            ProtosActorBootstrap.ownedContextClosedForTesting = null;
        }
    }

    private static Thread racer(
            CyclicBarrier start, List<Throwable> failures, Runnable action, String name) {
        Thread thread =
                new Thread(
                        () -> {
                            try {
                                start.await(5, TimeUnit.SECONDS);
                                action.run();
                            } catch (Throwable failure) {
                                failures.add(failure);
                            }
                        },
                        "i158-" + name);
        thread.setDaemon(true);
        thread.start();
        return thread;
    }
}
