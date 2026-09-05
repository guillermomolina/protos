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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class ProtosActorPublicApiTest {
    private static final Path CORE = Path.of("protos", "lib", "core");

    @Test
    void corePreludePublishesFrozenActorWithExactlySpawnAndCurrent() throws Exception {
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
        Object binding = prelude.bindings().readLocalSlot("Actor").orElseThrow();

        assertInstanceOf(ProtosObjectValue.class, binding);
        ProtosObjectValue actorObject = (ProtosObjectValue) binding;
        assertSame(ProtosObjectValue.rootObject(), actorObject.parent().orElseThrow());
        assertTrue(actorObject.isFrozen());
        assertEquals(Set.of("spawn", "current"), actorObject.localSlotsSnapshot().keySet());
        assertInstanceOf(ProtosClosureValue.class, actorObject.readLocalSlot("spawn").orElseThrow());
        assertInstanceOf(ProtosClosureValue.class, actorObject.readLocalSlot("current").orElseThrow());
        assertFalse(prelude.bindings().hasLocalSlot("SpawnOperation"));
        assertFalse(prelude.bindings().hasLocalSlot("ActorId"));
        assertFalse(prelude.bindings().hasLocalSlot("Mailbox"));
        assertFalse(prelude.bindings().hasLocalSlot("Task"));
    }

    @Test
    void actorProtocolInstallsIntoTheExactProvidedObjectWithoutAllocatingAReplacement() {
        ProtosObjectValue sourceObject =
                new ProtosObjectValue(ProtosObjectValue.rootObject());
        ProtosObjectValue actorRefPrototype =
                new ProtosObjectValue(ProtosObjectValue.rootObject());
        ProtosObjectValue sendOperationPrototype =
                new ProtosObjectValue(ProtosObjectValue.rootObject());
        ProtosObjectValue installed =
                new ProtosStandardActorProtocol(
                                new ProtosModuleRuntime(ProtosModuleResolver.rejecting()),
                                new ManualExecutor(),
                                actorRefPrototype,
                                sendOperationPrototype)
                        .installActorObject(sourceObject);

        assertSame(sourceObject, installed);
        assertTrue(installed.isFrozen());
        assertEquals(Set.of("spawn", "current"), installed.localSlotsSnapshot().keySet());
        assertTrue(actorRefPrototype.isFrozen());
        assertEquals(
                Set.of("send", "request", "stop", "termination"),
                actorRefPrototype.localSlotsSnapshot().keySet());
        assertTrue(sendOperationPrototype.isFrozen());
        assertEquals(
                Set.of("cancel", "retry"),
                sendOperationPrototype.localSlotsSnapshot().keySet());

        ProtosClosureValue spawn =
                assertInstanceOf(
                        ProtosClosureValue.class,
                        installed.readLocalSlot("spawn").orElseThrow());
        ProtosClosureValue current =
                assertInstanceOf(
                        ProtosClosureValue.class,
                        installed.readLocalSlot("current").orElseThrow());
        assertTrue(spawn.nativeBody().isPresent());
        assertTrue(current.nativeBody().isPresent());
        assertNull(spawn.definition());
        assertNull(current.definition());
    }

    @Test
    void currentUsesActorExecutionDomainAndKeepsSemanticIdentityAfterTermination()
            throws Exception {
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
        ProtosObjectValue actorObject = actorObject(prelude);
        ProtosActor actor = new ProtosActor(actorRefPrototype());
        ProtosActivation activation = actorActivation(prelude, actor, null);

        Object first = ProtosInvocation.invokeMessage(actorObject, "current", List.of(), activation);
        Object second = ProtosInvocation.invokeMessage(actorObject, "current", List.of(), activation);

        assertSame(actor.reference(), first);
        assertSame(actor.reference(), second);
        assertTrue(ProtosIdentity.identical(first, second));

        actor.beginTermination();
        actor.markTerminated();
        Object afterTermination =
                ProtosInvocation.invokeMessage(actorObject, "current", List.of(), activation);
        assertSame(first, afterTermination);
        assertTrue(ProtosIdentity.identical(first, afterTermination));
    }

    @Test
    void distinctActorsProduceDistinctCurrentReferences() throws Exception {
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
        ProtosObjectValue actorObject = actorObject(prelude);
        ProtosActor first = new ProtosActor(actorRefPrototype());
        ProtosActor second = new ProtosActor(actorRefPrototype());

        Object firstCurrent =
                ProtosInvocation.invokeMessage(
                        actorObject, "current", List.of(), actorActivation(prelude, first, null));
        Object secondCurrent =
                ProtosInvocation.invokeMessage(
                        actorObject, "current", List.of(), actorActivation(prelude, second, null));

        assertFalse(ProtosIdentity.identical(firstCurrent, secondCurrent));
    }

    @Test
    void spawnRejectsNonSemanticStringArgumentsBeforeResolutionOrCreation() throws Exception {
        RecordingResolver resolver = new RecordingResolver();
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE, resolver);
        ManualExecutor executor = new ManualExecutor();
        ProtosObjectValue actorObject = actorObject(new ProtosModuleRuntime(resolver), executor);
        ProtosActor creator = new ProtosActor(actorRefPrototype());
        ProtosActivation activation = actorActivation(prelude, creator, new ProtosModuleKey("creator"));

        assertThrows(
                ProtosSignalException.class,
                () ->
                        ProtosInvocation.invokeMessage(
                                actorObject,
                                "spawn",
                                List.of(new ProtosObjectValue(prelude.stringPrototype()), new ProtosStringValue("boot")),
                                activation));
        assertThrows(
                ProtosSignalException.class,
                () ->
                        ProtosInvocation.invokeMessage(
                                actorObject,
                                "spawn",
                                List.of(new ProtosStringValue("app"), new ProtosObjectValue(prelude.stringPrototype())),
                                activation));

        assertEquals(0, resolver.resolveCalls.get());
        assertEquals(0, executor.size());
    }

    @Test
    void failedCreatorSideResolutionQueuesNoBootstrap() throws Exception {
        RecordingResolver resolver = new RecordingResolver().failResolution();
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE, resolver);
        ManualExecutor executor = new ManualExecutor();
        ProtosObjectValue actorObject = actorObject(new ProtosModuleRuntime(resolver), executor);
        ProtosActor creator = new ProtosActor(actorRefPrototype());
        ProtosActivation activation = actorActivation(prelude, creator, new ProtosModuleKey("creator"));

        assertThrows(
                ProtosSignalException.class,
                () ->
                        ProtosInvocation.invokeMessage(
                                actorObject,
                                "spawn",
                                List.of(new ProtosStringValue("missing"), new ProtosStringValue("boot")),
                                activation));

        assertEquals(1, resolver.resolveCalls.get());
        assertEquals(0, executor.size());
    }

    @Test
    void spawnResolvesOnceSnapshotsWholeArgumentGraphAndReturnsBeforeReady() throws Exception {
        RecordingResolver resolver =
                new RecordingResolver()
                        .module(
                                "canonical:app",
                                "boot: (left, right) => {\n"
                                        + "    {\n"
                                        + "        left: left\n"
                                        + "        right: right\n"
                                        + "        current: Actor.current()\n"
                                        + "    }\n"
                                        + "}");
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE, resolver);
        ManualExecutor executor = new ManualExecutor();
        ProtosObjectValue actorObject = actorObject(new ProtosModuleRuntime(resolver), executor);
        ProtosActor creator = new ProtosActor(actorRefPrototype());
        ProtosModuleKey creatorKey = new ProtosModuleKey("creator");
        ProtosActivation activation = actorActivation(prelude, creator, creatorKey);
        ProtosObjectValue source = new ProtosObjectValue(ProtosObjectValue.rootObject());
        source.createLocalSlot("self", source);

        Object result =
                ProtosInvocation.invokeMessage(
                        actorObject,
                        "spawn",
                        List.of(
                                new ProtosStringValue("app"),
                                new ProtosStringValue("boot"),
                                source,
                                source),
                        activation);

        ProtosActorRefValue reference = assertInstanceOf(ProtosActorRefValue.class, result);
        ProtosActor spawned = reference.localActorForRuntime();
        assertEquals(ProtosActor.LifecycleState.INITIALIZING, spawned.lifecycleState());
        assertTrue(spawned.currentBehavior().isEmpty());
        assertEquals(1, executor.size());
        assertEquals(1, resolver.resolveCalls.get());
        assertEquals(Optional.of(creatorKey), resolver.lastImportingModule);
        assertEquals("app", resolver.lastSpecifier);
        assertEquals(0, resolver.loads("canonical:app"));

        executor.runNext();

        assertEquals(ProtosActor.LifecycleState.READY, spawned.lifecycleState());
        assertEquals(1, resolver.resolveCalls.get(), "destination must not re-resolve spelling");
        assertEquals(1, resolver.loads("canonical:app"));
        ProtosObjectValue behavior = spawned.currentBehavior().orElseThrow();
        Object left = behavior.readLocalSlot("left").orElseThrow();
        Object right = behavior.readLocalSlot("right").orElseThrow();
        assertSame(left, right, "one transfer memo must preserve aliasing across argument roots");
        assertNotSame(source, left);
        ProtosObjectValue copied = assertInstanceOf(ProtosObjectValue.class, left);
        assertSame(copied, copied.readLocalSlot("self").orElseThrow());
        Object current = behavior.readLocalSlot("current").orElseThrow();
        assertTrue(ProtosIdentity.identical(reference, current));
    }

    @Test
    void nonTransferableArgumentFailsSynchronouslyBeforeCreationCutover() throws Exception {
        RecordingResolver resolver = new RecordingResolver().module("canonical:app", "boot: () => { {} }");
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE, resolver);
        ManualExecutor executor = new ManualExecutor();
        ProtosObjectValue actorObject = actorObject(new ProtosModuleRuntime(resolver), executor);
        ProtosActor creator = new ProtosActor(actorRefPrototype());
        ProtosActivation activation = actorActivation(prelude, creator, null);
        ProtosClosureValue closure = ProtosClosureValue.nativeClosure((ignored, arguments) -> ProtosNullValue.INSTANCE);

        ProtosSignalException failure =
                assertThrows(
                        ProtosSignalException.class,
                        () ->
                                ProtosInvocation.invokeMessage(
                                        actorObject,
                                        "spawn",
                                        List.of(
                                                new ProtosStringValue("app"),
                                                new ProtosStringValue("boot"),
                                                closure),
                                        activation));

        assertSame(
                prelude.standardErrorPrototype("NonTransferableValue"),
                failure.error().parent().orElseThrow());
        assertEquals(1, resolver.resolveCalls.get());
        assertEquals(0, executor.size());
    }

    @Test
    void bootstrapFailureTerminatesTheSameAlreadyReturnedIncarnation() throws Exception {
        RecordingResolver resolver =
                new RecordingResolver().module("canonical:app", "boot: () => { 7 }");
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE, resolver);
        ManualExecutor executor = new ManualExecutor();
        ProtosObjectValue actorObject = actorObject(new ProtosModuleRuntime(resolver), executor);
        ProtosActor creator = new ProtosActor(actorRefPrototype());
        ProtosActivation activation = actorActivation(prelude, creator, null);

        ProtosActorRefValue reference =
                assertInstanceOf(
                        ProtosActorRefValue.class,
                        ProtosInvocation.invokeMessage(
                                actorObject,
                                "spawn",
                                List.of(new ProtosStringValue("app"), new ProtosStringValue("boot")),
                                activation));
        ProtosActor spawned = reference.localActorForRuntime();
        long identity = spawned.incarnationIdentityForRuntime();
        assertEquals(ProtosActor.LifecycleState.INITIALIZING, spawned.lifecycleState());

        executor.runNext();

        assertEquals(ProtosActor.LifecycleState.TERMINATED, spawned.lifecycleState());
        assertSame(reference, spawned.reference());
        assertEquals(identity, spawned.incarnationIdentityForRuntime());
        assertTrue(ProtosIdentity.identical(reference, spawned.reference()));
    }

    private static ProtosObjectValue actorObject(ProtosPrelude prelude) {
        return assertInstanceOf(
                ProtosObjectValue.class,
                prelude.bindings().readLocalSlot("Actor").orElseThrow());
    }

    private static ProtosObjectValue actorObject(
            ProtosModuleRuntime runtime, Executor executor) {
        ProtosObjectValue actorObject =
                new ProtosObjectValue(ProtosObjectValue.rootObject());
        ProtosObjectValue actorRefPrototype =
                new ProtosObjectValue(ProtosObjectValue.rootObject());
        ProtosObjectValue sendOperationPrototype =
                new ProtosObjectValue(ProtosObjectValue.rootObject());
        return new ProtosStandardActorProtocol(
                        runtime, executor, actorRefPrototype, sendOperationPrototype)
                .installActorObject(actorObject);
    }

    private static ProtosActivation actorActivation(
            ProtosPrelude prelude, ProtosActor actor, ProtosModuleKey moduleKey) {
        return prelude.newModuleActivation(
                actor.moduleState(),
                moduleKey,
                prelude.newExecutionContext(),
                actor.executionDomain());
    }

    private static ProtosObjectValue actorRefPrototype() {
        return new ProtosObjectValue(ProtosObjectValue.rootObject()).freeze();
    }

    private static final class ManualExecutor implements Executor {
        private final ArrayDeque<Runnable> pending = new ArrayDeque<>();

        @Override
        public void execute(Runnable command) {
            pending.addLast(command);
        }

        int size() {
            return pending.size();
        }

        void runNext() {
            pending.removeFirst().run();
        }
    }

    private static final class RecordingResolver implements ProtosModuleResolver {
        private final Map<String, String> sources = new HashMap<>();
        private final Map<String, Integer> loadCounts = new HashMap<>();
        private final AtomicInteger resolveCalls = new AtomicInteger();
        private boolean resolutionFails;
        private String lastSpecifier;
        private Optional<ProtosModuleKey> lastImportingModule = Optional.empty();

        RecordingResolver module(String canonicalKey, String source) {
            sources.put(canonicalKey, source);
            return this;
        }

        RecordingResolver failResolution() {
            resolutionFails = true;
            return this;
        }

        int loads(String canonicalKey) {
            return loadCounts.getOrDefault(canonicalKey, 0);
        }

        @Override
        public ProtosModuleKey resolve(
                String exactSpecifier, Optional<ProtosModuleKey> importingModule) throws Exception {
            resolveCalls.incrementAndGet();
            lastSpecifier = exactSpecifier;
            lastImportingModule = importingModule;
            if (resolutionFails) {
                throw new java.io.IOException("resolution failed");
            }
            return new ProtosModuleKey("canonical:" + exactSpecifier);
        }

        @Override
        public String loadSource(ProtosModuleKey key) throws Exception {
            loadCounts.merge(key.canonicalId(), 1, Integer::sum);
            String source = sources.get(key.canonicalId());
            if (source == null) {
                throw new java.io.IOException("module not found: " + key.canonicalId());
            }
            return source;
        }
    }
}
