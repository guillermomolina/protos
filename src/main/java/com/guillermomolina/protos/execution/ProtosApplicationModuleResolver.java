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

import com.guillermomolina.protos.runtime.ProtosModuleKey;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * PLAT055 application module catalog in front of the standard-library resolver of one embedded
 * Process ({@link ProtosEmbeddedModules}).
 *
 * <p>The canonical {@link ProtosModuleKey} of an entry is exactly its authorized {@code app:}
 * specifier, and its source is the in-memory characters the host supplied: no relative lookup,
 * aliasing, normalization, extension inference, or filesystem/classpath search takes place. Every
 * other specifier, including an unauthorized {@code app:} one, is delegated unchanged to the
 * fallback, so {@code std:} and the exclusive {@code foreign:} key domain are never affected.
 */
final class ProtosApplicationModuleResolver implements ProtosModuleResolver {
    static final String PREFIX = "app:";

    private final Map<String, String> sources;
    private final ProtosModuleResolver fallback;

    private ProtosApplicationModuleResolver(
            Map<String, String> sources, ProtosModuleResolver fallback) {
        this.sources = sources;
        this.fallback = fallback;
    }

    /**
     * The resolver of a Process whose Context holds {@code catalog} (an authorized catalog, or
     * {@code null} when none was installed). Without entries the fallback is returned unchanged,
     * so a Context that does not use the catalog pays nothing for it.
     */
    static ProtosModuleResolver over(Map<String, String> catalog, ProtosModuleResolver fallback) {
        Objects.requireNonNull(fallback, "fallback");
        return catalog == null || catalog.isEmpty()
                ? fallback
                : new ProtosApplicationModuleResolver(catalog, fallback);
    }

    /**
     * Validates {@code modules} and returns an immutable copy of it. The input is read exactly
     * once, so a later change to it, or a concurrent one, cannot reach the published catalog, and
     * an invalid entry fails before anything is published.
     */
    static Map<String, String> authorizedCatalog(Map<String, String> modules) {
        Objects.requireNonNull(modules, "modules");
        HashMap<String, String> copy = new HashMap<>();
        for (Map.Entry<String, String> entry : modules.entrySet()) {
            String specifier = Objects.requireNonNull(entry.getKey(), "module specifier");
            String characters =
                    Objects.requireNonNull(entry.getValue(), "source of module " + specifier);
            if (!specifier.startsWith(PREFIX) || specifier.length() == PREFIX.length()) {
                throw new IllegalArgumentException(
                        "an application module specifier must be \"app:\" followed by a"
                                + " non-empty name: "
                                + specifier);
            }
            copy.put(specifier, characters);
        }
        return Map.copyOf(copy);
    }

    @Override
    public ProtosModuleKey resolve(
            String exactSpecifier, Optional<ProtosModuleKey> importingModule) throws Exception {
        Objects.requireNonNull(exactSpecifier, "exactSpecifier");
        if (sources.containsKey(exactSpecifier)) {
            return new ProtosModuleKey(exactSpecifier);
        }
        return fallback.resolve(exactSpecifier, importingModule);
    }

    @Override
    public ProtosModuleSource loadSource(ProtosModuleKey key) throws Exception {
        Objects.requireNonNull(key, "key");
        String characters = sources.get(key.canonicalId());
        if (characters != null) {
            return ProtosModuleSource.fromCharacters(key, characters);
        }
        return fallback.loadSource(key);
    }
}
