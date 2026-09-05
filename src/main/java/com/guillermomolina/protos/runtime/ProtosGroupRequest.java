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

/** Runtime-owned ActorGroup request/reply operation across local or transport-routed members. */
public final class ProtosGroupRequest implements ProtosActorGroupRuntime.RoutingOperation {
    private final ProtosGroupRefValue destination;
    private final ProtosActorGroupRuntime group;
    private final ProtosActorRefValue sender;
    private final String selector;
    private final List<Object> snapshot;
    private final ProtosActivation callerActivation;
    private final ProtosFutureValue future;
    private ProtosActorRefValue selectedMember;
    private ProtosActorDeliveryAttempt attempt;
    private ProtosActorTransportRoute.Delivery transportAttempt;
    private long routingGeneration;
    private boolean routing = true;
    private boolean terminal;

    private ProtosGroupRequest(
            ProtosGroupRefValue destination,
            ProtosActorGroupRuntime group,
            ProtosActorRefValue sender,
            String selector,
            List<Object> snapshot,
            ProtosActivation callerActivation,
            ProtosFutureValue future) {
        this.destination = Objects.requireNonNull(destination, "destination");
        this.group = Objects.requireNonNull(group, "group");
        this.sender = Objects.requireNonNull(sender, "sender");
        this.selector = Objects.requireNonNull(selector, "selector");
        this.snapshot = List.copyOf(Objects.requireNonNull(snapshot, "snapshot"));
        this.callerActivation = Objects.requireNonNull(callerActivation, "callerActivation");
        this.future = Objects.requireNonNull(future, "future");
    }

    static ProtosFutureValue begin(
            ProtosGroupRefValue destination,
            ProtosActorGroupRuntime group,
            ProtosActorRefValue sender,
            String selector,
            List<Object> snapshot,
            ProtosActivation callerActivation) {
        ProtosObjectValue futurePrototype =
                callerActivation.prelude().orElseThrow(
                        () -> new IllegalStateException(
                                "GroupRef request requires an owning Core prelude"))
                        .futurePrototype();
        ProtosFutureValue future =
                new ProtosFutureValue(futurePrototype, callerActivation.executionDomain());
        ProtosGroupRequest request =
                new ProtosGroupRequest(
                        destination, group, sender, selector, snapshot, callerActivation, future);
        future.attachCancellationProducer(request::cancellationRequested);
        group.submitRoutingForRuntime(request);
        callerActivation.executionDomain().registerActorNonTaskFuture(future);
        return future;
    }

    @Override
    public void routeToForRuntime(ProtosActorRefValue member) {
        Objects.requireNonNull(member, "member");
        ProtosActorTransportRoute route = member.communicationRouteForRuntime().orElse(null);
        if (route != null) {
            beginRemoteAttempt(member, route);
            return;
        }
        beginLocalAttempt(member);
    }

    private void beginLocalAttempt(ProtosActorRefValue member) {
        synchronized (this) {
            if (!routing || terminal || attempt != null || transportAttempt != null) {
                return;
            }
            selectedMember = member;
            ++routingGeneration;
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

    private void beginRemoteAttempt(ProtosActorRefValue member, ProtosActorTransportRoute route) {
        final long generation;
        synchronized (this) {
            if (!routing || terminal || attempt != null || transportAttempt != null) {
                return;
            }
            selectedMember = member;
            generation = ++routingGeneration;
        }

        ProtosActorTransportRoute.Delivery created =
                Objects.requireNonNull(
                        route.beginRequest(
                                sender,
                                selector,
                                snapshot,
                                new ProtosActorTransportRoute.RequestObserver() {
                                    @Override
                                    public void reply(Object result) {
                                        remoteReplyForRuntime(generation, result);
                                    }

                                    @Override
                                    public void failedBeforeAcceptance() {
                                        remoteFailedBeforeAcceptanceForRuntime(generation);
                                    }

                                    @Override
                                    public void outcomeUncertain() {
                                        remoteOutcomeUncertainForRuntime(generation);
                                    }
                                }),
                        "transport route returned no Group request delivery");

        boolean observe;
        synchronized (this) {
            observe = !terminal && routingGeneration == generation && selectedMember == member;
            if (observe) {
                transportAttempt = created;
            }
        }
        if (observe) {
            created.observeForRuntime(
                    state -> remoteStateChangedForRuntime(generation, created, state));
        }
    }

    @Override
    public void memberBecameIneligibleForRuntime(ProtosActorRefValue member) {
        ProtosActorDeliveryAttempt local;
        ProtosActorTransportRoute.Delivery remote;
        long generation;
        synchronized (this) {
            if (!routing || terminal || !sameMember(selectedMember, member)) {
                return;
            }
            local = attempt;
            remote = transportAttempt;
            generation = routingGeneration;
        }
        if (local != null) {
            if (local.cancelBeforeAcceptance()) {
                rerouteKnownPreaccept(local, null, generation);
            }
            return;
        }
        if (remote != null) {
            if (remote.cancelBeforeAcceptance()) {
                rerouteKnownPreaccept(null, remote, generation);
                return;
            }
            switch (remote.stateForRuntime()) {
                case FAILED_BEFORE_ACCEPTANCE, CANCELLED_BEFORE_ACCEPTANCE ->
                        rerouteKnownPreaccept(null, remote, generation);
                case PENDING, FAILED_AFTER_ACCEPTANCE, ACCEPTANCE_UNCERTAIN ->
                        remoteOutcomeUncertainForRuntime(generation);
                case ACCEPTED, COMPLETED -> markRemoteAcceptedForRuntime(generation, remote);
            }
        }
    }

    @Override
    public void groupTerminatedBeforeAcceptanceForRuntime() {
        ProtosActorDeliveryAttempt local;
        ProtosActorTransportRoute.Delivery remote;
        long generation;
        synchronized (this) {
            if (!routing || terminal) {
                return;
            }
            local = attempt;
            remote = transportAttempt;
            generation = routingGeneration;
            if (local == null && remote == null) {
                selectedMember = null;
                routing = false;
                terminal = true;
            }
        }
        if (local == null && remote == null) {
            failKnownBeforeAcceptance();
            group.operationFinishedForRuntime(this);
            return;
        }
        if (local != null) {
            if (!local.cancelBeforeAcceptance()) {
                return;
            }
            finishKnownPreaccept(local, null, generation);
            return;
        }
        if (remote.cancelBeforeAcceptance()) {
            finishKnownPreaccept(null, remote, generation);
            return;
        }
        switch (remote.stateForRuntime()) {
            case FAILED_BEFORE_ACCEPTANCE, CANCELLED_BEFORE_ACCEPTANCE ->
                    finishKnownPreaccept(null, remote, generation);
            case PENDING, FAILED_AFTER_ACCEPTANCE, ACCEPTANCE_UNCERTAIN ->
                    remoteOutcomeUncertainForRuntime(generation);
            case ACCEPTED, COMPLETED -> markRemoteAcceptedForRuntime(generation, remote);
        }
    }

    private void cancellationRequested() {
        ProtosActorDeliveryAttempt local;
        ProtosActorTransportRoute.Delivery remote;
        long generation;
        synchronized (this) {
            if (terminal) {
                return;
            }
            local = attempt;
            remote = transportAttempt;
            generation = routingGeneration;
            if (local == null && remote == null) {
                if (group.cancelRoutingBeforeAcceptanceForRuntime(this)) {
                    routing = false;
                    terminal = true;
                    selectedMember = null;
                } else {
                    return;
                }
            }
        }
        if (local == null && remote == null) {
            future.cancelTerminal();
            group.operationFinishedForRuntime(this);
            return;
        }
        if (local != null) {
            if (local.cancelBeforeAcceptance()) {
                synchronized (this) {
                    if (terminal || attempt != local) {
                        return;
                    }
                    attempt = null;
                    selectedMember = null;
                    routing = false;
                    terminal = true;
                }
                future.cancelTerminal();
                group.operationFinishedForRuntime(this);
                return;
            }
            ProtosActorDeliveryAttempt.State state = local.state();
            if (state == ProtosActorDeliveryAttempt.State.ACCEPTED
                    || state == ProtosActorDeliveryAttempt.State.RUNNING) {
                future.cancelTerminal();
                return;
            }
            if (state == ProtosActorDeliveryAttempt.State.FAILED_AFTER_ACCEPTANCE) {
                failOutcomeUncertain();
            }
            return;
        }

        if (remote.cancelBeforeAcceptance()) {
            synchronized (this) {
                if (terminal || transportAttempt != remote || routingGeneration != generation) {
                    return;
                }
                transportAttempt = null;
                selectedMember = null;
                routing = false;
                terminal = true;
            }
            future.cancelTerminal();
            group.operationFinishedForRuntime(this);
            return;
        }
        switch (remote.stateForRuntime()) {
            case ACCEPTED -> {
                markRemoteAcceptedForRuntime(generation, remote);
                future.cancelTerminal();
            }
            case FAILED_AFTER_ACCEPTANCE, ACCEPTANCE_UNCERTAIN ->
                    remoteOutcomeUncertainForRuntime(generation);
            case FAILED_BEFORE_ACCEPTANCE, CANCELLED_BEFORE_ACCEPTANCE ->
                    rerouteKnownPreaccept(null, remote, generation);
            case COMPLETED, PENDING -> {
                // Terminal reply may already have won, or cancellation remains advisory/pending.
            }
        }
    }

    private void deliveryFailedForRuntime(ProtosActorDeliveryAttempt.State deliveryState) {
        if (deliveryState == ProtosActorDeliveryAttempt.State.FAILED_BEFORE_ACCEPTANCE) {
            ProtosActorDeliveryAttempt local;
            long generation;
            synchronized (this) {
                local = attempt;
                generation = routingGeneration;
            }
            rerouteKnownPreaccept(local, null, generation);
            return;
        }
        if (deliveryState == ProtosActorDeliveryAttempt.State.FAILED_AFTER_ACCEPTANCE) {
            synchronized (this) {
                if (terminal) {
                    return;
                }
                routing = false;
                terminal = true;
            }
            failOutcomeUncertain();
            group.operationFinishedForRuntime(this);
            return;
        }
        throw new IllegalArgumentException("unexpected Group request failure state " + deliveryState);
    }

    private void remoteStateChangedForRuntime(
            long generation,
            ProtosActorTransportRoute.Delivery delivery,
            ProtosActorTransportRoute.DeliveryState state) {
        Objects.requireNonNull(state, "state");
        switch (state) {
            case PENDING, CANCELLED_BEFORE_ACCEPTANCE, COMPLETED -> {
                // RequestObserver owns normal reply/failure terminalization. Cancellation is
                // classified by the caller that initiated it.
            }
            case FAILED_BEFORE_ACCEPTANCE -> remoteFailedBeforeAcceptanceForRuntime(generation);
            case ACCEPTED -> markRemoteAcceptedForRuntime(generation, delivery);
            case FAILED_AFTER_ACCEPTANCE, ACCEPTANCE_UNCERTAIN ->
                    remoteOutcomeUncertainForRuntime(generation);
        }
    }

    private void remoteFailedBeforeAcceptanceForRuntime(long generation) {
        ProtosActorTransportRoute.Delivery remote;
        synchronized (this) {
            if (terminal || !routing || routingGeneration != generation) {
                return;
            }
            remote = transportAttempt;
        }
        rerouteKnownPreaccept(null, remote, generation);
    }

    private void rerouteKnownPreaccept(
            ProtosActorDeliveryAttempt local,
            ProtosActorTransportRoute.Delivery remote,
            long generation) {
        synchronized (this) {
            if (terminal || !routing || routingGeneration != generation) {
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
        }
        if (!group.requeueAfterPreacceptFailureForRuntime(this)) {
            synchronized (this) {
                if (!terminal && routingGeneration == generation) {
                    routing = false;
                    terminal = true;
                }
            }
            failKnownBeforeAcceptance();
            group.operationFinishedForRuntime(this);
        }
    }

    private void finishKnownPreaccept(
            ProtosActorDeliveryAttempt local,
            ProtosActorTransportRoute.Delivery remote,
            long generation) {
        synchronized (this) {
            if (terminal || routingGeneration != generation) {
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
            routing = false;
            terminal = true;
        }
        failKnownBeforeAcceptance();
        group.operationFinishedForRuntime(this);
    }

    private void markRemoteAcceptedForRuntime(
            long generation, ProtosActorTransportRoute.Delivery remote) {
        boolean finish = false;
        synchronized (this) {
            if (!terminal
                    && routing
                    && routingGeneration == generation
                    && transportAttempt == remote) {
                routing = false;
                finish = true;
            }
        }
        if (finish) {
            group.operationFinishedForRuntime(this);
        }
    }

    private void remoteOutcomeUncertainForRuntime(long generation) {
        synchronized (this) {
            if (terminal || routingGeneration != generation) {
                return;
            }
            routing = false;
            terminal = true;
        }
        failOutcomeUncertain();
        group.operationFinishedForRuntime(this);
    }

    private void remoteReplyForRuntime(long generation, Object result) {
        Objects.requireNonNull(result, "result");
        synchronized (this) {
            if (terminal || routingGeneration != generation) {
                return;
            }
            routing = false;
            terminal = true;
        }
        try {
            Object transferred = ProtosActorValueTransfer.snapshotValue(result, callerActivation);
            future.resolve(transferred, callerActivation);
        } catch (ProtosSignalException nonTransferableReply) {
            future.fail(
                    ProtosCoreErrors.newOccurrence(
                            callerActivation,
                            ProtosCoreErrors.StandardError.NON_TRANSFERABLE_VALUE));
        }
        group.operationFinishedForRuntime(this);
    }

    private void executeAcceptedTurn(
            ProtosActor target, ProtosTask task, ProtosActorDeliveryAttempt delivery) {
        if (delivery == null) {
            throw new IllegalStateException("Group request lost its concrete delivery attempt");
        }
        if (!delivery.beginDispatchForRuntime()) {
            task.complete(ProtosNullValue.INSTANCE);
            return;
        }
        ProtosActivation turnActivation = target.newMessageActivationForRuntime();
        turnActivation.attachTask(task);
        task.executeAction(
                () -> {
                    ProtosObjectValue behavior =
                            target.currentBehavior()
                                    .orElseThrow(
                                            () -> new IllegalStateException(
                                                    "READY Group member lost current behavior"));
                    return ProtosInvocation.invokeMessage(behavior, selector, snapshot, turnActivation);
                });

        switch (task.state()) {
            case COMPLETED -> completeReply(task, turnActivation, delivery);
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
                            "Group request turn returned in non-yielded state " + task.state());
        }
    }

    private void completeReply(
            ProtosTask task,
            ProtosActivation turnActivation,
            ProtosActorDeliveryAttempt delivery) {
        Object result = task.result().orElseThrow();
        try {
            Object transferred = ProtosActorValueTransfer.snapshotValue(result, turnActivation);
            future.resolve(transferred, callerActivation);
            delivery.markCompletedForRuntime();
        } catch (ProtosSignalException nonTransferableReply) {
            future.fail(
                    ProtosCoreErrors.newOccurrence(
                            callerActivation,
                            ProtosCoreErrors.StandardError.NON_TRANSFERABLE_VALUE));
            delivery.markCompletedForRuntime();
        }
        synchronized (this) {
            routing = false;
            terminal = true;
        }
        group.operationFinishedForRuntime(this);
    }

    private void failKnownBeforeAcceptance() {
        future.fail(
                ProtosCoreErrors.newOccurrence(
                        callerActivation, ProtosCoreErrors.StandardError.ERROR));
    }

    private void failOutcomeUncertain() {
        future.fail(
                ProtosCoreErrors.newOccurrence(
                        callerActivation,
                        ProtosCoreErrors.StandardError.REQUEST_OUTCOME_UNCERTAIN));
    }

    private static boolean sameMember(ProtosActorRefValue left, ProtosActorRefValue right) {
        return left != null && right != null && left.denotesSameIncarnation(right);
    }
}
