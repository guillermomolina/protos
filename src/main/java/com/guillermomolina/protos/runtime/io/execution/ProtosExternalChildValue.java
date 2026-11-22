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

package com.guillermomolina.protos.runtime;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

/**
 * Host representation of one external OS child created through an execution grant
 * (EXTERNAL_PROCESS.md §§5-8).
 *
 * <p>The child's identity is this object; no native process identifier is retained as identity
 * or authority. Its public selectors are source-backed in ChildProcess.protos and reach this
 * object only through the private execution bridge. The owning Protos Process holds the custody
 * reference independently of guest reachability until every obligation is met: the native
 * supervisor verified the child reaped (a supervision failure or a termination request is never
 * such evidence), the release of each of the three native endpoint handles was confirmed (an
 * uncertain or still pending release keeps the obligation), and every host transfer resource of
 * the child (pumps and Pipes) retired. Exit of the child never implies EOF or release of its
 * endpoints (a descendant may keep a writer open), so a child that exited with unread output
 * stays in custody and its endpoints keep delivering the remaining bytes until they are closed by
 * the guest or by the host-only cleanup of the Process.
 *
 * <p>A child is an Actor-local capability: it belongs to the Actor execution domain it was
 * published into, fixed at construction, and is operable only from that domain. Delegation of the
 * execution grant never delegates the children another Actor created.
 *
 * <p>The terminal outcome is produced once by the native supervisor and retained here, so every
 * wait observes the same content without consuming native state again.
 */
@ExportLibrary(InteropLibrary.class)
public final class ProtosExternalChildValue implements ProtosRepresentedValue {

    /** Terminal category established by the native supervisor. */
    public enum TerminalCategory {
        /** Verified normal exit; the exit code is meaningful. */
        EXITED,
        /** Verified signal termination. */
        SIGNALED,
        /** The backend cannot reliably distinguish normal exit from signal termination. */
        UNKNOWN
    }

    /**
     * Retained terminal outcome. A numeric code alone never selects a category: the backend
     * reports the category from verifiable native evidence or reports {@link
     * TerminalCategory#UNKNOWN}.
     */
    public record TerminalOutcome(TerminalCategory category, long exitCode) {
        public TerminalOutcome {
            Objects.requireNonNull(category, "category");
        }

        public static TerminalOutcome exited(long exitCode) {
            return new TerminalOutcome(TerminalCategory.EXITED, exitCode);
        }

        public static TerminalOutcome signaled() {
            return new TerminalOutcome(TerminalCategory.SIGNALED, 0);
        }

        public static TerminalOutcome unknown() {
            return new TerminalOutcome(TerminalCategory.UNKNOWN, 0);
        }
    }

    /**
     * Host-only handle of one launched native child. Implementations never touch guest objects.
     */
    public interface NativeChild {
        /**
         * Non-blocking byte transport of {@code stream}: STDIN accepts writes, STDOUT and STDERR
         * reads. Its completions carry host data only and never wait for the child.
         */
        ProtosByteIoFlow.Backend lane(Stream stream);

        /**
         * Releases the native handle of {@code stream}. Completes with true once the release is
         * confirmed, false when its outcome is uncertain (for example a failed close), in which
         * case the handle is never closed again. With {@code discard} (host-only cleanup) bytes
         * not yet handed to the child are abandoned; otherwise every accepted byte is delivered
         * first. A later call with {@code discard} may escalate an ordinary release in progress.
         */
        CompletionStage<Boolean> release(Stream stream, boolean discard);

        /**
         * Completes normally exactly once, after the single native supervisor established that
         * the child was reaped or equivalently released. Exceptional completion reports a
         * supervision failure: it never establishes reaping.
         */
        CompletionStage<TerminalOutcome> termination();

        /** Completes once every host transfer resource of this child (pumps, Pipes) retired. */
        CompletionStage<Void> transportRetired();

        /** Submits a best-effort termination request; never waits for exit. */
        void requestTermination() throws IOException;

        /**
         * Host-only escalation used when the embedding host must reclaim the child. A submitted
         * request never establishes that the child terminated.
         */
        void requestForcedTermination() throws IOException;
    }

    /** Which standard endpoint of the child an {@link Endpoint} represents. */
    public enum Stream {
        STDIN,
        STDOUT,
        STDERR
    }

    /** Ownership of one native endpoint handle on behalf of the Process. */
    private enum HandleState {
        HELD,
        RELEASING,
        RELEASED,
        /** A release attempt failed: the handle may or may not be closed; custody is retained. */
        UNCERTAIN
    }

    private final ProtosPrelude.ExecutionProtocols protocols;
    private final ProtosProcessRuntime owner;
    /** Actor domain the guest capability is published into; null for a never-published child. */
    private final ProtosActorExecutionDomain ownerDomain;
    private final NativeChild nativeChild;
    private final Executor ioExecutor;
    private final Runnable admissionRelease;
    private Endpoint stdin;
    private Endpoint stdout;
    private Endpoint stderr;
    /** Outcome retained for waiters; non-null only after a verified reap. */
    private TerminalOutcome outcome;
    /** Supervision failure retained for waiters; never evidence of reaping. */
    private Throwable supervisionFailure;
    private boolean hostOnlyCustody;
    private boolean hostCloseReclaimed;
    private final java.util.EnumMap<Stream, HandleState> handles =
            new java.util.EnumMap<>(Stream.class);
    private final java.util.EnumMap<Stream, CompletionStage<Boolean>> releases =
            new java.util.EnumMap<>(Stream.class);
    private boolean custodyReleased;
    private boolean transportRetired;
    private boolean admissionReleased;

    ProtosExternalChildValue(
            ProtosPrelude.ExecutionProtocols protocols,
            ProtosProcessRuntime owner,
            ProtosActorExecutionDomain ownerDomain,
            NativeChild nativeChild,
            Executor ioExecutor,
            Runnable admissionRelease) {
        this.protocols = Objects.requireNonNull(protocols, "protocols");
        this.owner = Objects.requireNonNull(owner, "owner");
        this.ownerDomain = ownerDomain;
        this.nativeChild = Objects.requireNonNull(nativeChild, "nativeChild");
        this.ioExecutor = Objects.requireNonNull(ioExecutor, "ioExecutor");
        this.admissionRelease = admissionRelease == null ? () -> {} : admissionRelease;
        for (Stream stream : Stream.values()) {
            handles.put(stream, HandleState.HELD);
        }
        nativeChild.termination().whenComplete(this::supervisorFinished);
        nativeChild.transportRetired().whenComplete(this::transportFinished);
    }

    public ProtosProcessRuntime ownerForRuntime() {
        return owner;
    }

    /**
     * Side-effect-free owning-domain test: true only from the Actor domain this child was
     * published into. A child with no owning domain is operable from none.
     */
    public boolean ownedByForRuntime(ProtosActivation activation) {
        return ownerDomain != null && activation != null && activation.executionDomain() == ownerDomain;
    }

    /** The native child this custody wrapper owns; host-only. */
    public NativeChild nativeChildForRuntime() {
        return nativeChild;
    }

    /** Host executor of the creating grant, used for non-I/O host work off the Actor turn. */
    public Executor ioExecutorForRuntime() {
        return ioExecutor;
    }

    /** Guest operations are admitted only while the owner runs and custody is not host-only. */
    public boolean guestOperableForRuntime() {
        // The Process monitor is never taken while holding this child's monitor.
        if (owner.lifecycleState() != ProtosProcessRuntime.LifecycleState.RUNNING) {
            return false;
        }
        synchronized (this) {
            return !hostOnlyCustody;
        }
    }

    /** Retained terminal outcome, or null until the supervisor verified the reap. */
    public synchronized TerminalOutcome outcomeForRuntime() {
        return outcome;
    }

    /** True once the native supervisor established that the child was reaped. */
    public synchronized boolean reapedForRuntime() {
        return outcome != null;
    }

    /** True when the supervisor failed: reaping is then unknown and custody is retained. */
    public synchronized boolean supervisionFailedForRuntime() {
        return supervisionFailure != null;
    }

    /**
     * True once the child was reaped, all its native endpoint handles were released and its
     * transport retired, so the Process no longer owes it any supervision or release.
     */
    public synchronized boolean custodyReleasedForRuntime() {
        return custodyReleased;
    }

    /** Whether the native handle of {@code stream} is still owned on behalf of the Process. */
    public synchronized boolean endpointHandleHeldForRuntime(Stream stream) {
        return handles.get(stream) != HandleState.RELEASED;
    }

    /** True when a release attempt of {@code stream} had an uncertain outcome. */
    public synchronized boolean endpointReleaseUncertainForRuntime(Stream stream) {
        return handles.get(stream) == HandleState.UNCERTAIN;
    }

    /**
     * True once a host close reclamation established reaping, release of every handle and
     * retirement of the transport.
     */
    public synchronized boolean hostCloseReclaimedForRuntime() {
        return hostCloseReclaimed;
    }

    /** Native termination stage; completion callbacks must only carry host data. */
    public CompletionStage<TerminalOutcome> terminationForRuntime() {
        return nativeChild.termination();
    }

    /**
     * Best-effort termination request. A request after the verified reap is a no-op.
     *
     * @return false when the request could not be submitted
     */
    @TruffleBoundary
    public boolean requestTerminationForRuntime() {
        synchronized (this) {
            if (outcome != null) {
                return true;
            }
        }
        try {
            nativeChild.requestTermination();
            return true;
        } catch (IOException | RuntimeException failure) {
            return false;
        }
    }

    /**
     * Returns the single logical endpoint for {@code stream}, materializing it on first use. The
     * caller has authenticated {@code activation} as this child's owning domain, so every
     * endpoint lives in that domain.
     */
    public synchronized Endpoint endpointForRuntime(ProtosActivation activation, Stream stream) {
        Objects.requireNonNull(activation, "activation");
        Endpoint existing =
                switch (stream) {
                    case STDIN -> stdin;
                    case STDOUT -> stdout;
                    case STDERR -> stderr;
                };
        if (existing != null) {
            return existing;
        }
        Endpoint created = new Endpoint(this, stream, activation);
        switch (stream) {
            case STDIN -> stdin = created;
            case STDOUT -> stdout = created;
            case STDERR -> stderr = created;
        }
        return created;
    }

    /**
     * Process-termination cutover: revokes guest access, submits a best-effort termination when
     * still live and releases every native endpoint handle still owned on behalf of the Process,
     * abandoning stdin bytes not yet delivered, including those of a child that already exited.
     * Tracking continues until the supervisor reports reaping. Never waits for the OS and runs
     * no guest code.
     */
    @TruffleBoundary
    public void enterHostOnlyCustodyForRuntime() {
        boolean live;
        synchronized (this) {
            if (hostOnlyCustody) {
                return;
            }
            hostOnlyCustody = true;
            live = outcome == null;
        }
        if (live) {
            requestTerminationForRuntime();
        }
        for (Stream stream : Stream.values()) {
            releaseHandle(stream, true);
        }
    }

    /**
     * Host-only escalation used when the child could not be published or registered: guest access
     * never existed, every handle is released and a forced termination is requested at once. The
     * request establishes nothing: custody lasts until the supervisor verifies the reap.
     *
     * @return false when the forced request could not be submitted
     */
    @TruffleBoundary
    public boolean abandonUnpublishedForRuntime() {
        enterHostOnlyCustodyForRuntime();
        try {
            nativeChild.requestForcedTermination();
            return true;
        } catch (IOException | RuntimeException failure) {
            return false;
        }
    }

    /**
     * Host close reclamation: some hosts (a Truffle Context) refuse to close while a child they
     * created is alive. Releases every handle still owned, waits at most {@code graceMillis}
     * after the ordinary request, then escalates to forced termination and waits once more, and
     * finally waits at most {@code graceMillis} for the pending handle releases and at most
     * {@code graceMillis} for the retirement of the transport. Success is reported only when the
     * child left Process custody: the supervisor verified the reap, every handle release was
     * confirmed and every pump and Pipe retired. A termination request, a forced request, a
     * supervisor that completed exceptionally, or a pump still alive establishes nothing, so such
     * a child keeps its cleanup obligation and a later host close path retries it. A reclaimed
     * child is never reclaimed again and no reap is ever repeated. Runs on the host closing
     * thread, never in an Actor turn and never with a Process monitor held.
     *
     * @return true when the native child is known to be reaped, all its handles released and its
     *     transport retired
     */
    @TruffleBoundary
    public boolean reclaimForHostCloseRuntime(long graceMillis) {
        synchronized (this) {
            if (hostCloseReclaimed) {
                return true;
            }
        }
        enterHostOnlyCustodyForRuntime();
        java.util.concurrent.CompletableFuture<TerminalOutcome> stage =
                nativeChild.termination().toCompletableFuture();
        TerminalOutcome reaped = awaitReap(stage, graceMillis);
        if (reaped == null && !stage.isCompletedExceptionally()) {
            try {
                nativeChild.requestForcedTermination();
            } catch (IOException | RuntimeException failure) {
                return false;
            }
            reaped = awaitReap(stage, graceMillis);
        }
        if (reaped == null) {
            return false;
        }
        // The waiter may return before the stage's own callback ran; recording is idempotent.
        supervisorFinished(reaped, null);
        if (!awaitReleases(graceMillis) || !awaitTransport(graceMillis)) {
            return false;
        }
        synchronized (this) {
            if (!custodyReleased) {
                return false;
            }
            hostCloseReclaimed = true;
        }
        return true;
    }

    /**
     * Waits at most {@code millis} for the transport to retire. A normal completion is recorded
     * at once, since the waiter may return before the stage's own callback ran.
     */
    private boolean awaitTransport(long millis) {
        try {
            nativeChild.transportRetired()
                    .toCompletableFuture()
                    .get(millis, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        } catch (java.util.concurrent.ExecutionException
                | java.util.concurrent.CancellationException
                | java.util.concurrent.TimeoutException notRetired) {
            return false;
        }
        transportFinished(null, null);
        return true;
    }

    /**
     * The verified outcome, or null when the supervisor did not establish the reap in time or
     * failed. A failed supervisor is never treated as a reap.
     */
    private static TerminalOutcome awaitReap(
            java.util.concurrent.CompletableFuture<TerminalOutcome> stage, long millis) {
        try {
            return stage.get(millis, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return null;
        } catch (java.util.concurrent.ExecutionException
                | java.util.concurrent.CancellationException supervisionFailed) {
            return null;
        } catch (java.util.concurrent.TimeoutException notYet) {
            return null;
        }
    }

    private boolean awaitReleases(long millis) {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(millis);
        for (Stream stream : Stream.values()) {
            CompletionStage<Boolean> release;
            synchronized (this) {
                release = releases.get(stream);
            }
            if (release == null) {
                continue;
            }
            long remaining = Math.max(0, deadline - System.nanoTime());
            try {
                release.toCompletableFuture()
                        .get(remaining, java.util.concurrent.TimeUnit.NANOSECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return false;
            } catch (java.util.concurrent.ExecutionException
                    | java.util.concurrent.CancellationException
                    | java.util.concurrent.TimeoutException notConfirmed) {
                return false;
            }
        }
        synchronized (this) {
            for (HandleState state : handles.values()) {
                if (state != HandleState.RELEASED) {
                    return false;
                }
            }
            return true;
        }
    }

    /**
     * Records the single supervisor result. Only a normal completion is a verified reap; an
     * exceptional one is retained for waiters as a supervision failure and keeps custody. The
     * child leaves Process custody only once its endpoint handles were also released and its
     * transport retired.
     */
    private void supervisorFinished(TerminalOutcome observed, Throwable failure) {
        synchronized (this) {
            if (outcome != null) {
                return;
            }
            if (observed != null && failure == null) {
                outcome = observed;
            } else {
                supervisionFailure = failure != null ? failure : new IOException("no outcome");
                return;
            }
        }
        leaveCustodyIfDischarged();
    }

    /**
     * Records the retirement of every pump and Pipe. Only a normal completion is retirement; an
     * exceptional one leaves the transport obligation, and with it custody, in place. The
     * transport may be the last outstanding obligation, so custody is re-evaluated.
     */
    private void transportFinished(Void ignored, Throwable failure) {
        if (failure != null) {
            return;
        }
        synchronized (this) {
            if (transportRetired) {
                return;
            }
            transportRetired = true;
        }
        leaveCustodyIfDischarged();
    }

    /**
     * Single owner of the release of one native handle. A confirmed release is final; an
     * uncertain one is final too and is never retried, since the descriptor might have been
     * reused. {@code discard} escalates an ordinary stdin release in progress.
     */
    private CompletionStage<Boolean> releaseHandle(Stream stream, boolean discard) {
        java.util.concurrent.CompletableFuture<Boolean> recorded;
        synchronized (this) {
            if (handles.get(stream) != HandleState.HELD) {
                CompletionStage<Boolean> existing = releases.get(stream);
                if (discard
                        && stream == Stream.STDIN
                        && handles.get(stream) == HandleState.RELEASING) {
                    escalateQuietly(stream);
                }
                return existing;
            }
            handles.put(stream, HandleState.RELEASING);
            recorded = new java.util.concurrent.CompletableFuture<>();
            releases.put(stream, recorded);
        }
        CompletionStage<Boolean> started;
        try {
            started = nativeChild.release(stream, discard);
        } catch (RuntimeException uncertain) {
            started = java.util.concurrent.CompletableFuture.completedFuture(false);
        }
        started.whenComplete(
                (confirmed, failure) -> {
                    boolean released = failure == null && Boolean.TRUE.equals(confirmed);
                    synchronized (this) {
                        handles.put(stream, released ? HandleState.RELEASED : HandleState.UNCERTAIN);
                    }
                    // Custody is left before waiters observe the release, so a reclaiming host
                    // that saw every release confirmed also sees the registry updated.
                    if (released) {
                        leaveCustodyIfDischarged();
                    }
                    recorded.complete(released);
                });
        return recorded;
    }

    /** Called with this monitor held; the native call never re-enters this child. */
    private void escalateQuietly(Stream stream) {
        try {
            nativeChild.release(stream, true);
        } catch (RuntimeException ignored) {
            // The release in progress keeps the obligation.
        }
    }

    /**
     * Leaves Process custody once, when every obligation is discharged: a verified reap, the
     * confirmed release of all three handles (so no release is still in progress) and the
     * retirement of the transport. Each of those events calls this method, so whichever comes
     * last discharges the child.
     */
    private void leaveCustodyIfDischarged() {
        synchronized (this) {
            if (custodyReleased || outcome == null || !transportRetired) {
                return;
            }
            for (HandleState state : handles.values()) {
                if (state != HandleState.RELEASED) {
                    return;
                }
            }
            custodyReleased = true;
        }
        // The Process monitor is never taken while holding this child's monitor.
        owner.externalChildReleasedForRuntime(this);
        releaseAdmissionIfQuiescent();
    }

    /** Returns the child's admission slot once custody and every pump and Pipe retired. */
    private void releaseAdmissionIfQuiescent() {
        synchronized (this) {
            if (admissionReleased || !custodyReleased || !transportRetired) {
                return;
            }
            admissionReleased = true;
        }
        admissionRelease.run();
    }

    @Override
    public Object representedDelegationParent(ProtosPrelude ignored) {
        return protocols.childProcess();
    }

    @ExportMessage
    String toDisplayString(@SuppressWarnings("unused") boolean allowSideEffects) {
        return "ChildProcess";
    }

    /**
     * Materializes one immutable ExitStatus value for a verified outcome. Called only in the
     * observing Actor domain; an UNKNOWN outcome has no ExitStatus.
     */
    public Status statusForRuntime(TerminalOutcome verified) {
        if (verified.category() == TerminalCategory.UNKNOWN) {
            throw new IllegalArgumentException("unknown terminal category has no ExitStatus");
        }
        return new Status(protocols.exitStatus(), verified);
    }

    /** Immutable ExitStatus content; only host-materialized statuses answer its selectors. */
    @ExportLibrary(InteropLibrary.class)
    public static final class Status implements ProtosRepresentedValue {
        private final ProtosObjectValue prototype;
        private final TerminalOutcome outcome;

        private Status(ProtosObjectValue prototype, TerminalOutcome outcome) {
            this.prototype = Objects.requireNonNull(prototype, "prototype");
            this.outcome = outcome;
        }

        public boolean exited() {
            return outcome.category() == TerminalCategory.EXITED;
        }

        public boolean signaled() {
            return outcome.category() == TerminalCategory.SIGNALED;
        }

        /**
         * The inert terminal data of this status: the only part that crosses an Actor or
         * parallel boundary. It carries no process identity or authority.
         */
        public TerminalOutcome outcomeForRuntime() {
            return outcome;
        }

        /**
         * Destination stage of an ExitStatus transfer: one fresh, authority-free status bound to
         * the destination Prelude's ExitStatus protocol.
         *
         * @throws IllegalArgumentException for an unverified outcome
         * @throws IllegalStateException when the Prelude does not retain the execution protocols
         */
        public static Status materializeForRuntime(ProtosPrelude prelude, TerminalOutcome outcome) {
            Objects.requireNonNull(outcome, "outcome");
            if (outcome.category() == TerminalCategory.UNKNOWN) {
                throw new IllegalArgumentException("unknown terminal category has no ExitStatus");
            }
            return new Status(prelude.executionProtocolsForRuntime().exitStatus(), outcome);
        }

        /** The Protos Integer exit code of a normal exit, or Protos null for a signal. */
        public Object exitCode() {
            return exited()
                    ? ProtosNumericValueSupport.integer(outcome.exitCode())
                    : ProtosNullValue.INSTANCE;
        }

        @Override
        public Object representedDelegationParent(ProtosPrelude ignored) {
            return prototype;
        }

        @ExportMessage
        String toDisplayString(@SuppressWarnings("unused") boolean allowSideEffects) {
            return "ExitStatus";
        }
    }

    /**
     * One logical standard endpoint of a child. Byte transfer, ordering, partial reads, EOF,
     * cancellation and Futures are delegated to one {@link ProtosByteIoFlow} lane; the native
     * pipe is pulled only on demand, so host buffering is bounded and backpressure reaches the
     * child. Closing releases this endpoint only and never terminates the child.
     */
    public static final class Endpoint extends ProtosObjectValue {
        private final ProtosExternalChildValue child;
        private final Stream stream;
        private final ProtosIoLifecycle lifecycle;
        private final ProtosByteIoFlow flow;

        private Endpoint(ProtosExternalChildValue child, Stream stream, ProtosActivation activation) {
            super(stream == Stream.STDIN ? child.protocols.childStdin() : child.protocols.childOutput());
            this.child = child;
            this.stream = stream;
            ProtosPrelude prelude = activation.prelude().orElseThrow();
            this.lifecycle =
                    new ProtosIoLifecycle(
                            this,
                            prelude.futurePrototype(),
                            activation.executionDomain(),
                            this::release);
            this.flow =
                    new ProtosByteIoFlow(
                            this,
                            prelude.bytesPrototypeForRuntime(),
                            activation,
                            stream == Stream.STDIN ? new WriteLane() : new ReadLane(),
                            lifecycle);
        }

        public ProtosExternalChildValue childForRuntime() {
            return child;
        }

        public boolean readableForRuntime() {
            return stream != Stream.STDIN;
        }

        public ProtosFutureValue readForRuntime(ProtosActivation activation, Object maxBytes) {
            return flow.read(activation, maxBytes);
        }

        public ProtosFutureValue writeForRuntime(ProtosActivation activation, Object bytes) {
            return flow.write(activation, bytes);
        }

        public ProtosFutureValue closeForRuntime(ProtosActivation activation) {
            return lifecycle.close(activation);
        }

        /**
         * Guest close. stdin first hands every accepted byte to the child and completes with the
         * confirmation of its native close; an uncertain release fails the close with IOError.
         * stdout and stderr release the logical reader: once the request rejects later reads the
         * close succeeds, while the native close stays with the pump, which may remain blocked
         * while any native writer keeps the pipe open. In both cases only a confirmed native
         * release discharges the Process obligation; until then the handle stays in custody.
         */
        @TruffleBoundary
        private void release(ProtosIoLifecycle.ReleaseCompletion completion) {
            CompletionStage<Boolean> physical = child.releaseHandle(stream, false);
            if (stream != Stream.STDIN) {
                completion.succeeded();
                return;
            }
            physical.whenComplete(
                            (confirmed, failure) -> {
                                if (failure == null && Boolean.TRUE.equals(confirmed)) {
                                    completion.succeeded();
                                } else {
                                    completion.failedWithIoErrorForRuntime();
                                }
                            });
        }

        /**
         * stdout/stderr lane: one bounded transfer per guest read, completed by the host poller
         * from the child's bounded Pipe. No worker blocks on the child. A cancelled read leaves
         * its bytes in the Pipe for the next read, so no committed byte is lost.
         */
        private final class ReadLane implements ProtosByteIoFlow.Backend {
            @Override
            public ProtosByteIoFlow.Cancellation read(
                    int maxBytes, ProtosByteIoFlow.ReadCompletion completion) {
                if (!child.endpointHandleHeldForRuntime(stream)) {
                    completion.rejected(ProtosCoreErrors.StandardError.I_O_LIFECYCLE_ERROR);
                    return () -> {};
                }
                return child.nativeChild.lane(stream).read(maxBytes, completion);
            }

            @Override
            public ProtosByteIoFlow.Cancellation write(
                    byte[] bytes, ProtosByteIoFlow.WriteCompletion completion) {
                throw new IllegalStateException("write reached a child output endpoint");
            }
        }

        /**
         * stdin lane: one transfer per guest write into the child's bounded Pipe, with real
         * backpressure; a partially accepted write is never turned into a no-effect cancellation.
         */
        private final class WriteLane implements ProtosByteIoFlow.Backend {
            @Override
            public ProtosByteIoFlow.Cancellation read(
                    int maxBytes, ProtosByteIoFlow.ReadCompletion completion) {
                throw new IllegalStateException("read reached a child stdin endpoint");
            }

            @Override
            public ProtosByteIoFlow.Cancellation write(
                    byte[] bytes, ProtosByteIoFlow.WriteCompletion completion) {
                if (!child.endpointHandleHeldForRuntime(stream)) {
                    completion.rejected(ProtosCoreErrors.StandardError.I_O_LIFECYCLE_ERROR);
                    return () -> {};
                }
                return child.nativeChild.lane(stream).write(bytes, completion);
            }
        }
    }
}
