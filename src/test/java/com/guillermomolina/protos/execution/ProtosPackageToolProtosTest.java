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
final class ProtosPackageToolProtosTest {
    private static final Path CORE = Path.of("protos", "lib", "core");
    private static final Path STANDARD_LIBRARY = Path.of("protos", "lib");
    private static final Path TOOL_ROOT = Path.of("protos", "tools", "package");
    private static final Path TEST_ROOT = Path.of("protos", "tests", "package-tool");

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


    private static final String RESOLUTION_INPUT_FRESH_LOCK =
            "lock-format 1\n"
                    + "resolver-version 1\n"
                    + "resolution-input protos-resolution-input-v1 sha256:7041a8afff9ffd0879673557d5c88e47b5fa7f577b369165679d8899f382f579\n"
                    + "\n"
                    + "root workspace \"root\"\n";

    @TempDir Path projectRoot;

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


    @Test
    void metadataPublicationReplacesManifestTarget() throws Exception {
        Files.writeString(projectRoot.resolve("protos.toml"), "old\n", StandardCharsets.UTF_8);
        assertConfinedTrue("metadata-publication/publish-replaces-target.protos");

        assertEquals(
                "new metadata\n",
                Files.readString(projectRoot.resolve("protos.toml"), StandardCharsets.UTF_8));
        assertTrue(
                Files.notExists(
                        projectRoot.resolve(".protos.toml.stage"), LinkOption.NOFOLLOW_LINKS));
    }

    @Test
    void metadataPublicationReplacesLockTarget() throws Exception {
        Files.writeString(projectRoot.resolve("protos.lock"), "old lock\n", StandardCharsets.UTF_8);
        assertConfinedTrue("metadata-publication/publish-lock-replaces-target.protos");

        assertEquals(
                "new lock metadata\n",
                Files.readString(projectRoot.resolve("protos.lock"), StandardCharsets.UTF_8));
        assertTrue(
                Files.notExists(
                        projectRoot.resolve(".protos.lock.stage"), LinkOption.NOFOLLOW_LINKS));
    }

    @Test
    void metadataPublicationStageCollisionPreservesTarget() throws Exception {
        Files.writeString(projectRoot.resolve("protos.toml"), "old\n", StandardCharsets.UTF_8);
        Files.writeString(
                projectRoot.resolve(".protos.toml.stage"), "stale\n", StandardCharsets.UTF_8);

        try (Fixture fixture = confinedFixture(projectRoot)) {
            assertIoErrorOutcome(
                    executeFile(
                            TEST_ROOT.resolve(
                                    "metadata-publication/stage-collision-preserves-target.protos"),
                            fixture.activation()),
                    fixture.activation(),
                    "metadata-publication/stage-collision-preserves-target.protos");
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
    void metadataPublicationExplicitDiscardRemovesStage() throws Exception {
        Files.writeString(
                projectRoot.resolve(".protos.toml.stage"),
                "abandoned\n",
                StandardCharsets.UTF_8);

        assertConfinedTrue("metadata-publication/discard-staging.protos");

        assertTrue(
                Files.notExists(
                        projectRoot.resolve(".protos.toml.stage"), LinkOption.NOFOLLOW_LINKS));
    }

    @Test
    void metadataPublicationInvalidContentCreatesNoStage() throws Exception {
        Files.writeString(projectRoot.resolve("protos.toml"), "old\n", StandardCharsets.UTF_8);

        assertConfinedFailed("metadata-publication/invalid-content-has-no-stage.protos");

        assertFalse(
                Files.exists(
                        projectRoot.resolve(".protos.toml.stage"), LinkOption.NOFOLLOW_LINKS));
        assertEquals(
                "old\n",
                Files.readString(projectRoot.resolve("protos.toml"), StandardCharsets.UTF_8));
    }

    @Test
    void metadataPublicationForbiddenTargetPreservesPreparedStageAndTarget() throws Exception {
        Files.writeString(projectRoot.resolve("secret.txt"), "secret\n", StandardCharsets.UTF_8);

        try (Fixture fixture = confinedFixture(projectRoot)) {
            assertIoErrorOutcome(
                    executeFile(
                            TEST_ROOT.resolve(
                                    "metadata-publication/forbidden-target-preserves-target.protos"),
                            fixture.activation()),
                    fixture.activation(),
                    "metadata-publication/forbidden-target-preserves-target.protos");
        }

        assertEquals(
                "secret\n",
                Files.readString(projectRoot.resolve("secret.txt"), StandardCharsets.UTF_8));
        assertEquals(
                "staged but forbidden\n",
                Files.readString(
                        projectRoot.resolve(".protos.toml.stage"), StandardCharsets.UTF_8));
    }


    @Test
    void resolutionInputLockFileStaleComparisonUsesHeaderOnly() throws Exception {
        Files.writeString(
                projectRoot.resolve("protos.lock"),
                RESOLUTION_INPUT_FRESH_LOCK,
                StandardCharsets.UTF_8);

        try (Fixture fixture = confinedFixture(projectRoot)) {
            ProtosExecutionOutcome fresh =
                    executeFile(
                            TEST_ROOT.resolve("resolution-input/lockfile-fresh.protos"),
                            fixture.activation());
            assertExpected(fresh, "true", "resolution-input/lockfile-fresh.protos");
        }

        // Top-level declarations live in the supplied activation, so the stale
        // case deliberately uses an independent fresh activation.
        try (Fixture fixture = confinedFixture(projectRoot)) {
            ProtosExecutionOutcome stale =
                    executeFile(
                            TEST_ROOT.resolve("resolution-input/lockfile-stale.protos"),
                            fixture.activation());
            assertExpected(stale, "true", "resolution-input/lockfile-stale.protos");
        }
    }


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

    private static ProtosActivation readOnlyMetadataActivation(
            ProtosNioReadOnlyFilesystemBackend backend)
            throws Exception {
        ProtosPrelude prelude = newPackagePrelude();
        ProtosActivation activation = prelude.newModuleActivation();
        ProtosObjectValue rawFilesystem =
                ProtosStandardFilesystemProtocol.createCapability(
                        prelude.bytesPrototypeForRuntime(), activation, backend);
        ProtosFilesystemValue filesystem =
                assertInstanceOf(ProtosFilesystemValue.class, rawFilesystem);
        activation.context().createLocalSlot("filesystem", filesystem);
        return activation;
    }


    @Test
    void contentIdentityProductionCanonicalizerMatchesFrozenPositiveVectors() throws Exception {
        assertContentIdentityVerified(
                contentIdentityRoot("minimal"),
                "beb503347f64442d909b4848e69333a72f4d049d7f5b37c32f453f409641ef04",
                root -> writeContentIdentityFile(root, "protos.toml", new byte[0]));

        assertContentIdentityVerified(
                contentIdentityRoot("ordering"),
                "a4e71028b12d648a10729e5dedf947d8bfbff1e6c8e1b95bb04ac170f5682e67",
                root -> {
                    writeContentIdentityFile(root, "z.protos", asciiBytes("Z"));
                    writeContentIdentityFile(root, "protos.toml", asciiBytes("[package]\n"));
                    writeContentIdentityFile(root, "a.protos", new byte[0]);
                });

        assertContentIdentityVerified(
                contentIdentityRoot("binary"),
                "2de3f9f78fb1355348861e08cb549919d10e4b50f8965f3fc933eac72fff8870",
                root -> {
                    writeContentIdentityFile(root, "Main.protos", asciiBytes("value: 42\n"));
                    writeContentIdentityFile(
                            root,
                            "assets/data.bin",
                            new byte[] {0, 1, 2, (byte) 0xff, 0x7f, (byte) 0x80});
                    writeContentIdentityFile(root, "protos.toml", asciiBytes("id = \"pkg\"\n"));
                });

        assertContentIdentityVerified(
                contentIdentityRoot("varuint-boundaries"),
                "4ae0bad7f7915a5e6db1ad4bb29ee71351fcf812b562b41da34f99b8fd4b8f02",
                root -> {
                    writeContentIdentityFile(root, "protos.toml", new byte[0]);
                    writeContentIdentityFile(
                            root, "a".repeat(128), repeatedBytes((byte) 'Z', 300));
                });

        assertContentIdentityVerified(
                contentIdentityRoot("exact-case-upper"),
                "9afaaf7e2a250fd11b83d12e63cf7e18527c95820d03bff6895e57a51600e450",
                root -> {
                    writeContentIdentityFile(root, "Main.protos", asciiBytes("x"));
                    writeContentIdentityFile(root, "protos.toml", new byte[0]);
                });

        assertContentIdentityVerified(
                contentIdentityRoot("exact-case-lower"),
                "ff22975888d65d4dd9563bd09ca67190c02473bdead0c536faf101cd66f72ec2",
                root -> {
                    writeContentIdentityFile(root, "main.protos", asciiBytes("x"));
                    writeContentIdentityFile(root, "protos.toml", new byte[0]);
                });
    }

    @Test
    void contentIdentityProductionCanonicalizerRejectsInvalidLogicalTrees() throws Exception {
        Path missingManifest = contentIdentityRoot("missing-manifest");
        writeContentIdentityFile(missingManifest, "Main.protos", asciiBytes("x"));
        assertContentIdentityRejectsTree(missingManifest);

        Path invalidCharacter = contentIdentityRoot("invalid-character");
        writeContentIdentityFile(invalidCharacter, "protos.toml", new byte[0]);
        writeContentIdentityFile(invalidCharacter, "bad name", asciiBytes("x"));
        assertContentIdentityRejectsTree(invalidCharacter);

        Path reservedName = contentIdentityRoot("reserved-name");
        writeContentIdentityFile(reservedName, "protos.toml", new byte[0]);
        writeContentIdentityFile(reservedName, "CON.txt", asciiBytes("x"));
        assertContentIdentityRejectsTree(reservedName);
    }

    @Test
    void contentIdentityProductionCanonicalizerRejectsSiblingCaseFoldCollision()
            throws Exception {
        Path root = contentIdentityRoot("case-collision");
        writeContentIdentityFile(root, "protos.toml", new byte[0]);
        Path upper = root.resolve("Parser.protos");
        Path lower = root.resolve("parser.protos");
        Files.write(upper, asciiBytes("a"));
        Files.write(lower, asciiBytes("b"));

        assumeTrue(
                !Files.isSameFile(upper, lower),
                "host filesystem cannot materialize the case-collision vector");
        assertContentIdentityRejectsTree(root);
    }

    @Test
    void contentIdentityProductionCanonicalizerRejectsCapturedLinkWithoutFollowingIt()
            throws Exception {
        Path root = contentIdentityRoot("link");
        writeContentIdentityFile(root, "protos.toml", asciiBytes("x"));

        try {
            Files.createSymbolicLink(root.resolve("alias"), Path.of("protos.toml"));
        } catch (UnsupportedOperationException | IOException | SecurityException unsupported) {
            assumeTrue(false, "host filesystem cannot materialize the symbolic-link vector");
        }

        assertContentIdentityRejectsTree(root);
    }

    @Test
    void contentIdentityVerifierRejectsUnsupportedOrMalformedRecordedIdentity()
            throws Exception {
        Path root = contentIdentityRoot("unsupported");
        writeContentIdentityFile(root, "protos.toml", new byte[0]);

        String valid =
                "beb503347f64442d909b4848e69333a72f4d049d7f5b37c32f453f409641ef04";

        assertContentIdentityRejectsExpected(root, "future-package-tree-v2", "sha256", valid);
        assertContentIdentityRejectsExpected(root, "protos-package-tree-v1", "sha512", valid);
        assertContentIdentityRejectsExpected(root, "protos-package-tree-v1", "sha256", "00");
        assertContentIdentityRejectsExpected(
                root,
                "protos-package-tree-v1",
                "sha256",
                valid.substring(0, 63) + "A");
    }

    @Test
    void contentIdentityRecordShapeSurvivesSuspendingContentRead() throws Exception {
        Path root = contentIdentityRoot("record-shape");
        writeContentIdentityFile(root, "protos.toml", new byte[0]);

        ProtosExecutionOutcome outcome =
                executeContentIdentity(
                        root,
                        "record-shape.protos",
                        Map.of(),
                        true);
        assertExpected(
                outcome,
                "true",
                "content-identity/record-shape.protos");
    }

    @Test
    void contentIdentityVaruintImplementationCoversFrozenArbitraryPrecisionBoundaries()
            throws Exception {
        try (ProtosHostedExecutionTestFixture hosted =
                ProtosHostedExecutionTestFixture.open(newPackagePrelude())) {
            ProtosExecutionOutcome outcome =
                    executeFile(
                            TEST_ROOT.resolve("content-identity/varuint.protos"),
                            hosted.activation());
            assertExpected(outcome, "true", "content-identity/varuint.protos");
        }
    }

    private void assertContentIdentityVerified(
            Path root,
            String expectedHex,
            ContentIdentityTreeBuilder builder)
            throws Exception {
        Files.createDirectories(root);
        builder.build(root);

        ProtosExecutionOutcome outcome =
                executeContentIdentity(
                        root,
                        "digest-verify.protos",
                        Map.of("expectedHex", expectedHex),
                        true);
        assertExpected(outcome, "true", "content-identity/digest-verify.protos");
    }

    private void assertContentIdentityRejectsTree(Path root) throws Exception {
        ProtosExecutionOutcome outcome =
                executeContentIdentity(root, "reject-tree.protos", Map.of(), true);
        assertExpected(outcome, "error", "content-identity/reject-tree.protos");
    }

    private void assertContentIdentityRejectsExpected(
            Path root,
            String method,
            String algorithm,
            String hex)
            throws Exception {
        ProtosExecutionOutcome outcome =
                executeContentIdentity(
                        root,
                        "reject-expected.protos",
                        Map.of(
                                "expectedMethod", method,
                                "expectedAlgorithm", algorithm,
                                "expectedHex", hex),
                        true);
        assertExpected(outcome, "error", "content-identity/reject-expected.protos");
    }

    private static ProtosExecutionOutcome executeContentIdentity(
            Path root,
            String fixture,
            Map<String, String> stringBindings,
            boolean provisionFilesystem)
            throws Exception {
        ProtosPrelude prelude = newPackagePrelude();

        if (!provisionFilesystem) {
            try (ProtosHostedExecutionTestFixture hosted =
                    ProtosHostedExecutionTestFixture.open(prelude)) {
                for (Map.Entry<String, String> binding : stringBindings.entrySet()) {
                    hosted.activation()
                            .context()
                            .createLocalSlot(
                                    binding.getKey(),
                                    new ProtosStringValue(binding.getValue()));
                }
                return executeFile(
                        TEST_ROOT.resolve("content-identity").resolve(fixture),
                        hosted.activation());
            }
        }

        try (ProtosNioReadOnlyTreeFilesystemBackend backend =
                        new ProtosNioReadOnlyTreeFilesystemBackend(root);
                ProtosHostedExecutionTestFixture hosted =
                        ProtosHostedExecutionTestFixture.open(prelude)) {
            assumeTrue(
                    backend.secureConfinementAvailable(),
                    "host provider has no SecureDirectoryStream");

            for (Map.Entry<String, String> binding : stringBindings.entrySet()) {
                hosted.activation()
                        .context()
                        .createLocalSlot(
                                binding.getKey(),
                                new ProtosStringValue(binding.getValue()));
            }
            hosted.installFilesystem("filesystem", backend);

            return executeFile(
                    TEST_ROOT.resolve("content-identity").resolve(fixture),
                    hosted.activation());
        }
    }

    private Path contentIdentityRoot(String name) throws IOException {
        return Files.createDirectories(projectRoot.resolve("content-identity-" + name));
    }

    private static void writeContentIdentityFile(
            Path root,
            String relative,
            byte[] bytes)
            throws IOException {
        Path target = root.resolve(relative);
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.write(target, bytes);
    }

    private static byte[] asciiBytes(String text) {
        return text.getBytes(StandardCharsets.US_ASCII);
    }

    private static byte[] repeatedBytes(byte value, int count) {
        byte[] result = new byte[count];
        java.util.Arrays.fill(result, value);
        return result;
    }

    @FunctionalInterface
    private interface ContentIdentityTreeBuilder {
        void build(Path root) throws Exception;
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

        ProtosHostedExecutionTestFixture hosted =
                ProtosHostedExecutionTestFixture.open(newPackagePrelude());
        try {
            hosted.installFilesystem("filesystem", backend);
            return new Fixture(hosted, backend);
        } catch (RuntimeException | Error failure) {
            hosted.close();
            backend.close();
            throw failure;
        }
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
            ProtosHostedExecutionTestFixture hosted,
            ProtosNioConfinedFilesystemBackend backend)
            implements AutoCloseable {
        ProtosActivation activation() {
            return hosted.activation();
        }

        @Override
        public void close() throws Exception {
            try {
                hosted.close();
            } finally {
                backend.close();
            }
        }
    }

    private static ProtosActivation newPackageActivation() throws Exception {
        return newPackagePrelude().newModuleActivation();
    }

    private static ProtosPrelude newPackagePrelude() throws Exception {
        ProtosStandardLibraryModuleResolver standard =
                new ProtosStandardLibraryModuleResolver(STANDARD_LIBRARY);
        ProtosBundledToolModuleResolver resolver =
                new ProtosBundledToolModuleResolver("package", TOOL_ROOT, (TOOL_ROOT).resolveSibling("shared"), standard);
        return new ProtosCoreBootstrap().bootstrap(CORE, resolver);
    }

    private static ProtosExecutionOutcome execute(
            String source,
            ProtosActivation activation)
            throws Exception {
        Source guestSource =
                Source.newBuilder(
                                ProtosLanguage.ID,
                                source,
                                "<package-tool-java-harness>")
                        .mimeType(ProtosLanguage.MIME_TYPE)
                        .build();

        ProtosProcessRuntime process =
                activation.executionDomain()
                        .currentActorForRuntime()
                        .flatMap(actor -> actor.processForRuntime())
                        .orElse(null);
        if (process != null
                && process.executionHostForRuntime().orElse(null)
                        instanceof ProtosPolyglotProcessContext processContext) {
            return processContext.execute(guestSource, activation);
        }

        try (ProtosPolyglotExecutionContext context =
                ProtosPolyglotExecutionContext.open(
                        InputStream.nullInputStream(),
                        OutputStream.nullOutputStream(),
                        OutputStream.nullOutputStream())) {
            return context.execute(guestSource, activation);
        }
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
