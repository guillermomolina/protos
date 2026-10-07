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

import org.junit.jupiter.api.Test;

/** TOOL009-G2 / D185 public `protos test --case CASE_REF` rejection before scheduling. */
final class ProtosTestToolCaseRejectionPublicIntegrationTest
        extends ProtosTestToolCaseSelectionPublicIntegrationTestSupport {
    @Test
    void repeatedExactRefFailsBeforeScheduling() {
        assertFailsBeforeScheduling(
                run("test", "--file", PARSE_FILE, "--case", parseRef(1), "--case", parseRef(1)));
    }

    @Test
    void wellFormedUnknownRefFailsBeforeScheduling() {
        assertFailsBeforeScheduling(
                run("test", "--file", PARSE_FILE, "--case", ref(CORPUS, PARSE_SOURCE, "missing")));
    }

    @Test
    void malformedRefFailsBeforeScheduling() {
        assertFailsBeforeScheduling(
                run("test", "--file", PARSE_FILE, "--case", "uri/parse.protos::parse valid"));
    }

    @Test
    void unsupportedRefVersionFailsBeforeScheduling() {
        assertFailsBeforeScheduling(
                run(
                        "test",
                        "--file",
                        PARSE_FILE,
                        "--case",
                        "v2." + parseRef(2).substring("v1.".length())));
    }

    @Test
    void refOutsideActiveFileScopeFailsBeforeScheduling() {
        assertFailsBeforeScheduling(
                run(
                        "test",
                        "--file",
                        PARSE_FILE,
                        "--case",
                        ref(CORPUS, "uri/format.protos", "format constructed")));
    }
}
