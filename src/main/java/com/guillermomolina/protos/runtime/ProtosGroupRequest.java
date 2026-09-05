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

/** Runtime-owned local ActorGroup request/reply operation behind a future GroupRef bridge. */
public final class ProtosGroupRequest implements ProtosActorGroupRuntime.RoutingOperation {
    private final ProtosGroupRefValue destination;
    private final ProtosActorGroupRuntime group;
    private final ProtosActorRefValue sender;
    private final String selector;
    private final List<Object> snapshot;
    private final ProtosActivation callerActivation;
    private final ProtosFutureValue future;
    private ProtosActor selectedActor;
    private ProtosActorDeliveryAttempt attempt;
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
    public void routeToForRuntime(ProtosActor actor) {
        synchronized (this) {
            if (!routing || terminal || attempt != null) {
                return;
            }
            selectedActor = actor;
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
    public void memberBecameIneligibleForRuntime(ProtosActor actor) {
        boolean fail = false;
        synchronized (this) {
            if (!routing || terminal || selectedActor != actor || attempt == null) {
                return;
            }
            if (!attempt.cancelBeforeAcceptance()) {
                return;
            }
            attempt = null;
            selectedActor = null;
            if (!group.requeueAfterPreacceptFailureForRuntime(this)) {
                routing = false;
                terminal = true;
                fail = true;
            }
        }
        if (fail) {
            failKnownBeforeAcceptance();
            group.operationFinishedForRuntime(this);
        }
    }

    @Override
    public void groupTerminatedBeforeAcceptanceForRuntime() {
        boolean fail = false;
        synchronized (this) {
            if (!routing || terminal) {
                return;
            }
            if (attempt != null && !attempt.cancelBeforeAcceptance()) {
                return;
            }
            attempt = null;
            selectedActor = null;
            routing = false;
            terminal = true;
            fail = true;
        }
        if (fail) {
            failKnownBeforeAcceptance();
            group.operationFinishedForRuntime(this);
        }
    }

    private void cancellationRequested() {
        boolean cancelled = false;
        ProtosActorDeliveryAttempt current;
        synchronized (this) {
            if (terminal) {
                return;
            }
            current = attempt;
            if (current == null) {
                if (group.cancelRoutingBeforeAcceptanceForRuntime(this)) {
                    routing = false;
                    terminal = true;
                    cancelled = true;
                }
            } else if (current.cancelBeforeAcceptance()) {
                attempt = null;
                selectedActor = null;
                routing = false;
                terminal = true;
                cancelled = true;
            } else {
                ProtosActorDeliveryAttempt.State state = current.state();
                if (state == ProtosActorDeliveryAttempt.State.ACCEPTED
                        || state == ProtosActorDeliveryAttempt.State.RUNNING) {
                    future.cancelTerminal();
                    return;
                }
                if (state == ProtosActorDeliveryAttempt.State.FAILED_AFTER_ACCEPTANCE) {
                    failOutcomeUncertain();
                    return;
                }
            }
        }
        if (cancelled) {
            future.cancelTerminal();
            group.operationFinishedForRuntime(this);
        }
    }

    private void deliveryFailedForRuntime(ProtosActorDeliveryAttempt.State deliveryState) {
        if (deliveryState == ProtosActorDeliveryAttempt.State.FAILED_BEFORE_ACCEPTANCE) {
            boolean requeued;
            synchronized (this) {
                if (!routing || terminal) {
                    return;
                }
                attempt = null;
                selectedActor = null;
                requeued = group.requeueAfterPreacceptFailureForRuntime(this);
                if (!requeued) {
                    routing = false;
                    terminal = true;
                }
            }
            if (!requeued) {
                failKnownBeforeAcceptance();
                group.operationFinishedForRuntime(this);
            }
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
}
