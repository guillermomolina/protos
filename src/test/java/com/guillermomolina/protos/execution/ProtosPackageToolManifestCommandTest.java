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

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosFilesystemValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ProtosPackageToolManifestCommandTest {
    private static final Path CORE = Path.of("protos", "lib", "core");
    private static final Path STANDARD_LIBRARY = Path.of("protos", "lib");
    private static final Path TOOL_ROOT = Path.of("protos", "tools", "package");
    private static final Path CASE_ROOT =
            Path.of("protos", "tests", "package-tool", "manifest-command");

    private static final Set<String> READABLE = Set.of("protos.toml", "protos.lock");
    private static final Set<String> WRITABLE =
            Set.of(".protos.toml.stage", ".protos.lock.stage");
    private static final Set<String> MUTABLE =
            Set.of("protos.toml", "protos.lock", ".protos.toml.stage", ".protos.lock.stage");

    @TempDir Path projectRoot;

    @Test
    void validManifestIsReadDecodedValidatedAndReportedByProtosPolicy() throws Exception {
        Files.writeString(
                projectRoot.resolve("protos.toml"),
                "manifest-version = 1\n[package]\nid = \"pkg\"\nversion = \"1.0.0\"\n",
                StandardCharsets.UTF_8);
        assertCase("valid-minimal.protos");
    }

    @Test
    void invalidSchemaProducesTheProtosOwnedDiagnostic() throws Exception {
        Files.writeString(
                projectRoot.resolve("protos.toml"),
                "manifest-version = 1\n[package]\nid = \"pkg\"\n",
                StandardCharsets.UTF_8);
        assertCase("invalid-schema-diagnostic.protos");
    }

    @Test
    void missingManifestProducesTheProtosOwnedReadDiagnostic() throws Exception {
        assertCase("missing-manifest-diagnostic.protos");
    }

    @Test
    void invalidUtf8ProducesTheProtosOwnedReadDiagnostic() throws Exception {
        Files.write(projectRoot.resolve("protos.toml"), new byte[] {(byte) 0xc3, 0x28});
        assertCase("invalid-utf8-diagnostic.protos");
    }

    @Test
    void manifestReadContinuesAcrossMultipleTextReaderChunks() throws Exception {
        StringBuilder source =
                new StringBuilder(
                        "manifest-version = 1\n[package]\nid = \"large\"\nversion = \"1.0.0\"\n[exports]\n");
        for (int i = 0; i < 700; i++) {
            source.append("Export").append(i).append(" = \"module/")
                    .append(i).append("\"\n");
        }
        Files.writeString(projectRoot.resolve("protos.toml"), source, StandardCharsets.UTF_8);
        assertCase("multichunk-read.protos");
    }

    private void assertCase(String fixture) throws Exception {
        try (Fixture runtime = fixture()) {
            assertSame(ProtosBooleanValue.TRUE, runtime.execute(fixture));
        }
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
            String source = Files.readString(CASE_ROOT.resolve(fixture), StandardCharsets.UTF_8);
            return new ProtosSourceCompiler().compile(source).call(activation);
        }

        @Override
        public void close() throws Exception {
            backend.close();
        }
    }
}
