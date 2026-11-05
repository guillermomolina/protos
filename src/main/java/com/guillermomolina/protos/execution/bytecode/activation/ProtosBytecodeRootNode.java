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
import com.guillermomolina.protos.execution.ProtosStructuredEachCalls.PreparedIndexedEachCall;
import com.guillermomolina.protos.execution.ProtosStructuredEachCalls.PreparedLocalEachCall;
import com.guillermomolina.protos.execution.ProtosStructuredMapUpdateCalls.PreparedMapInitialDefinition;
import com.guillermomolina.protos.execution.ProtosStructuredControlCalls.PreparedWhileCall;
import com.guillermomolina.protos.execution.ProtosFrameLocalBindings.ResolvedLexicalWriteTarget;
import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosBooleanValue;
// PRUIBA DE I115, en algún momento esto volverá a funcionar
// import com.guillermomolina.protos.runtime.ProtosIntegerValue;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosLexicalCell;
import com.guillermomolina.protos.runtime.ProtosModuleBindingLocation;
import com.guillermomolina.protos.runtime.ProtosNumericValueSupport;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.control.ProtosReturnHome;
import com.guillermomolina.protos.runtime.lookup.ProtosSlotLookupResult;
import com.guillermomolina.protos.runtime.ProtosValueLookup;
import com.guillermomolina.protos.semantic.ast.CanonicalClosure;
import com.guillermomolina.protos.semantic.ast.CanonicalIntrinsic;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.bytecode.BytecodeNode;
import com.oracle.truffle.api.bytecode.BytecodeRootNode;
import com.oracle.truffle.api.bytecode.ConstantOperand;
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
import com.oracle.truffle.api.bytecode.Variadic;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.NonIdempotent;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ControlFlowException;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.PropertyGetter;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
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
 * as guest frames. I115-H4-B EXPERIMENTAL: automatic RootTag/RootBodyTag
 * tagging is enabled and applies to every physical root of this single
 * configuration, auxiliary roots included; the lowerer no longer emits an
 * explicit physical-root RootTag. Inline Closure
 * callbacks retain their nested semantic RootTag regions. The generated
 * interpreter does not infer guest identity from the mere existence of a
 * physical root. Object bodies are lowered inline
 * and never become roots. Lexically nested Closure roots are created in the same
 * {@code BytecodeRootNodes} parse as their owner. I115: a captured Closure
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
 * I115-H4-B EXPERIMENTAL (GraalPy PBytecodeDSLRootNode configuration):
 * default automatic RootTag/RootBodyTag tagging, no block scoping,
 * serialization, the bytecode index stored in the frame, an uncached
 * threshold of 4 and int-only boxing elimination. NOT PORTABLE: Materialized
 * local accesses stay enabled; illegalLocalException/Factory are not
 * configured, so uninitialized locals keep their existing behavior; the
 * root extends RootNode, not a PRootNode. With serialization enabled, every
 * mutable field of this root is transient: they hold per-parse lowering
 * facts, runtime state or a child node that the lowerer and execution
 * re-establish, and no BytecodeSerializer exists, so nothing serializes
 * them.
 */
@GenerateBytecode(
        languageClass = ProtosLanguage.class,
        enableYield = true,
        enableTagInstrumentation = true,
        enableMaterializedLocalAccesses = true,
        enableBlockScoping = false,
        enableSerialization = true,
        storeBytecodeIndexInFrame = true,
        defaultUncachedThreshold = "4",
        enableTailCallHandlers = true,
        enableUncachedInterpreter = true,
        boxingEliminationTypes = {long.class, double.class},
        sourceContentSupplier = "loadSourceContent",
        tagTreeNodeLibrary = ProtosBytecodeTagTreeNodeExports.class)
@OperationProxy(value = ProtosNumberDivideNode.class, name = "NumberDivide")
@OperationProxy(value = ProtosRichCompareLtNode.class, name = "RichCompareLt")
@OperationProxy(value = ProtosRichCompareLeNode.class, name = "RichCompareLe")
@OperationProxy(value = ProtosRichCompareGtNode.class, name = "RichCompareGt")
@OperationProxy(value = ProtosRichCompareGeNode.class, name = "RichCompareGe")
@OperationProxy(value = ProtosExperimentalReadGlobalNode.class, name = "ReadGlobal")
@OperationProxy(value = ProtosExperimentalWriteGlobalNode.class, name = "SelectWriteGlobal")
// I115: GraalPy-shaped method call and numeric operations shared by every source root.
@OperationProxy(value = ProtosCallNilaryMethodNode.class, name = "CallNilaryMethod")
// PRUIBA DE I115, en algún momento esto volverá a funcionar
// Original operation proxy:
// @OperationProxy(value = ProtosCallUnaryMethodNode.class, name = "CallUnaryMethod")
@OperationProxy(value = ProtosCallUnaryMethodNode.class, name = "CallUnaryMethod", forceCached = true)
@OperationProxy(value = ProtosExperimentalGetMethodNode.class, name = "GetMethod")
@OperationProxy(value = ProtosExperimentalCallBinaryMethodNode.class, name = "CallBinaryMethod")
@OperationProxy(value = ProtosCallTernaryMethodNode.class, name = "CallTernaryMethod")
@OperationProxy(value = ProtosCallQuaternaryMethodNode.class, name = "CallQuaternaryMethod")
@OperationProxy(value = ProtosCallVarargsMethodNode.class, name = "CallVarargsMethod")
@OperationProxy(value = ProtosExperimentalNumberAddNode.class, name = "NumberAdd")
@OperationProxy(value = ProtosExperimentalNumberSubtractNode.class, name = "NumberSubtract")
@OperationProxy(value = ProtosExperimentalNumberMultiplyNode.class, name = "NumberMultiply")
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
     * I115-H4-B EXPERIMENTAL (GraalPy needsTraceAndProfileInstrumentation):
     * whether the internal trace probe is enabled. The JVM-start value of
     * {@link ProtosExperimentalTraceHooks#ENABLED}; false by default.
     */
    @NonIdempotent
    public final boolean needsTraceAndProfileInstrumentation() {
        return ProtosExperimentalTraceHooks.ENABLED;
    }

    /* I115-H4-B EXPERIMENTAL: records ROOT_RETURN; the value is unchanged. */
    @InliningCutoff
    private void traceOrProfileReturn(VirtualFrame frame, BytecodeNode location, Object value) {
        ProtosExperimentalTraceHooks.record(
                ProtosExperimentalTraceHooks.Event.ROOT_RETURN,
                this,
                location.getBytecodeIndex(frame),
                value);
    }

    /* I115-H4-B EXPERIMENTAL: records ROOT_EXCEPTION; the exception is unchanged. */
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
     * I115-H4-B EXPERIMENTAL (GraalPy InstrumentCall): records CALL after the
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
     * I115-H4-B EXPERIMENTAL (GraalPy InstrumentCallReturn): records
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
     * I115-H4-B EXPERIMENTAL: the Bytecode DSL source content supplier. A
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
    public record ComposeReservedNames(java.util.List<String> names) {
        public ComposeReservedNames {
            names = java.util.List.copyOf(names);
        }
    }

    /*
     * I115: the declared names of a Closure root's LOCAL/CELL bindings, with
     * the offsets of their Bytecode DSL locals and whether each local holds a
     * cell, or null. Read only by tooling scope projection, which receives
     * the live frame; never consulted by guest execution.
     */
    private transient String[] toolingBindingNames;
    private transient int[] toolingBindingOffsets;
    private transient boolean[] toolingBindingCells;

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

    /*
     * I115 (GraalPy IndirectCallData.CallerFlagsAssumptionSet): what this
     * physical root has needed from its own caller reference. Until a need
     * is registered, every site that would satisfy it is compiled as a
     * deoptimization instead of the work (for NEEDS_EXACT_ACTIVATION, the
     * publication of a still-compact activation and of its deferred caller
     * chain behind the publishDeferredCaller boundary). The first execution
     * that needs it invalidates that code, registers the need monotonically
     * and replaces the stability Assumption, so the site converges after
     * one invalidation per need. The state belongs to this root only; it is
     * never shared with other roots or Contexts.
     *
     * NEEDS_CALLER_PRELUDE (GraalPy CallerFlags / CallContext): this root
     * reads an inherited prelude through its caller slot. It is registered
     * where such a read finds the prelude untransported
     * (ProtosFrameArguments#inheritsUntransportedCallerPrelude) and
     * consulted by the producers that enter this root with a deferred
     * caller (ProtosFunctionCallDispatchers): from then on they
     * pass a ProtosCallerFrameReference carrying the caller's prelude, and
     * a producer whose callee has not requested it prepares nothing. The
     * producer reads the need from its cached callee root, so the
     * stability Assumption is the dependency that recompiles it.
     *
     * I115 EXPERIMENTAL: it is also requested when an experimental numeric
     * result needs the large-Integer prototype of the Prelude (a signed-64
     * overflow, or a generic Integer route) in a root entered through a
     * reduced layout that did not carry it. The producers of the reduced
     * layouts consult it the
     * same way and transport the Prelude only from then on; the first such
     * read recovers it cold (experimentalRecoveredPrelude).
     *
     * Only needs with a request point and a consumer at HEAD are defined.
     */
    static final int NEEDS_EXACT_ACTIVATION = 1;
    static final int NEEDS_CALLER_PRELUDE = 2;

    private final CallerNeeds callerNeeds = new CallerNeeds();

    /**
     * I115: requests {@code need} for this root; see {@link CallerNeeds}.
     * Constant-folded to nothing once the need is registered.
     */
    final void requestCallerNeed(int need) {
        callerNeeds.request(need);
    }

    /** I115 test and diagnostic access: whether {@code need} is registered. */
    final boolean hasCallerNeed(int need) {
        return callerNeeds.has(need);
    }

    /** I115: the Assumption that the registered caller needs do not change. */
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
     * Recorded once per parse, before the root is published; never consulted
     * as a per-call compiler check beyond these guards.
     */
    private transient boolean provablyNonSuspendingBody;

    final void recordProvablyNonSuspendingBody(boolean proven) {
        this.provablyNonSuspendingBody = proven;
    }

    final boolean provablyNonSuspendingBody() {
        return provablyNonSuspendingBody;
    }

    /*
     * I115: a closure's whole lexical subtree (including defaults and nested
     * closures) cannot target this invocation's return home. The lowering
     * records the proof once on the physical callee root, so a cached direct
     * call does not inspect a closure's execution plan at runtime.
     */
    @CompilationFinal private transient boolean returnHomeProvenUnobservable;

    final void recordReturnHomeProvenUnobservable(boolean proven) {
        returnHomeProvenUnobservable = proven;
    }

    final boolean returnHomeProvenUnobservable() {
        return returnHomeProvenUnobservable;
    }

    // PRUIBA DE I115, en algún momento esto volverá a funcionar
    // Removed per-root numeric return-projection state.

    final void recordToolingBindings(String[] names, int[] offsets, boolean[] cells) {
        this.toolingBindingNames = names;
        this.toolingBindingOffsets = offsets;
        this.toolingBindingCells = cells;
    }

    /**
     * PLAT067-B / I115 tooling-only read of this root's established LOCAL
     * and CELL bindings at {@code bytecodeIndex} of {@code frame}, in
     * declaration order, or {@code null} when this root records none. An
     * unestablished local or cell is ABSENT and omitted; a primitive carrier
     * is answered as its guest value. Nothing is installed, published or
     * retained: the activation and its Context are left unchanged.
     *
     * <p>KNOWN I115 SEMANTIC DIVERGENCE: this view is read-only; a debugger
     * write to these bindings through the scope does not reach the frame.
     */
    @TruffleBoundary
    final java.util.Map<String, Object> toolingBindingsOrNull(
            BytecodeNode bytecodeNode,
            int bytecodeIndex,
            com.oracle.truffle.api.frame.MaterializedFrame materialized) {
        if (toolingBindingNames == null) {
            return null;
        }
        java.util.LinkedHashMap<String, Object> present = new java.util.LinkedHashMap<>();
        for (int index = 0; index < toolingBindingNames.length; index++) {
            Object stored =
                    bytecodeNode.getLocalValue(
                            bytecodeIndex, materialized, toolingBindingOffsets[index]);
            Object value = stored;
            if (toolingBindingCells[index]) {
                value = stored instanceof ProtosLexicalCell cell
                        ? cell.valueOrNullForRuntime()
                        : null;
            }
            if (value != null) {
                present.put(
                        toolingBindingNames[index],
                        ProtosNumericValueSupport.guestValue(value));
            }
        }
        return java.util.Collections.unmodifiableMap(present);
    }

    @Override
    public Object interceptControlFlowException(
            ControlFlowException transfer,
            VirtualFrame frame,
            BytecodeNode bytecodeNode,
            int bytecodeIndex)
            throws Throwable {
        /*
         * I115 (TruffleRuby RubyMethodRootNode / ReturnID, TruffleSqueak
         * home-context identity): a non-local return aimed by identity at the
         * still-active ReturnHome this activation owns is captured here, in
         * the owning root. Any other transfer leaving this activation closes
         * only the home this activation owns and keeps unwinding with its
         * target unchanged.
         */
        ProtosReturnHome owned =
                ProtosFrameArguments.ownedReturnHomeOrNull(frame.getArguments());
        if (owned != null) {
            if (transfer instanceof ProtosNonLocalReturnException nonLocalReturn
                    && nonLocalReturn.target() == owned
                    && owned.isActive()) {
                owned.closeOwned();
                return nonLocalReturn.value();
            }
            owned.closeOwned();
        }
        /*
         * I111-B: only a semantic activation root is a CLI008 origin; an
         * auxiliary root bridges the transfer without a position, exactly as
         * the former structured/C-prime interpreter did. Exceptional path only;
         * the classification is a compilation-final property of this root.
         */
        if (!semanticActivationRoot) {
            throw ProtosBytecodeControlTransferException.bridge(transfer);
        }
        throw ProtosBytecodeControlTransferException.bridge(transfer, bytecodeNode, bytecodeIndex);
    }

    /*
     * I115: a Java/Truffle internal failure that leaves this activation without
     * crossing EpilogForException still definitively abandons it.
     */
    @Override
    public Throwable interceptInternalException(
            Throwable t,
            VirtualFrame frame,
            BytecodeNode bytecodeNode,
            int bytecodeIndex) {
        // PRUIBA DE I115, en algún momento esto volverá a funcionar
        // closeOwnedReturnHome(frame, null);
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
     * I115-E1: root entry does not install a materialized-frame
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
     * I115 (GraalPy EnterCalleeContext): every source root is entered with
     * the same B5 invocation header and runs the same empty prologue. No
     * ProtosActivation is materialized and no Object.call, ReturnHome, Task
     * or suspension check runs.
     */
    @Prolog(storeBytecodeIndex = false)
    public static final class EnterRoot {
        @Specialization
        public static void perform() {
        }
    }

    /** I115: direct return, following GraalPy's return epilogue. */
    @EpilogReturn(storeBytecodeIndex = true)
    public static final class EpilogForReturn {
        @Specialization
        public static Object perform(
                Object value,
                @Bind VirtualFrame frame,
                @Bind ProtosBytecodeRootNode root,
                @Bind BytecodeNode bytecodeNode) {
            /*
             * I115-H4-B EXPERIMENTAL (GraalPy EpilogForReturn). I115-H5: an
             * experimental callee context root never projects.
             */
            if (root.needsTraceAndProfileInstrumentation()) {
                root.traceOrProfileReturn(frame, bytecodeNode, value);
            }
            // I115: the definitive completion of this activation closes its own home.
            // PRUIBA DE I115, en algún momento esto volverá a funcionar
            // closeOwnedReturnHome(frame, value);
            // PRUIBA DE I115, en algún momento esto volverá a funcionar
            return value;
        }
    }

    /**
     * I115-H4-B EXPERIMENTAL (GraalPy EpilogForException): the single
     * exceptional epilogue of every root: only the internal trace probe
     * records the exception, which is neither translated nor replaced.
     */
    @EpilogExceptional(storeBytecodeIndex = true)
    public static final class EpilogForException {
        @Specialization
        public static void perform(
                AbstractTruffleException exception,
                @Bind VirtualFrame frame,
                @Bind ProtosBytecodeRootNode root,
                @Bind BytecodeNode bytecodeNode) {
            if (root.needsTraceAndProfileInstrumentation()) {
                root.traceOrProfileException(frame, bytecodeNode, exception);
            }
            // I115: a guest exception definitively abandons this activation.
            // PRUIBA DE I115, en algún momento esto volverá a funcionar
            // closeOwnedReturnHome(frame, null);
        }
    }

    /*
     * I115 (TruffleRuby FrameOnStackMarker): closes the materialized ReturnHome
     * this activation owns when it terminates. A pending result (Bytecode DSL
     * suspension or terminal transport) is not a termination; captured homes
     * and the unobservable marker are never owned; closing is idempotent.
     */
    static void closeOwnedReturnHome(VirtualFrame frame, Object result) {
        if (result != null && ProtosTailCallContinuation.isSuspended(result)) {
            return;
        }
        ProtosReturnHome owned =
                ProtosFrameArguments.ownedReturnHomeOrNull(frame.getArguments());
        if (owned != null) {
            owned.closeOwned();
        }
    }

    @Operation
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
                 * I115: every caller-reference consumer is a send or call
                 * selection that reads this root's prelude; an inherited one
                 * its caller did not transport is this root's caller need.
                 */
                Object[] arguments = frame.getArguments();
                if (ProtosFrameArguments.inheritsUntransportedCallerPrelude(arguments)) {
                    root.requestCallerNeed(NEEDS_CALLER_PRELUDE);
                }
                return arguments;
            }
            // I115: publishing this root's own activation is a caller need.
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
     * I115 parameter operations. Supplied positional values keep their B5
     * offsets after publication, so a parameter is read from the frame
     * arguments in every state and stored immediately in its local or cell by
     * the lowering; no activation is materialized to bind a parameter. D203:
     * a position the call did not supply reads canonical null; no
     * Closure-binding operation signals an argument-count Error.
     */

    /** Supplied positional argument {@code index}, or canonical null (D203). */
    @Operation
    @ConstantOperand(type = int.class, name = "index")
    public static final class LoadSuppliedArgument {
        @Specialization
        public static Object perform(int index, @Bind VirtualFrame frame) {
            return ProtosFrameArguments.compactSuppliedArgumentOrNull(frame.getArguments(), index);
        }
    }

    /** Whether the call supplied positional argument {@code index}. */
    @Operation
    @ConstantOperand(type = int.class, name = "index")
    public static final class HasSuppliedArgument {
        @Specialization
        public static boolean perform(int index, @Bind VirtualFrame frame) {
            return ProtosFrameArguments.suppliedArgumentCount(frame.getArguments()) > index;
        }
    }

    /**
     * The frozen Array of the supplied arguments after the first {@code
     * positionalParametersBeforeRest}, the value of a rest parameter.
     */
    @Operation
    @ConstantOperand(type = int.class, name = "positionalParametersBeforeRest")
    public static final class LoadRestArguments {
        @Specialization
        public static Object perform(int positionalParametersBeforeRest, @Bind VirtualFrame frame) {
            return ProtosFrameArguments.restArgumentArray(
                    frame.getArguments(), positionalParametersBeforeRest);
        }
    }

    // ---- I115 lexical cells --------------------------------------------------------

    /*
     * I115 (GraalPy cell variables): a Closure binding captured by a nested
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
    @Operation
    @ConstantOperand(type = int.class, name = "index")
    public static final class LoadCapturedCell {
        @Specialization
        public static ProtosLexicalCell perform(int index, @Bind VirtualFrame frame) {
            return ProtosFrameArguments.capturedCells(frame.getArguments())[index];
        }
    }

    /**
     * The value of a cell. A read before the binding point signals the
     * unqualified-lookup Error at this boundary.
     */
    @Operation
    public static final class LoadCell {
        @Specialization
        public static Object perform(ProtosLexicalCell cell, @Bind VirtualFrame frame) {
            Object value = cell.valueOrNullForRuntime();
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

    /** Stores the guest value {@code value} into {@code cell}. */
    @Operation
    public static final class StoreCell {
        @Specialization
        public static void perform(ProtosLexicalCell cell, Object value) {
            cell.setForRuntime(value);
        }
    }

    /**
     * An access to a LOCAL binding that the lowering proved precedes its
     * binding point: the uninitialized-binding Error, with no lookup.
     */
    @Operation
    public static final class UninitializedBinding {
        @Specialization
        public static Object perform(@Bind VirtualFrame frame) {
            throw uninitializedCellError(frame.getArguments());
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

    // ---- Lexical lookup and frame-backed bindings ----------------------------------

    /**
     * I113: the residual name-keyed lexical accesses, selected by the
     * builder-fixed {@link ProtosNamedLexicalAccess}: the unqualified lookup,
     * the captured-binding fallback, or the generic write-target resolution
     * (which stays separate from its assignment).
     */
    @Operation
    @ConstantOperand(type = ProtosNamedLexicalAccess.class, name = "access")
    public static final class AccessNamedLexical {
        @Specialization
        public static Object perform(
                ProtosNamedLexicalAccess access, ProtosActivation activation, String name) {
            return access.access(activation, name);
        }
    }

    /**
     * I115: the assignment of a statically classified module (ROOT) NAME
     * binding through the destination {@code SelectWriteGlobal} selected
     * before the right-hand side ran. The value is stored through the
     * module context's authoritative assignment and is the operation's
     * result. A destination made unwritable meanwhile (a frozen context or a
     * removed binding) signals the assignment Error, exactly as the generic
     * NAME assignment does; no other owner is searched.
     */
    @Operation
    public static final class WriteGlobal {
        @Specialization
        public static Object perform(
                ProtosModuleBindingLocation destination,
                Object value,
                @Bind VirtualFrame frame) {
            assignGlobal(frame.getArguments(), destination, value);
            return value;
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
     * PERF030-I / I115 multiple creation of LOCAL/CELL bindings. The lowerer
     * splits it into one prefix observation, which observes the complete
     * fixed source prefix before any binding is created, followed by one
     * store per name, in source order, into that binding's own storage.
     */

    /** The observed prefix of a multiple creation. */
    @Operation
    public static final class ObserveMultipleCreatePrefix {
        @Specialization
        public static Object[] perform(ProtosActivation activation, int required, Object source) {
            return ProtosFrameLocalBindings.observeMultipleCreatePrefix(activation, required, source)
                    .toArray();
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

    @Operation
    public static final class RequireObjectMutationTarget {
        @Specialization
        public static ProtosObjectValue perform(ProtosActivation activation, Object target) {
            // I091: a large Integer is a frozen object physically but an immutable Integer value.
            if (target instanceof ProtosObjectValue object
                    && !ProtosNumericValueSupport.isCurrentNumber(target)) {
                return object;
            }
            throw new ProtosSignalException(ProtosCoreErrors.newError(activation));
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
     * {@link ProtosSlotMutation}: creation or assignment on an explicit object
     * target, or assignment through a resolved generic write target.
     */
    @Operation
    @ConstantOperand(type = ProtosSlotMutation.class, name = "mutation")
    public static final class MutateLocalSlot {
        @Specialization
        public static Object objectTarget(
                ProtosSlotMutation mutation,
                ProtosActivation activation,
                ProtosObjectValue target,
                String name,
                Object value) {
            try {
                if (mutation == ProtosSlotMutation.CREATE) {
                    target.createLocalSlot(name, value);
                } else {
                    target.assignLocalSlot(name, value);
                }
            } catch (IllegalStateException invalidMutation) {
                throw new ProtosSignalException(ProtosCoreErrors.newError(activation));
            }
            return value;
        }

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
     * I115-H8 EXPERIMENTAL: GraalPy-shaped SetAttribute.
     *
     * The common specialization caches one receiver and its existing
     * physical SlotCell. The slow path handles unsupported representations
     * and errors. This deliberately relaxes frozen-state checking and
     * permits an internal Integer carrier to inhabit the selected slot.
     */
    @Operation
    @ConstantOperand(type = String.class, name = "name")
    public static final class ExperimentalSetAttribute {
        /*
         * I115-H10-A: signed-64 primitive SetAttribute.
         * Uses the same authoritative DynamicObject storage as
         * GetAttribute, without any 32-bit narrowing.
         */
        @Specialization(
                guards = {
                    "receiver == cachedReceiver",
                    "cachedSelection != null"
                },
                assumptions = "cachedSelection.stability()",
                limit = "3")
        public static long directLong(
                String name,
                Object receiver,
                long value,
                @Cached("receiver") Object cachedReceiver,
                @Cached("writableSelection(receiver, name)")
                        ProtosValueLookup.GuardedSlotSelection cachedSelection,
                @Cached DynamicObject.PutNode putNode) {
            ProtosValueLookup.experimentalWriteSelected(
                    cachedSelection, name, value, putNode);
            return value;
        }

        @Specialization(
                guards = {
                    "receiver == cachedReceiver",
                    "cachedSelection != null"
                },
                assumptions = "cachedSelection.stability()",
                replaces = "directLong",
                limit = "3")
        public static Object direct(
                String name,
                Object receiver,
                Object value,
                @Cached("receiver") Object cachedReceiver,
                @Cached("writableSelection(receiver, name)")
                        ProtosValueLookup.GuardedSlotSelection cachedSelection,
                @Cached DynamicObject.PutNode putNode) {
            return ProtosValueLookup.experimentalWriteSelected(
                    cachedSelection, name, value, putNode);
        }

        @Specialization(replaces = "direct")
        public static Object generic(
                String name,
                Object receiver,
                Object value,
                @Bind VirtualFrame frame) {
            if (receiver instanceof ProtosObjectValue object
                    && !ProtosNumericValueSupport.isCurrentNumber(receiver)) {
                try {
                    // Non-admitted receivers keep their ordinary guest
                    // value representation and mutation checks.
                    object.assignLocalSlot(
                            name, ProtosNumericValueSupport.guestValue(value));
                    return value;
                } catch (IllegalStateException invalidMutation) {
                    // The exact activation is needed on an error only.
                }
            }
            throw new ProtosSignalException(
                    ProtosCoreErrors.newError(
                            ProtosFrameArguments.activation(
                                    frame.getArguments())));
        }

        static ProtosValueLookup.GuardedSlotSelection writableSelection(
                Object receiver, String name) {
            return ProtosValueLookup.experimentalWritableSelection(
                    receiver, name);
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
     * I115-H10-A: signed-64 GetAttribute with Object fallback.
     * Uses the same authoritative Shape location as SetAttribute.
     */
    @Operation
    @ConstantOperand(type = String.class, name = "name")
    public static final class ExperimentalGetAttribute {
        @Specialization(
                guards = {
                    "receiver == cachedReceiver",
                    "cachedLookup != null",
                    "cachedLookup.nonClosureContinuity() != null",
                    "cachedGetter != null",
                    "cachedGetter.accepts(cachedLookup.storage())"
                },
                assumptions = {
                    "cachedLookup.stability()",
                    "cachedLookup.nonClosureContinuity()"
                },
                rewriteOn = UnexpectedResultException.class,
                limit = "3")
        public static long doLong(
                String name,
                Object receiver,
                @Bind VirtualFrame frame,
                @Cached("receiver") Object cachedReceiver,
                @Cached("createGuardedLookup(receiver, name, frame.getArguments())")
                        ProtosValueLookup.GuardedSlotSelection cachedLookup,
                @Cached("cachedLookup.propertyGetter()") PropertyGetter cachedGetter)
                throws UnexpectedResultException {
            return cachedGetter.getLong(cachedLookup.storage());
        }

        @Specialization(
                guards = {
                    "receiver == cachedReceiver",
                    "cachedLookup != null",
                    "cachedLookup.nonClosureContinuity() != null",
                    "cachedGetter != null",
                    "cachedGetter.accepts(cachedLookup.storage())"
                },
                assumptions = {
                    "cachedLookup.stability()",
                    "cachedLookup.nonClosureContinuity()"
                },
                replaces = "doLong",
                limit = "3")
        public static Object doObjectCached(
                String name,
                Object receiver,
                @Bind VirtualFrame frame,
                @Cached("receiver") Object cachedReceiver,
                @Cached("createGuardedLookup(receiver, name, frame.getArguments())")
                        ProtosValueLookup.GuardedSlotSelection cachedLookup,
                @Cached("cachedLookup.propertyGetter()") PropertyGetter cachedGetter) {
            return ProtosNumericValueSupport.guestValue(
                    cachedGetter.get(cachedLookup.storage()));
        }

        @Specialization(replaces = {"doLong", "doObjectCached"})
        public static Object doGeneric(
                String name,
                Object receiver,
                @Bind VirtualFrame frame) {
            return ReadMemberAtRoot.perform(name, receiver, frame);
        }

        static ProtosValueLookup.GuardedSlotSelection createGuardedLookup(
                Object receiver, String name, Object[] arguments) {
            return ProtosValueLookup.experimentalPrimitiveReadableSelection(
                    receiver, name, ProtosFrameArguments.preludeOrNull(arguments));
        }
    }

    /**
     * PERF037-B root-level {@link ReadMember}: the lowerer emits it whenever
     * the current activation is the root's own (frame argument 0). The PIC
     * tiers are those of {@link ReadMember}, but neither selection nor a
     * successful read observes the activation: selection takes the prelude
     * the invocation's activation has or would have, and a valid hit loads
     * the selected binding's stable location. Only an absent member or an
     * unsupported representation materializes the exact activation, from
     * which its Error is raised.
     */
    @Operation
    @ConstantOperand(type = String.class, name = "name")
    public static final class ReadMemberAtRoot {
        @Specialization(
                guards = {
                    "cachedLookup != null",
                    "matchesSharedInheritedLookup(receiver, name, cachedLookup)",
                    "cachedLookup.nonClosureContinuity() != null"
                },
                assumptions = {
                    "cachedLookup.stability()",
                    "cachedLookup.nonClosureContinuity()"
                },
                limit = "3")
        public static Object guardedSharedInheritedPlain(
                String name,
                Object receiver,
                @Bind VirtualFrame frame,
                @Cached("createSharedInheritedLookup(receiver, name, frame.getArguments())")
                        ProtosValueLookup.SharedInheritedSlotSelection cachedLookup) {
            return ProtosValueLookup.guardedPlainSlotValue(
                    cachedLookup.parentSelection());
        }

        /**
         * PERF037: no method-binding branch in the common data-member
         * path. Truffle invalidates this specialization before a selected
         * slot can transition from data to Closure.
         */
        @Specialization(
                guards = {
                    "cachedLookup != null",
                    "matchesSharedInheritedLookup(receiver, name, cachedLookup)"
                },
                assumptions = "cachedLookup.stability()",
                limit = "3")
        public static Object guardedSharedInherited(
                String name,
                Object receiver,
                @Bind VirtualFrame frame,
                @Cached("createSharedInheritedLookup(receiver, name, frame.getArguments())")
                        ProtosValueLookup.SharedInheritedSlotSelection cachedLookup,
                @Bind Node node,
                @Cached InlinedBranchProfile closureExtraction) {
            return ProtosValueLookup.materializeGuardedMemberRead(
                    receiver, cachedLookup, node, closureExtraction);
        }

        /**
         * PERF037: no method-binding branch in the common data-member
         * path. Truffle invalidates this specialization before a selected
         * slot can transition from data to Closure.
         */
        /*
         * I115: primitive-long GetAttribute. Read the current
         * selected location on each execution; never cache
         * the value itself. A change of representation falls
         * back to the existing object specialization.
         */
        @Specialization(
                guards = {
                    "receiver == cachedReceiver",
                    "cachedLookup != null",
                    "cachedLookup.nonClosureContinuity() != null",
                    "selectedLong(cachedLookup)"
                },
                assumptions = {
                    "cachedLookup.stability()",
                    "cachedLookup.nonClosureContinuity()"
                },
                limit = "3")
        public static long guardedExactReceiverLong(
                String name,
                Object receiver,
                @Bind VirtualFrame frame,
                @Cached("receiver") Object cachedReceiver,
                @Cached("createGuardedLookup(receiver, name, frame.getArguments())")
                        ProtosValueLookup.GuardedSlotSelection cachedLookup) {
            return ((Long) cachedLookup.currentValue()).longValue();
        }

        @Specialization(
                guards = {
                    "receiver == cachedReceiver",
                    "cachedLookup != null",
                    "cachedLookup.nonClosureContinuity() != null"
                },
                assumptions = {
                    "cachedLookup.stability()",
                    "cachedLookup.nonClosureContinuity()"
                },
                replaces = "guardedExactReceiverLong",
                limit = "3")
        public static Object guardedExactReceiverPlain(
                String name,
                Object receiver,
                @Bind VirtualFrame frame,
                @Cached("receiver") Object cachedReceiver,
                @Cached("createGuardedLookup(receiver, name, frame.getArguments())")
                        ProtosValueLookup.GuardedSlotSelection cachedLookup) {
            return ProtosValueLookup.guardedPlainSlotValue(cachedLookup);
        }

        @Specialization(
                guards = {
                    "receiver == cachedReceiver",
                    "cachedLookup != null"
                },
                assumptions = "cachedLookup.stability()",
                limit = "3")
        public static Object guardedExactReceiver(
                String name,
                Object receiver,
                @Bind VirtualFrame frame,
                @Cached("receiver") Object cachedReceiver,
                @Cached("createGuardedLookup(receiver, name, frame.getArguments())")
                        ProtosValueLookup.GuardedSlotSelection cachedLookup,
                @Bind Node node,
                @Cached InlinedBranchProfile closureExtraction) {
            return ProtosValueLookup.materializeGuardedMemberRead(
                    receiver, cachedLookup, node, closureExtraction);
        }

        @Specialization(replaces = {"guardedSharedInheritedPlain", "guardedSharedInherited", "guardedExactReceiverPlain", "guardedExactReceiver"})
        public static Object perform(
                String name,
                Object receiver,
                @Bind VirtualFrame frame) {
            Object[] arguments = frame.getArguments();
            Object value =
                    ReadMember.readMemberOrNull(
                            receiver, name, ProtosFrameArguments.preludeOrNull(arguments));
            if (value != null) {
                return value;
            }
            return ReadMember.performForActivation(
                    ProtosFrameArguments.activation(arguments), receiver, name);
        }

        static ProtosValueLookup.SharedInheritedSlotSelection createSharedInheritedLookup(
                Object receiver, String name, Object[] arguments) {
            return ReadMember.createSharedInheritedLookupForPrelude(
                    receiver, name, ProtosFrameArguments.preludeOrNull(arguments));
        }

        static boolean matchesSharedInheritedLookup(
                Object receiver,
                String name,
                ProtosValueLookup.SharedInheritedSlotSelection cachedLookup) {
            return ReadMember.matchesSharedInheritedLookup(
                    receiver, name, cachedLookup);
        }

        @NonIdempotent
        static boolean selectedLong(
                ProtosValueLookup.GuardedSlotSelection selection) {
            return selection.currentValue() instanceof Long;
        }

        static ProtosValueLookup.GuardedSlotSelection createGuardedLookup(
                Object receiver, String name, Object[] arguments) {
            return ReadMember.createGuardedLookupForPrelude(
                    receiver, name, ProtosFrameArguments.preludeOrNull(arguments));
        }
    }

    @Operation
    @ConstantOperand(type = String.class, name = "name")
    public static final class ReadMember {
        @Specialization(
                guards = {
                    "cachedLookup != null",
                    "matchesSharedInheritedLookup(receiver, name, cachedLookup)",
                    "cachedLookup.nonClosureContinuity() != null"
                },
                assumptions = {
                    "cachedLookup.stability()",
                    "cachedLookup.nonClosureContinuity()"
                },
                limit = "3")
        public static Object guardedSharedInheritedPlain(
                String name,
                ProtosActivation activation,
                Object receiver,
                @Cached("createSharedInheritedLookup(receiver, name, activation)")
                        ProtosValueLookup.SharedInheritedSlotSelection cachedLookup) {
            return ProtosValueLookup.guardedPlainSlotValue(
                    cachedLookup.parentSelection());
        }

        @Specialization(
                guards = {
                    "cachedLookup != null",
                    "matchesSharedInheritedLookup(receiver, name, cachedLookup)"
                },
                assumptions = "cachedLookup.stability()",
                limit = "3")
        public static Object guardedSharedInherited(
                String name,
                ProtosActivation activation,
                Object receiver,
                @Cached("createSharedInheritedLookup(receiver, name, activation)")
                        ProtosValueLookup.SharedInheritedSlotSelection cachedLookup,
                @Bind Node node,
                @Cached InlinedBranchProfile closureExtraction) {
            return ProtosValueLookup.materializeGuardedMemberRead(
                    receiver, cachedLookup, node, closureExtraction);
        }

        @Specialization(
                guards = {
                    "receiver == cachedReceiver",
                    "cachedLookup != null",
                    "cachedLookup.nonClosureContinuity() != null"
                },
                assumptions = {
                    "cachedLookup.stability()",
                    "cachedLookup.nonClosureContinuity()"
                },
                limit = "3")
        public static Object guardedExactReceiverPlain(
                String name,
                ProtosActivation activation,
                Object receiver,
                @Cached("receiver") Object cachedReceiver,
                @Cached("createGuardedLookup(receiver, name, activation)")
                        ProtosValueLookup.GuardedSlotSelection cachedLookup) {
            return ProtosValueLookup.guardedPlainSlotValue(cachedLookup);
        }

        @Specialization(
                guards = {
                    "receiver == cachedReceiver",
                    "cachedLookup != null"
                },
                assumptions = "cachedLookup.stability()",
                limit = "3")
        public static Object guardedExactReceiver(
                String name,
                ProtosActivation activation,
                Object receiver,
                @Cached("receiver") Object cachedReceiver,
                @Cached("createGuardedLookup(receiver, name, activation)")
                        ProtosValueLookup.GuardedSlotSelection cachedLookup,
                @Bind Node node,
                @Cached InlinedBranchProfile closureExtraction) {
            return ProtosValueLookup.materializeGuardedMemberRead(
                    receiver, cachedLookup, node, closureExtraction);
        }

        @Specialization(
                replaces = {
                    "guardedSharedInheritedPlain", "guardedSharedInherited",
                    "guardedExactReceiverPlain", "guardedExactReceiver"
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

        static ProtosValueLookup.SharedInheritedSlotSelection createSharedInheritedLookup(
                Object receiver,
                String name,
                ProtosActivation activation) {
            return createSharedInheritedLookupForPrelude(
                    receiver,
                    name,
                    activation.preludeOrNullForRuntime());
        }

        static boolean matchesSharedInheritedLookup(
                Object receiver,
                String name,
                ProtosValueLookup.SharedInheritedSlotSelection cachedLookup) {
            return ProtosValueLookup.matchesGuardedSharedInheritedSlotSelection(
                    receiver,
                    name,
                    cachedLookup);
        }

        static ProtosValueLookup.GuardedSlotSelection createGuardedLookup(
                Object receiver,
                String name,
                ProtosActivation activation) {
            return createGuardedLookupForPrelude(
                    receiver,
                    name,
                    activation.preludeOrNullForRuntime());
        }

        /**
         * PERF025-H3A shared inherited-member PIC. Distinct exact ordinary
         * receivers may share one selection when they have the same exact
         * direct parent and none owns the selector locally. The D013
         * dependency starts at the parent; local shadowing is guarded per
         * receiver and therefore does not invalidate unrelated siblings.
         */
        public static Object guardedSharedInheritedPlainForActivation(
                ProtosActivation activation,
                Object receiver,
                String name,
                String cachedName,
                ProtosValueLookup.SharedInheritedSlotSelection cachedLookup) {
            return ProtosValueLookup.guardedPlainSlotValue(
                    cachedLookup.parentSelection());
        }

        /**
         * PERF025 ordinary exact-receiver member-read PIC. Selection is cached,
         * never the semantically observable extracted value. In particular a
         * selected Closure is rebound through materializeMemberRead on every
         * hit, so CALLABLES fresh receiver-bound extraction identity remains
         * exact.
         */
        public static Object guardedSharedInheritedForActivation(
                ProtosActivation activation,
                Object receiver,
                String name,
                String cachedName,
                ProtosValueLookup.SharedInheritedSlotSelection cachedLookup,
                Node node,
                InlinedBranchProfile closureExtraction) {
            return ProtosValueLookup.materializeGuardedMemberRead(
                    receiver, cachedLookup, node, closureExtraction);
        }

        /**
         * PERF025 ordinary exact-receiver member-read PIC. Selection is cached,
         * never the semantically observable extracted value. In particular a
         * selected Closure is rebound through materializeMemberRead on every
         * hit, so CALLABLES fresh receiver-bound extraction identity remains
         * exact.
         */
        public static Object guardedExactReceiverPlainForActivation(
                ProtosActivation activation,
                Object receiver,
                String name,
                Object cachedReceiver,
                String cachedName,
                ProtosValueLookup.GuardedSlotSelection cachedLookup) {
            return ProtosValueLookup.guardedPlainSlotValue(cachedLookup);
        }

        public static Object guardedExactReceiverForActivation(
                ProtosActivation activation,
                Object receiver,
                String name,
                Object cachedReceiver,
                String cachedName,
                ProtosValueLookup.GuardedSlotSelection cachedLookup,
                Node node,
                InlinedBranchProfile closureExtraction) {
            return ProtosValueLookup.materializeGuardedMemberRead(
                    receiver, cachedLookup, node, closureExtraction);
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

        static ProtosValueLookup.SharedInheritedSlotSelection createSharedInheritedLookupForPrelude(
                Object receiver,
                String name,
                ProtosPrelude prelude) {
            return ProtosValueLookup.lookupGuardedSharedInheritedSlotSelection(receiver, name, prelude);
        }

        static ProtosValueLookup.GuardedSlotSelection createGuardedLookupForPrelude(
                Object receiver,
                String name,
                ProtosPrelude prelude) {
            try {
                return ProtosValueLookup.lookupGuardedSlotSelection(receiver, name, prelude);
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
                    ? ProtosBooleanValue.TRUE
                    : ProtosBooleanValue.FALSE;
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
            return validated == ProtosBooleanValue.TRUE
                    ? ProtosBooleanValue.FALSE
                    : ProtosBooleanValue.TRUE;
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

    @Operation
    public static final class RaiseNonLocalReturn {
        @Specialization
        public static Object perform(ProtosActivation activation, Object value) {
            ProtosReturnHome target =
                    activation.returnHome().orElse(null);
            /*
             * I115 (TruffleSqueak): a finished, unreachable or unobservable
             * destination is an InvalidReturn Error; the target identity is
             * never replaced while propagating.
             */
            if (target == null || !target.isMaterialized() || !target.isActive()) {
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
                    executionPlanCell.plan());
        }
    }

    /**
     * Root-level Closure creation (I092, I112). The creating activation is
     * this root's own frame argument 0, bound from the frame rather than
     * loaded as a Bytecode operand. A published activation is captured
     * unchanged; a still-compact root captures without materializing only
     * when {@code compactCapture} (the I092 admission: a genuine root that
     * declares no frame-owned lexical bindings) holds, and otherwise
     * materializes and publishes its exact activation first, exactly as
     * {@link CurrentActivation} followed by {@link MaterializeClosure}.
     *
     * <p>I115 / PLAT066-C: {@code capturesCreatorContext} is the lowering's
     * classification of this exact Closure ({@code
     * CanonicalCompilationFacts#capturesCreatorContext}). When it is false
     * the operation is GraalPy's {@code MakeFunction} with empty {@code
     * freevars}: the Closure receives its creator's own captured environment
     * and the creator's activation is neither materialized nor published,
     * and no compact capture owner is created. That classification is a
     * recorded divergence from the unconditional capture of {@code
     * CALLABLES.md}, pending an owner decision.
     *
     * <p>I115-H (EXPERIMENTAL): when {@code mayObserveReturnHome} (the
     * lowering's {@code CanonicalCompilationFacts#mayObserveReturnHome}) is
     * also false, the single {@code nonCapturing} specialization builds the
     * lightweight {@link ProtosClosureValue#experimentalNonCapturing}
     * representation without binding the frame, so neither the creator's
     * activation nor its frame arguments are consulted. A
     * definition that may observe its ReturnHome ({@code ^}) keeps the I115-G
     * construction with the creator's ReturnHome. The capturing
     * specializations are unchanged.
     */
    @Operation
    @ConstantOperand(type = boolean.class, name = "compactCapture")
    @ConstantOperand(type = boolean.class, name = "capturesCreatorContext")
    @ConstantOperand(type = boolean.class, name = "mayObserveReturnHome")
    public static final class MaterializeCurrentClosure {
        /*
         * I115: each source-literal plan cell is frozen before guest execution.
         * Cache the plan by cell identity so a fresh non-capturing Closure does
         * not reload the cell at each invocation. This cache stores only the
         * plan, never the Closure or any lexical/caller state. Other cells,
         * including projected or rematerialized definitions, remain distinct.
         */
        @Specialization(
                guards = {
                    "!capturesCreatorContext",
                    "!mayObserveReturnHome",
                    "executionPlanCell == cachedCell"
                },
                limit = "3")
        public static ProtosClosureValue nonCapturingCached(
                boolean compactCapture,
                boolean capturesCreatorContext,
                boolean mayObserveReturnHome,
                CanonicalClosure definition,
                ProtosClosureExecutionPlanCell executionPlanCell,
                @Cached("executionPlanCell") ProtosClosureExecutionPlanCell cachedCell,
                @Cached("cachedCell.plan()") ProtosClosureExecutionPlan cachedPlan) {
            return ProtosClosureValue.experimentalNonCapturing(
                    definition, cachedPlan);
        }

        @Specialization(
                guards = {
                    "!capturesCreatorContext",
                    "!mayObserveReturnHome"
                },
                replaces = "nonCapturingCached")
        public static ProtosClosureValue nonCapturing(
                boolean compactCapture,
                boolean capturesCreatorContext,
                boolean mayObserveReturnHome,
                CanonicalClosure definition,
                ProtosClosureExecutionPlanCell executionPlanCell) {
            return ProtosClosureValue.experimentalNonCapturing(
                    definition, executionPlanCell.plan());
        }

        @Specialization(guards = {
                "!capturesCreatorContext",
                "mayObserveReturnHome",
                "hasPublishedActivation(frame)"})
        public static ProtosClosureValue publishedWithoutCreatorContext(
                boolean compactCapture,
                boolean capturesCreatorContext,
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
                    executionPlanCell.plan());
        }

        @Specialization(guards = {
                "!capturesCreatorContext",
                "mayObserveReturnHome",
                "!hasPublishedActivation(frame)"})
        public static ProtosClosureValue compactWithoutCreatorContext(
                boolean compactCapture,
                boolean capturesCreatorContext,
                boolean mayObserveReturnHome,
                CanonicalClosure definition,
                ProtosClosureExecutionPlanCell executionPlanCell,
                @Bind VirtualFrame frame) {
            return ProtosFrameArguments.materializeCompactClosure(
                    frame.getArguments(), definition, executionPlanCell,
                    false, mayObserveReturnHome);
        }

        @Specialization(guards = {
                "capturesCreatorContext",
                "hasPublishedActivation(frame)",
                "hasMaterializedCaptureContext(frame)"})
        public static ProtosClosureValue publishedMaterialized(
                boolean compactCapture,
                boolean capturesCreatorContext,
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
                    executionPlanCell.plan());
        }

        @Specialization(guards = {
                "capturesCreatorContext",
                "hasPublishedActivation(frame)",
                "!hasMaterializedCaptureContext(frame)"})
        public static ProtosClosureValue publishedGeneral(
                boolean compactCapture,
                boolean capturesCreatorContext,
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
                    executionPlanCell.plan());
        }

        @Specialization(guards = {
                "capturesCreatorContext",
                "compactCapture",
                "!hasPublishedActivation(frame)"})
        public static ProtosClosureValue compact(
                boolean compactCapture,
                boolean capturesCreatorContext,
                boolean mayObserveReturnHome,
                CanonicalClosure definition,
                ProtosClosureExecutionPlanCell executionPlanCell,
                @Bind VirtualFrame frame) {
            return ProtosFrameArguments.materializeCompactClosure(
                    frame.getArguments(), definition, executionPlanCell,
                    true, mayObserveReturnHome);
        }

        @Specialization(guards = {
                "capturesCreatorContext",
                "!compactCapture",
                "!hasPublishedActivation(frame)"})
        public static ProtosClosureValue rich(
                boolean compactCapture,
                boolean capturesCreatorContext,
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
                target.composeLocalSlotsFrom(source, reservedNames.names());
            } catch (IllegalStateException failure) {
                throw new ProtosSignalException(ProtosCoreErrors.newError(activation));
            }
            return target;
        }
    }

    // ---- Map construction ----------------------------------------------------------

    /**
     * Prepares the Map construction factory call on {@code value} (the
     * factory), or with {@code finish} validates its result {@code value}.
     * The guest factory call runs between the two instructions.
     */
    @Operation
    @ConstantOperand(type = boolean.class, name = "finish")
    public static final class MapConstructionFactoryCall {
        @Specialization
        public static Object perform(boolean finish, ProtosActivation activation, Object value) {
            if (finish) {
                return ProtosStandardMapProtocol
                        .requireMapConstructionResult(
                                value,
                                activation);
            }
            ProtosSlotLookupResult selected =
                    ProtosStandardMapProtocol
                            .selectFactoryCallForMapConstruction(
                                    value,
                                    activation);

            return ProtosCallRequest.method(selected, value, List.of(), activation);
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
            return vector.snapshot().toArray();
        }
    }


    /**
     * I113: classifies one result value as the builder-fixed
     * {@link ProtosResultKind} (a miss sentinel, a carrier type or a C-prime
     * gate); see that type for each predicate.
     */
    @Operation
    @ConstantOperand(type = ProtosResultKind.class, name = "kind")
    public static final class IsResultKind {
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
                    "kind == CONTINUATION_KIND",
                    "value != null",
                    "value.getClass() == cachedClass"
                },
                limit = "3")
        public static boolean continuationClassProfiled(
                ProtosResultKind kind,
                Object value,
                @Cached("value.getClass()") Class<?> cachedClass,
                @Cached("isContinuationClass(cachedClass)") boolean continuation) {
            return continuation;
        }

        @Specialization(replaces = "continuationClassProfiled")
        public static boolean perform(ProtosResultKind kind, Object value) {
            return kind.matches(value);
        }

        static final ProtosResultKind CONTINUATION_KIND = ProtosResultKind.CONTINUATION;

        static boolean isContinuationClass(Class<?> type) {
            return IsContinuation.isContinuationClass(type);
        }
    }

    /**
     * I113: whether a structured native invocation is the protocol named by the
     * builder-fixed {@code kind}. The predicates are owned by
     * {@link ProtosStructuredCallKind}; {@code kind} is a constant operand,
     * so partial evaluation folds the selection at every site.
     */
    @Operation
    @ConstantOperand(type = ProtosStructuredCallKind.class, name = "kind")
    public static final class IsStructuredCall {
        @Specialization
        public static boolean perform(
                ProtosStructuredCallKind kind, StructuredNativeInvocation prepared) {
            return kind.admits(prepared);
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
     * Prepares the structured state of the protocol named by the
     * builder-fixed {@code kind}; see {@link ProtosStructuredCallKind#prepare}.
     */
    @Operation
    @ConstantOperand(type = ProtosStructuredCallKind.class, name = "kind")
    public static final class PrepareStructuredCall {
        @Specialization
        public static Object perform(
                ProtosStructuredCallKind kind, StructuredNativeInvocation prepared) {
            return kind.prepare(prepared);
        }
    }


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
                    prepared.callback(), List.of(prepared.current()), prepared.activation());
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
        public static ProtosCallRequest whileLoop(
                ProtosStructuredPhase phase,
                PreparedWhileCall prepared) {
            return phase == ProtosStructuredPhase.SECONDARY_CHILD
                    ? prepared.prepareBody()
                    : prepared.prepareCondition();
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
     * Accepts the result of a structured protocol's guest child, by the
     * builder-fixed phase: {@code CHILD} the matcher or lower outcome;
     * {@code HASH}/{@code EQUALITY} a Map protocol's guest hash or equality
     * result.
     */
    @Operation
    @ConstantOperand(type = ProtosStructuredPhase.class, name = "phase")
    public static final class StructuredAcceptChildOutcome {
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

        @Specialization
        public static Object whileLoop(PreparedWhileCall prepared, Object result) {
            return prepared.result(result);
        }
    }

    /** Produces the protocol's result without a further guest child result. */
    @Operation
    public static final class StructuredFinish {
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


    // ---- PLAT044 B′ inline literal whileTrue (PERF026-C1) ---------------------------
    //
    // A selected standard whileTrue whose receiver and body are exactly the
    // send site's staged literals is sequenced locally in this root; every
    // other while keeps the structured-dispatch root. The loop state machine
    // and Boolean condition authority stay owned by
    // ProtosStructuredControlCalls.PreparedWhileCall.


    /**
     * D212: whether the prepared standard loop is post-test, so the lowering
     * runs the body before each condition. Fixed by the selected standard
     * behavior when the loop was prepared.
     */
    @Operation
    public static final class StructuredWhilePostTest {
        @Specialization
        public static boolean perform(PreparedWhileCall prepared) {
            return prepared.postTest();
        }
    }

    /**
     * I115-N: the Java boolean of a canonical Boolean for the structured
     * Bytecode branch of the unified Boolean control lowering. No
     * truthiness: a receiver or whileTrue condition result that is not exact
     * canonical true or false (for example the result of an ordinarily
     * dispatched comparison) signals the fresh standard Error of the standard
     * control behavior.
     */
    @Operation
    public static final class ExperimentalCanonicalBooleanCondition {
        @Specialization
        public static boolean doBoolean(ProtosBooleanValue value) {
            return value.value();
        }

        @Fallback
        public static boolean doOther(
                Object value,
                @Bind VirtualFrame frame,
                @Bind ProtosBytecodeRootNode root) {
            root.requestCallerNeed(NEEDS_EXACT_ACTIVATION);
            ProtosActivation activation = ProtosFrameArguments.activation(frame);
            // The signal of PreparedWhileCall.conditionResult.
            throw ProtosCoreErrors.signal(
                    activation, ProtosCoreErrors.newError(activation));
        }
    }

    @Operation
    public static final class StructuredWhileCondition {
        @Specialization
        public static boolean perform(PreparedWhileCall prepared, Object result) {
            return prepared.conditionResult(result);
        }
    }

    @Operation
    public static final class StructuredWhileRetainBodyResult {
        @Specialization
        public static Object perform(PreparedWhileCall prepared, Object bodyResult) {
            return prepared.retainedBodyResult(bodyResult);
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
    public static final class MaterializePrimitiveNumericCarrier {
        @Specialization
        public static Object perform(Object value, boolean carrierPermitted) {
            return carrierPermitted
                    ? ProtosNumericValueSupport.guestValue(value)
                    : value;
        }
    }

    // PRUIBA DE I115, en algún momento esto volverá a funcionar
    // /**
     // * I091: the implicit final value of a source root. A primitive numeric
     // * carrier is returned as is only to a fused direct source send that
     // * accepts it; every other caller receives the guest value.
     // */
    // public static final class MaterializeReturnCarrier {
        // public static Object perform(Object value, VirtualFrame frame) {
            // if (ProtosNumericValueSupport.isPrimitiveCarrier(value)
                    // && ProtosFrameArguments.acceptsPrimitiveResult(frame.getArguments())) {
                // return value;
            // }
            // return ProtosNumericValueSupport.guestValue(value);
        // }
    // }


    /**
     * I113: the continuation classification shared by
     * {@code IsResultKind[CONTINUATION]}.
     * Plain Java helpers; no longer a registered operation.
     */
    public static final class IsContinuation {
        public static boolean perform(Object value) {
            /*
             * A native suspension is the leaf request. Yielding it from this
             * root materializes the real C-prime ContinuationResult; callers
             * then continue to see only ordinary nested ContinuationResults.
             */
            return value instanceof ContinuationResult
                    || value instanceof ProtosTailCallContinuation
                    || value instanceof ProtosNativeSuspension
                    || value instanceof ProtosIoOperationSuspension
                    || value instanceof ProtosIoReleaseSuspension;
        }

        /** Exactly {@link IsContinuation#perform} for every instance of {@code type}. */
        static boolean isContinuationClass(Class<?> type) {
            return ContinuationResult.class.isAssignableFrom(type)
                    || ProtosTailCallContinuation.class.isAssignableFrom(type)
                    || ProtosNativeSuspension.class.isAssignableFrom(type)
                    || ProtosIoOperationSuspension.class.isAssignableFrom(type)
                    || ProtosIoReleaseSuspension.class.isAssignableFrom(type);
        }
    }


    /**
     * I115: performs a {@link ProtosCallRequest} produced by a structured
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
                @Cached ProtosFunctionCallDispatchers.CallNode callNode) {
            return callNode.execute(
                    frame, terminal, request.callable(), request.arguments(),
                    request.caller(), root);
        }
    }

    /** I115: the structured-protocol root is entered only for structured natives. */
    @Operation
    public static final class RejectUnstructuredInvocation {
        @Specialization
        public static Object perform(Object invocation) {
            throw CompilerDirectives.shouldNotReachHere(
                    "structured-protocol root entered for an unstructured invocation");
        }
    }

    /** I115: runs the module body of a structured standard import. */
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
     * I115 (GraalPy {@code super().method}): the super-send selection,
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
            return ProtosExperimentalGetMethodNode.selectSuper(
                    selector, root, ProtosFunctionCallDispatchers.callerReference(frame));
        }
    }

    /**
     * Resumes a continuation returned directly by a source CallTarget.
     *
     * The suspended callee
     * retains its own execution state; this operation only resumes
     * that state and propagates control transfers.
     */
    @Operation
    public static final class ResumeDispatchedContinuation {

        @Specialization
        public static Object terminal(
                ProtosTailCallContinuation pending,
                Object resumeValue) {
            try {
                return ProtosTailCallContinuation.resume(
                        pending, resumeValue);
            } catch (ProtosBytecodeControlTransferException bridged) {
                throw bridged.transfer();
            }
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
            } catch (ProtosBytecodeControlTransferException bridged) {
                throw bridged.transfer();
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
            } catch (ProtosBytecodeControlTransferException bridged) {
                throw bridged.transfer();
            }
        }

        /* A builtin suspension leaf resumes through its own descriptor. */
        @Fallback
        public static Object leaf(
                Object pending,
                Object resumeValue) {
            return ProtosTailCallContinuation.resumeLeaf(pending, resumeValue);
        }
    }


    /*
     * I111-B: the operations below were declared by the former untagged
     * structured-dispatch/C-prime interpreter. They are emitted only by
     * auxiliary roots (ProtosStructuredDispatchLowerer, the Task C-prime
     * entry plan and the I/O C-prime executions).
     */

    @Operation
    public static final class RethrowTruffleException {
        @Specialization
        public static void perform(AbstractTruffleException exception) {
            throw exception;
        }
    }

    @Operation
    public static final class IsCancellationUnwindActive {
        @Specialization
        public static boolean perform(ProtosActivation activation) {
            ProtosTask task = activation.task().orElse(null);
            return task != null
                    && task.cancellationPhase()
                            == ProtosTask.CancellationPhase.UNWINDING;
        }
    }

    @Operation
    public static final class SupersedeCancellationUnwindIfActive {
        @Specialization
        public static void perform(
                ProtosActivation activation,
                boolean cancellationWasUnwindingBeforeCleanup) {
            if (!cancellationWasUnwindingBeforeCleanup) {
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
                        List.of());
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
