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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.guillermomolina.protos.execution.ProtosExactExternalPackageIdentity;
import com.guillermomolina.protos.execution.ProtosExecutionOutcome;
import com.guillermomolina.protos.execution.ProtosNioReadOnlyTreeFilesystemBackend;
import com.guillermomolina.protos.execution.ProtosPackageContentIdentity;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
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

    private static final String CONTENT_METHOD = "protos-package-tree-v1";
    private static final String CONTENT_ALGORITHM = "sha256";
    private static final String ROOT_MANIFEST =
            "manifest-version = 1\n"
                    + "\n"
                    + "[package]\n"
                    + "id = \"root\"\n"
                    + "version = \"1.0.0\"\n"
                    + "\n"
                    + "[dependencies.reg]\n"
                    + "authority = \"public\"\n"
                    + "package = \"pkg\"\n"
                    + "version = \"1.0.0\"\n";
    private static final String EXTERNAL_MANIFEST =
            "manifest-version = 1\n"
                    + "\n"
                    + "[package]\n"
                    + "id = \"external-a\"\n"
                    + "version = \"1.0.0\"\n"
                    + "\n"
                    + "[exports]\n"
                    + "Api = \"Api\"\n";
    private static final String EXTERNAL_API =
            "value: \"external-module\"\n";
    private static final String MIXED_MAIN =
            "Api: import(\"dep:reg/Api\")\n"
                    + "writer: TextWriter(process.stdout(), process.stdoutEncoding())\n"
                    + "writer.writeLine(process.args().at(0)).value()\n"
                    + "writer.writeLine(Api.value).value()\n"
                    + "Api.value\n";

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
    void privateMaterializationBackendUsesTheCompleteExactIdentityWithoutFallback()
            throws Exception {
        ProtosPackageContentIdentity content =
                new ProtosPackageContentIdentity(
                        CONTENT_METHOD,
                        CONTENT_ALGORITHM,
                        "0123456789abcdef".repeat(4));
        ProtosExactExternalPackageIdentity.Registry v1 =
                new ProtosExactExternalPackageIdentity.Registry(
                        "external-a", "1.0.0", content);
        ProtosExactExternalPackageIdentity.Registry v2 =
                new ProtosExactExternalPackageIdentity.Registry(
                        "external-a", "2.0.0", content);
        ProtosExactExternalPackageIdentity.Git git =
                new ProtosExactExternalPackageIdentity.Git(
                        "external-a", "deadbeef", content);

        Path root = temp.resolve("private-materializations");
        ProtosLocalExactPackageMaterializationProvider provider =
                new ProtosLocalExactPackageMaterializationProvider(root);

        Path v1Location = provider.locationFor(v1);
        Path v2Location = provider.locationFor(v2);
        Path gitLocation = provider.locationFor(git);

        assertNotEquals(v1Location, v2Location);
        assertNotEquals(v1Location, gitLocation);
        assertNotEquals(v2Location, gitLocation);
        assertEquals(root.toAbsolutePath().normalize(), v1Location.getParent());
        assertEquals(64, v1Location.getFileName().toString().length());
        assertTrue(v1Location.getFileName().toString().matches("[0-9a-f]{64}"));

        Files.createDirectories(v1Location);

        assertEquals(v1Location, provider.select(v1));
        assertThrows(IOException.class, () -> provider.select(v2));
        assertThrows(IOException.class, () -> provider.select(git));
        assertFalse(Files.exists(v2Location));
        assertFalse(Files.exists(gitLocation));
    }

    @Test
    void workspaceRunDoesNotConsultTheMaterializationProvider() throws Exception {
        Path project = materializeProject();
        assumeSecureConfinement(project);
        int[] lookups = {0};
        ProtosCli cli =
                new ProtosCli(
                        ignoredDistributionRoot ->
                                identity -> {
                                    lookups[0]++;
                                    throw new IOException(
                                            "workspace-only execution consulted external provider");
                                });

        Result result =
                runWorkspace(cli, project, "Main", "application-value");

        assertEquals(0, result.code());
        assertEquals("application-value\n", result.stdout());
        assertTrue(result.stderr().isBlank(), result.stderr());
        assertEquals(0, lookups[0]);
    }

    @Test
    void publicRunBootstrapExecutesAnExactVerifiedExternalPackage() throws Exception {
        MixedProject mixed = materializeMixedProject("mixed-success");
        ProtosLocalExactPackageMaterializationProvider provider =
                new ProtosLocalExactPackageMaterializationProvider(
                        temp.resolve("mixed-success-store"));
        Path selectedRoot = provider.locationFor(mixed.identity());
        copyTree(mixed.externalSource(), selectedRoot);
        assumeSecureConfinement(selectedRoot);

        Result result =
                runWorkspace(
                        new ProtosCli(ignoredDistributionRoot -> provider),
                        mixed.project(),
                        "Main",
                        "argument-value");

        assertEquals(0, result.code(), result.stderr());
        assertEquals(
                "argument-value\nexternal-module\n",
                result.stdout());
        assertTrue(result.stderr().isBlank(), result.stderr());
    }

    @Test
    void publicRunMissingExactMaterializationFailsBeforeApplicationExecution()
            throws Exception {
        MixedProject mixed = materializeMixedProject("mixed-missing");
        Path storeRoot = temp.resolve("mixed-missing-store");
        ProtosLocalExactPackageMaterializationProvider provider =
                new ProtosLocalExactPackageMaterializationProvider(storeRoot);
        String lockBefore =
                Files.readString(
                        mixed.project().resolve("protos.lock"),
                        StandardCharsets.UTF_8);

        Result result =
                runWorkspace(
                        new ProtosCli(ignoredDistributionRoot -> provider),
                        mixed.project(),
                        "Main",
                        "argument-value");

        assertEquals(1, result.code());
        assertTrue(result.stdout().isBlank(), result.stdout());
        assertTrue(
                result.stderr().contains(
                        "exact external package materialization is unavailable"),
                result.stderr());
        assertFalse(Files.exists(storeRoot));
        assertEquals(
                lockBefore,
                Files.readString(
                        mixed.project().resolve("protos.lock"),
                        StandardCharsets.UTF_8));
    }

    @Test
    void workspaceRunOutcomeTranslationDoesNotRequireAnotherWorkspaceExecution() {
        ProtosCli cli = new ProtosCli();

        ByteArrayOutputStream failedBytes = new ByteArrayOutputStream();
        PrintStream failedErr = new PrintStream(failedBytes);
        ProtosObjectValue error =
                new ProtosObjectValue(ProtosObjectValue.rootObject());

        int failedCode =
                cli.workspaceOutcomeExitCode(
                        ProtosExecutionOutcome.failed(error),
                        failedErr);

        String failedText = failedBytes.toString(StandardCharsets.UTF_8);
        assertEquals(1, failedCode);
        assertTrue(failedText.startsWith("Error:"), failedText);
        assertFalse(failedText.contains("Internal error"), failedText);

        ByteArrayOutputStream cancelledBytes = new ByteArrayOutputStream();
        PrintStream cancelledErr = new PrintStream(cancelledBytes);

        int cancelledCode =
                cli.workspaceOutcomeExitCode(
                        ProtosExecutionOutcome.cancelled(),
                        cancelledErr);

        String cancelledText =
                cancelledBytes.toString(StandardCharsets.UTF_8);
        assertEquals(1, cancelledCode);
        assertTrue(
                cancelledText.startsWith(
                        "Runtime error: workspace application root task was cancelled"),
                cancelledText);

        ByteArrayOutputStream hostBytes = new ByteArrayOutputStream();
        PrintStream hostErr = new PrintStream(hostBytes);

        int hostCode =
                ProtosCli.workspaceHostFailureExitCode(
                        new IOException("missing entry"),
                        hostErr);

        String hostText = hostBytes.toString(StandardCharsets.UTF_8);
        assertEquals(1, hostCode);
        assertTrue(hostText.startsWith("protos run: missing entry"), hostText);
        assertFalse(hostText.contains("Internal error"), hostText);
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
        return runWorkspace(new ProtosCli(), project, entry, args);
    }

    private Result runWorkspace(
            ProtosCli cli, Path project, String entry, String... args) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int code =
                cli.runWorkspaceApplication(
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

    private MixedProject materializeMixedProject(String name) throws Exception {
        Path root = temp.resolve(name);
        Path project = Files.createDirectories(root.resolve("project"));
        Path external = Files.createDirectories(root.resolve("external-source"));

        Files.writeString(
                external.resolve("protos.toml"),
                EXTERNAL_MANIFEST,
                StandardCharsets.UTF_8);
        Files.writeString(
                external.resolve("Api.protos"),
                EXTERNAL_API,
                StandardCharsets.UTF_8);

        ProtosPackageContentIdentity content =
                contentIdentity(
                        Map.of(
                                "protos.toml", EXTERNAL_MANIFEST,
                                "Api.protos", EXTERNAL_API));
        ProtosExactExternalPackageIdentity.Registry identity =
                new ProtosExactExternalPackageIdentity.Registry(
                        "external-a", "1.0.0", content);

        Files.writeString(
                project.resolve("protos.toml"),
                ROOT_MANIFEST,
                StandardCharsets.UTF_8);
        Files.writeString(
                project.resolve("protos.lock"),
                mixedLock(content),
                StandardCharsets.UTF_8);
        Files.writeString(
                project.resolve("Main.protos"),
                MIXED_MAIN,
                StandardCharsets.UTF_8);

        assumeSecureConfinement(project);
        return new MixedProject(project, external, identity);
    }

    private static String mixedLock(ProtosPackageContentIdentity content) {
        return "lock-format 1\n"
                + "resolver-version 1\n"
                + "resolution-input protos-resolution-input-v1 "
                + "sha256:"
                + "6f2526d30fdc07e44c319bdb194eb0e7fafadcd61768eb36fc13917abf2fc2c5\n"
                + "\n"
                + "root workspace \"root\"\n"
                + "registry-node registry \"external-a\" \"1.0.0\" "
                + "locator \"pkg\" authority \"public\" content "
                + content.method()
                + " "
                + content.algorithm()
                + ":"
                + content.hex()
                + "\n"
                + "dependency workspace \"root\" alias \"reg\" "
                + "target registry \"external-a\" \"1.0.0\"\n";
    }

    /**
     * Independent small-tree oracle for the frozen protos-package-tree-v1 framing used by this
     * CLI integration fixture. The production verifier remains bundled Protos/F2E2.
     */
    private static ProtosPackageContentIdentity contentIdentity(
            Map<String, String> files) throws Exception {
        TreeMap<String, String> ordered = new TreeMap<>(files);
        ByteArrayOutputStream canonical = new ByteArrayOutputStream();
        canonical.writeBytes(
                "protos-package-tree-v1\0"
                        .getBytes(StandardCharsets.US_ASCII));

        for (Map.Entry<String, String> entry : ordered.entrySet()) {
            byte[] name =
                    entry.getKey().getBytes(StandardCharsets.US_ASCII);
            byte[] bytes =
                    entry.getValue().getBytes(StandardCharsets.UTF_8);

            canonical.write(0x01);
            writeVaruint(canonical, name.length);
            canonical.writeBytes(name);
            writeVaruint(canonical, bytes.length);
            canonical.writeBytes(bytes);
        }
        canonical.write(0x00);

        return new ProtosPackageContentIdentity(
                CONTENT_METHOD,
                CONTENT_ALGORITHM,
                HexFormat.of()
                        .formatHex(
                                MessageDigest.getInstance("SHA-256")
                                        .digest(canonical.toByteArray())));
    }

    private static void writeVaruint(
            ByteArrayOutputStream out, int value) {
        int remaining = value;
        do {
            int low = remaining & 0x7f;
            remaining >>>= 7;
            out.write(remaining == 0 ? low : low | 0x80);
        } while (remaining != 0);
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

    private record MixedProject(
            Path project,
            Path externalSource,
            ProtosExactExternalPackageIdentity.Registry identity) {}

    private record Result(int code, String stdout, String stderr) {}
}
