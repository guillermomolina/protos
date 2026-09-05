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
import org.junit.jupiter.api.Test;

/** Deterministic conformance for the host-neutral ActorRef transport/acceptance boundary. */
final class ProtosActorRemoteTransportTest {
    private static final Path CORE = Path.of("protos", "lib", "core");

    @Test
    void transportMaterializationPreservesActorRefSemanticIdentityAcrossActorTransfer()
            throws Exception {
        Fixture x = fixture();
        ProtosActor target = new ProtosActor(actorRefPrototype());
        FakeRoute route = new FakeRoute(target.incarnationIdentityForRuntime());
        ProtosActorRefValue routed = target.reference().withCommunicationRouteForRuntime(route);
        ProtosActorRefValue transferred =
                assertInstanceOf(
                        ProtosActorRefValue.class,
                        ProtosActorValueTransfer.snapshotValue(routed, x.activation));

        assertNotSame(target.reference(), routed);
        assertNotSame(routed, transferred);
        assertTrue(ProtosIdentity.identical(target.reference(), routed));
        assertTrue(ProtosIdentity.identical(routed, transferred));
        assertEquals(
                ProtosIdentity.identityHash(target.reference()),
                ProtosIdentity.identityHash(transferred));
        assertSame(route, transferred.communicationRouteForRuntime().orElseThrow());
        assertSame(target, transferred.localActorForRuntime());
    }

    @Test
    void remoteSendCancellationAndUncertainRetryReuseOriginalSnapshot() {
        ProtosActor sender = new ProtosActor(actorRefPrototype());
        ProtosActor target = new ProtosActor(actorRefPrototype());
        FakeRoute route = new FakeRoute(target.incarnationIdentityForRuntime());
        ProtosActorRefValue routed = target.reference().withCommunicationRouteForRuntime(route);
        ProtosObjectValue payload = new ProtosObjectValue(ProtosObjectValue.rootObject());
        List<Object> snapshot = List.of(payload);

        ProtosSendOperationValue cancelled =
                ProtosSendOperationValue.begin(
                        sendOperationPrototype(), routed, sender.reference(), "work", snapshot);
        assertEquals(ProtosActorTransportRoute.DeliveryState.PENDING, cancelled.transportStateForTesting());
        assertTrue(cancelled.cancelBeforeAcceptance());
        assertEquals(
                ProtosActorTransportRoute.DeliveryState.CANCELLED_BEFORE_ACCEPTANCE,
                cancelled.transportStateForTesting());
        assertNull(cancelled.retryAfterFailure());

        ProtosSendOperationValue uncertain =
                ProtosSendOperationValue.begin(
                        sendOperationPrototype(), routed, sender.reference(), "work", snapshot);
        route.lastDelivery().setState(ProtosActorTransportRoute.DeliveryState.ACCEPTANCE_UNCERTAIN);
        assertFalse(uncertain.cancelBeforeAcceptance());
        ProtosSendOperationValue retry = uncertain.retryAfterFailure();
        assertNotNull(retry);
        assertNotSame(uncertain, retry);
        assertEquals(3, route.sendCount);
        assertSame(payload, route.sendSnapshots.get(1).get(0));
        assertSame(payload, route.sendSnapshots.get(2).get(0));
    }

    @Test
    void remoteRequestTransportUncertaintyFailsWithRequestOutcomeUncertain() throws Exception {
        Fixture x = fixture();
        ProtosActor target = new ProtosActor(actorRefPrototype());
        FakeRoute route = new FakeRoute(target.incarnationIdentityForRuntime());
        ProtosActorRefValue routed = target.reference().withCommunicationRouteForRuntime(route);

        ProtosFutureValue future =
                ProtosActorRequest.begin(
                        routed,
                        x.caller.reference(),
                        "work",
                        List.of(new ProtosStringValue("payload")),
                        x.activation);
        route.markLastRequestUncertainAndNotify();

        assertEquals(ProtosFutureValue.State.FAILED, future.state());
        assertSame(
                x.prelude.standardErrorPrototype("RequestOutcomeUncertain"),
                future.failedError().orElseThrow().parent().orElseThrow());
    }

    @Test
    void cancellingRequestWhenAcceptanceIsUnknownIsOutcomeUncertain() throws Exception {
        Fixture x = fixture();
        ProtosActor target = new ProtosActor(actorRefPrototype());
        FakeRoute route = new FakeRoute(target.incarnationIdentityForRuntime());
        ProtosActorRefValue routed = target.reference().withCommunicationRouteForRuntime(route);

        ProtosFutureValue future =
                ProtosActorRequest.begin(routed, x.caller.reference(), "work", List.of(), x.activation);
        route.lastDelivery().setState(ProtosActorTransportRoute.DeliveryState.ACCEPTANCE_UNCERTAIN);

        assertTrue(future.cancelRequest());
        assertEquals(ProtosFutureValue.State.FAILED, future.state());
        assertSame(
                x.prelude.standardErrorPrototype("RequestOutcomeUncertain"),
                future.failedError().orElseThrow().parent().orElseThrow());
    }

    @Test
    void remoteNormalReplyIsResnapshottedBeforeCallerResolution() throws Exception {
        Fixture x = fixture();
        ProtosActor target = new ProtosActor(actorRefPrototype());
        FakeRoute route = new FakeRoute(target.incarnationIdentityForRuntime());
        ProtosActorRefValue routed = target.reference().withCommunicationRouteForRuntime(route);
        ProtosObjectValue reply = new ProtosObjectValue(ProtosObjectValue.rootObject());
        reply.createLocalSlot("value", new ProtosStringValue("before"));

        ProtosFutureValue future =
                ProtosActorRequest.begin(routed, x.caller.reference(), "work", List.of(), x.activation);
        route.replyToLastRequest(reply);
        reply.assignLocalSlot("value", new ProtosStringValue("after"));

        assertEquals(ProtosFutureValue.State.RESOLVED, future.state());
        ProtosObjectValue received =
                assertInstanceOf(ProtosObjectValue.class, future.resolvedValue().orElseThrow());
        assertNotSame(reply, received);
        assertEquals(
                "before",
                assertInstanceOf(
                                ProtosStringValue.class,
                                received.readLocalSlot("value").orElseThrow())
                        .value());
    }

    private static Fixture fixture() throws Exception {
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
        ProtosActor caller = new ProtosActor(actorRefPrototype());
        ProtosModuleKey moduleKey = new ProtosModuleKey("caller");
        caller.bindMessageEnvironmentForRuntime(prelude, moduleKey);
        caller.completeInitialization(new ProtosObjectValue(ProtosObjectValue.rootObject()));
        ProtosActivation activation =
                prelude.newModuleActivation(
                        caller.moduleState(),
                        moduleKey,
                        prelude.newExecutionContext(),
                        caller.executionDomain());
        return new Fixture(prelude, caller, activation);
    }

    private static ProtosObjectValue actorRefPrototype() {
        return new ProtosObjectValue(ProtosObjectValue.rootObject()).freeze();
    }

    private static ProtosObjectValue sendOperationPrototype() {
        return new ProtosObjectValue(ProtosObjectValue.rootObject()).freeze();
    }

    private record Fixture(ProtosPrelude prelude, ProtosActor caller, ProtosActivation activation) {}

    private static final class FakeRoute implements ProtosActorTransportRoute {
        private final long targetIdentity;
        private final List<List<Object>> sendSnapshots = new ArrayList<>();
        private int sendCount;
        private DeliveryImpl lastDelivery;
        private RequestObserver lastRequestObserver;

        private FakeRoute(long targetIdentity) {
            this.targetIdentity = targetIdentity;
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
            sendCount++;
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
            lastRequestObserver = observer;
            return lastDelivery;
        }

        private DeliveryImpl lastDelivery() {
            assertNotNull(lastDelivery);
            return lastDelivery;
        }

        private RequestObserver lastRequestObserver() {
            assertNotNull(lastRequestObserver);
            return lastRequestObserver;
        }

        private void markLastRequestUncertainAndNotify() {
            lastDelivery().setState(DeliveryState.ACCEPTANCE_UNCERTAIN);
            lastRequestObserver().outcomeUncertain();
        }

        private void replyToLastRequest(Object reply) {
            lastDelivery().setState(DeliveryState.COMPLETED);
            lastRequestObserver().reply(reply);
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
                observers.add(java.util.Objects.requireNonNull(observer, "observer"));
                current = state;
            }
            observer.stateChanged(current);
        }

        private void setState(ProtosActorTransportRoute.DeliveryState next) {
            List<ProtosActorTransportRoute.DeliveryObserver> notify;
            synchronized (this) {
                state = java.util.Objects.requireNonNull(next, "next");
                notify = List.copyOf(observers);
            }
            for (ProtosActorTransportRoute.DeliveryObserver observer : notify) {
                observer.stateChanged(next);
            }
        }
    }
}
