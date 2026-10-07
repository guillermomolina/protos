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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

/**
 * Integrated PLAT048 B′ composition: custody ownership when planning or reconciliation
 * fails.
 */
@ResourceLock(ProtosPackageRunDriverTestSupport.RESOURCE_LOCK)
final class ProtosPackageRunDriverPlanningFailureCustodyTest
        extends ProtosPackageRunDriverTestSupport {
    @Test
    void planningFailureClosesEveryVerifiedCustody() throws Exception {
        Path project = project("planning");
        RecordingProvider provider = new RecordingProvider(materializations("planning"));
        RecordingStages stages =
                new RecordingStages() {
                    @Override
                    public Object plan(
                            ProtosWorkspaceRunDriver.Request request,
                            List<ProtosExternalPackagePlanningPreflight.VerifiedExternalPackage>
                                    inputs)
                            throws IOException {
                        planCalls++;
                        assertOpen(verified);
                        throw new IOException("forced planning failure");
                    }
                };

        IOException failure =
                assertThrows(
                        IOException.class,
                        () ->
                                ProtosPackageRunDriver.execute(
                                        request(project, null), provider, stages));

        assertEquals("forced planning failure", failure.getMessage());
        assertEquals(1, stages.planCalls);
        assertAllClosed(stages.verified, 4);
        assertTrue(stages.hosted.isEmpty());
    }

    @Test
    void failedReconciliationTransfersNoOwnershipAndEveryCustodyCloses() throws Exception {
        Path project = project("reconcile");
        RecordingProvider provider = new RecordingProvider(materializations("reconcile"));
        RecordingStages stages =
                new RecordingStages() {
                    @Override
                    public ProtosExternalPackageResourceScope reconcile(
                            ProtosPackageExecutionPlanV2 plan,
                            List<ProtosExternalPackagePlanningPreflight.VerifiedExternalPackage>
                                    inputs)
                            throws IOException {
                        // A real reconciliation failure: one plan node has no verified custody.
                        return super.reconcile(plan, inputs.subList(0, inputs.size() - 1));
                    }
                };

        assertThrows(
                IOException.class,
                () -> ProtosPackageRunDriver.execute(request(project, null), provider, stages));

        assertEquals(1, stages.planCalls);
        assertAllClosed(stages.verified, 4);
        assertTrue(stages.hosted.isEmpty());
    }
}
