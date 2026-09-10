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

package com.guillermomolina.protos.execution;

import static org.junit.jupiter.api.Assertions.*;

import com.guillermomolina.protos.runtime.ProtosActor;
import com.guillermomolina.protos.runtime.ProtosActorScheduler;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosProcessRuntime;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

final class ProtosActorPolyglotContextRoutingTest {
    @Test
    void distinctActorCarriersEnterTheSameOwningProcessContext() throws Exception {
        ProtosObjectValue actorRefPrototype =
                new ProtosObjectValue(ProtosObjectValue.rootObject()).freeze();
        ProtosProcessRuntime process = new ProtosProcessRuntime(actorRefPrototype);

        try (ProtosPolyglotRuntimeHost host = ProtosPolyglotRuntimeHost.open()) {
            ProtosPolyglotProcessContext hosted =
                    host.hostProcess(
                            process,
                            InputStream.nullInputStream(),
                            OutputStream.nullOutputStream(),
                            OutputStream.nullOutputStream());

            try (ExecutorService carriers = Executors.newFixedThreadPool(2)) {
                ProtosActorScheduler scheduler = new ProtosActorScheduler(carriers, 2);
                ProtosActor first = process.rootActorForRuntime();
                ProtosActor second = process.createHostedActorForRuntime(actorRefPrototype);
                scheduler.attach(first);
                scheduler.attach(second);

                CountDownLatch entered = new CountDownLatch(2);
                CountDownLatch release = new CountDownLatch(1);
                CountDownLatch completed = new CountDownLatch(2);
                AtomicReference<ProtosLanguageContext> firstContext = new AtomicReference<>();
                AtomicReference<ProtosLanguageContext> secondContext = new AtomicReference<>();
                AtomicLong firstThread = new AtomicLong();
                AtomicLong secondThread = new AtomicLong();

                scheduler.submitControl(
                        first,
                        capture(
                                firstContext,
                                firstThread,
                                entered,
                                release,
                                completed));
                scheduler.submitControl(
                        second,
                        capture(
                                secondContext,
                                secondThread,
                                entered,
                                release,
                                completed));

                try {
                    assertTrue(
                            entered.await(5, TimeUnit.SECONDS),
                            "both Actor carriers must enter their Process Context concurrently");
                    assertNotEquals(
                            firstThread.get(),
                            secondThread.get(),
                            "distinct Actors must be able to progress on distinct carriers");
                    assertSame(
                            firstContext.get(),
                            secondContext.get(),
                            "Actors in one Process must observe one exact Truffle language Context");
                } finally {
                    release.countDown();
                }
                assertTrue(
                        completed.await(5, TimeUnit.SECONDS),
                        "both Actor carrier segments must leave before lifecycle cleanup");
            }

            assertTrue(process.requestTerminationForRuntime());
            assertEquals(
                    ProtosProcessRuntime.LifecycleState.TERMINATED,
                    process.lifecycleState());
            assertTrue(
                    hosted.isClosedForTesting(),
                    "semantic Process termination must close its hosted Context after carriers leave");
        }
    }

    @Test
    void runtimeHostOwnsOnePlatformActorSchedulerAcrossHostedProcesses() throws Exception {
        ProtosObjectValue actorRefPrototype =
                new ProtosObjectValue(ProtosObjectValue.rootObject()).freeze();
        ProtosProcessRuntime firstProcess = new ProtosProcessRuntime(actorRefPrototype);
        ProtosProcessRuntime secondProcess = new ProtosProcessRuntime(actorRefPrototype);

        try (ProtosPolyglotRuntimeHost host = ProtosPolyglotRuntimeHost.open()) {
            assertFalse(
                    host.actorCarrierSubstrateInitializedForTesting(),
                    "RuntimeHost must not allocate Actor carrier capacity before it is used");

            ProtosPolyglotProcessContext firstHosted =
                    host.hostProcess(
                            firstProcess,
                            InputStream.nullInputStream(),
                            OutputStream.nullOutputStream(),
                            OutputStream.nullOutputStream());
            ProtosPolyglotProcessContext secondHosted =
                    host.hostProcess(
                            secondProcess,
                            InputStream.nullInputStream(),
                            OutputStream.nullOutputStream(),
                            OutputStream.nullOutputStream());

            assertFalse(
                    host.actorCarrierSubstrateInitializedForTesting(),
                    "hosting Processes alone must not allocate Actor carrier threads");

            ProtosActorScheduler firstScheduler =
                    firstHosted.actorSchedulerForRuntime().orElseThrow();
            ProtosActorScheduler secondScheduler =
                    secondHosted.actorSchedulerForRuntime().orElseThrow();

            assertSame(
                    firstScheduler,
                    secondScheduler,
                    "Processes on one RuntimeHost must share one Actor scheduler");
            assertTrue(host.actorCarrierSubstrateInitializedForTesting());
            assertEquals(
                    Math.max(1, Runtime.getRuntime().availableProcessors()),
                    host.actorCarrierParallelismForTesting());

            ProtosLanguageContext expectedFirst =
                    firstHosted.currentLanguageContextForTesting();
            ProtosLanguageContext expectedSecond =
                    secondHosted.currentLanguageContextForTesting();
            assertNotSame(
                    expectedFirst,
                    expectedSecond,
                    "shared carriers must not collapse distinct Process Contexts");

            ProtosActor firstActor = firstProcess.rootActorForRuntime();
            ProtosActor secondActor = secondProcess.rootActorForRuntime();
            firstScheduler.attach(firstActor);
            firstScheduler.attach(secondActor);

            CountDownLatch completed = new CountDownLatch(2);
            AtomicReference<ProtosLanguageContext> observedFirst = new AtomicReference<>();
            AtomicReference<ProtosLanguageContext> observedSecond = new AtomicReference<>();
            AtomicReference<Thread> firstCarrier = new AtomicReference<>();
            AtomicReference<Thread> secondCarrier = new AtomicReference<>();

            firstScheduler.submitControl(
                    firstActor,
                    () -> {
                        observedFirst.set(ProtosLanguageContext.current());
                        firstCarrier.set(Thread.currentThread());
                        completed.countDown();
                    });
            firstScheduler.submitControl(
                    secondActor,
                    () -> {
                        observedSecond.set(ProtosLanguageContext.current());
                        secondCarrier.set(Thread.currentThread());
                        completed.countDown();
                    });

            assertTrue(
                    completed.await(5, TimeUnit.SECONDS),
                    "both hosted Processes must make progress on shared Actor capacity");
            assertSame(expectedFirst, observedFirst.get());
            assertSame(expectedSecond, observedSecond.get());
            assertNotNull(firstCarrier.get());
            assertNotNull(secondCarrier.get());
            assertFalse(firstCarrier.get().isVirtual(), "Actor carrier must be a platform thread");
            assertFalse(secondCarrier.get().isVirtual(), "Actor carrier must be a platform thread");
            assertTrue(firstCarrier.get().getName().startsWith("protos-actor-carrier-"));
            assertTrue(secondCarrier.get().getName().startsWith("protos-actor-carrier-"));

            firstScheduler.detach(firstActor);
            firstScheduler.detach(secondActor);

            assertTrue(firstProcess.requestTerminationForRuntime());
            assertTrue(secondProcess.requestTerminationForRuntime());
            assertEquals(
                    ProtosProcessRuntime.LifecycleState.TERMINATED,
                    firstProcess.lifecycleState());
            assertEquals(
                    ProtosProcessRuntime.LifecycleState.TERMINATED,
                    secondProcess.lifecycleState());
            assertTrue(firstHosted.isClosedForTesting());
            assertTrue(secondHosted.isClosedForTesting());
        }
    }

    private static Runnable capture(
            AtomicReference<ProtosLanguageContext> context,
            AtomicLong thread,
            CountDownLatch entered,
            CountDownLatch release,
            CountDownLatch completed) {
        return () -> {
            try {
                context.set(ProtosLanguageContext.current());
                thread.set(Thread.currentThread().threadId());
                entered.countDown();
                await(release);
            } finally {
                completed.countDown();
            }
        };
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new AssertionError(failure);
        }
    }
}
