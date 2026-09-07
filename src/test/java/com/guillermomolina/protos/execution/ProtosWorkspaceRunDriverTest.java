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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.guillermomolina.protos.runtime.ProtosEnvironmentValue;
import com.guillermomolina.protos.runtime.ProtosIntegerValue;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ProtosWorkspaceRunDriverTest {
    private static final Path CORE = Path.of("protos", "lib", "core");
    private static final Path STANDARD_LIBRARY = Path.of("protos", "lib");
    private static final Path TOOL_ROOT = Path.of("protos", "tools", "package");
    private static final Path WORKSPACE_CASE =
            Path.of(
                    "protos",
                    "tests",
                    "package-tool",
                    "execution-plan",
                    "cases",
                    "workspace");
    private static final Path APPLICATION =
            Path.of("src", "test", "resources", "workspace-package-authority-isolation");

    @TempDir Path temp;

    @Test
    void composesReadOnlyPreflightAndExplicitWorkspaceEntryWithoutMetadataMutation()
            throws Exception {
        Path project = materializeProject();
        assumeSecureConfinement(project);
        byte[] manifestBefore = Files.readAllBytes(project.resolve("protos.toml"));
        byte[] lockBefore = Files.readAllBytes(project.resolve("protos.lock"));

        ProtosExecutionOutcome outcome =
                ProtosWorkspaceRunDriver.execute(
                        new ProtosWorkspaceRunDriver.Request(
                                CORE,
                                TOOL_ROOT,
                                project,
                                new ProtosStandardLibraryModuleResolver(STANDARD_LIBRARY),
                                "Main",
                                List.of(),
                                exactEnvironmentDomain(),
                                List.of(),
                                null,
                                null,
                                null,
                                null,
                                null,
                                null));

        assertEquals(ProtosExecutionOutcome.State.COMPLETED, outcome.state());
        ProtosIntegerValue value = (ProtosIntegerValue) outcome.value();
        assertEquals(BigInteger.valueOf(73), value.value());
        assertArrayEquals(manifestBefore, Files.readAllBytes(project.resolve("protos.toml")));
        assertArrayEquals(lockBefore, Files.readAllBytes(project.resolve("protos.lock")));
    }

    private Path materializeProject() throws Exception {
        Path project = temp.resolve("project");
        copyTree(WORKSPACE_CASE, project);
        overlay(APPLICATION.resolve("Main.protos"), project.resolve("Main.protos"));
        overlay(
                APPLICATION.resolve("libs/a/internal/Thing.protos"),
                project.resolve("libs/a/internal/Thing.protos"));
        return project;
    }

    private static void copyTree(Path source, Path target) throws Exception {
        try (Stream<Path> paths = Files.walk(source)) {
            for (Path current : paths.toList()) {
                Path relative = source.relativize(current);
                Path destination = target.resolve(relative.toString());
                if (Files.isDirectory(current)) {
                    Files.createDirectories(destination);
                } else {
                    Files.createDirectories(destination.getParent());
                    Files.copy(
                            current,
                            destination,
                            StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.COPY_ATTRIBUTES);
                }
            }
        }
    }

    private static void overlay(Path source, Path destination) throws Exception {
        Files.createDirectories(destination.getParent());
        Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
    }

    private static void assumeSecureConfinement(Path root) throws Exception {
        try (ProtosNioReadOnlyTreeFilesystemBackend backend =
                new ProtosNioReadOnlyTreeFilesystemBackend(root)) {
            assumeTrue(
                    backend.secureConfinementAvailable(),
                    "host provider has no SecureDirectoryStream");
        }
    }

    private static ProtosEnvironmentValue.NativeNameDomain exactEnvironmentDomain() {
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
}
