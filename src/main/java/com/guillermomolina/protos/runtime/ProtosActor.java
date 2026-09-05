/*
 * THE LICENSED WORK IS PROVIDED UNDER THE TERMS OF THE ADAPTIVE PUBLIC LICENSE
 * ("LICENSE") AS FIRST COMPLETED BY: Guillermo Adrián Molina. See LICENSE.TXT.
 */
package com.guillermomolina.protos.runtime;

import java.util.Objects;
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

    private final long incarnationIdentity;
    private final ProtosActorExecutionDomain executionDomain;
    private final ProtosActorModuleState moduleState;
    private final ProtosActorRefValue reference;
    private final AtomicReference<LifecycleState> lifecycle =
            new AtomicReference<>(LifecycleState.INITIALIZING);

    public ProtosActor(ProtosObjectValue actorRefPrototype) {
        this(
                actorRefPrototype,
                new ProtosActorExecutionDomain(),
                new ProtosActorModuleState());
    }

    ProtosActor(
            ProtosObjectValue actorRefPrototype,
            ProtosActorExecutionDomain executionDomain,
            ProtosActorModuleState moduleState) {
        this.executionDomain =
                Objects.requireNonNull(executionDomain, "executionDomain");
        this.moduleState = Objects.requireNonNull(moduleState, "moduleState");
        long nextIdentity = NEXT_INCARNATION_ID.incrementAndGet();
        if (nextIdentity <= 0) {
            throw new IllegalStateException("Actor incarnation identity space exhausted");
        }
        this.incarnationIdentity = nextIdentity;
        this.reference =
                new ProtosActorRefValue(
                        Objects.requireNonNull(actorRefPrototype, "actorRefPrototype"), this);
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

    long incarnationIdentityForRuntime() {
        return incarnationIdentity;
    }

    /**
     * Establishes the unique INITIALIZING -> READY cutover.
     *
     * @return true only for the call that performs the transition
     */
    public boolean markReady() {
        while (true) {
            LifecycleState current = lifecycle.get();
            switch (current) {
                case INITIALIZING -> {
                    if (lifecycle.compareAndSet(
                            LifecycleState.INITIALIZING, LifecycleState.READY)) {
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
        while (true) {
            LifecycleState current = lifecycle.get();
            switch (current) {
                case INITIALIZING, READY -> {
                    if (lifecycle.compareAndSet(current, LifecycleState.TERMINATING)) {
                        return true;
                    }
                }
                case TERMINATING, TERMINATED -> {
                    return false;
                }
            }
        }
    }

    /**
     * Completes an already-started termination.
     *
     * @return true only for the call that performs TERMINATING -> TERMINATED
     */
    public boolean markTerminated() {
        while (true) {
            LifecycleState current = lifecycle.get();
            switch (current) {
                case TERMINATING -> {
                    if (lifecycle.compareAndSet(
                            LifecycleState.TERMINATING, LifecycleState.TERMINATED)) {
                        return true;
                    }
                }
                case TERMINATED -> {
                    return false;
                }
                case INITIALIZING, READY ->
                        throw new IllegalStateException(
                                "Actor termination must begin before completion");
            }
        }
    }
}
