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

import com.guillermomolina.protos.runtime.ProtosEncodingValue;
import com.guillermomolina.protos.runtime.ProtosEnvironmentValue;
import com.guillermomolina.protos.runtime.io.ProtosFilesystemValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosProcessCapabilityValue;
import com.guillermomolina.protos.runtime.ProtosProcessRuntime;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ProtosStandaloneProcessBootstrapTest {
    private static final Path CORE = Path.of("protos", "lib", "core");

    @Test
    void standaloneEntryGetsCompleteBootstrapStateBeforeSourceExecution()
            throws Exception {
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
        ProtosEncodingValue utf8 = encoding(prelude, "UTF8");

        ProtosStandaloneProcessBootstrap.Result result =
                ProtosStandaloneProcessBootstrap.create(
                        prelude,
                        List.of("alpha", "beta"),
                        exactDomain(),
                        List.of(
                                new ProtosEnvironmentValue.NativeEntry("A", "one"),
                                new ProtosEnvironmentValue.NativeEntry("B", "two")),
                        (maxBytes, completion) -> {
                            completion.eof();
                            return () -> {};
                        },
                        (bytes, completion) -> {
                            completion.succeeded();
                            return () -> {};
                        },
                        null,
                        utf8,
                        utf8,
                        null,
                        null);

        ProtosProcessRuntime process = result.process();
        ProtosProcessCapabilityValue capability =
                assertInstanceOf(
                        ProtosProcessCapabilityValue.class,
                        result.activation()
                                .context()
                                .readLocalSlot("process")
                                .orElseThrow());

        assertSame(process, capability.processForRuntime());
        assertSame(prelude.processPrototype(), capability.representedDelegationParent(prelude));
        assertFalse(result.activation().context().hasLocalSlot("filesystem"));
        assertFalse(
                result.activation().context().hasLocalSlot("print"),
                "host-neutral standalone Process bootstrap must not install CLI conveniences");

        assertEquals(
                List.of("alpha", "beta"),
                process.argumentsSnapshotForRuntime()
                        .orElseThrow()
                        .stream()
                        .map(value -> value.value())
                        .toList());
        assertEquals(
                ProtosProcessRuntime.EnvironmentSnapshotState.AVAILABLE,
                process.environmentSnapshotStateForRuntime());
        assertTrue(process.stdinForRuntime().isPresent());
        assertTrue(process.stdoutForRuntime().isPresent());
        assertTrue(process.stderrForRuntime().isEmpty());
        assertSame(utf8, process.stdinEncodingForRuntime().orElseThrow());
        assertSame(utf8, process.stdoutEncodingForRuntime().orElseThrow());
        assertTrue(process.stderrEncodingForRuntime().isEmpty());

        assertSame(
                process.rootActorForRuntime().reference(),
                result.activation()
                        .executionDomain()
                        .currentActorReference()
                        .orElseThrow());
        assertTrue(
                prelude.actorRefPrototypeForRuntime().hasLocalSlot("send"),
                "RootActor reference must use the standard hidden ActorRef prototype");
    }

    @Test
    void optionalFilesystemGrantUsesExactCapabilityAndAbsenceIsNotNullSlot()
            throws Exception {
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
        ProtosFilesystemValue filesystem = new ProtosFilesystemValue();

        ProtosStandaloneProcessBootstrap.Result granted =
                ProtosStandaloneProcessBootstrap.create(
                        prelude,
                        List.of(),
                        exactDomain(),
                        List.of(),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        filesystem);

        assertSame(
                filesystem,
                granted.activation()
                        .context()
                        .readLocalSlot("filesystem")
                        .orElseThrow());

        ProtosStandaloneProcessBootstrap.Result absent =
                ProtosStandaloneProcessBootstrap.create(
                        prelude,
                        List.of(),
                        exactDomain(),
                        List.of(),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null);
        assertFalse(absent.activation().context().hasLocalSlot("filesystem"));
    }

    /**
     * I155 / D248-B: the preallocated marker passed as {@code defaultFilesystem} is exactly the
     * RootActor's bootstrap-local capability, and its operations are bound before any guest
     * source runs; the backend is owned by the runtime host for that Process only.
     */
    @Test
    void grantedStandaloneFilesystemMarkerIsTheBoundRootActorCapability(
            @org.junit.jupiter.api.io.TempDir Path data) throws Exception {
        ProtosPolyglotRuntimeHost runtimeHost = ProtosPolyglotRuntimeHost.open();
        try {
            ProtosStandaloneProcessBootstrap.Result granted =
                    ProtosStandaloneHostedExecution.bootstrapProcess(
                            CORE,
                            new ProtosStandardLibraryModuleResolver(CORE.getParent()),
                            List.of(),
                            ProtosStandaloneHostedExecution.readableBackend(
                                    java.io.InputStream.nullInputStream()),
                            ProtosStandaloneHostedExecution.writableBackend(
                                    java.io.OutputStream.nullOutputStream()),
                            ProtosStandaloneHostedExecution.writableBackend(
                                    java.io.OutputStream.nullOutputStream()),
                            ProtosWorkspacePackageApplicationExecution.NetworkGrant.NONE,
                            ProtosStandaloneHostedExecution.ResolverGrant.NONE,
                            ProtosStandaloneHostedExecution.FilesystemGrant.directory(data),
                            runtimeHost);
            Object slot =
                    granted.activation().context().readLocalSlot("filesystem").orElseThrow();
            ProtosFilesystemValue filesystem = assertInstanceOf(ProtosFilesystemValue.class, slot);
            assertTrue(filesystem.hasLocalSlot("open"));
            assertTrue(filesystem.hasLocalSlot("entries"));
            assertTrue(filesystem.hasLocalSlot("replace"));
            assertTrue(filesystem.hasLocalSlot("remove"));
            assertFalse(granted.activation().context().hasLocalSlot("network"));
            assertFalse(granted.activation().context().hasLocalSlot("resolver"));
            assertEquals(1, runtimeHost.processFilesystemCountForTesting());
            granted.process().requestTerminationForRuntime();
        } finally {
            runtimeHost.close();
        }
        assertEquals(0, runtimeHost.processFilesystemCountForTesting());
    }

    @Test
    void missingGrantedFilesystemRootFailsBootstrapWithoutAProcess(
            @org.junit.jupiter.api.io.TempDir Path data) throws Exception {
        try (ProtosPolyglotRuntimeHost runtimeHost = ProtosPolyglotRuntimeHost.open()) {
            assertThrows(
                    ProtosStandaloneHostedExecution.FilesystemGrantProvisioningException.class,
                    () -> ProtosStandaloneHostedExecution.bootstrapProcess(
                            CORE,
                            new ProtosStandardLibraryModuleResolver(CORE.getParent()),
                            List.of(),
                            ProtosStandaloneHostedExecution.readableBackend(
                                    java.io.InputStream.nullInputStream()),
                            ProtosStandaloneHostedExecution.writableBackend(
                                    java.io.OutputStream.nullOutputStream()),
                            ProtosStandaloneHostedExecution.writableBackend(
                                    java.io.OutputStream.nullOutputStream()),
                            ProtosWorkspacePackageApplicationExecution.NetworkGrant.NONE,
                            ProtosStandaloneHostedExecution.ResolverGrant.NONE,
                            ProtosStandaloneHostedExecution.FilesystemGrant.directory(
                                    data.resolve("missing")),
                            runtimeHost));
            assertEquals(0, runtimeHost.processFilesystemCountForTesting());
        }
    }

    @Test
    void invalidBootstrapDataRemainsStableForPublicProcessAccessors()
            throws Exception {
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
        ProtosEncodingValue utf8 = encoding(prelude, "UTF8");

        ProtosStandaloneProcessBootstrap.Result result =
                ProtosStandaloneProcessBootstrap.create(
                        prelude,
                        List.of("ok", String.valueOf((char) 0xD800)),
                        exactDomain(),
                        List.of(),
                        null,
                        null,
                        null,
                        utf8,
                        null,
                        null,
                        null);

        assertEquals(
                ProtosProcessRuntime.ArgumentsSnapshotState.UNREPRESENTABLE,
                result.process().argumentsSnapshotStateForRuntime());
        assertEquals(
                ProtosProcessRuntime.StandardStreamEncodingState.INVALID,
                result.process().stdinEncodingStateForRuntime());
    }

    private static ProtosEnvironmentValue.NativeNameDomain exactDomain() {
        return new ProtosEnvironmentValue.NativeNameDomain() {
            @Override
            public boolean sameCapturedName(String left, String right) {
                return left.equals(right);
            }

            @Override
            public boolean isQueryRepresentable(String name) {
                return !name.contains("=") && name.indexOf('\0') < 0;
            }

            @Override
            public boolean matchesQuery(String captured, String query) {
                return captured.equals(query);
            }
        };
    }

    private static ProtosEncodingValue encoding(ProtosPrelude prelude, String name) {
        return assertInstanceOf(
                ProtosEncodingValue.class,
                prelude.encodingPrototype().readLocalSlot(name).orElseThrow());
    }

    // ---- I158 / D251-A1: bootstrap locals across a real suspension ------------------------

    private static ProtosObjectValue initializeRootWithSuspension(
            String specifier,
            com.guillermomolina.protos.runtime.modules.ProtosModuleKey key,
            String source)
            throws Exception {
        ProtosI158ActorBootstrapSuspensionTest.Harness x =
                ProtosI158ActorBootstrapSuspensionTest.Harness.create(specifier, key, source);
        com.guillermomolina.protos.runtime.ProtosActor root = x.process().rootActorForRuntime();
        List<?> transferred =
                com.guillermomolina.protos.runtime.ProtosActorValueTransfer.snapshotSpawnArguments(
                        List.of(x.acquire()), x.activation());
        java.util.concurrent.CompletableFuture<Boolean> result =
                new java.util.concurrent.CompletableFuture<>();
        Thread host =
                new Thread(
                        () -> {
                            try {
                                result.complete(
                                        new ProtosActorBootstrap(x.moduleRuntime())
                                                .initialize(root, x.prelude(), key, "boot", transferred));
                            } catch (Throwable failure) {
                                result.completeExceptionally(failure);
                            }
                        },
                        "i158-root-locals");
        host.setDaemon(true);
        host.start();
        // The completion is released only after the guest is provably suspended on it.
        assertTrue(x.backend().accepted.await(5, java.util.concurrent.TimeUnit.SECONDS));
        assertFalse(result.isDone());
        assertEquals(com.guillermomolina.protos.runtime.ProtosActor.LifecycleState.INITIALIZING, root.lifecycleState());
        x.backend().pending.pollFirst().failed();
        assertTrue(result.get(5, java.util.concurrent.TimeUnit.SECONDS));
        assertEquals(0, root.executionDomain().liveTaskCount());
        ProtosObjectValue behavior = root.currentBehavior().orElseThrow();
        behavior.createLocalSlot("i158TestModule", root.moduleState().lookup(key).orElseThrow().instance());
        return behavior;
    }

    @Test
    void i158SourceBootstrapLocalKeepsItsIdentityAcrossASuspension() throws Exception {
        ProtosObjectValue behavior =
                initializeRootWithSuspension(
                        "app",
                        com.guillermomolina.protos.runtime.modules.ProtosModuleKey.source("canonical:app"),
                        """
                        firstSeen: process
                        boot: (acceptor) => {
                            Error.handle(() => { acceptor.accept().value() }, (error) => { null })
                            { before: firstSeen; after: process }
                        }
                        """);
        Object before = behavior.readLocalSlot("before").orElseThrow();
        assertInstanceOf(ProtosProcessCapabilityValue.class, before);
        assertSame(before, behavior.readLocalSlot("after").orElseThrow());
    }

    @Test
    void i158ModuleBootstrapLocalStaysPrivateAcrossASuspension() throws Exception {
        ProtosObjectValue behavior =
                initializeRootWithSuspension(
                        "mod:app",
                        com.guillermomolina.protos.runtime.modules.ProtosModuleKey.module("canonical:mod:app"),
                        """
                        firstSeen: process
                        this.boot: (acceptor) => {
                            Error.handle(() => { acceptor.accept().value() }, (error) => { null })
                            { before: firstSeen; after: process }
                        }
                        """);
        assertSame(
                behavior.readLocalSlot("before").orElseThrow(),
                behavior.readLocalSlot("after").orElseThrow());
        ProtosObjectValue publicModule = (ProtosObjectValue) behavior.readLocalSlot("i158TestModule").orElseThrow();
        assertFalse(publicModule.hasLocalSlot("process"));
        assertFalse(publicModule.hasLocalSlot("firstSeen"));
    }
}
