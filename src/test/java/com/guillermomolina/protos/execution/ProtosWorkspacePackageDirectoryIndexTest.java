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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ProtosWorkspacePackageDirectoryIndexTest {
    @TempDir Path temporary;

    @Test
    void bindsRootAndMembersByExactPackageIdAndLocation() throws Exception {
        Path memberDirectory =
                Files.createDirectories(temporary.resolve("libs").resolve("member"));

        ProtosPackageExecutionPlan.PackageNode rootNode = node("root", "");
        ProtosPackageExecutionPlan.PackageNode memberNode =
                node("member-pkg", "libs/member");
        ProtosPackageExecutionPlan plan = plan(rootNode, memberNode);
        ProtosWorkspacePackageProjectIndex projectIndex =
                ProtosWorkspacePackageProjectIndex.bind(temporary, plan);

        ProtosWorkspacePackageDirectoryIndex index =
                ProtosWorkspacePackageDirectoryIndex.bind(projectIndex);

        ProtosWorkspacePackageDirectoryIndex.PackageDirectory root = index.rootPackage();
        assertSame(projectIndex, index.projectIndex());
        assertSame(rootNode, root.packageNode());
        assertEquals(temporary.toRealPath(), root.directory());

        ProtosWorkspacePackageDirectoryIndex.PackageDirectory member =
                index.requirePackage("member-pkg");
        assertSame(memberNode, member.packageNode());
        assertEquals(memberDirectory.toRealPath(), member.directory());
        assertSame(member, index.requireLocation("libs/member"));

        assertThrows(IOException.class, () -> index.requirePackage("missing"));
        assertThrows(IOException.class, () -> index.requireLocation("libs/missing"));
    }

    @Test
    void failsClosedWhenAnIndexedMemberCannotBeBoundPhysically() throws Exception {
        ProtosPackageExecutionPlan.PackageNode rootNode = node("root", "");
        ProtosPackageExecutionPlan.PackageNode missingMember =
                node("missing-pkg", "libs/missing");
        ProtosWorkspacePackageProjectIndex projectIndex =
                ProtosWorkspacePackageProjectIndex.bind(
                        temporary, plan(rootNode, missingMember));

        assertThrows(
                IOException.class,
                () -> ProtosWorkspacePackageDirectoryIndex.bind(projectIndex));
    }

    @Test
    void bindsAWorkspaceContainingOnlyTheRootWithoutMemberTraversal() throws Exception {
        ProtosPackageExecutionPlan.PackageNode rootNode = node("root", "");
        ProtosWorkspacePackageProjectIndex projectIndex =
                ProtosWorkspacePackageProjectIndex.bind(temporary, plan(rootNode));

        ProtosWorkspacePackageDirectoryIndex index =
                ProtosWorkspacePackageDirectoryIndex.bind(projectIndex);

        assertSame(rootNode, index.requirePackage("root").packageNode());
        assertEquals(temporary.toRealPath(), index.requireLocation("").directory());
    }

    private static ProtosPackageExecutionPlan plan(
            ProtosPackageExecutionPlan.PackageNode... packages) {
        return new ProtosPackageExecutionPlan(
                1,
                new ProtosPackageExecutionPlan.WorkspaceRef("root"),
                List.of(packages),
                List.of());
    }

    private static ProtosPackageExecutionPlan.PackageNode node(
            String packageId, String location) {
        return new ProtosPackageExecutionPlan.PackageNode(
                new ProtosPackageExecutionPlan.WorkspaceRef(packageId),
                location,
                Map.of());
    }
}
