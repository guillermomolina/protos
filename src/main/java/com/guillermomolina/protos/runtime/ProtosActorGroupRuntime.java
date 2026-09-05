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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Internal local ActorGroup identity, membership, and pre-acceptance routing substrate. */
public final class ProtosActorGroupRuntime {
    public enum LifecycleState {
        LIVE,
        TERMINATED
    }

    interface RoutingOperation {
        void routeToForRuntime(ProtosActor actor);
        void memberBecameIneligibleForRuntime(ProtosActor actor);
        void groupTerminatedBeforeAcceptanceForRuntime();
    }

    private final UUID groupIdentity = UUID.randomUUID();
    private final Set<ProtosActor> members = new LinkedHashSet<>();
    private final ArrayDeque<RoutingOperation> pending = new ArrayDeque<>();
    private final Set<RoutingOperation> active = new LinkedHashSet<>();
    private LifecycleState lifecycle = LifecycleState.LIVE;
    private int nextSelectionIndex;

    public synchronized UUID groupIdentityForRuntime() {
        return groupIdentity;
    }

    public synchronized LifecycleState lifecycleState() {
        return lifecycle;
    }

    /** Adds membership without changing Group identity and wakes pending routing when useful. */
    public boolean addMemberForRuntime(ProtosActor actor) {
        Objects.requireNonNull(actor, "actor");
        boolean added;
        synchronized (this) {
            if (lifecycle != LifecycleState.LIVE) {
                throw new IllegalStateException("terminated Group cannot acquire members");
            }
            added = members.add(actor);
        }
        if (added) {
            actor.registerRoutingGroupForRuntime(this);
            drainPendingForRuntime();
        }
        return added;
    }

    /** Removes membership only; already accepted concrete-Actor work is not revoked. */
    public boolean removeMemberForRuntime(ProtosActor actor) {
        Objects.requireNonNull(actor, "actor");
        List<RoutingOperation> operations;
        boolean removed;
        synchronized (this) {
            removed = members.remove(actor);
            if (!removed) {
                return false;
            }
            if (nextSelectionIndex >= members.size()) {
                nextSelectionIndex = 0;
            }
            operations = List.copyOf(active);
        }
        actor.unregisterRoutingGroupForRuntime(this);
        for (RoutingOperation operation : operations) {
            operation.memberBecameIneligibleForRuntime(actor);
        }
        drainPendingForRuntime();
        return true;
    }

    /** Selects one current READY member; the round-robin choice is not language-visible policy. */
    public synchronized Optional<ProtosActor> selectEligibleMemberForRuntime() {
        return Optional.ofNullable(selectEligibleMemberLocked());
    }

    private ProtosActor selectEligibleMemberLocked() {
        if (lifecycle != LifecycleState.LIVE || members.isEmpty()) {
            return null;
        }
        List<ProtosActor> eligible = new ArrayList<>();
        for (ProtosActor actor : members) {
            if (actor.lifecycleState() == ProtosActor.LifecycleState.READY) {
                eligible.add(actor);
            }
        }
        if (eligible.isEmpty()) {
            return null;
        }
        if (nextSelectionIndex >= eligible.size()) {
            nextSelectionIndex = 0;
        }
        ProtosActor selected = eligible.get(nextSelectionIndex);
        nextSelectionIndex = (nextSelectionIndex + 1) % eligible.size();
        return selected;
    }

    /** Runtime acquisition of a new GroupRef capability to this exact Group identity. */
    public synchronized ProtosGroupRefValue acquireReferenceForRuntime(
            ProtosObjectValue groupRefPrototype, UUID restrictionIdentity) {
        Objects.requireNonNull(groupRefPrototype, "groupRefPrototype");
        Objects.requireNonNull(restrictionIdentity, "restrictionIdentity");
        if (lifecycle != LifecycleState.LIVE) {
            throw new IllegalStateException("terminated Group cannot issue a new GroupRef");
        }
        return ProtosGroupRefValue.acquireForRuntime(groupRefPrototype, this, restrictionIdentity);
    }

    void submitRoutingForRuntime(RoutingOperation operation) {
        Objects.requireNonNull(operation, "operation");
        boolean terminated;
        synchronized (this) {
            terminated = lifecycle != LifecycleState.LIVE;
            if (!terminated) {
                active.add(operation);
                pending.addLast(operation);
            }
        }
        if (terminated) {
            operation.groupTerminatedBeforeAcceptanceForRuntime();
            return;
        }
        drainPendingForRuntime();
    }

    /** Requeues after a known concrete-Actor pre-acceptance failure; never retries accepted work. */
    boolean requeueAfterPreacceptFailureForRuntime(RoutingOperation operation) {
        synchronized (this) {
            if (lifecycle != LifecycleState.LIVE || !active.contains(operation)) {
                return false;
            }
            if (!pending.contains(operation)) {
                pending.addLast(operation);
            }
            return true;
        }
    }

    /** Cancels Group-owned routing even if a drain already selected but has not submitted a child. */
    boolean cancelRoutingBeforeAcceptanceForRuntime(RoutingOperation operation) {
        synchronized (this) {
            if (!active.contains(operation)) {
                return false;
            }
            pending.remove(operation);
            return true;
        }
    }

    void operationFinishedForRuntime(RoutingOperation operation) {
        synchronized (this) {
            pending.remove(operation);
            active.remove(operation);
        }
    }

    /** READY/termination changes can make a pending route possible or invalidate one target. */
    void memberLifecycleChangedForRuntime(ProtosActor actor) {
        Objects.requireNonNull(actor, "actor");
        synchronized (this) {
            if (!members.contains(actor) || lifecycle != LifecycleState.LIVE) {
                return;
            }
        }
        drainPendingForRuntime();
    }

    private void drainPendingForRuntime() {
        while (true) {
            RoutingOperation operation;
            ProtosActor selected;
            synchronized (this) {
                if (lifecycle != LifecycleState.LIVE) {
                    return;
                }
                operation = pending.peekFirst();
                if (operation == null) {
                    return;
                }
                selected = selectEligibleMemberLocked();
                if (selected == null) {
                    return;
                }
                pending.removeFirst();
            }
            // Group monitor is deliberately not held while entering concrete Actor admission.
            operation.routeToForRuntime(selected);
        }
    }

    /** Ends this Group identity without stopping members or revoking already accepted Actor work. */
    public boolean markTerminatedForRuntime() {
        List<RoutingOperation> operations;
        List<ProtosActor> currentMembers;
        synchronized (this) {
            if (lifecycle == LifecycleState.TERMINATED) {
                return false;
            }
            lifecycle = LifecycleState.TERMINATED;
            pending.clear();
            operations = List.copyOf(active);
            currentMembers = List.copyOf(members);
        }
        for (ProtosActor actor : currentMembers) {
            actor.unregisterRoutingGroupForRuntime(this);
        }
        for (RoutingOperation operation : operations) {
            operation.groupTerminatedBeforeAcceptanceForRuntime();
        }
        return true;
    }

    synchronized int memberCountForTesting() {
        return members.size();
    }

    synchronized int pendingOperationCountForTesting() {
        return pending.size();
    }
}
