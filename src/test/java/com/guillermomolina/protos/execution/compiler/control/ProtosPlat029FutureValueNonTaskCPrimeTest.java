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

import com.guillermomolina.protos.parser.ProtosParser;
import com.guillermomolina.protos.runtime.*;
import com.guillermomolina.protos.semantic.Canonicalizer;
import com.guillermomolina.protos.semantic.ast.CanonicalSequence;
import com.oracle.truffle.api.TruffleLanguage.LanguageReference;
import com.oracle.truffle.api.source.Source;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.graalvm.polyglot.Context;
import org.junit.jupiter.api.Test;

final class ProtosPlat029FutureValueNonTaskCPrimeTest {
    private static final LanguageReference<ProtosLanguage> LANGUAGE_REF =
            LanguageReference.create(ProtosLanguage.class);

    @Test
    void pendingFutureResumesOperationCPrimeWithoutTaskOrInlineGuestReentry() throws Exception {
        try (LanguageScope scope = languageScope()) {
            Fixture f = fixture();
            ProtosIoOperation op = f.lifecycle.beginOperation(f.module);
            ProtosActivation activation = op.deferredCPrimeActivationForRuntime();
            ProtosFutureValue source = new ProtosFutureValue(f.prelude.futurePrototype(), f.domain);
            ProtosObjectValue value = new ProtosObjectValue(ProtosObjectValue.rootObject());
            activation.context().createLocalSlot("source", source);

            install(op, activation, lowerRoot(scope.language, "source.value()", "plat029-future-value-pending.protos"));
            assertTrue(f.domain.dispatchOne());
            assertEquals(ProtosFutureValue.State.PENDING, op.future().state());
            assertEquals(0, f.domain.liveTaskCount());

            assertTrue(source.resolve(value, f.module));
            assertEquals(ProtosFutureValue.State.PENDING, op.future().state(),
                    "Future terminalization must only make the operation runnable");
            assertEquals(0, f.domain.liveTaskCount());

            assertTrue(f.domain.dispatchOne());
            assertEquals(ProtosFutureValue.State.RESOLVED, op.future().state());
            assertSame(value, op.future().resolvedValue().orElseThrow());
            assertEquals(0, f.domain.liveTaskCount());
            assertFalse(f.domain.dispatchOne());
        }
        System.out.println("PLAT029_FUTURE_VALUE_NON_TASK_CPRIME=PASS");
        System.out.println("PLAT029_FUTURE_VALUE_BACKEND_INLINE_GUEST_REENTRY=NO");
        System.out.println("PLAT029_FUTURE_VALUE_HIDDEN_TASK=NO");
    }

    @Test
    void alreadyTerminalFutureCompletesInInitialOperationSegment() throws Exception {
        try (LanguageScope scope = languageScope()) {
            Fixture f = fixture();
            ProtosIoOperation op = f.lifecycle.beginOperation(f.module);
            ProtosActivation activation = op.deferredCPrimeActivationForRuntime();
            ProtosFutureValue source = new ProtosFutureValue(f.prelude.futurePrototype(), f.domain);
            ProtosObjectValue value = new ProtosObjectValue(ProtosObjectValue.rootObject());
            assertTrue(source.resolve(value, f.module));
            activation.context().createLocalSlot("source", source);

            install(op, activation, lowerRoot(scope.language, "source.value()", "plat029-future-value-terminal.protos"));
            assertTrue(f.domain.dispatchOne());
            assertEquals(ProtosFutureValue.State.RESOLVED, op.future().state());
            assertSame(value, op.future().resolvedValue().orElseThrow());
            assertFalse(f.domain.dispatchOne());
            assertEquals(0, f.domain.liveTaskCount());
        }
        System.out.println("PLAT029_FUTURE_VALUE_TERMINAL_FAST_PATH=PASS");
    }

    @Test
    void operationCancellationReleasesPendingFutureObservationWithoutLateResume() throws Exception {
        try (LanguageScope scope = languageScope()) {
            Fixture f = fixture();
            ProtosIoOperation op = f.lifecycle.beginOperation(f.module);
            ProtosActivation activation = op.deferredCPrimeActivationForRuntime();
            ProtosFutureValue source = new ProtosFutureValue(f.prelude.futurePrototype(), f.domain);
            AtomicInteger probe = new AtomicInteger();
            activation.context().createLocalSlot("source", source);
            activation.context().createLocalSlot("probe", ProtosClosureValue.nativeClosure((a,x)->{probe.incrementAndGet();return ProtosNullValue.INSTANCE;}));

            install(op, activation, lowerRoot(scope.language, "source.value()\nprobe()", "plat029-future-value-cancel.protos"));
            assertTrue(f.domain.dispatchOne());
            assertEquals(ProtosFutureValue.State.PENDING, op.future().state());
            assertTrue(op.future().cancelRequest());
            assertEquals(ProtosFutureValue.State.CANCELLED, op.future().state());

            assertTrue(source.resolve(ProtosNullValue.INSTANCE, f.module));
            assertFalse(f.domain.dispatchOne());
            assertEquals(0, probe.get());
            assertEquals(0, f.domain.liveTaskCount());
        }
        System.out.println("PLAT029_FUTURE_VALUE_TERMINAL_RELEASE=PASS");
        System.out.println("PLAT029_FUTURE_VALUE_LATE_RESUME=NO");
    }

    @Test
    void failedFuturePreservesExactErrorThroughOperationCPrimeObservation() throws Exception {
        try (LanguageScope scope = languageScope()) {
            Fixture f = fixture();
            ProtosIoOperation op = f.lifecycle.beginOperation(f.module);
            ProtosActivation activation = op.deferredCPrimeActivationForRuntime();
            ProtosFutureValue source = new ProtosFutureValue(f.prelude.futurePrototype(), f.domain);
            ProtosObjectValue error = ProtosCoreErrors.newError(activation);
            activation.context().createLocalSlot("source", source);

            ProtosBytecodeIoOperationExecution.installAndSchedule(
                    op,
                    lowerRoot(scope.language, "source.value()", "plat029-future-value-failed.protos").getCallTarget(),
                    activation,
                    (current,value)->fail("failed source must not complete operation normally"),
                    (current,failed)->assertTrue(current.fail(failed)));
            assertTrue(f.domain.dispatchOne());
            assertTrue(source.fail(error));
            assertTrue(f.domain.dispatchOne());
            assertEquals(ProtosFutureValue.State.FAILED, op.future().state());
            assertSame(error, op.future().failedError().orElseThrow());
            assertEquals(0, f.domain.liveTaskCount());
        }
        System.out.println("PLAT029_FUTURE_VALUE_FAILED_ERROR_IDENTITY=PASS");
    }

    @Test
    void terminalIdleIsDeliveredOnceForCancellationBeforeFirstDispatch() throws Exception {
        try (LanguageScope scope = languageScope()) {
            Fixture f = fixture();
            ProtosIoOperation op = f.lifecycle.beginOperation(f.module);
            ProtosActivation activation = op.deferredCPrimeActivationForRuntime();
            AtomicInteger probe = probe(activation);
            AtomicInteger idle = new AtomicInteger();

            install(op, activation, lowerRoot(scope.language, "probe()", "bug029-idle-pre-dispatch.protos"));
            op.onDeferredCPrimeTerminalIdleForRuntime(() -> {
                assertEquals(0, f.domain.liveTaskCount());
                idle.incrementAndGet();
            });
            assertEquals(0, idle.get());

            assertTrue(op.future().cancelRequest());
            assertEquals(1, idle.get());
            assertFalse(f.domain.dispatchOne());
            assertEquals(0, probe.get());
            assertEquals(1, idle.get());
            assertEquals(0, f.domain.liveTaskCount());
        }
        System.out.println("BUG029_TERMINAL_IDLE_PRE_DISPATCH_ONCE=PASS");
    }

    @Test
    void terminalIdleRegisteredAfterTerminalityIsDeliveredImmediatelyOnce() throws Exception {
        try (LanguageScope scope = languageScope()) {
            Fixture f = fixture();
            ProtosIoOperation op = f.lifecycle.beginOperation(f.module);
            AtomicInteger idle = new AtomicInteger();
            assertTrue(op.future().cancelRequest());

            op.onDeferredCPrimeTerminalIdleForRuntime(idle::incrementAndGet);
            assertEquals(1, idle.get());
            assertThrows(
                    IllegalStateException.class,
                    () -> op.onDeferredCPrimeTerminalIdleForRuntime(idle::incrementAndGet));
            assertEquals(1, idle.get());
        }
        System.out.println("BUG029_TERMINAL_IDLE_LATE_REGISTRATION_ONCE=PASS");
    }

    @Test
    void terminalIdleFollowsCancellationOfSuspendedCPrimeWithoutGuestResume() throws Exception {
        try (LanguageScope scope = languageScope()) {
            Fixture f = fixture();
            ProtosIoOperation op = f.lifecycle.beginOperation(f.module);
            ProtosActivation activation = op.deferredCPrimeActivationForRuntime();
            ProtosFutureValue source = new ProtosFutureValue(f.prelude.futurePrototype(), f.domain);
            AtomicInteger probe = probe(activation);
            AtomicInteger idle = new AtomicInteger();
            activation.context().createLocalSlot("source", source);

            install(op, activation, lowerRoot(scope.language, "source.value()\nprobe()", "bug029-idle-suspended.protos"));
            op.onDeferredCPrimeTerminalIdleForRuntime(idle::incrementAndGet);
            assertTrue(f.domain.dispatchOne());
            assertEquals(ProtosFutureValue.State.PENDING, op.future().state());
            assertEquals(0, idle.get());

            assertTrue(op.future().cancelRequest());
            assertEquals(1, idle.get());
            assertTrue(source.resolve(ProtosNullValue.INSTANCE, f.module));
            assertFalse(f.domain.dispatchOne());
            assertEquals(0, probe.get());
            assertEquals(1, idle.get());
            assertEquals(0, f.domain.liveTaskCount());
        }
        System.out.println("BUG029_TERMINAL_IDLE_SUSPENDED_RELEASE=PASS");
    }

    @Test
    void terminalIdleWaitsForRunningCPrimeSegmentToExit() throws Exception {
        try (LanguageScope scope = languageScope()) {
            Fixture f = fixture();
            ProtosIoOperation op = f.lifecycle.beginOperation(f.module);
            ProtosActivation activation = op.deferredCPrimeActivationForRuntime();
            AtomicInteger idle = new AtomicInteger();
            AtomicInteger idleSeenInsideSegment = new AtomicInteger(-1);
            activation.context().createLocalSlot("cancelSelf", ProtosClosureValue.nativeClosure((a,x)->{
                assertTrue(op.future().cancelRequest());
                idleSeenInsideSegment.set(idle.get());
                return ProtosNullValue.INSTANCE;
            }));

            ProtosBytecodeIoOperationExecution.installAndSchedule(
                    op,
                    lowerRoot(scope.language, "cancelSelf()", "bug029-idle-running.protos").getCallTarget(),
                    activation,
                    (current,value)->assertFalse(current.commit()),
                    (current,error)->assertFalse(current.fail(error)));
            op.onDeferredCPrimeTerminalIdleForRuntime(idle::incrementAndGet);
            assertTrue(f.domain.dispatchOne());

            assertEquals(0, idleSeenInsideSegment.get(),
                    "terminal-idle must not be delivered while the C-prime segment is running");
            assertEquals(ProtosFutureValue.State.CANCELLED, op.future().state());
            assertEquals(1, idle.get());
            assertFalse(f.domain.dispatchOne());
            assertEquals(0, f.domain.liveTaskCount());
        }
        System.out.println("BUG029_TERMINAL_IDLE_AFTER_RUNNING_SEGMENT=PASS");
    }

    @Test
    void repeatedCancellationAndTerminalRequestsDeliverNoAdditionalNotification() throws Exception {
        try (LanguageScope scope = languageScope()) {
            Fixture f = fixture();
            ProtosIoOperation op = f.lifecycle.beginOperation(f.module);
            AtomicInteger idle = new AtomicInteger();
            op.onDeferredCPrimeTerminalIdleForRuntime(idle::incrementAndGet);

            assertTrue(op.future().cancelRequest());
            assertFalse(op.requestCancellation());
            op.future().cancelRequest();
            assertFalse(op.fail(ProtosCoreErrors.newError(f.module)));
            assertFalse(op.resolve(ProtosNullValue.INSTANCE));
            assertEquals(ProtosFutureValue.State.CANCELLED, op.future().state());
            assertEquals(1, idle.get());
        }
        System.out.println("BUG029_TERMINAL_IDLE_NO_DUPLICATE=PASS");
    }

    @Test
    void terminalIdleDoesNotAlterNormalCompletionOrCommitment() throws Exception {
        try (LanguageScope scope = languageScope()) {
            Fixture f = fixture();
            ProtosIoOperation op = f.lifecycle.beginOperation(f.module);
            ProtosActivation activation = op.deferredCPrimeActivationForRuntime();
            ProtosFutureValue source = new ProtosFutureValue(f.prelude.futurePrototype(), f.domain);
            ProtosObjectValue value = new ProtosObjectValue(ProtosObjectValue.rootObject());
            AtomicInteger idle = new AtomicInteger();
            activation.context().createLocalSlot("source", source);

            install(op, activation, lowerRoot(scope.language, "source.value()", "bug029-idle-normal.protos"));
            op.onDeferredCPrimeTerminalIdleForRuntime(() -> {
                assertEquals(ProtosFutureValue.State.RESOLVED, op.future().state());
                assertEquals(0, f.domain.liveTaskCount());
                idle.incrementAndGet();
            });
            assertTrue(f.domain.dispatchOne());
            assertEquals(0, idle.get());
            assertTrue(source.resolve(value, f.module));
            assertEquals(0, idle.get());

            assertTrue(f.domain.dispatchOne());
            assertEquals(ProtosFutureValue.State.RESOLVED, op.future().state());
            assertSame(value, op.future().resolvedValue().orElseThrow());
            assertTrue(op.terminal());
            assertFalse(op.requestCancellation());
            assertEquals(1, idle.get());
            assertFalse(f.domain.dispatchOne());
            assertEquals(0, f.domain.liveTaskCount());
        }
        System.out.println("BUG029_TERMINAL_IDLE_NORMAL_COMPLETION=PASS");
    }

    @Test
    void terminalIdleCallbackFailureStillRetiresOperationFromLifecycle() throws Exception {
        try (LanguageScope scope = languageScope()) {
            Fixture f = fixture();
            ProtosIoOperation op = f.lifecycle.beginOperation(f.module);
            AtomicInteger idle = new AtomicInteger();
            IllegalStateException failure =
                    new IllegalStateException("BUG029 deliberate terminal-idle failure");
            op.onDeferredCPrimeTerminalIdleForRuntime(() -> {
                idle.incrementAndGet();
                throw failure;
            });

            IllegalStateException propagated =
                    assertThrows(IllegalStateException.class, op::requestCancellation);
            assertSame(failure, propagated);
            assertEquals(ProtosFutureValue.State.CANCELLED, op.future().state());

            ProtosFutureValue close = f.lifecycle.close(f.module);
            assertEquals(ProtosFutureValue.State.RESOLVED, close.state(),
                    "a failing retirement callback must not leave the operation registered");
            assertEquals(1, idle.get());
        }
        System.out.println("BUG029_TERMINAL_IDLE_FAILURE_STILL_RETIRES=PASS");
    }

    @Test
    void terminalIdleWaitsForConcurrentRunningSegmentToExit() throws Exception {
        try (LanguageScope scope = languageScope()) {
            Fixture f = fixture();
            ProtosIoOperation op = f.lifecycle.beginOperation(f.module);
            CountDownLatch segmentStarted = new CountDownLatch(1);
            CountDownLatch allowSegmentExit = new CountDownLatch(1);
            AtomicInteger segments = new AtomicInteger();
            AtomicInteger idle = new AtomicInteger();
            AtomicInteger idleSeenAtSegmentEnd = new AtomicInteger(-1);
            AtomicReference<Throwable> dispatcherFailure = new AtomicReference<>();
            AtomicBoolean dispatched = new AtomicBoolean();

            op.installDeferredCPrimeExecutionForRuntime(current -> {
                segments.incrementAndGet();
                segmentStarted.countDown();
                try {
                    assertTrue(allowSegmentExit.await(30, TimeUnit.SECONDS),
                            "test guard: segment exit was never authorized");
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(interrupted);
                }
                idleSeenAtSegmentEnd.set(idle.get());
            });
            assertTrue(op.requestDeferredCPrimeRunForRuntime());

            Thread dispatcher = new Thread(() -> {
                try {
                    dispatched.set(f.domain.dispatchOne());
                } catch (Throwable thrown) {
                    dispatcherFailure.set(thrown);
                } finally {
                    segmentStarted.countDown();
                }
            }, "bug029-cprime-dispatcher");
            dispatcher.start();
            try {
                assertTrue(segmentStarted.await(30, TimeUnit.SECONDS),
                        "test guard: C-prime segment never started");
                assertNull(dispatcherFailure.get());
                op.onDeferredCPrimeTerminalIdleForRuntime(idle::incrementAndGet);

                assertTrue(op.future().cancelRequest());
                assertEquals(ProtosFutureValue.State.CANCELLED, op.future().state());
                assertEquals(0, idle.get(),
                        "terminal-idle must wait for the running C-prime segment to exit");
            } finally {
                allowSegmentExit.countDown();
                dispatcher.join(TimeUnit.SECONDS.toMillis(30));
            }
            assertFalse(dispatcher.isAlive(), "test guard: dispatcher thread did not finish");
            assertNull(dispatcherFailure.get());
            assertTrue(dispatched.get());

            assertEquals(1, segments.get());
            assertEquals(0, idleSeenAtSegmentEnd.get());
            assertEquals(1, idle.get());
            assertFalse(f.domain.dispatchOne());
            assertEquals(1, segments.get());
            assertEquals(1, idle.get());
            assertEquals(0, f.domain.liveTaskCount());
        }
        System.out.println("BUG029_TERMINAL_IDLE_CONCURRENT_SEGMENT_EXIT=PASS");
    }

    private static AtomicInteger probe(ProtosActivation activation) {
        AtomicInteger probe = new AtomicInteger();
        activation.context().createLocalSlot("probe", ProtosClosureValue.nativeClosure((a,x)->{probe.incrementAndGet();return ProtosNullValue.INSTANCE;}));
        return probe;
    }

    private static void install(ProtosIoOperation op, ProtosActivation activation, ProtosBytecodeRootNode root) {
        ProtosBytecodeIoOperationExecution.installAndSchedule(
                op, root.getCallTarget(), activation,
                (current,value)->{assertTrue(current.commit());assertTrue(current.resolve(value));},
                (current,error)->{assertTrue(current.fail(error));});
    }

    private static Fixture fixture() throws Exception {
        ProtosPrelude p = new ProtosCoreBootstrap().bootstrap(Path.of("protos","lib","core"));
        ProtosActorExecutionDomain d = new ProtosActorExecutionDomain();
        ProtosActivation m = p.newModuleActivation(new ProtosActorModuleState(),null,p.newExecutionContext(),d);
        ProtosObjectValue receiver = new ProtosObjectValue(ProtosObjectValue.rootObject());
        ProtosIoLifecycle lifecycle = new ProtosIoLifecycle(receiver,p.futurePrototype(),d,c->c.succeeded());
        return new Fixture(p,d,m,lifecycle);
    }

    private static ProtosBytecodeRootNode lowerRoot(ProtosLanguage language,String chars,String name) throws Exception {
        Source source=Source.newBuilder(ProtosLanguage.ID,chars,name).build();
        return new CanonicalToBytecodeLowerer(language,source).lowerRoot(
                (CanonicalSequence)new Canonicalizer().canonicalize(new ProtosParser(chars).parseProgram()));
    }

    private static LanguageScope languageScope() {
        Context c=Context.newBuilder(ProtosLanguage.ID).build();c.initialize(ProtosLanguage.ID);c.enter();
        return new LanguageScope(c,LANGUAGE_REF.get(null));
    }
    private record Fixture(ProtosPrelude prelude,ProtosActorExecutionDomain domain,ProtosActivation module,ProtosIoLifecycle lifecycle) {}
    private record LanguageScope(Context context,ProtosLanguage language) implements AutoCloseable {
        @Override public void close(){context.leave();context.close();}
    }
}
