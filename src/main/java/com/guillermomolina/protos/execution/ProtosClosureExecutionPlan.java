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

    /**
     * PERF006-B6A6A1R temporary B6B oracle/fallback projection.
     *
     * <p>A source-backed Bytecode Closure that reaches a still-legacy AST/replay caller cannot
     * synchronously expose a ContinuationResult. Re-lower its exact canonical definition and
     * source into the entered Context's historical AST backend instead. Task-owned explicit C-prime
     * entry continues to use rebuildBytecodeForLanguage and is unaffected.
     */
    ProtosClosureExecutionPlan rebuildAstForLanguage(
            CanonicalClosure definition, ProtosLanguage language) {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(language, "language");

        /*
         * Entered-Context AST projection is owned by that exact ProtosLanguageContext,
         * so it must remain language-bound. This also ensures nested source Closures
         * created while executing the projection inherit the same Context/language
         * rather than appearing language-unbound and spuriously expanding the A+
         * projection cache.
         */
        Source exactSource =
                bytecodePlan != null
                        ? bytecodePlan.source()
                        : rootFactory.source().orElse(null);
        ProtosRootFactory enteredContextRoots =
                exactSource == null
                        ? rootFactory.withLanguage(language)
                        : ProtosRootFactory.sourceBound(language, exactSource);
        return new CanonicalToTruffleLowerer(enteredContextRoots)
                .lowerClosurePlan(definition);
    }

    ProtosClosureExecutionPlan rebuildAstForLegacyFallback(
            CanonicalClosure definition) {
        Objects.requireNonNull(definition, "definition");

        /*
         * This method is only the no-entered-Context fallback. Those roots must not be
         * bound to a ProtosLanguage instance because there is no ProtosLanguageContext
         * that can own the sharing layer. Keep exact Source identity for source/debug
         * metadata, while entered-Context rebuildAstForLanguage() above remains explicitly
         * language-bound. C-prime/Bytecode projection remains language-bound separately.
         *
         * This method intentionally returns a fresh AST plan. Outside an entered Polyglot
         * Context there is no ProtosLanguageContext whose lifetime can safely own/cache a
         * Truffle CallTarget, so reusing the semantic template would reintroduce foreign
         * sharing-layer ownership through its LazyCallTargets.
         */
        Source exactSource =
                bytecodePlan != null
                        ? bytecodePlan.source()
                        : rootFactory.source().orElse(null);
        ProtosRootFactory fallbackRoots =
                exactSource == null
                        ? ProtosRootFactory.legacy()
                        : ProtosRootFactory.sourceOnly(exactSource);
        return new CanonicalToTruffleLowerer(fallbackRoots)
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
