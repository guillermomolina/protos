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

import static com.guillermomolina.protos.execution.ProtosPackageExecutionPlanAdapter.requireArray;
import static com.guillermomolina.protos.execution.ProtosPackageExecutionPlanAdapter.requireExactFields;
import static com.guillermomolina.protos.execution.ProtosPackageExecutionPlanAdapter.requireField;
import static com.guillermomolina.protos.execution.ProtosPackageExecutionPlanAdapter.requireObject;

import com.guillermomolina.protos.runtime.ProtosEnvironmentValue;
import com.guillermomolina.protos.runtime.ProtosFilesystemValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosProcessRuntime;
import com.oracle.truffle.api.source.Source;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Read-only Package Tool preflight that derives the complete exact external requirements of the
 * current project before any external package is located, captured or verified (PLAT048 B′).
 *
 * <p>The bundled Package Tool owns lock agreement with the current ResolutionRoot, workspace
 * membership and workspace dependency validation; this class only hosts that operation in a fresh
 * Package Tool Process over a read-only confined project Filesystem and defensively detaches the
 * result. Each returned {@link ProtosExactExternalPackageIdentity} is an exact NodeRef plus
 * ContentIdentity only: it carries no Path, locator, authority, fetch URL, store, custody or guest
 * value, and the Package Tool Process is terminated before this method returns. Locating a
 * materialization for these identities is host authority outside this class.
 */
final class ProtosExactExternalRequirementsPreflight {
    private static final String REQUIREMENTS_SOURCE =
            "Plan: import(\"self:ExecutionPlan\")\n"
                    + "Plan.exactExternalRequirements(projectTreeFilesystem)\n";
    private static final Set<String> REQUIREMENT_FIELDS = Set.of("ref", "content");

    private ProtosExactExternalRequirementsPreflight() {}

    static List<ProtosExactExternalPackageIdentity> derive(
            Path coreRoot,
            Path packageToolRoot,
            Path projectRoot,
            ProtosModuleResolver standardLibraryResolver)
            throws IOException {
        return derive(
                coreRoot, packageToolRoot, projectRoot, standardLibraryResolver, ignored -> {});
    }

    static List<ProtosExactExternalPackageIdentity> derive(
            Path coreRoot,
            Path packageToolRoot,
            Path projectRoot,
            ProtosModuleResolver standardLibraryResolver,
            Consumer<ProtosProcessRuntime> processObserver)
            throws IOException {
        Objects.requireNonNull(coreRoot, "coreRoot");
        Objects.requireNonNull(packageToolRoot, "packageToolRoot");
        Objects.requireNonNull(projectRoot, "projectRoot");
        Objects.requireNonNull(standardLibraryResolver, "standardLibraryResolver");
        Objects.requireNonNull(processObserver, "processObserver");

        try (ProtosPolyglotRuntimeHost runtimeHost = ProtosPolyglotRuntimeHost.open();
                ProtosNioReadOnlyTreeFilesystemBackend backend =
                        new ProtosNioReadOnlyTreeFilesystemBackend(projectRoot)) {
            if (!backend.secureConfinementAvailable()) {
                throw new IOException(
                        "secure read-only external requirements preflight is unavailable on this host");
            }

            ProtosBundledToolModuleResolver toolResolver =
                    new ProtosBundledToolModuleResolver(
                            "package",
                            packageToolRoot,
                            packageToolRoot.resolveSibling("shared"),
                            standardLibraryResolver);
            ProtosPrelude toolPrelude =
                    new ProtosCoreBootstrap().bootstrap(coreRoot, toolResolver);
            ProtosStandaloneProcessBootstrap.Result bootstrap =
                    ProtosStandaloneProcessBootstrap.create(
                            toolPrelude,
                            List.of(),
                            emptyEnvironmentDomain(),
                            List.of(),
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null);
            ProtosProcessRuntime process = bootstrap.process();

            try {
                ProtosPolyglotProcessContext processContext =
                        runtimeHost.hostProcess(
                                process,
                                InputStream.nullInputStream(),
                                OutputStream.nullOutputStream(),
                                OutputStream.nullOutputStream());
                processObserver.accept(process);

                ProtosObjectValue rawFilesystem =
                        ProtosStandardFilesystemProtocol.createCapability(
                                toolPrelude.bytesPrototypeForRuntime(),
                                bootstrap.activation(),
                                backend);
                if (!(rawFilesystem instanceof ProtosFilesystemValue filesystem)) {
                    throw new IOException(
                            "Package Tool project Filesystem has the wrong value family");
                }
                var context = bootstrap.activation().context();
                if (context.hasLocalSlot("projectTreeFilesystem")) {
                    throw new IOException(
                            "Package Tool project Filesystem bootstrap slot already exists");
                }
                context.createLocalSlot("projectTreeFilesystem", filesystem);

                Source source =
                        Source.newBuilder(
                                        ProtosLanguage.ID,
                                        REQUIREMENTS_SOURCE,
                                        "<exact-external-requirements-preflight>")
                                .mimeType(ProtosLanguage.MIME_TYPE)
                                .build();
                ProtosExecutionOutcome outcome =
                        processContext.execute(source, bootstrap.activation());
                if (outcome.state() != ProtosExecutionOutcome.State.COMPLETED) {
                    throw new IOException(
                            "external requirements Package Tool preflight did not complete normally");
                }
                return detach(outcome.value());
            } catch (IOException failure) {
                throw failure;
            } catch (RuntimeException failure) {
                throw new IOException("external requirements Package Tool preflight failed", failure);
            } finally {
                process.requestTerminationForRuntime();
            }
        }
    }

    /**
     * Defensively copies the Package Tool requirements Array into immutable host identities.
     *
     * <p>Only the exact {@code {ref, content}} shape with a registry or Git NodeRef is accepted.
     * Duplicates are rejected by complete exact identity, never by PackageId or ContentIdentity
     * alone, matching the lock's one-node-per-NodeRef model.
     */
    static List<ProtosExactExternalPackageIdentity> detach(Object rawRequirements)
            throws IOException {
        LinkedHashSet<ProtosExactExternalPackageIdentity> identities = new LinkedHashSet<>();
        LinkedHashSet<ProtosPackageExecutionPlanV2.ExternalRef> refs = new LinkedHashSet<>();

        for (Object rawRequirement :
                requireArray(rawRequirements, "external requirements").indexedSnapshot()) {
            ProtosObjectValue requirement = requireObject(rawRequirement, "external requirement");
            requireExactFields(requirement, REQUIREMENT_FIELDS, "external requirement");

            if (!(ProtosPackageExecutionPlanV2Adapter.detachRef(requireField(requirement, "ref"))
                    instanceof ProtosPackageExecutionPlanV2.ExternalRef ref)) {
                throw new IOException("external requirement ref is not an external NodeRef");
            }
            ProtosPackageContentIdentity content =
                    ProtosPackageExecutionPlanV2Adapter.detachContent(
                            requireField(requirement, "content"));

            if (!refs.add(ref) || !identities.add(ref.identity(content))) {
                throw new IOException("duplicate exact external requirement");
            }
        }

        return List.copyOf(identities);
    }

    private static ProtosEnvironmentValue.NativeNameDomain emptyEnvironmentDomain() {
        return new ProtosEnvironmentValue.NativeNameDomain() {
            @Override
            public boolean sameCapturedName(String left, String right) {
                return left.equals(right);
            }

            @Override
            public boolean isQueryRepresentable(String name) {
                return !name.contains("=") && name.indexOf('\0') < 0;
            }

            @Override
            public boolean matchesQuery(String captured, String query) {
                return captured.equals(query);
            }
        };
    }
}
