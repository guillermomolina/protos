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
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * PLAT036 Candidate D, Slice 1 canonical/lowering-boundary analysis: walks a
 * canonical subtree and produces a {@link CanonicalBindingAnalysis} carrying
 * statically proven lexical binding identity, owning-scope, depth and
 * presence-candidate metadata into Bytecode lowering, without changing which
 * runtime path actually resolves any binding (the existing exact
 * String-keyed {@code ProtosActivation} lookup/write paths remain
 * behaviorally authoritative; see {@code CanonicalToBytecodeLowerer}).
 *
 * <p>The walk runs in exactly two passes per scope:
 *
 * <ol>
 *   <li>{@link #collectDeclaredNames}: an order-independent scan of a scope's
 *       own body (stopping at nested {@code Closure}/{@code Object}
 *       boundaries) that records every name the scope ever declares anywhere,
 *       so a reference occurring textually before its scope's own creation
 *       site can still recognize that scope as the legal future owner (the
 *       "late nearer binding creation" case);
 *   <li>{@link #walk}: a left-to-right, evaluation-order walk (mirroring
 *       {@code CanonicalToBytecodeLowerer}'s own emission order) that
 *       progressively marks names PRESENT as their creation/establishment
 *       sites are reached, and classifies each reference against that
 *       progressive state.
 * </ol>
 *
 * <p>A reference classifies as {@link CanonicalBindingResolution.Resolved}
 * only when the owning scope is the reference's own current scope AND that
 * scope has already established the name at this exact program point.
 * Under D179 C0 this proves stable binding identity/layout, not permanent
 * runtime presence, so direct lowering must retain the applicable presence
 * guard. Any other statically discoverable owner classifies as {@link
 * CanonicalBindingResolution.Candidate}; a name no scope in the chain ever
 * declares classifies as {@link CanonicalBindingResolution.Dynamic}, exactly
 * preserving today's receiver/member-fallback dynamic path.
 */
final class CanonicalBindingAnalyzer {
    private final Map<CanonicalClosure, CanonicalLexicalScope> closureScopes = new IdentityHashMap<>();
    private final Map<CanonicalObject, CanonicalLexicalScope> objectScopes = new IdentityHashMap<>();
    private final Map<CanonicalLookup, CanonicalBindingResolution> lookupResolutions = new IdentityHashMap<>();
    private final Map<CanonicalAssign, CanonicalBindingResolution> assignResolutions = new IdentityHashMap<>();
    private final Map<CanonicalCreate, CanonicalBindingIdentity> createIdentities = new IdentityHashMap<>();
    private final Map<CanonicalMultipleCreate, List<CanonicalBindingIdentity>> multipleCreateIdentities =
            new IdentityHashMap<>();
    private final Map<CanonicalParameter, CanonicalBindingIdentity> parameterIdentities = new IdentityHashMap<>();

    private CanonicalBindingAnalyzer() {}

    /** Analyzes a whole module/program root sequence as the {@code ROOT} scope. */
    static CanonicalBindingAnalysis analyzeModule(CanonicalSequence moduleRoot) {
        Objects.requireNonNull(moduleRoot, "moduleRoot");
        CanonicalBindingAnalyzer analyzer = new CanonicalBindingAnalyzer();
        CanonicalLexicalScope root = new CanonicalLexicalScope(CanonicalLexicalScope.Kind.ROOT, null);
        analyzer.collectDeclaredNames(moduleRoot, root);
        analyzer.walkSequence(moduleRoot, root);
        return analyzer.toAnalysis(root);
    }

    /**
     * Analyzes one Closure definition in isolation, with no statically known
     * lexical parent. Any reference escaping this Closure's own scope
     * classifies as {@link CanonicalBindingResolution.Dynamic}: this is
     * always safe (never a wrong Resolved/Candidate claim), it is simply
     * conservative about captures that a whole-module analysis could
     * otherwise classify. See {@code CanonicalToBytecodeLowerer}'s per-unit
     * lowering entry points for why isolated analysis is this slice's
     * bounded integration point.
     */
    static CanonicalBindingAnalysis analyzeClosure(CanonicalClosure closure) {
        Objects.requireNonNull(closure, "closure");
        CanonicalBindingAnalyzer analyzer = new CanonicalBindingAnalyzer();
        analyzer.walkClosure(closure, null);
        CanonicalLexicalScope topScope = analyzer.closureScopes.get(closure);
        return analyzer.toAnalysis(topScope);
    }

    private CanonicalBindingAnalysis toAnalysis(CanonicalLexicalScope topScope) {
        return new CanonicalBindingAnalysis(
                topScope,
                closureScopes,
                objectScopes,
                lookupResolutions,
                assignResolutions,
                createIdentities,
                multipleCreateIdentities,
                parameterIdentities);
    }

    // ---- Phase 1: order-independent same-scope declaration discovery ----

    private void collectDeclaredNames(CanonicalExpression expression, CanonicalLexicalScope scope) {
        switch (expression) {
            case CanonicalCreate create -> {
                if (create.target().isPresent()) {
                    collectDeclaredNames(create.target().orElseThrow(), scope);
                } else {
                    scope.declare(create.name());
                }
                collectDeclaredNames(create.value(), scope);
            }
            case CanonicalMultipleCreate create -> {
                for (String name : create.names()) {
                    scope.declare(name);
                }
                collectDeclaredNames(create.value(), scope);
            }
            case CanonicalAssign assign -> {
                assign.target().ifPresent(target -> collectDeclaredNames(target, scope));
                collectDeclaredNames(assign.value(), scope);
            }
            case CanonicalIndexedAssign indexedAssign -> {
                collectDeclaredNames(indexedAssign.receiver(), scope);
                collectDeclaredNames(indexedAssign.index(), scope);
                collectDeclaredNames(indexedAssign.value(), scope);
            }
            case CanonicalSequence sequence -> {
                for (CanonicalExpression child : sequence.expressions()) {
                    collectDeclaredNames(child, scope);
                }
            }
            case CanonicalSend send -> {
                collectDeclaredNames(send.receiver(), scope);
                for (CanonicalExpression argument : send.arguments()) {
                    collectDeclaredNames(argument, scope);
                }
            }
            case CanonicalCall call -> {
                collectDeclaredNames(call.receiver(), scope);
                for (CanonicalExpression argument : call.arguments()) {
                    collectDeclaredNames(argument, scope);
                }
            }
            case CanonicalSuperSend superSend -> {
                for (CanonicalExpression argument : superSend.arguments()) {
                    collectDeclaredNames(argument, scope);
                }
            }
            case CanonicalMember member -> collectDeclaredNames(member.receiver(), scope);
            case CanonicalReturn returnExpression -> collectDeclaredNames(returnExpression.value(), scope);
            case CanonicalSpread spread -> collectDeclaredNames(spread.expression(), scope);
            case CanonicalCompose compose -> collectDeclaredNames(compose.object(), scope);
            case CanonicalIdentity identity -> {
                collectDeclaredNames(identity.left(), scope);
                collectDeclaredNames(identity.right(), scope);
            }
            case CanonicalNotIdentity identity -> {
                collectDeclaredNames(identity.left(), scope);
                collectDeclaredNames(identity.right(), scope);
            }
            case CanonicalDerivedInequality inequality -> {
                collectDeclaredNames(inequality.left(), scope);
                collectDeclaredNames(inequality.right(), scope);
            }
            case CanonicalMapConstruction map -> {
                collectDeclaredNames(map.factory(), scope);
                for (CanonicalMapConstruction.Entry entry : map.entries()) {
                    collectDeclaredNames(entry.key(), scope);
                    collectDeclaredNames(entry.value(), scope);
                }
            }
            case CanonicalObject object -> object.parent().ifPresent(parent -> collectDeclaredNames(parent, scope));
            case CanonicalClosure ignored -> {
                /* Opaque boundary: a nested Closure's own declarations belong to its own scope. */
            }
            case CanonicalLookup ignored -> {}
            case CanonicalLiteral ignored -> {}
            case CanonicalIntrinsic ignored -> {}
        }
    }

    // ---- Phase 2: left-to-right evaluation-order walk and classification ----

    private void walkSequence(CanonicalSequence sequence, CanonicalLexicalScope scope) {
        for (CanonicalExpression expression : sequence.expressions()) {
            walk(expression, scope);
        }
    }

    private void walk(CanonicalExpression expression, CanonicalLexicalScope scope) {
        switch (expression) {
            case CanonicalLookup lookup -> lookupResolutions.put(lookup, resolve(lookup.name(), scope));
            case CanonicalLiteral ignored -> {}
            case CanonicalIntrinsic ignored -> {}
            case CanonicalMember member -> walk(member.receiver(), scope);
            case CanonicalReturn returnExpression -> walk(returnExpression.value(), scope);
            case CanonicalSpread spread -> walk(spread.expression(), scope);
            case CanonicalCompose compose -> walk(compose.object(), scope);
            case CanonicalIdentity identity -> {
                walk(identity.left(), scope);
                walk(identity.right(), scope);
            }
            case CanonicalNotIdentity identity -> {
                walk(identity.left(), scope);
                walk(identity.right(), scope);
            }
            case CanonicalDerivedInequality inequality -> {
                walk(inequality.left(), scope);
                walk(inequality.right(), scope);
            }
            case CanonicalSequence sequence -> walkSequence(sequence, scope);
            case CanonicalSend send -> {
                walk(send.receiver(), scope);
                for (CanonicalExpression argument : send.arguments()) {
                    walk(argument, scope);
                }
            }
            case CanonicalCall call -> {
                walk(call.receiver(), scope);
                for (CanonicalExpression argument : call.arguments()) {
                    walk(argument, scope);
                }
            }
            case CanonicalSuperSend superSend -> {
                for (CanonicalExpression argument : superSend.arguments()) {
                    walk(argument, scope);
                }
            }
            case CanonicalIndexedAssign indexedAssign -> {
                walk(indexedAssign.receiver(), scope);
                walk(indexedAssign.index(), scope);
                walk(indexedAssign.value(), scope);
            }
            case CanonicalMapConstruction map -> {
                walk(map.factory(), scope);
                for (CanonicalMapConstruction.Entry entry : map.entries()) {
                    walk(entry.key(), scope);
                    walk(entry.value(), scope);
                }
            }
            case CanonicalCreate create -> walkCreate(create, scope);
            case CanonicalMultipleCreate create -> walkMultipleCreate(create, scope);
            case CanonicalAssign assign -> walkAssign(assign, scope);
            case CanonicalClosure closure -> walkClosure(closure, scope);
            case CanonicalObject object -> walkObject(object, scope);
        }
    }

    private void walkCreate(CanonicalCreate create, CanonicalLexicalScope scope) {
        if (create.target().isPresent()) {
            /* Explicit member target: dynamic object-member creation, not a lexical binding. */
            walk(create.target().orElseThrow(), scope);
            walk(create.value(), scope);
            return;
        }
        walk(create.value(), scope);
        CanonicalBindingIdentity identity = scope.declare(create.name());
        scope.markEstablished(create.name());
        createIdentities.put(create, identity);
    }

    private void walkMultipleCreate(CanonicalMultipleCreate create, CanonicalLexicalScope scope) {
        walk(create.value(), scope);
        List<CanonicalBindingIdentity> identities = new ArrayList<>(create.names().size());
        for (String name : create.names()) {
            CanonicalBindingIdentity identity = scope.declare(name);
            scope.markEstablished(name);
            identities.add(identity);
        }
        multipleCreateIdentities.put(create, List.copyOf(identities));
    }

    private void walkAssign(CanonicalAssign assign, CanonicalLexicalScope scope) {
        if (assign.target().isPresent()) {
            /* Explicit member target: dynamic object-member assignment, not a lexical binding. */
            walk(assign.target().orElseThrow(), scope);
            walk(assign.value(), scope);
            return;
        }
        /* Destination is resolved before the RHS evaluates, matching the runtime
         * ResolveWritableLexicalTarget-before-value ordering this slice must preserve. */
        assignResolutions.put(assign, resolve(assign.name(), scope));
        walk(assign.value(), scope);
    }

    private void walkClosure(CanonicalClosure closure, CanonicalLexicalScope enclosing) {
        CanonicalLexicalScope closureScope = new CanonicalLexicalScope(CanonicalLexicalScope.Kind.CLOSURE, enclosing);
        closureScopes.put(closure, closureScope);
        collectDeclaredNames(closure.body(), closureScope);

        /* Every parameter's identity exists from closure entry (declared upfront), even
         * though presence is established only at its own sequential binding point below.
         * This lets a forward/self default reference carry known identity without being
         * classified PRESENT early. */
        for (CanonicalParameter parameter : closure.parameters()) {
            CanonicalBindingIdentity identity = closureScope.declare(parameter.name());
            parameterIdentities.put(parameter, identity);
        }

        for (CanonicalParameter parameter : closure.parameters()) {
            /* Sequential/default establishment: a default expression only ever sees
             * parameters already established earlier in this same declared order. */
            parameter.defaultValue().ifPresent(defaultValue -> walk(defaultValue, closureScope));
            closureScope.markEstablished(parameter.name());
        }

        walkSequence(closure.body(), closureScope);
    }

    private void walkObject(CanonicalObject object, CanonicalLexicalScope enclosing) {
        /* The parent expression evaluates in the enclosing scope, before construction begins. */
        object.parent().ifPresent(parent -> walk(parent, enclosing));

        CanonicalLexicalScope objectScope =
                new CanonicalLexicalScope(CanonicalLexicalScope.Kind.OBJECT_BODY, enclosing);
        objectScopes.put(object, objectScope);
        collectDeclaredNames(object.body(), objectScope);
        walkSequence(object.body(), objectScope);
    }

    private CanonicalBindingResolution resolve(String name, CanonicalLexicalScope referenceScope) {
        CanonicalLexicalScope scope = referenceScope;
        int depth = 0;
        while (scope != null) {
            if (scope.declaresName(name)) {
                CanonicalBindingIdentity identity = scope.identity(name);
                if (scope.isEstablished(name)) {
                    if (depth == 0) {
                        return new CanonicalBindingResolution.Resolved(identity);
                    }
                    return new CanonicalBindingResolution.CapturedResolved(identity, depth);
                }
                return new CanonicalBindingResolution.Candidate(identity, depth);
            }
            scope = scope.outwardScope();
            depth++;
        }
        return new CanonicalBindingResolution.Dynamic();
    }
}
