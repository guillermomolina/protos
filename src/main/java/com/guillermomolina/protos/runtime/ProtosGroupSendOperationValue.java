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

/** One local Group-routed one-way operation preserving one logical message snapshot. */
public final class ProtosGroupSendOperationValue extends ProtosObjectValue
        implements ProtosSendOperationControl, ProtosActorGroupRuntime.RoutingOperation {
    enum State {
        ROUTING,
        COMPLETED,
        CANCELLED_BEFORE_ACCEPTANCE,
        FAILED_BEFORE_ACCEPTANCE,
        FAILED_AFTER_ACCEPTANCE
    }

    private final ProtosObjectValue sendOperationPrototype;
    private final ProtosGroupRefValue destination;
    private final ProtosActorGroupRuntime group;
    private final ProtosActorRefValue sender;
    private final String selector;
    private final List<Object> snapshot;
    private ProtosActor selectedActor;
    private ProtosActorDeliveryAttempt attempt;
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
        boolean finished = false;
        synchronized (this) {
            if (state != State.ROUTING) {
                return false;
            }
            if (attempt == null) {
                if (!group.cancelRoutingBeforeAcceptanceForRuntime(this)) {
                    return false;
                }
                state = State.CANCELLED_BEFORE_ACCEPTANCE;
                finished = true;
            } else if (attempt.cancelBeforeAcceptance()) {
                attempt = null;
                selectedActor = null;
                state = State.CANCELLED_BEFORE_ACCEPTANCE;
                finished = true;
            } else {
                return false;
            }
        }
        if (finished) {
            group.operationFinishedForRuntime(this);
        }
        return true;
    }

    @Override
    public ProtosGroupSendOperationValue retryAfterFailure() {
        synchronized (this) {
            if (state != State.FAILED_BEFORE_ACCEPTANCE
                    && state != State.FAILED_AFTER_ACCEPTANCE) {
                return null;
            }
        }
        return begin(sendOperationPrototype, destination, group, sender, selector, snapshot);
    }

    @Override
    public void routeToForRuntime(ProtosActor actor) {
        Objects.requireNonNull(actor, "actor");
        synchronized (this) {
            if (state != State.ROUTING || attempt != null) {
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
        boolean reroute = false;
        boolean fail = false;
        synchronized (this) {
            if (state != State.ROUTING || selectedActor != actor || attempt == null) {
                return;
            }
            if (!attempt.cancelBeforeAcceptance()) {
                return;
            }
            attempt = null;
            selectedActor = null;
            reroute = group.requeueAfterPreacceptFailureForRuntime(this);
            if (!reroute) {
                state = State.FAILED_BEFORE_ACCEPTANCE;
                fail = true;
            }
        }
        if (fail) {
            group.operationFinishedForRuntime(this);
        }
    }

    @Override
    public void groupTerminatedBeforeAcceptanceForRuntime() {
        boolean fail = false;
        synchronized (this) {
            if (state != State.ROUTING) {
                return;
            }
            if (attempt != null && !attempt.cancelBeforeAcceptance()) {
                // Concrete acceptance already won; Group termination cannot revoke ownership.
                return;
            }
            attempt = null;
            selectedActor = null;
            state = State.FAILED_BEFORE_ACCEPTANCE;
            fail = true;
        }
        if (fail) {
            group.operationFinishedForRuntime(this);
        }
    }

    private void deliveryFailedForRuntime(ProtosActorDeliveryAttempt.State deliveryState) {
        if (deliveryState == ProtosActorDeliveryAttempt.State.FAILED_BEFORE_ACCEPTANCE) {
            boolean requeued;
            synchronized (this) {
                if (state != State.ROUTING) {
                    return;
                }
                attempt = null;
                selectedActor = null;
                requeued = group.requeueAfterPreacceptFailureForRuntime(this);
                if (!requeued) {
                    state = State.FAILED_BEFORE_ACCEPTANCE;
                }
            }
            if (!requeued) {
                group.operationFinishedForRuntime(this);
            }
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

    synchronized State stateForTesting() {
        return state;
    }

    synchronized ProtosActorDeliveryAttempt.State deliveryStateForTesting() {
        return attempt == null ? null : attempt.state();
    }

    synchronized ProtosActor selectedActorForTesting() {
        return selectedActor;
    }

    List<Object> snapshotForTesting() {
        return snapshot;
    }
}
