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
import com.guillermomolina.protos.semantic.ast.CanonicalNamedArgument;
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
 * scope has already established the name at this exact program point. Any
 * other statically discoverable owner classifies as {@link
 * CanonicalBindingResolution.Candidate} or {@link
 * CanonicalBindingResolution.CapturedResolved}; a name no scope in the chain
 * ever declares classifies as {@link CanonicalBindingResolution.Dynamic}.
 *
 * <p>I115 storage classification, computed by the same walk and complete
 * before lowering: a {@code ROOT} declaration is {@code NAME} storage (the
 * module context), an {@code OBJECT_BODY} declaration is {@code MEMBER}
 * storage, and a {@code CLOSURE} declaration is {@code LOCAL} unless a
 * reference crosses at least one nested Closure boundary to reach it, which
 * makes it {@code CELL}. Every Closure scope crossed by such a reference
 * records the owner's identity among its free cells. A {@code Candidate}
 * owned by an outer Closure selects that owner's cell; no reference is ever
 * classified as "cell or lookup".
 *
 * <p>I115-S1c-L: static identity is not runtime presence. After the walk,
 * {@link #finishLexicalAccessPlan} decides for every bare read and assignment
 * site how it accesses its binding at run time ({@link
 * CanonicalBindingAnalysis.LexicalAccess}): directly when the owner's
 * binding is provably the PRESENT nearest binding, presence-checked when it
 * may be ABSENT (a {@code Candidate}, or a binding of a scope whose Context
 * may be observed and therefore removed), and by live lexical selection when
 * a nearer dynamically created binding is possible. A site that may continue
 * past its owner marks every further Closure declaring the same name as
 * requiring a frame-backed view, so live selection can reach that binding;
 * a captured cell may therefore exist long before its value becomes PRESENT.
 *

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

    /*
     * I115-S1c-L analysis-time facts, never part of the result: the scope in
     * which each bare read and assignment site occurs, and the creation sites
     * whose binding may already be PRESENT when they execute.
     */
    private final Map<CanonicalLookup, CanonicalLexicalScope> lookupSites = new IdentityHashMap<>();
    private final Map<CanonicalAssign, CanonicalLexicalScope> assignSites = new IdentityHashMap<>();
    private final Map<Object, CreationSite> creationSites = new IdentityHashMap<>();
    private final Map<CanonicalLookup, CanonicalBindingAnalysis.LexicalAccess> lookupAccesses =
            new IdentityHashMap<>();
    private final Map<CanonicalAssign, CanonicalBindingAnalysis.LexicalAccess> assignAccesses =
            new IdentityHashMap<>();
    private final java.util.Set<Object> presenceCheckedCreates =
            java.util.Collections.newSetFromMap(new IdentityHashMap<>());
    /*
     * Creation-site presence is determined within its genuine lexical
     * owner. A contextual callback is a separate Closure activation.
     */
    private record CreationSite(
            CanonicalLexicalScope scope, boolean alreadyEstablished) {}

    private CanonicalBindingAnalyzer() {}

    /** Analyzes a whole module/program root sequence as the {@code ROOT} scope. */
    static CanonicalBindingAnalysis analyzeModule(CanonicalSequence moduleRoot) {
        Objects.requireNonNull(moduleRoot, "moduleRoot");
        CanonicalBindingAnalyzer analyzer = new CanonicalBindingAnalyzer();
        CanonicalLexicalScope root = new CanonicalLexicalScope(CanonicalLexicalScope.Kind.ROOT, null);
        analyzer.collectDeclaredNames(moduleRoot, root);
        analyzer.walkSequence(moduleRoot, root);
        analyzer.finishLexicalAccessPlan();
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
        analyzer.finishLexicalAccessPlan();
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
                parameterIdentities,
                lookupAccesses,
                assignAccesses,
                presenceCheckedCreates,
                lookupSites,
                assignSites);
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
            case CanonicalNamedArgument named ->
                    collectDeclaredNames(named.expression(), scope);
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
            case CanonicalLookup lookup -> {
                lookupResolutions.put(lookup, resolve(lookup.name(), scope));
                lookupSites.put(lookup, scope);
            }
            case CanonicalLiteral ignored -> {}
            case CanonicalIntrinsic intrinsic -> {
                if (intrinsic.kind() == CanonicalIntrinsic.Kind.CONTEXT) {
                    scope.markObservesContext();
                }
            }
            case CanonicalMember member -> walk(member.receiver(), scope);
            case CanonicalReturn returnExpression -> walk(returnExpression.value(), scope);
            case CanonicalSpread spread -> walk(spread.expression(), scope);
            case CanonicalNamedArgument named -> walk(named.expression(), scope);
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
        creationSites.put(
                create,
                new CreationSite(
                        scope, scope.isEstablished(create.name())));
        scope.markEstablished(create.name());
        createIdentities.put(create, identity);
    }

    private void walkMultipleCreate(CanonicalMultipleCreate create, CanonicalLexicalScope scope) {
        walk(create.value(), scope);
        boolean alreadyEstablished = false;
        for (String name : create.names()) {
            alreadyEstablished |= scope.declaresName(name) && scope.isEstablished(name);
        }
        // A repeated name inside one multiple creation is itself a duplicate.
        alreadyEstablished |= new java.util.HashSet<>(create.names()).size() != create.names().size();
        creationSites.put(
                create, new CreationSite(scope, alreadyEstablished));
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
        assignSites.put(assign, scope);
        walk(assign.value(), scope);
    }

    private void walkClosure(CanonicalClosure closure, CanonicalLexicalScope enclosing) {
        CanonicalLexicalScope closureScope =
                new CanonicalLexicalScope(
                        CanonicalLexicalScope.Kind.CLOSURE,
                        enclosing);
        closureScopes.put(closure, closureScope);

        /*
         * Every parameter's identity exists from Closure entry. Declare those
         * identities before discovering body declarations: declaration-set
         * membership is semantically order-independent, while this insertion
         * order is also reused as the backend frame layout. Matching the
         * unavoidable parameter-before-body establishment order lets the
         * common frame-backed authority remain in its compact representation
         * without changing any semantic presence rule.
         */
        for (CanonicalParameter parameter : closure.parameters()) {
            CanonicalBindingIdentity identity =
                    closureScope.declare(parameter.name());
            parameterIdentities.put(parameter, identity);
        }

        collectDeclaredNames(closure.body(), closureScope);

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

    /*
     * I115: identity is purely lexical. The nearest declaring scope is the
     * reference's static owner whether or not its binding point has executed;
     * establishment only distinguishes Resolved/CapturedResolved from
     * Candidate. A Candidate is a possible owner, not a promise: when its
     * binding is ABSENT at run time, the site continues ordinary lookup with
     * live lexical selection and may reach an outer binding or the receiver
     * (see finishLexicalAccessPlan). The owner's cell is classified here even
     * before establishment, because it must exist before its value is PRESENT.
     */
    private CanonicalBindingResolution resolve(String name, CanonicalLexicalScope referenceScope) {
        CanonicalLexicalScope scope = referenceScope;
        int depth = 0;
        while (scope != null) {
            if (scope.declaresName(name)) {
                CanonicalBindingIdentity identity = scope.identity(name);
                classifyClosureCapture(identity, referenceScope);
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

    /*
     * I115: a reference reaching a Closure-owned binding through one or more
     * nested Closure scopes makes that binding a cell, and each crossed
     * Closure scope carries the cell. Object construction scopes are not
     * capture scopes and are skipped exactly as outwardScope() skips them.
     */
    private static void classifyClosureCapture(
            CanonicalBindingIdentity identity,
            CanonicalLexicalScope referenceScope) {
        CanonicalLexicalScope owner = identity.owner();
        if (owner.kind() != CanonicalLexicalScope.Kind.CLOSURE) {
            return;
        }
        List<CanonicalLexicalScope> crossed = new ArrayList<>();
        for (CanonicalLexicalScope scope = referenceScope;
                scope != null && scope != owner;
                scope = scope.outwardScope()) {
            if (scope.kind() == CanonicalLexicalScope.Kind.CLOSURE) {
                crossed.add(scope);
            }
        }
        if (crossed.isEmpty()) {
            return;
        }
        owner.markCell(identity.name());
        for (CanonicalLexicalScope closureScope : crossed) {
            closureScope.addFreeCell(identity);
        }
    }

    // ---- I115-S1c-L: run-time access plan --------------------------------------

    /*
     * Completed once after the walk, when every scope's final facts (its
     * declarations, cells and Context observation) are known. Decides every
     * bare read/assignment site's access, marks the Closure scopes whose
     * bindings live selection may need to reach by name, and the creations
     * that must apply the ordinary ':' presence rules.
     */
    private void finishLexicalAccessPlan() {
        for (Map.Entry<CanonicalLookup, CanonicalBindingResolution> entry : lookupResolutions.entrySet()) {
            CanonicalLexicalScope site = lookupSites.get(entry.getKey());
            lookupAccesses.put(
                    entry.getKey(),
                    planSite(entry.getValue(), site, entry.getKey().name()));
        }
        for (Map.Entry<CanonicalAssign, CanonicalBindingResolution> entry : assignResolutions.entrySet()) {
            CanonicalLexicalScope site = assignSites.get(entry.getKey());
            assignAccesses.put(
                    entry.getKey(),
                    planSite(entry.getValue(), site, entry.getKey().name()));
        }
        for (Map.Entry<Object, CreationSite> entry : creationSites.entrySet()) {
            CreationSite creation = entry.getValue();
            if (creation.scope().kind() == CanonicalLexicalScope.Kind.CLOSURE
                    && (creation.alreadyEstablished() || creation.scope().observesContext())) {
                presenceCheckedCreates.add(entry.getKey());
            }
        }
    }

    private CanonicalBindingAnalysis.LexicalAccess planSite(
            CanonicalBindingResolution resolution, CanonicalLexicalScope site, String name) {
        CanonicalBindingAnalysis.LexicalAccess access = lexicalAccessOf(resolution, site);
        CanonicalLexicalScope owner = ownerOrNull(resolution);
        if (owner != null && access == CanonicalBindingAnalysis.LexicalAccess.SELECTED) {
            markLexicalViews(owner, site, name);
            markEnclosingFrameViewForConstruction(owner, site, name);
        } else if (owner != null && access == CanonicalBindingAnalysis.LexicalAccess.PRESENCE_CHECKED) {
            markLexicalViews(owner.outwardScope(), site, name);
            markEnclosingFrameViewForConstruction(owner.outwardScope(), site, name);
        }
        return access;
    }

    /*
     * I115-S1c-L (OBJECT_MODEL.md §20): a selecting site inside an inline
     * object body searches the object under construction, then the actual
     * bindings of the enclosing genuine lexical contexts. When the nearest
     * enclosing Closure scope (the root in whose frame the construction is
     * emitted inline) lies on the selection path and declares the name, its
     * LOCAL/CELL binding is reachable by name only through the frame-backed
     * view, so every object body from the site out to that root is recorded
     * as requiring the view before its construction activation captures the
     * enclosing environment. Nothing is recorded for a body whose selection
     * cannot reach a binding of that root, so unrelated frames stay compact.
     */
    private static void markEnclosingFrameViewForConstruction(
            CanonicalLexicalScope from, CanonicalLexicalScope site, String name) {
        if (site == null || site.kind() != CanonicalLexicalScope.Kind.OBJECT_BODY || from == null) {
            return;
        }
        CanonicalLexicalScope enclosingRoot = site.outwardScope();
        if (enclosingRoot == null
                || enclosingRoot.kind() != CanonicalLexicalScope.Kind.CLOSURE
                || !enclosingRoot.declaresName(name)
                || !onSelectionPath(from, enclosingRoot)) {
            return;
        }
        for (CanonicalLexicalScope scope = site;
                scope != null && scope != enclosingRoot;
                scope = scope.lexicalParent()) {
            if (scope.kind() == CanonicalLexicalScope.Kind.OBJECT_BODY) {
                scope.markRequiresEnclosingFrameView();
            }
        }
    }

    /* Whether {@code target} is {@code from} or one of its outward scopes. */
    private static boolean onSelectionPath(CanonicalLexicalScope from, CanonicalLexicalScope target) {
        for (CanonicalLexicalScope scope = from; scope != null; scope = scope.outwardScope()) {
            if (scope == target) {
                return true;
            }
        }
        return false;
    }

    private static CanonicalLexicalScope ownerOrNull(CanonicalBindingResolution resolution) {
        if (resolution instanceof CanonicalBindingResolution.Resolved resolved) {
            return resolved.identity().owner();
        }
        if (resolution instanceof CanonicalBindingResolution.CapturedResolved captured) {
            return captured.identity().owner();
        }
        if (resolution instanceof CanonicalBindingResolution.Candidate candidate) {
            return candidate.identity().owner();
        }
        return null;
    }

    /*
     * The access of one site. A Closure scope from the site (inclusive) to
     * the owner (exclusive) whose Context may be observed can hold a nearer
     * dynamically created binding, so the site is SELECTED. Otherwise the
     * owner's binding is the nearest possible one; it is PRESENCE_CHECKED
     * when it may be ABSENT (Candidate, or a Closure owner whose Context may
     * be observed), else DIRECT. A module owner observes its own membership
     * through ReadGlobal/WriteGlobal, so it never needs the presence check.
     */
    private static CanonicalBindingAnalysis.LexicalAccess lexicalAccessOf(
            CanonicalBindingResolution resolution, CanonicalLexicalScope site) {
        CanonicalLexicalScope owner = ownerOrNull(resolution);
        if (owner == null || site == null) {
            return CanonicalBindingAnalysis.LexicalAccess.SELECTED;
        }
        for (CanonicalLexicalScope scope = site;
                scope != null && scope != owner;
                scope = scope.outwardScope()) {
            if (scope.kind() == CanonicalLexicalScope.Kind.CLOSURE && scope.observesContext()) {
                return CanonicalBindingAnalysis.LexicalAccess.SELECTED;
            }
        }
        if (resolution instanceof CanonicalBindingResolution.Candidate) {
            return CanonicalBindingAnalysis.LexicalAccess.PRESENCE_CHECKED;
        }
        if (owner.kind() == CanonicalLexicalScope.Kind.CLOSURE && owner.observesContext()) {
            return CanonicalBindingAnalysis.LexicalAccess.PRESENCE_CHECKED;
        }
        return CanonicalBindingAnalysis.LexicalAccess.DIRECT;
    }

    /*
     * Every Closure scope from {@code from} outward that declares {@code name}
     * (other than the site's own scope, whose frame the site reaches directly)
     * may hold the binding live selection selects, so its frame must be bound
     * to a frame-backed view before it creates a capturing Closure.
     */
    private static void markLexicalViews(
            CanonicalLexicalScope from, CanonicalLexicalScope site, String name) {
        for (CanonicalLexicalScope scope = from; scope != null; scope = scope.outwardScope()) {
            if (scope != site
                    && scope.kind() == CanonicalLexicalScope.Kind.CLOSURE
                    && scope.declaresName(name)) {
                scope.markRequiresLexicalView();
            }
        }
    }
}
