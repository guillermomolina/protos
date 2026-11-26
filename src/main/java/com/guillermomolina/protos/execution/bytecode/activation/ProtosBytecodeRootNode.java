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

import com.guillermomolina.protos.execution.ProtosStructuredControlCalls.PreparedCaseOfCall;
import com.guillermomolina.protos.execution.ProtosStructuredControlCalls.PreparedEnsureCall;
import com.guillermomolina.protos.execution.ProtosStructuredControlCalls.PreparedErrorHandlerCall;
import com.guillermomolina.protos.execution.ProtosStructuredControlCalls.PreparedStandardImportCall;
import com.guillermomolina.protos.execution.ProtosStructuredControlCalls.PreparedStandardObjectCall;
import com.guillermomolina.protos.execution.ProtosStructuredEachCalls.PreparedArrayMatchCall;
import com.guillermomolina.protos.execution.ProtosStructuredEachCalls.PreparedIdentityMapAtIfAbsentCall;
import com.guillermomolina.protos.execution.ProtosStructuredMapQueryCalls.PreparedMapMatchCall;
import com.guillermomolina.protos.execution.ProtosStructuredMapQueryCalls.PreparedMapReadLookupCall;
import com.guillermomolina.protos.execution.ProtosStructuredMapUpdateCalls.PreparedMapAtPutCall;
import com.guillermomolina.protos.execution.ProtosStructuredMapUpdateCalls.PreparedMapRemoveCall;
import com.guillermomolina.protos.runtime.ProtosCoreErrors;
import com.guillermomolina.protos.runtime.ProtosIdentity;
import com.guillermomolina.protos.runtime.control.ProtosNonLocalReturnException;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.guillermomolina.protos.runtime.ProtosIoOperation;
import com.guillermomolina.protos.runtime.ProtosTask;
import java.util.List;
import com.guillermomolina.protos.execution.ProtosPreparedCalls.PreparedArgumentVector;
import com.guillermomolina.protos.execution.ProtosPreparedCalls.StructuredNativeInvocation;
import com.guillermomolina.protos.execution.ProtosStructuredControlCalls.PreparedBooleanCall;
import com.guillermomolina.protos.execution.ProtosStructuredControlCalls.PreparedLoopCall;
import com.guillermomolina.protos.execution.ProtosStructuredEachCalls.PreparedIndexedEachCall;
import com.guillermomolina.protos.execution.ProtosStructuredEachCalls.PreparedLocalEachCall;
import com.guillermomolina.protos.execution.ProtosStructuredMapUpdateCalls.PreparedMapInitialDefinition;
import com.guillermomolina.protos.execution.ProtosFrameLocalBindings.ResolvedLexicalWriteTarget;
import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosArrayValue;
import com.guillermomolina.protos.runtime.ProtosBytesValue;

import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosLexicalCell;
import com.guillermomolina.protos.runtime.ProtosModuleBindingLocation;
import com.guillermomolina.protos.runtime.ProtosNumericValueSupport;
import com.guillermomolina.protos.runtime.ProtosMapValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosStringValue;
import com.guillermomolina.protos.runtime.control.ProtosReturnHome;
import com.guillermomolina.protos.runtime.lookup.ProtosSlotLookupResult;
import com.guillermomolina.protos.runtime.modules.ProtosModuleKey;
import com.guillermomolina.protos.runtime.ProtosValueLookup;
import com.guillermomolina.protos.semantic.ast.CanonicalClosure;
import com.guillermomolina.protos.semantic.ast.CanonicalIntrinsic;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.bytecode.BytecodeLocation;
import com.oracle.truffle.api.bytecode.BytecodeNode;
import com.oracle.truffle.api.bytecode.BytecodeRootNode;
import com.oracle.truffle.api.bytecode.ConstantOperand;
import com.oracle.truffle.api.bytecode.LocalAccessor;
import com.oracle.truffle.api.bytecode.LocalRangeAccessor;
import com.oracle.truffle.api.bytecode.ContinuationResult;
import com.oracle.truffle.api.bytecode.ContinuationRootNode;
import com.oracle.truffle.api.bytecode.EpilogExceptional;
import com.oracle.truffle.api.bytecode.EpilogReturn;
import com.oracle.truffle.api.bytecode.ForceQuickening;
import com.oracle.truffle.api.bytecode.GenerateBytecode;
import com.oracle.truffle.api.bytecode.Instrumentation;
import com.oracle.truffle.api.bytecode.Operation;
import com.oracle.truffle.api.bytecode.OperationProxy;
import com.oracle.truffle.api.bytecode.Prolog;
import com.oracle.truffle.api.bytecode.StoreBytecodeIndex;
import com.oracle.truffle.api.bytecode.Variadic;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Idempotent;
import com.oracle.truffle.api.dsl.NonIdempotent;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.nodes.ControlFlowException;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.PropertyGetter;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;

/**
 * The single Protos Bytecode DSL interpreter (I111-B / PLAT065-A).
 *
 * <p>It serves two kinds of physical roots. A genuine guest-semantic
 * top-level, module or Closure activation produced by {@link
 * CanonicalToBytecodeLowerer} is marked {@link #isSemanticActivationRoot()}.
 * Auxiliary structured-dispatch ({@link
 * ProtosStructuredDispatchLowerer}), Task C-prime entry and I/O C-prime roots
 * are created by their own builders and are unmarked, so they never appear
 * as guest frames. Automatic RootTag/RootBodyTag
 * tagging is enabled and applies to every physical root of this single
 * configuration, auxiliary roots included; the lowerer no longer emits an
 * explicit physical-root RootTag. Inline Closure
 * callbacks retain their nested semantic RootTag regions. The generated
 * interpreter does not infer guest identity from the mere existence of a
 * physical root. Object bodies are lowered inline
 * and never become roots. Lexically nested Closure roots are created in the same
 * {@code BytecodeRootNodes} parse as their owner. A captured Closure
 * binding is a shared cell held in a local of each root that reaches it, so
 * no root reads another root's frame.
 *
 * <p>Structured standard natives are not executed inside a semantic root. {@link
 * ProtosBuiltinCallRootNode} enters the auxiliary structured/C-prime root
 * of the Task C-prime entry plan once per such invocation and the semantic
 * root composes the returned C-prime continuation (PLAT014).
 *
 * <p>Each operation owns its implementation. Every call goes through
 * {@link ProtosFunctionCallDispatchers} (the {@code Call*Method}, {@code
 * GetMethod} and {@code CallRequest} operations); structured protocol state
 * lives with its domain owners ({@link ProtosPreparedCalls}, the structured
 * state families and {@link ProtosFrameLocalBindings}). The operations used only by auxiliary roots are
 * declared last.
 */
/*
 * Automatic physical root/body tags, root-scoped locals, materialized local
 * accesses and stored bytecode indices support tooling without a second
 * execution representation. Uninitialized locals retain their cleared state.
 * Runtime fields remain transient and are re-established by lowering or
 * execution; this interpreter has no BytecodeSerializer.
 */
@GenerateBytecode(
        languageClass = ProtosLanguage.class,
        enableYield = true,
        enableTagInstrumentation = true,
        enableBlockScoping = false,
        enableSerialization = true,
        storeBytecodeIndexInFrame = true,
        defaultUncachedThreshold = "4",
        enableTailCallHandlers = true,
        enableUncachedInterpreter = true,
        boxingEliminationTypes = {long.class, double.class},
        sourceContentSupplier = "loadSourceContent",
        tagTreeNodeLibrary = ProtosBytecodeTagTreeNodeExports.class,
        /*
         * PLAT070: internal Protos control transfers unwind directly through
         * generated frames. Active TryFinally cleanup runs for them; guest
         * TryCatch handlers never see them.
         */
        unwindExceptions = {
            ProtosNonLocalReturnException.class,
            ProtosTaskCancellationException.class
        })
@OperationProxy(value = ProtosNumberDivideNode.class, name = "NumberDivide")
@OperationProxy(value = ProtosRichCompareLtNode.class, name = "RichCompareLt")
@OperationProxy(value = ProtosRichCompareEqNode.class, name = "RichCompareEq")
@OperationProxy(value = ProtosRichCompareLeNode.class, name = "RichCompareLe")
@OperationProxy(value = ProtosRichCompareGtNode.class, name = "RichCompareGt")
@OperationProxy(value = ProtosRichCompareGeNode.class, name = "RichCompareGe")
@OperationProxy(value = ProtosExperimentalReadGlobalNode.class, name = "ReadGlobal")
@OperationProxy(value = ProtosExperimentalWriteGlobalNode.class, name = "SelectWriteGlobal")
// Fixed-arity calls and numeric operations shared by every source root.
@OperationProxy(value = ProtosCallNilaryMethodNode.class, name = "CallNilaryMethod")
@OperationProxy(value = ProtosCallUnaryMethodNode.class, name = "CallUnaryMethod", forceCached = true)
@OperationProxy(value = ProtosExperimentalGetMethodNode.class, name = "GetMethod")
@OperationProxy(value = ProtosExperimentalCallBinaryMethodNode.class, name = "CallBinaryMethod")
@OperationProxy(value = ProtosCallTernaryMethodNode.class, name = "CallTernaryMethod")
@OperationProxy(value = ProtosCallQuaternaryMethodNode.class, name = "CallQuaternaryMethod")
@OperationProxy(value = ProtosCallVarargsMethodNode.class, name = "CallVarargsMethod")
@OperationProxy(value = ProtosNumberAddNode.class, name = "NumberAdd")
@OperationProxy(value = ProtosNumberSubtractNode.class, name = "NumberSubtract")
@OperationProxy(value = ProtosNumberMultiplyNode.class, name = "NumberMultiply")
abstract class ProtosBytecodeRootNode extends RootNode implements BytecodeRootNode {
    protected ProtosBytecodeRootNode(
            ProtosLanguage language,
            FrameDescriptor frameDescriptor) {
        super(language, frameDescriptor);
    }

    /*
     * I111-B / PLAT065-A: whether this physical root is a genuine guest
     * semantic activation (module, top-level or Closure). False by default, so every auxiliary structured,
     * Task or I/O C-prime root of this single generated interpreter is
     * non-semantic without any global registry. Set once by
     * CanonicalToBytecodeLowerer#emitRootBody after endRoot(); a reparse
     * replays that lowering and re-establishes the same value.
     */
    @CompilationFinal private transient boolean semanticActivationRoot;

    final void markSemanticActivationRoot() {
        this.semanticActivationRoot = true;
    }

    final boolean isSemanticActivationRoot() {
        return semanticActivationRoot;
    }

    /**
     * (needsTraceAndProfileInstrumentation):
     * whether the internal trace probe is enabled. The JVM-start value of
     * {@link ProtosExperimentalTraceHooks#ENABLED}; false by default.
     */
    @NonIdempotent
    public final boolean needsTraceAndProfileInstrumentation() {
        return ProtosExperimentalTraceHooks.ENABLED;
    }

    /*: records ROOT_RETURN; the value is unchanged. */
    @InliningCutoff
    private void traceOrProfileReturn(VirtualFrame frame, BytecodeNode location, Object value) {
        ProtosExperimentalTraceHooks.record(
                ProtosExperimentalTraceHooks.Event.ROOT_RETURN,
                this,
                location.getBytecodeIndex(frame),
                value);
    }

    /*: records ROOT_EXCEPTION; the exception is unchanged. */
    @InliningCutoff
    private void traceOrProfileException(
            VirtualFrame frame, BytecodeNode location, AbstractTruffleException exception) {
        ProtosExperimentalTraceHooks.record(
                ProtosExperimentalTraceHooks.Event.ROOT_EXCEPTION,
                this,
                location.getBytecodeIndex(frame),
                exception);
    }

    /**
     * (InstrumentCall): records CALL after the
     * last argument of the experimental Call1 and returns it unchanged.
     * Present only in a BytecodeConfig that adds it.
     */
    @Instrumentation(storeBytecodeIndex = true)
    public static final class InstrumentCall {
        @Specialization
        public static Object perform(
                VirtualFrame frame,
                Object value,
                @Bind ProtosBytecodeRootNode root,
                @Bind BytecodeNode bytecodeNode,
                @Bind("$bytecodeIndex") int bytecodeIndex) {
            ProtosExperimentalTraceHooks.record(
                    ProtosExperimentalTraceHooks.Event.CALL, root, bytecodeIndex, value);
            return value;
        }
    }

    /**
     * (InstrumentCallReturn): records
     * CALL_RETURN for the experimental Call1 result and returns it unchanged.
     * Present only in a BytecodeConfig that adds it.
     */
    @Instrumentation(storeBytecodeIndex = true)
    public static final class InstrumentCallReturn {
        @Specialization
        public static Object perform(
                VirtualFrame frame,
                Object result,
                @Bind ProtosBytecodeRootNode root,
                @Bind BytecodeNode bytecodeNode,
                @Bind("$bytecodeIndex") int bytecodeIndex) {
            ProtosExperimentalTraceHooks.record(
                    ProtosExperimentalTraceHooks.Event.CALL_RETURN, root, bytecodeIndex, result);
            return result;
        }
    }

    /**
     *: the Bytecode DSL source content supplier. A
     * Source that already has characters or bytes is returned unchanged.
     * ProtosLanguage provides no content-recovery mechanism, so a Source
     * without content is also returned unchanged; no content is invented.
     */
    static Source loadSourceContent(ProtosLanguage language, Source source) {
        return source;
    }

    /**
     * Root exception interception shared by every root of the generated
     * Protos interpreter: genuine semantic activation roots and auxiliary
     * structured/C-prime roots must select Error handlers identically.
     */
    private static AbstractTruffleException interceptGuestException(
            AbstractTruffleException exception,
            VirtualFrame frame) {
        if (exception instanceof ProtosSignalException transfer) {
            selectGuestHandlerOnRootCrossing(transfer, frame == null ? null : frame.getArguments());
        }
        return exception;
    }

    /*
     * TEST009-E: the Error-path work of interceptGuestException, behind a host
     * boundary (it receives the frame-argument array, never the frame). Inlined,
     * the activation probing/materialization and handler selection were expanded
     * into the exception path of every compiled handler site.
     */
    @com.oracle.truffle.api.CompilerDirectives.TruffleBoundary
    private static void selectGuestHandlerOnRootCrossing(
            ProtosSignalException transfer,
            Object[] frameArguments) {
        /*
         * PERF025-H1: a semantic source root still in compact source-call
         * form materializes its exact activation here, on the Error path,
         * exactly as the former eager root prologue would have.
         */
        if (frameArguments != null && ProtosFrameArguments.hasActivation(frameArguments)) {
            ProtosActivation activation = ProtosFrameArguments.activation(frameArguments);
            /*
             * Selection is semantic authority and must happen before the
             * Bytecode EH table starts crossed TryFinally cleanup. Repeated
             * root crossings are idempotent because the exact transfer
             * remembers the one selected handler token.
             */
            ProtosCoreErrors.selectHandlerIfNeeded(activation, transfer);
        }
    }

    /**
     * Immutable reserved local-slot names of a composition item, carried as a
     * Bytecode constant operand of {@link ComposeLocalSlots}. A non-generic
     * carrier keeps the DSL-generated operand read a checked cast; a
     * parameterized {@code List<String>} operand forces an erased unchecked
     * cast in the generated interpreter.
     */
    public static final class ComposeReservedNames {
        private final java.util.List<String> names;
        private final String[] indexedNames;

        public ComposeReservedNames(java.util.List<String> names) {
            this.names = java.util.List.copyOf(names);
            this.indexedNames = this.names.toArray(new String[0]);
        }

        public java.util.List<String> names() {
            return names;
        }

        /** Internal read-only view for composition; consumers never write it. */
        String[] indexedNamesForComposition() {
            return indexedNames;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof ComposeReservedNames that
                    && names.equals(that.names);
        }

        @Override
        public int hashCode() {
            return names.hashCode();
        }

        @Override
        public String toString() {
            return "ComposeReservedNames[names=" + names + "]";
        }
    }

    /**
     * the single immutable physical layout of a Closure root's
     * declared LOCAL/CELL bindings: each binding's name, the offset and
     * {@link LocalAccessor} of its Bytecode DSL local, and whether that local
     * holds a shared {@link ProtosLexicalCell} (the physical storage, so a
     * capture promoted to a plain local by the inline storage plan is a
     * LOCAL). Both tooling scope projection and the lazy guest Context
     * projection ({@link ProtosBytecodeLexicalFrameView}) read the live frame
     * through it; there is no second layout.
     */
    static final class LexicalFrameLayout {
        @CompilationFinal(dimensions = 1) private final String[] names;
        @CompilationFinal(dimensions = 1) private final int[] offsets;
        @CompilationFinal(dimensions = 1) private final boolean[] cells;
        @CompilationFinal(dimensions = 1) private final LocalAccessor[] accessors;

        LexicalFrameLayout(String[] names, int[] offsets, boolean[] cells, LocalAccessor[] accessors) {
            if (names.length != offsets.length
                    || names.length != cells.length
                    || names.length != accessors.length) {
                throw new IllegalArgumentException("inconsistent lexical frame layout");
            }
            this.names = names.clone();
            this.offsets = offsets.clone();
            this.cells = cells.clone();
            this.accessors = accessors.clone();
        }

        int size() {
            return names.length;
        }

        String name(int index) {
            return names[index];
        }

        int offset(int index) {
            return offsets[index];
        }

        boolean isCell(int index) {
            return cells[index];
        }

        LocalAccessor accessor(int index) {
            return accessors[index];
        }

        /** The layout index of {@code name}, or {@code -1} for a name outside the layout. */
        @ExplodeLoop
        int indexOf(String name) {
            for (int index = 0; index < names.length; index++) {
                if (names[index].equals(name)) {
                    return index;
                }
            }
            return -1;
        }
    }

    static final LexicalFrameLayout EMPTY_LEXICAL_FRAME_LAYOUT =
            new LexicalFrameLayout(new String[0], new int[0], new boolean[0], new LocalAccessor[0]);

    /* The layout of a genuine Closure root, or null for any other root. */
    @CompilationFinal private transient LexicalFrameLayout lexicalFrameLayout;

    @CompilationFinal private transient boolean lexicalFrameEverEscaped;
    private final ConditionProfile publishedClosureFrameProfile =
            ConditionProfile.create();

    /*
     * TEST009-V: the exact root span, recorded by the lowerer only while the diagnostic stable
     * root identity is enabled, so a compiled root has a cross-run name without materializing
     * (reparsing) its lazy source information. Never read by guest execution.
     */
    private transient ProtosDiagnosticRootIdentity.Span diagnosticRootSpan;

    final void recordDiagnosticRootSpan(ProtosDiagnosticRootIdentity.Span span) {
        this.diagnosticRootSpan = span;
    }

    final ProtosDiagnosticRootIdentity.Span diagnosticRootSpan() {
        return diagnosticRootSpan;
    }

    /**
     * The default Truffle text unless {@link ProtosDiagnosticRootIdentity#PROPERTY} is set; then
     * the stable diagnostic target name that {@code engine.CompileOnly} can select across runs.
     */
    @Override
    public String toString() {
        if (ProtosDiagnosticRootIdentity.ENABLED && semanticActivationRoot) {
            return ProtosDiagnosticRootIdentity.targetName(diagnosticRootSpan);
        }
        return super.toString();
    }

    /* PERF038-C: set once, when the first guest exception crosses this root. */
    @CompilationFinal private transient boolean guestExceptionCrossed;

    /* demand-driven caller transport. An effective callee root
     * requests an optional direct caller payload (null, Prelude or
     * Activation), and its Assumption invalidates incoming PICs whenever
     * the monotonic needs change. No ancestral B5 array is retained.
     */
    static final int NEEDS_EXACT_ACTIVATION = 1;
    static final int NEEDS_CALLER_PRELUDE = 2;

    private final CallerNeeds callerNeeds = new CallerNeeds();

    /**
     * requests {@code need} for this root; see {@link CallerNeeds}.
     * Constant-folded to nothing once the need is registered.
     */
    final void requestCallerNeed(int need) {
        callerNeeds.request(need);
    }

    /** test and diagnostic access: whether {@code need} is registered. */
    final boolean hasCallerNeed(int need) {
        return callerNeeds.has(need);
    }

    /** the Assumption that the registered caller needs do not change. */
    final Assumption callerNeedsStability() {
        return callerNeeds.stability();
    }

    private static final class CallerNeeds {
        @CompilationFinal private int registered;
        @CompilationFinal private Assumption stable =
                com.oracle.truffle.api.Truffle.getRuntime()
                        .createAssumption("Protos root caller needs");

        boolean has(int need) {
            return stable.isValid() && (registered & need) == need;
        }

        /*
         * Fail-closed absence: true only while the stability Assumption is
         * valid and no part of {@code need} was ever registered. A change is published together with the
         * invalidation of the stability Assumption consumed by callers.
         */
        boolean lacks(int need) {
            return stable.isValid() && (registered & need) == 0;
        }

        Assumption stability() {
            return stable;
        }

        void request(int need) {
            if (!has(need)) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                register(need);
            }
        }

        /*
         * Interpreter only. The new state is complete before the previous
         * Assumption is invalidated, so code compiled against the new
         * Assumption always observes the registered need.
         */
        private synchronized void register(int need) {
            if ((registered & need) == need && stable.isValid()) {
                return;
            }
            registered |= need;
            Assumption previous = stable;
            stable = com.oracle.truffle.api.Truffle.getRuntime()
                    .createAssumption("Protos root caller needs");
            previous.invalidate();
        }
    }

    /*
     * PERF038-H / I107: conservative source-root execution capability. It is
     * the consumed EFFECTIVE_ROOT non-suspension proof of this emitted root
     * (CanonicalCompilationFacts): parameter binding, every default that may
     * be evaluated and the effective body after authorized lowerer
     * transformations, never the canonical body alone. It does not prove a
     * complete invocation: receiver evaluation, Object.call selection,
     * Context and ReturnHome remain the run-time guards of its consumers.
     * Recorded once per parse, before the root is published, and immutable
     * afterwards; never recomputed per call. Because it is compilation-final,
     * specialized direct call sites that cache this exact root consume it as
     * a compilation constant to omit suspension classification of the
     * outcome; an unproven root keeps every suspension check.
     */
    @CompilationFinal private transient boolean provablyNonSuspendingBody;

    final void recordProvablyNonSuspendingBody(boolean proven) {
        this.provablyNonSuspendingBody = proven;
    }

    final boolean provablyNonSuspendingBody() {
        return provablyNonSuspendingBody;
    }

    /*
     * Conservative static capability of this emitted root: its effective
     * body binds only ordinary parameters and evaluates only literals, reads
     * of those parameters and identity comparisons, so it never reads the
     * Task received from its caller. Recorded once per parse with the
     * non-suspension proof, immutable afterwards and never recomputed per
     * call. Missing evidence is false.
     */
    @CompilationFinal private transient boolean taskObservationProvenAbsent;

    final void recordTaskObservationProvenAbsent(boolean proven) {
        this.taskObservationProvenAbsent = proven;
    }

    final boolean taskObservationProvenAbsent() {
        return taskObservationProvenAbsent;
    }

    /*
     * Whether a direct call site that cached this exact root may leave the
     * caller's Task untransported. Requires the static capability together
     * with the non-suspension and unobservable-ReturnHome proofs of this
     * same root, no registered exact-activation caller need (consumers hold
     * callerNeedsStability(), so a later publication or materialization
     * deoptimizes them before the next omitted transport) and no enabled
     * trace probe. A materialized activation of an invocation entered this
     * way still recovers its exact caller, and therefore the caller's Task,
     * through the ordinary cold caller recovery.
     */
    final boolean admitsTaskTransportOmission() {
        return taskObservationProvenAbsent
                && provablyNonSuspendingBody
                && returnHomeProvenUnobservable
                && !needsTraceAndProfileInstrumentation()
                && callerNeeds.lacks(NEEDS_EXACT_ACTIVATION);
    }

    /*
     * a closure's whole lexical subtree (including defaults and nested
     * closures) cannot target this invocation's return home. The lowering
     * records the proof once on the physical callee root, so a cached direct
     * call does not inspect a closure's execution plan at runtime.
     */
    @CompilationFinal private transient boolean returnHomeProvenUnobservable;

    /*
     * valid while no published invocation owning a materialized
     * physical ReturnHome has been admitted to this proven no-home root.
     * Created once; invalidation is monotonic and the Assumption is never
     * recreated, so a reparse or repeated lowering cannot restore it.
     */
    @CompilationFinal private transient Assumption noPublishedPhysicalOwnerAssumption;

    final void recordReturnHomeProvenUnobservable(boolean proven) {
        returnHomeProvenUnobservable = proven;
        if (proven && noPublishedPhysicalOwnerAssumption == null) {
            noPublishedPhysicalOwnerAssumption =
                    com.oracle.truffle.api.Truffle.getRuntime()
                            .createAssumption("Protos no published physical ReturnHome owner");
        }
    }

    final boolean returnHomeProvenUnobservable() {
        return returnHomeProvenUnobservable;
    }

    /**
     * cold entry admission of a published invocation. When the
     * activation owns a materialized physical ReturnHome, this proven no-home
     * root permanently switches its epilogues to the ordinary owned-home
     * completion. Never creates, binds, completes or closes a home.
     */
    final void admitPublishedPhysicalOwner(ProtosActivation activation) {
        if (!returnHomeProvenUnobservable()) {
            return;
        }
        if (!activation.ownsReturnHome()) {
            return;
        }
        ProtosReturnHome home = activation.returnHomeOrNullForRuntime();
        if (home == null || !home.isMaterialized()) {
            return;
        }
        Assumption assumption = noPublishedPhysicalOwnerAssumption;
        if (assumption != null) {
            assumption.invalidate();
        }
    }

    /**
     * whether the epilogues must run the owned physical-home
     * completion. Missing state fails conservatively.
     */
    final boolean needsPhysicalHomeCompletionInEpilogue() {
        if (!returnHomeProvenUnobservable()) {
            return true;
        }
        Assumption assumption = noPublishedPhysicalOwnerAssumption;
        return assumption == null || !assumption.isValid();
    }

    // Removed per-root numeric return-projection state.

    /**
     * records the physical layout of this genuine Closure root,
     * once per parse, before the root is published. A Closure root without
     * declared bindings records the empty layout, so its escaped frame still
     * has one frame-backed authority (for dynamically created names).
     */
    final void recordLexicalFrameLayout(LexicalFrameLayout layout) {
        this.lexicalFrameLayout = layout == null ? EMPTY_LEXICAL_FRAME_LAYOUT : layout;
    }

    /** The recorded layout, or {@code null} when this is not a genuine Closure root. */
    final LexicalFrameLayout lexicalFrameLayoutOrNull() {
        return lexicalFrameLayout;
    }

    /** Associates a debugger's original materialized frame with its exact Closure activation. */
    @TruffleBoundary
    final void ensureToolingLexicalFrameView(
            MaterializedFrame frame,
            BytecodeNode bytecode,
            int bytecodeIndex,
            ProtosActivation activation) {
        LexicalFrameLayout layout = lexicalFrameLayout;
        if (layout != null && activation.publishedClosureLexicalAuthorityForRuntime() == null) {
            installLexicalFrameView(frame, bytecode, bytecodeIndex, activation, layout);
        }
    }

    /*
     * a Java/Truffle internal failure that leaves this activation without
     * crossing EpilogForException still definitively abandons it.
     */
    @Override
    public Throwable interceptInternalException(
            Throwable t,
            VirtualFrame frame,
            BytecodeNode bytecodeNode,
            int bytecodeIndex) {
        closeOwnedReturnHome(frame, null);
        return t;
    }

    @Override
    public AbstractTruffleException interceptTruffleException(
            AbstractTruffleException exception,
            VirtualFrame frame,
            BytecodeNode bytecodeNode,
            int bytecodeIndex) {
        /*
         * PERF038-C: a root no guest exception has crossed yet compiles this
         * interception as a deoptimization; the first crossing invalidates
         * that code once and every crossing then runs the unchanged work.
         */
        if (!guestExceptionCrossed) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            guestExceptionCrossed = true;
        }
        /*
         * CLI008-C1: Protos exceptions carry no location node, so the guest stack trace has no
         * bytecode index for the frame they are raised in. Record the first semantic crossing on
         * the occurrence itself; this runs only while an exception is already unwinding.
         * I111-B: an auxiliary root is never that semantic crossing.
         */
        if (semanticActivationRoot) {
            ProtosDiagnosticTraceCapture.recordOrigin(exception, frame, bytecodeNode, bytecodeIndex);
        }
        return interceptGuestException(exception, frame);
    }

    /**
     * PERF025-H1 sole operand seam for "the current activation" of a semantic source root (see
     * {@code CanonicalToBytecodeLowerer#emitCurrentActivation}). A guarded ordinary send, a stable
     * direct Closure call, or a Task-owned direct source Closure entry enters the root with a
     * compact source-call ABI and no rich activation. The root has no activation prologue: the
     * exact activation is materialized only when an operation that actually needs it executes,
     * at most once, and is published into frame argument 0 (see {@link
     * ProtosFrameArguments#activation}), so the body, root exception interception and debugger
     * scopes all observe one activation identity. A frame that already carries an activation
     * returns it unchanged.
     */
    /**
     * root entry does not install a materialized-frame
     * lexical authority.
     *
     * Admitted module bindings use the existing frame-native creation
     * transition. The first establishment installs durable authority
     * while the executing frame is live. Before any establishment,
     * there is no frame-owned lexical state to preserve on exit,
     * exception or suspension.
     *
     * Keeping the Prolog preserves the Bytecode DSL root shape.
     *
     * (EnterCalleeContext): every source root is entered with
     * the same B5 invocation header and runs the same empty prologue. No
     * ProtosActivation is materialized and no Object.call, ReturnHome, Task
     * or suspension check runs.
     */
    @Prolog(storeBytecodeIndex = false)
    public static final class EnterRoot {
        @Specialization
        public static void perform() {
            // No eager activation or compilation-profile state.
        }
    }

    /** The normal-return epilogue of a source root. */
    @EpilogReturn(storeBytecodeIndex = true)
    public static final class EpilogForReturn {
        /*
         * the primitive Boolean carrier of a native comparison or
         * Boolean selection never escapes the physical root; the externally
         * observable result is canonical true/false.
         */
        @Specialization
        public static Object doBoolean(
                boolean value,
                @Bind VirtualFrame frame,
                @Bind ProtosBytecodeRootNode root,
                @Bind BytecodeNode bytecodeNode,
                @Bind("$bytecodeIndex") int bytecodeIndex) {
            return perform(Boolean.valueOf(value), frame, root, bytecodeNode, bytecodeIndex);
        }

        @Specialization(replaces = "doBoolean")
        public static Object perform(
                Object value,
                @Bind VirtualFrame frame,
                @Bind ProtosBytecodeRootNode root,
                @Bind BytecodeNode bytecodeNode,
                @Bind("$bytecodeIndex") int bytecodeIndex) {
            root.retainPublishedClosureFrameOnExit(frame, bytecodeNode, bytecodeIndex);
            if (root.needsTraceAndProfileInstrumentation()) {
                root.traceOrProfileReturn(frame, bytecodeNode, value);
            }
            // A suspended invocation retains its owned ReturnHome.
            // Normal completion closes only the home owned by this root.
            if (root.needsPhysicalHomeCompletionInEpilogue()) {
                closeOwnedReturnHome(frame, value);
            }
            return value;
        }
    }

    /**
     * The exceptional root epilogue preserves published lexical state,
     * records diagnostics and completes the root's owned return home.
     * The original guest exception propagates unchanged.
     */
    @EpilogExceptional(storeBytecodeIndex = true)
    public static final class EpilogForException {
        @Specialization
        public static void perform(
                AbstractTruffleException exception,
                @Bind VirtualFrame frame,
                @Bind ProtosBytecodeRootNode root,
                @Bind BytecodeNode bytecodeNode,
                @Bind("$bytecodeIndex") int bytecodeIndex) {
            root.retainPublishedClosureFrameOnExit(frame, bytecodeNode, bytecodeIndex);
            if (root.needsTraceAndProfileInstrumentation()) {
                root.traceOrProfileException(frame, bytecodeNode, exception);
            }
            // A guest Error definitively leaves this invocation.
            // Captured ReturnHomes remain owned by their outer roots.
            if (root.needsPhysicalHomeCompletionInEpilogue()) {
                closeOwnedReturnHome(frame, null);
            }
        }
    }

    /*
     * Closes the materialized ReturnHome
     * this activation owns when it terminates. A pending result (Bytecode DSL
     * suspension or terminal transport) is not a termination; captured homes
     * and the unobservable marker are never owned; closing is idempotent.
     */
    static void closeOwnedReturnHome(VirtualFrame frame, Object result) {
        ProtosReturnHome owned =
                ProtosFrameArguments.ownedReturnHomeOrNull(frame.getArguments());
        if (owned == null) {
            return;
        }
        if (result != null && suspendedOwnedResult(result)) {
            return;
        }
        owned.closeOwned();
    }

    /** Physical owners alone need the full continuation classification. */
    @TruffleBoundary
    private static boolean suspendedOwnedResult(Object result) {
        return ProtosTailCallContinuation.isSuspended(result);
    }

    @Operation(storeBytecodeIndex = false)
    @ConstantOperand(type = boolean.class, name = "callerReference")
    public static final class CurrentActivation {
        /*
         * PERF038-C: the already-published activation and the unpublished
         * state are separate specializations, so a site that has only ever
         * seen one state compiles no path for the other. The guard is
         * re-evaluated on every execution.
         *
         * I113: with {@code callerReference} (PERF038-D, the former
         * CurrentCallerReference) an unpublished frame answers its compact
         * arguments instead of materializing; otherwise both specializations
         * return exactly what ProtosFrameArguments.activation returns.
         *
         * a resumed continuation frame reads the caller its
         * dispatcher sealed into B5[3] at suspension; it never relies on
         * active-stack recovery of a returned caller frame.
         */
        @Specialization(guards = "hasPublishedActivation(frame)")
        public static Object published(boolean callerReference, @Bind VirtualFrame frame) {
            return ProtosFrameArguments.publishedActivation(frame.getArguments());
        }

        @Specialization(guards = "!hasPublishedActivation(frame)")
        public static Object unpublished(
                boolean callerReference,
                @Bind VirtualFrame frame,
                @Bind ProtosBytecodeRootNode root) {
            if (callerReference) {
                /*
                 * every caller-reference consumer is a send or call
                 * selection that reads this root's prelude; an inherited one
                 * its caller did not transport is this root's caller need.
                 */
                Object[] arguments = frame.getArguments();
                if (ProtosFrameArguments.inheritsUntransportedCallerPrelude(arguments)) {
                    root.requestCallerNeed(NEEDS_CALLER_PRELUDE);
                }
                return arguments;
            }
            // publishing this root's own activation is a caller need.
            root.requestCallerNeed(NEEDS_EXACT_ACTIVATION);
            return ProtosFrameArguments.activation(frame);
        }

        @NonIdempotent
        static boolean hasPublishedActivation(VirtualFrame frame) {
            return ProtosFrameArguments.hasPublishedActivation(frame.getArguments());
        }
    }

    // ---- Closure parameter binding -------------------------------------------------

    /*
     * parameter operations. Supplied positional values keep their B5
     * offsets after publication, so a parameter is read from the frame
     * arguments in every state and stored immediately in its local or cell by
     * the lowering; no activation is materialized to bind a parameter. D203:
     * a position the call did not supply reads canonical null; no
     * Closure-binding operation signals an argument-count Error.
     */

    /** Supplied positional argument {@code index}, or canonical null (D203). */
    @Operation(storeBytecodeIndex = false)
    @ConstantOperand(type = int.class, name = "index")
    public static final class LoadSuppliedArgument {
        @Specialization
        public static Object perform(int index, @Bind VirtualFrame frame) {
            return ProtosFrameArguments.compactSuppliedArgumentOrNull(frame.getArguments(), index);
        }
    }


    /**
     * experimental grouped positional parameter initialization.
     *
     * The supplied B5 argument suffix is copied directly into the
     * consecutively allocated parameter locals. Missing arguments
     * retain the canonical Protos null value. No activation or
     * lexical context is materialized by this operation.
     */
    @Operation(storeBytecodeIndex = false)
    @ConstantOperand(type = LocalRangeAccessor.class, name = "locals")
    public static final class CopySuppliedArguments {
        @Specialization
        @ExplodeLoop
        public static void perform(
                LocalRangeAccessor locals,
                @Bind VirtualFrame frame,
                @Bind BytecodeNode bytecodeNode) {
            for (int index = 0; index < locals.getLength(); index++) {
                locals.setObject(
                        bytecodeNode,
                        frame,
                        index,
                        ProtosFrameArguments.compactSuppliedArgumentOrNull(
                                frame.getArguments(), index));
            }
        }
    }

    /** Whether the call supplied positional argument {@code index}. */
    @Operation(storeBytecodeIndex = false)
    @ConstantOperand(type = int.class, name = "index")
    public static final class HasSuppliedArgument {
        @Specialization
        public static boolean perform(int index, @Bind VirtualFrame frame) {
            return ProtosFrameArguments.hasSuppliedParameter(
                    frame.getArguments(), index);
        }
    }

    /**
     * The frozen Array of the supplied arguments after the first {@code
     * positionalParametersBeforeRest}, the value of a rest parameter.
     */
    @Operation(storeBytecodeIndex = false)
    @ConstantOperand(type = int.class, name = "positionalParametersBeforeRest")
    public static final class LoadRestArguments {
        @Specialization
        public static Object perform(int positionalParametersBeforeRest, @Bind VirtualFrame frame) {
            return ProtosFrameArguments.restArgumentArray(
                    frame.getArguments(), positionalParametersBeforeRest);
        }
    }

    // ---- lexical cells --------------------------------------------------------

    /*
     * (cell variables): a Closure binding captured by a nested
     * Closure is one ProtosLexicalCell held in a Bytecode DSL local of its
     * owning root. The owner creates the cell at entry; a capturing Closure
     * receives the same cell instance at creation and copies it into its own
     * local at entry. Reads and writes use the cell directly: there is no
     * owner-frame selection and no name-based fallback.
     */

    /** A fresh, uninitialized cell for one owned CELL binding. */
    @Operation
    public static final class CreateCell {
        @Specialization
        public static ProtosLexicalCell perform() {
            return new ProtosLexicalCell();
        }
    }

    /** Captured cell {@code index} of the invoked Closure, read at root entry. */
    @Operation(storeBytecodeIndex = false)
    @ConstantOperand(type = int.class, name = "index")
    public static final class LoadCapturedCell {
        @Specialization
        public static ProtosLexicalCell perform(int index, @Bind VirtualFrame frame) {
            return ProtosFrameArguments.capturedCells(frame.getArguments())[index];
        }
    }

    /**
     * The value of a cell whose binding the lowering proved PRESENT (an
     * established binding whose owner's Context can never be observed). An
     * ABSENT cell here is an internal invariant violation, answered with the
     * unqualified-lookup Error; every other cell read uses {@link
     * LoadLexicalCell}.
     */
    @Operation(storeBytecodeIndex = true)
    @ConstantOperand(type = LocalAccessor.class, name = "cell")
    public static final class LoadCell {
        @Specialization
        public static Object perform(
                LocalAccessor cell,
                @Bind VirtualFrame frame,
                @Bind BytecodeNode bytecode) {
            ProtosLexicalCell lexicalCell = (ProtosLexicalCell) cell.getObject(bytecode, frame);
            Object value = lexicalCell.valueOrNullForRuntime();
            if (value == null) {
                throw uninitializedCellError(frame.getArguments());
            }
            return value;
        }
    }

    @TruffleBoundary
    static ProtosSignalException uninitializedCellError(Object[] arguments) {
        return new ProtosSignalException(
                ProtosCoreErrors.newUnqualifiedLookupError(
                        ProtosFrameArguments.activation(arguments)));
    }

    /** Stores the guest value {@code value} into the cell held by {@code cell}. */
    @Operation(storeBytecodeIndex = true)
    @ConstantOperand(type = LocalAccessor.class, name = "cell")
    public static final class StoreCell {
        @Specialization
        public static void perform(
                LocalAccessor cell,
                Object value,
                @Bind VirtualFrame frame,
                @Bind BytecodeNode bytecode) {
            ProtosLexicalCell lexicalCell = (ProtosLexicalCell) cell.getObject(bytecode, frame);
            if (lexicalCell.isDiscardedParallelCaptureForRuntime()) {
                throw uninitializedCellError(frame.getArguments());
            }
            lexicalCell.setForRuntime(value);
        }
    }

    /**
     * Stores a completed value, held in the temporary local {@code value},
     * into the cell held by {@code cell}: the value is read at its last use
     * and cleared, exactly as {@link LoadAndClearTempLocal} does, and then
     * written with the {@link StoreCell} semantics. Nothing here invokes
     * guest behavior.
     */
    @Operation(storeBytecodeIndex = true)
    @ConstantOperand(type = LocalAccessor.class, name = "cell")
    @ConstantOperand(type = LocalAccessor.class, name = "value")
    public static final class StoreCellFromLocal {
        @Specialization
        public static void perform(
                LocalAccessor cell,
                LocalAccessor value,
                @Bind VirtualFrame frame,
                @Bind BytecodeNode bytecode) {
            Object completedValue =
                    LoadAndClearTempLocal.doObject(value, frame, bytecode);
            StoreCell.perform(cell, completedValue, frame, bytecode);
        }
    }

    /**
     * Installs the creator's cells, in the Closure scope's constant
     * captured-cell order, into a Closure created by the enclosed operation.
     */
    @Operation
    public static final class CaptureClosureCells {
        @Specialization
        public static ProtosClosureValue perform(
                ProtosClosureValue closure, @Variadic Object[] cells) {
            ProtosLexicalCell[] captured = new ProtosLexicalCell[cells.length];
            for (int index = 0; index < cells.length; index++) {
                captured[index] = (ProtosLexicalCell) cells[index];
            }
            return closure.installCapturedCellsForRuntime(captured);
        }
    }

    // ---- lexical frame publication and presence-aware access ------------

    /*
     * (fast locals versus frame-locals materialization).
     * The common unobserved path keeps LoadLocal/StoreLocal and the plain cell
     * operations. A root whose frame must survive (its Context is observed, or
     * a nested Closure may need to select one of its bindings by name) binds
     * its materialized frame to its exact activation once, through
     * ProtosBytecodeLexicalFrameView. Sites whose binding may be ABSENT use the
     * presence-aware operations below: the direct physical access while the
     * binding is PRESENT, and ProtosLexicalSelectionNode otherwise.
     */

    /*
     * The activation of this frame for a cold lexical miss, an error or a
     * publication: the published one, or this compact frame's own activation,
     * published exactly as CurrentActivation does (a caller need of this root).
     */
    static ProtosActivation lexicalActivation(VirtualFrame frame, ProtosBytecodeRootNode root) {
        Object[] arguments = frame.getArguments();
        if (ProtosFrameArguments.hasPublishedActivation(arguments)) {
            return ProtosFrameArguments.publishedActivation(arguments);
        }
        root.requestCallerNeed(NEEDS_EXACT_ACTIVATION);
        return ProtosFrameArguments.activation(frame);
    }

    /*
     * The guest Context of this frame's activation when it already exists,
     * else null. A compact frame has no activation, hence no Context: it is
     * OPEN and unfrozen. Never publishes or materializes anything.
     */
    static ProtosObjectValue observedContextOrNull(VirtualFrame frame) {
        Object[] arguments = frame.getArguments();
        return ProtosFrameArguments.hasPublishedActivation(arguments)
                ? ProtosFrameArguments.publishedActivation(arguments)
                        .materializedContextOrNullForRuntime()
                : null;
    }

    /*
     * Whether an owned LOCAL binding is PRESENT: its local is neither cleared
     * (a removal) nor still holding the uninitialized Java null of a local
     * whose binding point has not executed. A guest null is a PRESENT value.
     */
    @NonIdempotent
    public static boolean localBindingPresent(LocalAccessor local, BytecodeNode bytecode, VirtualFrame frame) {
        return !local.isCleared(bytecode, frame) && local.getObject(bytecode, frame) != null;
    }

    /* Whether a cell's binding is PRESENT; re-evaluated on every execution. */
    @NonIdempotent
    public static boolean cellBindingPresent(ProtosLexicalCell cell) {
        return cell.isPresentForRuntime();
    }

    @TruffleBoundary
    static ProtosSignalException lexicalMutationError(ProtosActivation activation) {
        return new ProtosSignalException(ProtosCoreErrors.newError(activation));
    }

    /*
     * Binds the original frame to its exact activation and this root's
     * physical layout, once. The VirtualFrame is materialized here, before
     * crossing the boundary, so it never escapes as a VirtualFrame.
     */
    final void ensureLexicalFrameView(
            VirtualFrame frame,
            BytecodeNode bytecode,
            int bytecodeIndex,
            ProtosActivation activation) {
        LexicalFrameLayout layout = lexicalFrameLayout;
        if (layout == null || activation.publishedClosureLexicalAuthorityForRuntime() != null) {
            return;
        }
        if (!lexicalFrameEverEscaped) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            lexicalFrameEverEscaped = true;
        }
        installLexicalFrameView(frame.materialize(), bytecode, bytecodeIndex, activation, layout);
    }

    /**
     * A published Closure activation can outlive its physical root.
     * Preserve its live lexical frame on normal or exceptional departure.
     * Compact calls without a published activation stay unmaterialized.
     */
    private void retainPublishedClosureFrameOnExit(
            VirtualFrame frame,
            BytecodeNode bytecode,
            int bytecodeIndex) {
        if (lexicalFrameLayout == null) {
            return;
        }
        if (publishedClosureFrameProfile.profile(
                ProtosFrameArguments.hasPublishedActivation(frame.getArguments()))) {
            retainEscapedClosureFrame(
                    frame,
                    bytecode,
                    bytecodeIndex,
                    ProtosFrameArguments.publishedActivation(frame.getArguments()));
        }
    }

    @InliningCutoff
    private void retainEscapedClosureFrame(
            VirtualFrame frame,
            BytecodeNode bytecode,
            int bytecodeIndex,
            ProtosActivation activation) {
        ensureLexicalFrameView(frame, bytecode, bytecodeIndex, activation);
    }

    @TruffleBoundary
    private void installLexicalFrameView(
            MaterializedFrame frame,
            BytecodeNode bytecode,
            int bytecodeIndex,
            ProtosActivation activation,
            LexicalFrameLayout layout) {
        ProtosBytecodeLexicalFrameView view =
                new ProtosBytecodeLexicalFrameView(
                        frame, bytecode.getBytecodeLocation(bytecodeIndex), layout);
        activation.installPublishedClosureLexicalAuthorityForRuntime(view);
        view.attachCellOwners(activation);
    }

    /**
     * Binds this Closure frame to its activation before a capture or escape
     * that requires its bindings to stay selectable by name. Idempotent.
     */
    @Operation(storeBytecodeIndex = true)
    public static final class EnsureCurrentClosureLexicalView {
        @Specialization
        public static void perform(
                @Bind VirtualFrame frame,
                @Bind ProtosBytecodeRootNode root,
                @Bind BytecodeNode bytecode,
                @Bind("$bytecodeIndex") int bytecodeIndex) {
            root.ensureLexicalFrameView(
                    frame, bytecode, bytecodeIndex, lexicalActivation(frame, root));
        }
    }

    /**
     * The {@code context} intrinsic of a genuine Closure root: binds the frame
     * first, then answers the activation's single guest Context, created
     * directly with the frame-backed authority when it does not exist yet.
     */
    @Operation(storeBytecodeIndex = true)
    public static final class PublishCurrentClosureContext {
        @Specialization
        public static ProtosObjectValue perform(
                @Bind VirtualFrame frame,
                @Bind ProtosBytecodeRootNode root,
                @Bind BytecodeNode bytecode,
                @Bind("$bytecodeIndex") int bytecodeIndex) {
            ProtosActivation activation = lexicalActivation(frame, root);
            root.ensureLexicalFrameView(frame, bytecode, bytecodeIndex, activation);
            return activation.context();
        }
    }

    /**
     * The {@code context} intrinsic inside a callback that runs inline in
     * this root. The first observation in one inline invocation creates that
     * invocation's genuine activation, as the callback's own invocation would:
     * its Closure is created over this root's activation, whose frame is bound
     * to its live view first, and its own bindings are the live view of the
     * inline region's locals in this same frame. Later observations in the
     * same invocation answer that activation's single Context; the next
     * invocation starts with an empty cache and gets a fresh one.
     */
    @Operation(storeBytecodeIndex = true)
    @ConstantOperand(type = LocalAccessor.class, name = "activationCache")
    @ConstantOperand(type = LexicalFrameLayout.class, name = "layout")
    @ConstantOperand(type = ProtosSelectedCallbackPlan.class, name = "callback")
    public static final class PublishInlineCallbackContext {
        @Specialization
        public static ProtosObjectValue perform(
                LocalAccessor activationCache,
                LexicalFrameLayout layout,
                ProtosSelectedCallbackPlan callback,
                @Bind VirtualFrame frame,
                @Bind ProtosBytecodeRootNode root,
                @Bind BytecodeNode bytecode,
                @Bind("$bytecodeIndex") int bytecodeIndex) {
            if (!activationCache.isCleared(bytecode, frame)
                    && activationCache.getObject(bytecode, frame) instanceof ProtosActivation inline) {
                return inline.context();
            }
            ProtosActivation creator = lexicalActivation(frame, root);
            root.ensureLexicalFrameView(frame, bytecode, bytecodeIndex, creator);
            ProtosActivation inline = materialize(
                    frame.materialize(), bytecode.getBytecodeLocation(bytecodeIndex),
                    layout, callback, creator);
            activationCache.setObject(bytecode, frame, inline);
            return inline.context();
        }

        @TruffleBoundary
        static ProtosActivation materialize(
                MaterializedFrame frame,
                BytecodeLocation location,
                LexicalFrameLayout layout,
                ProtosSelectedCallbackPlan callback,
                ProtosActivation creator) {
            ProtosClosureValue closure = callback.materialize(creator);
            ProtosActivation inline = ProtosActivation.forClosureInvocation(
                    closure,
                    java.util.List.of(),
                    creator.prelude().orElse(null),
                    creator.actorModuleState(),
                    creator.currentModuleKey().orElse(null),
                    creator.executionDomain());
            ProtosBytecodeLexicalFrameView view =
                    new ProtosBytecodeLexicalFrameView(frame, location, layout);
            inline.installPublishedClosureLexicalAuthorityForRuntime(view);
            view.attachCellOwners(inline);
            return inline;
        }
    }

    /**
     * Ends the previous inline invocation of a context-observing callback
     * before its region locals are reused: an activation materialized by that
     * invocation keeps its bindings as values of its own, so its Context no
     * longer observes the locals of a later invocation.
     */
    @Operation(storeBytecodeIndex = false)
    @ConstantOperand(type = LocalAccessor.class, name = "activationCache")
    public static final class DetachInlineCallbackActivation {
        @Specialization
        public static void perform(
                LocalAccessor activationCache,
                @Bind VirtualFrame frame,
                @Bind BytecodeNode bytecode) {
            if (!activationCache.isCleared(bytecode, frame)
                    && activationCache.getObject(bytecode, frame) instanceof ProtosActivation previous) {
                detach(previous);
            }
        }

        @TruffleBoundary
        static void detach(ProtosActivation previous) {
            if (previous.publishedClosureLexicalAuthorityForRuntime()
                    instanceof ProtosBytecodeLexicalFrameView view) {
                view.detach();
            }
        }
    }

    /**
     * DeleteLocal-style reset of a physical callback local.
     * Root scoping remains enabled; a fresh semantic callback activation
     * must not inherit PRESENT bindings from an earlier invocation.
     */
    @Operation(storeBytecodeIndex = false)
    @ConstantOperand(type = LocalAccessor.class, name = "local")
    public static final class ResetInlineCallbackLocal {
        @Specialization
        public static void perform(
                LocalAccessor local,
                @Bind VirtualFrame frame,
                @Bind BytecodeNode bytecodeNode) {
            local.clear(bytecodeNode, frame);
        }
    }

    /**
     * Presence-aware read of an owned LOCAL binding (checked fast
     * local load). Presence is the state of this activation's own local only:
     * a removal in one activation never affects another activation or the
     * specialization of this site. {@link #present} is the plain local read;
     * {@link #absent} (a cleared or never-written local) continues with live
     * lexical selection and is only compiled once some activation misses.
     */
    @Operation(storeBytecodeIndex = true)
    @ConstantOperand(type = String.class, name = "name")
    @ConstantOperand(type = LocalAccessor.class, name = "local")
    public static final class LoadLexicalLocal {
        @NonIdempotent
        static boolean localPresent(
                LocalAccessor local,
                BytecodeNode bytecode,
                VirtualFrame frame) {
            return ProtosBytecodeRootNode.localBindingPresent(
                    local, bytecode, frame);
        }

        @Specialization(guards = "localPresent(local, bytecode, frame)")
        public static Object present(
                String name,
                LocalAccessor local,
                @Bind VirtualFrame frame,
                @Bind BytecodeNode bytecode) {
            return local.getObject(bytecode, frame);
        }

        @Specialization(guards = "!localPresent(local, bytecode, frame)")
        public static Object absent(
                String name,
                LocalAccessor local,
                @Bind VirtualFrame frame,
                @Bind BytecodeNode bytecode,
                @Bind ProtosBytecodeRootNode root,
                @Cached ProtosLexicalSelectionNode selection) {
            return selection.execute(lexicalActivation(frame, root), name, false);
        }
    }

    /**
     * Presence-aware read of a CELL binding (owned or captured): the cell's
     * value while it is PRESENT, else live lexical selection, which may reach
     * an outer binding or the receiver. Presence belongs to the cell itself.
     */
    @Operation(storeBytecodeIndex = true)
    @ConstantOperand(type = String.class, name = "name")
    public static final class LoadLexicalCell {
        @NonIdempotent
        static boolean cellPresent(ProtosLexicalCell cell) {
            return ProtosBytecodeRootNode.cellBindingPresent(cell);
        }

        @Specialization(guards = "cellPresent(cell)")
        public static Object present(String name, ProtosLexicalCell cell) {
            return cell.valueOrNullForRuntime();
        }

        @Specialization(guards = "!cellPresent(cell)")
        public static Object absent(
                String name,
                ProtosLexicalCell cell,
                @Bind VirtualFrame frame,
                @Bind ProtosBytecodeRootNode root,
                @Cached ProtosLexicalSelectionNode selection) {
            return selection.execute(lexicalActivation(frame, root), name, false);
        }
    }

    /**
     * Write-target selection of a bare assignment to an owned LOCAL binding,
     * before its right-hand side: the binding's own local while it is
     * PRESENT, else the destination selected by live lexical selection.
     */
    @Operation(storeBytecodeIndex = true)
    @ConstantOperand(type = String.class, name = "name")
    @ConstantOperand(type = LocalAccessor.class, name = "local")
    public static final class SelectLexicalLocalWrite {
        @Specialization
        public static Object perform(
                String name,
                LocalAccessor local,
                @Bind VirtualFrame frame,
                @Bind BytecodeNode bytecode,
                @Bind ProtosBytecodeRootNode root,
                @Cached ProtosLexicalSelectionNode selection) {
            if (localBindingPresent(local, bytecode, frame)) {
                return ResolvedLexicalWriteTarget.ownPhysicalBinding();
            }
            return selection.execute(lexicalActivation(frame, root), name, true);
        }
    }

    /**
     * The assignment through a destination selected by {@link
     * SelectLexicalLocalWrite}; never selects again. The own local must still
     * be PRESENT and its Context not FROZEN, exactly as an assignment through
     * the Context; otherwise the assignment Error is signaled.
     */
    @Operation(storeBytecodeIndex = true)
    @ConstantOperand(type = String.class, name = "name")
    @ConstantOperand(type = LocalAccessor.class, name = "local")
    public static final class AssignLexicalLocal {
        @Specialization
        public static Object perform(
                String name,
                LocalAccessor local,
                Object destination,
                Object value,
                @Bind VirtualFrame frame,
                @Bind BytecodeNode bytecode,
                @Bind ProtosBytecodeRootNode root) {
            ResolvedLexicalWriteTarget target = (ResolvedLexicalWriteTarget) destination;
            if (!target.isOwnPhysicalBinding()) {
                return ProtosFrameLocalBindings.assignResolvedLexicalTarget(
                        lexicalActivation(frame, root), target, name, value);
            }
            ProtosObjectValue context = observedContextOrNull(frame);
            if (!localBindingPresent(local, bytecode, frame)
                    || (context != null && context.isFrozen())) {
                throw lexicalMutationError(lexicalActivation(frame, root));
            }
            local.setObject(bytecode, frame, value);
            return value;
        }
    }

    /**
     * Write-target selection of a bare assignment to a CELL binding, before
     * its right-hand side: the cell itself while its binding is PRESENT, else
     * the destination selected by live lexical selection.
     */
    @Operation(storeBytecodeIndex = true)
    @ConstantOperand(type = String.class, name = "name")
    public static final class SelectLexicalCellWrite {
        @Specialization
        public static Object perform(
                String name,
                ProtosLexicalCell cell,
                @Bind VirtualFrame frame,
                @Bind ProtosBytecodeRootNode root,
                @Cached ProtosLexicalSelectionNode selection) {
            if (cell.isPresentForRuntime()) {
                return cell;
            }
            return selection.execute(lexicalActivation(frame, root), name, true);
        }
    }

    /**
     * The assignment through a destination selected by {@link
     * SelectLexicalCellWrite}; never selects again. A selected cell must still
     * be PRESENT and its owner's Context not FROZEN.
     */
    @Operation(storeBytecodeIndex = true)
    @ConstantOperand(type = String.class, name = "name")
    public static final class AssignLexicalCell {
        @Specialization
        public static Object perform(
                String name,
                Object destination,
                Object value,
                @Bind VirtualFrame frame,
                @Bind ProtosBytecodeRootNode root) {
            if (destination instanceof ProtosLexicalCell cell) {
                if (!cell.isPresentForRuntime() || cell.ownerContextFrozenForRuntime()) {
                    throw lexicalMutationError(lexicalActivation(frame, root));
                }
                cell.setForRuntime(value);
                return value;
            }
            return ProtosFrameLocalBindings.assignResolvedLexicalTarget(
                    lexicalActivation(frame, root),
                    (ResolvedLexicalWriteTarget) destination,
                    name,
                    value);
        }
    }

    /**
     * A target-less creation of an owned LOCAL binding that may find it
     * PRESENT (a duplicate creation) or its Context CLOSED/FROZEN: the
     * ordinary {@code :} rules, then the store into the same local.
     */
    @Operation(storeBytecodeIndex = true)
    @ConstantOperand(type = String.class, name = "name")
    @ConstantOperand(type = LocalAccessor.class, name = "local")
    public static final class CreateLexicalLocal {
        @Specialization
        public static Object perform(
                String name,
                LocalAccessor local,
                Object value,
                @Bind VirtualFrame frame,
                @Bind BytecodeNode bytecode,
                @Bind ProtosBytecodeRootNode root) {
            ProtosObjectValue context = observedContextOrNull(frame);
            if ((context != null && !context.isOpen())
                    || localBindingPresent(local, bytecode, frame)) {
                throw lexicalMutationError(lexicalActivation(frame, root));
            }
            local.setObject(bytecode, frame, value);
            return value;
        }
    }

    /**
     * The CELL counterpart of {@link CreateLexicalLocal}: a recreation
     * establishes the value in the same cell, which every capturing Closure
     * keeps sharing.
     */
    @Operation(storeBytecodeIndex = true)
    @ConstantOperand(type = String.class, name = "name")
    public static final class CreateLexicalCell {
        @Specialization
        public static Object perform(
                String name,
                ProtosLexicalCell cell,
                Object value,
                @Bind VirtualFrame frame,
                @Bind ProtosBytecodeRootNode root) {
            ProtosObjectValue context = observedContextOrNull(frame);
            if (cell.isPresentForRuntime()
                    || (context != null && !context.isOpen())
                    || cell.ownerContextRejectsCreationForRuntime()) {
                throw lexicalMutationError(lexicalActivation(frame, root));
            }
            cell.setForRuntime(ProtosNumericValueSupport.guestValue(value));
            return value;
        }
    }

    /*
     * validated host index of a standard Bytes receiver; an index
     * outside 0..size-1 (including any outside the int range) signals
     * the standard guest Error.
     */
    private static int existingBytesIndex(
            ProtosBytesValue bytes, long index, VirtualFrame frame,
            Node inliningTarget, InlinedBranchProfile errorProfile) {
        if (Long.compareUnsigned(index, bytes.indexedSize()) >= 0) {
            errorProfile.enter(inliningTarget);
            throw indexedError(frame);
        }
        return (int) index;
    }

    /*
     * validated host index of an Array BinarySubscript receiver;
     * an index outside 0..size-1 signals the standard guest Error.
     */
    private static int existingArrayIndex(
            ProtosArrayValue array, long index, VirtualFrame frame,
            Node inliningTarget, InlinedBranchProfile errorProfile) {
        if (Long.compareUnsigned(index, array.indexedSize()) >= 0) {
            errorProfile.enter(inliningTarget);
            throw indexedError(frame);
        }
        return (int) index;
    }

    /*
     * validated host index of a standard Array atPut: FROZEN first,
     * then the signed long index against the current size, exactly as the
     * standard Array atPut behavior, before any narrowing to a host int.
     */
    private static int writableArrayIndex(
            ProtosArrayValue array, long index, VirtualFrame frame,
            Node inliningTarget, InlinedBranchProfile errorProfile) {
        if (array.isFrozen()) {
            errorProfile.enter(inliningTarget);
            throw indexedError(frame);
        }
        return existingArrayIndex(
                array, index, frame, inliningTarget, errorProfile);
    }

    /*
     * Standard indexed Error shared by the Array and Bytes operations; kept
     * out of the ordinary indexed fast path.
     */
    @InliningCutoff
    private static ProtosSignalException indexedError(
            VirtualFrame frame) {
        return indexedErrorOf(frame.getArguments());
    }

    /*
     * The cold activation recovery, Error construction and signaling of
     * indexedError. Only the frame arguments cross this boundary; handler
     * selection only records the selected frame and invokes no guest
     * behavior.
     */
    @TruffleBoundary
    private static ProtosSignalException indexedErrorOf(
            Object[] arguments) {
        ProtosActivation activation =
                ProtosFrameArguments.activation(arguments);
        return ProtosCoreErrors.signal(
                activation,
                ProtosCoreErrors.newError(activation));
    }

    /*
     * Selection of the standard Array/Bytes at, atPut and size.
     *
     * Protos is prototype-based, so a Java receiver type alone never proves
     * that the standard behavior is selected: an Array or Bytes receiver may
     * own, or inherit, an overriding at/atPut/size. This node answers whether
     * the behavior actually selected for {@code selector} on the receiver is
     * exactly the standard Array/Bytes behavior. It guards the standard
     * specializations of BinarySubscript, SetItem and GetLen, whose generic
     * path invokes any other selection. Nothing is invoked here.
     *
     * The cached path is the shared inherited structural selection used by
     * ReadMember: the receiver's Shape proves it owns no such slot, and the
     * selection guards every Shape along the delegation chain, so a later
     * override, removal or reassignment misses the cache. A chain whose
     * owners are all FROZEN caches its classification; a mutable chain reads
     * and classifies the selected slot on every execution, so a reassignment
     * that keeps every Shape is observed.
     */
    @GenerateUncached
    @GenerateInline(false)
    abstract static class StandardIndexedSelectionNode extends Node {

        abstract boolean execute(VirtualFrame frame, String selector, Object receiver);

        @Specialization(
                guards = {
                    "isStandardIndexedValue(receiver)",
                    "receiverShape.check(receiver)",
                    "selection != null",
                    "hasSelectionParent(receiver, selection)",
                    "frozen || selection.isValid()"
                },
                limit = "3",
                excludeForUncached = true)
        static boolean doCached(
                VirtualFrame frame,
                String selector,
                ProtosObjectValue receiver,
                @Cached("receiver.getShape()") Shape receiverShape,
                @Cached("selectInherited(receiver, receiverShape, selector)")
                        ProtosValueLookup.StructuralSelection selection,
                @Cached("allOwnersFrozen(selection)") boolean frozen,
                @Cached("frozenStandard(frozen, selector, receiver, selection)")
                        boolean frozenStandard) {
            if (frozen) {
                return frozenStandard;
            }
            return isStandardIndexedBehavior(selector, receiver, selection.read());
        }

        /*
         * The generic selection. It replaces no cached entry, so a site
         * that also sees an overriding or uncached receiver keeps its
         * standard entries eligible.
         */
        @Specialization(guards = "isStandardIndexedValue(receiver)")
        @InliningCutoff
        static boolean doGeneric(
                VirtualFrame frame,
                String selector,
                ProtosObjectValue receiver) {
            return selectsStandard(selector, receiver, frame.getArguments());
        }

        /* Receivers outside the Array/Bytes domain are never standard. */
        @Fallback
        static boolean doOther(VirtualFrame frame, String selector, Object receiver) {
            return false;
        }

        static boolean allOwnersFrozen(
                ProtosValueLookup.StructuralSelection selection) {
            return selection != null && selection.allOwnersFrozen();
        }

        /*
         * Specialization-time classification of a chain whose owners are
         * all FROZEN: while the selection stays valid its selected behavior
         * cannot change. A mutable chain is never classified here.
         */
        static boolean frozenStandard(
                boolean frozen,
                String selector,
                ProtosObjectValue receiver,
                ProtosValueLookup.StructuralSelection selection) {
            return frozen
                    && isStandardIndexedBehavior(
                            selector, receiver, selection.read());
        }

        static boolean isStandardIndexedValue(Object receiver) {
            return receiver instanceof ProtosArrayValue
                    || receiver instanceof ProtosBytesValue;
        }

        static boolean hasSelectionParent(
                ProtosObjectValue receiver,
                ProtosValueLookup.StructuralSelection selection) {
            return ProtosValueLookup.hasSelectionParent(receiver, selection);
        }

        /*
         * Specialization-time only: null when the receiver owns the slot.
         * The admitted receivers are the final Array and Bytes values, which
         * never replace their lexical-binding authority, so their local
         * slots always live in their own Shape.
         */
        @TruffleBoundary
        static ProtosValueLookup.StructuralSelection selectInherited(
                ProtosObjectValue receiver, Shape receiverShape, String selector) {
            if (ProtosValueLookup.hasProperty(receiverShape, selector)) {
                return null;
            }
            Object parent = receiver.parent().orElse(null);
            if (parent == null) {
                return null;
            }
            return ProtosValueLookup.selectStructural(parent, selector);
        }

        static boolean isStandardIndexedBehavior(
                String selector, Object receiver, Object behavior) {
            if (receiver instanceof ProtosArrayValue) {
                return ProtosStandardArrayProtocol.isStandardIndexedBehavior(
                        selector, behavior);
            }
            if (receiver instanceof ProtosBytesValue) {
                return ProtosStandardBytesProtocol.isStandardIndexedBehavior(
                        selector, behavior);
            }
            return false;
        }

        /*
         * The complete selection, observed without invoking anything. A
         * receiver outside the Array/Bytes domain, or a missing or
         * unsupported selection, is not standard: the selected invocation
         * then raises its own Error.
         */
        @TruffleBoundary
        static boolean selectsStandard(
                String selector, Object receiver, Object[] frameArguments) {
            if (!isStandardIndexedValue(receiver)) {
                return false;
            }
            try {
                return ProtosValueLookup.lookup(
                                receiver, selector,
                                ProtosFrameArguments.admittedPreludeOrNull(frameArguments))
                        .map(found -> isStandardIndexedBehavior(
                                selector, receiver, found.value()))
                        .orElse(false);
            } catch (UnsupportedOperationException unsupportedRepresentation) {
                return false;
            }
        }
    }

    /*
     * The generic path of BinarySubscript, SetItem and GetLen. When the
     * selected behavior is the standard one, the operands lie outside its
     * admitted lanes and the standard indexed Error is signaled. Otherwise
     * the complete send selection chooses the behavior and the shared call
     * dispatcher invokes it exactly as an ordinary send, over the operands
     * already evaluated once in Protos order: receiver, methodHome, caller,
     * Prelude, ReturnHome, guest Errors, non-local control transfers and
     * suspensions are those of that selection and dispatcher. A suspended
     * result is returned unchanged; the enclosing bytecode composes it.
     */
    @InliningCutoff
    private static Object invokeSelectedIndexed(
            VirtualFrame frame,
            String selector,
            Object receiver,
            Object[] callArguments,
            ProtosBytecodeRootNode root,
            ProtosFunctionCallDispatchers.CallNode call) {
        Object[] frameArguments = frame.getArguments();
        if (StandardIndexedSelectionNode.selectsStandard(selector, receiver, frameArguments)) {
            throw indexedErrorOf(frameArguments);
        }
        Object method = ProtosExperimentalGetMethodNode.select(
                ProtosNumericValueSupport.guestValue(receiver),
                selector,
                root,
                frameArguments);
        return call.execute(frame, false, method, callArguments, null, root);
    }

    /**
     * Whether the behavior selected for {@code selector} on the evaluated
     * receiver under the caller's Prelude is the standard numeric or String
     * operator. The classification and its structural caches are owned by
     * {@link ProtosNumericSelectionNode}.
     */
    @Operation(storeBytecodeIndex = false)
    @ConstantOperand(type = String.class, name = "selector")
    public static final class IsStandardNumericSelection {
        @Specialization
        public static boolean perform(
                String selector, Object receiver,
                @Bind VirtualFrame frame,
                @Cached ProtosNumericSelectionNode selection) {
            return selection.execute(frame, selector, receiver);
        }
    }

    /**
     * Exact truncating Integer quotient {@code div}. The primitive
     * specialization executes only while the standard {@code div} is the
     * behavior selected for the original receiver; the generic
     * specialization completes a standard selection with the existing
     * kernel and invokes any other selected implementation through
     * {@link ProtosNumericOverloadNode}. The result is stored in the
     * {@code outcome} local; the operation answers whether it must be
     * composed as a continuation.
     */
    @Operation(storeBytecodeIndex = true)
    @ConstantOperand(type = boolean.class, name = "terminal")
    @ConstantOperand(type = LocalAccessor.class, name = "outcome")
    @ConstantOperand(type = LocalAccessor.class, name = "commitCell")
    @ConstantOperand(type = boolean.class, name = "commitToCell")
    @ConstantOperand(type = SourceSection.class, name = "assignmentSection")
    public static final class NumberQuotient {
        static final String SELECTOR = "div";

        static boolean fitsLong(long left, long right) {
            return right != 0L && (left != Long.MIN_VALUE || right != -1L);
        }

        @Specialization(guards = {"fitsLong(left, right)",
                "selection.execute(frame, SELECTOR, left)"})
        public static boolean small(boolean terminal, LocalAccessor outcome,
                LocalAccessor commitCell, boolean commitToCell, SourceSection assignmentSection,
                long left, long right,
                @Bind VirtualFrame frame,
                @Shared("selection") @Cached ProtosNumericSelectionNode selection,
                @Bind BytecodeNode bytecode) {
            outcome.setLong(bytecode, frame, left / right);
            if (commitToCell) {
                StoreAndTestContinuation.commitCompletedCell(
                        commitCell, outcome, assignmentSection, frame, bytecode);
            }
            return false;
        }

        @Specialization
        @InliningCutoff
        public static boolean exact(boolean terminal, LocalAccessor outcome,
                LocalAccessor commitCell, boolean commitToCell, SourceSection assignmentSection,
                Object left, Object right,
                @Bind VirtualFrame frame,
                @Bind ProtosBytecodeRootNode root,
                @Bind BytecodeNode bytecode,
                @Bind("$bytecodeIndex") int bytecodeIndex,
                @Shared("selection") @Cached ProtosNumericSelectionNode selection,
                @Cached ProtosNumericOverloadNode overload) {
            if (selection.execute(frame, SELECTOR, left)) {
                return StoreAndTestContinuation.storeResultAndCommit(
                        outcome, commitCell, commitToCell, assignmentSection,
                        ProtosNumericOperationSupport.arithmetic(root, frame, left, SELECTOR, right),
                        frame, bytecode);
            }
            return StoreAndTestContinuation.storeResultAndCommit(
                    outcome, commitCell, commitToCell, assignmentSection,
                    overload.execute(frame, terminal, SELECTOR, left, right,
                    root, bytecode, bytecodeIndex), frame, bytecode);
        }
    }

    /**
     * Exact truncating Integer remainder {@code mod}, with the same
     * selection discipline as {@link NumberQuotient}.
     */
    @Operation(storeBytecodeIndex = true)
    @ConstantOperand(type = boolean.class, name = "terminal")
    @ConstantOperand(type = LocalAccessor.class, name = "outcome")
    @ConstantOperand(type = LocalAccessor.class, name = "commitCell")
    @ConstantOperand(type = boolean.class, name = "commitToCell")
    @ConstantOperand(type = SourceSection.class, name = "assignmentSection")
    public static final class NumberRemainder {
        static final String SELECTOR = "mod";

        @Specialization(guards = {"right != 0",
                "selection.execute(frame, SELECTOR, left)"})
        public static boolean small(boolean terminal, LocalAccessor outcome,
                LocalAccessor commitCell, boolean commitToCell, SourceSection assignmentSection,
                long left, long right,
                @Bind VirtualFrame frame,
                @Shared("selection") @Cached ProtosNumericSelectionNode selection,
                @Bind BytecodeNode bytecode) {
            outcome.setLong(bytecode, frame, left % right);
            if (commitToCell) {
                StoreAndTestContinuation.commitCompletedCell(
                        commitCell, outcome, assignmentSection, frame, bytecode);
            }
            return false;
        }

        @Specialization
        @InliningCutoff
        public static boolean exact(boolean terminal, LocalAccessor outcome,
                LocalAccessor commitCell, boolean commitToCell, SourceSection assignmentSection,
                Object left, Object right,
                @Bind VirtualFrame frame,
                @Bind ProtosBytecodeRootNode root,
                @Bind BytecodeNode bytecode,
                @Bind("$bytecodeIndex") int bytecodeIndex,
                @Shared("selection") @Cached ProtosNumericSelectionNode selection,
                @Cached ProtosNumericOverloadNode overload) {
            if (selection.execute(frame, SELECTOR, left)) {
                return StoreAndTestContinuation.storeResultAndCommit(
                        outcome, commitCell, commitToCell, assignmentSection,
                        ProtosNumericOperationSupport.arithmetic(root, frame, left, SELECTOR, right),
                        frame, bytecode);
            }
            return StoreAndTestContinuation.storeResultAndCommit(
                    outcome, commitCell, commitToCell, assignmentSection,
                    overload.execute(frame, terminal, SELECTOR, left, right,
                    root, bytecode, bytecodeIndex), frame, bytecode);
        }
    }

    /**
     * The single association snapshot of a standard Map, IdentityMap or
     * Environment {@code each}, taken when the iteration starts, exactly as
     * the standard behavior takes it, and stored in the constant
     * {@code snapshot} local.
     *
     * <p>The snapshot is taken only when {@code each} on the receiver
     * selects that receiver's standard behavior at its canonical home and
     * the root {@code call} selection is canonical; the operation then
     * answers true. Any other receiver or selection stores nothing and
     * answers false, and the lowering keeps ordinary selection. Nothing is
     * invoked here.
     */
    @Operation(storeBytecodeIndex = true)
    @ConstantOperand(type = LocalAccessor.class, name = "snapshot")
    public static final class GetAssociationEachSnapshot {
        @Specialization
        public static boolean perform(
                LocalAccessor snapshot,
                Object receiver,
                @Bind VirtualFrame frame,
                @Bind BytecodeNode bytecode) {
            Object[] arguments = frame.getArguments();
            if (!selectsStandard(receiver, ProtosFrameArguments.admittedPreludeOrNull(arguments))) {
                return false;
            }
            snapshot.setObject(bytecode, frame, snapshot(receiver, arguments));
            return true;
        }

        @TruffleBoundary
        static boolean selectsStandard(Object receiver, ProtosPrelude prelude) {
            if (!(receiver instanceof com.guillermomolina.protos.runtime.ProtosMapValue
                            || receiver instanceof com.guillermomolina.protos.runtime.ProtosIdentityMapValue
                            || receiver instanceof com.guillermomolina.protos.runtime.ProtosEnvironmentValue)
                    || ProtosStandardObjectProtocol.canonicalRootCallSelectionOrNullForRuntime() == null) {
                return false;
            }
            try {
                return ProtosValueLookup.lookup(receiver, "each", prelude)
                        .map(found -> found.value() instanceof ProtosClosureValue closure
                                && isStandardEachOf(receiver, closure, found.home(), prelude))
                        .orElse(false);
            } catch (UnsupportedOperationException unsupportedRepresentation) {
                return false;
            }
        }

        /* The standard behavior at its canonical home; a copy homed elsewhere is not. */
        private static boolean isStandardEachOf(
                Object receiver, ProtosClosureValue closure, ProtosObjectValue home, ProtosPrelude prelude) {
            if (receiver instanceof com.guillermomolina.protos.runtime.ProtosMapValue) {
                return ProtosStandardMapProtocol.isStandardEachImplementation(closure)
                        && prelude != null
                        && prelude.bindings().readLocalSlot("Map").orElse(null) == home;
            }
            if (receiver instanceof com.guillermomolina.protos.runtime.ProtosIdentityMapValue) {
                return ProtosStandardIdentityMapProtocol.isStandardEachImplementation(closure)
                        && prelude != null
                        && prelude.identityMapPrototype() == home;
            }
            return ProtosStandardEnvironmentProtocol.isStandardEachImplementation(closure)
                    && ProtosStandardEnvironmentProtocol.isCanonicalStandardEachHome(receiver, home);
        }

        @TruffleBoundary
        static AssociationEachSnapshot snapshot(Object receiver, Object[] arguments) {
            java.util.List<Object> keys = new java.util.ArrayList<>();
            java.util.List<Object> values = new java.util.ArrayList<>();
            if (receiver instanceof com.guillermomolina.protos.runtime.ProtosEnvironmentValue environment) {
                for (com.guillermomolina.protos.runtime.ProtosEnvironmentValue.PortableEntry entry
                        : ProtosStandardEnvironmentProtocol.portableEntriesForStructured(
                                environment, ProtosFrameArguments.activation(arguments))) {
                    keys.add(entry.name());
                    values.add(entry.value());
                }
            } else {
                java.util.List<java.util.Map.Entry<Object, Object>> entries =
                        receiver instanceof com.guillermomolina.protos.runtime.ProtosMapValue map
                                ? map.associationSnapshot()
                                : ((com.guillermomolina.protos.runtime.ProtosIdentityMapValue) receiver)
                                        .associationSnapshot();
                for (java.util.Map.Entry<Object, Object> entry : entries) {
                    keys.add(entry.getKey());
                    values.add(entry.getValue());
                }
            }
            return new AssociationEachSnapshot(keys.toArray(), values.toArray());
        }
    }
    /**
     * One step of a native standard association {@code each}: stores the next
     * snapshot key and value, in snapshot order, directly into the locals of
     * the inline callback's two parameters and answers whether an association
     * was stored.
     */
    @Operation(storeBytecodeIndex = false)
    @ConstantOperand(type = LocalAccessor.class, name = "key")
    @ConstantOperand(type = LocalAccessor.class, name = "value")
    public static final class AssociationEachNext {
        @Specialization
        public static boolean perform(
                LocalAccessor key,
                LocalAccessor value,
                AssociationEachSnapshot snapshot,
                @Bind VirtualFrame frame,
                @Bind BytecodeNode bytecode) {
            if (!snapshot.hasNext()) {
                return false;
            }
            key.setObject(bytecode, frame, snapshot.key());
            value.setObject(bytecode, frame, snapshot.value());
            snapshot.advance();
            return true;
        }
    }

    /**
     * The iteration state of one native standard association {@code each}.
     * Held only in a Bytecode DSL local of the iterating root, so it survives
     * that root's suspensions and is never a guest value.
     */
    public static final class AssociationEachSnapshot {
        private final Object[] keys;
        private final Object[] values;
        private int index;

        AssociationEachSnapshot(Object[] keys, Object[] values) {
            this.keys = keys;
            this.values = values;
        }

        boolean hasNext() {
            return index < keys.length;
        }

        Object key() {
            return keys[index];
        }

        Object value() {
            return values[index];
        }

        void advance() {
            index++;
        }
    }

    /**
     * The iteration state of one native standard {@code each}: the single
     * shallow snapshot established at its start and the position of the
     * next element to visit. The state belongs to the physical activation and
     * survives its suspensions; it is held in a Bytecode DSL local read by
     * the iteration operation. It is never a guest value.
     */
    public static final class ArrayEachSnapshot {
        /* One published indexed generation: known length, indexed reads, no copy. */
        private final com.guillermomolina.protos.runtime.ProtosIndexedSnapshot elements;
        private int next;

        ArrayEachSnapshot(com.guillermomolina.protos.runtime.ProtosIndexedSnapshot elements) {
            this.elements = elements;
        }

        @NonIdempotent
        boolean nextIsLong() {
            return next < elements.length() && elements.at(next) instanceof Long;
        }

        boolean hasNext() {
            return next < elements.length();
        }

        Object advance() {
            return elements.at(next++);
        }
    }

    /**
     * The single shallow snapshot of a standard Array or Bytes {@code each}
     * receiver, stored in the constant {@code snapshot} local.
     *
     * <p>The operation specializes on the behavior actually selected for
     * {@code each}: it stores the snapshot and answers true only when that
     * selection is exactly the standard Array (or Bytes) behavior and the
     * root Object's inherited {@code call} selection is the canonical
     * {@code Object.call}, so a fresh Closure literal argument would pass the
     * standard callability validation and its ordinary invocation would reach
     * its own body. Any other receiver or selection stores nothing and
     * answers false; the lowering then selects {@code each} with GetMethod
     * and dispatches it. Nothing is invoked here.
     *
     * <p>The cached path uses the same inherited structural selection as
     * {@link StandardIndexedSelectionNode}: a later override, removal or
     * reassignment of {@code each} along a mutable delegation chain, or of
     * the root {@code call}, misses the cache. A chain whose owners are all
     * FROZEN is classified once, when the entry is installed.
     */
    @Operation(storeBytecodeIndex = false)
    @ConstantOperand(type = LocalAccessor.class, name = "snapshot")
    public static final class GetArrayEachSnapshot {
        @Specialization(
                guards = {
                    "receiver.usesObjectStorageForRuntime()",
                    "receiverShape.check(receiver)",
                    "selection != null",
                    "hasSelectionParent(receiver, selection)",
                    "frozen || selection.isValid()",
                    "callSelection != null",
                    "callSelection.isValid()"
                },
                limit = "3",
                excludeForUncached = true)
        public static boolean doCached(
                LocalAccessor snapshot,
                ProtosArrayValue receiver,
                @Bind VirtualFrame frame,
                @Bind BytecodeNode bytecode,
                @Cached("receiver.getShape()") Shape receiverShape,
                @Cached("selectInherited(receiver, receiverShape)")
                        ProtosValueLookup.StructuralSelection selection,
                @Cached("allOwnersFrozen(selection)") boolean frozen,
                @Cached("frozenStandardEach(frozen, selection)") boolean frozenStandard,
                @Cached("rootCallSelection()")
                        ProtosStandardObjectProtocol.CanonicalRootCallSelection callSelection) {
            if (frozen
                    ? !frozenStandard
                    : !ProtosStandardArrayProtocol.isStandardEachBehavior(selection.read())) {
                return false;
            }
            snapshot.setObject(bytecode, frame, snapshot(receiver));
            return true;
        }

        /*
         * The generic selection. It replaces no cached entry, so a site that
         * also sees an overriding or uncached receiver keeps its standard
         * entries.
         */
        @Specialization
        @InliningCutoff
        public static boolean doGeneric(
                LocalAccessor snapshot,
                ProtosArrayValue receiver,
                @Bind VirtualFrame frame,
                @Bind BytecodeNode bytecode) {
            if (!selectsStandard(
                    receiver,
                    ProtosFrameArguments.admittedPreludeOrNull(frame.getArguments()))) {
                return false;
            }
            snapshot.setObject(bytecode, frame, snapshot(receiver));
            return true;
        }

        /* Bytes iterate their octets exactly as an Array iterates its elements. */
        @Specialization
        public static boolean doBytes(
                LocalAccessor snapshot,
                com.guillermomolina.protos.runtime.ProtosBytesValue receiver,
                @Bind VirtualFrame frame,
                @Bind BytecodeNode bytecode) {
            if (!selectsStandardBytes(
                    receiver,
                    ProtosFrameArguments.admittedPreludeOrNull(frame.getArguments()))) {
                return false;
            }
            snapshot.setObject(bytecode, frame, bytesSnapshot(receiver));
            return true;
        }

        @Fallback
        public static boolean doOther(LocalAccessor snapshot, Object receiver) {
            return false;
        }

        static ArrayEachSnapshot snapshot(ProtosArrayValue receiver) {
            return new ArrayEachSnapshot(receiver.indexedGenerationSnapshot());
        }

        @TruffleBoundary
        static ArrayEachSnapshot bytesSnapshot(com.guillermomolina.protos.runtime.ProtosBytesValue receiver) {
            List<Object> octets = receiver.indexedSnapshot();
            return new ArrayEachSnapshot(
                    com.guillermomolina.protos.runtime.ProtosIndexedSnapshot.elements(
                            octets.toArray(), octets.size()));
        }

        static boolean hasSelectionParent(
                ProtosObjectValue receiver,
                ProtosValueLookup.StructuralSelection selection) {
            return ProtosValueLookup.hasSelectionParent(receiver, selection);
        }

        static ProtosStandardObjectProtocol.CanonicalRootCallSelection rootCallSelection() {
            return ProtosStandardObjectProtocol.canonicalRootCallSelectionOrNullForRuntime();
        }

        static boolean allOwnersFrozen(ProtosValueLookup.StructuralSelection selection) {
            return selection != null && selection.allOwnersFrozen();
        }

        /* Specialization time only: the classification of an all-FROZEN chain. */
        static boolean frozenStandardEach(
                boolean frozen, ProtosValueLookup.StructuralSelection selection) {
            return frozen && ProtosStandardArrayProtocol.isStandardEachBehavior(selection.read());
        }

        /* Specialization-time only: null when the receiver owns the slot. */
        @TruffleBoundary
        static ProtosValueLookup.StructuralSelection selectInherited(
                ProtosObjectValue receiver, Shape receiverShape) {
            if (!receiver.usesObjectStorageForRuntime()
                    || ProtosValueLookup.hasProperty(receiverShape, "each")) {
                return null;
            }
            Object parent = receiver.parent().orElse(null);
            if (parent == null) {
                return null;
            }
            return ProtosValueLookup.selectStructural(parent, "each");
        }

        @TruffleBoundary
        static boolean selectsStandardBytes(
                com.guillermomolina.protos.runtime.ProtosBytesValue receiver, ProtosPrelude prelude) {
            if (prelude == null
                    || ProtosStandardObjectProtocol.canonicalRootCallSelectionOrNullForRuntime() == null) {
                return false;
            }
            try {
                ProtosObjectValue canonicalHome = prelude.bytesPrototypeForRuntime();
                return ProtosValueLookup.lookup(receiver, "each", prelude)
                        .map(found -> found.value() instanceof ProtosClosureValue closure
                                && ProtosStandardBytesProtocol.isStandardEachImplementation(closure)
                                && found.home() == canonicalHome)
                        .orElse(false);
            } catch (IllegalStateException | UnsupportedOperationException unavailable) {
                return false;
            }
        }

        /*
         * The complete selections, observed without invoking anything. A
         * missing or unsupported selection is not standard: the dispatched
         * GetMethod then raises its own Error.
         */
        @TruffleBoundary
        static boolean selectsStandard(ProtosArrayValue receiver, ProtosPrelude prelude) {
            if (ProtosStandardObjectProtocol.canonicalRootCallSelectionOrNullForRuntime() == null) {
                return false;
            }
            try {
                return ProtosValueLookup.lookup(receiver, "each", prelude)
                        .map(found -> ProtosStandardArrayProtocol
                                .isStandardEachBehavior(found.value()))
                        .orElse(false);
            } catch (UnsupportedOperationException unsupportedRepresentation) {
                return false;
            }
        }
    }
    /**
     * Advances an admitted Array snapshot or Range cursor before executing the
     * callback, writing its parameter through a {@link LocalAccessor}. Array
     * and Range long specializations use primitive {@code long} local storage;
     * the object specializations preserve other Array values and exact Range
     * Integers, including values beyond signed-64 bounds. The boolean result
     * controls the enclosing While: false means exhausted, true means one
     * element was stored. Suspension resumes the current callback without
     * advancing the snapshot or cursor twice.
     */
    @Operation(storeBytecodeIndex = false)
    @ConstantOperand(type = LocalAccessor.class, name = "element")
    @ConstantOperand(type = LocalAccessor.class, name = "cursor")
    public static final class IterationNext {
        /*
         * The cursor is read inside the operation from its constant local, so
         * no separate cursor load is executed per advance, and each iteration
         * family stays an independently profiled specialization.
         */
        /*
         * A primitive cursor whose step is 1 or -1 never overflows: the
         * advance runs only after next < stop (ascending) or next > stop
         * (descending) held, so next + step is at most stop or at least
         * stop and stays inside the signed long domain.
         */
        @Specialization(guards = "isRangeUnitStep(iteration)")
        public static boolean doRangeUnitStep(
                LocalAccessor element,
                LocalAccessor cursor,
                @Bind VirtualFrame frame,
                @Bind BytecodeNode bytecode,
                @Bind("cursor.getObject(bytecode, frame)") Object iteration) {
            RangeIterationState state = (RangeIterationState) iteration;
            if (!state.hasNextUnitStep()) {
                return false;
            }
            element.setLong(bytecode, frame, state.advanceUnitStep());
            return true;
        }

        @Specialization(guards = "isRangeLong(iteration)")
        public static boolean doRangeLong(
                LocalAccessor element,
                LocalAccessor cursor,
                @Bind VirtualFrame frame,
                @Bind BytecodeNode bytecode,
                @Bind("cursor.getObject(bytecode, frame)") Object iteration) {
            RangeIterationState state = (RangeIterationState) iteration;
            if (!state.hasNext()) {
                return false;
            }
            element.setLong(bytecode, frame, state.advance());
            return true;
        }

        @Specialization(guards = "isRangeExact(iteration)")
        public static boolean doRangeExact(
                LocalAccessor element,
                LocalAccessor cursor,
                @Bind VirtualFrame frame,
                @Bind BytecodeNode bytecode,
                @Bind("cursor.getObject(bytecode, frame)") Object iteration) {
            Object current = ((RangeIterationState) iteration).advanceExactOrNull();
            if (current == null) {
                return false;
            }
            element.setObject(bytecode, frame, current);
            return true;
        }

        @Specialization(guards = "isArrayLong(iteration)")
        public static boolean doArrayLong(
                LocalAccessor element,
                LocalAccessor cursor,
                @Bind VirtualFrame frame,
                @Bind BytecodeNode bytecode,
                @Bind("cursor.getObject(bytecode, frame)") Object iteration) {
            element.setLong(bytecode, frame, (Long) ((ArrayEachSnapshot) iteration).advance());
            return true;
        }

        @Specialization(guards = "isArrayObject(iteration)")
        public static boolean doArrayObject(
                LocalAccessor element,
                LocalAccessor cursor,
                @Bind VirtualFrame frame,
                @Bind BytecodeNode bytecode,
                @Bind("cursor.getObject(bytecode, frame)") Object iteration) {
            ArrayEachSnapshot snapshot = (ArrayEachSnapshot) iteration;
            if (!snapshot.hasNext()) {
                return false;
            }
            element.setObject(bytecode, frame, snapshot.advance());
            return true;
        }

        static boolean isRangeUnitStep(Object iteration) {
            return iteration instanceof RangeIterationState state && !state.isExact() && state.isUnitStep();
        }

        static boolean isRangeLong(Object iteration) {
            return iteration instanceof RangeIterationState state && !state.isExact() && !state.isUnitStep();
        }

        static boolean isRangeExact(Object iteration) {
            return iteration instanceof RangeIterationState state && state.isExact();
        }

        static boolean isArrayLong(Object iteration) {
            return iteration instanceof ArrayEachSnapshot snapshot && snapshot.nextIsLong();
        }

        static boolean isArrayObject(Object iteration) {
            return iteration instanceof ArrayEachSnapshot snapshot && !snapshot.nextIsLong();
        }
    }

    /**
     * The internal iteration of a standard {@code Range} or
     * {@code InvertedRange} {@code each} ({@code stepped} false) or
     * {@code eachStep} ({@code stepped} true) send whose receiver and
     * operands are already evaluated: stores the cursor in the constant
     * {@code cursor} local and answers true, or stores nothing and answers
     * false.
     *
     * <p>The cursor is established only when the receiver is a FROZEN direct
     * child of the exact {@code Range} or {@code InvertedRange} binding of
     * the admitted Prelude, that FROZEN prototype owns the selected
     * {@code each} and {@code eachStep} (so the {@code this.eachStep}
     * delegation of {@code each} selects the same canonical source-backed
     * behavior), the receiver owns neither selector, its bounds are
     * Integers, an explicit step is an Integer of the direction of the
     * prototype, the {@code Integer.recognizes} selection is FROZEN, the
     * SmallInteger {@code <}, {@code >} and {@code +} selections are the
     * standard ones and the root {@code call} selection is canonical. When a
     * bound or the step is a large Integer, or the cursor may leave the
     * signed-64 range, the {@code <}, {@code >} and {@code +} selections of
     * the large Integer family must also be FROZEN and standard, because the
     * canonical loop then compares and advances large receivers. Every other
     * receiver, representation or step answers false, and the lowering
     * invokes the ordinarily selected behavior with the same already
     * evaluated operands, which signals any step Error in its source order.
     * Nothing is invoked here.
     *
     * <p>The cursor starts at the start bound, ends before the exclusive stop
     * bound and advances by the step: for {@code each}, {@code +1} when the
     * selected canonical prototype is {@code Range} and {@code -1} when it is
     * {@code InvertedRange}, exactly as the inherited source methods delegate
     * to {@code eachStep}; for {@code eachStep}, the explicit step. The order
     * of the bounds never chooses the direction; it only decides whether the
     * traversal is empty. Signed-64 operands establish the primitive cursor;
     * any large bound or step establishes the exact cursor, which keeps the
     * published Integer representations of every visited value.
     */
    @Operation(storeBytecodeIndex = false)
    @ConstantOperand(type = boolean.class, name = "stepped")
    @ConstantOperand(type = LocalAccessor.class, name = "cursor")
    public static final class PrepareRangeIteration {
        static final String START = "start";
        static final String STOP = "stop";

        static final String LESS = "<";
        static final String GREATER = ">";
        static final String PLUS = "+";

        /*
         * The cached iteration of a FROZEN receiver Shape that owns neither
         * selector, whose immutable parent is the exact canonical prototype
         * of the cached Prelude. That prototype owns the canonical each and
         * eachStep, the Integer.recognizes selection is FROZEN and, for
         * frozenLongIterationOperators, the signed-64 operator chains are
         * FROZEN and standard, all proven when the entry is installed; the
         * root call selection is an assumption. Signed-64 operands whose
         * cursor stays in the signed-64 range therefore initialize the
         * primitive cursor directly from the bounds read through the
         * receiver Shape. Any other operand takes the complete selection.
         *
         * The captured entry derives the same facts from the Prelude read
         * once, from the final field of the compact invocation's own
         * Closure, when it is installed; it binds null once the invocation
         * published its activation, and never applies when the Closure
         * captured no Prelude. The general entry below serves every other
         * invocation and keeps caller inheritance.
         */
        @Specialization(
                guards = {
                    "closure != null",
                    "closure == cachedClosure",
                    "cachedPrelude != null",
                    "receiverShape.check(receiver)",
                    "eachSelection != null",
                    "hasSelectionParent(receiver, eachSelection)",
                    "callSelection != null",
                    "callSelection.isValid()",
                    "frozenLongIterationOperators"
                },
                limit = "3",
                excludeForUncached = true)
        public static boolean doCaptured(
                boolean stepped,
                LocalAccessor cursor,
                ProtosObjectValue receiver,
                Object step,
                @Bind VirtualFrame frame,
                @Bind BytecodeNode bytecode,
                @Bind("capturedClosure(frame)") ProtosClosureValue closure,
                @Cached("closure") ProtosClosureValue cachedClosure,
                @Cached("cachedClosure.preludeOrNullForRuntime()") ProtosPrelude cachedPrelude,
                @Cached("receiver.getShape()") Shape receiverShape,
                @Cached("selectCanonicalEach(receiver, receiverShape, cachedPrelude)")
                        ProtosValueLookup.StructuralSelection eachSelection,
                @Cached("isAscending(eachSelection, cachedPrelude)") boolean ascending,
                @Cached("boundGetter(receiverShape, START)") PropertyGetter startGetter,
                @Cached("boundGetter(receiverShape, STOP)") PropertyGetter stopGetter,
                @Cached.Exclusive @Cached("rootCallSelection()")
                        ProtosStandardObjectProtocol.CanonicalRootCallSelection callSelection,
                @Cached("frozenStandardLongOperators(cachedPrelude, LESS, GREATER, PLUS)")
                        boolean frozenLongIterationOperators) {
            return prepareCanonical(stepped, cursor, receiver, step, ascending,
                    startGetter, stopGetter, cachedPrelude, frame, bytecode);
        }

        @Specialization(
                guards = {
                    "prelude == cachedPrelude",
                    "receiverShape.check(receiver)",
                    "eachSelection != null",
                    "hasSelectionParent(receiver, eachSelection)",
                    "callSelection != null",
                    "callSelection.isValid()",
                    "frozenLongIterationOperators"
                },
                limit = "3",
                excludeForUncached = true)
        public static boolean doCached(
                boolean stepped,
                LocalAccessor cursor,
                ProtosObjectValue receiver,
                Object step,
                @Bind VirtualFrame frame,
                @Bind BytecodeNode bytecode,
                @Bind("admittedPrelude(frame)") ProtosPrelude prelude,
                @Cached("prelude") ProtosPrelude cachedPrelude,
                @Cached("receiver.getShape()") Shape receiverShape,
                @Cached("selectCanonicalEach(receiver, receiverShape, cachedPrelude)")
                        ProtosValueLookup.StructuralSelection eachSelection,
                @Cached("isAscending(eachSelection, cachedPrelude)") boolean ascending,
                @Cached("boundGetter(receiverShape, START)") PropertyGetter startGetter,
                @Cached("boundGetter(receiverShape, STOP)") PropertyGetter stopGetter,
                @Cached.Exclusive @Cached("rootCallSelection()")
                        ProtosStandardObjectProtocol.CanonicalRootCallSelection callSelection,
                @Cached("frozenStandardLongOperators(cachedPrelude, LESS, GREATER, PLUS)")
                        boolean frozenLongIterationOperators) {
            return prepareCanonical(stepped, cursor, receiver, step, ascending,
                    startGetter, stopGetter, cachedPrelude, frame, bytecode);
        }

        /*
         * The cursor of a receiver whose canonical iteration and signed-64
         * operators were proven when its entry was installed: signed-64
         * bounds read through the cached getters initialize the primitive
         * cursor directly.
         */
        private static boolean prepareCanonical(
                boolean stepped,
                LocalAccessor cursor,
                ProtosObjectValue receiver,
                Object step,
                boolean ascending,
                PropertyGetter startGetter,
                PropertyGetter stopGetter,
                ProtosPrelude cachedPrelude,
                VirtualFrame frame,
                BytecodeNode bytecode) {
            Object start = startGetter.get(receiver);
            Object stop = stopGetter.get(receiver);
            if (start instanceof Long first
                    && stop instanceof Long last
                    && (!stepped || step instanceof Long)) {
                long delta = stepped ? (Long) step : (ascending ? 1L : -1L);
                if (ascending ? delta <= 0 : delta >= 0) {
                    return false;
                }
                if (staysSigned64(last, delta)) {
                    cursor.setObject(bytecode, frame, new RangeIterationState(first, last, delta));
                    return true;
                }
            }
            // A large bound or step, or a cursor that may leave the signed-64
            // range: the signed-64 operators are already proven; only the
            // large operand selections remain, behind the exact boundary.
            return prepareExact(stepped, cursor, receiver, step, cachedPrelude, frame, bytecode);
        }

        /*
         * Any other receiver or Prelude. It replaces no cached entry, so a
         * site that also sees a custom receiver keeps its standard entries.
         */
        @Specialization
        @InliningCutoff
        public static boolean doObject(
                boolean stepped,
                LocalAccessor cursor,
                ProtosObjectValue receiver,
                Object step,
                @Bind VirtualFrame frame,
                @Bind BytecodeNode bytecode,
                @Cached ProtosNumericSelectionNode less,
                @Cached ProtosNumericSelectionNode greater,
                @Cached ProtosNumericSelectionNode plus) {
            return prepareSelected(
                    stepped, cursor, receiver, step, admittedPrelude(frame),
                    frame, bytecode, less, greater, plus);
        }

        @Fallback
        public static boolean doOther(
                boolean stepped, LocalAccessor cursor, Object receiver, Object step) {
            return false;
        }

        /*
         * The cold exact path of a cached canonical receiver: the complete
         * selection decides the step and any large operand selections, whose
         * signed-64 operators the cached entry already proved standard.
         */
        private static boolean prepareExact(
                boolean stepped,
                LocalAccessor cursor,
                ProtosObjectValue receiver,
                Object step,
                ProtosPrelude prelude,
                VirtualFrame frame,
                BytecodeNode bytecode) {
            if (!selectsStandard(stepped, receiver, step, prelude)) {
                return false;
            }
            cursor.setObject(bytecode, frame, prepare(stepped, receiver, step, prelude));
            return true;
        }

        /*
         * The complete selection over the evaluated operands, then the general
         * or exact cursor. Arbitrary precision stays behind the boundaries of
         * selectsStandard and prepare.
         */
        private static boolean prepareSelected(
                boolean stepped,
                LocalAccessor cursor,
                ProtosObjectValue receiver,
                Object step,
                ProtosPrelude prelude,
                VirtualFrame frame,
                BytecodeNode bytecode,
                ProtosNumericSelectionNode less,
                ProtosNumericSelectionNode greater,
                ProtosNumericSelectionNode plus) {
            if (!selectsStandard(stepped, receiver, step, prelude)
                    || !less.execute(frame, LESS, 0L)
                    || !greater.execute(frame, GREATER, 0L)
                    || !plus.execute(frame, PLUS, 0L)) {
                return false;
            }
            cursor.setObject(bytecode, frame, prepare(stepped, receiver, step, prelude));
            return true;
        }

        /*
         * Whether a signed-64 cursor advancing by {@code delta} toward the
         * exclusive {@code stop} stays in the signed-64 range for every value
         * the canonical loop compares.
         */
        static boolean staysSigned64(long stop, long delta) {
            return delta > 0
                    ? stop <= Long.MAX_VALUE - delta + 1
                    : stop >= Long.MIN_VALUE - delta - 1;
        }

        @NonIdempotent
        static ProtosPrelude admittedPrelude(VirtualFrame frame) {
            return ProtosFrameArguments.admittedPreludeOrNull(frame.getArguments());
        }

        /*
         * The compact invocation's own Closure, or null once it published its
         * activation.
         */
        @NonIdempotent
        static ProtosClosureValue capturedClosure(VirtualFrame frame) {
            return ProtosNumericSelectionNode.capturedClosure(frame);
        }

        /* Specialization time only: the FROZEN standard signed-64 operator proof. */
        static boolean frozenStandardLongOperators(
                ProtosPrelude prelude, String less, String greater, String plus) {
            return ProtosNumericSelectionNode.frozenStandardLongOperators(prelude, less, greater, plus);
        }

        static boolean hasSelectionParent(
                ProtosObjectValue receiver,
                ProtosValueLookup.StructuralSelection selection) {
            return ProtosValueLookup.hasSelectionParent(receiver, selection);
        }

        static ProtosStandardObjectProtocol.CanonicalRootCallSelection rootCallSelection() {
            return ProtosStandardObjectProtocol.canonicalRootCallSelectionOrNullForRuntime();
        }

        static PropertyGetter boundGetter(Shape receiverShape, String name) {
            return ProtosValueLookup.propertyGetterWithFinalAssumption(receiverShape, name);
        }

        /*
         * Specialization-time only: the FROZEN structural selection of each
         * starting at the receiver's parent, or null. Non-null only when the
         * receiver Shape is FROZEN, describes both bounds and owns neither
         * selector, the parent is the exact Range or InvertedRange binding of
         * the Prelude, that FROZEN prototype owns the canonical each and
         * eachStep, and the Integer.recognizes selection is FROZEN. A FROZEN
         * Shape and FROZEN owners cannot change, so these facts hold for every
         * receiver passing the Shape and parent guards.
         */
        @TruffleBoundary
        static ProtosValueLookup.StructuralSelection selectCanonicalEach(
                ProtosObjectValue receiver, Shape receiverShape, ProtosPrelude prelude) {
            if (prelude == null
                    || !ProtosValueLookup.isOrdinaryObject(receiver)
                    || !receiver.usesObjectStorageForRuntime()
                    || !ProtosObjectValue.isFrozenShape(receiverShape)
                    || !ProtosValueLookup.hasProperty(receiverShape, "start")
                    || !ProtosValueLookup.hasProperty(receiverShape, "stop")
                    || ProtosValueLookup.hasProperty(receiverShape, "each")
                    || ProtosValueLookup.hasProperty(receiverShape, "eachStep")) {
                return null;
            }
            try {
                Object parent = receiver.parent().orElse(null);
                Object range = prelude.bindings().readLocalSlot("Range").orElse(null);
                Object inverted = prelude.bindings().readLocalSlot("InvertedRange").orElse(null);
                if (parent == null || (parent != range && parent != inverted)) {
                    return null;
                }
                ProtosObjectValue prototype = (ProtosObjectValue) parent;
                if (!selectsCanonicalMethod(receiver, "each", prototype)
                        || !selectsCanonicalMethod(receiver, "eachStep", prototype)) {
                    return null;
                }
                ProtosValueLookup.StructuralSelection recognizes =
                        ProtosValueLookup.selectStructural(prelude.integerPrototype(), "recognizes");
                if (recognizes == null || !recognizes.allOwnersFrozen()) {
                    return null;
                }
                ProtosValueLookup.StructuralSelection each =
                        ProtosValueLookup.selectStructural(prototype, "each");
                return each != null && each.allOwnersFrozen() ? each : null;
            } catch (IllegalStateException | UnsupportedOperationException unavailable) {
                return null;
            }
        }

        /* Specialization-time only: whether the selected prototype is Range. */
        @TruffleBoundary
        static boolean isAscending(
                ProtosValueLookup.StructuralSelection eachSelection, ProtosPrelude prelude) {
            return eachSelection != null
                    && prelude != null
                    && eachSelection.origin()
                            == prelude.bindings().readLocalSlot("Range").orElse(null);
        }

        @TruffleBoundary
        static boolean selectsStandard(
                boolean stepped, ProtosObjectValue receiver, Object step, ProtosPrelude prelude) {
            if (prelude == null
                    || !ProtosValueLookup.isOrdinaryObject(receiver)
                    || !receiver.isFrozen()
                    || ProtosStandardObjectProtocol.canonicalRootCallSelectionOrNullForRuntime() == null) {
                return false;
            }
            try {
                Object parent = receiver.parent().orElse(null);
                Object range = prelude.bindings().readLocalSlot("Range").orElse(null);
                Object inverted = prelude.bindings().readLocalSlot("InvertedRange").orElse(null);
                boolean ascending;
                if (parent != null && parent == range) {
                    ascending = true;
                } else if (parent != null && parent == inverted) {
                    ascending = false;
                } else {
                    return false;
                }
                ProtosObjectValue prototype = (ProtosObjectValue) parent;
                if (!selectsCanonicalMethod(receiver, "each", prototype)
                        || !selectsCanonicalMethod(receiver, "eachStep", prototype)) {
                    return false;
                }
                Object start = receiver.readLocalSlot("start").orElse(null);
                Object stop = receiver.readLocalSlot("stop").orElse(null);
                if (!ProtosNumericValueSupport.isCurrentInteger(start)
                        || !ProtosNumericValueSupport.isCurrentInteger(stop)) {
                    return false;
                }
                Object increment = ascending ? 1L : -1L;
                if (stepped) {
                    if (!ProtosNumericValueSupport.isCurrentInteger(step)) {
                        return false;
                    }
                    int sign = ProtosNumericValueSupport.integerSignum(step);
                    if (ascending ? sign <= 0 : sign >= 0) {
                        return false;
                    }
                    increment = step;
                }
                ProtosValueLookup.StructuralSelection recognizes =
                        ProtosValueLookup.selectStructural(prelude.integerPrototype(), "recognizes");
                if (recognizes == null || !recognizes.allOwnersFrozen()) {
                    return false;
                }
                Object large = largeOperandOrNull(start, stop, increment, prelude);
                return large == null || selectsStandardLargeOperators(large, prelude);
            } catch (IllegalStateException | UnsupportedOperationException unavailable) {
                return false;
            }
        }

        /*
         * A large Integer whose {@code <}, {@code >} and {@code +} selections
         * the canonical loop observes, or null when every compared and added
         * value stays signed-64: a large bound or step itself, or a
         * representative large Integer when the signed-64 cursor may advance
         * past the signed-64 range before the exclusive stop ends the loop
         * (the canonical source then compares that large cursor). Every
         * large Integer delegates to the same Prelude prototype, so one
         * representative proves the selection for all of them.
         */
        private static Object largeOperandOrNull(
                Object start, Object stop, Object increment, ProtosPrelude prelude) {
            for (Object operand : new Object[] {start, stop, increment}) {
                if (ProtosNumericValueSupport.isLargeInteger(operand)) {
                    return operand;
                }
            }
            if (staysSigned64((Long) stop, (Long) increment)) {
                return null;
            }
            return ProtosNumericValueSupport.addIntegers(Long.MAX_VALUE, 1L, prelude);
        }

        /*
         * Whether the {@code <}, {@code >} and {@code +} selections of the
         * large Integer {@code large} are owned by FROZEN chains and are the
         * standard Integer operations. The structural selection starts at
         * the delegation parent, because a numeric value is never itself a
         * structural lookup origin.
         */
        private static boolean selectsStandardLargeOperators(Object large, ProtosPrelude prelude) {
            Object origin = ProtosValueLookup.delegationParent(large, prelude).orElse(null);
            if (!(origin instanceof ProtosObjectValue)) {
                return false;
            }
            for (String selector : new String[] {LESS, GREATER, PLUS}) {
                ProtosValueLookup.StructuralSelection selection =
                        ProtosValueLookup.selectStructural(origin, selector);
                if (selection == null
                        || !selection.allOwnersFrozen()
                        || !ProtosNumericSelectionNode.standard(
                                selector, large, selection.read(), selection.home(), prelude)) {
                    return false;
                }
            }
            return true;
        }

        /*
         * Whether the complete selection of {@code selector} on the receiver
         * is owned by {@code prototype}, the exact Prelude binding, over a
         * chain whose every examined object is FROZEN, and selects a Closure.
         * A FROZEN chain can change neither its slots nor its parents, so the
         * selected Closure is the one the canonical source installed in that
         * binding. The {@code eachStep} selection is the one that the
         * {@code this.eachStep} delegation of the canonical {@code each}
         * observes on the same receiver.
         */
        private static boolean selectsCanonicalMethod(
                ProtosObjectValue receiver, String selector, ProtosObjectValue prototype) {
            ProtosValueLookup.StructuralSelection selection =
                    ProtosValueLookup.selectStructural(receiver, selector);
            return selection != null
                    && selection.home() == prototype
                    && selection.allOwnersFrozen()
                    && selection.read() instanceof ProtosClosureValue;
        }

        /* The general and exact cursor; arbitrary precision is cold. */
        @TruffleBoundary
        static RangeIterationState prepare(
                boolean stepped, ProtosObjectValue receiver, Object step, ProtosPrelude prelude) {
            Object start = receiver.readLocalSlot("start").orElseThrow();
            Object stop = receiver.readLocalSlot("stop").orElseThrow();
            Object increment = stepped
                    ? step
                    : (receiver.parent().orElse(null)
                            == prelude.bindings().readLocalSlot("Range").orElse(null) ? 1L : -1L);
            if (start instanceof Long first && stop instanceof Long last
                    && increment instanceof Long delta) {
                return new RangeIterationState(first, last, delta);
            }
            return new RangeIterationState(start, stop, increment, prelude);
        }
    }


    /**
     * The cursor of one admitted standard Range iteration. Its direction is
     * the sign of the step chosen from the selected canonical prototype, not
     * the order of the bounds. Held only in a
     * Bytecode DSL local of the iterating root, so it survives that root's
     * suspensions and is never a guest value.
     */
    public static final class RangeIterationState {
        private long next;
        private final long stop;
        private final long step;
        private boolean exhausted;
        /* The exact cursor, used only when a bound or the step is a large Integer. */
        private final boolean exact;
        private Object exactNext;
        private final Object exactStop;
        private final Object exactStep;
        private final boolean ascending;
        private final ProtosPrelude prelude;

        RangeIterationState(long start, long stop, long step) {
            this.next = start;
            this.stop = stop;
            this.step = step;
            this.exact = false;
            this.exactNext = null;
            this.exactStop = null;
            this.exactStep = null;
            this.ascending = step > 0;
            this.prelude = null;
        }

        RangeIterationState(Object start, Object stop, Object step, ProtosPrelude prelude) {
            this.stop = 0L;
            this.step = 0L;
            this.exact = true;
            this.exactNext = start;
            this.exactStop = stop;
            this.exactStep = step;
            this.ascending = ProtosNumericValueSupport.integerSignum(step) > 0;
            this.prelude = prelude;
        }

        boolean isExact() {
            return exact;
        }

        /*
         * The exact cursor before the stop bound, after advancing it by the
         * step, or null once the stop bound is reached. Arbitrary-precision
         * comparison and addition are cold relative to the primitive cursor.
         */
        @TruffleBoundary
        Object advanceExactOrNull() {
            int order = ProtosNumericValueSupport.compareIntegers(exactNext, exactStop);
            if (ascending ? order >= 0 : order <= 0) {
                return null;
            }
            Object current = exactNext;
            exactNext = ProtosNumericValueSupport.addIntegers(current, exactStep, prelude);
            return current;
        }

        boolean isUnitStep() {
            return !exact && (step == 1L || step == -1L);
        }

        /*
         * Termination of a unit-step primitive cursor. Such a cursor is never
         * marked exhausted, because its advance cannot overflow.
         */
        boolean hasNextUnitStep() {
            return step == 1L ? next < stop : next > stop;
        }

        /*
         * Advances a unit-step primitive cursor; called only after
         * hasNextUnitStep() answered true. Then next < stop <= Long.MAX_VALUE
         * (step 1) or next > stop >= Long.MIN_VALUE (step -1), so next + step
         * cannot overflow.
         */
        long advanceUnitStep() {
            long current = next;
            next = current + step;
            return current;
        }

        boolean hasNext() {
            return !exhausted && (step > 0 ? next < stop : next > stop);
        }

        long advance() {
            long current = next;
            long following = current + step;
            if (((current ^ following) & (step ^ following)) < 0) {
                exhausted = true;
            } else {
                next = following;
            }
            return current;
        }
    }

    /**
     * The internal construction of a call of the evaluated {@code factory}
     * with the two evaluated bounds {@code start} and {@code stop}, the
     * single producer of its site's value: the completed value is stored in
     * the constant {@code outcome} local and the operation answers whether it
     * is a suspended invocation that the lowering must compose; a terminal
     * site keeps a suspended selected factory in its terminal transport
     * instead.
     *
     * <p>The Range is constructed only when the factory is exactly the
     * {@code Range} or {@code InvertedRange} binding of the admitted Prelude
     * and the complete source-backed construction would run, without
     * signaling, through only standard selections: both canonical prototypes
     * are FROZEN ordinary objects whose immediate parent is the root Object,
     * each owns its source-backed {@code construct} and {@code init} and owns
     * no {@code call}, {@code parent} or {@code freeze}; the root Object
     * selects the standard {@code call}, {@code construct}, {@code parent}
     * and {@code freeze}; the {@code Integer.recognizes} selection and the
     * Array {@code size} and {@code at} selections over the forwarded
     * argument Array are FROZEN standard selections; both bounds are
     * Integers; and the SmallInteger {@code ==} and the {@code <}, {@code >},
     * {@code <=} and {@code >=} selections of the start bound's family are
     * the standard ones. Any other factory, bound or selection invokes the
     * selected factory here with the same evaluated operands, in the B5
     * packet of CallBinaryMethod, which produces its own result or Error in
     * source order.
     *
     * <p>The constructed Range is a fresh ordinary object whose immediate
     * parent is the factory when the bounds are in its direction, and the
     * opposite canonical prototype otherwise ({@code Range}: start &lt;=
     * stop; {@code InvertedRange}: start &gt;= stop; equal bounds keep the
     * factory), with exactly the local slots {@code start} and {@code stop}
     * holding the supplied Integers unchanged, then FROZEN, through the same
     * object mutation path as the source initialization.
     *
     * <p>The cached entry specializes on the selected callable: the exact
     * factory identity and the identity of the compact invocation's own
     * Closure, or of the admitted Prelude. Every FROZEN fact is proven
     * when the entry is installed; a mutable root is guarded by its
     * structural selections and reads and classifies the selected slots on
     * every execution, so a reassignment that preserves the Shape is
     * observed.
     */
    @Operation(storeBytecodeIndex = false)
    @ConstantOperand(type = boolean.class, name = "terminal")
    @ConstantOperand(type = LocalAccessor.class, name = "outcome")
    public static final class MakeRange {
        static final String CALL = "call";
        static final String CONSTRUCT = "construct";
        static final String PARENT = "parent";
        static final String FREEZE = "freeze";

        static final String EQUAL = "==";
        static final String LESS = "<";
        static final String GREATER = ">";
        static final String LESS_EQUAL = "<=";
        static final String GREATER_EQUAL = ">=";

        /*
         * Signed-64 bounds of a cached canonical factory. Every fact of the
         * canonical construction (canonical prototypes, FROZEN standard root
         * call, construct, parent and freeze, FROZEN standard signed-64
         * comparisons) is classified once, when the entry is installed; a
         * FROZEN chain keeps it for good. Only the factory and Prelude
         * identities are compared, then the bounds choose the prototype and
         * the Range is constructed through cached object operations.
         *
         * The captured entry reads the Prelude once, from the final field of
         * the compact invocation's own Closure, when it is installed; it binds
         * null once the invocation published its activation, and never
         * applies when the Closure captured no Prelude.
         */
        @Specialization(
                guards = {
                    "closure != null",
                    "closure == cachedClosure",
                    "cachedPrelude != null",
                    "factory == cachedFactory",
                    "opposite != null",
                    "standard"
                },
                limit = "2",
                excludeForUncached = true)
        public static boolean doCapturedLong(
                boolean terminal,
                LocalAccessor outcome,
                ProtosObjectValue factory,
                long start,
                long stop,
                @Bind VirtualFrame frame,
                @Bind BytecodeNode bytecode,
                @Bind("capturedClosure(frame)") ProtosClosureValue closure,
                @Cached("closure") ProtosClosureValue cachedClosure,
                @Cached("cachedClosure.preludeOrNullForRuntime()") ProtosPrelude cachedPrelude,
                @Cached("factory") ProtosObjectValue cachedFactory,
                @Cached("oppositeOrNull(cachedFactory, cachedPrelude)") ProtosObjectValue opposite,
                @Cached("isRange(cachedFactory, cachedPrelude)") boolean ascending,
                @Cached("frozenStandardConstruction(cachedFactory, cachedPrelude)") boolean standard,
                @Shared("startGet") @Cached DynamicObject.GetNode startGet,
                @Shared("startPut") @Cached DynamicObject.PutNode startPut,
                @Shared("stopGet") @Cached DynamicObject.GetNode stopGet,
                @Shared("stopPut") @Cached DynamicObject.PutNode stopPut,
                @Shared("freeze") @Cached DynamicObject.SetShapeFlagsNode freeze) {
            boolean keep = ascending ? start <= stop : start >= stop;
            outcome.setObject(bytecode, frame, construct(keep ? cachedFactory : opposite, start, stop,
                    startGet, startPut, stopGet, stopPut, freeze));
            return false;
        }

        @Specialization(
                guards = {
                    "factory == cachedFactory",
                    "prelude == cachedPrelude",
                    "opposite != null",
                    "standard"
                },
                limit = "2",
                excludeForUncached = true)
        public static boolean doLong(
                boolean terminal,
                LocalAccessor outcome,
                ProtosObjectValue factory,
                long start,
                long stop,
                @Bind VirtualFrame frame,
                @Bind BytecodeNode bytecode,
                @Bind("admittedPrelude(frame)") ProtosPrelude prelude,
                @Cached("prelude") ProtosPrelude cachedPrelude,
                @Cached("factory") ProtosObjectValue cachedFactory,
                @Cached("oppositeOrNull(cachedFactory, cachedPrelude)") ProtosObjectValue opposite,
                @Cached("isRange(cachedFactory, cachedPrelude)") boolean ascending,
                @Cached("frozenStandardConstruction(cachedFactory, cachedPrelude)") boolean standard,
                @Shared("startGet") @Cached DynamicObject.GetNode startGet,
                @Shared("startPut") @Cached DynamicObject.PutNode startPut,
                @Shared("stopGet") @Cached DynamicObject.GetNode stopGet,
                @Shared("stopPut") @Cached DynamicObject.PutNode stopPut,
                @Shared("freeze") @Cached DynamicObject.SetShapeFlagsNode freeze) {
            boolean keep = ascending ? start <= stop : start >= stop;
            outcome.setObject(bytecode, frame, construct(keep ? cachedFactory : opposite, start, stop,
                    startGet, startPut, stopGet, stopPut, freeze));
            return false;
        }

        /*
         * Any other factory or bounds, including large Integer bounds of a
         * canonical factory, whose exact comparison is cold. It replaces no
         * cached entry, so a site that also sees another factory keeps its
         * cached entries eligible. Any representation other than an exact
         * ordinary object or Integer bounds skips the complete selection.
         * The standard construction completes here, including exact Integer
         * bounds; any other selected behavior is invoked here.
         */
        @Specialization
        @StoreBytecodeIndex
        @InliningCutoff
        public static boolean doGeneric(
                boolean terminal,
                LocalAccessor outcome,
                Object factory,
                Object start,
                Object stop,
                @Bind VirtualFrame frame,
                @Bind ProtosBytecodeRootNode root,
                @Bind BytecodeNode bytecode,
                @Bind("$bytecodeIndex") int bytecodeIndex,
                @Cached ProtosFunctionCallDispatchers.CallNode call,
                @Cached ProtosNumericSelectionNode less,
                @Cached ProtosNumericSelectionNode greater,
                @Cached ProtosNumericSelectionNode lessEqual,
                @Cached ProtosNumericSelectionNode greaterEqual,
                @Shared("startGet") @Cached DynamicObject.GetNode startGet,
                @Shared("startPut") @Cached DynamicObject.PutNode startPut,
                @Shared("stopGet") @Cached DynamicObject.GetNode stopGet,
                @Shared("stopPut") @Cached DynamicObject.PutNode stopPut,
                @Shared("freeze") @Cached DynamicObject.SetShapeFlagsNode freeze) {
            if (ProtosValueLookup.isOrdinaryObject(factory)
                    && ProtosNumericValueSupport.isCurrentInteger(start)
                    && ProtosNumericValueSupport.isCurrentInteger(stop)) {
                ProtosObjectValue candidate = (ProtosObjectValue) factory;
                ProtosPrelude prelude = admittedPrelude(frame);
                if (selectsStandard(candidate, prelude)
                        && standardComparisons(frame, start, less, greater, lessEqual, greaterEqual)) {
                    outcome.setObject(bytecode, frame, construct(
                            exactPrototype(candidate, start, stop, prelude), start, stop,
                            startGet, startPut, stopGet, stopPut, freeze));
                    return false;
                }
            }
            Object result = call.execute(
                    frame, terminal, factory,
                    ProtosFrameArguments.dispatchedCall2(
                            null, ProtosFrameArguments.functionCallMarker(),
                            null, null, null, start, stop),
                    null, root);
            return StoreAndTestContinuation.storeResult(
                    outcome,
                    ProtosTailCallContinuation.completeCall(
                            terminal, result, bytecode, bytecodeIndex),
                    frame, bytecode);
        }

        /*
         * The compact invocation's own Closure, or null once it published its
         * activation.
         */
        @NonIdempotent
        static ProtosClosureValue capturedClosure(VirtualFrame frame) {
            return ProtosNumericSelectionNode.capturedClosure(frame);
        }

        @NonIdempotent
        static ProtosPrelude admittedPrelude(VirtualFrame frame) {
            return ProtosFrameArguments.admittedPreludeOrNull(frame.getArguments());
        }

        private static ProtosObjectValue construct(
                ProtosObjectValue prototype,
                Object start,
                Object stop,
                DynamicObject.GetNode startGet,
                DynamicObject.PutNode startPut,
                DynamicObject.GetNode stopGet,
                DynamicObject.PutNode stopPut,
                DynamicObject.SetShapeFlagsNode freeze) {
            ProtosObjectValue instance = new ProtosObjectValue(prototype);
            instance.createLocalSlotCanonical("start", ProtosNumericValueSupport.guestValue(start),
                    startGet, startPut);
            instance.createLocalSlotCanonical("stop", ProtosNumericValueSupport.guestValue(stop),
                    stopGet, stopPut);
            return instance.freeze(freeze);
        }

        /*
         * Specialization time only: whether the factory is canonical and the
         * root call, construct, parent and freeze selections and the
         * signed-64 comparisons are FROZEN standard selections.
         */
        @TruffleBoundary
        static boolean frozenStandardConstruction(ProtosObjectValue factory, ProtosPrelude prelude) {
            if (canonicalFactoryOrNull(factory, prelude) == null) {
                return false;
            }
            ProtosValueLookup.StructuralSelection call = selectRoot(factory, CALL);
            ProtosValueLookup.StructuralSelection construct = selectRoot(factory, CONSTRUCT);
            ProtosValueLookup.StructuralSelection parent = selectRoot(factory, PARENT);
            ProtosValueLookup.StructuralSelection freeze = selectRoot(factory, FREEZE);
            return frozenStandard(
                            allOwnersFrozen(call, construct, parent, freeze),
                            call, construct, parent, freeze)
                    && frozenStandardLongOperators(
                            prelude, LESS, GREATER, LESS_EQUAL, GREATER_EQUAL);
        }

        /* Specialization time only: the FROZEN standard signed-64 operator proof. */
        static boolean frozenStandardLongOperators(
                ProtosPrelude prelude, String less, String greater, String lessEqual, String greaterEqual) {
            return ProtosNumericSelectionNode.frozenStandardLongOperators(
                    prelude, less, greater, lessEqual, greaterEqual);
        }

        /*
         * Arity equality is proven on its frozen chain with the factory;
         * every bound comparison has the start bound as its
         * receiver, so the selections of its own family are the ones the
         * source construction observes.
         */
        private static boolean standardComparisons(
                VirtualFrame frame,
                Object start,
                ProtosNumericSelectionNode less,
                ProtosNumericSelectionNode greater,
                ProtosNumericSelectionNode lessEqual,
                ProtosNumericSelectionNode greaterEqual) {
            return less.execute(frame, LESS, start)
                    && greater.execute(frame, GREATER, start)
                    && lessEqual.execute(frame, LESS_EQUAL, start)
                    && greaterEqual.execute(frame, GREATER_EQUAL, start);
        }

        static ProtosValueLookup.StructuralSelection selectRoot(
                ProtosObjectValue factory, String selector) {
            return factory == null
                    ? null
                    : ProtosValueLookup.selectStructural(ProtosObjectValue.rootObject(), selector);
        }

        static boolean allOwnersFrozen(
                ProtosValueLookup.StructuralSelection callSelection,
                ProtosValueLookup.StructuralSelection constructSelection,
                ProtosValueLookup.StructuralSelection parentSelection,
                ProtosValueLookup.StructuralSelection freezeSelection) {
            return callSelection != null
                    && constructSelection != null
                    && parentSelection != null
                    && freezeSelection != null
                    && callSelection.allOwnersFrozen()
                    && constructSelection.allOwnersFrozen()
                    && parentSelection.allOwnersFrozen()
                    && freezeSelection.allOwnersFrozen();
        }

        static boolean frozenStandard(
                boolean frozen,
                ProtosValueLookup.StructuralSelection callSelection,
                ProtosValueLookup.StructuralSelection constructSelection,
                ProtosValueLookup.StructuralSelection parentSelection,
                ProtosValueLookup.StructuralSelection freezeSelection) {
            return frozen && standardRoot(
                    callSelection, constructSelection, parentSelection, freezeSelection);
        }

        /* Whether the root Object currently selects the four standard behaviors. */
        static boolean standardRoot(
                ProtosValueLookup.StructuralSelection callSelection,
                ProtosValueLookup.StructuralSelection constructSelection,
                ProtosValueLookup.StructuralSelection parentSelection,
                ProtosValueLookup.StructuralSelection freezeSelection) {
            return ProtosStandardObjectProtocol.isCanonicalStandardCallSelection(
                            callSelection.read(), callSelection.home())
                    && ProtosStandardObjectProtocol.isCanonicalStandardConstructSelection(
                            constructSelection.read(), constructSelection.home())
                    && ProtosStandardObjectProtocol.isCanonicalStandardParentSelection(
                            parentSelection.read(), parentSelection.home())
                    && ProtosStandardObjectProtocol.isCanonicalStandardFreezeSelection(
                            freezeSelection.read(), freezeSelection.home());
        }

        /*
         * Specialization-time only: the factory when it is the exact Range or
         * InvertedRange binding of the Prelude and every FROZEN fact of the
         * canonical construction holds, or null. FROZEN objects can change
         * neither their slots nor their parents, so a proven fact holds for
         * as long as the identity is cached.
         */
        @TruffleBoundary
        static ProtosObjectValue canonicalFactoryOrNull(
                ProtosObjectValue factory, ProtosPrelude prelude) {
            if (prelude == null || !ProtosValueLookup.isOrdinaryObject(factory)) {
                return null;
            }
            try {
                Object range = prelude.bindings().readLocalSlot("Range").orElse(null);
                Object inverted = prelude.bindings().readLocalSlot("InvertedRange").orElse(null);
                if (factory != range && factory != inverted) {
                    return null;
                }
                if (!isCanonicalPrototype(range) || !isCanonicalPrototype(inverted)) {
                    return null;
                }
                ProtosValueLookup.StructuralSelection recognizes =
                        ProtosValueLookup.selectStructural(prelude.integerPrototype(), "recognizes");
                if (recognizes == null || !recognizes.allOwnersFrozen()) {
                    return null;
                }
                // The source compares args.size() with 2 using Number equality,
                // which is distinct from the arithmetic and ordering protocol.
                Object smallInteger = ProtosValueLookup.delegationParent(0L, prelude).orElse(null);
                ProtosValueLookup.StructuralSelection equality =
                        ProtosValueLookup.selectStructural(smallInteger, EQUAL);
                if (equality == null
                        || !equality.allOwnersFrozen()
                        || !ProtosStandardNumberEqualityProtocol.isCanonicalStandardEqualitySelection(
                                equality.read(), equality.home(), prelude)) {
                    return null;
                }
                for (String selector : new String[] {"size", "at"}) {
                    ProtosValueLookup.StructuralSelection indexed =
                            ProtosValueLookup.selectStructural(prelude.arrayPrototype(), selector);
                    if (indexed == null
                            || !indexed.allOwnersFrozen()
                            || !ProtosStandardArrayProtocol.isStandardIndexedBehavior(
                                    selector, indexed.read())) {
                        return null;
                    }
                }
                return factory;
            } catch (IllegalStateException
                    | UnsupportedOperationException
                    | java.util.NoSuchElementException unavailable) {
                return null;
            }
        }

        /*
         * A FROZEN ordinary prototype whose immediate parent is the root
         * Object, which owns its source-backed construct and init, and which
         * owns no call, parent or freeze, so those are selected from the root.
         */
        private static boolean isCanonicalPrototype(Object candidate) {
            if (!ProtosValueLookup.isOrdinaryObject(candidate)) {
                return false;
            }
            ProtosObjectValue prototype = (ProtosObjectValue) candidate;
            Shape shape = prototype.getShape();
            if (!prototype.usesObjectStorageForRuntime()
                    || !ProtosObjectValue.isFrozenShape(shape)
                    || !(prototype.parent().orElse(null) instanceof ProtosObjectValue parent)
                    || !parent.isRootObject()
                    || ProtosValueLookup.hasProperty(shape, "call")
                    || ProtosValueLookup.hasProperty(shape, "parent")
                    || ProtosValueLookup.hasProperty(shape, "freeze")) {
                return false;
            }
            return ownsSourceMethod(prototype, "construct") && ownsSourceMethod(prototype, "init");
        }

        private static boolean ownsSourceMethod(ProtosObjectValue prototype, String selector) {
            ProtosValueLookup.StructuralSelection selection =
                    ProtosValueLookup.selectStructural(prototype, selector);
            return selection != null
                    && selection.home() == prototype
                    && selection.allOwnersFrozen()
                    && selection.read() instanceof ProtosClosureValue closure
                    && closure.nativeBody().isEmpty();
        }

        /* The complete selection, observed without invoking anything. */
        @TruffleBoundary
        static boolean selectsStandard(ProtosObjectValue factory, ProtosPrelude prelude) {
            if (canonicalFactoryOrNull(factory, prelude) == null) {
                return false;
            }
            ProtosObjectValue root = ProtosObjectValue.rootObject();
            ProtosValueLookup.StructuralSelection call = ProtosValueLookup.selectStructural(root, "call");
            ProtosValueLookup.StructuralSelection construct =
                    ProtosValueLookup.selectStructural(root, "construct");
            ProtosValueLookup.StructuralSelection parent =
                    ProtosValueLookup.selectStructural(root, "parent");
            ProtosValueLookup.StructuralSelection freeze =
                    ProtosValueLookup.selectStructural(root, "freeze");
            return call != null && construct != null && parent != null && freeze != null
                    && standardRoot(call, construct, parent, freeze);
        }

        @TruffleBoundary
        static ProtosObjectValue oppositeOrNull(ProtosObjectValue factory, ProtosPrelude prelude) {
            if (prelude == null) {
                return null;
            }
            Object range = prelude.bindings().readLocalSlot("Range").orElse(null);
            Object inverted = prelude.bindings().readLocalSlot("InvertedRange").orElse(null);
            if (factory == range && inverted instanceof ProtosObjectValue other) {
                return other;
            }
            if (factory == inverted && range instanceof ProtosObjectValue other) {
                return other;
            }
            return null;
        }

        @TruffleBoundary
        static boolean isRange(ProtosObjectValue factory, ProtosPrelude prelude) {
            return prelude != null
                    && factory == prelude.bindings().readLocalSlot("Range").orElse(null);
        }

        @TruffleBoundary
        static ProtosObjectValue exactPrototype(
                ProtosObjectValue factory, Object start, Object stop, ProtosPrelude prelude) {
            int order = ProtosNumericValueSupport.compareIntegers(start, stop);
            boolean keep = isRange(factory, prelude) ? order <= 0 : order >= 0;
            return keep ? factory : oppositeOrNull(factory, prelude);
        }
    }

    /*
     * BinarySubscript / SetItem / GetLen model.
     *
     * Each operation owns the complete selection of its at, atPut or size
     * over operands already evaluated once, in Protos order (receiver,
     * arguments). Its standard specializations execute only while the
     * behavior selected on the receiver is the standard Array/Bytes one,
     * proven by the shared StandardIndexedSelectionNode; the built-in Array
     * path specializes primitive long, double and boolean storage. Every
     * other case reaches the fallback, which signals the standard Error for
     * an operand outside the standard lanes (a non-Integer or large Integer
     * index) and otherwise invokes the selected behavior through the shared
     * call dispatcher. The fallback replaces no standard specialization, so a
     * site that alternates between standard and redefined selections keeps
     * its standard entries. Each operation stores its result in the constant
     * outcome local, typed for a primitive carrier, and answers whether that
     * result must be composed as a continuation: only a suspended selected
     * invocation answers true, and the enclosing bytecode composes it.
     */
    @Operation(storeBytecodeIndex = true)
    @ConstantOperand(type = LocalAccessor.class, name = "outcome")
    public static final class BinarySubscript {
        static final String SELECTOR = "at";

        @Specialization(guards = {"array.hasLongStorageForRuntime()",
                "selection.execute(frame, SELECTOR, array)"})
        public static boolean doLongStorage(
                LocalAccessor outcome,
                ProtosArrayValue array,
                long index,
                @Bind VirtualFrame frame,
                @Shared("selection") @Cached StandardIndexedSelectionNode selection,
                @Bind Node inliningTarget,
                @Shared("indexError") @Cached InlinedBranchProfile errorProfile,
                @Bind BytecodeNode bytecode) {
            outcome.setLong(bytecode, frame, array.indexedLongAtNormalized(
                    existingArrayIndex(array, index, frame,
                            inliningTarget, errorProfile)));
            return false;
        }

        @Specialization(guards = {"array.hasDoubleStorageForRuntime()",
                "selection.execute(frame, SELECTOR, array)"})
        public static boolean doDoubleStorage(
                LocalAccessor outcome,
                ProtosArrayValue array,
                long index,
                @Bind VirtualFrame frame,
                @Shared("selection") @Cached StandardIndexedSelectionNode selection,
                @Bind Node inliningTarget,
                @Shared("indexError") @Cached InlinedBranchProfile errorProfile,
                @Bind BytecodeNode bytecode) {
            outcome.setDouble(bytecode, frame, array.indexedDoubleAtNormalized(
                    existingArrayIndex(array, index, frame,
                            inliningTarget, errorProfile)));
            return false;
        }

        @Specialization(guards = {"array.hasBooleanStorageForRuntime()",
                "selection.execute(frame, SELECTOR, array)"})
        public static boolean doBooleanStorage(
                LocalAccessor outcome,
                ProtosArrayValue array,
                long index,
                @Bind VirtualFrame frame,
                @Shared("selection") @Cached StandardIndexedSelectionNode selection,
                @Bind Node inliningTarget,
                @Shared("indexError") @Cached InlinedBranchProfile errorProfile,
                @Bind BytecodeNode bytecode) {
            outcome.setBoolean(bytecode, frame, array.indexedBooleanAtNormalized(
                    existingArrayIndex(array, index, frame,
                            inliningTarget, errorProfile)));
            return false;
        }

        /* Every storage representation, read as its exact guest value. */
        @Specialization(guards = "selection.execute(frame, SELECTOR, array)",
                replaces = {"doLongStorage", "doDoubleStorage", "doBooleanStorage"})
        public static boolean doArray(
                LocalAccessor outcome,
                ProtosArrayValue array,
                long index,
                @Bind VirtualFrame frame,
                @Shared("selection") @Cached StandardIndexedSelectionNode selection,
                @Bind Node inliningTarget,
                @Shared("indexError") @Cached InlinedBranchProfile errorProfile,
                @Bind BytecodeNode bytecode) {
            return StoreAndTestContinuation.storeResult(
                    outcome, array.indexedAtNormalized(
                    existingArrayIndex(array, index, frame,
                            inliningTarget, errorProfile)), frame, bytecode);
        }

        /*
         * Standard Bytes indexed read, mirroring the Bytes at protocol:
         * the long index is validated against the current size before
         * it is narrowed to a host index.
         */
        @Specialization(guards = "selection.execute(frame, SELECTOR, bytes)")
        public static boolean doBytes(
                LocalAccessor outcome,
                ProtosBytesValue bytes,
                long index,
                @Bind VirtualFrame frame,
                @Shared("selection") @Cached StandardIndexedSelectionNode selection,
                @Bind Node inliningTarget,
                @Shared("indexError") @Cached InlinedBranchProfile errorProfile,
                @Bind BytecodeNode bytecode) {
            return StoreAndTestContinuation.storeResult(
                    outcome, bytes.indexedAt(
                    existingBytesIndex(bytes, index, frame,
                            inliningTarget, errorProfile)), frame, bytecode);
        }

        @Fallback
        @InliningCutoff
        public static boolean doSelected(
                LocalAccessor outcome,
                Object receiver,
                Object index,
                @Bind VirtualFrame frame,
                @Bind ProtosBytecodeRootNode root,
                @Cached ProtosFunctionCallDispatchers.CallNode call,
                @Bind BytecodeNode bytecode) {
            return StoreAndTestContinuation.storeResult(
                    outcome, invokeSelectedIndexed(
                    frame, SELECTOR, receiver,
                    ProtosFrameArguments.dispatchedCall1(
                            null, ProtosFrameArguments.functionCallMarker(),
                            null, null, null,
                            ProtosNumericValueSupport.guestValue(index)),
                    root, call), frame, bytecode);
        }
    }

    /**
     * {@link BinarySubscript} over a receiver held by a lexical cell and an
     * index held by a local, both provably PRESENT, read inside the operation
     * instead of by a separate {@link LoadCell} and {@code load.local}. The
     * receiver is read first, exactly as {@link LoadCell} reads it, then the
     * index, as the local's current value: primitive {@code long} while the
     * local holds one, otherwise its exact guest value. The indexed
     * selection, access, outcome store and continuation answer are those of
     * {@link BinarySubscript}, through {@link CellLocalSubscriptNode}.
     */
    @Operation(storeBytecodeIndex = true)
    @ConstantOperand(type = LocalAccessor.class, name = "outcome")
    @ConstantOperand(type = LocalAccessor.class, name = "receiverCell")
    @ConstantOperand(type = LocalAccessor.class, name = "index")
    public static final class BinarySubscriptCellLocal {
        /*
         * The typed index read is attempted first. Its failure is caught
         * here, never declared as rewriteOn: under rewriteOn the DSL would
         * answer the exception's value (the index) as this operation's
         * boolean result. The exception carries the local's exact value,
         * which takes the generic selection unchanged.
         */
        @Specialization
        public static boolean perform(
                LocalAccessor outcome,
                LocalAccessor receiverCell,
                LocalAccessor index,
                @Bind VirtualFrame frame,
                @Bind BytecodeNode bytecode,
                @Bind ProtosBytecodeRootNode root,
                @Cached CellLocalSubscriptNode subscript) {
            Object receiver = LoadCell.perform(receiverCell, frame, bytecode);
            long indexValue;
            try {
                indexValue = index.getLong(bytecode, frame);
            } catch (UnexpectedResultException notLong) {
                return subscript.execute(
                        frame, outcome, receiver, notLong.getResult(), root, bytecode);
            }
            return subscript.executeLong(frame, outcome, receiver, indexValue, root, bytecode);
        }
    }

    /*
     * The indexed selection of BinarySubscriptCellLocal over its already read
     * operands. Each specialization has the guard of the BinarySubscript
     * specialization of the same name and executes that specialization's own
     * method, so the standard Array/Bytes access, bounds handling, selection
     * check, outcome store and selected-behavior fallback stay owned by
     * BinarySubscript.
     */
    @GenerateUncached
    @GenerateInline(false)
    abstract static class CellLocalSubscriptNode extends Node {
        static final String SELECTOR = BinarySubscript.SELECTOR;

        abstract boolean execute(VirtualFrame frame, LocalAccessor outcome, Object receiver,
                Object index, ProtosBytecodeRootNode root, BytecodeNode bytecode);

        abstract boolean executeLong(VirtualFrame frame, LocalAccessor outcome, Object receiver,
                long index, ProtosBytecodeRootNode root, BytecodeNode bytecode);

        @Specialization(guards = {"array.hasLongStorageForRuntime()",
                "selection.execute(frame, SELECTOR, array)"})
        static boolean doLongStorage(
                VirtualFrame frame, LocalAccessor outcome, ProtosArrayValue array, long index,
                ProtosBytecodeRootNode root, BytecodeNode bytecode,
                @Shared("selection") @Cached StandardIndexedSelectionNode selection,
                @Bind Node inliningTarget,
                @Shared("indexError") @Cached InlinedBranchProfile errorProfile) {
            return BinarySubscript.doLongStorage(outcome, array, index, frame,
                    selection, inliningTarget, errorProfile, bytecode);
        }

        @Specialization(guards = {"array.hasDoubleStorageForRuntime()",
                "selection.execute(frame, SELECTOR, array)"})
        static boolean doDoubleStorage(
                VirtualFrame frame, LocalAccessor outcome, ProtosArrayValue array, long index,
                ProtosBytecodeRootNode root, BytecodeNode bytecode,
                @Shared("selection") @Cached StandardIndexedSelectionNode selection,
                @Bind Node inliningTarget,
                @Shared("indexError") @Cached InlinedBranchProfile errorProfile) {
            return BinarySubscript.doDoubleStorage(outcome, array, index, frame,
                    selection, inliningTarget, errorProfile, bytecode);
        }

        @Specialization(guards = {"array.hasBooleanStorageForRuntime()",
                "selection.execute(frame, SELECTOR, array)"})
        static boolean doBooleanStorage(
                VirtualFrame frame, LocalAccessor outcome, ProtosArrayValue array, long index,
                ProtosBytecodeRootNode root, BytecodeNode bytecode,
                @Shared("selection") @Cached StandardIndexedSelectionNode selection,
                @Bind Node inliningTarget,
                @Shared("indexError") @Cached InlinedBranchProfile errorProfile) {
            return BinarySubscript.doBooleanStorage(outcome, array, index, frame,
                    selection, inliningTarget, errorProfile, bytecode);
        }

        @Specialization(guards = "selection.execute(frame, SELECTOR, array)",
                replaces = {"doLongStorage", "doDoubleStorage", "doBooleanStorage"})
        static boolean doArray(
                VirtualFrame frame, LocalAccessor outcome, ProtosArrayValue array, long index,
                ProtosBytecodeRootNode root, BytecodeNode bytecode,
                @Shared("selection") @Cached StandardIndexedSelectionNode selection,
                @Bind Node inliningTarget,
                @Shared("indexError") @Cached InlinedBranchProfile errorProfile) {
            return BinarySubscript.doArray(outcome, array, index, frame,
                    selection, inliningTarget, errorProfile, bytecode);
        }

        @Specialization(guards = "selection.execute(frame, SELECTOR, bytes)")
        static boolean doBytes(
                VirtualFrame frame, LocalAccessor outcome, ProtosBytesValue bytes, long index,
                ProtosBytecodeRootNode root, BytecodeNode bytecode,
                @Shared("selection") @Cached StandardIndexedSelectionNode selection,
                @Bind Node inliningTarget,
                @Shared("indexError") @Cached InlinedBranchProfile errorProfile) {
            return BinarySubscript.doBytes(outcome, bytes, index, frame,
                    selection, inliningTarget, errorProfile, bytecode);
        }

        @Fallback
        @InliningCutoff
        static boolean doSelected(
                VirtualFrame frame, LocalAccessor outcome, Object receiver, Object index,
                ProtosBytecodeRootNode root, BytecodeNode bytecode,
                @Cached ProtosFunctionCallDispatchers.CallNode call) {
            return BinarySubscript.doSelected(outcome, receiver, index, frame, root, call, bytecode);
        }
    }

    /*
     * The standard atPut result is the inserted value; a redefined atPut
     * answers the result of its invocation. Indexed assignment discards this
     * result and answers its own RHS.
     */
    @Operation(storeBytecodeIndex = true)
    @ConstantOperand(type = LocalAccessor.class, name = "outcome")
    public static final class SetItem {
        static final String SELECTOR = "atPut";

        /*
         * Standard Array indexed write, mirroring the Array atPut
         * protocol: FROZEN first, then the signed long index is validated
         * against the current size before it is narrowed to a host index,
         * so no host exception escapes as an implementation error.
         */
        @Specialization(guards = {"array.hasLongStorageForRuntime()",
                "selection.execute(frame, SELECTOR, array)"})
        public static boolean doLongStorage(
                LocalAccessor outcome,
                ProtosArrayValue array,
                long index,
                long value,
                @Bind VirtualFrame frame,
                @Shared("selection") @Cached StandardIndexedSelectionNode selection,
                @Bind Node inliningTarget,
                @Shared("indexError") @Cached InlinedBranchProfile errorProfile,
                @Bind BytecodeNode bytecode) {
            array.indexedLongStoragePutNormalized(
                    writableArrayIndex(array, index, frame,
                            inliningTarget, errorProfile), value);
            outcome.setLong(bytecode, frame, value);
            return false;
        }

        @Specialization(guards = {"array.hasDoubleStorageForRuntime()",
                "selection.execute(frame, SELECTOR, array)"})
        public static boolean doDoubleStorage(
                LocalAccessor outcome,
                ProtosArrayValue array,
                long index,
                double value,
                @Bind VirtualFrame frame,
                @Shared("selection") @Cached StandardIndexedSelectionNode selection,
                @Bind Node inliningTarget,
                @Shared("indexError") @Cached InlinedBranchProfile errorProfile,
                @Bind BytecodeNode bytecode) {
            array.indexedDoubleStoragePutNormalized(
                    writableArrayIndex(array, index, frame,
                            inliningTarget, errorProfile), value);
            outcome.setDouble(bytecode, frame, value);
            return false;
        }

        @Specialization(guards = {"array.hasBooleanStorageForRuntime()",
                "selection.execute(frame, SELECTOR, array)"})
        public static boolean doBooleanStorage(
                LocalAccessor outcome,
                ProtosArrayValue array,
                long index,
                boolean value,
                @Bind VirtualFrame frame,
                @Shared("selection") @Cached StandardIndexedSelectionNode selection,
                @Bind Node inliningTarget,
                @Shared("indexError") @Cached InlinedBranchProfile errorProfile,
                @Bind BytecodeNode bytecode) {
            array.indexedBooleanStoragePutNormalized(
                    writableArrayIndex(array, index, frame,
                            inliningTarget, errorProfile), value);
            outcome.setBoolean(bytecode, frame, value);
            return false;
        }

        /* Every storage; an incompatible value promotes primitive storage. */
        @Specialization(guards = "selection.execute(frame, SELECTOR, array)",
                replaces = {"doLongStorage", "doDoubleStorage", "doBooleanStorage"})
        public static boolean doArray(
                LocalAccessor outcome,
                ProtosArrayValue array,
                long index,
                Object value,
                @Bind VirtualFrame frame,
                @Shared("selection") @Cached StandardIndexedSelectionNode selection,
                @Bind Node inliningTarget,
                @Shared("indexError") @Cached InlinedBranchProfile errorProfile,
                @Bind BytecodeNode bytecode) {
            array.indexedPutNormalized(
                    writableArrayIndex(array, index, frame,
                            inliningTarget, errorProfile),
                    ProtosNumericValueSupport.guestValue(value));
            return StoreAndTestContinuation.storeResult(
                    outcome, value, frame, bytecode);
        }

        /*
         * Standard Bytes indexed write, mirroring the Bytes atPut
         * protocol: FROZEN first, then index, then octet 0..255.
         */
        @Specialization(guards = "selection.execute(frame, SELECTOR, bytes)")
        public static boolean doBytes(
                LocalAccessor outcome,
                ProtosBytesValue bytes,
                long index,
                Object value,
                @Bind VirtualFrame frame,
                @Shared("selection") @Cached StandardIndexedSelectionNode selection,
                @Bind Node inliningTarget,
                @Shared("indexError") @Cached InlinedBranchProfile errorProfile,
                @Bind BytecodeNode bytecode) {
            if (bytes.isFrozen()) {
                errorProfile.enter(inliningTarget);
                throw indexedError(frame);
            }
            int hostIndex = existingBytesIndex(bytes, index, frame,
                    inliningTarget, errorProfile);
            Object octet = ProtosNumericValueSupport.guestValue(value);
            if (!ProtosNumericValueSupport.isUnsignedIntegerWithin(
                    octet, Byte.SIZE)) {
                errorProfile.enter(inliningTarget);
                throw indexedError(frame);
            }
            bytes.indexedPut(hostIndex, octet);
            return StoreAndTestContinuation.storeResult(
                    outcome, value, frame, bytecode);
        }

        @Fallback
        @InliningCutoff
        public static boolean doSelected(
                LocalAccessor outcome,
                Object receiver,
                Object index,
                Object value,
                @Bind VirtualFrame frame,
                @Bind ProtosBytecodeRootNode root,
                @Cached ProtosFunctionCallDispatchers.CallNode call,
                @Bind BytecodeNode bytecode) {
            return StoreAndTestContinuation.storeResult(
                    outcome, invokeSelectedIndexed(
                    frame, SELECTOR, receiver,
                    ProtosFrameArguments.dispatchedCall2(
                            null, ProtosFrameArguments.functionCallMarker(),
                            null, null, null,
                            ProtosNumericValueSupport.guestValue(index),
                            ProtosNumericValueSupport.guestValue(value)),
                    root, call), frame, bytecode);
        }
    }

    /*
     * Constant signed-64 Array construction, the single producer of its
     * site's value. The completed value is stored in the constant outcome
     * local and the operation answers whether it is a suspended invocation
     * that the lowering must compose; a terminal site keeps a suspended
     * selected factory in its terminal transport instead.
     *
     * The operation specializes on the evaluated factory. A factory whose
     * call and construct selections are FROZEN and standard (the canonical
     * root Object.call reaching the original Array construct of the cached
     * Prelude's Array prototype) is classified once, when its entry is
     * installed, and afterwards only its identity and the identity of the
     * compact invocation's own Closure, or of the admitted Prelude, are
     * compared. The standard construction is a fresh Array and always a
     * normal completion. Any other factory takes the complete selection:
     * the standard construction is still performed here, and every other
     * selected behavior is invoked here with the boxed constant elements as
     * its supplied arguments, in source order, its own result becoming the
     * outcome. The compiler-owned long[] constant is never mutated: the
     * Array constructor copies it once.
     */
    @Operation(storeBytecodeIndex = false)
    @ConstantOperand(type = long[].class, dimensions = 0)
    @ConstantOperand(type = boolean.class, name = "terminal")
    @ConstantOperand(type = LocalAccessor.class, name = "outcome")
    public static final class MakeConstantLongArray {
        @Specialization(
                guards = {
                    "closure != null",
                    "closure == cachedClosure",
                    "cachedPrelude != null",
                    "factory == cachedFactory",
                    "standard"
                },
                limit = "3",
                excludeForUncached = true)
        public static boolean doCapturedStandard(
                long[] values,
                boolean terminal,
                LocalAccessor outcome,
                ProtosObjectValue factory,
                @Bind VirtualFrame frame,
                @Bind BytecodeNode bytecode,
                @Bind("capturedClosure(frame)") ProtosClosureValue closure,
                @Cached("closure") ProtosClosureValue cachedClosure,
                @Cached("cachedClosure.preludeOrNullForRuntime()") ProtosPrelude cachedPrelude,
                @Cached("factory") ProtosObjectValue cachedFactory,
                @Cached("frozenStandardFactory(cachedFactory, cachedPrelude)") boolean standard) {
            outcome.setObject(bytecode, frame, new ProtosArrayValue(cachedFactory, values));
            return false;
        }

        @Specialization(
                guards = {
                    "factory == cachedFactory",
                    "prelude == cachedPrelude",
                    "standard"
                },
                limit = "3",
                excludeForUncached = true)
        public static boolean doStandard(
                long[] values,
                boolean terminal,
                LocalAccessor outcome,
                ProtosObjectValue factory,
                @Bind VirtualFrame frame,
                @Bind BytecodeNode bytecode,
                @Bind("admittedPrelude(frame)") ProtosPrelude prelude,
                @Cached("prelude") ProtosPrelude cachedPrelude,
                @Cached("factory") ProtosObjectValue cachedFactory,
                @Cached("frozenStandardFactory(cachedFactory, cachedPrelude)") boolean standard) {
            outcome.setObject(bytecode, frame, new ProtosArrayValue(cachedFactory, values));
            return false;
        }

        @Specialization
        @StoreBytecodeIndex
        @InliningCutoff
        public static boolean doSelected(
                long[] values,
                boolean terminal,
                LocalAccessor outcome,
                Object factory,
                @Bind VirtualFrame frame,
                @Bind ProtosBytecodeRootNode root,
                @Bind BytecodeNode bytecode,
                @Bind("$bytecodeIndex") int bytecodeIndex,
                @Cached ProtosFunctionCallDispatchers.CallNode call) {
            if (MakeArray.selectsStandardFactory(factory, frame)) {
                outcome.setObject(bytecode, frame, new ProtosArrayValue(factory, values));
                return false;
            }
            Object[] supplied = new Object[values.length];
            for (int index = 0; index < values.length; index++) {
                supplied[index] = values[index];
            }
            return MakeArray.invokeFactory(
                    terminal, outcome, factory, supplied,
                    frame, root, bytecode, bytecodeIndex, call);
        }

        @NonIdempotent
        static ProtosClosureValue capturedClosure(VirtualFrame frame) {
            return MakeArray.capturedClosure(frame);
        }

        @NonIdempotent
        static ProtosPrelude admittedPrelude(VirtualFrame frame) {
            return MakeArray.admittedPrelude(frame);
        }

        static boolean frozenStandardFactory(ProtosObjectValue factory, ProtosPrelude prelude) {
            return MakeArray.frozenStandardFactory(factory, prelude);
        }
    }

    /*
     * Constant Float Array construction, with the producer contract and
     * factory specialization of MakeConstantLongArray. The double[]
     * constant is copied once per evaluation.
     */
    @Operation(storeBytecodeIndex = false)
    @ConstantOperand(type = double[].class, dimensions = 0)
    @ConstantOperand(type = boolean.class, name = "terminal")
    @ConstantOperand(type = LocalAccessor.class, name = "outcome")
    public static final class MakeConstantDoubleArray {
        @Specialization(
                guards = {
                    "closure != null",
                    "closure == cachedClosure",
                    "cachedPrelude != null",
                    "factory == cachedFactory",
                    "standard"
                },
                limit = "3",
                excludeForUncached = true)
        public static boolean doCapturedStandard(
                double[] values,
                boolean terminal,
                LocalAccessor outcome,
                ProtosObjectValue factory,
                @Bind VirtualFrame frame,
                @Bind BytecodeNode bytecode,
                @Bind("capturedClosure(frame)") ProtosClosureValue closure,
                @Cached("closure") ProtosClosureValue cachedClosure,
                @Cached("cachedClosure.preludeOrNullForRuntime()") ProtosPrelude cachedPrelude,
                @Cached("factory") ProtosObjectValue cachedFactory,
                @Cached("frozenStandardFactory(cachedFactory, cachedPrelude)") boolean standard) {
            outcome.setObject(bytecode, frame, new ProtosArrayValue(cachedFactory, values));
            return false;
        }

        @Specialization(
                guards = {
                    "factory == cachedFactory",
                    "prelude == cachedPrelude",
                    "standard"
                },
                limit = "3",
                excludeForUncached = true)
        public static boolean doStandard(
                double[] values,
                boolean terminal,
                LocalAccessor outcome,
                ProtosObjectValue factory,
                @Bind VirtualFrame frame,
                @Bind BytecodeNode bytecode,
                @Bind("admittedPrelude(frame)") ProtosPrelude prelude,
                @Cached("prelude") ProtosPrelude cachedPrelude,
                @Cached("factory") ProtosObjectValue cachedFactory,
                @Cached("frozenStandardFactory(cachedFactory, cachedPrelude)") boolean standard) {
            outcome.setObject(bytecode, frame, new ProtosArrayValue(cachedFactory, values));
            return false;
        }

        @Specialization
        @StoreBytecodeIndex
        @InliningCutoff
        public static boolean doSelected(
                double[] values,
                boolean terminal,
                LocalAccessor outcome,
                Object factory,
                @Bind VirtualFrame frame,
                @Bind ProtosBytecodeRootNode root,
                @Bind BytecodeNode bytecode,
                @Bind("$bytecodeIndex") int bytecodeIndex,
                @Cached ProtosFunctionCallDispatchers.CallNode call) {
            if (MakeArray.selectsStandardFactory(factory, frame)) {
                outcome.setObject(bytecode, frame, new ProtosArrayValue(factory, values));
                return false;
            }
            Object[] supplied = new Object[values.length];
            for (int index = 0; index < values.length; index++) {
                supplied[index] = values[index];
            }
            return MakeArray.invokeFactory(
                    terminal, outcome, factory, supplied,
                    frame, root, bytecode, bytecodeIndex, call);
        }

        @NonIdempotent
        static ProtosClosureValue capturedClosure(VirtualFrame frame) {
            return MakeArray.capturedClosure(frame);
        }

        @NonIdempotent
        static ProtosPrelude admittedPrelude(VirtualFrame frame) {
            return MakeArray.admittedPrelude(frame);
        }

        static boolean frozenStandardFactory(ProtosObjectValue factory, ProtosPrelude prelude) {
            return MakeArray.frozenStandardFactory(factory, prelude);
        }
    }

    /*
     * Constant Boolean Array construction, with the producer contract and
     * factory specialization of MakeConstantLongArray. The boolean[]
     * constant is copied once per evaluation.
     */
    @Operation(storeBytecodeIndex = false)
    @ConstantOperand(type = boolean[].class, dimensions = 0)
    @ConstantOperand(type = boolean.class, name = "terminal")
    @ConstantOperand(type = LocalAccessor.class, name = "outcome")
    public static final class MakeConstantBooleanArray {
        @Specialization(
                guards = {
                    "closure != null",
                    "closure == cachedClosure",
                    "cachedPrelude != null",
                    "factory == cachedFactory",
                    "standard"
                },
                limit = "3",
                excludeForUncached = true)
        public static boolean doCapturedStandard(
                boolean[] values,
                boolean terminal,
                LocalAccessor outcome,
                ProtosObjectValue factory,
                @Bind VirtualFrame frame,
                @Bind BytecodeNode bytecode,
                @Bind("capturedClosure(frame)") ProtosClosureValue closure,
                @Cached("closure") ProtosClosureValue cachedClosure,
                @Cached("cachedClosure.preludeOrNullForRuntime()") ProtosPrelude cachedPrelude,
                @Cached("factory") ProtosObjectValue cachedFactory,
                @Cached("frozenStandardFactory(cachedFactory, cachedPrelude)") boolean standard) {
            outcome.setObject(bytecode, frame, new ProtosArrayValue(cachedFactory, values));
            return false;
        }

        @Specialization(
                guards = {
                    "factory == cachedFactory",
                    "prelude == cachedPrelude",
                    "standard"
                },
                limit = "3",
                excludeForUncached = true)
        public static boolean doStandard(
                boolean[] values,
                boolean terminal,
                LocalAccessor outcome,
                ProtosObjectValue factory,
                @Bind VirtualFrame frame,
                @Bind BytecodeNode bytecode,
                @Bind("admittedPrelude(frame)") ProtosPrelude prelude,
                @Cached("prelude") ProtosPrelude cachedPrelude,
                @Cached("factory") ProtosObjectValue cachedFactory,
                @Cached("frozenStandardFactory(cachedFactory, cachedPrelude)") boolean standard) {
            outcome.setObject(bytecode, frame, new ProtosArrayValue(cachedFactory, values));
            return false;
        }

        @Specialization
        @StoreBytecodeIndex
        @InliningCutoff
        public static boolean doSelected(
                boolean[] values,
                boolean terminal,
                LocalAccessor outcome,
                Object factory,
                @Bind VirtualFrame frame,
                @Bind ProtosBytecodeRootNode root,
                @Bind BytecodeNode bytecode,
                @Bind("$bytecodeIndex") int bytecodeIndex,
                @Cached ProtosFunctionCallDispatchers.CallNode call) {
            if (MakeArray.selectsStandardFactory(factory, frame)) {
                outcome.setObject(bytecode, frame, new ProtosArrayValue(factory, values));
                return false;
            }
            Object[] supplied = new Object[values.length];
            for (int index = 0; index < values.length; index++) {
                supplied[index] = values[index];
            }
            return MakeArray.invokeFactory(
                    terminal, outcome, factory, supplied,
                    frame, root, bytecode, bytecodeIndex, call);
        }

        @NonIdempotent
        static ProtosClosureValue capturedClosure(VirtualFrame frame) {
            return MakeArray.capturedClosure(frame);
        }

        @NonIdempotent
        static ProtosPrelude admittedPrelude(VirtualFrame frame) {
            return MakeArray.admittedPrelude(frame);
        }

        static boolean frozenStandardFactory(ProtosObjectValue factory, ProtosPrelude prelude) {
            return MakeArray.frozenStandardFactory(factory, prelude);
        }
    }

    /*
     * Positional Array construction over the already evaluated argument
     * vector (CollectClosureCallArguments or SnapshotClosureCallArguments),
     * with the producer contract and factory specialization of
     * MakeConstantLongArray: the standard construction makes the factory
     * the delegation parent and the values its shallow elements,
     * materialized as guest values; any other selected behavior is invoked
     * with the same vector.
     *
     * It also owns the factory classification shared by the constant
     * constructions: a cached entry is installed only for a factory whose
     * call and construct selections are FROZEN and standard, which a FROZEN
     * chain keeps for good; a factory with a mutable chain, a Closure or any
     * other representation is selected completely on every execution, so a
     * reassignment that preserves the Shape is observed.
     */
    @Operation(storeBytecodeIndex = false)
    @ConstantOperand(type = boolean.class, name = "terminal")
    @ConstantOperand(type = LocalAccessor.class, name = "outcome")
    public static final class MakeArray {
        @Specialization(
                guards = {
                    "closure != null",
                    "closure == cachedClosure",
                    "cachedPrelude != null",
                    "factory == cachedFactory",
                    "standard"
                },
                limit = "3",
                excludeForUncached = true)
        public static boolean doCapturedStandard(
                boolean terminal,
                LocalAccessor outcome,
                ProtosObjectValue factory,
                Object[] values,
                @Bind VirtualFrame frame,
                @Bind BytecodeNode bytecode,
                @Bind("capturedClosure(frame)") ProtosClosureValue closure,
                @Cached("closure") ProtosClosureValue cachedClosure,
                @Cached("cachedClosure.preludeOrNullForRuntime()") ProtosPrelude cachedPrelude,
                @Cached("factory") ProtosObjectValue cachedFactory,
                @Cached("frozenStandardFactory(cachedFactory, cachedPrelude)") boolean standard) {
            outcome.setObject(bytecode, frame,
                    new ProtosArrayValue(cachedFactory, guestElements(values)));
            return false;
        }

        @Specialization(
                guards = {
                    "factory == cachedFactory",
                    "prelude == cachedPrelude",
                    "standard"
                },
                limit = "3",
                excludeForUncached = true)
        public static boolean doStandard(
                boolean terminal,
                LocalAccessor outcome,
                ProtosObjectValue factory,
                Object[] values,
                @Bind VirtualFrame frame,
                @Bind BytecodeNode bytecode,
                @Bind("admittedPrelude(frame)") ProtosPrelude prelude,
                @Cached("prelude") ProtosPrelude cachedPrelude,
                @Cached("factory") ProtosObjectValue cachedFactory,
                @Cached("frozenStandardFactory(cachedFactory, cachedPrelude)") boolean standard) {
            outcome.setObject(bytecode, frame,
                    new ProtosArrayValue(cachedFactory, guestElements(values)));
            return false;
        }

        @Specialization
        @StoreBytecodeIndex
        @InliningCutoff
        public static boolean doSelected(
                boolean terminal,
                LocalAccessor outcome,
                Object factory,
                Object[] values,
                @Bind VirtualFrame frame,
                @Bind ProtosBytecodeRootNode root,
                @Bind BytecodeNode bytecode,
                @Bind("$bytecodeIndex") int bytecodeIndex,
                @Cached ProtosFunctionCallDispatchers.CallNode call) {
            if (selectsStandardFactory(factory, frame)) {
                outcome.setObject(bytecode, frame,
                        new ProtosArrayValue(factory, guestElements(values)));
                return false;
            }
            return invokeFactory(
                    terminal, outcome, factory, values,
                    frame, root, bytecode, bytecodeIndex, call);
        }

        /*
         * The ordinary call of any other selected factory with the supplied
         * positional arguments, in the B5 packet of CallVarargsMethod, its
         * result stored in outcome and classified as every producer's.
         */
        static boolean invokeFactory(
                boolean terminal,
                LocalAccessor outcome,
                Object factory,
                Object[] supplied,
                VirtualFrame frame,
                ProtosBytecodeRootNode root,
                BytecodeNode bytecode,
                int bytecodeIndex,
                ProtosFunctionCallDispatchers.CallNode call) {
            Object result = call.execute(
                    frame, terminal, factory,
                    ProtosFrameArguments.dispatchedCallN(
                            null, ProtosFrameArguments.functionCallMarker(),
                            null, null, null, supplied),
                    null, root);
            return StoreAndTestContinuation.storeResult(
                    outcome,
                    ProtosTailCallContinuation.completeCall(
                            terminal, result, bytecode, bytecodeIndex),
                    frame, bytecode);
        }

        private static List<Object> guestElements(Object[] values) {
            Object[] elements = new Object[values.length];
            for (int index = 0; index < values.length; index++) {
                elements[index] = ProtosNumericValueSupport.guestValue(values[index]);
            }
            return java.util.Arrays.asList(elements);
        }

        /*
         * The compact invocation's own Closure, or null once it published its
         * activation; its Prelude is read from the Closure's final field.
         */
        @NonIdempotent
        static ProtosClosureValue capturedClosure(VirtualFrame frame) {
            return ProtosNumericSelectionNode.capturedClosure(frame);
        }

        /* The caller's Prelude may differ between executions of one site. */
        @NonIdempotent
        static ProtosPrelude admittedPrelude(VirtualFrame frame) {
            return ProtosFrameArguments.admittedPreludeOrNull(frame.getArguments());
        }

        /*
         * Exactly an ordinary object: a Closure takes the Closure branch of
         * Object.call, and other representations are never admitted here.
         */
        static boolean isOrdinaryFactory(Object factory) {
            return ProtosValueLookup.isOrdinaryObject(factory)
                    && ((ProtosObjectValue) factory).usesObjectStorageForRuntime();
        }

        /*
         * Specialization time only: whether the factory's call and construct
         * selections are owned by FROZEN chains and select the canonical
         * Object.call and the original standard construct of the Prelude's
         * Array prototype. A FROZEN chain can change neither its slots nor
         * its parents, so the classification holds for the cached identity.
         */
        @TruffleBoundary
        static boolean frozenStandardFactory(ProtosObjectValue factory, ProtosPrelude prelude) {
            if (prelude == null || !isOrdinaryFactory(factory)) {
                return false;
            }
            try {
                ProtosValueLookup.StructuralSelection call =
                        ProtosValueLookup.selectStructural(factory, "call");
                ProtosValueLookup.StructuralSelection construct =
                        ProtosValueLookup.selectStructural(factory, "construct");
                return call != null
                        && construct != null
                        && call.allOwnersFrozen()
                        && construct.allOwnersFrozen()
                        && ProtosStandardObjectProtocol.isCanonicalStandardCallSelection(
                                call.read(), call.home())
                        && ProtosStandardArrayProtocol.isInstalledStandardConstruct(
                                construct.read(), construct.home())
                        && prelude.arrayPrototype() == construct.home();
            } catch (IllegalStateException | UnsupportedOperationException unavailable) {
                return false;
            }
        }

        /* The complete selection of any other factory. */
        static boolean selectsStandardFactory(Object factory, VirtualFrame frame) {
            return isOrdinaryFactory(factory)
                    && selectsStandard(
                            (ProtosObjectValue) factory, admittedPrelude(frame));
        }

        /*
         * The complete selections, observed without invoking anything. A
         * missing or unsupported selection is not standard: the selected
         * factory is then invoked and produces its own outcome.
         */
        @TruffleBoundary
        static boolean selectsStandard(ProtosObjectValue factory, ProtosPrelude prelude) {
            if (prelude == null) {
                return false;
            }
            try {
                ProtosSlotLookupResult call =
                        ProtosValueLookup.lookup(factory, "call", prelude).orElse(null);
                if (call == null
                        || !ProtosStandardObjectProtocol.isCanonicalStandardCallSelection(
                                call.value(), call.home())) {
                    return false;
                }
                ProtosSlotLookupResult construct =
                        ProtosValueLookup.lookup(factory, "construct", prelude).orElse(null);
                return construct != null
                        && ProtosStandardArrayProtocol.isCanonicalStandardConstructSelection(
                                construct.value(), construct.home(), prelude);
            } catch (IllegalStateException
                    | UnsupportedOperationException
                    | java.util.NoSuchElementException unavailable) {
                return false;
            }
        }
    }


    /*
     * Reads the length of a standard Array or Bytes directly while the
     * standard size is the selected behavior; any other receiver or
     * selection invokes the selected size.
     */
    @Operation(storeBytecodeIndex = true)
    @ConstantOperand(type = LocalAccessor.class, name = "outcome")
    public static final class GetLen {
        static final String SELECTOR = "size";

        @Specialization(guards = "selection.execute(frame, SELECTOR, array)")
        public static boolean doArray(
                LocalAccessor outcome,
                ProtosArrayValue array,
                @Bind VirtualFrame frame,
                @Shared("selection") @Cached StandardIndexedSelectionNode selection,
                @Bind BytecodeNode bytecode) {
            outcome.setLong(bytecode, frame, array.indexedSize());
            return false;
        }

        /* Direct standard Bytes length, as primitive long. */
        @Specialization(guards = "selection.execute(frame, SELECTOR, bytes)")
        public static boolean doBytes(
                LocalAccessor outcome,
                ProtosBytesValue bytes,
                @Bind VirtualFrame frame,
                @Shared("selection") @Cached StandardIndexedSelectionNode selection,
                @Bind BytecodeNode bytecode) {
            outcome.setLong(bytecode, frame, bytes.indexedSize());
            return false;
        }

        @Fallback
        @InliningCutoff
        public static boolean doSelected(
                LocalAccessor outcome,
                Object receiver,
                @Bind VirtualFrame frame,
                @Bind ProtosBytecodeRootNode root,
                @Cached ProtosFunctionCallDispatchers.CallNode call,
                @Bind BytecodeNode bytecode) {
            return StoreAndTestContinuation.storeResult(
                    outcome, invokeSelectedIndexed(
                    frame, SELECTOR, receiver,
                    ProtosFrameArguments.dispatchedCall0(
                            null, ProtosFrameArguments.functionCallMarker(),
                            null, null, null),
                    root, call), frame, bytecode);
        }
    }

    /*
     * Reads a temporary local at its last use and clears it in the same
     * operation, so the value does not stay live in the frame.
     */
    @Operation(storeBytecodeIndex = false)
    @ConstantOperand(type = LocalAccessor.class)
    public static final class LoadAndClearTempLocal {
        @Specialization
        public static Object doObject(
                LocalAccessor accessor,
                @Bind VirtualFrame frame,
                @Bind BytecodeNode bytecode) {
            Object value = accessor.getObject(bytecode, frame);
            accessor.clear(bytecode, frame);
            return value;
        }
    }

    // ---- Lexical lookup and frame-backed bindings ----------------------------------

    /**
     * I113 / a name-keyed lexical access, selected by the
     * builder-fixed {@link ProtosNamedLexicalAccess}: read selection, or
     * write-target selection (which stays separate from its assignment).
     * Both are performed by the single {@link ProtosLexicalSelectionNode}.
     */
    @Operation(storeBytecodeIndex = true)
    @ConstantOperand(type = ProtosNamedLexicalAccess.class, name = "access")
    public static final class AccessNamedLexical {
        /*
         * A true internal operand selects this physical root, without retaining a
         * frame or publishing it. Inline bodies pass their exact owner instead.
         * Only the selector proves a compact access safe; the operand is no proof.
         */
        @Specialization(guards = "currentFrame")
        public static Object compact(
                ProtosNamedLexicalAccess access,
                boolean currentFrame,
                String name,
                @Bind VirtualFrame frame,
                @Bind ProtosBytecodeRootNode root,
                @Bind BytecodeNode bytecode,
                @Bind("$bytecodeIndex") int bytecodeIndex,
                @Cached ProtosLexicalSelectionNode selection) {
            Object[] arguments = frame.getArguments();
            if (ProtosFrameArguments.isUnmaterializedCompactCall(arguments)) {
                Object selected = selection.execute(
                        arguments, name, access.write(), root.lexicalFrameLayoutOrNull());
                if (selected != null) {
                    return selected;
                }
            }
            ProtosActivation activation = lexicalActivation(frame, root);
            root.ensureLexicalFrameView(frame, bytecode, bytecodeIndex, activation);
            return selection.execute(activation, name, access.write());
        }

        @Specialization
        public static Object perform(
                ProtosNamedLexicalAccess access,
                ProtosActivation activation,
                String name,
                @Cached ProtosLexicalSelectionNode selection) {
            return selection.execute(activation, name, access.write());
        }
    }

    /**
     * the assignment of a statically classified module (ROOT) NAME
     * binding through the destination {@code SelectWriteGlobal} selected
     * before the right-hand side ran. The value is stored through the
     * module context's authoritative assignment and is the operation's
     * result. A destination made unwritable meanwhile (a frozen context or a
     * removed binding) signals the assignment Error, exactly as the generic
     * NAME assignment does; no other owner is searched.
     *
     * <p>when the module binding was not the selected binding,
     * the destination is the one live lexical selection chose before the
     * right-hand side; it is assigned without selecting again.
     */
    @Operation(storeBytecodeIndex = true)
    public static final class WriteGlobal {
        @Specialization
        public static Object perform(
                ProtosModuleBindingLocation destination,
                Object value,
                @Bind VirtualFrame frame) {
            assignGlobal(frame.getArguments(), destination, value);
            return value;
        }

        @Specialization
        public static Object selected(
                ResolvedLexicalWriteTarget destination,
                Object value,
                @Bind VirtualFrame frame,
                @Bind ProtosBytecodeRootNode root) {
            return ProtosFrameLocalBindings.assignResolvedLexicalTarget(
                    lexicalActivation(frame, root), destination, destination.nameForRuntime(), value);
        }

        @TruffleBoundary
        private static void assignGlobal(
                Object[] arguments, ProtosModuleBindingLocation destination, Object value) {
            try {
                destination.assign(value);
            } catch (IllegalStateException invalidMutation) {
                throw new ProtosSignalException(
                        ProtosCoreErrors.newError(ProtosFrameArguments.activation(arguments)));
            }
        }
    }

    /*
     * PERF030-I / multiple creation of LOCAL/CELL bindings. The lowerer
     * splits it into one prefix observation, which observes the complete
     * fixed source prefix before any binding is created, followed by one
     * store per name, in source order, into that binding's own storage.
     */

    /** The observed prefix of a multiple creation. */
    @Operation(storeBytecodeIndex = true)
    public static final class ObserveMultipleCreatePrefix {
        @Specialization
        public static Object[] perform(
                int required,
                Object source,
                @Bind VirtualFrame frame,
                @Bind ProtosBytecodeRootNode root) {
            java.util.List<Object> observed =
                    ProtosFrameLocalBindings.multipleCreatePrefixOrNull(required, source);
            if (observed == null) {
                throw lexicalMutationError(lexicalActivation(frame, root));
            }
            return observed.toArray();
        }
    }

    /** Element {@code index} of an observed multiple-creation prefix. */
    @Operation
    public static final class ObservedMultipleCreateValue {
        @Specialization
        public static Object perform(Object[] observed, int index) {
            return observed[index];
        }
    }

    // ---- Slot creation/assignment, members, identity, intrinsics -------------------

    /**
     * StoreGlobal-style fused operation for a discarded, module-owned
     * lexical creation. Its single value operand is evaluated before
     * creation, just as in CreateCurrentLocalSlot.
     *
     * No activation is published on the fast emission path merely to
     * pass it as a separate bytecode operand. Runtime creation uses
     * exactly the existing authority, presence and mutability checks.
     */
    @Operation(storeBytecodeIndex = true)
    @ConstantOperand(type = String.class, name = "name")
    public static final class CreateDiscardedModuleBinding {
        @Specialization
        public static void perform(
                String name,
                Object value,
                @Bind VirtualFrame frame,
                @Bind ProtosBytecodeRootNode root) {
            Object[] arguments = frame.getArguments();
            if (!ProtosFrameArguments.hasPublishedActivation(arguments)) {
                root.requestCallerNeed(NEEDS_EXACT_ACTIVATION);
            }
            ProtosActivation activation =
                    ProtosFrameArguments.activation(frame);
            CreateCurrentLocalSlot.perform(activation, name, value);
        }
    }

    @Operation
    public static final class CreateCurrentLocalSlot {
        @Specialization
        public static Object perform(ProtosActivation activation, String name, Object value) {
            try {
                activation.createCurrentLocalSlotForRuntime(name, value);
            } catch (IllegalStateException invalidMutation) {
                throw new ProtosSignalException(
                        ProtosCoreErrors.newError(activation));
            }
            return value;
        }

        /**
         * I113 (former MultipleCreateLocalSlots): the generic multiple creation.
         * The complete required prefix is observed first, then each binding is
         * created in source order; the first invalid creation signals its Error.
         */
        @Specialization
        public static Object multiple(ProtosActivation activation, String[] names, Object source) {
            int required = names.length;
            List<Object> observed =
                    ProtosFrameLocalBindings.observeMultipleCreatePrefix(activation, required, source);

            for (int index = 0; index < required; index++) {
                try {
                    activation.createCurrentLocalSlotForRuntime(
                            names[index],
                            observed.get(index));
                } catch (IllegalStateException invalidMutation) {
                    /*
                     * Deliberately no rollback: D143 applies ordinary ':'
                     * creation semantics left-to-right after extraction.
                     */
                    throw new ProtosSignalException(
                            ProtosCoreErrors.newError(activation));
                }
            }

            return source;
        }
    }

    /**
     * I113: a local slot mutation selected by the builder-fixed
     * {@link ProtosSlotMutation}: assignment through a resolved
     * lexical write target only. Explicit object attributes use
     * SetAttribute and CreateAttribute instead.
     */
    @Operation
    @ConstantOperand(type = ProtosSlotMutation.class, name = "mutation")
    public static final class MutateLocalSlot {
        /** {@link ProtosSlotMutation#ASSIGN_RESOLVED}. */
        @Specialization
        public static Object resolvedTarget(
                ProtosSlotMutation mutation,
                ProtosActivation activation,
                ResolvedLexicalWriteTarget destination,
                String name,
                Object value) {
            return ProtosFrameLocalBindings.assignResolvedLexicalTarget(
                    activation, destination, name, value);
        }
    }

    /**
     * Physical assignment to an existing local slot. The shape guard proves
     * both property presence and mutation admission. Expression-result and
     * evaluation-order preservation belong to the lowerer.
     */
    @Operation(storeBytecodeIndex = true)
    @ConstantOperand(type = String.class, name = "name")
    public static final class SetAttribute {
        @ForceQuickening
        @Specialization(
                guards = {
                    "isOrdinaryObject(receiver)",
                    "!isRootCallAssignment(name, receiver)",
                    "cachedShape.check(receiver)",
                    "propertyPresent",
                    "isWritableShape(cachedShape)"
                },
                limit = "3")
        public static void direct(
                String name,
                Object value,
                ProtosObjectValue receiver,
                @Cached("receiver.getShape()") Shape cachedShape,
                @Cached("hasProperty(cachedShape, name)") boolean propertyPresent,
                @Cached DynamicObject.PutNode putNode) {
            putNode.execute(receiver, name, value);
        }

        @Specialization(replaces = "direct")
        public static void generic(
                String name,
                Object value,
                Object receiver,
                @Bind VirtualFrame frame) {
            if (receiver instanceof ProtosObjectValue object
                    && !ProtosNumericValueSupport.isCurrentNumber(receiver)) {
                try {
                    object.assignLocalSlot(
                            name, ProtosNumericValueSupport.guestValue(value));
                    return;
                } catch (IllegalStateException invalidMutation) {
                    // Resolve the activation only on the error path.
                }
            }
            throw new ProtosSignalException(
                    ProtosCoreErrors.newError(
                            ProtosFrameArguments.activation(
                                    frame.getArguments())));
        }

        static boolean isOrdinaryObject(Object receiver) {
            return ProtosValueLookup.isOrdinaryObject(receiver);
        }

        static boolean hasProperty(Shape shape, String name) {
            return ProtosValueLookup.hasProperty(shape, name);
        }

        @Idempotent
        static boolean isWritableShape(Shape shape) {
            return !ProtosObjectValue.isFrozenShape(shape);
        }

        /**
         * Whether the assignment targets the root {@code call} slot, whose
         * mutation must go through the lexical binding authority so the
         * canonical call selection is invalidated.
         */
        public static boolean isRootCallAssignment(String name, ProtosObjectValue receiver) {
            return receiver.isRootObject() && "call".equals(name);
        }
    }

    /**
     * explicit creation of a local attribute on a receiver.
     *
     * The creation is not an assignment: duplicate local slots must fail.
     * The slot is established through the authoritative creation path, as a
     * property of the receiver itself.
     */
    @Operation(storeBytecodeIndex = true)
    @ConstantOperand(type = String.class, name = "name")
    public static final class CreateAttribute {
        @Specialization
        public static void perform(
                String name,
                Object value,
                Object receiver,
                @Bind VirtualFrame frame) {
            if (receiver instanceof ProtosObjectValue object
                    && !ProtosNumericValueSupport.isCurrentNumber(receiver)) {
                try {
                    object.createLocalSlot(
                            name, ProtosNumericValueSupport.guestValue(value));
                    return;
                } catch (IllegalStateException invalidMutation) {
                    // Resolve the activation only on the error path.
                }
            }
            throw new ProtosSignalException(
                    ProtosCoreErrors.newError(
                            ProtosFrameArguments.activation(
                                    frame.getArguments())));
        }
    }

    /**
     * Consumes an evaluated expression whose value is not the Sequence
     * result. The child still executes, including errors and effects.
     */
    @Operation
    public static final class DiscardValue {
        @Specialization
        public static void perform(Object ignored) {
        }
    }

    /**
     * Attribute read, with a signed-64 {@code long} specialization.
     *
     * The cached specializations read the receiver's own property through a
     * {@link PropertyGetter} of the cached {@code Shape}, shared by every exact
     * ordinary object with that {@code Shape}. The value always comes from
     * the current physical location; a Closure is extracted freshly.
     * Inherited members of exact ordinary receivers use the shared
     * structural selection of {@link ReadMember}; other representations and
     * absent members use {@link #doGeneric}.
     */
    @Operation(storeBytecodeIndex = false)
    @ConstantOperand(type = String.class, name = "name")
    public static final class GetAttribute {
        @Specialization(
                guards = {
                    "isOrdinaryObject(receiver)",
                    "cachedShape.check(receiver)",
                    "getter != null",
                    "getter.accepts(receiver)"
                },
                rewriteOn = UnexpectedResultException.class,
                limit = "3")
        public static long doLong(
                String name,
                ProtosObjectValue receiver,
                @Cached("receiver.getShape()") Shape cachedShape,
                @Cached("propertyGetter(cachedShape, name)") PropertyGetter getter)
                throws UnexpectedResultException {
            return getter.getLong(receiver);
        }

        @ForceQuickening
        @Specialization(
                guards = {
                    "isOrdinaryObject(receiver)",
                    "cachedShape.check(receiver)",
                    "getter != null",
                    "getter.accepts(receiver)"
                },
                replaces = "doLong",
                limit = "3")
        public static Object doObjectCached(
                String name,
                ProtosObjectValue receiver,
                @Cached("receiver.getShape()") Shape cachedShape,
                @Cached("propertyGetter(cachedShape, name)") PropertyGetter getter) {
            return ProtosValueLookup.extractMember(
                    receiver, receiver, getter.get(receiver));
        }

        /*
         * Inherited member of an exact ordinary receiver, through the same
         * shared structural selection as ReadMember: the receiver's Shape
         * proves it owns no such slot, the selection guards every Shape
         * along the delegation chain, and the real owner's current value is
         * read on every execution.
         */
        @Specialization(
                guards = {
                    "isOrdinaryObject(receiver)",
                    "receiverShape.check(receiver)",
                    "selection != null",
                    "hasSelectionParent(receiver, selection)",
                    "selection.isValid()"
                },
                limit = "3")
        public static Object doInherited(
                String name,
                ProtosObjectValue receiver,
                @Cached("receiver.getShape()") Shape receiverShape,
                @Cached("selectInherited(receiver, name)")
                        ProtosValueLookup.StructuralSelection selection) {
            return ProtosValueLookup.extractMember(
                    receiver, selection.home(), selection.read());
        }

        @Specialization(replaces = {"doLong", "doObjectCached", "doInherited"})
        @StoreBytecodeIndex
        @InliningCutoff
        public static Object doGeneric(
                String name,
                Object receiver,
                @Bind VirtualFrame frame) {
            Object[] arguments = frame.getArguments();
            Object value = ReadMember.readMemberOrNull(
                    receiver,
                    name,
                    ProtosFrameArguments.preludeOrNull(arguments));
            if (value != null) {
                return value;
            }
            return ReadMember.performForActivation(
                    ProtosFrameArguments.activation(arguments),
                    receiver,
                    name);
        }

        static boolean isOrdinaryObject(Object receiver) {
            return ProtosValueLookup.isOrdinaryObject(receiver);
        }

        static PropertyGetter propertyGetter(Shape shape, String name) {
            return ProtosValueLookup.propertyGetterWithFinalAssumption(shape, name);
        }

        static ProtosValueLookup.StructuralSelection selectInherited(
                Object receiver, String name) {
            return ReadMember.selectInherited(receiver, name);
        }

        static boolean hasSelectionParent(
                ProtosObjectValue receiver,
                ProtosValueLookup.StructuralSelection selection) {
            return ProtosValueLookup.hasSelectionParent(receiver, selection);
        }
    }

    /**
     * Ordinary member read. Local reads of an exact ordinary receiver use its
     * cached {@code Shape} and a {@link PropertyGetter} of the receiver's own
     * property; inherited reads share a structural selection of the delegation
     * chain among receivers with the same {@code Shape} and direct parent and
     * read the real owner's current property. No value is cached: the current
     * value is read through the {@code PropertyGetter} and, when it is a
     * Closure, extracted freshly with its receiver and real owner.
     */
    @Operation(storeBytecodeIndex = true)
    @ConstantOperand(type = String.class, name = "name")
    public static final class ReadMember {
        @Specialization(
                guards = {
                    "isOrdinaryObject(receiver)",
                    "receiverShape.check(receiver)",
                    "selection != null",
                    "hasSelectionParent(receiver, selection)",
                    "selection.isValid()"
                },
                limit = "3")
        public static Object guardedSharedInherited(
                String name,
                ProtosActivation activation,
                ProtosObjectValue receiver,
                @Cached("receiver.getShape()") Shape receiverShape,
                @Cached("selectInherited(receiver, name)")
                        ProtosValueLookup.StructuralSelection selection) {
            return ProtosValueLookup.extractMember(
                    receiver, selection.home(), selection.read());
        }

        @Specialization(
                guards = {
                    "isOrdinaryObject(receiver)",
                    "cachedShape.check(receiver)",
                    "getter != null",
                    "getter.accepts(receiver)"
                },
                limit = "3")
        public static Object guardedExactReceiver(
                String name,
                ProtosActivation activation,
                ProtosObjectValue receiver,
                @Cached("receiver.getShape()") Shape cachedShape,
                @Cached("propertyGetter(cachedShape, name)") PropertyGetter getter) {
            return ProtosValueLookup.extractMember(
                    receiver, receiver, getter.get(receiver));
        }

        @Specialization(
                replaces = {
                    "guardedSharedInherited", "guardedExactReceiver"
                })
        public static Object perform(
                String name,
                ProtosActivation activation,
                Object receiver) {
            return ReadMember.performForActivation(
                    activation,
                    receiver,
                    name);
        }

        static boolean isOrdinaryObject(Object receiver) {
            return ProtosValueLookup.isOrdinaryObject(receiver);
        }

        static PropertyGetter propertyGetter(Shape shape, String name) {
            return ProtosValueLookup.propertyGetterWithFinalAssumption(shape, name);
        }

        static ProtosValueLookup.StructuralSelection selectInherited(
                Object receiver, String name) {
            return ProtosValueLookup.selectInheritedStructural(receiver, name);
        }

        static boolean hasSelectionParent(
                ProtosObjectValue receiver,
                ProtosValueLookup.StructuralSelection selection) {
            return ProtosValueLookup.hasSelectionParent(receiver, selection);
        }

        public static Object performForActivation(
                ProtosActivation activation,
                Object receiver,
                String name) {
            ProtosPrelude prelude = activation.prelude().orElse(null);
            try {
                return ProtosValueLookup.readMember(receiver, name, prelude)
                        .orElseThrow(
                                () ->
                                        new ProtosSignalException(
                                                ProtosCoreErrors.newSlotNotFound(activation)));
            } catch (UnsupportedOperationException unsupportedRepresentation) {
                throw new ProtosSignalException(ProtosCoreErrors.newError(activation));
            }
        }

        /**
         * The successful generic read of {@link #perform} through {@code
         * prelude}, or {@code null} when it would raise the absent-member or
         * unsupported-representation Error.
         */
        static Object readMemberOrNull(Object receiver, String name, ProtosPrelude prelude) {
            try {
                return ProtosValueLookup.readMember(receiver, name, prelude).orElse(null);
            } catch (UnsupportedOperationException unsupportedRepresentation) {
                return null;
            }
        }
    }

    /** Guest identity ({@code ===}), or its negation when {@code negated}. */
    @Operation
    @ConstantOperand(type = boolean.class, name = "negated")
    public static final class Identity {
        @Specialization
        public static Object perform(boolean negated, Object left, Object right) {
            return ProtosIdentity.identical(left, right) != negated
                    ? Boolean.TRUE
                    : Boolean.FALSE;
        }
    }

    @Operation
    public static final class ComplementEqualityResult {
        @Specialization
        public static Object perform(ProtosActivation activation, Object equalityResult) {
            Object validated =
                    ProtosStandardBooleanProtocol.requireBooleanResult(
                            equalityResult,
                            activation);
            return validated == Boolean.TRUE
                    ? Boolean.FALSE
                    : Boolean.TRUE;
        }
    }

    /** Reads the invocation receiver directly from its ordinary call header. */
    @Operation(storeBytecodeIndex = false)
    public static final class LoadCurrentReceiver {
        @Specialization
        public static Object perform(@Bind VirtualFrame frame) {
            return ProtosFrameArguments.currentReceiver(frame.getArguments());
        }
    }

    @Operation
    public static final class LoadIntrinsic {
        @Specialization
        public static Object perform(ProtosActivation activation, CanonicalIntrinsic.Kind kind) {
            return switch (kind) {
                case THIS -> activation.receiver();
                case CONTEXT -> activation.context();
            };
        }
    }

    /**
     * Raises a non-local return (PLAT070). An inactive or missing home signals InvalidReturn. An
     * active home is reachable only from the Task that owns it (CALLABLES.md section 14, D177):
     * the owner registered on the home when its invocation was established is compared by
     * identity with the executing Task ({@code null} on both sides for Task-free execution). A
     * mismatch signals the ordinary guest InvalidReturn here, at the {@code ^}; a match throws
     * the original {@link ProtosNonLocalReturnException}, which unwinds directly through
     * generated frames. A valid return performs no stack inspection and attaches nothing.
     */
    @Operation
    public static final class RaiseNonLocalReturn {
        @Specialization
        public static Object perform(ProtosActivation activation, Object value) {
            ProtosReturnHome target =
                    activation.returnHome().orElse(null);
            /*
             * (TruffleSqueak): a finished, unreachable or unobservable
             * destination is an InvalidReturn Error; the target identity is
             * never replaced while propagating.
             */
            if (target == null || !target.isMaterialized() || !target.isActive()
                    || target.ownerTaskForRuntime() != activation.taskForRuntime()) {
                throw new ProtosSignalException(
                        ProtosCoreErrors.newInvalidReturn(activation));
            }
            throw new ProtosNonLocalReturnException(target, value);
        }
    }

    // ---- Number literals ------------------------------------------------------------

    /**
     * I091 / PLAT056 Candidate C: an Integer literal outside the signed-64 range. Its value is an
     * ordinary guest object owned by the executing Prelude, so it is minted on evaluation with
     * that Prelude's BigInteger prototype rather than embedded as a constant shared by every
     * execution domain that runs this code. Signed-64 literals remain primitive constants.
     *
     * <p>{@code value} is the literal's lowering-time exact descriptor, never a guest value. Each
     * evaluation mints a fresh Integer, so no domain shares the object of another. A caller
     * without a Prelude cannot own the value; that is an internal failure, never a guest null.
     */
    @Operation
    public static final class LargeIntegerLiteral {
        @Specialization
        @TruffleBoundary
        public static Object perform(Object caller, java.math.BigInteger value) {
            ProtosPrelude prelude = ProtosFunctionCallDispatchers.callerPrelude(caller);
            if (prelude == null) {
                throw new IllegalStateException(
                        "a large Integer literal requires the executing Core prelude");
            }
            return ProtosNumericValueSupport.integerFromHost(value, prelude);
        }
    }

    // ---- Closure literals and inline Object construction ---------------------------

    /**
     * The genuine Closure of a callback literal that this root normally runs
     * inline, created only when a conditional receiver is not a canonical
     * Boolean and ordinary dispatch must receive the callback. The frame is
     * first bound to its live frame-backed view, so the Closure, which selects
     * the creating root's bindings by name, reads and writes the very frame
     * locals the inline execution uses. Never executed while the receiver is
     * true or false.
     */
    @Operation(storeBytecodeIndex = true)
    public static final class MaterializeSelectedCallback {
        @Specialization
        public static ProtosClosureValue perform(
                ProtosActivation activation,
                ProtosSelectedCallbackPlan callback,
                @Bind VirtualFrame frame,
                @Bind ProtosBytecodeRootNode root,
                @Bind BytecodeNode bytecode,
                @Bind("$bytecodeIndex") int bytecodeIndex) {
            root.ensureLexicalFrameView(frame, bytecode, bytecodeIndex, activation);
            return callback.materialize(activation);
        }
    }

    @Operation
    public static final class MaterializeClosure {
        @Specialization
        public static ProtosClosureValue perform(
                ProtosActivation activation,
                CanonicalClosure definition,
                ProtosClosureExecutionPlanCell executionPlanCell) {
            return new ProtosClosureValue(
                    definition,
                    activation.lexicalEnvironmentForClosureCapture(),
                    activation.receiver(),
                    activation.methodHome().orElse(null),
                    activation.returnHome().orElse(null),
                    activation.prelude().orElse(null),
                    executionPlanCell.plan(),
                    activation.currentModuleKey().orElse(null));
        }
    }

    /**
     * Root-level Closure creation (I092, I112). The creating activation is
     * this root's own frame argument 0, bound from the frame rather than
     * loaded as a Bytecode operand. A published activation is captured
     * unchanged. A creator-context capture publishes its exact activation
     * before its frame can return. With {@code compactCapture} (the I092
     * admission for a root with no frame-owned lexical bindings), the guest
     * Context remains deferred; other roots use {@link CurrentActivation}
     * followed by {@link MaterializeClosure}.
     *
     * <p>PLAT066-C: {@code capturesCreatorContext} is the lowering's
     * classification of this exact Closure ({@code
     * CanonicalCompilationFacts#capturesCreatorContext}). When it is false
     * the Closure has no free cell bindings: it receives its creator's own captured environment
     * and the creator's activation is neither materialized nor published,
     * and no compact capture owner is created. That classification is a
     * recorded divergence from the unconditional capture of {@code
     * CALLABLES.md}, pending an owner decision.
     *
     * <p>: when {@code mayObserveReturnHome} (the
     * lowering's {@code CanonicalCompilationFacts#mayObserveReturnHome}) is
     * also false and the complete lexical subtree is proven independent,
     * the {@code nonCapturing} specializations build the
     * lightweight {@link ProtosClosureValue#experimentalNonCapturing}
     * representation without binding the frame: the creator's activation is
     * not consulted, and its frame header is read only for the admitted
     * Prelude the new Closure retains. A
     * definition that may observe its ReturnHome ({@code ^}) keeps the * construction with the creator's ReturnHome. Inherited-only captures
     * retain the existing chain without creating a new creator context.
     */
    @Operation(storeBytecodeIndex = true)
    @ConstantOperand(type = boolean.class, name = "compactCapture")
    @ConstantOperand(type = boolean.class, name = "capturesCreatorContext")
    @ConstantOperand(type = boolean.class, name = "requiresInheritedLexicalEnvironment")
    @ConstantOperand(type = boolean.class, name = "mayObserveReturnHome")
    public static final class MaterializeCurrentClosure {
        /*
         * Identity PIC over the creating Closure. While the creator is still
         * the compact argument 0 of this root and carries its own Prelude,
         * that Prelude and its defining module key are final fields of that
         * exact creator, so they are cached with the frozen plan cell. The
         * creator's own Prelude is the complete creation-time answer, so no
         * caller recovery is ever needed on this path. A fresh Closure is
         * still created at every execution; no Closure, capture, Task,
         * ReturnHome, caller or activation is cached. A published creator, a
         * different creator or one without its own Prelude takes the general
         * specializations below.
         */
        @Specialization(
                guards = {
                    "!capturesCreatorContext",
                    "!requiresInheritedLexicalEnvironment",
                    "!mayObserveReturnHome",
                    "isSingleContext()",
                    "executionPlanCell == cachedCell",
                    "creator != null",
                    "creator == cachedCreator",
                    "cachedPrelude != null"
                },
                limit = "3")
        public static ProtosClosureValue nonCapturingCreatorCached(
                boolean compactCapture,
                boolean capturesCreatorContext,
                boolean requiresInheritedLexicalEnvironment,
                boolean mayObserveReturnHome,
                CanonicalClosure definition,
                ProtosClosureExecutionPlanCell executionPlanCell,
                @Bind VirtualFrame frame,
                @Bind("admittedCompactCreatorOrNull(frame)") ProtosClosureValue creator,
                @Cached(value = "creator", weak = true) ProtosClosureValue cachedCreator,
                @Cached("executionPlanCell") ProtosClosureExecutionPlanCell cachedCell,
                @Cached("cachedCell.plan()") ProtosClosureExecutionPlan cachedPlan,
                @Cached("cachedCreator.preludeOrNullForRuntime()") ProtosPrelude cachedPrelude,
                @Cached("cachedCreator.definingModuleKeyOrNullForRuntime()")
                        ProtosModuleKey cachedModuleKey) {
            return ProtosClosureValue.experimentalNonCapturing(
                    definition, cachedPlan, cachedModuleKey, cachedPrelude);
        }

        /*
         * each source-literal plan cell is frozen before guest execution.
         * Cache the plan by cell identity so a fresh non-capturing Closure does
         * not reload the cell at each invocation. This cache stores only the
         * plan, never the Closure or any lexical/caller state. Other cells,
         * including projected or rematerialized definitions, remain distinct.
         */
        @Specialization(
                guards = {
                    "!capturesCreatorContext",
                    "!requiresInheritedLexicalEnvironment",
                    "!mayObserveReturnHome",
                    "executionPlanCell == cachedCell"
                },
                replaces = "nonCapturingCreatorCached",
                limit = "3")
        public static ProtosClosureValue nonCapturingCached(
                boolean compactCapture,
                boolean capturesCreatorContext,
                boolean requiresInheritedLexicalEnvironment,
                boolean mayObserveReturnHome,
                CanonicalClosure definition,
                ProtosClosureExecutionPlanCell executionPlanCell,
                @Cached("executionPlanCell") ProtosClosureExecutionPlanCell cachedCell,
                @Cached("cachedCell.plan()") ProtosClosureExecutionPlan cachedPlan,
                @Bind VirtualFrame frame) {
            return ProtosClosureValue.experimentalNonCapturing(
                    definition, cachedPlan,
                    ProtosFrameArguments.admittedDefiningModuleKeyForClosureCreation(
                            frame.getArguments()),
                    ProtosFrameArguments.creatorPreludeForClosureCreation(
                            frame.getArguments()));
        }

        @Specialization(
                guards = {
                    "!capturesCreatorContext",
                    "!requiresInheritedLexicalEnvironment",
                    "!mayObserveReturnHome"
                },
                replaces = "nonCapturingCached")
        public static ProtosClosureValue nonCapturing(
                boolean compactCapture,
                boolean capturesCreatorContext,
                boolean requiresInheritedLexicalEnvironment,
                boolean mayObserveReturnHome,
                CanonicalClosure definition,
                ProtosClosureExecutionPlanCell executionPlanCell,
                @Bind VirtualFrame frame) {
            return ProtosClosureValue.experimentalNonCapturing(
                    definition, executionPlanCell.plan(),
                    ProtosFrameArguments.admittedDefiningModuleKeyForClosureCreation(
                            frame.getArguments()),
                    ProtosFrameArguments.creatorPreludeForClosureCreation(
                            frame.getArguments()));
        }

        @Specialization(guards = {
                "!capturesCreatorContext",
                "requiresInheritedLexicalEnvironment || mayObserveReturnHome",
                "hasPublishedActivation(frame)"})
        public static ProtosClosureValue publishedWithoutCreatorContext(
                boolean compactCapture,
                boolean capturesCreatorContext,
                boolean requiresInheritedLexicalEnvironment,
                boolean mayObserveReturnHome,
                CanonicalClosure definition,
                ProtosClosureExecutionPlanCell executionPlanCell,
                @Bind VirtualFrame frame) {
            ProtosActivation activation =
                    ProtosFrameArguments.publishedActivation(frame.getArguments());
            return new ProtosClosureValue(
                    definition,
                    activation.capturedLexicalEnvironmentForRuntime(),
                    activation.receiver(),
                    activation.methodHomeOrNullForRuntime(),
                    activation.returnHomeOrNullForRuntime(),
                    activation.preludeOrNullForRuntime(),
                    executionPlanCell.plan(),
                    activation.currentModuleKey().orElse(null));
        }

        @Specialization(guards = {
                "!capturesCreatorContext",
                "requiresInheritedLexicalEnvironment || mayObserveReturnHome",
                "!hasPublishedActivation(frame)"})
        public static ProtosClosureValue compactWithoutCreatorContext(
                boolean compactCapture,
                boolean capturesCreatorContext,
                boolean requiresInheritedLexicalEnvironment,
                boolean mayObserveReturnHome,
                CanonicalClosure definition,
                ProtosClosureExecutionPlanCell executionPlanCell,
                @Bind VirtualFrame frame) {
            return ProtosFrameArguments.materializeCompactClosure(
                    frame.getArguments(), definition, executionPlanCell,
                    false, requiresInheritedLexicalEnvironment, mayObserveReturnHome);
        }

        @Specialization(guards = {
                "capturesCreatorContext",
                "hasPublishedActivation(frame)",
                "hasMaterializedCaptureContext(frame)"})
        public static ProtosClosureValue publishedMaterialized(
                boolean compactCapture,
                boolean capturesCreatorContext,
                boolean requiresInheritedLexicalEnvironment,
                boolean mayObserveReturnHome,
                CanonicalClosure definition,
                ProtosClosureExecutionPlanCell executionPlanCell,
                @Bind VirtualFrame frame) {
            ProtosActivation activation =
                    ProtosFrameArguments.publishedActivation(frame.getArguments());
            return new ProtosClosureValue(
                    definition,
                    activation.lexicalEnvironmentForMaterializedContextCaptureForRuntime(),
                    activation.receiver(),
                    activation.methodHomeOrNullForRuntime(),
                    activation.returnHomeOrNullForRuntime(),
                    activation.preludeOrNullForRuntime(),
                    executionPlanCell.plan(),
                    activation.currentModuleKey().orElse(null));
        }

        @Specialization(guards = {
                "capturesCreatorContext",
                "hasPublishedActivation(frame)",
                "!hasMaterializedCaptureContext(frame)"})
        public static ProtosClosureValue publishedGeneral(
                boolean compactCapture,
                boolean capturesCreatorContext,
                boolean requiresInheritedLexicalEnvironment,
                boolean mayObserveReturnHome,
                CanonicalClosure definition,
                ProtosClosureExecutionPlanCell executionPlanCell,
                @Bind VirtualFrame frame) {
            ProtosActivation activation =
                    ProtosFrameArguments.publishedActivation(frame.getArguments());
            return new ProtosClosureValue(
                    definition,
                    activation.lexicalEnvironmentForClosureCapture(),
                    activation.receiver(),
                    activation.methodHomeOrNullForRuntime(),
                    activation.returnHomeOrNullForRuntime(),
                    activation.preludeOrNullForRuntime(),
                    executionPlanCell.plan(),
                    activation.currentModuleKey().orElse(null));
        }

        @Specialization(guards = {
                "capturesCreatorContext",
                "compactCapture",
                "!hasPublishedActivation(frame)"})
        public static ProtosClosureValue compact(
                boolean compactCapture,
                boolean capturesCreatorContext,
                boolean requiresInheritedLexicalEnvironment,
                boolean mayObserveReturnHome,
                CanonicalClosure definition,
                ProtosClosureExecutionPlanCell executionPlanCell,
                @Bind VirtualFrame frame) {
            return ProtosFrameArguments.materializeCompactClosure(
                    frame.getArguments(), definition, executionPlanCell,
                    true, requiresInheritedLexicalEnvironment, mayObserveReturnHome);
        }

        @Specialization(guards = {
                "capturesCreatorContext",
                "!compactCapture",
                "!hasPublishedActivation(frame)"})
        public static ProtosClosureValue rich(
                boolean compactCapture,
                boolean capturesCreatorContext,
                boolean requiresInheritedLexicalEnvironment,
                boolean mayObserveReturnHome,
                CanonicalClosure definition,
                ProtosClosureExecutionPlanCell executionPlanCell,
                @Bind VirtualFrame frame) {
            return MaterializeClosure.perform(
                    ProtosFrameArguments.activation(frame),
                    definition,
                    executionPlanCell);
        }

        @NonIdempotent
        static ProtosClosureValue admittedCompactCreatorOrNull(VirtualFrame frame) {
            return ProtosFrameArguments.admittedCompactCreatorOrNull(frame.getArguments());
        }

        @Idempotent
        static boolean isSingleContext() {
            return ProtosCallUnaryMethodNode.isSingleContext();
        }

        @NonIdempotent
        static boolean hasMaterializedCaptureContext(VirtualFrame frame) {
            ProtosActivation activation =
                    ProtosFrameArguments.publishedActivation(frame.getArguments());
            return activation.canCaptureMaterializedContextForRuntime();
        }

        @NonIdempotent
        static boolean hasPublishedActivation(VirtualFrame frame) {
            return ProtosFrameArguments.hasPublishedActivation(frame.getArguments());
        }
    }

    @Operation
    public static final class NewConstructedObject {
        @Specialization
        public static ProtosObjectValue perform(Object parent) {
            return new ProtosObjectValue(parent);
        }
    }

    @Operation
    public static final class NewObjectConstructionActivation {
        @Specialization
        public static ProtosActivation perform(ProtosActivation enclosing, ProtosObjectValue object) {
            return ProtosActivation.forObjectConstruction(object, enclosing);
        }
    }

    @Operation
    public static final class ComposeLocalSlots {
        @Specialization
        public static ProtosObjectValue perform(
                ProtosActivation activation,
                Object sourceValue,
                ComposeReservedNames reservedNames) {
            if (!(sourceValue instanceof ProtosObjectValue source)
                    || ProtosNumericValueSupport.isCurrentNumber(sourceValue)) {
                throw new ProtosSignalException(ProtosCoreErrors.newError(activation));
            }

            ProtosObjectValue target = activation.context();
            try {
                target.composeLocalSlotsFrom(
                        source, reservedNames.indexedNamesForComposition());
            } catch (IllegalStateException failure) {
                throw new ProtosSignalException(ProtosCoreErrors.newError(activation));
            }
            return target;
        }
    }

    // ---- Map construction ----------------------------------------------------------

    /**
     * Constructs the Map of a %{...} literal directly from its evaluated
     * factory, with the organization of MakeArray: D239-C1 requires the
     * factory to select exactly the standard Object.call and the standard
     * Map.construct whose home is the observing Prelude's canonical Map
     * binding, and the new empty Map has the actual factory as its parent.
     * No guest call is made and no CallRequest is prepared.
     *
     * A cached entry is installed only for an ordinary factory whose call and
     * construct selections are owned by FROZEN chains and are standard for
     * the cached Prelude; a FROZEN chain keeps that classification, and the
     * Prelude bindings, including Map, are FROZEN. Every other factory is
     * selected completely on every execution. Unlike MakeArray, an
     * incompatible factory is never invoked: it raises the ordinary Error,
     * with the authentic activation obtained only on that path.
     */
    @Operation(storeBytecodeIndex = false)
    public static final class MakeMap {
        @Specialization(
                guards = {
                    "closure != null",
                    "closure == cachedClosure",
                    "cachedPrelude != null",
                    "factory == cachedFactory",
                    "standard"
                },
                limit = "3",
                excludeForUncached = true)
        public static ProtosMapValue doCapturedStandard(
                ProtosObjectValue factory,
                @Bind VirtualFrame frame,
                @Bind("capturedClosure(frame)") ProtosClosureValue closure,
                @Cached("closure") ProtosClosureValue cachedClosure,
                @Cached("cachedClosure.preludeOrNullForRuntime()") ProtosPrelude cachedPrelude,
                @Cached("factory") ProtosObjectValue cachedFactory,
                @Cached("frozenStandardFactory(cachedFactory, cachedPrelude)") boolean standard) {
            return ProtosStandardMapProtocol.newStandardMap(cachedFactory);
        }

        @Specialization(
                guards = {
                    "factory == cachedFactory",
                    "prelude == cachedPrelude",
                    "standard"
                },
                limit = "3",
                excludeForUncached = true)
        public static ProtosMapValue doStandard(
                ProtosObjectValue factory,
                @Bind VirtualFrame frame,
                @Bind("admittedPrelude(frame)") ProtosPrelude prelude,
                @Cached("prelude") ProtosPrelude cachedPrelude,
                @Cached("factory") ProtosObjectValue cachedFactory,
                @Cached("frozenStandardFactory(cachedFactory, cachedPrelude)") boolean standard) {
            return ProtosStandardMapProtocol.newStandardMap(cachedFactory);
        }

        @Specialization
        @StoreBytecodeIndex
        @InliningCutoff
        public static ProtosMapValue doSelected(
                Object factory,
                @Bind VirtualFrame frame,
                @Bind ProtosBytecodeRootNode root) {
            ProtosMapValue constructed =
                    ProtosStandardMapProtocol.standardMapConstructionOrNull(
                            factory, admittedPrelude(frame));
            if (constructed != null) {
                return constructed;
            }
            return ProtosStandardMapProtocol.constructStandardForMapConstruction(
                    factory, lexicalActivation(frame, root));
        }

        /* As MakeArray.capturedClosure. */
        @NonIdempotent
        static ProtosClosureValue capturedClosure(VirtualFrame frame) {
            return MakeArray.capturedClosure(frame);
        }

        /* As MakeArray.admittedPrelude. */
        @NonIdempotent
        static ProtosPrelude admittedPrelude(VirtualFrame frame) {
            return MakeArray.admittedPrelude(frame);
        }

        /*
         * Specialization time only: whether the ordinary factory's call and
         * construct selections are owned by FROZEN chains, select the
         * canonical Object.call and the standard Map.construct homed at the
         * Prelude's canonical Map binding. The construct chain runs from the
         * factory to that home, so the factory delegates to it.
         */
        @TruffleBoundary
        static boolean frozenStandardFactory(ProtosObjectValue factory, ProtosPrelude prelude) {
            if (prelude == null || !MakeArray.isOrdinaryFactory(factory)) {
                return false;
            }
            try {
                ProtosValueLookup.StructuralSelection call =
                        ProtosValueLookup.selectStructural(factory, "call");
                ProtosValueLookup.StructuralSelection construct =
                        ProtosValueLookup.selectStructural(factory, "construct");
                return call != null
                        && construct != null
                        && call.allOwnersFrozen()
                        && construct.allOwnersFrozen()
                        && ProtosStandardObjectProtocol.isCanonicalStandardCallSelection(
                                call.read(), call.home())
                        && ProtosStandardMapProtocol.isCanonicalStandardConstructSelection(
                                construct.read(), construct.home(), prelude);
            } catch (IllegalStateException | UnsupportedOperationException unavailable) {
                return false;
            }
        }
    }

    @Operation
    public static final class PrepareMapInitialDefinition {
        @Specialization
        public static PreparedMapInitialDefinition perform(
                ProtosActivation activation,
                Object map,
                Object key,
                Object value) {
            return new PreparedMapInitialDefinition(
                    map,
                    key,
                    value,
                    activation);
        }
    }

    /*
     * I113: the comparison phases shared by every prepared Map protocol. Each
     * specialization delegates to its concrete state type; the builder fixes
     * the phase constant, so every site stays monomorphic.
     */

    // ---- Supplied argument vectors -------------------------------------------------

    @Operation
    public static final class CreateSuppliedArgumentVector {
        @Specialization
        public static PreparedArgumentVector perform() {
            return new PreparedArgumentVector();
        }
    }

    @Operation
    public static final class AppendSuppliedArgument {
        @Specialization
        public static void perform(PreparedArgumentVector vector, Object value) {
            vector.append(value);
        }
    }

    /**
     * I143: append an already evaluated named argument.
     * The label is a source-fixed constant, not a guest expression.
     * Parameter association occurs after callee selection.
     */
    @Operation
    @ConstantOperand(type = String.class, name = "name")
    public static final class AppendNamedSuppliedArgument {
        @Specialization
        public static void perform(
                String name,
                PreparedArgumentVector vector,
                Object value) {
            vector.appendNamed(name, value);
        }
    }

    @Operation
    public static final class AppendSpreadSuppliedArgument {
        @Specialization
        public static void perform(
                PreparedArgumentVector vector,
                Object value,
                Object caller) {
            if (!vector.appendSpreadArrayOrFalse(value)) {
                ProtosActivation exactCaller =
                        ProtosFunctionCallDispatchers.exactCaller(null, caller);
                throw new com.guillermomolina.protos.runtime.ProtosSignalException(
                        com.guillermomolina.protos.runtime.ProtosCoreErrors
                                .newError(exactCaller));
            }
        }

        public static void performForActivation(
                PreparedArgumentVector vector,
                Object value,
                ProtosActivation caller) {
            vector.appendSpread(value, caller);
        }
}


    /**
     * D237 / cold name-aware call after operand evaluation.
     *
     * The argument vector retains both positional values and source
     * labels. Only this operation freezes the P,K packet. Ordinary
     * positional Call*Method operations are unaffected.
     */
    @Operation(storeBytecodeIndex = true)
    public static final class CallNamedMethod {
        @Specialization
        public static Object perform(
                Object callable,
                PreparedArgumentVector supplied,
                @Bind VirtualFrame frame) {
            return ProtosFunctionCallDispatchers.callNamed(
                    callable,
                    supplied,
                    ProtosFrameArguments.activation(frame));
        }
    }

    // ---- Call arguments ------------------------------------------------------------

    /** I108-B: positional arguments, already evaluated left to right. */
    @Operation
    public static final class CollectClosureCallArguments {
        @Specialization
        public static Object[] perform(@Variadic Object[] arguments) {
            return arguments;
        }
    }

    /** I108-B: completed spread expansion, without repeating evaluation. */
    @Operation
    public static final class SnapshotClosureCallArguments {
        @Specialization
        public static Object[] perform(PreparedArgumentVector vector) {
            return vector.positionalArray();
        }
    }


    /**
     * I115: the BufferedWriter C-prime step gate. The gate produces a Boolean
     * carrier; any other value is an internal protocol violation.
     */
    @Operation
    public static final class IsBufferedByteWriterStepRequired {
        @Specialization
        public static boolean doBoolean(boolean required) {
            return required;
        }

        @Fallback
        public static boolean doInvalid(Object value) {
            throw new IllegalStateException(
                    "BufferedWriter C-prime step gate produced a non-Boolean value");
        }
    }

    /**
     * I115: whether the TextReader C-prime advance needs lower input. The
     * advance produces an {@link ProtosTextReaderCPrimeExecution.Advance}
     * carrier; any other value is an internal protocol violation.
     */
    @Operation
    public static final class IsTextReaderNeedInput {
        @Specialization
        public static boolean doAdvance(ProtosTextReaderCPrimeExecution.Advance state) {
            return state.needsInput();
        }

        @Fallback
        public static boolean doInvalid(Object value) {
            throw new IllegalStateException(
                    "TextReader C-prime advance produced an invalid carrier");
        }
    }

    /*
     * Structured-protocol admission and preparation. Each protocol owns one
     * concrete admission operation and one concrete preparation operation;
     * the builder emits the operation of the protocol it lowers, so every
     * site delegates directly to that protocol's capability.
     */

    /** Whether {@code prepared} is the structured Boolean protocol. */
    @Operation
    public static final class IsStructuredBoolean {
        @Specialization
        public static boolean perform(StructuredNativeInvocation prepared) {
            return prepared.isStructuredBoolean();
        }
    }

    /** Prepares the structured Boolean state; precondition {@code IsStructuredBoolean}. */
    @Operation
    public static final class PrepareStructuredBoolean {
        @Specialization
        public static Object perform(StructuredNativeInvocation prepared) {
            return prepared.prepareStructuredBoolean();
        }
    }

    /** Whether {@code prepared} is the structured CaseOf protocol. */
    @Operation
    public static final class IsStructuredCaseOf {
        @Specialization
        public static boolean perform(StructuredNativeInvocation prepared) {
            return prepared.isStructuredCaseOf();
        }
    }

    /** Prepares the structured CaseOf state; precondition {@code IsStructuredCaseOf}. */
    @Operation
    public static final class PrepareStructuredCaseOf {
        @Specialization
        public static Object perform(StructuredNativeInvocation prepared) {
            return prepared.prepareStructuredCaseOf();
        }
    }

    /**
     * The ordinary {@code caseOf} send of an already evaluated subject and
     * case argument, run in the enclosing root. When the behavior selected
     * for {@code caseOf} on the subject is the standard Object.caseOf body and
     * the argument is a Map, the insertion-ordered association snapshot is
     * established in {@code state} and the operation answers true; the
     * lowering then selects and invokes each reached matcher and the
     * committed action through the ordinary specialized invocation
     * operations. Any other selection or argument stores nothing and answers
     * false, and the lowering keeps the ordinary send, which signals every
     * argument Error itself. Nothing is invoked here.
     */
    @Operation(storeBytecodeIndex = true)
    @ConstantOperand(type = LocalAccessor.class, name = "state")
    public static final class GetCaseOfSnapshot {
        /*
         * An ordinary object subject that does not own caseOf: the inherited
         * structural selection is cached per receiver Shape and shared by
         * every receiver with the same direct parent, exactly as the
         * standard Array each selection. An all-FROZEN chain is classified
         * once; otherwise the selected value is re-read and classified on
         * each execution, so an in-place slot rewrite is observed.
         */
        @Specialization(
                guards = {
                    "subject.usesObjectStorageForRuntime()",
                    "subjectShape.check(subject)",
                    "selection != null",
                    "hasSelectionParent(subject, selection)",
                    "frozen || selection.isValid()"
                },
                limit = "3",
                excludeForUncached = true)
        public static boolean doObject(
                LocalAccessor state,
                ProtosObjectValue subject,
                ProtosMapValue cases,
                ProtosActivation activation,
                @Bind VirtualFrame frame,
                @Bind BytecodeNode bytecode,
                @Cached("subject.getShape()") Shape subjectShape,
                @Cached("selectInherited(subject, subjectShape)")
                        ProtosValueLookup.StructuralSelection selection,
                @Cached("allOwnersFrozen(selection)") boolean frozen,
                @Cached("frozenStandard(frozen, selection)") boolean frozenStandard) {
            if (frozen ? !frozenStandard : !isStandard(selection)) {
                return false;
            }
            state.setObject(bytecode, frame, new PreparedCaseOfCall(subject, cases, activation));
            return true;
        }

        /*
         * A primitive carrier subject (Integer, Float or Boolean). The
         * selection from its delegation parent is cached per carrier class
         * and Prelude, and is used only while that Prelude still delegates
         * the carrier to the same parent object and the selection chain is
         * valid, so a rebound prototype binding is observed.
         */
        @Specialization(
                guards = {
                    "isPrimitiveCarrier(subject)",
                    "subject.getClass() == subjectClass",
                    "prelude == cachedPrelude",
                    "selection != null",
                    "boundParent(subjectClass, binding, selection)",
                    "frozen || selection.isValid()"
                },
                limit = "3",
                excludeForUncached = true)
        public static boolean doPrimitive(
                LocalAccessor state,
                Object subject,
                ProtosMapValue cases,
                ProtosActivation activation,
                @Bind VirtualFrame frame,
                @Bind BytecodeNode bytecode,
                @Bind("preludeOf(frame)") ProtosPrelude prelude,
                @Cached("subject.getClass()") Class<?> subjectClass,
                @Cached("prelude") ProtosPrelude cachedPrelude,
                @Cached("selectFromCarrierParent(subject, cachedPrelude)")
                        ProtosValueLookup.StructuralSelection selection,
                @Cached("carrierBinding(subject, cachedPrelude)")
                        ProtosValueLookup.StructuralSelection binding,
                @Cached("allOwnersFrozen(selection)") boolean frozen,
                @Cached("frozenStandard(frozen, selection)") boolean frozenStandard) {
            if (frozen ? !frozenStandard : !isStandard(selection)) {
                return false;
            }
            state.setObject(bytecode, frame, new PreparedCaseOfCall(subject, cases, activation));
            return true;
        }

        /*
         * Every other subject representation, and sites past the cached
         * limit: the complete ordinary selection. It replaces no cached
         * entry.
         */
        @Specialization
        @InliningCutoff
        public static boolean doGeneric(
                LocalAccessor state,
                Object subject,
                ProtosMapValue cases,
                ProtosActivation activation,
                @Bind VirtualFrame frame,
                @Bind BytecodeNode bytecode) {
            if (!selectsStandard(subject, preludeOf(frame))) {
                return false;
            }
            state.setObject(bytecode, frame, new PreparedCaseOfCall(subject, cases, activation));
            return true;
        }

        /* A non-Map argument keeps the ordinary send, which signals its Error. */
        @Fallback
        public static boolean doOther(
                LocalAccessor state, Object subject, Object cases, Object activation) {
            return false;
        }

        /* The caller's Prelude is observed from the frame on each execution. */
        @NonIdempotent
        static ProtosPrelude preludeOf(VirtualFrame frame) {
            return ProtosFrameArguments.admittedPreludeOrNull(frame.getArguments());
        }

        /*
         * Specialization time only: the structural selection of the Prelude
         * binding that delegates an Integer or Float carrier to its
         * prototype, as the standard numeric operator selection observes it;
         * null for a Boolean, whose parent is the fixed root Object.
         */
        static ProtosValueLookup.StructuralSelection carrierBinding(
                Object subject, ProtosPrelude prelude) {
            if (subject instanceof Boolean) {
                return null;
            }
            return ProtosNumericSelectionNode.selectBinding(
                    prelude,
                    subject instanceof Long
                            ? ProtosNumericSelectionNode.SMALL_INTEGER_BINDING
                            : ProtosNumericSelectionNode.FLOAT_BINDING);
        }

        /*
         * Whether the carrier's prototype binding still selects the origin
         * of the cached caseOf selection: a Boolean's parent is fixed;
         * otherwise the binding chain must be unchanged and bound to that
         * prototype. Reads one slot; invokes nothing.
         */
        @NonIdempotent
        static boolean boundParent(
                Class<?> subjectClass,
                ProtosValueLookup.StructuralSelection binding,
                ProtosValueLookup.StructuralSelection selection) {
            if (subjectClass == Boolean.class) {
                return selection.origin() == ProtosObjectValue.rootObject();
            }
            // An absent Integer or Float prototype binding never admits.
            return binding != null
                    && binding.isValid()
                    && binding.read() == selection.origin();
        }

        static boolean isPrimitiveCarrier(Object subject) {
            return subject instanceof Long
                    || subject instanceof Double
                    || subject instanceof Boolean;
        }

        static boolean hasSelectionParent(
                ProtosObjectValue subject,
                ProtosValueLookup.StructuralSelection selection) {
            return ProtosValueLookup.hasSelectionParent(subject, selection);
        }

        static boolean allOwnersFrozen(ProtosValueLookup.StructuralSelection selection) {
            return selection != null && selection.allOwnersFrozen();
        }

        static boolean isStandard(ProtosValueLookup.StructuralSelection selection) {
            return ProtosStandardObjectProtocol.isCanonicalStandardCaseOfSelection(
                    selection.read(), selection.home());
        }

        /* Specialization time only: the classification of an all-FROZEN chain. */
        static boolean frozenStandard(
                boolean frozen, ProtosValueLookup.StructuralSelection selection) {
            return frozen && isStandard(selection);
        }

        /* Specialization time only: null when the subject owns caseOf. */
        @TruffleBoundary
        static ProtosValueLookup.StructuralSelection selectInherited(
                ProtosObjectValue subject, Shape subjectShape) {
            if (!subject.usesObjectStorageForRuntime()
                    || ProtosValueLookup.hasProperty(subjectShape, "caseOf")) {
                return null;
            }
            Object parent = subject.parent().orElse(null);
            return parent == null ? null : ProtosValueLookup.selectStructural(parent, "caseOf");
        }

        /* Specialization time only: null without an observing Prelude. */
        @TruffleBoundary
        static ProtosValueLookup.StructuralSelection selectFromCarrierParent(
                Object subject, ProtosPrelude prelude) {
            if (prelude == null) {
                return null;
            }
            try {
                return ProtosValueLookup.delegationParent(subject, prelude)
                        .map(parent -> ProtosValueLookup.selectStructural(parent, "caseOf"))
                        .orElse(null);
            } catch (RuntimeException unavailable) {
                return null;
            }
        }

        @TruffleBoundary
        static boolean selectsStandard(Object subject, ProtosPrelude prelude) {
            try {
                return ProtosValueLookup.lookup(subject, "caseOf", prelude)
                        .map(found -> ProtosStandardObjectProtocol
                                .isCanonicalStandardCaseOfSelection(found.value(), found.home()))
                        .orElse(false);
            } catch (UnsupportedOperationException unsupportedRepresentation) {
                return false;
            }
        }
    }

    /**
     * The matcher of the current caseOf association, read directly from the
     * observed snapshot. A source root sends it {@code match} through
     * {@code GetMethod} and {@code CallUnaryMethod}; no call request is
     * prepared.
     */
    @Operation
    public static final class StructuredMatcher {
        @Specialization
        public static Object caseOf(PreparedCaseOfCall prepared) {
            return prepared.currentMatcher();
        }

    }

    /**
     * The action committed by a caseOf selection. With no successful matcher
     * the ordinary no-selection Error is signalled here.
     */
    @Operation(storeBytecodeIndex = true)
    public static final class CaseOfSelectedAction {
        @Specialization
        public static Object perform(PreparedCaseOfCall prepared) {
            return prepared.selectedAction();
        }
    }

    /** Whether a caseOf matcher has succeeded, committing its action. */
    @Operation
    public static final class CaseOfCommitted {
        @Specialization
        public static boolean perform(PreparedCaseOfCall prepared) {
            return prepared.committed();
        }
    }

    /**
     * Whether the committed caseOf action receives exactly {@code count}
     * consumed captures.
     */
    @Operation
    @ConstantOperand(type = int.class, name = "count")
    public static final class CaseOfSelectedCaptureCount {
        @Specialization
        public static boolean perform(int count, PreparedCaseOfCall prepared) {
            return prepared.selectedCaptureCount() == count;
        }
    }

    /**
     * The consumed capture at {@code position} of a committed caseOf action
     * whose capture count the emitted code has already established.
     */
    @Operation
    @ConstantOperand(type = int.class, name = "position")
    public static final class CaseOfSelectedCapture {
        @Specialization
        public static Object perform(int position, PreparedCaseOfCall prepared) {
            return prepared.selectedCapture(position);
        }
    }

    /**
     * Whether the committed caseOf {@code action} is exactly the Closure
     * {@code retained} that this root created for a written case entry, and
     * invoking it would run that Closure's own source body through the
     * canonical standard Closure call: so that body, emitted in this root by
     * the compiler, may run in its place, binding exactly the {@code count}
     * captures its fixed parameters receive. It never invokes anything and
     * signals nothing; a false answer keeps the ordinary invocation.
     *
     * <p>The Closure definition is final, so identity with the retained
     * value proves the emitted source definition. The canonical {@code call}
     * selection is the established one of the nilary call site: a cached
     * Shape proves only the presence of a local {@code call} slot, whose
     * value is checked on every execution.
     */
    @Operation(storeBytecodeIndex = false)
    @ConstantOperand(type = int.class, name = "count")
    public static final class IsInlineCaseAction {
        @Specialization(
                guards = {
                    "action == retained",
                    "isSourceFunction(retained)",
                    "!retained.requiresContextLocalExecutionProjectionForRuntime()",
                    "cachedShape.check(retained)"
                },
                limit = "2")
        public static boolean doCached(
                int count,
                PreparedCaseOfCall prepared,
                ProtosClosureValue action,
                ProtosClosureValue retained,
                @Cached(value = "rootCallSelection()", allowUncached = true)
                        ProtosStandardObjectProtocol.CanonicalRootCallSelection rootSelection,
                @Cached(value = "retained.getShape()", allowUncached = true)
                        Shape cachedShape,
                @Cached(value = "callSlotGetter(cachedShape)", allowUncached = true)
                        PropertyGetter cachedLocalCallGetter) {
            return prepared.selectedCaptureCount() == count
                    && ProtosFunctionCallDispatchers.hasCanonicalCallSelectionForShape(
                            retained, rootSelection, cachedShape, cachedLocalCallGetter);
        }

        /* Another committed action (a dynamic or reassociated one) is never inlined. */
        @Specialization(guards = "action != retained")
        public static boolean doDistinct(
                int count, PreparedCaseOfCall prepared, Object action, Object retained) {
            return false;
        }

        @Specialization(replaces = "doCached")
        public static boolean doGeneric(
                int count, PreparedCaseOfCall prepared, Object action, Object retained) {
            return action == retained
                    && prepared.selectedCaptureCount() == count
                    && retained instanceof ProtosClosureValue closure
                    && isSourceFunction(closure)
                    && !closure.requiresContextLocalExecutionProjectionForRuntime()
                    && ProtosFunctionCallDispatchers.hasCanonicalCallSelection(
                            closure, rootCallSelection());
        }

        static boolean isSourceFunction(ProtosClosureValue closure) {
            return ProtosFunctionCallDispatchers.CallNode.isSourceFunction(closure);
        }

        static ProtosStandardObjectProtocol.CanonicalRootCallSelection rootCallSelection() {
            return ProtosStandardObjectProtocol.canonicalRootCallSelectionOrNullForRuntime();
        }

        static PropertyGetter callSlotGetter(Shape shape) {
            return ProtosFunctionCallDispatchers.callSlotGetter(shape);
        }
    }

    /** The captures the committed caseOf action receives, in order. */
    @Operation
    public static final class CaseOfSelectedArguments {
        @Specialization
        public static Object[] perform(PreparedCaseOfCall prepared) {
            return prepared.selectedArguments();
        }
    }

    /** Whether {@code prepared} is the structured ObjectCall protocol. */
    @Operation
    public static final class IsStructuredObjectCall {
        @Specialization
        public static boolean perform(StructuredNativeInvocation prepared) {
            return prepared.isStructuredObjectCall();
        }
    }

    /** Prepares the structured ObjectCall state; precondition {@code IsStructuredObjectCall}. */
    @Operation
    public static final class PrepareStructuredObjectCall {
        @Specialization
        public static Object perform(StructuredNativeInvocation prepared) {
            return prepared.prepareStructuredObjectCall();
        }
    }

    /** Whether {@code prepared} is the structured ImportCall protocol. */
    @Operation
    public static final class IsStructuredImportCall {
        @Specialization
        public static boolean perform(StructuredNativeInvocation prepared) {
            return prepared.isStructuredImportCall();
        }
    }

    /** Prepares the structured ImportCall state; precondition {@code IsStructuredImportCall}. */
    @Operation
    public static final class PrepareStructuredImportCall {
        @Specialization
        public static Object perform(StructuredNativeInvocation prepared) {
            return prepared.prepareStructuredImportCall();
        }
    }

    /** Whether {@code prepared} is the structured Ensure protocol. */
    @Operation
    public static final class IsStructuredEnsure {
        @Specialization
        public static boolean perform(StructuredNativeInvocation prepared) {
            return prepared.isStructuredEnsure();
        }
    }

    /** Prepares the structured Ensure state; precondition {@code IsStructuredEnsure}. */
    @Operation
    public static final class PrepareStructuredEnsure {
        @Specialization
        public static Object perform(StructuredNativeInvocation prepared) {
            return prepared.prepareStructuredEnsure();
        }
    }

    /** Whether {@code prepared} is the structured ForeignEach protocol. */
    @Operation
    public static final class IsStructuredForeignEach {
        @Specialization
        public static boolean perform(StructuredNativeInvocation prepared) {
            return prepared.isStructuredForeignEach();
        }
    }

    /** Prepares the structured ForeignEach state; precondition {@code IsStructuredForeignEach}. */
    @Operation
    public static final class PrepareStructuredForeignEach {
        @Specialization
        public static Object perform(StructuredNativeInvocation prepared) {
            return prepared.prepareStructuredForeignEach();
        }
    }

    /** Whether {@code prepared} is the structured IdentityMapAtIfAbsent protocol. */
    @Operation
    public static final class IsStructuredIdentityMapAtIfAbsent {
        @Specialization
        public static boolean perform(StructuredNativeInvocation prepared) {
            return prepared.isStructuredIdentityMapAtIfAbsent();
        }
    }

    /** Prepares the structured IdentityMapAtIfAbsent state; precondition {@code IsStructuredIdentityMapAtIfAbsent}. */
    @Operation
    public static final class PrepareStructuredIdentityMapAtIfAbsent {
        @Specialization
        public static Object perform(StructuredNativeInvocation prepared) {
            return prepared.prepareStructuredIdentityMapAtIfAbsent();
        }
    }

    /** Whether {@code prepared} is the structured MapMatch protocol. */
    @Operation
    public static final class IsStructuredMapMatch {
        @Specialization
        public static boolean perform(StructuredNativeInvocation prepared) {
            return prepared.isStructuredMapMatch();
        }
    }

    /** Prepares the structured MapMatch state; precondition {@code IsStructuredMapMatch}. */
    @Operation
    public static final class PrepareStructuredMapMatch {
        @Specialization
        public static Object perform(StructuredNativeInvocation prepared) {
            return prepared.prepareStructuredMapMatch();
        }
    }

    /** Whether {@code prepared} is the structured MapReadLookup protocol. */
    @Operation
    public static final class IsStructuredMapReadLookup {
        @Specialization
        public static boolean perform(StructuredNativeInvocation prepared) {
            return prepared.isStructuredMapReadLookup();
        }
    }

    /** Prepares the structured MapReadLookup state; precondition {@code IsStructuredMapReadLookup}. */
    @Operation
    public static final class PrepareStructuredMapReadLookup {
        @Specialization
        public static Object perform(StructuredNativeInvocation prepared) {
            return prepared.prepareStructuredMapReadLookup();
        }
    }

    /** Whether {@code prepared} is the structured MapAtPut protocol. */
    @Operation
    public static final class IsStructuredMapAtPut {
        @Specialization
        public static boolean perform(StructuredNativeInvocation prepared) {
            return prepared.isStructuredMapAtPut();
        }
    }

    /** Prepares the structured MapAtPut state; precondition {@code IsStructuredMapAtPut}. */
    @Operation
    public static final class PrepareStructuredMapAtPut {
        @Specialization
        public static Object perform(StructuredNativeInvocation prepared) {
            return prepared.prepareStructuredMapAtPut();
        }
    }

    /** Whether {@code prepared} is the structured MapRemove protocol. */
    @Operation
    public static final class IsStructuredMapRemove {
        @Specialization
        public static boolean perform(StructuredNativeInvocation prepared) {
            return prepared.isStructuredMapRemove();
        }
    }

    /** Prepares the structured MapRemove state; precondition {@code IsStructuredMapRemove}. */
    @Operation
    public static final class PrepareStructuredMapRemove {
        @Specialization
        public static Object perform(StructuredNativeInvocation prepared) {
            return prepared.prepareStructuredMapRemove();
        }
    }

    /** Whether {@code prepared} is the structured ErrorHandler protocol. */
    @Operation
    public static final class IsStructuredErrorHandler {
        @Specialization
        public static boolean perform(StructuredNativeInvocation prepared) {
            return prepared.isStructuredErrorHandler();
        }
    }

    /** Prepares the structured ErrorHandler state; precondition {@code IsStructuredErrorHandler}. */
    @Operation
    public static final class PrepareStructuredErrorHandler {
        @Specialization
        public static Object perform(StructuredNativeInvocation prepared) {
            return prepared.prepareStructuredErrorHandler();
        }
    }

    /** Whether {@code prepared} is one of the local each families sharing one loop. */
    @Operation
    public static final class IsStructuredLocalEach {
        @Specialization
        public static boolean perform(StructuredNativeInvocation prepared) {
            return prepared.isStructuredArrayEach()
                    || prepared.isStructuredBytesEach()
                    || prepared.isStructuredStringEach()
                    || prepared.isStructuredVectorEach()
                    || prepared.isStructuredMatrixEach()
                    || prepared.isStructuredEnvironmentEach()
                    || prepared.isStructuredIdentityMapEach()
                    || prepared.isStructuredMapEach();
        }
    }

    /** Prepares the admitted local each family; precondition {@code IsStructuredLocalEach}. */
    @Operation
    public static final class PrepareStructuredLocalEach {
        @Specialization
        public static Object perform(StructuredNativeInvocation prepared) {
            if (prepared.isStructuredArrayEach()) {
                return prepared.prepareStructuredArrayEach();
            }
            if (prepared.isStructuredBytesEach()) {
                return prepared.prepareStructuredBytesEach();
            }
            if (prepared.isStructuredStringEach()) {
                return prepared.prepareStructuredStringEach();
            }
            if (prepared.isStructuredVectorEach()) {
                return prepared.prepareStructuredVectorEach();
            }
            if (prepared.isStructuredMatrixEach()) {
                return prepared.prepareStructuredMatrixEach();
            }
            if (prepared.isStructuredEnvironmentEach()) {
                return prepared.prepareStructuredEnvironmentEach();
            }
            if (prepared.isStructuredIdentityMapEach()) {
                return prepared.prepareStructuredIdentityMapEach();
            }
            if (prepared.isStructuredMapEach()) {
                return prepared.prepareStructuredMapEach();
            }
            throw new IllegalStateException(
                    "Not a structured local each call");
        }
    }

    /** Whether {@code prepared} is Array.match or a standard Closure loop, both driven as a child-outcome sequence. */
    @Operation
    public static final class IsStructuredChildOutcomeSequence {
        @Specialization
        public static boolean perform(StructuredNativeInvocation prepared) {
            return prepared.isStructuredArrayMatch() || prepared.isStructuredLoop();
        }
    }

    /** Prepares the admitted loop or Array.match; precondition {@code IsStructuredChildOutcomeSequence}. */
    @Operation
    public static final class PrepareStructuredChildOutcomeSequence {
        @Specialization
        public static Object perform(StructuredNativeInvocation prepared) {
            return prepared.isStructuredLoop()
                    ? prepared.prepareStructuredLoop()
                    : prepared.prepareStructuredArrayMatch();
        }
    }


    /*
     * I113: structured-protocol phases shared across protocols. Every
     * specialization is typed on one protocol's final state carrier (or the
     * PreparedLocalEachCall interface of the five local each families), so a
     * builder-emitted site stays monomorphic and delegates exactly to that
     * protocol's method. No phase spans a guest call: each lowered guest
     * child invocation still sits between two of these instructions.
     */

    /**
     * A structured protocol state query, by the builder-fixed phase:
     * {@code STEP} whether it has a further position, requirement or
     * lifecycle release step; {@code CHILD} whether its optional guest child
     * (callback, matcher or fallback) must run; {@code EQUALITY} whether a Map
     * protocol needs its guest equality call. A protocol takes only the
     * phases it has.
     */
    @Operation
    @ConstantOperand(type = ProtosStructuredPhase.class, name = "phase")
    public static final class StructuredHasNext {
        @Specialization
        public static boolean loop(
                ProtosStructuredPhase phase,
                PreparedLoopCall prepared) {
            return prepared.hasNext();
        }

        @Specialization
        public static boolean localEach(
                ProtosStructuredPhase phase,
                PreparedLocalEachCall prepared) {
            return prepared.hasNext();
        }

        @Specialization
        public static boolean arrayMatch(
                ProtosStructuredPhase phase,
                PreparedArrayMatchCall prepared) {
            return prepared.hasNext();
        }

        @Specialization
        public static boolean foreignEach(
                ProtosStructuredPhase phase,
                ProtosForeignEachCall prepared) {
            return prepared.hasNext();
        }

        @Specialization
        public static boolean mapMatch(ProtosStructuredPhase phase, PreparedMapMatchCall prepared) {
            if (phase == ProtosStructuredPhase.EQUALITY) {
                return prepared.needsEquality();
            }
            return phase == ProtosStructuredPhase.CHILD
                    ? prepared.hasChildMatcher()
                    : prepared.hasRequirement();
        }

        @Specialization
        public static boolean ioRelease(
                ProtosStructuredPhase phase,
                ProtosIoReleaseCPrimeExecution.Sequence sequence) {
            return sequence.hasNextStep();
        }

        @Specialization
        public static boolean booleanCall(
                ProtosStructuredPhase phase,
                PreparedBooleanCall prepared) {
            return prepared.hasCallback();
        }

        @Specialization
        public static boolean caseOf(ProtosStructuredPhase phase, PreparedCaseOfCall prepared) {
            return prepared.needsMatcher();
        }

        @Specialization
        public static boolean identityMapAtIfAbsent(
                ProtosStructuredPhase phase,
                PreparedIdentityMapAtIfAbsentCall prepared) {
            return prepared.needsFallback();
        }

        @Specialization
        public static boolean mapReadLookup(
                ProtosStructuredPhase phase,
                PreparedMapReadLookupCall prepared) {
            if (phase == ProtosStructuredPhase.EQUALITY) {
                return prepared.needsEquality();
            }
            return prepared.needsFallback();
        }

        @Specialization
        public static boolean mapInitialDefinition(
                ProtosStructuredPhase phase,
                PreparedMapInitialDefinition prepared) {
            return prepared.needsEquality();
        }

        @Specialization
        public static boolean mapAtPut(ProtosStructuredPhase phase, PreparedMapAtPutCall prepared) {
            return prepared.needsEquality();
        }

        @Specialization
        public static boolean mapRemove(
                ProtosStructuredPhase phase,
                PreparedMapRemoveCall prepared) {
            return prepared.needsEquality();
        }
    }

    /**
     * Prepares a structured protocol's guest child call, by the builder-fixed
     * phase: {@code CHILD} the primary child (Boolean callback, caseOf
     * matcher, current each/match element, fallback, child matcher,
     * Object/import child, handler or ensure body, while condition);
     * {@code SECONDARY_CHILD} the selected caseOf action, ensure cleanup or
     * while body; {@code HASH}/{@code EQUALITY} a Map protocol's guest hash or
     * equality call. A protocol takes only the phases it has.
     */
    @Operation
    @ConstantOperand(type = ProtosStructuredPhase.class, name = "phase")
    public static final class StructuredPrepareChildCall {
        @Specialization
        public static ProtosCallRequest loop(
                ProtosStructuredPhase phase,
                PreparedLoopCall prepared) {
            return prepared.prepareCurrent();
        }

        @Specialization
        public static ProtosCallRequest booleanCall(
                ProtosStructuredPhase phase,
                PreparedBooleanCall prepared) {
            return prepared.prepareCallback();
        }

        @Specialization
        public static ProtosCallRequest caseOf(
                ProtosStructuredPhase phase,
                PreparedCaseOfCall prepared) {
            return phase == ProtosStructuredPhase.SECONDARY_CHILD
                    ? prepared.prepareSelectedAction()
                    : prepared.prepareMatcher();
        }

        @Specialization
        public static ProtosCallRequest arrayMatch(
                ProtosStructuredPhase phase,
                PreparedArrayMatchCall prepared) {
            return prepared.prepareCurrent();
        }

        @Specialization
        public static ProtosCallRequest localEach(
                ProtosStructuredPhase phase,
                PreparedLocalEachCall prepared) {
            return prepared.prepareCurrent();
        }

        @Specialization
        public static ProtosCallRequest foreignEach(
                ProtosStructuredPhase phase,
                ProtosForeignEachCall prepared) {
            return ProtosCallRequest.call(
                    prepared.callback(), new Object[] {prepared.current()}, prepared.activation());
        }

        @Specialization
        public static ProtosCallRequest identityMapAtIfAbsent(
                ProtosStructuredPhase phase,
                PreparedIdentityMapAtIfAbsentCall prepared) {
            return prepared.prepareFallback();
        }

        @Specialization
        public static ProtosCallRequest mapReadLookup(
                ProtosStructuredPhase phase,
                PreparedMapReadLookupCall prepared) {
            if (phase == ProtosStructuredPhase.HASH) {
                return prepared.prepareHash();
            }
            if (phase == ProtosStructuredPhase.EQUALITY) {
                return prepared.prepareEquality();
            }
            return prepared.prepareFallback();
        }

        @Specialization
        public static ProtosCallRequest mapMatch(
                ProtosStructuredPhase phase,
                PreparedMapMatchCall prepared) {
            if (phase == ProtosStructuredPhase.HASH) {
                return prepared.prepareHash();
            }
            if (phase == ProtosStructuredPhase.EQUALITY) {
                return prepared.prepareEquality();
            }
            return prepared.prepareChildMatcher();
        }

        @Specialization
        public static ProtosCallRequest standardObject(
                ProtosStructuredPhase phase,
                PreparedStandardObjectCall prepared) {
            return prepared.child();
        }

        @Specialization
        public static ProtosPreparedCalls.ModuleInitializationCall standardImport(
                ProtosStructuredPhase phase,
                PreparedStandardImportCall prepared) {
            return prepared.child();
        }

        @Specialization
        public static ProtosCallRequest errorHandler(
                ProtosStructuredPhase phase,
                PreparedErrorHandlerCall prepared) {
            return prepared.body();
        }

        @Specialization
        public static ProtosCallRequest ensure(
                ProtosStructuredPhase phase,
                PreparedEnsureCall prepared) {
            return phase == ProtosStructuredPhase.SECONDARY_CHILD
                    ? prepared.cleanup()
                    : prepared.body();
        }

        @Specialization
        public static ProtosCallRequest mapInitialDefinition(
                ProtosStructuredPhase phase,
                PreparedMapInitialDefinition prepared) {
            return phase == ProtosStructuredPhase.EQUALITY
                    ? prepared.prepareEquality()
                    : prepared.prepareHash();
        }

        @Specialization
        public static ProtosCallRequest mapAtPut(
                ProtosStructuredPhase phase,
                PreparedMapAtPutCall prepared) {
            return phase == ProtosStructuredPhase.EQUALITY
                    ? prepared.prepareEquality()
                    : prepared.prepareHash();
        }

        @Specialization
        public static ProtosCallRequest mapRemove(
                ProtosStructuredPhase phase,
                PreparedMapRemoveCall prepared) {
            return phase == ProtosStructuredPhase.EQUALITY
                    ? prepared.prepareEquality()
                    : prepared.prepareHash();
        }
    }


    /**
     * The query key of a prepared Map protocol: the receiver of both its
     * {@code hash} and its {@code ==} send.
     */
    @Operation(storeBytecodeIndex = false)
    public static final class StructuredComparisonKey {
        @Specialization
        public static Object mapInitialDefinition(PreparedMapInitialDefinition prepared) {
            return prepared.comparisonKey();
        }

        @Specialization
        public static Object mapReadLookup(PreparedMapReadLookupCall prepared) {
            return prepared.comparisonKey();
        }

        @Specialization
        public static Object mapAtPut(PreparedMapAtPutCall prepared) {
            return prepared.comparisonKey();
        }

        @Specialization
        public static Object mapRemove(PreparedMapRemoveCall prepared) {
            return prepared.comparisonKey();
        }

        @Specialization
        public static Object mapMatch(PreparedMapMatchCall prepared) {
            return prepared.comparisonKey();
        }
    }

    /**
     * The representative key of a prepared Map protocol's current candidate:
     * the argument of its {@code ==} send. Its recorded hash is reused.
     */
    @Operation(storeBytecodeIndex = false)
    public static final class StructuredComparisonCandidate {
        @Specialization
        public static Object mapInitialDefinition(PreparedMapInitialDefinition prepared) {
            return prepared.candidateKey();
        }

        @Specialization
        public static Object mapReadLookup(PreparedMapReadLookupCall prepared) {
            return prepared.candidateKey();
        }

        @Specialization
        public static Object mapAtPut(PreparedMapAtPutCall prepared) {
            return prepared.candidateKey();
        }

        @Specialization
        public static Object mapRemove(PreparedMapRemoveCall prepared) {
            return prepared.candidateKey();
        }

        @Specialization
        public static Object mapMatch(PreparedMapMatchCall prepared) {
            return prepared.candidateKey();
        }
    }

    /**
     * The exact semantic caller retained by a prepared Map protocol, never
     * the physical structured-root frame's activation.
     */
    @Operation(storeBytecodeIndex = false)
    public static final class StructuredComparisonCaller {
        @Specialization
        public static ProtosActivation mapInitialDefinition(PreparedMapInitialDefinition prepared) {
            return prepared.comparisonCaller();
        }

        @Specialization
        public static ProtosActivation mapReadLookup(PreparedMapReadLookupCall prepared) {
            return prepared.comparisonCaller();
        }

        @Specialization
        public static ProtosActivation mapAtPut(PreparedMapAtPutCall prepared) {
            return prepared.comparisonCaller();
        }

        @Specialization
        public static ProtosActivation mapRemove(PreparedMapRemoveCall prepared) {
            return prepared.comparisonCaller();
        }

        @Specialization
        public static ProtosActivation mapMatch(PreparedMapMatchCall prepared) {
            return prepared.comparisonCaller();
        }
    }

    /**
     * Attempts a Map protocol's key {@code hash} ({@code HASH} phase) or
     * {@code ==} ({@code EQUALITY} phase) directly, without a guest call.
     *
     * <p>The operation is admitted only when the behavior actually selected
     * for the phase's selector on the query key, under the Prelude of the
     * prepared protocol's exact semantic caller, is a standard behavior:
     * the root Object, Number or String hash at its standard home and for
     * its receiver domain ({@link ProtosStandardHashSupport}), or the
     * standard Number equality at the Prelude's Number prototype
     * ({@link ProtosStandardNumberEqualityProtocol}). Then the exact result
     * of that behavior, a semantic Integer hash or a canonical Boolean, is
     * stored in {@code result} and the operation answers true; the
     * protocol's acceptance validates and consumes it exactly as an invoked
     * result. The answer is the admission, never the guest equality result.
     *
     * <p>Any other selection, including a copied or rebound standard body
     * and the source Object equality, answers false without invoking
     * anything, storing anything or changing the prepared state; the
     * lowering then prepares and dispatches the ordinary send. The
     * {@code argument} operand is the candidate key in the {@code EQUALITY}
     * phase and is ignored in the {@code HASH} phase.
     */
    @Operation(storeBytecodeIndex = false)
    @ConstantOperand(type = ProtosStructuredPhase.class, name = "phase")
    @ConstantOperand(type = LocalAccessor.class, name = "result")
    public static final class StructuredTryStandardKeyOperation {
        /* A SmallInteger key: its standard hash is the key itself. */
        @Specialization
        public static boolean doLong(
                ProtosStructuredPhase phase,
                LocalAccessor result,
                long receiver,
                Object argument,
                ProtosActivation caller,
                @Bind VirtualFrame frame,
                @Bind BytecodeNode bytecode,
                @Shared("selection") @Cached StandardKeySelectionNode selection) {
            ProtosPrelude prelude = caller.preludeOrNullForRuntime();
            if (!selection.execute(selector(phase), receiver, prelude)) {
                return false;
            }
            if (phase == ProtosStructuredPhase.HASH) {
                result.setLong(bytecode, frame, receiver);
            } else if (argument instanceof Long other) {
                result.setBoolean(bytecode, frame, receiver == other.longValue());
            } else {
                result.setBoolean(bytecode, frame,
                        ProtosRichCompareEqNode.standardEquals(receiver, argument));
            }
            return true;
        }

        /* A Float key: the shared Float normal hash; binary64 equality between Floats. */
        @Specialization
        public static boolean doDouble(
                ProtosStructuredPhase phase,
                LocalAccessor result,
                double receiver,
                Object argument,
                ProtosActivation caller,
                @Bind VirtualFrame frame,
                @Bind BytecodeNode bytecode,
                @Shared("selection") @Cached StandardKeySelectionNode selection) {
            ProtosPrelude prelude = caller.preludeOrNullForRuntime();
            if (!selection.execute(selector(phase), receiver, prelude)) {
                return false;
            }
            if (phase == ProtosStructuredPhase.HASH) {
                result.setObject(bytecode, frame,
                        ProtosCurrentNumericRelations.floatNormalHash(receiver, prelude));
            } else if (argument instanceof Double other) {
                result.setBoolean(bytecode, frame, receiver == other.doubleValue());
            } else {
                result.setBoolean(bytecode, frame,
                        ProtosRichCompareEqNode.standardEquals(receiver, argument));
            }
            return true;
        }

        /*
         * Every other key representation. An admitted hash is computed by the
         * helper of the standard body the admission proved for the key's
         * domain; an admitted equality is the standard Number equality.
         */
        @Specialization
        public static boolean doOther(
                ProtosStructuredPhase phase,
                LocalAccessor result,
                Object receiver,
                Object argument,
                ProtosActivation caller,
                @Bind VirtualFrame frame,
                @Bind BytecodeNode bytecode,
                @Shared("selection") @Cached StandardKeySelectionNode selection) {
            ProtosPrelude prelude = caller.preludeOrNullForRuntime();
            if (!selection.execute(selector(phase), receiver, prelude)) {
                return false;
            }
            if (phase == ProtosStructuredPhase.HASH) {
                result.setObject(bytecode, frame, standardHash(receiver, prelude));
            } else {
                result.setBoolean(bytecode, frame,
                        ProtosRichCompareEqNode.standardEquals(receiver, argument));
            }
            return true;
        }

        static String selector(ProtosStructuredPhase phase) {
            return phase == ProtosStructuredPhase.HASH
                    ? ProtosStandardHashSupport.SELECTOR
                    : ProtosRichCompareEqNode.SELECTOR;
        }

        /* The result of the admitted standard hash for the key's domain. */
        static Object standardHash(Object receiver, ProtosPrelude prelude) {
            if (ProtosNumericValueSupport.isCurrentNumber(receiver)) {
                return numberHash(receiver, prelude);
            }
            if (receiver instanceof ProtosStringValue string) {
                return ProtosStandardHashSupport.stringHash(string);
            }
            return ProtosStandardHashSupport.objectHash(receiver);
        }

        /* Fraction, Complex and large Integer hashing stays off the compiled path. */
        @TruffleBoundary
        static Object numberHash(Object receiver, ProtosPrelude prelude) {
            return ProtosCurrentNumericRelations.normalHash(receiver, prelude);
        }
    }

    /*
     * Whether the behavior actually selected for a Map key {@code hash} or
     * {@code ==} on an evaluated receiver, under an explicitly supplied
     * observing Prelude, is a standard behavior whose result the structured
     * key operation computes directly. Nothing is invoked.
     *
     * Cached entries are keyed by the selector and the Prelude identity, and
     * guarded by the structural selection of the selector from the
     * receiver's actual delegation origin: the FROZEN SmallInteger, Float or
     * String binding's prototype of that Prelude for a primitive or String
     * key, root Object for a Boolean or null key, and the direct parent of an
     * ordinary object key that does not own the slot (its Shape proves it
     * owns none). A structural change anywhere along the chain misses the
     * entry. An all-FROZEN chain caches its classification; a mutable chain
     * re-reads and classifies the selected slot on every execution, so an
     * in-place reassignment that keeps every Shape is observed.
     *
     * Every other receiver, and sites past the cached limits, use the
     * complete ordinary lookup, which replaces no cached entry.
     */
    @GenerateUncached
    @GenerateInline(false)
    abstract static class StandardKeySelectionNode extends Node {
        static final String SMALL_INTEGER_BINDING = ProtosNumericSelectionNode.SMALL_INTEGER_BINDING;
        static final String FLOAT_BINDING = ProtosNumericSelectionNode.FLOAT_BINDING;
        static final String STRING_BINDING = "String";

        abstract boolean execute(String selector, Object receiver, ProtosPrelude prelude);

        @Specialization(
                guards = {
                    "selector == cachedSelector",
                    "prelude == cachedPrelude",
                    "selection != null",
                    "frozen || selection.isValid()"
                },
                limit = "3",
                excludeForUncached = true)
        static boolean doLong(
                String selector,
                long receiver,
                ProtosPrelude prelude,
                @Cached("selector") String cachedSelector,
                @Cached("prelude") ProtosPrelude cachedPrelude,
                @Cached("selectFromBinding(cachedPrelude, SMALL_INTEGER_BINDING, cachedSelector)")
                        ProtosValueLookup.StructuralSelection selection,
                @Cached("allOwnersFrozen(selection)") boolean frozen,
                @Cached("frozenStandard(frozen, cachedSelector, receiver, selection, cachedPrelude)")
                        boolean frozenStandard) {
            return frozen
                    ? frozenStandard
                    : standard(cachedSelector, receiver, selection, cachedPrelude);
        }

        @Specialization(
                guards = {
                    "selector == cachedSelector",
                    "prelude == cachedPrelude",
                    "selection != null",
                    "frozen || selection.isValid()"
                },
                limit = "3",
                excludeForUncached = true)
        static boolean doDouble(
                String selector,
                double receiver,
                ProtosPrelude prelude,
                @Cached("selector") String cachedSelector,
                @Cached("prelude") ProtosPrelude cachedPrelude,
                @Cached("selectFromBinding(cachedPrelude, FLOAT_BINDING, cachedSelector)")
                        ProtosValueLookup.StructuralSelection selection,
                @Cached("allOwnersFrozen(selection)") boolean frozen,
                @Cached("frozenStandard(frozen, cachedSelector, receiver, selection, cachedPrelude)")
                        boolean frozenStandard) {
            return frozen
                    ? frozenStandard
                    : standard(cachedSelector, receiver, selection, cachedPrelude);
        }

        @Specialization(
                guards = {
                    "selector == cachedSelector",
                    "prelude == cachedPrelude",
                    "selection != null",
                    "frozen || selection.isValid()"
                },
                limit = "3",
                excludeForUncached = true)
        static boolean doString(
                String selector,
                ProtosStringValue receiver,
                ProtosPrelude prelude,
                @Cached("selector") String cachedSelector,
                @Cached("prelude") ProtosPrelude cachedPrelude,
                @Cached("selectFromBinding(cachedPrelude, STRING_BINDING, cachedSelector)")
                        ProtosValueLookup.StructuralSelection selection,
                @Cached("allOwnersFrozen(selection)") boolean frozen,
                @Cached("frozenStandard(frozen, cachedSelector, receiver, selection, cachedPrelude)")
                        boolean frozenStandard) {
            return frozen
                    ? frozenStandard
                    : standard(cachedSelector, receiver, selection, cachedPrelude);
        }

        /* A Boolean or null key delegates directly to the fixed root Object. */
        @Specialization(
                guards = {
                    "isRootDelegated(receiver)",
                    "selector == cachedSelector",
                    "prelude == cachedPrelude",
                    "selection != null",
                    "frozen || selection.isValid()"
                },
                limit = "3",
                excludeForUncached = true)
        static boolean doRootDelegated(
                String selector,
                Object receiver,
                ProtosPrelude prelude,
                @Cached("selector") String cachedSelector,
                @Cached("prelude") ProtosPrelude cachedPrelude,
                @Cached("selectFromRoot(cachedSelector)")
                        ProtosValueLookup.StructuralSelection selection,
                @Cached("allOwnersFrozen(selection)") boolean frozen,
                @Cached("frozenStandard(frozen, cachedSelector, receiver, selection, cachedPrelude)")
                        boolean frozenStandard) {
            return frozen
                    ? frozenStandard
                    : standard(cachedSelector, receiver, selection, cachedPrelude);
        }

        /*
         * An ordinary object key that does not own the selector: the
         * inherited selection is shared by every receiver with this Shape
         * and the same direct parent; its real owner is the selection home.
         */
        @Specialization(
                guards = {
                    "receiver.usesObjectStorageForRuntime()",
                    "selector == cachedSelector",
                    "prelude == cachedPrelude",
                    "receiverShape.check(receiver)",
                    "selection != null",
                    "hasSelectionParent(receiver, selection)",
                    "frozen || selection.isValid()"
                },
                limit = "3",
                excludeForUncached = true)
        static boolean doObject(
                String selector,
                ProtosObjectValue receiver,
                ProtosPrelude prelude,
                @Cached("selector") String cachedSelector,
                @Cached("prelude") ProtosPrelude cachedPrelude,
                @Cached("receiver.getShape()") Shape receiverShape,
                @Cached("selectInherited(receiver, cachedSelector)")
                        ProtosValueLookup.StructuralSelection selection,
                @Cached("allOwnersFrozen(selection)") boolean frozen,
                @Cached("frozenStandard(frozen, cachedSelector, receiver, selection, cachedPrelude)")
                        boolean frozenStandard) {
            return frozen
                    ? frozenStandard
                    : standard(cachedSelector, receiver, selection, cachedPrelude);
        }

        /*
         * The complete selection, separate from the cached entries and
         * replacing none of them.
         */
        @Specialization
        @InliningCutoff
        static boolean doGeneric(String selector, Object receiver, ProtosPrelude prelude) {
            return selectsStandard(selector, receiver, prelude);
        }

        static boolean isRootDelegated(Object receiver) {
            return receiver instanceof Boolean || receiver == ProtosNullValue.INSTANCE;
        }

        static boolean hasSelectionParent(
                ProtosObjectValue receiver, ProtosValueLookup.StructuralSelection selection) {
            return ProtosValueLookup.hasSelectionParent(receiver, selection);
        }

        static boolean allOwnersFrozen(ProtosValueLookup.StructuralSelection selection) {
            return selection != null && selection.allOwnersFrozen();
        }

        /* Specialization time only: the classification of an all-FROZEN chain. */
        static boolean frozenStandard(
                boolean frozen,
                String selector,
                Object receiver,
                ProtosValueLookup.StructuralSelection selection,
                ProtosPrelude prelude) {
            return frozen
                    && selection.isValid()
                    && standard(selector, receiver, selection, prelude);
        }

        /* The current selected slot value and owner, classified; requires a valid selection. */
        static boolean standard(
                String selector,
                Object receiver,
                ProtosValueLookup.StructuralSelection selection,
                ProtosPrelude prelude) {
            return standard(selector, receiver, selection.read(), selection.home(), prelude);
        }

        static boolean standard(
                String selector,
                Object receiver,
                Object behavior,
                ProtosObjectValue home,
                ProtosPrelude prelude) {
            if (ProtosStandardHashSupport.SELECTOR.equals(selector)) {
                return ProtosStandardHashSupport.isCanonicalStandardHashSelection(
                        receiver, behavior, home, prelude);
            }
            return ProtosRichCompareEqNode.SELECTOR.equals(selector)
                    && ProtosNumericValueSupport.isCurrentNumber(receiver)
                    && ProtosStandardNumberEqualityProtocol.isCanonicalStandardEqualitySelection(
                            behavior, home, prelude);
        }

        /*
         * Specialization time only: the selection of {@code selector} from the
         * prototype of the Prelude's own FROZEN binding {@code name}, exactly
         * as the standard numeric operator selection observes it; null when
         * the Prelude is absent or the binding is not FROZEN.
         */
        @TruffleBoundary
        static ProtosValueLookup.StructuralSelection selectFromBinding(
                ProtosPrelude prelude, String name, String selector) {
            ProtosObjectValue prototype = ProtosNumericSelectionNode.frozenBoundPrototype(
                    ProtosNumericSelectionNode.selectBinding(prelude, name));
            return prototype == null
                    ? null
                    : ProtosValueLookup.selectStructural(prototype, selector);
        }

        /* Specialization time only. */
        @TruffleBoundary
        static ProtosValueLookup.StructuralSelection selectFromRoot(String selector) {
            return ProtosValueLookup.selectStructural(ProtosObjectValue.rootObject(), selector);
        }

        /* Specialization time only: null when the receiver owns the slot. */
        @TruffleBoundary
        static ProtosValueLookup.StructuralSelection selectInherited(
                ProtosObjectValue receiver, String selector) {
            return ProtosValueLookup.selectInheritedStructural(receiver, selector);
        }

        /*
         * The complete selection, observed without invoking anything. A
         * missing or unsupported selection is not standard: the ordinary
         * send then raises its own Error.
         */
        @TruffleBoundary
        static boolean selectsStandard(String selector, Object receiver, ProtosPrelude prelude) {
            if (prelude == null) {
                return false;
            }
            try {
                return ProtosValueLookup.lookup(receiver, selector, prelude)
                        .map(found -> standard(
                                selector, receiver, found.value(), found.home(), prelude))
                        .orElse(false);
            } catch (IllegalStateException | UnsupportedOperationException unavailable) {
                return false;
            }
        }
    }

    /**
     * Accepts the result of a structured protocol's guest child, by the
     * builder-fixed phase: {@code CHILD} the matcher or lower outcome;
     * {@code HASH}/{@code EQUALITY} a Map protocol's guest hash or equality
     * result.
     */
    @Operation
    @ConstantOperand(type = ProtosStructuredPhase.class, name = "phase")
    public static final class StructuredAcceptChildOutcome {
        @Specialization
        public static void loop(
                ProtosStructuredPhase phase,
                PreparedLoopCall prepared,
                Object outcome) {
            prepared.acceptCurrent(outcome);
        }

        @Specialization
        public static void caseOf(
                ProtosStructuredPhase phase,
                PreparedCaseOfCall prepared,
                Object outcome) {
            prepared.acceptMatcher(outcome);
        }

        @Specialization
        public static void arrayMatch(
                ProtosStructuredPhase phase,
                PreparedArrayMatchCall prepared,
                Object outcome) {
            prepared.acceptCurrent(outcome);
        }

        @Specialization
        public static void mapMatch(
                ProtosStructuredPhase phase,
                PreparedMapMatchCall prepared,
                Object outcome) {
            if (phase == ProtosStructuredPhase.HASH) {
                prepared.acceptHash(outcome);
                return;
            }
            if (phase == ProtosStructuredPhase.EQUALITY) {
                prepared.acceptEquality(outcome);
                return;
            }
            prepared.acceptChildOutcome(outcome);
        }

        @Specialization
        public static void ioRelease(
                ProtosStructuredPhase phase,
                ProtosIoReleaseCPrimeExecution.Sequence sequence,
                Object outcome) {
            if (!(outcome
                    instanceof ProtosIoReleaseCPrimeExecution.LowerOutcome
                            lowerOutcome)) {
                throw new IllegalStateException(
                        "lifecycle release C-prime lower wait produced an invalid carrier");
            }
            sequence.applyOutcome(lowerOutcome);
        }

        @Specialization
        public static void mapReadLookup(
                ProtosStructuredPhase phase,
                PreparedMapReadLookupCall prepared,
                Object outcome) {
            if (phase == ProtosStructuredPhase.EQUALITY) {
                prepared.acceptEquality(outcome);
            } else {
                prepared.acceptHash(outcome);
            }
        }

        @Specialization
        public static void mapInitialDefinition(
                ProtosStructuredPhase phase,
                PreparedMapInitialDefinition prepared,
                Object outcome) {
            if (phase == ProtosStructuredPhase.EQUALITY) {
                prepared.acceptEquality(outcome);
            } else {
                prepared.acceptHash(outcome);
            }
        }

        @Specialization
        public static void mapAtPut(
                ProtosStructuredPhase phase,
                PreparedMapAtPutCall prepared,
                Object outcome) {
            if (phase == ProtosStructuredPhase.EQUALITY) {
                prepared.acceptEquality(outcome);
            } else {
                prepared.acceptHash(outcome);
            }
        }

        @Specialization
        public static void mapRemove(
                ProtosStructuredPhase phase,
                PreparedMapRemoveCall prepared,
                Object outcome) {
            if (phase == ProtosStructuredPhase.EQUALITY) {
                prepared.acceptEquality(outcome);
            } else {
                prepared.acceptHash(outcome);
            }
        }
    }

    /** Produces the protocol's result from its last guest child result. */
    @Operation
    public static final class StructuredFinishWithChildResult {
        @Specialization
        public static Object booleanCall(PreparedBooleanCall prepared, Object result) {
            return prepared.finishCallback(result);
        }

        @Specialization
        public static Object identityMapAtIfAbsent(PreparedIdentityMapAtIfAbsentCall prepared, Object result) {
            return prepared.finishFallback(result);
        }

        @Specialization
        public static Object mapReadLookup(PreparedMapReadLookupCall prepared, Object result) {
            return prepared.finishFallback(result);
        }

        @Specialization
        public static Object standardObject(PreparedStandardObjectCall prepared, Object result) {
            return prepared.finish(result);
        }

        @Specialization
        public static Object standardImport(PreparedStandardImportCall prepared, Object result) {
            return prepared.finish(result);
        }
    }

    /** Produces the protocol's result without a further guest child result. */
    @Operation
    public static final class StructuredFinish {
        @Specialization
        public static Object loop(PreparedLoopCall prepared) {
            return prepared.finish();
        }

        @Specialization
        public static Object localEach(PreparedLocalEachCall prepared) {
            return prepared.finish();
        }

        @Specialization
        public static Object arrayMatch(PreparedArrayMatchCall prepared) {
            return prepared.finish();
        }

        @Specialization
        public static Object foreignEach(ProtosForeignEachCall prepared) {
            return prepared.finish();
        }

        @Specialization
        public static Object identityMapAtIfAbsent(PreparedIdentityMapAtIfAbsentCall prepared) {
            return prepared.finishPresent();
        }

        @Specialization
        public static Object mapMatch(PreparedMapMatchCall prepared) {
            return prepared.finish();
        }

        @Specialization
        public static Object mapReadLookup(PreparedMapReadLookupCall prepared) {
            return prepared.finish();
        }

        @Specialization
        public static Object mapAtPut(PreparedMapAtPutCall prepared) {
            return prepared.finish();
        }

        @Specialization
        public static Object mapRemove(PreparedMapRemoveCall prepared) {
            return prepared.finish();
        }

        @Specialization
        public static Object booleanCall(PreparedBooleanCall prepared) {
            return prepared.immediateResult();
        }

        @Specialization
        public static Object ioRelease(ProtosIoReleaseCPrimeExecution.Sequence sequence) {
            return sequence.finish();
        }
    }

    /**
     * A structured protocol state transition without a result, by the
     * builder-fixed phase: {@code STEP} the next each position or Map match
     * requirement, the end of a Map initial definition, or leaving an error
     * handler frame; {@code ENTER_COMPARISON}/{@code LEAVE_COMPARISON} a Map
     * protocol's guest comparison scope.
     */
    @Operation
    @ConstantOperand(type = ProtosStructuredPhase.class, name = "phase")
    public static final class StructuredAdvance {
        @Specialization
        public static void localEach(ProtosStructuredPhase phase, PreparedLocalEachCall prepared) {
            prepared.advance();
        }

        @Specialization
        public static void foreignEach(
                ProtosStructuredPhase phase,
                ProtosForeignEachCall prepared) {
            prepared.advance();
        }

        @Specialization
        public static void mapMatch(ProtosStructuredPhase phase, PreparedMapMatchCall prepared) {
            if (phase == ProtosStructuredPhase.ENTER_COMPARISON) {
                prepared.enterComparison();
                return;
            }
            if (phase == ProtosStructuredPhase.LEAVE_COMPARISON) {
                prepared.leaveComparison();
                return;
            }
            prepared.finishRequirement();
        }

        @Specialization
        public static void mapInitialDefinition(
                ProtosStructuredPhase phase,
                PreparedMapInitialDefinition prepared) {
            if (phase == ProtosStructuredPhase.ENTER_COMPARISON) {
                prepared.enterComparison();
                return;
            }
            if (phase == ProtosStructuredPhase.LEAVE_COMPARISON) {
                prepared.leaveComparison();
                return;
            }
            prepared.finish();
        }

        @Specialization
        public static void errorHandler(
                ProtosStructuredPhase phase,
                PreparedErrorHandlerCall prepared) {
            prepared.leave();
        }

        @Specialization
        public static void mapReadLookup(
                ProtosStructuredPhase phase,
                PreparedMapReadLookupCall prepared) {
            if (phase == ProtosStructuredPhase.LEAVE_COMPARISON) {
                prepared.leaveComparison();
            } else {
                prepared.enterComparison();
            }
        }

        @Specialization
        public static void mapAtPut(ProtosStructuredPhase phase, PreparedMapAtPutCall prepared) {
            if (phase == ProtosStructuredPhase.LEAVE_COMPARISON) {
                prepared.leaveComparison();
            } else {
                prepared.enterComparison();
            }
        }

        @Specialization
        public static void mapRemove(ProtosStructuredPhase phase, PreparedMapRemoveCall prepared) {
            if (phase == ProtosStructuredPhase.LEAVE_COMPARISON) {
                prepared.leaveComparison();
            } else {
                prepared.enterComparison();
            }
        }
    }

    // ---- PLAT044 B′ inline literal callback (PERF026-B1) ----------------------------
    //
    // After ordinary selection and PLAT043 preparation, an eligible immediate
    // literal callback runs its body inline in this root under the prepared
    // child's own fresh activation (see CanonicalToBytecodeLowerer
    // #emitInlineLiteralCallback). Eligibility is owned by PreparedBooleanCall.


    // ---- PLAT057-B inline ordinary Closure-literal call (I096-A) ------------------
    //
    // An ordinary positional call (exact arity up to
    // CanonicalToBytecodeLowerer#MAX_INLINE_CLOSURE_CALL_ARGUMENTS) whose
    // receiver is the site's own staged Closure literal (I096-A), or a
    // binding with a candidate literal body (I096-B1),
    // is prepared through the PLAT044 B′ carrier seam (one ordinary
    // call selection) and admitted only when the carrier's effective body
    // target is that literal's own plan target with an unobservable
    // ReturnHome (see CanonicalToBytecodeLowerer#emitInlineClosureLiteralCall).


    /**
     * the single validation of a canonical control condition
     * (ifTrue, ifFalse, ifTrueIfFalse and the standard loops). It accepts
     * exactly canonical {@code true} or {@code false} and yields the
     * primitive decision {@code value == polarity}, so the lowering inverts
     * ifFalse and the false-polarity loops without a {@code not()} send. Any
     * other value (no truthiness, no Future await/adoption) signals the
     * fresh standard Error; the exact activation is resolved only on that
     * error path, so the success path allocates nothing.
     */
    @Operation(storeBytecodeIndex = true)
    @ConstantOperand(type = boolean.class, name = "polarity")
    public static final class CanonicalControlCondition {
        /* A guest Boolean is a Java boolean: it is consumed directly. */
        @Specialization
        public static boolean doBoolean(boolean polarity, boolean value) {
            return value == polarity;
        }

        @Fallback
        public static boolean doOther(
                boolean polarity,
                Object value,
                @Bind VirtualFrame frame,
                @Bind ProtosBytecodeRootNode root) {
            root.requestCallerNeed(NEEDS_EXACT_ACTIVATION);
            ProtosActivation activation = ProtosFrameArguments.activation(frame);
            throw ProtosCoreErrors.signal(
                    activation, ProtosCoreErrors.newError(activation));
        }
    }

    /**
     * whether a staged {@code not}/{@code and}/{@code or} receiver is
     * exactly canonical {@code true} or {@code false} (or the primitive
     * Boolean carrier of a native comparison), so the frozen standard
     * Boolean behavior is the one ordinary lookup selects. Any other
     * receiver keeps ordinary method selection. Sends nothing.
     */
    @Operation
    public static final class IsCanonicalBooleanOperand {
        @Specialization
        public static boolean doBoolean(boolean value) {
            return true;
        }

        @Fallback
        public static boolean doOther(Object value) {
            return false;
        }
    }

    /**
     * Whether ordinary lookup of a standard loop selector on a loop receiver
     * selects the standard Closure loop: the receiver is a semantic Closure
     * and has no nearer slot of that selector (every Closure delegates to the
     * standard Object, whose loop slots are frozen). Any other receiver keeps
     * ordinary selection. Sends nothing.
     */
    @Operation
    @ConstantOperand(type = String.class, name = "selector")
    public static final class IsStandardLoopReceiver {
        @Specialization
        public static boolean doClosure(String selector, ProtosClosureValue value) {
            return !value.hasLocalSlot(selector);
        }

        @Fallback
        public static boolean doOther(String selector, Object value) {
            return false;
        }
    }

    /**
     * The Closure-domain validation of a standard loop operand, performed
     * after every operand is evaluated and before either Closure is
     * activated. A value that is not a semantic Closure signals the fresh
     * standard Error; an invokable non-Closure is not accepted.
     */
    @Operation(storeBytecodeIndex = false)
    public static final class RequireLoopClosure {
        @Specialization
        public static ProtosClosureValue doClosure(ProtosClosureValue value) {
            return value;
        }

        @Fallback
        @StoreBytecodeIndex
        public static Object doOther(
                Object value,
                @Bind VirtualFrame frame,
                @Bind ProtosBytecodeRootNode root) {
            root.requestCallerNeed(NEEDS_EXACT_ACTIVATION);
            ProtosActivation activation = ProtosFrameArguments.activation(frame);
            throw ProtosCoreErrors.signal(
                    activation, ProtosCoreErrors.newError(activation));
        }
    }

    /**
     * the strict result check of standard {@code and}/{@code or}
     * (VALUES_AND_COLLECTIONS §16). A selected RHS result must be exactly
     * canonical {@code true} or {@code false}; the primitive carrier of a
     * native comparison is accepted and yields the canonical guest value,
     * so no Java Boolean escapes. Any other result (no truthiness, no
     * Future await/adoption) signals the fresh standard Error after the
     * callback completed.
     */
    @Operation(storeBytecodeIndex = false)
    public static final class RequireCanonicalBooleanResult {
        @Specialization
        public static boolean doBoolean(boolean value) {
            return value;
        }

        @Fallback
        @StoreBytecodeIndex
        public static Object doOther(
                Object value,
                @Bind VirtualFrame frame,
                @Bind ProtosBytecodeRootNode root) {
            root.requestCallerNeed(NEEDS_EXACT_ACTIVATION);
            ProtosActivation activation = ProtosFrameArguments.activation(frame);
            throw ProtosCoreErrors.signal(
                    activation, ProtosCoreErrors.newError(activation));
        }
    }


    // ---- PLAT044 B′ inline literal standard each (PERF026-D1/D2/D3) ------------------
    //
    // A selected standard Array.each or Bytes.each whose sole argument is
    // exactly the send site's staged one-parameter literal, or a selected
    // standard Map.each, IdentityMap.each or Environment.each whose sole
    // argument is exactly the staged two-parameter literal, is sequenced
    // locally in this root; every other each keeps the structured-dispatch
    // root. Validation, the snapshot, the cursor and every per-position
    // activation stay owned by the prepared each calls of
    // ProtosStructuredEachCalls.


    /** I091-C2: an internal Long must never be observed as a guest Number. */
    @Operation
    @ConstantOperand(type = boolean.class, name = "carrierPermitted")
    public static final class MaterializePrimitiveNumericCarrier {
        @Specialization
        public static Object perform(boolean carrierPermitted, Object value) {
            return carrierPermitted
                    ? ProtosNumericValueSupport.guestValue(value)
                    : value;
        }
    }

    /**
     * Stores an already-produced operation result into {@code outcome} and
     * answers whether it must be composed as a continuation. Primitive
     * results are ordinary completions; every other result is written
     * unchanged and classified exactly as {@link IsContinuation#perform}.
     */
    @Operation(storeBytecodeIndex = false)
    @ConstantOperand(type = LocalAccessor.class, name = "outcome")
    public static final class StoreAndTestContinuation {
        // Numeric evaluation has already completed; only this write can acquire assignment provenance.
        static void commitCompletedCell(
                LocalAccessor commitCell,
                LocalAccessor outcome,
                SourceSection assignmentSection,
                VirtualFrame frame,
                BytecodeNode bytecode) {
            try {
                StoreCellFromLocal.perform(commitCell, outcome, frame, bytecode);
            } catch (ProtosSignalException failure) {
                failure.recordAssignmentFailureIfAbsentForRuntime(assignmentSection);
                throw failure;
            }
        }

        static boolean storeResultAndCommit(
                LocalAccessor outcome,
                LocalAccessor commitCell,
                boolean commitToCell,
                SourceSection assignmentSection,
                Object value,
                VirtualFrame frame,
                BytecodeNode bytecode) {
            boolean pending = storeResult(outcome, value, frame, bytecode);
            if (commitToCell && !pending) {
                commitCompletedCell(commitCell, outcome, assignmentSection, frame, bytecode);
            }
            return pending;
        }

        @Specialization
        public static boolean doLong(
                LocalAccessor outcome,
                long value,
                @Bind VirtualFrame frame,
                @Bind BytecodeNode bytecode) {
            outcome.setLong(bytecode, frame, value);
            return false;
        }

        @Specialization
        public static boolean doDouble(
                LocalAccessor outcome,
                double value,
                @Bind VirtualFrame frame,
                @Bind BytecodeNode bytecode) {
            outcome.setDouble(bytecode, frame, value);
            return false;
        }

        @Specialization
        public static boolean doBoolean(
                LocalAccessor outcome,
                boolean value,
                @Bind VirtualFrame frame,
                @Bind BytecodeNode bytecode) {
            outcome.setBoolean(bytecode, frame, value);
            return false;
        }

        @Fallback
        public static boolean doObject(
                LocalAccessor outcome,
                Object value,
                @Bind VirtualFrame frame,
                @Bind BytecodeNode bytecode) {
            return storeResult(outcome, value, frame, bytecode);
        }

        /**
         * Stores one completed producer result into {@code outcome} and
         * answers whether it must be composed as a continuation. A Long,
         * Double or Boolean carrier is stored typed and is an ordinary
         * completion; every other value is stored unchanged and classified
         * exactly as {@link IsContinuation#perform}. Producer operations
         * that own their result destination complete through this method.
         */
        static boolean storeResult(
                LocalAccessor outcome,
                Object value,
                VirtualFrame frame,
                BytecodeNode bytecode) {
            if (value instanceof Long carrier) {
                outcome.setLong(bytecode, frame, carrier);
                return false;
            }
            if (value instanceof Double carrier) {
                outcome.setDouble(bytecode, frame, carrier);
                return false;
            }
            if (value instanceof Boolean carrier) {
                outcome.setBoolean(bytecode, frame, carrier);
                return false;
            }
            outcome.setObject(bytecode, frame, value);
            return ProtosTailCallContinuation.isSuspended(value);
        }
    }

    /**
     * Whether one result value is a continuation or suspension the caller
     * must compose.
     */
    @Operation(storeBytecodeIndex = false)
    public static final class IsContinuation {
        /*
         * PERF038-F: an ordinary call site observes a small, stable set of
         * result classes, none of them a continuation. Profiling the exact
         * result class replaces the four suspension type tests of every
         * ordinary result with one class comparison; the answer is decided
         * once per class from the same type tests, so continuation results
         * keep their resumption path unchanged. A site seeing more classes
         * than the limit (and the uncached interpreter) takes the generic
         * form.
         */
        @Specialization(
                guards = {
                    "value != null",
                    "value.getClass() == cachedClass"
                },
                limit = "3")
        public static boolean continuationClassProfiled(
                Object value,
                @Cached("value.getClass()") Class<?> cachedClass,
                @Cached("isContinuationClass(cachedClass)") boolean continuation) {
            return continuation;
        }

        @Specialization(replaces = "continuationClassProfiled")
        public static boolean perform(Object value) {
            /*
             * A native suspension is the leaf request. Yielding it from this
             * root materializes the real C-prime ContinuationResult; callers
             * then continue to see only ordinary nested ContinuationResults.
             */
            return ProtosTailCallContinuation.isSuspended(value);
        }

        /** Exactly {@link IsContinuation#perform} for every instance of {@code type}. */
        static boolean isContinuationClass(Class<?> type) {
            return ContinuationResult.class.isAssignableFrom(type)
                    || ProtosTailCallContinuation.class.isAssignableFrom(type)
                    || ProtosTailCallContinuation.isMappedCompletionClass(type)
                    || ProtosNativeSuspension.class.isAssignableFrom(type)
                    || ProtosIoOperationSuspension.class.isAssignableFrom(type)
                    || ProtosIoReleaseSuspension.class.isAssignableFrom(type);
        }
    }


    // ---- Actor bootstrap using the shared call paths ---------------------------

    @Operation
    public static final class PrepareActorBootstrapModule {
        @Specialization
        public static ProtosPreparedCalls.ModuleInitializationCall perform(Object sequence) {
            return ProtosActorBootstrap.Sequence.prepareModuleForRuntime(sequence);
        }
    }

    @Operation
    public static final class FinishActorBootstrapModule {
        @Specialization
        public static Object perform(
                ProtosPreparedCalls.ModuleInitializationCall module,
                Object result) {
            return module.finish(result);
        }
    }

    @Operation
    public static final class PrepareActorBootstrapBinding {
        @Specialization
        public static ProtosCallRequest perform(Object sequence, Object module) {
            return ProtosActorBootstrap.Sequence.prepareBindingForRuntime(sequence, module);
        }
    }

    /**
     * performs a {@link ProtosCallRequest} produced by a structured
     * protocol, Task or I/O state through the shared dispatcher. The request
     * already carries its callable, B5 arguments and calling activation.
     */
    @Operation(storeBytecodeIndex = true)
    @ConstantOperand(type = boolean.class, name = "terminal")
    public static final class CallRequest {
        @Specialization
        public static Object perform(
                boolean terminal,
                ProtosCallRequest request,
                @Bind VirtualFrame frame,
                @Bind ProtosBytecodeRootNode root,
                @Cached ProtosFunctionCallDispatchers.CallNode callNode,
                @Cached ProtosFunctionCallDispatchers.SelectedBodyCallNode selectedBody) {
            if (request.selectedBody()) {
                return selectedBody.execute(
                        frame, terminal, (ProtosClosureValue) request.callable(),
                        request.arguments(), request.caller(), root);
            }
            return callNode.execute(
                    frame, terminal, request.callable(), request.arguments(),
                    request.caller(), root);
        }
    }

    /**
     * Direct activation of a semantic Closure already selected by a
     * standard protocol (the condition and body of a validated standard
     * loop): its own body runs with no supplied arguments through the
     * shared selected-body dispatcher, and {@code call} is never selected.
     * As for every in-frame call, the caller is the current frame's own
     * activation, resolved by the dispatcher rather than passed here.
     * Suspension composition follows in the lowering, as for every
     * non-terminal call.
     */
    @Operation(storeBytecodeIndex = true)
    public static final class CallSelectedClosureBody {
        @Specialization
        public static Object perform(
                ProtosClosureValue closure,
                @Bind VirtualFrame frame,
                @Bind ProtosBytecodeRootNode root,
                @Cached ProtosFunctionCallDispatchers.SelectedBodyCallNode selectedBody) {
            return selectedBody.execute(
                    frame, false, closure,
                    ProtosFrameArguments.functionCallArguments0(),
                    null, root);
        }
    }

    /**
     * Release calls use the shared dispatcher. Only a transfer escaping a mandatory
     * finalizer during transfer cleanup is held so the next finalizer can run.
     */
    @Operation(storeBytecodeIndex = true)
    public static final class IoReleaseCallRequest {
        @Specialization
        public static Object perform(
                ProtosIoReleaseCPrimeExecution.Sequence sequence,
                ProtosCallRequest request,
                @Bind VirtualFrame frame,
                @Bind ProtosBytecodeRootNode root,
                @Cached ProtosFunctionCallDispatchers.CallNode callNode,
                @Cached ProtosFunctionCallDispatchers.SelectedBodyCallNode selectedBody) {
            try {
                return CallRequest.perform(false, request, frame, root, callNode, selectedBody);
            } catch (ControlFlowException transfer) {
                return sequence.finalizerTransfer(transfer);
            }
        }
    }

    /** The same transfer accounting at a resumed release callback, without replay. */
    @Operation(storeBytecodeIndex = true)
    public static final class IoReleaseResumeChild {
        @Specialization(
                guards = "result.getContinuationRootNode() == cachedRoot",
                limit = "3")
        public static Object direct(
                ProtosIoReleaseCPrimeExecution.Sequence sequence,
                ContinuationResult result,
                Object resumeValue,
                @Cached("result.getContinuationRootNode()") ContinuationRootNode cachedRoot,
                @Cached("create(cachedRoot.getCallTarget())") DirectCallNode node) {
            try {
                return ResumeDispatchedContinuation.direct(result, resumeValue, cachedRoot, node);
            } catch (ControlFlowException transfer) {
                return sequence.finalizerTransfer(transfer);
            }
        }

        @Specialization(replaces = "direct")
        public static Object indirect(
                ProtosIoReleaseCPrimeExecution.Sequence sequence,
                ContinuationResult result,
                Object resumeValue,
                @Cached IndirectCallNode node) {
            try {
                return ResumeDispatchedContinuation.indirect(result, resumeValue, node);
            } catch (ControlFlowException transfer) {
                return sequence.finalizerTransfer(transfer);
            }
        }

        @Fallback
        public static Object leaf(
                Object state,
                Object pending,
                Object resumeValue) {
            ProtosIoReleaseCPrimeExecution.Sequence sequence =
                    (ProtosIoReleaseCPrimeExecution.Sequence) state;
            try {
                return ProtosTailCallContinuation.resume(pending, resumeValue);
            } catch (ControlFlowException transfer) {
                return sequence.finalizerTransfer(transfer);
            }
        }
    }

    /** the structured-protocol root is entered only for structured natives. */
    @Operation
    public static final class RejectUnstructuredInvocation {
        @Specialization
        public static Object perform(Object invocation) {
            throw CompilerDirectives.shouldNotReachHere(
                    "structured-protocol root entered for an unstructured invocation");
        }
    }

    /**
     * The {@code TryFinally} handler of a structured import: a module body that
     * left its composition without completing it fails its initialization record.
     */
    @Operation
    public static final class FailModuleInitializationUnlessCompleted {
        @Specialization
        public static void perform(
                ProtosPreparedCalls.ModuleInitializationCall module,
                boolean completed) {
            if (!completed) {
                fail(module);
            }
        }

        @TruffleBoundary
        private static void fail(ProtosPreparedCalls.ModuleInitializationCall module) {
            module.failIfModuleInitialization();
        }
    }

    /** runs the module body of a structured standard import. */
    @Operation(storeBytecodeIndex = true)
    public static final class RunModuleInitialization {
        @Specialization
        public static Object perform(
                ProtosPreparedCalls.ModuleInitializationCall module,
                @Cached IndirectCallNode callNode) {
            return module.run(callNode);
        }
    }

    /**
     * ({@code super().method}): the super-send selection,
     * starting at the parent of the current methodHome; the result is a
     * {@link ProtosBoundMethod} on the current receiver.
     */
    @Operation(storeBytecodeIndex = true)
    @ConstantOperand(type = String.class, name = "selector")
    public static final class GetSuperMethod {
        @Specialization
        public static Object perform(
                String selector,
                @Bind VirtualFrame frame,
                @Bind ProtosBytecodeRootNode root) {
            root.requestCallerNeed(NEEDS_EXACT_ACTIVATION);
            return ProtosExperimentalGetMethodNode.selectSuper(
                    selector, root, ProtosFrameArguments.activation(frame));
        }
    }

    /**
     * Resumes a continuation returned directly by a source CallTarget.
     *
     * The suspended callee
     * retains its own execution state; this operation only resumes
     * that state and propagates control transfers.
     */
    @Operation(storeBytecodeIndex = true)
    public static final class ResumeDispatchedContinuation {

        @Specialization
        public static Object terminal(
                ProtosTailCallContinuation pending,
                Object resumeValue) {

                return ProtosTailCallContinuation.resume(
                        pending, resumeValue);

        }

        @Specialization(
                guards = "result.getContinuationRootNode() == cachedRoot",
                limit = "3")
        public static Object direct(
                ContinuationResult result,
                Object resumeValue,
                @Cached("result.getContinuationRootNode()")
                        ContinuationRootNode cachedRoot,
                @Cached("create(cachedRoot.getCallTarget())")
                        DirectCallNode node) {

                try {
                    return node.call(result.getFrame(), resumeValue);
                } catch (ControlFlowException transfer) {
                    // Direct unwind bypasses the resumed root's exceptional epilogue.
                    return ProtosFunctionCallDispatchers.completeOwnedTransfer(
                            ProtosFrameArguments.ownedReturnHomeOrNull(
                                    result.getFrame().getArguments()),
                            transfer);
                }

        }

        @Specialization(replaces = "direct")
        public static Object indirect(
                ContinuationResult result,
                Object resumeValue,
                @Cached IndirectCallNode node) {

                try {
                    return node.call(
                            result.getContinuationCallTarget(),
                            result.getFrame(),
                            resumeValue);
                } catch (ControlFlowException transfer) {
                    return ProtosFunctionCallDispatchers.completeOwnedTransfer(
                            ProtosFrameArguments.ownedReturnHomeOrNull(
                                    result.getFrame().getArguments()),
                            transfer);
                }

        }

        /* A builtin suspension leaf resumes through its own descriptor. */
        @Fallback
        public static Object leaf(
                Object pending,
                Object resumeValue) {
            return ProtosTailCallContinuation.resume(pending, resumeValue);
        }
    }


    /*
     * I111-B: the operations below were declared by the former untagged
     * structured-dispatch/C-prime interpreter. They are emitted only by
     * auxiliary roots (ProtosStructuredDispatchLowerer, the Task C-prime
     * entry plan and the I/O C-prime executions).
     */

    /**
     * Snapshots, on entry to an {@code ensure} cleanup, whether a delivered cancellation is
     * unwinding through it. Only then is the cleanup also published as its Task's D121
     * termination-cleanup extent, left by {@link ExitCancellationCleanupExtent}; a cleanup entered
     * by any other transfer materializes nothing.
     */
    @Operation
    public static final class BeginEnsureCleanup {
        @Specialization
        public static boolean perform(ProtosActivation activation) {
            ProtosTask task = activation.task().orElse(null);
            if (task == null
                    || task.cancellationPhase()
                            != ProtosTask.CancellationPhase.UNWINDING) {
                return false;
            }
            task.enterCancellationCleanupExtentForRuntime();
            return true;
        }
    }

    /** Leaves the extent opened by {@link BeginEnsureCleanup} on every cleanup exit. */
    @Operation
    public static final class ExitCancellationCleanupExtent {
        @Specialization
        public static void perform(
                ProtosActivation activation,
                boolean cancellationWasUnwindingBeforeCleanup) {
            if (cancellationWasUnwindingBeforeCleanup) {
                activation.task()
                        .orElseThrow(
                                () -> new IllegalStateException(
                                        "cancellation cleanup requires a task"))
                        .exitCancellationCleanupExtentForRuntime();
            }
        }
    }

    /**
     * PLAT070: on every exit of an {@code ensure} cleanup invocation, supersedes the cancellation
     * that was already unwinding on entry to cleanup when the cleanup did not complete normally,
     * i.e. a later Error, non-local return or cancellation is escaping it. A normal completion,
     * including one after a transfer handled inside cleanup, supersedes nothing.
     */
    @Operation
    public static final class SupersedeCancellationUnwindIfActive {
        @Specialization
        public static void perform(
                ProtosActivation activation,
                boolean cancellationWasUnwindingBeforeCleanup,
                boolean cleanupCompletedNormally) {
            if (cleanupCompletedNormally || !cancellationWasUnwindingBeforeCleanup) {
                return;
            }
            ProtosTask task = activation.task().orElse(null);
            if (task == null
                    || task.cancellationPhase()
                            != ProtosTask.CancellationPhase.UNWINDING) {
                return;
            }
            if (!task.supersedeCancellationUnwind()) {
                throw new IllegalStateException(
                        "escaping cleanup transfer could not supersede cancellation unwind");
            }
        }
    }

    @Operation
    public static final class PrepareSelectedStructuredErrorHandlerCall {
        @Specialization
        public static ProtosCallRequest perform(
                PreparedErrorHandlerCall prepared,
                AbstractTruffleException exception) {
            return prepared.selectHandler(exception);
        }
    }

    /*
     * I113: the shared C-prime I/O phases. Each specialization is typed on
     * the protocol's own state carrier, so a builder-emitted site stays
     * monomorphic and delegates exactly to that protocol's method.
     */

    /**
     * Prepares the protocol's single (or first) lower target call; with
     * {@code flush} (builder-fixed) the BufferedWriter flush call.
     */
    @Operation
    @ConstantOperand(type = boolean.class, name = "flush")
    public static final class IoPrepareTargetCall {
        @Specialization
        public static ProtosCallRequest bufferedReader(
                boolean flush,
                ProtosBufferedByteReaderCPrimeExecution.CallState state,
                ProtosActivation activation) {
            return ProtosCallRequest.send(
                    state.target(),
                    "read",
                    activation,
                    state.arguments());
        }

        @Specialization
        public static ProtosCallRequest bufferedWriterFirst(
                boolean flush,
                ProtosBufferedByteWriterCPrimeExecution.CallState state,
                ProtosActivation activation) {
            if (flush) {
                return ProtosCallRequest.send(
                        state.target(),
                        "flush",
                        activation,
                        new Object[0]);
            }
            return ProtosCallRequest.send(
                    state.target(),
                    state.firstSelector(),
                    activation,
                    state.firstArguments());
        }

        @Specialization
        public static ProtosCallRequest ioRelease(
                boolean flush,
                ProtosIoReleaseCPrimeExecution.Sequence sequence,
                ProtosActivation activation) {
            return ProtosCallRequest.send(
                    sequence.target(),
                    sequence.selector(),
                    activation,
                    sequence.arguments());
        }

        @Specialization
        public static ProtosCallRequest textWriter(
                boolean flush,
                ProtosTextWriterCPrimeExecution.CallState state,
                ProtosActivation activation) {
            return ProtosCallRequest.send(
                    state.target(),
                    state.selector(),
                    activation,
                    state.arguments());
        }

        @Specialization
        public static ProtosCallRequest textReader(
                boolean flush,
                ProtosTextReaderCPrimeExecution.CallState state,
                ProtosActivation activation) {
            return ProtosCallRequest.send(
                    state.source(),
                    "read",
                    activation,
                    state.arguments());
        }
    }

    /**
     * The successful lower invocation result, or with {@code await}
     * (builder-fixed) the await of the invocation carrier's lower Future.
     */
    @Operation
    @ConstantOperand(type = boolean.class, name = "await")
    public static final class IoInvocationSucceeded {
        @Specialization
        public static Object bufferedReader(
                boolean await,
                ProtosBufferedByteReaderCPrimeExecution.CallState state,
                Object value) {
            if (!await) {
                return state.invocationSucceeded(value);
            }
            if (!(value
                    instanceof ProtosBufferedByteReaderCPrimeExecution.TargetInvocation
                            targetInvocation)) {
                throw new IllegalStateException(
                        "BufferedReader C-prime invocation produced an invalid carrier");
            }
            return state.awaitTargetFuture(targetInvocation);
        }

        @Specialization
        public static Object bufferedWriter(
                boolean await,
                ProtosBufferedByteWriterCPrimeExecution.CallState state,
                Object value) {
            if (!await) {
                return state.invocationSucceeded(value);
            }
            if (!(value
                    instanceof ProtosBufferedByteWriterCPrimeExecution.TargetInvocation
                            targetInvocation)) {
                throw new IllegalStateException(
                        "BufferedWriter C-prime invocation produced an invalid carrier");
            }
            return state.awaitTargetFuture(targetInvocation);
        }

        @Specialization
        public static Object ioRelease(
                boolean await,
                ProtosIoReleaseCPrimeExecution.Sequence sequence,
                Object value) {
            if (!await) {
                return sequence.invocationSucceeded(value);
            }
            if (!(value
                    instanceof ProtosIoReleaseCPrimeExecution.TargetInvocation
                            targetInvocation)) {
                throw new IllegalStateException(
                        "lifecycle release C-prime invocation produced an invalid carrier");
            }
            return sequence.awaitTargetFuture(targetInvocation);
        }

        @Specialization
        public static Object textWriter(
                boolean await,
                ProtosTextWriterCPrimeExecution.CallState state,
                Object value) {
            if (!await) {
                return state.invocationSucceeded(value);
            }
            if (!(value
                    instanceof ProtosTextWriterCPrimeExecution.TargetInvocation targetInvocation)) {
                throw new IllegalStateException(
                        "TextWriter C-prime invocation produced an invalid carrier");
            }
            return state.awaitTargetFuture(targetInvocation);
        }

        @Specialization
        public static Object textReader(
                boolean await,
                ProtosTextReaderCPrimeExecution.CallState state,
                Object value) {
            if (!await) {
                return state.invocationSucceeded(value);
            }
            if (!(value
                    instanceof ProtosTextReaderCPrimeExecution.SourceInvocation sourceInvocation)) {
                throw new IllegalStateException(
                        "TextReader C-prime invocation produced an invalid carrier");
            }
            return state.awaitSourceFuture(sourceInvocation);
        }
    }

    /**
     * The failure phase of the protocols whose failure takes no argument;
     * TextReader keeps {@link TextReaderSourceInvocationFailed}.
     */
    /**
     * PLAT070: the {@code TryFinally} handler of an I/O C-prime root (see {@code
     * ProtosIoCPrimeTransferAccounting}). When the root body neither completed nor left through a
     * guest Error, an internal control transfer is crossing it: the protocol records it with its
     * own failed-invocation bookkeeping. The transfer is not observed, caught or replaced here.
     */
    @Operation
    public static final class IoAccountEscapingTransfer {
        @Specialization
        public static void bufferedReader(
                ProtosBufferedByteReaderCPrimeExecution.CallState state,
                boolean completedOrSignalled) {
            if (!completedOrSignalled) {
                escaped(state::transferEscaped);
            }
        }

        @Specialization
        public static void bufferedWriter(
                ProtosBufferedByteWriterCPrimeExecution.CallState state,
                boolean completedOrSignalled) {
            if (!completedOrSignalled) {
                escaped(state::transferEscaped);
            }
        }

        @Specialization
        public static void textReader(
                ProtosTextReaderCPrimeExecution.CallState state,
                boolean completedOrSignalled) {
            if (!completedOrSignalled) {
                escaped(state::transferEscaped);
            }
        }

        @Specialization
        public static void textWriter(
                ProtosTextWriterCPrimeExecution.CallState state,
                boolean completedOrSignalled) {
            if (!completedOrSignalled) {
                escaped(state::transferEscaped);
            }
        }

        @TruffleBoundary
        private static void escaped(Runnable accounting) {
            accounting.run();
        }
    }

    /**
     * PLAT070: in the lifecycle-release root's finally handler, answers whether an internal
     * control transfer is leaving the release body; if so, the interrupted step is accounted and
     * pending mandatory finalizers are left for the transfer cleanup step loop.
     */
    @Operation
    public static final class IoReleaseBeginTransferCleanup {
        @Specialization
        @TruffleBoundary
        public static boolean perform(
                ProtosIoReleaseCPrimeExecution.Sequence sequence,
                boolean completedOrSignalled) {
            return sequence.beginTransferCleanup(completedOrSignalled);
        }
    }

    /** PLAT070: terminalizes the release after transfer cleanup ran its mandatory finalizers. */
    @Operation
    public static final class IoReleaseFinishTransferCleanup {
        @Specialization
        @TruffleBoundary
        public static void perform(ProtosIoReleaseCPrimeExecution.Sequence sequence) {
            sequence.finishTransferCleanup();
        }
    }

    /** PLAT070: accounts a later transfer escaping a mandatory finalizer during transfer cleanup. */
    @Operation
    public static final class IoReleaseAbandonTransferCleanup {
        @Specialization
        @TruffleBoundary
        public static void perform(
                ProtosIoReleaseCPrimeExecution.Sequence sequence,
                boolean cleanupCompleted) {
            sequence.abandonTransferCleanup(cleanupCompleted);
        }
    }

    /** Rethrows the exact guest Error caught by an I/O C-prime accounting extent. */
    @Operation
    public static final class RethrowTruffleException {
        @Specialization
        public static void perform(AbstractTruffleException exception) {
            throw exception;
        }
    }

    @Operation
    public static final class IoInvocationFailed {
        @Specialization
        public static Object bufferedReader(
                ProtosBufferedByteReaderCPrimeExecution.CallState state) {
            return state.invocationFailed();
        }

        @Specialization
        public static Object bufferedWriter(
                ProtosBufferedByteWriterCPrimeExecution.CallState state) {
            return state.invocationFailed();
        }

        @Specialization
        public static Object ioRelease(
                ProtosIoReleaseCPrimeExecution.Sequence sequence) {
            return sequence.invocationFailed();
        }

        @Specialization
        public static Object textWriter(
                ProtosTextWriterCPrimeExecution.CallState state) {
            return state.invocationFailed();
        }
    }

    /**
     * Resumes an ordinary {@link ProtosIoOperationSuspension} lower-Future
     * wait. The four protocols share this exact contract; {@code family} is
     * the builder-fixed protocol name used only by the invariant
     * diagnostics. The lifecycle release wait keeps
     * {@link ResumeIoReleaseTargetFutureWait}, whose suspension type and
     * authority differ.
     */
    @Operation
    @ConstantOperand(type = String.class, name = "family")
    public static final class IoResumeOperationFutureWait {
        @Specialization
        public static Object perform(
                String family,
                ProtosActivation activation,
                Object yielded,
                Object resumeValue) {
            if (!(yielded instanceof ProtosIoOperationSuspension suspension)) {
                throw new IllegalStateException(
                        family + " lower-Future wait yielded an invalid carrier");
            }
            ProtosIoOperation operation =
                    activation.deferredCPrimeOperationForRuntime()
                            .orElseThrow(
                                    () ->
                                            new IllegalStateException(
                                                    family
                                                            + " lower-Future wait requires an operation-owned activation"));
            if (suspension.operation() != operation) {
                throw new IllegalStateException(
                        family + " lower-Future wait belongs to another operation");
            }
            if (resumeValue != ProtosNullValue.INSTANCE) {
                throw new IllegalStateException(
                        family + " lower-Future wait received unsupported resume transport");
            }
            return suspension.resume();
        }
    }

    @Operation
    public static final class BeginBufferedByteWriterFirstEffect {
        @Specialization
        public static boolean perform(
                ProtosBufferedByteWriterCPrimeExecution.CallState state) {
            return state.beginFirstEffectAttempt();
        }
    }

    /** Applies the BufferedWriter first, or with {@code followup} follow-up flush, outcome. */
    @Operation
    @ConstantOperand(type = boolean.class, name = "followup")
    public static final class ApplyBufferedByteWriterOutcome {
        @Specialization
        public static boolean perform(
                boolean followup,
                ProtosBufferedByteWriterCPrimeExecution.CallState state,
                Object outcome) {
            if (!(outcome
                    instanceof ProtosBufferedByteWriterCPrimeExecution.LowerOutcome
                            lowerOutcome)) {
                throw new IllegalStateException(
                        followup
                                ? "BufferedWriter followup lower wait produced an invalid carrier"
                                : "BufferedWriter first lower wait produced an invalid carrier");
            }
            return followup
                    ? state.applyFollowupFlushOutcome(lowerOutcome)
                    : state.applyFirstOutcome(lowerOutcome);
        }
    }

    @Operation
    public static final class ResumeIoReleaseTargetFutureWait {
        @Specialization
        public static Object perform(
                ProtosActivation activation,
                Object yielded,
                Object resumeValue) {
            return ProtosStandardFutureProtocol.resumeIoReleaseFutureWaitForRuntime(
                    activation,
                    yielded,
                    resumeValue);
        }
    }

    @Operation
    public static final class TextReaderSourceInvocationFailed {
        @Specialization
        public static Object perform(
                ProtosTextReaderCPrimeExecution.CallState state,
                AbstractTruffleException failure) {
            return state.invocationFailed(failure);
        }
    }

    @Operation
    public static final class TextReaderInitialNeedInput {
        @Specialization
        public static Object perform() {
            return ProtosTextReaderCPrimeExecution.Advance.needInput();
        }
    }

    @Operation
    public static final class ApplyTextReaderLowerOutcome {
        @Specialization
        public static Object perform(
                ProtosTextReaderCPrimeExecution.CallState state,
                Object outcome) {
            if (!(outcome instanceof ProtosTextReaderCPrimeExecution.LowerOutcome lowerOutcome)) {
                throw new IllegalStateException(
                        "TextReader C-prime lower wait produced an invalid carrier");
            }
            return state.applyLowerOutcome(lowerOutcome);
        }
    }
}
