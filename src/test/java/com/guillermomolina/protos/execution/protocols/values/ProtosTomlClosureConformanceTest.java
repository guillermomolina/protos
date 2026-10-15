/*
 * THE LICENSED WORK IS PROVIDED UNDER THE TERMS OF THE ADAPTIVE PUBLIC LICENSE
 * ("LICENSE") AS FIRST COMPLETED BY: Guillermo Adrián Molina.
 * See LICENSE.TXT and https://github.com/guillermomolina/protos
 */
package com.guillermomolina.protos.execution;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

final class ProtosTomlClosureConformanceTest {
    private static final Path PUBLIC_TOML =
            Path.of("protos", "lib", "toml", "TOML.protos");
    private static final Path TOOLS = Path.of("protos", "tools");
    private static final List<Path> TOML_10_SCHEMA_CONSUMERS =
            List.of(
                    TOOLS.resolve("package").resolve("ManifestSchemaV1.protos"),
                    TOOLS.resolve("test").resolve("ResourceRequirements.protos"),
                    TOOLS.resolve("test").resolve("ResourceCatalog.protos"));

    @Test
    void publicTomlSourceKeepsTheD087AndHostRuntimeBoundaries() throws Exception {
        String source = Files.readString(PUBLIC_TOML, StandardCharsets.UTF_8);

        assertFalse(source.contains("tool-shared:Toml10"), source);
        assertFalse(source.contains("protos/tools/shared/Toml10"), source);
        assertFalse(source.contains("Double.toString"), source);
        assertFalse(source.contains("doubleToRawLongBits"), source);
        assertFalse(source.contains("java.time"), source);
        assertFalse(source.contains("java.nio.file"), source);
        assertFalse(source.contains("loadFile"), source);
        assertFalse(source.contains("saveFile"), source);
        assertFalse(source.contains("appendQuotes(quote, remaining - 1)"), source);
        assertFalse(source.contains("raw = \"0\" + raw"), source);
    }

    // I079: bundled tools parse their persisted TOML 1.0 schemas through the one
    // Standard Library parser; the private bundled-tool TOML engine stays retired.
    @Test
    void bundledToolsUseTheStandardLibraryParserWithToml10Selected() throws Exception {
        assertFalse(Files.exists(TOOLS.resolve("shared").resolve("Toml10")));
        try (Stream<Path> sources = Files.walk(TOOLS)) {
            for (Path source : sources.filter(Files::isRegularFile).toList()) {
                String text = Files.readString(source, StandardCharsets.UTF_8);
                assertFalse(text.contains("Toml10"), source::toString);
                assertFalse(text.contains("self:TomlDocument"), source::toString);
                assertFalse(text.contains("self:TomlSyntax"), source::toString);
            }
        }
        for (Path consumer : TOML_10_SCHEMA_CONSUMERS) {
            String text = Files.readString(consumer, StandardCharsets.UTF_8);
            assertTrue(text.contains("import(\"std:toml/TOML\")"), consumer::toString);
            assertTrue(text.contains("TOML.parseDialect(text, \"1.0\")"), consumer::toString);
        }
    }
}
