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
import com.guillermomolina.protos.runtime.ProtosValueLookup;
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
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Idempotent;
import com.oracle.truffle.api.dsl.NonIdempotent;
import com.oracle.truffle.api.dsl.ReportPolymorphism.Megamorphic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.PropertyGetter;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;

/**
 * Fixed-arity Protos Bytecode DSL call operation.
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

    static PropertyGetter callSlotGetter(Shape shape) {
        return ProtosFunctionCallDispatchers.callSlotGetter(shape);
    }

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
            @Bind Node inliningTarget,
            @Bind("bytecodeOf($node)") BytecodeNode bytecode,
            @Bind("bytecode.getBytecodeIndex(frame)") int bytecodeIndex,
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
            @Exclusive @Cached ProtosFunctionCallDispatchers.FunctionDirectInvokeNode invoke,
            @Exclusive @Cached InlinedConditionProfile taskPresence,
            @Exclusive @Cached InlinedConditionProfile explicitTaskPresence,
            @Exclusive @Cached InlinedConditionProfile publishedActivationPresence,
            @Exclusive @Cached InlinedConditionProfile closureHeaderPresence,
            @Exclusive @Cached InlinedConditionProfile callerActivationPresence,
            @Exclusive @Cached InlinedConditionProfile suspensionPresence) {
        Object[] arguments = ProtosFrameArguments.dispatchedCall1(
                null, receiver, receiverHomeOrNull(receiver),
                null, null, a0);
        Object outcome = ProtosFunctionCallDispatchers.invokeCachedClosure(
                frame, inliningTarget, cachedHomeMode, taskPresence, explicitTaskPresence,
                publishedActivationPresence, closureHeaderPresence,
                callerActivationPresence, suspensionPresence, invoke,
                callNode, cachedCallee, callable, arguments, null);
        // The cached effective root's compilation-final proof: a proven body
        // never yields a suspended outcome, so no terminal classification.
        if (cachedCallee.provablyNonSuspendingBody()) {
            return outcome;
        }
        return ProtosTailCallContinuation.completeCall(
                terminal, outcome, bytecode, bytecodeIndex);
    }

    static ProtosFunctionCallDispatchers.ReturnHomeMode classifyReturnHome(
            ProtosClosureValue closure, ProtosBytecodeRootNode callee) {
        return ProtosFunctionCallDispatchers.classifyReturnHome(closure, callee);
    }

    /*
     * Ordinary CALL2 of a source function: identity-keyed direct-call
     * cache. The canonical `call` selection is checked before the cached
     * target is used.
     */
    @ForceQuickening
    @ForceQuickening("callSelection")
    @Specialization(
            guards = {
                    "!methodSend",
                    "isSourceFunction(callable)",
                    "isSingleContext()",
                    "canonicalCallSelected(callable, rootSelection, cachedShape, cachedLocalCallGetter)",
                    "callable == cachedCallable",
                    "cachedTarget != null"
            },
            assumptions = {"cachedCodeStable", "cachedCallerNeedsStable"},
            excludeForUncached = true,
            limit = "3")
    static Object callCachedClosure(
            VirtualFrame frame,
            boolean terminal,
            boolean methodSend,
            ProtosClosureValue callable,
            Object a0,
            Object a1,
            @Bind Node inliningTarget,
            @Bind("bytecodeOf($node)") BytecodeNode bytecode,
            @Bind("bytecode.getBytecodeIndex(frame)") int bytecodeIndex,
            @Bind("rootOf($node)") ProtosBytecodeRootNode root,
            @Cached(value = "callable", weak = true)
                    ProtosClosureValue cachedCallable,
            @Exclusive @Cached(value = "rootCallSelection()", allowUncached = true)
                    ProtosStandardObjectProtocol.CanonicalRootCallSelection rootSelection,
            @Exclusive @Cached(value = "callable.getShape()", allowUncached = true)
                    Shape cachedShape,
            @Exclusive @Cached(
                    value = "callSlotGetter(cachedShape)",
                    allowUncached = true,
                    neverDefault = false)
                    PropertyGetter cachedLocalCallGetter,
            @Cached("cachedCallable.codeStableAssumptionForRuntime()")
                    Assumption cachedCodeStable,
            @Cached("singleContextTargetOrNull(cachedCallable, root)")
                    RootCallTarget cachedTarget,
            @Cached("createDirectCallNodeOrNull(cachedTarget)")
                    DirectCallNode callNode,
            @Cached(value = "calleeRootOrNull(cachedTarget)", adopt = false)
                    ProtosBytecodeRootNode cachedCallee,
            @Cached("cachedCallee.callerNeedsStability()")
                    Assumption cachedCallerNeedsStable,
            @Cached("classifyReturnHome(cachedCallable, cachedCallee)")
                    ProtosFunctionCallDispatchers.ReturnHomeMode cachedHomeMode,
            @Exclusive @Cached ProtosFunctionCallDispatchers.FunctionDirectInvokeNode invoke,
            @Exclusive @Cached InlinedConditionProfile taskPresence,
            @Exclusive @Cached InlinedConditionProfile explicitTaskPresence,
            @Exclusive @Cached InlinedConditionProfile publishedActivationPresence,
            @Exclusive @Cached InlinedConditionProfile closureHeaderPresence,
            @Exclusive @Cached InlinedConditionProfile callerActivationPresence,
            @Exclusive @Cached InlinedConditionProfile suspensionPresence) {
        Object[] arguments = ProtosFrameArguments.dispatchedCall2(
                null, ProtosFrameArguments.functionCallMarker(),
                null, null, null, a0, a1);
        Object outcome = ProtosFunctionCallDispatchers.invokeCachedClosure(
                frame, inliningTarget, cachedHomeMode, taskPresence, explicitTaskPresence,
                publishedActivationPresence, closureHeaderPresence,
                callerActivationPresence, suspensionPresence, invoke,
                callNode, cachedCallee, callable, arguments, null);
        // The cached effective root's compilation-final proof: a proven body
        // never yields a suspended outcome, so no terminal classification.
        if (cachedCallee.provablyNonSuspendingBody()) {
            return outcome;
        }
        return ProtosTailCallContinuation.completeCall(
                terminal, outcome, bytecode, bytecodeIndex);
    }

    @ForceQuickening("callSelection")
    @Specialization(
            limit = "3",
            guards = {
                    "!methodSend",
                    "isSourceFunction(callable)",
                    "canonicalCallSelected(callable, rootSelection, cachedShape, cachedLocalCallGetter)"
            },
            replaces = "callCachedClosure")
    @Megamorphic
    @InliningCutoff
    static Object callFunction(
            VirtualFrame frame,
            boolean terminal,
            boolean methodSend,
            ProtosClosureValue callable,
            Object a0,
            Object a1,
            @Bind Node inliningTarget,
            @Bind("bytecodeOf($node)") BytecodeNode bytecode,
            @Bind("bytecode.getBytecodeIndex(frame)") int bytecodeIndex,
            @Bind("rootOf($node)") ProtosBytecodeRootNode root,
            @Exclusive @Cached(
                    value = "rootCallSelection()",
                    allowUncached = true)
                    ProtosStandardObjectProtocol.CanonicalRootCallSelection rootSelection,
            @Exclusive @Cached(value = "callable.getShape()", allowUncached = true)
                    Shape cachedShape,
            @Exclusive @Cached(
                    value = "callSlotGetter(cachedShape)",
                    allowUncached = true,
                    neverDefault = false)
                    PropertyGetter cachedLocalCallGetter,
            @Exclusive @Cached ProtosFunctionCallDispatchers.FunctionCachedCallNode call) {
        Object outcome = call.execute(
                frame, inliningTarget, terminal, callable,
                ProtosFrameArguments.dispatchedCall2(
                        null, ProtosFrameArguments.functionCallMarker(),
                        null, null, null, a0, a1),
                null, root,
                ProtosFunctionCallDispatchers.currentContext(inliningTarget));
        return ProtosTailCallContinuation.completeCall(
                terminal, outcome, bytecode, bytecodeIndex);
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
            @Bind Node inliningTarget,
            @Bind("bytecodeOf($node)") BytecodeNode bytecode,
            @Bind("bytecode.getBytecodeIndex(frame)") int bytecodeIndex,
            @Bind("rootOf($node)") ProtosBytecodeRootNode root,
            @Exclusive @Cached ProtosFunctionCallDispatchers.FunctionCachedCallNode call) {
        Object[] arguments = ProtosFrameArguments.dispatchedCall2(
                null, ProtosFrameArguments.functionCallMarker(),
                null, null, null, a0, a1);
        ProtosFrameArguments.bindMethod(
                arguments, method.receiver(), method.methodHome());
        Object outcome = call.execute(
                frame, inliningTarget, terminal, method.callable(), arguments,
                null, root,
                ProtosFunctionCallDispatchers.currentContext(inliningTarget));
        return ProtosTailCallContinuation.completeCall(
                terminal, outcome, bytecode, bytecodeIndex);
    }

    /*
     * Ordinary CALL2 of any callable no earlier specialization admits. It
     * performs the complete ordinary `call` selection, so it is also correct
     * for a canonical source Closure past the Shape-keyed caches.
     */
    @ForceQuickening("callSelection")
    @Specialization(guards = "!methodSend")
    @InliningCutoff
    static Object call(
            VirtualFrame frame,
            boolean terminal,
            boolean methodSend,
            Object callable,
            Object a0,
            Object a1,
            @Bind("bytecodeOf($node)") BytecodeNode bytecode,
            @Bind("bytecode.getBytecodeIndex(frame)") int bytecodeIndex,
            @Bind("rootOf($node)") ProtosBytecodeRootNode root,
            @Exclusive @Cached ProtosFunctionCallDispatchers.CallNode call) {
        Object outcome = call.execute(
                frame, terminal, callable,
                ProtosFrameArguments.dispatchedCall2(
                        null, ProtosFrameArguments.functionCallMarker(),
                        null, null, null, a0, a1),
                null, root);
        return ProtosTailCallContinuation.completeCall(
                terminal, outcome, bytecode, bytecodeIndex);
    }

    /*
     * SEND1 of a source method already selected by GetMethod as a bound
     * method (an inherited selection, or a non-direct own selection). No
     * further selection is performed: the B5 header carries the one
     * positional argument, the selection's actual receiver and real
     * methodHome are written, and the selected Closure is invoked through
     * the cached function dispatcher, which keys the actual Closure, the
     * entered Context and its effective Context-local target. Suspension
     * and terminal completion are those of every source call. Declared
     * before the generic SEND1, which never replaces it.
     */
    @Specialization(
            guards = {
                    "methodSend",
                    "isSourceFunction(method.callable())"
            })
    static Object callBoundSourceSend(
            VirtualFrame frame,
            boolean terminal,
            boolean methodSend,
            ProtosBoundMethod method,
            Object receiver,
            Object a0,
            @Bind Node inliningTarget,
            @Bind("bytecodeOf($node)") BytecodeNode bytecode,
            @Bind("bytecode.getBytecodeIndex(frame)") int bytecodeIndex,
            @Bind("rootOf($node)") ProtosBytecodeRootNode root,
            @Exclusive @Cached ProtosFunctionCallDispatchers.FunctionCachedCallNode call) {
        Object[] arguments = ProtosFrameArguments.dispatchedCall1(
                null, receiver, receiverHomeOrNull(receiver),
                null, null, a0);
        ProtosFrameArguments.bindMethod(
                arguments, method.receiver(), method.methodHome());
        Object outcome = call.execute(
                frame, inliningTarget, terminal, method.callable(), arguments,
                null, root,
                ProtosFunctionCallDispatchers.currentContext(inliningTarget));
        return ProtosTailCallContinuation.completeCall(
                terminal, outcome, bytecode, bytecodeIndex);
    }

    /*
     * Generic SEND1 covers polymorphic selection, cached-PIC misses,
     * native methods and selected bound methods of native or foreign
     * callables. Its B5 suffix still
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
            @Bind("bytecodeOf($node)") BytecodeNode bytecode,
            @Bind("bytecode.getBytecodeIndex(frame)") int bytecodeIndex,
            @Bind("rootOf($node)") ProtosBytecodeRootNode root,
            @Exclusive @Cached ProtosFunctionCallDispatchers.CallNode call) {
        Object[] arguments = ProtosFrameArguments.dispatchedCall1(
                null, receiver, receiverHomeOrNull(receiver),
                null, null, a0);

        if (callable instanceof ProtosBoundMethod method) {
            ProtosFrameArguments.bindMethod(
                    arguments, method.receiver(), method.methodHome());
        }

        Object outcome = call.execute(
                frame, terminal, callable, arguments,
                null, root);
        return ProtosTailCallContinuation.completeCall(
                terminal, outcome, bytecode, bytecodeIndex);
    }

    /*
     * Local DSL expression entry points shared with the unary source PIC.
     * They retain its admission and target-selection contract.
     */
    @Idempotent
    static boolean isSingleContext() {
        return ProtosFunctionCallDispatchers.isSingleContext();
    }

    static RootCallTarget singleContextTargetOrNull(
            ProtosClosureValue callable,
            ProtosBytecodeRootNode root) {
        return ProtosFunctionCallDispatchers.singleContextTargetOrNull(
                callable, root);
    }

    static DirectCallNode createDirectCallNodeOrNull(
            RootCallTarget target) {
        return ProtosFunctionCallDispatchers.createDirectCallNodeOrNull(
                target);
    }

    static ProtosBytecodeRootNode calleeRootOrNull(
            RootCallTarget target) {
        return ProtosFunctionCallDispatchers.calleeRootOrNull(target);
    }

    static ProtosObjectValue receiverHomeOrNull(Object receiver) {
        return receiver instanceof ProtosObjectValue object ? object : null;
    }


    static ProtosStandardObjectProtocol.CanonicalRootCallSelection
            rootCallSelection() {
        return ProtosStandardObjectProtocol
                .canonicalRootCallSelectionOrNullForRuntime();
    }

    // The cached Shape proves only local `call` slot presence; a present
    // slot may be reassigned, so its value is checked on every execution.
    @NonIdempotent
    static boolean canonicalCallSelected(
            ProtosClosureValue callable,
            ProtosStandardObjectProtocol.CanonicalRootCallSelection selection,
            Shape cachedShape,
            PropertyGetter cachedLocalCallGetter) {
        return ProtosFunctionCallDispatchers.hasCanonicalCallSelectionForShape(
                callable, selection, cachedShape, cachedLocalCallGetter);
    }

    static boolean isSourceFunction(ProtosClosureValue callable) {
        return ProtosFunctionCallDispatchers.CallNode.isSourceFunction(callable);
    }

    // Proxy nodes bind through their operation node. The interpreter stores
    // this call site's index in its frame before entering the proxy.
    static BytecodeNode bytecodeOf(Node node) {
        return BytecodeNode.get(node);
    }

    static ProtosBytecodeRootNode rootOf(Node node) {
        return (ProtosBytecodeRootNode)
                BytecodeNode.get(node).getBytecodeRootNode();
    }
}
