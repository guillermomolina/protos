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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.BooleanSupplier;

/**
 * The Actor carrier threads of one embedded Process (PLAT054-3D), with explicit control over
 * carrier registration, start, and termination.
 *
 * <p>Invariants, all under {@link #lock}:
 *
 * <ul>
 *   <li>a carrier is registered only after {@code start()} succeeded, inside the same critical
 *       section as the {@link #closed} check, so the snapshot taken by {@link #closeAndJoin} holds
 *       every carrier that will ever run and none of them is still {@code NEW};
 *   <li>a command enters the queue only when a live carrier will take it; a command whose new
 *       carrier cannot be created or started is never queued and is rejected, so accepted commands
 *       are never removed.
 * </ul>
 *
 * <p>Termination is a thread join, never the pool's own bookkeeping, so it holds also when the
 * Context cancels a carrier before or during its first command.
 *
 * <p>Nothing that escapes a command is caught. The only command is the scheduler's {@code
 * workerLoop}, which already handles {@code RuntimeException}; anything else that escapes it
 * ({@code ThreadDeath}, including Truffle cancellation, a {@code VirtualMachineError}, or another
 * {@code Error}) has already broken the scheduler's worker accounting, so it ends the carrier as a
 * real uncaught exception. Before ending, the carrier starts a replacement if the pool is open and
 * accepted commands remain, because those were accounted correctly. If that replacement cannot
 * start either, they stay queued until a carrier becomes available: an exceptional runtime limit,
 * not a guaranteed recovery.
 */
final class ProtosEmbeddedCarrierPool implements Executor {
    /** Creates an unstarted carrier thread for {@code body}; may throw to refuse creation. */
    @FunctionalInterface
    interface CarrierFactory {
        Thread newCarrier(Runnable body, int number);
    }

    /** One wait for the next command, possibly interrupted. */
    @FunctionalInterface
    interface InterruptibleTake {
        Runnable take() throws InterruptedException;
    }

    /**
     * Runs one wait so the carrier's runtime can still reach it, for example as a Truffle blocked
     * region that processes safepoints and may end the wait by cancellation.
     */
    @FunctionalInterface
    interface BlockingRegion {
        Runnable await(InterruptibleTake take) throws InterruptedException;
    }

    private final Object lock = new Object();
    private final int parallelism;
    private final CarrierFactory factory;
    private final BlockingRegion blockingRegion;
    private final BooleanSupplier contextTerminating;
    private final ArrayDeque<Runnable> queue = new ArrayDeque<>();
    /* Every carrier ever started. */
    private final List<Thread> carriers = new ArrayList<>();
    private int liveCarriers;
    private int idleCarriers;
    private int sequence;
    private boolean closed;

    /* Test seams: run inside the start critical section, and before a carrier's first command. */
    volatile Runnable beforeStartForTesting;
    volatile Runnable beforeRunForTesting;

    ProtosEmbeddedCarrierPool(
            int parallelism,
            CarrierFactory factory,
            BlockingRegion blockingRegion,
            BooleanSupplier contextTerminating) {
        if (parallelism < 1) {
            throw new IllegalArgumentException("carrier parallelism must be positive");
        }
        this.parallelism = parallelism;
        this.factory = Objects.requireNonNull(factory, "factory");
        this.blockingRegion = Objects.requireNonNull(blockingRegion, "blockingRegion");
        this.contextTerminating = Objects.requireNonNull(contextTerminating, "contextTerminating");
    }

    /**
     * Accepts {@code command} for a carrier: an idle carrier takes it, else a new carrier starts
     * with it, else it waits for a busy carrier.
     *
     * @throws RejectedExecutionException when the pool is closed or the Context is terminating, or
     *     when the new carrier cannot be created or started; the command is then not accepted
     */
    @Override
    public void execute(Runnable command) {
        Objects.requireNonNull(command, "command");
        synchronized (lock) {
            if (closed || contextTerminating.getAsBoolean()) {
                throw new RejectedExecutionException("Protos Actor carriers are closed");
            }
            if (idleCarriers > queue.size() || liveCarriers >= parallelism) {
                queue.addLast(command);
                lock.notify();
                return;
            }
            startCarrierLocked(command);
        }
    }

    /** Starts and registers one carrier whose first command is {@code first} (or the queue). */
    private void startCarrierLocked(Runnable first) {
        Thread carrier;
        try {
            carrier = factory.newCarrier(() -> runCarrier(first), ++sequence);
            Runnable hook = beforeStartForTesting;
            if (hook != null) {
                hook.run();
            }
            carrier.start();
        } catch (RuntimeException refused) {
            throw new RejectedExecutionException("a Protos Actor carrier cannot be started", refused);
        }
        carriers.add(carrier);
        liveCarriers++;
    }

    private void runCarrier(Runnable first) {
        try {
            Runnable hook = beforeRunForTesting;
            if (hook != null) {
                hook.run();
            }
            Runnable next = first != null ? first : take();
            while (next != null) {
                next.run();
                next = take();
            }
        } finally {
            synchronized (lock) {
                liveCarriers--;
                if (!closed && !queue.isEmpty() && !contextTerminating.getAsBoolean()) {
                    try {
                        startCarrierLocked(null);
                    } catch (RejectedExecutionException unavailable) {
                        // Exceptional limit (see class documentation): the commands stay queued
                        // for any carrier; the escaping failure keeps propagating.
                    }
                }
            }
        }
    }

    /**
     * The next command, or {@code null} once closed. An interruption only re-checks the state: it
     * may be a runtime safepoint rather than closing, so the carrier keeps serving an open pool.
     */
    private Runnable take() {
        while (true) {
            try {
                return blockingRegion.await(this::awaitNext);
            } catch (InterruptedException interruption) {
                // Not a close request by itself; awaitNext re-reads the pool state.
            }
        }
    }

    /* Waits holding the lock only inside wait(), so a runtime action never runs under it. */
    private Runnable awaitNext() throws InterruptedException {
        synchronized (lock) {
            idleCarriers++;
            try {
                while (true) {
                    Runnable next = queue.pollFirst();
                    if (next != null || closed) {
                        return next;
                    }
                    lock.wait();
                }
            } finally {
                idleCarriers--;
            }
        }
    }

    /**
     * Closes the pool and joins every carrier that ever started. No carrier can start afterwards,
     * and queued commands are discarded. The join holds no pool lock.
     *
     * @throws IllegalStateException when called on one of this pool's carriers, which cannot join
     *     itself
     */
    void closeAndJoin() {
        List<Thread> snapshot;
        synchronized (lock) {
            closed = true;
            queue.clear();
            lock.notifyAll();
            snapshot = List.copyOf(carriers);
        }
        Thread current = Thread.currentThread();
        if (snapshot.contains(current)) {
            throw new IllegalStateException("a Protos Actor carrier cannot close its own carriers");
        }
        boolean interrupted = false;
        for (Thread carrier : snapshot) {
            while (carrier.isAlive()) {
                try {
                    carrier.join();
                } catch (InterruptedException interruption) {
                    interrupted = true;
                }
            }
        }
        if (interrupted) {
            current.interrupt();
        }
    }

    List<Thread> carriersForTesting() {
        synchronized (lock) {
            return List.copyOf(carriers);
        }
    }

    int queuedCommandCountForTesting() {
        synchronized (lock) {
            return queue.size();
        }
    }
}
