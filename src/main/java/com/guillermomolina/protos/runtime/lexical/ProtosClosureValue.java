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

package com.guillermomolina.protos.runtime;

import com.guillermomolina.protos.execution.ProtosClosureExecutionPlan;
import com.guillermomolina.protos.runtime.callables.ProtosNativeClosureBody;
import com.guillermomolina.protos.runtime.callables.ProtosSuspensionCapableNativeClosureBody;
import com.guillermomolina.protos.runtime.control.ProtosReturnHome;
import com.guillermomolina.protos.semantic.ast.CanonicalClosure;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import java.util.List;
import java.util.Objects;

public final class ProtosClosureValue extends ProtosObjectValue {
    private final CanonicalClosure definition;
    private final ProtosLexicalEnvironment capturedEnvironment;
    private final Object capturedReceiver;
    private final ProtosObjectValue methodHome;
    private final ProtosReturnHome returnHome;
    private final ProtosPrelude prelude;
    private volatile ProtosClosureExecutionPlan executionPlan;
    private final java.util.function.Supplier<ProtosClosureExecutionPlan> executionPlanRematerializer;
    private final ProtosNativeClosureBody nativeBody;
    private volatile boolean contextLocalExecutionProjectionRequired;

    /*
     * Demand-driven stability of this instance's Context projection
     * requirement. Allocated only if a direct-call PIC requests it.
     */
    private Assumption contextProjectionStability;

    /*
     * I115-H4-B EXPERIMENTAL (GraalPy PFunction.getCodeStableAssumption):
     * stability of this instance's executable target, independent of
     * contextProjectionStability. Allocated only if a direct-call cache
     * requests it; invalidated when a plan that changes the executable
     * target is installed. Object.call, ReturnHome and Prelude changes do
     * not invalidate it.
     */
    private Assumption codeStableAssumption;

    public ProtosClosureValue(
            CanonicalClosure definition,
            List<ProtosObjectValue> capturedLexicalContexts,
            Object capturedReceiver) {
        this(definition, capturedLexicalContexts, capturedReceiver, null, null, null, null, null);
    }

    public ProtosClosureValue(
            CanonicalClosure definition,
            List<ProtosObjectValue> capturedLexicalContexts,
            Object capturedReceiver,
            ProtosReturnHome returnHome) {
        this(
                definition,
                capturedLexicalContexts,
                capturedReceiver,
                null,
                returnHome,
                null,
                null,
                null);
    }

    public ProtosClosureValue(
            CanonicalClosure definition,
            List<ProtosObjectValue> capturedLexicalContexts,
            Object capturedReceiver,
            ProtosObjectValue methodHome,
            ProtosReturnHome returnHome,
            ProtosPrelude prelude) {
        this(
                definition,
                capturedLexicalContexts,
                capturedReceiver,
                methodHome,
                returnHome,
                prelude,
                null,
                null);
    }

    public ProtosClosureValue(
            CanonicalClosure definition,
            List<ProtosObjectValue> capturedLexicalContexts,
            Object capturedReceiver,
            ProtosObjectValue methodHome,
            ProtosReturnHome returnHome,
            ProtosPrelude prelude,
            ProtosClosureExecutionPlan executionPlan) {
        this(definition, capturedLexicalContexts, capturedReceiver, methodHome, returnHome, prelude, executionPlan, null);
    }

    private ProtosClosureValue(
            CanonicalClosure definition,
            List<ProtosObjectValue> capturedLexicalContexts,
            Object capturedReceiver,
            ProtosObjectValue methodHome,
            ProtosReturnHome returnHome,
            ProtosPrelude prelude,
            ProtosClosureExecutionPlan executionPlan,
            ProtosNativeClosureBody nativeBody) {
        this(
                definition,
                capturedLexicalContexts,
                capturedReceiver,
                methodHome,
                returnHome,
                prelude,
                executionPlan,
                nativeBody,
                null);
    }

    private ProtosClosureValue(
            CanonicalClosure definition,
            List<ProtosObjectValue> capturedLexicalContexts,
            Object capturedReceiver,
            ProtosObjectValue methodHome,
            ProtosReturnHome returnHome,
            ProtosPrelude prelude,
            ProtosClosureExecutionPlan executionPlan,
            ProtosNativeClosureBody nativeBody,
            java.util.function.Supplier<ProtosClosureExecutionPlan> executionPlanRematerializer) {
        this(
                definition,
                ProtosLexicalEnvironment.ofContexts(capturedLexicalContexts),
                capturedReceiver,
                methodHome,
                returnHome,
                prelude,
                executionPlan,
                nativeBody,
                executionPlanRematerializer);
    }

    /**
     * PERF025 Closure-literal materialization: captures the creating
     * activation's lexical environment by reference, without materializing
     * any guest context or copying the outer chain.
     */
    public ProtosClosureValue(
            CanonicalClosure definition,
            ProtosLexicalEnvironment capturedEnvironment,
            Object capturedReceiver,
            ProtosObjectValue methodHome,
            ProtosReturnHome returnHome,
            ProtosPrelude prelude,
            ProtosClosureExecutionPlan executionPlan) {
        this(definition, capturedEnvironment, capturedReceiver, methodHome, returnHome,
                prelude, executionPlan, null, null);
    }

    private ProtosClosureValue(
            CanonicalClosure definition,
            ProtosLexicalEnvironment capturedEnvironment,
            Object capturedReceiver,
            ProtosObjectValue methodHome,
            ProtosReturnHome returnHome,
            ProtosPrelude prelude,
            ProtosClosureExecutionPlan executionPlan,
            ProtosNativeClosureBody nativeBody,
            java.util.function.Supplier<ProtosClosureExecutionPlan> executionPlanRematerializer) {
        super(ProtosObjectValue.rootObject());
        this.definition = definition;
        this.capturedEnvironment = capturedEnvironment;
        this.capturedReceiver =
                Objects.requireNonNull(capturedReceiver, "capturedReceiver");
        this.methodHome = methodHome;
        this.returnHome = returnHome;
        this.prelude = prelude;
        this.executionPlan = executionPlan;
        this.executionPlanRematerializer = executionPlanRematerializer;
        this.nativeBody = nativeBody;
    }

    /**
     * I115-H (EXPERIMENTAL, PLAT066-C): minimal materialization of a source
     * Closure the lowering classified as not capturing its creator's context
     * ({@code capturesCreatorContext == false}): its definition and prepared
     * plan, the stable root receiver, and no captured lexical environment,
     * methodHome, ReturnHome, Prelude or plan rematerializer. Never used for
     * a capturing Closure, and other constructions keep their contract.
     *
     * <p>NORMATIVE DIVERGENCE (not an established equivalence): the creator's
     * receiver, methodHome, ReturnHome and Prelude are not retained.
     */
    public static ProtosClosureValue experimentalNonCapturing(
            CanonicalClosure definition,
            ProtosClosureExecutionPlan executionPlan) {
        return new ProtosClosureValue(
                Objects.requireNonNull(definition, "definition"),
                (ProtosLexicalEnvironment) null,
                ProtosObjectValue.rootObject(),
                null,
                null,
                null,
                Objects.requireNonNull(executionPlan, "executionPlan"),
                null,
                null);
    }

    public static ProtosClosureValue nativeClosure(ProtosNativeClosureBody body) {
        return new ProtosClosureValue(null, List.of(), ProtosObjectValue.rootObject(), null, null, null, null, Objects.requireNonNull(body, "body"));
    }

    /**
     * Creates one ordinary native Closure whose implementation also exposes the
     * private PLAT019 Bytecode+Task suspension capability.
     *
     * <p>The ordinary body remains the established evaluator/non-Task entry.
     * The continuation body is reachable only through the Bytecode composed
     * invocation path when an Actor-local Task is present. Both entries are
     * carried by one implementation object, so ordinary extraction, aliasing,
     * parallel projection and method rebinding preserve exact capability
     * provenance without selector-specific metadata.
     */
    public static ProtosClosureValue suspensionCapableNativeClosure(
            ProtosNativeClosureBody ordinaryBody,
            ProtosNativeClosureBody continuationBody) {
        ProtosNativeClosureBody ordinary =
                Objects.requireNonNull(
                        ordinaryBody,
                        "ordinaryBody");
        ProtosNativeClosureBody continuation =
                Objects.requireNonNull(
                        continuationBody,
                        "continuationBody");
        ProtosSuspensionCapableNativeClosureBody implementation =
                new ProtosSuspensionCapableNativeClosureBody() {
                    @Override
                    public Object execute(
                            ProtosActivation activation,
                            List<?> supplied) {
                        return ordinary.execute(
                                activation,
                                supplied);
                    }

                    @Override
                    public Object executeForBytecodeContinuation(
                            ProtosActivation activation,
                            List<?> supplied) {
                        return continuation.execute(
                                activation,
                                supplied);
                    }
                };
        return nativeClosure(implementation);
    }

    public CanonicalClosure definition() {
        return definition;
    }

    /**
     * Cold compatibility projection of the captured lexical chain as context
     * objects; materializes every captured context.
     */
    public List<ProtosObjectValue> capturedLexicalContexts() {
        return ProtosLexicalEnvironment.contextsOf(capturedEnvironment);
    }

    /** Innermost captured lexical scope, or {@code null} when none is captured. */
    public ProtosLexicalEnvironment capturedLexicalEnvironmentForRuntime() {
        return capturedEnvironment;
    }

    public Object capturedReceiver() {
        return capturedReceiver;
    }

    public java.util.Optional<ProtosObjectValue> methodHome() {
        return java.util.Optional.ofNullable(methodHome);
    }

    public java.util.Optional<ProtosReturnHome> returnHome() {
        return java.util.Optional.ofNullable(returnHome);
    }

    /**
     * The return home an invocation of this Closure runs under: the captured
     * home when one is captured; otherwise a home the invocation owns. PERF025:
     * an owned home of a source-backed Closure whose prepared plan proves it
     * unobservable is the non-materialized {@link ProtosReturnHome#unobservable()}
     * marker. Native bodies are opaque to that proof, and a plan that is not
     * yet prepared is not consulted, so both keep a fresh physical home.
     */
    public ProtosReturnHome invocationReturnHomeForRuntime() {
        if (returnHome != null) {
            return returnHome;
        }
        ProtosClosureExecutionPlan plan = executionPlan;
        if (nativeBody == null
                && plan != null
                && plan.returnHomeProvenUnobservableForRuntime()) {
            return ProtosReturnHome.unobservable();
        }
        return new ProtosReturnHome();
    }

    /**
     * I114: exactly {@code invocationReturnHomeForRuntime() ==
     * ProtosReturnHome.unobservable()}, without obtaining or constructing a
     * home and without rematerializing the execution plan. Admission decisions
     * use this so a rejected Closure never allocates a physical home.
     */
    public boolean hasUnobservableInvocationReturnHomeForRuntime() {
        if (returnHome != null) {
            return returnHome == ProtosReturnHome.unobservable();
        }
        if (nativeBody != null) {
            return false;
        }
        ProtosClosureExecutionPlan plan = executionPlan;
        return plan != null && plan.returnHomeProvenUnobservableForRuntime();
    }


    /**
     * I115-C: per-instance ReturnHome eligibility under an
     * already validated source executable template.
     *
     * The caller must have established that this Closure is
     * source-backed and uses the exact cached template.
     *
     * The template's non-observability proof is immutable.
     * The captured ReturnHome is final. Consequently the
     * caller needs no per-instance volatile execution-plan
     * lookup to classify this home.
     *
     * A real captured home always rejects the direct
     * unobservable-home route, even when the template itself
     * cannot observe a newly owned home.
     */
    public boolean hasUnobservableReturnHomeUnderTemplateProofForRuntime(
            boolean templateProvenUnobservable) {
        ProtosReturnHome capturedHome = returnHome;
        return capturedHome == ProtosReturnHome.unobservable()
                || (capturedHome == null && templateProvenUnobservable);
    }

    public java.util.Optional<ProtosPrelude> prelude() {
        return java.util.Optional.ofNullable(prelude);
    }

    /**
     * PERF038-E: exactly {@link #prelude()}'s value, or {@code null} when the
     * Closure captured none, without allocating an {@code Optional} on
     * compiled runtime paths. The prelude is final.
     */
    public ProtosPrelude preludeOrNullForRuntime() {
        return prelude;
    }

    public java.util.Optional<ProtosClosureExecutionPlan> executionPlan() {
        return java.util.Optional.ofNullable(executionPlan);
    }

    /**
     * I115-A: exactly the currently prepared execution plan, or {@code null}
     * when none is prepared yet. Never runs the rematerializer, synchronizes,
     * or projects to another Context, so reading it cannot make a deferred
     * plan eager.
     */
    public ProtosClosureExecutionPlan preparedExecutionPlanOrNullForRuntime() {
        return executionPlan;
    }

    public java.util.Optional<java.util.function.Supplier<ProtosClosureExecutionPlan>>
            executionPlanRematerializerForParallelRuntime() {
        return java.util.Optional.ofNullable(executionPlanRematerializer);
    }

    /** Implementation-only marker for semantic Closures shared across Truffle Contexts. */
    public boolean requiresContextLocalExecutionProjectionForRuntime() {
        return contextLocalExecutionProjectionRequired;
    }

        /**
     * Lazily certifies this Closure's current projection requirement.
     * Registration and transition use the same monitor.
     */
    @TruffleBoundary
    public synchronized Assumption contextProjectionStabilityForRuntime() {
        Assumption stability = contextProjectionStability;
        if (stability == null || !stability.isValid()) {
            stability = Truffle.getRuntime().createAssumption(
                    "Protos Closure Context projection stable");
            contextProjectionStability = stability;
        }
        return stability;
    }

    /**
     * I115-H4-B EXPERIMENTAL: lazily certifies that this Closure's executable
     * target does not change. Registration and plan installation use the
     * same monitor.
     */
    @TruffleBoundary
    public synchronized Assumption codeStableAssumptionForRuntime() {
        Assumption stability = codeStableAssumption;
        if (stability == null || !stability.isValid()) {
            stability = Truffle.getRuntime().createAssumption(
                    "Protos Closure code stable");
            codeStableAssumption = stability;
        }
        return stability;
    }

/**
     * Requires entered Polyglot execution to use a Context-owned executable projection.
     * The semantic Closure and its template plan remain unchanged.
     */
    public void requireContextLocalExecutionProjectionForRuntime() {
        if (definition == null || nativeBody != null || executionPlan == null) {
            throw new IllegalStateException(
                    "context-local execution projection requires a prepared source-backed Closure");
        }

        if (!contextLocalExecutionProjectionRequired) {
            synchronized (this) {
                if (!contextLocalExecutionProjectionRequired) {
                    Assumption stability = contextProjectionStability;
                    if (stability != null) {
                        stability.invalidate();
                    }
                    contextLocalExecutionProjectionRequired = true;
                }
            }
        }
    }

    public ProtosClosureExecutionPlan executionPlanForRuntimeInvocation() {
        ProtosClosureExecutionPlan plan = executionPlan;
        if (plan != null) return plan;
        java.util.function.Supplier<ProtosClosureExecutionPlan> rematerializer =
                executionPlanRematerializer;
        if (rematerializer == null) {
            throw new IllegalStateException("Closure invocation requires a prepared execution plan");
        }
        synchronized (this) {
            if (executionPlan == null) {
                executionPlan = Objects.requireNonNull(
                        invokeExecutionPlanRematerializer(rematerializer),
                        "executionPlanRematerializer returned null");
                // I115-H4-B EXPERIMENTAL: the executable target changed.
                Assumption stability = codeStableAssumption;
                if (stability != null) {
                    stability.invalidate();
                }
            }
            return executionPlan;
        }
    }


    @com.oracle.truffle.api.CompilerDirectives.TruffleBoundary
    private static ProtosClosureExecutionPlan invokeExecutionPlanRematerializer(
            java.util.function.Supplier<ProtosClosureExecutionPlan> rematerializer) {
        return rematerializer.get();
    }

    public java.util.Optional<ProtosNativeClosureBody> nativeBody() {
        return java.util.Optional.ofNullable(nativeBody);
    }

    public ProtosClosureValue parallelProjection(java.util.List<ProtosObjectValue> roots,Object receiver,ProtosPrelude prelude,ProtosClosureExecutionPlan plan) {
        return new ProtosClosureValue(definition,roots,java.util.Objects.requireNonNull(receiver),null,null,java.util.Objects.requireNonNull(prelude),plan,nativeBody);
    }

    public ProtosClosureValue parallelProjectionDeferred(
            java.util.List<ProtosObjectValue> roots,
            Object receiver,
            ProtosPrelude prelude,
            java.util.function.Supplier<ProtosClosureExecutionPlan> executionPlanRematerializer) {
        return new ProtosClosureValue(
                definition, roots, java.util.Objects.requireNonNull(receiver), null, null,
                java.util.Objects.requireNonNull(prelude), null, nativeBody,
                executionPlanRematerializer);
    }

    public ProtosClosureValue bindMethod(
            Object receiver,
            ProtosObjectValue home) {
        ProtosClosureValue bound = new ProtosClosureValue(
                definition, capturedEnvironment, Objects.requireNonNull(receiver, "receiver"),
                Objects.requireNonNull(home, "home"), returnHome, prelude, executionPlan, nativeBody,
                executionPlanRematerializer);
        if (contextLocalExecutionProjectionRequired) {
            bound.requireContextLocalExecutionProjectionForRuntime();
        }
        if (hasLocalBindingsForRuntime()) {
            java.util.ArrayList<String> localNames =
                    new java.util.ArrayList<>();
            java.util.ArrayList<Object> localValues =
                    new java.util.ArrayList<>();
            appendLocalBindingsTo(localNames, localValues);
            for (int index = 0; index < localNames.size(); index++) {
                bound.createLocalSlot(
                        localNames.get(index),
                        localValues.get(index));
            }
        }
        if (isFrozen()) {
            bound.freeze();
        } else if (isClosed()) {
            bound.close();
        }
        return bound;
    }
}
