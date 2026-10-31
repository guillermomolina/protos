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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/** D228-A1 exclusive, non-blocking Actor observation permits. */
final class ProtosActorSchedulerObservationPermitTest {
    @Test
    void idleActorIsGranted() {
        ProtosActorScheduler scheduler = new ProtosActorScheduler(new ManualExecutor(), 1);
        ProtosActor actor = actor();
        scheduler.attach(actor);

        Optional<ProtosActorScheduler.ObservationPermit> permit =
                scheduler.tryAcquireObservation(actor);
        assertTrue(permit.isPresent());
        permit.orElseThrow().close();
    }

    @Test
    void runningActorIsDeniedWithoutWaiting() throws Exception {
        Executor threads = command -> {
            Thread carrier = new Thread(command, "d228-permit-test");
            carrier.setDaemon(true);
            carrier.start();
        };
        ProtosActorScheduler scheduler = new ProtosActorScheduler(threads, 1);
        ProtosActor actor = actor();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch completed = new CountDownLatch(1);

        scheduler.attach(actor);
        actor.markReady();
        assertTrue(actor.tryAcceptMessageForRuntime(task -> {
            entered.countDown();
            await(release);
            task.complete(null);
            completed.countDown();
        }));

        try {
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            var attempt = CompletableFuture.supplyAsync(
                    () -> scheduler.tryAcquireObservation(actor));
            assertTrue(attempt.get(2, TimeUnit.SECONDS).isEmpty());
        } finally {
            release.countDown();
        }
        assertTrue(completed.await(2, TimeUnit.SECONDS));
    }

    @Test
    void livePermitPreventsSegmentsUntilReleased() {
        ManualExecutor carriers = new ManualExecutor();
        ProtosActorScheduler scheduler = new ProtosActorScheduler(carriers, 1);
        ProtosActor actor = actor();
        List<String> order = new ArrayList<>();
        scheduler.attach(actor);
        actor.markReady();

        var permit = scheduler.tryAcquireObservation(actor).orElseThrow();
        assertTrue(actor.tryAcceptMessageForRuntime(turn(order, "message")));
        carriers.runAll();
        assertTrue(order.isEmpty());

        permit.close();
        carriers.runAll();
        assertEquals(List.of("message"), order);
    }

    @Test
    void secondSimultaneousPermitIsDenied() {
        ProtosActorScheduler scheduler = new ProtosActorScheduler(new ManualExecutor(), 1);
        ProtosActor actor = actor();
        scheduler.attach(actor);

        var first = scheduler.tryAcquireObservation(actor).orElseThrow();
        assertTrue(scheduler.tryAcquireObservation(actor).isEmpty());
        first.close();
        first.close();
        scheduler.tryAcquireObservation(actor).orElseThrow().close();
    }

    @Test
    void queuedWorkIsKeptAndRequeuedOnRelease() {
        ManualExecutor carriers = new ManualExecutor();
        ProtosActorScheduler scheduler = new ProtosActorScheduler(carriers, 1);
        ProtosActor actor = actor();
        List<String> order = new ArrayList<>();
        scheduler.attach(actor);
        actor.markReady();
        assertTrue(actor.tryAcceptMessageForRuntime(turn(order, "first")));
        assertTrue(actor.tryAcceptMessageForRuntime(turn(order, "second")));

        var permit = scheduler.tryAcquireObservation(actor).orElseThrow();
        carriers.runAll();
        assertTrue(order.isEmpty());
        assertEquals(2, actor.mailboxForRuntime().size());

        permit.close();
        carriers.runAll();
        assertEquals(List.of("first", "second"), order);
    }

    @Test
    void otherActorsProgressIndependently() {
        ManualExecutor carriers = new ManualExecutor();
        ProtosActorScheduler scheduler = new ProtosActorScheduler(carriers, 1);
        ProtosActor observed = actor();
        ProtosActor other = actor();
        List<String> order = new ArrayList<>();
        scheduler.attach(observed);
        scheduler.attach(other);
        observed.markReady();
        other.markReady();

        var permit = scheduler.tryAcquireObservation(observed).orElseThrow();
        assertTrue(observed.tryAcceptMessageForRuntime(turn(order, "observed")));
        assertTrue(other.tryAcceptMessageForRuntime(turn(order, "other")));
        carriers.runAll();
        assertEquals(List.of("other"), order);

        permit.close();
        carriers.runAll();
        assertEquals(List.of("other", "observed"), order);
    }

    @Test
    void permitIsReleasedWhenTheObserverFails() {
        ProtosActorScheduler scheduler = new ProtosActorScheduler(new ManualExecutor(), 1);
        ProtosActor actor = actor();
        scheduler.attach(actor);

        assertThrows(IllegalStateException.class, () -> {
            try (var permit = scheduler.tryAcquireObservation(actor).orElseThrow()) {
                assertNotNull(permit);
                throw new IllegalStateException("observer failure");
            }
        });
        scheduler.tryAcquireObservation(actor).orElseThrow().close();
    }

    @Test
    void terminatingOrDetachedTerminalActorsAreDenied() {
        ProtosActorScheduler scheduler = new ProtosActorScheduler(new ManualExecutor(), 1);
        ProtosActor actor = actor();
        scheduler.attach(actor);
        actor.markReady();
        assertTrue(actor.beginTermination());
        assertTrue(scheduler.tryAcquireObservation(actor).isEmpty());

        scheduler.detach(actor);
        assertTrue(scheduler.tryAcquireObservation(actor).isEmpty());
    }

    @Test
    void unattachedLiveActorPermitDefersALaterAttach() {
        ManualExecutor carriers = new ManualExecutor();
        ProtosActorScheduler scheduler = new ProtosActorScheduler(carriers, 1);
        ProtosActor actor = actor();
        List<String> order = new ArrayList<>();

        var permit = scheduler.tryAcquireObservation(actor).orElseThrow();
        assertTrue(scheduler.tryAcquireObservation(actor).isEmpty());
        assertThrows(IllegalStateException.class,
                () -> scheduler.submitControl(actor, () -> order.add("control")));

        scheduler.attach(actor);
        actor.markReady();
        assertTrue(actor.tryAcceptMessageForRuntime(turn(order, "message")));
        carriers.runAll();
        assertTrue(order.isEmpty());

        permit.close();
        carriers.runAll();
        assertEquals(List.of("message"), order);
    }

    @Test
    void unattachedPermitReleasedBeforeAttachRetainsNoState() {
        ManualExecutor carriers = new ManualExecutor();
        ProtosActorScheduler scheduler = new ProtosActorScheduler(carriers, 1);
        ProtosActor actor = actor();
        List<String> order = new ArrayList<>();

        scheduler.tryAcquireObservation(actor).orElseThrow().close();

        // No placeholder survives: control is still rejected, and attach is a
        // fresh, complete attachment rather than an "already attached" error.
        assertThrows(IllegalStateException.class,
                () -> scheduler.submitControl(actor, () -> order.add("control")));
        scheduler.attach(actor);
        actor.markReady();
        assertTrue(actor.tryAcceptMessageForRuntime(turn(order, "message")));
        carriers.runAll();
        assertEquals(List.of("message"), order);
    }

    @Test
    void concurrentAttachDuringPermitStartsNoTurnUntilRelease() throws Exception {
        ManualExecutor carriers = new ManualExecutor();
        ProtosActorScheduler scheduler = new ProtosActorScheduler(carriers, 1);
        ProtosActor actor = actor();
        List<String> order = new ArrayList<>();

        var permit = scheduler.tryAcquireObservation(actor).orElseThrow();
        CompletableFuture.runAsync(() -> scheduler.attach(actor)).get(2, TimeUnit.SECONDS);
        assertTrue(scheduler.tryAcquireObservation(actor).isEmpty());

        // The adopted State now owns the ordinary wakeups and control queue.
        scheduler.submitControl(actor, () -> order.add("control"));
        carriers.runAll();
        assertTrue(order.isEmpty());

        permit.close();
        carriers.runAll();
        assertEquals(List.of("control"), order);
        assertThrows(IllegalStateException.class, () -> scheduler.attach(actor));
    }

    @Test
    void detachDuringPermitKeepsExclusionAndReleaseDoesNotResurrect() {
        ManualExecutor carriers = new ManualExecutor();
        ProtosActorScheduler scheduler = new ProtosActorScheduler(carriers, 1);
        ProtosActor actor = actor();
        List<String> order = new ArrayList<>();
        scheduler.attach(actor);
        scheduler.submitControl(actor, () -> order.add("control"));

        var permit = scheduler.tryAcquireObservation(actor).orElseThrow();
        scheduler.detach(actor);
        assertTrue(scheduler.tryAcquireObservation(actor).isEmpty());
        assertThrows(IllegalStateException.class,
                () -> scheduler.submitControl(actor, () -> order.add("late")));

        permit.close();
        carriers.runAll();
        assertTrue(order.isEmpty());
        assertThrows(IllegalStateException.class,
                () -> scheduler.submitControl(actor, () -> order.add("late")));
    }

    @Test
    void terminationDuringPermitDeniesLaterObservers() {
        ManualExecutor carriers = new ManualExecutor();
        ProtosActorScheduler scheduler = new ProtosActorScheduler(carriers, 1);
        ProtosActor actor = actor();
        scheduler.attach(actor);
        actor.markReady();

        var permit = scheduler.tryAcquireObservation(actor).orElseThrow();
        assertTrue(actor.beginTermination());
        assertTrue(scheduler.tryAcquireObservation(actor).isEmpty());
        permit.close();
        carriers.runAll();
        assertTrue(scheduler.tryAcquireObservation(actor).isEmpty());
    }

    @Test
    void failedAttachDuringPermitKeepsExclusionWithoutOrphans() {
        ProtosActorScheduler owner = new ProtosActorScheduler(new ManualExecutor(), 1);
        ProtosActorScheduler other = new ProtosActorScheduler(new ManualExecutor(), 1);
        ProtosActor actor = actor();
        owner.attach(actor);

        var permit = other.tryAcquireObservation(actor).orElseThrow();
        // The wakeups are already bound to owner, so this attach fails part-way.
        assertThrows(IllegalStateException.class, () -> other.attach(actor));
        assertTrue(other.tryAcquireObservation(actor).isEmpty());
        assertThrows(IllegalStateException.class,
                () -> other.submitControl(actor, () -> { }));

        permit.close();
        assertThrows(IllegalStateException.class,
                () -> other.submitControl(actor, () -> { }));
        other.tryAcquireObservation(actor).orElseThrow().close();
    }

    @Test
    void concurrentObserversGetAtMostOnePermit() throws Exception {
        ProtosActorScheduler scheduler = new ProtosActorScheduler(new ManualExecutor(), 1);
        ProtosActor actor = actor();
        scheduler.attach(actor);
        CountDownLatch start = new CountDownLatch(1);

        List<CompletableFuture<Optional<ProtosActorScheduler.ObservationPermit>>> attempts =
                new ArrayList<>();
        for (int index = 0; index < 8; index++) {
            attempts.add(CompletableFuture.supplyAsync(() -> {
                await(start);
                return scheduler.tryAcquireObservation(actor);
            }));
        }
        start.countDown();

        int granted = 0;
        for (var attempt : attempts) {
            var permit = attempt.get(2, TimeUnit.SECONDS);
            if (permit.isPresent()) {
                granted++;
            }
        }
        assertEquals(1, granted);
        for (var attempt : attempts) {
            attempt.get().ifPresent(ProtosActorScheduler.ObservationPermit::close);
        }
        scheduler.tryAcquireObservation(actor).orElseThrow().close();
    }

    private static ProtosActor actor() {
        return new ProtosActor(
                new ProtosObjectValue(ProtosObjectValue.rootObject()).freeze(),
                new ProtosActorExecutionDomain(),
                new ProtosActorModuleState(),
                4);
    }

    private static ProtosTask.Continuation turn(List<String> order, String value) {
        return task -> {
            order.add(value);
            task.complete(null);
        };
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new AssertionError(error);
        }
    }

    private static final class ManualExecutor implements Executor {
        private final ArrayDeque<Runnable> queued = new ArrayDeque<>();

        @Override
        public synchronized void execute(Runnable command) {
            queued.addLast(command);
        }

        void runAll() {
            while (true) {
                Runnable command;
                synchronized (this) {
                    command = queued.pollFirst();
                }
                if (command == null) {
                    return;
                }
                command.run();
            }
        }
    }
}
