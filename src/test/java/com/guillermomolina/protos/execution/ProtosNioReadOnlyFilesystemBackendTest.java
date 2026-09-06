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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosFilesystemValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.guillermomolina.protos.runtime.ProtosStringValue;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ProtosNioReadOnlyFilesystemBackendTest {
    private static final Path CORE = Path.of("protos", "lib", "core");
    private static final Path READ_FIXTURE =
            Path.of("protos", "tests", "package-tool", "read-project-metadata.protos");

    @TempDir Path authorityRoot;

    @Test
    void bundledToolStyleProtosCodeReadsOnlyTheAllowedProjectMetadataName() throws Exception {
        Files.writeString(authorityRoot.resolve("protos.toml"), "x", StandardCharsets.UTF_8);
        Files.writeString(authorityRoot.resolve("secret.txt"), "secret", StandardCharsets.UTF_8);

        try (ProtosNioReadOnlyFilesystemBackend backend =
                new ProtosNioReadOnlyFilesystemBackend(
                        authorityRoot, Set.of("protos.toml", "protos.lock"))) {
            assumeTrue(
                    backend.secureConfinementAvailable(),
                    "host provider has no SecureDirectoryStream");

            Fixture fixture = fixture(backend);
            String source = Files.readString(READ_FIXTURE, StandardCharsets.UTF_8);
            Object result =
                    new ProtosSourceCompiler().compile(source).call(fixture.activation());

            assertEquals("x", assertInstanceOf(ProtosStringValue.class, result).value());

            assertThrows(
                    ProtosSignalException.class,
                    () ->
                            new ProtosSourceCompiler()
                                    .compile(
                                            "filesystem.open("
                                                    + "Path.relative().child(\"secret.txt\"))"
                                                    + ".value()")
                                    .call(fixture.activation()));
        }
    }

    @Test
    void finalSymlinkCannotRedirectAnAllowedMetadataNameOutsideAuthority() throws Exception {
        Path outside = Files.createTempFile("protos-package-tool-outside-", ".txt");
        try {
            Files.writeString(outside, "outside", StandardCharsets.UTF_8);
            try {
                Files.createSymbolicLink(authorityRoot.resolve("protos.toml"), outside);
            } catch (UnsupportedOperationException | IOException | SecurityException unavailable) {
                assumeTrue(false, "host cannot create the symlink confinement fixture");
            }

            try (ProtosNioReadOnlyFilesystemBackend backend =
                    new ProtosNioReadOnlyFilesystemBackend(
                            authorityRoot, Set.of("protos.toml"))) {
                assumeTrue(
                        backend.secureConfinementAvailable(),
                        "host provider has no SecureDirectoryStream");

                Fixture fixture = fixture(backend);
                assertThrows(
                        ProtosSignalException.class,
                        () ->
                                new ProtosSourceCompiler()
                                        .compile(
                                                "filesystem.open("
                                                        + "Path.relative().child(\"protos.toml\"))"
                                                        + ".value()")
                                        .call(fixture.activation()));
            }
        } finally {
            Files.deleteIfExists(outside);
        }
    }

    @Test
    void unsupportedHostProviderFailsClosedRatherThanFallingBackToAmbientPaths()
            throws Exception {
        try (ProtosNioReadOnlyFilesystemBackend backend =
                new ProtosNioReadOnlyFilesystemBackend(
                        authorityRoot, Set.of("protos.toml"))) {
            if (backend.secureConfinementAvailable()) {
                return;
            }

            Fixture fixture = fixture(backend);
            assertThrows(
                    ProtosSignalException.class,
                    () ->
                            new ProtosSourceCompiler()
                                    .compile(
                                            "filesystem.open("
                                                    + "Path.relative().child(\"protos.toml\"))"
                                                    + ".value()")
                                    .call(fixture.activation()));
        }
    }

    private static Fixture fixture(ProtosNioReadOnlyFilesystemBackend backend) throws Exception {
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
        ProtosActivation activation = prelude.newModuleActivation();
        ProtosObjectValue rawFilesystem =
                ProtosStandardFilesystemProtocol.createCapability(
                        prelude.bytesPrototypeForRuntime(), activation, backend);
        ProtosFilesystemValue filesystem =
                assertInstanceOf(ProtosFilesystemValue.class, rawFilesystem);
        activation.context().createLocalSlot("filesystem", filesystem);
        return new Fixture(activation);
    }

    private record Fixture(ProtosActivation activation) {}
}
