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

package com.guillermomolina.protos.analysis;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable editor-neutral ProjectBinding snapshot over one canonical package projection.
 *
 * <p>This carrier owns no acquisition, persistence, discovery, filesystem enumeration, manifest,
 * lockfile, guest-execution, or index policy. A future D085 provider supplies already-validated
 * package roots and current canonical sources.
 */
public record ProtosProjectBinding(
        ProtosProjectBindingProjection projection,
        List<PackageRoot> packageRoots,
        List<Source> sources) {

    public ProtosProjectBinding {
        projection = Objects.requireNonNull(projection, "projection");
        packageRoots = List.copyOf(Objects.requireNonNull(packageRoots, "packageRoots"));
        sources = List.copyOf(Objects.requireNonNull(sources, "sources"));

        Map<String, ProtosProjectBindingProjection.PackageRef> projectedById = new HashMap<>();
        for (ProtosProjectBindingProjection.PackageRef packageRef : projection.packages()) {
            projectedById.put(packageRef.packageId(), packageRef);
        }

        Map<String, PackageRoot> rootsById = new HashMap<>();
        Set<String> boundLocations = new HashSet<>();
        for (PackageRoot root : packageRoots) {
            Objects.requireNonNull(root, "packageRoots contains null");
            ProtosProjectBindingProjection.PackageRef projected = projectedById.get(root.packageId());
            if (projected == null || !projected.location().equals(root.location())) {
                throw new IllegalArgumentException("ProjectBinding package root is outside projection");
            }
            if (rootsById.putIfAbsent(root.packageId(), root) != null) {
                throw new IllegalArgumentException("duplicate ProjectBinding package root");
            }
            if (!boundLocations.add(root.location())) {
                throw new IllegalArgumentException("duplicate ProjectBinding bound location");
            }
        }
        if (rootsById.size() != projectedById.size()) {
            throw new IllegalArgumentException("ProjectBinding package roots do not cover projection");
        }

        PackageRoot rootPackage = rootsById.get(projection.rootPackageId());
        if (rootPackage == null
                || !rootPackage.location().isEmpty()
                || !rootPackage.directory().equals(projection.canonicalProjectRoot())) {
            throw new IllegalArgumentException("ProjectBinding canonical project root mismatch");
        }

        Set<String> sourceIdentities = new HashSet<>();
        for (Source source : sources) {
            Objects.requireNonNull(source, "sources contains null");
            PackageRoot packageRoot = rootsById.get(source.packageId());
            if (packageRoot == null) {
                throw new IllegalArgumentException("ProjectBinding source package is outside projection");
            }
            if (!source.source().startsWith(packageRoot.directory())) {
                throw new IllegalArgumentException("ProjectBinding source escaped its package root");
            }
            String identity = source.packageId() + "\u0000" + source.logicalModule();
            if (!sourceIdentities.add(identity)) {
                throw new IllegalArgumentException("duplicate ProjectBinding source identity");
            }
        }
    }

    public record PackageRoot(String packageId, String location, Path directory) {
        public PackageRoot {
            packageId = requireText(packageId, "packageId");
            location = Objects.requireNonNull(location, "location");
            directory = requireCanonicalAbsolute(directory, "directory");
        }
    }

    public record Source(String packageId, String logicalModule, Path source) {
        public Source {
            packageId = requireText(packageId, "packageId");
            logicalModule = requireText(logicalModule, "logicalModule");
            source = requireCanonicalAbsolute(source, "source");
        }
    }

    private static Path requireCanonicalAbsolute(Path path, String name) {
        Objects.requireNonNull(path, name);
        if (!path.isAbsolute() || !path.equals(path.normalize())) {
            throw new IllegalArgumentException(name + " must be absolute and normalized");
        }
        return path;
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be empty");
        }
        return value;
    }
}
