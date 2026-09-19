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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ProtosTestToolDiscoveryImportedModuleTest {
    private static final Path CORE = Path.of("protos", "lib", "core");
    private static final Path STANDARD_LIBRARY = Path.of("protos", "lib");
    private static final Path TOOL_ROOT = Path.of("protos", "tools", "test");
    private static final Path SHARED_ROOT = Path.of("protos", "tools", "shared");

    @Test
    void importedSuiteProjectsAuthorityFreeOrderedSignatureWithoutRunningBodies(
            @TempDir Path root) throws Exception {
        Path entry = root.resolve("entry.protos");
        Path suite = root.resolve("suite.protos");

        String entrySource =
                """
                Suite: import("./suite.protos")
                Suite.hasSlot("tests")
                """;

        String suiteSource =
                """
                TestValue: import("std:test/Test")

                missing: (body) => {
                    failed: false

                    Error.handle(
                        () => {
                            body()
                            null
                        },
                        (error) => {
                            failed = true
                            null
                        }
                    )

                    failed
                }

                authorityFree:
                    missing(() => { process }) &&
                    missing(() => { filesystem }) &&
                    missing(() => { print })

                calls: 0

                tests: Array(
                    TestValue("first", () => {
                        calls = calls + 1
                        11
                    }),
                    TestValue("second", () => {
                        calls = calls + 1
                        22
                    })
                )
                """;

        Files.writeString(entry, entrySource, StandardCharsets.UTF_8);
        Files.writeString(suite, suiteSource, StandardCharsets.UTF_8);

        ProtosBundledToolModuleResolver bundled =
                new ProtosBundledToolModuleResolver(
                        "test",
                        TOOL_ROOT,
                        SHARED_ROOT,
                        new ProtosStandardLibraryModuleResolver(STANDARD_LIBRARY));

        try (ProtosDirectFileModuleResolver resolver =
                new ProtosDirectFileModuleResolver(entry, entrySource, bundled)) {
            assumeTrue(
                    resolver.secureConfinementAvailable(),
                    "host provider has no SecureDirectoryStream");

            ProtosPrelude prelude =
                    new ProtosCoreBootstrap().bootstrap(CORE, resolver);
            var activation = prelude.newModuleActivation();

            ProtosExecutionOutcome discoveryImport =
                    ProtosCanonicalInitialModuleExecution.execute(
                            prelude,
                            resolver,
                            resolver.entryModule(),
                            activation);

            assertEquals(
                    ProtosExecutionOutcome.State.COMPLETED,
                    discoveryImport.state());
            assertSame(ProtosBooleanValue.TRUE, discoveryImport.value());

            var suiteKey =
                    resolver.resolve(
                            "./suite.protos",
                            Optional.of(resolver.entryModule()));

            var suiteRecord =
                    activation.actorModuleState()
                            .lookup(suiteKey)
                            .orElseThrow();

            activation.context()
                    .createLocalSlot(
                            "discoverySubject",
                            suiteRecord.instance());

            ProtosExecutionOutcome projection =
                    ProtosTestExecutionSupport.execute(
                            """
                            Discovery: import("self:Discovery")

                            signature:
                                Discovery.declarationSignatureFromModule(
                                    discoverySubject
                                )

                            discoverySubject.authorityFree &&
                                (discoverySubject.calls == 0) &&
                                (signature.size() == 2) &&
                                (signature[0] == "first") &&
                                (signature[1] == "second")
                            """,
                            activation);

            assertEquals(
                    ProtosExecutionOutcome.State.COMPLETED,
                    projection.state());
            assertSame(ProtosBooleanValue.TRUE, projection.value());
        }
    }
}
