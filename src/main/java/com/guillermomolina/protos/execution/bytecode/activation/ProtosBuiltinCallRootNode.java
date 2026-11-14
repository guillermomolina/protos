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
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosNumericValueSupport;
import com.guillermomolina.protos.runtime.callables.ProtosNativeClosureBody;
import com.guillermomolina.protos.runtime.callables.ProtosSuspensionCapableNativeClosureBody;
import com.guillermomolina.protos.runtime.control.ProtosReturnHome;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ControlFlowException;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.RootNode;
import java.util.List;
import java.util.Objects;

/**
 * The Truffle root of one native
 * Closure body. Entered with the same B5 header as a source function:
 * Closure, receiver or function marker, methodHome, caller and ReturnHome,
 * then the supplied arguments. One CallTarget per body and Context, cached
 * by {@link ProtosLanguageContext#builtinCallTarget}.
 *
 * <p>Standard Integer arithmetic consumes the B5 operands directly. Other native
 * bodies materialize their activation only when their body requires one. A suspension-capable body running under a Task, a
 * deferred I/O operation or a suspendible host entry may return a
 * suspension leaf; the leaf is returned in a terminal transport so the
 * caller composes it as any pending callee result. A structured standard
 * native (ensure, Error handle, loops, collection iteration, Boolean
 * control, Object.call, import) enters the Context's structured-protocol
 * root, whose pending results compose the same way. No callback dispatch is
 * implemented here: callbacks re-enter {@link ProtosFunctionCallDispatchers}.
 */
final class ProtosBuiltinCallRootNode extends RootNode {
    /** The host entry convention {@code (activation, closure)}; never a B5 header. */
    static final int PUBLISHED_ENTRY_LENGTH = 2;

    private final ProtosNativeClosureBody body;
    @Child private IndirectCallNode structuredCall = IndirectCallNode.create();

    ProtosBuiltinCallRootNode(ProtosLanguage language, ProtosNativeClosureBody body) {
        super(language);
        this.body = Objects.requireNonNull(body, "body");
    }

    @Override
    public Object execute(VirtualFrame frame) {
        Object[] arguments = frame.getArguments();
        ProtosClosureValue closure;
        ProtosActivation activation;
        if (arguments.length == PUBLISHED_ENTRY_LENGTH) {
            // Host entry without a caller: (activation, closure).
            activation = (ProtosActivation) arguments[0];
            closure = (ProtosClosureValue) arguments[1];
        } else {
            closure = ProtosFrameArguments.compactClosure(arguments);
            if (!ProtosFrameArguments.isFunctionCall(arguments)
                    && ProtosStandardIntegerProtocol.isCanonicalArithmetic(closure)) {
                ProtosPrelude prelude = ProtosFrameArguments.admittedPreludeOrNull(arguments);
                var operation = ProtosStandardIntegerProtocol.canonicalOperationForSelection(
                        closure, ProtosFrameArguments.compactMethodHome(arguments), prelude);
                if (operation != null) {
                    return executeInteger(arguments, operation, prelude);
                }
            }
            try {
                activation = ProtosFrameArguments.activation(arguments);
            } catch (RuntimeException | Error failure) {
                ProtosReturnHome owned = ProtosFrameArguments.ownedReturnHomeOrNull(arguments);
                if (owned != null) {
                    owned.closeOwned();
                }
                throw failure;
            }
        }
        Object result;
        try {
            result = executeBody(closure, activation);
        } catch (ControlFlowException transfer) {
            return ProtosFunctionCallDispatchers.completeOwnedTransfer(
                    activation.ownsReturnHome()
                            ? activation.returnHomeOrNullForRuntime() : null,
                    transfer);
        } catch (RuntimeException | Error failure) {
            closeOwnedHome(activation);
            throw failure;
        }
        if (ProtosTailCallContinuation.isSuspended(result)) {
            ProtosReturnHome ownedHome =
                    activation.ownsReturnHome()
                            ? activation.returnHomeOrNullForRuntime()
                            : null;

            if (ownedHome != null && ownedHome.isMaterialized()) {
                return ProtosTailCallContinuation.deferOwned(
                        result, ownedHome);
            }

            return ProtosTailCallContinuation.isPending(result)
                    ? result
                    : ProtosTailCallContinuation.defer(result);
        }

        closeOwnedHome(activation);
        return result;
    }

    /** A proven native numeric body needs only its B5 operands and owning Core. */
    private static Object executeInteger(Object[] arguments,
            ProtosStandardIntegerProtocol.CanonicalIntegerOperation operation,
            ProtosPrelude prelude) {
        Object result = null;
        try {
            if (ProtosFrameArguments.compactSuppliedArgumentCount(arguments) == 1) {
                result = ProtosStandardIntegerProtocol.tryExecuteCanonicalOperationOne(
                        operation, ProtosFrameArguments.currentReceiver(arguments),
                        ProtosFrameArguments.compactSuppliedArgumentWithinCount(arguments, 0), prelude);
            }
            if (result == null) {
                ProtosActivation caller = ProtosFrameArguments.compactCaller(arguments);
                throw ProtosCoreErrors.signal(caller, ProtosCoreErrors.newError(caller));
            }
            return result;
        } catch (ArithmeticException invalidArithmetic) {
            ProtosActivation caller = ProtosFrameArguments.compactCaller(arguments);
            throw ProtosCoreErrors.signal(caller, ProtosCoreErrors.newError(caller));
        } finally {
            ProtosReturnHome owned = ProtosFrameArguments.ownedReturnHomeOrNull(arguments);
            if (owned != null) owned.closeOwned();
        }
    }

    private Object executeBody(ProtosClosureValue closure, ProtosActivation activation) {
        List<?> supplied = activation.suppliedArgumentsForRuntime();
        ProtosPreparedCalls.StructuredNativeInvocation structured =
                ProtosPreparedCalls.StructuredNativeInvocation.forBodyOrNull(
                        closure,
                        supplied,
                        activation);
        if (structured != null) {

                return structuredCall.call(
                        ProtosTaskCPrimeEntryExecution.structuredDispatchTargetForEnteredContext(),
                        activation,
                        structured);

        }
        if (body instanceof ProtosSuspensionCapableNativeClosureBody suspensionCapable
                && suspendible(activation)) {
            return executeForContinuation(suspensionCapable, activation, supplied);
        }
        return executeNative(body, activation, supplied);
    }

    /*
     * A native body is ordinary host code, not a node tree: partial evaluation
     * stops at its entry. Guest Errors and non-local transfers raised by the
     * body propagate through the boundary without invalidating the caller.
     */
    @TruffleBoundary(transferToInterpreterOnException = false)
    private static Object executeNative(
            ProtosNativeClosureBody body, ProtosActivation activation, List<?> supplied) {
        return ProtosNumericValueSupport.guestValue(body.execute(activation, supplied));
    }

    @TruffleBoundary(transferToInterpreterOnException = false)
    private static Object executeForContinuation(
            ProtosSuspensionCapableNativeClosureBody body,
            ProtosActivation activation,
            List<?> supplied) {
        return body.executeForBytecodeContinuation(activation, supplied);
    }

    @TruffleBoundary
    private static boolean suspendible(ProtosActivation activation) {
        return activation.task().isPresent()
                || activation.deferredCPrimeOperationForRuntime().isPresent()
                || activation.deferredCPrimeReleaseForRuntime().isPresent()
                || ProtosHostEntrySuspension.suspendible(activation)
                // Task-free Actor initialization: only Future.value() may suspend it.
                || ProtosActorBootstrapSuspension.admitsNativeContinuation(activation);
    }

    private static void closeOwnedHome(ProtosActivation activation) {
        if (activation.ownsReturnHome()) {
            ProtosReturnHome owned = activation.returnHomeOrNullForRuntime();
            if (owned != null) {
                owned.closeOwned();
            }
        }
    }

    @Override
    public String getName() {
        return "builtin";
    }

    @Override
    public boolean isInternal() {
        return true;
    }
}
