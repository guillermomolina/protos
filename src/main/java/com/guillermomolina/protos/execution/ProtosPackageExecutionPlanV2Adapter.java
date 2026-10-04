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

import static com.guillermomolina.protos.execution.ProtosPackageExecutionPlanAdapter.detachExports;
import static com.guillermomolina.protos.execution.ProtosPackageExecutionPlanAdapter.requireArray;
import static com.guillermomolina.protos.execution.ProtosPackageExecutionPlanAdapter.requireExactFields;
import static com.guillermomolina.protos.execution.ProtosPackageExecutionPlanAdapter.requireField;
import static com.guillermomolina.protos.execution.ProtosPackageExecutionPlanAdapter.requireInteger;
import static com.guillermomolina.protos.execution.ProtosPackageExecutionPlanAdapter.requireMap;
import static com.guillermomolina.protos.execution.ProtosPackageExecutionPlanAdapter.requireObject;
import static com.guillermomolina.protos.execution.ProtosPackageExecutionPlanAdapter.requireString;
import static com.guillermomolina.protos.execution.ProtosPackageExecutionPlanAdapter.validateLocation;

import com.guillermomolina.protos.runtime.ProtosArrayValue;
import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosIntegerValue;
import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Defensive detach of ordinary Protos PackageExecutionPlanV2 data into immutable host data (D053).
 *
 * <p>This boundary validates only the published generation-2 ABI shape and graph closure. Package
 * policy (ReleaseVersion grammar, ContentIdentity method/algorithm/hex policy, lock agreement) stays
 * owned by the bundled Package Tool that produced the plan; this class re-checks structure and
 * internal consistency only. Generation 1 remains owned by {@link
 * ProtosPackageExecutionPlanAdapter}.
 *
 * <p>The project root is used only to confine workspace package locations, exactly as generation 1
 * does. External packages are never validated against, or located through, any host path.
 */
public final class ProtosPackageExecutionPlanV2Adapter {
    private static final Set<String> PLAN_FIELDS =
            Set.of("generation", "root", "packages", "dependencies");
    private static final Set<String> WORKSPACE_REF_FIELDS = Set.of("kind", "packageId");
    private static final Set<String> REGISTRY_REF_FIELDS = Set.of("kind", "packageId", "version");
    private static final Set<String> GIT_REF_FIELDS = Set.of("kind", "packageId", "revision");
    private static final Set<String> VERSION_FIELDS =
            Set.of("major", "minor", "patch", "prerelease", "text");
    private static final Set<String> PRERELEASE_FIELDS = Set.of("numeric", "number", "text");
    private static final Set<String> WORKSPACE_PACKAGE_FIELDS = Set.of("ref", "location", "exports");
    private static final Set<String> EXTERNAL_PACKAGE_FIELDS = Set.of("ref", "content", "exports");
    private static final Set<String> CONTENT_FIELDS = Set.of("method", "algorithm", "hex");
    private static final Set<String> EDGE_FIELDS = Set.of("declaring", "alias", "target");

    private ProtosPackageExecutionPlanV2Adapter() {}

    public static ProtosPackageExecutionPlanV2 detach(Object rawPlan, Path projectRoot)
            throws IOException {
        Objects.requireNonNull(projectRoot, "projectRoot");
        Path realRoot = projectRoot.toAbsolutePath().normalize().toRealPath();
        if (!Files.isDirectory(realRoot)) {
            throw new IOException("package project root is not a directory");
        }

        ProtosObjectValue plan = requireObject(rawPlan, "plan");
        requireExactFields(plan, PLAN_FIELDS, "plan");

        int generation = requireInteger(plan, "generation").intValueExact();
        if (generation != 2) {
            throw new IOException("unsupported PackageExecutionPlan generation");
        }

        if (!(detachRef(requireField(plan, "root"))
                instanceof ProtosPackageExecutionPlanV2.WorkspaceRef root)) {
            throw new IOException("PackageExecutionPlan root is not a workspace ref");
        }

        ArrayList<ProtosPackageExecutionPlanV2.PackageNode> packages = new ArrayList<>();
        LinkedHashSet<ProtosPackageExecutionPlanV2.NodeRef> refs = new LinkedHashSet<>();
        LinkedHashSet<String> locations = new LinkedHashSet<>();
        ProtosPackageExecutionPlanV2.WorkspacePackage rootPackage = null;

        for (Object rawPackage :
                requireArray(requireField(plan, "packages"), "packages").indexedSnapshot()) {
            ProtosPackageExecutionPlanV2.PackageNode node = detachPackage(rawPackage, realRoot);
            if (!refs.add(node.ref())) {
                throw new IOException("duplicate exact NodeRef in execution plan");
            }
            if (node instanceof ProtosPackageExecutionPlanV2.WorkspacePackage workspace) {
                if (!locations.add(workspace.location())) {
                    throw new IOException("duplicate workspace location in execution plan");
                }
                if (workspace.location().isEmpty()) {
                    rootPackage = workspace;
                }
            }
            packages.add(node);
        }

        if (packages.isEmpty()) {
            throw new IOException("PackageExecutionPlan has no packages");
        }
        if (rootPackage == null || !rootPackage.ref().equals(root)) {
            throw new IOException("PackageExecutionPlan root package mismatch");
        }

        ArrayList<ProtosPackageExecutionPlanV2.DependencyEdge> dependencies = new ArrayList<>();
        LinkedHashSet<Map.Entry<ProtosPackageExecutionPlanV2.NodeRef, String>> edgeKeys =
                new LinkedHashSet<>();

        for (Object rawDependency :
                requireArray(requireField(plan, "dependencies"), "dependencies")
                        .indexedSnapshot()) {
            ProtosObjectValue edge = requireObject(rawDependency, "dependency");
            requireExactFields(edge, EDGE_FIELDS, "dependency");

            ProtosPackageExecutionPlanV2.NodeRef declaring =
                    detachRef(requireField(edge, "declaring"));
            String alias = requireString(requireField(edge, "alias"), "alias");
            ProtosPackageRuntimeNames.requireAlias(alias);
            ProtosPackageExecutionPlanV2.NodeRef target = detachRef(requireField(edge, "target"));

            if (!refs.contains(declaring) || !refs.contains(target)) {
                throw new IOException("PackageExecutionPlan dependency references unknown package");
            }
            if (!edgeKeys.add(Map.entry(declaring, alias))) {
                throw new IOException("duplicate PackageExecutionPlan dependency alias");
            }

            dependencies.add(
                    new ProtosPackageExecutionPlanV2.DependencyEdge(declaring, alias, target));
        }

        return new ProtosPackageExecutionPlanV2(generation, root, packages, dependencies);
    }

    private static ProtosPackageExecutionPlanV2.PackageNode detachPackage(
            Object rawPackage, Path realRoot) throws IOException {
        ProtosObjectValue packageValue = requireObject(rawPackage, "package");
        ProtosPackageExecutionPlanV2.NodeRef ref = detachRef(requireField(packageValue, "ref"));

        if (ref instanceof ProtosPackageExecutionPlanV2.WorkspaceRef workspaceRef) {
            requireExactFields(packageValue, WORKSPACE_PACKAGE_FIELDS, "workspace package");
            String location = requireString(requireField(packageValue, "location"), "location");
            validateLocation(realRoot, location);
            return new ProtosPackageExecutionPlanV2.WorkspacePackage(
                    workspaceRef, location, detachPackageExports(packageValue));
        }

        requireExactFields(packageValue, EXTERNAL_PACKAGE_FIELDS, "external package");
        return new ProtosPackageExecutionPlanV2.ExternalPackage(
                (ProtosPackageExecutionPlanV2.ExternalRef) ref,
                detachContent(requireField(packageValue, "content")),
                detachPackageExports(packageValue));
    }

    private static Map<String, String> detachPackageExports(ProtosObjectValue packageValue)
            throws IOException {
        return detachExports(requireMap(requireField(packageValue, "exports"), "exports"));
    }

    static ProtosPackageExecutionPlanV2.NodeRef detachRef(Object value)
            throws IOException {
        ProtosObjectValue ref = requireObject(value, "node ref");
        String kind = requireString(requireField(ref, "kind"), "ref kind");
        switch (kind) {
            case "workspace" -> {
                requireExactFields(ref, WORKSPACE_REF_FIELDS, "workspace ref");
                return new ProtosPackageExecutionPlanV2.WorkspaceRef(requirePackageId(ref));
            }
            case "registry" -> {
                requireExactFields(ref, REGISTRY_REF_FIELDS, "registry ref");
                return new ProtosPackageExecutionPlanV2.RegistryRef(
                        requirePackageId(ref), detachVersion(requireField(ref, "version")));
            }
            case "git" -> {
                requireExactFields(ref, GIT_REF_FIELDS, "git ref");
                return new ProtosPackageExecutionPlanV2.GitRef(
                        requirePackageId(ref),
                        requireNonEmptyString(requireField(ref, "revision"), "Git revision"));
            }
            default -> throw new IOException("unknown PackageExecutionPlan ref kind");
        }
    }

    private static String requirePackageId(ProtosObjectValue ref) throws IOException {
        return requireNonEmptyString(requireField(ref, "packageId"), "PackageId");
    }

    /**
     * Copies one exact ReleaseVersion record. ReleaseVersion grammar remains owned by bundled
     * {@code self:ReleaseVersion}; this only requires that the canonical text is exactly the
     * rendering of the copied components, so identity by text and by components cannot diverge.
     */
    private static ProtosPackageExecutionPlanV2.ReleaseVersion detachVersion(Object value)
            throws IOException {
        ProtosObjectValue version = requireObject(value, "ReleaseVersion");
        requireExactFields(version, VERSION_FIELDS, "ReleaseVersion");

        BigInteger major = requireNonNegative(version, "major");
        BigInteger minor = requireNonNegative(version, "minor");
        BigInteger patch = requireNonNegative(version, "patch");
        String text = requireNonEmptyString(requireField(version, "text"), "ReleaseVersion text");

        ArrayList<ProtosPackageExecutionPlanV2.PrereleaseIdentifier> prerelease =
                new ArrayList<>();
        Object rawPrerelease = requireField(version, "prerelease");
        if (!(rawPrerelease instanceof ProtosNullValue)) {
            ProtosArrayValue identifiers = requireArray(rawPrerelease, "prerelease");
            for (Object identifier : identifiers.indexedSnapshot()) {
                prerelease.add(detachPrereleaseIdentifier(identifier));
            }
            if (prerelease.isEmpty()) {
                throw new IOException("empty ReleaseVersion prerelease");
            }
        }

        StringBuilder rendered =
                new StringBuilder().append(major).append('.').append(minor).append('.').append(patch);
        for (int index = 0; index < prerelease.size(); index++) {
            rendered.append(index == 0 ? '-' : '.').append(prerelease.get(index).text());
        }
        if (!rendered.toString().equals(text)) {
            throw new IOException("inconsistent ReleaseVersion text");
        }

        return new ProtosPackageExecutionPlanV2.ReleaseVersion(
                major, minor, patch, prerelease, text);
    }

    private static ProtosPackageExecutionPlanV2.PrereleaseIdentifier detachPrereleaseIdentifier(
            Object value) throws IOException {
        ProtosObjectValue identifier = requireObject(value, "prerelease identifier");
        requireExactFields(identifier, PRERELEASE_FIELDS, "prerelease identifier");

        if (!(requireField(identifier, "numeric") instanceof ProtosBooleanValue numericValue)) {
            throw new IOException("prerelease numeric is not a Boolean");
        }
        boolean numeric = numericValue.value();
        String text =
                requireNonEmptyString(requireField(identifier, "text"), "prerelease text");
        Object number = requireField(identifier, "number");

        boolean consistent =
                numeric
                        ? number instanceof ProtosIntegerValue integer
                                && integer.value().signum() >= 0
                                && integer.value().toString().equals(text)
                        : number instanceof ProtosNullValue
                                && !text.chars().allMatch(c -> c >= '0' && c <= '9');
        if (!consistent) {
            throw new IOException("inconsistent prerelease identifier");
        }
        return new ProtosPackageExecutionPlanV2.PrereleaseIdentifier(numeric, text);
    }

    static ProtosPackageContentIdentity detachContent(Object value) throws IOException {
        ProtosObjectValue content = requireObject(value, "ContentIdentity");
        requireExactFields(content, CONTENT_FIELDS, "ContentIdentity");
        return new ProtosPackageContentIdentity(
                requireNonEmptyString(requireField(content, "method"), "ContentIdentity method"),
                requireNonEmptyString(
                        requireField(content, "algorithm"), "ContentIdentity algorithm"),
                requireNonEmptyString(requireField(content, "hex"), "ContentIdentity hex"));
    }

    private static BigInteger requireNonNegative(ProtosObjectValue object, String name)
            throws IOException {
        BigInteger value = requireInteger(object, name);
        if (value.signum() < 0) {
            throw new IOException(name + " is negative");
        }
        return value;
    }

    private static String requireNonEmptyString(Object value, String label) throws IOException {
        String string = requireString(value, label);
        if (string.isEmpty()) {
            throw new IOException("empty " + label);
        }
        return string;
    }
}
