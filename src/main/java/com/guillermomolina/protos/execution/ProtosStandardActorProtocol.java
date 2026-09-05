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
import com.guillermomolina.protos.runtime.ProtosActorValueTransfer;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosCoreErrors;
import com.guillermomolina.protos.runtime.ProtosModuleKey;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.guillermomolina.protos.runtime.ProtosStringValue;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;

/** Installs the Core v0.1 public Actor entry object over the existing Actor runtime layers. */
public final class ProtosStandardActorProtocol {
    private final ProtosModuleRuntime moduleRuntime;
    private final ProtosActorBootstrap actorBootstrap;
    private final Executor bootstrapExecutor;
    private final ProtosObjectValue actorRefPrototype;

    public ProtosStandardActorProtocol(ProtosModuleRuntime moduleRuntime) {
        this(moduleRuntime, command -> Thread.startVirtualThread(command));
    }

    /**
     * Runtime/test constructor. The executor is internal machinery and is never exposed to Protos.
     * Callers must provide an executor whose execute method only schedules the command; it must not
     * wait for command completion.
     */
    public ProtosStandardActorProtocol(
            ProtosModuleRuntime moduleRuntime, Executor bootstrapExecutor) {
        this.moduleRuntime = Objects.requireNonNull(moduleRuntime, "moduleRuntime");
        this.actorBootstrap = new ProtosActorBootstrap(moduleRuntime);
        this.bootstrapExecutor = Objects.requireNonNull(bootstrapExecutor, "bootstrapExecutor");
        this.actorRefPrototype =
                new ProtosObjectValue(ProtosObjectValue.rootObject()).freeze();
    }

    /** Creates the ordinary frozen Core prelude object whose local surface is spawn/current. */
    public ProtosObjectValue createActorObject() {
        ProtosObjectValue actorObject = new ProtosObjectValue(ProtosObjectValue.rootObject());
        actorObject.createLocalSlot(
                "spawn",
                ProtosClosureValue.nativeClosure(
                        (activation, supplied) -> spawn(activation, supplied)));
        actorObject.createLocalSlot(
                "current",
                ProtosClosureValue.nativeClosure(
                        (activation, supplied) -> current(activation, supplied)));
        return actorObject.freeze();
    }

    private Object spawn(ProtosActivation activation, List<?> supplied) {
        Objects.requireNonNull(activation, "activation");
        Objects.requireNonNull(supplied, "supplied");
        if (supplied.size() < 2) {
            throw error(activation);
        }

        Object moduleSpecifier = supplied.get(0);
        Object bindingValue = supplied.get(1);
        if (!(moduleSpecifier instanceof ProtosStringValue)
                || !(bindingValue instanceof ProtosStringValue)) {
            throw error(activation);
        }

        ProtosPrelude prelude =
                activation.prelude()
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "Actor.spawn requires an owning Core prelude"));
        ProtosModuleKey moduleKey = moduleRuntime.resolveModuleKey(moduleSpecifier, activation);
        String bindingName = ((ProtosStringValue) bindingValue).value();
        List<Object> transferredArguments =
                ProtosActorValueTransfer.snapshotArguments(
                        supplied.subList(2, supplied.size()), activation);

        // Semantic creation cutover: nothing above this point creates an Actor or ActorRef.
        ProtosActor actor = new ProtosActor(actorRefPrototype);
        ProtosActorRefValue reference = actor.reference();
        Runnable bootstrap =
                () -> {
                    try {
                        actorBootstrap.initialize(
                                actor,
                                prelude,
                                moduleKey,
                                bindingName,
                                transferredArguments);
                    } catch (ProtosSignalException initializationFailure) {
                        // I011-3 already terminalized this same incarnation. Core exposes no
                        // implicit remote Error channel for initialization failure.
                    } catch (RuntimeException runtimeFailure) {
                        terminateAfterCutover(actor);
                    }
                };
        try {
            bootstrapExecutor.execute(bootstrap);
        } catch (RuntimeException admissionFailure) {
            // Capacity/admission after the semantic creation cutover cannot retroactively fail
            // spawn. Preserve the already-created incarnation and make it terminal instead.
            terminateAfterCutover(actor);
        }
        return reference;
    }

    private static Object current(ProtosActivation activation, List<?> supplied) {
        Objects.requireNonNull(activation, "activation");
        Objects.requireNonNull(supplied, "supplied");
        if (!supplied.isEmpty()) {
            throw error(activation);
        }
        return activation.executionDomain()
                .currentActorReference()
                .orElseThrow(
                        () ->
                                new IllegalStateException(
                                        "Actor.current requires execution inside an Actor incarnation"));
    }

    private static void terminateAfterCutover(ProtosActor actor) {
        while (true) {
            ProtosActor.LifecycleState state = actor.lifecycleState();
            switch (state) {
                case INITIALIZING, READY -> actor.beginTermination();
                case TERMINATING -> {
                    if (actor.markTerminated()) {
                        actor.executionDomain().actorTerminated();
                    }
                    return;
                }
                case TERMINATED -> {
                    return;
                }
            }
        }
    }

    private static ProtosSignalException error(ProtosActivation activation) {
        return new ProtosSignalException(ProtosCoreErrors.newError(activation));
    }
}
