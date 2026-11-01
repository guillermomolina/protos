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
import com.guillermomolina.protos.runtime.ProtosActorExecutionDomain;
import com.guillermomolina.protos.runtime.ProtosActorExecutionDomain.HostEntryExtent;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosFutureValue;
import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.guillermomolina.protos.runtime.ProtosNumericValueSupport;
import com.guillermomolina.protos.runtime.ProtosTask;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleSafepoint;
import java.util.List;
import java.util.Objects;

/**
 * PLAT054-3E2 suspension of an outermost standard-embedding host entry ({@code
 * FUTURES_AND_TASKS.md} §29, Suspendible host-initiated RootActor entries, HOST-FUT-1).
 *
 * <p>The entry stays on its Task-free call path: the compact direct call, or, for a Closure without
 * a compact target, the shared C-prime entry root ({@link #executeSelected}). Nothing here is allocated unless an
 * explicit suspension point actually finds its prerequisite pending: the domain only records the
 * {@link HostEntryExtent} of the running entry. A pending {@code Future.value()} then registers one
 * ordinary Future observer atomically with the pending-state check and returns the same
 * backend-private {@link ProtosNativeSuspension} leaf a Task uses, so the Bytecode roots between the
 * leaf and the entry yield the exact C-prime continuation. No second evaluator and no replay
 * exist: resumption continues that continuation, and the leaf re-projects the terminal outcome
 * through the ordinary §29 rules.
 *
 * <p>Custody: the suspended continuation is retained only by the host thread that is blocked in
 * {@link #resumeAfterWaits} while it still holds the RootActor entry, so RootActor exclusivity and
 * the unsafe-concurrent-entry rejection are unchanged. While blocked it dispatches the RootActor's
 * own runnable work (which may be what terminalizes the Future) and otherwise parks in a
 * Truffle-interruptible region, so a cancelling Context close interrupts it. Process termination
 * or Context finalization ends the wait: the observer is removed, the continuation is dropped
 * without resuming guest code, and the host observes termination. The observed Future is never
 * cancelled by this.
 */
final class ProtosHostEntrySuspension {
    private ProtosHostEntrySuspension() {}

    /**
     * Test-only, read-only observation; null in production. Called on the entry thread once a
     * host entry holds its suspended continuation and is about to wait for the observed Future.
     * It changes nothing in the HOST-FUT-1 protocol.
     */
    static volatile java.util.function.Consumer<ProtosFutureValue> suspendedForTesting;

    /** Whether {@code activation} executes directly inside a suspendible outermost host entry. */
    static boolean suspendible(ProtosActivation activation) {
        ProtosActorExecutionDomain domain = activation.executionDomain();
        return domain != null
                && domain.hostEntryExtentForRuntime() == HostEntryExtent.SUSPENDIBLE
                && activation.task().isEmpty();
    }

    /**
     * The host-entry continuation body of {@code Future.value()}: the terminal outcome now, or the
     * suspension leaf whose resumer projects the terminal outcome after the registered observer
     * fired.
     */
    @TruffleBoundary
    static Object awaitFuture(ProtosActivation activation, ProtosFutureValue observed) {
        FutureDependency dependency = new FutureDependency(observed);
        // Registration and the pending check share the Future monitor: no terminalization is lost.
        observed.observe(dependency);
        if (dependency.isReady()) {
            return observed.observeValue(activation);
        }
        return ProtosNativeSuspension.pending(dependency, () -> observed.observeValue(activation));
    }

    /**
     * Drives a host-entry call whose body yielded {@code outcome} until it
     * produces its ordinary result. I115: the pending result belongs to the
     * callee frames, whose roots own their ReturnHomes; the driver only waits
     * and resumes.
     */
    @TruffleBoundary
    static Object resumeAfterWaits(ProtosEmbeddedProcess embedding, Object outcome) {
        return drive(embedding, outcome);
    }

    /**
     * An outermost host entry of a Closure through the shared dispatcher,
     * suspendible exactly like a source entry: the host-entry extent of the
     * caller's domain lets suspension-capable builtins yield.
     */
    @TruffleBoundary
    static Object executeSelected(
            ProtosEmbeddedProcess embedding,
            ProtosClosureValue closure,
            Object[] supplied,
            ProtosActivation caller) {
        Object outcome;
        try {
            outcome =
                    Objects.requireNonNull(
                            ProtosCallRequest.call(closure, List.of(supplied), caller).callUncached(),
                            "host entry returned null");
        } catch (ProtosBytecodeControlTransferException bridged) {
            throw bridged.transfer();
        }
        return drive(embedding, outcome);
    }

    private static Object drive(ProtosEmbeddedProcess embedding, Object outcome) {
        Objects.requireNonNull(embedding, "embedding");
        while (ProtosTailCallContinuation.isPending(outcome)) {
            Object continuation = outcome;
            awaitReady(embedding, suspensionLeaf(continuation).dependency(), continuation);
            try {
                outcome =
                        Objects.requireNonNull(
                                ProtosTailCallContinuation.resume(
                                        continuation, ProtosNullValue.INSTANCE),
                                "resumed host-entry continuation returned null");
            } catch (ProtosBytecodeControlTransferException bridged) {
                throw bridged.transfer();
            }
        }
        return ProtosNumericValueSupport.guestValue(outcome);
    }

    private static void awaitReady(
            ProtosEmbeddedProcess embedding,
            ProtosTask.WaitDependency dependency,
            Object continuation) {
        ProtosActorExecutionDomain domain = embedding.rootExecutionDomain();
        // Actor-local work dispatched while waiting is not inside this entry's Bytecode extent.
        HostEntryExtent entryExtent = domain.swapHostEntryExtentForRuntime(HostEntryExtent.NONE);
        boolean ready = false;
        try {
            java.util.function.Consumer<ProtosFutureValue> observation = suspendedForTesting;
            if (observation != null && dependency instanceof FutureDependency observer) {
                observation.accept(observer.future);
            }
            while (true) {
                /*
                 * Liveness first: termination itself may terminalize the observed Future (Actor
                 * TERMINATING cancels registered non-Task Futures), and a terminated entry must
                 * not resume even when its dependency became ready (§29 rule 5).
                 */
                if (!embedding.hostEntryMayResume()) {
                    throw embedding.suspendedEntryTerminated();
                }
                if (dependency.isReady()) {
                    break;
                }
                if (domain.dispatchOne()) {
                    continue;
                }
                TruffleSafepoint.setBlockedThreadInterruptible(
                        null,
                        waiting ->
                                waiting.awaitHostEntryWakeupForRuntime(
                                        () ->
                                                dependency.isReady()
                                                        || !embedding.hostEntryMayResume()),
                        domain);
            }
            ready = true;
        } finally {
            domain.swapHostEntryExtentForRuntime(entryExtent);
            if (!ready) {
                // Abandoned: never resumed, so release the wait and close the return home.
                if (dependency instanceof FutureDependency observer) {
                    observer.release();
                }
                // Inner continuations first, then the homes their frames own.
                ProtosTailCallContinuation.abandon(continuation);
            }
        }
    }

    private static ProtosNativeSuspension suspensionLeaf(Object top) {
        Object current = ProtosTailCallContinuation.suspensionLeaf(top);
        if (!(current instanceof ProtosNativeSuspension suspension)) {
            throw new IllegalStateException(
                    "host-entry C-prime suspension requires a native suspension leaf");
        }
        return suspension;
    }

    /** One host entry's ordinary observer of one pending Future; inert once terminal. */
    private static final class FutureDependency
            implements ProtosTask.WaitDependency, ProtosFutureValue.Observer {
        private final ProtosFutureValue future;
        private volatile boolean ready;

        private FutureDependency(ProtosFutureValue future) {
            this.future = future;
        }

        @Override
        public void terminal(ProtosFutureValue terminal) {
            ready = true;
            future.domain().wakeHostEntryWaiterForRuntime();
        }

        @Override
        public boolean isReady() {
            return ready;
        }

        void release() {
            future.removeObserver(this);
        }
    }
}
