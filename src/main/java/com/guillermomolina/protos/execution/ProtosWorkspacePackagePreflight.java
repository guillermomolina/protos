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

import com.guillermomolina.protos.runtime.ProtosEnvironmentValue;
import com.guillermomolina.protos.runtime.ProtosFilesystemValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosProcessRuntime;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** Read-only Package Tool preflight that returns only a detached workspace execution plan. */
public final class ProtosWorkspacePackagePreflight {
    private static final String BUILD_PLAN_SOURCE =
            "Plan: import(\"self:ExecutionPlan\")\n"
                    + "Plan.build(projectTreeFilesystem)\n";

    private ProtosWorkspacePackagePreflight() {}

    public static ProtosPackageExecutionPlan build(
            Path coreRoot,
            Path packageToolRoot,
            Path projectRoot,
            ProtosModuleResolver standardLibraryResolver)
            throws IOException {
        return build(
                coreRoot,
                packageToolRoot,
                projectRoot,
                standardLibraryResolver,
                ignored -> {});
    }

    static ProtosPackageExecutionPlan build(
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

        try (ProtosNioReadOnlyTreeFilesystemBackend backend =
                new ProtosNioReadOnlyTreeFilesystemBackend(projectRoot)) {
            if (!backend.secureConfinementAvailable()) {
                throw new IOException(
                        "secure read-only workspace preflight is unavailable on this host");
            }

            ProtosBundledToolModuleResolver toolResolver =
                    new ProtosBundledToolModuleResolver(
                            "package", packageToolRoot, standardLibraryResolver);
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
                processObserver.accept(process);
                ProtosObjectValue rawFilesystem =
                        ProtosStandardFilesystemProtocol.createCapability(
                                toolPrelude.bytesPrototypeForRuntime(),
                                bootstrap.activation(),
                                backend);
                if (!(rawFilesystem instanceof ProtosFilesystemValue filesystem)) {
                    throw new IOException(
                            "Package Tool workspace Filesystem has the wrong value family");
                }
                if (bootstrap.activation().context().hasLocalSlot("projectTreeFilesystem")) {
                    throw new IOException(
                            "Package Tool workspace Filesystem bootstrap slot already exists");
                }
                bootstrap.activation().context().createLocalSlot(
                        "projectTreeFilesystem", filesystem);

                ProtosExecutionOutcome outcome =
                        ProtosRootTaskExecution.execute(
                                new ProtosSourceCompiler().compile(BUILD_PLAN_SOURCE),
                                bootstrap.activation());
                if (outcome.state() != ProtosExecutionOutcome.State.COMPLETED) {
                    throw new IOException(
                            "workspace Package Tool preflight did not complete normally");
                }
                return ProtosPackageExecutionPlanAdapter.detach(
                        outcome.value(), projectRoot);
            } catch (IOException failure) {
                throw failure;
            } catch (RuntimeException failure) {
                throw new IOException("workspace Package Tool preflight failed", failure);
            } finally {
                process.requestTerminationForRuntime();
            }
        }
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
