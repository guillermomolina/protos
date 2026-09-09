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
 */
public final class ProtosWorkspacePackageApplicationExecution {
    private ProtosWorkspacePackageApplicationExecution() {}

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
    }

    public static ProtosExecutionOutcome execute(Request request) throws IOException {
        Objects.requireNonNull(request, "request");
        try (ProtosPolyglotRuntimeHost runtimeHost = ProtosPolyglotRuntimeHost.open()) {
            return execute(request, ignored -> {}, runtimeHost);
        }
    }

    static ProtosExecutionOutcome execute(
            Request request, ProtosPolyglotRuntimeHost runtimeHost) throws IOException {
        return execute(request, ignored -> {}, runtimeHost);
    }

    static ProtosExecutionOutcome execute(
            Request request, Consumer<ProtosProcessRuntime> processObserver) throws IOException {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(processObserver, "processObserver");
        try (ProtosPolyglotRuntimeHost runtimeHost = ProtosPolyglotRuntimeHost.open()) {
            return execute(request, processObserver, runtimeHost);
        }
    }

    static ProtosExecutionOutcome execute(
            Request request,
            Consumer<ProtosProcessRuntime> processObserver,
            ProtosPolyglotRuntimeHost runtimeHost) throws IOException {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(processObserver, "processObserver");
        Objects.requireNonNull(runtimeHost, "runtimeHost");

        ProtosWorkspacePackageModuleResolver resolver =
                new ProtosWorkspacePackageModuleResolver(
                        request.projectRoot(),
                        request.plan(),
                        request.standardLibraryResolver());
        ProtosModuleKey entryKey = resolver.entryModule(request.entryLogicalModule());
        ProtosPrelude prelude =
                new ProtosCoreBootstrap().bootstrap(request.coreRoot(), resolver);

        ProtosEncodingValue stdinEncoding =
                requireEncoding(prelude, request.stdinEncodingBinding());
        ProtosEncodingValue stdoutEncoding =
                requireEncoding(prelude, request.stdoutEncodingBinding());
        ProtosEncodingValue stderrEncoding =
                requireEncoding(prelude, request.stderrEncodingBinding());

        ProtosStandaloneProcessBootstrap.Result bootstrap =
                ProtosStandaloneProcessBootstrap.create(
                        prelude,
                        request.applicationArguments(),
                        request.environmentNameDomain(),
                        request.environmentEntries(),
                        request.stdinBackend(),
                        request.stdoutBackend(),
                        request.stderrBackend(),
                        stdinEncoding,
                        stdoutEncoding,
                        stderrEncoding,
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
            try {
                return processContext.callForRuntime(
                        () -> {
                            try {
                                return ProtosCanonicalInitialModuleExecution.execute(
                                        prelude,
                                        resolver,
                                        entryKey,
                                        bootstrap.activation());
                            } catch (IOException failure) {
                                throw new CanonicalModuleIOException(failure);
                            }
                        });
            } catch (CanonicalModuleIOException failure) {
                throw failure.ioFailure();
            }
        } catch (IOException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw new IOException("workspace package application Process failed", failure);
        } finally {
            process.requestTerminationForRuntime();
        }
    }

    private static final class CanonicalModuleIOException extends RuntimeException {
        private final IOException ioFailure;

        CanonicalModuleIOException(IOException ioFailure) {
            super(null, null, false, false);
            this.ioFailure = Objects.requireNonNull(ioFailure, "ioFailure");
        }

        IOException ioFailure() {
            return ioFailure;
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
