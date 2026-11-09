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

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosActor;
import com.guillermomolina.protos.runtime.ProtosComplexNumericSupport;
import com.guillermomolina.protos.runtime.ProtosFutureValue;
import com.guillermomolina.protos.runtime.ProtosNumericValueSupport;
import com.guillermomolina.protos.runtime.modules.ProtosModuleKey;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.guillermomolina.protos.runtime.ProtosActorRefValue;
import com.guillermomolina.protos.runtime.ProtosProcessRuntime;
import com.guillermomolina.protos.runtime.ProtosStringValue;
import com.guillermomolina.protos.runtime.io.ProtosTcpAcceptorValue;
import com.guillermomolina.protos.runtime.io.ProtosTcpListenerFlow;
import com.guillermomolina.protos.runtime.io.ProtosTcpListenerValue;
import java.util.ArrayDeque;
import java.util.concurrent.Executor;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class ProtosActorBootstrapTest {
    private static final Path CORE = Path.of("protos", "lib", "core");

    @Test
    void canonicalBootstrapLoadsActorLocalModuleAndInstallsExactBehavior() throws Exception {
        CanonicalResolver resolver =
                new CanonicalResolver()
                        .module(
                                "app/bootstrap",
                                "bootstrap: (value) => {\n"
                                        + "    { observed: value }\n"
                                        + "}");
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE, resolver);
        ProtosActor actor = new ProtosActor(actorRefPrototype());
        ProtosObjectValue transferred = new ProtosObjectValue(ProtosObjectValue.rootObject());
        ProtosModuleKey key = ProtosModuleKey.source("app/bootstrap");

        boolean becameReady =
                new ProtosActorBootstrap(new ProtosModuleRuntime(resolver))
                        .initialize(actor, prelude, key, "bootstrap", List.of(transferred));

        assertTrue(becameReady);
        assertEquals(ProtosActor.LifecycleState.READY, actor.lifecycleState());
        ProtosObjectValue behavior = actor.currentBehavior().orElseThrow();
        assertSame(transferred, behavior.readLocalSlot("observed").orElseThrow());
        assertEquals(0, resolver.resolveCalls.get(), "canonical bootstrap must not re-resolve spelling");
        assertEquals(1, resolver.loads("app/bootstrap"));
        assertEquals(
                com.guillermomolina.protos.runtime.ProtosActorModuleState.InitializationState.READY,
                actor.moduleState().lookup(key).orElseThrow().state());
    }

    @Test
    void d242ModuleBootstrapSelectsPublicExportAndPreservesPrivateLexicalSlots()
            throws Exception {
        CanonicalResolver resolver =
                new CanonicalResolver()
                        .module(
                                "app/bootstrap",
                                "privateValue: 17\n"
                                        + "this.bootstrap: () => {\n"
                                        + "    moduleReceiver: this\n"
                                        + "    { owner: moduleReceiver; observed: privateValue }\n"
                                        + "}");
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE, resolver);
        ProtosActor actor = new ProtosActor(actorRefPrototype());
        ProtosModuleKey key = ProtosModuleKey.module("app/bootstrap");

        assertTrue(
                new ProtosActorBootstrap(new ProtosModuleRuntime(resolver))
                        .initialize(actor, prelude, key, "bootstrap", List.of()));

        ProtosObjectValue publicModule =
                actor.moduleState().lookup(key).orElseThrow().instance();
        ProtosObjectValue behavior = actor.currentBehavior().orElseThrow();

        assertTrue(publicModule.hasLocalSlot("bootstrap"));
        assertFalse(publicModule.hasLocalSlot("privateValue"));
        assertTrue(behavior.hasLocalSlot("observed"));
        assertSame(
                publicModule,
                behavior.readLocalSlot("owner").orElseThrow());
    }

    @Test
    void d242PrivateModuleBindingCannotBeUsedAsActorBootstrap()
            throws Exception {
        CanonicalResolver resolver =
                new CanonicalResolver()
                        .module(
                                "app/bootstrap",
                                "bootstrap: () => { {} }");
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE, resolver);
        ProtosActor actor = new ProtosActor(actorRefPrototype());
        ProtosModuleKey key = ProtosModuleKey.module("app/bootstrap");

        assertThrows(
                ProtosSignalException.class,
                () ->
                        new ProtosActorBootstrap(new ProtosModuleRuntime(resolver))
                                .initialize(
                                        actor,
                                        prelude,
                                        key,
                                        "bootstrap",
                                        List.of()));

        assertEquals(
                ProtosActor.LifecycleState.TERMINATED,
                actor.lifecycleState());

        ProtosObjectValue publicModule =
                actor.moduleState().lookup(key).orElseThrow().instance();
        assertFalse(publicModule.hasLocalSlot("bootstrap"));
    }

    @Test
    void executionDomainCarriesCurrentActorReferenceWithoutGlobalState() throws Exception {
        CanonicalResolver resolver = new CanonicalResolver();
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE, resolver);
        ProtosActor actor = new ProtosActor(actorRefPrototype());
        ProtosActivation activation =
                prelude.newModuleActivation(
                        actor.moduleState(),
                        null,
                        prelude.newExecutionContext(),
                        actor.executionDomain());

        assertSame(actor.reference(), actor.executionDomain().currentActorReference().orElseThrow());
        assertSame(actor.reference(), activation.executionDomain().currentActorReference().orElseThrow());

        actor.beginTermination();
        actor.markTerminated();
        assertSame(actor.reference(), activation.executionDomain().currentActorReference().orElseThrow());
    }

    @Test
    void sameCanonicalBootstrapModuleIsInstantiatedIndependentlyPerActor() throws Exception {
        CanonicalResolver resolver =
                new CanonicalResolver()
                        .module("app/bootstrap", "bootstrap: () => { { state: {} } }");
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE, resolver);
        ProtosModuleKey key = ProtosModuleKey.source("app/bootstrap");
        ProtosActor first = new ProtosActor(actorRefPrototype());
        ProtosActor second = new ProtosActor(actorRefPrototype());
        ProtosActorBootstrap bootstrap =
                new ProtosActorBootstrap(new ProtosModuleRuntime(resolver));

        assertTrue(bootstrap.initialize(first, prelude, key, "bootstrap", List.of()));
        assertTrue(bootstrap.initialize(second, prelude, key, "bootstrap", List.of()));

        assertNotSame(
                first.moduleState().lookup(key).orElseThrow().instance(),
                second.moduleState().lookup(key).orElseThrow().instance());
        assertNotSame(first.currentBehavior().orElseThrow(), second.currentBehavior().orElseThrow());
        assertEquals(2, resolver.loads("app/bootstrap"));
    }

    @Test
    void inheritedBindingCannotSatisfyBootstrapLocalBindingRequirement() throws Exception {
        CanonicalResolver resolver =
                new CanonicalResolver().module("app/bootstrap", "other: () => { {} }");
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE, resolver);
        ProtosActor actor = new ProtosActor(actorRefPrototype());

        assertThrows(
                ProtosSignalException.class,
                () ->
                        new ProtosActorBootstrap(new ProtosModuleRuntime(resolver))
                                .initialize(
                                        actor,
                                        prelude,
                                        ProtosModuleKey.source("app/bootstrap"),
                                        "call",
                                        List.of()));
        assertEquals(ProtosActor.LifecycleState.TERMINATED, actor.lifecycleState());
        assertTrue(actor.currentBehavior().isEmpty());
    }

    @Test
    void nonInvokableBindingAndNonObjectBehaviorAreInitializationFailures() throws Exception {
        assertBootstrapFailure("bootstrap: 7");
        assertBootstrapFailure("bootstrap: () => { 7 }");
    }

    @Test
    void numericRichValuesCannotBecomeActorBehaviorButOrdinaryDelegatesCan()
            throws Exception {
        // Source-produced Fraction is a sealed ProtosObjectValue, but
        // semantically it is a Number, not an Actor behavior object.
        assertBootstrapFailure("bootstrap: () => { 1 / 2 }");

        CanonicalResolver resolver =
                new CanonicalResolver().module(
                        "app/bootstrap",
                        "bootstrap: (candidate) => { candidate }");

        ProtosPrelude prelude =
                new ProtosCoreBootstrap().bootstrap(CORE, resolver);

        Object complex = ProtosComplexNumericSupport.normalize(
                Long.valueOf(2L),
                Long.valueOf(3L),
                prelude);

        assertTrue(ProtosComplexNumericSupport.isComplex(complex));

        ProtosActorBootstrap bootstrap =
                new ProtosActorBootstrap(new ProtosModuleRuntime(resolver));
        ProtosModuleKey key = ProtosModuleKey.source("app/bootstrap");

        ProtosActor numericActor = new ProtosActor(actorRefPrototype());

        assertThrows(
                ProtosSignalException.class,
                () -> bootstrap.initialize(
                        numericActor, prelude, key, "bootstrap",
                        List.of(complex)));

        assertEquals(
                ProtosActor.LifecycleState.TERMINATED,
                numericActor.lifecycleState());
        assertTrue(numericActor.currentBehavior().isEmpty());

        // Parentability is independent of numeric family membership.
        // This is a genuine ordinary object, even though its parent is
        // a genuine Complex value.
        ProtosObjectValue ordinary = new ProtosObjectValue(complex);
        assertFalse(ProtosNumericValueSupport.isCurrentNumber(ordinary));

        ProtosActor ordinaryActor = new ProtosActor(actorRefPrototype());

        assertTrue(bootstrap.initialize(
                ordinaryActor, prelude, key, "bootstrap",
                List.of(ordinary)));

        assertEquals(
                ProtosActor.LifecycleState.READY,
                ordinaryActor.lifecycleState());
        assertSame(
                ordinary,
                ordinaryActor.currentBehavior().orElseThrow());
    }

    @Test
    void sourceFailureTerminatesSameExistingIncarnationWithoutRetargetingReference() throws Exception {
        CanonicalResolver resolver =
                new CanonicalResolver().module("app/bootstrap", "broken: (");
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE, resolver);
        ProtosActor actor = new ProtosActor(actorRefPrototype());
        Object reference = actor.reference();

        assertThrows(
                ProtosSignalException.class,
                () ->
                        new ProtosActorBootstrap(new ProtosModuleRuntime(resolver))
                                .initialize(
                                        actor,
                                        prelude,
                                        ProtosModuleKey.source("app/bootstrap"),
                                        "bootstrap",
                                        List.of()));

        assertSame(reference, actor.reference());
        assertEquals(ProtosActor.LifecycleState.TERMINATED, actor.lifecycleState());
        assertTrue(actor.currentBehavior().isEmpty());
    }

    @Test
    void behaviorReferenceCannotBeReplacedAfterReady() throws Exception {
        CanonicalResolver resolver =
                new CanonicalResolver().module("app/bootstrap", "bootstrap: () => { {} }");
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE, resolver);
        ProtosActor actor = new ProtosActor(actorRefPrototype());
        ProtosActorBootstrap bootstrap =
                new ProtosActorBootstrap(new ProtosModuleRuntime(resolver));
        ProtosModuleKey key = ProtosModuleKey.source("app/bootstrap");

        assertTrue(bootstrap.initialize(actor, prelude, key, "bootstrap", List.of()));
        ProtosObjectValue behavior = actor.currentBehavior().orElseThrow();
        assertFalse(actor.completeInitialization(new ProtosObjectValue(ProtosObjectValue.rootObject())));
        assertSame(behavior, actor.currentBehavior().orElseThrow());
    }

    @Test
    void spawnBootstrapReceivesFreshDestinationLocalNestedTcpAcceptor() throws Exception {
        SpawnHarness x = spawnHarness();
        ProtosTcpAcceptorValue acceptor = x.acquire();
        acceptor.createLocalSlot("label", Long.valueOf(3));
        ProtosObjectValue holder = new ProtosObjectValue(ProtosObjectValue.rootObject());
        holder.createLocalSlot("inner", acceptor);

        ProtosActor spawned = x.spawn(acceptor, acceptor, holder);

        ProtosObjectValue behavior = spawned.currentBehavior().orElseThrow();
        ProtosObjectValue nested = (ProtosObjectValue) behavior.readLocalSlot("nested").orElseThrow();
        ProtosTcpAcceptorValue delivered =
                assertInstanceOf(ProtosTcpAcceptorValue.class, nested.readLocalSlot("inner").orElseThrow());
        assertNotSame(acceptor, delivered);
        assertTrue(delivered.ownedByForRuntime(x.activationOf(spawned)));
        assertFalse(delivered.ownedByForRuntime(x.activation));
        assertSame(x.prelude.tcpAcceptorPrototypeForRuntime(), delivered.parent().orElseThrow());
        assertTrue(delivered.isOpen());
        assertTrue(delivered.localSlotsSnapshot().isEmpty());
        assertEquals(1, acceptor.localSlotsSnapshot().size());
        assertEquals(0, x.backend.accepts.get());
    }

    @Test
    void spawnBootstrapObservesOneCapabilityIdentityAcrossArgumentRoots() throws Exception {
        SpawnHarness x = spawnHarness();
        ProtosTcpAcceptorValue acceptor = x.acquire();
        ProtosObjectValue holder = new ProtosObjectValue(ProtosObjectValue.rootObject());
        holder.createLocalSlot("inner", acceptor);

        ProtosObjectValue behavior = x.spawn(acceptor, acceptor, holder).currentBehavior().orElseThrow();

        Object first = behavior.readLocalSlot("first").orElseThrow();
        assertInstanceOf(ProtosTcpAcceptorValue.class, first);
        assertSame(first, behavior.readLocalSlot("second").orElseThrow());
        assertSame(
                first,
                ((ProtosObjectValue) behavior.readLocalSlot("nested").orElseThrow())
                        .readLocalSlot("inner").orElseThrow());
    }

    @Test
    void spawnWithIndependentNonTransferableArgumentFailsBeforeActorCreation() throws Exception {
        SpawnHarness x = spawnHarness();
        ProtosTcpAcceptorValue acceptor = x.acquire();

        ProtosSignalException failure =
                assertThrows(
                        ProtosSignalException.class,
                        () -> x.invokeSpawn(acceptor, x.listener, acceptor));

        assertSame(
                x.prelude.bindings().readLocalSlot("NonTransferableValue").orElseThrow(),
                failure.error().parent().orElseThrow());
        assertEquals(0, x.executor.size(), "no Actor creation cutover was scheduled");
        assertTrue(acceptor.isOpen());
        assertEquals(0, x.backend.accepts.get());
    }

    @Test
    void spawnedAcceptorHolderIsHostedByTheCreatorsExactProcess() throws Exception {
        SpawnHarness x = spawnHarness();
        ProtosTcpAcceptorValue acceptor = x.acquire();

        ProtosActor spawned = x.spawn(acceptor, acceptor, acceptor);

        assertSame(x.process, spawned.processForRuntime().orElseThrow());
        assertFalse(spawned.isRootActorForRuntime());
        assertEquals(ProtosActor.LifecycleState.READY, spawned.lifecycleState());
    }

    @Test
    void actorRefSendAndRequestStillRejectConcreteTcpAcceptors() throws Exception {
        SpawnHarness x = spawnHarness();
        ProtosTcpAcceptorValue acceptor = x.acquire();
        ProtosObjectValue holder = new ProtosObjectValue(ProtosObjectValue.rootObject());
        holder.createLocalSlot("inner", acceptor);
        ProtosActorRefValue reference = x.spawn(holder, holder, holder).reference();

        for (String selector : List.of("send", "request")) {
            for (Object argument : List.of(acceptor, holder)) {
                ProtosSignalException failure =
                        assertThrows(
                                ProtosSignalException.class,
                                () -> ProtosInvocation.invokeMessage(
                                        reference,
                                        selector,
                                        List.of(new ProtosStringValue("take"), argument),
                                        x.activation));
                assertSame(
                        x.prelude.bindings().readLocalSlot("NonTransferableValue").orElseThrow(),
                        failure.error().parent().orElseThrow());
            }
        }
        assertEquals(0, x.backend.accepts.get());
    }

    private static SpawnHarness spawnHarness() throws Exception {
        ProtosModuleResolver resolver =
                new ProtosModuleResolver() {
                    @Override
                    public ProtosModuleKey resolve(
                            String exactSpecifier, Optional<ProtosModuleKey> importingModule) {
                        return ProtosModuleKey.source("canonical:" + exactSpecifier);
                    }

                    @Override
                    public ProtosModuleSource loadSource(ProtosModuleKey key) {
                        return ProtosModuleSource.fromCharacters(
                                key,
                                // Object-body items are separated by newlines or ';', never ','.
                                "boot: (a, b, c) => {\n"
                                        + "    {\n"
                                        + "        first: a\n"
                                        + "        second: b\n"
                                        + "        nested: c\n"
                                        + "    }\n"
                                        + "}");
                    }
                };
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE, resolver);
        ManualExecutor executor = new ManualExecutor();
        ProtosObjectValue actorRefPrototype = new ProtosObjectValue(ProtosObjectValue.rootObject());
        ProtosObjectValue actorObject = new ProtosObjectValue(ProtosObjectValue.rootObject());
        new ProtosStandardActorProtocol(
                        new ProtosModuleRuntime(resolver),
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
        CountingListenerBackend backend = new CountingListenerBackend();
        ProtosTcpListenerValue listener =
                new ProtosTcpListenerValue(
                        prelude, new Object(), activation, backend, 51000,
                        ProtosStandardTcpListenerProtocol::materializeAcceptedConnection);
        return new SpawnHarness(prelude, process, activation, actorObject, executor, listener, backend);
    }

    private record SpawnHarness(
            ProtosPrelude prelude,
            ProtosProcessRuntime process,
            ProtosActivation activation,
            ProtosObjectValue actorObject,
            ManualExecutor executor,
            ProtosTcpListenerValue listener,
            CountingListenerBackend backend) {
        ProtosTcpAcceptorValue acquire() {
            return assertInstanceOf(
                    ProtosTcpAcceptorValue.class,
                    ProtosInvocation.invokeMessage(listener, "acceptCapability", List.of(), activation));
        }

        Object invokeSpawn(Object... initialization) {
            java.util.ArrayList<Object> arguments = new java.util.ArrayList<>();
            arguments.add(new ProtosStringValue("app"));
            arguments.add(new ProtosStringValue("boot"));
            arguments.addAll(List.of(initialization));
            return ProtosInvocation.invokeMessage(actorObject, "spawn", arguments, activation);
        }

        ProtosActor spawn(Object... initialization) throws ReflectiveOperationException {
            ProtosActorRefValue reference =
                    assertInstanceOf(ProtosActorRefValue.class, invokeSpawn(initialization));
            executor.drain();
            // The ActorRef-to-incarnation bridge is runtime-package private; tests reach it only.
            java.lang.reflect.Method local =
                    ProtosActorRefValue.class.getDeclaredMethod("localActorForRuntime");
            local.setAccessible(true);
            ProtosActor spawned = (ProtosActor) local.invoke(reference);
            assertEquals(ProtosActor.LifecycleState.READY, spawned.lifecycleState());
            return spawned;
        }

        ProtosActivation activationOf(ProtosActor actor) {
            return prelude.newModuleActivation(
                    actor.moduleState(), null, prelude.newExecutionContext(), actor.executionDomain());
        }
    }

    private static final class ManualExecutor implements Executor {
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
    }

    private static final class CountingListenerBackend implements ProtosTcpListenerFlow.Backend {
        final AtomicInteger accepts = new AtomicInteger();

        @Override
        public void close(ProtosTcpListenerFlow.CloseCompletion completion) {}

        @Override
        public ProtosTcpListenerFlow.Cancellation accept(ProtosTcpListenerFlow.AcceptCompletion completion) {
            accepts.incrementAndGet();
            return () -> {};
        }
    }

    private static void assertBootstrapFailure(String source) throws Exception {
        CanonicalResolver resolver = new CanonicalResolver().module("app/bootstrap", source);
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE, resolver);
        ProtosActor actor = new ProtosActor(actorRefPrototype());

        assertThrows(
                ProtosSignalException.class,
                () ->
                        new ProtosActorBootstrap(new ProtosModuleRuntime(resolver))
                                .initialize(
                                        actor,
                                        prelude,
                                        ProtosModuleKey.source("app/bootstrap"),
                                        "bootstrap",
                                        List.of()));
        assertEquals(ProtosActor.LifecycleState.TERMINATED, actor.lifecycleState());
        assertTrue(actor.currentBehavior().isEmpty());
    }

    private static ProtosObjectValue actorRefPrototype() {
        return new ProtosObjectValue(ProtosObjectValue.rootObject()).freeze();
    }

    private static final class CanonicalResolver implements ProtosModuleResolver {
        private final Map<String, String> sources = new HashMap<>();
        private final Map<String, Integer> loads = new HashMap<>();
        private final AtomicInteger resolveCalls = new AtomicInteger();

        CanonicalResolver module(String key, String source) {
            sources.put(key, source);
            return this;
        }

        int loads(String key) {
            return loads.getOrDefault(key, 0);
        }

        @Override
        public ProtosModuleKey resolve(
                String exactSpecifier, Optional<ProtosModuleKey> importingModule) throws Exception {
            resolveCalls.incrementAndGet();
            throw new AssertionError("I011-3 bootstrap must consume an already-canonical ModuleKey");
        }

        @Override
        public ProtosModuleSource loadSource(ProtosModuleKey key) throws Exception {
            loads.merge(key.canonicalId(), 1, Integer::sum);
            String source = sources.get(key.canonicalId());
            if (source == null) {
                throw new java.io.IOException("module not found: " + key.canonicalId());
            }
            return ProtosModuleSource.fromCharacters(key, source);
        }
    }

    // ---- I158 / D251-A1: the shared suspendible bootstrap engine --------------------------
    // The awaited Future is a transferred TcpAcceptor accept() whose completion the test holds
    // (ProtosI158ActorBootstrapSuspensionTest.Harness); carriers run only when drained.

    private static final String I158_ARGUMENTS =
            """
            boot: (acceptor, holder) => {
                before: holder
                Error.handle(() => { acceptor.accept().value() }, (error) => { null })
                { before: before; after: holder }
            }
            """;

    @Test
    void i158SpawnReturnsTheRefAndMaterializesArgumentsOnceAcrossTheSuspension() throws Exception {
        ProtosI158ActorBootstrapSuspensionTest.Harness x =
                ProtosI158ActorBootstrapSuspensionTest.Harness.create(
                        "app", ProtosModuleKey.source("canonical:app"), I158_ARGUMENTS);
        ProtosObjectValue holder = new ProtosObjectValue(ProtosObjectValue.rootObject());

        ProtosActorRefValue reference = x.invokeSpawn(x.acquire(), holder);
        ProtosActor spawned = x.local(reference);
        assertEquals(ProtosActor.LifecycleState.INITIALIZING, spawned.lifecycleState());
        x.executor().drain();
        assertEquals(ProtosActor.LifecycleState.INITIALIZING, spawned.lifecycleState());
        assertTrue(spawned.currentBehavior().isEmpty());
        // The suspended initialization owns no Task.
        assertEquals(0, spawned.executionDomain().liveTaskCount());

        x.backend().pending.pollFirst().failed();
        x.executor().drain();

        ProtosObjectValue behavior = spawned.currentBehavior().orElseThrow();
        Object before = behavior.readLocalSlot("before").orElseThrow();
        assertSame(before, behavior.readLocalSlot("after").orElseThrow());
        assertNotSame(holder, before, "destination-local materialization");
        assertSame(behavior, spawned.currentBehavior().orElseThrow());
        assertEquals(0, spawned.executionDomain().liveTaskCount());
    }

    @Test
    void i158ModuleBindingSuspensionKeepsTheReceiverAndPrivateContext() throws Exception {
        ProtosModuleKey key = ProtosModuleKey.module("canonical:mod:app");
        ProtosI158ActorBootstrapSuspensionTest.Harness x =
                ProtosI158ActorBootstrapSuspensionTest.Harness.create(
                        "mod:app",
                        key,
                        """
                        hidden: 3
                        this.boot: (acceptor) => {
                            receiver: this
                            Error.handle(() => { acceptor.accept().value() }, (error) => { null })
                            { receiver: receiver; same: receiver == this; hidden: hidden }
                        }
                        """);
        ProtosActor spawned = x.local(x.invokeSpawn(x.acquire()));
        x.executor().drain();
        x.backend().pending.pollFirst().failed();
        x.executor().drain();

        ProtosObjectValue publicModule = spawned.moduleState().lookup(key).orElseThrow().instance();
        ProtosObjectValue behavior = spawned.currentBehavior().orElseThrow();
        assertSame(publicModule, behavior.readLocalSlot("receiver").orElseThrow());
        assertFalse(publicModule.hasLocalSlot("hidden"));
        assertEquals("3", ProtosNumericValueSupport.integerDecimalText(behavior.readLocalSlot("hidden").orElseThrow()));
    }

    @Test
    void i158HandledAndUnhandledFutureErrorsDuringBootstrap() throws Exception {
        ProtosI158ActorBootstrapSuspensionTest.Harness handled =
                ProtosI158ActorBootstrapSuspensionTest.Harness.create(
                        "app",
                        ProtosModuleKey.source("canonical:app"),
                        "boot: (acceptor) => {\n"
                                + "    label: IOError.handle(() => { acceptor.accept().value() }, (error) => { \"io\" })\n"
                                + "    { label: label }\n"
                                + "}\n");
        ProtosActor ok = handled.local(handled.invokeSpawn(handled.acquire()));
        handled.executor().drain();
        handled.backend().pending.pollFirst().failed();
        handled.executor().drain();
        assertEquals(ProtosActor.LifecycleState.READY, ok.lifecycleState());

        ProtosI158ActorBootstrapSuspensionTest.Harness unhandled =
                ProtosI158ActorBootstrapSuspensionTest.Harness.create(
                        "app",
                        ProtosModuleKey.source("canonical:app"),
                        "boot: (acceptor) => {\n    acceptor.accept().value()\n    {}\n}\n");
        ProtosActor failed = unhandled.local(unhandled.invokeSpawn(unhandled.acquire()));
        unhandled.executor().drain();
        unhandled.backend().pending.pollFirst().failed();
        unhandled.executor().drain();
        assertEquals(ProtosActor.LifecycleState.TERMINATED, failed.lifecycleState());
        assertTrue(failed.currentBehavior().isEmpty());
        // A non-root failure does not terminate the Process.
        assertEquals(ProtosProcessRuntime.LifecycleState.RUNNING, unhandled.process().lifecycleState());
    }

    @Test
    void i158ResumptionMayRunOnADifferentCarrierThread() throws Exception {
        ProtosI158ActorBootstrapSuspensionTest.Harness x =
                ProtosI158ActorBootstrapSuspensionTest.Harness.create(
                        "app", ProtosModuleKey.source("canonical:app"), I158_ARGUMENTS);
        ProtosActor spawned =
                x.local(x.invokeSpawn(x.acquire(), new ProtosObjectValue(ProtosObjectValue.rootObject())));
        x.executor().drain();
        x.backend().pending.pollFirst().failed();

        Thread other = new Thread(() -> x.executor().drain(), "i158-second-carrier");
        other.start();
        other.join(TimeUnit.SECONDS.toMillis(5));
        assertFalse(other.isAlive());
        assertEquals(ProtosActor.LifecycleState.READY, spawned.lifecycleState());
    }

    // ---- I158: body-level suspension on a request to an independent provider Actor -------
    // The test execution host puts the guest's global Actor.spawn on the same manual scheduler,
    // so the provider cannot answer before the consumer has suspended.

    private static final String PROVIDER =
            """
            boot: () => {
                { twice: (n) => { n * 2 } }
            }
            """;

    @Test
    void i158SpawnedSourceBodyAwaitsAProviderRequestBeforeDefiningItsBinding() throws Exception {
        ProtosI158ActorBootstrapSuspensionTest.Harness x =
                ProtosI158ActorBootstrapSuspensionTest.Harness.create(
                                "app",
                                ProtosModuleKey.source("canonical:app"),
                                """
                                effects: 0
                                effects = effects + 1
                                provider: Actor.spawn("provider", "boot")
                                answer: provider.request("twice", 21).value()
                                effects = effects + 10
                                boot: () => {
                                    {
                                        answer: answer
                                        poke: () => {
                                            effects = effects + 100
                                            null
                                        }
                                    }
                                }
                                """,
                                Map.of("canonical:provider", PROVIDER))
                        .hosted();

        ProtosActorRefValue reference = x.invokeSpawn();
        ProtosActor consumer = x.local(reference);
        assertEquals(ProtosActor.LifecycleState.INITIALIZING, consumer.lifecycleState(), "immediate ActorRef");
        ProtosInvocation.invokeMessage(
                reference, "send", List.of(new ProtosStringValue("poke")), x.activation());

        ProtosFutureValue awaited = observeRetainedProviderRequest(
                x, consumer,
                () -> assertEquals(1L, x.sourceSlot(consumer, "effects"),
                        "no message dispatched before READY"));
        assertEquals(ProtosFutureValue.State.RESOLVED, awaited.state(),
                "the provider answers only after the suspension has been inspected");

        assertEquals(ProtosActor.LifecycleState.READY, consumer.lifecycleState());
        ProtosObjectValue behavior = consumer.currentBehavior().orElseThrow();
        assertEquals("42", ProtosNumericValueSupport.integerDecimalText(behavior.readLocalSlot("answer").orElseThrow()));
        // Module effects ran once (1 + 10); the retained message ran once, after READY (+100).
        assertEquals(111L, x.sourceSlot(consumer, "effects"));
        assertSame(behavior, consumer.currentBehavior().orElseThrow(), "one behavior publication");
    }

    @Test
    void i158SpawnedModuleBodyAwaitsAProviderRequestAndKeepsItsPublicIdentity() throws Exception {
        ProtosModuleKey key = ProtosModuleKey.module("canonical:mod:app");
        ProtosI158ActorBootstrapSuspensionTest.Harness x =
                ProtosI158ActorBootstrapSuspensionTest.Harness.create(
                                "mod:app",
                                key,
                                """
                                effects: 0
                                effects = effects + 1
                                provider: Actor.spawn("provider", "boot")
                                answer: provider.request("twice", 21).value()
                                effects = effects + 10
                                this.boot: () => {
                                    hits: { count: 0 }
                                    moduleOwner: this
                                    {
                                        owner: moduleOwner
                                        answer: answer
                                        effects: effects
                                        hits: hits
                                        poke: () => {
                                            hits.count = hits.count + 1
                                            null
                                        }
                                    }
                                }
                                """,
                                Map.of("canonical:provider", PROVIDER))
                        .hosted();

        ProtosActorRefValue reference = x.invokeSpawn();
        ProtosActor consumer = x.local(reference);
        assertEquals(ProtosActor.LifecycleState.INITIALIZING, consumer.lifecycleState(), "immediate ActorRef");

        // Queue this message before initialization. It cannot dispatch until READY.
        ProtosInvocation.invokeMessage(
                reference, "send", List.of(new ProtosStringValue("poke")), x.activation());
        java.util.concurrent.atomic.AtomicReference<ProtosObjectValue> moduleAtSuspension =
                new java.util.concurrent.atomic.AtomicReference<>();
        ProtosFutureValue awaited = observeRetainedProviderRequest(
                x, consumer, () -> {
                    ProtosObjectValue partial =
                            consumer.moduleState().lookup(key).orElseThrow().instance();
                    moduleAtSuspension.set(partial);
                    assertFalse(partial.hasLocalSlot("boot"),
                            "the binding is not selectable before the body ends");
                });
        assertEquals(ProtosFutureValue.State.RESOLVED, awaited.state());
        ProtosObjectValue publicModule = moduleAtSuspension.get();
        assertNotNull(publicModule);

        assertEquals(ProtosActor.LifecycleState.READY, consumer.lifecycleState());
        assertSame(publicModule, consumer.moduleState().lookup(key).orElseThrow().instance());
        ProtosObjectValue behavior = consumer.currentBehavior().orElseThrow();
        assertSame(publicModule, behavior.readLocalSlot("owner").orElseThrow());
        assertEquals("42", ProtosNumericValueSupport.integerDecimalText(behavior.readLocalSlot("answer").orElseThrow()));
        // Selected only after the body ended: it sees both effects, each exactly once.
        assertEquals("11", ProtosNumericValueSupport.integerDecimalText(behavior.readLocalSlot("effects").orElseThrow()));
        for (String privateName : List.of("effects", "provider", "answer")) {
            assertFalse(publicModule.hasLocalSlot(privateName), privateName + " stays private");
        }
        assertSame(behavior, consumer.currentBehavior().orElseThrow());
        // The retained message ran after READY, exactly once.
        ProtosObjectValue hits = (ProtosObjectValue) behavior.readLocalSlot("hits").orElseThrow();
        assertEquals("1", ProtosNumericValueSupport.integerDecimalText(hits.readLocalSlot("count").orElseThrow()));
    }

    /**
     * Observe the first pending provider request while the scheduler carrier
     * is paused after the consumer's continuation has been retained.
     *
     * A single ManualExecutor command can execute several Actor segments.
     * Inspecting only after runOne() may miss the suspension entirely.
     */
    private static ProtosFutureValue observeRetainedProviderRequest(
            ProtosI158ActorBootstrapSuspensionTest.Harness x,
            ProtosActor consumer,
            Runnable whileSuspended) throws Exception {
        java.util.concurrent.CountDownLatch retained =
                new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch release =
                new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicReference<ProtosFutureValue> observed =
                new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicReference<Throwable> carrierFailure =
                new java.util.concurrent.atomic.AtomicReference<>();

        ProtosActorBootstrapSuspension.probeForTesting = (point, future) -> {
            if (point != ProtosActorBootstrapSuspension.ProbePoint.AFTER_RETENTION
                    || !observed.compareAndSet(null, future)) {
                return;
            }
            retained.countDown();
            try {
                if (!release.await(5, TimeUnit.SECONDS)) {
                    throw new AssertionError("test barrier was not released");
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new AssertionError("test carrier interrupted", interrupted);
            }
        };

        Thread carrier = Thread.ofPlatform()
                .name("i158-provider-bootstrap-carrier")
                .start(() -> {
                    try {
                        x.executor().drain();
                    } catch (Throwable failure) {
                        carrierFailure.set(failure);
                    }
                });

        try {
            assertTrue(retained.await(5, TimeUnit.SECONDS),
                    "no bootstrap continuation retained: actor=" + consumer.lifecycleState()
                            + ", process=" + x.process().lifecycleState()
                            + ", carrierFailure=" + carrierFailure.get());

            ProtosFutureValue awaited = observed.get();
            assertNotNull(awaited);
            assertEquals(ProtosFutureValue.State.PENDING, awaited.state());
            assertSame(awaited,
                    ProtosActorBootstrapSuspension.awaitedFutureForTesting(consumer),
                    "the awaiting Future must belong to the consumer, not the provider");
            assertEquals(ProtosActor.LifecycleState.INITIALIZING,
                    consumer.lifecycleState());
            assertTrue(consumer.currentBehavior().isEmpty(),
                    "no behavior before READY");

            whileSuspended.run();
        } finally {
            release.countDown();
            carrier.join(TimeUnit.SECONDS.toMillis(5));
            ProtosActorBootstrapSuspension.probeForTesting = null;
        }

        assertFalse(carrier.isAlive(),
                "the provider/consumer carrier did not finish");

        if (carrierFailure.get() != null) {
            throw new AssertionError(
                    "provider/consumer carrier failed", carrierFailure.get());
        }

        return observed.get();
    }

    // ---- I158: the real RootActor service initialization Future (startRootService) ----------

    /** Test execution host: one manual scheduler, optional placement failure, cleanup counters. */
    private static final class CountingHost
            implements com.guillermomolina.protos.runtime.process.ProtosProcessExecutionHost {
        final com.guillermomolina.protos.runtime.ProtosActorScheduler scheduler;
        final RuntimeException placementFailure;
        final AtomicInteger actorTerminations = new AtomicInteger();
        final AtomicInteger processTerminations = new AtomicInteger();

        CountingHost(Executor carriers, RuntimeException placementFailure) {
            this.scheduler = new com.guillermomolina.protos.runtime.ProtosActorScheduler(carriers, 1);
            this.placementFailure = placementFailure;
        }

        @Override
        public <T> T callForRuntime(java.util.function.Supplier<T> action) {
            if (placementFailure != null) {
                throw placementFailure;
            }
            return ProtosTestExecutionSupport.callEntered(action);
        }

        @Override
        public Optional<com.guillermomolina.protos.runtime.ProtosActorScheduler> actorSchedulerForRuntime() {
            return Optional.of(scheduler);
        }

        @Override
        public void actorTerminatedForRuntime(ProtosActor actor) {
            actorTerminations.incrementAndGet();
        }

        @Override
        public void processTerminatedForRuntime() {
            processTerminations.incrementAndGet();
        }
    }

    private static ProtosI158ActorBootstrapSuspensionTest.Harness rootService(
            String source, CountingHost[] host, RuntimeException placementFailure) throws Exception {
        ProtosI158ActorBootstrapSuspensionTest.Harness x =
                ProtosI158ActorBootstrapSuspensionTest.Harness.create(
                        "app", ProtosModuleKey.source("canonical:app"), source);
        host[0] = new CountingHost(x.executor(), placementFailure);
        x.process().bindExecutionHostForRuntime(host[0]);
        return x;
    }

    @Test
    void i158RootServiceFutureCompletesTrueOnlyAfterTheBehaviorIsInstalled() throws Exception {
        CountingHost[] host = new CountingHost[1];
        var x = rootService("boot: () => { { ok: 1 } }\n", host, null);
        ProtosActor root = x.process().rootActorForRuntime();
        java.util.concurrent.CompletableFuture<Boolean> outcome =
                ProtosActorBootstrap.startRootService(x.process(), x.prelude(), x.key(), "boot");
        AtomicInteger behaviorAtCompletion = new AtomicInteger(-1);
        outcome.thenAccept(ready -> behaviorAtCompletion.set(root.currentBehavior().isPresent() ? 1 : 0));
        assertFalse(outcome.isDone(), "nothing ran before the scheduler turn");

        x.executor().drain();

        assertTrue(outcome.getNow(false));
        assertEquals(1, behaviorAtCompletion.get(), "true is published after the behavior");
        assertEquals(ProtosActor.LifecycleState.READY, root.lifecycleState());
    }

    @Test
    void i158RootServiceGuestFailureCompletesFalseAndTerminatesTheProcess() throws Exception {
        CountingHost[] host = new CountingHost[1];
        var x = rootService("boot: () => { Error().signal() }\n", host, null);
        java.util.concurrent.CompletableFuture<Boolean> outcome =
                ProtosActorBootstrap.startRootService(x.process(), x.prelude(), x.key(), "boot");

        x.executor().drain();

        assertTrue(outcome.isDone());
        assertFalse(outcome.isCompletedExceptionally(), "a Protos Error is not a host failure");
        assertFalse(outcome.join());
        assertTrue(x.process().rootActorForRuntime().currentBehavior().isEmpty());
        assertTrue(
                x.process().lifecycleState() != ProtosProcessRuntime.LifecycleState.RUNNING,
                "RootActor initialization failure terminates the Process");
    }

    @Test
    void i158RootServiceTerminationBeforeReadyCompletesFalse() throws Exception {
        CountingHost[] host = new CountingHost[1];
        // The only producer is a Task of the same Actor: the initialization stays suspended.
        var x = rootService("pending: (() => 1).future()\npending.value()\nboot: () => { {} }\n", host, null);
        ProtosActor root = x.process().rootActorForRuntime();
        java.util.concurrent.CompletableFuture<Boolean> outcome =
                ProtosActorBootstrap.startRootService(x.process(), x.prelude(), x.key(), "boot");
        x.executor().drain();
        assertFalse(outcome.isDone(), "a suspension is never reported as provisional success");
        assertEquals(ProtosActor.LifecycleState.INITIALIZING, root.lifecycleState());

        x.process().requestTerminationForRuntime();
        x.executor().drain();

        assertEquals(Boolean.FALSE, outcome.getNow(null));
        assertTrue(root.currentBehavior().isEmpty());
        assertEquals(ProtosActor.LifecycleState.TERMINATED, root.lifecycleState());
        assertTrue(host[0].processTerminations.get() <= 1, "Process cleanup runs at most once");
        assertTrue(host[0].actorTerminations.get() <= 1, "Actor cleanup runs at most once");
    }

    @Test
    void i158RootServiceHostFailureCompletesExceptionallyWithItsCause() throws Exception {
        CountingHost[] host = new CountingHost[1];
        IllegalStateException placement = new IllegalStateException("i158 test host placement failure");
        var x = rootService("boot: () => { {} }\n", host, placement);
        java.util.concurrent.CompletableFuture<Boolean> outcome =
                ProtosActorBootstrap.startRootService(x.process(), x.prelude(), x.key(), "boot");

        // No Context is entered by the carrier, so every segment goes through the host placement.
        x.executor().drainUnentered();

        assertTrue(outcome.isCompletedExceptionally());
        java.util.concurrent.CompletionException failure =
                assertThrows(java.util.concurrent.CompletionException.class, outcome::join);
        assertSame(placement, failure.getCause());
        assertTrue(x.process().rootActorForRuntime().currentBehavior().isEmpty());
    }

    @Test
    void i158RootServiceTerminationDuringAnActiveSegmentWaitsForIt() throws Exception {
        CountingHost[] host = new CountingHost[1];
        var x = rootService("pending: (() => 1).future()\npending.value()\nboot: () => { {} }\n", host, null);
        ProtosActor root = x.process().rootActorForRuntime();
        java.util.concurrent.CompletableFuture<Boolean> outcome =
                ProtosActorBootstrap.startRootService(x.process(), x.prelude(), x.key(), "boot");
        java.util.concurrent.atomic.AtomicReference<ProtosActor.LifecycleState> stateInSegment =
                new java.util.concurrent.atomic.AtomicReference<>();
        AtomicInteger segmentsInSegment = new AtomicInteger(-1);
        ProtosActorBootstrapSuspension.probeForTesting =
                (point, future) -> {
                    if (point != ProtosActorBootstrapSuspension.ProbePoint.BEFORE_REGISTRATION
                            || stateInSegment.get() != null) {
                        return;
                    }
                    Thread terminator =
                            new Thread(() -> x.process().requestTerminationForRuntime(), "i158-process-stop");
                    terminator.start();
                    try {
                        terminator.join(TimeUnit.SECONDS.toMillis(5));
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                    }
                    stateInSegment.set(root.lifecycleState());
                    segmentsInSegment.set(root.executionDomain().activeBootstrapSegmentCountForRuntime());
                };
        try {
            x.executor().drain();
        } finally {
            ProtosActorBootstrapSuspension.probeForTesting = null;
        }
        x.executor().drain();

        assertEquals(ProtosActor.LifecycleState.TERMINATING, stateInSegment.get(),
                "the Process cannot make the RootActor TERMINATED while its segment runs");
        assertEquals(1, segmentsInSegment.get());
        assertEquals(Boolean.FALSE, outcome.getNow(null));
        assertEquals(ProtosActor.LifecycleState.TERMINATED, root.lifecycleState());
        assertEquals(0, root.executionDomain().activeBootstrapSegmentCountForRuntime());
        assertTrue(host[0].processTerminations.get() <= 1);
        assertTrue(host[0].actorTerminations.get() <= 1);
    }
}
