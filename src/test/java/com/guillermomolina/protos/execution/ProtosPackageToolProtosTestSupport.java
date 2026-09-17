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
abstract class ProtosPackageToolProtosTestSupport {
    protected static final Path CORE = Path.of("protos", "lib", "core");
    protected static final Path STANDARD_LIBRARY = Path.of("protos", "lib");
    protected static final Path TOOL_ROOT = Path.of("protos", "tools", "package");
    protected static final Path TEST_ROOT = Path.of("protos", "tests", "package-tool");

    protected static final Set<String> READABLE = Set.of("protos.toml", "protos.lock");
    protected static final Set<String> WRITABLE =
            Set.of(".protos.toml.stage", ".protos.lock.stage");
    protected static final Set<String> MUTABLE =
            Set.of("protos.toml", "protos.lock", ".protos.toml.stage", ".protos.lock.stage");

    protected static final String CANONICAL_LOCK =
            "lock-format 1\n"
                    + "resolver-version 1\n"
                    + "resolution-input protos-resolution-input-v1 sha256:0123456789abcdef\n"
                    + "\n"
                    + "root workspace \"root\"\n"
                    + "registry-node registry \"pkg\" \"1.0.0\" locator \"pkg\" authority \"public\" content protos-package-tree-v1 sha256:aaaa\n"
                    + "dependency workspace \"root\" alias \"dep\" target registry \"pkg\" \"1.0.0\"\n";

    protected static final String NONCANONICAL_LOCK =
            "lock-format 1\n"
                    + "resolver-version 1\n"
                    + "resolution-input protos-resolution-input-v1 sha256:0123456789abcdef\n"
                    + "\n"
                    + "dependency workspace \"root\" alias \"dep\" target registry \"pkg\" \"1.0.0\"\n"
                    + "registry-node registry \"pkg\" \"1.0.0\" locator \"pkg\" authority \"public\" content protos-package-tree-v1 sha256:aaaa\n"
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
        return execute(Files.readString(source, StandardCharsets.UTF_8), activation);
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
        public void close() throws Exception {
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
        ProtosBundledToolModuleResolver resolver =
                new ProtosBundledToolModuleResolver("package", TOOL_ROOT, (TOOL_ROOT).resolveSibling("shared"), standard);
        return new ProtosCoreBootstrap().bootstrap(CORE, resolver);
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
