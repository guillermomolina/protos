/*
 * THE LICENSED WORK IS PROVIDED UNDER THE TERMS OF THE ADAPTIVE PUBLIC LICENSE
 * ("LICENSE") AS FIRST COMPLETED BY: Guillermo Adrián Molina. ANY USE, PUBLIC
 * DISPLAY, PUBLIC PERFORMANCE, REPRODUCTION OR DISTRIBUTION OF, OR PREPARATION OF
 * DERIVATIVE WORKS BASED ON, THE LICENSED WORK CONSTITUTES RECIPIENT'S ACCEPTANCE
 * OF THIS LICENSE AND ITS TERMS, WHETHER OR NOT SUCH RECIPIENT READS THE TERMS OF
 * THE LICENSE. "LICENSED WORK" AND "RECIPIENT" ARE DEFINED IN THE LICENSE. A COPY
 * OF THE LICENSE IS LOCATED IN THE TEXT FILE ENTITLED "LICENSE.TXT" ACCOMPANYING
 * THE CONTENTS OF THIS FILE.
 */
package com.guillermomolina.protos.execution;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosEnvironmentValue;
import com.guillermomolina.protos.runtime.ProtosFilesystemValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosProcessRuntime;
import com.oracle.truffle.api.source.Source;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Test-only owner of the already-ratified production Process hosting/public-parse path.
 *
 * <p>C3C uses this fixture only where an old Java harness had crossed a RootTask, C-prime I/O,
 * source-backed Task callback or P boundary after B6B retired the AST/replay fallback. It is not a
 * second runtime entry architecture and is deliberately kept under src/test.
 */
final class ProtosHostedExecutionTestFixture implements AutoCloseable {
    private final ProtosPrelude prelude;
    private final ProtosStandaloneProcessBootstrap.Result bootstrap;
    private final ProtosPolyglotRuntimeHost runtimeHost;
    private final ProtosPolyglotProcessContext processContext;
    private boolean closed;

    private ProtosHostedExecutionTestFixture(
            ProtosPrelude prelude,
            ProtosStandaloneProcessBootstrap.Result bootstrap,
            ProtosPolyglotRuntimeHost runtimeHost,
            ProtosPolyglotProcessContext processContext) {
        this.prelude = Objects.requireNonNull(prelude, "prelude");
        this.bootstrap = Objects.requireNonNull(bootstrap, "bootstrap");
        this.runtimeHost = Objects.requireNonNull(runtimeHost, "runtimeHost");
        this.processContext = Objects.requireNonNull(processContext, "processContext");
    }

    static ProtosHostedExecutionTestFixture open(ProtosPrelude prelude) {
        Objects.requireNonNull(prelude, "prelude");
        ProtosStandaloneProcessBootstrap.Result bootstrap =
                ProtosStandaloneProcessBootstrap.create(
                        prelude,
                        List.of(),
                        exactEnvironmentDomain(),
                        List.of(),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null);
        ProtosPolyglotRuntimeHost runtimeHost = ProtosPolyglotRuntimeHost.open();
        try {
            ProtosPolyglotProcessContext processContext =
                    runtimeHost.hostProcess(
                            bootstrap.process(),
                            InputStream.nullInputStream(),
                            OutputStream.nullOutputStream(),
                            OutputStream.nullOutputStream());
            return new ProtosHostedExecutionTestFixture(
                    prelude, bootstrap, runtimeHost, processContext);
        } catch (RuntimeException | Error failure) {
            bootstrap.process().requestTerminationForRuntime();
            try {
                runtimeHost.close();
            } catch (RuntimeException | Error closeFailure) {
                failure.addSuppressed(closeFailure);
            }
            throw failure;
        }
    }

    ProtosActivation activation() {
        return bootstrap.activation();
    }

    ProtosProcessRuntime process() {
        return bootstrap.process();
    }

    ProtosExecutionOutcome execute(String sourceName, CharSequence characters) {
        return processContext.execute(source(sourceName, characters), activation());
    }

    Object evaluatePersistent(String sourceName, CharSequence characters) {
        return processContext.evaluatePersistent(source(sourceName, characters), activation());
    }

    <T> T callEntered(Supplier<T> action) {
        return processContext.callForRuntime(action);
    }

    ProtosFilesystemValue installFilesystem(
            String slotName, ProtosStandardFilesystemProtocol.Backend backend) {
        Objects.requireNonNull(slotName, "slotName");
        Objects.requireNonNull(backend, "backend");
        if (activation().context().hasLocalSlot(slotName)) {
            throw new IllegalStateException("hosted test slot already exists: " + slotName);
        }
        Object raw =
                ProtosStandardFilesystemProtocol.createCapability(
                        prelude.bytesPrototypeForRuntime(), activation(), backend);
        if (!(raw instanceof ProtosFilesystemValue filesystem)) {
            throw new IllegalStateException("filesystem capability has the wrong value family");
        }
        activation().context().createLocalSlot(slotName, filesystem);
        return filesystem;
    }

    private static Source source(String sourceName, CharSequence characters) {
        return Source.newBuilder(
                        ProtosLanguage.ID,
                        Objects.requireNonNull(characters, "characters"),
                        Objects.requireNonNull(sourceName, "sourceName"))
                .mimeType(ProtosLanguage.MIME_TYPE)
                .build();
    }

    private static ProtosEnvironmentValue.NativeNameDomain exactEnvironmentDomain() {
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

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        Throwable failure = null;
        try {
            bootstrap.process().requestTerminationForRuntime();
            bootstrap.process().awaitTerminationForRuntime();
            processContext.awaitTerminalDispositionForRuntime();
        } catch (RuntimeException | Error terminalFailure) {
            failure = terminalFailure;
        }
        try {
            runtimeHost.close();
        } catch (RuntimeException | Error closeFailure) {
            if (failure != null) {
                failure.addSuppressed(closeFailure);
            } else {
                failure = closeFailure;
            }
        }
        if (failure instanceof RuntimeException runtimeFailure) {
            throw runtimeFailure;
        }
        if (failure instanceof Error error) {
            throw error;
        }
    }
}
