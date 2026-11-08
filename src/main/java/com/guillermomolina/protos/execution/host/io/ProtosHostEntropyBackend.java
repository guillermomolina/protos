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

import com.guillermomolina.protos.runtime.io.ProtosEntropyBackend;
import java.security.SecureRandom;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * D243-C asynchronous host CSPRNG backend.
 *
 * <p>Each grant owns a separate backend and executor. SecureRandom instances
 * are created only by runtime workers, after an authorized positive request.
 * No generator or generated material is captured in a Native Image heap.
 */
final class ProtosHostEntropyBackend implements ProtosEntropyBackend {
    static final int MAX_WORKERS = 4;
    static final int MAX_QUEUED = 64;

    @FunctionalInterface
    interface ByteSource {
        byte[] generate(int count) throws Exception;
    }

    private static final AtomicInteger THREAD_SEQUENCE =
            new AtomicInteger();

    private final AtomicBoolean randomInitialized = new AtomicBoolean();

    private final ThreadLocal<SecureRandom> generators =
            ThreadLocal.withInitial(() -> {
                SecureRandom generator = new SecureRandom();
                randomInitialized.set(true);
                return generator;
            });

    private final ByteSource source;
    private volatile boolean closed;
    private ThreadPoolExecutor executor;

    ProtosHostEntropyBackend() {
        this.source = count -> {
            byte[] result = new byte[count];
            generators.get().nextBytes(result);
            return result;
        };
    }

    /** Host-only injection seam for deterministic concurrency tests. */
    ProtosHostEntropyBackend(ByteSource source) {
        this.source = Objects.requireNonNull(source, "source");
    }

    @Override
    public Cancellation generate(int count, Completion completion) {
        Objects.requireNonNull(completion, "completion");

        if (count <= 0 || count > MAX_BYTES) {
            completion.failed();
            return () -> {};
        }

        Request request = new Request(count, completion);
        ThreadPoolExecutor current;

        synchronized (this) {
            if (closed) {
                current = null;
            } else {
                if (executor == null) {
                    executor = newExecutor();
                }
                current = executor;
            }
        }

        if (current == null) {
            completion.failed();
            return () -> {};
        }

        try {
            current.execute(request);
        } catch (RejectedExecutionException rejected) {
            completion.failed();
            return () -> {};
        }

        return () -> {
            request.cancelled.set(true);
            current.remove(request);
        };
    }

    @Override
    public void close() {
        ThreadPoolExecutor current;

        synchronized (this) {
            if (closed) {
                return;
            }
            closed = true;
            current = executor;
        }

        if (current != null) {
            // Never wait for a potentially blocked CSPRNG provider.
            // Interruptions are best effort; running results are discarded.
            List<Runnable> pending = current.shutdownNow();
            for (Runnable runnable : pending) {
                if (runnable instanceof Request request) {
                    request.cancelled.set(true);
                }
            }
        }
    }

    synchronized boolean executorInitializedForTesting() {
        return executor != null;
    }

    boolean secureRandomInitializedForTesting() {
        return randomInitialized.get();
    }

    private static ThreadPoolExecutor newExecutor() {
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                MAX_WORKERS,
                MAX_WORKERS,
                30,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(MAX_QUEUED),
                command -> {
                    Thread worker = new Thread(
                            command,
                            "protos-entropy-"
                                    + THREAD_SEQUENCE.incrementAndGet());
                    worker.setDaemon(true);
                    return worker;
                },
                new ThreadPoolExecutor.AbortPolicy());
        pool.allowCoreThreadTimeOut(true);
        return pool;
    }

    private final class Request implements Runnable {
        private final int count;
        private final Completion completion;
        private final AtomicBoolean cancelled = new AtomicBoolean();

        Request(int count, Completion completion) {
            this.count = count;
            this.completion = completion;
        }

        @Override
        public void run() {
            if (cancelled.get() || closed) {
                return;
            }

            byte[] result = null;
            boolean failed = false;

            try {
                result = source.generate(count);
                if (result == null || result.length != count) {
                    failed = true;
                } else {
                    // Never publish a buffer retained by an injected source.
                    result = result.clone();
                }
            } catch (Exception providerFailure) {
                failed = true;
            }

            if (cancelled.get() || closed) {
                return;
            }

            if (failed) {
                completion.failed();
            } else {
                completion.succeeded(result);
            }
        }
    }
}
