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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class ProtosTestToolI8D2ProviderTransactionTest {

    private static final Path MAIN = Path.of("protos", "tools", "test", "Main.protos");
    private static final Path RUNNER = Path.of("protos", "tools", "test", "Runner.protos");
    private static final Path BOOTSTRAP =
            Path.of(
                    "src",
                    "main",
                    "java",
                    "com",
                    "guillermomolina",
                    "protos",
                    "execution",
                    "ProtosStandaloneProcessBootstrap.java");

    @Test
    void resourceFreeProvisioningPerformsZeroRegistryOrProviderWork() {
        AtomicInteger calls = new AtomicInteger();
        ProtosTestResourceProviderAdapter unused =
                request -> {
                    calls.incrementAndGet();
                    return CompletableFuture.completedFuture(
                            lease(Map.of(), () -> {}));
                };

        ProtosTestResourceProviderCoordinator coordinator =
                new ProtosTestResourceProviderCoordinator(
                        new ProtosTestResourceProviderRegistry(
                                List.of(
                                        new ProtosTestResourceProviderRegistry.Registration(
                                                "unused/provider", unused))));

        ProtosTestResourceProviderTransaction transaction =
                coordinator.provision(List.of()).toCompletableFuture().join();

        assertEquals(0, calls.get());
        assertEquals(0, transaction.providerLeaseCount());
        assertTrue(transaction.candidateGuestCapabilities().isEmpty());
        transaction.cleanup().toCompletableFuture().join();
    }

    @Test
    void groupsByProviderInStableFirstAppearanceOrderAndCallsEachProviderOnce() {
        ArrayList<String> calls = new ArrayList<>();

        ProtosTestResourceProviderAdapter gpu =
                request -> {
                    calls.add("gpu:" + keys(request));
                    assertEquals("device/gpu", request.provider());
                    assertEquals(List.of("gpu/compute", "gpu/display"), keys(request));
                    return CompletableFuture.completedFuture(
                            lease(
                                    Map.of(
                                            "gpu/compute", "gpu-capability-1",
                                            "gpu/display", "gpu-capability-2"),
                                    () -> calls.add("cleanup-gpu")));
                };
        ProtosTestResourceProviderAdapter db =
                request -> {
                    calls.add("db:" + keys(request));
                    assertEquals(List.of("db/integration"), keys(request));
                    return CompletableFuture.completedFuture(
                            lease(
                                    Map.of("db/integration", "db-capability"),
                                    () -> calls.add("cleanup-db")));
                };

        ProtosTestResourceProviderCoordinator coordinator =
                new ProtosTestResourceProviderCoordinator(
                        new ProtosTestResourceProviderRegistry(
                                List.of(
                                        registration("device/gpu", gpu),
                                        registration("service/db", db))));

        ProtosTestResourceProviderTransaction transaction =
                coordinator
                        .provision(
                                List.of(
                                        binding(
                                                "gpu/compute",
                                                "shared",
                                                2,
                                                "placement",
                                                "device/gpu",
                                                "a100"),
                                        binding(
                                                "db/integration",
                                                "exclusive",
                                                null,
                                                "run",
                                                "service/db",
                                                null),
                                        binding(
                                                "gpu/display",
                                                "exclusive",
                                                null,
                                                "placement",
                                                "device/gpu",
                                                "a100")))
                        .toCompletableFuture()
                        .join();

        assertEquals(
                List.of(
                        "gpu:[gpu/compute, gpu/display]",
                        "db:[db/integration]"),
                calls);
        assertEquals(2, transaction.providerLeaseCount());
        assertEquals(
                List.of("gpu/compute", "db/integration", "gpu/display"),
                List.copyOf(transaction.candidateGuestCapabilities().keySet()));
        assertEquals(
                "gpu-capability-1",
                transaction.candidateGuestCapabilities().get("gpu/compute"));
        assertEquals(
                "db-capability",
                transaction.candidateGuestCapabilities().get("db/integration"));
        assertEquals(
                "gpu-capability-2",
                transaction.candidateGuestCapabilities().get("gpu/display"));
    }

    @Test
    void asynchronousProviderMustCompleteBeforeTheNextProviderStarts() {
        CompletableFuture<ProtosTestResourceProviderLease> firstPending =
                new CompletableFuture<>();
        AtomicInteger secondCalls = new AtomicInteger();

        ProtosTestResourceProviderAdapter first = request -> firstPending;
        ProtosTestResourceProviderAdapter second =
                request -> {
                    secondCalls.incrementAndGet();
                    return CompletableFuture.completedFuture(
                            lease(Map.of("db", "db-capability"), () -> {}));
                };

        ProtosTestResourceProviderCoordinator coordinator =
                new ProtosTestResourceProviderCoordinator(
                        new ProtosTestResourceProviderRegistry(
                                List.of(
                                        registration("provider/first", first),
                                        registration("provider/second", second))));

        CompletableFuture<ProtosTestResourceProviderTransaction> transaction =
                coordinator
                        .provision(
                                List.of(
                                        binding(
                                                "gpu",
                                                "exclusive",
                                                null,
                                                "placement",
                                                "provider/first",
                                                null),
                                        binding(
                                                "db",
                                                "exclusive",
                                                null,
                                                "run",
                                                "provider/second",
                                                null)))
                        .toCompletableFuture();

        assertFalse(transaction.isDone());
        assertEquals(0, secondCalls.get());

        firstPending.complete(lease(Map.of("gpu", "gpu-capability"), () -> {}));

        ProtosTestResourceProviderTransaction completed = transaction.join();
        assertEquals(1, secondCalls.get());
        assertEquals(2, completed.providerLeaseCount());
    }

    @Test
    void laterProvisionFailureRollsBackEarlierProvidersInReverseOrderAndStops() {
        ArrayList<String> events = new ArrayList<>();
        RuntimeException provisionFailure = new RuntimeException("provider-b-failed");
        AtomicInteger thirdCalls = new AtomicInteger();

        ProtosTestResourceProviderAdapter first =
                request -> {
                    events.add("provision-a");
                    return CompletableFuture.completedFuture(
                            lease(
                                    Map.of("a", "cap-a"),
                                    () -> events.add("cleanup-a")));
                };
        ProtosTestResourceProviderAdapter second =
                request -> {
                    events.add("provision-b");
                    CompletableFuture<ProtosTestResourceProviderLease> failed =
                            new CompletableFuture<>();
                    failed.completeExceptionally(provisionFailure);
                    return failed;
                };
        ProtosTestResourceProviderAdapter third =
                request -> {
                    thirdCalls.incrementAndGet();
                    return CompletableFuture.completedFuture(
                            lease(Map.of("c", "cap-c"), () -> {}));
                };

        ProtosTestResourceProviderCoordinator coordinator =
                new ProtosTestResourceProviderCoordinator(
                        new ProtosTestResourceProviderRegistry(
                                List.of(
                                        registration("provider/a", first),
                                        registration("provider/b", second),
                                        registration("provider/c", third))));

        CompletionException failure =
                assertThrows(
                        CompletionException.class,
                        () ->
                                coordinator
                                        .provision(
                                                List.of(
                                                        binding(
                                                                "a",
                                                                "exclusive",
                                                                null,
                                                                "run",
                                                                "provider/a",
                                                                null),
                                                        binding(
                                                                "b",
                                                                "exclusive",
                                                                null,
                                                                "run",
                                                                "provider/b",
                                                                null),
                                                        binding(
                                                                "c",
                                                                "exclusive",
                                                                null,
                                                                "run",
                                                                "provider/c",
                                                                null)))
                                        .toCompletableFuture()
                                        .join());

        assertSame(provisionFailure, failure.getCause());
        assertEquals(
                List.of("provision-a", "provision-b", "cleanup-a"),
                events);
        assertEquals(0, thirdCalls.get());
    }

    @Test
    void invalidLeaseCoverageCleansCurrentLeaseThenEarlierLease() {
        ArrayList<String> events = new ArrayList<>();

        ProtosTestResourceProviderAdapter first =
                request ->
                        CompletableFuture.completedFuture(
                                lease(
                                        Map.of("a", "cap-a"),
                                        () -> events.add("cleanup-a")));
        ProtosTestResourceProviderAdapter second =
                request ->
                        CompletableFuture.completedFuture(
                                lease(
                                        Map.of("unexpected", "wrong"),
                                        () -> events.add("cleanup-b")));

        ProtosTestResourceProviderCoordinator coordinator =
                new ProtosTestResourceProviderCoordinator(
                        new ProtosTestResourceProviderRegistry(
                                List.of(
                                        registration("provider/a", first),
                                        registration("provider/b", second))));

        CompletionException failure =
                assertThrows(
                        CompletionException.class,
                        () ->
                                coordinator
                                        .provision(
                                                List.of(
                                                        binding(
                                                                "a",
                                                                "exclusive",
                                                                null,
                                                                "run",
                                                                "provider/a",
                                                                null),
                                                        binding(
                                                                "b",
                                                                "exclusive",
                                                                null,
                                                                "run",
                                                                "provider/b",
                                                                null)))
                                        .toCompletableFuture()
                                        .join());

        assertTrue(
                failure.getCause()
                        instanceof
                        ProtosTestResourceProviderCoordinator
                                .InvalidLeaseCoverageException);
        assertEquals(List.of("cleanup-b", "cleanup-a"), events);
    }

    @Test
    void successfulTransactionCleanupIsReverseOrderAndAttemptsAllAfterFailure() {
        ArrayList<String> events = new ArrayList<>();
        RuntimeException cleanupBFailure = new RuntimeException("cleanup-b-failed");

        ProtosTestResourceProviderAdapter a =
                request ->
                        CompletableFuture.completedFuture(
                                lease(
                                        Map.of("a", "cap-a"),
                                        () -> events.add("cleanup-a")));
        ProtosTestResourceProviderAdapter b =
                request ->
                        CompletableFuture.completedFuture(
                                new ProtosTestResourceProviderLease(
                                        Map.of("b", "cap-b"),
                                        () -> {
                                            events.add("cleanup-b");
                                            CompletableFuture<Void> failed =
                                                    new CompletableFuture<>();
                                            failed.completeExceptionally(cleanupBFailure);
                                            return failed;
                                        }));
        ProtosTestResourceProviderAdapter c =
                request ->
                        CompletableFuture.completedFuture(
                                lease(
                                        Map.of("c", "cap-c"),
                                        () -> events.add("cleanup-c")));

        ProtosTestResourceProviderCoordinator coordinator =
                new ProtosTestResourceProviderCoordinator(
                        new ProtosTestResourceProviderRegistry(
                                List.of(
                                        registration("provider/a", a),
                                        registration("provider/b", b),
                                        registration("provider/c", c))));

        ProtosTestResourceProviderTransaction transaction =
                coordinator
                        .provision(
                                List.of(
                                        binding(
                                                "a",
                                                "exclusive",
                                                null,
                                                "run",
                                                "provider/a",
                                                null),
                                        binding(
                                                "b",
                                                "exclusive",
                                                null,
                                                "run",
                                                "provider/b",
                                                null),
                                        binding(
                                                "c",
                                                "exclusive",
                                                null,
                                                "run",
                                                "provider/c",
                                                null)))
                        .toCompletableFuture()
                        .join();

        CompletionException cleanupFailure =
                assertThrows(
                        CompletionException.class,
                        () -> transaction.cleanup().toCompletableFuture().join());

        assertSame(cleanupBFailure, cleanupFailure.getCause());
        assertEquals(List.of("cleanup-c", "cleanup-b", "cleanup-a"), events);
    }

    @Test
    void duplicateResourceKeysFailBeforeAnyProviderCall() {
        AtomicInteger calls = new AtomicInteger();
        ProtosTestResourceProviderAdapter provider =
                request -> {
                    calls.incrementAndGet();
                    return CompletableFuture.completedFuture(
                            lease(Map.of("dup", "capability"), () -> {}));
                };

        ProtosTestResourceProviderCoordinator coordinator =
                new ProtosTestResourceProviderCoordinator(
                        new ProtosTestResourceProviderRegistry(
                                List.of(registration("provider/a", provider))));

        CompletionException failure =
                assertThrows(
                        CompletionException.class,
                        () ->
                                coordinator
                                        .provision(
                                                List.of(
                                                        binding(
                                                                "dup",
                                                                "shared",
                                                                1,
                                                                "run",
                                                                "provider/a",
                                                                null),
                                                        binding(
                                                                "dup",
                                                                "shared",
                                                                1,
                                                                "run",
                                                                "provider/a",
                                                                null)))
                                        .toCompletableFuture()
                                        .join());

        assertTrue(
                failure.getCause()
                        instanceof
                        ProtosTestResourceProviderCoordinator
                                .DuplicateResourceKeyException);
        assertEquals(0, calls.get());
    }

    @Test
    void i8d2StillDoesNotWireProcessRunnerOrPublicMain() throws Exception {
        String main = Files.readString(MAIN, StandardCharsets.UTF_8);
        String runner = Files.readString(RUNNER, StandardCharsets.UTF_8);
        String bootstrap = Files.readString(BOOTSTRAP, StandardCharsets.UTF_8);

        assertFalse(main.contains("ProtosTestResourceProviderCoordinator"));
        assertFalse(runner.contains("ProtosTestResourceProviderCoordinator"));
        assertFalse(bootstrap.contains("\"resources\""));
    }

    private static ProtosTestResourceProviderRegistry.Registration registration(
            String provider, ProtosTestResourceProviderAdapter adapter) {
        return new ProtosTestResourceProviderRegistry.Registration(provider, adapter);
    }

    private static ProtosTestResourceProviderRequest.Binding binding(
            String key,
            String mode,
            Integer units,
            String scope,
            String provider,
            String profile) {
        return new ProtosTestResourceProviderRequest.Binding(
                key,
                mode,
                units == null ? null : BigInteger.valueOf(units),
                scope,
                provider,
                profile);
    }

    private static List<String> keys(ProtosTestResourceProviderRequest request) {
        ArrayList<String> keys = new ArrayList<>();
        for (ProtosTestResourceProviderRequest.Binding binding : request.bindings()) {
            keys.add(binding.resourceKey());
        }
        return List.copyOf(keys);
    }

    private static ProtosTestResourceProviderLease lease(
            Map<String, ?> capabilities, Runnable cleanup) {
        return new ProtosTestResourceProviderLease(
                capabilities,
                () -> {
                    cleanup.run();
                    return CompletableFuture.completedFuture(null);
                });
    }
}
