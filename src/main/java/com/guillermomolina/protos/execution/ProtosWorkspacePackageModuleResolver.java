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
import java.util.Objects;
import java.util.Optional;

/** Exact workspace package resolver, initially closed over importer-relative {@code self:}. */
public final class ProtosWorkspacePackageModuleResolver implements ProtosModuleResolver {
    private static final String SELF_PREFIX = "self:";

    private final ProtosWorkspacePackageDirectoryIndex directoryIndex;
    private final ProtosWorkspacePackageSourceLookup sourceLookup;

    public ProtosWorkspacePackageModuleResolver(
            Path projectRoot, ProtosPackageExecutionPlan plan) throws IOException {
        Objects.requireNonNull(projectRoot, "projectRoot");
        Objects.requireNonNull(plan, "plan");

        ProtosWorkspacePackageProjectIndex projectIndex =
                ProtosWorkspacePackageProjectIndex.bind(projectRoot, plan);
        this.directoryIndex = ProtosWorkspacePackageDirectoryIndex.bind(projectIndex);
        this.sourceLookup = ProtosWorkspacePackageSourceLookup.bind(directoryIndex);
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

        if (!exactSpecifier.startsWith(SELF_PREFIX)) {
            throw new IOException("unsupported workspace package module specifier");
        }
        ProtosModuleKey importer =
                importingModule.orElseThrow(
                        () -> new IOException("self import requires an importing package module"));
        ProtosWorkspacePackageModuleKey.Address importerAddress =
                ProtosWorkspacePackageModuleKey.decode(importer);
        directoryIndex.requirePackage(importerAddress.packageId());

        String logicalModule = exactSpecifier.substring(SELF_PREFIX.length());
        sourceLookup.requireSource(importerAddress.packageId(), logicalModule);
        return ProtosWorkspacePackageModuleKey.encode(
                importerAddress.packageId(), logicalModule);
    }

    @Override
    public String loadSource(ProtosModuleKey key) throws IOException {
        Objects.requireNonNull(key, "key");
        ProtosWorkspacePackageModuleKey.Address address =
                ProtosWorkspacePackageModuleKey.decode(key);
        Path source = sourceLookup.requireSource(address.packageId(), address.logicalModule());
        return Files.readString(source, StandardCharsets.UTF_8);
    }
}
