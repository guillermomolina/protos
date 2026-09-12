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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable exact D107 environment provider registry.
 *
 * <p>No ambient discovery exists here: no classpath scan, ServiceLoader, PATH lookup, module/import
 * lookup, URI/package interpretation, aliases or fallback chain. The environment explicitly
 * supplies every logical D098 provider identity and its configured adapter.
 */
final class ProtosTestResourceProviderRegistry {

    record Registration(String provider, ProtosTestResourceProviderAdapter adapter) {
        Registration {
            Objects.requireNonNull(provider, "provider");
            Objects.requireNonNull(adapter, "adapter");
        }
    }

    static final class UnknownProviderException extends IllegalStateException {
        UnknownProviderException(String provider) {
            super("unresolved Test Tool resource provider: " + provider);
        }
    }

    private final Map<String, ProtosTestResourceProviderAdapter> adapters;

    ProtosTestResourceProviderRegistry(List<Registration> registrations) {
        Objects.requireNonNull(registrations, "registrations");

        LinkedHashMap<String, ProtosTestResourceProviderAdapter> snapshot =
                new LinkedHashMap<>();
        for (Registration registration : List.copyOf(registrations)) {
            ProtosTestResourceProviderAdapter previous =
                    snapshot.putIfAbsent(registration.provider(), registration.adapter());
            if (previous != null) {
                throw new IllegalArgumentException(
                        "duplicate Test Tool resource provider registration: "
                                + registration.provider());
            }
        }

        adapters = Map.copyOf(snapshot);
    }

    static ProtosTestResourceProviderRegistry empty() {
        return new ProtosTestResourceProviderRegistry(List.of());
    }

    ProtosTestResourceProviderAdapter resolve(String provider) {
        Objects.requireNonNull(provider, "provider");
        ProtosTestResourceProviderAdapter adapter = adapters.get(provider);
        if (adapter == null) {
            throw new UnknownProviderException(provider);
        }
        return adapter;
    }

    int size() {
        return adapters.size();
    }
}
