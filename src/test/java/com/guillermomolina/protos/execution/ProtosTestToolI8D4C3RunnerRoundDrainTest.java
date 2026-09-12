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
 * the specific language governing rights and limitations under the LICENSE.
 */

package com.guillermomolina.protos.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class ProtosTestToolI8D4C3RunnerRoundDrainTest {
    private static final Path CORE = Path.of("protos", "lib", "core");
    private static final Path STANDARD_LIBRARY = Path.of("protos", "lib");
    private static final Path TOOL_ROOT = Path.of("protos", "tools", "test");
    private static final Path SHARED_ROOT = Path.of("protos", "tools", "shared");
    private static final Path FIXTURE =
            Path.of(
                    "protos",
                    "tests",
                    "tooling",
                    "tool002-i8d4c3-runner-round-drain.protos");
    private static final Path RUNNER = TOOL_ROOT.resolve("Runner.protos");
    private static final Path BINDING = TOOL_ROOT.resolve("ResourceBinding.protos");
    private static final Path MAIN = TOOL_ROOT.resolve("Main.protos");

    @Test
    void d108RunnerDrainsRoundReleasesOnlySafeCapacityAndFailStopsWithoutFabrication()
            throws Exception {
        ProtosBundledToolModuleResolver resolver =
                new ProtosBundledToolModuleResolver(
                        "test",
                        TOOL_ROOT,
                        SHARED_ROOT,
                        new ProtosStandardLibraryModuleResolver(STANDARD_LIBRARY));
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE, resolver);

        ProtosExecutionOutcome outcome =
                ProtosRootTaskExecution.execute(
                        new ProtosSourceCompiler()
                                .compile(Files.readString(FIXTURE, StandardCharsets.UTF_8)),
                        prelude.newModuleActivation());

        assertEquals(
                ProtosExecutionOutcome.State.COMPLETED,
                outcome.state(),
                () ->
                        "I8D4C3 root outcome="
                                + outcome.state()
                                + ", error="
                                + outcome.error());
        assertSame(ProtosBooleanValue.TRUE, outcome.value());

        String runner = Files.readString(RUNNER, StandardCharsets.UTF_8);
        assertTrue(runner.contains("runD108WithResourcesWithLoader: ("));
        assertTrue(runner.contains("scheduleD108CaseAsync: ("));
        assertTrue(runner.contains("completionValue.capacitySafe"));
        assertTrue(runner.contains("completionValue.infrastructureFailed"));
        assertTrue(runner.contains("ResourceReservation.release("));
        assertTrue(runner.contains("d108RunInfrastructureAttempts: ("));
        assertTrue(runner.contains("d108RunCutoverCases: ("));
        assertTrue(runner.contains("d108RunRetainedReservationCount: ("));
        assertTrue(runner.contains("d108RunHealthyRun: ("));

        String binding = Files.readString(BINDING, StandardCharsets.UTF_8);
        assertTrue(binding.contains("providerRequestSnapshot: (bindings) => {"));
        assertTrue(binding.contains("ResourceCatalog.entryProvider(entry)"));
        assertTrue(binding.contains("ResourceCatalog.entryProfile(entry)"));

        // I8D4C3 remains a private Runner composition. Public Main/reporting and
        // production provider-registry wiring are separate later boundaries.
        String main = Files.readString(MAIN, StandardCharsets.UTF_8);
        assertTrue(main.contains("runD108WithResources"));
        assertTrue(main.contains("resourceExecutionAsync"));
        assertTrue(main.contains("resourceExecutionInspectAsync"));
    }
}
