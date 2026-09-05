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

import com.guillermomolina.protos.execution.ProtosCoreBootstrap;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** Cross-domain closure evidence for the implemented I011 Actor/Group communication semantics. */
final class ProtosActorCrossDomainConformanceTest {
    private static final Path CORE = Path.of("protos", "lib", "core");

    @Test
    void groupRefContinuitySurvivesCrossProcessMemberReplacementWithoutRetargetingActorRef()
            throws Exception {
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
        ProtosProcessRuntime callerProcess = new ProtosProcessRuntime(actorRefPrototype());
        ProtosProcessRuntime firstProcess = new ProtosProcessRuntime(actorRefPrototype());
        ProtosProcessRuntime secondProcess = new ProtosProcessRuntime(actorRefPrototype());
        ProtosActor sender = ready(callerProcess.rootActorForRuntime(), prelude, noopBehavior());
        AtomicInteger firstCalls = new AtomicInteger();
        AtomicInteger secondCalls = new AtomicInteger();
        ProtosActor first = ready(firstProcess.rootActorForRuntime(), prelude, pingBehavior(firstCalls));
        ProtosActor second = ready(secondProcess.rootActorForRuntime(), prelude, pingBehavior(secondCalls));

        ProtosActorGroupRuntime group = new ProtosActorGroupRuntime();
        assertTrue(group.addMemberForRuntime(first));
        ProtosGroupRefValue original = reference(group);
        ProtosActivation caller = callerActivation(prelude, sender);
        ProtosGroupRefValue transferred =
                assertInstanceOf(
                        ProtosGroupRefValue.class,
                        ProtosActorValueTransfer.snapshotValue(original, caller));
        assertNotSame(original, transferred);
        assertTrue(ProtosIdentity.identical(original, transferred));

        ProtosGroupSendOperationValue firstSend =
                transferred.beginSendForRuntime(
                        sendOperationPrototype(), sender.reference(), "ping", List.of());
        assertSame(first, firstSend.selectedActorForTesting());
        dispatchAccepted(first);
        assertEquals(1, firstCalls.get());

        assertTrue(group.removeMemberForRuntime(first));
        assertTrue(firstProcess.requestTerminationForRuntime());
        assertEquals(ProtosActor.LifecycleState.TERMINATED, first.lifecycleState());
        assertSame(first, first.reference().localActorForRuntime());

        assertTrue(group.addMemberForRuntime(second));
        ProtosGroupSendOperationValue secondSend =
                original.beginSendForRuntime(
                        sendOperationPrototype(), sender.reference(), "ping", List.of());
        assertSame(second, secondSend.selectedActorForTesting());
        dispatchAccepted(second);
        assertEquals(1, secondCalls.get());
        assertTrue(ProtosIdentity.identical(original, transferred));
        assertEquals(original.groupIdentityForRuntime(), transferred.groupIdentityForRuntime());
    }

    @Test
    void selectedProcessTerminationBeforeAcceptanceReroutesToAnotherProcess() throws Exception {
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
        ProtosProcessRuntime callerProcess = new ProtosProcessRuntime(actorRefPrototype());
        ProtosProcessRuntime firstProcess = new ProtosProcessRuntime(actorRefPrototype());
        ProtosProcessRuntime secondProcess = new ProtosProcessRuntime(actorRefPrototype());
        ProtosActor sender = ready(callerProcess.rootActorForRuntime(), prelude, noopBehavior());
        AtomicInteger firstCalls = new AtomicInteger();
        AtomicInteger secondCalls = new AtomicInteger();
        ProtosActor first = ready(firstProcess.rootActorForRuntime(), prelude, pingBehavior(firstCalls));
        ProtosActor second = ready(secondProcess.rootActorForRuntime(), prelude, pingBehavior(secondCalls));

        for (int i = 0; i < first.mailboxForRuntime().capacity(); i++) {
            assertTrue(
                    first.mailboxForRuntime()
                            .tryAccept(task -> task.complete(ProtosNullValue.INSTANCE)));
        }

        ProtosActorGroupRuntime group = new ProtosActorGroupRuntime();
        group.addMemberForRuntime(first);
        group.addMemberForRuntime(second);
        ProtosGroupSendOperationValue operation =
                reference(group)
                        .beginSendForRuntime(
                                sendOperationPrototype(), sender.reference(), "ping", List.of());
        assertSame(first, operation.selectedActorForTesting());
        assertEquals(
                ProtosActorDeliveryAttempt.State.PENDING,
                operation.deliveryStateForTesting());

        assertTrue(firstProcess.requestTerminationForRuntime());

        assertSame(second, operation.selectedActorForTesting());
        assertEquals(
                ProtosActorDeliveryAttempt.State.ACCEPTED,
                operation.deliveryStateForTesting());
        dispatchAccepted(second);
        assertEquals(0, firstCalls.get());
        assertEquals(1, secondCalls.get());
        assertEquals(ProtosGroupSendOperationValue.State.COMPLETED, operation.stateForTesting());
    }

    @Test
    void acceptedGroupRequestLostToTargetProcessTerminationIsUncertainAndNeverRerouted()
            throws Exception {
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
        ProtosProcessRuntime callerProcess = new ProtosProcessRuntime(actorRefPrototype());
        ProtosProcessRuntime targetProcess = new ProtosProcessRuntime(actorRefPrototype());
        ProtosProcessRuntime fallbackProcess = new ProtosProcessRuntime(actorRefPrototype());
        ProtosActor sender = ready(callerProcess.rootActorForRuntime(), prelude, noopBehavior());
        ProtosActor target = ready(targetProcess.rootActorForRuntime(), prelude, echoBehavior());
        AtomicInteger fallbackCalls = new AtomicInteger();
        ProtosActor fallback =
                ready(fallbackProcess.rootActorForRuntime(), prelude, pingBehavior(fallbackCalls));
        ProtosActorGroupRuntime group = new ProtosActorGroupRuntime();
        group.addMemberForRuntime(target);
        group.addMemberForRuntime(fallback);
        ProtosActivation caller = callerActivation(prelude, sender);

        ProtosFutureValue future =
                reference(group)
                        .beginRequestForRuntime(
                                sender.reference(),
                                "echo",
                                List.of(new ProtosStringValue("payload")),
                                caller);
        assertEquals(1, target.mailboxForRuntime().size());
        assertEquals(0, fallback.mailboxForRuntime().size());

        assertTrue(targetProcess.requestTerminationForRuntime());

        assertEquals(ProtosFutureValue.State.FAILED, future.state());
        assertSame(
                prelude.standardErrorPrototype("RequestOutcomeUncertain"),
                future.failedError().orElseThrow().parent().orElseThrow());
        assertEquals(0, fallback.mailboxForRuntime().size());
        assertEquals(0, fallbackCalls.get());
    }

    @Test
    void readyVersusCancellationRaceNeverDuplicatesGroupDelivery() throws Exception {
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
        ProtosActor sender = new ProtosActor(actorRefPrototype());
        ready(sender, prelude, noopBehavior());

        for (int iteration = 0; iteration < 128; iteration++) {
            AtomicInteger calls = new AtomicInteger();
            ProtosActor member = new ProtosActor(actorRefPrototype());
            member.bindMessageEnvironmentForRuntime(
                    prelude, new ProtosModuleKey("race-target-" + iteration));
            ProtosObjectValue behavior = pingBehavior(calls);
            ProtosActorGroupRuntime group = new ProtosActorGroupRuntime();
            group.addMemberForRuntime(member);
            ProtosGroupSendOperationValue operation =
                    reference(group)
                            .beginSendForRuntime(
                                    sendOperationPrototype(),
                                    sender.reference(),
                                    "ping",
                                    List.of());
            assertNull(operation.deliveryStateForTesting());

            CountDownLatch start = new CountDownLatch(1);
            AtomicBoolean cancelled = new AtomicBoolean();
            AtomicReference<Throwable> failure = new AtomicReference<>();
            Thread cancellation =
                    new Thread(
                            () -> {
                                await(start, failure);
                                if (failure.get() == null) {
                                    try {
                                        cancelled.set(operation.cancelBeforeAcceptance());
                                    } catch (Throwable t) {
                                        failure.compareAndSet(null, t);
                                    }
                                }
                            },
                            "i011-18-cancel-" + iteration);
            Thread readiness =
                    new Thread(
                            () -> {
                                await(start, failure);
                                if (failure.get() == null) {
                                    try {
                                        member.completeInitialization(behavior);
                                    } catch (Throwable t) {
                                        failure.compareAndSet(null, t);
                                    }
                                }
                            },
                            "i011-18-ready-" + iteration);

            cancellation.start();
            readiness.start();
            start.countDown();
            cancellation.join(5000L);
            readiness.join(5000L);
            assertFalse(cancellation.isAlive(), "cancellation thread stalled");
            assertFalse(readiness.isAlive(), "readiness thread stalled");
            if (failure.get() != null) {
                throw new AssertionError("race worker failed", failure.get());
            }

            if (cancelled.get()) {
                assertEquals(
                        ProtosGroupSendOperationValue.State.CANCELLED_BEFORE_ACCEPTANCE,
                        operation.stateForTesting());
                assertEquals(0, member.mailboxForRuntime().size());
                assertEquals(0, calls.get());
            } else {
                assertEquals(
                        ProtosActorDeliveryAttempt.State.ACCEPTED,
                        operation.deliveryStateForTesting());
                assertEquals(1, member.mailboxForRuntime().size());
                dispatchAccepted(member);
                assertEquals(1, calls.get());
                assertEquals(
                        ProtosGroupSendOperationValue.State.COMPLETED,
                        operation.stateForTesting());
            }
        }
    }

    private static void await(CountDownLatch latch, AtomicReference<Throwable> failure) {
        try {
            latch.await();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            failure.compareAndSet(null, interrupted);
        }
    }

    private static ProtosActivation callerActivation(ProtosPrelude prelude, ProtosActor actor) {
        return prelude.newModuleActivation(
                actor.moduleState(),
                new ProtosModuleKey("caller"),
                prelude.newExecutionContext(),
                actor.executionDomain());
    }

    private static ProtosActor ready(
            ProtosActor actor, ProtosPrelude prelude, ProtosObjectValue behavior) {
        actor.bindMessageEnvironmentForRuntime(
                prelude, new ProtosModuleKey("actor-" + actor.incarnationIdentityForRuntime()));
        assertTrue(actor.completeInitialization(behavior));
        return actor;
    }

    private static ProtosObjectValue noopBehavior() {
        return new ProtosObjectValue(ProtosObjectValue.rootObject());
    }

    private static ProtosObjectValue pingBehavior(AtomicInteger calls) {
        ProtosObjectValue behavior = new ProtosObjectValue(ProtosObjectValue.rootObject());
        behavior.createLocalSlot(
                "ping",
                ProtosClosureValue.nativeClosure(
                        (activation, arguments) -> {
                            calls.incrementAndGet();
                            return ProtosNullValue.INSTANCE;
                        }));
        return behavior;
    }

    private static ProtosObjectValue echoBehavior() {
        ProtosObjectValue behavior = new ProtosObjectValue(ProtosObjectValue.rootObject());
        behavior.createLocalSlot(
                "echo",
                ProtosClosureValue.nativeClosure(
                        (activation, arguments) -> arguments.get(0)));
        return behavior;
    }

    private static void dispatchAccepted(ProtosActor actor) {
        ProtosTask.Continuation turn = actor.mailboxForRuntime().pollForDispatch();
        assertNotNull(turn);
        actor.executionDomain().dispatchAcceptedTurn(turn);
    }

    private static ProtosGroupRefValue reference(ProtosActorGroupRuntime group) {
        return group.acquireReferenceForRuntime(
                groupRefPrototype(),
                UUID.fromString("18181818-1818-1818-1818-181818181818"));
    }

    private static ProtosObjectValue actorRefPrototype() {
        return new ProtosObjectValue(ProtosObjectValue.rootObject()).freeze();
    }

    private static ProtosObjectValue groupRefPrototype() {
        return new ProtosObjectValue(ProtosObjectValue.rootObject()).freeze();
    }

    private static ProtosObjectValue sendOperationPrototype() {
        return new ProtosObjectValue(ProtosObjectValue.rootObject()).freeze();
    }
}
