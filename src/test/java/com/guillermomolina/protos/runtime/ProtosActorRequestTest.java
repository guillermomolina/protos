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

import com.guillermomolina.protos.execution.ProtosInvocation;
import com.guillermomolina.protos.execution.ProtosModuleResolver;
import com.guillermomolina.protos.execution.ProtosModuleRuntime;
import com.guillermomolina.protos.execution.ProtosStandardActorProtocol;
import com.guillermomolina.protos.execution.ProtosCoreBootstrap;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class ProtosActorRequestTest {
    private static final Path CORE = Path.of("protos", "lib", "core");

    @Test
    void requestReturnsFreshCallerDomainFuturesAndExtendsOnlyActorRefSurface() throws Exception {
        Fixture fixture = fixture();
        ProtosActor target = fixture.readyTarget(2, echoBehavior());

        ProtosFutureValue first = fixture.request(target, "echo", new ProtosStringValue("one"));
        ProtosFutureValue second = fixture.request(target, "echo", new ProtosStringValue("two"));

        assertNotSame(first, second);
        assertSame(fixture.creator.executionDomain(), first.domain());
        assertSame(fixture.creator.executionDomain(), second.domain());

        ProtosObjectValue actorRefPrototype =
                assertInstanceOf(
                        ProtosObjectValue.class,
                        target.reference().representedDelegationParent(fixture.prelude));
        assertEquals(Set.of("send", "request"), actorRefPrototype.localSlotsSnapshot().keySet());
        assertTrue(actorRefPrototype.isFrozen());
    }

    @Test
    void requestSnapshotsAtInvocationAndTransfersReplyBackAcrossActorBoundary() throws Exception {
        Fixture fixture = fixture();
        ProtosActor target = fixture.readyTarget(1, echoBehavior());
        ProtosObjectValue source = new ProtosObjectValue(ProtosObjectValue.rootObject());
        source.createLocalSlot("value", new ProtosStringValue("before"));

        ProtosFutureValue future = fixture.request(target, "echo", source);
        source.assignLocalSlot("value", new ProtosStringValue("after"));

        fixture.carriers.runNext();

        assertEquals(ProtosFutureValue.State.RESOLVED, future.state());
        ProtosObjectValue reply =
                assertInstanceOf(ProtosObjectValue.class, future.resolvedValue().orElseThrow());
        assertNotSame(source, reply);
        ProtosStringValue value =
                assertInstanceOf(
                        ProtosStringValue.class,
                        reply.readLocalSlot("value").orElseThrow());
        assertEquals("before", value.value());
        assertEquals(ProtosActor.LifecycleState.READY, target.lifecycleState());
    }

    @Test
    void destinationLocalFutureReplyFailsNonTransferableWithoutKillingActor() throws Exception {
        Fixture fixture = fixture();
        ProtosObjectValue behavior = new ProtosObjectValue(ProtosObjectValue.rootObject());
        behavior.createLocalSlot(
                "futureReply",
                ProtosClosureValue.nativeClosure(
                        (activation, arguments) ->
                                new ProtosFutureValue(
                                        fixture.prelude.futurePrototype(),
                                        activation.executionDomain())));
        ProtosActor target = fixture.readyTarget(1, behavior);

        ProtosFutureValue future = fixture.request(target, "futureReply");
        fixture.carriers.runNext();

        assertEquals(ProtosFutureValue.State.FAILED, future.state());
        assertSame(
                fixture.prelude.standardErrorPrototype("NonTransferableValue"),
                future.failedError().orElseThrow().parent().orElseThrow());
        assertEquals(ProtosActor.LifecycleState.READY, target.lifecycleState());
    }

    @Test
    void unhandledAcceptedHandlerFailureBecomesRequestOutcomeUncertain() throws Exception {
        Fixture fixture = fixture();
        ProtosObjectValue behavior = new ProtosObjectValue(ProtosObjectValue.rootObject());
        behavior.createLocalSlot(
                "boom",
                ProtosClosureValue.nativeClosure(
                        (activation, arguments) -> {
                            throw new ProtosSignalException(ProtosCoreErrors.newError(activation));
                        }));
        ProtosActor target = fixture.readyTarget(1, behavior);

        ProtosFutureValue future = fixture.request(target, "boom");
        fixture.carriers.runNext();

        assertEquals(ProtosFutureValue.State.FAILED, future.state());
        assertSame(
                fixture.prelude.standardErrorPrototype("RequestOutcomeUncertain"),
                future.failedError().orElseThrow().parent().orElseThrow());
        assertEquals(ProtosActor.LifecycleState.TERMINATED, target.lifecycleState());
    }

    @Test
    void acceptedUndispatchedTerminationIsUncertainAndMakesSendRetryable() throws Exception {
        Fixture fixture = fixture();
        ProtosActor target = fixture.readyTarget(2, echoBehavior());

        ProtosFutureValue request =
                fixture.request(target, "echo", new ProtosStringValue("request"));
        ProtosSendOperationValue send =
                fixture.send(target, "echo", new ProtosStringValue("send"));

        assertEquals(ProtosFutureValue.State.PENDING, request.state());
        assertEquals(ProtosActorDeliveryAttempt.State.ACCEPTED, send.deliveryStateForTesting());

        assertTrue(target.beginTermination());
        assertTrue(target.markTerminated());

        assertEquals(ProtosFutureValue.State.FAILED, request.state());
        assertSame(
                fixture.prelude.standardErrorPrototype("RequestOutcomeUncertain"),
                request.failedError().orElseThrow().parent().orElseThrow());
        assertEquals(
                ProtosActorDeliveryAttempt.State.FAILED_AFTER_ACCEPTANCE,
                send.deliveryStateForTesting());
        ProtosSendOperationValue retry =
                assertInstanceOf(
                        ProtosSendOperationValue.class,
                        ProtosInvocation.invokeMessage(
                                send, "retry", List.of(), fixture.creatorActivation));
        assertNotSame(send, retry);
        assertEquals(
                ProtosActorDeliveryAttempt.State.FAILED_BEFORE_ACCEPTANCE,
                retry.deliveryStateForTesting());
    }

    @Test
    void knownPreAcceptanceFailureIsOrdinaryFailedFutureNotUncertainty() throws Exception {
        Fixture fixture = fixture();
        ProtosActor target = fixture.readyTarget(1, echoBehavior());
        assertTrue(target.beginTermination());
        assertTrue(target.markTerminated());

        ProtosFutureValue future = fixture.request(target, "echo", new ProtosStringValue("x"));

        assertEquals(ProtosFutureValue.State.FAILED, future.state());
        ProtosObjectValue failure = future.failedError().orElseThrow();
        assertSame(fixture.prelude.errorPrototype(), failure.parent().orElseThrow());
        assertNotSame(
                fixture.prelude.standardErrorPrototype("RequestOutcomeUncertain"),
                failure.parent().orElseThrow());
    }

    @Test
    void cancellationBeforeAcceptanceCancelsWhileAcceptedCancellationCannotUnsend() throws Exception {
        Fixture fixture = fixture();
        ProtosActor blocked = fixture.readyTarget(1, echoBehavior());
        fixture.send(blocked, "echo", new ProtosStringValue("occupy"));
        ProtosFutureValue pending =
                fixture.request(blocked, "echo", new ProtosStringValue("pending"));

        assertEquals(1, blocked.deliveryAdmissionForRuntime().pendingCountForTesting());
        assertTrue(pending.cancelRequest());
        assertEquals(ProtosFutureValue.State.CANCELLED, pending.state());
        assertEquals(0, blocked.deliveryAdmissionForRuntime().pendingCountForTesting());

        AtomicInteger calls = new AtomicInteger();
        ProtosObjectValue behavior = new ProtosObjectValue(ProtosObjectValue.rootObject());
        behavior.createLocalSlot(
                "count",
                ProtosClosureValue.nativeClosure(
                        (activation, arguments) -> {
                            calls.incrementAndGet();
                            return new ProtosStringValue("reply");
                        }));
        // Use an independent carrier queue for the post-acceptance half. The blocked Actor above
        // intentionally left its accepted occupancy scheduled but undispatched; sharing that manual
        // executor would make a single runNext() select the unrelated worker instead.
        Fixture acceptedFixture = fixture();
        ProtosActor accepted = acceptedFixture.readyTarget(1, behavior);
        ProtosFutureValue acceptedFuture = acceptedFixture.request(accepted, "count");

        assertTrue(acceptedFuture.cancelRequest());
        assertEquals(ProtosFutureValue.State.CANCELLED, acceptedFuture.state());
        acceptedFixture.carriers.runNext();

        assertEquals(1, calls.get());
        assertEquals(ProtosFutureValue.State.CANCELLED, acceptedFuture.state());
    }

    @Test
    void requestValidatesSelectorAndCompleteSnapshotBeforeAdmission() throws Exception {
        Fixture fixture = fixture();
        ProtosActor target = fixture.readyTarget(1, echoBehavior());
        ProtosObjectValue stringLike = new ProtosObjectValue(fixture.prelude.stringPrototype());

        assertThrows(
                ProtosSignalException.class,
                () ->
                        ProtosInvocation.invokeMessage(
                                target.reference(),
                                "request",
                                List.of(stringLike),
                                fixture.creatorActivation));
        assertEquals(0, target.deliveryAdmissionForRuntime().pendingCountForTesting());
        assertEquals(0, target.mailboxForRuntime().size());

        ProtosClosureValue closure =
                ProtosClosureValue.nativeClosure(
                        (activation, arguments) -> ProtosNullValue.INSTANCE);
        ProtosSignalException nonTransferable =
                assertThrows(
                        ProtosSignalException.class,
                        () ->
                                ProtosInvocation.invokeMessage(
                                        target.reference(),
                                        "request",
                                        List.of(new ProtosStringValue("echo"), closure),
                                        fixture.creatorActivation));
        assertSame(
                fixture.prelude.standardErrorPrototype("NonTransferableValue"),
                nonTransferable.error().parent().orElseThrow());
        assertEquals(0, target.deliveryAdmissionForRuntime().pendingCountForTesting());
        assertEquals(0, target.mailboxForRuntime().size());
    }

    private static ProtosObjectValue echoBehavior() {
        ProtosObjectValue behavior = new ProtosObjectValue(ProtosObjectValue.rootObject());
        behavior.createLocalSlot(
                "echo",
                ProtosClosureValue.nativeClosure(
                        (activation, arguments) ->
                                arguments.isEmpty()
                                        ? ProtosNullValue.INSTANCE
                                        : arguments.get(0)));
        return behavior;
    }

    private static Fixture fixture() throws Exception {
        ProtosModuleResolver resolver =
                new ProtosModuleResolver() {
                    @Override
                    public ProtosModuleKey resolve(
                            String exactSpecifier, Optional<ProtosModuleKey> importingModule) {
                        return new ProtosModuleKey("canonical:" + exactSpecifier);
                    }

                    @Override
                    public String loadSource(ProtosModuleKey key) {
                        return "boot: () => {\n    {}\n}";
                    }
                };
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE, resolver);
        ManualExecutor bootstrapExecutor = new ManualExecutor();
        ProtosStandardActorProtocol protocol =
                new ProtosStandardActorProtocol(
                        new ProtosModuleRuntime(resolver), bootstrapExecutor);
        ProtosObjectValue actorObject =
                protocol.installActorObject(
                        new ProtosObjectValue(ProtosObjectValue.rootObject()));
        ProtosActor creator =
                new ProtosActor(new ProtosObjectValue(ProtosObjectValue.rootObject()).freeze());
        ProtosActivation creatorActivation =
                prelude.newModuleActivation(
                        creator.moduleState(),
                        new ProtosModuleKey("creator"),
                        prelude.newExecutionContext(),
                        creator.executionDomain());

        ProtosActorRefValue seed =
                assertInstanceOf(
                        ProtosActorRefValue.class,
                        ProtosInvocation.invokeMessage(
                                actorObject,
                                "spawn",
                                List.of(
                                        new ProtosStringValue("seed"),
                                        new ProtosStringValue("boot")),
                                creatorActivation));
        ProtosObjectValue refPrototype =
                assertInstanceOf(
                        ProtosObjectValue.class,
                        seed.representedDelegationParent(prelude));
        bootstrapExecutor.runNext();

        return new Fixture(
                prelude,
                creator,
                creatorActivation,
                refPrototype,
                new ManualExecutor());
    }

    private static final class Fixture {
        private final ProtosPrelude prelude;
        private final ProtosActor creator;
        private final ProtosActivation creatorActivation;
        private final ProtosObjectValue actorRefPrototype;
        private final ManualExecutor carriers;

        private Fixture(
                ProtosPrelude prelude,
                ProtosActor creator,
                ProtosActivation creatorActivation,
                ProtosObjectValue actorRefPrototype,
                ManualExecutor carriers) {
            this.prelude = prelude;
            this.creator = creator;
            this.creatorActivation = creatorActivation;
            this.actorRefPrototype = actorRefPrototype;
            this.carriers = carriers;
        }

        private ProtosActor readyTarget(int capacity, ProtosObjectValue behavior) {
            ProtosActor actor =
                    new ProtosActor(
                            actorRefPrototype,
                            new ProtosActorExecutionDomain(),
                            new ProtosActorModuleState(),
                            capacity);
            actor.bindMessageEnvironmentForRuntime(
                    prelude, new ProtosModuleKey("target"));
            actor.completeInitialization(behavior);
            new ProtosActorScheduler(carriers, 1).attach(actor);
            return actor;
        }

        private ProtosFutureValue request(
                ProtosActor target, String selector, Object... arguments) {
            ArrayList<Object> supplied = new ArrayList<>();
            supplied.add(new ProtosStringValue(selector));
            supplied.addAll(List.of(arguments));
            return assertInstanceOf(
                    ProtosFutureValue.class,
                    ProtosInvocation.invokeMessage(
                            target.reference(), "request", supplied, creatorActivation));
        }

        private ProtosSendOperationValue send(
                ProtosActor target, String selector, Object... arguments) {
            ArrayList<Object> supplied = new ArrayList<>();
            supplied.add(new ProtosStringValue(selector));
            supplied.addAll(List.of(arguments));
            return assertInstanceOf(
                    ProtosSendOperationValue.class,
                    ProtosInvocation.invokeMessage(
                            target.reference(), "send", supplied, creatorActivation));
        }
    }

    private static final class ManualExecutor implements Executor {
        private final ArrayDeque<Runnable> queued = new ArrayDeque<>();

        @Override
        public synchronized void execute(Runnable command) {
            queued.addLast(command);
        }

        void runNext() {
            Runnable command;
            synchronized (this) {
                command = queued.pollFirst();
            }
            assertNotNull(command, "expected scheduled Actor carrier");
            command.run();
        }
    }
}
