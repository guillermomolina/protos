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

import com.guillermomolina.protos.execution.ProtosExperimentalGetMethodNode.Method;
import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosCoreErrors;
import com.guillermomolina.protos.runtime.ProtosNumericValueSupport;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.bytecode.BytecodeNode;
import com.oracle.truffle.api.bytecode.ConstantOperand;
import com.oracle.truffle.api.bytecode.OperationProxy;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.NonIdempotent;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.Node;

/**
 * I115-H7 EXPERIMENTAL, proxied as the {@code CallBinaryMethod} operation
 * (GraalPy {@code CallBinaryMethodNode.callPFunction} shape): the call half of
 * an ordinary one-argument send, consuming the {@link Method} produced by
 * {@code GetMethod} and the explicit argument, each evaluated once by the
 * caller bytecode.
 *
 * <ul>
 *   <li>{@link #doDirect}: a PIC of at most three selected source Closures,
 *       keyed by Closure identity, each with its stable {@link
 *       RootCallTarget} and a cached {@link DirectCallNode}. The B5+ header
 *       ({@link ProtosFrameArguments#experimentalMethodCallOne}) is built
 *       immediately before the single call; the callee's result is returned
 *       directly.</li>
 *   <li>{@link #doIndirect}: the megamorphic Closure tier, the same header
 *       through an {@link IndirectCallNode}.</li>
 *   <li>{@link #doGeneric}: any other selection (no slot, a non-Closure
 *       value, a native body, a target without the non-suspension proof):
 *       the complete ordinary send, behind {@link InliningCutoff}, never
 *       another DSL operation tree.</li>
 * </ul>
 *
 * <p>Caller protocol (I115-H7-R1): the callee's caller slot is produced by
 * the existing B5 producer ({@code InvokeMethodOneTail#callerArgument}) from
 * the caller reference and prelude {@code GetMethod} read: the reference as
 * is, or a {@link ProtosCallerFrameReference} transporting the prelude when
 * the callee root registered {@code NEEDS_CALLER_PRELUDE}. The generic route
 * resolves the exact caller through {@code InvokeClosureOneTail#exactCaller},
 * which registers this root's {@code NEEDS_EXACT_ACTIVATION}.
 *
 * <p>Suspension: the direct and indirect tiers enter only roots proven
 * non-suspending. When the send is the root's terminal expression ({@code
 * terminal}), a suspended generic outcome is returned as the existing
 * terminal transport, exactly as {@code InvokeMethodOneTail}. A non-terminal
 * site has no continuation in the compact root: a suspended generic outcome
 * is abandoned and the standard {@code Error} is signalled at the caller
 * (RECORDED LIMITATION, see the I115-H7 experiment record).
 *
 * <p>An argument in the I115-H7 int representation is passed as is only to
 * a callee root that the experimental lowering entered with {@code
 * CopyArguments}; every other callee receives the guest value.
 */
@GenerateUncached
@GenerateInline(false)
@OperationProxy.Proxyable(allowUncached = true, storeBytecodeIndex = false)
@ConstantOperand(type = boolean.class, name = "terminal")
abstract class ProtosExperimentalCallBinaryMethodNode extends Node {

    abstract Object execute(
            VirtualFrame frame, boolean terminal, Object method, Object argument);

    @Specialization(
            excludeForUncached = true,
            guards = {
                "cachedClosure != null",
                "cachedTarget != null",
                "selects(method, cachedClosure)"
            },
            limit = "3")
    static Object doDirect(
            VirtualFrame frame,
            boolean terminal,
            Method method,
            Object argument,
            @Cached("closureOrNull(method.callable())") ProtosClosureValue cachedClosure,
            @Cached("admittedTargetOrNull(cachedClosure, currentEnteredContext($node))")
                    RootCallTarget cachedTarget,
            @Cached(value = "calleeRootOrNull(cachedTarget)", adopt = false)
                    ProtosBytecodeRootNode cachedCallee,
            @Cached("acceptsPrimitiveArguments(cachedCallee)") boolean cachedPrimitive,
            @Cached("createDirectCallNodeOrNull(cachedTarget)") DirectCallNode call) {
        Object[] arguments = ProtosFrameArguments.experimentalMethodCallOne(
                cachedClosure,
                ProtosNumericValueSupport.guestValue(method.receiver()),
                method.home(),
                callerArgument(cachedCallee, cachedClosure, method),
                cachedPrimitive ? argument : ProtosNumericValueSupport.guestValue(argument));
        try {
            return call.call(arguments);
        } catch (ProtosBytecodeControlTransferException bridged) {
            throw bridged.transfer();
        }
    }

    @Specialization(guards = "isClosure(method)", replaces = "doDirect")
    static Object doIndirect(
            VirtualFrame frame,
            boolean terminal,
            Method method,
            Object argument,
            @Bind("rootOf($node)") ProtosBytecodeRootNode root,
            @Cached IndirectCallNode call) {
        ProtosClosureValue closure = (ProtosClosureValue) method.callable();
        RootCallTarget target =
                admittedTargetOrNull(closure, ProtosLanguageContext.current(call));
        if (target == null) {
            return sendOne(root, method.caller(), terminal,
                    method.receiver(), method.selector(), argument);
        }
        ProtosBytecodeRootNode callee = calleeRootOrNull(target);
        Object[] arguments = ProtosFrameArguments.experimentalMethodCallOne(
                closure,
                ProtosNumericValueSupport.guestValue(method.receiver()),
                method.home(),
                callerArgument(callee, closure, method),
                acceptsPrimitiveArguments(callee)
                        ? argument
                        : ProtosNumericValueSupport.guestValue(argument));
        try {
            return call.call(target, arguments);
        } catch (ProtosBytecodeControlTransferException bridged) {
            throw bridged.transfer();
        }
    }

    @Specialization(guards = "!isClosure(method)")
    @InliningCutoff
    static Object doGeneric(
            VirtualFrame frame,
            boolean terminal,
            Method method,
            Object argument,
            @Bind("rootOf($node)") ProtosBytecodeRootNode root) {
        return sendOne(root, method.caller(), terminal,
                method.receiver(), method.selector(), argument);
    }

    /* Closure identity of the cached PIC entry. */
    static boolean selects(Method method, ProtosClosureValue cachedClosure) {
        return method.callable() == cachedClosure;
    }

    static boolean isClosure(Method method) {
        return method.callable() instanceof ProtosClosureValue;
    }

    static ProtosClosureValue closureOrNull(Object callable) {
        return callable instanceof ProtosClosureValue closure ? closure : null;
    }

    /**
     * Installation (direct tier) or megamorphic (indirect tier): the
     * Context-owned Bytecode target of a source Closure whose root proves
     * non-suspension, or null.
     */
    @TruffleBoundary
    static RootCallTarget admittedTargetOrNull(
            ProtosClosureValue closure, ProtosLanguageContext enteredContext) {
        if (closure == null || closure.nativeBody().isPresent()) {
            return null;
        }
        RootCallTarget target =
                ProtosGuardedSendSelection.fastOrdinarySendTarget(closure, enteredContext);
        return target != null
                        && target.getRootNode() instanceof ProtosBytecodeRootNode root
                        && root.provablyNonSuspendingBody()
                ? target
                : null;
    }

    static ProtosBytecodeRootNode calleeRootOrNull(RootCallTarget target) {
        return target != null
                        && target.getRootNode() instanceof ProtosBytecodeRootNode callee
                ? callee
                : null;
    }

    static boolean acceptsPrimitiveArguments(ProtosBytecodeRootNode callee) {
        return callee != null && callee.isExperimentalPrimitiveArgumentRoot();
    }

    static DirectCallNode createDirectCallNodeOrNull(RootCallTarget target) {
        return target == null ? null : DirectCallNode.create(target);
    }

    /*
     * The callee's B5 caller slot through the existing producer contract
     * (InvokeMethodOneTail#callerArgument), with the prelude GetMethod read.
     */
    static Object callerArgument(
            ProtosBytecodeRootNode callee, ProtosClosureValue closure, Method method) {
        if (callee == null) {
            return method.caller();
        }
        return ProtosBytecodeRootNode.InvokeMethodOneTail.callerArgument(
                callee, closure, method.caller(), method.callerPrelude());
    }

    @NonIdempotent
    static ProtosLanguageContext currentEnteredContext(Node node) {
        return ProtosLanguageContext.current(node);
    }

    /**
     * I115-H7: the single generic route of the experimental operations: the
     * complete ordinary Send1 of {@code selector} on the guest values of the
     * already evaluated operands. The exact caller is resolved through
     * {@code InvokeClosureOneTail#exactCaller}, registering {@code root}'s
     * {@code NEEDS_EXACT_ACTIVATION}. A {@code terminal} site returns a
     * suspended outcome as the terminal transport; any other site abandons
     * it and signals the standard {@code Error} at the caller.
     */
    static Object sendOne(
            ProtosBytecodeRootNode root,
            Object caller,
            boolean terminal,
            Object receiver,
            String selector,
            Object argument) {
        ProtosActivation activation =
                ProtosBytecodeRootNode.InvokeClosureOneTail.exactCaller(root, caller);
        return sendOneSelected(activation, terminal, receiver, selector, argument);
    }

    @TruffleBoundary
    private static Object sendOneSelected(
            ProtosActivation caller,
            boolean terminal,
            Object receiver,
            String selector,
            Object argument) {
        Object outcome = ProtosTailCallContinuation.executeTerminalPreparedCall(
                ProtosSendPreparation.prepareSendOneFallback(
                        ProtosNumericValueSupport.guestValue(receiver),
                        selector,
                        caller,
                        ProtosNumericValueSupport.guestValue(argument)),
                IndirectCallNode.getUncached(),
                null,
                -1);
        if (!terminal && ProtosTailCallContinuation.isSuspended(outcome)) {
            ProtosTailCallContinuation.abandon(outcome);
            throw ProtosCoreErrors.signal(caller, ProtosCoreErrors.newError(caller));
        }
        return outcome;
    }

    /** The caller reference of the running root, for the numeric generic routes. */
    @NonIdempotent
    static Object callerReference(VirtualFrame frame) {
        return ProtosBytecodeRootNode.InvokeClosureOneTail.callerReference(frame);
    }

    /*
     * I115-H7-R1: the Protos root executing this operation. In a proxied
     * node the default root bind is typed Node, so it is resolved from the
     * enclosing BytecodeNode.
     */
    static ProtosBytecodeRootNode rootOf(Node node) {
        return (ProtosBytecodeRootNode) BytecodeNode.get(node).getBytecodeRootNode();
    }
}
