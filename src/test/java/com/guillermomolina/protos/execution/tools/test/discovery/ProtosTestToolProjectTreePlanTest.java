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
import org.junit.jupiter.api.io.TempDir;

/**
 * D133 project-tree planning coverage separated from the general Manifest planning suite.
 *
 * <p>The implementation remains owned by {@link ProtosTestToolManifestPlanTest}; this class
 * provides a distinct JUnit scheduling boundary for the D133 project-tree responsibility.
 */
final class ProtosTestToolProjectTreePlanTest {
    @Test
    void d133ProjectTreeAuthorityDescriptorIsInertCaseScopedData()
            throws Exception {
        ProtosTestToolManifestPlanTest
                .d133ProjectTreeAuthorityDescriptorIsInertCaseScopedData();
    }

    @Test
    void d133ProjectTreeLoaderNormalizesCaseIdentitySourceAndAuthority(
            @TempDir Path corpusRoot)
            throws Exception {
        ProtosTestToolManifestPlanTest
                .d133ProjectTreeLoaderNormalizesCaseIdentitySourceAndAuthority(corpusRoot);
    }

    @Test
    void d133ResourceRequirementAttachmentPreservesCaseAuthorityDescriptor()
            throws Exception {
        ProtosTestToolManifestPlanTest
                .d133ResourceRequirementAttachmentPreservesCaseAuthorityDescriptor();
    }

    @Test
    void d133RealProjectTreeCorporaMaterializeCompletelyBeforeScheduling()
            throws Exception {
        ProtosTestToolManifestPlanTest
                .d133RealProjectTreeCorporaMaterializeCompletelyBeforeScheduling();
    }
}
