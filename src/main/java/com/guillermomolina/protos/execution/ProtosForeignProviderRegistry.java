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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable registry of host-supplied foreign providers owned by one RuntimeHost (PLAT053).
 *
 * <p>The registry is fixed when its RuntimeHost is constructed. It performs no discovery, is not a
 * global singleton, is not guest-visible, and never invokes provider factories.
 */
final class ProtosForeignProviderRegistry {
    static final ProtosForeignProviderRegistry EMPTY = new ProtosForeignProviderRegistry(Map.of());

    private final Map<ProtosForeignProviderId, ProtosForeignProviderDescriptor> providers;

    private ProtosForeignProviderRegistry(
            Map<ProtosForeignProviderId, ProtosForeignProviderDescriptor> providers) {
        this.providers = providers;
    }

    /** Builds a registry, rejecting the first repeated identity in the supplied order. */
    static ProtosForeignProviderRegistry of(List<ProtosForeignProviderDescriptor> descriptors) {
        Objects.requireNonNull(descriptors, "descriptors");
        if (descriptors.isEmpty()) {
            return EMPTY;
        }
        Map<ProtosForeignProviderId, ProtosForeignProviderDescriptor> providers = new HashMap<>();
        for (ProtosForeignProviderDescriptor descriptor : descriptors) {
            Objects.requireNonNull(descriptor, "descriptor");
            if (providers.putIfAbsent(descriptor.id(), descriptor) != null) {
                throw new IllegalArgumentException(
                        "duplicate foreign provider identity: " + descriptor.id().value());
            }
        }
        return new ProtosForeignProviderRegistry(Map.copyOf(providers));
    }

    Optional<ProtosForeignProviderDescriptor> lookup(ProtosForeignProviderId id) {
        return Optional.ofNullable(providers.get(Objects.requireNonNull(id, "id")));
    }

    int size() {
        return providers.size();
    }

    boolean isEmpty() {
        return providers.isEmpty();
    }
}
