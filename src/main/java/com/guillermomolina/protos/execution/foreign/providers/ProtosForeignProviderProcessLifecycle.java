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
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Process-owned lazy foreign provider compartments and their Actor-isolated sessions (PLAT053).
 *
 * <p>Nothing is opened until an internal caller acquires a session. The first use of a provider in
 * this Process opens exactly one compartment; the first use of that provider by an Actor opens
 * exactly one session. Provider code always runs outside this object's monitor: the monitor only
 * publishes in-flight construction slots, so racing callers wait for the single constructor while
 * other providers and Actors proceed. Failed construction is never cached. No compartment or
 * session is opened for a provider that {@link ProtosForeignProviderAdmission} rejects, and an
 * admitted compartment is opened only through its admission.
 *
 * <p>Actor terminal disposition closes that Actor's sessions; Process terminal disposition closes
 * every remaining session and then every compartment. Cleanup failures are host/runtime failures:
 * they never stop the remaining cleanup and are reported by {@link #closeForRuntime()}.
 */
final class ProtosForeignProviderProcessLifecycle {
    private final ProtosForeignProviderRegistry registry;
    private final ProtosProcessRuntime process;
    private final Map<ProtosForeignProviderId, Slot<ProtosForeignProviderCompartment>>
            compartments = new LinkedHashMap<>();
    private final Map<ProtosActor, ActorSessions> sessions = new IdentityHashMap<>();
    private boolean closed;
    private Throwable cleanupFailure;

    ProtosForeignProviderProcessLifecycle(
            ProtosForeignProviderRegistry registry, ProtosProcessRuntime process) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.process = Objects.requireNonNull(process, "process");
    }

    /**
     * Returns the live session of the exact Actor/provider pair, opening the Process compartment
     * and the Actor session lazily on first use.
     */
    ProtosForeignProviderSessionBinding sessionForRuntime(
            ProtosActor actor, ProtosForeignProviderId providerId) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(providerId, "providerId");
        if (actor.processForRuntime().orElse(null) != process) {
            throw new IllegalArgumentException(
                    "Actor belongs to another or unhosted Protos Process");
        }
        ProtosForeignProviderDescriptor descriptor =
                registry.lookup(providerId)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "unknown foreign provider: " + providerId.value()));
        ProtosForeignProviderAdmission.require(descriptor);

        Slot<ProtosForeignProviderSessionBinding> slot;
        boolean create = false;
        synchronized (this) {
            requireActorAdmitted(actor);
            slot = sessions.containsKey(actor) ? sessions.get(actor).slots.get(providerId) : null;
            if (slot == null) {
                requireNewAdmission();
                slot = new Slot<>();
                sessions.computeIfAbsent(actor, ignored -> new ActorSessions())
                        .slots
                        .put(providerId, slot);
                create = true;
            }
        }
        if (!create) {
            ProtosForeignProviderSessionBinding binding = slot.await("session");
            if (!binding.isOpenForRuntime()) {
                throw new IllegalStateException(
                        "foreign provider session is closed: " + providerId.value());
            }
            return binding;
        }

        ProtosForeignProviderSessionBinding binding;
        try {
            ProtosForeignProviderCompartment compartment = compartmentForRuntime(descriptor);
            binding =
                    new ProtosForeignProviderSessionBinding(
                            providerId,
                            Objects.requireNonNull(
                                    compartment.openSession(), "foreign provider session"));
        } catch (RuntimeException | Error failure) {
            synchronized (this) {
                ActorSessions owned = sessions.get(actor);
                if (owned != null && owned.slots.get(providerId) == slot) {
                    owned.slots.remove(providerId);
                    if (owned.slots.isEmpty()) {
                        sessions.remove(actor);
                    }
                }
            }
            slot.fail(failure);
            throw failure;
        }

        boolean published;
        synchronized (this) {
            ActorSessions owned = sessions.get(actor);
            // Actor or Process terminal cleanup removes in-flight slots; never resurrect them.
            published = owned != null && owned.slots.get(providerId) == slot;
            if (published) {
                slot.complete(binding);
            }
        }
        if (!published) {
            IllegalStateException failure =
                    new IllegalStateException(
                            "foreign provider session owner terminated during construction");
            closeLoser(binding::closeForRuntime, failure);
            slot.fail(failure);
            throw failure;
        }
        return binding;
    }

    private ProtosForeignProviderCompartment compartmentForRuntime(
            ProtosForeignProviderDescriptor descriptor) {
        ProtosForeignProviderId providerId = descriptor.id();
        Slot<ProtosForeignProviderCompartment> slot;
        boolean create = false;
        synchronized (this) {
            slot = compartments.get(providerId);
            if (slot == null) {
                requireNewAdmission();
                slot = new Slot<>();
                compartments.put(providerId, slot);
                create = true;
            }
        }
        if (!create) {
            return slot.await("compartment");
        }

        ProtosForeignProviderCompartment compartment;
        try {
            compartment = ProtosForeignProviderAdmission.openCompartment(descriptor);
            if (!providerId.equals(compartment.providerId())) {
                IllegalStateException failure =
                        new IllegalStateException(
                                "foreign provider compartment identity mismatch: "
                                        + providerId.value());
                closeLoser(compartment::close, failure);
                throw failure;
            }
        } catch (RuntimeException | Error failure) {
            synchronized (this) {
                compartments.remove(providerId, slot);
            }
            slot.fail(failure);
            throw failure;
        }

        boolean published;
        synchronized (this) {
            // Process terminal cleanup removes in-flight slots; never resurrect them.
            published = compartments.get(providerId) == slot;
            if (published) {
                slot.complete(compartment);
            }
        }
        if (!published) {
            IllegalStateException failure =
                    new IllegalStateException(
                            "foreign provider compartment owner terminated during construction");
            closeLoser(compartment::close, failure);
            slot.fail(failure);
            throw failure;
        }
        return compartment;
    }

    private void requireActorAdmitted(ProtosActor actor) {
        if (closed) {
            throw new IllegalStateException("Process no longer admits foreign provider use");
        }
        if (actor.lifecycleState() == ProtosActor.LifecycleState.TERMINATED) {
            throw new IllegalStateException("terminated Actor cannot use a foreign provider");
        }
    }

    private void requireNewAdmission() {
        if (closed || process.lifecycleState() != ProtosProcessRuntime.LifecycleState.RUNNING) {
            throw new IllegalStateException("Process no longer admits foreign provider use");
        }
    }

    /** Closes every session of one Actor that reached TERMINATED; never throws. */
    void actorTerminatedForRuntime(ProtosActor actor) {
        Objects.requireNonNull(actor, "actor");
        ActorSessions owned;
        synchronized (this) {
            owned = sessions.remove(actor);
        }
        if (owned != null) {
            closeSessions(new ArrayList<>(owned.slots.values()));
        }
    }

    /**
     * Process terminal disposition: rejects all later use, closes remaining sessions, then closes
     * every compartment exactly once.
     *
     * @return the accumulated host/runtime cleanup failure of this Process, or null
     */
    Throwable closeForRuntime() {
        List<Slot<ProtosForeignProviderSessionBinding>> remainingSessions = new ArrayList<>();
        List<Slot<ProtosForeignProviderCompartment>> remainingCompartments;
        synchronized (this) {
            if (closed) {
                return cleanupFailure;
            }
            closed = true;
            for (ActorSessions owned : sessions.values()) {
                remainingSessions.addAll(owned.slots.values());
            }
            sessions.clear();
            remainingCompartments = new ArrayList<>(compartments.values());
            compartments.clear();
        }
        closeSessions(remainingSessions);
        for (Slot<ProtosForeignProviderCompartment> slot : remainingCompartments) {
            ProtosForeignProviderCompartment compartment = slot.publishedOrNull();
            if (compartment != null) {
                try {
                    compartment.close();
                } catch (RuntimeException | Error failure) {
                    recordCleanupFailure(failure);
                }
            }
        }
        synchronized (this) {
            return cleanupFailure;
        }
    }

    private void closeSessions(List<Slot<ProtosForeignProviderSessionBinding>> slots) {
        // In-flight slots are closed by their constructor once it observes the removal.
        for (Slot<ProtosForeignProviderSessionBinding> slot : slots) {
            ProtosForeignProviderSessionBinding binding = slot.publishedOrNull();
            if (binding != null) {
                try {
                    binding.closeForRuntime();
                } catch (RuntimeException | Error failure) {
                    recordCleanupFailure(failure);
                }
            }
        }
    }

    private synchronized void recordCleanupFailure(Throwable failure) {
        if (cleanupFailure == null) {
            cleanupFailure = failure;
        } else if (cleanupFailure != failure) {
            cleanupFailure.addSuppressed(failure);
        }
    }

    private static void closeLoser(Runnable close, Throwable primary) {
        try {
            close.run();
        } catch (RuntimeException | Error closeFailure) {
            primary.addSuppressed(closeFailure);
        }
    }

    synchronized int compartmentCountForTesting() {
        return compartments.size();
    }

    synchronized int sessionCountForTesting() {
        int count = 0;
        for (ActorSessions owned : sessions.values()) {
            count += owned.slots.size();
        }
        return count;
    }

    /** Provider sessions of one Actor, in acquisition order. */
    private static final class ActorSessions {
        private final Map<ProtosForeignProviderId, Slot<ProtosForeignProviderSessionBinding>>
                slots = new LinkedHashMap<>();
    }

    /** One in-flight or published construction; waiters observe the single constructor. */
    private static final class Slot<T> {
        private final Thread constructor = Thread.currentThread();
        private final CompletableFuture<T> value = new CompletableFuture<>();

        T await(String role) {
            if (!value.isDone() && constructor == Thread.currentThread()) {
                throw new IllegalStateException(
                        "reentrant foreign provider " + role + " construction");
            }
            try {
                return value.join();
            } catch (CompletionException | CancellationException failure) {
                throw new IllegalStateException(
                        "foreign provider " + role + " construction failed", failure.getCause());
            }
        }

        void complete(T published) {
            value.complete(published);
        }

        void fail(Throwable failure) {
            value.completeExceptionally(failure);
        }

        T publishedOrNull() {
            return value.isDone() && !value.isCompletedExceptionally() ? value.join() : null;
        }
    }
}
