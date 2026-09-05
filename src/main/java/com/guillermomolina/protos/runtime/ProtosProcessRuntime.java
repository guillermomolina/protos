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
package com.guillermomolina.protos.runtime;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Internal local Protos Process failure-domain and RootActor failure-authority substrate.
 *
 * <p>A Process is semantic runtime capacity, not an operating-system process. This class performs
 * no OS termination and exposes no language-level Process capability. It only owns the current
 * local Process incarnation, its unique RootActor, and the Actor incarnations currently hosted by
 * that Process so the already-closed Process/Actor failure consequences have one authoritative
 * implementation boundary.
 */
public final class ProtosProcessRuntime {
    public enum LifecycleState {
        RUNNING,
        TERMINATING,
        TERMINATED
    }

    private final ProtosActor rootActor;
    private final Set<ProtosActor> liveActors = new LinkedHashSet<>();
    private LifecycleState lifecycle = LifecycleState.RUNNING;
    private Object rootFailureCause;

    /** Creates one Process incarnation together with its unique RootActor. */
    public ProtosProcessRuntime(ProtosObjectValue actorRefPrototype) {
        rootActor =
                new ProtosActor(
                        Objects.requireNonNull(actorRefPrototype, "actorRefPrototype"),
                        this,
                        true);
        liveActors.add(rootActor);
    }

    public synchronized LifecycleState lifecycleState() {
        return lifecycle;
    }

    public ProtosActor rootActorForRuntime() {
        return rootActor;
    }

    /**
     * Creates one additional Actor hosted by this Process.
     *
     * <p>If Process termination already began while an existing non-preemptive Actor segment is
     * still running, the semantic Actor creation cutover may still occur. The new incarnation is
     * therefore registered and immediately receives the already-established Process termination
     * request instead of escaping into another failure domain.
     */
    public ProtosActor createHostedActorForRuntime(ProtosObjectValue actorRefPrototype) {
        Objects.requireNonNull(actorRefPrototype, "actorRefPrototype");
        ProtosActor actor;
        boolean terminateImmediately;
        synchronized (this) {
            if (lifecycle == LifecycleState.TERMINATED) {
                throw new IllegalStateException("terminated Process cannot host a new Actor");
            }
            actor = new ProtosActor(actorRefPrototype, this, false);
            liveActors.add(actor);
            terminateImmediately = lifecycle == LifecycleState.TERMINATING;
        }
        if (terminateImmediately) {
            actor.requestTerminationForRuntime();
        }
        return actor;
    }

    /** Internal failure-authority entry used only for an unhandled fatal Actor failure. */
    void actorFatalFailureForRuntime(ProtosActor actor, Object failure) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(failure, "failure");
        requireOwned(actor);
        if (actor != rootActor) {
            // Core v0.1 non-root failure authority has no automatic replacement/escalation.
            actor.requestTerminationForRuntime();
            return;
        }
        beginTermination(failure);
    }

    /** Authoritative local Process termination request; this is not an OS-process operation. */
    public boolean requestTerminationForRuntime() {
        return beginTermination(null);
    }

    private boolean beginTermination(Object rootFailure) {
        Set<ProtosActor> terminate;
        synchronized (this) {
            if (rootFailure != null && rootFailureCause == null) {
                rootFailureCause = rootFailure;
            }
            if (lifecycle != LifecycleState.RUNNING) {
                return false;
            }
            lifecycle = LifecycleState.TERMINATING;
            terminate = Set.copyOf(liveActors);
        }

        // Never hold the Process monitor while entering Actor termination. Actor cleanup can invoke
        // producer callbacks and, when complete, calls back into actorTerminatedForRuntime().
        for (ProtosActor actor : terminate) {
            actor.requestTerminationForRuntime();
        }
        tryCompleteTermination();
        return true;
    }

    void actorTerminatedForRuntime(ProtosActor actor) {
        Objects.requireNonNull(actor, "actor");
        synchronized (this) {
            if (!liveActors.remove(actor)) {
                return;
            }
            if (lifecycle == LifecycleState.TERMINATING && liveActors.isEmpty()) {
                lifecycle = LifecycleState.TERMINATED;
                notifyAll();
            }
        }
    }

    private void tryCompleteTermination() {
        synchronized (this) {
            if (lifecycle == LifecycleState.TERMINATING && liveActors.isEmpty()) {
                lifecycle = LifecycleState.TERMINATED;
                notifyAll();
            }
        }
    }

    private void requireOwned(ProtosActor actor) {
        if (actor.processForRuntime().orElse(null) != this) {
            throw new IllegalArgumentException("Actor belongs to another Process runtime");
        }
    }

    synchronized int liveActorCountForTesting() {
        return liveActors.size();
    }

    synchronized Optional<Object> rootFailureCauseForTesting() {
        return Optional.ofNullable(rootFailureCause);
    }
}
