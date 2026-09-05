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
import com.guillermomolina.protos.execution.ProtosInvocation;
import com.guillermomolina.protos.execution.ProtosModuleResolver;
import com.guillermomolina.protos.execution.ProtosModuleRuntime;
import com.guillermomolina.protos.execution.ProtosStandardActorProtocol;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

final class ProtosActorSendOperationTest {
    private static final Path CORE = Path.of("protos", "lib", "core");

    @Test
    void actorRefSendAndSendOperationExposeOnlyTheNormativeLocalSurface() throws Exception {
        Fixture fixture = fixture();
        ProtosActor target = fixture.readyTarget(2, ordinaryBehavior());

        ProtosSendOperationValue operation = fixture.send(target, "noop");

        ProtosObjectValue actorRefPrototype =
                assertInstanceOf(
                        ProtosObjectValue.class,
                        target.reference().representedDelegationParent(fixture.prelude));
        assertEquals(Set.of("send", "request"), actorRefPrototype.localSlotsSnapshot().keySet());
        assertTrue(actorRefPrototype.isFrozen());
        assertTrue(ProtosValueLookup.lookup(target.reference(), "request", fixture.prelude).isPresent());

        ProtosObjectValue operationPrototype =
                assertInstanceOf(ProtosObjectValue.class, operation.parent().orElseThrow());
        assertEquals(Set.of("cancel", "retry"), operationPrototype.localSlotsSnapshot().keySet());
        assertTrue(operationPrototype.isFrozen());
        assertTrue(operation.localSlotsSnapshot().isEmpty());
        assertFalse(fixture.prelude.bindings().hasLocalSlot("SendOperation"));
    }

    @Test
    void sendValidatesExactStringAndWholeSnapshotBeforeAdmission() throws Exception {
        Fixture fixture = fixture();
        ProtosActor target = fixture.readyTarget(2, ordinaryBehavior());
        ProtosObjectValue stringLike = new ProtosObjectValue(fixture.prelude.stringPrototype());

        assertThrows(
                ProtosSignalException.class,
                () ->
                        ProtosInvocation.invokeMessage(
                                target.reference(), "send", List.of(stringLike), fixture.creatorActivation));
        assertEquals(0, target.deliveryAdmissionForRuntime().pendingCountForTesting());
        assertEquals(0, target.mailboxForRuntime().size());

        ProtosClosureValue closure =
                ProtosClosureValue.nativeClosure((activation, arguments) -> ProtosNullValue.INSTANCE);
        ProtosSignalException nonTransferable =
                assertThrows(
                        ProtosSignalException.class,
                        () ->
                                ProtosInvocation.invokeMessage(
                                        target.reference(),
                                        "send",
                                        List.of(new ProtosStringValue("noop"), closure),
                                        fixture.creatorActivation));
        assertSame(
                fixture.prelude.standardErrorPrototype("NonTransferableValue"),
                nonTransferable.error().parent().orElseThrow());
        assertEquals(0, target.deliveryAdmissionForRuntime().pendingCountForTesting());
        assertEquals(0, target.mailboxForRuntime().size());
    }

    @Test
    void sendSnapshotsBeforeBackpressureAndDispatchesAgainstStableBehavior() throws Exception {
        Fixture fixture = fixture();
        AtomicReference<Object> received = new AtomicReference<>();
        ProtosObjectValue behavior = new ProtosObjectValue(ProtosObjectValue.rootObject());
        behavior.createLocalSlot(
                "take",
                ProtosClosureValue.nativeClosure(
                        (activation, arguments) -> {
                            received.set(arguments.get(0));
                            return new ProtosStringValue("ignored result");
                        }));
        ProtosActor target = fixture.readyTarget(2, behavior);
        ProtosObjectValue source = new ProtosObjectValue(ProtosObjectValue.rootObject());
        source.createLocalSlot("value", new ProtosStringValue("before"));

        ProtosSendOperationValue operation = fixture.send(target, "take", source);
        source.assignLocalSlot("value", new ProtosStringValue("after"));
        assertEquals(ProtosActorDeliveryAttempt.State.ACCEPTED, operation.deliveryStateForTesting());

        fixture.carriers.runNext();

        assertEquals(ProtosActorDeliveryAttempt.State.COMPLETED, operation.deliveryStateForTesting());
        ProtosObjectValue copied = assertInstanceOf(ProtosObjectValue.class, received.get());
        assertNotSame(source, copied);
        ProtosStringValue value =
                assertInstanceOf(ProtosStringValue.class, copied.readLocalSlot("value").orElseThrow());
        assertEquals("before", value.value());
        assertSame(behavior, target.currentBehavior().orElseThrow());
    }

    @Test
    void cancellationReturnsTrueOnlyWhenItWinsBeforeAcceptance() throws Exception {
        Fixture fixture = fixture();
        ProtosActor target = fixture.readyTarget(1, ordinaryBehavior());
        ProtosSendOperationValue accepted = fixture.send(target, "noop");
        ProtosSendOperationValue pending = fixture.send(target, "noop");

        assertEquals(ProtosActorDeliveryAttempt.State.ACCEPTED, accepted.deliveryStateForTesting());
        assertEquals(ProtosActorDeliveryAttempt.State.PENDING, pending.deliveryStateForTesting());
        assertSame(
                ProtosBooleanValue.FALSE,
                ProtosInvocation.invokeMessage(
                        accepted, "cancel", List.of(), fixture.creatorActivation));
        assertSame(
                ProtosBooleanValue.TRUE,
                ProtosInvocation.invokeMessage(
                        pending, "cancel", List.of(), fixture.creatorActivation));
        assertEquals(
                ProtosActorDeliveryAttempt.State.CANCELLED_BEFORE_ACCEPTANCE,
                pending.deliveryStateForTesting());
        assertSame(
                ProtosBooleanValue.FALSE,
                ProtosInvocation.invokeMessage(
                        pending, "cancel", List.of(), fixture.creatorActivation));
    }

    @Test
    void retryRequiresFailureCreatesFreshIdentityAndReusesOriginalSnapshot() throws Exception {
        Fixture fixture = fixture();
        ProtosActor target = fixture.readyTarget(1, ordinaryBehavior());
        target.beginTermination();
        target.markTerminated();
        ProtosObjectValue source = new ProtosObjectValue(ProtosObjectValue.rootObject());
        source.createLocalSlot("value", new ProtosStringValue("frozen-at-send"));

        ProtosSendOperationValue failed = fixture.send(target, "noop", source);
        assertEquals(
                ProtosActorDeliveryAttempt.State.FAILED_BEFORE_ACCEPTANCE,
                failed.deliveryStateForTesting());
        source.assignLocalSlot("value", new ProtosStringValue("changed"));

        ProtosSendOperationValue retry =
                assertInstanceOf(
                        ProtosSendOperationValue.class,
                        ProtosInvocation.invokeMessage(
                                failed, "retry", List.of(), fixture.creatorActivation));
        assertNotSame(failed, retry);
        assertSame(failed.snapshotForTesting().get(0), retry.snapshotForTesting().get(0));
        assertEquals(
                ProtosActorDeliveryAttempt.State.FAILED_BEFORE_ACCEPTANCE,
                retry.deliveryStateForTesting());

        assertThrows(
                ProtosSignalException.class,
                () ->
                        ProtosInvocation.invokeMessage(
                                retry, "cancel", List.of(ProtosNullValue.INSTANCE), fixture.creatorActivation));
    }

    @Test
    void retryOnAcceptedOperationSignalsError() throws Exception {
        Fixture fixture = fixture();
        ProtosActor target = fixture.readyTarget(1, ordinaryBehavior());
        ProtosSendOperationValue accepted = fixture.send(target, "noop");

        assertThrows(
                ProtosSignalException.class,
                () ->
                        ProtosInvocation.invokeMessage(
                                accepted, "retry", List.of(), fixture.creatorActivation));
    }

    @Test
    void unhandledHandlerErrorFailsDeliveryAndTerminatesSameActor() throws Exception {
        Fixture fixture = fixture();
        ProtosObjectValue behavior = new ProtosObjectValue(ProtosObjectValue.rootObject());
        behavior.createLocalSlot(
                "boom",
                ProtosClosureValue.nativeClosure(
                        (activation, arguments) -> {
                            throw new ProtosSignalException(ProtosCoreErrors.newError(activation));
                        }));
        ProtosActor target = fixture.readyTarget(1, behavior);
        ProtosSendOperationValue operation = fixture.send(target, "boom");
        ProtosActorRefValue originalRef = target.reference();

        fixture.carriers.runNext();

        assertEquals(ProtosActor.LifecycleState.TERMINATED, target.lifecycleState());
        assertSame(originalRef, target.reference());
        assertEquals(
                ProtosActorDeliveryAttempt.State.FAILED_AFTER_ACCEPTANCE,
                operation.deliveryStateForTesting());
        ProtosSendOperationValue retry =
                assertInstanceOf(
                        ProtosSendOperationValue.class,
                        ProtosInvocation.invokeMessage(
                                operation, "retry", List.of(), fixture.creatorActivation));
        assertNotSame(operation, retry);
    }

    @Test
    void sendOperationIsNotActorTransferable() throws Exception {
        Fixture fixture = fixture();
        ProtosActor target = fixture.readyTarget(1, ordinaryBehavior());
        ProtosSendOperationValue operation = fixture.send(target, "noop");

        ProtosSignalException failure =
                assertThrows(
                        ProtosSignalException.class,
                        () -> ProtosActorValueTransfer.snapshotValue(operation, fixture.creatorActivation));
        assertSame(
                fixture.prelude.standardErrorPrototype("NonTransferableValue"),
                failure.error().parent().orElseThrow());
    }

    private static ProtosObjectValue ordinaryBehavior() {
        ProtosObjectValue behavior = new ProtosObjectValue(ProtosObjectValue.rootObject());
        behavior.createLocalSlot(
                "noop",
                ProtosClosureValue.nativeClosure(
                        (activation, arguments) -> new ProtosStringValue("ignored")));
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
            actor.bindMessageEnvironmentForRuntime(prelude, new ProtosModuleKey("target"));
            actor.completeInitialization(behavior);
            new ProtosActorScheduler(carriers, 1).attach(actor);
            return actor;
        }

        private ProtosSendOperationValue send(ProtosActor target, String selector, Object... arguments) {
            java.util.ArrayList<Object> supplied = new java.util.ArrayList<>();
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
