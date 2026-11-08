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

import com.guillermomolina.protos.runtime.ProtosNumericValueSupport;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.guillermomolina.protos.runtime.control.ProtosReturnHome;
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
 * <p>A terminal invocation leaves no guest work in its caller root, so the
 * caller does not yield. When the entered invocation suspends, the caller
 * root returns this transport instead: it retains the real pending result
 * (the callee root's own {@link ContinuationResult} with its frame, a nested
 * transport, or the native/I/O suspension descriptor of a builtin leaf). It
 * is never allocated for a synchronous result, never exposes a guest value,
 * and resuming it continues the suspended state, never the invocation from
 * its beginning.
 *
 * <p>I115 (TruffleRuby): the transport owns no invocation. The ReturnHome of
 * a suspended activation stays in that activation's frame and is captured and
 * closed by its own root when it completes (see {@link
 * ProtosBytecodeRootNode#interceptControlFlowException}); this transport only
 * propagates results and transfers. On abandonment it closes, innermost
 * first, the homes the abandoned frames own.
 *
 * <p>Like a Bytecode root, the transport reports a control transfer leaving it
 * directly as {@link ControlFlowException}.
 *
 * <p>Diagnostic continuity (CLI008): the transport keeps the logical location
 * of its terminal call, created only on a real suspension.
 *
 * <p>Like {@link ContinuationResult}, it is a (message-less) {@link
 * TruffleObject} so a root may legally return it across a CallTarget
 * boundary; it is still never a guest value.
 */
final class ProtosTailCallContinuation implements TruffleObject {
    private enum State { PENDING, RESUMING, DONE }

    private final BytecodeLocation callerLocation;
    private Object pending;
    private State state = State.PENDING;

    private ProtosTailCallContinuation(Object pending, BytecodeLocation callerLocation) {
        this.callerLocation = callerLocation;
        this.pending = Objects.requireNonNull(pending, "pending");
    }

    /**
     * The transport of a terminal entry whose outcome {@link #isSuspended was
     * proved suspended} by the caller; the caller location is materialized
     * only here, inside the cold transport construction. The transport never
     * reads caller, Task or dynamic-control state: the suspended callee frame
     * keeps its B5 arguments unchanged.
     */
    @TruffleBoundary
    static Object deferDirect(
            Object suspendedOutcome,
            BytecodeNode callerBytecode,
            int callerIndex) {
        BytecodeLocation caller =
                callerBytecode == null || callerIndex < 0
                        ? null
                        : callerBytecode.getBytecodeLocation(callerIndex);
        return new ProtosTailCallContinuation(suspendedOutcome, caller);
    }

    /** A terminal transport without a caller location (host and Task drivers). */
    @TruffleBoundary
    static Object defer(Object suspendedOutcome) {
        return new ProtosTailCallContinuation(suspendedOutcome, null);
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
     * Resumes a suspended result with {@code resumeValue}: a Truffle
     * continuation through {@code continueWith}, a terminal transport through
     * its retained state, a builtin leaf through its own descriptor. The
     * result may be pending again.
     */
    static Object resume(Object pending, Object resumeValue) {
        if (pending instanceof ProtosTailCallContinuation transport) {
            return transport.resumeOwned(resumeValue);
        }
        if (pending instanceof ContinuationResult continuation) {
            return continuation.continueWith(resumeValue);
        }
        return resumeLeaf(pending, resumeValue);
    }

    /*
     * A builtin suspension leaf owns the state needed to finish its logical
     * operation. The backend-private cancellation transfer and a D189
     * rejected-suspension Error are thrown at the suspension point; the
     * descriptor is then never resumed.
     */
    @TruffleBoundary
    static Object resumeLeaf(Object leaf, Object resumeValue) {
        if (resumeValue instanceof ProtosTaskCancellationException cancellation) {
            throw cancellation;
        }
        if (resumeValue instanceof ProtosSignalException rejectedSuspension) {
            throw rejectedSuspension;
        }
        if (leaf instanceof ProtosNativeSuspension suspension) {
            return suspension.resume();
        }
        if (leaf instanceof ProtosIoOperationSuspension suspension) {
            return suspension.resume();
        }
        throw new IllegalStateException(
                "resumed a result that is not a pending continuation");
    }

    /**
     * Drops a pending result that will never be resumed (host-entry or Task
     * termination). Inner continuations are abandoned first; then every
     * ReturnHome owned by an abandoned frame is closed, innermost first.
     * Captured homes belong to outer activations and are left untouched.
     * Idempotent.
     */
    static void abandon(Object pending) {
        if (pending instanceof ProtosTailCallContinuation transport) {
            transport.abandonOwned();
        } else if (pending instanceof ContinuationResult continuation) {
            abandon(continuation.getResult());
            closeOwnedHome(continuation);
        }
    }

    @TruffleBoundary
    private static void closeOwnedHome(ContinuationResult continuation) {
        ProtosReturnHome owned =
                ProtosFrameArguments.ownedReturnHomeOrNull(
                        continuation.getFrame().getArguments());
        if (owned != null) {
            owned.closeOwned();
        }
    }

    @TruffleBoundary
    private Object resumeOwned(Object resumeValue) {
        /*
         * Each retained pending result is resumed at most once: RESUMING
         * rejects a re-entrant resumption, DONE a resumption after completion.
         * Suspending again stores the new pending result and returns to
         * PENDING with the same transport and frames.
         */
        if (state != State.PENDING) {
            throw new IllegalStateException(
                    "terminal Closure continuation resumed while not pending");
        }
        state = State.RESUMING;
        Object next;
        try {
            next = Objects.requireNonNull(
                    resume(pending, resumeValue),
                    "resumed terminal Closure continuation returned null");
        } catch (ControlFlowException transfer) {
            state = State.DONE;
            throw transfer;
        } catch (AbstractTruffleException failure) {
            state = State.DONE;
            ProtosDiagnosticTraceCapture.recordResumedTerminalCaller(failure, callerLocation);
            throw failure;
        } catch (RuntimeException | Error failure) {
            state = State.DONE;
            throw failure;
        }
        if (isSuspended(next)) {
            pending = next;
            state = State.PENDING;
            return this;
        }
        state = State.DONE;
        pending = null;
        return ProtosNumericValueSupport.guestValue(next);
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
    }
}
