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
import com.guillermomolina.protos.runtime.ProtosProcessRuntime;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * CLI-neutral host driver for one package-backed application run with external package support
 * (PLAT048 B′).
 *
 * <p>The Package Tool first derives the complete exact external requirements. When there are none
 * the run is exactly the generation-1 {@link ProtosWorkspaceRunDriver} route: the materialization
 * provider is never consulted and no capture, V2 plan, resource scope or mixed resolver exists.
 *
 * <p>Otherwise, in requirement order, the host-supplied provider selects one root per complete
 * exact identity, F2E2 captures and verifies it once, F2E3 plans the mixed V2 graph over the
 * verified custodies, F2E4 detaches the plan and reconciles a run-owned resource scope, and one
 * fresh application Process executes over the mixed resolver. The Process is TERMINATED before the
 * scope is closed. The application receives no provider, selected root, Package Tool Process or
 * custody Filesystem; only the resolver reads external resources, and only through the scope.
 *
 * <p>This driver owns no CLI spelling, current-directory policy, default materialization backend
 * or diagnostic text.
 */
public final class ProtosPackageRunDriver {
    private static final Stages PRODUCTION_STAGES = new Stages() {};

    private ProtosPackageRunDriver() {}

    public static ProtosExecutionOutcome execute(
            ProtosWorkspaceRunDriver.Request request,
            ProtosExactPackageMaterializationProvider provider)
            throws IOException {
        return execute(request, provider, PRODUCTION_STAGES);
    }

    static ProtosExecutionOutcome execute(
            ProtosWorkspaceRunDriver.Request request,
            ProtosExactPackageMaterializationProvider provider,
            Stages stages)
            throws IOException {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(stages, "stages");

        List<ProtosExactExternalPackageIdentity> requirements =
                ProtosExactExternalRequirementsPreflight.derive(
                        request.coreRoot(),
                        request.packageToolRoot(),
                        request.projectRoot(),
                        request.standardLibraryResolver());
        if (requirements.isEmpty()) {
            return ProtosWorkspaceRunDriver.execute(request);
        }

        ProtosPackageExecutionPlanV2 plan;
        ProtosExternalPackageResourceScope scope;
        // Until reconcile succeeds this driver owns every verified custody; a failed reconcile
        // transfers nothing. After success the scope is the sole owner and nothing here closes
        // an individual custody again.
        List<ProtosCapturedFilesystemCustody> owned = new ArrayList<>(requirements.size());
        try {
            List<ProtosExternalPackagePlanningPreflight.VerifiedExternalPackage> verified =
                    new ArrayList<>(requirements.size());
            for (ProtosExactExternalPackageIdentity identity : requirements) {
                Path selectedRoot = provider.select(identity);
                if (selectedRoot == null) {
                    throw new IOException("no materialization selected for exact external package");
                }
                ProtosCapturedFilesystemCustody custody =
                        stages.verify(request, identity, selectedRoot);
                owned.add(custody);
                verified.add(
                        ProtosExternalPackagePlanningPreflight.VerifiedExternalPackage
                                .fromIdentity(identity, custody));
            }
            plan = stages.detach(stages.plan(request, verified), request.projectRoot());
            scope = stages.reconcile(plan, verified);
        } catch (Throwable failure) {
            closeAll(owned, failure);
            throw failure;
        }

        try (scope) {
            return executeApplication(request, plan, scope, stages);
        }
    }

    private static ProtosExecutionOutcome executeApplication(
            ProtosWorkspaceRunDriver.Request request,
            ProtosPackageExecutionPlanV2 plan,
            ProtosExternalPackageResourceScope scope,
            Stages stages)
            throws IOException {
        ProtosPackageExecutionPlanV2ModuleResolver resolver =
                new ProtosPackageExecutionPlanV2ModuleResolver(
                        request.projectRoot(), plan, scope, request.standardLibraryResolver());
        ProtosModuleKey entryKey = resolver.entryModule(request.entryLogicalModule());

        try (ProtosPolyglotRuntimeHost runtimeHost = ProtosPolyglotRuntimeHost.open()) {
            // Termination is awaited, not only requested: hosted Actors may still be loading
            // external modules through the scope while the Process unwinds (PLAT012).
            return ProtosWorkspacePackageApplicationExecution.executeEntry(
                    request.coreRoot(),
                    resolver,
                    entryKey,
                    new ProtosWorkspacePackageApplicationExecution.ApplicationAuthority(
                            request.applicationArguments(),
                            request.environmentNameDomain(),
                            request.environmentEntries(),
                            request.stdinBackend(),
                            request.stdoutBackend(),
                            request.stderrBackend(),
                            request.stdinEncodingBinding(),
                            request.stdoutEncodingBinding(),
                            request.stderrEncodingBinding()),
                    true,
                    "package application Process failed",
                    stages::applicationProcessHosted,
                    runtimeHost);
        }
    }

    private static void closeAll(
            List<ProtosCapturedFilesystemCustody> custodies, Throwable failure) {
        for (ProtosCapturedFilesystemCustody custody : custodies) {
            try {
                custody.close();
            } catch (RuntimeException closeFailure) {
                failure.addSuppressed(closeFailure);
            }
        }
    }

    /**
     * The composed F2E2-F2E4 operations. Production uses the defaults unchanged; tests override
     * single stages to observe ownership or force a failure between verification and
     * reconciliation without inventing guest semantics.
     */
    interface Stages {
        default ProtosCapturedFilesystemCustody verify(
                ProtosWorkspaceRunDriver.Request request,
                ProtosExactExternalPackageIdentity identity,
                Path selectedRoot)
                throws IOException {
            ProtosPackageContentIdentity content = identity.content();
            return ProtosPackageContentVerification.captureAndVerify(
                    request.coreRoot(),
                    request.packageToolRoot(),
                    selectedRoot,
                    request.standardLibraryResolver(),
                    content.method(),
                    content.algorithm(),
                    content.hex());
        }

        default Object plan(
                ProtosWorkspaceRunDriver.Request request,
                List<ProtosExternalPackagePlanningPreflight.VerifiedExternalPackage> verified)
                throws IOException {
            return ProtosExternalPackagePlanningPreflight.buildRaw(
                    request.coreRoot(),
                    request.packageToolRoot(),
                    request.projectRoot(),
                    request.standardLibraryResolver(),
                    verified);
        }

        default ProtosPackageExecutionPlanV2 detach(Object rawPlan, Path projectRoot)
                throws IOException {
            return ProtosPackageExecutionPlanV2Adapter.detach(rawPlan, projectRoot);
        }

        default ProtosExternalPackageResourceScope reconcile(
                ProtosPackageExecutionPlanV2 plan,
                List<ProtosExternalPackagePlanningPreflight.VerifiedExternalPackage> verified)
                throws IOException {
            return ProtosExternalPackageResourceScope.reconcile(plan, verified);
        }

        default void applicationProcessHosted(ProtosProcessRuntime process) {}
    }
}
