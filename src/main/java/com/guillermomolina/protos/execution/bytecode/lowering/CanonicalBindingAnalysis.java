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
import com.guillermomolina.protos.semantic.ast.CanonicalMultipleCreate;
import com.guillermomolina.protos.semantic.ast.CanonicalLookup;
import com.guillermomolina.protos.semantic.ast.CanonicalObject;
import com.guillermomolina.protos.semantic.ast.CanonicalParameter;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * PLAT036 Candidate D, Slice 1 immutable result of one {@link
 * CanonicalBindingAnalyzer} run: the discovered {@link CanonicalLexicalScope}
 * tree plus every reference/declaration site's binding metadata for the
 * analyzed canonical subtree.
 *
 * <p>All node-keyed lookups use identity, never the canonical AST records'
 * generated structural {@code equals}/{@code hashCode}, because two
 * structurally identical nodes at different source positions (or two
 * genuinely distinct occurrences) must never be conflated. Callers must key
 * by the exact canonical AST node instance obtained from the same
 * compilation.
 */
final class CanonicalBindingAnalysis {
    private final CanonicalLexicalScope topScope;
    private final Map<CanonicalClosure, CanonicalLexicalScope> closureScopes;
    private final Map<CanonicalObject, CanonicalLexicalScope> objectScopes;
    private final Map<CanonicalLookup, CanonicalBindingResolution> lookupResolutions;
    private final Map<CanonicalAssign, CanonicalBindingResolution> assignResolutions;
    private final Map<CanonicalCreate, CanonicalBindingIdentity> createIdentities;
    private final Map<CanonicalMultipleCreate, List<CanonicalBindingIdentity>> multipleCreateIdentities;
    private final Map<CanonicalParameter, CanonicalBindingIdentity> parameterIdentities;
    private final Map<CanonicalLookup, LexicalAccess> lookupAccesses;
    private final Map<CanonicalAssign, LexicalAccess> assignAccesses;
    private final Set<Object> presenceCheckedCreates;
    private final Map<CanonicalLookup, CanonicalLexicalScope> lookupSites;
    private final Map<CanonicalAssign, CanonicalLexicalScope> assignSites;

    /**
     * I115-S1c-L: how a bare read or assignment site accesses its binding at
     * run time, decided once after the complete analysis walk from the site's
     * static resolution and the final per-scope facts. It is a property of
     * the site's physical access, never a second semantic resolution: the
     * {@link CanonicalBindingResolution} remains the binding identity.
     */
    enum LexicalAccess {
        /**
         * The owner's binding is the actual nearest binding and is PRESENT
         * whenever the site executes: an established binding whose owner's
         * Context can never be observed, reached through no scope whose
         * Context can be observed. The site accesses the physical storage
         * directly ({@code LoadLocal}/{@code StoreLocal}, the plain cell
         * operations, or the module {@code ReadGlobal}/{@code WriteGlobal},
         * which observe module membership themselves).
         */
        DIRECT,
        /**
         * The owner's binding is the nearest possible binding, but it may be
         * ABSENT at run time (a {@code Candidate}, or a binding of a scope
         * whose Context can be observed): the site accesses the physical
         * storage while it is PRESENT and otherwise continues with live
         * lexical selection.
         */
        PRESENCE_CHECKED,
        /**
         * The actual nearest binding is not proven by any physical binding
         * identity (a {@code Dynamic} name, or a scope between the site and
         * the owner whose Context can be observed and may hold a dynamically
         * created nearer binding): the site uses live lexical selection.
         */
        SELECTED
    }

    CanonicalBindingAnalysis(
            CanonicalLexicalScope topScope,
            Map<CanonicalClosure, CanonicalLexicalScope> closureScopes,
            Map<CanonicalObject, CanonicalLexicalScope> objectScopes,
            Map<CanonicalLookup, CanonicalBindingResolution> lookupResolutions,
            Map<CanonicalAssign, CanonicalBindingResolution> assignResolutions,
            Map<CanonicalCreate, CanonicalBindingIdentity> createIdentities,
            Map<CanonicalMultipleCreate, List<CanonicalBindingIdentity>> multipleCreateIdentities,
            Map<CanonicalParameter, CanonicalBindingIdentity> parameterIdentities,
            Map<CanonicalLookup, LexicalAccess> lookupAccesses,
            Map<CanonicalAssign, LexicalAccess> assignAccesses,
            Set<Object> presenceCheckedCreates,
            Map<CanonicalLookup, CanonicalLexicalScope> lookupSites,
            Map<CanonicalAssign, CanonicalLexicalScope> assignSites) {
        this.topScope = Objects.requireNonNull(topScope, "topScope");
        this.closureScopes = copyOfIdentity(closureScopes);
        this.objectScopes = copyOfIdentity(objectScopes);
        this.lookupResolutions = copyOfIdentity(lookupResolutions);
        this.assignResolutions = copyOfIdentity(assignResolutions);
        this.createIdentities = copyOfIdentity(createIdentities);
        this.multipleCreateIdentities = copyOfIdentity(multipleCreateIdentities);
        this.parameterIdentities = copyOfIdentity(parameterIdentities);
        this.lookupAccesses = copyOfIdentity(lookupAccesses);
        this.assignAccesses = copyOfIdentity(assignAccesses);
        Set<Object> checked = Collections.newSetFromMap(new IdentityHashMap<>());
        checked.addAll(Objects.requireNonNull(presenceCheckedCreates, "presenceCheckedCreates"));
        this.presenceCheckedCreates = Collections.unmodifiableSet(checked);
        this.lookupSites = copyOfIdentity(lookupSites);
        this.assignSites = copyOfIdentity(assignSites);
    }

    /* I115-S1c-L: the scope of each bare read site (rematerialization validation only). */
    Map<CanonicalLookup, CanonicalLexicalScope> lookupSiteScopes() {
        return lookupSites;
    }

    /* I115-S1c-L: the scope of each bare assignment site (rematerialization validation only). */
    Map<CanonicalAssign, CanonicalLexicalScope> assignSiteScopes() {
        return assignSites;
    }

    private static <K, V> Map<K, V> copyOfIdentity(Map<K, V> source) {
        Objects.requireNonNull(source, "source");
        return Collections.unmodifiableMap(new IdentityHashMap<>(source));
    }

    /*
     * I095-B1: an activation-transparent inline body cannot
     * contain lexical binding sites belonging to the callback.
     * Analyze the callback in isolation; do not accidentally
     * include sites belonging to its enclosing module/root.
     *
     * This is a conservative whole-block eligibility proof,
     * not a classification of expression or message types.
     */
    /*
     * I095-B4: this callback may read the direct enclosing root
     * without its own physical activation only when every lookup
     * is statically captured from that exact root, at depth one.
     *
     * The isolated analysis supplies precisely this callback's
     * lookup sites. The enclosing whole-tree analysis supplies
     * their real lexical owners.
     *
     * All writes, creations, parameters, dynamic lookups and
     * unknown/candidate resolutions remain on B-prime.
     */
    boolean admitsDirectParentCaptures(
            CanonicalBindingAnalysis enclosing,
            CanonicalLexicalScope parentScope,
            java.util.Set<String> parentFrameNames,
            boolean parentHasFrameLayout) {
        if (!createIdentities.isEmpty()
                || !multipleCreateIdentities.isEmpty()
                || !parameterIdentities.isEmpty()) {
            return false;
        }

        if (lookupResolutions.isEmpty()
                && assignResolutions.isEmpty()) {
            return true;
        }

        if (enclosing == null
                || parentScope == null
                || !parentHasFrameLayout) {
            return false;
        }

        for (CanonicalLookup lookup : lookupResolutions.keySet()) {
            CanonicalBindingResolution resolution =
                    enclosing.resolutionOf(lookup).orElse(null);

            if (resolution instanceof CanonicalBindingResolution.Candidate candidate
                    && admittedInlineCandidateRead(candidate, parentScope)) {
                // The physical parent performs the regular presence-aware
                // named lexical lookup. Never claim that Candidate is PRESENT.
                continue;
            }
            if (Boolean.getBoolean("protos.i115.traceInlineAdmission")
                    && parentFrameNames.contains("result")
                    && parentFrameNames.contains("n")) {
                System.err.println("I115_CAPTURE_LOOKUP name=" + lookup.name()
                        + " resolution=" + resolution
                        + " parentKind=" + parentScope.kind()
                        + " parentNames=" + parentFrameNames
                        + " inlineGlobal=" + (resolution instanceof
                                CanonicalBindingResolution.CapturedResolved c
                                && admittedInlineGlobalRead(c, parentScope)));
            }

            if (!(resolution
                    instanceof CanonicalBindingResolution.CapturedResolved
                            captured)) {
                return false;
            }

            if (admittedInlineGlobalRead(captured, parentScope)) {
                // The callback is removed physically: the enclosing root's
                // global read uses the same lexical owner at depth minus one.
                continue;
            }
            if (captured.identity().owner() != parentScope
                    || captured.lexicalDepth() != 1
                    || !parentFrameNames.contains(
                            captured.identity().name())) {
                return false;
            }
        }

        for (CanonicalAssign assign : assignResolutions.keySet()) {
            if (assign.target().isPresent()) {
                return false;
            }

            CanonicalBindingResolution resolution =
                    enclosing.resolutionOf(assign).orElse(null);
            if (Boolean.getBoolean("protos.i115.traceInlineAdmission")
                    && parentFrameNames.contains("result")
                    && parentFrameNames.contains("n")) {
                System.err.println("I115_CAPTURE_ASSIGN name=" + assign.name()
                        + " resolution=" + resolution);
            }

            if (!(resolution
                    instanceof CanonicalBindingResolution.CapturedResolved
                            captured)) {
                return false;
            }

            if (captured.identity().owner() != parentScope
                    || captured.lexicalDepth() != 1
                    || !parentFrameNames.contains(
                            captured.identity().name())) {
                return false;
            }
        }

        return true;
    }

    /**
     * I095-B6b: a callback with its own local bindings may also reference
     * statically established names owned by its immediate physical parent.
     * Isolated analysis supplies callback-owned identities; enclosing analysis
     * supplies the exact capture owner. Neither name spelling nor a candidate
     * binding is sufficient evidence for the parent route.
     */
    boolean admitsOwnBindingsAndDirectParentCaptures(
            CanonicalBindingAnalysis enclosing,
            CanonicalLexicalScope ownScope,
            CanonicalLexicalScope parentScope,
            java.util.Set<String> parentFrameNames,
            boolean parentHasFrameLayout) {
        if (enclosing == null
                || ownScope == null
                || parentScope == null
                || !parentHasFrameLayout
                || !multipleCreateIdentities.isEmpty()
                || !parameterIdentities.isEmpty()) {
            return false;
        }

        for (CanonicalBindingIdentity identity : createIdentities.values()) {
            if (identity.owner() != ownScope) {
                return false;
            }
        }

        for (var entry : lookupResolutions.entrySet()) {
            CanonicalBindingResolution local = entry.getValue();
            if (local instanceof CanonicalBindingResolution.Resolved resolved
                    && resolved.identity().owner() == ownScope) {
                continue;
            }
            CanonicalBindingResolution parent =
                    enclosing.resolutionOf(entry.getKey()).orElse(null);
            if (parent instanceof CanonicalBindingResolution.Candidate candidate
                    && admittedInlineCandidateRead(candidate, parentScope)) {
                continue;
            }
            if (!(parent instanceof CanonicalBindingResolution.CapturedResolved captured)
                    || (!admittedInlineGlobalRead(captured, parentScope)
                            && !directParentCapture(
                                    parent, parentScope, parentFrameNames))) {
                return false;
            }
        }

        for (var entry : assignResolutions.entrySet()) {
            CanonicalBindingResolution local = entry.getValue();
            if (local instanceof CanonicalBindingResolution.Resolved resolved
                    && resolved.identity().owner() == ownScope) {
                continue;
            }
            if (!directParentCapture(
                    enclosing.resolutionOf(entry.getKey()).orElse(null),
                    parentScope, parentFrameNames)) {
                return false;
            }
        }
        return true;
    }

    /**
     * I115: a read in an inlined callback may refer to an enclosing module
     * declaration. The lexical owner and complete depth are proven against
     * the current parent's scope chain; no name-only global assumption is
     * allowed. Writes still use the separate direct-parent proof.
     */
    static boolean admittedInlineGlobalRead(
            CanonicalBindingResolution.CapturedResolved captured,
            CanonicalLexicalScope parentScope) {
        if (parentScope == null
                || captured.identity().owner().kind() != CanonicalLexicalScope.Kind.ROOT
                || captured.lexicalDepth() < 2) {
            return false;
        }
        int depth = 1;
        if (Boolean.getBoolean("protos.i115.traceInlineAdmission")) {
            System.err.println("I115_CAPTURE_GLOBAL owner=" + captured.identity().owner()
                    + " name=" + captured.identity().name()
                    + " sourceDepth=" + captured.lexicalDepth()
                    + " parent=" + parentScope);
        }
        for (CanonicalLexicalScope current = parentScope;
                current != null;
                current = current.outwardScope(), depth++) {
            if (current == captured.identity().owner()) {
                return captured.lexicalDepth() == depth;
            }
        }
        return false;
    }

    /**
     * I115: exact identity/depth proof for a Candidate read whose Closure
     * activation is eliminated. Candidate is NOT a PRESENT proof. The
     * physical enclosing root must execute the ordinary named lexical read
     * so runtime presence, shadowing and dynamic lookup remain authoritative.
     *
     * Depth is counted from the original callback: the physical parent's
     * depth to the owner is one less. Writes are intentionally excluded.
     */
    static boolean admittedInlineCandidateRead(
            CanonicalBindingResolution.Candidate candidate,
            CanonicalLexicalScope parentScope) {
        if (parentScope == null || candidate.lexicalDepth() < 1) {
            return false;
        }
        int depth = 1;
        for (CanonicalLexicalScope current = parentScope;
                current != null;
                current = current.outwardScope(), depth++) {
            if (current == candidate.identity().owner()) {
                return candidate.lexicalDepth() == depth
                        && current.declaresName(candidate.identity().name());
            }
        }
        return false;
    }

    private static boolean directParentCapture(
            CanonicalBindingResolution resolution,
            CanonicalLexicalScope parentScope,
            java.util.Set<String> parentFrameNames) {
        return resolution
                        instanceof CanonicalBindingResolution.CapturedResolved captured
                && captured.identity().owner() == parentScope
                && captured.lexicalDepth() == 1
                && parentFrameNames.contains(captured.identity().name());
    }

    int lexicalCreateSiteCount() {
        return createIdentities.size();
    }

    CanonicalLexicalScope topScope() {
        return topScope;
    }

    Optional<CanonicalLexicalScope> scopeOf(CanonicalClosure closure) {
        return Optional.ofNullable(closureScopes.get(closure));
    }

    Optional<CanonicalLexicalScope> scopeOf(CanonicalObject object) {
        return Optional.ofNullable(objectScopes.get(object));
    }

    Optional<CanonicalBindingResolution> resolutionOf(CanonicalLookup lookup) {
        return Optional.ofNullable(lookupResolutions.get(lookup));
    }

    Optional<CanonicalBindingResolution> resolutionOf(CanonicalAssign assign) {
        return Optional.ofNullable(assignResolutions.get(assign));
    }

    Optional<CanonicalBindingIdentity> identityOf(CanonicalCreate create) {
        return Optional.ofNullable(createIdentities.get(create));
    }

    Optional<List<CanonicalBindingIdentity>> identitiesOf(CanonicalMultipleCreate create) {
        return Optional.ofNullable(multipleCreateIdentities.get(create));
    }

    Optional<CanonicalBindingIdentity> identityOf(CanonicalParameter parameter) {
        return Optional.ofNullable(parameterIdentities.get(parameter));
    }

    /**
     * I115-S1c-L: the run-time access of a bare read site of this analysis.
     * Every analyzed site has a recorded decision; a missing one is an
     * internal invariant failure, never an implicit {@code DIRECT} proof.
     */
    LexicalAccess accessOf(CanonicalLookup lookup) {
        return requireAccess(lookupAccesses.get(lookup), lookup.name());
    }

    /** I115-S1c-L: the run-time access of a bare assignment's destination. */
    LexicalAccess accessOf(CanonicalAssign assign) {
        return requireAccess(assignAccesses.get(assign), assign.name());
    }

    private static LexicalAccess requireAccess(LexicalAccess access, String name) {
        if (access == null) {
            throw new IllegalStateException(
                    "I115-S1c-L: lexical site without a recorded run-time access: " + name);
        }
        return access;
    }

    /**
     * I115-S1c-L: whether a target-less creation of a Closure-owned binding
     * may find its binding PRESENT or its Context CLOSED/FROZEN at run time
     * (the scope's Context may be observed, or the same scope already
     * established the name earlier), so it must apply the ordinary {@code :}
     * creation rules instead of storing directly. Normatively every
     * creation applies those rules; the direct store elsewhere is only the
     * proven-equivalent fast path, except for creations inside contextual
     * control operands, which are withheld as a known unresolved
     * implementation divergence (I115-S1c-D, see CanonicalBindingAnalyzer).
     */
    boolean requiresPresenceCheckedCreation(CanonicalCreate create) {
        return presenceCheckedCreates.contains(create);
    }

    /** I115-S1c-L: the multiple-creation counterpart of {@link #requiresPresenceCheckedCreation}. */
    boolean requiresPresenceCheckedCreation(CanonicalMultipleCreate create) {
        return presenceCheckedCreates.contains(create);
    }

    /** I115: the lowering-time storage of {@code identity}. */
    static CanonicalLexicalScope.Storage storageOf(CanonicalBindingIdentity identity) {
        return identity.owner().storageOf(identity.name());
    }

    /** I115: the lowering-time storage of {@code name} declared by {@code scope}. */
    static CanonicalLexicalScope.Storage storageOf(CanonicalLexicalScope scope, String name) {
        return scope.storageOf(name);
    }

    /** I115: the ordered names of {@code scope}'s own cell bindings. */
    static List<String> cellNamesOf(CanonicalLexicalScope scope) {
        return scope.cellNamesInDeclarationOrder();
    }

    /** I115: the ordered outer cells a Closure of {@code scope} carries. */
    static List<CanonicalBindingIdentity> freeCellsOf(CanonicalLexicalScope scope) {
        return scope.freeCells();
    }

    /**
     * I068 Slice 5 source/parser rematerialization seam.
     *
     * <p>Canonical AST maps are identity-keyed, so a reparsed Closure cannot
     * reuse this analysis directly. Re-analyze the new Closure normally, then
     * restore only capture sites that this analysis had already proven
     * CapturedResolved. Sites are matched by immutable source coordinates,
     * access kind and binding name.
     *
     * <p>The reconstructed CapturedResolved identity carries only a synthetic
     * copy of the owner's declared-name layout and cell classification. No
     * old AST node, runtime Context, Truffle Frame or Bytecode object becomes
     * semantic Closure state.
     *
     * <p>I115: the storage classification is carried over unchanged. Every
     * reparsed Closure scope receives the free-cell list of the original
     * scope at the same source coordinates, in the same order, with owners
     * remapped to the reparsed scope or to the portable owner copy, so a
     * reparse never changes a captured-cell index or a binding's storage.
     */
    CanonicalBindingAnalysis rematerializeForReparsedClosure(
            CanonicalClosure reparsedDefinition) {
        Objects.requireNonNull(
                reparsedDefinition,
                "reparsedDefinition");

        CanonicalBindingAnalysis reparsed =
                CanonicalBindingAnalyzer.analyzeClosure(
                        reparsedDefinition);

        Map<CapturedSiteKey, CanonicalBindingResolution.CapturedResolved>
                oldCapturedLookups = new java.util.HashMap<>();
        for (Map.Entry<CanonicalLookup, CanonicalBindingResolution> entry
                : lookupResolutions.entrySet()) {
            if (entry.getValue()
                    instanceof CanonicalBindingResolution.CapturedResolved captured) {
                oldCapturedLookups.put(
                        CapturedSiteKey.lookup(entry.getKey()),
                        captured);
            }
        }

        Map<CapturedSiteKey, CanonicalBindingResolution.CapturedResolved>
                oldCapturedAssigns = new java.util.HashMap<>();
        for (Map.Entry<CanonicalAssign, CanonicalBindingResolution> entry
                : assignResolutions.entrySet()) {
            if (entry.getValue()
                    instanceof CanonicalBindingResolution.CapturedResolved captured) {
                oldCapturedAssigns.put(
                        CapturedSiteKey.assign(entry.getKey()),
                        captured);
            }
        }

        IdentityHashMap<CanonicalLookup, CanonicalBindingResolution>
                remappedLookups =
                        new IdentityHashMap<>(
                                reparsed.lookupResolutions);
        IdentityHashMap<CanonicalAssign, CanonicalBindingResolution>
                remappedAssigns =
                        new IdentityHashMap<>(
                                reparsed.assignResolutions);

        IdentityHashMap<CanonicalLexicalScope, CanonicalLexicalScope>
                portableOwnerLayouts =
                        new IdentityHashMap<>();
        IdentityHashMap<CanonicalLexicalScope, CanonicalLexicalScope>
                reparsedByOriginal = reparsedScopesByOriginal(reparsed);

        /*
         * I115-S1c-L: a restored CapturedResolved site keeps the access the
         * original whole-tree analysis decided for it, because only that
         * analysis saw every scope between the site and its owner.
         */
        Map<CapturedSiteKey, LexicalAccess> oldLookupAccesses = new java.util.HashMap<>();
        for (CanonicalLookup lookup : lookupResolutions.keySet()) {
            oldLookupAccesses.put(CapturedSiteKey.lookup(lookup), accessOf(lookup));
        }
        Map<CapturedSiteKey, LexicalAccess> oldAssignAccesses = new java.util.HashMap<>();
        for (CanonicalAssign assign : assignResolutions.keySet()) {
            oldAssignAccesses.put(CapturedSiteKey.assign(assign), accessOf(assign));
        }
        IdentityHashMap<CanonicalLookup, LexicalAccess> remappedLookupAccesses =
                new IdentityHashMap<>(reparsed.lookupAccesses);
        Map<CanonicalLookup, CanonicalLexicalScope> reparsedLookupSites = reparsed.lookupSiteScopes();
        Map<CanonicalAssign, CanonicalLexicalScope> reparsedAssignSites = reparsed.assignSiteScopes();
        IdentityHashMap<CanonicalAssign, LexicalAccess> remappedAssignAccesses =
                new IdentityHashMap<>(reparsed.assignAccesses);

        for (CanonicalLookup lookup
                : reparsed.lookupResolutions.keySet()) {
            CanonicalBindingResolution.CapturedResolved previous =
                    oldCapturedLookups.get(
                            CapturedSiteKey.lookup(lookup));
            if (previous != null
                    && !reparsedByOriginal.containsKey(previous.identity().owner())) {
                remappedLookups.put(
                        lookup,
                        portableCapturedResolution(
                                previous,
                                portableOwnerLayouts));
                remappedLookupAccesses.put(
                        lookup,
                        revalidatedCapturedAccess(
                                oldLookupAccesses.get(CapturedSiteKey.lookup(lookup)),
                                previous.identity().owner(),
                                reparsedLookupSites.get(lookup),
                                lookup.name()));
            }
        }

        for (CanonicalAssign assign
                : reparsed.assignResolutions.keySet()) {
            CanonicalBindingResolution.CapturedResolved previous =
                    oldCapturedAssigns.get(
                            CapturedSiteKey.assign(assign));
            if (previous != null
                    && !reparsedByOriginal.containsKey(previous.identity().owner())) {
                remappedAssigns.put(
                        assign,
                        portableCapturedResolution(
                                previous,
                                portableOwnerLayouts));
                remappedAssignAccesses.put(
                        assign,
                        revalidatedCapturedAccess(
                                oldAssignAccesses.get(CapturedSiteKey.assign(assign)),
                                previous.identity().owner(),
                                reparsedAssignSites.get(assign),
                                assign.name()));
            }
        }

        remapFreeCells(reparsedByOriginal, portableOwnerLayouts);

        return new CanonicalBindingAnalysis(
                reparsed.topScope,
                reparsed.closureScopes,
                reparsed.objectScopes,
                remappedLookups,
                remappedAssigns,
                reparsed.createIdentities,
                reparsed.multipleCreateIdentities,
                reparsed.parameterIdentities,
                remappedLookupAccesses,
                remappedAssignAccesses,
                reparsed.presenceCheckedCreates,
                reparsed.lookupSites,
                reparsed.assignSites);
    }

    /*
     * I115-S1c-L: a restored CapturedResolved site keeps the original
     * whole-tree access only while the facts behind it still hold. The
     * original owner's Context-observation fact is unchanged by a reparse;
     * the intervening scopes inside the reparsed Closure are re-checked
     * against their fresh analysis. A proof contradicted by either is an
     * internal invariant failure: the original lowering already relied on it
     * (for example by not binding outer frames to views), so silently
     * weakening or strengthening it here could not be made correct.
     */
    private static LexicalAccess revalidatedCapturedAccess(
            LexicalAccess original,
            CanonicalLexicalScope originalOwner,
            CanonicalLexicalScope reparsedSite,
            String name) {
        if (original == null || reparsedSite == null) {
            throw new IllegalStateException(
                    "I115-S1c-L: rematerialized captured site without its original access: " + name);
        }
        if (original == LexicalAccess.SELECTED) {
            return original;
        }
        boolean ownerMayRemove = originalOwner.kind() == CanonicalLexicalScope.Kind.CLOSURE
                && originalOwner.observesContext();
        if (ownerMayRemove && original == LexicalAccess.DIRECT) {
            throw new IllegalStateException(
                    "I115-S1c-L: direct access to a removable captured binding: " + name);
        }
        for (CanonicalLexicalScope scope = reparsedSite; scope != null; scope = scope.outwardScope()) {
            if (scope.kind() == CanonicalLexicalScope.Kind.CLOSURE && scope.observesContext()) {
                throw new IllegalStateException(
                        "I115-S1c-L: rematerialized scope observes its Context between a "
                                + "captured site and its owner: " + name);
            }
        }
        return original;
    }

    private IdentityHashMap<CanonicalLexicalScope, CanonicalLexicalScope>
            reparsedScopesByOriginal(CanonicalBindingAnalysis reparsed) {
        Map<ClosureSiteKey, CanonicalLexicalScope> originalScopes = new java.util.HashMap<>();
        for (Map.Entry<CanonicalClosure, CanonicalLexicalScope> entry : closureScopes.entrySet()) {
            originalScopes.put(ClosureSiteKey.of(entry.getKey()), entry.getValue());
        }
        IdentityHashMap<CanonicalLexicalScope, CanonicalLexicalScope> reparsedByOriginal =
                new IdentityHashMap<>();
        for (Map.Entry<CanonicalClosure, CanonicalLexicalScope> entry
                : reparsed.closureScopes.entrySet()) {
            CanonicalLexicalScope original = originalScopes.get(ClosureSiteKey.of(entry.getKey()));
            if (original != null) {
                reparsedByOriginal.put(original, entry.getValue());
            }
        }
        return reparsedByOriginal;
    }

    private static void remapFreeCells(
            IdentityHashMap<CanonicalLexicalScope, CanonicalLexicalScope> reparsedByOriginal,
            IdentityHashMap<CanonicalLexicalScope, CanonicalLexicalScope> portableOwnerLayouts) {
        for (Map.Entry<CanonicalLexicalScope, CanonicalLexicalScope> entry
                : reparsedByOriginal.entrySet()) {
            java.util.ArrayList<CanonicalBindingIdentity> remapped = new java.util.ArrayList<>();
            for (CanonicalBindingIdentity free : entry.getKey().freeCells()) {
                CanonicalLexicalScope reparsedOwner = reparsedByOriginal.get(free.owner());
                remapped.add(
                        reparsedOwner != null
                                ? reparsedOwner.identity(free.name())
                                : portableOwner(free.owner(), portableOwnerLayouts)
                                        .identity(free.name()));
            }
            entry.getValue().replaceFreeCells(remapped);
        }
    }

    private static CanonicalLexicalScope portableOwner(
            CanonicalLexicalScope owner,
            IdentityHashMap<CanonicalLexicalScope, CanonicalLexicalScope> portableOwnerLayouts) {
        return portableOwnerLayouts.computeIfAbsent(
                owner,
                original -> {
                    CanonicalLexicalScope copy =
                            new CanonicalLexicalScope(original.kind(), null);
                    for (String name : original.declaredNames()) {
                        copy.declare(name);
                        if (original.needsCell(name)) {
                            copy.markCell(name);
                        }
                    }
                    // I115-S1c-L: the owner's physical facts are part of its layout.
                    if (original.observesContext()) {
                        copy.markObservesContext();
                    }
                    if (original.requiresLexicalView()) {
                        copy.markRequiresLexicalView();
                    }
                    return copy;
                });
    }

    private record ClosureSiteKey(int startOffset, int length) {
        static ClosureSiteKey of(CanonicalClosure closure) {
            return new ClosureSiteKey(closure.span().startOffset(), closure.span().length());
        }
    }

    private static CanonicalBindingResolution.CapturedResolved
            portableCapturedResolution(
                    CanonicalBindingResolution.CapturedResolved previous,
                    IdentityHashMap<
                                    CanonicalLexicalScope,
                                    CanonicalLexicalScope>
                            portableOwnerLayouts) {
        CanonicalLexicalScope previousOwner =
                previous.identity().owner();

        CanonicalLexicalScope portableOwner =
                portableOwner(previousOwner, portableOwnerLayouts);

        return new CanonicalBindingResolution.CapturedResolved(
                portableOwner.identity(
                        previous.identity().name()),
                previous.lexicalDepth());
    }

    private record CapturedSiteKey(
            int startOffset,
            int length,
            String name) {
        private CapturedSiteKey {
            Objects.requireNonNull(name, "name");
        }

        static CapturedSiteKey lookup(
                CanonicalLookup lookup) {
            return new CapturedSiteKey(
                    lookup.span().startOffset(),
                    lookup.span().length(),
                    lookup.name());
        }

        static CapturedSiteKey assign(
                CanonicalAssign assign) {
            return new CapturedSiteKey(
                    assign.span().startOffset(),
                    assign.span().length(),
                    assign.name());
        }
    }
}
