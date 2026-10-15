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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Host-owned successful D107 attempt provisioning transaction.
 *
 * <p>This is not the guest {@code resources} bundle. It keeps successful provider leases in the
 * Test Tool host and exposes only an immutable ordered snapshot of candidate guest capability
 * values for the later RootActor projection slice.
 */
final class ProtosTestResourceProviderTransaction {

    private final Map<String, Object> candidateGuestCapabilities;
    private final List<ProtosTestResourceProviderLease> leases;

    ProtosTestResourceProviderTransaction(
            Map<String, ?> candidateGuestCapabilities,
            List<ProtosTestResourceProviderLease> leases) {
        Objects.requireNonNull(candidateGuestCapabilities, "candidateGuestCapabilities");
        Objects.requireNonNull(leases, "leases");
        this.candidateGuestCapabilities =
                Collections.unmodifiableMap(new LinkedHashMap<>(candidateGuestCapabilities));
        this.leases = List.copyOf(leases);
    }

    Map<String, Object> candidateGuestCapabilities() {
        return candidateGuestCapabilities;
    }

    int providerLeaseCount() {
        return leases.size();
    }

    /**
     * Cleans every successful provider lease in strict reverse acquisition order.
     *
     * <p>All cleanups are attempted even if one fails. The first cleanup failure remains primary
     * and later cleanup failures are attached as suppressed infrastructure evidence.
     */
    CompletionStage<Void> cleanup() {
        return ProtosTestResourceProviderCoordinator.cleanupLeasesReverse(leases, null)
                .thenCompose(
                        failure ->
                                failure == null
                                        ? CompletableFuture.completedFuture(null)
                                        : failedStage(failure));
    }

    private static CompletionStage<Void> failedStage(Throwable failure) {
        CompletableFuture<Void> failed = new CompletableFuture<>();
        failed.completeExceptionally(failure);
        return failed;
    }
}
