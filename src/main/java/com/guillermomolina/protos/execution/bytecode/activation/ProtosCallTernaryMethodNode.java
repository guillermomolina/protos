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
 * Protos Bytecode DSL: the {@code CallTernaryMethod} operation, a call
 * of an evaluated callable with 3 positional arguments. The callable is a source Closure, a
 * native Closure, a {@link ProtosBoundMethod} produced by {@code GetMethod}
 * (its receiver is not counted among the arguments) or any other object.
 *
 * <p>The B5 frame-argument array is built once here and becomes the callee
 * frame's arguments. A source function uses an identity-keyed direct-call
 * cache before the shared function PIC; a bound source method writes its receiver and methodHome
 * and uses the same PIC; every other callable takes {@link
 * ProtosFunctionCallDispatchers.CallNode}. No call is prepared.
 */
@GenerateUncached
@GenerateInline(false)
@ConstantOperand(type = boolean.class, name = "terminal")
@OperationProxy.Proxyable(allowUncached = true, storeBytecodeIndex = true)
abstract class ProtosCallTernaryMethodNode extends Node {

    static PropertyGetter callSlotGetter(Shape shape) {
        return ProtosFunctionCallDispatchers.callSlotGetter(shape);
    }

    abstract Object execute(VirtualFrame frame, boolean terminal, Object callable, Object a0, Object a1, Object a2);

    /*
     * Identity-keyed direct-call cache for source functions. The canonical
     * `call` selection is checked before the cached target is used; code
     * changes invalidate the entry through its code-stability assumption.
     */
    @ForceQuickening
    @ForceQuickening("callSelection")
    @Specialization(
            guards = {
                    "isSourceFunction(callable)",
                    "isSingleContext()",
                    "canonicalCallSelected(callable, rootSelection, cachedShape, cachedLocalCallGetter)",
                    "callable == cachedCallable",
                    "cachedTarget != null"
            },
            assumptions = {"cachedCallable.codeStableAssumptionForRuntime()", "cachedCallee.callerNeedsStability()"},
            excludeForUncached = true,
            limit = "4")
    static Object callCachedClosure(
            VirtualFrame frame,
            boolean terminal,
            ProtosClosureValue callable,
            Object a0,
            Object a1,
            Object a2,
            @Bind Node inliningTarget,
            @Bind("bytecodeOf($node)") BytecodeNode bytecode,
            @Bind("bytecode.getBytecodeIndex(frame)") int bytecodeIndex,
            @Cached(value = "callable", weak = true)
                    ProtosClosureValue cachedCallable,
            @Exclusive @Cached(value = "rootCallSelection()", allowUncached = true)
                    ProtosStandardObjectProtocol.CanonicalRootCallSelection rootSelection,
            @Exclusive @Cached(value = "callable.getShape()", allowUncached = true)
                    Shape cachedShape,
            @Exclusive @Cached(
                    value = "callSlotGetter(cachedShape)",
                    allowUncached = true)
                    PropertyGetter cachedLocalCallGetter,
            @Cached("singleContextTargetOrNull(cachedCallable, rootOf($node))")
                    RootCallTarget cachedTarget,
            @Cached("createDirectCallNodeOrNull(cachedTarget)")
                    DirectCallNode callNode,
            @Cached(value = "calleeRootOrNull(cachedTarget)", adopt = false)
                    ProtosBytecodeRootNode cachedCallee,
            @Cached("classifyReturnHome(cachedCallable, cachedCallee)")
                    ProtosFunctionCallDispatchers.ReturnHomeMode cachedHomeMode,
            @Exclusive @Cached ProtosFunctionCallDispatchers.FunctionDirectInvokeNode invoke,
            @Exclusive @Cached ProtosFunctionCallDispatchers.PrepareInvocationTaskNode prepareTask,
            @Exclusive @Cached InlinedConditionProfile suspensionPresence) {
        Object[] arguments = ProtosFrameArguments.dispatchedCall3(
                null, ProtosFrameArguments.functionCallMarker(),
                null, null, null, a0, a1, a2);
        Object outcome = ProtosFunctionCallDispatchers.invokeCachedClosure(
                frame, inliningTarget, cachedHomeMode, prepareTask, suspensionPresence, invoke,
                callNode, cachedCallee, callable, arguments, null);
        // The cached effective root's compilation-final proof: a proven body
        // never yields a suspended outcome, so no terminal classification.
        if (cachedCallee.provablyNonSuspendingBody()) {
            return outcome;
        }
        return ProtosTailCallContinuation.completeCall(
                terminal, outcome, bytecode, bytecodeIndex);
    }

    /*
     * Megamorphic transition: the context-aware function dispatcher, which
     * keeps its own target cache and indirect fallback.
     */
    @ForceQuickening("callSelection")
    @Specialization(
            limit = "3",
            guards = {
                    "isSourceFunction(callable)",
                    "canonicalCallSelected(callable, rootSelection, cachedShape, cachedLocalCallGetter)"
            },
            replaces = "callCachedClosure")
    @Megamorphic
    @InliningCutoff
    static Object callFunction(
            VirtualFrame frame,
            boolean terminal,
            ProtosClosureValue callable,
            Object a0,
            Object a1,
            Object a2,
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
                    allowUncached = true)
                    PropertyGetter cachedLocalCallGetter,
            @Exclusive @Cached ProtosFunctionCallDispatchers.FunctionCachedCallNode call) {
        Object outcome = call.execute(
                frame, inliningTarget, terminal, callable,
                ProtosFrameArguments.dispatchedCall3(
                null, ProtosFrameArguments.functionCallMarker(), null, null, null, a0, a1, a2),
                null, root,
                ProtosFunctionCallDispatchers.currentContext(inliningTarget));
        return ProtosTailCallContinuation.completeCall(
                terminal, outcome, bytecode, bytecodeIndex);
    }

    @Specialization(guards = "isSourceFunction(method.callable())")
    static Object callBoundMethod(
            VirtualFrame frame,
            boolean terminal,
            ProtosBoundMethod method,
            Object a0,
            Object a1,
            Object a2,
            @Bind Node inliningTarget,
            @Bind("bytecodeOf($node)") BytecodeNode bytecode,
            @Bind("bytecode.getBytecodeIndex(frame)") int bytecodeIndex,
            @Bind("rootOf($node)") ProtosBytecodeRootNode root,
            @Exclusive @Cached ProtosFunctionCallDispatchers.FunctionCachedCallNode call) {
        Object[] arguments = ProtosFrameArguments.dispatchedCall3(
                null, ProtosFrameArguments.functionCallMarker(), null, null, null, a0, a1, a2);
        ProtosFrameArguments.bindMethod(arguments, method.receiver(), method.methodHome());
        Object outcome = call.execute(
                frame, inliningTarget, terminal, method.callable(), arguments,
                null, root,
                ProtosFunctionCallDispatchers.currentContext(inliningTarget));
        return ProtosTailCallContinuation.completeCall(
                terminal, outcome, bytecode, bytecodeIndex);
    }

    /* Builtins, bound builtins and generic callables: the shared CallNode. */
    // Declared last: ordinary `call` selection for every callable no earlier
    // specialization admits, including canonical Closures past the
    // Shape-keyed caches.
    @ForceQuickening("callSelection")
    @Specialization
    @InliningCutoff
    static Object call(
            VirtualFrame frame,
            boolean terminal,
            Object callable,
            Object a0,
            Object a1,
            Object a2,
            @Bind("bytecodeOf($node)") BytecodeNode bytecode,
            @Bind("bytecode.getBytecodeIndex(frame)") int bytecodeIndex,
            @Bind("rootOf($node)") ProtosBytecodeRootNode root,
            @Cached ProtosFunctionCallDispatchers.CallNode call) {
        Object outcome = call.execute(
                frame, terminal, callable,
                ProtosFrameArguments.dispatchedCall3(
                null, ProtosFrameArguments.functionCallMarker(), null, null, null, a0, a1, a2),
                null, root);
        return ProtosTailCallContinuation.completeCall(
                terminal, outcome, bytecode, bytecodeIndex);
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

    // Admission-time guard, independent of the generated cache node.
    @Idempotent
    static boolean isSingleContext() {
        return ProtosFunctionCallDispatchers.isSingleContext();
    }

    static RootCallTarget singleContextTargetOrNull(
            ProtosClosureValue callable,
            ProtosBytecodeRootNode callerRoot) {
        return ProtosFunctionCallDispatchers.singleContextTargetOrNull(
                callable, callerRoot);
    }

    static DirectCallNode createDirectCallNodeOrNull(RootCallTarget target) {
        return ProtosFunctionCallDispatchers.createDirectCallNodeOrNull(target);
    }

    static ProtosBytecodeRootNode calleeRootOrNull(RootCallTarget target) {
        return ProtosFunctionCallDispatchers.calleeRootOrNull(target);
    }

    static ProtosFunctionCallDispatchers.ReturnHomeMode classifyReturnHome(
            ProtosClosureValue closure, ProtosBytecodeRootNode callee) {
        return ProtosFunctionCallDispatchers.classifyReturnHome(closure, callee);
    }

    // Proxy nodes bind through their operation node. The interpreter stores
    // this call site's index in its frame before entering the proxy.
    static BytecodeNode bytecodeOf(Node node) {
        return BytecodeNode.get(node);
    }

    static ProtosBytecodeRootNode rootOf(Node node) {
        return (ProtosBytecodeRootNode) BytecodeNode.get(node).getBytecodeRootNode();
    }
}
