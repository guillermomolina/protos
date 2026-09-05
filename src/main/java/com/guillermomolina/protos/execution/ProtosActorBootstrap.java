/*
 * THE LICENSED WORK IS PROVIDED UNDER THE TERMS OF THE ADAPTIVE PUBLIC LICENSE
 * ("LICENSE") AS FIRST COMPLETED BY: Guillermo Adrián Molina. See LICENSE.TXT.
 */
package com.guillermomolina.protos.execution;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosActor;
import com.guillermomolina.protos.runtime.ProtosActorRefValue;
import com.guillermomolina.protos.runtime.ProtosCoreErrors;
import com.guillermomolina.protos.runtime.ProtosIdentity;
import com.guillermomolina.protos.runtime.ProtosModuleKey;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import java.util.List;
import java.util.Objects;

/**
 * Destination-local Actor bootstrap machinery.
 *
 * <p>This layer deliberately starts after public-spawn synchronous validation and Actor-boundary
 * argument transfer. It consumes a canonical ModuleKey and already-transferred destination-local
 * arguments, so it never re-resolves the creator's module spelling and never shares creator
 * mutable state. The public Actor.spawn/current surface is installed by a later slice once its
 * remaining transfer/runtime prerequisites are present.
 */
public final class ProtosActorBootstrap {
    private final ProtosModuleRuntime moduleRuntime;

    public ProtosActorBootstrap(ProtosModuleRuntime moduleRuntime) {
        this.moduleRuntime = Objects.requireNonNull(moduleRuntime, "moduleRuntime");
    }

    /**
     * Runs bootstrap for one existing Actor incarnation.
     *
     * @return true only when this invocation performs the INITIALIZING -> READY cutover; false
     *     when a concurrent termination cutover wins first
     */
    public boolean initialize(
            ProtosActor actor,
            ProtosPrelude prelude,
            ProtosModuleKey canonicalModuleKey,
            String bindingName,
            List<?> transferredArguments) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(prelude, "prelude");
        Objects.requireNonNull(canonicalModuleKey, "canonicalModuleKey");
        Objects.requireNonNull(bindingName, "bindingName");
        Objects.requireNonNull(transferredArguments, "transferredArguments");

        if (actor.lifecycleState() != ProtosActor.LifecycleState.INITIALIZING) {
            throw new IllegalStateException("Actor bootstrap requires INITIALIZING lifecycle");
        }

        ProtosObjectValue bootstrapContext = prelude.newExecutionContext();
        ProtosActivation activation =
                prelude.newModuleActivation(
                        actor.moduleState(),
                        null,
                        bootstrapContext,
                        actor.executionDomain());
        ProtosActorRefValue current =
                activation.executionDomain().currentActorReference().orElseThrow(
                        () -> new IllegalStateException("Actor execution domain lost its owner"));
        if (!ProtosIdentity.identical(current, actor.reference())) {
            throw new IllegalStateException("bootstrap activation belongs to another Actor");
        }

        try {
            ProtosObjectValue module =
                    moduleRuntime.loadCanonicalModule(canonicalModuleKey, activation);
            Object bootstrapBinding =
                    module.readLocalSlot(bindingName)
                            .orElseThrow(() -> bootstrapError(activation));
            Object result =
                    ProtosInvocation.invoke(
                            bootstrapBinding,
                            List.copyOf(transferredArguments),
                            activation);
            if (!(result instanceof ProtosObjectValue behavior)) {
                throw bootstrapError(activation);
            }
            actor.bindMessageEnvironmentForRuntime(prelude, canonicalModuleKey);
            return actor.completeInitialization(behavior);
        } catch (ProtosSignalException failure) {
            terminateFailedInitialization(actor);
            throw failure;
        }
    }

    private static ProtosSignalException bootstrapError(ProtosActivation activation) {
        return new ProtosSignalException(ProtosCoreErrors.newError(activation));
    }

    private static void terminateFailedInitialization(ProtosActor actor) {
        while (true) {
            ProtosActor.LifecycleState state = actor.lifecycleState();
            switch (state) {
                case INITIALIZING -> {
                    actor.beginTermination();
                }
                case TERMINATING -> {
                    if (actor.markTerminated()) {
                        actor.executionDomain().actorTerminated();
                    }
                    return;
                }
                case TERMINATED -> {
                    return;
                }
                case READY ->
                        throw new IllegalStateException(
                                "bootstrap failure observed after Actor became READY");
            }
        }
    }
}
