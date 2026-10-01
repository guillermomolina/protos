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

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosFileFlow;
import com.guillermomolina.protos.runtime.ProtosFilesystemValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

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
    void bundledProtosLoaderReadsCompleteUtf8SourceAcrossReadBatchBoundary()
            throws Exception {
        /*
         * Runner.readSource issues 16 reads per batch. A backend is allowed to
         * return any non-empty prefix up to maxBytes, so 2-byte short reads let
         * this regression cross the same batch boundary without a 1 MiB test
         * payload. The 31-byte ASCII prefix also splits the following UTF-8 pi
         * scalar across two File.read results.
         */
        byte[] content =
                ("a".repeat(31) + "\u03c0\ud83d\ude42\n")
                        .getBytes(StandardCharsets.UTF_8);

        ProtosBundledToolModuleResolver resolver =
                new ProtosBundledToolModuleResolver(
                        "test",
                        TOOL_ROOT,
                        TOOL_ROOT.resolveSibling("shared"),
                        new ProtosStandardLibraryModuleResolver(STANDARD_LIBRARY));
        ProtosPrelude prelude =
                new ProtosCoreBootstrap().bootstrap(CORE, resolver);
        ProtosActivation activation = prelude.newModuleActivation();

        ProtosStandardFilesystemProtocol.Backend backend =
                (path, options, completion) -> {
                    if (!path.components().equals(List.of("large.protos"))) {
                        completion.failed();
                        return () -> {};
                    }

                    ShortReadResource resource =
                            new ShortReadResource(content);
                    completion.succeeded(
                            resource,
                            new ProtosFileFlow.Capabilities(
                                    true,
                                    false,
                                    false,
                                    false,
                                    false,
                                    false),
                            () -> {});
                    return () -> {};
                };

        ProtosObjectValue rawFilesystem =
                ProtosStandardFilesystemProtocol.createCapability(
                        prelude.bytesPrototypeForRuntime(),
                        activation,
                        backend);
        ProtosFilesystemValue filesystem =
                assertInstanceOf(ProtosFilesystemValue.class, rawFilesystem);
        activation.context().createLocalSlot("filesystem", filesystem);

        ProtosExecutionOutcome outcome =
                ProtosTestExecutionSupport.execute(
                        Files.readString(
                                FIXTURE,
                                StandardCharsets.UTF_8),
                        activation);

        assertEquals(
                ProtosExecutionOutcome.State.COMPLETED,
                outcome.state(),
                () ->
                        "Protos source-loader fixture failed: "
                                + outcome.error());
        assertSame(ProtosBooleanValue.TRUE, outcome.value());
    }

    private static final class ShortReadResource
            implements ProtosFileFlow.ReadableResource {
        private final byte[] content;

        private ShortReadResource(byte[] content) {
            this.content = content.clone();
        }

        @Override
        public ProtosFileFlow.Cancellation readAt(
                BigInteger position,
                int maxBytes,
                ProtosFileFlow.ReadCompletion completion) {
            int start = position.intValueExact();
            if (start >= content.length) {
                completion.eof();
                return () -> {};
            }

            int end =
                    Math.min(
                            content.length,
                            start + Math.min(maxBytes, 2));
            completion.data(Arrays.copyOfRange(content, start, end));
            return () -> {};
        }

        @Override
        public void close(ProtosFileFlow.CloseCompletion completion) {
            completion.succeeded();
        }
    }
}
