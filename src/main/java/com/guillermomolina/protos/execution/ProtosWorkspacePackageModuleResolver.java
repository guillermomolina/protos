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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Exact workspace package resolver over the detached workspace execution plan. */
public final class ProtosWorkspacePackageModuleResolver implements ProtosModuleResolver {
    private static final String SELF_PREFIX = "self:";
    private static final String DEP_PREFIX = "dep:";
    private static final String STD_PREFIX = "std:";

    private final ProtosWorkspacePackageDirectoryIndex directoryIndex;
    private final ProtosWorkspacePackageSourceLookup sourceLookup;
    private final Map<String, Map<String, String>> dependencyTargetsByDeclaringPackageId;
    private final ProtosModuleResolver standardLibraryResolver;

    public ProtosWorkspacePackageModuleResolver(
            Path projectRoot, ProtosPackageExecutionPlan plan) throws IOException {
        this(projectRoot, plan, ProtosModuleResolver.rejecting());
    }

    public ProtosWorkspacePackageModuleResolver(
            Path projectRoot, ProtosPackageExecutionPlan plan, ProtosModuleResolver standardLibraryResolver) throws IOException {
        Objects.requireNonNull(projectRoot, "projectRoot");
        Objects.requireNonNull(plan, "plan");

        ProtosWorkspacePackageProjectIndex projectIndex =
                ProtosWorkspacePackageProjectIndex.bind(projectRoot, plan);
        this.directoryIndex = ProtosWorkspacePackageDirectoryIndex.bind(projectIndex);
        this.sourceLookup = ProtosWorkspacePackageSourceLookup.bind(directoryIndex);
        this.dependencyTargetsByDeclaringPackageId = indexDependencies(plan);
        this.standardLibraryResolver = Objects.requireNonNull(standardLibraryResolver, "standardLibraryResolver");
    }

    /** Returns the exact root-package entry identity without inventing an ambient self import. */
    public ProtosModuleKey entryModule(String logicalModule) throws IOException {
        String packageId =
                directoryIndex.rootPackage().packageNode().ref().packageId();
        sourceLookup.requireSource(packageId, logicalModule);
        return ProtosWorkspacePackageModuleKey.encode(packageId, logicalModule);
    }

    @Override
    public ProtosModuleKey resolve(
            String exactSpecifier, Optional<ProtosModuleKey> importingModule)
            throws IOException {
        Objects.requireNonNull(exactSpecifier, "exactSpecifier");
        Objects.requireNonNull(importingModule, "importingModule");

        if (exactSpecifier.startsWith(SELF_PREFIX)) {
            return resolveSelf(exactSpecifier, importingModule);
        }
        if (exactSpecifier.startsWith(DEP_PREFIX)) {
            return resolveDependency(exactSpecifier, importingModule);
        }
        if (exactSpecifier.startsWith(STD_PREFIX)) {
            return resolveStandard(exactSpecifier, importingModule);
        }
        throw new IOException("unsupported workspace package module specifier");
    }

    @Override
    public String loadSource(ProtosModuleKey key) throws IOException {
        Objects.requireNonNull(key, "key");
        if (key.canonicalId().startsWith(STD_PREFIX)) {
            try { return standardLibraryResolver.loadSource(key); }
            catch (IOException e) { throw e; }
            catch (Exception e) { throw new IOException("standard-library source loading failed", e); }
        }
        ProtosWorkspacePackageModuleKey.Address address =
                ProtosWorkspacePackageModuleKey.decode(key);
        Path source = sourceLookup.requireSource(address.packageId(), address.logicalModule());
        return Files.readString(source, StandardCharsets.UTF_8);
    }

    private ProtosModuleKey resolveStandard(
            String exactSpecifier, Optional<ProtosModuleKey> importingModule) throws IOException {
        try {
            ProtosModuleKey key = Objects.requireNonNull(standardLibraryResolver.resolve(exactSpecifier, importingModule), "standard-library resolver returned null ModuleKey");
            if (!key.canonicalId().startsWith(STD_PREFIX)) throw new IOException("standard-library resolver returned a foreign ModuleKey");
            return key;
        } catch (IOException e) { throw e; }
        catch (Exception e) { throw new IOException("standard-library resolution failed", e); }
    }

    private ProtosModuleKey resolveSelf(
            String exactSpecifier, Optional<ProtosModuleKey> importingModule)
            throws IOException {
        ProtosWorkspacePackageModuleKey.Address importer =
                requireOwnedImporter(importingModule, "self import");
        String logicalModule = exactSpecifier.substring(SELF_PREFIX.length());
        sourceLookup.requireSource(importer.packageId(), logicalModule);
        return ProtosWorkspacePackageModuleKey.encode(importer.packageId(), logicalModule);
    }

    private ProtosModuleKey resolveDependency(
            String exactSpecifier, Optional<ProtosModuleKey> importingModule)
            throws IOException {
        ProtosWorkspacePackageModuleKey.Address importer =
                requireOwnedImporter(importingModule, "dependency import");

        String route = exactSpecifier.substring(DEP_PREFIX.length());
        int separator = route.indexOf('/');
        if (separator <= 0 || separator == route.length() - 1) {
            throw new IOException("invalid workspace dependency module specifier");
        }

        String alias = ProtosPackageRuntimeNames.requireAlias(route.substring(0, separator));
        String publicExport =
                ProtosPackageRuntimeNames.requireLogicalName(route.substring(separator + 1));

        Map<String, String> aliases =
                dependencyTargetsByDeclaringPackageId.get(importer.packageId());
        String targetPackageId = aliases == null ? null : aliases.get(alias);
        if (targetPackageId == null) {
            throw new IOException("workspace dependency alias is outside execution plan");
        }

        ProtosWorkspacePackageDirectoryIndex.PackageDirectory target =
                directoryIndex.requirePackage(targetPackageId);
        String internalLogicalModule = target.packageNode().exports().get(publicExport);
        if (internalLogicalModule == null) {
            throw new IOException("workspace dependency export is not visible");
        }
        internalLogicalModule =
                ProtosPackageRuntimeNames.requireLogicalName(internalLogicalModule);

        sourceLookup.requireSource(targetPackageId, internalLogicalModule);
        return ProtosWorkspacePackageModuleKey.encode(
                targetPackageId, internalLogicalModule);
    }

    private ProtosWorkspacePackageModuleKey.Address requireOwnedImporter(
            Optional<ProtosModuleKey> importingModule, String operation)
            throws IOException {
        ProtosModuleKey importer =
                importingModule.orElseThrow(
                        () -> new IOException(operation + " requires an importing package module"));
        ProtosWorkspacePackageModuleKey.Address address =
                ProtosWorkspacePackageModuleKey.decode(importer);
        directoryIndex.requirePackage(address.packageId());
        return address;
    }

    private Map<String, Map<String, String>> indexDependencies(
            ProtosPackageExecutionPlan plan) throws IOException {
        LinkedHashMap<String, LinkedHashMap<String, String>> mutable =
                new LinkedHashMap<>();

        for (ProtosPackageExecutionPlan.DependencyEdge edge : plan.dependencies()) {
            String declaringPackageId = edge.declaring().packageId();
            String targetPackageId = edge.target().packageId();
            directoryIndex.requirePackage(declaringPackageId);
            directoryIndex.requirePackage(targetPackageId);
            String alias = ProtosPackageRuntimeNames.requireAlias(edge.alias());

            LinkedHashMap<String, String> aliases =
                    mutable.computeIfAbsent(
                            declaringPackageId, ignored -> new LinkedHashMap<>());
            if (aliases.putIfAbsent(alias, targetPackageId) != null) {
                throw new IOException("duplicate workspace dependency alias");
            }
        }

        LinkedHashMap<String, Map<String, String>> immutable = new LinkedHashMap<>();
        for (Map.Entry<String, LinkedHashMap<String, String>> entry : mutable.entrySet()) {
            immutable.put(entry.getKey(), Map.copyOf(entry.getValue()));
        }
        return Map.copyOf(immutable);
    }
}
