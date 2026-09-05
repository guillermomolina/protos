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

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * One concrete Actor incarnation.
 *
 * <p>The incarnation identity is fixed at construction time and is independent of lifecycle,
 * execution-domain, module-state, mailbox, placement, or host-thread identity.
 */
public final class ProtosActor {
    public enum LifecycleState {
        INITIALIZING,
        READY,
        TERMINATING,
        TERMINATED
    }

    private static final AtomicLong NEXT_INCARNATION_ID = new AtomicLong();
    private static final int DEFAULT_MAILBOX_CAPACITY = 256;

    private final long incarnationIdentity;
    private final ProtosActorExecutionDomain executionDomain;
    private final ProtosActorModuleState moduleState;
    private final ProtosActorRefValue reference;
    private final ProtosActorMailbox mailbox;
    private final ProtosActorDeliveryAdmission deliveryAdmission;
    private ProtosPrelude messagePrelude;
    private ProtosModuleKey messageModuleKey;
    private final AtomicReference<LifecycleState> lifecycle =
            new AtomicReference<>(LifecycleState.INITIALIZING);
    private final AtomicReference<ProtosObjectValue> currentBehavior =
            new AtomicReference<>();
    private final Set<ProtosActorTerminationObservation> terminationObservers =
            new LinkedHashSet<>();
    private boolean terminationCutoverCleanupComplete;

    public ProtosActor(ProtosObjectValue actorRefPrototype) {
        this(
                actorRefPrototype,
                new ProtosActorExecutionDomain(),
                new ProtosActorModuleState(),
                DEFAULT_MAILBOX_CAPACITY);
    }

    ProtosActor(
            ProtosObjectValue actorRefPrototype,
            ProtosActorExecutionDomain executionDomain,
            ProtosActorModuleState moduleState) {
        this(actorRefPrototype, executionDomain, moduleState, DEFAULT_MAILBOX_CAPACITY);
    }

    ProtosActor(
            ProtosObjectValue actorRefPrototype,
            ProtosActorExecutionDomain executionDomain,
            ProtosActorModuleState moduleState,
            int mailboxCapacity) {
        this.executionDomain =
                Objects.requireNonNull(executionDomain, "executionDomain");
        this.moduleState = Objects.requireNonNull(moduleState, "moduleState");
        this.mailbox = new ProtosActorMailbox(mailboxCapacity);
        this.deliveryAdmission = new ProtosActorDeliveryAdmission(this);
        this.mailbox.bindAdmissionWakeup(deliveryAdmission::capacityAvailable);
        long nextIdentity = NEXT_INCARNATION_ID.incrementAndGet();
        if (nextIdentity <= 0) {
            throw new IllegalStateException("Actor incarnation identity space exhausted");
        }
        this.incarnationIdentity = nextIdentity;
        this.reference =
                new ProtosActorRefValue(
                        Objects.requireNonNull(actorRefPrototype, "actorRefPrototype"), this);
        this.executionDomain.bindActor(this);
    }

    public LifecycleState lifecycleState() {
        return lifecycle.get();
    }

    public ProtosActorExecutionDomain executionDomain() {
        return executionDomain;
    }

    public ProtosActorModuleState moduleState() {
        return moduleState;
    }

    public ProtosActorRefValue reference() {
        return reference;
    }

    synchronized boolean tryAcceptMessageForRuntime(ProtosTask.Continuation turn) {
        LifecycleState state = lifecycle.get();
        if (state == LifecycleState.TERMINATING || state == LifecycleState.TERMINATED) {
            return false;
        }
        return mailbox.tryAccept(turn);
    }

    ProtosActorMailbox mailboxForRuntime() {
        return mailbox;
    }

    ProtosActorDeliveryAttempt beginDeliveryForRuntime(
            ProtosActorRefValue sender, ProtosTask.Continuation turn) {
        return deliveryAdmission.submit(sender, turn);
    }

    ProtosActorDeliveryAdmission deliveryAdmissionForRuntime() {
        return deliveryAdmission;
    }

    /**
     * Fixes the destination execution environment used by ordinary external message turns.
     * Bootstrap owns this binding; message delivery never imports the sender's module environment.
     */
    public synchronized void bindMessageEnvironmentForRuntime(
            ProtosPrelude prelude, ProtosModuleKey canonicalModuleKey) {
        Objects.requireNonNull(prelude, "prelude");
        Objects.requireNonNull(canonicalModuleKey, "canonicalModuleKey");
        if (messagePrelude != null
                && (messagePrelude != prelude || !messageModuleKey.equals(canonicalModuleKey))) {
            throw new IllegalStateException("Actor message environment is already fixed");
        }
        messagePrelude = prelude;
        messageModuleKey = canonicalModuleKey;
    }

    ProtosActivation newMessageActivationForRuntime() {
        ProtosPrelude prelude;
        ProtosModuleKey moduleKey;
        synchronized (this) {
            prelude = messagePrelude;
            moduleKey = messageModuleKey;
        }
        if (prelude == null || moduleKey == null) {
            throw new IllegalStateException("Actor has no completed bootstrap message environment");
        }
        return prelude.newModuleActivation(
                moduleState,
                moduleKey,
                prelude.newExecutionContext(),
                executionDomain);
    }

    /** Destination-local behavior installed by successful bootstrap. */
    public java.util.Optional<ProtosObjectValue> currentBehavior() {
        return java.util.Optional.ofNullable(currentBehavior.get());
    }

    /**
     * Installs the exact bootstrap result and attempts the unique READY cutover.
     *
     * <p>If termination wins the lifecycle race first, this returns false and never reopens
     * the Actor. The installed behavior reference is never replaced.
     */
    public synchronized boolean completeInitialization(ProtosObjectValue behavior) {
        Objects.requireNonNull(behavior, "behavior");
        while (true) {
            if (lifecycle.get() != LifecycleState.INITIALIZING) {
                return false;
            }
            ProtosObjectValue existing = currentBehavior.get();
            if (existing != null) {
                if (existing != behavior) {
                    throw new IllegalStateException("Actor initial behavior is already fixed");
                }
                return markReady();
            }
            if (currentBehavior.compareAndSet(null, behavior)) {
                return markReady();
            }
        }
    }

    long incarnationIdentityForRuntime() {
        return incarnationIdentity;
    }

    /**
     * Establishes the unique INITIALIZING -> READY cutover.
     *
     * @return true only for the call that performs the transition
     */
    public synchronized boolean markReady() {
        while (true) {
            LifecycleState current = lifecycle.get();
            switch (current) {
                case INITIALIZING -> {
                    if (lifecycle.compareAndSet(
                            LifecycleState.INITIALIZING, LifecycleState.READY)) {
                        mailbox.signalRuntime();
                        return true;
                    }
                }
                case READY -> {
                    return false;
                }
                case TERMINATING, TERMINATED -> {
                    return false;
                }
            }
        }
    }

    /**
     * Establishes the irreversible transition into termination.
     *
     * @return true only for the call that performs the cutover
     */
    public boolean beginTermination() {
        boolean transitioned = false;
        synchronized (this) {
            while (true) {
                LifecycleState current = lifecycle.get();
                switch (current) {
                    case INITIALIZING, READY -> {
                        if (lifecycle.compareAndSet(current, LifecycleState.TERMINATING)) {
                            transitioned = true;
                            break;
                        }
                    }
                    case TERMINATING, TERMINATED -> {
                        return false;
                    }
                }
                if (transitioned) break;
            }
        }

        // The cutover owns cancellation, but producer callbacks may acquire another Actor's
        // admission lock. Never hold this Actor monitor while requesting those cancellations.
        deliveryAdmission.lifecycleChanged();
        executionDomain.actorTerminationBegun();
        mailbox.signalRuntime();
        synchronized (this) {
            terminationCutoverCleanupComplete = true;
        }
        return true;
    }

    /** Establishes graceful/fatal termination and completes it once task cleanup permits. */
    public void requestTerminationForRuntime() {
        beginTermination();
        tryCompleteTerminationForRuntime();
    }

    void tryCompleteTerminationForRuntime() {
        if (lifecycle.get() == LifecycleState.TERMINATING
                && !executionDomain.hasLiveTasksForRuntime()) {
            markTerminated();
        }
    }

    boolean registerTerminationObservationForRuntime(
            ProtosActorTerminationObservation observation) {
        Objects.requireNonNull(observation, "observation");
        synchronized (this) {
            if (lifecycle.get() == LifecycleState.TERMINATED) {
                return false;
            }
            terminationObservers.add(observation);
            return true;
        }
    }

    void unregisterTerminationObservationForRuntime(
            ProtosActorTerminationObservation observation) {
        synchronized (this) {
            terminationObservers.remove(observation);
        }
    }

    /**
     * Completes an already-started termination.
     *
     * @return true only for the call that performs TERMINATING -> TERMINATED
     */
    public boolean markTerminated() {
        List<ProtosActorTerminationObservation> notify;
        synchronized (this) {
            LifecycleState current = lifecycle.get();
            if (current == LifecycleState.TERMINATED) {
                return false;
            }
            if (current != LifecycleState.TERMINATING) {
                throw new IllegalStateException(
                        "Actor termination must begin before completion");
            }
            if (!terminationCutoverCleanupComplete
                    || executionDomain.hasLiveTasksForRuntime()) {
                return false;
            }
            lifecycle.set(LifecycleState.TERMINATED);
            notify = new ArrayList<>(terminationObservers);
            terminationObservers.clear();
        }
        deliveryAdmission.lifecycleChanged();
        mailbox.signalRuntime();
        for (ProtosActorTerminationObservation observation : notify) {
            observation.targetTerminated();
        }
        return true;
    }
}
