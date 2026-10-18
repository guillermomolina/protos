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
import com.guillermomolina.protos.execution.ProtosStructuredEachCalls.PreparedArrayEachCall;
import com.guillermomolina.protos.execution.ProtosStructuredEachCalls.PreparedArrayMatchCall;
import com.guillermomolina.protos.execution.ProtosStructuredEachCalls.PreparedBytesEachCall;
import com.guillermomolina.protos.execution.ProtosStructuredEachCalls.PreparedEnvironmentEachCall;
import com.guillermomolina.protos.execution.ProtosStructuredEachCalls.PreparedIdentityMapAtIfAbsentCall;
import com.guillermomolina.protos.execution.ProtosStructuredEachCalls.PreparedIdentityMapEachCall;
import com.guillermomolina.protos.execution.ProtosStructuredEachCalls.PreparedMapEachCall;
import com.guillermomolina.protos.execution.ProtosStructuredMapQueryCalls.PreparedMapMatchCall;
import com.guillermomolina.protos.execution.ProtosStructuredMapQueryCalls.PreparedMapReadLookupCall;
import com.guillermomolina.protos.execution.ProtosStructuredMapUpdateCalls.PreparedMapAtPutCall;
import com.guillermomolina.protos.execution.ProtosStructuredMapUpdateCalls.PreparedMapRemoveCall;
import com.guillermomolina.protos.runtime.ProtosLexicalEnvironment;
import com.guillermomolina.protos.runtime.ProtosLexicalFallback;
import com.guillermomolina.protos.runtime.ProtosCoreErrors;
import com.guillermomolina.protos.runtime.ProtosIdentity;
import com.guillermomolina.protos.runtime.control.ProtosNonLocalReturnException;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.guillermomolina.protos.runtime.ProtosIoOperation;
import com.guillermomolina.protos.runtime.ProtosTask;
import java.util.List;
import com.guillermomolina.protos.execution.ProtosCapturedLexicalAccess.CapturedLexicalWriteTarget;
import com.guillermomolina.protos.execution.ProtosCapturedLexicalAccess.CapturedNearerScopeAbsence;
import com.guillermomolina.protos.execution.ProtosCapturedLexicalAccess.CapturedOwnerFrameCache;
import com.guillermomolina.protos.execution.ProtosClosureCallPreparation.GuardedDirectClosureCallTarget;
import com.guillermomolina.protos.execution.ProtosPreparedCalls.ImmediateResultCall;
import com.guillermomolina.protos.execution.ProtosPreparedCalls.ModuleInitializationCall;
import com.guillermomolina.protos.execution.ProtosPreparedCalls.NativeCall;
import com.guillermomolina.protos.execution.ProtosPreparedCalls.OrdinarySourceCall;
import com.guillermomolina.protos.execution.ProtosBytecodeRootNode.PrepareSendArguments.GuardedIntegerSend;
import com.guillermomolina.protos.execution.ProtosBytecodeRootNode.PrepareSendArguments.GuardedSendTarget;
import com.guillermomolina.protos.execution.ProtosBytecodeRootNode.PrepareSendArguments.GuardedStructuredSend;
import com.guillermomolina.protos.execution.ProtosPreparedCalls.PreparedArgumentVector;
import com.guillermomolina.protos.execution.ProtosStructuredControlCalls.PreparedBooleanCall;
import com.guillermomolina.protos.execution.ProtosPreparedCalls.PreparedClosureCall;
import com.guillermomolina.protos.execution.ProtosInlineCallbackFrameBindings.PreparedInlineLiteralCall;
import com.guillermomolina.protos.execution.ProtosStructuredEachCalls.PreparedIndexedEachCall;
import com.guillermomolina.protos.execution.ProtosStructuredEachCalls.PreparedLocalEachCall;
import com.guillermomolina.protos.execution.ProtosStructuredMapUpdateCalls.PreparedMapInitialDefinition;
import com.guillermomolina.protos.execution.ProtosStructuredControlCalls.PreparedWhileCall;
import com.guillermomolina.protos.execution.ProtosFrameLocalBindings.ResolvedLexicalWriteTarget;
import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosMapValue;
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
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.bytecode.BytecodeLocation;
import com.oracle.truffle.api.bytecode.BytecodeNode;
import com.oracle.truffle.api.bytecode.BytecodeRootNode;
import com.oracle.truffle.api.bytecode.ConstantOperand;
import com.oracle.truffle.api.bytecode.ContinuationResult;
import com.oracle.truffle.api.bytecode.ContinuationRootNode;
import com.oracle.truffle.api.bytecode.EpilogReturn;
import com.oracle.truffle.api.bytecode.GenerateBytecode;
import com.oracle.truffle.api.bytecode.LocalAccessor;
import com.oracle.truffle.api.bytecode.LocalRangeAccessor;
import com.oracle.truffle.api.bytecode.MaterializedLocalAccessor;
import com.oracle.truffle.api.bytecode.Operation;
import com.oracle.truffle.api.bytecode.OperationProxy;
import com.oracle.truffle.api.bytecode.Prolog;
import com.oracle.truffle.api.bytecode.Variadic;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
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
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;

/**
 * The single Protos Bytecode DSL interpreter (I111-B / PLAT065-A).
 *
 * <p>It serves two kinds of physical roots. A genuine guest-semantic
 * top-level, module or Closure activation produced by {@link
 * CanonicalToBytecodeLowerer} is marked {@link #isSemanticActivationRoot()}
 * and carries the explicit RootTag that lowerer emits; automatic root tagging
 * stays disabled, because this configuration covers every root of the
 * interpreter. Auxiliary structured-dispatch ({@link
 * ProtosStructuredDispatchLowerer}), Task C-prime entry and I/O C-prime roots
 * are created by their own builders, are unmarked and carry no tag, so they
 * never appear as guest frames, scopes or RootTag regions. Inline Closure
 * callbacks retain their nested semantic RootTag regions. The generated
 * interpreter does not infer guest identity from the mere existence of a
 * physical root. Object bodies are lowered inline
 * and never become roots. Lexically nested Closure roots are created in the same
 * {@code BytecodeRootNodes} parse as their owner, so PERF013 materialized
 * captured accesses use a {@link MaterializedLocalAccessor} of this interpreter
 * only against an owner frame of this interpreter.
 *
 * <p>Structured prepared invocations other than the PLAT043 standard Boolean
 * family are not executed inside a semantic root. {@link
 * EnterNestedStructuredDispatch} enters the auxiliary structured/C-prime root
 * of the Task C-prime entry plan once per such invocation and the semantic
 * root composes the returned C-prime continuation (PLAT014).
 *
 * <p>Each operation owns its implementation. Prepared-call protocol state
 * lives with its domain owners ({@link ProtosPreparedCalls}, the structured
 * prepared-call families, {@link ProtosSendPreparation}, {@link
 * ProtosClosureCallPreparation}, {@link ProtosCapturedLexicalAccess} and
 * {@link ProtosFrameLocalBindings}). The operations used only by auxiliary
 * roots are declared last.
 */
@GenerateBytecode(
        languageClass = ProtosLanguage.class,
        enableYield = true,
        enableTagInstrumentation = true,
        enableRootTagging = false,
        enableRootBodyTagging = false,
        enableMaterializedLocalAccesses = true,
        enableTailCallHandlers = true,
        enableUncachedInterpreter = true,
        boxingEliminationTypes = {int.class, long.class, double.class, boolean.class},
        tagTreeNodeLibrary = ProtosBytecodeTagTreeNodeExports.class)
@OperationProxy(value = ProtosCanonicalBooleanOneNode.class, name = "TryCompactCanonicalBooleanOne")
@OperationProxy(value = ProtosNumericAddNode.class, name = "TryPrimitiveNumericAdd")
@OperationProxy(value = ProtosNumericSubtractNode.class, name = "TryPrimitiveNumericSubtract")
@OperationProxy(value = ProtosNumericMultiplyNode.class, name = "TryPrimitiveNumericMultiply")
@OperationProxy(value = ProtosNumericDivideNode.class, name = "TryPrimitiveNumericDivide")
@OperationProxy(value = ProtosNumericLessNode.class, name = "TryPrimitiveNumericLess")
@OperationProxy(value = ProtosNumericLessEqualNode.class, name = "TryPrimitiveNumericLessEqual")
@OperationProxy(value = ProtosNumericGreaterNode.class, name = "TryPrimitiveNumericGreater")
@OperationProxy(value = ProtosNumericGreaterEqualNode.class, name = "TryPrimitiveNumericGreaterEqual")
abstract class ProtosBytecodeRootNode extends RootNode implements BytecodeRootNode {
    protected ProtosBytecodeRootNode(
            ProtosLanguage language,
            FrameDescriptor frameDescriptor) {
        super(language, frameDescriptor);
    }

    /*
     * I111-B / PLAT065-A: whether this physical root is a genuine guest
     * semantic activation (module, top-level or Closure) that carries its
     * explicit RootTag. False by default, so every auxiliary structured,
     * Task or I/O C-prime root of this single generated interpreter is
     * non-semantic without any global registry. Set once by
     * CanonicalToBytecodeLowerer#emitRootBody after endRoot(); a reparse
     * replays that lowering and re-establishes the same value.
     */
    @CompilationFinal private boolean semanticActivationRoot;

    final void markSemanticActivationRoot() {
        this.semanticActivationRoot = true;
    }

    final boolean isSemanticActivationRoot() {
        return semanticActivationRoot;
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
     * PERF025 frame-materialization slice: the frame-binding range and layout
     * of a Closure root lowered without InstallFrameLexicalAuthority, or null.
     * Read only by tooling scope projection, which receives the live frame.
     */
    private LocalRangeAccessor frameNativeBindingLocals;
    private ProtosFrameLexicalLayout frameNativeBindingLayout;

    /*
     * TEST009-V: the exact root span, recorded by the lowerer only while the diagnostic stable
     * root identity is enabled, so a compiled root has a cross-run name without materializing
     * (reparsing) its lazy source information. Never read by guest execution.
     */
    private ProtosDiagnosticRootIdentity.Span diagnosticRootSpan;

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
    @CompilationFinal private boolean guestExceptionCrossed;

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
    private boolean provablyNonSuspendingBody;

    final void recordProvablyNonSuspendingBody(boolean proven) {
        this.provablyNonSuspendingBody = proven;
    }

    final boolean provablyNonSuspendingBody() {
        return provablyNonSuspendingBody;
    }

    /*
     * I101-B: a NORMAL scalar-local root returns its private primitive Long
     * carrier without bytecode materialization. Only the caller completion
     * of this root may project that carrier into the observable guest domain.
     * It remains a private carrier for explicitly accepting fused call sites.
     */
    @CompilationFinal private boolean directScalarLocalCarrierBody;

    final void recordDirectScalarLocalCarrierBody(boolean proven) {
        directScalarLocalCarrierBody = proven;
    }

    final boolean directScalarLocalCarrierBody() {
        return directScalarLocalCarrierBody;
    }

    /*
     * I112 / PLAT062-T: the persistent frame lexical authority of a root that
     * EnterRoot installs at entry instead of an InstallFrameLexicalAuthority
     * body operation, or null. Recorded by the lowerer once per parse, before
     * the root can execute; the same range and layout as its frame-native
     * creation and read operations.
     */
    /*
     * I112: whether EpilogForReturn applies the MaterializeReturnCarrier
     * projection, exactly at the roots whose terminal value the lowerer used
     * to wrap in that operation. Every other root (an I101-B scalar-lane
     * body, a builder-level root) returns its value unchanged and leaves the
     * projection to its caller. Recorded once per parse.
     */
    @CompilationFinal private boolean epilogProjectsReturnCarrier;

    final void recordEpilogProjectsReturnCarrier(boolean projects) {
        this.epilogProjectsReturnCarrier = projects;
    }

    final boolean epilogProjectsReturnCarrier() {
        return epilogProjectsReturnCarrier;
    }

    private LocalRangeAccessor prologAuthorityLocals;
    private ProtosFrameLexicalLayout prologAuthorityLayout;

    final void recordPrologFrameLexicalAuthority(
            LocalRangeAccessor frameBackedLocals,
            ProtosFrameLexicalLayout frameBackedLayout) {
        this.prologAuthorityLocals = frameBackedLocals;
        this.prologAuthorityLayout = frameBackedLayout;
    }

    final LocalRangeAccessor prologAuthorityLocals() {
        return prologAuthorityLocals;
    }

    final ProtosFrameLexicalLayout prologAuthorityLayout() {
        return prologAuthorityLayout;
    }

    final void recordFrameNativeBindings(
            LocalRangeAccessor frameBackedLocals,
            ProtosFrameLexicalLayout frameBackedLayout) {
        this.frameNativeBindingLocals = frameBackedLocals;
        this.frameNativeBindingLayout = frameBackedLayout;
    }

    /**
     * Installs, for tooling scope projection, the materialized-frame authority
     * of this root over {@code frame} on {@code activation}, when this root was
     * lowered without a persistent frame authority. {@code frame} must be the
     * live frame of an executing activation of this root, and {@code location}
     * a location of this root; only the frame's materialized form is retained.
     */
    final void installFrameNativeAuthorityForTooling(
            ProtosActivation activation,
            BytecodeLocation location,
            com.oracle.truffle.api.frame.Frame frame) {
        if (frameNativeBindingLocals == null) {
            return;
        }
        ProtosFrameLocalBindings.installFrameLexicalAuthorityOnTransition(
                frameNativeBindingLocals,
                frameNativeBindingLayout,
                activation,
                location,
                frame.materialize());
    }

    @Override
    public Object interceptControlFlowException(
            ControlFlowException transfer,
            VirtualFrame frame,
            BytecodeNode bytecodeNode,
            int bytecodeIndex)
            throws Throwable {
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
     * I112 / PLAT062-T root prologue. It never creates a rich activation (the
     * activation stays lazy, {@link CurrentActivation}) and leaves every
     * existing entry contract unchanged; a root registered by the lowerer
     * additionally receives its persistent frame lexical authority here.
     * I111-B: an auxiliary structured/C-prime root never records that
     * authority, so the prologue does nothing for it.
     */
    @Prolog
    public static final class EnterRoot {
        @Specialization
        public static void perform(
                @Bind ProtosBytecodeRootNode root,
                @Bind BytecodeNode bytecodeNode,
                @Bind("$bytecodeIndex") int bytecodeIndex,
                @Bind VirtualFrame frame) {
            LocalRangeAccessor locals = root.prologAuthorityLocals();
            if (locals != null) {
                /*
                 * I112: exactly the entry installation of
                 * InstallFrameLexicalAuthority, before the first body
                 * operation, over this root's own frame, location and the
                 * layout its frame-native operations use. Those operations
                 * then recognize it as the installed authority.
                 */
                InstallFrameLexicalAuthority.perform(
                        locals,
                        root.prologAuthorityLayout(),
                        ProtosFrameArguments.activation(frame),
                        bytecodeNode,
                        firstBodyBytecodeIndex(bytecodeNode, bytecodeIndex),
                        frame);
            }
        }

        /*
         * The authority's location must lie inside the root's local scope,
         * which starts after the prologue: the first body instruction, where
         * InstallFrameLexicalAuthority itself used to execute.
         */
        @TruffleBoundary
        private static int firstBodyBytecodeIndex(BytecodeNode bytecodeNode, int prologIndex) {
            return bytecodeNode.getInstruction(prologIndex).getNextBytecodeIndex();
        }
    }

    /**
     * I112 / PLAT062-T return epilogue: for a root recorded by the lowerer as
     * projecting (exactly where it used to emit that operation), the implicit
     * final projection of {@link MaterializeReturnCarrier}: a primitive
     * numeric carrier reaches only a caller that accepts it. Every other value,
     * including a pending continuation or terminal transport, and every value
     * of a non-projecting root, is returned unchanged. It never finishes a
     * suspended invocation. I111-B: an auxiliary structured/C-prime root is
     * never projecting, so it returns its values unchanged as before.
     */
    @EpilogReturn
    public static final class EpilogForReturn {
        @Specialization
        public static Object perform(
                Object value,
                @Bind ProtosBytecodeRootNode root,
                @Bind VirtualFrame frame) {
            if (!root.epilogProjectsReturnCarrier()) {
                return value;
            }
            return MaterializeReturnCarrier.perform(value, frame);
        }
    }

    @Operation
    public static final class CurrentActivation {
        /*
         * PERF038-C: the already-published activation and the first
         * materialization are separate specializations, so a site that has
         * only ever seen one state compiles no path for the other. The guard
         * is re-evaluated on every execution; both specializations return
         * exactly what ProtosFrameArguments.activation returns.
         */
        @Specialization(guards = "hasPublishedActivation(frame)")
        public static ProtosActivation published(@Bind VirtualFrame frame) {
            return ProtosFrameArguments.publishedActivation(frame.getArguments());
        }

        @Specialization(guards = "!hasPublishedActivation(frame)")
        public static ProtosActivation materialize(@Bind VirtualFrame frame) {
            return ProtosFrameArguments.activation(frame);
        }

        @NonIdempotent
        static boolean hasPublishedActivation(VirtualFrame frame) {
            return ProtosFrameArguments.hasPublishedActivation(frame.getArguments());
        }
    }

    /**
     * PERF038-D root-level caller operand of a send preparation (see {@code
     * CanonicalToBytecodeLowerer#emitSendCallerOperand}): the published
     * activation, or, while the root is still in compact source-call form,
     * its frame-argument array itself. It never materializes; {@link
     * PrepareSendArguments} resolves it through {@link
     * PrepareSendArguments#exactCaller} wherever the exact activation is
     * needed, and through its full-header caller slot ({@link
     * ProtosFrameArguments#unmaterializedFullHeaderCaller}) where a
     * provenance-equivalent caller suffices.
     */
    @Operation
    public static final class CurrentCallerReference {
        @Specialization(guards = "hasPublishedActivation(frame)")
        public static Object published(@Bind VirtualFrame frame) {
            return ProtosFrameArguments.publishedActivation(frame.getArguments());
        }

        @Specialization(guards = "!hasPublishedActivation(frame)")
        public static Object compact(@Bind VirtualFrame frame) {
            return frame.getArguments();
        }

        @NonIdempotent
        static boolean hasPublishedActivation(VirtualFrame frame) {
            return ProtosFrameArguments.hasPublishedActivation(frame.getArguments());
        }
    }

    // ---- Closure parameter binding -------------------------------------------------

    @Operation
    public static final class HasClosureArgument {
        @Specialization
        public static boolean perform(ProtosActivation activation, int positionalIndex) {
            List<?> arguments =
                    ProtosFrameLocalBindings.closureArguments(activation);
            return arguments.size() > positionalIndex;
        }
    }

    @Operation
    public static final class LoadClosureArgument {
        @Specialization
        public static Object perform(ProtosActivation activation, int positionalIndex) {
            List<?> arguments =
                    ProtosFrameLocalBindings.closureArguments(activation);
            // D203: an unsupplied parameter without a default binds canonical null.
            if (positionalIndex >= arguments.size()) {
                return ProtosNullValue.INSTANCE;
            }
            return arguments.get(positionalIndex);
        }
    }

    /*
     * PERF025-H1 root-level parameter operations. The lowerer emits these
     * instead of the activation-operand forms whenever the current activation
     * is the root's own (never inside an inline Object body or inline callback
     * region). While the frame is still in compact source-call form, the
     * supplied positional values are read directly from the frame arguments.
     * D203: a position the call did not supply reads canonical null on both
     * paths; no Closure-binding operation signals an argument-count Error.
     *
     * PERF038-C: each operation's compact path and its activation path are
     * separate specializations (the PERF038-B BindClosureFrameParameter
     * shape). A site that has only ever run in compact frames compiles the
     * frame-argument read alone; the materialization and the activation
     * implementation are reached only through the respecializing fallback.
     * The guards are re-evaluated on every execution and are exact
     * complements of the former in-line conditions.
     */

    @Operation
    public static final class HasFrameClosureArgument {
        @Specialization(guards = "isCompact(frame)")
        public static boolean compact(int positionalIndex, @Bind VirtualFrame frame) {
            /* PERF038-F: the supplied-count comparison without the layout branch. */
            return ProtosFrameArguments.compactHasSuppliedArgument(
                    frame.getArguments(), positionalIndex);
        }

        @Specialization(guards = "!isCompact(frame)")
        public static boolean materialized(int positionalIndex, @Bind VirtualFrame frame) {
            return HasClosureArgument.perform(
                    ProtosFrameArguments.activation(frame.getArguments()), positionalIndex);
        }

        @NonIdempotent
        static boolean isCompact(VirtualFrame frame) {
            return ProtosFrameArguments.isUnmaterializedCompactCall(frame.getArguments());
        }
    }

    @Operation
    public static final class LoadFrameClosureArgument {
        @Specialization(guards = "isCompact(frame)")
        public static Object compact(int positionalIndex, @Bind VirtualFrame frame) {
            return ProtosFrameArguments.compactSuppliedArgumentOrNull(
                    frame.getArguments(), positionalIndex);
        }

        @Specialization(guards = "!isCompact(frame)")
        public static Object materialized(int positionalIndex, @Bind VirtualFrame frame) {
            return LoadClosureArgument.perform(
                    ProtosFrameArguments.activation(frame.getArguments()), positionalIndex);
        }

        @NonIdempotent
        static boolean isCompact(VirtualFrame frame) {
            return ProtosFrameArguments.isUnmaterializedCompactCall(frame.getArguments());
        }
    }

    @Operation
    public static final class BindClosureParameter {
        @Specialization
        public static void perform(ProtosActivation activation, String name, Object value) {
            ProtosFrameLocalBindings.createClosureParameterSlot(
                    activation,
                    name,
                    value);
        }
    }

    @Operation
    public static final class BindClosureRest {
        @Specialization
        public static void perform(
                ProtosActivation activation,
                String name,
                int positionalParametersBeforeRest) {
            ProtosFrameLocalBindings.createClosureParameterSlot(
                    activation,
                    name,
                    ProtosFrameLocalBindings.closureRestArray(activation, positionalParametersBeforeRest));
        }
    }

    /**
     * PERF025-H1: binds a statically proven parameter of a root that installs
     * its persistent frame authority, at the ordinal the lowerer took from
     * {@code frameBackedLayout} instead of re-resolving {@code name}, which
     * only the unchanged named fallback uses.
     *
     * <p>PERF030-I: {@code ordinal} is a constant operand because the
     * frame-local accesses of {@link
     * ProtosFrameLexicalBindingAuthority#createFrameBackedBindingAt} require a
     * partial-evaluation constant index.
     */
    @Operation
    @ConstantOperand(
            type = ProtosFrameLexicalLayout.class,
            name = "frameBackedLayout")
    @ConstantOperand(type = int.class, name = "ordinal")
    public static final class BindClosureIndexedParameter {
        @Specialization
        public static void perform(
                ProtosFrameLexicalLayout frameBackedLayout,
                int ordinal,
                ProtosActivation activation,
                String name,
                Object value) {
            ProtosFrameLocalBindings.bindIndexedClosureParameter(
                    frameBackedLayout, activation, ordinal, name, value);
        }
    }

    /** PERF025-H1 rest counterpart of {@link BindClosureIndexedParameter}. */
    @Operation
    @ConstantOperand(
            type = ProtosFrameLexicalLayout.class,
            name = "frameBackedLayout")
    @ConstantOperand(type = int.class, name = "ordinal")
    public static final class BindClosureIndexedRest {
        @Specialization
        public static void perform(
                ProtosFrameLexicalLayout frameBackedLayout,
                int ordinal,
                ProtosActivation activation,
                String name,
                int positionalParametersBeforeRest) {
            ProtosBytecodeRootNode.BindClosureIndexedRest.bindIndexedClosureRest(
                    frameBackedLayout, activation, ordinal, name, positionalParametersBeforeRest);
        }


        /** PERF025-H1 rest counterpart of {@link #bindIndexedClosureParameter}. */
        static void bindIndexedClosureRest(
                ProtosFrameLexicalLayout frameBackedLayout,
                ProtosActivation activation,
                int ordinal,
                String name,
                int positionalParametersBeforeRest) {
            ProtosFrameLocalBindings.bindIndexedClosureParameter(
                    frameBackedLayout,
                    activation,
                    ordinal,
                    name,
                    ProtosFrameLocalBindings.closureRestArray(activation, positionalParametersBeforeRest));
        }
}

    // ---- Lexical lookup and frame-backed bindings ----------------------------------

    @Operation
    public static final class Lookup {
        @Specialization
        public static Object perform(ProtosActivation activation, String name) {
            return ProtosLexicalFallback.readByName(activation, name)
                    .orElseThrow(
                            () ->
                                    new ProtosSignalException(
                                            ProtosCoreErrors.newUnqualifiedLookupError(
                                                    activation)));
        }
    }

    @Operation
    @ConstantOperand(
            type = LocalRangeAccessor.class,
            name = "frameBackedLocals")
    @ConstantOperand(
            type = ProtosFrameLexicalLayout.class,
            name = "frameBackedLayout")
    public static final class InstallFrameLexicalAuthority {
        @Specialization
        public static void perform(
                LocalRangeAccessor frameBackedLocals,
                ProtosFrameLexicalLayout frameBackedLayout,
                ProtosActivation activation,
                @Bind BytecodeNode bytecodeNode,
                @Bind("$bytecodeIndex") int bytecodeIndex,
                @Bind VirtualFrame frame) {
            MaterializedFrame materializedFrame = frame.materialize();
            activation.installFrameLexicalBindingAuthorityForRuntime(
                    new ProtosFrameLexicalBindingAuthority(
                            frameBackedLayout,
                            frameBackedLocals,
                            bytecodeNode.getBytecodeLocation(bytecodeIndex),
                            materializedFrame));
        }
    }


    /**
     * I104-B: establish one required parameter per Bytecode DSL
     * instruction. Each local ordinal is an exact constant operand.
     */
    @Operation
    @ConstantOperand(type = LocalRangeAccessor.class, name = "frameBackedLocals")
    @ConstantOperand(type = ProtosFrameLexicalLayout.class, name = "frameBackedLayout")
    @ConstantOperand(type = int.class, name = "ordinal")
    public static final class BindRequiredFrameParameter {
        @Specialization(
                guards = "isCompactUnbound(frameBackedLocals, ordinal, bytecodeNode, frame)")
        public static void compact(
                LocalRangeAccessor frameBackedLocals,
                ProtosFrameLexicalLayout frameBackedLayout,
                int ordinal,
                String name,
                @Bind BytecodeNode bytecodeNode,
                @Bind VirtualFrame frame) {
            Object value =
                    ProtosFrameArguments.compactSuppliedArgumentOrNull(
                            frame.getArguments(), ordinal);
            frameBackedLocals.setObject(bytecodeNode, frame, ordinal, value);
        }

        @Specialization(
                guards = "!isCompactUnbound(frameBackedLocals, ordinal, bytecodeNode, frame)")
        public static void authoritative(
                LocalRangeAccessor frameBackedLocals,
                ProtosFrameLexicalLayout frameBackedLayout,
                int ordinal,
                String name,
                @Bind BytecodeNode bytecodeNode,
                @Bind("$bytecodeIndex") int bytecodeIndex,
                @Bind VirtualFrame frame) {
            slowBind(
                    frameBackedLocals, frameBackedLayout, ordinal, name,
                    bytecodeNode, bytecodeIndex, frame.materialize());
        }

        @NonIdempotent
        static boolean isCompactUnbound(
                LocalRangeAccessor frameBackedLocals,
                int ordinal,
                BytecodeNode bytecodeNode,
                VirtualFrame frame) {
            return ProtosFrameArguments.isUnmaterializedCompactCall(frame.getArguments())
                    && frameBackedLocals.isCleared(
                            bytecodeNode, frame, ordinal);
        }

        /**
         * Retain the established rich-activation and duplicate-binding
         * semantics.
         */
        @TruffleBoundary
        private static void slowBind(
                LocalRangeAccessor frameBackedLocals,
                ProtosFrameLexicalLayout frameBackedLayout,
                int ordinal,
                String name,
                BytecodeNode bytecodeNode,
                int bytecodeIndex,
                MaterializedFrame frame) {
            ProtosActivation activation =
                    ProtosFrameArguments.activation(frame.getArguments());
            Object value =
                    LoadClosureArgument.perform(
                            activation, ordinal);
            BindClosureFrameParameter.perform(
                    frameBackedLocals,
                    frameBackedLayout,
                    ordinal,
                    activation,
                    name,
                    value,
                    bytecodeNode,
                    bytecodeIndex,
                    frame);
        }
    }

    @Operation
    @ConstantOperand(
            type = LocalRangeAccessor.class,
            name = "frameBackedLocals")
    @ConstantOperand(
            type = ProtosFrameLexicalLayout.class,
            name = "frameBackedLayout")
    @ConstantOperand(type = int.class, name = "ordinal")
    public static final class BindClosureFrameParameter {
        /**
         * PERF025-H1: a frame still in compact source-call form has no
         * materialized activation, hence an unobserved execution context
         * without an authority; the frame local is then the binding's only
         * store, exactly as {@code createCurrentFrameBinding} treats an
         * unobserved context. Any other state, including a duplicate
         * creation's Error, takes the unchanged activation path.
         *
         * <p>PERF029: {@code ordinal} is a constant operand, not a stack
         * operand, because every {@link LocalRangeAccessor} local operation
         * requires its index to be a partial-evaluation constant.
         *
         * <p>PERF038-B: the compact direct store and the authoritative
         * activation path are separate specializations. A site that has only
         * ever bound into compact unobserved frames compiles the guard and
         * the slot store alone; the activation, lexical-authority and
         * duplicate-creation machinery is reached only through the
         * respecializing fallback, and even when that fallback is active it
         * stays behind a boundary (the PERF034-B {@code CreateCurrentFrameLocal}
         * shape). The guard is re-evaluated on every execution, so a frame
         * materialized or an ordinal already established since the last
         * execution always takes the fallback.
         */
        @Specialization(guards = "isCompactUnbound(frameBackedLocals, ordinal, bytecodeNode, frame)")
        public static void compact(
                LocalRangeAccessor frameBackedLocals,
                ProtosFrameLexicalLayout frameBackedLayout,
                int ordinal,
                String name,
                Object value,
                @Bind BytecodeNode bytecodeNode,
                @Bind VirtualFrame frame) {
            frameBackedLocals.setObject(bytecodeNode, frame, ordinal, value);
        }

        @Specialization(guards = "!isCompactUnbound(frameBackedLocals, ordinal, bytecodeNode, frame)")
        public static void materialized(
                LocalRangeAccessor frameBackedLocals,
                ProtosFrameLexicalLayout frameBackedLayout,
                int ordinal,
                String name,
                Object value,
                @Bind BytecodeNode bytecodeNode,
                @Bind("$bytecodeIndex") int bytecodeIndex,
                @Bind VirtualFrame frame) {
            slowBind(
                    frameBackedLocals, frameBackedLayout, ordinal,
                    frame.getArguments(), name, value, bytecodeNode, bytecodeIndex,
                    frame.materialize());
        }

        @NonIdempotent
        static boolean isCompactUnbound(
                LocalRangeAccessor frameBackedLocals,
                int ordinal,
                BytecodeNode bytecodeNode,
                VirtualFrame frame) {
            return ProtosFrameArguments.isUnmaterializedCompactCall(frame.getArguments())
                    && frameBackedLocals.isCleared(bytecodeNode, frame, ordinal);
        }

        /**
         * The unchanged authoritative binding, including duplicate-creation
         * Errors and activation materialization. The frame is materialized
         * before entering the boundary so no VirtualFrame escapes.
         */
        @TruffleBoundary
        private static void slowBind(
                LocalRangeAccessor frameBackedLocals,
                ProtosFrameLexicalLayout frameBackedLayout,
                int ordinal,
                Object[] arguments,
                String name,
                Object value,
                BytecodeNode bytecodeNode,
                int bytecodeIndex,
                MaterializedFrame frame) {
            BindClosureFrameParameter.perform(
                    frameBackedLocals, frameBackedLayout, ordinal,
                    ProtosFrameArguments.activation(arguments), name, value,
                    bytecodeNode, bytecodeIndex, frame);
        }

        public static void perform(
                LocalRangeAccessor frameBackedLocals,
                ProtosFrameLexicalLayout frameBackedLayout,
                int ordinal,
                ProtosActivation activation,
                String name,
                Object value,
                BytecodeNode bytecodeNode,
                int bytecodeIndex,
                VirtualFrame frame) {
            try {
                ProtosFrameLocalBindings.createCurrentFrameBinding(
                        frameBackedLocals, frameBackedLayout, activation,
                        ordinal, name, value, bytecodeNode, bytecodeIndex, frame);
            } catch (IllegalStateException invalidCreation) {
                throw new ProtosSignalException(
                        ProtosCoreErrors.newError(activation));
            }
        }
}

    /**
     * I104-A: fused unary NORMAL entry. The parameter's identity, layout,
     * ordinal and spelling are compilation constants. Only its supplied
     * value is dynamic.
     *
     * The compact specialization combines the former frame argument load
     * and frame-local binding into one Bytecode DSL operation; an unsupplied
     * argument binds canonical null (D203). Published or otherwise
     * non-compact activations retain the original authoritative operations,
     * including duplicate-binding Errors.
     */
    @Operation
    @ConstantOperand(
            type = LocalRangeAccessor.class,
            name = "frameBackedLocals")
    @ConstantOperand(
            type = ProtosFrameLexicalLayout.class,
            name = "frameBackedLayout")
    @ConstantOperand(type = int.class, name = "ordinal")
    @ConstantOperand(type = String.class, name = "name")
    public static final class BindUnaryFrameParameter {
        @Specialization(
                guards = "isCompactUnbound(frameBackedLocals, ordinal, bytecodeNode, frame)")
        public static void compact(
                LocalRangeAccessor frameBackedLocals,
                ProtosFrameLexicalLayout frameBackedLayout,
                int ordinal,
                String name,
                @Bind BytecodeNode bytecodeNode,
                @Bind VirtualFrame frame) {
            Object value = ProtosFrameArguments.compactSuppliedArgumentOrNull(
                    frame.getArguments(), 0);
            frameBackedLocals.setObject(
                    bytecodeNode, frame, ordinal, value);
        }

        @Specialization(
                guards = "!isCompactUnbound(frameBackedLocals, ordinal, bytecodeNode, frame)")
        public static void authoritative(
                LocalRangeAccessor frameBackedLocals,
                ProtosFrameLexicalLayout frameBackedLayout,
                int ordinal,
                String name,
                @Bind BytecodeNode bytecodeNode,
                @Bind("$bytecodeIndex") int bytecodeIndex,
                @Bind VirtualFrame frame) {
            bindAuthoritatively(
                    frameBackedLocals,
                    frameBackedLayout,
                    ordinal,
                    name,
                    frame.getArguments(),
                    bytecodeNode,
                    bytecodeIndex,
                    frame.materialize());
        }

        @NonIdempotent
        static boolean isCompactUnbound(
                LocalRangeAccessor frameBackedLocals,
                int ordinal,
                BytecodeNode bytecodeNode,
                VirtualFrame frame) {
            return ProtosFrameArguments.isUnmaterializedCompactCall(frame.getArguments())
                    && frameBackedLocals.isCleared(
                            bytecodeNode, frame, ordinal);
        }

        @TruffleBoundary
        private static void bindAuthoritatively(
                LocalRangeAccessor frameBackedLocals,
                ProtosFrameLexicalLayout frameBackedLayout,
                int ordinal,
                String name,
                Object[] arguments,
                BytecodeNode bytecodeNode,
                int bytecodeIndex,
                MaterializedFrame frame) {
            ProtosActivation activation =
                    ProtosFrameArguments.activation(arguments);
            Object value =
                    LoadClosureArgument.perform(
                            activation, 0);
            BindClosureFrameParameter.perform(
                    frameBackedLocals,
                    frameBackedLayout,
                    ordinal,
                    activation,
                    name,
                    value,
                    bytecodeNode,
                    bytecodeIndex,
                    frame);
        }
    }

    /*
     * PERF025 lazy inline callback activation. The B-prime inline-callback
     * counterparts of the root-level frame-native operations: frame argument
     * zero belongs to the enclosing physical root, so each takes the
     * callback invocation's PreparedInlineLiteralCall carrier plus the
     * callback's block-local range and layout. The callback activation is
     * materialized only when an operation really needs it, and only through
     * MaterializeInlineCallbackActivation's durable transfer
     * (ProtosInlineCallbackFrameBindings).
     */

    @Operation
    @ConstantOperand(
            type = LocalRangeAccessor.class,
            name = "frameBackedLocals")
    @ConstantOperand(
            type = ProtosFrameLexicalLayout.class,
            name = "frameBackedLayout")
    public static final class MaterializeInlineCallbackActivation {
        @Specialization
        public static ProtosActivation perform(
                LocalRangeAccessor frameBackedLocals,
                ProtosFrameLexicalLayout frameBackedLayout,
                PreparedInlineLiteralCall child,
                @Bind BytecodeNode bytecodeNode,
                @Bind VirtualFrame frame) {
            return ProtosInlineCallbackFrameBindings.durableActivation(
                    child, frameBackedLocals, frameBackedLayout, bytecodeNode, frame);
        }
    }

    @Operation
    @ConstantOperand(
            type = LocalRangeAccessor.class,
            name = "frameBackedLocals")
    @ConstantOperand(
            type = ProtosFrameLexicalLayout.class,
            name = "frameBackedLayout")
    public static final class LoadInlineClosureArgument {
        @Specialization
        public static Object perform(
                LocalRangeAccessor frameBackedLocals,
                ProtosFrameLexicalLayout frameBackedLayout,
                PreparedInlineLiteralCall child,
                int positionalIndex,
                @Bind BytecodeNode bytecodeNode,
                @Bind VirtualFrame frame) {
            return ProtosInlineCallbackFrameBindings.loadArgument(
                    child, frameBackedLocals, frameBackedLayout,
                    positionalIndex, bytecodeNode, frame);
        }
    }

    @Operation
    @ConstantOperand(
            type = LocalRangeAccessor.class,
            name = "frameBackedLocals")
    @ConstantOperand(
            type = ProtosFrameLexicalLayout.class,
            name = "frameBackedLayout")
    @ConstantOperand(type = int.class, name = "ordinal")
    public static final class BindInlineClosureFrameParameter {
        @Specialization
        public static void perform(
                LocalRangeAccessor frameBackedLocals,
                ProtosFrameLexicalLayout frameBackedLayout,
                int ordinal,
                PreparedInlineLiteralCall child,
                String name,
                Object value,
                @Bind BytecodeNode bytecodeNode,
                @Bind VirtualFrame frame) {
            ProtosInlineCallbackFrameBindings.create(
                    child,
                    frameBackedLocals,
                    frameBackedLayout,
                    ordinal,
                    name,
                    value,
                    bytecodeNode,
                    frame);
        }
    }

    @Operation
    @ConstantOperand(
            type = LocalRangeAccessor.class,
            name = "frameBackedLocals")
    @ConstantOperand(
            type = ProtosFrameLexicalLayout.class,
            name = "frameBackedLayout")
    @ConstantOperand(type = int.class, name = "ordinal")
    public static final class BindClosureFrameRest {
        @Specialization
        public static void perform(
                LocalRangeAccessor frameBackedLocals,
                ProtosFrameLexicalLayout frameBackedLayout,
                int ordinal,
                ProtosActivation activation,
                String name,
                int positionalParametersBeforeRest,
                @Bind BytecodeNode bytecodeNode,
                @Bind("$bytecodeIndex") int bytecodeIndex,
                @Bind VirtualFrame frame) {
            Object rest = ProtosFrameLocalBindings.closureRestArray(activation, positionalParametersBeforeRest);
            try {
                ProtosFrameLocalBindings.createCurrentFrameBinding(
                        frameBackedLocals, frameBackedLayout, activation,
                        ordinal, name, rest, bytecodeNode, bytecodeIndex, frame);
            } catch (IllegalStateException invalidCreation) {
                throw new ProtosSignalException(
                        ProtosCoreErrors.newError(activation));
            }
        }
    }

    /**
     * PERF034-C: a bytecode-local scalar lane is valid only while the
     * current source-call activation remains compact and unobserved.
     * A materialized activation continues through the authoritative
     * frame-local creation/read operations.
     */
    @Operation
    public static final class IsCompactLocalFrame {
        @Specialization
        public static boolean perform(@Bind VirtualFrame frame) {
            return ProtosFrameArguments.isUnmaterializedCompactScalarLocalCall(
                    frame.getArguments());
        }
    }

    @Operation
    @ConstantOperand(
            type = LocalRangeAccessor.class,
            name = "frameBackedLocals")
    @ConstantOperand(
            type = ProtosFrameLexicalLayout.class,
            name = "frameBackedLayout")
    @ConstantOperand(type = int.class, name = "ordinal")
    @ConstantOperand(type = String.class, name = "name")
    public static final class CreateCurrentFrameLocal {
        /**
         * PERF025 compact callee execution: the body-level counterpart of
         * {@link BindClosureFrameParameter}. While the frame is still in
         * compact source-call form the context is unobserved and has no
         * authority, so an ABSENT local is established directly in the frame;
         * a PRESENT one (duplicate creation) or any other state takes the
         * unchanged activation path, including its exact creation Error.
         *
         * <p>PERF030: {@code ordinal} is a constant operand for the same
         * reason as in {@link BindClosureFrameParameter}.
         */
        @Specialization
        public static Object perform(
                LocalRangeAccessor frameBackedLocals,
                ProtosFrameLexicalLayout frameBackedLayout,
                int ordinal,
                String name,
                Object value,
                @Bind BytecodeNode bytecodeNode,
                @Bind("$bytecodeIndex") int bytecodeIndex,
                @Bind VirtualFrame frame) {
            Object[] arguments = frame.getArguments();
            if (ProtosFrameArguments.isUnmaterializedCompactCall(arguments)
                    && frameBackedLocals.isCleared(bytecodeNode, frame, ordinal)) {
                frameBackedLocals.setObject(bytecodeNode, frame, ordinal, value);
                return value;
            }
            return slowCreate(
                    frameBackedLocals, frameBackedLayout, ordinal,
                    arguments, name, value, bytecodeNode, bytecodeIndex,
                    frame.materialize());
        }

        /**
         * PERF034-B: keep rich-activation transitions and duplicate-creation
         * errors outside the compiled ordinary compact-local path.
         *
         * The frame is materialized before entering the boundary. This
         * preserves the existing authoritative creation implementation
         * without allowing a VirtualFrame to escape compilation.
         */
        @TruffleBoundary
        private static Object slowCreate(
                LocalRangeAccessor frameBackedLocals,
                ProtosFrameLexicalLayout frameBackedLayout,
                int ordinal,
                Object[] arguments,
                String name,
                Object value,
                BytecodeNode bytecodeNode,
                int bytecodeIndex,
                MaterializedFrame frame) {
            return CreateCurrentFrameLocal.performForActivation(
                    frameBackedLocals, frameBackedLayout, ordinal,
                    ProtosFrameArguments.activation(arguments), name, value,
                    bytecodeNode, bytecodeIndex, frame);
        }

        public static Object performForActivation(
                LocalRangeAccessor frameBackedLocals,
                ProtosFrameLexicalLayout frameBackedLayout,
                int ordinal,
                ProtosActivation activation,
                String name,
                Object value,
                BytecodeNode bytecodeNode,
                int bytecodeIndex,
                VirtualFrame frame) {
            try {
                ProtosFrameLocalBindings.createCurrentFrameBinding(
                        frameBackedLocals, frameBackedLayout, activation,
                        ordinal, name, value, bytecodeNode, bytecodeIndex, frame);
            } catch (IllegalStateException invalidMutation) {
                throw new ProtosSignalException(
                        ProtosCoreErrors.newError(activation));
            }
            return value;
        }
}

    @Operation
    @ConstantOperand(
            type = LocalRangeAccessor.class,
            name = "frameBackedLocals")
    @ConstantOperand(
            type = ProtosFrameLexicalLayout.class,
            name = "frameBackedLayout")
    @ConstantOperand(type = int.class, name = "ordinal")
    public static final class CreateInlineCurrentFrameLocal {
        @Specialization
        public static Object perform(
                LocalRangeAccessor frameBackedLocals,
                ProtosFrameLexicalLayout frameBackedLayout,
                int ordinal,
                PreparedInlineLiteralCall child,
                String name,
                Object value,
                @Bind BytecodeNode bytecodeNode,
                @Bind VirtualFrame frame) {
            ProtosInlineCallbackFrameBindings.create(
                    child,
                    frameBackedLocals,
                    frameBackedLayout,
                    ordinal,
                    name,
                    value,
                    bytecodeNode,
                    frame);
            return value;
        }
    }

    /*
     * PERF030-I frame-native multiple creation. The lowerer splits it into one
     * prefix observation, which observes the complete fixed source prefix
     * before any binding is created, followed by one scalar
     * CreateCurrentFrameLocal or CreateInlineCurrentFrameLocal per name, in
     * source order, each owning its ordinal as a constant operand. A failing
     * creation leaves the earlier ones in place (no rollback), exactly as
     * MultipleCreateLocalSlots. No ordinal array is ever indexed at run time.
     */

    /** The observed prefix of a frame-native root multiple creation. */
    @Operation
    public static final class ObserveMultipleCreatePrefix {
        @Specialization
        public static Object[] perform(ProtosActivation activation, int required, Object source) {
            return ProtosFrameLocalBindings.observeMultipleCreatePrefix(activation, required, source)
                    .toArray();
        }
    }

    /**
     * The observed prefix of an inline-callback multiple creation; the
     * callback activation is materialized only for the Error of an invalid or
     * insufficient source.
     */
    @Operation
    @ConstantOperand(
            type = LocalRangeAccessor.class,
            name = "frameBackedLocals")
    @ConstantOperand(
            type = ProtosFrameLexicalLayout.class,
            name = "frameBackedLayout")
    public static final class ObserveInlineMultipleCreatePrefix {
        @Specialization
        public static Object[] perform(
                LocalRangeAccessor frameBackedLocals,
                ProtosFrameLexicalLayout frameBackedLayout,
                PreparedInlineLiteralCall child,
                int required,
                Object source,
                @Bind BytecodeNode bytecodeNode,
                @Bind VirtualFrame frame) {
            return ProtosInlineCallbackFrameBindings.observeMultipleCreatePrefix(
                    child, frameBackedLocals, frameBackedLayout,
                    required, source, bytecodeNode, frame);
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

    @Operation
    @ConstantOperand(type = LocalAccessor.class, name = "accessor")
    @ConstantOperand(type = Assumption.class, name = "presenceContinuity")
    public static final class ReadFrameLocal {
        @Specialization
        public static Object perform(
                LocalAccessor accessor,
                Assumption presenceContinuity,
                ProtosActivation activation,
                String name,
                @Bind BytecodeNode bytecodeNode,
                @Bind VirtualFrame frame) {
            return ProtosNumericValueSupport.guestValue(
                    readRetainingCarrier(
                            accessor, presenceContinuity, activation, name, bytecodeNode, frame));
        }


        /**
         * I091: {@link #perform} without materializing a frame-retained
         * primitive carrier, for a lowered read whose consumer materializes
         * it (or is an operand of the guarded numeric chain).
         */
        static Object readRetainingCarrier(
                LocalAccessor accessor,
                Assumption presenceContinuity,
                ProtosActivation activation,
                String name,
                BytecodeNode bytecodeNode,
                VirtualFrame frame) {
            if (activation.hasGenuineExecutionContextForRuntime()
                    && (presenceContinuity.isValid()
                            || !accessor.isCleared(bytecodeNode, frame))) {
                return accessor.getObject(bytecodeNode, frame);
            }

            /*
             * Once continuity has been invalidated, D179 C0 remains exactly
             * presence-aware: a cleared genuine execution-context local is
             * semantically ABSENT and resumes lexical/receiver fallback.
             * Legacy/internal activations whose current lexical context is an
             * ordinary ProtosObjectValue use the same fallback.
             */
            return ProtosLexicalFallback.readByName(activation, name)
                    .orElseThrow(
                            () ->
                                    new ProtosSignalException(
                                            ProtosCoreErrors.newUnqualifiedLookupError(
                                                    activation)));
        }
}

    /**
     * PERF025-H1 root-level form of {@link ReadFrameLocal}, emitted when the
     * current activation is the root's own. A frame still in compact
     * source-call form denotes a genuine, unobserved execution context, so a
     * PRESENT local is read directly; an ABSENT one (D179 C0) materializes the
     * exact activation and resumes the unchanged fallback.
     */
    @Operation
    @ConstantOperand(type = LocalAccessor.class, name = "accessor")
    @ConstantOperand(type = Assumption.class, name = "presenceContinuity")
    @ConstantOperand(type = String.class, name = "name")
    public static final class ReadRootFrameLocal {
        /*
         * The lowerer emitted this operation only for a current Resolved
         * binding. While the call is still compact its guest Context has
         * never become observable, so no D179 structural removal could have
         * made that established binding ABSENT.
         *
         * PERF038-C: the compact read and the presence-aware fallback are
         * separate specializations, so a site that has only read compact
         * frames compiles no fallback call; the guard is re-evaluated on
         * every execution.
         */
        @Specialization(guards = "isCompact(frame)")
        public static Object compact(
                LocalAccessor accessor,
                Assumption presenceContinuity,
                String name,
                @Bind BytecodeNode bytecodeNode,
                @Bind VirtualFrame frame) {
            /*
             * I091: may yield a frame-retained primitive carrier. The lowerer
             * materializes it for every consumer outside the guarded numeric
             * chain.
             */
            return accessor.getObject(bytecodeNode, frame);
        }

        @Specialization(guards = "!isCompact(frame)")
        public static Object materialized(
                LocalAccessor accessor,
                Assumption presenceContinuity,
                String name,
                @Bind BytecodeNode bytecodeNode,
                @Bind VirtualFrame frame) {
            return slowRead(
                    accessor, presenceContinuity, frame.getArguments(),
                    name, bytecodeNode, frame.materialize());
        }

        @NonIdempotent
        static boolean isCompact(VirtualFrame frame) {
            return ProtosFrameArguments.isUnmaterializedCompactCall(frame.getArguments());
        }

        /**
         * PERF034-B: observed contexts retain the authoritative D179
         * presence-aware fallback, but do not expand that machinery into
         * the compiled unobserved compact-local read.
         */
        @TruffleBoundary
        private static Object slowRead(
                LocalAccessor accessor,
                Assumption presenceContinuity,
                Object[] arguments,
                String name,
                BytecodeNode bytecodeNode,
                MaterializedFrame frame) {
            // I091: the lowered consumer materializes a retained carrier.
            return ReadFrameLocal.readRetainingCarrier(
                    accessor,
                    presenceContinuity,
                    ProtosFrameArguments.activation(arguments),
                    name,
                    bytecodeNode,
                    frame);
        }
    }

    /** Inline-callback form of {@link ReadFrameLocal}. */
    @Operation
    @ConstantOperand(type = LocalAccessor.class, name = "accessor")
    @ConstantOperand(type = Assumption.class, name = "presenceContinuity")
    @ConstantOperand(
            type = LocalRangeAccessor.class,
            name = "frameBackedLocals")
    @ConstantOperand(
            type = ProtosFrameLexicalLayout.class,
            name = "frameBackedLayout")
    public static final class ReadInlineFrameLocal {
        @Specialization
        public static Object perform(
                LocalAccessor accessor,
                Assumption presenceContinuity,
                LocalRangeAccessor frameBackedLocals,
                ProtosFrameLexicalLayout frameBackedLayout,
                PreparedInlineLiteralCall child,
                String name,
                @Bind BytecodeNode bytecodeNode,
                @Bind VirtualFrame frame) {
            return ProtosInlineCallbackFrameBindings.read(
                    child,
                    accessor,
                    presenceContinuity,
                    frameBackedLocals,
                    frameBackedLayout,
                    name,
                    bytecodeNode,
                    frame);
        }
    }

    /*
     * PERF037-B root-level captured reads. The lowerer emits these instead of
     * the activation-operand forms whenever the current activation is the
     * root's own. While the frame is still in compact source-call form the
     * invocation's guest Context has never become observable, so it cannot
     * hold a binding the static analysis did not declare (D179 C0 late
     * creation needs that Context), and the captured lexical chain is the
     * invoked Closure's own: the nearer-scope presence checks and the owner
     * selection then run without materializing the activation. A
     * materialized frame, and every fallback, take the unchanged
     * activation path.
     */

    /*
     * PERF037-C: the name and lexical depth are constant operands, so the
     * nearer-scope walk has a partial-evaluation-constant length. The compact
     * and materialized-activation forms are separate specializations, so a
     * root whose invocations stay compact never compiles the activation path.
     * The compact form retains a CapturedNearerScopeAbsence proof instead of
     * asking each nearer scope for membership; the uncached interpreter uses
     * an unproven one and walks exactly as before.
     */

    @Operation
    @ConstantOperand(type = int.class, name = "frameOrdinal")
    @ConstantOperand(type = String.class, name = "name")
    @ConstantOperand(type = int.class, name = "lexicalDepth")
    public static final class ReadCapturedFrameLocalAtRoot {
        /*
         * PERF038-C: compact and materialized frames are separate
         * specializations (guard re-evaluated on every execution), and the
         * compact miss materializes and looks up behind one boundary instead
         * of two, so a compact-only site compiles no activation path.
         */
        @Specialization(guards = "isCompact(frame)")
        public static Object compact(
                int frameOrdinal,
                String name,
                int lexicalDepth,
                @Bind VirtualFrame frame,
                @Cached(
                                value = "createNearerScopeAbsence(frame, name, lexicalDepth)",
                                uncached = "unprovenNearerScopeAbsence(lexicalDepth)")
                        CapturedNearerScopeAbsence nearerAbsence) {
            Object[] arguments = frame.getArguments();
            if (lexicalDepth > 0) {
                Object value =
                        ProtosCapturedLexicalAccess.readOwnerFrameBindingOrNull(
                                nearerAbsence.ownerOrNull(
                                        ProtosFrameArguments.compactCapturedLexicalEnvironment(
                                                arguments),
                                        name),
                                name,
                                frameOrdinal);
                if (value != null) {
                    return value;
                }
            }
            return lookupCapturedFallbackAtRoot(arguments, name);
        }

        @Specialization(guards = "!isCompact(frame)")
        public static Object materialized(
                int frameOrdinal,
                String name,
                int lexicalDepth,
                @Bind VirtualFrame frame) {
            return ReadCapturedFrameLocal.perform(
                    frameOrdinal,
                    ProtosFrameArguments.activation(frame.getArguments()),
                    name,
                    lexicalDepth);
        }

        @NonIdempotent
        static boolean isCompact(VirtualFrame frame) {
            return ProtosFrameArguments.isUnmaterializedCompactCall(frame.getArguments());
        }

        static CapturedNearerScopeAbsence createNearerScopeAbsence(
                VirtualFrame frame, String name, int lexicalDepth) {
            return CapturedNearerScopeAbsence.create(
                    ProtosFrameArguments.compactCapturedLexicalEnvironment(frame.getArguments()),
                    name,
                    lexicalDepth);
        }

        static CapturedNearerScopeAbsence unprovenNearerScopeAbsence(int lexicalDepth) {
            return CapturedNearerScopeAbsence.unproven(lexicalDepth);
        }
    }

    @Operation
    @ConstantOperand(type = MaterializedLocalAccessor.class)
    @ConstantOperand(type = String.class, name = "name")
    @ConstantOperand(type = int.class, name = "lexicalDepth")
    public static final class SelectCapturedMaterializedOwnerFrameAtRoot {
        /* PERF038-C: compact and materialized frames specialize separately. */
        @Specialization(guards = "isCompact(frame)")
        public static MaterializedFrame compact(
                MaterializedLocalAccessor accessor,
                String name,
                int lexicalDepth,
                @Bind BytecodeNode bytecodeNode,
                @Bind VirtualFrame frame,
                @Cached(
                                value = "createNearerScopeAbsence(frame, name, lexicalDepth)",
                                uncached = "unprovenNearerScopeAbsence(lexicalDepth)")
                        CapturedNearerScopeAbsence nearerAbsence,
                @Cached(
                                value = "createOwnerFrameCache()",
                                uncached = "retiredOwnerFrameCache()")
                        CapturedOwnerFrameCache ownerFrameCache) {
            if (lexicalDepth > 0) {
                return ownerFrameCache.ownerFrameOrNull(
                        accessor,
                        nearerAbsence.ownerOrNull(
                                ProtosFrameArguments.compactCapturedLexicalEnvironment(
                                        frame.getArguments()),
                                name),
                        name,
                        bytecodeNode,
                        nearerAbsence);
            }
            return null;
        }

        @Specialization(guards = "!isCompact(frame)")
        public static MaterializedFrame materialized(
                MaterializedLocalAccessor accessor,
                String name,
                int lexicalDepth,
                @Bind BytecodeNode bytecodeNode,
                @Bind VirtualFrame frame) {
            return SelectCapturedMaterializedOwnerFrame.perform(
                    accessor,
                    ProtosFrameArguments.activation(frame.getArguments()),
                    name,
                    lexicalDepth,
                    bytecodeNode);
        }

        @NonIdempotent
        static boolean isCompact(VirtualFrame frame) {
            return ProtosFrameArguments.isUnmaterializedCompactCall(frame.getArguments());
        }

        static CapturedNearerScopeAbsence createNearerScopeAbsence(
                VirtualFrame frame, String name, int lexicalDepth) {
            return CapturedNearerScopeAbsence.create(
                    ProtosFrameArguments.compactCapturedLexicalEnvironment(frame.getArguments()),
                    name,
                    lexicalDepth);
        }

        static CapturedNearerScopeAbsence unprovenNearerScopeAbsence(int lexicalDepth) {
            return CapturedNearerScopeAbsence.uncached(lexicalDepth);
        }

        static CapturedOwnerFrameCache createOwnerFrameCache() {
            return CapturedOwnerFrameCache.create();
        }

        static CapturedOwnerFrameCache retiredOwnerFrameCache() {
            return CapturedOwnerFrameCache.retired();
        }
    }

    @Operation
    @ConstantOperand(type = String.class, name = "name")
    public static final class ReadCapturedFallbackAtRoot {
        @Specialization
        public static Object perform(String name, @Bind VirtualFrame frame) {
            return lookupCapturedFallbackAtRoot(frame.getArguments(), name);
        }
    }

    /**
     * PERF038-C: the generic captured lookup of a root-level read, including
     * the exact activation materialization and publication it may require,
     * as one host call instead of a materialization call followed by a
     * lookup call. Receives the frame-argument array, never the frame.
     */
    @TruffleBoundary
    static Object lookupCapturedFallbackAtRoot(Object[] arguments, String name) {
        return ProtosCapturedLexicalAccess.lookupCapturedFallback(
                ProtosFrameArguments.activation(arguments), name);
    }

    @Operation
    @ConstantOperand(type = int.class, name = "frameOrdinal")
    public static final class ReadCapturedFrameLocal {
        @Specialization
        public static Object perform(
                int frameOrdinal,
                ProtosActivation activation,
                String name,
                int lexicalDepth) {
            if (lexicalDepth > 0
                    && !activation.currentContextHasLocalSlotForRuntime(name)) {
                Object value =
                        ProtosCapturedLexicalAccess.readCapturedFrameBindingOrNull(
                                activation.capturedLexicalEnvironmentForRuntime(),
                                name,
                                lexicalDepth,
                                frameOrdinal);
                if (value != null) {
                    return value;
                }
            }
            return ProtosCapturedLexicalAccess.lookupCapturedFallback(activation, name);
        }
    }

    /*
     * BUG018-C: a proven captured-materialized read is lowered as owner-frame
     * selection, IsCapturedOwnerFrameSelected, then either the builtin
     * LoadLocalMaterialized of the owner local from the selected frame or the
     * generic captured lookup. Selection never inspects the value.
     */

    @Operation
    @ConstantOperand(type = MaterializedLocalAccessor.class)
    public static final class SelectCapturedMaterializedOwnerFrame {
        @Specialization
        public static MaterializedFrame perform(
                MaterializedLocalAccessor accessor,
                ProtosActivation activation,
                String name,
                int lexicalDepth,
                @Bind BytecodeNode bytecodeNode) {
            if (lexicalDepth > 0
                    && !activation.currentContextHasLocalSlotForRuntime(name)) {
                return ProtosCapturedLexicalAccess.capturedMaterializedOwnerFrameOrNull(
                        accessor,
                        activation.capturedLexicalEnvironmentForRuntime(),
                        name,
                        lexicalDepth,
                        bytecodeNode);
            }
            return null;
        }
    }

    /** True when owner-frame selection produced a frame (host {@code null} means none). */
    @Operation
    public static final class IsCapturedOwnerFrameSelected {
        @Specialization
        public static boolean perform(Object selectedOwnerFrame) {
            return selectedOwnerFrame != null;
        }
    }

    @Operation
    public static final class ReadCapturedFallback {
        @Specialization
        public static Object perform(ProtosActivation activation, String name) {
            return ProtosCapturedLexicalAccess.lookupCapturedFallback(activation, name);
        }
    }

    @Operation
    @ConstantOperand(type = int.class, name = "frameOrdinal")
    public static final class ResolveCapturedWritableLexicalTarget {
        @Specialization
        public static CapturedLexicalWriteTarget perform(
                int frameOrdinal,
                ProtosActivation activation,
                String name,
                int lexicalDepth) {
            if (lexicalDepth > 0) {
                if (activation.currentContextHasLocalSlotForRuntime(name)) {
                    return CapturedLexicalWriteTarget.generic(
                            activation.context());
                }

                CapturedLexicalWriteTarget selected =
                        ProtosCapturedLexicalAccess.capturedFrameWriteTargetOrNull(
                                activation.capturedLexicalEnvironmentForRuntime(),
                                name,
                                lexicalDepth,
                                frameOrdinal);
                if (selected != null) {
                    return selected;
                }
            }

            ProtosObjectValue fallback =
                    ProtosLexicalFallback.writableContextByName(activation, name)
                            .orElseThrow(
                                    () ->
                                            new ProtosSignalException(
                                                    ProtosCoreErrors.newSlotNotFound(
                                                            activation)));
            return CapturedLexicalWriteTarget.generic(fallback);
        }
    }

    @Operation
    @ConstantOperand(type = int.class, name = "frameOrdinal")
    public static final class AssignCapturedFrameLocal {
        @Specialization
        public static Object perform(
                int frameOrdinal,
                ProtosActivation activation,
                CapturedLexicalWriteTarget destination,
                String name,
                Object value) {
            try {
                ProtosCapturedLexicalAccess.assignCapturedFrameDestination(destination, name, frameOrdinal, value);
            } catch (IllegalStateException invalidMutation) {
                throw new ProtosSignalException(
                        ProtosCoreErrors.newError(activation));
            }
            return value;
        }
    }

    @Operation
    @ConstantOperand(type = MaterializedLocalAccessor.class)
    public static final class ResolveCapturedMaterializedWritableLexicalTarget {
        @Specialization
        public static CapturedLexicalWriteTarget perform(
                MaterializedLocalAccessor accessor,
                ProtosActivation activation,
                String name,
                int lexicalDepth,
                @Bind BytecodeNode bytecodeNode) {
            if (lexicalDepth > 0) {
                if (activation.currentContextHasLocalSlotForRuntime(name)) {
                    return CapturedLexicalWriteTarget.generic(
                            activation.context());
                }

                CapturedLexicalWriteTarget selected =
                        ProtosCapturedLexicalAccess.capturedMaterializedWriteTargetOrNull(
                                accessor,
                                activation.capturedLexicalEnvironmentForRuntime(),
                                name,
                                lexicalDepth,
                                bytecodeNode);
                if (selected != null) {
                    return selected;
                }
            }

            ProtosObjectValue fallback =
                    ProtosLexicalFallback.writableContextByName(activation, name)
                            .orElseThrow(
                                    () ->
                                            new ProtosSignalException(
                                                    ProtosCoreErrors.newSlotNotFound(
                                                            activation)));
            return CapturedLexicalWriteTarget.generic(fallback);
        }
    }

    @Operation
    @ConstantOperand(type = MaterializedLocalAccessor.class)
    public static final class AssignCapturedMaterializedLocal {
        @Specialization
        public static Object perform(
                MaterializedLocalAccessor accessor,
                ProtosActivation activation,
                CapturedLexicalWriteTarget destination,
                String name,
                Object value,
                @Bind BytecodeNode bytecodeNode) {
            try {
                ProtosCapturedLexicalAccess.assignCapturedMaterializedDestination(
                        accessor, destination, name, value, bytecodeNode);
            } catch (IllegalStateException invalidMutation) {
                throw new ProtosSignalException(
                        ProtosCoreErrors.newError(activation));
            }
            return value;
        }
    }

    /*
     * PERF025 slice 3: inline-callback consumers whose successful path
     * observes neither the callback activation nor its Context. Each takes
     * the PreparedInlineLiteralCall carrier; the activation is materialized
     * (through ProtosInlineCallbackFrameBindings.durableActivation) only for
     * an observer, Error or fallback path, which then runs the unchanged
     * activation operation.
     */

    @Operation
    @ConstantOperand(
            type = LocalRangeAccessor.class,
            name = "frameBackedLocals")
    @ConstantOperand(
            type = ProtosFrameLexicalLayout.class,
            name = "frameBackedLayout")
    @ConstantOperand(type = int.class, name = "frameOrdinal")
    public static final class ReadInlineCapturedFrameLocal {
        @Specialization
        public static Object perform(
                LocalRangeAccessor frameBackedLocals,
                ProtosFrameLexicalLayout frameBackedLayout,
                int frameOrdinal,
                PreparedInlineLiteralCall child,
                String name,
                int lexicalDepth,
                @Bind BytecodeNode bytecodeNode,
                @Bind VirtualFrame frame) {
            return ProtosInlineCallbackFrameBindings.readCaptured(
                    child, frameBackedLocals, frameBackedLayout,
                    name, lexicalDepth, frameOrdinal, bytecodeNode, frame);
        }
    }

    @Operation
    @ConstantOperand(type = MaterializedLocalAccessor.class)
    @ConstantOperand(
            type = LocalRangeAccessor.class,
            name = "frameBackedLocals")
    @ConstantOperand(
            type = ProtosFrameLexicalLayout.class,
            name = "frameBackedLayout")
    public static final class SelectInlineCapturedMaterializedOwnerFrame {
        @Specialization
        public static MaterializedFrame perform(
                MaterializedLocalAccessor accessor,
                LocalRangeAccessor frameBackedLocals,
                ProtosFrameLexicalLayout frameBackedLayout,
                PreparedInlineLiteralCall child,
                String name,
                int lexicalDepth,
                @Bind BytecodeNode bytecodeNode,
                @Bind VirtualFrame frame) {
            return ProtosInlineCallbackFrameBindings.selectCapturedMaterializedOwnerFrame(
                    accessor, child, frameBackedLocals, frameBackedLayout,
                    name, lexicalDepth, bytecodeNode, frame);
        }
    }

    @Operation
    @ConstantOperand(
            type = LocalRangeAccessor.class,
            name = "frameBackedLocals")
    @ConstantOperand(
            type = ProtosFrameLexicalLayout.class,
            name = "frameBackedLayout")
    public static final class ReadInlineCapturedFallback {
        @Specialization
        public static Object perform(
                LocalRangeAccessor frameBackedLocals,
                ProtosFrameLexicalLayout frameBackedLayout,
                PreparedInlineLiteralCall child,
                String name,
                @Bind BytecodeNode bytecodeNode,
                @Bind VirtualFrame frame) {
            return ProtosInlineCallbackFrameBindings.readCapturedFallback(
                    child, frameBackedLocals, frameBackedLayout,
                    name, bytecodeNode, frame);
        }
    }

    @Operation
    @ConstantOperand(
            type = LocalRangeAccessor.class,
            name = "frameBackedLocals")
    @ConstantOperand(
            type = ProtosFrameLexicalLayout.class,
            name = "frameBackedLayout")
    @ConstantOperand(type = int.class, name = "frameOrdinal")
    public static final class ResolveInlineCapturedWritableLexicalTarget {
        @Specialization
        public static CapturedLexicalWriteTarget perform(
                LocalRangeAccessor frameBackedLocals,
                ProtosFrameLexicalLayout frameBackedLayout,
                int frameOrdinal,
                PreparedInlineLiteralCall child,
                String name,
                int lexicalDepth,
                @Bind BytecodeNode bytecodeNode,
                @Bind VirtualFrame frame) {
            return ProtosInlineCallbackFrameBindings.resolveCapturedWriteTarget(
                    child, frameBackedLocals, frameBackedLayout,
                    name, lexicalDepth, frameOrdinal, bytecodeNode, frame);
        }
    }

    @Operation
    @ConstantOperand(
            type = LocalRangeAccessor.class,
            name = "frameBackedLocals")
    @ConstantOperand(
            type = ProtosFrameLexicalLayout.class,
            name = "frameBackedLayout")
    @ConstantOperand(type = int.class, name = "frameOrdinal")
    public static final class AssignInlineCapturedFrameLocal {
        @Specialization
        public static Object perform(
                LocalRangeAccessor frameBackedLocals,
                ProtosFrameLexicalLayout frameBackedLayout,
                int frameOrdinal,
                PreparedInlineLiteralCall child,
                CapturedLexicalWriteTarget destination,
                String name,
                Object value,
                @Bind BytecodeNode bytecodeNode,
                @Bind VirtualFrame frame) {
            return ProtosInlineCallbackFrameBindings.assignCaptured(
                    child, frameBackedLocals, frameBackedLayout,
                    frameOrdinal, destination, name, value, bytecodeNode, frame);
        }
    }

    @Operation
    @ConstantOperand(type = MaterializedLocalAccessor.class)
    @ConstantOperand(
            type = LocalRangeAccessor.class,
            name = "frameBackedLocals")
    @ConstantOperand(
            type = ProtosFrameLexicalLayout.class,
            name = "frameBackedLayout")
    public static final class ResolveInlineCapturedMaterializedWritableLexicalTarget {
        @Specialization
        public static CapturedLexicalWriteTarget perform(
                MaterializedLocalAccessor accessor,
                LocalRangeAccessor frameBackedLocals,
                ProtosFrameLexicalLayout frameBackedLayout,
                PreparedInlineLiteralCall child,
                String name,
                int lexicalDepth,
                @Bind BytecodeNode bytecodeNode,
                @Bind VirtualFrame frame) {
            return ProtosInlineCallbackFrameBindings.resolveCapturedMaterializedWriteTarget(
                    accessor, child, frameBackedLocals, frameBackedLayout,
                    name, lexicalDepth, bytecodeNode, frame);
        }
    }

    @Operation
    @ConstantOperand(type = MaterializedLocalAccessor.class)
    @ConstantOperand(
            type = LocalRangeAccessor.class,
            name = "frameBackedLocals")
    @ConstantOperand(
            type = ProtosFrameLexicalLayout.class,
            name = "frameBackedLayout")
    public static final class AssignInlineCapturedMaterializedLocal {
        @Specialization
        public static Object perform(
                MaterializedLocalAccessor accessor,
                LocalRangeAccessor frameBackedLocals,
                ProtosFrameLexicalLayout frameBackedLayout,
                PreparedInlineLiteralCall child,
                CapturedLexicalWriteTarget destination,
                String name,
                Object value,
                @Bind BytecodeNode bytecodeNode,
                @Bind VirtualFrame frame) {
            return ProtosInlineCallbackFrameBindings.assignCapturedMaterialized(
                    accessor, child, frameBackedLocals, frameBackedLayout,
                    destination, name, value, bytecodeNode, frame);
        }
    }

    /** Inline-callback {@code THIS}: the receiver a direct Closure call binds. */
    @Operation
    @ConstantOperand(
            type = LocalRangeAccessor.class,
            name = "frameBackedLocals")
    @ConstantOperand(
            type = ProtosFrameLexicalLayout.class,
            name = "frameBackedLayout")
    public static final class LoadInlineCallbackReceiver {
        @Specialization
        public static Object perform(
                LocalRangeAccessor frameBackedLocals,
                ProtosFrameLexicalLayout frameBackedLayout,
                PreparedInlineLiteralCall child,
                @Bind BytecodeNode bytecodeNode,
                @Bind VirtualFrame frame) {
            return ProtosInlineCallbackFrameBindings.receiver(
                    child, frameBackedLocals, frameBackedLayout, bytecodeNode, frame);
        }
    }

    /**
     * Inline-callback {@link ReadMember}: the same PIC tiers, selected through
     * the invocation's prelude. Selection and receiver-bound extraction never
     * observe the activation; an absent member or unsupported representation
     * raises its exact Error from the materialized activation.
     */
    @Operation
    @ConstantOperand(
            type = LocalRangeAccessor.class,
            name = "frameBackedLocals")
    @ConstantOperand(
            type = ProtosFrameLexicalLayout.class,
            name = "frameBackedLayout")
    @ConstantOperand(type = String.class, name = "name")
    public static final class ReadInlineMember {
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
                LocalRangeAccessor frameBackedLocals,
                ProtosFrameLexicalLayout frameBackedLayout,
                String name,
                PreparedInlineLiteralCall child,
                Object receiver,
                @Bind("child.prelude()") ProtosPrelude prelude,
                @Cached("createSharedInheritedLookupForPrelude(receiver, name, prelude)")
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
                LocalRangeAccessor frameBackedLocals,
                ProtosFrameLexicalLayout frameBackedLayout,
                String name,
                PreparedInlineLiteralCall child,
                Object receiver,
                @Bind("child.prelude()") ProtosPrelude prelude,
                @Cached("createSharedInheritedLookupForPrelude(receiver, name, prelude)")
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
                LocalRangeAccessor frameBackedLocals,
                ProtosFrameLexicalLayout frameBackedLayout,
                String name,
                PreparedInlineLiteralCall child,
                Object receiver,
                @Bind("child.prelude()") ProtosPrelude prelude,
                @Cached("receiver") Object cachedReceiver,
                @Cached("createGuardedLookupForPrelude(receiver, name, prelude)")
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
                LocalRangeAccessor frameBackedLocals,
                ProtosFrameLexicalLayout frameBackedLayout,
                String name,
                PreparedInlineLiteralCall child,
                Object receiver,
                @Bind("child.prelude()") ProtosPrelude prelude,
                @Cached("receiver") Object cachedReceiver,
                @Cached("createGuardedLookupForPrelude(receiver, name, prelude)")
                        ProtosValueLookup.GuardedSlotSelection cachedLookup,
                @Bind Node node,
                @Cached InlinedBranchProfile closureExtraction) {
            return ProtosValueLookup.materializeGuardedMemberRead(
                    receiver, cachedLookup, node, closureExtraction);
        }

        @Specialization(replaces = {"guardedSharedInheritedPlain", "guardedSharedInherited", "guardedExactReceiverPlain", "guardedExactReceiver"})
        public static Object perform(
                LocalRangeAccessor frameBackedLocals,
                ProtosFrameLexicalLayout frameBackedLayout,
                String name,
                PreparedInlineLiteralCall child,
                Object receiver,
                @Bind BytecodeNode bytecodeNode,
                @Bind VirtualFrame frame) {
            Object value =
                    ReadMember.readMemberOrNull(
                            receiver, name, child.prelude());
            if (value != null) {
                return value;
            }
            return ReadMember.performForActivation(
                    ProtosInlineCallbackFrameBindings.durableActivation(
                            child, frameBackedLocals, frameBackedLayout, bytecodeNode, frame),
                    receiver,
                    name);
        }

        static ProtosValueLookup.SharedInheritedSlotSelection createSharedInheritedLookupForPrelude(
                Object receiver, String name, ProtosPrelude prelude) {
            return ReadMember.createSharedInheritedLookupForPrelude(
                    receiver, name, prelude);
        }

        static boolean matchesSharedInheritedLookup(
                Object receiver,
                String name,
                ProtosValueLookup.SharedInheritedSlotSelection cachedLookup) {
            return ReadMember.matchesSharedInheritedLookup(
                    receiver, name, cachedLookup);
        }

        static ProtosValueLookup.GuardedSlotSelection createGuardedLookupForPrelude(
                Object receiver, String name, ProtosPrelude prelude) {
            return ReadMember.createGuardedLookupForPrelude(
                    receiver, name, prelude);
        }
    }

    @Operation
    public static final class ResolveWritableLexicalTarget {
        @Specialization
        public static ResolvedLexicalWriteTarget perform(ProtosActivation activation, String name) {
            ResolvedLexicalWriteTarget selected = selectByNameOrNull(activation, name);
            if (selected == null) {
                throw new ProtosSignalException(
                        ProtosCoreErrors.newSlotNotFound(activation));
            }
            return selected;
        }


        /**
         * TEST009-K: the residual String-keyed destination walk is a host boundary, like
         * {@link ProtosLexicalFallback#readByName}. Statically proven bindings select their
         * destination through the frame-backed operations and never reach it.
         */
        @com.oracle.truffle.api.CompilerDirectives.TruffleBoundary
        private static ResolvedLexicalWriteTarget selectByNameOrNull(
                ProtosActivation activation,
                String name) {
            if (activation.currentContextHasLocalSlotForRuntime(name)) {
                return ResolvedLexicalWriteTarget.currentContext();
            }

            for (ProtosLexicalEnvironment lexical =
                            activation.capturedLexicalEnvironmentForRuntime();
                    lexical != null;
                    lexical = lexical.outer()) {
                if (lexical.hasLocalSlotForRuntime(name)) {
                    return ResolvedLexicalWriteTarget.object(
                            lexical.context());
                }
            }

            if (activation.receiver()
                            instanceof ProtosObjectValue ordinaryReceiver
                    && ordinaryReceiver.hasLocalSlot(name)) {
                return ResolvedLexicalWriteTarget.object(
                        ordinaryReceiver);
            }

            return null;
        }
}

    @Operation
    public static final class AssignResolvedLexicalTarget {
        @Specialization
        public static Object perform(
                ProtosActivation activation,
                ResolvedLexicalWriteTarget destination,
                String name,
                Object value) {
            try {
                if (destination.currentContext) {
                    activation.assignCurrentLocalSlotForRuntime(
                            name,
                            value);
                } else {
                    destination.object.assignLocalSlot(
                            name,
                            value);
                }
            } catch (IllegalStateException invalidMutation) {
                throw new ProtosSignalException(
                        ProtosCoreErrors.newError(activation));
            }
            return value;
        }
    }

    @Operation
    @ConstantOperand(type = LocalAccessor.class, name = "accessor")
    public static final class ResolveCurrentFrameLocalWriteTarget {
        @Specialization
        public static ResolvedLexicalWriteTarget perform(
                LocalAccessor accessor,
                ProtosActivation activation,
                String name,
                @Bind BytecodeNode bytecodeNode,
                @Bind VirtualFrame frame) {
            if (activation.hasGenuineExecutionContextForRuntime()
                    && !accessor.isCleared(bytecodeNode, frame)) {
                return ResolvedLexicalWriteTarget.STATIC_CURRENT_FRAME_LOCAL;
            }
            return ResolveWritableLexicalTarget.perform(activation, name);
        }
    }

    @Operation
    @ConstantOperand(type = LocalAccessor.class, name = "accessor")
    public static final class AssignCurrentFrameLocal {
        @Specialization
        public static Object perform(
                LocalAccessor accessor,
                ProtosActivation activation,
                ResolvedLexicalWriteTarget destination,
                String name,
                Object value,
                @Bind BytecodeNode bytecodeNode,
                @Bind VirtualFrame frame) {
            if (destination != ResolvedLexicalWriteTarget.STATIC_CURRENT_FRAME_LOCAL) {
                // I091: only direct frame storage may retain a primitive carrier.
                return AssignResolvedLexicalTarget.perform(
                        activation, destination, name,
                        ProtosNumericValueSupport.guestValue(value));
            }
            if (activation.currentContextIsFrozenForRuntime()
                    || accessor.isCleared(bytecodeNode, frame)) {
                throw new ProtosSignalException(
                        ProtosCoreErrors.newError(activation));
            }
            accessor.setObject(bytecodeNode, frame, value);
            return value;
        }
    }

    /**
     * PERF025 compact callee execution: root-level form of {@link
     * ResolveCurrentFrameLocalWriteTarget}, emitted when the current
     * activation is the root's own. A frame still in compact source-call form
     * denotes a genuine, unobserved (hence OPEN, non-FROZEN) execution
     * context, so a PRESENT local is selected directly; an ABSENT one (D179
     * C0) materializes the exact activation and runs the unchanged selection.
     */
    @Operation
    @ConstantOperand(type = LocalAccessor.class, name = "accessor")
    public static final class ResolveRootFrameLocalWriteTarget {
        @Specialization
        public static ResolvedLexicalWriteTarget perform(
                LocalAccessor accessor,
                String name,
                @Bind BytecodeNode bytecodeNode,
                @Bind VirtualFrame frame) {
            Object[] arguments = frame.getArguments();
            if (ProtosFrameArguments.isUnmaterializedCompactCall(arguments)
                    && !accessor.isCleared(bytecodeNode, frame)) {
                return ResolvedLexicalWriteTarget.STATIC_CURRENT_FRAME_LOCAL;
            }
            return ResolveCurrentFrameLocalWriteTarget.perform(
                    accessor, ProtosFrameArguments.activation(arguments), name, bytecodeNode, frame);
        }
    }

    /**
     * PERF025 compact callee execution: root-level form of {@link
     * AssignCurrentFrameLocal}. The retained static destination of a still
     * compact (never FROZEN) context is written directly when PRESENT; a local
     * cleared by the RHS, and every other destination, takes the unchanged
     * activation path and its exact mutation Error.
     */
    @Operation
    @ConstantOperand(type = LocalAccessor.class, name = "accessor")
    public static final class AssignRootFrameLocal {
        @Specialization
        public static Object perform(
                LocalAccessor accessor,
                ResolvedLexicalWriteTarget destination,
                String name,
                Object value,
                @Bind BytecodeNode bytecodeNode,
                @Bind VirtualFrame frame) {
            Object[] arguments = frame.getArguments();
            if (destination == ResolvedLexicalWriteTarget.STATIC_CURRENT_FRAME_LOCAL
                    && ProtosFrameArguments.isUnmaterializedCompactCall(arguments)
                    && !accessor.isCleared(bytecodeNode, frame)) {
                accessor.setObject(bytecodeNode, frame, value);
                return value;
            }
            return AssignCurrentFrameLocal.perform(
                    accessor, ProtosFrameArguments.activation(arguments),
                    destination, name, value, bytecodeNode, frame);
        }
    }

    /** Inline-callback form of {@link ResolveCurrentFrameLocalWriteTarget}. */
    @Operation
    @ConstantOperand(type = LocalAccessor.class, name = "accessor")
    @ConstantOperand(
            type = LocalRangeAccessor.class,
            name = "frameBackedLocals")
    @ConstantOperand(
            type = ProtosFrameLexicalLayout.class,
            name = "frameBackedLayout")
    public static final class ResolveInlineFrameLocalWriteTarget {
        @Specialization
        public static ResolvedLexicalWriteTarget perform(
                LocalAccessor accessor,
                LocalRangeAccessor frameBackedLocals,
                ProtosFrameLexicalLayout frameBackedLayout,
                PreparedInlineLiteralCall child,
                String name,
                @Bind BytecodeNode bytecodeNode,
                @Bind VirtualFrame frame) {
            return ProtosInlineCallbackFrameBindings.resolveWriteTarget(
                    child, accessor, frameBackedLocals, frameBackedLayout,
                    name, bytecodeNode, frame);
        }
    }

    /** Inline-callback form of {@link AssignCurrentFrameLocal}. */
    @Operation
    @ConstantOperand(type = LocalAccessor.class, name = "accessor")
    @ConstantOperand(
            type = LocalRangeAccessor.class,
            name = "frameBackedLocals")
    @ConstantOperand(
            type = ProtosFrameLexicalLayout.class,
            name = "frameBackedLayout")
    public static final class AssignInlineFrameLocal {
        @Specialization
        public static Object perform(
                LocalAccessor accessor,
                LocalRangeAccessor frameBackedLocals,
                ProtosFrameLexicalLayout frameBackedLayout,
                PreparedInlineLiteralCall child,
                ResolvedLexicalWriteTarget destination,
                String name,
                Object value,
                @Bind BytecodeNode bytecodeNode,
                @Bind VirtualFrame frame) {
            return ProtosInlineCallbackFrameBindings.assign(
                    child, accessor, frameBackedLocals, frameBackedLayout,
                    destination, name, value, bytecodeNode, frame);
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
    }

    /**
     * PERF030: {@link CreateCurrentLocalSlot} for a statically proven current
     * binding of a root that installs its persistent frame authority. The
     * {@code ordinal} is a constant operand because the frame-local accesses
     * of {@link ProtosFrameLexicalBindingAuthority#createFrameBackedBindingAt}
     * require a partial-evaluation constant index; only the unchanged named
     * fallback resolves {@code name}.
     */
    @Operation
    @ConstantOperand(
            type = ProtosFrameLexicalLayout.class,
            name = "frameBackedLayout")
    @ConstantOperand(type = int.class, name = "ordinal")
    public static final class CreateCurrentIndexedLocalSlot {
        @Specialization
        public static Object perform(
                ProtosFrameLexicalLayout frameBackedLayout,
                int ordinal,
                ProtosActivation activation,
                String name,
                Object value) {
            return ProtosBytecodeRootNode.CreateCurrentIndexedLocalSlot.createIndexedCurrentLocalSlot(
                    frameBackedLayout, activation, ordinal, name, value);
        }


        /**
         * PERF030: target-less creation of a statically proven current binding of
         * a root that installs its persistent frame authority, at the ordinal the
         * lowerer took from {@code frameBackedLayout}. Admitted exactly like
         * {@link #bindIndexedClosureParameter}, still rejecting a PRESENT binding
         * as a duplicate creation; any other state takes the unchanged named
         * {@link CreateCurrentLocalSlot} path.
         */
        static Object createIndexedCurrentLocalSlot(
                ProtosFrameLexicalLayout frameBackedLayout,
                ProtosActivation activation,
                int ordinal,
                String name,
                Object value) {
            if (activation.currentAuthorityAdmittingLocalCreationForRuntime()
                            instanceof ProtosFrameLexicalBindingAuthority authority
                    && authority.storesLayout(frameBackedLayout)) {
                try {
                    authority.createFrameBackedBindingAt(ordinal, value);
                } catch (IllegalStateException invalidCreation) {
                    throw new ProtosSignalException(
                            ProtosCoreErrors.newError(activation));
                }
                return value;
            }
            return CreateCurrentLocalSlot.perform(activation, name, value);
        }
}

    @Operation
    public static final class CreateLocalSlot {
        @Specialization
        public static Object perform(
                ProtosActivation activation,
                ProtosObjectValue target,
                String name,
                Object value) {
            try {
                target.createLocalSlot(name, value);
            } catch (IllegalStateException invalidMutation) {
                throw new ProtosSignalException(ProtosCoreErrors.newError(activation));
            }
            return value;
        }
    }

    @Operation
    public static final class MultipleCreateLocalSlots {
        @Specialization
        public static Object perform(ProtosActivation activation, String[] names, Object source) {
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

    @Operation
    public static final class AssignLocalSlot {
        @Specialization
        public static Object perform(
                ProtosActivation activation,
                ProtosObjectValue target,
                String name,
                Object value) {
            try {
                target.assignLocalSlot(name, value);
            } catch (IllegalStateException invalidMutation) {
                throw new ProtosSignalException(ProtosCoreErrors.newError(activation));
            }
            return value;
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

    @Operation
    public static final class Identity {
        @Specialization
        public static Object perform(Object left, Object right) {
            return ProtosIdentity.identical(left, right)
                    ? ProtosBooleanValue.TRUE
                    : ProtosBooleanValue.FALSE;
        }
    }

    @Operation
    public static final class NotIdentity {
        @Specialization
        public static Object perform(Object left, Object right) {
            return ProtosIdentity.identical(left, right)
                    ? ProtosBooleanValue.FALSE
                    : ProtosBooleanValue.TRUE;
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
            if (target != null && !target.isMaterialized()) {
                throw new IllegalStateException(
                        "non-local return reached a return home proven unobservable");
            }
            if (target == null || !target.isActive()) {
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
            ProtosPrelude prelude = PrepareSendOne.callerPrelude(caller);
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
     */
    @Operation
    @ConstantOperand(type = boolean.class, name = "compactCapture")
    public static final class MaterializeCurrentClosure {
        @Specialization(guards = "hasPublishedActivation(frame)")
        public static ProtosClosureValue published(
                boolean compactCapture,
                CanonicalClosure definition,
                ProtosClosureExecutionPlanCell executionPlanCell,
                @Bind VirtualFrame frame) {
            return MaterializeClosure.perform(
                    ProtosFrameArguments.publishedActivation(frame.getArguments()),
                    definition,
                    executionPlanCell);
        }

        @Specialization(guards = {"compactCapture", "!hasPublishedActivation(frame)"})
        public static ProtosClosureValue compact(
                boolean compactCapture,
                CanonicalClosure definition,
                ProtosClosureExecutionPlanCell executionPlanCell,
                @Bind VirtualFrame frame) {
            return ProtosFrameArguments.materializeCompactClosure(
                    frame.getArguments(), definition, executionPlanCell);
        }

        @Specialization(guards = {"!compactCapture", "!hasPublishedActivation(frame)"})
        public static ProtosClosureValue rich(
                boolean compactCapture,
                CanonicalClosure definition,
                ProtosClosureExecutionPlanCell executionPlanCell,
                @Bind VirtualFrame frame) {
            return MaterializeClosure.perform(
                    ProtosFrameArguments.activation(frame),
                    definition,
                    executionPlanCell);
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

    @Operation
    public static final class PrepareMapConstructionFactoryCall {
        @Specialization
        public static PreparedClosureCall perform(ProtosActivation activation, Object factory) {
            ProtosSlotLookupResult selected =
                    ProtosStandardMapProtocol
                            .selectFactoryCallForMapConstruction(
                                    factory,
                                    activation);

            return ProtosSendPreparation.prepareImmediateMethodCall(
                    (ProtosClosureValue) selected.value(),
                    factory,
                    selected.home(),
                    List.of(),
                    activation);
        }
    }

    @Operation
    public static final class FinishMapConstructionFactoryCall {
        @Specialization
        public static ProtosMapValue perform(ProtosActivation activation, Object result) {
            return ProtosStandardMapProtocol
                    .requireMapConstructionResult(
                            result,
                            activation);
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

    @Operation
    public static final class EnterMapInitialDefinitionComparison {
        @Specialization
        public static void perform(PreparedMapInitialDefinition prepared) {
            prepared.enterComparison();
        }
    }

    @Operation
    public static final class LeaveMapInitialDefinitionComparison {
        @Specialization
        public static void perform(PreparedMapInitialDefinition prepared) {
            prepared.leaveComparison();
        }
    }

    @Operation
    public static final class PrepareMapInitialDefinitionHashCall {
        @Specialization
        public static PreparedClosureCall perform(PreparedMapInitialDefinition prepared) {
            return prepared.prepareHash();
        }
    }

    @Operation
    public static final class AcceptMapInitialDefinitionHashResult {
        @Specialization
        public static void perform(PreparedMapInitialDefinition prepared, Object result) {
            prepared.acceptHash(result);
        }
    }

    @Operation
    public static final class MapInitialDefinitionNeedsEquality {
        @Specialization
        public static boolean perform(PreparedMapInitialDefinition prepared) {
            return prepared.needsEquality();
        }
    }

    @Operation
    public static final class PrepareMapInitialDefinitionEqualityCall {
        @Specialization
        public static PreparedClosureCall perform(PreparedMapInitialDefinition prepared) {
            return prepared.prepareEquality();
        }
    }

    @Operation
    public static final class AcceptMapInitialDefinitionEqualityResult {
        @Specialization
        public static void perform(PreparedMapInitialDefinition prepared, Object result) {
            prepared.acceptEquality(result);
        }
    }

    @Operation
    public static final class FinishMapInitialDefinition {
        @Specialization
        public static void perform(PreparedMapInitialDefinition prepared) {
            prepared.finish();
        }
    }

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
                        PrepareSendArguments.exactCaller(caller);
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


    @Operation
    @ConstantOperand(type = LocalRangeAccessor.class, name = "frameBackedLocals")
    @ConstantOperand(type = ProtosFrameLexicalLayout.class, name = "frameBackedLayout")
    public static final class AppendInlineSpreadSuppliedArgument {
        @Specialization
        public static void perform(
                LocalRangeAccessor frameBackedLocals,
                ProtosFrameLexicalLayout frameBackedLayout,
                PreparedArgumentVector vector,
                Object value,
                PreparedInlineLiteralCall child,
                @Bind BytecodeNode bytecodeNode,
                @Bind VirtualFrame frame) {
            if (!vector.appendSpreadArrayOrFalse(value)) {
                ProtosActivation exactCaller =
                        ProtosInlineCallbackFrameBindings.durableActivation(
                                child, frameBackedLocals, frameBackedLayout,
                                bytecodeNode, frame);
                throw new com.guillermomolina.protos.runtime.ProtosSignalException(
                        com.guillermomolina.protos.runtime.ProtosCoreErrors
                                .newError(exactCaller));
            }
        }
    }

    // ---- Prepared call/send selection ----------------------------------------------

    @Operation
    public static final class PrepareClosureCall {
        @Specialization(
                guards = {
                    "receiver == cachedReceiver",
                    "enteredContext != null",
                    "enteredContext == cachedContext",
                    "cachedGuarded != null"
                },
                assumptions = "cachedGuarded.stability()",
                limit = "3")
        public static PreparedClosureCall guardedDirect(
                Object receiver,
                ProtosActivation caller,
                @Bind("currentEnteredContext($node)")
                        ProtosLanguageContext enteredContext,
                @Cached("receiver") Object cachedReceiver,
                @Cached("enteredContext") ProtosLanguageContext cachedContext,
                @Cached("createGuardedDirectClosureCall(receiver, caller, enteredContext)")
                        GuardedDirectClosureCallTarget cachedGuarded) {
            return ProtosClosureCallPreparation.finishDirectClosureCallZero(
                    cachedGuarded.closure(), cachedGuarded.target(), caller);
        }

        @Specialization(
                guards = {
                    "closure != null",
                    "enteredContext != null",
                    "closureDefinition != null",
                    "closureDefinition == cachedClosureDefinition",
                    "enteredContext == cachedContext",
                    "cachedTarget != null"
                },
                limit = "3")
        public static PreparedClosureCall fastDirect(
                Object receiver,
                ProtosActivation caller,
                @Bind("directClosureCallSelectionOrNull(receiver, caller)")
                        ProtosClosureValue closure,
                @Bind("directClosureCallDefinitionOrNull(closure)")
                        CanonicalClosure closureDefinition,
                @Bind("currentEnteredContext($node)")
                        ProtosLanguageContext enteredContext,
                @Cached("closureDefinition") CanonicalClosure cachedClosureDefinition,
                @Cached("enteredContext") ProtosLanguageContext cachedContext,
                @Cached("fastOrdinarySendTarget(closure, enteredContext)")
                        RootCallTarget cachedTarget) {
            return ProtosClosureCallPreparation.finishDirectClosureCallZero(
                    closure, cachedTarget, caller);
        }

        @Specialization(replaces = {"guardedDirect", "fastDirect"})
        public static PreparedClosureCall perform(Object receiver, ProtosActivation caller) {
            return ProtosClosureCallPreparation.prepareClosureCall(receiver, List.of(), caller);
        }

        @NonIdempotent
        static ProtosLanguageContext currentEnteredContext(Node node) {
            return ProtosLanguageContext.current(node);
        }

        static GuardedDirectClosureCallTarget createGuardedDirectClosureCall(
                Object receiver, ProtosActivation caller, ProtosLanguageContext enteredContext) {
            return ProtosClosureCallPreparation.createGuardedDirectClosureCall(
                    receiver, caller, enteredContext);
        }

        static ProtosClosureValue directClosureCallSelectionOrNull(
                Object receiver, ProtosActivation caller) {
            return ProtosClosureCallPreparation.directClosureCallSelectionOrNull(receiver, caller);
        }

        static CanonicalClosure directClosureCallDefinitionOrNull(ProtosClosureValue closure) {
            return ProtosClosureCallPreparation.directClosureCallDefinitionOrNull(closure);
        }

        static RootCallTarget fastOrdinarySendTarget(
                ProtosClosureValue closure, ProtosLanguageContext enteredContext) {
            return PrepareSendArguments.fastOrdinarySendTarget(
                    closure, enteredContext);
        }
    }

    @Operation
    public static final class PrepareClosureCallArguments {
        @Specialization(
                guards = {
                    "receiver == cachedReceiver",
                    "enteredContext != null",
                    "enteredContext == cachedContext",
                    "cachedGuarded != null"
                },
                assumptions = "cachedGuarded.stability()",
                limit = "3")
        public static PreparedClosureCall guardedDirect(
                Object receiver,
                ProtosActivation caller,
                @Variadic Object[] supplied,
                @Bind("currentEnteredContext($node)")
                        ProtosLanguageContext enteredContext,
                @Cached("receiver") Object cachedReceiver,
                @Cached("enteredContext") ProtosLanguageContext cachedContext,
                @Cached("createGuardedDirectClosureCall(receiver, caller, enteredContext)")
                        GuardedDirectClosureCallTarget cachedGuarded) {
            return ProtosClosureCallPreparation.finishDirectClosureCall(
                    cachedGuarded.closure(), cachedGuarded.target(), supplied, caller);
        }

        @Specialization(
                guards = {
                    "closure != null",
                    "enteredContext != null",
                    "closureDefinition != null",
                    "closureDefinition == cachedClosureDefinition",
                    "enteredContext == cachedContext",
                    "cachedTarget != null"
                },
                limit = "3")
        public static PreparedClosureCall fastDirect(
                Object receiver,
                ProtosActivation caller,
                @Variadic Object[] supplied,
                @Bind("directClosureCallSelectionOrNull(receiver, caller)")
                        ProtosClosureValue closure,
                @Bind("directClosureCallDefinitionOrNull(closure)")
                        CanonicalClosure closureDefinition,
                @Bind("currentEnteredContext($node)")
                        ProtosLanguageContext enteredContext,
                @Cached("closureDefinition") CanonicalClosure cachedClosureDefinition,
                @Cached("enteredContext") ProtosLanguageContext cachedContext,
                @Cached("fastOrdinarySendTarget(closure, enteredContext)")
                        RootCallTarget cachedTarget) {
            return ProtosClosureCallPreparation.finishDirectClosureCall(closure, cachedTarget, supplied, caller);
        }

        @Specialization(replaces = {"guardedDirect", "fastDirect"})
        public static PreparedClosureCall perform(
                Object receiver,
                ProtosActivation caller,
                @Variadic Object[] supplied) {
            return ProtosClosureCallPreparation.prepareClosureCall(
                    receiver,
                    List.of(supplied),
                    caller);
        }

        @NonIdempotent
        static ProtosLanguageContext currentEnteredContext(Node node) {
            return ProtosLanguageContext.current(node);
        }

        static GuardedDirectClosureCallTarget createGuardedDirectClosureCall(
                Object receiver, ProtosActivation caller, ProtosLanguageContext enteredContext) {
            return ProtosClosureCallPreparation.createGuardedDirectClosureCall(
                    receiver, caller, enteredContext);
        }

        static ProtosClosureValue directClosureCallSelectionOrNull(
                Object receiver, ProtosActivation caller) {
            return ProtosClosureCallPreparation.directClosureCallSelectionOrNull(receiver, caller);
        }

        static CanonicalClosure directClosureCallDefinitionOrNull(ProtosClosureValue closure) {
            return ProtosClosureCallPreparation.directClosureCallDefinitionOrNull(closure);
        }

        static RootCallTarget fastOrdinarySendTarget(
                ProtosClosureValue closure, ProtosLanguageContext enteredContext) {
            return PrepareSendArguments.fastOrdinarySendTarget(
                    closure, enteredContext);
        }
    }


    /* PERF038-H: dedicated single-argument Closure call. */
    @Operation
    public static final class PrepareClosureCallOne {
        @Specialization(
                guards = {
                    "receiver == cachedReceiver",
                    "enteredContext != null",
                    "enteredContext == cachedContext",
                    "cachedGuarded != null"
                },
                assumptions = "cachedGuarded.stability()",
                limit = "3")
        public static PreparedClosureCall guardedDirect(
                Object receiver,
                ProtosActivation caller,
                Object supplied0,
                @Bind("currentEnteredContext($node)")
                        ProtosLanguageContext enteredContext,
                @Cached("receiver") Object cachedReceiver,
                @Cached("enteredContext") ProtosLanguageContext cachedContext,
                @Cached("createGuardedDirectClosureCall(receiver, caller, enteredContext)")
                        GuardedDirectClosureCallTarget cachedGuarded) {

            return ProtosClosureCallPreparation.finishDirectClosureCallOne(
                    cachedGuarded.closure(),
                    cachedGuarded.target(),
                    supplied0,
                    caller);
        }

        @Specialization(
                guards = {
                    "closure != null",
                    "enteredContext != null",
                    "closureDefinition != null",
                    "closureDefinition == cachedClosureDefinition",
                    "enteredContext == cachedContext",
                    "cachedTarget != null"
                },
                limit = "3")
        public static PreparedClosureCall fastDirect(
                Object receiver,
                ProtosActivation caller,
                Object supplied0,
                @Bind("directClosureCallSelectionOrNull(receiver, caller)")
                        ProtosClosureValue closure,
                @Bind("directClosureCallDefinitionOrNull(closure)")
                        CanonicalClosure closureDefinition,
                @Bind("currentEnteredContext($node)")
                        ProtosLanguageContext enteredContext,
                @Cached("closureDefinition") CanonicalClosure cachedClosureDefinition,
                @Cached("enteredContext") ProtosLanguageContext cachedContext,
                @Cached("fastOrdinarySendTarget(closure, enteredContext)")
                        RootCallTarget cachedTarget) {

            return ProtosClosureCallPreparation.finishDirectClosureCallOne(
                    closure,
                    cachedTarget,
                    supplied0,
                    caller);
        }

        @Specialization(replaces = {"guardedDirect", "fastDirect"})
        public static PreparedClosureCall perform(
                Object receiver,
                ProtosActivation caller,
                Object supplied0) {
            return ProtosClosureCallPreparation.prepareClosureCallOneFallback(
                    receiver, caller, supplied0);

        }

        @NonIdempotent
        static ProtosLanguageContext currentEnteredContext(Node node) {
            return ProtosLanguageContext.current(node);
        }

        static GuardedDirectClosureCallTarget createGuardedDirectClosureCall(
                Object receiver, ProtosActivation caller, ProtosLanguageContext enteredContext) {
            return ProtosClosureCallPreparation.createGuardedDirectClosureCall(
                    receiver, caller, enteredContext);
        }

        static ProtosClosureValue directClosureCallSelectionOrNull(
                Object receiver, ProtosActivation caller) {
            return ProtosClosureCallPreparation.directClosureCallSelectionOrNull(receiver, caller);
        }

        static CanonicalClosure directClosureCallDefinitionOrNull(ProtosClosureValue closure) {
            return ProtosClosureCallPreparation.directClosureCallDefinitionOrNull(closure);
        }

        static RootCallTarget fastOrdinarySendTarget(
                ProtosClosureValue closure, ProtosLanguageContext enteredContext) {
            return PrepareSendArguments.fastOrdinarySendTarget(
                    closure, enteredContext);
        }
    }

    @Operation
    public static final class PrepareDefaultClosureCallArguments {
        @Specialization(
                guards = {
                    "receiver == cachedReceiver",
                    "enteredContext != null",
                    "enteredContext == cachedContext",
                    "cachedGuarded != null"
                },
                assumptions = "cachedGuarded.stability()",
                limit = "3")
        public static PreparedClosureCall guardedDirect(
                Object receiver,
                ProtosActivation caller,
                @Variadic Object[] supplied,
                @Bind("currentEnteredContext($node)")
                        ProtosLanguageContext enteredContext,
                @Cached("receiver") Object cachedReceiver,
                @Cached("enteredContext") ProtosLanguageContext cachedContext,
                @Cached("createGuardedDirectClosureCall(receiver, caller, enteredContext)")
                        GuardedDirectClosureCallTarget cachedGuarded) {
            return ProtosClosureCallPreparation.finishDirectClosureCall(
                    cachedGuarded.closure(), cachedGuarded.target(), supplied, caller);
        }

        @Specialization(
                guards = {
                    "closure != null",
                    "enteredContext != null",
                    "closureDefinition != null",
                    "closureDefinition == cachedClosureDefinition",
                    "enteredContext == cachedContext",
                    "cachedTarget != null"
                },
                limit = "3")
        public static PreparedClosureCall fastDirect(
                Object receiver,
                ProtosActivation caller,
                @Variadic Object[] supplied,
                @Bind("directClosureCallSelectionOrNull(receiver, caller)")
                        ProtosClosureValue closure,
                @Bind("directClosureCallDefinitionOrNull(closure)")
                        CanonicalClosure closureDefinition,
                @Bind("currentEnteredContext($node)")
                        ProtosLanguageContext enteredContext,
                @Cached("closureDefinition") CanonicalClosure cachedClosureDefinition,
                @Cached("enteredContext") ProtosLanguageContext cachedContext,
                @Cached("fastOrdinarySendTarget(closure, enteredContext)")
                        RootCallTarget cachedTarget) {
            return ProtosClosureCallPreparation.finishDirectClosureCall(closure, cachedTarget, supplied, caller);
        }

        @Specialization(replaces = {"guardedDirect", "fastDirect"})
        public static PreparedClosureCall perform(
                Object receiver,
                ProtosActivation caller,
                @Variadic Object[] supplied) {
            return ProtosClosureCallPreparation.prepareClosureCall(
                    receiver,
                    List.of(supplied),
                    caller);
        }

        @NonIdempotent
        static ProtosLanguageContext currentEnteredContext(Node node) {
            return ProtosLanguageContext.current(node);
        }

        static GuardedDirectClosureCallTarget createGuardedDirectClosureCall(
                Object receiver, ProtosActivation caller, ProtosLanguageContext enteredContext) {
            return ProtosClosureCallPreparation.createGuardedDirectClosureCall(
                    receiver, caller, enteredContext);
        }

        static ProtosClosureValue directClosureCallSelectionOrNull(
                Object receiver, ProtosActivation caller) {
            return ProtosClosureCallPreparation.directClosureCallSelectionOrNull(receiver, caller);
        }

        static CanonicalClosure directClosureCallDefinitionOrNull(ProtosClosureValue closure) {
            return ProtosClosureCallPreparation.directClosureCallDefinitionOrNull(closure);
        }

        static RootCallTarget fastOrdinarySendTarget(
                ProtosClosureValue closure, ProtosLanguageContext enteredContext) {
            return PrepareSendArguments.fastOrdinarySendTarget(
                    closure, enteredContext);
        }
    }

    /**
     * PERF025 slice 3 inline-callback {@link PrepareSendArguments}: the same
     * specialization tiers, selected through the invocation's prelude. A
     * canonical guarded Integer hit observes no activation; an ordinary
     * source-backed hit prepares its compact child with a provenance-equivalent
     * caller ({@link ProtosInlineCallbackFrameBindings#invocationCaller}).
     * Every other path (non-canonical Integer selection, structured send,
     * generic send, any Error) runs the unchanged activation path.
     */
    @Operation
    @ConstantOperand(
            type = LocalRangeAccessor.class,
            name = "frameBackedLocals")
    @ConstantOperand(
            type = ProtosFrameLexicalLayout.class,
            name = "frameBackedLayout")
    public static final class PrepareInlineSendArguments {
        @Specialization(
                guards = {
                    "isIntegerReceiver(receiver)",
                    "selector.equals(cachedSelector)",
                    "enteredContext != null",
                    "enteredContext == cachedContext",
                    "prelude == cachedPrelude",
                    "cachedInteger != null"
                },
                assumptions = "cachedInteger.stability()",
                limit = "3")
        public static PreparedClosureCall guardedIntegerSend(
                LocalRangeAccessor frameBackedLocals,
                ProtosFrameLexicalLayout frameBackedLayout,
                Object receiver,
                String selector,
                PreparedInlineLiteralCall child,
                @Variadic Object[] supplied,
                @Bind BytecodeNode bytecodeNode,
                @Bind VirtualFrame frame,
                @Bind("currentEnteredContext($node)")
                        ProtosLanguageContext enteredContext,
                @Bind("child.prelude()") ProtosPrelude prelude,
                @Cached("selector") String cachedSelector,
                @Cached("enteredContext") ProtosLanguageContext cachedContext,
                @Cached("prelude") ProtosPrelude cachedPrelude,
                @Cached("createGuardedIntegerSend(receiver, selector, prelude, enteredContext)")
                        GuardedIntegerSend cachedInteger) {
            PreparedClosureCall canonical =
                    PrepareSendArguments.canonicalIntegerResultOrNull(
                            cachedInteger, receiver, supplied, prelude);
            if (canonical != null) {
                return canonical;
            }
            return PrepareSendArguments.guardedIntegerSendForActivation(
                    receiver, selector,
                    ProtosInlineCallbackFrameBindings.invocationCaller(
                            child, frameBackedLocals, frameBackedLayout, bytecodeNode, frame),
                    supplied, enteredContext, prelude,
                    cachedSelector, cachedContext, cachedPrelude, cachedInteger);
        }

        @Specialization(
                guards = {
                    "receiver == cachedReceiver",
                    "selector.equals(cachedSelector)",
                    "enteredContext != null",
                    "enteredContext == cachedContext",
                    "cachedSend != null"
                },
                assumptions = "cachedSend.stability()",
                limit = "3")
        public static PreparedClosureCall guardedOrdinarySend(
                LocalRangeAccessor frameBackedLocals,
                ProtosFrameLexicalLayout frameBackedLayout,
                Object receiver,
                String selector,
                PreparedInlineLiteralCall child,
                @Variadic Object[] supplied,
                @Bind BytecodeNode bytecodeNode,
                @Bind VirtualFrame frame,
                @Bind("currentEnteredContext($node)")
                        ProtosLanguageContext enteredContext,
                @Bind("child.prelude()") ProtosPrelude prelude,
                @Cached("receiver") Object cachedReceiver,
                @Cached("selector") String cachedSelector,
                @Cached("enteredContext") ProtosLanguageContext cachedContext,
                @Cached("createGuardedSendForPrelude(receiver, selector, prelude, enteredContext)")
                        GuardedSendTarget cachedSend) {
            return PrepareSendArguments.guardedOrdinarySend(
                    receiver, selector,
                    ProtosInlineCallbackFrameBindings.invocationCaller(
                            child, frameBackedLocals, frameBackedLayout, bytecodeNode, frame),
                    supplied, enteredContext,
                    cachedReceiver, cachedSelector, cachedContext, cachedSend);
        }

        /*
         * Unlike PrepareSendArguments.fastOrdinarySend, a failing selection
         * binds null and misses here: its exact Error is raised by the
         * generic path from the materialized activation.
         */
        @Specialization(
                guards = {
                    "!isForeignReceiver(receiver)",
                    "closure != null",
                    "enteredContext != null",
                    "selector.equals(cachedSelector)",
                    "closureDefinition != null",
                    "closureDefinition == cachedClosureDefinition",
                    "enteredContext == cachedContext",
                    "cachedTarget != null"
                },
                limit = "3")
        public static PreparedClosureCall fastOrdinarySend(
                LocalRangeAccessor frameBackedLocals,
                ProtosFrameLexicalLayout frameBackedLayout,
                Object receiver,
                String selector,
                PreparedInlineLiteralCall child,
                @Variadic Object[] supplied,
                @Bind BytecodeNode bytecodeNode,
                @Bind VirtualFrame frame,
                @Bind("ordinarySendSelectionOrNull(receiver, selector, child)")
                        ProtosSlotLookupResult selected,
                @Bind("ordinarySendClosureOrNull(selected)")
                        ProtosClosureValue closure,
                @Bind("ordinarySendClosureDefinitionOrNull(closure)")
                        CanonicalClosure closureDefinition,
                @Bind("currentEnteredContext($node)")
                        ProtosLanguageContext enteredContext,
                @Cached("selector") String cachedSelector,
                @Cached("closureDefinition") CanonicalClosure cachedClosureDefinition,
                @Cached("enteredContext") ProtosLanguageContext cachedContext,
                @Cached("fastOrdinarySendTarget(closure, enteredContext)")
                        RootCallTarget cachedTarget) {
            return PrepareSendArguments.fastOrdinarySendForActivation(
                    receiver, selector,
                    ProtosInlineCallbackFrameBindings.invocationCaller(
                            child, frameBackedLocals, frameBackedLayout, bytecodeNode, frame),
                    supplied, selected, closure, selected.home(),
                    closureDefinition, enteredContext, cachedSelector, cachedClosureDefinition,
                    cachedContext, cachedTarget);
        }

        /*
         * Structured sends keep the materialized activation as their caller;
         * only their D013 classification, which reads the caller's prelude,
         * uses a provenance-equivalent caller.
         */
        @Specialization(
                guards = {
                    "receiver == cachedReceiver",
                    "selector.equals(cachedSelector)",
                    "enteredContext != null",
                    "enteredContext == cachedContext",
                    "lookupCaller != null",
                    "cachedStructured != null"
                },
                assumptions = "cachedStructured.stability()",
                limit = "3")
        public static PreparedClosureCall guardedStructuredSend(
                LocalRangeAccessor frameBackedLocals,
                ProtosFrameLexicalLayout frameBackedLayout,
                Object receiver,
                String selector,
                PreparedInlineLiteralCall child,
                @Variadic Object[] supplied,
                @Bind BytecodeNode bytecodeNode,
                @Bind VirtualFrame frame,
                @Bind("currentEnteredContext($node)")
                        ProtosLanguageContext enteredContext,
                @Bind("child.provenanceCallerOrNull()") ProtosActivation lookupCaller,
                @Cached("receiver") Object cachedReceiver,
                @Cached("selector") String cachedSelector,
                @Cached("enteredContext") ProtosLanguageContext cachedContext,
                @Cached("createGuardedStructuredSend(receiver, selector, lookupCaller)")
                        GuardedStructuredSend cachedStructured) {
            return PrepareSendArguments.guardedStructuredSendForActivation(
                    receiver, selector,
                    ProtosInlineCallbackFrameBindings.durableActivation(
                            child, frameBackedLocals, frameBackedLayout, bytecodeNode, frame),
                    supplied, enteredContext,
                    cachedReceiver, cachedSelector, cachedContext, cachedStructured);
        }

        @Specialization(
                replaces = {
                    "guardedIntegerSend",
                    "guardedOrdinarySend",
                    "fastOrdinarySend",
                    "guardedStructuredSend"
                })
        public static PreparedClosureCall perform(
                LocalRangeAccessor frameBackedLocals,
                ProtosFrameLexicalLayout frameBackedLayout,
                Object receiver,
                String selector,
                PreparedInlineLiteralCall child,
                @Variadic Object[] supplied,
                @Bind BytecodeNode bytecodeNode,
                @Bind VirtualFrame frame) {
            return PrepareSendArguments.performForActivation(
                    receiver, selector,
                    ProtosInlineCallbackFrameBindings.durableActivation(
                            child, frameBackedLocals, frameBackedLayout, bytecodeNode, frame),
                    supplied);
        }

        static GuardedIntegerSend createGuardedIntegerSend(
                Object receiver,
                String selector,
                ProtosPrelude prelude,
                ProtosLanguageContext enteredContext) {
            return PrepareSendArguments.createGuardedIntegerSend(
                    receiver, selector, prelude, enteredContext);
        }

        static boolean isIntegerReceiver(Object receiver) {
            return PrepareSendArguments.isIntegerReceiver(receiver);
        }

        static GuardedSendTarget createGuardedSendForPrelude(
                Object receiver,
                String selector,
                ProtosPrelude prelude,
                ProtosLanguageContext enteredContext) {
            return PrepareSendArguments.createGuardedSendForPrelude(
                    receiver, selector, prelude, enteredContext);
        }

        static GuardedStructuredSend createGuardedStructuredSend(
                Object receiver, String selector, ProtosActivation caller) {
            return PrepareSendArguments.createGuardedStructuredSend(
                    receiver, selector, caller);
        }

        static ProtosSlotLookupResult ordinarySendSelectionOrNull(
                Object receiver, String selector, PreparedInlineLiteralCall child) {
            try {
                return ProtosValueLookup.lookup(receiver, selector, child.prelude())
                        .orElse(null);
            } catch (UnsupportedOperationException unsupportedRepresentation) {
                return null;
            }
        }

        static boolean isForeignReceiver(Object receiver) {
            return PrepareSendArguments.isForeignReceiver(receiver);
        }

        static ProtosClosureValue ordinarySendClosureOrNull(ProtosSlotLookupResult selected) {
            return selected == null
                    ? null
                    : PrepareSendArguments.ordinarySendClosureOrNull(
                            selected);
        }

        static CanonicalClosure ordinarySendClosureDefinitionOrNull(ProtosClosureValue closure) {
            return PrepareSendArguments.ordinarySendClosureDefinitionOrNull(
                    closure);
        }

        @NonIdempotent
        static ProtosLanguageContext currentEnteredContext(Node node) {
            return ProtosLanguageContext.current(node);
        }

        static RootCallTarget fastOrdinarySendTarget(
                ProtosClosureValue closure, ProtosLanguageContext enteredContext) {
            return PrepareSendArguments.fastOrdinarySendTarget(
                    closure, enteredContext);
        }
    }

    /**
     * PERF025 slice 3 inline-callback {@link PrepareClosureCall} and {@link
     * PrepareClosureCallArguments}: the canonical direct source-Closure tiers
     * select through the invocation's prelude and prepare the compact child
     * with a provenance-equivalent caller; every other selection runs the
     * unchanged activation path.
     */

    /* PERF038-H: fixed-arity inline sends. */
    @Operation
    @ConstantOperand(
            type = LocalRangeAccessor.class,
            name = "frameBackedLocals")
    @ConstantOperand(
            type = ProtosFrameLexicalLayout.class,
            name = "frameBackedLayout")
    public static final class PrepareInlineSendZero {
        @Specialization(
                guards = {
                    "isIntegerReceiver(receiver)",
                    "selector.equals(cachedSelector)",
                    "enteredContext != null",
                    "enteredContext == cachedContext",
                    "prelude == cachedPrelude",
                    "cachedInteger != null"
                },
                assumptions = "cachedInteger.stability()",
                limit = "3")
        public static PreparedClosureCall guardedIntegerSend(
                LocalRangeAccessor frameBackedLocals,
                ProtosFrameLexicalLayout frameBackedLayout,
                Object receiver,
                String selector,
                PreparedInlineLiteralCall child,

                @Bind BytecodeNode bytecodeNode,
                @Bind VirtualFrame frame,
                @Bind("currentEnteredContext($node)")
                        ProtosLanguageContext enteredContext,
                @Bind("child.prelude()") ProtosPrelude prelude,
                @Cached("selector") String cachedSelector,
                @Cached("enteredContext") ProtosLanguageContext cachedContext,
                @Cached("prelude") ProtosPrelude cachedPrelude,
                @Cached("createGuardedIntegerSend(receiver, selector, prelude, enteredContext)")
                        GuardedIntegerSend cachedInteger) {
            return PrepareSendArguments
                    .guardedIntegerSendZero(
                            receiver,
                            ProtosInlineCallbackFrameBindings.invocationCaller(
                    child, frameBackedLocals, frameBackedLayout,
                    bytecodeNode, frame),
                            cachedInteger);
        }

        @Specialization(
                guards = {
                    "receiver == cachedReceiver",
                    "selector.equals(cachedSelector)",
                    "enteredContext != null",
                    "enteredContext == cachedContext",
                    "cachedSend != null"
                },
                assumptions = "cachedSend.stability()",
                limit = "3")
        public static PreparedClosureCall guardedOrdinarySend(
                LocalRangeAccessor frameBackedLocals,
                ProtosFrameLexicalLayout frameBackedLayout,
                Object receiver,
                String selector,
                PreparedInlineLiteralCall child,

                @Bind BytecodeNode bytecodeNode,
                @Bind VirtualFrame frame,
                @Bind("currentEnteredContext($node)")
                        ProtosLanguageContext enteredContext,
                @Bind("child.prelude()") ProtosPrelude prelude,
                @Cached("receiver") Object cachedReceiver,
                @Cached("selector") String cachedSelector,
                @Cached("enteredContext") ProtosLanguageContext cachedContext,
                @Cached("createGuardedSendForPrelude(receiver, selector, prelude, enteredContext)")
                        GuardedSendTarget cachedSend) {
            return PrepareSendArguments
                    .guardedOrdinarySendZero(
                            receiver,
                            ProtosInlineCallbackFrameBindings.invocationCaller(
                    child, frameBackedLocals, frameBackedLayout,
                    bytecodeNode, frame),
                            cachedSend);
        }

        /*
         * Unlike PrepareSendArguments.fastOrdinarySend, a failing selection
         * binds null and misses here: its exact Error is raised by the
         * generic path from the materialized activation.
         */
        @Specialization(
                guards = {
                    "!isForeignReceiver(receiver)",
                    "closure != null",
                    "enteredContext != null",
                    "selector.equals(cachedSelector)",
                    "closureDefinition != null",
                    "closureDefinition == cachedClosureDefinition",
                    "enteredContext == cachedContext",
                    "cachedTarget != null"
                },
                limit = "3")
        public static PreparedClosureCall fastOrdinarySend(
                LocalRangeAccessor frameBackedLocals,
                ProtosFrameLexicalLayout frameBackedLayout,
                Object receiver,
                String selector,
                PreparedInlineLiteralCall child,

                @Bind BytecodeNode bytecodeNode,
                @Bind VirtualFrame frame,
                @Bind("ordinarySendSelectionOrNull(receiver, selector, child)")
                        ProtosSlotLookupResult selected,
                @Bind("ordinarySendClosureOrNull(selected)")
                        ProtosClosureValue closure,
                @Bind("ordinarySendClosureDefinitionOrNull(closure)")
                        CanonicalClosure closureDefinition,
                @Bind("currentEnteredContext($node)")
                        ProtosLanguageContext enteredContext,
                @Cached("selector") String cachedSelector,
                @Cached("closureDefinition") CanonicalClosure cachedClosureDefinition,
                @Cached("enteredContext") ProtosLanguageContext cachedContext,
                @Cached("fastOrdinarySendTarget(closure, enteredContext)")
                        RootCallTarget cachedTarget) {
            return PrepareSendArguments
                    .fastOrdinarySendZero(
                            closure,
                            receiver,
                            selected.home(),
                            ProtosInlineCallbackFrameBindings.invocationCaller(
                    child, frameBackedLocals, frameBackedLayout,
                    bytecodeNode, frame),
                            cachedTarget);
        }

        /*
         * Structured sends keep the materialized activation as their caller;
         * only their D013 classification, which reads the caller's prelude,
         * uses a provenance-equivalent caller.
         */
        @Specialization(
                guards = {
                    "receiver == cachedReceiver",
                    "selector.equals(cachedSelector)",
                    "enteredContext != null",
                    "enteredContext == cachedContext",
                    "lookupCaller != null",
                    "cachedStructured != null"
                },
                assumptions = "cachedStructured.stability()",
                limit = "3")
        public static PreparedClosureCall guardedStructuredSend(
                LocalRangeAccessor frameBackedLocals,
                ProtosFrameLexicalLayout frameBackedLayout,
                Object receiver,
                String selector,
                PreparedInlineLiteralCall child,

                @Bind BytecodeNode bytecodeNode,
                @Bind VirtualFrame frame,
                @Bind("currentEnteredContext($node)")
                        ProtosLanguageContext enteredContext,
                @Bind("child.provenanceCallerOrNull()") ProtosActivation lookupCaller,
                @Cached("receiver") Object cachedReceiver,
                @Cached("selector") String cachedSelector,
                @Cached("enteredContext") ProtosLanguageContext cachedContext,
                @Cached("createGuardedStructuredSend(receiver, selector, lookupCaller)")
                        GuardedStructuredSend cachedStructured) {
            return PrepareSendArguments
                    .guardedStructuredSendZero(
                            receiver,
                            ProtosInlineCallbackFrameBindings.durableActivation(
                    child, frameBackedLocals, frameBackedLayout,
                    bytecodeNode, frame),
                            cachedStructured);
        }

        @Specialization(
                replaces = {
                    "guardedIntegerSend",
                    "guardedOrdinarySend",
                    "fastOrdinarySend",
                    "guardedStructuredSend"
                })
        public static PreparedClosureCall perform(
                LocalRangeAccessor frameBackedLocals,
                ProtosFrameLexicalLayout frameBackedLayout,
                Object receiver,
                String selector,
                PreparedInlineLiteralCall child,

                @Bind BytecodeNode bytecodeNode,
                @Bind VirtualFrame frame) {
            return ProtosSendPreparation.prepareSendZeroFallback(
                    receiver,
                    selector,
                    ProtosInlineCallbackFrameBindings.durableActivation(
                            child, frameBackedLocals, frameBackedLayout,
                            bytecodeNode, frame));

        }

        static GuardedIntegerSend createGuardedIntegerSend(
                Object receiver,
                String selector,
                ProtosPrelude prelude,
                ProtosLanguageContext enteredContext) {
            return PrepareSendArguments.createGuardedIntegerSend(
                    receiver, selector, prelude, enteredContext);
        }

        static boolean isIntegerReceiver(Object receiver) {
            return PrepareSendArguments.isIntegerReceiver(receiver);
        }

        static GuardedSendTarget createGuardedSendForPrelude(
                Object receiver,
                String selector,
                ProtosPrelude prelude,
                ProtosLanguageContext enteredContext) {
            return PrepareSendArguments.createGuardedSendForPrelude(
                    receiver, selector, prelude, enteredContext);
        }

        static GuardedStructuredSend createGuardedStructuredSend(
                Object receiver, String selector, ProtosActivation caller) {
            return PrepareSendArguments.createGuardedStructuredSend(
                    receiver, selector, caller);
        }

        static ProtosSlotLookupResult ordinarySendSelectionOrNull(
                Object receiver, String selector, PreparedInlineLiteralCall child) {
            try {
                return ProtosValueLookup.lookup(receiver, selector, child.prelude())
                        .orElse(null);
            } catch (UnsupportedOperationException unsupportedRepresentation) {
                return null;
            }
        }

        static boolean isForeignReceiver(Object receiver) {
            return PrepareSendArguments.isForeignReceiver(receiver);
        }

        static ProtosClosureValue ordinarySendClosureOrNull(ProtosSlotLookupResult selected) {
            return selected == null
                    ? null
                    : PrepareSendArguments.ordinarySendClosureOrNull(
                            selected);
        }

        static CanonicalClosure ordinarySendClosureDefinitionOrNull(ProtosClosureValue closure) {
            return PrepareSendArguments.ordinarySendClosureDefinitionOrNull(
                    closure);
        }

        @NonIdempotent
        static ProtosLanguageContext currentEnteredContext(Node node) {
            return ProtosLanguageContext.current(node);
        }

        static RootCallTarget fastOrdinarySendTarget(
                ProtosClosureValue closure, ProtosLanguageContext enteredContext) {
            return PrepareSendArguments.fastOrdinarySendTarget(
                    closure, enteredContext);
        }
    }

    /**
     * PERF025 slice 3 inline-callback {@link PrepareClosureCall} and {@link
     * PrepareClosureCallArguments}: the canonical direct source-Closure tiers
     * select through the invocation's prelude and prepare the compact child
     * with a provenance-equivalent caller; every other selection runs the
     * unchanged activation path.
     */

    @Operation
    @ConstantOperand(
            type = LocalRangeAccessor.class,
            name = "frameBackedLocals")
    @ConstantOperand(
            type = ProtosFrameLexicalLayout.class,
            name = "frameBackedLayout")
    public static final class PrepareInlineSendOne {
        @Specialization(
                guards = {
                    "isIntegerReceiver(receiver)",
                    "selector.equals(cachedSelector)",
                    "enteredContext != null",
                    "enteredContext == cachedContext",
                    "prelude == cachedPrelude",
                    "cachedInteger != null"
                },
                assumptions = "cachedInteger.stability()",
                limit = "3")
        public static PreparedClosureCall guardedIntegerSend(
                LocalRangeAccessor frameBackedLocals,
                ProtosFrameLexicalLayout frameBackedLayout,
                Object receiver,
                String selector,
                PreparedInlineLiteralCall child,
                Object supplied0,
                @Bind BytecodeNode bytecodeNode,
                @Bind VirtualFrame frame,
                @Bind("currentEnteredContext($node)")
                        ProtosLanguageContext enteredContext,
                @Bind("child.prelude()") ProtosPrelude prelude,
                @Cached("selector") String cachedSelector,
                @Cached("enteredContext") ProtosLanguageContext cachedContext,
                @Cached("prelude") ProtosPrelude cachedPrelude,
                @Cached("createGuardedIntegerSend(receiver, selector, prelude, enteredContext)")
                        GuardedIntegerSend cachedInteger) {
            if (cachedInteger.operation() != null) {
                Object direct =
                        ProtosStandardIntegerProtocol.tryExecuteCanonicalOperationOne(
                                cachedInteger.operation(), receiver, supplied0, prelude);
                if (direct != null) {
                    return PreparedClosureCall.immediateResult(direct);
                }
            }

            return PrepareSendArguments
                    .guardedIntegerSendOne(
                            receiver,
                            ProtosInlineCallbackFrameBindings.invocationCaller(
                    child, frameBackedLocals, frameBackedLayout,
                    bytecodeNode, frame), supplied0,
                            cachedInteger);
        }

        @Specialization(
                guards = {
                    "receiver == cachedReceiver",
                    "selector.equals(cachedSelector)",
                    "enteredContext != null",
                    "enteredContext == cachedContext",
                    "cachedSend != null"
                },
                assumptions = "cachedSend.stability()",
                limit = "3")
        public static PreparedClosureCall guardedOrdinarySend(
                LocalRangeAccessor frameBackedLocals,
                ProtosFrameLexicalLayout frameBackedLayout,
                Object receiver,
                String selector,
                PreparedInlineLiteralCall child,
                Object supplied0,
                @Bind BytecodeNode bytecodeNode,
                @Bind VirtualFrame frame,
                @Bind("currentEnteredContext($node)")
                        ProtosLanguageContext enteredContext,
                @Bind("child.prelude()") ProtosPrelude prelude,
                @Cached("receiver") Object cachedReceiver,
                @Cached("selector") String cachedSelector,
                @Cached("enteredContext") ProtosLanguageContext cachedContext,
                @Cached("createGuardedSendForPrelude(receiver, selector, prelude, enteredContext)")
                        GuardedSendTarget cachedSend) {
            return PrepareSendArguments
                    .guardedOrdinarySendOne(
                            receiver,
                            ProtosInlineCallbackFrameBindings.invocationCaller(
                    child, frameBackedLocals, frameBackedLayout,
                    bytecodeNode, frame), supplied0,
                            cachedSend);
        }

        /*
         * Unlike PrepareSendArguments.fastOrdinarySend, a failing selection
         * binds null and misses here: its exact Error is raised by the
         * generic path from the materialized activation.
         */
        @Specialization(
                guards = {
                    "!isForeignReceiver(receiver)",
                    "closure != null",
                    "enteredContext != null",
                    "selector.equals(cachedSelector)",
                    "closureDefinition != null",
                    "closureDefinition == cachedClosureDefinition",
                    "enteredContext == cachedContext",
                    "cachedTarget != null"
                },
                limit = "3")
        public static PreparedClosureCall fastOrdinarySend(
                LocalRangeAccessor frameBackedLocals,
                ProtosFrameLexicalLayout frameBackedLayout,
                Object receiver,
                String selector,
                PreparedInlineLiteralCall child,
                Object supplied0,
                @Bind BytecodeNode bytecodeNode,
                @Bind VirtualFrame frame,
                @Bind("ordinarySendSelectionOrNull(receiver, selector, child)")
                        ProtosSlotLookupResult selected,
                @Bind("ordinarySendClosureOrNull(selected)")
                        ProtosClosureValue closure,
                @Bind("ordinarySendClosureDefinitionOrNull(closure)")
                        CanonicalClosure closureDefinition,
                @Bind("currentEnteredContext($node)")
                        ProtosLanguageContext enteredContext,
                @Cached("selector") String cachedSelector,
                @Cached("closureDefinition") CanonicalClosure cachedClosureDefinition,
                @Cached("enteredContext") ProtosLanguageContext cachedContext,
                @Cached("fastOrdinarySendTarget(closure, enteredContext)")
                        RootCallTarget cachedTarget) {
            return PrepareSendArguments
                    .fastOrdinarySendOne(
                            closure,
                            receiver,
                            selected.home(),
                            ProtosInlineCallbackFrameBindings.invocationCaller(
                    child, frameBackedLocals, frameBackedLayout,
                    bytecodeNode, frame),
                            cachedTarget, supplied0);
        }

        /*
         * Structured sends keep the materialized activation as their caller;
         * only their D013 classification, which reads the caller's prelude,
         * uses a provenance-equivalent caller.
         */
        @Specialization(
                guards = {
                    "receiver == cachedReceiver",
                    "selector.equals(cachedSelector)",
                    "enteredContext != null",
                    "enteredContext == cachedContext",
                    "lookupCaller != null",
                    "cachedStructured != null"
                },
                assumptions = "cachedStructured.stability()",
                limit = "3")
        public static PreparedClosureCall guardedStructuredSend(
                LocalRangeAccessor frameBackedLocals,
                ProtosFrameLexicalLayout frameBackedLayout,
                Object receiver,
                String selector,
                PreparedInlineLiteralCall child,
                Object supplied0,
                @Bind BytecodeNode bytecodeNode,
                @Bind VirtualFrame frame,
                @Bind("currentEnteredContext($node)")
                        ProtosLanguageContext enteredContext,
                @Bind("child.provenanceCallerOrNull()") ProtosActivation lookupCaller,
                @Cached("receiver") Object cachedReceiver,
                @Cached("selector") String cachedSelector,
                @Cached("enteredContext") ProtosLanguageContext cachedContext,
                @Cached("createGuardedStructuredSend(receiver, selector, lookupCaller)")
                        GuardedStructuredSend cachedStructured) {
            return PrepareSendArguments
                    .guardedStructuredSendOne(
                            receiver,
                            ProtosInlineCallbackFrameBindings.durableActivation(
                    child, frameBackedLocals, frameBackedLayout,
                    bytecodeNode, frame), supplied0,
                            cachedStructured);
        }

        @Specialization(
                replaces = {
                    "guardedIntegerSend",
                    "guardedOrdinarySend",
                    "fastOrdinarySend",
                    "guardedStructuredSend"
                })
        public static PreparedClosureCall perform(
                LocalRangeAccessor frameBackedLocals,
                ProtosFrameLexicalLayout frameBackedLayout,
                Object receiver,
                String selector,
                PreparedInlineLiteralCall child,
                Object supplied0,
                @Bind BytecodeNode bytecodeNode,
                @Bind VirtualFrame frame) {
            return ProtosSendPreparation.prepareSendOneFallback(
                    receiver,
                    selector,
                    ProtosInlineCallbackFrameBindings.durableActivation(
                            child, frameBackedLocals, frameBackedLayout,
                            bytecodeNode, frame),
                    supplied0);

        }

        static GuardedIntegerSend createGuardedIntegerSend(
                Object receiver,
                String selector,
                ProtosPrelude prelude,
                ProtosLanguageContext enteredContext) {
            return PrepareSendArguments.createGuardedIntegerSend(
                    receiver, selector, prelude, enteredContext);
        }

        static boolean isIntegerReceiver(Object receiver) {
            return PrepareSendArguments.isIntegerReceiver(receiver);
        }

        static GuardedSendTarget createGuardedSendForPrelude(
                Object receiver,
                String selector,
                ProtosPrelude prelude,
                ProtosLanguageContext enteredContext) {
            return PrepareSendArguments.createGuardedSendForPrelude(
                    receiver, selector, prelude, enteredContext);
        }

        static GuardedStructuredSend createGuardedStructuredSend(
                Object receiver, String selector, ProtosActivation caller) {
            return PrepareSendArguments.createGuardedStructuredSend(
                    receiver, selector, caller);
        }

        static ProtosSlotLookupResult ordinarySendSelectionOrNull(
                Object receiver, String selector, PreparedInlineLiteralCall child) {
            try {
                return ProtosValueLookup.lookup(receiver, selector, child.prelude())
                        .orElse(null);
            } catch (UnsupportedOperationException unsupportedRepresentation) {
                return null;
            }
        }

        static boolean isForeignReceiver(Object receiver) {
            return PrepareSendArguments.isForeignReceiver(receiver);
        }

        static ProtosClosureValue ordinarySendClosureOrNull(ProtosSlotLookupResult selected) {
            return selected == null
                    ? null
                    : PrepareSendArguments.ordinarySendClosureOrNull(
                            selected);
        }

        static CanonicalClosure ordinarySendClosureDefinitionOrNull(ProtosClosureValue closure) {
            return PrepareSendArguments.ordinarySendClosureDefinitionOrNull(
                    closure);
        }

        @NonIdempotent
        static ProtosLanguageContext currentEnteredContext(Node node) {
            return ProtosLanguageContext.current(node);
        }

        static RootCallTarget fastOrdinarySendTarget(
                ProtosClosureValue closure, ProtosLanguageContext enteredContext) {
            return PrepareSendArguments.fastOrdinarySendTarget(
                    closure, enteredContext);
        }
    }


    /**
     * I108: semantic Call0 inside an I096 frame-native callback.
     *
     * The PIC owns canonical call selection, target admission and
     * direct entry. An unadmitted call returns the existing prepared
     * invocation for Bytecode DSL continuation composition.
     *
     * The inline carrier is not materialized merely to select the
     * call or construct a PIC entry.
     */
    @Operation
    @ConstantOperand(
            type = LocalRangeAccessor.class,
            name = "frameBackedLocals")
    @ConstantOperand(
            type = ProtosFrameLexicalLayout.class,
            name = "frameBackedLayout")
    public static final class InvokeInlineClosureZero {
        @Specialization(
                excludeForUncached = true,
                guards = {
                    "definition != null",
                    "definition == cachedDefinition",
                    "plan != null",
                    "plan == cachedPlan",
                    "entered != null",
                    "entered == cachedContext",
                    "projection == cachedProjection",
                    "cachedTarget != null"
                },
                limit = "3")
        public static Object direct(
                LocalRangeAccessor frameBackedLocals,
                ProtosFrameLexicalLayout frameBackedLayout,
                Object receiver,
                PreparedInlineLiteralCall child,
                @Bind BytecodeNode bytecodeNode,
                @Bind VirtualFrame frame,
                @Bind("sourceDefinitionOrNull(receiver)")
                        CanonicalClosure definition,
                @Bind("sourcePlanOrNull(receiver)")
                        ProtosClosureExecutionPlan plan,
                @Bind("currentEnteredContext($node)")
                        ProtosLanguageContext entered,
                @Bind("requiresProjection(receiver)")
                        boolean projection,
                @Cached("definition")
                        CanonicalClosure cachedDefinition,
                @Cached("plan")
                        ProtosClosureExecutionPlan cachedPlan,
                @Cached("entered")
                        ProtosLanguageContext cachedContext,
                @Cached("projection")
                        boolean cachedProjection,
                @Cached("targetForSource(receiver, entered)")
                        RootCallTarget cachedTarget,
                @Cached("nonSuspending(cachedTarget)")
                        boolean cachedAdmitted,
                @Cached("createDirectCallNodeOrNull(cachedTarget)")
                        DirectCallNode node) {

            // Select D013 only after this PIC entry has been chosen.
            // Its exact methodHome is reused by the generic fallback.
            ProtosSlotLookupResult selectedCall =
                    selectCallOrNull(receiver, child);
            ProtosClosureValue closure =
                    selectedSourceOrNull(receiver, selectedCall);

            if (closure == null) {
                return fallback(frameBackedLocals, frameBackedLayout, receiver, child, selectedCall, bytecodeNode, frame);
            }

            /*
             * The target-level non-suspension proof is cached.
             * Current-instance selection and ReturnHome are not:
             * they must be rechecked on every execution.
             *
             * Even when direct entry is rejected, a canonical
             * source Closure retains its compact prepared path.
             */
            ProtosActivation caller =
                    ProtosInlineCallbackFrameBindings.invocationCaller(
                            child, frameBackedLocals,
                            frameBackedLayout, bytecodeNode, frame);

            if (cachedAdmitted
                    && closure.invocationReturnHomeForRuntime()
                            == ProtosReturnHome.unobservable()) {

                Object[] arguments =
                        ProtosFrameArguments
                                .compactDirectClosureCallZeroNoHome(
                                        closure, caller);

                try {
                    return ProtosNumericValueSupport.guestValue(
                            node.call(arguments));
                } catch (ProtosBytecodeControlTransferException bridged) {
                    throw bridged.transfer();
                }
            }

            /*
             * A suspendible target or observable ReturnHome must
             * continue through Bytecode composition. Reuse the
             * Context-owned cached target and the exact caller
             * without forcing durable activation when provenance
             * already provides an equivalent caller.
             */
            return ProtosClosureCallPreparation.finishDirectClosureCallZero(
                            closure, cachedTarget, caller);

        }

        @Specialization
        public static Object generic(
                LocalRangeAccessor frameBackedLocals,
                ProtosFrameLexicalLayout frameBackedLayout,
                Object receiver,
                PreparedInlineLiteralCall child,
                @Bind BytecodeNode bytecodeNode,
                @Bind VirtualFrame frame,
                @Bind("selectCallOrNull(receiver, child)")
                        ProtosSlotLookupResult selectedCall) {
            return fallback(frameBackedLocals, frameBackedLayout, receiver, child, selectedCall, bytecodeNode, frame);
        }

        static Object fallback(
                LocalRangeAccessor frameBackedLocals,
                ProtosFrameLexicalLayout frameBackedLayout,
                Object receiver,
                PreparedInlineLiteralCall child,
                ProtosSlotLookupResult selectedCall,
                BytecodeNode bytecodeNode,
                VirtualFrame frame) {
            return prepareSelectedFallback(
                    frameBackedLocals, frameBackedLayout,
                    receiver, child, selectedCall,
                    java.util.List.of(), bytecodeNode, frame);
        }

        @NonIdempotent
        static ProtosSlotLookupResult selectCallOrNull(
                Object receiver,
                PreparedInlineLiteralCall child) {
            ProtosPrelude prelude = child.prelude();

            if (prelude == null) {
                throw new IllegalStateException(
                        "polymorphic invocation requires an owning Core prelude");
            }

            try {
                return ProtosValueLookup.lookup(
                        receiver, "call", prelude).orElse(null);
            } catch (UnsupportedOperationException unsupported) {
                return null;
            }
        }

        static ProtosClosureValue selectedSourceOrNull(
                Object receiver,
                ProtosSlotLookupResult selection) {
            if (!(receiver instanceof ProtosClosureValue closure)
                    || closure.nativeBody().isPresent()
                    || selection == null
                    || !ProtosStandardObjectProtocol
                            .isCanonicalStandardCallSelection(
                                    selection.value(), selection.home())) {
                return null;
            }
            return closure;
        }

        static Object prepareSelectedFallback(
                LocalRangeAccessor frameBackedLocals,
                ProtosFrameLexicalLayout frameBackedLayout,
                Object receiver,
                PreparedInlineLiteralCall child,
                ProtosSlotLookupResult selection,
                java.util.List<?> supplied,
                BytecodeNode bytecodeNode,
                VirtualFrame frame) {
            ProtosActivation caller =
                    ProtosInlineCallbackFrameBindings.durableActivation(
                            child, frameBackedLocals,
                            frameBackedLayout, bytecodeNode, frame);

            if (selection == null
                    || !(selection.value() instanceof ProtosClosureValue)) {
                throw new com.guillermomolina.protos.runtime.ProtosSignalException(
                        com.guillermomolina.protos.runtime.ProtosCoreErrors
                                .newError(caller));
            }

            return ProtosClosureCallPreparation.prepareSelectedClosureCall(
                    receiver, supplied, caller, selection);
        }

        static CanonicalClosure sourceDefinitionOrNull(
                Object receiver) {
            return InvokeClosureZero.sourceDefinitionOrNull(receiver);
        }

        static ProtosClosureExecutionPlan sourcePlanOrNull(
                Object receiver) {
            return InvokeClosureZero.sourcePlanOrNull(receiver);
        }

        static boolean requiresProjection(Object receiver) {
            return InvokeClosureZero.requiresContextProjection(receiver);
        }

        static RootCallTarget targetForSelected(
                ProtosClosureValue selected,
                ProtosLanguageContext entered) {
            if (entered == null
                    || selected == null
                    || selected.executionPlan().isEmpty()) {
                return null;
            }

            return PrepareSendArguments
                    .fastOrdinarySendTarget(selected, entered);
        }

        static RootCallTarget targetForSource(
                Object receiver,
                ProtosLanguageContext entered) {
            if (!(receiver instanceof ProtosClosureValue closure)
                    || closure.nativeBody().isPresent()) {
                return null;
            }
            return targetForSelected(closure, entered);
        }

        static boolean nonSuspending(RootCallTarget target) {
            return InvokeClosureZero.admittedTarget(target, 0);
        }

        static DirectCallNode createDirectCallNodeOrNull(
                RootCallTarget target) {
            return InvokeClosureZero
                    .createDirectCallNodeOrNull(target);
        }


        @NonIdempotent
        static ProtosLanguageContext currentEnteredContext(
                Node node) {
            return ProtosLanguageContext.current(node);
        }
    }

    /**
     * PERF025 slice 3 inline-callback {@link PrepareClosureCall} and {@link
     * PrepareClosureCallArguments}: the canonical direct source-Closure tiers
     * select through the invocation's prelude and prepare the compact child
     * with a provenance-equivalent caller; every other selection runs the
     * unchanged activation path.
     */
    @Operation
    @ConstantOperand(
            type = LocalRangeAccessor.class,
            name = "frameBackedLocals")
    @ConstantOperand(
            type = ProtosFrameLexicalLayout.class,
            name = "frameBackedLayout")
    public static final class PrepareInlineClosureCall {
        @Specialization(
                guards = {
                    "receiver == cachedReceiver",
                    "enteredContext != null",
                    "enteredContext == cachedContext",
                    "cachedGuarded != null"
                },
                assumptions = "cachedGuarded.stability()",
                limit = "3")
        public static PreparedClosureCall guardedDirect(
                LocalRangeAccessor frameBackedLocals,
                ProtosFrameLexicalLayout frameBackedLayout,
                Object receiver,
                PreparedInlineLiteralCall child,
                @Bind BytecodeNode bytecodeNode,
                @Bind VirtualFrame frame,
                @Bind("currentEnteredContext($node)")
                        ProtosLanguageContext enteredContext,
                @Bind("child.prelude()") ProtosPrelude prelude,
                @Cached("receiver") Object cachedReceiver,
                @Cached("enteredContext") ProtosLanguageContext cachedContext,
                @Cached("createGuardedDirectClosureCall(receiver, prelude, enteredContext)")
                        GuardedDirectClosureCallTarget cachedGuarded) {
            return PrepareClosureCall.guardedDirect(
                    receiver,
                    ProtosInlineCallbackFrameBindings.invocationCaller(
                            child, frameBackedLocals, frameBackedLayout, bytecodeNode, frame),
                    enteredContext, cachedReceiver, cachedContext, cachedGuarded);
        }

        @Specialization(
                guards = {
                    "closure != null",
                    "enteredContext != null",
                    "closureDefinition != null",
                    "closureDefinition == cachedClosureDefinition",
                    "enteredContext == cachedContext",
                    "cachedTarget != null"
                },
                limit = "3")
        public static PreparedClosureCall fastDirect(
                LocalRangeAccessor frameBackedLocals,
                ProtosFrameLexicalLayout frameBackedLayout,
                Object receiver,
                PreparedInlineLiteralCall child,
                @Bind BytecodeNode bytecodeNode,
                @Bind VirtualFrame frame,
                @Bind("directClosureCallSelectionOrNull(receiver, child)")
                        ProtosClosureValue closure,
                @Bind("directClosureCallDefinitionOrNull(closure)")
                        CanonicalClosure closureDefinition,
                @Bind("currentEnteredContext($node)")
                        ProtosLanguageContext enteredContext,
                @Cached("closureDefinition") CanonicalClosure cachedClosureDefinition,
                @Cached("enteredContext") ProtosLanguageContext cachedContext,
                @Cached("fastOrdinarySendTarget(closure, enteredContext)")
                        RootCallTarget cachedTarget) {
            return PrepareClosureCall.fastDirect(
                    receiver,
                    ProtosInlineCallbackFrameBindings.invocationCaller(
                            child, frameBackedLocals, frameBackedLayout, bytecodeNode, frame),
                    closure, closureDefinition, enteredContext,
                    cachedClosureDefinition, cachedContext, cachedTarget);
        }

        @Specialization(replaces = {"guardedDirect", "fastDirect"})
        public static PreparedClosureCall perform(
                LocalRangeAccessor frameBackedLocals,
                ProtosFrameLexicalLayout frameBackedLayout,
                Object receiver,
                PreparedInlineLiteralCall child,
                @Bind BytecodeNode bytecodeNode,
                @Bind VirtualFrame frame) {
            return PrepareClosureCall.perform(
                    receiver,
                    ProtosInlineCallbackFrameBindings.durableActivation(
                            child, frameBackedLocals, frameBackedLayout, bytecodeNode, frame));
        }

        @NonIdempotent
        static ProtosLanguageContext currentEnteredContext(Node node) {
            return ProtosLanguageContext.current(node);
        }

        static GuardedDirectClosureCallTarget createGuardedDirectClosureCall(
                Object receiver, ProtosPrelude prelude, ProtosLanguageContext enteredContext) {
            return ProtosClosureCallPreparation.createGuardedDirectClosureCallForPrelude(
                    receiver, prelude, enteredContext);
        }

        static ProtosClosureValue directClosureCallSelectionOrNull(
                Object receiver, PreparedInlineLiteralCall child) {
            return ProtosClosureCallPreparation.directClosureCallSelectionForPreludeOrNull(
                    receiver, child.prelude());
        }

        static CanonicalClosure directClosureCallDefinitionOrNull(ProtosClosureValue closure) {
            return ProtosClosureCallPreparation.directClosureCallDefinitionOrNull(closure);
        }

        static RootCallTarget fastOrdinarySendTarget(
                ProtosClosureValue closure, ProtosLanguageContext enteredContext) {
            return PrepareSendArguments.fastOrdinarySendTarget(
                    closure, enteredContext);
        }
    }

    @Operation
    @ConstantOperand(
            type = LocalRangeAccessor.class,
            name = "frameBackedLocals")
    @ConstantOperand(
            type = ProtosFrameLexicalLayout.class,
            name = "frameBackedLayout")
    public static final class PrepareInlineClosureCallArguments {
        @Specialization(
                guards = {
                    "receiver == cachedReceiver",
                    "enteredContext != null",
                    "enteredContext == cachedContext",
                    "cachedGuarded != null"
                },
                assumptions = "cachedGuarded.stability()",
                limit = "3")
        public static PreparedClosureCall guardedDirect(
                LocalRangeAccessor frameBackedLocals,
                ProtosFrameLexicalLayout frameBackedLayout,
                Object receiver,
                PreparedInlineLiteralCall child,
                @Variadic Object[] supplied,
                @Bind BytecodeNode bytecodeNode,
                @Bind VirtualFrame frame,
                @Bind("currentEnteredContext($node)")
                        ProtosLanguageContext enteredContext,
                @Bind("child.prelude()") ProtosPrelude prelude,
                @Cached("receiver") Object cachedReceiver,
                @Cached("enteredContext") ProtosLanguageContext cachedContext,
                @Cached("createGuardedDirectClosureCall(receiver, prelude, enteredContext)")
                        GuardedDirectClosureCallTarget cachedGuarded) {
            return PrepareClosureCallArguments.guardedDirect(
                    receiver,
                    ProtosInlineCallbackFrameBindings.invocationCaller(
                            child, frameBackedLocals, frameBackedLayout, bytecodeNode, frame),
                    supplied, enteredContext, cachedReceiver, cachedContext, cachedGuarded);
        }

        @Specialization(
                guards = {
                    "closure != null",
                    "enteredContext != null",
                    "closureDefinition != null",
                    "closureDefinition == cachedClosureDefinition",
                    "enteredContext == cachedContext",
                    "cachedTarget != null"
                },
                limit = "3")
        public static PreparedClosureCall fastDirect(
                LocalRangeAccessor frameBackedLocals,
                ProtosFrameLexicalLayout frameBackedLayout,
                Object receiver,
                PreparedInlineLiteralCall child,
                @Variadic Object[] supplied,
                @Bind BytecodeNode bytecodeNode,
                @Bind VirtualFrame frame,
                @Bind("directClosureCallSelectionOrNull(receiver, child)")
                        ProtosClosureValue closure,
                @Bind("directClosureCallDefinitionOrNull(closure)")
                        CanonicalClosure closureDefinition,
                @Bind("currentEnteredContext($node)")
                        ProtosLanguageContext enteredContext,
                @Cached("closureDefinition") CanonicalClosure cachedClosureDefinition,
                @Cached("enteredContext") ProtosLanguageContext cachedContext,
                @Cached("fastOrdinarySendTarget(closure, enteredContext)")
                        RootCallTarget cachedTarget) {
            return PrepareClosureCallArguments.fastDirect(
                    receiver,
                    ProtosInlineCallbackFrameBindings.invocationCaller(
                            child, frameBackedLocals, frameBackedLayout, bytecodeNode, frame),
                    supplied, closure, closureDefinition, enteredContext,
                    cachedClosureDefinition, cachedContext, cachedTarget);
        }

        @Specialization(replaces = {"guardedDirect", "fastDirect"})
        public static PreparedClosureCall perform(
                LocalRangeAccessor frameBackedLocals,
                ProtosFrameLexicalLayout frameBackedLayout,
                Object receiver,
                PreparedInlineLiteralCall child,
                @Variadic Object[] supplied,
                @Bind BytecodeNode bytecodeNode,
                @Bind VirtualFrame frame) {
            return PrepareClosureCallArguments.perform(
                    receiver,
                    ProtosInlineCallbackFrameBindings.durableActivation(
                            child, frameBackedLocals, frameBackedLayout, bytecodeNode, frame),
                    supplied);
        }

        @NonIdempotent
        static ProtosLanguageContext currentEnteredContext(Node node) {
            return ProtosLanguageContext.current(node);
        }

        static GuardedDirectClosureCallTarget createGuardedDirectClosureCall(
                Object receiver, ProtosPrelude prelude, ProtosLanguageContext enteredContext) {
            return ProtosClosureCallPreparation.createGuardedDirectClosureCallForPrelude(
                    receiver, prelude, enteredContext);
        }

        static ProtosClosureValue directClosureCallSelectionOrNull(
                Object receiver, PreparedInlineLiteralCall child) {
            return ProtosClosureCallPreparation.directClosureCallSelectionForPreludeOrNull(
                    receiver, child.prelude());
        }

        static CanonicalClosure directClosureCallDefinitionOrNull(ProtosClosureValue closure) {
            return ProtosClosureCallPreparation.directClosureCallDefinitionOrNull(closure);
        }

        static RootCallTarget fastOrdinarySendTarget(
                ProtosClosureValue closure, ProtosLanguageContext enteredContext) {
            return PrepareSendArguments.fastOrdinarySendTarget(
                    closure, enteredContext);
        }
    }



    /**
     * I108: semantic InvokeInlineClosureOne in a frame-native inline callback.
     *
     * The definition-keyed PIC owns the D013 source selection,
     * entered-Context target, direct admission, and prepared result.
     * The actual instance selection and ReturnHome are checked
     * on every hit. The inline activation is never materialized
     * unconditionally before the operation.
     */
    @Operation
    @ConstantOperand(
            type = LocalRangeAccessor.class,
            name = "frameBackedLocals")
    @ConstantOperand(
            type = ProtosFrameLexicalLayout.class,
            name = "frameBackedLayout")
    public static final class InvokeInlineClosureOne {
        @Specialization(
                excludeForUncached = true,
                guards = {
                    "definition != null",
                    "definition == cachedDefinition",
                    "plan != null",
                    "plan == cachedPlan",
                    "entered != null",
                    "entered == cachedContext",
                    "projection == cachedProjection",
                    "cachedTarget != null"
                },
                limit = "3")
        public static Object source(
                LocalRangeAccessor frameBackedLocals,
                ProtosFrameLexicalLayout frameBackedLayout,
                Object receiver,
                PreparedInlineLiteralCall child,
                Object supplied0,
                @Bind BytecodeNode bytecodeNode,
                @Bind VirtualFrame frame,
                @Bind("sourceDefinitionOrNull(receiver)")
                        CanonicalClosure definition,
                @Bind("sourcePlanOrNull(receiver)")
                        ProtosClosureExecutionPlan plan,
                @Bind("currentEnteredContext($node)")
                        ProtosLanguageContext entered,
                @Bind("requiresProjection(receiver)")
                        boolean projection,
                @Cached("definition")
                        CanonicalClosure cachedDefinition,
                @Cached("plan")
                        ProtosClosureExecutionPlan cachedPlan,
                @Cached("entered")
                        ProtosLanguageContext cachedContext,
                @Cached("projection")
                        boolean cachedProjection,
                @Cached("targetForSource(receiver, entered)")
                        RootCallTarget cachedTarget,
                @Cached("nonSuspending(cachedTarget)")
                        boolean cachedNonSuspending,
                @Cached("createDirectCallNodeOrNull(cachedTarget)")
                        DirectCallNode node) {

            // Select D013 only after this PIC entry has been chosen.
            // Its exact methodHome is reused by the generic fallback.
            ProtosSlotLookupResult selectedCall =
                    selectCallOrNull(receiver, child);
            ProtosClosureValue closure =
                    selectedSourceOrNull(receiver, selectedCall);

            if (closure == null) {
                return fallback(frameBackedLocals, frameBackedLayout, receiver, child, selectedCall, supplied0, bytecodeNode, frame);
            }

            /*
             * This method is evaluated only after receiver and all
             * supplied arguments have been staged. The provenance
             * projection avoids transferring ephemeral bindings
             * where the original compact caller is equivalent.
             */
            ProtosActivation caller =
                    ProtosInlineCallbackFrameBindings.invocationCaller(
                            child, frameBackedLocals,
                            frameBackedLayout, bytecodeNode, frame);

            if (cachedNonSuspending
                    && closure.invocationReturnHomeForRuntime()
                            == ProtosReturnHome.unobservable()) {

                Object[] frameArguments = ProtosFrameArguments.compactDirectClosureCallOneNoHome(closure, caller, supplied0);

                try {
                    return ProtosNumericValueSupport.guestValue(
                            node.call(frameArguments));
                } catch (ProtosBytecodeControlTransferException bridged) {
                    throw bridged.transfer();
                }
            }

            /*
             * A potentially suspending target or observable
             * ReturnHome must be composed by Bytecode DSL.
             * Preserve the compact prepared target and exact home.
             */
            return ProtosClosureCallPreparation.finishDirectClosureCallOne(closure, cachedTarget, supplied0, caller);
        }

        @Specialization
        public static Object generic(
                LocalRangeAccessor frameBackedLocals,
                ProtosFrameLexicalLayout frameBackedLayout,
                Object receiver,
                PreparedInlineLiteralCall child,
                Object supplied0,
                @Bind BytecodeNode bytecodeNode,
                @Bind VirtualFrame frame,
                @Bind("selectCallOrNull(receiver, child)")
                        ProtosSlotLookupResult selectedCall) {
            return fallback(frameBackedLocals, frameBackedLayout, receiver, child, selectedCall, supplied0, bytecodeNode, frame);
        }

        static Object fallback(
                LocalRangeAccessor frameBackedLocals,
                ProtosFrameLexicalLayout frameBackedLayout,
                Object receiver,
                PreparedInlineLiteralCall child,
                ProtosSlotLookupResult selectedCall,
                Object supplied0,
                BytecodeNode bytecodeNode,
                VirtualFrame frame) {
            return InvokeInlineClosureZero.prepareSelectedFallback(
                    frameBackedLocals, frameBackedLayout,
                    receiver, child, selectedCall,
                    java.util.List.of(supplied0), bytecodeNode, frame);
        }


        static ProtosSlotLookupResult selectCallOrNull(
                Object receiver,
                PreparedInlineLiteralCall child) {
            return InvokeInlineClosureZero
                    .selectCallOrNull(receiver, child);
        }

        static ProtosClosureValue selectedSourceOrNull(
                Object receiver,
                ProtosSlotLookupResult selection) {
            return InvokeInlineClosureZero
                    .selectedSourceOrNull(receiver, selection);
        }

        static CanonicalClosure sourceDefinitionOrNull(
                Object receiver) {
            return InvokeInlineClosureZero
                    .sourceDefinitionOrNull(receiver);
        }

        static ProtosClosureExecutionPlan sourcePlanOrNull(
                Object receiver) {
            return InvokeInlineClosureZero
                    .sourcePlanOrNull(receiver);
        }

        static boolean requiresProjection(Object receiver) {
            return InvokeInlineClosureZero
                    .requiresProjection(receiver);
        }

        static RootCallTarget targetForSelected(
                ProtosClosureValue selected,
                ProtosLanguageContext entered) {
            return InvokeInlineClosureZero
                    .targetForSelected(selected, entered);
        }

        static RootCallTarget targetForSource(
                Object receiver,
                ProtosLanguageContext entered) {
            return InvokeInlineClosureZero.targetForSource(receiver, entered);
        }

        static boolean nonSuspending(RootCallTarget target) {
            return InvokeInlineClosureZero.nonSuspending(target);
        }

        static DirectCallNode createDirectCallNodeOrNull(
                RootCallTarget target) {
            return InvokeInlineClosureZero
                    .createDirectCallNodeOrNull(target);
        }


        @NonIdempotent
        static ProtosLanguageContext currentEnteredContext(
                Node node) {
            return ProtosLanguageContext.current(node);
        }
    }


    /**
     * I108: semantic InvokeInlineClosureArguments in a frame-native inline callback.
     *
     * The definition-keyed PIC owns the D013 source selection,
     * entered-Context target, direct admission, and prepared result.
     * The actual instance selection and ReturnHome are checked
     * on every hit. The inline activation is never materialized
     * unconditionally before the operation.
     */
    @Operation
    @ConstantOperand(
            type = LocalRangeAccessor.class,
            name = "frameBackedLocals")
    @ConstantOperand(
            type = ProtosFrameLexicalLayout.class,
            name = "frameBackedLayout")
    public static final class InvokeInlineClosureArguments {
        @Specialization(
                excludeForUncached = true,
                guards = {
                    "definition != null",
                    "definition == cachedDefinition",
                    "plan != null",
                    "plan == cachedPlan",
                    "entered != null",
                    "entered == cachedContext",
                    "projection == cachedProjection",
                    "cachedTarget != null"
                },
                limit = "3")
        public static Object source(
                LocalRangeAccessor frameBackedLocals,
                ProtosFrameLexicalLayout frameBackedLayout,
                Object receiver,
                PreparedInlineLiteralCall child,
                Object[] supplied,
                @Bind BytecodeNode bytecodeNode,
                @Bind VirtualFrame frame,
                @Bind("sourceDefinitionOrNull(receiver)")
                        CanonicalClosure definition,
                @Bind("sourcePlanOrNull(receiver)")
                        ProtosClosureExecutionPlan plan,
                @Bind("currentEnteredContext($node)")
                        ProtosLanguageContext entered,
                @Bind("requiresProjection(receiver)")
                        boolean projection,
                @Cached("definition")
                        CanonicalClosure cachedDefinition,
                @Cached("plan")
                        ProtosClosureExecutionPlan cachedPlan,
                @Cached("entered")
                        ProtosLanguageContext cachedContext,
                @Cached("projection")
                        boolean cachedProjection,
                @Cached("targetForSource(receiver, entered)")
                        RootCallTarget cachedTarget,
                @Cached("nonSuspending(cachedTarget)")
                        boolean cachedNonSuspending,
                @Cached("createDirectCallNodeOrNull(cachedTarget)")
                        DirectCallNode node) {

            // Select D013 only after this PIC entry has been chosen.
            // Its exact methodHome is reused by the generic fallback.
            ProtosSlotLookupResult selectedCall =
                    selectCallOrNull(receiver, child);
            ProtosClosureValue closure =
                    selectedSourceOrNull(receiver, selectedCall);

            if (closure == null) {
                return fallback(frameBackedLocals, frameBackedLayout, receiver, child, selectedCall, supplied, bytecodeNode, frame);
            }

            /*
             * This method is evaluated only after receiver and all
             * supplied arguments have been staged. The provenance
             * projection avoids transferring ephemeral bindings
             * where the original compact caller is equivalent.
             */
            ProtosActivation caller =
                    ProtosInlineCallbackFrameBindings.invocationCaller(
                            child, frameBackedLocals,
                            frameBackedLayout, bytecodeNode, frame);

            if (cachedNonSuspending
                    && closure.invocationReturnHomeForRuntime()
                            == ProtosReturnHome.unobservable()) {

                Object[] frameArguments = ProtosFrameArguments.compactDirectClosureCall(closure, caller, null, supplied);

                try {
                    return ProtosNumericValueSupport.guestValue(
                            node.call(frameArguments));
                } catch (ProtosBytecodeControlTransferException bridged) {
                    throw bridged.transfer();
                }
            }

            /*
             * A potentially suspending target or observable
             * ReturnHome must be composed by Bytecode DSL.
             * Preserve the compact prepared target and exact home.
             */
            return ProtosClosureCallPreparation.finishDirectClosureCall(closure, cachedTarget, supplied, caller);
        }

        @Specialization
        public static Object generic(
                LocalRangeAccessor frameBackedLocals,
                ProtosFrameLexicalLayout frameBackedLayout,
                Object receiver,
                PreparedInlineLiteralCall child,
                Object[] supplied,
                @Bind BytecodeNode bytecodeNode,
                @Bind VirtualFrame frame,
                @Bind("selectCallOrNull(receiver, child)")
                        ProtosSlotLookupResult selectedCall) {
            return fallback(frameBackedLocals, frameBackedLayout, receiver, child, selectedCall, supplied, bytecodeNode, frame);
        }

        static Object fallback(
                LocalRangeAccessor frameBackedLocals,
                ProtosFrameLexicalLayout frameBackedLayout,
                Object receiver,
                PreparedInlineLiteralCall child,
                ProtosSlotLookupResult selectedCall,
                Object[] supplied,
                BytecodeNode bytecodeNode,
                VirtualFrame frame) {
            return InvokeInlineClosureZero.prepareSelectedFallback(
                    frameBackedLocals, frameBackedLayout,
                    receiver, child, selectedCall,
                    java.util.Arrays.asList(supplied), bytecodeNode, frame);
        }


        static ProtosSlotLookupResult selectCallOrNull(
                Object receiver,
                PreparedInlineLiteralCall child) {
            return InvokeInlineClosureZero
                    .selectCallOrNull(receiver, child);
        }

        static ProtosClosureValue selectedSourceOrNull(
                Object receiver,
                ProtosSlotLookupResult selection) {
            return InvokeInlineClosureZero
                    .selectedSourceOrNull(receiver, selection);
        }

        static CanonicalClosure sourceDefinitionOrNull(
                Object receiver) {
            return InvokeInlineClosureZero
                    .sourceDefinitionOrNull(receiver);
        }

        static ProtosClosureExecutionPlan sourcePlanOrNull(
                Object receiver) {
            return InvokeInlineClosureZero
                    .sourcePlanOrNull(receiver);
        }

        static boolean requiresProjection(Object receiver) {
            return InvokeInlineClosureZero
                    .requiresProjection(receiver);
        }

        static RootCallTarget targetForSelected(
                ProtosClosureValue selected,
                ProtosLanguageContext entered) {
            return InvokeInlineClosureZero
                    .targetForSelected(selected, entered);
        }

        static RootCallTarget targetForSource(
                Object receiver,
                ProtosLanguageContext entered) {
            return InvokeInlineClosureZero.targetForSource(receiver, entered);
        }

        static boolean nonSuspending(RootCallTarget target) {
            return InvokeInlineClosureZero.nonSuspending(target);
        }

        static DirectCallNode createDirectCallNodeOrNull(
                RootCallTarget target) {
            return InvokeInlineClosureZero
                    .createDirectCallNodeOrNull(target);
        }


        @NonIdempotent
        static ProtosLanguageContext currentEnteredContext(
                Node node) {
            return ProtosLanguageContext.current(node);
        }
    }

    /* PERF038-H: dedicated single-argument Closure call. */
    @Operation
    @ConstantOperand(
            type = LocalRangeAccessor.class,
            name = "frameBackedLocals")
    @ConstantOperand(
            type = ProtosFrameLexicalLayout.class,
            name = "frameBackedLayout")
    public static final class PrepareInlineClosureCallOne {
        @Specialization(
                guards = {
                    "receiver == cachedReceiver",
                    "enteredContext != null",
                    "enteredContext == cachedContext",
                    "cachedGuarded != null"
                },
                assumptions = "cachedGuarded.stability()",
                limit = "3")
        public static PreparedClosureCall guardedDirect(
                LocalRangeAccessor frameBackedLocals,
                ProtosFrameLexicalLayout frameBackedLayout,
                Object receiver,
                PreparedInlineLiteralCall child,
                Object supplied0,
                @Bind BytecodeNode bytecodeNode,
                @Bind VirtualFrame frame,
                @Bind("currentEnteredContext($node)")
                        ProtosLanguageContext enteredContext,
                @Bind("child.prelude()") ProtosPrelude prelude,
                @Cached("receiver") Object cachedReceiver,
                @Cached("enteredContext") ProtosLanguageContext cachedContext,
                @Cached("createGuardedDirectClosureCall(receiver, prelude, enteredContext)")
                        GuardedDirectClosureCallTarget cachedGuarded) {

            return ProtosClosureCallPreparation.finishDirectClosureCallOne(
                    cachedGuarded.closure(),
                    cachedGuarded.target(),
                    supplied0,
                    ProtosInlineCallbackFrameBindings.invocationCaller(
                            child, frameBackedLocals,
                            frameBackedLayout, bytecodeNode, frame));
        }

        @Specialization(
                guards = {
                    "closure != null",
                    "enteredContext != null",
                    "closureDefinition != null",
                    "closureDefinition == cachedClosureDefinition",
                    "enteredContext == cachedContext",
                    "cachedTarget != null"
                },
                limit = "3")
        public static PreparedClosureCall fastDirect(
                LocalRangeAccessor frameBackedLocals,
                ProtosFrameLexicalLayout frameBackedLayout,
                Object receiver,
                PreparedInlineLiteralCall child,
                Object supplied0,
                @Bind BytecodeNode bytecodeNode,
                @Bind VirtualFrame frame,
                @Bind("directClosureCallSelectionOrNull(receiver, child)")
                        ProtosClosureValue closure,
                @Bind("directClosureCallDefinitionOrNull(closure)")
                        CanonicalClosure closureDefinition,
                @Bind("currentEnteredContext($node)")
                        ProtosLanguageContext enteredContext,
                @Cached("closureDefinition") CanonicalClosure cachedClosureDefinition,
                @Cached("enteredContext") ProtosLanguageContext cachedContext,
                @Cached("fastOrdinarySendTarget(closure, enteredContext)")
                        RootCallTarget cachedTarget) {

            return ProtosClosureCallPreparation.finishDirectClosureCallOne(
                    closure,
                    cachedTarget,
                    supplied0,
                    ProtosInlineCallbackFrameBindings.invocationCaller(
                            child, frameBackedLocals,
                            frameBackedLayout, bytecodeNode, frame));
        }

        @Specialization(replaces = {"guardedDirect", "fastDirect"})
        public static PreparedClosureCall perform(
                LocalRangeAccessor frameBackedLocals,
                ProtosFrameLexicalLayout frameBackedLayout,
                Object receiver,
                PreparedInlineLiteralCall child,
                Object supplied0,
                @Bind BytecodeNode bytecodeNode,
                @Bind VirtualFrame frame) {
            return ProtosClosureCallPreparation.prepareClosureCallOneFallback(
                    receiver,
                    ProtosInlineCallbackFrameBindings.durableActivation(
                            child, frameBackedLocals, frameBackedLayout,
                            bytecodeNode, frame),
                    supplied0);

        }

        @NonIdempotent
        static ProtosLanguageContext currentEnteredContext(Node node) {
            return ProtosLanguageContext.current(node);
        }

        static GuardedDirectClosureCallTarget createGuardedDirectClosureCall(
                Object receiver, ProtosPrelude prelude, ProtosLanguageContext enteredContext) {
            return ProtosClosureCallPreparation.createGuardedDirectClosureCallForPrelude(
                    receiver, prelude, enteredContext);
        }

        static ProtosClosureValue directClosureCallSelectionOrNull(
                Object receiver, PreparedInlineLiteralCall child) {
            return ProtosClosureCallPreparation.directClosureCallSelectionForPreludeOrNull(
                    receiver, child.prelude());
        }

        static CanonicalClosure directClosureCallDefinitionOrNull(ProtosClosureValue closure) {
            return ProtosClosureCallPreparation.directClosureCallDefinitionOrNull(closure);
        }

        static RootCallTarget fastOrdinarySendTarget(
                ProtosClosureValue closure, ProtosLanguageContext enteredContext) {
            return PrepareSendArguments.fastOrdinarySendTarget(
                    closure, enteredContext);
        }
    }


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
     * I108-B: semantic invocation for CallN and spread.
     *
     * Selection is performed after argument effects. The admitted path
     * executes the existing B5 entry; every other case produces the
     * existing prepared invocation for structural composition.
     */
    @Operation
    public static final class InvokeClosureArguments {
        @Specialization(
                excludeForUncached = true,
                guards = {
                    "definition != null",
                    "definition == cachedDefinition",
                    "plan != null",
                    "plan == cachedPlan",
                    "entered != null",
                    "entered == cachedContext",
                    "projection == cachedProjection",
                    "cachedTarget != null",
                    "cachedAdmitted"
                },
                limit = "3")
        public static Object direct(
                Object receiver,
                ProtosActivation caller,
                Object[] supplied,
                @Bind("sourceDefinitionOrNull(receiver)")
                        CanonicalClosure definition,
                @Bind("sourcePlanOrNull(receiver)")
                        ProtosClosureExecutionPlan plan,
                @Bind("currentEnteredContext($node)")
                        ProtosLanguageContext entered,
                @Bind("requiresProjection(receiver)")
                        boolean projection,
                @Cached("definition")
                        CanonicalClosure cachedDefinition,
                @Cached("plan")
                        ProtosClosureExecutionPlan cachedPlan,
                @Cached("entered")
                        ProtosLanguageContext cachedContext,
                @Cached("projection")
                        boolean cachedProjection,
                @Cached("targetFor(receiver, caller, entered)")
                        RootCallTarget cachedTarget,
                @Cached("nonSuspending(cachedTarget)")
                        boolean cachedAdmitted,
                @Cached("createDirectCallNodeOrNull(cachedTarget)")
                        DirectCallNode node) {

            ProtosClosureValue closure = admitted(receiver, caller);

            if (closure == null) {
                return fallback(receiver, caller, supplied);
            }

            Object[] frameArguments =
                    ProtosFrameArguments.compactDirectClosureCall(
                            closure, caller, null, supplied);

            try {
                return ProtosNumericValueSupport.guestValue(
                        node.call(frameArguments));
            } catch (ProtosBytecodeControlTransferException bridged) {
                throw bridged.transfer();
            }
        }

        @Specialization(guards = "temporarilyRejected(receiver, caller)")
        public static Object rejected(
                Object receiver,
                ProtosActivation caller,
                Object[] supplied) {
            return fallback(receiver, caller, supplied);
        }

        @Specialization
        public static Object generic(
                Object receiver,
                ProtosActivation caller,
                Object[] supplied) {
            return fallback(receiver, caller, supplied);
        }

        static Object fallback(
                Object receiver,
                ProtosActivation caller,
                Object[] supplied) {
            return PrepareClosureCallArguments
                    .perform(receiver, caller, supplied);
        }

        static CanonicalClosure sourceDefinitionOrNull(Object receiver) {
            return receiver instanceof ProtosClosureValue closure
                            && closure.nativeBody().isEmpty()
                    ? closure.definition() : null;
        }

        static ProtosClosureExecutionPlan sourcePlanOrNull(Object receiver) {
            return receiver instanceof ProtosClosureValue closure
                            && closure.nativeBody().isEmpty()
                    ? closure.executionPlan().orElse(null) : null;
        }

        static boolean requiresProjection(Object receiver) {
            return receiver instanceof ProtosClosureValue closure
                    && closure.requiresContextLocalExecutionProjectionForRuntime();
        }

        static ProtosClosureValue admitted(
                Object receiver, ProtosActivation caller) {
            ProtosClosureValue closure =
                    ProtosClosureCallPreparation.directClosureCallSelectionOrNull(
                            receiver, caller);

            return closure != null
                            && closure.nativeBody().isEmpty()
                            && closure.invocationReturnHomeForRuntime()
                                    == ProtosReturnHome.unobservable()
                    ? closure : null;
        }

        @NonIdempotent
        static boolean temporarilyRejected(
                Object receiver, ProtosActivation caller) {
            return sourceDefinitionOrNull(receiver) != null
                    && admitted(receiver, caller) == null;
        }

        static RootCallTarget targetFor(
                Object receiver,
                ProtosActivation caller,
                ProtosLanguageContext entered) {
            if (entered == null || sourcePlanOrNull(receiver) == null) {
                return null;
            }
            ProtosClosureValue closure = admitted(receiver, caller);
            return closure == null ? null
                    : PrepareSendArguments
                            .fastOrdinarySendTarget(closure, entered);
        }

        static DirectCallNode createDirectCallNodeOrNull(
                RootCallTarget target) {
            return target == null
                    ? null
                    : DirectCallNode.create(target);
        }

        static boolean nonSuspending(RootCallTarget target) {
            return target != null
                    && target.getRootNode()
                            instanceof ProtosBytecodeRootNode root
                    && root.provablyNonSuspendingBody();
        }

        @NonIdempotent
        static ProtosLanguageContext currentEnteredContext(Node node) {
            return ProtosLanguageContext.current(node);
        }
    }

    @Operation
    public static final class PrepareClosureCallVector {
        @Specialization
        public static PreparedClosureCall perform(
                Object receiver,
                ProtosActivation caller,
                PreparedArgumentVector supplied) {
            return ProtosClosureCallPreparation.prepareClosureCall(
                    receiver,
                    supplied.snapshot(),
                    caller);
        }
    }

    @Operation
    public static final class PrepareSendArguments {
        @Specialization(
                guards = {
                    "isIntegerReceiver(receiver)",
                    "selector.equals(cachedSelector)",
                    "enteredContext != null",
                    "enteredContext == cachedContext",
                    "prelude == cachedPrelude",
                    "cachedInteger != null"
                },
                assumptions = "cachedInteger.stability()",
                limit = "3")
        public static PreparedClosureCall guardedIntegerSend(
                Object receiver,
                String selector,
                Object caller,
                @Variadic Object[] supplied,
                @Bind("currentEnteredContext($node)")
                        ProtosLanguageContext enteredContext,
                @Bind("callerPrelude(caller)") ProtosPrelude prelude,
                @Cached("selector") String cachedSelector,
                @Cached("enteredContext") ProtosLanguageContext cachedContext,
                @Cached("prelude") ProtosPrelude cachedPrelude,
                @Cached("createGuardedIntegerSend(receiver, selector, prelude, enteredContext)")
                        GuardedIntegerSend cachedInteger) {
            return PrepareSendArguments.guardedIntegerSendForActivation(
                    receiver, selector, exactCaller(caller), supplied, enteredContext, prelude,
                    cachedSelector, cachedContext, cachedPrelude, cachedInteger);
        }

        /*
         * PERF038-E: the guarded ordinary send is specialized on the state of
         * its caller operand (see CurrentCallerReference), so each compiled
         * form carries only the provenance path it has observed:
         *
         *   guardedOrdinarySend           a published caller activation;
         *   guardedOrdinarySendInherited* a compact caller whose callee
         *                                 inherits all provenance from its own
         *                                 caller, passed in its place without
         *                                 materialization;
         *   guardedOrdinarySendMaterializing  any other compact caller, which
         *                                 is materialized and published
         *                                 exactly as CurrentActivation would.
         *
         * The inheritance condition is a guard evaluated on every execution,
         * so a compact caller that stops satisfying it (a different root
         * Closure, prelude or explicit Task) leaves the inherited form and
         * respecializes; no materialization path is compiled into a site
         * that has never needed one. Every form shares the selection
         * Assumption, receiver identity and entered-Context guards and runs
         * the same preparation.
         */
        @Specialization(
                guards = {
                    "receiver == cachedReceiver",
                    "selector.equals(cachedSelector)",
                    "enteredContext != null",
                    "enteredContext == cachedContext",
                    "cachedSend != null"
                },
                assumptions = "cachedSend.stability()",
                limit = "3")
        public static PreparedClosureCall guardedOrdinarySend(
                Object receiver,
                String selector,
                ProtosActivation caller,
                @Variadic Object[] supplied,
                @Bind("currentEnteredContext($node)")
                        ProtosLanguageContext enteredContext,
                @Cached("receiver") Object cachedReceiver,
                @Cached("selector") String cachedSelector,
                @Cached("enteredContext") ProtosLanguageContext cachedContext,
                @Cached("createGuardedSend(receiver, selector, caller, enteredContext)")
                        GuardedSendTarget cachedSend) {
            /*
             * PERF038-D: the selected Closure, method home, target and shared
             * return home are the specialization's constants; the frame
             * arguments are built right here, so the carrier takes their
             * header values directly instead of re-validating the array.
             */
            ProtosReturnHome returnHome = cachedSend.invocationHome();
            Object[] frameArguments =
                    ProtosFrameArguments.compactImmediateMethodCall(
                            cachedSend.closure(),
                            receiver,
                            cachedSend.methodHome(),
                            caller,
                            returnHome,
                            supplied);
            return PreparedClosureCall.ordinaryCompactSelected(
                    cachedSend.target(),
                    frameArguments,
                    returnHome,
                    cachedSend.ownsReturnHome());
        }

        /*
         * PERF038-F: the inherited form is split by the provenance relation
         * the site has observed, each decided by fixed full-header slots
         * instead of re-deriving the ABI and merging the alternatives:
         *
         *   guardedOrdinarySendInheritedOwnPreludeAbsent  the root Closure has
         *                                 no prelude of its own;
         *   guardedOrdinarySendInheritedSamePrelude  the root Closure's prelude
         *                                 is its caller's.
         *
         * I102: every unmaterialized frame carries the full B5 header, so
         * these two forms cover every inherited relation.
         *
         * A Closure with a different prelude or an explicit Task satisfies
         * none of the inherited guards and takes the materializing form, as
         * before. The guards are re-evaluated on every execution.
         */
        @Specialization(
                guards = {
                    "isInheritingFullHeader(caller)",
                    "ownPreludeAbsent(caller)",
                    "receiver == cachedReceiver",
                    "selector.equals(cachedSelector)",
                    "enteredContext != null",
                    "enteredContext == cachedContext",
                    "cachedSend != null"
                },
                assumptions = "cachedSend.stability()",
                limit = "3")
        public static PreparedClosureCall guardedOrdinarySendInheritedOwnPreludeAbsent(
                Object receiver,
                String selector,
                Object[] caller,
                @Variadic Object[] supplied,
                @Bind("currentEnteredContext($node)")
                        ProtosLanguageContext enteredContext,
                @Cached("receiver") Object cachedReceiver,
                @Cached("selector") String cachedSelector,
                @Cached("enteredContext") ProtosLanguageContext cachedContext,
                @Cached("createGuardedSend(receiver, selector, fullHeaderCaller(caller), enteredContext)")
                        GuardedSendTarget cachedSend) {
            return PrepareSendArguments.guardedOrdinarySend(
                    receiver, selector, ProtosFrameArguments.unmaterializedFullHeaderCaller(caller),
                    supplied, enteredContext, cachedReceiver, cachedSelector, cachedContext,
                    cachedSend);
        }

        @Specialization(
                guards = {
                    "isInheritingFullHeader(caller)",
                    "ownPreludeIsCallers(caller)",
                    "receiver == cachedReceiver",
                    "selector.equals(cachedSelector)",
                    "enteredContext != null",
                    "enteredContext == cachedContext",
                    "cachedSend != null"
                },
                assumptions = "cachedSend.stability()",
                limit = "3")
        public static PreparedClosureCall guardedOrdinarySendInheritedSamePrelude(
                Object receiver,
                String selector,
                Object[] caller,
                @Variadic Object[] supplied,
                @Bind("currentEnteredContext($node)")
                        ProtosLanguageContext enteredContext,
                @Cached("receiver") Object cachedReceiver,
                @Cached("selector") String cachedSelector,
                @Cached("enteredContext") ProtosLanguageContext cachedContext,
                @Cached("createGuardedSend(receiver, selector, fullHeaderCaller(caller), enteredContext)")
                        GuardedSendTarget cachedSend) {
            return PrepareSendArguments.guardedOrdinarySend(
                    receiver, selector, ProtosFrameArguments.unmaterializedFullHeaderCaller(caller),
                    supplied, enteredContext, cachedReceiver, cachedSelector, cachedContext,
                    cachedSend);
        }

        @Specialization(
                guards = {
                    "receiver == cachedReceiver",
                    "selector.equals(cachedSelector)",
                    "enteredContext != null",
                    "enteredContext == cachedContext",
                    "cachedSend != null"
                },
                assumptions = "cachedSend.stability()",
                limit = "3")
        public static PreparedClosureCall guardedOrdinarySendMaterializing(
                Object receiver,
                String selector,
                Object[] caller,
                @Variadic Object[] supplied,
                @Bind("currentEnteredContext($node)")
                        ProtosLanguageContext enteredContext,
                @Cached("receiver") Object cachedReceiver,
                @Cached("selector") String cachedSelector,
                @Cached("enteredContext") ProtosLanguageContext cachedContext,
                @Cached("createGuardedSend(receiver, selector, exactCaller(caller), enteredContext)")
                        GuardedSendTarget cachedSend) {
            return PrepareSendArguments.guardedOrdinarySend(
                    receiver, selector, exactCaller(caller), supplied, enteredContext,
                    cachedReceiver, cachedSelector, cachedContext, cachedSend);
        }

        @Specialization(
                guards = {
                    "!isForeignReceiver(receiver)",
                    "closure != null",
                    "enteredContext != null",
                    "selector.equals(cachedSelector)",
                    "closureDefinition != null",
                    "closureDefinition == cachedClosureDefinition",
                    "enteredContext == cachedContext",
                    "cachedTarget != null"
                },
                limit = "3")
        public static PreparedClosureCall fastOrdinarySend(
                Object receiver,
                String selector,
                Object caller,
                @Variadic Object[] supplied,
                @Bind("performOrdinarySendLookup(receiver, selector, exactCaller(caller))")
                        ProtosSlotLookupResult selected,
                @Bind("ordinarySendClosureOrNull(selected)")
                        ProtosClosureValue closure,
                @Bind("selected.home()") ProtosObjectValue methodHome,
                @Bind("ordinarySendClosureDefinitionOrNull(closure)")
                        CanonicalClosure closureDefinition,
                @Bind("currentEnteredContext($node)")
                        ProtosLanguageContext enteredContext,
                @Cached("selector") String cachedSelector,
                @Cached("closureDefinition") CanonicalClosure cachedClosureDefinition,
                @Cached("enteredContext") ProtosLanguageContext cachedContext,
                @Cached("fastOrdinarySendTarget(closure, enteredContext)")
                        RootCallTarget cachedTarget) {
            return PrepareSendArguments.fastOrdinarySendForActivation(
                    receiver, selector, exactCaller(caller), supplied, selected, closure, methodHome,
                    closureDefinition, enteredContext, cachedSelector, cachedClosureDefinition,
                    cachedContext, cachedTarget);
        }

        @Specialization(
                guards = {
                    "receiver == cachedReceiver",
                    "selector.equals(cachedSelector)",
                    "enteredContext != null",
                    "enteredContext == cachedContext",
                    "cachedStructured != null"
                },
                assumptions = "cachedStructured.stability()",
                limit = "3")
        public static PreparedClosureCall guardedStructuredSend(
                Object receiver,
                String selector,
                Object caller,
                @Variadic Object[] supplied,
                @Bind("currentEnteredContext($node)")
                        ProtosLanguageContext enteredContext,
                @Cached("receiver") Object cachedReceiver,
                @Cached("selector") String cachedSelector,
                @Cached("enteredContext") ProtosLanguageContext cachedContext,
                @Cached("createGuardedStructuredSend(receiver, selector, exactCaller(caller))")
                        GuardedStructuredSend cachedStructured) {
            return PrepareSendArguments.guardedStructuredSendForActivation(
                    receiver, selector, exactCaller(caller), supplied, enteredContext,
                    cachedReceiver, cachedSelector, cachedContext, cachedStructured);
        }

        @Specialization(
                replaces = {
                    "guardedIntegerSend",
                    "guardedOrdinarySend",
                    "guardedOrdinarySendInheritedOwnPreludeAbsent",
                    "guardedOrdinarySendInheritedSamePrelude",
                    "guardedOrdinarySendMaterializing",
                    "fastOrdinarySend",
                    "guardedStructuredSend"
                })
        public static PreparedClosureCall perform(
                Object receiver,
                String selector,
                Object caller,
                @Variadic Object[] supplied) {
            return PrepareSendArguments.performForActivation(
                    receiver, selector, exactCaller(caller), supplied);
        }

        /**
         * Resolves only while establishing a specialization. Admits a selected
         * native Closure only; a source-backed selection (for example a user
         * override) stays on the existing ordinary/generic paths.
         */
                static GuardedIntegerSend createGuardedIntegerSend(
                Object receiver, String selector, ProtosPrelude prelude) {
            return createGuardedIntegerSend(
                    receiver,
                    selector,
                    prelude,
                    ProtosLanguageContext.currentIfEnteredForRuntime());
        }

        static GuardedIntegerSend createGuardedIntegerSend(
                Object receiver,
                String selector,
                ProtosPrelude prelude,
                ProtosLanguageContext enteredContext) {
            if (enteredContext == null) {
                return null;
            }
            ProtosValueLookup.GuardedLookup lookup;
            try {
                lookup = ProtosValueLookup.lookupGuardedInteger(
                        receiver, selector, prelude);
            } catch (UnsupportedOperationException unsupportedRepresentation) {
                return null;
            }
            if (lookup == null) {
                return null;
            }
            ProtosSlotLookupResult selected = lookup.selected();
            if (!(selected.value() instanceof ProtosClosureValue closure)
                    || closure.nativeBody().isEmpty()
                    || !lookup.stability().isValid()) {
                lookup.stability().invalidate();
                return null;
            }
            ProtosStandardIntegerProtocol.CanonicalIntegerOperation operation =
                    ProtosStandardIntegerProtocol.canonicalOperationForSelection(
                            closure,
                            selected.home(),
                            selector,
                            prelude);
            return new GuardedIntegerSend(
                    closure,
                    selected.home(),
                    operation,
                    lookup.stability());
        }

        static boolean isIntegerReceiver(Object receiver) {
            return ProtosValueLookup.isInteger(receiver);
        }

        /**
         * PERF038-D: the prelude of the caller a {@link CurrentCallerReference}
         * denotes, without materializing it ({@link
         * ProtosFrameArguments#preludeOrNull} answers exactly what the
         * materialized activation would).
         */
        static ProtosPrelude callerPrelude(Object caller) {
            if (caller instanceof ProtosActivation activation) {
                return PrepareSendArguments.callerPreludeForActivation(activation);
            }
            return ProtosFrameArguments.preludeOrNull((Object[]) caller);
        }

        /**
         * PERF038-D: the exact caller activation a caller operand denotes,
         * materializing and publishing a compact root activation exactly as
         * {@link CurrentActivation} would have when the operand was evaluated.
         * Materialization is not guest-observable by itself: the activation
         * identity is the one every later observer of the root also sees.
         */
        static ProtosActivation exactCaller(Object caller) {
            if (caller instanceof ProtosActivation activation) {
                return activation;
            }
            return ProtosFrameArguments.activation((Object[]) caller);
        }

        /**
         * PERF038-F: {@link ProtosFrameArguments#isUnmaterializedInheritingFullHeader}
         * of a compact caller operand, re-evaluated on every execution because
         * the root may be published between executions.
         */
        @NonIdempotent
        static boolean isInheritingFullHeader(Object[] caller) {
            return ProtosFrameArguments.isUnmaterializedInheritingFullHeader(caller);
        }

        /** Precondition: {@link #isInheritingFullHeader}. */
        static boolean ownPreludeAbsent(Object[] caller) {
            return ProtosFrameArguments.unmaterializedFullHeaderOwnPrelude(caller) == null;
        }

        /** Precondition: {@link #isInheritingFullHeader}. */
        static boolean ownPreludeIsCallers(Object[] caller) {
            return ProtosFrameArguments.unmaterializedFullHeaderOwnPrelude(caller)
                    == ProtosFrameArguments.unmaterializedFullHeaderCaller(caller)
                            .preludeOrNullForRuntime();
        }

        /** Precondition: {@link #isInheritingFullHeader}. */
        static ProtosActivation fullHeaderCaller(Object[] caller) {
            return ProtosFrameArguments.unmaterializedFullHeaderCaller(caller);
        }

        /**
         * Resolves and classifies only while establishing a specialization.
         * The entered Context owns the target; neither Closure definition
         * identity alone nor an unguarded method home authorizes this hit.
         */
                static GuardedSendTarget createGuardedSend(
                Object receiver,
                String selector,
                ProtosActivation caller,
                ProtosLanguageContext enteredContext) {
            return createGuardedSendForPrelude(
                    receiver, selector, caller.preludeOrNullForRuntime(), enteredContext);
        }


        /**
         * Resolves and classifies only while establishing a specialization.
         * Reuses {@link ProtosValueLookup#lookupGuarded} exactly as
         * {@link #createGuardedSend} does, then admits only a selected native
         * Closure whose behavior/home satisfies one canonical-selection helper.
         * A copied/aliased/overridden selection (same native body at a
         * noncanonical home, or a different selected behavior entirely) misses
         * here and invalidates the assumption immediately, leaving the exact
         * generic classification as the sole authority for that selector.
         */
                static GuardedStructuredSend createGuardedStructuredSend(
                Object receiver, String selector, ProtosActivation caller) {
            boolean canonicalBoolean = ProtosValueLookup.isCanonicalBoolean(receiver);
            ProtosValueLookup.GuardedLookup lookup;
            try {
                lookup = canonicalBoolean
                        ? ProtosValueLookup.lookupGuardedCanonicalBoolean(
                                receiver, selector, caller.preludeOrNullForRuntime())
                        : ProtosValueLookup.lookupGuarded(
                                receiver, selector, caller.preludeOrNullForRuntime());
            } catch (UnsupportedOperationException unsupportedRepresentation) {
                return null;
            }
            if (lookup == null) {
                return null;
            }
            ProtosSlotLookupResult selected = lookup.selected();
            if (!(selected.value() instanceof ProtosClosureValue closure)
                    || closure.nativeBody().isEmpty()) {
                lookup.stability().invalidate();
                return null;
            }
            ProtosObjectValue home = selected.home();
            if (canonicalBoolean) {
                // PERF015: a represented Boolean is admitted only for the
                // canonical Boolean callbacks; every other selection stays generic.
                ProtosStandardBooleanProtocol.StructuredCallbackKind canonicalKind =
                        ProtosStandardBooleanProtocol
                                .structuredCallbackKindForCanonicalSelection(closure, home);
                if (canonicalKind == null || !lookup.stability().isValid()) {
                    lookup.stability().invalidate();
                    return null;
                }
                return new GuardedStructuredSend(
                        closure, home, GuardedStructuredKind.BOOLEAN, canonicalKind,
                        null, lookup.stability());
            }
            GuardedStructuredKind kind;
            ProtosStandardBooleanProtocol.StructuredCallbackKind booleanKind = null;
            ProtosStandardMapProtocol.StructuredReadLookupKind mapReadLookupKind = null;
            if (ProtosStandardObjectProtocol.isCanonicalStandardEnsureSelection(
                    closure, home)) {
                kind = GuardedStructuredKind.ENSURE;
            } else if (ProtosStandardErrorProtocol.isCanonicalStandardHandleSelection(
                    closure, home, caller)) {
                kind = GuardedStructuredKind.ERROR_HANDLE;
            } else if (ProtosStandardObjectProtocol.isCanonicalStandardPreTestLoopSelection(
                    closure, home)) {
                kind = GuardedStructuredKind.WHILE;
            } else if ((booleanKind =
                    ProtosStandardBooleanProtocol
                            .structuredCallbackKindForCanonicalSelection(
                                    closure, home))
                    != null) {
                kind = GuardedStructuredKind.BOOLEAN;
            } else if (ProtosStandardArrayProtocol.isCanonicalStandardEachSelection(
                    closure, home, caller)) {
                kind = GuardedStructuredKind.ARRAY_EACH;
            } else if (ProtosStandardBytesProtocol.isCanonicalStandardEachSelection(
                    closure, home, caller)) {
                kind = GuardedStructuredKind.BYTES_EACH;
            } else if (ProtosStandardEnvironmentProtocol.isCanonicalStandardEachSelection(
                    closure, receiver, home)) {
                kind = GuardedStructuredKind.ENVIRONMENT_EACH;
            } else if (ProtosStandardIdentityMapProtocol.isCanonicalStandardEachSelection(
                    closure, home, caller)) {
                kind = GuardedStructuredKind.IDENTITY_MAP_EACH;
            } else if (ProtosStandardMapProtocol.isCanonicalStandardEachSelection(
                    closure, home, caller)) {
                kind = GuardedStructuredKind.MAP_EACH;
            } else if ((mapReadLookupKind =
                    ProtosStandardMapProtocol
                            .structuredReadLookupKindForCanonicalSelection(
                                    closure, home, caller))
                    != null) {
                kind = GuardedStructuredKind.MAP_READ_LOOKUP;
            } else if (ProtosStandardMapProtocol.isCanonicalStandardAtPutSelection(
                    closure, home, caller)) {
                kind = GuardedStructuredKind.MAP_AT_PUT;
            } else if (ProtosStandardMapProtocol.isCanonicalStandardRemoveSelection(
                    closure, home, caller)) {
                kind = GuardedStructuredKind.MAP_REMOVE;
            } else if (ProtosStandardObjectProtocol.isCanonicalStandardCaseOfSelection(
                    closure, home)) {
                kind = GuardedStructuredKind.CASE_OF;
            } else if (ProtosStandardArrayProtocol.isCanonicalStandardMatchSelection(
                    closure, home, caller)) {
                kind = GuardedStructuredKind.ARRAY_MATCH;
            } else if (ProtosStandardMapProtocol.isCanonicalStandardMatchSelection(
                    closure, home, caller)) {
                kind = GuardedStructuredKind.MAP_MATCH;
            } else if (ProtosStandardIdentityMapProtocol.isCanonicalStandardAtIfAbsentSelection(
                    closure, home, caller)) {
                kind = GuardedStructuredKind.IDENTITY_MAP_AT_IF_ABSENT;
            } else {
                lookup.stability().invalidate();
                return null;
            }
            if (!lookup.stability().isValid()) {
                return null;
            }
            return new GuardedStructuredSend(
                    closure, home, kind, booleanKind, mapReadLookupKind, lookup.stability());
        }

        /*
         * Bytecode DSL guard/@Bind expressions in this Operation are resolved
         * by the DSL processor's own expression parser, which only sees
         * members declared directly on this class (not members of the
         * enclosing root node, and not other top-level types by simple
         * name). These forwarders keep that resolution local while
         * delegating to the exact shared implementation, avoiding a duplicate
         * source of truth for lookup/error semantics.
         */

                static ProtosSlotLookupResult performOrdinarySendLookup(
                Object receiver, String selector, ProtosActivation caller) {
            return ProtosSendPreparation.performOrdinarySendLookup(
                    receiver,
                    selector,
                    caller);
        }

        /**
         * D188: a foreign-member read is a foreign operation, so the speculative fast-path
         * selection must not perform it; foreign receivers always use the generic send path.
         */
                static boolean isForeignReceiver(Object receiver) {
            return receiver
                    instanceof com.guillermomolina.protos.runtime.ProtosForeignProjectedReceiver;
        }

        /**
         * Returns the non-native ordinary Closure selected by {@code selected},
         * or {@code null} when the selection is not an ordinary source-backed
         * Closure. Native Closures (including the canonical standard-import
         * intrinsic) always miss here and remain on the exact generic
         * {@code perform} path.
         */
                static ProtosClosureValue ordinarySendClosureOrNull(ProtosSlotLookupResult selected) {
            if (selected.value() instanceof ProtosClosureValue closure
                    && closure.nativeBody().isEmpty()) {
                return closure;
            }
            return null;
        }

        /**
         * Returns the stable executable-identity key for {@code closure}, or
         * {@code null} when there is no selected Closure. Same-definition
         * Closures always rematerialize this exact {@link CanonicalClosure}
         * instance (see the class-level PERF010-A note), so this key stays
         * stable across fresh {@code ProtosClosureValue}/{@code methodHome}
         * materializations of the same executable behavior.
         */
                static CanonicalClosure ordinarySendClosureDefinitionOrNull(ProtosClosureValue closure) {
            return closure == null ? null : closure.definition();
        }

        @NonIdempotent
        static ProtosLanguageContext currentEnteredContext(Node node) {
            return ProtosLanguageContext.current(node);
        }

        /**
         * Materializes the effective Context-owned Bytecode activation target
         * for one cached fast-hit specialization instance.
         *
         * <p>This runs once, at cache-population time, and may use the
         * existing Context-owned-plan machinery
         * ({@link ProtosClosureCallPreparation#taskOwnedBytecodePlan}); it is not
         * called again on the resulting hot hit. Returns {@code null} when no
         * safe target can be produced for the current entered Context, which
         * keeps the fast specialization from being instantiated for this call
         * and leaves the exact generic path as the fallback.
         */
                static RootCallTarget fastOrdinarySendTarget(
                ProtosClosureValue closure, ProtosLanguageContext enteredContext) {
            if (enteredContext == null) {
                return null;
            }
            ProtosClosureExecutionPlan plan =
                    ProtosClosureCallPreparation.taskOwnedBytecodePlan(
                            closure,
                            enteredContext);
            if (plan == null) {
                return null;
            }
            return plan.bytecodeActivationTargetForComposition();
        }

        /**
         * PERF038-H: guarded source Send0.
         * PIC guards and selection stability were checked by the DSL.
         */
        static PreparedClosureCall guardedOrdinarySendZero(
                Object receiver,
                ProtosActivation caller,
                GuardedSendTarget cachedSend) {
            ProtosReturnHome returnHome = cachedSend.invocationHome();
            Object[] arguments =
                    ProtosFrameArguments.compactImmediateMethodCallZero(
                            cachedSend.closure(),
                            receiver,
                            cachedSend.methodHome(),
                            caller,
                            returnHome);
            return PreparedClosureCall.ordinaryCompactSelected(
                    cachedSend.target(),
                    arguments,
                    returnHome,
                    cachedSend.ownsReturnHome());
        }

        /**
         * PERF038-H: guarded source Send1.
         */
        static PreparedClosureCall guardedOrdinarySendOne(
                Object receiver,
                ProtosActivation caller,
                Object supplied0,
                GuardedSendTarget cachedSend) {
            ProtosReturnHome returnHome = cachedSend.invocationHome();
            Object[] arguments =
                    ProtosFrameArguments.compactImmediateMethodCallOne(
                            cachedSend.closure(),
                            receiver,
                            cachedSend.methodHome(),
                            caller,
                            returnHome,
                            supplied0);
            return PreparedClosureCall.ordinaryCompactSelected(
                    cachedSend.target(),
                    arguments,
                    returnHome,
                    cachedSend.ownsReturnHome());
        }

        /**
         * {@code sharedInvocationHome} (PERF038-D) is the return home every
         * invocation of {@code closure} uses when it is shared, i.e. the
         * Closure's captured home or the unobservable marker of a plan proven
         * return-home-unobservable; {@code null} when each invocation owns a
         * fresh home. The Closure's captured home is final and its execution
         * plan, once present, is never replaced, so a decision taken at
         * specialization stays exactly {@link
         * ProtosClosureValue#invocationReturnHomeForRuntime}'s for this Closure.
         *
         * <p>PERF038-E: {@code freshInvocationHome} is true when, with no
         * shared home, the Closure's plan was already present (and, being
         * never replaced, permanently not proven unobservable) at
         * specialization: each invocation then owns a fresh physical home
         * without re-reading the plan. {@code ownsReturnHome} is exactly
         * {@code closure.returnHome().isEmpty()}, final for the Closure.
         */
        public record GuardedSendTarget(
                ProtosClosureValue closure,
                ProtosObjectValue methodHome,
                RootCallTarget target,
                Assumption stability,
                ProtosReturnHome sharedInvocationHome,
                boolean freshInvocationHome,
                boolean ownsReturnHome) {

            /** Exactly {@code closure().invocationReturnHomeForRuntime()}. */
            ProtosReturnHome invocationHome() {
                if (sharedInvocationHome != null) {
                    return sharedInvocationHome;
                }
                if (freshInvocationHome) {
                    return new ProtosReturnHome();
                }
                return closure.invocationReturnHomeForRuntime();
            }
        }

        /**
         * I072 Phase E structured kinds admitted by {@link #guardedStructuredSend}.
         * Each member corresponds to exactly one canonical-selection helper;
         * the Boolean family additionally carries its
         * {@link ProtosStandardBooleanProtocol.StructuredCallbackKind}.
         */
        enum GuardedStructuredKind {
            ENSURE,
            ERROR_HANDLE,
            WHILE,
            BOOLEAN,
            ARRAY_EACH,
            BYTES_EACH,
            ENVIRONMENT_EACH,
            IDENTITY_MAP_EACH,
            MAP_EACH,
            MAP_READ_LOOKUP,
            MAP_AT_PUT,
            MAP_REMOVE,
            CASE_OF,
            ARRAY_MATCH,
            MAP_MATCH,
            IDENTITY_MAP_AT_IF_ABSENT
        }

        public record GuardedStructuredSend(
                ProtosClosureValue closure,
                ProtosObjectValue methodHome,
                GuardedStructuredKind kind,
                ProtosStandardBooleanProtocol.StructuredCallbackKind booleanKind,
                ProtosStandardMapProtocol.StructuredReadLookupKind mapReadLookupKind,
                Assumption stability) {}

        /**
         * PERF016: a guarded D013 selection for the semantic Integer
         * representation family. Unlike {@link #guardedOrdinarySend} the key is
         * the family, not one receiver object: {@code count - 1} yields a fresh
         * {@code ProtosIntegerValue} on every iteration and must still hit.
         * Only selection is cached; the actual current receiver and arguments
         * flow into the unchanged {@code prepareImmediateMethodCall} path, so
         * the selected native Closure executes exactly as on the generic path.
         * The prelude is part of the key because the represented parent is that
         * prelude's SmallInteger or BigInteger prototype; ProtosValueLookup admits
         * only selections identical for both concrete families, so one entry
         * serves every Integer receiver. The entered Context is retained for the
         * same isolation rule as the other guarded sends.
         */
        public record GuardedIntegerSend(
                ProtosClosureValue closure,
                ProtosObjectValue methodHome,
                ProtosStandardIntegerProtocol.CanonicalIntegerOperation operation,
                Assumption stability) {}

        public static PreparedClosureCall guardedIntegerSendForActivation(
                Object receiver,
                String selector,
                ProtosActivation caller,
                Object[] supplied,
                ProtosLanguageContext enteredContext,
                ProtosPrelude prelude,
                String cachedSelector,
                ProtosLanguageContext cachedContext,
                ProtosPrelude cachedPrelude,
                GuardedIntegerSend cachedInteger) {
            PreparedClosureCall canonical =
                    canonicalIntegerResultOrNull(cachedInteger, receiver, supplied, prelude);
            if (canonical != null) {
                return canonical;
            }

            /*
             * Non-canonical guarded Integer selections, inherited Number
             * operations, wrong-domain operands and Error-producing cases keep
             * the exact PERF027-A path. That preserves the selected Closure,
             * methodHome and rich semantic invocation whenever it is actually
             * observable.
             */
            return ProtosSendPreparation.prepareDeferredImmediateNativeMethodCall(
                    cachedInteger.closure(),
                    receiver,
                    cachedInteger.methodHome(),
                    List.of(supplied),
                    caller);
        }

        /**
         * The immediate result of a canonical guarded Integer operation, or
         * {@code null} for every case that must take the exact selected native
         * invocation (non-canonical selection, wrong-domain operand, Error).
         * Observes neither the caller activation nor its Context.
         */
        /**
         * PERF038-H: Send0 has no successful binary canonical Integer
         * operation. Preserve its native fallback and Error semantics.
         */
        static PreparedClosureCall guardedIntegerSendZero(
                Object receiver,
                Object caller,
                GuardedIntegerSend cachedInteger) {
            ProtosActivation exactCaller =
                    caller instanceof ProtosActivation activation
                            ? activation
                            : ProtosFrameArguments.activation((Object[]) caller);

            return ProtosSendPreparation.prepareDeferredImmediateNativeMethodCall(
                    cachedInteger.closure(),
                    receiver,
                    cachedInteger.methodHome(),
                    List.of(),
                    exactCaller);
        }

        /**
         * PERF038-H: canonical Send1 needs neither an argument vector
         * nor the caller's activation. Materialize the caller only
         * when the native/Error fallback is actually required.
         */
        static PreparedClosureCall guardedIntegerSendOne(
                Object receiver,
                Object caller,
                Object supplied0,
                GuardedIntegerSend cachedInteger) {
            if (cachedInteger.operation() != null) {
                Object directResult =
                        ProtosStandardIntegerProtocol
                                .tryExecuteCanonicalOperationOne(
                                        cachedInteger.operation(),
                                        receiver,
                                        supplied0,
                                        // I091: owner of a result beyond the signed-64 range.
                                        caller instanceof ProtosActivation entered
                                                ? entered.preludeOrNullForRuntime()
                                                : ProtosFrameArguments.preludeOrNull(
                                                        (Object[]) caller));
                if (directResult != null) {
                    return PreparedClosureCall.immediateResult(directResult);
                }
            }

            ProtosActivation exactCaller =
                    caller instanceof ProtosActivation activation
                            ? activation
                            : ProtosFrameArguments.activation((Object[]) caller);

            return ProtosSendPreparation.prepareDeferredImmediateNativeMethodCall(
                    cachedInteger.closure(),
                    receiver,
                    cachedInteger.methodHome(),
                    List.of(supplied0),
                    exactCaller);
        }

        static PreparedClosureCall canonicalIntegerResultOrNull(
                GuardedIntegerSend cachedInteger,
                Object receiver,
                Object[] supplied,
                ProtosPrelude prelude) {
            if (cachedInteger.operation() == null) {
                return null;
            }
            Object directResult =
                    ProtosStandardIntegerProtocol.tryExecuteCanonicalOperation(
                            cachedInteger.operation(),
                            receiver,
                            supplied,
                            // I091: owner of a result beyond the signed-64 range.
                            prelude);
            return directResult == null
                    ? null
                    : PreparedClosureCall.immediateResult(directResult);
        }

        static ProtosPrelude callerPreludeForActivation(ProtosActivation caller) {
            return caller.preludeOrNullForRuntime();
        }

        /** PERF038-D: see {@link GuardedSendTarget#sharedInvocationHome}. */
        private static ProtosReturnHome sharedInvocationHomeOrNull(ProtosClosureValue closure) {
            if (closure.returnHome().isPresent()) {
                return closure.returnHome().orElseThrow();
            }
            ProtosReturnHome home = closure.invocationReturnHomeForRuntime();
            return home == ProtosReturnHome.unobservable() ? home : null;
        }

        static GuardedSendTarget createGuardedSendForPrelude(
                Object receiver,
                String selector,
                ProtosPrelude prelude,
                ProtosLanguageContext enteredContext) {
            if (enteredContext == null) {
                return null;
            }
            ProtosValueLookup.GuardedLookup lookup;
            try {
                lookup = ProtosValueLookup.lookupGuarded(
                        receiver, selector, prelude);
            } catch (UnsupportedOperationException unsupportedRepresentation) {
                return null;
            }
            if (lookup == null) {
                return null;
            }
            ProtosClosureValue closure =
                    ordinarySendClosureOrNull(lookup.selected());
            if (closure == null) {
                lookup.stability().invalidate();
                return null;
            }
            RootCallTarget target =
                    fastOrdinarySendTarget(closure, enteredContext);
            if (target == null || !lookup.stability().isValid()) {
                lookup.stability().invalidate();
                return null;
            }
            ProtosReturnHome sharedHome = sharedInvocationHomeOrNull(closure);
            return new GuardedSendTarget(
                    closure,
                    lookup.selected().home(),
                    target,
                    lookup.stability(),
                    sharedHome,
                    sharedHome == null && closure.executionPlan().isPresent(),
                    closure.returnHome().isEmpty());
        }

        /**
         * PERF038-H: Send0 with dynamically selected source Closure.
         * Preserve the current selected Closure/home, but avoid the
         * supplied-argument vector and compact-header validation.
         */
        static PreparedClosureCall fastOrdinarySendZero(
                ProtosClosureValue closure,
                Object receiver,
                ProtosObjectValue methodHome,
                ProtosActivation caller,
                RootCallTarget target) {
            ProtosReturnHome returnHome =
                    closure.invocationReturnHomeForRuntime();

            return PreparedClosureCall.ordinaryCompactSelected(
                    target,
                    ProtosFrameArguments.compactImmediateMethodCallZero(
                            closure, receiver, methodHome, caller, returnHome),
                    returnHome,
                    closure.returnHome().isEmpty());
        }

        /**
         * PERF038-H: Send1 with dynamically selected source Closure.
         * The exact supplied value enters the final frame directly.
         */
        static PreparedClosureCall fastOrdinarySendOne(
                ProtosClosureValue closure,
                Object receiver,
                ProtosObjectValue methodHome,
                ProtosActivation caller,
                RootCallTarget target,
                Object supplied0) {
            ProtosReturnHome returnHome =
                    closure.invocationReturnHomeForRuntime();

            return PreparedClosureCall.ordinaryCompactSelected(
                    target,
                    ProtosFrameArguments.compactImmediateMethodCallOne(
                            closure, receiver, methodHome, caller,
                            returnHome, supplied0),
                    returnHome,
                    closure.returnHome().isEmpty());
        }

        public static PreparedClosureCall fastOrdinarySendForActivation(
                Object receiver,
                String selector,
                ProtosActivation caller,
                Object[] supplied,
                ProtosSlotLookupResult selected,
                ProtosClosureValue closure,
                ProtosObjectValue methodHome,
                CanonicalClosure closureDefinition,
                ProtosLanguageContext enteredContext,
                String cachedSelector,
                CanonicalClosure cachedClosureDefinition,
                ProtosLanguageContext cachedContext,
                RootCallTarget cachedTarget) {
            Object[] frameArguments =
                    ProtosFrameArguments.compactImmediateMethodCall(
                            closure,
                            receiver,
                            methodHome,
                            caller,
                            supplied);
            return PreparedClosureCall.ordinaryCompact(
                    cachedTarget,
                    frameArguments);
        }

        /*
         * I072 Phase E: a stable canonical structured native send (Object.ensure,
         * Error.handle, Object.whileTrue, Boolean callbacks) reuses the exact Phase A
         * lookupGuarded/Assumption contract that guardedOrdinarySend already
         * establishes for source-backed Closures. createGuardedStructuredSend
         * runs the authoritative D013 lookup and classifies the selected value
         * against the canonical-selection helpers (identity + home/provenance,
         * not native-body identity alone) exactly once, at specialization
         * establishment. The valid hit below never repeats that lookup or the
         * generic finishPreparingComposedCallByImplementation classifier scan;
         * it feeds the single cached kind directly into the unchanged
         * finishPreparingComposedCall/PreparedClosureCall.nativeCall path, so
         * ensure/handle/while/Boolean execution, suspension, cleanup and error
         * semantics remain byte-identical to the generic path. A selector whose
         * selection is not exactly one canonical standard implementation misses
         * here and falls through to the exact generic {@code perform} path,
         * which preserves override/alias/noncanonical-home behavior unchanged.
         */
        public static PreparedClosureCall guardedStructuredSendForActivation(
                Object receiver,
                String selector,
                ProtosActivation caller,
                Object[] supplied,
                ProtosLanguageContext enteredContext,
                Object cachedReceiver,
                String cachedSelector,
                ProtosLanguageContext cachedContext,
                GuardedStructuredSend cachedStructured) {
            return guardedStructuredSendPrepared(
                    receiver, caller, List.of(supplied), cachedStructured);
        }

        /**
         * PERF038-H: Send0, preserving the selected structured kind.
         */
        static PreparedClosureCall guardedStructuredSendZero(
                Object receiver,
                ProtosActivation caller,
                GuardedStructuredSend cachedStructured) {
            return guardedStructuredSendPrepared(
                    receiver, caller, List.of(), cachedStructured);
        }

        /**
         * PERF038-H: Send1 without an intermediate Object[].
         */
        static PreparedClosureCall guardedStructuredSendOne(
                Object receiver,
                ProtosActivation caller,
                Object supplied0,
                GuardedStructuredSend cachedStructured) {
            return guardedStructuredSendPrepared(
                    receiver, caller, List.of(supplied0), cachedStructured);
        }

        /**
         * Exact existing structured invocation body, shared by the
         * variadic and fixed-arity entry points.
         */
        private static PreparedClosureCall guardedStructuredSendPrepared(
                Object receiver,
                ProtosActivation caller,
                List<?> suppliedList,
                GuardedStructuredSend cachedStructured) {
            ProtosSendPreparation.rejectComposedInvocationProjection(cachedStructured.closure());
            ProtosActivation activation =
                    ProtosActivation.forImmediateMethodInvocation(
                            cachedStructured.closure(),
                            suppliedList,
                            receiver,
                            cachedStructured.methodHome(),
                            caller.prelude().orElse(null),
                            caller.actorModuleState(),
                            caller.currentModuleKey().orElse(null),
                            caller.executionDomain());
            ProtosSendPreparation.attachTaskOrInheritDynamicControlState(activation, caller);
            return ProtosSendPreparation.finishPreparingComposedCall(
                    cachedStructured.closure(),
                    suppliedList,
                    activation,
                    cachedStructured.kind() == GuardedStructuredKind.ENSURE,
                    cachedStructured.kind() == GuardedStructuredKind.ERROR_HANDLE,
                    cachedStructured.kind() == GuardedStructuredKind.WHILE,
                    cachedStructured.booleanKind(),
                    cachedStructured.kind() == GuardedStructuredKind.ARRAY_EACH,
                    cachedStructured.kind() == GuardedStructuredKind.BYTES_EACH,
                    cachedStructured.kind() == GuardedStructuredKind.ENVIRONMENT_EACH,
                    cachedStructured.kind() == GuardedStructuredKind.IDENTITY_MAP_EACH,
                    cachedStructured.kind() == GuardedStructuredKind.MAP_EACH,
                    cachedStructured.mapReadLookupKind(),
                    cachedStructured.kind() == GuardedStructuredKind.MAP_AT_PUT,
                    cachedStructured.kind() == GuardedStructuredKind.MAP_REMOVE,
                    false,
                    false,
                    null);
        }

        public static PreparedClosureCall performForActivation(
                Object receiver,
                String selector,
                ProtosActivation caller,
                Object[] supplied) {
            return ProtosSendPreparation.prepareSend(
                    receiver,
                    selector,
                    caller,
                    List.of(supplied));
        }
}


    /* PERF038-H: fixed operand count, with the complete existing PIC policy. */
    @Operation
    public static final class PrepareSendZero {
        @Specialization(
                guards = {
                    "isIntegerReceiver(receiver)",
                    "selector.equals(cachedSelector)",
                    "enteredContext != null",
                    "enteredContext == cachedContext",
                    "prelude == cachedPrelude",
                    "cachedInteger != null"
                },
                assumptions = "cachedInteger.stability()",
                limit = "3")
        public static PreparedClosureCall guardedIntegerSend(
                Object receiver,
                String selector,
                Object caller,

                @Bind("currentEnteredContext($node)")
                        ProtosLanguageContext enteredContext,
                @Bind("callerPrelude(caller)") ProtosPrelude prelude,
                @Cached("selector") String cachedSelector,
                @Cached("enteredContext") ProtosLanguageContext cachedContext,
                @Cached("prelude") ProtosPrelude cachedPrelude,
                @Cached("createGuardedIntegerSend(receiver, selector, prelude, enteredContext)")
                        GuardedIntegerSend cachedInteger) {
            return PrepareSendArguments.guardedIntegerSendZero(
                    receiver, caller, cachedInteger);
        }

        /*
         * PERF038-E: the guarded ordinary send is specialized on the state of
         * its caller operand (see CurrentCallerReference), so each compiled
         * form carries only the provenance path it has observed:
         *
         *   guardedOrdinarySend           a published caller activation;
         *   guardedOrdinarySendInherited* a compact caller whose callee
         *                                 inherits all provenance from its own
         *                                 caller, passed in its place without
         *                                 materialization;
         *   guardedOrdinarySendMaterializing  any other compact caller, which
         *                                 is materialized and published
         *                                 exactly as CurrentActivation would.
         *
         * The inheritance condition is a guard evaluated on every execution,
         * so a compact caller that stops satisfying it (a different root
         * Closure, prelude or explicit Task) leaves the inherited form and
         * respecializes; no materialization path is compiled into a site
         * that has never needed one. Every form shares the selection
         * Assumption, receiver identity and entered-Context guards and runs
         * the same preparation.
         */
        @Specialization(
                guards = {
                    "receiver == cachedReceiver",
                    "selector.equals(cachedSelector)",
                    "enteredContext != null",
                    "enteredContext == cachedContext",
                    "cachedSend != null"
                },
                assumptions = "cachedSend.stability()",
                limit = "3")
        public static PreparedClosureCall guardedOrdinarySend(
                Object receiver,
                String selector,
                ProtosActivation caller,

                @Bind("currentEnteredContext($node)")
                        ProtosLanguageContext enteredContext,
                @Cached("receiver") Object cachedReceiver,
                @Cached("selector") String cachedSelector,
                @Cached("enteredContext") ProtosLanguageContext cachedContext,
                @Cached("createGuardedSend(receiver, selector, caller, enteredContext)")
                        GuardedSendTarget cachedSend) {
            return PrepareSendArguments.guardedOrdinarySendZero(
                    receiver, caller, cachedSend);
        }

        /*
         * PERF038-F: the inherited form is split by the provenance relation
         * the site has observed, each decided by fixed full-header slots
         * instead of re-deriving the ABI and merging the alternatives:
         *
         *   guardedOrdinarySendInheritedOwnPreludeAbsent  the root Closure has
         *                                 no prelude of its own;
         *   guardedOrdinarySendInheritedSamePrelude  the root Closure's prelude
         *                                 is its caller's.
         *
         * I102: every unmaterialized frame carries the full B5 header, so
         * these two forms cover every inherited relation.
         *
         * A Closure with a different prelude or an explicit Task satisfies
         * none of the inherited guards and takes the materializing form, as
         * before. The guards are re-evaluated on every execution.
         */
        @Specialization(
                guards = {
                    "isInheritingFullHeader(caller)",
                    "ownPreludeAbsent(caller)",
                    "receiver == cachedReceiver",
                    "selector.equals(cachedSelector)",
                    "enteredContext != null",
                    "enteredContext == cachedContext",
                    "cachedSend != null"
                },
                assumptions = "cachedSend.stability()",
                limit = "3")
        public static PreparedClosureCall guardedOrdinarySendInheritedOwnPreludeAbsent(
                Object receiver,
                String selector,
                Object[] caller,

                @Bind("currentEnteredContext($node)")
                        ProtosLanguageContext enteredContext,
                @Cached("receiver") Object cachedReceiver,
                @Cached("selector") String cachedSelector,
                @Cached("enteredContext") ProtosLanguageContext cachedContext,
                @Cached("createGuardedSend(receiver, selector, fullHeaderCaller(caller), enteredContext)")
                        GuardedSendTarget cachedSend) {
            return PrepareSendArguments.guardedOrdinarySendZero(
                    receiver, ProtosFrameArguments.unmaterializedFullHeaderCaller(caller), cachedSend);
        }

        @Specialization(
                guards = {
                    "isInheritingFullHeader(caller)",
                    "ownPreludeIsCallers(caller)",
                    "receiver == cachedReceiver",
                    "selector.equals(cachedSelector)",
                    "enteredContext != null",
                    "enteredContext == cachedContext",
                    "cachedSend != null"
                },
                assumptions = "cachedSend.stability()",
                limit = "3")
        public static PreparedClosureCall guardedOrdinarySendInheritedSamePrelude(
                Object receiver,
                String selector,
                Object[] caller,

                @Bind("currentEnteredContext($node)")
                        ProtosLanguageContext enteredContext,
                @Cached("receiver") Object cachedReceiver,
                @Cached("selector") String cachedSelector,
                @Cached("enteredContext") ProtosLanguageContext cachedContext,
                @Cached("createGuardedSend(receiver, selector, fullHeaderCaller(caller), enteredContext)")
                        GuardedSendTarget cachedSend) {
            return PrepareSendArguments.guardedOrdinarySendZero(
                    receiver, ProtosFrameArguments.unmaterializedFullHeaderCaller(caller), cachedSend);
        }

        @Specialization(
                guards = {
                    "receiver == cachedReceiver",
                    "selector.equals(cachedSelector)",
                    "enteredContext != null",
                    "enteredContext == cachedContext",
                    "cachedSend != null"
                },
                assumptions = "cachedSend.stability()",
                limit = "3")
        public static PreparedClosureCall guardedOrdinarySendMaterializing(
                Object receiver,
                String selector,
                Object[] caller,

                @Bind("currentEnteredContext($node)")
                        ProtosLanguageContext enteredContext,
                @Cached("receiver") Object cachedReceiver,
                @Cached("selector") String cachedSelector,
                @Cached("enteredContext") ProtosLanguageContext cachedContext,
                @Cached("createGuardedSend(receiver, selector, exactCaller(caller), enteredContext)")
                        GuardedSendTarget cachedSend) {
            return PrepareSendArguments.guardedOrdinarySendZero(
                    receiver, exactCaller(caller), cachedSend);
        }

        @Specialization(
                guards = {
                    "!isForeignReceiver(receiver)",
                    "closure != null",
                    "enteredContext != null",
                    "selector.equals(cachedSelector)",
                    "closureDefinition != null",
                    "closureDefinition == cachedClosureDefinition",
                    "enteredContext == cachedContext",
                    "cachedTarget != null"
                },
                limit = "3")
        public static PreparedClosureCall fastOrdinarySend(
                Object receiver,
                String selector,
                Object caller,

                @Bind("performOrdinarySendLookup(receiver, selector, exactCaller(caller))")
                        ProtosSlotLookupResult selected,
                @Bind("ordinarySendClosureOrNull(selected)")
                        ProtosClosureValue closure,
                @Bind("selected.home()") ProtosObjectValue methodHome,
                @Bind("ordinarySendClosureDefinitionOrNull(closure)")
                        CanonicalClosure closureDefinition,
                @Bind("currentEnteredContext($node)")
                        ProtosLanguageContext enteredContext,
                @Cached("selector") String cachedSelector,
                @Cached("closureDefinition") CanonicalClosure cachedClosureDefinition,
                @Cached("enteredContext") ProtosLanguageContext cachedContext,
                @Cached("fastOrdinarySendTarget(closure, enteredContext)")
                        RootCallTarget cachedTarget) {
            return PrepareSendArguments.fastOrdinarySendZero(
                    closure,
                    receiver,
                    methodHome,
                    exactCaller(caller),
                    cachedTarget);
        }

        @Specialization(
                guards = {
                    "receiver == cachedReceiver",
                    "selector.equals(cachedSelector)",
                    "enteredContext != null",
                    "enteredContext == cachedContext",
                    "cachedStructured != null"
                },
                assumptions = "cachedStructured.stability()",
                limit = "3")
        public static PreparedClosureCall guardedStructuredSend(
                Object receiver,
                String selector,
                Object caller,

                @Bind("currentEnteredContext($node)")
                        ProtosLanguageContext enteredContext,
                @Cached("receiver") Object cachedReceiver,
                @Cached("selector") String cachedSelector,
                @Cached("enteredContext") ProtosLanguageContext cachedContext,
                @Cached("createGuardedStructuredSend(receiver, selector, exactCaller(caller))")
                        GuardedStructuredSend cachedStructured) {
            return PrepareSendArguments.guardedStructuredSendZero(
                    receiver, exactCaller(caller), cachedStructured);
        }

        @Specialization(
                replaces = {
                    "guardedIntegerSend",
                    "guardedOrdinarySend",
                    "guardedOrdinarySendInheritedOwnPreludeAbsent",
                    "guardedOrdinarySendInheritedSamePrelude",
                    "guardedOrdinarySendMaterializing",
                    "fastOrdinarySend",
                    "guardedStructuredSend"
                })
        public static PreparedClosureCall perform(
                Object receiver,
                String selector,
                Object caller) {
            return ProtosSendPreparation.prepareSendZeroFallback(
                    receiver, selector, exactCaller(caller));

        }

        static GuardedIntegerSend createGuardedIntegerSend(
                Object receiver, String selector, ProtosPrelude prelude) {
            return PrepareSendArguments.createGuardedIntegerSend(
                    receiver, selector, prelude);
        }

        static GuardedIntegerSend createGuardedIntegerSend(
                Object receiver,
                String selector,
                ProtosPrelude prelude,
                ProtosLanguageContext enteredContext) {
            return PrepareSendArguments.createGuardedIntegerSend(
                    receiver, selector, prelude, enteredContext);
        }

        static boolean isIntegerReceiver(Object receiver) {
            return PrepareSendArguments.isIntegerReceiver(receiver);
        }

        /**
         * PERF038-D: the prelude of the caller a {@link CurrentCallerReference}
         * denotes, without materializing it ({@link
         * ProtosFrameArguments#preludeOrNull} answers exactly what the
         * materialized activation would).
         */
        static ProtosPrelude callerPrelude(Object caller) {
            if (caller instanceof ProtosActivation activation) {
                return PrepareSendArguments.callerPreludeForActivation(activation);
            }
            return ProtosFrameArguments.preludeOrNull((Object[]) caller);
        }

        /**
         * PERF038-D: the exact caller activation a caller operand denotes,
         * materializing and publishing a compact root activation exactly as
         * {@link CurrentActivation} would have when the operand was evaluated.
         * Materialization is not guest-observable by itself: the activation
         * identity is the one every later observer of the root also sees.
         */
        static ProtosActivation exactCaller(Object caller) {
            if (caller instanceof ProtosActivation activation) {
                return activation;
            }
            return ProtosFrameArguments.activation((Object[]) caller);
        }

        /**
         * PERF038-F: {@link ProtosFrameArguments#isUnmaterializedInheritingFullHeader}
         * of a compact caller operand, re-evaluated on every execution because
         * the root may be published between executions.
         */
        @NonIdempotent
        static boolean isInheritingFullHeader(Object[] caller) {
            return ProtosFrameArguments.isUnmaterializedInheritingFullHeader(caller);
        }

        /** Precondition: {@link #isInheritingFullHeader}. */
        static boolean ownPreludeAbsent(Object[] caller) {
            return ProtosFrameArguments.unmaterializedFullHeaderOwnPrelude(caller) == null;
        }

        /** Precondition: {@link #isInheritingFullHeader}. */
        static boolean ownPreludeIsCallers(Object[] caller) {
            return ProtosFrameArguments.unmaterializedFullHeaderOwnPrelude(caller)
                    == ProtosFrameArguments.unmaterializedFullHeaderCaller(caller)
                            .preludeOrNullForRuntime();
        }

        /** Precondition: {@link #isInheritingFullHeader}. */
        static ProtosActivation fullHeaderCaller(Object[] caller) {
            return ProtosFrameArguments.unmaterializedFullHeaderCaller(caller);
        }

        static GuardedSendTarget createGuardedSend(
                Object receiver,
                String selector,
                ProtosActivation caller,
                ProtosLanguageContext enteredContext) {
            return PrepareSendArguments.createGuardedSend(
                    receiver, selector, caller, enteredContext);
        }

        static GuardedStructuredSend createGuardedStructuredSend(
                Object receiver, String selector, ProtosActivation caller) {
            return PrepareSendArguments.createGuardedStructuredSend(
                    receiver, selector, caller);
        }

        static ProtosSlotLookupResult performOrdinarySendLookup(
                Object receiver, String selector, ProtosActivation caller) {
            return PrepareSendArguments.performOrdinarySendLookup(
                    receiver, selector, caller);
        }

        static boolean isForeignReceiver(Object receiver) {
            return PrepareSendArguments.isForeignReceiver(receiver);
        }

        static ProtosClosureValue ordinarySendClosureOrNull(ProtosSlotLookupResult selected) {
            return PrepareSendArguments.ordinarySendClosureOrNull(selected);
        }

        static CanonicalClosure ordinarySendClosureDefinitionOrNull(ProtosClosureValue closure) {
            return PrepareSendArguments.ordinarySendClosureDefinitionOrNull(
                    closure);
        }

        @NonIdempotent
        static ProtosLanguageContext currentEnteredContext(Node node) {
            return PrepareSendArguments.currentEnteredContext(node);
        }

        static RootCallTarget fastOrdinarySendTarget(
                ProtosClosureValue closure, ProtosLanguageContext enteredContext) {
            return PrepareSendArguments.fastOrdinarySendTarget(
                    closure, enteredContext);
        }
    }


    @Operation
    public static final class PrepareSendOne {
        @Specialization(
                guards = {
                    "isIntegerReceiver(receiver)",
                    "selector.equals(cachedSelector)",
                    "enteredContext != null",
                    "enteredContext == cachedContext",
                    "prelude == cachedPrelude",
                    "cachedInteger != null"
                },
                assumptions = "cachedInteger.stability()",
                limit = "3")
        public static PreparedClosureCall guardedIntegerSend(
                Object receiver,
                String selector,
                Object caller,
                Object supplied0,
                @Bind("currentEnteredContext($node)")
                        ProtosLanguageContext enteredContext,
                @Bind("callerPrelude(caller)") ProtosPrelude prelude,
                @Cached("selector") String cachedSelector,
                @Cached("enteredContext") ProtosLanguageContext cachedContext,
                @Cached("prelude") ProtosPrelude cachedPrelude,
                @Cached("createGuardedIntegerSend(receiver, selector, prelude, enteredContext)")
                        GuardedIntegerSend cachedInteger) {
            return PrepareSendArguments.guardedIntegerSendOne(
                    receiver, caller, supplied0, cachedInteger);
        }

        /*
         * PERF038-E: the guarded ordinary send is specialized on the state of
         * its caller operand (see CurrentCallerReference), so each compiled
         * form carries only the provenance path it has observed:
         *
         *   guardedOrdinarySend           a published caller activation;
         *   guardedOrdinarySendInherited* a compact caller whose callee
         *                                 inherits all provenance from its own
         *                                 caller, passed in its place without
         *                                 materialization;
         *   guardedOrdinarySendMaterializing  any other compact caller, which
         *                                 is materialized and published
         *                                 exactly as CurrentActivation would.
         *
         * The inheritance condition is a guard evaluated on every execution,
         * so a compact caller that stops satisfying it (a different root
         * Closure, prelude or explicit Task) leaves the inherited form and
         * respecializes; no materialization path is compiled into a site
         * that has never needed one. Every form shares the selection
         * Assumption, receiver identity and entered-Context guards and runs
         * the same preparation.
         */
        @Specialization(
                guards = {
                    "receiver == cachedReceiver",
                    "selector.equals(cachedSelector)",
                    "enteredContext != null",
                    "enteredContext == cachedContext",
                    "cachedSend != null"
                },
                assumptions = "cachedSend.stability()",
                limit = "3")
        public static PreparedClosureCall guardedOrdinarySend(
                Object receiver,
                String selector,
                ProtosActivation caller,
                Object supplied0,
                @Bind("currentEnteredContext($node)")
                        ProtosLanguageContext enteredContext,
                @Cached("receiver") Object cachedReceiver,
                @Cached("selector") String cachedSelector,
                @Cached("enteredContext") ProtosLanguageContext cachedContext,
                @Cached("createGuardedSend(receiver, selector, caller, enteredContext)")
                        GuardedSendTarget cachedSend) {
            return PrepareSendArguments.guardedOrdinarySendOne(
                    receiver, caller, supplied0, cachedSend);
        }

        /*
         * PERF038-F: the inherited form is split by the provenance relation
         * the site has observed, each decided by fixed full-header slots
         * instead of re-deriving the ABI and merging the alternatives:
         *
         *   guardedOrdinarySendInheritedOwnPreludeAbsent  the root Closure has
         *                                 no prelude of its own;
         *   guardedOrdinarySendInheritedSamePrelude  the root Closure's prelude
         *                                 is its caller's.
         *
         * I102: every unmaterialized frame carries the full B5 header, so
         * these two forms cover every inherited relation.
         *
         * A Closure with a different prelude or an explicit Task satisfies
         * none of the inherited guards and takes the materializing form, as
         * before. The guards are re-evaluated on every execution.
         */
        @Specialization(
                guards = {
                    "isInheritingFullHeader(caller)",
                    "ownPreludeAbsent(caller)",
                    "receiver == cachedReceiver",
                    "selector.equals(cachedSelector)",
                    "enteredContext != null",
                    "enteredContext == cachedContext",
                    "cachedSend != null"
                },
                assumptions = "cachedSend.stability()",
                limit = "3")
        public static PreparedClosureCall guardedOrdinarySendInheritedOwnPreludeAbsent(
                Object receiver,
                String selector,
                Object[] caller,
                Object supplied0,
                @Bind("currentEnteredContext($node)")
                        ProtosLanguageContext enteredContext,
                @Cached("receiver") Object cachedReceiver,
                @Cached("selector") String cachedSelector,
                @Cached("enteredContext") ProtosLanguageContext cachedContext,
                @Cached("createGuardedSend(receiver, selector, fullHeaderCaller(caller), enteredContext)")
                        GuardedSendTarget cachedSend) {
            return PrepareSendArguments.guardedOrdinarySendOne(
                    receiver, ProtosFrameArguments.unmaterializedFullHeaderCaller(caller), supplied0, cachedSend);
        }

        @Specialization(
                guards = {
                    "isInheritingFullHeader(caller)",
                    "ownPreludeIsCallers(caller)",
                    "receiver == cachedReceiver",
                    "selector.equals(cachedSelector)",
                    "enteredContext != null",
                    "enteredContext == cachedContext",
                    "cachedSend != null"
                },
                assumptions = "cachedSend.stability()",
                limit = "3")
        public static PreparedClosureCall guardedOrdinarySendInheritedSamePrelude(
                Object receiver,
                String selector,
                Object[] caller,
                Object supplied0,
                @Bind("currentEnteredContext($node)")
                        ProtosLanguageContext enteredContext,
                @Cached("receiver") Object cachedReceiver,
                @Cached("selector") String cachedSelector,
                @Cached("enteredContext") ProtosLanguageContext cachedContext,
                @Cached("createGuardedSend(receiver, selector, fullHeaderCaller(caller), enteredContext)")
                        GuardedSendTarget cachedSend) {
            return PrepareSendArguments.guardedOrdinarySendOne(
                    receiver, ProtosFrameArguments.unmaterializedFullHeaderCaller(caller), supplied0, cachedSend);
        }

        @Specialization(
                guards = {
                    "receiver == cachedReceiver",
                    "selector.equals(cachedSelector)",
                    "enteredContext != null",
                    "enteredContext == cachedContext",
                    "cachedSend != null"
                },
                assumptions = "cachedSend.stability()",
                limit = "3")
        public static PreparedClosureCall guardedOrdinarySendMaterializing(
                Object receiver,
                String selector,
                Object[] caller,
                Object supplied0,
                @Bind("currentEnteredContext($node)")
                        ProtosLanguageContext enteredContext,
                @Cached("receiver") Object cachedReceiver,
                @Cached("selector") String cachedSelector,
                @Cached("enteredContext") ProtosLanguageContext cachedContext,
                @Cached("createGuardedSend(receiver, selector, exactCaller(caller), enteredContext)")
                        GuardedSendTarget cachedSend) {
            return PrepareSendArguments.guardedOrdinarySendOne(
                    receiver, exactCaller(caller), supplied0, cachedSend);
        }

        @Specialization(
                guards = {
                    "!isForeignReceiver(receiver)",
                    "closure != null",
                    "enteredContext != null",
                    "selector.equals(cachedSelector)",
                    "closureDefinition != null",
                    "closureDefinition == cachedClosureDefinition",
                    "enteredContext == cachedContext",
                    "cachedTarget != null"
                },
                limit = "3")
        public static PreparedClosureCall fastOrdinarySend(
                Object receiver,
                String selector,
                Object caller,
                Object supplied0,
                @Bind("performOrdinarySendLookup(receiver, selector, exactCaller(caller))")
                        ProtosSlotLookupResult selected,
                @Bind("ordinarySendClosureOrNull(selected)")
                        ProtosClosureValue closure,
                @Bind("selected.home()") ProtosObjectValue methodHome,
                @Bind("ordinarySendClosureDefinitionOrNull(closure)")
                        CanonicalClosure closureDefinition,
                @Bind("currentEnteredContext($node)")
                        ProtosLanguageContext enteredContext,
                @Cached("selector") String cachedSelector,
                @Cached("closureDefinition") CanonicalClosure cachedClosureDefinition,
                @Cached("enteredContext") ProtosLanguageContext cachedContext,
                @Cached("fastOrdinarySendTarget(closure, enteredContext)")
                        RootCallTarget cachedTarget) {
            return PrepareSendArguments.fastOrdinarySendOne(
                    closure,
                    receiver,
                    methodHome,
                    exactCaller(caller),
                    cachedTarget,
                    supplied0);
        }

        @Specialization(
                guards = {
                    "receiver == cachedReceiver",
                    "selector.equals(cachedSelector)",
                    "enteredContext != null",
                    "enteredContext == cachedContext",
                    "cachedStructured != null"
                },
                assumptions = "cachedStructured.stability()",
                limit = "3")
        public static PreparedClosureCall guardedStructuredSend(
                Object receiver,
                String selector,
                Object caller,
                Object supplied0,
                @Bind("currentEnteredContext($node)")
                        ProtosLanguageContext enteredContext,
                @Cached("receiver") Object cachedReceiver,
                @Cached("selector") String cachedSelector,
                @Cached("enteredContext") ProtosLanguageContext cachedContext,
                @Cached("createGuardedStructuredSend(receiver, selector, exactCaller(caller))")
                        GuardedStructuredSend cachedStructured) {
            return PrepareSendArguments.guardedStructuredSendOne(
                    receiver, exactCaller(caller), supplied0, cachedStructured);
        }

        @Specialization(
                replaces = {
                    "guardedIntegerSend",
                    "guardedOrdinarySend",
                    "guardedOrdinarySendInheritedOwnPreludeAbsent",
                    "guardedOrdinarySendInheritedSamePrelude",
                    "guardedOrdinarySendMaterializing",
                    "fastOrdinarySend",
                    "guardedStructuredSend"
                })
        public static PreparedClosureCall perform(
                Object receiver,
                String selector,
                Object caller,
                Object supplied0) {
            return ProtosSendPreparation.prepareSendOneFallback(
                    receiver, selector, exactCaller(caller), supplied0);

        }

        static GuardedIntegerSend createGuardedIntegerSend(
                Object receiver, String selector, ProtosPrelude prelude) {
            return PrepareSendArguments.createGuardedIntegerSend(
                    receiver, selector, prelude);
        }

        static GuardedIntegerSend createGuardedIntegerSend(
                Object receiver,
                String selector,
                ProtosPrelude prelude,
                ProtosLanguageContext enteredContext) {
            return PrepareSendArguments.createGuardedIntegerSend(
                    receiver, selector, prelude, enteredContext);
        }

        static boolean isIntegerReceiver(Object receiver) {
            return PrepareSendArguments.isIntegerReceiver(receiver);
        }

        /**
         * PERF038-D: the prelude of the caller a {@link CurrentCallerReference}
         * denotes, without materializing it ({@link
         * ProtosFrameArguments#preludeOrNull} answers exactly what the
         * materialized activation would).
         */
        static ProtosPrelude callerPrelude(Object caller) {
            if (caller instanceof ProtosActivation activation) {
                return PrepareSendArguments.callerPreludeForActivation(activation);
            }
            if (caller instanceof PreparedInlineLiteralCall inline) {
                return inline.prelude();
            }
            return ProtosFrameArguments.preludeOrNull((Object[]) caller);
        }

        /**
         * PERF038-D: the exact caller activation a caller operand denotes,
         * materializing and publishing a compact root activation exactly as
         * {@link CurrentActivation} would have when the operand was evaluated.
         * Materialization is not guest-observable by itself: the activation
         * identity is the one every later observer of the root also sees.
         */
        static ProtosActivation exactCaller(Object caller) {
            if (caller instanceof ProtosActivation activation) {
                return activation;
            }
            return ProtosFrameArguments.activation((Object[]) caller);
        }

        /**
         * PERF038-F: {@link ProtosFrameArguments#isUnmaterializedInheritingFullHeader}
         * of a compact caller operand, re-evaluated on every execution because
         * the root may be published between executions.
         */
        @NonIdempotent
        static boolean isInheritingFullHeader(Object[] caller) {
            return ProtosFrameArguments.isUnmaterializedInheritingFullHeader(caller);
        }

        /** Precondition: {@link #isInheritingFullHeader}. */
        static boolean ownPreludeAbsent(Object[] caller) {
            return ProtosFrameArguments.unmaterializedFullHeaderOwnPrelude(caller) == null;
        }

        /** Precondition: {@link #isInheritingFullHeader}. */
        static boolean ownPreludeIsCallers(Object[] caller) {
            return ProtosFrameArguments.unmaterializedFullHeaderOwnPrelude(caller)
                    == ProtosFrameArguments.unmaterializedFullHeaderCaller(caller)
                            .preludeOrNullForRuntime();
        }

        /** Precondition: {@link #isInheritingFullHeader}. */
        static ProtosActivation fullHeaderCaller(Object[] caller) {
            return ProtosFrameArguments.unmaterializedFullHeaderCaller(caller);
        }

        static GuardedSendTarget createGuardedSend(
                Object receiver,
                String selector,
                ProtosActivation caller,
                ProtosLanguageContext enteredContext) {
            return PrepareSendArguments.createGuardedSend(
                    receiver, selector, caller, enteredContext);
        }

        static GuardedStructuredSend createGuardedStructuredSend(
                Object receiver, String selector, ProtosActivation caller) {
            return PrepareSendArguments.createGuardedStructuredSend(
                    receiver, selector, caller);
        }

        static ProtosSlotLookupResult performOrdinarySendLookup(
                Object receiver, String selector, ProtosActivation caller) {
            return PrepareSendArguments.performOrdinarySendLookup(
                    receiver, selector, caller);
        }

        static boolean isForeignReceiver(Object receiver) {
            return PrepareSendArguments.isForeignReceiver(receiver);
        }

        static ProtosClosureValue ordinarySendClosureOrNull(ProtosSlotLookupResult selected) {
            return PrepareSendArguments.ordinarySendClosureOrNull(selected);
        }

        static CanonicalClosure ordinarySendClosureDefinitionOrNull(ProtosClosureValue closure) {
            return PrepareSendArguments.ordinarySendClosureDefinitionOrNull(
                    closure);
        }

        @NonIdempotent
        static ProtosLanguageContext currentEnteredContext(Node node) {
            return PrepareSendArguments.currentEnteredContext(node);
        }

        static RootCallTarget fastOrdinarySendTarget(
                ProtosClosureValue closure, ProtosLanguageContext enteredContext) {
            return PrepareSendArguments.fastOrdinarySendTarget(
                    closure, enteredContext);
        }
    }

    @Operation
    public static final class PrepareSendVector {
        @Specialization
        public static PreparedClosureCall perform(
                Object receiver,
                String selector,
                ProtosActivation caller,
                PreparedArgumentVector supplied) {
            return ProtosSendPreparation.prepareSend(
                    receiver,
                    selector,
                    caller,
                    supplied.snapshot());
        }
    }

    @Operation
    public static final class PrepareSuperSendArguments {
        @Specialization
        public static PreparedClosureCall perform(
                String selector,
                ProtosActivation caller,
                @Variadic Object[] supplied) {
            return ProtosSendPreparation.prepareSuperSend(selector, caller, List.of(supplied));
        }
    }

    @Operation
    public static final class PrepareSuperSendVector {
        @Specialization
        public static PreparedClosureCall perform(
                String selector,
                ProtosActivation caller,
                PreparedArgumentVector supplied) {
            return ProtosSendPreparation.prepareSuperSend(selector, caller, supplied.snapshot());
        }
    }

    // ---- Prepared invocation and C-prime composition -------------------------------

    @Operation
    public static final class RequiresStructuredDispatch {
        @Specialization
        public static boolean ordinary(OrdinarySourceCall prepared) {
            return prepared.requiresStructuredDispatch();
        }

        @Specialization
        public static boolean nativeCall(NativeCall prepared) {
            return prepared.requiresStructuredDispatch();
        }

        @Specialization
        public static boolean immediate(ImmediateResultCall prepared) {
            return prepared.requiresStructuredDispatch();
        }

        @Specialization
        public static boolean moduleInitialization(ModuleInitializationCall prepared) {
            return prepared.requiresStructuredDispatch();
        }
    }

    // ---- PLAT043 standard Boolean orchestration ------------------------------------
    //
    // The prepared standard Boolean capability (IF_TRUE, IF_FALSE,
    // IF_TRUE_IF_FALSE, AND, OR) is sequenced locally in this root instead of
    // entering the untagged structured root. The capability is read from the
    // already-selected prepared call, never from the selector spelling; the
    // finite state machine stays owned by ProtosStructuredControlCalls.PreparedBooleanCall.

    /**
     * Records whether an ordinary non-Boolean send was actually observed
     * at this Boolean-control site.
     *
     * <p>Until the first miss, the generic continuation is reached only after
     * deoptimization, so it does not belong to the compiled Boolean-only
     * execution path.
     */
    static final class CanonicalBooleanMissProfile {
        @CompilationFinal private boolean genericObserved;

        private CanonicalBooleanMissProfile(boolean genericObserved) {
            this.genericObserved = genericObserved;
        }

        static CanonicalBooleanMissProfile create() {
            return new CanonicalBooleanMissProfile(false);
        }

        static CanonicalBooleanMissProfile uncached() {
            return new CanonicalBooleanMissProfile(true);
        }

        void observeMiss() {
            if (!genericObserved) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                genericObserved = true;
            }
        }
    }

    /**
     * I092: native canonical Boolean.ifTrue, before generic send preparation.
     *
     * <p>The lowerer emits these candidates only for an ordinary {@code ifTrue}
     * send compiled after the standard root Object was published FROZEN (D049).
     * Canonical true/false own no slots and delegate to that root by
     * representation, and publication validates its ifTrue as the standard
     * behavior, so D013 selection for those two receivers is fixed for the life
     * of the site: no lookup, Assumption or cache is needed, and the cached and
     * uncached interpreters execute the same two identity tests.
     *
     * <p>Result: null for any other receiver (ordinary dispatch), ProtosNullValue
     * for false, otherwise the selected callback as a PreparedInlineLiteralCall
     * invoked from the send's own caller. No standard ifTrue activation exists.
     */

    /**
     * PERF041-D1: the primitive classification decides only whether
     * canonical Boolean.ifTrue selects its callback.
     */
    @Operation
    public static final class IsCanonicalIfTrueTrue {
        @Specialization
        public static boolean perform(int classification) {
            return classification
                    == ProtosCanonicalBooleanOneNode.CLASS_TRUE;
        }
    }

    /** I095-C: the canonical FALSE classification of a dynamic control receiver. */
    @Operation
    public static final class IsCanonicalBooleanFalseClass {
        @Specialization
        public static boolean perform(int classification) {
            return classification
                    == ProtosCanonicalBooleanOneNode.CLASS_FALSE;
        }
    }

    /**
     * I095-D: the and/or policy for the normal result of a selected callback
     * that ran inline. A canonical true/false passes unchanged; any other
     * value, including null and a Future, signals the same standard Error as
     * {@link ProtosStandardBooleanProtocol#requireBooleanResult}. The exact
     * caller is resolved only on that failure path.
     */
    @Operation
    public static final class RequireCanonicalBooleanControlResult {
        @Specialization
        public static Object perform(Object result, Object caller) {
            if (result == ProtosBooleanValue.TRUE
                    || result == ProtosBooleanValue.FALSE) {
                return result;
            }
            return ProtosStandardBooleanProtocol.requireBooleanResult(
                    result, PrepareSendArguments.exactCaller(caller));
        }
    }

    /**
     * Result when no native callback is selected.
     * MISS remains the Java null sentinel for ordinary dispatch.
     */
    @Operation
    public static final class CanonicalIfTrueNonTrueResult {
        @Specialization
        public static Object perform(int classification) {
            return classification
                    == ProtosCanonicalBooleanOneNode.CLASS_FALSE
                            ? ProtosNullValue.INSTANCE
                            : null;
        }
    }

    /**
     * Prepare the selected callback only on canonical TRUE.
     * The generic callable protocol remains the existing fallback.
     */
    @Operation
    public static final class PrepareCanonicalIfTrueCallback {
        @Specialization
        public static PreparedInlineLiteralCall perform(
                Object caller,
                Object supplied,
                Object literalPlan,
                @Bind("currentEnteredContext($node)")
                    ProtosLanguageContext context,
                @Cached ProtosNativeIfTrueCallbackNode callback) {
            return callback.execute(
                    caller, supplied, literalPlan, context);
        }

        @NonIdempotent
        static ProtosLanguageContext currentEnteredContext(Node node) {
            return ProtosLanguageContext.current(node);
        }
    }

    /** Spread: exact arity is part of canonical classification. */

    /*
     * I101-A (PLAT058-D): there is no run-time execution-mode operation. The
     * immutable profile of the owning Context selects the physical lowering
     * when the root is built (CanonicalToBytecodeLowerer).
     */

    @Operation
    public static final class TryCompactCanonicalBooleanVector {

        static CanonicalBooleanMissProfile createMissProfile() {
            return CanonicalBooleanMissProfile.create();
        }

        static CanonicalBooleanMissProfile uncachedMissProfile() {
            return CanonicalBooleanMissProfile.uncached();
        }

        static boolean canonicalFalse(Object receiver, PreparedArgumentVector vector) {
            return vector.soleValueOrNull() != null && receiver == ProtosBooleanValue.FALSE;
        }

        static boolean canonicalTrue(Object receiver, PreparedArgumentVector vector) {
            return vector.soleValueOrNull() != null && receiver == ProtosBooleanValue.TRUE;
        }

        @Specialization(guards = "canonicalFalse(receiver, vector)")
        public static int falseBranch(Object receiver, PreparedArgumentVector vector) {
            return ProtosCanonicalBooleanOneNode.CLASS_FALSE;
        }

        @Specialization(guards = "canonicalTrue(receiver, vector)")
        public static int trueBranch(Object receiver, PreparedArgumentVector vector) {
            return ProtosCanonicalBooleanOneNode.CLASS_TRUE;
        }

        @Specialization(guards = {
                "!canonicalFalse(receiver, vector)",
                "!canonicalTrue(receiver, vector)"
        })
        public static int genericMiss(
                Object receiver, PreparedArgumentVector vector,
                @Cached(
                    value = "createMissProfile()",
                    uncached = "uncachedMissProfile()")
                    CanonicalBooleanMissProfile missProfile) {
            missProfile.observeMiss();
            return ProtosCanonicalBooleanOneNode.CLASS_MISS;
        }
    }

    /** Frame-native One: classification does not observe the caller. */

    @Operation
    public static final class TryCompactCanonicalBooleanInlineOne {

        static CanonicalBooleanMissProfile createMissProfile() {
            return CanonicalBooleanMissProfile.create();
        }

        static CanonicalBooleanMissProfile uncachedMissProfile() {
            return CanonicalBooleanMissProfile.uncached();
        }

        static boolean canonicalFalse(Object receiver) {
            return receiver == ProtosBooleanValue.FALSE;
        }

        static boolean canonicalTrue(Object receiver) {
            return receiver == ProtosBooleanValue.TRUE;
        }

        @Specialization(guards = "canonicalFalse(receiver)")
        public static int falseBranch(Object receiver) {
            return ProtosCanonicalBooleanOneNode.CLASS_FALSE;
        }

        @Specialization(guards = "canonicalTrue(receiver)")
        public static int trueBranch(Object receiver) {
            return ProtosCanonicalBooleanOneNode.CLASS_TRUE;
        }

        @Specialization(guards = {
                "!canonicalFalse(receiver)",
                "!canonicalTrue(receiver)"
        })
        public static int genericMiss(
                Object receiver,
                @Cached(
                    value = "createMissProfile()",
                    uncached = "uncachedMissProfile()")
                    CanonicalBooleanMissProfile missProfile) {
            missProfile.observeMiss();
            return ProtosCanonicalBooleanOneNode.CLASS_MISS;
        }
    }

    /** Frame-native Vector: caller observation is deferred. */

    @Operation
    public static final class TryCompactCanonicalBooleanInlineVector {

        static CanonicalBooleanMissProfile createMissProfile() {
            return CanonicalBooleanMissProfile.create();
        }

        static CanonicalBooleanMissProfile uncachedMissProfile() {
            return CanonicalBooleanMissProfile.uncached();
        }

        static boolean canonicalFalse(Object receiver, PreparedArgumentVector vector) {
            return vector.soleValueOrNull() != null && receiver == ProtosBooleanValue.FALSE;
        }

        static boolean canonicalTrue(Object receiver, PreparedArgumentVector vector) {
            return vector.soleValueOrNull() != null && receiver == ProtosBooleanValue.TRUE;
        }

        @Specialization(guards = "canonicalFalse(receiver, vector)")
        public static int falseBranch(Object receiver, PreparedArgumentVector vector) {
            return ProtosCanonicalBooleanOneNode.CLASS_FALSE;
        }

        @Specialization(guards = "canonicalTrue(receiver, vector)")
        public static int trueBranch(Object receiver, PreparedArgumentVector vector) {
            return ProtosCanonicalBooleanOneNode.CLASS_TRUE;
        }

        @Specialization(guards = {
                "!canonicalFalse(receiver, vector)",
                "!canonicalTrue(receiver, vector)"
        })
        public static int genericMiss(
                Object receiver, PreparedArgumentVector vector,
                @Cached(
                    value = "createMissProfile()",
                    uncached = "uncachedMissProfile()")
                    CanonicalBooleanMissProfile missProfile) {
            missProfile.observeMiss();
            return ProtosCanonicalBooleanOneNode.CLASS_MISS;
        }
    }

    /**
     * Selected spread callback. The vector has already passed the
     * one-argument classification guard.
     */
    @Operation
    public static final class PrepareCanonicalIfTrueVectorCallback {
        @Specialization
        public static PreparedInlineLiteralCall perform(
                Object caller,
                PreparedArgumentVector vector,
                @Cached ProtosNativeIfTrueCallbackNode callback) {
            return callback.execute(
                    caller, vector.soleValueOrNull(), null, null);
        }
    }

    /**
     * The inline callback's exact caller is observed only when TRUE
     * selects its child. FALSE and MISS never enter this operation.
     */
    @Operation
    @ConstantOperand(
            type = LocalRangeAccessor.class,
            name = "frameBackedLocals")
    @ConstantOperand(
            type = ProtosFrameLexicalLayout.class,
            name = "frameBackedLayout")
    public static final class PrepareCanonicalIfTrueInlineCallback {
        @Specialization
        public static PreparedInlineLiteralCall perform(
                LocalRangeAccessor frameBackedLocals,
                ProtosFrameLexicalLayout frameBackedLayout,
                PreparedInlineLiteralCall child,
                Object supplied,
                Object literalPlan,
                @Bind BytecodeNode bytecodeNode,
                @Bind VirtualFrame frame,
                @Bind("currentEnteredContext($node)")
                    ProtosLanguageContext context,
                @Cached ProtosNativeIfTrueCallbackNode callback) {

            ProtosActivation caller =
                    ProtosInlineCallbackFrameBindings.invocationCaller(
                            child, frameBackedLocals,
                            frameBackedLayout, bytecodeNode, frame);

            return callback.execute(
                    caller, supplied, literalPlan, context);
        }

        @NonIdempotent
        static ProtosLanguageContext currentEnteredContext(Node node) {
            return ProtosLanguageContext.current(node);
        }
    }

    /**
     * Inline spread: preserve the original vector callback protocol,
     * including its null entered-context fallback.
     */
    @Operation
    @ConstantOperand(
            type = LocalRangeAccessor.class,
            name = "frameBackedLocals")
    @ConstantOperand(
            type = ProtosFrameLexicalLayout.class,
            name = "frameBackedLayout")
    public static final class PrepareCanonicalIfTrueInlineVectorCallback {
        @Specialization
        public static PreparedInlineLiteralCall perform(
                LocalRangeAccessor frameBackedLocals,
                ProtosFrameLexicalLayout frameBackedLayout,
                PreparedInlineLiteralCall child,
                PreparedArgumentVector vector,
                @Bind BytecodeNode bytecodeNode,
                @Bind VirtualFrame frame,
                @Cached ProtosNativeIfTrueCallbackNode callback) {

            ProtosActivation caller =
                    ProtosInlineCallbackFrameBindings.invocationCaller(
                            child, frameBackedLocals,
                            frameBackedLayout, bytecodeNode, frame);

            return callback.execute(
                    caller, vector.soleValueOrNull(), null, null);
        }
    }

    @Operation
    public static final class IsCompactCanonicalBooleanMiss {
        @Specialization
        public static boolean perform(Object value) {
            return value == null;
        }
    }

    @Operation
    public static final class IsCompactCanonicalBooleanNoCallback {
        @Specialization
        public static boolean perform(Object value) {
            return value == ProtosNullValue.INSTANCE;
        }
    }

    @Operation
    public static final class IsNativeIfTrueInlineCallback {
        @Specialization
        public static boolean perform(Object value) {
            return value instanceof PreparedInlineLiteralCall;
        }
    }

    /**
     * I092: completes the outer call of an ifTrue literal site exactly once,
     * after its callback, on every exit. ProtosNullValue means no outer call:
     * the native path (which owns no ifTrue activation or ReturnHome), a
     * non-Boolean generic selection (completed by its own dispatch) or an exit
     * before any outer call was recorded.
     */
    @Operation
    public static final class CompleteIfTrueSiteOuter {
        @Specialization
        public static void none(ProtosNullValue outer) {
            // No outer call owned by this site.
        }

        @Specialization(guards = "!prepared.hasReturnHomeLifecycle()")
        public static void ordinaryWithoutHomeLifecycle(
                OrdinarySourceCall prepared) {
            // No owned materialized ReturnHome to complete.
        }

        @Specialization(guards = "prepared.hasReturnHomeLifecycle()")
        public static void ordinary(OrdinarySourceCall prepared) {
            CompleteClosureCall.ordinary(prepared);
        }

        @Specialization
        public static void nativeCall(NativeCall prepared) {
            CompleteClosureCall.nativeCall(prepared);
        }

        @Specialization
        public static void immediate(ImmediateResultCall prepared) {
            CompleteClosureCall.immediate(prepared);
        }

        @Specialization
        public static void moduleInitialization(ModuleInitializationCall prepared) {
            CompleteClosureCall.moduleInitialization(prepared);
        }
    }

    /** I092: B-prime admission of the native path's prepared callback. */
    @Operation
    public static final class AdmitsNativeIfTrueInline {
        @Specialization
        public static boolean perform(
                PreparedInlineLiteralCall child,
                ProtosClosureExecutionPlanCell literalPlan) {
            return child.admits(literalPlan);
        }
    }

    @Operation
    public static final class IsStructuredBooleanCall {
        @Specialization
        public static boolean ordinary(OrdinarySourceCall prepared) {
            return prepared.isStructuredBoolean();
        }

        @Specialization
        public static boolean nativeCall(NativeCall prepared) {
            return prepared.isStructuredBoolean();
        }

        @Specialization
        public static boolean immediate(ImmediateResultCall prepared) {
            return prepared.isStructuredBoolean();
        }

        @Specialization
        public static boolean moduleInitialization(ModuleInitializationCall prepared) {
            return prepared.isStructuredBoolean();
        }

    }

    @Operation
    public static final class PrepareStructuredBooleanCall {
        @Specialization
        public static PreparedBooleanCall ordinary(OrdinarySourceCall prepared) {
            return prepared.prepareStructuredBoolean();
        }

        @Specialization
        public static PreparedBooleanCall nativeCall(NativeCall prepared) {
            return prepared.prepareStructuredBoolean();
        }

        @Specialization
        public static PreparedBooleanCall immediate(ImmediateResultCall prepared) {
            return prepared.prepareStructuredBoolean();
        }

        @Specialization
        public static PreparedBooleanCall moduleInitialization(ModuleInitializationCall prepared) {
            return prepared.prepareStructuredBoolean();
        }

    }

    @Operation
    public static final class StructuredBooleanHasCallback {
        @Specialization
        public static boolean perform(PreparedBooleanCall prepared) {
            return prepared.hasCallback();
        }
    }

    @Operation
    public static final class PrepareStructuredBooleanCallbackCall {
        @Specialization
        public static PreparedClosureCall perform(PreparedBooleanCall prepared) {
            return prepared.prepareCallback();
        }
    }

    @Operation
    public static final class PrepareInlineStructuredBooleanCallbackCall {
        @Specialization
        public static PreparedInlineLiteralCall perform(PreparedBooleanCall prepared) {
            return prepared.prepareInlineCallback();
        }
    }

    @Operation
    public static final class StructuredBooleanImmediateResult {
        @Specialization
        public static Object perform(PreparedBooleanCall prepared) {
            return prepared.immediateResult();
        }
    }

    @Operation
    public static final class FinishStructuredBooleanCallback {
        @Specialization
        public static Object perform(PreparedBooleanCall prepared, Object result) {
            return prepared.finishCallback(result);
        }
    }

    // ---- PLAT044 B′ inline literal callback (PERF026-B1) ----------------------------
    //
    // After ordinary selection and PLAT043 preparation, an eligible immediate
    // literal callback runs its body inline in this root under the prepared
    // child's own fresh activation (see CanonicalToBytecodeLowerer
    // #emitInlineLiteralCallback). Eligibility is owned by PreparedBooleanCall.

    @Operation
    public static final class AdmitsInlineLiteralCallback {
        @Specialization
        public static boolean perform(
                PreparedBooleanCall prepared,
                PreparedInlineLiteralCall child,
                Object literal,
                ProtosClosureExecutionPlanCell literalPlan,
                int position) {
            return prepared.admitsInlineLiteralCallback(child, literal, literalPlan, position);
        }
    }

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

    @Operation
    public static final class PrepareInlineClosureLiteralCall {
        @Specialization
        public static PreparedInlineLiteralCall perform(
                Object receiver,
                ProtosActivation caller,
                @Variadic Object[] supplied) {
            return ProtosClosureCallPreparation.prepareInlineClosureLiteralCall(
                    receiver, caller, supplied);
        }
    }

    @Operation
    public static final class AdmitsInlineClosureLiteralCall {
        @Specialization
        public static boolean perform(
                PreparedInlineLiteralCall child,
                ProtosClosureExecutionPlanCell literalPlan) {
            return child.admits(literalPlan);
        }
    }

    // ---- PLAT044 B′ inline literal whileTrue (PERF026-C1) ---------------------------
    //
    // A selected standard whileTrue whose receiver and body are exactly the
    // send site's staged literals is sequenced locally in this root; every
    // other while keeps the structured-dispatch root. The loop state machine
    // and Boolean condition authority stay owned by
    // ProtosStructuredControlCalls.PreparedWhileCall.

    @Operation
    public static final class AdmitsInlineLiteralWhile {
        @Specialization
        public static boolean ordinary(
                OrdinarySourceCall prepared,
                Object condition,
                Object body) {
            return prepared.admitsInlineLiteralWhile(condition, body);
        }

        @Specialization
        public static boolean nativeCall(
                NativeCall prepared,
                Object condition,
                Object body) {
            return prepared.admitsInlineLiteralWhile(condition, body);
        }

        @Specialization
        public static boolean immediate(
                ImmediateResultCall prepared,
                Object condition,
                Object body) {
            return prepared.admitsInlineLiteralWhile(condition, body);
        }

        @Specialization
        public static boolean moduleInitialization(
                ModuleInitializationCall prepared,
                Object condition,
                Object body) {
            return prepared.admitsInlineLiteralWhile(condition, body);
        }
    }

    @Operation
    public static final class PrepareStructuredWhileCall {
        @Specialization
        public static PreparedWhileCall ordinary(OrdinarySourceCall prepared) {
            return prepared.prepareStructuredWhile();
        }

        @Specialization
        public static PreparedWhileCall nativeCall(NativeCall prepared) {
            return prepared.prepareStructuredWhile();
        }

        @Specialization
        public static PreparedWhileCall immediate(ImmediateResultCall prepared) {
            return prepared.prepareStructuredWhile();
        }

        @Specialization
        public static PreparedWhileCall moduleInitialization(ModuleInitializationCall prepared) {
            return prepared.prepareStructuredWhile();
        }
    }

    @Operation
    public static final class PrepareStructuredWhileConditionCall {
        @Specialization
        public static PreparedClosureCall perform(PreparedWhileCall prepared) {
            return prepared.prepareCondition();
        }
    }

    @Operation
    public static final class PrepareStructuredWhileBodyCall {
        @Specialization
        public static PreparedClosureCall perform(PreparedWhileCall prepared) {
            return prepared.prepareBody();
        }
    }

    @Operation
    public static final class PrepareInlineStructuredWhileConditionCall {
        @Specialization
        public static PreparedInlineLiteralCall perform(PreparedWhileCall prepared) {
            return prepared.prepareInlineCondition();
        }
    }

    @Operation
    public static final class PrepareInlineStructuredWhileBodyCall {
        @Specialization
        public static PreparedInlineLiteralCall perform(PreparedWhileCall prepared) {
            return prepared.prepareInlineBody();
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

    @Operation
    public static final class StructuredWhileResult {
        @Specialization
        public static Object perform(PreparedWhileCall prepared, Object lastBodyResult) {
            return prepared.result(lastBodyResult);
        }
    }

    @Operation
    public static final class AdmitsInlineLiteralWhileCondition {
        @Specialization
        public static boolean perform(
                PreparedWhileCall prepared,
                PreparedInlineLiteralCall child,
                Object literal,
                ProtosClosureExecutionPlanCell literalPlan) {
            return prepared.admitsInlineLiteralCondition(child, literal, literalPlan);
        }
    }

    @Operation
    public static final class AdmitsInlineLiteralWhileBody {
        @Specialization
        public static boolean perform(
                PreparedWhileCall prepared,
                PreparedInlineLiteralCall child,
                Object literal,
                ProtosClosureExecutionPlanCell literalPlan) {
            return prepared.admitsInlineLiteralBody(child, literal, literalPlan);
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

    @Operation
    public static final class AdmitsInlineLiteralIndexedEach {
        @Specialization
        public static boolean ordinary(OrdinarySourceCall prepared, Object callback) {
            return prepared.admitsInlineLiteralIndexedEach(callback);
        }

        @Specialization
        public static boolean nativeCall(NativeCall prepared, Object callback) {
            return prepared.admitsInlineLiteralIndexedEach(callback);
        }

        @Specialization
        public static boolean immediate(ImmediateResultCall prepared, Object callback) {
            return prepared.admitsInlineLiteralIndexedEach(callback);
        }

        @Specialization
        public static boolean moduleInitialization(
                ModuleInitializationCall prepared,
                Object callback) {
            return prepared.admitsInlineLiteralIndexedEach(callback);
        }
    }

    /**
     * Reached only after {@link AdmitsInlineLiteralIndexedEach}, so exactly
     * one of the two standard capabilities was selected; the ordinary
     * structured preparation of that capability runs unchanged.
     */
    @Operation
    public static final class PrepareStructuredIndexedEachCall {
        @Specialization
        public static PreparedIndexedEachCall ordinary(OrdinarySourceCall prepared) {
            return prepared.isStructuredArrayEach()
                    ? PrepareStructuredArrayEachCall.ordinary(prepared)
                    : PrepareStructuredBytesEachCall.ordinary(prepared);
        }

        @Specialization
        public static PreparedIndexedEachCall nativeCall(NativeCall prepared) {
            return prepared.isStructuredArrayEach()
                    ? PrepareStructuredArrayEachCall.nativeCall(prepared)
                    : PrepareStructuredBytesEachCall.nativeCall(prepared);
        }

        @Specialization
        public static PreparedIndexedEachCall immediate(ImmediateResultCall prepared) {
            return prepared.isStructuredArrayEach()
                    ? PrepareStructuredArrayEachCall.immediate(prepared)
                    : PrepareStructuredBytesEachCall.immediate(prepared);
        }

        @Specialization
        public static PreparedIndexedEachCall moduleInitialization(
                ModuleInitializationCall prepared) {
            return prepared.isStructuredArrayEach()
                    ? PrepareStructuredArrayEachCall
                            .moduleInitialization(prepared)
                    : PrepareStructuredBytesEachCall
                            .moduleInitialization(prepared);
        }
    }

    @Operation
    public static final class AdmitsInlineLiteralTwoParameterEach {
        @Specialization
        public static boolean ordinary(OrdinarySourceCall prepared, Object callback) {
            return prepared.admitsInlineLiteralAssociationEach(callback)
                    || prepared.admitsInlineLiteralEnvironmentEach(callback);
        }

        @Specialization
        public static boolean nativeCall(NativeCall prepared, Object callback) {
            return prepared.admitsInlineLiteralAssociationEach(callback)
                    || prepared.admitsInlineLiteralEnvironmentEach(callback);
        }

        @Specialization
        public static boolean immediate(ImmediateResultCall prepared, Object callback) {
            return prepared.admitsInlineLiteralAssociationEach(callback)
                    || prepared.admitsInlineLiteralEnvironmentEach(callback);
        }

        @Specialization
        public static boolean moduleInitialization(
                ModuleInitializationCall prepared,
                Object callback) {
            return prepared.admitsInlineLiteralAssociationEach(callback)
                    || prepared.admitsInlineLiteralEnvironmentEach(callback);
        }
    }

    /**
     * Reached only after {@link AdmitsInlineLiteralTwoParameterEach}, so
     * exactly one of the three standard capabilities was selected; the
     * ordinary structured preparation of that capability runs unchanged. For
     * Environment.each that preparation still validates callability, then
     * converts, validates and canonically orders the complete portable
     * snapshot before any callback child exists.
     */
    @Operation
    public static final class PrepareStructuredTwoParameterEachCall {
        @Specialization
        public static PreparedLocalEachCall ordinary(OrdinarySourceCall prepared) {
            if (prepared.isStructuredEnvironmentEach()) {
                return PrepareStructuredEnvironmentEachCall
                        .ordinary(prepared);
            }
            return prepared.isStructuredMapEach()
                    ? PrepareStructuredMapEachCall.ordinary(prepared)
                    : PrepareStructuredIdentityMapEachCall
                            .ordinary(prepared);
        }

        @Specialization
        public static PreparedLocalEachCall nativeCall(NativeCall prepared) {
            if (prepared.isStructuredEnvironmentEach()) {
                return PrepareStructuredEnvironmentEachCall
                        .nativeCall(prepared);
            }
            return prepared.isStructuredMapEach()
                    ? PrepareStructuredMapEachCall.nativeCall(prepared)
                    : PrepareStructuredIdentityMapEachCall
                            .nativeCall(prepared);
        }

        @Specialization
        public static PreparedLocalEachCall immediate(ImmediateResultCall prepared) {
            if (prepared.isStructuredEnvironmentEach()) {
                return PrepareStructuredEnvironmentEachCall
                        .immediate(prepared);
            }
            return prepared.isStructuredMapEach()
                    ? PrepareStructuredMapEachCall.immediate(prepared)
                    : PrepareStructuredIdentityMapEachCall
                            .immediate(prepared);
        }

        @Specialization
        public static PreparedLocalEachCall moduleInitialization(
                ModuleInitializationCall prepared) {
            if (prepared.isStructuredEnvironmentEach()) {
                return PrepareStructuredEnvironmentEachCall
                        .moduleInitialization(prepared);
            }
            return prepared.isStructuredMapEach()
                    ? PrepareStructuredMapEachCall
                            .moduleInitialization(prepared)
                    : PrepareStructuredIdentityMapEachCall
                            .moduleInitialization(prepared);
        }
    }

    @Operation
    public static final class StructuredLocalEachHasNext {
        @Specialization
        public static boolean perform(PreparedLocalEachCall prepared) {
            return prepared.hasNext();
        }
    }

    @Operation
    public static final class PrepareStructuredLocalEachChildCall {
        @Specialization
        public static PreparedClosureCall perform(PreparedLocalEachCall prepared) {
            return prepared.prepareCurrent();
        }
    }

    @Operation
    public static final class PrepareInlineStructuredLocalEachChildCall {
        @Specialization
        public static PreparedInlineLiteralCall perform(PreparedLocalEachCall prepared) {
            return prepared.prepareInlineCurrent();
        }
    }

    @Operation
    public static final class AdvanceStructuredLocalEach {
        @Specialization
        public static void perform(PreparedLocalEachCall prepared) {
            prepared.advance();
        }
    }

    @Operation
    public static final class FinishStructuredLocalEach {
        @Specialization
        public static Object perform(PreparedLocalEachCall prepared) {
            return prepared.finish();
        }
    }

    @Operation
    public static final class AdmitsInlineLiteralLocalEachChild {
        @Specialization
        public static boolean perform(
                PreparedLocalEachCall prepared,
                PreparedInlineLiteralCall child,
                Object literal,
                ProtosClosureExecutionPlanCell literalPlan) {
            return prepared.admitsInlineLiteralChild(child, literal, literalPlan);
        }
    }

    /**
     * The callback's fresh semantic activation, materialized from the region's
     * carrier by the first operation that needs it and shared, by identity,
     * with every later operation and tooling query of the same invocation.
     */
    @Operation
    public static final class LoadInlineCallbackActivation {
        @Specialization
        public static ProtosActivation perform(PreparedInlineLiteralCall child) {
            return child.activation();
        }
    }

    @Operation
    public static final class LoadInlineLiteralFallbackCall {
        @Specialization
        public static PreparedClosureCall perform(PreparedInlineLiteralCall child) {
            return child.fallbackCall();
        }
    }

    @Operation
    public static final class CompleteClosureCall {
        /*
         * PERF038-H: completion has no observable effect when this call
         * owns no materialized ReturnHome. Avoid entering the general
         * completion machinery on the pay-as-you-grow path.
         */
        @Specialization(guards = "!prepared.hasReturnHomeLifecycle()")
        public static void ordinaryWithoutHomeLifecycle(
                OrdinarySourceCall prepared) {
            // No owned materialized ReturnHome to complete.
        }

        @Specialization(guards = "prepared.hasReturnHomeLifecycle()")
        public static void ordinary(OrdinarySourceCall prepared) {
            prepared.complete();
        }


        @Specialization
        public static void nativeCall(NativeCall prepared) {
            prepared.complete();
        }

        @Specialization
        public static void immediate(ImmediateResultCall prepared) {
            prepared.complete();
        }

        @Specialization
        public static void moduleInitialization(ModuleInitializationCall prepared) {
            prepared.complete();
        }
    }

    /**
     * PLAT042 B′ structured-dispatch boundary: one helper CallTarget per
     * structured invocation. The Task C-prime entry plan is cached per entered
     * Context, so its target is stable for a Context; a call site seen by
     * several Contexts falls back to an indirect call. Control-transfer,
     * module-initialization and runtime-failure mapping is the same as for an
     * ordinary prepared call. TEST009-J: emitted only inside
     * {@link RequiresStructuredDispatch}, so the operand is always a
     * structured {@link NativeCall}.
     */
    @Operation
    public static final class EnterNestedStructuredDispatch {
        @Specialization(guards = "structuredDispatchTarget() == cachedTarget", excludeForUncached = true)
        public static Object direct(
                NativeCall prepared,
                @Cached(value = "structuredDispatchTarget()", neverDefault = true) RootCallTarget cachedTarget,
                @Cached("create(cachedTarget)") DirectCallNode node) {
            try {
                return node.call(prepared.activation(), prepared);
            } catch (ProtosBytecodeControlTransferException bridged) {
                return prepared.handleControlTransfer(bridged.transfer());
            } catch (AbstractTruffleException transfer) {
                prepared.failIfModuleInitialization();
                throw transfer;
            } catch (RuntimeException failure) {
                throw prepared.mapRuntimeFailure(failure);
            }
        }

        @Specialization(replaces = "direct")
        public static Object indirect(
                NativeCall prepared,
                @Cached IndirectCallNode node) {
            return EnterNestedStructuredDispatch.perform(prepared, node);
        }

        @NonIdempotent
        static RootCallTarget structuredDispatchTarget() {
            return ProtosTaskCPrimeEntryExecution.planForEnteredContext().target();
        }

        public static Object perform(
                NativeCall prepared,
                IndirectCallNode node) {
            RootCallTarget target =
                    ProtosTaskCPrimeEntryExecution
                            .planForEnteredContext()
                            .target();
            try {
                return node.call(
                        target,
                        prepared.activation(),
                        prepared);
            } catch (ProtosBytecodeControlTransferException bridged) {
                return prepared.handleControlTransfer(bridged.transfer());
            } catch (AbstractTruffleException transfer) {
                prepared.failIfModuleInitialization();
                throw transfer;
            } catch (RuntimeException failure) {
                throw prepared.mapRuntimeFailure(failure);
            }
        }
}

    /**
     * PERF038-H: straight-line execution is permitted only for the
     * selected source entry, not by selector spelling or receiver type.
     *
     * The proof excludes suspension and an owned materialized
     * ReturnHome. Unknown and native calls preserve the full path.
     */
    @Operation
    public static final class AdmitsStraightLineSourceCall {
        @Specialization
        public static boolean perform(OrdinarySourceCall prepared) {
            return prepared.provablyNonSuspending()
                    && !prepared.hasReturnHomeLifecycle();
        }

        @Specialization
        public static boolean nativeCall(NativeCall prepared) {
            return false;
        }

        @Specialization
        public static boolean immediate(ImmediateResultCall prepared) {
            return false;
        }

        @Specialization
        public static boolean moduleInitialization(
                ModuleInitializationCall prepared) {
            return false;
        }
    }

    /**
     * I108-A: admission and entry of a prepared ordinary source
     * invocation are one semantic operation. Proven non-suspending
     * entries execute directly. Other entries return their existing
     * PreparedClosureCall for Bytecode DSL composition.
     *
     * The enclosing TryFinally remains authoritative for Error,
     * cancellation and cleanup. Existing direct/indirect invocation
     * mechanisms and their exception bridge are reused.
     */
    @Operation
    public static final class EnterStraightLineSourceCall {
        /*
         * I108-A: the semantic operation owns admission and entry.
         * A call that needs Bytecode DSL control composition is
         * returned unchanged, without entering its target.
         *
         * Positive and negative specializations remain disjoint,
         * so generic -> direct -> generic transitions stay valid.
         */
        @Specialization(
                guards = {
                    "admitted(prepared)",
                    "prepared.bodyTarget() == cachedTarget"
                },
                limit = "3")
        public static Object ordinaryDirect(
                OrdinarySourceCall prepared,
                @Cached("prepared.bodyTarget()")
                        RootCallTarget cachedTarget,
                @Cached("create(cachedTarget)")
                        DirectCallNode node) {
            return ProtosNumericValueSupport.guestValue(
                    EnterClosureCall.ordinaryDirect(
                            prepared, cachedTarget, node));
        }

        @Specialization(
                replaces = "ordinaryDirect",
                guards = "admitted(prepared)")
        public static Object ordinaryIndirect(
                OrdinarySourceCall prepared,
                @Cached IndirectCallNode node) {
            return ProtosNumericValueSupport.guestValue(
                    EnterClosureCall.ordinaryIndirect(
                            prepared, node));
        }

        @Specialization(guards = "!admitted(prepared)")
        public static Object ordinaryRequiresComposition(
                OrdinarySourceCall prepared) {
            return prepared;
        }

        @Specialization
        public static Object nativeCall(NativeCall prepared) {
            return prepared;
        }

        @Specialization
        public static Object immediate(ImmediateResultCall prepared) {
            return prepared;
        }

        @Specialization
        public static Object moduleInitialization(
                ModuleInitializationCall prepared) {
            return prepared;
        }

        static boolean admitted(OrdinarySourceCall prepared) {
            return prepared.provablyNonSuspending()
                    && !prepared.hasReturnHomeLifecycle();
        }
    }

    /**
     * PERF038-H: source-backed Send1 fast entry.
     *
     * Both argument evaluation and the D013 member-selection guards
     * remain authoritative. The selected Closure must have a
     * statically proven non-suspending source body, an entry matching
     * the supplied arity, and no physical ReturnHome lifecycle.
     *
     * Every other selection returns MISS without invoking the guest.
     * The lowerer then runs the original prepared-call path using
     * the already evaluated operands.
     */
    /**
     * PERF038-H: fused zero-argument ordinary source-method send.
     *
     * Lookup identity, selected method home, entered Context,
     * prelude and invalidation are guarded exactly as in Send1.
     * The selected source root must be proven non-suspending and
     * must require no physical ReturnHome completion.
     */
    @Operation
    public static final class TryDirectSendZero {
        @Specialization(
                guards = {
                    "!isForeignReceiver(receiver)",
                    "receiver == cachedReceiver",
                    "selector.equals(cachedSelector)",
                    "enteredContext != null",
                    "enteredContext == cachedContext",
                    "prelude == cachedPrelude",
                    "cachedSend != null",
                    "admitted(cachedSend)"
                },
                assumptions = "cachedSend.stability()",
                limit = "3")
        public static Object guardedSource(
                Object receiver,
                String selector,
                Object caller,
                @Bind("currentEnteredContext($node)")
                        ProtosLanguageContext enteredContext,
                @Bind("callerPrelude(caller)")
                        ProtosPrelude prelude,
                @Cached("receiver") Object cachedReceiver,
                @Cached("selector") String cachedSelector,
                @Cached("enteredContext")
                        ProtosLanguageContext cachedContext,
                @Cached("prelude") ProtosPrelude cachedPrelude,
                @Cached("createGuardedSendForPrelude(receiver, selector, prelude, enteredContext)")
                        GuardedSendTarget cachedSend,
                @Cached("create(cachedSend.target())")
                        DirectCallNode node) {
            ProtosActivation exactCaller =
                    PrepareSendArguments.exactCaller(caller);

            Object[] frameArguments =
                    ProtosFrameArguments
                            .compactImmediateMethodCallZeroNoHome(
                                    cachedSend.closure(),
                                    receiver,
                                    cachedSend.methodHome(),
                                    exactCaller);

            try {
                return ProtosNumericValueSupport.guestValue(
                        node.call(frameArguments));
            } catch (ProtosBytecodeControlTransferException bridged) {
                /*
                 * The admission excludes ownership of a materialized
                 * ReturnHome, so no transfer can be consumed here.
                 */
                throw bridged.transfer();
            }
        }

        @Specialization(replaces = "guardedSource")
        public static Object generic(
                Object receiver,
                String selector,
                Object caller) {
            return TryDirectSendOne.MISS;
        }

        static boolean admitted(GuardedSendTarget send) {
            return TryDirectSendOne.admittedForArity(send, 0);
        }

        static GuardedSendTarget createGuardedSendForPrelude(
                Object receiver,
                String selector,
                ProtosPrelude prelude,
                ProtosLanguageContext enteredContext) {
            return TryDirectSendOne.createGuardedSendForPrelude(
                    receiver, selector, prelude, enteredContext);
        }

        static ProtosPrelude callerPrelude(Object caller) {
            return TryDirectSendOne.callerPrelude(caller);
        }

        static boolean isForeignReceiver(Object receiver) {
            return TryDirectSendOne.isForeignReceiver(receiver);
        }

        @NonIdempotent
        static ProtosLanguageContext currentEnteredContext(Node node) {
            return ProtosLanguageContext.current(node);
        }
    }


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

    /**
     * I091: the implicit final value of a source root. A primitive numeric
     * carrier is returned as is only to a fused direct source send that
     * accepts it; every other caller receives the guest value.
     */
    @Operation
    public static final class MaterializeReturnCarrier {
        @Specialization
        public static Object perform(Object value, @Bind VirtualFrame frame) {
            if (ProtosNumericValueSupport.isPrimitiveCarrier(value)
                    && ProtosFrameArguments.acceptsPrimitiveResult(frame.getArguments())) {
                return value;
            }
            return ProtosNumericValueSupport.guestValue(value);
        }
    }

    @Operation
    public static final class IsPrimitiveNumericSendMiss {
        @Specialization
        public static boolean perform(Object value) {
            return value == ProtosPrimitiveNumericSendFastPathsBase.MISS;
        }
    }

    @Operation
    public static final class TryDirectSendOne {
        private static final Object MISS = new Object();

        @Specialization(
                guards = {
                    "!isForeignReceiver(receiver)",
                    "receiver == cachedReceiver",
                    "selector.equals(cachedSelector)",
                    "enteredContext != null",
                    "enteredContext == cachedContext",
                    "prelude == cachedPrelude",
                    "cachedSend != null",
                    "admitted(cachedSend)"
                },
                assumptions = "cachedSend.stability()",
                limit = "3")
        public static Object guardedSource(
                Object receiver,
                String selector,
                Object caller,
                Object supplied0,
                @Bind("currentEnteredContext($node)")
                        ProtosLanguageContext enteredContext,
                @Bind("callerPrelude(caller)")
                        ProtosPrelude prelude,
                @Cached("receiver") Object cachedReceiver,
                @Cached("selector") String cachedSelector,
                @Cached("enteredContext")
                        ProtosLanguageContext cachedContext,
                @Cached("prelude") ProtosPrelude cachedPrelude,
                @Cached("createGuardedSendForPrelude(receiver, selector, prelude, enteredContext)")
                        GuardedSendTarget cachedSend,
                @Cached("create(cachedSend.target())")
                        DirectCallNode node) {
            ProtosActivation exactCaller =
                    PrepareSendArguments.exactCaller(caller);

            Object[] frameArguments =
                    ProtosFrameArguments.compactImmediateMethodCallOne(
                            cachedSend.closure(),
                            receiver,
                            cachedSend.methodHome(),
                            exactCaller,
                            /*
                             * I091: the admitted home is unobservable; this
                             * variant also lets the callee return a primitive
                             * numeric carrier, which the lowered call site
                             * consumes or materializes.
                             */
                            ProtosReturnHome.unobservableAcceptingPrimitiveResultForRuntime(),
                            supplied0);

            try {
                return node.call(frameArguments);
            } catch (ProtosBytecodeControlTransferException bridged) {
                /*
                 * Exactly the no-home OrdinarySourceCall bridge:
                 * there is no owned materialized home able to
                 * consume this control transfer.
                 */
                throw bridged.transfer();
            }
        }

        @Specialization(replaces = "guardedSource")
        public static Object generic(
                Object receiver,
                String selector,
                Object caller,
                Object supplied0) {
            return MISS;
        }

        static boolean admitted(GuardedSendTarget send) {
            return admittedForArity(send, 1);
        }

        static boolean admittedForArity(
                GuardedSendTarget send, int suppliedArgumentCount) {
            if (send == null
                    || send.freshInvocationHome()
                    || send.sharedInvocationHome()
                            != ProtosReturnHome.unobservable()) {
                return false;
            }

            RootCallTarget target = send.target();
            return target != null
                    && target.getRootNode()
                            instanceof ProtosBytecodeRootNode root
                    && root.provablyNonSuspendingBody();
        }

        static GuardedSendTarget createGuardedSendForPrelude(
                Object receiver,
                String selector,
                ProtosPrelude prelude,
                ProtosLanguageContext enteredContext) {
            return PrepareSendArguments
                    .createGuardedSendForPrelude(
                            receiver, selector, prelude, enteredContext);
        }

        static ProtosPrelude callerPrelude(Object caller) {
            return PrepareSendArguments.callerPrelude(caller);
        }

        static boolean isForeignReceiver(Object receiver) {
            return PrepareSendArguments
                    .isForeignReceiver(receiver);
        }

        @NonIdempotent
        static ProtosLanguageContext currentEnteredContext(Node node) {
            return ProtosLanguageContext.current(node);
        }
    }

    /**
     * A failed fused attempt delegates to the original prepared
     * invocation. The marker cannot collide with a Protos result.
     */
    /**
     * PERF038-H: direct Call0 of a proven straight-line source
     * Closure, without constructing an OrdinarySourceCall carrier.
     */
    @Operation
    public static final class InvokeClosureZero {
        @Specialization(
                excludeForUncached = true,
                guards = {
                    "receiver == cachedReceiver",
                    "enteredContext != null",
                    "enteredContext == cachedContext",
                    "prelude == cachedPrelude",
                    "cachedGuarded != null",
                    "admitted(cachedGuarded, 0)"
                },
                assumptions = "cachedGuarded.stability()",
                limit = "3")
        public static Object guardedSource(
                Object receiver,
                Object caller,
                @Bind("currentEnteredContext($node)")
                        ProtosLanguageContext enteredContext,
                @Bind("callerPrelude(caller)")
                        ProtosPrelude prelude,
                @Cached("receiver") Object cachedReceiver,
                @Cached("enteredContext")
                        ProtosLanguageContext cachedContext,
                @Cached("prelude") ProtosPrelude cachedPrelude,
                @Cached("createGuardedDirectClosureCallForPrelude(receiver, prelude, enteredContext)")
                        GuardedDirectClosureCallTarget cachedGuarded,
                @Cached("create(cachedGuarded.target())")
                        DirectCallNode node) {
            ProtosActivation exactCaller =
                    PrepareSendArguments.exactCaller(caller);

            Object[] frameArguments =
                    ProtosFrameArguments
                            .compactDirectClosureCallZeroNoHome(
                                    cachedGuarded.closure(),
                                    exactCaller);

            try {
                return ProtosNumericValueSupport.guestValue(
                        node.call(frameArguments));
            } catch (ProtosBytecodeControlTransferException bridged) {
                throw bridged.transfer();
            }
        }

        /**
         * PERF042: second tier keyed by the stable {@link CanonicalClosure}
         * definition, the receiver's prepared plan template, its
         * Context-projection requirement and the entered Context: exactly the
         * inputs from which {@code fastOrdinarySendTarget} derives the
         * Context-owned target, so the cached target is the one the ordinary
         * path would select for the current instance. Only those keys, the
         * target and target-level admission are cached; the current Closure
         * is always the one entered. Per-instance facts (the ordinary
         * {@code call} selection and the unobservable ReturnHome) are
         * re-proved on every hit, and a rejected instance answers the miss
         * marker, so neither fresh instances nor one overridden instance
         * degrade the site.
         */
        @Specialization(
                excludeForUncached = true,
                guards = {
                    "closureDefinition != null",
                    "closureDefinition == cachedClosureDefinition",
                    "sourcePlan != null",
                    "sourcePlan == cachedSourcePlan",
                    "enteredContext != null",
                    "enteredContext == cachedContext",
                    "projectionRequired == cachedProjectionRequired",
                    "cachedTarget != null",
                    "cachedAdmitted"
                },
                limit = "3")
        public static Object definitionSource(
                Object receiver,
                Object caller,
                @Bind("sourceDefinitionOrNull(receiver)")
                        CanonicalClosure closureDefinition,
                @Bind("currentEnteredContext($node)")
                        ProtosLanguageContext enteredContext,
                @Bind("sourcePlanOrNull(receiver)")
                        ProtosClosureExecutionPlan sourcePlan,
                @Bind("requiresContextProjection(receiver)")
                        boolean projectionRequired,
                @Cached("closureDefinition")
                        CanonicalClosure cachedClosureDefinition,
                @Cached("enteredContext")
                        ProtosLanguageContext cachedContext,
                @Cached("sourcePlan")
                        ProtosClosureExecutionPlan cachedSourcePlan,
                @Cached("projectionRequired")
                        boolean cachedProjectionRequired,
                @Cached("definitionTarget(receiver, caller, enteredContext)")
                        RootCallTarget cachedTarget,
                @Cached("admittedTarget(cachedTarget, 0)")
                        boolean cachedAdmitted,
                @Cached("createDirectCallNodeOrNull(cachedTarget)")
                        DirectCallNode node) {
            ProtosClosureValue closure =
                    admittedInstanceOrNull(receiver, caller);
            if (closure == null) {
                return prepareFallback(receiver, caller);
            }
            ProtosActivation exactCaller =
                    PrepareSendArguments.exactCaller(caller);

            Object[] frameArguments =
                    ProtosFrameArguments
                            .compactDirectClosureCallZeroNoHome(
                                    closure,
                                    exactCaller);

            try {
                return ProtosNumericValueSupport.guestValue(
                        node.call(frameArguments));
            } catch (ProtosBytecodeControlTransferException bridged) {
                throw bridged.transfer();
            }
        }

        /**
         * PERF042: a source Closure that currently cannot take the fused path
         * (overridden {@code call}, observable ReturnHome, or a plan not yet
         * prepared) misses without activating {@link #generic}, so the
         * cached tiers keep admitting later instances.
         */
        @Specialization(
                guards = "temporarilyRejectedInstance(receiver, caller)")
        public static Object rejectedSource(Object receiver, Object caller) {
            return prepareFallback(receiver, caller);
        }

        @Specialization
        public static Object generic(Object receiver, Object caller) {
            return prepareFallback(receiver, caller);
        }

        /*
         * I108-A: an unadmitted Call0 prepares the real ordinary
         * invocation here, rather than returning a miss marker to
         * trigger another selection in the caller's bytecode.
         */
        private static PreparedClosureCall prepareFallback(
                Object receiver, Object caller) {
            return PrepareClosureCall.perform(
                    receiver, PrepareSendArguments.exactCaller(caller));
        }

        static CanonicalClosure sourceDefinitionOrNull(Object receiver) {
            return receiver instanceof ProtosClosureValue closure
                            && closure.nativeBody().isEmpty()
                    ? closure.definition()
                    : null;
        }

        static ProtosClosureExecutionPlan sourcePlanOrNull(
                Object receiver) {
            return receiver instanceof ProtosClosureValue closure
                            && closure.nativeBody().isEmpty()
                    ? closure.executionPlan().orElse(null)
                    : null;
        }

        static boolean requiresContextProjection(Object receiver) {
            return receiver instanceof ProtosClosureValue closure
                    && closure.requiresContextLocalExecutionProjectionForRuntime();
        }

        static DirectCallNode createDirectCallNodeOrNull(
                RootCallTarget target) {
            return target == null ? null : DirectCallNode.create(target);
        }

        @NonIdempotent
        static boolean temporarilyRejectedInstance(
                Object receiver, Object caller) {
            return sourceDefinitionOrNull(receiver) != null
                    && admittedInstanceOrNull(receiver, caller) == null;
        }

        /**
         * Runs only while populating a definition-tier entry. The instance's
         * ordinary {@code call} selection and ReturnHome are proved, and its
         * plan must already be prepared, before any target is derived: a
         * deferred plan is never rematerialized here and an overridden
         * {@code call} never reaches plan or projection work.
         */
        static RootCallTarget definitionTarget(
                Object receiver,
                Object caller,
                ProtosLanguageContext enteredContext) {
            if (enteredContext == null
                    || !(receiver instanceof ProtosClosureValue closure)
                    || closure.executionPlan().isEmpty()
                    || admittedInstanceOrNull(receiver, caller) != closure) {
                return null;
            }
            return PrepareSendArguments
                    .fastOrdinarySendTarget(closure, enteredContext);
        }

        /**
         * Target-level half of {@link #admitted}: depends only on the
         * cached target, so it is proved once per cache entry.
         */
        static boolean admittedTarget(
                RootCallTarget target, int suppliedArgumentCount) {
            return target != null
                    && target.getRootNode()
                            instanceof ProtosBytecodeRootNode root
                    && root.provablyNonSuspendingBody();
        }

        /**
         * Instance-level half of {@link #admitted}, re-proved on every
         * definition-tier hit: the receiver's current ordinary {@code call}
         * selection is the canonical standard one, and this exact Closure
         * proves the unobservable ReturnHome marker.
         */
        static ProtosClosureValue admittedInstanceOrNull(
                Object receiver, Object caller) {
            ProtosClosureValue closure =
                    ProtosClosureCallPreparation.directClosureCallSelectionForPreludeOrNull(
                                    receiver, callerPrelude(caller));
            if (closure == null
                    || !closure.hasUnobservableInvocationReturnHomeForRuntime()) {
                return null;
            }
            return closure;
        }

        static boolean admitted(
                GuardedDirectClosureCallTarget guarded,
                int suppliedArgumentCount) {
            if (guarded == null
                    || !(guarded.target().getRootNode()
                            instanceof ProtosBytecodeRootNode root)
                    || !root.provablyNonSuspendingBody()) {
                return false;
            }

            /*
             * No physical ReturnHome is discarded. The exact Closure
             * must independently prove the unobservable marker.
             */
            return guarded.closure()
                    .hasUnobservableInvocationReturnHomeForRuntime();
        }

        static GuardedDirectClosureCallTarget
                createGuardedDirectClosureCallForPrelude(
                        Object receiver,
                        ProtosPrelude prelude,
                        ProtosLanguageContext enteredContext) {
            return ProtosClosureCallPreparation.createGuardedDirectClosureCallForPrelude(
                            receiver, prelude, enteredContext);
        }

        static ProtosPrelude callerPrelude(Object caller) {
            return PrepareSendArguments.callerPrelude(caller);
        }

        @NonIdempotent
        static ProtosLanguageContext currentEnteredContext(Node node) {
            return ProtosLanguageContext.current(node);
        }
    }

    /**
     * PERF038-H: scalar direct Call1 with the same canonical
     * selection and execution-capability proof as Call0.
     */
    @Operation
    public static final class InvokeClosureOne {
        @Specialization(
                excludeForUncached = true,
                guards = {
                    "receiver == cachedReceiver",
                    "enteredContext != null",
                    "enteredContext == cachedContext",
                    "prelude == cachedPrelude",
                    "cachedGuarded != null",
                    "admitted(cachedGuarded)"
                },
                assumptions = "cachedGuarded.stability()",
                limit = "3")
        public static Object guardedSource(
                Object receiver,
                Object caller,
                Object supplied0,
                @Bind("currentEnteredContext($node)")
                        ProtosLanguageContext enteredContext,
                @Bind("callerPrelude(caller)")
                        ProtosPrelude prelude,
                @Cached("receiver") Object cachedReceiver,
                @Cached("enteredContext")
                        ProtosLanguageContext cachedContext,
                @Cached("prelude") ProtosPrelude cachedPrelude,
                @Cached("createGuardedDirectClosureCallForPrelude(receiver, prelude, enteredContext)")
                        GuardedDirectClosureCallTarget cachedGuarded,
                @Cached("create(cachedGuarded.target())")
                        DirectCallNode node) {
            ProtosActivation exactCaller =
                    PrepareSendArguments.exactCaller(caller);

            Object[] frameArguments =
                    ProtosFrameArguments
                            .compactDirectClosureCallOneNoHome(
                                    cachedGuarded.closure(),
                                    exactCaller,
                                    supplied0);

            try {
                return ProtosNumericValueSupport.guestValue(
                        node.call(frameArguments));
            } catch (ProtosBytecodeControlTransferException bridged) {
                throw bridged.transfer();
            }
        }

        /** PERF042: the Call1 counterpart of {@link InvokeClosureZero#definitionSource}. */
        @Specialization(
                excludeForUncached = true,
                guards = {
                    "closureDefinition != null",
                    "closureDefinition == cachedClosureDefinition",
                    "sourcePlan != null",
                    "sourcePlan == cachedSourcePlan",
                    "enteredContext != null",
                    "enteredContext == cachedContext",
                    "projectionRequired == cachedProjectionRequired",
                    "cachedTarget != null",
                    "cachedAdmitted"
                },
                limit = "3")
        public static Object definitionSource(
                Object receiver,
                Object caller,
                Object supplied0,
                @Bind("sourceDefinitionOrNull(receiver)")
                        CanonicalClosure closureDefinition,
                @Bind("currentEnteredContext($node)")
                        ProtosLanguageContext enteredContext,
                @Bind("sourcePlanOrNull(receiver)")
                        ProtosClosureExecutionPlan sourcePlan,
                @Bind("requiresContextProjection(receiver)")
                        boolean projectionRequired,
                @Cached("closureDefinition")
                        CanonicalClosure cachedClosureDefinition,
                @Cached("enteredContext")
                        ProtosLanguageContext cachedContext,
                @Cached("sourcePlan")
                        ProtosClosureExecutionPlan cachedSourcePlan,
                @Cached("projectionRequired")
                        boolean cachedProjectionRequired,
                @Cached("definitionTarget(receiver, caller, enteredContext)")
                        RootCallTarget cachedTarget,
                @Cached("admittedTarget(cachedTarget, 1)")
                        boolean cachedAdmitted,
                @Cached("createDirectCallNodeOrNull(cachedTarget)")
                        DirectCallNode node) {
            ProtosClosureValue closure =
                    InvokeClosureZero.admittedInstanceOrNull(receiver, caller);
            if (closure == null) {
                return prepareFallback(receiver, caller, supplied0);
            }
            ProtosActivation exactCaller =
                    PrepareSendArguments.exactCaller(caller);

            Object[] frameArguments =
                    ProtosFrameArguments
                            .compactDirectClosureCallOneNoHome(
                                    closure,
                                    exactCaller,
                                    supplied0);

            try {
                return ProtosNumericValueSupport.guestValue(
                        node.call(frameArguments));
            } catch (ProtosBytecodeControlTransferException bridged) {
                throw bridged.transfer();
            }
        }

        /** PERF042: see {@link InvokeClosureZero#rejectedSource}. */
        @Specialization(
                guards = "temporarilyRejectedInstance(receiver, caller)")
        public static Object rejectedSource(Object receiver, Object caller, Object supplied0) {
            return prepareFallback(receiver, caller, supplied0);
        }

        @Specialization
        public static Object generic(
                Object receiver,
                Object caller,
                Object supplied0) {
            return prepareFallback(receiver, caller, supplied0);
        }

        /*
         * I108-A: the semantic operation owns generic selection and
         * preparation. A primitive argument carrier must be projected
         * before the ordinary D013 selection/binding protocol sees it.
         */
        private static PreparedClosureCall prepareFallback(
                Object receiver, Object caller, Object supplied0) {
            return ProtosClosureCallPreparation.prepareClosureCallOneFallback(
                    receiver,
                    PrepareSendArguments.exactCaller(caller),
                    ProtosNumericValueSupport.guestValue(supplied0));
        }

        static CanonicalClosure sourceDefinitionOrNull(Object receiver) {
            return InvokeClosureZero.sourceDefinitionOrNull(receiver);
        }

        static ProtosClosureExecutionPlan sourcePlanOrNull(
                Object receiver) {
            return InvokeClosureZero.sourcePlanOrNull(receiver);
        }

        static boolean requiresContextProjection(Object receiver) {
            return InvokeClosureZero.requiresContextProjection(receiver);
        }

        static DirectCallNode createDirectCallNodeOrNull(
                RootCallTarget target) {
            return InvokeClosureZero.createDirectCallNodeOrNull(target);
        }

        @NonIdempotent
        static boolean temporarilyRejectedInstance(
                Object receiver, Object caller) {
            return InvokeClosureZero.temporarilyRejectedInstance(
                    receiver, caller);
        }

        static RootCallTarget definitionTarget(
                Object receiver,
                Object caller,
                ProtosLanguageContext enteredContext) {
            return InvokeClosureZero.definitionTarget(
                    receiver, caller, enteredContext);
        }

        static boolean admittedTarget(
                RootCallTarget target, int suppliedArgumentCount) {
            return InvokeClosureZero.admittedTarget(
                    target, suppliedArgumentCount);
        }

        static boolean admitted(
                GuardedDirectClosureCallTarget guarded) {
            return InvokeClosureZero.admitted(guarded, 1);
        }

        static GuardedDirectClosureCallTarget
                createGuardedDirectClosureCallForPrelude(
                        Object receiver,
                        ProtosPrelude prelude,
                        ProtosLanguageContext enteredContext) {
            return InvokeClosureZero
                    .createGuardedDirectClosureCallForPrelude(
                            receiver, prelude, enteredContext);
        }

        static ProtosPrelude callerPrelude(Object caller) {
            return InvokeClosureZero.callerPrelude(caller);
        }

        @NonIdempotent
        static ProtosLanguageContext currentEnteredContext(Node node) {
            return ProtosLanguageContext.current(node);
        }
    }

    /**
     * I112 / PLAT062-T terminal Call1: the final expression of a genuine
     * source root, after which the root only returns. Its two operands are
     * the receiver and the single supplied value, evaluated once, left to
     * right; the caller is this root's own frame argument 0. D013 selection
     * of {@code call} happens here, after both operands.
     *
     * <p>The caches and guards are those of {@link InvokeClosureOne}, except
     * that admission does not require a non-suspending callee: a terminal
     * caller has no guest work left, so a suspended callee is returned as a
     * {@link ProtosTailCallContinuation} that retains its pending state and
     * the owner of its completion. A synchronous result is returned as the
     * guest value without any allocation. Every other selection (overridden
     * {@code call}, native body, observable ReturnHome, foreign target or
     * Context) is prepared and executed inside this operation; a prepared
     * call never reaches the caller root.
     */
    @Operation
    public static final class InvokeClosureOneTail {
        @Specialization(
                excludeForUncached = true,
                guards = {
                    "receiver == cachedReceiver",
                    "enteredContext != null",
                    "enteredContext == cachedContext",
                    "prelude == cachedPrelude",
                    "cachedGuarded != null",
                    "admittedTail(cachedGuarded)"
                },
                assumptions = "cachedGuarded.stability()",
                limit = "3")
        public static Object guardedSource(
                Object receiver,
                Object supplied0,
                @Bind VirtualFrame frame,
                @Bind BytecodeNode bytecodeNode,
                @Bind("$bytecodeIndex") int bytecodeIndex,
                @Bind("callerReference(frame)") Object caller,
                @Bind("currentEnteredContext($node)")
                        ProtosLanguageContext enteredContext,
                @Bind("callerPrelude(caller)")
                        ProtosPrelude prelude,
                @Cached("receiver") Object cachedReceiver,
                @Cached("enteredContext")
                        ProtosLanguageContext cachedContext,
                @Cached("prelude") ProtosPrelude cachedPrelude,
                @Cached("createGuardedDirectClosureCallForPrelude(receiver, prelude, enteredContext)")
                        GuardedDirectClosureCallTarget cachedGuarded,
                @Cached("create(cachedGuarded.target())")
                        DirectCallNode node) {
            return enterDirect(
                    bytecodeNode,
                    bytecodeIndex,
                    node,
                    ProtosFrameArguments.compactDirectClosureCallOneNoHome(
                            cachedGuarded.closure(),
                            PrepareSendArguments.exactCaller(caller),
                            supplied0));
        }

        /**
         * The terminal counterpart of {@link InvokeClosureOne#definitionSource}.
         * I114: admission of the current instance is a DSL guard evaluated
         * before any cache initializer, so when this body runs the direct
         * decision is already made. A rejected instance falls through to
         * {@link #rejectedSource} without consuming a cache entry, and a
         * restored instance re-enters an existing entry. The entered Closure is
         * always {@code admittedClosure}, never one cached by an entry.
         */
        @Specialization(
                excludeForUncached = true,
                guards = {
                    "admittedClosure != null",
                    "closureDefinition != null",
                    "closureDefinition == cachedClosureDefinition",
                    "sourcePlan != null",
                    "sourcePlan == cachedSourcePlan",
                    "enteredContext != null",
                    "enteredContext == cachedContext",
                    "projectionRequired == cachedProjectionRequired",
                    "cachedTarget != null",
                    "cachedAdmitted"
                },
                limit = "3")
        public static Object definitionSource(
                Object receiver,
                Object supplied0,
                @Bind VirtualFrame frame,
                @Bind BytecodeNode bytecodeNode,
                @Bind("$bytecodeIndex") int bytecodeIndex,
                @Bind("callerReference(frame)") Object caller,
                @Bind("admittedInstanceOrNull(receiver, caller)")
                        ProtosClosureValue admittedClosure,
                @Bind("sourceDefinitionOrNull(receiver)")
                        CanonicalClosure closureDefinition,
                @Bind("currentEnteredContext($node)")
                        ProtosLanguageContext enteredContext,
                @Bind("sourcePlanOrNull(receiver)")
                        ProtosClosureExecutionPlan sourcePlan,
                @Bind("requiresContextProjection(receiver)")
                        boolean projectionRequired,
                @Cached("closureDefinition")
                        CanonicalClosure cachedClosureDefinition,
                @Cached("enteredContext")
                        ProtosLanguageContext cachedContext,
                @Cached("sourcePlan")
                        ProtosClosureExecutionPlan cachedSourcePlan,
                @Cached("projectionRequired")
                        boolean cachedProjectionRequired,
                @Cached("admittedDefinitionTarget(admittedClosure, enteredContext)")
                        RootCallTarget cachedTarget,
                @Cached("admittedTailTarget(cachedTarget)")
                        boolean cachedAdmitted,
                @Cached("createDirectCallNodeOrNull(cachedTarget)")
                        DirectCallNode node) {
            ProtosActivation exactCaller =
                    PrepareSendArguments.exactCaller(caller);

            Object[] frameArguments =
                    ProtosFrameArguments.compactDirectClosureCallOneNoHome(
                            admittedClosure,
                            exactCaller,
                            supplied0);

            return enterDirect(
                    bytecodeNode,
                    bytecodeIndex,
                    node,
                    frameArguments);
        }

        /** See {@link InvokeClosureZero#rejectedSource}. */
        @Specialization(
                guards = "temporarilyRejectedInstance(receiver, caller)")
        @InliningCutoff
        public static Object rejectedSource(
                Object receiver,
                Object supplied0,
                @Bind VirtualFrame frame,
                @Bind BytecodeNode bytecodeNode,
                @Bind("$bytecodeIndex") int bytecodeIndex,
                @Bind("callerReference(frame)") Object caller,
                @Cached IndirectCallNode indirectCall) {
            return prepareAndEnter(
                    receiver, caller, supplied0, indirectCall, bytecodeNode, bytecodeIndex);
        }

        @Specialization
        @InliningCutoff
        public static Object generic(
                Object receiver,
                Object supplied0,
                @Bind VirtualFrame frame,
                @Bind BytecodeNode bytecodeNode,
                @Bind("$bytecodeIndex") int bytecodeIndex,
                @Bind("callerReference(frame)") Object caller,
                @Cached IndirectCallNode indirectCall) {
            return prepareAndEnter(
                    receiver, caller, supplied0, indirectCall, bytecodeNode, bytecodeIndex);
        }

        /*
         * The complete ordinary D013 selection on the already evaluated
         * operands, then the terminal execution of the selected invocation.
         * A primitive carrier operand is projected before selection.
         */
        private static Object prepareAndEnter(
                Object receiver,
                Object caller,
                Object supplied0,
                IndirectCallNode indirectCall,
                BytecodeNode bytecodeNode,
                int bytecodeIndex) {
            return ProtosTailCallContinuation.executeTerminalPreparedCall(
                    ProtosClosureCallPreparation.prepareClosureCallOneFallback(
                            ProtosNumericValueSupport.guestValue(receiver),
                            PrepareSendArguments.exactCaller(caller),
                            ProtosNumericValueSupport.guestValue(supplied0)),
                    indirectCall, bytecodeNode, bytecodeIndex);
        }

        /*
         * The admitted direct entry of every terminal arity: a synchronous
         * result is the projected guest value; a suspended one is retained
         * by one terminal transport without an owning prepared call. I114:
         * only the suspension test stays on the normal path; the transport is
         * built behind the cold deferDirect boundary, never through the
         * owner-aware completeOrDefer tree.
         */
        static Object enterDirect(
                BytecodeNode bytecodeNode,
                int bytecodeIndex,
                DirectCallNode node,
                Object[] frameArguments) {
            Object outcome;
            try {
                outcome = node.call(frameArguments);
            } catch (ProtosBytecodeControlTransferException bridged) {
                throw bridged.transfer();
            }
            java.util.Objects.requireNonNull(outcome, "terminal Closure invocation returned null");
            if (ProtosTailCallContinuation.isSuspended(outcome)) {
                return ProtosTailCallContinuation.deferDirect(
                        outcome, bytecodeNode, bytecodeIndex);
            }
            return ProtosNumericValueSupport.guestValue(outcome);
        }

        /**
         * This root's own caller reference (see {@link CurrentCallerReference}):
         * the published activation, or the still-compact frame arguments.
         */
        @NonIdempotent
        static Object callerReference(VirtualFrame frame) {
            Object[] arguments = frame.getArguments();
            return ProtosFrameArguments.hasPublishedActivation(arguments)
                    ? ProtosFrameArguments.publishedActivation(arguments)
                    : arguments;
        }

        /**
         * {@link InvokeClosureZero#admitted} without the non-suspension
         * requirement: a source entry, and the exact Closure proves the
         * unobservable ReturnHome marker.
         */
        static boolean admittedTail(GuardedDirectClosureCallTarget guarded) {
            return guarded != null
                    && guarded.target().getRootNode()
                            instanceof ProtosBytecodeRootNode
                    && guarded.closure()
                            .hasUnobservableInvocationReturnHomeForRuntime();
        }

        /** {@link InvokeClosureZero#admittedTarget} without the non-suspension requirement. */
        static boolean admittedTailTarget(RootCallTarget target) {
            return target != null
                    && target.getRootNode() instanceof ProtosBytecodeRootNode;
        }

        static CanonicalClosure sourceDefinitionOrNull(Object receiver) {
            return InvokeClosureZero.sourceDefinitionOrNull(receiver);
        }

        static ProtosClosureExecutionPlan sourcePlanOrNull(Object receiver) {
            return InvokeClosureZero.sourcePlanOrNull(receiver);
        }

        static boolean requiresContextProjection(Object receiver) {
            return InvokeClosureZero.requiresContextProjection(receiver);
        }

        static DirectCallNode createDirectCallNodeOrNull(RootCallTarget target) {
            return InvokeClosureZero.createDirectCallNodeOrNull(target);
        }

        @NonIdempotent
        static boolean temporarilyRejectedInstance(Object receiver, Object caller) {
            return InvokeClosureZero.temporarilyRejectedInstance(receiver, caller);
        }

        /** I114: the per-instance admission guard of {@link #definitionSource}. */
        @NonIdempotent
        static ProtosClosureValue admittedInstanceOrNull(
                Object receiver, Object caller) {
            return InvokeClosureZero.admittedInstanceOrNull(receiver, caller);
        }

        /**
         * Runs only while populating a {@link #definitionSource} entry, after
         * the instance guard admitted {@code admittedClosure} and the plan
         * guard proved its plan prepared: admission is not repeated, {@code
         * call} is not looked up again, and no plan is rematerialized.
         */
        static RootCallTarget admittedDefinitionTarget(
                ProtosClosureValue admittedClosure,
                ProtosLanguageContext enteredContext) {
            if (enteredContext == null) {
                return null;
            }
            return PrepareSendArguments
                    .fastOrdinarySendTarget(admittedClosure, enteredContext);
        }

        static GuardedDirectClosureCallTarget
                createGuardedDirectClosureCallForPrelude(
                        Object receiver,
                        ProtosPrelude prelude,
                        ProtosLanguageContext enteredContext) {
            return InvokeClosureZero.createGuardedDirectClosureCallForPrelude(
                    receiver, prelude, enteredContext);
        }

        static ProtosPrelude callerPrelude(Object caller) {
            return InvokeClosureZero.callerPrelude(caller);
        }

        @NonIdempotent
        static ProtosLanguageContext currentEnteredContext(Node node) {
            return ProtosLanguageContext.current(node);
        }
    }

    /** I112 / PLAT062-T terminal Call0; see {@link InvokeClosureOneTail}. */
    @Operation
    public static final class InvokeClosureZeroTail {
        @Specialization(
                excludeForUncached = true,
                guards = {
                    "receiver == cachedReceiver",
                    "enteredContext != null",
                    "enteredContext == cachedContext",
                    "prelude == cachedPrelude",
                    "cachedGuarded != null",
                    "admittedTail(cachedGuarded)"
                },
                assumptions = "cachedGuarded.stability()",
                limit = "3")
        public static Object guardedSource(
                Object receiver,
                @Bind VirtualFrame frame,
                @Bind BytecodeNode bytecodeNode,
                @Bind("$bytecodeIndex") int bytecodeIndex,
                @Bind("callerReference(frame)") Object caller,
                @Bind("currentEnteredContext($node)")
                        ProtosLanguageContext enteredContext,
                @Bind("callerPrelude(caller)")
                        ProtosPrelude prelude,
                @Cached("receiver") Object cachedReceiver,
                @Cached("enteredContext")
                        ProtosLanguageContext cachedContext,
                @Cached("prelude") ProtosPrelude cachedPrelude,
                @Cached("createGuardedDirectClosureCallForPrelude(receiver, prelude, enteredContext)")
                        GuardedDirectClosureCallTarget cachedGuarded,
                @Cached("create(cachedGuarded.target())")
                        DirectCallNode node) {
            return InvokeClosureOneTail.enterDirect(
                    bytecodeNode,
                    bytecodeIndex,
                    node,
                    ProtosFrameArguments.compactDirectClosureCallZeroNoHome(
                            cachedGuarded.closure(),
                            PrepareSendArguments.exactCaller(caller)));
        }

        @Specialization(
                excludeForUncached = true,
                guards = {
                    "closureDefinition != null",
                    "closureDefinition == cachedClosureDefinition",
                    "sourcePlan != null",
                    "sourcePlan == cachedSourcePlan",
                    "enteredContext != null",
                    "enteredContext == cachedContext",
                    "projectionRequired == cachedProjectionRequired",
                    "cachedTarget != null",
                    "cachedAdmitted"
                },
                limit = "3")
        public static Object definitionSource(
                Object receiver,
                @Bind VirtualFrame frame,
                @Bind BytecodeNode bytecodeNode,
                @Bind("$bytecodeIndex") int bytecodeIndex,
                @Bind("callerReference(frame)") Object caller,
                @Bind("sourceDefinitionOrNull(receiver)")
                        CanonicalClosure closureDefinition,
                @Bind("currentEnteredContext($node)")
                        ProtosLanguageContext enteredContext,
                @Bind("sourcePlanOrNull(receiver)")
                        ProtosClosureExecutionPlan sourcePlan,
                @Bind("requiresContextProjection(receiver)")
                        boolean projectionRequired,
                @Cached("closureDefinition")
                        CanonicalClosure cachedClosureDefinition,
                @Cached("enteredContext")
                        ProtosLanguageContext cachedContext,
                @Cached("sourcePlan")
                        ProtosClosureExecutionPlan cachedSourcePlan,
                @Cached("projectionRequired")
                        boolean cachedProjectionRequired,
                @Cached("definitionTarget(receiver, caller, enteredContext)")
                        RootCallTarget cachedTarget,
                @Cached("admittedTailTarget(cachedTarget)")
                        boolean cachedAdmitted,
                @Cached("createDirectCallNodeOrNull(cachedTarget)")
                        DirectCallNode node,
                @Cached IndirectCallNode indirectCall) {
            ProtosClosureValue closure =
                    InvokeClosureZero.admittedInstanceOrNull(receiver, caller);
            if (closure == null) {
                return prepareAndEnter(
                    receiver, caller, indirectCall, bytecodeNode, bytecodeIndex);
            }
            return InvokeClosureOneTail.enterDirect(
                    bytecodeNode,
                    bytecodeIndex,
                    node,
                    ProtosFrameArguments.compactDirectClosureCallZeroNoHome(
                            closure,
                            PrepareSendArguments.exactCaller(caller)));
        }

        @Specialization(
                guards = "temporarilyRejectedInstance(receiver, caller)")
        public static Object rejectedSource(
                Object receiver,
                @Bind VirtualFrame frame,
                @Bind BytecodeNode bytecodeNode,
                @Bind("$bytecodeIndex") int bytecodeIndex,
                @Bind("callerReference(frame)") Object caller,
                @Cached IndirectCallNode indirectCall) {
            return prepareAndEnter(
                    receiver, caller, indirectCall, bytecodeNode, bytecodeIndex);
        }

        @Specialization
        public static Object generic(
                Object receiver,
                @Bind VirtualFrame frame,
                @Bind BytecodeNode bytecodeNode,
                @Bind("$bytecodeIndex") int bytecodeIndex,
                @Bind("callerReference(frame)") Object caller,
                @Cached IndirectCallNode indirectCall) {
            return prepareAndEnter(
                    receiver, caller, indirectCall, bytecodeNode, bytecodeIndex);
        }

        private static Object prepareAndEnter(
                Object receiver,
                Object caller,
                IndirectCallNode indirectCall,
                BytecodeNode bytecodeNode,
                int bytecodeIndex) {
            return ProtosTailCallContinuation.executeTerminalPreparedCall(
                    PrepareClosureCall.perform(
                            ProtosNumericValueSupport.guestValue(receiver),
                            PrepareSendArguments.exactCaller(caller)),
                    indirectCall, bytecodeNode, bytecodeIndex);
        }

        @NonIdempotent
        static Object callerReference(VirtualFrame frame) {
            return InvokeClosureOneTail.callerReference(frame);
        }

        static boolean admittedTail(GuardedDirectClosureCallTarget guarded) {
            return InvokeClosureOneTail.admittedTail(guarded);
        }

        static boolean admittedTailTarget(RootCallTarget target) {
            return InvokeClosureOneTail.admittedTailTarget(target);
        }

        static CanonicalClosure sourceDefinitionOrNull(Object receiver) {
            return InvokeClosureZero.sourceDefinitionOrNull(receiver);
        }

        static ProtosClosureExecutionPlan sourcePlanOrNull(Object receiver) {
            return InvokeClosureZero.sourcePlanOrNull(receiver);
        }

        static boolean requiresContextProjection(Object receiver) {
            return InvokeClosureZero.requiresContextProjection(receiver);
        }

        static DirectCallNode createDirectCallNodeOrNull(RootCallTarget target) {
            return InvokeClosureZero.createDirectCallNodeOrNull(target);
        }

        @NonIdempotent
        static boolean temporarilyRejectedInstance(Object receiver, Object caller) {
            return InvokeClosureZero.temporarilyRejectedInstance(receiver, caller);
        }

        static RootCallTarget definitionTarget(
                Object receiver,
                Object caller,
                ProtosLanguageContext enteredContext) {
            return InvokeClosureZero.definitionTarget(receiver, caller, enteredContext);
        }

        static GuardedDirectClosureCallTarget
                createGuardedDirectClosureCallForPrelude(
                        Object receiver,
                        ProtosPrelude prelude,
                        ProtosLanguageContext enteredContext) {
            return InvokeClosureZero.createGuardedDirectClosureCallForPrelude(
                    receiver, prelude, enteredContext);
        }

        static ProtosPrelude callerPrelude(Object caller) {
            return InvokeClosureZero.callerPrelude(caller);
        }

        @NonIdempotent
        static ProtosLanguageContext currentEnteredContext(Node node) {
            return ProtosLanguageContext.current(node);
        }
    }

    /**
     * I112 / PLAT062-T terminal CallN and spread; see {@link
     * InvokeClosureOneTail}. The supplied vector is the already evaluated
     * {@link CollectClosureCallArguments} or {@link
     * SnapshotClosureCallArguments} operand, so argument effects and the
     * spread snapshot keep their order. The caches are those of {@link
     * InvokeClosureArguments}, without its non-suspension requirement.
     */
    @Operation
    public static final class InvokeClosureArgumentsTail {
        @Specialization(
                excludeForUncached = true,
                guards = {
                    "definition != null",
                    "definition == cachedDefinition",
                    "plan != null",
                    "plan == cachedPlan",
                    "entered != null",
                    "entered == cachedContext",
                    "projection == cachedProjection",
                    "cachedTarget != null",
                    "cachedAdmitted"
                },
                limit = "3")
        public static Object direct(
                Object receiver,
                Object[] supplied,
                @Bind VirtualFrame frame,
                @Bind BytecodeNode bytecodeNode,
                @Bind("$bytecodeIndex") int bytecodeIndex,
                @Bind("currentActivation(frame)") ProtosActivation caller,
                @Bind("sourceDefinitionOrNull(receiver)")
                        CanonicalClosure definition,
                @Bind("sourcePlanOrNull(receiver)")
                        ProtosClosureExecutionPlan plan,
                @Bind("currentEnteredContext($node)")
                        ProtosLanguageContext entered,
                @Bind("requiresProjection(receiver)")
                        boolean projection,
                @Cached("definition")
                        CanonicalClosure cachedDefinition,
                @Cached("plan")
                        ProtosClosureExecutionPlan cachedPlan,
                @Cached("entered")
                        ProtosLanguageContext cachedContext,
                @Cached("projection")
                        boolean cachedProjection,
                @Cached("targetFor(receiver, caller, entered)")
                        RootCallTarget cachedTarget,
                @Cached("admittedTailTarget(cachedTarget)")
                        boolean cachedAdmitted,
                @Cached("createDirectCallNodeOrNull(cachedTarget)")
                        DirectCallNode node,
                @Cached IndirectCallNode indirectCall) {
            ProtosClosureValue closure = InvokeClosureArguments.admitted(receiver, caller);
            if (closure == null) {
                return prepareAndEnter(
                    receiver, caller, supplied, indirectCall, bytecodeNode, bytecodeIndex);
            }
            return InvokeClosureOneTail.enterDirect(
                    bytecodeNode,
                    bytecodeIndex,
                    node,
                    ProtosFrameArguments.compactDirectClosureCall(
                            closure, caller, null, supplied));
        }

        @Specialization(guards = "temporarilyRejected(receiver, caller)")
        public static Object rejected(
                Object receiver,
                Object[] supplied,
                @Bind VirtualFrame frame,
                @Bind BytecodeNode bytecodeNode,
                @Bind("$bytecodeIndex") int bytecodeIndex,
                @Bind("currentActivation(frame)") ProtosActivation caller,
                @Cached IndirectCallNode indirectCall) {
            return prepareAndEnter(
                    receiver, caller, supplied, indirectCall, bytecodeNode, bytecodeIndex);
        }

        @Specialization
        public static Object generic(
                Object receiver,
                Object[] supplied,
                @Bind VirtualFrame frame,
                @Bind BytecodeNode bytecodeNode,
                @Bind("$bytecodeIndex") int bytecodeIndex,
                @Bind("currentActivation(frame)") ProtosActivation caller,
                @Cached IndirectCallNode indirectCall) {
            return prepareAndEnter(
                    receiver, caller, supplied, indirectCall, bytecodeNode, bytecodeIndex);
        }

        private static Object prepareAndEnter(
                Object receiver,
                ProtosActivation caller,
                Object[] supplied,
                IndirectCallNode indirectCall,
                BytecodeNode bytecodeNode,
                int bytecodeIndex) {
            return ProtosTailCallContinuation.executeTerminalPreparedCall(
                    PrepareClosureCallArguments.perform(
                            ProtosNumericValueSupport.guestValue(receiver),
                            caller,
                            supplied),
                    indirectCall, bytecodeNode, bytecodeIndex);
        }

        /** The exact activation of this root, as {@link CurrentActivation} yields it. */
        @NonIdempotent
        static ProtosActivation currentActivation(VirtualFrame frame) {
            return ProtosFrameArguments.activation(frame);
        }

        static boolean admittedTailTarget(RootCallTarget target) {
            return InvokeClosureOneTail.admittedTailTarget(target);
        }

        static CanonicalClosure sourceDefinitionOrNull(Object receiver) {
            return InvokeClosureArguments.sourceDefinitionOrNull(receiver);
        }

        static ProtosClosureExecutionPlan sourcePlanOrNull(Object receiver) {
            return InvokeClosureArguments.sourcePlanOrNull(receiver);
        }

        static boolean requiresProjection(Object receiver) {
            return InvokeClosureArguments.requiresProjection(receiver);
        }

        @NonIdempotent
        static boolean temporarilyRejected(Object receiver, ProtosActivation caller) {
            return InvokeClosureArguments.temporarilyRejected(receiver, caller);
        }

        static RootCallTarget targetFor(
                Object receiver,
                ProtosActivation caller,
                ProtosLanguageContext entered) {
            return InvokeClosureArguments.targetFor(receiver, caller, entered);
        }

        static DirectCallNode createDirectCallNodeOrNull(RootCallTarget target) {
            return InvokeClosureArguments.createDirectCallNodeOrNull(target);
        }

        @NonIdempotent
        static ProtosLanguageContext currentEnteredContext(Node node) {
            return ProtosLanguageContext.current(node);
        }
    }

    /*
     * I108-A: only a prepared invocation requires caller-side
     * Bytecode DSL control composition. Direct invocation results
     * remain ordinary guest values, without a wrapper allocation.
     * PreparedClosureCall is an internal runtime interface and
     * cannot be produced as a guest-language result.
     */
    @Operation
    public static final class IsPreparedClosureInvocation {
        @Specialization
        public static boolean perform(Object result) {
            return result instanceof PreparedClosureCall;
        }
    }

    @Operation
    public static final class IsDirectSendMiss {
        @Specialization
        public static boolean perform(Object result) {
            return result == TryDirectSendOne.MISS;
        }
    }

    @Operation
    public static final class EnterClosureCall {
        @Specialization
        public static Object immediate(ImmediateResultCall prepared) {
            return prepared.enterImmediate();
        }

        @Specialization
        public static Object nativeCall(NativeCall prepared) {
            return prepared.enterNative();
        }

        /*
         * PERF038-H: no-home ordinary calls do not own a lifecycle, cannot
         * consume a non-local return and never remap runtime failures.
         * Their only required exception adaptation is unwrapping the
         * bytecode control-flow bridge.
         */
                @Specialization(
                guards = {
                    "!prepared.hasReturnHomeLifecycle()",
                    "prepared.bodyTarget() == cachedTarget"
                },
                limit = "3")
        public static Object ordinaryDirectNoHome(
                OrdinarySourceCall prepared,
                @Cached("prepared.bodyTarget()")
                        RootCallTarget cachedTarget,
                @Cached("create(cachedTarget)")
                        DirectCallNode node) {
            try {
                return node.call(prepared.targetArguments());
            } catch (ProtosBytecodeControlTransferException bridged) {
                throw bridged.transfer();
            }
        }

        @Specialization(
                guards = {
                    "prepared.hasReturnHomeLifecycle()",
                    "prepared.bodyTarget() == cachedTarget"
                },
                limit = "3")
        public static Object ordinaryDirect(
                OrdinarySourceCall prepared,
                @Cached("prepared.bodyTarget()")
                        RootCallTarget cachedTarget,
                @Cached("create(cachedTarget)")
                        DirectCallNode node) {
            try {
                return node.call(prepared.targetArguments());
            } catch (ProtosBytecodeControlTransferException bridged) {
                return prepared.handleControlTransfer(bridged.transfer());
            } catch (AbstractTruffleException transfer) {
                prepared.failIfModuleInitialization();
                throw transfer;
            } catch (RuntimeException failure) {
                throw prepared.mapRuntimeFailure(failure);
            }
        }

        @Specialization(replaces = {"ordinaryDirectNoHome", "ordinaryDirect"})
        public static Object ordinaryIndirect(
                OrdinarySourceCall prepared,
                @Shared("indirectCall") @Cached IndirectCallNode node) {
            try {
                return node.call(
                        prepared.bodyTarget(),
                        prepared.targetArguments());
            } catch (ProtosBytecodeControlTransferException bridged) {
                return prepared.handleControlTransfer(bridged.transfer());
            } catch (AbstractTruffleException transfer) {
                prepared.failIfModuleInitialization();
                throw transfer;
            } catch (RuntimeException failure) {
                throw prepared.mapRuntimeFailure(failure);
            }
        }

        @Specialization(guards = "prepared.isImmediate()")
        public static Object moduleImmediate(ModuleInitializationCall prepared) {
            return prepared.enterImmediate();
        }

        @Specialization(
                guards = {
                    "!prepared.isImmediate()",
                    "prepared.bodyTarget() == cachedTarget"
                },
                limit = "3")
        public static Object moduleDirect(
                ModuleInitializationCall prepared,
                @Cached("prepared.bodyTarget()")
                        RootCallTarget cachedTarget,
                @Cached("create(cachedTarget)")
                        DirectCallNode node) {
            try {
                return node.call(prepared.targetArguments());
            } catch (ProtosBytecodeControlTransferException bridged) {
                return prepared.handleControlTransfer(bridged.transfer());
            } catch (AbstractTruffleException transfer) {
                prepared.failIfModuleInitialization();
                throw transfer;
            } catch (RuntimeException failure) {
                throw prepared.mapRuntimeFailure(failure);
            }
        }

        @Specialization(replaces = "moduleDirect", guards = "!prepared.isImmediate()")
        public static Object moduleIndirect(
                ModuleInitializationCall prepared,
                @Shared("indirectCall") @Cached IndirectCallNode node) {
            try {
                return node.call(
                        prepared.bodyTarget(),
                        prepared.targetArguments());
            } catch (ProtosBytecodeControlTransferException bridged) {
                return prepared.handleControlTransfer(bridged.transfer());
            } catch (AbstractTruffleException transfer) {
                prepared.failIfModuleInitialization();
                throw transfer;
            } catch (RuntimeException failure) {
                throw prepared.mapRuntimeFailure(failure);
            }
        }
    }

    @Operation
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
                guards = {"value != null", "value.getClass() == cachedClass"},
                limit = "3")
        public static boolean classProfiled(
                Object value,
                @Cached("value.getClass()") Class<?> cachedClass,
                @Cached("isContinuationClass(cachedClass)") boolean continuation) {
            return continuation;
        }

        @Specialization(replaces = "classProfiled")
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
     * PERF038-H: suppress continuation classification only for a
     * source entry statically proven incapable of suspending.
     *
     * Every native, structured, dynamically uncertain or
     * polymorphic call retains the generic classification.
     */
    /**
     * PERF038-H: continuation composition specialized by the
     * concrete prepared-call representation (TEST009-J).
     *
     * A source entry proven incapable of suspension answers
     * false directly. Other ordinary source entries retain the
     * result-class PIC of PERF038-F.
     *
     * Native, immediate and module entries preserve the
     * complete continuation predicate.
     */
    @Operation
    public static final class IsContinuationForCall {
        @Specialization(guards = "prepared.provablyNonSuspending()")
        public static boolean provenNonSuspending(
                OrdinarySourceCall prepared,
                Object value) {
            return false;
        }

        @Specialization(
                guards = {
                    "!prepared.provablyNonSuspending()",
                    "value != null",
                    "value.getClass() == cachedClass"
                },
                limit = "3")
        public static boolean ordinaryClassProfiled(
                OrdinarySourceCall prepared,
                Object value,
                @Cached("value.getClass()")
                        Class<?> cachedClass,
                @Cached("isContinuationClass(cachedClass)")
                        boolean continuation) {
            return continuation;
        }

        @Specialization(
                replaces = {"provenNonSuspending", "ordinaryClassProfiled"})
        public static boolean ordinaryGeneric(
                OrdinarySourceCall prepared,
                Object value) {
            return IsContinuation.perform(value);
        }

        @Specialization
        public static boolean nativeCall(
                NativeCall prepared,
                Object value) {
            return IsContinuation.perform(value);
        }

        @Specialization
        public static boolean immediate(
                ImmediateResultCall prepared,
                Object value) {
            return IsContinuation.perform(value);
        }

        @Specialization
        public static boolean moduleInitialization(
                ModuleInitializationCall prepared,
                Object value) {
            return IsContinuation.perform(value);
        }

        static boolean isContinuationClass(Class<?> type) {
            return IsContinuation.isContinuationClass(type);
        }
    }

    @Operation
    public static final class ResumeContinuation {
        @Specialization
        public static Object nativeSuspension(
                PreparedClosureCall prepared,
                ProtosNativeSuspension suspension,
                Object resumeValue) {
            /*
             * Normal resume values are only C-prime transport through caller
             * roots; the native descriptor owns the state needed to finish the
             * logical operation. B4E reserves only the backend-private exact
             * cancellation transfer as an unwind injection marker. It reaches
             * the suspended native leaf without re-entering/re-observing the
             * detached Future waiter, then enters the existing B4A EH bridge.
             */
            if (resumeValue instanceof ProtosTaskCancellationException cancellation) {
                ProtosTask task =
                        prepared.taskForRuntime();
                if (task == null) {
                    throw new IllegalStateException(
                            "C-prime cancellation resume requires a task");
                }
                if (task.cancellationPhase()
                        != ProtosTask.CancellationPhase.UNWINDING) {
                    throw new IllegalStateException(
                            "C-prime cancellation resume requires UNWINDING phase");
                }
                throw cancellation;
            }
            if (resumeValue instanceof ProtosSignalException rejectedSuspension) {
                /*
                 * D189: the Task gate rejected this suspension across a live synchronous
                 * foreign operation and already removed the wait relationship. The fresh
                 * Error is signaled at the suspension point; the descriptor is never resumed.
                 */
                throw rejectedSuspension;
            }
            return suspension.resume();
        }

        @Specialization
        public static Object ioOperationSuspension(
                PreparedClosureCall prepared,
                ProtosIoOperationSuspension suspension,
                Object resumeValue) {
            if (prepared.activation().task().isPresent()) {
                throw new IllegalStateException(
                        "operation-owned suspension cannot resume through a Task activation");
            }
            if (prepared.activation().deferredCPrimeOperationForRuntime().orElse(null)
                    != suspension.operation()) {
                throw new IllegalStateException(
                        "operation-owned suspension resumed through another I/O operation");
            }
            if (resumeValue != ProtosNullValue.INSTANCE) {
                throw new IllegalStateException(
                        "operation-owned suspension received an unsupported resume transport");
            }
            return suspension.resume();
        }

        /** I112: continues a child's terminal transport; see {@link ProtosTailCallContinuation}. */
        @Specialization
        public static Object terminal(
                PreparedClosureCall prepared,
                ProtosTailCallContinuation transport,
                Object resumeValue) {
            try {
                return ProtosTailCallContinuation.resume(transport, resumeValue);
            } catch (ProtosBytecodeControlTransferException bridged) {
                return prepared.handleControlTransfer(bridged.transfer());
            } catch (AbstractTruffleException transfer) {
                prepared.failIfModuleInitialization();
                throw transfer;
            } catch (RuntimeException failure) {
                throw prepared.mapRuntimeFailure(failure);
            }
        }

        @Specialization(
                guards = "result.getContinuationRootNode() == cachedRoot",
                limit = "3")
        public static Object direct(
                PreparedClosureCall prepared,
                ContinuationResult result,
                Object resumeValue,
                @Cached("result.getContinuationRootNode()")
                        ContinuationRootNode cachedRoot,
                @Cached("create(cachedRoot.getCallTarget())")
                        DirectCallNode node) {
            try {
                return node.call(
                        result.getFrame(),
                        resumeValue);
            } catch (ProtosBytecodeControlTransferException bridged) {
                return prepared.handleControlTransfer(bridged.transfer());
            } catch (AbstractTruffleException transfer) {
                prepared.failIfModuleInitialization();
                throw transfer;
            } catch (RuntimeException failure) {
                throw prepared.mapRuntimeFailure(failure);
            }
        }

        @Specialization(replaces = "direct")
        public static Object indirect(
                PreparedClosureCall prepared,
                ContinuationResult result,
                Object resumeValue,
                @Cached IndirectCallNode node) {
            try {
                return node.call(
                        result.getContinuationCallTarget(),
                        result.getFrame(),
                        resumeValue);
            } catch (ProtosBytecodeControlTransferException bridged) {
                return prepared.handleControlTransfer(bridged.transfer());
            } catch (AbstractTruffleException transfer) {
                prepared.failIfModuleInitialization();
                throw transfer;
            } catch (RuntimeException failure) {
                throw prepared.mapRuntimeFailure(failure);
            }
        }
    }

    @Operation
    public static final class FinishClosureCall {
        /*
         * PERF038-E: an ordinary source call that owns no materialized return
         * home (an unobservable owned home, or a captured home owned by an
         * outer invocation) has no completion effect, so a site that has only
         * finished such calls compiles neither the lifecycle test nor the
         * completion. The guard reads a final carrier field on every
         * execution; the lifecycle form keeps the unchanged completion.
         */
        @Specialization(guards = "!prepared.hasReturnHomeLifecycle()")
        public static Object ordinaryWithoutHomeLifecycle(
                OrdinarySourceCall prepared, Object result) {
            return result;
        }

        @Specialization(guards = "prepared.hasReturnHomeLifecycle()")
        public static Object ordinary(OrdinarySourceCall prepared, Object result) {
            return prepared.finish(result);
        }

        @Specialization
        public static Object nativeCall(NativeCall prepared, Object result) {
            return prepared.finish(result);
        }

        @Specialization
        public static Object immediate(ImmediateResultCall prepared, Object result) {
            return prepared.finish(result);
        }

        @Specialization
        public static Object moduleInitialization(
                ModuleInitializationCall prepared,
                Object result) {
            return prepared.finish(result);
        }
    }

    /**
     * I111-B: the result completion of a prepared invocation in an auxiliary
     * structured-dispatch or I/O C-prime root. Unlike {@link
     * FinishClosureCall}, whose no-lifecycle ordinary form leaves a primitive
     * carrier to the consumers and return epilogue of a semantic root, every
     * form here applies the prepared call's complete finish, including the
     * I101-B guest-boundary projection, because these roots hand the result
     * to protocol code. It is exactly the former structured/C-prime
     * interpreter's {@code FinishClosureCall}.
     */
    @Operation
    public static final class FinishCPrimeClosureCall {
        @Specialization
        public static Object ordinary(OrdinarySourceCall prepared, Object result) {
            return FinishClosureCall.ordinary(prepared, result);
        }

        @Specialization
        public static Object nativeCall(NativeCall prepared, Object result) {
            return FinishClosureCall.nativeCall(prepared, result);
        }

        @Specialization
        public static Object immediate(ImmediateResultCall prepared, Object result) {
            return FinishClosureCall.immediate(prepared, result);
        }

        @Specialization
        public static Object moduleInitialization(
                ModuleInitializationCall prepared,
                Object result) {
            return FinishClosureCall.moduleInitialization(
                    prepared, result);
        }
    }

    /*
     * I111-B: the operations below were declared by the former untagged
     * structured-dispatch/C-prime interpreter. They are emitted only by
     * auxiliary roots (ProtosStructuredDispatchLowerer, the Task C-prime
     * entry plan and the I/O C-prime executions).
     */

    @Operation
    public static final class IsStructuredCaseOfCall {
        @Specialization
        public static boolean ordinary(OrdinarySourceCall prepared) {
            return prepared.isStructuredCaseOf();
        }

        @Specialization
        public static boolean nativeCall(NativeCall prepared) {
            return prepared.isStructuredCaseOf();
        }

        @Specialization
        public static boolean immediate(ImmediateResultCall prepared) {
            return prepared.isStructuredCaseOf();
        }

        @Specialization
        public static boolean moduleInitialization(ModuleInitializationCall prepared) {
            return prepared.isStructuredCaseOf();
        }
    }

    @Operation
    public static final class PrepareStructuredCaseOfCall {
        @Specialization
        public static PreparedCaseOfCall ordinary(OrdinarySourceCall prepared) {
            return prepared.prepareStructuredCaseOf();
        }

        @Specialization
        public static PreparedCaseOfCall nativeCall(NativeCall prepared) {
            return prepared.prepareStructuredCaseOf();
        }

        @Specialization
        public static PreparedCaseOfCall immediate(ImmediateResultCall prepared) {
            return prepared.prepareStructuredCaseOf();
        }

        @Specialization
        public static PreparedCaseOfCall moduleInitialization(ModuleInitializationCall prepared) {
            return prepared.prepareStructuredCaseOf();
        }
    }

    @Operation
    public static final class StructuredCaseOfNeedsMatcher {
        @Specialization
        public static boolean perform(PreparedCaseOfCall prepared) {
            return prepared.needsMatcher();
        }
    }

    @Operation
    public static final class PrepareStructuredCaseOfMatcherCall {
        @Specialization
        public static PreparedClosureCall perform(
                PreparedCaseOfCall prepared) {
            return prepared.prepareMatcher();
        }
    }

    @Operation
    public static final class AcceptStructuredCaseOfMatcherOutcome {
        @Specialization
        public static void perform(
                PreparedCaseOfCall prepared,
                Object outcome) {
            prepared.acceptMatcher(outcome);
        }
    }

    @Operation
    public static final class PrepareStructuredCaseOfActionCall {
        @Specialization
        public static PreparedClosureCall perform(
                PreparedCaseOfCall prepared) {
            return prepared.prepareSelectedAction();
        }
    }

    @Operation
    public static final class IsStructuredObjectCall {
        @Specialization
        public static boolean ordinary(OrdinarySourceCall prepared) {
            return prepared.isStructuredObjectCall();
        }

        @Specialization
        public static boolean nativeCall(NativeCall prepared) {
            return prepared.isStructuredObjectCall();
        }

        @Specialization
        public static boolean immediate(ImmediateResultCall prepared) {
            return prepared.isStructuredObjectCall();
        }

        @Specialization
        public static boolean moduleInitialization(ModuleInitializationCall prepared) {
            return prepared.isStructuredObjectCall();
        }
    }

    @Operation
    public static final class PrepareStructuredObjectCall {
        @Specialization
        public static PreparedStandardObjectCall ordinary(OrdinarySourceCall prepared) {
            return prepared.prepareStructuredObjectCall();
        }

        @Specialization
        public static PreparedStandardObjectCall nativeCall(NativeCall prepared) {
            return prepared.prepareStructuredObjectCall();
        }

        @Specialization
        public static PreparedStandardObjectCall immediate(ImmediateResultCall prepared) {
            return prepared.prepareStructuredObjectCall();
        }

        @Specialization
        public static PreparedStandardObjectCall moduleInitialization(
                ModuleInitializationCall prepared) {
            return prepared.prepareStructuredObjectCall();
        }
    }

    @Operation
    public static final class LoadStructuredObjectCallChild {
        @Specialization
        public static PreparedClosureCall perform(
                PreparedStandardObjectCall prepared) {
            return prepared.child();
        }
    }

    @Operation
    public static final class FinishStructuredObjectCall {
        @Specialization
        public static Object perform(
                PreparedStandardObjectCall prepared,
                Object childResult) {
            return prepared.finish(childResult);
        }
    }

    @Operation
    public static final class IsStructuredImportCall {
        @Specialization
        public static boolean ordinary(OrdinarySourceCall prepared) {
            return prepared.isStructuredImportCall();
        }

        @Specialization
        public static boolean nativeCall(NativeCall prepared) {
            return prepared.isStructuredImportCall();
        }

        @Specialization
        public static boolean immediate(ImmediateResultCall prepared) {
            return prepared.isStructuredImportCall();
        }

        @Specialization
        public static boolean moduleInitialization(ModuleInitializationCall prepared) {
            return prepared.isStructuredImportCall();
        }
    }

    @Operation
    public static final class PrepareStructuredImportCall {
        @Specialization
        public static PreparedStandardImportCall ordinary(OrdinarySourceCall prepared) {
            return prepared.prepareStructuredImportCall();
        }

        @Specialization
        public static PreparedStandardImportCall nativeCall(NativeCall prepared) {
            return prepared.prepareStructuredImportCall();
        }

        @Specialization
        public static PreparedStandardImportCall immediate(ImmediateResultCall prepared) {
            return prepared.prepareStructuredImportCall();
        }

        @Specialization
        public static PreparedStandardImportCall moduleInitialization(
                ModuleInitializationCall prepared) {
            return prepared.prepareStructuredImportCall();
        }
    }

    @Operation
    public static final class LoadStructuredImportCallChild {
        @Specialization
        public static PreparedClosureCall perform(
                PreparedStandardImportCall prepared) {
            return prepared.child();
        }
    }

    @Operation
    public static final class FinishStructuredImportCall {
        @Specialization
        public static Object perform(
                PreparedStandardImportCall prepared,
                Object childResult) {
            return prepared.finish(childResult);
        }
    }

    @Operation
    public static final class IsStructuredEnsureCall {
        @Specialization
        public static boolean ordinary(OrdinarySourceCall prepared) {
            return prepared.isStructuredEnsure();
        }

        @Specialization
        public static boolean nativeCall(NativeCall prepared) {
            return prepared.isStructuredEnsure();
        }

        @Specialization
        public static boolean immediate(ImmediateResultCall prepared) {
            return prepared.isStructuredEnsure();
        }

        @Specialization
        public static boolean moduleInitialization(ModuleInitializationCall prepared) {
            return prepared.isStructuredEnsure();
        }
    }

    @Operation
    public static final class PrepareStructuredEnsureCall {
        @Specialization
        public static PreparedEnsureCall ordinary(OrdinarySourceCall prepared) {
            return prepared.prepareStructuredEnsure();
        }

        @Specialization
        public static PreparedEnsureCall nativeCall(NativeCall prepared) {
            return prepared.prepareStructuredEnsure();
        }

        @Specialization
        public static PreparedEnsureCall immediate(ImmediateResultCall prepared) {
            return prepared.prepareStructuredEnsure();
        }

        @Specialization
        public static PreparedEnsureCall moduleInitialization(ModuleInitializationCall prepared) {
            return prepared.prepareStructuredEnsure();
        }
    }

    @Operation
    public static final class LoadStructuredEnsureBodyCall {
        @Specialization
        public static PreparedClosureCall perform(PreparedEnsureCall prepared) {
            return prepared.body();
        }
    }

    @Operation
    public static final class LoadStructuredEnsureCleanupCall {
        @Specialization
        public static PreparedClosureCall perform(PreparedEnsureCall prepared) {
            return prepared.cleanup();
        }
    }

    @Operation
    public static final class IsStructuredWhileCall {
        @Specialization
        public static boolean ordinary(OrdinarySourceCall prepared) {
            return prepared.isStructuredWhile();
        }

        @Specialization
        public static boolean nativeCall(NativeCall prepared) {
            return prepared.isStructuredWhile();
        }

        @Specialization
        public static boolean immediate(ImmediateResultCall prepared) {
            return prepared.isStructuredWhile();
        }

        @Specialization
        public static boolean moduleInitialization(ModuleInitializationCall prepared) {
            return prepared.isStructuredWhile();
        }
    }

    @Operation
    public static final class IsStructuredLocalEachCall {
        @Specialization
        public static boolean ordinary(OrdinarySourceCall prepared) {
            return isStandardLocalEach(prepared);
        }

        @Specialization
        public static boolean nativeCall(NativeCall prepared) {
            return isStandardLocalEach(prepared);
        }

        @Specialization
        public static boolean immediate(ImmediateResultCall prepared) {
            return isStandardLocalEach(prepared);
        }

        @Specialization
        public static boolean moduleInitialization(ModuleInitializationCall prepared) {
            return isStandardLocalEach(prepared);
        }

        private static boolean isStandardLocalEach(
                PreparedClosureCall prepared) {
            return prepared.isStructuredArrayEach()
                    || prepared.isStructuredBytesEach()
                    || prepared.isStructuredEnvironmentEach()
                    || prepared.isStructuredIdentityMapEach()
                    || prepared.isStructuredMapEach();
        }
    }

    @Operation
    public static final class PrepareStructuredLocalEachCall {
        @Specialization
        public static PreparedLocalEachCall ordinary(
                OrdinarySourceCall prepared) {
            if (prepared.isStructuredArrayEach()) {
                return PrepareStructuredArrayEachCall.ordinary(prepared);
            }
            if (prepared.isStructuredBytesEach()) {
                return PrepareStructuredBytesEachCall.ordinary(prepared);
            }
            if (prepared.isStructuredEnvironmentEach()) {
                return PrepareStructuredEnvironmentEachCall.ordinary(prepared);
            }
            if (prepared.isStructuredIdentityMapEach()) {
                return PrepareStructuredIdentityMapEachCall.ordinary(prepared);
            }
            if (prepared.isStructuredMapEach()) {
                return PrepareStructuredMapEachCall.ordinary(prepared);
            }
            throw new IllegalStateException(
                    "Not a structured local each call");
        }

        @Specialization
        public static PreparedLocalEachCall nativeCall(
                NativeCall prepared) {
            if (prepared.isStructuredArrayEach()) {
                return PrepareStructuredArrayEachCall.nativeCall(prepared);
            }
            if (prepared.isStructuredBytesEach()) {
                return PrepareStructuredBytesEachCall.nativeCall(prepared);
            }
            if (prepared.isStructuredEnvironmentEach()) {
                return PrepareStructuredEnvironmentEachCall.nativeCall(prepared);
            }
            if (prepared.isStructuredIdentityMapEach()) {
                return PrepareStructuredIdentityMapEachCall.nativeCall(prepared);
            }
            if (prepared.isStructuredMapEach()) {
                return PrepareStructuredMapEachCall.nativeCall(prepared);
            }
            throw new IllegalStateException(
                    "Not a structured local each call");
        }

        @Specialization
        public static PreparedLocalEachCall immediate(
                ImmediateResultCall prepared) {
            if (prepared.isStructuredArrayEach()) {
                return PrepareStructuredArrayEachCall.immediate(prepared);
            }
            if (prepared.isStructuredBytesEach()) {
                return PrepareStructuredBytesEachCall.immediate(prepared);
            }
            if (prepared.isStructuredEnvironmentEach()) {
                return PrepareStructuredEnvironmentEachCall.immediate(prepared);
            }
            if (prepared.isStructuredIdentityMapEach()) {
                return PrepareStructuredIdentityMapEachCall.immediate(prepared);
            }
            if (prepared.isStructuredMapEach()) {
                return PrepareStructuredMapEachCall.immediate(prepared);
            }
            throw new IllegalStateException(
                    "Not a structured local each call");
        }

        @Specialization
        public static PreparedLocalEachCall moduleInitialization(
                ModuleInitializationCall prepared) {
            if (prepared.isStructuredArrayEach()) {
                return PrepareStructuredArrayEachCall.moduleInitialization(prepared);
            }
            if (prepared.isStructuredBytesEach()) {
                return PrepareStructuredBytesEachCall.moduleInitialization(prepared);
            }
            if (prepared.isStructuredEnvironmentEach()) {
                return PrepareStructuredEnvironmentEachCall.moduleInitialization(prepared);
            }
            if (prepared.isStructuredIdentityMapEach()) {
                return PrepareStructuredIdentityMapEachCall.moduleInitialization(prepared);
            }
            if (prepared.isStructuredMapEach()) {
                return PrepareStructuredMapEachCall.moduleInitialization(prepared);
            }
            throw new IllegalStateException(
                    "Not a structured local each call");
        }

    }

    @Operation
    public static final class IsStructuredArrayEachCall {
        @Specialization
        public static boolean ordinary(OrdinarySourceCall prepared) {
            return prepared.isStructuredArrayEach();
        }

        @Specialization
        public static boolean nativeCall(NativeCall prepared) {
            return prepared.isStructuredArrayEach();
        }

        @Specialization
        public static boolean immediate(ImmediateResultCall prepared) {
            return prepared.isStructuredArrayEach();
        }

        @Specialization
        public static boolean moduleInitialization(ModuleInitializationCall prepared) {
            return prepared.isStructuredArrayEach();
        }
    }

    @Operation
    public static final class PrepareStructuredArrayEachCall {
        @Specialization
        public static PreparedArrayEachCall ordinary(OrdinarySourceCall prepared) {
            return prepared.prepareStructuredArrayEach();
        }

        @Specialization
        public static PreparedArrayEachCall nativeCall(NativeCall prepared) {
            return prepared.prepareStructuredArrayEach();
        }

        @Specialization
        public static PreparedArrayEachCall immediate(ImmediateResultCall prepared) {
            return prepared.prepareStructuredArrayEach();
        }

        @Specialization
        public static PreparedArrayEachCall moduleInitialization(
                ModuleInitializationCall prepared) {
            return prepared.prepareStructuredArrayEach();
        }
    }

    @Operation
    public static final class StructuredArrayEachHasNext {
        @Specialization
        public static boolean perform(PreparedArrayEachCall prepared) {
            return prepared.hasNext();
        }
    }

    @Operation
    public static final class PrepareStructuredArrayEachElementCall {
        @Specialization
        public static PreparedClosureCall perform(PreparedArrayEachCall prepared) {
            return prepared.prepareCurrent();
        }
    }

    @Operation
    public static final class AdvanceStructuredArrayEach {
        @Specialization
        public static void perform(PreparedArrayEachCall prepared) {
            prepared.advance();
        }
    }

    @Operation
    public static final class FinishStructuredArrayEach {
        @Specialization
        public static Object perform(PreparedArrayEachCall prepared) {
            return prepared.finish();
        }
    }

    @Operation
    public static final class IsStructuredArrayMatchCall {
        @Specialization
        public static boolean ordinary(OrdinarySourceCall prepared) {
            return prepared.isStructuredArrayMatch();
        }

        @Specialization
        public static boolean nativeCall(NativeCall prepared) {
            return prepared.isStructuredArrayMatch();
        }

        @Specialization
        public static boolean immediate(ImmediateResultCall prepared) {
            return prepared.isStructuredArrayMatch();
        }

        @Specialization
        public static boolean moduleInitialization(ModuleInitializationCall prepared) {
            return prepared.isStructuredArrayMatch();
        }
    }

    @Operation
    public static final class PrepareStructuredArrayMatchCall {
        @Specialization
        public static PreparedArrayMatchCall ordinary(OrdinarySourceCall prepared) {
            return prepared.prepareStructuredArrayMatch();
        }

        @Specialization
        public static PreparedArrayMatchCall nativeCall(NativeCall prepared) {
            return prepared.prepareStructuredArrayMatch();
        }

        @Specialization
        public static PreparedArrayMatchCall immediate(ImmediateResultCall prepared) {
            return prepared.prepareStructuredArrayMatch();
        }

        @Specialization
        public static PreparedArrayMatchCall moduleInitialization(
                ModuleInitializationCall prepared) {
            return prepared.prepareStructuredArrayMatch();
        }
    }

    @Operation
    public static final class StructuredArrayMatchHasNext {
        @Specialization
        public static boolean perform(PreparedArrayMatchCall prepared) {
            return prepared.hasNext();
        }
    }

    @Operation
    public static final class PrepareStructuredArrayMatchElementCall {
        @Specialization
        public static PreparedClosureCall perform(
                PreparedArrayMatchCall prepared) {
            return prepared.prepareCurrent();
        }
    }

    @Operation
    public static final class AcceptStructuredArrayMatchOutcome {
        @Specialization
        public static void perform(
                PreparedArrayMatchCall prepared,
                Object outcome) {
            prepared.acceptCurrent(outcome);
        }
    }

    @Operation
    public static final class FinishStructuredArrayMatch {
        @Specialization
        public static Object perform(PreparedArrayMatchCall prepared) {
            return prepared.finish();
        }
    }

    @Operation
    public static final class IsStructuredBytesEachCall {
        @Specialization
        public static boolean ordinary(OrdinarySourceCall prepared) {
            return prepared.isStructuredBytesEach();
        }

        @Specialization
        public static boolean nativeCall(NativeCall prepared) {
            return prepared.isStructuredBytesEach();
        }

        @Specialization
        public static boolean immediate(ImmediateResultCall prepared) {
            return prepared.isStructuredBytesEach();
        }

        @Specialization
        public static boolean moduleInitialization(ModuleInitializationCall prepared) {
            return prepared.isStructuredBytesEach();
        }
    }

    @Operation
    public static final class PrepareStructuredBytesEachCall {
        @Specialization
        public static PreparedBytesEachCall ordinary(OrdinarySourceCall prepared) {
            return prepared.prepareStructuredBytesEach();
        }

        @Specialization
        public static PreparedBytesEachCall nativeCall(NativeCall prepared) {
            return prepared.prepareStructuredBytesEach();
        }

        @Specialization
        public static PreparedBytesEachCall immediate(ImmediateResultCall prepared) {
            return prepared.prepareStructuredBytesEach();
        }

        @Specialization
        public static PreparedBytesEachCall moduleInitialization(
                ModuleInitializationCall prepared) {
            return prepared.prepareStructuredBytesEach();
        }
    }

    @Operation
    public static final class StructuredBytesEachHasNext {
        @Specialization
        public static boolean perform(PreparedBytesEachCall prepared) {
            return prepared.hasNext();
        }
    }

    @Operation
    public static final class PrepareStructuredBytesEachElementCall {
        @Specialization
        public static PreparedClosureCall perform(PreparedBytesEachCall prepared) {
            return prepared.prepareCurrent();
        }
    }

    @Operation
    public static final class AdvanceStructuredBytesEach {
        @Specialization
        public static void perform(PreparedBytesEachCall prepared) {
            prepared.advance();
        }
    }

    @Operation
    public static final class FinishStructuredBytesEach {
        @Specialization
        public static Object perform(PreparedBytesEachCall prepared) {
            return prepared.finish();
        }
    }

    @Operation
    public static final class IsStructuredEnvironmentEachCall {
        @Specialization
        public static boolean ordinary(OrdinarySourceCall prepared) {
            return prepared.isStructuredEnvironmentEach();
        }

        @Specialization
        public static boolean nativeCall(NativeCall prepared) {
            return prepared.isStructuredEnvironmentEach();
        }

        @Specialization
        public static boolean immediate(ImmediateResultCall prepared) {
            return prepared.isStructuredEnvironmentEach();
        }

        @Specialization
        public static boolean moduleInitialization(ModuleInitializationCall prepared) {
            return prepared.isStructuredEnvironmentEach();
        }
    }

    @Operation
    public static final class PrepareStructuredEnvironmentEachCall {
        @Specialization
        public static PreparedEnvironmentEachCall ordinary(OrdinarySourceCall prepared) {
            return prepared.prepareStructuredEnvironmentEach();
        }

        @Specialization
        public static PreparedEnvironmentEachCall nativeCall(NativeCall prepared) {
            return prepared.prepareStructuredEnvironmentEach();
        }

        @Specialization
        public static PreparedEnvironmentEachCall immediate(ImmediateResultCall prepared) {
            return prepared.prepareStructuredEnvironmentEach();
        }

        @Specialization
        public static PreparedEnvironmentEachCall moduleInitialization(
                ModuleInitializationCall prepared) {
            return prepared.prepareStructuredEnvironmentEach();
        }
    }

    @Operation
    public static final class StructuredEnvironmentEachHasNext {
        @Specialization
        public static boolean perform(PreparedEnvironmentEachCall prepared) {
            return prepared.hasNext();
        }
    }

    @Operation
    public static final class PrepareStructuredEnvironmentEachEntryCall {
        @Specialization
        public static PreparedClosureCall perform(PreparedEnvironmentEachCall prepared) {
            return prepared.prepareCurrent();
        }
    }

    @Operation
    public static final class AdvanceStructuredEnvironmentEach {
        @Specialization
        public static void perform(PreparedEnvironmentEachCall prepared) {
            prepared.advance();
        }
    }

    @Operation
    public static final class FinishStructuredEnvironmentEach {
        @Specialization
        public static Object perform(PreparedEnvironmentEachCall prepared) {
            return prepared.finish();
        }
    }

    @Operation
    public static final class IsStructuredForeignEachCall {
        @Specialization
        public static boolean ordinary(OrdinarySourceCall prepared) {
            return prepared.isStructuredForeignEach();
        }

        @Specialization
        public static boolean nativeCall(NativeCall prepared) {
            return prepared.isStructuredForeignEach();
        }

        @Specialization
        public static boolean immediate(ImmediateResultCall prepared) {
            return prepared.isStructuredForeignEach();
        }

        @Specialization
        public static boolean moduleInitialization(ModuleInitializationCall prepared) {
            return prepared.isStructuredForeignEach();
        }
    }

    /**
     * D188 projected foreign {@code each}: the cursor validates the receiver, arity and block
     * callability before any foreign operation; the provider iterator is acquired lazily by the
     * first {@link StructuredForeignEachHasNext}.
     */
    @Operation
    public static final class PrepareStructuredForeignEachCall {
        @Specialization
        public static ProtosForeignEachCall ordinary(OrdinarySourceCall prepared) {
            return prepared.prepareStructuredForeignEach();
        }

        @Specialization
        public static ProtosForeignEachCall nativeCall(NativeCall prepared) {
            return prepared.prepareStructuredForeignEach();
        }

        @Specialization
        public static ProtosForeignEachCall immediate(ImmediateResultCall prepared) {
            return prepared.prepareStructuredForeignEach();
        }

        @Specialization
        public static ProtosForeignEachCall moduleInitialization(
                ModuleInitializationCall prepared) {
            return prepared.prepareStructuredForeignEach();
        }
    }

    @Operation
    public static final class StructuredForeignEachHasNext {
        @Specialization
        public static boolean perform(ProtosForeignEachCall prepared) {
            return prepared.hasNext();
        }
    }

    /** Pulls and admits one element, then prepares the ordinary invocation of the block. */
    @Operation
    public static final class PrepareStructuredForeignEachElementCall {
        @Specialization
        public static PreparedClosureCall perform(ProtosForeignEachCall prepared) {
            return ProtosClosureCallPreparation.prepareClosureCall(
                    prepared.callback(), List.of(prepared.current()), prepared.activation());
        }
    }

    @Operation
    public static final class AdvanceStructuredForeignEach {
        @Specialization
        public static void perform(ProtosForeignEachCall prepared) {
            prepared.advance();
        }
    }

    @Operation
    public static final class FinishStructuredForeignEach {
        @Specialization
        public static Object perform(ProtosForeignEachCall prepared) {
            return prepared.finish();
        }
    }

    @Operation
    public static final class IsStructuredIdentityMapAtIfAbsentCall {
        @Specialization
        public static boolean ordinary(OrdinarySourceCall prepared) {
            return prepared.isStructuredIdentityMapAtIfAbsent();
        }

        @Specialization
        public static boolean nativeCall(NativeCall prepared) {
            return prepared.isStructuredIdentityMapAtIfAbsent();
        }

        @Specialization
        public static boolean immediate(ImmediateResultCall prepared) {
            return prepared.isStructuredIdentityMapAtIfAbsent();
        }

        @Specialization
        public static boolean moduleInitialization(ModuleInitializationCall prepared) {
            return prepared.isStructuredIdentityMapAtIfAbsent();
        }
    }

    @Operation
    public static final class PrepareStructuredIdentityMapAtIfAbsentCall {
        @Specialization
        public static PreparedIdentityMapAtIfAbsentCall ordinary(OrdinarySourceCall prepared) {
            return prepared.prepareStructuredIdentityMapAtIfAbsent();
        }

        @Specialization
        public static PreparedIdentityMapAtIfAbsentCall nativeCall(NativeCall prepared) {
            return prepared.prepareStructuredIdentityMapAtIfAbsent();
        }

        @Specialization
        public static PreparedIdentityMapAtIfAbsentCall immediate(ImmediateResultCall prepared) {
            return prepared.prepareStructuredIdentityMapAtIfAbsent();
        }

        @Specialization
        public static PreparedIdentityMapAtIfAbsentCall moduleInitialization(
                ModuleInitializationCall prepared) {
            return prepared.prepareStructuredIdentityMapAtIfAbsent();
        }
    }

    @Operation
    public static final class StructuredIdentityMapAtIfAbsentNeedsFallback {
        @Specialization
        public static boolean perform(
                PreparedIdentityMapAtIfAbsentCall prepared) {
            return prepared.needsFallback();
        }
    }

    @Operation
    public static final class PrepareStructuredIdentityMapAtIfAbsentFallbackCall {
        @Specialization
        public static PreparedClosureCall perform(
                PreparedIdentityMapAtIfAbsentCall prepared) {
            return prepared.prepareFallback();
        }
    }

    @Operation
    public static final class FinishStructuredIdentityMapAtIfAbsentFallback {
        @Specialization
        public static Object perform(
                PreparedIdentityMapAtIfAbsentCall prepared,
                Object result) {
            return prepared.finishFallback(result);
        }
    }

    @Operation
    public static final class FinishStructuredIdentityMapAtIfAbsentPresent {
        @Specialization
        public static Object perform(
                PreparedIdentityMapAtIfAbsentCall prepared) {
            return prepared.finishPresent();
        }
    }

    @Operation
    public static final class IsStructuredIdentityMapEachCall {
        @Specialization
        public static boolean ordinary(OrdinarySourceCall prepared) {
            return prepared.isStructuredIdentityMapEach();
        }

        @Specialization
        public static boolean nativeCall(NativeCall prepared) {
            return prepared.isStructuredIdentityMapEach();
        }

        @Specialization
        public static boolean immediate(ImmediateResultCall prepared) {
            return prepared.isStructuredIdentityMapEach();
        }

        @Specialization
        public static boolean moduleInitialization(ModuleInitializationCall prepared) {
            return prepared.isStructuredIdentityMapEach();
        }
    }

    @Operation
    public static final class PrepareStructuredIdentityMapEachCall {
        @Specialization
        public static PreparedIdentityMapEachCall ordinary(OrdinarySourceCall prepared) {
            return prepared.prepareStructuredIdentityMapEach();
        }

        @Specialization
        public static PreparedIdentityMapEachCall nativeCall(NativeCall prepared) {
            return prepared.prepareStructuredIdentityMapEach();
        }

        @Specialization
        public static PreparedIdentityMapEachCall immediate(ImmediateResultCall prepared) {
            return prepared.prepareStructuredIdentityMapEach();
        }

        @Specialization
        public static PreparedIdentityMapEachCall moduleInitialization(
                ModuleInitializationCall prepared) {
            return prepared.prepareStructuredIdentityMapEach();
        }
    }

    @Operation
    public static final class StructuredIdentityMapEachHasNext {
        @Specialization
        public static boolean perform(PreparedIdentityMapEachCall prepared) {
            return prepared.hasNext();
        }
    }

    @Operation
    public static final class PrepareStructuredIdentityMapEachEntryCall {
        @Specialization
        public static PreparedClosureCall perform(PreparedIdentityMapEachCall prepared) {
            return prepared.prepareCurrent();
        }
    }

    @Operation
    public static final class AdvanceStructuredIdentityMapEach {
        @Specialization
        public static void perform(PreparedIdentityMapEachCall prepared) {
            prepared.advance();
        }
    }

    @Operation
    public static final class FinishStructuredIdentityMapEach {
        @Specialization
        public static Object perform(PreparedIdentityMapEachCall prepared) {
            return prepared.finish();
        }
    }

    @Operation
    public static final class IsStructuredMapMatchCall {
        @Specialization
        public static boolean ordinary(OrdinarySourceCall prepared) {
            return prepared.isStructuredMapMatch();
        }

        @Specialization
        public static boolean nativeCall(NativeCall prepared) {
            return prepared.isStructuredMapMatch();
        }

        @Specialization
        public static boolean immediate(ImmediateResultCall prepared) {
            return prepared.isStructuredMapMatch();
        }

        @Specialization
        public static boolean moduleInitialization(ModuleInitializationCall prepared) {
            return prepared.isStructuredMapMatch();
        }
    }

    @Operation
    public static final class PrepareStructuredMapMatchCall {
        @Specialization
        public static PreparedMapMatchCall ordinary(OrdinarySourceCall prepared) {
            return prepared.prepareStructuredMapMatch();
        }

        @Specialization
        public static PreparedMapMatchCall nativeCall(NativeCall prepared) {
            return prepared.prepareStructuredMapMatch();
        }

        @Specialization
        public static PreparedMapMatchCall immediate(ImmediateResultCall prepared) {
            return prepared.prepareStructuredMapMatch();
        }

        @Specialization
        public static PreparedMapMatchCall moduleInitialization(ModuleInitializationCall prepared) {
            return prepared.prepareStructuredMapMatch();
        }
    }

    @Operation
    public static final class StructuredMapMatchHasRequirement {
        @Specialization
        public static boolean perform(PreparedMapMatchCall prepared) {
            return prepared.hasRequirement();
        }
    }

    @Operation
    public static final class EnterStructuredMapMatchComparison {
        @Specialization
        public static void perform(PreparedMapMatchCall prepared) {
            prepared.enterComparison();
        }
    }

    @Operation
    public static final class LeaveStructuredMapMatchComparison {
        @Specialization
        public static void perform(PreparedMapMatchCall prepared) {
            prepared.leaveComparison();
        }
    }

    @Operation
    public static final class PrepareStructuredMapMatchHashCall {
        @Specialization
        public static PreparedClosureCall perform(
                PreparedMapMatchCall prepared) {
            return prepared.prepareHash();
        }
    }

    @Operation
    public static final class AcceptStructuredMapMatchHashResult {
        @Specialization
        public static void perform(
                PreparedMapMatchCall prepared,
                Object result) {
            prepared.acceptHash(result);
        }
    }

    @Operation
    public static final class StructuredMapMatchNeedsEquality {
        @Specialization
        public static boolean perform(PreparedMapMatchCall prepared) {
            return prepared.needsEquality();
        }
    }

    @Operation
    public static final class PrepareStructuredMapMatchEqualityCall {
        @Specialization
        public static PreparedClosureCall perform(
                PreparedMapMatchCall prepared) {
            return prepared.prepareEquality();
        }
    }

    @Operation
    public static final class AcceptStructuredMapMatchEqualityResult {
        @Specialization
        public static void perform(
                PreparedMapMatchCall prepared,
                Object result) {
            prepared.acceptEquality(result);
        }
    }

    @Operation
    public static final class FinishStructuredMapMatchRequirement {
        @Specialization
        public static void perform(PreparedMapMatchCall prepared) {
            prepared.finishRequirement();
        }
    }

    @Operation
    public static final class StructuredMapMatchHasChildMatcher {
        @Specialization
        public static boolean perform(PreparedMapMatchCall prepared) {
            return prepared.hasChildMatcher();
        }
    }

    @Operation
    public static final class PrepareStructuredMapMatchChildCall {
        @Specialization
        public static PreparedClosureCall perform(
                PreparedMapMatchCall prepared) {
            return prepared.prepareChildMatcher();
        }
    }

    @Operation
    public static final class AcceptStructuredMapMatchChildOutcome {
        @Specialization
        public static void perform(
                PreparedMapMatchCall prepared,
                Object outcome) {
            prepared.acceptChildOutcome(outcome);
        }
    }

    @Operation
    public static final class FinishStructuredMapMatch {
        @Specialization
        public static Object perform(PreparedMapMatchCall prepared) {
            return prepared.finish();
        }
    }

    @Operation
    public static final class IsStructuredMapReadLookupCall {
        @Specialization
        public static boolean ordinary(OrdinarySourceCall prepared) {
            return prepared.isStructuredMapReadLookup();
        }

        @Specialization
        public static boolean nativeCall(NativeCall prepared) {
            return prepared.isStructuredMapReadLookup();
        }

        @Specialization
        public static boolean immediate(ImmediateResultCall prepared) {
            return prepared.isStructuredMapReadLookup();
        }

        @Specialization
        public static boolean moduleInitialization(ModuleInitializationCall prepared) {
            return prepared.isStructuredMapReadLookup();
        }
    }

    @Operation
    public static final class PrepareStructuredMapReadLookupCall {
        @Specialization
        public static PreparedMapReadLookupCall ordinary(OrdinarySourceCall prepared) {
            return prepared.prepareStructuredMapReadLookup();
        }

        @Specialization
        public static PreparedMapReadLookupCall nativeCall(NativeCall prepared) {
            return prepared.prepareStructuredMapReadLookup();
        }

        @Specialization
        public static PreparedMapReadLookupCall immediate(ImmediateResultCall prepared) {
            return prepared.prepareStructuredMapReadLookup();
        }

        @Specialization
        public static PreparedMapReadLookupCall moduleInitialization(
                ModuleInitializationCall prepared) {
            return prepared.prepareStructuredMapReadLookup();
        }
    }

    @Operation
    public static final class EnterStructuredMapReadLookupComparison {
        @Specialization
        public static void perform(PreparedMapReadLookupCall prepared) {
            prepared.enterComparison();
        }
    }

    @Operation
    public static final class LeaveStructuredMapReadLookupComparison {
        @Specialization
        public static void perform(PreparedMapReadLookupCall prepared) {
            prepared.leaveComparison();
        }
    }

    @Operation
    public static final class PrepareStructuredMapReadLookupHashCall {
        @Specialization
        public static PreparedClosureCall perform(PreparedMapReadLookupCall prepared) {
            return prepared.prepareHash();
        }
    }

    @Operation
    public static final class AcceptStructuredMapReadLookupHashResult {
        @Specialization
        public static void perform(PreparedMapReadLookupCall prepared, Object result) {
            prepared.acceptHash(result);
        }
    }

    @Operation
    public static final class StructuredMapReadLookupNeedsEquality {
        @Specialization
        public static boolean perform(PreparedMapReadLookupCall prepared) {
            return prepared.needsEquality();
        }
    }

    @Operation
    public static final class PrepareStructuredMapReadLookupEqualityCall {
        @Specialization
        public static PreparedClosureCall perform(PreparedMapReadLookupCall prepared) {
            return prepared.prepareEquality();
        }
    }

    @Operation
    public static final class AcceptStructuredMapReadLookupEqualityResult {
        @Specialization
        public static void perform(PreparedMapReadLookupCall prepared, Object result) {
            prepared.acceptEquality(result);
        }
    }

    @Operation
    public static final class StructuredMapReadLookupNeedsFallback {
        @Specialization
        public static boolean perform(PreparedMapReadLookupCall prepared) {
            return prepared.needsFallback();
        }
    }

    @Operation
    public static final class PrepareStructuredMapReadLookupFallbackCall {
        @Specialization
        public static PreparedClosureCall perform(
                PreparedMapReadLookupCall prepared) {
            return prepared.prepareFallback();
        }
    }

    @Operation
    public static final class FinishStructuredMapReadLookupFallback {
        @Specialization
        public static Object perform(
                PreparedMapReadLookupCall prepared,
                Object result) {
            return prepared.finishFallback(result);
        }
    }

    @Operation
    public static final class FinishStructuredMapReadLookup {
        @Specialization
        public static Object perform(PreparedMapReadLookupCall prepared) {
            return prepared.finish();
        }
    }

    @Operation
    public static final class IsStructuredMapAtPutCall {
        @Specialization
        public static boolean ordinary(OrdinarySourceCall prepared) {
            return prepared.isStructuredMapAtPut();
        }

        @Specialization
        public static boolean nativeCall(NativeCall prepared) {
            return prepared.isStructuredMapAtPut();
        }

        @Specialization
        public static boolean immediate(ImmediateResultCall prepared) {
            return prepared.isStructuredMapAtPut();
        }

        @Specialization
        public static boolean moduleInitialization(ModuleInitializationCall prepared) {
            return prepared.isStructuredMapAtPut();
        }
    }

    @Operation
    public static final class PrepareStructuredMapAtPutCall {
        @Specialization
        public static PreparedMapAtPutCall ordinary(OrdinarySourceCall prepared) {
            return prepared.prepareStructuredMapAtPut();
        }

        @Specialization
        public static PreparedMapAtPutCall nativeCall(NativeCall prepared) {
            return prepared.prepareStructuredMapAtPut();
        }

        @Specialization
        public static PreparedMapAtPutCall immediate(ImmediateResultCall prepared) {
            return prepared.prepareStructuredMapAtPut();
        }

        @Specialization
        public static PreparedMapAtPutCall moduleInitialization(ModuleInitializationCall prepared) {
            return prepared.prepareStructuredMapAtPut();
        }
    }

    @Operation
    public static final class EnterStructuredMapAtPutComparison {
        @Specialization
        public static void perform(PreparedMapAtPutCall prepared) {
            prepared.enterComparison();
        }
    }

    @Operation
    public static final class LeaveStructuredMapAtPutComparison {
        @Specialization
        public static void perform(PreparedMapAtPutCall prepared) {
            prepared.leaveComparison();
        }
    }

    @Operation
    public static final class PrepareStructuredMapAtPutHashCall {
        @Specialization
        public static PreparedClosureCall perform(PreparedMapAtPutCall prepared) {
            return prepared.prepareHash();
        }
    }

    @Operation
    public static final class AcceptStructuredMapAtPutHashResult {
        @Specialization
        public static void perform(PreparedMapAtPutCall prepared, Object result) {
            prepared.acceptHash(result);
        }
    }

    @Operation
    public static final class StructuredMapAtPutNeedsEquality {
        @Specialization
        public static boolean perform(PreparedMapAtPutCall prepared) {
            return prepared.needsEquality();
        }
    }

    @Operation
    public static final class PrepareStructuredMapAtPutEqualityCall {
        @Specialization
        public static PreparedClosureCall perform(PreparedMapAtPutCall prepared) {
            return prepared.prepareEquality();
        }
    }

    @Operation
    public static final class AcceptStructuredMapAtPutEqualityResult {
        @Specialization
        public static void perform(PreparedMapAtPutCall prepared, Object result) {
            prepared.acceptEquality(result);
        }
    }

    @Operation
    public static final class FinishStructuredMapAtPut {
        @Specialization
        public static Object perform(PreparedMapAtPutCall prepared) {
            return prepared.finish();
        }
    }

    @Operation
    public static final class IsStructuredMapRemoveCall {
        @Specialization
        public static boolean ordinary(OrdinarySourceCall prepared) {
            return prepared.isStructuredMapRemove();
        }

        @Specialization
        public static boolean nativeCall(NativeCall prepared) {
            return prepared.isStructuredMapRemove();
        }

        @Specialization
        public static boolean immediate(ImmediateResultCall prepared) {
            return prepared.isStructuredMapRemove();
        }

        @Specialization
        public static boolean moduleInitialization(ModuleInitializationCall prepared) {
            return prepared.isStructuredMapRemove();
        }
    }

    @Operation
    public static final class PrepareStructuredMapRemoveCall {
        @Specialization
        public static PreparedMapRemoveCall ordinary(OrdinarySourceCall prepared) {
            return prepared.prepareStructuredMapRemove();
        }

        @Specialization
        public static PreparedMapRemoveCall nativeCall(NativeCall prepared) {
            return prepared.prepareStructuredMapRemove();
        }

        @Specialization
        public static PreparedMapRemoveCall immediate(ImmediateResultCall prepared) {
            return prepared.prepareStructuredMapRemove();
        }

        @Specialization
        public static PreparedMapRemoveCall moduleInitialization(
                ModuleInitializationCall prepared) {
            return prepared.prepareStructuredMapRemove();
        }
    }

    @Operation
    public static final class EnterStructuredMapRemoveComparison {
        @Specialization
        public static void perform(PreparedMapRemoveCall prepared) {
            prepared.enterComparison();
        }
    }

    @Operation
    public static final class LeaveStructuredMapRemoveComparison {
        @Specialization
        public static void perform(PreparedMapRemoveCall prepared) {
            prepared.leaveComparison();
        }
    }

    @Operation
    public static final class PrepareStructuredMapRemoveHashCall {
        @Specialization
        public static PreparedClosureCall perform(PreparedMapRemoveCall prepared) {
            return prepared.prepareHash();
        }
    }

    @Operation
    public static final class AcceptStructuredMapRemoveHashResult {
        @Specialization
        public static void perform(PreparedMapRemoveCall prepared, Object result) {
            prepared.acceptHash(result);
        }
    }

    @Operation
    public static final class StructuredMapRemoveNeedsEquality {
        @Specialization
        public static boolean perform(PreparedMapRemoveCall prepared) {
            return prepared.needsEquality();
        }
    }

    @Operation
    public static final class PrepareStructuredMapRemoveEqualityCall {
        @Specialization
        public static PreparedClosureCall perform(PreparedMapRemoveCall prepared) {
            return prepared.prepareEquality();
        }
    }

    @Operation
    public static final class AcceptStructuredMapRemoveEqualityResult {
        @Specialization
        public static void perform(PreparedMapRemoveCall prepared, Object result) {
            prepared.acceptEquality(result);
        }
    }

    @Operation
    public static final class FinishStructuredMapRemove {
        @Specialization
        public static Object perform(PreparedMapRemoveCall prepared) {
            return prepared.finish();
        }
    }

    @Operation
    public static final class IsStructuredMapEachCall {
        @Specialization
        public static boolean ordinary(OrdinarySourceCall prepared) {
            return prepared.isStructuredMapEach();
        }

        @Specialization
        public static boolean nativeCall(NativeCall prepared) {
            return prepared.isStructuredMapEach();
        }

        @Specialization
        public static boolean immediate(ImmediateResultCall prepared) {
            return prepared.isStructuredMapEach();
        }

        @Specialization
        public static boolean moduleInitialization(ModuleInitializationCall prepared) {
            return prepared.isStructuredMapEach();
        }
    }

    @Operation
    public static final class PrepareStructuredMapEachCall {
        @Specialization
        public static PreparedMapEachCall ordinary(OrdinarySourceCall prepared) {
            return prepared.prepareStructuredMapEach();
        }

        @Specialization
        public static PreparedMapEachCall nativeCall(NativeCall prepared) {
            return prepared.prepareStructuredMapEach();
        }

        @Specialization
        public static PreparedMapEachCall immediate(ImmediateResultCall prepared) {
            return prepared.prepareStructuredMapEach();
        }

        @Specialization
        public static PreparedMapEachCall moduleInitialization(ModuleInitializationCall prepared) {
            return prepared.prepareStructuredMapEach();
        }
    }

    @Operation
    public static final class StructuredMapEachHasNext {
        @Specialization
        public static boolean perform(PreparedMapEachCall prepared) {
            return prepared.hasNext();
        }
    }

    @Operation
    public static final class PrepareStructuredMapEachEntryCall {
        @Specialization
        public static PreparedClosureCall perform(PreparedMapEachCall prepared) {
            return prepared.prepareCurrent();
        }
    }

    @Operation
    public static final class AdvanceStructuredMapEach {
        @Specialization
        public static void perform(PreparedMapEachCall prepared) {
            prepared.advance();
        }
    }

    @Operation
    public static final class FinishStructuredMapEach {
        @Specialization
        public static Object perform(PreparedMapEachCall prepared) {
            return prepared.finish();
        }
    }

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
    public static final class IsStructuredErrorHandlerCall {
        @Specialization
        public static boolean ordinary(OrdinarySourceCall prepared) {
            return prepared.isStructuredErrorHandler();
        }

        @Specialization
        public static boolean nativeCall(NativeCall prepared) {
            return prepared.isStructuredErrorHandler();
        }

        @Specialization
        public static boolean immediate(ImmediateResultCall prepared) {
            return prepared.isStructuredErrorHandler();
        }

        @Specialization
        public static boolean moduleInitialization(ModuleInitializationCall prepared) {
            return prepared.isStructuredErrorHandler();
        }
    }

    @Operation
    public static final class PrepareStructuredErrorHandlerCall {
        @Specialization
        public static PreparedErrorHandlerCall ordinary(OrdinarySourceCall prepared) {
            return prepared.prepareStructuredErrorHandler();
        }

        @Specialization
        public static PreparedErrorHandlerCall nativeCall(NativeCall prepared) {
            return prepared.prepareStructuredErrorHandler();
        }

        @Specialization
        public static PreparedErrorHandlerCall immediate(ImmediateResultCall prepared) {
            return prepared.prepareStructuredErrorHandler();
        }

        @Specialization
        public static PreparedErrorHandlerCall moduleInitialization(
                ModuleInitializationCall prepared) {
            return prepared.prepareStructuredErrorHandler();
        }
    }

    @Operation
    public static final class LoadStructuredErrorHandlerBodyCall {
        @Specialization
        public static PreparedClosureCall perform(PreparedErrorHandlerCall prepared) {
            return prepared.body();
        }
    }

    @Operation
    public static final class PrepareSelectedStructuredErrorHandlerCall {
        @Specialization
        public static PreparedClosureCall perform(
                PreparedErrorHandlerCall prepared,
                AbstractTruffleException exception) {
            return prepared.selectHandler(exception);
        }
    }

    @Operation
    public static final class LeaveStructuredErrorHandlerFrame {
        @Specialization
        public static void perform(PreparedErrorHandlerCall prepared) {
            prepared.leave();
        }
    }

    @Operation
    public static final class PrepareBufferedByteReaderTargetCall {
        @Specialization
        public static PreparedClosureCall perform(
                ProtosBufferedByteReaderCPrimeExecution.CallState state,
                ProtosActivation activation) {
            return ProtosSendPreparation.prepareSend(
                    state.target(),
                    "read",
                    activation,
                    state.arguments());
        }
    }

    @Operation
    public static final class WrapBufferedByteReaderTargetInvocation {
        @Specialization
        public static Object perform(
                ProtosBufferedByteReaderCPrimeExecution.CallState state,
                Object result) {
            return state.invocationSucceeded(result);
        }
    }

    @Operation
    public static final class BufferedByteReaderTargetInvocationFailed {
        @Specialization
        public static Object perform(
                ProtosBufferedByteReaderCPrimeExecution.CallState state) {
            return state.invocationFailed();
        }
    }

    @Operation
    public static final class AwaitBufferedByteReaderTargetFuture {
        @Specialization
        public static Object perform(
                ProtosBufferedByteReaderCPrimeExecution.CallState state,
                Object invocation) {
            if (!(invocation
                    instanceof ProtosBufferedByteReaderCPrimeExecution.TargetInvocation
                            targetInvocation)) {
                throw new IllegalStateException(
                        "BufferedReader C-prime invocation produced an invalid carrier");
            }
            return state.awaitTargetFuture(targetInvocation);
        }
    }

    @Operation
    public static final class ResumeBufferedByteReaderTargetFutureWait {
        @Specialization
        public static Object perform(
                ProtosActivation activation,
                Object yielded,
                Object resumeValue) {
            if (!(yielded instanceof ProtosIoOperationSuspension suspension)) {
                throw new IllegalStateException(
                        "BufferedReader lower-Future wait yielded an invalid carrier");
            }
            ProtosIoOperation operation =
                    activation.deferredCPrimeOperationForRuntime()
                            .orElseThrow(
                                    () ->
                                            new IllegalStateException(
                                                    "BufferedReader lower-Future wait requires an operation-owned activation"));
            if (suspension.operation() != operation) {
                throw new IllegalStateException(
                        "BufferedReader lower-Future wait belongs to another operation");
            }
            if (resumeValue != ProtosNullValue.INSTANCE) {
                throw new IllegalStateException(
                        "BufferedReader lower-Future wait received unsupported resume transport");
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

    @Operation
    public static final class IsBufferedByteWriterStepRequired {
        @Specialization
        public static boolean perform(Object value) {
            if (!(value instanceof Boolean required)) {
                throw new IllegalStateException(
                        "BufferedWriter C-prime step gate produced a non-Boolean value");
            }
            return required;
        }
    }

    @Operation
    public static final class PrepareBufferedByteWriterFirstCall {
        @Specialization
        public static PreparedClosureCall perform(
                ProtosBufferedByteWriterCPrimeExecution.CallState state,
                ProtosActivation activation) {
            return ProtosSendPreparation.prepareSend(
                    state.target(),
                    state.firstSelector(),
                    activation,
                    state.firstArguments());
        }
    }

    @Operation
    public static final class PrepareBufferedByteWriterFlushCall {
        @Specialization
        public static PreparedClosureCall perform(
                ProtosBufferedByteWriterCPrimeExecution.CallState state,
                ProtosActivation activation) {
            return ProtosSendPreparation.prepareSend(
                    state.target(),
                    "flush",
                    activation,
                    List.of());
        }
    }

    @Operation
    public static final class WrapBufferedByteWriterTargetInvocation {
        @Specialization
        public static Object perform(
                ProtosBufferedByteWriterCPrimeExecution.CallState state,
                Object result) {
            return state.invocationSucceeded(result);
        }
    }

    @Operation
    public static final class BufferedByteWriterTargetInvocationFailed {
        @Specialization
        public static Object perform(
                ProtosBufferedByteWriterCPrimeExecution.CallState state) {
            return state.invocationFailed();
        }
    }

    @Operation
    public static final class AwaitBufferedByteWriterTargetFuture {
        @Specialization
        public static Object perform(
                ProtosBufferedByteWriterCPrimeExecution.CallState state,
                Object invocation) {
            if (!(invocation
                    instanceof ProtosBufferedByteWriterCPrimeExecution.TargetInvocation
                            targetInvocation)) {
                throw new IllegalStateException(
                        "BufferedWriter C-prime invocation produced an invalid carrier");
            }
            return state.awaitTargetFuture(targetInvocation);
        }
    }

    @Operation
    public static final class ResumeBufferedByteWriterTargetFutureWait {
        @Specialization
        public static Object perform(
                ProtosActivation activation,
                Object yielded,
                Object resumeValue) {
            if (!(yielded instanceof ProtosIoOperationSuspension suspension)) {
                throw new IllegalStateException(
                        "BufferedWriter lower-Future wait yielded an invalid carrier");
            }
            ProtosIoOperation operation =
                    activation.deferredCPrimeOperationForRuntime()
                            .orElseThrow(
                                    () ->
                                            new IllegalStateException(
                                                    "BufferedWriter lower-Future wait requires an operation-owned activation"));
            if (suspension.operation() != operation) {
                throw new IllegalStateException(
                        "BufferedWriter lower-Future wait belongs to another operation");
            }
            if (resumeValue != ProtosNullValue.INSTANCE) {
                throw new IllegalStateException(
                        "BufferedWriter lower-Future wait received unsupported resume transport");
            }
            return suspension.resume();
        }
    }

    @Operation
    public static final class ApplyBufferedByteWriterFirstOutcome {
        @Specialization
        public static boolean perform(
                ProtosBufferedByteWriterCPrimeExecution.CallState state,
                Object outcome) {
            if (!(outcome
                    instanceof ProtosBufferedByteWriterCPrimeExecution.LowerOutcome
                            lowerOutcome)) {
                throw new IllegalStateException(
                        "BufferedWriter first lower wait produced an invalid carrier");
            }
            return state.applyFirstOutcome(lowerOutcome);
        }
    }

    @Operation
    public static final class ApplyBufferedByteWriterFollowupOutcome {
        @Specialization
        public static boolean perform(
                ProtosBufferedByteWriterCPrimeExecution.CallState state,
                Object outcome) {
            if (!(outcome
                    instanceof ProtosBufferedByteWriterCPrimeExecution.LowerOutcome
                            lowerOutcome)) {
                throw new IllegalStateException(
                        "BufferedWriter followup lower wait produced an invalid carrier");
            }
            return state.applyFollowupFlushOutcome(lowerOutcome);
        }
    }


    @Operation
    public static final class IoReleaseHasNextStep {
        @Specialization
        public static boolean perform(
                ProtosIoReleaseCPrimeExecution.Sequence sequence) {
            return sequence.hasNextStep();
        }
    }

    @Operation
    public static final class PrepareIoReleaseTargetCall {
        @Specialization
        public static PreparedClosureCall perform(
                ProtosIoReleaseCPrimeExecution.Sequence sequence,
                ProtosActivation activation) {
            return ProtosSendPreparation.prepareSend(
                    sequence.target(),
                    sequence.selector(),
                    activation,
                    sequence.arguments());
        }
    }

    @Operation
    public static final class WrapIoReleaseTargetInvocation {
        @Specialization
        public static Object perform(
                ProtosIoReleaseCPrimeExecution.Sequence sequence,
                Object result) {
            return sequence.invocationSucceeded(result);
        }
    }

    @Operation
    public static final class IoReleaseTargetInvocationFailed {
        @Specialization
        public static Object perform(
                ProtosIoReleaseCPrimeExecution.Sequence sequence) {
            return sequence.invocationFailed();
        }
    }

    @Operation
    public static final class AwaitIoReleaseTargetFuture {
        @Specialization
        public static Object perform(
                ProtosIoReleaseCPrimeExecution.Sequence sequence,
                Object invocation) {
            if (!(invocation
                    instanceof ProtosIoReleaseCPrimeExecution.TargetInvocation
                            targetInvocation)) {
                throw new IllegalStateException(
                        "lifecycle release C-prime invocation produced an invalid carrier");
            }
            return sequence.awaitTargetFuture(targetInvocation);
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
    public static final class ApplyIoReleaseTargetOutcome {
        @Specialization
        public static void perform(
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
    }

    @Operation
    public static final class FinishIoReleaseSequence {
        @Specialization
        public static Object perform(
                ProtosIoReleaseCPrimeExecution.Sequence sequence) {
            return sequence.finish();
        }
    }

    @Operation
    public static final class PrepareTextWriterTargetCall {
        @Specialization
        public static PreparedClosureCall perform(
                ProtosTextWriterCPrimeExecution.CallState state,
                ProtosActivation activation) {
            return ProtosSendPreparation.prepareSend(
                    state.target(),
                    state.selector(),
                    activation,
                    state.arguments());
        }
    }

    @Operation
    public static final class WrapTextWriterTargetInvocation {
        @Specialization
        public static Object perform(
                ProtosTextWriterCPrimeExecution.CallState state,
                Object result) {
            return state.invocationSucceeded(result);
        }
    }

    @Operation
    public static final class TextWriterTargetInvocationFailed {
        @Specialization
        public static Object perform(
                ProtosTextWriterCPrimeExecution.CallState state) {
            return state.invocationFailed();
        }
    }

    @Operation
    public static final class AwaitTextWriterTargetFuture {
        @Specialization
        public static Object perform(
                ProtosTextWriterCPrimeExecution.CallState state,
                Object invocation) {
            if (!(invocation
                    instanceof ProtosTextWriterCPrimeExecution.TargetInvocation targetInvocation)) {
                throw new IllegalStateException(
                        "TextWriter C-prime invocation produced an invalid carrier");
            }
            return state.awaitTargetFuture(targetInvocation);
        }
    }

    @Operation
    public static final class ResumeTextWriterTargetFutureWait {
        @Specialization
        public static Object perform(
                ProtosActivation activation,
                Object yielded,
                Object resumeValue) {
            if (!(yielded instanceof ProtosIoOperationSuspension suspension)) {
                throw new IllegalStateException(
                        "TextWriter lower-Future wait yielded an invalid carrier");
            }
            ProtosIoOperation operation =
                    activation.deferredCPrimeOperationForRuntime()
                            .orElseThrow(
                                    () ->
                                            new IllegalStateException(
                                                    "TextWriter lower-Future wait requires an operation-owned activation"));
            if (suspension.operation() != operation) {
                throw new IllegalStateException(
                        "TextWriter lower-Future wait belongs to another operation");
            }
            if (resumeValue != ProtosNullValue.INSTANCE) {
                throw new IllegalStateException(
                        "TextWriter lower-Future wait received unsupported resume transport");
            }
            return suspension.resume();
        }
    }

    @Operation
    public static final class PrepareTextReaderSourceCall {
        @Specialization
        public static PreparedClosureCall perform(
                ProtosTextReaderCPrimeExecution.CallState state,
                ProtosActivation activation) {
            return ProtosSendPreparation.prepareSend(
                    state.source(),
                    "read",
                    activation,
                    state.arguments());
        }
    }

    @Operation
    public static final class WrapTextReaderSourceInvocation {
        @Specialization
        public static Object perform(
                ProtosTextReaderCPrimeExecution.CallState state,
                Object result) {
            return state.invocationSucceeded(result);
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
    public static final class AwaitTextReaderSourceFuture {
        @Specialization
        public static Object perform(
                ProtosTextReaderCPrimeExecution.CallState state,
                Object invocation) {
            if (!(invocation
                    instanceof ProtosTextReaderCPrimeExecution.SourceInvocation sourceInvocation)) {
                throw new IllegalStateException(
                        "TextReader C-prime invocation produced an invalid carrier");
            }
            return state.awaitSourceFuture(sourceInvocation);
        }
    }

    @Operation
    public static final class ResumeTextReaderSourceFutureWait {
        @Specialization
        public static Object perform(
                ProtosActivation activation,
                Object yielded,
                Object resumeValue) {
            if (!(yielded instanceof ProtosIoOperationSuspension suspension)) {
                throw new IllegalStateException(
                        "TextReader lower-Future wait yielded an invalid carrier");
            }
            ProtosIoOperation operation =
                    activation.deferredCPrimeOperationForRuntime()
                            .orElseThrow(
                                    () ->
                                            new IllegalStateException(
                                                    "TextReader lower-Future wait requires an operation-owned activation"));
            if (suspension.operation() != operation) {
                throw new IllegalStateException(
                        "TextReader lower-Future wait belongs to another operation");
            }
            if (resumeValue != ProtosNullValue.INSTANCE) {
                throw new IllegalStateException(
                        "TextReader lower-Future wait received unsupported resume transport");
            }
            return suspension.resume();
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
    public static final class IsTextReaderNeedInput {
        @Specialization
        public static boolean perform(Object advance) {
            if (!(advance instanceof ProtosTextReaderCPrimeExecution.Advance state)) {
                throw new IllegalStateException(
                        "TextReader C-prime advance produced an invalid carrier");
            }
            return state.needsInput();
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
