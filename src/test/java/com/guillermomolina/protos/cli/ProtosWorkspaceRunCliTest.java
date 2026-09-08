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
package com.guillermomolina.protos.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.guillermomolina.protos.execution.ProtosNioReadOnlyTreeFilesystemBackend;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ProtosWorkspaceRunCliTest {
    private static final Path WORKSPACE_CASE =
            Path.of(
                    "protos",
                    "tests",
                    "package-tool",
                    "execution-plan",
                    "cases",
                    "workspace");
    private static final Path APPLICATION =
            Path.of("src", "test", "resources", "workspace-package-cli-run");

    @TempDir Path temp;

    @Test
    void publicRunRequiresExplicitEntryAndHelpDocumentsExactContract() {
        Result missing = runPublic("run");
        assertEquals(2, missing.code());
        assertTrue(missing.stderr().contains("run requires a root-package logical entry"));

        Result help = runPublic("--help");
        assertEquals(0, help.code());
        assertTrue(help.stdout().contains("protos run <entry> [args...]"));
        assertTrue(help.stdout().contains("current directory"));
        assertTrue(help.stdout().contains("neither 'run' nor <entry>"));
    }

    @Test
    void workspaceRunPassesOnlyPostEntryApplicationArguments() throws Exception {
        Path project = materializeProject();
        assumeSecureConfinement(project);

        Result result = runWorkspace(project, "Main", "application-value");

        assertEquals(0, result.code());
        assertEquals("application-value\n", result.stdout());
        assertTrue(result.stderr().isBlank(), result.stderr());
    }

    @Test
    void workspaceRunTranslatesSemanticAndHostFailuresWithoutInternalError() throws Exception {
        Path project = materializeProject();
        assumeSecureConfinement(project);

        Result semantic = runWorkspace(project, "Fail");
        assertEquals(1, semantic.code());
        assertTrue(semantic.stdout().isBlank(), semantic.stdout());
        assertTrue(semantic.stderr().startsWith("Error:"), semantic.stderr());
        assertFalse(semantic.stderr().contains("Internal error"), semantic.stderr());

        Result missing = runWorkspace(project, "Missing");
        assertEquals(1, missing.code());
        assertTrue(missing.stdout().isBlank(), missing.stdout());
        assertTrue(missing.stderr().startsWith("protos run:"), missing.stderr());
        assertFalse(missing.stderr().contains("Internal error"), missing.stderr());
    }

    private Result runPublic(String... args) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int code =
                new ProtosCli()
                        .run(
                                args,
                                InputStream.nullInputStream(),
                                new PrintStream(out),
                                new PrintStream(err));
        return result(code, out, err);
    }

    private Result runWorkspace(Path project, String entry, String... args) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int code =
                new ProtosCli()
                        .runWorkspaceApplication(
                                project,
                                entry,
                                List.of(args),
                                InputStream.nullInputStream(),
                                new PrintStream(out),
                                new PrintStream(err));
        return result(code, out, err);
    }

    private static Result result(
            int code, ByteArrayOutputStream out, ByteArrayOutputStream err) {
        return new Result(
                code,
                out.toString(StandardCharsets.UTF_8),
                err.toString(StandardCharsets.UTF_8));
    }

    private Path materializeProject() throws Exception {
        Path project = temp.resolve("project");
        copyTree(WORKSPACE_CASE, project);
        overlay(APPLICATION.resolve("Main.protos"), project.resolve("Main.protos"));
        overlay(APPLICATION.resolve("Fail.protos"), project.resolve("Fail.protos"));
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

    private record Result(int code, String stdout, String stderr) {}
}
