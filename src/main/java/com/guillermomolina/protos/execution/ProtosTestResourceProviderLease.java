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

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

/**
 * Host-owned D107 provider lease.
 *
 * <p>The lease is never guest authority. It snapshots the provider's proposed guest capability
 * values while retaining provider-private cleanup machinery in the host. A later transaction
 * coordinator validates exact required-key coverage before any capability bundle can be projected
 * into a child Process.
 */
final class ProtosTestResourceProviderLease {

    private final Map<String, Object> guestCapabilities;
    private final Supplier<? extends CompletionStage<Void>> cleanup;

    ProtosTestResourceProviderLease(
            Map<String, ?> guestCapabilities,
            Supplier<? extends CompletionStage<Void>> cleanup) {
        Objects.requireNonNull(guestCapabilities, "guestCapabilities");
        this.guestCapabilities = Map.copyOf(guestCapabilities);
        this.cleanup = Objects.requireNonNull(cleanup, "cleanup");
    }

    /**
     * Returns the immutable host snapshot of candidate guest capability values keyed by exact D077
     * resource key.
     */
    Map<String, Object> guestCapabilities() {
        return guestCapabilities;
    }

    /**
     * Starts provider-owned cleanup.
     *
     * <p>The result is normalized to a CompletionStage so later transaction/lifecycle code can
     * await both synchronous local and genuinely asynchronous providers through one boundary.
     */
    CompletionStage<Void> cleanup() {
        try {
            CompletionStage<Void> stage = cleanup.get();
            return Objects.requireNonNull(stage, "provider cleanup stage");
        } catch (RuntimeException failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }
}
