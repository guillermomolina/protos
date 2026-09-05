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

import com.guillermomolina.protos.execution.ProtosInvocation;
import java.util.List;
import java.util.Objects;

/** One Group-routed one-way operation preserving one logical message snapshot. */
public final class ProtosGroupSendOperationValue extends ProtosObjectValue
        implements ProtosSendOperationControl, ProtosActorGroupRuntime.RoutingOperation {
    enum State {
        ROUTING,
        ACCEPTED,
        COMPLETED,
        CANCELLED_BEFORE_ACCEPTANCE,
        FAILED_BEFORE_ACCEPTANCE,
        FAILED_AFTER_ACCEPTANCE,
        ACCEPTANCE_UNCERTAIN
    }

    private final ProtosObjectValue sendOperationPrototype;
    private final ProtosGroupRefValue destination;
    private final ProtosActorGroupRuntime group;
    private final ProtosActorRefValue sender;
    private final String selector;
    private final List<Object> snapshot;
    private ProtosActorRefValue selectedMember;
    private ProtosActorDeliveryAttempt attempt;
    private ProtosActorTransportRoute.Delivery transportAttempt;
    private State state = State.ROUTING;

    private ProtosGroupSendOperationValue(
            ProtosObjectValue sendOperationPrototype,
            ProtosGroupRefValue destination,
            ProtosActorGroupRuntime group,
            ProtosActorRefValue sender,
            String selector,
            List<Object> snapshot) {
        super(Objects.requireNonNull(sendOperationPrototype, "sendOperationPrototype"));
        this.sendOperationPrototype = sendOperationPrototype;
        this.destination = Objects.requireNonNull(destination, "destination");
        this.group = Objects.requireNonNull(group, "group");
        this.sender = Objects.requireNonNull(sender, "sender");
        this.selector = Objects.requireNonNull(selector, "selector");
        this.snapshot = List.copyOf(Objects.requireNonNull(snapshot, "snapshot"));
    }

    static ProtosGroupSendOperationValue begin(
            ProtosObjectValue sendOperationPrototype,
            ProtosGroupRefValue destination,
            ProtosActorGroupRuntime group,
            ProtosActorRefValue sender,
            String selector,
            List<Object> snapshot) {
        ProtosGroupSendOperationValue operation =
                new ProtosGroupSendOperationValue(
                        sendOperationPrototype, destination, group, sender, selector, snapshot);
        group.submitRoutingForRuntime(operation);
        return operation;
    }

    @Override
    public boolean cancelBeforeAcceptance() {
        ProtosActorDeliveryAttempt local;
        ProtosActorTransportRoute.Delivery remote;
        synchronized (this) {
            if (state != State.ROUTING) {
                return false;
            }
            local = attempt;
            remote = transportAttempt;
            if (local == null && remote == null) {
                if (!group.cancelRoutingBeforeAcceptanceForRuntime(this)) {
                    return false;
                }
                selectedMember = null;
                state = State.CANCELLED_BEFORE_ACCEPTANCE;
            }
        }
        if (local == null && remote == null) {
            group.operationFinishedForRuntime(this);
            return true;
        }
        if (local != null) {
            if (!local.cancelBeforeAcceptance()) {
                return false;
            }
            synchronized (this) {
                if (state != State.ROUTING || attempt != local) {
                    return false;
                }
                attempt = null;
                selectedMember = null;
                state = State.CANCELLED_BEFORE_ACCEPTANCE;
            }
            group.operationFinishedForRuntime(this);
            return true;
        }
        if (!remote.cancelBeforeAcceptance()) {
            return false;
        }
        synchronized (this) {
            if (state != State.ROUTING || transportAttempt != remote) {
                return false;
            }
            transportAttempt = null;
            selectedMember = null;
            state = State.CANCELLED_BEFORE_ACCEPTANCE;
        }
        group.operationFinishedForRuntime(this);
        return true;
    }

    @Override
    public ProtosGroupSendOperationValue retryAfterFailure() {
        synchronized (this) {
            if (state != State.FAILED_BEFORE_ACCEPTANCE
                    && state != State.FAILED_AFTER_ACCEPTANCE
                    && state != State.ACCEPTANCE_UNCERTAIN) {
                return null;
            }
        }
        return begin(sendOperationPrototype, destination, group, sender, selector, snapshot);
    }

    @Override
    public void routeToForRuntime(ProtosActorRefValue member) {
        Objects.requireNonNull(member, "member");
        synchronized (this) {
            if (state != State.ROUTING || attempt != null || transportAttempt != null) {
                return;
            }
            selectedMember = member;
            ProtosActorTransportRoute route = member.communicationRouteForRuntime().orElse(null);
            if (route != null) {
                ProtosActorTransportRoute.Delivery created =
                        Objects.requireNonNull(
                                route.beginSend(sender, selector, snapshot),
                                "transport route returned no Group send delivery");
                transportAttempt = created;
                created.observeForRuntime(
                        transportState -> remoteStateChangedForRuntime(member, created, transportState));
                return;
            }

            ProtosActor actor = member.localActorForRuntime();
            ProtosActorDeliveryAdmission admission = actor.deliveryAdmissionForRuntime();
            final ProtosActorDeliveryAttempt[] holder = new ProtosActorDeliveryAttempt[1];
            ProtosActorDeliveryAttempt created =
                    new ProtosActorDeliveryAttempt(
                            admission,
                            sender,
                            task -> executeAcceptedTurn(actor, task, holder[0]),
                            this::deliveryFailedForRuntime);
            holder[0] = created;
            attempt = created;
            admission.submit(created);
        }
    }

    @Override
    public void memberBecameIneligibleForRuntime(ProtosActorRefValue member) {
        ProtosActorDeliveryAttempt local;
        ProtosActorTransportRoute.Delivery remote;
        synchronized (this) {
            if (state != State.ROUTING
                    || !sameMember(selectedMember, member)) {
                return;
            }
            local = attempt;
            remote = transportAttempt;
        }
        if (local != null) {
            if (!local.cancelBeforeAcceptance()) {
                return;
            }
            rerouteKnownPreaccept(local, null);
            return;
        }
        if (remote != null) {
            if (remote.cancelBeforeAcceptance()) {
                rerouteKnownPreaccept(null, remote);
                return;
            }
            ProtosActorTransportRoute.DeliveryState transportState = remote.stateForRuntime();
            if (transportState == ProtosActorTransportRoute.DeliveryState.FAILED_BEFORE_ACCEPTANCE
                    || transportState == ProtosActorTransportRoute.DeliveryState.CANCELLED_BEFORE_ACCEPTANCE) {
                rerouteKnownPreaccept(null, remote);
            } else if (transportState == ProtosActorTransportRoute.DeliveryState.PENDING
                    || transportState == ProtosActorTransportRoute.DeliveryState.ACCEPTANCE_UNCERTAIN) {
                finishRemote(State.ACCEPTANCE_UNCERTAIN, remote);
            }
        }
    }

    @Override
    public void groupTerminatedBeforeAcceptanceForRuntime() {
        ProtosActorDeliveryAttempt local;
        ProtosActorTransportRoute.Delivery remote;
        synchronized (this) {
            if (state != State.ROUTING) {
                return;
            }
            local = attempt;
            remote = transportAttempt;
            if (local == null && remote == null) {
                selectedMember = null;
                state = State.FAILED_BEFORE_ACCEPTANCE;
            }
        }
        if (local == null && remote == null) {
            group.operationFinishedForRuntime(this);
            return;
        }
        if (local != null) {
            if (!local.cancelBeforeAcceptance()) {
                return;
            }
            finishKnownPreaccept(local, null);
            return;
        }
        if (remote.cancelBeforeAcceptance()) {
            finishKnownPreaccept(null, remote);
            return;
        }
        ProtosActorTransportRoute.DeliveryState transportState = remote.stateForRuntime();
        switch (transportState) {
            case FAILED_BEFORE_ACCEPTANCE, CANCELLED_BEFORE_ACCEPTANCE ->
                    finishKnownPreaccept(null, remote);
            case PENDING, ACCEPTANCE_UNCERTAIN ->
                    finishRemote(State.ACCEPTANCE_UNCERTAIN, remote);
            case ACCEPTED, COMPLETED, FAILED_AFTER_ACCEPTANCE -> {
                // Group termination cannot revoke ownership that the concrete destination may have.
            }
        }
    }

    private void remoteStateChangedForRuntime(
            ProtosActorRefValue member,
            ProtosActorTransportRoute.Delivery delivery,
            ProtosActorTransportRoute.DeliveryState transportState) {
        Objects.requireNonNull(transportState, "transportState");
        switch (transportState) {
            case PENDING, CANCELLED_BEFORE_ACCEPTANCE -> {
                // Pending is non-terminal. Cancellation transitions are classified by the caller
                // that initiated cancellation/rerouting/group termination.
            }
            case FAILED_BEFORE_ACCEPTANCE -> rerouteKnownPreaccept(null, delivery);
            case ACCEPTED -> finishRemote(State.ACCEPTED, delivery);
            case COMPLETED -> finishRemote(State.COMPLETED, delivery);
            case FAILED_AFTER_ACCEPTANCE -> finishRemote(State.FAILED_AFTER_ACCEPTANCE, delivery);
            case ACCEPTANCE_UNCERTAIN -> finishRemote(State.ACCEPTANCE_UNCERTAIN, delivery);
        }
    }

    private void rerouteKnownPreaccept(
            ProtosActorDeliveryAttempt local,
            ProtosActorTransportRoute.Delivery remote) {
        boolean shouldRequeue;
        synchronized (this) {
            if (state != State.ROUTING) {
                return;
            }
            if (local != null && attempt != local) {
                return;
            }
            if (remote != null && transportAttempt != remote) {
                return;
            }
            attempt = null;
            transportAttempt = null;
            selectedMember = null;
            shouldRequeue = true;
        }
        if (shouldRequeue && !group.requeueAfterPreacceptFailureForRuntime(this)) {
            synchronized (this) {
                if (state == State.ROUTING && attempt == null && transportAttempt == null) {
                    state = State.FAILED_BEFORE_ACCEPTANCE;
                }
            }
            group.operationFinishedForRuntime(this);
        }
    }

    private void finishKnownPreaccept(
            ProtosActorDeliveryAttempt local,
            ProtosActorTransportRoute.Delivery remote) {
        synchronized (this) {
            if (state != State.ROUTING) {
                return;
            }
            if (local != null && attempt != local) {
                return;
            }
            if (remote != null && transportAttempt != remote) {
                return;
            }
            attempt = null;
            transportAttempt = null;
            selectedMember = null;
            state = State.FAILED_BEFORE_ACCEPTANCE;
        }
        group.operationFinishedForRuntime(this);
    }

    private void finishRemote(State terminalState, ProtosActorTransportRoute.Delivery remote) {
        synchronized (this) {
            if (transportAttempt != remote) {
                return;
            }
            if (state != State.ROUTING && state != State.ACCEPTED) {
                return;
            }
            state = terminalState;
            if (terminalState != State.ACCEPTED) {
                transportAttempt = remote;
            }
        }
        // ACCEPTED is already beyond Group routing ownership; later transport transitions remain
        // observable by this operation's transport observer without returning ownership to Group.
        group.operationFinishedForRuntime(this);
    }

    private void deliveryFailedForRuntime(ProtosActorDeliveryAttempt.State deliveryState) {
        if (deliveryState == ProtosActorDeliveryAttempt.State.FAILED_BEFORE_ACCEPTANCE) {
            ProtosActorDeliveryAttempt local;
            synchronized (this) {
                local = attempt;
            }
            rerouteKnownPreaccept(local, null);
            return;
        }
        if (deliveryState == ProtosActorDeliveryAttempt.State.FAILED_AFTER_ACCEPTANCE) {
            synchronized (this) {
                if (state == State.COMPLETED) {
                    return;
                }
                state = State.FAILED_AFTER_ACCEPTANCE;
            }
            group.operationFinishedForRuntime(this);
            return;
        }
        throw new IllegalArgumentException("unexpected Group send failure state " + deliveryState);
    }

    private void executeAcceptedTurn(
            ProtosActor target, ProtosTask task, ProtosActorDeliveryAttempt delivery) {
        if (delivery == null) {
            throw new IllegalStateException("Group send lost its concrete delivery attempt");
        }
        if (!delivery.beginDispatchForRuntime()) {
            task.complete(ProtosNullValue.INSTANCE);
            return;
        }
        try {
            task.executeAction(
                    () -> {
                        ProtosActivation activation = target.newMessageActivationForRuntime();
                        activation.attachTask(task);
                        ProtosObjectValue behavior =
                                target.currentBehavior()
                                        .orElseThrow(
                                                () -> new IllegalStateException(
                                                        "READY Group member lost current behavior"));
                        ProtosInvocation.invokeMessage(behavior, selector, snapshot, activation);
                        return ProtosNullValue.INSTANCE;
                    });
        } catch (RuntimeException failure) {
            delivery.markFailedAfterAcceptanceForRuntime();
            throw failure;
        }

        switch (task.state()) {
            case COMPLETED -> {
                delivery.markCompletedForRuntime();
                synchronized (this) {
                    state = State.COMPLETED;
                }
                group.operationFinishedForRuntime(this);
            }
            case FAILED -> {
                delivery.markFailedAfterAcceptanceForRuntime();
                Object failure = task.failure().orElseThrow();
                target.failForRuntime(failure);
            }
            case CANCELLED -> {
                delivery.markFailedAfterAcceptanceForRuntime();
                target.requestTerminationForRuntime();
            }
            case SUSPENDED -> { }
            case RUNNABLE, RUNNING ->
                    throw new IllegalStateException(
                            "Group send turn returned in non-yielded state " + task.state());
        }
    }

    private static boolean sameMember(ProtosActorRefValue left, ProtosActorRefValue right) {
        return left != null && right != null && left.denotesSameIncarnation(right);
    }

    synchronized State stateForTesting() {
        return state;
    }

    synchronized ProtosActorDeliveryAttempt.State deliveryStateForTesting() {
        return attempt == null ? null : attempt.state();
    }

    synchronized ProtosActorTransportRoute.DeliveryState transportStateForTesting() {
        return transportAttempt == null ? null : transportAttempt.stateForRuntime();
    }

    synchronized ProtosActor selectedActorForTesting() {
        return selectedMember == null || selectedMember.communicationRouteForRuntime().isPresent()
                ? null
                : selectedMember.localActorForRuntime();
    }

    synchronized ProtosActorRefValue selectedMemberForTesting() {
        return selectedMember;
    }

    List<Object> snapshotForTesting() {
        return snapshot;
    }
}
