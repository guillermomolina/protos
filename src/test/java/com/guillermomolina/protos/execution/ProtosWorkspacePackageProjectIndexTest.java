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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ProtosWorkspacePackageProjectIndexTest {
    @TempDir Path temporary;

    @Test
    void anchorsRealProjectRootAndIndexesDetachedPackagesWithoutBindingMembers()
            throws Exception {
        Files.createDirectories(temporary.resolve("libs").resolve("a"));

        ProtosPackageExecutionPlan.PackageNode rootNode =
                node("root", "", Map.of("Main", "Main"));
        ProtosPackageExecutionPlan.PackageNode member =
                node("a-pkg", "libs/a", Map.of("Public", "internal/Thing"));
        ProtosPackageExecutionPlan plan =
                plan(rootNode, member);

        ProtosWorkspacePackageProjectIndex index =
                ProtosWorkspacePackageProjectIndex.bind(temporary, plan);

        assertEquals(temporary.toAbsolutePath().normalize(), index.selectedProjectRoot());
        assertEquals(temporary.toRealPath(), index.realProjectRoot());
        assertSame(plan, index.plan());
        assertSame(rootNode, index.rootPackage());
        assertSame(rootNode, index.requirePackage("root"));
        assertSame(member, index.requirePackage("a-pkg"));
        assertSame(member, index.requireLocation("libs/a"));

        assertThrows(Exception.class, () -> index.requirePackage("missing"));
        assertThrows(Exception.class, () -> index.requireLocation("libs/missing"));
    }

    @Test
    void rejectsInvalidRootIndexShapeBeforeAnyMemberPathBinding() throws Exception {
        Files.createDirectories(temporary.resolve("project"));
        Path project = temporary.resolve("project");

        ProtosPackageExecutionPlan.PackageNode rootNode = node("root", "", Map.of());
        ProtosPackageExecutionPlan.PackageNode duplicateId =
                node("root", "libs/a", Map.of());
        assertThrows(
                Exception.class,
                () ->
                        ProtosWorkspacePackageProjectIndex.bind(
                                project,
                                new ProtosPackageExecutionPlan(
                                        1,
                                        ref("root"),
                                        List.of(rootNode, duplicateId),
                                        List.of())));

        ProtosPackageExecutionPlan.PackageNode duplicateLocation =
                node("other", "", Map.of());
        assertThrows(
                Exception.class,
                () ->
                        ProtosWorkspacePackageProjectIndex.bind(
                                project,
                                new ProtosPackageExecutionPlan(
                                        1,
                                        ref("root"),
                                        List.of(rootNode, duplicateLocation),
                                        List.of())));

        assertThrows(
                Exception.class,
                () ->
                        ProtosWorkspacePackageProjectIndex.bind(
                                project,
                                new ProtosPackageExecutionPlan(
                                        1,
                                        ref("other"),
                                        List.of(rootNode),
                                        List.of())));

        assertThrows(
                Exception.class,
                () ->
                        ProtosWorkspacePackageProjectIndex.bind(
                                project,
                                new ProtosPackageExecutionPlan(
                                        1,
                                        ref(""),
                                        List.of(node("", "", Map.of())),
                                        List.of())));
    }

    @Test
    void rejectsMissingOrNonDirectorySelectedProjectRoot() throws Exception {
        Path missing = temporary.resolve("missing");
        assertThrows(
                Exception.class,
                () ->
                        ProtosWorkspacePackageProjectIndex.bind(
                                missing,
                                plan(node("root", "", Map.of()))));

        Path file = temporary.resolve("file");
        Files.writeString(file, "not a directory");
        assertThrows(
                Exception.class,
                () ->
                        ProtosWorkspacePackageProjectIndex.bind(
                                file,
                                plan(node("root", "", Map.of()))));
    }

    @Test
    void deliberatelyLeavesMemberLocationTraversalForB1B2() throws Exception {
        ProtosPackageExecutionPlan.PackageNode rootNode = node("root", "", Map.of());
        ProtosPackageExecutionPlan.PackageNode unresolvedMember =
                node("member", "../not-bound-here", Map.of());

        ProtosWorkspacePackageProjectIndex index =
                ProtosWorkspacePackageProjectIndex.bind(
                        temporary,
                        plan(rootNode, unresolvedMember));

        assertSame(unresolvedMember, index.requirePackage("member"));
        assertEquals("../not-bound-here", index.requirePackage("member").location());
    }

    private static ProtosPackageExecutionPlan plan(
            ProtosPackageExecutionPlan.PackageNode... packages) {
        return new ProtosPackageExecutionPlan(
                1,
                ref("root"),
                List.of(packages),
                List.of());
    }

    private static ProtosPackageExecutionPlan.PackageNode node(
            String packageId, String location, Map<String, String> exports) {
        return new ProtosPackageExecutionPlan.PackageNode(
                ref(packageId), location, exports);
    }

    private static ProtosPackageExecutionPlan.WorkspaceRef ref(String packageId) {
        return new ProtosPackageExecutionPlan.WorkspaceRef(packageId);
    }
}
