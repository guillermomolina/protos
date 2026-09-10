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

import com.oracle.truffle.api.bytecode.BytecodeRootNode;
import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosArrayValue;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosCoreErrors;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosReturnHome;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.guillermomolina.protos.runtime.ProtosNativeClosureBody;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosSlotLookupResult;
import com.guillermomolina.protos.runtime.ProtosValueLookup;
import com.guillermomolina.protos.semantic.ast.CanonicalClosure;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.bytecode.ContinuationResult;
import com.oracle.truffle.api.bytecode.ContinuationRootNode;
import com.oracle.truffle.api.bytecode.GenerateBytecode;
import com.oracle.truffle.api.bytecode.Operation;
import com.oracle.truffle.api.bytecode.Variadic;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameDescriptor;
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
        enableRootBodyTagging = false)
abstract class ProtosBytecodeRootNode extends RootNode implements BytecodeRootNode {
    protected ProtosBytecodeRootNode(
            ProtosLanguage language,
            FrameDescriptor frameDescriptor) {
        super(language, frameDescriptor);
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

    static final class PreparedClosureCall {
        private final RootCallTarget bodyTarget;
        private final ProtosNativeClosureBody nativeBody;
        private final List<?> supplied;
        private final ProtosActivation activation;
        private final ProtosReturnHome returnHome;
        private final boolean ownsReturnHome;

        PreparedClosureCall(RootCallTarget bodyTarget, ProtosActivation activation) {
            this(java.util.Objects.requireNonNull(bodyTarget, "bodyTarget"), null, List.of(), activation);
        }

        private PreparedClosureCall(
                RootCallTarget bodyTarget,
                ProtosNativeClosureBody nativeBody,
                List<?> supplied,
                ProtosActivation activation) {
            this.bodyTarget = bodyTarget;
            this.nativeBody = nativeBody;
            this.supplied = List.copyOf(supplied);
            this.activation = java.util.Objects.requireNonNull(activation, "activation");
            this.returnHome = activation.returnHome().orElseThrow(
                    () -> new IllegalStateException("Closure invocation requires a return home"));
            this.ownsReturnHome = activation.ownsReturnHome();
        }

        static PreparedClosureCall nativeCall(
                ProtosNativeClosureBody nativeBody,
                List<?> supplied,
                ProtosActivation activation) {
            return new PreparedClosureCall(null, java.util.Objects.requireNonNull(nativeBody, "nativeBody"), supplied, activation);
        }

        RootCallTarget bodyTarget() { return bodyTarget; }
        ProtosActivation activation() { return activation; }
        boolean isNative() { return nativeBody != null; }
        Object enterNative() {
            if (nativeBody == null) throw new IllegalStateException("prepared Closure call is not native");
            return nativeBody.execute(activation, supplied);
        }
        Object finish(Object result) {
            if (ownsReturnHome && returnHome.isActive()) returnHome.complete();
            return result;
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
        rejectComposedInvocationTaskOrProjection(
                targetClosure,
                caller);
        ProtosActivation activation =
                ProtosActivation.forClosureInvocation(
                        targetClosure,
                        supplied,
                        caller.prelude().orElse(null),
                        caller.actorModuleState(),
                        caller.currentModuleKey().orElse(null),
                        caller.executionDomain());
        activation.inheritDynamicControlState(caller);
        return finishPreparingComposedCall(
                targetClosure,
                supplied,
                activation);
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

    private static PreparedClosureCall prepareImmediateMethodCall(
            ProtosClosureValue closure,
            Object receiver,
            ProtosObjectValue methodHome,
            List<?> supplied,
            ProtosActivation caller) {
        rejectComposedInvocationTaskOrProjection(closure, caller);
        ProtosActivation activation = ProtosActivation.forImmediateMethodInvocation(
                closure, supplied, receiver, methodHome, caller.prelude().orElse(null),
                caller.actorModuleState(), caller.currentModuleKey().orElse(null), caller.executionDomain());
        activation.inheritDynamicControlState(caller);
        return finishPreparingComposedCall(closure, supplied, activation);
    }

    private static void rejectComposedInvocationTaskOrProjection(
            ProtosClosureValue closure, ProtosActivation caller) {
        if (caller.task().isPresent()) {
            throw new UnsupportedOperationException(
                    "PERF006-B2 Task/Future continuation ownership belongs to PERF006-B3");
        }
        if (closure.requiresContextLocalExecutionProjectionForRuntime()) {
            throw new UnsupportedOperationException(
                    "PERF006-B2 shared-Context Closure projection is not migrated yet");
        }
    }

    private static PreparedClosureCall finishPreparingComposedCall(
            ProtosClosureValue closure, List<?> supplied, ProtosActivation activation) {
        if (closure.nativeBody().isPresent()) {
            return PreparedClosureCall.nativeCall(closure.nativeBody().orElseThrow(), supplied, activation);
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
            return node.call(prepared.activation());
        }

        @Specialization(replaces = "direct")
        public static Object indirect(
                PreparedClosureCall prepared,
                @Cached IndirectCallNode node) {
            return node.call(
                    prepared.bodyTarget(),
                    prepared.activation());
        }
    }

    @Operation
    public static final class IsContinuation {
        @Specialization
        public static boolean perform(Object value) {
            return value instanceof ContinuationResult;
        }
    }

    @Operation
    public static final class ResumeContinuation {
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
            return node.call(
                    result.getFrame(),
                    resumeValue);
        }

        @Specialization(replaces = "direct")
        public static Object indirect(
                ContinuationResult result,
                Object resumeValue,
                @Cached IndirectCallNode node) {
            return node.call(
                    result.getContinuationCallTarget(),
                    result.getFrame(),
                    resumeValue);
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
