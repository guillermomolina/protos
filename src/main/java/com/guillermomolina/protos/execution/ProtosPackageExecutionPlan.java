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

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable host DTO detached from the Protos-owned PackageExecutionPlanV1 value. */
public record ProtosPackageExecutionPlan(
        int generation,
        WorkspaceRef root,
        List<PackageNode> packages,
        List<DependencyEdge> dependencies) {

    public ProtosPackageExecutionPlan {
        if (generation != 1) {
            throw new IllegalArgumentException("unsupported package execution plan generation");
        }
        Objects.requireNonNull(root, "root");
        packages = List.copyOf(Objects.requireNonNull(packages, "packages"));
        dependencies = List.copyOf(Objects.requireNonNull(dependencies, "dependencies"));
    }

    public record WorkspaceRef(String packageId) {
        public WorkspaceRef {
            Objects.requireNonNull(packageId, "packageId");
        }
    }

    public record PackageNode(
            WorkspaceRef ref,
            String location,
            Map<String, String> exports) {
        public PackageNode {
            Objects.requireNonNull(ref, "ref");
            Objects.requireNonNull(location, "location");
            exports = Map.copyOf(Objects.requireNonNull(exports, "exports"));
        }
    }

    public record DependencyEdge(
            WorkspaceRef declaring,
            String alias,
            WorkspaceRef target) {
        public DependencyEdge {
            Objects.requireNonNull(declaring, "declaring");
            Objects.requireNonNull(alias, "alias");
            Objects.requireNonNull(target, "target");
        }
    }
}
