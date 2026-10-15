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

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * TOOL001-F2E3B execution-plan vectors rejecting verified graphs with invalid Git evidence or
 * dependency edges, over host-captured external package custody as in
 * {@link ProtosPackageToolExecutionPlanCaptureTest}.
 */
final class ProtosPackageToolExecutionPlanInvalidGitAndEdgeGraphTest
        extends ProtosPackageToolProtosTestSupport {
    private static final Path CASES = TEST_ROOT.resolve("execution-plan").resolve("cases");

    @Test
    void invalidGitAndEdgeGraphsAreRejected() throws Exception {
        for (String name :
                List.of(
                        "f2e3b-git-fetch",
                        "f2e3b-git-revision",
                        "f2e3b-missing-edge",
                        "f2e3b-extra-edge",
                        "f2e3b-git-invalid-package-version",
                        "f2e3b-duplicate-edge",
                        "f2e3b-dangling-target")) {
            assertExpected(
                    executeExternalCaptureFixture(
                            CASES.resolve(name), "f2e3b-build-v2-error.protos"),
                    "error",
                    name);
        }
    }
}
