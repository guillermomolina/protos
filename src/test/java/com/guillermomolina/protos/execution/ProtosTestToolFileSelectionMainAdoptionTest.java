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

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class ProtosTestToolFileSelectionMainAdoptionTest {
    private static final Path MAIN =
            Path.of("protos", "tools", "test", "Main.protos");

    @Test
    void selectionOccursAfterPlanMaterializationAndBeforeScheduling()
            throws Exception {
        String source =
                Files.readString(MAIN, StandardCharsets.UTF_8);

        int plansFrozen = source.indexOf("plannedSuites.freeze()");
        int fileResolver =
                source.indexOf("fileSourceResolver(filePath)");
        int directoryResolver =
                source.indexOf("directorySourceResolver(directoryPath)");
        int filter =
                source.indexOf(
                        "FileSelection.selectPlanWithDirectories(");
        int zeroMatch =
                source.indexOf("(selectedSuites.size() == 0)");
        int ownershipSplit =
                source.indexOf("LogicalCaseMigration.suiteNativeSpecs(");
        int logicalDiscovery =
                source.indexOf("logicalCaseDiscovery(");
        int progressBinding = source.indexOf("suiteProgress:");
        int progress =
                source.indexOf("startProgress(", progressBinding);
        int scheduler =
                source.indexOf("Runner.runD108WithResources(");

        assertTrue(plansFrozen >= 0);
        assertTrue(fileResolver > plansFrozen);
        assertTrue(directoryResolver > fileResolver);
        assertTrue(filter > directoryResolver);
        assertTrue(zeroMatch > filter);
        assertTrue(ownershipSplit > zeroMatch);
        assertTrue(logicalDiscovery > ownershipSplit);
        assertTrue(progressBinding > logicalDiscovery);
        assertTrue(progress > progressBinding);
        assertTrue(scheduler > progress);
    }

    @Test
    void everyExplicitSelectorMustMatchBeforeUnionFiltering()
            throws Exception {
        String source =
                Files.readString(MAIN, StandardCharsets.UTF_8);

        assertTrue(
                source.contains(
                        "filePaths.each((filePath) => {"));

        assertTrue(
                source.contains(
                        "directoryPaths.each((directoryPath) => {"));

        assertTrue(
                source.contains(
                        """
                        selectorMatchesAnyPlan(
                                resolvedAssociations,
                                []
                            ).ifFalse(() => {
                                Error().signal()
                            })
                        """.strip()));

        assertTrue(
                source.contains(
                        """
                        selectorMatchesAnyPlan(
                                [],
                                resolvedAssociations
                            ).ifFalse(() => {
                                Error().signal()
                            })
                        """.strip()));
    }

    @Test
    void unfilteredInvocationRetainsOriginalPlannedSuites()
            throws Exception {
        String source =
                Files.readString(MAIN, StandardCharsets.UTF_8);

        assertTrue(
                source.contains(
                        "filePaths: Options.filePaths(arguments)"));

        assertTrue(
                source.contains(
                        "directoryPaths: Options.directoryPaths(arguments)"));

        assertTrue(
                source.contains(
                        "executionSuites: plannedSuites"));

        assertTrue(
                source.contains(
                        "selectorsActive.ifTrue(() => {"));

        assertTrue(
                source.contains(
                        "(suiteIndex < executionSuites.size())"));

        assertTrue(
                source.contains(
                        "planned: executionSuites[suiteIndex]"));
    }
}
