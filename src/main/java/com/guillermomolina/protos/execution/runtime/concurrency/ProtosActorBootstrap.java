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
import com.guillermomolina.protos.runtime.ProtosActorScheduler;
import com.guillermomolina.protos.runtime.ProtosActorValueTransfer;
import com.guillermomolina.protos.runtime.ProtosCoreErrors;
import com.guillermomolina.protos.runtime.ProtosIdentity;
import com.guillermomolina.protos.runtime.modules.ProtosModuleKey;
import com.guillermomolina.protos.runtime.ProtosNumericValueSupport;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosProcessRuntime;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

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
            // The binding runs in its own module's environment, as every later message turn of
            // this Actor does: specifiers it resolves are relative to that canonical module.
            ProtosActivation invocation =
                    prelude.newModuleActivation(
                            actor.moduleState(),
                            canonicalModuleKey,
                            bootstrapContext,
                            actor.executionDomain());
            Object result =
                    ProtosInvocation.invoke(
                            bootstrapBinding,
                            List.copyOf(
                                    ProtosActorValueTransfer.materializeArguments(
                                            transferredArguments, invocation)),
                            invocation);
            // A represented Number is a semantic value, not an ordinary
            // Actor behavior object. Check semantic membership, not the Java
            // representation: BigInteger, Fraction and Complex all extend
            // ProtosObjectValue, while ordinary delegates remain admissible.
            if (!(result instanceof ProtosObjectValue behavior)
                    || ProtosNumericValueSupport.isCurrentNumber(result)) {
                throw bootstrapError(invocation);
            }
            actor.bindMessageEnvironmentForRuntime(prelude, canonicalModuleKey);
            return actor.completeInitialization(behavior);
        } catch (ProtosSignalException failure) {
            terminateFailedInitialization(actor, failure.error());
            throw failure;
        }
    }

    /**
     * D225-B1: starts the explicitly selected message-service bootstrap of the existing RootActor
     * of a hosted Process.
     *
     * <p>No new Actor is created and no second bootstrap algorithm exists: the RootActor is
     * attached to the Process host's ordinary Actor scheduler and {@link #initialize} runs as that
     * Actor's control turn, exactly as for a spawned Actor. It therefore executes entered in the
     * Process Context, loads the canonical initial module once with the RootActor's
     * bootstrap-local slots, selects {@code bindingName} as a local slot of that module, validates
     * the result and performs the single READY cutover. After READY the same scheduler dispatches
     * ordinary external messages against the installed behavior.
     *
     * <p>The returned Future completes with the result of {@link #initialize}: true for the READY
     * cutover, false when initialization failed with a Protos Error (the RootActor failure has
     * already begun Process termination) or termination won first. A host failure inside the turn
     * requests Process termination and completes it exceptionally. The control turn is the only
     * completion path; it is submitted while the RootActor is still INITIALIZING and before any
     * guest code of this Process runs, so it is never discarded unexecuted.
     */
    static CompletableFuture<Boolean> startRootService(
            ProtosProcessRuntime process,
            ProtosPrelude prelude,
            ProtosModuleKey canonicalModuleKey,
            String bindingName) {
        Objects.requireNonNull(process, "process");
        Objects.requireNonNull(prelude, "prelude");
        Objects.requireNonNull(canonicalModuleKey, "canonicalModuleKey");
        Objects.requireNonNull(bindingName, "bindingName");
        ProtosActor root = process.rootActorForRuntime();
        if (root.lifecycleState() != ProtosActor.LifecycleState.INITIALIZING) {
            throw new IllegalStateException(
                    "RootActor service bootstrap requires INITIALIZING lifecycle");
        }
        ProtosActorScheduler scheduler =
                process.executionHostForRuntime()
                        .flatMap(host -> host.actorSchedulerForRuntime())
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "RootActor service bootstrap requires a hosted Process"));
        ProtosModuleRuntime moduleRuntime = ProtosStandardImportProtocol.runtimeForPrelude(prelude);
        if (moduleRuntime == null) {
            throw new IllegalStateException("Core prelude has no standard import facility");
        }
        ProtosActorBootstrap bootstrap = new ProtosActorBootstrap(moduleRuntime);
        CompletableFuture<Boolean> outcome = new CompletableFuture<>();
        Runnable turn =
                () -> {
                    try {
                        outcome.complete(
                                bootstrap.initialize(
                                        root, prelude, canonicalModuleKey, bindingName, List.of()));
                    } catch (ProtosSignalException initializationFailure) {
                        // initialize already made this RootActor failure terminate the Process.
                        outcome.complete(false);
                    } catch (RuntimeException | Error hostFailure) {
                        process.requestTerminationForRuntime();
                        outcome.completeExceptionally(hostFailure);
                        if (hostFailure instanceof Error error) throw error;
                    }
                };
        scheduler.attach(root);
        try {
            scheduler.submitControl(root, turn);
        } catch (RuntimeException admissionFailure) {
            scheduler.detach(root);
            throw admissionFailure;
        }
        return outcome;
    }

    /**
     * Creates one non-importable standalone RootActor entry activation with the exact E2
     * bootstrap-local authority model before any entry source expression executes.
     */
    static ProtosActivation newStandaloneInitialActivation(
            ProtosProcessRuntime process, ProtosPrelude prelude) {
        Objects.requireNonNull(process, "process");
        Objects.requireNonNull(prelude, "prelude");
        if (process.lifecycleState() != ProtosProcessRuntime.LifecycleState.RUNNING) {
            throw new IllegalStateException(
                    "standalone entry bootstrap requires a running Process");
        }
        ProtosActor root = process.rootActorForRuntime();
        if (root.lifecycleState() != ProtosActor.LifecycleState.INITIALIZING) {
            throw new IllegalStateException(
                    "standalone RootActor entry requires INITIALIZING lifecycle");
        }
        ProtosObjectValue moduleContext = prelude.newExecutionContext();
        for (Map.Entry<String, Object> entry : rootBootstrapLocals(root, prelude).entrySet()) {
            moduleContext.createLocalSlot(entry.getKey(), entry.getValue());
        }
        ProtosActivation activation =
                prelude.newModuleActivation(
                        root.moduleState(), null, moduleContext, root.executionDomain());
        ProtosActorRefValue current =
                activation.executionDomain().currentActorReference().orElseThrow(
                        () -> new IllegalStateException(
                                "standalone RootActor execution domain lost its owner"));
        if (!ProtosIdentity.identical(current, root.reference())) {
            throw new IllegalStateException("standalone entry activation belongs to another Actor");
        }
        return activation;
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
        process.rootNetworkForRuntime()
                .ifPresent(
                        network -> {
                            if (network.representedDelegationParent(prelude)
                                    != prelude.networkPrototype()) {
                                throw new IllegalStateException(
                                        "RootActor Network grant belongs to another Core prelude");
                            }
                            locals.put("network", network);
                        });
        process.rootResolverForRuntime()
                .ifPresent(
                        resolver -> {
                            if (resolver.representedDelegationParent(prelude)
                                    != prelude.resolverPrototypeForRuntime()) {
                                throw new IllegalStateException(
                                        "RootActor Resolver grant belongs to another Core prelude");
                            }
                            locals.put("resolver", resolver);
                        });
        process.rootResourcesForRuntime()
                .ifPresent(
                        resources -> {
                            if (!resources.isFrozen()) {
                                throw new IllegalStateException(
                                        "RootActor resources grant must be frozen");
                            }
                            if (resources.parent().orElse(null) != prelude.mapPrototype()) {
                                throw new IllegalStateException(
                                        "RootActor resources grant belongs to another Core prelude");
                            }
                            locals.put("resources", resources);
                        });
        return Map.copyOf(locals);
    }

    private static ProtosSignalException bootstrapError(ProtosActivation activation) {
        return new ProtosSignalException(ProtosCoreErrors.newError(activation));
    }

    private static void terminateFailedInitialization(ProtosActor actor, Object failure) {
        actor.failForRuntime(failure);
    }
}
