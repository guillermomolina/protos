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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.execution.ProtosExecutionOutcome;
import com.guillermomolina.protos.runtime.ProtosDiagnosticTrace;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * CLI008-D: every terminal Error presenting boundary consumes the occurrence trace it already
 * holds, with its surface-specific summary prefix, exactly once.
 */
final class ProtosCli008DDiagnosticReconciliationTest {
    private static final ProtosDiagnosticTrace TRACE =
            new ProtosDiagnosticTrace(
                    List.of(
                            new ProtosDiagnosticTrace.Frame(
                                    "inner", "app/main", "/w/main.protos", 6, 9),
                            new ProtosDiagnosticTrace.Frame(null, "app/main", null, 8, 1)),
                    false);
    private static final List<String> FRAME_LINES =
            List.of("  at inner (/w/main.protos:6:9)", "  at app/main:8:1");

    private final ProtosCli cli = new ProtosCli();

    @Test
    void workspaceFailedOutcomeRendersItsTraceExactlyOnce() {
        Capture capture = new Capture();
        int code =
                cli.workspaceOutcomeExitCode(
                        ProtosExecutionOutcome.failed(error(), TRACE), capture.stream);

        assertEquals(1, code);
        assertSummaryThenTrace(capture.lines(), "Error: ");
        System.out.println("CLI008_D_WORKSPACE_ERROR_TRACE=CONSISTENT");
    }

    @Test
    void bundledToolFailedOutcomeKeepsToolPrefixAndRendersTrace() {
        for (String[] tool : new String[][] {{"package", "Package"}, {"test", "Test"}}) {
            Capture capture = new Capture();
            int code =
                    cli.bundledToolOutcomeExitCode(
                            tool[0],
                            tool[1],
                            ProtosExecutionOutcome.failed(error(), TRACE),
                            capture.stream);

            assertEquals(1, code);
            assertSummaryThenTrace(capture.lines(), tool[1] + " tool error: ");
        }
        System.out.println("CLI008_D_BUNDLED_TOOL_ERROR_TRACE=CONSISTENT");
    }

    @Test
    void bundledToolFailedOutcomeWithoutTraceShowsSummaryOnly() {
        Capture capture = new Capture();
        int code =
                cli.bundledToolOutcomeExitCode(
                        "package", "Package", ProtosExecutionOutcome.failed(error()),
                        capture.stream);

        assertEquals(1, code);
        List<String> lines = capture.lines();
        assertEquals(1, lines.size(), lines.toString());
        assertTrue(lines.get(0).startsWith("Package tool error: "), lines.toString());
    }

    @Test
    void bundledToolCompletedAndCancelledKeepHistoricalTranslation() {
        Capture capture = new Capture();
        assertEquals(
                0,
                cli.bundledToolOutcomeExitCode(
                        "package", "Package", ProtosExecutionOutcome.completed(error()),
                        capture.stream));
        assertEquals("", capture.text());

        // COMPLETED Test Tool still goes through testToolExitCodeForRuntime.
        IllegalStateException malformed =
                assertThrows(
                        IllegalStateException.class,
                        () -> cli.bundledToolOutcomeExitCode(
                                "test", "Test", ProtosExecutionOutcome.completed(error()),
                                capture.stream));
        assertEquals("Test tool returned a non-TestRunOutcome value", malformed.getMessage());

        // CANCELLED still surfaces as the IllegalStateException runBundledTool reports as a
        // Tool runtime error with the existing exit policy.
        IllegalStateException cancelled =
                assertThrows(
                        IllegalStateException.class,
                        () -> cli.bundledToolOutcomeExitCode(
                                "test", "Test", ProtosExecutionOutcome.cancelled(),
                                capture.stream));
        assertEquals(
                "standalone root task was cancelled before entry completion",
                cancelled.getMessage());
        assertEquals("", capture.text());
    }

    @Test
    void signalFallbackRendersAnAttachedTerminalTrace() {
        ProtosSignalException signal = new ProtosSignalException(error());
        signal.attachTerminalDiagnosticTraceForRuntime(TRACE);
        Capture capture = new Capture();

        cli.reportUncaughtError(signal, capture.stream);

        assertSummaryThenTrace(capture.lines(), "Error: ");
        System.out.println("CLI008_D_SIGNAL_FALLBACK_TRACE_DROPPED=NO");
    }

    @Test
    void signalFallbackWithoutTraceShowsSummaryOnly() {
        Capture capture = new Capture();

        cli.reportUncaughtError(new ProtosSignalException(error()), capture.stream);

        List<String> lines = capture.lines();
        assertEquals(1, lines.size(), lines.toString());
        assertTrue(lines.get(0).startsWith("Error: "), lines.toString());
    }

    private static ProtosObjectValue error() {
        return new ProtosObjectValue(ProtosObjectValue.rootObject());
    }

    private static void assertSummaryThenTrace(List<String> lines, String prefix) {
        assertEquals(1 + FRAME_LINES.size(), lines.size(), lines.toString());
        assertTrue(lines.get(0).startsWith(prefix), lines.toString());
        assertEquals(FRAME_LINES, lines.subList(1, lines.size()));
    }

    private static final class Capture {
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private final PrintStream stream = new PrintStream(bytes, true, StandardCharsets.UTF_8);

        String text() {
            return bytes.toString(StandardCharsets.UTF_8);
        }

        List<String> lines() {
            return text().lines().toList();
        }
    }
}
