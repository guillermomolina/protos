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
