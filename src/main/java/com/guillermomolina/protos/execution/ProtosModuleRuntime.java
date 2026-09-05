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
        Objects.requireNonNull(caller, "caller");
        if (!(specifier instanceof ProtosStringValue semanticString)) {
            throw new ProtosSignalException(ProtosCoreErrors.newError(caller));
        }

        final ProtosModuleKey key;
        try {
            key = Objects.requireNonNull(
                    resolver.resolve(semanticString.value(), caller.currentModuleKey()),
                    "module resolver returned null ModuleKey");
        } catch (ProtosSignalException signal) {
            throw signal;
        } catch (Exception hostFailure) {
            throw new ProtosSignalException(ProtosCoreErrors.newError(caller));
        }

        ProtosActorModuleState actorState = caller.actorModuleState();
        ProtosActorModuleState.ModuleRecord existing = actorState.lookup(key).orElse(null);
        if (existing != null) {
            return existing.instance();
        }

        ProtosPrelude prelude = caller.prelude().orElseThrow(
                () -> new IllegalStateException("module import requires an owning Core prelude"));
        ProtosObjectValue moduleInstance = prelude.newExecutionContext();
        ProtosActorModuleState.ModuleRecord record = new ProtosActorModuleState.ModuleRecord(moduleInstance);
        actorState.put(key, record); // normative cache-before-execute point

        try {
            String source = Objects.requireNonNull(resolver.loadSource(key), "module source");
            ProtosActivation moduleActivation = prelude.newModuleActivation(actorState, key, moduleInstance);
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
