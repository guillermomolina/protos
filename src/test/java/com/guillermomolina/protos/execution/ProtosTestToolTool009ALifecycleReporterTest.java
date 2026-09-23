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
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** TOOL009-A: the D174 observation boundary feeding the separate D176 reporting machinery. */
final class ProtosTestToolTool009ALifecycleReporterTest {
    private static final Path CORE = Path.of("protos", "lib", "core");
    private static final Path STANDARD_LIBRARY = Path.of("protos", "lib");
    private static final Path TOOL_ROOT = Path.of("protos", "tools", "test");
    private static final Path SHARED_ROOT = Path.of("protos", "tools", "shared");
    private static final Path FIXTURE =
            Path.of(
                    "protos",
                    "tests",
                    "tooling",
                    "tool009-a-lifecycle-reporter.protos");
    private static final Path MAIN = TOOL_ROOT.resolve("Main.protos");
    private static final Path PROGRESS = TOOL_ROOT.resolve("Progress.protos");
    private static final Path LOGICAL_CASE_RUNNER =
            TOOL_ROOT.resolve("LogicalCaseRunner.protos");

    @Test
    void lifecycleTrackerForwardsTokensAndDisplayReferencesToTheWatchdogSink()
            throws Exception {
        ProtosBundledToolModuleResolver resolver =
                new ProtosBundledToolModuleResolver(
                        "test",
                        TOOL_ROOT,
                        SHARED_ROOT,
                        new ProtosStandardLibraryModuleResolver(STANDARD_LIBRARY));
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE, resolver);

        ProtosExecutionOutcome outcome =
                ProtosTestExecutionSupport.execute(
                        Files.readString(FIXTURE, StandardCharsets.UTF_8),
                        prelude.newModuleActivation());

        assertEquals(
                ProtosExecutionOutcome.State.COMPLETED,
                outcome.state(),
                () ->
                        "TOOL009-A root outcome="
                                + outcome.state()
                                + ", error="
                                + outcome.error());
        assertSame(ProtosBooleanValue.TRUE, outcome.value());
    }

    @Test
    void bothExecutionPathsReportThroughOneInvocationWideLifecycleTracker() throws Exception {
        String progress = Files.readString(PROGRESS, StandardCharsets.UTF_8);
        assertTrue(progress.contains("beginLifecycle: (sink = null) => {"));
        assertTrue(
                progress.contains(
                        "lifecycleObserver: (state, displayReference = null) => {"));
        assertTrue(progress.contains("inFlightCount: (state) => {"));
        assertTrue(progress.contains("inFlightSnapshot: (state) => {"));

        String runner = normalized(Files.readString(LOGICAL_CASE_RUNNER, StandardCharsets.UTF_8));
        assertTrue(
                runner.contains("lifecycleObserver( \"started\", entry, null )"),
                "the suite-native scheduler must emit CaseStarted before admission");
        assertTrue(
                runner.contains("lifecycleObserver( \"terminal\", entry, completion )"),
                "the suite-native scheduler must emit CaseTerminal after completion");

        String main = Files.readString(MAIN, StandardCharsets.UTF_8);
        assertTrue(
                normalized(main)
                        .contains(
                                "Progress.beginLifecycle( "
                                        + ProtosTestToolStalledCaseDiagnosticFacility
                                                .BOOTSTRAP_SLOT
                                        + " )"),
                "the D176 host watchdog is the only reporting sink the Tool attaches");
        // TOOL009-B: repository execution is logical-only, so there is
        // exactly one execution path and exactly one lifecycle observer/
        // display renderer left; the legacy renderer no longer exists.
        assertEquals(1, occurrences(main, "Progress.lifecycleObserver("));
        assertTrue(main.contains("logicalCaseDisplayReference: (entry) => {"));
        assertTrue(
                normalized(main)
                        .contains("jobs, logicalLifecycleObserver ).value()"),
                "the suite-native path reports through the logical renderer");

        // D174/D176 add no mandatory per-Case terminal write: normal output stays the
        // compact D120 aggregate progress.
        assertEquals(1, occurrences(main, "progressWriter.writeLine("));
        assertEquals(1, occurrences(main, "Progress.observer("));
        assertEquals(1, occurrences(main, "Progress.finishPhase("));
    }

    /** Structural guards assert wiring, not layout, so whitespace runs collapse first. */
    private static String normalized(String text) {
        return text.replaceAll("\\s+", " ");
    }

    private static int occurrences(String text, String needle) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }
}
