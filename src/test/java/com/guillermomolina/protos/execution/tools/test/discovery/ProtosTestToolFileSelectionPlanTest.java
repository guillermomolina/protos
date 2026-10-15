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
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class ProtosTestToolFileSelectionPlanTest {
    private static final Path CORE = Path.of("protos", "lib", "core");
    private static final Path STANDARD_LIBRARY = Path.of("protos", "lib");
    private static final Path TOOL_ROOT = Path.of("protos", "tools", "test");
    private static final Path SHARED_ROOT = Path.of("protos", "tools", "shared");

    @Test
    void oneSourceMaySelectMultipleAuthoritativeCasesInPlanOrder()
            throws Exception {
        ProtosExecutionOutcome outcome =
                execute(
                        """
                        Manifest: import("self:Manifest")
                        FileSelection: import("self:FileSelection")

                        first:
                            Manifest.projectTreeCaseSpec(
                                Array("project-a", "shared.protos", "true"),
                                "protos/example"
                            )
                        second:
                            Manifest.projectTreeCaseSpec(
                                Array("project-b", "other.protos", "true"),
                                "protos/example"
                            )
                        third:
                            Manifest.projectTreeCaseSpec(
                                Array("project-c", "shared.protos", "error"),
                                "protos/example"
                            )

                        cases: Array(first, second, third)
                        cases.freeze()
                        plan: Array(cases)
                        plan.freeze()

                        associations:
                            Array(
                                Array(
                                    "protos/corpus/example",
                                    "fixtures/shared.protos"
                                )
                            )

                        selected:
                            FileSelection.selectPlan(
                                "protos/corpus/example",
                                plan,
                                associations
                            )

                        selectedCases: Manifest.planCases(selected)

                        (selectedCases.size() == 2) &&
                            (selectedCases[0] === first) &&
                            (selectedCases[1] === third) &&
                            (
                                Manifest.caseId(selectedCases[0]) ==
                                "protos/example/project-a/shared.protos"
                            ) &&
                            (
                                Manifest.caseId(selectedCases[1]) ==
                                "protos/example/project-c/shared.protos"
                            )
                        """);

        assertEquals(ProtosExecutionOutcome.State.COMPLETED, outcome.state());
        assertSame(ProtosBooleanValue.TRUE, outcome.value());
    }

    @Test
    void otherCorpusAssociationDoesNotSelectSameRelativePath()
            throws Exception {
        ProtosExecutionOutcome outcome =
                execute(
                        """
                        Manifest: import("self:Manifest")
                        FileSelection: import("self:FileSelection")

                        spec:
                            Manifest.projectTreeCaseSpec(
                                Array("project-a", "shared.protos", "true"),
                                "protos/example"
                            )

                        cases: Array(spec)
                        cases.freeze()
                        plan: Array(cases)
                        plan.freeze()

                        associations:
                            Array(
                                Array(
                                    "protos/corpus/other",
                                    "fixtures/shared.protos"
                                )
                            )

                        selected:
                            FileSelection.selectPlan(
                                "protos/corpus/example",
                                plan,
                                associations
                            )

                        Manifest.planCases(selected).size() == 0
                        """);

        assertEquals(ProtosExecutionOutcome.State.COMPLETED, outcome.state());
        assertSame(ProtosBooleanValue.TRUE, outcome.value());
    }

    @Test
    void duplicateAssociationsDoNotDuplicateCases()
            throws Exception {
        ProtosExecutionOutcome outcome =
                execute(
                        """
                        Manifest: import("self:Manifest")
                        FileSelection: import("self:FileSelection")

                        spec:
                            Manifest.projectTreeCaseSpec(
                                Array("project-a", "shared.protos", "true"),
                                "protos/example"
                            )

                        cases: Array(spec)
                        cases.freeze()
                        plan: Array(cases)
                        plan.freeze()

                        associations:
                            Array(
                                Array("protos/corpus/example", "shared.protos"),
                                Array(
                                    "protos/corpus/example",
                                    "fixtures/shared.protos"
                                )
                            )

                        selected:
                            FileSelection.selectPlan(
                                "protos/corpus/example",
                                plan,
                                associations
                            )

                        selectedCases: Manifest.planCases(selected)

                        (selectedCases.size() == 1) &&
                            (selectedCases[0] === spec)
                        """);

        assertEquals(ProtosExecutionOutcome.State.COMPLETED, outcome.state());
        assertSame(ProtosBooleanValue.TRUE, outcome.value());
    }

    @Test
    void directoryAssociationSelectsRecursivelyWithoutSiblingPrefix()
            throws Exception {
        ProtosExecutionOutcome outcome =
                execute(
                        """
                        Manifest: import("self:Manifest")
                        FileSelection: import("self:FileSelection")

                        first:
                            Manifest.projectTreeCaseSpec(
                                ["project-a", "group/first.protos", "true"],
                                "protos/example"
                            )
                        second:
                            Manifest.projectTreeCaseSpec(
                                [
                                    "project-b",
                                    "group/nested/second.protos",
                                    "true"
                                ],
                                "protos/example"
                            )
                        sibling:
                            Manifest.projectTreeCaseSpec(
                                ["project-c", "grouped/third.protos", "true"],
                                "protos/example"
                            )

                        cases: [first, second, sibling]
                        cases.freeze()
                        plan: [cases]
                        plan.freeze()

                        selected:
                            FileSelection.selectPlanWithDirectories(
                                "protos/corpus/example",
                                plan,
                                [],
                                [
                                    [
                                        "protos/corpus/example",
                                        "fixtures/group"
                                    ]
                                ]
                            )

                        selectedCases: Manifest.planCases(selected)

                        (selectedCases.size() == 2) &&
                            (selectedCases[0] === first) &&
                            (selectedCases[1] === second)
                        """);

        assertEquals(ProtosExecutionOutcome.State.COMPLETED, outcome.state());
        assertSame(ProtosBooleanValue.TRUE, outcome.value());
    }

    @Test
    void overlappingSelectorsDeduplicateAndKeepCanonicalPlanOrder()
            throws Exception {
        ProtosExecutionOutcome outcome =
                execute(
                        """
                        Manifest: import("self:Manifest")
                        FileSelection: import("self:FileSelection")

                        first:
                            Manifest.projectTreeCaseSpec(
                                ["project-a", "group/first.protos", "true"],
                                "protos/example"
                            )
                        second:
                            Manifest.projectTreeCaseSpec(
                                ["project-b", "other.protos", "true"],
                                "protos/example"
                            )
                        third:
                            Manifest.projectTreeCaseSpec(
                                ["project-c", "group/third.protos", "error"],
                                "protos/example"
                            )

                        cases: [first, second, third]
                        cases.freeze()
                        plan: [cases]
                        plan.freeze()

                        selected:
                            FileSelection.selectPlanWithDirectories(
                                "protos/corpus/example",
                                plan,
                                [
                                    [
                                        "protos/corpus/example",
                                        "fixtures/group/third.protos"
                                    ],
                                    [
                                        "protos/corpus/example",
                                        "fixtures/other.protos"
                                    ]
                                ],
                                [
                                    [
                                        "protos/corpus/example",
                                        "fixtures/group"
                                    ]
                                ]
                            )

                        selectedCases: Manifest.planCases(selected)

                        (selectedCases.size() == 3) &&
                            (selectedCases[0] === first) &&
                            (selectedCases[1] === second) &&
                            (selectedCases[2] === third)
                        """);

        assertEquals(ProtosExecutionOutcome.State.COMPLETED, outcome.state());
        assertSame(ProtosBooleanValue.TRUE, outcome.value());
    }

    private static ProtosExecutionOutcome execute(String source) throws Exception {
        ProtosBundledToolModuleResolver resolver =
                new ProtosBundledToolModuleResolver(
                        "test",
                        TOOL_ROOT,
                        SHARED_ROOT,
                        new ProtosStandardLibraryModuleResolver(STANDARD_LIBRARY));

        ProtosPrelude prelude =
                new ProtosCoreBootstrap().bootstrap(CORE, resolver);

        return ProtosTestExecutionSupport.execute(
                source,
                prelude.newModuleActivation());
    }
}
