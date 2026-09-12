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

import java.math.BigInteger;
import java.util.List;
import java.util.Objects;

/**
 * Immutable host-side D107 provisioning request for exactly one logical provider.
 *
 * <p>The fields are inert scheduler/catalog facts already established by D076/D077/D097/D098.
 * This carrier performs no provider discovery, resource admission, profile inference or placement
 * selection.
 */
final class ProtosTestResourceProviderRequest {

    /**
     * One already-bound resource requirement/catalog fact inside a provider group.
     *
     * <p>{@code units} is null for the already-canonical exclusive mode and {@code profile} is null
     * when D098 profile identity is absent. Their semantic validation remains owned by the
     * previously closed Protos-side representation/binding slices.
     */
    record Binding(
            String resourceKey,
            String mode,
            BigInteger units,
            String scope,
            String provider,
            String profile) {

        Binding {
            Objects.requireNonNull(resourceKey, "resourceKey");
            Objects.requireNonNull(mode, "mode");
            Objects.requireNonNull(scope, "scope");
            Objects.requireNonNull(provider, "provider");
        }
    }

    private final String provider;
    private final List<Binding> bindings;

    ProtosTestResourceProviderRequest(String provider, List<Binding> bindings) {
        this.provider = Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(bindings, "bindings");
        if (bindings.isEmpty()) {
            throw new IllegalArgumentException(
                    "provider provisioning request requires at least one binding");
        }

        List<Binding> snapshot = List.copyOf(bindings);
        for (Binding binding : snapshot) {
            if (!provider.equals(binding.provider())) {
                throw new IllegalArgumentException(
                        "provider provisioning request cannot mix logical providers");
            }
        }
        this.bindings = snapshot;
    }

    String provider() {
        return provider;
    }

    List<Binding> bindings() {
        return bindings;
    }
}
