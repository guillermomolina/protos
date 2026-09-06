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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosCoreErrors;
import com.guillermomolina.protos.runtime.ProtosFilesystemValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ProtosPackageToolMetadataPublicationTest {
    private static final Path CORE = Path.of("protos", "lib", "core");
    private static final Path STANDARD_LIBRARY = Path.of("protos", "lib");
    private static final Path TOOL_ROOT = Path.of("protos", "tools", "package");
    private static final Path CASE_ROOT =
            Path.of("protos", "tests", "package-tool", "metadata-publication");

    private static final Set<String> READABLE = Set.of("protos.toml", "protos.lock");
    private static final Set<String> WRITABLE =
            Set.of(".protos.toml.stage", ".protos.lock.stage");
    private static final Set<String> MUTABLE =
            Set.of("protos.toml", "protos.lock", ".protos.toml.stage", ".protos.lock.stage");

    @TempDir Path projectRoot;

    @Test
    void protosToolStagesWritesAndAtomicallyPublishesMetadata() throws Exception {
        Files.writeString(projectRoot.resolve("protos.toml"), "old\n", StandardCharsets.UTF_8);

        try (Fixture fixture = fixture()) {
            assertSame(ProtosBooleanValue.TRUE, fixture.execute("publish-replaces-target.protos"));
        }

        assertEquals(
                "new metadata\n",
                Files.readString(projectRoot.resolve("protos.toml"), StandardCharsets.UTF_8));
        assertTrue(
                Files.notExists(
                        projectRoot.resolve(".protos.toml.stage"), LinkOption.NOFOLLOW_LINKS));
    }

    @Test
    void protosToolPublishesLockMetadataThroughTheSameConfinedPath() throws Exception {
        Files.writeString(projectRoot.resolve("protos.lock"), "old lock\n", StandardCharsets.UTF_8);

        try (Fixture fixture = fixture()) {
            assertSame(ProtosBooleanValue.TRUE, fixture.execute("publish-lock-replaces-target.protos"));
        }

        assertEquals(
                "new lock metadata\n",
                Files.readString(projectRoot.resolve("protos.lock"), StandardCharsets.UTF_8));
        assertTrue(
                Files.notExists(
                        projectRoot.resolve(".protos.lock.stage"), LinkOption.NOFOLLOW_LINKS));
    }

    @Test
    void createNewStagingCollisionFailsWithoutTouchingTarget() throws Exception {
        Files.writeString(projectRoot.resolve("protos.toml"), "old\n", StandardCharsets.UTF_8);
        Files.writeString(
                projectRoot.resolve(".protos.toml.stage"), "stale\n", StandardCharsets.UTF_8);

        try (Fixture fixture = fixture()) {
            ProtosSignalException signal =
                    assertThrows(
                            ProtosSignalException.class,
                            () -> fixture.execute("stage-collision-preserves-target.protos"));
            assertSame(
                    ProtosCoreErrors.prototype(
                            fixture.activation(), ProtosCoreErrors.StandardError.I_O_ERROR),
                    signal.error().parent().orElseThrow());
        }

        assertEquals(
                "old\n",
                Files.readString(projectRoot.resolve("protos.toml"), StandardCharsets.UTF_8));
        assertEquals(
                "stale\n",
                Files.readString(
                        projectRoot.resolve(".protos.toml.stage"), StandardCharsets.UTF_8));
    }

    @Test
    void explicitDiscardUsesFilesystemRemoveForAnAbandonedStage() throws Exception {
        Files.writeString(
                projectRoot.resolve(".protos.toml.stage"), "abandoned\n", StandardCharsets.UTF_8);

        try (Fixture fixture = fixture()) {
            assertSame(ProtosBooleanValue.TRUE, fixture.execute("discard-staging.protos"));
        }

        assertTrue(
                Files.notExists(
                        projectRoot.resolve(".protos.toml.stage"), LinkOption.NOFOLLOW_LINKS));
    }

    @Test
    void invalidContentFailsBeforeCreatingAStage() throws Exception {
        Files.writeString(projectRoot.resolve("protos.toml"), "old\n", StandardCharsets.UTF_8);

        try (Fixture fixture = fixture()) {
            assertThrows(
                    ProtosSignalException.class,
                    () -> fixture.execute("invalid-content-has-no-stage.protos"));
        }

        assertFalse(
                Files.exists(
                        projectRoot.resolve(".protos.toml.stage"), LinkOption.NOFOLLOW_LINKS));
        assertEquals(
                "old\n",
                Files.readString(projectRoot.resolve("protos.toml"), StandardCharsets.UTF_8));
    }

    @Test
    void forbiddenTargetLeavesPreparedStageAndUnrelatedTargetUntouched() throws Exception {
        Files.writeString(projectRoot.resolve("secret.txt"), "secret\n", StandardCharsets.UTF_8);

        try (Fixture fixture = fixture()) {
            ProtosSignalException signal =
                    assertThrows(
                            ProtosSignalException.class,
                            () -> fixture.execute("forbidden-target-preserves-target.protos"));
            assertSame(
                    ProtosCoreErrors.prototype(
                            fixture.activation(), ProtosCoreErrors.StandardError.I_O_ERROR),
                    signal.error().parent().orElseThrow());
        }

        assertEquals(
                "secret\n",
                Files.readString(projectRoot.resolve("secret.txt"), StandardCharsets.UTF_8));
        assertEquals(
                "staged but forbidden\n",
                Files.readString(
                        projectRoot.resolve(".protos.toml.stage"), StandardCharsets.UTF_8));
    }

    private Fixture fixture() throws Exception {
        ProtosStandardLibraryModuleResolver standard =
                new ProtosStandardLibraryModuleResolver(STANDARD_LIBRARY);
        ProtosBundledToolModuleResolver resolver =
                new ProtosBundledToolModuleResolver("package", TOOL_ROOT, standard);
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE, resolver);
        ProtosActivation activation = prelude.newModuleActivation();
        ProtosNioConfinedFilesystemBackend backend =
                new ProtosNioConfinedFilesystemBackend(projectRoot, READABLE, WRITABLE, MUTABLE);
        if (!backend.secureNamespaceConfinementAvailable()) {
            backend.close();
            assumeTrue(false, "host provider has no SecureDirectoryStream");
        }

        ProtosObjectValue rawFilesystem =
                ProtosStandardFilesystemProtocol.createCapability(
                        prelude.bytesPrototypeForRuntime(), activation, backend);
        ProtosFilesystemValue filesystem =
                assertInstanceOf(ProtosFilesystemValue.class, rawFilesystem);
        activation.context().createLocalSlot("filesystem", filesystem);
        return new Fixture(activation, backend);
    }

    private static final class Fixture implements AutoCloseable {
        private final ProtosActivation activation;
        private final ProtosNioConfinedFilesystemBackend backend;

        Fixture(ProtosActivation activation, ProtosNioConfinedFilesystemBackend backend) {
            this.activation = activation;
            this.backend = backend;
        }

        ProtosActivation activation() {
            return activation;
        }

        Object execute(String fixture) throws Exception {
            String source =
                    Files.readString(CASE_ROOT.resolve(fixture), StandardCharsets.UTF_8);
            return new ProtosSourceCompiler().compile(source).call(activation);
        }

        @Override
        public void close() throws Exception {
            backend.close();
        }
    }
}
