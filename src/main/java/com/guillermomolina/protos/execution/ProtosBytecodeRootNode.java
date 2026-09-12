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
import com.guillermomolina.protos.runtime.ProtosBytesValue;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosCoreErrors;
import com.guillermomolina.protos.runtime.ProtosDynamicControlState;
import com.guillermomolina.protos.runtime.ProtosEnvironmentValue;
import com.guillermomolina.protos.runtime.ProtosIdentity;
import com.guillermomolina.protos.runtime.ProtosIdentityMapValue;
import com.guillermomolina.protos.runtime.ProtosMapValue;
import com.guillermomolina.protos.runtime.ProtosNonLocalReturnException;
import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosProcessArgumentsValue;
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
    private static final Object MATCH_PATTERN_FAILED = new Object();
    private static final Object MATCH_GUARD_REJECTED = new Object();

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
        private final ProtosStandardBooleanProtocol.StructuredCallbackKind structuredBoolean;
        private final boolean structuredArrayEach;
        private final boolean structuredBytesEach;
        private final boolean structuredProcessArgumentsEach;
        private final boolean structuredEnvironmentEach;
        private final boolean structuredIdentityMapEach;
        private final boolean structuredMapEach;
        private final ProtosStandardMapProtocol.StructuredReadLookupKind structuredMapReadLookup;
        private final boolean structuredMapAtPut;
        private final boolean structuredMapRemove;
        private final boolean directControlNative;
        private final ProtosModuleRuntime.PreparedModuleInitialization moduleInitialization;

        PreparedClosureCall(RootCallTarget bodyTarget, ProtosActivation activation) {
            this(
                    java.util.Objects.requireNonNull(bodyTarget, "bodyTarget"),
                    null,
                    List.of(),
                    activation,
                    false,
                    false,
                    false,
                    null,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    null,
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
                ProtosStandardBooleanProtocol.StructuredCallbackKind structuredBoolean,
                boolean structuredArrayEach,
                boolean structuredBytesEach,
                boolean structuredProcessArgumentsEach,
                boolean structuredEnvironmentEach,
                boolean structuredIdentityMapEach,
                boolean structuredMapEach,
                ProtosStandardMapProtocol.StructuredReadLookupKind structuredMapReadLookup,
                boolean structuredMapAtPut,
                boolean structuredMapRemove,
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
            this.structuredBoolean = structuredBoolean;
            this.structuredArrayEach = structuredArrayEach;
            this.structuredBytesEach = structuredBytesEach;
            this.structuredProcessArgumentsEach = structuredProcessArgumentsEach;
            this.structuredEnvironmentEach = structuredEnvironmentEach;
            this.structuredIdentityMapEach = structuredIdentityMapEach;
            this.structuredMapEach = structuredMapEach;
            this.structuredMapReadLookup = structuredMapReadLookup;
            this.structuredMapAtPut = structuredMapAtPut;
            this.structuredMapRemove = structuredMapRemove;
            this.directControlNative = directControlNative;
            this.moduleInitialization = null;
            int controlCapabilities =
                    (structuredEnsure ? 1 : 0)
                            + (structuredErrorHandler ? 1 : 0)
                            + (structuredWhile ? 1 : 0)
                            + (structuredBoolean != null ? 1 : 0)
                            + (structuredArrayEach ? 1 : 0)
                            + (structuredBytesEach ? 1 : 0)
                            + (structuredProcessArgumentsEach ? 1 : 0)
                            + (structuredEnvironmentEach ? 1 : 0)
                            + (structuredIdentityMapEach ? 1 : 0)
                            + (structuredMapEach ? 1 : 0)
                            + (structuredMapReadLookup != null ? 1 : 0)
                            + (structuredMapAtPut ? 1 : 0)
                            + (structuredMapRemove ? 1 : 0)
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
                ProtosStandardBooleanProtocol.StructuredCallbackKind structuredBoolean,
                boolean structuredArrayEach,
                boolean structuredBytesEach,
                boolean structuredProcessArgumentsEach,
                boolean structuredEnvironmentEach,
                boolean structuredIdentityMapEach,
                boolean structuredMapEach,
                ProtosStandardMapProtocol.StructuredReadLookupKind structuredMapReadLookup,
                boolean structuredMapAtPut,
                boolean structuredMapRemove,
                boolean directControlNative) {
            return new PreparedClosureCall(
                    null,
                    java.util.Objects.requireNonNull(nativeBody, "nativeBody"),
                    supplied,
                    activation,
                    structuredEnsure,
                    structuredErrorHandler,
                    structuredWhile,
                    structuredBoolean,
                    structuredArrayEach,
                    structuredBytesEach,
                    structuredProcessArgumentsEach,
                    structuredEnvironmentEach,
                    structuredIdentityMapEach,
                    structuredMapEach,
                    structuredMapReadLookup,
                    structuredMapAtPut,
                    structuredMapRemove,
                    directControlNative);
        }

        private PreparedClosureCall(
                ProtosModuleRuntime.PreparedModuleInitialization moduleInitialization) {
            this.bodyTarget = moduleInitialization.bodyTarget();
            this.nativeBody = null;
            this.supplied = List.of();
            this.activation = moduleInitialization.activation();
            this.returnHome = null;
            this.ownsReturnHome = false;
            this.structuredEnsure = false;
            this.structuredErrorHandler = false;
            this.structuredWhile = false;
            this.structuredBoolean = null;
            this.structuredArrayEach = false;
            this.structuredBytesEach = false;
            this.structuredProcessArgumentsEach = false;
            this.structuredEnvironmentEach = false;
            this.structuredIdentityMapEach = false;
            this.structuredMapEach = false;
            this.structuredMapReadLookup = null;
            this.structuredMapAtPut = false;
            this.structuredMapRemove = false;
            this.directControlNative = false;
            this.moduleInitialization =
                    java.util.Objects.requireNonNull(
                            moduleInitialization,
                            "moduleInitialization");
        }

        static PreparedClosureCall moduleInitialization(
                ProtosModuleRuntime.PreparedModuleInitialization moduleInitialization) {
            return new PreparedClosureCall(moduleInitialization);
        }

        RootCallTarget bodyTarget() { return bodyTarget; }
        ProtosActivation activation() { return activation; }
        boolean isNative() { return nativeBody != null; }
        boolean isImmediate() {
            return moduleInitialization != null && moduleInitialization.isImmediate();
        }
        Object enterImmediate() {
            if (!isImmediate()) {
                throw new IllegalStateException("prepared call is not an immediate module hit");
            }
            return moduleInitialization.immediateResult();
        }
        boolean isStructuredEnsure() { return structuredEnsure; }
        boolean isStructuredErrorHandler() { return structuredErrorHandler; }
        boolean isStructuredWhile() { return structuredWhile; }
        boolean isStructuredBoolean() { return structuredBoolean != null; }
        boolean isStructuredArrayEach() { return structuredArrayEach; }
        boolean isStructuredBytesEach() { return structuredBytesEach; }
        boolean isStructuredProcessArgumentsEach() { return structuredProcessArgumentsEach; }
        boolean isStructuredEnvironmentEach() { return structuredEnvironmentEach; }
        boolean isStructuredIdentityMapEach() { return structuredIdentityMapEach; }
        boolean isStructuredMapEach() { return structuredMapEach; }
        boolean isStructuredMapReadLookup() { return structuredMapReadLookup != null; }
        boolean isStructuredMapAtPut() { return structuredMapAtPut; }
        boolean isStructuredMapRemove() { return structuredMapRemove; }

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

        PreparedBooleanCall prepareStructuredBoolean() {
            if (structuredBoolean == null) {
                throw new IllegalStateException(
                        "prepared Closure call has no structured Boolean callback capability");
            }
            return new PreparedBooleanCall(
                    structuredBoolean,
                    activation.receiver(),
                    supplied,
                    activation);
        }

        PreparedArrayEachCall prepareStructuredArrayEach() {
            if (!structuredArrayEach) {
                throw new IllegalStateException(
                        "prepared Closure call has no structured Array.each capability");
            }
            return new PreparedArrayEachCall(
                    activation.receiver(),
                    supplied,
                    activation);
        }

        PreparedBytesEachCall prepareStructuredBytesEach() {
            if (!structuredBytesEach) {
                throw new IllegalStateException(
                        "prepared Closure call has no structured Bytes.each capability");
            }
            return new PreparedBytesEachCall(
                    activation.receiver(),
                    supplied,
                    activation);
        }

        PreparedProcessArgumentsEachCall prepareStructuredProcessArgumentsEach() {
            if (!structuredProcessArgumentsEach) {
                throw new IllegalStateException(
                        "prepared Closure call has no structured ProcessArguments.each capability");
            }
            return new PreparedProcessArgumentsEachCall(
                    activation.receiver(),
                    supplied,
                    activation);
        }

        PreparedEnvironmentEachCall prepareStructuredEnvironmentEach() {
            if (!structuredEnvironmentEach) {
                throw new IllegalStateException(
                        "prepared Closure call has no structured Environment.each capability");
            }
            return new PreparedEnvironmentEachCall(
                    activation.receiver(),
                    supplied,
                    activation);
        }

        PreparedIdentityMapEachCall prepareStructuredIdentityMapEach() {
            if (!structuredIdentityMapEach) {
                throw new IllegalStateException(
                        "prepared Closure call has no structured IdentityMap.each capability");
            }
            return new PreparedIdentityMapEachCall(
                    activation.receiver(),
                    supplied,
                    activation);
        }

        PreparedMapEachCall prepareStructuredMapEach() {
            if (!structuredMapEach) {
                throw new IllegalStateException(
                        "prepared Closure call has no structured Map.each capability");
            }
            return new PreparedMapEachCall(
                    activation.receiver(),
                    supplied,
                    activation);
        }

        PreparedMapReadLookupCall prepareStructuredMapReadLookup() {
            if (structuredMapReadLookup == null) {
                throw new IllegalStateException(
                        "prepared Closure call has no structured Map read-lookup capability");
            }
            return new PreparedMapReadLookupCall(
                    structuredMapReadLookup,
                    activation.receiver(),
                    supplied,
                    activation);
        }

        PreparedMapAtPutCall prepareStructuredMapAtPut() {
            if (!structuredMapAtPut) {
                throw new IllegalStateException(
                        "prepared Closure call has no structured Map.atPut capability");
            }
            return new PreparedMapAtPutCall(
                    activation.receiver(),
                    supplied,
                    activation);
        }

        PreparedMapRemoveCall prepareStructuredMapRemove() {
            if (!structuredMapRemove) {
                throw new IllegalStateException(
                        "prepared Closure call has no structured Map.remove capability");
            }
            return new PreparedMapRemoveCall(
                    activation.receiver(),
                    supplied,
                    activation);
        }

        Object enterNative() {
            if (nativeBody == null) {
                throw new IllegalStateException(
                        "prepared Closure call is not native");
            }
            if (structuredEnsure
                    || structuredErrorHandler
                    || structuredWhile
                    || structuredBoolean != null
                    || structuredArrayEach
                    || structuredBytesEach
                    || structuredProcessArgumentsEach
                    || structuredEnvironmentEach
                    || structuredIdentityMapEach
                    || structuredMapEach
                    || structuredMapReadLookup != null
                    || structuredMapAtPut
                    || structuredMapRemove) {
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
            if (moduleInitialization != null) {
                moduleInitialization.fail();
                throw transfer;
            }
            if (transfer instanceof ProtosNonLocalReturnException nonLocalReturn
                    && ownsReturnHome
                    && returnHome.isActive()
                    && nonLocalReturn.target() == returnHome) {
                return nonLocalReturn.value();
            }
            throw transfer;
        }

        void failIfModuleInitialization() {
            if (moduleInitialization != null) {
                moduleInitialization.fail();
            }
        }

        RuntimeException mapRuntimeFailure(RuntimeException failure) {
            if (moduleInitialization == null) {
                return failure;
            }
            return moduleInitialization.mapUnexpectedHostFailure(failure);
        }

        void complete() {
            if (moduleInitialization != null) {
                return;
            }
            if (ownsReturnHome && returnHome.isActive()) {
                returnHome.complete();
            }
        }

        Object finish(Object result) {
            if (moduleInitialization != null) {
                return moduleInitialization.finish(result);
            }
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
            Object selected =
                    switch (kind) {
                        case IF_TRUE, IF_FALSE, AND, OR -> supplied.get(0);
                        case IF_TRUE_IF_FALSE ->
                                receiver == ProtosBooleanValue.TRUE
                                        ? supplied.get(0)
                                        : supplied.get(1);
                    };
            return prepareClosureCall(selected, List.of(), activation);
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

    static final class PreparedArrayEachCall {
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

        boolean hasNext() {
            return index < snapshot.size();
        }

        PreparedClosureCall prepareCurrent() {
            if (!hasNext()) {
                throw new IllegalStateException(
                        "Array.each callback requested after snapshot exhaustion");
            }
            return prepareClosureCall(
                    block,
                    List.of(snapshot.get(index)),
                    activation);
        }

        void advance() {
            if (!hasNext()) {
                throw new IllegalStateException(
                        "Array.each cursor advanced after snapshot exhaustion");
            }
            index++;
        }

        Object finish() {
            if (hasNext()) {
                throw new IllegalStateException(
                        "Array.each finished before snapshot exhaustion");
            }
            return array;
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

    static final class PreparedBytesEachCall {
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

        boolean hasNext() {
            return index < snapshot.size();
        }

        PreparedClosureCall prepareCurrent() {
            if (!hasNext()) {
                throw new IllegalStateException(
                        "Bytes.each callback requested after snapshot exhaustion");
            }
            return prepareClosureCall(
                    block,
                    List.of(snapshot.get(index)),
                    activation);
        }

        void advance() {
            if (!hasNext()) {
                throw new IllegalStateException(
                        "Bytes.each cursor advanced after snapshot exhaustion");
            }
            index++;
        }

        Object finish() {
            if (hasNext()) {
                throw new IllegalStateException(
                        "Bytes.each finished before snapshot exhaustion");
            }
            return bytes;
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

    static final class PreparedProcessArgumentsEachCall {
        private final ProtosProcessArgumentsValue arguments;
        private final List<?> snapshot;
        private final Object block;
        private final ProtosActivation activation;
        private int index;

        PreparedProcessArgumentsEachCall(
                Object receiver,
                List<?> supplied,
                ProtosActivation activation) {
            this.activation = java.util.Objects.requireNonNull(activation, "activation");
            if (!(receiver instanceof ProtosProcessArgumentsValue value)
                    || supplied.size() != 1) {
                throw ProtosCoreErrors.signal(
                        activation,
                        ProtosCoreErrors.newError(activation));
            }
            this.arguments = value;
            this.block = supplied.get(0);
            ProtosStandardProcessArgumentsProtocol.requireInvokableForStructured(
                    block,
                    activation);
            this.snapshot = List.copyOf(value.valuesForRuntime());
        }

        boolean hasNext() {
            return index < snapshot.size();
        }

        PreparedClosureCall prepareCurrent() {
            if (!hasNext()) {
                throw new IllegalStateException(
                        "ProcessArguments.each callback requested after snapshot exhaustion");
            }
            return prepareClosureCall(
                    block,
                    List.of(snapshot.get(index)),
                    activation);
        }

        void advance() {
            if (!hasNext()) {
                throw new IllegalStateException(
                        "ProcessArguments.each cursor advanced after snapshot exhaustion");
            }
            index++;
        }

        Object finish() {
            if (hasNext()) {
                throw new IllegalStateException(
                        "ProcessArguments.each finished before snapshot exhaustion");
            }
            return arguments;
        }
    }

    @Operation
    public static final class IsStructuredProcessArgumentsEachCall {
        @Specialization
        public static boolean perform(PreparedClosureCall prepared) {
            return prepared.isStructuredProcessArgumentsEach();
        }
    }

    @Operation
    public static final class PrepareStructuredProcessArgumentsEachCall {
        @Specialization
        public static PreparedProcessArgumentsEachCall perform(PreparedClosureCall prepared) {
            return prepared.prepareStructuredProcessArgumentsEach();
        }
    }

    @Operation
    public static final class StructuredProcessArgumentsEachHasNext {
        @Specialization
        public static boolean perform(PreparedProcessArgumentsEachCall prepared) {
            return prepared.hasNext();
        }
    }

    @Operation
    public static final class PrepareStructuredProcessArgumentsEachElementCall {
        @Specialization
        public static PreparedClosureCall perform(PreparedProcessArgumentsEachCall prepared) {
            return prepared.prepareCurrent();
        }
    }

    @Operation
    public static final class AdvanceStructuredProcessArgumentsEach {
        @Specialization
        public static void perform(PreparedProcessArgumentsEachCall prepared) {
            prepared.advance();
        }
    }

    @Operation
    public static final class FinishStructuredProcessArgumentsEach {
        @Specialization
        public static Object perform(PreparedProcessArgumentsEachCall prepared) {
            return prepared.finish();
        }
    }

    static final class PreparedEnvironmentEachCall {
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

        boolean hasNext() {
            return index < snapshot.size();
        }

        PreparedClosureCall prepareCurrent() {
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

        void advance() {
            if (!hasNext()) {
                throw new IllegalStateException(
                        "Environment.each cursor advanced after snapshot exhaustion");
            }
            index++;
        }

        Object finish() {
            if (hasNext()) {
                throw new IllegalStateException(
                        "Environment.each finished before snapshot exhaustion");
            }
            return environment;
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


    static final class PreparedIdentityMapEachCall {
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

        boolean hasNext() {
            return index < snapshot.size();
        }

        PreparedClosureCall prepareCurrent() {
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

        void advance() {
            if (!hasNext()) {
                throw new IllegalStateException(
                        "IdentityMap.each cursor advanced after snapshot exhaustion");
            }
            index++;
        }

        Object finish() {
            if (hasNext()) {
                throw new IllegalStateException(
                        "IdentityMap.each finished before snapshot exhaustion");
            }
            return identityMap;
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



    static final class PreparedMapReadLookupCall {
        private final ProtosStandardMapProtocol.StructuredReadLookupKind kind;
        private final ProtosMapValue map;
        private final Object key;
        private final ProtosActivation activation;
        private BigInteger queryHash;
        private List<ProtosStandardMapProtocol.StableAssociation> snapshot;
        private int index;
        private ProtosStandardMapProtocol.StableAssociation match;
        private boolean hashAccepted;
        private boolean comparisonEntered;

        PreparedMapReadLookupCall(
                ProtosStandardMapProtocol.StructuredReadLookupKind kind,
                Object receiver,
                List<?> supplied,
                ProtosActivation activation) {
            this.kind = java.util.Objects.requireNonNull(kind, "kind");
            this.activation = java.util.Objects.requireNonNull(activation, "activation");
            if (!(receiver instanceof ProtosMapValue value)
                    || supplied.size() != 1) {
                throw ProtosCoreErrors.signal(
                        activation,
                        ProtosCoreErrors.newError(activation));
            }
            this.map = value;
            this.key = supplied.get(0);
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

        Object finish() {
            requireHashAccepted();
            if (comparisonEntered) {
                throw new IllegalStateException(
                        "Map read lookup finished with an active comparison scope");
            }
            if (needsEquality()) {
                throw new IllegalStateException(
                        "Map read lookup finished before candidate exhaustion");
            }
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
            };
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
    public static final class FinishStructuredMapReadLookup {
        @Specialization
        public static Object perform(PreparedMapReadLookupCall prepared) {
            return prepared.finish();
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

    static final class PreparedMapEachCall {
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

        boolean hasNext() {
            return index < snapshot.size();
        }

        PreparedClosureCall prepareCurrent() {
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

        void advance() {
            if (!hasNext()) {
                throw new IllegalStateException(
                        "Map.each cursor advanced after snapshot exhaustion");
            }
            index++;
        }

        Object finish() {
            if (hasNext()) {
                throw new IllegalStateException(
                        "Map.each finished before snapshot exhaustion");
            }
            return map;
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
            values.addAll(array.indexedSnapshot());
        }

        List<Object> snapshot() {
            return List.copyOf(values);
        }
    }



    /**
     * Inert D084 Array-pattern attempt state. The carrier snapshots ordinary
     * element references once before child matching and may materialize the one
     * fresh frozen remainder Array. It never invokes guest code.
     */
    static final class PreparedArrayMatchAttempt {
        private final List<Object> observed;
        private final int prefixCount;
        private final int suffixCount;
        private final ProtosArrayValue remainder;

        PreparedArrayMatchAttempt(
                List<Object> observed,
                int prefixCount,
                int suffixCount,
                ProtosArrayValue remainder) {
            this.observed = List.copyOf(observed);
            this.prefixCount = prefixCount;
            this.suffixCount = suffixCount;
            this.remainder = remainder;
        }

        Object childValue(int section, int index) {
            return switch (section) {
                case 0 -> observed.get(index);
                case 1 -> {
                    if (index != 0 || remainder == null) {
                        throw new IllegalStateException(
                                "Array match requested an unavailable remainder child");
                    }
                    yield remainder;
                }
                case 2 -> observed.get(observed.size() - suffixCount + index);
                default -> throw new IllegalArgumentException(
                        "unknown Array match child section: " + section);
            };
        }
    }

    @Operation
    public static final class PrepareArrayMatchAttempt {
        @Specialization
        public static Object perform(
                Object subject,
                int prefixCount,
                boolean hasRemainder,
                boolean materializeRemainder,
                int suffixCount,
                ProtosActivation activation) {
            if (!(subject instanceof ProtosArrayValue array)) {
                return MATCH_PATTERN_FAILED;
            }

            List<Object> observed = array.indexedSnapshot();
            int fixedCount = prefixCount + suffixCount;
            if (hasRemainder) {
                if (observed.size() < fixedCount) {
                    return MATCH_PATTERN_FAILED;
                }
            } else if (observed.size() != fixedCount) {
                return MATCH_PATTERN_FAILED;
            }

            ProtosArrayValue remainder = null;
            if (materializeRemainder) {
                int remainderEnd = observed.size() - suffixCount;
                remainder =
                        activation.prelude()
                                .orElseThrow(
                                        () ->
                                                new IllegalStateException(
                                                        "Array matching requires an owning Core prelude"))
                                .newFrozenArray(
                                        observed.subList(prefixCount, remainderEnd));
            }
            return new PreparedArrayMatchAttempt(
                    observed,
                    prefixCount,
                    suffixCount,
                    remainder);
        }
    }

    @Operation
    public static final class ArrayMatchAttemptSucceeded {
        @Specialization
        public static boolean perform(Object attempt) {
            if (attempt == MATCH_PATTERN_FAILED) {
                return false;
            }
            if (attempt instanceof PreparedArrayMatchAttempt) {
                return true;
            }
            throw new IllegalStateException(
                    "Array match attempt produced an invalid carrier");
        }
    }

    @Operation
    public static final class ArrayMatchChildValue {
        @Specialization
        public static Object perform(
                Object attempt,
                int section,
                int index) {
            if (!(attempt instanceof PreparedArrayMatchAttempt prepared)) {
                throw new IllegalStateException(
                        "Array match child requested from a failed/invalid attempt");
            }
            return prepared.childValue(section, index);
        }
    }

    @Operation
    public static final class MergeMatchCaptures {
        @Specialization
        public static Object perform(
                Object aggregate,
                Object child) {
            if (child == MATCH_PATTERN_FAILED) {
                return MATCH_PATTERN_FAILED;
            }
            if (!(aggregate instanceof PreparedArgumentVector target)
                    || !(child instanceof PreparedArgumentVector additions)) {
                throw new IllegalStateException(
                        "Array match capture merge received an invalid carrier");
            }
            for (Object value : additions.snapshot()) {
                target.append(value);
            }
            return target;
        }
    }

    @Operation
    public static final class DecodeMatchOutcome {
        @Specialization
        public static Object perform(
                Object outcome,
                ProtosActivation activation) {
            if (outcome == ProtosBooleanValue.FALSE) {
                return MATCH_PATTERN_FAILED;
            }
            if (outcome == ProtosBooleanValue.TRUE) {
                return new PreparedArgumentVector();
            }
            if (outcome instanceof ProtosArrayValue captures) {
                List<Object> observed = captures.indexedSnapshot();
                if (observed.isEmpty()) {
                    throw new ProtosSignalException(
                            ProtosCoreErrors.newError(activation));
                }
                PreparedArgumentVector vector = new PreparedArgumentVector();
                for (Object value : observed) {
                    vector.append(value);
                }
                return vector;
            }
            throw new ProtosSignalException(
                    ProtosCoreErrors.newError(activation));
        }
    }

    @Operation
    public static final class PrefixMatchAlias {
        @Specialization
        public static Object perform(
                Object subject,
                Object nested) {
            if (nested == MATCH_PATTERN_FAILED) {
                return MATCH_PATTERN_FAILED;
            }
            if (!(nested instanceof PreparedArgumentVector captures)) {
                throw new IllegalStateException(
                        "match alias received an invalid capture carrier");
            }
            PreparedArgumentVector prefixed = new PreparedArgumentVector();
            prefixed.append(subject);
            for (Object value : captures.snapshot()) {
                prefixed.append(value);
            }
            return prefixed;
        }
    }

    @Operation
    public static final class MatchAttemptSucceeded {
        @Specialization
        public static boolean perform(Object attempt) {
            if (attempt == MATCH_PATTERN_FAILED) {
                return false;
            }
            if (attempt instanceof PreparedArgumentVector) {
                return true;
            }
            throw new IllegalStateException(
                    "match attempt produced an invalid capture carrier");
        }
    }

    @Operation
    public static final class MatchAttemptFailed {
        @Specialization
        public static boolean perform(Object attempt) {
            if (attempt == MATCH_PATTERN_FAILED) {
                return true;
            }
            if (attempt instanceof PreparedArgumentVector) {
                return false;
            }
            throw new IllegalStateException(
                    "match attempt produced an invalid capture carrier");
        }
    }

    @Operation
    public static final class MatchStillOpen {
        @Specialization
        public static boolean perform(Object completed) {
            if (completed == Boolean.FALSE) {
                return true;
            }
            if (completed == Boolean.TRUE) {
                return false;
            }
            throw new IllegalStateException(
                    "match completion state is not boolean");
        }
    }

    @Operation
    public static final class MatchGuardCondition {
        @Specialization
        public static boolean perform(
                Object result,
                ProtosActivation activation) {
            if (result == ProtosBooleanValue.TRUE) {
                return true;
            }
            if (result == ProtosBooleanValue.FALSE) {
                return false;
            }
            throw new ProtosSignalException(
                    ProtosCoreErrors.newError(activation));
        }
    }

    @Operation
    public static final class MatchGuardRejected {
        @Specialization
        public static Object perform() {
            return MATCH_GUARD_REJECTED;
        }
    }

    @Operation
    public static final class IsMatchGuardRejected {
        @Specialization
        public static boolean perform(Object result) {
            return result == MATCH_GUARD_REJECTED;
        }
    }

    @Operation
    public static final class FinishMatch {
        @Specialization
        public static Object perform(
                Object completed,
                Object result,
                ProtosActivation activation) {
            if (completed == Boolean.TRUE) {
                return result;
            }
            if (completed == Boolean.FALSE) {
                throw new ProtosSignalException(
                        ProtosCoreErrors.newError(activation));
            }
            throw new IllegalStateException(
                    "match completion state is not boolean");
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
        ProtosActivation activation =
                ProtosActivation.forClosureInvocation(
                        closure,
                        supplied,
                        creator.prelude().orElse(null),
                        creator.actorModuleState(),
                        creator.currentModuleKey().orElse(null),
                        creator.executionDomain());
        activation.attachTask(task);
        return new PreparedClosureCall(
                plan.bytecodeActivationTargetForComposition(),
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
        return new PreparedClosureCall(
                plan.bytecodeActivationTargetForComposition(),
                activation);
    }

    private static ProtosClosureExecutionPlan taskOwnedBytecodePlan(
            ProtosClosureValue closure) {
        ProtosClosureExecutionPlan template =
                closure.executionPlanForRuntimeInvocation();

        ProtosLanguageContext enteredContext =
                ProtosLanguageContext.currentIfEnteredForRuntime();
        if (enteredContext != null) {
            if (closure.requiresContextLocalExecutionProjectionForRuntime()
                    || !template.isBytecodeBackendForRuntime()) {
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

        return template.isBytecodeBackendForRuntime() ? template : null;
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
                ProtosStandardBooleanProtocol.structuredCallbackKindForImplementation(
                        targetClosure),
                ProtosStandardArrayProtocol.isStandardEachImplementation(targetClosure),
                ProtosStandardBytesProtocol.isStandardEachImplementation(targetClosure),
                ProtosStandardProcessArgumentsProtocol.isStandardEachImplementation(targetClosure),
                ProtosStandardEnvironmentProtocol.isStandardEachImplementation(targetClosure),
                ProtosStandardIdentityMapProtocol.isStandardEachImplementation(targetClosure),
                ProtosStandardMapProtocol.isStandardEachImplementation(targetClosure),
                ProtosStandardMapProtocol.structuredReadLookupKindForImplementation(targetClosure),
                ProtosStandardMapProtocol.isStandardAtPutImplementation(targetClosure),
                ProtosStandardMapProtocol.isStandardRemoveImplementation(targetClosure),
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
                ProtosStandardBooleanProtocol.structuredCallbackKindForCanonicalSelection(
                        closure,
                        methodHome),
                ProtosStandardArrayProtocol.isCanonicalStandardEachSelection(
                        closure,
                        methodHome,
                        caller),
                ProtosStandardBytesProtocol.isCanonicalStandardEachSelection(
                        closure,
                        methodHome,
                        caller),
                ProtosStandardProcessArgumentsProtocol.isCanonicalStandardEachSelection(
                        closure,
                        receiver,
                        methodHome),
                ProtosStandardEnvironmentProtocol.isCanonicalStandardEachSelection(
                        closure,
                        receiver,
                        methodHome),
                ProtosStandardIdentityMapProtocol.isCanonicalStandardEachSelection(
                        closure,
                        methodHome,
                        caller),
                ProtosStandardMapProtocol.isCanonicalStandardEachSelection(
                        closure,
                        methodHome,
                        caller),
                ProtosStandardMapProtocol.structuredReadLookupKindForCanonicalSelection(
                        closure,
                        methodHome,
                        caller),
                ProtosStandardMapProtocol.isCanonicalStandardAtPutSelection(
                        closure,
                        methodHome,
                        caller),
                ProtosStandardMapProtocol.isCanonicalStandardRemoveSelection(
                        closure,
                        methodHome,
                        caller),
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
                null,
                false,
                false,
                false,
                false,
                false,
                false,
                null,
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
        if (closure.requiresContextLocalExecutionProjectionForRuntime()
                && ProtosLanguageContext.currentIfEnteredForRuntime() == null) {
            throw new UnsupportedOperationException(
                    "Context-local Closure projection requires an entered Protos Context");
        }
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
            boolean structuredProcessArgumentsEach,
            boolean structuredEnvironmentEach,
            boolean structuredIdentityMapEach,
            boolean structuredMapEach,
            ProtosStandardMapProtocol.StructuredReadLookupKind structuredMapReadLookup,
            boolean structuredMapAtPut,
            boolean structuredMapRemove,
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
                    structuredBoolean,
                    structuredArrayEach,
                    structuredBytesEach,
                    structuredProcessArgumentsEach,
                    structuredEnvironmentEach,
                    structuredIdentityMapEach,
                    structuredMapEach,
                    structuredMapReadLookup,
                    structuredMapAtPut,
                    structuredMapRemove,
                    directControlNative);
        }
        if (structuredEnsure
                || structuredErrorHandler
                || structuredWhile
                || structuredBoolean != null
                || structuredArrayEach
                || structuredBytesEach
                || structuredProcessArgumentsEach
                || structuredEnvironmentEach
                || structuredIdentityMapEach
                || structuredMapEach
                || structuredMapReadLookup != null
                || structuredMapAtPut
                || structuredMapRemove
                || directControlNative) {
            throw new IllegalStateException(
                    "structured control capability requires the canonical native implementation");
        }
        ProtosClosureExecutionPlan plan = closure.executionPlanForRuntimeInvocation();
        ProtosLanguageContext enteredContext =
                ProtosLanguageContext.currentIfEnteredForRuntime();
        if (enteredContext != null
                && (closure.requiresContextLocalExecutionProjectionForRuntime()
                        || !plan.isBytecodeBackendForRuntime())) {
            if (plan.source().isEmpty()) {
                throw new UnsupportedOperationException(
                        "C-prime composition cannot project a source-less AST Closure plan");
            }
            plan =
                    enteredContext.bytecodeExecutionPlanForEnteredClosure(
                            closure,
                            plan);
        }
        if (!plan.isBytecodeBackendForRuntime()) {
            throw new UnsupportedOperationException(
                    "C-prime composed invocation requires a Bytecode execution plan");
        }
        return new PreparedClosureCall(plan.bytecodeActivationTargetForComposition(), activation);
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
                return node.call(prepared.activation());
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
                        prepared.activation());
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
