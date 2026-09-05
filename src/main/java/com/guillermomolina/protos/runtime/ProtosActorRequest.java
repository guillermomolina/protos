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
 * Runtime-owned request/reply operation behind ActorRef.request(...).
 *
 * <p>The public operation is the caller-domain Future. Delivery reuses the ordinary concrete-Actor
 * admission boundary and the original message snapshot. The destination's normal handler result is
 * transferred back across the Actor boundary before resolving the caller Future; destination-local
 * failures never cross as Error objects.
 */
public final class ProtosActorRequest {
    private final ProtosActorRefValue destination;
    private final ProtosActorRefValue sender;
    private final String selector;
    private final List<Object> snapshot;
    private final ProtosActivation callerActivation;
    private final ProtosFutureValue future;
    private ProtosActorDeliveryAttempt attempt;

    private ProtosActorRequest(
            ProtosActorRefValue destination,
            ProtosActorRefValue sender,
            String selector,
            List<Object> snapshot,
            ProtosActivation callerActivation,
            ProtosFutureValue future) {
        this.destination = Objects.requireNonNull(destination, "destination");
        this.sender = Objects.requireNonNull(sender, "sender");
        this.selector = Objects.requireNonNull(selector, "selector");
        this.snapshot = List.copyOf(Objects.requireNonNull(snapshot, "snapshot"));
        this.callerActivation = Objects.requireNonNull(callerActivation, "callerActivation");
        this.future = Objects.requireNonNull(future, "future");
    }

    /** Creates one fresh caller-domain request Future and starts the corresponding delivery. */
    public static ProtosFutureValue begin(
            ProtosActorRefValue destination,
            ProtosActorRefValue sender,
            String selector,
            List<Object> snapshot,
            ProtosActivation callerActivation) {
        Objects.requireNonNull(callerActivation, "callerActivation");
        ProtosObjectValue futurePrototype =
                callerActivation
                        .prelude()
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "ActorRef.request requires an owning Core prelude"))
                        .futurePrototype();
        ProtosFutureValue future =
                new ProtosFutureValue(futurePrototype, callerActivation.executionDomain());
        ProtosActorRequest request =
                new ProtosActorRequest(
                        destination, sender, selector, snapshot, callerActivation, future);
        future.attachCancellationProducer(request::cancellationRequested);
        request.beginAttempt();
        callerActivation.executionDomain().registerActorNonTaskFuture(future);
        return future;
    }

    private void beginAttempt() {
        ProtosActor target = destination.localActorForRuntime();
        ProtosActorDeliveryAdmission admission = target.deliveryAdmissionForRuntime();
        final ProtosActorDeliveryAttempt[] holder = new ProtosActorDeliveryAttempt[1];
        ProtosActorDeliveryAttempt created =
                new ProtosActorDeliveryAttempt(
                        admission,
                        sender,
                        task -> executeAcceptedTurn(target, task, holder[0]),
                        this::deliveryFailed);
        holder[0] = created;
        synchronized (this) {
            attempt = created;
        }
        admission.submit(created);
    }

    private void cancellationRequested() {
        ProtosActorDeliveryAttempt current;
        synchronized (this) {
            current = attempt;
        }
        if (current == null) {
            future.cancelTerminal();
            return;
        }
        if (current.cancelBeforeAcceptance()) {
            future.cancelTerminal();
            return;
        }

        switch (current.state()) {
            case CANCELLED_BEFORE_ACCEPTANCE -> future.cancelTerminal();
            case FAILED_BEFORE_ACCEPTANCE -> failKnownBeforeAcceptance();
            case ACCEPTED, RUNNING -> future.cancelTerminal();
            case FAILED_AFTER_ACCEPTANCE -> failOutcomeUncertain();
            case COMPLETED -> {
                // Reply/terminal outcome already won, or is completing concurrently.
            }
            case PENDING -> {
                // A cancellation request is advisory until the producer establishes an outcome.
                // In the direct concrete-Actor path cancelBeforeAcceptance normally removes this
                // state synchronously; retaining PENDING here is safer than falsely claiming
                // non-delivery.
            }
        }
    }

    private void deliveryFailed(ProtosActorDeliveryAttempt.State state) {
        switch (state) {
            case FAILED_BEFORE_ACCEPTANCE -> failKnownBeforeAcceptance();
            case FAILED_AFTER_ACCEPTANCE -> failOutcomeUncertain();
            default ->
                    throw new IllegalArgumentException(
                            "delivery failure callback received non-failure state " + state);
        }
    }

    private void executeAcceptedTurn(
            ProtosActor target, ProtosTask task, ProtosActorDeliveryAttempt delivery) {
        if (delivery == null) {
            throw new IllegalStateException("request delivery lost its attempt identity");
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
                                            () ->
                                                    new IllegalStateException(
                                                            "READY Actor lost current behavior"));
                    return ProtosInvocation.invokeMessage(
                            behavior, selector, snapshot, turnActivation);
                });

        switch (task.state()) {
            case COMPLETED -> completeReply(task, turnActivation, delivery);
            case FAILED -> {
                delivery.markFailedAfterAcceptanceForRuntime();
                Object failure =
                        task.failure()
                                .orElseThrow(
                                        () ->
                                                new IllegalStateException(
                                                        "failed Actor request turn lost failure value"));
                target.failForRuntime(failure);
            }
            case CANCELLED -> {
                delivery.markFailedAfterAcceptanceForRuntime();
                target.requestTerminationForRuntime();
            }
            case SUSPENDED -> {
                // The same task/evaluator continuation resumes as another Actor segment.
            }
            case RUNNABLE, RUNNING ->
                    throw new IllegalStateException(
                            "request turn returned in non-yielded state " + task.state());
        }
    }

    private void completeReply(
            ProtosTask task,
            ProtosActivation turnActivation,
            ProtosActorDeliveryAttempt delivery) {
        Object result =
                task.result()
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "completed request handler has no result"));
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
