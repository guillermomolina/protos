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
import com.guillermomolina.protos.runtime.ProtosProcessStandardStreamBinding;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * CLI-neutral host driver for one workspace-only package-backed application run.
 *
 * <p>The request makes toolchain roots, selected project root, logical entry and application
 * bootstrap authority explicit. The driver performs only the already-closed C1 read-only Package
 * Tool preflight followed by the already-closed C2B separately-authorized application execution.
 * It owns no CLI spelling, current-directory policy, default entry convention or diagnostic text.
 */
public final class ProtosWorkspaceRunDriver {
    private ProtosWorkspaceRunDriver() {}

    public record Request(
            Path coreRoot,
            Path packageToolRoot,
            Path projectRoot,
            ProtosModuleResolver standardLibraryResolver,
            String entryLogicalModule,
            List<String> applicationArguments,
            ProtosEnvironmentValue.NativeNameDomain environmentNameDomain,
            List<ProtosEnvironmentValue.NativeEntry> environmentEntries,
            ProtosProcessStandardStreamBinding.ReadableBackend stdinBackend,
            ProtosProcessStandardStreamBinding.WritableBackend stdoutBackend,
            ProtosProcessStandardStreamBinding.WritableBackend stderrBackend,
            String stdinEncodingBinding,
            String stdoutEncodingBinding,
            String stderrEncodingBinding) {
        public Request {
            Objects.requireNonNull(coreRoot, "coreRoot");
            Objects.requireNonNull(packageToolRoot, "packageToolRoot");
            Objects.requireNonNull(projectRoot, "projectRoot");
            Objects.requireNonNull(standardLibraryResolver, "standardLibraryResolver");
            Objects.requireNonNull(entryLogicalModule, "entryLogicalModule");
            Objects.requireNonNull(applicationArguments, "applicationArguments");
            Objects.requireNonNull(environmentNameDomain, "environmentNameDomain");
            Objects.requireNonNull(environmentEntries, "environmentEntries");
            applicationArguments = List.copyOf(applicationArguments);
            environmentEntries = List.copyOf(environmentEntries);
        }
    }

    public static ProtosExecutionOutcome execute(Request request) throws IOException {
        Objects.requireNonNull(request, "request");

        try (ProtosPolyglotRuntimeHost runtimeHost = ProtosPolyglotRuntimeHost.open()) {
            ProtosPackageExecutionPlan plan =
                    ProtosWorkspacePackagePreflight.build(
                            request.coreRoot(),
                            request.packageToolRoot(),
                            request.projectRoot(),
                            request.standardLibraryResolver(),
                            runtimeHost);

            return ProtosWorkspacePackageApplicationExecution.execute(
                    new ProtosWorkspacePackageApplicationExecution.Request(
                            request.coreRoot(),
                            request.projectRoot(),
                            plan,
                            request.standardLibraryResolver(),
                            request.entryLogicalModule(),
                            request.applicationArguments(),
                            request.environmentNameDomain(),
                            request.environmentEntries(),
                            request.stdinBackend(),
                            request.stdoutBackend(),
                            request.stderrBackend(),
                            request.stdinEncodingBinding(),
                            request.stdoutEncodingBinding(),
                            request.stderrEncodingBinding()),
                    runtimeHost);
        }
    }
}
