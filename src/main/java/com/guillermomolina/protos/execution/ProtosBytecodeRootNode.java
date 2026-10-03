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

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.bytecode.BytecodeNode;
import com.oracle.truffle.api.bytecode.BytecodeRootNode;
import com.oracle.truffle.api.bytecode.ConstantOperand;
import com.oracle.truffle.api.bytecode.LocalAccessor;
import com.oracle.truffle.api.bytecode.LocalRangeAccessor;
import com.oracle.truffle.api.bytecode.MaterializedLocalAccessor;
import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosLexicalEnvironment;
import com.guillermomolina.protos.runtime.ProtosLexicalFallback;
import com.guillermomolina.protos.runtime.ProtosArrayValue;
import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosBytesValue;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosCoreErrors;
import com.guillermomolina.protos.runtime.ProtosDynamicControlState;
import com.guillermomolina.protos.runtime.ProtosEnvironmentValue;
import com.guillermomolina.protos.runtime.ProtosIdentity;
import com.guillermomolina.protos.runtime.ProtosIdentityMapValue;
import com.guillermomolina.protos.runtime.ProtosMapValue;
import com.guillermomolina.protos.runtime.ProtosIoOperation;
import com.guillermomolina.protos.runtime.ProtosNonLocalReturnException;
import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosReturnHome;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.guillermomolina.protos.runtime.ProtosTask;
import com.guillermomolina.protos.runtime.ProtosNativeClosureBody;
import com.guillermomolina.protos.runtime.ProtosSuspensionCapableNativeClosureBody;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosSlotLookupResult;
import com.guillermomolina.protos.runtime.ProtosValueLookup;
import com.guillermomolina.protos.semantic.ast.CanonicalClosure;
import com.guillermomolina.protos.semantic.ast.CanonicalIntrinsic;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.bytecode.ContinuationResult;
import com.oracle.truffle.api.bytecode.ContinuationRootNode;
import com.oracle.truffle.api.bytecode.GenerateBytecode;
import com.oracle.truffle.api.bytecode.Operation;
import com.oracle.truffle.api.bytecode.Variadic;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ControlFlowException;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.RootNode;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * Internal Bytecode DSL root substrate selected by PLAT014.
 *
 * <p>This class is deliberately not a second Protos execution model. Under
 * PLAT042 Candidate B′ it is the untagged structured-dispatch/C-prime
 * interpreter: Task C-prime entry, structured prepared invocations (lowered by
 * {@link ProtosStructuredDispatchLowerer}) and the I/O C-prime roots. Ordinary
 * source roots are {@link ProtosSemanticBytecodeRootNode}s, whose source-surface
 * operations delegate to the single operation implementations owned here.
 * Source-only operations that no untagged root emits any longer may remain
 * declared here until a later cleanup.</p>
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
        boxingEliminationTypes = {int.class},
        tagTreeNodeLibrary = ProtosBytecodeTagTreeNodeExports.class)
abstract class ProtosBytecodeRootNode extends RootNode implements BytecodeRootNode {

    protected ProtosBytecodeRootNode(
            ProtosLanguage language,
            FrameDescriptor frameDescriptor) {
        super(language, frameDescriptor);
    }

    @Override
    public Object interceptControlFlowException(
            ControlFlowException transfer,
            VirtualFrame frame,
            BytecodeNode bytecodeNode,
            int bytecodeIndex)
            throws Throwable {
        throw ProtosBytecodeControlTransferException.bridge(transfer);
    }

    @Override
    public AbstractTruffleException interceptTruffleException(
            AbstractTruffleException exception,
            VirtualFrame frame,
            BytecodeNode bytecodeNode,
            int bytecodeIndex) {
        return interceptGuestException(exception, frame);
    }

    /**
     * Root exception interception shared by both generated Protos interpreters
     * (PLAT042 B′): the tagged semantic source root and this untagged
     * structured/C-prime root must select Error handlers identically.
     */
    static AbstractTruffleException interceptGuestException(
            AbstractTruffleException exception,
            VirtualFrame frame) {
        if (exception instanceof ProtosSignalException transfer) {
            /*
             * PERF025-H1: a semantic source root still in compact source-call
             * form materializes its exact activation here, on the Error path,
             * exactly as the former eager root prologue would have.
             */
            if (ProtosFrameArguments.hasActivation(frame)) {
                ProtosActivation activation = ProtosFrameArguments.activation(frame);
                /*
                 * Selection is semantic authority and must happen before the
                 * Bytecode EH table starts crossed TryFinally cleanup. Repeated
                 * root crossings are idempotent because the exact transfer
                 * remembers the one selected handler token.
                 */
                ProtosCoreErrors.selectHandlerIfNeeded(activation, transfer);
            }
        }
        return exception;
    }

    /**
     * Bytecode operation for unqualified lexical lookup.
     *
     * <p>The activation is loaded from frame argument 0 by the lowerer, preserving
     * the established Protos root calling convention.</p>
     */
    @Operation
    public static final class HasClosureArgument {
        @Specialization
        public static boolean perform(
                ProtosActivation activation,
                int positionalIndex) {
            List<?> arguments =
                    closureArguments(activation);
            return arguments.size() > positionalIndex;
        }
    }

    @Operation
    public static final class LoadClosureArgument {
        @Specialization
        public static Object perform(
                ProtosActivation activation,
                int positionalIndex) {
            List<?> arguments =
                    closureArguments(activation);
            if (positionalIndex >= arguments.size()) {
                throw closureArgumentCountError(activation);
            }
            return arguments.get(positionalIndex);
        }
    }

    @Operation
    public static final class BindClosureParameter {
        @Specialization
        public static void perform(
                ProtosActivation activation,
                String name,
                Object value) {
            createClosureParameterSlot(
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
            createClosureParameterSlot(
                    activation,
                    name,
                    closureRestArray(activation, positionalParametersBeforeRest));
        }
    }

    private static Object closureRestArray(
            ProtosActivation activation,
            int positionalParametersBeforeRest) {
        List<?> supplied =
                closureArguments(activation);
        int restStart =
                Math.min(
                        positionalParametersBeforeRest,
                        supplied.size());
        ProtosPrelude prelude =
                activation.prelude()
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "parameter binding requires an owning Core prelude"));
        return prelude.newFrozenArray(
                supplied.subList(
                        restStart,
                        supplied.size()));
    }

    @Operation
    public static final class CheckClosureArgumentUpperBound {
        @Specialization
        public static void perform(
                ProtosActivation activation,
                int maximumPositionalArguments) {
            if (closureArguments(activation).size()
                    > maximumPositionalArguments) {
                throw closureArgumentCountError(activation);
            }
        }
    }

    private static List<?> closureArguments(
            ProtosActivation activation) {
        return activation.suppliedArgumentsForRuntime();
    }

    private static void createClosureParameterSlot(
            ProtosActivation activation,
            String name,
            Object value) {
        try {
            activation.createCurrentLocalSlotForRuntime(name, value);
        } catch (IllegalStateException invalidCreation) {
            throw new ProtosSignalException(
                    ProtosCoreErrors.newError(activation));
        }
    }

    private static ProtosSignalException closureArgumentCountError(
            ProtosActivation activation) {
        return new ProtosSignalException(
                ProtosCoreErrors.newError(activation));
    }

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

    /**
     * PLAT036 Candidate D, Slice 3: installs a frame-backed {@link
     * com.guillermomolina.protos.runtime.ProtosLexicalBindingAuthority} on the
     * current genuine execution context, exactly once, as the first operation
     * of every {@code ROOT}/{@code CLOSURE} Bytecode root that {@code
     * CanonicalToBytecodeLowerer} found at least one eligible current-scope
     * binding for. The frame is materialized before authority construction and
     * retained so a context that later escapes its own activation keeps observing
     * the same authoritative
     * values through this same authority instance.
     *
     * <p>No-op when {@code activation.context()} is not a genuine execution
     * context (never the case for a real ROOT/CLOSURE lowering unit, but
     * defensively harmless otherwise).
     */
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
            MaterializedFrame materializedFrame = frame.materialize();
            activation.installFrameLexicalBindingAuthorityForRuntime(
                    new ProtosFrameLexicalBindingAuthority(
                            frameBackedLayout,
                            frameBackedLocals,
                            bytecodeNode,
                            materializedFrame));
        }
    }

    /**
     * PERF025 frame-materialization slice: establishes a statically proven
     * current binding of a root lowered without {@link
     * InstallFrameLexicalAuthority} (see {@code
     * CanonicalToBytecodeLowerer#requiresPersistentFrameAuthority}).
     *
     * <p>While the activation's execution context is unobserved and has no
     * authority, the root's frame local is its only possible store: the
     * binding is created there directly, with the exact OPEN-context creation
     * rule (a PRESENT, i.e. non-cleared, local is a duplicate creation; an
     * unobserved context is necessarily OPEN). Allocating the local never made
     * it PRESENT; only this write does.
     *
     * <p>In any other state (a Context already materialized, or an authority
     * already installed), the root first installs, exactly once and while its
     * frame is live, the same materialized-frame authority {@link
     * InstallFrameLexicalAuthority} would have installed at entry, adopting
     * the bindings already established in its locals, and then uses the
     * unchanged authority creation path. No VirtualFrame is ever retained.
     */
    static void createCurrentFrameBinding(
            LocalRangeAccessor frameBackedLocals,
            ProtosFrameLexicalLayout frameBackedLayout,
            ProtosActivation activation,
            int ordinal,
            String name,
            Object value,
            BytecodeNode bytecodeNode,
            VirtualFrame frame) {
        if (activation.hasUnobservedFrameNativeExecutionContextForRuntime()) {
            if (!frameBackedLocals.isCleared(bytecodeNode, frame, ordinal)) {
                throw new IllegalStateException("local slot already exists: " + name);
            }
            frameBackedLocals.setObject(bytecodeNode, frame, ordinal, value);
            return;
        }
        if (activation.hasGenuineExecutionContextForRuntime()) {
            installFrameLexicalAuthorityOnTransition(
                    frameBackedLocals,
                    frameBackedLayout,
                    activation,
                    bytecodeNode,
                    frame.materialize());
        }
        activation.createCurrentLocalSlotForRuntime(name, value);
    }

    /**
     * Installs the materialized-frame authority of a live root lowered
     * without {@link InstallFrameLexicalAuthority}, unless the authority
     * already installed is that root's own over this same frame. Callers pass
     * the frame of an activation that is still executing.
     */
    static void installFrameLexicalAuthorityOnTransition(
            LocalRangeAccessor frameBackedLocals,
            ProtosFrameLexicalLayout frameBackedLayout,
            ProtosActivation activation,
            BytecodeNode bytecodeNode,
            MaterializedFrame materializedFrame) {
        if (activation.currentLexicalBindingAuthorityForRuntime()
                        instanceof ProtosFrameLexicalBindingAuthority installed
                && installed.isInstalledFor(bytecodeNode, materializedFrame)) {
            return;
        }
        ProtosFrameLexicalBindingAuthority authority =
                new ProtosFrameLexicalBindingAuthority(
                        frameBackedLayout,
                        frameBackedLocals,
                        bytecodeNode,
                        materializedFrame);
        authority.adoptPresentFrameBackedBindings();
        activation.installFrameLexicalBindingAuthorityForRuntime(authority);
    }

    /** Frame-native counterpart of {@link BindClosureParameter}. */
    @Operation
    @ConstantOperand(
            type = LocalRangeAccessor.class,
            name = "frameBackedLocals")
    @ConstantOperand(
            type = ProtosFrameLexicalLayout.class,
            name = "frameBackedLayout")
    public static final class BindClosureFrameParameter {
        @Specialization
        public static void perform(
                LocalRangeAccessor frameBackedLocals,
                ProtosFrameLexicalLayout frameBackedLayout,
                ProtosActivation activation,
                int ordinal,
                String name,
                Object value,
                @Bind("$bytecodeNode") BytecodeNode bytecodeNode,
                @Bind("$frame") VirtualFrame frame) {
            try {
                createCurrentFrameBinding(
                        frameBackedLocals, frameBackedLayout, activation,
                        ordinal, name, value, bytecodeNode, frame);
            } catch (IllegalStateException invalidCreation) {
                throw new ProtosSignalException(
                        ProtosCoreErrors.newError(activation));
            }
        }
    }

    /** Frame-native counterpart of {@link BindClosureRest}. */
    @Operation
    @ConstantOperand(
            type = LocalRangeAccessor.class,
            name = "frameBackedLocals")
    @ConstantOperand(
            type = ProtosFrameLexicalLayout.class,
            name = "frameBackedLayout")
    public static final class BindClosureFrameRest {
        @Specialization
        public static void perform(
                LocalRangeAccessor frameBackedLocals,
                ProtosFrameLexicalLayout frameBackedLayout,
                ProtosActivation activation,
                int ordinal,
                String name,
                int positionalParametersBeforeRest,
                @Bind("$bytecodeNode") BytecodeNode bytecodeNode,
                @Bind("$frame") VirtualFrame frame) {
            Object rest = closureRestArray(activation, positionalParametersBeforeRest);
            try {
                createCurrentFrameBinding(
                        frameBackedLocals, frameBackedLayout, activation,
                        ordinal, name, rest, bytecodeNode, frame);
            } catch (IllegalStateException invalidCreation) {
                throw new ProtosSignalException(
                        ProtosCoreErrors.newError(activation));
            }
        }
    }

    /** Frame-native counterpart of {@link CreateCurrentLocalSlot}. */
    @Operation
    @ConstantOperand(
            type = LocalRangeAccessor.class,
            name = "frameBackedLocals")
    @ConstantOperand(
            type = ProtosFrameLexicalLayout.class,
            name = "frameBackedLayout")
    public static final class CreateCurrentFrameLocal {
        @Specialization
        public static Object perform(
                LocalRangeAccessor frameBackedLocals,
                ProtosFrameLexicalLayout frameBackedLayout,
                ProtosActivation activation,
                int ordinal,
                String name,
                Object value,
                @Bind("$bytecodeNode") BytecodeNode bytecodeNode,
                @Bind("$frame") VirtualFrame frame) {
            try {
                createCurrentFrameBinding(
                        frameBackedLocals, frameBackedLayout, activation,
                        ordinal, name, value, bytecodeNode, frame);
            } catch (IllegalStateException invalidMutation) {
                throw new ProtosSignalException(
                        ProtosCoreErrors.newError(activation));
            }
            return value;
        }
    }

    /** Frame-native counterpart of {@link MultipleCreateLocalSlots}. */
    @Operation
    @ConstantOperand(
            type = LocalRangeAccessor.class,
            name = "frameBackedLocals")
    @ConstantOperand(
            type = ProtosFrameLexicalLayout.class,
            name = "frameBackedLayout")
    public static final class MultipleCreateFrameLocals {
        @Specialization
        public static Object perform(
                LocalRangeAccessor frameBackedLocals,
                ProtosFrameLexicalLayout frameBackedLayout,
                ProtosActivation activation,
                int[] ordinals,
                String[] names,
                Object source,
                @Bind("$bytecodeNode") BytecodeNode bytecodeNode,
                @Bind("$frame") VirtualFrame frame) {
            List<Object> observed =
                    observeMultipleCreatePrefix(activation, names.length, source);
            for (int index = 0; index < names.length; index++) {
                try {
                    createCurrentFrameBinding(
                            frameBackedLocals, frameBackedLayout, activation,
                            ordinals[index], names[index], observed.get(index),
                            bytecodeNode, frame);
                } catch (IllegalStateException invalidMutation) {
                    // Deliberately no rollback, as in MultipleCreateLocalSlots.
                    throw new ProtosSignalException(
                            ProtosCoreErrors.newError(activation));
                }
            }
            return source;
        }
    }

    /**
     * PLAT036 Candidate D, Slice 3: the direct/fast read of a {@code
     * Resolved} current-scope binding. Deliberately uses the same {@link
     * LocalAccessor} mechanism {@link ProtosFrameLexicalBindingAuthority}
     * itself uses to read/write this local, rather than the raw generated
     * {@code LoadLocal} bytecode instruction: a local ever written only
     * through the dynamic {@link LocalAccessor} API does not participate in
     * the frame-slot-kind speculation the DSL's own literal {@code
     * StoreLocal}/{@code LoadLocal} instruction pair relies on, so mixing the
     * two access mechanisms for the same local is not safe. Static resolution
     * proves binding identity, not permanent presence: D179 C0 allows a
     * PRESENT execution-context local to become ABSENT, so a cleared local
     * must resume the exact lexical/receiver fallback path.
     */
    @Operation
    @ConstantOperand(type = LocalAccessor.class, name = "accessor")
    public static final class ReadFrameLocal {
        @Specialization
        public static Object perform(
                LocalAccessor accessor,
                ProtosActivation activation,
                String name,
                @Bind("$bytecodeNode") BytecodeNode bytecodeNode,
                @Bind("$frame") VirtualFrame frame) {
            if (activation.hasGenuineExecutionContextForRuntime()
                    && !accessor.isCleared(bytecodeNode, frame)) {
                return accessor.getObject(bytecodeNode, frame);
            }

            /*
             * A cleared genuine execution-context local is semantically ABSENT
             * under D179 C0 and therefore resumes exact lexical/receiver
             * fallback. Legacy/internal activations whose current lexical
             * context is an ordinary ProtosObjectValue use the same fallback.
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
     * I068 Slice 5 direct captured read.
     *
     * <p>The statically proven owner is addressed by lexical depth and stable
     * frame-layout ordinal. Before taking that path, every semantically nearer
     * execution context is checked for PRESENT membership. This preserves
     * D179 C0 late creation/removal retargeting. Any topology/layout mismatch
     * falls back to the exact existing String-keyed lookup path.
     */
    @Operation
    public static final class ReadCapturedFrameLocal {
        @Specialization
        public static Object perform(
                ProtosActivation activation,
                String name,
                int lexicalDepth,
                int frameOrdinal) {
            if (lexicalDepth <= 0) {
                return lookupCapturedFallback(activation, name);
            }

            if (activation.currentContextHasLocalSlotForRuntime(name)) {
                return lookupCapturedFallback(activation, name);
            }

            ProtosLexicalEnvironment owner =
                    capturedOwnerWithoutNearerBinding(
                            activation, name, lexicalDepth - 1);
            if (owner != null
                    && owner.lexicalBindingAuthorityForRuntime()
                            instanceof ProtosFrameLexicalBindingAuthority authority
                    && authority.hasFrameBackedBindingAt(name, frameOrdinal)) {
                return authority.readFrameBackedBindingAt(
                        name,
                        frameOrdinal);
            }

            return lookupCapturedFallback(activation, name);
        }
    }

    /**
     * PERF013 Slice B1 direct captured read using a compile-time-proven owner
     * {@link BytecodeLocal}, materialized via a generated {@link
     * MaterializedLocalAccessor} rather than the runtime frame-lexical-binding
     * authority's dynamic {@link LocalRangeAccessor}/owner-{@link
     * BytecodeNode} metadata. The accessor's identity (owner root/local) is
     * proven at lowering time by {@code CanonicalToBytecodeLowerer} from the
     * owner's own {@link com.oracle.truffle.api.bytecode.BytecodeLocal}, once
     * both owner and this Closure are lowered in the same shared {@code
     * BytecodeRootNodes} group (PERF013 Slice A/A2/A3); only the owner's
     * {@link MaterializedFrame} is looked up dynamically, through the same
     * retained {@link ProtosFrameLexicalBindingAuthority} instance {@link
     * ReadCapturedFrameLocal} already relies on. Every semantically nearer
     * execution-context presence check is preserved exactly as in {@link
     * ReadCapturedFrameLocal}, so D179 C0 late-creation/removal retargeting is
     * unaffected, and {@code isCleared} distinguishes {@code PRESENT(null)}
     * from {@code ABSENT} exactly as the runtime-authority path does.
     */
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
            if (lexicalDepth <= 0) {
                return lookupCapturedFallback(activation, name);
            }

            if (activation.currentContextHasLocalSlotForRuntime(name)) {
                return lookupCapturedFallback(activation, name);
            }

            ProtosLexicalEnvironment owner =
                    capturedOwnerWithoutNearerBinding(
                            activation, name, lexicalDepth - 1);
            if (owner == null
                    || !(owner.lexicalBindingAuthorityForRuntime()
                            instanceof ProtosFrameLexicalBindingAuthority authority)) {
                return lookupCapturedFallback(activation, name);
            }

            MaterializedFrame ownerFrame =
                    authority.retainedMaterializedFrameForCapturedAccess();
            if (ownerFrame == null
                    || accessor.isCleared(bytecodeNode, ownerFrame)) {
                return lookupCapturedFallback(activation, name);
            }

            return accessor.getObject(bytecodeNode, ownerFrame);
        }
    }

    /**
     * PERF025: selects the statically proven captured owner {@code ownerIndex}
     * scopes outward, or {@code null} when the captured chain is shorter or a
     * semantically nearer captured scope currently has the name PRESENT (D179
     * C0 late creation/removal retargeting). Membership is read through each
     * scope's single authority, so no deferred guest context is materialized.
     */
    private static ProtosLexicalEnvironment capturedOwnerWithoutNearerBinding(
            ProtosActivation activation,
            String name,
            int ownerIndex) {
        ProtosLexicalEnvironment scope =
                activation.capturedLexicalEnvironmentForRuntime();
        for (int index = 0; scope != null && index < ownerIndex; index++) {
            if (scope.hasLocalSlotForRuntime(name)) {
                return null;
            }
            scope = scope.outer();
        }
        return scope;
    }

    private static Object lookupCapturedFallback(
            ProtosActivation activation,
            String name) {
        return ProtosLexicalFallback.readByName(activation, name)
                .orElseThrow(
                        () ->
                                new ProtosSignalException(
                                        ProtosCoreErrors.newUnqualifiedLookupError(
                                                activation)));
    }

    /**
     * I068 Slice 5 ephemeral write destination. It exists only inside one
     * executing activation; it is never stored in a semantic Closure value.
     *
     * <p>For a proven captured frame binding, {@code frameAuthority} and
     * {@code frameOrdinal} identify the same single authoritative outer local.
     * For a PERF013 Slice B2 proven same-group captured binding, {@code
     * materializedOwnerFrame} identifies the exact owner {@link
     * MaterializedFrame} already selected before RHS evaluation, to be
     * revalidated and written through the assignment site's own {@link
     * MaterializedLocalAccessor} constant operand rather than re-resolved.
     * Otherwise {@code target} preserves the exact generic destination chosen
     * before RHS evaluation.
     */
    public static final class CapturedLexicalWriteTarget {
        private final ProtosObjectValue target;
        private final ProtosLexicalEnvironment frameOwner;
        private final ProtosFrameLexicalBindingAuthority frameAuthority;
        private final int frameOrdinal;
        private final MaterializedFrame materializedOwnerFrame;

        private CapturedLexicalWriteTarget(
                ProtosObjectValue target,
                ProtosLexicalEnvironment frameOwner,
                ProtosFrameLexicalBindingAuthority frameAuthority,
                int frameOrdinal,
                MaterializedFrame materializedOwnerFrame) {
            this.target = target;
            this.frameOwner = frameOwner;
            this.frameAuthority = frameAuthority;
            this.frameOrdinal = frameOrdinal;
            this.materializedOwnerFrame = materializedOwnerFrame;
        }

        static CapturedLexicalWriteTarget generic(
                ProtosObjectValue target) {
            return new CapturedLexicalWriteTarget(
                    java.util.Objects.requireNonNull(target, "target"),
                    null,
                    null,
                    -1,
                    null);
        }

        static CapturedLexicalWriteTarget frameBacked(
                ProtosLexicalEnvironment owner,
                ProtosFrameLexicalBindingAuthority authority,
                int frameOrdinal) {
            return new CapturedLexicalWriteTarget(
                    null,
                    java.util.Objects.requireNonNull(owner, "owner"),
                    java.util.Objects.requireNonNull(authority, "authority"),
                    frameOrdinal,
                    null);
        }

        static CapturedLexicalWriteTarget materialized(
                ProtosLexicalEnvironment owner,
                MaterializedFrame ownerFrame) {
            return new CapturedLexicalWriteTarget(
                    null,
                    java.util.Objects.requireNonNull(owner, "owner"),
                    null,
                    -1,
                    java.util.Objects.requireNonNull(ownerFrame, "ownerFrame"));
        }
    }

    @Operation
    public static final class ResolveCapturedWritableLexicalTarget {
        @Specialization
        public static CapturedLexicalWriteTarget perform(
                ProtosActivation activation,
                String name,
                int lexicalDepth,
                int frameOrdinal) {
            if (lexicalDepth > 0) {
                if (activation.currentContextHasLocalSlotForRuntime(name)) {
                    return CapturedLexicalWriteTarget.generic(
                            activation.context());
                }

                ProtosLexicalEnvironment owner =
                        capturedOwnerWithoutNearerBinding(
                                activation, name, lexicalDepth - 1);
                if (owner != null
                        && owner.lexicalBindingAuthorityForRuntime()
                                instanceof ProtosFrameLexicalBindingAuthority authority
                        && authority.hasFrameBackedBindingAt(
                                name,
                                frameOrdinal)) {
                    return CapturedLexicalWriteTarget.frameBacked(
                            owner,
                            authority,
                            frameOrdinal);
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
    public static final class AssignCapturedFrameLocal {
        @Specialization
        public static Object perform(
                ProtosActivation activation,
                CapturedLexicalWriteTarget destination,
                String name,
                Object value) {
            try {
                if (destination.frameAuthority != null) {
                    /*
                     * Match ProtosObjectValue.assignLocalSlot: CLOSED remains
                     * writable, FROZEN does not. Presence/layout are checked
                     * again at the actual mutation point.
                     */
                    if (destination.frameOwner.isFrozenForRuntime()) {
                        throw new IllegalStateException("object is frozen");
                    }
                    destination.frameAuthority.assignFrameBackedBindingAt(
                            name,
                            destination.frameOrdinal,
                            value);
                } else {
                    destination.target.assignLocalSlot(name, value);
                }
            } catch (IllegalStateException invalidMutation) {
                throw new ProtosSignalException(
                        ProtosCoreErrors.newError(activation));
            }
            return value;
        }
    }

    /**
     * PERF013 Slice B2 direct captured-write destination resolution using a
     * compile-time-proven owner {@link BytecodeLocal}, materialized via a
     * generated {@link MaterializedLocalAccessor}, mirroring {@link
     * ReadCapturedMaterializedLocal} on the write side. Every semantically
     * nearer execution-context presence check, and the static owner's own
     * {@code isCleared} presence check, are preserved exactly as in {@link
     * ResolveCapturedWritableLexicalTarget}, so D179 C0 late-creation/removal
     * retargeting is unaffected. The destination is fully selected here,
     * before RHS evaluation; {@link AssignCapturedMaterializedLocal} only
     * revalidates and writes the exact selection returned here, it never
     * re-resolves it.
     */
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
            if (lexicalDepth > 0) {
                if (activation.currentContextHasLocalSlotForRuntime(name)) {
                    return CapturedLexicalWriteTarget.generic(
                            activation.context());
                }

                ProtosLexicalEnvironment owner =
                        capturedOwnerWithoutNearerBinding(
                                activation, name, lexicalDepth - 1);
                if (owner != null
                        && owner.lexicalBindingAuthorityForRuntime()
                                instanceof ProtosFrameLexicalBindingAuthority authority) {
                    MaterializedFrame ownerFrame =
                            authority.retainedMaterializedFrameForCapturedAccess();
                    if (ownerFrame != null
                            && !accessor.isCleared(bytecodeNode, ownerFrame)) {
                        return CapturedLexicalWriteTarget.materialized(
                                owner,
                                ownerFrame);
                    }
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

    /**
     * PERF013 Slice B2 counterpart to {@link
     * ResolveCapturedMaterializedWritableLexicalTarget}: writes exactly the
     * destination already selected before RHS evaluation. For a materialized
     * destination, presence is revalidated (not re-resolved) through the same
     * constant {@link MaterializedLocalAccessor} used at resolution time; a
     * binding that has since been removed is a mutation error, never a
     * retarget. This never re-searches captured contexts.
     */
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
            try {
                if (destination.materializedOwnerFrame != null) {
                    /*
                     * Match ProtosObjectValue.assignLocalSlot: CLOSED remains
                     * writable, FROZEN does not. Presence is checked again at
                     * the actual mutation point, against the exact selected
                     * owner, never a different one.
                     */
                    if (destination.frameOwner.isFrozenForRuntime()) {
                        throw new IllegalStateException("object is frozen");
                    }
                    if (accessor.isCleared(bytecodeNode, destination.materializedOwnerFrame)) {
                        throw new IllegalStateException(
                                "captured frame-backed binding is absent or layout metadata mismatched: "
                                        + name);
                    }
                    accessor.setObject(bytecodeNode, destination.materializedOwnerFrame, value);
                } else {
                    destination.target.assignLocalSlot(name, value);
                }
            } catch (IllegalStateException invalidMutation) {
                throw new ProtosSignalException(
                        ProtosCoreErrors.newError(activation));
            }
            return value;
        }
    }

    public static final class ResolvedLexicalWriteTarget {
        /**
         * PERF028-A selection of the statically proven current frame local,
         * recognized only by identity in {@link AssignCurrentFrameLocal}. It
         * is stateless because the destination is the assignment site's own
         * constant {@link LocalAccessor} in the executing frame, so the
         * ordinary PRESENT path allocates no destination object.
         */
        static final ResolvedLexicalWriteTarget STATIC_CURRENT_FRAME_LOCAL =
                new ResolvedLexicalWriteTarget(true, null);

        private final boolean currentContext;
        private final ProtosObjectValue object;

        private ResolvedLexicalWriteTarget(
                boolean currentContext,
                ProtosObjectValue object) {
            this.currentContext = currentContext;
            this.object = object;
        }

        static ResolvedLexicalWriteTarget currentContext() {
            return new ResolvedLexicalWriteTarget(true, null);
        }

        static ResolvedLexicalWriteTarget object(
                ProtosObjectValue object) {
            return new ResolvedLexicalWriteTarget(
                    false,
                    java.util.Objects.requireNonNull(object, "object"));
        }
    }

    @Operation
    public static final class ResolveWritableLexicalTarget {
        @Specialization
        public static ResolvedLexicalWriteTarget perform(
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

            throw new ProtosSignalException(
                    ProtosCoreErrors.newSlotNotFound(activation));
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

    /**
     * PERF028-A destination selection for a bare assignment whose binding is
     * statically {@code Resolved} in the scope that owns the executing frame.
     * The assignment site's constant {@link LocalAccessor} identifies the
     * binding, so a PRESENT binding is selected without a String-keyed search
     * or a fresh destination object, using exactly the presence condition of
     * {@link ReadFrameLocal}. Static resolution does not prove presence (D179
     * C0): a cleared local, or a non-genuine current scope, runs the exact
     * generic {@link ResolveWritableLexicalTarget} selection now, before RHS
     * evaluation, and that selection is retained unchanged.
     */
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
            if (activation.hasGenuineExecutionContextForRuntime()
                    && !accessor.isCleared(bytecodeNode, frame)) {
                return ResolvedLexicalWriteTarget.STATIC_CURRENT_FRAME_LOCAL;
            }
            return ResolveWritableLexicalTarget.perform(activation, name);
        }
    }

    /**
     * PERF028-A counterpart to {@link ResolveCurrentFrameLocalWriteTarget}:
     * writes exactly the destination selected before RHS evaluation and never
     * re-resolves it. For the static current local, the mutation checks of
     * {@link ProtosObjectValue#assignLocalSlot} are applied to the same
     * current context and the same accessor: FROZEN is rejected (CLOSED stays
     * writable), and a binding cleared by the RHS is a mutation error rather
     * than a retarget, while one removed and recreated in the same context is
     * the same physical local and receives the write. The FROZEN check does
     * not materialize the guest Context. Execution contexts never track
     * lookup dependencies, so the direct write needs no invalidation. Any
     * other retained destination is written through the unchanged {@link
     * AssignResolvedLexicalTarget} path.
     */
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
            if (destination != ResolvedLexicalWriteTarget.STATIC_CURRENT_FRAME_LOCAL) {
                return AssignResolvedLexicalTarget.perform(
                        activation, destination, name, value);
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

    @Operation
    public static final class RequireObjectMutationTarget {
        @Specialization
        public static ProtosObjectValue perform(
                ProtosActivation activation,
                Object target) {
            if (target instanceof ProtosObjectValue object) {
                return object;
            }
            throw new ProtosSignalException(ProtosCoreErrors.newError(activation));
        }
    }

    @Operation
    public static final class CreateCurrentLocalSlot {
        @Specialization
        public static Object perform(
                ProtosActivation activation,
                String name,
                Object value) {
            try {
                activation.createCurrentLocalSlotForRuntime(name, value);
            } catch (IllegalStateException invalidMutation) {
                throw new ProtosSignalException(
                        ProtosCoreErrors.newError(activation));
            }
            return value;
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
        public static Object perform(
                ProtosActivation activation,
                String[] names,
                Object source) {
            int required = names.length;
            List<Object> observed =
                    observeMultipleCreatePrefix(activation, required, source);

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

    private static List<Object> observeMultipleCreatePrefix(
            ProtosActivation activation,
            int required,
            Object source) {
        if (!(source instanceof ProtosArrayValue array)) {
            throw new ProtosSignalException(
                    ProtosCoreErrors.newError(activation));
        }

        if (array.indexedSize().compareTo(BigInteger.valueOf(required)) < 0) {
            throw new ProtosSignalException(
                    ProtosCoreErrors.newError(activation));
        }

        /*
         * D143 requires the complete fixed prefix to be shallow-observed
         * before the first target slot is created. Do not use at,
         * iteration, indexedSnapshot(), or any guest-visible protocol.
         */
        List<Object> observed = new ArrayList<>(required);
        for (int index = 0; index < required; index++) {
            observed.add(array.indexedAt(BigInteger.valueOf(index)));
        }
        return observed;
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

    @Operation
    public static final class ReadMember {
        @Specialization
        public static Object perform(
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
        public static Object perform(
                ProtosActivation activation,
                Object equalityResult) {
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
        public static Object perform(
                ProtosActivation activation,
                CanonicalIntrinsic.Kind kind) {
            return switch (kind) {
                case THIS -> activation.receiver();
                case CONTEXT -> activation.context();
            };
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
                    executionPlanCell.plan());
        }
    }

    /**
     * PLAT041 C′: allocates the Object under construction. The Object body is
     * then lowered inline in the enclosing root as a resumable region (see
     * {@code CanonicalToBytecodeLowerer#emitInlineObjectConstruction}); it is
     * not a physical root, nor a lexical execution context.
     */
    @Operation
    public static final class NewConstructedObject {
        @Specialization
        public static ProtosObjectValue perform(Object parent) {
            return new ProtosObjectValue(parent);
        }
    }

    /**
     * PLAT041 C′: the construction activation the inline Object body executes
     * with. {@link ProtosActivation#forObjectConstruction} remains the sole
     * authority for its receiver/context, capture chain, homes, and inherited
     * Task or dynamic-control identity.
     */
    @Operation
    public static final class NewObjectConstructionActivation {
        @Specialization
        public static ProtosActivation perform(
                ProtosActivation enclosing,
                ProtosObjectValue object) {
            return ProtosActivation.forObjectConstruction(object, enclosing);
        }
    }

    @Operation
    public static final class ComposeLocalSlots {
        @Specialization
        public static ProtosObjectValue perform(
                ProtosActivation activation,
                Object sourceValue,
                java.util.List<String> reservedNames) {
            if (!(sourceValue instanceof ProtosObjectValue source)) {
                throw new ProtosSignalException(ProtosCoreErrors.newError(activation));
            }

            ProtosObjectValue target = activation.context();
            try {
                target.composeLocalSlotsFrom(source, reservedNames);
            } catch (IllegalStateException failure) {
                throw new ProtosSignalException(ProtosCoreErrors.newError(activation));
            }
            return target;
        }
    }

    @Operation
    public static final class RaiseNonLocalReturn {
        @Specialization
        public static Object perform(
                ProtosActivation activation,
                Object value) {
            ProtosReturnHome target =
                    activation.returnHome().orElse(null);
            if (target == null || !target.isActive()) {
                throw new ProtosSignalException(
                        ProtosCoreErrors.newInvalidReturn(activation));
            }
            throw new ProtosNonLocalReturnException(target, value);
        }
    }

    /**
     * Optional structured-control capability carried by a {@link NativeCall}.
     *
     * <p>Ordinary source-backed and plain-native calls never allocate this holder
     * (PLAT040 Candidate F′: optional semantic capability must not force unrelated
     * ordinary calls to carry its full physical machinery). It exists only when a
     * call actually owns one of the mutually exclusive structured-control,
     * collection-callback, or import capabilities below.
     */
    private static final class StructuredCallCapabilities {
        final boolean ensure;
        final boolean errorHandler;
        final boolean whileLoop;
        final ProtosStandardBooleanProtocol.StructuredCallbackKind booleanKind;
        final boolean arrayEach;
        final boolean bytesEach;
        final boolean environmentEach;
        final boolean identityMapEach;
        final boolean mapEach;
        final ProtosStandardMapProtocol.StructuredReadLookupKind mapReadLookup;
        final boolean mapAtPut;
        final boolean mapRemove;
        final boolean directControlNative;
        final boolean objectCall;
        final ProtosModuleRuntime importRuntime;

        private StructuredCallCapabilities(
                boolean ensure,
                boolean errorHandler,
                boolean whileLoop,
                ProtosStandardBooleanProtocol.StructuredCallbackKind booleanKind,
                boolean arrayEach,
                boolean bytesEach,
                boolean environmentEach,
                boolean identityMapEach,
                boolean mapEach,
                ProtosStandardMapProtocol.StructuredReadLookupKind mapReadLookup,
                boolean mapAtPut,
                boolean mapRemove,
                boolean directControlNative,
                boolean objectCall,
                ProtosModuleRuntime importRuntime) {
            this.ensure = ensure;
            this.errorHandler = errorHandler;
            this.whileLoop = whileLoop;
            this.booleanKind = booleanKind;
            this.arrayEach = arrayEach;
            this.bytesEach = bytesEach;
            this.environmentEach = environmentEach;
            this.identityMapEach = identityMapEach;
            this.mapEach = mapEach;
            this.mapReadLookup = mapReadLookup;
            this.mapAtPut = mapAtPut;
            this.mapRemove = mapRemove;
            this.directControlNative = directControlNative;
            this.objectCall = objectCall;
            this.importRuntime = importRuntime;
            int controlCapabilities =
                    (ensure ? 1 : 0)
                            + (errorHandler ? 1 : 0)
                            + (whileLoop ? 1 : 0)
                            + (booleanKind != null ? 1 : 0)
                            + (arrayEach ? 1 : 0)
                            + (bytesEach ? 1 : 0)
                            + (environmentEach ? 1 : 0)
                            + (identityMapEach ? 1 : 0)
                            + (mapEach ? 1 : 0)
                            + (mapReadLookup != null ? 1 : 0)
                            + (mapAtPut ? 1 : 0)
                            + (mapRemove ? 1 : 0)
                            + (directControlNative ? 1 : 0)
                            + (objectCall ? 1 : 0)
                            + (importRuntime != null ? 1 : 0);
            if (controlCapabilities > 1) {
                throw new IllegalArgumentException(
                        "prepared Closure call cannot own multiple structured-control capabilities");
            }
        }

        /** Returns {@code null} when every capability is at its default/unset value. */
        static StructuredCallCapabilities of(
                boolean ensure,
                boolean errorHandler,
                boolean whileLoop,
                ProtosStandardBooleanProtocol.StructuredCallbackKind booleanKind,
                boolean arrayEach,
                boolean bytesEach,
                boolean environmentEach,
                boolean identityMapEach,
                boolean mapEach,
                ProtosStandardMapProtocol.StructuredReadLookupKind mapReadLookup,
                boolean mapAtPut,
                boolean mapRemove,
                boolean directControlNative,
                boolean objectCall,
                ProtosModuleRuntime importRuntime) {
            boolean any =
                    ensure
                            || errorHandler
                            || whileLoop
                            || booleanKind != null
                            || arrayEach
                            || bytesEach
                            || environmentEach
                            || identityMapEach
                            || mapEach
                            || mapReadLookup != null
                            || mapAtPut
                            || mapRemove
                            || directControlNative
                            || objectCall
                            || importRuntime != null;
            if (!any) {
                return null;
            }
            return new StructuredCallCapabilities(
                    ensure,
                    errorHandler,
                    whileLoop,
                    booleanKind,
                    arrayEach,
                    bytesEach,
                    environmentEach,
                    identityMapEach,
                    mapEach,
                    mapReadLookup,
                    mapAtPut,
                    mapRemove,
                    directControlNative,
                    objectCall,
                    importRuntime);
        }
    }

    /**
     * A prepared, about-to-execute Closure invocation.
     *
     * <p>I072 Phase D (PLAT040 Candidate F′): this is a small dispatch surface
     * shared by every call shape, not a universal physical carrier. Exactly one
     * of three leaf shapes implements it per call — {@link OrdinarySourceCall},
     * {@link NativeCall}, or {@link ModuleInitializationCall} — and each leaf
     * physically carries only the state its own shape actually uses. An
     * ordinary source-backed call never allocates native-body, structured-
     * control-capability, or module-initialization state; those remain
     * exclusive to the two special shapes that actually need them. Interface
     * default methods below only supply the "this capability is absent"
     * answer for shapes that never own it; they add no per-instance state.
     */
    interface PreparedClosureCall {
        RootCallTarget bodyTarget();

        ProtosActivation activation();

        Object[] targetArguments();

        ProtosTask taskForRuntime();

        default boolean isNative() { return false; }

        default boolean isImmediate() { return false; }

        default Object enterImmediate() {
            throw new IllegalStateException("prepared call is not an immediate module hit");
        }

        default Object enterNative() {
            throw new IllegalStateException("prepared Closure call is not native");
        }

        default boolean requiresStructuredDispatch() { return false; }

        default boolean isStructuredObjectCall() { return false; }
        default boolean isStructuredCaseOf() { return false; }
        default boolean isStructuredMapMatch() { return false; }
        default boolean isStructuredImportCall() { return false; }
        default boolean isStructuredEnsure() { return false; }
        default boolean isStructuredErrorHandler() { return false; }
        default boolean isStructuredWhile() { return false; }
        default boolean isStructuredBoolean() { return false; }
        default boolean isStructuredArrayEach() { return false; }
        default boolean isStructuredArrayMatch() { return false; }
        default boolean isStructuredBytesEach() { return false; }
        default boolean isStructuredEnvironmentEach() { return false; }
        default boolean isStructuredIdentityMapAtIfAbsent() { return false; }
        default boolean isStructuredIdentityMapEach() { return false; }
        default boolean isStructuredMapEach() { return false; }
        default boolean isStructuredMapReadLookup() { return false; }
        default boolean isStructuredMapAtPut() { return false; }
        default boolean isStructuredMapRemove() { return false; }

        /**
         * PLAT044 B′ (PERF026-C1) whole-pair admission of a selected standard
         * whileTrue; see {@link NativeCall#admitsInlineLiteralWhile}.
         */
        default boolean admitsInlineLiteralWhile(Object condition, Object body) {
            return false;
        }

        /**
         * PLAT044 B′ (PERF026-D1) whole-call admission of a selected standard
         * Array.each or Bytes.each; see {@link
         * NativeCall#admitsInlineLiteralIndexedEach}.
         */
        default boolean admitsInlineLiteralIndexedEach(Object callback) {
            return false;
        }

        /**
         * PLAT044 B′ (PERF026-D2) whole-call admission of a selected standard
         * Map.each or IdentityMap.each; see {@link
         * NativeCall#admitsInlineLiteralAssociationEach}.
         */
        default boolean admitsInlineLiteralAssociationEach(Object callback) {
            return false;
        }

        /**
         * PLAT044 B′ (PERF026-D3) whole-call admission of a selected standard
         * Environment.each; see {@link
         * NativeCall#admitsInlineLiteralEnvironmentEach}.
         */
        default boolean admitsInlineLiteralEnvironmentEach(Object callback) {
            return false;
        }

        default PreparedMapMatchCall prepareStructuredMapMatch() {
            throw new IllegalStateException(
                    "prepared Closure call has no structured Map.match capability");
        }

        default PreparedCaseOfCall prepareStructuredCaseOf() {
            throw new IllegalStateException(
                    "prepared Closure call has no structured Object.caseOf capability");
        }

        default PreparedStandardObjectCall prepareStructuredObjectCall() {
            throw new IllegalStateException(
                    "prepared Closure call has no PLAT032 Object.call capability");
        }

        default PreparedStandardImportCall prepareStructuredImportCall() {
            throw new IllegalStateException(
                    "prepared Closure call has no PLAT032 import capability");
        }

        default PreparedEnsureCall prepareStructuredEnsure() {
            throw new IllegalStateException(
                    "prepared Closure call has no structured ensure capability");
        }

        default PreparedWhileCall prepareStructuredWhile() {
            throw new IllegalStateException(
                    "prepared Closure call has no structured while capability");
        }

        default PreparedErrorHandlerCall prepareStructuredErrorHandler() {
            throw new IllegalStateException(
                    "prepared Closure call has no structured Error.handle capability");
        }

        default PreparedBooleanCall prepareStructuredBoolean() {
            throw new IllegalStateException(
                    "prepared Closure call has no structured Boolean callback capability");
        }

        default PreparedArrayEachCall prepareStructuredArrayEach() {
            throw new IllegalStateException(
                    "prepared Closure call has no structured Array.each capability");
        }

        default PreparedArrayMatchCall prepareStructuredArrayMatch() {
            throw new IllegalStateException(
                    "prepared Closure call has no structured Array.match capability");
        }

        default PreparedBytesEachCall prepareStructuredBytesEach() {
            throw new IllegalStateException(
                    "prepared Closure call has no structured Bytes.each capability");
        }

        default PreparedEnvironmentEachCall prepareStructuredEnvironmentEach() {
            throw new IllegalStateException(
                    "prepared Closure call has no structured Environment.each capability");
        }

        default PreparedIdentityMapAtIfAbsentCall prepareStructuredIdentityMapAtIfAbsent() {
            throw new IllegalStateException(
                    "prepared Closure call has no structured IdentityMap.atIfAbsent capability");
        }

        default PreparedIdentityMapEachCall prepareStructuredIdentityMapEach() {
            throw new IllegalStateException(
                    "prepared Closure call has no structured IdentityMap.each capability");
        }

        default PreparedMapEachCall prepareStructuredMapEach() {
            throw new IllegalStateException(
                    "prepared Closure call has no structured Map.each capability");
        }

        default PreparedMapReadLookupCall prepareStructuredMapReadLookup() {
            throw new IllegalStateException(
                    "prepared Closure call has no structured Map read-lookup capability");
        }

        default PreparedMapAtPutCall prepareStructuredMapAtPut() {
            throw new IllegalStateException(
                    "prepared Closure call has no structured Map.atPut capability");
        }

        default PreparedMapRemoveCall prepareStructuredMapRemove() {
            throw new IllegalStateException(
                    "prepared Closure call has no structured Map.remove capability");
        }

        Object handleControlTransfer(ControlFlowException transfer);

        void failIfModuleInitialization();

        RuntimeException mapRuntimeFailure(RuntimeException failure);

        void complete();

        Object finish(Object result);

        static PreparedClosureCall ordinary(RootCallTarget bodyTarget, ProtosActivation activation) {
            return new OrdinarySourceCall(bodyTarget, activation);
        }

        static PreparedClosureCall ordinaryCompact(
                RootCallTarget bodyTarget, Object[] compactTargetArguments) {
            return new OrdinarySourceCall(bodyTarget, compactTargetArguments);
        }

        static PreparedClosureCall nativeCall(
                ProtosNativeClosureBody nativeBody,
                List<?> supplied,
                ProtosActivation activation,
                boolean structuredEnsure,
                boolean structuredErrorHandler,
                boolean structuredWhile,
                ProtosStandardBooleanProtocol.StructuredCallbackKind structuredBoolean,
                boolean structuredArrayEach,
                boolean structuredBytesEach,
                boolean structuredEnvironmentEach,
                boolean structuredIdentityMapEach,
                boolean structuredMapEach,
                ProtosStandardMapProtocol.StructuredReadLookupKind structuredMapReadLookup,
                boolean structuredMapAtPut,
                boolean structuredMapRemove,
                boolean directControlNative,
                boolean structuredObjectCall,
                ProtosModuleRuntime structuredImportRuntime) {
            return new NativeCall(
                    java.util.Objects.requireNonNull(nativeBody, "nativeBody"),
                    supplied,
                    activation,
                    StructuredCallCapabilities.of(
                            structuredEnsure,
                            structuredErrorHandler,
                            structuredWhile,
                            structuredBoolean,
                            structuredArrayEach,
                            structuredBytesEach,
                            structuredEnvironmentEach,
                            structuredIdentityMapEach,
                            structuredMapEach,
                            structuredMapReadLookup,
                            structuredMapAtPut,
                            structuredMapRemove,
                            directControlNative,
                            structuredObjectCall,
                            structuredImportRuntime));
        }

        static PreparedClosureCall moduleInitialization(
                ProtosModuleRuntime.PreparedModuleInitialization moduleInitialization) {
            return new ModuleInitializationCall(moduleInitialization);
        }
    }

    /**
     * Shared return-home ownership/completion behavior for the two prepared-call
     * shapes that actually own a {@link ProtosReturnHome}: {@link
     * OrdinarySourceCall} and {@link NativeCall}. {@link ModuleInitializationCall}
     * has no return-home concept and implements {@link PreparedClosureCall}
     * directly instead of extending this class.
     */
    private abstract static class ReturnHomeOwningCall implements PreparedClosureCall {
        private final ProtosReturnHome returnHome;
        private final boolean ownsReturnHome;

        ReturnHomeOwningCall(ProtosReturnHome returnHome, boolean ownsReturnHome) {
            this.returnHome = returnHome;
            this.ownsReturnHome = ownsReturnHome;
        }

        @Override
        public Object handleControlTransfer(ControlFlowException transfer) {
            if (transfer instanceof ProtosNonLocalReturnException nonLocalReturn
                    && ownsReturnHome
                    && returnHome.isActive()
                    && nonLocalReturn.target() == returnHome) {
                return nonLocalReturn.value();
            }
            throw transfer;
        }

        @Override
        public void failIfModuleInitialization() {
            // Neither shape extending this class owns module-initialization state.
        }

        @Override
        public RuntimeException mapRuntimeFailure(RuntimeException failure) {
            return failure;
        }

        @Override
        public void complete() {
            if (ownsReturnHome && returnHome.isActive()) {
                returnHome.complete();
            }
        }

        @Override
        public Object finish(Object result) {
            complete();
            return result;
        }
    }

    /**
     * The lean, pay-only-when-used representation of an ordinary source-backed
     * Closure call. It carries exactly a selected target, the compact frame
     * arguments (or, for the pre-target rich-activation shape, the activation
     * itself), and the return-home/ownership state that non-local return
     * completion actually requires. It never carries native-body, structured-
     * control-capability, or module-initialization state.
     */
    static final class OrdinarySourceCall extends ReturnHomeOwningCall {
        private final RootCallTarget bodyTarget;
        private final ProtosActivation activation;
        private final Object[] targetArguments;

        OrdinarySourceCall(RootCallTarget bodyTarget, ProtosActivation activation) {
            super(
                    java.util.Objects.requireNonNull(activation, "activation")
                            .returnHome()
                            .orElseThrow(
                                    () ->
                                            new IllegalStateException(
                                                    "Closure invocation requires a return home")),
                    activation.ownsReturnHome());
            this.bodyTarget = java.util.Objects.requireNonNull(bodyTarget, "bodyTarget");
            this.activation = activation;
            this.targetArguments = new Object[] {activation};
        }

        OrdinarySourceCall(RootCallTarget bodyTarget, Object[] compactTargetArguments) {
            super(
                    ProtosFrameArguments.compactReturnHome(
                            java.util.Objects.requireNonNull(
                                    compactTargetArguments, "compactTargetArguments")),
                    ProtosFrameArguments.compactOwnsReturnHome(compactTargetArguments));
            this.bodyTarget = java.util.Objects.requireNonNull(bodyTarget, "bodyTarget");
            this.activation = null;
            this.targetArguments = compactTargetArguments;
        }

        @Override
        public RootCallTarget bodyTarget() { return bodyTarget; }

        @Override
        public ProtosActivation activation() {
            if (activation == null) {
                throw new IllegalStateException(
                        "compact source call has no pre-target rich activation");
            }
            return activation;
        }

        @Override
        public Object[] targetArguments() { return targetArguments; }

        @Override
        public ProtosTask taskForRuntime() {
            if (activation != null) {
                return activation.task().orElse(null);
            }
            return ProtosFrameArguments.compactTask(targetArguments);
        }
    }

    /**
     * The special-call representation for a native Closure body, optionally
     * carrying exactly one mutually exclusive {@link StructuredCallCapabilities}.
     * Ordinary source calls never instantiate this class.
     */
    static final class NativeCall extends ReturnHomeOwningCall {
        private final ProtosNativeClosureBody nativeBody;
        private final List<?> supplied;
        private final ProtosActivation activation;
        private final StructuredCallCapabilities structured;

        NativeCall(
                ProtosNativeClosureBody nativeBody,
                List<?> supplied,
                ProtosActivation activation,
                StructuredCallCapabilities structured) {
            super(
                    java.util.Objects.requireNonNull(activation, "activation")
                            .returnHome()
                            .orElseThrow(
                                    () ->
                                            new IllegalStateException(
                                                    "Closure invocation requires a return home")),
                    activation.ownsReturnHome());
            this.nativeBody = java.util.Objects.requireNonNull(nativeBody, "nativeBody");
            this.supplied = List.copyOf(supplied);
            this.activation = activation;
            this.structured = structured;
        }

        @Override
        public RootCallTarget bodyTarget() { return null; }

        @Override
        public ProtosActivation activation() { return activation; }

        @Override
        public Object[] targetArguments() {
            throw new IllegalStateException("prepared call has no source target arguments");
        }

        @Override
        public ProtosTask taskForRuntime() { return activation.task().orElse(null); }

        @Override
        public boolean isNative() { return true; }

        @Override
        public boolean isStructuredObjectCall() { return structured != null && structured.objectCall; }

        @Override
        public boolean isStructuredCaseOf() {
            return ProtosStandardObjectProtocol.isStandardCaseOfImplementation(nativeBody);
        }

        @Override
        public boolean isStructuredMapMatch() {
            return ProtosStandardMapProtocol.isStandardMatchImplementation(nativeBody);
        }

        @Override
        public boolean isStructuredImportCall() {
            return structured != null && structured.importRuntime != null;
        }

        @Override
        public boolean isStructuredEnsure() { return structured != null && structured.ensure; }

        @Override
        public boolean isStructuredErrorHandler() {
            return structured != null && structured.errorHandler;
        }

        @Override
        public boolean isStructuredWhile() { return structured != null && structured.whileLoop; }

        /**
         * PLAT044 B′ (PERF026-C1): true only when ordinary selection prepared
         * the canonical standard whileTrue capability and its receiver and
         * sole supplied value are exactly the send site's staged {@code
         * condition} and {@code body} literal values, and neither literal
         * would fail the per-child B′ proof for a reason already known now:
         * an owned return home (no captured one) or a required Context-local
         * plan projection. A pure read of the already prepared call and the
         * literal values: no activation is prepared and nothing is validated,
         * so condition/body preparation timing is unchanged, and any other
         * shape keeps the structured-dispatch while. A per-child proof that
         * still fails later (for example a foreign-Context plan) keeps that
         * child's exact physical invocation.
         */
        @Override
        public boolean admitsInlineLiteralWhile(Object condition, Object body) {
            return isStructuredWhile()
                    && activation.receiver() == condition
                    && supplied.size() == 1
                    && supplied.get(0) == body
                    && isInlineLiteralCallbackValue(condition)
                    && isInlineLiteralCallbackValue(body);
        }

        /**
         * PLAT044 B′ (PERF026-D1): true only when ordinary selection prepared
         * the canonical standard Array.each or Bytes.each capability, its sole
         * supplied value is exactly the send site's staged one-parameter
         * {@code callback} literal value, and that literal would not fail the
         * per-element B′ proof for a reason already known now (as in {@link
         * #admitsInlineLiteralWhile}). A pure read: the receiver and callback
         * are not validated here and no snapshot or activation is created, so
         * {@code PreparedArrayEachCall}/{@code PreparedBytesEachCall} keep sole
         * authority over validation order, the snapshot and every per-element
         * activation.
         */
        @Override
        public boolean admitsInlineLiteralIndexedEach(Object callback) {
            return (isStructuredArrayEach() || isStructuredBytesEach())
                    && supplied.size() == 1
                    && supplied.get(0) == callback
                    && isInlineLiteralCallbackValue(callback);
        }

        /**
         * PLAT044 B′ (PERF026-D2): the {@link #admitsInlineLiteralIndexedEach}
         * proof for the canonical standard Map.each or IdentityMap.each
         * capability and the send site's staged two-parameter {@code
         * callback} literal value. Equally a pure read: {@code
         * PreparedMapEachCall}/{@code PreparedIdentityMapEachCall} keep sole
         * authority over receiver and callability validation, the association
         * snapshot and every per-entry activation, and callback arity is not
         * examined here.
         */
        @Override
        public boolean admitsInlineLiteralAssociationEach(Object callback) {
            return (isStructuredMapEach() || isStructuredIdentityMapEach())
                    && supplied.size() == 1
                    && supplied.get(0) == callback
                    && isInlineLiteralCallbackValue(callback);
        }

        /**
         * PLAT044 B′ (PERF026-D3): the {@link #admitsInlineLiteralIndexedEach}
         * proof for the canonical standard Environment.each capability and the
         * send site's staged two-parameter {@code callback} literal value.
         * Equally a pure read: no callability check, portable conversion,
         * ordering or activation happens here, so {@code
         * PreparedEnvironmentEachCall} keeps sole authority over receiver and
         * callability validation, the complete portable canonically ordered
         * snapshot established before callback #1 and every per-entry
         * activation, and callback arity is not examined here.
         *
         * <p>The structured capability may have been classified by the
         * standard each implementation alone (the generic preparation path),
         * which a copy of that Closure at another home shares; admission
         * therefore re-proves the canonical home from this call's own
         * receiver and method home, so a copied each keeps its ordinary path.
         */
        @Override
        public boolean admitsInlineLiteralEnvironmentEach(Object callback) {
            return isStructuredEnvironmentEach()
                    && activation.methodHome()
                            .map(home ->
                                    ProtosStandardEnvironmentProtocol.isCanonicalStandardEachHome(
                                            activation.receiver(), home))
                            .orElse(false)
                    && supplied.size() == 1
                    && supplied.get(0) == callback
                    && isInlineLiteralCallbackValue(callback);
        }

        private static boolean isInlineLiteralCallbackValue(Object value) {
            return value instanceof ProtosClosureValue closure
                    && closure.returnHome().isPresent()
                    && !closure.requiresContextLocalExecutionProjectionForRuntime();
        }

        @Override
        public boolean isStructuredBoolean() {
            return structured != null && structured.booleanKind != null;
        }

        @Override
        public boolean isStructuredArrayEach() { return structured != null && structured.arrayEach; }

        @Override
        public boolean isStructuredArrayMatch() {
            return ProtosStandardArrayProtocol.isStandardMatchImplementation(nativeBody);
        }

        @Override
        public boolean isStructuredBytesEach() { return structured != null && structured.bytesEach; }

        @Override
        public boolean isStructuredEnvironmentEach() {
            return structured != null && structured.environmentEach;
        }

        @Override
        public boolean isStructuredIdentityMapAtIfAbsent() {
            return ProtosStandardIdentityMapProtocol.isStandardAtIfAbsentImplementation(nativeBody);
        }

        @Override
        public boolean isStructuredIdentityMapEach() {
            return structured != null && structured.identityMapEach;
        }

        @Override
        public boolean isStructuredMapEach() { return structured != null && structured.mapEach; }

        @Override
        public boolean isStructuredMapReadLookup() {
            return structured != null && structured.mapReadLookup != null;
        }

        @Override
        public boolean isStructuredMapAtPut() { return structured != null && structured.mapAtPut; }

        @Override
        public boolean isStructuredMapRemove() { return structured != null && structured.mapRemove; }

        @Override
        public boolean requiresStructuredDispatch() {
            if (structured != null
                    && (structured.objectCall
                            || structured.importRuntime != null
                            || structured.ensure
                            || structured.errorHandler
                            || structured.whileLoop
                            || structured.booleanKind != null
                            || structured.arrayEach
                            || structured.bytesEach
                            || structured.environmentEach
                            || structured.identityMapEach
                            || structured.mapEach
                            || structured.mapReadLookup != null
                            || structured.mapAtPut
                            || structured.mapRemove)) {
                return true;
            }
            return isStructuredCaseOf()
                    || isStructuredMapMatch()
                    || isStructuredArrayMatch()
                    || isStructuredIdentityMapAtIfAbsent();
        }

        @Override
        public PreparedMapMatchCall prepareStructuredMapMatch() {
            if (!isStructuredMapMatch()) {
                throw new IllegalStateException(
                        "prepared Closure call has no structured Map.match capability");
            }
            return new PreparedMapMatchCall(
                    activation.receiver(),
                    supplied,
                    activation);
        }

        @Override
        public PreparedCaseOfCall prepareStructuredCaseOf() {
            if (!isStructuredCaseOf()) {
                throw new IllegalStateException(
                        "prepared Closure call has no structured Object.caseOf capability");
            }
            return new PreparedCaseOfCall(
                    activation.receiver(),
                    supplied,
                    activation);
        }

        @Override
        public PreparedStandardObjectCall prepareStructuredObjectCall() {
            if (structured == null || !structured.objectCall) {
                throw new IllegalStateException(
                        "prepared Closure call has no PLAT032 Object.call capability");
            }

            Object receiver = activation.receiver();
            if (receiver instanceof ProtosClosureValue targetClosure) {
                return PreparedStandardObjectCall.closure(
                        prepareDirectClosureCall(
                                targetClosure,
                                supplied,
                                activation));
            }
            if (receiver instanceof ProtosObjectValue prototype) {
                ProtosObjectValue instance = new ProtosObjectValue(prototype);
                return PreparedStandardObjectCall.construction(
                        prepareSend(
                                instance,
                                "init",
                                activation,
                                supplied),
                        instance);
            }
            throw ProtosCoreErrors.signal(
                    activation,
                    ProtosCoreErrors.newError(activation));
        }

        @Override
        public PreparedStandardImportCall prepareStructuredImportCall() {
            if (structured == null || structured.importRuntime == null) {
                throw new IllegalStateException(
                        "prepared Closure call has no PLAT032 import capability");
            }
            return new PreparedStandardImportCall(
                    PreparedClosureCall.moduleInitialization(
                            structured.importRuntime.prepareBytecodeImport(
                                    supplied,
                                    activation)));
        }

        @Override
        public PreparedEnsureCall prepareStructuredEnsure() {
            if (structured == null || !structured.ensure) {
                throw new IllegalStateException(
                        "prepared Closure call has no structured ensure capability");
            }
            Object receiver = activation.receiver();
            if (!(receiver instanceof ProtosClosureValue body)
                    || supplied.size() != 1
                    || !(supplied.get(0) instanceof ProtosClosureValue cleanup)) {
                throw ProtosCoreErrors.signal(
                        activation,
                        ProtosCoreErrors.newError(activation));
            }
            return new PreparedEnsureCall(
                    prepareDirectClosureCall(body, List.of(), activation),
                    prepareDirectClosureCall(cleanup, List.of(), activation));
        }

        @Override
        public PreparedWhileCall prepareStructuredWhile() {
            if (structured == null || !structured.whileLoop) {
                throw new IllegalStateException(
                        "prepared Closure call has no structured while capability");
            }
            Object receiver = activation.receiver();
            if (!(receiver instanceof ProtosClosureValue condition)
                    || supplied.size() != 1
                    || !(supplied.get(0) instanceof ProtosClosureValue body)) {
                throw ProtosCoreErrors.signal(
                        activation,
                        ProtosCoreErrors.newError(activation));
            }
            return new PreparedWhileCall(condition, body, activation);
        }

        @Override
        public PreparedErrorHandlerCall prepareStructuredErrorHandler() {
            if (structured == null || !structured.errorHandler) {
                throw new IllegalStateException(
                        "prepared Closure call has no structured Error.handle capability");
            }
            Object receiver = activation.receiver();
            if (!(receiver instanceof ProtosObjectValue matchPrototype)
                    || !ProtosCoreErrors.isError(activation, matchPrototype)
                    || supplied.size() != 2
                    || !(supplied.get(0) instanceof ProtosClosureValue body)
                    || !(supplied.get(1) instanceof ProtosClosureValue handler)) {
                throw ProtosCoreErrors.signal(
                        activation,
                        ProtosCoreErrors.newError(activation));
            }

            ProtosDynamicControlState state = activation.dynamicControlState();
            ProtosDynamicControlState.Frame frame =
                    state.enterHandlerFrame(activation, matchPrototype);
            try {
                return new PreparedErrorHandlerCall(
                        state,
                        frame,
                        handler,
                        activation,
                        prepareDirectClosureCall(body, List.of(), activation));
            } catch (RuntimeException | Error preparationFailure) {
                state.leaveFrame(frame);
                throw preparationFailure;
            }
        }

        @Override
        public PreparedBooleanCall prepareStructuredBoolean() {
            if (structured == null || structured.booleanKind == null) {
                throw new IllegalStateException(
                        "prepared Closure call has no structured Boolean callback capability");
            }
            return new PreparedBooleanCall(
                    structured.booleanKind,
                    activation.receiver(),
                    supplied,
                    activation);
        }

        @Override
        public PreparedArrayEachCall prepareStructuredArrayEach() {
            if (structured == null || !structured.arrayEach) {
                throw new IllegalStateException(
                        "prepared Closure call has no structured Array.each capability");
            }
            return new PreparedArrayEachCall(
                    activation.receiver(),
                    supplied,
                    activation);
        }

        @Override
        public PreparedArrayMatchCall prepareStructuredArrayMatch() {
            if (!isStructuredArrayMatch()) {
                throw new IllegalStateException(
                        "prepared Closure call has no structured Array.match capability");
            }
            return new PreparedArrayMatchCall(
                    activation.receiver(),
                    supplied,
                    activation);
        }

        @Override
        public PreparedBytesEachCall prepareStructuredBytesEach() {
            if (structured == null || !structured.bytesEach) {
                throw new IllegalStateException(
                        "prepared Closure call has no structured Bytes.each capability");
            }
            return new PreparedBytesEachCall(
                    activation.receiver(),
                    supplied,
                    activation);
        }

        @Override
        public PreparedEnvironmentEachCall prepareStructuredEnvironmentEach() {
            if (structured == null || !structured.environmentEach) {
                throw new IllegalStateException(
                        "prepared Closure call has no structured Environment.each capability");
            }
            return new PreparedEnvironmentEachCall(
                    activation.receiver(),
                    supplied,
                    activation);
        }

        @Override
        public PreparedIdentityMapAtIfAbsentCall prepareStructuredIdentityMapAtIfAbsent() {
            if (!isStructuredIdentityMapAtIfAbsent()) {
                throw new IllegalStateException(
                        "prepared Closure call has no structured IdentityMap.atIfAbsent capability");
            }
            return new PreparedIdentityMapAtIfAbsentCall(
                    activation.receiver(),
                    supplied,
                    activation);
        }

        @Override
        public PreparedIdentityMapEachCall prepareStructuredIdentityMapEach() {
            if (structured == null || !structured.identityMapEach) {
                throw new IllegalStateException(
                        "prepared Closure call has no structured IdentityMap.each capability");
            }
            return new PreparedIdentityMapEachCall(
                    activation.receiver(),
                    supplied,
                    activation);
        }

        @Override
        public PreparedMapEachCall prepareStructuredMapEach() {
            if (structured == null || !structured.mapEach) {
                throw new IllegalStateException(
                        "prepared Closure call has no structured Map.each capability");
            }
            return new PreparedMapEachCall(
                    activation.receiver(),
                    supplied,
                    activation);
        }

        @Override
        public PreparedMapReadLookupCall prepareStructuredMapReadLookup() {
            if (structured == null || structured.mapReadLookup == null) {
                throw new IllegalStateException(
                        "prepared Closure call has no structured Map read-lookup capability");
            }
            return new PreparedMapReadLookupCall(
                    structured.mapReadLookup,
                    activation.receiver(),
                    supplied,
                    activation);
        }

        @Override
        public PreparedMapAtPutCall prepareStructuredMapAtPut() {
            if (structured == null || !structured.mapAtPut) {
                throw new IllegalStateException(
                        "prepared Closure call has no structured Map.atPut capability");
            }
            return new PreparedMapAtPutCall(
                    activation.receiver(),
                    supplied,
                    activation);
        }

        @Override
        public PreparedMapRemoveCall prepareStructuredMapRemove() {
            if (structured == null || !structured.mapRemove) {
                throw new IllegalStateException(
                        "prepared Closure call has no structured Map.remove capability");
            }
            return new PreparedMapRemoveCall(
                    activation.receiver(),
                    supplied,
                    activation);
        }

        @Override
        public Object enterNative() {
            if (requiresStructuredDispatch()) {
                throw new IllegalStateException(
                        "structured control native must execute through Bytecode control operations");
            }
            if ((activation.task().isPresent()
                            || activation.deferredCPrimeOperationForRuntime().isPresent()
                            || activation.deferredCPrimeReleaseForRuntime().isPresent())
                    && nativeBody
                            instanceof ProtosSuspensionCapableNativeClosureBody
                                    suspensionCapable) {
                return suspensionCapable
                        .executeForBytecodeContinuation(
                                activation,
                                supplied);
            }
            return nativeBody.execute(
                    activation,
                    supplied);
        }
    }

    /**
     * The special-call representation for a standard-import module
     * initialization. It carries only the {@link
     * ProtosModuleRuntime.PreparedModuleInitialization} lifecycle handle;
     * ordinary source calls never instantiate this class and it has no
     * return-home concept of its own.
     */
    static final class ModuleInitializationCall implements PreparedClosureCall {
        private final ProtosModuleRuntime.PreparedModuleInitialization moduleInitialization;
        private final RootCallTarget bodyTarget;
        private final ProtosActivation activation;
        private final Object[] targetArguments;

        ModuleInitializationCall(
                ProtosModuleRuntime.PreparedModuleInitialization moduleInitialization) {
            this.moduleInitialization =
                    java.util.Objects.requireNonNull(
                            moduleInitialization,
                            "moduleInitialization");
            this.bodyTarget = moduleInitialization.bodyTarget();
            this.activation = moduleInitialization.activation();
            this.targetArguments =
                    bodyTarget == null
                            ? null
                            : new Object[] {this.activation};
        }

        @Override
        public RootCallTarget bodyTarget() { return bodyTarget; }

        @Override
        public ProtosActivation activation() { return activation; }

        @Override
        public Object[] targetArguments() {
            if (targetArguments == null) {
                throw new IllegalStateException(
                        "prepared call has no source target arguments");
            }
            return targetArguments;
        }

        @Override
        public ProtosTask taskForRuntime() { return activation.task().orElse(null); }

        @Override
        public boolean isImmediate() { return moduleInitialization.isImmediate(); }

        @Override
        public Object enterImmediate() {
            if (!isImmediate()) {
                throw new IllegalStateException("prepared call is not an immediate module hit");
            }
            return moduleInitialization.immediateResult();
        }

        @Override
        public Object handleControlTransfer(ControlFlowException transfer) {
            moduleInitialization.fail();
            throw transfer;
        }

        @Override
        public void failIfModuleInitialization() { moduleInitialization.fail(); }

        @Override
        public RuntimeException mapRuntimeFailure(RuntimeException failure) {
            return moduleInitialization.mapUnexpectedHostFailure(failure);
        }

        @Override
        public void complete() {
            // Module-initialization lifecycle is finalized exclusively through finish()/fail().
        }

        @Override
        public Object finish(Object result) { return moduleInitialization.finish(result); }
    }


    static final class PreparedCaseOfCall {
        private final Object subject;
        private final List<java.util.Map.Entry<Object, Object>> cases;
        private final ProtosActivation activation;
        private int index;
        private Object selectedAction;
        private List<Object> selectedArguments;

        PreparedCaseOfCall(
                Object subject,
                List<?> supplied,
                ProtosActivation activation) {
            this.subject = java.util.Objects.requireNonNull(subject, "subject");
            this.activation =
                    java.util.Objects.requireNonNull(
                            activation,
                            "activation");

            if (supplied.size() != 1
                    || !(supplied.get(0) instanceof ProtosMapValue caseMap)) {
                throw ProtosCoreErrors.signal(
                        activation,
                        ProtosCoreErrors.newError(activation));
            }

            this.cases =
                    List.copyOf(
                            caseMap.associationSnapshot());
        }

        boolean needsMatcher() {
            return selectedAction == null && index < cases.size();
        }

        PreparedClosureCall prepareMatcher() {
            if (!needsMatcher()) {
                throw new IllegalStateException(
                        "Object.caseOf matcher requested after selection or exhaustion");
            }

            return prepareSend(
                    cases.get(index).getKey(),
                    "match",
                    activation,
                    List.of(subject));
        }

        void acceptMatcher(Object outcome) {
            if (!needsMatcher()) {
                throw new IllegalStateException(
                        "Object.caseOf matcher outcome accepted after selection or exhaustion");
            }

            if (outcome == ProtosBooleanValue.FALSE) {
                index++;
                return;
            }

            if (outcome == ProtosBooleanValue.TRUE) {
                selectedAction = cases.get(index).getValue();
                selectedArguments = List.of();
                return;
            }

            if (outcome instanceof ProtosArrayValue captures) {
                List<Object> snapshot = captures.indexedSnapshot();

                if (snapshot.isEmpty()) {
                    throw ProtosCoreErrors.signal(
                            activation,
                            ProtosCoreErrors.newError(activation));
                }

                selectedAction = cases.get(index).getValue();
                selectedArguments = List.copyOf(snapshot);
                return;
            }

            throw ProtosCoreErrors.signal(
                    activation,
                    ProtosCoreErrors.newError(activation));
        }

        PreparedClosureCall prepareSelectedAction() {
            if (selectedAction == null) {
                throw ProtosCoreErrors.signal(
                        activation,
                        ProtosCoreErrors.newError(activation));
            }

            return prepareClosureCall(
                    selectedAction,
                    selectedArguments,
                    activation);
        }
    }

    @Operation
    public static final class IsStructuredCaseOfCall {
        @Specialization
        public static boolean perform(PreparedClosureCall prepared) {
            return prepared.isStructuredCaseOf();
        }
    }

    @Operation
    public static final class PrepareStructuredCaseOfCall {
        @Specialization
        public static PreparedCaseOfCall perform(
                PreparedClosureCall prepared) {
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

    static final class PreparedStandardObjectCall {
        private final PreparedClosureCall child;
        private final ProtosObjectValue constructedInstance;

        private PreparedStandardObjectCall(
                PreparedClosureCall child,
                ProtosObjectValue constructedInstance) {
            this.child = java.util.Objects.requireNonNull(child, "child");
            this.constructedInstance = constructedInstance;
        }

        static PreparedStandardObjectCall closure(
                PreparedClosureCall child) {
            return new PreparedStandardObjectCall(child, null);
        }

        static PreparedStandardObjectCall construction(
                PreparedClosureCall child,
                ProtosObjectValue constructedInstance) {
            return new PreparedStandardObjectCall(
                    child,
                    java.util.Objects.requireNonNull(
                            constructedInstance,
                            "constructedInstance"));
        }

        PreparedClosureCall child() {
            return child;
        }

        Object finish(Object childResult) {
            return constructedInstance == null
                    ? childResult
                    : constructedInstance;
        }
    }

    static final class PreparedStandardImportCall {
        private final PreparedClosureCall child;

        PreparedStandardImportCall(
                PreparedClosureCall child) {
            this.child = java.util.Objects.requireNonNull(child, "child");
        }

        PreparedClosureCall child() {
            return child;
        }

        Object finish(Object childResult) {
            return childResult;
        }
    }

    static final class PreparedEnsureCall {
        private final PreparedClosureCall body;
        private final PreparedClosureCall cleanup;

        PreparedEnsureCall(
                PreparedClosureCall body,
                PreparedClosureCall cleanup) {
            this.body = java.util.Objects.requireNonNull(body, "body");
            this.cleanup = java.util.Objects.requireNonNull(cleanup, "cleanup");
        }

        PreparedClosureCall body() { return body; }
        PreparedClosureCall cleanup() { return cleanup; }
    }

    static final class PreparedWhileCall {
        private final ProtosClosureValue condition;
        private final ProtosClosureValue body;
        private final ProtosActivation activation;

        PreparedWhileCall(
                ProtosClosureValue condition,
                ProtosClosureValue body,
                ProtosActivation activation) {
            this.condition = java.util.Objects.requireNonNull(condition, "condition");
            this.body = java.util.Objects.requireNonNull(body, "body");
            this.activation = java.util.Objects.requireNonNull(activation, "activation");
        }

        PreparedClosureCall prepareCondition() {
            return prepareDirectClosureCall(condition, List.of(), activation);
        }

        PreparedClosureCall prepareBody() {
            return prepareDirectClosureCall(body, List.of(), activation);
        }

        boolean conditionResult(Object result) {
            if (result == ProtosBooleanValue.TRUE) {
                return true;
            }
            if (result == ProtosBooleanValue.FALSE) {
                return false;
            }
            throw ProtosCoreErrors.signal(
                    activation,
                    ProtosCoreErrors.newError(activation));
        }

        /**
         * PLAT044 B′ (PERF026-C1) inline eligibility of an already prepared
         * fresh condition {@code child}: the condition is exactly the staged
         * {@code literal} and {@code child} is its exact inline-equivalent
         * invocation ({@link #isInlineLiteralInvocation}).
         */
        boolean admitsInlineLiteralCondition(
                PreparedClosureCall child,
                Object literal,
                ProtosClosureExecutionPlanCell literalPlan) {
            return condition == literal
                    && isInlineLiteralInvocation(child, literalPlan);
        }

        /** As {@link #admitsInlineLiteralCondition}, for a fresh body child. */
        boolean admitsInlineLiteralBody(
                PreparedClosureCall child,
                Object literal,
                ProtosClosureExecutionPlanCell literalPlan) {
            return body == literal
                    && isInlineLiteralInvocation(child, literalPlan);
        }
    }

    /**
     * PLAT044 B′ exact-invocation proof shared by every inline literal
     * callback consumer: {@code child} is the ordinary source invocation of
     * {@code literalPlan}'s own semantic activation root with a rich
     * activation whose return home is captured. Executing the literal's body
     * inline under {@code child.activation()} is then exactly {@code
     * call(child.bodyTarget(), child.activation())}: the same body and the
     * same fresh activation, and {@link
     * ReturnHomeOwningCall#handleControlTransfer} can only rethrow. Any other
     * shape (a re-projected plan, a compact or native call, an owned return
     * home) keeps the exact physical callback invocation.
     */
    private static boolean isInlineLiteralInvocation(
            PreparedClosureCall child,
            ProtosClosureExecutionPlanCell literalPlan) {
        return child instanceof OrdinarySourceCall ordinary
                && ordinary.activation != null
                && !ordinary.activation.ownsReturnHome()
                && ordinary.bodyTarget
                        == literalPlan.plan().bytecodeActivationTargetForComposition();
    }

    static final class PreparedErrorHandlerCall {
        private final ProtosDynamicControlState state;
        private final ProtosDynamicControlState.Frame frame;
        private final ProtosClosureValue handler;
        private final ProtosActivation activation;
        private final PreparedClosureCall body;
        private boolean left;

        PreparedErrorHandlerCall(
                ProtosDynamicControlState state,
                ProtosDynamicControlState.Frame frame,
                ProtosClosureValue handler,
                ProtosActivation activation,
                PreparedClosureCall body) {
            this.state = java.util.Objects.requireNonNull(state, "state");
            this.frame = java.util.Objects.requireNonNull(frame, "frame");
            this.handler = java.util.Objects.requireNonNull(handler, "handler");
            this.activation = java.util.Objects.requireNonNull(activation, "activation");
            this.body = java.util.Objects.requireNonNull(body, "body");
        }

        PreparedClosureCall body() { return body; }

        PreparedClosureCall selectHandler(AbstractTruffleException exception) {
            if (exception instanceof ProtosSignalException transfer
                    && transfer.selectedHandlerFrame().orElse(null) == frame) {
                leave();
                return prepareDirectClosureCall(
                        handler,
                        List.of(transfer.error()),
                        activation);
            }
            leave();
            throw exception;
        }

        void leave() {
            if (left) {
                return;
            }
            state.leaveFrame(frame);
            left = true;
        }
    }


    static final class PreparedBooleanCall {
        private final ProtosStandardBooleanProtocol.StructuredCallbackKind kind;
        private final Object receiver;
        private final List<?> supplied;
        private final ProtosActivation activation;

        PreparedBooleanCall(
                ProtosStandardBooleanProtocol.StructuredCallbackKind kind,
                Object receiver,
                List<?> supplied,
                ProtosActivation activation) {
            this.kind = java.util.Objects.requireNonNull(kind, "kind");
            this.receiver = java.util.Objects.requireNonNull(receiver, "receiver");
            this.supplied = List.copyOf(supplied);
            this.activation = java.util.Objects.requireNonNull(activation, "activation");
            if (receiver != ProtosBooleanValue.TRUE
                    && receiver != ProtosBooleanValue.FALSE) {
                throw ProtosCoreErrors.signal(
                        activation,
                        ProtosCoreErrors.newError(activation));
            }
            if (this.supplied.size() != kind.arity()) {
                throw ProtosCoreErrors.signal(
                        activation,
                        ProtosCoreErrors.newError(activation));
            }
        }

        boolean hasCallback() {
            return switch (kind) {
                case IF_TRUE -> receiver == ProtosBooleanValue.TRUE;
                case IF_FALSE -> receiver == ProtosBooleanValue.FALSE;
                case IF_TRUE_IF_FALSE -> true;
                case AND -> receiver == ProtosBooleanValue.TRUE;
                case OR -> receiver == ProtosBooleanValue.FALSE;
            };
        }

        PreparedClosureCall prepareCallback() {
            if (!hasCallback()) {
                throw new IllegalStateException(
                        "short-circuited Boolean operation has no selected callback");
            }
            return prepareClosureCall(
                    supplied.get(selectedIndex()),
                    List.of(),
                    activation);
        }

        /**
         * Supplied position of the selected callback; meaningful only when
         * {@link #hasCallback} holds. Shared by {@link #prepareCallback} and
         * {@link #admitsInlineLiteralCallback} so inline admission can never
         * disagree with ordinary selection.
         */
        private int selectedIndex() {
            return switch (kind) {
                case IF_TRUE, IF_FALSE, AND, OR -> 0;
                case IF_TRUE_IF_FALSE ->
                        receiver == ProtosBooleanValue.TRUE ? 0 : 1;
            };
        }

        Object immediateResult() {
            if (hasCallback()) {
                throw new IllegalStateException(
                        "selected Boolean callback has no immediate result");
            }
            return switch (kind) {
                case IF_TRUE, IF_FALSE -> ProtosNullValue.INSTANCE;
                case AND -> ProtosBooleanValue.FALSE;
                case OR -> ProtosBooleanValue.TRUE;
                case IF_TRUE_IF_FALSE ->
                        throw new IllegalStateException(
                                "ifTrueIfFalse always selects one callback");
            };
        }

        Object finishCallback(Object result) {
            return switch (kind) {
                case AND, OR ->
                        ProtosStandardBooleanProtocol.requireBooleanResult(
                                result,
                                activation);
                case IF_TRUE, IF_FALSE, IF_TRUE_IF_FALSE -> result;
            };
        }

        /**
         * PLAT044 B′ (PERF026-B1/B2/B3) inline eligibility of an already
         * prepared selected callback {@code child}.
         *
         * <p>True only when this standard capability selected a callback, the
         * selected supplied position ({@link #selectedIndex}) is exactly the
         * send site's candidate {@code position}, the value supplied there is
         * exactly {@code literal} (the immediate Closure literal value staged
         * by the send site), and {@code child} is that literal's exact
         * inline-equivalent invocation ({@link #isInlineLiteralInvocation}).
         * For IF_TRUE_IF_FALSE both callbacks are eagerly evaluated and
         * staged, but only the selected position can be admitted, so the
         * unselected literal is never entered. Any other shape (including a
         * different selected value or position) keeps the exact physical
         * callback invocation. The inline result still completes through
         * {@link #finishCallback}, so AND/OR Boolean-result validation is
         * unchanged.
         */
        boolean admitsInlineLiteralCallback(
                PreparedClosureCall child,
                Object literal,
                ProtosClosureExecutionPlanCell literalPlan,
                int position) {
            return hasCallback()
                    && selectedIndex() == position
                    && supplied.get(position) == literal
                    && isInlineLiteralInvocation(child, literalPlan);
        }
    }

    @Operation
    public static final class IsStructuredObjectCall {
        @Specialization
        public static boolean perform(PreparedClosureCall prepared) {
            return prepared.isStructuredObjectCall();
        }
    }

    @Operation
    public static final class PrepareStructuredObjectCall {
        @Specialization
        public static PreparedStandardObjectCall perform(
                PreparedClosureCall prepared) {
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
        public static boolean perform(PreparedClosureCall prepared) {
            return prepared.isStructuredImportCall();
        }
    }

    @Operation
    public static final class PrepareStructuredImportCall {
        @Specialization
        public static PreparedStandardImportCall perform(
                PreparedClosureCall prepared) {
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
    public static final class RequiresStructuredDispatch {
        @Specialization
        public static boolean perform(PreparedClosureCall prepared) {
            return prepared.requiresStructuredDispatch();
        }
    }

    @Operation
    public static final class EnterNestedStructuredDispatch {
        @Specialization
        public static Object perform(
                PreparedClosureCall prepared,
                @Cached IndirectCallNode node) {
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

    @Operation
    public static final class IsStructuredEnsureCall {
        @Specialization
        public static boolean perform(PreparedClosureCall prepared) {
            return prepared.isStructuredEnsure();
        }
    }

    @Operation
    public static final class PrepareStructuredEnsureCall {
        @Specialization
        public static PreparedEnsureCall perform(PreparedClosureCall prepared) {
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
        public static boolean perform(PreparedClosureCall prepared) {
            return prepared.isStructuredWhile();
        }
    }

    @Operation
    public static final class PrepareStructuredWhileCall {
        @Specialization
        public static PreparedWhileCall perform(PreparedClosureCall prepared) {
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
    public static final class StructuredWhileCondition {
        @Specialization
        public static boolean perform(PreparedWhileCall prepared, Object result) {
            return prepared.conditionResult(result);
        }
    }

    /**
     * PLAT044 B′ common loop state of a standard each whose callback a send
     * site may sequence locally (PERF026-D1 indexed, PERF026-D2 association,
     * PERF026-D3 Environment): validated receiver and callback, one snapshot
     * established before the first callback, one fresh
     * callback invocation per snapshot position carrying exactly that
     * position's arguments, advance only after that invocation completes
     * normally, and the original receiver as result. It lets a send site
     * sequence any such loop without duplicating its inline callback region;
     * each implementation keeps its own validation and snapshot authority.
     */
    sealed interface PreparedLocalEachCall
            permits PreparedIndexedEachCall,
                    PreparedAssociationEachCall,
                    PreparedEnvironmentEachCall {
        boolean hasNext();

        PreparedClosureCall prepareCurrent();

        void advance();

        Object finish();

        /** The validated callback value supplied to this each invocation. */
        Object callback();

        /**
         * Inline eligibility of an already prepared per-position {@code
         * child}: the callback is exactly the staged {@code literal} and
         * {@code child} is its exact inline-equivalent invocation ({@link
         * #isInlineLiteralInvocation}), whose activation already carries the
         * snapshot arguments the inline region binds.
         */
        default boolean admitsInlineLiteralChild(
                PreparedClosureCall child,
                Object literal,
                ProtosClosureExecutionPlanCell literalPlan) {
            return callback() == literal
                    && isInlineLiteralInvocation(child, literalPlan);
        }
    }

    /**
     * PLAT044 B′ (PERF026-D1) standard Array.each and Bytes.each loops, whose
     * observable shapes coincide: an ascending indexed snapshot and one
     * one-argument callback invocation per snapshot element.
     */
    sealed interface PreparedIndexedEachCall extends PreparedLocalEachCall
            permits PreparedArrayEachCall, PreparedBytesEachCall {}

    /**
     * PLAT044 B′ (PERF026-D2) standard Map.each and IdentityMap.each loops,
     * whose observable shapes coincide: an insertion-ordered association
     * snapshot of representative keys and their values, and one
     * {@code block(key, value)} invocation per snapshot association. The two
     * receiver families stay distinct; only the loop view is shared.
     */
    sealed interface PreparedAssociationEachCall extends PreparedLocalEachCall
            permits PreparedMapEachCall, PreparedIdentityMapEachCall {}

    static final class PreparedArrayEachCall implements PreparedIndexedEachCall {
        private final ProtosArrayValue array;
        private final List<Object> snapshot;
        private final Object block;
        private final ProtosActivation activation;
        private int index;

        PreparedArrayEachCall(
                Object receiver,
                List<?> supplied,
                ProtosActivation activation) {
            this.activation = java.util.Objects.requireNonNull(activation, "activation");
            if (!(receiver instanceof ProtosArrayValue value)
                    || supplied.size() != 1) {
                throw ProtosCoreErrors.signal(
                        activation,
                        ProtosCoreErrors.newError(activation));
            }
            this.array = value;
            this.block = supplied.get(0);
            ProtosStandardArrayProtocol.requireInvokableForStructured(
                    block,
                    activation);
            this.snapshot = value.indexedSnapshot();
        }

        @Override
        public boolean hasNext() {
            return index < snapshot.size();
        }

        @Override
        public PreparedClosureCall prepareCurrent() {
            if (!hasNext()) {
                throw new IllegalStateException(
                        "Array.each callback requested after snapshot exhaustion");
            }
            return prepareClosureCall(
                    block,
                    List.of(snapshot.get(index)),
                    activation);
        }

        @Override
        public void advance() {
            if (!hasNext()) {
                throw new IllegalStateException(
                        "Array.each cursor advanced after snapshot exhaustion");
            }
            index++;
        }

        @Override
        public Object finish() {
            if (hasNext()) {
                throw new IllegalStateException(
                        "Array.each finished before snapshot exhaustion");
            }
            return array;
        }

        @Override
        public Object callback() {
            return block;
        }
    }

    @Operation
    public static final class IsStructuredArrayEachCall {
        @Specialization
        public static boolean perform(PreparedClosureCall prepared) {
            return prepared.isStructuredArrayEach();
        }
    }

    @Operation
    public static final class PrepareStructuredArrayEachCall {
        @Specialization
        public static PreparedArrayEachCall perform(PreparedClosureCall prepared) {
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


    static final class PreparedArrayMatchCall {
        private final ProtosActivation activation;
        private final List<Object> matcherSnapshot;
        private final List<Object> subjectSnapshot;
        private final List<Object> captures = new ArrayList<>();
        private int index;
        private boolean mismatch;

        PreparedArrayMatchCall(
                Object receiver,
                List<?> supplied,
                ProtosActivation activation) {
            this.activation =
                    java.util.Objects.requireNonNull(
                            activation,
                            "activation");

            if (!(receiver instanceof ProtosArrayValue matcher)
                    || supplied.size() != 1) {
                throw ProtosCoreErrors.signal(
                        activation,
                        ProtosCoreErrors.newError(activation));
            }

            Object subjectValue = supplied.get(0);
            if (!(subjectValue instanceof ProtosArrayValue subject)) {
                matcherSnapshot = List.of();
                subjectSnapshot = List.of();
                mismatch = true;
                return;
            }

            matcherSnapshot = matcher.indexedSnapshot();
            subjectSnapshot = subject.indexedSnapshot();

            if (matcherSnapshot.size() != subjectSnapshot.size()) {
                mismatch = true;
            }
        }

        boolean hasNext() {
            return !mismatch && index < matcherSnapshot.size();
        }

        PreparedClosureCall prepareCurrent() {
            if (!hasNext()) {
                throw new IllegalStateException(
                        "Array.match child requested after terminal outcome");
            }

            return prepareSend(
                    matcherSnapshot.get(index),
                    "match",
                    activation,
                    List.of(subjectSnapshot.get(index)));
        }

        void acceptCurrent(Object outcome) {
            if (!hasNext()) {
                throw new IllegalStateException(
                        "Array.match child outcome accepted after terminal outcome");
            }

            if (outcome == ProtosBooleanValue.FALSE) {
                mismatch = true;
                return;
            }

            if (outcome == ProtosBooleanValue.TRUE) {
                index++;
                return;
            }

            if (outcome instanceof ProtosArrayValue childCaptures) {
                List<Object> observedCaptures =
                        childCaptures.indexedSnapshot();

                if (observedCaptures.isEmpty()) {
                    throw ProtosCoreErrors.signal(
                            activation,
                            ProtosCoreErrors.newError(activation));
                }

                for (int captureIndex = 0;
                        captureIndex < observedCaptures.size();
                        captureIndex++) {
                    captures.add(observedCaptures.get(captureIndex));
                }
                index++;
                return;
            }

            throw ProtosCoreErrors.signal(
                    activation,
                    ProtosCoreErrors.newError(activation));
        }

        Object finish() {
            if (hasNext()) {
                throw new IllegalStateException(
                        "Array.match finished before snapshot exhaustion");
            }

            if (mismatch) {
                return ProtosBooleanValue.FALSE;
            }

            if (captures.isEmpty()) {
                return ProtosBooleanValue.TRUE;
            }

            return activation
                    .prelude()
                    .orElseThrow(
                            () ->
                                    new IllegalStateException(
                                            "standard Array.match requires an owning Core prelude"))
                    .newArray(captures);
        }
    }

    @Operation
    public static final class IsStructuredArrayMatchCall {
        @Specialization
        public static boolean perform(PreparedClosureCall prepared) {
            return prepared.isStructuredArrayMatch();
        }
    }

    @Operation
    public static final class PrepareStructuredArrayMatchCall {
        @Specialization
        public static PreparedArrayMatchCall perform(
                PreparedClosureCall prepared) {
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

    static final class PreparedBytesEachCall implements PreparedIndexedEachCall {
        private final ProtosBytesValue bytes;
        private final List<Object> snapshot;
        private final Object block;
        private final ProtosActivation activation;
        private int index;

        PreparedBytesEachCall(
                Object receiver,
                List<?> supplied,
                ProtosActivation activation) {
            this.activation = java.util.Objects.requireNonNull(activation, "activation");
            if (!(receiver instanceof ProtosBytesValue value)
                    || supplied.size() != 1) {
                throw ProtosCoreErrors.signal(
                        activation,
                        ProtosCoreErrors.newError(activation));
            }
            this.bytes = value;
            this.block = supplied.get(0);
            ProtosStandardBytesProtocol.requireInvokableForStructured(
                    block,
                    activation);
            this.snapshot = value.indexedSnapshot();
        }

        @Override
        public boolean hasNext() {
            return index < snapshot.size();
        }

        @Override
        public PreparedClosureCall prepareCurrent() {
            if (!hasNext()) {
                throw new IllegalStateException(
                        "Bytes.each callback requested after snapshot exhaustion");
            }
            return prepareClosureCall(
                    block,
                    List.of(snapshot.get(index)),
                    activation);
        }

        @Override
        public void advance() {
            if (!hasNext()) {
                throw new IllegalStateException(
                        "Bytes.each cursor advanced after snapshot exhaustion");
            }
            index++;
        }

        @Override
        public Object finish() {
            if (hasNext()) {
                throw new IllegalStateException(
                        "Bytes.each finished before snapshot exhaustion");
            }
            return bytes;
        }

        @Override
        public Object callback() {
            return block;
        }
    }

    @Operation
    public static final class IsStructuredBytesEachCall {
        @Specialization
        public static boolean perform(PreparedClosureCall prepared) {
            return prepared.isStructuredBytesEach();
        }
    }

    @Operation
    public static final class PrepareStructuredBytesEachCall {
        @Specialization
        public static PreparedBytesEachCall perform(PreparedClosureCall prepared) {
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

    /**
     * Standard Environment.each loop state. It joins the PLAT044 B′
     * (PERF026-D3) local each view directly, not as an association each: its
     * snapshot is not a shallow association snapshot but the complete
     * portable (String, String) snapshot, converted, validated and
     * canonically ordered by the Environment protocol before any callback is
     * prepared.
     */
    static final class PreparedEnvironmentEachCall implements PreparedLocalEachCall {
        private final ProtosEnvironmentValue environment;
        private final List<ProtosEnvironmentValue.PortableEntry> snapshot;
        private final Object block;
        private final ProtosActivation activation;
        private int index;

        PreparedEnvironmentEachCall(
                Object receiver,
                List<?> supplied,
                ProtosActivation activation) {
            this.activation = java.util.Objects.requireNonNull(activation, "activation");
            if (!(receiver instanceof ProtosEnvironmentValue value)
                    || supplied.size() != 1) {
                throw ProtosCoreErrors.signal(
                        activation,
                        ProtosCoreErrors.newError(activation));
            }
            this.environment = value;
            this.block = supplied.get(0);
            ProtosStandardEnvironmentProtocol.requireInvokableForStructured(
                    block,
                    activation);
            /*
             * PLAT028 Environment.each must preserve the historical eager
             * portable-representation cutover. This call validates/converts
             * the complete environment before callback #1 can be prepared.
             */
            this.snapshot =
                    List.copyOf(
                            ProtosStandardEnvironmentProtocol.portableEntriesForStructured(
                                    value,
                                    activation));
        }

        @Override
        public boolean hasNext() {
            return index < snapshot.size();
        }

        @Override
        public PreparedClosureCall prepareCurrent() {
            if (!hasNext()) {
                throw new IllegalStateException(
                        "Environment.each callback requested after snapshot exhaustion");
            }
            ProtosEnvironmentValue.PortableEntry entry = snapshot.get(index);
            return prepareClosureCall(
                    block,
                    List.of(entry.name(), entry.value()),
                    activation);
        }

        @Override
        public void advance() {
            if (!hasNext()) {
                throw new IllegalStateException(
                        "Environment.each cursor advanced after snapshot exhaustion");
            }
            index++;
        }

        @Override
        public Object finish() {
            if (hasNext()) {
                throw new IllegalStateException(
                        "Environment.each finished before snapshot exhaustion");
            }
            return environment;
        }

        @Override
        public Object callback() {
            return block;
        }
    }

    @Operation
    public static final class IsStructuredEnvironmentEachCall {
        @Specialization
        public static boolean perform(PreparedClosureCall prepared) {
            return prepared.isStructuredEnvironmentEach();
        }
    }

    @Operation
    public static final class PrepareStructuredEnvironmentEachCall {
        @Specialization
        public static PreparedEnvironmentEachCall perform(PreparedClosureCall prepared) {
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


    static final class PreparedIdentityMapAtIfAbsentCall {
        private final ProtosIdentityMapValue.Entry match;
        private final Object fallback;
        private final ProtosActivation activation;
        private boolean fallbackPrepared;

        PreparedIdentityMapAtIfAbsentCall(
                Object receiver,
                List<?> supplied,
                ProtosActivation activation) {
            this.activation =
                    java.util.Objects.requireNonNull(
                            activation,
                            "activation");

            if (!(receiver instanceof ProtosIdentityMapValue map)
                    || supplied.size() != 2) {
                throw ProtosCoreErrors.signal(
                        activation,
                        ProtosCoreErrors.newError(activation));
            }

            Object key = supplied.get(0);
            this.fallback = supplied.get(1);

            this.match =
                    ProtosStandardIdentityMapProtocol.findForStructured(
                            map,
                            key);
        }

        boolean needsFallback() {
            return match == null;
        }

        PreparedClosureCall prepareFallback() {
            if (!needsFallback() || fallbackPrepared) {
                throw new IllegalStateException(
                        "IdentityMap.atIfAbsent fallback requested in an invalid state");
            }

            fallbackPrepared = true;

            return prepareClosureCall(
                    fallback,
                    List.of(),
                    activation);
        }

        Object finishFallback(Object result) {
            if (!needsFallback() || !fallbackPrepared) {
                throw new IllegalStateException(
                        "IdentityMap.atIfAbsent fallback result accepted in an invalid state");
            }
            return result;
        }

        Object finishPresent() {
            if (needsFallback()) {
                throw new IllegalStateException(
                        "IdentityMap.atIfAbsent present path has no matching entry");
            }
            if (fallbackPrepared) {
                throw new IllegalStateException(
                        "IdentityMap.atIfAbsent present path prepared its fallback");
            }
            return match.value();
        }
    }

    @Operation
    public static final class IsStructuredIdentityMapAtIfAbsentCall {
        @Specialization
        public static boolean perform(PreparedClosureCall prepared) {
            return prepared.isStructuredIdentityMapAtIfAbsent();
        }
    }

    @Operation
    public static final class PrepareStructuredIdentityMapAtIfAbsentCall {
        @Specialization
        public static PreparedIdentityMapAtIfAbsentCall perform(
                PreparedClosureCall prepared) {
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


    static final class PreparedIdentityMapEachCall implements PreparedAssociationEachCall {
        private final ProtosIdentityMapValue identityMap;
        private final List<java.util.Map.Entry<Object, Object>> snapshot;
        private final Object block;
        private final ProtosActivation activation;
        private int index;

        PreparedIdentityMapEachCall(
                Object receiver,
                List<?> supplied,
                ProtosActivation activation) {
            this.activation = java.util.Objects.requireNonNull(activation, "activation");
            if (!(receiver instanceof ProtosIdentityMapValue value)
                    || supplied.size() != 1) {
                throw ProtosCoreErrors.signal(
                        activation,
                        ProtosCoreErrors.newError(activation));
            }
            this.identityMap = value;
            this.block = supplied.get(0);
            ProtosStandardIdentityMapProtocol.requireInvokableForStructured(
                    block,
                    activation);
            this.snapshot = List.copyOf(value.associationSnapshot());
        }

        @Override
        public boolean hasNext() {
            return index < snapshot.size();
        }

        @Override
        public PreparedClosureCall prepareCurrent() {
            if (!hasNext()) {
                throw new IllegalStateException(
                        "IdentityMap.each callback requested after snapshot exhaustion");
            }
            java.util.Map.Entry<Object, Object> entry = snapshot.get(index);
            return prepareClosureCall(
                    block,
                    List.of(entry.getKey(), entry.getValue()),
                    activation);
        }

        @Override
        public void advance() {
            if (!hasNext()) {
                throw new IllegalStateException(
                        "IdentityMap.each cursor advanced after snapshot exhaustion");
            }
            index++;
        }

        @Override
        public Object finish() {
            if (hasNext()) {
                throw new IllegalStateException(
                        "IdentityMap.each finished before snapshot exhaustion");
            }
            return identityMap;
        }

        @Override
        public Object callback() {
            return block;
        }
    }

    @Operation
    public static final class IsStructuredIdentityMapEachCall {
        @Specialization
        public static boolean perform(PreparedClosureCall prepared) {
            return prepared.isStructuredIdentityMapEach();
        }
    }

    @Operation
    public static final class PrepareStructuredIdentityMapEachCall {
        @Specialization
        public static PreparedIdentityMapEachCall perform(PreparedClosureCall prepared) {
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




    static final class PreparedMapMatchCall {
        private final ProtosActivation activation;
        private final ProtosMapValue subject;
        private final List<ProtosStandardMapProtocol.StableAssociation> matcherSnapshot;
        private final List<ProtosStandardMapProtocol.StableAssociation> subjectSnapshot;
        private final List<Object> childMatchers = new ArrayList<>();
        private final List<Object> selectedValues = new ArrayList<>();
        private final List<Object> captures = new ArrayList<>();

        private int requirementIndex;
        private int candidateIndex;
        private int childIndex;

        private BigInteger queryHash;
        private ProtosStandardMapProtocol.StableAssociation selectedAssociation;

        private boolean hashAccepted;
        private boolean comparisonEntered;
        private boolean mismatch;

        PreparedMapMatchCall(
                Object receiver,
                List<?> supplied,
                ProtosActivation activation) {
            this.activation =
                    java.util.Objects.requireNonNull(
                            activation,
                            "activation");

            if (!(receiver instanceof ProtosMapValue matcher)
                    || supplied.size() != 1) {
                throw ProtosCoreErrors.signal(
                        activation,
                        ProtosCoreErrors.newError(activation));
            }

            Object subjectValue = supplied.get(0);
            if (!(subjectValue instanceof ProtosMapValue subjectMap)) {
                subject = null;
                matcherSnapshot = List.of();
                subjectSnapshot = List.of();
                mismatch = true;
                return;
            }

            subject = subjectMap;
            matcherSnapshot =
                    ProtosStandardMapProtocol.stableSnapshot(matcher);
            subjectSnapshot =
                    ProtosStandardMapProtocol.stableSnapshot(subjectMap);
        }

        boolean hasRequirement() {
            return !mismatch
                    && requirementIndex < matcherSnapshot.size();
        }

        void enterComparison() {
            if (!hasRequirement()) {
                throw new IllegalStateException(
                        "Map.match comparison entered without a current requirement");
            }
            if (comparisonEntered) {
                throw new IllegalStateException(
                        "Map.match attempted to nest its own comparison scope");
            }
            subject.enterComparison();
            comparisonEntered = true;
        }

        void leaveComparison() {
            if (!comparisonEntered) {
                throw new IllegalStateException(
                        "Map.match comparison scope left without entry");
            }
            subject.leaveComparison();
            comparisonEntered = false;
        }

        PreparedClosureCall prepareHash() {
            if (!hasRequirement() || hashAccepted) {
                throw new IllegalStateException(
                        "Map.match hash requested in an invalid state");
            }

            return prepareSend(
                    matcherSnapshot.get(requirementIndex).key(),
                    "hash",
                    activation,
                    List.of());
        }

        void acceptHash(Object result) {
            if (comparisonEntered) {
                throw new IllegalStateException(
                        "Map.match hash accepted before comparison-scope exit");
            }
            if (!hasRequirement() || hashAccepted) {
                throw new IllegalStateException(
                        "Map.match hash accepted in an invalid state");
            }

            queryHash =
                    ProtosStandardMapProtocol.requireHashResultForStructured(
                            result,
                            activation);
            candidateIndex = 0;
            selectedAssociation = null;
            hashAccepted = true;
        }

        boolean needsEquality() {
            requireHashAccepted();

            if (selectedAssociation != null) {
                return false;
            }

            while (candidateIndex < subjectSnapshot.size()
                    && !subjectSnapshot.get(candidateIndex)
                            .recordedHash()
                            .equals(queryHash)) {
                candidateIndex++;
            }

            return candidateIndex < subjectSnapshot.size();
        }

        PreparedClosureCall prepareEquality() {
            if (!needsEquality()) {
                throw new IllegalStateException(
                        "Map.match equality requested without a candidate");
            }

            return prepareSend(
                    matcherSnapshot.get(requirementIndex).key(),
                    "==",
                    activation,
                    List.of(subjectSnapshot.get(candidateIndex).key()));
        }

        void acceptEquality(Object result) {
            if (comparisonEntered) {
                throw new IllegalStateException(
                        "Map.match equality accepted before comparison-scope exit");
            }
            if (!needsEquality()) {
                throw new IllegalStateException(
                        "Map.match equality accepted without a candidate");
            }

            boolean equal =
                    ProtosStandardMapProtocol.requireEqualityResultForStructured(
                            result,
                            activation);

            if (equal) {
                selectedAssociation =
                        subjectSnapshot.get(candidateIndex);
            } else {
                candidateIndex++;
            }
        }

        void finishRequirement() {
            requireHashAccepted();

            if (comparisonEntered) {
                throw new IllegalStateException(
                        "Map.match requirement finished with an active comparison scope");
            }
            if (needsEquality()) {
                throw new IllegalStateException(
                        "Map.match requirement finished before candidate exhaustion");
            }

            if (selectedAssociation == null) {
                mismatch = true;
                return;
            }

            ProtosStandardMapProtocol.StableAssociation requirement =
                    matcherSnapshot.get(requirementIndex);

            childMatchers.add(requirement.value());
            selectedValues.add(selectedAssociation.value());

            requirementIndex++;
            candidateIndex = 0;
            queryHash = null;
            selectedAssociation = null;
            hashAccepted = false;
        }

        boolean hasChildMatcher() {
            return !mismatch && childIndex < childMatchers.size();
        }

        PreparedClosureCall prepareChildMatcher() {
            if (!hasChildMatcher()) {
                throw new IllegalStateException(
                        "Map.match child matcher requested after terminal outcome");
            }

            return prepareSend(
                    childMatchers.get(childIndex),
                    "match",
                    activation,
                    List.of(selectedValues.get(childIndex)));
        }

        void acceptChildOutcome(Object outcome) {
            if (!hasChildMatcher()) {
                throw new IllegalStateException(
                        "Map.match child outcome accepted after terminal outcome");
            }

            if (outcome == ProtosBooleanValue.FALSE) {
                mismatch = true;
                return;
            }

            if (outcome == ProtosBooleanValue.TRUE) {
                childIndex++;
                return;
            }

            if (outcome instanceof ProtosArrayValue childCaptures) {
                List<Object> observedCaptures =
                        childCaptures.indexedSnapshot();

                if (observedCaptures.isEmpty()) {
                    throw ProtosCoreErrors.signal(
                            activation,
                            ProtosCoreErrors.newError(activation));
                }

                for (int captureIndex = 0;
                        captureIndex < observedCaptures.size();
                        captureIndex++) {
                    captures.add(observedCaptures.get(captureIndex));
                }
                childIndex++;
                return;
            }

            throw ProtosCoreErrors.signal(
                    activation,
                    ProtosCoreErrors.newError(activation));
        }

        Object finish() {
            if (comparisonEntered) {
                throw new IllegalStateException(
                        "Map.match finished with an active comparison scope");
            }
            if (hasRequirement()) {
                throw new IllegalStateException(
                        "Map.match finished before requirement resolution completed");
            }
            if (hasChildMatcher()) {
                throw new IllegalStateException(
                        "Map.match finished before child matcher exhaustion");
            }

            if (mismatch) {
                return ProtosBooleanValue.FALSE;
            }

            if (captures.isEmpty()) {
                return ProtosBooleanValue.TRUE;
            }

            return activation
                    .prelude()
                    .orElseThrow(
                            () ->
                                    new IllegalStateException(
                                            "standard Map.match requires an owning Core prelude"))
                    .newArray(captures);
        }

        private void requireHashAccepted() {
            if (!hashAccepted) {
                throw new IllegalStateException(
                        "Map.match requirement used before hash acceptance");
            }
        }
    }

    @Operation
    public static final class IsStructuredMapMatchCall {
        @Specialization
        public static boolean perform(PreparedClosureCall prepared) {
            return prepared.isStructuredMapMatch();
        }
    }

    @Operation
    public static final class PrepareStructuredMapMatchCall {
        @Specialization
        public static PreparedMapMatchCall perform(
                PreparedClosureCall prepared) {
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

    static final class PreparedMapReadLookupCall {
        private final ProtosStandardMapProtocol.StructuredReadLookupKind kind;
        private final ProtosMapValue map;
        private final Object key;
        private final Object fallback;
        private final ProtosActivation activation;
        private BigInteger queryHash;
        private List<ProtosStandardMapProtocol.StableAssociation> snapshot;
        private int index;
        private ProtosStandardMapProtocol.StableAssociation match;
        private boolean hashAccepted;
        private boolean comparisonEntered;
        private boolean fallbackPrepared;

        PreparedMapReadLookupCall(
                ProtosStandardMapProtocol.StructuredReadLookupKind kind,
                Object receiver,
                List<?> supplied,
                ProtosActivation activation) {
            this.kind = java.util.Objects.requireNonNull(kind, "kind");
            this.activation = java.util.Objects.requireNonNull(activation, "activation");
            int expectedArity =
                    kind == ProtosStandardMapProtocol.StructuredReadLookupKind.AT_IF_ABSENT
                            ? 2
                            : 1;
            if (!(receiver instanceof ProtosMapValue value)
                    || supplied.size() != expectedArity) {
                throw ProtosCoreErrors.signal(
                        activation,
                        ProtosCoreErrors.newError(activation));
            }
            this.map = value;
            this.key = supplied.get(0);
            this.fallback =
                    kind == ProtosStandardMapProtocol.StructuredReadLookupKind.AT_IF_ABSENT
                            ? supplied.get(1)
                            : null;
        }

        void enterComparison() {
            if (comparisonEntered) {
                throw new IllegalStateException(
                        "Map read lookup attempted to nest its own comparison scope");
            }
            map.enterComparison();
            comparisonEntered = true;
        }

        void leaveComparison() {
            if (!comparisonEntered) {
                throw new IllegalStateException(
                        "Map read lookup comparison scope left without entry");
            }
            map.leaveComparison();
            comparisonEntered = false;
        }

        PreparedClosureCall prepareHash() {
            if (hashAccepted) {
                throw new IllegalStateException(
                        "Map read lookup hash callback prepared after hash acceptance");
            }
            return prepareSend(
                    key,
                    "hash",
                    activation,
                    List.of());
        }

        void acceptHash(Object result) {
            if (comparisonEntered) {
                throw new IllegalStateException(
                        "Map read lookup hash result accepted before comparison-scope exit");
            }
            if (hashAccepted) {
                throw new IllegalStateException(
                        "Map read lookup hash result accepted twice");
            }
            queryHash =
                    ProtosStandardMapProtocol.requireHashResultForStructured(
                            result,
                            activation);
            snapshot = ProtosStandardMapProtocol.stableSnapshot(map);
            hashAccepted = true;
        }

        boolean needsEquality() {
            requireHashAccepted();
            if (match != null) {
                return false;
            }
            while (index < snapshot.size()
                    && !snapshot.get(index).recordedHash().equals(queryHash)) {
                index++;
            }
            return index < snapshot.size();
        }

        PreparedClosureCall prepareEquality() {
            if (!needsEquality()) {
                throw new IllegalStateException(
                        "Map read lookup equality callback requested without a candidate");
            }
            return prepareSend(
                    key,
                    "==",
                    activation,
                    List.of(snapshot.get(index).key()));
        }

        void acceptEquality(Object result) {
            if (comparisonEntered) {
                throw new IllegalStateException(
                        "Map read lookup equality result accepted before comparison-scope exit");
            }
            if (!needsEquality()) {
                throw new IllegalStateException(
                        "Map read lookup equality result accepted without a candidate");
            }
            boolean equal =
                    ProtosStandardMapProtocol.requireEqualityResultForStructured(
                            result,
                            activation);
            if (equal) {
                match = snapshot.get(index);
            } else {
                index++;
            }
        }

        boolean needsFallback() {
            requireSearchComplete();
            return kind == ProtosStandardMapProtocol.StructuredReadLookupKind.AT_IF_ABSENT
                    && match == null;
        }

        PreparedClosureCall prepareFallback() {
            if (!needsFallback() || fallbackPrepared) {
                throw new IllegalStateException(
                        "Map.atIfAbsent fallback requested in an invalid state");
            }
            fallbackPrepared = true;
            return prepareClosureCall(
                    fallback,
                    List.of(),
                    activation);
        }

        Object finishFallback(Object result) {
            requireSearchComplete();
            if (kind != ProtosStandardMapProtocol.StructuredReadLookupKind.AT_IF_ABSENT
                    || match != null
                    || !fallbackPrepared) {
                throw new IllegalStateException(
                        "Map.atIfAbsent fallback result accepted in an invalid state");
            }
            return result;
        }

        Object finish() {
            requireSearchComplete();

            return switch (kind) {
                case AT -> {
                    if (match == null) {
                        throw ProtosCoreErrors.signal(
                                activation,
                                ProtosCoreErrors.newError(activation));
                    }
                    yield match.value();
                }
                case CONTAINS_KEY ->
                        match == null
                                ? ProtosBooleanValue.FALSE
                                : ProtosBooleanValue.TRUE;
                case AT_IF_ABSENT -> {
                    if (match == null) {
                        throw new IllegalStateException(
                                "Map.atIfAbsent absent path requires its fallback result");
                    }
                    yield match.value();
                }
            };
        }

        private void requireSearchComplete() {
            requireHashAccepted();
            if (comparisonEntered) {
                throw new IllegalStateException(
                        "Map read lookup finished with an active comparison scope");
            }
            if (needsEquality()) {
                throw new IllegalStateException(
                        "Map read lookup used before candidate exhaustion");
            }
        }

        private void requireHashAccepted() {
            if (!hashAccepted) {
                throw new IllegalStateException(
                        "Map read lookup used before hash acceptance");
            }
        }
    }

    @Operation
    public static final class IsStructuredMapReadLookupCall {
        @Specialization
        public static boolean perform(PreparedClosureCall prepared) {
            return prepared.isStructuredMapReadLookup();
        }
    }

    @Operation
    public static final class PrepareStructuredMapReadLookupCall {
        @Specialization
        public static PreparedMapReadLookupCall perform(PreparedClosureCall prepared) {
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
    public static final class PrepareMapConstructionFactoryCall {
        @Specialization
        public static PreparedClosureCall perform(
                ProtosActivation activation,
                Object factory) {
            ProtosSlotLookupResult selected =
                    ProtosStandardMapProtocol
                            .selectFactoryCallForMapConstruction(
                                    factory,
                                    activation);

            return prepareImmediateMethodCall(
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
        public static ProtosMapValue perform(
                ProtosActivation activation,
                Object result) {
            return ProtosStandardMapProtocol
                    .requireMapConstructionResult(
                            result,
                            activation);
        }
    }

    static final class PreparedMapInitialDefinition {
        private final ProtosMapValue map;
        private final Object key;
        private final Object value;
        private final ProtosActivation activation;
        private BigInteger queryHash;
        private List<ProtosMapValue.Entry> snapshot;
        private int index;
        private ProtosMapValue.Entry match;
        private boolean hashAccepted;
        private boolean comparisonEntered;

        PreparedMapInitialDefinition(
                Object receiver,
                Object key,
                Object value,
                ProtosActivation activation) {
            this.activation =
                    java.util.Objects.requireNonNull(
                            activation,
                            "activation");

            if (!(receiver instanceof ProtosMapValue mapValue)
                    || !mapValue.isOpen()
                    || mapValue.comparisonActive()) {
                throw ProtosCoreErrors.signal(
                        activation,
                        ProtosCoreErrors.newError(activation));
            }

            this.map = mapValue;
            this.key = java.util.Objects.requireNonNull(key, "key");
            this.value = java.util.Objects.requireNonNull(value, "value");
        }

        void enterComparison() {
            if (comparisonEntered) {
                throw new IllegalStateException(
                        "Map initial definition attempted to nest its own comparison scope");
            }
            map.enterComparison();
            comparisonEntered = true;
        }

        void leaveComparison() {
            if (!comparisonEntered) {
                throw new IllegalStateException(
                        "Map initial-definition comparison scope left without entry");
            }
            map.leaveComparison();
            comparisonEntered = false;
        }

        PreparedClosureCall prepareHash() {
            if (hashAccepted) {
                throw new IllegalStateException(
                        "Map initial-definition hash callback prepared after hash acceptance");
            }
            return prepareSend(
                    key,
                    "hash",
                    activation,
                    List.of());
        }

        void acceptHash(Object result) {
            if (comparisonEntered) {
                throw new IllegalStateException(
                        "Map initial-definition hash accepted before comparison-scope exit");
            }
            if (hashAccepted) {
                throw new IllegalStateException(
                        "Map initial-definition hash accepted twice");
            }

            queryHash =
                    ProtosStandardMapProtocol.requireHashResultForStructured(
                            result,
                            activation);
            snapshot = List.copyOf(map.keyedSnapshot());
            hashAccepted = true;
        }

        boolean needsEquality() {
            requireHashAccepted();

            if (match != null) {
                return false;
            }

            while (index < snapshot.size()
                    && !snapshot.get(index)
                            .recordedHash()
                            .equals(queryHash)) {
                index++;
            }

            return index < snapshot.size();
        }

        PreparedClosureCall prepareEquality() {
            if (!needsEquality()) {
                throw new IllegalStateException(
                        "Map initial-definition equality callback requested without a candidate");
            }

            return prepareSend(
                    key,
                    "==",
                    activation,
                    List.of(snapshot.get(index).key()));
        }

        void acceptEquality(Object result) {
            if (comparisonEntered) {
                throw new IllegalStateException(
                        "Map initial-definition equality accepted before comparison-scope exit");
            }
            if (!needsEquality()) {
                throw new IllegalStateException(
                        "Map initial-definition equality accepted without a candidate");
            }

            boolean equal =
                    ProtosStandardMapProtocol.requireEqualityResultForStructured(
                            result,
                            activation);

            if (equal) {
                match = snapshot.get(index);
            } else {
                index++;
            }
        }

        void finish() {
            requireHashAccepted();

            if (comparisonEntered) {
                throw new IllegalStateException(
                        "Map initial definition finished with an active comparison scope");
            }
            if (needsEquality()) {
                throw new IllegalStateException(
                        "Map initial definition finished before candidate exhaustion");
            }

            if (match != null
                    || !map.isOpen()
                    || map.comparisonActive()) {
                throw ProtosCoreErrors.signal(
                        activation,
                        ProtosCoreErrors.newError(activation));
            }

            map.append(key, queryHash, value);
        }

        private void requireHashAccepted() {
            if (!hashAccepted) {
                throw new IllegalStateException(
                        "Map initial definition used before hash acceptance");
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

    @Operation
    public static final class EnterMapInitialDefinitionComparison {
        @Specialization
        public static void perform(
                PreparedMapInitialDefinition prepared) {
            prepared.enterComparison();
        }
    }

    @Operation
    public static final class LeaveMapInitialDefinitionComparison {
        @Specialization
        public static void perform(
                PreparedMapInitialDefinition prepared) {
            prepared.leaveComparison();
        }
    }

    @Operation
    public static final class PrepareMapInitialDefinitionHashCall {
        @Specialization
        public static PreparedClosureCall perform(
                PreparedMapInitialDefinition prepared) {
            return prepared.prepareHash();
        }
    }

    @Operation
    public static final class AcceptMapInitialDefinitionHashResult {
        @Specialization
        public static void perform(
                PreparedMapInitialDefinition prepared,
                Object result) {
            prepared.acceptHash(result);
        }
    }

    @Operation
    public static final class MapInitialDefinitionNeedsEquality {
        @Specialization
        public static boolean perform(
                PreparedMapInitialDefinition prepared) {
            return prepared.needsEquality();
        }
    }

    @Operation
    public static final class PrepareMapInitialDefinitionEqualityCall {
        @Specialization
        public static PreparedClosureCall perform(
                PreparedMapInitialDefinition prepared) {
            return prepared.prepareEquality();
        }
    }

    @Operation
    public static final class AcceptMapInitialDefinitionEqualityResult {
        @Specialization
        public static void perform(
                PreparedMapInitialDefinition prepared,
                Object result) {
            prepared.acceptEquality(result);
        }
    }

    @Operation
    public static final class FinishMapInitialDefinition {
        @Specialization
        public static void perform(
                PreparedMapInitialDefinition prepared) {
            prepared.finish();
        }
    }

    static final class PreparedMapAtPutCall {
        private final ProtosMapValue map;
        private final Object key;
        private final Object newValue;
        private final ProtosActivation activation;
        private BigInteger queryHash;
        private List<ProtosMapValue.Entry> snapshot;
        private int index;
        private ProtosMapValue.Entry match;
        private boolean hashAccepted;
        private boolean comparisonEntered;

        PreparedMapAtPutCall(
                Object receiver,
                List<?> supplied,
                ProtosActivation activation) {
            this.activation = java.util.Objects.requireNonNull(activation, "activation");
            if (!(receiver instanceof ProtosMapValue mapValue)
                    || supplied.size() != 2) {
                throw ProtosCoreErrors.signal(
                        activation,
                        ProtosCoreErrors.newError(activation));
            }
            this.map = mapValue;
            ProtosStandardMapProtocol.requireMutationEntryForStructured(map, activation);
            this.key = supplied.get(0);
            this.newValue = supplied.get(1);
        }

        void enterComparison() {
            if (comparisonEntered) {
                throw new IllegalStateException(
                        "Map.atPut attempted to nest its own comparison scope");
            }
            map.enterComparison();
            comparisonEntered = true;
        }

        void leaveComparison() {
            if (!comparisonEntered) {
                throw new IllegalStateException(
                        "Map.atPut comparison scope left without entry");
            }
            map.leaveComparison();
            comparisonEntered = false;
        }

        PreparedClosureCall prepareHash() {
            if (hashAccepted) {
                throw new IllegalStateException(
                        "Map.atPut hash callback prepared after hash acceptance");
            }
            return prepareSend(key, "hash", activation, List.of());
        }

        void acceptHash(Object result) {
            if (comparisonEntered) {
                throw new IllegalStateException(
                        "Map.atPut hash result accepted before comparison-scope exit");
            }
            if (hashAccepted) {
                throw new IllegalStateException(
                        "Map.atPut hash result accepted twice");
            }
            queryHash =
                    ProtosStandardMapProtocol.requireHashResultForStructured(
                            result,
                            activation);
            snapshot = List.copyOf(map.keyedSnapshot());
            hashAccepted = true;
        }

        boolean needsEquality() {
            requireHashAccepted();
            if (match != null) {
                return false;
            }
            while (index < snapshot.size()
                    && !snapshot.get(index).recordedHash().equals(queryHash)) {
                index++;
            }
            return index < snapshot.size();
        }

        PreparedClosureCall prepareEquality() {
            if (!needsEquality()) {
                throw new IllegalStateException(
                        "Map.atPut equality callback requested without a candidate");
            }
            return prepareSend(
                    key,
                    "==",
                    activation,
                    List.of(snapshot.get(index).key()));
        }

        void acceptEquality(Object result) {
            if (comparisonEntered) {
                throw new IllegalStateException(
                        "Map.atPut equality result accepted before comparison-scope exit");
            }
            if (!needsEquality()) {
                throw new IllegalStateException(
                        "Map.atPut equality result accepted without a candidate");
            }
            boolean equal =
                    ProtosStandardMapProtocol.requireEqualityResultForStructured(
                            result,
                            activation);
            if (equal) {
                match = snapshot.get(index);
            } else {
                index++;
            }
        }

        Object finish() {
            requireHashAccepted();
            if (comparisonEntered) {
                throw new IllegalStateException(
                        "Map.atPut finished with an active comparison scope");
            }
            if (needsEquality()) {
                throw new IllegalStateException(
                        "Map.atPut finished before candidate exhaustion");
            }
            if (match != null) {
                if (map.isFrozen()) {
                    throw ProtosCoreErrors.signal(
                            activation,
                            ProtosCoreErrors.newError(activation));
                }
                map.replaceValue(match, newValue);
                return newValue;
            }
            if (!map.isOpen()) {
                throw ProtosCoreErrors.signal(
                        activation,
                        ProtosCoreErrors.newError(activation));
            }
            map.append(key, queryHash, newValue);
            return newValue;
        }

        private void requireHashAccepted() {
            if (!hashAccepted) {
                throw new IllegalStateException(
                        "Map.atPut used before hash acceptance");
            }
        }
    }

    @Operation
    public static final class IsStructuredMapAtPutCall {
        @Specialization
        public static boolean perform(PreparedClosureCall prepared) {
            return prepared.isStructuredMapAtPut();
        }
    }

    @Operation
    public static final class PrepareStructuredMapAtPutCall {
        @Specialization
        public static PreparedMapAtPutCall perform(PreparedClosureCall prepared) {
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

    static final class PreparedMapRemoveCall {
        private final ProtosMapValue map;
        private final Object key;
        private final ProtosActivation activation;
        private BigInteger queryHash;
        private List<ProtosMapValue.Entry> snapshot;
        private int index;
        private ProtosMapValue.Entry match;
        private boolean hashAccepted;
        private boolean comparisonEntered;

        PreparedMapRemoveCall(
                Object receiver,
                List<?> supplied,
                ProtosActivation activation) {
            this.activation = java.util.Objects.requireNonNull(activation, "activation");
            if (!(receiver instanceof ProtosMapValue mapValue)
                    || supplied.size() != 1) {
                throw ProtosCoreErrors.signal(
                        activation,
                        ProtosCoreErrors.newError(activation));
            }
            this.map = mapValue;
            ProtosStandardMapProtocol.requireMutationEntryForStructured(map, activation);
            if (!map.isOpen()) {
                throw ProtosCoreErrors.signal(
                        activation,
                        ProtosCoreErrors.newError(activation));
            }
            this.key = supplied.get(0);
        }

        void enterComparison() {
            if (comparisonEntered) {
                throw new IllegalStateException(
                        "Map.remove attempted to nest its own comparison scope");
            }
            map.enterComparison();
            comparisonEntered = true;
        }

        void leaveComparison() {
            if (!comparisonEntered) {
                throw new IllegalStateException(
                        "Map.remove comparison scope left without entry");
            }
            map.leaveComparison();
            comparisonEntered = false;
        }

        PreparedClosureCall prepareHash() {
            if (hashAccepted) {
                throw new IllegalStateException(
                        "Map.remove hash callback prepared after hash acceptance");
            }
            return prepareSend(key, "hash", activation, List.of());
        }

        void acceptHash(Object result) {
            if (comparisonEntered) {
                throw new IllegalStateException(
                        "Map.remove hash result accepted before comparison-scope exit");
            }
            if (hashAccepted) {
                throw new IllegalStateException(
                        "Map.remove hash result accepted twice");
            }
            queryHash =
                    ProtosStandardMapProtocol.requireHashResultForStructured(
                            result,
                            activation);
            snapshot = List.copyOf(map.keyedSnapshot());
            hashAccepted = true;
        }

        boolean needsEquality() {
            requireHashAccepted();
            if (match != null) {
                return false;
            }
            while (index < snapshot.size()
                    && !snapshot.get(index).recordedHash().equals(queryHash)) {
                index++;
            }
            return index < snapshot.size();
        }

        PreparedClosureCall prepareEquality() {
            if (!needsEquality()) {
                throw new IllegalStateException(
                        "Map.remove equality callback requested without a candidate");
            }
            return prepareSend(
                    key,
                    "==",
                    activation,
                    List.of(snapshot.get(index).key()));
        }

        void acceptEquality(Object result) {
            if (comparisonEntered) {
                throw new IllegalStateException(
                        "Map.remove equality result accepted before comparison-scope exit");
            }
            if (!needsEquality()) {
                throw new IllegalStateException(
                        "Map.remove equality result accepted without a candidate");
            }
            boolean equal =
                    ProtosStandardMapProtocol.requireEqualityResultForStructured(
                            result,
                            activation);
            if (equal) {
                match = snapshot.get(index);
            } else {
                index++;
            }
        }

        Object finish() {
            requireHashAccepted();
            if (comparisonEntered) {
                throw new IllegalStateException(
                        "Map.remove finished with an active comparison scope");
            }
            if (needsEquality()) {
                throw new IllegalStateException(
                        "Map.remove finished before candidate exhaustion");
            }
            if (match == null || !map.isOpen()) {
                throw ProtosCoreErrors.signal(
                        activation,
                        ProtosCoreErrors.newError(activation));
            }
            return map.remove(match);
        }

        private void requireHashAccepted() {
            if (!hashAccepted) {
                throw new IllegalStateException(
                        "Map.remove used before hash acceptance");
            }
        }
    }

    @Operation
    public static final class IsStructuredMapRemoveCall {
        @Specialization
        public static boolean perform(PreparedClosureCall prepared) {
            return prepared.isStructuredMapRemove();
        }
    }

    @Operation
    public static final class PrepareStructuredMapRemoveCall {
        @Specialization
        public static PreparedMapRemoveCall perform(PreparedClosureCall prepared) {
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

    static final class PreparedMapEachCall implements PreparedAssociationEachCall {
        private final ProtosMapValue map;
        private final List<java.util.Map.Entry<Object, Object>> snapshot;
        private final Object block;
        private final ProtosActivation activation;
        private int index;

        PreparedMapEachCall(
                Object receiver,
                List<?> supplied,
                ProtosActivation activation) {
            this.activation = java.util.Objects.requireNonNull(activation, "activation");
            if (!(receiver instanceof ProtosMapValue value)
                    || supplied.size() != 1) {
                throw ProtosCoreErrors.signal(
                        activation,
                        ProtosCoreErrors.newError(activation));
            }
            this.map = value;
            this.block = supplied.get(0);
            ProtosStandardMapProtocol.requireInvokableForStructured(
                    block,
                    activation);
            this.snapshot = List.copyOf(value.associationSnapshot());
        }

        @Override
        public boolean hasNext() {
            return index < snapshot.size();
        }

        @Override
        public PreparedClosureCall prepareCurrent() {
            if (!hasNext()) {
                throw new IllegalStateException(
                        "Map.each callback requested after snapshot exhaustion");
            }
            java.util.Map.Entry<Object, Object> entry = snapshot.get(index);
            return prepareClosureCall(
                    block,
                    List.of(entry.getKey(), entry.getValue()),
                    activation);
        }

        @Override
        public void advance() {
            if (!hasNext()) {
                throw new IllegalStateException(
                        "Map.each cursor advanced after snapshot exhaustion");
            }
            index++;
        }

        @Override
        public Object finish() {
            if (hasNext()) {
                throw new IllegalStateException(
                        "Map.each finished before snapshot exhaustion");
            }
            return map;
        }

        @Override
        public Object callback() {
            return block;
        }
    }

    @Operation
    public static final class IsStructuredMapEachCall {
        @Specialization
        public static boolean perform(PreparedClosureCall prepared) {
            return prepared.isStructuredMapEach();
        }
    }

    @Operation
    public static final class PrepareStructuredMapEachCall {
        @Specialization
        public static PreparedMapEachCall perform(PreparedClosureCall prepared) {
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
    public static final class IsStructuredBooleanCall {
        @Specialization
        public static boolean perform(PreparedClosureCall prepared) {
            return prepared.isStructuredBoolean();
        }
    }

    @Operation
    public static final class PrepareStructuredBooleanCall {
        @Specialization
        public static PreparedBooleanCall perform(PreparedClosureCall prepared) {
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
        public static boolean perform(PreparedClosureCall prepared) {
            return prepared.isStructuredErrorHandler();
        }
    }

    @Operation
    public static final class PrepareStructuredErrorHandlerCall {
        @Specialization
        public static PreparedErrorHandlerCall perform(PreparedClosureCall prepared) {
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
    public static final class CompleteClosureCall {
        @Specialization
        public static void perform(PreparedClosureCall prepared) {
            prepared.complete();
        }
    }

    static final class PreparedArgumentVector {
        private final ArrayList<Object> values = new ArrayList<>();

        void append(Object value) {
            values.add(
                    java.util.Objects.requireNonNull(
                            value,
                            "supplied argument"));
        }

        void appendSpread(
                Object value,
                ProtosActivation caller) {
            if (!(value instanceof ProtosArrayValue array)) {
                throw new ProtosSignalException(
                        ProtosCoreErrors.newError(caller));
            }

            /*
             * CALLABLES requires the shallow Array snapshot at the spread
             * item's own left-to-right evaluation position, before any later
             * argument expression can mutate the source Array.
             */
            List<Object> spread = array.indexedSnapshot();
            for (int index = 0; index < spread.size(); index++) {
                values.add(spread.get(index));
            }
        }

        List<Object> snapshot() {
            return List.copyOf(values);
        }
    }




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
        public static void perform(
                PreparedArgumentVector vector,
                Object value) {
            vector.append(value);
        }
    }

    @Operation
    public static final class AppendSpreadSuppliedArgument {
        @Specialization
        public static void perform(
                PreparedArgumentVector vector,
                Object value,
                ProtosActivation caller) {
            vector.appendSpread(value, caller);
        }
    }

    @Operation
    public static final class PrepareClosureCallVector {
        @Specialization
        public static PreparedClosureCall perform(
                Object receiver,
                ProtosActivation caller,
                PreparedArgumentVector supplied) {
            return prepareClosureCall(
                    receiver,
                    supplied.snapshot(),
                    caller);
        }
    }

    static PreparedClosureCall prepareTaskOwnedDirectClosureIfBytecode(
            ProtosClosureValue closure,
            List<?> supplied,
            ProtosActivation creator,
            ProtosTask task) {
        java.util.Objects.requireNonNull(closure, "closure");
        java.util.Objects.requireNonNull(supplied, "supplied");
        java.util.Objects.requireNonNull(creator, "creator");
        java.util.Objects.requireNonNull(task, "task");
        if (closure.nativeBody().isPresent()) {
            return null;
        }
        ProtosClosureExecutionPlan plan = taskOwnedBytecodePlan(closure);
        if (plan == null) {
            return null;
        }
        /*
         * PERF025 / PLAT040 F': the callee materializes its exact activation,
         * attached to this exact Task, after target entry; its guest Context
         * and supplied guest Array stay deferred.
         */
        return PreparedClosureCall.ordinaryCompact(
                plan.bytecodeActivationTargetForComposition(),
                ProtosFrameArguments.compactDirectClosureCall(
                        closure,
                        creator,
                        task,
                        supplied.toArray()));
    }


    static PreparedClosureCall prepareTaskOwnedDirectNativeForCPrime(
            ProtosClosureValue closure,
            List<?> supplied,
            ProtosActivation creator,
            ProtosTask task) {
        java.util.Objects.requireNonNull(closure, "closure");
        java.util.Objects.requireNonNull(supplied, "supplied");
        java.util.Objects.requireNonNull(creator, "creator");
        java.util.Objects.requireNonNull(task, "task");
        if (closure.nativeBody().isEmpty()) {
            throw new IllegalArgumentException(
                    "Task native C-prime entry requires a native Closure");
        }

        /*
         * Direct Closure execution has already selected the exact Closure
         * identity. Preserve implementation provenance for extracted/bound
         * standard callback/control Closures exactly as PLAT019/PLAT021/PLAT028
         * require; no selector/home inference is introduced here.
         */
        rejectComposedInvocationProjection(closure);
        ProtosActivation activation =
                ProtosActivation.forClosureInvocation(
                        closure,
                        supplied,
                        creator.prelude().orElse(null),
                        creator.actorModuleState(),
                        creator.currentModuleKey().orElse(null),
                        creator.executionDomain());
        activation.attachTask(task);
        return finishPreparingComposedCallByImplementation(
                closure,
                supplied,
                activation);
    }

    static PreparedClosureCall prepareTaskOwnedSelectedCallIfBytecode(
            Object receiver,
            ProtosSlotLookupResult selected,
            List<?> supplied,
            ProtosActivation creator,
            ProtosTask task) {
        java.util.Objects.requireNonNull(receiver, "receiver");
        java.util.Objects.requireNonNull(selected, "selected");
        java.util.Objects.requireNonNull(supplied, "supplied");
        java.util.Objects.requireNonNull(creator, "creator");
        java.util.Objects.requireNonNull(task, "task");

        if (!(selected.value() instanceof ProtosClosureValue closure)) {
            throw new ProtosSignalException(ProtosCoreErrors.newError(creator));
        }

        /*
         * Preserve PLAT017 exactly: ordinary D013 lookup has already selected
         * Object.call. Only the exact canonical selection may invoke the Closure
         * receiver intrinsically; aliases/overrides remain normal methods.
         */
        if (receiver instanceof ProtosClosureValue targetClosure
                && ProtosStandardObjectProtocol.isCanonicalStandardCallSelection(
                        closure,
                        selected.home())) {
            return prepareTaskOwnedDirectClosureIfBytecode(
                    targetClosure,
                    supplied,
                    creator,
                    task);
        }

        if (closure.nativeBody().isPresent()) {
            return null;
        }
        ProtosClosureExecutionPlan plan = taskOwnedBytecodePlan(closure);
        if (plan == null) {
            return null;
        }
        ProtosActivation activation =
                ProtosActivation.forImmediateMethodInvocation(
                        closure,
                        supplied,
                        receiver,
                        selected.home(),
                        creator.prelude().orElse(null),
                        creator.actorModuleState(),
                        creator.currentModuleKey().orElse(null),
                        creator.executionDomain());
        activation.attachTask(task);
        return PreparedClosureCall.ordinary(
                plan.bytecodeActivationTargetForComposition(),
                activation);
    }


    static PreparedClosureCall prepareTaskOwnedSelectedNativeForCPrime(
            Object receiver,
            ProtosSlotLookupResult selected,
            List<?> supplied,
            ProtosActivation creator,
            ProtosTask task) {
        java.util.Objects.requireNonNull(receiver, "receiver");
        java.util.Objects.requireNonNull(selected, "selected");
        java.util.Objects.requireNonNull(supplied, "supplied");
        java.util.Objects.requireNonNull(creator, "creator");
        java.util.Objects.requireNonNull(task, "task");

        if (!(selected.value() instanceof ProtosClosureValue closure)) {
            throw new ProtosSignalException(ProtosCoreErrors.newError(creator));
        }

        /*
         * PLAT017 remains post-lookup authority. Only the exact canonical
         * Object.call selection may elide that native bridge and execute the
         * Closure receiver itself.
         */
        if (receiver instanceof ProtosClosureValue targetClosure
                && ProtosStandardObjectProtocol.isCanonicalStandardCallSelection(
                        closure,
                        selected.home())) {
            return prepareTaskOwnedDirectNativeForCPrime(
                    targetClosure,
                    supplied,
                    creator,
                    task);
        }

        if (closure.nativeBody().isEmpty()) {
            throw new IllegalArgumentException(
                    "Task selected-native C-prime entry requires a native effective target");
        }

        /*
         * Preserve PLAT025 for the exact canonical import facility. The caller
         * remains the original semantic caller for resolution/error attribution;
         * the explicit Task is supplied separately so no shared creator
         * activation is mutated merely to host module initialization.
         */
        ProtosModuleRuntime standardImportRuntime =
                ProtosStandardImportProtocol.selectedRuntimeForBytecodeIntrinsic(
                        receiver,
                        closure,
                        selected.home(),
                        creator.prelude().orElse(null));
        if (standardImportRuntime != null) {
            return PreparedClosureCall.moduleInitialization(
                    standardImportRuntime.prepareBytecodeImportForTask(
                            supplied,
                            creator,
                            task));
        }

        rejectComposedInvocationProjection(closure);
        ProtosActivation activation =
                ProtosActivation.forImmediateMethodInvocation(
                        closure,
                        supplied,
                        receiver,
                        selected.home(),
                        creator.prelude().orElse(null),
                        creator.actorModuleState(),
                        creator.currentModuleKey().orElse(null),
                        creator.executionDomain());
        activation.attachTask(task);
        return finishPreparingComposedCallByImplementation(
                closure,
                supplied,
                activation);
    }

    private static ProtosClosureExecutionPlan taskOwnedBytecodePlan(
            ProtosClosureValue closure) {
        ProtosClosureExecutionPlan template =
                closure.executionPlanForRuntimeInvocation();

        ProtosLanguageContext enteredContext =
                ProtosLanguageContext.currentIfEnteredForRuntime();
        if (enteredContext != null) {
            boolean foreignContextPlan =
                    template.language().orElseThrow()
                            != enteredContext.languageForRuntime();
            if (closure.requiresContextLocalExecutionProjectionForRuntime()
                    || foreignContextPlan) {
                if (template.source().isEmpty()) {
                    return null;
                }
                return enteredContext.bytecodeExecutionPlanForEnteredClosure(
                        closure,
                        template);
            }
        } else if (closure.requiresContextLocalExecutionProjectionForRuntime()) {
            return null;
        }

        return template;
    }

    /**
     * PERF014: the stable executable-identity key and Context-owned activation
     * target admitted by direct Closure-call invocation ({@code operation()}).
     *
     * <p>Admission requires exactly the canonical {@code Object.call} selection
     * (see {@link ProtosStandardObjectProtocol#isCanonicalStandardCallSelection})
     * on a non-native, ordinary source-backed Closure receiver whose Context-
     * owned Bytecode target can be derived. Any override, shadowing, native
     * body, or projection failure misses here and remains on the exact generic
     * {@link #prepareClosureCall} path.
     */
    record GuardedDirectClosureCallTarget(
            ProtosClosureValue closure,
            RootCallTarget target,
            Assumption stability) {}

    /**
     * Resolves and classifies only while establishing a specialization for one
     * direct Closure-call site. Reuses {@link ProtosValueLookup#lookupGuarded}
     * exactly as {@link PrepareSendArguments#createGuardedSend} does for
     * ordinary sends, and reuses
     * {@link PrepareSendArguments#fastOrdinarySendTarget} for the
     * Context-owned target derivation (including foreign-Context projection),
     * so admission stays exactly as discriminating as the institutions I072-A
     * already established. The valid hit below never repeats this lookup or
     * the generic {@code finishPreparingComposedCallByImplementation}
     * structured-protocol classification.
     */
    static GuardedDirectClosureCallTarget createGuardedDirectClosureCall(
            Object receiver,
            ProtosActivation caller,
            ProtosLanguageContext enteredContext) {
        if (enteredContext == null || !(receiver instanceof ProtosClosureValue targetClosure)) {
            return null;
        }
        ProtosValueLookup.GuardedLookup lookup;
        try {
            lookup = ProtosValueLookup.lookupGuarded(
                    receiver, "call", caller.preludeOrNullForRuntime());
        } catch (UnsupportedOperationException unsupportedRepresentation) {
            return null;
        }
        if (lookup == null) {
            return null;
        }
        ProtosSlotLookupResult selected = lookup.selected();
        if (targetClosure.nativeBody().isPresent()
                || !ProtosStandardObjectProtocol.isCanonicalStandardCallSelection(
                        selected.value(), selected.home())) {
            lookup.stability().invalidate();
            return null;
        }
        RootCallTarget target =
                PrepareSendArguments.fastOrdinarySendTarget(targetClosure, enteredContext);
        if (target == null || !lookup.stability().isValid()) {
            lookup.stability().invalidate();
            return null;
        }
        return new GuardedDirectClosureCallTarget(targetClosure, target, lookup.stability());
    }

    private static final Object[] NO_SUPPLIED_ARGUMENTS = new Object[0];

    /**
     * Builds the ordinary prepared Closure invocation for an admitted direct
     * Closure-call hit (either cache tier below), without repeating
     * structured-protocol classification. PERF025 / PLAT040 F': the hit enters
     * the source target through the compact direct-Closure frame ABI; the
     * callee materializes exactly the activation the generic
     * {@link #prepareDirectClosureCall} would build (same captures, return
     * home and Task/dynamic-control inheritance), with its guest Context and
     * supplied guest Array deferred.
     */
    private static PreparedClosureCall finishDirectClosureCall(
            ProtosClosureValue closure,
            RootCallTarget target,
            Object[] supplied,
            ProtosActivation caller) {
        return PreparedClosureCall.ordinaryCompact(
                target,
                ProtosFrameArguments.compactDirectClosureCall(
                        closure, caller, null, supplied));
    }

    /**
     * PERF014 second tier: admits a stable direct Closure-call target keyed by
     * the closure's stable {@link CanonicalClosure} definition identity rather
     * than by receiver instance identity, mirroring
     * {@link PrepareSendArguments#fastOrdinarySend}. A distinct
     * {@code ProtosClosureValue} materialization of the same definition (for
     * example a fresh closure rebuilt on every loop iteration, or every
     * recursive call) reuses the same Context-owned target without
     * re-deriving it, while the authoritative D013 {@code call} selection is
     * still performed fresh on every hit (not cached), so a local override on
     * one particular instance is observed exactly as before. This is the
     * institution the identity-keyed first tier
     * ({@link #createGuardedDirectClosureCall}) alone omitted, which left
     * receiver-identity churn paying repeated specialization-establishment
     * cost instead of converging.
     */
    static ProtosClosureValue directClosureCallSelectionOrNull(
            Object receiver, ProtosActivation caller) {
        if (!(receiver instanceof ProtosClosureValue targetClosure)) {
            return null;
        }
        java.util.Optional<ProtosSlotLookupResult> selected;
        try {
            selected = ProtosValueLookup.lookup(
                    receiver, "call", caller.preludeOrNullForRuntime());
        } catch (UnsupportedOperationException unsupportedRepresentation) {
            return null;
        }
        if (selected.isEmpty() || targetClosure.nativeBody().isPresent()) {
            return null;
        }
        ProtosSlotLookupResult result = selected.orElseThrow();
        if (!ProtosStandardObjectProtocol.isCanonicalStandardCallSelection(
                result.value(), result.home())) {
            return null;
        }
        return targetClosure;
    }

    static CanonicalClosure directClosureCallDefinitionOrNull(ProtosClosureValue closure) {
        return closure == null ? null : closure.definition();
    }

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
                @Bind("currentEnteredContext()")
                        ProtosLanguageContext enteredContext,
                @Cached("receiver") Object cachedReceiver,
                @Cached("enteredContext") ProtosLanguageContext cachedContext,
                @Cached("createGuardedDirectClosureCall(receiver, caller, enteredContext)")
                        GuardedDirectClosureCallTarget cachedGuarded) {
            return finishDirectClosureCall(
                    cachedGuarded.closure(), cachedGuarded.target(), NO_SUPPLIED_ARGUMENTS, caller);
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
                @Bind("currentEnteredContext()")
                        ProtosLanguageContext enteredContext,
                @Cached("closureDefinition") CanonicalClosure cachedClosureDefinition,
                @Cached("enteredContext") ProtosLanguageContext cachedContext,
                @Cached("fastOrdinarySendTarget(closure, enteredContext)")
                        RootCallTarget cachedTarget) {
            return finishDirectClosureCall(closure, cachedTarget, NO_SUPPLIED_ARGUMENTS, caller);
        }

        @Specialization(replaces = {"guardedDirect", "fastDirect"})
        public static PreparedClosureCall perform(Object receiver, ProtosActivation caller) {
            return prepareClosureCall(receiver, List.of(), caller);
        }

        static ProtosLanguageContext currentEnteredContext() {
            return ProtosLanguageContext.currentIfEnteredForRuntime();
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
            return PrepareSendArguments.fastOrdinarySendTarget(closure, enteredContext);
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
                @Bind("currentEnteredContext()")
                        ProtosLanguageContext enteredContext,
                @Cached("receiver") Object cachedReceiver,
                @Cached("enteredContext") ProtosLanguageContext cachedContext,
                @Cached("createGuardedDirectClosureCall(receiver, caller, enteredContext)")
                        GuardedDirectClosureCallTarget cachedGuarded) {
            return finishDirectClosureCall(
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
                @Bind("currentEnteredContext()")
                        ProtosLanguageContext enteredContext,
                @Cached("closureDefinition") CanonicalClosure cachedClosureDefinition,
                @Cached("enteredContext") ProtosLanguageContext cachedContext,
                @Cached("fastOrdinarySendTarget(closure, enteredContext)")
                        RootCallTarget cachedTarget) {
            return finishDirectClosureCall(closure, cachedTarget, supplied, caller);
        }

        @Specialization(replaces = {"guardedDirect", "fastDirect"})
        public static PreparedClosureCall perform(
                Object receiver,
                ProtosActivation caller,
                @Variadic Object[] supplied) {
            return prepareClosureCall(
                    receiver,
                    List.of(supplied),
                    caller);
        }

        static ProtosLanguageContext currentEnteredContext() {
            return ProtosLanguageContext.currentIfEnteredForRuntime();
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
            return PrepareSendArguments.fastOrdinarySendTarget(closure, enteredContext);
        }
    }

    private static PreparedClosureCall prepareClosureCall(
            Object receiver,
            List<?> supplied,
            ProtosActivation caller) {
        ProtosPrelude prelude =
                caller.prelude()
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "polymorphic invocation requires an owning Core prelude"));
        ProtosSlotLookupResult selected;
        try {
            selected =
                    ProtosValueLookup.lookup(
                                    receiver,
                                    "call",
                                    prelude)
                            .orElseThrow(
                                    () ->
                                            new ProtosSignalException(
                                                    ProtosCoreErrors.newError(caller)));
        } catch (UnsupportedOperationException unsupportedRepresentation) {
            throw new ProtosSignalException(
                    ProtosCoreErrors.newError(caller));
        }

        if (!(selected.value() instanceof ProtosClosureValue callBehavior)) {
            throw new ProtosSignalException(
                    ProtosCoreErrors.newError(caller));
        }

        /*
         * PLAT017-A: D013 lookup has already selected one ordinary `call`
         * behavior. Only the exact canonical root Object.call implementation
         * may elide its native bridge for a Closure target. A nearer override,
         * including an alias of the standard behavior at another home, remains
         * an ordinary method invocation.
         */
        if (receiver instanceof ProtosClosureValue targetClosure
                && ProtosStandardObjectProtocol
                        .isCanonicalStandardCallSelection(
                                callBehavior,
                                selected.home())) {
            return prepareStandardClosureCallIntrinsic(
                    targetClosure,
                    supplied,
                    caller);
        }

        return prepareImmediateMethodCall(
                callBehavior,
                receiver,
                selected.home(),
                supplied,
                caller);
    }

    private static PreparedClosureCall prepareStandardClosureCallIntrinsic(
            ProtosClosureValue targetClosure,
            List<?> supplied,
            ProtosActivation caller) {
        rejectComposedInvocationProjection(
                targetClosure);
        ProtosActivation activation =
                ProtosActivation.forClosureInvocation(
                        targetClosure,
                        supplied,
                        caller.prelude().orElse(null),
                        caller.actorModuleState(),
                        caller.currentModuleKey().orElse(null),
                        caller.executionDomain());
        attachTaskOrInheritDynamicControlState(
                activation,
                caller);
        return finishPreparingComposedCallByImplementation(
                targetClosure,
                supplied,
                activation);
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
                @Bind("currentEnteredContext()")
                        ProtosLanguageContext enteredContext,
                @Cached("receiver") Object cachedReceiver,
                @Cached("enteredContext") ProtosLanguageContext cachedContext,
                @Cached("createGuardedDirectClosureCall(receiver, caller, enteredContext)")
                        GuardedDirectClosureCallTarget cachedGuarded) {
            return finishDirectClosureCall(
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
                @Bind("currentEnteredContext()")
                        ProtosLanguageContext enteredContext,
                @Cached("closureDefinition") CanonicalClosure cachedClosureDefinition,
                @Cached("enteredContext") ProtosLanguageContext cachedContext,
                @Cached("fastOrdinarySendTarget(closure, enteredContext)")
                        RootCallTarget cachedTarget) {
            return finishDirectClosureCall(closure, cachedTarget, supplied, caller);
        }

        @Specialization(replaces = {"guardedDirect", "fastDirect"})
        public static PreparedClosureCall perform(
                Object receiver,
                ProtosActivation caller,
                @Variadic Object[] supplied) {
            return prepareClosureCall(
                    receiver,
                    List.of(supplied),
                    caller);
        }

        static ProtosLanguageContext currentEnteredContext() {
            return ProtosLanguageContext.currentIfEnteredForRuntime();
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
            return PrepareSendArguments.fastOrdinarySendTarget(closure, enteredContext);
        }
    }

    /*
     * I072 Phase A guarded selection precedes the retained PERF010-A
     * prepared-target specialization. A stable ordinary receiver uses one
     * selector-specific assumption and never repeats lookup on a valid hit.
     * Unsupported chains and freshly rematerialized receivers can still use
     * the existing definition-based specialization described below.
     *
     * PERF010-A prepared-target specialization.
     *
     * <p>The generic ordinary-send path re-classifies every monomorphic hit
     * against the canonical standard-import native intrinsic and re-derives
     * the Context-owned Bytecode execution plan through
     * {@code sharedBytecodeExecutionPlans.computeIfAbsent(...)}. Both
     * operations are compiler-visible host machinery that a proven
     * non-native, source-backed ordinary Closure can never actually need
     * (see the retained PERF010-A causal evidence). {@code fastOrdinarySend}
     * re-runs authoritative D013 lookup on every hit, admits only an exact
     * cached selector/entered-Context match against the selected Closure's
     * stable executable identity, and reuses an effective Context-owned
     * activation target materialized once at cache-population time. Any
     * mismatch, native Closure, or unsupported case falls through to the
     * exact existing generic {@code perform} fallback.
     *
     * <p>The selected {@code ProtosClosureValue} and its {@code methodHome}
     * are fresh runtime objects on every invocation that re-executes the
     * Source producing them (for example a repeatedly re-materialized
     * Closure literal or a freshly constructed receiver): D013 selects the
     * same executable behavior every time, but the wrapper object and its
     * home differ by identity, so guarding on their identity previously
     * consumed a fresh cache entry per execution and, after {@code limit}
     * entries, permanently generalized to {@code perform} (see the retained
     * PERF010-A causal evidence). The cache key instead uses
     * {@link ProtosClosureValue#definition()}: the canonical, immutable
     * Closure definition that {@code MaterializeClosure} always rematerializes
     * from the exact same semantic AST node for one Closure literal, and
     * therefore stays identity-stable across fresh materializations of the
     * same executable behavior while remaining distinct whenever D013
     * genuinely selects a different Closure. The effective Context-owned
     * activation target depends only on that definition and the entered
     * Context (see {@link #fastOrdinarySendTarget}), never on the selected
     * Closure or methodHome instance, so this key remains exactly as
     * discriminating as the target it guards. The currently selected
     * {@code closure} and {@code methodHome} are still bound fresh on every
     * hit and flow into a fresh {@code ProtosActivation} exactly as before;
     * only the cache guard identity changes.
     */
    @Operation
    public static final class PrepareSendArguments {
        public record GuardedSendTarget(
                ProtosClosureValue closure,
                ProtosObjectValue methodHome,
                RootCallTarget target,
                Assumption stability) {}

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
         * prelude's Integer prototype; the entered Context is retained for the
         * same isolation rule as the other guarded sends.
         */
        public record GuardedIntegerSend(
                ProtosClosureValue closure,
                ProtosObjectValue methodHome,
                Assumption stability) {}

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
                @Bind("currentEnteredContext()")
                        ProtosLanguageContext enteredContext,
                @Bind("callerPrelude(caller)") ProtosPrelude prelude,
                @Cached("selector") String cachedSelector,
                @Cached("enteredContext") ProtosLanguageContext cachedContext,
                @Cached("prelude") ProtosPrelude cachedPrelude,
                @Cached("createGuardedIntegerSend(receiver, selector, prelude)")
                        GuardedIntegerSend cachedInteger) {
            return prepareDeferredImmediateNativeMethodCall(
                    cachedInteger.closure(),
                    receiver,
                    cachedInteger.methodHome(),
                    List.of(supplied),
                    caller);
        }

        /**
         * Resolves only while establishing a specialization. Admits a selected
         * native Closure only; a source-backed selection (for example a user
         * override) stays on the existing ordinary/generic paths.
         */
        static GuardedIntegerSend createGuardedIntegerSend(
                Object receiver,
                String selector,
                ProtosPrelude prelude) {
            if (currentEnteredContext() == null) {
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
            return new GuardedIntegerSend(
                    closure, selected.home(), lookup.stability());
        }

        static boolean isIntegerReceiver(Object receiver) {
            return ProtosValueLookup.isInteger(receiver);
        }

        static ProtosPrelude callerPrelude(ProtosActivation caller) {
            return caller.preludeOrNullForRuntime();
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
                @Bind("currentEnteredContext()")
                        ProtosLanguageContext enteredContext,
                @Cached("receiver") Object cachedReceiver,
                @Cached("selector") String cachedSelector,
                @Cached("enteredContext") ProtosLanguageContext cachedContext,
                @Cached("createGuardedSend(receiver, selector, caller, enteredContext)")
                        GuardedSendTarget cachedSend) {
            Object[] frameArguments =
                    ProtosFrameArguments.compactImmediateMethodCall(
                            cachedSend.closure(),
                            receiver,
                            cachedSend.methodHome(),
                            caller,
                            supplied);
            return PreparedClosureCall.ordinaryCompact(
                    cachedSend.target(),
                    frameArguments);
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
            if (enteredContext == null) {
                return null;
            }
            ProtosValueLookup.GuardedLookup lookup;
            try {
                lookup = ProtosValueLookup.lookupGuarded(
                        receiver, selector, caller.preludeOrNullForRuntime());
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
            return new GuardedSendTarget(
                    closure, lookup.selected().home(), target, lookup.stability());
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
                @Bind("currentEnteredContext()")
                        ProtosLanguageContext enteredContext,
                @Cached("selector") String cachedSelector,
                @Cached("closureDefinition") CanonicalClosure cachedClosureDefinition,
                @Cached("enteredContext") ProtosLanguageContext cachedContext,
                @Cached("fastOrdinarySendTarget(closure, enteredContext)")
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
                @Bind("currentEnteredContext()")
                        ProtosLanguageContext enteredContext,
                @Cached("receiver") Object cachedReceiver,
                @Cached("selector") String cachedSelector,
                @Cached("enteredContext") ProtosLanguageContext cachedContext,
                @Cached("createGuardedStructuredSend(receiver, selector, caller)")
                        GuardedStructuredSend cachedStructured) {
            List<?> suppliedList = List.of(supplied);
            rejectComposedInvocationProjection(cachedStructured.closure());
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
            attachTaskOrInheritDynamicControlState(activation, caller);
            return finishPreparingComposedCall(
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
                Object receiver,
                String selector,
                ProtosActivation caller) {
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
            } else if (ProtosStandardObjectProtocol.isCanonicalStandardWhileTrueSelection(
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
            return prepareSend(
                    receiver,
                    selector,
                    caller,
                    List.of(supplied));
        }

        /*
         * Bytecode DSL guard/@Bind expressions in this Operation are resolved
         * by the DSL processor's own expression parser, which only sees
         * members declared directly on this class (not private members of the
         * enclosing ProtosBytecodeRootNode, and not other top-level types by
         * simple name). These forwarders keep that resolution local while
         * delegating to the exact shared implementation, avoiding a duplicate
         * source of truth for lookup/error semantics.
         */

        static ProtosSlotLookupResult performOrdinarySendLookup(
                Object receiver,
                String selector,
                ProtosActivation caller) {
            return ProtosBytecodeRootNode.performOrdinarySendLookup(
                    receiver,
                    selector,
                    caller);
        }

        /**
         * Returns the non-native ordinary Closure selected by {@code selected},
         * or {@code null} when the selection is not an ordinary source-backed
         * Closure. Native Closures (including the canonical standard-import
         * intrinsic) always miss here and remain on the exact generic
         * {@code perform} path.
         */
        static ProtosClosureValue ordinarySendClosureOrNull(
                ProtosSlotLookupResult selected) {
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
        static CanonicalClosure ordinarySendClosureDefinitionOrNull(
                ProtosClosureValue closure) {
            return closure == null ? null : closure.definition();
        }

        static ProtosLanguageContext currentEnteredContext() {
            return ProtosLanguageContext.currentIfEnteredForRuntime();
        }

        /**
         * Materializes the effective Context-owned Bytecode activation target
         * for one cached fast-hit specialization instance.
         *
         * <p>This runs once, at cache-population time, and may use the
         * existing Context-owned-plan machinery
         * ({@link ProtosBytecodeRootNode#taskOwnedBytecodePlan}); it is not
         * called again on the resulting hot hit. Returns {@code null} when no
         * safe target can be produced for the current entered Context, which
         * keeps the fast specialization from being instantiated for this call
         * and leaves the exact generic path as the fallback.
         */
        static RootCallTarget fastOrdinarySendTarget(
                ProtosClosureValue closure,
                ProtosLanguageContext enteredContext) {
            if (currentEnteredContext() != enteredContext) {
                return null;
            }
            ProtosClosureExecutionPlan plan =
                    ProtosBytecodeRootNode.taskOwnedBytecodePlan(closure);
            if (plan == null || !plan.isBytecodeBackendForRuntime()) {
                return null;
            }
            return plan.bytecodeActivationTargetForComposition();
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
            return prepareSend(
                    receiver,
                    selector,
                    caller,
                    supplied.snapshot());
        }
    }

    private static PreparedClosureCall prepareSend(
            Object receiver,
            String selector,
            ProtosActivation caller,
            List<?> supplied) {
        ProtosSlotLookupResult selected =
                performOrdinarySendLookup(receiver, selector, caller);
        if (!(selected.value() instanceof ProtosClosureValue closure)) {
            throw new ProtosSignalException(
                    ProtosCoreErrors.newError(caller));
        }
        return prepareImmediateMethodCall(
                closure,
                receiver,
                selected.home(),
                supplied,
                caller);
    }

    /**
     * Performs the authoritative D013 ordinary-send lookup shared by the
     * generic {@link #prepareSend} path and the {@code PrepareSendArguments}
     * fast-hit specialization, preserving the exact existing lookup-failure
     * and unsupported-representation error semantics.
     */
    static ProtosSlotLookupResult performOrdinarySendLookup(
            Object receiver,
            String selector,
            ProtosActivation caller) {
        ProtosPrelude prelude =
                caller.preludeOrNullForRuntime();
        try {
            var selected =
                    ProtosValueLookup.lookup(
                            receiver,
                            selector,
                            prelude);
            if (selected.isEmpty()) {
                throw new ProtosSignalException(
                        ProtosCoreErrors.newSlotNotFound(caller));
            }
            return selected.orElseThrow();
        } catch (UnsupportedOperationException unsupportedRepresentation) {
            throw new ProtosSignalException(
                    ProtosCoreErrors.newError(caller));
        }
    }

    @Operation
    public static final class PrepareBufferedByteReaderTargetCall {
        @Specialization
        public static PreparedClosureCall perform(
                ProtosBufferedByteReaderCPrimeExecution.CallState state,
                ProtosActivation activation) {
            return prepareSend(
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
            return prepareSend(
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
            return prepareSend(
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
            return prepareSend(
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
            return prepareSend(
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
            return prepareSend(
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

    @Operation
    public static final class PrepareSuperSendArguments {
        @Specialization
        public static PreparedClosureCall perform(
                String selector,
                ProtosActivation caller,
                @Variadic Object[] supplied) {
            return prepareSuperSend(selector, caller, List.of(supplied));
        }
    }

    @Operation
    public static final class PrepareSuperSendVector {
        @Specialization
        public static PreparedClosureCall perform(
                String selector,
                ProtosActivation caller,
                PreparedArgumentVector supplied) {
            return prepareSuperSend(selector, caller, supplied.snapshot());
        }
    }

    private static PreparedClosureCall prepareSuperSend(
            String selector,
            ProtosActivation caller,
            List<?> supplied) {
        ProtosObjectValue methodHome =
                caller.methodHome()
                        .orElseThrow(
                                () ->
                                        new ProtosSignalException(
                                                ProtosCoreErrors.newInvalidSuper(caller)));
        Object lookupOrigin =
                methodHome.parent()
                        .orElseThrow(
                                () ->
                                        new ProtosSignalException(
                                                ProtosCoreErrors.newSlotNotFound(caller)));

        ProtosPrelude prelude = caller.prelude().orElse(null);
        ProtosSlotLookupResult selected;
        try {
            selected =
                    ProtosValueLookup.lookup(lookupOrigin, selector, prelude)
                            .orElseThrow(
                                    () ->
                                            new ProtosSignalException(
                                                    ProtosCoreErrors.newSlotNotFound(caller)));
        } catch (UnsupportedOperationException unsupportedRepresentation) {
            throw new ProtosSignalException(ProtosCoreErrors.newError(caller));
        }
        if (!(selected.value() instanceof ProtosClosureValue closure)) {
            throw new ProtosSignalException(ProtosCoreErrors.newError(caller));
        }

        return prepareImmediateMethodCall(
                closure,
                caller.receiver(),
                selected.home(),
                supplied,
                caller);
    }

    private static PreparedClosureCall prepareImmediateMethodCall(
            ProtosClosureValue closure,
            Object receiver,
            ProtosObjectValue methodHome,
            List<?> supplied,
            ProtosActivation caller) {
        return prepareImmediateMethodCall(
                closure, receiver, methodHome, supplied, caller, false);
    }

    /**
     * PERF027-A: the guarded Integer native send prepares the same selected
     * method invocation through the PLAT040 deferred activation. The fresh
     * invocation return home is established here exactly as the eager factory
     * would; the guest execution Context and supplied guest Array materialize
     * only if the native body, an Error path, or another observer requests them.
     */
    static PreparedClosureCall prepareDeferredImmediateNativeMethodCall(
            ProtosClosureValue closure,
            Object receiver,
            ProtosObjectValue methodHome,
            List<?> supplied,
            ProtosActivation caller) {
        return prepareImmediateMethodCall(
                closure, receiver, methodHome, supplied, caller, true);
    }

    private static PreparedClosureCall prepareImmediateMethodCall(
            ProtosClosureValue closure,
            Object receiver,
            ProtosObjectValue methodHome,
            List<?> supplied,
            ProtosActivation caller,
            boolean deferGuestInvocationState) {
        ProtosModuleRuntime standardImportRuntime =
                ProtosStandardImportProtocol.selectedRuntimeForBytecodeIntrinsic(
                        receiver,
                        closure,
                        methodHome,
                        caller.prelude().orElse(null));
        if (standardImportRuntime != null) {
            return PreparedClosureCall.moduleInitialization(
                    standardImportRuntime.prepareBytecodeImport(
                            supplied,
                            caller));
        }

        rejectComposedInvocationProjection(closure);
        ProtosActivation activation = deferGuestInvocationState
                ? ProtosActivation.forImmediateMethodInvocationWithReturnHomeForRuntime(
                        closure, supplied, receiver, methodHome, caller.prelude().orElse(null),
                        caller.actorModuleState(), caller.currentModuleKey().orElse(null),
                        caller.executionDomain(),
                        closure.returnHome().orElseGet(ProtosReturnHome::new))
                : ProtosActivation.forImmediateMethodInvocation(
                        closure, supplied, receiver, methodHome, caller.prelude().orElse(null),
                        caller.actorModuleState(), caller.currentModuleKey().orElse(null),
                        caller.executionDomain());
        attachTaskOrInheritDynamicControlState(
                activation,
                caller);
        return finishPreparingComposedCallByImplementation(
                closure,
                supplied,
                activation);
    }

    private static PreparedClosureCall prepareDirectClosureCall(
            ProtosClosureValue closure,
            ProtosActivation caller) {
        return prepareDirectClosureCall(closure, List.of(), caller);
    }

    private static PreparedClosureCall prepareDirectClosureCall(
            ProtosClosureValue closure,
            List<?> supplied,
            ProtosActivation caller) {
        rejectComposedInvocationProjection(closure);
        ProtosActivation activation =
                ProtosActivation.forClosureInvocation(
                        closure,
                        supplied,
                        caller.prelude().orElse(null),
                        caller.actorModuleState(),
                        caller.currentModuleKey().orElse(null),
                        caller.executionDomain());
        attachTaskOrInheritDynamicControlState(
                activation,
                caller);
        return finishPreparingComposedCallByImplementation(
                closure,
                supplied,
                activation);
    }

    private static void attachTaskOrInheritDynamicControlState(
            ProtosActivation activation,
            ProtosActivation caller) {
        if (caller.task().isPresent()) {
            activation.attachTask(
                    caller.task().orElseThrow());
        } else {
            activation.inheritDynamicControlState(caller);
        }
    }

    private static void rejectComposedInvocationProjection(
            ProtosClosureValue closure) {
        if (closure.requiresContextLocalExecutionProjectionForRuntime()
                && ProtosLanguageContext.currentIfEnteredForRuntime() == null) {
            throw new UnsupportedOperationException(
                    "Context-local Closure projection requires an entered Protos Context");
        }
    }

    static PreparedClosureCall prepareSynchronousSourceClosureForRuntime(
            ProtosClosureValue closure,
            List<?> supplied,
            ProtosActivation activation) {
        java.util.Objects.requireNonNull(closure, "closure");
        java.util.Objects.requireNonNull(supplied, "supplied");
        java.util.Objects.requireNonNull(activation, "activation");

        if (closure.nativeBody().isPresent()) {
            throw new IllegalArgumentException(
                    "synchronous source Closure preparation requires a non-native Closure");
        }
        if (activation.task().isPresent()) {
            throw new IllegalArgumentException(
                    "synchronous source Closure preparation cannot own a Task");
        }
        if (!ProtosPolyglotExecutionContext.hasEnteredContextForRuntime()
                || ProtosLanguageContext.currentIfEnteredForRuntime() == null) {
            throw new IllegalStateException(
                    "synchronous source Closure preparation requires an entered host Context");
        }

        ProtosClosureExecutionPlan template =
                closure.executionPlanForRuntimeInvocation();
        if (template.source().isEmpty()) {
            throw new UnsupportedOperationException(
                    "synchronous Bytecode Closure preparation requires exact source");
        }

        return finishPreparingComposedCallByImplementation(
                closure,
                supplied,
                activation);
    }

    private static PreparedClosureCall finishPreparingComposedCallByImplementation(
            ProtosClosureValue closure,
            List<?> supplied,
            ProtosActivation activation) {
        return finishPreparingComposedCall(
                closure,
                supplied,
                activation,
                ProtosStandardObjectProtocol.isStandardEnsureImplementation(closure),
                ProtosStandardErrorProtocol.isStandardHandleImplementation(closure),
                ProtosStandardObjectProtocol.isStandardWhileTrueImplementation(closure),
                ProtosStandardBooleanProtocol.structuredCallbackKindForImplementation(closure),
                ProtosStandardArrayProtocol.isStandardEachImplementation(closure),
                ProtosStandardBytesProtocol.isStandardEachImplementation(closure),
                ProtosStandardEnvironmentProtocol.isStandardEachImplementation(closure),
                ProtosStandardIdentityMapProtocol.isStandardEachImplementation(closure),
                ProtosStandardMapProtocol.isStandardEachImplementation(closure),
                ProtosStandardMapProtocol.structuredReadLookupKindForImplementation(closure),
                ProtosStandardMapProtocol.isStandardAtPutImplementation(closure),
                ProtosStandardMapProtocol.isStandardRemoveImplementation(closure),
                ProtosStandardErrorProtocol.isStandardSignalImplementation(closure),
                ProtosStandardObjectProtocol.isStandardCallImplementation(closure),
                ProtosStandardImportProtocol.runtimeForImplementation(closure));
    }

    private static PreparedClosureCall finishPreparingComposedCall(
            ProtosClosureValue closure,
            List<?> supplied,
            ProtosActivation activation,
            boolean structuredEnsure,
            boolean structuredErrorHandler,
            boolean structuredWhile,
            ProtosStandardBooleanProtocol.StructuredCallbackKind structuredBoolean,
            boolean structuredArrayEach,
            boolean structuredBytesEach,
            boolean structuredEnvironmentEach,
            boolean structuredIdentityMapEach,
            boolean structuredMapEach,
            ProtosStandardMapProtocol.StructuredReadLookupKind structuredMapReadLookup,
            boolean structuredMapAtPut,
            boolean structuredMapRemove,
            boolean directControlNative,
            boolean structuredObjectCall,
            ProtosModuleRuntime structuredImportRuntime) {
        if (closure.nativeBody().isPresent()) {
            ProtosNativeClosureBody nativeBody =
                    closure.nativeBody().orElseThrow();
            /*
             * PLAT019 keeps the ordinary non-suspending native path direct and
             * pay-only-when-used. Only bodies carrying the explicit suspension
             * capability enter executeForBytecodeContinuation(); ordinary native
             * bodies execute synchronously through PreparedClosureCall.enterNative().
             * PLAT021 structured-control provenance remains independently guarded.
             */
            return PreparedClosureCall.nativeCall(
                    nativeBody,
                    supplied,
                    activation,
                    structuredEnsure,
                    structuredErrorHandler,
                    structuredWhile,
                    structuredBoolean,
                    structuredArrayEach,
                    structuredBytesEach,
                    structuredEnvironmentEach,
                    structuredIdentityMapEach,
                    structuredMapEach,
                    structuredMapReadLookup,
                    structuredMapAtPut,
                    structuredMapRemove,
                    directControlNative,
                    structuredObjectCall,
                    structuredImportRuntime);
        }
        if (structuredEnsure
                || structuredErrorHandler
                || structuredWhile
                || structuredBoolean != null
                || structuredArrayEach
                || structuredBytesEach
                || structuredEnvironmentEach
                || structuredIdentityMapEach
                || structuredMapEach
                || structuredMapReadLookup != null
                || structuredMapAtPut
                || structuredMapRemove
                || directControlNative
                || structuredObjectCall
                || structuredImportRuntime != null) {
            throw new IllegalStateException(
                    "structured control capability requires the canonical native implementation");
        }
        ProtosClosureExecutionPlan plan = closure.executionPlanForRuntimeInvocation();
        ProtosLanguageContext enteredContext =
                ProtosLanguageContext.currentIfEnteredForRuntime();
        if (enteredContext != null) {
            boolean foreignContextPlan =
                    plan.language().orElseThrow()
                            != enteredContext.languageForRuntime();
            if (closure.requiresContextLocalExecutionProjectionForRuntime()
                    || foreignContextPlan) {
                if (plan.source().isEmpty()) {
                    throw new UnsupportedOperationException(
                            "Bytecode composition cannot project a source-less Closure plan");
                }
                plan =
                        enteredContext.bytecodeExecutionPlanForEnteredClosure(
                                closure,
                                plan);
            }
        }
        if (!plan.isBytecodeBackendForRuntime()) {
            throw new UnsupportedOperationException(
                    "C-prime composed invocation requires a Bytecode execution plan");
        }
        return PreparedClosureCall.ordinary(plan.bytecodeActivationTargetForComposition(), activation);
    }

    @Operation
    public static final class EnterClosureCall {
        @Specialization(guards = "prepared.isImmediate()")
        public static Object immediate(PreparedClosureCall prepared) {
            return prepared.enterImmediate();
        }

        @Specialization(guards = "prepared.isNative()")
        public static Object nativeCall(PreparedClosureCall prepared) {
            return prepared.enterNative();
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

        @Specialization(
                replaces = "direct",
                guards = {"!prepared.isImmediate()", "!prepared.isNative()"})
        public static Object indirect(
                PreparedClosureCall prepared,
                @Cached IndirectCallNode node) {
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
        @Specialization
        public static boolean perform(Object value) {
            /*
             * A native suspension is the leaf request. Yielding it from this
             * root materializes the real C-prime ContinuationResult; callers
             * then continue to see only ordinary nested ContinuationResults.
             */
            return value instanceof ContinuationResult
                    || value instanceof ProtosNativeSuspension
                    || value instanceof ProtosIoOperationSuspension
                    || value instanceof ProtosIoReleaseSuspension;
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
        @Specialization
        public static Object perform(
                PreparedClosureCall prepared,
                Object result) {
            return prepared.finish(result);
        }
    }
}
