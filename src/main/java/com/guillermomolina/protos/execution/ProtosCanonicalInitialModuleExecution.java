/*
 * THE LICENSED WORK IS PROVIDED UNDER THE TERMS OF THE ADAPTIVE PUBLIC LICENSE
 * ("LICENSE") AS FIRST COMPLETED BY: Guillermo Adrián Molina. See LICENSE.TXT.
 */
package com.guillermomolina.protos.execution;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosActorModuleState;
import com.guillermomolina.protos.runtime.ProtosModuleKey;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosProcessRuntime;
import java.io.IOException;
import java.util.Objects;

/** Executes one already-canonical initial module in an already-created RootActor context. */
public final class ProtosCanonicalInitialModuleExecution {
    private ProtosCanonicalInitialModuleExecution() {}

    public static ProtosExecutionOutcome execute(
            ProtosPrelude prelude,
            ProtosModuleResolver resolver,
            ProtosModuleKey key,
            ProtosActivation initialActivation)
            throws IOException {
        Objects.requireNonNull(prelude, "prelude");
        Objects.requireNonNull(resolver, "resolver");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(initialActivation, "initialActivation");

        ProtosActorModuleState state = initialActivation.actorModuleState();
        if (state.lookup(key).isPresent()) {
            throw new IOException("canonical initial module is already cached");
        }

        final ProtosModuleSource source;
        try {
            source =
                    Objects.requireNonNull(resolver.loadSource(key), "module source")
                            .requireKey(key);
        } catch (IOException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new IOException("canonical initial module preparation failed", failure);
        }

        ProtosObjectValue instance = initialActivation.context();
        ProtosActorModuleState.ModuleRecord record =
                new ProtosActorModuleState.ModuleRecord(instance);
        state.put(key, record); // normative cache-before-execute point
        ProtosActivation activation =
                prelude.newModuleActivation(
                        state,
                        key,
                        instance,
                        initialActivation.executionDomain());

        try {
            ProtosExecutionOutcome outcome = executeSource(source, activation);
            if (outcome.state() == ProtosExecutionOutcome.State.COMPLETED) {
                record.markReady();
            } else {
                state.removeIfSame(key, record);
            }
            return outcome;
        } catch (RuntimeException failure) {
            state.removeIfSame(key, record);
            throw new IOException("canonical initial module execution failed", failure);
        }
    }

    /**
     * Uses public parse for every hosted Process. The direct branch below is retained solely for
     * deliberately unhosted Java semantic harnesses and is not reachable from a production driver.
     */
    private static ProtosExecutionOutcome executeSource(
            ProtosModuleSource source,
            ProtosActivation activation) {
        ProtosProcessRuntime process =
                activation.executionDomain()
                        .currentActorForRuntime()
                        .flatMap(actor -> actor.processForRuntime())
                        .orElse(null);
        if (process == null || process.executionHostForRuntime().isEmpty()) {
            return ProtosRootTaskExecution.execute(
                    new ProtosSourceCompiler().compile(source),
                    activation);
        }
        return process.callInExecutionHostForRuntime(
                () ->
                        ProtosRootTaskExecution.execute(
                                ProtosLanguageContext.current().parsePublic(source.source()),
                                activation));
    }
}
