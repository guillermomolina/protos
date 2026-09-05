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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Internal bounded ownership queue for already-accepted Actor message turns.
 *
 * <p>The mailbox owns only work that has crossed the concrete-Actor acceptance boundary. Pending
 * pre-acceptance/backpressure operations remain outside this queue and are implemented by the
 * communication layer. The finite capacity is an implementation policy, not a portable numeric
 * constant.
 */
final class ProtosActorMailbox {
    private static final Runnable NOOP_WAKEUP = () -> {};

    private static final class AcceptedTurn {
        private final ProtosTask.Continuation turn;
        private final ProtosActorDeliveryAttempt attempt;

        private AcceptedTurn(
                ProtosTask.Continuation turn, ProtosActorDeliveryAttempt attempt) {
            this.turn = Objects.requireNonNull(turn, "turn");
            this.attempt = attempt;
        }
    }

    private final int capacity;
    private final ArrayDeque<AcceptedTurn> accepted = new ArrayDeque<>();
    private Runnable schedulerWakeup = NOOP_WAKEUP;
    private Runnable admissionWakeup = NOOP_WAKEUP;

    ProtosActorMailbox(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Actor mailbox capacity must be positive");
        }
        this.capacity = capacity;
    }

    boolean tryAccept(ProtosTask.Continuation turn) {
        return tryAcceptEntry(new AcceptedTurn(turn, null), false);
    }

    boolean tryAccept(ProtosActorDeliveryAttempt attempt) {
        Objects.requireNonNull(attempt, "attempt");
        return tryAcceptEntry(new AcceptedTurn(attempt.turnForRuntime(), attempt), true);
    }

    private boolean tryAcceptEntry(AcceptedTurn entry, boolean establishAcceptance) {
        Runnable wakeup;
        synchronized (this) {
            if (accepted.size() >= capacity) {
                return false;
            }
            // Publish ACCEPTED before the scheduler can observe the retained turn.
            if (establishAcceptance) {
                entry.attempt.markAcceptedForRuntime();
            }
            accepted.addLast(entry);
            wakeup = schedulerWakeup;
        }
        wakeup.run();
        return true;
    }

    ProtosTask.Continuation pollForDispatch() {
        AcceptedTurn entry;
        Runnable capacityWakeup = null;
        synchronized (this) {
            entry = accepted.pollFirst();
            if (entry != null) {
                capacityWakeup = admissionWakeup;
            }
        }
        if (capacityWakeup != null) {
            capacityWakeup.run();
        }
        return entry == null ? null : entry.turn;
    }

    void failAcceptedForTermination() {
        List<ProtosActorDeliveryAttempt> lost = new ArrayList<>();
        synchronized (this) {
            AcceptedTurn entry;
            while ((entry = accepted.pollFirst()) != null) {
                if (entry.attempt != null) {
                    lost.add(entry.attempt);
                }
            }
        }
        for (ProtosActorDeliveryAttempt attempt : lost) {
            attempt.markFailedAfterAcceptanceForRuntime();
        }
    }

    synchronized boolean hasAccepted() {
        return !accepted.isEmpty();
    }

    synchronized int size() {
        return accepted.size();
    }

    int capacity() {
        return capacity;
    }

    void bindSchedulerWakeup(Runnable wakeup) {
        Objects.requireNonNull(wakeup, "wakeup");
        synchronized (this) {
            if (schedulerWakeup != NOOP_WAKEUP) {
                throw new IllegalStateException("Actor mailbox is already attached to a scheduler");
            }
            schedulerWakeup = wakeup;
        }
    }

    void bindAdmissionWakeup(Runnable wakeup) {
        Objects.requireNonNull(wakeup, "wakeup");
        synchronized (this) {
            if (admissionWakeup != NOOP_WAKEUP) {
                throw new IllegalStateException(
                        "Actor mailbox already has an admission wakeup");
            }
            admissionWakeup = wakeup;
        }
    }

    void signalRuntime() {
        Runnable wakeup;
        synchronized (this) {
            wakeup = schedulerWakeup;
        }
        wakeup.run();
    }
}
