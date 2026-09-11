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

final class ProtosTestToolPackageFullPlanExecutionTest {
    private static final Path CORE = Path.of("protos", "lib", "core");
    private static final Path STANDARD_LIBRARY = Path.of("protos", "lib");
    private static final Path TEST_TOOL_ROOT = Path.of("protos", "tools", "test");
    private static final Path PACKAGE_TOOL_ROOT = Path.of("protos", "tools", "package");
    private static final Path PACKAGE_TOML_CORPUS_ROOT =
            Path.of("protos", "tests", "package-tool", "toml-syntax");
    private static final Path FIXTURE =
            Path.of(
                    "protos",
                    "tests",
                    "tooling",
                    "tool002-e2b2-package-toml-full-plan.protos");

    @Test
    void bundledProtosRunnerOwnsCompletePackageTomlCorpusExecution()
            throws Exception {
        ProtosPrelude testPrelude =
                new ProtosCoreBootstrap()
                        .bootstrap(
                                CORE,
                                new ProtosBundledToolModuleResolver(
                                        "test",
                                        TEST_TOOL_ROOT,
                                        new ProtosStandardLibraryModuleResolver(
                                                STANDARD_LIBRARY)));
        ProtosPrelude packagePrelude =
                new ProtosCoreBootstrap()
                        .bootstrap(
                                CORE,
                                new ProtosBundledToolModuleResolver(
                                        "package",
                                        PACKAGE_TOOL_ROOT, (PACKAGE_TOOL_ROOT).resolveSibling("shared"),
                                        new ProtosStandardLibraryModuleResolver(
                                                STANDARD_LIBRARY)));
        ProtosActivation activation = testPrelude.newModuleActivation();

        ProtosExactExecutionFacility.install(
                activation,
                "packageExecution",
                packagePrelude);

        try (ProtosNioReadOnlyTreeFilesystemBackend backend =
                new ProtosNioReadOnlyTreeFilesystemBackend(
                        PACKAGE_TOML_CORPUS_ROOT)) {
            assumeTrue(
                    backend.secureConfinementAvailable(),
                    "host provider has no SecureDirectoryStream");

            ProtosObjectValue rawFilesystem =
                    ProtosStandardFilesystemProtocol.createCapability(
                            testPrelude.bytesPrototypeForRuntime(),
                            activation,
                            backend);
            ProtosFilesystemValue filesystem =
                    assertInstanceOf(
                            ProtosFilesystemValue.class,
                            rawFilesystem);
            activation.context().createLocalSlot(
                    "packageTomlFilesystem",
                    filesystem);

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
                            "E2B2 Protos full-corpus fixture did not complete; state="
                                    + outcome.state()
                                    + ", error="
                                    + outcome.error());
            assertSame(ProtosBooleanValue.TRUE, outcome.value());
        }
    }
}
