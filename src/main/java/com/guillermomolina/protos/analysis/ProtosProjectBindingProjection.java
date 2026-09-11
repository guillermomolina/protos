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
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Inert immutable package-owned projection consumed by future ProjectBinding providers.
 *
 * <p>The projection carries already-canonical project/package identity and one opaque freshness
 * witness. It does not discover projects, read manifests/locks, enumerate source files, or define
 * how the package authority produces or persists the projection.
 */
public record ProtosProjectBindingProjection(
        int generation,
        Path canonicalProjectRoot,
        String rootPackageId,
        List<PackageRef> packages,
        String freshnessWitness) {

    public static final int CURRENT_GENERATION = 1;

    public ProtosProjectBindingProjection {
        if (generation != CURRENT_GENERATION) {
            throw new IllegalArgumentException("unsupported ProjectBinding projection generation");
        }
        canonicalProjectRoot = requireCanonicalAbsolute(canonicalProjectRoot, "canonicalProjectRoot");
        rootPackageId = requireText(rootPackageId, "rootPackageId");
        freshnessWitness = requireText(freshnessWitness, "freshnessWitness");
        packages = List.copyOf(Objects.requireNonNull(packages, "packages"));
        if (packages.isEmpty()) {
            throw new IllegalArgumentException("ProjectBinding projection has no workspace packages");
        }

        Set<String> packageIds = new HashSet<>();
        Set<String> locations = new HashSet<>();
        PackageRef physicalRoot = null;
        for (PackageRef packageRef : packages) {
            Objects.requireNonNull(packageRef, "packages contains null");
            if (!packageIds.add(packageRef.packageId())) {
                throw new IllegalArgumentException("duplicate ProjectBinding PackageId");
            }
            if (!locations.add(packageRef.location())) {
                throw new IllegalArgumentException("duplicate ProjectBinding package location");
            }
            if (packageRef.location().isEmpty()) {
                physicalRoot = packageRef;
            }
        }
        if (physicalRoot == null || !physicalRoot.packageId().equals(rootPackageId)) {
            throw new IllegalArgumentException("ProjectBinding root package mismatch");
        }
    }

    public record PackageRef(String packageId, String location) {
        public PackageRef {
            packageId = requireText(packageId, "packageId");
            location = Objects.requireNonNull(location, "location");
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
