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

import com.guillermomolina.protos.execution.ProtosPreparedCalls.ImmediateResultCall;
import com.guillermomolina.protos.execution.ProtosPreparedCalls.ModuleInitializationCall;
import com.guillermomolina.protos.execution.ProtosPreparedCalls.NativeCall;
import com.guillermomolina.protos.execution.ProtosPreparedCalls.OrdinarySourceCall;
import com.guillermomolina.protos.execution.ProtosPreparedCalls.PreparedClosureCall;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.guillermomolina.protos.runtime.ProtosNumericValueSupport;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.bytecode.BytecodeLocation;
import com.oracle.truffle.api.bytecode.BytecodeNode;
import com.oracle.truffle.api.bytecode.ContinuationResult;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.ControlFlowException;
import java.util.Objects;

/**
 * I112 / PLAT062-T backend-private transport of a terminal Closure invocation
 * that suspended.
 *
 * <p>A terminal invocation ({@code InvokeClosure*Tail}) leaves no guest work in
 * its caller root, so the caller does not yield. When the entered invocation
 * suspends, the caller root returns this transport instead: it retains the
 * real pending result (the child root's own {@link ContinuationResult} with
 * its frame, a nested transport, or the native/I/O suspension descriptor of a
 * native leaf) together with the {@link
 * ProtosPreparedCalls.PreparedClosureCall} that still owns the invocation's
 * completion. It is never allocated for a synchronous result, never exposes a
 * guest value, and is never a new preparation: resuming it continues the
 * suspended state, never the invocation from its beginning.
 *
 * <p>Ownership: when {@code ownerFinishesHere} the transport applies the
 * owner's {@code finish}/{@code handleControlTransfer}/{@code complete} exactly
 * once. A structured call entered through the C-prime dispatcher root keeps
 * that root as the owner of its own finish, ensure/handler regions and
 * cleanup; the transport then only adapts the dispatcher boundary's control
 * transfers, exactly as {@code EnterNestedStructuredDispatch} does. A direct
 * entry without a prepared call has no owner and only projects its result to
 * the guest domain.
 *
 * <p>Like a Bytecode root, the transport reports a control transfer leaving it
 * in bridged form ({@link ProtosBytecodeControlTransferException}), so callers
 * compose it exactly like a child {@link ContinuationResult}. Nested
 * transports complete innermost first: an inner transport finishes its owner
 * before returning its result to the outer one.
 *
 * <p>Diagnostic continuity (CLI008): the transport keeps the logical location of its terminal
 * call, created only when the transport itself is, i.e. only on a real suspension. When an
 * Error crosses its resumption, that caller (no longer a physical frame) is recorded on the
 * occurrence for {@code ProtosDiagnosticTraceCapture}. The location is diagnostic and tooling
 * information only; it is not a physical Truffle frame and no caller continuation exists.
 *
 * <p>Like {@link ContinuationResult}, it is a (message-less) {@link
 * TruffleObject} so a root may legally return it across a CallTarget
 * boundary; it is still never a guest value.
 */
final class ProtosTailCallContinuation implements TruffleObject {
    private enum State { PENDING, RESUMING, DONE }

    private final ProtosPreparedCalls.PreparedClosureCall owner;
    private final boolean ownerFinishesHere;
    private final BytecodeLocation callerLocation;
    private Object pending;
    private State state = State.PENDING;

    private ProtosTailCallContinuation(
            ProtosPreparedCalls.PreparedClosureCall owner,
            Object pending,
            boolean ownerFinishesHere,
            BytecodeLocation callerLocation) {
        this.owner = owner;
        this.callerLocation = callerLocation;
        this.pending = Objects.requireNonNull(pending, "pending");
        this.ownerFinishesHere = owner != null && ownerFinishesHere;
    }

    /**
     * The result of a terminal invocation that produced {@code outcome}.
     * A synchronous outcome is completed here and returned directly, without
     * any allocation; a suspended one is retained, with its owner, in one
     * transport.
     *
     * @param owner the prepared call owning the invocation, or {@code null}
     *     for a direct entry
     * @param ownerFinishesHere whether this transport (rather than a
     *     structured C-prime root) applies the owner's completion
     */
    static Object completeOrDefer(
            ProtosPreparedCalls.PreparedClosureCall owner,
            Object outcome,
            boolean ownerFinishesHere) {
        return completeOrDefer(owner, outcome, ownerFinishesHere, null, -1);
    }

    /**
     * {@link #completeOrDefer(ProtosPreparedCalls.PreparedClosureCall, Object, boolean)} for a
     * terminal call at {@code callerIndex} of {@code callerBytecode}; that location is
     * materialized only if a transport is created.
     */
    static Object completeOrDefer(
            ProtosPreparedCalls.PreparedClosureCall owner,
            Object outcome,
            boolean ownerFinishesHere,
            BytecodeNode callerBytecode,
            int callerIndex) {
        Objects.requireNonNull(outcome, "terminal Closure invocation returned null");
        if (isSuspended(outcome)) {
            return deferred(owner, outcome, ownerFinishesHere, callerBytecode, callerIndex);
        }
        if (owner == null) {
            return ProtosNumericValueSupport.guestValue(outcome);
        }
        return ownerFinishesHere ? owner.finish(outcome) : outcome;
    }

    /**
     * I114: the transport of an admitted direct terminal entry whose outcome
     * {@link #isSuspended was proved suspended} by the caller. A direct entry
     * has no owner, so no prepared call is built; the caller location is
     * materialized only here, inside the cold transport construction.
     *
     * <p>I115: the transport retains only the suspended outcome, which owns
     * the callee frame and therefore its B5 arguments with the caller
     * reference still unresolved. The transport itself never reads caller,
     * Task or dynamic-control state. After resumption the callee's terminal
     * operations and its owning call read them through the shared admitted
     * accessors of ProtosFrameArguments (admittedPreludeOrNull,
     * admittedCompactTask, resolveAdmittedCaller); other readers keep the
     * general accessors. Either way the caller's single activation is
     * published only when an exact identity is required. A PROVEN entry
     * never creates a transport. A caller slot holding a
     * ProtosCallerFrameReference survives suspension unchanged: the
     * reference is immutable, its transported prelude was fixed before the
     * call, and resolution after resumption still publishes through the
     * referenced header, so the same single activation is obtained.
     */
    static Object deferDirect(
            Object suspendedOutcome,
            BytecodeNode callerBytecode,
            int callerIndex) {
        return deferred(null, suspendedOutcome, false, callerBytecode, callerIndex);
    }

    @TruffleBoundary
    private static ProtosTailCallContinuation deferred(
            ProtosPreparedCalls.PreparedClosureCall owner,
            Object outcome,
            boolean ownerFinishesHere,
            BytecodeNode callerBytecode,
            int callerIndex) {
        BytecodeLocation caller =
                callerBytecode == null || callerIndex < 0
                        ? null
                        : callerBytecode.getBytecodeLocation(callerIndex);
        return new ProtosTailCallContinuation(owner, outcome, ownerFinishesHere, caller);
    }

    /**
     * The logical location of the terminal call that created this transport (tooling and
     * diagnostics), or {@code null}. Use {@link BytecodeLocation#update()} after a reparse.
     */
    BytecodeLocation callerLocation() {
        return callerLocation;
    }

    /** The retained pending result, without consuming it (tooling and tests). */
    Object pendingResult() {
        return pending;
    }

    /**
     * Whether {@code outcome} is a resumable pending root result: a Truffle
     * continuation or a terminal transport.
     */
    static boolean isPending(Object outcome) {
        return outcome instanceof ContinuationResult
                || outcome instanceof ProtosTailCallContinuation;
    }

    /** Whether {@code outcome} is any suspended result, including a raw native/I/O leaf. */
    static boolean isSuspended(Object outcome) {
        return outcome instanceof ContinuationResult
                || outcome instanceof ProtosTailCallContinuation
                || outcome instanceof ProtosNativeSuspension
                || outcome instanceof ProtosIoOperationSuspension
                || outcome instanceof ProtosIoReleaseSuspension;
    }

    /**
     * The suspension leaf of a pending result, found through Truffle
     * continuations and terminal transports without consuming either.
     */
    static Object suspensionLeaf(Object outcome) {
        Object current = outcome;
        while (true) {
            if (current instanceof ContinuationResult continuation) {
                current = continuation.getResult();
            } else if (current instanceof ProtosTailCallContinuation transport) {
                current = transport.pending;
            } else {
                return current;
            }
        }
    }

    /** The native suspension leaf of a pending result; see {@link #suspensionLeaf}. */
    static ProtosNativeSuspension nativeSuspensionLeaf(Object outcome) {
        if (!(suspensionLeaf(outcome) instanceof ProtosNativeSuspension suspension)) {
            throw new UnsupportedOperationException(
                    "C-prime suspension requires a native suspension leaf");
        }
        return suspension;
    }

    /**
     * Resumes a pending result with {@code resumeValue}: a Truffle
     * continuation through {@code continueWith}, a terminal transport through
     * its retained state. The result may be pending again.
     */
    static Object resume(Object pending, Object resumeValue) {
        if (pending instanceof ProtosTailCallContinuation transport) {
            return transport.resumeOwned(resumeValue);
        }
        if (pending instanceof ContinuationResult continuation) {
            return continuation.continueWith(resumeValue);
        }
        throw new IllegalStateException(
                "resumed a result that is not a pending C-prime continuation");
    }

    /**
     * Drops a pending result that will never be resumed (host-entry
     * termination): every reachable terminal transport closes its deferred
     * owner, innermost first. Idempotent.
     */
    static void abandon(Object pending) {
        if (pending instanceof ProtosTailCallContinuation transport) {
            transport.abandonOwned();
        } else if (pending instanceof ContinuationResult continuation) {
            abandon(continuation.getResult());
        }
    }

    @TruffleBoundary
    private Object resumeOwned(Object resumeValue) {
        /*
         * Each retained pending result is resumed at most once: RESUMING
         * rejects a re-entrant resumption of the same pending state, and DONE
         * a resumption after the owner was completed. Suspending again stores
         * the new pending result and returns to PENDING, so the legitimate
         * sequence suspend A, resume A, suspend B, resume B is accepted with
         * the same owner.
         */
        if (state != State.PENDING) {
            throw new IllegalStateException(
                    "terminal Closure continuation resumed while not pending");
        }
        state = State.RESUMING;
        Object next;
        try {
            next = Objects.requireNonNull(
                    resumeInner(resumeValue),
                    "resumed terminal Closure continuation returned null");
        } catch (ProtosBytecodeControlTransferException bridged) {
            state = State.DONE;
            return completeTransfer(bridged.transfer());
        } catch (ControlFlowException transfer) {
            state = State.DONE;
            return completeTransfer(transfer);
        } catch (AbstractTruffleException failure) {
            state = State.DONE;
            ProtosDiagnosticTraceCapture.recordResumedTerminalCaller(failure, callerLocation);
            if (owner != null) {
                owner.failIfModuleInitialization();
                if (ownerFinishesHere) {
                    owner.complete();
                }
            }
            throw failure;
        } catch (RuntimeException failure) {
            state = State.DONE;
            if (owner == null) {
                throw failure;
            }
            if (ownerFinishesHere) {
                owner.complete();
            }
            throw owner.mapRuntimeFailure(failure);
        } catch (Error failure) {
            state = State.DONE;
            if (ownerFinishesHere) {
                owner.complete();
            }
            throw failure;
        }
        if (isSuspended(next)) {
            // Suspended again: the same owner keeps its completion pending.
            pending = next;
            state = State.PENDING;
            return this;
        }
        state = State.DONE;
        pending = null;
        if (owner == null) {
            return ProtosNumericValueSupport.guestValue(next);
        }
        return ownerFinishesHere ? owner.finish(next) : next;
    }

    private Object resumeInner(Object resumeValue) {
        Object current = pending;
        if (current instanceof ProtosTailCallContinuation nested) {
            return nested.resumeOwned(resumeValue);
        }
        if (current instanceof ContinuationResult continuation) {
            return continuation.continueWith(resumeValue);
        }
        if (owner == null) {
            throw new IllegalStateException(
                    "a direct terminal entry cannot retain a native suspension leaf");
        }
        if (current instanceof ProtosNativeSuspension suspension) {
            return ProtosBytecodeRootNode.ResumeContinuation.nativeSuspension(
                    owner, suspension, resumeValue);
        }
        if (current instanceof ProtosIoOperationSuspension suspension) {
            return ProtosBytecodeRootNode.ResumeContinuation.ioOperationSuspension(
                    owner, suspension, resumeValue);
        }
        throw new IllegalStateException(
                "terminal Closure continuation retained an unsupported suspension leaf");
    }

    /*
     * The invocation left through a control transfer. Its owner selects its own
     * non-local return; any other transfer completes the owner and continues
     * unwinding in the bridged form a Bytecode root would produce.
     */
    private Object completeTransfer(ControlFlowException transfer) {
        if (owner == null) {
            throw ProtosBytecodeControlTransferException.bridge(transfer);
        }
        Object handled;
        try {
            handled = owner.handleControlTransfer(transfer);
        } catch (ControlFlowException escaped) {
            if (ownerFinishesHere) {
                owner.complete();
            }
            throw ProtosBytecodeControlTransferException.bridge(escaped);
        } catch (RuntimeException | Error escaped) {
            if (ownerFinishesHere) {
                owner.complete();
            }
            throw escaped;
        }
        return ownerFinishesHere ? owner.finish(handled) : handled;
    }

    @TruffleBoundary
    private void abandonOwned() {
        if (state != State.PENDING) {
            return;
        }
        state = State.DONE;
        Object inner = pending;
        pending = null;
        abandon(inner);
        if (ownerFinishesHere) {
            owner.complete();
        }
    }

    /**
     * I112 / PLAT062-T terminal execution of a prepared call selected inside a
     * terminal invocation operation ({@code InvokeClosure*Tail}). It selects
     * the entry of the four existing representations exactly as {@link
     * EnterClosureCall} and {@link EnterNestedStructuredDispatch} do, then
     * completes the call here: a synchronous result is finished directly; a
     * suspended one is returned as one {@link ProtosTailCallContinuation}
     * retaining this call as the owner of its pending completion.
     *
     * <p>A structured native call enters the Context's existing C-prime
     * dispatcher root, which keeps ownership of its own finish, ensure and
     * handler regions and cleanup; this method then never finishes the same
     * call again and only adapts the dispatcher boundary. Every other
     * representation is finished, or completed on an abrupt exit, exactly
     * once by this method or by its transport.
     */
    static Object executeTerminalPreparedCall(
            PreparedClosureCall prepared,
            IndirectCallNode indirectCall,
            BytecodeNode callerBytecode,
            int callerIndex) {
        boolean ownerFinishesHere = !prepared.requiresStructuredDispatch();
        Object outcome;
        try {
            outcome = enterTerminalPreparedCall(prepared, indirectCall);
        } catch (ProtosBytecodeControlTransferException bridged) {
            Object handled;
            try {
                handled = prepared.handleControlTransfer(bridged.transfer());
            } catch (RuntimeException | Error escaped) {
                if (ownerFinishesHere) {
                    prepared.complete();
                }
                throw escaped;
            }
            return ownerFinishesHere ? prepared.finish(handled) : handled;
        } catch (AbstractTruffleException failure) {
            prepared.failIfModuleInitialization();
            if (ownerFinishesHere) {
                prepared.complete();
            }
            throw failure;
        } catch (ControlFlowException transfer) {
            if (ownerFinishesHere) {
                prepared.complete();
            }
            throw transfer;
        } catch (RuntimeException failure) {
            if (ownerFinishesHere) {
                prepared.complete();
            }
            throw prepared.mapRuntimeFailure(failure);
        } catch (Error failure) {
            if (ownerFinishesHere) {
                prepared.complete();
            }
            throw failure;
        }
        return ProtosTailCallContinuation.completeOrDefer(
                prepared, outcome, ownerFinishesHere, callerBytecode, callerIndex);
    }

    private static Object enterTerminalPreparedCall(
            PreparedClosureCall prepared,
            IndirectCallNode indirectCall) {
        if (prepared instanceof OrdinarySourceCall ordinary) {
            return indirectCall.call(ordinary.bodyTarget(), ordinary.targetArguments());
        }
        if (prepared instanceof NativeCall nativeCall) {
            if (nativeCall.requiresStructuredDispatch()) {
                return indirectCall.call(
                        ProtosTaskCPrimeEntryExecution.structuredDispatchTargetForEnteredContext(),
                        nativeCall.activation(),
                        nativeCall);
            }
            return nativeCall.enterNative();
        }
        if (prepared instanceof ImmediateResultCall immediate) {
            return immediate.enterImmediate();
        }
        if (prepared instanceof ModuleInitializationCall module) {
            if (module.isImmediate()) {
                return module.enterImmediate();
            }
            return indirectCall.call(module.bodyTarget(), module.targetArguments());
        }
        throw new IllegalStateException(
                "unsupported prepared Closure call representation");
    }
}
