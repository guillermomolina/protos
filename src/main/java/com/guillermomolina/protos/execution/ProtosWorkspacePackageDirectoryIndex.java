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

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Immutable physical-directory bindings for the detached workspace package index. */
final class ProtosWorkspacePackageDirectoryIndex {
    record PackageDirectory(
            ProtosPackageExecutionPlan.PackageNode packageNode,
            Path directory) {
        PackageDirectory {
            Objects.requireNonNull(packageNode, "packageNode");
            Objects.requireNonNull(directory, "directory");
        }
    }

    private final ProtosWorkspacePackageProjectIndex projectIndex;
    private final Map<String, PackageDirectory> packagesById;
    private final Map<String, PackageDirectory> packagesByLocation;

    private ProtosWorkspacePackageDirectoryIndex(
            ProtosWorkspacePackageProjectIndex projectIndex,
            Map<String, PackageDirectory> packagesById,
            Map<String, PackageDirectory> packagesByLocation) {
        this.projectIndex = projectIndex;
        this.packagesById = packagesById;
        this.packagesByLocation = packagesByLocation;
    }

    static ProtosWorkspacePackageDirectoryIndex bind(
            ProtosWorkspacePackageProjectIndex projectIndex)
            throws IOException {
        Objects.requireNonNull(projectIndex, "projectIndex");

        LinkedHashMap<String, PackageDirectory> byId = new LinkedHashMap<>();
        LinkedHashMap<String, PackageDirectory> byLocation = new LinkedHashMap<>();
        Path realProjectRoot = projectIndex.realProjectRoot();

        for (ProtosPackageExecutionPlan.PackageNode node : projectIndex.plan().packages()) {
            Path directory =
                    node.location().isEmpty()
                            ? realProjectRoot
                            : ProtosWorkspaceMemberLocationTraversal
                                    .requireConfinedMemberDirectory(
                                            realProjectRoot, node.location());
            PackageDirectory binding = new PackageDirectory(node, directory);

            if (byId.putIfAbsent(node.ref().packageId(), binding) != null) {
                throw new IOException("duplicate workspace PackageId while binding directories");
            }
            if (byLocation.putIfAbsent(node.location(), binding) != null) {
                throw new IOException("duplicate workspace location while binding directories");
            }
        }

        PackageDirectory root = byLocation.get("");
        if (root == null
                || !root.packageNode().equals(projectIndex.rootPackage())
                || !root.directory().equals(realProjectRoot)) {
            throw new IOException("workspace root directory binding mismatch");
        }

        return new ProtosWorkspacePackageDirectoryIndex(
                projectIndex, Map.copyOf(byId), Map.copyOf(byLocation));
    }

    ProtosWorkspacePackageProjectIndex projectIndex() {
        return projectIndex;
    }

    PackageDirectory rootPackage() {
        return packagesByLocation.get("");
    }

    PackageDirectory requirePackage(String packageId) throws IOException {
        PackageDirectory binding = packagesById.get(packageId);
        if (binding == null) {
            throw new IOException("workspace PackageId has no physical directory binding");
        }
        return binding;
    }

    PackageDirectory requireLocation(String location) throws IOException {
        PackageDirectory binding = packagesByLocation.get(location);
        if (binding == null) {
            throw new IOException("workspace location has no physical directory binding");
        }
        return binding;
    }
}
