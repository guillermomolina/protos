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

import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.Optional;
import java.util.function.LongSupplier;

/**
 * D176 stalled in-flight Case diagnostic policy.
 *
 * <p>This is Test-Tool-internal machinery. It consumes the D174 lifecycle facts already derived by
 * the Tool's reporter ({@code CaseStarted} / {@code CaseTerminal} carrying an invocation-local
 * token and a display reference) and decides when one bounded diagnostic snapshot is due. It never
 * infers activity from guest output.
 *
 * <p>The policy is deliberately diagnostic only. Reaching the threshold never fails, cancels,
 * classifies, retries or reschedules a Case, and never changes a result or exit code.
 *
 * <p>Episode rules:
 *
 * <ul>
 *   <li>the diagnostic is armed while at least one Case is in flight;
 *   <li>the interval measures absence of terminal progress for the invocation, so it restarts at
 *       every {@code CaseTerminal} and at the transition from nothing in flight to something in
 *       flight;
 *   <li>a {@code CaseStarted} inside an already armed episode does not restart the interval;
 *   <li>at most one snapshot is emitted per no-terminal-progress episode, and a later
 *       {@code CaseTerminal} is required to re-arm it;
 *   <li>the diagnostic disarms when nothing remains in flight.
 * </ul>
 *
 * <p>Every method is safe for concurrent use. The instance is also the monitor the watchdog waits
 * on, so lifecycle observations delivered from guest execution wake a waiting watchdog immediately.
 */
public final class ProtosTestToolStalledCaseReporter {
    /**
     * D176 liveness-diagnostic threshold. This is not a timeout and carries no semantic meaning for
     * the Case it describes.
     */
    public static final long STALL_THRESHOLD_NANOS = 30_000_000_000L;

    /** D176 bound on displayed Case references; the remainder collapses into a "+N more" line. */
    public static final int MAX_DISPLAYED_REFERENCES = 8;

    private static final String PREFIX = "[stalled] ";

    private final long thresholdNanos;
    private final LongSupplier monotonicNanos;

    /** Insertion order is admission order, which is what makes the bounded head useful. */
    private final LinkedHashMap<Long, String> inFlight = new LinkedHashMap<>();

    private long episodeStartNanos;
    private boolean reported;
    private boolean closed;

    public ProtosTestToolStalledCaseReporter(long thresholdNanos, LongSupplier monotonicNanos) {
        if (thresholdNanos <= 0L) {
            throw new IllegalArgumentException("stall threshold must be positive");
        }
        this.thresholdNanos = thresholdNanos;
        this.monotonicNanos = Objects.requireNonNull(monotonicNanos, "monotonicNanos");
        this.episodeStartNanos = monotonicNanos.getAsLong();
    }

    /**
     * Records one {@code CaseStarted}.
     *
     * @return false when the token is already in flight, which the caller reports as a Tool error
     *     rather than a host exception crossing the guest boundary
     */
    public synchronized boolean caseStarted(long token, String displayReference) {
        Objects.requireNonNull(displayReference, "displayReference");
        if (inFlight.putIfAbsent(token, displayReference) != null) {
            return false;
        }
        if (inFlight.size() == 1) {
            startEpisode();
        }
        notifyAll();
        return true;
    }

    /**
     * Records one {@code CaseTerminal}.
     *
     * @return false when the token is not in flight, which the caller reports as a Tool error
     */
    public synchronized boolean caseTerminal(long token) {
        if (inFlight.remove(token) == null) {
            return false;
        }
        // Terminal progress restarts the interval whether or not work remains: with an empty
        // in-flight set the diagnostic is simply disarmed until the next admission.
        startEpisode();
        notifyAll();
        return true;
    }

    /**
     * Returns the snapshot text when one is due now, consuming this episode's single emission.
     *
     * <p>The caller writes the returned text outside any lock, so no Tool I/O happens on the path
     * that delivers lifecycle observations.
     */
    public synchronized Optional<String> pollDiagnostic() {
        if (closed || reported || inFlight.isEmpty()) {
            return Optional.empty();
        }
        if (monotonicNanos.getAsLong() - episodeStartNanos < thresholdNanos) {
            return Optional.empty();
        }
        reported = true;
        return Optional.of(renderSnapshot());
    }

    /**
     * Blocks until the next poll could produce a snapshot, until a lifecycle observation arrives, or
     * until this reporter is closed.
     */
    synchronized void awaitNextCheck() throws InterruptedException {
        if (closed) {
            return;
        }
        wait(waitBudgetMillis());
    }

    /** Zero means "no deadline to watch": wait until an observation or close wakes the watchdog. */
    synchronized long waitBudgetMillis() {
        if (closed) {
            return 1L;
        }
        if (reported || inFlight.isEmpty()) {
            return 0L;
        }
        long remainingNanos = thresholdNanos - (monotonicNanos.getAsLong() - episodeStartNanos);
        if (remainingNanos <= 0L) {
            return 1L;
        }
        return Math.max(1L, remainingNanos / 1_000_000L);
    }

    public synchronized void close() {
        closed = true;
        notifyAll();
    }

    synchronized boolean isClosed() {
        return closed;
    }

    synchronized int inFlightCountForTest() {
        return inFlight.size();
    }

    private void startEpisode() {
        episodeStartNanos = monotonicNanos.getAsLong();
        reported = false;
    }

    private String renderSnapshot() {
        int total = inFlight.size();
        StringBuilder text = new StringBuilder();
        text.append(PREFIX)
                .append("no Case completed for ")
                .append(thresholdNanos / 1_000_000_000L)
                .append("s; ")
                .append(total)
                .append(total == 1 ? " Case in flight" : " Cases in flight")
                .append('\n');

        int shown = 0;
        for (String displayReference : inFlight.values()) {
            if (shown == MAX_DISPLAYED_REFERENCES) {
                break;
            }
            text.append(PREFIX).append("- ").append(displayReference).append('\n');
            shown++;
        }

        if (total > shown) {
            text.append(PREFIX).append('+').append(total - shown).append(" more").append('\n');
        }
        return text.toString();
    }
}
