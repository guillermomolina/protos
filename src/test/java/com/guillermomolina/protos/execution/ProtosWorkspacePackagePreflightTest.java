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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.guillermomolina.protos.runtime.ProtosProcessRuntime;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

final class ProtosWorkspacePackagePreflightTest {
    private static final Path CORE = Path.of("protos", "lib", "core");
    private static final Path STANDARD_LIBRARY = Path.of("protos", "lib");
    private static final Path TOOL_ROOT = Path.of("protos", "tools", "package");
    private static final Path CASES =
            Path.of("protos", "tests", "package-tool", "execution-plan", "cases");

    @Test
    void buildsDetachedPlanInTerminatedToolProcessWithoutMetadataMutation()
            throws Exception {
        Path project = CASES.resolve("workspace");
        assumeSecureConfinement(project);
        byte[] manifestBefore = Files.readAllBytes(project.resolve("protos.toml"));
        byte[] lockBefore = Files.readAllBytes(project.resolve("protos.lock"));
        AtomicReference<ProtosProcessRuntime> observed = new AtomicReference<>();

        ProtosPackageExecutionPlan plan =
                ProtosWorkspacePackagePreflight.build(
                        CORE,
                        TOOL_ROOT,
                        project,
                        new ProtosStandardLibraryModuleResolver(STANDARD_LIBRARY),
                        observed::set);

        assertEquals("root", plan.root().packageId());
        assertEquals(2, plan.packages().size());
        assertEquals(2, plan.dependencies().size());
        assertEquals("internal/Thing", plan.packages().get(1).exports().get("Public"));
        assertArrayEquals(manifestBefore, Files.readAllBytes(project.resolve("protos.toml")));
        assertArrayEquals(lockBefore, Files.readAllBytes(project.resolve("protos.lock")));

        ProtosProcessRuntime process = observed.get();
        assertNotNull(process);
        assertEquals(ProtosProcessRuntime.LifecycleState.TERMINATED, process.lifecycleState());
        assertTrue(process.rootFilesystemForRuntime().isEmpty());
    }

    @Test
    void staleProjectFailsClosedAndStillTerminatesToolProcess() throws Exception {
        Path project = CASES.resolve("stale");
        assumeSecureConfinement(project);
        AtomicReference<ProtosProcessRuntime> observed = new AtomicReference<>();

        assertThrows(
                IOException.class,
                () ->
                        ProtosWorkspacePackagePreflight.build(
                                CORE,
                                TOOL_ROOT,
                                project,
                                new ProtosStandardLibraryModuleResolver(STANDARD_LIBRARY),
                                observed::set));

        ProtosProcessRuntime process = observed.get();
        assertNotNull(process);
        assertEquals(ProtosProcessRuntime.LifecycleState.TERMINATED, process.lifecycleState());
        assertTrue(process.rootFilesystemForRuntime().isEmpty());
    }

    private static void assumeSecureConfinement(Path root) throws Exception {
        try (ProtosNioReadOnlyTreeFilesystemBackend backend =
                new ProtosNioReadOnlyTreeFilesystemBackend(root)) {
            assumeTrue(
                    backend.secureConfinementAvailable(),
                    "host provider has no SecureDirectoryStream");
        }
    }
}
