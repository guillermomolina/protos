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

import com.guillermomolina.protos.execution.ProtosStandaloneHostedExecution.FilesystemGrant;
import com.guillermomolina.protos.execution.ProtosStandaloneHostedExecution.ResolverGrant;
import com.guillermomolina.protos.execution.ProtosStandaloneHostedExecution.EntropyGrant;
import com.guillermomolina.protos.execution.ProtosStandaloneHostedExecution.ClockGrant;
import com.guillermomolina.protos.execution.ProtosWorkspacePackageApplicationExecution.NetworkGrant;
import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosProcessRuntime;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * D225-B1 explicitly opted-in hosted RootActor message service: one live standalone Protos Process
 * whose existing RootActor was initialized through the service-bootstrap entry
 * ({@code ACTORS.md} §32) instead of direct top-level evaluation.
 *
 * <p>{@link #open} resolves the entry source as the direct-file entry module, opens one
 * RuntimeHost, bootstraps one Process with exactly the requested, independent Network and Resolver
 * grants, hosts it in its Process-scoped Polyglot Context, and runs {@link
 * ProtosStandaloneHostedExecution#bootstrapRootActorService}: the RootActor executes its canonical
 * initial module once, invokes the module's own local {@code bootstrapBinding} and installs the
 * returned ordinary Protos object as its behavior at the READY cutover. {@code open} returns only
 * once the RootActor is READY.
 *
 * <p>From then on the application runs as Protos code: Actors created by the service reach it
 * through ordinary ActorRef or {@code Actor.group(Actor.current())} GroupRef messages dispatched
 * by the ordinary Actor scheduler. This session exposes no message API, starts no receive loop and
 * dispatches nothing itself; it only owns the Process, its Polyglot Context, the RuntimeHost (and
 * with it any granted Network or Resolver) and the module resolver until {@link #close()} or until
 * the Process terminates by itself, for example after a fatal RootActor failure.
 *
 * <p>This mode is distinct from {@link ProtosStandaloneHostedSession}, from {@link
 * ProtosStandaloneHostedExecution#executeFile} and from standard Polyglot embedding, which all
 * keep normal direct-entry evaluation.
 */
public final class ProtosRootActorServiceSession implements AutoCloseable {
    private final ProtosDirectFileModuleResolver resolver;
    private final ProtosProcessRuntime process;
    private final ProtosPolyglotRuntimeHost runtimeHost;
    private final ProtosPolyglotProcessContext processContext;
    private final AtomicBoolean closeStarted = new AtomicBoolean();

    private ProtosRootActorServiceSession(
            ProtosDirectFileModuleResolver resolver,
            ProtosProcessRuntime process,
            ProtosPolyglotRuntimeHost runtimeHost,
            ProtosPolyglotProcessContext processContext) {
        this.resolver = resolver;
        this.process = process;
        this.runtimeHost = runtimeHost;
        this.processContext = processContext;
    }

    /**
     * Opens a RootActor message-service session over one standalone source file.
     *
     * <p>{@code bootstrapBinding} names a top-level binding of that file itself: a local slot of
     * its module instance, never an inherited or imported one. The grants are independent: neither
     * implies the other, and selecting this mode grants nothing. Unreadable or unparsable sources
     * and host failures are thrown as {@link IOException}; a bootstrap that fails with a Protos
     * Error (a missing, inherited or non-callable binding, an invalid behavior or an escaping
     * Error) terminates the Process and throws {@link InitializationFailedException}. In every
     * failure case nothing is left open.
     *
     * @param coreRoot the {@code protos/lib/core} directory of the Protos distribution
     * @param sourceFile the entry source file, read as UTF-8
     * @param bootstrapBinding the entry module's local bootstrap binding
     * @param applicationArguments the Process {@code args()} snapshot
     * @param in backing stream of the Process standard input
     * @param out backing stream of the Process standard output
     * @param err backing stream of the Process standard error
     * @param networkGrant whether the RootActor's initial module receives {@code network}
     * @param resolverGrant whether the RootActor's initial module receives {@code resolver}
     */
    public static ProtosRootActorServiceSession open(
            Path coreRoot,
            Path sourceFile,
            String bootstrapBinding,
            List<String> applicationArguments,
            InputStream in,
            OutputStream out,
            OutputStream err,
            NetworkGrant networkGrant,
            ResolverGrant resolverGrant)
            throws IOException {
        return open(
                coreRoot,
                sourceFile,
                bootstrapBinding,
                applicationArguments,
                in,
                out,
                err,
                networkGrant,
                resolverGrant,
                Map.of());
    }

    /** D243-C service entry with an explicit independent Entropy grant. */
    public static ProtosRootActorServiceSession open(
            Path coreRoot,
            Path sourceFile,
            String bootstrapBinding,
            List<String> applicationArguments,
            InputStream in,
            OutputStream out,
            OutputStream err,
            NetworkGrant networkGrant,
            ResolverGrant resolverGrant,
            EntropyGrant entropyGrant)
            throws IOException {
        return open(
                coreRoot,
                sourceFile,
                bootstrapBinding,
                applicationArguments,
                in,
                out,
                err,
                networkGrant,
                resolverGrant,
                entropyGrant,
                ClockGrant.NONE);
    }

    public static ProtosRootActorServiceSession open(
            Path coreRoot,
            Path sourceFile,
            String bootstrapBinding,
            List<String> applicationArguments,
            InputStream in,
            OutputStream out,
            OutputStream err,
            NetworkGrant networkGrant,
            ResolverGrant resolverGrant,
            EntropyGrant entropyGrant, ClockGrant clockGrant)
            throws IOException {
        return open(
                coreRoot, sourceFile, bootstrapBinding,
                applicationArguments, in, out, err,
                networkGrant, resolverGrant, entropyGrant, clockGrant, Map.of());
    }

    /**
     * As {@link #open(Path, Path, String, List, InputStream, OutputStream, OutputStream,
     * NetworkGrant, ResolverGrant)}, additionally making the host-supplied PLAT055 application
     * module catalog importable for the whole Process lifetime.
     *
     * <p>Each key is an exact {@code app:} specifier and each value the Protos source of that
     * module. The catalog is validated and snapshotted by {@link
     * ProtosApplicationModuleResolver#authorizedCatalog} before anything is opened; an invalid
     * entry throws {@link IllegalArgumentException} or {@link NullPointerException} and creates no
     * RuntimeHost or Process. Resolution is composed as direct-file entry and its {@code ./}
     * modules, then the catalog, then the standard library, so a catalog entry can neither shadow
     * a {@code std:} module nor a direct-file identity. Every Actor of the Process, including the
     * RootActor and HTTP Workers, imports its own instance of a catalog module under the ordinary
     * Actor-local cache rules. The catalog is source, not authority: its modules receive no
     * bootstrap-local capability.
     *
     * @param applicationModules exact {@code app:} specifiers mapped to Protos source text
     */
    public static ProtosRootActorServiceSession open(
            Path coreRoot,
            Path sourceFile,
            String bootstrapBinding,
            List<String> applicationArguments,
            InputStream in,
            OutputStream out,
            OutputStream err,
            NetworkGrant networkGrant,
            ResolverGrant resolverGrant,
            Map<String, String> applicationModules)
            throws IOException {
        return open(
                coreRoot, sourceFile, bootstrapBinding,
                applicationArguments, in, out, err,
                networkGrant, resolverGrant,
                EntropyGrant.NONE, applicationModules);
    }

    /** D243-C service entry with Entropy and an application module catalog. */
    public static ProtosRootActorServiceSession open(
            Path coreRoot,
            Path sourceFile,
            String bootstrapBinding,
            List<String> applicationArguments,
            InputStream in,
            OutputStream out,
            OutputStream err,
            NetworkGrant networkGrant,
            ResolverGrant resolverGrant,
            EntropyGrant entropyGrant,
            Map<String, String> applicationModules)
            throws IOException {
        return open(
                coreRoot,
                sourceFile,
                bootstrapBinding,
                applicationArguments,
                in,
                out,
                err,
                networkGrant,
                resolverGrant,
                entropyGrant,
                ClockGrant.NONE,
                applicationModules);
    }

    public static ProtosRootActorServiceSession open(
            Path coreRoot,
            Path sourceFile,
            String bootstrapBinding,
            List<String> applicationArguments,
            InputStream in,
            OutputStream out,
            OutputStream err,
            NetworkGrant networkGrant,
            ResolverGrant resolverGrant,
            EntropyGrant entropyGrant,
            ClockGrant clockGrant,
            Map<String, String> applicationModules)
            throws IOException {
        return openWithHost(
                coreRoot,
                sourceFile,
                bootstrapBinding,
                applicationArguments,
                in,
                out,
                err,
                networkGrant,
                resolverGrant,
                entropyGrant, clockGrant,
                FilesystemGrant.NONE,
                applicationModules,
                ProtosPolyglotRuntimeHost::open,
                (activation, process) -> {});
    }

    /**
     * As {@link #open(Path, Path, String, List, InputStream, OutputStream, OutputStream,
     * NetworkGrant, ResolverGrant, Map)}, additionally applying the independent D248-B Filesystem
     * selection; see {@link #openWithHost(Path, Path, String, List, InputStream, OutputStream,
     * OutputStream, NetworkGrant, ResolverGrant, FilesystemGrant, Map, Supplier, BiConsumer)}.
     */
    public static ProtosRootActorServiceSession open(
            Path coreRoot,
            Path sourceFile,
            String bootstrapBinding,
            List<String> applicationArguments,
            InputStream in,
            OutputStream out,
            OutputStream err,
            NetworkGrant networkGrant,
            ResolverGrant resolverGrant,
            FilesystemGrant filesystemGrant,
            Map<String, String> applicationModules)
            throws IOException {
        return openWithHost(
                coreRoot,
                sourceFile,
                bootstrapBinding,
                applicationArguments,
                in,
                out,
                err,
                networkGrant,
                resolverGrant,
                EntropyGrant.NONE,
                filesystemGrant,
                applicationModules,
                ProtosPolyglotRuntimeHost::open,
                (activation, process) -> {});
    }

    /** Explicit independent Entropy and Filesystem selection for service embedding. */
    public static ProtosRootActorServiceSession open(
            Path coreRoot, Path sourceFile, String bootstrapBinding,
            List<String> applicationArguments, InputStream in, OutputStream out, OutputStream err,
            NetworkGrant networkGrant, ResolverGrant resolverGrant,
            EntropyGrant entropyGrant, FilesystemGrant filesystemGrant,
            Map<String, String> applicationModules) throws IOException {
        return open(
                coreRoot,
                sourceFile,
                bootstrapBinding,
                applicationArguments,
                in,
                out,
                err,
                networkGrant,
                resolverGrant,
                entropyGrant,
                ClockGrant.NONE,
                filesystemGrant,
                applicationModules);
    }

    public static ProtosRootActorServiceSession open(
            Path coreRoot, Path sourceFile, String bootstrapBinding,
            List<String> applicationArguments, InputStream in, OutputStream out, OutputStream err,
            NetworkGrant networkGrant, ResolverGrant resolverGrant,
            EntropyGrant entropyGrant,
            ClockGrant clockGrant,
            FilesystemGrant filesystemGrant,
            Map<String, String> applicationModules) throws IOException {
        return openWithHost(coreRoot, sourceFile, bootstrapBinding, applicationArguments,
                in, out, err, networkGrant, resolverGrant, entropyGrant, clockGrant, filesystemGrant,
                applicationModules, ProtosPolyglotRuntimeHost::open,
                (activation, process) -> {});
    }

    /**
     * Host-configurable D225-B1 entry. The factory creates the owning RuntimeHost;
     * the hook installs host-selected CLI facilities after Process binding and
     * before any RootActor service source executes. Neither operation grants
     * guest authority beyond the explicitly supplied bootstrap grants.
     */
    public static ProtosRootActorServiceSession openWithHost(
            Path coreRoot,
            Path sourceFile,
            String bootstrapBinding,
            List<String> applicationArguments,
            InputStream in,
            OutputStream out,
            OutputStream err,
            NetworkGrant networkGrant,
            ResolverGrant resolverGrant,
            Map<String, String> applicationModules,
            Supplier<ProtosPolyglotRuntimeHost> runtimeHostFactory,
            BiConsumer<ProtosActivation, ProtosProcessRuntime> bootstrapHook)
            throws IOException {
        return openWithHost(
                coreRoot,
                sourceFile,
                bootstrapBinding,
                applicationArguments,
                in,
                out,
                err,
                networkGrant,
                resolverGrant,
                EntropyGrant.NONE,
                FilesystemGrant.NONE,
                applicationModules,
                runtimeHostFactory,
                bootstrapHook);
    }

    /** D243-C compatibility: Entropy grant without a Filesystem grant. */
    public static ProtosRootActorServiceSession openWithHost(
            Path coreRoot, Path sourceFile, String bootstrapBinding,
            List<String> applicationArguments, InputStream in, OutputStream out, OutputStream err,
            NetworkGrant networkGrant, ResolverGrant resolverGrant, EntropyGrant entropyGrant,
            Map<String, String> applicationModules,
            Supplier<ProtosPolyglotRuntimeHost> runtimeHostFactory,
            BiConsumer<ProtosActivation, ProtosProcessRuntime> bootstrapHook) throws IOException {
        return openWithHost(
                coreRoot,
                sourceFile,
                bootstrapBinding,
                applicationArguments,
                in,
                out,
                err,
                networkGrant,
                resolverGrant,
                entropyGrant,
                ClockGrant.NONE,
                applicationModules,
                runtimeHostFactory,
                bootstrapHook);
    }

    public static ProtosRootActorServiceSession openWithHost(
            Path coreRoot, Path sourceFile, String bootstrapBinding,
            List<String> applicationArguments, InputStream in, OutputStream out, OutputStream err,
            NetworkGrant networkGrant, ResolverGrant resolverGrant, EntropyGrant entropyGrant, ClockGrant clockGrant,
            Map<String, String> applicationModules,
            Supplier<ProtosPolyglotRuntimeHost> runtimeHostFactory,
            BiConsumer<ProtosActivation, ProtosProcessRuntime> bootstrapHook) throws IOException {
        return openWithHost(
                coreRoot, sourceFile, bootstrapBinding, applicationArguments, in, out, err,
                networkGrant, resolverGrant, entropyGrant, clockGrant, FilesystemGrant.NONE,
                applicationModules, runtimeHostFactory, bootstrapHook);
    }

    /** D248-B compatibility: Filesystem grant without an Entropy grant. */
    public static ProtosRootActorServiceSession openWithHost(
            Path coreRoot, Path sourceFile, String bootstrapBinding,
            List<String> applicationArguments, InputStream in, OutputStream out, OutputStream err,
            NetworkGrant networkGrant, ResolverGrant resolverGrant, FilesystemGrant filesystemGrant,
            Map<String, String> applicationModules,
            Supplier<ProtosPolyglotRuntimeHost> runtimeHostFactory,
            BiConsumer<ProtosActivation, ProtosProcessRuntime> bootstrapHook) throws IOException {
        return openWithHost(
                coreRoot, sourceFile, bootstrapBinding, applicationArguments, in, out, err,
                networkGrant, resolverGrant, EntropyGrant.NONE, filesystemGrant,
                applicationModules, runtimeHostFactory, bootstrapHook);
    }

    /**
     * RootActor service authority is selected independently: Entropy and confined
     * Filesystem grants are never inferred from Network or Resolver. Filesystem
     * custody survives READY but is revoked at Process termination.
     */
    public static ProtosRootActorServiceSession openWithHost(
            Path coreRoot,
            Path sourceFile,
            String bootstrapBinding,
            List<String> applicationArguments,
            InputStream in,
            OutputStream out,
            OutputStream err,
            NetworkGrant networkGrant,
            ResolverGrant resolverGrant,
            EntropyGrant entropyGrant,
            FilesystemGrant filesystemGrant,
            Map<String, String> applicationModules,
            Supplier<ProtosPolyglotRuntimeHost> runtimeHostFactory,
            BiConsumer<ProtosActivation, ProtosProcessRuntime> bootstrapHook)
            throws IOException {
        return openWithHost(
                coreRoot,
                sourceFile,
                bootstrapBinding,
                applicationArguments,
                in,
                out,
                err,
                networkGrant,
                resolverGrant,
                entropyGrant,
                ClockGrant.NONE,
                filesystemGrant,
                applicationModules,
                runtimeHostFactory,
                bootstrapHook);
    }

    public static ProtosRootActorServiceSession openWithHost(
            Path coreRoot,
            Path sourceFile,
            String bootstrapBinding,
            List<String> applicationArguments,
            InputStream in,
            OutputStream out,
            OutputStream err,
            NetworkGrant networkGrant,
            ResolverGrant resolverGrant,
            EntropyGrant entropyGrant,
            ClockGrant clockGrant,
            FilesystemGrant filesystemGrant,
            Map<String, String> applicationModules,
            Supplier<ProtosPolyglotRuntimeHost> runtimeHostFactory,
            BiConsumer<ProtosActivation, ProtosProcessRuntime> bootstrapHook)
            throws IOException {
        Objects.requireNonNull(filesystemGrant, "filesystemGrant");
        Objects.requireNonNull(runtimeHostFactory, "runtimeHostFactory");
        Objects.requireNonNull(bootstrapHook, "bootstrapHook");
        Map<String, String> catalog =
                ProtosApplicationModuleResolver.authorizedCatalog(applicationModules);
        Objects.requireNonNull(coreRoot, "coreRoot");
        Objects.requireNonNull(sourceFile, "sourceFile");
        Objects.requireNonNull(bootstrapBinding, "bootstrapBinding");
        Objects.requireNonNull(applicationArguments, "applicationArguments");
        Objects.requireNonNull(in, "in");
        Objects.requireNonNull(out, "out");
        Objects.requireNonNull(err, "err");
        Objects.requireNonNull(networkGrant, "networkGrant");
        Objects.requireNonNull(resolverGrant, "resolverGrant");
        Objects.requireNonNull(entropyGrant, "entropyGrant");
        Objects.requireNonNull(clockGrant, "clockGrant");

        Path sourcePath = sourceFile.toAbsolutePath().normalize();
        String characters = Files.readString(sourcePath, StandardCharsets.UTF_8);
        ProtosDirectFileModuleResolver resolver =
                new ProtosDirectFileModuleResolver(
                        sourcePath,
                        characters,
                        ProtosApplicationModuleResolver.over(
                                catalog,
                                new ProtosStandardLibraryModuleResolver(coreRoot.getParent())));
        ProtosProcessRuntime process = null;
        ProtosPolyglotRuntimeHost runtimeHost = null;
        try {
            // Grants are provisioned on the host that will host this Process, from its Prelude.
            runtimeHost =
                    Objects.requireNonNull(
                            runtimeHostFactory.get(), "runtimeHostFactory returned null");
            ProtosStandaloneProcessBootstrap.Result bootstrap =
                    ProtosStandaloneHostedExecution.bootstrapProcess(
                            coreRoot,
                            resolver,
                            List.copyOf(applicationArguments),
                            ProtosStandaloneHostedExecution.readableBackend(in),
                            ProtosStandaloneHostedExecution.writableBackend(out),
                            ProtosStandaloneHostedExecution.writableBackend(err),
                            networkGrant,
                            resolverGrant,
                            entropyGrant, clockGrant,
                            filesystemGrant,
                            runtimeHost);
            process = bootstrap.process();
            // bindProcess terminates the Process and closes the host itself when binding fails.
            ProtosPolyglotProcessContext processContext =
                    ProtosStandaloneHostedExecution.bindProcess(
                            bootstrap, runtimeHost, in, out, err);
            bootstrapHook.accept(bootstrap.activation(), process);
            if (!ProtosStandaloneHostedExecution.bootstrapRootActorService(
                    bootstrap, resolver.entryModule(), bootstrapBinding)) {
                throw new InitializationFailedException(bootstrapBinding);
            }
            return new ProtosRootActorServiceSession(
                    resolver, process, runtimeHost, processContext);
        } catch (IOException | RuntimeException | Error failure) {
            Throwable cleanup = shutdown(process, runtimeHost, resolver);
            if (cleanup != null) failure.addSuppressed(cleanup);
            throw failure;
        }
    }

    /**
     * The RootActor service bootstrap did not reach READY: it failed with a Protos Error, and the
     * Process terminated as for any fatal RootActor initialization failure.
     */
    public static final class InitializationFailedException extends IOException {
        private static final long serialVersionUID = 1L;

        InitializationFailedException(String bootstrapBinding) {
            super("RootActor service bootstrap '" + bootstrapBinding + "' did not reach READY");
        }
    }

    /** Whether an unhandled fatal RootActor failure ended this Process. */
    public boolean failedFromRoot() {
        return process.failedFromRootForRuntime();
    }

    /** True once the Process has terminated, whether by {@link #close()} or by itself. */
    public boolean isTerminated() {
        return process.lifecycleState() == ProtosProcessRuntime.LifecycleState.TERMINATED;
    }

    /**
     * Waits until the Process has terminated by itself or through {@link #close()}. It does not
     * request termination and does not release host resources; {@link #close()} still must be
     * called. Interruption is preserved and does not end the wait early.
     */
    public void awaitTermination() {
        process.awaitTerminationForRuntime();
    }

    /**
     * Wait until the service RootActor has terminated, including a graceful
     * Actor.current().stop(). Does not by itself stop the Process or close
     * host resources; the session owner still calls close().
     */
    public void awaitRootActorTermination() {
        process.awaitRootActorTerminationForRuntime();
    }

    /**
     * Requests Process termination (which terminates the RootActor service and every Actor and
     * Group of the Process), waits for terminal Process completion and Context disposition, and
     * closes the runtime host, releasing any granted Network or Resolver, and the module resolver.
     * Idempotent; a failure is thrown after every step has been attempted.
     */
    @Override
    public void close() throws IOException {
        if (!closeStarted.compareAndSet(false, true)) return;
        Throwable failure = shutdown(process, runtimeHost, resolver);
        if (failure instanceof RuntimeException runtime) throw runtime;
        if (failure instanceof Error error) throw error;
    }

    /**
     * Attempts every cleanup step in the established hosted-session order and returns the first
     * failure with the later ones suppressed into it, or null. Null arguments are skipped.
     */
    private static Throwable shutdown(
            ProtosProcessRuntime process,
            ProtosPolyglotRuntimeHost runtimeHost,
            ProtosDirectFileModuleResolver resolver) {
        Throwable[] first = new Throwable[1];
        Consumer<Throwable> record =
                failure -> {
                    if (first[0] == null) first[0] = failure;
                    else first[0].addSuppressed(failure);
                };
        if (process != null) {
            try {
                process.requestTerminationForRuntime();
                process.awaitTerminationForRuntime();
                process.executionHostForRuntime()
                        .ifPresent(host -> host.awaitTerminalDispositionForRuntime());
            } catch (RuntimeException | Error failure) {
                record.accept(failure);
            }
        }
        if (runtimeHost != null) {
            try {
                runtimeHost.close();
            } catch (RuntimeException | Error failure) {
                record.accept(failure);
            }
        }
        try {
            resolver.close();
        } catch (RuntimeException | Error failure) {
            record.accept(failure);
        }
        return first[0];
    }

    ProtosProcessRuntime processForTesting() {
        return process;
    }

    ProtosPolyglotRuntimeHost runtimeHostForTesting() {
        return runtimeHost;
    }

    ProtosPolyglotProcessContext processContextForTesting() {
        return processContext;
    }
}
