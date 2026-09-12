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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.runtime.ProtosActorScheduler;
import com.guillermomolina.protos.runtime.ProtosEnvironmentValue;
import com.guillermomolina.protos.runtime.ProtosIntegerValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosProcessExecutionHost;
import com.guillermomolina.protos.runtime.ProtosProcessRuntime;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

final class ProtosTestToolI8D4ATerminalAttemptEnvelopeTest {
    private static final java.nio.file.Path CORE =
            java.nio.file.Path.of("protos", "lib", "core");

    @Test
    void attemptDoesNotCompleteBeforeExecutionHostAndProviderCleanupAreTerminal()
            throws Exception {
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
        ProtosProcessRuntime process =
                new ProtosProcessRuntime(prelude.actorRefPrototypeForRuntime());
        BlockingExecutionHost host = new BlockingExecutionHost();
        process.bindExecutionHostForRuntime(host);

        AtomicInteger cleanupCalls = new AtomicInteger();
        CompletableFuture<Void> cleanupTerminal = new CompletableFuture<>();
        ProtosTestResourceProviderTransaction transaction =
                transaction(cleanupCalls, cleanupTerminal);
        ProtosCapturedProcessExecution.Result guest =
                guest(new ProtosIntegerValue(BigInteger.valueOf(42)));

        CompletableFuture<ProtosTestResourceAttemptCompletion> terminal =
                CompletableFuture.supplyAsync(
                        () -> ProtosTestResourceAttemptTerminalizer
                                .finishStarted(process, guest, transaction)
                                .toCompletableFuture()
                                .join());

        assertTrue(host.terminationNotified.await(5, TimeUnit.SECONDS));
        assertFalse(terminal.isDone());
        assertEquals(0, cleanupCalls.get());

        host.releaseTerminalDisposition.countDown();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (cleanupCalls.get() == 0 && System.nanoTime() < deadline) {
            Thread.sleep(1);
        }
        assertEquals(1, cleanupCalls.get());
        assertFalse(terminal.isDone());

        cleanupTerminal.complete(null);
        ProtosTestResourceAttemptCompletion completion =
                terminal.get(5, TimeUnit.SECONDS);

        assertSame(guest, completion.guestObservation().orElseThrow());
        assertFalse(completion.infrastructureFailed());
        assertEquals(
                ProtosTestResourceAttemptCompletion.CapacityDisposition.SAFE,
                completion.capacityDisposition());
        assertEquals(
                ProtosProcessRuntime.LifecycleState.TERMINATED,
                process.lifecycleState());
    }

    @Test
    void executionHostFailureStillRunsProviderCleanupAndMakesCapacityUnsafe()
            throws Exception {
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
        ProtosProcessRuntime process =
                new ProtosProcessRuntime(prelude.actorRefPrototypeForRuntime());
        RuntimeException hostFailure = new RuntimeException("context-close-failed");
        process.bindExecutionHostForRuntime(new FailingExecutionHost(hostFailure));

        AtomicInteger cleanupCalls = new AtomicInteger();
        ProtosCapturedProcessExecution.Result guest =
                guest(new ProtosIntegerValue(BigInteger.ONE));

        ProtosTestResourceAttemptCompletion completion =
                ProtosTestResourceAttemptTerminalizer
                        .finishStarted(
                                process,
                                guest,
                                transaction(
                                        cleanupCalls,
                                        CompletableFuture.completedFuture(null)))
                        .toCompletableFuture()
                        .join();

        assertEquals(1, cleanupCalls.get());
        assertSame(guest, completion.guestObservation().orElseThrow());
        assertEquals(1, completion.infrastructureFailures().size());
        assertSame(hostFailure, completion.infrastructureFailures().get(0));
        assertEquals(
                ProtosTestResourceAttemptCompletion.CapacityDisposition.UNSAFE,
                completion.capacityDisposition());
    }

    @Test
    void providerCleanupFailurePreservesGuestObservationAndMarksUnsafe()
            throws Exception {
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
        ProtosProcessRuntime process =
                new ProtosProcessRuntime(prelude.actorRefPrototypeForRuntime());
        process.bindExecutionHostForRuntime(new ImmediateExecutionHost());

        RuntimeException cleanupFailure =
                new RuntimeException("provider-cleanup-failed");
        CompletableFuture<Void> failedCleanup = new CompletableFuture<>();
        failedCleanup.completeExceptionally(cleanupFailure);

        ProtosCapturedProcessExecution.Result guest =
                guest(new ProtosIntegerValue(BigInteger.TEN));
        ProtosTestResourceAttemptCompletion completion =
                ProtosTestResourceAttemptTerminalizer
                        .finishStarted(
                                process,
                                guest,
                                transaction(new AtomicInteger(), failedCleanup))
                        .toCompletableFuture()
                        .join();

        assertSame(guest, completion.guestObservation().orElseThrow());
        assertEquals(1, completion.infrastructureFailures().size());
        assertSame(cleanupFailure, completion.infrastructureFailures().get(0));
        assertEquals(
                ProtosTestResourceAttemptCompletion.CapacityDisposition.UNSAFE,
                completion.capacityDisposition());
    }

    @Test
    void infrastructureOnlyCanStillHaveSafeCapacity() {
        RuntimeException provisionFailure = new RuntimeException("provider-unavailable");
        ProtosTestResourceAttemptCompletion completion =
                new ProtosTestResourceAttemptCompletion(
                        null,
                        List.of(provisionFailure),
                        ProtosTestResourceAttemptCompletion.CapacityDisposition.SAFE);
        assertTrue(completion.guestObservation().isEmpty());
        assertTrue(completion.infrastructureFailed());
        assertEquals(
                ProtosTestResourceAttemptCompletion.CapacityDisposition.SAFE,
                completion.capacityDisposition());
    }

    private static ProtosCapturedProcessExecution.Result guest(Object value) {
        return new ProtosCapturedProcessExecution.Result(
                ProtosExecutionOutcome.completed(value),
                new byte[] {1, 2},
                new byte[] {3});
    }

    private static ProtosTestResourceProviderTransaction transaction(
            AtomicInteger cleanupCalls, CompletionStage<Void> cleanupStage) {
        ProtosTestResourceProviderLease lease =
                new ProtosTestResourceProviderLease(
                        Map.of("gpu", new Object()),
                        () -> {
                            cleanupCalls.incrementAndGet();
                            return cleanupStage;
                        });
        return new ProtosTestResourceProviderTransaction(
                Map.of("gpu", new Object()), List.of(lease));
    }

    private abstract static class BaseExecutionHost implements ProtosProcessExecutionHost {
        @Override public <T> T callForRuntime(Supplier<T> action) {
            return action.get();
        }
        @Override public Optional<ProtosActorScheduler> actorSchedulerForRuntime() {
            return Optional.empty();
        }
    }

    private static final class BlockingExecutionHost extends BaseExecutionHost {
        private final CountDownLatch terminationNotified = new CountDownLatch(1);
        private final CountDownLatch releaseTerminalDisposition = new CountDownLatch(1);
        @Override public void processTerminatedForRuntime() {
            terminationNotified.countDown();
        }
        @Override public void awaitTerminalDispositionForRuntime() {
            boolean interrupted = false;
            while (true) {
                try {
                    releaseTerminalDisposition.await();
                    break;
                } catch (InterruptedException interruption) {
                    interrupted = true;
                }
            }
            if (interrupted) Thread.currentThread().interrupt();
        }
    }

    private static final class FailingExecutionHost extends BaseExecutionHost {
        private final RuntimeException failure;
        private FailingExecutionHost(RuntimeException failure) {
            this.failure = failure;
        }
        @Override public void processTerminatedForRuntime() {}
        @Override public void awaitTerminalDispositionForRuntime() {
            throw failure;
        }
    }

    private static final class ImmediateExecutionHost extends BaseExecutionHost {
        @Override public void processTerminatedForRuntime() {}
    }
}
