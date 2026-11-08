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

import com.guillermomolina.protos.runtime.ProtosHostCompletionThread;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * I156 / PLAT072-E native executor of exactly one Filesystem grant.
 *
 * <p>Two dedicated platform threads per grant, never shared with another grant, a virtual-thread
 * carrier, a common pool or the Actor scheduler:
 *
 * <ul>
 *   <li>The <b>restricted thread</b> runs the grant's {@link Installer} on itself (Landlock is per
 *       thread) and afterwards executes only the grant's system calls, handed to it one at a time.
 *       It never runs Java orchestration, flow callbacks or Actor delivery, so it never needs to
 *       load a class or open a file outside the grant.
 *   <li>The <b>driver thread</b> runs the grant's operations ({@link Task}s). It is bound so that
 *       every system call it issues is executed synchronously on the restricted thread, and it is
 *       marked as a {@link ProtosHostCompletionThread}, so flows deliver its completions to the
 *       owning Actor. Native effects therefore keep running inside the commitment boundary of
 *       their operation, under the Landlock domain.
 * </ul>
 *
 * <p>A worker whose installation failed starts no driver, admits no work, and {@link #start}
 * fails. The task queue is bounded: a full queue refuses the submission (the operation then
 * fails). Cleanup tasks (descriptor release) bypass the bound but still respect revocation,
 * because each live descriptor contributes at most one of them.
 *
 * <p><b>Cancellation.</b> A queued task can be withdrawn before it starts ({@link
 * Task#rejected()} is then never called; the caller already owns the outcome). A started task runs
 * to completion: whether it crosses an irreversible commitment is decided inside the task through
 * the Protos commitment handshake, never by interrupting a native call.
 *
 * <p><b>Shutdown.</b> {@link #shutdown} stops admission, rejects queued tasks, lets the in-flight
 * task finish, runs the final cleanup on the driver (its system calls still run restricted), then
 * stops both threads and waits for them.
 */
final class ProtosLinuxConfinedFilesystemWorker {
    /** Maximum queued operations of one grant before submissions are refused. */
    static final int QUEUE_CAPACITY = 1024;
    private static final long INSTALL_TIMEOUT_SECONDS = 30;

    /** One unit of grant work, run on the driver thread. */
    interface Task {
        void run();

        /** Called at most once, instead of {@link #run()}, when the task can never run. */
        void rejected();

        /**
         * Called at most once, on the driver, after {@link #run()} threw unexpectedly. The task
         * must give its operation an explicit disposition: an outcome not yet published is
         * finalized as a failure, while an outcome already published (including an irreversible
         * commitment) stays as it is. It must not throw; the worker keeps serving later tasks.
         */
        void failedUnexpectedly(Throwable cause);
    }

    /** Installs the grant's restriction on the calling (restricted) thread. */
    @FunctionalInterface
    interface Installer {
        void install() throws IOException;
    }

    /** One system call handed from the driver to the restricted thread. */
    private static final class SyscallRequest {
        final String operation;
        final long number;
        final long[] arguments;
        long result;
        ProtosLinuxConfinedFilesystemNative.NativeException failure;
        boolean done;

        SyscallRequest(String operation, long number, long[] arguments) {
            this.operation = operation;
            this.number = number;
            this.arguments = arguments;
        }
    }

    private final Thread restricted;
    private final Thread driver;
    private final Installer installer;
    private final CompletableFuture<Void> ready = new CompletableFuture<>();

    /* Guarded by this. */
    private final ArrayDeque<Task> queue = new ArrayDeque<>();
    /* Guarded by this. */
    private boolean accepting;
    /* Guarded by this. */
    private boolean stopping;
    /* Guarded by this. */
    private boolean installed;
    /* Guarded by this. */
    private Runnable finalAction;

    private final Object handoff = new Object();
    /* Guarded by handoff. */
    private SyscallRequest pending;
    /* Guarded by handoff. */
    private boolean serving = true;

    private ProtosLinuxConfinedFilesystemWorker(String name, Installer installer) {
        this.installer = Objects.requireNonNull(installer, "installer");
        this.restricted =
                Thread.ofPlatform().name(name + "-restricted").daemon(true).unstarted(this::restrictedLoop);
        this.driver = Thread.ofPlatform().name(name).daemon(true).unstarted(this::driverLoop);
    }

    /**
     * Starts the worker and waits until its restriction is installed.
     *
     * @throws IOException when installation failed or did not finish; no work was admitted and
     *     both threads have ended
     */
    static ProtosLinuxConfinedFilesystemWorker start(String name, Installer installer)
            throws IOException {
        ProtosLinuxConfinedFilesystemWorker worker =
                new ProtosLinuxConfinedFilesystemWorker(name, installer);
        worker.restricted.start();
        try {
            worker.ready.get(INSTALL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (ExecutionException failed) {
            join(worker.restricted);
            Throwable cause = failed.getCause();
            throw cause instanceof IOException io
                    ? io
                    : new IOException("the Filesystem worker could not be restricted", cause);
        } catch (TimeoutException | InterruptedException notReady) {
            if (notReady instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            worker.stopServing();
            join(worker.restricted);
            throw new IOException("the Filesystem worker restriction did not complete", notReady);
        }
        synchronized (worker) {
            worker.installed = true;
            worker.accepting = true;
        }
        worker.driver.start();
        return worker;
    }

    // ----- restricted thread -----

    private void restrictedLoop() {
        // Everything this thread executes later is linked before the restriction.
        new SyscallRequest("preload", 0, new long[6]).done = true;
        try {
            installer.install();
        } catch (Throwable failure) {
            ready.completeExceptionally(failure);
            return;
        }
        ready.complete(null);
        while (true) {
            SyscallRequest request;
            synchronized (handoff) {
                while (pending == null && serving) {
                    waitUninterruptibly(handoff);
                }
                if (pending == null) {
                    return;
                }
                request = pending;
                pending = null;
            }
            execute(request);
            synchronized (handoff) {
                request.done = true;
                handoff.notifyAll();
            }
        }
    }

    private static void execute(SyscallRequest request) {
        long[] a = request.arguments;
        try {
            request.result =
                    ProtosLinuxConfinedFilesystemNative.invokeDirect(
                            request.operation, request.number, a[0], a[1], a[2], a[3], a[4], a[5]);
        } catch (ProtosLinuxConfinedFilesystemNative.NativeException failure) {
            request.failure = failure;
        } catch (RuntimeException | Error unexpected) {
            request.failure =
                    new ProtosLinuxConfinedFilesystemNative.NativeException(
                            "restricted native call failed", unexpected);
        }
    }

    /** Runs one system call of the driver on the restricted thread and waits for its result. */
    private long route(
            String operation, long number, long a0, long a1, long a2, long a3, long a4, long a5)
            throws ProtosLinuxConfinedFilesystemNative.NativeException {
        SyscallRequest request =
                new SyscallRequest(operation, number, new long[] {a0, a1, a2, a3, a4, a5});
        synchronized (handoff) {
            if (!serving) {
                throw new ProtosLinuxConfinedFilesystemNative.NativeException(
                        "the restricted Filesystem thread has stopped", null);
            }
            pending = request;
            handoff.notifyAll();
            while (!request.done) {
                waitUninterruptibly(handoff);
            }
        }
        if (request.failure != null) {
            throw request.failure;
        }
        return request.result;
    }

    private void stopServing() {
        synchronized (handoff) {
            serving = false;
            handoff.notifyAll();
        }
    }

    // ----- driver thread -----

    private void driverLoop() {
        ProtosLinuxConfinedFilesystemNative.bindRoute(this::route);
        ProtosHostCompletionThread.markCurrent();
        while (true) {
            Task task;
            Runnable cleanup = null;
            synchronized (this) {
                while (queue.isEmpty() && !stopping) {
                    waitUninterruptibly(this);
                }
                task = queue.pollFirst();
                if (task == null) {
                    cleanup = finalAction;
                    finalAction = null;
                }
            }
            if (task == null) {
                if (cleanup != null) {
                    runSafely(cleanup);
                }
                stopServing();
                return;
            }
            runTask(task);
        }
    }

    private void runTask(Task task) {
        boolean admitted;
        synchronized (this) {
            admitted = installed;
        }
        if (!admitted) {
            // Defensive: work never runs without the installed restriction.
            task.rejected();
            return;
        }
        try {
            task.run();
        } catch (RuntimeException | AssertionError unexpected) {
            // The grant keeps serving; the failed task alone receives an explicit disposition.
            try {
                task.failedUnexpectedly(unexpected);
            } catch (RuntimeException | AssertionError dispositionFailure) {
                unexpected.addSuppressed(dispositionFailure);
            }
        }
    }

    private static void runSafely(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException ignored) {
            // Final cleanup cannot publish a new outcome.
        }
    }

    // ----- admission -----

    /** Queues {@code task}; false when revoked or when the bounded queue is full. */
    boolean submit(Task task) {
        return enqueue(task, true);
    }

    /** Queues a descriptor-release task; false only when the worker no longer accepts work. */
    boolean submitCleanup(Task task) {
        return enqueue(task, false);
    }

    private synchronized boolean enqueue(Task task, boolean bounded) {
        Objects.requireNonNull(task, "task");
        if (!accepting || (bounded && queue.size() >= QUEUE_CAPACITY)) {
            return false;
        }
        queue.addLast(task);
        notifyAll();
        return true;
    }

    /** Withdraws a queued task that has not started; true when it will never run. */
    synchronized boolean withdraw(Task task) {
        return queue.remove(task);
    }

    /** Whether the caller is this grant's driver thread (where descriptors are confined). */
    boolean isWorkerThread() {
        return Thread.currentThread() == driver;
    }

    synchronized boolean isRestrictedForTesting() {
        return installed;
    }

    synchronized boolean isAcceptingForTesting() {
        return accepting;
    }

    /** The driver thread, on which grant operations and their completions run. */
    Thread threadForTesting() {
        return driver;
    }

    Thread restrictedThreadForTesting() {
        return restricted;
    }

    /**
     * Stops admission, rejects queued tasks, then runs {@code cleanup} on the driver after the
     * in-flight task, stops the restricted thread and waits for both threads. Idempotent: a later
     * call only waits. Called from the driver itself it does not wait.
     */
    void shutdown(Runnable cleanup) {
        Objects.requireNonNull(cleanup, "cleanup");
        List<Task> rejected;
        synchronized (this) {
            if (!stopping) {
                stopping = true;
                accepting = false;
                rejected = List.copyOf(queue);
                queue.clear();
                finalAction = cleanup;
                notifyAll();
            } else {
                rejected = List.of();
            }
        }
        for (Task task : rejected) {
            try {
                task.rejected();
            } catch (RuntimeException ignored) {
                // Rejection only reports a failure; it cannot keep the worker alive.
            }
        }
        if (!isWorkerThread()) {
            join(driver);
            join(restricted);
        }
    }

    private static void waitUninterruptibly(Object monitor) {
        try {
            monitor.wait();
        } catch (InterruptedException ignored) {
            // Only shutdown ends the loops; interruption never abandons custody or a native call.
        }
    }

    private static void join(Thread thread) {
        boolean interrupted = false;
        while (thread.isAlive()) {
            try {
                thread.join();
            } catch (InterruptedException interruption) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
