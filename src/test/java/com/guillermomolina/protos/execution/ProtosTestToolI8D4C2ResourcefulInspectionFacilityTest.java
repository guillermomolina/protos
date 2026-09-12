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
import com.guillermomolina.protos.runtime.ProtosIntegerValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class ProtosTestToolI8D4C2ResourcefulInspectionFacilityTest {
    private static final Path CORE = Path.of("protos", "lib", "core");
    private static final Path RUNNER = Path.of("protos", "tools", "test", "Runner.protos");
    private static final Path MAIN = Path.of("protos", "tools", "test", "Main.protos");

    @Test
    void liveFutureInspectionRunsInsideTheResourcefulProcessAndPreservesD108Completion()
            throws Exception {
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
        var activation = prelude.newModuleActivation();

        AtomicInteger cleanupCalls = new AtomicInteger();
        ProtosTestResourceProviderAdapter gpu =
                request ->
                        CompletableFuture.completedFuture(
                                new ProtosTestResourceProviderLease(
                                        Map.of(
                                                "gpu",
                                                new ProtosIntegerValue(
                                                        BigInteger.valueOf(42))),
                                        () -> {
                                            cleanupCalls.incrementAndGet();
                                            return CompletableFuture.completedFuture(null);
                                        }));

        ProtosTestResourceProviderRegistry registry =
                new ProtosTestResourceProviderRegistry(
                        List.of(
                                new ProtosTestResourceProviderRegistry.Registration(
                                        "device/gpu",
                                        gpu)));

        ProtosAsyncExactExecutionFacility.Submission submission =
                work -> {
                    Thread carrier =
                            Thread.ofPlatform()
                                    .name("i8d4c2-resourceful-inspection")
                                    .unstarted(work);
                    carrier.start();
                    return () -> false;
                };

        try (ProtosPolyglotRuntimeHost runtimeHost = ProtosPolyglotRuntimeHost.open();
                ProtosTestResourcefulExecutionFacility inspectionFacility =
                        ProtosTestResourcefulExecutionFacility.installInspection(
                                activation,
                                registry,
                                runtimeHost,
                                submission)) {

            String source =
                    """
                    bindings: Array(
                        Array("gpu", "exclusive", null, "placement", "device/gpu", null)
                    )

                    live: resourceExecutionInspectAsync(
                        "(() => { resources[\\"gpu\\"] }).future()",
                        "(subject) => { subject.value() }",
                        bindings
                    ).value()

                    passed: live.infrastructureFailed.not()
                    passed = passed && live.capacitySafe
                    passed = passed && (live.infrastructureFailures.size() == 0)
                    passed = passed && (live.guestObservation === null).not()
                    passed.ifTrue(() => {
                        passed = live.guestObservation.state === "completed"
                    })
                    passed.ifTrue(() => {
                        passed = live.guestObservation.error === null
                    })
                    passed.ifTrue(() => {
                        passed = live.guestObservation.value == 42
                    })

                    failed: resourceExecutionInspectAsync(
                        "resources[\\"gpu\\"]",
                        "(subject) => { Error().signal() }",
                        bindings
                    ).value()

                    passed = passed && failed.infrastructureFailed.not()
                    passed = passed && failed.capacitySafe
                    passed = passed && (failed.infrastructureFailures.size() == 0)
                    passed = passed && (failed.guestObservation === null).not()
                    passed.ifTrue(() => {
                        passed = failed.guestObservation.state === "failed"
                    })
                    passed.ifTrue(() => {
                        passed = failed.guestObservation.value === null
                    })
                    passed.ifTrue(() => {
                        passed = (failed.guestObservation.error === null).not()
                    })

                    passed
                    """;

            ProtosExecutionOutcome outcome =
                    ProtosRootTaskExecution.execute(
                            new ProtosSourceCompiler().compile(source),
                            activation);

            assertEquals(
                    ProtosExecutionOutcome.State.COMPLETED,
                    outcome.state(),
                    () ->
                            "I8D4C2 root outcome="
                                    + outcome.state()
                                    + ", error="
                                    + outcome.error());
            assertSame(ProtosBooleanValue.TRUE, outcome.value());
        }

        assertEquals(2, cleanupCalls.get());

        String runner = Files.readString(RUNNER, StandardCharsets.UTF_8);
        String main = Files.readString(MAIN, StandardCharsets.UTF_8);
        assertFalse(runner.contains("resourceExecutionInspectAsync"));
        assertFalse(main.contains("resourceExecutionInspectAsync"));
        assertFalse(runner.contains("runInfrastructureFailed"));
        assertFalse(main.contains("runInfrastructureFailed"));
    }

    @Test
    void malformedInspectionBindingSnapshotFailsAtPrivateProtocolBoundary()
            throws Exception {
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
        var activation = prelude.newModuleActivation();

        try (ProtosPolyglotRuntimeHost runtimeHost = ProtosPolyglotRuntimeHost.open();
                ProtosTestResourcefulExecutionFacility inspectionFacility =
                        ProtosTestResourcefulExecutionFacility.installInspection(
                                activation,
                                ProtosTestResourceProviderRegistry.empty(),
                                runtimeHost,
                                work -> {
                                    Thread carrier =
                                            Thread.ofPlatform()
                                                    .name("i8d4c2-malformed")
                                                    .unstarted(work);
                                    carrier.start();
                                    return () -> false;
                                })) {

            ProtosExecutionOutcome outcome =
                    ProtosRootTaskExecution.execute(
                            new ProtosSourceCompiler()
                                    .compile(
                                            "resourceExecutionInspectAsync("
                                                    + "\"1\", "
                                                    + "\"(subject) => { subject }\", "
                                                    + "Array()).value()"),
                            activation);

            assertEquals(ProtosExecutionOutcome.State.FAILED, outcome.state());
            assertTrue(outcome.error() != null);
        }
    }
}
