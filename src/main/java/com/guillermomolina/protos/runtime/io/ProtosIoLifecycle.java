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

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Reusable whole-resource Closable lifecycle and admission/commitment cutover machinery. */
public final class ProtosIoLifecycle {
    public enum State { OPEN, CLOSING, CLOSED_SUCCESS, CLOSED_FAILED }

    @FunctionalInterface public interface ReleaseStarter { void start(ReleaseCompletion completion); }
    public interface ReleaseCompletion {
        void succeeded();
        void failed(ProtosObjectValue error);

        /**
         * I125-B: release failed with the portable IOError, which this lifecycle constructs in its
         * owner's execution domain rather than on the reporting thread.
         */
        default void failedWithIoErrorForRuntime() {
            throw new UnsupportedOperationException("lifecycle-constructed release failure is not supported");
        }
    }

    private final Object receiver;
    private final ProtosObjectValue futurePrototype;
    private final ProtosActorExecutionDomain domain;
    private final ReleaseStarter releaseStarter;
    private final Set<ProtosIoOperation> operations=new LinkedHashSet<>();
    private final List<ProtosFutureValue> closeFollowers=new ArrayList<>();
    private State state=State.OPEN;
    private ProtosObjectValue closeError;
    private ProtosActivation closeActivation;
    private boolean releaseStarted;
    /**
     * I125-B opt-in (TCP listener): every release outcome not reported synchronously inside the
     * owner-domain close's own release start is published in the owner domain.
     */
    private final boolean ownerDomainPublication;
    /** Caller of an in-progress owner-domain close release start; delimits that call only. */
    private Thread synchronousReleaseCaller;
    private boolean terminationCleanupReserved;
    private ProtosIoReleaseExecution releaseExecution;

    public ProtosIoLifecycle(Object receiver, ProtosObjectValue futurePrototype,
            ProtosActorExecutionDomain domain, ReleaseStarter releaseStarter) {
        this(receiver, futurePrototype, domain, releaseStarter, false);
    }

    /** I125-B: a lifecycle whose deferred release outcomes are published in its owner domain. */
    public static ProtosIoLifecycle withOwnerDomainPublicationForRuntime(Object receiver,
            ProtosObjectValue futurePrototype, ProtosActorExecutionDomain domain, ReleaseStarter releaseStarter) {
        return new ProtosIoLifecycle(receiver, futurePrototype, domain, releaseStarter, true);
    }

    private ProtosIoLifecycle(Object receiver, ProtosObjectValue futurePrototype,
            ProtosActorExecutionDomain domain, ReleaseStarter releaseStarter, boolean ownerDomainPublication) {
        this.ownerDomainPublication=ownerDomainPublication;
        this.receiver=Objects.requireNonNull(receiver,"receiver");
        this.futurePrototype=Objects.requireNonNull(futurePrototype,"futurePrototype");
        this.domain=Objects.requireNonNull(domain,"domain");
        this.releaseStarter=Objects.requireNonNull(releaseStarter,"releaseStarter");
    }

    public synchronized State state() { return state; }
    synchronized boolean isOpenLocked() { return state == State.OPEN; }

    /** Admits ordinary resource work, or returns a fresh lifecycle-failed Future if close already cut over. */
    public ProtosIoOperation beginOperation(ProtosActivation origin) {
        Objects.requireNonNull(origin,"origin");
        if (origin.executionDomain()!=domain) throw new IllegalArgumentException("I/O operation belongs to another Actor domain");
        ProtosFutureValue future=new ProtosFutureValue(futurePrototype,domain);
        ProtosIoOperation operation;
        synchronized(this) {
            if (state != State.OPEN) {
                future.fail(ProtosCoreErrors.newOccurrence(origin,ProtosCoreErrors.StandardError.I_O_LIFECYCLE_ERROR));
                return newRejectedOperation(origin,future);
            }
            operation=new ProtosIoOperation(this,origin,future);
            operations.add(operation);
        }
        return operation;
    }

    /**
     * I125-B admission for an already-authorized delegated TcpAcceptor accept (D217). The
     * operation and its Future belong to the accepting Actor, which must be hosted by the exact
     * Process incarnation hosting this lifecycle's owner (the owner domain itself crosses no Actor
     * boundary); every other rule is the ordinary one.
     */
    public ProtosIoOperation beginDelegatedTcpAcceptForRuntime(ProtosActivation origin) {
        Objects.requireNonNull(origin,"origin");
        ProtosActorExecutionDomain originDomain=origin.executionDomain();
        if (originDomain!=domain) {
            ProtosProcessRuntime acceptingProcess=hostingProcessForRuntime(originDomain).orElse(null);
            if (acceptingProcess==null || acceptingProcess!=hostingProcessForRuntime(domain).orElse(null)) {
                throw new IllegalArgumentException("delegated TCP accept requires the listener's exact Process");
            }
        }
        ProtosObjectValue originFuturePrototype=origin.prelude()
                .orElseThrow(() -> new IllegalStateException("delegated TCP accept requires an owning Core prelude"))
                .futurePrototype();
        ProtosFutureValue future=new ProtosFutureValue(originFuturePrototype,originDomain);
        ProtosIoOperation operation;
        synchronized(this) {
            if (state != State.OPEN) {
                future.fail(ProtosCoreErrors.newOccurrence(origin,ProtosCoreErrors.StandardError.I_O_LIFECYCLE_ERROR));
                return newRejectedOperation(origin,future);
            }
            operation=new ProtosIoOperation(this,origin,future,true);
            operations.add(operation);
        }
        return operation;
    }

    /** Exact hosting Process incarnation of the Actor bound to a domain; never a guest identifier. */
    public static java.util.Optional<ProtosProcessRuntime> hostingProcessForRuntime(
            ProtosActorExecutionDomain executionDomain) {
        Objects.requireNonNull(executionDomain,"executionDomain");
        return executionDomain.currentActorForRuntime().flatMap(ProtosActor::processForRuntime);
    }

    ProtosActorExecutionDomain ownerDomainForRuntime() { return domain; }

    private ProtosIoOperation newRejectedOperation(ProtosActivation origin, ProtosFutureValue future) {
        ProtosIoOperation operation=new ProtosIoOperation(this,origin,future);
        synchronized(this) { operation.closeCutoverLocked(); }
        origin.executionDomain().terminalActorIoOperation(operation);
        return operation;
    }

    /** Commits close at invocation, returns a fresh Future, and shares one permanent logical outcome. */
    public ProtosFutureValue close(ProtosActivation activation) { return close(activation, false); }

    /** PLAT030 close entry for release that may require Actor-local guest execution. */
    public ProtosFutureValue closeWithGuestReleaseForRuntime(ProtosActivation activation) {
        return close(activation, true);
    }

    private ProtosFutureValue close(ProtosActivation activation, boolean reserveGuestCleanup) {
        Objects.requireNonNull(activation,"activation");
        if (activation.executionDomain()!=domain) throw new IllegalArgumentException("close belongs to another Actor domain");
        ProtosFutureValue follower=new ProtosFutureValue(futurePrototype,domain);
        // Close is committed by invocation; Future.cancel() may stop a waiter but cannot cancel this lifecycle.
        follower.attachCancellationProducer(() -> {});
        List<CloseFailure> cutover=new ArrayList<>();
        boolean start=false;
        synchronized(this) {
            switch(state) {
                case CLOSED_SUCCESS -> { follower.resolve(receiver,activation); return follower; }
                case CLOSED_FAILED -> {
                    // A host-only terminal publication leaves the Error to the next owner caller.
                    if (closeError == null) closeError=ProtosCoreErrors.newOccurrence(activation,ProtosCoreErrors.StandardError.I_O_ERROR);
                    follower.fail(closeError);
                    return follower;
                }
                case CLOSING -> { closeFollowers.add(follower); return follower; }
                case OPEN -> {
                    if (!authorizeFirstCloseLocked(activation, reserveGuestCleanup)) {
                        follower.fail(ProtosCoreErrors.newOccurrence(activation,ProtosCoreErrors.StandardError.I_O_LIFECYCLE_ERROR));
                        return follower;
                    }
                    state=State.CLOSING;
                    closeActivation=activation;
                    closeFollowers.add(follower);
                    terminationCleanupReserved=reserveGuestCleanup;
                    for (ProtosIoOperation operation : List.copyOf(operations)) {
                        ProtosIoOperation.CloseCutoverAction action =
                                operation.closeCutoverLocked();
                        if (action != null) {
                            cutover.add(
                                    new CloseFailure(
                                            operation,
                                            action.error(),
                                            action.cancellationHandler(),
                                            action.deferredForeignSettlement()));
                        }
                    }
                    start=readyToReleaseLocked();
                }
            }
        }
        for (CloseFailure failure : cutover) {
            if (failure.cancellationHandler != null) {
                try {
                    runCancellationHandler(failure.cancellationHandler);
                } catch (RuntimeException ignored) {
                    // Backend cancellation is best-effort machinery after the semantic cutover.
                    // It cannot rewrite the required IOLifecycleError terminal outcome.
                }
            }
            if (failure.deferredForeignSettlement) {
                // I125-B: the foreign Actor builds and publishes its own IOLifecycleError.
                failure.operation.scheduleDeferredCloseCutoverSettlement();
            } else if (failure.error != null) {
                failure.operation.failAtCloseCutover(failure.error);
            }
        }
        if (start) startRelease(true);
        else maybeStartRelease();
        return follower;
    }


    @TruffleBoundary
    private static void runCancellationHandler(Runnable handler) {
        handler.run();
    }

    private boolean authorizeFirstCloseLocked(ProtosActivation activation, boolean reserveGuestCleanup) {
        ProtosActor actor=domain.currentActorForRuntime().orElse(null);
        if (actor != null) return actor.authorizeLifecycleCloseForRuntime(activation,this,reserveGuestCleanup);
        if (reserveGuestCleanup) domain.registerActorIoLifecycleCleanupForRuntime(this);
        return true;
    }

    public ProtosIoReleaseExecution beginReleaseExecutionForRuntime(
            ProtosActivation activation, ReleaseCompletion completion) {
        Objects.requireNonNull(activation,"activation"); Objects.requireNonNull(completion,"completion");
        if (activation.executionDomain()!=domain) throw new IllegalArgumentException("lifecycle release belongs to another Actor execution domain");
        ProtosIoReleaseExecution created;
        synchronized(this) {
            if (state!=State.CLOSING || !releaseStarted) throw new IllegalStateException("lifecycle release execution requires an active closing release");
            if (closeActivation!=activation) throw new IllegalArgumentException("lifecycle release execution must use the committed close activation");
            if (releaseExecution!=null) throw new IllegalStateException("lifecycle already owns a release execution record");
            created=new ProtosIoReleaseExecution(this,activation,completion); releaseExecution=created;
        }
        try { domain.registerActorIoReleaseForRuntime(created); }
        catch(RuntimeException failure) { synchronized(this){if(releaseExecution==created)releaseExecution=null;} throw failure; }
        return created;
    }

    void releaseExecutionTerminalForRuntime(ProtosIoReleaseExecution terminalRelease) {
        synchronized(this) { if (releaseExecution==terminalRelease) releaseExecution=null; }
    }

    void operationTerminal(ProtosIoOperation operation) {
        boolean start;
        synchronized(this) {
            operations.remove(operation);
            start=readyToReleaseLocked();
        }
        // A retirement may run in any Actor or on a host thread: never an owner-synchronous start.
        if (start) startRelease(false);
    }

    /** Called only by the owner-domain close. */
    private void maybeStartRelease() {
        boolean start;
        synchronized(this) { start=readyToReleaseLocked(); }
        if (start) startRelease(true);
    }

    private boolean readyToReleaseLocked() {
        if (state != State.CLOSING || releaseStarted || !operations.isEmpty()) return false;
        releaseStarted=true;
        return true;
    }

    private void startRelease(boolean ownerClose) {
        if (ownerClose) synchronized(this) { synchronousReleaseCaller=Thread.currentThread(); }
        try {
            releaseStarter.start(new ReleaseCompletion() {
                @Override public void succeeded() { completeClose(null, false); }
                @Override public void failed(ProtosObjectValue error) { completeClose(Objects.requireNonNull(error,"error"), false); }
                @Override public void failedWithIoErrorForRuntime() { completeClose(null, true); }
            });
        } catch (RuntimeException ex) {
            completeClose(null, true);
        } finally {
            if (ownerClose) synchronized(this) { synchronousReleaseCaller=null; }
        }
    }

    /**
     * Publishes the terminal close outcome. Without owner-domain publication every outcome keeps
     * the direct path. With it, only an outcome reported synchronously inside the owner-domain
     * close's own release start is direct; any other outcome (a later backend callback, another
     * thread, or a release started by an operation retirement) is published through a targeted
     * completion in the owner domain, where any IOError is constructed. A terminating owner
     * with live cleanup Tasks may still publish a committed close outcome in its own domain,
     * allowing those Tasks to finish. Without live cleanup Tasks, or after the owner terminates,
     * the discarded outcome takes the host-only terminal path without guest execution.
     */
    private void completeClose(ProtosObjectValue error, boolean constructIoError) {
        boolean deferred;
        ProtosFutureValue carrier=null;
        synchronized(this) {
            deferred=ownerDomainPublication && synchronousReleaseCaller != Thread.currentThread();
            if (deferred) {
                if (state != State.CLOSING) return;
                carrier=closeFollowers.isEmpty() ? null : closeFollowers.get(0);
            }
        }
        if (!deferred) {
            finishClose(constructIoError ? ProtosCoreErrors.newOccurrence(closeActivation,ProtosCoreErrors.StandardError.I_O_ERROR) : error);
            return;
        }
        boolean success=error == null && !constructIoError;
        if (carrier == null) { finishCloseHostOnly(success); return; }
        try {
            ProtosFutureValue published=carrier;
            domain.enqueueCommittedLifecycleCloseCompletionForRuntime(
                    published,
                    () -> {
                        // Owner domain: the IOError and every close follower outcome are built here.
                        finishClose(constructIoError ? ProtosCoreErrors.newOccurrence(closeActivation,ProtosCoreErrors.StandardError.I_O_ERROR) : error);
                        return !published.isPending();
                    },
                    () -> finishCloseHostOnly(success));
        } catch (IllegalStateException terminatingOwner) {
            finishCloseHostOnly(success);
        }
    }

    private void finishCloseHostOnly(boolean success) {
        synchronized(this) {
            if (state != State.CLOSING) return;
            finishCloseHostOnlyLocked(success);
        }
        releaseCleanupReservation();
    }

    /** Terminal state without guest Error or Future settlement; followers keep the termination contract. */
    private void finishCloseHostOnlyLocked(boolean success) {
        state=success ? State.CLOSED_SUCCESS : State.CLOSED_FAILED;
        closeError=null;
        closeFollowers.clear();
    }

    private void releaseCleanupReservation() {
        boolean releaseReservation;
        synchronized(this) { releaseReservation=terminationCleanupReserved; terminationCleanupReserved=false; }
        if (releaseReservation) domain.terminalActorIoLifecycleCleanupForRuntime(this);
    }

    private void finishClose(ProtosObjectValue error) {
        List<ProtosFutureValue> followers;
        synchronized(this) {
            if (state != State.CLOSING) return;
            closeError=error;
            state=error==null ? State.CLOSED_SUCCESS : State.CLOSED_FAILED;
            followers=List.copyOf(closeFollowers);
            closeFollowers.clear();
        }
        ProtosActivation activation;
        synchronized(this) { activation=closeActivation; }
        for (ProtosFutureValue follower : followers) {
            if (error==null) follower.resolve(receiver, activation);
            else follower.fail(error);
        }
        releaseCleanupReservation();
    }

    private record CloseFailure(
            ProtosIoOperation operation,
            ProtosObjectValue error,
            Runnable cancellationHandler,
            boolean deferredForeignSettlement) {}
}
