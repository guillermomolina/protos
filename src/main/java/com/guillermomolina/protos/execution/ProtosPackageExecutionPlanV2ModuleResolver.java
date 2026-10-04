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

import com.guillermomolina.protos.runtime.ProtosModuleKey;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Exact package resolver over one detached mixed workspace/registry/Git PackageExecutionPlanV2
 * (D053, PLAT012).
 *
 * <p>Routing uses the same {@code self:}, {@code dep:} and {@code std:} domains as
 * {@link ProtosWorkspacePackageModuleResolver}. An importer's declaring package is recovered only
 * from its canonical workspace or external ModuleKey; {@code dep:} follows the plan's exact
 * {@code declaring NodeRef + alias -> target NodeRef} edge and the target's exports. The resolver
 * never solves, fetches, consults a lock or creates edges.
 *
 * <p>Workspace packages keep the generation-1 physical source rules under the selected project
 * root. External packages are read lazily, per load, as package-relative resources from the
 * borrowed {@link ProtosExternalPackageResourceScope}; no source, store or cache path is reopened
 * and nothing is cached. The scope is borrowed: this resolver never closes it, and once its owner
 * closes it external source loading fails.
 */
final class ProtosPackageExecutionPlanV2ModuleResolver implements ProtosModuleResolver {
    private static final String SELF_PREFIX = "self:";
    private static final String DEP_PREFIX = "dep:";
    private static final String STD_PREFIX = "std:";
    private static final String SOURCE_SUFFIX = ".protos";

    private final ProtosPackageExecutionPlanV2.WorkspaceRef root;
    private final Map<ProtosPackageExecutionPlanV2.NodeRef, ProtosPackageExecutionPlanV2.PackageNode>
            packagesByRef;
    private final Map<ProtosExactExternalPackageIdentity, ProtosPackageExecutionPlanV2.ExternalPackage>
            externalsByIdentity;
    private final Map<
                    ProtosPackageExecutionPlanV2.NodeRef,
                    Map<String, ProtosPackageExecutionPlanV2.NodeRef>>
            dependencyTargetsByDeclaringRef;
    private final ProtosWorkspacePackageSourceLookup workspaceSources;
    private final ProtosExternalPackageResourceScope externalResources;
    private final ProtosModuleResolver standardLibraryResolver;

    ProtosPackageExecutionPlanV2ModuleResolver(
            Path projectRoot,
            ProtosPackageExecutionPlanV2 plan,
            ProtosExternalPackageResourceScope externalResources,
            ProtosModuleResolver standardLibraryResolver)
            throws IOException {
        Objects.requireNonNull(projectRoot, "projectRoot");
        Objects.requireNonNull(plan, "plan");
        this.externalResources = Objects.requireNonNull(externalResources, "externalResources");
        this.standardLibraryResolver =
                Objects.requireNonNull(standardLibraryResolver, "standardLibraryResolver");

        HashMap<ProtosPackageExecutionPlanV2.NodeRef, ProtosPackageExecutionPlanV2.PackageNode>
                byRef = new HashMap<>();
        HashMap<ProtosExactExternalPackageIdentity, ProtosPackageExecutionPlanV2.ExternalPackage>
                byIdentity = new HashMap<>();
        List<ProtosPackageExecutionPlan.PackageNode> workspaceNodes = new ArrayList<>();
        for (ProtosPackageExecutionPlanV2.PackageNode node : plan.packages()) {
            if (byRef.putIfAbsent(node.ref(), node) != null) {
                throw new IOException("duplicate exact NodeRef in execution plan");
            }
            switch (node) {
                case ProtosPackageExecutionPlanV2.WorkspacePackage workspace ->
                        workspaceNodes.add(
                                new ProtosPackageExecutionPlan.PackageNode(
                                        new ProtosPackageExecutionPlan.WorkspaceRef(
                                                workspace.ref().packageId()),
                                        workspace.location(),
                                        workspace.exports()));
                case ProtosPackageExecutionPlanV2.ExternalPackage external -> {
                    if (byIdentity.putIfAbsent(external.identity(), external) != null) {
                        throw new IOException(
                                "duplicate exact external identity in execution plan");
                    }
                    requireCustody(external.identity());
                }
            }
        }
        if (!(byRef.get(plan.root())
                instanceof ProtosPackageExecutionPlanV2.WorkspacePackage)) {
            throw new IOException("PackageExecutionPlan root workspace package is absent");
        }
        this.root = plan.root();
        this.packagesByRef = Map.copyOf(byRef);
        this.externalsByIdentity = Map.copyOf(byIdentity);
        this.dependencyTargetsByDeclaringRef = indexDependencies(plan, packagesByRef);

        // The workspace-only projection exists solely to reuse the unchanged generation-1
        // physical binding (confinement, exact spelling, child package boundaries). It carries
        // no edges: V2 routing never consults it.
        ProtosPackageExecutionPlan workspaceProjection =
                new ProtosPackageExecutionPlan(
                        1,
                        new ProtosPackageExecutionPlan.WorkspaceRef(root.packageId()),
                        workspaceNodes,
                        List.of());
        this.workspaceSources =
                ProtosWorkspacePackageSourceLookup.bind(
                        ProtosWorkspacePackageDirectoryIndex.bind(
                                ProtosWorkspacePackageProjectIndex.bind(
                                        projectRoot, workspaceProjection)));
    }

    /** Returns the exact root-package entry identity without inventing an ambient self import. */
    ProtosModuleKey entryModule(String logicalModule) throws IOException {
        workspaceSources.requireSource(root.packageId(), logicalModule);
        return ProtosWorkspacePackageModuleKey.encode(root.packageId(), logicalModule);
    }

    @Override
    public ProtosModuleKey resolve(
            String exactSpecifier, Optional<ProtosModuleKey> importingModule)
            throws IOException {
        Objects.requireNonNull(exactSpecifier, "exactSpecifier");
        Objects.requireNonNull(importingModule, "importingModule");

        if (exactSpecifier.startsWith(SELF_PREFIX)) {
            ProtosPackageExecutionPlanV2.PackageNode importer =
                    requireImporter(importingModule, "self import");
            return moduleKey(importer, exactSpecifier.substring(SELF_PREFIX.length()));
        }
        if (exactSpecifier.startsWith(DEP_PREFIX)) {
            return resolveDependency(exactSpecifier, importingModule);
        }
        if (exactSpecifier.startsWith(STD_PREFIX)) {
            return resolveStandard(exactSpecifier, importingModule);
        }
        throw new IOException("unsupported package module specifier");
    }

    @Override
    public ProtosModuleSource loadSource(ProtosModuleKey key) throws IOException {
        Objects.requireNonNull(key, "key");
        if (key.canonicalId().startsWith(STD_PREFIX)) {
            try {
                return standardLibraryResolver.loadSource(key);
            } catch (IOException e) {
                throw e;
            } catch (Exception e) {
                throw new IOException("standard-library source loading failed", e);
            }
        }
        if (ProtosWorkspacePackageModuleKey.owns(key)) {
            ProtosWorkspacePackageModuleKey.Address address =
                    ProtosWorkspacePackageModuleKey.decode(key);
            requireWorkspace(address.packageId());
            return ProtosModuleSource.fromPath(
                    key,
                    workspaceSources.requireSource(
                            address.packageId(), address.logicalModule()));
        }
        if (ProtosExternalPackageModuleKey.owns(key)) {
            ProtosExternalPackageModuleKey.Address address =
                    ProtosExternalPackageModuleKey.decode(key);
            requireExternal(address.identity());
            return ProtosModuleSource.fromCharacters(
                    key,
                    decodeStrictUtf8(
                            readExternalResource(
                                    address.identity(),
                                    sourceResourceName(address.logicalModule()))));
        }
        throw new IOException("module key is outside package execution plan domains");
    }

    private ProtosModuleKey resolveDependency(
            String exactSpecifier, Optional<ProtosModuleKey> importingModule)
            throws IOException {
        ProtosPackageExecutionPlanV2.PackageNode importer =
                requireImporter(importingModule, "dependency import");

        String route = exactSpecifier.substring(DEP_PREFIX.length());
        int separator = route.indexOf('/');
        if (separator <= 0 || separator == route.length() - 1) {
            throw new IOException("invalid package dependency module specifier");
        }
        String alias = ProtosPackageRuntimeNames.requireAlias(route.substring(0, separator));
        String publicExport =
                ProtosPackageRuntimeNames.requireLogicalName(route.substring(separator + 1));

        Map<String, ProtosPackageExecutionPlanV2.NodeRef> aliases =
                dependencyTargetsByDeclaringRef.get(importer.ref());
        ProtosPackageExecutionPlanV2.NodeRef targetRef = aliases == null ? null : aliases.get(alias);
        if (targetRef == null) {
            throw new IOException("package dependency alias is outside execution plan");
        }
        ProtosPackageExecutionPlanV2.PackageNode target = packagesByRef.get(targetRef);
        String internalLogicalModule = target.exports().get(publicExport);
        if (internalLogicalModule == null) {
            throw new IOException("package dependency export is not visible");
        }
        return moduleKey(target, internalLogicalModule);
    }

    private ProtosModuleKey resolveStandard(
            String exactSpecifier, Optional<ProtosModuleKey> importingModule) throws IOException {
        try {
            ProtosModuleKey key =
                    Objects.requireNonNull(
                            standardLibraryResolver.resolve(exactSpecifier, importingModule),
                            "standard-library resolver returned null ModuleKey");
            if (!key.canonicalId().startsWith(STD_PREFIX)) {
                throw new IOException("standard-library resolver returned a foreign ModuleKey");
            }
            return key;
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("standard-library resolution failed", e);
        }
    }

    /**
     * Canonical identity of one logical module in {@code node}. Workspace modules must exist under
     * the workspace physical rules; external existence is checked lazily by {@link #loadSource}.
     */
    private ProtosModuleKey moduleKey(
            ProtosPackageExecutionPlanV2.PackageNode node, String logicalModule)
            throws IOException {
        String checkedModule = ProtosPackageRuntimeNames.requireLogicalName(logicalModule);
        return switch (node) {
            case ProtosPackageExecutionPlanV2.WorkspacePackage workspace -> {
                workspaceSources.requireSource(workspace.ref().packageId(), checkedModule);
                yield ProtosWorkspacePackageModuleKey.encode(
                        workspace.ref().packageId(), checkedModule);
            }
            case ProtosPackageExecutionPlanV2.ExternalPackage external ->
                    ProtosExternalPackageModuleKey.encode(external.identity(), checkedModule);
        };
    }

    private ProtosPackageExecutionPlanV2.PackageNode requireImporter(
            Optional<ProtosModuleKey> importingModule, String operation) throws IOException {
        ProtosModuleKey importer =
                importingModule.orElseThrow(
                        () -> new IOException(operation + " requires an importing package module"));
        if (ProtosWorkspacePackageModuleKey.owns(importer)) {
            return requireWorkspace(ProtosWorkspacePackageModuleKey.decode(importer).packageId());
        }
        if (ProtosExternalPackageModuleKey.owns(importer)) {
            return requireExternal(ProtosExternalPackageModuleKey.decode(importer).identity());
        }
        throw new IOException(operation + " requires an importing package module");
    }

    private ProtosPackageExecutionPlanV2.WorkspacePackage requireWorkspace(String packageId)
            throws IOException {
        if (packagesByRef.get(new ProtosPackageExecutionPlanV2.WorkspaceRef(packageId))
                instanceof ProtosPackageExecutionPlanV2.WorkspacePackage workspace) {
            return workspace;
        }
        throw new IOException("workspace PackageId is outside execution plan");
    }

    private ProtosPackageExecutionPlanV2.ExternalPackage requireExternal(
            ProtosExactExternalPackageIdentity identity) throws IOException {
        ProtosPackageExecutionPlanV2.ExternalPackage external = externalsByIdentity.get(identity);
        if (external == null) {
            throw new IOException("exact external package identity is outside execution plan");
        }
        return external;
    }

    private void requireCustody(ProtosExactExternalPackageIdentity identity) throws IOException {
        try {
            if (!externalResources.contains(identity)) {
                throw new IOException("external execution plan node has no exact custody");
            }
        } catch (IllegalStateException closed) {
            throw new IOException("external package resource scope is closed", closed);
        }
    }

    private byte[] readExternalResource(
            ProtosExactExternalPackageIdentity identity, ProtosPackageResourceName name)
            throws IOException {
        try {
            return externalResources.readResource(identity, name);
        } catch (IllegalStateException | IllegalArgumentException unavailable) {
            throw new IOException("external package source is unavailable", unavailable);
        }
    }

    /** {@code Internal/Helper} becomes the package-relative resource {@code Internal/Helper.protos}. */
    private static ProtosPackageResourceName sourceResourceName(String logicalModule)
            throws IOException {
        String[] segments =
                ProtosPackageRuntimeNames.requireLogicalName(logicalModule).split("/", -1);
        segments[segments.length - 1] = segments[segments.length - 1] + SOURCE_SUFFIX;
        return new ProtosPackageResourceName(List.of(segments));
    }

    private static String decodeStrictUtf8(byte[] bytes) throws IOException {
        try {
            return StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException invalidUtf8) {
            throw new IOException("external package source is not valid UTF-8", invalidUtf8);
        }
    }

    private static Map<
                    ProtosPackageExecutionPlanV2.NodeRef,
                    Map<String, ProtosPackageExecutionPlanV2.NodeRef>>
            indexDependencies(
                    ProtosPackageExecutionPlanV2 plan,
                    Map<ProtosPackageExecutionPlanV2.NodeRef, ProtosPackageExecutionPlanV2.PackageNode>
                            packagesByRef)
                    throws IOException {
        HashMap<ProtosPackageExecutionPlanV2.NodeRef, HashMap<String, ProtosPackageExecutionPlanV2.NodeRef>>
                mutable = new HashMap<>();
        for (ProtosPackageExecutionPlanV2.DependencyEdge edge : plan.dependencies()) {
            if (!packagesByRef.containsKey(edge.declaring())
                    || !packagesByRef.containsKey(edge.target())) {
                throw new IOException("PackageExecutionPlan dependency references unknown package");
            }
            String alias = ProtosPackageRuntimeNames.requireAlias(edge.alias());
            if (mutable.computeIfAbsent(edge.declaring(), ignored -> new HashMap<>())
                            .putIfAbsent(alias, edge.target())
                    != null) {
                throw new IOException("duplicate PackageExecutionPlan dependency alias");
            }
        }
        HashMap<ProtosPackageExecutionPlanV2.NodeRef, Map<String, ProtosPackageExecutionPlanV2.NodeRef>>
                immutable = new HashMap<>();
        for (var entry : mutable.entrySet()) {
            immutable.put(entry.getKey(), Map.copyOf(entry.getValue()));
        }
        return Map.copyOf(immutable);
    }
}
