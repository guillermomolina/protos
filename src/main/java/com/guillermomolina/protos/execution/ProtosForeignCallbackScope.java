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
import com.guillermomolina.protos.runtime.ProtosActor;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.guillermomolina.protos.runtime.ProtosTask;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * D189 lifetime of the callbacks handed to one foreign operation, created only when an outbound
 * argument is a Closure.
 *
 * <p>Preparing callbacks before entry gives them no lifetime: the scope becomes live when {@link
 * ProtosForeignOperation} enters the operation and expires, exactly once, when the operation leaves
 * by any path. Expiry is explicit, never left to garbage collection, and drops every reference to
 * the Closure, the calling activation, and the provider session, so a retained capability roots and
 * revives nothing. Sequential and nested operations each own an independent scope.
 *
 * <p>Invocation is admitted only on the thread executing the operation while it is live, which
 * excludes foreign-created threads and any concurrent entry without serializing anything beyond
 * this one operation. Every check precedes argument admission and guest entry.
 *
 * <p>A Protos outcome leaving a callback is thrown to the provider wrapped in an {@link Outcome}
 * carrier bound to this scope. Only that exact carrier leaving this exact operation resumes the
 * original outcome ({@link #recognizes}); a carrier of another operation, or anything wrapping
 * or replacing it, is an ordinary foreign failure.
 */
final class ProtosForeignCallbackScope {
    private enum State {
        PREPARED,
        LIVE,
        EXPIRED
    }

    private volatile State state = State.PREPARED;
    /** The thread executing the operation's synchronous segment; written before going live. */
    private Thread segmentThread;
    private ProtosForeignHandle origin;
    private ProtosActivation caller;
    private final List<ProtosForeignCallback> callbacks = new ArrayList<>(1);
    private List<Outcome> carriers;
    /** A Task cancellation transfer that left a callback of this operation, if any. */
    private ProtosTaskCancellationException crossedCancellation;

    ProtosForeignCallbackScope(ProtosForeignHandle origin, ProtosActivation caller) {
        this.origin = Objects.requireNonNull(origin, "origin");
        this.caller = Objects.requireNonNull(caller, "caller");
    }

    /** Prepares the capability of one Closure argument; it is usable only once entered. */
    ProtosForeignCallback prepare(ProtosClosureValue closure) {
        if (state != State.PREPARED) {
            throw new IllegalStateException("callbacks are prepared only before entry");
        }
        ProtosForeignCallback callback = new ProtosForeignCallback(this, closure);
        callbacks.add(callback);
        return callback;
    }

    /** Called by the operation boundary right after the session check, before provider entry. */
    void enter() {
        if (state != State.PREPARED) {
            throw new IllegalStateException("callback scope entered twice");
        }
        segmentThread = Thread.currentThread();
        state = State.LIVE;
    }

    /** Ends the lifetime on every exit path; repeated calls do nothing. */
    void expire() {
        if (state == State.EXPIRED) {
            return;
        }
        state = State.EXPIRED;
        for (ProtosForeignCallback callback : callbacks) {
            callback.expireForRuntime();
        }
        callbacks.clear();
        if (carriers != null) {
            for (Outcome carrier : carriers) {
                carrier.outcome = null;
            }
            carriers = null;
        }
        segmentThread = null;
        origin = null;
        caller = null;
        crossedCancellation = null;
    }

    /** True only for the exact carrier this scope issued, while the operation is still live. */
    boolean recognizes(Throwable failure) {
        return failure instanceof Outcome carrier
                && carrier.scope == this
                && state == State.LIVE
                && carrier.outcome != null;
    }

    /** The original Protos outcome of a recognized carrier, to be re-thrown unchanged. */
    RuntimeException resume(Throwable recognized) {
        return ((Outcome) recognized).outcome;
    }

    /**
     * A Task cancellation that left a callback has already been observed by Protos and cannot be
     * swallowed by foreign code: after a normal return it keeps unwinding.
     */
    void requireNoSwallowedCancellation() {
        if (crossedCancellation != null) {
            throw crossedCancellation;
        }
    }

    /**
     * A different failure returned after a crossed cancellation replaces it, like an Error
     * escaping cleanup during cancellation unwind.
     */
    void supersedeCrossedCancellation() {
        if (crossedCancellation != null) {
            caller.task().ifPresent(ProtosTask::supersedeCancellationUnwind);
        }
    }

    ProtosForeignArgument invoke(ProtosForeignCallback callback, List<?> foreignArguments)
            throws Exception {
        if (state != State.LIVE) {
            throw new ProtosForeignCallback.Rejection("foreign callback expired");
        }
        if (Thread.currentThread() != segmentThread) {
            throw new ProtosForeignCallback.Rejection(
                    "foreign callback invoked outside its synchronous operation");
        }
        if (crossedCancellation != null) {
            throw new ProtosForeignCallback.Rejection("foreign callback Task is unwinding");
        }
        Object callable = callback.callableOrNullForRuntime();
        ProtosForeignHandle origin = this.origin;
        ProtosActivation caller = this.caller;
        if (callable == null || origin == null || caller == null) {
            throw new ProtosForeignCallback.Rejection("foreign callback expired");
        }
        ProtosForeignProviderSession live = liveSessionOrNull(origin.session());
        if (live == null) {
            throw new ProtosForeignCallback.Rejection("foreign callback session closed");
        }
        ProtosTask task = caller.task().orElse(null);
        if (task != null && task.state() != ProtosTask.State.RUNNING) {
            throw new ProtosForeignCallback.Rejection("foreign callback Task is not running");
        }
        ProtosActor actor = caller.executionDomain().currentActorForRuntime().orElse(null);
        if (actor != null && actor.lifecycleState() == ProtosActor.LifecycleState.TERMINATED) {
            throw new ProtosForeignCallback.Rejection("foreign callback Actor terminated");
        }

        List<Object> admitted = new ArrayList<>(foreignArguments.size());
        for (Object foreign : foreignArguments) {
            admitted.add(
                    ProtosForeignValueAdmission.admit(
                            origin.session(), origin.adapter(), live, foreign));
        }

        Object result;
        try {
            result = ProtosInvocation.invokeFromForeignCallbackForRuntime(callable, admitted, caller);
        } catch (RuntimeException outcome) {
            throw carry(outcome);
        }
        ProtosForeignArgument projected = ProtosForeignValueAdmission.exportOrNull(origin, result);
        if (projected == null) {
            throw new ProtosForeignCallback.Rejection("foreign callback result has no projection");
        }
        return projected;
    }

    private Outcome carry(RuntimeException outcome) {
        if (outcome instanceof ProtosTaskCancellationException cancellation) {
            crossedCancellation = cancellation;
        }
        if (outcome instanceof ProtosSignalException signal) {
            signal.releaseHandlerSelectionForForeignCallbackForRuntime();
        }
        Outcome carrier = new Outcome(this, outcome);
        if (carriers == null) {
            carriers = new ArrayList<>(1);
        }
        carriers.add(carrier);
        return carrier;
    }

    private static ProtosForeignProviderSession liveSessionOrNull(
            ProtosForeignProviderSessionBinding session) {
        if (!session.isOpenForRuntime()) {
            return null;
        }
        try {
            return session.sessionForRuntime();
        } catch (IllegalStateException closedConcurrently) {
            return null;
        }
    }

    /**
     * Implementation-private carrier of one Protos outcome through foreign frames. It exposes no
     * message, stack, or accessor; the carried outcome is dropped when the operation leaves.
     */
    static final class Outcome extends RuntimeException {
        private static final long serialVersionUID = 1L;

        private final transient ProtosForeignCallbackScope scope;
        private transient RuntimeException outcome;

        private Outcome(ProtosForeignCallbackScope scope, RuntimeException outcome) {
            super(null, null, false, false);
            this.scope = scope;
            this.outcome = outcome;
        }
    }
}
