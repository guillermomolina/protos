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

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

/**
 * Private TOOL002-I8D2 D107 multi-provider attempt transaction coordinator.
 *
 * <p>Input bindings are already scheduler-reserved facts. The coordinator groups them by logical
 * provider in stable first-appearance order, resolves each provider exactly through the explicit
 * environment registry, provisions providers sequentially in that order, validates exact lease
 * capability coverage, and returns a host-owned successful transaction. It does not create a
 * Process, project a guest {@code resources} local, touch Runner/Main or release I8B capacity.
 */
final class ProtosTestResourceProviderCoordinator {

    static final class InvalidLeaseCoverageException extends IllegalStateException {
        InvalidLeaseCoverageException(String provider, Set<String> required, Set<String> actual) {
            super(
                    "provider "
                            + provider
                            + " returned invalid resource capability coverage; required="
                            + required
                            + ", actual="
                            + actual);
        }
    }

    static final class DuplicateResourceKeyException extends IllegalArgumentException {
        DuplicateResourceKeyException(String key) {
            super("duplicate resource key in provider transaction: " + key);
        }
    }

    private record ProviderGroup(
            String provider,
            ProtosTestResourceProviderRequest request,
            List<String> requiredKeys) {}

    private final ProtosTestResourceProviderRegistry registry;

    ProtosTestResourceProviderCoordinator(ProtosTestResourceProviderRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    CompletionStage<ProtosTestResourceProviderTransaction> provision(
            List<ProtosTestResourceProviderRequest.Binding> bindings) {
        Objects.requireNonNull(bindings, "bindings");

        final List<ProtosTestResourceProviderRequest.Binding> snapshot;
        final List<ProviderGroup> groups;
        try {
            snapshot = List.copyOf(bindings);
            groups = groupBindings(snapshot);
        } catch (RuntimeException failure) {
            return failedStage(failure);
        }

        if (snapshot.isEmpty()) {
            return CompletableFuture.completedFuture(
                    new ProtosTestResourceProviderTransaction(Map.of(), List.of()));
        }

        ArrayList<ProtosTestResourceProviderLease> acquiredLeases = new ArrayList<>();
        LinkedHashMap<String, Object> capabilitiesByKey = new LinkedHashMap<>();

        return provisionGroup(
                        groups,
                        0,
                        acquiredLeases,
                        capabilitiesByKey)
                .thenApply(
                        ignored -> {
                            LinkedHashMap<String, Object> orderedCapabilities =
                                    new LinkedHashMap<>();
                            for (ProtosTestResourceProviderRequest.Binding binding : snapshot) {
                                Object capability = capabilitiesByKey.get(binding.resourceKey());
                                if (capability == null) {
                                    throw new IllegalStateException(
                                            "successful provider transaction lost capability for "
                                                    + binding.resourceKey());
                                }
                                orderedCapabilities.put(binding.resourceKey(), capability);
                            }
                            return new ProtosTestResourceProviderTransaction(
                                    orderedCapabilities, acquiredLeases);
                        });
    }

    private CompletionStage<Void> provisionGroup(
            List<ProviderGroup> groups,
            int index,
            ArrayList<ProtosTestResourceProviderLease> acquiredLeases,
            LinkedHashMap<String, Object> capabilitiesByKey) {
        if (index >= groups.size()) {
            return CompletableFuture.completedFuture(null);
        }

        ProviderGroup group = groups.get(index);

        final ProtosTestResourceProviderAdapter adapter;
        try {
            adapter = registry.resolve(group.provider());
        } catch (RuntimeException failure) {
            return rollbackAndFail(acquiredLeases, failure);
        }

        final CompletionStage<ProtosTestResourceProviderLease> provisionStage;
        try {
            provisionStage =
                    Objects.requireNonNull(
                            adapter.provision(group.request()),
                            "provider provisioning stage");
        } catch (RuntimeException failure) {
            return rollbackAndFail(acquiredLeases, failure);
        }

        return provisionStage
                .handle(
                        (lease, failure) ->
                                new ProvisionOutcome(
                                        lease,
                                        failure == null
                                                ? null
                                                : unwrapCompletionFailure(failure)))
                .thenCompose(
                        outcome -> {
                            if (outcome.failure() != null) {
                                return rollbackAndFail(
                                        acquiredLeases, outcome.failure());
                            }

                            ProtosTestResourceProviderLease lease =
                                    Objects.requireNonNull(
                                            outcome.lease(), "provider lease");

                            RuntimeException coverageFailure =
                                    validateLeaseCoverage(group, lease);
                            if (coverageFailure != null) {
                                ArrayList<ProtosTestResourceProviderLease> rollback =
                                        new ArrayList<>(acquiredLeases);
                                rollback.add(lease);
                                return rollbackAndFail(rollback, coverageFailure);
                            }

                            for (String key : group.requiredKeys()) {
                                Object previous =
                                        capabilitiesByKey.put(
                                                key, lease.guestCapabilities().get(key));
                                if (previous != null) {
                                    ArrayList<ProtosTestResourceProviderLease> rollback =
                                            new ArrayList<>(acquiredLeases);
                                    rollback.add(lease);
                                    return rollbackAndFail(
                                            rollback,
                                            new DuplicateResourceKeyException(key));
                                }
                            }

                            acquiredLeases.add(lease);
                            return provisionGroup(
                                    groups,
                                    index + 1,
                                    acquiredLeases,
                                    capabilitiesByKey);
                        });
    }

    private record ProvisionOutcome(
            ProtosTestResourceProviderLease lease, Throwable failure) {}

    private static List<ProviderGroup> groupBindings(
            List<ProtosTestResourceProviderRequest.Binding> bindings) {
        LinkedHashMap<String, ArrayList<ProtosTestResourceProviderRequest.Binding>> byProvider =
                new LinkedHashMap<>();
        LinkedHashSet<String> seenKeys = new LinkedHashSet<>();

        for (ProtosTestResourceProviderRequest.Binding binding : bindings) {
            Objects.requireNonNull(binding, "binding");
            if (!seenKeys.add(binding.resourceKey())) {
                throw new DuplicateResourceKeyException(binding.resourceKey());
            }
            byProvider.computeIfAbsent(binding.provider(), ignored -> new ArrayList<>())
                    .add(binding);
        }

        ArrayList<ProviderGroup> groups = new ArrayList<>();
        for (Map.Entry<String, ArrayList<ProtosTestResourceProviderRequest.Binding>> entry :
                byProvider.entrySet()) {
            List<ProtosTestResourceProviderRequest.Binding> providerBindings =
                    List.copyOf(entry.getValue());
            ArrayList<String> requiredKeys = new ArrayList<>();
            for (ProtosTestResourceProviderRequest.Binding binding : providerBindings) {
                requiredKeys.add(binding.resourceKey());
            }
            groups.add(
                    new ProviderGroup(
                            entry.getKey(),
                            new ProtosTestResourceProviderRequest(
                                    entry.getKey(), providerBindings),
                            List.copyOf(requiredKeys)));
        }
        return List.copyOf(groups);
    }

    private static RuntimeException validateLeaseCoverage(
            ProviderGroup group,
            ProtosTestResourceProviderLease lease) {
        Set<String> required =
                Collections.unmodifiableSet(new LinkedHashSet<>(group.requiredKeys()));
        Set<String> actual =
                Collections.unmodifiableSet(
                        new LinkedHashSet<>(lease.guestCapabilities().keySet()));
        if (!required.equals(actual)) {
            return new InvalidLeaseCoverageException(group.provider(), required, actual);
        }
        return null;
    }

    private static CompletionStage<Void> rollbackAndFail(
            List<ProtosTestResourceProviderLease> acquiredLeases,
            Throwable primaryFailure) {
        return cleanupLeasesReverse(acquiredLeases, primaryFailure)
                .thenCompose(failure -> failedStage(failure));
    }

    /**
     * Attempts all lease cleanups in reverse list order and returns accumulated infrastructure
     * failure evidence without throwing from the cleanup walk itself.
     */
    static CompletionStage<Throwable> cleanupLeasesReverse(
            List<ProtosTestResourceProviderLease> leases,
            Throwable primaryFailure) {
        Objects.requireNonNull(leases, "leases");
        CompletionStage<Throwable> aggregate =
                CompletableFuture.completedFuture(primaryFailure);

        for (int index = leases.size() - 1; index >= 0; index--) {
            ProtosTestResourceProviderLease lease =
                    Objects.requireNonNull(leases.get(index), "provider lease");
            aggregate =
                    aggregate.thenCompose(
                            current ->
                                    safeCleanup(lease)
                                            .handle(
                                                    (ignored, failure) ->
                                                            mergeFailure(
                                                                    current,
                                                                    failure == null
                                                                            ? null
                                                                            : unwrapCompletionFailure(
                                                                                    failure))));
        }

        return aggregate;
    }

    private static CompletionStage<Void> safeCleanup(
            ProtosTestResourceProviderLease lease) {
        try {
            return Objects.requireNonNull(lease.cleanup(), "provider cleanup stage");
        } catch (RuntimeException failure) {
            return failedStage(failure);
        }
    }

    private static Throwable mergeFailure(Throwable primary, Throwable additional) {
        if (additional == null) {
            return primary;
        }
        if (primary == null) {
            return additional;
        }
        if (primary != additional) {
            primary.addSuppressed(additional);
        }
        return primary;
    }

    private static Throwable unwrapCompletionFailure(Throwable failure) {
        Throwable current = failure;
        while (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static <T> CompletionStage<T> failedStage(Throwable failure) {
        CompletableFuture<T> failed = new CompletableFuture<>();
        failed.completeExceptionally(Objects.requireNonNull(failure, "failure"));
        return failed;
    }
}
