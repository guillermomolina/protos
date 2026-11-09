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
package com.guillermomolina.protos.runtime.process;

import com.guillermomolina.protos.runtime.ProtosActor;
import com.guillermomolina.protos.runtime.ProtosActorScheduler;
import java.util.Objects;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Host-runtime execution placement for one local Protos Process.
 *
 * <p>This interface is implementation machinery only. It carries no Protos identity or authority
 * and must not be exposed as a Process/Actor/Task value. A Process may be unbound while legacy
 * drivers are still being migrated by I026-A4B3; once bound, the host is fixed for that Process
 * incarnation.
 */
public interface ProtosProcessExecutionHost {
    <T> T callForRuntime(Supplier<T> action);

    /**
     * I160: cancellation handle for one privately admitted Argon2id
     * computation. This is host machinery, not a Protos capability.
     */
    @FunctionalInterface
    interface PasswordKdfTicket {
        void cancel();
    }

    /**
     * Complete, independently owned result of one password KDF operation.
     *
     * <p>No guest object, Actor activation or Closure is retained.
     * The receiver must close the result after publication or discard.
     */
    final class PasswordKdfResult implements AutoCloseable {
        private byte[] salt;
        private byte[] tag;
        private final Boolean matches;
        private final boolean unavailable;

        public PasswordKdfResult(
                byte[] salt,
                byte[] tag,
                Boolean matches,
                boolean unavailable) {
            this.salt = salt == null ? null : Arrays.copyOf(salt, salt.length);
            this.tag = tag == null ? null : Arrays.copyOf(tag, tag.length);
            this.matches = matches;
            this.unavailable = unavailable;
        }

        public boolean unavailable() {
            return unavailable;
        }

        public boolean verification() {
            return matches != null;
        }

        public boolean matches() {
            if (matches == null) {
                throw new IllegalStateException(
                        "Result does not contain verification");
            }
            return matches;
        }

        public synchronized byte[] saltSnapshot() {
            if (salt == null) {
                throw new IllegalStateException("No salt result");
            }
            return Arrays.copyOf(salt, salt.length);
        }

        public synchronized byte[] tagSnapshot() {
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

    /**
     * Host-only completion handoff.
     *
     * <p>Called off-turn. Implementations must enqueue completion
     * into the owning Actor and never invoke guest code here.
     */
    @FunctionalInterface
    interface PasswordKdfCompletion {
        void completed(PasswordKdfResult result);
    }

    /**
     * Submit one fixed-profile Argon2id computation.
     *
     * <p>expectedTag is null for hashing and exactly 32 octets
     * for verification. All input arrays are borrowed and must
     * be snapshotted by the implementation before returning.
     *
     * <p>Admission is nonblocking. An empty result means that
     * the host is unavailable or its bounded queue is full.
     *
     * <p>Unhosted execution does not silently create a fallback
     * executor or compute Argon2id on the Actor.
     */
    /**
     * Private KDF admission failures. These values are host-only and
     * are not exposed to Protos programs.
     */
    enum PasswordKdfRejection {
        CAPACITY,
        UNAVAILABLE
    }

    /**
     * Exactly one of ticket and rejection is present.
     *
     * <p>Successful admission owns one cancellable ticket.
     * Rejected admission retains no submitted work.
     */
    record PasswordKdfAdmission(
            PasswordKdfTicket ticket,
            PasswordKdfRejection rejection) {

        public PasswordKdfAdmission {
            if ((ticket == null) == (rejection == null)) {
                throw new IllegalArgumentException(
                        "Invalid password KDF admission result");
            }
        }

        public static PasswordKdfAdmission accepted(
                PasswordKdfTicket ticket) {
            return new PasswordKdfAdmission(
                    Objects.requireNonNull(ticket), null);
        }

        public static PasswordKdfAdmission refused(
                PasswordKdfRejection rejection) {
            return new PasswordKdfAdmission(
                    null, Objects.requireNonNull(rejection));
        }

        public boolean isAdmitted() {
            return ticket != null;
        }
    }

    default PasswordKdfAdmission submitPasswordKdfForRuntime(
            ProtosActor owner,
            byte[] normalizedPassword,
            byte[] salt,
            byte[] expectedTag,
            PasswordKdfCompletion completion) {
        return PasswordKdfAdmission.refused(
                PasswordKdfRejection.UNAVAILABLE);
    }


    default void runForRuntime(Runnable action) {
        Objects.requireNonNull(action, "action");
        callForRuntime(
                () -> {
                    action.run();
                    return null;
                });
    }

    /**
     * Returns the host-owned normal Actor scheduler when this placement provides one.
     *
     * <p>Execution hosts without Actor carrier ownership return empty so staged unhosted and
     * deterministic test paths may retain their explicitly supplied scheduler.
     */
    default Optional<ProtosActorScheduler> actorSchedulerForRuntime() {
        return Optional.empty();
    }

    /**
     * Waits for this execution host's terminal disposition after semantic Process termination.
     *
     * <p>Hosts whose {@link #processTerminatedForRuntime()} callback is synchronously terminal may
     * retain this no-op default. Hosts with deferred cleanup override it and must surface terminal
     * cleanup failure as host/runtime failure rather than guest Error.
     */
    default void awaitTerminalDispositionForRuntime() {}

    /**
     * Platform lifecycle notification after one hosted Actor actually reached TERMINATED.
     *
     * <p>It precedes the Actor's removal from its Process, so Process terminal notification never
     * observes host resources of a terminated Actor. Implementations release Actor-owned host
     * resources only, must not throw, and report cleanup failure through terminal disposition.
     */
    default void actorTerminatedForRuntime(ProtosActor actor) {}

    /** Platform lifecycle notification after semantic Process termination is already complete. */
    void processTerminatedForRuntime();
}
