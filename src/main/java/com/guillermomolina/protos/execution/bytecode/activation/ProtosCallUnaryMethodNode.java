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
import com.oracle.truffle.api.dsl.NonIdempotent;
import com.oracle.truffle.api.dsl.ReportPolymorphism.Megamorphic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;

/**
 * Protos Bytecode DSL: the {@code CallUnaryMethod} operation, a call
 * of an evaluated callable with 1 positional argument. The callable is a source Closure, a
 * native Closure, a {@link ProtosBoundMethod} produced by {@code GetMethod}
 * (its receiver is not counted among the arguments) or any other object.
 *
 * <p>The B5 frame-argument array is built once here and becomes the callee
 * frame's arguments. Source functions use an identity-keyed
 * unary direct-call cache, then a cache keyed by the shared prepared
 * execution plan, before entering the existing call dispatcher.
 * A bound source method preserves its receiver and methodHome and uses
 * the shared PIC; other callables use {@link
 * ProtosFunctionCallDispatchers.CallNode}. No call is prepared.
 */
@GenerateUncached
@GenerateInline(false)
@ConstantOperand(type = boolean.class, name = "terminal")
@OperationProxy.Proxyable(allowUncached = true, storeBytecodeIndex = true)
abstract class ProtosCallUnaryMethodNode extends Node {

    static ProtosStandardObjectProtocol.CanonicalRootCallSelection
            rootCallSelection() {
        return ProtosStandardObjectProtocol
                .canonicalRootCallSelectionOrNullForRuntime();
    }

    abstract Object execute(VirtualFrame frame, boolean terminal, Object callable, Object a0);

    /*
     * Cache the callable
     * instance rather than its shared source execution plan. Cache hits
     * use the already-prepared, same-language target without resolving
     * an entered Context. Other source calls use the existing dispatcher.
     */
    // The direct-call PIC is keyed by Closure identity. Its cached code
    // stability assumption invalidates the installed target when code changes.
    @ForceQuickening
    @Specialization(
            guards = {
                    "isSourceFunction(callable)",
                    "isSingleContext()",
                    // D013 selection first: an overridden `call` never
                    // prepares the Closure's own target.
                    "canonicalCallSelected(callable, rootSelection)",
                    "callable == cachedCallable",
                    "cachedTarget != null"
            },
            assumptions = {"cachedCodeStable", "cachedCallerNeedsStable"},
            excludeForUncached = true,
            limit = "4")
    static Object callCachedClosure(
            VirtualFrame frame,
            boolean terminal,
            ProtosClosureValue callable,
            Object a0,
            @Bind("$node") Node inliningTarget,
            @Bind("bytecodeOf($node)") BytecodeNode bytecode,
            @Bind("bytecode.getBytecodeIndex(frame)") int bytecodeIndex,
            @Bind("rootOf($node)") ProtosBytecodeRootNode root,
            @Cached(value = "callable", weak = true)
                    ProtosClosureValue cachedCallable,
            @Cached(value = "rootCallSelection()", allowUncached = true)
                    ProtosStandardObjectProtocol.CanonicalRootCallSelection rootSelection,
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
            @Cached ProtosFunctionCallDispatchers.FunctionDirectInvokeNode invoke,
            @Cached InlinedConditionProfile taskPresence) {
        Object[] arguments = ProtosFrameArguments.dispatchedCall1(
                null, ProtosFrameArguments.functionCallMarker(),
                null, null, null, a0);
        Object outcome;
        if (cachedHomeMode == ProtosFunctionCallDispatchers.ReturnHomeMode.UNOBSERVABLE) {
            outcome = ProtosFunctionCallDispatchers.invokeUnobservableHome(
                    frame, inliningTarget, taskPresence, callNode, cachedCallee, callable, arguments, null);
        } else if (cachedHomeMode == ProtosFunctionCallDispatchers.ReturnHomeMode.CAPTURED) {
            outcome = ProtosFunctionCallDispatchers.invokeCapturedHome(
                    frame, inliningTarget, taskPresence, callNode, cachedCallee, callable, arguments, null);
        } else {
            outcome = invoke.execute(
                    frame, inliningTarget, callNode, cachedCallee,
                    callable, arguments, null);
        }
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
     * Once the identity PIC saturates, distinct Closures may still share one
     * prepared execution plan. Cache that plan and its effective target,
     * never a Closure: lexical captures and the ReturnHome are read from the
     * current Closure on every call. A deferred plan, a Context-local
     * projection or a foreign-language plan is never admitted here.
     */
    @ForceQuickening
    @Specialization(
            guards = {
                    "isSourceFunction(callable)",
                    "isSingleContext()",
                    "canonicalCallSelected(callable, rootSelection)",
                    "currentPlan != null",
                    "currentPlan == cachedPlan",
                    "!callable.requiresContextLocalExecutionProjectionForRuntime()",
                    "cachedTarget != null",
                    "currentPlan.bytecodeActivationTargetForComposition() == cachedTarget",
                    "cachedCallee != null"
            },
            assumptions = "cachedCallerNeedsStable",
            excludeForUncached = true,
            limit = "3",
            replaces = "callCachedClosure")
    static Object callPreparedTarget(
            VirtualFrame frame,
            boolean terminal,
            ProtosClosureValue callable,
            Object a0,
            @Bind("$node") Node inliningTarget,
            @Bind("bytecodeOf($node)") BytecodeNode bytecode,
            @Bind("bytecode.getBytecodeIndex(frame)") int bytecodeIndex,
            @Bind("rootOf($node)") ProtosBytecodeRootNode root,
            @Bind("callable.preparedExecutionPlanOrNullForRuntime()")
                    ProtosClosureExecutionPlan currentPlan,
            @Cached(value = "rootCallSelection()", allowUncached = true)
                    ProtosStandardObjectProtocol.CanonicalRootCallSelection rootSelection,
            @Cached("currentPlan") ProtosClosureExecutionPlan cachedPlan,
            @Cached("singleContextTargetOrNull(callable, root)")
                    RootCallTarget cachedTarget,
            @Cached("createDirectCallNodeOrNull(cachedTarget)")
                    DirectCallNode callNode,
            @Cached(value = "calleeRootOrNull(cachedTarget)", adopt = false)
                    ProtosBytecodeRootNode cachedCallee,
            @Cached("cachedCallee.callerNeedsStability()")
                    Assumption cachedCallerNeedsStable,
            @Cached ProtosFunctionCallDispatchers.FunctionDirectInvokeNode invoke,
            @Cached InlinedConditionProfile taskPresence) {
        Object[] arguments = ProtosFrameArguments.dispatchedCall1(
                null, ProtosFrameArguments.functionCallMarker(),
                null, null, null, a0);
        Object outcome = ProtosFunctionCallDispatchers.invokeSharedTarget(
                frame, inliningTarget, taskPresence, invoke, callNode,
                cachedCallee, callable, arguments, null);
        // Compilation-final proof of the shared effective root.
        if (cachedCallee.provablyNonSuspendingBody()) {
            return outcome;
        }
        return ProtosTailCallContinuation.completeCall(
                terminal, outcome, bytecode, bytecodeIndex);
    }

    /*
     * General transition once both direct caches are exhausted. The guard
     * already proves the canonical `call` selection, so the selected source
     * body enters the function dispatcher directly without reselecting.
     */
    @Specialization(
            guards = {
                    "isSourceFunction(callable)",
                    "canonicalCallSelected(callable, rootSelection)"
            },
            replaces = {"callCachedClosure", "callPreparedTarget"})
    @Megamorphic
    @InliningCutoff
    static Object callSourceMegamorphic(
            VirtualFrame frame,
            boolean terminal,
            ProtosClosureValue callable,
            Object a0,
            @Bind("$node") Node inliningTarget,
            @Bind("bytecodeOf($node)") BytecodeNode bytecode,
            @Bind("bytecode.getBytecodeIndex(frame)") int bytecodeIndex,
            @Bind("rootOf($node)") ProtosBytecodeRootNode root,
            @Cached(value = "rootCallSelection()", allowUncached = true)
                    ProtosStandardObjectProtocol.CanonicalRootCallSelection rootSelection,
            @Cached ProtosFunctionCallDispatchers.FunctionCachedCallNode call) {
        Object outcome = call.execute(
                frame, inliningTarget, terminal, callable,
                ProtosFrameArguments.dispatchedCall1(
                        null, ProtosFrameArguments.functionCallMarker(),
                        null, null, null, a0),
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
            @Bind("$node") Node inliningTarget,
            @Bind("bytecodeOf($node)") BytecodeNode bytecode,
            @Bind("bytecode.getBytecodeIndex(frame)") int bytecodeIndex,
            @Bind("rootOf($node)") ProtosBytecodeRootNode root,
            @Cached ProtosFunctionCallDispatchers.FunctionCachedCallNode call) {
        Object[] arguments = ProtosFrameArguments.dispatchedCall1(
                null, ProtosFrameArguments.functionCallMarker(), null, null, null, a0);
        ProtosFrameArguments.bindMethod(arguments, method.receiver(), method.methodHome());
        Object outcome = call.execute(
                frame, inliningTarget, terminal, method.callable(), arguments,
                null, root,
                ProtosFunctionCallDispatchers.currentContext(inliningTarget));
        return ProtosTailCallContinuation.completeCall(
                terminal, outcome, bytecode, bytecodeIndex);
    }

    /* Builtins, bound builtins and generic callables: CallNode. */
    /*
     * Source functions retain the specialized call path while their
     * selected call behavior is canonical. Bound methods are already
     * selected and must not perform another callable lookup.
     */
    static boolean requiresCallableSelection(Object callable) {
        return !isSourceCallable(callable)
                || (callable instanceof ProtosClosureValue closure
                        && !canonicalCallSelected(
                                closure, rootCallSelection()));
    }

    @Specialization(guards = "requiresCallableSelection(callable)")
    @InliningCutoff
    static Object call(
            VirtualFrame frame,
            boolean terminal,
            Object callable,
            Object a0,
            @Bind("bytecodeOf($node)") BytecodeNode bytecode,
            @Bind("bytecode.getBytecodeIndex(frame)") int bytecodeIndex,
            @Bind("rootOf($node)") ProtosBytecodeRootNode root,
            @Cached ProtosFunctionCallDispatchers.CallNode call) {
        Object outcome = call.execute(
                frame, terminal, callable,
                ProtosFrameArguments.dispatchedCall1(
                null, ProtosFrameArguments.functionCallMarker(), null, null, null, a0),
                null, root);
        return ProtosTailCallContinuation.completeCall(
                terminal, outcome, bytecode, bytecodeIndex);
    }

    // A local `call` slot may be added or removed between executions.
    @NonIdempotent
    static boolean canonicalCallSelected(
            ProtosClosureValue callable,
            ProtosStandardObjectProtocol.CanonicalRootCallSelection selection) {
        return ProtosFunctionCallDispatchers.hasCanonicalCallSelection(
                callable, selection);
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
     * AbstractCallMethodNode.isSingleContext:
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

    // Proxy nodes bind through their operation node. The interpreter stores
    // this call site's index in its frame before entering the proxy.
    static BytecodeNode bytecodeOf(Node node) {
        return BytecodeNode.get(node);
    }

    static ProtosBytecodeRootNode rootOf(Node node) {
        return (ProtosBytecodeRootNode) BytecodeNode.get(node).getBytecodeRootNode();
    }
}
