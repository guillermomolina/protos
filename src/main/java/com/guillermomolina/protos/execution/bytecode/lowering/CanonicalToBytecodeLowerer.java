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
import com.guillermomolina.protos.runtime.numeric.ProtosNumberLiteral;
import com.guillermomolina.protos.runtime.ProtosNumericValueSupport;
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
import com.guillermomolina.protos.semantic.ast.CanonicalNamedArgument;
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
import com.oracle.truffle.api.bytecode.StackValue;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.strings.TruffleString;
import java.math.BigInteger;
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
 * produced here is a real tagged {@link ProtosBytecodeRootNode} (I111-A
 * explicitly emits its RootTag, the guest semantic root identity, when each
 * physical semantic root is created, and I111-B marks it as a semantic
 * activation root, which no auxiliary root of the same interpreter is) and
 * lexically nested Closure roots share that interpreter's {@code
 * BytecodeRootNodes} group. Structured prepared invocations are not lowered
 * here; they enter the untagged {@link ProtosStructuredDispatchLowerer} root
 * once per invocation.</p>
 */
final class CanonicalToBytecodeLowerer {
    /**
     * Name of the Bytecode local holding the {@link
     * Object} carrier of a retired PLAT044 B′ inline
     * literal callback region: the carrier of that invocation's one fresh
     * semantic activation, materialized lazily by its first semantic or
     * tooling observer. Tooling reads it back by name ({@link
     * ProtosBytecodeTagTreeNodeExports}).
     */
    static final String INLINE_CALLBACK_CALL_LOCAL = "inlineCallbackCall";

    /**
     * Physical Bytecode-local prefix for statically admitted bindings owned by
     * a PLAT044 B-prime inline callback. Guest binding identity remains the
     * CanonicalLexicalScope/name identity; this name is implementation-only.
     */
    static final String INLINE_CALLBACK_BINDING_LOCAL_PREFIX =
            "$inlineCallbackBinding:";

    /**
     * I096 (PLAT057-B) expansion budget: the largest exact positional arity
     * whose ordinary Closure call site may receive a lexically inlined body.
     * A wider call keeps the unchanged physical call, so a single site never
     * grows the caller root by an unbounded staging and parameter prologue.
     */
    static final int MAX_INLINE_CLOSURE_CALL_ARGUMENTS = 8;

    private final ProtosLanguage language;
    private final Source source;

    /**
     * I101-A: whether the standard root {@code Object} was already published
     * (FROZEN) when this lowerer was created. Publication is irreversible and
     * validates the standard Boolean and {@code call} behaviors, so a
     * {@code true} snapshot holds for every execution of every site lowered
     * here; a {@code false} snapshot keeps generic dispatch. The snapshot is
     * taken once, before any group is created, so a {@code BytecodeRootNodes}
     * reparse after a later publication replays exactly the decisions of the
     * original parse instead of acquiring a different executable path.
     */
    private final boolean standardObjectPublished;

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
            ProtosBytecodeRootNode root,
            ProtosClosureExecutionPlanCell cell) {}

    private final java.util.IdentityHashMap<CanonicalCompose, java.util.List<String>>
            bytecodeComposeReservedNames = new java.util.IdentityHashMap<>();
    /*
     * I106 (PLAT061-B): canonical proofs of this compilation unit, shared by
     * every parse of its retained Bytecode parser and never by another
     * lowerer or Context.
     */
    private final CanonicalCompilationFacts compilationFacts =
            new CanonicalCompilationFacts();
    /*
     * I107: generic prepared invocations (each carrying the continuation
     * composition) emitted so far into the genuine root being lowered. A root
     * planned as non-suspending emits none; an emitted one invalidates the
     * plan before publication instead of publishing an unsound capability.
     * Saved and restored per root.
     */
    private boolean currentRootEmittedContinuationPath;
    /*
     * I112 / PLAT062-T (D204): a terminal Closure invocation emitted into the
     * genuine root being lowered. It emits no Yield, but the invoked call may
     * still suspend and the root then returns its terminal transport, so the
     * root is never published as non-suspending. Independent of the
     * continuation-path flag; saved and restored per root.
     */
    private boolean currentRootEmittedTerminalInvocation;
    /*
     * I091: exact host descriptors of the number literals of this lowering, parsed once per
     * literal node however many lowering decisions consult it.
     */
    private final java.util.IdentityHashMap<CanonicalLiteral, Object> numberLiteralDescriptors =
            new java.util.IdentityHashMap<>();
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
    /*
     * I115: the Bytecode DSL locals of the genuine CLOSURE root being lowered,
     * keyed by declared name: the value of each LOCAL binding, or the cell
     * reference of each CELL binding. Empty for a module ROOT, whose
     * bindings live in its module context (NAME storage).
     */
    private java.util.Map<String, BytecodeLocal> currentRootFrameLocals =
            java.util.Map.of();

    /*
     * I115: the cell references reachable from the root being lowered, keyed
     * by binding identity: the root's own CELL bindings and the free cells
     * its Closure carries, each copied once at entry into a root-scoped
     * Bytecode DSL local. Saved and restored with the other per-root state.
     */
    private java.util.Map<CanonicalBindingIdentity, BytecodeLocal> currentRootCellLocals =
            java.util.Map.of();

    /*
     * PERF034-C: direct BytecodeLocal storage is admitted only for a
     * statically complete, effect-free, straight-line local body.
     * This is per-root lowering state, not runtime/global state.
     */
    private java.util.Set<String> currentRootScalarLocals =
            java.util.Set.of();

    /*
     * I115-H2 EXPERIMENTAL: a proven module-local recursive Closure may
     * use static unary invocation and direct Integer operations. This lane
     * intentionally preserves the I115-R6 semantic exceptions. Other roots
     * retain H1-FIX dispatch and lexical-authority behavior.
     */
    private final java.util.Map<CanonicalClosure, CanonicalBindingIdentity>
            experimentalRecursiveBindings = new java.util.IdentityHashMap<>();
    /*
     * I115-H5 EXPERIMENTAL: the module ROOT bindings whose Closure was
     * lowered as an experimentalRecursiveInteger root, the only callees the
     * static unary ABI of InvokeExperimentalClosureOneTail can enter.
     */
    private final java.util.Set<CanonicalBindingIdentity>
            experimentalDirectUnaryBindings = new java.util.HashSet<>();
    private CanonicalClosure currentExperimentalRootDefinition;

    /*
     * I115-H4-B EXPERIMENTAL: the parse configuration selected only when the
     * internal trace probe is enabled at JVM start; it adds the experimental
     * Call1 instrumentations, which are otherwise absent from the bytecode.
     */
    private static final BytecodeConfig EXPERIMENTAL_TRACE_CONFIG =
            ProtosBytecodeRootNodeGen.newConfigBuilder()
                    .addInstrumentation(ProtosBytecodeRootNode.InstrumentCall.class)
                    .addInstrumentation(ProtosBytecodeRootNode.InstrumentCallReturn.class)
                    .build();
    private boolean currentExperimentalRecursiveIntegerAdmission;
    private boolean currentExperimentalRecursiveIntegerEmission;


    private java.util.Map<CanonicalSend,
            ProtosPrimitiveNumericSendFastPathsBase
                    .FoldedIntegerArithmetic> currentRootArithmeticHints =
            java.util.Map.of();

    private CanonicalSend currentRootTerminalIntegerSend;
    private ProtosPrimitiveNumericSendFastPathsBase
            .FoldedIntegerArithmetic currentRootTerminalIntegerAddition;

    /*
     * I096-B1 (PLAT057-B) per-root lowering state, recomputed from the
     * canonical AST on every parse of the root (including Bytecode reparses)
     * and saved/restored around nested roots. The first maps each admitted
     * direct call site of the genuine root sequence to the candidate body of
     * the binding its receiver resolves to; it is never a proof of the
     * called value. The second records the Closure literals whose
     * materialization this parse of the root has already emitted, so a
     * candidate is used only when its creation precedes the site in this very
     * parse and every reparse emits the same instructions.
     */
    private java.util.Map<CanonicalCall, CanonicalClosure> currentRootBindingCallCandidates =
            java.util.Map.of();
    private java.util.Set<CanonicalClosure> currentRootMaterializedClosures =
            java.util.Set.of();

    /*
     * I096 (PLAT057-B) nested-Closure admission. The first is true while an
     * admitted candidate body is lowered inline in its caller root (reset for
     * every nested root); the second holds the Closure definitions whose own
     * root this parse of the group has already lowered, and is reset at the
     * start of every parse, including Bytecode reparses. Together they let a
     * Closure literal reached again inside an inline body reuse the root and
     * plan its callee's own root already lowered in this parse, so the group
     * never gains a duplicate root and every reparse replays the same roots
     * in the same order (see bytecodeClosurePlan).
     */
    private boolean currentInlineClosureCallBody;
    private java.util.Set<CanonicalClosure> closureRootsLoweredThisParse =
            java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());

    /*
     * I095-B4: lowering-time marker for a physically inline
     * zero-parameter callback whose reads were proven to belong
     * to this root. It is saved/restored around the inline body.
     */
    private boolean currentNormalDirectParentCaptureReads;


    /*
     * I095-B6a: independent, lexically scoped Bytecode locals belonging to
     * one inline callback. Only the existing PERF034/036 straight-line
     * scalar proof permits this lane. No callback invocation/activation is
     * created, and none of these locals can alias a root binding by name.
     */
    // Physical storage is in the parent root; lexical ownership remains the callback.
    // Tooling uses this prefix to project a virtual callback scope on demand.
    /* I115: root-level Closure creation never materializes its creator (see beginClosureCapture). */
    private static final boolean COMPACT_ROOT_CLOSURE_CAPTURE = true;

    /* I115: the name prefix of the root local holding one free cell. */
    static final String FREE_CELL_LOCAL_PREFIX = "i115FreeCell:";
    static final String NORMAL_INLINE_CALLBACK_BINDING_LOCAL_PREFIX =
            "i095CallbackScalar:";
    private java.util.Map<String, BytecodeLocal> currentNormalCallbackScalarLocals =
            java.util.Map.of();
    private CanonicalBindingAnalysis currentNormalCallbackScalarAnalysis;
    private CanonicalLexicalScope currentNormalCallbackScalarScope;
    /*
     * I115-O EXPERIMENTAL: the returned binding of the compact root being
     * emitted whose Integer literal stores load prepared guest constants;
     * null outside such a root.
     */
    private CanonicalBindingIdentity currentExperimentalGuestIntegerBinding;
    /*
     * I115-H1-FIX: the root-scoped prepared-call, child-result and resume
     * locals of the compact root being emitted, shared by the ordinary send
     * of every numeric MISS path; null when the root has no numeric send.
     */
    private ComposedSequenceScratch currentExperimentalCompactScratch;
    /* I115-H7 EXPERIMENTAL: the physical scope of the compact root being emitted. */
    private CanonicalLexicalScope currentExperimentalCompactScope;
    /*
     * I115-H7-R1: the root's terminal expression while a compact root is
     * emitted; a method send there may return a suspension transport.
     */
    private CanonicalExpression currentExperimentalCompactTerminal;
    /*
     * I115-H1-FIX: the dispatching Boolean control sites of the compact root
     * being emitted (empty outside one). During admission only: the
     * bindings pinned to OBJECT, the bindings assigned by dispatching
     * callbacks, and the dispatching sites found by this pass.
     */
    private java.util.Set<CanonicalSend> currentExperimentalDispatchingControls =
            java.util.Set.of();
    private java.util.Set<CanonicalBindingIdentity> currentExperimentalCompactPinned;
    private java.util.Set<CanonicalBindingIdentity> currentExperimentalCompactEscaped;
    private java.util.Set<CanonicalSend> currentExperimentalCompactDispatchingControls;

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
     * PLAT067-B: one proof-optimized lowering for every Context. Whether the
     * host enables a debugger never changes what is emitted; Truffle
     * materializes source, tag and instrumentation information on demand.
     */
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
        this.standardObjectPublished = ProtosObjectValue.rootObject().isFrozen();
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
            return compilationFacts.isolatedClosureAnalysis(activationDefinition);
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
        return compilationFacts.isolatedClosureAnalysis(definition);
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
     * I106 (PLAT061-B): resolves the analysis and scope of one root and
     * proves its canonical facts before that root's builder section opens.
     * Physical facts (layout, carriers, BytecodeLocals) stay with emission.
     */
    private CanonicalCompilationFacts.RootFacts prepareRootFacts(
            CanonicalSequence sequence,
            CanonicalClosure activationDefinition,
            CanonicalBindingAnalysis analysis) {
        return compilationFacts.rootFacts(
                sequence,
                activationDefinition,
                analysis,
                rootScopeFor(analysis, activationDefinition));
    }

    /**
     * I107 (PLAT061-B) immutable emission plan of one root, prepared before
     * its builder section opens ({@code create()} for a group root, {@code
     * beginRoot()} for a nested one). It holds only canonical decisions:
     * the root facts, the authorized terminal fold, the identity of the
     * effective root and the proofs of the canonical body and of the
     * effective root under this lowerer's publication snapshot. Every parse of the group consumes the same decisions.
     */
    record RootEmissionPlan(
            CanonicalCompilationFacts.RootFacts rootFacts,
            CanonicalLiteral foldedTerminal,
            CanonicalCompilationFacts.EffectiveRoot effectiveRoot,
            CanonicalNonSuspensionAnalysis.Result canonicalBody,
            CanonicalNonSuspensionAnalysis.Result plannedEffectiveRoot) {}

    /**
     * The control-admission state of one emission point; at a root's entry
     * it is fully determined by the root's analysis and scope (a CLOSURE
     * root's locals are exactly its declared names, a module ROOT has none;
     * see {@link #emitRootBody}).
     */
    private record ControlSiteContext(
            CanonicalBindingAnalysis analysis,
            CanonicalLexicalScope topScope,
            java.util.Set<String> frameNames,
            boolean hasFrameLayout,
            boolean inlineCallbackFrameNative,
            boolean activationLocalPresent) {

        static ControlSiteContext rootEntry(
                CanonicalBindingAnalysis analysis, CanonicalLexicalScope scope) {
            java.util.Set<String> names =
                    scope.kind() == CanonicalLexicalScope.Kind.CLOSURE
                            ? scope.declaredNames()
                            : java.util.Set.of();
            return new ControlSiteContext(
                    analysis, scope, names, !names.isEmpty(), false, false);
        }
    }

    private ControlSiteContext currentControlSiteContext() {
        return new ControlSiteContext(
                currentRootAnalysis,
                currentRootTopScope,
                currentRootFrameLocals.keySet(),
                !currentRootFrameLocals.isEmpty(),
                false,
                currentActivationLocal != null);
    }

    private RootEmissionPlan prepareRootPlan(
            CanonicalSequence sequence,
            CanonicalClosure activationDefinition,
            CanonicalBindingAnalysis analysis) {
        CanonicalCompilationFacts.RootFacts rootFacts =
                prepareRootFacts(sequence, activationDefinition, analysis);
        CanonicalLexicalScope scope = rootFacts.scope();
        ControlSiteContext entry = ControlSiteContext.rootEntry(analysis, scope);
        CanonicalLiteral foldedTerminal =
                sequence.expressions().isEmpty()
                        ? null
                        : foldedConstantControlResult(
                                sequence.expressions().get(sequence.expressions().size() - 1),
                                entry);
        CanonicalNonSuspensionAnalysis.SendSelection selection =
                (send, ownerAnalysis, ownerScope) ->
                        selectedControlSend(
                                send, ControlSiteContext.rootEntry(ownerAnalysis, ownerScope));
        CanonicalNonSuspensionAnalysis.Result canonicalBody =
                compilationFacts.canonicalBodyNonSuspension(
                        sequence, activationDefinition, analysis, scope);
        Object canonicalRoot = activationDefinition == null ? sequence : activationDefinition;
        CanonicalCompilationFacts.EffectiveRoot effectiveRoot =
                compilationFacts.effectiveRoot(canonicalRoot, analysis, scope, foldedTerminal);
        CanonicalNonSuspensionAnalysis.Result planned =
                compilationFacts.newAnalysis(analysis, scope, activationDefinition, selection)
                        .proveEffectiveRoot(
                                effectiveRoot,
                                activationDefinition,
                                effectiveBody(sequence, foldedTerminal),
                                activationDefinition != null
                                        && compilationFacts.mayObserveReturnHome(
                                                activationDefinition),
                                foldedTerminal == null
                                        ? "planned effective root"
                                        : "planned effective root after constant-control fold");
        compilationFacts.publishNonSuspension(planned);
        return new RootEmissionPlan(
                rootFacts, foldedTerminal, effectiveRoot, canonicalBody, planned);
    }

    /**
     * I107 selection producer: an admitted Boolean-control send whose receiver
     * is a canonical Boolean literal is statically selected by the frozen
     * standard root ({@link #normalBooleanControlSite}); it evaluates its
     * non-Closure arguments and runs exactly its INLINE callback. A dynamic
     * receiver can take the ordinary send on MISS and stays unselected.
     */
    private CanonicalNonSuspensionAnalysis.SelectedSend selectedControlSend(
            CanonicalSend send, ControlSiteContext context) {
        if (!(send.receiver() instanceof CanonicalLiteral receiver)) {
            return null;
        }
        NormalBooleanControlSite site = normalBooleanControlSite(send, context);
        if (site == null) {
            return null;
        }
        boolean value = receiver.kind() == CanonicalLiteral.Kind.TRUE;
        java.util.List<CanonicalExpression> evaluated = new java.util.ArrayList<>();
        for (CanonicalExpression argument : send.arguments()) {
            if (!(argument instanceof CanonicalClosure)) {
                evaluated.add(argument);
            }
        }
        java.util.List<CanonicalClosure> bodies =
                site.path(value) == NormalBooleanControlPath.INLINE
                        ? java.util.List.of((CanonicalClosure)
                                send.arguments().get(site.control().selectedIndex(value)))
                        : java.util.List.of();
        return new CanonicalNonSuspensionAnalysis.SelectedSend(
                java.util.List.copyOf(evaluated),
                bodies,
                java.util.EnumSet.of(
                        CanonicalNonSuspensionAnalysis.Precondition.STANDARD_OBJECT_PUBLISHED));
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
     *
     * <p>I096 (PLAT057-B): inside an inline candidate body, a definition
     * whose root this parse already lowered (inside the callee's own root,
     * which always precedes the inline site) is not lowered again; the
     * inline copy materializes its instances with that same root and plan,
     * exactly as a physical invocation of the callee would. The choice
     * depends only on the order of this parse, so a reparse makes it
     * identically. Outside inline bodies nothing changes.
     *
     * <p>I101-A: a callee root omits the roots of the Boolean
     * callbacks it inlines, folds or never selects, so its inline copy, which
     * keeps the ordinary send, lowers such a callback here for the first
     * time.
     */
    private ProtosClosureExecutionPlanCell bytecodeClosurePlan(
            ProtosBytecodeRootNodeGen.Builder builder,
            CanonicalClosure definition) {
        ProtosClosureExecutionPlanCell existing = bytecodeClosurePlans.get(definition);
        boolean loweredInThisParse = !closureRootsLoweredThisParse.add(definition);
        if (currentInlineClosureCallBody && loweredInThisParse && existing != null) {
            currentRootMaterializedClosures.add(definition);
            return existing;
        }

        // I115-R2: publish the pending cell before emitting its root.
        // Freezing the cell still happens after create() completes.
        ProtosClosureExecutionPlanCell cell = existing;
        if (cell == null) {
            cell = ProtosClosureExecutionPlanCell.pendingGroup();
            bytecodeClosurePlans.put(definition, cell);
        }

        ProtosBytecodeRootNode nestedRoot =
                lowerNestedClosureRoot(builder, definition);
        // I096-B1: after the nested root restored this root's state.
        currentRootMaterializedClosures.add(definition);

        if (existing != null) {
            return existing;
        }
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

    ProtosBytecodeRootNode lowerRoot(CanonicalSequence sequence) {
        return lowerRoot(sequence, null);
    }

    ProtosBytecodeRootNode lowerClosureActivationRoot(
            CanonicalClosure definition) {
        Objects.requireNonNull(definition, "definition");
        validateSupportedDefaults(definition);
        return lowerRoot(definition.body(), definition);
    }

    private ProtosBytecodeRootNode lowerRoot(
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
         * one shared BytecodeRootNodes<ProtosBytecodeRootNode> group.
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
        RootEmissionPlan rootPlan =
                prepareRootPlan(
                        sequence,
                        activationDefinition,
                        bindingAnalysisFor(sequence, activationDefinition));
        // I115-R2: discover the module binding before nested root lowering.
        if (activationDefinition == null) {
            experimentalDirectUnaryBindings.clear();
            CanonicalBindingAnalysis analysis =
                    rootPlan.rootFacts().bindingAnalysis();
            CanonicalLexicalScope scope = rootPlan.rootFacts().scope();
            for (CanonicalExpression expression : sequence.expressions()) {
                if (expression instanceof CanonicalCreate create
                        && create.target().isEmpty()
                        && create.value() instanceof CanonicalClosure definition) {
                    CanonicalBindingIdentity identity =
                            analysis.identityOf(create).orElse(null);
                    if (identity != null && identity.owner() == scope) {
                        experimentalRecursiveBindings.put(definition, identity);
                    }
                }
            }
        }

        java.util.List<PendingGroupClosure> savedPendingGroupClosures =
                pendingGroupClosures;
        pendingGroupClosures = new java.util.ArrayList<>();
        try {
            BytecodeRootNodes<ProtosBytecodeRootNode> roots =
                    ProtosBytecodeRootNodeGen.create(
                            language,
                            ProtosExperimentalTraceHooks.ENABLED
                                    ? EXPERIMENTAL_TRACE_CONFIG
                                    : BytecodeConfig.DEFAULT,
                            builder -> {
                                java.util.Set<CanonicalClosure> savedLoweredThisParse =
                                        closureRootsLoweredThisParse;
                                closureRootsLoweredThisParse =
                                        java.util.Collections.newSetFromMap(
                                                new java.util.IdentityHashMap<>());
                                try {
                                    /*
                                     * Root-inside-Source is the Bytecode DSL
                                     * shape that gives every root in the group
                                     * a reliable exact source section once lazy
                                     * source information is materialized.
                                     */
                                    builder.beginSource(source);
                                    lowerRootInto(
                                            builder,
                                            sequence,
                                            activationDefinition,
                                            rootPlan);
                                    builder.endSource();
                                } finally {
                                    closureRootsLoweredThisParse = savedLoweredThisParse;
                                }
                            });

            for (PendingGroupClosure pending : pendingGroupClosures) {
                pending.cell()
                        .freeze(
                                ProtosClosureExecutionPlan.bytecode(
                                        pending.definition(),
                                        language,
                                        source,
                                        pending.bindingAnalysis(),
                                        pending.root(),
                                        compilationFacts.mayObserveReturnHome(
                                                pending.definition())));
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
     *
     * <p>I106/I107: {@code rootPlan} was prepared by the caller before this
     * root's builder section opened and is not proven again here.
     */
    private ProtosBytecodeRootNode lowerRootInto(
            ProtosBytecodeRootNodeGen.Builder builder,
            CanonicalSequence sequence,
            CanonicalClosure activationDefinition,
            RootEmissionPlan rootPlan) {
        CanonicalCompilationFacts.RootFacts rootFacts = rootPlan.rootFacts();
        CanonicalBindingAnalysis analysisForThisRoot = rootFacts.bindingAnalysis();
        CanonicalLexicalScope scopeForThisRoot = rootFacts.scope();
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
        CanonicalClosure savedExperimentalDefinition =
                currentExperimentalRootDefinition;
        CanonicalLexicalScope savedTopScope = currentRootTopScope;
        java.util.Map<String, BytecodeLocal> savedFrameLocals =
                currentRootFrameLocals;
        BytecodeLocal savedActivationLocal = currentActivationLocal;
        java.util.Map<CanonicalBindingIdentity, BytecodeLocal> savedCellLocals =
                currentRootCellLocals;
        java.util.Set<String> savedScalarLocals = currentRootScalarLocals;
        var savedArithmeticHints = currentRootArithmeticHints;
        CanonicalSend savedTerminalIntegerSend = currentRootTerminalIntegerSend;
        var savedTerminalIntegerAddition = currentRootTerminalIntegerAddition;
        java.util.Map<CanonicalCall, CanonicalClosure> savedBindingCallCandidates =
                currentRootBindingCallCandidates;
        java.util.Set<CanonicalClosure> savedMaterializedClosures =
                currentRootMaterializedClosures;
        boolean savedInlineClosureCallBody = currentInlineClosureCallBody;
        boolean savedContinuationPath = currentRootEmittedContinuationPath;
        boolean savedTerminalInvocation = currentRootEmittedTerminalInvocation;
        try {
            currentInlineClosureCallBody = false;
            currentRootEmittedContinuationPath = false;
            currentRootEmittedTerminalInvocation = false;
            /*
             * I096-B1: a Closure created by a module root owns a fresh
             * ReturnHome, which the carrier never admits, so a module root
             * would only grow by regions that always fall back.
             */
            currentRootBindingCallCandidates =
                    activationDefinition == null
                            ? java.util.Map.of()
                            : compilationFacts.bindingClosureCallCandidates(
                                    sequence,
                                    activationDefinition,
                                    analysisForThisRoot,
                                    scopeForThisRoot);
            currentRootMaterializedClosures =
                    java.util.Collections.newSetFromMap(
                            new java.util.IdentityHashMap<>());
            currentRootAnalysis = analysisForThisRoot;
            currentExperimentalRootDefinition = activationDefinition;
            currentRootTopScope = scopeForThisRoot;
            currentRootFrameLocals = java.util.Map.of();
            currentRootCellLocals = java.util.Map.of();
            currentActivationLocal = null;
            currentRootScalarLocals = java.util.Set.of();
            currentRootArithmeticHints = java.util.Map.of();
            currentRootTerminalIntegerSend = null;
            currentRootTerminalIntegerAddition = null;
            return emitRootBody(
                    builder,
                    sequence,
                    activationDefinition,
                    rootPlan);
        } finally {
            currentRootEmittedContinuationPath = savedContinuationPath;
            currentRootEmittedTerminalInvocation = savedTerminalInvocation;
            currentRootAnalysis = savedAnalysis;
            currentExperimentalRootDefinition = savedExperimentalDefinition;
            currentRootTopScope = savedTopScope;
            currentRootFrameLocals = savedFrameLocals;
            currentRootCellLocals = savedCellLocals;
            currentActivationLocal = savedActivationLocal;
            currentRootScalarLocals = savedScalarLocals;
            currentRootArithmeticHints = savedArithmeticHints;
            currentRootTerminalIntegerSend = savedTerminalIntegerSend;
            currentRootTerminalIntegerAddition = savedTerminalIntegerAddition;
            currentRootBindingCallCandidates = savedBindingCallCandidates;
            currentRootMaterializedClosures = savedMaterializedClosures;
            currentInlineClosureCallBody = savedInlineClosureCallBody;
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
    private ProtosBytecodeRootNode lowerNestedClosureRoot(
            ProtosBytecodeRootNodeGen.Builder builder,
            CanonicalClosure definition) {
        validateSupportedDefaults(definition);
        RootEmissionPlan rootPlan =
                prepareRootPlan(
                        definition.body(),
                        definition,
                        bindingAnalysisForNestedClosure(definition));
        return lowerRootInto(
                builder,
                definition.body(),
                definition,
                rootPlan);
    }

    private ProtosBytecodeRootNode emitRootBody(
            ProtosBytecodeRootNodeGen.Builder builder,
            CanonicalSequence sequence,
            CanonicalClosure activationDefinition,
            RootEmissionPlan rootPlan) {
        CanonicalCompilationFacts.RootFacts rootFacts = rootPlan.rootFacts();
        CanonicalLexicalScope scopeForThisRoot = rootFacts.scope();
        SourceSpan rootSpan = sequence.span();

        // I115-M EXPERIMENTAL: whole-root compact lowering admission.
        ExperimentalCompactRoot experimentalCompact =
                admitExperimentalCompactRoot(
                        sequence, activationDefinition, rootFacts);
        // I115-H5: record the binding of an admitted recursive Integer callee.
        if (activationDefinition != null
                && experimentalCompact != null
                && experimentalCompact.experimentalRecursiveInteger()) {
            CanonicalBindingIdentity binding =
                    experimentalRecursiveBindings.get(activationDefinition);
            if (binding != null) {
                experimentalDirectUnaryBindings.add(binding);
            }
        }

        builder.beginSourceSection(
                rootSpan.startOffset(),
                rootSpan.length());
        builder.beginRoot();

        /*
         * I115: the storage of every binding of this root is fixed here,
         * before its body is emitted, from the binding analysis. A module
         * ROOT allocates no local for its declarations: they are NAME
         * bindings created in its module context. A CLOSURE root allocates
         * exactly one root-scoped Bytecode DSL local per declared binding,
         * including parameters (the value of a LOCAL binding or the cell
         * reference of a CELL binding), plus one local per free cell its
         * Closure carries. Allocating a local does not establish the binding.
         *
         * I111-A: these locals are created before the root Block so they
         * stay root-scoped and are live at every location of this root,
         * including the EnterRoot entry location.
         */
        java.util.Map<String, BytecodeLocal> frameLocals =
                new java.util.LinkedHashMap<>();
        java.util.Map<CanonicalBindingIdentity, BytecodeLocal> cellLocals =
                new java.util.LinkedHashMap<>();
        if (scopeForThisRoot.kind() == CanonicalLexicalScope.Kind.CLOSURE) {
            for (String name : scopeForThisRoot.declaredNames()) {
                BytecodeLocal local = builder.createLocal(name, null);
                frameLocals.put(name, local);
                if (scopeForThisRoot.needsCell(name)) {
                    cellLocals.put(scopeForThisRoot.identity(name), local);
                }
            }
            for (CanonicalBindingIdentity free : scopeForThisRoot.freeCells()) {
                cellLocals.put(
                        free,
                        builder.createLocal(FREE_CELL_LOCAL_PREFIX + free.name(), null));
            }
        }
        currentRootFrameLocals = java.util.Collections.unmodifiableMap(frameLocals);
        currentRootCellLocals = java.util.Collections.unmodifiableMap(cellLocals);

        /*
         * I111-A: this physical root is a genuine semantic source activation.
         * I115-H4-B EXPERIMENTAL: its RootTag/RootBodyTag are the Bytecode
         * DSL's automatic tags, no longer an explicit RootTag.
         */
        builder.beginBlock();

        /*
         * PLAT042 B′ / PERF025-H1: this root is entered directly (no
         * wrapper) and has no activation prologue. A compact source-call
         * frame stays compact until an operation that needs the rich
         * activation loads it through emitCurrentActivation.
         */
        emitCellPrologue(builder, scopeForThisRoot);

        currentRootScalarLocals =
                currentRootFrameLocals.isEmpty()
                        ? java.util.Set.of()
                        : scalarLocalNamesForRoot(
                                sequence,
                                activationDefinition,
                                currentRootAnalysis,
                                scopeForThisRoot);

        /*
         * I101-B: the lowering can encode a closed, proven sequence of
         * primitive-valued creations and established local reads directly in
         * Bytecode DSL locals. Neither the source body nor any admitted
         * operation can observe/capture the activation or affect the local
         * namespace. Caller-side completion owns the primitive carrier's
         * transition to a guest value, rather than this bytecode root.
         */
        boolean directScalarLocalBody =
                admitsNormalDirectScalarLocalBody(
                        sequence, activationDefinition, scopeForThisRoot);

        currentRootArithmeticHints =
                arithmeticHintsForRoot(sequence, scopeForThisRoot);

        if (!sequence.expressions().isEmpty()) {
            CanonicalExpression terminal =
                    sequence.expressions().get(sequence.expressions().size() - 1);
            if (terminal instanceof CanonicalSend send) {
                currentRootTerminalIntegerSend = send;
                currentRootTerminalIntegerAddition =
                        terminalKnownIntegerAddition(sequence, send);
            }
        }

        // I115-H7 EXPERIMENTAL: whether the prologue emitted CopyArguments.
        boolean experimentalPrimitiveArgumentRoot = false;
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
            if (experimentalCompact != null
                    && experimentalCompact.experimentalRecursiveInteger()) {
                /*
                 * I115-H3 EXPERIMENTAL: the admitted recursive Integer root
                 * binds its parameter with the built-in StoreLocal into the
                 * same frame local every compact read and store uses; no
                 * presence, activation or authority check.
                 */
                builder.beginStoreLocal(experimentalCompactLocal(
                        activationDefinition.parameters().get(0).name()));
                builder.emitExperimentalLongArgument0();
                builder.endStoreLocal();
            } else if (experimentalCompact != null
                    && !experimentalCompact.dispatches()
                    && activationDefinition.parameters().size() == 1) {
                /*
                 * I115-H7 EXPERIMENTAL: a non-dispatching compact unary root
                 * copies its argument from the B5 position into the
                 * parameter's frame local (GraalPy CopyArguments); the body
                 * reads it with a plain LoadLocal.
                 */
                builder.beginStoreLocal(experimentalCompactLocal(
                        activationDefinition.parameters().get(0).name()));
                builder.emitCopyArguments();
                builder.endStoreLocal();
                experimentalPrimitiveArgumentRoot = true;
            } else {
                emitClosureParameterBindings(
                        builder,
                        activationDefinition,
                        defaultValue,
                        defaultPreparedCall,
                        defaultChildResult,
                        defaultResumeValue);
            }
        }
        /*
         * I101-A: the emission plan of an admitted constant Boolean-control
         * terminal is its proven effective result. The canonical sequence is
         * not rewritten: every analysis keeps inspecting the original
         * statement, which also owns the statement's source section. I107:
         * the decision was taken by the pre-builder plan, a function of the
         * canonical tree and the publication snapshot, so every
         * reparse consumes the same one.
         */
        CanonicalLiteral foldedTerminal = rootPlan.foldedTerminal();

        boolean loweringRefinementAccepted = true;
        // PRUIBA DE I115, en algún momento esto volverá a funcionar
        // Terminal return values propagate unchanged.
        // I115-H4-B EXPERIMENTAL: whether the focal terminal Call1 was emitted.
        boolean experimentalCallRoot = false;
        if (experimentalCompact != null) {
            emitExperimentalCompactRoot(builder, sequence, experimentalCompact);
            /*
             * I115-M: literals, root locals, inline whileTrue and structured
             * Boolean control emit no send, callback, prepared invocation or
             * Yield. I115-H1-FIX: a numeric send's MISS path is an ordinary
             * send whose selected method may suspend, so only a root without
             * one publishes the refinement, as the I101-B direct
             * scalar-local body does.
             */
            if (!experimentalCompact.dispatches() && !experimentalCompact.invokes()) {
                loweringRefinementAccepted = acceptsLoweringRefinement(
                        publishLoweringRefinement(rootPlan, "I115-M compact root"));
            }
        } else if (activationDefinition == null
                && sequence.expressions().size() == 1
                && sequence.expressions().get(0) instanceof CanonicalCreate create
                && create.target().isEmpty()
                && create.value() instanceof CanonicalClosure) {
            // I115-L: experimental fused module creation and return.
            emitExperimentalModuleClosureReturn(builder, create);
        } else if (directScalarLocalBody) {
            emitNormalDirectScalarLocalBody(builder, sequence);
            loweringRefinementAccepted = acceptsLoweringRefinement(
                    publishLoweringRefinement(rootPlan, "I101-B direct scalar local body"));
        } else {
            CanonicalExpression minimalTerminal =
                    sequence.expressions().size() == 1
                            ? (foldedTerminal != null
                                    ? foldedTerminal
                                    : sequence.expressions().get(0))
                            : null;

            Object constant = null;
            if (activationDefinition != null
                    && activationDefinition.parameters().isEmpty()
                    && scopeForThisRoot.declaredNames().isEmpty()
                    && minimalTerminal != null) {
                constant = i101Constant(
                        minimalTerminal, java.util.Map.of(), scopeForThisRoot);
            }

            if (constant instanceof Long
                    || constant instanceof ProtosBooleanValue) {
                /*
                 * PLAT067-B: the constant return is the surviving executable
                 * location of the source statement, so it carries that
                 * statement's passive StatementTag + ExpressionTag (its exact
                 * span, not a folded callback literal's), as the ordinary
                 * literal-carrier terminal does. No operation is added and
                 * BytecodeConfig.DEFAULT materializes no tag instruction.
                 */
                beginStatement(builder, sequence.expressions().get(0));
                builder.beginReturn();
                builder.emitLoadConstant(constant);
                builder.endReturn();
                endStatement(builder);
                loweringRefinementAccepted = acceptsLoweringRefinement(
                        publishLoweringRefinement(rootPlan, "I101 minimal constant body"));
            } else if (sequence.expressions().isEmpty()) {
                builder.beginReturn();
                builder.emitLoadConstant(ProtosNullValue.INSTANCE);
                builder.endReturn();
            } else {
                java.util.List<CanonicalExpression> expressions =
                        sequence.expressions();
                CanonicalExpression lastStatement =
                        expressions.get(expressions.size() - 1);
                CanonicalExpression last =
                        foldedTerminal == null ? lastStatement : foldedTerminal;

                boolean scalarTerminal =
                        last instanceof CanonicalLookup lookup
                                && isScalarLocalRead(lookup);
                boolean directTerminal =
                        !requiresComposedInvocation(last)
                                && isDirectTerminalValue(last);

                boolean closureTerminal =
                        last instanceof CanonicalClosure;

                if (scalarTerminal || directTerminal || closureTerminal) {
                    /*
                     * PERF037-E: previous statements retain their established
                     * lowering. Only the terminal value bypasses sequenceResult.
                     * A single direct expression requires no result local.
                     */
                    if (expressions.size() > 1) {
                        java.util.List<CanonicalExpression> prefix =
                                expressions.subList(0, expressions.size() - 1);

                        emitDiscardedPrefix(builder, prefix);
                    }

                    /*
                     * A nested Closure root must be registered in the shared
                     * BytecodeRootNodes group before the Return operation opens.
                     * The Closure value itself is still created at runtime.
                     */
                    /*
                     * I112: a terminal read returns from the root as its last
                     * operation, so the Builder appends no implicit fallthrough
                     * return (load.null, epilogue, return) after the statement.
                     */
                    boolean returnEnclosesStatement =
                            !scalarTerminal
                                    && !closureTerminal
                                    && last instanceof CanonicalLookup;
                    if (returnEnclosesStatement) {
                        builder.beginReturn();
                    }
                    beginStatement(builder, lastStatement);
                    ProtosClosureExecutionPlanCell terminalClosurePlan =
                            closureTerminal
                                    ? bytecodeClosurePlan(
                                            builder, (CanonicalClosure) last)
                                    : null;
                    if (scalarTerminal) {
                        emitScalarLocalRead(
                                builder, (CanonicalLookup) last, null);
                    } else if (closureTerminal) {
                        builder.beginReturn();
                        emitClosureCapture(
                                builder, (CanonicalClosure) last, terminalClosurePlan);
                        builder.endReturn();
                    } else if (last instanceof CanonicalLiteral literal
                            && primitiveNumberLiteralOrNull(literal) != null) {
                        // I115: numeric literals return directly.
                        builder.beginReturn();
                        builder.emitLoadConstant(primitiveNumberLiteralOrNull(literal));
                        builder.endReturn();
                    } else if (last instanceof CanonicalLookup terminalRead) {
                        // I115: a terminal binding read returns directly.
                        emitLookup(builder, terminalRead, true);
                    } else {
                        builder.beginReturn();
                        emitExpression(builder, last);
                        builder.endReturn();
                    }
                    endStatement(builder);
                    if (returnEnclosesStatement) {
                        builder.endReturn();
                    }
                } else if (admitsTerminalClosureCall(last)) {
                    // I115: Call0 and Call1 return directly.
                    /*
                     * I112 / PLAT062-T: the final Closure call is the root's
                     * terminal invocation. No result or scratch local, no
                     * prepared-call dispatch and no Yield is emitted here: the
                     * terminal operation owns selection, its fallback and the
                     * transport of a suspended invocation.
                     */
                    if (expressions.size() > 1) {
                        emitDiscardedPrefix(
                                builder,
                                expressions.subList(0, expressions.size() - 1));
                    }
                    // Return is the root's last operation: no implicit fallthrough.
                    builder.beginReturn();
                    beginStatement(builder, last);
                    emitCall(builder, last);
                    endStatement(builder);
                    experimentalCallRoot =
                            isExperimentalLocalUnaryClosureCall(sequence, last);
                    builder.endReturn();
                } else if (admitsTerminalMethodSend(last)) {
                    /*
                     * PERF038-I: the final immediate Send1 is the root's
                     * terminal invocation, exactly as a terminal Call1. No
                     * sequenceResult, prepared-call or miss local and no
                     * direct send: GetMethod and CallUnaryMethod own selection,
                     * its fallback and the terminal execution. A retained
                     * numeric carrier is projected by the return epilogue.
                     */
                    if (expressions.size() > 1) {
                        emitDiscardedPrefix(
                                builder,
                                expressions.subList(0, expressions.size() - 1));
                    }
                    // Return is the root's last operation: no implicit fallthrough.
                    builder.beginReturn();
                    beginStatement(builder, last);
                    emitCall(builder, last);
                    endStatement(builder);
                    builder.endReturn();
                } else {
                    /*
                     * PERF037-E: even when the final expression requires
                     * composed lowering, earlier Sequence values are not
                     * observable. Evaluate them in order and discard their
                     * results; only the final value needs result storage.
                     */
                    ComposedSequenceScratch sharedScratch =
                            createComposedSequenceScratch(builder, expressions);

                    emitDiscardedPrefix(
                            builder,
                            expressions.subList(0, expressions.size() - 1),
                            sharedScratch);

                    BytecodeLocal result =
                            builder.createLocal("sequenceResult", null);

                    beginStatement(builder, last);

                    boolean carrierTerminal =
                            sharedScratch != null
                                    && ((last instanceof CanonicalSend terminalSend
                                                    && primitiveResultSend(terminalSend))
                                            || (last instanceof CanonicalCall terminalCall
                                                    && primitiveResultCall(terminalCall)));
                    if (carrierTerminal) {
                        // I091: the final numeric result may return as a carrier.
                        emitPrimitiveNumericOperandToLocal(
                                builder,
                                last,
                                result,
                                sharedScratch.preparedCall(),
                                sharedScratch.childResult(),
                                sharedScratch.resumeValue());
                    } else if (requiresComposedInvocation(last)) {
                        emitBodyExpressionToLocal(
                                builder,
                                last,
                                result,
                                sharedScratch == null
                                        ? null : sharedScratch.preparedCall(),
                                sharedScratch == null
                                        ? null : sharedScratch.childResult(),
                                sharedScratch == null
                                        ? null : sharedScratch.resumeValue());
                    } else if (last instanceof CanonicalLookup lookup
                            && isScalarLocalRead(lookup)) {
                        emitScalarLocalReadToResult(builder, lookup, result);
                    } else {
                        builder.beginStoreLocal(result);
                        emitExpression(builder, last);
                        builder.endStoreLocal();
                    }

                    endStatement(builder);

                    builder.beginReturn();
                    builder.emitLoadLocal(result);
                    builder.endReturn();
                }
            }

        }
        builder.endBlock();
        ProtosBytecodeRootNode result = builder.endRoot();
        result.markSemanticActivationRoot();
        if (activationDefinition != null) {
            result.recordReturnHomeProvenUnobservable(
                    !compilationFacts.mayObserveReturnHome(activationDefinition));
        }
        /*
         * I115-H4-B EXPERIMENTAL: the focal run root and the H3 recursive
         * Integer roots run the GraalPy-style callee context.
         */
        if (experimentalCallRoot
                || (experimentalCompact != null
                        && experimentalCompact.experimentalRecursiveInteger())) {
            result.markExperimentalCalleeContextRoot();
        }
        if (experimentalPrimitiveArgumentRoot) {
            result.markExperimentalPrimitiveArgumentRoot();

            /*
             * I115-H11-B1: physical ReadGlobal is the compact unary
             * callee operation that consumes the selected Closure.
             * Determine the ABI requirement once, when emitting the root.
             */
            if (result.getBytecodeNode().getInstructionsAsList().stream()
                    .anyMatch(instruction ->
                            instruction.getName().startsWith("c.ReadGlobal"))) {
                result.markExperimentalUnaryNeedsCapturedOwner();
            }
        }
        /*
         * I115-H7-R5: the compact admission and the presence of an H7
         * invocation are compile-time facts. The root and its host entry
         * must agree on whether a returned signed-64 long remains unwrapped.
         */
        if (experimentalCompact != null && experimentalCompact.invokes()) {
            result.markExperimentalH7RawIntegerReturn();
        }
        builder.endSourceSection();
        /*
         * I107: the root publishes the EFFECTIVE_ROOT proof consumed from the
         * facts: the planner's proof of entry plus effective body (a folded
         * terminal is its proven result, I101-A), possibly refined above by
         * this lowerer's own transformations. Publication cannot authorize
         * anything already emitted: a root that nevertheless emitted a generic
         * prepared invocation invalidates the unconsumed proof first.
         */
        CanonicalCompilationFacts.EffectiveRoot effectiveRoot = rootPlan.effectiveRoot();
        boolean emittedGenericInvocation = currentRootEmittedContinuationPath;
        /*
         * I112 / D204: a terminal invocation has no Yield in this root, yet
         * it may delegate a real continuation through its transport. Its
         * terminality is a property of the emission point, never a proof of
         * non-suspension.
         */
        boolean emittedTerminalInvocation = currentRootEmittedTerminalInvocation;
        if (emittedGenericInvocation || emittedTerminalInvocation) {
            // A consumed result is kept (REJECTED_CONSUMED is not an error).
            compilationFacts.invalidateNonSuspension(
                    CanonicalNonSuspensionAnalysis.Subject.EFFECTIVE_ROOT,
                    effectiveRoot,
                    rootFacts.bindingAnalysis(),
                    scopeForThisRoot,
                    emittedGenericInvocation
                            ? "generic invocation emitted"
                            : "terminal invocation emitted");
        }
        /*
         * The physical evidence of this parse always wins: a root that
         * emitted a generic invocation is never published as non-suspending,
         * whatever an earlier parse consumed.
         */
        result.recordProvablyNonSuspendingBody(
                !emittedGenericInvocation
                        && !emittedTerminalInvocation
                        && compilationFacts.consumeNonSuspension(
                                        CanonicalNonSuspensionAnalysis.Subject.EFFECTIVE_ROOT,
                                        effectiveRoot,
                                        rootFacts.bindingAnalysis(),
                                        scopeForThisRoot)
                                .proven()
                        && (loweringRefinementAccepted
                                || rootPlan.plannedEffectiveRoot().proven()));
        result.recordDirectScalarLocalCarrierBody(directScalarLocalBody);
        recordToolingBindings(result, scopeForThisRoot);
        if (ProtosDiagnosticRootIdentity.ENABLED) {
            ProtosDiagnosticRootIdentity.record(
                    result,
                    new ProtosDiagnosticRootIdentity.Span(
                            source,
                            rootSpan.startOffset(),
                            rootSpan.length(),
                            activationDefinition == null
                                    ? ProtosDiagnosticRootIdentity.TOP_LEVEL
                                    : ProtosDiagnosticRootIdentity.CLOSURE));
        }
        return result;
    }

    /*
     * I115: tooling-only description of a Closure root's LOCAL/CELL
     * bindings, so a debugger scope can read the live frame. Guest
     * execution never consults it.
     */
    private void recordToolingBindings(
            ProtosBytecodeRootNode root,
            CanonicalLexicalScope scope) {
        if (currentRootFrameLocals.isEmpty()) {
            return;
        }
        int count = currentRootFrameLocals.size();
        String[] names = new String[count];
        int[] offsets = new int[count];
        boolean[] cells = new boolean[count];
        int index = 0;
        for (java.util.Map.Entry<String, BytecodeLocal> entry : currentRootFrameLocals.entrySet()) {
            names[index] = entry.getKey();
            offsets[index] = entry.getValue().getLocalOffset();
            cells[index] = scope.needsCell(entry.getKey());
            index++;
        }
        root.recordToolingBindings(names, offsets, cells);
    }

    /**
     * I101-C1: one bounded compile-time scalar domain for arithmetic,
     * numeric comparisons, identity and immutable lexical bindings.
     *
     * Unknown values and effectful expressions are never admitted.
     */
    private boolean admitsNormalDirectScalarLocalBody(
            CanonicalSequence sequence,
            CanonicalClosure definition,
            CanonicalLexicalScope scope) {
        if (definition == null
                || !definition.parameters().isEmpty()
                || currentRootAnalysis == null
                || scope == null
                || currentRootFrameLocals.isEmpty()
                || !currentRootCellLocals.isEmpty()
                || sequence.expressions().isEmpty()) {
            return false;
        }

        java.util.Map<String, Object> known = new java.util.HashMap<>();
        var expressions = sequence.expressions();

        for (int index = 0; index < expressions.size(); index++) {
            CanonicalExpression expression = expressions.get(index);
            if (expression instanceof CanonicalCreate create) {
                if (create.target().isPresent()
                        || !currentRootFrameLocals.containsKey(create.name())
                        || known.containsKey(create.name())
                        || currentRootAnalysis.identityOf(create)
                                .map(identity -> identity.owner() != scope
                                        || !identity.name().equals(create.name()))
                                .orElse(true)) {
                    return false;
                }
                Object value = i101Constant(create.value(), known, scope);
                if (!i101DirectCarrier(value)) {
                    return false;
                }
                known.put(create.name(), value);
            } else if (expression instanceof CanonicalLookup lookup) {
                if (i101Constant(lookup, known, scope) == null) {
                    return false;
                }
            } else if (index == expressions.size() - 1) {
                if (!i101DirectCarrier(
                        i101Constant(expression, known, scope))) {
                    return false;
                }
            } else {
                return false;
            }
        }

        return known.keySet().equals(scope.declaredNames());
    }

    /**
     * Evaluate a bounded pure expression from source literals and proven
     * lexical constants. No guest dispatch or runtime state is consulted.
     */
    private Object knownScalarValue(
            CanonicalExpression expression,
            java.util.Map<String, Object> known,
            CanonicalLexicalScope scope) {
        if (expression instanceof CanonicalLiteral literal) {
            return switch (literal.kind()) {
                case NUMBER -> numberLiteralDescriptor(literal);
                case TRUE -> ProtosBooleanValue.TRUE;
                case FALSE -> ProtosBooleanValue.FALSE;
                case NULL -> ProtosNullValue.INSTANCE;
                default -> null;
            };
        }

        if (expression instanceof CanonicalLookup lookup) {
            if (!known.containsKey(lookup.name())
                    || currentRootAnalysis == null
                    || currentRootAnalysis.resolutionOf(lookup)
                            .map(resolution -> !(resolution instanceof
                                    CanonicalBindingResolution.Resolved resolved)
                                    || resolved.identity().owner() != scope
                                    || !resolved.identity().name()
                                            .equals(lookup.name()))
                            .orElse(true)) {
                return null;
            }
            return known.get(lookup.name());
        }

        if (expression instanceof CanonicalIdentity identity) {
            Object left = knownScalarValue(identity.left(), known, scope);
            Object right = knownScalarValue(identity.right(), known, scope);
            return left == null || right == null
                    ? null
                    : ProtosBooleanValue.of(knownIdentity(left, right));
        }

        if (expression instanceof CanonicalNotIdentity identity) {
            Object left = knownScalarValue(identity.left(), known, scope);
            Object right = knownScalarValue(identity.right(), known, scope);
            return left == null || right == null
                    ? null
                    : ProtosBooleanValue.of(!knownIdentity(left, right));
        }

        if (expression instanceof CanonicalDerivedInequality inequality) {
            Object left = knownScalarValue(inequality.left(), known, scope);
            Object right = knownScalarValue(inequality.right(), known, scope);
            Object equality = knownEquality(left, right);
            return equality instanceof ProtosBooleanValue result
                    ? ProtosBooleanValue.of(!result.value())
                    : null;
        }

        if (!(expression instanceof CanonicalSend send)
                || send.arguments().size() != 1) {
            return null;
        }

        // Reuse I101-B exact arithmetic, including its overflow and division
        // restrictions. Only proven Long bindings enter that evaluator.
        if (java.util.Set.of("+", "-", "*", "/").contains(send.message())) {
            java.util.Map<String, Long> integers = new java.util.HashMap<>();
            known.forEach((name, value) -> {
                if (value instanceof Long integer) {
                    integers.put(name, integer);
                }
            });
            return candidateSmallIntegerForFold(send, integers);
        }

        Object left = knownScalarValue(send.receiver(), known, scope);
        Object right = knownScalarValue(send.arguments().get(0), known, scope);

        if ("==".equals(send.message())) {
            return knownEquality(left, right);
        }

        if (!numericDescriptor(left) || !numericDescriptor(right)) {
            return null;
        }

        ProtosStandardNumberOrderingProtocol.Comparison comparison =
                compareKnownNumbers(left, right);

        Boolean result = switch (send.message()) {
            case "<" ->
                    comparison == ProtosStandardNumberOrderingProtocol
                            .Comparison.LESS;
            case "<=" ->
                    comparison == ProtosStandardNumberOrderingProtocol
                            .Comparison.LESS
                    || comparison == ProtosStandardNumberOrderingProtocol
                            .Comparison.EQUAL;
            case ">" ->
                    comparison == ProtosStandardNumberOrderingProtocol
                            .Comparison.GREATER;
            case ">=" ->
                    comparison == ProtosStandardNumberOrderingProtocol
                            .Comparison.GREATER
                    || comparison == ProtosStandardNumberOrderingProtocol
                            .Comparison.EQUAL;
            default -> {
                yield null;
            }
        };

        return result == null ? null : ProtosBooleanValue.of(result);
    }

    private static boolean numericDescriptor(Object value) {
        return value instanceof Long
                || value instanceof Double
                || value instanceof BigInteger;
    }

    private static BigInteger knownInteger(Object value) {
        return value instanceof Long integer
                ? BigInteger.valueOf(integer)
                : (BigInteger) value;
    }

    /**
     * Preserve mathematical equality across Integer/Float, but never
     * convert a large Integer to a rounded binary64 value.
     */
    private static ProtosStandardNumberOrderingProtocol.Comparison
            compareKnownNumbers(Object left, Object right) {
        if (left instanceof Double floating) {
            if (Double.isNaN(floating)) {
                return ProtosStandardNumberOrderingProtocol
                        .Comparison.UNORDERED;
            }
            if (right instanceof Double other) {
                return ProtosCurrentNumericRelations.compareFloats(
                        floating, other);
            }
            if (Double.isInfinite(floating)) {
                return floating > 0.0
                        ? ProtosStandardNumberOrderingProtocol
                                .Comparison.GREATER
                        : ProtosStandardNumberOrderingProtocol
                                .Comparison.LESS;
            }

            int order = new java.math.BigDecimal(floating)
                    .compareTo(new java.math.BigDecimal(knownInteger(right)));
            return knownComparisonFromSign(order);
        }

        if (right instanceof Double) {
            return ProtosCurrentNumericRelations.reverse(
                    compareKnownNumbers(right, left));
        }

        return knownComparisonFromSign(
                knownInteger(left).compareTo(knownInteger(right)));
    }

    private static ProtosStandardNumberOrderingProtocol.Comparison
            knownComparisonFromSign(int sign) {
        if (sign < 0) {
            return ProtosStandardNumberOrderingProtocol.Comparison.LESS;
        }
        if (sign > 0) {
            return ProtosStandardNumberOrderingProtocol.Comparison.GREATER;
        }
        return ProtosStandardNumberOrderingProtocol.Comparison.EQUAL;
    }

    private static Object knownEquality(Object left, Object right) {
        if (left == null || right == null) {
            return null;
        }
        if (numericDescriptor(left) && numericDescriptor(right)) {
            return ProtosBooleanValue.of(
                    compareKnownNumbers(left, right)
                            == ProtosStandardNumberOrderingProtocol
                                    .Comparison.EQUAL);
        }
        if (left instanceof ProtosBooleanValue
                && right instanceof ProtosBooleanValue) {
            return ProtosBooleanValue.of(left == right);
        }
        if (left == ProtosNullValue.INSTANCE
                || right == ProtosNullValue.INSTANCE) {
            return ProtosBooleanValue.of(left == right);
        }
        return null;
    }

    /**
     * Semantic numeric identity includes the numeric family. Float identity
     * distinguishes signed zero and unifies all NaN representations.
     */
    private static boolean knownIdentity(Object left, Object right) {
        if (numericDescriptor(left) && numericDescriptor(right)) {
            if (left instanceof Double floating) {
                if (!(right instanceof Double other)) {
                    return false;
                }
                return (Double.isNaN(floating) && Double.isNaN(other))
                        || Double.doubleToRawLongBits(floating)
                                == Double.doubleToRawLongBits(other);
            }
            if (right instanceof Double) {
                return false;
            }
            return knownInteger(left).equals(knownInteger(right));
        }
        return left == right;
    }

    /**
     * Direct emission preserves lexical StoreLocal/LoadLocal operations,
     * but replaces every admitted constant initializer and comparison with
     * its compile-time value.
     */
    /**
     * I101-C1: one typed, bounded compile-time evaluator for arithmetic,
     * numeric comparison, semantic equality and identity.
     */
    private Object i101Constant(
            CanonicalExpression expression,
            java.util.Map<String, Object> known,
            CanonicalLexicalScope scope) {
        if (expression instanceof CanonicalLiteral literal) {
            return switch (literal.kind()) {
                case NUMBER -> numberLiteralDescriptor(literal);
                case TRUE -> ProtosBooleanValue.TRUE;
                case FALSE -> ProtosBooleanValue.FALSE;
                case NULL -> ProtosNullValue.INSTANCE;
                default -> null;
            };
        }

        if (expression instanceof CanonicalLookup lookup) {
            if (!known.containsKey(lookup.name())
                    || currentRootAnalysis == null
                    || scope == null
                    || currentRootAnalysis.resolutionOf(lookup)
                            .map(resolution -> !(resolution instanceof
                                    CanonicalBindingResolution.Resolved resolved)
                                    || resolved.identity().owner() != scope
                                    || !resolved.identity().name()
                                            .equals(lookup.name()))
                            .orElse(true)) {
                return null;
            }
            return known.get(lookup.name());
        }

        if (expression instanceof CanonicalIdentity identity) {
            Object left = i101Constant(identity.left(), known, scope);
            Object right = i101Constant(identity.right(), known, scope);
            return left == null || right == null ? null
                    : ProtosBooleanValue.of(i101Identity(left, right));
        }

        if (expression instanceof CanonicalNotIdentity identity) {
            Object left = i101Constant(identity.left(), known, scope);
            Object right = i101Constant(identity.right(), known, scope);
            return left == null || right == null ? null
                    : ProtosBooleanValue.of(!i101Identity(left, right));
        }

        if (expression instanceof CanonicalDerivedInequality inequality) {
            Object left = i101Constant(inequality.left(), known, scope);
            Object right = i101Constant(inequality.right(), known, scope);
            Object equal = i101Equality(left, right);
            return equal instanceof ProtosBooleanValue booleanValue
                    ? ProtosBooleanValue.of(!booleanValue.value()) : null;
        }

        if (!(expression instanceof CanonicalSend send)
                || send.arguments().size() != 1) {
            return null;
        }

        if (java.util.Set.of("+", "-", "*", "/").contains(send.message())) {
            java.util.Map<String, Long> integers =
                    new java.util.HashMap<>();
            known.forEach((name, value) -> {
                if (value instanceof Long integer) {
                    integers.put(name, integer);
                }
            });
            return candidateSmallIntegerForFold(send, integers);
        }

        Object left = i101Constant(send.receiver(), known, scope);
        Object right = i101Constant(send.arguments().get(0), known, scope);

        if ("==".equals(send.message())) {
            return i101Equality(left, right);
        }

        if (!i101Numeric(left) || !i101Numeric(right)) {
            return null;
        }

        var relation = i101Compare(left, right);
        Boolean value = switch (send.message()) {
            case "<" -> relation ==
                    ProtosStandardNumberOrderingProtocol.Comparison.LESS;
            case "<=" -> relation ==
                    ProtosStandardNumberOrderingProtocol.Comparison.LESS
                    || relation ==
                    ProtosStandardNumberOrderingProtocol.Comparison.EQUAL;
            case ">" -> relation ==
                    ProtosStandardNumberOrderingProtocol.Comparison.GREATER;
            case ">=" -> relation ==
                    ProtosStandardNumberOrderingProtocol.Comparison.GREATER
                    || relation ==
                    ProtosStandardNumberOrderingProtocol.Comparison.EQUAL;
            default -> {
                yield null;
            }
        };
        return value == null ? null : ProtosBooleanValue.of(value);
    }

    private static boolean i101DirectCarrier(Object value) {
        return value instanceof Long
                || value instanceof Double
                || value instanceof ProtosBooleanValue
                || value == ProtosNullValue.INSTANCE;
    }

    private static boolean i101Numeric(Object value) {
        return value instanceof Long
                || value instanceof Double
                || value instanceof BigInteger;
    }

    private static BigInteger i101Integer(Object value) {
        return value instanceof Long integer
                ? BigInteger.valueOf(integer) : (BigInteger) value;
    }

    private static ProtosStandardNumberOrderingProtocol.Comparison
            i101Compare(Object left, Object right) {
        if (left instanceof Double floating) {
            if (Double.isNaN(floating)) {
                return ProtosStandardNumberOrderingProtocol
                        .Comparison.UNORDERED;
            }
            if (right instanceof Double other) {
                return ProtosCurrentNumericRelations.compareFloats(
                        floating, other);
            }
            if (Double.isInfinite(floating)) {
                return floating > 0.0
                        ? ProtosStandardNumberOrderingProtocol
                                .Comparison.GREATER
                        : ProtosStandardNumberOrderingProtocol
                                .Comparison.LESS;
            }
            return i101Sign(new java.math.BigDecimal(floating)
                    .compareTo(new java.math.BigDecimal(i101Integer(right))));
        }

        if (right instanceof Double) {
            return ProtosCurrentNumericRelations.reverse(
                    i101Compare(right, left));
        }
        return i101Sign(i101Integer(left).compareTo(i101Integer(right)));
    }

    private static ProtosStandardNumberOrderingProtocol.Comparison
            i101Sign(int sign) {
        if (sign < 0) {
            return ProtosStandardNumberOrderingProtocol.Comparison.LESS;
        }
        if (sign > 0) {
            return ProtosStandardNumberOrderingProtocol.Comparison.GREATER;
        }
        return ProtosStandardNumberOrderingProtocol.Comparison.EQUAL;
    }

    private static Object i101Equality(Object left, Object right) {
        if (left == null || right == null) {
            return null;
        }
        if (i101Numeric(left) && i101Numeric(right)) {
            return ProtosBooleanValue.of(i101Compare(left, right)
                    == ProtosStandardNumberOrderingProtocol
                            .Comparison.EQUAL);
        }
        if (left instanceof ProtosBooleanValue
                && right instanceof ProtosBooleanValue) {
            return ProtosBooleanValue.of(left == right);
        }
        if (left == ProtosNullValue.INSTANCE
                || right == ProtosNullValue.INSTANCE) {
            return ProtosBooleanValue.of(left == right);
        }
        return null;
    }

    private static boolean i101Identity(Object left, Object right) {
        if (i101Numeric(left) && i101Numeric(right)) {
            if (left instanceof Double floating) {
                if (!(right instanceof Double other)) {
                    return false;
                }
                return Double.isNaN(floating) && Double.isNaN(other)
                        || Double.doubleToRawLongBits(floating)
                                == Double.doubleToRawLongBits(other);
            }
            return !(right instanceof Double)
                    && i101Integer(left).equals(i101Integer(right));
        }
        return left == right;
    }

    private void emitNormalDirectScalarLocalBody(
            ProtosBytecodeRootNodeGen.Builder builder,
            CanonicalSequence sequence) {
        java.util.Map<String, Object> known = new java.util.HashMap<>();
        var expressions = sequence.expressions();

        for (int index = 0; index < expressions.size(); index++) {
            CanonicalExpression expression = expressions.get(index);
            /*
             * PLAT067-B: every statement survives as Bytecode-local stores
             * and the final return, so each keeps the ordinary passive
             * StatementTag + ExpressionTag over its exact span.
             */
            beginStatement(builder, expression);

            if (expression instanceof CanonicalCreate create) {
                Object value = i101Constant(
                        create.value(), known, currentRootTopScope);
                if (!i101DirectCarrier(value)) {
                    throw new AssertionError(
                            "I101-C1 scalar proof changed during lowering");
                }

                builder.beginStoreLocal(
                        currentRootFrameLocals.get(create.name()));
                if (create.value() instanceof CanonicalLookup lookup) {
                    builder.emitLoadLocal(
                            currentRootFrameLocals.get(lookup.name()));
                } else {
                    builder.emitLoadConstant(value);
                }
                builder.endStoreLocal();
                known.put(create.name(), value);
            }

            if (index == expressions.size() - 1) {
                builder.beginReturn();
                if (expression instanceof CanonicalCreate create) {
                    builder.emitLoadLocal(
                            currentRootFrameLocals.get(create.name()));
                } else if (expression instanceof CanonicalLookup lookup) {
                    builder.emitLoadLocal(
                            currentRootFrameLocals.get(lookup.name()));
                } else {
                    Object value = i101Constant(
                            expression, known, currentRootTopScope);
                    if (!i101DirectCarrier(value)) {
                        throw new AssertionError(
                                "I101-C1 terminal proof changed");
                    }
                    builder.emitLoadConstant(value);
                }
                builder.endReturn();
            }
            endStatement(builder);
        }
    }

    /**
     * PERF032-G6: a genuine source Closure root whose signature is empty.
     * Under the normative parameter-binding algorithm ({@code CALLABLES.md})
     * its only binding step is the final excess-argument check, which no
     * parameter, default, or body effect can precede; that check is therefore
     * taken by the call's entry-target selection ({@link
     * ProtosBytecodeRootNode#selectSourceEntryTarget}) instead of by
     * the root's own body. Closures with any parameter keep the in-body
     * algorithm unchanged.
     */
    /**
     * I101-A: the statements the root actually evaluates; the original
     * sequence, with a folded terminal replaced by its proven result.
     */
    private static java.util.List<CanonicalExpression> effectiveBody(
            CanonicalSequence sequence, CanonicalLiteral foldedTerminal) {
        java.util.List<CanonicalExpression> expressions = sequence.expressions();
        if (foldedTerminal == null) {
            return expressions;
        }
        java.util.List<CanonicalExpression> effective =
                new java.util.ArrayList<>(expressions);
        effective.set(effective.size() - 1, foldedTerminal);
        return java.util.List.copyOf(effective);
    }

    /**
     * I107: publishes a proof this lowerer's own transformation established
     * for the effective root it is emitting (a refinement of the planner's
     * UNKNOWN). Valid only for this root's effective identity, analysis and
     * scope.
     */
    private CanonicalCompilationFacts.Publication publishLoweringRefinement(
            RootEmissionPlan rootPlan, String provenance) {
        CanonicalCompilationFacts.RootFacts rootFacts = rootPlan.rootFacts();
        return compilationFacts.publishNonSuspension(
                new CanonicalNonSuspensionAnalysis.Result(
                        CanonicalNonSuspensionAnalysis.Proof.PROVEN_NO_SUSPENSION,
                        CanonicalNonSuspensionAnalysis.Subject.EFFECTIVE_ROOT,
                        rootPlan.effectiveRoot(),
                        rootFacts.bindingAnalysis(),
                        rootFacts.scope(),
                        java.util.EnumSet.noneOf(
                                CanonicalNonSuspensionAnalysis.Precondition.class),
                        provenance));
    }

    private static boolean acceptsLoweringRefinement(
            CanonicalCompilationFacts.Publication publication) {
        return switch (publication) {
            case RECORDED, REFINED, UNCHANGED -> true;
            case REJECTED_INCOMPATIBLE, REJECTED_CONSUMED,
                    REJECTED_INVALIDATED, INVALIDATED -> false;
        };
    }


    /**
     * PERF034-C: only a parameterless source Closure with an effect-free
     * straight-line body may use builtin StoreLocal/LoadLocal for its own
     * bindings. Each creation must establish a fresh, statically owned
     * name exactly once; reads must select an already-established name.
     *
     * No calls, context observation, closures, objects, mutation,
     * duplicate creation, candidate lookup or dynamic lookup are admitted.
     * All other programs retain the existing authoritative lowering.
     */
    private static java.util.Set<String> scalarLocalNamesForRoot(
            CanonicalSequence body,
            CanonicalClosure definition,
            CanonicalBindingAnalysis analysis,
            CanonicalLexicalScope scope) {
        if (definition == null
                || !definition.parameters().isEmpty()
                || analysis == null
                || scope == null) {
            return java.util.Set.of();
        }

        java.util.LinkedHashSet<String> established =
                new java.util.LinkedHashSet<>();

        java.util.List<CanonicalExpression> expressions = body.expressions();
        for (int index = 0; index < expressions.size(); index++) {
            CanonicalExpression expression = expressions.get(index);
            if (expression instanceof CanonicalCreate create) {
                if (create.target().isPresent()
                        || !(create.value() instanceof CanonicalLiteral
                                || (create.value() instanceof CanonicalLookup read
                                        && readsEstablishedScalar(
                                                read,
                                                analysis,
                                                scope,
                                                established)))
                        || analysis.identityOf(create)
                                .map(identity ->
                                        identity.owner() != scope
                                                || !identity.name().equals(
                                                        create.name()))
                                .orElse(true)
                        || !established.add(create.name())) {
                    return java.util.Set.of();
                }
                continue;
            }

            if (expression instanceof CanonicalLookup lookup) {
                if (!readsEstablishedScalar(
                        lookup, analysis, scope, established)) {
                    return java.util.Set.of();
                }
                continue;
            }

            /*
             * PERF036-A: a bare assignment whose destination is Resolved to
             * an already-established binding of this scope, and whose RHS is
             * a literal or an established scalar read. Such an RHS cannot
             * observe, remove, freeze or close the destination.
             */
            if (expression instanceof CanonicalAssign assign) {
                if (assign.target().isPresent()
                        || !(assign.value() instanceof CanonicalLiteral
                                || (assign.value() instanceof CanonicalLookup read
                                        && readsEstablishedScalar(
                                                read,
                                                analysis,
                                                scope,
                                                established)))
                        || !assignsEstablishedScalar(
                                assign, analysis, scope, established)) {
                    return java.util.Set.of();
                }
                continue;
            }

            /*
             * PERF043-A: a terminal send cannot invalidate an earlier local
             * read. Admit only a single, effect-free operand evaluation on
             * each side. The selected method remains entirely dynamic.
             */
            if (index == expressions.size() - 1
                    && expression instanceof CanonicalSend send
                    && java.util.Set.of("+", "-", "*", "/")
                            .contains(send.message())
                    && send.arguments().size() == 1
                    && scalarTerminalOperand(
                            send.receiver(), analysis, scope, established)
                    && scalarTerminalOperand(
                            send.arguments().get(0), analysis, scope, established)) {
                continue;
            }

            if (!(expression instanceof CanonicalLiteral)) {
                return java.util.Set.of();
            }
        }

        return java.util.Set.copyOf(established);
    }

    /**
     * PERF034-C/E: {@code lookup} is a {@code Resolved} read of a binding
     * owned by {@code scope} (its lexical identity, not its spelling) that an
     * earlier creation of the same straight-line body already established.
     * Such a read is effect-free and cannot reach a capture or a dynamic
     * binding.
     */
    private static boolean scalarTerminalOperand(
            CanonicalExpression expression,
            CanonicalBindingAnalysis analysis,
            CanonicalLexicalScope scope,
            java.util.Set<String> established) {
        return expression instanceof CanonicalLiteral
                || (expression instanceof CanonicalLookup lookup
                        && readsEstablishedScalar(
                                lookup, analysis, scope, established));
    }

    private static boolean readsEstablishedScalar(
            CanonicalLookup lookup,
            CanonicalBindingAnalysis analysis,
            CanonicalLexicalScope scope,
            java.util.Set<String> established) {
        return analysis.resolutionOf(lookup)
                .map(resolution ->
                        resolution
                                instanceof CanonicalBindingResolution.Resolved resolved
                                && resolved.identity().owner() == scope
                                && resolved.identity().name().equals(lookup.name())
                                && established.contains(
                                        resolved.identity().name()))
                .orElse(false);
    }

    /**
     * PERF036-A: the assignment counterpart of {@link
     * #readsEstablishedScalar}: {@code assign}'s destination is {@code
     * Resolved} to a binding of {@code scope} that an earlier creation of the
     * same straight-line body already established.
     */
    private static boolean assignsEstablishedScalar(
            CanonicalAssign assign,
            CanonicalBindingAnalysis analysis,
            CanonicalLexicalScope scope,
            java.util.Set<String> established) {
        return analysis.resolutionOf(assign)
                .map(resolution ->
                        resolution
                                instanceof CanonicalBindingResolution.Resolved resolved
                                && resolved.identity().owner() == scope
                                && resolved.identity().name().equals(assign.name())
                                && established.contains(
                                        resolved.identity().name()))
                .orElse(false);
    }


    /*
     * PERF043-A: compile-time values are only evidence for a guarded
     * runtime selection. They never replace the CanonicalSend itself.
     */
    private ProtosPrimitiveNumericSendFastPathsBase
            .FoldedIntegerArithmetic terminalKnownIntegerAddition(
                    CanonicalSequence body, CanonicalSend send) {
        java.util.Map<String, Long> known = new java.util.HashMap<>();

        if (!currentRootScalarLocals.isEmpty()) {
            java.util.List<CanonicalExpression> expressions = body.expressions();
            for (int index = 0; index < expressions.size() - 1; index++) {
                CanonicalExpression expression = expressions.get(index);

                if (expression instanceof CanonicalCreate create
                        && create.target().isEmpty()) {
                    Long value = candidateSmallIntegerForFold(create.value(), known);
                    if (value == null) {
                        known.remove(create.name());
                    } else {
                        known.put(create.name(), value);
                    }
                } else if (expression instanceof CanonicalAssign assign
                        && assign.target().isEmpty()) {
                    Long value = candidateSmallIntegerForFold(assign.value(), known);
                    if (value == null) {
                        known.remove(assign.name());
                    } else {
                        known.put(assign.name(), value);
                    }
                }
            }
        }

        return foldedIntegerAddition(send, known);
    }

    private Long knownSmallInteger(
            CanonicalExpression expression,
            java.util.Map<String, Long> known) {
        if (expression instanceof CanonicalLiteral literal) {
            Object value = primitiveNumberLiteralOrNull(literal);
            return value instanceof Long integer ? integer : null;
        }
        if (expression instanceof CanonicalLookup lookup
                && currentRootAnalysis != null
                && currentRootTopScope != null
                && currentRootAnalysis.resolutionOf(lookup)
                        .map(resolution ->
                                resolution instanceof CanonicalBindingResolution.Resolved resolved
                                        && resolved.identity().owner() == currentRootTopScope
                                        && resolved.identity().name().equals(lookup.name()))
                        .orElse(false)) {
            return known.get(lookup.name());
        }
        return null;
    }


    /**
     * A recursive candidate is not an executable constant.
     * Every CanonicalSend remains an ordinary guarded message send.
     */
    private Long candidateSmallIntegerForFold(
            CanonicalExpression expression,
            java.util.Map<String, Long> known) {
        if (expression instanceof CanonicalSend send) {
            var folded = foldedIntegerAddition(send, known);
            return folded == null ? null : folded.result();
        }
        return knownSmallInteger(expression, known);
    }

    private ProtosPrimitiveNumericSendFastPathsBase
            .FoldedIntegerArithmetic foldedIntegerAddition(
                    CanonicalSend send, java.util.Map<String, Long> known) {
        if (send.arguments().size() != 1) {
            return null;
        }

        var operation = switch (send.message()) {
            case "+" -> ProtosStandardIntegerProtocol
                    .CanonicalIntegerOperation.ADD;
            case "-" -> ProtosStandardIntegerProtocol
                    .CanonicalIntegerOperation.SUBTRACT;
            case "*" -> ProtosStandardIntegerProtocol
                    .CanonicalIntegerOperation.MULTIPLY;
            case "/" -> ProtosStandardIntegerProtocol
                    .CanonicalIntegerOperation.EXACT_DIVIDE;
            default -> null;
        };

        if (operation == null) {
            return null;
        }

        Long left = candidateSmallIntegerForFold(send.receiver(), known);
        Long right = candidateSmallIntegerForFold(send.arguments().get(0), known);

        if (left == null || right == null) {
            return null;
        }

        try {
            long result;
            switch (operation) {
                case ADD -> result = Math.addExact(left, right);
                case SUBTRACT -> result = Math.subtractExact(left, right);
                case MULTIPLY -> result = Math.multiplyExact(left, right);
                case EXACT_DIVIDE -> {
                    if (right == 0L
                            || (left == Long.MIN_VALUE && right == -1L)
                            || left % right != 0L) {
                        return null;
                    }
                    result = left / right;
                }
                default -> {
                    return null;
                }
            }

            return new ProtosPrimitiveNumericSendFastPathsBase
                    .FoldedIntegerArithmetic(operation, left, right, result);
        } catch (ArithmeticException overflow) {
            return null;
        }
    }

    /**
     * GraalJS-style bounded constant candidates, not a guest evaluator.
     * Every operation still executes via its guarded canonical send.
     */
    private java.util.Map<CanonicalSend,
            ProtosPrimitiveNumericSendFastPathsBase
                    .FoldedIntegerArithmetic> arithmeticHintsForRoot(
                    CanonicalSequence body, CanonicalLexicalScope scope) {
        java.util.Map<CanonicalSend,
                ProtosPrimitiveNumericSendFastPathsBase
                        .FoldedIntegerArithmetic> hints =
                new java.util.IdentityHashMap<>();
        if (currentRootAnalysis == null || scope == null) {
            return hints;
        }

        java.util.Map<String, Long> known = new java.util.HashMap<>();
        java.util.Set<String> established = new java.util.HashSet<>();

        for (CanonicalExpression expression : body.expressions()) {
            if (expression instanceof CanonicalCreate create
                    && create.target().isEmpty()
                    && currentRootAnalysis.identityOf(create)
                            .map(identity ->
                                    identity.owner() == scope
                                            && identity.name().equals(create.name()))
                            .orElse(false)
                    && established.add(create.name())) {
                collectArithmeticHints(create.value(), known, hints);
                Long value = candidateSmallIntegerForFold(create.value(), known);
                if (value == null) {
                    known.remove(create.name());
                } else {
                    known.put(create.name(), value);
                }
                continue;
            }

            if (expression instanceof CanonicalAssign assign
                    && assign.target().isEmpty()
                    && established.contains(assign.name())
                    && currentRootAnalysis.resolutionOf(assign)
                            .map(resolution ->
                                    resolution instanceof CanonicalBindingResolution.Resolved resolved
                                            && resolved.identity().owner() == scope
                                            && resolved.identity().name().equals(assign.name()))
                            .orElse(false)) {
                collectArithmeticHints(assign.value(), known, hints);
                Long value = candidateSmallIntegerForFold(assign.value(), known);
                if (value == null) {
                    known.remove(assign.name());
                } else {
                    known.put(assign.name(), value);
                }
                continue;
            }

            if (expression instanceof CanonicalSend send) {
                collectArithmeticHints(send, known, hints);
            }

            if (!(expression instanceof CanonicalLookup)) {
                known.clear();
            }
        }
        return hints;
    }

    private void collectArithmeticHints(
            CanonicalExpression expression,
            java.util.Map<String, Long> known,
            java.util.Map<CanonicalSend,
                    ProtosPrimitiveNumericSendFastPathsBase
                            .FoldedIntegerArithmetic> hints) {
        if (!(expression instanceof CanonicalSend send)) {
            return;
        }

        collectArithmeticHints(send.receiver(), known, hints);
        for (CanonicalExpression argument : send.arguments()) {
            collectArithmeticHints(argument, known, hints);
        }

        var candidate = foldedIntegerAddition(send, known);
        if (candidate != null) {
            hints.put(send, candidate);
        }
    }

    /** PERF034-C / I115: an admitted scalar read stored into {@code result}. */
    private void emitScalarLocalReadToResult(
            ProtosBytecodeRootNodeGen.Builder builder,
            CanonicalLookup lookup,
            BytecodeLocal result) {
        emitScalarLocalRead(builder, lookup, result);
    }

    /**
     * PERF034-C/E: whether {@code lookup} reads an admitted scalar local of
     * the root being lowered (outside any inline activation region).
     */
    private boolean isScalarLocalRead(CanonicalLookup lookup) {
        return currentNormalCallbackScalarLocals.isEmpty()
                && currentActivationLocal == null
                && currentRootScalarLocals.contains(lookup.name());
    }

    /**
     * I115 / GraalPy load.local: a statically resolved current-root scalar
     * reads its Bytecode DSL local directly, with no compact/published switch.
     *
     * <p>KNOWN I115 SEMANTIC DIVERGENCE (D179 C0, PERF034-C/E): after context
     * publication, dynamic removal/recreation or lexical redirection of this
     * binding is not observed by this read. I115 explicitly assumes those
     * operations do not occur; restore their semantics in a separate change,
     * not by adding a fallback to the ordinary local path.
     */
    private void emitScalarLocalRead(
            ProtosBytecodeRootNodeGen.Builder builder,
            CanonicalLookup lookup,
            BytecodeLocal result) {
        BytecodeLocal local = currentRootFrameLocals.get(lookup.name());
        if (local == null) {
            throw new AssertionError(
                    "admitted scalar local is missing: " + lookup.name());
        }
        beginScalarReadSink(builder, result);
        builder.emitLoadLocal(local);
        endScalarReadSink(builder, result);
    }

    private static void beginScalarReadSink(
            ProtosBytecodeRootNodeGen.Builder builder,
            BytecodeLocal result) {
        if (result == null) {
            builder.beginReturn();
        } else {
            builder.beginStoreLocal(result);
        }
    }

    private static void endScalarReadSink(
            ProtosBytecodeRootNodeGen.Builder builder,
            BytecodeLocal result) {
        if (result == null) {
            builder.endReturn();
        } else {
            builder.endStoreLocal();
        }
    }

    /**
     * Opens one source statement over {@code expression}'s exact span:
     * StatementTag + ExpressionTag around a block.
     */
    private static void beginStatement(
            ProtosBytecodeRootNodeGen.Builder builder,
            CanonicalExpression expression) {
        SourceSpan span = expression.span();
        builder.beginSourceSection(
                span.startOffset(),
                span.length());
        builder.beginTag(
                StandardTags.StatementTag.class,
                StandardTags.ExpressionTag.class);
        builder.beginBlock();
    }

    private static void endStatement(
            ProtosBytecodeRootNodeGen.Builder builder) {
        builder.endBlock();
        builder.endTag(
                StandardTags.StatementTag.class,
                StandardTags.ExpressionTag.class);
        builder.endSourceSection();
    }

    private void emitStatementsToLocal(
            ProtosBytecodeRootNodeGen.Builder builder,
            CanonicalSequence sequence,
            BytecodeLocal result) {
        emitStatementsToLocal(builder, sequence.expressions(), result);
    }

    private void emitStatementsToLocal(
            ProtosBytecodeRootNodeGen.Builder builder,
            java.util.List<CanonicalExpression> expressions,
            BytecodeLocal result) {
        // PERF036-A: an admitted scalar assignment needs no invocation staging.
        boolean hasComposedInvocation =
                expressions.stream()
                        .anyMatch(expression ->
                                requiresComposedInvocation(expression)
                                        && !(expression instanceof CanonicalAssign assign
                                                && isScalarLocalAssign(assign)));
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

        for (CanonicalExpression expression : expressions) {
            beginStatement(builder, expression);

            if (requiresComposedInvocation(expression)) {
                emitBodyExpressionToLocal(
                        builder,
                        expression,
                        result,
                        preparedCall,
                        childResult,
                        resumeValue);
            } else if (expression instanceof CanonicalLookup lookup
                    && isScalarLocalRead(lookup)) {
                emitScalarLocalReadToResult(builder, lookup, result);
            } else {
                builder.beginStoreLocal(result);
                emitExpression(builder, expression);
                builder.endStoreLocal();
            }

            endStatement(builder);
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
        if (expression instanceof CanonicalNamedArgument named) {
            validateSupportedDefaultExpression(named.expression());
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
     * {@link com.guillermomolina.protos.runtime.ProtosActivation}. A root's
     * own activation is frame argument 0, or is materialized there on demand
     * from a compact source-call ABI by the {@code CurrentActivation}
     * operation (PERF025-H1); inside an inline object-construction body
     * (PLAT041 C′) the current
     * activation is instead that body's construction activation, held in
     * {@link #currentActivationLocal}. Inside a PLAT044 B′ inline callback
     * region that local holds the invocation's carrier instead, and the
     * callback activation is materialized from it on demand, at most once
     * (PERF025 lazy inline callback activation); in a frame-native region
     * that materialization first moves the callback's block-local bindings
     * to a durable authority on the activation. Operations that need "the
     * current activation" must obtain it through this method rather than
     * emitting the argument load directly. This is a compile-time choice only.
     */
    private void emitCurrentActivation(
            ProtosBytecodeRootNodeGen.Builder builder) {
        if (currentActivationLocal == null) {
            builder.emitCurrentActivation(false);
        } else {
            builder.emitLoadLocal(currentActivationLocal);
        }
    }

    /*
     * PERF025 slice 3. Inside a frame-native inline callback region, the
     * consumers below take the carrier-operand operation, which runs its
     * successful path without the callback activation and otherwise
     * materializes it exactly as emitCurrentActivation would; everywhere else
     * they take the unchanged activation operation. Each begin/end pair is
     * emitted within one region, so the selection is the same for both.
     */

    /** The caller operand of a send/Closure-call preparation opened above. */
    private void emitInvocationCallerOperand(
            ProtosBytecodeRootNodeGen.Builder builder) {
        {
            emitCurrentActivation(builder);
        }
    }

    /**
     * PERF038-D: the caller operand of a send preparation opened by {@link
     * #beginPrepareSend}. At root level it is the lazy caller reference,
     * which materializes the root's activation only for the specializations
     * that need its exact identity; elsewhere it is {@link
     * #emitInvocationCallerOperand}.
     */
    private void emitSendCallerOperand(
            ProtosBytecodeRootNodeGen.Builder builder) {
        if (currentActivationLocal == null) {
            builder.emitCurrentActivation(true);
        } else {
            emitInvocationCallerOperand(builder);
        }
    }

    /**
     * Opens a member read and emits its activation (or carrier) operand. A
     * root-level read (PERF037-B) takes no activation operand: it reaches
     * the root's frame arguments itself, only when it must.
     */
    private void beginMemberRead(
            ProtosBytecodeRootNodeGen.Builder builder,
            String name) {
        if (currentActivationLocal == null) {
            builder.beginReadMemberAtRoot(name);
        } else {
            builder.beginReadMember(name);
            emitCurrentActivation(builder);
        }
    }

    private void endMemberRead(ProtosBytecodeRootNodeGen.Builder builder) {
        if (currentActivationLocal == null) {
            builder.endReadMemberAtRoot();
        } else {
            builder.endReadMember();
        }
    }

    /**
     * I115: binds every parameter of {@code definition}, left to right,
     * directly into its classified storage. Each supplied value is read from
     * the B5 call ABI, whose supplied positions keep their offsets after
     * publication, so no activation is materialized to bind a parameter.
     *
     * <p>D203: parameter {@code k} reads supplied position {@code k}; an
     * unsupplied position evaluates its default, if any, after every earlier
     * parameter is bound, or binds canonical null. Excess supplied arguments
     * are ignored without rest, so no upper-bound check is emitted. A rest
     * parameter binds the frozen Array of the remaining supplied values.
     *
     * <p>KNOWN I115 SEMANTIC DIVERGENCE: parameter establishment does not
     * check for a duplicate creation through an observed context.
     */
    private void emitClosureParameterBindings(
            ProtosBytecodeRootNodeGen.Builder builder,
            CanonicalClosure definition,
            BytecodeLocal defaultValue,
            BytecodeLocal defaultPreparedCall,
            BytecodeLocal defaultChildResult,
            BytecodeLocal defaultResumeValue) {
        int positionalIndex = 0;
        for (CanonicalParameter parameter : definition.parameters()) {
            if (parameter.rest()) {
                beginStoreParameter(builder, parameter);
                builder.emitLoadRestArguments(positionalIndex);
                endStoreParameter(builder, parameter);
                continue;
            }

            if (parameter.defaultValue().isPresent()) {
                CanonicalExpression defaultExpression =
                        parameter.defaultValue().orElseThrow();

                builder.beginIfThenElse();
                builder.emitHasSuppliedArgument(positionalIndex);

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
                    beginStoreParameter(builder, parameter);
                    builder.beginSourceSection(
                            defaultExpression.span().startOffset(),
                            defaultExpression.span().length());
                    emitExpression(builder, defaultExpression);
                    builder.endSourceSection();
                    endStoreParameter(builder, parameter);
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
    }

    private void emitBindDefaultLocal(
            ProtosBytecodeRootNodeGen.Builder builder,
            CanonicalParameter parameter,
            BytecodeLocal defaultValue) {
        if (defaultValue == null) {
            throw new AssertionError("composed default value local was not allocated");
        }
        beginStoreParameter(builder, parameter);
        builder.emitLoadLocal(defaultValue);
        endStoreParameter(builder, parameter);
    }

    /** I115: a supplied (or D203 canonical null) parameter value, stored directly. */
    private void emitBindSuppliedClosureParameter(
            ProtosBytecodeRootNodeGen.Builder builder,
            CanonicalParameter parameter,
            int positionalIndex) {
        beginStoreParameter(builder, parameter);
        builder.emitLoadSuppliedArgument(positionalIndex);
        endStoreParameter(builder, parameter);
    }

    /**
     * I115: opens the store of {@code parameter} into its classified storage
     * ({@code StoreCell} into its cell, otherwise {@code StoreLocal}),
     * leaving only the value operand to emit.
     */
    private void beginStoreParameter(
            ProtosBytecodeRootNodeGen.Builder builder,
            CanonicalParameter parameter) {
        BytecodeLocal cell = parameterCellLocalOrNull(parameter);
        if (cell != null) {
            builder.beginStoreCell();
            builder.emitLoadLocal(cell);
            return;
        }
        builder.beginStoreLocal(parameterLocal(parameter));
    }

    private void endStoreParameter(
            ProtosBytecodeRootNodeGen.Builder builder,
            CanonicalParameter parameter) {
        if (parameterCellLocalOrNull(parameter) != null) {
            builder.endStoreCell();
        } else {
            builder.endStoreLocal();
        }
    }

    private BytecodeLocal parameterCellLocalOrNull(CanonicalParameter parameter) {
        return currentRootTopScope.needsCell(parameterIdentity(parameter).name())
                ? currentRootCellLocals.get(parameterIdentity(parameter))
                : null;
    }

    private BytecodeLocal parameterLocal(CanonicalParameter parameter) {
        BytecodeLocal local = currentRootFrameLocals.get(parameterIdentity(parameter).name());
        if (local == null) {
            throw new AssertionError("I115 parameter has no local: " + parameter.name());
        }
        return local;
    }

    private CanonicalBindingIdentity parameterIdentity(CanonicalParameter parameter) {
        CanonicalBindingIdentity identity =
                currentRootAnalysis == null
                        ? null
                        : currentRootAnalysis.identityOf(parameter).orElse(null);
        if (identity == null
                || identity.owner() != currentRootTopScope
                || currentActivationLocal != null) {
            throw new AssertionError("I115 unclassified parameter: " + parameter.name());
        }
        return identity;
    }

    /*
     * I115: the root-scoped locals of the cells visible to a CLOSURE root are
     * established once at entry: a fresh cell for each own CELL binding
     * (uninitialized until its binding point) and the Closure's captured
     * cell for each free cell, at its constant captured-cell index.
     */
    private void emitCellPrologue(
            ProtosBytecodeRootNodeGen.Builder builder,
            CanonicalLexicalScope scope) {
        if (scope.kind() != CanonicalLexicalScope.Kind.CLOSURE) {
            return;
        }
        for (String name : scope.cellNamesInDeclarationOrder()) {
            builder.beginStoreLocal(currentRootCellLocals.get(scope.identity(name)));
            builder.emitCreateCell();
            builder.endStoreLocal();
        }
        java.util.List<CanonicalBindingIdentity> free = scope.freeCells();
        for (int index = 0; index < free.size(); index++) {
            builder.beginStoreLocal(currentRootCellLocals.get(free.get(index)));
            builder.emitLoadCapturedCell(index);
            builder.endStoreLocal();
        }
    }

    /**
     * I115: the static binding identity of a classified site, or {@code null}
     * for a {@code Dynamic} (or unanalyzed) site, which is a name operation.
     */
    private static CanonicalBindingIdentity staticIdentityOrNull(
            CanonicalBindingResolution resolution) {
        if (resolution instanceof CanonicalBindingResolution.Resolved resolved) {
            return resolved.identity();
        }
        if (resolution instanceof CanonicalBindingResolution.CapturedResolved captured) {
            return captured.identity();
        }
        if (resolution instanceof CanonicalBindingResolution.Candidate candidate) {
            return candidate.identity();
        }
        return null;
    }

    /**
     * I115: the cell local of {@code identity} when its storage is CELL, else
     * {@code null}. A cell binding is always reachable from the root being
     * lowered (its owner, or a root carrying it as a free cell); anything
     * else is a lowering defect, never a reason to choose another storage.
     */
    private BytecodeLocal cellLocalOrNull(CanonicalBindingIdentity identity) {
        if (CanonicalBindingAnalysis.storageOf(identity) != CanonicalLexicalScope.Storage.CELL) {
            return null;
        }
        BytecodeLocal local = currentRootCellLocals.get(identity);
        if (local == null) {
            throw new AssertionError("I115 cell is not reachable from this root: " + identity.name());
        }
        return local;
    }

    /**
     * I115: the Bytecode DSL local of a LOCAL binding owned by the CLOSURE
     * root being lowered, else {@code null} (NAME or MEMBER storage). A LOCAL
     * binding is only ever accessed from its own root; another owner is a
     * lowering defect.
     */
    private BytecodeLocal ownLocalOrNull(CanonicalBindingIdentity identity) {
        if (CanonicalBindingAnalysis.storageOf(identity) != CanonicalLexicalScope.Storage.LOCAL) {
            return null;
        }
        BytecodeLocal local =
                identity.owner() == currentRootTopScope
                        ? currentRootFrameLocals.get(identity.name())
                        : null;
        if (local == null) {
            throw new AssertionError("I115 local is not owned by this root: " + identity.name());
        }
        return local;
    }

    /**
     * I115: whether a classified site accesses a LOCAL binding before its
     * binding point. A LOCAL binding is never referenced across a Closure
     * boundary, so a {@code Candidate} (not yet established at this program
     * point) of LOCAL storage executes in place, strictly before the binding
     * point: the access is statically the uninitialized-binding Error. It is
     * never redirected to another binding or storage.
     */
    private static boolean isUninitializedLocalAccess(
            CanonicalBindingResolution resolution,
            CanonicalBindingIdentity identity) {
        return resolution instanceof CanonicalBindingResolution.Candidate
                && CanonicalBindingAnalysis.storageOf(identity)
                        == CanonicalLexicalScope.Storage.LOCAL;
    }

    /**
     * Emits the target-less creation of {@code create} from {@code value}
     * into its classified storage, yielding the created value: {@code
     * StoreCell} for a CELL binding, {@code StoreLocal} for a LOCAL binding,
     * and the {@code CreateCurrentLocalSlot} name operation for a module
     * (NAME) or object-body (MEMBER) binding. The value is evaluated once,
     * by the caller, before this operation.
     *
     * <p>KNOWN I115 SEMANTIC DIVERGENCE (D179 C0, PERF025, PERF030): a
     * LOCAL/CELL creation does not detect a duplicate creation and is not
     * observed through the activation's context.
     */
    private void emitCreateCurrentBinding(
            ProtosBytecodeRootNodeGen.Builder builder,
            CanonicalCreate create,
            BytecodeLocal value) {
        CanonicalBindingIdentity identity =
                currentRootAnalysis == null
                        ? null
                        : currentRootAnalysis.identityOf(create).orElse(null);
        if (identity != null && emitStoreBinding(builder, identity, value)) {
            return;
        }
        builder.beginCreateCurrentLocalSlot();
        emitCurrentActivation(builder);
        builder.emitLoadConstant(create.name());
        builder.emitLoadLocal(value);
        builder.endCreateCurrentLocalSlot();
    }

    /**
     * I115: when {@code identity} has LOCAL or CELL storage, emits its store
     * of the guest value in {@code value} followed by that value, and returns
     * {@code true}; returns {@code false} for NAME and MEMBER storage.
     */
    private boolean emitStoreBinding(
            ProtosBytecodeRootNodeGen.Builder builder,
            CanonicalBindingIdentity identity,
            BytecodeLocal value) {
        BytecodeLocal cell = cellLocalOrNull(identity);
        if (cell != null) {
            builder.beginBlock();
            builder.beginStoreCell();
            builder.emitLoadLocal(cell);
            builder.emitLoadLocal(value);
            builder.endStoreCell();
            builder.emitLoadLocal(value);
            builder.endBlock();
            return true;
        }
        BytecodeLocal local = ownLocalOrNull(identity);
        if (local != null) {
            builder.beginBlock();
            builder.beginStoreLocal(local);
            builder.emitLoadLocal(value);
            builder.endStoreLocal();
            builder.emitLoadLocal(local);
            builder.endBlock();
            return true;
        }
        return false;
    }

    /**
     * PERF034-C/E: one admitted scalar creation. A scalar root contains no
     * Closure literal, so its bindings are always LOCAL: {@code emitValue}'s
     * operand is stored into the builtin local, which then yields the
     * binding's value. {@code emitValue} must be effect-free (a constant or
     * an already-evaluated local).
     */
    private void emitScalarCreate(
            ProtosBytecodeRootNodeGen.Builder builder,
            CanonicalCreate create,
            BytecodeLocal result,
            Runnable emitValue) {
        BytecodeLocal local = currentRootFrameLocals.get(create.name());
        if (local == null) {
            throw new AssertionError(
                    "I115 missing scalar creation local: " + create.name());
        }

        builder.beginStoreLocal(local);
        emitValue.run();
        builder.endStoreLocal();

        builder.beginStoreLocal(result);
        builder.emitLoadLocal(local);
        builder.endStoreLocal();
    }

    /**
     * Emits a multiple creation from {@code source}. When the names are
     * LOCAL/CELL bindings of the root being lowered, the complete fixed
     * prefix is observed once, before any binding is created, and each value
     * is then stored, in source order, into its own classified storage; a
     * failing observation creates nothing. The whole expression yields
     * {@code source}. NAME and MEMBER bindings keep the {@code
     * CreateCurrentLocalSlot} name operation over all names.
     */
    private void emitMultipleCreateCurrentBindings(
            ProtosBytecodeRootNodeGen.Builder builder,
            java.util.List<String> names,
            java.util.List<CanonicalBindingIdentity> identities,
            BytecodeLocal source) {
        boolean direct =
                identities != null
                        && !identities.isEmpty()
                        && identities.stream().allMatch(identity ->
                                CanonicalBindingAnalysis.storageOf(identity)
                                                == CanonicalLexicalScope.Storage.LOCAL
                                        || CanonicalBindingAnalysis.storageOf(identity)
                                                == CanonicalLexicalScope.Storage.CELL);
        if (!direct) {
            builder.beginCreateCurrentLocalSlot();
            emitCurrentActivation(builder);
            builder.emitLoadConstant(multipleCreateNamesConstant(names));
            builder.emitLoadLocal(source);
            builder.endCreateCurrentLocalSlot();
            return;
        }
        builder.beginBlock();
        BytecodeLocal observed = builder.createLocal("multipleCreateObserved", null);
        builder.beginStoreLocal(observed);
        builder.beginObserveMultipleCreatePrefix();
        emitCurrentActivation(builder);
        builder.emitLoadConstant(identities.size());
        builder.emitLoadLocal(source);
        builder.endObserveMultipleCreatePrefix();
        builder.endStoreLocal();
        BytecodeLocal element = builder.createLocal("multipleCreateElement", null);
        for (int index = 0; index < identities.size(); index++) {
            builder.beginStoreLocal(element);
            builder.beginObservedMultipleCreateValue();
            builder.emitLoadLocal(observed);
            builder.emitLoadConstant(index);
            builder.endObservedMultipleCreateValue();
            builder.endStoreLocal();
            builder.beginDiscardValue();
            emitStoreBinding(builder, identities.get(index), element);
            builder.endDiscardValue();
        }
        builder.emitLoadLocal(source);
        builder.endBlock();
    }

    /**
     * PERF037-E: value expressions that can be emitted inside Return without
     * invoking the composed-expression machinery. Keep Closure materialization
     * on its established path because it may lower nested roots first.
     */
    private static boolean isDirectTerminalValue(
            CanonicalExpression expression) {
        if (expression instanceof CanonicalLiteral
                || expression instanceof CanonicalLookup
                || expression instanceof CanonicalIntrinsic) {
            return true;
        }
        if (expression instanceof CanonicalMember member) {
            return isDirectTerminalValue(member.receiver());
        }
        if (expression instanceof CanonicalIdentity identity) {
            return isDirectTerminalValue(identity.left())
                    && isDirectTerminalValue(identity.right());
        }
        if (expression instanceof CanonicalNotIdentity identity) {
            return isDirectTerminalValue(identity.left())
                    && isDirectTerminalValue(identity.right());
        }
        return false;
    }

    /**
     * A simple prefix can consume each evaluated result directly. Preserve
     * the established scalar lane and composed-control lowering otherwise.
     */
    private record ComposedSequenceScratch(
            BytecodeLocal preparedCall,
            BytecodeLocal childResult,
            BytecodeLocal resumeValue) {}

    private ComposedSequenceScratch createComposedSequenceScratch(
            ProtosBytecodeRootNodeGen.Builder builder,
            java.util.List<CanonicalExpression> expressions) {
        boolean needed =
                expressions.stream()
                        .anyMatch(expression ->
                                requiresComposedInvocation(expression)
                                        && !(expression instanceof CanonicalAssign assign
                                                && isScalarLocalAssign(assign))
                                        && !admitsCompactClosureCreate(expression));
        if (!needed) {
            return null;
        }
        return new ComposedSequenceScratch(
                builder.createLocal("preparedClosureCall", null),
                builder.createLocal("childResult", null),
                builder.createLocal("resumeValue", null));
    }

    private void emitDiscardedPrefix(
            ProtosBytecodeRootNodeGen.Builder builder,
            java.util.List<CanonicalExpression> expressions) {
        emitDiscardedPrefix(
                builder,
                expressions,
                createComposedSequenceScratch(builder, expressions));
    }

    private void emitDiscardedPrefix(
            ProtosBytecodeRootNodeGen.Builder builder,
            java.util.List<CanonicalExpression> expressions,
            ComposedSequenceScratch scratch) {
        BytecodeLocal preparedCall =
                scratch == null ? null : scratch.preparedCall();
        BytecodeLocal childResult =
                scratch == null ? null : scratch.childResult();
        BytecodeLocal resumeValue =
                scratch == null ? null : scratch.resumeValue();
        BytecodeLocal stagedResult = null;

        for (CanonicalExpression expression : expressions) {
            boolean directDiscard =
                    isDirectTerminalValue(expression)
                            && !requiresComposedInvocation(expression)
                            && !(expression instanceof CanonicalLookup lookup
                                    && isScalarLocalRead(lookup));

            if (directDiscard) {
                beginStatement(builder, expression);
                builder.beginDiscardValue();
                emitExpression(builder, expression);
                builder.endDiscardValue();
                endStatement(builder);
            } else if (expression instanceof CanonicalLookup lookup
                    && isScalarLocalRead(lookup)) {
                beginStatement(builder, expression);
                emitDiscardedScalarLocalRead(builder, lookup);
                endStatement(builder);
            } else if (admitsCompactClosureCreate(expression)) {
                emitCompactClosureCreate(builder, (CanonicalCreate) expression);
            } else if (expression instanceof CanonicalClosure closure) {
                /*
                 * Register the nested root before opening DiscardValue.
                 * Materialization still executes at the original statement.
                 */
                beginStatement(builder, expression);
                ProtosClosureExecutionPlanCell plan =
                        bytecodeClosurePlan(builder, closure);
                builder.beginDiscardValue();
                emitClosureCapture(builder, closure, plan);
                builder.endDiscardValue();
                endStatement(builder);
            } else {
                /*
                 * A composed/control expression keeps its existing lowering.
                 * Allocate scratch storage only if one actually occurs.
                 * Direct expressions on either side still avoid result stores.
                 */
                if (stagedResult == null) {
                    stagedResult =
                            builder.createLocal("sequenceResult", null);
                }
                beginStatement(builder, expression);
                if (requiresComposedInvocation(expression)) {
                    emitBodyExpressionToLocal(
                            builder,
                            expression,
                            stagedResult,
                            preparedCall,
                            childResult,
                            resumeValue);
                } else {
                    builder.beginStoreLocal(stagedResult);
                    emitExpression(builder, expression);
                    builder.endStoreLocal();
                }
                endStatement(builder);
            }
        }
    }

    private void emitDiscardedScalarLocalRead(
            ProtosBytecodeRootNodeGen.Builder builder,
            CanonicalLookup lookup) {
        BytecodeLocal local =
                currentRootFrameLocals.get(lookup.name());
        if (local == null) {
            throw new AssertionError(
                    "admitted scalar local is missing: " + lookup.name());
        }
        builder.beginDiscardValue();
        builder.emitLoadLocal(local);
        builder.endDiscardValue();
    }

    /**
     * I112: a statement {@code name: <Closure literal>} creating a LOCAL or
     * CELL binding of the genuine root being lowered. Its value cannot
     * suspend or observe the binding, so the creation consumes the Closure
     * value directly, without a value local or staged sequence result. The
     * rule is structural; every other creation keeps {@link #emitBodyCreate}.
     */
    private boolean admitsCompactClosureCreate(CanonicalExpression expression) {
        if (!(expression instanceof CanonicalCreate create)
                || create.target().isPresent()
                || !(create.value() instanceof CanonicalClosure)
                || currentActivationLocal != null
                || !currentNormalCallbackScalarLocals.isEmpty()
                || currentRootScalarLocals.contains(create.name())
                || currentRootAnalysis == null) {
            return false;
        }
        CanonicalBindingIdentity identity =
                currentRootAnalysis.identityOf(create).orElse(null);
        return identity != null
                && identity.owner() == currentRootTopScope
                && currentRootFrameLocals.containsKey(create.name());
    }

    /**
     * I112: one admitted {@link #admitsCompactClosureCreate} statement. The
     * nested root and its plan cell are registered before any consuming
     * operation opens; the Closure is created at the statement and its
     * identity is stored directly in the binding's classified storage
     * (GraalPy {@code store.local} or {@code store.cell}). The statement
     * value is discarded by the enclosing sequence.
     */
    private void emitCompactClosureCreate(
            ProtosBytecodeRootNodeGen.Builder builder,
            CanonicalCreate create) {
        CanonicalClosure closure = (CanonicalClosure) create.value();
        beginStatement(builder, create);
        ProtosClosureExecutionPlanCell plan = bytecodeClosurePlan(builder, closure);
        CanonicalBindingIdentity identity =
                currentRootAnalysis.identityOf(create).orElseThrow();
        BytecodeLocal cell = cellLocalOrNull(identity);
        if (cell != null) {
            builder.beginStoreCell();
            builder.emitLoadLocal(cell);
            emitClosureCapture(builder, closure, plan);
            builder.endStoreCell();
        } else {
            builder.beginStoreLocal(ownLocalOrNull(identity));
            emitClosureCapture(builder, closure, plan);
            builder.endStoreLocal();
        }
        endStatement(builder);
    }

    private void emitClosureCapture(
            ProtosBytecodeRootNodeGen.Builder builder,
            CanonicalClosure closure,
            ProtosClosureExecutionPlanCell plan) {
        java.util.List<CanonicalBindingIdentity> cells = beginClosureCapture(builder, closure);
        builder.emitLoadConstant(closure);
        builder.emitLoadConstant(plan);
        endClosureCapture(builder, cells);
    }

    /**
     * I112 / PLAT062-T: whether {@code last}, the final expression of the
     * genuine root being lowered, is a terminal Closure invocation: a
     * {@link CanonicalCall} after which the root only returns its value,
     * whose receiver and positional arguments are direct values that can be
     * evaluated once, left to right, as operands of the terminal operation.
     * Terminality is a property of this emission point, never a proof that
     * the invocation cannot suspend.
     *
     * <p>Not admitted, and therefore lowered unchanged: spread arguments,
     * composed or control operands, and every inline callback or activation
     * region, whose enclosing work must still resume physically.
     *
     * <p>PERF042 / PLAT066-C: PLAT057-B permitted proven inlining during
     * lowering, and an I096 candidate other than a lexical one-argument
     * binding call (an immediate Closure literal, a C0 or C2/N binding call)
     * keeps that inline lowering. For an ordinary terminal C1 call whose
     * receiver is the lexical binding of a known Closure, the approved
     * direction is to keep the callee's physical CallTarget: the existence of
     * an I096 candidate does not justify expanding that call in the caller,
     * so it is admitted here and Graal decides inlining between CallTargets.
     * This compact admission is not a proof that the call cannot suspend, and
     * the terminal operation keeps the dynamic D013 selection and its
     * fallbacks. The exception covers only this scope; it does not presume a
     * general retirement of PLAT057-B.
     */
    private boolean admitsTerminalClosureCall(CanonicalExpression last) {
        if (!(last instanceof CanonicalCall call)
                || currentActivationLocal != null
                || currentInlineClosureCallBody
                || !currentNormalCallbackScalarLocals.isEmpty()
                || hasVectorArgument(call.arguments())
                || !isTerminalCallOperand(call.receiver())) {
            return false;
        }
        boolean lexicalC1BindingCandidate =
                call.arguments().size() == 1
                        && call.receiver() instanceof CanonicalLookup
                        && currentRootBindingCallCandidates.containsKey(call);
        // Decided before the prefix materializes its literals: every other
        // I096 candidate keeps its inline lowering.
        if (!lexicalC1BindingCandidate
                && currentRootBindingCallCandidates.containsKey(call)) {
            return false;
        }
        for (CanonicalExpression argument : call.arguments()) {
            if (!isTerminalCallOperand(argument)) {
                return false;
            }
        }
        return true;
    }

    /**
     * PERF038-I: an immediate method Send1 that is the final expression of a
     * genuine source root, with both operands direct terminal values (so
     * nothing composed remains after the invocation). Primitive numeric
     * operator sends keep their established Integer lowering; every other
     * selector is admitted regardless of its name.
     */
    private boolean admitsTerminalMethodSend(CanonicalExpression last) {
        if (!(last instanceof CanonicalSend send)
                || currentActivationLocal != null
                || currentInlineClosureCallBody
                || !currentNormalCallbackScalarLocals.isEmpty()
                || send.arguments().size() != 1
                || hasSpreadArgument(send.arguments())
                || primitiveNumericSendCandidate(send)) {
            return false;
        }
        return isTerminalCallOperand(send.receiver())
                && isTerminalCallOperand(send.arguments().get(0));
    }


    /**
     * I115: a terminal call or send whose operands are direct terminal
     * values: the same arity-selected {@code Call*Method} (terminal form) of
     * the shared dispatcher; a send first selects through {@code GetMethod}
     * on the receiver, evaluated once. A suspended callee returns the
     * terminal transport.
     */
    private void emitCall(
            ProtosBytecodeRootNodeGen.Builder builder,
            CanonicalExpression expression) {

        currentRootEmittedTerminalInvocation = true;

        SourceSpan span = expression instanceof CanonicalCall call
                ? call.span()
                : ((CanonicalSend) expression).span();

        builder.beginSourceSection(span.startOffset(), span.length());
        builder.beginTag(StandardTags.CallTag.class);

        // GraalPy RootNodeCompiler.emitCall: emit unary operands directly.
        // No intermediate BytecodeLocal stores or reloads are required.
        if (expression instanceof CanonicalCall call
                && call.arguments().size() == 1) {
            builder.beginCallUnaryMethod(true);
            emitTerminalCallOperand(builder, call.receiver());
            emitTerminalCallOneArgument(builder, call.arguments().get(0));
            builder.endCallUnaryMethod();
            builder.endTag(StandardTags.CallTag.class);
            builder.endSourceSection();
            return;
        }

        builder.beginBlock();

        BytecodeLocal callable = builder.createLocal("terminalCallable", null);
        java.util.List<CanonicalExpression> arguments;
        if (expression instanceof CanonicalCall call) {
            arguments = call.arguments();
            builder.beginStoreLocal(callable);
            emitTerminalCallOperand(builder, call.receiver());
            builder.endStoreLocal();
        } else if (expression instanceof CanonicalSend send) {
            arguments = send.arguments();
            BytecodeLocal receiver = builder.createLocal("terminalReceiver", null);
            builder.beginStoreLocal(receiver);
            emitTerminalCallOperand(builder, send.receiver());
            builder.endStoreLocal();
            emitGetMethod(builder, send.message(), receiver, callable);
        } else {
            throw new AssertionError(
                    "Unsupported fixed-arity call: " + expression.getClass());
        }
        java.util.List<BytecodeLocal> argumentValues = new java.util.ArrayList<>(arguments.size());
        for (CanonicalExpression argument : arguments) {
            BytecodeLocal argumentValue = builder.createLocal("terminalArgument", null);
            builder.beginStoreLocal(argumentValue);
            emitTerminalCallOneArgument(builder, argument);
            builder.endStoreLocal();
            argumentValues.add(argumentValue);
        }
        // The block's value: the terminal call result or transport.
        emitCallOperation(builder, true, callable, argumentValues, null);

        builder.endBlock();
        builder.endTag(StandardTags.CallTag.class);
        builder.endSourceSection();
    }


    /**
     * I115 EXPERIMENTAL: whether the terminal Call1 invokes an ordinary
     * Closure local created exactly once in this root's Sequence by a
     * Closure that neither captures its creator's context nor may observe
     * its ReturnHome. Only such a root runs the experimental callee context
     * and returns the call result without carrier projection. Decided from
     * compilation facts alone, never from a name.
     */
    private boolean isExperimentalLocalUnaryClosureCall(
            CanonicalSequence sequence,
            CanonicalExpression last) {
        if (!(last instanceof CanonicalCall call)
                || call.arguments().size() != 1
                || !(call.receiver() instanceof CanonicalLookup lookup)
                || !currentRootFrameLocals.containsKey(lookup.name())
                || currentRootTopScope.needsCell(lookup.name())) {
            return false;
        }
        CanonicalClosure created = null;
        int creations = 0;
        for (CanonicalExpression expression : sequence.expressions()) {
            if (expression instanceof CanonicalCreate create
                    && create.name().equals(lookup.name())) {
                creations++;
                created = create.value() instanceof CanonicalClosure closure
                        ? closure
                        : null;
            }
        }
        return creations == 1
                && created != null
                && !CanonicalCompilationFacts.capturesCreatorContext(
                        created, currentRootAnalysis)
                && !compilationFacts.mayObserveReturnHome(created);
    }

    private static boolean isTerminalCallOperand(CanonicalExpression expression) {
        return isDirectTerminalValue(expression)
                && !requiresComposedInvocation(expression);
    }

    /** The single Call1 argument; a number literal stays a primitive carrier. */
    private void emitTerminalCallOneArgument(
            ProtosBytecodeRootNodeGen.Builder builder,
            CanonicalExpression argument) {
        Object carrier =
                argument instanceof CanonicalLiteral literal
                        ? primitiveNumberLiteralOrNull(literal)
                        : null;
        if (carrier != null) {
            builder.emitLoadConstant(carrier);
        } else {
            emitTerminalCallOperand(builder, argument);
        }
    }

    private void emitTerminalCallOperand(
            ProtosBytecodeRootNodeGen.Builder builder,
            CanonicalExpression operand) {
        if (operand instanceof CanonicalLookup lookup) {
            emitLookup(builder, lookup, true);
        } else {
            emitExpression(builder, operand);
        }
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

    /** I143: labels explicitly supplied at a source call site. */
    private static boolean hasNamedArgument(
            java.util.List<CanonicalExpression> arguments) {
        return arguments.stream()
                .anyMatch(CanonicalNamedArgument.class::isInstance);
    }

    /** Either form requires the source-ordered argument vector. */
    private static boolean hasVectorArgument(
            java.util.List<CanonicalExpression> arguments) {
        return hasSpreadArgument(arguments)
                || hasNamedArgument(arguments);
    }

    private static CanonicalExpression suppliedArgumentExpression(
            CanonicalExpression argument) {
        if (argument instanceof CanonicalSpread spread) {
            return spread.expression();
        }
        if (argument instanceof CanonicalNamedArgument named) {
            return named.expression();
        }
        return argument;
    }

    private void emitBodySpreadArgumentVector(
            ProtosBytecodeRootNodeGen.Builder builder,
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
                {
                    builder.beginAppendSpreadSuppliedArgument();
                }
                builder.emitLoadLocal(suppliedVector);
                builder.emitLoadLocal(value);
                emitSendCallerOperand(builder);
                {
                    builder.endAppendSpreadSuppliedArgument();
                }
            } else {
                builder.beginAppendSuppliedArgument();
                builder.emitLoadLocal(suppliedVector);
                builder.emitLoadLocal(value);
                builder.endAppendSuppliedArgument();
            }
        }
    }

    private void emitDefaultSpreadArgumentVector(
            ProtosBytecodeRootNodeGen.Builder builder,
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
                {
                    builder.beginAppendSpreadSuppliedArgument();
                }
                builder.emitLoadLocal(suppliedVector);
                builder.emitLoadLocal(value);
                emitSendCallerOperand(builder);
                {
                    builder.endAppendSpreadSuppliedArgument();
                }
            } else {
                builder.beginAppendSuppliedArgument();
                builder.emitLoadLocal(suppliedVector);
                builder.emitLoadLocal(value);
                builder.endAppendSuppliedArgument();
            }
        }
    }

    private void emitBodyExpressionToLocal(
            ProtosBytecodeRootNodeGen.Builder builder,
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
            beginMemberRead(builder, member.name());
            builder.emitLoadLocal(receiverValue);
            endMemberRead(builder);
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
            builder.beginIdentity(false);
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
            builder.beginIdentity(true);
            builder.emitLoadLocal(leftValue);
            builder.emitLoadLocal(rightValue);
            builder.endIdentity();
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
        if (expression instanceof CanonicalLookup lookup
                && isScalarLocalRead(lookup)) {
            // PERF034-E: an admitted scalar RHS re-checks compactness itself.
            emitScalarLocalReadToResult(builder, lookup, target);
            return;
        }
        builder.beginStoreLocal(target);
        emitExpression(builder, expression);
        builder.endStoreLocal();
    }

    private void emitDefaultExpressionToLocal(
            ProtosBytecodeRootNodeGen.Builder builder,
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
            beginMemberRead(builder, member.name());
            builder.emitLoadLocal(receiverValue);
            endMemberRead(builder);
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
            builder.beginIdentity(false);
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
            builder.beginIdentity(true);
            builder.emitLoadLocal(leftValue);
            builder.emitLoadLocal(rightValue);
            builder.endIdentity();
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
            ProtosBytecodeRootNodeGen.Builder builder,
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
            ProtosBytecodeRootNodeGen.Builder builder,
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
            ProtosBytecodeRootNodeGen.Builder builder,
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
            ProtosBytecodeRootNodeGen.Builder builder,
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
            ProtosBytecodeRootNodeGen.Builder builder,
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
        builder.beginMapConstructionFactoryCall(false);
        emitCurrentActivation(builder);
        builder.emitLoadLocal(factoryValue);
        builder.endMapConstructionFactoryCall();
        builder.endStoreLocal();

        emitRequestInvocation(
                builder,
                factoryResult,
                preparedCall,
                childResult,
                resumeValue);

        builder.beginStoreLocal(mapValue);
        builder.beginMapConstructionFactoryCall(true);
        emitCurrentActivation(builder);
        builder.emitLoadLocal(factoryResult);
        builder.endMapConstructionFactoryCall();
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

            builder.beginStructuredAdvance(ProtosStructuredPhase.ENTER_COMPARISON);
            builder.emitLoadLocal(initialDefinition);
            builder.endStructuredAdvance();

            builder.beginTryFinally(
                    () -> {
                        builder.beginStructuredAdvance(ProtosStructuredPhase.LEAVE_COMPARISON);
                        builder.emitLoadLocal(initialDefinition);
                        builder.endStructuredAdvance();
                    });

            builder.beginBlock();

            builder.beginStoreLocal(preparedCall);
            builder.beginStructuredPrepareChildCall(ProtosStructuredPhase.HASH);
            builder.emitLoadLocal(initialDefinition);
            builder.endStructuredPrepareChildCall();
            builder.endStoreLocal();

            emitRequestInvocation(
                    builder,
                    callbackResult,
                    preparedCall,
                    childResult,
                    resumeValue);

            builder.endBlock();
            builder.endTryFinally();

            builder.beginStructuredAcceptChildOutcome(ProtosStructuredPhase.HASH);
            builder.emitLoadLocal(initialDefinition);
            builder.emitLoadLocal(callbackResult);
            builder.endStructuredAcceptChildOutcome();

            builder.beginWhile();

            builder.beginStructuredHasNext(ProtosStructuredPhase.EQUALITY);
            builder.emitLoadLocal(initialDefinition);
            builder.endStructuredHasNext();

            builder.beginBlock();

            builder.beginStructuredAdvance(ProtosStructuredPhase.ENTER_COMPARISON);
            builder.emitLoadLocal(initialDefinition);
            builder.endStructuredAdvance();

            builder.beginTryFinally(
                    () -> {
                        builder.beginStructuredAdvance(ProtosStructuredPhase.LEAVE_COMPARISON);
                        builder.emitLoadLocal(initialDefinition);
                        builder.endStructuredAdvance();
                    });

            builder.beginBlock();

            builder.beginStoreLocal(preparedCall);
            builder.beginStructuredPrepareChildCall(ProtosStructuredPhase.EQUALITY);
            builder.emitLoadLocal(initialDefinition);
            builder.endStructuredPrepareChildCall();
            builder.endStoreLocal();

            emitRequestInvocation(
                    builder,
                    callbackResult,
                    preparedCall,
                    childResult,
                    resumeValue);

            builder.endBlock();
            builder.endTryFinally();

            builder.beginStructuredAcceptChildOutcome(ProtosStructuredPhase.EQUALITY);
            builder.emitLoadLocal(initialDefinition);
            builder.emitLoadLocal(callbackResult);
            builder.endStructuredAcceptChildOutcome();

            builder.endBlock();
            builder.endWhile();

            builder.beginStructuredAdvance(ProtosStructuredPhase.STEP);
            builder.emitLoadLocal(initialDefinition);
            builder.endStructuredAdvance();
        }

        builder.beginStoreLocal(result);
        builder.emitLoadLocal(mapValue);
        builder.endStoreLocal();
    }

    private void emitBodyObjectLiteral(
            ProtosBytecodeRootNodeGen.Builder builder,
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
            ProtosBytecodeRootNodeGen.Builder builder,
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
            ProtosBytecodeRootNodeGen.Builder builder,
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
        builder.emitLoadConstant(
                new ProtosBytecodeRootNode.ComposeReservedNames(composeReservedNames(compose)));
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
     * <p>The body is not a lexical capture scope. I115: it keeps the
     * enclosing root's analysis and storage, so a reference to an enclosing
     * Closure binding uses that binding's own LOCAL or CELL storage, Closures
     * nested in the body capture cells from the enclosing root, and the
     * body's own bindings are MEMBER bindings created through its
     * construction activation.
     */
    private void emitInlineObjectConstruction(
            ProtosBytecodeRootNodeGen.Builder builder,
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
            BytecodeLocal savedActivationLocal = currentActivationLocal;
            try {
                currentActivationLocal = constructionActivation;
                BytecodeLocal bodyResult =
                        builder.createLocal("objectBodyResult", null);
                emitStatementsToLocal(builder, object.body(), bodyResult);
            } finally {
                currentActivationLocal = savedActivationLocal;
            }
        }

        builder.beginStoreLocal(result);
        builder.emitLoadLocal(constructed);
        builder.endStoreLocal();
    }

    private void emitBodyCreate(
            ProtosBytecodeRootNodeGen.Builder builder,
            CanonicalCreate create,
            BytecodeLocal result,
            BytecodeLocal preparedCall,
            BytecodeLocal childResult,
            BytecodeLocal resumeValue) {
        if (!currentNormalCallbackScalarLocals.isEmpty()) {
            BytecodeLocal own = currentNormalCallbackScalarLocals.get(create.name());
            CanonicalBindingIdentity identity =
                    currentNormalCallbackScalarAnalysis.identityOf(create).orElse(null);
            if (create.target().isPresent()
                    || own == null
                    || identity == null
                    || identity.owner() != currentNormalCallbackScalarScope) {
                throw new AssertionError(
                        "I095-B6a unproven callback creation: " + create.name());
            }
            builder.beginStoreLocal(own);
            emitExpression(builder, create.value());
            builder.endStoreLocal();

            builder.beginStoreLocal(result);
            builder.emitLoadLocal(own);
            builder.endStoreLocal();
            return;
        }
        boolean scalarCreate =
                create.target().isEmpty()
                        && currentActivationLocal == null
                        && currentRootScalarLocals.contains(create.name());
        if (scalarCreate
                && create.value() instanceof CanonicalLiteral literal) {
            // PERF034-E: a literal is its own operand; no createValue local.
            emitScalarCreate(
                    builder,
                    create,
                    result,
                    () -> emitLiteral(builder, literal));
            return;
        }
        BytecodeLocal value = builder.createLocal("createValue", null);
        if (create.target().isEmpty()) {
            /*
             * I091: a LOCAL creation may retain a primitive carrier from the
             * guarded numeric chain; its own value is a guest value. A cell
             * holds guest values only.
             */
            CanonicalBindingIdentity identity =
                    currentRootAnalysis == null
                            ? null
                            : currentRootAnalysis.identityOf(create).orElse(null);
            boolean carrierCreate =
                    !scalarCreate
                            && currentActivationLocal == null
                            && identity != null
                            && CanonicalBindingAnalysis.storageOf(identity)
                                    == CanonicalLexicalScope.Storage.LOCAL
                            && primitiveNumericCarrierValue(create.value());
            if (carrierCreate) {
                emitPrimitiveNumericOperandToLocal(
                        builder, create.value(), value, preparedCall, childResult, resumeValue);
                builder.beginStoreLocal(result);
                builder.beginMaterializePrimitiveNumericCarrier();
                emitCreateCurrentBinding(builder, create, value);
                builder.emitLoadConstant(true);
                builder.endMaterializePrimitiveNumericCarrier();
                builder.endStoreLocal();
                return;
            }
            emitBodyExpressionToLocal(
                    builder, create.value(), value, preparedCall, childResult, resumeValue);

            if (scalarCreate) {
                emitScalarCreate(
                        builder,
                        create,
                        result,
                        () -> builder.emitLoadLocal(value));
                return;
            }

            builder.beginStoreLocal(result);
            emitCreateCurrentBinding(builder, create, value);
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
        builder.beginMutateLocalSlot(ProtosSlotMutation.CREATE);
        emitCurrentActivation(builder);
        builder.emitLoadLocal(mutationTarget);
        builder.emitLoadConstant(create.name());
        builder.emitLoadLocal(value);
        builder.endMutateLocalSlot();
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
            ProtosBytecodeRootNodeGen.Builder builder,
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
        emitMultipleCreateCurrentBindings(
                builder,
                create.names(),
                currentRootAnalysis == null
                        ? null
                        : currentRootAnalysis.identitiesOf(create).orElse(null),
                source);
        builder.endStoreLocal();
    }

    private void emitBodyAssign(
            ProtosBytecodeRootNodeGen.Builder builder,
            CanonicalAssign assign,
            BytecodeLocal result,
            BytecodeLocal preparedCall,
            BytecodeLocal childResult,
            BytecodeLocal resumeValue) {
        if (!currentNormalCallbackScalarLocals.isEmpty()
                && currentNormalCallbackScalarLocals.containsKey(assign.name())) {
            BytecodeLocal own = currentNormalCallbackScalarLocals.get(assign.name());
            CanonicalBindingResolution resolution =
                    currentNormalCallbackScalarAnalysis.resolutionOf(assign)
                            .orElse(null);
            if (assign.target().isPresent()
                    || own == null
                    || !(resolution instanceof CanonicalBindingResolution.Resolved resolved)
                    || resolved.identity().owner() != currentNormalCallbackScalarScope) {
                throw new AssertionError(
                        "I095-B6a unproven callback assignment: " + assign.name());
            }
            builder.beginStoreLocal(own);
            emitExpression(builder, assign.value());
            builder.endStoreLocal();

            builder.beginStoreLocal(result);
            builder.emitLoadLocal(own);
            builder.endStoreLocal();
            return;
        }
        if (isScalarLocalAssign(assign)) {
            emitScalarAssign(
                    builder, assign, result, preparedCall, childResult, resumeValue);
            return;
        }
        emitResolvedBodyAssign(
                builder, assign, result, preparedCall, childResult, resumeValue);
    }

    /**
     * PERF036-A: whether {@code assign} is an admitted scalar assignment of
     * the root being lowered (outside any inline activation region). Within
     * an admitted root every bare assignment was proven by {@link
     * #scalarLocalNamesForRoot}.
     */
    private boolean isScalarLocalAssign(CanonicalAssign assign) {
        return currentNormalCallbackScalarLocals.isEmpty()
                && assign.target().isEmpty()
                && currentActivationLocal == null
                && currentRootScalarLocals.contains(assign.name());
    }

    /**
     * I115 / GraalPy store.local: write an admitted scalar binding directly.
     *
     * <p>KNOWN I115 SEMANTIC DIVERGENCE (D179 C0, PERF036-A): a published
     * context may remove/recreate the binding or change the lexical write
     * destination. This direct write intentionally ignores those operations
     * under the I115 assumption; there is no authoritative fallback.
     */
    private void emitScalarAssign(
            ProtosBytecodeRootNodeGen.Builder builder,
            CanonicalAssign assign,
            BytecodeLocal result,
            BytecodeLocal preparedCall,
            BytecodeLocal childResult,
            BytecodeLocal resumeValue) {
        BytecodeLocal local = currentRootFrameLocals.get(assign.name());
        if (local == null) {
            throw new AssertionError(
                    "admitted scalar assignment is missing: " + assign.name());
        }
        builder.beginStoreLocal(local);
        if (assign.value() instanceof CanonicalLiteral literal) {
            emitLiteral(builder, literal);
        } else if (assign.value() instanceof CanonicalLookup read
                && isScalarLocalRead(read)) {
            builder.emitLoadLocal(currentRootFrameLocals.get(read.name()));
        } else {
            throw new AssertionError(
                    "admitted scalar assignment has an unproven value: "
                            + assign.value().getClass().getSimpleName());
        }
        builder.endStoreLocal();
        builder.beginStoreLocal(result);
        builder.emitLoadLocal(local);
        builder.endStoreLocal();
    }

    /**
     * Emits a bare or member assignment outside the admitted scalar lane.
     *
     * <p>I115: a bare assignment classified to a LOCAL or CELL binding
     * evaluates its RHS once and stores it directly into that storage
     * (GraalPy {@code store.local}/{@code store.cell}). A NAME binding
     * (module, object body or dynamic name) keeps one name operation that
     * selects its destination before the RHS, then assigns it. A member
     * assignment keeps the existing member operations.
     *
     * <p>KNOWN I115 SEMANTIC DIVERGENCE (D179 C0, PERF028-A): a LOCAL/CELL
     * destination is fixed at lowering time. It is not re-selected before the
     * RHS, so a removal, recreation or nearer late creation of the name
     * through an observed context is not observed by the assignment.
     */
    private void emitResolvedBodyAssign(
            ProtosBytecodeRootNodeGen.Builder builder,
            CanonicalAssign assign,
            BytecodeLocal result,
            BytecodeLocal preparedCall,
            BytecodeLocal childResult,
            BytecodeLocal resumeValue) {
        BytecodeLocal value = builder.createLocal("assignValue", null);
        CanonicalBindingIdentity identity = assignmentIdentityOrNull(assign);
        if (emitUninitializedLocalAssign(builder, assign, identity, result)) {
            return;
        }
        if (identity != null) {
            BytecodeLocal cell = cellLocalOrNull(identity);
            BytecodeLocal local = cell == null ? ownLocalOrNull(identity) : null;
            if (cell != null || local != null) {
                boolean carrier = local != null && primitiveNumericCarrierValue(assign.value());
                if (carrier) {
                    emitPrimitiveNumericOperandToLocal(
                            builder, assign.value(), value, preparedCall, childResult, resumeValue);
                } else {
                    emitBodyExpressionToLocal(
                            builder, assign.value(), value, preparedCall, childResult, resumeValue);
                }
                emitStoreAssignedValue(builder, cell, local, value, result, carrier);
                return;
            }
        }

        BytecodeLocal mutationTarget = builder.createLocal("assignMutationTarget", null);
        if (assign.target().isPresent()) {
            BytecodeLocal rawTarget = builder.createLocal("assignRawTarget", null);
            emitBodyExpressionToLocal(
                    builder,
                    assign.target().orElseThrow(),
                    rawTarget,
                    preparedCall,
                    childResult,
                    resumeValue);
            emitRequireMutationTarget(builder, mutationTarget, rawTarget);
        } else {
            emitResolveNamedWriteTarget(builder, assign, mutationTarget);
        }
        emitBodyExpressionToLocal(
                builder, assign.value(), value, preparedCall, childResult, resumeValue);
        emitNamedOrMemberAssign(builder, assign, mutationTarget, value, result);
    }

    /*
     * I115: an assignment to a LOCAL binding before its binding point selects
     * no destination; it signals the uninitialized-binding Error before the
     * RHS evaluates, exactly where a destination would have been selected.
     */
    private boolean emitUninitializedLocalAssign(
            ProtosBytecodeRootNodeGen.Builder builder,
            CanonicalAssign assign,
            CanonicalBindingIdentity identity,
            BytecodeLocal result) {
        if (identity == null
                || !isUninitializedLocalAccess(
                        currentRootAnalysis.resolutionOf(assign).orElse(null), identity)) {
            return false;
        }
        builder.beginStoreLocal(result);
        builder.emitUninitializedBinding();
        builder.endStoreLocal();
        return true;
    }

    /** I115: the static identity of a bare assignment, or {@code null}. */
    private CanonicalBindingIdentity assignmentIdentityOrNull(CanonicalAssign assign) {
        if (assign.target().isPresent() || currentRootAnalysis == null) {
            return null;
        }
        return staticIdentityOrNull(currentRootAnalysis.resolutionOf(assign).orElse(null));
    }

    /*
     * I115: stores the already evaluated RHS into exactly one LOCAL or CELL
     * storage; the assignment yields the guest value of the RHS.
     */
    private static void emitStoreAssignedValue(
            ProtosBytecodeRootNodeGen.Builder builder,
            BytecodeLocal cell,
            BytecodeLocal local,
            BytecodeLocal value,
            BytecodeLocal result,
            boolean carrier) {
        if (cell != null) {
            builder.beginStoreCell();
            builder.emitLoadLocal(cell);
            builder.emitLoadLocal(value);
            builder.endStoreCell();
        } else {
            builder.beginStoreLocal(local);
            builder.emitLoadLocal(value);
            builder.endStoreLocal();
        }
        builder.beginStoreLocal(result);
        if (carrier) {
            builder.beginMaterializePrimitiveNumericCarrier();
        }
        builder.emitLoadLocal(value);
        if (carrier) {
            builder.emitLoadConstant(true);
            builder.endMaterializePrimitiveNumericCarrier();
        }
        builder.endStoreLocal();
    }

    private void emitRequireMutationTarget(
            ProtosBytecodeRootNodeGen.Builder builder,
            BytecodeLocal mutationTarget,
            BytecodeLocal rawTarget) {
        builder.beginStoreLocal(mutationTarget);
        builder.beginRequireObjectMutationTarget();
        emitCurrentActivation(builder);
        builder.emitLoadLocal(rawTarget);
        builder.endRequireObjectMutationTarget();
        builder.endStoreLocal();
    }

    /* The NAME destination is selected before the RHS evaluates. */
    private void emitResolveNamedWriteTarget(
            ProtosBytecodeRootNodeGen.Builder builder,
            CanonicalAssign assign,
            BytecodeLocal mutationTarget) {
        builder.beginStoreLocal(mutationTarget);
        builder.beginAccessNamedLexical(ProtosNamedLexicalAccess.RESOLVE_WRITE);
        emitCurrentActivation(builder);
        builder.emitLoadConstant(assign.name());
        builder.endAccessNamedLexical();
        builder.endStoreLocal();
    }

    private void emitNamedOrMemberAssign(
            ProtosBytecodeRootNodeGen.Builder builder,
            CanonicalAssign assign,
            BytecodeLocal mutationTarget,
            BytecodeLocal value,
            BytecodeLocal result) {
        builder.beginStoreLocal(result);
        builder.beginMutateLocalSlot(
                assign.target().isPresent()
                        ? ProtosSlotMutation.ASSIGN
                        : ProtosSlotMutation.ASSIGN_RESOLVED);
        emitCurrentActivation(builder);
        builder.emitLoadLocal(mutationTarget);
        builder.emitLoadConstant(assign.name());
        builder.emitLoadLocal(value);
        builder.endMutateLocalSlot();
        builder.endStoreLocal();
    }

    private void emitBodyIndexedAssign(
            ProtosBytecodeRootNodeGen.Builder builder,
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

        emitDispatchedSendOfLocals(
                builder, "atPut", receiver, java.util.List.of(index, value),
                dispatchResult, childResult, resumeValue);

        /* Indexed assignment returns the exact RHS, never the atPut result. */
        builder.beginStoreLocal(result);
        builder.emitLoadLocal(value);
        builder.endStoreLocal();
    }

    private void emitDefaultCreate(
            ProtosBytecodeRootNodeGen.Builder builder,
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
            emitCreateCurrentBinding(builder, create, value);
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
        builder.beginMutateLocalSlot(ProtosSlotMutation.CREATE);
        emitCurrentActivation(builder);
        builder.emitLoadLocal(mutationTarget);
        builder.emitLoadConstant(create.name());
        builder.emitLoadLocal(value);
        builder.endMutateLocalSlot();
        builder.endStoreLocal();
    }

    private void emitDefaultMultipleCreate(
            ProtosBytecodeRootNodeGen.Builder builder,
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
        emitMultipleCreateCurrentBindings(
                builder,
                create.names(),
                currentRootAnalysis == null
                        ? null
                        : currentRootAnalysis.identitiesOf(create).orElse(null),
                source);
        builder.endStoreLocal();
    }

    /*
     * I115: the parameter default-expression counterpart of
     * emitResolvedBodyAssign, with the same single-storage selection and the
     * same KNOWN I115 SEMANTIC DIVERGENCE (D179 C0, PERF028-A).
     */
    private void emitDefaultAssign(
            ProtosBytecodeRootNodeGen.Builder builder,
            CanonicalAssign assign,
            BytecodeLocal result,
            BytecodeLocal preparedCall,
            BytecodeLocal childResult,
            BytecodeLocal resumeValue) {
        BytecodeLocal value = builder.createLocal("defaultAssignValue", null);
        CanonicalBindingIdentity identity = assignmentIdentityOrNull(assign);
        if (emitUninitializedLocalAssign(builder, assign, identity, result)) {
            return;
        }
        if (identity != null) {
            BytecodeLocal cell = cellLocalOrNull(identity);
            BytecodeLocal local = cell == null ? ownLocalOrNull(identity) : null;
            if (cell != null || local != null) {
                emitDefaultExpressionToLocal(
                        builder, assign.value(), value, preparedCall, childResult, resumeValue);
                emitStoreAssignedValue(builder, cell, local, value, result, false);
                return;
            }
        }

        BytecodeLocal mutationTarget = builder.createLocal("defaultAssignMutationTarget", null);
        if (assign.target().isPresent()) {
            BytecodeLocal rawTarget = builder.createLocal("defaultAssignRawTarget", null);
            emitDefaultExpressionToLocal(
                    builder,
                    assign.target().orElseThrow(),
                    rawTarget,
                    preparedCall,
                    childResult,
                    resumeValue);
            emitRequireMutationTarget(builder, mutationTarget, rawTarget);
        } else {
            emitResolveNamedWriteTarget(builder, assign, mutationTarget);
        }
        emitDefaultExpressionToLocal(
                builder, assign.value(), value, preparedCall, childResult, resumeValue);
        emitNamedOrMemberAssign(builder, assign, mutationTarget, value, result);
    }

    private void emitDefaultIndexedAssign(
            ProtosBytecodeRootNodeGen.Builder builder,
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

        emitDispatchedSendOfLocals(
                builder, "atPut", receiver, java.util.List.of(index, value),
                dispatchResult, childResult, resumeValue);

        builder.beginStoreLocal(result);
        builder.emitLoadLocal(value);
        builder.endStoreLocal();
    }

    private void emitComposedSuperSend(
            ProtosBytecodeRootNodeGen.Builder builder,
            CanonicalSuperSend send,
            BytecodeLocal result,
            BytecodeLocal scratch,
            BytecodeLocal childResult,
            BytecodeLocal resumeValue) {
        emitDispatchedInvocation(
                builder, InvocationShape.SUPER_SEND, null, send.message(), send.arguments(),
                result, scratch, childResult, resumeValue,
                this::emitBodyExpressionToLocal, this::emitBodySpreadArgumentVector, false);
    }


    private void emitComposedDefaultSuperSend(
            ProtosBytecodeRootNodeGen.Builder builder,
            CanonicalSuperSend send,
            BytecodeLocal result,
            BytecodeLocal scratch,
            BytecodeLocal childResult,
            BytecodeLocal resumeValue) {
        builder.beginSourceSection(send.span().startOffset(), send.span().length());
        emitDispatchedInvocation(
                builder, InvocationShape.SUPER_SEND, null, send.message(), send.arguments(),
                result, scratch, childResult, resumeValue,
                this::emitDefaultExpressionToLocal, this::emitDefaultSpreadArgumentVector, false);
        builder.endSourceSection();
    }


    private void emitComposedDefaultCall(
            ProtosBytecodeRootNodeGen.Builder builder,
            CanonicalCall call,
            BytecodeLocal result,
            BytecodeLocal scratch,
            BytecodeLocal childResult,
            BytecodeLocal resumeValue) {
        builder.beginSourceSection(call.span().startOffset(), call.span().length());
        emitDispatchedInvocation(
                builder, InvocationShape.CALL, call.receiver(), null, call.arguments(),
                result, scratch, childResult, resumeValue,
                this::emitDefaultExpressionToLocal, this::emitDefaultSpreadArgumentVector, false);
        builder.endSourceSection();
    }


    private void emitComposedDefaultSend(
            ProtosBytecodeRootNodeGen.Builder builder,
            CanonicalSend send,
            BytecodeLocal result,
            BytecodeLocal scratch,
            BytecodeLocal childResult,
            BytecodeLocal resumeValue) {
        builder.beginSourceSection(send.span().startOffset(), send.span().length());
        emitDispatchedInvocation(
                builder, InvocationShape.SEND, send.receiver(), send.message(), send.arguments(),
                result, scratch, childResult, resumeValue,
                this::emitDefaultExpressionToLocal, this::emitDefaultSpreadArgumentVector, false);
        builder.endSourceSection();
    }


    /** I115: how an invocation operand is staged in the current lowering context. */
    @FunctionalInterface
    private interface OperandStager {
        void stage(
                ProtosBytecodeRootNodeGen.Builder builder,
                CanonicalExpression expression,
                BytecodeLocal target,
                BytecodeLocal scratch,
                BytecodeLocal childResult,
                BytecodeLocal resumeValue);
    }

    /** I115: how a spread argument vector is staged in the current lowering context. */
    @FunctionalInterface
    private interface SpreadStager {
        void stage(
                ProtosBytecodeRootNodeGen.Builder builder,
                java.util.List<CanonicalExpression> arguments,
                BytecodeLocal vector,
                BytecodeLocal scratch,
                BytecodeLocal childResult,
                BytecodeLocal resumeValue);
    }

    /** I115: the three invocation shapes of the canonical AST. */
    private enum InvocationShape { CALL, SEND, SUPER_SEND }

    /**
     * I115 (GraalPy call lowering): the single lowering of every ordinary
     * call, send and super send.
     *
     * <ol>
     *   <li>CALL: the callee expression is evaluated into the callable.
     *   <li>SEND: the receiver is evaluated once and kept; {@code GetMethod}
     *       selects the method on it (a bound method carrying the receiver
     *       and methodHome).
     *   <li>SUPER_SEND: {@code GetSuperMethod} selects from the parent of the
     *       current methodHome.
     *   <li>The arguments are evaluated left to right (a spread argument
     *       builds the positional vector).
     *   <li>{@code Call*Method} by arity (0..4, otherwise or with spread
     *       {@code CallVarargsMethod}) enters the shared dispatcher.
     *   <li>A pending result is composed: {@code IsResultKind(CONTINUATION)
     *       -> Yield -> ResumeDispatchedContinuation}.
     * </ol>
     * The call is wrapped once in {@link StandardTags.CallTag}.
     */
    private void emitDispatchedInvocation(
            ProtosBytecodeRootNodeGen.Builder builder,
            InvocationShape shape,
            CanonicalExpression receiver,
            String selector,
            java.util.List<CanonicalExpression> arguments,
            BytecodeLocal result,
            BytecodeLocal scratch,
            BytecodeLocal childResult,
            BytecodeLocal resumeValue,
            OperandStager stager,
            SpreadStager spreadStager,
            boolean primitiveCarrierResult) {
        if (result == null || scratch == null || childResult == null || resumeValue == null) {
            throw new AssertionError("Bytecode invocation result/scratch locals were not allocated");
        }
        if (shape == InvocationShape.CALL
                && arguments.isEmpty()
                && i115AdmitsInlineClosure(receiver)) {
            builder.beginTag(StandardTags.CallTag.class);
            builder.beginBlock();
            emitI115ClosureInvocation(
                    builder, receiver, null, true, result,
                    scratch, childResult, resumeValue, primitiveCarrierResult);
            builder.endBlock();
            builder.endTag(StandardTags.CallTag.class);
            return;
        }
        if (shape == InvocationShape.SEND && i115ControlSelector(selector)) {
            emitI115UnifiedControl(
                    builder,
                    new CanonicalSend(receiver, selector, arguments, receiver.span()),
                    result, scratch, childResult, resumeValue,
                    stager, primitiveCarrierResult);
            return;
        }

        builder.beginTag(StandardTags.CallTag.class);
        builder.beginBlock();

        BytecodeLocal callable = builder.createLocal("invocationCallable", null);
        switch (shape) {
            case CALL -> stageOperand(builder, receiver, callable, scratch, childResult, resumeValue, stager);
            case SEND -> {
                BytecodeLocal receiverValue = builder.createLocal("invocationReceiver", null);
                stageOperand(builder, receiver, receiverValue, scratch, childResult, resumeValue, stager);
                emitGetMethod(builder, selector, receiverValue, callable);
            }
            case SUPER_SEND -> {
                builder.beginStoreLocal(callable);
                builder.emitGetSuperMethod(selector);
                builder.endStoreLocal();
            }
        }

        BytecodeLocal suppliedVector = null;
        java.util.List<BytecodeLocal> argumentValues = new java.util.ArrayList<>(arguments.size());
        if (hasSpreadArgument(arguments)) {
            suppliedVector = builder.createLocal("suppliedArgumentVector", null);
            spreadStager.stage(builder, arguments, suppliedVector, scratch, childResult, resumeValue);
        } else {
            for (CanonicalExpression argument : arguments) {
                BytecodeLocal argumentValue = builder.createLocal("invocationArgument", null);
                stageOperand(builder, argument, argumentValue, scratch, childResult, resumeValue, stager);
                argumentValues.add(argumentValue);
            }
        }

        emitDispatchedCall(builder, callable, argumentValues, suppliedVector, childResult, result,
                resumeValue, primitiveCarrierResult);

        builder.endBlock();
        builder.endTag(StandardTags.CallTag.class);
    }


    /**
     * I115: ifTrue is Bytecode DSL control flow, not a method send.
     *
     * The receiver and callback-producing expression are evaluated
     * exactly once. No GetMethod(ifTrue), PrepareSend(ifTrue) or
     * Boolean method activation is emitted.
     *
     * The selected callback uses the shared call dispatcher and
     * its existing continuation transport.
     */
    /**
     * I115: THE sole bytecode emission for ifTrue, independent of the
     * general or compact root planner. Root planners may prove different
     * expression representations, but neither owns a separate ifTrue op.
     *
     * Compact admission already proves a literal callback with a body that
     * can be emitted in the containing root; the ordinary path uses the
     * existing I095 whole-activation proof, invoking a non-inlineable
     * Closure directly rather than sending ifTrue as a method.
     *
     * There is exactly one beginIfThen[Else] below for the selector.
     */
    /*
     * I115: emit a callback in the caller only after I095 has proved its
     * activation unobservable and its direct-parent captures admissible.
     * Preserve nested invocation handling and source statement tags by
     * reusing the ordinary body expression lowerer.
     */
    private void emitI115InlineClosureBody(
            ProtosBytecodeRootNodeGen.Builder builder,
            CanonicalClosure closure,
            BytecodeLocal result,
            BytecodeLocal scratch,
            BytecodeLocal childResult,
            BytecodeLocal resumeValue) {
        // Reuse I095's lexical proof and existing callback-local handlers.
        // An inline callback's bindings are *not* bindings of its parent root.
        CanonicalBindingAnalysis analysis =
                compilationFacts.isolatedClosureAnalysis(closure);
        CanonicalLexicalScope scope =
                analysis.scopeOf(closure).orElseThrow();

        java.util.Map<String, BytecodeLocal> savedLocals =
                currentNormalCallbackScalarLocals;
        CanonicalBindingAnalysis savedAnalysis =
                currentNormalCallbackScalarAnalysis;
        CanonicalLexicalScope savedScope =
                currentNormalCallbackScalarScope;
        boolean savedDirectParent = currentNormalDirectParentCaptureReads;
        boolean savedInlineBody = currentInlineClosureCallBody;

        // Stable ordering matters for Bytecode DSL reparses. The I095
        // emitBodyCreate/emitBodyAssign/emitLookup handlers recognize these
        // locals by their isolated lexical binding identity.
        java.util.Map<String, BytecodeLocal> locals = new java.util.LinkedHashMap<>();
        java.util.List<String> names =
                new java.util.ArrayList<>(scope.declaredNames());
        java.util.Collections.sort(names);
        for (String name : names) {
            locals.put(name, builder.createLocal(
                    NORMAL_INLINE_CALLBACK_BINDING_LOCAL_PREFIX + name, null));
        }
        currentNormalCallbackScalarLocals = java.util.Map.copyOf(locals);
        currentNormalCallbackScalarAnalysis = analysis;
        currentNormalCallbackScalarScope = scope;
        currentNormalDirectParentCaptureReads = true;
        currentInlineClosureCallBody = true;
        try {
            java.util.List<CanonicalExpression> expressions =
                    closure.body().expressions();
            if (expressions.isEmpty()) {
                builder.beginStoreLocal(result);
                builder.emitLoadConstant(ProtosNullValue.INSTANCE);
                builder.endStoreLocal();
                return;
            }
            for (CanonicalExpression expression : expressions) {
                beginStatement(builder, expression);
                emitBodyExpressionToLocal(
                        builder, expression, result,
                        scratch, childResult, resumeValue);
                endStatement(builder);
            }
        } finally {
            currentNormalCallbackScalarLocals = savedLocals;
            currentNormalCallbackScalarAnalysis = savedAnalysis;
            currentNormalCallbackScalarScope = savedScope;
            currentInlineClosureCallBody = savedInlineBody;
            currentNormalDirectParentCaptureReads = savedDirectParent;
        }
    }

    /**
     * I115: the ONE lowering for ifTrue, ifFalse, ifTrueIfFalse and whileTrue.
     * No method lookup, prepared Boolean method, or compact-specific If/While.
     * The same normal callback proof is used on ordinary roots, while compact
     * admission already certifies its literal callbacks. The control-flow
     * bytecode is emitted only here, regardless of the root's storage lane.
     */
    private static boolean i115ControlSelector(String selector) {
        return switch (selector) {
            case "ifTrue", "ifFalse", "ifTrueIfFalse", "whileTrue" -> true;
            default -> false;
        };
    }

    /**
     * I115: one static admission policy for an immediately invoked Closure
     * literal, whether invoked as a control callback or as an ordinary Call0.
     * Nonliteral/dynamic callables are never statically inlined here; they use
     * the ordinary callable dispatcher. In particular, a failed lexical or
     * ReturnHome proof does not authorize a syntactic inline.
     */
    private boolean i115AdmitsInlineClosure(CanonicalExpression callable) {
        return callable instanceof CanonicalClosure closure
                && admitsNormalInlineControlBody(
                        closure, currentControlSiteContext());
    }

    private void emitI115UnifiedControl(
            ProtosBytecodeRootNodeGen.Builder builder,
            CanonicalSend send,
            BytecodeLocal result,
            BytecodeLocal scratch,
            BytecodeLocal childResult,
            BytecodeLocal resumeValue,
            OperandStager stager,
            boolean primitiveCarrierResult) {
        boolean compact = currentExperimentalCompactScope != null;
        boolean loop = "whileTrue".equals(send.message());
        int arity = "ifTrueIfFalse".equals(send.message()) ? 2 : 1;
        if (send.arguments().size() != arity || hasSpreadArgument(send.arguments())) {
            throw new AssertionError("I115 control requires " + arity
                    + " positional callback(s), without spread: " + send.message());
        }
        if (!compact && (result == null || scratch == null
                || childResult == null || resumeValue == null)) {
            throw new AssertionError("I115 general control requires composed scratch");
        }

        builder.beginTag(StandardTags.CallTag.class);
        builder.beginBlock();
        // Evaluate receiver and all arguments once, left-to-right, before
        // choosing a branch. In a compact root the receiver and all callbacks
        // are syntactically proved safe for direct expression/body emission.
        java.util.List<CanonicalExpression> operands = new java.util.ArrayList<>();
        operands.add(send.receiver());
        operands.addAll(send.arguments());
        java.util.List<BytecodeLocal> values = new java.util.ArrayList<>(operands.size());
        java.util.List<Boolean> inline = new java.util.ArrayList<>(operands.size());
        for (int index = 0; index < operands.size(); index++) {
            CanonicalExpression operand = operands.get(index);
            boolean callback = loop || index > 0;
            boolean admitted = callback && i115AdmitsInlineClosure(operand);
            inline.add(admitted);
            if (admitted || (compact && !callback)) {
                // Inline body, or compact Boolean receiver consumed by its
                // expression emitter: no Closure is materialized here.
                values.add(null);
            } else {
                BytecodeLocal local = builder.createLocal("i115ControlOperand", null);
                if (compact) {
                    // A rejected callback in an admitted compact root is a
                    // genuine Closure value, not a second inline policy.
                    builder.beginStoreLocal(local);
                    emitExpression(builder, operand);
                    builder.endStoreLocal();
                } else {
                    stageOperand(builder, operand, local,
                            scratch, childResult, resumeValue, stager);
                }
                values.add(local);
            }
        }

        if (loop) {
            // Exactly one DSL While implementation for compact/general.
            if (result != null) {
                builder.beginStoreLocal(result);
                builder.emitLoadConstant(ProtosNullValue.INSTANCE);
                builder.endStoreLocal();
            }
            builder.beginWhile();
            // Both compact and general roots invoke the condition via the
            // shared Closure mechanism. A While condition must be a value,
            // so the statement-shaped invocation is wrapped in a Block.
            builder.beginBlock();
            BytecodeLocal condition = builder.createLocal("i115WhileCondition", null);
            emitI115ClosureInvocation(builder, send.receiver(), values.get(0),
                    inline.get(0), condition, scratch, childResult,
                    resumeValue, primitiveCarrierResult);
            builder.beginExperimentalCanonicalBooleanCondition();
            builder.emitLoadLocal(condition);
            builder.endExperimentalCanonicalBooleanCondition();
            builder.endBlock();
            builder.beginBlock();
            // The loop's body is a void child. When the entire whileTrue
            // result is discarded, an invoked callback still needs a result
            // destination for the shared continuation machinery.
            BytecodeLocal bodyResult = result == null && !inline.get(1)
                    ? builder.createLocal("i115DiscardedWhileBody", null)
                    : result;
            emitI115ClosureInvocation(builder, send.arguments().get(0),
                    values.get(1), inline.get(1), bodyResult,
                    scratch, childResult, resumeValue, primitiveCarrierResult);
            builder.endBlock();
            builder.endWhile();
        } else {
            NormalBooleanControl control = NormalBooleanControl.of(send.message());
            boolean discard = compact && result == null;
            if (discard && control.selectedIndex(false) < 0) {
                builder.beginIfThen();
            } else {
                builder.beginIfThenElse();
            }
            if (compact) {
                emitExperimentalCompactBooleanCondition(builder, send.receiver());
            } else {
                builder.beginExperimentalCanonicalBooleanCondition();
                builder.emitLoadLocal(values.get(0));
                builder.endExperimentalCanonicalBooleanCondition();
            }
            emitI115UnifiedBranch(builder, send, control, true, values, inline,
                    result, scratch, childResult, resumeValue, primitiveCarrierResult);
            if (discard && control.selectedIndex(false) < 0) {
                builder.endIfThen();
            } else {
                emitI115UnifiedBranch(builder, send, control, false, values, inline,
                        result, scratch, childResult, resumeValue, primitiveCarrierResult);
                builder.endIfThenElse();
            }
        }
        builder.endBlock();
        builder.endTag(StandardTags.CallTag.class);
    }

    private void emitI115UnifiedBranch(
            ProtosBytecodeRootNodeGen.Builder builder,
            CanonicalSend send,
            NormalBooleanControl control,
            boolean receiver,
            java.util.List<BytecodeLocal> values,
            java.util.List<Boolean> inline,
            BytecodeLocal result,
            BytecodeLocal scratch,
            BytecodeLocal childResult,
            BytecodeLocal resumeValue,
            boolean primitiveCarrierResult) {
        builder.beginBlock();
        int index = control.selectedIndex(receiver);
        if (index < 0) {
            if (result != null) {
                builder.beginStoreLocal(result);
                builder.emitLoadConstant(control.unselectedResult(receiver));
                builder.endStoreLocal();
            }
        } else {
            emitI115ClosureInvocation(builder, send.arguments().get(index),
                    values.get(index + 1), inline.get(index + 1), result,
                    scratch, childResult, resumeValue, primitiveCarrierResult);
        }
        builder.endBlock();
    }

    private void emitI115ClosureInvocation(
            ProtosBytecodeRootNodeGen.Builder builder,
            CanonicalExpression callback,
            BytecodeLocal callable,
            boolean inline,
            BytecodeLocal result,
            BytecodeLocal scratch,
            BytecodeLocal childResult,
            BytecodeLocal resumeValue,
            boolean primitiveCarrierResult) {
        if (inline) {
            CanonicalClosure literal = (CanonicalClosure) callback;
            if (currentExperimentalCompactScope != null) {
                if (result == null) {
                    emitExperimentalCompactSequence(builder, literal.body());
                } else {
                    emitExperimentalCompactSequenceValue(builder, literal.body(), result);
                }
            } else {
                emitI115InlineClosureBody(
                        builder, literal, result, scratch, childResult, resumeValue);
            }
        } else {
            BytecodeLocal destination = result != null
                    ? result : builder.createLocal("i115IgnoredClosureResult", null);
            BytecodeLocal callChildResult = childResult != null
                    ? childResult : builder.createLocal("i115ClosureChildResult", null);
            BytecodeLocal callResumeValue = resumeValue != null
                    ? resumeValue : builder.createLocal("i115ClosureResumeValue", null);
            emitDispatchedCall(builder, callable, java.util.List.of(), null,
                    callChildResult, destination, callResumeValue,
                    primitiveCarrierResult);
        }
    }

    private void stageOperand(
            ProtosBytecodeRootNodeGen.Builder builder,
            CanonicalExpression expression,
            BytecodeLocal target,
            BytecodeLocal scratch,
            BytecodeLocal childResult,
            BytecodeLocal resumeValue,
            OperandStager stager) {
        if (requiresComposedInvocation(expression)) {
            stager.stage(builder, expression, target, scratch, childResult, resumeValue);
        } else {
            builder.beginStoreLocal(target);
            emitExpression(builder, expression);
            builder.endStoreLocal();
        }
    }

    /** {@code GetMethod(selector)} on the staged receiver, stored in {@code callable}. */
    private static void emitGetMethod(
            ProtosBytecodeRootNodeGen.Builder builder,
            String selector,
            BytecodeLocal receiver,
            BytecodeLocal callable) {
        builder.beginStoreLocal(callable);
        builder.beginGetMethod(
                TruffleString.fromJavaStringUncached(selector, TruffleString.Encoding.UTF_16));
        builder.emitLoadLocal(receiver);
        builder.endGetMethod();
        builder.endStoreLocal();
    }

    /**
     * The {@code Call*Method} of the staged callable and arguments, its
     * continuation composition and the result store. Shared by every
     * invocation shape so all arities reach the same dispatcher.
     */
    private void emitDispatchedCall(
            ProtosBytecodeRootNodeGen.Builder builder,
            BytecodeLocal callable,
            java.util.List<BytecodeLocal> argumentValues,
            BytecodeLocal suppliedVector,
            BytecodeLocal outcome,
            BytecodeLocal result,
            BytecodeLocal resumeValue,
            boolean primitiveCarrierResult) {
        builder.beginStoreLocal(outcome);
        emitCallOperation(builder, false, callable, argumentValues, suppliedVector);
        builder.endStoreLocal();

        emitDispatchedContinuationComposition(builder, outcome, resumeValue);

        builder.beginStoreLocal(result);
        if (primitiveCarrierResult) {
            builder.emitLoadLocal(outcome);
        } else {
            builder.beginMaterializePrimitiveNumericCarrier();
            builder.emitLoadLocal(outcome);
            builder.emitLoadConstant(true);
            builder.endMaterializePrimitiveNumericCarrier();
        }
        builder.endStoreLocal();
    }

    /** The arity-selected {@code Call*Method} operation over staged locals. */
    private static void emitCallOperation(
            ProtosBytecodeRootNodeGen.Builder builder,
            boolean terminal,
            BytecodeLocal callable,
            java.util.List<BytecodeLocal> argumentValues,
            BytecodeLocal suppliedVector) {
        int arity = suppliedVector != null ? -1 : argumentValues.size();
        switch (arity) {
            case 0 -> builder.beginCallNilaryMethod(terminal);
            case 1 -> builder.beginCallUnaryMethod(terminal);
            case 2 -> builder.beginCallBinaryMethod(terminal);
            case 3 -> builder.beginCallTernaryMethod(terminal);
            case 4 -> builder.beginCallQuaternaryMethod(terminal);
            default -> builder.beginCallVarargsMethod(terminal);
        }
        builder.emitLoadLocal(callable);
        if (arity >= 0 && arity <= 4) {
            for (BytecodeLocal argumentValue : argumentValues) {
                builder.emitLoadLocal(argumentValue);
            }
        } else if (suppliedVector != null) {
            builder.beginSnapshotClosureCallArguments();
            builder.emitLoadLocal(suppliedVector);
            builder.endSnapshotClosureCallArguments();
        } else {
            builder.beginCollectClosureCallArguments();
            for (BytecodeLocal argumentValue : argumentValues) {
                builder.emitLoadLocal(argumentValue);
            }
            builder.endCollectClosureCallArguments();
        }
        switch (arity) {
            case 0 -> builder.endCallNilaryMethod();
            case 1 -> builder.endCallUnaryMethod();
            case 2 -> builder.endCallBinaryMethod();
            case 3 -> builder.endCallTernaryMethod();
            case 4 -> builder.endCallQuaternaryMethod();
            default -> builder.endCallVarargsMethod();
        }
    }

    /** I115: a send of already staged locals: GetMethod, then the dispatched call. */
    private void emitDispatchedSendOfLocals(
            ProtosBytecodeRootNodeGen.Builder builder,
            String selector,
            BytecodeLocal receiver,
            java.util.List<BytecodeLocal> argumentValues,
            BytecodeLocal result,
            BytecodeLocal childResult,
            BytecodeLocal resumeValue) {
        BytecodeLocal callable = builder.createLocal("sendMethod", null);
        emitGetMethod(builder, selector, receiver, callable);
        emitDispatchedCall(
                builder, callable, argumentValues, null, childResult, result, resumeValue, false);
    }

    /**
     * I115: a {@link ProtosCallRequest} produced by a protocol operation
     * (Map construction factory, Map hashing/equality), made through the
     * shared dispatcher and composed like any call.
     */
    private void emitRequestInvocation(
            ProtosBytecodeRootNodeGen.Builder builder,
            BytecodeLocal result,
            BytecodeLocal request,
            BytecodeLocal childResult,
            BytecodeLocal resumeValue) {
        builder.beginTag(StandardTags.CallTag.class);
        builder.beginBlock();
        builder.beginStoreLocal(childResult);
        builder.beginCallRequest(false);
        builder.emitLoadLocal(request);
        builder.endCallRequest();
        builder.endStoreLocal();
        emitDispatchedContinuationComposition(builder, childResult, resumeValue);
        builder.beginStoreLocal(result);
        builder.beginMaterializePrimitiveNumericCarrier();
        builder.emitLoadLocal(childResult);
        builder.emitLoadConstant(true);
        builder.endMaterializePrimitiveNumericCarrier();
        builder.endStoreLocal();
        builder.endBlock();
        builder.endTag(StandardTags.CallTag.class);
    }

    /**
     * Composes a pending result from a direct source-function dispatch.
     * The callee frames own their ReturnHomes.
     */
    private void emitDispatchedContinuationComposition(
            ProtosBytecodeRootNodeGen.Builder builder,
            BytecodeLocal childResult,
            BytecodeLocal resumeValue) {

        currentRootEmittedContinuationPath = true;

        builder.beginWhile();

        builder.beginIsResultKind(ProtosResultKind.CONTINUATION);
        builder.emitLoadLocal(childResult);
        builder.endIsResultKind();

        builder.beginBlock();

        builder.beginStoreLocal(resumeValue);
        builder.beginYield();
        builder.emitLoadLocal(childResult);
        builder.endYield();
        builder.endStoreLocal();

        builder.beginStoreLocal(childResult);
        builder.beginResumeDispatchedContinuation();
        builder.emitLoadLocal(childResult);
        builder.emitLoadLocal(resumeValue);
        builder.endResumeDispatchedContinuation();
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
        if (expression instanceof CanonicalNamedArgument named) {
            validateSupportedExpression(named.expression());
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

    /*
     * I112: at root level the creating activation is the root's own frame
     * argument 0, bound by MaterializeCurrentClosure itself rather than
     * loaded as an operand. I115: a root's LOCAL and CELL bindings are never
     * part of its context, so a still-compact root always captures without
     * materializing its activation. Inline object regions keep their
     * explicit activation operand.
     *
     * I115 / PLAT066-C: the second constant operand carries the capture
     * classification of this exact Closure under the root's analysis; a
     * Closure that does not capture its creator is created from the
     * creator's own environment (GraalPy MakeFunction without freevars).
     *
     * I115 (GraalPy MakeFunction closure tuple): a Closure whose scope
     * carries free cells is wrapped by CaptureClosureCells, which installs
     * the creator's cell references, in the Closure scope's constant order,
     * before the Closure escapes. Returns those cells for endClosureCapture.
     */
    private java.util.List<CanonicalBindingIdentity> beginClosureCapture(
            ProtosBytecodeRootNodeGen.Builder builder,
            CanonicalClosure closure) {
        java.util.List<CanonicalBindingIdentity> cells =
                currentRootAnalysis == null
                        ? java.util.List.of()
                        : currentRootAnalysis.scopeOf(closure)
                                .map(CanonicalBindingAnalysis::freeCellsOf)
                                .orElse(java.util.List.of());
        if (!cells.isEmpty()) {
            builder.beginCaptureClosureCells();
        }
        if (currentActivationLocal == null) {
            /*
             * I115 invariant of the compactCapture operand: it answers whether
             * capturing the creator may defer its activation instead of
             * materializing it. Under the I115 storage model the creator's
             * frame never holds lexical state a captured context must expose:
             * LOCAL bindings are invisible to captures, CELL bindings are
             * transported explicitly by CaptureClosureCells, and NAME bindings
             * already live in the creator's context object. The deferred owner
             * keeps the B5 header (argument 0, ReturnHome, prelude) and resolves
             * that same activation on demand, so the answer is statically true
             * for every root-level creation; only an inline object region,
             * which has an explicit activation, uses MaterializeClosure. The
             * operation's other modes are thereby unreachable from lowering.
             *
             * I115-H: the ReturnHome fact gates the lightweight representation.
             */
            builder.beginMaterializeCurrentClosure(
                    COMPACT_ROOT_CLOSURE_CAPTURE,
                    CanonicalCompilationFacts.capturesCreatorContext(
                            closure, currentRootAnalysis),
                    compilationFacts.mayObserveReturnHome(closure));
        } else {
            builder.beginMaterializeClosure();
            emitCurrentActivation(builder);
        }
        return cells;
    }

    private void endClosureCapture(
            ProtosBytecodeRootNodeGen.Builder builder,
            java.util.List<CanonicalBindingIdentity> cells) {
        if (currentActivationLocal == null) {
            builder.endMaterializeCurrentClosure();
        } else {
            builder.endMaterializeClosure();
        }
        if (!cells.isEmpty()) {
            for (CanonicalBindingIdentity cell : cells) {
                BytecodeLocal local = currentRootCellLocals.get(cell);
                if (local == null) {
                    throw new AssertionError(
                            "I115 captured cell is not reachable from its creator: "
                                    + cell.name());
                }
                builder.emitLoadLocal(local);
            }
            builder.endCaptureClosureCells();
        }
    }

    private void emitExpression(
            ProtosBytecodeRootNodeGen.Builder builder,
            CanonicalExpression expression) {
        if (expression instanceof CanonicalLiteral literal) {
            emitLiteral(builder, literal);
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
            emitClosureCapture(builder, closure, cell);
            return;
        }
        if (expression instanceof CanonicalLookup lookup) {
            emitLookup(builder, lookup);
            return;
        }
        if (expression instanceof CanonicalIntrinsic intrinsic) {
            /*
             * KNOWN I115 SEMANTIC DIVERGENCE: the observed context of a
             * Closure activation does not contain its LOCAL or CELL bindings.
             */
            builder.beginLoadIntrinsic();
            emitCurrentActivation(builder);
            builder.emitLoadConstant(intrinsic.kind());
            builder.endLoadIntrinsic();
            return;
        }
        if (expression instanceof CanonicalMember member) {
            beginMemberRead(builder, member.name());
            emitExpression(builder, member.receiver());
            endMemberRead(builder);
            return;
        }
        if (expression instanceof CanonicalIdentity identity) {
            builder.beginIdentity(false);
            emitExpression(builder, identity.left());
            emitExpression(builder, identity.right());
            builder.endIdentity();
            return;
        }
        if (expression instanceof CanonicalNotIdentity identity) {
            builder.beginIdentity(true);
            emitExpression(builder, identity.left());
            emitExpression(builder, identity.right());
            builder.endIdentity();
            return;
        }
        throw new AssertionError(
                "validated value-shaped Bytecode expression became unsupported: "
                        + expression.getClass().getSimpleName());
    }

    private void emitLookup(
            ProtosBytecodeRootNodeGen.Builder builder,
            CanonicalLookup lookup) {
        emitLookup(builder, lookup, false);
    }

    /**
     * I115: emits exactly one access for a bare read, selected at lowering
     * time from its static classification: {@code LoadLocal} for a LOCAL
     * binding of this root, {@code LoadCell} for a CELL binding (owned or
     * carried as a free cell), {@code ReadGlobal} for a module binding
     * captured by a Closure root, and otherwise one {@code
     * AccessNamedLexical} name operation (a module binding read in its own
     * root, an object-body member, or a dynamic name). No access is ever
     * followed by another storage when it does not find the binding.
     *
     * <p>I091: carrierOperand is true only for an operand of the guarded
     * primitive numeric chain, which may then receive a local-retained
     * primitive carrier. Every other read yields a guest value.
     *
     * <p>KNOWN I115 SEMANTIC DIVERGENCE (D179 C0, PLAT066-C): a LOCAL or
     * CELL read does not observe removal/recreation of the binding through an
     * observed context, nor a later lexical redirection of the name.
     */
    private void emitLookup(
            ProtosBytecodeRootNodeGen.Builder builder,
            CanonicalLookup lookup,
            boolean carrierOperand) {
        if (!currentNormalCallbackScalarLocals.isEmpty()
                && currentNormalCallbackScalarLocals.containsKey(lookup.name())) {
            CanonicalBindingResolution resolution =
                    currentNormalCallbackScalarAnalysis.resolutionOf(lookup)
                            .orElse(null);
            BytecodeLocal own = currentNormalCallbackScalarLocals.get(lookup.name());
            if (own == null
                    || !(resolution instanceof CanonicalBindingResolution.Resolved resolved)
                    || resolved.identity().owner() != currentNormalCallbackScalarScope) {
                throw new AssertionError(
                        "I095-B6a unproven callback lookup: " + lookup.name());
            }
            builder.emitLoadLocal(own);
            return;
        }

        CanonicalBindingResolution resolution =
                currentRootAnalysis == null
                        ? null
                        : currentRootAnalysis.resolutionOf(lookup).orElse(null);
        CanonicalBindingIdentity identity = staticIdentityOrNull(resolution);
        if (identity != null) {
            BytecodeLocal cell = cellLocalOrNull(identity);
            if (cell != null) {
                builder.beginLoadCell();
                builder.emitLoadLocal(cell);
                builder.endLoadCell();
                return;
            }
            if (isUninitializedLocalAccess(resolution, identity)) {
                builder.emitUninitializedBinding();
                return;
            }
            BytecodeLocal local = ownLocalOrNull(identity);
            if (local != null) {
                if (!carrierOperand) {
                    builder.beginMaterializePrimitiveNumericCarrier();
                }
                builder.emitLoadLocal(local);
                if (!carrierOperand) {
                    builder.emitLoadConstant(true);
                    builder.endMaterializePrimitiveNumericCarrier();
                }
                return;
            }
            if (resolution instanceof CanonicalBindingResolution.CapturedResolved captured
                    && identity.owner().kind() == CanonicalLexicalScope.Kind.ROOT
                    && currentActivationLocal == null) {
                /*
                 * I115-R6: a module binding read from a Closure root reads
                 * the captured module context (GraalPy ReadGlobal). Inside
                 * an inline callback the physical root is one scope nearer.
                 */
                int physicalDepth =
                        currentNormalDirectParentCaptureReads
                                ? captured.lexicalDepth() - 1
                                : captured.lexicalDepth();
                if (physicalDepth >= 1) {
                    builder.emitReadGlobal(
                            TruffleString.fromJavaStringUncached(
                                    identity.name(), TruffleString.Encoding.UTF_16),
                            physicalDepth);
                    return;
                }
            }
        }
        builder.beginAccessNamedLexical(ProtosNamedLexicalAccess.LOOKUP);
        emitCurrentActivation(builder);
        builder.emitLoadConstant(lookup.name());
        builder.endAccessNamedLexical();
    }

    /*
     * Numeric carriers are private to the guarded arithmetic expression chain.
     * Ordering consumes carriers and always yields a guest Boolean.
     */
    private static boolean primitiveNumericSendCandidate(CanonicalSend send) {
        return send.arguments().size() == 1
                && primitiveNumericSelector(send.message())
                && !hasVectorArgument(send.arguments());
    }

    /*
     * I091: a send whose fused direct source call may deliver a callee's
     * primitive result to a carrier-permitting consumer.
     */
    private static boolean primitiveResultSend(CanonicalSend send) {
        if (hasNamedArgument(send.arguments())) {
            return false;
        }
        // I091-F2: zero-argument sends may return primitive carriers
        // through the admitted direct Send0 frame. Generic dispatch
        // remains materialized, and Send1 retains its existing rules.
        return send.arguments().isEmpty()
                || primitiveNumericSendCandidate(send)
                || (send.arguments().size() == 1
                        && !hasSpreadArgument(send.arguments()));
    }

    /*
     * I091-F2: a direct Call1 has the full compact header required
     * for the private primitive-result marker. A generic or rejected
     * call still returns an observable guest value.
     */
    private static boolean primitiveResultCall(CanonicalCall call) {
        return call.arguments().size() == 1
                && !hasVectorArgument(call.arguments());
    }

    private static boolean primitiveNumericSelector(String selector) {
        return switch (selector) {
            case "+", "-", "*", "/", "<", "<=", ">", ">=" -> true;
            default -> false;
        };
    }

    /* I091: a binding value the guarded chain or a raw binding read may yield as a carrier. */
    private boolean primitiveNumericCarrierValue(CanonicalExpression value) {
        if (value instanceof CanonicalLiteral literal) {
            return primitiveNumberLiteralOrNull(literal) != null;
        }
        if (value instanceof CanonicalLookup lookup) {
            return !isScalarLocalRead(lookup);
        }
        return (value instanceof CanonicalSend send && primitiveResultSend(send))
                || (value instanceof CanonicalCall call && primitiveResultCall(call));
    }

    private void emitPrimitiveNumericOperandToLocal(
            ProtosBytecodeRootNodeGen.Builder builder,
            CanonicalExpression expression,
            BytecodeLocal target,
            BytecodeLocal preparedCall,
            BytecodeLocal childResult,
            BytecodeLocal resumeValue) {
        if (expression instanceof CanonicalLiteral literal) {
            // The staged operand is the literal's own carrier: a Long or Double descriptor.
            Object carrier = primitiveNumberLiteralOrNull(literal);
            if (carrier != null) {
                builder.beginStoreLocal(target);
                builder.emitLoadConstant(carrier);
                builder.endStoreLocal();
                return;
            }
        }
        if (expression instanceof CanonicalLookup lookup
                && !isScalarLocalRead(lookup)) {
            builder.beginStoreLocal(target);
            emitLookup(builder, lookup, true);
            builder.endStoreLocal();
            return;
        }
        if (expression instanceof CanonicalSend nested && primitiveResultSend(nested)) {
            builder.beginSourceSection(
                    nested.span().startOffset(), nested.span().length());
            emitComposedSend(builder, nested, target, preparedCall,
                    childResult, resumeValue, true);
            builder.endSourceSection();
            return;
        }
        if (expression instanceof CanonicalCall nested
                && primitiveResultCall(nested)) {
            builder.beginSourceSection(
                    nested.span().startOffset(), nested.span().length());
            emitComposedCall(
                    builder, nested, target, preparedCall,
                    childResult, resumeValue, true);
            builder.endSourceSection();
            return;
        }
        emitBodyExpressionToLocal(builder, expression, target,
                preparedCall, childResult, resumeValue);
    }

    /**
     * PERF043-A2: recognize literal Integer additions in any send position.
     * This descriptor never authorizes dispatch by itself. The certified
     * lane of the per-selector numeric operation remains authoritative.
     */
    private Object foldedIntegerAdditionForSend(CanonicalSend send) {
        if (currentRootTerminalIntegerSend == send
                && currentRootTerminalIntegerAddition != null) {
            return currentRootTerminalIntegerAddition;
        }

        var candidate = currentRootArithmeticHints.get(send);
        if (candidate == null) {
            candidate = foldedIntegerAddition(send, java.util.Map.of());
        }
        return candidate == null ? Boolean.FALSE : candidate;
    }

    private void emitComposedSend(
            ProtosBytecodeRootNodeGen.Builder builder,
            CanonicalSend send,
            BytecodeLocal result,
            BytecodeLocal scratch,
            BytecodeLocal childResult,
            BytecodeLocal resumeValue) {
        emitComposedSend(builder, send, result, scratch, childResult, resumeValue, false);
    }

    private void emitComposedSend(
            ProtosBytecodeRootNodeGen.Builder builder,
            CanonicalSend send,
            BytecodeLocal result,
            BytecodeLocal scratch,
            BytecodeLocal childResult,
            BytecodeLocal resumeValue,
            boolean primitiveCarrierResult) {
        emitComposedSendUnfolded(
                builder, send, result, scratch, childResult, resumeValue, primitiveCarrierResult);
    }


    /**
     * PERF040-E1: the selector is known here, so the site emits the one
     * primitive numeric operation that owns it; no operator spelling is
     * compared at run time. The staged receiver and argument were each
     * evaluated exactly once. A MISS result keeps the unchanged ordinary
     * send. Arithmetic sites additionally carry their PERF043 fold candidate.
     * {@code receiverCarrier} and {@code argumentCarrier} state whether each
     * staged operand may hold a primitive carrier.
     */
    private void emitPrimitiveNumericAttempt(
            ProtosBytecodeRootNodeGen.Builder builder,
            CanonicalSend send,
            BytecodeLocal receiverValue,
            BytecodeLocal argumentValue,
            boolean receiverCarrier,
            boolean argumentCarrier) {
        String selector = send.message();
        switch (selector) {
            case "+" -> builder.beginTryPrimitiveNumericAdd();
            case "-" -> builder.beginTryPrimitiveNumericSubtract();
            case "*" -> builder.beginTryPrimitiveNumericMultiply();
            case "/" -> builder.beginTryPrimitiveNumericDivide();
            case "<" -> builder.beginTryPrimitiveNumericLess();
            case "<=" -> builder.beginTryPrimitiveNumericLessEqual();
            case ">" -> builder.beginTryPrimitiveNumericGreater();
            case ">=" -> builder.beginTryPrimitiveNumericGreaterEqual();
            default -> throw new AssertionError(
                    "not a primitive numeric selector: " + selector);
        }
        builder.emitLoadLocal(receiverValue);
        emitSendCallerOperand(builder);
        builder.emitLoadLocal(argumentValue);
        builder.emitLoadConstant(receiverCarrier);
        builder.emitLoadConstant(argumentCarrier);
        switch (selector) {
            case "+", "-", "*", "/" -> {
                Object fold = foldedIntegerAdditionForSend(send);
                builder.emitLoadConstant(fold);
                builder.emitLoadConstant(
                        fold instanceof ProtosPrimitiveNumericSendFastPathsBase
                                        .FoldedIntegerArithmetic known
                                ? known.result()
                                : Boolean.FALSE);
            }
            default -> {
                // Ordering has no arithmetic fold candidate.
            }
        }
        switch (selector) {
            case "+" -> builder.endTryPrimitiveNumericAdd();
            case "-" -> builder.endTryPrimitiveNumericSubtract();
            case "*" -> builder.endTryPrimitiveNumericMultiply();
            case "/" -> builder.endTryPrimitiveNumericDivide();
            case "<" -> builder.endTryPrimitiveNumericLess();
            case "<=" -> builder.endTryPrimitiveNumericLessEqual();
            case ">" -> builder.endTryPrimitiveNumericGreater();
            case ">=" -> builder.endTryPrimitiveNumericGreaterEqual();
            default -> throw new AssertionError(
                    "not a primitive numeric selector: " + selector);
        }
    }

    /**
     * PERF040-E1: store the primitive numeric attempt over the staged
     * operands and open its MISS branch, where the staged carriers become
     * guest values for the ordinary send the caller emits next. {@link
     * #endPrimitiveNumericMiss} closes the branch; its HIT branch consumes
     * the attempt. I115-H1-FIX: the general and the compact send lowerings
     * share this one numeric site shape.
     */
    private BytecodeLocal beginPrimitiveNumericMiss(
            ProtosBytecodeRootNodeGen.Builder builder,
            CanonicalSend send,
            BytecodeLocal receiverValue,
            BytecodeLocal argumentValue,
            boolean receiverCarrier,
            boolean argumentCarrier) {
        BytecodeLocal attempt = builder.createLocal("primitiveNumericAttempt", null);
        builder.beginStoreLocal(attempt);
        emitPrimitiveNumericAttempt(
                builder, send, receiverValue, argumentValue,
                receiverCarrier, argumentCarrier);
        builder.endStoreLocal();

        builder.beginIfThenElse();
        builder.beginIsResultKind(ProtosResultKind.PRIMITIVE_NUMERIC_SEND_MISS);
        builder.emitLoadLocal(attempt);
        builder.endIsResultKind();
        builder.beginBlock();
        // Any internal carriers must become guest values before normal D013.
        builder.beginStoreLocal(receiverValue);
        builder.beginMaterializePrimitiveNumericCarrier();
        builder.emitLoadLocal(receiverValue);
        builder.emitLoadConstant(receiverCarrier);
        builder.endMaterializePrimitiveNumericCarrier();
        builder.endStoreLocal();
        builder.beginStoreLocal(argumentValue);
        builder.beginMaterializePrimitiveNumericCarrier();
        builder.emitLoadLocal(argumentValue);
        builder.emitLoadConstant(argumentCarrier);
        builder.endMaterializePrimitiveNumericCarrier();
        builder.endStoreLocal();
        return attempt;
    }

    /*
     * PERF040-E1: close the MISS branch opened by beginPrimitiveNumericMiss;
     * the HIT branch stores the attempt's result, materialized unless the
     * consumer permits a primitive carrier.
     */
    private static void endPrimitiveNumericMiss(
            ProtosBytecodeRootNodeGen.Builder builder,
            BytecodeLocal attempt,
            BytecodeLocal result,
            boolean primitiveCarrierResult) {
        builder.endBlock();
        builder.beginBlock();
        builder.beginStoreLocal(result);
        if (primitiveCarrierResult) {
            builder.emitLoadLocal(attempt);
        } else {
            builder.beginMaterializePrimitiveNumericCarrier();
            builder.emitLoadLocal(attempt);
            builder.emitLoadConstant(true);
            builder.endMaterializePrimitiveNumericCarrier();
        }
        builder.endStoreLocal();
        builder.endBlock();
        builder.endIfThenElse();
    }

    /*
     * I095-C/D: five callback-taking standard Boolean selectors share
     * this table-driven open-coded lowering. The standard not selector
     * uses a separate no-callback path. Each row declares, for each
     * canonical receiver, the
     * supplied position of the selected callback (-1: none), the result
     * when no callback is selected and the policy applied to a selected
     * callback's normal result. The rows restate the standard behaviors of
     * ProtosStandardBooleanProtocol (also sequenced by PreparedBooleanCall);
     * frozen root publication validates that exactly those behaviors are
     * selected for canonical receivers.
     */
    private enum NormalBooleanControl {
        NOT(0, -1, -1, Unselected.NEGATION, SelectedResult.EXACT),
        IF_TRUE(1, 0, -1, Unselected.NULL, SelectedResult.EXACT),
        IF_FALSE(1, -1, 0, Unselected.NULL, SelectedResult.EXACT),
        IF_TRUE_IF_FALSE(2, 0, 1, Unselected.NULL, SelectedResult.EXACT),
        AND(1, 0, -1, Unselected.RECEIVER, SelectedResult.CANONICAL_BOOLEAN),
        OR(1, -1, 0, Unselected.RECEIVER, SelectedResult.CANONICAL_BOOLEAN);

        /** Result of a canonical receiver that selects no callback. */
        private enum Unselected { NULL, RECEIVER, NEGATION }

        /**
         * Exact callback result (ifTrue, ifFalse, ifTrueIfFalse) or the
         * result required to be canonical true/false (and/or).
         */
        private enum SelectedResult { EXACT, CANONICAL_BOOLEAN }

        private final int arity;
        private final int selectedOnTrue;
        private final int selectedOnFalse;
        private final Unselected unselected;
        private final SelectedResult selectedResult;

        NormalBooleanControl(
                int arity,
                int selectedOnTrue,
                int selectedOnFalse,
                Unselected unselected,
                SelectedResult selectedResult) {
            this.arity = arity;
            this.selectedOnTrue = selectedOnTrue;
            this.selectedOnFalse = selectedOnFalse;
            this.unselected = unselected;
            this.selectedResult = selectedResult;
        }

        static NormalBooleanControl of(String message) {
            return switch (message) {
                case "not" -> NOT;
                case "ifTrue" -> IF_TRUE;
                case "ifFalse" -> IF_FALSE;
                case "ifTrueIfFalse" -> IF_TRUE_IF_FALSE;
                case "and" -> AND;
                case "or" -> OR;
                default -> null;
            };
        }

        /** Supplied position of the selected callback, or -1. */
        int selectedIndex(boolean receiver) {
            return receiver ? selectedOnTrue : selectedOnFalse;
        }

        Object unselectedResult(boolean receiver) {
            return switch (unselected) {
                case NULL -> ProtosNullValue.INSTANCE;
                case RECEIVER -> canonicalBoolean(receiver);
                case NEGATION -> canonicalBoolean(!receiver);
            };
        }

        static Object canonicalBoolean(boolean value) {
            return value ? ProtosBooleanValue.TRUE : ProtosBooleanValue.FALSE;
        }
    }

    /**
     * I095-D: how one canonical receiver completes an admitted site:
     * an immediate result, the selected literal callback inline in this
     * root, or the ordinary send with the already evaluated operands.
     */
    private enum NormalBooleanControlPath { IMMEDIATE, INLINE, ORDINARY }

    /**
     * I095-D: an admitted site. {@code inlineCallbacks} holds, per supplied
     * position, whether that argument is a literal Closure that some
     * reachable canonical receiver selects and whose whole activation is
     * proven unobservable ({@link #admitsNormalInlineControlBody}).
     */
    private record NormalBooleanControlSite(
            NormalBooleanControl control,
            java.util.List<Boolean> inlineCallbacks) {

        NormalBooleanControlPath path(boolean receiver) {
            int selected = control.selectedIndex(receiver);
            if (selected < 0) {
                return NormalBooleanControlPath.IMMEDIATE;
            }
            return inlineCallbacks.get(selected)
                    ? NormalBooleanControlPath.INLINE
                    : NormalBooleanControlPath.ORDINARY;
        }
    }

    /*
     * I095-C/D: a canonical receiver that does not select a callback needs
     * no proof about that argument, which may be any expression: it is
     * still evaluated, but neither validated nor invoked. A selected
     * argument runs inline only when it is a literal Closure carrying the
     * I095-B proof of the whole callback activation; each callback of
     * ifTrueIfFalse is proven independently. A literal receiver is admitted
     * when its own path is not the ordinary send. A dynamic receiver,
     * classified at run time, is admitted when some canonical path runs a
     * callback inline or no path invokes one (not); otherwise the existing
     * prepared/B-prime lowering is kept unchanged. Spread arguments and
     * arities other than the standard one keep ordinary dispatch.
     *
     * I101-A: admission is a pure function of the canonical site and this
     * lowerer's publication snapshot, so every reparse of a group makes the
     * same decision.
     */
    private NormalBooleanControlSite normalBooleanControlSite(
            CanonicalSend send) {
        return normalBooleanControlSite(send, currentControlSiteContext());
    }

    /**
     * I107: the selection row of an admissible site, or {@code null}; the
     * shared precondition of admission ({@link #normalBooleanControlSite})
     * and of the constant fold ({@link #foldedConstantControlResult}).
     */
    private NormalBooleanControl normalBooleanControlRow(
            CanonicalSend send, ControlSiteContext context) {
        NormalBooleanControl control =
                NormalBooleanControl.of(send.message());
        if (control == null
                || !standardObjectPublished
                || context.inlineCallbackFrameNative()
                || context.activationLocalPresent()
                || send.arguments().size() != control.arity
                || hasVectorArgument(send.arguments())) {
            return null;
        }
        return control;
    }

    private NormalBooleanControlSite normalBooleanControlSite(
            CanonicalSend send, ControlSiteContext context) {
        NormalBooleanControl control = normalBooleanControlRow(send, context);
        if (control == null) {
            return null;
        }

        Boolean literalReceiver = null;
        if (send.receiver() instanceof CanonicalLiteral receiver) {
            if (receiver.kind() == CanonicalLiteral.Kind.TRUE) {
                literalReceiver = Boolean.TRUE;
            } else if (receiver.kind() == CanonicalLiteral.Kind.FALSE) {
                literalReceiver = Boolean.FALSE;
            } else {
                return null;
            }
        }

        java.util.List<Boolean> inlineCallbacks =
                new java.util.ArrayList<>(control.arity);
        for (int position = 0; position < control.arity; position++) {
            boolean selectable = literalReceiver != null
                    ? control.selectedIndex(literalReceiver) == position
                    : control.selectedIndex(true) == position
                            || control.selectedIndex(false) == position;
            inlineCallbacks.add(
                    selectable
                            && send.arguments().get(position)
                                    instanceof CanonicalClosure closure
                            && admitsNormalInlineControlBody(closure, context));
        }
        NormalBooleanControlSite site = new NormalBooleanControlSite(
                control, java.util.List.copyOf(inlineCallbacks));

        if (literalReceiver != null) {
            return site.path(literalReceiver)
                            == NormalBooleanControlPath.ORDINARY
                    ? null
                    : site;
        }
        NormalBooleanControlPath onTrue = site.path(true);
        NormalBooleanControlPath onFalse = site.path(false);
        boolean inlinePath = onTrue == NormalBooleanControlPath.INLINE
                || onFalse == NormalBooleanControlPath.INLINE;
        boolean noCallback = onTrue == NormalBooleanControlPath.IMMEDIATE
                && onFalse == NormalBooleanControlPath.IMMEDIATE;
        return inlinePath || noCallback ? site : null;
    }

    /*
     * I095-B1: admission belongs to the entire callback activation,
     * not to individual expression categories. A selected literal
     * Closure is admitted when its own lexical scope, return home and
     * context authority are unobservable; its statements are then
     * lowered through the general Bytecode DSL machinery. The argument
     * must be a literal Closure, so omitting its physical
     * materialization cannot suppress an argument expression's effects.
     *
     * A failed proof retains the established B-prime path.
     */
    private boolean admitsNormalInlineControlBody(
            CanonicalClosure closure, ControlSiteContext context) {
        if (!closure.parameters().isEmpty()
                || compilationFacts.mayObserveReturnHome(closure)) {
            if (Boolean.getBoolean("protos.i115.traceInlineAdmission")) {
                System.err.println("I115_INLINE_PROOF=REJECT_EARLY span=" + closure.span()
                        + " params=" + closure.parameters().size()
                        + " returnHome=" + compilationFacts.mayObserveReturnHome(closure));
            }
            return false;
        }

        /*
         * I115: an inline callback keeps its own bindings in plain locals of
         * the physical root. A callback owning a cell binding (captured by a
         * Closure nested in it) stays a genuine Closure, whose root creates
         * that cell.
         */
        CanonicalLexicalScope wholeTreeScope =
                context.analysis() == null
                        ? null
                        : context.analysis().scopeOf(closure).orElse(null);
        if (wholeTreeScope != null
                && !wholeTreeScope.cellNamesInDeclarationOrder().isEmpty()) {
            return false;
        }

        // Deliberately isolated: the own-binding and direct-parent-capture
        // proofs below compare it against the current root's analysis.
        CanonicalBindingAnalysis analysis =
                compilationFacts.isolatedClosureAnalysis(closure);
        CanonicalLexicalScope scope =
                analysis.scopeOf(closure).orElseThrow();

        java.util.Set<String> scalarNames = scalarLocalNamesForRoot(
                closure.body(), closure, analysis, scope);
        boolean ownScalarBindings =
                !scope.declaredNames().isEmpty()
                        && scalarNames.equals(scope.declaredNames());
        boolean noOwnBindingsAndProvenCaptures =
                scope.declaredNames().isEmpty()
                        && analysis.admitsDirectParentCaptures(
                                context.analysis(),
                                context.topScope(),
                                context.frameNames(),
                                context.hasFrameLayout());

        boolean mixedOwnBindingsAndParentCaptures =
                !scope.declaredNames().isEmpty()
                        && admitsNormalMixedCallbackOwnCreations(
                                closure.body(), analysis, scope)
                        && analysis.admitsOwnBindingsAndDirectParentCaptures(
                                context.analysis(),
                                scope,
                                context.topScope(),
                                context.frameNames(),
                                context.hasFrameLayout());

        boolean requiresPersistentAuthority = compilationFacts.rootFacts(
                        closure.body(), closure, analysis, scope)
                .requiresPersistentFrameAuthority();
        boolean admitted = (ownScalarBindings
                        || noOwnBindingsAndProvenCaptures
                        || mixedOwnBindingsAndParentCaptures)
                && !requiresPersistentAuthority;
        if (Boolean.getBoolean("protos.i115.traceInlineAdmission")) {
            System.err.println("I115_INLINE_PROOF=" + (admitted ? "ADMIT" : "REJECT")
                    + " span=" + closure.span()
                    + " params=" + closure.parameters().size()
                    + " ownNames=" + scope.declaredNames()
                    + " scalarNames=" + scalarNames
                    + " ownScalar=" + ownScalarBindings
                    + " directCaptures=" + noOwnBindingsAndProvenCaptures
                    + " mixed=" + mixedOwnBindingsAndParentCaptures
                    + " persistentAuthority=" + requiresPersistentAuthority
                    + " parentScope=" + context.topScope()
                    + " parentFrameNames=" + context.frameNames()
                    + " parentHasFrameLayout=" + context.hasFrameLayout());
        }
        return admitted;
    }

    /**
     * I095-B6b: prove that every own binding is created once, at top level,
     * before any admitted own read or write. The isolated binding analysis
     * supplies presence proof for reads/writes; the exact create-site count
     * excludes hidden creations inside an operand. Direct owner creation and
     * assignment deliberately remain scalar-value-shaped in this slice.
     * All other statements can use the existing general body lowering.
     */
    private static boolean admitsNormalMixedCallbackOwnCreations(
            CanonicalSequence body,
            CanonicalBindingAnalysis analysis,
            CanonicalLexicalScope scope) {
        java.util.LinkedHashSet<String> established =
                new java.util.LinkedHashSet<>();
        int createSites = 0;
        for (CanonicalExpression expression : body.expressions()) {
            if (expression instanceof CanonicalCreate create) {
                if (create.target().isPresent()
                        || !(create.value() instanceof CanonicalLiteral
                                || (create.value() instanceof CanonicalLookup read
                                        && readsEstablishedScalar(
                                                read, analysis, scope, established)))
                        || analysis.identityOf(create)
                                .map(identity -> identity.owner() != scope
                                        || !identity.name().equals(create.name()))
                                .orElse(true)
                        || !established.add(create.name())) {
                    return false;
                }
                createSites++;
                continue;
            }
            if (expression instanceof CanonicalAssign assign) {
                if (assign.target().isPresent()
                        || containsMutationInMixedInlineOperand(assign.value())) {
                    return false;
                }
                if (scope.declaresName(assign.name())
                        && !(assign.value() instanceof CanonicalLiteral
                                || (assign.value() instanceof CanonicalLookup read
                                        && readsEstablishedScalar(
                                                read, analysis, scope, established)))) {
                    return false;
                }
                continue;
            }
            if (containsMutationInMixedInlineOperand(expression)) {
                return false;
            }
        }
        return !established.isEmpty()
                && established.equals(scope.declaredNames())
                && analysis.lexicalCreateSiteCount() == createSites;
    }

    /** Reject nested binding mutations; ordinary side-effectful sends stay allowed. */
    private static boolean containsMutationInMixedInlineOperand(
            CanonicalExpression expression) {
        return switch (expression) {
            case CanonicalCreate ignored -> true;
            case CanonicalMultipleCreate ignored -> true;
            case CanonicalAssign ignored -> true;
            case CanonicalIndexedAssign ignored -> true;
            case CanonicalClosure ignored -> true;
            case CanonicalObject ignored -> true;
            case CanonicalCompose ignored -> true;
            case CanonicalSequence sequence ->
                    sequence.expressions().stream()
                            .anyMatch(CanonicalToBytecodeLowerer::containsMutationInMixedInlineOperand);
            case CanonicalCall call ->
                    containsMutationInMixedInlineOperand(call.receiver())
                            || call.arguments().stream().anyMatch(
                                    CanonicalToBytecodeLowerer::containsMutationInMixedInlineOperand);
            case CanonicalSend send ->
                    containsMutationInMixedInlineOperand(send.receiver())
                            || send.arguments().stream().anyMatch(
                                    CanonicalToBytecodeLowerer::containsMutationInMixedInlineOperand);
            case CanonicalSuperSend send ->
                    send.arguments().stream().anyMatch(
                            CanonicalToBytecodeLowerer::containsMutationInMixedInlineOperand);
            case CanonicalMember member ->
                    containsMutationInMixedInlineOperand(member.receiver());
            case CanonicalIdentity identity ->
                    containsMutationInMixedInlineOperand(identity.left())
                            || containsMutationInMixedInlineOperand(identity.right());
            case CanonicalNotIdentity identity ->
                    containsMutationInMixedInlineOperand(identity.left())
                            || containsMutationInMixedInlineOperand(identity.right());
            case CanonicalDerivedInequality inequality ->
                    containsMutationInMixedInlineOperand(inequality.left())
                            || containsMutationInMixedInlineOperand(inequality.right());
            case CanonicalMapConstruction map ->
                    containsMutationInMixedInlineOperand(map.factory())
                            || map.entries().stream().anyMatch(entry ->
                                    containsMutationInMixedInlineOperand(entry.key())
                                            || containsMutationInMixedInlineOperand(entry.value()));
            case CanonicalReturn result ->
                    containsMutationInMixedInlineOperand(result.value());
            case CanonicalSpread spread ->
                    containsMutationInMixedInlineOperand(spread.expression());
            case CanonicalNamedArgument ignored -> true;
            case CanonicalIntrinsic ignored -> false;
            case CanonicalLiteral ignored -> false;
            case CanonicalLookup ignored -> false;
        };
    }

    /**
     * I101-A (PLAT058-D) complete fold of a constant Boolean-control site:
     * the literal that is the site's exact normal result in every execution,
     * or {@code null} when that is not proven.
     *
     * <p>The proof composes existing ones without weakening them. The site is
     * admitted by {@link #normalBooleanControlSite} (standard
     * root published in this lowerer's snapshot, standard selector and
     * arity, no spread). Its receiver is a canonical Boolean literal and
     * every argument is a literal Closure, so no operand evaluation exists to
     * preserve and no Closure identity can be observed (an unselected or
     * inline literal is never materialized, I095-C). The selected callback's
     * path is INLINE, which carries the I095-B proof of its whole activation
     * (no parameters, unobservable ReturnHome and lexical scope), and its row
     * returns the callback's exact result (ifTrue, ifFalse, ifTrueIfFalse).
     * Finally the callback body is one literal accepted by {@link
     * #isNormalPureControlExpression}: evaluating it has no effect, cannot
     * fail, suspend or observe any Context, and its value does not depend on
     * the activation evaluating it. The site therefore evaluates exactly to
     * that literal, which is emitted in place of the whole send.
     *
     * <p>I107: the fold composes. When the selected callback's only
     * statement is itself a foldable site, the callback evaluates exactly to
     * that site's literal with no effect; the callback is then as
     * unobservable as a literal-bodied one (no parameters, no own bindings,
     * unobservable ReturnHome, and none of its nested literals is ever
     * materialized), so the outer site folds to the same literal at any
     * nesting depth. The planner computes the fold before the builder opens,
     * under the root-entry control context.
     */
    private CanonicalLiteral foldedConstantControlResult(
            CanonicalExpression expression, ControlSiteContext context) {
        if (!(expression instanceof CanonicalSend send)
                || !(send.receiver() instanceof CanonicalLiteral receiver)
                || !send.arguments().stream()
                        .allMatch(CanonicalClosure.class::isInstance)) {
            return null;
        }
        NormalBooleanControl control = normalBooleanControlRow(send, context);
        if (control == null
                || control.selectedResult != NormalBooleanControl.SelectedResult.EXACT
                || (receiver.kind() != CanonicalLiteral.Kind.TRUE
                        && receiver.kind() != CanonicalLiteral.Kind.FALSE)) {
            return null;
        }
        boolean value = receiver.kind() == CanonicalLiteral.Kind.TRUE;
        int selectedIndex = control.selectedIndex(value);
        if (selectedIndex < 0) {
            return null;
        }
        CanonicalClosure selected = (CanonicalClosure) send.arguments().get(selectedIndex);
        java.util.List<CanonicalExpression> body =
                selected.body().expressions();
        if (body.size() != 1) {
            return null;
        }
        if (body.get(0) instanceof CanonicalLiteral literal) {
            NormalBooleanControlSite site = normalBooleanControlSite(send, context);
            return site != null
                            && site.path(value) == NormalBooleanControlPath.INLINE
                            && isNormalPureControlExpression(literal)
                    ? literal
                    : null;
        }
        if (!selected.parameters().isEmpty()
                || compilationFacts.mayObserveReturnHome(selected)
                || !compilationFacts.isolatedClosureAnalysis(selected)
                        .scopeOf(selected)
                        .map(scope -> scope.declaredNames().isEmpty())
                        .orElse(false)) {
            return null;
        }
        return foldedConstantControlResult(body.get(0), context);
    }

    private boolean isNormalPureControlExpression(
            CanonicalExpression expression) {
        if (expression instanceof CanonicalLiteral literal) {
            return switch (literal.kind()) {
                case TRUE, FALSE, NULL -> true;
                case NUMBER ->
                        !(numberLiteralDescriptor(literal)
                                instanceof BigInteger);
                case STRING -> false;
            };
        }

        if (expression instanceof CanonicalIdentity identity) {
            return isNormalPureControlExpression(identity.left())
                    && isNormalPureControlExpression(identity.right());
        }

        if (expression instanceof CanonicalNotIdentity identity) {
            return isNormalPureControlExpression(identity.left())
                    && isNormalPureControlExpression(identity.right());
        }

        return false;
    }

    /**
     * I115-M/I115-N EXPERIMENTAL: the value family an admitted compact
     * expression produces. Bindings carry the family of their last admitted
     * store on every path; Boolean control requires a BOOLEAN receiver, and
     * OBJECT is any admitted value (null, a parameter, a joined family, a
     * control result). I115-H1-FIX: a numeric send is dispatched at run
     * time, so it accepts operands of any family; arithmetic produces
     * OBJECT, since its MISS path may select a method returning any value.
     * A comparison produces COMPARISON: canonical Boolean on its HIT, any
     * value on its MISS, so Boolean control over it is guarded at run time.
     * I115-H7: GLOBAL is a bare read of a module (ROOT) binding, lowered to
     * ReadGlobal and never registered as a local of the root; METHOD_RESULT
     * is the result of an ordinary one-argument send lowered to GetMethod
     * and CallBinaryMethod, any value at run time (the operation's
     * specialization may produce a primitive).
     */
    private enum ExperimentalCompactKind {
        INTEGER, BOOLEAN, COMPARISON, OBJECT, GLOBAL, METHOD_RESULT
    }

    /**
     * I115-M EXPERIMENTAL: compilation fact that every expression of a
     * parameterless Closure root belongs to the compact subset over the
     * bindings of {@code scope}. It carries no physical or runtime state.
     * I115-O: {@code guestIntegerReturn} is the returned binding proven by
     * admitExperimentalConstantIntegerReturn, or null.
     * I115-Q: {@code guestIntegerLiteralReturn} is true when the terminal is
     * a signed-64 Integer literal returned as a prepared guest constant.
     * I115-H1-FIX: {@code dispatches} is true when the root contains a
     * numeric send or a dispatching Boolean control, whose MISS path
     * performs an ordinary send; {@code dispatchingControls} are the Boolean
     * control sites whose receiver is not proven canonical Boolean.
     * I115-H7-R1: {@code invokes} is true when the root contains an H7 send
     * (NumberAdd, NumberInPlaceAdd, Lt or a method send) whose generic route
     * performs an ordinary send; such a root publishes no non-suspension
     * refinement, exactly like a pre-H7 root with a numeric send site.
     */
    private record ExperimentalCompactRoot(
            CanonicalLexicalScope scope,
            CanonicalBindingIdentity guestIntegerReturn,
            boolean guestIntegerLiteralReturn,
            boolean dispatches,
            boolean experimentalRecursiveInteger,
            java.util.Set<CanonicalSend> dispatchingControls,
            boolean invokes) {}

    /*
     * I115-N: compile-time binding families keyed by binding identity. A
     * missing identity is a binding not yet established on this path.
     */
    private static final class ExperimentalCompactKinds
            extends java.util.HashMap<CanonicalBindingIdentity, ExperimentalCompactKind> {
        private static final long serialVersionUID = 1L;

        ExperimentalCompactKinds() {}

        ExperimentalCompactKinds(ExperimentalCompactKinds other) {
            super(other);
        }

        /* Replace this state by the join of two path states. */
        void joinFrom(ExperimentalCompactKinds left, ExperimentalCompactKinds right) {
            clear();
            for (java.util.Map.Entry<CanonicalBindingIdentity, ExperimentalCompactKind> entry
                    : left.entrySet()) {
                ExperimentalCompactKind other = right.get(entry.getKey());
                if (other != null) {
                    put(entry.getKey(), experimentalCompactJoin(entry.getValue(), other));
                }
            }
        }
    }

    private static ExperimentalCompactKind experimentalCompactJoin(
            ExperimentalCompactKind left,
            ExperimentalCompactKind right) {
        if (left == right) {
            return left;
        }
        // A proven canonical Boolean joined with a comparison stays guarded.
        boolean booleanLike =
                (left == ExperimentalCompactKind.BOOLEAN
                                || left == ExperimentalCompactKind.COMPARISON)
                        && (right == ExperimentalCompactKind.BOOLEAN
                                || right == ExperimentalCompactKind.COMPARISON);
        return booleanLike
                ? ExperimentalCompactKind.COMPARISON
                : ExperimentalCompactKind.OBJECT;
    }

    /**
     * I115-M EXPERIMENTAL: complete, pre-builder admission of the whole root.
     * Either every expression is compact or the root keeps the general
     * lowerer. Deliberately ignores guest lexical authority, callback
     * identity and dynamic selector selection.
     */
    private ExperimentalCompactRoot admitExperimentalCompactRoot(
            CanonicalSequence sequence,
            CanonicalClosure activationDefinition,
            CanonicalCompilationFacts.RootFacts rootFacts) {
        if (activationDefinition == null
                || sequence.expressions().isEmpty()
                || currentRootAnalysis == null
                || rootFacts.scope() == null) {
            return null;
        }
        CanonicalLexicalScope scope = rootFacts.scope();
        /*
         * I115: the compact subset stores every binding with StoreLocal.
         * A root whose bindings include a cell (captured by a Closure,
         * including a callback materialized on a MISS path) is not compact.
         */
        if (scope.kind() != CanonicalLexicalScope.Kind.CLOSURE
                || !scope.cellNamesInDeclarationOrder().isEmpty()) {
            return null;
        }
        java.util.List<CanonicalParameter> parameters =
                activationDefinition.parameters();

        // I115-R1 EXPERIMENTAL: admit one required parameter using the
        // existing unary frame entry. I115-H1-FIX: its lexical identity
        // proves which binding is read, not the family of the supplied
        // value, so it is an OBJECT. Calls, defaults, rest and arbitrary
        // body expressions remain outside the compact subset.
        boolean unaryRequired = parameters.size() == 1
                && !parameters.get(0).rest()
                && parameters.get(0).defaultValue().isEmpty()
                && scope.declaresName(parameters.get(0).name());

        if (!parameters.isEmpty() && !unaryRequired) {
            return null;
        }
        boolean experimentalRecursiveInteger =
                unaryRequired
                        && activationDefinition == currentExperimentalRootDefinition
                        && experimentalContainsRecursiveSelfCall(sequence);

        /*
         * I115-H1-FIX: the callbacks of a dispatching Boolean control may
         * be invoked by its selected method any number of times, now or
         * later, so every binding they assign is admitted as OBJECT
         * everywhere. Pinning only widens families, which can only add
         * dispatching sites: iterate to the fixpoint.
         */
        boolean savedRecursiveAdmission =
                currentExperimentalRecursiveIntegerAdmission;
        currentExperimentalRecursiveIntegerAdmission =
                experimentalRecursiveInteger;
        java.util.Set<CanonicalBindingIdentity> pinned = new java.util.HashSet<>();
        java.util.Set<CanonicalSend> dispatchingControls;
        try {
            while (true) {
                currentExperimentalCompactPinned = pinned;
                currentExperimentalCompactEscaped = new java.util.HashSet<>();
                currentExperimentalCompactDispatchingControls =
                        java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
                ExperimentalCompactKinds kinds = new ExperimentalCompactKinds();
                if (unaryRequired) {
                    kinds.put(
                            scope.identity(parameters.get(0).name()),
                            experimentalRecursiveInteger
                                    ? ExperimentalCompactKind.INTEGER
                                    : ExperimentalCompactKind.OBJECT);
                }
                if (admitExperimentalCompactSequence(
                                sequence, scope, kinds, false) == null) {
                    return null;
                }
                if (pinned.containsAll(currentExperimentalCompactEscaped)) {
                    dispatchingControls = currentExperimentalCompactDispatchingControls;
                    break;
                }
                pinned.addAll(currentExperimentalCompactEscaped);
            }
        } finally {
            currentExperimentalCompactPinned = null;
            currentExperimentalCompactEscaped = null;
            currentExperimentalCompactDispatchingControls = null;
            currentExperimentalRecursiveIntegerAdmission =
                    savedRecursiveAdmission;
        }
        CanonicalExpression terminal =
                sequence.expressions().get(sequence.expressions().size() - 1);
        boolean guestIntegerLiteralReturn =
                terminal instanceof CanonicalLiteral literal
                        && literal.kind() == CanonicalLiteral.Kind.NUMBER
                        && primitiveNumberLiteralOrNull(literal) instanceof Long;
        boolean dispatches =
                !experimentalRecursiveInteger && !dispatchingControls.isEmpty();
        if (!experimentalRecursiveInteger) {
            for (CanonicalExpression expression : sequence.expressions()) {
                dispatches |= experimentalCompactDispatches(expression);
            }
        }
        // A binding a physical callback root may store keeps carrier projection.
        CanonicalBindingIdentity guestIntegerReturn =
                admitExperimentalConstantIntegerReturn(sequence, scope);
        if (guestIntegerReturn != null && pinned.contains(guestIntegerReturn)) {
            guestIntegerReturn = null;
        }
        boolean invokes = false;
        if (!experimentalRecursiveInteger) {
            for (CanonicalExpression expression : sequence.expressions()) {
                invokes |= experimentalCompactInvokes(expression);
            }
        }
        return new ExperimentalCompactRoot(
                scope,
                guestIntegerReturn,
                guestIntegerLiteralReturn,
                dispatches,
                experimentalRecursiveInteger,
                java.util.Collections.unmodifiableSet(dispatchingControls),
                invokes);
    }

    /*
     * I115-H1-FIX: the identities a callback body assigns, at any depth of
     * its admitted expressions and nested inline callbacks.
     */
    private void collectExperimentalCompactAssigned(
            CanonicalExpression expression,
            CanonicalLexicalScope scope,
            java.util.Set<CanonicalBindingIdentity> assigned) {
        if (expression instanceof CanonicalClosure callback) {
            for (CanonicalExpression inner : callback.body().expressions()) {
                collectExperimentalCompactAssigned(inner, scope, assigned);
            }
        } else if (expression instanceof CanonicalAssign assign) {
            CanonicalBindingIdentity identity =
                    experimentalCompactStoreIdentity(assign, scope);
            if (identity != null) {
                assigned.add(identity);
            }
            collectExperimentalCompactAssigned(assign.value(), scope, assigned);
        } else if (expression instanceof CanonicalCreate create) {
            collectExperimentalCompactAssigned(create.value(), scope, assigned);
        } else if (expression instanceof CanonicalSend send) {
            collectExperimentalCompactAssigned(send.receiver(), scope, assigned);
            for (CanonicalExpression argument : send.arguments()) {
                collectExperimentalCompactAssigned(argument, scope, assigned);
            }
        }
    }

    /*
     * I115-H1-FIX: whether an admitted compact expression, including its
     * inline callback bodies, contains a numeric send.
     */
    private static boolean experimentalCompactDispatches(
            CanonicalExpression expression) {
        return experimentalCompactDispatches(expression, false);
    }

    /*
     * I115-H7-R1: whether an admitted expression, including its inline
     * callback bodies, contains any send other than whileTrue and the
     * structured Boolean controls themselves.
     */
    private static boolean experimentalCompactInvokes(CanonicalExpression expression) {
        if (expression instanceof CanonicalCreate create) {
            return experimentalCompactInvokes(create.value());
        }
        if (expression instanceof CanonicalAssign assign) {
            return assign.target().isPresent()
                    || experimentalCompactInvokes(assign.value());
        }
        if (expression instanceof CanonicalMember) {
            // H8: explicit member access participates in compact return transport.
            return true;
        }
        if (!(expression instanceof CanonicalSend send)) {
            return false;
        }
        if (!"whileTrue".equals(send.message())
                && experimentalCompactBooleanControl(send.message()) == null) {
            return true;
        }
        java.util.List<CanonicalExpression> parts =
                new java.util.ArrayList<>(send.arguments().size() + 1);
        parts.add(send.receiver());
        parts.addAll(send.arguments());
        for (CanonicalExpression part : parts) {
            java.util.List<CanonicalExpression> nested =
                    part instanceof CanonicalClosure callback
                            ? callback.body().expressions()
                            : java.util.List.of(part);
            for (CanonicalExpression inner : nested) {
                if (experimentalCompactInvokes(inner)) {
                    return true;
                }
            }
        }
        return false;
    }

    /*
     * I115-H7: NumberAdd ({@code +}), Lt (the {@code <} ending a whileTrue
     * condition) and an ordinary method send (GetMethod, CallBinaryMethod)
     * keep their generic route inside the operation, so they are not
     * dispatching sites; only their operands are inspected.
     */
    private static boolean experimentalCompactDispatches(
            CanonicalExpression expression,
            boolean whileCondition) {
        if (expression instanceof CanonicalCreate create) {
            return experimentalCompactDispatches(create.value());
        }
        if (expression instanceof CanonicalAssign assign) {
            return experimentalCompactDispatches(assign.value());
        }
        if (!(expression instanceof CanonicalSend send)) {
            return false;
        }
        String selector = send.message();
        if (!"whileTrue".equals(selector)
                && experimentalCompactBooleanControl(selector) == null) {
            boolean h7 = experimentalCompactArithmetic(selector)
                    || (whileCondition && "<".equals(selector))
                    || experimentalCompactMethodSend(send);
            return !h7
                    || experimentalCompactDispatches(send.receiver())
                    || experimentalCompactDispatches(send.arguments().get(0));
        }
        java.util.List<CanonicalExpression> parts =
                new java.util.ArrayList<>(send.arguments().size() + 1);
        parts.add(send.receiver());
        parts.addAll(send.arguments());
        for (CanonicalExpression part : parts) {
            java.util.List<CanonicalExpression> nested =
                    part instanceof CanonicalClosure callback
                            ? callback.body().expressions()
                            : java.util.List.of(part);
            boolean condition = "whileTrue".equals(selector)
                    && part == send.receiver()
                    && part instanceof CanonicalClosure;
            for (int index = 0; index < nested.size(); index++) {
                if (experimentalCompactDispatches(
                        nested.get(index),
                        condition && index == nested.size() - 1)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * I115-O EXPERIMENTAL: the identity of the binding an admitted compact
     * root returns when that binding can hold prepared guest constants
     * instead of primitive carriers, or null. The terminal must be a lookup
     * of a root-scope binding; every store to it anywhere in the root
     * (including inline callback and loop bodies) must be a statement whose
     * value is an Integer (signed-64), null, true or false literal, with at
     * least one Integer; and no other read of it may exist. Admission of the
     * root already established the binding before every use.
     */
    private CanonicalBindingIdentity admitExperimentalConstantIntegerReturn(
            CanonicalSequence sequence,
            CanonicalLexicalScope scope) {
        java.util.List<CanonicalExpression> expressions = sequence.expressions();
        if (!(expressions.get(expressions.size() - 1) instanceof CanonicalLookup terminal)) {
            return null;
        }
        CanonicalBindingIdentity identity = experimentalCompactIdentity(
                currentRootAnalysis.resolutionOf(terminal).orElse(null),
                scope,
                terminal.name());
        if (identity == null) {
            return null;
        }
        int[] integerStores = {0};
        for (int index = 0; index < expressions.size() - 1; index++) {
            if (!experimentalGuestReturnScan(
                    expressions.get(index), false, scope, identity, integerStores)) {
                return null;
            }
        }
        return integerStores[0] > 0 ? identity : null;
    }

    /*
     * I115-O: scan one admitted compact expression. {@code value} is true
     * when the expression's result may be consumed; the last expression of
     * a callback body is treated as consumed whenever its control is.
     */
    private boolean experimentalRecursiveSelfCall(CanonicalCall call) {
        if (currentExperimentalRootDefinition == null
                || currentRootAnalysis == null
                || call.arguments().size() != 1
                || !(call.receiver() instanceof CanonicalLookup lookup)) {
            return false;
        }

        CanonicalBindingIdentity registered =
                experimentalRecursiveBindings.get(
                        currentExperimentalRootDefinition);

        CanonicalBindingResolution resolution =
                currentRootAnalysis.resolutionOf(lookup).orElse(null);

        CanonicalBindingIdentity resolved = null;
        int depth = -1;

        if (resolution
                instanceof CanonicalBindingResolution.CapturedResolved captured) {
            resolved = captured.identity();
            depth = captured.lexicalDepth();
        } else if (resolution
                instanceof CanonicalBindingResolution.Candidate candidate) {
            resolved = candidate.identity();
            depth = candidate.lexicalDepth();
        }

        boolean admitted =
                registered != null
                        && registered.owner().kind()
                                == CanonicalLexicalScope.Kind.ROOT
                        && registered.name().equals(lookup.name())
                        && resolved != null
                        && resolved.owner().kind()
                                == CanonicalLexicalScope.Kind.ROOT
                        && resolved.name().equals(registered.name())
                        && depth > 0
                        && currentRootAnalysis
                                .scopeOf(currentExperimentalRootDefinition)
                                .isPresent();

        return admitted;
    }


    /*
     * I115-H2: select the experimental Integer lane only if this root
     * actually contains an admitted static self-call. A module binding
     * alone is insufficient.
     */
    private boolean experimentalContainsRecursiveSelfCall(
            CanonicalSequence sequence) {
        for (CanonicalExpression expression : sequence.expressions()) {
            if (experimentalContainsRecursiveSelfCall(expression)) {
                return true;
            }
        }
        return false;
    }

    private boolean experimentalContainsRecursiveSelfCall(
            CanonicalExpression expression) {
        if (expression instanceof CanonicalCall call) {
            return experimentalRecursiveSelfCall(call);
        }
        if (expression instanceof CanonicalClosure closure) {
            return experimentalContainsRecursiveSelfCall(closure.body());
        }
        if (expression instanceof CanonicalCreate create) {
            return experimentalContainsRecursiveSelfCall(create.value());
        }
        if (expression instanceof CanonicalAssign assign) {
            return experimentalContainsRecursiveSelfCall(assign.value());
        }
        if (expression instanceof CanonicalSend send) {
            if (experimentalContainsRecursiveSelfCall(send.receiver())) {
                return true;
            }
            for (CanonicalExpression argument : send.arguments()) {
                if (experimentalContainsRecursiveSelfCall(argument)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean experimentalGuestReturnScan(
            CanonicalExpression expression,
            boolean value,
            CanonicalLexicalScope scope,
            CanonicalBindingIdentity identity,
            int[] integerStores) {
        if (expression instanceof CanonicalLiteral) {
            return true;
        }
        if (expression instanceof CanonicalLookup lookup) {
            return !identity.equals(experimentalCompactIdentity(
                    currentRootAnalysis.resolutionOf(lookup).orElse(null),
                    scope,
                    lookup.name()));
        }
        if (expression instanceof CanonicalCreate
                || expression instanceof CanonicalAssign) {
            CanonicalExpression stored = expression instanceof CanonicalCreate create
                    ? create.value()
                    : ((CanonicalAssign) expression).value();
            if (!identity.equals(experimentalCompactStoreIdentity(expression, scope))) {
                return experimentalGuestReturnScan(
                        stored, true, scope, identity, integerStores);
            }
            if (value || !(stored instanceof CanonicalLiteral literal)) {
                return false;
            }
            return switch (literal.kind()) {
                case NULL, TRUE, FALSE -> true;
                case NUMBER -> {
                    if (!(primitiveNumberLiteralOrNull(literal) instanceof Long)) {
                        yield false;
                    }
                    integerStores[0]++;
                    yield true;
                }
                default -> false;
            };
        }
        if (!(expression instanceof CanonicalSend send)) {
            return false;
        }
        if ("whileTrue".equals(send.message())
                || experimentalCompactBooleanControl(send.message()) != null) {
            if (!(send.receiver() instanceof CanonicalClosure)
                    && !experimentalGuestReturnScan(
                            send.receiver(), true, scope, identity, integerStores)) {
                return false;
            }
            java.util.List<CanonicalSequence> bodies = new java.util.ArrayList<>();
            if (send.receiver() instanceof CanonicalClosure condition) {
                bodies.add(condition.body());
            }
            for (CanonicalExpression argument : send.arguments()) {
                bodies.add(((CanonicalClosure) argument).body());
            }
            for (CanonicalSequence body : bodies) {
                java.util.List<CanonicalExpression> statements = body.expressions();
                for (int index = 0; index < statements.size(); index++) {
                    boolean last = index == statements.size() - 1;
                    if (!experimentalGuestReturnScan(
                            statements.get(index), value && last,
                            scope, identity, integerStores)) {
                        return false;
                    }
                }
            }
            return true;
        }
        for (CanonicalExpression operand : java.util.List.of(
                send.receiver(), send.arguments().get(0))) {
            if (!experimentalGuestReturnScan(
                    operand, true, scope, identity, integerStores)) {
                return false;
            }
        }
        return true;
    }

    /* I115-O: the root-scope identity a compact create or assign stores. */
    private CanonicalBindingIdentity experimentalCompactStoreIdentity(
            CanonicalExpression binding,
            CanonicalLexicalScope scope) {
        if (binding instanceof CanonicalCreate create) {
            return currentRootAnalysis.identityOf(create).orElse(null);
        }
        CanonicalAssign assign = (CanonicalAssign) binding;
        return experimentalCompactIdentity(
                currentRootAnalysis.resolutionOf(assign).orElse(null),
                scope,
                assign.name());
    }

    /* An empty sequence produces null (OBJECT). */
    private ExperimentalCompactKind admitExperimentalCompactSequence(
            CanonicalSequence sequence,
            CanonicalLexicalScope scope,
            ExperimentalCompactKinds kinds,
            boolean callback) {
        ExperimentalCompactKind kind = ExperimentalCompactKind.OBJECT;
        for (CanonicalExpression expression : sequence.expressions()) {
            kind = admitExperimentalCompactExpression(
                    expression, scope, kinds, callback);
            if (kind == null) {
                return null;
            }
        }
        return kind;
    }

    private ExperimentalCompactKind admitExperimentalCompactExpression(
            CanonicalExpression expression,
            CanonicalLexicalScope scope,
            ExperimentalCompactKinds kinds,
            boolean callback) {
        if (expression instanceof CanonicalLiteral literal) {
            return switch (literal.kind()) {
                case TRUE, FALSE -> ExperimentalCompactKind.BOOLEAN;
                case NULL -> ExperimentalCompactKind.OBJECT;
                case NUMBER -> primitiveNumberLiteralOrNull(literal) instanceof Long
                        ? ExperimentalCompactKind.INTEGER
                        : null;
                default -> null;
            };
        }
        if (expression instanceof CanonicalLookup lookup) {
            CanonicalBindingIdentity identity =
                    experimentalCompactIdentity(
                            currentRootAnalysis.resolutionOf(lookup).orElse(null),
                            scope,
                            lookup.name());
            if (identity == null) {
                // I115-H7: a module binding is read in place, never localized.
                return !currentExperimentalRecursiveIntegerAdmission
                                && experimentalCompactGlobal(lookup, scope) != null
                        ? ExperimentalCompactKind.GLOBAL
                        : null;
            }
            return kinds.get(identity);
        }
        if (expression instanceof CanonicalMember member) {
            // I115-H8: admit an ordinary member read from a captured
            // module binding, without the generic member-read lowerer.
            if (currentExperimentalRecursiveIntegerAdmission
                    || !(member.receiver() instanceof CanonicalLookup receiver)
                    || experimentalCompactGlobal(receiver, scope) == null) {
                return null;
            }
            return ExperimentalCompactKind.OBJECT;
        }
        if (expression instanceof CanonicalCreate
                || expression instanceof CanonicalAssign) {
            return admitExperimentalCompactBinding(
                    expression, scope, kinds, callback);
        }
        if (expression instanceof CanonicalCall) {
            // I115: calls are made only through the shared dispatcher.
            return null;
        }
        if (!(expression instanceof CanonicalSend send)) {
            return null;
        }
        if ("whileTrue".equals(send.message())) {
            return admitExperimentalCompactWhile(send, scope, kinds);
        }
        if (experimentalCompactBooleanControl(send.message()) != null) {
            return admitExperimentalCompactBooleanControl(
                    send, scope, kinds, callback);
        }
        if (!currentExperimentalRecursiveIntegerAdmission
                && experimentalCompactMethodSend(send)) {
            // I115-H7: receiver then argument, each admitted and evaluated once.
            if (admitExperimentalCompactExpression(
                            send.receiver(), scope, kinds, callback) == null
                    || admitExperimentalCompactExpression(
                                    send.arguments().get(0), scope, kinds, callback)
                            == null) {
                return null;
            }
            return ExperimentalCompactKind.METHOD_RESULT;
        }

        boolean arithmetic = experimentalCompactArithmetic(send.message());

        if ((!arithmetic && !experimentalCompactComparison(send.message()))
                || send.arguments().size() != 1) {
            return null;
        }

        ExperimentalCompactKind receiverKind =
                admitExperimentalCompactExpression(
                        send.receiver(), scope, kinds, callback);

        if (receiverKind == null
                || (currentExperimentalRecursiveIntegerAdmission
                        && receiverKind != ExperimentalCompactKind.INTEGER)) {
            return null;
        }

        ExperimentalCompactKind argumentKind =
                admitExperimentalCompactExpression(
                        send.arguments().get(0), scope, kinds, callback);

        if (argumentKind == null
                || (currentExperimentalRecursiveIntegerAdmission
                        && argumentKind != ExperimentalCompactKind.INTEGER)) {
            return null;
        }

        if (currentExperimentalRecursiveIntegerAdmission) {
            return arithmetic
                    ? ExperimentalCompactKind.INTEGER
                    : ExperimentalCompactKind.BOOLEAN;
        }

        // H1-FIX remains the default: dynamic numeric dispatch.
        return experimentalCompactComparison(send.message())
                ? ExperimentalCompactKind.COMPARISON
                : ExperimentalCompactKind.OBJECT;
    }

    /**
     * I115-M/I115-N: an admitted binding is created at the root level of the
     * physical scope and only assigned (never created) inside inline
     * callbacks. Each store records the family of its admitted value.
     */
    private ExperimentalCompactKind admitExperimentalCompactBinding(
            CanonicalExpression binding,
            CanonicalLexicalScope scope,
            ExperimentalCompactKinds kinds,
            boolean callback) {
        if (binding instanceof CanonicalCreate create) {
            if (callback || create.target().isPresent()) {
                return null;
            }
            CanonicalBindingIdentity identity =
                    currentRootAnalysis.identityOf(create).orElse(null);
            if (identity == null
                    || identity.owner() != scope
                    || !identity.name().equals(create.name())
                    || !scope.declaresName(create.name())
                    || kinds.containsKey(identity)) {
                return null;
            }
            ExperimentalCompactKind kind = admitExperimentalCompactExpression(
                    create.value(), scope, kinds, false);
            if (kind == null) {
                return null;
            }
            kinds.put(identity, experimentalCompactStoredKind(identity, kind));
            return kind;
        }
        if (binding instanceof CanonicalAssign assign) {
            if (assign.target().isPresent()) {
                // I115-H8: compact explicit assignment to an existing
                // member of a captured module object. The destination
                // is evaluated before the RHS, but its full semantic
                // validation is deliberately delegated to SetAttribute.
                if (currentExperimentalRecursiveIntegerAdmission
                        || !(assign.target().orElseThrow()
                                instanceof CanonicalLookup receiver)
                        || experimentalCompactGlobal(receiver, scope) == null) {
                    return null;
                }
                return admitExperimentalCompactExpression(
                        assign.value(), scope, kinds, callback);
            }
            CanonicalBindingIdentity identity =
                    experimentalCompactIdentity(
                            currentRootAnalysis.resolutionOf(assign).orElse(null),
                            scope,
                            assign.name());
            if (identity == null || !kinds.containsKey(identity)) {
                return null;
            }
            ExperimentalCompactKind kind = admitExperimentalCompactExpression(
                    assign.value(), scope, kinds, callback);
            if (kind == null) {
                return null;
            }
            kinds.put(identity, experimentalCompactStoredKind(identity, kind));
            return kind;
        }
        return null;
    }

    /* I115-H1-FIX: a binding a dispatching callback may assign is OBJECT. */
    private ExperimentalCompactKind experimentalCompactStoredKind(
            CanonicalBindingIdentity identity,
            ExperimentalCompactKind kind) {
        return currentExperimentalCompactPinned != null
                        && currentExperimentalCompactPinned.contains(identity)
                ? ExperimentalCompactKind.OBJECT
                : kind;
    }

    /**
     * I115-M/I115-P: a whileTrue send over two parameterless literal
     * Closures whose bodies are compact. The condition's last expression is
     * any admitted value not proven INTEGER; its whole body, prefix
     * included, is evaluated on every iteration. I115-H1-FIX: the condition result is validated at run
     * time by ExperimentalCanonicalBooleanCondition, as the standard
     * whileTrue behavior does: exact canonical true or false, otherwise the
     * standard Error.
     * I115-N: the loop-head state is the join of the entry state and every
     * body exit state, iterated to its fixpoint (families only widen to
     * OBJECT); the exit state is the condition's exit at that head.
     */
    private ExperimentalCompactKind admitExperimentalCompactWhile(
            CanonicalSend send,
            CanonicalLexicalScope scope,
            ExperimentalCompactKinds kinds) {
        if (!(send.receiver() instanceof CanonicalClosure condition)
                || send.arguments().size() != 1
                || !(send.arguments().get(0) instanceof CanonicalClosure body)
                || !condition.parameters().isEmpty()
                || !body.parameters().isEmpty()
                || !experimentalCompactCallbackScope(condition)
                || !experimentalCompactCallbackScope(body)) {
            return null;
        }
        java.util.List<CanonicalExpression> tests =
                condition.body().expressions();
        if (tests.isEmpty()) {
            return null;
        }
        ExperimentalCompactKinds head = new ExperimentalCompactKinds(kinds);
        while (true) {
            ExperimentalCompactKinds afterTest = new ExperimentalCompactKinds(head);
            ExperimentalCompactKind test = admitExperimentalCompactSequence(
                    condition.body(), scope, afterTest, true);
            // A proven Integer condition always fails; the general lowerer keeps it.
            if (test == null || test == ExperimentalCompactKind.INTEGER) {
                return null;
            }
            ExperimentalCompactKinds afterBody = new ExperimentalCompactKinds(afterTest);
            if (admitExperimentalCompactSequence(
                            body.body(), scope, afterBody, true)
                    == null) {
                return null;
            }
            ExperimentalCompactKinds next = new ExperimentalCompactKinds();
            next.joinFrom(head, afterBody);
            if (next.equals(head)) {
                kinds.clear();
                kinds.putAll(afterTest);
                return ExperimentalCompactKind.OBJECT;
            }
            head = next;
        }
    }

    /*
     * I115-N: the NormalBooleanControl row of a selector admitted by the
     * compact Boolean subset; not, and and or stay with the general lowerer.
     */
    private static NormalBooleanControl experimentalCompactBooleanControl(
            String selector) {
        NormalBooleanControl control = NormalBooleanControl.of(selector);
        return control == NormalBooleanControl.IF_TRUE
                        || control == NormalBooleanControl.IF_FALSE
                        || control == NormalBooleanControl.IF_TRUE_IF_FALSE
                ? control
                : null;
    }

    /**
     * I115-N EXPERIMENTAL: Boolean control over a BOOLEAN receiver whose
     * arguments are exactly the row's parameterless literal Closures with
     * compact bodies and no bindings of their own. Each callback is admitted
     * from its own copy of the receiver's exit state; the exit state joins
     * the selectable paths (the unselected path keeps the entry state).
     */
    private ExperimentalCompactKind admitExperimentalCompactBooleanControl(
            CanonicalSend send,
            CanonicalLexicalScope scope,
            ExperimentalCompactKinds kinds,
            boolean callback) {
        NormalBooleanControl control =
                experimentalCompactBooleanControl(send.message());
        java.util.List<CanonicalExpression> arguments = send.arguments();
        if (arguments.size() != control.arity) {
            return null;
        }
        for (CanonicalExpression argument : arguments) {
            if (!(argument instanceof CanonicalClosure closure)
                    || !closure.parameters().isEmpty()
                    || !experimentalCompactCallbackScope(closure)) {
                return null;
            }
        }
        ExperimentalCompactKind receiverKind = admitExperimentalCompactExpression(
                send.receiver(), scope, kinds, callback);
        if (receiverKind != ExperimentalCompactKind.BOOLEAN
                && receiverKind != ExperimentalCompactKind.COMPARISON) {
            return null;
        }
        /*
         * I115-H1-FIX: a comparison receiver is guarded at run time; when it
         * is not canonical, the control selector is sent ordinarily with the
         * callbacks as guest Closures, which the selected method may invoke
         * at any time.
         */
        boolean dispatching = receiverKind == ExperimentalCompactKind.COMPARISON;
        if (dispatching) {
            currentExperimentalCompactDispatchingControls.add(send);
            for (CanonicalExpression argument : arguments) {
                collectExperimentalCompactAssigned(
                        argument, scope, currentExperimentalCompactEscaped);
            }
        }
        ExperimentalCompactKinds[] exits =
                new ExperimentalCompactKinds[arguments.size()];
        ExperimentalCompactKind[] results =
                new ExperimentalCompactKind[arguments.size()];
        for (int index = 0; index < arguments.size(); index++) {
            exits[index] = new ExperimentalCompactKinds(kinds);
            results[index] = admitExperimentalCompactSequence(
                    ((CanonicalClosure) arguments.get(index)).body(),
                    scope,
                    exits[index],
                    true);
            if (results[index] == null) {
                return null;
            }
        }
        ExperimentalCompactKinds entry = new ExperimentalCompactKinds(kinds);
        kinds.joinFrom(exits[0], exits.length == 2 ? exits[1] : entry);
        if (dispatching) {
            // The ordinary send may select no callback.
            ExperimentalCompactKinds selected = new ExperimentalCompactKinds(kinds);
            kinds.joinFrom(selected, entry);
            return ExperimentalCompactKind.OBJECT;
        }
        return exits.length == 2
                ? experimentalCompactJoin(results[0], results[1])
                : ExperimentalCompactKind.OBJECT;
    }

    /*
     * I115-M: an inline callback has an analyzed scope and introduces no
     * binding of its own.
     */
    private boolean experimentalCompactCallbackScope(CanonicalClosure callback) {
        return currentRootAnalysis.scopeOf(callback)
                .map(callbackScope -> callbackScope.declaredNames().isEmpty())
                .orElse(false);
    }

    /*
     * I115-M: the identity of a reference resolved (directly or through any
     * depth of inline callbacks) to a binding owned by the physical scope.
     */
    private static CanonicalBindingIdentity experimentalCompactIdentity(
            CanonicalBindingResolution resolution,
            CanonicalLexicalScope scope,
            String name) {
        CanonicalBindingIdentity identity;
        if (resolution instanceof CanonicalBindingResolution.Resolved resolved) {
            identity = resolved.identity();
        } else if (resolution
                instanceof CanonicalBindingResolution.CapturedResolved captured) {
            identity = captured.identity();
        } else {
            return null;
        }
        return identity.owner() == scope && identity.name().equals(name)
                ? identity
                : null;
    }

    /**
     * I115-H7 EXPERIMENTAL: a compact read of a module binding. The lookup
     * must resolve, through any depth of inline callbacks, to a binding
     * captured from a ROOT scope outside the physical scope. The result is
     * the binding and its lexical depth counted from the physical root (the
     * depth ReadGlobal resolves from that root's frame), or null.
     */
    private record ExperimentalCompactGlobal(
            CanonicalBindingIdentity identity, int physicalDepth) {}

    private ExperimentalCompactGlobal experimentalCompactGlobal(
            CanonicalLookup lookup,
            CanonicalLexicalScope scope) {
        if (!(currentRootAnalysis.resolutionOf(lookup).orElse(null)
                instanceof CanonicalBindingResolution.CapturedResolved captured)) {
            return null;
        }
        CanonicalBindingIdentity identity = captured.identity();
        if (identity.owner().kind() != CanonicalLexicalScope.Kind.ROOT
                || identity.owner() == scope
                || !identity.name().equals(lookup.name())) {
            return null;
        }
        int depth = 0;
        for (CanonicalLexicalScope current = scope;
                current != null;
                current = current.outwardScope()) {
            if (current == identity.owner()) {
                return new ExperimentalCompactGlobal(identity, depth);
            }
            depth++;
        }
        return null;
    }

    /**
     * I115-H7 EXPERIMENTAL: an ordinary send of exactly one argument that
     * the compact subset lowers to GetMethod and CallBinaryMethod: any
     * selector except the numeric selectors, whileTrue and the standard
     * Boolean controls, which keep their own lowering. Independent of the
     * selector's spelling otherwise.
     */
    private static boolean experimentalCompactMethodSend(CanonicalSend send) {
        String selector = send.message();
        return send.arguments().size() == 1
                && !experimentalCompactArithmetic(selector)
                && !experimentalCompactComparison(selector)
                && !"whileTrue".equals(selector)
                && NormalBooleanControl.of(selector) == null;
    }

    private static boolean experimentalCompactArithmetic(String selector) {
        return "+".equals(selector)
                || "-".equals(selector)
                || "*".equals(selector);
    }

    private static boolean experimentalCompactComparison(String selector) {
        return "<".equals(selector)
                || "<=".equals(selector)
                || ">".equals(selector)
                || ">=".equals(selector);
    }

    /**
     * I115-M EXPERIMENTAL: emit an admitted root. Every prefix expression is
     * a discarded statement; the terminal value is returned directly and
     * the existing EpilogForReturn projects a numeric carrier. I115-O: the
     * Integer literal stores of a certified returned binding load prepared
     * guest constants instead, and the epilog projects nothing.
     */
    private void emitExperimentalCompactRoot(
            ProtosBytecodeRootNodeGen.Builder builder,
            CanonicalSequence sequence,
            ExperimentalCompactRoot compact) {
        CanonicalBindingIdentity savedGuestIntegerBinding =
                currentExperimentalGuestIntegerBinding;
        ComposedSequenceScratch savedScratch = currentExperimentalCompactScratch;
        boolean savedRecursiveEmission =
                currentExperimentalRecursiveIntegerEmission;
        java.util.Set<CanonicalSend> savedDispatchingControls =
                currentExperimentalDispatchingControls;
        CanonicalLexicalScope savedCompactScope = currentExperimentalCompactScope;
        currentExperimentalCompactScope = compact.scope();
        CanonicalExpression savedCompactTerminal = currentExperimentalCompactTerminal;
        currentExperimentalCompactTerminal =
                sequence.expressions().get(sequence.expressions().size() - 1);
        currentExperimentalGuestIntegerBinding = compact.guestIntegerReturn();
        currentExperimentalRecursiveIntegerEmission =
                compact.experimentalRecursiveInteger();
        currentExperimentalDispatchingControls = compact.dispatchingControls();
        // Created in the root block, so live across every loop and callback.
        currentExperimentalCompactScratch = compact.dispatches()
                ? new ComposedSequenceScratch(
                        builder.createLocal("preparedClosureCall", null),
                        builder.createLocal("childResult", null),
                        builder.createLocal("resumeValue", null))
                : null;
        try {
            java.util.List<CanonicalExpression> expressions =
                    sequence.expressions();
            for (int index = 0; index < expressions.size() - 1; index++) {
                emitExperimentalCompactStatement(builder, expressions.get(index));
            }
            CanonicalExpression terminal =
                    expressions.get(expressions.size() - 1);
            beginStatement(builder, terminal);
            builder.beginReturn();
            if (compact.guestIntegerLiteralReturn()) {
                // I115-Q: the returned guest Integer is prepared at lowering time.
                builder.emitLoadConstant(
                        guestLiteralConstant((CanonicalLiteral) terminal));
            } else {
                emitExperimentalCompactValue(builder, terminal);
            }
            builder.endReturn();
            endStatement(builder);
        } finally {
            currentExperimentalGuestIntegerBinding = savedGuestIntegerBinding;
            currentExperimentalRecursiveIntegerEmission =
                    savedRecursiveEmission;
            currentExperimentalCompactScratch = savedScratch;
            currentExperimentalDispatchingControls = savedDispatchingControls;
            currentExperimentalCompactScope = savedCompactScope;
            currentExperimentalCompactTerminal = savedCompactTerminal;
        }
    }

    private void emitExperimentalCompactSequence(
            ProtosBytecodeRootNodeGen.Builder builder,
            CanonicalSequence sequence) {
        for (CanonicalExpression expression : sequence.expressions()) {
            emitExperimentalCompactStatement(builder, expression);
        }
    }

    /* I115-M: an admitted expression whose value is not consumed. */
    private void emitExperimentalCompactStatement(
            ProtosBytecodeRootNodeGen.Builder builder,
            CanonicalExpression expression) {
        beginStatement(builder, expression);
        if (expression instanceof CanonicalCreate create) {
            emitExperimentalCompactStore(builder, create, create.name(), create.value());
        } else if (expression instanceof CanonicalAssign assign
                && assign.target().isPresent()) {
            // The explicit assignment's result is discarded by this statement.
            builder.beginDiscardValue();
            emitExperimentalCompactValue(builder, assign);
            builder.endDiscardValue();
        } else if (expression instanceof CanonicalAssign assign) {
            emitExperimentalCompactStore(builder, assign, assign.name(), assign.value());
        } else if (expression instanceof CanonicalSend send
                && ("whileTrue".equals(send.message())
                        || experimentalCompactBooleanControl(send.message()) != null)) {
            emitI115UnifiedControl(builder, send, null, null, null, null, null, false);
        } else {
            builder.beginDiscardValue();
            emitExperimentalCompactValue(builder, expression);
            builder.endDiscardValue();
        }
        endStatement(builder);
    }

    private void emitExperimentalCompactStore(
            ProtosBytecodeRootNodeGen.Builder builder,
            CanonicalExpression binding,
            String name,
            CanonicalExpression value) {
        /*
         * I115: a compact root has no cell bindings (see
         * admitExperimentalCompactRoot), so creation and assignment are the
         * same GraalPy store.local over the binding's own local.
         *
         * KNOWN I115 SEMANTIC DIVERGENCE: a duplicate creation is not
         * detected.
         */
        builder.beginStoreLocal(experimentalCompactLocal(name));
        if (currentExperimentalGuestIntegerBinding != null
                && value instanceof CanonicalLiteral literal
                && primitiveNumberLiteralOrNull(literal) instanceof Long
                && currentExperimentalGuestIntegerBinding.equals(
                        experimentalCompactStoreIdentity(
                                binding,
                                currentExperimentalGuestIntegerBinding.owner()))) {
            // I115-O: the guest Integer is prepared at lowering time.
            builder.emitLoadConstant(guestLiteralConstant(literal));
        } else if (experimentalCompactInPlaceAdd(binding, value)) {
            // I115-H7: x = x + e over the same binding (GraalPy PyNumberInPlaceAdd).
            CanonicalSend add = (CanonicalSend) value;
            SourceSpan span = add.span();
            builder.beginSourceSection(span.startOffset(), span.length());
            builder.beginNumberInPlaceAdd();
            emitExperimentalCompactValue(builder, add.receiver());
            emitExperimentalCompactValue(builder, add.arguments().get(0));
            builder.endNumberInPlaceAdd();
            builder.endSourceSection();
        } else {
            emitExperimentalCompactValue(builder, value);
        }
        builder.endStoreLocal();
    }

    /*
     * I115-H7: whether {@code value}, stored by {@code binding}, is a {@code
     * +} whose left operand reads the same binding identity.
     */
    private boolean experimentalCompactInPlaceAdd(
            CanonicalExpression binding,
            CanonicalExpression value) {
        if (currentExperimentalRecursiveIntegerEmission
                || currentExperimentalCompactScope == null
                || !(value instanceof CanonicalSend send)
                || !"+".equals(send.message())
                || send.arguments().size() != 1
                || !(send.receiver() instanceof CanonicalLookup left)) {
            return false;
        }
        CanonicalBindingIdentity stored =
                experimentalCompactStoreIdentity(binding, currentExperimentalCompactScope);
        return stored != null
                && stored.equals(experimentalCompactIdentity(
                        currentRootAnalysis.resolutionOf(left).orElse(null),
                        currentExperimentalCompactScope,
                        left.name()));
    }

    private BytecodeLocal experimentalCompactLocal(String name) {
        BytecodeLocal local = currentRootFrameLocals.get(name);
        if (local == null) {
            throw new AssertionError(
                    "I115-M compact root local is missing: " + name);
        }
        return local;
    }

    /* I115-M: an admitted expression whose value is consumed. */
    private void emitExperimentalCompactValue(
            ProtosBytecodeRootNodeGen.Builder builder,
            CanonicalExpression expression) {
        if (expression instanceof CanonicalLiteral literal) {
            builder.emitLoadConstant(switch (literal.kind()) {
                case TRUE -> ProtosBooleanValue.TRUE;
                case FALSE -> ProtosBooleanValue.FALSE;
                case NULL -> ProtosNullValue.INSTANCE;
                default -> experimentalCompactNumber(literal);
            });
        } else if (expression instanceof CanonicalLookup lookup) {
            ExperimentalCompactGlobal global =
                    currentExperimentalRecursiveIntegerEmission
                                    || currentExperimentalCompactScope == null
                            ? null
                            : experimentalCompactGlobal(lookup, currentExperimentalCompactScope);
            if (global != null) {
                // I115-H7: the module binding, read in place at this position.
                builder.emitReadGlobal(
                        TruffleString.fromJavaStringUncached(
                                global.identity().name(),
                                TruffleString.Encoding.UTF_16),
                        global.physicalDepth());
            } else {
                builder.emitLoadLocal(experimentalCompactLocal(lookup.name()));
            }
        } else if (expression instanceof CanonicalMember member) {
            // I115: GraalPy-shaped typed GetAttribute in compact roots.
            builder.beginExperimentalGetAttribute(member.name());
            emitExperimentalCompactValue(builder, member.receiver());
            builder.endExperimentalGetAttribute();
        } else if (expression instanceof CanonicalAssign assign
                && assign.target().isPresent()) {
            // I115-H8: GraalPy-shaped SetAttribute.
            // Receiver first, RHS second, one physical store operation.
            builder.beginExperimentalSetAttribute(assign.name());
            emitExperimentalCompactValue(
                    builder, assign.target().orElseThrow());
            if (assign.value() instanceof CanonicalLiteral literal
                    && literal.kind() == CanonicalLiteral.Kind.NUMBER
                    && primitiveNumberLiteralOrNull(literal) instanceof Long integer) {
                // The attribute lane is signed-64, like every compact literal.
                builder.emitLoadConstant(integer);
            } else {
                emitExperimentalCompactValue(builder, assign.value());
            }
            builder.endExperimentalSetAttribute();
        } else if (expression instanceof CanonicalCreate
                || expression instanceof CanonicalAssign) {
            // The stored value is the result; the RHS is not reevaluated.
            String name = expression instanceof CanonicalCreate create
                    ? create.name()
                    : ((CanonicalAssign) expression).name();
            CanonicalExpression value = expression instanceof CanonicalCreate create
                    ? create.value()
                    : ((CanonicalAssign) expression).value();
            builder.beginBlock();
            emitExperimentalCompactStore(builder, expression, name, value);
            builder.emitLoadLocal(experimentalCompactLocal(name));
            builder.endBlock();

        } else {
            CanonicalSend send = (CanonicalSend) expression;
            if ("whileTrue".equals(send.message())) {
                builder.beginBlock();
                BytecodeLocal result =
                        builder.createLocal("compactWhileResult", null);
                emitI115UnifiedControl(builder, send, result, null, null, null, null, false);
                builder.emitLoadLocal(result);
                builder.endBlock();
            } else if (experimentalCompactBooleanControl(send.message()) != null) {
                builder.beginBlock();
                BytecodeLocal result =
                        builder.createLocal("compactBooleanResult", null);
                emitI115UnifiedControl(builder, send, result, null, null, null, null, false);
                builder.emitLoadLocal(result);
                builder.endBlock();
            } else if (!currentExperimentalRecursiveIntegerEmission
                    && experimentalCompactMethodSend(send)) {
                emitExperimentalCompactMethodSend(builder, send);
            } else if (!currentExperimentalRecursiveIntegerEmission
                    && experimentalCompactArithmetic(send.message())) {
                // I115-LONG-4: GraalPy-shaped arithmetic; fallback stays inside the node.
                SourceSpan span = send.span();
                builder.beginSourceSection(span.startOffset(), span.length());
                switch (send.message()) {
                    case "+" -> builder.beginNumberAdd();
                    case "-" -> builder.beginNumberSubtract();
                    case "*" -> builder.beginNumberMultiply();
                    default -> throw new AssertionError("unknown direct arithmetic operation");
                }
                emitExperimentalCompactValue(builder, send.receiver());
                emitExperimentalCompactValue(builder, send.arguments().get(0));
                switch (send.message()) {
                    case "+" -> builder.endNumberAdd();
                    case "-" -> builder.endNumberSubtract();
                    case "*" -> builder.endNumberMultiply();
                    default -> throw new AssertionError("unknown direct arithmetic operation");
                }
                builder.endSourceSection();
            } else {
                if (currentExperimentalRecursiveIntegerEmission) {
                    emitExperimentalCompactSend(builder, send, false);
                } else {
                    builder.beginBlock();
                    BytecodeLocal result =
                            builder.createLocal("compactSendResult", null);
                    emitExperimentalCompactNumericSend(builder, send, result);
                    builder.emitLoadLocal(result);
                    builder.endBlock();
                }
            }
        }
    }

    /*
     * I115-H7: every signed-64 Integer literal is a long carrier, even when
     * it fits in 32 bits; there is no 32-bit guest integer level.
     */
    private Object experimentalCompactNumber(CanonicalLiteral literal) {
        return primitiveNumberLiteralOrNull(literal);
    }

    /**
     * I115-H7 EXPERIMENTAL (GraalPy emitCall for an attribute call with one
     * explicit argument): the receiver is evaluated once into GetMethod,
     * which selects with the constant selector; the argument is evaluated
     * once; CallBinaryMethod performs the single call and its result is
     * consumed directly. Selection failure, non-Closure values and any other
     * case are handled inside the two operations, so no DSL branch, prepared
     * call or authority promotion is emitted. The original send keeps one
     * CallTag region.
     */
    private void emitExperimentalCompactMethodSend(
            ProtosBytecodeRootNodeGen.Builder builder,
            CanonicalSend send) {
        /*
         * I115-H7-R1: the root's terminal send may return a suspended
         * outcome as the terminal transport; like every terminal invocation
         * it withdraws the root's non-suspension proof.
         */
        boolean terminal = send == currentExperimentalCompactTerminal;
        if (terminal) {
            currentRootEmittedTerminalInvocation = true;
        }
        SourceSpan span = send.span();
        builder.beginSourceSection(span.startOffset(), span.length());
        builder.beginTag(StandardTags.CallTag.class);
        /*
         * I115-H7-R3 (GraalPy RootNodeCompiler.emitCall / emitGetMethod
         * stack-value layout): the receiver is evaluated once and bound as a
         * stack value of the enclosing Block; GetMethod and CallBinaryMethod
         * each reload it, so CallBinaryMethod consumes (callable, receiver,
         * argument) and the argument is evaluated once, after the selection.
         */
        TruffleString selector = TruffleString.fromJavaStringUncached(
                send.message(), TruffleString.Encoding.UTF_16);
        builder.beginBlock();
        builder.beginBindStackValue();
        emitExperimentalCompactValue(builder, send.receiver());
        StackValue receiver = builder.endBindStackValue();
        // I115: GetMethod binds the receiver; the call has one argument.
        builder.beginCallUnaryMethod(terminal);
        builder.beginGetMethod(selector);
        builder.emitLoadStackValue(receiver);
        builder.endGetMethod();
        emitExperimentalCompactValue(builder, send.arguments().get(0));
        builder.endCallUnaryMethod();
        builder.endBlock();
        builder.endTag(StandardTags.CallTag.class);
        builder.endSourceSection();
    }

    /**
     * I115-M: one Integer operation over two operands emitted exactly once,
     * in evaluation order. {@code condition} selects the Java-boolean form
     * of a comparison consumed directly by a Bytecode branch.
     */
    /**
     * I115: recursive compact roots use exactly the same Number and
     * RichCompare DSL operations as ordinary roots. Each operand is evaluated
     * once. The generic operation may return a continuation; it is composed
     * in the current root before a consumer observes the resulting value.
     * A branch condition converts only the *completed* canonical Boolean.
     */
    private void emitExperimentalCompactSend(
            ProtosBytecodeRootNodeGen.Builder builder,
            CanonicalSend send,
            boolean condition) {
        String selector = send.message();
        if (condition) {
            builder.beginExperimentalCanonicalBooleanCondition();
        }
        builder.beginBlock();
        BytecodeLocal receiver = builder.createLocal("recursiveNumericReceiver", null);
        BytecodeLocal argument = builder.createLocal("recursiveNumericArgument", null);
        BytecodeLocal result = builder.createLocal("recursiveNumericResult", null);
        BytecodeLocal resumed = builder.createLocal("recursiveNumericResumed", null);
        builder.beginStoreLocal(receiver);
        emitExperimentalCompactValue(builder, send.receiver());
        builder.endStoreLocal();
        builder.beginStoreLocal(argument);
        emitExperimentalCompactValue(builder, send.arguments().get(0));
        builder.endStoreLocal();
        builder.beginStoreLocal(result);
        switch (selector) {
            case "+" -> builder.beginNumberAdd();
            case "-" -> builder.beginNumberSubtract();
            case "*" -> builder.beginNumberMultiply();
            case "<" -> builder.beginRichCompareLt();
            case "<=" -> builder.beginRichCompareLe();
            case ">" -> builder.beginRichCompareGt();
            case ">=" -> builder.beginRichCompareGe();
            default -> throw new AssertionError("I115 unadmitted compact selector: " + selector);
        }
        builder.emitLoadLocal(receiver);
        builder.emitLoadLocal(argument);
        switch (selector) {
            case "+" -> builder.endNumberAdd();
            case "-" -> builder.endNumberSubtract();
            case "*" -> builder.endNumberMultiply();
            case "<" -> builder.endRichCompareLt();
            case "<=" -> builder.endRichCompareLe();
            case ">" -> builder.endRichCompareGt();
            case ">=" -> builder.endRichCompareGe();
            default -> throw new AssertionError("I115 unadmitted compact selector: " + selector);
        }
        builder.endStoreLocal();
        emitDispatchedContinuationComposition(builder, result, resumed);
        builder.emitLoadLocal(result);
        builder.endBlock();
        if (condition) {
            builder.endExperimentalCanonicalBooleanCondition();
        }
    }

    /**
     * I115-H1-FIX: a compact numeric send is the shared numeric send site.
     * The receiver and then the argument are each evaluated exactly once
     * into staged locals; the selector's TryPrimitiveNumeric operation
     * (guarded on the actual operands and the standard numeric protocol)
     * either produces the result, or MISSes into the ordinary
     * GetMethod and the dispatched call, with its continuation,
     * ReturnHome and Error paths, over the materialized operands; before
     * that send the root's frame locals become its activation's lexical
     * authority, so the selected method observes the same bindings. Both
     * branches store into {@code result}. Compact values may hold primitive
     * carriers in every operand position, and a compact consumer accepts a
     * carrier result (the return epilog projects it).
     */
    private void emitExperimentalCompactNumericSend(
            ProtosBytecodeRootNodeGen.Builder builder,
            CanonicalSend send,
            BytecodeLocal result) {
        ComposedSequenceScratch scratch = currentExperimentalCompactScratch;
        if (scratch == null) {
            throw new AssertionError(
                    "I115-M compact numeric send without send scratch");
        }
        SourceSpan span = send.span();
        builder.beginSourceSection(span.startOffset(), span.length());
        builder.beginTag(StandardTags.CallTag.class);
        builder.beginBlock();
        BytecodeLocal receiverValue = builder.createLocal("sendReceiver", null);
        builder.beginStoreLocal(receiverValue);
        emitExperimentalCompactValue(builder, send.receiver());
        builder.endStoreLocal();
        BytecodeLocal argumentValue = builder.createLocal("sendArgument", null);
        builder.beginStoreLocal(argumentValue);
        emitExperimentalCompactValue(builder, send.arguments().get(0));
        builder.endStoreLocal();

        if ("/".equals(send.message()) || "<".equals(send.message())
                || "<=".equals(send.message()) || ">".equals(send.message())
                || ">=".equals(send.message())) {
            // The same DSL operation used by ordinary roots. There is no
            // numeric MISS or separate GetMethod dispatch on this path.
            builder.beginStoreLocal(scratch.childResult());
            switch (send.message()) {
                case "/" -> builder.beginNumberDivide();
                case "<" -> builder.beginRichCompareLt();
                case "<=" -> builder.beginRichCompareLe();
                case ">" -> builder.beginRichCompareGt();
                case ">=" -> builder.beginRichCompareGe();
                default -> throw new AssertionError("not a unified numeric selector");
            }
            builder.emitLoadLocal(receiverValue);
            builder.emitLoadLocal(argumentValue);
            switch (send.message()) {
                case "/" -> builder.endNumberDivide();
                case "<" -> builder.endRichCompareLt();
                case "<=" -> builder.endRichCompareLe();
                case ">" -> builder.endRichCompareGt();
                case ">=" -> builder.endRichCompareGe();
                default -> throw new AssertionError("not a unified numeric selector");
            }
            builder.endStoreLocal();
            emitDispatchedContinuationComposition(
                    builder, scratch.childResult(), scratch.resumeValue());
            builder.beginStoreLocal(result);
            builder.emitLoadLocal(scratch.childResult());
            builder.endStoreLocal();
        } else {
            // Temporary compatibility for remaining compact arithmetic
            // selectors. Removal requires retiring their old operations.
            BytecodeLocal attempt = beginPrimitiveNumericMiss(
                    builder, send, receiverValue, argumentValue, true, true);
            emitDispatchedSendOfLocals(
                    builder, send.message(), receiverValue, java.util.List.of(argumentValue),
                    result, scratch.childResult(), scratch.resumeValue());
            endPrimitiveNumericMiss(builder, attempt, result, true);
        }

        builder.endBlock();
        builder.endTag(StandardTags.CallTag.class);
        builder.endSourceSection();
    }

    /**
     * I115-M EXPERIMENTAL: inline literal whileTrue as a Bytecode While. The
     * loop itself has no callback root, prepared call, continuation or
     * protocol fallback; numeric sends in its bodies keep their own.
     * {@code result} is null when the loop value is discarded; otherwise it
     * starts as null and holds the last completed body result.
     */
    /*
     * I115-P: an admitted Boolean condition produces a Java primitive
     * boolean. Preserve preceding expressions and reuse the I115-N
     * Boolean conversion for the final expression.
     */
    private void emitExperimentalCompactCondition(
            ProtosBytecodeRootNodeGen.Builder builder,
            CanonicalSequence sequence) {
        java.util.List<CanonicalExpression> tests = sequence.expressions();
        CanonicalExpression condition = tests.get(tests.size() - 1);
        if (tests.size() == 1) {
            emitExperimentalCompactWhileTest(builder, condition);
            return;
        }
        builder.beginBlock();
        for (int index = 0; index < tests.size() - 1; index++) {
            emitExperimentalCompactStatement(builder, tests.get(index));
        }
        emitExperimentalCompactWhileTest(builder, condition);
        builder.endBlock();
    }

    /*
     * I115-H7: a whileTrue condition ending in {@code <} is one Lt whose
     * Java boolean the While consumes directly (no Boolean value, MISS
     * classification or branch); every other condition keeps the I115-N/P
     * conversion.
     */
    private void emitExperimentalCompactWhileTest(
            ProtosBytecodeRootNodeGen.Builder builder,
            CanonicalExpression condition) {
        if (!currentExperimentalRecursiveIntegerEmission
                && condition instanceof CanonicalSend less
                && "<".equals(less.message())
                && less.arguments().size() == 1) {
            SourceSpan span = less.span();
            builder.beginSourceSection(span.startOffset(), span.length());
            builder.beginLt();
            emitExperimentalCompactValue(builder, less.receiver());
            emitExperimentalCompactValue(builder, less.arguments().get(0));
            builder.endLt();
            builder.endSourceSection();
            return;
        }
        emitExperimentalCompactBooleanCondition(builder, condition);
    }

    /**
     * I115-N EXPERIMENTAL: admitted Boolean control as a structured Bytecode
     * IfThen/IfThenElse over the receiver's Java boolean. Each branch applies
     * the NormalBooleanControl row for that receiver: the selected literal
     * callback body runs inline (no Closure, root, message or prepared call)
     * and yields its exact result, or the receiver after discarding it; an
     * unselected receiver yields the row's unselected result. {@code result}
     * is null when the control value is discarded, so no local is used.
     * I115-H1-FIX: a dispatching site (receiver not proven canonical
     * Boolean) is guarded at run time; see
     * emitExperimentalCompactBooleanDispatch.
     */
    /*
     * I115-N: the receiver evaluated once as a Java boolean, converted by one
     * ExperimentalCanonicalBooleanCondition only after the value is known to
     * be exact canonical true or false. I115-H1-FIX: a comparison is the
     * shared numeric send site, so its HIT canonical Boolean takes the
     * condition's Boolean specialization and a MISS result is validated the
     * same way (a whileTrue condition signals the standard Error otherwise).
     * I115-Q: a true/false literal is loaded directly as a Java boolean.
     */
    private void emitExperimentalCompactBooleanCondition(
            ProtosBytecodeRootNodeGen.Builder builder,
            CanonicalExpression receiver) {
        if (receiver instanceof CanonicalLiteral literal
                && (literal.kind() == CanonicalLiteral.Kind.TRUE
                        || literal.kind() == CanonicalLiteral.Kind.FALSE)) {
            builder.emitLoadConstant(literal.kind() == CanonicalLiteral.Kind.TRUE);
            return;
        }
        if (currentExperimentalRecursiveIntegerEmission
                && receiver instanceof CanonicalSend comparison
                && experimentalCompactComparison(comparison.message())) {
            emitExperimentalCompactSend(builder, comparison, true);
            return;
        }
        builder.beginExperimentalCanonicalBooleanCondition();
        emitExperimentalCompactValue(builder, receiver);
        builder.endExperimentalCanonicalBooleanCondition();
    }

    /*
     * I115-N: run a callback body inline, storing its final expression's
     * value (evaluated once) in {@code result}; an empty body yields null.
     */
    private void emitExperimentalCompactSequenceValue(
            ProtosBytecodeRootNodeGen.Builder builder,
            CanonicalSequence sequence,
            BytecodeLocal result) {
        java.util.List<CanonicalExpression> statements = sequence.expressions();
        if (statements.isEmpty()) {
            builder.beginStoreLocal(result);
            builder.emitLoadConstant(ProtosNullValue.INSTANCE);
            builder.endStoreLocal();
            return;
        }
        for (int index = 0; index < statements.size() - 1; index++) {
            emitExperimentalCompactStatement(builder, statements.get(index));
        }
        CanonicalExpression last = statements.get(statements.size() - 1);
        beginStatement(builder, last);
        builder.beginStoreLocal(result);
        emitExperimentalCompactValue(builder, last);
        builder.endStoreLocal();
        endStatement(builder);
    }

    /**
     * I115-L EXPERIMENTAL: return the result of the module's
     * single Closure binding creation directly. The Closure root
     * is registered before opening the consuming Bytecode operation.
     */
    private void emitExperimentalModuleClosureReturn(
            ProtosBytecodeRootNodeGen.Builder builder,
            CanonicalCreate create) {
        CanonicalClosure closure =
                (CanonicalClosure) create.value();

        ProtosClosureExecutionPlanCell plan =
                bytecodeClosurePlan(builder, closure);

        // I115: a module binding is a NAME binding of the module context.
        beginStatement(builder, create);
        builder.beginReturn();
        builder.beginCreateCurrentLocalSlot();
        emitCurrentActivation(builder);
        builder.emitLoadConstant(create.name());
        emitClosureCapture(builder, closure, plan);
        builder.endCreateCurrentLocalSlot();
        builder.endReturn();
        endStatement(builder);
    }

    /**
     * I115-LONG-5 EXPERIMENTAL: the general source lowering uses the same
     * GraalPy-shaped arithmetic operations as the H7 compact lowering.
     * No numeric carrier certificates and no MISS branch for +, - or *.
     * Each operand is evaluated exactly once, from left to right. The
     * operation owns its generic fallback, as in PyNumber{Add,Subtract,Multiply}.
     */
    /**
     * I115 GraalPy-shaped ordinary comparison and division. The operator
     * itself chooses the primitive or generic implementation. A generic
     * result can suspend and is composed exactly like every other call.
     */
    private void emitI115UnifiedNumericSend(
            ProtosBytecodeRootNodeGen.Builder builder,
            CanonicalSend send,
            BytecodeLocal result,
            BytecodeLocal scratch,
            BytecodeLocal childResult,
            BytecodeLocal resumeValue,
            boolean primitiveCarrierResult) {
        builder.beginTag(StandardTags.CallTag.class);
        builder.beginBlock();
        BytecodeLocal receiver = builder.createLocal("numericReceiver", null);
        BytecodeLocal argument = builder.createLocal("numericArgument", null);
        emitPrimitiveNumericOperandToLocal(
                builder, send.receiver(), receiver, scratch, childResult, resumeValue);
        emitPrimitiveNumericOperandToLocal(
                builder, send.arguments().get(0), argument, scratch, childResult, resumeValue);
        builder.beginStoreLocal(childResult);
        switch (send.message()) {
            case "/" -> builder.beginNumberDivide();
            case "<" -> builder.beginRichCompareLt();
            case "<=" -> builder.beginRichCompareLe();
            case ">" -> builder.beginRichCompareGt();
            case ">=" -> builder.beginRichCompareGe();
            default -> throw new AssertionError("not a unified numeric selector");
        }
        builder.emitLoadLocal(receiver);
        builder.emitLoadLocal(argument);
        switch (send.message()) {
            case "/" -> builder.endNumberDivide();
            case "<" -> builder.endRichCompareLt();
            case "<=" -> builder.endRichCompareLe();
            case ">" -> builder.endRichCompareGt();
            case ">=" -> builder.endRichCompareGe();
            default -> throw new AssertionError("not a unified numeric selector");
        }
        builder.endStoreLocal();
        emitDispatchedContinuationComposition(builder, childResult, resumeValue);
        builder.beginStoreLocal(result);
        if (primitiveCarrierResult) {
            builder.emitLoadLocal(childResult);
        } else {
            builder.beginMaterializePrimitiveNumericCarrier();
            builder.emitLoadLocal(childResult);
            builder.emitLoadConstant(true);
            builder.endMaterializePrimitiveNumericCarrier();
        }
        builder.endStoreLocal();
        builder.endBlock();
        builder.endTag(StandardTags.CallTag.class);
    }

    private void emitGraalPyGeneralArithmeticSend(
            ProtosBytecodeRootNodeGen.Builder builder,
            CanonicalSend send,
            BytecodeLocal result,
            BytecodeLocal scratch,
            BytecodeLocal childResult,
            BytecodeLocal resumeValue) {
        builder.beginTag(StandardTags.CallTag.class);
        builder.beginBlock();
        BytecodeLocal receiverValue = builder.createLocal("numericReceiver", null);
        BytecodeLocal argumentValue = builder.createLocal("numericArgument", null);
        emitPrimitiveNumericOperandToLocal(
                builder, send.receiver(), receiverValue, scratch, childResult, resumeValue);
        emitPrimitiveNumericOperandToLocal(
                builder, send.arguments().get(0), argumentValue, scratch, childResult, resumeValue);
        builder.beginStoreLocal(result);
        switch (send.message()) {
            case "+" -> builder.beginNumberAdd();
            case "-" -> builder.beginNumberSubtract();
            case "*" -> builder.beginNumberMultiply();
            default -> throw new AssertionError("not a direct arithmetic selector");
        }
        builder.emitLoadLocal(receiverValue);
        builder.emitLoadLocal(argumentValue);
        switch (send.message()) {
            case "+" -> builder.endNumberAdd();
            case "-" -> builder.endNumberSubtract();
            case "*" -> builder.endNumberMultiply();
            default -> throw new AssertionError("not a direct arithmetic selector");
        }
        builder.endStoreLocal();
        builder.endBlock();
        builder.endTag(StandardTags.CallTag.class);
    }

    /**
     * PERF040-E1 numeric operator sends keep their primitive attempt over the
     * staged operands; its MISS branch (and every other send) is the
     * dispatched GraalPy send: GetMethod on the staged receiver, then the
     * arity-selected Call*Method.
     */
    private void emitComposedSendUnfolded(
            ProtosBytecodeRootNodeGen.Builder builder,
            CanonicalSend send,
            BytecodeLocal result,
            BytecodeLocal scratch,
            BytecodeLocal childResult,
            BytecodeLocal resumeValue,
            boolean primitiveCarrierResult) {
        if (send.arguments().size() == 1
                && !hasSpreadArgument(send.arguments())
                && ("+".equals(send.message())
                        || "-".equals(send.message())
                        || "*".equals(send.message()))) {
            emitGraalPyGeneralArithmeticSend(
                    builder, send, result, scratch, childResult, resumeValue);
            return;
        }
        if (send.arguments().size() == 1
                && !hasSpreadArgument(send.arguments())
                && ("/".equals(send.message())
                        || "<".equals(send.message())
                        || "<=".equals(send.message())
                        || ">".equals(send.message())
                        || ">=".equals(send.message()))) {
            emitI115UnifiedNumericSend(
                    builder, send, result, scratch, childResult, resumeValue,
                    primitiveCarrierResult);
            return;
        }
        if (!primitiveNumericSendCandidate(send)) {
            emitDispatchedInvocation(
                    builder, InvocationShape.SEND, send.receiver(), send.message(), send.arguments(),
                    result, scratch, childResult, resumeValue,
                    this::emitBodyExpressionToLocal, this::emitBodySpreadArgumentVector,
                    primitiveCarrierResult);
            return;
        }
        builder.beginTag(StandardTags.CallTag.class);
        builder.beginBlock();
        BytecodeLocal receiverValue = builder.createLocal("numericReceiver", null);
        BytecodeLocal argumentValue = builder.createLocal("numericArgument", null);
        emitPrimitiveNumericOperandToLocal(
                builder, send.receiver(), receiverValue, scratch, childResult, resumeValue);
        emitPrimitiveNumericOperandToLocal(
                builder, send.arguments().get(0), argumentValue, scratch, childResult, resumeValue);
        BytecodeLocal attempt =
                beginPrimitiveNumericMiss(builder, send, receiverValue, argumentValue, true, true);
        BytecodeLocal callable = builder.createLocal("numericMethod", null);
        emitGetMethod(builder, send.message(), receiverValue, callable);
        emitDispatchedCall(
                builder, callable, java.util.List.of(argumentValue), null, childResult, result,
                resumeValue, primitiveCarrierResult);
        endPrimitiveNumericMiss(builder, attempt, result, primitiveCarrierResult);
        builder.endBlock();
        builder.endTag(StandardTags.CallTag.class);
    }


    private static boolean containsClosure(CanonicalExpression expression) {
        return switch (expression) {
            case CanonicalClosure closure -> true;
            case CanonicalSequence sequence ->
                    sequence.expressions().stream()
                            .anyMatch(CanonicalToBytecodeLowerer::containsClosure);
            case CanonicalAssign assign ->
                    assign.target().map(CanonicalToBytecodeLowerer::containsClosure).orElse(false)
                            || containsClosure(assign.value());
            case CanonicalCreate create ->
                    create.target().map(CanonicalToBytecodeLowerer::containsClosure).orElse(false)
                            || containsClosure(create.value());
            case CanonicalMultipleCreate create -> containsClosure(create.value());
            case CanonicalCall call ->
                    containsClosure(call.receiver()) || anyContainsClosure(call.arguments());
            case CanonicalSend send ->
                    containsClosure(send.receiver()) || anyContainsClosure(send.arguments());
            case CanonicalSuperSend send -> anyContainsClosure(send.arguments());
            case CanonicalCompose compose -> containsClosure(compose.object());
            case CanonicalDerivedInequality inequality ->
                    containsClosure(inequality.left()) || containsClosure(inequality.right());
            case CanonicalIdentity identity ->
                    containsClosure(identity.left()) || containsClosure(identity.right());
            case CanonicalNotIdentity identity ->
                    containsClosure(identity.left()) || containsClosure(identity.right());
            case CanonicalIndexedAssign assign ->
                    containsClosure(assign.receiver())
                            || containsClosure(assign.index())
                            || containsClosure(assign.value());
            case CanonicalMapConstruction map ->
                    containsClosure(map.factory())
                            || map.entries().stream()
                                    .anyMatch(
                                            entry ->
                                                    containsClosure(entry.key())
                                                            || containsClosure(entry.value()));
            case CanonicalMember member -> containsClosure(member.receiver());
            case CanonicalObject object ->
                    object.parent().map(CanonicalToBytecodeLowerer::containsClosure).orElse(false)
                            || containsClosure(object.body());
            case CanonicalReturn returnExpression -> containsClosure(returnExpression.value());
            case CanonicalSpread spread -> containsClosure(spread.expression());
            case CanonicalNamedArgument named ->
                    containsClosure(named.expression());
            case CanonicalIntrinsic intrinsic -> false;
            case CanonicalLiteral literal -> false;
            case CanonicalLookup lookup -> false;
        };
    }

    private static boolean anyContainsClosure(
            java.util.List<CanonicalExpression> expressions) {
        return expressions.stream().anyMatch(CanonicalToBytecodeLowerer::containsClosure);
    }

    private void emitComposedCall(
            ProtosBytecodeRootNodeGen.Builder builder,
            CanonicalCall call,
            BytecodeLocal result,
            BytecodeLocal scratch,
            BytecodeLocal childResult,
            BytecodeLocal resumeValue) {
        emitComposedCall(builder, call, result, scratch, childResult, resumeValue, false);
    }

    private void emitComposedCall(
            ProtosBytecodeRootNodeGen.Builder builder,
            CanonicalCall call,
            BytecodeLocal result,
            BytecodeLocal scratch,
            BytecodeLocal childResult,
            BytecodeLocal resumeValue,
            boolean primitiveCarrierResult) {
        emitDispatchedInvocation(
                builder, InvocationShape.CALL, call.receiver(), null, call.arguments(),
                result, scratch, childResult, resumeValue,
                this::emitBodyExpressionToLocal, this::emitBodySpreadArgumentVector,
                primitiveCarrierResult);
    }


    /*
     * I091: a literal is a guest constant unless it is an Integer outside the signed-64 range,
     * which is an ordinary guest object minted with the executing Prelude on each evaluation.
     * The constant of a signed-64 Integer or Float literal is immutable, carries no prototype,
     * and compares by value, so one constant serves every execution domain.
     */
    private void emitLiteral(
            ProtosBytecodeRootNodeGen.Builder builder, CanonicalLiteral literal) {
        if (literal.kind() == CanonicalLiteral.Kind.NUMBER
                && numberLiteralDescriptor(literal) instanceof BigInteger large) {
            builder.beginLargeIntegerLiteral();
            emitSendCallerOperand(builder);
            builder.emitLoadConstant(large);
            builder.endLargeIntegerLiteral();
            return;
        }
        builder.emitLoadConstant(guestLiteralConstant(literal));
    }

    /*
     * I091: the Long or Double descriptor of a number literal that may be staged as a primitive
     * carrier, or null for every other literal (including a large Integer literal).
     */
    private Object primitiveNumberLiteralOrNull(CanonicalLiteral literal) {
        if (literal.kind() != CanonicalLiteral.Kind.NUMBER) {
            return null;
        }
        Object descriptor = numberLiteralDescriptor(literal);
        return descriptor instanceof Long || descriptor instanceof Double ? descriptor : null;
    }

    private Object numberLiteralDescriptor(CanonicalLiteral literal) {
        return numberLiteralDescriptors.computeIfAbsent(
                literal, number -> ProtosNumberLiteral.parse(number.value()));
    }

    /* The guest constant of a literal other than a large Integer literal. */
    private Object guestLiteralConstant(CanonicalLiteral literal) {
        return switch (literal.kind()) {
            case NUMBER -> numberLiteralDescriptor(literal) instanceof Long small
                    ? ProtosNumericValueSupport.integer(small)
                    : ProtosNumericValueSupport.floating((Double) numberLiteralDescriptor(literal));
            case STRING -> new ProtosStringValue(literal.value());
            case TRUE -> ProtosBooleanValue.TRUE;
            case FALSE -> ProtosBooleanValue.FALSE;
            case NULL -> ProtosNullValue.INSTANCE;
        };
    }
}
