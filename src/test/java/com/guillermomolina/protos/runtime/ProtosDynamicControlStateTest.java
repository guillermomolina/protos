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
    void taskOwnedStateAndFrameSurviveEvaluatorSegmentReset() {
        ProtosActorExecutionDomain domain = new ProtosActorExecutionDomain();
        ProtosTask task = domain.createTask(null, current -> current.complete("done"));
        ProtosDynamicControlState state = task.dynamicControlState();
        ProtosDynamicControlState.Frame frame =
                state.enterFrame(new Object(), ProtosDynamicControlState.FrameKind.ENSURE);

        task.evaluatorContinuation().beginSegment();
        task.evaluatorContinuation().endSegment();
        task.evaluatorContinuation().beginSegment();

        assertSame(state, task.dynamicControlState());
        assertSame(frame, state.framesNewestFirst().get(0));

        task.evaluatorContinuation().endSegment();
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

}
