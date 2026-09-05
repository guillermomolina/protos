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

import java.util.ArrayDeque;
import java.util.Objects;

/**
 * Internal concrete-Actor pre-acceptance admission/backpressure queue.
 *
 * <p>Accepted message ownership remains exclusively in {@link ProtosActorMailbox}. Pending entries
 * here are logical delivery-operation state waiting for a mailbox acceptance opportunity; this
 * layer does not create another accepted-message buffer and never dispatches application code.
 * A FIFO pending discipline is an implementation choice that satisfies both same-sender FIFO and
 * Core weak admission fairness without creating a language-level total-order promise between
 * unrelated senders.
 */
final class ProtosActorDeliveryAdmission {
    private final ProtosActor actor;
    private final ArrayDeque<ProtosActorDeliveryAttempt> pending = new ArrayDeque<>();

    ProtosActorDeliveryAdmission(ProtosActor actor) {
        this.actor = Objects.requireNonNull(actor, "actor");
    }

    ProtosActorDeliveryAttempt submit(
            ProtosActorRefValue sender, ProtosTask.Continuation turn) {
        ProtosActorDeliveryAttempt attempt =
                new ProtosActorDeliveryAttempt(this, sender, turn);
        synchronized (actor) {
            synchronized (this) {
                if (isTerminating()) {
                    attempt.markFailedBeforeAcceptanceForRuntime();
                    return attempt;
                }
                pending.addLast(attempt);
                drainLocked();
            }
        }
        return attempt;
    }

    boolean cancel(ProtosActorDeliveryAttempt attempt) {
        Objects.requireNonNull(attempt, "attempt");
        synchronized (actor) {
            synchronized (this) {
                if (!attempt.isPendingForRuntime() || !pending.remove(attempt)) {
                    return false;
                }
                attempt.markCancelledForRuntime();
                drainLocked();
                return true;
            }
        }
    }

    void capacityAvailable() {
        synchronized (actor) {
            synchronized (this) {
                drainLocked();
            }
        }
    }

    void lifecycleChanged() {
        synchronized (actor) {
            synchronized (this) {
                drainLocked();
            }
        }
    }

    synchronized int pendingCountForTesting() {
        return pending.size();
    }

    private void drainLocked() {
        if (isTerminating()) {
            failAllPendingLocked();
            return;
        }

        while (true) {
            ProtosActorDeliveryAttempt attempt = pending.peekFirst();
            if (attempt == null) {
                return;
            }
            if (!attempt.isPendingForRuntime()) {
                pending.removeFirst();
                continue;
            }

            if (!actor.tryAcceptMessageForRuntime(attempt.turnForRuntime())) {
                if (isTerminating()) {
                    failAllPendingLocked();
                }
                return;
            }

            pending.removeFirst();
            attempt.markAcceptedForRuntime();
        }
    }

    private void failAllPendingLocked() {
        ProtosActorDeliveryAttempt attempt;
        while ((attempt = pending.pollFirst()) != null) {
            if (attempt.isPendingForRuntime()) {
                attempt.markFailedBeforeAcceptanceForRuntime();
            }
        }
    }

    private boolean isTerminating() {
        ProtosActor.LifecycleState state = actor.lifecycleState();
        return state == ProtosActor.LifecycleState.TERMINATING
                || state == ProtosActor.LifecycleState.TERMINATED;
    }
}
