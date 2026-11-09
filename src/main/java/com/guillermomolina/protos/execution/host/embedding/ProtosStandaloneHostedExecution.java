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

import com.guillermomolina.protos.execution.ProtosWorkspacePackageApplicationExecution.NetworkGrant;
import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosActor;
import com.guillermomolina.protos.runtime.ProtosEncodingValue;
import com.guillermomolina.protos.runtime.ProtosEnvironmentValue;
import com.guillermomolina.protos.runtime.ProtosNetworkCapabilityValue;
import com.guillermomolina.protos.runtime.ProtosResolverCapabilityValue;
import com.guillermomolina.protos.runtime.ProtosEntropyCapabilityValue;
import com.guillermomolina.protos.runtime.ProtosClockCapabilityValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosProcessRuntime;
import com.guillermomolina.protos.runtime.ProtosProcessStandardStreamBinding;
import com.guillermomolina.protos.runtime.io.ProtosFilesystemValue;
import com.guillermomolina.protos.runtime.modules.ProtosModuleKey;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Shared standalone hosted-execution authority for the CLI and JVM embedders.
 *
 * <p>It owns the standalone sequence Core bootstrap, standalone Process bootstrap, Process-scoped
 * Polyglot Context binding and canonical initial-module execution, together with the host
 * Environment/stream/UTF-8 provisioning that sequence needs. The supported embedding contracts are
 * {@link #executeFile} (one-shot) and {@link ProtosStandaloneHostedSession} (reusable session,
 * which {@code executeFile} itself is built on), plus the explicitly opted-in D225-B1 {@link
 * ProtosRootActorServiceSession} built on {@link #bootstrapRootActorService}. The remaining public
 * members are the building
 * blocks the CLI composes around its own Session (it lives in another package); they are shared
 * support, not an embedding contract, and embedders should not need them.
 *
 * <p>Embedded execution provisions no CLI conveniences: there is no {@code print} binding.
 * A source communicates its result through its terminal expression, which is carried by the
 * returned {@link ProtosExecutionOutcome}.
 */
public final class ProtosStandaloneHostedExecution {
    /**
     * BUG008: guest recursion consumes substantial host stack per level. Since PERF025-C (PLAT042
     * B′) an ordinary source Closure call is one Bytecode CallTarget. Since PERF025-C2B (PLAT043)
     * standard Boolean control enters no structured-dispatch root, and since PERF026-B1 (PLAT044
     * B′) an eligible standard {@code ifTrue} immediate literal callback (as in each level of the
     * retained recursive benchmark drivers) runs inline in its source root with no callback
     * CallTarget; other structured invocations and non-eligible callbacks still add their roots.
     * With those artificial per-level root multipliers removed, PERF025-C2D adopts a conservative
     * fixed 16 MiB budget for the retained 10,000-deep drivers; it is not a claimed minimum. The
     * guest carriers that own this budget (the CLI and the Test Tool) never depend on the ambient
     * stack size of whichever thread happens to call in. Since PERF025-F1 (PLAT046 Candidate B) a
     * {@link ProtosStandaloneHostedSession}, and therefore {@link #executeFile}, instead runs guest
     * work on the calling thread. The budget remains a stable, recorded part of this reference
     * runtime's execution identity; see {@code protos/benchmarks/README.md}.
     */
    public static final long GUEST_CALL_STACK_SIZE_BYTES = 16L * 1024 * 1024;

    private ProtosStandaloneHostedExecution() {}

    /**
     * Executes one standalone Protos source file with normal standalone direct-file semantics and
     * returns its terminal outcome.
     *
     * <p>The file is the entry module: explicit {@code ./} and {@code ../} specifiers resolve
     * relative to it inside its directory tree, and Core and {@code std:} modules resolve from
     * {@code coreRoot}. The call runs the guest on a dedicated carrier thread and closes the
     * Process, its Polyglot Context and the runtime host before returning or throwing. Guest
     * failure is reported as a {@code FAILED} outcome; unreadable sources, unparsable sources and
     * host failures surface as exceptions.
     *
     * @param coreRoot the {@code protos/lib/core} directory of the Protos distribution
     * @param sourceFile the entry source file, read as UTF-8
     * @param applicationArguments the Process {@code args()} snapshot
     * @param in backing stream of the Process standard input
     * @param out backing stream of the Process standard output
     * @param err backing stream of the Process standard error
     */
    public static ProtosExecutionOutcome executeFile(
            Path coreRoot,
            Path sourceFile,
            List<String> applicationArguments,
            InputStream in,
            OutputStream out,
            OutputStream err)
            throws IOException {
        return executeFile(
                coreRoot, sourceFile, applicationArguments, in, out, err, NetworkGrant.NONE);
    }

    /**
     * As {@link #executeFile(Path, Path, List, InputStream, OutputStream, OutputStream)}, under
     * the embedder's explicit Network selection; the other overloads select {@link
     * NetworkGrant#NONE}. The selection is applied by {@link ProtosStandaloneHostedSession#open(
     * Path, Path, List, InputStream, OutputStream, OutputStream, NetworkGrant)}.
     */
    public static ProtosExecutionOutcome executeFile(
            Path coreRoot,
            Path sourceFile,
            List<String> applicationArguments,
            InputStream in,
            OutputStream out,
            OutputStream err,
            NetworkGrant networkGrant)
            throws IOException {
        try (ProtosStandaloneHostedSession session =
                ProtosStandaloneHostedSession.open(
                        coreRoot, sourceFile, applicationArguments, in, out, err, networkGrant)) {
            return session.initialOutcome();
        }
    }

    /** D243-C one-shot entry with an independent Entropy selection. */
    public static ProtosExecutionOutcome executeFile(
            Path coreRoot,
            Path sourceFile,
            List<String> applicationArguments,
            InputStream in,
            OutputStream out,
            OutputStream err,
            NetworkGrant networkGrant,
            EntropyGrant entropyGrant)
            throws IOException {
        return executeFile(coreRoot, sourceFile, applicationArguments, in, out, err, networkGrant, entropyGrant, ClockGrant.NONE);
    }

    public static ProtosExecutionOutcome executeFile(
            Path coreRoot,
            Path sourceFile,
            List<String> applicationArguments,
            InputStream in,
            OutputStream out,
            OutputStream err,
            NetworkGrant networkGrant,
            EntropyGrant entropyGrant, ClockGrant clockGrant)
            throws IOException {
        try (ProtosStandaloneHostedSession session =
                ProtosStandaloneHostedSession.open(
                        coreRoot, sourceFile, applicationArguments, in, out, err,
                        networkGrant, entropyGrant, clockGrant)) {
            return session.initialOutcome();
        }
    }

    /** Convenience form: no application arguments, empty stdin, discarded stdout and stderr. */
    public static ProtosExecutionOutcome executeFile(Path coreRoot, Path sourceFile)
            throws IOException {
        return executeFile(
                coreRoot,
                sourceFile,
                List.of(),
                InputStream.nullInputStream(),
                OutputStream.nullOutputStream(),
                OutputStream.nullOutputStream());
    }

    /**
     * Bootstraps Core and one standalone Process whose standard streams use UTF-8. The Process
     * receives no Network authority: its initial {@code network} slot is absent.
     */
    public static ProtosStandaloneProcessBootstrap.Result bootstrapProcess(
            Path coreRoot,
            ProtosModuleResolver moduleResolver,
            List<String> applicationArguments,
            ProtosProcessStandardStreamBinding.ReadableBackend stdinBackend,
            ProtosProcessStandardStreamBinding.WritableBackend stdoutBackend,
            ProtosProcessStandardStreamBinding.WritableBackend stderrBackend)
            throws IOException {
        return bootstrapProcessWithGrant(
                coreRoot,
                moduleResolver,
                applicationArguments,
                stdinBackend,
                stdoutBackend,
                stderrBackend,
                NetworkGrant.NONE,
                null);
    }

    /**
     * Bootstraps Core and one standalone Process, as {@link #bootstrapProcess(Path,
     * ProtosModuleResolver, List, ProtosProcessStandardStreamBinding.ReadableBackend,
     * ProtosProcessStandardStreamBinding.WritableBackend,
     * ProtosProcessStandardStreamBinding.WritableBackend)}, under the owning host's explicit
     * Network selection (D047/D173).
     *
     * <p>{@code runtimeHost} must be the live RuntimeHost that will afterwards host this Process
     * and that stays open for the Process's whole lifetime. With {@link NetworkGrant#HOST_NETWORK}
     * the capability is provisioned on it from the exact Prelude created here, so it delegates to
     * this application's Network prototype and cannot outlive its host. {@link NetworkGrant#NONE}
     * provisions nothing and leaves the host's Network plane uninitialized. This method neither
     * hosts the Process nor closes {@code runtimeHost}; on failure the caller still owns the host.
     */
    public static ProtosStandaloneProcessBootstrap.Result bootstrapProcess(
            Path coreRoot,
            ProtosModuleResolver moduleResolver,
            List<String> applicationArguments,
            ProtosProcessStandardStreamBinding.ReadableBackend stdinBackend,
            ProtosProcessStandardStreamBinding.WritableBackend stdoutBackend,
            ProtosProcessStandardStreamBinding.WritableBackend stderrBackend,
            NetworkGrant networkGrant,
            ProtosPolyglotRuntimeHost runtimeHost)
            throws IOException {
        Objects.requireNonNull(networkGrant, "networkGrant");
        Objects.requireNonNull(runtimeHost, "runtimeHost");
        return bootstrapProcessWithGrant(
                coreRoot,
                moduleResolver,
                applicationArguments,
                stdinBackend,
                stdoutBackend,
                stderrBackend,
                networkGrant,
                runtimeHost);
    }

    /**
     * Explicit host selection of the optional D223-A1 Resolver grant. It is independent of
     * {@link NetworkGrant}: {@link #NONE} leaves the initial {@code resolver} slot absent whatever
     * Network grant is selected, and no CLI or default policy selects {@link #HOST_RESOLVER}.
     */
    public enum ResolverGrant {
        NONE,
        HOST_RESOLVER
    }

    /** Independent, explicit wall-clock authorization; default denial. */
    public enum ClockGrant {
        NONE,
        HOST_CLOCK
    }

    /** D243-C independent host entropy selection; existing entry points default to NONE. */
    public enum EntropyGrant {
        NONE,
        HOST_ENTROPY
    }

    /**
     * As {@link #bootstrapProcess(Path, ProtosModuleResolver, List,
     * ProtosProcessStandardStreamBinding.ReadableBackend,
     * ProtosProcessStandardStreamBinding.WritableBackend,
     * ProtosProcessStandardStreamBinding.WritableBackend, NetworkGrant, ProtosPolyglotRuntimeHost)},
     * additionally applying the embedder's independent Resolver selection. With {@link
     * ResolverGrant#HOST_RESOLVER} the capability is provisioned on {@code runtimeHost} from the
     * exact Prelude created here.
     */
    public static ProtosStandaloneProcessBootstrap.Result bootstrapProcess(
            Path coreRoot,
            ProtosModuleResolver moduleResolver,
            List<String> applicationArguments,
            ProtosProcessStandardStreamBinding.ReadableBackend stdinBackend,
            ProtosProcessStandardStreamBinding.WritableBackend stdoutBackend,
            ProtosProcessStandardStreamBinding.WritableBackend stderrBackend,
            NetworkGrant networkGrant,
            ResolverGrant resolverGrant,
            ProtosPolyglotRuntimeHost runtimeHost)
            throws IOException {
        Objects.requireNonNull(networkGrant, "networkGrant");
        Objects.requireNonNull(resolverGrant, "resolverGrant");
        Objects.requireNonNull(runtimeHost, "runtimeHost");
        return bootstrapProcessWithGrant(
                coreRoot,
                moduleResolver,
                applicationArguments,
                stdinBackend,
                stdoutBackend,
                stderrBackend,
                networkGrant,
                resolverGrant,
                runtimeHost);
    }

    /**
     * Explicit host selection of the optional D248-B standalone Filesystem grant: one existing
     * host directory whose recursive namespace becomes the RootActor's bootstrap-local {@code
     * filesystem}. It is independent of {@link NetworkGrant} and {@link ResolverGrant}; {@link
     * #NONE} leaves the initial {@code filesystem} slot absent. The host root is never published
     * to guest code.
     *
     * @param authorizedRoot the absolute host directory, or {@code null} for {@link #NONE}
     */
    public record FilesystemGrant(Path authorizedRoot) {
        public static final FilesystemGrant NONE = new FilesystemGrant(null);

        public FilesystemGrant {
            if (authorizedRoot != null && !authorizedRoot.isAbsolute()) {
                throw new IllegalArgumentException("authorized Filesystem root must be absolute");
            }
        }

        public static FilesystemGrant directory(Path authorizedRoot) {
            return new FilesystemGrant(Objects.requireNonNull(authorizedRoot, "authorizedRoot"));
        }

        public boolean granted() {
            return authorizedRoot != null;
        }
    }

    /**
     * Expected host provisioning failure of a {@link FilesystemGrant}: the selected root is
     * absent, not a directory, a final symbolic link, or cannot be confined by the host provider.
     * It is raised before any guest source executes and is an application bootstrap failure, not
     * an internal runtime defect.
     */
    public static final class FilesystemGrantProvisioningException extends IOException {
        private static final long serialVersionUID = 1L;

        FilesystemGrantProvisioningException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Process-local provisioning of one optional Filesystem grant. {@link #marker()} is passed to
     * {@link ProtosStandaloneProcessBootstrap#create} as {@code defaultFilesystem}; {@link #bind}
     * then installs the operations on the new RootActor activation and transfers backend custody
     * to {@code runtimeHost} for exactly that Process. Until that transfer {@link #close()}
     * releases the backend; afterwards it does nothing.
     */
    static final class FilesystemProvisioning implements AutoCloseable {
        private static final FilesystemProvisioning NONE = new FilesystemProvisioning(null, null);

        private final ProtosLinuxConfinedReadWriteTreeFilesystemBackend backend;
        private final ProtosFilesystemValue marker;
        private boolean transferred;

        private FilesystemProvisioning(
                ProtosLinuxConfinedReadWriteTreeFilesystemBackend backend,
                ProtosFilesystemValue marker) {
            this.backend = backend;
            this.marker = marker;
        }

        static FilesystemProvisioning provision(FilesystemGrant grant)
                throws FilesystemGrantProvisioningException {
            Objects.requireNonNull(grant, "filesystemGrant");
            if (!grant.granted()) {
                return NONE;
            }
            try {
                return new FilesystemProvisioning(
                        ProtosLinuxConfinedReadWriteTreeFilesystemBackend.provision(
                                grant.authorizedRoot()),
                        new ProtosFilesystemValue());
            } catch (IOException | RuntimeException failure) {
                throw new FilesystemGrantProvisioningException(
                        "cannot authorize Filesystem root "
                                + grant.authorizedRoot()
                                + ": "
                                + failure.getMessage(),
                        failure);
            }
        }

        /** The uninitialized bootstrap marker, or {@code null} when nothing was granted. */
        ProtosFilesystemValue marker() {
            return marker;
        }

        /**
         * Binds the marker to the exact RootActor bootstrap activation, before any guest source
         * runs, and transfers backend custody to {@code runtimeHost} for this Process only.
         */
        void bind(
                ProtosStandaloneProcessBootstrap.Result bootstrap,
                ProtosPrelude prelude,
                ProtosPolyglotRuntimeHost runtimeHost) {
            if (backend == null) {
                return;
            }
            Objects.requireNonNull(runtimeHost, "runtimeHost");
            ProtosStandardFilesystemProtocol.installOperations(
                    marker, prelude.bytesPrototypeForRuntime(), bootstrap.activation(), backend);
            runtimeHost.attachProcessFilesystemForRuntime(bootstrap.process(), backend);
            transferred = true;
        }

        @Override
        public void close() {
            if (backend == null || transferred) {
                return;
            }
            // Revocation of an untransferred backend publishes no Protos-visible result.
            backend.close();
        }
    }

    /**
     * As {@link #bootstrapProcess(Path, ProtosModuleResolver, List,
     * ProtosProcessStandardStreamBinding.ReadableBackend,
     * ProtosProcessStandardStreamBinding.WritableBackend,
     * ProtosProcessStandardStreamBinding.WritableBackend, NetworkGrant, ResolverGrant,
     * ProtosPolyglotRuntimeHost)}, additionally applying the host's independent D248-B Filesystem
     * selection. A granted root is securely provisioned after Core bootstrap and before the
     * Process exists; its backend is then owned by {@code runtimeHost} for this Process and is
     * revoked at Process termination or when {@code runtimeHost} closes.
     *
     * @throws FilesystemGrantProvisioningException when the granted root cannot be provisioned;
     *     no Process was created and nothing remains open
     */
    public static ProtosStandaloneProcessBootstrap.Result bootstrapProcess(
            Path coreRoot,
            ProtosModuleResolver moduleResolver,
            List<String> applicationArguments,
            ProtosProcessStandardStreamBinding.ReadableBackend stdinBackend,
            ProtosProcessStandardStreamBinding.WritableBackend stdoutBackend,
            ProtosProcessStandardStreamBinding.WritableBackend stderrBackend,
            NetworkGrant networkGrant,
            ResolverGrant resolverGrant,
            EntropyGrant entropyGrant,
            FilesystemGrant filesystemGrant,
            ProtosPolyglotRuntimeHost runtimeHost)
            throws IOException {
        return bootstrapProcess(
                coreRoot,
                moduleResolver,
                applicationArguments,
                stdinBackend,
                stdoutBackend,
                stderrBackend,
                networkGrant,
                resolverGrant,
                entropyGrant,
                ClockGrant.NONE,
                filesystemGrant,
                runtimeHost);
    }

    public static ProtosStandaloneProcessBootstrap.Result bootstrapProcess(
            Path coreRoot,
            ProtosModuleResolver moduleResolver,
            List<String> applicationArguments,
            ProtosProcessStandardStreamBinding.ReadableBackend stdinBackend,
            ProtosProcessStandardStreamBinding.WritableBackend stdoutBackend,
            ProtosProcessStandardStreamBinding.WritableBackend stderrBackend,
            NetworkGrant networkGrant,
            ResolverGrant resolverGrant,
            EntropyGrant entropyGrant,
            ClockGrant clockGrant,
            FilesystemGrant filesystemGrant,
            ProtosPolyglotRuntimeHost runtimeHost)
            throws IOException {
        Objects.requireNonNull(networkGrant, "networkGrant");
        Objects.requireNonNull(resolverGrant, "resolverGrant");
        Objects.requireNonNull(entropyGrant, "entropyGrant");
        Objects.requireNonNull(clockGrant, "clockGrant");
        Objects.requireNonNull(filesystemGrant, "filesystemGrant");
        Objects.requireNonNull(runtimeHost, "runtimeHost");
        return bootstrapProcessWithGrant(
                coreRoot,
                moduleResolver,
                applicationArguments,
                stdinBackend,
                stdoutBackend,
                stderrBackend,
                networkGrant,
                resolverGrant,
                entropyGrant, clockGrant,
                filesystemGrant,
                runtimeHost);
    }

    /** D243-C source compatibility: no Filesystem grant. */
    public static ProtosStandaloneProcessBootstrap.Result bootstrapProcess(
            Path coreRoot, ProtosModuleResolver moduleResolver, List<String> applicationArguments,
            ProtosProcessStandardStreamBinding.ReadableBackend stdinBackend,
            ProtosProcessStandardStreamBinding.WritableBackend stdoutBackend,
            ProtosProcessStandardStreamBinding.WritableBackend stderrBackend,
            NetworkGrant networkGrant, ResolverGrant resolverGrant, EntropyGrant entropyGrant,
            ProtosPolyglotRuntimeHost runtimeHost) throws IOException {
        return bootstrapProcess(
                coreRoot,
                moduleResolver,
                applicationArguments,
                stdinBackend,
                stdoutBackend,
                stderrBackend,
                networkGrant,
                resolverGrant,
                entropyGrant,
                ClockGrant.NONE,
                runtimeHost);
    }

    public static ProtosStandaloneProcessBootstrap.Result bootstrapProcess(
            Path coreRoot, ProtosModuleResolver moduleResolver, List<String> applicationArguments,
            ProtosProcessStandardStreamBinding.ReadableBackend stdinBackend,
            ProtosProcessStandardStreamBinding.WritableBackend stdoutBackend,
            ProtosProcessStandardStreamBinding.WritableBackend stderrBackend,
            NetworkGrant networkGrant, ResolverGrant resolverGrant, EntropyGrant entropyGrant, ClockGrant clockGrant,
            ProtosPolyglotRuntimeHost runtimeHost) throws IOException {
        return bootstrapProcess(coreRoot, moduleResolver, applicationArguments,
                stdinBackend, stdoutBackend, stderrBackend, networkGrant, resolverGrant,
                entropyGrant, clockGrant, FilesystemGrant.NONE, runtimeHost);
    }

    /** D248-B source compatibility: no Entropy grant. */
    public static ProtosStandaloneProcessBootstrap.Result bootstrapProcess(
            Path coreRoot, ProtosModuleResolver moduleResolver, List<String> applicationArguments,
            ProtosProcessStandardStreamBinding.ReadableBackend stdinBackend,
            ProtosProcessStandardStreamBinding.WritableBackend stdoutBackend,
            ProtosProcessStandardStreamBinding.WritableBackend stderrBackend,
            NetworkGrant networkGrant, ResolverGrant resolverGrant, FilesystemGrant filesystemGrant,
            ProtosPolyglotRuntimeHost runtimeHost) throws IOException {
        return bootstrapProcess(coreRoot, moduleResolver, applicationArguments,
                stdinBackend, stdoutBackend, stderrBackend, networkGrant, resolverGrant,
                EntropyGrant.NONE, filesystemGrant, runtimeHost);
    }

    /** {@code runtimeHost} is consulted only for a HOST grant. */
    private static ProtosStandaloneProcessBootstrap.Result bootstrapProcessWithGrant(
            Path coreRoot,
            ProtosModuleResolver moduleResolver,
            List<String> applicationArguments,
            ProtosProcessStandardStreamBinding.ReadableBackend stdinBackend,
            ProtosProcessStandardStreamBinding.WritableBackend stdoutBackend,
            ProtosProcessStandardStreamBinding.WritableBackend stderrBackend,
            NetworkGrant networkGrant,
            ProtosPolyglotRuntimeHost runtimeHost)
            throws IOException {
        return bootstrapProcessWithGrant(
                coreRoot,
                moduleResolver,
                applicationArguments,
                stdinBackend,
                stdoutBackend,
                stderrBackend,
                networkGrant,
                ResolverGrant.NONE,
                runtimeHost);
    }

    private static ProtosStandaloneProcessBootstrap.Result bootstrapProcessWithGrant(
            Path coreRoot,
            ProtosModuleResolver moduleResolver,
            List<String> applicationArguments,
            ProtosProcessStandardStreamBinding.ReadableBackend stdinBackend,
            ProtosProcessStandardStreamBinding.WritableBackend stdoutBackend,
            ProtosProcessStandardStreamBinding.WritableBackend stderrBackend,
            NetworkGrant networkGrant,
            ResolverGrant resolverGrant,
            ProtosPolyglotRuntimeHost runtimeHost)
            throws IOException {
        return bootstrapProcessWithGrant(
                coreRoot,
                moduleResolver,
                applicationArguments,
                stdinBackend,
                stdoutBackend,
                stderrBackend,
                networkGrant,
                resolverGrant,
                EntropyGrant.NONE,
                FilesystemGrant.NONE,
                runtimeHost);
    }

    private static ProtosStandaloneProcessBootstrap.Result bootstrapProcessWithGrant(
            Path coreRoot,
            ProtosModuleResolver moduleResolver,
            List<String> applicationArguments,
            ProtosProcessStandardStreamBinding.ReadableBackend stdinBackend,
            ProtosProcessStandardStreamBinding.WritableBackend stdoutBackend,
            ProtosProcessStandardStreamBinding.WritableBackend stderrBackend,
            NetworkGrant networkGrant,
            ResolverGrant resolverGrant,
            EntropyGrant entropyGrant,
            FilesystemGrant filesystemGrant,
            ProtosPolyglotRuntimeHost runtimeHost)
            throws IOException {
        return bootstrapProcessWithGrant(
                coreRoot,
                moduleResolver,
                applicationArguments,
                stdinBackend,
                stdoutBackend,
                stderrBackend,
                networkGrant,
                resolverGrant,
                entropyGrant,
                ClockGrant.NONE,
                filesystemGrant,
                runtimeHost);
    }

    private static ProtosStandaloneProcessBootstrap.Result bootstrapProcessWithGrant(
            Path coreRoot,
            ProtosModuleResolver moduleResolver,
            List<String> applicationArguments,
            ProtosProcessStandardStreamBinding.ReadableBackend stdinBackend,
            ProtosProcessStandardStreamBinding.WritableBackend stdoutBackend,
            ProtosProcessStandardStreamBinding.WritableBackend stderrBackend,
            NetworkGrant networkGrant,
            ResolverGrant resolverGrant,
            EntropyGrant entropyGrant,
            ClockGrant clockGrant,
            FilesystemGrant filesystemGrant,
            ProtosPolyglotRuntimeHost runtimeHost)
            throws IOException {
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(coreRoot, moduleResolver);
        ProtosEncodingValue utf8 = utf8(prelude);
        ProtosNetworkCapabilityValue network =
                switch (networkGrant) {
                    case NONE -> null;
                    case HOST_NETWORK -> runtimeHost.provisionHostNetwork(prelude);
                };
        ProtosResolverCapabilityValue resolver =
                switch (resolverGrant) {
                    case NONE -> null;
                    case HOST_RESOLVER -> runtimeHost.provisionHostResolver(prelude);
                };
        try (FilesystemProvisioning filesystem = FilesystemProvisioning.provision(filesystemGrant)) {
            ProtosEntropyCapabilityValue entropy =
                    switch (entropyGrant) {
                        case NONE -> null;
                        case HOST_ENTROPY -> runtimeHost.provisionHostEntropy(prelude);
                    };
            ProtosClockCapabilityValue clock = null;
            ProtosStandaloneProcessBootstrap.Result bootstrap;
            try {
                clock = clockGrant == ClockGrant.HOST_CLOCK
                        ? runtimeHost.provisionHostClock(prelude) : null;
                bootstrap = ProtosStandaloneProcessBootstrap.create(
                        prelude,
                        applicationArguments,
                        HOST_ENVIRONMENT_NAME_DOMAIN,
                        hostEnvironmentEntries(),
                        stdinBackend,
                        stdoutBackend,
                        stderrBackend,
                        utf8,
                        utf8,
                        utf8,
                        filesystem.marker(),
                        network,
                        resolver,
                        entropy, clock);
            } catch (RuntimeException | Error failure) {
                if (clock != null) clock.retireForRuntime();
                if (entropy != null) {
                    entropy.retireForRuntime();
                }
                throw failure;
            }
            boolean bound = false;
            try {
                // Bind the confined Filesystem before the initial source can run.
                filesystem.bind(bootstrap, prelude, runtimeHost);
                bound = true;
            } finally {
                if (!bound) {
                    bootstrap.process().requestTerminationForRuntime();
                }
            }
            return bootstrap;
        }
    }

    /**
     * Binds the bootstrapped Process to a Process-scoped Polyglot Context of {@code runtimeHost}.
     * On failure the Process is terminated and the runtime host closed, so the caller owns neither.
     */
    public static ProtosPolyglotProcessContext bindProcess(
            ProtosStandaloneProcessBootstrap.Result bootstrap,
            ProtosPolyglotRuntimeHost runtimeHost,
            InputStream in,
            OutputStream out,
            OutputStream err) {
        boolean bound = false;
        try {
            ProtosPolyglotProcessContext processContext =
                    runtimeHost.hostProcess(bootstrap.process(), in, out, err);
            bound = true;
            return processContext;
        } finally {
            if (!bound) {
                bootstrap.process().requestTerminationForRuntime();
                runtimeHost.close();
            }
        }
    }

    /**
     * Executes the direct-file entry module in the bootstrapped, already hosted Process and
     * returns its terminal outcome.
     */
    public static ProtosExecutionOutcome executeDirectFile(
            ProtosDirectFileModuleResolver resolver, ProtosActivation activation)
            throws IOException {
        ProtosPrelude prelude =
                activation
                        .prelude()
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "direct-file entry requires an owning Core prelude"));
        return ProtosCanonicalInitialModuleExecution.execute(
                prelude, resolver, resolver.entryModule(), activation);
    }

    /**
     * D225-B1 explicit host opt-in: runs the RootActor message-service bootstrap of the
     * bootstrapped, already hosted Process instead of a direct-file entry, and waits for its
     * outcome.
     *
     * <p>The existing RootActor loads {@code entryModule} once as its canonical initial module,
     * with exactly the bootstrap-local slots the Process was provisioned with, invokes the local
     * slot {@code bootstrapBinding} and installs its result as its behavior at the single READY
     * cutover; the RootActor then serves ordinary Actor messages. Nothing is parsed per message and
     * no behavior is created here. This method neither closes nor terminates a Process that
     * reached READY.
     *
     * @return true when the RootActor reached READY with its initial behavior; false when
     *     initialization failed with a Protos Error, which terminated the Process
     * @throws IOException when a host failure prevented initialization; the Process is terminated
     */
    public static boolean bootstrapRootActorService(
            ProtosStandaloneProcessBootstrap.Result bootstrap,
            ProtosModuleKey entryModule,
            String bootstrapBinding)
            throws IOException {
        Objects.requireNonNull(bootstrap, "bootstrap");
        Objects.requireNonNull(entryModule, "entryModule");
        Objects.requireNonNull(bootstrapBinding, "bootstrapBinding");
        ProtosProcessRuntime process = bootstrap.process();
        ProtosPrelude prelude =
                bootstrap.activation()
                        .prelude()
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "RootActor service bootstrap requires an owning Core prelude"));
        CompletableFuture<Boolean> outcome =
                ProtosActorBootstrap.startRootService(
                        process, prelude, entryModule, bootstrapBinding);
        /*
         * I158 / D251-A1: synchronous for the host only. The guest initialization may suspend in
         * pending Future.value() calls; it then holds no scheduler carrier, and this host thread
         * (never a carrier) waits for the real terminal outcome: READY, initialization failure or
         * termination. A suspension never completes this Future provisionally.
         */
        boolean ready;
        try {
            ready = outcome.join();
        } catch (CompletionException hostFailure) {
            throw new IOException("RootActor service bootstrap failed", hostFailure.getCause());
        }
        ProtosActor root = process.rootActorForRuntime();
        return ready
                && root.lifecycleState() == ProtosActor.LifecycleState.READY
                && root.currentBehavior().isPresent();
    }

    static ProtosEncodingValue utf8(ProtosPrelude prelude) {
        Object value =
                prelude.encodingPrototype()
                        .readLocalSlot("UTF8")
                        .orElseThrow(
                                () -> new IllegalStateException("Core Encoding.UTF8 is missing"));
        if (!(value instanceof ProtosEncodingValue encoding)) {
            throw new IllegalStateException("Core Encoding.UTF8 is not an Encoding descriptor");
        }
        return encoding;
    }

    public static List<ProtosEnvironmentValue.NativeEntry> hostEnvironmentEntries() {
        ArrayList<ProtosEnvironmentValue.NativeEntry> entries = new ArrayList<>();
        for (Map.Entry<String, String> entry : System.getenv().entrySet()) {
            entries.add(
                    new ProtosEnvironmentValue.NativeEntry(entry.getKey(), entry.getValue()));
        }
        return List.copyOf(entries);
    }

    /**
     * Use the JDK's native ProcessBuilder environment map itself as the probe for native
     * environment-name representability and name identity. This avoids inventing a POSIX/Windows
     * Unicode case-folding policy in Core and does not mutate this JVM's real environment.
     */
    public static final ProtosEnvironmentValue.NativeNameDomain HOST_ENVIRONMENT_NAME_DOMAIN =
            new ProtosEnvironmentValue.NativeNameDomain() {
                @Override
                public boolean sameCapturedName(String left, String right) {
                    return nativeEnvironmentNameMatches(left, right);
                }

                @Override
                public boolean isQueryRepresentable(String name) {
                    Map<String, String> probe = new ProcessBuilder().environment();
                    probe.clear();
                    try {
                        probe.put(name, "");
                        return probe.size() == 1 && probe.containsKey(name);
                    } catch (IllegalArgumentException | NullPointerException invalid) {
                        return false;
                    }
                }

                @Override
                public boolean matchesQuery(String captured, String query) {
                    return nativeEnvironmentNameMatches(captured, query);
                }
            };

    private static boolean nativeEnvironmentNameMatches(String captured, String query) {
        Map<String, String> probe = new ProcessBuilder().environment();
        probe.clear();
        try {
            probe.put(captured, "");
            return probe.containsKey(query);
        } catch (IllegalArgumentException | NullPointerException invalid) {
            return false;
        }
    }

    public static ProtosProcessStandardStreamBinding.ReadableBackend readableBackend(
            InputStream in) {
        return (maxBytes, completion) -> {
            Thread worker =
                    Thread.ofVirtual()
                            .name("protos-stdin-read")
                            .start(
                                    () -> {
                                        ByteArrayOutputStream captured =
                                                new ByteArrayOutputStream(
                                                        Math.min(maxBytes, 8192));
                                        try {
                                            int first = in.read();
                                            if (first < 0) {
                                                completion.eof();
                                                return;
                                            }
                                            captured.write(first);

                                            while (captured.size() < maxBytes) {
                                                int available = in.available();
                                                if (available <= 0) break;
                                                int wanted =
                                                        Math.min(
                                                                maxBytes - captured.size(),
                                                                available);
                                                byte[] more = in.readNBytes(wanted);
                                                if (more.length == 0) break;
                                                captured.write(more, 0, more.length);
                                            }
                                            completion.data(captured.toByteArray());
                                        } catch (IOException failure) {
                                            /*
                                             * Preserve any already consumed prefix as progress.
                                             * If cancellation has already won, the Process-stream
                                             * binding will put that prefix back in its semantic
                                             * unread buffer instead of committing it.
                                             */
                                            if (captured.size() > 0) {
                                                completion.data(captured.toByteArray());
                                            } else {
                                                completion.failed();
                                            }
                                        }
                                    });
            return worker::interrupt;
        };
    }

    public static ProtosProcessStandardStreamBinding.WritableBackend writableBackend(
            OutputStream stream) {
        return (bytes, completion) -> {
            try {
                /*
                 * Keep the portable write commitment synchronous with this call. Writing to the
                 * stream does not imply an explicit Protos flush operation.
                 */
                stream.write(bytes, 0, bytes.length);
                completion.succeeded();
            } catch (IOException | RuntimeException failure) {
                completion.failed(0);
            }
            return () -> {};
        };
    }
}
