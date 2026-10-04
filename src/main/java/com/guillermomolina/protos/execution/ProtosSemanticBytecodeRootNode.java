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

import com.guillermomolina.protos.execution.ProtosBytecodeRootNode.CapturedLexicalWriteTarget;
import com.guillermomolina.protos.execution.ProtosBytecodeRootNode.GuardedDirectClosureCallTarget;
import com.guillermomolina.protos.execution.ProtosBytecodeRootNode.PrepareSendArguments.GuardedIntegerSend;
import com.guillermomolina.protos.execution.ProtosBytecodeRootNode.PrepareSendArguments.GuardedSendTarget;
import com.guillermomolina.protos.execution.ProtosBytecodeRootNode.PrepareSendArguments.GuardedStructuredSend;
import com.guillermomolina.protos.execution.ProtosBytecodeRootNode.PreparedArgumentVector;
import com.guillermomolina.protos.execution.ProtosBytecodeRootNode.PreparedBooleanCall;
import com.guillermomolina.protos.execution.ProtosBytecodeRootNode.PreparedClosureCall;
import com.guillermomolina.protos.execution.ProtosBytecodeRootNode.PreparedInlineLiteralCall;
import com.guillermomolina.protos.execution.ProtosBytecodeRootNode.PreparedIndexedEachCall;
import com.guillermomolina.protos.execution.ProtosBytecodeRootNode.PreparedLocalEachCall;
import com.guillermomolina.protos.execution.ProtosBytecodeRootNode.PreparedMapInitialDefinition;
import com.guillermomolina.protos.execution.ProtosBytecodeRootNode.PreparedWhileCall;
import com.guillermomolina.protos.execution.ProtosBytecodeRootNode.ResolvedLexicalWriteTarget;
import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosMapValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSlotLookupResult;
import com.guillermomolina.protos.runtime.ProtosValueLookup;
import com.guillermomolina.protos.semantic.ast.CanonicalClosure;
import com.guillermomolina.protos.semantic.ast.CanonicalIntrinsic;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.bytecode.BytecodeNode;
import com.oracle.truffle.api.bytecode.BytecodeRootNode;
import com.oracle.truffle.api.bytecode.ConstantOperand;
import com.oracle.truffle.api.bytecode.ContinuationResult;
import com.oracle.truffle.api.bytecode.ContinuationRootNode;
import com.oracle.truffle.api.bytecode.GenerateBytecode;
import com.oracle.truffle.api.bytecode.LocalAccessor;
import com.oracle.truffle.api.bytecode.LocalRangeAccessor;
import com.oracle.truffle.api.bytecode.MaterializedLocalAccessor;
import com.oracle.truffle.api.bytecode.Operation;
import com.oracle.truffle.api.bytecode.Variadic;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ControlFlowException;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;

/**
 * PLAT042 Candidate B′ tagged semantic source interpreter.
 *
 * <p>Every root of this generated interpreter is a guest-semantic top-level,
 * module or Closure activation produced by {@link CanonicalToBytecodeLowerer};
 * its automatic RootTag is therefore truthful. Object bodies are lowered inline
 * and never become roots. Lexically nested Closure roots are created in the same
 * {@code BytecodeRootNodes} parse as their owner, so PERF013 materialized
 * captured accesses use a {@link MaterializedLocalAccessor} of this interpreter
 * only against an owner frame of this interpreter.
 *
 * <p>Structured prepared invocations other than the PLAT043 standard Boolean
 * family are not executed here. {@link
 * EnterNestedStructuredDispatch} enters the untagged {@link
 * ProtosBytecodeRootNode} structured/C-prime root once per such invocation and
 * this root composes the returned C-prime continuation (PLAT014).
 *
 * <p>The operations below are the source-surface subset of the canonical
 * operation set. Each delegates to the single implementation owned by {@link
 * ProtosBytecodeRootNode}; only the operation declarations (operands,
 * specialization guards and caches) are repeated, because one generated Bytecode
 * DSL interpreter cannot declare operations of another and {@code
 * OperationProxy} requires Node-based proxyable classes.
 */
@GenerateBytecode(
        languageClass = ProtosLanguage.class,
        enableYield = true,
        enableTagInstrumentation = true,
        enableRootTagging = true,
        enableRootBodyTagging = false,
        enableMaterializedLocalAccesses = true,
        enableTailCallHandlers = true,
        enableUncachedInterpreter = true,
        boxingEliminationTypes = {int.class},
        tagTreeNodeLibrary = ProtosBytecodeTagTreeNodeExports.class)
abstract class ProtosSemanticBytecodeRootNode extends RootNode implements BytecodeRootNode {
    protected ProtosSemanticBytecodeRootNode(
            ProtosLanguage language,
            FrameDescriptor frameDescriptor) {
        super(language, frameDescriptor);
    }

    /*
     * PERF025 frame-materialization slice: the frame-binding range and layout
     * of a Closure root lowered without InstallFrameLexicalAuthority, or null.
     * Read only by tooling scope projection, which receives the live frame.
     */
    private LocalRangeAccessor frameNativeBindingLocals;
    private ProtosFrameLexicalLayout frameNativeBindingLayout;

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
     * live frame of an executing activation of this root; only its
     * materialized form is retained.
     */
    final void installFrameNativeAuthorityForTooling(
            ProtosActivation activation,
            BytecodeNode bytecodeNode,
            com.oracle.truffle.api.frame.Frame frame) {
        if (frameNativeBindingLocals == null) {
            return;
        }
        ProtosBytecodeRootNode.installFrameLexicalAuthorityOnTransition(
                frameNativeBindingLocals,
                frameNativeBindingLayout,
                activation,
                bytecodeNode,
                frame.materialize());
    }

    @Override
    public Object interceptControlFlowException(
            ControlFlowException transfer,
            VirtualFrame frame,
            BytecodeNode bytecodeNode,
            int bytecodeIndex)
            throws Throwable {
        throw ProtosBytecodeControlTransferException.bridge(transfer, bytecodeNode, bytecodeIndex);
    }

    @Override
    public AbstractTruffleException interceptTruffleException(
            AbstractTruffleException exception,
            VirtualFrame frame,
            BytecodeNode bytecodeNode,
            int bytecodeIndex) {
        /*
         * CLI008-C1: Protos exceptions carry no location node, so the guest stack trace has no
         * bytecode index for the frame they are raised in. Record the first semantic crossing on
         * the occurrence itself; this runs only while an exception is already unwinding.
         */
        ProtosDiagnosticTraceCapture.recordOrigin(exception, frame, bytecodeNode, bytecodeIndex);
        return ProtosBytecodeRootNode.interceptGuestException(exception, frame);
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
    @Operation
    public static final class CurrentActivation {
        @Specialization
        public static ProtosActivation perform(@Bind("$frame") VirtualFrame frame) {
            return ProtosFrameArguments.activation(frame);
        }
    }

    // ---- Closure parameter binding -------------------------------------------------

    @Operation
    public static final class HasClosureArgument {
        @Specialization
        public static boolean perform(ProtosActivation activation, int positionalIndex) {
            return ProtosBytecodeRootNode.HasClosureArgument.perform(activation, positionalIndex);
        }
    }

    @Operation
    public static final class LoadClosureArgument {
        @Specialization
        public static Object perform(ProtosActivation activation, int positionalIndex) {
            return ProtosBytecodeRootNode.LoadClosureArgument.perform(activation, positionalIndex);
        }
    }

    /*
     * PERF025-H1 root-level parameter operations. The lowerer emits these
     * instead of the activation-operand forms whenever the current activation
     * is the root's own (never inside an inline Object body or inline callback
     * region). While the frame is still in compact source-call form, the
     * supplied positional values are read directly from the frame arguments;
     * an arity Error, like every other path, materializes the exact activation
     * first and takes the unchanged activation implementation.
     */

    @Operation
    public static final class HasFrameClosureArgument {
        @Specialization
        public static boolean perform(int positionalIndex, @Bind("$frame") VirtualFrame frame) {
            Object[] arguments = frame.getArguments();
            if (ProtosFrameArguments.isUnmaterializedCompactCall(arguments)) {
                return ProtosFrameArguments.compactSuppliedArgumentCount(arguments)
                        > positionalIndex;
            }
            return ProtosBytecodeRootNode.HasClosureArgument.perform(
                    ProtosFrameArguments.activation(arguments), positionalIndex);
        }
    }

    @Operation
    public static final class LoadFrameClosureArgument {
        @Specialization
        public static Object perform(int positionalIndex, @Bind("$frame") VirtualFrame frame) {
            Object[] arguments = frame.getArguments();
            if (ProtosFrameArguments.isUnmaterializedCompactCall(arguments)
                    && positionalIndex
                            < ProtosFrameArguments.compactSuppliedArgumentCount(arguments)) {
                return ProtosFrameArguments.compactSuppliedArgument(arguments, positionalIndex);
            }
            return ProtosBytecodeRootNode.LoadClosureArgument.perform(
                    ProtosFrameArguments.activation(arguments), positionalIndex);
        }
    }

    @Operation
    public static final class CheckFrameClosureArgumentUpperBound {
        @Specialization
        public static void perform(
                int maximumPositionalArguments,
                @Bind("$frame") VirtualFrame frame) {
            Object[] arguments = frame.getArguments();
            if (ProtosFrameArguments.isUnmaterializedCompactCall(arguments)
                    && ProtosFrameArguments.compactSuppliedArgumentCount(arguments)
                            <= maximumPositionalArguments) {
                return;
            }
            ProtosBytecodeRootNode.CheckClosureArgumentUpperBound.perform(
                    ProtosFrameArguments.activation(arguments), maximumPositionalArguments);
        }
    }

    @Operation
    public static final class BindClosureParameter {
        @Specialization
        public static void perform(ProtosActivation activation, String name, Object value) {
            ProtosBytecodeRootNode.BindClosureParameter.perform(activation, name, value);
        }
    }

    @Operation
    public static final class BindClosureRest {
        @Specialization
        public static void perform(
                ProtosActivation activation,
                String name,
                int positionalParametersBeforeRest) {
            ProtosBytecodeRootNode.BindClosureRest.perform(
                    activation, name, positionalParametersBeforeRest);
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
            ProtosBytecodeRootNode.bindIndexedClosureParameter(
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
            ProtosBytecodeRootNode.bindIndexedClosureRest(
                    frameBackedLayout, activation, ordinal, name, positionalParametersBeforeRest);
        }
    }

    @Operation
    public static final class CheckClosureArgumentUpperBound {
        @Specialization
        public static void perform(ProtosActivation activation, int maximumPositionalArguments) {
            ProtosBytecodeRootNode.CheckClosureArgumentUpperBound.perform(
                    activation, maximumPositionalArguments);
        }
    }

    // ---- Lexical lookup and frame-backed bindings ----------------------------------

    @Operation
    public static final class Lookup {
        @Specialization
        public static Object perform(ProtosActivation activation, String name) {
            return ProtosBytecodeRootNode.Lookup.perform(activation, name);
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
                @Bind("$bytecodeNode") BytecodeNode bytecodeNode,
                @Bind("$frame") VirtualFrame frame) {
            ProtosBytecodeRootNode.InstallFrameLexicalAuthority.perform(
                    frameBackedLocals, frameBackedLayout, activation, bytecodeNode, frame);
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
         */
        @Specialization
        public static void perform(
                LocalRangeAccessor frameBackedLocals,
                ProtosFrameLexicalLayout frameBackedLayout,
                int ordinal,
                String name,
                Object value,
                @Bind("$bytecodeNode") BytecodeNode bytecodeNode,
                @Bind("$frame") VirtualFrame frame) {
            Object[] arguments = frame.getArguments();
            if (ProtosFrameArguments.isUnmaterializedCompactCall(arguments)
                    && frameBackedLocals.isCleared(bytecodeNode, frame, ordinal)) {
                frameBackedLocals.setObject(bytecodeNode, frame, ordinal, value);
                return;
            }
            ProtosBytecodeRootNode.BindClosureFrameParameter.perform(
                    frameBackedLocals, frameBackedLayout, ordinal,
                    ProtosFrameArguments.activation(arguments), name, value, bytecodeNode, frame);
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
                @Bind("$bytecodeNode") BytecodeNode bytecodeNode,
                @Bind("$frame") VirtualFrame frame) {
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
                @Bind("$bytecodeNode") BytecodeNode bytecodeNode,
                @Bind("$frame") VirtualFrame frame) {
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
    public static final class CheckInlineClosureArgumentUpperBound {
        @Specialization
        public static void perform(
                LocalRangeAccessor frameBackedLocals,
                ProtosFrameLexicalLayout frameBackedLayout,
                PreparedInlineLiteralCall child,
                int maximumPositionalArguments,
                @Bind("$bytecodeNode") BytecodeNode bytecodeNode,
                @Bind("$frame") VirtualFrame frame) {
            ProtosInlineCallbackFrameBindings.checkArgumentUpperBound(
                    child, frameBackedLocals, frameBackedLayout,
                    maximumPositionalArguments, bytecodeNode, frame);
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
                @Bind("$bytecodeNode") BytecodeNode bytecodeNode,
                @Bind("$frame") VirtualFrame frame) {
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
                @Bind("$bytecodeNode") BytecodeNode bytecodeNode,
                @Bind("$frame") VirtualFrame frame) {
            ProtosBytecodeRootNode.BindClosureFrameRest.perform(
                    frameBackedLocals, frameBackedLayout, ordinal,
                    activation, name, positionalParametersBeforeRest, bytecodeNode, frame);
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
                @Bind("$bytecodeNode") BytecodeNode bytecodeNode,
                @Bind("$frame") VirtualFrame frame) {
            Object[] arguments = frame.getArguments();
            if (ProtosFrameArguments.isUnmaterializedCompactCall(arguments)
                    && frameBackedLocals.isCleared(bytecodeNode, frame, ordinal)) {
                frameBackedLocals.setObject(bytecodeNode, frame, ordinal, value);
                return value;
            }
            return ProtosBytecodeRootNode.CreateCurrentFrameLocal.perform(
                    frameBackedLocals, frameBackedLayout, ordinal,
                    ProtosFrameArguments.activation(arguments), name, value, bytecodeNode, frame);
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
                @Bind("$bytecodeNode") BytecodeNode bytecodeNode,
                @Bind("$frame") VirtualFrame frame) {
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
            return ProtosBytecodeRootNode.observeMultipleCreatePrefix(activation, required, source)
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
                @Bind("$bytecodeNode") BytecodeNode bytecodeNode,
                @Bind("$frame") VirtualFrame frame) {
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
                @Bind("$bytecodeNode") BytecodeNode bytecodeNode,
                @Bind("$frame") VirtualFrame frame) {
            return ProtosBytecodeRootNode.ReadFrameLocal.perform(
                    accessor,
                    presenceContinuity,
                    activation,
                    name,
                    bytecodeNode,
                    frame);
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
    public static final class ReadRootFrameLocal {
        @Specialization
        public static Object perform(
                LocalAccessor accessor,
                Assumption presenceContinuity,
                String name,
                @Bind("$bytecodeNode") BytecodeNode bytecodeNode,
                @Bind("$frame") VirtualFrame frame) {
            Object[] arguments = frame.getArguments();
            if (ProtosFrameArguments.isUnmaterializedCompactCall(arguments)) {
                /*
                 * The lowerer emitted this operation only for a current
                 * Resolved binding. While the call is still compact its guest
                 * Context has never become observable, so no D179 structural
                 * removal could have made that established binding ABSENT.
                 */
                return accessor.getObject(bytecodeNode, frame);
            }
            return ProtosBytecodeRootNode.ReadFrameLocal.perform(
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
                @Bind("$bytecodeNode") BytecodeNode bytecodeNode,
                @Bind("$frame") VirtualFrame frame) {
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

    @Operation
    @ConstantOperand(type = int.class, name = "frameOrdinal")
    public static final class ReadCapturedFrameLocal {
        @Specialization
        public static Object perform(
                int frameOrdinal,
                ProtosActivation activation,
                String name,
                int lexicalDepth) {
            return ProtosBytecodeRootNode.ReadCapturedFrameLocal.perform(
                    frameOrdinal, activation, name, lexicalDepth);
        }
    }

    @Operation
    @ConstantOperand(type = MaterializedLocalAccessor.class)
    public static final class ReadCapturedMaterializedLocal {
        @Specialization
        public static Object perform(
                MaterializedLocalAccessor accessor,
                ProtosActivation activation,
                String name,
                int lexicalDepth,
                @Bind("$bytecodeNode") BytecodeNode bytecodeNode) {
            return ProtosBytecodeRootNode.ReadCapturedMaterializedLocal.perform(
                    accessor, activation, name, lexicalDepth, bytecodeNode);
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
            return ProtosBytecodeRootNode.ResolveCapturedWritableLexicalTarget.perform(
                    frameOrdinal, activation, name, lexicalDepth);
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
            return ProtosBytecodeRootNode.AssignCapturedFrameLocal.perform(
                    frameOrdinal, activation, destination, name, value);
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
                @Bind("$bytecodeNode") BytecodeNode bytecodeNode) {
            return ProtosBytecodeRootNode.ResolveCapturedMaterializedWritableLexicalTarget.perform(
                    accessor, activation, name, lexicalDepth, bytecodeNode);
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
                @Bind("$bytecodeNode") BytecodeNode bytecodeNode) {
            return ProtosBytecodeRootNode.AssignCapturedMaterializedLocal.perform(
                    accessor, activation, destination, name, value, bytecodeNode);
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
                @Bind("$bytecodeNode") BytecodeNode bytecodeNode,
                @Bind("$frame") VirtualFrame frame) {
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
    public static final class ReadInlineCapturedMaterializedLocal {
        @Specialization
        public static Object perform(
                MaterializedLocalAccessor accessor,
                LocalRangeAccessor frameBackedLocals,
                ProtosFrameLexicalLayout frameBackedLayout,
                PreparedInlineLiteralCall child,
                String name,
                int lexicalDepth,
                @Bind("$bytecodeNode") BytecodeNode bytecodeNode,
                @Bind("$frame") VirtualFrame frame) {
            return ProtosInlineCallbackFrameBindings.readCapturedMaterialized(
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
                @Bind("$bytecodeNode") BytecodeNode bytecodeNode,
                @Bind("$frame") VirtualFrame frame) {
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
                @Bind("$bytecodeNode") BytecodeNode bytecodeNode,
                @Bind("$frame") VirtualFrame frame) {
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
                @Bind("$bytecodeNode") BytecodeNode bytecodeNode,
                @Bind("$frame") VirtualFrame frame) {
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
                @Bind("$bytecodeNode") BytecodeNode bytecodeNode,
                @Bind("$frame") VirtualFrame frame) {
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
                @Bind("$bytecodeNode") BytecodeNode bytecodeNode,
                @Bind("$frame") VirtualFrame frame) {
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
    public static final class ReadInlineMember {
        @Specialization(
                guards = {
                    "name.equals(cachedName)",
                    "cachedLookup != null",
                    "matchesSharedInheritedLookup(receiver, cachedName, cachedLookup)"
                },
                assumptions = "cachedLookup.stability()",
                limit = "3")
        public static Object guardedSharedInherited(
                LocalRangeAccessor frameBackedLocals,
                ProtosFrameLexicalLayout frameBackedLayout,
                PreparedInlineLiteralCall child,
                Object receiver,
                String name,
                @Bind("child.prelude()") ProtosPrelude prelude,
                @Cached("name") String cachedName,
                @Cached("createSharedInheritedLookupForPrelude(receiver, name, prelude)")
                        ProtosValueLookup.SharedInheritedLookup cachedLookup) {
            return ProtosValueLookup.materializeMemberRead(receiver, cachedLookup.selected());
        }

        @Specialization(
                guards = {
                    "receiver == cachedReceiver",
                    "name.equals(cachedName)",
                    "cachedLookup != null"
                },
                assumptions = "cachedLookup.stability()",
                limit = "3")
        public static Object guardedExactReceiver(
                LocalRangeAccessor frameBackedLocals,
                ProtosFrameLexicalLayout frameBackedLayout,
                PreparedInlineLiteralCall child,
                Object receiver,
                String name,
                @Bind("child.prelude()") ProtosPrelude prelude,
                @Cached("receiver") Object cachedReceiver,
                @Cached("name") String cachedName,
                @Cached("createGuardedLookupForPrelude(receiver, name, prelude)")
                        ProtosValueLookup.GuardedLookup cachedLookup) {
            return ProtosValueLookup.materializeMemberRead(receiver, cachedLookup.selected());
        }

        @Specialization(replaces = {"guardedSharedInherited", "guardedExactReceiver"})
        public static Object perform(
                LocalRangeAccessor frameBackedLocals,
                ProtosFrameLexicalLayout frameBackedLayout,
                PreparedInlineLiteralCall child,
                Object receiver,
                String name,
                @Bind("$bytecodeNode") BytecodeNode bytecodeNode,
                @Bind("$frame") VirtualFrame frame) {
            Object value =
                    ProtosBytecodeRootNode.ReadMember.readMemberOrNull(
                            receiver, name, child.prelude());
            if (value != null) {
                return value;
            }
            return ProtosBytecodeRootNode.ReadMember.perform(
                    ProtosInlineCallbackFrameBindings.durableActivation(
                            child, frameBackedLocals, frameBackedLayout, bytecodeNode, frame),
                    receiver,
                    name);
        }

        static ProtosValueLookup.SharedInheritedLookup createSharedInheritedLookupForPrelude(
                Object receiver, String name, ProtosPrelude prelude) {
            return ProtosBytecodeRootNode.ReadMember.createSharedInheritedLookupForPrelude(
                    receiver, name, prelude);
        }

        static boolean matchesSharedInheritedLookup(
                Object receiver,
                String name,
                ProtosValueLookup.SharedInheritedLookup cachedLookup) {
            return ProtosBytecodeRootNode.ReadMember.matchesSharedInheritedLookup(
                    receiver, name, cachedLookup);
        }

        static ProtosValueLookup.GuardedLookup createGuardedLookupForPrelude(
                Object receiver, String name, ProtosPrelude prelude) {
            return ProtosBytecodeRootNode.ReadMember.createGuardedLookupForPrelude(
                    receiver, name, prelude);
        }
    }

    @Operation
    public static final class ResolveWritableLexicalTarget {
        @Specialization
        public static ResolvedLexicalWriteTarget perform(ProtosActivation activation, String name) {
            return ProtosBytecodeRootNode.ResolveWritableLexicalTarget.perform(activation, name);
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
            return ProtosBytecodeRootNode.AssignResolvedLexicalTarget.perform(
                    activation, destination, name, value);
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
                @Bind("$bytecodeNode") BytecodeNode bytecodeNode,
                @Bind("$frame") VirtualFrame frame) {
            return ProtosBytecodeRootNode.ResolveCurrentFrameLocalWriteTarget.perform(
                    accessor, activation, name, bytecodeNode, frame);
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
                @Bind("$bytecodeNode") BytecodeNode bytecodeNode,
                @Bind("$frame") VirtualFrame frame) {
            return ProtosBytecodeRootNode.AssignCurrentFrameLocal.perform(
                    accessor, activation, destination, name, value, bytecodeNode, frame);
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
                @Bind("$bytecodeNode") BytecodeNode bytecodeNode,
                @Bind("$frame") VirtualFrame frame) {
            Object[] arguments = frame.getArguments();
            if (ProtosFrameArguments.isUnmaterializedCompactCall(arguments)
                    && !accessor.isCleared(bytecodeNode, frame)) {
                return ResolvedLexicalWriteTarget.STATIC_CURRENT_FRAME_LOCAL;
            }
            return ProtosBytecodeRootNode.ResolveCurrentFrameLocalWriteTarget.perform(
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
                @Bind("$bytecodeNode") BytecodeNode bytecodeNode,
                @Bind("$frame") VirtualFrame frame) {
            Object[] arguments = frame.getArguments();
            if (destination == ResolvedLexicalWriteTarget.STATIC_CURRENT_FRAME_LOCAL
                    && ProtosFrameArguments.isUnmaterializedCompactCall(arguments)
                    && !accessor.isCleared(bytecodeNode, frame)) {
                accessor.setObject(bytecodeNode, frame, value);
                return value;
            }
            return ProtosBytecodeRootNode.AssignCurrentFrameLocal.perform(
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
                @Bind("$bytecodeNode") BytecodeNode bytecodeNode,
                @Bind("$frame") VirtualFrame frame) {
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
                @Bind("$bytecodeNode") BytecodeNode bytecodeNode,
                @Bind("$frame") VirtualFrame frame) {
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
            return ProtosBytecodeRootNode.RequireObjectMutationTarget.perform(activation, target);
        }
    }

    @Operation
    public static final class CreateCurrentLocalSlot {
        @Specialization
        public static Object perform(ProtosActivation activation, String name, Object value) {
            return ProtosBytecodeRootNode.CreateCurrentLocalSlot.perform(activation, name, value);
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
            return ProtosBytecodeRootNode.createIndexedCurrentLocalSlot(
                    frameBackedLayout, activation, ordinal, name, value);
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
            return ProtosBytecodeRootNode.CreateLocalSlot.perform(activation, target, name, value);
        }
    }

    @Operation
    public static final class MultipleCreateLocalSlots {
        @Specialization
        public static Object perform(ProtosActivation activation, String[] names, Object source) {
            return ProtosBytecodeRootNode.MultipleCreateLocalSlots.perform(activation, names, source);
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
            return ProtosBytecodeRootNode.AssignLocalSlot.perform(activation, target, name, value);
        }
    }

    @Operation
    public static final class ReadMember {
        @Specialization(
                guards = {
                    "name.equals(cachedName)",
                    "cachedLookup != null",
                    "matchesSharedInheritedLookup(receiver, cachedName, cachedLookup)"
                },
                assumptions = "cachedLookup.stability()",
                limit = "3")
        public static Object guardedSharedInherited(
                ProtosActivation activation,
                Object receiver,
                String name,
                @Cached("name") String cachedName,
                @Cached("createSharedInheritedLookup(receiver, name, activation)")
                        ProtosValueLookup.SharedInheritedLookup cachedLookup) {
            return ProtosBytecodeRootNode.ReadMember.guardedSharedInherited(
                    activation,
                    receiver,
                    name,
                    cachedName,
                    cachedLookup);
        }

        @Specialization(
                guards = {
                    "receiver == cachedReceiver",
                    "name.equals(cachedName)",
                    "cachedLookup != null"
                },
                assumptions = "cachedLookup.stability()",
                limit = "3")
        public static Object guardedExactReceiver(
                ProtosActivation activation,
                Object receiver,
                String name,
                @Cached("receiver") Object cachedReceiver,
                @Cached("name") String cachedName,
                @Cached("createGuardedLookup(receiver, name, activation)")
                        ProtosValueLookup.GuardedLookup cachedLookup) {
            return ProtosBytecodeRootNode.ReadMember.guardedExactReceiver(
                    activation,
                    receiver,
                    name,
                    cachedReceiver,
                    cachedName,
                    cachedLookup);
        }

        @Specialization(
                replaces = {
                    "guardedSharedInherited",
                    "guardedExactReceiver"
                })
        public static Object perform(
                ProtosActivation activation,
                Object receiver,
                String name) {
            return ProtosBytecodeRootNode.ReadMember.perform(
                    activation,
                    receiver,
                    name);
        }

        static ProtosValueLookup.SharedInheritedLookup createSharedInheritedLookup(
                Object receiver,
                String name,
                ProtosActivation activation) {
            return ProtosBytecodeRootNode.ReadMember.createSharedInheritedLookup(
                    receiver,
                    name,
                    activation);
        }

        static boolean matchesSharedInheritedLookup(
                Object receiver,
                String name,
                ProtosValueLookup.SharedInheritedLookup cachedLookup) {
            return ProtosBytecodeRootNode.ReadMember.matchesSharedInheritedLookup(
                    receiver,
                    name,
                    cachedLookup);
        }

        static ProtosValueLookup.GuardedLookup createGuardedLookup(
                Object receiver,
                String name,
                ProtosActivation activation) {
            return ProtosBytecodeRootNode.ReadMember.createGuardedLookup(
                    receiver,
                    name,
                    activation);
        }
    }

    @Operation
    public static final class Identity {
        @Specialization
        public static Object perform(Object left, Object right) {
            return ProtosBytecodeRootNode.Identity.perform(left, right);
        }
    }

    @Operation
    public static final class NotIdentity {
        @Specialization
        public static Object perform(Object left, Object right) {
            return ProtosBytecodeRootNode.NotIdentity.perform(left, right);
        }
    }

    @Operation
    public static final class ComplementEqualityResult {
        @Specialization
        public static Object perform(ProtosActivation activation, Object equalityResult) {
            return ProtosBytecodeRootNode.ComplementEqualityResult.perform(activation, equalityResult);
        }
    }

    @Operation
    public static final class LoadIntrinsic {
        @Specialization
        public static Object perform(ProtosActivation activation, CanonicalIntrinsic.Kind kind) {
            return ProtosBytecodeRootNode.LoadIntrinsic.perform(activation, kind);
        }
    }

    @Operation
    public static final class RaiseNonLocalReturn {
        @Specialization
        public static Object perform(ProtosActivation activation, Object value) {
            return ProtosBytecodeRootNode.RaiseNonLocalReturn.perform(activation, value);
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
            return ProtosBytecodeRootNode.MaterializeClosure.perform(
                    activation, definition, executionPlanCell);
        }
    }

    @Operation
    public static final class NewConstructedObject {
        @Specialization
        public static ProtosObjectValue perform(Object parent) {
            return ProtosBytecodeRootNode.NewConstructedObject.perform(parent);
        }
    }

    @Operation
    public static final class NewObjectConstructionActivation {
        @Specialization
        public static ProtosActivation perform(ProtosActivation enclosing, ProtosObjectValue object) {
            return ProtosBytecodeRootNode.NewObjectConstructionActivation.perform(enclosing, object);
        }
    }

    @Operation
    public static final class ComposeLocalSlots {
        @Specialization
        public static ProtosObjectValue perform(
                ProtosActivation activation,
                Object sourceValue,
                java.util.List<String> reservedNames) {
            return ProtosBytecodeRootNode.ComposeLocalSlots.perform(
                    activation, sourceValue, reservedNames);
        }
    }

    // ---- Map construction ----------------------------------------------------------

    @Operation
    public static final class PrepareMapConstructionFactoryCall {
        @Specialization
        public static PreparedClosureCall perform(ProtosActivation activation, Object factory) {
            return ProtosBytecodeRootNode.PrepareMapConstructionFactoryCall.perform(activation, factory);
        }
    }

    @Operation
    public static final class FinishMapConstructionFactoryCall {
        @Specialization
        public static ProtosMapValue perform(ProtosActivation activation, Object result) {
            return ProtosBytecodeRootNode.FinishMapConstructionFactoryCall.perform(activation, result);
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
            return ProtosBytecodeRootNode.PrepareMapInitialDefinition.perform(
                    activation, map, key, value);
        }
    }

    @Operation
    public static final class EnterMapInitialDefinitionComparison {
        @Specialization
        public static void perform(PreparedMapInitialDefinition prepared) {
            ProtosBytecodeRootNode.EnterMapInitialDefinitionComparison.perform(prepared);
        }
    }

    @Operation
    public static final class LeaveMapInitialDefinitionComparison {
        @Specialization
        public static void perform(PreparedMapInitialDefinition prepared) {
            ProtosBytecodeRootNode.LeaveMapInitialDefinitionComparison.perform(prepared);
        }
    }

    @Operation
    public static final class PrepareMapInitialDefinitionHashCall {
        @Specialization
        public static PreparedClosureCall perform(PreparedMapInitialDefinition prepared) {
            return ProtosBytecodeRootNode.PrepareMapInitialDefinitionHashCall.perform(prepared);
        }
    }

    @Operation
    public static final class AcceptMapInitialDefinitionHashResult {
        @Specialization
        public static void perform(PreparedMapInitialDefinition prepared, Object result) {
            ProtosBytecodeRootNode.AcceptMapInitialDefinitionHashResult.perform(prepared, result);
        }
    }

    @Operation
    public static final class MapInitialDefinitionNeedsEquality {
        @Specialization
        public static boolean perform(PreparedMapInitialDefinition prepared) {
            return ProtosBytecodeRootNode.MapInitialDefinitionNeedsEquality.perform(prepared);
        }
    }

    @Operation
    public static final class PrepareMapInitialDefinitionEqualityCall {
        @Specialization
        public static PreparedClosureCall perform(PreparedMapInitialDefinition prepared) {
            return ProtosBytecodeRootNode.PrepareMapInitialDefinitionEqualityCall.perform(prepared);
        }
    }

    @Operation
    public static final class AcceptMapInitialDefinitionEqualityResult {
        @Specialization
        public static void perform(PreparedMapInitialDefinition prepared, Object result) {
            ProtosBytecodeRootNode.AcceptMapInitialDefinitionEqualityResult.perform(prepared, result);
        }
    }

    @Operation
    public static final class FinishMapInitialDefinition {
        @Specialization
        public static void perform(PreparedMapInitialDefinition prepared) {
            ProtosBytecodeRootNode.FinishMapInitialDefinition.perform(prepared);
        }
    }

    // ---- Supplied argument vectors -------------------------------------------------

    @Operation
    public static final class CreateSuppliedArgumentVector {
        @Specialization
        public static PreparedArgumentVector perform() {
            return ProtosBytecodeRootNode.CreateSuppliedArgumentVector.perform();
        }
    }

    @Operation
    public static final class AppendSuppliedArgument {
        @Specialization
        public static void perform(PreparedArgumentVector vector, Object value) {
            ProtosBytecodeRootNode.AppendSuppliedArgument.perform(vector, value);
        }
    }

    @Operation
    public static final class AppendSpreadSuppliedArgument {
        @Specialization
        public static void perform(
                PreparedArgumentVector vector,
                Object value,
                ProtosActivation caller) {
            ProtosBytecodeRootNode.AppendSpreadSuppliedArgument.perform(vector, value, caller);
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
            return ProtosBytecodeRootNode.PrepareClosureCall.guardedDirect(
                    receiver, caller, enteredContext, cachedReceiver, cachedContext, cachedGuarded);
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
            return ProtosBytecodeRootNode.PrepareClosureCall.fastDirect(
                    receiver, caller, closure, closureDefinition, enteredContext,
                    cachedClosureDefinition, cachedContext, cachedTarget);
        }

        @Specialization(replaces = {"guardedDirect", "fastDirect"})
        public static PreparedClosureCall perform(Object receiver, ProtosActivation caller) {
            return ProtosBytecodeRootNode.PrepareClosureCall.perform(receiver, caller);
        }

        static ProtosLanguageContext currentEnteredContext(Node node) {
            return ProtosLanguageContext.current(node);
        }

        static GuardedDirectClosureCallTarget createGuardedDirectClosureCall(
                Object receiver, ProtosActivation caller, ProtosLanguageContext enteredContext) {
            return ProtosBytecodeRootNode.createGuardedDirectClosureCall(
                    receiver, caller, enteredContext);
        }

        static ProtosClosureValue directClosureCallSelectionOrNull(
                Object receiver, ProtosActivation caller) {
            return ProtosBytecodeRootNode.directClosureCallSelectionOrNull(receiver, caller);
        }

        static CanonicalClosure directClosureCallDefinitionOrNull(ProtosClosureValue closure) {
            return ProtosBytecodeRootNode.directClosureCallDefinitionOrNull(closure);
        }

        static RootCallTarget fastOrdinarySendTarget(
                ProtosClosureValue closure, ProtosLanguageContext enteredContext) {
            return ProtosBytecodeRootNode.PrepareSendArguments.fastOrdinarySendTarget(
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
            return ProtosBytecodeRootNode.PrepareClosureCallArguments.guardedDirect(
                    receiver, caller, supplied, enteredContext, cachedReceiver, cachedContext,
                    cachedGuarded);
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
            return ProtosBytecodeRootNode.PrepareClosureCallArguments.fastDirect(
                    receiver, caller, supplied, closure, closureDefinition, enteredContext,
                    cachedClosureDefinition, cachedContext, cachedTarget);
        }

        @Specialization(replaces = {"guardedDirect", "fastDirect"})
        public static PreparedClosureCall perform(
                Object receiver,
                ProtosActivation caller,
                @Variadic Object[] supplied) {
            return ProtosBytecodeRootNode.PrepareClosureCallArguments.perform(
                    receiver, caller, supplied);
        }

        static ProtosLanguageContext currentEnteredContext(Node node) {
            return ProtosLanguageContext.current(node);
        }

        static GuardedDirectClosureCallTarget createGuardedDirectClosureCall(
                Object receiver, ProtosActivation caller, ProtosLanguageContext enteredContext) {
            return ProtosBytecodeRootNode.createGuardedDirectClosureCall(
                    receiver, caller, enteredContext);
        }

        static ProtosClosureValue directClosureCallSelectionOrNull(
                Object receiver, ProtosActivation caller) {
            return ProtosBytecodeRootNode.directClosureCallSelectionOrNull(receiver, caller);
        }

        static CanonicalClosure directClosureCallDefinitionOrNull(ProtosClosureValue closure) {
            return ProtosBytecodeRootNode.directClosureCallDefinitionOrNull(closure);
        }

        static RootCallTarget fastOrdinarySendTarget(
                ProtosClosureValue closure, ProtosLanguageContext enteredContext) {
            return ProtosBytecodeRootNode.PrepareSendArguments.fastOrdinarySendTarget(
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
            return ProtosBytecodeRootNode.PrepareDefaultClosureCallArguments.guardedDirect(
                    receiver, caller, supplied, enteredContext, cachedReceiver, cachedContext,
                    cachedGuarded);
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
            return ProtosBytecodeRootNode.PrepareDefaultClosureCallArguments.fastDirect(
                    receiver, caller, supplied, closure, closureDefinition, enteredContext,
                    cachedClosureDefinition, cachedContext, cachedTarget);
        }

        @Specialization(replaces = {"guardedDirect", "fastDirect"})
        public static PreparedClosureCall perform(
                Object receiver,
                ProtosActivation caller,
                @Variadic Object[] supplied) {
            return ProtosBytecodeRootNode.PrepareDefaultClosureCallArguments.perform(
                    receiver, caller, supplied);
        }

        static ProtosLanguageContext currentEnteredContext(Node node) {
            return ProtosLanguageContext.current(node);
        }

        static GuardedDirectClosureCallTarget createGuardedDirectClosureCall(
                Object receiver, ProtosActivation caller, ProtosLanguageContext enteredContext) {
            return ProtosBytecodeRootNode.createGuardedDirectClosureCall(
                    receiver, caller, enteredContext);
        }

        static ProtosClosureValue directClosureCallSelectionOrNull(
                Object receiver, ProtosActivation caller) {
            return ProtosBytecodeRootNode.directClosureCallSelectionOrNull(receiver, caller);
        }

        static CanonicalClosure directClosureCallDefinitionOrNull(ProtosClosureValue closure) {
            return ProtosBytecodeRootNode.directClosureCallDefinitionOrNull(closure);
        }

        static RootCallTarget fastOrdinarySendTarget(
                ProtosClosureValue closure, ProtosLanguageContext enteredContext) {
            return ProtosBytecodeRootNode.PrepareSendArguments.fastOrdinarySendTarget(
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
                @Bind("$bytecodeNode") BytecodeNode bytecodeNode,
                @Bind("$frame") VirtualFrame frame,
                @Bind("currentEnteredContext($node)")
                        ProtosLanguageContext enteredContext,
                @Bind("child.prelude()") ProtosPrelude prelude,
                @Cached("selector") String cachedSelector,
                @Cached("enteredContext") ProtosLanguageContext cachedContext,
                @Cached("prelude") ProtosPrelude cachedPrelude,
                @Cached("createGuardedIntegerSend(receiver, selector, prelude, enteredContext)")
                        GuardedIntegerSend cachedInteger) {
            PreparedClosureCall canonical =
                    ProtosBytecodeRootNode.PrepareSendArguments.canonicalIntegerResultOrNull(
                            cachedInteger, receiver, supplied);
            if (canonical != null) {
                return canonical;
            }
            return ProtosBytecodeRootNode.PrepareSendArguments.guardedIntegerSend(
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
                @Bind("$bytecodeNode") BytecodeNode bytecodeNode,
                @Bind("$frame") VirtualFrame frame,
                @Bind("currentEnteredContext($node)")
                        ProtosLanguageContext enteredContext,
                @Bind("child.prelude()") ProtosPrelude prelude,
                @Cached("receiver") Object cachedReceiver,
                @Cached("selector") String cachedSelector,
                @Cached("enteredContext") ProtosLanguageContext cachedContext,
                @Cached("createGuardedSendForPrelude(receiver, selector, prelude, enteredContext)")
                        GuardedSendTarget cachedSend) {
            return ProtosBytecodeRootNode.PrepareSendArguments.guardedOrdinarySend(
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
                @Bind("$bytecodeNode") BytecodeNode bytecodeNode,
                @Bind("$frame") VirtualFrame frame,
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
            return ProtosBytecodeRootNode.PrepareSendArguments.fastOrdinarySend(
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
                @Bind("$bytecodeNode") BytecodeNode bytecodeNode,
                @Bind("$frame") VirtualFrame frame,
                @Bind("currentEnteredContext($node)")
                        ProtosLanguageContext enteredContext,
                @Bind("child.provenanceCallerOrNull()") ProtosActivation lookupCaller,
                @Cached("receiver") Object cachedReceiver,
                @Cached("selector") String cachedSelector,
                @Cached("enteredContext") ProtosLanguageContext cachedContext,
                @Cached("createGuardedStructuredSend(receiver, selector, lookupCaller)")
                        GuardedStructuredSend cachedStructured) {
            return ProtosBytecodeRootNode.PrepareSendArguments.guardedStructuredSend(
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
                @Bind("$bytecodeNode") BytecodeNode bytecodeNode,
                @Bind("$frame") VirtualFrame frame) {
            return ProtosBytecodeRootNode.PrepareSendArguments.perform(
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
            return ProtosBytecodeRootNode.PrepareSendArguments.createGuardedIntegerSend(
                    receiver, selector, prelude, enteredContext);
        }

        static boolean isIntegerReceiver(Object receiver) {
            return ProtosBytecodeRootNode.PrepareSendArguments.isIntegerReceiver(receiver);
        }

        static GuardedSendTarget createGuardedSendForPrelude(
                Object receiver,
                String selector,
                ProtosPrelude prelude,
                ProtosLanguageContext enteredContext) {
            return ProtosBytecodeRootNode.PrepareSendArguments.createGuardedSendForPrelude(
                    receiver, selector, prelude, enteredContext);
        }

        static GuardedStructuredSend createGuardedStructuredSend(
                Object receiver, String selector, ProtosActivation caller) {
            return ProtosBytecodeRootNode.PrepareSendArguments.createGuardedStructuredSend(
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

        static ProtosClosureValue ordinarySendClosureOrNull(ProtosSlotLookupResult selected) {
            return selected == null
                    ? null
                    : ProtosBytecodeRootNode.PrepareSendArguments.ordinarySendClosureOrNull(
                            selected);
        }

        static CanonicalClosure ordinarySendClosureDefinitionOrNull(ProtosClosureValue closure) {
            return ProtosBytecodeRootNode.PrepareSendArguments.ordinarySendClosureDefinitionOrNull(
                    closure);
        }

        static ProtosLanguageContext currentEnteredContext(Node node) {
            return ProtosLanguageContext.current(node);
        }

        static RootCallTarget fastOrdinarySendTarget(
                ProtosClosureValue closure, ProtosLanguageContext enteredContext) {
            return ProtosBytecodeRootNode.PrepareSendArguments.fastOrdinarySendTarget(
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
                @Bind("$bytecodeNode") BytecodeNode bytecodeNode,
                @Bind("$frame") VirtualFrame frame,
                @Bind("currentEnteredContext($node)")
                        ProtosLanguageContext enteredContext,
                @Bind("child.prelude()") ProtosPrelude prelude,
                @Cached("receiver") Object cachedReceiver,
                @Cached("enteredContext") ProtosLanguageContext cachedContext,
                @Cached("createGuardedDirectClosureCall(receiver, prelude, enteredContext)")
                        GuardedDirectClosureCallTarget cachedGuarded) {
            return ProtosBytecodeRootNode.PrepareClosureCall.guardedDirect(
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
                @Bind("$bytecodeNode") BytecodeNode bytecodeNode,
                @Bind("$frame") VirtualFrame frame,
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
            return ProtosBytecodeRootNode.PrepareClosureCall.fastDirect(
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
                @Bind("$bytecodeNode") BytecodeNode bytecodeNode,
                @Bind("$frame") VirtualFrame frame) {
            return ProtosBytecodeRootNode.PrepareClosureCall.perform(
                    receiver,
                    ProtosInlineCallbackFrameBindings.durableActivation(
                            child, frameBackedLocals, frameBackedLayout, bytecodeNode, frame));
        }

        static ProtosLanguageContext currentEnteredContext(Node node) {
            return ProtosLanguageContext.current(node);
        }

        static GuardedDirectClosureCallTarget createGuardedDirectClosureCall(
                Object receiver, ProtosPrelude prelude, ProtosLanguageContext enteredContext) {
            return ProtosBytecodeRootNode.createGuardedDirectClosureCallForPrelude(
                    receiver, prelude, enteredContext);
        }

        static ProtosClosureValue directClosureCallSelectionOrNull(
                Object receiver, PreparedInlineLiteralCall child) {
            return ProtosBytecodeRootNode.directClosureCallSelectionForPreludeOrNull(
                    receiver, child.prelude());
        }

        static CanonicalClosure directClosureCallDefinitionOrNull(ProtosClosureValue closure) {
            return ProtosBytecodeRootNode.directClosureCallDefinitionOrNull(closure);
        }

        static RootCallTarget fastOrdinarySendTarget(
                ProtosClosureValue closure, ProtosLanguageContext enteredContext) {
            return ProtosBytecodeRootNode.PrepareSendArguments.fastOrdinarySendTarget(
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
                @Bind("$bytecodeNode") BytecodeNode bytecodeNode,
                @Bind("$frame") VirtualFrame frame,
                @Bind("currentEnteredContext($node)")
                        ProtosLanguageContext enteredContext,
                @Bind("child.prelude()") ProtosPrelude prelude,
                @Cached("receiver") Object cachedReceiver,
                @Cached("enteredContext") ProtosLanguageContext cachedContext,
                @Cached("createGuardedDirectClosureCall(receiver, prelude, enteredContext)")
                        GuardedDirectClosureCallTarget cachedGuarded) {
            return ProtosBytecodeRootNode.PrepareClosureCallArguments.guardedDirect(
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
                @Bind("$bytecodeNode") BytecodeNode bytecodeNode,
                @Bind("$frame") VirtualFrame frame,
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
            return ProtosBytecodeRootNode.PrepareClosureCallArguments.fastDirect(
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
                @Bind("$bytecodeNode") BytecodeNode bytecodeNode,
                @Bind("$frame") VirtualFrame frame) {
            return ProtosBytecodeRootNode.PrepareClosureCallArguments.perform(
                    receiver,
                    ProtosInlineCallbackFrameBindings.durableActivation(
                            child, frameBackedLocals, frameBackedLayout, bytecodeNode, frame),
                    supplied);
        }

        static ProtosLanguageContext currentEnteredContext(Node node) {
            return ProtosLanguageContext.current(node);
        }

        static GuardedDirectClosureCallTarget createGuardedDirectClosureCall(
                Object receiver, ProtosPrelude prelude, ProtosLanguageContext enteredContext) {
            return ProtosBytecodeRootNode.createGuardedDirectClosureCallForPrelude(
                    receiver, prelude, enteredContext);
        }

        static ProtosClosureValue directClosureCallSelectionOrNull(
                Object receiver, PreparedInlineLiteralCall child) {
            return ProtosBytecodeRootNode.directClosureCallSelectionForPreludeOrNull(
                    receiver, child.prelude());
        }

        static CanonicalClosure directClosureCallDefinitionOrNull(ProtosClosureValue closure) {
            return ProtosBytecodeRootNode.directClosureCallDefinitionOrNull(closure);
        }

        static RootCallTarget fastOrdinarySendTarget(
                ProtosClosureValue closure, ProtosLanguageContext enteredContext) {
            return ProtosBytecodeRootNode.PrepareSendArguments.fastOrdinarySendTarget(
                    closure, enteredContext);
        }
    }

    @Operation
    public static final class PrepareClosureCallVector {
        @Specialization
        public static PreparedClosureCall perform(
                Object receiver,
                ProtosActivation caller,
                PreparedArgumentVector supplied) {
            return ProtosBytecodeRootNode.PrepareClosureCallVector.perform(receiver, caller, supplied);
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
                ProtosActivation caller,
                @Variadic Object[] supplied,
                @Bind("currentEnteredContext($node)")
                        ProtosLanguageContext enteredContext,
                @Bind("callerPrelude(caller)") ProtosPrelude prelude,
                @Cached("selector") String cachedSelector,
                @Cached("enteredContext") ProtosLanguageContext cachedContext,
                @Cached("prelude") ProtosPrelude cachedPrelude,
                @Cached("createGuardedIntegerSend(receiver, selector, prelude, enteredContext)")
                        GuardedIntegerSend cachedInteger) {
            return ProtosBytecodeRootNode.PrepareSendArguments.guardedIntegerSend(
                    receiver, selector, caller, supplied, enteredContext, prelude,
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
            return ProtosBytecodeRootNode.PrepareSendArguments.guardedOrdinarySend(
                    receiver, selector, caller, supplied, enteredContext,
                    cachedReceiver, cachedSelector, cachedContext, cachedSend);
        }

        @Specialization(
                guards = {
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
                ProtosActivation caller,
                @Variadic Object[] supplied,
                @Bind("performOrdinarySendLookup(receiver, selector, caller)")
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
            return ProtosBytecodeRootNode.PrepareSendArguments.fastOrdinarySend(
                    receiver, selector, caller, supplied, selected, closure, methodHome,
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
                ProtosActivation caller,
                @Variadic Object[] supplied,
                @Bind("currentEnteredContext($node)")
                        ProtosLanguageContext enteredContext,
                @Cached("receiver") Object cachedReceiver,
                @Cached("selector") String cachedSelector,
                @Cached("enteredContext") ProtosLanguageContext cachedContext,
                @Cached("createGuardedStructuredSend(receiver, selector, caller)")
                        GuardedStructuredSend cachedStructured) {
            return ProtosBytecodeRootNode.PrepareSendArguments.guardedStructuredSend(
                    receiver, selector, caller, supplied, enteredContext,
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
                Object receiver,
                String selector,
                ProtosActivation caller,
                @Variadic Object[] supplied) {
            return ProtosBytecodeRootNode.PrepareSendArguments.perform(
                    receiver, selector, caller, supplied);
        }

        static GuardedIntegerSend createGuardedIntegerSend(
                Object receiver, String selector, ProtosPrelude prelude) {
            return ProtosBytecodeRootNode.PrepareSendArguments.createGuardedIntegerSend(
                    receiver, selector, prelude);
        }

        static GuardedIntegerSend createGuardedIntegerSend(
                Object receiver,
                String selector,
                ProtosPrelude prelude,
                ProtosLanguageContext enteredContext) {
            return ProtosBytecodeRootNode.PrepareSendArguments.createGuardedIntegerSend(
                    receiver, selector, prelude, enteredContext);
        }

        static boolean isIntegerReceiver(Object receiver) {
            return ProtosBytecodeRootNode.PrepareSendArguments.isIntegerReceiver(receiver);
        }

        static ProtosPrelude callerPrelude(ProtosActivation caller) {
            return ProtosBytecodeRootNode.PrepareSendArguments.callerPrelude(caller);
        }

        static GuardedSendTarget createGuardedSend(
                Object receiver,
                String selector,
                ProtosActivation caller,
                ProtosLanguageContext enteredContext) {
            return ProtosBytecodeRootNode.PrepareSendArguments.createGuardedSend(
                    receiver, selector, caller, enteredContext);
        }

        static GuardedStructuredSend createGuardedStructuredSend(
                Object receiver, String selector, ProtosActivation caller) {
            return ProtosBytecodeRootNode.PrepareSendArguments.createGuardedStructuredSend(
                    receiver, selector, caller);
        }

        static ProtosSlotLookupResult performOrdinarySendLookup(
                Object receiver, String selector, ProtosActivation caller) {
            return ProtosBytecodeRootNode.PrepareSendArguments.performOrdinarySendLookup(
                    receiver, selector, caller);
        }

        static ProtosClosureValue ordinarySendClosureOrNull(ProtosSlotLookupResult selected) {
            return ProtosBytecodeRootNode.PrepareSendArguments.ordinarySendClosureOrNull(selected);
        }

        static CanonicalClosure ordinarySendClosureDefinitionOrNull(ProtosClosureValue closure) {
            return ProtosBytecodeRootNode.PrepareSendArguments.ordinarySendClosureDefinitionOrNull(
                    closure);
        }

        static ProtosLanguageContext currentEnteredContext(Node node) {
            return ProtosBytecodeRootNode.PrepareSendArguments.currentEnteredContext(node);
        }

        static RootCallTarget fastOrdinarySendTarget(
                ProtosClosureValue closure, ProtosLanguageContext enteredContext) {
            return ProtosBytecodeRootNode.PrepareSendArguments.fastOrdinarySendTarget(
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
            return ProtosBytecodeRootNode.PrepareSendVector.perform(receiver, selector, caller, supplied);
        }
    }

    @Operation
    public static final class PrepareSuperSendArguments {
        @Specialization
        public static PreparedClosureCall perform(
                String selector,
                ProtosActivation caller,
                @Variadic Object[] supplied) {
            return ProtosBytecodeRootNode.PrepareSuperSendArguments.perform(selector, caller, supplied);
        }
    }

    @Operation
    public static final class PrepareSuperSendVector {
        @Specialization
        public static PreparedClosureCall perform(
                String selector,
                ProtosActivation caller,
                PreparedArgumentVector supplied) {
            return ProtosBytecodeRootNode.PrepareSuperSendVector.perform(selector, caller, supplied);
        }
    }

    // ---- Prepared invocation and C-prime composition -------------------------------

    @Operation
    public static final class RequiresStructuredDispatch {
        @Specialization
        public static boolean perform(PreparedClosureCall prepared) {
            return ProtosBytecodeRootNode.RequiresStructuredDispatch.perform(prepared);
        }
    }

    // ---- PLAT043 standard Boolean orchestration ------------------------------------
    //
    // The prepared standard Boolean capability (IF_TRUE, IF_FALSE,
    // IF_TRUE_IF_FALSE, AND, OR) is sequenced locally in this root instead of
    // entering the untagged structured root. The capability is read from the
    // already-selected prepared call, never from the selector spelling; the
    // finite state machine stays owned by ProtosBytecodeRootNode.PreparedBooleanCall.

    @Operation
    public static final class IsStructuredBooleanCall {
        @Specialization
        public static boolean perform(PreparedClosureCall prepared) {
            return ProtosBytecodeRootNode.IsStructuredBooleanCall.perform(prepared);
        }
    }

    @Operation
    public static final class PrepareStructuredBooleanCall {
        @Specialization
        public static PreparedBooleanCall perform(PreparedClosureCall prepared) {
            return ProtosBytecodeRootNode.PrepareStructuredBooleanCall.perform(prepared);
        }
    }

    @Operation
    public static final class StructuredBooleanHasCallback {
        @Specialization
        public static boolean perform(PreparedBooleanCall prepared) {
            return ProtosBytecodeRootNode.StructuredBooleanHasCallback.perform(prepared);
        }
    }

    @Operation
    public static final class PrepareStructuredBooleanCallbackCall {
        @Specialization
        public static PreparedClosureCall perform(PreparedBooleanCall prepared) {
            return ProtosBytecodeRootNode.PrepareStructuredBooleanCallbackCall.perform(prepared);
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
            return ProtosBytecodeRootNode.StructuredBooleanImmediateResult.perform(prepared);
        }
    }

    @Operation
    public static final class FinishStructuredBooleanCallback {
        @Specialization
        public static Object perform(PreparedBooleanCall prepared, Object result) {
            return ProtosBytecodeRootNode.FinishStructuredBooleanCallback.perform(prepared, result);
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

    // ---- PLAT044 B′ inline literal whileTrue (PERF026-C1) ---------------------------
    //
    // A selected standard whileTrue whose receiver and body are exactly the
    // send site's staged literals is sequenced locally in this root; every
    // other while keeps the structured-dispatch root. The loop state machine
    // and Boolean condition authority stay owned by
    // ProtosBytecodeRootNode.PreparedWhileCall.

    @Operation
    public static final class AdmitsInlineLiteralWhile {
        @Specialization
        public static boolean perform(
                PreparedClosureCall prepared,
                Object condition,
                Object body) {
            return prepared.admitsInlineLiteralWhile(condition, body);
        }
    }

    @Operation
    public static final class PrepareStructuredWhileCall {
        @Specialization
        public static PreparedWhileCall perform(PreparedClosureCall prepared) {
            return ProtosBytecodeRootNode.PrepareStructuredWhileCall.perform(prepared);
        }
    }

    @Operation
    public static final class PrepareStructuredWhileConditionCall {
        @Specialization
        public static PreparedClosureCall perform(PreparedWhileCall prepared) {
            return ProtosBytecodeRootNode.PrepareStructuredWhileConditionCall.perform(prepared);
        }
    }

    @Operation
    public static final class PrepareStructuredWhileBodyCall {
        @Specialization
        public static PreparedClosureCall perform(PreparedWhileCall prepared) {
            return ProtosBytecodeRootNode.PrepareStructuredWhileBodyCall.perform(prepared);
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
            return ProtosBytecodeRootNode.StructuredWhileCondition.perform(prepared, result);
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
    // ProtosBytecodeRootNode.

    @Operation
    public static final class AdmitsInlineLiteralIndexedEach {
        @Specialization
        public static boolean perform(PreparedClosureCall prepared, Object callback) {
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
        public static PreparedIndexedEachCall perform(PreparedClosureCall prepared) {
            return prepared.isStructuredArrayEach()
                    ? ProtosBytecodeRootNode.PrepareStructuredArrayEachCall.perform(prepared)
                    : ProtosBytecodeRootNode.PrepareStructuredBytesEachCall.perform(prepared);
        }
    }

    @Operation
    public static final class AdmitsInlineLiteralTwoParameterEach {
        @Specialization
        public static boolean perform(PreparedClosureCall prepared, Object callback) {
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
        public static PreparedLocalEachCall perform(PreparedClosureCall prepared) {
            if (prepared.isStructuredEnvironmentEach()) {
                return ProtosBytecodeRootNode.PrepareStructuredEnvironmentEachCall.perform(prepared);
            }
            return prepared.isStructuredMapEach()
                    ? ProtosBytecodeRootNode.PrepareStructuredMapEachCall.perform(prepared)
                    : ProtosBytecodeRootNode.PrepareStructuredIdentityMapEachCall.perform(prepared);
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
        @Specialization
        public static void perform(PreparedClosureCall prepared) {
            ProtosBytecodeRootNode.CompleteClosureCall.perform(prepared);
        }
    }

    /**
     * PLAT042 B′ structured-dispatch boundary: one helper CallTarget per
     * structured invocation. The Task C-prime entry plan is cached per entered
     * Context, so its target is stable for a Context; a call site seen by
     * several Contexts falls back to an indirect call. Control-transfer,
     * module-initialization and runtime-failure mapping is the same as for an
     * ordinary prepared call.
     */
    @Operation
    public static final class EnterNestedStructuredDispatch {
        @Specialization(guards = "structuredDispatchTarget() == cachedTarget")
        public static Object direct(
                PreparedClosureCall prepared,
                @Cached("structuredDispatchTarget()") RootCallTarget cachedTarget,
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
                PreparedClosureCall prepared,
                @Cached IndirectCallNode node) {
            return ProtosBytecodeRootNode.EnterNestedStructuredDispatch.perform(prepared, node);
        }

        static RootCallTarget structuredDispatchTarget() {
            return ProtosTaskCPrimeEntryExecution.planForEnteredContext().target();
        }
    }

    @Operation
    public static final class EnterClosureCall {
        @Specialization(guards = "prepared.isImmediate()")
        public static Object immediate(PreparedClosureCall prepared) {
            return ProtosBytecodeRootNode.EnterClosureCall.immediate(prepared);
        }

        @Specialization(guards = "prepared.isNative()")
        public static Object nativeCall(PreparedClosureCall prepared) {
            return ProtosBytecodeRootNode.EnterClosureCall.nativeCall(prepared);
        }

        @Specialization(
                guards = {
                    "!prepared.isImmediate()",
                    "!prepared.isNative()",
                    "prepared.bodyTarget() == cachedTarget"
                },
                limit = "3")
        public static Object direct(
                PreparedClosureCall prepared,
                @Cached("prepared.bodyTarget()")
                        RootCallTarget cachedTarget,
                @Cached("create(cachedTarget)")
                        DirectCallNode node) {
            return ProtosBytecodeRootNode.EnterClosureCall.direct(prepared, cachedTarget, node);
        }

        @Specialization(
                replaces = "direct",
                guards = {"!prepared.isImmediate()", "!prepared.isNative()"})
        public static Object indirect(
                PreparedClosureCall prepared,
                @Cached IndirectCallNode node) {
            return ProtosBytecodeRootNode.EnterClosureCall.indirect(prepared, node);
        }
    }

    @Operation
    public static final class IsContinuation {
        @Specialization
        public static boolean perform(Object value) {
            return ProtosBytecodeRootNode.IsContinuation.perform(value);
        }
    }

    @Operation
    public static final class ResumeContinuation {
        @Specialization
        public static Object nativeSuspension(
                PreparedClosureCall prepared,
                ProtosNativeSuspension suspension,
                Object resumeValue) {
            return ProtosBytecodeRootNode.ResumeContinuation.nativeSuspension(
                    prepared, suspension, resumeValue);
        }

        @Specialization
        public static Object ioOperationSuspension(
                PreparedClosureCall prepared,
                ProtosIoOperationSuspension suspension,
                Object resumeValue) {
            return ProtosBytecodeRootNode.ResumeContinuation.ioOperationSuspension(
                    prepared, suspension, resumeValue);
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
            return ProtosBytecodeRootNode.ResumeContinuation.direct(
                    prepared, result, resumeValue, cachedRoot, node);
        }

        @Specialization(replaces = "direct")
        public static Object indirect(
                PreparedClosureCall prepared,
                ContinuationResult result,
                Object resumeValue,
                @Cached IndirectCallNode node) {
            return ProtosBytecodeRootNode.ResumeContinuation.indirect(
                    prepared, result, resumeValue, node);
        }
    }

    @Operation
    public static final class FinishClosureCall {
        @Specialization
        public static Object perform(PreparedClosureCall prepared, Object result) {
            return ProtosBytecodeRootNode.FinishClosureCall.perform(prepared, result);
        }
    }
}
