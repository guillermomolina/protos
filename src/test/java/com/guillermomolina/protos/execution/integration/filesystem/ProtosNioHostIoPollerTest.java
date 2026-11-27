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

import java.nio.ByteBuffer;
import java.nio.channels.Pipe;
import java.nio.channels.SelectionKey;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ProtosNioHostIoPollerTest {
    @Test void registeredTcpRetirementUsesTheConnectionsRecordedPhysicalOutcome() throws Exception {
        var poller=new ProtosNioHostIoPoller("tcp-poller-retirement");
        var channel=java.nio.channels.SocketChannel.open();
        var backend=new ProtosNioTcpConnectionBackend(poller,channel);
        var registered=new CountDownLatch(1);
        try {
            poller.submit(()->{
                try{backend.attachKey(poller.register(channel,0,backend));}
                catch(java.io.IOException e){throw new java.io.UncheckedIOException(e);}
                finally{registered.countDown();}
            },e->{registered.countDown();throw new AssertionError(e);});
            assertTrue(registered.await(5,TimeUnit.SECONDS));
            poller.close();assertFalse(channel.isOpen());
            var successes=new AtomicInteger();var failures=new AtomicInteger();
            backend.close(new com.guillermomolina.protos.runtime.ProtosByteIoFlow.ReceiverCompletion(){
                public void succeeded(){successes.incrementAndGet();}
                public void failed(){failures.incrementAndGet();}
            });
            assertEquals(1,successes.get());assertEquals(0,failures.get());
        }finally{poller.close();channel.close();}
    }

    @Test void duplicatePhysicalRequestsCannotOvertakeAnInProgressFailedClose() throws Exception {
        try(var x=new ProtosNioTcpConnectionWriteTest.ControlledFixture(0,0,false)) {
            x.channel.holdClose=true;x.channel.failClose=true;
            var successes=new AtomicInteger();var failures=new AtomicInteger();
            var completion=new com.guillermomolina.protos.runtime.ProtosByteIoFlow.ReceiverCompletion(){
                public void succeeded(){successes.incrementAndGet();}
                public void failed(){failures.incrementAndGet();}
            };
            x.backend.close(completion);
            assertTrue(x.channel.closeEntered.await(5,TimeUnit.SECONDS));
            x.backend.close(completion);assertEquals(0,successes.get());assertEquals(0,failures.get());
            x.channel.closeProceed.countDown();x.barrier();
            assertEquals(0,successes.get());assertEquals(2,failures.get());assertEquals(1,x.channel.closeCalls);
            x.backend.close(completion);x.barrier();
            assertEquals(0,successes.get());assertEquals(3,failures.get());assertEquals(1,x.channel.closeCalls);
            x.poller.close();x.backend.close(completion);
            assertEquals(0,successes.get());assertEquals(4,failures.get());assertEquals(1,x.channel.closeCalls);
        }
    }

    @Test void retirementFallsBackWhenPollerNoLongerAcceptsCommands() throws Exception {
        try(var x=new ProtosNioTcpConnectionWriteTest.ControlledFixture(0,0,false)) {
            x.poller.close();
            var successes=new AtomicInteger();var failures=new AtomicInteger();
            x.backend.close(new com.guillermomolina.protos.runtime.ProtosByteIoFlow.ReceiverCompletion(){
                public void succeeded(){successes.incrementAndGet();}
                public void failed(){failures.incrementAndGet();}
            });
            assertEquals(1,successes.get());assertEquals(0,failures.get());
            assertEquals(1,x.channel.closeCalls);assertFalse(x.key.isValid());
        }
    }

    @Test void pendingPhysicalRetirementAndFollowersSurvivePollerTermination() throws Exception {
        try(var x=new ProtosNioTcpConnectionWriteTest.ControlledFixture(0,0,false)) {
            x.channel.holdClose=true;
            var successes=new AtomicInteger();var failures=new AtomicInteger();
            var completion=new com.guillermomolina.protos.runtime.ProtosByteIoFlow.ReceiverCompletion(){
                public void succeeded(){successes.incrementAndGet();}
                public void failed(){failures.incrementAndGet();}
            };
            var commandFailure=new AtomicReference<RuntimeException>();
            // Stop admission on the poller itself, then hold its physical release.
            x.poller.submit(()->{x.poller.close();x.backend.close(completion);},commandFailure::set);
            assertTrue(x.channel.closeEntered.await(5,TimeUnit.SECONDS));
            assertThrows(IllegalStateException.class,()->x.poller.submit(()->{},ignored->{}));
            ExecutorService followers=Executors.newFixedThreadPool(2);
            var entered=new CountDownLatch(2);
            try {
                var first=followers.submit(()->{entered.countDown();x.backend.close(completion);});
                var second=followers.submit(()->{entered.countDown();x.backend.close(completion);});
                assertTrue(entered.await(5,TimeUnit.SECONDS));
                assertEquals(0,successes.get());assertEquals(0,failures.get());
                assertFalse(x.poller.isTerminatedForTesting());
                x.channel.closeProceed.countDown();
                first.get(5,TimeUnit.SECONDS);second.get(5,TimeUnit.SECONDS);
                x.poller.close();
                assertNull(commandFailure.get());assertTrue(x.poller.isTerminatedForTesting());
                assertEquals(3,successes.get());assertEquals(0,failures.get());
                assertEquals(1,x.channel.closeCalls);
            } finally {
                x.channel.closeProceed.countDown();followers.shutdown();
                assertTrue(followers.awaitTermination(5,TimeUnit.SECONDS));
            }
        }
    }

    @Test void lateKeyAttachmentCannotRestoreInterestOnRetiredBackend() throws Exception {
        try(var x=new ProtosNioTcpConnectionWriteTest.ControlledFixture(0,0,false)) {
            // A key acquired before retirement can arrive at attachKey after release.
            var late=new ProtosNioTcpConnectionBackend(x.poller,x.channel);
            late.releaseIfUntransferred();x.barrier();
            assertTrue(x.key.isValid());
            x.key.interestOps(SelectionKey.OP_READ|SelectionKey.OP_WRITE);
            x.key.attach(late);
            late.attachKey(x.key);
            assertFalse(x.key.isValid());assertNull(x.key.attachment());
            var refusal=new AtomicReference<RuntimeException>();
            x.poller.submit(()->x.poller.interestOps(x.key,SelectionKey.OP_READ|SelectionKey.OP_WRITE),refusal::set);
            x.barrier();assertInstanceOf(java.nio.channels.CancelledKeyException.class,refusal.get());
            assertEquals(0,x.channel.writeCalls);assertEquals(1,x.channel.closeCalls);
        }
    }

    @Test void selectorFailureNotificationDoesNotInventAPhysicalCloseFailure() throws Exception {
        try(var x=new ProtosNioTcpConnectionWriteTest.ControlledFixture(0,0,false)) {
            var commandFailure=new AtomicReference<RuntimeException>();
            x.poller.submit(()->x.backend.failed(new java.io.IOException("selector failure")),commandFailure::set);
            x.barrier();assertNull(commandFailure.get());
            var successes=new AtomicInteger();var failures=new AtomicInteger();
            x.backend.close(new com.guillermomolina.protos.runtime.ProtosByteIoFlow.ReceiverCompletion(){
                public void succeeded(){successes.incrementAndGet();}
                public void failed(){failures.incrementAndGet();}
            });
            x.barrier();
            assertEquals(1,successes.get());assertEquals(0,failures.get());
            assertEquals(1,x.channel.closeCalls);
        }
    }

    @Test
    void controlRunsOnDedicatedPlatformThreadAndWakesBlockedSelector() throws Exception {
        Thread caller = Thread.currentThread();
        try (ProtosNioHostIoPoller poller = new ProtosNioHostIoPoller("protos-nio-e1-control")) {
            CountDownLatch ran = new CountDownLatch(1);
            AtomicReference<Thread> observed = new AtomicReference<>();
            AtomicReference<RuntimeException> failure = new AtomicReference<>();

            poller.submit(
                    () -> {
                        observed.set(Thread.currentThread());
                        ran.countDown();
                    },
                    failure::set);

            assertTrue(ran.await(2, TimeUnit.SECONDS));
            assertNull(failure.get());
            assertNotSame(caller, observed.get());
            assertFalse(observed.get().isVirtual());
            assertEquals("protos-nio-e1-control", observed.get().getName());
        }
    }

    @Test
    void selectorDispatchesRealReadinessOnOwningPoller() throws Exception {
        try (ProtosNioHostIoPoller poller = new ProtosNioHostIoPoller("protos-nio-e1-ready")) {
            Pipe pipe = Pipe.open();
            try {
                CountDownLatch registered = new CountDownLatch(1);
                CountDownLatch ready = new CountDownLatch(1);
                AtomicReference<Exception> failure = new AtomicReference<>();
                AtomicReference<Thread> readyThread = new AtomicReference<>();

                poller.submit(
                        () -> {
                            try {
                                poller.register(
                                        pipe.source(),
                                        SelectionKey.OP_READ,
                                        new ProtosNioHostIoPoller.SelectionHandler() {
                                            @Override
                                            public void ready(SelectionKey key) throws Exception {
                                                readyThread.set(Thread.currentThread());
                                                ByteBuffer one = ByteBuffer.allocate(1);
                                                int read = ((Pipe.SourceChannel) key.channel()).read(one);
                                                if (read != 1) {
                                                    throw new IllegalStateException(
                                                            "expected one ready byte, got " + read);
                                                }
                                                key.interestOps(0);
                                                ready.countDown();
                                            }

                                            @Override
                                            public void failed(Exception problem) {
                                                failure.compareAndSet(null, problem);
                                                ready.countDown();
                                            }
                                        });
                            } catch (Exception problem) {
                                throw new IllegalStateException(problem);
                            }
                            registered.countDown();
                        },
                        problem -> {
                            failure.compareAndSet(null, problem);
                            registered.countDown();
                        });

                assertTrue(registered.await(2, TimeUnit.SECONDS));
                assertNull(failure.get());
                pipe.sink().write(ByteBuffer.wrap(new byte[] {0x2a}));
                assertTrue(ready.await(2, TimeUnit.SECONDS));
                assertNull(failure.get());
                assertNotNull(readyThread.get());
                assertFalse(readyThread.get().isVirtual());
            } finally {
                pipe.sink().close();
            }
        }
    }

    @Test
    void concurrentControlSubmissionsExecuteExactlyOnce() throws Exception {
        int count = 256;
        try (ProtosNioHostIoPoller poller = new ProtosNioHostIoPoller("protos-nio-e1-many")) {
            ExecutorService submitters = Executors.newFixedThreadPool(8);
            CountDownLatch ran = new CountDownLatch(count);
            AtomicInteger executions = new AtomicInteger();
            List<RuntimeException> failures = java.util.Collections.synchronizedList(new ArrayList<>());
            try {
                for (int i = 0; i < count; i++) {
                    submitters.submit(
                            () ->
                                    poller.submit(
                                            () -> {
                                                executions.incrementAndGet();
                                                ran.countDown();
                                            },
                                            failures::add));
                }
            } finally {
                submitters.shutdown();
            }

            assertTrue(submitters.awaitTermination(2, TimeUnit.SECONDS));
            assertTrue(ran.await(2, TimeUnit.SECONDS));
            assertEquals(count, executions.get());
            assertTrue(failures.isEmpty());
        }
    }

    @Test
    void commandFailureIsReportedWithoutKillingSharedPoller() throws Exception {
        try (ProtosNioHostIoPoller poller = new ProtosNioHostIoPoller("protos-nio-e1-failure")) {
            CountDownLatch reported = new CountDownLatch(1);
            AtomicReference<RuntimeException> failure = new AtomicReference<>();
            poller.submit(
                    () -> {
                        throw new IllegalStateException("expected");
                    },
                    problem -> {
                        failure.set(problem);
                        reported.countDown();
                    });

            assertTrue(reported.await(2, TimeUnit.SECONDS));
            assertInstanceOf(IllegalStateException.class, failure.get());

            CountDownLatch later = new CountDownLatch(1);
            poller.submit(later::countDown, ignored -> fail("later command unexpectedly failed"));
            assertTrue(later.await(2, TimeUnit.SECONDS));
        }
    }

    @Test
    void closeIsIdempotentRejectsNewWorkAndReleasesRegisteredChannels() throws Exception {
        ProtosNioHostIoPoller poller = new ProtosNioHostIoPoller("protos-nio-e1-close");
        Pipe pipe = Pipe.open();
        CountDownLatch registered = new CountDownLatch(1);
        CountDownLatch closed = new CountDownLatch(1);
        AtomicReference<RuntimeException> registrationFailure = new AtomicReference<>();
        try {
            poller.submit(
                    () -> {
                        try {
                            poller.register(
                                    pipe.source(),
                                    SelectionKey.OP_READ,
                                    new ProtosNioHostIoPoller.SelectionHandler() {
                                        @Override
                                        public void ready(SelectionKey key) {}

                                        @Override
                                        public void failed(Exception failure) {
                                            closed.countDown();
                                        }

                                        @Override
                                        public void closed() {
                                            closed.countDown();
                                        }
                                    });
                        } catch (Exception failure) {
                            throw new IllegalStateException(failure);
                        }
                        registered.countDown();
                    },
                    failure -> {
                        registrationFailure.compareAndSet(null, failure);
                        registered.countDown();
                    });

            assertTrue(registered.await(2, TimeUnit.SECONDS));
            assertNull(registrationFailure.get());
            assertTrue(pipe.source().isOpen());

            assertTimeoutPreemptively(Duration.ofSeconds(2), poller::close);
            assertTrue(closed.await(2, TimeUnit.SECONDS));
            assertFalse(pipe.source().isOpen());
            assertTrue(poller.isTerminatedForTesting());
            assertDoesNotThrow(poller::close);
            assertThrows(
                    IllegalStateException.class,
                    () -> poller.submit(() -> {}, ignored -> {}));
        } finally {
            poller.close();
            pipe.sink().close();
            pipe.source().close();
        }
    }
}
