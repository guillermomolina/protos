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

import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** PERF025-G1: structured-child bookkeeping is materialized only when a child is installed. */
final class ProtosPerf025G1LazyTaskChildrenTest {
    private static final class Dependency implements ProtosTask.WaitDependency {}

    @Test
    void childlessTaskCompletesWithoutMaterializingChildren() {
        ProtosActorExecutionDomain domain = new ProtosActorExecutionDomain();
        ProtosTask task = domain.createTask(null, current -> current.complete("done"));
        assertFalse(task.childrenMaterializedForTesting());

        domain.dispatchUntilIdle();

        assertEquals(ProtosTask.State.COMPLETED, task.state());
        assertEquals("done", task.result().orElseThrow());
        assertTrue(task.children().isEmpty());
        assertFalse(task.childrenMaterializedForTesting());
    }

    @Test
    void firstChildMaterializesOwnershipAndTerminalChildIsRemoved() {
        ProtosActorExecutionDomain domain = new ProtosActorExecutionDomain();
        ProtosTask parent = domain.createTask(null, current -> current.suspend(new Dependency()));
        assertTrue(domain.dispatchOne());
        assertFalse(parent.childrenMaterializedForTesting());

        ProtosTask child = domain.createTask(parent, null, current -> current.complete("child"));
        assertTrue(parent.childrenMaterializedForTesting());
        assertEquals(Set.of(child), parent.children());
        assertSame(parent, child.parent().orElseThrow());

        assertTrue(domain.dispatchOne());
        assertEquals(ProtosTask.State.COMPLETED, child.state());
        assertTrue(parent.children().isEmpty());
        assertEquals(ProtosTask.State.SUSPENDED, parent.state());
    }

    @Test
    void normalCompletionWaitsForLiveChildrenPerParent() {
        ProtosActorExecutionDomain domain = new ProtosActorExecutionDomain();
        AtomicReference<ProtosTask> childA = new AtomicReference<>();
        AtomicReference<ProtosTask> childB = new AtomicReference<>();
        Dependency gate = new Dependency();
        ProtosTask parentA = domain.createTask(null, current -> {
            childA.set(domain.createTask(current, null, c -> c.complete("a")));
            current.complete("parent-a");
        });
        ProtosTask parentB = domain.createTask(null, current -> {
            childB.set(domain.createTask(current, null, c -> {
                if (c.consumeResume(gate)) {
                    c.complete("b");
                } else {
                    c.suspend(gate);
                }
            }));
            current.complete("parent-b");
        });

        assertTrue(domain.dispatchOne());
        assertTrue(domain.dispatchOne());
        assertEquals(ProtosTask.State.SUSPENDED, parentA.state());
        assertEquals(ProtosTask.State.SUSPENDED, parentB.state());

        domain.dispatchUntilIdle();
        assertEquals(ProtosTask.State.COMPLETED, childA.get().state());
        assertEquals(ProtosTask.State.COMPLETED, parentA.state());
        assertEquals("parent-a", parentA.result().orElseThrow());
        assertTrue(parentA.children().isEmpty());
        assertEquals(ProtosTask.State.SUSPENDED, childB.get().state());
        assertEquals(
                ProtosTask.State.SUSPENDED,
                parentB.state(),
                "shared drain sentinel must not wake an unrelated parent");

        assertTrue(childB.get().resume(gate));
        domain.dispatchUntilIdle();
        assertEquals(ProtosTask.State.COMPLETED, childB.get().state());
        assertEquals(ProtosTask.State.COMPLETED, parentB.state());
        assertEquals("parent-b", parentB.result().orElseThrow());
    }

    @Test
    void failureCancelsAndDrainsChildrenBeforeTerminalFailure() {
        ProtosActorExecutionDomain domain = new ProtosActorExecutionDomain();
        AtomicReference<ProtosTask> child = new AtomicReference<>();
        Object error = new Object();
        ProtosTask parent = domain.createTask(null, current -> {
            child.set(domain.createTask(current, null, c -> c.suspend(new Dependency())));
            current.fail(error);
        });

        assertTrue(domain.dispatchOne());
        assertEquals(ProtosTask.State.SUSPENDED, parent.state());

        domain.dispatchUntilIdle();
        assertEquals(ProtosTask.State.CANCELLED, child.get().state());
        assertEquals(ProtosTask.State.FAILED, parent.state());
        assertSame(error, parent.failure().orElseThrow());
        assertTrue(parent.children().isEmpty());
    }

    @Test
    void cancellationRequestsChildCancellationAndDrainsBeforeCancelled() {
        ProtosActorExecutionDomain domain = new ProtosActorExecutionDomain();
        AtomicReference<ProtosTask> child = new AtomicReference<>();
        ProtosTask parent = domain.createTask(null, current -> {
            child.set(domain.createTask(current, null, c -> c.suspend(new Dependency())));
            assertTrue(current.requestCancellation());
            assertTrue(current.observeCancellation());
        });

        assertTrue(domain.dispatchOne());
        assertEquals(ProtosTask.State.SUSPENDED, parent.state());
        assertEquals(ProtosTask.CancellationPhase.UNWINDING, parent.cancellationPhase());

        domain.dispatchUntilIdle();
        assertEquals(ProtosTask.State.CANCELLED, child.get().state());
        assertEquals(ProtosTask.State.CANCELLED, parent.state());
        assertEquals(ProtosTask.CancellationPhase.TERMINAL, parent.cancellationPhase());
        assertTrue(parent.children().isEmpty());
    }
}
