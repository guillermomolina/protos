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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

/**
 * Integrated PLAT048 B′ composition: custody ownership when materialization or verification fails
 * before planning.
 */
@ResourceLock(ProtosPackageRunDriverTestSupport.RESOURCE_LOCK)
final class ProtosPackageRunDriverFailureCustodyTest extends ProtosPackageRunDriverTestSupport {
    @Test
    void providerMissClosesPriorVerifiedCustodyAndNeverStartsApplication() throws Exception {
        Path project = project("miss");
        Map<ProtosExactExternalPackageIdentity, Path> materializations =
                new HashMap<>(materializations("miss"));
        materializations.remove(a2);
        RecordingProvider provider = new RecordingProvider(materializations);
        RecordingStages stages = new RecordingStages();

        assertThrows(
                IOException.class,
                () -> ProtosPackageRunDriver.execute(request(project, null), provider, stages));

        assertEquals(List.of(a1, a2), provider.calls);
        assertEquals(1, stages.verifyAttempts);
        assertAllClosed(stages.verified, 1);
        assertTrue(stages.hosted.isEmpty());
        assertEquals(0, stages.planCalls);
    }

    @Test
    void wrongMaterializationFailsNthVerificationAndClosesPriorCustody() throws Exception {
        Path project = project("wrong");
        Map<ProtosExactExternalPackageIdentity, Path> materializations =
                new HashMap<>(materializations("wrong"));
        // A present tree whose ContentIdentity is not a2's.
        materializations.put(a2, materializations.get(cRegistry));
        RecordingProvider provider = new RecordingProvider(materializations);
        RecordingStages stages = new RecordingStages();

        assertThrows(
                IOException.class,
                () -> ProtosPackageRunDriver.execute(request(project, null), provider, stages));

        assertEquals(List.of(a1, a2), provider.calls);
        assertEquals(2, stages.verifyAttempts);
        assertAllClosed(stages.verified, 1);
        assertTrue(stages.hosted.isEmpty());
        assertEquals(0, stages.planCalls);
    }
}
