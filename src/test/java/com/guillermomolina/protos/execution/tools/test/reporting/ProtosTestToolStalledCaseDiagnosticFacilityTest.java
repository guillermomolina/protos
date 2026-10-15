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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import java.nio.file.Path;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/** D176 host watchdog wiring: the guest observation boundary and the elapsed-time trigger. */
final class ProtosTestToolStalledCaseDiagnosticFacilityTest {
    private static final Path CORE = Path.of("protos", "lib", "core");
    private static final Path STANDARD_LIBRARY = Path.of("protos", "lib");
    private static final String SLOT =
            ProtosTestToolStalledCaseDiagnosticFacility.BOOTSTRAP_SLOT;

    @Test
    void watchdogEmitsOneBoundedSnapshotWhenTerminalProgressStops() throws Exception {
        ProtosActivation activation = newActivation();
        LinkedBlockingQueue<String> snapshots = new LinkedBlockingQueue<>();

        try (ProtosTestToolStalledCaseDiagnosticFacility facility =
                ProtosTestToolStalledCaseDiagnosticFacility.install(
                        activation,
                        SLOT,
                        snapshots::add,
                        TimeUnit.MILLISECONDS.toNanos(50L),
                        System::nanoTime)) {
            observe(activation, "\"started\", 0, \"corpus:a.protos::first\"");
            observe(activation, "\"started\", 1, \"corpus:b.protos::second\"");

            String snapshot = snapshots.poll(30L, TimeUnit.SECONDS);
            assertNotNull(snapshot, "the watchdog never reported the stalled invocation");
            assertTrue(snapshot.startsWith("[stalled] no Case completed for "), snapshot);
            assertTrue(snapshot.contains("; 2 Cases in flight\n"), snapshot);
            assertTrue(snapshot.contains("[stalled] - corpus:a.protos::first\n"), snapshot);
            assertTrue(snapshot.contains("[stalled] - corpus:b.protos::second\n"), snapshot);

            observe(activation, "\"terminal\", 0, null");
            observe(activation, "\"terminal\", 1, null");
            assertEquals(0, facility.reporterForTest().inFlightCountForTest());
        }
    }

    @Test
    void anInvocationWithNothingInFlightIsNeverReported() throws Exception {
        ProtosActivation activation = newActivation();
        LinkedBlockingQueue<String> snapshots = new LinkedBlockingQueue<>();

        try (ProtosTestToolStalledCaseDiagnosticFacility facility =
                ProtosTestToolStalledCaseDiagnosticFacility.install(
                        activation,
                        SLOT,
                        snapshots::add,
                        TimeUnit.MILLISECONDS.toNanos(20L),
                        System::nanoTime)) {
            observe(activation, "\"started\", 0, \"corpus:a.protos::first\"");
            observe(activation, "\"terminal\", 0, null");

            assertNull(
                    snapshots.poll(300L, TimeUnit.MILLISECONDS),
                    "a disarmed diagnostic must stay silent");
            assertEquals(0, facility.reporterForTest().inFlightCountForTest());
        }
    }

    @Test
    void malformedObservationsBecomeToolErrorsRatherThanHostExceptions() throws Exception {
        ProtosActivation activation = newActivation();

        try (ProtosTestToolStalledCaseDiagnosticFacility facility =
                ProtosTestToolStalledCaseDiagnosticFacility.install(
                        activation,
                        SLOT,
                        snapshot -> {},
                        ProtosTestToolStalledCaseReporter.STALL_THRESHOLD_NANOS,
                        System::nanoTime)) {
            assertFails(activation, "\"started\", 0");
            assertFails(activation, "\"started\", 0, \"a\", \"b\"");
            assertFails(activation, "\"admitted\", 0, \"a\"");
            assertFails(activation, "\"started\", \"0\", \"a\"");
            assertFails(activation, "\"started\", -1, \"a\"");
            assertFails(activation, "\"started\", 0, null");
            assertFails(activation, "\"terminal\", 9, null");

            observe(activation, "\"started\", 0, \"corpus:a.protos::first\"");
            assertFails(activation, "\"started\", 0, \"corpus:a.protos::first\"");

            assertEquals(1, facility.reporterForTest().inFlightCountForTest());
        }
    }

    @Test
    void theBootstrapSlotIsInstalledExactlyOnce() throws Exception {
        ProtosActivation activation = newActivation();

        try (ProtosTestToolStalledCaseDiagnosticFacility facility =
                ProtosTestToolStalledCaseDiagnosticFacility.install(
                        activation,
                        SLOT,
                        snapshot -> {},
                        ProtosTestToolStalledCaseReporter.STALL_THRESHOLD_NANOS,
                        System::nanoTime)) {
            assertTrue(activation.context().hasLocalSlot(SLOT));
            assertThrows(
                    IllegalStateException.class,
                    () ->
                            ProtosTestToolStalledCaseDiagnosticFacility.install(
                                    activation,
                                    SLOT,
                                    snapshot -> {},
                                    ProtosTestToolStalledCaseReporter.STALL_THRESHOLD_NANOS,
                                    System::nanoTime));
            assertNotNull(facility);
        }
    }

    @Test
    void closingTheFacilityStopsTheWatchdog() throws Exception {
        ProtosActivation activation = newActivation();
        ProtosTestToolStalledCaseDiagnosticFacility facility =
                ProtosTestToolStalledCaseDiagnosticFacility.install(
                        activation,
                        SLOT,
                        snapshot -> {},
                        TimeUnit.MILLISECONDS.toNanos(10L),
                        System::nanoTime);

        observe(activation, "\"started\", 0, \"corpus:a.protos::first\"");
        facility.close();

        assertTrue(facility.reporterForTest().isClosed());
    }

    private static void observe(ProtosActivation activation, String arguments) {
        ProtosTestExecutionSupport.evaluate(SLOT + "(" + arguments + ")", activation);
    }

    private static void assertFails(ProtosActivation activation, String arguments) {
        assertThrows(
                ProtosSignalException.class,
                () -> observe(activation, arguments),
                () -> "expected a Tool error for arguments: " + arguments);
    }

    private static ProtosActivation newActivation() throws Exception {
        ProtosPrelude prelude =
                new ProtosCoreBootstrap()
                        .bootstrap(
                                CORE,
                                new ProtosStandardLibraryModuleResolver(STANDARD_LIBRARY));
        return prelude.newModuleActivation();
    }
}
