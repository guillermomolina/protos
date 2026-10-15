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
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.source.Source;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Value;

/**
 * One host-owned Polyglot context that may be entered by multiple Protos carrier threads.
 *
 * <p>This is implementation machinery, not a Protos semantic context, Process, Actor, Task, or
 * carrier identity. Each execution enters and leaves the Polyglot context on the current carrier.
 * A per-context read/write lifecycle lock allows executions to overlap while preventing close from
 * racing an in-flight guest segment; it is not a global execution lock or language-level GIL.
 * Semantic execution state remains explicit in {@link ProtosActivation}.
 */
public final class ProtosPolyglotExecutionContext implements AutoCloseable {
    private final Context context;
    private final ProtosSourceReadabilityAuthority sourceReadability;
    private final ReentrantReadWriteLock lifecycle = new ReentrantReadWriteLock(true);
    private final Lock executionLock = lifecycle.readLock();
    private final Lock closeLock = lifecycle.writeLock();
    private final Runnable closedCallback;
    private volatile boolean closeRequested;
    private volatile boolean closed;
    private boolean closeTerminal;
    private Throwable closeFailure;

    private ProtosPolyglotExecutionContext(
            Context context,
            ProtosSourceReadabilityAuthority sourceReadability,
            Runnable closedCallback) {
        this.context = Objects.requireNonNull(context, "context");
        this.sourceReadability = Objects.requireNonNull(sourceReadability, "sourceReadability");
        this.closedCallback = Objects.requireNonNull(closedCallback, "closedCallback");
    }

    public static ProtosPolyglotExecutionContext open(
            InputStream in, OutputStream out, OutputStream err) {
        return open(null, in, out, err, () -> {});
    }

    @com.oracle.truffle.api.CompilerDirectives.TruffleBoundary
    static ProtosPolyglotExecutionContext open(
            Engine engine,
            InputStream in,
            OutputStream out,
            OutputStream err,
            Runnable closedCallback) {
        Objects.requireNonNull(in, "in");
        Objects.requireNonNull(out, "out");
        Objects.requireNonNull(err, "err");
        Objects.requireNonNull(closedCallback, "closedCallback");

        ProtosSourceReadabilityAuthority sourceReadability =
                new ProtosSourceReadabilityAuthority();
        Context.Builder builder =
                Context.newBuilder(ProtosLanguage.ID)
                        .in(in)
                        .out(out)
                        .err(err)
                        .allowIO(sourceReadability.ioAccess());
        if (engine != null) {
            builder.engine(engine);
        }
        Context context = builder.build();
        boolean initialized = false;
        try {
            context.initialize(ProtosLanguage.ID);
            ProtosPolyglotExecutionContext host =
                    new ProtosPolyglotExecutionContext(context, sourceReadability, closedCallback);
            /*
             * The wrapper is bound to its Context's ProtosLanguageContext once, so bounded
             * Context-local platform services such as PLAT022 admission reach this wrapper on any
             * entry, including framework-owned Value execution. It is not Protos Process, Actor,
             * Task, or semantic Context identity and carries no guest state.
             */
            context.enter();
            try {
                ProtosLanguageContext.current().bindHostExecutionContextForRuntime(host);
            } finally {
                context.leave();
            }
            initialized = true;
            return host;
        } finally {
            if (!initialized) {
                context.close();
            }
        }
    }

    /**
     * Parses through the registered public Protos language and runs one semantic root task.
     *
     * <p>The current carrier enters only for the bounded parse/execution extent. Concurrent calls
     * share this same Context and may execute simultaneously. The returned value is the existing
     * inert {@link ProtosExecutionOutcome}; I026-D still owns any Polyglot-visible value model.
     */
    public ProtosExecutionOutcome execute(Source source, ProtosActivation activation) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(activation, "activation");
        return callEntered(
                () -> {
                    CallTarget target = ProtosLanguageContext.current().parsePublic(source);
                    return ProtosRootTaskExecution.execute(target, activation);
                });
    }

    ProtosExecutionOutcome executeFile(
            Path path,
            CharSequence characters,
            ProtosActivation activation) {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(characters, "characters");
        Objects.requireNonNull(activation, "activation");
        return callEntered(
                () -> {
                    ProtosLanguageContext languageContext = ProtosLanguageContext.current();
                    Source source = languageContext.materializeFileSource(path, characters);
                    CallTarget target = languageContext.parsePublic(source);
                    return ProtosRootTaskExecution.execute(target, activation);
                });
    }

    ProtosExecutionOutcome executeModuleSource(
            ProtosModuleSource moduleSource,
            ProtosActivation activation) {
        Objects.requireNonNull(moduleSource, "moduleSource");
        Objects.requireNonNull(activation, "activation");
        return callEntered(
                () -> {
                    ProtosLanguageContext languageContext = ProtosLanguageContext.current();
                    Source source = languageContext.materializeModuleSource(moduleSource);
                    CallTarget target = languageContext.parsePublic(source);
                    return ProtosRootTaskExecution.execute(target, activation);
                });
    }

    /**
     * Parses and evaluates one persistent top-level unit without manufacturing a new RootActor task.
     *
     * <p>The caller owns the persistent activation contract (currently the REPL). Guest execution
     * still occurs only while this Process Context is entered.
     */
    Object evaluatePersistent(Source source, ProtosActivation activation) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(activation, "activation");
        return callEntered(
                () -> {
                    CallTarget target = ProtosLanguageContext.current().parsePublic(source);
                    try {
                        return target.call(activation);
                    } catch (ProtosSignalException escaped) {
                        /*
                         * CLI008-C1: this unit has no Task, so the escaping occurrence is its
                         * terminal failure. Project it while the Context is still entered.
                         */
                        escaped.attachTerminalDiagnosticTraceForRuntime(
                                ProtosDiagnosticTraceCapture.capture(escaped));
                        throw escaped;
                    }
                });
    }

    <T> T callEntered(Supplier<T> action) {
        Objects.requireNonNull(action, "action");
        executionLock.lock();
        try {
            requireOpen();
            context.enter();
            Throwable failure = null;
            try {
                return action.get();
            } catch (RuntimeException | Error executionFailure) {
                failure = executionFailure;
                throw executionFailure;
            } finally {
                try {
                    context.leave();
                } catch (RuntimeException | Error leaveFailure) {
                    if (failure != null) {
                        failure.addSuppressed(leaveFailure);
                    } else {
                        throw leaveFailure;
                    }
                }
            }
        } finally {
            executionLock.unlock();
            finishRequestedClose();
        }
    }

    /**
     * True when the current entered Polyglot Context places a Protos Process: it is owned by a
     * Protos host wrapper, or it is a PLAT054 standard embedding placing its own Process.
     */
    static boolean hasEnteredContextForRuntime() {
        ProtosLanguageContext entered = ProtosLanguageContext.currentIfEnteredForRuntime();
        return entered != null
                && (entered.hostExecutionContextOrNullForRuntime() != null
                        || entered.isStandardEmbeddingForRuntime());
    }

    private static ProtosPolyglotExecutionContext enteredHostOrNull() {
        ProtosLanguageContext entered = ProtosLanguageContext.currentIfEnteredForRuntime();
        return entered == null ? null : entered.hostExecutionContextOrNullForRuntime();
    }

    /**
     * PERF033-A: wraps a guest interop object as a {@link Value} bound to this Context, for
     * framework-owned host-to-guest execution. No Context entry is performed here.
     */
    Value asValueForRuntime(Object guestObject) {
        Objects.requireNonNull(guestObject, "guestObject");
        requireOpen();
        return context.asValue(guestObject);
    }

    static void admitPhysicalSourceForRuntime(Path path) {
        ProtosPolyglotExecutionContext current = enteredHostOrNull();
        if (current == null) {
            throw new IllegalStateException(
                    "physical Source admission requires an entered Protos Process Context");
        }
        current.sourceReadability.admit(path);
    }

    private void requireOpen() {
        if (closeRequested || closed) {
            throw new IllegalStateException("Polyglot Protos execution context is closed");
        }
    }

    /**
     * Requests platform cleanup after semantic Process termination.
     *
     * <p>If invoked from an entered carrier, actual Context.close is deferred until that carrier
     * leaves. New entries fail immediately after the request. Calls from other threads may wait for
     * already-entered carriers through the existing exclusive close side.
     */
    void requestClose() {
        closeRequested = true;
        if (lifecycle.getReadHoldCount() == 0) {
            finishRequestedClose();
        }
    }

    @com.oracle.truffle.api.CompilerDirectives.TruffleBoundary
    private void finishRequestedClose() {
        if (!closeRequested || closed || closeTerminal || lifecycle.getReadHoldCount() != 0) {
            return;
        }
        closeLock.lock();
        try {
            if (closed || closeTerminal) {
                return;
            }
            Throwable failure = null;
            try {
                context.close();
                closed = true;
                closedCallback.run();
            } catch (RuntimeException | Error closeFailure) {
                failure = closeFailure;
                throw closeFailure;
            } finally {
                synchronized (this) {
                    this.closeFailure = failure;
                    closeTerminal = true;
                    notifyAll();
                }
            }
        } finally {
            closeLock.unlock();
        }
    }

    Throwable awaitCloseDispositionForRuntime() {
        boolean interrupted = false;
        Throwable failure;
        synchronized (this) {
            while (!closeTerminal) {
                try {
                    wait();
                } catch (InterruptedException interruption) {
                    interrupted = true;
                }
            }
            failure = closeFailure;
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
        return failure;
    }

    Engine engineForTesting() {
        return context.getEngine();
    }

    boolean isClosedForTesting() {
        return closed;
    }

    @Override
    public void close() {
        if (lifecycle.getReadHoldCount() != 0) {
            throw new IllegalStateException(
                    "Polyglot Protos execution context cannot close from an executing carrier");
        }
        closeRequested = true;
        finishRequestedClose();
    }
}
