/* APL-1.0 licensed work; see LICENSE.TXT. */
package com.guillermomolina.protos.cli;

import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import org.junit.jupiter.api.Test;

final class ProtosCliTest {
    private R run(String... args) {
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();
        int code =
                new ProtosCli()
                        .run(
                                args,
                                InputStream.nullInputStream(),
                                new PrintStream(out),
                                new PrintStream(err));
        return new R(
                code,
                out.toString(StandardCharsets.UTF_8),
                err.toString(StandardCharsets.UTF_8));
    }

    @Test
    void helpVersionEvalStringNull() {
        R help = run("--help");
        assertEquals(0, help.c);
        assertTrue(help.o.contains("[args...]"));
        assertTrue(help.o.contains("process.args()"));

        assertTrue(run("--version").o.startsWith("Protos "));
        assertEquals("2\n", run("-e", "1 + 1").o);
        assertEquals("\"hello\"\n", run("-e", "\"hello\"").o);
        assertEquals("null\n", run("-e", "null").o);
    }

    @Test
    void evalAndFileApplicationArgumentsExcludeLauncherIdentity() throws Exception {
        assertEquals(
                "2\n",
                run("-e", "process.args().size()", "one", "two").o);
        assertEquals(
                "\"two\"\n",
                run("-e", "process.args().at(1)", "one", "two").o);

        Path file = Files.createTempFile("protos-cli-", ".protos");
        try {
            Files.writeString(file, "process.args().at(0)");
            R result = run(file.toString(), "application-value");
            assertEquals(0, result.c);
            assertEquals("\"application-value\"\n", result.o);
            assertTrue(result.e.isBlank(), result.e);
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    void standaloneProcessEnvironmentActorAndEncodingAreBootstrapped() {
        assertEquals(
                "true\n",
                run("-e", "process.environment() === process.environment()").o);
        assertEquals(
                "true\n",
                run("-e", "process.stdinEncoding() === Encoding.UTF8").o);
        assertEquals(
                "true\n",
                run("-e", "process.stdoutEncoding() === Encoding.UTF8").o);
        assertEquals(
                "true\n",
                run("-e", "process.stderrEncoding() === Encoding.UTF8").o);
        assertEquals(
                "true\n",
                run("-e", "Actor.current() === Actor.current()").o);
    }

    @Test
    void syntaxUsageAndMissingFile() throws Exception {
        assertNotEquals(0, run("-e", "(").c);
        assertEquals(2, run("-e").c);
        assertEquals(2, run("--unknown").c);

        Path file = Files.createTempFile("protos-cli-", ".protos");
        Files.deleteIfExists(file);
        assertNotEquals(0, run(file.toString()).c);
    }

    private record R(int c, String o, String e) {}
}
