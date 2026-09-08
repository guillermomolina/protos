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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Temporary single Java execution bridge for Protos-owned TOOL001 fixture corpora.
 *
 * <p>Until TOOL002 owns this boundary, new TOOL001 observable-behavior fixtures belong under
 * {@code protos/tests/package-tool/**} and are executed through this class rather than through
 * one Java wrapper class per corpus.
 */
final class ProtosPackageToolProtosTest {
    private static final Path CORE = Path.of("protos", "lib", "core");
    private static final Path STANDARD_LIBRARY = Path.of("protos", "lib");
    private static final Path TOOL_ROOT = Path.of("protos", "tools", "package");
    private static final Path TEST_ROOT = Path.of("protos", "tests", "package-tool");
    private static final Path RUNNER_MANIFEST = TEST_ROOT.resolve("java-runner.tsv");

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
    void manifestDrivenCorporaUseTheSingleTool001Runner() throws Exception {
        for (String line :
                Files.readAllLines(RUNNER_MANIFEST, StandardCharsets.UTF_8)) {
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }

            String[] fields = line.split("\\t", -1);
            if (fields.length != 2) {
                throw new AssertionError("invalid TOOL001 runner row: " + line);
            }

            Path suiteRoot = TEST_ROOT.resolve(fields[1]);
            switch (fields[0]) {
                case "plain" -> runPlainSuite(suiteRoot);
                case "project-tree" -> runProjectTreeSuite(suiteRoot);
                default -> throw new AssertionError("unknown TOOL001 runner profile: " + line);
            }
        }
    }

    private static void runPlainSuite(Path suiteRoot) throws Exception {
        List<String> lines =
                Files.readAllLines(suiteRoot.resolve("manifest.tsv"), StandardCharsets.UTF_8);

        for (String line : lines) {
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }

            String[] fields = line.split("\\t", -1);
            if (fields.length != 2) {
                throw new AssertionError(
                        "invalid TOOL001 plain fixture row in " + suiteRoot + ": " + line);
            }

            Path fixture = suiteRoot.resolve(fields[0]);
            ProtosExecutionOutcome outcome =
                    execute(
                            Files.readString(fixture, StandardCharsets.UTF_8),
                            newPackageActivation());
            assertExpected(outcome, fields[1], fixture.toString());
        }
    }

    private static void runProjectTreeSuite(Path suiteRoot) throws Exception {
        Path cases = suiteRoot.resolve("cases");
        Path fixtures = suiteRoot.resolve("fixtures");
        List<String> lines =
                Files.readAllLines(suiteRoot.resolve("manifest.tsv"), StandardCharsets.UTF_8);

        for (String line : lines) {
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }

            String[] fields = line.split("\\t", -1);
            if (fields.length != 3) {
                throw new AssertionError(
                        "invalid TOOL001 project-tree fixture row in " + suiteRoot + ": " + line);
            }

            Path caseRoot = cases.resolve(fields[0]);
            Path fixture = fixtures.resolve(fields[1]);

            try (ProtosNioReadOnlyTreeFilesystemBackend backend =
                    new ProtosNioReadOnlyTreeFilesystemBackend(caseRoot)) {
                assumeTrue(
                        backend.secureConfinementAvailable(),
                        "host provider has no SecureDirectoryStream");

                ProtosPrelude prelude = newPackagePrelude();
                ProtosActivation activation = prelude.newModuleActivation();
                ProtosObjectValue rawFilesystem =
                        ProtosStandardFilesystemProtocol.createCapability(
                                prelude.bytesPrototypeForRuntime(),
                                activation,
                                backend);
                ProtosFilesystemValue filesystem =
                        assertInstanceOf(ProtosFilesystemValue.class, rawFilesystem);
                activation.context()
                        .createLocalSlot("projectTreeFilesystem", filesystem);

                ProtosExecutionOutcome outcome =
                        execute(
                                Files.readString(fixture, StandardCharsets.UTF_8),
                                activation);
                assertExpected(outcome, fields[2], fields[0] + "/" + fields[1]);
            }
        }
    }


    @Test
    void manifestCommandReadsValidManifest() throws Exception {
        Files.writeString(
                projectRoot.resolve("protos.toml"),
                "manifest-version = 1\n[package]\nid = \"pkg\"\nversion = \"1.0.0\"\n",
                StandardCharsets.UTF_8);
        assertConfinedTrue("manifest-command/valid-minimal.protos");
    }

    @Test
    void manifestCommandReportsInvalidSchema() throws Exception {
        Files.writeString(
                projectRoot.resolve("protos.toml"),
                "manifest-version = 1\n[package]\nid = \"pkg\"\n",
                StandardCharsets.UTF_8);
        assertConfinedTrue("manifest-command/invalid-schema-diagnostic.protos");
    }

    @Test
    void manifestCommandReportsMissingManifest() throws Exception {
        assertConfinedTrue("manifest-command/missing-manifest-diagnostic.protos");
    }

    @Test
    void manifestCommandReportsInvalidUtf8() throws Exception {
        Files.write(projectRoot.resolve("protos.toml"), new byte[] {(byte) 0xc3, 0x28});
        assertConfinedTrue("manifest-command/invalid-utf8-diagnostic.protos");
    }

    @Test
    void manifestCommandReadsAcrossMultipleTextReaderChunks() throws Exception {
        StringBuilder source =
                new StringBuilder(
                        "manifest-version = 1\n[package]\nid = \"large\"\nversion = \"1.0.0\"\n[exports]\n");
        for (int i = 0; i < 700; i++) {
            source.append("Export").append(i).append(" = \"module/")
                    .append(i).append("\"\n");
        }
        Files.writeString(projectRoot.resolve("protos.toml"), source, StandardCharsets.UTF_8);
        assertConfinedTrue("manifest-command/multichunk-read.protos");
    }

    @Test
    void lockFileLoadsCanonicalContent() throws Exception {
        Files.writeString(projectRoot.resolve("protos.lock"), CANONICAL_LOCK, StandardCharsets.UTF_8);
        assertConfinedTrue("lock-file/load-canonical.protos");
    }

    @Test
    void lockFileRejectsNonCanonicalContent() throws Exception {
        Files.writeString(
                projectRoot.resolve("protos.lock"), NONCANONICAL_LOCK, StandardCharsets.UTF_8);
        assertConfinedFailed("lock-file/load-noncanonical-error.protos");
    }

    @Test
    void lockFileMissingUsesOrdinaryIoFailure() throws Exception {
        try (Fixture fixture = confinedFixture(projectRoot)) {
            assertIoErrorOutcome(
                    executeFile(
                            TEST_ROOT.resolve("lock-file/load-missing-error.protos"),
                            fixture.activation()),
                    fixture.activation(),
                    "lock-file/load-missing-error.protos");
        }
    }

    @Test
    void lockFilePublishesCanonicalBytes() throws Exception {
        Files.writeString(projectRoot.resolve("protos.lock"), "old\n", StandardCharsets.UTF_8);
        assertConfinedTrue("lock-file/publish-canonical.protos");
        assertEquals(CANONICAL_LOCK, Files.readString(projectRoot.resolve("protos.lock")));
        assertTrue(
                Files.notExists(
                        projectRoot.resolve(".protos.lock.stage"), LinkOption.NOFOLLOW_LINKS));
    }

    @Test
    void lockFileInvalidModelCreatesNoStageAndPreservesTarget() throws Exception {
        Files.writeString(projectRoot.resolve("protos.lock"), "old\n", StandardCharsets.UTF_8);
        assertConfinedFailed("lock-file/publish-invalid-no-stage.protos");
        assertEquals("old\n", Files.readString(projectRoot.resolve("protos.lock")));
        assertFalse(
                Files.exists(
                        projectRoot.resolve(".protos.lock.stage"), LinkOption.NOFOLLOW_LINKS));
    }

    @Test
    void lockFileStageCollisionPreservesExistingLockAndStage() throws Exception {
        Files.writeString(projectRoot.resolve("protos.lock"), "old\n", StandardCharsets.UTF_8);
        Files.writeString(
                projectRoot.resolve(".protos.lock.stage"), "stale\n", StandardCharsets.UTF_8);

        try (Fixture fixture = confinedFixture(projectRoot)) {
            assertIoErrorOutcome(
                    executeFile(
                            TEST_ROOT.resolve("lock-file/publish-stage-collision.protos"),
                            fixture.activation()),
                    fixture.activation(),
                    "lock-file/publish-stage-collision.protos");
        }

        assertEquals("old\n", Files.readString(projectRoot.resolve("protos.lock")));
        assertEquals("stale\n", Files.readString(projectRoot.resolve(".protos.lock.stage")));
    }

    private void assertConfinedTrue(String relative) throws Exception {
        try (Fixture fixture = confinedFixture(projectRoot)) {
            ProtosExecutionOutcome outcome =
                    executeFile(TEST_ROOT.resolve(relative), fixture.activation());
            assertExpected(outcome, "true", relative);
        }
    }

    private void assertConfinedFailed(String relative) throws Exception {
        try (Fixture fixture = confinedFixture(projectRoot)) {
            ProtosExecutionOutcome outcome =
                    executeFile(TEST_ROOT.resolve(relative), fixture.activation());
            assertExpected(outcome, "error", relative);
        }
    }

    private static Fixture confinedFixture(Path root) throws Exception {
        ProtosNioConfinedFilesystemBackend backend =
                new ProtosNioConfinedFilesystemBackend(root, READABLE, WRITABLE, MUTABLE);
        if (!backend.secureNamespaceConfinementAvailable()) {
            backend.close();
            assumeTrue(false, "host provider has no SecureDirectoryStream");
        }

        ProtosPrelude prelude = newPackagePrelude();
        ProtosActivation activation = prelude.newModuleActivation();
        ProtosObjectValue rawFilesystem =
                ProtosStandardFilesystemProtocol.createCapability(
                        prelude.bytesPrototypeForRuntime(), activation, backend);
        ProtosFilesystemValue filesystem =
                assertInstanceOf(ProtosFilesystemValue.class, rawFilesystem);
        activation.context().createLocalSlot("filesystem", filesystem);
        return new Fixture(activation, backend);
    }

    private static ProtosExecutionOutcome executeFile(
            Path source,
            ProtosActivation activation)
            throws Exception {
        return execute(Files.readString(source, StandardCharsets.UTF_8), activation);
    }

    private static void assertIoErrorOutcome(
            ProtosExecutionOutcome outcome,
            ProtosActivation activation,
            String label) {
        assertEquals(ProtosExecutionOutcome.State.FAILED, outcome.state(), label);
        ProtosObjectValue error = outcome.error();
        assertSame(
                ProtosCoreErrors.prototype(
                        activation, ProtosCoreErrors.StandardError.I_O_ERROR),
                error.parent().orElseThrow(),
                label);
    }

    private record Fixture(
            ProtosActivation activation,
            ProtosNioConfinedFilesystemBackend backend)
            implements AutoCloseable {
        @Override
        public void close() throws Exception {
            backend.close();
        }
    }

    private static ProtosActivation newPackageActivation() throws Exception {
        return newPackagePrelude().newModuleActivation();
    }

    private static ProtosPrelude newPackagePrelude() throws Exception {
        ProtosStandardLibraryModuleResolver standard =
                new ProtosStandardLibraryModuleResolver(STANDARD_LIBRARY);
        ProtosBundledToolModuleResolver resolver =
                new ProtosBundledToolModuleResolver("package", TOOL_ROOT, standard);
        return new ProtosCoreBootstrap().bootstrap(CORE, resolver);
    }

    private static ProtosExecutionOutcome execute(
            String source,
            ProtosActivation activation)
            throws Exception {
        return ProtosRootTaskExecution.execute(
                new ProtosSourceCompiler().compile(source),
                activation);
    }

    private static void assertExpected(
            ProtosExecutionOutcome outcome,
            String expected,
            String label) {
        switch (expected) {
            case "true" -> {
                assertEquals(ProtosExecutionOutcome.State.COMPLETED, outcome.state(), label);
                assertSame(ProtosBooleanValue.TRUE, outcome.value(), label);
            }
            case "error" ->
                    assertEquals(
                            ProtosExecutionOutcome.State.FAILED,
                            outcome.state(),
                            label);
            default ->
                    throw new AssertionError(
                            "unknown TOOL001 fixture expectation: " + expected);
        }
    }
}
