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

import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosNumberLiteral;
import com.guillermomolina.protos.runtime.ProtosStringValue;
import com.guillermomolina.protos.semantic.ast.CanonicalAssign;
import com.guillermomolina.protos.semantic.ast.CanonicalCall;
import com.guillermomolina.protos.semantic.ast.CanonicalClosure;
import com.guillermomolina.protos.semantic.ast.CanonicalCompose;
import com.guillermomolina.protos.semantic.ast.CanonicalCreate;
import com.guillermomolina.protos.semantic.ast.CanonicalMultipleCreate;
import com.guillermomolina.protos.semantic.ast.CanonicalDerivedInequality;
import com.guillermomolina.protos.semantic.ast.CanonicalExpression;
import com.guillermomolina.protos.semantic.ast.CanonicalIdentity;
import com.guillermomolina.protos.semantic.ast.CanonicalIndexedAssign;
import com.guillermomolina.protos.semantic.ast.CanonicalIntrinsic;
import com.guillermomolina.protos.semantic.ast.CanonicalLiteral;
import com.guillermomolina.protos.semantic.ast.CanonicalLookup;
import com.guillermomolina.protos.semantic.ast.CanonicalMapConstruction;
import com.guillermomolina.protos.semantic.ast.CanonicalMember;
import com.guillermomolina.protos.semantic.ast.CanonicalNotIdentity;
import com.guillermomolina.protos.semantic.ast.CanonicalObject;
import com.guillermomolina.protos.semantic.ast.CanonicalParameter;
import com.guillermomolina.protos.semantic.ast.CanonicalReturn;
import com.guillermomolina.protos.semantic.ast.CanonicalSequence;
import com.guillermomolina.protos.semantic.ast.CanonicalSend;
import com.guillermomolina.protos.semantic.ast.CanonicalSpread;
import com.guillermomolina.protos.semantic.ast.CanonicalSuperSend;
import com.guillermomolina.protos.source.SourceSpan;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeLocal;
import com.oracle.truffle.api.bytecode.BytecodeRootNodes;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.source.Source;
import java.util.Objects;

/**
 * Parallel canonical-to-Bytecode lowering seam for the PERF006-B migration.
 *
 * <p>PERF006-B2D5C completes B2 invocation composition by supporting
 * caller-supplied spread in call/send default expressions as well as Closure
 * bodies. Body and default paths share the same backend-private supplied-vector
 * representation and the same immediate shallow Array snapshot rule at each
 * spread item's exact left-to-right position. Non-spread body/default fast paths
 * remain unchanged.
 * PERF006-B6A1 extends the same C-prime lowering with read-only member,
 * identity and execution-intrinsic expressions. PERF006-B6A2 adds canonical
 * slot creation/assignment and indexed assignment while preserving the AST
 * validation/evaluation order and composing ordinary atPut dispatch through
 * the same continuation backend. PERF006-B6A3A adds canonical super sends,
 * preserving physical method-home lookup, dynamic receiver identity, spread
 * argument semantics and C-prime suspension composition. PERF006-B6A3B adds
 * Closure literal materialization with exact lexical/receiver/method-home/
 * return-home/prelude capture and a pre-lowered Bytecode execution-plan
 * template per canonical Closure position. PERF006-B6A3C adds Object
 * literals and contextual composition using a pre-lowered child Bytecode
 * root per Object position plus a non-Closure construction carrier, so
 * construction activation/lexical-capture semantics and suspension are
 * preserved without manufacturing ReturnHome ownership.</p>
 *
 * <p>PLAT042 Candidate B′: every top-level, module and Closure activation root
 * produced here is a real tagged {@link ProtosSemanticBytecodeRootNode} (its
 * automatic RootTag is the guest semantic root identity) and lexically nested
 * Closure roots share that interpreter's {@code BytecodeRootNodes} group.
 * Structured prepared invocations are not lowered here; they enter the untagged
 * {@link ProtosStructuredDispatchLowerer} root once per invocation.</p>
 */
final class CanonicalToBytecodeLowerer {
    private final ProtosLanguage language;
    private final Source source;

    /**
     * I068 Slice 5 backend-private whole-tree lexical analysis supplied by an
     * enclosing lowering unit. When present, nested Closure activation roots
     * reuse this analysis so captured binding owners/depths are not lost by
     * re-analyzing the Closure in isolation.
     */
    private final CanonicalBindingAnalysis inheritedBindingAnalysis;

    private final java.util.IdentityHashMap<CanonicalClosure, ProtosClosureExecutionPlanCell>
            bytecodeClosurePlans = new java.util.IdentityHashMap<>();

    /**
     * PERF013 Slice A: Closure roots nested (via {@link #lowerNestedClosureRoot})
     * in the group currently being built by the top-level {@link #lowerRoot}
     * call in progress. Their real {@link ProtosClosureExecutionPlan} cannot be
     * constructed until that group's {@code create()} call returns ({@link
     * com.oracle.truffle.api.nodes.RootNode#getCallTarget()} refuses while
     * parsing is still in progress), so construction is deferred and this list
     * is drained/frozen right after {@code create()} returns. Save/restored
     * around each {@link #lowerRoot} call so a reentrant {@link #lowerRoot}
     * never sees or pollutes this group's pending list.
     */
    private java.util.List<PendingGroupClosure> pendingGroupClosures;

    private record PendingGroupClosure(
            CanonicalClosure definition,
            CanonicalBindingAnalysis bindingAnalysis,
            ProtosSemanticBytecodeRootNode root,
            ProtosClosureExecutionPlanCell cell) {}

    private final java.util.IdentityHashMap<CanonicalCompose, java.util.List<String>>
            bytecodeComposeReservedNames = new java.util.IdentityHashMap<>();
    private final java.util.IdentityHashMap<CanonicalClosure, CanonicalBindingAnalysis>
            bindingAnalysisByClosure = new java.util.IdentityHashMap<>();
    private CanonicalBindingAnalysis moduleBindingAnalysis;

    /**
     * PLAT036 Candidate D, Slice 3 fast-path context for whichever genuine
     * {@code ROOT}/{@code CLOSURE} Bytecode root is currently being lowered
     * (null/empty while lowering an inline object-construction body, which is
     * never eligible). Saved and restored around each {@link #lowerRootInto}
     * call and each inline object body ({@link
     * #emitInlineObjectConstruction}) so lowering either inside an enclosing
     * root's own body never corrupts the enclosing root's context.
     */
    private CanonicalBindingAnalysis currentRootAnalysis;
    private CanonicalLexicalScope currentRootTopScope;
    private java.util.Map<String, BytecodeLocal> currentRootFrameLocals =
            java.util.Map.of();

    /**
     * PLAT041 C′ lowering-time source of the current {@link
     * com.guillermomolina.protos.runtime.ProtosActivation} consumed by {@link
     * #emitCurrentActivation}: {@code null} selects the root's frame argument
     * 0; inside an inline object-construction body it is that body's own
     * construction-activation local. Reset to {@code null} for every new root
     * ({@link #lowerRootInto}), so an enclosing object body's selection never
     * leaks into a Closure root nested inside it, and saved/restored around
     * each inline object body, so nested object bodies select their own.
     */
    private BytecodeLocal currentActivationLocal;

    /**
     * PERF013 Slice B1 backend-private registry of the frame-backed {@link
     * BytecodeLocal}s created for every genuine lexical scope this lowerer has
     * lowered so far, keyed by the exact {@link CanonicalLexicalScope} object
     * the whole-tree binding analysis assigned it. A captured read or write
     * (Slice B2) whose owner scope is proven ({@link
     * #capturedOwnerMatchesCurrentRoot}) to be part of the current shared
     * {@code BytecodeRootNodes} group looks its owner's stable {@link
     * BytecodeLocal} up here (see {@link #capturedOwnerBytecodeLocal}) instead
     * of resolving it dynamically from the runtime frame-lexical-binding
     * authority. Populated by {@link
     * #emitRootBody} for every genuine execution-context root, before that
     * root's own body (and therefore any nested Closure root that might
     * capture one of its bindings) is lowered, so the owner's current
     * entry is always fresh by the time a nested captured read needs it —
     * including on a {@code BytecodeRootNodes} reparse replay, since the owner
     * root in the group is always replayed before its nested children and
     * simply overwrites its own entry with the current parse's {@link
     * BytecodeLocal} objects.
     */
    private final java.util.IdentityHashMap<CanonicalLexicalScope, java.util.Map<String, BytecodeLocal>>
            frameLocalsByScope = new java.util.IdentityHashMap<>();

    CanonicalToBytecodeLowerer(ProtosLanguage language, Source source) {
        this(language, source, null);
    }

    CanonicalToBytecodeLowerer(
            ProtosLanguage language,
            Source source,
            CanonicalBindingAnalysis inheritedBindingAnalysis) {
        this.language = Objects.requireNonNull(language, "language");
        this.source = Objects.requireNonNull(source, "source");
        this.inheritedBindingAnalysis = inheritedBindingAnalysis;
    }

    /**
     * PLAT036 Candidate D, Slice 1 preparatory metadata: computes and caches
     * statically proven lexical binding identity/presence-candidate metadata
     * for the unit currently being lowered, so it is available at lowering
     * time. Not yet consumed by codegen ({@code RUNTIME_AUTHORITY_CUTOVER=NO});
     * {@link #emitLookup}, {@link #emitBodyAssign} and {@link #emitBodyCreate}
     * continue to emit the exact existing String-keyed runtime operations.
     */
    private CanonicalBindingAnalysis bindingAnalysisFor(
            CanonicalSequence sequence, CanonicalClosure activationDefinition) {
        if (activationDefinition != null) {
            if (inheritedBindingAnalysis != null
                    && inheritedBindingAnalysis.scopeOf(activationDefinition).isPresent()) {
                return inheritedBindingAnalysis;
            }
            return bindingAnalysisByClosure.computeIfAbsent(
                    activationDefinition, CanonicalBindingAnalyzer::analyzeClosure);
        }

        /*
         * Inline object bodies reached while lowering a Closure with
         * inherited whole-tree analysis keep that same analysis available for
         * Closure literals nested inside the object. The object body itself
         * remains non-authoritative and never takes the direct-local path.
         */
        if (inheritedBindingAnalysis != null) {
            return inheritedBindingAnalysis;
        }

        if (moduleBindingAnalysis == null) {
            moduleBindingAnalysis = CanonicalBindingAnalyzer.analyzeModule(sequence);
        }
        return moduleBindingAnalysis;
    }

    private CanonicalBindingAnalysis bindingAnalysisForNestedClosure(
            CanonicalClosure definition) {
        if (currentRootAnalysis != null
                && currentRootAnalysis.scopeOf(definition).isPresent()) {
            return currentRootAnalysis;
        }
        if (inheritedBindingAnalysis != null
                && inheritedBindingAnalysis.scopeOf(definition).isPresent()) {
            return inheritedBindingAnalysis;
        }
        if (moduleBindingAnalysis != null
                && moduleBindingAnalysis.scopeOf(definition).isPresent()) {
            return moduleBindingAnalysis;
        }
        return CanonicalBindingAnalyzer.analyzeClosure(definition);
    }

    private CanonicalLexicalScope rootScopeFor(
            CanonicalBindingAnalysis analysis,
            CanonicalClosure activationDefinition) {
        if (activationDefinition == null) {
            return analysis.topScope();
        }
        return analysis.scopeOf(activationDefinition)
                .orElseGet(analysis::topScope);
    }

    /**
     * PERF013 Slice A/A2: construction path for a Closure reached while
     * emitting an already-open enclosing root's own body — including a
     * parameter default-value Closure, which (since Slice A2) is reached the
     * same way: real emission (via {@code emitClosureParameterBindings})
     * always happens while the owner root's own builder is already open.
     * Nests the Closure's root in the same {@code create()} invocation as
     * that enclosing root (see {@link #lowerNestedClosureRoot}) instead of
     * opening an independent lowerer/{@code create()} call, so owner and
     * child end up in one shared {@code BytecodeRootNodes} group.
     *
     * <p>A cache hit in {@code bytecodeClosurePlans} means this exact group's
     * lambda is being replayed (a {@code BytecodeRootNodes} reparse, e.g. to
     * materialize source/tag information): {@code beginRoot()}/{@code
     * endRoot()} must still be replayed for this Closure so every root in the
     * group keeps the same index it had during the original parse, but the
     * plan already frozen then remains valid (the Bytecode DSL patches the
     * same root identity in place on reparse) and must not be rebuilt.
     */
    private ProtosClosureExecutionPlanCell bytecodeClosurePlan(
            ProtosSemanticBytecodeRootNodeGen.Builder builder,
            CanonicalClosure definition) {
        ProtosClosureExecutionPlanCell existing = bytecodeClosurePlans.get(definition);

        ProtosSemanticBytecodeRootNode nestedRoot =
                lowerNestedClosureRoot(builder, definition);

        if (existing != null) {
            return existing;
        }

        ProtosClosureExecutionPlanCell cell = ProtosClosureExecutionPlanCell.pendingGroup();
        bytecodeClosurePlans.put(definition, cell);
        /*
         * lowerNestedClosureRoot's own currentRootAnalysis save/restore has
         * already unwound by this point, so this recomputes the exact same
         * whole-tree analysis it resolved and used while lowering nestedRoot
         * (bindingAnalysisForNestedClosure is a cheap, idempotent lookup, not
         * a re-analysis) rather than re-deriving one in isolation.
         */
        pendingGroupClosures.add(
                new PendingGroupClosure(
                        definition,
                        bindingAnalysisForNestedClosure(definition),
                        nestedRoot,
                        cell));
        return cell;
    }


    /**
     * Preparation-time metadata registration only. Every {@link
     * CanonicalCompose} directly inside {@code object}'s body must know its
     * enclosing object's reserved local-slot names before {@code
     * composeReservedNames} is consulted during structural validation of the
     * compose itself. This registration is idempotent.
     */
    private void registerObjectBodyReservedNames(CanonicalObject object) {
        java.util.List<String> reservedNames =
                java.util.List.copyOf(object.reservedLocalSlotNames());
        for (CanonicalExpression expression : object.body().expressions()) {
            if (expression instanceof CanonicalCompose compose) {
                java.util.List<String> previous =
                        bytecodeComposeReservedNames.put(compose, reservedNames);
                if (previous != null && !previous.equals(reservedNames)) {
                    throw new IllegalStateException(
                            "canonical composition item belongs to multiple object bodies");
                }
            }
        }
    }

    private java.util.List<String> composeReservedNames(
            CanonicalCompose compose) {
        java.util.List<String> reservedNames = bytecodeComposeReservedNames.get(compose);
        if (reservedNames == null) {
            throw new AssertionError(
                    "contextual composition item was not registered by its object body");
        }
        return reservedNames;
    }

    CallTarget lower(CanonicalSequence sequence) {
        return lowerRoot(sequence).getCallTarget();
    }

    ProtosSemanticBytecodeRootNode lowerRoot(CanonicalSequence sequence) {
        return lowerRoot(sequence, null);
    }

    ProtosSemanticBytecodeRootNode lowerClosureActivationRoot(
            CanonicalClosure definition) {
        Objects.requireNonNull(definition, "definition");
        validateSupportedDefaults(definition);
        return lowerRoot(definition.body(), definition);
    }

    private ProtosSemanticBytecodeRootNode lowerRoot(
            CanonicalSequence sequence,
            CanonicalClosure activationDefinition) {
        Objects.requireNonNull(sequence, "sequence");
        /*
         * PERF013 Slice A: this top-level entry owns the one Source wrapper for
         * the whole lexical root group. Every Closure lexically reached while
         * lowering this root's own body nests its own beginRoot()/endRoot()
         * pair inside this same open builder (see emitExpression's
         * CanonicalClosure case and lowerNestedClosureRoot below) instead of
         * opening an independent create() call, so owner and child end up in
         * one shared BytecodeRootNodes<ProtosSemanticBytecodeRootNode> group.
         *
         * A nested Closure's real ProtosClosureExecutionPlan cannot be built
         * while this create() call is still in progress (getCallTarget()
         * refuses until parsing completes), so construction is deferred via
         * pendingGroupClosures and drained right after create() returns, once
         * every root in the group is real and getCallTarget()-safe. Saved and
         * restored here so a reentrant lowerRoot/create() call never sees or
         * pollutes this group's pending list.
         *
         * PLAT041 C′: an object-construction body is lowered inline in the
         * enclosing root (emitInlineObjectConstruction) and contributes no
         * root to the group.
         */
        java.util.List<PendingGroupClosure> savedPendingGroupClosures =
                pendingGroupClosures;
        pendingGroupClosures = new java.util.ArrayList<>();
        try {
            BytecodeRootNodes<ProtosSemanticBytecodeRootNode> roots =
                    ProtosSemanticBytecodeRootNodeGen.create(
                            language,
                            BytecodeConfig.DEFAULT,
                            builder -> {
                                /*
                                 * Root-inside-Source is the Bytecode DSL shape
                                 * that gives every root in the group a
                                 * reliable exact source section once lazy
                                 * source information is materialized.
                                 */
                                builder.beginSource(source);
                                lowerRootInto(
                                        builder,
                                        sequence,
                                        activationDefinition);
                                builder.endSource();
                            });

            for (PendingGroupClosure pending : pendingGroupClosures) {
                pending.cell()
                        .freeze(
                                ProtosClosureExecutionPlan.bytecode(
                                        pending.definition(),
                                        language,
                                        source,
                                        pending.bindingAnalysis(),
                                        pending.root()));
            }

            return roots.getNode(0);
        } finally {
            pendingGroupClosures = savedPendingGroupClosures;
        }
    }

    /**
     * PERF013 Slice A: lowers one root — top-level, or a Closure lexically
     * nested inside an already-open enclosing root's own {@code beginRoot()}/
     * {@code endRoot()} pair — into {@code builder}. The caller owns the
     * enclosing {@code beginSource}/{@code endSource} pair.
     *
     * <p>I068 Slice 5: establishes whole-tree binding metadata before
     * recursive validation constructs nested Closure/Object plans. Otherwise
     * those plans would conservatively re-analyze nested Closures in
     * isolation and lose their captured owner/depth metadata.
     */
    private ProtosSemanticBytecodeRootNode lowerRootInto(
            ProtosSemanticBytecodeRootNodeGen.Builder builder,
            CanonicalSequence sequence,
            CanonicalClosure activationDefinition) {
        CanonicalBindingAnalysis analysisForThisRoot =
                bindingAnalysisFor(sequence, activationDefinition);
        CanonicalLexicalScope scopeForThisRoot =
                rootScopeFor(analysisForThisRoot, activationDefinition);
        return lowerRootInto(
                builder,
                sequence,
                activationDefinition,
                analysisForThisRoot,
                scopeForThisRoot);
    }

    private ProtosSemanticBytecodeRootNode lowerRootInto(
            ProtosSemanticBytecodeRootNodeGen.Builder builder,
            CanonicalSequence sequence,
            CanonicalClosure activationDefinition,
            CanonicalBindingAnalysis analysisForThisRoot,
            CanonicalLexicalScope scopeForThisRoot) {
        validateSupported(sequence);
        validateSpan(sequence.span());

        /*
         * PLAT036 Slice 3: save/restore around this (possibly reentrant, e.g.
         * a nested Closure lowered while lowering its enclosing root's own
         * body) lowering pass's fast-path context. This both protects
         * same-invocation reentrancy and re-establishes state correctly on
         * every future BytecodeRootNodes reparse invocation of the retained
         * parser, since the whole nest replays together.
         *
         * PLAT041 C′: every root starts again from its own frame argument 0
         * as the current activation, even when nested inside an inline object
         * body of its enclosing root.
         */
        CanonicalBindingAnalysis savedAnalysis = currentRootAnalysis;
        CanonicalLexicalScope savedTopScope = currentRootTopScope;
        java.util.Map<String, BytecodeLocal> savedFrameLocals =
                currentRootFrameLocals;
        BytecodeLocal savedActivationLocal = currentActivationLocal;
        try {
            currentRootAnalysis = analysisForThisRoot;
            currentRootTopScope = scopeForThisRoot;
            currentRootFrameLocals = java.util.Map.of();
            currentActivationLocal = null;
            return emitRootBody(
                    builder,
                    sequence,
                    activationDefinition,
                    scopeForThisRoot);
        } finally {
            currentRootAnalysis = savedAnalysis;
            currentRootTopScope = savedTopScope;
            currentRootFrameLocals = savedFrameLocals;
            currentActivationLocal = savedActivationLocal;
        }
    }

    /**
     * PERF013 Slice A/A2: lowers a Closure lexically reached while an
     * enclosing root's own {@code beginRoot()}/{@code endRoot()} pair is
     * still open, nesting this Closure's root in the exact same {@code
     * create()} invocation (and therefore the same {@code BytecodeRootNodes}
     * group) as that enclosing root instead of opening an independent
     * lowerer/{@code create()} call. Since Slice A2 this also covers a
     * parameter default-value Closure: real emission
     * ({@code emitClosureParameterBindings} -> {@code emitExpression} ->
     * {@link #bytecodeClosurePlan}) always reaches this method while the
     * owner root's own builder is already open.
     *
     * <p>Resolves binding analysis via {@link #bindingAnalysisForNestedClosure}:
     * the enclosing root's own whole-tree analysis takes priority so captured
     * owner/depth metadata is not lost by re-analyzing this Closure in
     * isolation.
     */
    private ProtosSemanticBytecodeRootNode lowerNestedClosureRoot(
            ProtosSemanticBytecodeRootNodeGen.Builder builder,
            CanonicalClosure definition) {
        validateSupportedDefaults(definition);
        CanonicalBindingAnalysis analysisForThisRoot =
                bindingAnalysisForNestedClosure(definition);
        CanonicalLexicalScope scopeForThisRoot =
                rootScopeFor(analysisForThisRoot, definition);
        return lowerRootInto(
                builder,
                definition.body(),
                definition,
                analysisForThisRoot,
                scopeForThisRoot);
    }

    private ProtosSemanticBytecodeRootNode emitRootBody(
            ProtosSemanticBytecodeRootNodeGen.Builder builder,
            CanonicalSequence sequence,
            CanonicalClosure activationDefinition,
            CanonicalLexicalScope scopeForThisRoot) {
        SourceSpan rootSpan = sequence.span();

        builder.beginSourceSection(
                rootSpan.startOffset(),
                rootSpan.length());
        builder.beginRoot();

        /*
         * PLAT042 B′: this root is entered directly (no wrapper), so it
         * materializes a compact source-call activation into frame argument 0
         * before anything reads the current activation.
         */
        builder.emitPublishFrameActivation();

        /*
         * PLAT036 Candidate D, I068 Slice 4: every statically
         * declared binding owned by this genuine execution-context
         * root, including Closure parameters, receives one stable
         * BytecodeLocal. Allocating that physical local does not
         * establish semantic presence: it stays cleared until the
         * existing createLocalSlot binding point writes through the
         * frame-backed authority.
         */
        java.util.Map<String, BytecodeLocal> frameLocals =
                new java.util.LinkedHashMap<>();
        for (String name : scopeForThisRoot.declaredNames()) {
            BytecodeLocal local = builder.createLocal(name, null);
            frameLocals.put(name, local);
        }
        currentRootFrameLocals = java.util.Map.copyOf(frameLocals);
        /*
         * PERF013 Slice B1: register this root's own frame locals under its
         * own scope identity before its body (and therefore any nested
         * Closure root that might capture one of these bindings) is lowered,
         * so a captured read reached while lowering this root's own body
         * already sees a fresh entry.
         */
        frameLocalsByScope.put(scopeForThisRoot, currentRootFrameLocals);
        if (!frameLocals.isEmpty()) {
            BytecodeLocal[] frameLocalRange =
                    frameLocals.values().toArray(BytecodeLocal[]::new);
            String[] frameLocalNames =
                    frameLocals.keySet().toArray(String[]::new);
            ProtosFrameLexicalLayout frameLocalLayout =
                    ProtosFrameLexicalLayout.of(frameLocalNames);
            builder.beginInstallFrameLexicalAuthority(
                    frameLocalRange,
                    frameLocalLayout);
            emitCurrentActivation(builder);
            builder.endInstallFrameLexicalAuthority();
        }

        BytecodeLocal defaultValue = null;
        BytecodeLocal defaultPreparedCall = null;
        BytecodeLocal defaultChildResult = null;
        BytecodeLocal defaultResumeValue = null;
        if (activationDefinition != null
                && hasComposedDefault(activationDefinition)) {
            defaultValue = builder.createLocal("defaultValue", null);
            defaultPreparedCall = builder.createLocal("defaultPreparedClosureCall", null);
            defaultChildResult = builder.createLocal("defaultChildResult", null);
            defaultResumeValue = builder.createLocal("defaultResumeValue", null);
        }

        if (activationDefinition != null) {
            emitClosureParameterBindings(
                    builder,
                    activationDefinition,
                    defaultValue,
                    defaultPreparedCall,
                    defaultChildResult,
                    defaultResumeValue);
        }

        if (sequence.expressions().isEmpty()) {
            builder.beginReturn();
            builder.emitLoadConstant(ProtosNullValue.INSTANCE);
            builder.endReturn();
        } else {
            BytecodeLocal result =
                    builder.createLocal("sequenceResult", null);
            emitStatementsToLocal(builder, sequence, result);

            builder.beginReturn();
            builder.emitLoadLocal(result);
            builder.endReturn();
        }

        ProtosSemanticBytecodeRootNode result = builder.endRoot();
        builder.endSourceSection();
        return result;
    }

    /**
     * Emits each expression of {@code sequence} as one source statement
     * (StatementTag + ExpressionTag over its exact span), storing each value
     * into {@code result}. Shared by root bodies and inline object-construction
     * bodies so both keep identical statement/expression tag membership.
     */
    private void emitStatementsToLocal(
            ProtosSemanticBytecodeRootNodeGen.Builder builder,
            CanonicalSequence sequence,
            BytecodeLocal result) {
        boolean hasComposedInvocation =
                sequence.expressions().stream()
                        .anyMatch(
                                CanonicalToBytecodeLowerer::requiresComposedInvocation);
        BytecodeLocal preparedCall =
                hasComposedInvocation
                        ? builder.createLocal(
                                "preparedClosureCall",
                                null)
                        : null;
        BytecodeLocal childResult =
                hasComposedInvocation
                        ? builder.createLocal(
                                "childResult",
                                null)
                        : null;
        BytecodeLocal resumeValue =
                hasComposedInvocation
                        ? builder.createLocal(
                                "resumeValue",
                                null)
                        : null;

        for (CanonicalExpression expression :
                sequence.expressions()) {
            SourceSpan span = expression.span();

            builder.beginSourceSection(
                    span.startOffset(),
                    span.length());
            builder.beginTag(
                    StandardTags.StatementTag.class,
                    StandardTags.ExpressionTag.class);
            builder.beginBlock();

            if (requiresComposedInvocation(expression)) {
                emitBodyExpressionToLocal(
                        builder,
                        expression,
                        result,
                        preparedCall,
                        childResult,
                        resumeValue);
            } else {
                builder.beginStoreLocal(result);
                emitExpression(builder, expression);
                builder.endStoreLocal();
            }

            builder.endBlock();
            builder.endTag(
                    StandardTags.StatementTag.class,
                    StandardTags.ExpressionTag.class);
            builder.endSourceSection();
        }
    }

    private void validateSupportedDefaults(
            CanonicalClosure definition) {
        for (CanonicalParameter parameter : definition.parameters()) {
            if (parameter.defaultValue().isEmpty()) {
                continue;
            }
            validateSupportedDefaultExpression(parameter.defaultValue().orElseThrow());
        }
    }

    private void validateSupportedDefaultExpression(
            CanonicalExpression expression) {
        validateSpan(expression.span());
        if (expression instanceof CanonicalLiteral
                || expression instanceof CanonicalLookup
                || expression instanceof CanonicalIntrinsic) {
            return;
        }
        if (expression instanceof CanonicalClosure closure) {
            /*
             * PERF013 Slice A2: structural-support validation only, mirroring
             * validateSupportedExpression's CanonicalClosure case. This must
             * NOT construct the Closure's execution plan/root here: real
             * emission (emitClosureParameterBindings -> emitExpression) always
             * happens later, while the owner root's own builder is already
             * open, so the default-value Closure's root is nested in that
             * same shared BytecodeRootNodes group (bytecodeClosurePlan /
             * lowerNestedClosureRoot) instead of opening an independent
             * create() call.
             */
            validateSupportedDefaults(closure);
            validateSpan(closure.body().span());
            validateSupported(closure.body());
            return;
        }
        if (expression instanceof CanonicalObject object) {
            /*
             * Structural-support validation only: the body is emitted inline
             * later (emitInlineObjectConstruction), while the owner root's
             * own builder is open.
             */
            object.parent().ifPresent(this::validateSupportedDefaultExpression);
            registerObjectBodyReservedNames(object);
            validateSupported(object.body());
            return;
        }
        if (expression instanceof CanonicalMapConstruction map) {
            validateSupportedDefaultExpression(map.factory());
            for (CanonicalMapConstruction.Entry entry : map.entries()) {
                validateSupportedDefaultExpression(entry.key());
                validateSupportedDefaultExpression(entry.value());
            }
            return;
        }
        if (expression instanceof CanonicalMember member) {
            validateSupportedDefaultExpression(member.receiver());
            return;
        }
        if (expression instanceof CanonicalIdentity identity) {
            validateSupportedDefaultExpression(identity.left());
            validateSupportedDefaultExpression(identity.right());
            return;
        }
        if (expression instanceof CanonicalNotIdentity identity) {
            validateSupportedDefaultExpression(identity.left());
            validateSupportedDefaultExpression(identity.right());
            return;
        }
        if (expression instanceof CanonicalDerivedInequality inequality) {
            validateSupportedDefaultExpression(inequality.left());
            validateSupportedDefaultExpression(inequality.right());
            return;
        }
        if (expression instanceof CanonicalCreate create) {
            create.target().ifPresent(this::validateSupportedDefaultExpression);
            validateSupportedDefaultExpression(create.value());
            return;
        }
        if (expression instanceof CanonicalMultipleCreate create) {
            validateSupportedDefaultExpression(create.value());
            return;
        }
        if (expression instanceof CanonicalAssign assign) {
            assign.target().ifPresent(this::validateSupportedDefaultExpression);
            validateSupportedDefaultExpression(assign.value());
            return;
        }
        if (expression instanceof CanonicalIndexedAssign indexedAssign) {
            validateSupportedDefaultExpression(indexedAssign.receiver());
            validateSupportedDefaultExpression(indexedAssign.index());
            validateSupportedDefaultExpression(indexedAssign.value());
            return;
        }
        if (expression instanceof CanonicalCall call) {
            validateSupportedDefaultExpression(call.receiver());
            for (CanonicalExpression argument : call.arguments()) {
                if (argument instanceof CanonicalSpread spread) {
                    validateSupportedDefaultExpression(
                            spread.expression());
                } else {
                    validateSupportedDefaultExpression(argument);
                }
            }
            return;
        }
        if (expression instanceof CanonicalSend send) {
            validateSupportedDefaultExpression(send.receiver());
            for (CanonicalExpression argument : send.arguments()) {
                if (argument instanceof CanonicalSpread spread) {
                    validateSupportedDefaultExpression(
                            spread.expression());
                } else {
                    validateSupportedDefaultExpression(argument);
                }
            }
            return;
        }
        if (expression instanceof CanonicalSuperSend send) {
            for (CanonicalExpression argument : send.arguments()) {
                if (argument instanceof CanonicalSpread spread) {
                    validateSupportedDefaultExpression(spread.expression());
                } else {
                    validateSupportedDefaultExpression(argument);
                }
            }
            return;
        }
        if (expression instanceof CanonicalReturn returnExpression) {
            validateSupportedDefaultExpression(returnExpression.value());
            return;
        }
        throw new UnsupportedOperationException(
                "PERF006-B2C3B3 default expression is not migrated: "
                        + expression.getClass().getSimpleName());
    }

    private static boolean hasComposedDefault(CanonicalClosure definition) {
        return definition.parameters().stream()
                .flatMap(parameter -> parameter.defaultValue().stream())
                .anyMatch(CanonicalToBytecodeLowerer::requiresComposedInvocation);
    }

    /**
     * Sole lowering authority for loading the currently executing
     * {@link com.guillermomolina.protos.runtime.ProtosActivation}. Every
     * Protos Bytecode root receives that activation as frame argument 0;
     * inside an inline object-construction body (PLAT041 C′) the current
     * activation is instead that body's construction activation, held in
     * {@link #currentActivationLocal}. Operations that need "the current
     * activation" must obtain it through this method rather than emitting the
     * argument load directly. This is a compile-time choice only.
     */
    private void emitCurrentActivation(
            ProtosSemanticBytecodeRootNodeGen.Builder builder) {
        if (currentActivationLocal == null) {
            builder.emitLoadArgument(0);
        } else {
            builder.emitLoadLocal(currentActivationLocal);
        }
    }

    private void emitClosureParameterBindings(
            ProtosSemanticBytecodeRootNodeGen.Builder builder,
            CanonicalClosure definition,
            BytecodeLocal defaultValue,
            BytecodeLocal defaultPreparedCall,
            BytecodeLocal defaultChildResult,
            BytecodeLocal defaultResumeValue) {
        int positionalIndex = 0;
        boolean hasRest = false;

        for (CanonicalParameter parameter : definition.parameters()) {
            if (parameter.rest()) {
                builder.beginBindClosureRest();
                emitCurrentActivation(builder);
                builder.emitLoadConstant(parameter.name());
                builder.emitLoadConstant(positionalIndex);
                builder.endBindClosureRest();
                hasRest = true;
                continue;
            }

            if (parameter.defaultValue().isPresent()) {
                CanonicalExpression defaultExpression =
                        parameter.defaultValue().orElseThrow();

                builder.beginIfThenElse();

                builder.beginHasClosureArgument();
                emitCurrentActivation(builder);
                builder.emitLoadConstant(positionalIndex);
                builder.endHasClosureArgument();

                builder.beginBlock();
                emitBindSuppliedClosureParameter(
                        builder,
                        parameter,
                        positionalIndex);
                builder.endBlock();

                builder.beginBlock();
                if (defaultExpression instanceof CanonicalCall defaultCall) {
                    emitComposedDefaultCall(
                            builder,
                            defaultCall,
                            defaultValue,
                            defaultPreparedCall,
                            defaultChildResult,
                            defaultResumeValue);
                    emitBindDefaultLocal(builder, parameter, defaultValue);
                } else if (defaultExpression instanceof CanonicalSend defaultSend) {
                    emitComposedDefaultSend(
                            builder,
                            defaultSend,
                            defaultValue,
                            defaultPreparedCall,
                            defaultChildResult,
                            defaultResumeValue);
                    emitBindDefaultLocal(builder, parameter, defaultValue);
                } else if (defaultExpression instanceof CanonicalReturn
                        || requiresComposedInvocation(defaultExpression)) {
                    emitDefaultExpressionToLocal(
                            builder,
                            defaultExpression,
                            defaultValue,
                            defaultPreparedCall,
                            defaultChildResult,
                            defaultResumeValue);
                    emitBindDefaultLocal(builder, parameter, defaultValue);
                } else {
                    builder.beginBindClosureParameter();
                    emitCurrentActivation(builder);
                    builder.emitLoadConstant(parameter.name());
                    builder.beginSourceSection(
                            defaultExpression.span().startOffset(),
                            defaultExpression.span().length());
                    emitExpression(builder, defaultExpression);
                    builder.endSourceSection();
                    builder.endBindClosureParameter();
                }
                builder.endBlock();

                builder.endIfThenElse();
            } else {
                emitBindSuppliedClosureParameter(
                        builder,
                        parameter,
                        positionalIndex);
            }

            positionalIndex++;
        }

        if (!hasRest) {
            builder.beginCheckClosureArgumentUpperBound();
            emitCurrentActivation(builder);
            builder.emitLoadConstant(positionalIndex);
            builder.endCheckClosureArgumentUpperBound();
        }
    }

    private void emitBindDefaultLocal(
            ProtosSemanticBytecodeRootNodeGen.Builder builder,
            CanonicalParameter parameter,
            BytecodeLocal defaultValue) {
        if (defaultValue == null) {
            throw new AssertionError("composed default value local was not allocated");
        }
        builder.beginBindClosureParameter();
        emitCurrentActivation(builder);
        builder.emitLoadConstant(parameter.name());
        builder.emitLoadLocal(defaultValue);
        builder.endBindClosureParameter();
    }

    private static boolean hasComposedArgument(
            java.util.List<CanonicalExpression> arguments) {
        return arguments.stream()
                .map(CanonicalToBytecodeLowerer::suppliedArgumentExpression)
                .anyMatch(CanonicalToBytecodeLowerer::requiresComposedInvocation);
    }

    private static boolean requiresComposedInvocation(
            CanonicalExpression expression) {
        if (expression instanceof CanonicalCall
                || expression instanceof CanonicalSend
                || expression instanceof CanonicalDerivedInequality
                || expression instanceof CanonicalSuperSend
                || expression instanceof CanonicalReturn
                || expression instanceof CanonicalObject
                || expression instanceof CanonicalMapConstruction
                || expression instanceof CanonicalCompose) {
            return true;
        }
        if (expression instanceof CanonicalMember member) {
            return requiresComposedInvocation(member.receiver());
        }
        if (expression instanceof CanonicalIdentity identity) {
            return requiresComposedInvocation(identity.left())
                    || requiresComposedInvocation(identity.right());
        }
        if (expression instanceof CanonicalNotIdentity identity) {
            return requiresComposedInvocation(identity.left())
                    || requiresComposedInvocation(identity.right());
        }
        if (expression instanceof CanonicalCreate
                || expression instanceof CanonicalMultipleCreate
                || expression instanceof CanonicalAssign
                || expression instanceof CanonicalIndexedAssign) {
            return true;
        }
        return false;
    }

    private static boolean hasSpreadArgument(
            java.util.List<CanonicalExpression> arguments) {
        return arguments.stream()
                .anyMatch(CanonicalSpread.class::isInstance);
    }

    private static CanonicalExpression suppliedArgumentExpression(
            CanonicalExpression argument) {
        if (argument instanceof CanonicalSpread spread) {
            return spread.expression();
        }
        return argument;
    }

    private void emitBodySpreadArgumentVector(
            ProtosSemanticBytecodeRootNodeGen.Builder builder,
            java.util.List<CanonicalExpression> arguments,
            BytecodeLocal suppliedVector,
            BytecodeLocal preparedCall,
            BytecodeLocal childResult,
            BytecodeLocal resumeValue) {
        builder.beginStoreLocal(suppliedVector);
        builder.emitCreateSuppliedArgumentVector();
        builder.endStoreLocal();

        for (CanonicalExpression argument : arguments) {
            CanonicalExpression valueExpression =
                    suppliedArgumentExpression(argument);
            BytecodeLocal value =
                    builder.createLocal(
                            argument instanceof CanonicalSpread
                                    ? "spreadArgumentValue"
                                    : "callArgumentValue",
                            null);
            emitBodyExpressionToLocal(
                    builder,
                    valueExpression,
                    value,
                    preparedCall,
                    childResult,
                    resumeValue);

            if (argument instanceof CanonicalSpread) {
                builder.beginAppendSpreadSuppliedArgument();
                builder.emitLoadLocal(suppliedVector);
                builder.emitLoadLocal(value);
                emitCurrentActivation(builder);
                builder.endAppendSpreadSuppliedArgument();
            } else {
                builder.beginAppendSuppliedArgument();
                builder.emitLoadLocal(suppliedVector);
                builder.emitLoadLocal(value);
                builder.endAppendSuppliedArgument();
            }
        }
    }

    private void emitDefaultSpreadArgumentVector(
            ProtosSemanticBytecodeRootNodeGen.Builder builder,
            java.util.List<CanonicalExpression> arguments,
            BytecodeLocal suppliedVector,
            BytecodeLocal preparedCall,
            BytecodeLocal childResult,
            BytecodeLocal resumeValue) {
        builder.beginStoreLocal(suppliedVector);
        builder.emitCreateSuppliedArgumentVector();
        builder.endStoreLocal();

        for (CanonicalExpression argument : arguments) {
            CanonicalExpression valueExpression =
                    suppliedArgumentExpression(argument);
            BytecodeLocal value =
                    builder.createLocal(
                            argument instanceof CanonicalSpread
                                    ? "defaultSpreadArgumentValue"
                                    : "defaultArgumentValue",
                            null);
            emitDefaultExpressionToLocal(
                    builder,
                    valueExpression,
                    value,
                    preparedCall,
                    childResult,
                    resumeValue);

            if (argument instanceof CanonicalSpread) {
                builder.beginAppendSpreadSuppliedArgument();
                builder.emitLoadLocal(suppliedVector);
                builder.emitLoadLocal(value);
                emitCurrentActivation(builder);
                builder.endAppendSpreadSuppliedArgument();
            } else {
                builder.beginAppendSuppliedArgument();
                builder.emitLoadLocal(suppliedVector);
                builder.emitLoadLocal(value);
                builder.endAppendSuppliedArgument();
            }
        }
    }

    private void emitBodyExpressionToLocal(
            ProtosSemanticBytecodeRootNodeGen.Builder builder,
            CanonicalExpression expression,
            BytecodeLocal target,
            BytecodeLocal preparedCall,
            BytecodeLocal childResult,
            BytecodeLocal resumeValue) {
        if (expression instanceof CanonicalCall call) {
            builder.beginSourceSection(
                    call.span().startOffset(),
                    call.span().length());
            emitComposedCall(
                    builder,
                    call,
                    target,
                    preparedCall,
                    childResult,
                    resumeValue);
            builder.endSourceSection();
            return;
        }
        if (expression instanceof CanonicalSend send) {
            builder.beginSourceSection(
                    send.span().startOffset(),
                    send.span().length());
            emitComposedSend(
                    builder,
                    send,
                    target,
                    preparedCall,
                    childResult,
                    resumeValue);
            builder.endSourceSection();
            return;
        }
        if (expression instanceof CanonicalDerivedInequality inequality) {
            builder.beginSourceSection(
                    inequality.span().startOffset(),
                    inequality.span().length());
            emitBodyDerivedInequality(
                    builder,
                    inequality,
                    target,
                    preparedCall,
                    childResult,
                    resumeValue);
            builder.endSourceSection();
            return;
        }
        if (expression instanceof CanonicalSuperSend send) {
            builder.beginSourceSection(
                    send.span().startOffset(),
                    send.span().length());
            emitComposedSuperSend(
                    builder,
                    send,
                    target,
                    preparedCall,
                    childResult,
                    resumeValue);
            builder.endSourceSection();
            return;
        }
        if (expression instanceof CanonicalMapConstruction map) {
            emitBodyMapConstruction(
                    builder,
                    map,
                    target,
                    preparedCall,
                    childResult,
                    resumeValue);
            return;
        }
        if (expression instanceof CanonicalObject object) {
            emitBodyObjectLiteral(
                    builder,
                    object,
                    target,
                    preparedCall,
                    childResult,
                    resumeValue);
            return;
        }
        if (expression instanceof CanonicalCompose compose) {
            emitBodyCompose(
                    builder,
                    compose,
                    target,
                    preparedCall,
                    childResult,
                    resumeValue);
            return;
        }
        if (expression instanceof CanonicalReturn returnExpression) {
            emitBodyExpressionToLocal(
                    builder,
                    returnExpression.value(),
                    target,
                    preparedCall,
                    childResult,
                    resumeValue);
            builder.beginStoreLocal(target);
            builder.beginRaiseNonLocalReturn();
            emitCurrentActivation(builder);
            builder.emitLoadLocal(target);
            builder.endRaiseNonLocalReturn();
            builder.endStoreLocal();
            return;
        }
        if (expression instanceof CanonicalMember member) {
            BytecodeLocal receiverValue = builder.createLocal("memberReceiver", null);
            emitBodyExpressionToLocal(
                    builder, member.receiver(), receiverValue, preparedCall, childResult, resumeValue);
            builder.beginStoreLocal(target);
            builder.beginReadMember();
            emitCurrentActivation(builder);
            builder.emitLoadLocal(receiverValue);
            builder.emitLoadConstant(member.name());
            builder.endReadMember();
            builder.endStoreLocal();
            return;
        }
        if (expression instanceof CanonicalIdentity identity) {
            BytecodeLocal leftValue = builder.createLocal("identityLeft", null);
            BytecodeLocal rightValue = builder.createLocal("identityRight", null);
            emitBodyExpressionToLocal(
                    builder, identity.left(), leftValue, preparedCall, childResult, resumeValue);
            emitBodyExpressionToLocal(
                    builder, identity.right(), rightValue, preparedCall, childResult, resumeValue);
            builder.beginStoreLocal(target);
            builder.beginIdentity();
            builder.emitLoadLocal(leftValue);
            builder.emitLoadLocal(rightValue);
            builder.endIdentity();
            builder.endStoreLocal();
            return;
        }
        if (expression instanceof CanonicalNotIdentity identity) {
            BytecodeLocal leftValue = builder.createLocal("notIdentityLeft", null);
            BytecodeLocal rightValue = builder.createLocal("notIdentityRight", null);
            emitBodyExpressionToLocal(
                    builder, identity.left(), leftValue, preparedCall, childResult, resumeValue);
            emitBodyExpressionToLocal(
                    builder, identity.right(), rightValue, preparedCall, childResult, resumeValue);
            builder.beginStoreLocal(target);
            builder.beginNotIdentity();
            builder.emitLoadLocal(leftValue);
            builder.emitLoadLocal(rightValue);
            builder.endNotIdentity();
            builder.endStoreLocal();
            return;
        }
        if (expression instanceof CanonicalCreate create) {
            emitBodyCreate(
                    builder, create, target, preparedCall, childResult, resumeValue);
            return;
        }
        if (expression instanceof CanonicalMultipleCreate create) {
            emitBodyMultipleCreate(
                    builder, create, target, preparedCall, childResult, resumeValue);
            return;
        }
        if (expression instanceof CanonicalAssign assign) {
            emitBodyAssign(
                    builder, assign, target, preparedCall, childResult, resumeValue);
            return;
        }
        if (expression instanceof CanonicalIndexedAssign indexedAssign) {
            emitBodyIndexedAssign(
                    builder, indexedAssign, target, preparedCall, childResult, resumeValue);
            return;
        }
        builder.beginStoreLocal(target);
        emitExpression(builder, expression);
        builder.endStoreLocal();
    }

    private void emitDefaultExpressionToLocal(
            ProtosSemanticBytecodeRootNodeGen.Builder builder,
            CanonicalExpression expression,
            BytecodeLocal target,
            BytecodeLocal preparedCall,
            BytecodeLocal childResult,
            BytecodeLocal resumeValue) {
        if (expression instanceof CanonicalCall call) {
            emitComposedDefaultCall(
                    builder,
                    call,
                    target,
                    preparedCall,
                    childResult,
                    resumeValue);
            return;
        }
        if (expression instanceof CanonicalSend send) {
            emitComposedDefaultSend(
                    builder,
                    send,
                    target,
                    preparedCall,
                    childResult,
                    resumeValue);
            return;
        }
        if (expression instanceof CanonicalDerivedInequality inequality) {
            emitDefaultDerivedInequality(
                    builder,
                    inequality,
                    target,
                    preparedCall,
                    childResult,
                    resumeValue);
            return;
        }
        if (expression instanceof CanonicalSuperSend send) {
            emitComposedDefaultSuperSend(
                    builder,
                    send,
                    target,
                    preparedCall,
                    childResult,
                    resumeValue);
            return;
        }
        if (expression instanceof CanonicalMapConstruction map) {
            emitDefaultMapConstruction(
                    builder,
                    map,
                    target,
                    preparedCall,
                    childResult,
                    resumeValue);
            return;
        }
        if (expression instanceof CanonicalObject object) {
            emitDefaultObjectLiteral(
                    builder,
                    object,
                    target,
                    preparedCall,
                    childResult,
                    resumeValue);
            return;
        }
        if (expression instanceof CanonicalReturn returnExpression) {
            emitDefaultExpressionToLocal(
                    builder,
                    returnExpression.value(),
                    target,
                    preparedCall,
                    childResult,
                    resumeValue);
            builder.beginStoreLocal(target);
            builder.beginRaiseNonLocalReturn();
            emitCurrentActivation(builder);
            builder.emitLoadLocal(target);
            builder.endRaiseNonLocalReturn();
            builder.endStoreLocal();
            return;
        }
        if (expression instanceof CanonicalMember member) {
            BytecodeLocal receiverValue = builder.createLocal("defaultMemberReceiver", null);
            emitDefaultExpressionToLocal(
                    builder, member.receiver(), receiverValue, preparedCall, childResult, resumeValue);
            builder.beginStoreLocal(target);
            builder.beginReadMember();
            emitCurrentActivation(builder);
            builder.emitLoadLocal(receiverValue);
            builder.emitLoadConstant(member.name());
            builder.endReadMember();
            builder.endStoreLocal();
            return;
        }
        if (expression instanceof CanonicalIdentity identity) {
            BytecodeLocal leftValue = builder.createLocal("defaultIdentityLeft", null);
            BytecodeLocal rightValue = builder.createLocal("defaultIdentityRight", null);
            emitDefaultExpressionToLocal(
                    builder, identity.left(), leftValue, preparedCall, childResult, resumeValue);
            emitDefaultExpressionToLocal(
                    builder, identity.right(), rightValue, preparedCall, childResult, resumeValue);
            builder.beginStoreLocal(target);
            builder.beginIdentity();
            builder.emitLoadLocal(leftValue);
            builder.emitLoadLocal(rightValue);
            builder.endIdentity();
            builder.endStoreLocal();
            return;
        }
        if (expression instanceof CanonicalNotIdentity identity) {
            BytecodeLocal leftValue = builder.createLocal("defaultNotIdentityLeft", null);
            BytecodeLocal rightValue = builder.createLocal("defaultNotIdentityRight", null);
            emitDefaultExpressionToLocal(
                    builder, identity.left(), leftValue, preparedCall, childResult, resumeValue);
            emitDefaultExpressionToLocal(
                    builder, identity.right(), rightValue, preparedCall, childResult, resumeValue);
            builder.beginStoreLocal(target);
            builder.beginNotIdentity();
            builder.emitLoadLocal(leftValue);
            builder.emitLoadLocal(rightValue);
            builder.endNotIdentity();
            builder.endStoreLocal();
            return;
        }
        if (expression instanceof CanonicalCreate create) {
            emitDefaultCreate(
                    builder, create, target, preparedCall, childResult, resumeValue);
            return;
        }
        if (expression instanceof CanonicalMultipleCreate create) {
            emitDefaultMultipleCreate(
                    builder, create, target, preparedCall, childResult, resumeValue);
            return;
        }
        if (expression instanceof CanonicalAssign assign) {
            emitDefaultAssign(
                    builder, assign, target, preparedCall, childResult, resumeValue);
            return;
        }
        if (expression instanceof CanonicalIndexedAssign indexedAssign) {
            emitDefaultIndexedAssign(
                    builder, indexedAssign, target, preparedCall, childResult, resumeValue);
            return;
        }
        builder.beginStoreLocal(target);
        emitExpression(builder, expression);
        builder.endStoreLocal();
    }


    private void emitBodyDerivedInequality(
            ProtosSemanticBytecodeRootNodeGen.Builder builder,
            CanonicalDerivedInequality inequality,
            BytecodeLocal target,
            BytecodeLocal preparedCall,
            BytecodeLocal childResult,
            BytecodeLocal resumeValue) {
        BytecodeLocal equalityResult =
                builder.createLocal("derivedInequalityEqualityResult", null);

        CanonicalSend equalitySend =
                new CanonicalSend(
                        inequality.left(),
                        "==",
                        java.util.List.of(inequality.right()),
                        inequality.span());

        emitComposedSend(
                builder,
                equalitySend,
                equalityResult,
                preparedCall,
                childResult,
                resumeValue);

        builder.beginStoreLocal(target);
        builder.beginComplementEqualityResult();
        emitCurrentActivation(builder);
        builder.emitLoadLocal(equalityResult);
        builder.endComplementEqualityResult();
        builder.endStoreLocal();
    }

    private void emitDefaultDerivedInequality(
            ProtosSemanticBytecodeRootNodeGen.Builder builder,
            CanonicalDerivedInequality inequality,
            BytecodeLocal target,
            BytecodeLocal preparedCall,
            BytecodeLocal childResult,
            BytecodeLocal resumeValue) {
        BytecodeLocal equalityResult =
                builder.createLocal(
                        "defaultDerivedInequalityEqualityResult",
                        null);

        CanonicalSend equalitySend =
                new CanonicalSend(
                        inequality.left(),
                        "==",
                        java.util.List.of(inequality.right()),
                        inequality.span());

        emitComposedDefaultSend(
                builder,
                equalitySend,
                equalityResult,
                preparedCall,
                childResult,
                resumeValue);

        builder.beginStoreLocal(target);
        builder.beginComplementEqualityResult();
        emitCurrentActivation(builder);
        builder.emitLoadLocal(equalityResult);
        builder.endComplementEqualityResult();
        builder.endStoreLocal();
    }

    private void emitBodyMapConstruction(
            ProtosSemanticBytecodeRootNodeGen.Builder builder,
            CanonicalMapConstruction map,
            BytecodeLocal result,
            BytecodeLocal preparedCall,
            BytecodeLocal childResult,
            BytecodeLocal resumeValue) {
        emitMapConstruction(
                builder,
                map,
                result,
                preparedCall,
                childResult,
                resumeValue,
                false);
    }

    private void emitDefaultMapConstruction(
            ProtosSemanticBytecodeRootNodeGen.Builder builder,
            CanonicalMapConstruction map,
            BytecodeLocal result,
            BytecodeLocal preparedCall,
            BytecodeLocal childResult,
            BytecodeLocal resumeValue) {
        emitMapConstruction(
                builder,
                map,
                result,
                preparedCall,
                childResult,
                resumeValue,
                true);
    }

    private void emitMapConstruction(
            ProtosSemanticBytecodeRootNodeGen.Builder builder,
            CanonicalMapConstruction map,
            BytecodeLocal result,
            BytecodeLocal preparedCall,
            BytecodeLocal childResult,
            BytecodeLocal resumeValue,
            boolean defaultContext) {
        requireDefaultScratch(
                result,
                preparedCall,
                childResult,
                resumeValue);

        BytecodeLocal factoryValue =
                builder.createLocal(
                        defaultContext
                                ? "defaultMapConstructionFactory"
                                : "mapConstructionFactory",
                        null);
        BytecodeLocal mapValue =
                builder.createLocal(
                        defaultContext
                                ? "defaultMapConstructionValue"
                                : "mapConstructionValue",
                        null);
        BytecodeLocal factoryResult =
                builder.createLocal(
                        defaultContext
                                ? "defaultMapConstructionFactoryResult"
                                : "mapConstructionFactoryResult",
                        null);

        if (defaultContext) {
            emitDefaultExpressionToLocal(
                    builder,
                    map.factory(),
                    factoryValue,
                    preparedCall,
                    childResult,
                    resumeValue);
        } else {
            emitBodyExpressionToLocal(
                    builder,
                    map.factory(),
                    factoryValue,
                    preparedCall,
                    childResult,
                    resumeValue);
        }

        builder.beginStoreLocal(preparedCall);
        builder.beginPrepareMapConstructionFactoryCall();
        emitCurrentActivation(builder);
        builder.emitLoadLocal(factoryValue);
        builder.endPrepareMapConstructionFactoryCall();
        builder.endStoreLocal();

        emitPreparedInvocation(
                builder,
                factoryResult,
                preparedCall,
                childResult,
                resumeValue);

        builder.beginStoreLocal(mapValue);
        builder.beginFinishMapConstructionFactoryCall();
        emitCurrentActivation(builder);
        builder.emitLoadLocal(factoryResult);
        builder.endFinishMapConstructionFactoryCall();
        builder.endStoreLocal();

        for (CanonicalMapConstruction.Entry entry : map.entries()) {
            BytecodeLocal key =
                    builder.createLocal(
                            defaultContext
                                    ? "defaultMapConstructionKey"
                                    : "mapConstructionKey",
                            null);
            BytecodeLocal value =
                    builder.createLocal(
                            defaultContext
                                    ? "defaultMapConstructionEntryValue"
                                    : "mapConstructionEntryValue",
                            null);
            BytecodeLocal initialDefinition =
                    builder.createLocal(
                            defaultContext
                                    ? "defaultMapInitialDefinition"
                                    : "mapInitialDefinition",
                            null);
            BytecodeLocal callbackResult =
                    builder.createLocal(
                            defaultContext
                                    ? "defaultMapInitialDefinitionCallbackResult"
                                    : "mapInitialDefinitionCallbackResult",
                            null);

            if (defaultContext) {
                emitDefaultExpressionToLocal(
                        builder,
                        entry.key(),
                        key,
                        preparedCall,
                        childResult,
                        resumeValue);
                emitDefaultExpressionToLocal(
                        builder,
                        entry.value(),
                        value,
                        preparedCall,
                        childResult,
                        resumeValue);
            } else {
                emitBodyExpressionToLocal(
                        builder,
                        entry.key(),
                        key,
                        preparedCall,
                        childResult,
                        resumeValue);
                emitBodyExpressionToLocal(
                        builder,
                        entry.value(),
                        value,
                        preparedCall,
                        childResult,
                        resumeValue);
            }

            builder.beginStoreLocal(initialDefinition);
            builder.beginPrepareMapInitialDefinition();
            emitCurrentActivation(builder);
            builder.emitLoadLocal(mapValue);
            builder.emitLoadLocal(key);
            builder.emitLoadLocal(value);
            builder.endPrepareMapInitialDefinition();
            builder.endStoreLocal();

            builder.beginEnterMapInitialDefinitionComparison();
            builder.emitLoadLocal(initialDefinition);
            builder.endEnterMapInitialDefinitionComparison();

            builder.beginTryFinally(
                    () -> {
                        builder.beginLeaveMapInitialDefinitionComparison();
                        builder.emitLoadLocal(initialDefinition);
                        builder.endLeaveMapInitialDefinitionComparison();
                    });

            builder.beginBlock();

            builder.beginStoreLocal(preparedCall);
            builder.beginPrepareMapInitialDefinitionHashCall();
            builder.emitLoadLocal(initialDefinition);
            builder.endPrepareMapInitialDefinitionHashCall();
            builder.endStoreLocal();

            emitPreparedInvocation(
                    builder,
                    callbackResult,
                    preparedCall,
                    childResult,
                    resumeValue);

            builder.endBlock();
            builder.endTryFinally();

            builder.beginAcceptMapInitialDefinitionHashResult();
            builder.emitLoadLocal(initialDefinition);
            builder.emitLoadLocal(callbackResult);
            builder.endAcceptMapInitialDefinitionHashResult();

            builder.beginWhile();

            builder.beginMapInitialDefinitionNeedsEquality();
            builder.emitLoadLocal(initialDefinition);
            builder.endMapInitialDefinitionNeedsEquality();

            builder.beginBlock();

            builder.beginEnterMapInitialDefinitionComparison();
            builder.emitLoadLocal(initialDefinition);
            builder.endEnterMapInitialDefinitionComparison();

            builder.beginTryFinally(
                    () -> {
                        builder.beginLeaveMapInitialDefinitionComparison();
                        builder.emitLoadLocal(initialDefinition);
                        builder.endLeaveMapInitialDefinitionComparison();
                    });

            builder.beginBlock();

            builder.beginStoreLocal(preparedCall);
            builder.beginPrepareMapInitialDefinitionEqualityCall();
            builder.emitLoadLocal(initialDefinition);
            builder.endPrepareMapInitialDefinitionEqualityCall();
            builder.endStoreLocal();

            emitPreparedInvocation(
                    builder,
                    callbackResult,
                    preparedCall,
                    childResult,
                    resumeValue);

            builder.endBlock();
            builder.endTryFinally();

            builder.beginAcceptMapInitialDefinitionEqualityResult();
            builder.emitLoadLocal(initialDefinition);
            builder.emitLoadLocal(callbackResult);
            builder.endAcceptMapInitialDefinitionEqualityResult();

            builder.endBlock();
            builder.endWhile();

            builder.beginFinishMapInitialDefinition();
            builder.emitLoadLocal(initialDefinition);
            builder.endFinishMapInitialDefinition();
        }

        builder.beginStoreLocal(result);
        builder.emitLoadLocal(mapValue);
        builder.endStoreLocal();
    }

    private void emitBodySequenceToLocal(
            ProtosSemanticBytecodeRootNodeGen.Builder builder,
            CanonicalSequence sequence,
            BytecodeLocal result,
            BytecodeLocal preparedCall,
            BytecodeLocal childResult,
            BytecodeLocal resumeValue) {
        if (sequence.expressions().isEmpty()) {
            builder.beginStoreLocal(result);
            builder.emitLoadConstant(ProtosNullValue.INSTANCE);
            builder.endStoreLocal();
            return;
        }

        for (CanonicalExpression expression : sequence.expressions()) {
            SourceSpan span = expression.span();
            builder.beginSourceSection(span.startOffset(), span.length());
            builder.beginTag(
                    StandardTags.StatementTag.class,
                    StandardTags.ExpressionTag.class);
            builder.beginBlock();
            emitBodyExpressionToLocal(
                    builder,
                    expression,
                    result,
                    preparedCall,
                    childResult,
                    resumeValue);
            builder.endBlock();
            builder.endTag(
                    StandardTags.StatementTag.class,
                    StandardTags.ExpressionTag.class);
            builder.endSourceSection();
        }
    }

    private static void emitLocalNoop(
            ProtosSemanticBytecodeRootNodeGen.Builder builder,
            BytecodeLocal local) {
        builder.beginStoreLocal(local);
        builder.emitLoadLocal(local);
        builder.endStoreLocal();
    }

    private void emitBodyObjectLiteral(
            ProtosSemanticBytecodeRootNodeGen.Builder builder,
            CanonicalObject object,
            BytecodeLocal result,
            BytecodeLocal preparedCall,
            BytecodeLocal childResult,
            BytecodeLocal resumeValue) {
        requireDefaultScratch(result, preparedCall, childResult, resumeValue);
        BytecodeLocal parent = builder.createLocal("objectParent", null);

        if (object.parent().isPresent()) {
            emitBodyExpressionToLocal(
                    builder,
                    object.parent().orElseThrow(),
                    parent,
                    preparedCall,
                    childResult,
                    resumeValue);
        } else {
            builder.beginStoreLocal(parent);
            builder.emitLoadConstant(ProtosObjectValue.rootObject());
            builder.endStoreLocal();
        }

        emitInlineObjectConstruction(builder, object, parent, result);
    }

    private void emitDefaultObjectLiteral(
            ProtosSemanticBytecodeRootNodeGen.Builder builder,
            CanonicalObject object,
            BytecodeLocal result,
            BytecodeLocal preparedCall,
            BytecodeLocal childResult,
            BytecodeLocal resumeValue) {
        requireDefaultScratch(result, preparedCall, childResult, resumeValue);
        BytecodeLocal parent = builder.createLocal("defaultObjectParent", null);

        if (object.parent().isPresent()) {
            emitDefaultExpressionToLocal(
                    builder,
                    object.parent().orElseThrow(),
                    parent,
                    preparedCall,
                    childResult,
                    resumeValue);
        } else {
            builder.beginStoreLocal(parent);
            builder.emitLoadConstant(ProtosObjectValue.rootObject());
            builder.endStoreLocal();
        }

        emitInlineObjectConstruction(builder, object, parent, result);
    }

    private void emitBodyCompose(
            ProtosSemanticBytecodeRootNodeGen.Builder builder,
            CanonicalCompose compose,
            BytecodeLocal result,
            BytecodeLocal preparedCall,
            BytecodeLocal childResult,
            BytecodeLocal resumeValue) {
        BytecodeLocal sourceValue = builder.createLocal("compositionSource", null);
        emitBodyExpressionToLocal(
                builder,
                compose.object(),
                sourceValue,
                preparedCall,
                childResult,
                resumeValue);
        builder.beginStoreLocal(result);
        builder.beginComposeLocalSlots();
        emitCurrentActivation(builder);
        builder.emitLoadLocal(sourceValue);
        builder.emitLoadConstant(composeReservedNames(compose));
        builder.endComposeLocalSlots();
        builder.endStoreLocal();
    }

    /**
     * PLAT041 C′ inline object construction. The object body is an ordinary
     * resumable region of the enclosing root, not a physical root: the
     * constructed object and its construction activation live in Bytecode
     * locals, so any suspension inside the body is captured by the enclosing
     * root's own continuation, and Error/ensure/non-local-return transfers
     * propagate through the enclosing root exactly as from any other
     * operation (the construction activation inherits the enclosing Task or
     * dynamic-control authority and homes; see {@link
     * com.guillermomolina.protos.runtime.ProtosActivation#forObjectConstruction}).
     *
     * <p>The body is not a lexical execution context: it receives no frame
     * lexical authority and never takes the direct-local fast path, and its
     * binding-analysis selection is exactly that of a non-genuine root, so
     * Closures nested inside it resolve and capture exactly as before.
     */
    private void emitInlineObjectConstruction(
            ProtosSemanticBytecodeRootNodeGen.Builder builder,
            CanonicalObject object,
            BytecodeLocal parent,
            BytecodeLocal result) {
        BytecodeLocal constructed = builder.createLocal("constructedObject", null);
        BytecodeLocal constructionActivation =
                builder.createLocal("constructionActivation", null);

        builder.beginStoreLocal(constructed);
        builder.beginNewConstructedObject();
        builder.emitLoadLocal(parent);
        builder.endNewConstructedObject();
        builder.endStoreLocal();

        builder.beginStoreLocal(constructionActivation);
        builder.beginNewObjectConstructionActivation();
        emitCurrentActivation(builder);
        builder.emitLoadLocal(constructed);
        builder.endNewObjectConstructionActivation();
        builder.endStoreLocal();

        validateSpan(object.body().span());
        /*
         * Mirrors exactly the binding-analysis selection the former object
         * body root performed (it may lazily establish the module analysis
         * that Closure roots nested in object bodies resolve against).
         */
        bindingAnalysisFor(object.body(), null);

        if (!object.body().expressions().isEmpty()) {
            CanonicalBindingAnalysis savedAnalysis = currentRootAnalysis;
            CanonicalLexicalScope savedTopScope = currentRootTopScope;
            java.util.Map<String, BytecodeLocal> savedFrameLocals =
                    currentRootFrameLocals;
            BytecodeLocal savedActivationLocal = currentActivationLocal;
            try {
                currentRootAnalysis = null;
                currentRootTopScope = null;
                currentRootFrameLocals = java.util.Map.of();
                currentActivationLocal = constructionActivation;
                BytecodeLocal bodyResult =
                        builder.createLocal("objectBodyResult", null);
                emitStatementsToLocal(builder, object.body(), bodyResult);
            } finally {
                currentRootAnalysis = savedAnalysis;
                currentRootTopScope = savedTopScope;
                currentRootFrameLocals = savedFrameLocals;
                currentActivationLocal = savedActivationLocal;
            }
        }

        builder.beginStoreLocal(result);
        builder.emitLoadLocal(constructed);
        builder.endStoreLocal();
    }

    private void emitBodyCreate(
            ProtosSemanticBytecodeRootNodeGen.Builder builder,
            CanonicalCreate create,
            BytecodeLocal result,
            BytecodeLocal preparedCall,
            BytecodeLocal childResult,
            BytecodeLocal resumeValue) {
        BytecodeLocal value = builder.createLocal("createValue", null);
        if (create.target().isEmpty()) {
            emitBodyExpressionToLocal(
                    builder, create.value(), value, preparedCall, childResult, resumeValue);
            builder.beginStoreLocal(result);
            builder.beginCreateCurrentLocalSlot();
            emitCurrentActivation(builder);
            builder.emitLoadConstant(create.name());
            builder.emitLoadLocal(value);
            builder.endCreateCurrentLocalSlot();
            builder.endStoreLocal();
            return;
        }

        BytecodeLocal rawTarget = builder.createLocal("createRawTarget", null);
        BytecodeLocal mutationTarget = builder.createLocal("createMutationTarget", null);
        emitBodyExpressionToLocal(
                builder,
                create.target().orElseThrow(),
                rawTarget,
                preparedCall,
                childResult,
                resumeValue);
        builder.beginStoreLocal(mutationTarget);
        builder.beginRequireObjectMutationTarget();
        emitCurrentActivation(builder);
        builder.emitLoadLocal(rawTarget);
        builder.endRequireObjectMutationTarget();
        builder.endStoreLocal();
        emitBodyExpressionToLocal(
                builder, create.value(), value, preparedCall, childResult, resumeValue);
        builder.beginStoreLocal(result);
        builder.beginCreateLocalSlot();
        emitCurrentActivation(builder);
        builder.emitLoadLocal(mutationTarget);
        builder.emitLoadConstant(create.name());
        builder.emitLoadLocal(value);
        builder.endCreateLocalSlot();
        builder.endStoreLocal();
    }

    @com.oracle.truffle.api.CompilerDirectives.TruffleBoundary
    private static String[] multipleCreateNamesConstant(java.util.List<String> names) {
        Objects.requireNonNull(names, "names");
        String[] result = new String[names.size()];
        for (int index = 0; index < result.length; index++) {
            result[index] = Objects.requireNonNull(names.get(index), "names[" + index + "]");
        }
        return result;
    }

    private void emitBodyMultipleCreate(
            ProtosSemanticBytecodeRootNodeGen.Builder builder,
            CanonicalMultipleCreate create,
            BytecodeLocal result,
            BytecodeLocal preparedCall,
            BytecodeLocal childResult,
            BytecodeLocal resumeValue) {
        BytecodeLocal source = builder.createLocal("multipleCreateSource", null);

        emitBodyExpressionToLocal(
                builder,
                create.value(),
                source,
                preparedCall,
                childResult,
                resumeValue);

        builder.beginStoreLocal(result);
        builder.beginMultipleCreateLocalSlots();
        emitCurrentActivation(builder);
        builder.emitLoadConstant(multipleCreateNamesConstant(create.names()));
        builder.emitLoadLocal(source);
        builder.endMultipleCreateLocalSlots();
        builder.endStoreLocal();
    }

    private void emitBodyAssign(
            ProtosSemanticBytecodeRootNodeGen.Builder builder,
            CanonicalAssign assign,
            BytecodeLocal result,
            BytecodeLocal preparedCall,
            BytecodeLocal childResult,
            BytecodeLocal resumeValue) {
        BytecodeLocal value = builder.createLocal("assignValue", null);
        BytecodeLocal mutationTarget = builder.createLocal("assignMutationTarget", null);
        java.util.Optional<CanonicalBindingResolution.CapturedResolved> capturedResolution =
                capturedResolvedAssignment(assign);
        BytecodeLocal capturedOwnerLocal =
                capturedResolution.map(this::capturedOwnerBytecodeLocal).orElse(null);

        if (assign.target().isPresent()) {
            BytecodeLocal rawTarget = builder.createLocal("assignRawTarget", null);
            emitBodyExpressionToLocal(
                    builder,
                    assign.target().orElseThrow(),
                    rawTarget,
                    preparedCall,
                    childResult,
                    resumeValue);
            builder.beginStoreLocal(mutationTarget);
            builder.beginRequireObjectMutationTarget();
            emitCurrentActivation(builder);
            builder.emitLoadLocal(rawTarget);
            builder.endRequireObjectMutationTarget();
            builder.endStoreLocal();
        } else if (capturedResolution.isPresent()) {
            CanonicalBindingResolution.CapturedResolved captured =
                    capturedResolution.orElseThrow();
            /*
             * Critical ordering invariant: resolve and retain the exact
             * destination before the RHS is evaluated.
             */
            builder.beginStoreLocal(mutationTarget);
            if (capturedOwnerLocal != null) {
                builder.beginResolveCapturedMaterializedWritableLexicalTarget(
                        capturedOwnerLocal);
                emitCurrentActivation(builder);
                builder.emitLoadConstant(assign.name());
                builder.emitLoadConstant(captured.lexicalDepth());
                builder.endResolveCapturedMaterializedWritableLexicalTarget();
            } else {
                builder.beginResolveCapturedWritableLexicalTarget();
                emitCurrentActivation(builder);
                builder.emitLoadConstant(assign.name());
                builder.emitLoadConstant(captured.lexicalDepth());
                builder.emitLoadConstant(frameBackedOrdinal(captured.identity()));
                builder.endResolveCapturedWritableLexicalTarget();
            }
            builder.endStoreLocal();
        } else {
            /* AST authority resolves the writable lexical destination before RHS evaluation. */
            builder.beginStoreLocal(mutationTarget);
            builder.beginResolveWritableLexicalTarget();
            emitCurrentActivation(builder);
            builder.emitLoadConstant(assign.name());
            builder.endResolveWritableLexicalTarget();
            builder.endStoreLocal();
        }

        emitBodyExpressionToLocal(
                builder, assign.value(), value, preparedCall, childResult, resumeValue);
        builder.beginStoreLocal(result);
        if (capturedResolution.isPresent()) {
            if (capturedOwnerLocal != null) {
                builder.beginAssignCapturedMaterializedLocal(capturedOwnerLocal);
                emitCurrentActivation(builder);
                builder.emitLoadLocal(mutationTarget);
                builder.emitLoadConstant(assign.name());
                builder.emitLoadLocal(value);
                builder.endAssignCapturedMaterializedLocal();
            } else {
                builder.beginAssignCapturedFrameLocal();
                emitCurrentActivation(builder);
                builder.emitLoadLocal(mutationTarget);
                builder.emitLoadConstant(assign.name());
                builder.emitLoadLocal(value);
                builder.endAssignCapturedFrameLocal();
            }
        } else if (assign.target().isPresent()) {
            builder.beginAssignLocalSlot();
            emitCurrentActivation(builder);
            builder.emitLoadLocal(mutationTarget);
            builder.emitLoadConstant(assign.name());
            builder.emitLoadLocal(value);
            builder.endAssignLocalSlot();
        } else {
            builder.beginAssignResolvedLexicalTarget();
            emitCurrentActivation(builder);
            builder.emitLoadLocal(mutationTarget);
            builder.emitLoadConstant(assign.name());
            builder.emitLoadLocal(value);
            builder.endAssignResolvedLexicalTarget();
        }
        builder.endStoreLocal();
    }

    private void emitBodyIndexedAssign(
            ProtosSemanticBytecodeRootNodeGen.Builder builder,
            CanonicalIndexedAssign indexedAssign,
            BytecodeLocal result,
            BytecodeLocal preparedCall,
            BytecodeLocal childResult,
            BytecodeLocal resumeValue) {
        BytecodeLocal receiver = builder.createLocal("indexedAssignReceiver", null);
        BytecodeLocal index = builder.createLocal("indexedAssignIndex", null);
        BytecodeLocal value = builder.createLocal("indexedAssignValue", null);
        BytecodeLocal dispatchResult = builder.createLocal("indexedAssignDispatchResult", null);

        emitBodyExpressionToLocal(
                builder,
                indexedAssign.receiver(),
                receiver,
                preparedCall,
                childResult,
                resumeValue);
        emitBodyExpressionToLocal(
                builder,
                indexedAssign.index(),
                index,
                preparedCall,
                childResult,
                resumeValue);
        emitBodyExpressionToLocal(
                builder,
                indexedAssign.value(),
                value,
                preparedCall,
                childResult,
                resumeValue);

        builder.beginStoreLocal(preparedCall);
        builder.beginPrepareSendArguments();
        builder.emitLoadLocal(receiver);
        builder.emitLoadConstant("atPut");
        emitCurrentActivation(builder);
        builder.emitLoadLocal(index);
        builder.emitLoadLocal(value);
        builder.endPrepareSendArguments();
        builder.endStoreLocal();
        emitPreparedInvocation(
                builder,
                dispatchResult,
                preparedCall,
                childResult,
                resumeValue);

        /* Indexed assignment returns the exact RHS, never the atPut result. */
        builder.beginStoreLocal(result);
        builder.emitLoadLocal(value);
        builder.endStoreLocal();
    }

    private void emitDefaultCreate(
            ProtosSemanticBytecodeRootNodeGen.Builder builder,
            CanonicalCreate create,
            BytecodeLocal result,
            BytecodeLocal preparedCall,
            BytecodeLocal childResult,
            BytecodeLocal resumeValue) {
        BytecodeLocal value = builder.createLocal("defaultCreateValue", null);
        if (create.target().isEmpty()) {
            emitDefaultExpressionToLocal(
                    builder, create.value(), value, preparedCall, childResult, resumeValue);
            builder.beginStoreLocal(result);
            builder.beginCreateCurrentLocalSlot();
            emitCurrentActivation(builder);
            builder.emitLoadConstant(create.name());
            builder.emitLoadLocal(value);
            builder.endCreateCurrentLocalSlot();
            builder.endStoreLocal();
            return;
        }

        BytecodeLocal rawTarget = builder.createLocal("defaultCreateRawTarget", null);
        BytecodeLocal mutationTarget = builder.createLocal("defaultCreateMutationTarget", null);
        emitDefaultExpressionToLocal(
                builder,
                create.target().orElseThrow(),
                rawTarget,
                preparedCall,
                childResult,
                resumeValue);
        builder.beginStoreLocal(mutationTarget);
        builder.beginRequireObjectMutationTarget();
        emitCurrentActivation(builder);
        builder.emitLoadLocal(rawTarget);
        builder.endRequireObjectMutationTarget();
        builder.endStoreLocal();
        emitDefaultExpressionToLocal(
                builder, create.value(), value, preparedCall, childResult, resumeValue);
        builder.beginStoreLocal(result);
        builder.beginCreateLocalSlot();
        emitCurrentActivation(builder);
        builder.emitLoadLocal(mutationTarget);
        builder.emitLoadConstant(create.name());
        builder.emitLoadLocal(value);
        builder.endCreateLocalSlot();
        builder.endStoreLocal();
    }

    private void emitDefaultMultipleCreate(
            ProtosSemanticBytecodeRootNodeGen.Builder builder,
            CanonicalMultipleCreate create,
            BytecodeLocal result,
            BytecodeLocal preparedCall,
            BytecodeLocal childResult,
            BytecodeLocal resumeValue) {
        BytecodeLocal source =
                builder.createLocal("defaultMultipleCreateSource", null);

        emitDefaultExpressionToLocal(
                builder,
                create.value(),
                source,
                preparedCall,
                childResult,
                resumeValue);

        builder.beginStoreLocal(result);
        builder.beginMultipleCreateLocalSlots();
        emitCurrentActivation(builder);
        builder.emitLoadConstant(multipleCreateNamesConstant(create.names()));
        builder.emitLoadLocal(source);
        builder.endMultipleCreateLocalSlots();
        builder.endStoreLocal();
    }

    private void emitDefaultAssign(
            ProtosSemanticBytecodeRootNodeGen.Builder builder,
            CanonicalAssign assign,
            BytecodeLocal result,
            BytecodeLocal preparedCall,
            BytecodeLocal childResult,
            BytecodeLocal resumeValue) {
        BytecodeLocal value = builder.createLocal("defaultAssignValue", null);
        BytecodeLocal mutationTarget = builder.createLocal("defaultAssignMutationTarget", null);
        java.util.Optional<CanonicalBindingResolution.CapturedResolved> capturedResolution =
                capturedResolvedAssignment(assign);
        BytecodeLocal capturedOwnerLocal =
                capturedResolution.map(this::capturedOwnerBytecodeLocal).orElse(null);

        if (assign.target().isPresent()) {
            BytecodeLocal rawTarget = builder.createLocal("defaultAssignRawTarget", null);
            emitDefaultExpressionToLocal(
                    builder,
                    assign.target().orElseThrow(),
                    rawTarget,
                    preparedCall,
                    childResult,
                    resumeValue);
            builder.beginStoreLocal(mutationTarget);
            builder.beginRequireObjectMutationTarget();
            emitCurrentActivation(builder);
            builder.emitLoadLocal(rawTarget);
            builder.endRequireObjectMutationTarget();
            builder.endStoreLocal();
        } else if (capturedResolution.isPresent()) {
            CanonicalBindingResolution.CapturedResolved captured =
                    capturedResolution.orElseThrow();
            builder.beginStoreLocal(mutationTarget);
            if (capturedOwnerLocal != null) {
                builder.beginResolveCapturedMaterializedWritableLexicalTarget(
                        capturedOwnerLocal);
                emitCurrentActivation(builder);
                builder.emitLoadConstant(assign.name());
                builder.emitLoadConstant(captured.lexicalDepth());
                builder.endResolveCapturedMaterializedWritableLexicalTarget();
            } else {
                builder.beginResolveCapturedWritableLexicalTarget();
                emitCurrentActivation(builder);
                builder.emitLoadConstant(assign.name());
                builder.emitLoadConstant(captured.lexicalDepth());
                builder.emitLoadConstant(frameBackedOrdinal(captured.identity()));
                builder.endResolveCapturedWritableLexicalTarget();
            }
            builder.endStoreLocal();
        } else {
            builder.beginStoreLocal(mutationTarget);
            builder.beginResolveWritableLexicalTarget();
            emitCurrentActivation(builder);
            builder.emitLoadConstant(assign.name());
            builder.endResolveWritableLexicalTarget();
            builder.endStoreLocal();
        }

        emitDefaultExpressionToLocal(
                builder, assign.value(), value, preparedCall, childResult, resumeValue);
        builder.beginStoreLocal(result);
        if (capturedResolution.isPresent()) {
            if (capturedOwnerLocal != null) {
                builder.beginAssignCapturedMaterializedLocal(capturedOwnerLocal);
                emitCurrentActivation(builder);
                builder.emitLoadLocal(mutationTarget);
                builder.emitLoadConstant(assign.name());
                builder.emitLoadLocal(value);
                builder.endAssignCapturedMaterializedLocal();
            } else {
                builder.beginAssignCapturedFrameLocal();
                emitCurrentActivation(builder);
                builder.emitLoadLocal(mutationTarget);
                builder.emitLoadConstant(assign.name());
                builder.emitLoadLocal(value);
                builder.endAssignCapturedFrameLocal();
            }
        } else if (assign.target().isPresent()) {
            builder.beginAssignLocalSlot();
            emitCurrentActivation(builder);
            builder.emitLoadLocal(mutationTarget);
            builder.emitLoadConstant(assign.name());
            builder.emitLoadLocal(value);
            builder.endAssignLocalSlot();
        } else {
            builder.beginAssignResolvedLexicalTarget();
            emitCurrentActivation(builder);
            builder.emitLoadLocal(mutationTarget);
            builder.emitLoadConstant(assign.name());
            builder.emitLoadLocal(value);
            builder.endAssignResolvedLexicalTarget();
        }
        builder.endStoreLocal();
    }

    private void emitDefaultIndexedAssign(
            ProtosSemanticBytecodeRootNodeGen.Builder builder,
            CanonicalIndexedAssign indexedAssign,
            BytecodeLocal result,
            BytecodeLocal preparedCall,
            BytecodeLocal childResult,
            BytecodeLocal resumeValue) {
        BytecodeLocal receiver = builder.createLocal("defaultIndexedAssignReceiver", null);
        BytecodeLocal index = builder.createLocal("defaultIndexedAssignIndex", null);
        BytecodeLocal value = builder.createLocal("defaultIndexedAssignValue", null);
        BytecodeLocal dispatchResult = builder.createLocal("defaultIndexedAssignDispatchResult", null);

        emitDefaultExpressionToLocal(
                builder,
                indexedAssign.receiver(),
                receiver,
                preparedCall,
                childResult,
                resumeValue);
        emitDefaultExpressionToLocal(
                builder,
                indexedAssign.index(),
                index,
                preparedCall,
                childResult,
                resumeValue);
        emitDefaultExpressionToLocal(
                builder,
                indexedAssign.value(),
                value,
                preparedCall,
                childResult,
                resumeValue);

        builder.beginStoreLocal(preparedCall);
        builder.beginPrepareSendArguments();
        builder.emitLoadLocal(receiver);
        builder.emitLoadConstant("atPut");
        emitCurrentActivation(builder);
        builder.emitLoadLocal(index);
        builder.emitLoadLocal(value);
        builder.endPrepareSendArguments();
        builder.endStoreLocal();
        emitPreparedInvocation(
                builder,
                dispatchResult,
                preparedCall,
                childResult,
                resumeValue);

        builder.beginStoreLocal(result);
        builder.emitLoadLocal(value);
        builder.endStoreLocal();
    }

    private void emitComposedSuperSend(
            ProtosSemanticBytecodeRootNodeGen.Builder builder,
            CanonicalSuperSend send,
            BytecodeLocal result,
            BytecodeLocal preparedCall,
            BytecodeLocal childResult,
            BytecodeLocal resumeValue) {
        requireDefaultScratch(result, preparedCall, childResult, resumeValue);
        boolean stageArguments = hasComposedArgument(send.arguments());
        boolean spreadArguments = hasSpreadArgument(send.arguments());
        boolean stageInputs = stageArguments || spreadArguments;
        BytecodeLocal suppliedVector = null;
        java.util.List<BytecodeLocal> argumentValues = java.util.List.of();

        builder.beginTag(StandardTags.CallTag.class);
        builder.beginBlock();

        if (stageInputs) {
            if (spreadArguments) {
                suppliedVector = builder.createLocal("superSuppliedArgumentVector", null);
                emitBodySpreadArgumentVector(
                        builder,
                        send.arguments(),
                        suppliedVector,
                        preparedCall,
                        childResult,
                        resumeValue);
            } else {
                argumentValues = new java.util.ArrayList<>(send.arguments().size());
                for (CanonicalExpression argument : send.arguments()) {
                    BytecodeLocal argumentValue = builder.createLocal("superArgument", null);
                    emitBodyExpressionToLocal(
                            builder,
                            argument,
                            argumentValue,
                            preparedCall,
                            childResult,
                            resumeValue);
                    argumentValues.add(argumentValue);
                }
            }
        }

        builder.beginStoreLocal(preparedCall);
        if (spreadArguments) {
            builder.beginPrepareSuperSendVector();
            builder.emitLoadConstant(send.message());
            emitCurrentActivation(builder);
            builder.emitLoadLocal(suppliedVector);
            builder.endPrepareSuperSendVector();
        } else {
            builder.beginPrepareSuperSendArguments();
            builder.emitLoadConstant(send.message());
            emitCurrentActivation(builder);
            if (stageInputs) {
                for (BytecodeLocal argumentValue : argumentValues) {
                    builder.emitLoadLocal(argumentValue);
                }
            } else {
                for (CanonicalExpression argument : send.arguments()) {
                    emitExpression(builder, argument);
                }
            }
            builder.endPrepareSuperSendArguments();
        }
        builder.endStoreLocal();

        emitPreparedInvocation(
                builder,
                result,
                preparedCall,
                childResult,
                resumeValue);
        builder.endBlock();
        builder.endTag(StandardTags.CallTag.class);
    }

    private void emitComposedDefaultSuperSend(
            ProtosSemanticBytecodeRootNodeGen.Builder builder,
            CanonicalSuperSend send,
            BytecodeLocal result,
            BytecodeLocal preparedCall,
            BytecodeLocal childResult,
            BytecodeLocal resumeValue) {
        requireDefaultScratch(result, preparedCall, childResult, resumeValue);
        boolean stageArguments = hasComposedArgument(send.arguments());
        boolean spreadArguments = hasSpreadArgument(send.arguments());
        boolean stageInputs = stageArguments || spreadArguments;
        BytecodeLocal suppliedVector = null;
        java.util.List<BytecodeLocal> argumentValues = java.util.List.of();

        builder.beginSourceSection(
                send.span().startOffset(),
                send.span().length());
        builder.beginTag(StandardTags.CallTag.class);
        builder.beginBlock();

        if (stageInputs) {
            if (spreadArguments) {
                suppliedVector = builder.createLocal("defaultSuperSuppliedArgumentVector", null);
                emitDefaultSpreadArgumentVector(
                        builder,
                        send.arguments(),
                        suppliedVector,
                        preparedCall,
                        childResult,
                        resumeValue);
            } else {
                argumentValues = new java.util.ArrayList<>(send.arguments().size());
                for (CanonicalExpression argument : send.arguments()) {
                    BytecodeLocal argumentValue = builder.createLocal("defaultSuperArgument", null);
                    emitDefaultExpressionToLocal(
                            builder,
                            argument,
                            argumentValue,
                            preparedCall,
                            childResult,
                            resumeValue);
                    argumentValues.add(argumentValue);
                }
            }
        }

        builder.beginStoreLocal(preparedCall);
        if (spreadArguments) {
            builder.beginPrepareSuperSendVector();
            builder.emitLoadConstant(send.message());
            emitCurrentActivation(builder);
            builder.emitLoadLocal(suppliedVector);
            builder.endPrepareSuperSendVector();
        } else {
            builder.beginPrepareSuperSendArguments();
            builder.emitLoadConstant(send.message());
            emitCurrentActivation(builder);
            if (stageInputs) {
                for (BytecodeLocal argumentValue : argumentValues) {
                    builder.emitLoadLocal(argumentValue);
                }
            } else {
                for (CanonicalExpression argument : send.arguments()) {
                    emitExpression(builder, argument);
                }
            }
            builder.endPrepareSuperSendArguments();
        }
        builder.endStoreLocal();

        emitPreparedInvocation(
                builder,
                result,
                preparedCall,
                childResult,
                resumeValue);
        builder.endBlock();
        builder.endTag(StandardTags.CallTag.class);
        builder.endSourceSection();
    }

    private void emitComposedDefaultCall(
            ProtosSemanticBytecodeRootNodeGen.Builder builder,
            CanonicalCall call,
            BytecodeLocal result,
            BytecodeLocal preparedCall,
            BytecodeLocal childResult,
            BytecodeLocal resumeValue) {
        requireDefaultScratch(
                result,
                preparedCall,
                childResult,
                resumeValue);
        CanonicalExpression receiver = call.receiver();
        boolean stageReceiver = requiresComposedInvocation(receiver);
        boolean stageArguments =
                hasComposedArgument(call.arguments());
        boolean spreadArguments =
                hasSpreadArgument(call.arguments());
        boolean stageInputs =
                stageReceiver
                        || stageArguments
                        || spreadArguments;
        BytecodeLocal receiverValue = null;
        BytecodeLocal suppliedVector = null;
        java.util.List<BytecodeLocal> argumentValues =
                java.util.List.of();

        builder.beginSourceSection(
                call.span().startOffset(),
                call.span().length());
        builder.beginTag(StandardTags.CallTag.class);
        builder.beginBlock();

        if (stageInputs) {
            receiverValue =
                    builder.createLocal(
                            "defaultCallReceiver",
                            null);
            if (stageReceiver) {
                emitDefaultExpressionToLocal(
                        builder,
                        receiver,
                        receiverValue,
                        preparedCall,
                        childResult,
                        resumeValue);
            } else {
                builder.beginStoreLocal(receiverValue);
                emitExpression(builder, receiver);
                builder.endStoreLocal();
            }

            if (spreadArguments) {
                suppliedVector =
                        builder.createLocal(
                                "defaultSuppliedArgumentVector",
                                null);
                emitDefaultSpreadArgumentVector(
                        builder,
                        call.arguments(),
                        suppliedVector,
                        preparedCall,
                        childResult,
                        resumeValue);
            } else {
                argumentValues =
                        new java.util.ArrayList<>(
                                call.arguments().size());
                for (CanonicalExpression argument :
                        call.arguments()) {
                    BytecodeLocal argumentValue =
                            builder.createLocal(
                                    "defaultCallArgument",
                                    null);
                    emitDefaultExpressionToLocal(
                            builder,
                            argument,
                            argumentValue,
                            preparedCall,
                            childResult,
                            resumeValue);
                    argumentValues.add(argumentValue);
                }
            }
        }

        builder.beginStoreLocal(preparedCall);
        if (spreadArguments) {
            builder.beginPrepareClosureCallVector();
            builder.emitLoadLocal(receiverValue);
            emitCurrentActivation(builder);
            builder.emitLoadLocal(suppliedVector);
            builder.endPrepareClosureCallVector();
        } else {
            builder.beginPrepareDefaultClosureCallArguments();
            if (stageInputs) {
                builder.emitLoadLocal(receiverValue);
            } else {
                emitExpression(builder, receiver);
            }
            emitCurrentActivation(builder);
            if (stageInputs) {
                for (BytecodeLocal argumentValue :
                        argumentValues) {
                    builder.emitLoadLocal(argumentValue);
                }
            } else {
                for (CanonicalExpression argument :
                        call.arguments()) {
                    emitExpression(builder, argument);
                }
            }
            builder.endPrepareDefaultClosureCallArguments();
        }
        builder.endStoreLocal();
        emitComposedPreparedDefaultInvocation(
                builder,
                result,
                preparedCall,
                childResult,
                resumeValue);
        builder.endBlock();
        builder.endTag(StandardTags.CallTag.class);
        builder.endSourceSection();
    }

    private void emitComposedDefaultSend(
            ProtosSemanticBytecodeRootNodeGen.Builder builder,
            CanonicalSend send,
            BytecodeLocal result,
            BytecodeLocal preparedCall,
            BytecodeLocal childResult,
            BytecodeLocal resumeValue) {
        requireDefaultScratch(
                result,
                preparedCall,
                childResult,
                resumeValue);
        CanonicalExpression receiver = send.receiver();
        boolean stageReceiver = requiresComposedInvocation(receiver);
        boolean stageArguments =
                hasComposedArgument(send.arguments());
        boolean spreadArguments =
                hasSpreadArgument(send.arguments());
        boolean stageInputs =
                stageReceiver
                        || stageArguments
                        || spreadArguments;
        BytecodeLocal receiverValue = null;
        BytecodeLocal suppliedVector = null;
        java.util.List<BytecodeLocal> argumentValues =
                java.util.List.of();

        builder.beginSourceSection(
                send.span().startOffset(),
                send.span().length());
        builder.beginTag(StandardTags.CallTag.class);
        builder.beginBlock();

        if (stageInputs) {
            receiverValue =
                    builder.createLocal(
                            "defaultSendReceiver",
                            null);
            if (stageReceiver) {
                emitDefaultExpressionToLocal(
                        builder,
                        receiver,
                        receiverValue,
                        preparedCall,
                        childResult,
                        resumeValue);
            } else {
                builder.beginStoreLocal(receiverValue);
                emitExpression(builder, receiver);
                builder.endStoreLocal();
            }

            if (spreadArguments) {
                suppliedVector =
                        builder.createLocal(
                                "defaultSuppliedArgumentVector",
                                null);
                emitDefaultSpreadArgumentVector(
                        builder,
                        send.arguments(),
                        suppliedVector,
                        preparedCall,
                        childResult,
                        resumeValue);
            } else {
                argumentValues =
                        new java.util.ArrayList<>(
                                send.arguments().size());
                for (CanonicalExpression argument :
                        send.arguments()) {
                    BytecodeLocal argumentValue =
                            builder.createLocal(
                                    "defaultSendArgument",
                                    null);
                    emitDefaultExpressionToLocal(
                            builder,
                            argument,
                            argumentValue,
                            preparedCall,
                            childResult,
                            resumeValue);
                    argumentValues.add(argumentValue);
                }
            }
        }

        builder.beginStoreLocal(preparedCall);
        if (spreadArguments) {
            builder.beginPrepareSendVector();
            builder.emitLoadLocal(receiverValue);
            builder.emitLoadConstant(send.message());
            emitCurrentActivation(builder);
            builder.emitLoadLocal(suppliedVector);
            builder.endPrepareSendVector();
        } else {
            builder.beginPrepareSendArguments();
            if (stageInputs) {
                builder.emitLoadLocal(receiverValue);
            } else {
                emitExpression(builder, receiver);
            }
            builder.emitLoadConstant(send.message());
            emitCurrentActivation(builder);
            if (stageInputs) {
                for (BytecodeLocal argumentValue :
                        argumentValues) {
                    builder.emitLoadLocal(argumentValue);
                }
            } else {
                for (CanonicalExpression argument :
                        send.arguments()) {
                    emitExpression(builder, argument);
                }
            }
            builder.endPrepareSendArguments();
        }
        builder.endStoreLocal();
        emitComposedPreparedDefaultInvocation(
                builder,
                result,
                preparedCall,
                childResult,
                resumeValue);
        builder.endBlock();
        builder.endTag(StandardTags.CallTag.class);
        builder.endSourceSection();
    }

    private void emitComposedPreparedDefaultInvocation(
            ProtosSemanticBytecodeRootNodeGen.Builder builder,
            BytecodeLocal result,
            BytecodeLocal preparedCall,
            BytecodeLocal childResult,
            BytecodeLocal resumeValue) {
        emitPreparedInvocation(
                builder,
                result,
                preparedCall,
                childResult,
                resumeValue);
    }

    /**
     * PLAT042 Candidate B′ source-root prepared invocation.
     *
     * <p>A structured prepared call (while, each, ensure, Error.handle,
     * match/case, import, structured collection callbacks, ...) enters the
     * untagged structured-dispatch root exactly once for this invocation and
     * composes its C-prime continuation through this semantic root; the
     * structured root then performs every callback call itself. Any other
     * prepared call is invoked locally, exactly as the final ordinary branch of
     * the structured dispatcher does.
     *
     * <p>PLAT043 narrows the structured case: a prepared standard Boolean call
     * (IF_TRUE, IF_FALSE, IF_TRUE_IF_FALSE, AND, OR) is sequenced in this root
     * (see {@link #emitLocalBooleanInvocation}), so a reached callback is
     * entered directly from this root without an intermediate helper root.
     */
    private void emitPreparedInvocation(
            ProtosSemanticBytecodeRootNodeGen.Builder builder,
            BytecodeLocal result,
            BytecodeLocal preparedCall,
            BytecodeLocal childResult,
            BytecodeLocal resumeValue) {
        requireDefaultScratch(
                result,
                preparedCall,
                childResult,
                resumeValue);

        builder.beginIfThenElse();

        builder.beginIsStructuredBooleanCall();
        builder.emitLoadLocal(preparedCall);
        builder.endIsStructuredBooleanCall();

        emitLocalBooleanInvocation(
                builder,
                result,
                preparedCall,
                childResult,
                resumeValue);

        builder.beginBlock();
        emitDispatchedPreparedInvocation(
                builder,
                result,
                preparedCall,
                childResult,
                resumeValue);
        builder.endBlock();

        builder.endIfThenElse();
    }

    /**
     * Invokes a prepared call that is not sequenced in this root: a structured
     * call enters the untagged structured root (PLAT042), any other call is
     * invoked locally.
     */
    private static void emitDispatchedPreparedInvocation(
            ProtosSemanticBytecodeRootNodeGen.Builder builder,
            BytecodeLocal result,
            BytecodeLocal preparedCall,
            BytecodeLocal childResult,
            BytecodeLocal resumeValue) {
        builder.beginIfThenElse();

        builder.beginRequiresStructuredDispatch();
        builder.emitLoadLocal(preparedCall);
        builder.endRequiresStructuredDispatch();

        builder.beginBlock();
        emitNestedStructuredInvocation(builder, result, preparedCall, childResult, resumeValue);
        builder.endBlock();

        builder.beginBlock();
        emitOrdinaryPreparedInvocation(builder, result, preparedCall, childResult, resumeValue);
        builder.endBlock();

        builder.endIfThenElse();
    }

    private static void emitNestedStructuredInvocation(
            ProtosSemanticBytecodeRootNodeGen.Builder builder,
            BytecodeLocal result,
            BytecodeLocal preparedCall,
            BytecodeLocal childResult,
            BytecodeLocal resumeValue) {
        builder.beginStoreLocal(childResult);
        builder.beginEnterNestedStructuredDispatch();
        builder.emitLoadLocal(preparedCall);
        builder.endEnterNestedStructuredDispatch();
        builder.endStoreLocal();
        emitContinuationComposition(
                builder,
                preparedCall,
                childResult,
                resumeValue);
        builder.beginStoreLocal(result);
        builder.emitLoadLocal(childResult);
        builder.endStoreLocal();
    }

    private static void emitOrdinaryPreparedInvocation(
            ProtosSemanticBytecodeRootNodeGen.Builder builder,
            BytecodeLocal result,
            BytecodeLocal preparedCall,
            BytecodeLocal childResult,
            BytecodeLocal resumeValue) {
        builder.beginStoreLocal(childResult);
        builder.beginEnterClosureCall();
        builder.emitLoadLocal(preparedCall);
        builder.endEnterClosureCall();
        builder.endStoreLocal();
        emitContinuationComposition(
                builder,
                preparedCall,
                childResult,
                resumeValue);
        builder.beginStoreLocal(result);
        builder.beginFinishClosureCall();
        builder.emitLoadLocal(preparedCall);
        builder.emitLoadLocal(childResult);
        builder.endFinishClosureCall();
        builder.endStoreLocal();
    }

    /**
     * PLAT043 standard Boolean orchestration in the semantic source root.
     *
     * <p>Mirrors the Boolean branch of {@code ProtosStructuredDispatchLowerer}
     * against the same {@code PreparedBooleanCall} state machine: the outer
     * prepared call is completed exactly once by the enclosing TryFinally on
     * normal, Error, control-transfer and cancellation exits, an unselected
     * callback is neither validated nor invoked, and the selected callback is
     * prepared through ordinary polymorphic Closure-call preparation. The
     * callback child keeps the helper's scoped shape: a child that itself
     * requires structured dispatch enters the untagged root, and any other
     * child is completed by its own TryFinally. C-prime continuations of the
     * child are composed through this root (PLAT014); no new continuation
     * kind, Task, handler or return home is introduced.
     */
    private static void emitLocalBooleanInvocation(
            ProtosSemanticBytecodeRootNodeGen.Builder builder,
            BytecodeLocal result,
            BytecodeLocal preparedCall,
            BytecodeLocal childResult,
            BytecodeLocal resumeValue) {
        builder.beginBlock();
        BytecodeLocal structuredBoolean =
                builder.createLocal("structuredBoolean", null);
        BytecodeLocal structuredBooleanChild =
                builder.createLocal("structuredBooleanChild", null);

        builder.beginTryFinally(
                () -> {
                    builder.beginCompleteClosureCall();
                    builder.emitLoadLocal(preparedCall);
                    builder.endCompleteClosureCall();
                });
        builder.beginBlock();

        builder.beginStoreLocal(structuredBoolean);
        builder.beginPrepareStructuredBooleanCall();
        builder.emitLoadLocal(preparedCall);
        builder.endPrepareStructuredBooleanCall();
        builder.endStoreLocal();

        builder.beginIfThenElse();
        builder.beginStructuredBooleanHasCallback();
        builder.emitLoadLocal(structuredBoolean);
        builder.endStructuredBooleanHasCallback();

        builder.beginBlock();
        builder.beginStoreLocal(structuredBooleanChild);
        builder.beginPrepareStructuredBooleanCallbackCall();
        builder.emitLoadLocal(structuredBoolean);
        builder.endPrepareStructuredBooleanCallbackCall();
        builder.endStoreLocal();

        builder.beginIfThenElse();
        builder.beginRequiresStructuredDispatch();
        builder.emitLoadLocal(structuredBooleanChild);
        builder.endRequiresStructuredDispatch();

        builder.beginBlock();
        emitNestedStructuredInvocation(
                builder,
                childResult,
                structuredBooleanChild,
                childResult,
                resumeValue);
        builder.endBlock();

        builder.beginBlock();
        builder.beginTryFinally(
                () -> {
                    builder.beginCompleteClosureCall();
                    builder.emitLoadLocal(structuredBooleanChild);
                    builder.endCompleteClosureCall();
                });
        builder.beginBlock();
        emitOrdinaryPreparedInvocation(
                builder,
                childResult,
                structuredBooleanChild,
                childResult,
                resumeValue);
        builder.endBlock();
        builder.endTryFinally();
        builder.endBlock();

        builder.endIfThenElse();

        builder.beginStoreLocal(result);
        builder.beginFinishStructuredBooleanCallback();
        builder.emitLoadLocal(structuredBoolean);
        builder.emitLoadLocal(childResult);
        builder.endFinishStructuredBooleanCallback();
        builder.endStoreLocal();
        builder.endBlock();

        builder.beginBlock();
        builder.beginStoreLocal(result);
        builder.beginStructuredBooleanImmediateResult();
        builder.emitLoadLocal(structuredBoolean);
        builder.endStructuredBooleanImmediateResult();
        builder.endStoreLocal();
        builder.endBlock();

        builder.endIfThenElse();
        builder.endBlock();
        builder.endTryFinally();
        builder.endBlock();
    }

    /**
     * Re-yields every nested C-prime continuation of {@code childResult}
     * through this root and resumes it with the value this root is resumed
     * with, until the child completes (PLAT014; no replay, no new
     * continuation kind).
     */
    private static void emitContinuationComposition(
            ProtosSemanticBytecodeRootNodeGen.Builder builder,
            BytecodeLocal preparedCall,
            BytecodeLocal childResult,
            BytecodeLocal resumeValue) {
        builder.beginWhile();
        builder.beginIsContinuation();
        builder.emitLoadLocal(childResult);
        builder.endIsContinuation();

        builder.beginBlock();
        builder.beginStoreLocal(resumeValue);
        builder.beginYield();
        builder.emitLoadLocal(childResult);
        builder.endYield();
        builder.endStoreLocal();

        builder.beginStoreLocal(childResult);
        builder.beginResumeContinuation();
        builder.emitLoadLocal(preparedCall);
        builder.emitLoadLocal(childResult);
        builder.emitLoadLocal(resumeValue);
        builder.endResumeContinuation();
        builder.endStoreLocal();
        builder.endBlock();

        builder.endWhile();
    }

    static void requireDefaultScratch(
            BytecodeLocal result,
            BytecodeLocal preparedCall,
            BytecodeLocal childResult,
            BytecodeLocal resumeValue) {
        if (result == null || preparedCall == null || childResult == null || resumeValue == null) {
            throw new AssertionError("Bytecode composed-default scratch locals were not allocated");
        }
    }

    private void emitBindSuppliedClosureParameter(
            ProtosSemanticBytecodeRootNodeGen.Builder builder,
            CanonicalParameter parameter,
            int positionalIndex) {
        builder.beginBindClosureParameter();
        emitCurrentActivation(builder);
        builder.emitLoadConstant(parameter.name());
        builder.beginLoadClosureArgument();
        emitCurrentActivation(builder);
        builder.emitLoadConstant(positionalIndex);
        builder.endLoadClosureArgument();
        builder.endBindClosureParameter();
    }

    private void validateSupported(CanonicalSequence sequence) {
        for (CanonicalExpression expression : sequence.expressions()) {
            validateSupportedExpression(expression);
        }
    }

    private void validateSupportedExpression(
            CanonicalExpression expression) {
        validateSpan(expression.span());
        if (expression instanceof CanonicalLiteral
                || expression instanceof CanonicalLookup
                || expression instanceof CanonicalIntrinsic) {
            return;
        }
        if (expression instanceof CanonicalClosure closure) {
            /*
             * PERF013 Slice A: structural-support validation only. This must
             * NOT construct the Closure's execution plan/root here: that
             * happens later, at real emission time in emitExpression, so the
             * Closure's root can be nested inside whichever enclosing root's
             * builder is actually open then (shared BytecodeRootNodes
             * grouping). Mirrors exactly the checks full construction would
             * have performed (validateSupportedDefaults + a supported-shape
             * walk of the body), so an unsupported nested construct is still
             * reported before any builder is touched.
             */
            validateSupportedDefaults(closure);
            validateSpan(closure.body().span());
            validateSupported(closure.body());
            return;
        }
        if (expression instanceof CanonicalObject object) {
            /*
             * Structural-support validation only: the body is emitted inline
             * later (emitInlineObjectConstruction), while the enclosing
             * root's own builder is open.
             */
            object.parent().ifPresent(this::validateSupportedExpression);
            registerObjectBodyReservedNames(object);
            validateSupported(object.body());
            return;
        }
        if (expression instanceof CanonicalMapConstruction map) {
            validateSupportedExpression(map.factory());
            for (CanonicalMapConstruction.Entry entry : map.entries()) {
                validateSupportedExpression(entry.key());
                validateSupportedExpression(entry.value());
            }
            return;
        }
        if (expression instanceof CanonicalCompose compose) {
            if (!bytecodeComposeReservedNames.containsKey(compose)) {
                throw new UnsupportedOperationException(
                        "CanonicalCompose is valid only in its registered Object body");
            }
            validateSupportedExpression(compose.object());
            return;
        }
        if (expression instanceof CanonicalMember member) {
            validateSupportedExpression(member.receiver());
            return;
        }
        if (expression instanceof CanonicalIdentity identity) {
            validateSupportedExpression(identity.left());
            validateSupportedExpression(identity.right());
            return;
        }
        if (expression instanceof CanonicalNotIdentity identity) {
            validateSupportedExpression(identity.left());
            validateSupportedExpression(identity.right());
            return;
        }
        if (expression instanceof CanonicalDerivedInequality inequality) {
            validateSupportedExpression(inequality.left());
            validateSupportedExpression(inequality.right());
            return;
        }
        if (expression instanceof CanonicalCreate create) {
            create.target().ifPresent(this::validateSupportedExpression);
            validateSupportedExpression(create.value());
            return;
        }
        if (expression instanceof CanonicalMultipleCreate create) {
            validateSupportedExpression(create.value());
            return;
        }
        if (expression instanceof CanonicalAssign assign) {
            assign.target().ifPresent(this::validateSupportedExpression);
            validateSupportedExpression(assign.value());
            return;
        }
        if (expression instanceof CanonicalIndexedAssign indexedAssign) {
            validateSupportedExpression(indexedAssign.receiver());
            validateSupportedExpression(indexedAssign.index());
            validateSupportedExpression(indexedAssign.value());
            return;
        }
        if (expression instanceof CanonicalCall call) {
            validateSupportedExpression(call.receiver());
            for (CanonicalExpression argument : call.arguments()) {
                if (argument instanceof CanonicalSpread spread) {
                    validateSupportedExpression(spread.expression());
                } else {
                    validateSupportedExpression(argument);
                }
            }
            return;
        }
        if (expression instanceof CanonicalSend send) {
            validateSupportedExpression(send.receiver());
            for (CanonicalExpression argument : send.arguments()) {
                if (argument instanceof CanonicalSpread spread) {
                    validateSupportedExpression(spread.expression());
                } else {
                    validateSupportedExpression(argument);
                }
            }
            return;
        }
        if (expression instanceof CanonicalSuperSend send) {
            for (CanonicalExpression argument : send.arguments()) {
                if (argument instanceof CanonicalSpread spread) {
                    validateSupportedExpression(spread.expression());
                } else {
                    validateSupportedExpression(argument);
                }
            }
            return;
        }
        if (expression instanceof CanonicalReturn returnExpression) {
            validateSupportedExpression(returnExpression.value());
            return;
        }
        throw new UnsupportedOperationException(
                "PERF006-B2B Bytecode lowerer does not yet support "
                        + expression.getClass().getSimpleName());
    }

    private void validateSpan(SourceSpan span) {
        Objects.requireNonNull(span, "span");
        if (span.endOffset() > source.getLength()) {
            throw new IllegalArgumentException(
                    "source span "
                            + span
                            + " exceeds owning Truffle Source length "
                            + source.getLength()
                            + " for "
                            + source.getName());
        }
    }

    private void emitExpression(
            ProtosSemanticBytecodeRootNodeGen.Builder builder,
            CanonicalExpression expression) {
        if (expression instanceof CanonicalLiteral literal) {
            builder.emitLoadConstant(materialize(literal));
            return;
        }
        if (expression instanceof CanonicalClosure closure) {
            /*
             * PERF013 Slice A/A2: resolve/lower the Closure's own root
             * (nesting its beginRoot()/endRoot() in this same open builder,
             * or reusing the cell already registered on a BytecodeRootNodes
             * reparse) before opening the MaterializeClosure operation,
             * rather than nesting root construction inside that operation's
             * own argument evaluation.
             */
            ProtosClosureExecutionPlanCell cell = bytecodeClosurePlan(builder, closure);
            builder.beginMaterializeClosure();
            emitCurrentActivation(builder);
            builder.emitLoadConstant(closure);
            builder.emitLoadConstant(cell);
            builder.endMaterializeClosure();
            return;
        }
        if (expression instanceof CanonicalLookup lookup) {
            emitLookup(builder, lookup);
            return;
        }
        if (expression instanceof CanonicalIntrinsic intrinsic) {
            builder.beginLoadIntrinsic();
            emitCurrentActivation(builder);
            builder.emitLoadConstant(intrinsic.kind());
            builder.endLoadIntrinsic();
            return;
        }
        if (expression instanceof CanonicalMember member) {
            builder.beginReadMember();
            emitCurrentActivation(builder);
            emitExpression(builder, member.receiver());
            builder.emitLoadConstant(member.name());
            builder.endReadMember();
            return;
        }
        if (expression instanceof CanonicalIdentity identity) {
            builder.beginIdentity();
            emitExpression(builder, identity.left());
            emitExpression(builder, identity.right());
            builder.endIdentity();
            return;
        }
        if (expression instanceof CanonicalNotIdentity identity) {
            builder.beginNotIdentity();
            emitExpression(builder, identity.left());
            emitExpression(builder, identity.right());
            builder.endNotIdentity();
            return;
        }
        throw new AssertionError(
                "validated value-shaped Bytecode expression became unsupported: "
                        + expression.getClass().getSimpleName());
    }

    /**
     * PLAT036 Candidate D, Slice 3 key direct/fast path: a reference that is
     * statically {@link CanonicalBindingResolution.Resolved} in the exact
     * genuine execution-context scope currently being lowered compiles
     * straight to a {@code ReadFrameLocal} of its own stable {@link
     * com.oracle.truffle.api.bytecode.LocalAccessor}, bypassing the generic {@code Lookup} operation and exact name-based
     * lexical fallback entirely. This uses the
     * same accessor-based mechanism the installed frame-backed authority uses
     * to write this local (see {@code ProtosFrameLexicalBindingAuthority}),
     * deliberately never the raw generated {@code LoadLocal} instruction: a
     * local written only through the dynamic accessor API does not
     * participate in the frame-slot-kind speculation the DSL's own literal
     * {@code StoreLocal}/{@code LoadLocal} pair relies on. Presence is
     * guaranteed by the static proof itself, so no runtime presence check is
     * needed here. {@code Candidate} and {@code Dynamic} resolutions, and any
     * {@code Resolved} binding owned by a different scope (an {@code
     * OBJECT_BODY} or a different root entirely), always fall through to the
     * unchanged generic path.
     */
    private void emitLookup(
            ProtosSemanticBytecodeRootNodeGen.Builder builder,
            CanonicalLookup lookup) {
        if (currentRootAnalysis != null) {
            java.util.Optional<CanonicalBindingResolution> resolution =
                    currentRootAnalysis.resolutionOf(lookup);
            if (resolution.isPresent()
                    && resolution.orElseThrow() instanceof CanonicalBindingResolution.Resolved resolved
                    && resolved.identity().owner() == currentRootTopScope) {
                BytecodeLocal local =
                        currentRootFrameLocals.get(resolved.identity().name());
                if (local != null) {
                    builder.beginReadFrameLocal(local);
                    emitCurrentActivation(builder);
                    builder.emitLoadConstant(resolved.identity().name());
                    builder.endReadFrameLocal();
                    return;
                }
            }

            if (resolution.isPresent()
                    && resolution.orElseThrow()
                            instanceof CanonicalBindingResolution.CapturedResolved captured) {
                BytecodeLocal ownerLocal = capturedOwnerBytecodeLocal(captured);
                if (ownerLocal != null) {
                    builder.beginReadCapturedMaterializedLocal(ownerLocal);
                    emitCurrentActivation(builder);
                    builder.emitLoadConstant(captured.identity().name());
                    builder.emitLoadConstant(captured.lexicalDepth());
                    builder.endReadCapturedMaterializedLocal();
                    return;
                }

                builder.beginReadCapturedFrameLocal();
                emitCurrentActivation(builder);
                builder.emitLoadConstant(captured.identity().name());
                builder.emitLoadConstant(captured.lexicalDepth());
                builder.emitLoadConstant(frameBackedOrdinal(captured.identity()));
                builder.endReadCapturedFrameLocal();
                return;
            }
        }
        builder.beginLookup();
        emitCurrentActivation(builder);
        builder.emitLoadConstant(lookup.name());
        builder.endLookup();
    }

    /**
     * PERF013 Slice B1 (reads) / Slice B2 (writes): the owner {@link
     * BytecodeLocal} for a proven captured access, when and only when {@code
     * captured}'s owner scope is both structurally reachable from the exact
     * root currently being lowered ({@link #capturedOwnerMatchesCurrentRoot})
     * and already registered in {@link #frameLocalsByScope} — meaning owner
     * and this Closure share the same physical {@code BytecodeRootNodes}
     * group. Returns {@code null} (never partial/best-effort metadata)
     * whenever either condition fails, so the caller falls back to the exact
     * existing {@code ReadCapturedFrameLocal}/{@code
     * ResolveCapturedWritableLexicalTarget} runtime-authority path unchanged.
     * This is expected, not an error, for an owner root reached through an
     * isolated Closure rebuild/rematerialization that does not include its
     * lexical owner in the same lowering group (PERF013 Slice C).
     */
    private BytecodeLocal capturedOwnerBytecodeLocal(
            CanonicalBindingResolution.CapturedResolved captured) {
        if (!capturedOwnerMatchesCurrentRoot(captured)) {
            return null;
        }
        java.util.Map<String, BytecodeLocal> ownerFrameLocals =
                frameLocalsByScope.get(captured.identity().owner());
        if (ownerFrameLocals == null) {
            return null;
        }
        return ownerFrameLocals.get(captured.identity().name());
    }

    private java.util.Optional<CanonicalBindingResolution.CapturedResolved>
            capturedResolvedAssignment(CanonicalAssign assign) {
        if (assign.target().isPresent() || currentRootAnalysis == null) {
            return java.util.Optional.empty();
        }

        java.util.Optional<CanonicalBindingResolution> resolution =
                currentRootAnalysis.resolutionOf(assign);
        if (resolution.isPresent()
                && resolution.orElseThrow()
                        instanceof CanonicalBindingResolution.CapturedResolved captured) {
            return java.util.Optional.of(captured);
        }
        return java.util.Optional.empty();
    }

    private boolean capturedOwnerMatchesCurrentRoot(
            CanonicalBindingResolution.CapturedResolved captured) {
        if (currentRootTopScope == null) {
            return false;
        }

        CanonicalLexicalScope scope = currentRootTopScope;
        for (int depth = 0; depth < captured.lexicalDepth(); depth++) {
            scope = scope.outwardScope();
            if (scope == null) {
                return false;
            }
        }
        return scope == captured.identity().owner();
    }

    private static int frameBackedOrdinal(
            CanonicalBindingIdentity identity) {
        int ordinal = 0;
        for (String declaredName : identity.owner().declaredNames()) {
            if (declaredName.equals(identity.name())) {
                return ordinal;
            }
            ordinal++;
        }
        throw new AssertionError(
                "binding identity owner no longer declares its own name: "
                        + identity.name());
    }

    private void emitComposedSend(
            ProtosSemanticBytecodeRootNodeGen.Builder builder,
            CanonicalSend send,
            BytecodeLocal result,
            BytecodeLocal preparedCall,
            BytecodeLocal childResult,
            BytecodeLocal resumeValue) {
        if (result == null
                || preparedCall == null
                || childResult == null
                || resumeValue == null) {
            throw new AssertionError(
                    "Bytecode send result/scratch locals were not allocated");
        }

        CanonicalExpression receiver = send.receiver();
        boolean stageReceiver = requiresComposedInvocation(receiver);
        boolean stageArguments =
                hasComposedArgument(send.arguments());
        boolean spreadArguments =
                hasSpreadArgument(send.arguments());
        boolean stageInputs =
                stageReceiver
                        || stageArguments
                        || spreadArguments;
        BytecodeLocal receiverValue = null;
        BytecodeLocal suppliedVector = null;
        java.util.List<BytecodeLocal> argumentValues =
                java.util.List.of();

        builder.beginTag(StandardTags.CallTag.class);
        builder.beginBlock();

        if (stageInputs) {
            receiverValue =
                    builder.createLocal(
                            "sendReceiver",
                            null);
            if (stageReceiver) {
                emitBodyExpressionToLocal(
                        builder,
                        receiver,
                        receiverValue,
                        preparedCall,
                        childResult,
                        resumeValue);
            } else {
                builder.beginStoreLocal(receiverValue);
                emitExpression(builder, receiver);
                builder.endStoreLocal();
            }

            if (spreadArguments) {
                suppliedVector =
                        builder.createLocal(
                                "suppliedArgumentVector",
                                null);
                emitBodySpreadArgumentVector(
                        builder,
                        send.arguments(),
                        suppliedVector,
                        preparedCall,
                        childResult,
                        resumeValue);
            } else {
                argumentValues =
                        new java.util.ArrayList<>(
                                send.arguments().size());
                for (CanonicalExpression argument :
                        send.arguments()) {
                    BytecodeLocal argumentValue =
                            builder.createLocal(
                                    "sendArgument",
                                    null);
                    emitBodyExpressionToLocal(
                            builder,
                            argument,
                            argumentValue,
                            preparedCall,
                            childResult,
                            resumeValue);
                    argumentValues.add(argumentValue);
                }
            }
        }

        builder.beginStoreLocal(preparedCall);
        if (spreadArguments) {
            builder.beginPrepareSendVector();
            builder.emitLoadLocal(receiverValue);
            builder.emitLoadConstant(send.message());
            emitCurrentActivation(builder);
            builder.emitLoadLocal(suppliedVector);
            builder.endPrepareSendVector();
        } else {
            builder.beginPrepareSendArguments();
            if (stageInputs) {
                builder.emitLoadLocal(receiverValue);
            } else {
                emitExpression(builder, receiver);
            }
            builder.emitLoadConstant(send.message());
            emitCurrentActivation(builder);
            if (stageInputs) {
                for (BytecodeLocal argumentValue :
                        argumentValues) {
                    builder.emitLoadLocal(argumentValue);
                }
            } else {
                for (CanonicalExpression argument :
                        send.arguments()) {
                    emitExpression(builder, argument);
                }
            }
            builder.endPrepareSendArguments();
        }
        builder.endStoreLocal();


        emitPreparedInvocation(
                builder,
                result,
                preparedCall,
                childResult,
                resumeValue);
        builder.endBlock();
        builder.endTag(StandardTags.CallTag.class);
    }

    private void emitComposedCall(
            ProtosSemanticBytecodeRootNodeGen.Builder builder,
            CanonicalCall call,
            BytecodeLocal result,
            BytecodeLocal preparedCall,
            BytecodeLocal childResult,
            BytecodeLocal resumeValue) {
        if (result == null
                || preparedCall == null
                || childResult == null
                || resumeValue == null) {
            throw new AssertionError(
                    "Bytecode call result/scratch locals were not allocated");
        }

        CanonicalExpression receiver = call.receiver();
        boolean stageReceiver = requiresComposedInvocation(receiver);
        boolean stageArguments = hasComposedArgument(call.arguments());
        boolean spreadArguments = hasSpreadArgument(call.arguments());
        boolean stageInputs =
                stageReceiver
                        || stageArguments
                        || spreadArguments;
        BytecodeLocal receiverValue = null;
        BytecodeLocal suppliedVector = null;
        java.util.List<BytecodeLocal> argumentValues =
                java.util.List.of();

        builder.beginTag(StandardTags.CallTag.class);
        builder.beginBlock();

        if (stageInputs) {
            receiverValue =
                    builder.createLocal(
                            "callReceiver",
                            null);
            if (stageReceiver) {
                emitBodyExpressionToLocal(
                        builder,
                        receiver,
                        receiverValue,
                        preparedCall,
                        childResult,
                        resumeValue);
            } else {
                builder.beginStoreLocal(receiverValue);
                emitExpression(builder, receiver);
                builder.endStoreLocal();
            }

            if (spreadArguments) {
                suppliedVector =
                        builder.createLocal(
                                "suppliedArgumentVector",
                                null);
                emitBodySpreadArgumentVector(
                        builder,
                        call.arguments(),
                        suppliedVector,
                        preparedCall,
                        childResult,
                        resumeValue);
            } else {
                argumentValues =
                        new java.util.ArrayList<>(
                                call.arguments().size());
                for (CanonicalExpression argument :
                        call.arguments()) {
                    BytecodeLocal argumentValue =
                            builder.createLocal(
                                    "callArgument",
                                    null);
                    emitBodyExpressionToLocal(
                            builder,
                            argument,
                            argumentValue,
                            preparedCall,
                            childResult,
                            resumeValue);
                    argumentValues.add(argumentValue);
                }
            }
        }

        builder.beginStoreLocal(preparedCall);
        if (spreadArguments) {
            builder.beginPrepareClosureCallVector();
            builder.emitLoadLocal(receiverValue);
            emitCurrentActivation(builder);
            builder.emitLoadLocal(suppliedVector);
            builder.endPrepareClosureCallVector();
        } else if (call.arguments().isEmpty()) {
            builder.beginPrepareClosureCall();
            if (stageInputs) {
                builder.emitLoadLocal(receiverValue);
            } else {
                emitExpression(builder, receiver);
            }
            emitCurrentActivation(builder);
            builder.endPrepareClosureCall();
        } else {
            builder.beginPrepareClosureCallArguments();
            if (stageInputs) {
                builder.emitLoadLocal(receiverValue);
            } else {
                emitExpression(builder, receiver);
            }
            emitCurrentActivation(builder);
            if (stageInputs) {
                for (BytecodeLocal argumentValue :
                        argumentValues) {
                    builder.emitLoadLocal(argumentValue);
                }
            } else {
                for (CanonicalExpression argument :
                        call.arguments()) {
                    emitExpression(builder, argument);
                }
            }
            builder.endPrepareClosureCallArguments();
        }
        builder.endStoreLocal();


        emitPreparedInvocation(
                builder,
                result,
                preparedCall,
                childResult,
                resumeValue);
        builder.endBlock();
        builder.endTag(StandardTags.CallTag.class);
    }

    private static Object materialize(CanonicalLiteral literal) {
        return switch (literal.kind()) {
            case NUMBER -> ProtosNumberLiteral.materialize(literal.value());
            case STRING -> new ProtosStringValue(literal.value());
            case TRUE -> ProtosBooleanValue.TRUE;
            case FALSE -> ProtosBooleanValue.FALSE;
            case NULL -> ProtosNullValue.INSTANCE;
        };
    }
}
