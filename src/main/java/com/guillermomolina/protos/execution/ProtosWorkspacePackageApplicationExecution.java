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

import com.guillermomolina.protos.runtime.ProtosEncodingValue;
import com.guillermomolina.protos.runtime.ProtosEnvironmentValue;
import com.guillermomolina.protos.runtime.ProtosModuleKey;
import com.guillermomolina.protos.runtime.ProtosNetworkCapabilityValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosProcessRuntime;
import com.guillermomolina.protos.runtime.ProtosProcessStandardStreamBinding;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Wires one detached workspace package plan into one fresh application Process.
 *
 * <p>This boundary owns application Process construction/lifecycle only. It receives no Package
 * Tool Process, activation, Filesystem or mutable Protos plan. The package resolver is reconstructed
 * from the detached DTO and the canonical initial module is executed through C2A.
 *
 * <p>The application Process receives no Network authority unless the owning host explicitly
 * selects {@link NetworkGrant#HOST_NETWORK} (D047/D173). The capability is then provisioned from
 * the exact application Prelude on the same live {@link ProtosPolyglotRuntimeHost} that hosts the
 * Process, so it can neither delegate to another Prelude's Network prototype nor outlive its host.
 */
public final class ProtosWorkspacePackageApplicationExecution {
    private ProtosWorkspacePackageApplicationExecution() {}

    /**
     * Hosting-level selection of whether the owning host grants the application Process its
     * initial {@code network} binding. This selection is policy-neutral: it carries no CLI,
     * manifest or environment spelling, and the resulting capability is never created by the
     * selecting caller but only here, for the exact application Prelude.
     */
    public enum NetworkGrant {
        /** No Network authority; the initial {@code network} slot is absent. */
        NONE,
        /** Provision one host Network capability on the RuntimeHost hosting the Process. */
        HOST_NETWORK
    }

    public record Request(
            Path coreRoot,
            Path projectRoot,
            ProtosPackageExecutionPlan plan,
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
            Objects.requireNonNull(projectRoot, "projectRoot");
            Objects.requireNonNull(plan, "plan");
            Objects.requireNonNull(standardLibraryResolver, "standardLibraryResolver");
            Objects.requireNonNull(entryLogicalModule, "entryLogicalModule");
            Objects.requireNonNull(applicationArguments, "applicationArguments");
            Objects.requireNonNull(environmentNameDomain, "environmentNameDomain");
            Objects.requireNonNull(environmentEntries, "environmentEntries");
            applicationArguments = List.copyOf(applicationArguments);
            environmentEntries = List.copyOf(environmentEntries);
            requireNonEmptyEncodingBinding(stdinEncodingBinding, "stdinEncodingBinding");
            requireNonEmptyEncodingBinding(stdoutEncodingBinding, "stdoutEncodingBinding");
            requireNonEmptyEncodingBinding(stderrEncodingBinding, "stderrEncodingBinding");
        }

        ApplicationAuthority authority() {
            return new ApplicationAuthority(
                    applicationArguments,
                    environmentNameDomain,
                    environmentEntries,
                    stdinBackend,
                    stdoutBackend,
                    stderrBackend,
                    stdinEncodingBinding,
                    stdoutEncodingBinding,
                    stderrEncodingBinding);
        }
    }

    public static ProtosExecutionOutcome execute(Request request) throws IOException {
        Objects.requireNonNull(request, "request");
        try (ProtosPolyglotRuntimeHost runtimeHost = ProtosPolyglotRuntimeHost.open()) {
            return execute(request, ignored -> {}, NetworkGrant.NONE, runtimeHost);
        }
    }

    static ProtosExecutionOutcome execute(
            Request request, NetworkGrant networkGrant, ProtosPolyglotRuntimeHost runtimeHost)
            throws IOException {
        return execute(request, ignored -> {}, networkGrant, runtimeHost);
    }

    static ProtosExecutionOutcome execute(
            Request request, Consumer<ProtosProcessRuntime> processObserver) throws IOException {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(processObserver, "processObserver");
        try (ProtosPolyglotRuntimeHost runtimeHost = ProtosPolyglotRuntimeHost.open()) {
            return execute(request, processObserver, NetworkGrant.NONE, runtimeHost);
        }
    }

    static ProtosExecutionOutcome execute(
            Request request,
            Consumer<ProtosProcessRuntime> processObserver,
            NetworkGrant networkGrant,
            ProtosPolyglotRuntimeHost runtimeHost) throws IOException {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(processObserver, "processObserver");
        Objects.requireNonNull(networkGrant, "networkGrant");
        Objects.requireNonNull(runtimeHost, "runtimeHost");

        ProtosWorkspacePackageModuleResolver resolver =
                new ProtosWorkspacePackageModuleResolver(
                        request.projectRoot(),
                        request.plan(),
                        request.standardLibraryResolver());
        ProtosModuleKey entryKey = resolver.entryModule(request.entryLogicalModule());
        return executeEntry(
                request.coreRoot(),
                resolver,
                entryKey,
                request.authority(),
                networkGrant,
                false,
                "workspace package application Process failed",
                processObserver,
                runtimeHost);
    }

    /**
     * Explicit application bootstrap authority shared by every package-backed application route:
     * arguments, environment, standard-stream backends and exact Encoding bindings.
     */
    record ApplicationAuthority(
            List<String> applicationArguments,
            ProtosEnvironmentValue.NativeNameDomain environmentNameDomain,
            List<ProtosEnvironmentValue.NativeEntry> environmentEntries,
            ProtosProcessStandardStreamBinding.ReadableBackend stdinBackend,
            ProtosProcessStandardStreamBinding.WritableBackend stdoutBackend,
            ProtosProcessStandardStreamBinding.WritableBackend stderrBackend,
            String stdinEncodingBinding,
            String stdoutEncodingBinding,
            String stderrEncodingBinding) {
        ApplicationAuthority {
            Objects.requireNonNull(applicationArguments, "applicationArguments");
            Objects.requireNonNull(environmentNameDomain, "environmentNameDomain");
            Objects.requireNonNull(environmentEntries, "environmentEntries");
            applicationArguments = List.copyOf(applicationArguments);
            environmentEntries = List.copyOf(environmentEntries);
            requireNonEmptyEncodingBinding(stdinEncodingBinding, "stdinEncodingBinding");
            requireNonEmptyEncodingBinding(stdoutEncodingBinding, "stdoutEncodingBinding");
            requireNonEmptyEncodingBinding(stderrEncodingBinding, "stderrEncodingBinding");
        }
    }

    /**
     * Bootstraps one fresh application Process over {@code resolver} and executes the canonical
     * initial module {@code entryKey}. Termination of the Process is always requested before
     * return; with {@code awaitTermination} this method also waits until the Process is
     * TERMINATED, so a caller may release resources the resolver borrows immediately afterwards.
     *
     * <p>With {@link NetworkGrant#HOST_NETWORK} the Network capability is provisioned from the
     * Prelude created here on {@code runtimeHost}, which also hosts the Process; the caller keeps
     * that host open until after this method returns.
     */
    static ProtosExecutionOutcome executeEntry(
            Path coreRoot,
            ProtosModuleResolver resolver,
            ProtosModuleKey entryKey,
            ApplicationAuthority authority,
            NetworkGrant networkGrant,
            boolean awaitTermination,
            String failureMessage,
            Consumer<ProtosProcessRuntime> processObserver,
            ProtosPolyglotRuntimeHost runtimeHost) throws IOException {
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(coreRoot, resolver);

        ProtosEncodingValue stdinEncoding =
                requireEncoding(prelude, authority.stdinEncodingBinding());
        ProtosEncodingValue stdoutEncoding =
                requireEncoding(prelude, authority.stdoutEncodingBinding());
        ProtosEncodingValue stderrEncoding =
                requireEncoding(prelude, authority.stderrEncodingBinding());
        ProtosNetworkCapabilityValue network =
                switch (networkGrant) {
                    case NONE -> null;
                    case HOST_NETWORK -> runtimeHost.provisionHostNetwork(prelude);
                };

        ProtosStandaloneProcessBootstrap.Result bootstrap =
                ProtosStandaloneProcessBootstrap.create(
                        prelude,
                        authority.applicationArguments(),
                        authority.environmentNameDomain(),
                        authority.environmentEntries(),
                        authority.stdinBackend(),
                        authority.stdoutBackend(),
                        authority.stderrBackend(),
                        stdinEncoding,
                        stdoutEncoding,
                        stderrEncoding,
                        null,
                        network);
        ProtosProcessRuntime process = bootstrap.process();
        ProtosPolyglotProcessContext processContext = null;

        try {
            processContext =
                    runtimeHost.hostProcess(
                            process,
                            InputStream.nullInputStream(),
                            OutputStream.nullOutputStream(),
                            OutputStream.nullOutputStream());
            processObserver.accept(process);
            return ProtosCanonicalInitialModuleExecution.execute(
                    prelude,
                    resolver,
                    entryKey,
                    bootstrap.activation());
        } catch (IOException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw new IOException(failureMessage, failure);
        } finally {
            process.requestTerminationForRuntime();
            if (awaitTermination && processContext != null) {
                process.awaitTerminationForRuntime();
                processContext.awaitTerminalDispositionForRuntime();
            }
        }
    }


    private static ProtosEncodingValue requireEncoding(
            ProtosPrelude prelude, String exactBinding) throws IOException {
        if (exactBinding == null) {
            return null;
        }
        Object value =
                prelude.encodingPrototype()
                        .readLocalSlot(exactBinding)
                        .orElseThrow(
                                () ->
                                        new IOException(
                                                "unknown application stream Encoding binding"));
        if (!(value instanceof ProtosEncodingValue encoding)) {
            throw new IOException("application stream Encoding binding has wrong value family");
        }
        return encoding;
    }

    private static void requireNonEmptyEncodingBinding(String binding, String field) {
        if (binding != null && binding.isEmpty()) {
            throw new IllegalArgumentException(field + " must be null or a non-empty exact binding");
        }
    }
}
