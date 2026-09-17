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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosCoreErrors;
import com.guillermomolina.protos.runtime.ProtosFilesystemValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosStringValue;
import com.guillermomolina.protos.runtime.ProtosProcessRuntime;
import com.oracle.truffle.api.source.Source;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Host/runtime integration tests for TOOL001 Package Tool mechanics.
 *
 * <p>Repository-owned Protos semantic corpora are executed by TOOL002 through {@code protos test}.
 */
final class ProtosPackageToolFilesystemSecurityTest extends ProtosPackageToolProtosTestSupport {
    @Test
    void readOnlyMetadataBackendAllowsOnlyConfiguredProjectMetadata() throws Exception {
        Files.writeString(
                projectRoot.resolve("protos.toml"), "x", StandardCharsets.UTF_8);
        Files.writeString(
                projectRoot.resolve("secret.txt"), "secret", StandardCharsets.UTF_8);

        try (ProtosNioReadOnlyFilesystemBackend backend =
                new ProtosNioReadOnlyFilesystemBackend(
                        projectRoot, Set.of("protos.toml", "protos.lock"))) {
            assumeTrue(
                    backend.secureConfinementAvailable(),
                    "host provider has no SecureDirectoryStream");

            ProtosExecutionOutcome read =
                    executeFile(
                            TEST_ROOT.resolve("read-project-metadata.protos"),
                            readOnlyMetadataActivation(backend));
            assertEquals(
                    ProtosExecutionOutcome.State.COMPLETED,
                    read.state(),
                    "read-project-metadata.protos");
            assertEquals(
                    "x",
                    assertInstanceOf(ProtosStringValue.class, read.value()).value(),
                    "read-project-metadata.protos");

            ProtosExecutionOutcome secret =
                    execute(
                            "filesystem.open(Path.relative().child(\"secret.txt\")).value()",
                            readOnlyMetadataActivation(backend));
            assertExpected(secret, "error", "read-only metadata secret rejection");
        }
    }

    @Test
    void readOnlyMetadataBackendDoesNotFollowFinalSymlink() throws Exception {
        Path outside = Files.createTempFile("protos-package-tool-outside-", ".txt");
        try {
            Files.writeString(outside, "outside", StandardCharsets.UTF_8);
            try {
                Files.createSymbolicLink(projectRoot.resolve("protos.toml"), outside);
            } catch (UnsupportedOperationException | IOException | SecurityException unavailable) {
                assumeTrue(false, "host cannot create the symlink confinement fixture");
            }

            try (ProtosNioReadOnlyFilesystemBackend backend =
                    new ProtosNioReadOnlyFilesystemBackend(
                            projectRoot, Set.of("protos.toml"))) {
                assumeTrue(
                        backend.secureConfinementAvailable(),
                        "host provider has no SecureDirectoryStream");

                ProtosExecutionOutcome outcome =
                        execute(
                                "filesystem.open(Path.relative().child(\"protos.toml\")).value()",
                                readOnlyMetadataActivation(backend));
                assertExpected(
                        outcome,
                        "error",
                        "read-only metadata final symlink rejection");
            }
        } finally {
            Files.deleteIfExists(outside);
        }
    }

    @Test
    void readOnlyMetadataBackendFailsClosedWithoutSecureProvider() throws Exception {
        try (ProtosNioReadOnlyFilesystemBackend backend =
                new ProtosNioReadOnlyFilesystemBackend(
                        projectRoot, Set.of("protos.toml"))) {
            if (backend.secureConfinementAvailable()) {
                return;
            }

            ProtosExecutionOutcome outcome =
                    execute(
                            "filesystem.open(Path.relative().child(\"protos.toml\")).value()",
                            readOnlyMetadataActivation(backend));
            assertExpected(
                    outcome,
                    "error",
                    "read-only metadata unsupported provider");
        }
    }

}
