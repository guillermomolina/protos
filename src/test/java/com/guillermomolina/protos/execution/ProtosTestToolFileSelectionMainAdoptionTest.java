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
    void fileSelectionOccursAfterPlanMaterializationAndBeforeScheduling()
            throws Exception {
        String source =
                Files.readString(MAIN, StandardCharsets.UTF_8);

        int plansFrozen = source.indexOf("plannedSuites.freeze()");
        int resolver = source.indexOf("fileSourceResolver(filePath)");
        int filter = source.indexOf("FileSelection.selectPlan(");
        int zeroMatch = source.indexOf("(selectedSuites.size() == 0)");
        int progress = source.indexOf(
                "suiteProgress: startProgress(");
        int scheduler = source.indexOf(
                "Runner.runD108WithResources(");

        assertTrue(plansFrozen >= 0);
        assertTrue(resolver > plansFrozen);
        assertTrue(filter > resolver);
        assertTrue(zeroMatch > filter);
        assertTrue(progress > zeroMatch);
        assertTrue(scheduler > progress);
    }

    @Test
    void unfilteredInvocationRetainsOriginalPlannedSuites()
            throws Exception {
        String source =
                Files.readString(MAIN, StandardCharsets.UTF_8);

        assertTrue(
                source.contains(
                        "executionSuites: plannedSuites"));

        assertTrue(
                source.contains(
                        "(filePath === null).ifFalse(() => {"));

        assertTrue(
                source.contains(
                        "(suiteIndex < executionSuites.size())"));

        assertTrue(
                source.contains(
                        "planned: executionSuites[suiteIndex]"));
    }
}
