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
import com.guillermomolina.protos.runtime.ProtosEncodingValue;
import com.guillermomolina.protos.runtime.ProtosEnvironmentValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosProcessStandardStreamBinding;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Shared standalone hosted-execution authority for the CLI and JVM embedders.
 *
 * <p>It owns the standalone sequence Core bootstrap, standalone Process bootstrap, Process-scoped
 * Polyglot Context binding and canonical initial-module execution, together with the host
 * Environment/stream/UTF-8 provisioning that sequence needs. The supported embedding contract is
 * {@link #executeFile}. The remaining public members are the building blocks the CLI composes
 * around its own Session (it lives in another package); they are shared support, not an embedding
 * contract, and embedders should not need them.
 *
 * <p>Embedded execution provisions no CLI conveniences: there is no {@code print} binding.
 * A source communicates its result through its terminal expression, which is carried by the
 * returned {@link ProtosExecutionOutcome}.
 */
public final class ProtosStandaloneHostedExecution {
    /**
     * BUG008: a Protos-level Closure call composes through two nested Bytecode CallTargets, so host
     * stack consumption per guest recursion level is higher than a single-CallTarget interpreter
     * would need. Guest execution therefore never depends on the ambient stack size of whichever
     * thread happens to call in (a JVM main thread, a test worker, an embedder's thread) and runs
     * on a dedicated carrier thread with this explicit, fixed budget, which remains a stable,
     * recorded part of this reference runtime's execution identity; see
     * {@code protos/benchmarks/README.md}.
     */
    public static final long GUEST_CALL_STACK_SIZE_BYTES = 64L * 1024 * 1024;

    private ProtosStandaloneHostedExecution() {}

    /**
     * Executes one standalone Protos source file with normal standalone direct-file semantics and
     * returns its terminal outcome.
     *
     * <p>The file is the entry module: explicit {@code ./} and {@code ../} specifiers resolve
     * relative to it inside its directory tree, and Core and {@code std:} modules resolve from
     * {@code coreRoot}. The call runs the guest on a dedicated carrier thread, blocks until the
     * Process terminates, and closes the Process, its Polyglot Context and the runtime host before
     * returning or throwing. Guest failure is reported as a {@code FAILED} outcome; unreadable
     * sources, unparsable sources and host failures surface as exceptions.
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
        Objects.requireNonNull(coreRoot, "coreRoot");
        Objects.requireNonNull(sourceFile, "sourceFile");
        Objects.requireNonNull(applicationArguments, "applicationArguments");
        Objects.requireNonNull(in, "in");
        Objects.requireNonNull(out, "out");
        Objects.requireNonNull(err, "err");

        List<String> arguments = List.copyOf(applicationArguments);
        ProtosExecutionOutcome[] outcome = new ProtosExecutionOutcome[1];
        Throwable[] failure = new Throwable[1];
        Thread carrier =
                new Thread(
                        null,
                        () -> {
                            try {
                                outcome[0] =
                                        executeFileOnCurrentThread(
                                                coreRoot, sourceFile, arguments, in, out, err);
                            } catch (Throwable thrown) {
                                failure[0] = thrown;
                            }
                        },
                        "protos-embedded-guest",
                        GUEST_CALL_STACK_SIZE_BYTES);
        carrier.start();
        try {
            carrier.join();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "embedded Protos execution was interrupted", interrupted);
        }
        if (failure[0] instanceof IOException io) throw io;
        if (failure[0] instanceof RuntimeException runtime) throw runtime;
        if (failure[0] instanceof Error error) throw error;
        return outcome[0];
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

    private static ProtosExecutionOutcome executeFileOnCurrentThread(
            Path coreRoot,
            Path sourceFile,
            List<String> applicationArguments,
            InputStream in,
            OutputStream out,
            OutputStream err)
            throws IOException {
        Path sourcePath = sourceFile.toAbsolutePath().normalize();
        String characters = Files.readString(sourcePath, StandardCharsets.UTF_8);
        try (ProtosDirectFileModuleResolver resolver =
                new ProtosDirectFileModuleResolver(
                        sourcePath,
                        characters,
                        new ProtosStandardLibraryModuleResolver(coreRoot.getParent()))) {
            ProtosStandaloneProcessBootstrap.Result bootstrap =
                    bootstrapProcess(
                            coreRoot,
                            resolver,
                            applicationArguments,
                            readableBackend(in),
                            writableBackend(out),
                            writableBackend(err));
            ProtosPolyglotRuntimeHost runtimeHost = ProtosPolyglotRuntimeHost.open();
            try {
                bindProcess(bootstrap, runtimeHost, in, out, err);
                return executeDirectFile(resolver, bootstrap.activation());
            } finally {
                bootstrap.process().requestTerminationForRuntime();
                runtimeHost.close();
            }
        }
    }

    /** Bootstraps Core and one standalone Process whose standard streams use UTF-8. */
    public static ProtosStandaloneProcessBootstrap.Result bootstrapProcess(
            Path coreRoot,
            ProtosModuleResolver moduleResolver,
            List<String> applicationArguments,
            ProtosProcessStandardStreamBinding.ReadableBackend stdinBackend,
            ProtosProcessStandardStreamBinding.WritableBackend stdoutBackend,
            ProtosProcessStandardStreamBinding.WritableBackend stderrBackend)
            throws IOException {
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(coreRoot, moduleResolver);
        ProtosEncodingValue utf8 = utf8(prelude);
        return ProtosStandaloneProcessBootstrap.create(
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
                null);
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

    private static ProtosEncodingValue utf8(ProtosPrelude prelude) {
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
