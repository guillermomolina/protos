/*
 * THE LICENSED WORK IS PROVIDED UNDER THE TERMS OF THE ADAPTIVE PUBLIC LICENSE
 * ("LICENSE") AS FIRST COMPLETED BY: Guillermo Adrián Molina. See LICENSE.TXT.
 */
package com.guillermomolina.protos.runtime;

import static org.junit.jupiter.api.Assertions.*;

import com.guillermomolina.protos.execution.ProtosCoreBootstrap;
import com.guillermomolina.protos.execution.ProtosLanguage;
import com.guillermomolina.protos.execution.ProtosPerf006BytecodeTestSupport;
import com.guillermomolina.protos.execution.ProtosPerf006BytecodeTestSupport.Dependency;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.graalvm.polyglot.Context;
import org.junit.jupiter.api.Test;

final class ProtosPerf006B6A6A3ActorGroupLifecycleIntegrationTest {
    private static final Path CORE = Path.of("protos", "lib", "core");

    @Test
    void cPrimeSuspensionFinalizesActorAndGroupRequestSendOnlyAfterTrueTerminalResume()
            throws Exception {
        try (Context context = Context.newBuilder(ProtosLanguage.ID).build()) {
            context.initialize(ProtosLanguage.ID);
            context.enter();
            try {
                ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
                ProtosActor sender = readySender(prelude);
                ProtosActivation caller =
                        prelude.newModuleActivation(
                                sender.moduleState(),
                                new ProtosModuleKey("perf006-b6a6a3-sender"),
                                prelude.newExecutionContext(),
                                sender.executionDomain());

                Target actorRequest = cPrimeTarget(prelude, "actor-request");
                ProtosFutureValue actorReply =
                        ProtosActorRequest.begin(
                                actorRequest.actor.reference(),
                                sender.reference(),
                                "work",
                                List.of(),
                                caller);
                dispatchAccepted(actorRequest.actor);
                assertEquals(ProtosFutureValue.State.PENDING, actorReply.state());
                assertEquals(1, actorRequest.probes.get());
                assertEquals(1, actorRequest.actor.executionDomain().liveTaskCount());
                assertTrue(actorRequest.dependency.complete());
                assertTrue(actorRequest.actor.executionDomain().dispatchOne());
                assertEquals(ProtosFutureValue.State.RESOLVED, actorReply.state());
                assertEquals(
                        "reply",
                        assertInstanceOf(
                                        ProtosStringValue.class,
                                        actorReply.resolvedValue().orElseThrow())
                                .value());
                assertEquals(1, actorRequest.probes.get());
                assertEquals(0, actorRequest.actor.executionDomain().liveTaskCount());

                Target actorSend = cPrimeTarget(prelude, "actor-send");
                ProtosSendOperationValue send =
                        ProtosSendOperationValue.begin(
                                sendOperationPrototype(),
                                actorSend.actor.reference(),
                                sender.reference(),
                                "work",
                                List.of());
                dispatchAccepted(actorSend.actor);
                assertEquals(
                        ProtosActorDeliveryAttempt.State.RUNNING,
                        send.deliveryStateForTesting());
                assertEquals(1, actorSend.probes.get());
                assertTrue(actorSend.dependency.complete());
                assertTrue(actorSend.actor.executionDomain().dispatchOne());
                assertEquals(
                        ProtosActorDeliveryAttempt.State.COMPLETED,
                        send.deliveryStateForTesting());
                assertEquals(1, actorSend.probes.get());

                Target groupRequestTarget = cPrimeTarget(prelude, "group-request");
                ProtosActorGroupRuntime requestGroup = new ProtosActorGroupRuntime();
                requestGroup.addMemberForRuntime(groupRequestTarget.actor);
                ProtosFutureValue groupReply =
                        groupReference(requestGroup, 1)
                                .beginRequestForRuntime(
                                        sender.reference(),
                                        "work",
                                        List.of(),
                                        caller);
                dispatchAccepted(groupRequestTarget.actor);
                assertEquals(ProtosFutureValue.State.PENDING, groupReply.state());
                assertEquals(1, groupRequestTarget.probes.get());
                assertTrue(groupRequestTarget.dependency.complete());
                assertTrue(groupRequestTarget.actor.executionDomain().dispatchOne());
                assertEquals(ProtosFutureValue.State.RESOLVED, groupReply.state());
                assertEquals(
                        "reply",
                        assertInstanceOf(
                                        ProtosStringValue.class,
                                        groupReply.resolvedValue().orElseThrow())
                                .value());
                assertEquals(0, requestGroup.pendingOperationCountForTesting());
                assertEquals(1, groupRequestTarget.probes.get());

                Target groupSendTarget = cPrimeTarget(prelude, "group-send");
                ProtosActorGroupRuntime sendGroup = new ProtosActorGroupRuntime();
                sendGroup.addMemberForRuntime(groupSendTarget.actor);
                ProtosGroupSendOperationValue groupSend =
                        groupReference(sendGroup, 2)
                                .beginSendForRuntime(
                                        sendOperationPrototype(),
                                        sender.reference(),
                                        "work",
                                        List.of());
                dispatchAccepted(groupSendTarget.actor);
                assertEquals(1, groupSendTarget.probes.get());
                assertTrue(groupSendTarget.dependency.complete());
                assertTrue(groupSendTarget.actor.executionDomain().dispatchOne());
                assertEquals(
                        ProtosGroupSendOperationValue.State.COMPLETED,
                        groupSend.stateForTesting());
                assertEquals(0, sendGroup.pendingOperationCountForTesting());
                assertEquals(1, groupSendTarget.probes.get());
            } finally {
                context.leave();
            }
        }

        System.out.println("PERF006_B6A6A3_ACTOR_REQUEST_CPRIME_LIFECYCLE=PASS");
        System.out.println("PERF006_B6A6A3_ACTOR_SEND_CPRIME_LIFECYCLE=PASS");
        System.out.println("PERF006_B6A6A3_GROUP_REQUEST_CPRIME_LIFECYCLE=PASS");
        System.out.println("PERF006_B6A6A3_GROUP_SEND_CPRIME_LIFECYCLE=PASS");
        System.out.println("PERF006_B6A6A3_COMPLETED_PREFIX_REPLAY=NO");
    }

    private static Target cPrimeTarget(ProtosPrelude prelude, String name) {
        ProtosActor actor = new ProtosActor(actorRefPrototype());
        ProtosModuleKey key = new ProtosModuleKey("perf006-b6a6a3-" + name);
        actor.bindMessageEnvironmentForRuntime(prelude, key);
        ProtosActivation lexical =
                prelude.newModuleActivation(
                        actor.moduleState(),
                        key,
                        prelude.newExecutionContext(),
                        actor.executionDomain());

        Dependency dependency = new Dependency();
        AtomicInteger probes = new AtomicInteger();
        lexical.context().createLocalSlot(
                "probe",
                ProtosClosureValue.nativeClosure(
                        (activation, supplied) -> {
                            probes.incrementAndGet();
                            return ProtosNullValue.INSTANCE;
                        }));
        lexical.context().createLocalSlot(
                "pause",
                ProtosPerf006BytecodeTestSupport.suspensionCapablePause(
                        dependency,
                        ProtosNullValue.INSTANCE));

        ProtosClosureValue handler =
                ProtosPerf006BytecodeTestSupport.parsedBytecodeClosure(
                        "() => { probe()\npause()\n\"reply\" }",
                        name + ".protos",
                        lexical);
        ProtosObjectValue behavior =
                new ProtosObjectValue(ProtosObjectValue.rootObject());
        behavior.createLocalSlot("work", handler);
        assertTrue(actor.completeInitialization(behavior));
        return new Target(actor, dependency, probes);
    }

    private static ProtosActor readySender(ProtosPrelude prelude) {
        ProtosActor actor = new ProtosActor(actorRefPrototype());
        ProtosModuleKey key = new ProtosModuleKey("perf006-b6a6a3-sender");
        actor.bindMessageEnvironmentForRuntime(prelude, key);
        assertTrue(
                actor.completeInitialization(
                        new ProtosObjectValue(ProtosObjectValue.rootObject())));
        return actor;
    }

    private static void dispatchAccepted(ProtosActor actor) {
        ProtosTask.Continuation turn = actor.mailboxForRuntime().pollForDispatch();
        assertNotNull(turn, "expected accepted Actor turn");
        actor.executionDomain().dispatchAcceptedTurn(turn);
    }

    private static ProtosGroupRefValue groupReference(
            ProtosActorGroupRuntime group,
            int suffix) {
        return group.acquireReferenceForRuntime(
                groupRefPrototype(),
                UUID.fromString(
                        String.format(
                                "99999999-9999-9999-9999-%012d",
                                suffix)));
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

    private static final class Target {
        private final ProtosActor actor;
        private final Dependency dependency;
        private final AtomicInteger probes;

        private Target(
                ProtosActor actor,
                Dependency dependency,
                AtomicInteger probes) {
            this.actor = actor;
            this.dependency = dependency;
            this.probes = probes;
        }
    }
}
