/*
 * THE LICENSED WORK IS PROVIDED UNDER THE TERMS OF THE ADAPTIVE PUBLIC LICENSE
 * ("LICENSE") AS FIRST COMPLETED BY: Guillermo Adrián Molina. See LICENSE.TXT.
 */
package com.guillermomolina.protos.execution;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosActorModuleState;
import com.guillermomolina.protos.runtime.ProtosCoreErrors;
import com.guillermomolina.protos.runtime.ProtosModuleKey;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.guillermomolina.protos.runtime.ProtosStringValue;
import java.util.Map;
import java.util.Objects;

/** Core module semantics layered over the host-defined resolver boundary. */
public final class ProtosModuleRuntime {
    private final ProtosModuleResolver resolver;
    private final ProtosSourceCompiler compiler;

    public ProtosModuleRuntime(ProtosModuleResolver resolver) {
        this(resolver, new ProtosSourceCompiler());
    }

    ProtosModuleRuntime(ProtosModuleResolver resolver, ProtosSourceCompiler compiler) {
        this.resolver = Objects.requireNonNull(resolver, "resolver");
        this.compiler = Objects.requireNonNull(compiler, "compiler");
    }

    public Object importModule(Object specifier, ProtosActivation caller) {
        return loadCanonicalModule(resolveModuleKey(specifier, caller), caller);
    }

    /** Resolves one exact semantic String in the caller's module-resolution environment. */
    public ProtosModuleKey resolveModuleKey(Object specifier, ProtosActivation caller) {
        Objects.requireNonNull(caller, "caller");
        if (!(specifier instanceof ProtosStringValue semanticString)) {
            throw new ProtosSignalException(ProtosCoreErrors.newError(caller));
        }

        try {
            return Objects.requireNonNull(
                    resolver.resolve(semanticString.value(), caller.currentModuleKey()),
                    "module resolver returned null ModuleKey");
        } catch (ProtosSignalException signal) {
            throw signal;
        } catch (Exception hostFailure) {
            throw new ProtosSignalException(ProtosCoreErrors.newError(caller));
        }
    }

    /**
     * Loads one already-canonical module identity in the caller's Actor-local module state.
     *
     * <p>This deliberately bypasses specifier resolution. Actor bootstrap resolves in the creator
     * before the creation cutover and the destination consumes only the resulting ModuleKey.
     */
    public ProtosObjectValue loadCanonicalModule(
            ProtosModuleKey key, ProtosActivation caller) {
        return loadCanonicalModuleInternal(key, caller, Map.of(), false);
    }

    /**
     * Loads the RootActor initial module after installing bootstrap-local slots and before the
     * first source expression executes.
     *
     * <p>This package-private entry is used only by Actor bootstrap. Ordinary imports always use
     * {@link #loadCanonicalModule} and therefore receive no ambient bootstrap locals. The initial
     * record is still inserted before source execution, preserving cache-before-execute and cycles.
     */
    ProtosObjectValue loadCanonicalInitialModule(
            ProtosModuleKey key,
            ProtosActivation caller,
            Map<String, ?> bootstrapLocals) {
        Objects.requireNonNull(bootstrapLocals, "bootstrapLocals");
        if (bootstrapLocals.isEmpty()) {
            throw new IllegalArgumentException(
                    "RootActor initial module requires bootstrap-local authority");
        }
        return loadCanonicalModuleInternal(key, caller, bootstrapLocals, true);
    }

    private ProtosObjectValue loadCanonicalModuleInternal(
            ProtosModuleKey key,
            ProtosActivation caller,
            Map<String, ?> bootstrapLocals,
            boolean initialBootstrap) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(caller, "caller");
        Objects.requireNonNull(bootstrapLocals, "bootstrapLocals");

        ProtosActorModuleState actorState = caller.actorModuleState();
        ProtosActorModuleState.ModuleRecord existing = actorState.lookup(key).orElse(null);
        if (existing != null) {
            if (initialBootstrap) {
                throw new IllegalStateException(
                        "RootActor initial module was cached before bootstrap-local provisioning");
            }
            return existing.instance();
        }

        ProtosPrelude prelude =
                caller.prelude()
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "module import requires an owning Core prelude"));
        ProtosObjectValue moduleInstance = prelude.newExecutionContext();

        if (initialBootstrap) {
            for (Map.Entry<String, ?> entry : bootstrapLocals.entrySet()) {
                String name = Objects.requireNonNull(entry.getKey(), "bootstrap local name");
                Object value = Objects.requireNonNull(entry.getValue(), "bootstrap local value");
                if (moduleInstance.hasLocalSlot(name)) {
                    throw new IllegalStateException(
                            "duplicate RootActor bootstrap local: " + name);
                }
                moduleInstance.createLocalSlot(name, value);
            }
        }

        ProtosActorModuleState.ModuleRecord record =
                new ProtosActorModuleState.ModuleRecord(moduleInstance);
        actorState.put(key, record); // normative cache-before-execute point

        try {
            String source = Objects.requireNonNull(resolver.loadSource(key), "module source");
            ProtosActivation moduleActivation =
                    prelude.newModuleActivation(
                            actorState,
                            key,
                            moduleInstance,
                            caller.executionDomain());
            compiler.compile(source).call(moduleActivation);
            record.markReady();
            return moduleInstance;
        } catch (ProtosSignalException signal) {
            actorState.removeIfSame(key, record);
            throw signal;
        } catch (Exception hostOrCompilerFailure) {
            actorState.removeIfSame(key, record);
            throw new ProtosSignalException(ProtosCoreErrors.newError(caller));
        }
    }
}
