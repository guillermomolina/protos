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
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

final class ProtosGroupRefPublicApiTest {
    private static final Path CORE = Path.of("protos", "lib", "core");

    @Test
    void groupRefPrototypePublishesExactlySendAndRequest() {
        ProtosObjectValue actorRefPrototype = new ProtosObjectValue(ProtosObjectValue.rootObject());
        ProtosObjectValue groupRefPrototype = new ProtosObjectValue(ProtosObjectValue.rootObject());
        ProtosObjectValue sendOperationPrototype = new ProtosObjectValue(ProtosObjectValue.rootObject());
        ProtosStandardActorProtocol protocol =
                new ProtosStandardActorProtocol(
                        new ProtosModuleRuntime(ProtosModuleResolver.rejecting()),
                        Runnable::run,
                        actorRefPrototype,
                        sendOperationPrototype);

        ProtosObjectValue installed = protocol.installGroupRefPrototype(groupRefPrototype);

        assertSame(groupRefPrototype, installed);
        assertTrue(installed.isFrozen());
        assertEquals(Set.of("send", "request"), installed.localSlotsSnapshot().keySet());
        assertFalse(installed.hasLocalSlot("stop"));
        assertFalse(installed.hasLocalSlot("termination"));
        for (String selector : Set.of("send", "request")) {
            ProtosClosureValue closure =
                    assertInstanceOf(
                            ProtosClosureValue.class,
                            installed.readLocalSlot(selector).orElseThrow());
            assertTrue(closure.nativeBody().isPresent());
        }
        assertTrue(actorRefPrototype.isFrozen());
        assertTrue(sendOperationPrototype.isFrozen());
    }

    @Test
    void publicSendAndRequestReuseTheExistingGroupRoutingSemantics() throws Exception {
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
        ProtosObjectValue actorRefPrototype = new ProtosObjectValue(ProtosObjectValue.rootObject());
        ProtosObjectValue groupRefPrototype = new ProtosObjectValue(ProtosObjectValue.rootObject());
        ProtosObjectValue sendOperationPrototype = new ProtosObjectValue(ProtosObjectValue.rootObject());
        ProtosStandardActorProtocol protocol =
                new ProtosStandardActorProtocol(
                        new ProtosModuleRuntime(ProtosModuleResolver.rejecting()),
                        Runnable::run,
                        actorRefPrototype,
                        sendOperationPrototype);
        protocol.installGroupRefPrototype(groupRefPrototype);

        ProtosActor sender = readyActor(prelude, actorRefPrototype, emptyBehavior(), "sender");
        ProtosActivation caller =
                prelude.newModuleActivation(
                        sender.moduleState(),
                        new ProtosModuleKey("sender"),
                        prelude.newExecutionContext(),
                        sender.executionDomain());

        AtomicReference<Object> observed = new AtomicReference<>();
        ProtosObjectValue behavior = new ProtosObjectValue(ProtosObjectValue.rootObject());
        behavior.createLocalSlot(
                "capture",
                ProtosClosureValue.nativeClosure(
                        (activation, supplied) -> {
                            observed.set(supplied.get(0));
                            return ProtosNullValue.INSTANCE;
                        }));
        behavior.createLocalSlot(
                "echo",
                ProtosClosureValue.nativeClosure(
                        (activation, supplied) -> supplied.get(0)));
        ProtosActor target = readyActor(prelude, actorRefPrototype, behavior, "target");
        ProtosActorGroupRuntime group = new ProtosActorGroupRuntime();
        group.addMemberForRuntime(target);
        ProtosGroupRefValue reference =
                group.acquireReferenceForRuntime(
                        groupRefPrototype,
                        UUID.fromString("17171717-1717-1717-1717-171717171717"));

        ProtosObjectValue mutable = new ProtosObjectValue(ProtosObjectValue.rootObject());
        mutable.createLocalSlot("value", new ProtosStringValue("before"));
        ProtosGroupSendOperationValue send =
                assertInstanceOf(
                        ProtosGroupSendOperationValue.class,
                        ProtosInvocation.invokeMessage(
                                reference,
                                "send",
                                List.of(new ProtosStringValue("capture"), mutable),
                                caller));
        assertEquals(ProtosActorDeliveryAttempt.State.ACCEPTED, send.deliveryStateForTesting());
        ProtosObjectValue snapshotted =
                assertInstanceOf(ProtosObjectValue.class, send.snapshotForTesting().get(0));
        assertNotSame(mutable, snapshotted);
        assertEquals(
                "before",
                ((ProtosStringValue) snapshotted.readLocalSlot("value").orElseThrow()).value());
        mutable.assignLocalSlot("value", new ProtosStringValue("after"));
        dispatchAccepted(target);
        assertSame(snapshotted, observed.get());

        ProtosFutureValue request =
                assertInstanceOf(
                        ProtosFutureValue.class,
                        ProtosInvocation.invokeMessage(
                                reference,
                                "request",
                                List.of(
                                        new ProtosStringValue("echo"),
                                        new ProtosStringValue("reply")),
                                caller));
        dispatchAccepted(target);
        assertEquals(ProtosFutureValue.State.RESOLVED, request.state());
        assertEquals(
                "reply",
                assertInstanceOf(
                                ProtosStringValue.class,
                                request.resolvedValue().orElseThrow())
                        .value());
    }

    @Test
    void coreBootstrapKeepsTheGroupRefPrototypeInternal() throws Exception {
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
        assertFalse(prelude.bindings().hasLocalSlot("GroupRef"));
        assertFalse(prelude.bindings().hasLocalSlot("_coreGroupRefPrototype"));
    }

    private static ProtosActor readyActor(
            ProtosPrelude prelude,
            ProtosObjectValue actorRefPrototype,
            ProtosObjectValue behavior,
            String moduleName) {
        ProtosActor actor = new ProtosActor(actorRefPrototype);
        actor.bindMessageEnvironmentForRuntime(prelude, new ProtosModuleKey(moduleName));
        assertTrue(actor.completeInitialization(behavior));
        return actor;
    }

    private static ProtosObjectValue emptyBehavior() {
        return new ProtosObjectValue(ProtosObjectValue.rootObject());
    }

    private static void dispatchAccepted(ProtosActor actor) {
        ProtosTask.Continuation turn = actor.mailboxForRuntime().pollForDispatch();
        assertNotNull(turn);
        actor.executionDomain().dispatchAcceptedTurn(turn);
    }
}
