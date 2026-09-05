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

import com.guillermomolina.protos.execution.ProtosActorBootstrap;
import com.guillermomolina.protos.execution.ProtosCoreBootstrap;
import com.guillermomolina.protos.execution.ProtosInvocation;
import com.guillermomolina.protos.execution.ProtosModuleResolver;
import com.guillermomolina.protos.execution.ProtosModuleRuntime;
import com.guillermomolina.protos.execution.ProtosStandardActorProtocol;
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
                        return new ProtosModuleKey("canonical:" + exactSpecifier);
                    }

                    @Override
                    public String loadSource(ProtosModuleKey key) {
                        return "boot: () => { 7 }";
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
                                        new ProtosModuleKey("canonical:app"),
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
                        return new ProtosModuleKey("canonical:" + exactSpecifier);
                    }

                    @Override
                    public String loadSource(ProtosModuleKey key) {
                        return "boot: () => { {} }";
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
                        new ProtosModuleKey("creator"),
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
}
