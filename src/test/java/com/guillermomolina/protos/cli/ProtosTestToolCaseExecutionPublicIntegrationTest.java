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
package com.guillermomolina.protos.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** TOOL009-G2 / D185 public `protos test --case CASE_REF` exact execution. */
final class ProtosTestToolCaseExecutionPublicIntegrationTest
        extends ProtosTestToolCaseSelectionPublicIntegrationTestSupport {
    @Test
    void shardExecutionUsesExistingProgressAndSummaryAtEveryCapacity() {
        for (String jobs : new String[] {"1", "4"}) {
            assertShardExecution("2", "1", jobs, 2);
            assertShardExecution("2", "2", jobs, 2);
        }
        assertShardExecution("1", "1", "1", 4);
        assertShardExecution("4", "3", "1", 1);
        assertShardExecution("5", "5", "1", 0);
        assertShardExecution("9223372036854775809", "9223372036854775809", "1", 0);
    }

    @Test
    void exactSelectionResolvesBothRequestedCasesBeforeShardExecution() {
        for (String shard : new String[] {"1", "2"}) {
            R result = run("test", "--file", PARSE_FILE, "--case", parseRef(3),
                    "--case", parseRef(0), "--shard-count", "2", "--shard-run", shard);
            assertCompletedCases(result, 1);
        }
    }

    private static void assertShardExecution(String count, String shard, String jobs, int cases) {
        assertCompletedCases(run("test", "--file", PARSE_FILE, "--shard-count", count,
                "--shard-run", shard, "--jobs", jobs), cases);
    }

    private static void assertCompletedCases(R result, int cases) {
        assertEquals(0, result.code(), result::err);
        assertEquals(EXPECTED_BOOTSTRAP_STDOUT, result.out());
        StringBuilder expected = new StringBuilder();
        if (cases > 0) {
            expected.append("[library/uri] 0/").append(cases).append('\n');
            for (int completed = 1; completed <= cases; completed++) {
                expected.append("[library/uri] ").append(completed).append('/').append(cases);
                if (completed == cases) {
                    expected.append(" passed");
                }
                expected.append('\n');
            }
        }
        expected.append(cases).append(" passed, 0 failed\n");
        assertEquals(expected.toString(), result.err());
    }

    @Test
    void exactCaseExecutesOnlyThatCase() {
        R result = run("test", "--file", PARSE_FILE, "--case", parseRef(2));

        assertEquals(0, result.code());
        assertEquals(EXPECTED_BOOTSTRAP_STDOUT, result.out());
        assertEquals(
                "[library/uri] 0/1\n" + "[library/uri] 1/1 passed\n" + "1 passed, 0 failed\n",
                result.err());
    }

    @Test
    void exactCasesInReverseArgumentOrderExecuteOnlyThoseCases() {
        R result =
                run("test", "--file", PARSE_FILE, "--case", parseRef(3), "--case", parseRef(1));

        assertEquals(0, result.code());
        assertEquals(EXPECTED_BOOTSTRAP_STDOUT, result.out());
        assertEquals(
                "[library/uri] 0/2\n"
                        + "[library/uri] 1/2\n"
                        + "[library/uri] 2/2 passed\n"
                        + "2 passed, 0 failed\n",
                result.err());
    }
}
