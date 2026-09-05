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

/**
 * Core local identity-bearing one-way Actor delivery operation.
 *
 * <p>The logical destination, selector, and already-formed Actor snapshot are immutable operation
 * state. Retry creates a fresh operation identity over that same snapshot and never re-evaluates or
 * re-reads the source argument graph. Direct concrete-Actor accepted-work loss is recorded as a
 * post-acceptance delivery failure; distributed transport uncertainty remains a later integration.
 */
public final class ProtosSendOperationValue extends ProtosObjectValue {
    private final ProtosObjectValue sendOperationPrototype;
    private final ProtosActorRefValue destination;
    private final ProtosActorRefValue sender;
    private final String selector;
    private final List<Object> snapshot;
    private ProtosActorDeliveryAttempt attempt;

    private ProtosSendOperationValue(
            ProtosObjectValue sendOperationPrototype,
            ProtosActorRefValue destination,
            ProtosActorRefValue sender,
            String selector,
            List<Object> snapshot) {
        super(Objects.requireNonNull(sendOperationPrototype, "sendOperationPrototype"));
        this.sendOperationPrototype = sendOperationPrototype;
        this.destination = Objects.requireNonNull(destination, "destination");
        this.sender = Objects.requireNonNull(sender, "sender");
        this.selector = Objects.requireNonNull(selector, "selector");
        this.snapshot = List.copyOf(Objects.requireNonNull(snapshot, "snapshot"));
    }

    public static ProtosSendOperationValue begin(
            ProtosObjectValue sendOperationPrototype,
            ProtosActorRefValue destination,
            ProtosActorRefValue sender,
            String selector,
            List<Object> snapshot) {
        ProtosSendOperationValue operation =
                new ProtosSendOperationValue(
                        sendOperationPrototype, destination, sender, selector, snapshot);
        operation.beginAttempt();
        return operation;
    }

    /** True only when this call establishes known cancellation before concrete-Actor acceptance. */
    public boolean cancelBeforeAcceptance() {
        ProtosActorDeliveryAttempt current;
        synchronized (this) {
            current = attempt;
        }
        return current != null && current.cancelBeforeAcceptance();
    }

    /**
     * Returns a fresh retry operation after known terminal delivery failure, otherwise null.
     * The caller protocol turns null into the ordinary Error required by retry().
     */
    public ProtosSendOperationValue retryAfterFailure() {
        ProtosActorDeliveryAttempt current;
        synchronized (this) {
            current = attempt;
        }
        if (current == null || !current.retryableForRuntime()) {
            return null;
        }
        return begin(sendOperationPrototype, destination, sender, selector, snapshot);
    }

    ProtosActorDeliveryAttempt.State deliveryStateForTesting() {
        synchronized (this) {
            return attempt.state();
        }
    }

    List<Object> snapshotForTesting() {
        return snapshot;
    }

    private void beginAttempt() {
        ProtosActor target = destination.localActorForRuntime();
        ProtosActorDeliveryAdmission admission = target.deliveryAdmissionForRuntime();
        final ProtosActorDeliveryAttempt[] holder = new ProtosActorDeliveryAttempt[1];
        ProtosActorDeliveryAttempt created =
                new ProtosActorDeliveryAttempt(
                        admission,
                        sender,
                        task -> executeAcceptedTurn(target, task, holder[0]));
        holder[0] = created;
        synchronized (this) {
            attempt = created;
        }
        admission.submit(created);
    }

    private void executeAcceptedTurn(
            ProtosActor target,
            ProtosTask task,
            ProtosActorDeliveryAttempt delivery) {
        if (delivery == null) {
            throw new IllegalStateException("accepted delivery lost its attempt identity");
        }
        if (!delivery.beginDispatchForRuntime()) {
            // Termination won before this accepted turn began. The delivery attempt already records
            // post-acceptance loss; terminalize only the fresh runtime task, never application code.
            task.complete(ProtosNullValue.INSTANCE);
            return;
        }
        try {
            task.executeAction(
                    () -> {
                        ProtosActivation turnActivation = target.newMessageActivationForRuntime();
                        turnActivation.attachTask(task);
                        ProtosObjectValue behavior =
                                target.currentBehavior()
                                        .orElseThrow(
                                                () ->
                                                        new IllegalStateException(
                                                                "READY Actor lost current behavior"));
                        ProtosInvocation.invokeMessage(
                                behavior, selector, snapshot, turnActivation);
                        // send() ignores the handler's ordinary result.
                        return ProtosNullValue.INSTANCE;
                    });
        } catch (RuntimeException failure) {
            delivery.markFailedAfterAcceptanceForRuntime();
            throw failure;
        }

        switch (task.state()) {
            case COMPLETED -> delivery.markCompletedForRuntime();
            case FAILED, CANCELLED -> {
                delivery.markFailedAfterAcceptanceForRuntime();
                terminateAfterUnhandledOrCancelledTurn(target);
            }
            case SUSPENDED -> {
                // The same task continuation resumes later in this Actor domain.
            }
            case RUNNABLE, RUNNING ->
                    throw new IllegalStateException(
                            "message turn returned in non-yielded state " + task.state());
        }
    }

    private static void terminateAfterUnhandledOrCancelledTurn(ProtosActor actor) {
        actor.requestTerminationForRuntime();
    }
}
