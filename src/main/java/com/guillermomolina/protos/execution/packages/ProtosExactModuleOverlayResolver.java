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

import com.guillermomolina.protos.runtime.modules.ProtosModuleKey;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Host-selected exact module overlay in front of another resolver.
 *
 * <p>The overlay performs no discovery, aliasing or fallback search. A host explicitly supplies
 * the accepted source specifier, canonical ModuleKey and exact source path for each overlay entry.
 * Relative SOURCE imports from an overlay entry are confined to other exact authorized SOURCE
 * entries; relative aliases and MODULE initializer SOURCE aliases are forbidden. Other imports
 * are delegated to the fallback resolver unchanged.
 */
public final class ProtosExactModuleOverlayResolver implements ProtosModuleResolver {
    public record ExactModule(ProtosModuleKey key, Path source) {
        public ExactModule {
            Objects.requireNonNull(key, "key");
            source = Objects.requireNonNull(source, "source").toAbsolutePath().normalize();
            Path name = source.getFileName();
            if (name == null) {
                throw new IllegalArgumentException("exact overlay source has no filename");
            }
            String filename = name.toString();
            if (key.isModule()) {
                if (!filename.equals("module.protos")) {
                    throw new IllegalArgumentException(
                            "MODULE overlay must name its exact module.protos initializer");
                }
            } else if (key.isSource()) {
                if (!filename.endsWith(".protos")
                        || filename.equalsIgnoreCase("module.protos")) {
                    throw new IllegalArgumentException(
                            "SOURCE overlay must name an exact non-initializer .protos file");
                }
            } else {
                throw new IllegalArgumentException(
                        "foreign module keys are not source-backed overlay entries");
            }
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
        LinkedHashMap<Path, ProtosModuleKey> keysBySource = new LinkedHashMap<>();
        for (Map.Entry<String, ExactModule> entry : exactModules.entrySet()) {
            String specifier = Objects.requireNonNull(entry.getKey(), "exact module specifier");
            if (specifier.isEmpty()) {
                throw new IllegalArgumentException("exact module specifier must not be empty");
            }
            // Relative SOURCE imports have physical, importer-scoped authority.
            // They must not be captured by an unrelated global overlay alias.
            if (specifier.startsWith("./") || specifier.startsWith("../")) {
                throw new IllegalArgumentException(
                        "relative SOURCE imports cannot be overlay specifier aliases");
            }
            ExactModule module = Objects.requireNonNull(entry.getValue(), "exact module");
            bySpecifier.put(specifier, module);
            if (byKey.putIfAbsent(module.key(), module) != null) {
                throw new IllegalArgumentException(
                        "exact module overlay contains duplicate canonical ModuleKey");
            }
            if (keysBySource.putIfAbsent(module.source(), module.key()) != null) {
                throw new IllegalArgumentException(
                        "exact overlay source has multiple canonical ModuleKeys");
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
        boolean relative =
                exactSpecifier.startsWith("./") || exactSpecifier.startsWith("../");
        if (relative) {
            if (!ProtosPackageRuntimeNames.isRelativeSourceSpecifier(exactSpecifier)) {
                throw new java.io.IOException(
                        "relative SOURCE import must end in .protos");
            }
            ExactModule importer = importingModule.map(modulesByKey::get).orElse(null);
            if (importer != null) {
                // Only the exact authorized SOURCE file at the relative physical path
                // can be selected. MODULE initializers cannot be imported as SOURCE.
                Path target = importer.source().getParent().resolve(exactSpecifier).normalize();
                for (ExactModule candidate : modulesByKey.values()) {
                    if (candidate.key().isSource() && candidate.source().equals(target)) {
                        return candidate.key();
                    }
                }
                throw new java.io.IOException(
                        "relative SOURCE import is outside the exact overlay");
            }
        }
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
