/*
 * THE LICENSED WORK IS PROVIDED UNDER THE TERMS OF THE ADAPTIVE PUBLIC LICENSE
 * ("LICENSE") AS FIRST COMPLETED BY: Guillermo Adrián Molina. See LICENSE.TXT.
 */
package com.guillermomolina.protos.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.guillermomolina.protos.runtime.ProtosModuleKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ProtosDirectFileModuleResolverTest {
    @Test
    void resolvesImporterRelativeNamesAndNormalizesAliases(@TempDir Path root)
            throws Exception {
        Path entry = root.resolve("main.protos");
        Path sub = Files.createDirectory(root.resolve("sub"));
        Path a = sub.resolve("a.protos");
        Path b = root.resolve("b.protos");
        Files.writeString(entry, "entry", StandardCharsets.UTF_8);
        Files.writeString(a, "a", StandardCharsets.UTF_8);
        Files.writeString(b, "b", StandardCharsets.UTF_8);

        try (ProtosDirectFileModuleResolver resolver =
                resolver(entry, "entry")) {
            assumeTrue(resolver.secureConfinementAvailable());

            ProtosModuleKey aKey =
                    resolver.resolve(
                            "./sub/a.protos",
                            Optional.of(resolver.entryModule()));
            ProtosModuleKey bFromA =
                    resolver.resolve("../b.protos", Optional.of(aKey));
            ProtosModuleKey bFromEntry =
                    resolver.resolve(
                            "./sub/../b.protos",
                            Optional.of(resolver.entryModule()));

            assertEquals(bFromEntry, bFromA);
            assertEquals("b", resolver.loadSource(bFromA).characters());
            assertEquals(
                    b.toAbsolutePath().normalize(),
                    resolver.loadSource(bFromA).physicalPath().orElseThrow());
        }
    }

    @Test
    void directEntryIsCanonicalAndReimportAliasesIt(@TempDir Path root)
            throws Exception {
        Path entry = root.resolve("main.protos");
        Files.writeString(entry, "entry", StandardCharsets.UTF_8);

        try (ProtosDirectFileModuleResolver resolver =
                resolver(entry, "entry")) {
            ProtosModuleKey again =
                    resolver.resolve(
                            "./main.protos",
                            Optional.of(resolver.entryModule()));

            assertEquals(resolver.entryModule(), again);
            ProtosModuleSource source = resolver.loadSource(again);
            assertEquals("entry", source.characters());
            assertEquals(
                    entry.toAbsolutePath().normalize(),
                    source.physicalPath().orElseThrow());
        }
    }

    @Test
    void rejectsRootEscapeAndLocalImportWithoutLocalImporter(@TempDir Path root)
            throws Exception {
        Path entry = root.resolve("main.protos");
        Files.writeString(entry, "entry", StandardCharsets.UTF_8);

        try (ProtosDirectFileModuleResolver resolver =
                resolver(entry, "entry")) {
            assertThrows(
                    IOException.class,
                    () ->
                            resolver.resolve(
                                    "../outside.protos",
                                    Optional.of(resolver.entryModule())));
            assertThrows(
                    IOException.class,
                    () -> resolver.resolve("./helper.protos", Optional.empty()));
            assertThrows(
                    IOException.class,
                    () ->
                            resolver.resolve(
                                    "./helper.protos",
                                    Optional.of(new ProtosModuleKey("std:example"))));
        }
    }

    @Test
    void requiresExactNamesAndDoesNotProbeExtensions(@TempDir Path root)
            throws Exception {
        Path entry = root.resolve("main.protos");
        Path helper = root.resolve("Helper.protos");
        Files.writeString(entry, "entry", StandardCharsets.UTF_8);
        Files.writeString(helper, "helper", StandardCharsets.UTF_8);

        try (ProtosDirectFileModuleResolver resolver =
                resolver(entry, "entry")) {
            assumeTrue(resolver.secureConfinementAvailable());

            ProtosModuleKey wrongCase =
                    resolver.resolve(
                            "./helper.protos",
                            Optional.of(resolver.entryModule()));
            assertThrows(
                    IOException.class,
                    () -> resolver.loadSource(wrongCase));

            ProtosModuleKey noExtension =
                    resolver.resolve(
                            "./Helper",
                            Optional.of(resolver.entryModule()));
            assertThrows(
                    IOException.class,
                    () -> resolver.loadSource(noExtension));

            ProtosModuleKey exact =
                    resolver.resolve(
                            "./Helper.protos",
                            Optional.of(resolver.entryModule()));
            assertEquals("helper", resolver.loadSource(exact).characters());
        }
    }

    @Test
    void rejectsImportedSymlinkButKeepsDistinctHardLinkNamespaceIdentity(
            @TempDir Path root)
            throws Exception {
        Path entry = root.resolve("main.protos");
        Path helper = root.resolve("helper.protos");
        Path symlink = root.resolve("alias.protos");
        Path hardlink = root.resolve("hard.protos");
        Files.writeString(entry, "entry", StandardCharsets.UTF_8);
        Files.writeString(helper, "helper", StandardCharsets.UTF_8);

        try {
            Files.createSymbolicLink(symlink, Path.of("helper.protos"));
            Files.createLink(hardlink, helper);
        } catch (UnsupportedOperationException | IOException unavailable) {
            assumeTrue(false, "host does not support required link fixture");
        }

        try (ProtosDirectFileModuleResolver resolver =
                resolver(entry, "entry")) {
            assumeTrue(resolver.secureConfinementAvailable());

            ProtosModuleKey symlinkKey =
                    resolver.resolve(
                            "./alias.protos",
                            Optional.of(resolver.entryModule()));
            assertThrows(
                    IOException.class,
                    () -> resolver.loadSource(symlinkKey));

            ProtosModuleKey helperKey =
                    resolver.resolve(
                            "./helper.protos",
                            Optional.of(resolver.entryModule()));
            ProtosModuleKey hardlinkKey =
                    resolver.resolve(
                            "./hard.protos",
                            Optional.of(resolver.entryModule()));
            assertNotEquals(helperKey, hardlinkKey);
            assertEquals("helper", resolver.loadSource(helperKey).characters());
            assertEquals("helper", resolver.loadSource(hardlinkKey).characters());
        }
    }

    @Test
    void delegatesNonLocalDomainsWithoutGrantingThemLocalAuthority(
            @TempDir Path root)
            throws Exception {
        Path entry = root.resolve("main.protos");
        Files.writeString(entry, "entry", StandardCharsets.UTF_8);

        ProtosModuleKey standardKey = new ProtosModuleKey("std:example");
        ProtosModuleSource standardSource =
                ProtosModuleSource.fromCharacters(standardKey, "standard");
        ProtosModuleResolver fallback =
                new ProtosModuleResolver() {
                    @Override
                    public ProtosModuleKey resolve(
                            String exactSpecifier,
                            Optional<ProtosModuleKey> importingModule)
                            throws IOException {
                        if (!exactSpecifier.equals("std:example")) {
                            throw new IOException("unsupported");
                        }
                        return standardKey;
                    }

                    @Override
                    public ProtosModuleSource loadSource(ProtosModuleKey key)
                            throws IOException {
                        if (!key.equals(standardKey)) {
                            throw new IOException("unsupported");
                        }
                        return standardSource;
                    }
                };

        try (ProtosDirectFileModuleResolver resolver =
                new ProtosDirectFileModuleResolver(entry, "entry", fallback)) {
            ProtosModuleKey resolved =
                    resolver.resolve(
                            "std:example",
                            Optional.of(resolver.entryModule()));
            assertSame(standardKey, resolved);
            assertSame(standardSource, resolver.loadSource(resolved));

            assertThrows(
                    IOException.class,
                    () ->
                            resolver.resolve(
                                    "./helper.protos",
                                    Optional.of(standardKey)));
        }
    }

    private static ProtosDirectFileModuleResolver resolver(
            Path entry, String characters) {
        return new ProtosDirectFileModuleResolver(
                entry,
                characters,
                ProtosModuleResolver.rejecting());
    }
}
