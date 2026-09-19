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

import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosStringValue;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ProtosTestLogicalCaseDiscoveryFacilityTest {
    private static final Path CORE =
            Path.of("protos", "lib", "core");
    private static final Path STANDARD_LIBRARY =
            Path.of("protos", "lib");
    private static final Path TOOL_ROOT =
            Path.of("protos", "tools", "test");
    private static final Path SHARED_ROOT =
            Path.of("protos", "tools", "shared");

    @Test
    void discoversDetachedInertProjectionWithoutInvokingBodies(
            @TempDir Path root)
            throws Exception {
        ProtosBundledToolModuleResolver resolver =
                new ProtosBundledToolModuleResolver(
                        "test",
                        TOOL_ROOT,
                        SHARED_ROOT,
                        new ProtosStandardLibraryModuleResolver(
                                STANDARD_LIBRARY));

        ProtosPrelude prelude =
                new ProtosCoreBootstrap()
                        .bootstrap(
                                CORE,
                                resolver);

        var activation =
                prelude.newModuleActivation();

        String suiteSource =
                """
                TestValue: import("std:test/Test")

                tests: Array(
                    TestValue("first", () => {
                        Error().signal()
                    }),
                    TestValue("second", () => {
                        Error().signal()
                    })
                )
                """;

        Path suite =
                root.resolve("suite.protos");
        Files.writeString(
                suite,
                suiteSource,
                StandardCharsets.UTF_8);

        ProtosTestLogicalCaseDiscoveryFacility.install(
                activation,
                CORE,
                resolver,
                List.of(
                        new ProtosTestToolFileSelectionFacility.CorpusSourceRoot(
                                "test-corpus",
                                root)));

        activation.context()
                .createLocalSlot(
                        "sourceAssociation",
                        prelude.newFrozenArray(
                                List.of(
                                        new ProtosStringValue(
                                                "test-corpus"),
                                        new ProtosStringValue(
                                                "suite.protos"))));

        activation.context()
                .createLocalSlot(
                        "suiteSource",
                        new ProtosStringValue(
                                suiteSource));

        ProtosExecutionOutcome outcome =
                ProtosTestExecutionSupport.execute(
                        """
                        Discovery:
                            import("self:Discovery")
                        CasePlan:
                            import("self:LogicalCasePlan")

                        projection:
                            logicalCaseDiscovery(
                                sourceAssociation,
                                suiteSource
                            )

                        signature:
                            Discovery.projectionSignature(
                                projection
                            )

                        plan:
                            Discovery.projectionPlan(
                                projection
                            )

                        (signature.size() == 2) &&
                            (signature[0] == "first") &&
                            (signature[1] == "second") &&
                            (plan.size() == 2) &&
                            (
                                CasePlan.sourceAssociation(
                                    plan[0]
                                )[0] == "test-corpus"
                            ) &&
                            (
                                CasePlan.sourceAssociation(
                                    plan[0]
                                )[1] == "suite.protos"
                            ) &&
                            (
                                CasePlan.selector(
                                    plan[0]
                                ) == "first"
                            ) &&
                            (
                                CasePlan.sourceAssociation(
                                    plan[1]
                                )[0] == "test-corpus"
                            ) &&
                            (
                                CasePlan.sourceAssociation(
                                    plan[1]
                                )[1] == "suite.protos"
                            ) &&
                            (
                                CasePlan.selector(
                                    plan[1]
                                ) == "second"
                            )
                        """,
                        activation);

        assertEquals(
                ProtosExecutionOutcome.State.COMPLETED,
                outcome.state(),
                () ->
                        "TOOL009 discovery facility outcome="
                                + outcome.state()
                                + ", error="
                                + outcome.error());

        assertSame(
                ProtosBooleanValue.TRUE,
                outcome.value());
    }
}
