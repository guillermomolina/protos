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
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.source.Source;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;

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
    private final ReentrantReadWriteLock lifecycle = new ReentrantReadWriteLock(true);
    private final Lock executionLock = lifecycle.readLock();
    private final Lock closeLock = lifecycle.writeLock();
    private final Runnable closedCallback;
    private volatile boolean closeRequested;
    private volatile boolean closed;

    private ProtosPolyglotExecutionContext(Context context, Runnable closedCallback) {
        this.context = Objects.requireNonNull(context, "context");
        this.closedCallback = Objects.requireNonNull(closedCallback, "closedCallback");
    }

    public static ProtosPolyglotExecutionContext open(
            InputStream in, OutputStream out, OutputStream err) {
        return open(null, in, out, err, () -> {});
    }

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

        Context.Builder builder =
                Context.newBuilder(ProtosLanguage.ID)
                        .in(in)
                        .out(out)
                        .err(err);
        if (engine != null) {
            builder.engine(engine);
        }
        Context context = builder.build();
        boolean initialized = false;
        try {
            context.initialize(ProtosLanguage.ID);
            initialized = true;
            return new ProtosPolyglotExecutionContext(context, closedCallback);
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

    private void finishRequestedClose() {
        if (!closeRequested || closed || lifecycle.getReadHoldCount() != 0) {
            return;
        }
        closeLock.lock();
        try {
            if (closed) {
                return;
            }
            context.close();
            closed = true;
            closedCallback.run();
        } finally {
            closeLock.unlock();
        }
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
