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

import java.util.Objects;

/**
 * Internal state of one concrete-Actor delivery attempt from admission through completion.
 *
 * <p>This is runtime machinery, not a public status object. The same attempt state underlies
 * one-way SendOperation delivery and request/reply delivery while preserving the single concrete-
 * Actor acceptance boundary and explicit post-acceptance loss classification.
 */
public final class ProtosActorDeliveryAttempt {
    @FunctionalInterface
    interface FailureObserver {
        void failed(State state);
    }

    private static final FailureObserver NOOP_FAILURE_OBSERVER = ignored -> {};

    public enum State {
        PENDING,
        ACCEPTED,
        RUNNING,
        COMPLETED,
        CANCELLED_BEFORE_ACCEPTANCE,
        FAILED_BEFORE_ACCEPTANCE,
        FAILED_AFTER_ACCEPTANCE
    }

    private final ProtosActorDeliveryAdmission owner;
    private final ProtosActorRefValue sender;
    private final ProtosTask.Continuation turn;
    private final FailureObserver failureObserver;
    private State state = State.PENDING;

    ProtosActorDeliveryAttempt(
            ProtosActorDeliveryAdmission owner,
            ProtosActorRefValue sender,
            ProtosTask.Continuation turn) {
        this(owner, sender, turn, NOOP_FAILURE_OBSERVER);
    }

    ProtosActorDeliveryAttempt(
            ProtosActorDeliveryAdmission owner,
            ProtosActorRefValue sender,
            ProtosTask.Continuation turn,
            FailureObserver failureObserver) {
        this.owner = Objects.requireNonNull(owner, "owner");
        this.sender = sender;
        this.turn = Objects.requireNonNull(turn, "turn");
        this.failureObserver = Objects.requireNonNull(failureObserver, "failureObserver");
    }

    public synchronized State state() {
        return state;
    }

    /**
     * Establishes known pre-acceptance cancellation when it still wins the acceptance race.
     *
     * @return true only for the call that establishes cancellation before concrete-Actor acceptance
     */
    public boolean cancelBeforeAcceptance() {
        return owner.cancel(this);
    }

    boolean belongsToForRuntime(ProtosActorDeliveryAdmission admission) {
        return owner == admission;
    }

    ProtosActorRefValue senderForRuntime() {
        return sender;
    }

    ProtosTask.Continuation turnForRuntime() {
        return turn;
    }

    boolean beginDispatchForRuntime() {
        return owner.beginDispatchForRuntime(this);
    }

    synchronized boolean markRunningIfAcceptedForRuntime() {
        if (state == State.ACCEPTED) {
            state = State.RUNNING;
            return true;
        }
        return state == State.RUNNING;
    }

    synchronized void markCompletedForRuntime() {
        if (state != State.RUNNING) {
            throw new IllegalStateException("complete requires running delivery attempt, was " + state);
        }
        state = State.COMPLETED;
    }

    void markFailedAfterAcceptanceForRuntime() {
        FailureObserver notify;
        synchronized (this) {
            if (state == State.FAILED_AFTER_ACCEPTANCE) {
                return;
            }
            if (state != State.ACCEPTED && state != State.RUNNING) {
                throw new IllegalStateException(
                        "post-acceptance failure requires accepted/running delivery attempt, was " + state);
            }
            state = State.FAILED_AFTER_ACCEPTANCE;
            notify = failureObserver;
        }
        notify.failed(State.FAILED_AFTER_ACCEPTANCE);
    }

    synchronized boolean retryableForRuntime() {
        return state == State.FAILED_BEFORE_ACCEPTANCE
                || state == State.FAILED_AFTER_ACCEPTANCE;
    }

    synchronized boolean isPendingForRuntime() {
        return state == State.PENDING;
    }

    synchronized void markAcceptedForRuntime() {
        requirePending("accept");
        state = State.ACCEPTED;
    }

    synchronized void markCancelledForRuntime() {
        requirePending("cancel");
        state = State.CANCELLED_BEFORE_ACCEPTANCE;
    }

    void markFailedBeforeAcceptanceForRuntime() {
        FailureObserver notify;
        synchronized (this) {
            requirePending("fail before acceptance");
            state = State.FAILED_BEFORE_ACCEPTANCE;
            notify = failureObserver;
        }
        notify.failed(State.FAILED_BEFORE_ACCEPTANCE);
    }

    private void requirePending(String transition) {
        if (state != State.PENDING) {
            throw new IllegalStateException(
                    transition + " requires pending delivery attempt, was " + state);
        }
    }
}
