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

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosActor;
import com.guillermomolina.protos.runtime.ProtosActorRefValue;
import com.guillermomolina.protos.runtime.ProtosCoreErrors;
import com.guillermomolina.protos.runtime.ProtosIdentity;
import com.guillermomolina.protos.runtime.ProtosModuleKey;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosProcessRuntime;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
                    actor.isRootActorForRuntime()
                            ? moduleRuntime.loadCanonicalInitialModule(
                                    canonicalModuleKey,
                                    activation,
                                    rootBootstrapLocals(actor, prelude))
                            : moduleRuntime.loadCanonicalModule(
                                    canonicalModuleKey, activation);
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
            terminateFailedInitialization(actor, failure.error());
            throw failure;
        }
    }

    private static Map<String, Object> rootBootstrapLocals(
            ProtosActor actor, ProtosPrelude prelude) {
        ProtosProcessRuntime process =
                actor.processForRuntime()
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "RootActor lost its owning Process runtime"));

        LinkedHashMap<String, Object> locals = new LinkedHashMap<>();
        locals.put(
                "process",
                process.provisionCapabilityForRuntime(
                        prelude.processPrototype()));
        process.rootFilesystemForRuntime()
                .ifPresent(filesystem -> locals.put("filesystem", filesystem));
        return Map.copyOf(locals);
    }

    private static ProtosSignalException bootstrapError(ProtosActivation activation) {
        return new ProtosSignalException(ProtosCoreErrors.newError(activation));
    }

    private static void terminateFailedInitialization(ProtosActor actor, Object failure) {
        actor.failForRuntime(failure);
    }
}
