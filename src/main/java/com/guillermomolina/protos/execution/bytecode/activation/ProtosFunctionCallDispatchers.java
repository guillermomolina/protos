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
import com.guillermomolina.protos.runtime.control.ProtosReturnHome;
import com.guillermomolina.protos.runtime.control.ProtosNonLocalReturnException;
import com.guillermomolina.protos.runtime.ProtosTask;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosCoreErrors;
import com.guillermomolina.protos.runtime.ProtosNumericValueSupport;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.guillermomolina.protos.runtime.ProtosValueLookup;
import com.guillermomolina.protos.runtime.callables.ProtosNativeClosureBody;
import com.guillermomolina.protos.runtime.lookup.ProtosSlotLookupResult;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Idempotent;
import com.oracle.truffle.api.dsl.NonIdempotent;
import com.oracle.truffle.api.dsl.ReportPolymorphism.Megamorphic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ControlFlowException;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.object.PropertyGetter;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;

/**
 * The single callable dispatcher of Protos
 * ({@code CallNode} and {@code CallDispatchers}).
 *
 * <p>Every call enters {@link CallNode} with an already evaluated callable and
 * a B5 argument array whose receiver/methodHome slots ({@code [1]}, {@code
 * [2]}) and positional arguments ({@code [5..]}) were written by the call
 * operation; the dispatcher writes the Closure ({@code [0]}), the caller
 * reference ({@code [3]}) and the exact invocation home ({@code [4]})
 * on direct and indirect calls. That array is the callee frame's
 * arguments: no intermediate array exists.
 *
 * <ul>
 *   <li>{@code functionCall}: a source Closure, selected by representation
 *       only, through {@link FunctionCachedCallNode}. Its local {@code call}
 *       selection is checked before direct execution.
 *   <li>{@code builtinFunctionCall} / {@code builtinMethodCall}: a native
 *       Closure through its Context-local {@link ProtosBuiltinCallRootNode}.
 *   <li>{@code boundMethodCall}: a {@link ProtosBoundMethod} selected by
 *       {@code GetMethod}; its receiver and methodHome are written and its
 *       callable is dispatched.
 *   <li>{@code doGeneric}: any other object; its {@code call} behavior is
 *       selected by ordinary lookup and invoked as a method on the object.
 * </ul>
 *
 * <p>Direct and indirect invocations preserve their result representation.
 * The owning bytecode call operation attaches terminal suspension provenance;
 * numeric carriers are projected only at the actual consumer boundary.
 */
final class ProtosFunctionCallDispatchers {
    private ProtosFunctionCallDispatchers() {}

    /*
     * A source function may bypass method selection only while ordinary
     * `call` lookup would select the standard Object.call behavior: the
     * inherited canonical selection, or a nearer local slot holding that same
     * standard implementation, whose Closure branch executes this Closure's
     * own body. Dispatch is keyed by the selected behavior's identity, not by
     * the object that owns it. An already-selected method has a separate
     * invocation contract.
     */
    static boolean hasCanonicalCallSelection(
            ProtosClosureValue closure,
            ProtosStandardObjectProtocol.CanonicalRootCallSelection selection) {
        return selection != null
                && selection.isValid()
                && (!closure.hasLocalCallSlotForRuntime()
                        || localCallIsStandardCall(closure));
    }

    /** The ordinary slot name whose selection defines invocation. */
    static final String CALL_SLOT_NAME = "call";

    /**
     * The property getter for the local {@code call} slot of {@code shape},
     * or {@code null} when that Shape has no local {@code call} property.
     */
    public static PropertyGetter callSlotGetter(Shape shape) {
        return ProtosValueLookup.propertyGetterWithFinalAssumption(
                shape, CALL_SLOT_NAME);
    }

    /**
     * The canonical selection test specialized on a call-site cached
     * {@link Shape} and its cached {@link PropertyGetter} for {@code call}
     * ({@code null} when that Shape has no local {@code call} property).
     *
     * <p>A Closure always stores its slots as properties of its own object,
     * so its exact Shape proves which local slots exist: a cached Shape
     * without {@code call} proves the absence of a local {@code call}, and
     * selection is then the inherited root selection, admitted only while it
     * remains valid. A cached Shape with {@code call} obliges reading the
     * slot's current value on every execution, because assignment may change
     * it without a Shape change; only the standard implementation is
     * admitted. A different Shape rejects the cached entry, and the getter,
     * made from the exact cached Shape, is used only after
     * {@link Shape#check} succeeds. Every rejection falls back to ordinary
     * selection. Execution-context values are not Closures and are never
     * admitted here.
     */
    static boolean hasCanonicalCallSelectionForShape(
            ProtosClosureValue closure,
            ProtosStandardObjectProtocol.CanonicalRootCallSelection selection,
            Shape cachedShape,
            PropertyGetter cachedLocalCallGetter) {
        if (selection == null || !selection.isValid()) {
            return false;
        }
        if (!cachedShape.check(closure)) {
            return false;
        }
        if (cachedLocalCallGetter == null) {
            return true;
        }
        Object localCall = cachedLocalCallGetter.get(closure);
        return localCall instanceof ProtosClosureValue selected
                && ProtosStandardObjectProtocol
                        .isStandardCallImplementation(selected);
    }

    @TruffleBoundary
    private static boolean localCallIsStandardCall(ProtosClosureValue closure) {
        return closure.readLocalSlot("call").orElse(null)
                        instanceof ProtosClosureValue local
                && ProtosStandardObjectProtocol.isStandardCallImplementation(local);
    }


    /** {@code CallNode}. */
    @GenerateUncached
    @GenerateInline(false)
    abstract static class CallNode extends Node {

        static PropertyGetter callSlotGetter(Shape shape) {
            return ProtosFunctionCallDispatchers.callSlotGetter(shape);
        }

    static ProtosStandardObjectProtocol.CanonicalRootCallSelection
            rootCallSelection() {
        return ProtosStandardObjectProtocol
                .canonicalRootCallSelectionOrNullForRuntime();
    }

        // The cached Shape proves only slot presence; a present `call` slot
        // may be reassigned, so its value is read on every execution.
        @NonIdempotent
        static boolean hasCanonicalCallSelectionForShape(
                ProtosClosureValue closure,
                ProtosStandardObjectProtocol.CanonicalRootCallSelection
                        selection,
                Shape cachedShape,
                PropertyGetter cachedLocalCallGetter) {
            return ProtosFunctionCallDispatchers
                    .hasCanonicalCallSelectionForShape(
                            closure, selection, cachedShape,
                            cachedLocalCallGetter);
        }

        /**
         * @param frame the caller frame, or {@code null} for a host-side call
         * @param terminal whether a suspended result is returned in a terminal transport
         * @param callable the evaluated callable
         * @param arguments the B5 array described in the class comment
         * @param callerReference optional explicit host/native caller;
         *     source call sites supply null
         * @param root the calling Bytecode root, or {@code null} for a host-side call
         */
        abstract Object execute(
                VirtualFrame frame,
                boolean terminal,
                Object callable,
                Object[] arguments,
                Object callerReference,
                ProtosBytecodeRootNode root);

        @Specialization(limit = "3", guards = {
                "isSourceFunction(callable)",
                "!isFunctionCall(arguments) || hasCanonicalCallSelectionForShape(callable, rootSelection, cachedShape, cachedLocalCallGetter)"
        })
        static Object functionCall(
                VirtualFrame frame,
                boolean terminal,
                ProtosClosureValue callable,
                Object[] arguments,
                Object callerReference,
                ProtosBytecodeRootNode root,
                @Bind Node inliningTarget,
                @Exclusive @Cached(value = "rootCallSelection()", allowUncached = true)
                    ProtosStandardObjectProtocol.CanonicalRootCallSelection rootSelection,
                @Exclusive @Cached(value = "callable.getShape()", allowUncached = true)
                    Shape cachedShape,
                @Exclusive @Cached(
                        value = "callSlotGetter(cachedShape)",
                        allowUncached = true,
                        neverDefault = false)
                    PropertyGetter cachedLocalCallGetter,
                @Cached FunctionCachedCallNode cachedCall) {
            return cachedCall.execute(
                    frame, inliningTarget, terminal, callable, arguments,
                    callerReference, root, currentContext(inliningTarget));
        }

        @Specialization(limit = "3", guards = {
                "isBuiltin(callable)",
                "isFunctionCall(arguments)",
                "hasCanonicalCallSelectionForShape(callable, rootSelection, cachedShape, cachedLocalCallGetter)"
        })
        static Object builtinFunctionCall(
                VirtualFrame frame,
                boolean terminal,
                ProtosClosureValue callable,
                Object[] arguments,
                Object callerReference,
                ProtosBytecodeRootNode root,
                @Bind Node inliningTarget,
                @Exclusive @Cached(value = "rootCallSelection()", allowUncached = true)
                    ProtosStandardObjectProtocol.CanonicalRootCallSelection rootSelection,
                @Exclusive @Cached(value = "callable.getShape()", allowUncached = true)
                    Shape cachedShape,
                @Exclusive @Cached(
                        value = "callSlotGetter(cachedShape)",
                        allowUncached = true,
                        neverDefault = false)
                    PropertyGetter cachedLocalCallGetter,
                @Cached BuiltinFunctionCachedCallNode cachedCall) {
            return cachedCall.execute(
                    frame, inliningTarget, terminal, callable, arguments,
                    frame == null
                            ? callerReference
                            : ProtosFrameArguments.activation(frame),
                    currentContext(inliningTarget));
        }

        @Specialization(guards = {"isBuiltin(callable)", "!isFunctionCall(arguments)"})
        static Object builtinMethodCall(
                VirtualFrame frame,
                boolean terminal,
                ProtosClosureValue callable,
                Object[] arguments,
                Object callerReference,
                ProtosBytecodeRootNode root,
                @Bind Node inliningTarget,
                @Cached BuiltinMethodCachedCallNode cachedCall) {
            return cachedCall.execute(
                    frame, inliningTarget, terminal, callable, arguments,
                    frame == null || callerReference instanceof ProtosActivation
                            ? callerReference
                            : ProtosStandardIntegerProtocol.isCanonicalArithmetic(callable)
                                    ? ProtosFrameArguments.admittedPreludeOrNull(frame.getArguments())
                                    : ProtosFrameArguments.activation(frame),
                    currentContext(inliningTarget));
        }

        @Specialization
        static Object boundMethodCall(
                VirtualFrame frame,
                boolean terminal,
                ProtosBoundMethod method,
                Object[] arguments,
                Object callerReference,
                ProtosBytecodeRootNode root,
                @Exclusive @Cached CallNode selectedCall) {
            ProtosFrameArguments.bindMethod(arguments, method.receiver(), method.methodHome());
            return selectedCall.execute(
                    frame, terminal, method.callable(), arguments, callerReference, root);
        }

        /*
         * Generic callable selection: the callable behavior of any other
         * object, or of a Closure whose `call` selection is not canonical,
         * is its ordinary `call` slot, invoked as a method on it. A selected
         * method already has its receiver and methodHome and never reaches
         * this path. Declared last and unguarded: the ordinary selection
         * is also correct for a canonical Closure past the Shape-keyed
         * function caches.
         */
        @Specialization
        @InliningCutoff
        static Object doGeneric(
                VirtualFrame frame,
                boolean terminal,
                Object callable,
                Object[] arguments,
                Object callerReference,
                ProtosBytecodeRootNode root,
                @Exclusive @Cached CallNode selectedCall) {
            Object receiver = ProtosNumericValueSupport.guestValue(callable);
            ProtosSlotLookupResult selected = selectCall(
                    receiver,
                    frame == null ? callerReference : frame.getArguments(),
                    root);
            ProtosFrameArguments.bindMethod(
                    arguments, receiver, (ProtosObjectValue) selected.home());
            return selectedCall.execute(
                    frame, terminal, selected.value(), arguments, callerReference, root);
        }

        /* Classification only: a source body, whatever the state of its plan. */
        static boolean isSourceFunction(ProtosClosureValue callable) {
            return !callable.hasNativeBody() && callable.definition() != null;
        }

        static boolean isBuiltin(ProtosClosureValue callable) {
            return callable.hasNativeBody();
        }

        static boolean isFunctionCall(Object[] arguments) {
            return ProtosFrameArguments.isFunctionCall(arguments);
        }
    }

    /**
     * D237 / cold name-bearing entry to the shared dispatcher.
     *
     * The caller's source expressions and spreads have already been
     * evaluated. Selection uses the actual callable, then association
     * uses the selected implementation's declared source formals.
     *
     * Positional-only Call*Method sites never enter this route.
     */
    @TruffleBoundary
    static Object callNamed(
            Object callable,
            ProtosPreparedCalls.PreparedArgumentVector vector,
            ProtosActivation caller) {
        java.util.Objects.requireNonNull(vector, "prepared arguments");
        // The immutable packet is materialized once, inside this boundary.
        ProtosPreparedCalls.NamedArgumentPacket packet = vector.namedPacket();
        return callNamedPacket(callable, packet, caller);
    }

    /**
     * Shared selection and association for an already materialized
     * named packet; also re-entered for standard call/construct/init
     * forwarding.
     */
    private static Object callNamedPacket(
            Object callable,
            ProtosPreparedCalls.NamedArgumentPacket packet,
            ProtosActivation caller) {
        java.util.Objects.requireNonNull(packet, "named packet");
        java.util.Objects.requireNonNull(caller, "caller");

        if (callable instanceof ProtosBoundMethod bound) {
            return invokeSelectedNamed(
                    bound.callable(),
                    bound.receiver(),
                    bound.methodHome(),
                    packet,
                    caller);
        }

        // D237: a name-bearing invocation selects the actual D013
        // `call` behavior even when its receiver is a Closure.
        // Standard Object.call subsequently forwards directly to
        // that Closure, without repeating the lookup.
        if (callable instanceof ProtosClosureValue closure) {
            ProtosSlotLookupResult selected =
                    selectCall(closure, caller, null);

            return invokeSelectedNamed(
                    (ProtosClosureValue) selected.value(),
                    closure,
                    selected.home(),
                    packet,
                    caller);
        }

        // All other values use ordinary dynamic `call` lookup.
        Object receiver = ProtosNumericValueSupport.guestValue(callable);
        ProtosSlotLookupResult selected =
                selectCall(receiver, caller, null);

        return invokeSelectedNamed(
                (ProtosClosureValue) selected.value(),
                receiver,
                selected.home(),
                packet,
                caller);
    }

    /**
     * Invoke the exact selected implementation with the P,K packet.
     *
     * Only the actual executable standard Object.call/construct bodies
     * forward names transparently. Other native implementations have
     * no declared Protos named signature and reject the packet.
     */
    private static Object invokeSelectedNamed(
            ProtosClosureValue selected,
            Object receiver,
            ProtosObjectValue methodHome,
            ProtosPreparedCalls.NamedArgumentPacket packet,
            ProtosActivation caller) {
        ProtosNamedArgumentAssociation.SelectedBinding binding =
                ProtosNamedArgumentAssociation.classifySelectedImplementation(
                        selected,
                        packet.positional(),
                        packet.named());

        if (binding == null) {
            throw new ProtosSignalException(
                    ProtosCoreErrors.newError(caller));
        }

        if (ProtosStandardObjectProtocol
                .isStandardCallImplementation(selected)) {

            if (receiver instanceof ProtosClosureValue closure) {
                // Standard Object.call executes the receiver Closure
                // directly, retaining its captured receiver/home.
                return invokeSelectedNamed(
                        closure,
                        closure.capturedReceiver(),
                        closure.methodHome().orElse(null),
                        packet,
                        caller);
            }

            if (!ProtosStandardObjectProtocol
                    .isOrdinaryConstructionReceiver(receiver)) {
                throw new ProtosSignalException(
                        ProtosCoreErrors.newError(caller));
            }

            // D239-C1: ordinary construct lookup on the original
            // receiver, after all source arguments were evaluated.
            Object construct =
                    ProtosExperimentalGetMethodNode.select(
                            receiver, "construct", null, caller);

            return callNamedPacket(construct, packet, caller);
        }

        if (ProtosStandardObjectProtocol
                .isStandardConstructImplementation(selected)) {

            if (!ProtosStandardObjectProtocol
                    .isOrdinaryConstructionReceiver(receiver)) {
                throw new ProtosSignalException(
                        ProtosCoreErrors.newError(caller));
            }

            ProtosObjectValue instance =
                    new ProtosObjectValue((ProtosObjectValue) receiver);

            // D239-C1: select init on the fresh child, preserving
            // its actual receiver and methodHome.
            Object initializer =
                    ProtosExperimentalGetMethodNode.select(
                            instance, "init", null, caller);

            Object initialized =
                    callNamedPacket(initializer, packet, caller);

            // A suspended init must finish before construct returns.
            // Its normal return value is ignored; the instance wins.
            return ProtosTailCallContinuation.afterSuccessfulChild(
                    initialized, instance);
        }

        if (!(binding instanceof
                ProtosNamedArgumentAssociation.SourceBinding source)) {
            throw new IllegalStateException(
                    "named source binding classification was not consumed");
        }

        ProtosNamedArgumentAssociation.Association association =
                source.association();

        java.util.List<Object> supplied =
                association.boundSuppliedArgumentsForRuntime();

        ProtosActivation activation;
        if (methodHome == null) {
            activation = ProtosActivation.forClosureInvocation(
                    selected,
                    supplied,
                    caller.prelude().orElse(null),
                    caller.actorModuleState(),
                    caller.currentModuleKey().orElse(null),
                    caller.executionDomain());
        } else {
            activation = ProtosActivation.forImmediateMethodInvocation(
                    selected,
                    supplied,
                    receiver,
                    methodHome,
                    caller.prelude().orElse(null),
                    caller.actorModuleState(),
                    caller.currentModuleKey().orElse(null),
                    caller.executionDomain());
        }

        activation.installNamedParameterPresenceForRuntime(
                association.parameterPresenceForRuntime());

        if (caller.task().isPresent()) {
            activation.attachTask(caller.task().orElseThrow());
        } else {
            activation.inheritDynamicControlState(caller);
        }

        // The same Context-local source CallTarget used by ,
        // entered with an already published exact activation.
        return callPublishedUncached(selected, activation);
    }

    /**
     * Execution of a Closure whose own body is already the selected behavior:
     * its function CallTarget is invoked directly and {@code call} is never
     * looked up again.
     */
    @GenerateUncached
    @GenerateInline(false)
    abstract static class SelectedBodyCallNode extends Node {

        abstract Object execute(
                VirtualFrame frame,
                boolean terminal,
                ProtosClosureValue closure,
                Object[] arguments,
                Object callerReference,
                ProtosBytecodeRootNode root);

        @Specialization(guards = "isSourceFunction(closure)")
        static Object sourceBody(
                VirtualFrame frame,
                boolean terminal,
                ProtosClosureValue closure,
                Object[] arguments,
                Object callerReference,
                ProtosBytecodeRootNode root,
                @Bind Node inliningTarget,
                @Cached FunctionCachedCallNode cachedCall) {
            return cachedCall.execute(
                    frame, inliningTarget, terminal, closure, arguments,
                    callerReference, root, currentContext(inliningTarget));
        }

        @Specialization(guards = "isBuiltin(closure)")
        static Object builtinBody(
                VirtualFrame frame,
                boolean terminal,
                ProtosClosureValue closure,
                Object[] arguments,
                Object callerReference,
                ProtosBytecodeRootNode root,
                @Bind Node inliningTarget,
                @Cached BuiltinFunctionCachedCallNode cachedCall) {
            return cachedCall.execute(
                    frame, inliningTarget, terminal, closure, arguments,
                    frame == null
                            ? callerReference
                            : ProtosFrameArguments.activation(frame),
                    currentContext(inliningTarget));
        }

        static boolean isSourceFunction(ProtosClosureValue closure) {
            return CallNode.isSourceFunction(closure);
        }

        static boolean isBuiltin(ProtosClosureValue closure) {
            return CallNode.isBuiltin(closure);
        }
    }

    /** Host-side execution of an already selected Closure body. */
    static Object callSelectedBodyUncached(
            ProtosClosureValue closure, Object[] arguments, ProtosActivation caller) {
        Object outcome = ProtosFunctionCallDispatchersFactory.SelectedBodyCallNodeGen.getUncached()
                .execute(null, false, closure, arguments, caller, null);
        return projectForCaller(outcome, false);
    }

    /** {@code CallDispatchers.FunctionCachedCallNode}. */
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
                    "cachedProjectionStable",
                    "cachedCallerNeedsStable"
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
                @Cached(value = "enteredContext", weak = true)
                        ProtosLanguageContext cachedContext,
                @Cached("cachedClosure.codeStableAssumptionForRuntime()")
                        Assumption cachedCodeStable,
                @Cached("cachedClosure.contextProjectionStabilityForRuntime()")
                        Assumption cachedProjectionStable,
                @Cached("functionCallTarget(cachedClosure, enteredContext)")
                        RootCallTarget cachedTarget,
                @Cached("createDirectCallNodeOrNull(cachedTarget)") DirectCallNode callNode,
                @Cached(value = "calleeRootOrNull(cachedTarget)", adopt = false)
                        ProtosBytecodeRootNode cachedCallee,
                @Cached("cachedCallee.callerNeedsStability()")
                        Assumption cachedCallerNeedsStable,
                @Cached("classifyReturnHome(cachedClosure, cachedCallee)")
                        ReturnHomeMode cachedHomeMode,
                @Exclusive @Cached FunctionDirectInvokeNode invoke,
                @Exclusive @Cached InlinedConditionProfile taskPresence,
                @Exclusive @Cached InlinedConditionProfile explicitTaskPresence,
                @Exclusive @Cached InlinedConditionProfile publishedActivationPresence,
                @Exclusive @Cached InlinedConditionProfile closureHeaderPresence,
                @Exclusive @Cached InlinedConditionProfile callerActivationPresence,
                @Exclusive @Cached InlinedConditionProfile suspensionPresence) {
            return invokeCachedClosure(
                    frame, inliningTarget, cachedHomeMode, taskPresence, explicitTaskPresence,
                    publishedActivationPresence, closureHeaderPresence,
                    callerActivationPresence, suspensionPresence, invoke,
                    callNode, cachedCallee, closure, arguments, callerReference);
        }

        static ReturnHomeMode classifyReturnHome(
                ProtosClosureValue closure, ProtosBytecodeRootNode effectiveRoot) {
            return ProtosFunctionCallDispatchers.classifyReturnHome(closure, effectiveRoot);
        }

        /*
         * Once the closure-identity PIC saturates, different Closure values
         * may still share a prepared executable target. Cache that target,
         * never the Closure (and therefore never its lexical captures).
         *
         * A deferred plan or Context-local projection must go through the
         * general resolver; a plan from another language/Context must not be
         * entered by this direct node. No rematerialization runs in a guard.
         *
         * Closures sharing this target may capture different homes,
         * so no ReturnHome mode is cached here; the general invocation
         * selects the home per call.
         */
        @Specialization(
                guards = {
                    "currentPlan != null",
                    "enteredContext == cachedContext",
                    "!closure.requiresContextLocalExecutionProjectionForRuntime()",
                    "samePreparedPlanLanguage(currentPlan, enteredContext)",
                    "cachedTarget != null",
                    "currentPlan.bytecodeActivationTargetForComposition() == cachedTarget"
                },
                assumptions = "cachedCallerNeedsStable",
                limit = "3",
                replaces = "callFunctionCached")
        static Object callFunctionCachedCt(
                VirtualFrame frame,
                Node inliningTarget,
                boolean terminal,
                ProtosClosureValue closure,
                Object[] arguments,
                Object callerReference,
                ProtosBytecodeRootNode root,
                ProtosLanguageContext enteredContext,
                @Bind("closure.preparedExecutionPlanOrNullForRuntime()")
                        ProtosClosureExecutionPlan currentPlan,
                @Cached(value = "enteredContext", weak = true)
                        ProtosLanguageContext cachedContext,
                @Cached("currentPlan.bytecodeActivationTargetForComposition()")
                        RootCallTarget cachedTarget,
                @Cached("createDirectCallNodeOrNull(cachedTarget)") DirectCallNode callNode,
                @Cached(value = "calleeRootOrNull(cachedTarget)", adopt = false)
                        ProtosBytecodeRootNode cachedCallee,
                @Cached("cachedCallee.callerNeedsStability()")
                        Assumption cachedCallerNeedsStable,
                @Exclusive @Cached FunctionDirectInvokeNode invoke,
                @Exclusive @Cached InlinedConditionProfile taskPresence,
                @Exclusive @Cached InlinedConditionProfile explicitTaskPresence,
                @Exclusive @Cached InlinedConditionProfile publishedActivationPresence,
                @Exclusive @Cached InlinedConditionProfile closureHeaderPresence,
                @Exclusive @Cached InlinedConditionProfile callerActivationPresence,
                @Exclusive @Cached InlinedConditionProfile suspensionPresence) {
            return invokeSharedTarget(
                    frame, inliningTarget, taskPresence, explicitTaskPresence,
                    publishedActivationPresence, closureHeaderPresence,
                    callerActivationPresence, suspensionPresence, invoke, callNode,
                    cachedCallee, closure, arguments, callerReference);
        }

        /* Last resort: different effective targets beyond the secondary PIC. */
        @Specialization(replaces = {"callFunctionCached", "callFunctionCachedCt"})
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
                    frame, inliningTarget, target,
                    calleeRootOrNull(target.getRootNode()),
                    closure, arguments, callerReference);
        }

        static boolean samePreparedPlanLanguage(
                ProtosClosureExecutionPlan plan,
                ProtosLanguageContext context) {
            return plan != null
                    && context != null
                    && plan.language().orElse(null)
                            == context.languageForRuntime();
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

    }

    /**
     * {@code CallDispatchers.BuiltinFunctionCachedCallNode}: a native
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
                    frame, inliningTarget, callNode, null,
                    closure, arguments, callerReference);
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
                    frame, inliningTarget,
                    builtinCallTarget(nativeBody(closure), enteredContext), null,
                    closure, arguments, callerReference);
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
     * {@code CallDispatchers.BuiltinMethodCachedCallNode}: a native
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
                    frame, inliningTarget, callNode, null,
                    closure, arguments, callerReference);
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
                    frame, inliningTarget,
                    builtinCallTarget(nativeBody(closure), enteredContext), null,
                    closure, arguments, callerReference);
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
     * select only the caller state requested by the effective
     * callee root. The ordinary source-call case transports null.
     *
     * NEEDS_CALLER_PRELUDE transports a Prelude; NEEDS_EXACT_ACTIVATION
     * transports the exact Activation. Callee slot 3 never contains arrays.
     * Frame-null host entry retains its explicit caller, as does entry
     * without a source Bytecode callee root.
     *
     * A selected source Closure without its own Prelude inherits the
     * observing caller's admitted Prelude. It is transported even before
     * the effective root has registered NEEDS_CALLER_PRELUDE, so the first
     * invocation never depends on an older physical caller frame (a resumed
     * caller has none). Nothing is published to obtain it.
     */
    private static Object prepareCallerForCallee(
            VirtualFrame frame,
            ProtosBytecodeRootNode callee,
            ProtosClosureValue closure,
            Object explicitCaller) {

        if (frame == null || callee == null) {
            return explicitCaller;
        }

        /*
         * A structured call request (CallRequest.perform) supplies its
         * authoritative caller explicitly. The current frame may be a
         * continuation or resumer frame, never a substitute for it.
         */
        if (explicitCaller instanceof ProtosActivation) {
            return explicitCaller;
        }

        if (callee.hasCallerNeed(
                ProtosBytecodeRootNode.NEEDS_EXACT_ACTIVATION)) {
            return ProtosFrameArguments.activation(frame);
        }

        if (callee.hasCallerNeed(
                ProtosBytecodeRootNode.NEEDS_CALLER_PRELUDE)
                || inheritsCallerPrelude(closure)) {
            return ProtosFrameArguments.admittedPreludeOrNull(
                    frame.getArguments());
        }

        return null;
    }

    /** The selected source Closure owns no Prelude, so it inherits its caller's. */
    private static boolean inheritsCallerPrelude(ProtosClosureValue closure) {
        return closure != null && closure.preludeOrNullForRuntime() == null;
    }

    /** Select from the actual executable, never a cached Closure's plan. */
    private static ProtosReturnHome invocationHome(
            ProtosClosureValue closure, ProtosBytecodeRootNode effectiveRoot) {
        if (closure.capturedReturnHomeOrNullForRuntime() == null
                && closure.hasNativeBody()
                && ProtosStandardIntegerProtocol.isCanonicalArithmetic(closure)) {
            return ProtosReturnHome.unobservable();
        }
        return closure.invocationReturnHomeUnderTemplateProofForRuntime(
                !closure.hasNativeBody()
                        && effectiveRoot != null
                        && effectiveRoot.returnHomeProvenUnobservable());
    }

    /**
     * how the identity PIC entry of one exact Closure and its
     * effective callee root treats the invocation's ReturnHome. Only stable
     * data decides it: the Closure's final captured home and the proof the
     * lowering recorded once on the effective root. It is never derived from
     * the Task, the caller or the lifecycle state of a home.
     */
    enum ReturnHomeMode {
        /** The shared non-materialized marker; no lifecycle, no transfer target. */
        UNOBSERVABLE,
        /** A home captured from, and owned by, another invocation. */
        CAPTURED,
        /** A fresh physical home owned by this invocation. */
        OWNED_PHYSICAL
    }

    /**
     * pure classification, the same precedence as {@link
     * #invocationHome}: a captured canonical marker, then any other captured
     * home (exact identity retained), then the effective root's proof. It
     * never creates a home, prepares a plan or consults a Context; {@code
     * effectiveRoot} must be the root of the cached effective CallTarget.
     */
    static ReturnHomeMode classifyReturnHome(
            ProtosClosureValue closure, ProtosBytecodeRootNode effectiveRoot) {
        ProtosReturnHome captured = closure.capturedReturnHomeOrNullForRuntime();
        if (captured == ProtosReturnHome.unobservable()) {
            return ReturnHomeMode.UNOBSERVABLE;
        }
        if (captured != null) {
            return ReturnHomeMode.CAPTURED;
        }
        if (closure.hasNativeBody()
                && ProtosStandardIntegerProtocol.isCanonicalArithmetic(closure)) {
            return ReturnHomeMode.UNOBSERVABLE;
        }
        if (!closure.hasNativeBody()
                && closure.definition() != null
                && effectiveRoot != null
                && effectiveRoot.returnHomeProvenUnobservable()) {
            return ReturnHomeMode.UNOBSERVABLE;
        }
        return ReturnHomeMode.OWNED_PHYSICAL;
    }

    /**
     * Shared entry of every identity-keyed direct-call cache. The mode is a
     * per-entry constant of the cache, so partial evaluation keeps exactly
     * one of the three routes.
     */
    static Object invokeCachedClosure(
            VirtualFrame frame,
            Node inliningTarget,
            ReturnHomeMode homeMode,
            InlinedConditionProfile taskPresence,
            InlinedConditionProfile explicitTaskPresence,
            InlinedConditionProfile publishedActivationPresence,
            InlinedConditionProfile closureHeaderPresence,
            InlinedConditionProfile callerActivationPresence,
            InlinedConditionProfile suspensionPresence,
            FunctionDirectInvokeNode invoke,
            DirectCallNode callNode,
            ProtosBytecodeRootNode callee,
            ProtosClosureValue closure,
            Object[] arguments,
            Object explicitCaller) {
        if (homeMode == ReturnHomeMode.UNOBSERVABLE) {
            return invokeUnobservableHome(
                    frame, inliningTarget, taskPresence, explicitTaskPresence,
                    publishedActivationPresence, closureHeaderPresence,
                    callerActivationPresence, suspensionPresence,
                    callNode, callee, closure, arguments, explicitCaller);
        }
        if (homeMode == ReturnHomeMode.CAPTURED) {
            return invokeCapturedHome(
                    frame, inliningTarget, taskPresence, explicitTaskPresence,
                    publishedActivationPresence, closureHeaderPresence,
                    callerActivationPresence, suspensionPresence,
                    callNode, callee, closure, arguments, explicitCaller);
        }
        return invoke.execute(
                frame, inliningTarget, callNode, callee,
                closure, arguments, explicitCaller);
    }

    /**
     * Admission guard of the single-context direct-call caches. It reads the
     * language's single-context state once, when a cache entry is created.
     */
    @Idempotent
    static boolean isSingleContext() {
        CompilerAsserts.neverPartOfCompilation();
        return ProtosLanguageContext.current()
                .languageForRuntime()
                .isSingleContextForRuntime();
    }

    /**
     * Only a prepared plan belonging to the caller root's language can enter
     * a single-context direct cache. A deferred, foreign or projection-required
     * plan takes the context-aware dispatcher.
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

    /**
     * UNOBSERVABLE route: no physical home exists, so nothing is bound,
     * closed or consumed here and no non-local return handler is installed;
     * transfers crossing the CallTarget propagate unchanged. The Task is
     * inherited unless the cached callee root admits omitting its transport
     * (see omitsTaskTransport), and a Task-free invocation may still suspend
     * and is sealed.
     */
    static Object invokeUnobservableHome(
            VirtualFrame frame,
            Node inliningTarget,
            InlinedConditionProfile taskPresence,
            InlinedConditionProfile explicitTaskPresence,
            InlinedConditionProfile publishedActivationPresence,
            InlinedConditionProfile closureHeaderPresence,
            InlinedConditionProfile callerActivationPresence,
            InlinedConditionProfile suspensionPresence,
            DirectCallNode callNode,
            ProtosBytecodeRootNode callee,
            ProtosClosureValue closure,
            Object[] arguments,
            Object explicitCaller) {
        Object caller = prepareCallerForCallee(frame, callee, closure, explicitCaller);
        ProtosFrameArguments.completeDispatchedHeader(
                arguments, closure, caller, ProtosReturnHome.unobservable());
        if (!omitsTaskTransport(frame, callee, explicitCaller)) {
            prepareInvocationTask(
                    frame, arguments, explicitCaller, inliningTarget, taskPresence, explicitTaskPresence,
                    publishedActivationPresence, closureHeaderPresence, callerActivationPresence);
        }
        Object outcome = callNode.call(arguments);
        if (callee != null && callee.provablyNonSuspendingBody()) {
            return outcome;
        }
        return sealIfSuspended(
                frame, arguments, explicitCaller, outcome,
                inliningTarget, suspensionPresence);
    }

    /**
     * CAPTURED route: B5[4] carries the exact captured home. Its owner
     * invocation binds its Task and closes it; a return targeting it is
     * consumed only by that owner, after PLAT070 unwinds intermediate ensure
     * regions, so this boundary neither binds, closes nor catches anything.
     */
    static Object invokeCapturedHome(
            VirtualFrame frame,
            Node inliningTarget,
            InlinedConditionProfile taskPresence,
            InlinedConditionProfile explicitTaskPresence,
            InlinedConditionProfile publishedActivationPresence,
            InlinedConditionProfile closureHeaderPresence,
            InlinedConditionProfile callerActivationPresence,
            InlinedConditionProfile suspensionPresence,
            DirectCallNode callNode,
            ProtosBytecodeRootNode callee,
            ProtosClosureValue closure,
            Object[] arguments,
            Object explicitCaller) {
        Object caller = prepareCallerForCallee(frame, callee, closure, explicitCaller);
        ProtosFrameArguments.completeDispatchedHeader(
                arguments, closure, caller,
                closure.capturedReturnHomeOrNullForRuntime());
        prepareInvocationTask(
                frame, arguments, explicitCaller, inliningTarget, taskPresence, explicitTaskPresence,
                publishedActivationPresence, closureHeaderPresence, callerActivationPresence);
        Object outcome = callNode.call(arguments);
        if (callee != null && callee.provablyNonSuspendingBody()) {
            return outcome;
        }
        return sealIfSuspended(
                frame, arguments, explicitCaller, outcome,
                inliningTarget, suspensionPresence);
    }

    /**
     * Invocation through a cached executable shared by several Closures. The
     * ReturnHome route is selected from the current Closure on every call,
     * because Closures sharing one target may capture different homes: a
     * captured home travels with its exact identity, an uncaptured source
     * Closure whose effective root proves its home unobservable uses the
     * shared marker, and every other case owns a fresh physical home.
     */
    static Object invokeSharedTarget(
            VirtualFrame frame,
            Node inliningTarget,
            InlinedConditionProfile taskPresence,
            InlinedConditionProfile explicitTaskPresence,
            InlinedConditionProfile publishedActivationPresence,
            InlinedConditionProfile closureHeaderPresence,
            InlinedConditionProfile callerActivationPresence,
            InlinedConditionProfile suspensionPresence,
            FunctionDirectInvokeNode invoke,
            DirectCallNode callNode,
            ProtosBytecodeRootNode callee,
            ProtosClosureValue closure,
            Object[] arguments,
            Object explicitCaller) {
        if (closure.capturedReturnHomeOrNullForRuntime() != null) {
            return invokeCapturedHome(
                    frame, inliningTarget, taskPresence, explicitTaskPresence,
                    publishedActivationPresence, closureHeaderPresence,
                    callerActivationPresence, suspensionPresence,
                    callNode, callee, closure, arguments, explicitCaller);
        }
        if (!closure.hasNativeBody()
                && closure.definition() != null
                && callee != null
                && callee.returnHomeProvenUnobservable()) {
            return invokeUnobservableHome(
                    frame, inliningTarget, taskPresence, explicitTaskPresence,
                    publishedActivationPresence, closureHeaderPresence,
                    callerActivationPresence, suspensionPresence,
                    callNode, callee, closure, arguments, explicitCaller);
        }
        return invoke.execute(
                frame, inliningTarget, callNode, callee,
                closure, arguments, explicitCaller);
    }

    /*
     * Task demand of the callee. The callee is the root of the cached
     * effective CallTarget, so its capability is a compilation constant and
     * the caller-needs Assumption held by every consuming site deoptimizes
     * this decision when an exact activation becomes needed. Only a guest
     * caller frame without an explicit caller Activation qualifies: then
     * the Task the callee would have inherited is exactly the Task of the
     * caller that cold recovery finds on the active stack, so a later
     * materialization observes the same Task. An explicit Task already in
     * B5[2] is never replaced and a method's B5[2] methodHome is untouched.
     */
    private static boolean omitsTaskTransport(
            VirtualFrame frame, ProtosBytecodeRootNode callee, Object explicitCaller) {
        return callee != null
                && callee.admitsTaskTransportOmission()
                && frame != null
                && !(explicitCaller instanceof ProtosActivation);
    }

    /** Preserve Task reachability even when the callee needs no caller state. */
    private static ProtosTask prepareInvocationTask(
            VirtualFrame frame, Object[] arguments, Object explicitCaller,
            Node inliningTarget,
            InlinedConditionProfile taskPresence,
            InlinedConditionProfile explicitTaskPresence,
            InlinedConditionProfile publishedActivationPresence,
            InlinedConditionProfile closureHeaderPresence,
            InlinedConditionProfile callerActivationPresence) {
        ProtosTask inherited =
                explicitCaller instanceof ProtosActivation activation
                        ? activation.taskForRuntime()
                        : frame != null
                                ? ProtosFrameArguments.admittedCompactTask(
                                        frame.getArguments(),
                                        inliningTarget,
                                        explicitTaskPresence,
                                        publishedActivationPresence,
                                        closureHeaderPresence,
                                        callerActivationPresence)
                                : null;
        ProtosTask task = ProtosFrameArguments.inheritInvocationTask(arguments, inherited);
        if (taskPresence.profile(inliningTarget, task != null)
                && !ProtosFrameArguments.isFunctionCall(arguments)) {
            // Methods use slot 2 for methodHome. Only a Task-owned method
            // needs an exact caller in slot 3 to retain its Task provenance.
            if (!(arguments[3] instanceof ProtosActivation caller)
                    || caller.taskForRuntime() != task) {
                arguments[3] =
                        explicitCaller instanceof ProtosActivation
                                ? explicitCaller
                                : frame != null
                                        ? ProtosFrameArguments.activation(frame)
                                        : explicitCaller;
            }
        }
        return task;
    }

    /**
     * Cold departure from an invocation that owns a physical home. Captured
     * homes must never be passed here. PLAT070 unwinds finally regions before
     * this boundary consumes a return; unrelated transfers retain identity.
     */
    static Object completeOwnedTransfer(
            ProtosReturnHome owned, ControlFlowException transfer) {
        if (owned != null && owned.isMaterialized()) {
            if (transfer instanceof ProtosNonLocalReturnException returned
                    && returned.target() == owned && owned.isActive()) {
                owned.closeOwned();
                return returned.value();
            }
            owned.closeOwned();
        }
        throw transfer;
    }

    /**
     * a suspended outcome leaves this boundary with the callee's B5
     * header retained by the pending continuation. The caller is still
     * live here, so its exact provenance is sealed into the callee's B5[3]
     * before the pending outcome is returned: the guest caller frame's
     * header when one exists, otherwise the explicit caller of a host entry.
     * Ordinary results are returned first and unchanged, without reading
     * the header or publishing the caller; exceptional transfers propagate.
     * The seal itself is the cold boundary.
     */
    private static Object sealIfSuspended(
            VirtualFrame frame,
            Object[] arguments,
            Object explicitCaller,
            Object outcome,
            Node inliningTarget,
            InlinedConditionProfile suspensionPresence) {
        if (frame == null) {
            return sealHostSuspension(arguments, explicitCaller, outcome);
        }
        boolean suspended = ProtosTailCallContinuation.isSuspended(outcome);
        if (!suspensionPresence.profile(inliningTarget, suspended)) {
            return outcome;
        }
        ProtosFrameArguments.sealSuspendedCaller(
                arguments, frame.getArguments(), explicitCaller);
        return outcome;
    }

    /** Host entry without a guest frame: the explicit caller is sealed. */
    @TruffleBoundary
    private static Object sealHostSuspension(
            Object[] arguments, Object explicitCaller, Object outcome) {
        if (ProtosTailCallContinuation.isSuspended(outcome)) {
            ProtosFrameArguments.sealSuspendedCaller(arguments, null, explicitCaller);
        }
        return outcome;
    }

    /** {@code CallDispatchers.FunctionDirectInvokeNode}. */
    @GenerateInline
    @GenerateCached(false)
    @GenerateUncached
    abstract static class FunctionDirectInvokeNode extends Node {

        abstract Object execute(
                VirtualFrame frame,
                Node inliningTarget,
                DirectCallNode callNode,
                ProtosBytecodeRootNode callee,
                ProtosClosureValue closure,
                Object[] arguments,
                Object explicitCaller);

        @Specialization
        static Object invoke(
                VirtualFrame frame,
                Node inliningTarget,
                DirectCallNode callNode,
                ProtosBytecodeRootNode callee,
                ProtosClosureValue closure,
                Object[] arguments,
                Object explicitCaller,
                @Cached InlinedConditionProfile taskPresence,
                @Cached InlinedConditionProfile explicitTaskPresence,
                @Cached InlinedConditionProfile publishedActivationPresence,
                @Cached InlinedConditionProfile closureHeaderPresence,
                @Cached InlinedConditionProfile callerActivationPresence,
                @Cached InlinedConditionProfile suspensionPresence) {
            Object caller = prepareCallerForCallee(frame, callee, closure, explicitCaller);
            ProtosReturnHome home = invocationHome(closure, callee);
            ProtosFrameArguments.completeDispatchedHeader(arguments, closure, caller, home);
            ProtosTask task = prepareInvocationTask(
                    frame, arguments, explicitCaller, inliningTarget, taskPresence, explicitTaskPresence,
                    publishedActivationPresence, closureHeaderPresence, callerActivationPresence);
            ProtosFrameArguments.bindOwnedReturnHome(closure, home, task);

            Object outcome;
            try {
                outcome = callNode.call(arguments);
            } catch (ControlFlowException transfer) {
                if (home.isMaterialized()
                        && closure.capturedReturnHomeOrNullForRuntime() == null) {
                    return completeOwnedTransfer(home, transfer);
                }
                throw transfer;
            }
            if (callee != null && callee.provablyNonSuspendingBody()) {
                return outcome;
            }
            return sealIfSuspended(
                    frame, arguments, explicitCaller, outcome,
                    inliningTarget, suspensionPresence);
        }
    }

    /** {@code CallDispatchers.FunctionIndirectInvokeNode}. */
    @GenerateInline
    @GenerateCached(false)
    @GenerateUncached
    abstract static class FunctionIndirectInvokeNode extends Node {

        abstract Object execute(
                VirtualFrame frame,
                Node inliningTarget,
                RootCallTarget target,
                ProtosBytecodeRootNode callee,
                ProtosClosureValue closure,
                Object[] arguments,
                Object explicitCaller);

        @Specialization
        static Object invoke(
                VirtualFrame frame,
                Node inliningTarget,
                RootCallTarget target,
                ProtosBytecodeRootNode callee,
                ProtosClosureValue closure,
                Object[] arguments,
                Object explicitCaller,
                @Cached(inline = false) IndirectCallNode callNode,
                @Cached InlinedConditionProfile taskPresence,
                @Cached InlinedConditionProfile explicitTaskPresence,
                @Cached InlinedConditionProfile publishedActivationPresence,
                @Cached InlinedConditionProfile closureHeaderPresence,
                @Cached InlinedConditionProfile callerActivationPresence,
                @Cached InlinedConditionProfile suspensionPresence) {
            Object caller = prepareCallerForCallee(frame, callee, closure, explicitCaller);
            ProtosReturnHome home = invocationHome(closure, calleeRootOrNull(target));
            ProtosFrameArguments.completeDispatchedHeader(arguments, closure, caller, home);
            ProtosTask task = prepareInvocationTask(
                    frame, arguments, explicitCaller, inliningTarget, taskPresence, explicitTaskPresence,
                    publishedActivationPresence, closureHeaderPresence, callerActivationPresence);
            ProtosFrameArguments.bindOwnedReturnHome(closure, home, task);

            Object outcome;
            try {
                outcome = callNode.call(target, arguments);
            } catch (ControlFlowException transfer) {
                if (home.isMaterialized()
                        && closure.capturedReturnHomeOrNullForRuntime() == null) {
                    return completeOwnedTransfer(home, transfer);
                }
                throw transfer;
            }
            return sealIfSuspended(
                    frame, arguments, explicitCaller, outcome,
                    inliningTarget, suspensionPresence);
        }
    }

    /*
     * Host-side guest-value projection only. Internal source invocations
     * return their exact results to the consuming Bytecode DSL operation.
     */
    private static Object projectForCaller(Object outcome, boolean acceptsCarrier) {
        if (acceptsCarrier || ProtosTailCallContinuation.isSuspended(outcome)) {
            return outcome;
        }
        return ProtosNumericValueSupport.guestValue(outcome);
    }

    /*
     * Callable method lookup for the generic path: the ordinary `call` slot of
     * the receiver; a missing or non-Closure behavior is an Error. Selection
     * reads only the caller's admitted Prelude; the exact caller activation is
     * demanded only to construct a signaled Error.
     */
    @TruffleBoundary
    static ProtosSlotLookupResult selectCall(
            Object receiver, Object callerReference, ProtosBytecodeRootNode root) {
        ProtosPrelude prelude = admittedCallerPrelude(root, callerReference);
        if (prelude == null) {
            prelude =
                    exactCaller(root, callerReference)
                            .prelude()
                            .orElseThrow(
                                    () ->
                                            new IllegalStateException(
                                                    "polymorphic invocation requires an owning Core prelude"));
        }
        java.util.Optional<ProtosSlotLookupResult> found;
        try {
            found = ProtosValueLookup.lookup(receiver, "call", prelude);
        } catch (UnsupportedOperationException unsupportedRepresentation) {
            throw callSelectionError(root, callerReference);
        }
        if (found.isEmpty() || !(found.get().value() instanceof ProtosClosureValue)) {
            throw callSelectionError(root, callerReference);
        }
        return found.get();
    }

    private static ProtosSignalException callSelectionError(
            ProtosBytecodeRootNode root, Object callerReference) {
        return new ProtosSignalException(
                ProtosCoreErrors.newError(exactCaller(root, callerReference)));
    }

    /**
     * Host-side entry (Tasks, callbacks from Java, embedding): the same
     * dispatch through the uncached nodes. {@code caller} is the exact
     * calling activation.
     */
    static Object callUncached(Object callable, Object[] arguments, ProtosActivation caller) {
        ProtosClosureValue closure = callable instanceof ProtosClosureValue source
                ? source : callable instanceof ProtosBoundMethod method ? method.callable() : null;
        if (closure != null && closure.requiresContextLocalExecutionProjectionForRuntime()
                && ProtosLanguageContext.currentIfEnteredForRuntime() == null) {
            throw new UnsupportedOperationException(
                    "Context-local Closure projection requires an entered Protos Context");
        }
        // This host-side entry is an observable guest-value boundary, unlike
        // an admitted direct/indirect guest call whose caller may accept carriers.
        // The callee epilogue and internal dispatcher return carriers unchanged.
        Object outcome = ProtosFunctionCallDispatchersFactory.CallNodeGen.getUncached()
                .execute(null, false, callable, arguments, caller, null);
        return projectForCaller(outcome, false);
    }

    /**
     * A one-argument send from a numeric generic route: GetMethod selection
     * and the shared dispatcher. A non-terminal route cannot compose a
     * suspension, which is rejected with an Error.
     */
    /**
     * Cold generic path for one-argument operators. The caller must compose
     * the returned continuation with emitDispatchedContinuationComposition.
     * In particular, do not reject a suspended outcome as sendOne(false) does.
     */
    @TruffleBoundary
    static Object sendOneComposable(
            ProtosBytecodeRootNode root,
            Object caller,
            Object receiver,
            String selector,
            Object argument) {
        ProtosActivation activation = exactCaller(root, caller);
        Object method = ProtosExperimentalGetMethodNode.select(
                ProtosNumericValueSupport.guestValue(receiver), selector, root, activation);
        return callFunctionUncached(
                method,
                new Object[] {ProtosNumericValueSupport.guestValue(argument)},
                activation);
    }

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

        if (closure.nativeBody().isPresent()) {
            return context.builtinCallTarget(nativeBody(closure)).call(activation, closure);
        }
        RootCallTarget target = functionCallTargetOrFail(closure, context);
        Object[] arguments = ProtosFrameArguments.publishedInvocation(activation, target);
        ProtosReturnHome owned = ProtosFrameArguments.ownedReturnHomeOrNull(arguments);
        if (owned == null) {
            return target.call(arguments);
        }
        try {
            return target.call(arguments);
        } catch (ControlFlowException transfer) {
            return completeOwnedTransfer(owned, transfer);
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
     * Observes the exact activation of the caller. An argument array here
     * is the running caller root's own current header: publishing it records
     * NEEDS_EXACT_ACTIVATION. It is never retained in a callee's B5[3], whose
     * payload is only null, Prelude, or exact Activation. Frame-null host
     * entry retains its explicit caller instead of recovering a source frame.
     */
    static ProtosActivation exactCaller(ProtosBytecodeRootNode root, Object caller) {
        if (caller instanceof ProtosActivation activation) {
            return activation;
        }
        if (root != null) {
            root.requestCallerNeed(
                    ProtosBytecodeRootNode.NEEDS_EXACT_ACTIVATION);
        }
        if (caller instanceof Object[] currentFrameArguments) {
            return ProtosFrameArguments.activation(currentFrameArguments);
        }
        return ProtosFrameArguments.resolveAdmittedCaller(caller);
    }


    /**
     * Reads a transported Prelude or exact Activation, or the running
     * caller root's own admitted header. That array is current-frame input
     * to this helper, never a callee's B5[3] payload. An untransported
     * inherited Prelude registers NEEDS_CALLER_PRELUDE once so subsequent
     * calls transport it; this read itself publishes no activation.
     */
    static ProtosPrelude admittedCallerPrelude(ProtosBytecodeRootNode root, Object caller) {
        if (caller instanceof ProtosPrelude prelude) {
            return prelude;
        }
        if (caller instanceof ProtosActivation activation) {
            return activation.preludeOrNullForRuntime();
        }
        if (caller instanceof Object[] currentFrameArguments) {
            if (root != null
                    && ProtosFrameArguments.inheritsUntransportedCallerPrelude(
                            currentFrameArguments)) {
                root.requestCallerNeed(
                        ProtosBytecodeRootNode.NEEDS_CALLER_PRELUDE);
            }
            return ProtosFrameArguments.admittedPreludeOrNull(currentFrameArguments);
        }
        return null;
    }


    /** The prelude a caller reference denotes, without publishing anything. */
    static ProtosPrelude callerPrelude(Object caller) {
        if (caller instanceof ProtosPrelude prelude) {
            return prelude;
        }
        if (caller instanceof ProtosActivation activation) {
            return activation.preludeOrNullForRuntime();
        }
        return caller instanceof Object[] currentFrameArguments
                ? ProtosFrameArguments.preludeOrNull(currentFrameArguments)
                : null;
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
