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
import static org.junit.jupiter.api.Assertions.assertSame;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class ProtosTestToolH2B2BoundedDCaseSchedulingTest {
    private static final Path CORE = Path.of("protos", "lib", "core");
    private static final Path STANDARD_LIBRARY = Path.of("protos", "lib");
    private static final Path TOOL_ROOT = Path.of("protos", "tools", "test");
    private static final Path FIXTURE =
            Path.of(
                    "protos",
                    "tests",
                    "tooling",
                    "tool002-h2b2-bounded-d-scheduling.protos");

    @Test
    void oneProtosBoundCoversExecutionWrappingAndInspectionRoutes() throws Exception {
        try (TrackingSubmission submission = new TrackingSubmission();
                ProtosPolyglotRuntimeHost runtimeHost = ProtosPolyglotRuntimeHost.open()) {
            ProtosBundledToolModuleResolver resolver =
                    new ProtosBundledToolModuleResolver(
                            "test",
                            TOOL_ROOT,
                            new ProtosStandardLibraryModuleResolver(STANDARD_LIBRARY));
            ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE, resolver);
            ProtosActivation activation = prelude.newModuleActivation();

            try (ProtosAsyncExactExecutionFacility execution =
                            ProtosAsyncExactExecutionFacility.install(
                                    activation, runtimeHost, submission);
                    ProtosAsyncExactExecutionFacility inspection =
                            ProtosAsyncExactExecutionFacility.installInspection(
                                    activation, runtimeHost, submission)) {
                ProtosExecutionOutcome outcome =
                        ProtosRootTaskExecution.execute(
                                new ProtosSourceCompiler()
                                        .compile(
                                                Files.readString(
                                                        FIXTURE,
                                                        StandardCharsets.UTF_8)),
                                activation);

                assertEquals(
                        ProtosExecutionOutcome.State.COMPLETED,
                        outcome.state(),
                        () ->
                                "H2B2 root outcome="
                                        + outcome.state()
                                        + ", submitted="
                                        + submission.submittedCount()
                                        + ", maxActive="
                                        + submission.maxActive()
                                        + ", completionOrder="
                                        + submission.completionOrder()
                                        + ", guestErrorParent="
                                        + guestErrorParentName(outcome, prelude));
                assertSame(ProtosBooleanValue.TRUE, outcome.value());
                assertEquals(5, submission.submittedCount());
                assertEquals(
                        2,
                        submission.maxActive(),
                        "execution and inspection routes must share the Protos max-in-flight bound");
                assertEquals(
                        List.of(1, 0),
                        submission.completionOrder().subList(0, 2),
                        "first mixed wave must complete physically out of TestPlan order");
            }
        }
    }

    private static String guestErrorParentName(
            ProtosExecutionOutcome outcome, ProtosPrelude prelude) {
        if (outcome.error() == null) {
            return "-";
        }
        Object parent = outcome.error().parent().orElse(null);
        for (var entry : prelude.bindings().localSlotsSnapshot().entrySet()) {
            if (entry.getValue() == parent) {
                return entry.getKey();
            }
        }
        return parent == null ? "<none>" : parent.getClass().getSimpleName();
    }

    private static final class TrackingSubmission
            implements ProtosAsyncExactExecutionFacility.Submission, AutoCloseable {
        private final ExecutorService executor = Executors.newFixedThreadPool(4);
        private final AtomicInteger submitted = new AtomicInteger();
        private final AtomicInteger active = new AtomicInteger();
        private final AtomicInteger maxActive = new AtomicInteger();
        private final CountDownLatch firstPairStarted = new CountDownLatch(2);
        private final CountDownLatch secondFinished = new CountDownLatch(1);
        private final CopyOnWriteArrayList<Integer> completionOrder =
                new CopyOnWriteArrayList<>();

        @Override
        public ProtosAsyncExactExecutionFacility.Submitted submit(Runnable work) {
            int ordinal = submitted.getAndIncrement();
            Job job = new Job(Objects.requireNonNull(work, "work"), ordinal);
            executor.submit(job);
            return job;
        }

        int submittedCount() {
            return submitted.get();
        }

        int maxActive() {
            return maxActive.get();
        }

        List<Integer> completionOrder() {
            return List.copyOf(completionOrder);
        }

        @Override
        public void close() {
            executor.shutdownNow();
        }

        private final class Job
                implements Runnable, ProtosAsyncExactExecutionFacility.Submitted {
            private static final int QUEUED = 0;
            private static final int RUNNING = 1;
            private static final int CANCELLED = 2;
            private static final int DONE = 3;

            private final Runnable work;
            private final int ordinal;
            private final AtomicInteger state = new AtomicInteger(QUEUED);

            private Job(Runnable work, int ordinal) {
                this.work = work;
                this.ordinal = ordinal;
            }

            @Override
            public void run() {
                if (!state.compareAndSet(QUEUED, RUNNING)) {
                    return;
                }

                int now = active.incrementAndGet();
                maxActive.accumulateAndGet(now, Math::max);
                try {
                    if (ordinal < 2) {
                        firstPairStarted.countDown();
                        if (!firstPairStarted.await(30, TimeUnit.SECONDS)) {
                            throw new IllegalStateException(
                                    "the first mixed bounded wave did not admit two host executions");
                        }
                    }

                    if (ordinal == 0) {
                        if (!secondFinished.await(30, TimeUnit.SECONDS)) {
                            throw new IllegalStateException(
                                    "the inspection execution did not physically finish first");
                        }
                    }

                    work.run();
                } catch (InterruptedException interruption) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(
                            "H2B2 bounded scheduling evidence was interrupted",
                            interruption);
                } finally {
                    completionOrder.add(ordinal);
                    if (ordinal == 1) {
                        secondFinished.countDown();
                    }
                    active.decrementAndGet();
                    state.compareAndSet(RUNNING, DONE);
                }
            }

            @Override
            public boolean cancelIfNotStarted() {
                return state.compareAndSet(QUEUED, CANCELLED);
            }
        }
    }
}
