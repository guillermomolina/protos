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

    /** Records Group callbacks; optionally equal to every other instance of this class. */
    private static final class RecordingOperation
            implements ProtosActorGroupRuntime.RoutingOperation {
        private final String name;
        private final java.util.List<String> log;
        private final boolean equalToAll;

        RecordingOperation(String name, java.util.List<String> log, boolean equalToAll) {
            this.name = name;
            this.log = log;
            this.equalToAll = equalToAll;
        }

        @Override
        public void routeToForRuntime(ProtosActorRefValue member) {
            log.add(name + ":route");
        }

        @Override
        public void memberBecameIneligibleForRuntime(ProtosActorRefValue member) {
            log.add(name + ":ineligible");
        }

        @Override
        public void groupTerminatedBeforeAcceptanceForRuntime() {
            log.add(name + ":terminated");
        }

        @Override
        public boolean equals(Object other) {
            return equalToAll ? other instanceof RecordingOperation : this == other;
        }

        @Override
        public int hashCode() {
            return equalToAll ? 1 : System.identityHashCode(this);
        }
    }

    @Test
    void activeRoutingMembershipIsByIdentityAndPreservesSubmissionOrder() {
        ProtosActorGroupRuntime group = new ProtosActorGroupRuntime();
        java.util.List<String> log = new java.util.ArrayList<>();
        RecordingOperation first = new RecordingOperation("first", log, true);
        RecordingOperation second = new RecordingOperation("second", log, true);

        // No eligible member: both stay pending and active, distinct despite equality.
        group.submitRoutingForRuntime(first);
        group.submitRoutingForRuntime(second);
        assertEquals(2, group.pendingOperationCountForTesting());

        assertTrue(group.markTerminatedForRuntime());
        assertEquals(java.util.List.of("first:terminated", "second:terminated"), log);
        assertEquals(0, group.pendingOperationCountForTesting());
        assertFalse(group.markTerminatedForRuntime());
        assertEquals(2, log.size());
    }

    @Test
    void cancellationBeforeAcceptanceAndTerminalRemovalFollowTheExactOperation() {
        ProtosActorGroupRuntime group = new ProtosActorGroupRuntime();
        java.util.List<String> log = new java.util.ArrayList<>();
        RecordingOperation cancelled = new RecordingOperation("cancelled", log, false);
        RecordingOperation kept = new RecordingOperation("kept", log, false);
        RecordingOperation never = new RecordingOperation("never", log, false);

        group.submitRoutingForRuntime(cancelled);
        group.submitRoutingForRuntime(kept);
        assertFalse(group.cancelRoutingBeforeAcceptanceForRuntime(never));

        // Cancellation leaves pending routing but keeps the operation active until it finishes.
        assertTrue(group.cancelRoutingBeforeAcceptanceForRuntime(cancelled));
        assertEquals(1, group.pendingOperationCountForTesting());
        assertTrue(group.cancelRoutingBeforeAcceptanceForRuntime(cancelled));

        group.operationFinishedForRuntime(cancelled);
        assertFalse(group.cancelRoutingBeforeAcceptanceForRuntime(cancelled));
        assertFalse(group.requeueAfterPreacceptFailureForRuntime(cancelled));
        group.operationFinishedForRuntime(cancelled);

        // A finished operation is no longer notified; the remaining one is notified once.
        assertTrue(group.markTerminatedForRuntime());
        assertEquals(java.util.List.of("kept:terminated"), log);
    }

    @Test
    void resubmittingTheSameOperationKeepsOneActiveEntry() {
        ProtosActorGroupRuntime group = new ProtosActorGroupRuntime();
        java.util.List<String> log = new java.util.ArrayList<>();
        RecordingOperation operation = new RecordingOperation("only", log, false);

        group.submitRoutingForRuntime(operation);
        group.submitRoutingForRuntime(operation);
        assertEquals(1, group.pendingOperationCountForTesting());
        assertTrue(group.requeueAfterPreacceptFailureForRuntime(operation));
        assertEquals(1, group.pendingOperationCountForTesting());

        assertTrue(group.markTerminatedForRuntime());
        assertEquals(java.util.List.of("only:terminated"), log);
    }

    @Test
    void cancellingAnEqualButDistinctOperationLeavesTheFirstPending() {
        ProtosActorGroupRuntime group = new ProtosActorGroupRuntime();
        java.util.List<String> log = new java.util.ArrayList<>();
        RecordingOperation first = new RecordingOperation("first", log, true);
        RecordingOperation second = new RecordingOperation("second", log, true);
        assertEquals(first, second);

        group.submitRoutingForRuntime(first);
        group.submitRoutingForRuntime(second);
        assertTrue(group.cancelRoutingBeforeAcceptanceForRuntime(second));
        assertEquals(1, group.pendingOperationCountForTesting());

        // Only the exact remaining instance is routed once a member becomes eligible.
        assertTrue(group.addMemberForRuntime(readyActor()));
        assertEquals(java.util.List.of("first:route"), log);
        assertEquals(0, group.pendingOperationCountForTesting());
    }

    @Test
    void finishingAnOperationNeverRetiresAnEqualButDistinctOne() {
        ProtosActorGroupRuntime group = new ProtosActorGroupRuntime();
        java.util.List<String> log = new java.util.ArrayList<>();
        RecordingOperation first = new RecordingOperation("first", log, true);
        RecordingOperation second = new RecordingOperation("second", log, true);

        group.submitRoutingForRuntime(first);
        group.submitRoutingForRuntime(second);
        group.operationFinishedForRuntime(second);
        assertEquals(1, group.pendingOperationCountForTesting());
        assertFalse(group.cancelRoutingBeforeAcceptanceForRuntime(second));

        assertTrue(group.markTerminatedForRuntime());
        assertEquals(java.util.List.of("first:terminated"), log);
    }

    @Test
    void requeueChecksPendingByExactInstanceAndKeepsFifoOrder() {
        ProtosActorGroupRuntime group = new ProtosActorGroupRuntime();
        java.util.List<String> log = new java.util.ArrayList<>();
        RecordingOperation first = new RecordingOperation("first", log, true);
        RecordingOperation second = new RecordingOperation("second", log, true);

        group.submitRoutingForRuntime(first);
        group.submitRoutingForRuntime(second);
        assertTrue(group.cancelRoutingBeforeAcceptanceForRuntime(first));
        assertEquals(1, group.pendingOperationCountForTesting());

        // An equal but distinct pending entry must not suppress requeueing the exact instance.
        assertTrue(group.requeueAfterPreacceptFailureForRuntime(first));
        assertEquals(2, group.pendingOperationCountForTesting());
        assertTrue(group.requeueAfterPreacceptFailureForRuntime(first));
        assertEquals(2, group.pendingOperationCountForTesting());

        assertTrue(group.addMemberForRuntime(readyActor()));
        assertEquals(java.util.List.of("second:route", "first:route"), log);
    }

    @Test
    void pendingRoutingDrainsInSubmissionOrder() {
        ProtosActorGroupRuntime group = new ProtosActorGroupRuntime();
        java.util.List<String> log = new java.util.ArrayList<>();
        RecordingOperation first = new RecordingOperation("first", log, true);
        RecordingOperation second = new RecordingOperation("second", log, true);
        RecordingOperation third = new RecordingOperation("third", log, false);

        group.submitRoutingForRuntime(first);
        group.submitRoutingForRuntime(second);
        group.submitRoutingForRuntime(third);
        assertTrue(group.addMemberForRuntime(readyActor()));
        assertEquals(java.util.List.of("first:route", "second:route", "third:route"), log);
    }

    @Test
    void repeatedMembershipAcquisitionKeepsOneMemberPerIncarnation() {
        ProtosActorGroupRuntime group = new ProtosActorGroupRuntime();
        ProtosActor actor = readyActor();

        assertTrue(group.addMemberForRuntime(actor));
        assertFalse(group.addMemberForRuntime(actor));
        assertFalse(group.addInitialMemberReferenceForRuntime(actor.reference()));
        assertEquals(1, group.memberCountForTesting());

        assertTrue(group.removeMemberForRuntime(actor));
        assertFalse(group.removeMemberForRuntime(actor));
        assertEquals(0, group.memberCountForTesting());

        assertTrue(group.addMemberForRuntime(actor));
        assertEquals(1, group.memberCountForTesting());
        assertSame(actor, group.selectEligibleMemberForRuntime().orElseThrow());
    }

    @Test
    void removingAMemberPreservesTheOrderOfTheRemainingMembers() {
        ProtosActorGroupRuntime group = new ProtosActorGroupRuntime();
        ProtosActor first = readyActor();
        ProtosActor second = readyActor();
        ProtosActor third = readyActor();
        ProtosActor outsider = readyActor();
        group.addMemberForRuntime(first);
        group.addMemberForRuntime(second);
        group.addMemberForRuntime(third);

        assertFalse(group.removeMemberForRuntime(outsider));
        assertTrue(group.removeMemberForRuntime(second));
        assertEquals(2, group.memberCountForTesting());
        assertSame(first, group.selectEligibleMemberForRuntime().orElseThrow());
        assertSame(third, group.selectEligibleMemberForRuntime().orElseThrow());
        assertSame(first, group.selectEligibleMemberForRuntime().orElseThrow());

        assertTrue(group.addMemberForRuntime(second));
        assertEquals(3, group.memberCountForTesting());
    }

    @Test
    void submissionAfterTerminationIsRejectedBeforeAcceptanceWithoutRegistration() {
        ProtosActorGroupRuntime group = new ProtosActorGroupRuntime();
        java.util.List<String> log = new java.util.ArrayList<>();
        RecordingOperation late = new RecordingOperation("late", log, false);
        assertTrue(group.markTerminatedForRuntime());

        group.submitRoutingForRuntime(late);
        assertEquals(java.util.List.of("late:terminated"), log);
        assertFalse(group.cancelRoutingBeforeAcceptanceForRuntime(late));
        assertEquals(0, group.pendingOperationCountForTesting());
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
