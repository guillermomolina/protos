/*
 * THE LICENSED WORK IS PROVIDED UNDER THE TERMS OF THE ADAPTIVE PUBLIC LICENSE
 * ("LICENSE") AS FIRST COMPLETED BY: Guillermo Adrián Molina. ANY USE, PUBLIC
 * DISPLAY, PUBLIC PERFORMANCE, REPRODUCTION OR DISTRIBUTION OF, OR PREPARATION OF
 * DERIVATIVE WORKS BASED ON, THE LICENSED WORK CONSTITUTES RECIPIENT'S ACCEPTANCE
 * OF THIS LICENSE AND ITS TERMS, WHETHER OR NOT SUCH RECIPIENT READS THE TERMS OF
 * THE LICENSE. "LICENSED WORK" AND "RECIPIENT" ARE DEFINED IN THE LICENSE. A COPY
 * OF THE LICENSE IS LOCATED IN THE TEXT FILE ENTITLED "LICENSE.TXT" ACCOMPANYING
 * THE CONTENTS OF THIS FILE.
 */
package com.guillermomolina.protos.execution;

import com.guillermomolina.protos.runtime.ProtosModuleKey;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Host-selected exact module overlay in front of another resolver.
 *
 * <p>The overlay performs no discovery, relative lookup, aliasing, fallback search or naming
 * policy. A host explicitly supplies the accepted source specifier, canonical ModuleKey and exact
 * source path for each overlay entry. All other resolution and source loading is delegated to the
 * fallback resolver unchanged.
 */
public final class ProtosExactModuleOverlayResolver implements ProtosModuleResolver {
    public record ExactModule(ProtosModuleKey key, Path source) {
        public ExactModule {
            Objects.requireNonNull(key, "key");
            source = Objects.requireNonNull(source, "source").toAbsolutePath().normalize();
        }
    }

    private final Map<String, ExactModule> modulesBySpecifier;
    private final Map<ProtosModuleKey, ExactModule> modulesByKey;
    private final ProtosModuleResolver fallback;

    public ProtosExactModuleOverlayResolver(
            Map<String, ExactModule> exactModules,
            ProtosModuleResolver fallback) {
        Objects.requireNonNull(exactModules, "exactModules");
        this.fallback = Objects.requireNonNull(fallback, "fallback");

        LinkedHashMap<String, ExactModule> bySpecifier = new LinkedHashMap<>();
        LinkedHashMap<ProtosModuleKey, ExactModule> byKey = new LinkedHashMap<>();
        for (Map.Entry<String, ExactModule> entry : exactModules.entrySet()) {
            String specifier = Objects.requireNonNull(entry.getKey(), "exact module specifier");
            if (specifier.isEmpty()) {
                throw new IllegalArgumentException("exact module specifier must not be empty");
            }
            ExactModule module = Objects.requireNonNull(entry.getValue(), "exact module");
            bySpecifier.put(specifier, module);
            if (byKey.putIfAbsent(module.key(), module) != null) {
                throw new IllegalArgumentException(
                        "exact module overlay contains duplicate canonical ModuleKey");
            }
        }
        this.modulesBySpecifier = Map.copyOf(bySpecifier);
        this.modulesByKey = Map.copyOf(byKey);
    }

    @Override
    public ProtosModuleKey resolve(
            String exactSpecifier,
            Optional<ProtosModuleKey> importingModule)
            throws Exception {
        Objects.requireNonNull(exactSpecifier, "exactSpecifier");
        Objects.requireNonNull(importingModule, "importingModule");
        ExactModule exact = modulesBySpecifier.get(exactSpecifier);
        if (exact != null) {
            return exact.key();
        }
        return fallback.resolve(exactSpecifier, importingModule);
    }

    @Override
    public ProtosModuleSource loadSource(ProtosModuleKey key) throws Exception {
        Objects.requireNonNull(key, "key");
        ExactModule exact = modulesByKey.get(key);
        if (exact != null) {
            return ProtosModuleSource.fromPath(key, exact.source());
        }
        return fallback.loadSource(key);
    }
}
