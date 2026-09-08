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

import com.guillermomolina.protos.runtime.ProtosProcessRuntime;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.graalvm.polyglot.Engine;

/**
 * Explicit host owner of one shareable Truffle Engine and its Process-scoped Contexts.
 *
 * <p>This object is not a Protos Node or another semantic runtime entity. Its lifetime is selected
 * by the embedding driver; A4B2 deliberately does not make it a global singleton or decide the
 * permanent number of Engines per JVM.
 */
public final class ProtosPolyglotRuntimeHost implements AutoCloseable {
    private final Engine engine;
    private final AtomicInteger activeProcessContexts = new AtomicInteger();
    private final AtomicReference<Throwable> contextCloseFailure = new AtomicReference<>();
    private boolean closed;

    private ProtosPolyglotRuntimeHost(Engine engine) {
        this.engine = Objects.requireNonNull(engine, "engine");
    }

    public static ProtosPolyglotRuntimeHost open() {
        return new ProtosPolyglotRuntimeHost(Engine.create(ProtosLanguage.ID));
    }

    public ProtosPolyglotProcessContext hostProcess(
            ProtosProcessRuntime process,
            InputStream in,
            OutputStream out,
            OutputStream err) {
        Objects.requireNonNull(process, "process");
        Objects.requireNonNull(in, "in");
        Objects.requireNonNull(out, "out");
        Objects.requireNonNull(err, "err");

        synchronized (this) {
            if (closed) {
                throw new IllegalStateException("Polyglot runtime host is closed");
            }
            activeProcessContexts.incrementAndGet();
        }

        ProtosPolyglotExecutionContext context = null;
        try {
            context =
                    ProtosPolyglotExecutionContext.open(
                            engine,
                            in,
                            out,
                            err,
                            activeProcessContexts::decrementAndGet);
            ProtosPolyglotProcessContext hosted =
                    new ProtosPolyglotProcessContext(this, process, context);
            process.bindExecutionHostForRuntime(hosted);
            return hosted;
        } catch (RuntimeException | Error failure) {
            if (context != null) {
                try {
                    context.close();
                } catch (RuntimeException | Error closeFailure) {
                    failure.addSuppressed(closeFailure);
                }
            } else {
                activeProcessContexts.decrementAndGet();
            }
            throw failure;
        }
    }

    void recordContextCloseFailure(Throwable failure) {
        contextCloseFailure.compareAndSet(null, Objects.requireNonNull(failure, "failure"));
    }

    Engine engineForTesting() {
        return engine;
    }

    int activeProcessContextCountForTesting() {
        return activeProcessContexts.get();
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        if (activeProcessContexts.get() != 0) {
            throw new IllegalStateException(
                    "Polyglot runtime host cannot close while Process Contexts are active");
        }
        Throwable failure = contextCloseFailure.get();
        if (failure != null) {
            throw new IllegalStateException("A Process Context failed to close", failure);
        }
        engine.close();
        closed = true;
    }
}
