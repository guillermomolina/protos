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

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

final class ProtosActorDeliveryAdmissionTest {
    @Test
    void availableCapacityCrossesAcceptanceImmediatelyEvenWhileInitializing() {
        ProtosActor destination = actorWithMailboxCapacity(2);

        ProtosActorDeliveryAttempt attempt =
                destination.beginDeliveryForRuntime(null, task -> task.complete(null));

        assertEquals(ProtosActorDeliveryAttempt.State.ACCEPTED, attempt.state());
        assertEquals(ProtosActor.LifecycleState.INITIALIZING, destination.lifecycleState());
        assertEquals(1, destination.mailboxForRuntime().size());
        assertEquals(0, destination.deliveryAdmissionForRuntime().pendingCountForTesting());
    }

    @Test
    void fullMailboxBackpressuresAndCapacityWakeupAdmitsOldestPendingAttempt() {
        ProtosActor destination = actorWithMailboxCapacity(1);
        ProtosActorDeliveryAttempt first = delivery(destination, null);
        ProtosActorDeliveryAttempt second = delivery(destination, null);
        ProtosActorDeliveryAttempt third = delivery(destination, null);

        assertEquals(ProtosActorDeliveryAttempt.State.ACCEPTED, first.state());
        assertEquals(ProtosActorDeliveryAttempt.State.PENDING, second.state());
        assertEquals(ProtosActorDeliveryAttempt.State.PENDING, third.state());
        assertEquals(2, destination.deliveryAdmissionForRuntime().pendingCountForTesting());

        assertNotNull(destination.mailboxForRuntime().pollForDispatch());
        assertEquals(ProtosActorDeliveryAttempt.State.ACCEPTED, second.state());
        assertEquals(ProtosActorDeliveryAttempt.State.PENDING, third.state());
        assertEquals(1, destination.deliveryAdmissionForRuntime().pendingCountForTesting());

        assertNotNull(destination.mailboxForRuntime().pollForDispatch());
        assertEquals(ProtosActorDeliveryAttempt.State.ACCEPTED, third.state());
        assertEquals(0, destination.deliveryAdmissionForRuntime().pendingCountForTesting());
    }

    @Test
    void knownPreAcceptanceCancellationRemovesPendingAttemptAndCannotCancelAcceptedWork() {
        ProtosActor destination = actorWithMailboxCapacity(1);
        ProtosActorDeliveryAttempt accepted = delivery(destination, null);
        ProtosActorDeliveryAttempt cancelled = delivery(destination, null);
        ProtosActorDeliveryAttempt later = delivery(destination, null);

        assertFalse(accepted.cancelBeforeAcceptance());
        assertTrue(cancelled.cancelBeforeAcceptance());
        assertFalse(cancelled.cancelBeforeAcceptance());
        assertEquals(
                ProtosActorDeliveryAttempt.State.CANCELLED_BEFORE_ACCEPTANCE,
                cancelled.state());

        destination.mailboxForRuntime().pollForDispatch();
        assertEquals(ProtosActorDeliveryAttempt.State.ACCEPTED, later.state());
    }

    @Test
    void terminatingActorFailsPendingAndFutureAttemptsButDoesNotRewriteAcceptedAttempt() {
        ProtosActor destination = actorWithMailboxCapacity(1);
        ProtosActorDeliveryAttempt accepted = delivery(destination, null);
        ProtosActorDeliveryAttempt pending = delivery(destination, null);

        assertTrue(destination.beginTermination());

        assertEquals(ProtosActorDeliveryAttempt.State.ACCEPTED, accepted.state());
        assertEquals(
                ProtosActorDeliveryAttempt.State.FAILED_BEFORE_ACCEPTANCE,
                pending.state());
        assertEquals(0, destination.deliveryAdmissionForRuntime().pendingCountForTesting());

        ProtosActorDeliveryAttempt afterCutover = delivery(destination, null);
        assertEquals(
                ProtosActorDeliveryAttempt.State.FAILED_BEFORE_ACCEPTANCE,
                afterCutover.state());
        assertFalse(afterCutover.cancelBeforeAcceptance());
    }

    @Test
    void fifoPendingDisciplinePreservesSameSenderOrderAndWeakAdmissionFairness() {
        ProtosActor source = actorWithMailboxCapacity(1);
        ProtosActor otherSource = actorWithMailboxCapacity(1);
        ProtosActor destination = actorWithMailboxCapacity(1);

        ProtosActorDeliveryAttempt accepted = delivery(destination, source.reference());
        ProtosActorDeliveryAttempt sameSenderEarlier = delivery(destination, source.reference());
        ProtosActorDeliveryAttempt unrelatedLater = delivery(destination, otherSource.reference());

        assertEquals(ProtosActorDeliveryAttempt.State.ACCEPTED, accepted.state());
        assertEquals(ProtosActorDeliveryAttempt.State.PENDING, sameSenderEarlier.state());
        assertEquals(ProtosActorDeliveryAttempt.State.PENDING, unrelatedLater.state());

        destination.mailboxForRuntime().pollForDispatch();
        assertEquals(ProtosActorDeliveryAttempt.State.ACCEPTED, sameSenderEarlier.state());
        assertEquals(ProtosActorDeliveryAttempt.State.PENDING, unrelatedLater.state());

        destination.mailboxForRuntime().pollForDispatch();
        assertEquals(ProtosActorDeliveryAttempt.State.ACCEPTED, unrelatedLater.state());
    }

    @Test
    void cancellationRacingCapacityReleaseProducesOneStableBoundaryOutcome() throws Exception {
        ProtosActor destination = actorWithMailboxCapacity(1);
        delivery(destination, null);
        ProtosActorDeliveryAttempt racing = delivery(destination, null);
        CountDownLatch start = new CountDownLatch(1);
        AtomicBoolean cancelled = new AtomicBoolean();

        Thread cancel = new Thread(() -> {
            await(start);
            cancelled.set(racing.cancelBeforeAcceptance());
        });
        Thread release = new Thread(() -> {
            await(start);
            destination.mailboxForRuntime().pollForDispatch();
        });
        cancel.start();
        release.start();
        start.countDown();
        cancel.join();
        release.join();

        ProtosActorDeliveryAttempt.State state = racing.state();
        assertTrue(
                state == ProtosActorDeliveryAttempt.State.ACCEPTED
                        || state == ProtosActorDeliveryAttempt.State.CANCELLED_BEFORE_ACCEPTANCE);
        assertEquals(
                state == ProtosActorDeliveryAttempt.State.CANCELLED_BEFORE_ACCEPTANCE,
                cancelled.get());
        assertNotEquals(ProtosActorDeliveryAttempt.State.PENDING, state);
    }

    private static ProtosActorDeliveryAttempt delivery(
            ProtosActor destination, ProtosActorRefValue sender) {
        return destination.beginDeliveryForRuntime(sender, task -> task.complete(null));
    }

    private static ProtosActor actorWithMailboxCapacity(int capacity) {
        return new ProtosActor(
                new ProtosObjectValue(ProtosObjectValue.rootObject()).freeze(),
                new ProtosActorExecutionDomain(),
                new ProtosActorModuleState(),
                capacity);
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new AssertionError(error);
        }
    }
}
