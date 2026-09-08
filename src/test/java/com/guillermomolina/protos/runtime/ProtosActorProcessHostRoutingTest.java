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

import java.util.ArrayDeque;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

final class ProtosActorProcessHostRoutingTest {
    @Test
    void controlTaskAndMailboxSegmentsRunInsideBoundProcessHost() {
        ProtosObjectValue actorRefPrototype =
                new ProtosObjectValue(ProtosObjectValue.rootObject()).freeze();
        ProtosProcessRuntime process = new ProtosProcessRuntime(actorRefPrototype);
        RecordingHost host = new RecordingHost();
        process.bindExecutionHostForRuntime(host);

        ManualExecutor carriers = new ManualExecutor();
        ProtosActorScheduler scheduler = new ProtosActorScheduler(carriers, 1);
        ProtosActor actor = process.rootActorForRuntime();

        scheduler.attach(actor);
        AtomicInteger controlTurns = new AtomicInteger();
        scheduler.submitControl(
                actor,
                () -> {
                    host.requireEntered();
                    controlTurns.incrementAndGet();
                });
        carriers.runNext();

        assertEquals(1, controlTurns.get());
        assertEquals(1, host.segmentCalls());

        assertTrue(actor.markReady());
        AtomicInteger taskTurns = new AtomicInteger();
        AtomicInteger mailboxTurns = new AtomicInteger();
        actor.executionDomain()
                .createTask(
                        null,
                        task -> {
                            host.requireEntered();
                            taskTurns.incrementAndGet();
                            task.complete(null);
                        });
        assertTrue(
                actor.tryAcceptMessageForRuntime(
                        task -> {
                            host.requireEntered();
                            mailboxTurns.incrementAndGet();
                            task.complete(null);
                        }));

        carriers.runNext();

        assertEquals(1, taskTurns.get());
        assertEquals(1, mailboxTurns.get());
        assertEquals(3, host.segmentCalls());
    }

    private static final class RecordingHost implements ProtosProcessExecutionHost {
        private final AtomicInteger calls = new AtomicInteger();
        private int depth;

        @Override
        public synchronized <T> T callForRuntime(Supplier<T> action) {
            calls.incrementAndGet();
            depth++;
            try {
                return action.get();
            } finally {
                depth--;
            }
        }

        void requireEntered() {
            synchronized (this) {
                assertTrue(depth > 0, "Actor segment escaped the bound Process execution host");
            }
        }

        int segmentCalls() {
            return calls.get();
        }

        @Override
        public void processTerminatedForRuntime() {}
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
            assertNotNull(command, "expected one Actor carrier worker");
            command.run();
        }
    }
}
