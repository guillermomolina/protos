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
package com.guillermomolina.protos.conformance;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.guillermomolina.protos.execution.ProtosCoreBootstrap;
import com.guillermomolina.protos.execution.ProtosExecutionOutcome;
import com.guillermomolina.protos.execution.ProtosNioReadOnlyTreeFilesystemBackend;
import com.guillermomolina.protos.execution.ProtosRootTaskExecution;
import com.guillermomolina.protos.execution.ProtosSourceCompiler;
import com.guillermomolina.protos.execution.ProtosStandardFilesystemProtocol;
import com.guillermomolina.protos.execution.ProtosStandardLibraryModuleResolver;
import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosCoreErrors;
import com.guillermomolina.protos.runtime.ProtosFilesystemValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * LIB004-A1 integration harness.
 *
 * <p>The behavior assertions live in Protos source. Java is restricted to provisioning the
 * confined Filesystem/standard-library resolver and inspecting the host fixture or exact Core
 * Error category where that boundary is not source-constructible.
 */
final class ProtosFilesystemLibraryConformanceTest {
    private static final Path CORE = Path.of("protos", "lib", "core");
    private static final Path STANDARD_LIBRARY = Path.of("protos", "lib");
    private static final Path CASE_ROOT =
            Path.of("protos", "tests", "conformance", "library", "io");

    @TempDir Path authorityRoot;

    @Test
    void readAllBytesReturnsFreshFutureAndFreshOpenBytesForEmptyFile() throws Exception {
        Files.write(authorityRoot.resolve("empty.bin"), new byte[0]);

        try (Fixture fixture = fixture()) {
            assertSame(
                    ProtosBooleanValue.TRUE,
                    execute("read-all-bytes-empty.protos", fixture.activation()));
        }
    }

    @Test
    void readAllBytesPreservesOrderedContentAcrossMultipleReadWindows() throws Exception {
        byte[] content = new byte[131089];
        Arrays.fill(content, 0, 65536, (byte) 65);
        Arrays.fill(content, 65536, 131072, (byte) 66);
        Arrays.fill(content, 131072, content.length, (byte) 67);
        Files.write(authorityRoot.resolve("multi.bin"), content);

        try (Fixture fixture = fixture()) {
            assertSame(
                    ProtosBooleanValue.TRUE,
                    execute("read-all-bytes-multichunk.protos", fixture.activation()));
        }
    }

    @Test
    void readAllBytesPropagatesOpenFailureAsTheExistingCoreIoError() throws Exception {
        try (Fixture fixture = fixture()) {
            ProtosSignalException signal =
                    assertThrows(
                            ProtosSignalException.class,
                            () ->
                                    execute(
                                            "read-all-bytes-open-error.protos",
                                            fixture.activation()));

            assertSame(
                    ProtosCoreErrors.prototype(
                            fixture.activation(), ProtosCoreErrors.StandardError.I_O_ERROR),
                    signal.error().parent().orElseThrow());
        }
    }

    private Fixture fixture() throws Exception {
        ProtosPrelude prelude =
                new ProtosCoreBootstrap()
                        .bootstrap(
                                CORE,
                                new ProtosStandardLibraryModuleResolver(STANDARD_LIBRARY));
        ProtosActivation activation = prelude.newModuleActivation();
        ProtosNioReadOnlyTreeFilesystemBackend backend =
                new ProtosNioReadOnlyTreeFilesystemBackend(authorityRoot);
        if (!backend.secureConfinementAvailable()) {
            backend.close();
            assumeTrue(false, "host provider has no SecureDirectoryStream");
        }

        ProtosObjectValue rawFilesystem =
                ProtosStandardFilesystemProtocol.createCapability(
                        prelude.bytesPrototypeForRuntime(), activation, backend);
        ProtosFilesystemValue filesystem =
                org.junit.jupiter.api.Assertions.assertInstanceOf(
                        ProtosFilesystemValue.class, rawFilesystem);
        activation.context().createLocalSlot("filesystem", filesystem);
        return new Fixture(activation, backend);
    }

    private static Object execute(String file, ProtosActivation activation) throws IOException {
        String source =
                Files.readString(CASE_ROOT.resolve(file), StandardCharsets.UTF_8);
        ProtosExecutionOutcome outcome =
                ProtosRootTaskExecution.execute(
                        new ProtosSourceCompiler().compile(source), activation);

        return switch (outcome.state()) {
            case COMPLETED -> outcome.value();
            case FAILED -> throw new ProtosSignalException(outcome.error());
            case CANCELLED -> throw new AssertionError(
                    "LIB004-A1 conformance root unexpectedly cancelled");
        };
    }

    private record Fixture(
            ProtosActivation activation,
            ProtosNioReadOnlyTreeFilesystemBackend backend)
            implements AutoCloseable {
        @Override
        public void close() throws IOException {
            backend.close();
        }
    }
}
