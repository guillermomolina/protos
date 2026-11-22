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

import com.guillermomolina.protos.execution.ProtosStandaloneHostedExecution.ClockGrant;
import com.guillermomolina.protos.execution.ProtosStandaloneHostedExecution.EntropyGrant;
import com.guillermomolina.protos.execution.ProtosStandaloneHostedExecution.ExecutionGrant;
import com.guillermomolina.protos.execution.ProtosStandaloneHostedExecution.ResolverGrant;
import com.guillermomolina.protos.execution.ProtosWorkspacePackageApplicationExecution.NetworkGrant;
import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosActorExecutionDomain;
import com.guillermomolina.protos.runtime.ProtosExternalChildValue;
import com.guillermomolina.protos.runtime.ProtosExternalExecutionCapabilityValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosProcessRuntime;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.io.IOAccess;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * Authority boundary of standalone execution (EXTERNAL_PROCESS.md §§1, 8-9): the grant is
 * independent of every other authority, absent by default, never inherited by spawned Actors,
 * delegated only explicitly as a fresh Actor-local view, never enters P, and a
 * Process termination reclaims live children before the Process Context closes.
 */
@Timeout(value = 30, unit = TimeUnit.SECONDS)
final class ProtosI166ExternalExecutionAuthorityTest {
    private static final Path CORE = Path.of("protos", "lib", "core");

    private static final String SLOTS =
            """
            hasExecution: context.hasSlot("execution")
            hasClock: context.hasSlot("clock")
            hasEntropy: context.hasSlot("entropy")
            hasNetwork: context.hasSlot("network")
            hasResolver: context.hasSlot("resolver")
            hasFilesystem: context.hasSlot("filesystem")
            """;

    private static ProtosStandaloneHostedSession open(
            Path directory, String source, EntropyGrant entropy, ClockGrant clock,
            ExecutionGrant execution) throws IOException {
        Path entry = directory.resolve("i166-authority.protos");
        Files.writeString(entry, ProtosI166ExternalExecutionLaunchTest.DRAIN + SLOTS + source + "\nnull\n");
        return ProtosStandaloneHostedSession.open(
                CORE, entry, List.of(), InputStream.nullInputStream(),
                OutputStream.nullOutputStream(), OutputStream.nullOutputStream(),
                NetworkGrant.NONE, ResolverGrant.NONE, entropy, clock, execution);
    }

    @Test
    void executionGrantsNothingElse(@TempDir Path directory) throws IOException {
        try (ProtosStandaloneHostedSession session = open(directory, """
                probe: () => {
                    hasExecution && !hasClock && !hasEntropy && !hasNetwork &&
                        !hasResolver && !hasFilesystem
                }
                """, EntropyGrant.NONE, ClockGrant.NONE, ExecutionGrant.HOST_EXECUTION)) {
            assertSame(Boolean.TRUE, ProtosI166ExternalExecutionLaunchTest.invoke(session, "probe"));
        }
    }

    @Test
    void otherGrantsNeverImplyExecution(@TempDir Path directory) throws IOException {
        try (ProtosStandaloneHostedSession session = open(directory, """
                probe: () => { hasClock && hasEntropy && !hasExecution }
                """, EntropyGrant.HOST_ENTROPY, ClockGrant.HOST_CLOCK, ExecutionGrant.NONE)) {
            assertSame(Boolean.TRUE, ProtosI166ExternalExecutionLaunchTest.invoke(session, "probe"));
        }
    }

    @Test
    void legacyOverloadsKeepDenial(@TempDir Path directory) throws IOException {
        Path entry = directory.resolve("legacy.protos");
        Files.writeString(entry, "had: context.hasSlot(\"execution\")\nprobe: () => { had }\nnull\n");
        try (ProtosStandaloneHostedSession session = ProtosStandaloneHostedSession.open(
                CORE, entry, List.of(), InputStream.nullInputStream(),
                OutputStream.nullOutputStream(), OutputStream.nullOutputStream(),
                NetworkGrant.NONE, ResolverGrant.NONE, EntropyGrant.NONE, ClockGrant.HOST_CLOCK)) {
            assertSame(Boolean.FALSE, ProtosI166ExternalExecutionLaunchTest.invoke(session, "probe"));
        }
    }

    @Test
    void grantChildrenAndEndpointsNeverEnterParallelExecution(@TempDir Path directory)
            throws IOException {
        ProtosI166ExternalExecutionLaunchTest.assumeProgram("/bin/cat");
        try (ProtosStandaloneHostedSession session = open(directory, """
                denied: (value) => {
                    NonParallelValue.handle(() => { ((x) => { x }).parallel(value).value(); false },
                            (error) => { true })
                }
                probe: () => {
                    child: execution.start("/bin/cat", [], Map(), null).value()
                    result: denied(execution) && denied(child) && denied(child.stdin()) &&
                            denied(child.stdout()) && denied(child.stderr())
                    child.stdin().close().value()
                    result
                }
                """, EntropyGrant.NONE, ClockGrant.NONE, ExecutionGrant.HOST_EXECUTION)) {
            assertSame(Boolean.TRUE, ProtosI166ExternalExecutionLaunchTest.invoke(session, "probe"));
        }
    }

    @Test
    void processTerminationReclaimsLiveChildrenBeforeContextClose(@TempDir Path directory)
            throws IOException {
        ProtosI166ExternalExecutionLaunchTest.assumeProgram("/bin/sleep");
        // The session close must not fail although the child would otherwise outlive it: the
        // live child is terminated and reaped within the bounded host close.
        try (ProtosStandaloneHostedSession session = open(directory, """
                probe: () => {
                    execution.start("/bin/sleep", ["30"], Map(), null).value()
                    true
                }
                """, EntropyGrant.NONE, ClockGrant.NONE, ExecutionGrant.HOST_EXECUTION)) {
            assertSame(Boolean.TRUE, ProtosI166ExternalExecutionLaunchTest.invoke(session, "probe"));
        }
    }

    /** Worker module loaded by spawned Actors; the RootActor's grant is never inherited. */
    private static final String WORKER =
            ProtosI166ExternalExecutionLaunchTest.DRAIN
                    + """
                    held: null
                    denied: (value) => {
                        NonParallelValue.handle(() => { ((x) => { x }).parallel(value).value(); false },
                                (error) => { true })
                    }
                    worker: () => {
                        behavior: {
                            inherited: () => { context.hasSlot("execution") }
                            run: (grant) => {
                                drain(grant.start("/bin/echo", ["delegated"], Map(), null).value().stdout())
                            }
                            hold: (grant) => { held = grant; true }
                            runHeld: () => {
                                drain(held.start("/bin/echo", ["held"], Map(), null).value().stdout())
                            }
                            sameAsHeld: (grant) => { grant === held }
                            aliases: (pair) => { pair[0] === pair[1] }
                            relativeDenied: (grant) => {
                                IOError.handle(() => {
                                    grant.start("/bin/echo", [], Map(), "relative").value()
                                    false
                                }, (error) => { true })
                            }
                            parallelDenied: (grant) => { denied(grant) }
                            accept: (value) => { true }
                        }
                        behavior
                    }
                    withGrant: (grant) => {
                        behavior: {
                            run: () => {
                                drain(grant.start("/bin/echo", ["spawned"], Map(), null).value().stdout())
                            }
                        }
                        behavior
                    }
                    null
                    """;

    private static ProtosStandaloneHostedSession openWithWorker(Path directory, String source)
            throws IOException {
        Files.writeString(directory.resolve("i166-worker.protos"), WORKER);
        return open(directory, source, EntropyGrant.NONE, ClockGrant.NONE,
                ExecutionGrant.HOST_EXECUTION);
    }

    @Test
    void grantIsDelegatedOnlyExplicitlyThroughMessages(@TempDir Path directory)
            throws IOException {
        ProtosI166ExternalExecutionLaunchTest.assumeProgram("/bin/echo");
        try (ProtosStandaloneHostedSession session = openWithWorker(directory, """
                probe: () => {
                    worker: Actor.spawn("./i166-worker.protos", "worker")
                    inherited: worker.request("inherited").value()
                    delegated: worker.request("run", execution).value()
                    worker.request("hold", execution).value()
                    held: worker.request("runHeld").value()
                    // One fresh Actor-local view per delivery; aliases inside one graph stay aliases.
                    fresh: !worker.request("sameAsHeld", execution).value()
                    aliased: worker.request("aliases", [execution, execution]).value()
                    // The view carries the same policy, never a wider one, and never enters P.
                    policy: worker.request("relativeDenied", execution).value()
                    parallel: worker.request("parallelDenied", execution).value()
                    worker.stop()
                    !inherited && (delegated == "delegated\\n") && (held == "held\\n") &&
                        fresh && aliased && policy && parallel
                }
                """)) {
            assertSame(Boolean.TRUE, ProtosI166ExternalExecutionLaunchTest.invoke(session, "probe"));
        }
    }

    @Test
    void grantIsDelegatedThroughExplicitSpawnInitialization(@TempDir Path directory)
            throws IOException {
        ProtosI166ExternalExecutionLaunchTest.assumeProgram("/bin/echo");
        try (ProtosStandaloneHostedSession session = openWithWorker(directory, """
                probe: () => {
                    first: Actor.spawn("./i166-worker.protos", "withGrant", execution)
                    second: Actor.spawn("./i166-worker.protos", "withGrant", execution)
                    plain: Actor.spawn("./i166-worker.protos", "worker")
                    a: first.request("run").value()
                    b: second.request("run").value()
                    inherited: plain.request("inherited").value()
                    first.stop()
                    second.stop()
                    plain.stop()
                    (a == "spawned\\n") && (b == "spawned\\n") && !inherited
                }
                """)) {
            assertSame(Boolean.TRUE, ProtosI166ExternalExecutionLaunchTest.invoke(session, "probe"));
        }
    }

    @Test
    void childrenAndEndpointsNeverCrossActors(@TempDir Path directory) throws IOException {
        ProtosI166ExternalExecutionLaunchTest.assumeProgram("/bin/cat");
        try (ProtosStandaloneHostedSession session = openWithWorker(directory, """
                refused: (worker, value) => {
                    NonTransferableValue.handle(() => { worker.request("accept", value).value(); false },
                            (error) => { true })
                }
                probe: () => {
                    worker: Actor.spawn("./i166-worker.protos", "worker")
                    child: execution.start("/bin/cat", [], Map(), null).value()
                    result: refused(worker, child) && refused(worker, child.stdin()) &&
                            refused(worker, child.stdout()) && refused(worker, child.stderr()) &&
                            refused(worker, [execution, child])
                    child.stdin().close().value()
                    drain(child.stdout())
                    worker.stop()
                    result
                }
                """)) {
            assertSame(Boolean.TRUE, ProtosI166ExternalExecutionLaunchTest.invoke(session, "probe"));
        }
    }

    @Test
    void unselectedRuntimeHostProvisionsNothing() throws IOException {
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
        try (ProtosPolyglotRuntimeHost host = ProtosPolyglotRuntimeHost.open()) {
            ProtosExternalExecutionCapabilityValue grant = host.provisionHostExecution(
                    prelude, ProtosStandaloneHostedExecution.HOST_ENVIRONMENT_NAME_DOMAIN);
            // Provisioning binds no launcher, creates no worker and is not owned by any Process.
            assertEquals(null, grant.launcherForRuntime());
            assertFalse(grant.executorInitializedForTesting());
            assertTrue(grant.ownerForRuntime().isEmpty());
            host.close();
            assertTrue(grant.retiredForRuntime());
        }
    }

    private static Context grantedEmbedding() {
        return Context.newBuilder(ProtosLanguage.ID)
                .option("protos.CoreRoot", CORE.toAbsolutePath().toString())
                .option("protos.AllowExecution", "true")
                .allowCreateProcess(true)
                .allowIO(IOAccess.ALL)
                .build();
    }

    private static ProtosProcessRuntime embeddedProcess(Context context) {
        context.eval(ProtosLanguage.ID, "0");
        context.enter();
        try {
            return ProtosLanguageContext.current().embeddedProcessOrNull().processForTesting();
        } finally {
            context.leave();
        }
    }

    /** A view of {@code grant}'s authority for {@code destination}, as an Actor transfer makes. */
    private static ProtosExternalExecutionCapabilityValue delegatedView(
            ProtosExternalExecutionCapabilityValue grant, ProtosActorExecutionDomain destination)
            throws ReflectiveOperationException {
        Field authorityField = ProtosExternalExecutionCapabilityValue.class.getDeclaredField("authority");
        authorityField.setAccessible(true);
        Object authority = authorityField.get(grant);
        Constructor<ProtosExternalExecutionCapabilityValue> constructor =
                ProtosExternalExecutionCapabilityValue.class.getDeclaredConstructor(
                        authority.getClass(), ProtosActorExecutionDomain.class);
        constructor.setAccessible(true);
        return constructor.newInstance(authority, destination);
    }

    @Test
    void initialGrantIsBoundOnceAndOnlyToItsRootActorDomain() throws IOException {
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
        ProtosActorExecutionDomain other = prelude.newModuleActivation().executionDomain();
        try (Context context = grantedEmbedding()) {
            ProtosProcessRuntime process = embeddedProcess(context);
            ProtosExternalExecutionCapabilityValue grant = process.rootExecutionForRuntime().orElseThrow();
            ProtosActorExecutionDomain root = process.rootActorForRuntime().executionDomain();
            assertNotSame(root, other);
            assertTrue(grant.usableFromForRuntime(root));
            assertFalse(grant.usableFromForRuntime(other));
            assertFalse(grant.usableFromForRuntime(null));
            // Rebinding the same RootActor is a no-op; another domain is refused for good.
            grant.bindRootActorForRuntime(process, root);
            assertThrows(IllegalStateException.class, () -> grant.bindRootActorForRuntime(process, other));
            assertTrue(grant.usableFromForRuntime(root));
            assertFalse(grant.usableFromForRuntime(other));
            // A foreign Actor cannot even begin a delegation of the initial view.
            assertThrows(IllegalArgumentException.class,
                    () -> grant.prepareDelegationForRuntime(prelude.newModuleActivation()));
        }
    }

    @Test
    void unboundGrantIsUsableFromNoDomain() throws IOException {
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
        ProtosActorExecutionDomain domain = prelude.newModuleActivation().executionDomain();
        try (ProtosPolyglotRuntimeHost host = ProtosPolyglotRuntimeHost.open();
                Context context = grantedEmbedding()) {
            ProtosExternalExecutionCapabilityValue grant = host.provisionHostExecution(
                    prelude, ProtosStandaloneHostedExecution.HOST_ENVIRONMENT_NAME_DOMAIN);
            assertFalse(grant.usableFromForRuntime(domain));
            // Not owned by that Process: it can never become its RootActor grant.
            ProtosProcessRuntime process = embeddedProcess(context);
            assertThrows(IllegalStateException.class,
                    () -> grant.bindRootActorForRuntime(process, domain));
            assertFalse(grant.usableFromForRuntime(domain));
            host.close();
        }
    }

    @Test
    void delegatedViewIsUsableOnlyByItsDestinationAndSharesRevocation() throws Exception {
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
        ProtosActorExecutionDomain destination = prelude.newModuleActivation().executionDomain();
        ProtosActorExecutionDomain third = prelude.newModuleActivation().executionDomain();
        assertNotSame(destination, third);
        try (Context context = grantedEmbedding()) {
            ProtosProcessRuntime process = embeddedProcess(context);
            ProtosExternalExecutionCapabilityValue grant = process.rootExecutionForRuntime().orElseThrow();
            ProtosActorExecutionDomain sender = process.rootActorForRuntime().executionDomain();
            ProtosExternalExecutionCapabilityValue view = delegatedView(grant, destination);
            assertTrue(view.delegatedForRuntime());
            assertTrue(view.sameAuthorityForRuntime(grant));
            assertTrue(view.usableFromForRuntime(destination));
            assertFalse(view.usableFromForRuntime(sender));
            assertFalse(view.usableFromForRuntime(third));
            // A view can never become a RootActor grant, so it never gains another owner.
            assertThrows(IllegalStateException.class,
                    () -> view.bindRootActorForRuntime(process, destination));
            // One revocation retires every view at once; no view may begin a launch.
            assertTrue(view.beginLaunchForRuntime());
            view.endLaunchForRuntime();
            grant.retireForRuntime();
            assertTrue(view.retiredForRuntime());
            assertFalse(view.beginLaunchForRuntime());
            assertFalse(grant.beginLaunchForRuntime());
            assertEquals(null, view.reserveChildForRuntime());
        }
    }

    /** Inert native child stand-in: already reaped, no transport, every release confirmed. */
    private static ProtosExternalChildValue.NativeChild reapedNativeChild() {
        return new ProtosExternalChildValue.NativeChild() {
            @Override
            public com.guillermomolina.protos.runtime.ProtosByteIoFlow.Backend lane(
                    ProtosExternalChildValue.Stream stream) {
                throw new IllegalStateException("no transport");
            }

            @Override
            public CompletionStage<Boolean> release(
                    ProtosExternalChildValue.Stream stream, boolean discard) {
                return CompletableFuture.completedFuture(true);
            }

            @Override
            public CompletionStage<ProtosExternalChildValue.TerminalOutcome> termination() {
                return CompletableFuture.completedFuture(
                        ProtosExternalChildValue.TerminalOutcome.unknown());
            }

            @Override
            public CompletionStage<Void> transportRetired() {
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public void requestTermination() {}

            @Override
            public void requestForcedTermination() {}
        };
    }

    /**
     * A child is owned by the Actor domain it was published into. A grant delegated to another
     * Actor creates children owned by that Actor only and confers no control over the children of
     * the origin; a child reference held outside the Actor protocol, by Java identity, is still
     * refused from every other domain, and a child of another Process's grant is never owned by
     * this Process's domains.
     */
    @Test
    void childrenBelongOnlyToTheirPublishingActorDomain() throws Exception {
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
        // Two distinct Actor execution domains: the origin that holds the grant and a destination.
        ProtosActivation origin = prelude.newModuleActivation();
        ProtosActivation destination = prelude.newModuleActivation();
        try (Context context = grantedEmbedding(); Context otherContext = grantedEmbedding()) {
            ProtosProcessRuntime process = embeddedProcess(context);
            ProtosExternalExecutionCapabilityValue grant = process.rootExecutionForRuntime().orElseThrow();
            ProtosExternalExecutionCapabilityValue view =
                    delegatedView(grant, destination.executionDomain());
            ProtosExternalChildValue originChild = grant.newChildForRuntime(
                    process, reapedNativeChild(), null, origin.executionDomain());
            ProtosExternalChildValue delegatedChild = view.newChildForRuntime(
                    process, reapedNativeChild(), null, destination.executionDomain());
            assertTrue(originChild.ownedByForRuntime(origin));
            assertFalse(originChild.ownedByForRuntime(destination));
            assertTrue(delegatedChild.ownedByForRuntime(destination));
            assertFalse(delegatedChild.ownedByForRuntime(origin));

            ProtosProcessRuntime otherProcess = embeddedProcess(otherContext);
            ProtosExternalChildValue foreign = otherProcess.rootExecutionForRuntime().orElseThrow()
                    .newChildForRuntime(otherProcess, reapedNativeChild(), null,
                            otherProcess.rootActorForRuntime().executionDomain());
            assertFalse(foreign.ownedByForRuntime(origin));
            assertFalse(foreign.ownedByForRuntime(destination));
            assertNotSame(foreign.ownerForRuntime(), process);
        }
    }
}
