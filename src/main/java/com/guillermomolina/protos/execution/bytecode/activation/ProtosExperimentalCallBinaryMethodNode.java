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
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.oracle.truffle.api.Assumption;
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
 * I115 GraalPy-shaped fixed-arity call operation.
 *
 * Ordinary CALL2 receives two positional arguments. Immediate SEND1
 * receives an independently transported receiver and one positional
 * argument. Both use the existing B5 frame layout and dispatcher family.
 */
@GenerateUncached
@GenerateInline(false)
@ConstantOperand(type = boolean.class, name = "terminal")
@ConstantOperand(type = boolean.class, name = "methodSend")
@OperationProxy.Proxyable(allowUncached = true, storeBytecodeIndex = true)
abstract class ProtosExperimentalCallBinaryMethodNode extends Node {

    abstract Object execute(
            VirtualFrame frame,
            boolean terminal,
            boolean methodSend,
            Object callable,
            Object a0,
            Object a1);

    @ForceQuickening
    @Specialization(
            guards = {
                    "methodSend",
                    "isSourceFunction(callable)",
                    "isSingleContext()",
                    "callable == cachedCallable",
                    "cachedTarget != null"
            },
            assumptions = {"cachedCodeStable", "cachedCallerNeedsStable"},
            excludeForUncached = true,
            limit = "3")
    static Object callDirectSourceSend(
            VirtualFrame frame,
            boolean terminal,
            boolean methodSend,
            ProtosClosureValue callable,
            Object receiver,
            Object a0,
            @Bind("$node") Node inliningTarget,
            @Bind("rootOf($node)") ProtosBytecodeRootNode root,
            @Cached(value = "callable", weak = true)
                    ProtosClosureValue cachedCallable,
            @Cached("cachedCallable.codeStableAssumptionForRuntime()")
                    Assumption cachedCodeStable,
            @Cached("singleContextTargetOrNull(cachedCallable, root)")
                    RootCallTarget cachedTarget,
            @Cached("createDirectCallNodeOrNull(cachedTarget)")
                    DirectCallNode callNode,
            @Cached(
                    value = "calleeRootOrNull(cachedTarget)",
                    adopt = false)
                    ProtosBytecodeRootNode cachedCallee,
            @Cached("cachedCallee.callerNeedsStability()")
                    Assumption cachedCallerNeedsStable,
            @Cached("classifyReturnHome(cachedCallable, cachedCallee)")
                    ProtosFunctionCallDispatchers.ReturnHomeMode cachedHomeMode,
            @Cached ProtosFunctionCallDispatchers.FunctionDirectInvokeNode invoke) {
        Object[] arguments = ProtosFrameArguments.dispatchedCall1(
                null, receiver, receiverHomeOrNull(receiver),
                null, null, a0);
        if (cachedHomeMode == ProtosFunctionCallDispatchers.ReturnHomeMode.UNOBSERVABLE) {
            return ProtosFunctionCallDispatchers.invokeUnobservableHome(
                    frame, callNode, cachedCallee, callable, arguments, null);
        }
        if (cachedHomeMode == ProtosFunctionCallDispatchers.ReturnHomeMode.CAPTURED) {
            return ProtosFunctionCallDispatchers.invokeCapturedHome(
                    frame, callNode, cachedCallee, callable, arguments, null);
        }
        return invoke.execute(
                frame, inliningTarget, callNode, cachedCallee,
                callable, arguments, null);
    }

    static ProtosFunctionCallDispatchers.ReturnHomeMode classifyReturnHome(
            ProtosClosureValue closure, ProtosBytecodeRootNode callee) {
        return ProtosFunctionCallDispatchers.classifyReturnHome(closure, callee);
    }

    @Specialization(guards = {"!methodSend", "isSourceFunction(callable)"})
    static Object callFunction(
            VirtualFrame frame,
            boolean terminal,
            boolean methodSend,
            ProtosClosureValue callable,
            Object a0,
            Object a1,
            @Bind("$node") Node inliningTarget,
            @Bind("rootOf($node)") ProtosBytecodeRootNode root,
            @Cached ProtosFunctionCallDispatchers.FunctionCachedCallNode call) {
        return call.execute(
                frame, inliningTarget, terminal, callable,
                ProtosFrameArguments.dispatchedCall2(
                        null, ProtosFrameArguments.functionCallMarker(),
                        null, null, null, a0, a1),
                null, root,
                ProtosFunctionCallDispatchers.currentContext(inliningTarget));
    }

    @Specialization(
            guards = {
                    "!methodSend",
                    "isSourceFunction(method.callable())"
            })
    static Object callBoundMethod(
            VirtualFrame frame,
            boolean terminal,
            boolean methodSend,
            ProtosBoundMethod method,
            Object a0,
            Object a1,
            @Bind("$node") Node inliningTarget,
            @Bind("rootOf($node)") ProtosBytecodeRootNode root,
            @Cached ProtosFunctionCallDispatchers.FunctionCachedCallNode call) {
        Object[] arguments = ProtosFrameArguments.dispatchedCall2(
                null, ProtosFrameArguments.functionCallMarker(),
                null, null, null, a0, a1);
        ProtosFrameArguments.bindMethod(
                arguments, method.receiver(), method.methodHome());
        return call.execute(
                frame, inliningTarget, terminal, method.callable(), arguments,
                null, root,
                ProtosFunctionCallDispatchers.currentContext(inliningTarget));
    }

    @Specialization(
            guards = {"!methodSend", "!isSourceCallable(callable)"})
    @InliningCutoff
    static Object call(
            VirtualFrame frame,
            boolean terminal,
            boolean methodSend,
            Object callable,
            Object a0,
            Object a1,
            @Bind("rootOf($node)") ProtosBytecodeRootNode root,
            @Cached ProtosFunctionCallDispatchers.CallNode call) {
        return call.execute(
                frame, terminal, callable,
                ProtosFrameArguments.dispatchedCall2(
                        null, ProtosFrameArguments.functionCallMarker(),
                        null, null, null, a0, a1),
                null, root);
    }

    /*
     * Generic SEND1 covers polymorphic selection, cached-PIC misses,
     * native methods and selected bound methods. Its B5 suffix still
     * contains exactly one supplied positional argument.
     */
    @Specialization(
            guards = "methodSend",
            replaces = "callDirectSourceSend")
    @InliningCutoff
    static Object callGenericSend(
            VirtualFrame frame,
            boolean terminal,
            boolean methodSend,
            Object callable,
            Object receiver,
            Object a0,
            @Bind("rootOf($node)") ProtosBytecodeRootNode root,
            @Cached ProtosFunctionCallDispatchers.CallNode call) {
        Object[] arguments = ProtosFrameArguments.dispatchedCall1(
                null, receiver, receiverHomeOrNull(receiver),
                null, null, a0);

        if (callable instanceof ProtosBoundMethod method) {
            ProtosFrameArguments.bindMethod(
                    arguments, method.receiver(), method.methodHome());
        }

        return call.execute(
                frame, terminal, callable, arguments,
                null, root);
    }

    /*
     * Local DSL expression entry points shared with the unary source PIC.
     * They retain its admission and target-selection contract.
     */
    @Idempotent
    static boolean isSingleContext() {
        return ProtosCallUnaryMethodNode.isSingleContext();
    }

    static RootCallTarget singleContextTargetOrNull(
            ProtosClosureValue callable,
            ProtosBytecodeRootNode root) {
        return ProtosCallUnaryMethodNode.singleContextTargetOrNull(
                callable, root);
    }

    static DirectCallNode createDirectCallNodeOrNull(
            RootCallTarget target) {
        return ProtosCallUnaryMethodNode.createDirectCallNodeOrNull(
                target);
    }

    static ProtosBytecodeRootNode calleeRootOrNull(
            RootCallTarget target) {
        return ProtosCallUnaryMethodNode.calleeRootOrNull(target);
    }

    static ProtosObjectValue receiverHomeOrNull(Object receiver) {
        return receiver instanceof ProtosObjectValue object ? object : null;
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

    static ProtosBytecodeRootNode rootOf(Node node) {
        return (ProtosBytecodeRootNode)
                BytecodeNode.get(node).getBytecodeRootNode();
    }
}
