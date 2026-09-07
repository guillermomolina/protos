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

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosFilesystemValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ProtosPackageToolResolutionInputTest {
    private static final Path CORE = Path.of("protos", "lib", "core");
    private static final Path STANDARD_LIBRARY = Path.of("protos", "lib");
    private static final Path TOOL_ROOT = Path.of("protos", "tools", "package");
    private static final Path CASE_ROOT =
            Path.of("protos", "tests", "package-tool", "resolution-input");

    private static final Set<String> READABLE = Set.of("protos.toml", "protos.lock");
    private static final Set<String> WRITABLE =
            Set.of(".protos.toml.stage", ".protos.lock.stage");
    private static final Set<String> MUTABLE =
            Set.of("protos.toml", "protos.lock", ".protos.toml.stage", ".protos.lock.stage");

    private static final String FRESH_LOCK =
            "lock-format 1\n"
                    + "resolver-version 1\n"
                    + "resolution-input protos-resolution-input-v1 sha256:7041a8afff9ffd0879673557d5c88e47b5fa7f577b369165679d8899f382f579\n"
                    + "\n"
                    + "root workspace \"root\"\n";

    @TempDir Path projectRoot;

    @Test
    void protosFixturesOwnResolutionInputBehavior() throws Exception {
        List<String> lines =
                Files.readAllLines(CASE_ROOT.resolve("manifest.tsv"), StandardCharsets.UTF_8);

        for (String line : lines) {
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            String[] fields = line.split("\\t", -1);
            Path fixture = CASE_ROOT.resolve(fields[0]);
            String source = Files.readString(fixture, StandardCharsets.UTF_8);

            switch (fields[1]) {
                case "true" -> {
                    Object result;
                    try {
                        result = run(source);
                    } catch (ProtosSignalException signal) {
                        throw new AssertionError(
                                "unexpected Protos signal in fixture " + fixture, signal);
                    }
                    assertSame(ProtosBooleanValue.TRUE, result, fixture.toString());
                }
                case "error" ->
                        assertThrows(
                                ProtosSignalException.class,
                                () -> run(source),
                                fixture.toString());
                default -> throw new AssertionError("unknown fixture expectation: " + line);
            }
        }
    }

    @Test
    void lockFileStaleComparisonUsesResolutionInputHeaderOnly() throws Exception {
        Files.writeString(projectRoot.resolve("protos.lock"), FRESH_LOCK, StandardCharsets.UTF_8);

        try (Fixture fixture = fixture()) {
            assertSame(
                    ProtosBooleanValue.TRUE,
                    fixture.execute("lockfile-fresh.protos"),
                    "fresh lock comparison");
        }

        // Top-level fixture declarations live in the supplied ProtosActivation.
        // Use a fresh activation for the independent stale case instead of
        // redeclaring Version/LockFile/manifest/root in the first activation.
        try (Fixture fixture = fixture()) {
            assertSame(
                    ProtosBooleanValue.TRUE,
                    fixture.execute("lockfile-stale.protos"),
                    "stale lock comparison");
        }
    }

    private static Object run(String source) throws Exception {
        ProtosStandardLibraryModuleResolver standard =
                new ProtosStandardLibraryModuleResolver(STANDARD_LIBRARY);
        ProtosBundledToolModuleResolver resolver =
                new ProtosBundledToolModuleResolver("package", TOOL_ROOT, standard);
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE, resolver);
        return new ProtosSourceCompiler()
                .compile(source)
                .call(prelude.newModuleActivation());
    }

    private Fixture fixture() throws Exception {
        ProtosStandardLibraryModuleResolver standard =
                new ProtosStandardLibraryModuleResolver(STANDARD_LIBRARY);
        ProtosBundledToolModuleResolver resolver =
                new ProtosBundledToolModuleResolver("package", TOOL_ROOT, standard);
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE, resolver);
        ProtosActivation activation = prelude.newModuleActivation();
        ProtosNioConfinedFilesystemBackend backend =
                new ProtosNioConfinedFilesystemBackend(projectRoot, READABLE, WRITABLE, MUTABLE);
        if (!backend.secureNamespaceConfinementAvailable()) {
            backend.close();
            assumeTrue(false, "host provider has no SecureDirectoryStream");
        }

        ProtosObjectValue rawFilesystem =
                ProtosStandardFilesystemProtocol.createCapability(
                        prelude.bytesPrototypeForRuntime(), activation, backend);
        ProtosFilesystemValue filesystem =
                assertInstanceOf(ProtosFilesystemValue.class, rawFilesystem);
        activation.context().createLocalSlot("filesystem", filesystem);
        return new Fixture(activation, backend);
    }

    private static final class Fixture implements AutoCloseable {
        private final ProtosActivation activation;
        private final ProtosNioConfinedFilesystemBackend backend;

        Fixture(ProtosActivation activation, ProtosNioConfinedFilesystemBackend backend) {
            this.activation = activation;
            this.backend = backend;
        }

        Object execute(String fixture) throws Exception {
            String source =
                    Files.readString(CASE_ROOT.resolve(fixture), StandardCharsets.UTF_8);
            return new ProtosSourceCompiler().compile(source).call(activation);
        }

        @Override
        public void close() throws Exception {
            backend.close();
        }
    }
}
