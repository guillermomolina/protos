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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class ProtosTestToolHClosureReconciliationTest {
    @Test
    void publishedH2B3SchedulingContractRemainsMaterializedAfterI8D5CCutover() throws Exception {
        String main = Files.readString(Path.of("protos", "tools", "test", "Main.protos"));
        String options = Files.readString(Path.of("protos", "tools", "test", "Options.protos"));
        String carrier =
                Files.readString(
                        Path.of(
                                "src",
                                "main",
                                "java",
                                "com",
                                "guillermomolina",
                                "protos",
                                "cli",
                                "ProtosTestToolAsyncExecutionScope.java"));

        assertTrue(main.contains("arguments: process.args()"));
        assertTrue(main.contains("jobs: Options.jobs(arguments)"));
        assertEquals(4, occurrences(main, "Runner.runD108WithResources("));
        assertEquals(0, occurrences(main, "Runner.runBounded("));
        assertFalse(main.contains("Runner.runSimple("));
        assertTrue(main.contains("Runner.testRunOutcomeCompletedAcrossRuns("));
        assertTrue(main.contains("Runner.testRunOutcomeInfrastructureAbortedAcrossInvocation("));
        assertTrue(main.trim().endsWith("finalOutcome"));
        assertTrue(options.contains("jobs: (arguments) => {"));
        assertTrue(options.contains("jobsValue: 1"));
        assertTrue(carrier.contains("Thread.ofPlatform()"));
    }

    private static int occurrences(String text, String needle) {
        int count = 0;
        int offset = 0;
        while (true) {
            int next = text.indexOf(needle, offset);
            if (next < 0) {
                return count;
            }
            count++;
            offset = next + needle.length();
        }
    }
}
