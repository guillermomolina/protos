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

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable host DTO detached from the Protos-owned PackageExecutionPlanV2 value (D053).
 *
 * <p>Generation 2 is one inert mixed workspace/registry/Git package graph. Nodes are unique by exact
 * typed {@link NodeRef}, so several exact versions or revisions of one PackageId may coexist. Only
 * external packages carry a ContentIdentity; only workspace packages carry a location, which is
 * never external identity. No locator, mirror, store/cache path, custody, resolver authority, host
 * handle or credential is represented. Graph-level validation belongs to
 * {@link ProtosPackageExecutionPlanV2Adapter}.
 */
public record ProtosPackageExecutionPlanV2(
        int generation,
        WorkspaceRef root,
        List<PackageNode> packages,
        List<DependencyEdge> dependencies) {

    public ProtosPackageExecutionPlanV2 {
        if (generation != 2) {
            throw new IllegalArgumentException("unsupported package execution plan generation");
        }
        Objects.requireNonNull(root, "root");
        packages = List.copyOf(Objects.requireNonNull(packages, "packages"));
        dependencies = List.copyOf(Objects.requireNonNull(dependencies, "dependencies"));
    }

    /** Exact typed node reference; record equality is the exact NodeRef identity. */
    public sealed interface NodeRef {
        String packageId();
    }

    public sealed interface ExternalRef extends NodeRef {
        ProtosExactExternalPackageIdentity identity(ProtosPackageContentIdentity content);
    }

    public record WorkspaceRef(String packageId) implements NodeRef {
        public WorkspaceRef {
            Objects.requireNonNull(packageId, "packageId");
        }
    }

    public record RegistryRef(String packageId, ReleaseVersion version) implements ExternalRef {
        public RegistryRef {
            Objects.requireNonNull(packageId, "packageId");
            Objects.requireNonNull(version, "version");
        }

        @Override
        public ProtosExactExternalPackageIdentity identity(ProtosPackageContentIdentity content) {
            return new ProtosExactExternalPackageIdentity.Registry(
                    packageId, version.text(), content);
        }
    }

    public record GitRef(String packageId, String revision) implements ExternalRef {
        public GitRef {
            Objects.requireNonNull(packageId, "packageId");
            Objects.requireNonNull(revision, "revision");
        }

        @Override
        public ProtosExactExternalPackageIdentity identity(ProtosPackageContentIdentity content) {
            return new ProtosExactExternalPackageIdentity.Git(packageId, revision, content);
        }
    }

    /**
     * Detached exact ReleaseVersion. {@code text} is the canonical spelling; an empty
     * {@code prerelease} list represents the Protos {@code null} of a stable release.
     */
    public record ReleaseVersion(
            BigInteger major,
            BigInteger minor,
            BigInteger patch,
            List<PrereleaseIdentifier> prerelease,
            String text) {
        public ReleaseVersion {
            Objects.requireNonNull(major, "major");
            Objects.requireNonNull(minor, "minor");
            Objects.requireNonNull(patch, "patch");
            prerelease = List.copyOf(Objects.requireNonNull(prerelease, "prerelease"));
            Objects.requireNonNull(text, "text");
        }
    }

    /** One prerelease identifier; a numeric identifier's number is exactly its decimal text. */
    public record PrereleaseIdentifier(boolean numeric, String text) {
        public PrereleaseIdentifier {
            Objects.requireNonNull(text, "text");
        }
    }

    public sealed interface PackageNode {
        NodeRef ref();

        Map<String, String> exports();
    }

    public record WorkspacePackage(
            WorkspaceRef ref,
            String location,
            Map<String, String> exports) implements PackageNode {
        public WorkspacePackage {
            Objects.requireNonNull(ref, "ref");
            Objects.requireNonNull(location, "location");
            exports = Map.copyOf(Objects.requireNonNull(exports, "exports"));
        }
    }

    public record ExternalPackage(
            ExternalRef ref,
            ProtosPackageContentIdentity content,
            Map<String, String> exports) implements PackageNode {
        public ExternalPackage {
            Objects.requireNonNull(ref, "ref");
            Objects.requireNonNull(content, "content");
            exports = Map.copyOf(Objects.requireNonNull(exports, "exports"));
        }

        public ProtosExactExternalPackageIdentity identity() {
            return ref.identity(content);
        }
    }

    public record DependencyEdge(
            NodeRef declaring,
            String alias,
            NodeRef target) {
        public DependencyEdge {
            Objects.requireNonNull(declaring, "declaring");
            Objects.requireNonNull(alias, "alias");
            Objects.requireNonNull(target, "target");
        }
    }
}
