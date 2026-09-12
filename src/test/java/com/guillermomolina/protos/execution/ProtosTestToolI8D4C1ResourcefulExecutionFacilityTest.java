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

final class ProtosTestToolI8D4C1ResourcefulExecutionFacilityTest {
    private static final Path CORE = Path.of("protos", "lib", "core");
    private static final Path RUNNER = Path.of("protos", "tools", "test", "Runner.protos");
    private static final Path MAIN = Path.of("protos", "tools", "test", "Main.protos");

    @Test
    void resourcefulCompletionRematerializesWithoutTurningInfrastructureIntoGuestError()
            throws Exception {
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
        var activation = prelude.newModuleActivation();

        AtomicInteger successfulCleanupCalls = new AtomicInteger();
        AtomicInteger unsafeCleanupCalls = new AtomicInteger();

        ProtosTestResourceProviderAdapter successful =
                request ->
                        CompletableFuture.completedFuture(
                                new ProtosTestResourceProviderLease(
                                        Map.of(
                                                "gpu",
                                                new ProtosIntegerValue(
                                                        BigInteger.valueOf(42))),
                                        () -> {
                                            successfulCleanupCalls.incrementAndGet();
                                            return CompletableFuture.completedFuture(null);
                                        }));

        RuntimeException provisioningFailure =
                new RuntimeException("provider-unavailable");
        ProtosTestResourceProviderAdapter unavailable =
                request -> CompletableFuture.failedFuture(provisioningFailure);

        RuntimeException cleanupFailure =
                new RuntimeException("provider-cleanup-failed");
        ProtosTestResourceProviderAdapter unsafe =
                request ->
                        CompletableFuture.completedFuture(
                                new ProtosTestResourceProviderLease(
                                        Map.of(
                                                "db",
                                                new ProtosIntegerValue(
                                                        BigInteger.valueOf(7))),
                                        () -> {
                                            unsafeCleanupCalls.incrementAndGet();
                                            return CompletableFuture.failedFuture(cleanupFailure);
                                        }));

        ProtosTestResourceProviderRegistry registry =
                new ProtosTestResourceProviderRegistry(
                        List.of(
                                new ProtosTestResourceProviderRegistry.Registration(
                                        "device/gpu",
                                        successful),
                                new ProtosTestResourceProviderRegistry.Registration(
                                        "service/unavailable",
                                        unavailable),
                                new ProtosTestResourceProviderRegistry.Registration(
                                        "service/unsafe",
                                        unsafe)));

        ProtosAsyncExactExecutionFacility.Submission submission =
                work -> {
                    Thread carrier =
                            Thread.ofPlatform()
                                    .name("i8d4c1-resourceful")
                                    .unstarted(work);
                    carrier.start();
                    return () -> false;
                };

        try (ProtosPolyglotRuntimeHost runtimeHost = ProtosPolyglotRuntimeHost.open();
                ProtosTestResourcefulExecutionFacility facility =
                        ProtosTestResourcefulExecutionFacility.install(
                                activation,
                                registry,
                                runtimeHost,
                                submission)) {

            String source =
                    """
                    successBindings: Array(
                        Array("gpu", "exclusive", null, "placement", "device/gpu", null)
                    )
                    success: resourceExecutionAsync(
                        "resources[\\"gpu\\"]",
                        successBindings
                    ).value()

                    passed: success.infrastructureFailed.not()
                    passed = passed && success.capacitySafe
                    passed = passed && (success.infrastructureFailures.size() == 0)
                    passed = passed && (success.guestObservation === null).not()
                    passed.ifTrue(() => {
                        passed = success.guestObservation.state === "completed"
                    })
                    passed.ifTrue(() => {
                        passed = success.guestObservation.error === null
                    })
                    passed.ifTrue(() => {
                        passed = success.guestObservation.value == 42
                    })

                    failedBindings: Array(
                        Array(
                            "service",
                            "exclusive",
                            null,
                            "run",
                            "service/unavailable",
                            null
                        )
                    )
                    failed: resourceExecutionAsync(
                        "1",
                        failedBindings
                    ).value()

                    passed = passed && failed.infrastructureFailed
                    passed = passed && failed.capacitySafe
                    passed = passed && (failed.guestObservation === null)
                    passed = passed && (failed.infrastructureFailures.size() == 1)

                    unsafeBindings: Array(
                        Array("db", "exclusive", null, "run", "service/unsafe", null)
                    )
                    unsafe: resourceExecutionAsync(
                        "resources[\\"db\\"]",
                        unsafeBindings
                    ).value()

                    passed = passed && unsafe.infrastructureFailed
                    passed = passed && unsafe.capacitySafe.not()
                    passed = passed && (unsafe.guestObservation === null).not()
                    passed = passed && (unsafe.infrastructureFailures.size() == 1)
                    passed.ifTrue(() => {
                        passed = unsafe.guestObservation.state === "completed"
                    })
                    passed.ifTrue(() => {
                        passed = unsafe.guestObservation.value == 7
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
                            "I8D4C1 root outcome="
                                    + outcome.state()
                                    + ", error="
                                    + outcome.error());
            assertSame(ProtosBooleanValue.TRUE, outcome.value());
        }

        assertEquals(1, successfulCleanupCalls.get());
        assertEquals(1, unsafeCleanupCalls.get());

        String runner = Files.readString(RUNNER, StandardCharsets.UTF_8);
        String main = Files.readString(MAIN, StandardCharsets.UTF_8);
        assertFalse(runner.contains("resourceExecutionAsync"));
        assertTrue(main.contains("resourceExecutionAsync"));
        assertFalse(runner.contains("runInfrastructureFailed"));
        assertFalse(main.contains("runInfrastructureFailed"));
    }

    @Test
    void malformedBindingSnapshotFailsAtPrivateProtocolBoundary() throws Exception {
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
        var activation = prelude.newModuleActivation();

        try (ProtosPolyglotRuntimeHost runtimeHost = ProtosPolyglotRuntimeHost.open();
                ProtosTestResourcefulExecutionFacility facility =
                        ProtosTestResourcefulExecutionFacility.install(
                                activation,
                                ProtosTestResourceProviderRegistry.empty(),
                                runtimeHost,
                                work -> {
                                    Thread carrier =
                                            Thread.ofPlatform()
                                                    .name("i8d4c1-malformed")
                                                    .unstarted(work);
                                    carrier.start();
                                    return () -> false;
                                })) {
            ProtosExecutionOutcome outcome =
                    ProtosRootTaskExecution.execute(
                            new ProtosSourceCompiler()
                                    .compile(
                                            "resourceExecutionAsync(\"1\", Array()).value()"),
                            activation);

            assertEquals(ProtosExecutionOutcome.State.FAILED, outcome.state());
            assertTrue(outcome.error() != null);
        }
    }
}
