/*
 * THE LICENSED WORK IS PROVIDED UNDER THE TERMS OF THE ADAPTIVE PUBLIC LICENSE
 * ("LICENSE") AS FIRST COMPLETED BY: Guillermo Adrián Molina.
 * See LICENSE.TXT and https://github.com/guillermomolina/protos
 */
package com.guillermomolina.protos.execution;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class ProtosTomlClosureConformanceTest {
    private static final Path PUBLIC_TOML =
            Path.of("protos", "lib", "toml", "TOML.protos");

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
}
