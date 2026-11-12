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

/**
 * Protos Bytecode DSL: the {@code CallUnaryMethod} operation, a call
 * of an evaluated callable with 1 positional argument. The callable is a source Closure, a
 * native Closure, a {@link ProtosBoundMethod} produced by {@code GetMethod}
 * (its receiver is not counted among the arguments) or any other object.
 *
 * <p>The B5 frame-argument array is built once here and becomes the callee
 * frame's arguments. Source functions use a identity-keyed
 * unary direct-call cache before entering the existing call dispatcher.
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
     * CallUnaryMethodNode.callPFunction: cache the callable
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
    static Object callPFunction(
            VirtualFrame frame,
            boolean terminal,
            ProtosClosureValue callable,
            Object a0,
            @Bind("$node") Node inliningTarget,
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
            @Cached ProtosFunctionCallDispatchers.FunctionDirectInvokeNode invoke) {
        Object[] arguments = ProtosFrameArguments.dispatchedCall1(
                null, ProtosFrameArguments.functionCallMarker(),
                null, null, null, a0);
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

    /*
     * megamorphic transition for source functions.
     * Reuse the existing CallNode and its current dispatch implementation.
     */
    @Specialization(
            guards = {
                    "isSourceFunction(callable)",
                    "canonicalCallSelected(callable, rootSelection)"
            },
            replaces = "callPFunction")
    @Megamorphic
    @InliningCutoff
    static Object callSourceMegamorphic(
            VirtualFrame frame,
            boolean terminal,
            ProtosClosureValue callable,
            Object a0,
            @Bind("rootOf($node)") ProtosBytecodeRootNode root,
            @Cached(value = "rootCallSelection()", allowUncached = true)
                    ProtosStandardObjectProtocol.CanonicalRootCallSelection rootSelection,
            @Cached ProtosFunctionCallDispatchers.CallNode call) {
        return call.execute(
                frame, terminal, callable,
                ProtosFrameArguments.dispatchedCall1(
                        null, ProtosFrameArguments.functionCallMarker(),
                        null, null, null, a0),
                null, root);
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
                null, root,
                ProtosFunctionCallDispatchers.currentContext(inliningTarget));
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
            @Bind("rootOf($node)") ProtosBytecodeRootNode root,
            @Cached ProtosFunctionCallDispatchers.CallNode call) {
        return call.execute(
                frame, terminal, callable,
                ProtosFrameArguments.dispatchedCall1(
                null, ProtosFrameArguments.functionCallMarker(), null, null, null, a0),
                null, root);
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

    static ProtosBytecodeRootNode rootOf(Node node) {
        return (ProtosBytecodeRootNode) BytecodeNode.get(node).getBytecodeRootNode();
    }
}
