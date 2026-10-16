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
import com.guillermomolina.protos.semantic.ast.CanonicalCall;
import com.guillermomolina.protos.semantic.ast.CanonicalClosure;
import com.guillermomolina.protos.semantic.ast.CanonicalCompose;
import com.guillermomolina.protos.semantic.ast.CanonicalCreate;
import com.guillermomolina.protos.semantic.ast.CanonicalDerivedInequality;
import com.guillermomolina.protos.semantic.ast.CanonicalExpression;
import com.guillermomolina.protos.semantic.ast.CanonicalIdentity;
import com.guillermomolina.protos.semantic.ast.CanonicalIndexedAssign;
import com.guillermomolina.protos.semantic.ast.CanonicalIntrinsic;
import com.guillermomolina.protos.semantic.ast.CanonicalLiteral;
import com.guillermomolina.protos.semantic.ast.CanonicalLookup;
import com.guillermomolina.protos.semantic.ast.CanonicalMapConstruction;
import com.guillermomolina.protos.semantic.ast.CanonicalMember;
import com.guillermomolina.protos.semantic.ast.CanonicalMultipleCreate;
import com.guillermomolina.protos.semantic.ast.CanonicalNotIdentity;
import com.guillermomolina.protos.semantic.ast.CanonicalObject;
import com.guillermomolina.protos.semantic.ast.CanonicalParameter;
import com.guillermomolina.protos.semantic.ast.CanonicalReturn;
import com.guillermomolina.protos.semantic.ast.CanonicalSend;
import com.guillermomolina.protos.semantic.ast.CanonicalSequence;
import com.guillermomolina.protos.semantic.ast.CanonicalSpread;
import com.guillermomolina.protos.semantic.ast.CanonicalSuperSend;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * I106 (PLAT061-B): compilation-unit-local owner of the canonical proofs the
 * Bytecode lowerer reuses. It is owned by exactly one {@link
 * CanonicalToBytecodeLowerer}, and therefore by that lowerer's compilation
 * unit and retained Bytecode parser; it is never shared between lowerers or
 * Contexts.
 *
 * <p>It answers exactly four families of questions the lowerer already asked:
 * <ol>
 *   <li>the isolated lexical analysis of one Closure definition;</li>
 *   <li>whether a root must install persistent frame authority ({@link
 *       RootFacts});</li>
 *   <li>whether a Closure definition may observe its ReturnHome;</li>
 *   <li>the static binding Closure-call candidates of one Closure root.</li>
 * </ol>
 *
 * <p>Validity scopes are kept apart. The isolated analysis and ReturnHome
 * observability belong to the exact {@link CanonicalClosure} identity. Root
 * facts and call candidates belong to the exact {@link
 * CanonicalBindingAnalysis} and {@link CanonicalLexicalScope} they were
 * proven under: the same definition analyzed in the whole lexical tree and in
 * isolation receives two distinct answers, and neither substitutes for the
 * other. Facts of the emitted root (effective body after authorized folds,
 * non-suspension, physical layout, carriers, {@code BytecodeLocal}s and the
 * root group) are not canonical facts and stay with emission.
 *
 * <p>Every key is an AST node, analysis or scope compared by identity, so two
 * structurally equal definitions, or a reparsed definition, never share a
 * proof.
 */
final class CanonicalCompilationFacts {

    /**
     * Immutable canonical proof about one root, valid only for the exact
     * analysis and scope it names. It carries no physical, runtime or
     * invocation-dependent state.
     */
    record RootFacts(
            CanonicalBindingAnalysis bindingAnalysis,
            CanonicalLexicalScope scope,
            boolean requiresPersistentFrameAuthority) {}

    private record CallCandidates(
            CanonicalLexicalScope scope,
            Map<CanonicalCall, CanonicalClosure> candidates) {}

    private final IdentityHashMap<CanonicalClosure, CanonicalBindingAnalysis>
            isolatedClosureAnalyses = new IdentityHashMap<>();
    private final IdentityHashMap<CanonicalClosure, Boolean> returnHomeObservability =
            new IdentityHashMap<>();
    private final IdentityHashMap<
                    CanonicalBindingAnalysis, IdentityHashMap<CanonicalClosure, RootFacts>>
            closureRootFacts = new IdentityHashMap<>();
    private final IdentityHashMap<CanonicalSequence, RootFacts> moduleRootFacts =
            new IdentityHashMap<>();
    private final IdentityHashMap<
                    CanonicalBindingAnalysis, IdentityHashMap<CanonicalClosure, CallCandidates>>
            closureCallCandidates = new IdentityHashMap<>();

    /**
     * The analysis of {@code definition} with no statically known enclosing
     * scope. It never stands in for a whole-tree analysis that knows the
     * owners of the definition's captured references.
     */
    CanonicalBindingAnalysis isolatedClosureAnalysis(CanonicalClosure definition) {
        return isolatedClosureAnalyses.computeIfAbsent(
                definition, CanonicalBindingAnalyzer::analyzeClosure);
    }

    /**
     * Whether {@code definition} may observe its ReturnHome ({@link
     * CanonicalReturnHomeAnalysis}). The answer belongs to the canonical
     * definition, not to an emitted root or a compilation profile.
     */
    boolean mayObserveReturnHome(CanonicalClosure definition) {
        return returnHomeObservability.computeIfAbsent(
                definition, CanonicalReturnHomeAnalysis::mayObserveReturnHome);
    }

    /**
     * The canonical facts of the root whose body is {@code body}, lowered as
     * the activation of {@code definition} ({@code null} for a module root)
     * under {@code analysis} and {@code scope}. A repeated query for the same
     * definition, analysis and scope returns the same instance.
     *
     * <p>Missing proof is conservative: a module root, an unknown analysis or
     * scope, a definition whose own scope in {@code analysis} is not
     * {@code scope}, or a {@code body} other than the definition's own always
     * requires persistent frame authority.
     */
    RootFacts rootFacts(
            CanonicalSequence body,
            CanonicalClosure definition,
            CanonicalBindingAnalysis analysis,
            CanonicalLexicalScope scope) {
        if (analysis == null || scope == null) {
            return new RootFacts(analysis, scope, true);
        }
        if (definition == null) {
            RootFacts cached = moduleRootFacts.get(body);
            if (cached != null
                    && cached.bindingAnalysis() == analysis
                    && cached.scope() == scope) {
                return cached;
            }
            RootFacts facts = new RootFacts(analysis, scope, true);
            moduleRootFacts.put(body, facts);
            return facts;
        }
        // A query outside the definition's own body and scope proves nothing.
        if (analysis.scopeOf(definition).orElse(null) != scope
                || body != definition.body()) {
            return new RootFacts(analysis, scope, true);
        }
        IdentityHashMap<CanonicalClosure, RootFacts> byDefinition =
                closureRootFacts.computeIfAbsent(analysis, ignored -> new IdentityHashMap<>());
        RootFacts cached = byDefinition.get(definition);
        if (cached != null) {
            return cached;
        }
        RootFacts facts =
                new RootFacts(
                        analysis,
                        scope,
                        requiresPersistentFrameAuthority(body, definition, analysis, scope));
        byDefinition.put(definition, facts);
        return facts;
    }

    /**
     * I096-B1 (PLAT057-B) candidate bodies of the root of {@code definition}
     * under {@code analysis} and {@code scope}, memoized for that exact triple
     * so a Bytecode reparse reuses the same immutable identity-keyed map. A
     * candidate only states which body a site may expect; the called value is
     * still decided at run time by the guarded carrier admission.
     */
    Map<CanonicalCall, CanonicalClosure> bindingClosureCallCandidates(
            CanonicalSequence body,
            CanonicalClosure definition,
            CanonicalBindingAnalysis analysis,
            CanonicalLexicalScope scope) {
        // No candidate is proven outside the definition's own body and scope.
        if (body == null
                || definition == null
                || analysis == null
                || scope == null
                || body != definition.body()
                || analysis.scopeOf(definition).orElse(null) != scope) {
            return Map.of();
        }
        IdentityHashMap<CanonicalClosure, CallCandidates> byDefinition =
                closureCallCandidates.computeIfAbsent(
                        analysis, ignored -> new IdentityHashMap<>());
        CallCandidates cached = byDefinition.get(definition);
        if (cached != null) {
            return cached.candidates();
        }
        Map<CanonicalCall, CanonicalClosure> candidates =
                Collections.unmodifiableMap(
                        new IdentityHashMap<>(collectCandidates(body, analysis, scope)));
        byDefinition.put(definition, new CallCandidates(scope, candidates));
        return candidates;
    }

    /**
     * I096-B1 (PLAT057-B) static discovery of candidate bodies for ordinary
     * calls through a lexical binding, restricted to the expressions of one
     * genuine root sequence: a target-less {@code CanonicalCreate} whose
     * value is a Closure literal and whose binding identity is owned by
     * {@code scope} declares a candidate, and a call in a later expression
     * whose receiver lookup is {@link CanonicalBindingResolution.Resolved}
     * to that exact identity is associated with it, whether it is the
     * expression itself or one of its operands evaluated by this same
     * activation ({@link #collectBindingClosureCallCandidates}). Identities,
     * never names, decide membership. An identity created more than once in
     * the sequence keeps no candidate after its second creation, so each site
     * has at most one.
     */
    private static IdentityHashMap<CanonicalCall, CanonicalClosure> collectCandidates(
            CanonicalSequence sequence,
            CanonicalBindingAnalysis analysis,
            CanonicalLexicalScope scope) {
        IdentityHashMap<CanonicalBindingIdentity, CanonicalClosure> definitions =
                new IdentityHashMap<>();
        Set<CanonicalBindingIdentity> recreated =
                Collections.newSetFromMap(new IdentityHashMap<>());
        IdentityHashMap<CanonicalCall, CanonicalClosure> candidates =
                new IdentityHashMap<>();

        for (CanonicalExpression expression : sequence.expressions()) {
            collectBindingClosureCallCandidates(
                    expression, analysis, definitions, candidates);

            if (expression instanceof CanonicalCreate create
                    && create.target().isEmpty()) {
                CanonicalBindingIdentity identity =
                        analysis.identityOf(create).orElse(null);
                if (identity == null || identity.owner() != scope) {
                    continue;
                }
                if (create.value() instanceof CanonicalClosure closure
                        && !definitions.containsKey(identity)
                        && !recreated.contains(identity)) {
                    definitions.put(identity, closure);
                } else {
                    definitions.remove(identity);
                    recreated.add(identity);
                }
            }
        }
        return candidates;
    }

    /**
     * Associates every ordinary call of {@code expression} whose receiver
     * lookup resolves to an identity with a current candidate definition.
     * Only operand positions evaluated by this same activation are visited:
     * a nested Closure or Object body, a sequence, and any other
     * scope-introducing or vector-staging form is not entered, so no call is
     * associated across a heterogeneous scope.
     */
    private static void collectBindingClosureCallCandidates(
            CanonicalExpression expression,
            CanonicalBindingAnalysis analysis,
            Map<CanonicalBindingIdentity, CanonicalClosure> definitions,
            Map<CanonicalCall, CanonicalClosure> candidates) {
        List<CanonicalExpression> operands = switch (expression) {
            case CanonicalCall call -> {
                if (call.receiver() instanceof CanonicalLookup lookup
                        && analysis.resolutionOf(lookup).orElse(null)
                                instanceof CanonicalBindingResolution.Resolved resolved) {
                    CanonicalClosure definition = definitions.get(resolved.identity());
                    if (definition != null) {
                        candidates.put(call, definition);
                    }
                }
                List<CanonicalExpression> callOperands =
                        new java.util.ArrayList<>(call.arguments());
                callOperands.add(0, call.receiver());
                yield callOperands;
            }
            case CanonicalSend send -> {
                List<CanonicalExpression> sendOperands =
                        new java.util.ArrayList<>(send.arguments());
                sendOperands.add(0, send.receiver());
                yield sendOperands;
            }
            case CanonicalCreate create -> java.util.stream.Stream.concat(
                            create.target().stream(), java.util.stream.Stream.of(create.value()))
                    .toList();
            case CanonicalAssign assign -> java.util.stream.Stream.concat(
                            assign.target().stream(), java.util.stream.Stream.of(assign.value()))
                    .toList();
            case CanonicalIndexedAssign indexed ->
                    List.of(indexed.receiver(), indexed.index(), indexed.value());
            case CanonicalMember member -> List.of(member.receiver());
            case CanonicalIdentity identity ->
                    List.of(identity.left(), identity.right());
            case CanonicalNotIdentity identity ->
                    List.of(identity.left(), identity.right());
            case CanonicalDerivedInequality inequality ->
                    List.of(inequality.left(), inequality.right());
            case CanonicalReturn returnExpression -> List.of(returnExpression.value());
            default -> List.of();
        };
        for (CanonicalExpression operand : operands) {
            collectBindingClosureCallCandidates(operand, analysis, definitions, candidates);
        }
    }

    /**
     * PERF025 frame-materialization slice: conservative lowering decision on
     * whether a genuine root must install its persistent, escape-safe
     * materialized-frame authority at entry, or may keep its statically proven
     * current bindings in its ordinary frame locals for the lifetime of its
     * live frame.
     *
     * <p>Only a Closure root can avoid the authority: a top-level/module root
     * may share its activation with later roots (REPL/module persistence and
     * the I072-C authority handoff), so it always installs it. A Closure root
     * avoids it only when nothing in its own parameters or body can make its
     * current execution context observable or capturable, or reach a current
     * binding other than through its static identity:
     * <ul>
     *   <li>no nested Closure (including inline literal callbacks and Closure
     *       defaults), which captures the current context by reference;</li>
     *   <li>no Object construction, whose construction activation captures it;</li>
     *   <li>no {@code context} intrinsic and no {@code compose}, which observe
     *       or populate it as a guest Context;</li>
     *   <li>every bare read or write of a name this root declares is {@code
     *       Resolved} to this root's own scope (never {@code Candidate} or
     *       {@code Dynamic}), and every target-less creation is proven to
     *       establish a binding of this root's own scope.</li>
     * </ul>
     * Any unrecognized form keeps the authority. A root that is admitted but
     * whose activation is nonetheless observed at run time takes the
     * in-frame transition of {@link
     * ProtosBytecodeRootNode#createCurrentFrameBinding}.
     */
    private static boolean requiresPersistentFrameAuthority(
            CanonicalSequence body,
            CanonicalClosure activationDefinition,
            CanonicalBindingAnalysis analysis,
            CanonicalLexicalScope scope) {
        if (activationDefinition == null || analysis == null || scope == null) {
            return true;
        }
        for (CanonicalParameter parameter : activationDefinition.parameters()) {
            if (analysis.identityOf(parameter)
                            .map(identity -> identity.owner() != scope)
                            .orElse(true)
                    || (parameter.defaultValue().isPresent()
                            && demandsPersistentFrame(
                                    parameter.defaultValue().orElseThrow(), analysis, scope))) {
                return true;
            }
        }
        return demandsPersistentFrame(body, analysis, scope);
    }

    private static boolean demandsPersistentFrame(
            CanonicalExpression expression,
            CanonicalBindingAnalysis analysis,
            CanonicalLexicalScope scope) {
        if (expression instanceof CanonicalLiteral) {
            return false;
        }
        if (expression instanceof CanonicalClosure
                || expression instanceof CanonicalObject
                || expression instanceof CanonicalCompose) {
            return true;
        }
        if (expression instanceof CanonicalIntrinsic intrinsic) {
            return intrinsic.kind() == CanonicalIntrinsic.Kind.CONTEXT;
        }
        if (expression instanceof CanonicalLookup lookup) {
            return scope.declaresName(lookup.name())
                    && !resolvedInScope(analysis.resolutionOf(lookup), scope);
        }
        if (expression instanceof CanonicalAssign assign) {
            if (assign.target().isPresent()) {
                return demandsPersistentFrame(assign.target().orElseThrow(), analysis, scope)
                        || demandsPersistentFrame(assign.value(), analysis, scope);
            }
            return (scope.declaresName(assign.name())
                            && !resolvedInScope(analysis.resolutionOf(assign), scope))
                    || demandsPersistentFrame(assign.value(), analysis, scope);
        }
        if (expression instanceof CanonicalCreate create) {
            if (create.target().isPresent()) {
                return demandsPersistentFrame(create.target().orElseThrow(), analysis, scope)
                        || demandsPersistentFrame(create.value(), analysis, scope);
            }
            return analysis.identityOf(create)
                            .map(identity -> identity.owner() != scope)
                            .orElse(true)
                    || demandsPersistentFrame(create.value(), analysis, scope);
        }
        if (expression instanceof CanonicalMultipleCreate create) {
            return analysis.identitiesOf(create)
                            .map(identities -> identities.size() != create.names().size()
                                    || identities.stream()
                                            .anyMatch(identity -> identity.owner() != scope))
                            .orElse(true)
                    || demandsPersistentFrame(create.value(), analysis, scope);
        }
        if (expression instanceof CanonicalSequence sequence) {
            return anyDemandsPersistentFrame(sequence.expressions(), analysis, scope);
        }
        if (expression instanceof CanonicalCall call) {
            return demandsPersistentFrame(call.receiver(), analysis, scope)
                    || anyDemandsPersistentFrame(call.arguments(), analysis, scope);
        }
        if (expression instanceof CanonicalSend send) {
            return demandsPersistentFrame(send.receiver(), analysis, scope)
                    || anyDemandsPersistentFrame(send.arguments(), analysis, scope);
        }
        if (expression instanceof CanonicalSuperSend superSend) {
            return anyDemandsPersistentFrame(superSend.arguments(), analysis, scope);
        }
        if (expression instanceof CanonicalMember member) {
            return demandsPersistentFrame(member.receiver(), analysis, scope);
        }
        if (expression instanceof CanonicalIdentity identity) {
            return demandsPersistentFrame(identity.left(), analysis, scope)
                    || demandsPersistentFrame(identity.right(), analysis, scope);
        }
        if (expression instanceof CanonicalNotIdentity identity) {
            return demandsPersistentFrame(identity.left(), analysis, scope)
                    || demandsPersistentFrame(identity.right(), analysis, scope);
        }
        if (expression instanceof CanonicalDerivedInequality inequality) {
            return demandsPersistentFrame(inequality.left(), analysis, scope)
                    || demandsPersistentFrame(inequality.right(), analysis, scope);
        }
        if (expression instanceof CanonicalIndexedAssign indexed) {
            return demandsPersistentFrame(indexed.receiver(), analysis, scope)
                    || demandsPersistentFrame(indexed.index(), analysis, scope)
                    || demandsPersistentFrame(indexed.value(), analysis, scope);
        }
        if (expression instanceof CanonicalMapConstruction map) {
            if (demandsPersistentFrame(map.factory(), analysis, scope)) {
                return true;
            }
            for (CanonicalMapConstruction.Entry entry : map.entries()) {
                if (demandsPersistentFrame(entry.key(), analysis, scope)
                        || demandsPersistentFrame(entry.value(), analysis, scope)) {
                    return true;
                }
            }
            return false;
        }
        if (expression instanceof CanonicalReturn returnExpression) {
            return demandsPersistentFrame(returnExpression.value(), analysis, scope);
        }
        if (expression instanceof CanonicalSpread spread) {
            return demandsPersistentFrame(spread.expression(), analysis, scope);
        }
        return true;
    }

    private static boolean anyDemandsPersistentFrame(
            List<CanonicalExpression> expressions,
            CanonicalBindingAnalysis analysis,
            CanonicalLexicalScope scope) {
        for (CanonicalExpression expression : expressions) {
            if (demandsPersistentFrame(expression, analysis, scope)) {
                return true;
            }
        }
        return false;
    }

    private static boolean resolvedInScope(
            Optional<CanonicalBindingResolution> resolution,
            CanonicalLexicalScope scope) {
        return resolution.isPresent()
                && resolution.orElseThrow() instanceof CanonicalBindingResolution.Resolved resolved
                && resolved.identity().owner() == scope;
    }
}
