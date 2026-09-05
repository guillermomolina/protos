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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.execution.ProtosCoreBootstrap;
import com.guillermomolina.protos.execution.ProtosInvocation;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** D039 / I011-21 conformance for direct Core ActorGroup acquisition and Process ownership. */
final class ProtosActorGroupAcquisitionTest {
    private static final Path CORE = Path.of("protos", "lib", "core");

    @Test
    void groupValidatesWholeVectorAndPublishesNoPartialIdentity() throws Exception {
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
        ProtosProcessRuntime owner = new ProtosProcessRuntime(prelude.actorRefPrototypeForRuntime());
        ProtosActor creator = owner.rootActorForRuntime();
        ProtosObjectValue actorObject = actorObject(prelude);
        ProtosActivation activation = activation(prelude, creator);
        ProtosActor member = new ProtosActor(prelude.actorRefPrototypeForRuntime());

        assertThrows(
                ProtosSignalException.class,
                () -> ProtosInvocation.invokeMessage(actorObject, "group", List.of(), activation));
        assertThrows(
                ProtosSignalException.class,
                () ->
                        ProtosInvocation.invokeMessage(
                                actorObject,
                                "group",
                                List.of(member.reference(), new ProtosStringValue("not-an-ActorRef")),
                                activation));

        assertEquals(0, owner.ownedGroupCountForTesting());
        assertFalse(prelude.bindings().hasLocalSlot("Group"));
        assertFalse(prelude.bindings().hasLocalSlot("GroupRef"));
    }

    @Test
    void groupCreatesFreshIdentitiesDeduplicatesMembershipAndDoesNotWaitForReady()
            throws Exception {
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
        ProtosProcessRuntime owner = new ProtosProcessRuntime(prelude.actorRefPrototypeForRuntime());
        ProtosActor creator = owner.rootActorForRuntime();
        ProtosObjectValue actorObject = actorObject(prelude);
        ProtosActivation activation = activation(prelude, creator);
        ProtosActor first = new ProtosActor(prelude.actorRefPrototypeForRuntime());
        ProtosActor second = new ProtosActor(prelude.actorRefPrototypeForRuntime());

        ProtosGroupRefValue reference =
                assertInstanceOf(
                        ProtosGroupRefValue.class,
                        ProtosInvocation.invokeMessage(
                                actorObject,
                                "group",
                                List.of(first.reference(), first.reference(), second.reference()),
                                activation));
        ProtosActorGroupRuntime group = reference.localGroupForRuntime().orElseThrow();

        assertEquals(ProtosActorGroupRuntime.LifecycleState.LIVE, group.lifecycleState());
        assertEquals(2, group.memberCountForTesting());
        assertTrue(group.selectEligibleMemberForRuntime().isEmpty());
        assertEquals(1, owner.ownedGroupCountForTesting());

        assertTrue(first.markReady());
        assertSame(first, group.selectEligibleMemberForRuntime().orElseThrow());

        ProtosGroupRefValue secondAcquisition =
                assertInstanceOf(
                        ProtosGroupRefValue.class,
                        ProtosInvocation.invokeMessage(
                                actorObject,
                                "group",
                                List.of(first.reference(), second.reference()),
                                activation));
        assertNotEquals(reference.groupIdentityForRuntime(), secondAcquisition.groupIdentityForRuntime());
        assertNotEquals(reference.semanticIdentityForRuntime(), secondAcquisition.semanticIdentityForRuntime());
        assertFalse(ProtosIdentity.identical(reference, secondAcquisition));
        assertEquals(2, owner.ownedGroupCountForTesting());
    }

    @Test
    void owningProcessTerminationEndsGroupWithoutStoppingExternalMembers() throws Exception {
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
        ProtosProcessRuntime owner = new ProtosProcessRuntime(prelude.actorRefPrototypeForRuntime());
        ProtosActor creator = owner.rootActorForRuntime();
        ProtosProcessRuntime memberProcess = new ProtosProcessRuntime(prelude.actorRefPrototypeForRuntime());
        ProtosActor externalMember = memberProcess.rootActorForRuntime();
        assertTrue(externalMember.markReady());

        ProtosGroupRefValue reference =
                assertInstanceOf(
                        ProtosGroupRefValue.class,
                        ProtosInvocation.invokeMessage(
                                actorObject(prelude),
                                "group",
                                List.of(externalMember.reference()),
                                activation(prelude, creator)));
        ProtosActorGroupRuntime group = reference.localGroupForRuntime().orElseThrow();
        java.util.UUID groupIdentity = reference.groupIdentityForRuntime();

        assertSame(externalMember, group.selectEligibleMemberForRuntime().orElseThrow());
        assertTrue(owner.requestTerminationForRuntime());

        assertEquals(ProtosActorGroupRuntime.LifecycleState.TERMINATED, group.lifecycleState());
        assertTrue(group.selectEligibleMemberForRuntime().isEmpty());
        assertEquals(ProtosActor.LifecycleState.READY, externalMember.lifecycleState());
        assertEquals(ProtosProcessRuntime.LifecycleState.RUNNING, memberProcess.lifecycleState());
        assertEquals(groupIdentity, reference.groupIdentityForRuntime());
        assertSame(group, reference.localGroupForRuntime().orElseThrow());
    }

    private static ProtosObjectValue actorObject(ProtosPrelude prelude) {
        return assertInstanceOf(
                ProtosObjectValue.class,
                prelude.bindings().readLocalSlot("Actor").orElseThrow());
    }

    private static ProtosActivation activation(ProtosPrelude prelude, ProtosActor actor) {
        return prelude.newModuleActivation(
                actor.moduleState(),
                new ProtosModuleKey("group-owner"),
                prelude.newExecutionContext(),
                actor.executionDomain());
    }
}
