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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosFilesystemValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.guillermomolina.protos.runtime.ProtosStringValue;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ProtosNioReadOnlyTreeFilesystemBackendTest {
    private static final Path CORE = Path.of("protos", "lib", "core");

    @TempDir Path authorityRoot;

    @Test
    void nestedRelativeFilesAreReadableInsidePinnedTree() throws Exception {
        Files.createDirectories(authorityRoot.resolve("nested"));
        Files.writeString(
                authorityRoot.resolve("nested").resolve("case.protos"),
                "42",
                StandardCharsets.UTF_8);

        try (ProtosNioReadOnlyTreeFilesystemBackend backend =
                new ProtosNioReadOnlyTreeFilesystemBackend(authorityRoot)) {
            assumeTrue(
                    backend.secureConfinementAvailable(),
                    "host provider has no SecureDirectoryStream");

            Fixture fixture = fixture(backend);
            Object result =
                    new ProtosSourceCompiler()
                            .compile(
                                    "file: filesystem.open("
                                            + "Path.relative().child(\"nested\").child(\"case.protos\"))"
                                            + ".value()\n"
                                            + "reader: TextReader.owning(file, Encoding.UTF8)\n"
                                            + "text: reader.readText().value()\n"
                                            + "reader.close().value()\n"
                                            + "text")
                            .call(fixture.activation());

            assertEquals(
                    "42",
                    assertInstanceOf(ProtosStringValue.class, result).value());
        }
    }

    @Test
    void textReaderCanConsumeAFileAcrossMultipleBackendReadChunks()
            throws Exception {
        String content = "x".repeat(20000);
        Files.writeString(
                authorityRoot.resolve("large.txt"),
                content,
                StandardCharsets.UTF_8);

        try (ProtosNioReadOnlyTreeFilesystemBackend backend =
                new ProtosNioReadOnlyTreeFilesystemBackend(authorityRoot)) {
            assumeTrue(
                    backend.secureConfinementAvailable(),
                    "host provider has no SecureDirectoryStream");

            Fixture fixture = fixture(backend);
            String source =
                    "file: filesystem.open(Path.relative().child(\"large.txt\")).value()\n"
                            + "reader: TextReader.owning(file, Encoding.UTF8)\n"
                            + "first: reader.readText().value()\n"
                            + "second: reader.readText().value()\n"
                            + "third: reader.readText().value()\n"
                            + "fourth: reader.readText().value()\n"
                            + "reader.close().value()\n"
                            + "(first.size() + second.size() + third.size() == 20000) && "
                            + "(fourth === null)";

            ProtosExecutionOutcome outcome =
                    ProtosRootTaskExecution.execute(
                            new ProtosSourceCompiler().compile(source),
                            fixture.activation());

            assertEquals(
                    ProtosExecutionOutcome.State.COMPLETED,
                    outcome.state(),
                    () ->
                            "multi-read execution failed: "
                                    + outcome.error());
            assertSame(
                    com.guillermomolina.protos.runtime.ProtosBooleanValue.TRUE,
                    outcome.value());
        }
    }

    @Test
    void intermediateSymlinkCannotEscapeAuthorityTree() throws Exception {
        Path outside = Files.createTempDirectory("protos-test-tree-outside-");
        try {
            Files.writeString(
                    outside.resolve("secret.txt"),
                    "secret",
                    StandardCharsets.UTF_8);
            try {
                Files.createSymbolicLink(authorityRoot.resolve("escape"), outside);
            } catch (UnsupportedOperationException | IOException | SecurityException unavailable) {
                assumeTrue(false, "host cannot create the symlink confinement fixture");
            }

            try (ProtosNioReadOnlyTreeFilesystemBackend backend =
                    new ProtosNioReadOnlyTreeFilesystemBackend(authorityRoot)) {
                assumeTrue(
                        backend.secureConfinementAvailable(),
                        "host provider has no SecureDirectoryStream");

                Fixture fixture = fixture(backend);
                assertThrows(
                        ProtosSignalException.class,
                        () ->
                                new ProtosSourceCompiler()
                                        .compile(
                                                "filesystem.open("
                                                        + "Path.relative().child(\"escape\")"
                                                        + ".child(\"secret.txt\"))"
                                                        + ".value()")
                                        .call(fixture.activation()));
            }
        } finally {
            Files.deleteIfExists(outside.resolve("secret.txt"));
            Files.deleteIfExists(outside);
        }
    }

    private static Fixture fixture(
            ProtosNioReadOnlyTreeFilesystemBackend backend)
            throws Exception {
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
        ProtosActivation activation = prelude.newModuleActivation();
        ProtosObjectValue rawFilesystem =
                ProtosStandardFilesystemProtocol.createCapability(
                        prelude.bytesPrototypeForRuntime(),
                        activation,
                        backend);
        ProtosFilesystemValue filesystem =
                assertInstanceOf(ProtosFilesystemValue.class, rawFilesystem);
        activation.context().createLocalSlot("filesystem", filesystem);
        return new Fixture(activation);
    }

    private record Fixture(ProtosActivation activation) {}
}
