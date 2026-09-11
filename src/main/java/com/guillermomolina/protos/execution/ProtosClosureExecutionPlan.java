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
import com.guillermomolina.protos.semantic.ast.CanonicalClosure;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.source.Source;
import java.util.Objects;
import java.util.Optional;

public final class ProtosClosureExecutionPlan {
    private final ProtosRootFactory rootFactory;
    private final ProtosRootFactory.LazyCallTarget parameterBindingTarget;
    private final ProtosRootFactory.LazyCallTarget bodyTarget;
    private final ProtosBytecodeClosureExecutionPlan bytecodePlan;

    public ProtosClosureExecutionPlan(
            ProtosParameterBindingNode parameterBinding,
            ProtosExpressionNode body) {
        this(parameterBinding, body, ProtosRootFactory.legacy());
    }

    ProtosClosureExecutionPlan(
            ProtosParameterBindingNode parameterBinding,
            ProtosExpressionNode body,
            ProtosRootFactory rootFactory) {
        this.rootFactory = Objects.requireNonNull(rootFactory, "rootFactory");
        this.parameterBindingTarget =
                rootFactory.createLazyCallTarget(
                        Objects.requireNonNull(parameterBinding, "parameterBinding"));
        this.bodyTarget =
                rootFactory.createLazyCallTarget(
                        Objects.requireNonNull(body, "body"));
        this.bytecodePlan = null;
    }

    private ProtosClosureExecutionPlan(
            ProtosBytecodeClosureExecutionPlan bytecodePlan) {
        this.rootFactory = null;
        this.parameterBindingTarget = null;
        this.bodyTarget = null;
        this.bytecodePlan =
                Objects.requireNonNull(bytecodePlan, "bytecodePlan");
    }

    static ProtosClosureExecutionPlan bytecode(
            CanonicalClosure definition,
            ProtosLanguage language,
            Source source) {
        return new ProtosClosureExecutionPlan(
                new ProtosBytecodeClosureExecutionPlan(
                        definition,
                        language,
                        source));
    }

    static ProtosClosureExecutionPlan bytecode(
            CanonicalClosure definition,
            ProtosLanguage language,
            Source source,
            ProtosBytecodeRootNode bodyRoot) {
        return new ProtosClosureExecutionPlan(
                new ProtosBytecodeClosureExecutionPlan(
                        definition,
                        language,
                        source,
                        bodyRoot));
    }

    boolean isBytecodeBackendForRuntime() {
        return bytecodePlan != null;
    }

    RootCallTarget bytecodeActivationTargetForComposition() {
        if (bytecodePlan == null) {
            throw new IllegalStateException(
                    "Closure execution plan is not Bytecode-backed");
        }
        return bytecodePlan.activationTargetForComposition();
    }

    ProtosClosureExecutionPlan rebuild(CanonicalClosure definition) {
        Objects.requireNonNull(definition, "definition");
        if (bytecodePlan != null) {
            throw new UnsupportedOperationException(
                    "Bytecode Closure rematerialization requires an explicit destination "
                            + "language until PERF006-B2 context/parallel cutover");
        }
        return new CanonicalToTruffleLowerer(rootFactory)
                .lowerClosurePlan(definition);
    }

    ProtosClosureExecutionPlan rebuildForLanguage(
            CanonicalClosure definition, ProtosLanguage language) {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(language, "language");
        if (bytecodePlan != null) {
            return new ProtosClosureExecutionPlan(
                    bytecodePlan.rebuildForLanguage(definition, language));
        }
        return new CanonicalToTruffleLowerer(
                        rootFactory.withLanguage(language))
                .lowerClosurePlan(definition);
    }

    ProtosClosureExecutionPlan rebuildBytecodeForLanguage(
            CanonicalClosure definition, ProtosLanguage language) {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(language, "language");
        if (bytecodePlan != null) {
            return new ProtosClosureExecutionPlan(
                    bytecodePlan.rebuildForLanguage(definition, language));
        }
        Source exactSource =
                rootFactory.source()
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "Bytecode Closure projection requires exact source"));
        return bytecode(definition, language, exactSource);
    }

    Optional<ProtosLanguage> language() {
        return bytecodePlan != null
                ? Optional.of(bytecodePlan.language())
                : rootFactory.language();
    }

    Optional<Source> source() {
        return bytecodePlan != null
                ? Optional.of(bytecodePlan.source())
                : rootFactory.source();
    }

    CallTarget parameterBindingTargetForTesting() {
        if (bytecodePlan != null) {
            throw new UnsupportedOperationException(
                    "Bytecode parameter binding is not migrated in PERF006-B2B");
        }
        return parameterBindingTarget.get();
    }

    CallTarget bodyTargetForTesting() {
        return bytecodePlan != null
                ? bytecodePlan.activationTargetForComposition()
                : bodyTarget.get();
    }

    public void bind(ProtosActivation activation) {
        Objects.requireNonNull(activation, "activation");
        if (bytecodePlan != null) {
            bytecodePlan.bind(activation);
            return;
        }
        parameterBindingTarget.get().call(activation);
    }

    public Object executeBody(ProtosActivation activation) {
        Objects.requireNonNull(activation, "activation");
        return bytecodePlan != null
                ? bytecodePlan.executeActivation(activation)
                : bodyTarget.get().call(activation);
    }
}
