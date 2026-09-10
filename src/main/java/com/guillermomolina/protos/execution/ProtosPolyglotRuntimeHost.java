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

import com.guillermomolina.protos.runtime.ProtosActorScheduler;
import com.guillermomolina.protos.runtime.ProtosNetworkCapabilityValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosProcessRuntime;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
    private final int actorCarrierParallelism;
    private final AtomicInteger activeProcessContexts = new AtomicInteger();
    private final AtomicInteger actorCarrierThreadSequence = new AtomicInteger();
    private final AtomicReference<Throwable> contextCloseFailure = new AtomicReference<>();
    private ExecutorService actorCarrierExecutor;
    private ProtosActorScheduler actorScheduler;
    private ProtosNioNetworkHost networkHost;
    private boolean closed;

    private ProtosPolyglotRuntimeHost(Engine engine) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.actorCarrierParallelism =
                Math.max(1, Runtime.getRuntime().availableProcessors());
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

    /**
     * Returns the one normal-Actor scheduler owned by this RuntimeHost.
     *
     * <p>The fixed executor is created lazily so a host that never schedules a child Actor pays no
     * Actor-carrier thread cost. Its platform threads are shared across every local Process Context
     * hosted here; Process termination never owns this executor's lifetime.
     */
    synchronized ProtosActorScheduler actorSchedulerForRuntime() {
        if (closed) {
            throw new IllegalStateException("Polyglot runtime host is closed");
        }
        if (actorScheduler == null) {
            actorCarrierExecutor =
                    Executors.newFixedThreadPool(
                            actorCarrierParallelism,
                            command -> {
                                Thread carrier =
                                        new Thread(
                                                command,
                                                "protos-actor-carrier-"
                                                        + actorCarrierThreadSequence.incrementAndGet());
                                carrier.setDaemon(true);
                                return carrier;
                            });
            actorScheduler =
                    new ProtosActorScheduler(actorCarrierExecutor, actorCarrierParallelism);
        }
        return actorScheduler;
    }

    boolean actorCarrierSubstrateInitializedForTesting() {
        synchronized (this) {
            return actorScheduler != null;
        }
    }

    int actorCarrierParallelismForTesting() {
        return actorCarrierParallelism;
    }

    /**
     * Explicitly provisions one host Network capability for the supplied Prelude.
     *
     * <p>This operation does not install the capability into a Process or module. The caller owns
     * the existing B3 grant decision. The underlying NIO plane is lazy and shared by every
     * capability explicitly provisioned from this RuntimeHost.
     */
    public synchronized ProtosNetworkCapabilityValue provisionHostNetwork(ProtosPrelude prelude)
            throws IOException {
        Objects.requireNonNull(prelude, "prelude");
        if (closed) {
            throw new IllegalStateException("Polyglot runtime host is closed");
        }
        if (networkHost == null) {
            networkHost = new ProtosNioNetworkHost();
        }
        return networkHost.provision(prelude);
    }

    boolean networkHostInitializedForTesting() {
        synchronized (this) {
            return networkHost != null;
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
        if (actorCarrierExecutor != null) {
            actorCarrierExecutor.close();
        }
        if (networkHost != null) {
            networkHost.close();
        }
        if (failure != null) {
            throw new IllegalStateException("A Process Context failed to close", failure);
        }
        engine.close();
        closed = true;
    }
}
