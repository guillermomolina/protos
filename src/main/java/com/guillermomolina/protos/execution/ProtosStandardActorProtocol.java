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
import com.guillermomolina.protos.runtime.ProtosActorRequest;
import com.guillermomolina.protos.runtime.ProtosActorScheduler;
import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosSendOperationValue;
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
    private final ProtosActorScheduler actorScheduler;
    private final ProtosObjectValue sendOperationPrototype;
    private final ProtosObjectValue actorRefPrototype;

    public ProtosStandardActorProtocol(
            ProtosModuleRuntime moduleRuntime,
            ProtosObjectValue actorRefPrototype,
            ProtosObjectValue sendOperationPrototype) {
        this(
                moduleRuntime,
                new ProtosActorScheduler(),
                actorRefPrototype,
                sendOperationPrototype);
    }

    /**
     * Runtime/test constructor. The executor is internal carrier machinery and is never exposed to
     * Protos. The injected form intentionally uses one scheduler worker for deterministic tests.
     */
    public ProtosStandardActorProtocol(
            ProtosModuleRuntime moduleRuntime,
            Executor bootstrapExecutor,
            ProtosObjectValue actorRefPrototype,
            ProtosObjectValue sendOperationPrototype) {
        this(
                moduleRuntime,
                new ProtosActorScheduler(bootstrapExecutor, 1),
                actorRefPrototype,
                sendOperationPrototype);
    }

    private ProtosStandardActorProtocol(
            ProtosModuleRuntime moduleRuntime,
            ProtosActorScheduler actorScheduler,
            ProtosObjectValue actorRefPrototype,
            ProtosObjectValue sendOperationPrototype) {
        this.moduleRuntime = Objects.requireNonNull(moduleRuntime, "moduleRuntime");
        this.actorBootstrap = new ProtosActorBootstrap(moduleRuntime);
        this.actorScheduler = Objects.requireNonNull(actorScheduler, "actorScheduler");
        validateSourcePrototype(actorRefPrototype, "ActorRef");
        validateSourcePrototype(sendOperationPrototype, "SendOperation");
        this.actorRefPrototype = installActorRefPrototype(actorRefPrototype);
        this.sendOperationPrototype = installSendOperationPrototype(sendOperationPrototype);
    }

    private ProtosObjectValue installActorRefPrototype(ProtosObjectValue prototype) {
        prototype.createLocalSlot(
                "send",
                ProtosClosureValue.nativeClosure(
                        (activation, supplied) -> send(activation, supplied)));
        prototype.createLocalSlot(
                "request",
                ProtosClosureValue.nativeClosure(
                        (activation, supplied) -> request(activation, supplied)));
        prototype.createLocalSlot(
                "stop",
                ProtosClosureValue.nativeClosure(
                        (activation, supplied) -> stop(activation, supplied)));
        prototype.createLocalSlot(
                "termination",
                ProtosClosureValue.nativeClosure(
                        (activation, supplied) -> termination(activation, supplied)));
        return prototype.freeze();
    }

    private static ProtosObjectValue installSendOperationPrototype(
            ProtosObjectValue prototype) {
        prototype.createLocalSlot(
                "cancel",
                ProtosClosureValue.nativeClosure(
                        (activation, supplied) -> cancelSendOperation(activation, supplied)));
        prototype.createLocalSlot(
                "retry",
                ProtosClosureValue.nativeClosure(
                        (activation, supplied) -> retrySendOperation(activation, supplied)));
        return prototype.freeze();
    }

    private static void validateSourcePrototype(
            ProtosObjectValue prototype, String standardName) {
        Objects.requireNonNull(prototype, standardName + " prototype");
        if (prototype.parent().orElse(null) != ProtosObjectValue.rootObject()) {
            throw new IllegalArgumentException(
                    "Core " + standardName + " prototype must delegate directly to Object");
        }
        if (!prototype.isOpen() || !prototype.localSlotsSnapshot().isEmpty()) {
            throw new IllegalArgumentException(
                    "source-created Core " + standardName
                            + " prototype must begin open and without local slots");
        }
    }

    /** Installs host-backed Actor entry operations on the exact source-created Core object. */
    public ProtosObjectValue installActorObject(ProtosObjectValue actorObject) {
        Objects.requireNonNull(actorObject, "actorObject");
        if (actorObject.parent().orElse(null) != ProtosObjectValue.rootObject()) {
            throw new IllegalArgumentException(
                    "Core Actor object must delegate directly to Object");
        }
        if (!actorObject.isOpen() || !actorObject.localSlotsSnapshot().isEmpty()) {
            throw new IllegalArgumentException(
                    "source-created Core Actor object must begin open and without local slots");
        }

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
            actorScheduler.attach(actor);
            actorScheduler.submitControl(actor, bootstrap);
        } catch (RuntimeException admissionFailure) {
            // Scheduler/carrier failure is after the semantic creation cutover and therefore cannot
            // retroactively fail spawn. Preserve the fixed incarnation and make it terminal.
            actorScheduler.detach(actor);
            terminateAfterCutover(actor);
        }
        return reference;
    }

    private Object send(ProtosActivation activation, List<?> supplied) {
        Objects.requireNonNull(activation, "activation");
        Objects.requireNonNull(supplied, "supplied");
        if (!(activation.receiver() instanceof ProtosActorRefValue destination)
                || supplied.isEmpty()) {
            throw error(activation);
        }
        Object selectorValue = supplied.get(0);
        if (!(selectorValue instanceof ProtosStringValue selector)) {
            throw error(activation);
        }

        // Snapshot formation is synchronous and atomic before any admission attempt exists.
        List<Object> snapshot =
                ProtosActorValueTransfer.snapshotArguments(
                        supplied.subList(1, supplied.size()), activation);
        ProtosActorRefValue sender =
                activation.executionDomain()
                        .currentActorReference()
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "ActorRef.send requires execution inside an Actor incarnation"));
        return ProtosSendOperationValue.begin(
                sendOperationPrototype,
                destination,
                sender,
                selector.value(),
                snapshot);
    }

    private Object request(ProtosActivation activation, List<?> supplied) {
        Objects.requireNonNull(activation, "activation");
        Objects.requireNonNull(supplied, "supplied");
        if (!(activation.receiver() instanceof ProtosActorRefValue destination)
                || supplied.isEmpty()) {
            throw error(activation);
        }
        Object selectorValue = supplied.get(0);
        if (!(selectorValue instanceof ProtosStringValue selector)) {
            throw error(activation);
        }

        // Request shares send's synchronous whole-graph snapshot and concrete-Actor admission.
        List<Object> snapshot =
                ProtosActorValueTransfer.snapshotArguments(
                        supplied.subList(1, supplied.size()), activation);
        ProtosActorRefValue sender =
                activation.executionDomain()
                        .currentActorReference()
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "ActorRef.request requires execution inside an Actor incarnation"));
        return ProtosActorRequest.begin(
                destination, sender, selector.value(), snapshot, activation);
    }

    private static Object stop(ProtosActivation activation, List<?> supplied) {
        if (!(activation.receiver() instanceof ProtosActorRefValue reference)
                || !supplied.isEmpty()) {
            throw error(activation);
        }
        reference.requestStopForRuntime();
        return com.guillermomolina.protos.runtime.ProtosNullValue.INSTANCE;
    }

    private static Object termination(ProtosActivation activation, List<?> supplied) {
        if (!(activation.receiver() instanceof ProtosActorRefValue reference)
                || !supplied.isEmpty()) {
            throw error(activation);
        }
        ProtosObjectValue futurePrototype =
                activation.prelude().orElseThrow(
                        () -> new IllegalStateException(
                                "ActorRef.termination requires an owning Core prelude"))
                        .futurePrototype();
        return reference.observeTerminationForRuntime(
                futurePrototype, activation.executionDomain());
    }

    private static Object cancelSendOperation(
            ProtosActivation activation, List<?> supplied) {
        if (!(activation.receiver() instanceof ProtosSendOperationValue operation)
                || !supplied.isEmpty()) {
            throw error(activation);
        }
        return operation.cancelBeforeAcceptance()
                ? ProtosBooleanValue.TRUE
                : ProtosBooleanValue.FALSE;
    }

    private static Object retrySendOperation(
            ProtosActivation activation, List<?> supplied) {
        if (!(activation.receiver() instanceof ProtosSendOperationValue operation)
                || !supplied.isEmpty()) {
            throw error(activation);
        }
        ProtosSendOperationValue retry = operation.retryAfterFailure();
        if (retry == null) {
            throw error(activation);
        }
        return retry;
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
        actor.requestTerminationForRuntime();
    }

    private static ProtosSignalException error(ProtosActivation activation) {
        return new ProtosSignalException(ProtosCoreErrors.newError(activation));
    }
}
