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

import com.oracle.truffle.api.bytecode.BytecodeNode;
import com.oracle.truffle.api.bytecode.BytecodeRootNode;
import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosArrayValue;
import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosCoreErrors;
import com.guillermomolina.protos.runtime.ProtosDynamicControlState;
import com.guillermomolina.protos.runtime.ProtosIdentity;
import com.guillermomolina.protos.runtime.ProtosNonLocalReturnException;
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
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.frame.FrameDescriptor;
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
 * <p>This class is deliberately not a second Protos execution model. It is the
 * generated-interpreter root on which the current canonical frontend is migrated
 * incrementally. PERF006-B1 does not route normal source execution through it.</p>
 */
@GenerateBytecode(
        languageClass = ProtosLanguage.class,
        enableYield = true,
        enableTagInstrumentation = true,
        enableRootTagging = false,
        enableRootBodyTagging = false,
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
        if (exception instanceof ProtosSignalException transfer) {
            Object[] arguments = frame.getArguments();
            if (arguments.length > 0 && arguments[0] instanceof ProtosActivation activation) {
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
     * Bytecode equivalent of the existing ProtosLookupNode operation.
     *
     * <p>The activation is loaded from frame argument 0 by the lowerer, preserving
     * the established Protos root calling convention.</p>
     */
    @Operation
    public static final class BindClosureParameters {
        @Specialization
        public static void perform(
                ProtosActivation activation,
                CanonicalClosure definition) {
            ProtosBytecodeClosureExecutionPlan.bindParameters(
                    definition,
                    activation);
        }
    }

    @Operation
    public static final class HasClosureArgument {
        @Specialization
        public static boolean perform(
                ProtosActivation activation,
                int positionalIndex) {
            ProtosArrayValue arguments =
                    closureArguments(activation);
            return arguments.indexedSize()
                            .compareTo(
                                    BigInteger.valueOf(
                                            positionalIndex))
                    > 0;
        }
    }

    @Operation
    public static final class LoadClosureArgument {
        @Specialization
        public static Object perform(
                ProtosActivation activation,
                int positionalIndex) {
            ProtosArrayValue arguments =
                    closureArguments(activation);
            BigInteger index =
                    BigInteger.valueOf(positionalIndex);
            if (arguments.indexedSize().compareTo(index) <= 0) {
                throw closureArgumentCountError(activation);
            }
            return arguments.indexedAt(index);
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
            ProtosArrayValue arguments =
                    closureArguments(activation);
            List<Object> supplied =
                    arguments.indexedSnapshot();
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
            createClosureParameterSlot(
                    activation,
                    name,
                    prelude.newFrozenArray(
                            supplied.subList(
                                    restStart,
                                    supplied.size())));
        }
    }

    @Operation
    public static final class CheckClosureArgumentUpperBound {
        @Specialization
        public static void perform(
                ProtosActivation activation,
                int maximumPositionalArguments) {
            if (closureArguments(activation)
                            .indexedSize()
                            .compareTo(
                                    BigInteger.valueOf(
                                            maximumPositionalArguments))
                    > 0) {
                throw closureArgumentCountError(activation);
            }
        }
    }

    private static ProtosArrayValue closureArguments(
            ProtosActivation activation) {
        return activation.arguments()
                .orElseThrow(
                        () ->
                                new IllegalStateException(
                                        "parameter binding requires an invocation activation"));
    }

    private static void createClosureParameterSlot(
            ProtosActivation activation,
            String name,
            Object value) {
        try {
            activation.context()
                    .createLocalSlot(name, value);
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
            return activation.lookup(name)
                    .orElseThrow(
                            () ->
                                    new ProtosSignalException(
                                            ProtosCoreErrors.newUnqualifiedLookupError(
                                                    activation)));
        }
    }

    @Operation
    public static final class ResolveWritableLexicalContext {
        @Specialization
        public static ProtosObjectValue perform(
                ProtosActivation activation,
                String name) {
            return activation.writableLexicalContext(name)
                    .orElseThrow(
                            () ->
                                    new ProtosSignalException(
                                            ProtosCoreErrors.newSlotNotFound(activation)));
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
    public static final class LoadIntrinsic {
        @Specialization
        public static Object perform(
                ProtosActivation activation,
                CanonicalIntrinsic.Kind kind) {
            return switch (kind) {
                case THIS -> activation.receiver();
                case CONTEXT -> activation.context();
                case ARGS ->
                        activation.arguments()
                                .orElseThrow(
                                        () ->
                                                new IllegalStateException(
                                                        "args requires a Closure invocation activation"));
            };
        }
    }

    @Operation
    public static final class MaterializeClosure {
        @Specialization
        public static ProtosClosureValue perform(
                ProtosActivation activation,
                CanonicalClosure definition,
                ProtosClosureExecutionPlan executionPlan) {
            return new ProtosClosureValue(
                    definition,
                    activation.lexicalContextsForClosureCapture(),
                    activation.receiver(),
                    activation.methodHome().orElse(null),
                    activation.returnHome().orElse(null),
                    activation.prelude().orElse(null),
                    executionPlan);
        }
    }

    static final class PreparedObjectConstruction {
        private final RootCallTarget bodyTarget;
        private final ProtosActivation activation;
        private final ProtosObjectValue object;

        PreparedObjectConstruction(
                RootCallTarget bodyTarget,
                ProtosActivation activation,
                ProtosObjectValue object) {
            this.bodyTarget = java.util.Objects.requireNonNull(bodyTarget, "bodyTarget");
            this.activation = java.util.Objects.requireNonNull(activation, "activation");
            this.object = java.util.Objects.requireNonNull(object, "object");
        }

        RootCallTarget bodyTarget() {
            return bodyTarget;
        }

        ProtosActivation activation() {
            return activation;
        }

        ProtosObjectValue object() {
            return object;
        }
    }

    @Operation
    public static final class PrepareObjectConstruction {
        @Specialization
        public static PreparedObjectConstruction perform(
                ProtosActivation enclosing,
                Object parent,
                RootCallTarget bodyTarget) {
            ProtosObjectValue object = new ProtosObjectValue(parent);
            ProtosActivation construction =
                    ProtosActivation.forObjectConstruction(object, enclosing);
            return new PreparedObjectConstruction(bodyTarget, construction, object);
        }
    }

    @Operation
    public static final class EnterObjectConstruction {
        @Specialization(
                guards = "prepared.bodyTarget() == cachedTarget",
                limit = "3")
        public static Object direct(
                PreparedObjectConstruction prepared,
                @Cached("prepared.bodyTarget()") RootCallTarget cachedTarget,
                @Cached("create(cachedTarget)") DirectCallNode node) {
            try {
                return node.call(prepared.activation());
            } catch (ProtosBytecodeControlTransferException bridged) {
                throw bridged.transfer();
            }
        }

        @Specialization(replaces = "direct")
        public static Object indirect(
                PreparedObjectConstruction prepared,
                @Cached IndirectCallNode node) {
            try {
                return node.call(prepared.bodyTarget(), prepared.activation());
            } catch (ProtosBytecodeControlTransferException bridged) {
                throw bridged.transfer();
            }
        }
    }

    @Operation
    public static final class ResumeObjectConstruction {
        @Specialization(
                guards = "result.getContinuationRootNode() == cachedRoot",
                limit = "3")
        public static Object direct(
                @SuppressWarnings("unused") PreparedObjectConstruction prepared,
                ContinuationResult result,
                Object resumeValue,
                @Cached("result.getContinuationRootNode()") ContinuationRootNode cachedRoot,
                @Cached("create(cachedRoot.getCallTarget())") DirectCallNode node) {
            try {
                return node.call(result.getFrame(), resumeValue);
            } catch (ProtosBytecodeControlTransferException bridged) {
                throw bridged.transfer();
            }
        }

        @Specialization(replaces = "direct")
        public static Object indirect(
                @SuppressWarnings("unused") PreparedObjectConstruction prepared,
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
    }

    @Operation
    public static final class FinishObjectConstruction {
        @Specialization
        public static ProtosObjectValue perform(
                PreparedObjectConstruction prepared,
                @SuppressWarnings("unused") Object bodyResult) {
            return prepared.object();
        }
    }

    @Operation
    public static final class ComposeLocalSlots {
        @Specialization
        public static ProtosObjectValue perform(
                ProtosActivation activation,
                Object sourceValue,
                java.util.Set<String> reservedNames) {
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

    static final class PreparedClosureCall {
        private final RootCallTarget bodyTarget;
        private final ProtosNativeClosureBody nativeBody;
        private final List<?> supplied;
        private final ProtosActivation activation;
        private final ProtosReturnHome returnHome;
        private final boolean ownsReturnHome;
        private final boolean structuredEnsure;
        private final boolean structuredErrorHandler;
        private final boolean structuredWhile;
        private final boolean directControlNative;

        PreparedClosureCall(RootCallTarget bodyTarget, ProtosActivation activation) {
            this(
                    java.util.Objects.requireNonNull(bodyTarget, "bodyTarget"),
                    null,
                    List.of(),
                    activation,
                    false,
                    false,
                    false,
                    false);
        }

        private PreparedClosureCall(
                RootCallTarget bodyTarget,
                ProtosNativeClosureBody nativeBody,
                List<?> supplied,
                ProtosActivation activation,
                boolean structuredEnsure,
                boolean structuredErrorHandler,
                boolean structuredWhile,
                boolean directControlNative) {
            this.bodyTarget = bodyTarget;
            this.nativeBody = nativeBody;
            this.supplied = List.copyOf(supplied);
            this.activation = java.util.Objects.requireNonNull(activation, "activation");
            this.returnHome = activation.returnHome().orElseThrow(
                    () -> new IllegalStateException("Closure invocation requires a return home"));
            this.ownsReturnHome = activation.ownsReturnHome();
            this.structuredEnsure = structuredEnsure;
            this.structuredErrorHandler = structuredErrorHandler;
            this.structuredWhile = structuredWhile;
            this.directControlNative = directControlNative;
            int controlCapabilities =
                    (structuredEnsure ? 1 : 0)
                            + (structuredErrorHandler ? 1 : 0)
                            + (structuredWhile ? 1 : 0)
                            + (directControlNative ? 1 : 0);
            if (controlCapabilities > 1) {
                throw new IllegalArgumentException(
                        "prepared Closure call cannot own multiple structured-control capabilities");
            }
        }

        static PreparedClosureCall nativeCall(
                ProtosNativeClosureBody nativeBody,
                List<?> supplied,
                ProtosActivation activation,
                boolean structuredEnsure,
                boolean structuredErrorHandler,
                boolean structuredWhile,
                boolean directControlNative) {
            return new PreparedClosureCall(
                    null,
                    java.util.Objects.requireNonNull(nativeBody, "nativeBody"),
                    supplied,
                    activation,
                    structuredEnsure,
                    structuredErrorHandler,
                    structuredWhile,
                    directControlNative);
        }

        RootCallTarget bodyTarget() { return bodyTarget; }
        ProtosActivation activation() { return activation; }
        boolean isNative() { return nativeBody != null; }
        boolean isStructuredEnsure() { return structuredEnsure; }
        boolean isStructuredErrorHandler() { return structuredErrorHandler; }
        boolean isStructuredWhile() { return structuredWhile; }

        PreparedEnsureCall prepareStructuredEnsure() {
            if (!structuredEnsure) {
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

        PreparedWhileCall prepareStructuredWhile() {
            if (!structuredWhile) {
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

        PreparedErrorHandlerCall prepareStructuredErrorHandler() {
            if (!structuredErrorHandler) {
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

        Object enterNative() {
            if (nativeBody == null) {
                throw new IllegalStateException(
                        "prepared Closure call is not native");
            }
            if (structuredEnsure || structuredErrorHandler || structuredWhile) {
                throw new IllegalStateException(
                        "structured control native must execute through Bytecode control operations");
            }
            if (activation.task().isPresent()
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

        Object handleControlTransfer(ControlFlowException transfer) {
            if (transfer instanceof ProtosNonLocalReturnException nonLocalReturn
                    && ownsReturnHome
                    && returnHome.isActive()
                    && nonLocalReturn.target() == returnHome) {
                return nonLocalReturn.value();
            }
            throw transfer;
        }

        void complete() {
            if (ownsReturnHome && returnHome.isActive()) {
                returnHome.complete();
            }
        }

        Object finish(Object result) {
            complete();
            return result;
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

    @Operation
    public static final class RethrowTruffleException {
        @Specialization
        public static void perform(AbstractTruffleException exception) {
            throw exception;
        }
    }

    @Operation
    public static final class SupersedeCancellationUnwindIfActive {
        @Specialization
        public static void perform(ProtosActivation activation) {
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
            values.addAll(array.indexedSnapshot());
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

    @Operation
    public static final class PrepareClosureCall {
        @Specialization
        public static PreparedClosureCall perform(Object receiver, ProtosActivation caller) {
            return prepareClosureCall(receiver, List.of(), caller);
        }
    }

    @Operation
    public static final class PrepareClosureCallArguments {
        @Specialization
        public static PreparedClosureCall perform(
                Object receiver,
                ProtosActivation caller,
                @Variadic Object[] supplied) {
            return prepareClosureCall(
                    receiver,
                    List.of(supplied),
                    caller);
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
        return finishPreparingComposedCall(
                targetClosure,
                supplied,
                activation,
                ProtosStandardObjectProtocol.isStandardEnsureImplementation(targetClosure),
                ProtosStandardErrorProtocol.isStandardHandleImplementation(targetClosure),
                ProtosStandardObjectProtocol.isStandardWhileImplementation(targetClosure),
                ProtosStandardErrorProtocol.isStandardSignalImplementation(targetClosure));
    }

    @Operation
    public static final class PrepareDefaultClosureCallArguments {
        @Specialization
        public static PreparedClosureCall perform(
                Object receiver,
                ProtosActivation caller,
                @Variadic Object[] supplied) {
            return prepareClosureCall(
                    receiver,
                    List.of(supplied),
                    caller);
        }
    }

    @Operation
    public static final class PrepareSendArguments {
        @Specialization
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
        ProtosPrelude prelude =
                caller.prelude().orElse(null);
        ProtosSlotLookupResult selected;
        try {
            selected =
                    ProtosValueLookup.lookup(
                                    receiver,
                                    selector,
                                    prelude)
                            .orElseThrow(
                                    () ->
                                            new ProtosSignalException(
                                                    ProtosCoreErrors.newSlotNotFound(
                                                            caller)));
        } catch (UnsupportedOperationException unsupportedRepresentation) {
            throw new ProtosSignalException(
                    ProtosCoreErrors.newError(caller));
        }
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
        rejectComposedInvocationProjection(closure);
        ProtosActivation activation = ProtosActivation.forImmediateMethodInvocation(
                closure, supplied, receiver, methodHome, caller.prelude().orElse(null),
                caller.actorModuleState(), caller.currentModuleKey().orElse(null), caller.executionDomain());
        attachTaskOrInheritDynamicControlState(
                activation,
                caller);
        return finishPreparingComposedCall(
                closure,
                supplied,
                activation,
                ProtosStandardObjectProtocol.isCanonicalStandardEnsureSelection(
                        closure,
                        methodHome),
                ProtosStandardErrorProtocol.isCanonicalStandardHandleSelection(
                        closure,
                        methodHome,
                        caller),
                ProtosStandardObjectProtocol.isCanonicalStandardWhileSelection(
                        closure,
                        methodHome),
                ProtosStandardErrorProtocol.isCanonicalStandardSignalSelection(
                        closure,
                        methodHome,
                        caller));
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
        return finishPreparingComposedCall(
                closure,
                supplied,
                activation,
                false,
                false,
                false,
                false);
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
        if (closure.requiresContextLocalExecutionProjectionForRuntime()) {
            throw new UnsupportedOperationException(
                    "PERF006-B2 shared-Context Closure projection is not migrated yet");
        }
    }

    private static PreparedClosureCall finishPreparingComposedCall(
            ProtosClosureValue closure,
            List<?> supplied,
            ProtosActivation activation,
            boolean structuredEnsure,
            boolean structuredErrorHandler,
            boolean structuredWhile,
            boolean directControlNative) {
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
                    directControlNative);
        }
        if (structuredEnsure || structuredErrorHandler || structuredWhile || directControlNative) {
            throw new IllegalStateException(
                    "structured control capability requires the canonical native implementation");
        }
        ProtosClosureExecutionPlan plan = closure.executionPlanForRuntimeInvocation();
        if (!plan.isBytecodeBackendForRuntime()) {
            throw new UnsupportedOperationException(
                    "PERF006-B2 composed invocation receiver still has an AST execution plan");
        }
        return new PreparedClosureCall(plan.bytecodeActivationTargetForComposition(), activation);
    }

    @Operation
    public static final class EnterClosureCall {
        @Specialization(guards = "prepared.isNative()")
        public static Object nativeCall(PreparedClosureCall prepared) {
            return prepared.enterNative();
        }

        @Specialization(
                guards = {"!prepared.isNative()", "prepared.bodyTarget() == cachedTarget"},
                limit = "3")
        public static Object direct(
                PreparedClosureCall prepared,
                @Cached("prepared.bodyTarget()")
                        RootCallTarget cachedTarget,
                @Cached("create(cachedTarget)")
                        DirectCallNode node) {
            try {
                return node.call(prepared.activation());
            } catch (ProtosBytecodeControlTransferException bridged) {
                return prepared.handleControlTransfer(bridged.transfer());
            }
        }

        @Specialization(replaces = "direct")
        public static Object indirect(
                PreparedClosureCall prepared,
                @Cached IndirectCallNode node) {
            try {
                return node.call(
                        prepared.bodyTarget(),
                        prepared.activation());
            } catch (ProtosBytecodeControlTransferException bridged) {
                return prepared.handleControlTransfer(bridged.transfer());
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
                    || value instanceof ProtosNativeSuspension;
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
                        prepared.activation().task()
                                .orElseThrow(
                                        () ->
                                                new IllegalStateException(
                                                        "C-prime cancellation resume requires a task"));
                if (task.cancellationPhase()
                        != ProtosTask.CancellationPhase.UNWINDING) {
                    throw new IllegalStateException(
                            "C-prime cancellation resume requires UNWINDING phase");
                }
                throw cancellation;
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
