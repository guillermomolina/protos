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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable host binding between one selected workspace project root and a detached execution
 * plan's package index.
 *
 * <p>This slice deliberately does not interpret member locations physically. It anchors the real
 * project root and establishes exact PackageId/location uniqueness only. Later source slices bind
 * member locations and logical module paths.
 */
final class ProtosWorkspacePackageProjectIndex {
    private final Path selectedProjectRoot;
    private final Path realProjectRoot;
    private final ProtosPackageExecutionPlan plan;
    private final Map<String, ProtosPackageExecutionPlan.PackageNode> packagesById;
    private final Map<String, ProtosPackageExecutionPlan.PackageNode> packagesByLocation;

    private ProtosWorkspacePackageProjectIndex(
            Path selectedProjectRoot,
            Path realProjectRoot,
            ProtosPackageExecutionPlan plan,
            Map<String, ProtosPackageExecutionPlan.PackageNode> packagesById,
            Map<String, ProtosPackageExecutionPlan.PackageNode> packagesByLocation) {
        this.selectedProjectRoot = selectedProjectRoot;
        this.realProjectRoot = realProjectRoot;
        this.plan = plan;
        this.packagesById = packagesById;
        this.packagesByLocation = packagesByLocation;
    }

    static ProtosWorkspacePackageProjectIndex bind(
            Path projectRoot, ProtosPackageExecutionPlan plan)
            throws IOException {
        Objects.requireNonNull(projectRoot, "projectRoot");
        Objects.requireNonNull(plan, "plan");

        Path selected = projectRoot.toAbsolutePath().normalize();
        Path real = selected.toRealPath();
        if (!Files.isDirectory(real)) {
            throw new IOException("package project root is not a directory");
        }
        if (plan.generation() != 1) {
            throw new IOException("unsupported PackageExecutionPlan generation");
        }

        LinkedHashMap<String, ProtosPackageExecutionPlan.PackageNode> byId =
                new LinkedHashMap<>();
        LinkedHashMap<String, ProtosPackageExecutionPlan.PackageNode> byLocation =
                new LinkedHashMap<>();

        for (ProtosPackageExecutionPlan.PackageNode node : plan.packages()) {
            if (node.ref().packageId().isEmpty()) {
                throw new IOException("empty workspace PackageId");
            }
            if (byId.putIfAbsent(node.ref().packageId(), node) != null) {
                throw new IOException("duplicate workspace PackageId");
            }
            if (byLocation.putIfAbsent(node.location(), node) != null) {
                throw new IOException("duplicate workspace package location");
            }
        }

        if (byId.isEmpty()) {
            throw new IOException("PackageExecutionPlan has no workspace packages");
        }

        ProtosPackageExecutionPlan.PackageNode physicalRoot = byLocation.get("");
        if (physicalRoot == null || !physicalRoot.ref().equals(plan.root())) {
            throw new IOException("PackageExecutionPlan root package mismatch");
        }
        if (!byId.containsKey(plan.root().packageId())) {
            throw new IOException("PackageExecutionPlan root PackageId is absent");
        }

        return new ProtosWorkspacePackageProjectIndex(
                selected,
                real,
                plan,
                Map.copyOf(byId),
                Map.copyOf(byLocation));
    }

    Path selectedProjectRoot() {
        return selectedProjectRoot;
    }

    Path realProjectRoot() {
        return realProjectRoot;
    }

    ProtosPackageExecutionPlan plan() {
        return plan;
    }

    ProtosPackageExecutionPlan.PackageNode rootPackage() {
        return packagesByLocation.get("");
    }

    ProtosPackageExecutionPlan.PackageNode requirePackage(String packageId)
            throws IOException {
        ProtosPackageExecutionPlan.PackageNode node = packagesById.get(packageId);
        if (node == null) {
            throw new IOException("workspace PackageId is outside execution plan");
        }
        return node;
    }

    ProtosPackageExecutionPlan.PackageNode requireLocation(String location)
            throws IOException {
        ProtosPackageExecutionPlan.PackageNode node = packagesByLocation.get(location);
        if (node == null) {
            throw new IOException("workspace location is outside execution plan");
        }
        return node;
    }
}
