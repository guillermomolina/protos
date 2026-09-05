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
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class ProtosActorGroupCommunicationTest {
    private static final Path CORE = Path.of("protos", "lib", "core");

    @Test
    void zeroEligibleMembershipBackpressuresUntilInitializingMemberBecomesReady() throws Exception {
        Fixture x = fixture();
        ProtosActorGroupRuntime group = new ProtosActorGroupRuntime();
        ProtosActor member = new ProtosActor(actorRefPrototype());
        group.addMemberForRuntime(member);
        ProtosGroupRefValue reference = reference(group);

        ProtosGroupSendOperationValue operation =
                reference.beginSendForRuntime(
                        sendOperationPrototype(), x.sender.reference(), "ping", List.of());
        assertEquals(ProtosGroupSendOperationValue.State.ROUTING, operation.stateForTesting());
        assertEquals(1, group.pendingOperationCountForTesting());
        assertNull(operation.deliveryStateForTesting());

        ready(member, x.prelude, pingBehavior(new AtomicInteger()));

        assertEquals(ProtosActorDeliveryAttempt.State.ACCEPTED, operation.deliveryStateForTesting());
        assertEquals(0, group.pendingOperationCountForTesting());
    }

    @Test
    void selectedMemberRemovalReroutesOnlyBeforeConcreteAcceptance() throws Exception {
        Fixture x = fixture();
        ProtosActorGroupRuntime group = new ProtosActorGroupRuntime();
        ProtosActor first = readyWithCapacity(x.prelude, pingBehavior(new AtomicInteger()), 1);
        ProtosActor second = ready(x.prelude, pingBehavior(new AtomicInteger()));
        assertTrue(first.mailboxForRuntime().tryAccept(task -> task.complete(ProtosNullValue.INSTANCE)));
        group.addMemberForRuntime(first);
        group.addMemberForRuntime(second);

        ProtosGroupSendOperationValue operation =
                reference(group).beginSendForRuntime(
                        sendOperationPrototype(), x.sender.reference(), "ping", List.of());
        assertSame(first, operation.selectedActorForTesting());
        assertEquals(ProtosActorDeliveryAttempt.State.PENDING, operation.deliveryStateForTesting());

        assertTrue(group.removeMemberForRuntime(first));

        assertSame(second, operation.selectedActorForTesting());
        assertEquals(ProtosActorDeliveryAttempt.State.ACCEPTED, operation.deliveryStateForTesting());
    }

    @Test
    void groupTerminationFailsPendingButCannotRevokeAlreadyAcceptedWork() throws Exception {
        Fixture x = fixture();
        AtomicInteger calls = new AtomicInteger();
        ProtosActorGroupRuntime pendingGroup = new ProtosActorGroupRuntime();
        ProtosActor full = readyWithCapacity(x.prelude, pingBehavior(calls), 1);
        assertTrue(full.mailboxForRuntime().tryAccept(task -> task.complete(ProtosNullValue.INSTANCE)));
        pendingGroup.addMemberForRuntime(full);
        ProtosGroupSendOperationValue pending =
                reference(pendingGroup).beginSendForRuntime(
                        sendOperationPrototype(), x.sender.reference(), "ping", List.of());
        assertEquals(ProtosActorDeliveryAttempt.State.PENDING, pending.deliveryStateForTesting());

        assertTrue(pendingGroup.markTerminatedForRuntime());
        assertEquals(ProtosGroupSendOperationValue.State.FAILED_BEFORE_ACCEPTANCE, pending.stateForTesting());

        ProtosActorGroupRuntime acceptedGroup = new ProtosActorGroupRuntime();
        ProtosActor ready = ready(x.prelude, pingBehavior(calls));
        acceptedGroup.addMemberForRuntime(ready);
        ProtosGroupSendOperationValue accepted =
                reference(acceptedGroup).beginSendForRuntime(
                        sendOperationPrototype(), x.sender.reference(), "ping", List.of());
        assertEquals(ProtosActorDeliveryAttempt.State.ACCEPTED, accepted.deliveryStateForTesting());
        assertTrue(acceptedGroup.markTerminatedForRuntime());
        dispatchAccepted(ready);
        assertEquals(1, calls.get());
        assertEquals(ProtosGroupSendOperationValue.State.COMPLETED, accepted.stateForTesting());
    }

    @Test
    void groupRequestUsesOneSnapshotAndAcceptedLossIsOutcomeUncertain() throws Exception {
        Fixture x = fixture();
        ProtosActorGroupRuntime group = new ProtosActorGroupRuntime();
        ProtosActor target = ready(x.prelude, echoBehavior());
        group.addMemberForRuntime(target);
        ProtosObjectValue mutable = new ProtosObjectValue(ProtosObjectValue.rootObject());
        mutable.createLocalSlot("value", new ProtosStringValue("before"));
        List<Object> snapshot = ProtosActorValueTransfer.snapshotArguments(List.of(mutable), x.caller);

        ProtosFutureValue future =
                reference(group).beginRequestForRuntime(
                        x.sender.reference(), "echo", snapshot, x.caller);
        mutable.assignLocalSlot("value", new ProtosStringValue("after"));
        dispatchAccepted(target);

        assertEquals(ProtosFutureValue.State.RESOLVED, future.state());
        ProtosObjectValue reply = assertInstanceOf(ProtosObjectValue.class, future.resolvedValue().orElseThrow());
        assertEquals("before", ((ProtosStringValue) reply.readLocalSlot("value").orElseThrow()).value());

        ProtosActorGroupRuntime lostGroup = new ProtosActorGroupRuntime();
        ProtosActor lost = ready(x.prelude, echoBehavior());
        lostGroup.addMemberForRuntime(lost);
        ProtosFutureValue lostFuture =
                reference(lostGroup).beginRequestForRuntime(
                        x.sender.reference(), "echo", List.of(new ProtosStringValue("x")), x.caller);
        assertEquals(1, lost.mailboxForRuntime().size());
        lost.requestTerminationForRuntime();

        assertEquals(ProtosFutureValue.State.FAILED, lostFuture.state());
        assertSame(
                x.prelude.standardErrorPrototype("RequestOutcomeUncertain"),
                lostFuture.failedError().orElseThrow().parent().orElseThrow());
    }

    @Test
    void groupSendCancellationAndRetryReuseTheOriginalLogicalSnapshot() throws Exception {
        Fixture x = fixture();
        ProtosActorGroupRuntime group = new ProtosActorGroupRuntime();
        ProtosGroupRefValue reference = reference(group);
        ProtosObjectValue mutable = new ProtosObjectValue(ProtosObjectValue.rootObject());
        mutable.createLocalSlot("value", new ProtosStringValue("snap"));
        List<Object> snapshot = ProtosActorValueTransfer.snapshotArguments(List.of(mutable), x.caller);
        ProtosGroupSendOperationValue operation =
                reference.beginSendForRuntime(
                        sendOperationPrototype(), x.sender.reference(), "echo", snapshot);

        assertTrue(operation.cancelBeforeAcceptance());
        assertEquals(ProtosGroupSendOperationValue.State.CANCELLED_BEFORE_ACCEPTANCE, operation.stateForTesting());
        assertNull(operation.retryAfterFailure());

        ProtosActorGroupRuntime terminated = new ProtosActorGroupRuntime();
        ProtosGroupRefValue terminatedRef = reference(terminated);
        terminated.markTerminatedForRuntime();
        ProtosGroupSendOperationValue failed =
                terminatedRef.beginSendForRuntime(
                        sendOperationPrototype(), x.sender.reference(), "echo", snapshot);
        assertEquals(ProtosGroupSendOperationValue.State.FAILED_BEFORE_ACCEPTANCE, failed.stateForTesting());
        ProtosGroupSendOperationValue retry = failed.retryAfterFailure();
        assertNotNull(retry);
        assertNotSame(failed, retry);
        assertSame(snapshot.get(0), failed.snapshotForTesting().get(0));
        assertSame(snapshot.get(0), retry.snapshotForTesting().get(0));
        assertEquals(ProtosGroupSendOperationValue.State.FAILED_BEFORE_ACCEPTANCE, retry.stateForTesting());
    }

    @Test
    void groupSendOperationIsLocalAcrossActorAndPBoundaries() throws Exception {
        Fixture x = fixture();
        ProtosActorGroupRuntime group = new ProtosActorGroupRuntime();
        ProtosGroupSendOperationValue operation =
                reference(group).beginSendForRuntime(
                        sendOperationPrototype(), x.sender.reference(), "ping", List.of());

        ProtosSignalException actorFailure =
                assertThrows(
                        ProtosSignalException.class,
                        () -> ProtosActorValueTransfer.snapshotValue(operation, x.caller));
        assertSame(
                x.prelude.standardErrorPrototype("NonTransferableValue"),
                actorFailure.error().parent().orElseThrow());

        Class<?> transfer =
                Class.forName("com.guillermomolina.protos.execution.ProtosParallelRuntime$Transfer");
        Method copy =
                transfer.getDeclaredMethod(
                        "copy", Object.class, ProtosActivation.class, IdentityHashMap.class);
        copy.setAccessible(true);
        InvocationTargetException pFailure =
                assertThrows(
                        InvocationTargetException.class,
                        () -> copy.invoke(null, operation, x.caller, new IdentityHashMap<>()));
        assertEquals("NonParallel", pFailure.getCause().getClass().getSimpleName());
    }

    private static Fixture fixture() throws Exception {
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
        ProtosActor sender = ready(prelude, pingBehavior(new AtomicInteger()));
        ProtosActivation caller =
                prelude.newModuleActivation(
                        sender.moduleState(),
                        new ProtosModuleKey("sender"),
                        prelude.newExecutionContext(),
                        sender.executionDomain());
        return new Fixture(prelude, sender, caller);
    }

    private static ProtosActor ready(ProtosPrelude prelude, ProtosObjectValue behavior) {
        ProtosActor actor = new ProtosActor(actorRefPrototype());
        ready(actor, prelude, behavior);
        return actor;
    }

    private static ProtosActor readyWithCapacity(
            ProtosPrelude prelude, ProtosObjectValue behavior, int capacity) {
        ProtosActor actor =
                new ProtosActor(
                        actorRefPrototype(),
                        new ProtosActorExecutionDomain(),
                        new ProtosActorModuleState(),
                        capacity);
        ready(actor, prelude, behavior);
        return actor;
    }

    private static void ready(ProtosActor actor, ProtosPrelude prelude, ProtosObjectValue behavior) {
        actor.bindMessageEnvironmentForRuntime(prelude, new ProtosModuleKey("target"));
        assertTrue(actor.completeInitialization(behavior));
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
                UUID.fromString("99999999-9999-9999-9999-999999999999"));
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

    private record Fixture(ProtosPrelude prelude, ProtosActor sender, ProtosActivation caller) {}
}
