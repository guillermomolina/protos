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


import com.guillermomolina.protos.execution.ProtosModuleSource;
import static org.junit.jupiter.api.Assertions.*;

import com.guillermomolina.protos.execution.ProtosActorBootstrap;
import com.guillermomolina.protos.execution.ProtosCoreBootstrap;
import com.guillermomolina.protos.execution.ProtosInvocation;
import com.guillermomolina.protos.execution.ProtosModuleResolver;
import com.guillermomolina.protos.execution.ProtosModuleRuntime;
import com.guillermomolina.protos.execution.ProtosStandardActorProtocol;
import com.guillermomolina.protos.runtime.modules.ProtosModuleKey;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class ProtosProcessRuntimeTest {
    private static final Path CORE = Path.of("protos", "lib", "core");

    @Test
    void processOwnsOneRootAndHostedActorSet() {
        ProtosObjectValue prototype = actorRefPrototype();
        ProtosProcessRuntime process = new ProtosProcessRuntime(prototype);
        ProtosActor root = process.rootActorForRuntime();
        ProtosActor child = process.createHostedActorForRuntime(prototype);

        assertEquals(ProtosProcessRuntime.LifecycleState.RUNNING, process.lifecycleState());
        assertTrue(root.isRootActorForRuntime());
        assertFalse(child.isRootActorForRuntime());
        assertSame(process, root.processForRuntime().orElseThrow());
        assertSame(process, child.processForRuntime().orElseThrow());
        assertEquals(2, process.liveActorCountForTesting());
    }

    @Test
    void nonRootFatalFailureTerminatesOnlyThatIncarnation() {
        ProtosObjectValue prototype = actorRefPrototype();
        ProtosProcessRuntime process = new ProtosProcessRuntime(prototype);
        ProtosActor root = process.rootActorForRuntime();
        ProtosActor child = process.createHostedActorForRuntime(prototype);
        ProtosObjectValue failure = new ProtosObjectValue(ProtosObjectValue.rootObject());

        child.failForRuntime(failure);

        assertEquals(ProtosActor.LifecycleState.TERMINATED, child.lifecycleState());
        assertEquals(ProtosActor.LifecycleState.INITIALIZING, root.lifecycleState());
        assertEquals(ProtosProcessRuntime.LifecycleState.RUNNING, process.lifecycleState());
        assertEquals(1, process.liveActorCountForTesting());
        assertTrue(process.rootFailureCauseForTesting().isEmpty());
    }

    @Test
    void rootFatalFailureTerminatesWholeProcessWithoutExportingFailure() {
        ProtosObjectValue prototype = actorRefPrototype();
        ProtosProcessRuntime process = new ProtosProcessRuntime(prototype);
        ProtosActor root = process.rootActorForRuntime();
        ProtosActor first = process.createHostedActorForRuntime(prototype);
        ProtosActor second = process.createHostedActorForRuntime(prototype);
        ProtosObjectValue failure = new ProtosObjectValue(ProtosObjectValue.rootObject());

        root.failForRuntime(failure);

        assertEquals(ProtosActor.LifecycleState.TERMINATED, root.lifecycleState());
        assertEquals(ProtosActor.LifecycleState.TERMINATED, first.lifecycleState());
        assertEquals(ProtosActor.LifecycleState.TERMINATED, second.lifecycleState());
        assertEquals(ProtosProcessRuntime.LifecycleState.TERMINATED, process.lifecycleState());
        assertEquals(0, process.liveActorCountForTesting());
        assertSame(failure, process.rootFailureCauseForTesting().orElseThrow());
    }

    @Test
    void rootBootstrapErrorUsesProcessFailureAuthority() throws Exception {
        ProtosModuleResolver resolver =
                new ProtosModuleResolver() {
                    @Override
                    public ProtosModuleKey resolve(
                            String exactSpecifier, Optional<ProtosModuleKey> importingModule) {
                        return ProtosModuleKey.source("canonical:" + exactSpecifier);
                    }

                    @Override
                    public ProtosModuleSource loadSource(ProtosModuleKey key) {
                        return ProtosModuleSource.fromCharacters(key, "boot: () => { 7 }");
                    }
                };
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE, resolver);
        ProtosProcessRuntime process = new ProtosProcessRuntime(actorRefPrototype());
        ProtosActor root = process.rootActorForRuntime();
        ProtosActorBootstrap bootstrap =
                new ProtosActorBootstrap(new ProtosModuleRuntime(resolver));

        ProtosSignalException failure =
                assertThrows(
                        ProtosSignalException.class,
                        () ->
                                bootstrap.initialize(
                                        root,
                                        prelude,
                                        ProtosModuleKey.source("canonical:app"),
                                        "boot",
                                        List.of()));

        assertEquals(ProtosActor.LifecycleState.TERMINATED, root.lifecycleState());
        assertEquals(ProtosProcessRuntime.LifecycleState.TERMINATED, process.lifecycleState());
        assertSame(failure.error(), process.rootFailureCauseForTesting().orElseThrow());
    }

    @Test
    void processTerminationWaitsForActorCancellationUnwind() {
        ProtosObjectValue prototype = actorRefPrototype();
        ProtosProcessRuntime process = new ProtosProcessRuntime(prototype);
        ProtosActor root = process.rootActorForRuntime();
        ProtosActor child = process.createHostedActorForRuntime(prototype);
        ProtosTask.WaitDependency wait = new ProtosTask.WaitDependency() {};
        AtomicInteger segments = new AtomicInteger();
        ProtosTask task =
                child.executionDomain()
                        .createTask(
                                null,
                                current -> {
                                    if (segments.getAndIncrement() == 0) {
                                        assertTrue(current.suspend(wait));
                                        return;
                                    }
                                    assertTrue(current.observeCancellation());
                                });

        assertTrue(child.executionDomain().dispatchOne());
        assertEquals(ProtosTask.State.SUSPENDED, task.state());

        root.failForRuntime(new ProtosObjectValue(ProtosObjectValue.rootObject()));

        assertEquals(ProtosActor.LifecycleState.TERMINATED, root.lifecycleState());
        assertEquals(ProtosActor.LifecycleState.TERMINATING, child.lifecycleState());
        assertEquals(ProtosTask.State.RUNNABLE, task.state());
        assertEquals(ProtosProcessRuntime.LifecycleState.TERMINATING, process.lifecycleState());

        assertTrue(child.executionDomain().dispatchOne());

        assertEquals(ProtosTask.State.CANCELLED, task.state());
        assertEquals(ProtosActor.LifecycleState.TERMINATED, child.lifecycleState());
        assertEquals(ProtosProcessRuntime.LifecycleState.TERMINATED, process.lifecycleState());
    }

    @Test
    void standardSpawnFromProcessBoundActorKeepsLocalProcessHosting() throws Exception {
        ProtosModuleResolver resolver =
                new ProtosModuleResolver() {
                    @Override
                    public ProtosModuleKey resolve(
                            String exactSpecifier, Optional<ProtosModuleKey> importingModule) {
                        return ProtosModuleKey.source("canonical:" + exactSpecifier);
                    }

                    @Override
                    public ProtosModuleSource loadSource(ProtosModuleKey key) {
                        return ProtosModuleSource.fromCharacters(key, "boot: () => { {} }");
                    }
                };
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE, resolver);
        ManualExecutor executor = new ManualExecutor();
        ProtosObjectValue actorRefPrototype =
                new ProtosObjectValue(ProtosObjectValue.rootObject());
        ProtosObjectValue sendOperationPrototype =
                new ProtosObjectValue(ProtosObjectValue.rootObject());
        ProtosObjectValue actorObject =
                new ProtosObjectValue(ProtosObjectValue.rootObject());
        new ProtosStandardActorProtocol(
                        new ProtosModuleRuntime(resolver),
                        executor,
                        actorRefPrototype,
                        sendOperationPrototype)
                .installActorObject(actorObject);
        ProtosProcessRuntime process = new ProtosProcessRuntime(actorRefPrototype);
        ProtosActor root = process.rootActorForRuntime();
        ProtosActivation activation =
                prelude.newModuleActivation(
                        root.moduleState(),
                        ProtosModuleKey.source("creator"),
                        prelude.newExecutionContext(),
                        root.executionDomain());

        ProtosActorRefValue spawnedReference =
                assertInstanceOf(
                        ProtosActorRefValue.class,
                        ProtosInvocation.invokeMessage(
                                actorObject,
                                "spawn",
                                List.of(
                                        new ProtosStringValue("app"),
                                        new ProtosStringValue("boot")),
                                activation));
        ProtosActor spawned = spawnedReference.localActorForRuntime();

        assertSame(process, spawned.processForRuntime().orElseThrow());
        assertFalse(spawned.isRootActorForRuntime());
        assertEquals(2, process.liveActorCountForTesting());
        assertEquals(1, executor.size());
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
    }

    // ---- I158 / D251-A1: termination of a suspended initialization ------------------------
    // The custodian mirrors the two-phase protocol of ProtosActorBootstrapSuspension: phase 1
    // (terminationBegun) only invalidates and registers the pending cleanup obligation; phase 2
    // runs later under domain ownership and releases it.

    private static final class TwoPhaseCustody implements ProtosActor.BootstrapCustody {
        private final ProtosActor actor;
        private final AtomicInteger invalidations = new AtomicInteger();
        private final AtomicInteger cleanups = new AtomicInteger();

        TwoPhaseCustody(ProtosActor actor) {
            this.actor = actor;
        }

        @Override
        public void terminationBegun() {
            invalidations.incrementAndGet();
            actor.executionDomain().beginBootstrapCleanupForRuntime();
        }

        void runCleanup() {
            if (cleanups.incrementAndGet() == 1) {
                actor.clearBootstrapCustodyForRuntime(this);
                actor.executionDomain().finishBootstrapCleanupForRuntime();
            }
        }
    }

    @Test
    void i158NonRootSuspendedTerminationWaitsForCleanupAndSparesTheProcess() {
        ProtosObjectValue prototype = actorRefPrototype();
        ProtosProcessRuntime process = new ProtosProcessRuntime(prototype);
        ProtosActor child = process.createHostedActorForRuntime(prototype);
        TwoPhaseCustody custody = new TwoPhaseCustody(child);
        assertTrue(child.registerBootstrapCustodyForRuntime(custody));

        child.requestTerminationForRuntime();
        child.requestTerminationForRuntime();

        assertEquals(1, custody.invalidations.get(), "invalidated exactly once");
        assertEquals(ProtosActor.LifecycleState.TERMINATING, child.lifecycleState());
        assertTrue(child.executionDomain().hasTerminationCleanupForRuntime());

        custody.runCleanup();
        custody.runCleanup();

        assertEquals(ProtosActor.LifecycleState.TERMINATED, child.lifecycleState());
        assertFalse(child.executionDomain().hasTerminationCleanupForRuntime());
        assertTrue(child.currentBehavior().isEmpty());
        assertEquals(ProtosProcessRuntime.LifecycleState.RUNNING, process.lifecycleState());
        assertFalse(child.registerBootstrapCustodyForRuntime(new TwoPhaseCustody(child)));
    }

    @Test
    void i158ProcessTerminationWithASuspendedRootActorCompletesOnlyAfterCleanup() {
        ProtosObjectValue prototype = actorRefPrototype();
        ProtosProcessRuntime process = new ProtosProcessRuntime(prototype);
        ProtosActor root = process.rootActorForRuntime();
        TwoPhaseCustody custody = new TwoPhaseCustody(root);
        assertTrue(root.registerBootstrapCustodyForRuntime(custody));

        process.requestTerminationForRuntime();

        assertEquals(1, custody.invalidations.get());
        assertEquals(ProtosActor.LifecycleState.TERMINATING, root.lifecycleState());
        assertFalse(root.completeInitialization(new ProtosObjectValue(ProtosObjectValue.rootObject())),
                "no READY after the termination cutover");

        custody.runCleanup();
        assertEquals(ProtosActor.LifecycleState.TERMINATED, root.lifecycleState());
        assertTrue(root.currentBehavior().isEmpty());
        assertTrue(process.lifecycleState() != ProtosProcessRuntime.LifecycleState.RUNNING);
    }

    @Test
    void i158CustodyClearedByATerminalInitializationIsNeverInvalidated() {
        ProtosObjectValue prototype = actorRefPrototype();
        ProtosProcessRuntime process = new ProtosProcessRuntime(prototype);
        ProtosActor child = process.createHostedActorForRuntime(prototype);
        TwoPhaseCustody custody = new TwoPhaseCustody(child);
        assertTrue(child.registerBootstrapCustodyForRuntime(custody));
        // The Future terminalized and the initialization completed just before termination.
        child.clearBootstrapCustodyForRuntime(custody);
        assertTrue(child.completeInitialization(new ProtosObjectValue(ProtosObjectValue.rootObject())));

        child.requestTerminationForRuntime();

        assertEquals(0, custody.invalidations.get());
        assertEquals(ProtosActor.LifecycleState.TERMINATED, child.lifecycleState());
    }

    @Test
    void i158ConcurrentTerminationAndTerminalizationInvalidateAtMostOnce() throws Exception {
        ProtosObjectValue prototype = actorRefPrototype();
        ProtosProcessRuntime process = new ProtosProcessRuntime(prototype);
        ProtosActor child = process.createHostedActorForRuntime(prototype);
        TwoPhaseCustody custody = new TwoPhaseCustody(child);
        assertTrue(child.registerBootstrapCustodyForRuntime(custody));
        java.util.concurrent.CyclicBarrier start = new java.util.concurrent.CyclicBarrier(2);
        java.util.concurrent.atomic.AtomicBoolean ready = new java.util.concurrent.atomic.AtomicBoolean();

        Thread terminalizer =
                new Thread(
                        () -> {
                            await(start);
                            child.clearBootstrapCustodyForRuntime(custody);
                            ready.set(child.completeInitialization(
                                    new ProtosObjectValue(ProtosObjectValue.rootObject())));
                        });
        terminalizer.start();
        await(start);
        child.requestTerminationForRuntime();
        terminalizer.join(java.util.concurrent.TimeUnit.SECONDS.toMillis(5));
        assertFalse(terminalizer.isAlive());

        assertTrue(custody.invalidations.get() <= 1);
        if (custody.invalidations.get() == 1) {
            assertFalse(ready.get(), "an invalidated initialization never publishes READY");
            custody.runCleanup();
        }
        assertEquals(ProtosActor.LifecycleState.TERMINATED, child.lifecycleState());
    }

    private static void await(java.util.concurrent.CyclicBarrier barrier) {
        try {
            barrier.await(5, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception failure) {
            throw new AssertionError(failure);
        }
    }
}
