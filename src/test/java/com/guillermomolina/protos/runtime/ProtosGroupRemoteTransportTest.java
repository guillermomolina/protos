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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Deterministic conformance for Group routing across the I011-19 Actor transport boundary. */
final class ProtosGroupRemoteTransportTest {
    private static final Path CORE = Path.of("protos", "lib", "core");

    @Test
    void knownRemotePreacceptFailureReroutesSendToAnotherEligibleMember() throws Exception {
        Fixture x = fixture();
        AtomicInteger calls = new AtomicInteger();
        ProtosActorGroupRuntime group = new ProtosActorGroupRuntime();
        FakeRoute route = new FakeRoute();
        ProtosActorRefValue remote = routedReference(route);
        ProtosActor local = ready(x.prelude, pingBehavior(calls));
        assertTrue(group.addRemoteReadyMemberForRuntime(remote));
        assertTrue(group.addMemberForRuntime(local));

        ProtosGroupSendOperationValue operation =
                reference(group).beginSendForRuntime(
                        sendOperationPrototype(), x.sender.reference(), "ping", List.of());
        assertSame(remote, operation.selectedMemberForTesting());
        assertEquals(ProtosActorTransportRoute.DeliveryState.PENDING, operation.transportStateForTesting());
        assertEquals(0, local.mailboxForRuntime().size());

        route.failLastBeforeAcceptance();

        assertSame(local, operation.selectedActorForTesting());
        assertEquals(ProtosActorDeliveryAttempt.State.ACCEPTED, operation.deliveryStateForTesting());
        dispatchAccepted(local);
        assertEquals(1, calls.get());
        assertEquals(ProtosGroupSendOperationValue.State.COMPLETED, operation.stateForTesting());
    }

    @Test
    void remoteAcceptanceUncertaintyNeverTransparentlyReroutesButExplicitRetryMay() throws Exception {
        Fixture x = fixture();
        AtomicInteger calls = new AtomicInteger();
        ProtosActorGroupRuntime group = new ProtosActorGroupRuntime();
        FakeRoute route = new FakeRoute();
        ProtosActorRefValue remote = routedReference(route);
        ProtosActor local = ready(x.prelude, pingBehavior(calls));
        assertTrue(group.addRemoteReadyMemberForRuntime(remote));
        assertTrue(group.addMemberForRuntime(local));
        ProtosObjectValue payload = new ProtosObjectValue(ProtosObjectValue.rootObject());
        List<Object> snapshot = List.of(payload);

        ProtosGroupSendOperationValue operation =
                reference(group).beginSendForRuntime(
                        sendOperationPrototype(), x.sender.reference(), "ping", snapshot);
        route.markLastUncertain();

        assertEquals(ProtosGroupSendOperationValue.State.ACCEPTANCE_UNCERTAIN, operation.stateForTesting());
        assertEquals(0, local.mailboxForRuntime().size());
        ProtosGroupSendOperationValue retry = operation.retryAfterFailure();
        assertNotNull(retry);
        assertNotSame(operation, retry);
        assertSame(payload, retry.snapshotForTesting().get(0));
        assertSame(local, retry.selectedActorForTesting());
        assertEquals(ProtosActorDeliveryAttempt.State.ACCEPTED, retry.deliveryStateForTesting());

        dispatchAccepted(local);
        assertEquals(1, calls.get());
    }

    @Test
    void knownRemoteRequestNonacceptanceReroutesAndNormalReplyCompletes() throws Exception {
        Fixture x = fixture();
        ProtosActorGroupRuntime group = new ProtosActorGroupRuntime();
        FakeRoute route = new FakeRoute();
        ProtosActorRefValue remote = routedReference(route);
        ProtosActor local = ready(x.prelude, echoBehavior());
        assertTrue(group.addRemoteReadyMemberForRuntime(remote));
        assertTrue(group.addMemberForRuntime(local));

        ProtosFutureValue future =
                reference(group).beginRequestForRuntime(
                        x.sender.reference(),
                        "echo",
                        List.of(new ProtosStringValue("ok")),
                        x.caller);
        route.failLastBeforeAcceptance();
        assertEquals(1, local.mailboxForRuntime().size());
        dispatchAccepted(local);

        assertEquals(ProtosFutureValue.State.RESOLVED, future.state());
        assertEquals(
                "ok",
                assertInstanceOf(ProtosStringValue.class, future.resolvedValue().orElseThrow()).value());
    }

    @Test
    void remoteGroupRequestAcceptanceUncertaintyDoesNotFallbackAndFailsUncertain() throws Exception {
        Fixture x = fixture();
        AtomicInteger fallbackCalls = new AtomicInteger();
        ProtosActorGroupRuntime group = new ProtosActorGroupRuntime();
        FakeRoute route = new FakeRoute();
        ProtosActorRefValue remote = routedReference(route);
        ProtosActor local = ready(x.prelude, pingBehavior(fallbackCalls));
        assertTrue(group.addRemoteReadyMemberForRuntime(remote));
        assertTrue(group.addMemberForRuntime(local));

        ProtosFutureValue future =
                reference(group).beginRequestForRuntime(
                        x.sender.reference(), "ping", List.of(), x.caller);
        route.markLastUncertain();

        assertEquals(ProtosFutureValue.State.FAILED, future.state());
        assertSame(
                x.prelude.standardErrorPrototype("RequestOutcomeUncertain"),
                future.failedError().orElseThrow().parent().orElseThrow());
        assertEquals(0, local.mailboxForRuntime().size());
        assertEquals(0, fallbackCalls.get());
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
        actor.bindMessageEnvironmentForRuntime(prelude, new ProtosModuleKey("target"));
        assertTrue(actor.completeInitialization(behavior));
        return actor;
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
                ProtosClosureValue.nativeClosure((activation, arguments) -> arguments.get(0)));
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

    private static ProtosActorRefValue routedReference(FakeRoute route) {
        return route.targetActor.reference().withCommunicationRouteForRuntime(route);
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

    private static final class FakeRoute implements ProtosActorTransportRoute {
        private final ProtosActor targetActor;
        private final long targetIdentity;
        private final List<List<Object>> sendSnapshots = new ArrayList<>();
        private DeliveryImpl lastDelivery;
        private RequestObserver lastRequestObserver;

        private FakeRoute() {
            this.targetActor = new ProtosActor(actorRefPrototype());
            this.targetIdentity = targetActor.incarnationIdentityForRuntime();
        }

        @Override
        public long targetIncarnationIdentityForRuntime() {
            return targetIdentity;
        }

        @Override
        public Delivery beginSend(
                ProtosActorRefValue sender, String selector, List<Object> logicalSnapshot) {
            assertNotNull(sender);
            assertNotNull(selector);
            sendSnapshots.add(logicalSnapshot);
            lastDelivery = new DeliveryImpl();
            lastRequestObserver = null;
            return lastDelivery;
        }

        @Override
        public Delivery beginRequest(
                ProtosActorRefValue sender,
                String selector,
                List<Object> logicalSnapshot,
                RequestObserver observer) {
            assertNotNull(sender);
            assertNotNull(selector);
            assertNotNull(logicalSnapshot);
            lastDelivery = new DeliveryImpl();
            lastRequestObserver = Objects.requireNonNull(observer, "observer");
            return lastDelivery;
        }

        private void failLastBeforeAcceptance() {
            DeliveryImpl delivery = lastDelivery();
            delivery.setState(DeliveryState.FAILED_BEFORE_ACCEPTANCE);
            RequestObserver observer = lastRequestObserver;
            if (observer != null) {
                observer.failedBeforeAcceptance();
            }
        }

        private void markLastUncertain() {
            DeliveryImpl delivery = lastDelivery();
            delivery.setState(DeliveryState.ACCEPTANCE_UNCERTAIN);
            RequestObserver observer = lastRequestObserver;
            if (observer != null) {
                observer.outcomeUncertain();
            }
        }

        private DeliveryImpl lastDelivery() {
            assertNotNull(lastDelivery);
            return lastDelivery;
        }
    }

    private static final class DeliveryImpl implements ProtosActorTransportRoute.Delivery {
        private final List<ProtosActorTransportRoute.DeliveryObserver> observers = new ArrayList<>();
        private ProtosActorTransportRoute.DeliveryState state =
                ProtosActorTransportRoute.DeliveryState.PENDING;

        @Override
        public synchronized ProtosActorTransportRoute.DeliveryState stateForRuntime() {
            return state;
        }

        @Override
        public boolean cancelBeforeAcceptance() {
            List<ProtosActorTransportRoute.DeliveryObserver> notify;
            synchronized (this) {
                if (state != ProtosActorTransportRoute.DeliveryState.PENDING) {
                    return false;
                }
                state = ProtosActorTransportRoute.DeliveryState.CANCELLED_BEFORE_ACCEPTANCE;
                notify = List.copyOf(observers);
            }
            for (ProtosActorTransportRoute.DeliveryObserver observer : notify) {
                observer.stateChanged(ProtosActorTransportRoute.DeliveryState.CANCELLED_BEFORE_ACCEPTANCE);
            }
            return true;
        }

        @Override
        public void observeForRuntime(ProtosActorTransportRoute.DeliveryObserver observer) {
            ProtosActorTransportRoute.DeliveryState current;
            synchronized (this) {
                observers.add(Objects.requireNonNull(observer, "observer"));
                current = state;
            }
            observer.stateChanged(current);
        }

        private void setState(ProtosActorTransportRoute.DeliveryState next) {
            List<ProtosActorTransportRoute.DeliveryObserver> notify;
            synchronized (this) {
                state = Objects.requireNonNull(next, "next");
                notify = List.copyOf(observers);
            }
            for (ProtosActorTransportRoute.DeliveryObserver observer : notify) {
                observer.stateChanged(next);
            }
        }
    }
}
