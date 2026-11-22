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
import com.guillermomolina.protos.execution.ProtosExternalForeignProviderTest;
import com.guillermomolina.protos.execution.ProtosNioReadOnlyTreeFilesystemBackend;
import com.guillermomolina.protos.execution.ProtosPackageContentIdentity;
import com.guillermomolina.protos.execution.ProtosStandaloneHostedExecution.FilesystemGrant;
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

    private static final String CONTENT_METHOD = "protos-package-tree-v2";
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
            "this.value: \"external-module\"\n";
    private static final String MIXED_MAIN =
            "Api: import(\"dep:reg/Api\")\n"
                    + "writer: TextWriter(process.stdout(), process.stdoutEncoding())\n"
                    + "writer.writeLine(process.args().at(0)).value()\n"
                    + "writer.writeLine(Api.value).value()\n"
                    + "Api.value\n";

    /** I155: a root-package MODULE that writes and reads under its explicit Filesystem grant. */
    private static final String FILESYSTEM_MAIN =
            "Files: import(\"std:io/files\")\n"
                    + "path: Path.relative().child(\"nested\").child(\"run.txt\")\n"
                    + "Files.writeAllText(filesystem, path, process.args().at(0), Encoding.UTF8)"
                    + ".value()\n"
                    + "writer: TextWriter(process.stdout(), process.stdoutEncoding())\n"
                    + "writer.writeLine(Files.readAllText(filesystem, path, Encoding.UTF8).value())"
                    + ".value()\n"
                    + "null\n";

    @TempDir Path temp;

    private static final String CLOCK_MAIN = """
            writer: TextWriter(process.stdout(), process.stdoutEncoding())
            if(context.hasSlot("clock")) {
                if(!Integer.recognizes(clock.epochNanoseconds())) { Error().signal() }
                writer.writeLine("true").value()
            } else {
                writer.writeLine("false").value()
            }
            null
            """;


    @Test
    void resolverAndNetworkMatrixReachesOnlyWorkspaceApplication() throws Exception {
        Path project = materializeProject();
        assumeSecureConfinement(project);
        Files.writeString(project.resolve("Main/module.protos"), """
                writer: TextWriter(process.stdout(), process.stdoutEncoding())
                if(context.hasSlot("resolver")) {
                    if(resolver == null) { Error().signal() }
                    resolver.close().value()
                    writer.writeLine("resolver").value()
                }
                if(context.hasSlot("network")) { writer.writeLine("network").value() }
                writer.writeLine(process.args().at(0)).value()
                null
                """);
        for (boolean resolver : new boolean[] {false, true}) {
            for (boolean network : new boolean[] {false, true}) {
                ProtosCli cli = new ProtosCli();
                selectApplicationGrant(cli, "resolverAuthorized", resolver);
                selectApplicationGrant(cli, "networkAuthorized", network);
                Result result = runWorkspace(cli, project, "Main", "--allow-resolver");
                assertEquals(0, result.code(), result.stderr());
                assertEquals((resolver ? "resolver\n" : "") + (network ? "network\n" : "")
                        + "--allow-resolver\n", result.stdout());
            }
        }
        assertEquals(2, runPublic("--allow-resolver", "run", "--allow-resolver", "Main").code());
    }

    @Test
    void workspaceAndExternalPackageServiceKeepResolverAfterReady() throws Exception {
        String service = """
                this.service: () => {
                    Actor.current().send("finish")
                    {
                        finish: () => {
                            if(resolver == null) { Error().signal() }
                            resolver.close().value()
                            writer: TextWriter(process.stdout(), process.stdoutEncoding())
                            writer.writeLine("resolver-alive-after-ready").value()
                            Actor.current().stop()
                            null
                        }
                    }
                }
                """;
        Path project = materializeProject();
        assumeSecureConfinement(project);
        Files.writeString(project.resolve("Main/module.protos"), service);
        ProtosCli cli = new ProtosCli();
        selectApplicationGrant(cli, "resolverAuthorized", true);
        Result workspace = runWorkspaceService(cli, project, "Main", "service");
        assertEquals(0, workspace.code(), workspace.stderr());
        assertEquals("resolver-alive-after-ready\n", workspace.stdout());

        MixedProject mixed = materializeMixedProject("mixed-resolver");
        ProtosLocalExactPackageMaterializationProvider provider =
                new ProtosLocalExactPackageMaterializationProvider(temp.resolve("resolver-store"));
        Path selectedRoot = provider.locationFor(mixed.identity());
        copyTree(mixed.externalSource(), selectedRoot);
        assumeSecureConfinement(selectedRoot);
        Files.writeString(mixed.project().resolve("Main/module.protos"),
                "Api: import(\"dep:reg/Api\")\n" + service);
        ProtosCli packageCli = new ProtosCli(ignored -> provider);
        selectApplicationGrant(packageCli, "resolverAuthorized", true);
        Result packaged = runWorkspaceService(packageCli, mixed.project(), "Main", "service");
        assertEquals(0, packaged.code(), packaged.stderr());
        assertEquals("resolver-alive-after-ready\n", packaged.stdout());
        Files.writeString(mixed.project().resolve("Main/module.protos"), """
                Api: import("dep:reg/Api")
                resolver.close().value()
                null
                """);
        Result ordinary = runWorkspace(packageCli, mixed.project(), "Main");
        assertEquals(0, ordinary.code(), ordinary.stderr());
    }

    /** Selects authority for internal propagation tests; this bypasses the public CLI parser. */
    private static void selectApplicationGrant(ProtosCli cli, String name, boolean granted)
            throws Exception {
        java.lang.reflect.Field field = ProtosCli.class.getDeclaredField(name);
        field.setAccessible(true);
        field.setBoolean(cli, granted);
    }

    @Test
    void workspaceAndPackageRunForwardOnlyExplicitClockSelection() throws Exception {
        Path project = materializeProject();
        assumeSecureConfinement(project);
        Files.writeString(project.resolve("Main/module.protos"), CLOCK_MAIN);
        Result absent = runWorkspace(project, "Main");
        assertEquals(0, absent.code(), absent.stderr());
        assertEquals("false\n", absent.stdout());
        ProtosCli grantedCli = new ProtosCli();
        selectClockForApplication(grantedCli);
        Result granted = runWorkspace(grantedCli, project, "Main");
        assertEquals(0, granted.code(), granted.stderr());
        assertEquals("true\n", granted.stdout());

        MixedProject mixed = materializeMixedProject("mixed-clock");
        ProtosLocalExactPackageMaterializationProvider provider =
                new ProtosLocalExactPackageMaterializationProvider(temp.resolve("mixed-clock-store"));
        Path selectedRoot = provider.locationFor(mixed.identity());
        copyTree(mixed.externalSource(), selectedRoot);
        assumeSecureConfinement(selectedRoot);
        Files.writeString(mixed.project().resolve("Main/module.protos"),
                "Api: import(\"dep:reg/Api\")\n" + CLOCK_MAIN);
        ProtosCli packageCli = new ProtosCli(ignored -> provider);
        selectClockForApplication(packageCli);
        Result packaged = runWorkspace(packageCli, mixed.project(), "Main");
        assertEquals(0, packaged.code(), packaged.stderr());
        assertEquals("true\n", packaged.stdout());
    }

    @Test
    void workspaceRunLaunchesOnlyWithTheExplicitExecutionSelection() throws Exception {
        assumeTrue(Files.isExecutable(Path.of("/bin/echo")), "/bin/echo is required");
        Path project = materializeProject();
        assumeSecureConfinement(project);
        Files.writeString(project.resolve("Main/module.protos"), """
                writer: TextWriter(process.stdout(), process.stdoutEncoding())
                if(context.hasSlot("execution")) {
                    child: execution.start("/bin/echo", ["workspace-child"], Map(), null).value()
                    if(Encoding.UTF8.decode(child.stdout().read(64).value()) == "workspace-child\\n") {
                        writer.writeLine("workspace-child").value()
                    }
                } else {
                    writer.writeLine("false").value()
                }
                null
                """);
        Result absent = runWorkspace(project, "Main");
        assertEquals(0, absent.code(), absent.stderr());
        assertEquals("false\n", absent.stdout());
        ProtosCli grantedCli = new ProtosCli();
        java.lang.reflect.Field selection = ProtosCli.class.getDeclaredField("executionAuthorized");
        selection.setAccessible(true);
        selection.setBoolean(grantedCli, true);
        Result granted = runWorkspace(grantedCli, project, "Main");
        assertEquals(0, granted.code(), granted.stderr());
        assertEquals("workspace-child\n", granted.stdout());
    }

    @Test
    void workspaceAndPackageServiceKeepClockAfterReady() throws Exception {
        Path project = materializeProject();
        assumeSecureConfinement(project);
        String service = """
                this.service: () => {
                    Actor.current().send("finish")
                    {
                        finish: () => {
                            if(!Integer.recognizes(clock.epochNanoseconds())) { Error().signal() }
                            writer: TextWriter(process.stdout(), process.stdoutEncoding())
                            writer.writeLine("true").value()
                            Actor.current().stop()
                            null
                        }
                    }
                }
                """;
        Files.writeString(project.resolve("Main/module.protos"), service);
        ProtosCli workspaceCli = new ProtosCli();
        selectClockForApplication(workspaceCli);
        Result workspace = runWorkspaceService(workspaceCli, project, "Main", "service");
        assertEquals(0, workspace.code(), workspace.stderr());
        assertEquals("true\n", workspace.stdout());

        MixedProject mixed = materializeMixedProject("mixed-clock-service");
        ProtosLocalExactPackageMaterializationProvider provider =
                new ProtosLocalExactPackageMaterializationProvider(temp.resolve("mixed-clock-service-store"));
        Path selectedRoot = provider.locationFor(mixed.identity());
        copyTree(mixed.externalSource(), selectedRoot);
        assumeSecureConfinement(selectedRoot);
        Files.writeString(mixed.project().resolve("Main/module.protos"),
                "Api: import(\"dep:reg/Api\")\n" + service);
        ProtosCli packageCli = new ProtosCli(ignored -> provider);
        selectClockForApplication(packageCli);
        Result packaged = runWorkspaceService(packageCli, mixed.project(), "Main", "service");
        assertEquals(0, packaged.code(), packaged.stderr());
        assertEquals("true\n", packaged.stdout());
    }

    /** Exercises the post-admission run seam with the same selection set by CLI dispatch. */
    private static void selectClockForApplication(ProtosCli cli) throws Exception {
        java.lang.reflect.Field selection = ProtosCli.class.getDeclaredField("clockAuthorized");
        selection.setAccessible(true);
        selection.setBoolean(cli, true);
    }

    @Test
    void publicRunRequiresExplicitEntryAndHelpDocumentsExactContract() {
        Result missing = runPublic("run");
        assertEquals(2, missing.code());
        assertTrue(missing.stderr().contains("run requires a root-package logical entry"));

        Result help = runPublic("--help");
        assertEquals(0, help.code());
        assertTrue(help.stdout().contains(
                "protos run [--allow-network] [--allow-resolver] [--allow-entropy] [--allow-clock] [--allow-execution] [--service=<binding>] <entry> [args...]"));
        assertTrue(help.stdout().contains("current directory"));
        assertTrue(help.stdout().contains("neither 'run' nor <entry>"));
    }

    @Test
    void workspaceRunReceivesOnlyTheExplicitFilesystemGrant() throws Exception {
        Path project = materializeProject();
        assumeSecureConfinement(project);
        Files.writeString(project.resolve("Main/module.protos"), FILESYSTEM_MAIN);
        String lockBefore = Files.readString(project.resolve("protos.lock"));
        Path data = Files.createDirectories(temp.resolve("granted-data/nested"));

        Result granted =
                runWorkspace(
                        new ProtosCli(),
                        project,
                        "Main",
                        FilesystemGrant.directory(data.getParent()),
                        "workspace-value");

        assertEquals(0, granted.code(), granted.stderr());
        assertEquals("workspace-value\n", granted.stdout());
        assertEquals("workspace-value", Files.readString(data.resolve("run.txt")));
        // The grant is the selected directory, never the package root, and preflight left the
        // package metadata untouched.
        assertTrue(Files.notExists(project.resolve("nested")));
        assertEquals(lockBefore, Files.readString(project.resolve("protos.lock")));

        Result ungranted = runWorkspace(project, "Main", "workspace-value");
        assertEquals(1, ungranted.code());
        assertTrue(ungranted.stderr().contains("Error:"), ungranted.stderr());
    }

    @Test
    void publicRunParsesFilesystemGrantBeforeTheEntryOnly() {
        Result duplicate =
                runPublic("run", "--allow-filesystem=a", "--allow-filesystem=b", "Main");
        assertEquals(2, duplicate.code());
        Result bare = runPublic("run", "--allow-filesystem", "Main");
        assertEquals(2, bare.code());
    }

    @Test
    void externalMaterializationRunReceivesTheExplicitFilesystemGrant() throws Exception {
        MixedProject mixed = materializeMixedProject("mixed-filesystem");
        ProtosLocalExactPackageMaterializationProvider provider =
                new ProtosLocalExactPackageMaterializationProvider(
                        temp.resolve("mixed-filesystem-store"));
        Path selectedRoot = provider.locationFor(mixed.identity());
        copyTree(mixed.externalSource(), selectedRoot);
        assumeSecureConfinement(selectedRoot);
        Files.writeString(
                mixed.project().resolve("Main/module.protos"),
                "Api: import(\"dep:reg/Api\")\n" + FILESYSTEM_MAIN);
        Path data = Files.createDirectories(temp.resolve("mixed-data/nested"));

        Result granted =
                runWorkspace(
                        new ProtosCli(ignoredDistributionRoot -> provider),
                        mixed.project(),
                        "Main",
                        FilesystemGrant.directory(data.getParent()),
                        "external-value");

        assertEquals(0, granted.code(), granted.stderr());
        assertEquals("external-value\n", granted.stdout());
        assertEquals("external-value", Files.readString(data.resolve("run.txt")));
        assertTrue(Files.notExists(selectedRoot.resolve("nested")));

        Result ungranted =
                runWorkspace(
                        new ProtosCli(ignoredDistributionRoot -> provider),
                        mixed.project(),
                        "Main",
                        "external-value");
        assertEquals(1, ungranted.code());
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

    @Test
    void exportedModuleServicePreservesArgumentsAndTerminatesNormally()
            throws Exception {
        Path project = materializeProject();
        assumeSecureConfinement(project);

        Files.writeString(
                project.resolve("Main/module.protos"),
                """
                this.service: () => {
                    behavior: {
                        finish: () => {
                            writer: TextWriter(
                                process.stdout(),
                                process.stdoutEncoding()
                            )
                            writer.writeLine(process.args().at(0)).value()
                            Actor.current().stop()
                            null
                        }
                    }
                    Actor.current().send("finish")
                    behavior
                }
                """,
                StandardCharsets.UTF_8);

        Result result = runWorkspaceService(
                new ProtosCli(),
                project,
                "Main",
                "service",
                "module-argument");

        assertEquals(0, result.code(), result.stderr());
        assertEquals("module-argument\n", result.stdout());
    }

    @Test
    void privateModuleBindingCannotBeSelectedAsService()
            throws Exception {
        Path project = materializeProject();
        assumeSecureConfinement(project);

        Files.writeString(
                project.resolve("Main/module.protos"),
                "service: () => { {} }\n",
                StandardCharsets.UTF_8);

        Result result = runWorkspaceService(
                new ProtosCli(), project, "Main", "service");

        assertEquals(1, result.code());
        assertTrue(result.stderr().contains("RootActor"), result.stderr());
    }

    @Test
    void mixedPackageServiceKeepsExternalCustodyUntilLateMessageImport()
            throws Exception {
        MixedProject mixed = materializeMixedProject("i154-mixed-service");
        ProtosLocalExactPackageMaterializationProvider provider =
                new ProtosLocalExactPackageMaterializationProvider(
                        temp.resolve("i154-mixed-service-store"));

        Path selectedRoot = provider.locationFor(mixed.identity());
        copyTree(mixed.externalSource(), selectedRoot);
        assumeSecureConfinement(selectedRoot);

        Files.writeString(
                mixed.project().resolve("Main/module.protos"),
                """
                this.service: () => {
                    behavior: {
                        finish: () => {
                            api: import("dep:reg/Api")
                            writer: TextWriter(
                                process.stdout(),
                                process.stdoutEncoding()
                            )
                            writer.writeLine(api.value).value()
                            Actor.current().stop()
                            null
                        }
                    }
                    Actor.current().send("finish")
                    behavior
                }
                """,
                StandardCharsets.UTF_8);

        Result result = runWorkspaceService(
                new ProtosCli(ignored -> provider),
                mixed.project(),
                "Main",
                "service");

        assertEquals(0, result.code(), result.stderr());
        assertEquals("external-module\n", result.stdout());
    }

    /** I169: a root-package MODULE that calls one authorized C function through protos-ffm. */
    private static final String FFI_MAIN =
            "ffi: import(\"ffi:first\")\n"
                    + "identity: ffi.bind(\"ffi_test_i32_identity\","
                    + " ffi.param(ffi.signature(ffi.i32), ffi.i32))\n"
                    + "if(!(identity(7) == 7)) { Error().signal() }\n"
                    + "writer: TextWriter(process.stdout(), process.stdoutEncoding())\n"
                    + "writer.writeLine(\"ffi-identity-7\").value()\n"
                    + "null\n";

    @Test
    void i169RunAcceptsProviderAndLibraryOptionsInBothPositions() throws Exception {
        Path provider = Files.writeString(temp.resolve("provider.jar"), "");
        for (String[] args :
                List.of(
                        new String[] {"run", "--foreign-provider-path", provider.toString(),
                                "--allow-library", "sqlite3=libsqlite3.so.0", "Main"},
                        new String[] {"--foreign-provider-path", provider.toString(),
                                "--allow-library", "sqlite3=libsqlite3.so.0", "run", "Main"},
                        new String[] {"--foreign-provider-path", provider.toString(), "run",
                                "--allow-library", "sqlite3=libsqlite3.so.0", "Main"})) {
            ProtosCli cli = new ProtosCli();
            Result result = runPublic(cli, args);
            // Parsing succeeded; the later workspace failure (no package in this directory) is
            // not a usage error.
            assertNotEquals(2, result.code(), () -> List.of(args) + ": " + result.stderr());
            assertEquals(Map.of("sqlite3", "libsqlite3.so.0"), cli.allowedLibraries());
            assertEquals(List.of(provider.toAbsolutePath().normalize()), providerPaths(cli));
        }

        ProtosCli several = new ProtosCli();
        Result many =
                runPublic(several,
                        "--allow-library", "first=libfirst.so.1",
                        "run",
                        "--allow-library", "second=/opt/lib/libsecond.so",
                        "--allow-library", "third=libthird.so",
                        "Main");
        assertNotEquals(2, many.code(), many.stderr());
        assertEquals(
                Map.of(
                        "first", "libfirst.so.1",
                        "second", "/opt/lib/libsecond.so",
                        "third", "libthird.so"),
                several.allowedLibraries());
    }

    @Test
    void i169RunRejectsMalformedProviderAndLibraryOptionsAsUsageErrors() throws Exception {
        Path provider = Files.writeString(temp.resolve("provider.jar"), "");
        for (List<String> args :
                List.of(
                        List.of("--allow-library", "a=liba.so", "run",
                                "--allow-library", "a=libother.so", "Main"),
                        List.of("run", "--allow-library", "a=liba.so",
                                "--allow-library", "a=liba.so", "Main"),
                        List.of("run", "--foreign-provider-path",
                                temp.resolve("absent.jar").toString(), "Main"),
                        List.of("--foreign-provider-path",
                                temp.resolve("absent.jar").toString(), "run", "Main"),
                        List.of("run", "--foreign-provider-path"),
                        List.of("run", "--allow-library"),
                        List.of("run", "--foreign-provider-path", provider.toString()),
                        List.of("run", "--allow-library", "a=liba.so"),
                        List.of("run", "--allow-library", "sqlite3=lib/libsqlite3.so.0", "Main"),
                        List.of("run", "--allow-library", "sqlite3=./libsqlite3.so.0", "Main"),
                        List.of("run", "--allow-library", "lib.sqlite3=libsqlite3.so.0", "Main"),
                        List.of("run", "--allow-library", "9lib=libsqlite3.so.0", "Main"),
                        List.of("run", "--allow-library", "sqlite3", "Main"),
                        List.of("--inspect", "run", "Main"))) {
            Result result = runPublic(args.toArray(String[]::new));
            assertEquals(2, result.code(), () -> args + ": " + result.stderr());
            assertEquals("", result.stdout(), args::toString);
        }
    }

    @Test
    void i169AllowLibraryAfterTheRunEntryIsAnApplicationArgument() throws Exception {
        Path project = materializeProject();
        assumeSecureConfinement(project);
        Files.writeString(project.resolve("Main/module.protos"), """
                writer: TextWriter(process.stdout(), process.stdoutEncoding())
                writer.writeLine(process.args().at(0)).value()
                writer.writeLine(process.args().at(1)).value()
                null
                """);
        // Public parsing: after the entry the spelling is not an option, so no usage error and
        // no authorization is recorded.
        ProtosCli parsed = new ProtosCli();
        Result trailing = runPublic(parsed, "run", "Main", "--allow-library", "a/b");
        assertNotEquals(2, trailing.code(), trailing.stderr());
        assertEquals(Map.of(), parsed.allowedLibraries());
        // The post-admission seam passes both spellings unchanged to process.args().
        Result result = runWorkspace(project, "Main", "--allow-library", "a/b");
        assertEquals(0, result.code(), result.stderr());
        assertEquals("--allow-library\na/b\n", result.stdout());
    }

    @Test
    void i169WorkspaceRunReachesTheSelectedFfmRuntimeHost() throws Exception {
        Path library = ProtosExternalForeignProviderTest.ffiTestLibrary(temp);
        assumeTrue(library != null, "FFM fixture requires Linux x86-64 and a C compiler");
        Path provider = ProtosExternalForeignProviderTest.distributableFfmProviderJar(temp);
        Path project = materializeProject();
        assumeSecureConfinement(project);
        Files.writeString(project.resolve("Main/module.protos"), FFI_MAIN);

        ProtosCli authorized = new ProtosCli();
        selectForeignAuthority(authorized, List.of(provider), Map.of("first", library.toString()));
        Result granted = runWorkspace(authorized, project, "Main");
        assertEquals(0, granted.code(), granted.stderr());
        assertEquals("ffi-identity-7\n", granted.stdout());

        // The provider without the library authorization: the import is refused.
        ProtosCli unauthorized = new ProtosCli();
        selectForeignAuthority(unauthorized, List.of(provider), Map.of());
        Result denied = runWorkspace(unauthorized, project, "Main");
        assertEquals(1, denied.code(), denied.stderr());
        assertEquals("", denied.stdout());

        // The authorization without the provider: no ffi scheme exists.
        ProtosCli providerless = new ProtosCli();
        selectForeignAuthority(providerless, List.of(), Map.of("first", library.toString()));
        Result missing = runWorkspace(providerless, project, "Main");
        assertEquals(1, missing.code(), missing.stderr());
        assertEquals("", missing.stdout());
    }

    @Test
    void i169WorkspaceServiceReachesTheSelectedFfmRuntimeHost() throws Exception {
        Path library = ProtosExternalForeignProviderTest.ffiTestLibrary(temp);
        assumeTrue(library != null, "FFM fixture requires Linux x86-64 and a C compiler");
        Path provider = ProtosExternalForeignProviderTest.distributableFfmProviderJar(temp);
        Path project = materializeProject();
        assumeSecureConfinement(project);
        Files.writeString(project.resolve("Main/module.protos"), """
                this.service: () => {
                    behavior: {
                        finish: () => {
                            ffi: import("ffi:first")
                            identity: ffi.bind("ffi_test_i32_identity",
                                ffi.param(ffi.signature(ffi.i32), ffi.i32))
                            if(!(identity(9) == 9)) { Error().signal() }
                            writer: TextWriter(process.stdout(), process.stdoutEncoding())
                            writer.writeLine("ffi-service-9").value()
                            Actor.current().stop()
                            null
                        }
                    }
                    Actor.current().send("finish")
                    behavior
                }
                """);
        ProtosCli cli = new ProtosCli();
        selectForeignAuthority(cli, List.of(provider), Map.of("first", library.toString()));
        Result result = runWorkspaceService(cli, project, "Main", "service");
        assertEquals(0, result.code(), result.stderr());
        assertEquals("ffi-service-9\n", result.stdout());

        ProtosCli unauthorized = new ProtosCli();
        selectForeignAuthority(unauthorized, List.of(provider), Map.of());
        Result denied = runWorkspaceService(unauthorized, project, "Main", "service");
        assertEquals(1, denied.code(), denied.stderr());
    }

    @Test
    void i169ExternalPackageRunReachesTheSelectedFfmRuntimeHost() throws Exception {
        Path library = ProtosExternalForeignProviderTest.ffiTestLibrary(temp);
        assumeTrue(library != null, "FFM fixture requires Linux x86-64 and a C compiler");
        Path provider = ProtosExternalForeignProviderTest.distributableFfmProviderJar(temp);
        MixedProject mixed = materializeMixedProject("i169-mixed-ffm");
        ProtosLocalExactPackageMaterializationProvider materialization =
                new ProtosLocalExactPackageMaterializationProvider(temp.resolve("i169-ffm-store"));
        Path selectedRoot = materialization.locationFor(mixed.identity());
        copyTree(mixed.externalSource(), selectedRoot);
        assumeSecureConfinement(selectedRoot);
        Files.writeString(
                mixed.project().resolve("Main/module.protos"),
                "Api: import(\"dep:reg/Api\")\n" + FFI_MAIN);

        ProtosCli authorized = new ProtosCli(ignored -> materialization);
        selectForeignAuthority(authorized, List.of(provider), Map.of("first", library.toString()));
        Result granted = runWorkspace(authorized, mixed.project(), "Main");
        assertEquals(0, granted.code(), granted.stderr());
        assertEquals("ffi-identity-7\n", granted.stdout());

        ProtosCli unauthorized = new ProtosCli(ignored -> materialization);
        selectForeignAuthority(unauthorized, List.of(provider), Map.of());
        Result denied = runWorkspace(unauthorized, mixed.project(), "Main");
        assertEquals(1, denied.code(), denied.stderr());
        assertEquals("", denied.stdout());
    }

    @Test
    void i169FfmOptionsAreNotAvailableToBundledTools() throws Exception {
        Path provider = Files.writeString(temp.resolve("provider.jar"), "");
        for (String tool : List.of("test", "package", "format", "lint", "language-server")) {
            for (List<String> args :
                    List.of(
                            List.of("--allow-library", "sqlite3=libsqlite3.so.0", tool),
                            List.of("--foreign-provider-path", provider.toString(), tool))) {
                Result result = runPublic(args.toArray(String[]::new));
                assertEquals(2, result.code(), () -> args + ": " + result.stderr());
                assertEquals("", result.stdout(), args::toString);
            }
        }
    }

    /** Selects foreign authority for the post-admission run seam exactly as CLI dispatch does. */
    private static void selectForeignAuthority(
            ProtosCli cli, List<Path> providerPaths, Map<String, String> libraries)
            throws Exception {
        java.lang.reflect.Field paths = ProtosCli.class.getDeclaredField("foreignProviderPaths");
        paths.setAccessible(true);
        paths.set(cli, List.copyOf(providerPaths));
        java.lang.reflect.Field allowed = ProtosCli.class.getDeclaredField("allowedLibraries");
        allowed.setAccessible(true);
        allowed.set(cli, Map.copyOf(libraries));
    }

    @SuppressWarnings("unchecked")
    private static List<Path> providerPaths(ProtosCli cli) throws Exception {
        java.lang.reflect.Field paths = ProtosCli.class.getDeclaredField("foreignProviderPaths");
        paths.setAccessible(true);
        return (List<Path>) paths.get(cli);
    }

    private Result runWorkspaceService(
            ProtosCli cli,
            Path project,
            String entry,
            String binding,
            String... args) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        int code = cli.runWorkspaceApplication(
                project,
                entry,
                List.of(args),
                InputStream.nullInputStream(),
                new PrintStream(out),
                new PrintStream(err),
                binding);

        return result(code, out, err);
    }

    private Result runPublic(String... args) {
        return runPublic(new ProtosCli(), args);
    }

    private Result runPublic(ProtosCli cli, String... args) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int code =
                cli
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
            ProtosCli cli, Path project, String entry, FilesystemGrant grant, String... args) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int code =
                cli.runWorkspaceApplication(
                        project,
                        entry,
                        List.of(args),
                        InputStream.nullInputStream(),
                        new PrintStream(out),
                        new PrintStream(err),
                        null,
                        grant);
        return result(code, out, err);
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
                external.resolve("package.toml"),
                EXTERNAL_MANIFEST,
                StandardCharsets.UTF_8);
        Files.createDirectories(external.resolve("Api"));
        Files.writeString(
                external.resolve("Api/module.protos"),
                EXTERNAL_API,
                StandardCharsets.UTF_8);

        ProtosPackageContentIdentity content =
                contentIdentity(
                        Map.of(
                                "package.toml", EXTERNAL_MANIFEST,
                                "Api/module.protos", EXTERNAL_API));
        ProtosExactExternalPackageIdentity.Registry identity =
                new ProtosExactExternalPackageIdentity.Registry(
                        "external-a", "1.0.0", content);

        Files.writeString(
                project.resolve("package.toml"),
                ROOT_MANIFEST,
                StandardCharsets.UTF_8);
        Files.writeString(
                project.resolve("protos.lock"),
                mixedLock(content),
                StandardCharsets.UTF_8);
        Files.createDirectories(project.resolve("Main"));
        Files.writeString(
                project.resolve("Main/module.protos"),
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
     * Independent small-tree oracle for the frozen protos-package-tree-v2 framing used by this
     * CLI integration fixture. The production verifier remains bundled Protos/F2E2.
     */
    private static ProtosPackageContentIdentity contentIdentity(
            Map<String, String> files) throws Exception {
        TreeMap<String, String> ordered = new TreeMap<>(files);
        ByteArrayOutputStream canonical = new ByteArrayOutputStream();
        canonical.writeBytes(
                "protos-package-tree-v2\0"
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
        overlay(APPLICATION.resolve("Main/module.protos"), project.resolve("Main/module.protos"));
        overlay(APPLICATION.resolve("Fail/module.protos"), project.resolve("Fail/module.protos"));
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
