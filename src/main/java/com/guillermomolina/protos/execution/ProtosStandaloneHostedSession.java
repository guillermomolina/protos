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

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosActorModuleState;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosProcessRuntime;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Supported reusable JVM embedding session: one live standalone Protos Process whose entry source
 * has been executed once and whose top-level Closures can then be invoked repeatedly.
 *
 * <p>Opening performs the same sequence as {@link ProtosStandaloneHostedExecution#executeFile}
 * (Core bootstrap, standalone Process bootstrap, Process-scoped Polyglot Context binding,
 * canonical direct-file execution) but keeps the Process, its Polyglot Context (and therefore
 * Engine and JIT/profiling state), and the runtime host alive until {@link #close()}. The
 * terminal outcome of that first execution is {@link #initialOutcome()}.
 *
 * <p>{@link #invokeTopLevel} reads the named slot of the entry module instance and invokes the
 * Closure it holds as a RootActor-local task in that same Process; no source text is parsed per
 * call. Guest operations, including close, run on one dedicated carrier with the explicit {@link
 * ProtosStandaloneHostedExecution#GUEST_CALL_STACK_SIZE_BYTES} stack budget; the calling thread
 * never executes guest code. A session is safe to share between threads only in the sense that
 * calls are serialized on that carrier.
 */
public final class ProtosStandaloneHostedSession implements AutoCloseable {
    private final ProtosGuestCarrier carrier;
    private final ProtosDirectFileModuleResolver resolver;
    private final ProtosProcessRuntime process;
    private final ProtosPolyglotRuntimeHost runtimeHost;
    private final ProtosPolyglotProcessContext processContext;
    private final ProtosExecutionOutcome initialOutcome;
    private final ProtosObjectValue entryModule;
    private final ProtosActivation entryActivation;
    private final AtomicBoolean closeStarted = new AtomicBoolean();

    private ProtosStandaloneHostedSession(
            ProtosGuestCarrier carrier,
            ProtosDirectFileModuleResolver resolver,
            ProtosProcessRuntime process,
            ProtosPolyglotRuntimeHost runtimeHost,
            ProtosPolyglotProcessContext processContext,
            ProtosExecutionOutcome initialOutcome,
            ProtosObjectValue entryModule,
            ProtosActivation entryActivation) {
        this.carrier = carrier;
        this.resolver = resolver;
        this.process = process;
        this.runtimeHost = runtimeHost;
        this.processContext = processContext;
        this.initialOutcome = initialOutcome;
        this.entryModule = entryModule;
        this.entryActivation = entryActivation;
    }

    /**
     * Opens a session over one standalone source file and executes it with normal direct-file
     * semantics. Unreadable or unparsable sources and host failures are thrown, and nothing is left
     * open; guest failure is reported by {@link #initialOutcome()}.
     *
     * @param coreRoot the {@code protos/lib/core} directory of the Protos distribution
     * @param sourceFile the entry source file, read as UTF-8
     * @param applicationArguments the Process {@code args()} snapshot
     * @param in backing stream of the Process standard input
     * @param out backing stream of the Process standard output
     * @param err backing stream of the Process standard error
     */
    public static ProtosStandaloneHostedSession open(
            Path coreRoot,
            Path sourceFile,
            List<String> applicationArguments,
            InputStream in,
            OutputStream out,
            OutputStream err)
            throws IOException {
        Objects.requireNonNull(coreRoot, "coreRoot");
        Objects.requireNonNull(sourceFile, "sourceFile");
        Objects.requireNonNull(applicationArguments, "applicationArguments");
        Objects.requireNonNull(in, "in");
        Objects.requireNonNull(out, "out");
        Objects.requireNonNull(err, "err");

        List<String> arguments = List.copyOf(applicationArguments);
        ProtosGuestCarrier carrier = new ProtosGuestCarrier("protos-embedded-guest");
        try {
            return carrier.call(
                    () -> openOnCarrier(carrier, coreRoot, sourceFile, arguments, in, out, err));
        } catch (IOException | RuntimeException | Error failure) {
            carrier.close();
            throw failure;
        }
    }

    /** Convenience form: no application arguments, empty stdin, discarded stdout and stderr. */
    public static ProtosStandaloneHostedSession open(Path coreRoot, Path sourceFile)
            throws IOException {
        return open(
                coreRoot,
                sourceFile,
                List.of(),
                InputStream.nullInputStream(),
                OutputStream.nullOutputStream(),
                OutputStream.nullOutputStream());
    }

    private static ProtosStandaloneHostedSession openOnCarrier(
            ProtosGuestCarrier carrier,
            Path coreRoot,
            Path sourceFile,
            List<String> arguments,
            InputStream in,
            OutputStream out,
            OutputStream err)
            throws IOException {
        Path sourcePath = sourceFile.toAbsolutePath().normalize();
        String characters = Files.readString(sourcePath, StandardCharsets.UTF_8);
        ProtosDirectFileModuleResolver resolver =
                new ProtosDirectFileModuleResolver(
                        sourcePath,
                        characters,
                        new ProtosStandardLibraryModuleResolver(coreRoot.getParent()));
        ProtosProcessRuntime process = null;
        ProtosPolyglotRuntimeHost runtimeHost = null;
        try {
            ProtosStandaloneProcessBootstrap.Result bootstrap =
                    ProtosStandaloneHostedExecution.bootstrapProcess(
                            coreRoot,
                            resolver,
                            arguments,
                            ProtosStandaloneHostedExecution.readableBackend(in),
                            ProtosStandaloneHostedExecution.writableBackend(out),
                            ProtosStandaloneHostedExecution.writableBackend(err));
            process = bootstrap.process();
            runtimeHost = ProtosPolyglotRuntimeHost.open();
            // bindProcess terminates the Process and closes the host itself when binding fails.
            ProtosPolyglotProcessContext processContext =
                    ProtosStandaloneHostedExecution.bindProcess(
                            bootstrap, runtimeHost, in, out, err);
            ProtosExecutionOutcome initial =
                    ProtosStandaloneHostedExecution.executeDirectFile(
                            resolver, bootstrap.activation());

            ProtosObjectValue entryModule = null;
            ProtosActivation entryActivation = null;
            if (initial.state() == ProtosExecutionOutcome.State.COMPLETED) {
                ProtosActivation initialActivation = bootstrap.activation();
                ProtosActorModuleState state = initialActivation.actorModuleState();
                entryModule =
                        state.lookup(resolver.entryModule())
                                .orElseThrow(
                                        () ->
                                                new IllegalStateException(
                                                        "completed entry module is not cached"))
                                .instance();
                ProtosPrelude prelude = initialActivation.prelude().orElseThrow();
                entryActivation =
                        prelude.newModuleActivation(
                                state,
                                resolver.entryModule(),
                                entryModule,
                                initialActivation.executionDomain());
            }
            return new ProtosStandaloneHostedSession(
                    carrier,
                    resolver,
                    process,
                    runtimeHost,
                    processContext,
                    initial,
                    entryModule,
                    entryActivation);
        } catch (IOException | RuntimeException | Error failure) {
            Throwable cleanup = shutdown(process, runtimeHost, resolver);
            if (cleanup != null) failure.addSuppressed(cleanup);
            throw failure;
        }
    }

    /** The terminal outcome of executing the entry source when the session was opened. */
    public ProtosExecutionOutcome initialOutcome() {
        return initialOutcome;
    }

    /**
     * Invokes the Closure currently held by the entry module's top-level slot {@code name} with no
     * arguments, inside this session's live Process, and returns its terminal outcome.
     *
     * <p>Guest failure is reported as a {@code FAILED} outcome. The slot is read on each call, so
     * reassignment by the guest is observed exactly as for an in-language call, but nothing is
     * parsed. Throws {@link IllegalStateException} when the entry source did not complete or the
     * session is closed, and {@link IllegalArgumentException} when the slot is absent or does not
     * hold a source-backed Closure.
     */
    public ProtosExecutionOutcome invokeTopLevel(String name) throws IOException {
        Objects.requireNonNull(name, "name");
        return carrier.call(
                () -> {
                    if (closeStarted.get()) {
                        throw new IllegalStateException("standalone hosted session is closed");
                    }
                    if (entryModule == null) {
                        throw new IllegalStateException(
                                "entry source did not complete; no top-level entries exist");
                    }
                    Object entry =
                            entryModule
                                    .readLocalSlot(name)
                                    .orElseThrow(
                                            () ->
                                                    new IllegalArgumentException(
                                                            "entry module has no top-level slot "
                                                                    + name));
                    if (!(entry instanceof ProtosClosureValue closure)
                            || closure.nativeBody().isPresent()) {
                        throw new IllegalArgumentException(
                                "top-level slot " + name + " is not a source-backed Closure");
                    }
                    return process.callInExecutionHostForRuntime(
                            () ->
                                    ProtosRootTaskExecution.executeClosure(
                                            closure, List.of(), entryActivation));
                });
    }

    /**
     * Requests Process termination, waits for terminal Process completion and Context disposition,
     * then closes the runtime host and module resolver. Idempotent; a failure is thrown after every
     * step has been attempted.
     */
    @Override
    public void close() throws IOException {
        if (!closeStarted.compareAndSet(false, true)) return;
        try {
            carrier.call(
                    () -> {
                        Throwable failure = shutdown(process, runtimeHost, resolver);
                        if (failure instanceof IOException io) throw io;
                        if (failure instanceof RuntimeException runtime) throw runtime;
                        if (failure instanceof Error error) throw error;
                        return null;
                    });
        } finally {
            carrier.close();
        }
    }

    /**
     * Attempts every cleanup step in the established order and returns the first failure with the
     * later ones suppressed into it, or null. Null arguments are skipped.
     */
    private static Throwable shutdown(
            ProtosProcessRuntime process,
            ProtosPolyglotRuntimeHost runtimeHost,
            ProtosDirectFileModuleResolver resolver) {
        Throwable[] first = new Throwable[1];
        java.util.function.Consumer<Throwable> record =
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

    boolean isCarrierThreadForTesting() {
        return carrier.isCarrierThread();
    }
}
