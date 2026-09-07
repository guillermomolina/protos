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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

final class ProtosPackageToolLockFileTest {
    private static final Path CORE = Path.of("protos", "lib", "core");
    private static final Path STANDARD_LIBRARY = Path.of("protos", "lib");
    private static final Path TOOL_ROOT = Path.of("protos", "tools", "package");
    private static final Path CASE_ROOT =
            Path.of("protos", "tests", "package-tool", "lock-file");

    private static final Set<String> READABLE = Set.of("protos.toml", "protos.lock");
    private static final Set<String> WRITABLE =
            Set.of(".protos.toml.stage", ".protos.lock.stage");
    private static final Set<String> MUTABLE =
            Set.of("protos.toml", "protos.lock", ".protos.toml.stage", ".protos.lock.stage");

    private static final String CANONICAL_LOCK =
            "lock-format 1\n"
                    + "resolver-version 1\n"
                    + "resolution-input protos-resolution-input-v1 sha256:0123456789abcdef\n"
                    + "\n"
                    + "root workspace \"root\"\n"
                    + "registry-node registry \"pkg\" \"1.0.0\" locator \"pkg\" authority \"public\" content protos-package-tree-v1 sha256:aaaa\n"
                    + "dependency workspace \"root\" alias \"dep\" target registry \"pkg\" \"1.0.0\"\n";

    private static final String NONCANONICAL_LOCK =
            "lock-format 1\n"
                    + "resolver-version 1\n"
                    + "resolution-input protos-resolution-input-v1 sha256:0123456789abcdef\n"
                    + "\n"
                    + "dependency workspace \"root\" alias \"dep\" target registry \"pkg\" \"1.0.0\"\n"
                    + "registry-node registry \"pkg\" \"1.0.0\" locator \"pkg\" authority \"public\" content protos-package-tree-v1 sha256:aaaa\n"
                    + "root workspace \"root\"\n";

    @TempDir Path projectRoot;

    @Test
    void loadsCanonicalLockThroughConfinedFilesystem() throws Exception {
        Files.writeString(projectRoot.resolve("protos.lock"), CANONICAL_LOCK, StandardCharsets.UTF_8);

        try (Fixture fixture = fixture()) {
            assertSame(ProtosBooleanValue.TRUE, fixture.execute("load-canonical.protos"));
        }
    }

    @Test
    void nonCanonicalLockFailsClosedOnLoad() throws Exception {
        Files.writeString(
                projectRoot.resolve("protos.lock"), NONCANONICAL_LOCK, StandardCharsets.UTF_8);

        try (Fixture fixture = fixture()) {
            assertThrows(
                    ProtosSignalException.class,
                    () -> fixture.execute("load-noncanonical-error.protos"));
        }
    }

    @Test
    void missingLockUsesOrdinaryIoFailure() throws Exception {
        try (Fixture fixture = fixture()) {
            ProtosSignalException signal =
                    assertThrows(
                            ProtosSignalException.class,
                            () -> fixture.execute("load-missing-error.protos"));
            assertSame(
                    ProtosCoreErrors.prototype(
                            fixture.activation(), ProtosCoreErrors.StandardError.I_O_ERROR),
                    signal.error().parent().orElseThrow());
        }
    }

    @Test
    void publishesCanonicalBytesThroughExistingMetadataTransaction() throws Exception {
        Files.writeString(projectRoot.resolve("protos.lock"), "old\n", StandardCharsets.UTF_8);

        try (Fixture fixture = fixture()) {
            assertSame(ProtosBooleanValue.TRUE, fixture.execute("publish-canonical.protos"));
        }

        assertEquals(CANONICAL_LOCK, Files.readString(projectRoot.resolve("protos.lock")));
        assertTrue(
                Files.notExists(
                        projectRoot.resolve(".protos.lock.stage"), LinkOption.NOFOLLOW_LINKS));
    }

    @Test
    void invalidModelFailsBeforeCreatingStageOrChangingTarget() throws Exception {
        Files.writeString(projectRoot.resolve("protos.lock"), "old\n", StandardCharsets.UTF_8);

        try (Fixture fixture = fixture()) {
            assertThrows(
                    ProtosSignalException.class,
                    () -> fixture.execute("publish-invalid-no-stage.protos"));
        }

        assertEquals("old\n", Files.readString(projectRoot.resolve("protos.lock")));
        assertFalse(
                Files.exists(
                        projectRoot.resolve(".protos.lock.stage"), LinkOption.NOFOLLOW_LINKS));
    }

    @Test
    void stagingCollisionPreservesExistingLockAndStage() throws Exception {
        Files.writeString(projectRoot.resolve("protos.lock"), "old\n", StandardCharsets.UTF_8);
        Files.writeString(
                projectRoot.resolve(".protos.lock.stage"),
                "stale\n",
                StandardCharsets.UTF_8);

        try (Fixture fixture = fixture()) {
            ProtosSignalException signal =
                    assertThrows(
                            ProtosSignalException.class,
                            () -> fixture.execute("publish-stage-collision.protos"));
            assertSame(
                    ProtosCoreErrors.prototype(
                            fixture.activation(), ProtosCoreErrors.StandardError.I_O_ERROR),
                    signal.error().parent().orElseThrow());
        }

        assertEquals("old\n", Files.readString(projectRoot.resolve("protos.lock")));
        assertEquals(
                "stale\n",
                Files.readString(projectRoot.resolve(".protos.lock.stage")));
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
