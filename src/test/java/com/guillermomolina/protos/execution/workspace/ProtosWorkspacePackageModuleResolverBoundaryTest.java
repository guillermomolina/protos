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

import com.guillermomolina.protos.runtime.modules.ProtosModuleKey;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ProtosWorkspacePackageModuleResolverBoundaryTest {
    @TempDir Path temporary;

    @Test
    void runtimeResolutionUsesTheSameHardMemberBoundaryAsStaticInventory()
            throws Exception {
        Files.createDirectories(temporary.resolve("Main"));
        Files.writeString(temporary.resolve("Main/module.protos"), "this.value: 1\n");
        Path memberDirectory = Files.createDirectories(temporary.resolve("libs/member/Api"));
        Files.writeString(memberDirectory.resolve("module.protos"), "this.value: 2\n");

        ProtosPackageExecutionPlan.PackageNode root = node("root", "");
        ProtosPackageExecutionPlan.PackageNode member =
                node("member-pkg", "libs/member");
        ProtosPackageExecutionPlan plan =
                new ProtosPackageExecutionPlan(
                        1,
                        root.ref(),
                        List.of(root, member),
                        List.of());

        ProtosWorkspacePackageModuleResolver resolver =
                new ProtosWorkspacePackageModuleResolver(temporary, plan);

        assertEquals(
                ProtosWorkspacePackageModuleKey.encode(ProtosModuleKey.Kind.MODULE, "root", "Main"),
                resolver.entryModule("Main"));
        assertThrows(
                IOException.class,
                () -> resolver.entryModule("libs/member/Api"));
        assertThrows(
                IOException.class,
                () -> resolver.resolve(
                        "./libs/member/Api/module.protos",
                        Optional.of(resolver.entryModule("Main"))));
        assertEquals(
                ProtosWorkspacePackageModuleKey.encode(ProtosModuleKey.Kind.MODULE, "member-pkg", "Api"),
                resolver.resolve(
                        "self:Api",
                        Optional.of(
                                ProtosWorkspacePackageModuleKey.encode(
                                        ProtosModuleKey.Kind.MODULE, "member-pkg", "Api"))));
    }

    private static ProtosPackageExecutionPlan.PackageNode node(
            String packageId, String location) {
        return new ProtosPackageExecutionPlan.PackageNode(
                new ProtosPackageExecutionPlan.WorkspaceRef(packageId),
                location,
                Map.of());
    }
}
