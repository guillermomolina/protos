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

import com.guillermomolina.protos.semantic.ast.CanonicalAssign;
import com.guillermomolina.protos.semantic.ast.CanonicalClosure;
import com.guillermomolina.protos.semantic.ast.CanonicalCreate;
import com.guillermomolina.protos.semantic.ast.CanonicalExpression;
import com.guillermomolina.protos.semantic.ast.CanonicalIdentity;
import com.guillermomolina.protos.semantic.ast.CanonicalIntrinsic;
import com.guillermomolina.protos.semantic.ast.CanonicalLiteral;
import com.guillermomolina.protos.semantic.ast.CanonicalLookup;
import com.guillermomolina.protos.semantic.ast.CanonicalNotIdentity;
import com.guillermomolina.protos.semantic.ast.CanonicalParameter;
import com.guillermomolina.protos.semantic.ast.CanonicalReturn;
import com.guillermomolina.protos.semantic.ast.CanonicalSend;
import com.guillermomolina.protos.semantic.ast.CanonicalSequence;
import java.util.Collections;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * I107 (PLAT061-B, D204-A): bounded compositional proof that evaluating a
 * canonical subject cannot suspend its execution.
 *
 * <p>The analysis is not an effect system. It answers one question, for one
 * {@link Subject}, under one exact {@link CanonicalBindingAnalysis} and
 * {@link CanonicalLexicalScope}. Missing proof is {@link Proof#UNKNOWN}, which
 * never means that suspension is possible: it only keeps the generic path.
 *
 * <p>Proven forms are literals, Closure literals as value construction (their
 * body is never evaluated here), {@code this}, identity and non-identity of
 * proven operands, {@code ^} of a proven value (ReturnHome observation is a
 * separate capability), reads of own bindings whose presence is established
 * on every path reaching the read, and own target-less creations or
 * assignments of proven values when the root keeps its bindings in direct
 * frame locals. A send is proven only when the {@link SendSelection} supplied
 * by the lowerer states that its selection is statically complete; it then
 * contributes exactly the operands it evaluates and the bodies it runs. Every
 * call, other send, super send, member access, spread, map, object,
 * composition, {@code context}, multiple creation, indexed assignment and
 * unrecognized form is {@link Proof#UNKNOWN}; recursion is therefore never
 * proven.
 *
 * <p>Each expression node is visited at most once per instance (identity
 * memoization); presence flows forward through the sequence in one pass, so
 * the cost is linear in the analyzed subtree.
 */
final class CanonicalNonSuspensionAnalysis {

    /** Explicit proof state: absence of proof is not a negative proof. */
    enum Proof { UNKNOWN, PROVEN_NO_SUSPENSION }

    /** The independent subjects a proof may be about; none implies another. */
    enum Subject {
        /** Evaluation of one canonical body, without entry or invocation. */
        CANONICAL_BODY,
        /** The emitted root: parameter binding, defaults and effective body. */
        EFFECTIVE_ROOT,
        /** A whole invocation: receiver, selection, arguments, binding, body. */
        COMPLETE_INVOCATION,
        /** A call site, valid only while its run-time guard holds. */
        GUARDED_CALL_SITE
    }

    /** Validity preconditions a proof depends on. */
    enum Precondition {
        STANDARD_OBJECT_PUBLISHED,
        DIRECT_FRAME_LOCALS,
        UNOBSERVABLE_RETURN_HOME,
        RUNTIME_SELECTION_GUARD
    }

    /**
     * Immutable proof. {@code identity}, {@code analysis} and {@code scope}
     * are compared by Java identity, so structurally equal or reparsed
     * definitions never share a result. No Builder state is retained.
     */
    static final class Result {
        private final Proof proof;
        private final Subject subject;
        private final Object identity;
        private final CanonicalBindingAnalysis analysis;
        private final CanonicalLexicalScope scope;
        private final Set<Precondition> preconditions;
        private final String provenance;

        Result(
                Proof proof,
                Subject subject,
                Object identity,
                CanonicalBindingAnalysis analysis,
                CanonicalLexicalScope scope,
                Set<Precondition> preconditions,
                String provenance) {
            this.proof = Objects.requireNonNull(proof, "proof");
            this.subject = Objects.requireNonNull(subject, "subject");
            this.identity = Objects.requireNonNull(identity, "identity");
            this.analysis = analysis;
            this.scope = scope;
            this.preconditions = preconditions.isEmpty()
                    ? Collections.unmodifiableSet(EnumSet.noneOf(Precondition.class))
                    : Collections.unmodifiableSet(EnumSet.copyOf(preconditions));
            this.provenance = Objects.requireNonNull(provenance, "provenance");
        }

        static Result unknown(
                Subject subject,
                Object identity,
                CanonicalBindingAnalysis analysis,
                CanonicalLexicalScope scope,
                String provenance) {
            return new Result(Proof.UNKNOWN, subject, identity, analysis, scope,
                    Set.of(), provenance);
        }

        Proof proof() { return proof; }
        Subject subject() { return subject; }
        Object identity() { return identity; }
        CanonicalBindingAnalysis analysis() { return analysis; }
        CanonicalLexicalScope scope() { return scope; }
        Set<Precondition> preconditions() { return preconditions; }
        String provenance() { return provenance; }

        boolean proven() { return proof == Proof.PROVEN_NO_SUSPENSION; }

        /** Same subject, identity, analysis and scope (by Java identity). */
        boolean sameSubjectAs(Result other) {
            return subject == other.subject
                    && identity == other.identity
                    && analysis == other.analysis
                    && scope == other.scope;
        }

        @Override
        public String toString() {
            return subject + ":" + proof + preconditions + "<" + provenance + ">";
        }
    }

    /**
     * Statically complete selection of one send, supplied by the phase that
     * owns the selection proof (the lowerer's NORMAL Boolean-control table
     * under the published standard Object). {@code null} means the selection
     * may reach dynamic dispatch.
     */
    @FunctionalInterface
    interface SendSelection {
        SendSelection NONE = (send, analysis, scope) -> null;

        SelectedSend select(
                CanonicalSend send,
                CanonicalBindingAnalysis analysis,
                CanonicalLexicalScope scope);
    }

    /**
     * The exact work of a statically selected send: the operands it
     * evaluates in the current activation, and the parameterless literal
     * Closure bodies it runs (each in its own scope).
     */
    record SelectedSend(
            List<CanonicalExpression> evaluatedOperands,
            List<CanonicalClosure> selectedBodies,
            Set<Precondition> preconditions) {}

    /**
     * How to analyze a selected callback body: its analysis, scope and
     * whether its bindings are direct locals.
     */
    record CallbackScope(
            CanonicalBindingAnalysis analysis,
            CanonicalLexicalScope scope,
            boolean directFrameLocals) {}

    private final CanonicalBindingAnalysis analysis;
    private final CanonicalLexicalScope scope;
    private final boolean directFrameLocals;
    private final SendSelection selection;
    private final Function<CanonicalClosure, CallbackScope> callbackScopes;
    private final IdentityHashMap<CanonicalExpression, Boolean> memo =
            new IdentityHashMap<>();
    private final IdentityHashMap<CanonicalClosure, Boolean> callbackMemo;
    private final Set<CanonicalBindingIdentity> established =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private final EnumSet<Precondition> used = EnumSet.noneOf(Precondition.class);

    CanonicalNonSuspensionAnalysis(
            CanonicalBindingAnalysis analysis,
            CanonicalLexicalScope scope,
            boolean directFrameLocals,
            SendSelection selection,
            Function<CanonicalClosure, CallbackScope> callbackScopes) {
        this(analysis, scope, directFrameLocals, selection, callbackScopes,
                new IdentityHashMap<>());
    }

    private CanonicalNonSuspensionAnalysis(
            CanonicalBindingAnalysis analysis,
            CanonicalLexicalScope scope,
            boolean directFrameLocals,
            SendSelection selection,
            Function<CanonicalClosure, CallbackScope> callbackScopes,
            IdentityHashMap<CanonicalClosure, Boolean> callbackMemo) {
        this.analysis = Objects.requireNonNull(analysis, "analysis");
        this.scope = Objects.requireNonNull(scope, "scope");
        this.directFrameLocals = directFrameLocals;
        this.selection = selection == null ? SendSelection.NONE : selection;
        this.callbackScopes = callbackScopes;
        this.callbackMemo = callbackMemo;
    }

    /** {@link Subject#CANONICAL_BODY}: the body alone; no entry operation. */
    Result proveBody(CanonicalSequence body, CanonicalClosure definition) {
        if (definition != null) {
            for (CanonicalParameter parameter : definition.parameters()) {
                analysis.identityOf(parameter)
                        .filter(identity -> identity.owner() == scope)
                        .ifPresent(established::add);
            }
        }
        boolean proven = provesAll(body.expressions());
        return result(proven, Subject.CANONICAL_BODY, body, "canonical body");
    }

    /**
     * {@link Subject#EFFECTIVE_ROOT}: the entry of {@code definition}
     * (parameters, in order, with every default that may be evaluated) and
     * the statements the root actually evaluates. A safe body never makes a
     * root with an unproven default safe. A rest parameter and an observable
     * ReturnHome keep the root UNKNOWN.
     */
    Result proveEffectiveRoot(
            Object effectiveIdentity,
            CanonicalClosure definition,
            List<CanonicalExpression> effectiveBody,
            boolean mayObserveReturnHome,
            String provenance) {
        if (definition == null || mayObserveReturnHome) {
            return result(false, Subject.EFFECTIVE_ROOT, effectiveIdentity, provenance);
        }
        used.add(Precondition.UNOBSERVABLE_RETURN_HOME);
        for (CanonicalParameter parameter : definition.parameters()) {
            CanonicalBindingIdentity identity =
                    analysis.identityOf(parameter).orElse(null);
            if (parameter.rest() || identity == null || identity.owner() != scope) {
                return result(false, Subject.EFFECTIVE_ROOT, effectiveIdentity, provenance);
            }
            if (parameter.defaultValue().isPresent()
                    && !proves(parameter.defaultValue().orElseThrow())) {
                return result(false, Subject.EFFECTIVE_ROOT, effectiveIdentity, provenance);
            }
            established.add(identity);
        }
        return result(
                provesAll(effectiveBody), Subject.EFFECTIVE_ROOT, effectiveIdentity, provenance);
    }

    private Result result(boolean proven, Subject subject, Object identity, String provenance) {
        if (!proven) {
            return Result.unknown(subject, identity, analysis, scope, provenance);
        }
        EnumSet<Precondition> preconditions = EnumSet.copyOf(used);
        preconditions.add(Precondition.UNOBSERVABLE_RETURN_HOME);
        if (subject != Subject.EFFECTIVE_ROOT) {
            preconditions.remove(Precondition.UNOBSERVABLE_RETURN_HOME);
        }
        return new Result(Proof.PROVEN_NO_SUSPENSION, subject, identity, analysis, scope,
                preconditions, provenance);
    }

    private boolean provesAll(List<CanonicalExpression> expressions) {
        for (CanonicalExpression expression : expressions) {
            if (!proves(expression)) {
                return false;
            }
        }
        return true;
    }

    private boolean proves(CanonicalExpression expression) {
        Boolean cached = memo.get(expression);
        if (cached != null) {
            return cached;
        }
        boolean proven = compute(expression);
        memo.put(expression, proven);
        return proven;
    }

    private boolean compute(CanonicalExpression expression) {
        return switch (expression) {
            case CanonicalLiteral ignored -> true;
            // Construction of a value; the body runs only when called.
            case CanonicalClosure ignored -> true;
            case CanonicalIntrinsic intrinsic -> intrinsic.kind() == CanonicalIntrinsic.Kind.THIS;
            case CanonicalSequence sequence -> provesAll(sequence.expressions());
            case CanonicalIdentity identity -> proves(identity.left()) && proves(identity.right());
            case CanonicalNotIdentity identity ->
                    proves(identity.left()) && proves(identity.right());
            case CanonicalReturn returnExpression -> proves(returnExpression.value());
            case CanonicalLookup lookup -> readsEstablished(analysis.resolutionOf(lookup).orElse(null));
            case CanonicalCreate create -> provesCreate(create);
            case CanonicalAssign assign -> provesAssign(assign);
            case CanonicalSend send -> provesSelectedSend(send);
            default -> false;
        };
    }

    private boolean readsEstablished(CanonicalBindingResolution resolution) {
        return resolution instanceof CanonicalBindingResolution.Resolved resolved
                && resolved.identity().owner() == scope
                && established.contains(resolved.identity());
    }

    private boolean provesCreate(CanonicalCreate create) {
        if (!directFrameLocals || create.target().isPresent()) {
            return false;
        }
        CanonicalBindingIdentity identity = analysis.identityOf(create).orElse(null);
        if (identity == null || identity.owner() != scope || !proves(create.value())) {
            return false;
        }
        used.add(Precondition.DIRECT_FRAME_LOCALS);
        established.add(identity);
        return true;
    }

    private boolean provesAssign(CanonicalAssign assign) {
        if (!directFrameLocals
                || assign.target().isPresent()
                || !readsEstablished(analysis.resolutionOf(assign).orElse(null))
                || !proves(assign.value())) {
            return false;
        }
        used.add(Precondition.DIRECT_FRAME_LOCALS);
        return true;
    }

    private boolean provesSelectedSend(CanonicalSend send) {
        SelectedSend selected = selection.select(send, analysis, scope);
        if (selected == null || !provesAll(selected.evaluatedOperands())) {
            return false;
        }
        for (CanonicalClosure callback : selected.selectedBodies()) {
            if (!provesCallback(callback)) {
                return false;
            }
        }
        used.addAll(selected.preconditions());
        return true;
    }

    /** A selected callback body, under its own analysis and scope. */
    private boolean provesCallback(CanonicalClosure callback) {
        Boolean cached = callbackMemo.get(callback);
        if (cached != null) {
            return cached;
        }
        boolean proven = false;
        CallbackScope callbackScope =
                callbackScopes == null || !callback.parameters().isEmpty()
                        ? null
                        : callbackScopes.apply(callback);
        if (callbackScope != null) {
            CanonicalNonSuspensionAnalysis nested = new CanonicalNonSuspensionAnalysis(
                    callbackScope.analysis(),
                    callbackScope.scope(),
                    callbackScope.directFrameLocals(),
                    selection,
                    callbackScopes,
                    callbackMemo);
            proven = nested.provesAll(callback.body().expressions());
            if (proven) {
                used.addAll(nested.used);
            }
        }
        callbackMemo.put(callback, proven);
        return proven;
    }

    /** Number of distinct expression nodes analyzed (evidence of linearity). */
    int analyzedNodeCount() {
        return memo.size();
    }
}
