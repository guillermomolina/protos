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
import com.guillermomolina.protos.runtime.modules.ProtosModuleKey;
import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosCoreErrors;
import com.guillermomolina.protos.runtime.io.ProtosFilesystemValue;
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
import java.util.ArrayList;
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
abstract class ProtosPackageToolProtosTestSupport {
    protected static final Path CORE = Path.of("protos", "lib", "core");
    protected static final Path STANDARD_LIBRARY = Path.of("protos", "lib");
    protected static final Path TOOL_ROOT = Path.of("protos", "tools", "package");
    protected static final Path TEST_ROOT = Path.of("protos", "tests", "package-tool");

    protected static final Set<String> READABLE = Set.of("package.toml", "protos.lock");
    protected static final Set<String> WRITABLE =
            Set.of(".package.toml.stage", ".protos.lock.stage");
    protected static final Set<String> MUTABLE =
            Set.of("package.toml", "protos.lock", ".package.toml.stage", ".protos.lock.stage");

    protected static final String CANONICAL_LOCK =
            "lock-format 1\n"
                    + "resolver-version 1\n"
                    + "resolution-input protos-resolution-input-v1 sha256:0123456789abcdef\n"
                    + "\n"
                    + "root workspace \"root\"\n"
                    + "registry-node registry \"pkg\" \"1.0.0\" locator \"pkg\" authority \"public\" content protos-package-tree-v2 sha256:aaaa\n"
                    + "dependency workspace \"root\" alias \"dep\" target registry \"pkg\" \"1.0.0\"\n";

    protected static final String NONCANONICAL_LOCK =
            "lock-format 1\n"
                    + "resolver-version 1\n"
                    + "resolution-input protos-resolution-input-v1 sha256:0123456789abcdef\n"
                    + "\n"
                    + "dependency workspace \"root\" alias \"dep\" target registry \"pkg\" \"1.0.0\"\n"
                    + "registry-node registry \"pkg\" \"1.0.0\" locator \"pkg\" authority \"public\" content protos-package-tree-v2 sha256:aaaa\n"
                    + "root workspace \"root\"\n";


    @TempDir Path projectRoot;

    protected static ProtosActivation readOnlyMetadataActivation(
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


    protected void assertContentIdentityVerified(
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

    protected void assertContentIdentityRejectsTree(Path root) throws Exception {
        ProtosExecutionOutcome outcome =
                executeContentIdentity(root, "reject-tree.protos", Map.of(), true);
        assertExpected(outcome, "error", "content-identity/reject-tree.protos");
    }

    protected void assertContentIdentityRejectsExpected(
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

    protected static ProtosExecutionOutcome executeContentIdentity(
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

        // PLAT012/D171: the fixture receives a host-captured read-only Filesystem, never a guest
        // capture selector; ContentIdentity sees only the retained open/entries operations.
        assumeSecureTreeConfinement(root);
        try (ProtosCapturedFilesystemCustody custody =
                        ProtosCapturedFilesystemCustody.captureSelectedRoot(root);
                ProtosHostedExecutionTestFixture hosted =
                        ProtosHostedExecutionTestFixture.open(prelude)) {
            for (Map.Entry<String, String> binding : stringBindings.entrySet()) {
                hosted.activation()
                        .context()
                        .createLocalSlot(
                                binding.getKey(),
                                new ProtosStringValue(binding.getValue()));
            }
            hosted.activation()
                    .context()
                    .createLocalSlot("filesystem", custody.materialize(hosted.activation()));

            return executeFile(
                    TEST_ROOT.resolve("content-identity").resolve(fixture),
                    hosted.activation());
        }
    }

    /**
     * Executes one F2E3B execution-plan fixture with the live read-only project tree as
     * {@code projectTreeFilesystem} and one host-captured read-only Filesystem per
     * {@code external/<packageId>} directory, bound as {@code externalCaptures} records
     * ({@code packageId}, {@code filesystem}).
     */
    protected static ProtosExecutionOutcome executeExternalCaptureFixture(
            Path root, String fixture) throws Exception {
        assumeSecureTreeConfinement(root);
        ArrayList<ProtosCapturedFilesystemCustody> custodies = new ArrayList<>();
        try (ProtosNioReadOnlyTreeFilesystemBackend projectTree =
                        new ProtosNioReadOnlyTreeFilesystemBackend(root);
                ProtosHostedExecutionTestFixture hosted =
                        ProtosHostedExecutionTestFixture.open(SharedExecutionPlanCore.PRELUDE)) {
            hosted.installFilesystem("projectTreeFilesystem", projectTree);

            ArrayList<ProtosObjectValue> captures = new ArrayList<>();
            Path external = root.resolve("external");
            if (Files.isDirectory(external, LinkOption.NOFOLLOW_LINKS)) {
                List<Path> packageRoots;
                try (var listing = Files.list(external)) {
                    packageRoots = listing.sorted().toList();
                }
                for (Path packageRoot : packageRoots) {
                    ProtosCapturedFilesystemCustody custody =
                            ProtosCapturedFilesystemCustody.captureSelectedRoot(packageRoot);
                    custodies.add(custody);
                    ProtosObjectValue capture =
                            new ProtosObjectValue(ProtosObjectValue.rootObject());
                    capture.createLocalSlot(
                            "packageId",
                            new ProtosStringValue(packageRoot.getFileName().toString()));
                    capture.createLocalSlot(
                            "filesystem", custody.materialize(hosted.activation()));
                    capture.freeze();
                    captures.add(capture);
                }
            }
            hosted.activation()
                    .context()
                    .createLocalSlot(
                            "externalCaptures",
                            hosted.activation().prelude().orElseThrow().newArray(captures));

            return executeFile(
                    TEST_ROOT.resolve("execution-plan").resolve(fixture),
                    hosted.activation());
        } finally {
            for (ProtosCapturedFilesystemCustody custody : custodies) {
                custody.close();
            }
        }
    }

    private static void assumeSecureTreeConfinement(Path root) throws IOException {
        try (ProtosNioReadOnlyTreeFilesystemBackend probe =
                new ProtosNioReadOnlyTreeFilesystemBackend(root)) {
            assumeTrue(
                    probe.secureConfinementAvailable(),
                    "host provider has no SecureDirectoryStream");
        }
    }

    protected Path contentIdentityRoot(String name) throws IOException {
        return Files.createDirectories(projectRoot.resolve("content-identity-" + name));
    }

    protected static void writeContentIdentityFile(
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

    protected static byte[] asciiBytes(String text) {
        return text.getBytes(StandardCharsets.US_ASCII);
    }

    protected static byte[] repeatedBytes(byte value, int count) {
        byte[] result = new byte[count];
        java.util.Arrays.fill(result, value);
        return result;
    }

    @FunctionalInterface
    protected interface ContentIdentityTreeBuilder {
        void build(Path root) throws Exception;
    }

    protected void assertConfinedTrue(String relative) throws Exception {
        try (Fixture fixture = confinedFixture(projectRoot)) {
            ProtosExecutionOutcome outcome =
                    executeFile(TEST_ROOT.resolve(relative), fixture.activation());
            assertExpected(outcome, "true", relative);
        }
    }

    protected void assertConfinedFailed(String relative) throws Exception {
        try (Fixture fixture = confinedFixture(projectRoot)) {
            ProtosExecutionOutcome outcome =
                    executeFile(TEST_ROOT.resolve(relative), fixture.activation());
            assertExpected(outcome, "error", relative);
        }
    }

    protected static Fixture confinedFixture(Path root) throws Exception {
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

    protected static ProtosExecutionOutcome executeFile(
            Path source,
            ProtosActivation activation)
            throws Exception {
        // D242: these fixtures exercise private package-tool SOURCE helpers. Resolve
        // ./Helper.protos under the package tool root, but preserve every host grant
        // already installed on the fixture's original execution Context.
        ProtosActivation sourceActivation =
                activation.prelude().orElseThrow().newModuleActivation(
                        activation.actorModuleState(),
                        ProtosBundledToolModuleResolver.toolSourceKey(
                                "package", "I151ToolHarness"),
                        activation.context(),
                        activation.executionDomain());
        return execute(Files.readString(source, StandardCharsets.UTF_8), sourceActivation);
    }

    protected static void assertIoErrorOutcome(
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

    protected record Fixture(
            ProtosHostedExecutionTestFixture hosted,
            ProtosNioConfinedFilesystemBackend backend)
            implements AutoCloseable {
        ProtosActivation activation() {
            return hosted.activation();
        }

        @Override
        public void close() throws IOException {
            try {
                hosted.close();
            } finally {
                backend.close();
            }
        }
    }

    protected static ProtosActivation newPackageActivation() throws Exception {
        return newPackagePrelude().newModuleActivation();
    }

    protected static ProtosPrelude newPackagePrelude() throws Exception {
        ProtosStandardLibraryModuleResolver standard =
                new ProtosStandardLibraryModuleResolver(STANDARD_LIBRARY);
        // I151_B60_JAVA_PACKAGE_SOURCE_ENTRANCES:
        // Only this host-created fixture SOURCE may import the exact
        // Package implementation entrances selected for TOOL009.
        // All other imports retain the bundled-tool resolver's custody rules.
        ProtosBundledToolModuleResolver toolResolver =
                new ProtosBundledToolModuleResolver(
                        "package",
                        TOOL_ROOT,
                        TOOL_ROOT.resolveSibling("shared"),
                        standard);

        Set<String> entrances = Set.of(
                        "tool-source:package/ContentIdentity.protos",
                        "tool-source:package/DependencyConstraint.protos",
                        "tool-source:package/ExecutionPlan.protos",
                        "tool-source:package/FreshVersionSelection.protos",
                        "tool-source:package/LockDocument.protos",
                        "tool-source:package/LockFile.protos",
                        "tool-source:package/LockSyntax.protos",
                        "tool-source:package/ManifestCommand.protos",
                        "tool-source:package/ManifestSchemaV1.protos",
                        "tool-source:package/MetadataPublication.protos",
                        "tool-source:package/ProjectDocument.protos",
                        "tool-source:package/ProjectFile.protos",
                        "tool-source:package/ProjectMetadata.protos",
                        "tool-source:package/ReleaseVersion.protos",
                        "tool-source:package/ResolutionInput.protos",
                        "tool-source:package/ResolutionRoot.protos",
                        "tool-source:package/RetainedVersionSelection.protos",
                        "tool-source:package/RuntimeNames.protos"
        );

        ProtosModuleKey harnessSource =
                ProtosBundledToolModuleResolver.toolSourceKey(
                        "package", "I151ToolHarness");

        ProtosModuleResolver resolver =
                new ProtosModuleResolver() {
                    @Override
                    public ProtosModuleKey resolve(
                            String specifier,
                            java.util.Optional<ProtosModuleKey> importer)
                            throws Exception {
                        if (entrances.contains(specifier)) {
                            if (importer.isEmpty()
                                    || !importer.orElseThrow().equals(harnessSource)) {
                                throw new IOException(
                                        "Package SOURCE entrance requires fixture importer");
                            }

                            String prefix = "tool-source:package/";
                            String logicalName =
                                    specifier.substring(
                                            prefix.length(),
                                            specifier.length() - ".protos".length());

                            return ProtosBundledToolModuleResolver.toolSourceKey(
                                    "package", logicalName);
                        }

                        return toolResolver.resolve(specifier, importer);
                    }

                    @Override
                    public ProtosModuleSource loadSource(ProtosModuleKey key)
                            throws Exception {
                        return toolResolver.loadSource(key);
                    }
                };

        return new ProtosCoreBootstrap().bootstrap(CORE, resolver);
    }

    // Holder for the immutable Package-flavored resolver configuration and its bootstrapped
    // Core Prelude, initialized exactly once and used only by executeExternalCaptureFixture.
    // The resolvers retain only fixed roots and the Prelude's shared standard graph is frozen.
    // It holds no project-tree backend, captured custody, hosted fixture, Process, activation,
    // or Actor module state: every invocation still opens its own hosted Process and fresh
    // activation and materializes its own captures into that activation.
    private static final class SharedExecutionPlanCore {
        private static final ProtosPrelude PRELUDE = create();

        private static ProtosPrelude create() {
            try {
                return newPackagePrelude();
            } catch (Exception failure) {
                throw new ExceptionInInitializerError(failure);
            }
        }
    }

    protected static ProtosExecutionOutcome execute(
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

    protected static void assertExpected(
            ProtosExecutionOutcome outcome,
            String expected,
            String label) {
        switch (expected) {
            case "true" -> {
                assertEquals(
                        ProtosExecutionOutcome.State.COMPLETED,
                        outcome.state(),
                        () -> label + " guest failure slots="
                                + (outcome.state() == ProtosExecutionOutcome.State.FAILED
                                        ? outcome.error().localSlotsSnapshot()
                                        : "<not failed>"));
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
