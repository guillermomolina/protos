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
import org.junit.jupiter.api.Test;

/**
 * TOOL001-F2E3B execution-plan vectors over host-captured external package custody.
 *
 * <p>D171 retired the guest {@code Filesystem.captureTree} selector these vectors previously used
 * under {@code protos test}. Each {@code external/<packageId>} tree is now captured exactly once
 * by {@link ProtosCapturedFilesystemCustody} and handed to the Protos fixture as an ordinary
 * read-only Filesystem, matching the PLAT012 host/runtime custody architecture.
 */
final class ProtosPackageToolExecutionPlanCaptureTest extends ProtosPackageToolProtosTestSupport {
    private static final Path CASES = TEST_ROOT.resolve("execution-plan").resolve("cases");

    @Test
    void transitiveRegistryAndGitCapturesBuildPlanV2() throws Exception {
        assertExpected(
                executeExternalCaptureFixture(
                        CASES.resolve("f2e3b-transitive"), "f2e3b-transitive.protos"),
                "true",
                "f2e3b-transitive");
    }

    @Test
    void missingVerifiedDescriptorIsRejected() throws Exception {
        assertExpected(
                executeExternalCaptureFixture(
                        CASES.resolve("f2e3b-transitive"),
                        "f2e3b-missing-descriptor-error.protos"),
                "error",
                "f2e3b-missing-descriptor-error");
    }

    @Test
    void nonlockedVerifiedDescriptorIsRejected() throws Exception {
        assertExpected(
                executeExternalCaptureFixture(
                        CASES.resolve("f2e3b-transitive"),
                        "f2e3b-nonlocked-descriptor-error.protos"),
                "error",
                "f2e3b-nonlocked-descriptor-error");
    }
}
