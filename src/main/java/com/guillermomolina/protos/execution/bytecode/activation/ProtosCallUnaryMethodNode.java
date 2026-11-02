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

import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.bytecode.BytecodeNode;
import com.oracle.truffle.api.bytecode.ConstantOperand;
import com.oracle.truffle.api.bytecode.ForceQuickening;
import com.oracle.truffle.api.bytecode.OperationProxy;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Idempotent;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.Node;

/**
 * I115 (GraalPy {@code Call*Method}): the {@code CallUnaryMethod} operation, a call
 * of an evaluated callable with 1 positional argument. The callable is a source Closure, a
 * native Closure, a {@link ProtosBoundMethod} produced by {@code GetMethod}
 * (its receiver is not counted among the arguments) or any other object.
 *
 * <p>The B5 frame-argument array is built once here and becomes the callee
 * frame's arguments. Source functions first use the unary direct-call
 * cache, with the shared function PIC as their polymorphic fallback.
 * A bound source method preserves its receiver and methodHome and uses
 * the shared PIC; other callables use {@link
 * ProtosFunctionCallDispatchers.CallNode}. No call is prepared.
 */
@GenerateUncached
@GenerateInline(false)
@ConstantOperand(type = boolean.class, name = "terminal")
@OperationProxy.Proxyable(allowUncached = true, storeBytecodeIndex = true)
abstract class ProtosCallUnaryMethodNode extends Node {

    abstract Object execute(VirtualFrame frame, boolean terminal, Object callable, Object a0);

    /*
     * GraalPy CallUnaryMethodNode.callPFunction: this specialization is
     * exclusive to a language instance with one Context. The target is
     * obtained from the already-prepared, same-language executable plan.
     * It neither resolves an entered Context nor projects an execution
     * plan on a cache hit. Multi-context and deferred cases use the
     * existing FunctionCachedCallNode fallback.
     */
    @ForceQuickening
    @Specialization(
            guards = {
                    "isSourceFunction(callable)",
                    "isSingleContext()",
                    "callable == cachedCallable",
                    "cachedTarget != null"
            },
            assumptions = {
                    "cachedCodeStable",
                    "cachedProjectionStable"
            },
            excludeForUncached = true,
            limit = "3")
    static Object callPFunction(
            VirtualFrame frame,
            boolean terminal,
            ProtosClosureValue callable,
            Object a0,
            @Bind("$node") Node inliningTarget,
            @Bind("rootOf($node)") ProtosBytecodeRootNode root,
            @Cached(value = "callable", weak = true)
                    ProtosClosureValue cachedCallable,
            @Cached("cachedCallable.codeStableAssumptionForRuntime()")
                    Assumption cachedCodeStable,
            @Cached("cachedCallable.contextProjectionStabilityForRuntime()")
                    Assumption cachedProjectionStable,
            @Cached("singleContextTargetOrNull(cachedCallable, root)")
                    RootCallTarget cachedTarget,
            @Cached("createDirectCallNodeOrNull(cachedTarget)")
                    DirectCallNode callNode,
            @Cached(value = "calleeRootOrNull(cachedTarget)", adopt = false)
                    ProtosBytecodeRootNode cachedCallee,
            @Cached ProtosFunctionCallDispatchers.FunctionDirectInvokeNode invoke) {
        Object[] arguments = ProtosFrameArguments.dispatchedCall1(
                null, ProtosFrameArguments.functionCallMarker(),
                null, null, null, a0);
        return invoke.execute(
                frame, inliningTarget, terminal, callNode, cachedCallee,
                callable, arguments,
                ProtosFunctionCallDispatchers.callerReference(frame), root);
    }

    @Specialization(
            guards = "isSourceFunction(callable)",
            replaces = "callPFunction")
    static Object callFunction(
            VirtualFrame frame,
            boolean terminal,
            ProtosClosureValue callable,
            Object a0,
            @Bind("$node") Node inliningTarget,
            @Bind("rootOf($node)") ProtosBytecodeRootNode root,
            @Cached ProtosFunctionCallDispatchers.FunctionCachedCallNode call) {
        return call.execute(
                frame, inliningTarget, terminal, callable,
                ProtosFrameArguments.dispatchedCall1(
                        null, ProtosFrameArguments.functionCallMarker(),
                        null, null, null, a0),
                ProtosFunctionCallDispatchers.callerReference(frame), root,
                ProtosFunctionCallDispatchers.currentContext(inliningTarget));
    }

    @Specialization(guards = "isSourceFunction(method.callable())")
    static Object callBoundMethod(
            VirtualFrame frame,
            boolean terminal,
            ProtosBoundMethod method,
            Object a0,
            @Bind("$node") Node inliningTarget,
            @Bind("rootOf($node)") ProtosBytecodeRootNode root,
            @Cached ProtosFunctionCallDispatchers.FunctionCachedCallNode call) {
        Object[] arguments = ProtosFrameArguments.dispatchedCall1(
                null, ProtosFrameArguments.functionCallMarker(), null, null, null, a0);
        ProtosFrameArguments.bindMethod(arguments, method.receiver(), method.methodHome());
        return call.execute(
                frame, inliningTarget, terminal, method.callable(), arguments,
                ProtosFunctionCallDispatchers.callerReference(frame), root,
                ProtosFunctionCallDispatchers.currentContext(inliningTarget));
    }

    /* Builtins, bound builtins and generic callables: GraalPy CallNode. */
    @Specialization(guards = "!isSourceCallable(callable)")
    @InliningCutoff
    static Object call(
            VirtualFrame frame,
            boolean terminal,
            Object callable,
            Object a0,
            @Bind("rootOf($node)") ProtosBytecodeRootNode root,
            @Cached ProtosFunctionCallDispatchers.CallNode call) {
        return call.execute(
                frame, terminal, callable,
                ProtosFrameArguments.dispatchedCall1(
                null, ProtosFrameArguments.functionCallMarker(), null, null, null, a0),
                ProtosFunctionCallDispatchers.callerReference(frame), root);
    }

    static boolean isSourceFunction(ProtosClosureValue callable) {
        return ProtosFunctionCallDispatchers.CallNode.isSourceFunction(callable);
    }

    static boolean isSourceCallable(Object callable) {
        return callable instanceof ProtosClosureValue closure
                        ? isSourceFunction(closure)
                        : callable instanceof ProtosBoundMethod method
                                && isSourceFunction(method.callable());
    }

    /*
     * GraalPy AbstractCallMethodNode.isSingleContext:
     * admission-time guard, independent of the generated cache node.
     */
    @Idempotent
    static boolean isSingleContext() {
        CompilerAsserts.neverPartOfCompilation();
        return ProtosLanguageContext.current()
                .languageForRuntime()
                .isSingleContextForRuntime();
    }

    /*
     * Only a prepared plan belonging to this physical root's language
     * can enter the single-context direct specialization. A deferred,
     * foreign or projection-required plan takes the context-aware PIC.
     */
    static RootCallTarget singleContextTargetOrNull(
            ProtosClosureValue callable,
            ProtosBytecodeRootNode callerRoot) {
        ProtosClosureExecutionPlan plan =
                callable.preparedExecutionPlanOrNullForRuntime();

        if (plan == null
                || callable.requiresContextLocalExecutionProjectionForRuntime()
                || plan.language().orElse(null)
                        != callerRoot.getLanguage(ProtosLanguage.class)) {
            return null;
        }

        return plan.bytecodeActivationTargetForComposition();
    }

    static DirectCallNode createDirectCallNodeOrNull(RootCallTarget target) {
        return ProtosFunctionCallDispatchers.createDirectCallNodeOrNull(target);
    }

    static ProtosBytecodeRootNode calleeRootOrNull(RootCallTarget target) {
        return ProtosFunctionCallDispatchers.calleeRootOrNull(target);
    }

    static ProtosBytecodeRootNode rootOf(Node node) {
        return (ProtosBytecodeRootNode) BytecodeNode.get(node).getBytecodeRootNode();
    }
}
