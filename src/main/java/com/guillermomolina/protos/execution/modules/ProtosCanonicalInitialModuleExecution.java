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
import com.guillermomolina.protos.runtime.ProtosActorModuleState;
import com.guillermomolina.protos.runtime.modules.ProtosModuleKey;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosProcessRuntime;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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

        if (key.isForeign()) {
            throw new IOException("canonical initial module must be source-backed");
        }
        // D242: the initial activation Context is always the private lexical Context. A MODULE
        // receives a distinct fresh public object as its cached import result; a SOURCE caches its
        // complete Context.
        ProtosObjectValue lexicalContext = initialActivation.context();
        final ProtosObjectValue instance;
        try {
            prelude.installStandardModuleMembersForRuntime(key, lexicalContext);
            instance = key.isModule() ? prelude.newPublicModuleObject() : lexicalContext;
        } catch (RuntimeException failure) {
            throw new IOException("canonical initial module preparation failed", failure);
        }
        ProtosActorModuleState.ModuleRecord record =
                new ProtosActorModuleState.ModuleRecord(instance);
        state.put(key, record); // normative cache-before-execute point
        ProtosActivation activation =
                key.isModule()
                        ? prelude.newModuleInitializerActivation(
                                state,
                                key,
                                lexicalContext,
                                instance,
                                initialActivation.executionDomain())
                        : prelude.newModuleActivation(
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
            throw new IOException(
                    "canonical initial module execution failed: "
                            + failure.getClass().getName() + ": " + failure.getMessage(),
                    failure);
        }
    }

    /**
     * Uses public parse for every hosted Process. Deliberately unhosted Java semantic harnesses
     * receive a fresh host-owned Polyglot context so source execution still terminates on the
     * canonical Bytecode-backed public parse path.
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
            try (ProtosPolyglotExecutionContext executionContext =
                    ProtosPolyglotExecutionContext.open(
                            InputStream.nullInputStream(),
                            OutputStream.nullOutputStream(),
                            OutputStream.nullOutputStream())) {
                return executionContext.executeModuleSource(source, activation);
            }
        }
        return process.callInExecutionHostForRuntime(
                () -> {
                    ProtosLanguageContext languageContext = ProtosLanguageContext.current();
                    return ProtosRootTaskExecution.execute(
                            languageContext.parsePublic(
                                    languageContext.materializeModuleSource(source)),
                            activation);
                });
    }
}
