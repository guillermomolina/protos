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
import org.junit.jupiter.api.Test;

final class ProtosPackageToolExecutionPlanTest {
    private static final Path CORE = Path.of("protos", "lib", "core");
    private static final Path STANDARD_LIBRARY = Path.of("protos", "lib");
    private static final Path TOOL_ROOT = Path.of("protos", "tools", "package");
    private static final Path CASE_ROOT =
            Path.of("protos", "tests", "package-tool", "execution-plan");
    private static final Path CASES = CASE_ROOT.resolve("cases");
    private static final Path FIXTURES = CASE_ROOT.resolve("fixtures");

    @Test
    void protosFixturesOwnWorkspaceExecutionPlanPolicy() throws Exception {
        List<String> lines =
                Files.readAllLines(CASE_ROOT.resolve("manifest.tsv"), StandardCharsets.UTF_8);

        for (String line : lines) {
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }

            String[] fields = line.split("\\t", -1);
            if (fields.length != 3) {
                throw new AssertionError("invalid execution-plan fixture row: " + line);
            }

            Path caseRoot = CASES.resolve(fields[0]);
            String source =
                    Files.readString(FIXTURES.resolve(fields[1]), StandardCharsets.UTF_8);

            try (ProtosNioReadOnlyTreeFilesystemBackend backend =
                    new ProtosNioReadOnlyTreeFilesystemBackend(caseRoot)) {
                assumeTrue(
                        backend.secureConfinementAvailable(),
                        "host provider has no SecureDirectoryStream");

                ProtosStandardLibraryModuleResolver standard =
                        new ProtosStandardLibraryModuleResolver(STANDARD_LIBRARY);
                ProtosBundledToolModuleResolver resolver =
                        new ProtosBundledToolModuleResolver("package", TOOL_ROOT, standard);
                ProtosPrelude prelude =
                        new ProtosCoreBootstrap().bootstrap(CORE, resolver);
                ProtosActivation activation = prelude.newModuleActivation();

                ProtosObjectValue rawFilesystem =
                        ProtosStandardFilesystemProtocol.createCapability(
                                prelude.bytesPrototypeForRuntime(), activation, backend);
                ProtosFilesystemValue filesystem =
                        (ProtosFilesystemValue) rawFilesystem;
                activation.context()
                        .createLocalSlot("projectTreeFilesystem", filesystem);

                switch (fields[2]) {
                    case "true" -> {
                        Object result;
                        try {
                            result =
                                    new ProtosSourceCompiler()
                                            .compile(source)
                                            .call(activation);
                        } catch (ProtosSignalException signal) {
                            throw new AssertionError(
                                    "unexpected Protos signal in case " + fields[0],
                                    signal);
                        }
                        assertSame(
                                ProtosBooleanValue.TRUE,
                                result,
                                fields[0] + "/" + fields[1]);
                    }
                    case "error" ->
                            assertThrows(
                                    ProtosSignalException.class,
                                    () ->
                                            new ProtosSourceCompiler()
                                                    .compile(source)
                                                    .call(activation),
                                    fields[0] + "/" + fields[1]);
                    default ->
                            throw new AssertionError(
                                    "unknown execution-plan expectation: " + fields[2]);
                }
            }
        }
    }
}
