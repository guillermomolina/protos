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
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ProtosDynamicControlStateTest {
    @Test
    void oneTaskReusesLazyStateAndChildTaskDoesNotInheritIt() {
        ProtosActorExecutionDomain domain = new ProtosActorExecutionDomain();
        ProtosTask parent = domain.createTask(null, current -> current.complete("parent"));
        ProtosTask child = domain.createTask(parent, null, current -> current.complete("child"));

        ProtosDynamicControlState parentState = parent.dynamicControlState();
        assertSame(parentState, parent.dynamicControlState());
        assertNotSame(parentState, child.dynamicControlState());
    }

    /**
     * I126 (D121): the Bytecode cancellation-cleanup extent is the exact authority, not the
     * UNWINDING phase. An unwinding Task outside every extent, another Task, and a cleanup whose
     * cancellation was superseded are not authorized; nested extents keep the authority until the
     * outermost one is left.
     */
    @Test
    void onlyAnOpenCancellationCleanupExtentOfTheUnwindingTaskIsAuthorized() {
        ProtosActorExecutionDomain domain = new ProtosActorExecutionDomain();
        java.util.List<Boolean> observed = new java.util.ArrayList<>();
        ProtosTask[] other = new ProtosTask[1];
        ProtosTask task =
                domain.runFreshRootTaskDirectly(
                        current -> {
                            current.requestCancellation();
                            observed.add(current.beginContinuationCancellationUnwindForRuntime());
                            // Point 7: UNWINDING alone grants nothing.
                            observed.add(current.inCancellationCleanupExtentForRuntime());
                            current.enterCancellationCleanupExtentForRuntime();
                            observed.add(current.inCancellationCleanupExtentForRuntime());
                            // A nested cleanup keeps it; leaving the inner one keeps the outer.
                            current.enterCancellationCleanupExtentForRuntime();
                            current.exitCancellationCleanupExtentForRuntime();
                            observed.add(current.inCancellationCleanupExtentForRuntime());
                            // Another Task never inherits it.
                            other[0] = domain.createTask(null, ignored -> {});
                            observed.add(other[0].inCancellationCleanupExtentForRuntime());
                            // A later transfer superseding the cancellation ends it at once.
                            observed.add(current.supersedeCancellationUnwind());
                            observed.add(current.inCancellationCleanupExtentForRuntime());
                            current.exitCancellationCleanupExtentForRuntime();
                            observed.add(current.inCancellationCleanupExtentForRuntime());
                            current.complete("done");
                        });

        assertEquals(List.of(true, false, true, true, false, true, false, false), observed);
        // A Task that never entered such a cleanup materialized no state for it.
        assertTrue(other[0].dynamicControlStateIfPresent().isEmpty());
        // Leaving more extents than were entered is a runtime defect, never ignored.
        assertThrows(IllegalStateException.class, task::exitCancellationCleanupExtentForRuntime);
        // Entering requires a delivered cancellation unwind.
        assertThrows(IllegalStateException.class, other[0]::enterCancellationCleanupExtentForRuntime);
    }

    @Test
    void replayInvocationIdentityReusesExactlyOneFrame() {
        ProtosDynamicControlState state = new ProtosDynamicControlState();
        Object invocation = new Object();

        ProtosDynamicControlState.Frame first =
                state.enterFrame(invocation, ProtosDynamicControlState.FrameKind.HANDLER);
        ProtosDynamicControlState.Frame replay =
                state.enterFrame(invocation, ProtosDynamicControlState.FrameKind.HANDLER);

        assertSame(first, replay);
        assertSame(first, state.frameForInvocation(invocation).orElseThrow());
        assertEquals(List.of(first), state.framesNewestFirst());
        assertThrows(
                IllegalStateException.class,
                () -> state.enterFrame(invocation, ProtosDynamicControlState.FrameKind.ENSURE));
    }

    @Test
    void selectedHandlerCanDeactivateBeforeLifoBoundaryRemoval() {
        ProtosDynamicControlState state = new ProtosDynamicControlState();
        ProtosDynamicControlState.Frame outer =
                state.enterFrame(new Object(), ProtosDynamicControlState.FrameKind.HANDLER);
        ProtosDynamicControlState.Frame inner =
                state.enterFrame(new Object(), ProtosDynamicControlState.FrameKind.ENSURE);

        state.deactivate(outer);
        assertFalse(outer.active());
        assertTrue(inner.active());
        assertEquals(List.of(inner, outer), state.framesNewestFirst());

        assertThrows(IllegalStateException.class, () -> state.leaveFrame(outer));
        state.leaveFrame(inner);
        state.leaveFrame(outer);
        assertTrue(state.framesNewestFirst().isEmpty());
    }

    @Test
    void laterTransferSupersedesEarlierAndOnlyCurrentTransferCanClear() {
        ProtosDynamicControlState state = new ProtosDynamicControlState();
        Object originalError = new Object();
        Object cleanupReturn = new Object();

        ProtosDynamicControlState.Transfer first =
                state.beginTransfer(ProtosDynamicControlState.TransferKind.ERROR, originalError);
        ProtosDynamicControlState.Transfer later =
                state.replaceTransfer(ProtosDynamicControlState.TransferKind.RETURN, cleanupReturn);

        assertSame(later, state.activeTransfer().orElseThrow());
        assertEquals(ProtosDynamicControlState.TransferKind.RETURN, later.kind());
        assertSame(cleanupReturn, later.payload().orElseThrow());
        assertFalse(state.clearTransfer(first));
        assertTrue(state.clearTransfer(later));
        assertTrue(state.activeTransfer().isEmpty());
    }

    @Test
    void frameCannotBeRemovedThroughAnotherTaskState() {
        ProtosDynamicControlState first = new ProtosDynamicControlState();
        ProtosDynamicControlState second = new ProtosDynamicControlState();
        ProtosDynamicControlState.Frame frame =
                first.enterFrame(new Object(), ProtosDynamicControlState.FrameKind.ENSURE);

        assertThrows(IllegalArgumentException.class, () -> second.deactivate(frame));
        assertThrows(IllegalArgumentException.class, () -> second.leaveFrame(frame));
        assertSame(frame, first.framesNewestFirst().get(0));
    }

    @Test
    void innermostMatchingHandlerSelectionUsesDelegationAndDeactivatesSelection() {
        ProtosDynamicControlState state = new ProtosDynamicControlState();
        ProtosObjectValue errorRoot = new ProtosObjectValue(ProtosObjectValue.rootObject());
        ProtosObjectValue specificPrototype = new ProtosObjectValue(errorRoot);
        ProtosObjectValue occurrence = new ProtosObjectValue(specificPrototype);

        ProtosDynamicControlState.Frame outer =
                state.enterHandlerFrame(new Object(), errorRoot);
        ProtosDynamicControlState.Frame inner =
                state.enterHandlerFrame(new Object(), specificPrototype);

        assertSame(inner, state.selectMatchingHandler(occurrence).orElseThrow());
        assertFalse(inner.active());
        assertTrue(outer.active());

        state.leaveFrame(inner);
        assertSame(outer, state.selectMatchingHandler(occurrence).orElseThrow());
        assertFalse(outer.active());
        state.leaveFrame(outer);
    }


    @Test
    void ensureCleanupPhaseRetainsExactOutcomeAcrossStableFrameReuse() {
        ProtosDynamicControlState state = new ProtosDynamicControlState();
        Object invocation = new Object();
        Object result = new Object();

        ProtosDynamicControlState.Frame frame =
                state.enterFrame(invocation, ProtosDynamicControlState.FrameKind.ENSURE);
        assertEquals(ProtosDynamicControlState.EnsurePhase.BODY, frame.ensurePhase());

        state.beginEnsureCleanup(
                frame,
                ProtosDynamicControlState.EnsureExitKind.NORMAL,
                result);

        ProtosDynamicControlState.Frame replay =
                state.enterFrame(invocation, ProtosDynamicControlState.FrameKind.ENSURE);
        assertSame(frame, replay);
        assertEquals(ProtosDynamicControlState.EnsurePhase.CLEANUP, replay.ensurePhase());
        assertEquals(
                ProtosDynamicControlState.EnsureExitKind.NORMAL,
                replay.ensureExitKind().orElseThrow());
        assertSame(result, replay.ensureOutcome().orElseThrow());

        assertThrows(
                IllegalStateException.class,
                () ->
                        state.beginEnsureCleanup(
                                frame,
                                ProtosDynamicControlState.EnsureExitKind.NORMAL,
                                result));

        state.leaveFrame(frame);
    }

}
