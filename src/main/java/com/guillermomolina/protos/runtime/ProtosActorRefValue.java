/*
 * THE LICENSED WORK IS PROVIDED UNDER THE TERMS OF THE ADAPTIVE PUBLIC LICENSE
 * ("LICENSE") AS FIRST COMPLETED BY: Guillermo Adrián Molina. See LICENSE.TXT.
 */
package com.guillermomolina.protos.runtime;

import java.util.Objects;

/**
 * Semantic ActorRef capability permanently bound to one concrete Actor incarnation.
 *
 * <p>The Protos-visible value is a wrapper around an opaque runtime target descriptor. Actor
 * boundary rematerialization creates a fresh wrapper while preserving the same incarnation
 * identity and communication target. The mutable Actor itself is never exposed as Protos state.
 */
public final class ProtosActorRefValue implements ProtosRepresentedValue {
    private final ProtosObjectValue prototype;
    private final Target target;

    ProtosActorRefValue(ProtosObjectValue prototype, ProtosActor actor) {
        this(
                prototype,
                new Target(
                        Objects.requireNonNull(actor, "actor").incarnationIdentityForRuntime(),
                        actor));
    }

    private ProtosActorRefValue(ProtosObjectValue prototype, Target target) {
        this.prototype = Objects.requireNonNull(prototype, "prototype");
        this.target = Objects.requireNonNull(target, "target");
    }

    boolean denotesSameIncarnation(ProtosActorRefValue other) {
        return other != null
                && incarnationIdentityForRuntime() == other.incarnationIdentityForRuntime();
    }

    long incarnationIdentityForRuntime() {
        return target.incarnationIdentity;
    }

    ProtosActor localActorForRuntime() {
        return target.localActor;
    }

    /**
     * Rematerializes this communication capability for an Actor transfer boundary.
     *
     * <p>This is intentionally package-private infrastructure. I011-6 will compose it into full
     * graph snapshot/pass-by-value transfer. The operation does not copy the Actor target, retarget
     * the reference, or expose any public transfer primitive.
     */
    ProtosActorRefValue rematerializeForActorTransfer() {
        return new ProtosActorRefValue(prototype, target);
    }

    @Override
    public Object representedDelegationParent(ProtosPrelude ignored) {
        return prototype;
    }

    /** Opaque runtime communication-target descriptor, never a Protos-visible object. */
    private static final class Target {
        private final long incarnationIdentity;
        private final ProtosActor localActor;

        private Target(long incarnationIdentity, ProtosActor localActor) {
            if (incarnationIdentity <= 0) {
                throw new IllegalArgumentException("ActorRef incarnation identity must be positive");
            }
            this.incarnationIdentity = incarnationIdentity;
            this.localActor = Objects.requireNonNull(localActor, "localActor");
        }
    }
}
