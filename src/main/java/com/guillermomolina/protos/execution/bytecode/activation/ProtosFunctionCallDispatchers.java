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

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosCoreErrors;
import com.guillermomolina.protos.runtime.ProtosNumericValueSupport;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.guillermomolina.protos.runtime.ProtosValueLookup;
import com.guillermomolina.protos.runtime.callables.ProtosNativeClosureBody;
import com.guillermomolina.protos.runtime.control.ProtosReturnHome;
import com.guillermomolina.protos.runtime.lookup.ProtosSlotLookupResult;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.bytecode.BytecodeNode;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.NonIdempotent;
import com.oracle.truffle.api.dsl.ReportPolymorphism.Megamorphic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;

/**
 * I115: the single call dispatch of Protos, copied from GraalPy 25.4.4
 * ({@code CallNode} and {@code CallDispatchers}).
 *
 * <p>Every call enters {@link CallNode} with an already evaluated callable and
 * a B5 argument array whose receiver/methodHome slots ({@code [1]}, {@code
 * [2]}) and positional arguments ({@code [5..]}) were written by the call
 * operation; the dispatcher writes the Closure ({@code [0]}), the caller
 * reference ({@code [3]}) and the ReturnHome ({@code [4]}). That array is the
 * callee frame's arguments: no intermediate array exists.
 *
 * <ul>
 *   <li>{@code functionCall}: a source Closure, selected by representation
 *       only, through {@link FunctionCachedCallNode}. Its local {@code call}
 *       slot is not consulted (owner-approved I115 divergence from D013).
 *   <li>{@code builtinFunctionCall} / {@code builtinMethodCall}: a native
 *       Closure through its Context-local {@link ProtosBuiltinCallRootNode}.
 *   <li>{@code boundMethodCall}: a {@link ProtosBoundMethod} selected by
 *       {@code GetMethod}; its receiver and methodHome are written and its
 *       callable is dispatched.
 *   <li>{@code doGeneric}: any other object; its {@code call} behavior is
 *       selected by ordinary lookup and invoked as a method on the object.
 * </ul>
 *
 * <p>The invoke nodes own no ReturnHome: the callee root captures a
 * non-local return aimed at its own home and closes it when the activation
 * terminates (TruffleRuby RubyMethodRootNode). A transfer leaving the callee
 * is unbridged so it keeps unwinding through the caller root.
 */
final class ProtosFunctionCallDispatchers {
    private ProtosFunctionCallDispatchers() {}

    /** GraalPy {@code CallNode}. */
    @GenerateUncached
    @GenerateInline(false)
    abstract static class CallNode extends Node {

        /**
         * @param frame the caller frame, or {@code null} for a host-side call
         * @param terminal whether a suspended result is returned in a terminal transport
         * @param callable the evaluated callable
         * @param arguments the B5 array described in the class comment
         * @param callerReference the caller's published activation or its own
         *     frame arguments (see {@link #callerReference})
         * @param root the calling Bytecode root, or {@code null} for a host-side call
         */
        abstract Object execute(
                VirtualFrame frame,
                boolean terminal,
                Object callable,
                Object[] arguments,
                Object callerReference,
                ProtosBytecodeRootNode root);

        @Specialization(guards = "isSourceFunction(callable)")
        static Object functionCall(
                VirtualFrame frame,
                boolean terminal,
                ProtosClosureValue callable,
                Object[] arguments,
                Object callerReference,
                ProtosBytecodeRootNode root,
                @Bind("$node") Node inliningTarget,
                @Cached FunctionCachedCallNode cachedCall) {
            return cachedCall.execute(
                    frame, inliningTarget, terminal, callable, arguments,
                    callerReference, root, currentContext(inliningTarget));
        }

        @Specialization(guards = {"isBuiltin(callable)", "isFunctionCall(arguments)"})
        static Object builtinFunctionCall(
                VirtualFrame frame,
                boolean terminal,
                ProtosClosureValue callable,
                Object[] arguments,
                Object callerReference,
                ProtosBytecodeRootNode root,
                @Bind("$node") Node inliningTarget,
                @Cached BuiltinFunctionCachedCallNode cachedCall) {
            return cachedCall.execute(
                    frame, inliningTarget, terminal, callable, arguments,
                    callerReference, currentContext(inliningTarget));
        }

        @Specialization(guards = {"isBuiltin(callable)", "!isFunctionCall(arguments)"})
        static Object builtinMethodCall(
                VirtualFrame frame,
                boolean terminal,
                ProtosClosureValue callable,
                Object[] arguments,
                Object callerReference,
                ProtosBytecodeRootNode root,
                @Bind("$node") Node inliningTarget,
                @Cached BuiltinMethodCachedCallNode cachedCall) {
            return cachedCall.execute(
                    frame, inliningTarget, terminal, callable, arguments,
                    callerReference, currentContext(inliningTarget));
        }

        @Specialization
        static Object boundMethodCall(
                VirtualFrame frame,
                boolean terminal,
                ProtosBoundMethod method,
                Object[] arguments,
                Object callerReference,
                ProtosBytecodeRootNode root,
                @Cached(inline = false) CallNode selectedCall) {
            ProtosFrameArguments.bindMethod(arguments, method.receiver(), method.methodHome());
            return selectedCall.execute(
                    frame, terminal, method.callable(), arguments, callerReference, root);
        }

        /*
         * GraalPy CallNode.doGeneric: the callable behavior of any other
         * object is its ordinary `call` slot, invoked as a method on it.
         */
        @Fallback
        @InliningCutoff
        static Object doGeneric(
                VirtualFrame frame,
                boolean terminal,
                Object callable,
                Object[] arguments,
                Object callerReference,
                ProtosBytecodeRootNode root,
                @Cached(inline = false) CallNode selectedCall) {
            Object receiver = ProtosNumericValueSupport.guestValue(callable);
            ProtosSlotLookupResult selected = selectCall(receiver, callerReference, root);
            ProtosFrameArguments.bindMethod(
                    arguments, receiver, (ProtosObjectValue) selected.home());
            return selectedCall.execute(
                    frame, terminal, selected.value(), arguments, callerReference, root);
        }

        /* Classification only: a source body, whatever the state of its plan. */
        static boolean isSourceFunction(ProtosClosureValue callable) {
            return callable.nativeBody().isEmpty() && callable.definition() != null;
        }

        static boolean isBuiltin(ProtosClosureValue callable) {
            return callable.nativeBody().isPresent();
        }

        static boolean isFunctionCall(Object[] arguments) {
            return ProtosFrameArguments.isFunctionCall(arguments);
        }
    }

    /** GraalPy {@code CallDispatchers.FunctionCachedCallNode}. */
    @GenerateInline
    @GenerateCached(false)
    @GenerateUncached
    abstract static class FunctionCachedCallNode extends Node {

        abstract Object execute(
                VirtualFrame frame,
                Node inliningTarget,
                boolean terminal,
                ProtosClosureValue closure,
                Object[] arguments,
                Object callerReference,
                ProtosBytecodeRootNode root,
                ProtosLanguageContext enteredContext);

        @Specialization(
                guards = {
                    "closure == cachedClosure",
                    "enteredContext == cachedContext",
                    "cachedTarget != null"
                },
                assumptions = {
                    "cachedCodeStable",
                    "cachedProjectionStable"
                },
                limit = "3")
        static Object callFunctionCached(
                VirtualFrame frame,
                Node inliningTarget,
                boolean terminal,
                ProtosClosureValue closure,
                Object[] arguments,
                Object callerReference,
                ProtosBytecodeRootNode root,
                ProtosLanguageContext enteredContext,
                @Cached(value = "closure", weak = true)
                        ProtosClosureValue cachedClosure,
                @Cached("enteredContext") ProtosLanguageContext cachedContext,
                @Cached("cachedClosure.codeStableAssumptionForRuntime()")
                        Assumption cachedCodeStable,
                @Cached("cachedClosure.contextProjectionStabilityForRuntime()")
                        Assumption cachedProjectionStable,
                @Cached("functionCallTarget(cachedClosure, enteredContext)")
                        RootCallTarget cachedTarget,
                @Cached("createDirectCallNodeOrNull(cachedTarget)") DirectCallNode callNode,
                @Cached(value = "calleeRootOrNull(cachedTarget)", adopt = false)
                        ProtosBytecodeRootNode cachedCallee,
                @Cached FunctionDirectInvokeNode invoke) {
            return invoke.execute(
                    frame, inliningTarget, terminal, callNode, cachedCallee,
                    closure, arguments, callerReference, root);
        }

        // PRUIBA DE I115, en algún momento esto volverá a funcionar
        // /*
        //  * Keyed by the effective code root, not by Closure identity: the
        //  * current Closure, with its current captures, travels in the arguments.
        //  */
        // @Specialization(
        //         guards = {
        //             "currentRoot != null",
        //             "currentRoot == cachedRoot"
        //         },
        //         limit = "3",
        //         replaces = "callFunctionCached")
        // static Object callFunctionCachedCt(
        //         VirtualFrame frame,
        //         Node inliningTarget,
        //         boolean terminal,
        //         ProtosClosureValue closure,
        //         Object[] arguments,
        //         Object callerReference,
        //         ProtosBytecodeRootNode root,
        //         ProtosLanguageContext enteredContext,
        //         @Bind("functionRootOrNull(closure, enteredContext)") RootNode currentRoot,
        //         @Cached(value = "currentRoot", adopt = false) RootNode cachedRoot,
        //         @Cached("create(cachedRoot.getCallTarget())") DirectCallNode callNode,
        //         @Cached(value = "calleeRootOrNull(cachedRoot)", adopt = false)
        //                 ProtosBytecodeRootNode cachedCallee,
        //         @Cached FunctionDirectInvokeNode invoke) {
        //     return invoke.execute(
        //             frame, inliningTarget, terminal, callNode, cachedCallee,
        //             closure, arguments, callerReference, root);
        // }

        // PRUIBA DE I115, en algún momento esto volverá a funcionar
        // @Specialization(replaces = {
        //     "callFunctionCached",
        //     "callFunctionCachedCt"
        // })
        @Specialization(replaces = "callFunctionCached")
        @Megamorphic
        @InliningCutoff
        static Object callFunctionMegamorphic(
                VirtualFrame frame,
                Node inliningTarget,
                boolean terminal,
                ProtosClosureValue closure,
                Object[] arguments,
                Object callerReference,
                ProtosBytecodeRootNode root,
                ProtosLanguageContext enteredContext,
                @Cached FunctionIndirectInvokeNode invoke) {
            RootCallTarget target = functionCallTarget(closure, enteredContext);
            if (target == null) {
                // A source Closure has an effective target once its plan exists.
                throw CompilerDirectives.shouldNotReachHere(
                        "source function without an effective CallTarget");
            }
            return invoke.execute(
                    frame, inliningTarget, terminal, target,
                    calleeRootOrNull(target.getRootNode()),
                    closure, arguments, callerReference, root);
        }

        static RootCallTarget functionCallTarget(
                ProtosClosureValue closure,
                ProtosLanguageContext enteredContext) {
            return ProtosClosureCallPreparation.functionCallTarget(
                    closure, enteredContext);
        }

        static DirectCallNode createDirectCallNodeOrNull(RootCallTarget target) {
            return ProtosFunctionCallDispatchers.createDirectCallNodeOrNull(target);
        }

        static ProtosBytecodeRootNode calleeRootOrNull(RootCallTarget target) {
            return ProtosFunctionCallDispatchers.calleeRootOrNull(target);
        }

        static ProtosBytecodeRootNode calleeRootOrNull(RootNode rootNode) {
            return ProtosFunctionCallDispatchers.calleeRootOrNull(rootNode);
        }

        // PRUIBA DE I115, en algún momento esto volverá a funcionar
        // @NonIdempotent
        // static RootNode functionRootOrNull(
        //         ProtosClosureValue closure,
        //         ProtosLanguageContext enteredContext) {
        //     /*
        //      * I115: like GraalPy PFunction.getCode().getRootNode(), read
        //      * the prepared root directly when it already belongs to the
        //      * entered Context. Preserve the general resolution path for
        //      * deferred plans and Context-local projections.
        //      */
        //     if (closure != null
        //             && enteredContext != null
        //             && closure.nativeBody().isEmpty()
        //             && closure.definition() != null) {
        //         ProtosClosureExecutionPlan prepared =
        //                 closure.preparedExecutionPlanOrNullForRuntime();
        //         if (prepared != null
        //                 && !closure.requiresContextLocalExecutionProjectionForRuntime()
        //                 && prepared.language().orElse(null)
        //                         == enteredContext.languageForRuntime()) {
        //             return prepared.bytecodeActivationTargetForComposition()
        //                     .getRootNode();
        //         }
        //     }

        //     RootCallTarget target = ProtosClosureCallPreparation.functionCallTarget(
        //             closure, enteredContext);
        //     return target == null ? null : target.getRootNode();
        // }
    }

    /**
     * GraalPy {@code CallDispatchers.BuiltinFunctionCachedCallNode}: a native
     * Closure called as a function, keyed by the identity of its body.
     */
    @GenerateInline
    @GenerateCached(false)
    @GenerateUncached
    abstract static class BuiltinFunctionCachedCallNode extends Node {

        abstract Object execute(
                VirtualFrame frame,
                Node inliningTarget,
                boolean terminal,
                ProtosClosureValue closure,
                Object[] arguments,
                Object callerReference,
                ProtosLanguageContext enteredContext);

        @Specialization(
                guards = {
                    "nativeBody(closure) == cachedBody",
                    "enteredContext == cachedContext"
                },
                limit = "3")
        static Object callBuiltinCached(
                VirtualFrame frame,
                Node inliningTarget,
                boolean terminal,
                ProtosClosureValue closure,
                Object[] arguments,
                Object callerReference,
                ProtosLanguageContext enteredContext,
                @Cached("nativeBody(closure)") ProtosNativeClosureBody cachedBody,
                @Cached("enteredContext") ProtosLanguageContext cachedContext,
                @Cached("create(builtinCallTarget(cachedBody, enteredContext))")
                        DirectCallNode callNode,
                @Cached FunctionDirectInvokeNode invoke) {
            return invoke.execute(
                    frame, inliningTarget, terminal, callNode, null,
                    closure, arguments, callerReference, null);
        }

        @Specialization(replaces = "callBuiltinCached")
        @Megamorphic
        @InliningCutoff
        static Object callBuiltinMegamorphic(
                VirtualFrame frame,
                Node inliningTarget,
                boolean terminal,
                ProtosClosureValue closure,
                Object[] arguments,
                Object callerReference,
                ProtosLanguageContext enteredContext,
                @Cached FunctionIndirectInvokeNode invoke) {
            return invoke.execute(
                    frame, inliningTarget, terminal,
                    builtinCallTarget(nativeBody(closure), enteredContext), null,
                    closure, arguments, callerReference, null);
        }

        static ProtosNativeClosureBody nativeBody(ProtosClosureValue closure) {
            return ProtosFunctionCallDispatchers.nativeBody(closure);
        }

        static RootCallTarget builtinCallTarget(
                ProtosNativeClosureBody body, ProtosLanguageContext enteredContext) {
            return ProtosFunctionCallDispatchers.builtinCallTarget(body, enteredContext);
        }
    }

    /**
     * GraalPy {@code CallDispatchers.BuiltinMethodCachedCallNode}: a native
     * Closure selected as a method; the receiver and methodHome are already
     * in the arguments.
     */
    @GenerateInline
    @GenerateCached(false)
    @GenerateUncached
    abstract static class BuiltinMethodCachedCallNode extends Node {

        abstract Object execute(
                VirtualFrame frame,
                Node inliningTarget,
                boolean terminal,
                ProtosClosureValue closure,
                Object[] arguments,
                Object callerReference,
                ProtosLanguageContext enteredContext);

        @Specialization(
                guards = {
                    "nativeBody(closure) == cachedBody",
                    "enteredContext == cachedContext"
                },
                limit = "3")
        static Object callBuiltinMethodCached(
                VirtualFrame frame,
                Node inliningTarget,
                boolean terminal,
                ProtosClosureValue closure,
                Object[] arguments,
                Object callerReference,
                ProtosLanguageContext enteredContext,
                @Cached("nativeBody(closure)") ProtosNativeClosureBody cachedBody,
                @Cached("enteredContext") ProtosLanguageContext cachedContext,
                @Cached("create(builtinCallTarget(cachedBody, enteredContext))")
                        DirectCallNode callNode,
                @Cached FunctionDirectInvokeNode invoke) {
            return invoke.execute(
                    frame, inliningTarget, terminal, callNode, null,
                    closure, arguments, callerReference, null);
        }

        @Specialization(replaces = "callBuiltinMethodCached")
        @Megamorphic
        @InliningCutoff
        static Object callBuiltinMethodMegamorphic(
                VirtualFrame frame,
                Node inliningTarget,
                boolean terminal,
                ProtosClosureValue closure,
                Object[] arguments,
                Object callerReference,
                ProtosLanguageContext enteredContext,
                @Cached FunctionIndirectInvokeNode invoke) {
            return invoke.execute(
                    frame, inliningTarget, terminal,
                    builtinCallTarget(nativeBody(closure), enteredContext), null,
                    closure, arguments, callerReference, null);
        }

        static ProtosNativeClosureBody nativeBody(ProtosClosureValue closure) {
            return ProtosFunctionCallDispatchers.nativeBody(closure);
        }

        static RootCallTarget builtinCallTarget(
                ProtosNativeClosureBody body, ProtosLanguageContext enteredContext) {
            return ProtosFunctionCallDispatchers.builtinCallTarget(body, enteredContext);
        }
    }

    /** GraalPy {@code CallDispatchers.FunctionDirectInvokeNode}. */
    @GenerateInline
    @GenerateCached(false)
    @GenerateUncached
    abstract static class FunctionDirectInvokeNode extends Node {

        abstract Object execute(
                VirtualFrame frame,
                Node inliningTarget,
                boolean terminal,
                DirectCallNode callNode,
                ProtosBytecodeRootNode callee,
                ProtosClosureValue closure,
                Object[] arguments,
                Object callerReference,
                ProtosBytecodeRootNode root);

        @Specialization
        static Object invoke(
                VirtualFrame frame,
                Node inliningTarget,
                boolean terminal,
                DirectCallNode callNode,
                ProtosBytecodeRootNode callee,
                ProtosClosureValue closure,
                Object[] arguments,
                Object callerReference,
                ProtosBytecodeRootNode root) {
            prepareArguments(callee, closure, arguments, callerReference, root);
            Object outcome;
            try {
                outcome = callNode.call(arguments);
            } catch (ProtosBytecodeControlTransferException bridged) {
                // Bytecode DSL bridge: the owning root captures its own return.
                throw bridged.transfer();
            }
            /*
             * GraalPy FunctionDirectInvokeNode returns the direct-call
             * outcome. Protos needs terminal suspension transport only
             * when the effective callee body may suspend. The existing
             * whole-root proof includes defaults and effective-body
             * transformations; an unproven callee keeps the general path.
             */
            if (callee != null && callee.provablyNonSuspendingBody()) {
                return outcome;
            }
            return completed(frame, inliningTarget, terminal, outcome);
        }
    }

    /** GraalPy {@code CallDispatchers.FunctionIndirectInvokeNode}. */
    @GenerateInline
    @GenerateCached(false)
    @GenerateUncached
    abstract static class FunctionIndirectInvokeNode extends Node {

        abstract Object execute(
                VirtualFrame frame,
                Node inliningTarget,
                boolean terminal,
                RootCallTarget target,
                ProtosBytecodeRootNode callee,
                ProtosClosureValue closure,
                Object[] arguments,
                Object callerReference,
                ProtosBytecodeRootNode root);

        @Specialization
        static Object invoke(
                VirtualFrame frame,
                Node inliningTarget,
                boolean terminal,
                RootCallTarget target,
                ProtosBytecodeRootNode callee,
                ProtosClosureValue closure,
                Object[] arguments,
                Object callerReference,
                ProtosBytecodeRootNode root,
                @Cached(inline = false) IndirectCallNode callNode) {
            prepareArguments(callee, closure, arguments, callerReference, root);
            Object outcome;
            try {
                outcome = callNode.call(target, arguments);
            } catch (ProtosBytecodeControlTransferException bridged) {
                throw bridged.transfer();
            }
            return completed(frame, inliningTarget, terminal, outcome);
        }
    }

    /*
     * The header slots the dispatcher owns (GraalPy PArguments.setFunctionObject
     * and CallContext.prepareCall): the Closure, the caller reference as is
     * unless the callee registered NEEDS_CALLER_PRELUDE, and the exact
     * ReturnHome: a captured home by identity, the shared marker when the
     * plan proves the home unobservable, otherwise a fresh home this
     * invocation's root owns.
     */
    private static void prepareArguments(
            ProtosBytecodeRootNode callee,
            ProtosClosureValue closure,
            Object[] arguments,
            Object callerReference,
            ProtosBytecodeRootNode root) {
        Object caller =
                callee == null
                        ? callerReference
                        : callerArgument(root, callee, closure, callerReference);
        // PRUIBA DE I115, en algún momento esto volverá a funcionar
        // ProtosReturnHome returnHome =
        //         closure.hasUnobservableInvocationReturnHomeForRuntime()
        //                 ? ProtosReturnHome.unobservableAcceptingPrimitiveResultForRuntime()
        //                 : closure.invocationReturnHomeForRuntime();

        // PRUIBA DE I115, en algún momento esto volverá a funcionar
        // Do not claim primitive-result acceptance for every generic call.
        // Preserve the previous experimental marker for restoration:
        // ProtosReturnHome returnHome =
        //         ProtosReturnHome.unobservableAcceptingPrimitiveResultForRuntime();
        ProtosReturnHome returnHome = ProtosReturnHome.unobservable();
        ProtosFrameArguments.completeDispatchedHeader(arguments, closure, caller, returnHome);
    }

    /*
     * The root epilogue owns guest-boundary projection. A terminal suspended
     * outcome is returned in the terminal transport; a non-terminal outcome
     * is returned unchanged and composed by the caller's continuation loop.
     */
    private static Object completed(
            VirtualFrame frame, Node inliningTarget, boolean terminal, Object outcome) {
        if (terminal && ProtosTailCallContinuation.isSuspended(outcome)) {
            BytecodeNode bytecodeNode =
                    frame == null ? null : BytecodeNode.get(inliningTarget);
            return ProtosTailCallContinuation.deferDirect(
                    outcome,
                    bytecodeNode,
                    bytecodeNode == null ? -1 : bytecodeNode.getBytecodeIndex(frame));
        }
        return outcome;
    }

    /*
     * GraalPy GetMethod for the generic path: the ordinary `call` slot of
     * the receiver; a missing or non-Closure behavior is an Error.
     */
    @TruffleBoundary
    static ProtosSlotLookupResult selectCall(
            Object receiver, Object callerReference, ProtosBytecodeRootNode root) {
        ProtosActivation caller = exactCaller(root, callerReference);
        ProtosPrelude prelude =
                caller.prelude()
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "polymorphic invocation requires an owning Core prelude"));
        ProtosSlotLookupResult selected;
        try {
            selected =
                    ProtosValueLookup.lookup(receiver, "call", prelude)
                            .orElseThrow(
                                    () -> new ProtosSignalException(ProtosCoreErrors.newError(caller)));
        } catch (UnsupportedOperationException unsupportedRepresentation) {
            throw new ProtosSignalException(ProtosCoreErrors.newError(caller));
        }
        if (!(selected.value() instanceof ProtosClosureValue)) {
            throw new ProtosSignalException(ProtosCoreErrors.newError(caller));
        }
        return selected;
    }

    /**
     * Host-side entry (Tasks, callbacks from Java, embedding): the same
     * dispatch through the uncached nodes. {@code caller} is the exact
     * calling activation.
     */
    static Object callUncached(Object callable, Object[] arguments, ProtosActivation caller) {
        return ProtosFunctionCallDispatchersFactory.CallNodeGen.getUncached()
                .execute(null, false, callable, arguments, caller, null);
    }

    /**
     * A one-argument send from a numeric generic route: GetMethod selection
     * and the shared dispatcher. A non-terminal route cannot compose a
     * suspension, which is rejected with an Error.
     */
    @TruffleBoundary
    static Object sendOne(
            ProtosBytecodeRootNode root,
            Object caller,
            boolean terminal,
            Object receiver,
            String selector,
            Object argument) {
        ProtosActivation activation = exactCaller(root, caller);
        Object method =
                ProtosExperimentalGetMethodNode.select(
                        ProtosNumericValueSupport.guestValue(receiver), selector, root, activation);
        Object outcome =
                callFunctionUncached(
                        method,
                        new Object[] {ProtosNumericValueSupport.guestValue(argument)},
                        activation);
        if (ProtosTailCallContinuation.isSuspended(outcome)) {
            if (terminal) {
                return ProtosTailCallContinuation.defer(outcome);
            }
            ProtosTailCallContinuation.abandon(outcome);
            throw ProtosCoreErrors.signal(activation, ProtosCoreErrors.newError(activation));
        }
        return outcome;
    }

    /** {@link #callUncached} for a function call with {@code supplied} positional arguments. */
    static Object callFunctionUncached(
            Object callable, Object[] supplied, ProtosActivation caller) {
        return callUncached(callable, ProtosFrameArguments.functionCallArguments(supplied), caller);
    }

    /** {@link #callUncached} for a method already selected on {@code receiver}. */
    static Object callMethodUncached(
            ProtosClosureValue method,
            Object receiver,
            ProtosObjectValue methodHome,
            Object[] supplied,
            ProtosActivation caller) {
        return callUncached(
                method,
                ProtosFrameArguments.methodCallArguments(receiver, methodHome, supplied),
                caller);
    }

    /**
     * Host-side entry with no calling activation (embedding, tooling): the
     * callee activation is already materialized and published in argument
     * 0, and the same CallTargets as {@link CallNode} are entered.
     */
    @TruffleBoundary
    static Object callPublishedUncached(ProtosClosureValue closure, ProtosActivation activation) {
        ProtosLanguageContext context = ProtosLanguageContext.currentIfEnteredForRuntime();
        if (context == null) {
            throw new IllegalStateException("Closure invocation requires an entered Protos Context");
        }
        try {
            if (closure.nativeBody().isPresent()) {
                return context.builtinCallTarget(nativeBody(closure)).call(activation, closure);
            }
            return functionCallTargetOrFail(closure, context)
                    .call(ProtosFrameArguments.publishedInvocation(activation));
        } catch (ProtosBytecodeControlTransferException bridged) {
            throw bridged.transfer();
        }
    }

    private static RootCallTarget functionCallTargetOrFail(
            ProtosClosureValue closure, ProtosLanguageContext context) {
        RootCallTarget target = ProtosClosureCallPreparation.functionCallTarget(closure, context);
        if (target == null) {
            throw new UnsupportedOperationException(
                    "source-less Closure cannot execute through the Bytecode-only runtime");
        }
        return target;
    }

    /**
     * This root's own caller reference: the published activation, or the
     * still-compact frame arguments.
     */
    @NonIdempotent
    static Object callerReference(VirtualFrame frame) {
        Object[] arguments = frame.getArguments();
        return ProtosFrameArguments.hasPublishedActivation(arguments)
                ? ProtosFrameArguments.publishedActivation(arguments)
                : arguments;
    }

    /**
     * I115 (GraalPy CalleeContext / ReadFrameNode): the exact activation a
     * caller reference denotes. Publishing a still-compact reference is the
     * root's {@link ProtosBytecodeRootNode#NEEDS_EXACT_ACTIVATION} need.
     */
    static ProtosActivation exactCaller(ProtosBytecodeRootNode root, Object caller) {
        if (caller instanceof ProtosActivation activation) {
            return activation;
        }
        if (root != null) {
            root.requestCallerNeed(ProtosBytecodeRootNode.NEEDS_EXACT_ACTIVATION);
        }
        return ProtosFrameArguments.resolveAdmittedCaller(caller);
    }

    /**
     * I115: the prelude of a caller reference by the admitted B5 accessors,
     * without publishing anything; an untransported inherited prelude
     * registers {@code root}'s NEEDS_CALLER_PRELUDE once.
     */
    static ProtosPrelude admittedCallerPrelude(ProtosBytecodeRootNode root, Object caller) {
        if (caller instanceof ProtosActivation activation) {
            return activation.preludeOrNullForRuntime();
        }
        Object[] arguments = (Object[]) caller;
        if (root != null && ProtosFrameArguments.inheritsUntransportedCallerPrelude(arguments)) {
            root.requestCallerNeed(ProtosBytecodeRootNode.NEEDS_CALLER_PRELUDE);
        }
        return ProtosFrameArguments.admittedPreludeOrNull(arguments);
    }

    /** The prelude a caller reference denotes, without publishing anything. */
    static ProtosPrelude callerPrelude(Object caller) {
        if (caller instanceof ProtosActivation activation) {
            return activation.preludeOrNullForRuntime();
        }
        return ProtosFrameArguments.preludeOrNull((Object[]) caller);
    }

    /**
     * I115 (GraalPy CallContext.prepareCall): the B5 caller slot passed to
     * {@code callee}: the reference unchanged unless the callee requested the
     * caller prelude, which then travels in a ProtosCallerFrameReference.
     */
    static Object callerArgument(
            ProtosBytecodeRootNode root,
            ProtosBytecodeRootNode callee,
            ProtosClosureValue closure,
            Object caller) {
        if (!callee.hasCallerNeed(ProtosBytecodeRootNode.NEEDS_CALLER_PRELUDE)
                || !(caller instanceof Object[] callerArguments)
                || closure.preludeOrNullForRuntime() != null) {
            return caller;
        }
        return ProtosFrameArguments.callerFrameReference(
                callerArguments, admittedCallerPrelude(root, caller));
    }

    static ProtosNativeClosureBody nativeBody(ProtosClosureValue closure) {
        return closure.nativeBody().orElseThrow();
    }

    static RootCallTarget builtinCallTarget(
            ProtosNativeClosureBody body, ProtosLanguageContext enteredContext) {
        return enteredContext.builtinCallTarget(body);
    }

    @NonIdempotent
    static ProtosLanguageContext currentContext(Node node) {
        ProtosLanguageContext context = ProtosLanguageContext.current(node);
        if (context == null) {
            throw CompilerDirectives.shouldNotReachHere("call dispatch requires an entered Context");
        }
        return context;
    }

    static DirectCallNode createDirectCallNodeOrNull(RootCallTarget target) {
        return target == null ? null : DirectCallNode.create(target);
    }

    static ProtosBytecodeRootNode calleeRootOrNull(RootCallTarget target) {
        return target == null ? null : calleeRootOrNull(target.getRootNode());
    }

    static ProtosBytecodeRootNode calleeRootOrNull(RootNode rootNode) {
        return rootNode instanceof ProtosBytecodeRootNode callee ? callee : null;
    }
}
