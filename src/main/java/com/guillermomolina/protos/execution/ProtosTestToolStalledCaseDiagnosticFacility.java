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

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosCoreErrors;
import com.guillermomolina.protos.runtime.ProtosIntegerValue;
import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.guillermomolina.protos.runtime.ProtosStringValue;
import java.io.PrintStream;
import java.math.BigInteger;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * D176 Test-Tool-private stalled in-flight Case diagnostic.
 *
 * <p>D174 keeps the Tool's schedulers presentation-neutral, so this facility is the separate
 * reporting machinery that observes elapsed host time. It installs one bootstrap-local Closure that
 * bundled Test Tool Protos calls with already-derived reporter facts, and it runs one daemon
 * watchdog that writes the bounded snapshot to Test Tool stderr when terminal progress stops.
 *
 * <p>Protos guest code gains no Clock or Timer capability from this facility: the installed Closure
 * only accepts lifecycle facts and never reports time back to the guest. The slot is installed only
 * into the initial bundled Test Tool module activation and is never a Core/prelude binding.
 *
 * <p>The diagnostic is advisory. Nothing here fails, cancels, times out, classifies or retries a
 * Case, and no normal-output line depends on it.
 */
public final class ProtosTestToolStalledCaseDiagnosticFacility implements AutoCloseable {
    public static final String BOOTSTRAP_SLOT = "caseLifecycleSink";

    private static final String STARTED = "started";
    private static final String TERMINAL = "terminal";

    private final ProtosTestToolStalledCaseReporter reporter;
    private final Thread watchdog;

    private ProtosTestToolStalledCaseDiagnosticFacility(
            ProtosTestToolStalledCaseReporter reporter, Consumer<String> diagnostics) {
        this.reporter = reporter;
        this.watchdog =
                Thread.ofPlatform()
                        .name("protos-test-stall-watchdog")
                        .daemon(true)
                        .unstarted(() -> watch(reporter, diagnostics));
    }

    /** Production installation: D176 threshold, host monotonic time, Test Tool stderr. */
    public static ProtosTestToolStalledCaseDiagnosticFacility install(
            ProtosActivation activation, PrintStream diagnostics) {
        Objects.requireNonNull(diagnostics, "diagnostics");
        return install(
                activation,
                BOOTSTRAP_SLOT,
                snapshot -> {
                    // One complete snapshot per call keeps it from interleaving inside a line the
                    // guest progress writer is emitting to the same stream.
                    diagnostics.print(snapshot);
                    diagnostics.flush();
                },
                ProtosTestToolStalledCaseReporter.STALL_THRESHOLD_NANOS,
                System::nanoTime);
    }

    static ProtosTestToolStalledCaseDiagnosticFacility install(
            ProtosActivation activation,
            String slotName,
            Consumer<String> diagnostics,
            long thresholdNanos,
            LongSupplier monotonicNanos) {
        Objects.requireNonNull(activation, "activation");
        Objects.requireNonNull(slotName, "slotName");
        Objects.requireNonNull(diagnostics, "diagnostics");

        if (slotName.isEmpty()) {
            throw new IllegalArgumentException(
                    "stalled Case diagnostic bootstrap slot name must not be empty");
        }

        if (activation.context().hasLocalSlot(slotName)) {
            throw new IllegalStateException(
                    "stalled Case diagnostic bootstrap slot already exists: " + slotName);
        }

        ProtosTestToolStalledCaseDiagnosticFacility facility =
                new ProtosTestToolStalledCaseDiagnosticFacility(
                        new ProtosTestToolStalledCaseReporter(thresholdNanos, monotonicNanos),
                        diagnostics);

        activation
                .context()
                .createLocalSlot(
                        slotName,
                        ProtosClosureValue.nativeClosure(
                                (caller, supplied) -> facility.observe(caller, supplied)));

        facility.watchdog.start();
        return facility;
    }

    ProtosTestToolStalledCaseReporter reporterForTest() {
        return reporter;
    }

    @Override
    public void close() {
        reporter.close();
        try {
            watchdog.join();
        } catch (InterruptedException interruption) {
            Thread.currentThread().interrupt();
        }
    }

    private static void watch(
            ProtosTestToolStalledCaseReporter reporter, Consumer<String> diagnostics) {
        while (true) {
            try {
                reporter.awaitNextCheck();
            } catch (InterruptedException interruption) {
                Thread.currentThread().interrupt();
                return;
            }
            if (reporter.isClosed()) {
                return;
            }
            reporter.pollDiagnostic().ifPresent(diagnostics);
        }
    }

    private Object observe(ProtosActivation caller, List<?> supplied) {
        if (supplied.size() != 3 || !(supplied.get(0) instanceof ProtosStringValue kind)) {
            throw toolError(caller);
        }

        long token = token(caller, supplied.get(1));

        boolean accepted;
        if (STARTED.equals(kind.value())) {
            if (!(supplied.get(2) instanceof ProtosStringValue displayReference)) {
                throw toolError(caller);
            }
            accepted = reporter.caseStarted(token, displayReference.value());
        } else if (TERMINAL.equals(kind.value())) {
            accepted = reporter.caseTerminal(token);
        } else {
            throw toolError(caller);
        }

        if (!accepted) {
            throw toolError(caller);
        }
        return ProtosNullValue.INSTANCE;
    }

    private static long token(ProtosActivation caller, Object value) {
        if (!(value instanceof ProtosIntegerValue integer)) {
            throw toolError(caller);
        }
        BigInteger token = integer.value();
        if (token.signum() < 0 || token.bitLength() >= Long.SIZE) {
            throw toolError(caller);
        }
        return token.longValueExact();
    }

    private static ProtosSignalException toolError(ProtosActivation activation) {
        return new ProtosSignalException(ProtosCoreErrors.newError(activation));
    }
}
