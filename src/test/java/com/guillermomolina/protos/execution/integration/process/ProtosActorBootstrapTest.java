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
import com.guillermomolina.protos.runtime.ProtosIntegerValue;
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
        ProtosModuleKey key = new ProtosModuleKey("app/bootstrap");

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
        ProtosModuleKey key = new ProtosModuleKey("app/bootstrap");
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
                                        new ProtosModuleKey("app/bootstrap"),
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
                new ProtosIntegerValue(2L),
                new ProtosIntegerValue(3L),
                prelude);

        assertTrue(ProtosComplexNumericSupport.isComplex(complex));

        ProtosActorBootstrap bootstrap =
                new ProtosActorBootstrap(new ProtosModuleRuntime(resolver));
        ProtosModuleKey key = new ProtosModuleKey("app/bootstrap");

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
                                        new ProtosModuleKey("app/bootstrap"),
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
        ProtosModuleKey key = new ProtosModuleKey("app/bootstrap");

        assertTrue(bootstrap.initialize(actor, prelude, key, "bootstrap", List.of()));
        ProtosObjectValue behavior = actor.currentBehavior().orElseThrow();
        assertFalse(actor.completeInitialization(new ProtosObjectValue(ProtosObjectValue.rootObject())));
        assertSame(behavior, actor.currentBehavior().orElseThrow());
    }

    @Test
    void spawnBootstrapReceivesFreshDestinationLocalNestedTcpAcceptor() throws Exception {
        SpawnHarness x = spawnHarness();
        ProtosTcpAcceptorValue acceptor = x.acquire();
        acceptor.createLocalSlot("label", new ProtosIntegerValue(3));
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
                        return new ProtosModuleKey("canonical:" + exactSpecifier);
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
                        new ProtosModuleKey("creator"),
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
                                        new ProtosModuleKey("app/bootstrap"),
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
}
