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

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Internal local ActorGroup identity, membership, and routing-eligibility substrate.
 *
 * <p>The Group is stable independently of its current members. Membership is runtime/control-plane
 * state and is never exposed by GroupRef transfer. A live Group may have zero current or eligible
 * members. Selection here is deliberately an internal deterministic round-robin implementation
 * policy; Core promises neither this policy nor Group-wide FIFO ordering.
 */
public final class ProtosActorGroupRuntime {
    public enum LifecycleState {
        LIVE,
        TERMINATED
    }

    private final UUID groupIdentity = UUID.randomUUID();
    private final Set<ProtosActor> members = new LinkedHashSet<>();
    private LifecycleState lifecycle = LifecycleState.LIVE;
    private int nextSelectionIndex;

    public synchronized UUID groupIdentityForRuntime() {
        return groupIdentity;
    }

    public synchronized LifecycleState lifecycleState() {
        return lifecycle;
    }

    /** Adds one concrete Actor to this Group's membership without changing Group identity. */
    public synchronized boolean addMemberForRuntime(ProtosActor actor) {
        Objects.requireNonNull(actor, "actor");
        if (lifecycle != LifecycleState.LIVE) {
            throw new IllegalStateException("terminated Group cannot acquire members");
        }
        return members.add(actor);
    }

    /** Removes membership only; it does not stop or otherwise mutate the Actor incarnation. */
    public synchronized boolean removeMemberForRuntime(ProtosActor actor) {
        Objects.requireNonNull(actor, "actor");
        boolean removed = members.remove(actor);
        if (removed && nextSelectionIndex > members.size()) {
            nextSelectionIndex = 0;
        }
        return removed;
    }

    /**
     * Selects one currently routing-eligible concrete Actor.
     *
     * <p>Membership and routing eligibility are distinct: INITIALIZING, TERMINATING, and
     * TERMINATED members are not eligible. A live Group with no eligible member returns empty so a
     * later communication layer can apply bounded backpressure rather than fabricate failure.
     */
    public synchronized Optional<ProtosActor> selectEligibleMemberForRuntime() {
        if (lifecycle != LifecycleState.LIVE || members.isEmpty()) {
            return Optional.empty();
        }
        List<ProtosActor> eligible = new ArrayList<>();
        for (ProtosActor actor : members) {
            if (actor.lifecycleState() == ProtosActor.LifecycleState.READY) {
                eligible.add(actor);
            }
        }
        if (eligible.isEmpty()) {
            return Optional.empty();
        }
        if (nextSelectionIndex >= eligible.size()) {
            nextSelectionIndex = 0;
        }
        ProtosActor selected = eligible.get(nextSelectionIndex);
        nextSelectionIndex = (nextSelectionIndex + 1) % eligible.size();
        return Optional.of(selected);
    }

    /** Runtime acquisition of a new GroupRef capability to this exact Group identity. */
    public synchronized ProtosGroupRefValue acquireReferenceForRuntime(
            ProtosObjectValue groupRefPrototype, UUID restrictionIdentity) {
        Objects.requireNonNull(groupRefPrototype, "groupRefPrototype");
        Objects.requireNonNull(restrictionIdentity, "restrictionIdentity");
        if (lifecycle != LifecycleState.LIVE) {
            throw new IllegalStateException("terminated Group cannot issue a new GroupRef");
        }
        return ProtosGroupRefValue.acquireForRuntime(
                groupRefPrototype, this, restrictionIdentity);
    }

    /**
     * Ends this Group identity without terminating member Actors.
     *
     * <p>Existing GroupRefs remain permanently bound to this terminated identity. Member Actor
     * lifecycle is independent; accepted concrete-Actor work remains owned by that Actor.
     */
    public synchronized boolean markTerminatedForRuntime() {
        if (lifecycle == LifecycleState.TERMINATED) {
            return false;
        }
        lifecycle = LifecycleState.TERMINATED;
        return true;
    }

    synchronized int memberCountForTesting() {
        return members.size();
    }
}
