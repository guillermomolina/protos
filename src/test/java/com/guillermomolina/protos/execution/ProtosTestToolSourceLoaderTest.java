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
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosFilesystemValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ProtosTestToolSourceLoaderTest {
    private static final Path CORE = Path.of("protos", "lib", "core");
    private static final Path STANDARD_LIBRARY = Path.of("protos", "lib");
    private static final Path TOOL_ROOT = Path.of("protos", "tools", "test");
    private static final Path FIXTURE =
            Path.of(
                    "protos",
                    "tests",
                    "tooling",
                    "tool002-d3a1-read-source.protos");

    @Test
    void bundledProtosLoaderReadsCompleteUtf8SourceAcrossReadBatchBoundary(
            @TempDir Path corpusRoot) throws Exception {
        String content = "a".repeat(1_100_000) + "\u03c0\ud83d\ude42\n";
        Files.writeString(
                corpusRoot.resolve("large.protos"),
                content,
                StandardCharsets.UTF_8);

        ProtosBundledToolModuleResolver resolver =
                new ProtosBundledToolModuleResolver(
                        "test",
                        TOOL_ROOT,
                        TOOL_ROOT.resolveSibling("shared"),
                        new ProtosStandardLibraryModuleResolver(STANDARD_LIBRARY));
        ProtosPrelude prelude =
                new ProtosCoreBootstrap().bootstrap(CORE, resolver);
        ProtosActivation activation = prelude.newModuleActivation();

        try (ProtosNioReadOnlyTreeFilesystemBackend backend =
                new ProtosNioReadOnlyTreeFilesystemBackend(corpusRoot)) {
            assumeTrue(
                    backend.secureConfinementAvailable(),
                    "host provider has no SecureDirectoryStream");

            ProtosObjectValue rawFilesystem =
                    ProtosStandardFilesystemProtocol.createCapability(
                            prelude.bytesPrototypeForRuntime(),
                            activation,
                            backend);
            ProtosFilesystemValue filesystem =
                    assertInstanceOf(ProtosFilesystemValue.class, rawFilesystem);
            activation.context().createLocalSlot("filesystem", filesystem);

            ProtosExecutionOutcome outcome =
                    ProtosRootTaskExecution.execute(
                            new ProtosSourceCompiler()
                                    .compile(
                                            Files.readString(
                                                    FIXTURE,
                                                    StandardCharsets.UTF_8)),
                            activation);

            assertEquals(
                    ProtosExecutionOutcome.State.COMPLETED,
                    outcome.state(),
                    () ->
                            "Protos source-loader fixture failed: "
                                    + outcome.error());
            assertSame(ProtosBooleanValue.TRUE, outcome.value());
        }
    }
}
