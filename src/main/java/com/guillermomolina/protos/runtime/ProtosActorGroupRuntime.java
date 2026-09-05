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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Internal ActorGroup identity, membership, and pre-acceptance routing substrate. */
public final class ProtosActorGroupRuntime {
    public enum LifecycleState {
        LIVE,
        TERMINATED
    }

    interface RoutingOperation {
        void routeToForRuntime(ProtosActorRefValue member);
        void memberBecameIneligibleForRuntime(ProtosActorRefValue member);
        void groupTerminatedBeforeAcceptanceForRuntime();
    }

    private final UUID groupIdentity = UUID.randomUUID();
    private final Map<Long, Member> members = new LinkedHashMap<>();
    private final ArrayDeque<RoutingOperation> pending = new ArrayDeque<>();
    private final Set<RoutingOperation> active = new LinkedHashSet<>();
    private LifecycleState lifecycle = LifecycleState.LIVE;
    private int nextSelectionIndex;
    private int nextLocalSelectionIndex;
    private boolean draining;

    public synchronized UUID groupIdentityForRuntime() {
        return groupIdentity;
    }

    public synchronized LifecycleState lifecycleState() {
        return lifecycle;
    }

    /** Adds local membership without changing Group identity and wakes pending routing when useful. */
    public boolean addMemberForRuntime(ProtosActor actor) {
        Objects.requireNonNull(actor, "actor");
        Member member = Member.local(actor);
        boolean added;
        synchronized (this) {
            if (lifecycle != LifecycleState.LIVE) {
                throw new IllegalStateException("terminated Group cannot acquire members");
            }
            added = members.putIfAbsent(member.identity(), member) == null;
        }
        if (added) {
            actor.registerRoutingGroupForRuntime(this);
            drainPendingForRuntime();
        }
        return added;
    }

    /**
     * Establishes one D039 initial member from the exact supplied ActorRef capability.
     *
     * <p>The reference itself is retained so an already-materialized transport route remains usable,
     * while the currently known Actor lifecycle supplies readiness/termination eligibility. This is
     * construction-only runtime machinery, not a public post-creation membership operation.
     */
    boolean addInitialMemberReferenceForRuntime(ProtosActorRefValue reference) {
        Objects.requireNonNull(reference, "reference");
        Member member = Member.initial(reference);
        boolean added;
        synchronized (this) {
            if (lifecycle != LifecycleState.LIVE) {
                throw new IllegalStateException("terminated Group cannot acquire members");
            }
            added = members.putIfAbsent(member.identity(), member) == null;
        }
        if (added) {
            member.localActor.registerRoutingGroupForRuntime(this);
            drainPendingForRuntime();
        }
        return added;
    }

    /**
     * Adds one remotely routed ActorRef already known eligible in the caller's Group membership view.
     *
     * <p>This is not a public membership/discovery API. The caller owns remote READY/membership
     * knowledge; this method neither probes nor infers liveness. The reference must already denote
     * one exact Actor incarnation and carry the I011-19 transport route for that incarnation. Group
     * identity, GroupRef identity, membership, reachability, and transport remain separate state.
     */
    public boolean addRemoteReadyMemberForRuntime(ProtosActorRefValue reference) {
        Objects.requireNonNull(reference, "reference");
        if (reference.communicationRouteForRuntime().isEmpty()) {
            throw new IllegalArgumentException("remote Group member requires an Actor transport route");
        }
        Member member = Member.remote(reference);
        boolean added;
        synchronized (this) {
            if (lifecycle != LifecycleState.LIVE) {
                throw new IllegalStateException("terminated Group cannot acquire members");
            }
            added = members.putIfAbsent(member.identity(), member) == null;
        }
        if (added) {
            drainPendingForRuntime();
        }
        return added;
    }

    /** Removes local membership only; already accepted concrete-Actor work is not revoked. */
    public boolean removeMemberForRuntime(ProtosActor actor) {
        Objects.requireNonNull(actor, "actor");
        Member removed;
        List<RoutingOperation> operations;
        synchronized (this) {
            Member current = members.get(actor.incarnationIdentityForRuntime());
            if (current == null || current.localActor != actor) {
                return false;
            }
            removed = members.remove(current.identity());
            normalizeSelectionIndexLocked();
            operations = List.copyOf(active);
        }
        actor.unregisterRoutingGroupForRuntime(this);
        for (RoutingOperation operation : operations) {
            operation.memberBecameIneligibleForRuntime(removed.reference);
        }
        drainPendingForRuntime();
        return true;
    }

    /** Removes one remote routing member without inferring Actor termination from route loss. */
    public boolean removeRemoteMemberForRuntime(ProtosActorRefValue reference) {
        Objects.requireNonNull(reference, "reference");
        Member removed;
        List<RoutingOperation> operations;
        synchronized (this) {
            Member current = members.get(reference.incarnationIdentityForRuntime());
            if (current == null || current.localActor != null) {
                return false;
            }
            removed = members.remove(current.identity());
            normalizeSelectionIndexLocked();
            operations = List.copyOf(active);
        }
        for (RoutingOperation operation : operations) {
            operation.memberBecameIneligibleForRuntime(removed.reference);
        }
        drainPendingForRuntime();
        return true;
    }

    /** Selects one current READY local member; retained for deterministic local routing tests. */
    public synchronized Optional<ProtosActor> selectEligibleMemberForRuntime() {
        if (lifecycle != LifecycleState.LIVE) {
            return Optional.empty();
        }
        List<Member> eligible = new ArrayList<>();
        for (Member member : members.values()) {
            if (member.localActor != null && member.routingEligible()) {
                eligible.add(member);
            }
        }
        if (eligible.isEmpty()) {
            return Optional.empty();
        }
        if (nextLocalSelectionIndex >= eligible.size()) {
            nextLocalSelectionIndex = 0;
        }
        ProtosActor selected = eligible.get(nextLocalSelectionIndex).localActor;
        nextLocalSelectionIndex = (nextLocalSelectionIndex + 1) % eligible.size();
        return Optional.of(selected);
    }

    private Member selectEligibleMemberLocked() {
        if (lifecycle != LifecycleState.LIVE || members.isEmpty()) {
            return null;
        }
        List<Member> eligible = new ArrayList<>();
        for (Member member : members.values()) {
            if (member.routingEligible()) {
                eligible.add(member);
            }
        }
        if (eligible.isEmpty()) {
            return null;
        }
        if (nextSelectionIndex >= eligible.size()) {
            nextSelectionIndex = 0;
        }
        Member selected = eligible.get(nextSelectionIndex);
        nextSelectionIndex = (nextSelectionIndex + 1) % eligible.size();
        return selected;
    }

    private void normalizeSelectionIndexLocked() {
        if (nextSelectionIndex >= members.size()) {
            nextSelectionIndex = 0;
        }
        if (nextLocalSelectionIndex >= members.size()) {
            nextLocalSelectionIndex = 0;
        }
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

    /** Requeues after known pre-acceptance failure; accepted or uncertain work never comes here. */
    boolean requeueAfterPreacceptFailureForRuntime(RoutingOperation operation) {
        boolean queued;
        synchronized (this) {
            if (lifecycle != LifecycleState.LIVE || !active.contains(operation)) {
                return false;
            }
            queued = !pending.contains(operation);
            if (queued) {
                pending.addLast(operation);
            }
        }
        if (queued) {
            drainPendingForRuntime();
        }
        return true;
    }

    /** Cancels Group-owned routing even if a drain selected a member but acceptance is still known absent. */
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

    /** READY/termination changes can make a pending local route possible or invalidate one target. */
    void memberLifecycleChangedForRuntime(ProtosActor actor) {
        Objects.requireNonNull(actor, "actor");
        synchronized (this) {
            Member member = members.get(actor.incarnationIdentityForRuntime());
            if (member == null || member.localActor != actor || lifecycle != LifecycleState.LIVE) {
                return;
            }
        }
        drainPendingForRuntime();
    }

    /**
     * Routes a bounded amount of pending work per drain activation.
     *
     * <p>The non-reentrant/bounded drain prevents an immediately failing remote route from spinning
     * forever on one member while still allowing one pass across the currently available routing
     * set. Further membership/lifecycle/transport events can activate another pass.
     */
    private void drainPendingForRuntime() {
        int budget;
        synchronized (this) {
            if (draining || lifecycle != LifecycleState.LIVE) {
                return;
            }
            if (pending.isEmpty()) {
                return;
            }
            draining = true;
            budget = Math.max(1, pending.size() * Math.max(1, members.size()));
        }
        try {
            while (budget-- > 0) {
                RoutingOperation operation;
                Member selected;
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
                // Group monitor is deliberately not held while entering Actor admission/transport.
                operation.routeToForRuntime(selected.reference);
            }
        } finally {
            synchronized (this) {
                draining = false;
            }
        }
    }

    /** Ends this Group identity without stopping members or revoking already accepted Actor work. */
    public boolean markTerminatedForRuntime() {
        List<RoutingOperation> operations;
        List<Member> currentMembers;
        synchronized (this) {
            if (lifecycle == LifecycleState.TERMINATED) {
                return false;
            }
            lifecycle = LifecycleState.TERMINATED;
            pending.clear();
            operations = List.copyOf(active);
            currentMembers = List.copyOf(members.values());
        }
        for (Member member : currentMembers) {
            if (member.localActor != null) {
                member.localActor.unregisterRoutingGroupForRuntime(this);
            }
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

    private static final class Member {
        private final ProtosActorRefValue reference;
        private final ProtosActor localActor;

        private Member(ProtosActorRefValue reference, ProtosActor localActor) {
            this.reference = Objects.requireNonNull(reference, "reference");
            this.localActor = localActor;
        }

        private static Member local(ProtosActor actor) {
            return new Member(actor.reference(), actor);
        }

        private static Member initial(ProtosActorRefValue reference) {
            return new Member(reference, reference.localActorForRuntime());
        }

        private static Member remote(ProtosActorRefValue reference) {
            return new Member(reference, null);
        }

        private long identity() {
            return reference.incarnationIdentityForRuntime();
        }

        private boolean routingEligible() {
            return localActor == null || localActor.lifecycleState() == ProtosActor.LifecycleState.READY;
        }
    }
}
