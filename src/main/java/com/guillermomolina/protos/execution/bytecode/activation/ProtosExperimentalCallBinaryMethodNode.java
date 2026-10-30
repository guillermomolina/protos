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
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
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
import com.oracle.truffle.api.dsl.NonIdempotent;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

/**
 * I115-H7 EXPERIMENTAL, proxied as the {@code CallBinaryMethod} operation
 * (GraalPy {@code CallBinaryMethodNode.callPFunction} shape): the call half of
 * an ordinary one-argument send. I115-H7-R3: it consumes three operands, each
 * evaluated once by the caller bytecode: the selected {@code callable}
 * produced by {@code GetMethod}, the {@code receiver} reloaded from its bound
 * stack value, and the explicit {@code argument}. The selector and {@code
 * terminal} are constant operands.
 *
 * <ul>
 *   <li>{@link #doDirect}: a PIC of at most three selected source Closures,
 *       keyed by Closure identity, each with its stable {@link
 *       RootCallTarget} and a cached {@link DirectCallNode}. Only admitted
 *       compact H7 callees ({@link #admittedTargetOrNull}) enter it. The B5+
 *       header ({@link ProtosFrameArguments#experimentalMethodCallOne}) is
 *       built immediately before the single call, with a {@code null} method
 *       home; the caller reference is read only when the callee root has
 *       registered {@code NEEDS_CALLER_PRELUDE} or {@code
 *       NEEDS_EXACT_ACTIVATION}. The callee's result is returned directly.</li>
 *   <li>{@link #doIndirect}: the megamorphic Closure tier, the same header
 *       through an {@link IndirectCallNode}, or the ordinary send for a
 *       Closure that is not admitted.</li>
 *   <li>{@link #doGeneric}: any other selection (no slot, a non-Closure
 *       value): the complete ordinary send of the constant selector on the
 *       already evaluated operands, behind {@link InliningCutoff}, never
 *       another DSL operation tree.</li>
 * </ul>
 *
 * <p>Suspension: the direct and indirect tiers enter only roots proven
 * non-suspending. When the send is the root's terminal expression ({@code
 * terminal}), a suspended generic outcome is returned as the existing
 * terminal transport, exactly as {@code InvokeMethodOneTail}. A non-terminal
 * site has no continuation in the compact root: a suspended generic outcome
 * is abandoned and the standard {@code Error} is signalled at the caller
 * (RECORDED LIMITATION, see the I115-H7 experiment record).
 *
 * <p>RECORDED DIVERGENCES (I115-H7-R3): an admitted callee receives no
 * method home and, unless it registered a caller need, no caller reference;
 * a non-local control transfer out of an admitted callee is not translated
 * at this call site; the int carrier of the H7 operations is passed as is.
 */
@GenerateUncached
@GenerateInline(false)
@OperationProxy.Proxyable(allowUncached = true, storeBytecodeIndex = false)
@ConstantOperand(type = TruffleString.class, name = "selector")
@ConstantOperand(type = boolean.class, name = "terminal")
abstract class ProtosExperimentalCallBinaryMethodNode extends Node {

    abstract Object execute(
            VirtualFrame frame,
            TruffleString selector,
            boolean terminal,
            Object callable,
            Object receiver,
            Object argument);

    @Specialization(
            excludeForUncached = true,
            guards = {
                "callable == cachedClosure",
                "cachedTarget != null"
            },
            limit = "3")
    @ForceQuickening
    static Object doDirect(
            VirtualFrame frame,
            TruffleString selector,
            boolean terminal,
            ProtosClosureValue callable,
            Object receiver,
            Object argument,
            @Cached("callable") ProtosClosureValue cachedClosure,
            @Cached("admittedTargetOrNull(cachedClosure, currentEnteredContext($node))")
                    RootCallTarget cachedTarget,
            @Cached(value = "calleeRootOrNull(cachedTarget)", adopt = false)
                    ProtosBytecodeRootNode cachedCallee,
            @Bind("rootOf($node)") ProtosBytecodeRootNode root,
            @Cached("createDirectCallNodeOrNull(cachedTarget)") DirectCallNode call) {
        return call.call(ProtosFrameArguments.experimentalMethodCallOne(
                cachedClosure,
                ProtosNumericValueSupport.guestValue(receiver),
                null,
                callerArgumentIfNeeded(frame, root, cachedCallee, cachedClosure),
                argument));
    }

    @Specialization(replaces = "doDirect")
    static Object doIndirect(
            VirtualFrame frame,
            TruffleString selector,
            boolean terminal,
            ProtosClosureValue callable,
            Object receiver,
            Object argument,
            @Bind("rootOf($node)") ProtosBytecodeRootNode root,
            @Cached IndirectCallNode call) {
        RootCallTarget target =
                admittedTargetOrNull(callable, ProtosLanguageContext.current(call));
        if (target == null) {
            return sendOne(root, callerReference(frame), terminal,
                    receiver, selector.toJavaStringUncached(), argument);
        }
        ProtosBytecodeRootNode callee = calleeRootOrNull(target);
        Object[] arguments = ProtosFrameArguments.experimentalMethodCallOne(
                callable,
                ProtosNumericValueSupport.guestValue(receiver),
                null,
                callerArgumentIfNeeded(frame, root, callee, callable),
                argument);
        try {
            return call.call(target, arguments);
        } catch (ProtosBytecodeControlTransferException bridged) {
            throw bridged.transfer();
        }
    }

    @Specialization(guards = "!isClosure(callable)")
    @InliningCutoff
    static Object doGeneric(
            VirtualFrame frame,
            TruffleString selector,
            boolean terminal,
            Object callable,
            Object receiver,
            Object argument,
            @Bind("rootOf($node)") ProtosBytecodeRootNode root) {
        return sendOne(root, callerReference(frame), terminal,
                receiver, selector.toJavaStringUncached(), argument);
    }

    static boolean isClosure(Object callable) {
        return callable instanceof ProtosClosureValue;
    }

    /*
     * I115-H7-R3: the callee's B5 caller slot. Null unless the callee root
     * registered a caller need; the needs are read from the (in doDirect,
     * partial-evaluation constant) callee root under its stability
     * Assumption, so compiled code of a callee without needs reads nothing.
     * A requested prelude is transported through the existing producer
     * contract (InvokeMethodOneTail#callerArgument).
     */
    static Object callerArgumentIfNeeded(
            VirtualFrame frame,
            ProtosBytecodeRootNode root,
            ProtosBytecodeRootNode callee,
            ProtosClosureValue closure) {
        boolean needsPrelude = callee.hasCallerNeed(
                ProtosBytecodeRootNode.NEEDS_CALLER_PRELUDE);
        if (!needsPrelude
                && !callee.hasCallerNeed(ProtosBytecodeRootNode.NEEDS_EXACT_ACTIVATION)) {
            return null;
        }
        Object caller = callerReference(frame);
        if (!needsPrelude) {
            return caller;
        }
        return ProtosBytecodeRootNode.InvokeMethodOneTail.callerArgument(
                callee,
                closure,
                caller,
                ProtosBytecodeRootNode.InvokeClosureOneTail.admittedCallerPrelude(root, caller));
    }

    /**
     * Installation (direct tier) or megamorphic (indirect tier): the
     * Context-owned Bytecode target of a source Closure whose root proves
     * non-suspension, whose invocation ReturnHome is unobservable and which
     * the experimental lowering entered with {@code CopyArguments} (a compact
     * H7 root using only its argument local), or null. The experimental B5
     * header carries no method home and the primitive-result unobservable
     * marker, so every other callee must use the ordinary send path.
     */
    @TruffleBoundary
    static RootCallTarget admittedTargetOrNull(
            ProtosClosureValue closure, ProtosLanguageContext enteredContext) {
        if (closure == null
                || closure.nativeBody().isPresent()
                || !closure.hasUnobservableInvocationReturnHomeForRuntime()) {
            return null;
        }
        RootCallTarget target =
                ProtosGuardedSendSelection.fastOrdinarySendTarget(closure, enteredContext);
        return target != null
                        && target.getRootNode() instanceof ProtosBytecodeRootNode root
                        && root.provablyNonSuspendingBody()
                        && root.isExperimentalPrimitiveArgumentRoot()
                ? target
                : null;
    }

    static ProtosBytecodeRootNode calleeRootOrNull(RootCallTarget target) {
        return target != null
                        && target.getRootNode() instanceof ProtosBytecodeRootNode callee
                ? callee
                : null;
    }

    static DirectCallNode createDirectCallNodeOrNull(RootCallTarget target) {
        return target == null ? null : DirectCallNode.create(target);
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
