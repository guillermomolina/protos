/*
 * THE LICENSED WORK IS PROVIDED UNDER THE TERMS OF THE ADAPTIVE PUBLIC LICENSE
 * ("LICENSE") AS FIRST COMPLETED BY: Guillermo Adrián Molina. See LICENSE.TXT.
 */
package com.guillermomolina.protos.runtime;

import java.util.Objects;

/**
 * Semantic ActorRef value bound permanently to one concrete Actor incarnation.
 *
 * <p>This slice intentionally exposes no public Actor operations. Future Actor protocol slices can
 * use the retained local incarnation while transfer/routing layers may rematerialize wrappers that
 * preserve the same semantic incarnation identity.
 */
public final class ProtosActorRefValue implements ProtosRepresentedValue {
    private final ProtosObjectValue prototype;
    private final ProtosActor actor;

    ProtosActorRefValue(ProtosObjectValue prototype, ProtosActor actor) {
        this.prototype = Objects.requireNonNull(prototype, "prototype");
        this.actor = Objects.requireNonNull(actor, "actor");
    }

    boolean denotesSameIncarnation(ProtosActorRefValue other) {
        return other != null
                && incarnationIdentityForRuntime() == other.incarnationIdentityForRuntime();
    }

    long incarnationIdentityForRuntime() {
        return actor.incarnationIdentityForRuntime();
    }

    ProtosActor localActorForRuntime() {
        return actor;
    }

    @Override
    public Object representedDelegationParent(ProtosPrelude ignored) {
        return prototype;
    }
}
