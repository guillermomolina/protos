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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;
import org.junit.jupiter.api.Test;

/** D176 stalled in-flight Case diagnostic policy, driven by a deterministic logical clock. */
final class ProtosTestToolStalledCaseReporterTest {
    private static final long THRESHOLD =
            ProtosTestToolStalledCaseReporter.STALL_THRESHOLD_NANOS;

    @Test
    void d176ThresholdAndDisplayBoundMatchTheRatifiedPolicy() {
        assertEquals(
                30L,
                ProtosTestToolStalledCaseReporter.STALL_THRESHOLD_NANOS / 1_000_000_000L,
                "D176 ratified a 30-second terminal-inactivity interval");
        assertEquals(
                8,
                ProtosTestToolStalledCaseReporter.MAX_DISPLAYED_REFERENCES,
                "D176 ratified a bound of 8 displayed Case references");
    }

    @Test
    void nothingInFlightNeverProducesADiagnostic() {
        LogicalClock clock = new LogicalClock();
        ProtosTestToolStalledCaseReporter reporter = reporter(clock);

        clock.advanceSeconds(600L);

        assertEquals(Optional.empty(), reporter.pollDiagnostic());
        assertEquals(0L, reporter.waitBudgetMillis());
    }

    @Test
    void noDiagnosticWhileTerminalProgressKeepsArriving() {
        LogicalClock clock = new LogicalClock();
        ProtosTestToolStalledCaseReporter reporter = reporter(clock);

        assertTrue(reporter.caseStarted(0L, "corpus:a.protos::first"));
        assertTrue(reporter.caseStarted(1L, "corpus:b.protos::first"));

        for (long round = 0; round < 20; round++) {
            clock.advanceSeconds(29L);
            assertEquals(
                    Optional.empty(),
                    reporter.pollDiagnostic(),
                    "no episode reached the interval");
            assertTrue(reporter.caseTerminal(round % 2));
            assertTrue(reporter.caseStarted(round % 2, "corpus:c.protos::" + round));
        }

        assertEquals(2, reporter.inFlightCountForTest());
    }

    @Test
    void oneShotSnapshotAfterTheIntervalWithoutTerminalProgress() {
        LogicalClock clock = new LogicalClock();
        ProtosTestToolStalledCaseReporter reporter = reporter(clock);

        reporter.caseStarted(0L, "corpus:a.protos::first");
        reporter.caseStarted(1L, "corpus:b.protos::second");

        clock.advanceNanos(THRESHOLD - 1L);
        assertEquals(Optional.empty(), reporter.pollDiagnostic());

        clock.advanceNanos(1L);
        List<String> snapshot = lines(reporter.pollDiagnostic().orElseThrow());

        assertEquals(
                List.of(
                        "[stalled] no Case completed for 30s; 2 Cases in flight",
                        "[stalled] - corpus:a.protos::first",
                        "[stalled] - corpus:b.protos::second"),
                snapshot);

        // The episode continues: it must not repeat without later terminal progress.
        clock.advanceSeconds(300L);
        assertEquals(Optional.empty(), reporter.pollDiagnostic());
    }

    @Test
    void snapshotBoundsDisplayedReferencesAndCollapsesTheRemainder() {
        LogicalClock clock = new LogicalClock();
        ProtosTestToolStalledCaseReporter reporter = reporter(clock);

        for (long token = 0; token < 12; token++) {
            reporter.caseStarted(token, "corpus:case-" + token + ".protos::only");
        }

        clock.advanceSeconds(30L);
        List<String> snapshot = lines(reporter.pollDiagnostic().orElseThrow());

        assertEquals(10, snapshot.size(), "one header, eight references, one collapsed remainder");
        assertEquals("[stalled] no Case completed for 30s; 12 Cases in flight", snapshot.get(0));
        for (int index = 0; index < 8; index++) {
            assertEquals(
                    "[stalled] - corpus:case-" + index + ".protos::only",
                    snapshot.get(index + 1),
                    "admission order selects the displayed references");
        }
        assertEquals("[stalled] +4 more", snapshot.get(9));
    }

    @Test
    void additionalStartDoesNotRestartTheInterval() {
        LogicalClock clock = new LogicalClock();
        ProtosTestToolStalledCaseReporter reporter = reporter(clock);

        reporter.caseStarted(0L, "corpus:a.protos::first");

        clock.advanceSeconds(25L);
        reporter.caseStarted(1L, "corpus:b.protos::second");

        clock.advanceSeconds(5L);
        List<String> snapshot = lines(reporter.pollDiagnostic().orElseThrow());

        assertEquals(
                "[stalled] no Case completed for 30s; 2 Cases in flight",
                snapshot.get(0),
                "the interval measures absence of terminal progress, not Case age");
    }

    @Test
    void laterTerminalProgressReArmsTheOneShotDiagnostic() {
        LogicalClock clock = new LogicalClock();
        ProtosTestToolStalledCaseReporter reporter = reporter(clock);

        reporter.caseStarted(0L, "corpus:a.protos::first");
        reporter.caseStarted(1L, "corpus:b.protos::second");

        clock.advanceSeconds(30L);
        assertTrue(reporter.pollDiagnostic().isPresent());

        clock.advanceSeconds(30L);
        assertEquals(Optional.empty(), reporter.pollDiagnostic(), "still the same episode");

        assertTrue(reporter.caseTerminal(0L));
        assertEquals(Optional.empty(), reporter.pollDiagnostic(), "a fresh interval just started");

        clock.advanceSeconds(30L);
        assertEquals(
                List.of(
                        "[stalled] no Case completed for 30s; 1 Case in flight",
                        "[stalled] - corpus:b.protos::second"),
                lines(reporter.pollDiagnostic().orElseThrow()));
    }

    @Test
    void terminalProgressThatEmptiesTheInvocationDisarmsTheDiagnostic() {
        LogicalClock clock = new LogicalClock();
        ProtosTestToolStalledCaseReporter reporter = reporter(clock);

        reporter.caseStarted(0L, "corpus:a.protos::first");

        clock.advanceSeconds(30L);
        assertTrue(reporter.pollDiagnostic().isPresent());

        assertTrue(reporter.caseTerminal(0L));

        clock.advanceSeconds(600L);
        assertEquals(Optional.empty(), reporter.pollDiagnostic());
        assertEquals(0L, reporter.waitBudgetMillis(), "nothing in flight leaves no deadline");
    }

    @Test
    void waitBudgetTracksTheRemainingInterval() {
        LogicalClock clock = new LogicalClock();
        ProtosTestToolStalledCaseReporter reporter = reporter(clock);

        assertEquals(0L, reporter.waitBudgetMillis());

        reporter.caseStarted(0L, "corpus:a.protos::first");
        assertEquals(30_000L, reporter.waitBudgetMillis());

        clock.advanceSeconds(20L);
        assertEquals(10_000L, reporter.waitBudgetMillis());

        clock.advanceSeconds(10L);
        assertEquals(1L, reporter.waitBudgetMillis(), "due now, so the watchdog must not block");

        assertTrue(reporter.pollDiagnostic().isPresent());
        assertEquals(0L, reporter.waitBudgetMillis(), "already reported in this episode");
    }

    @Test
    void duplicateAndUnknownTokensAreRejectedWithoutMutatingState() {
        ProtosTestToolStalledCaseReporter reporter = reporter(new LogicalClock());

        assertTrue(reporter.caseStarted(7L, "corpus:a.protos::first"));
        assertFalse(reporter.caseStarted(7L, "corpus:a.protos::first"));
        assertFalse(reporter.caseTerminal(8L));
        assertEquals(1, reporter.inFlightCountForTest());

        assertTrue(reporter.caseTerminal(7L));
        assertFalse(reporter.caseTerminal(7L));
        assertEquals(0, reporter.inFlightCountForTest());
    }

    @Test
    void closedReporterStopsProducingDiagnostics() {
        LogicalClock clock = new LogicalClock();
        ProtosTestToolStalledCaseReporter reporter = reporter(clock);

        reporter.caseStarted(0L, "corpus:a.protos::first");
        clock.advanceSeconds(30L);
        reporter.close();

        assertTrue(reporter.isClosed());
        assertEquals(Optional.empty(), reporter.pollDiagnostic());
    }

    @Test
    void concurrentLifecycleObservationsAndPollsStayConsistent() throws Exception {
        LogicalClock clock = new LogicalClock();
        ProtosTestToolStalledCaseReporter reporter = reporter(clock);

        int writers = 8;
        int casesPerWriter = 500;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(writers);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        for (int writer = 0; writer < writers; writer++) {
            long base = (long) writer * casesPerWriter;
            Thread.ofPlatform()
                    .name("stall-reporter-writer-" + writer)
                    .start(
                            () -> {
                                try {
                                    start.await();
                                    for (int index = 0; index < casesPerWriter; index++) {
                                        long token = base + index;
                                        assertTrue(
                                                reporter.caseStarted(token, "case-" + token));
                                        assertTrue(reporter.caseTerminal(token));
                                    }
                                } catch (Throwable thrown) {
                                    failure.compareAndSet(null, thrown);
                                } finally {
                                    done.countDown();
                                }
                            });
        }

        Thread poller =
                Thread.ofPlatform()
                        .name("stall-reporter-poller")
                        .start(
                                () -> {
                                    try {
                                        start.await();
                                        while (done.getCount() > 0) {
                                            reporter.pollDiagnostic();
                                            reporter.waitBudgetMillis();
                                            Thread.yield();
                                        }
                                    } catch (Throwable thrown) {
                                        failure.compareAndSet(null, thrown);
                                    }
                                });

        start.countDown();
        assertTrue(done.await(30L, TimeUnit.SECONDS), "concurrent writers did not finish");
        poller.join(TimeUnit.SECONDS.toMillis(30L));

        if (failure.get() != null) {
            throw new AssertionError("concurrent lifecycle observation failed", failure.get());
        }

        assertEquals(0, reporter.inFlightCountForTest());
        clock.advanceSeconds(600L);
        assertEquals(
                Optional.empty(),
                reporter.pollDiagnostic(),
                "every Case reached terminal, so the diagnostic stays disarmed");
    }

    private static ProtosTestToolStalledCaseReporter reporter(LogicalClock clock) {
        return new ProtosTestToolStalledCaseReporter(THRESHOLD, clock);
    }

    private static List<String> lines(String snapshot) {
        assertTrue(snapshot.endsWith("\n"), "snapshot must be line-oriented");
        return List.of(snapshot.substring(0, snapshot.length() - 1).split("\n", -1));
    }

    /** Host time is the only input D176 cannot derive from lifecycle facts, so tests supply it. */
    private static final class LogicalClock implements LongSupplier {
        private final AtomicLong nanos = new AtomicLong();

        @Override
        public long getAsLong() {
            return nanos.get();
        }

        void advanceNanos(long amount) {
            nanos.addAndGet(amount);
        }

        void advanceSeconds(long amount) {
            advanceNanos(amount * 1_000_000_000L);
        }
    }
}
