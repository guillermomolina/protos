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

import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Private bounded Argon2id computation substrate (I160, D245-C).
 *
 * <p>One instance belongs to one owning runtime host.
 * It has at most two active derivations and sixteen queued jobs.
 * Admission never waits for memory or an available worker.
 *
 * <p>Workers manipulate only owned byte arrays and inert results.
 * A Completion is a HOST-ONLY handoff callback: implementations must
 * enqueue Actor-domain publication, never execute guest code or
 * construct Protos guest objects from a worker.
 *
 * <p>Cancellation prevents publication of a precommit result.
 * Running Argon2 calculations may finish privately if interruption
 * cannot safely stop the provider.
 */
final class ProtosPasswordKdfExecutor implements AutoCloseable {

    static final int MAX_ACTIVE = 2;
    static final int MAX_QUEUED = 16;

    interface Ticket {
        void cancel();
    }

    @FunctionalInterface
    interface Completion {
        /**
         * Host-only handoff. The receiver takes ownership of the
         * result and must close it after Actor-domain publication
         * or discard.
         *
         * <p>On provider failure, result is null.
         */
        void completed(Result result);
    }

    /**
     * Independently owned inert result.
     *
     * <p>Never contains a guest object, activation or Closure.
     * Its secret buffers are cleared on close.
     */
    static final class Result implements AutoCloseable {

        private byte[] salt;
        private byte[] tag;
        private final boolean matches;
        private final boolean verified;
        private final boolean unavailable;

        private Result(
                byte[] salt,
                byte[] tag,
                boolean matches,
                boolean verified,
                boolean unavailable) {
            this.salt = salt;
            this.tag = tag;
            this.matches = matches;
            this.verified = verified;
            this.unavailable = unavailable;
        }

        static Result hashed(byte[] salt, byte[] tag) {
            return new Result(salt, tag, false, false, false);
        }

        static Result verified(boolean matches) {
            return new Result(null, null, matches, true, false);
        }

        static Result unavailable() {
            return new Result(null, null, false, false, true);
        }

        boolean isUnavailable() {
            return unavailable;
        }

        boolean isVerification() {
            return verified;
        }

        boolean matches() {
            return matches;
        }

        synchronized byte[] saltSnapshot() {
            if (salt == null) {
                throw new IllegalStateException("No salt result");
            }
            return Arrays.copyOf(salt, salt.length);
        }

        synchronized byte[] tagSnapshot() {
            if (tag == null) {
                throw new IllegalStateException("No tag result");
            }
            return Arrays.copyOf(tag, tag.length);
        }

        @Override
        public synchronized void close() {
            if (salt != null) {
                Arrays.fill(salt, (byte) 0);
                salt = null;
            }
            if (tag != null) {
                Arrays.fill(tag, (byte) 0);
                tag = null;
            }
        }
    }

    private final AtomicBoolean closed = new AtomicBoolean();
    private final Set<Job> liveJobs = ConcurrentHashMap.newKeySet();

    boolean isClosedForRuntime() {
        return closed.get();
    }
    private final ThreadPoolExecutor workers;

    // Package-private deterministic test seam. The ordinary production
    // constructor installs a no-op; this never becomes a guest facility.
    private final Runnable beforeDerive;

    ProtosPasswordKdfExecutor() {
        this(() -> {});
    }

    ProtosPasswordKdfExecutor(Runnable beforeDerive) {
        this.beforeDerive = Objects.requireNonNull(beforeDerive);
        AtomicInteger sequence = new AtomicInteger();

        workers = new ThreadPoolExecutor(
                MAX_ACTIVE,
                MAX_ACTIVE,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(MAX_QUEUED),
                command -> {
                    Thread thread = new Thread(
                            command,
                            "protos-password-kdf-"
                                    + sequence.incrementAndGet()
                    );
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy()
        );
    }

    /**
     * Nonblocking admission.
     *
     * <p>Inputs must already be strictly validated and normalized.
     * This method takes independent copies immediately.
     *
     * @param expectedTag null for hashing; a full tag for verification
     * @return a cancellation ticket, or null when admission is closed
     *         or capacity is exhausted
     */
    Ticket submit(
            byte[] normalizedPassword,
            byte[] salt,
            byte[] expectedTag,
            Completion completion) {

        Objects.requireNonNull(normalizedPassword);
        Objects.requireNonNull(salt);
        Objects.requireNonNull(completion);

        if (normalizedPassword.length
                    > ProtosPasswordArgon2Facility.MAX_PASSWORD_BYTES
                || salt.length
                    != ProtosPasswordArgon2Facility.SALT_BYTES
                || (expectedTag != null
                    && expectedTag.length
                        != ProtosPasswordArgon2Facility.TAG_BYTES)) {
            throw new IllegalArgumentException("Invalid KDF input");
        }

        if (closed.get()) {
            return null;
        }

        byte[] passwordCopy =
                Arrays.copyOf(normalizedPassword, normalizedPassword.length);
        byte[] saltCopy = Arrays.copyOf(salt, salt.length);
        byte[] tagCopy = expectedTag == null
                ? null
                : Arrays.copyOf(expectedTag, expectedTag.length);

        Job job = new Job(passwordCopy, saltCopy, tagCopy, completion);
        liveJobs.add(job);

        if (closed.get()) {
            job.cancel();
            liveJobs.remove(job);
            return null;
        }

        try {
            workers.execute(job);
        } catch (RejectedExecutionException rejected) {
            job.cancel();
            liveJobs.remove(job);
            return null;
        }

        if (closed.get()) {
            // A host retirement racing with queue admission must
            // not answer a success ticket for a cancelled job.
            job.cancel();
            return null;
        }

        return job;
    }

    private final class Job implements Runnable, Ticket {

        private byte[] password;
        private byte[] salt;
        private byte[] expectedTag;
        // Cleared as soon as cancellation withdraws publication
        // authority, even if a provider calculation is still running.
        private Completion completion;

        private boolean running;
        private boolean finished;
        private boolean cancelled;

        Job(
                byte[] password,
                byte[] salt,
                byte[] expectedTag,
                Completion completion) {
            this.password = password;
            this.salt = salt;
            this.expectedTag = expectedTag;
            this.completion = completion;
        }

        private void clearInputs() {
            if (password != null) {
                Arrays.fill(password, (byte) 0);
                password = null;
            }
            if (salt != null) {
                Arrays.fill(salt, (byte) 0);
                salt = null;
            }
            if (expectedTag != null) {
                Arrays.fill(expectedTag, (byte) 0);
                expectedTag = null;
            }
        }

        @Override
        public void cancel() {
            boolean remove;

            synchronized (this) {
                if (finished) {
                    return;
                }

                cancelled = true;

                // A cancelled computation must not retain its
                // Actor-domain publication handle until Argon2 ends.
                completion = null;

                remove = !running;

                if (remove) {
                    finished = true;
                    clearInputs();
                }
            }

            if (remove) {
                workers.remove(this);
                liveJobs.remove(this);
            }
        }

        @Override
        public void run() {
            synchronized (this) {
                if (finished || cancelled || closed.get()) {
                    finished = true;
                    completion = null;
                    clearInputs();
                    liveJobs.remove(this);
                    return;
                }
                running = true;
            }

            Result outcome = null;

            try {
                beforeDerive.run();

                // Cancellation may arrive while the worker is admitted
                // but before expensive provider work begins.
                boolean mayCompute;
                synchronized (this) {
                    mayCompute = !cancelled && !closed.get();
                }

                if (!mayCompute) {
                    // No Argon2 memory allocation after observed
                    // cancellation or host retirement.
                } else if (expectedTag == null) {
                    byte[] derived =
                            ProtosPasswordArgon2Facility.derive(
                                    password, salt
                            );

                    outcome = Result.hashed(
                            Arrays.copyOf(salt, salt.length),
                            derived
                    );
                } else {
                    byte[] derived =
                            ProtosPasswordArgon2Facility.derive(
                                    password, salt
                            );

                    boolean matches;
                    try {
                        matches = MessageDigest.isEqual(
                                derived,
                                expectedTag
                        );
                    } finally {
                        Arrays.fill(derived, (byte) 0);
                    }

                    outcome = Result.verified(matches);
                }
            } catch (RuntimeException | LinkageError | OutOfMemoryError failure) {
                // A memory-hard provider can fail allocation even after
                // admission. Settle as unavailable rather than leaving
                // the originating Future indefinitely pending.
                // Never expose exception text or stack details.
                outcome = Result.unavailable();
            } finally {
                synchronized (this) {
                    clearInputs();
                    running = false;
                }
            }

            Completion publication;

            synchronized (this) {
                publication =
                        !cancelled && !closed.get()
                                ? completion
                                : null;

                // Transfer the callback to this final handoff extent.
                // The Job itself no longer retains that reference.
                completion = null;
                finished = true;
            }

            try {
                if (publication != null) {
                    // Host-only handoff. The Process ticket and owning
                    // Future independently reject late cancellation.
                    // No guest code or guest object is created here.
                    publication.completed(outcome);
                    outcome = null;
                }
            } finally {
                if (outcome != null) {
                    outcome.close();
                }
                liveJobs.remove(this);
            }
        }
    }

    /**
     * Deterministic worker-retirement observation for package-local
     * runtime tests; never called by guest code or normal admission.
     */
    boolean awaitTerminationForTesting(
            long timeout, TimeUnit unit) throws InterruptedException {
        return workers.awaitTermination(timeout, unit);
    }

    /**
     * Retire this host's KDF admission without running guest code.
     * Running computations may finish privately, but cannot publish
     * after the close flag becomes visible.
     */
    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }

        for (Job job : liveJobs) {
            job.cancel();
        }

        // Never interrupt a running Argon2id provider calculation.
        // Every admitted job has already received cancellation above.
        // Queued jobs release their private arrays without running.
        workers.shutdown();

        for (Runnable pending : workers.getQueue()) {
            if (pending instanceof Job job) {
                job.cancel();
            }
        }

        workers.getQueue().clear();
    }
}
