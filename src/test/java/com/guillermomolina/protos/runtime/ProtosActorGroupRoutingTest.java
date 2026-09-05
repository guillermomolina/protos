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
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class ProtosActorGroupRoutingTest {
    private static final Path CORE = Path.of("protos", "lib", "core");

    @Test
    void groupIdentitySurvivesEmptyAndCompletelyReplacedMembership() {
        ProtosActorGroupRuntime group = new ProtosActorGroupRuntime();
        UUID identity = group.groupIdentityForRuntime();
        ProtosActor first = readyActor();
        ProtosActor second = readyActor();

        assertEquals(0, group.memberCountForTesting());
        assertTrue(group.selectEligibleMemberForRuntime().isEmpty());
        assertTrue(group.addMemberForRuntime(first));
        assertSame(first, group.selectEligibleMemberForRuntime().orElseThrow());
        assertTrue(group.removeMemberForRuntime(first));
        assertTrue(group.selectEligibleMemberForRuntime().isEmpty());
        assertTrue(group.addMemberForRuntime(second));
        assertSame(second, group.selectEligibleMemberForRuntime().orElseThrow());
        assertEquals(identity, group.groupIdentityForRuntime());
    }

    @Test
    void routingEligibilityRequiresReadyMembershipAndNeverStartsInitializingActor() {
        ProtosActorGroupRuntime group = new ProtosActorGroupRuntime();
        ProtosActor initializing = new ProtosActor(actorRefPrototype());
        ProtosActor ready = readyActor();
        ProtosActor stopping = readyActor();
        stopping.requestTerminationForRuntime();

        group.addMemberForRuntime(initializing);
        group.addMemberForRuntime(stopping);
        assertTrue(group.selectEligibleMemberForRuntime().isEmpty());

        group.addMemberForRuntime(ready);
        for (int i = 0; i < 20; i++) {
            assertSame(ready, group.selectEligibleMemberForRuntime().orElseThrow());
        }
    }

    @Test
    void selectionAmongEligibleMembersIsInternalAndDoesNotChangeMembership() {
        ProtosActorGroupRuntime group = new ProtosActorGroupRuntime();
        ProtosActor first = readyActor();
        ProtosActor second = readyActor();
        group.addMemberForRuntime(first);
        group.addMemberForRuntime(second);

        assertSame(first, group.selectEligibleMemberForRuntime().orElseThrow());
        assertSame(second, group.selectEligibleMemberForRuntime().orElseThrow());
        assertSame(first, group.selectEligibleMemberForRuntime().orElseThrow());
        assertEquals(2, group.memberCountForTesting());
    }

    @Test
    void boundGroupRefsHaveIndependentReferenceIdentityAndSameStableGroupTarget() throws Exception {
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
        ProtosActorGroupRuntime group = new ProtosActorGroupRuntime();
        ProtosObjectValue prototype = groupRefPrototype();
        UUID restriction = UUID.fromString("77777777-7777-7777-7777-777777777777");
        ProtosGroupRefValue first = group.acquireReferenceForRuntime(prototype, restriction);
        ProtosGroupRefValue second = group.acquireReferenceForRuntime(prototype, restriction);
        ProtosGroupRefValue transferred = first.rematerializeForActorTransfer();

        assertFalse(ProtosIdentity.identical(first, second));
        assertTrue(ProtosIdentity.identical(first, transferred));
        assertEquals(group.groupIdentityForRuntime(), first.groupIdentityForRuntime());
        assertSame(group, first.localGroupForRuntime().orElseThrow());
        assertSame(group, transferred.localGroupForRuntime().orElseThrow());
        assertSame(prototype, first.representedDelegationParent(prelude));
    }

    @Test
    void groupTerminationDoesNotStopMembersAndNeverRetargetsExistingReference() {
        ProtosActorGroupRuntime group = new ProtosActorGroupRuntime();
        ProtosActor member = readyActor();
        group.addMemberForRuntime(member);
        ProtosGroupRefValue reference =
                group.acquireReferenceForRuntime(
                        groupRefPrototype(),
                        UUID.fromString("88888888-8888-8888-8888-888888888888"));
        UUID identity = reference.groupIdentityForRuntime();

        assertTrue(group.markTerminatedForRuntime());
        assertFalse(group.markTerminatedForRuntime());
        assertEquals(ProtosActorGroupRuntime.LifecycleState.TERMINATED, group.lifecycleState());
        assertEquals(ProtosActor.LifecycleState.READY, member.lifecycleState());
        assertTrue(group.selectEligibleMemberForRuntime().isEmpty());
        assertEquals(identity, reference.groupIdentityForRuntime());
        assertSame(group, reference.localGroupForRuntime().orElseThrow());
        assertThrows(
                IllegalStateException.class,
                () -> group.acquireReferenceForRuntime(groupRefPrototype(), UUID.randomUUID()));
    }

    private static ProtosActor readyActor() {
        ProtosActor actor = new ProtosActor(actorRefPrototype());
        assertTrue(actor.completeInitialization(new ProtosObjectValue(ProtosObjectValue.rootObject())));
        return actor;
    }

    private static ProtosObjectValue actorRefPrototype() {
        return new ProtosObjectValue(ProtosObjectValue.rootObject()).freeze();
    }

    private static ProtosObjectValue groupRefPrototype() {
        return new ProtosObjectValue(ProtosObjectValue.rootObject()).freeze();
    }
}
