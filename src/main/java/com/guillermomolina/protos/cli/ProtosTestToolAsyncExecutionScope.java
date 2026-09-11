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
package com.guillermomolina.protos.cli;

import com.guillermomolina.protos.execution.ProtosAsyncExactExecutionFacility;
import com.guillermomolina.protos.execution.ProtosPolyglotRuntimeHost;
import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Production Test Tool ownership scope for PLAT023 async exact execution.
 *
 * <p>Bundled Protos owns admission through D069/H2B. This host scope transports only already
 * admitted exact executions, using one fresh named platform Thread per accepted execution. It
 * deliberately has no pool size, queue capacity, TestPlan policy or relationship to the Actor
 * carrier substrate.
 */
final class ProtosTestToolAsyncExecutionScope implements AutoCloseable {
    private final PlatformThreadPerTaskSubmission submission;
    private final List<ProtosAsyncExactExecutionFacility> facilities;
    private boolean closed;

    private ProtosTestToolAsyncExecutionScope(
            PlatformThreadPerTaskSubmission submission,
            List<ProtosAsyncExactExecutionFacility> facilities) {
        this.submission = Objects.requireNonNull(submission, "submission");
        this.facilities = List.copyOf(facilities);
    }

    static ProtosTestToolAsyncExecutionScope install(
            ProtosActivation activation,
            ProtosPolyglotRuntimeHost runtimeHost,
            ProtosPrelude actorPrelude,
            ProtosPrelude groupPrelude,
            ProtosPrelude packagePrelude) {
        Objects.requireNonNull(activation, "activation");
        Objects.requireNonNull(runtimeHost, "runtimeHost");
        Objects.requireNonNull(actorPrelude, "actorPrelude");
        Objects.requireNonNull(groupPrelude, "groupPrelude");
        Objects.requireNonNull(packagePrelude, "packagePrelude");

        PlatformThreadPerTaskSubmission submission = new PlatformThreadPerTaskSubmission();
        ArrayList<ProtosAsyncExactExecutionFacility> facilities = new ArrayList<>();
        try {
            facilities.add(
                    ProtosAsyncExactExecutionFacility.install(
                            activation, runtimeHost, submission));
            facilities.add(
                    ProtosAsyncExactExecutionFacility.installInspection(
                            activation, runtimeHost, submission));
            facilities.add(
                    ProtosAsyncExactExecutionFacility.install(
                            activation,
                            "actorExecutionAsync",
                            actorPrelude,
                            runtimeHost,
                            submission));
            facilities.add(
                    ProtosAsyncExactExecutionFacility.installInspection(
                            activation,
                            "actorExecutionInspectAsync",
                            actorPrelude,
                            runtimeHost,
                            submission));
            facilities.add(
                    ProtosAsyncExactExecutionFacility.install(
                            activation,
                            "groupExecutionAsync",
                            groupPrelude,
                            runtimeHost,
                            submission));
            facilities.add(
                    ProtosAsyncExactExecutionFacility.installInspection(
                            activation,
                            "groupExecutionInspectAsync",
                            groupPrelude,
                            runtimeHost,
                            submission));
            facilities.add(
                    ProtosAsyncExactExecutionFacility.install(
                            activation,
                            "packageExecutionAsync",
                            packagePrelude,
                            runtimeHost,
                            submission));
            return new ProtosTestToolAsyncExecutionScope(submission, facilities);
        } catch (RuntimeException failure) {
            closeFacilities(facilities);
            submission.close();
            throw failure;
        }
    }

    private static void closeFacilities(List<ProtosAsyncExactExecutionFacility> facilities) {
        for (ProtosAsyncExactExecutionFacility facility : facilities) {
            facility.close();
        }
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        closeFacilities(facilities);
        submission.close();
    }

    /** PLAT023 mechanism: no executor/pool; each accepted task receives a fresh platform Thread. */
    static final class PlatformThreadPerTaskSubmission
            implements ProtosAsyncExactExecutionFacility.Submission, AutoCloseable {
        private static final int QUEUED = 0;
        private static final int RUNNING = 1;
        private static final int CANCELLED = 2;
        private static final int DONE = 3;

        private final AtomicLong nextCarrierId = new AtomicLong();
        private final Set<Thread> activeCarriers = new LinkedHashSet<>();
        private boolean closed;

        @Override
        public ProtosAsyncExactExecutionFacility.Submitted submit(Runnable work) {
            Objects.requireNonNull(work, "work");
            Job job = new Job(work);
            Thread carrier =
                    Thread.ofPlatform()
                            .name("protos-test-exact-" + nextCarrierId.incrementAndGet())
                            .unstarted(
                                    () -> {
                                        try {
                                            job.runAccepted();
                                        } finally {
                                            carrierFinished(Thread.currentThread());
                                        }
                                    });

            synchronized (this) {
                if (closed) {
                    throw new IllegalStateException("Test Tool async execution scope is closed");
                }
                activeCarriers.add(carrier);
                try {
                    carrier.start();
                } catch (RuntimeException | Error failure) {
                    activeCarriers.remove(carrier);
                    notifyAll();
                    throw failure;
                }
            }
            return job;
        }

        private synchronized void carrierFinished(Thread carrier) {
            activeCarriers.remove(carrier);
            notifyAll();
        }

        synchronized int activeCarrierCountForTest() {
            return activeCarriers.size();
        }

        @Override
        public void close() {
            boolean interrupted = false;
            synchronized (this) {
                closed = true;
                while (!activeCarriers.isEmpty()) {
                    try {
                        wait();
                    } catch (InterruptedException interruption) {
                        interrupted = true;
                    }
                }
            }
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }

        private static final class Job implements ProtosAsyncExactExecutionFacility.Submitted {
            private final Runnable work;
            private final AtomicInteger state = new AtomicInteger(QUEUED);

            private Job(Runnable work) {
                this.work = work;
            }

            private void runAccepted() {
                if (!state.compareAndSet(QUEUED, RUNNING)) {
                    return;
                }
                try {
                    work.run();
                } finally {
                    state.set(DONE);
                }
            }

            @Override
            public boolean cancelIfNotStarted() {
                return state.compareAndSet(QUEUED, CANCELLED);
            }
        }
    }
}
