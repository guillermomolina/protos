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

import com.guillermomolina.protos.runtime.io.ProtosResolverBackend;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Initial D223-A1 host name-service adapter of one Resolver capability.
 *
 * <p>It is the only place that calls {@link InetAddress#getAllByName}; it receives names that the
 * Protos Resolver protocol has already admitted and normalized (lowercase, absolute, terminated
 * by the root dot, so no search domain applies). Work runs on at most {@link #MAX_WORKERS} named
 * daemon host threads with at most {@link #MAX_QUEUED} waiting requests; a request beyond that
 * capacity fails synchronously with {@code LIMIT}. Workers copy address octets into host-only
 * descriptors and never touch guest objects, Futures or Actors. The executor is created lazily by
 * the first lookup, so a granted but unused Resolver costs no thread.
 *
 * <p>Cache boundary. Every capability owns its own instance of this adapter, and with it its own
 * admission capacity, pending-request state, cancellation state and lifecycle; the adapter holds
 * no cache and no result state, and the lookup flow materializes every successful result as a
 * fresh frozen Array of fresh IpAddress values in the owning Actor domain. The host name service
 * behind {@link InetAddress#getAllByName} is not per capability: the JDK keeps its own JVM-wide
 * positive and negative lookup cache, governed by JVM security properties, and the operating
 * system may cache as well. Those caches are shared by every capability, Polyglot Context and
 * other code in the same JVM, so an earlier lookup can affect the timing and the answer of a later
 * lookup made through another capability. A separate executor per capability does not create a
 * separate {@code InetAddress} cache. This adapter neither configures, disables nor bypasses
 * those caches, and they confer no authority: name-service work starts only through an explicit,
 * unretired Resolver grant.
 *
 * <p>Cancellation removes a queued request or marks a running one so that its result is not
 * delivered; a running operating-system lookup is never interrupted. {@link #close()} stops
 * admission and retires queued requests without completing them.
 *
 * <p>{@link UnknownHostException} does not portably distinguish a negative answer from a
 * transient name-service failure, so this adapter reports both as {@code NO_ADDRESS}; only other
 * runtime failures are {@code UNAVAILABLE}.
 */
final class ProtosHostResolverBackend implements ProtosResolverBackend {
    static final int MAX_WORKERS = 4;
    static final int MAX_QUEUED = 64;

    /** Host name-service seam; the production adapter is {@link InetAddress#getAllByName}. */
    @FunctionalInterface
    interface NameService {
        InetAddress[] lookup(String absoluteName) throws UnknownHostException;
    }

    private static final AtomicInteger THREAD_SEQUENCE = new AtomicInteger();

    private final NameService nameService;
    private ThreadPoolExecutor executor;
    private boolean closed;

    ProtosHostResolverBackend() {
        this(InetAddress::getAllByName);
    }

    ProtosHostResolverBackend(NameService nameService) {
        this.nameService = Objects.requireNonNull(nameService, "nameService");
    }

    @Override
    public Cancellation lookup(String absoluteName, Completion completion) {
        Objects.requireNonNull(absoluteName, "absoluteName");
        Objects.requireNonNull(completion, "completion");
        Request request = new Request(absoluteName, completion);
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
            completion.failed(FailureKind.UNAVAILABLE);
            return () -> {};
        }
        try {
            current.execute(request);
        } catch (RejectedExecutionException saturatedOrClosed) {
            completion.failed(FailureKind.LIMIT);
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
            // Queued requests are retired undelivered; their operations were already settled
            // by the Resolver close cutover. A blocked worker finishes and is discarded.
            current.shutdownNow();
        }
    }

    synchronized boolean executorInitializedForTesting() {
        return executor != null;
    }

    private static ThreadPoolExecutor newExecutor() {
        ThreadPoolExecutor pool =
                new ThreadPoolExecutor(
                        MAX_WORKERS,
                        MAX_WORKERS,
                        30,
                        TimeUnit.SECONDS,
                        new ArrayBlockingQueue<>(MAX_QUEUED),
                        command -> {
                            Thread worker =
                                    new Thread(
                                            command,
                                            "protos-resolver-" + THREAD_SEQUENCE.incrementAndGet());
                            worker.setDaemon(true);
                            return worker;
                        },
                        new ThreadPoolExecutor.AbortPolicy());
        pool.allowCoreThreadTimeOut(true);
        return pool;
    }

    private final class Request implements Runnable {
        private final String name;
        private final Completion completion;
        private final AtomicBoolean cancelled = new AtomicBoolean();

        Request(String name, Completion completion) {
            this.name = name;
            this.completion = completion;
        }

        @Override
        public void run() {
            if (cancelled.get()) {
                return;
            }
            List<AddressDescriptor> descriptors = null;
            FailureKind failure = null;
            try {
                InetAddress[] answers = nameService.lookup(name);
                if (answers == null || answers.length == 0) {
                    failure = FailureKind.NO_ADDRESS;
                } else if (answers.length > MAX_RAW_ADDRESSES) {
                    failure = FailureKind.LIMIT;
                } else {
                    descriptors = new ArrayList<>(answers.length);
                    for (InetAddress answer : answers) {
                        byte[] octets = answer == null ? null : answer.getAddress();
                        if (octets != null && (octets.length == 4 || octets.length == 16)) {
                            descriptors.add(new AddressDescriptor(octets));
                        }
                    }
                    if (descriptors.isEmpty()) {
                        failure = FailureKind.NO_ADDRESS;
                    }
                }
            } catch (UnknownHostException unknown) {
                failure = FailureKind.NO_ADDRESS;
            } catch (SecurityException refused) {
                failure = FailureKind.DENIED;
            } catch (RuntimeException unavailable) {
                failure = FailureKind.UNAVAILABLE;
            }
            if (cancelled.get()) {
                return; // late result after cancellation: never delivered
            }
            if (failure != null) {
                completion.failed(failure);
            } else {
                completion.succeeded(List.copyOf(descriptors));
            }
        }
    }
}
