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
    void helpVersionAndNonInteractiveResultPolicy() {
        R help = run("--help");
        assertEquals(0, help.c);
        assertTrue(help.o.contains("[args...]"));
        assertTrue(help.o.contains("process.args()"));
        assertTrue(help.o.contains("protos package"));
        assertTrue(help.o.contains("explicit program output"));

        assertTrue(run("--version").o.startsWith("Protos "));
        assertEquals("", run("-e", "1 + 1").o);
        assertEquals("", run("-e", "\"hello\"").o);
        assertEquals("", run("-e", "null").o);
    }

    @Test
    void cliPrintRunsProtosSourceThroughProcessStdoutWithoutDoubleEcho() {
        R result = run("protos/tests/cli/print-output.protos");

        assertEquals(0, result.c);
        assertEquals("hello\n42\ntrue\nfalse\nnull\n", result.o);
        assertTrue(result.e.isBlank(), result.e);
    }

    @Test
    void printArityFailuresAreOrdinaryProtosErrors() {
        R zero = run("-e", "print()");
        assertEquals(1, zero.c);
        assertTrue(zero.o.isBlank(), zero.o);
        assertTrue(zero.e.startsWith("Error:"), zero.e);
        assertFalse(zero.e.contains("IllegalArgumentException"), zero.e);

        R two = run("-e", "print(1, 2)");
        assertEquals(1, two.c);
        assertTrue(two.o.isBlank(), two.o);
        assertTrue(two.e.startsWith("Error:"), two.e);
        assertFalse(two.e.contains("IllegalArgumentException"), two.e);
    }

    @Test
    void evalAndFileApplicationArgumentsExcludeLauncherIdentity() throws Exception {
        assertEquals(
                "2\n",
                run("-e", "print(process.args().size())", "one", "two").o);
        assertEquals(
                "two\n",
                run("-e", "print(process.args().at(1))", "one", "two").o);

        Path file = Files.createTempFile("protos-cli-", ".protos");
        try {
            Files.writeString(file, "print(process.args().at(0))");
            R result = run(file.toString(), "application-value");
            assertEquals(0, result.c);
            assertEquals("application-value\n", result.o);
            assertTrue(result.e.isBlank(), result.e);
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    void standaloneProcessEnvironmentActorAndEncodingAreBootstrapped() {
        assertEquals(
                "true\n",
                run("-e", "print(process.environment() === process.environment())").o);
        assertEquals(
                "true\n",
                run("-e", "print(process.stdinEncoding() === Encoding.UTF8)").o);
        assertEquals(
                "true\n",
                run("-e", "print(process.stdoutEncoding() === Encoding.UTF8)").o);
        assertEquals(
                "true\n",
                run("-e", "print(process.stderrEncoding() === Encoding.UTF8)").o);
        assertEquals(
                "true\n",
                run("-e", "print(Actor.current() === Actor.current())").o);
    }

    @Test
    void publishedHelloWorldAndValuesTutorialRunEndToEnd() {
        R hello = run("protos/examples/hello-world.protos");
        assertEquals(0, hello.c);
        assertEquals("Hello, Protos!\n", hello.o);
        assertTrue(hello.e.isBlank(), hello.e);

        R values = run("protos/tutorials/01-values-and-slots/01-values.protos");
        assertEquals(0, values.c);
        assertEquals("42\nhello\ntrue\nnull\n", values.o);
        assertTrue(values.e.isBlank(), values.e);
    }

    @Test
    void packageSubcommandRunsBundledProtosToolWithProcessArgsStdoutAndRestrictedFilesystem() {
        R result = run("package");

        assertEquals(0, result.c);
        assertEquals("Protos package tool bootstrap\npackage\n", result.o);
        assertTrue(result.e.isBlank(), result.e);
    }

    @Test
    void ordinaryCliApplicationDoesNotReceivePackageToolFilesystemAuthority() {
        assertNotEquals(0, run("-e", "filesystem").c);
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
