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

import com.guillermomolina.protos.runtime.ProtosActor;
import com.guillermomolina.protos.runtime.ProtosProcessRuntime;
import com.guillermomolina.protos.runtime.process.ProtosProcessExecutionHost.PasswordKdfAdmission;
import com.guillermomolina.protos.runtime.process.ProtosProcessExecutionHost.PasswordKdfCompletion;
import com.guillermomolina.protos.runtime.process.ProtosProcessExecutionHost.PasswordKdfRejection;
import com.guillermomolina.protos.runtime.process.ProtosProcessExecutionHost.PasswordKdfResult;
import com.guillermomolina.protos.runtime.process.ProtosProcessExecutionHost.PasswordKdfTicket;
import java.util.Arrays;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Private per-Process KDF admission and cancellation custody.
 *
 * Host workers never execute guest code. Completion transfers only
 * inert owned data to the owning Actor's completion queue.
 */
final class ProtosPasswordKdfCustody implements AutoCloseable {

    private final ProtosProcessRuntime process;
    private final Supplier<ProtosPasswordKdfExecutor> sharedExecutor;
    private ProtosPasswordKdfExecutor ownedExecutor;

    private final Object passwordKdfLock = new Object();
    private final Map<ProtosActor, Set<PasswordTicket>>
            passwordKdfTickets = new IdentityHashMap<>();
    private boolean passwordKdfRetired;

    private ProtosPasswordKdfCustody(
            ProtosProcessRuntime process,
            Supplier<ProtosPasswordKdfExecutor> sharedExecutor) {
        this.process = Objects.requireNonNull(process);
        this.sharedExecutor = sharedExecutor;
    }

    static ProtosPasswordKdfCustody shared(
            ProtosProcessRuntime process,
            Supplier<ProtosPasswordKdfExecutor> executor) {
        return new ProtosPasswordKdfCustody(
                process, Objects.requireNonNull(executor));
    }

    static ProtosPasswordKdfCustody owned(
            ProtosProcessRuntime process) {
        return new ProtosPasswordKdfCustody(process, null);
    }

    private ProtosPasswordKdfExecutor executorForRuntime() {
        if (sharedExecutor != null) {
            return sharedExecutor.get();
        }
        if (ownedExecutor == null) {
            ownedExecutor = new ProtosPasswordKdfExecutor();
        }
        return ownedExecutor;
    }

    public PasswordKdfAdmission submitForRuntime(
            ProtosActor owner,
            byte[] normalizedPassword,
            byte[] salt,
            byte[] expectedTag,
            PasswordKdfCompletion completion) {

        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(normalizedPassword, "normalizedPassword");
        Objects.requireNonNull(salt, "salt");
        Objects.requireNonNull(completion, "completion");

        if (owner.processForRuntime().orElse(null) != process) {
            throw new IllegalArgumentException(
                    "KDF Actor belongs to another Process");
        }

        synchronized (passwordKdfLock) {
            if (passwordKdfRetired
                    || owner.lifecycleState()
                            == ProtosActor.LifecycleState.TERMINATING
                    || owner.lifecycleState()
                            == ProtosActor.LifecycleState.TERMINATED) {
                return PasswordKdfAdmission.refused(
                        PasswordKdfRejection.UNAVAILABLE);
            }

            PasswordTicket ticket = new PasswordTicket(owner);

            ProtosPasswordKdfExecutor.Ticket admitted;
            ProtosPasswordKdfExecutor executor;

            try {
                executor = executorForRuntime();
                admitted = executor.submit(
                        normalizedPassword,
                        salt,
                        expectedTag,
                        result -> ticket.completed(result, completion));
            } catch (IllegalStateException unavailable) {
                return PasswordKdfAdmission.refused(
                        PasswordKdfRejection.UNAVAILABLE);
            }

            if (admitted == null) {
                return PasswordKdfAdmission.refused(
                        executor.isClosedForRuntime()
                                ? PasswordKdfRejection.UNAVAILABLE
                                : PasswordKdfRejection.CAPACITY);
            }

            ticket.delegate = admitted;

            // The worker may have completed before this method
            // receives its admission ticket. Do not retain an
            // already-terminal ticket in the Process registry.
            if (!ticket.completed && !ticket.cancelled) {
                passwordKdfTickets
                        .computeIfAbsent(owner, ignored -> new HashSet<>())
                        .add(ticket);
            }

            if (ticket.cancelled) {
                admitted.cancel();
            }

            return PasswordKdfAdmission.accepted(ticket);
        }
    }

    /**
     * One cancellation token per admitted KDF invocation.
     *
     * <p>Its state is guarded by passwordKdfLock. The executor may call
     * completed before submit returns; the same lock orders both events.
     */
    private final class PasswordTicket implements PasswordKdfTicket {

        private final ProtosActor owner;
        private ProtosPasswordKdfExecutor.Ticket delegate;
        private boolean cancelled;
        private boolean completed;

        PasswordTicket(ProtosActor owner) {
            this.owner = owner;
        }

        @Override
        public void cancel() {
            ProtosPasswordKdfExecutor.Ticket underlying;

            synchronized (passwordKdfLock) {
                if (cancelled) {
                    return;
                }

                // Mark cancellation even if computation has already
                // finished: a host-only completion handoff may still
                // be preparing its independently owned result.
                cancelled = true;
                underlying = delegate;
                forgetTicketLocked(this);
            }

            if (underlying != null) {
                underlying.cancel();
            }
        }

        void completed(
                ProtosPasswordKdfExecutor.Result result,
                PasswordKdfCompletion completion) {

            boolean deliver;

            synchronized (passwordKdfLock) {
                deliver = !cancelled
                        && !completed
                        && !passwordKdfRetired
                        && owner.lifecycleState()
                                != ProtosActor.LifecycleState.TERMINATING
                        && owner.lifecycleState()
                                != ProtosActor.LifecycleState.TERMINATED;

                completed = true;
                delegate = null;
                forgetTicketLocked(this);
            }

            if (!deliver) {
                if (result != null) {
                    result.close();
                }
                return;
            }

            PasswordKdfResult transfer = null;

            try {
                if (result == null || result.isUnavailable()) {
                    transfer = new PasswordKdfResult(
                            null, null, null, true);
                } else if (result.isVerification()) {
                    transfer = new PasswordKdfResult(
                            null, null, result.matches(), false);
                } else {
                    byte[] saltCopy = result.saltSnapshot();
                    byte[] tagCopy = result.tagSnapshot();

                    try {
                        transfer = new PasswordKdfResult(
                                saltCopy, tagCopy, null, false);
                    } finally {
                        Arrays.fill(saltCopy, (byte) 0);
                        Arrays.fill(tagCopy, (byte) 0);
                    }
                }

                // Recheck ownership immediately before the host-only
                // handoff. Cancellation may have arrived while copying
                // the private result arrays.
                synchronized (passwordKdfLock) {
                    if (cancelled
                            || passwordKdfRetired
                            || owner.lifecycleState()
                                    == ProtosActor.LifecycleState.TERMINATING
                            || owner.lifecycleState()
                                    == ProtosActor.LifecycleState.TERMINATED) {
                        return;
                    }
                }

                // This callback only queues a targeted Actor completion.
                // The Future's atomic terminal transition is the final
                // commitment gate if cancellation races with this call.
                completion.completed(transfer);
                transfer = null;
            } finally {
                if (transfer != null) {
                    transfer.close();
                }
                if (result != null) {
                    result.close();
                }
            }
        }
    }

    private void forgetTicketLocked(PasswordTicket ticket) {
        Set<PasswordTicket> tickets = passwordKdfTickets.get(ticket.owner);

        if (tickets == null) {
            return;
        }

        tickets.remove(ticket);

        if (tickets.isEmpty()) {
            passwordKdfTickets.remove(ticket.owner);
        }
    }

    private void cancelPasswordKdfForActor(ProtosActor actor) {
        Set<PasswordTicket> tickets;

        synchronized (passwordKdfLock) {
            tickets = passwordKdfTickets.remove(actor);

            if (tickets == null) {
                return;
            }

            tickets = new HashSet<>(tickets);
        }

        for (PasswordTicket ticket : tickets) {
            ticket.cancel();
        }
    }

    private void retirePasswordKdfForProcess() {
        Set<PasswordTicket> tickets = new HashSet<>();

        synchronized (passwordKdfLock) {
            if (passwordKdfRetired) {
                return;
            }

            passwordKdfRetired = true;

            for (Set<PasswordTicket> owned : passwordKdfTickets.values()) {
                tickets.addAll(owned);
            }

            passwordKdfTickets.clear();
        }

        for (PasswordTicket ticket : tickets) {
            ticket.cancel();
        }
    }


    void cancelActorForRuntime(ProtosActor actor) {
        cancelPasswordKdfForActor(actor);
    }

    void retireForRuntime() {
        retirePasswordKdfForProcess();
    }

    @Override
    public void close() {
        retireForRuntime();

        ProtosPasswordKdfExecutor executor;
        synchronized (passwordKdfLock) {
            executor = ownedExecutor;
            ownedExecutor = null;
        }

        if (executor != null) {
            executor.close();
        }
    }
}
