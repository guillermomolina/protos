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
import com.guillermomolina.protos.runtime.ProtosProcessExecutionHost;
import com.guillermomolina.protos.runtime.ProtosProcessRuntime;
import com.oracle.truffle.api.source.Source;
import java.util.Objects;
import java.util.function.Supplier;
import org.graalvm.polyglot.Engine;

/**
 * Current Truffle hosting placement for one semantic Protos Process.
 *
 * <p>Object identity here is deliberately not Process identity. The semantic Process remains the
 * supplied {@link ProtosProcessRuntime}; this wrapper only routes its host execution through one
 * multithread Polyglot Context and closes that Context after semantic Process termination.
 */
public final class ProtosPolyglotProcessContext implements ProtosProcessExecutionHost {
    private final ProtosPolyglotRuntimeHost runtimeHost;
    private final ProtosProcessRuntime process;
    private final ProtosPolyglotExecutionContext context;

    ProtosPolyglotProcessContext(
            ProtosPolyglotRuntimeHost runtimeHost,
            ProtosProcessRuntime process,
            ProtosPolyglotExecutionContext context) {
        this.runtimeHost = Objects.requireNonNull(runtimeHost, "runtimeHost");
        this.process = Objects.requireNonNull(process, "process");
        this.context = Objects.requireNonNull(context, "context");
    }

    public ProtosExecutionOutcome execute(Source source, ProtosActivation activation) {
        Objects.requireNonNull(source, "source");
        requireActivationProcess(activation);
        return context.execute(source, activation);
    }

    /** Persistent top-level evaluation for REPL-like drivers inside this exact Process Context. */
    public Object evaluatePersistent(Source source, ProtosActivation activation) {
        Objects.requireNonNull(source, "source");
        requireActivationProcess(activation);
        return context.evaluatePersistent(source, activation);
    }

    private void requireActivationProcess(ProtosActivation activation) {
        Objects.requireNonNull(activation, "activation");
        ProtosProcessRuntime activationProcess =
                activation.executionDomain()
                        .currentActorForRuntime()
                        .flatMap(actor -> actor.processForRuntime())
                        .orElse(null);
        if (activationProcess != process) {
            throw new IllegalArgumentException(
                    "entry activation belongs to another or unhosted Protos Process");
        }
    }

    @Override
    public <T> T callForRuntime(Supplier<T> action) {
        return context.callEntered(Objects.requireNonNull(action, "action"));
    }

    @Override
    public void processTerminatedForRuntime() {
        try {
            context.requestClose();
        } catch (RuntimeException | Error failure) {
            runtimeHost.recordContextCloseFailure(failure);
        }
    }

    Engine engineForTesting() {
        return context.engineForTesting();
    }

    ProtosLanguageContext currentLanguageContextForTesting() {
        return callForRuntime(ProtosLanguageContext::current);
    }

    boolean isClosedForTesting() {
        return context.isClosedForTesting();
    }
}
