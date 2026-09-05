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
 * Internal state of one concrete-Actor delivery attempt before/at acceptance.
 *
 * <p>This is runtime machinery, not the public Core SendOperation. I011-7 uses it as the
 * acceptance/backpressure foundation that later send/request slices compose with snapshots,
 * retry, reply, and uncertainty.
 */
public final class ProtosActorDeliveryAttempt {
    public enum State {
        PENDING,
        ACCEPTED,
        CANCELLED_BEFORE_ACCEPTANCE,
        FAILED_BEFORE_ACCEPTANCE
    }

    private final ProtosActorDeliveryAdmission owner;
    private final ProtosActorRefValue sender;
    private final ProtosTask.Continuation turn;
    private State state = State.PENDING;

    ProtosActorDeliveryAttempt(
            ProtosActorDeliveryAdmission owner,
            ProtosActorRefValue sender,
            ProtosTask.Continuation turn) {
        this.owner = Objects.requireNonNull(owner, "owner");
        this.sender = sender;
        this.turn = Objects.requireNonNull(turn, "turn");
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

    ProtosActorRefValue senderForRuntime() {
        return sender;
    }

    ProtosTask.Continuation turnForRuntime() {
        return turn;
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

    synchronized void markFailedBeforeAcceptanceForRuntime() {
        requirePending("fail before acceptance");
        state = State.FAILED_BEFORE_ACCEPTANCE;
    }

    private void requirePending(String transition) {
        if (state != State.PENDING) {
            throw new IllegalStateException(
                    transition + " requires pending delivery attempt, was " + state);
        }
    }
}
