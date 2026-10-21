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
package com.guillermomolina.protos.cli;

import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import org.junit.jupiter.api.Test;

/** CLI010: explicit per-invocation {@code --inspect} authorization of {@code std:inspect}. */
final class ProtosCli010InspectTest {
    private static final String INSPECT_SOURCE =
            """
            Inspect: import("std:inspect")
            identity: (value) => value

            print(Inspect.representation(identity))
            print(Inspect.callableInfo(identity))
            print(Inspect.bytecode(identity))
            print(Inspect.truffleTree(identity))
            """;

    /** Prints whether the import was denied, then the application arguments. */
    private static final String ARGS_SOURCE =
            """
            denied: (() => {
                result: false
                Error.handle(
                    () => {
                        import("std:inspect")
                    },
                    (error) => {
                        result = error.parent() === Error
                    }
                )
                result
            })()
            print(denied)
            print(process.args().size())
            process.args().each((argument) => print(argument))
            """;

    private R run(String... args) {
        return run(new ProtosCli(), new byte[0], args);
    }

    private static R run(ProtosCli cli, byte[] input, String... args) {
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();
        int code =
                cli.run(
                        args,
                        new ByteArrayInputStream(input),
                        new PrintStream(out),
                        new PrintStream(err));
        return new R(
                code,
                out.toString(StandardCharsets.UTF_8),
                err.toString(StandardCharsets.UTF_8));
    }

    private static Path writeTemp(String source) throws IOException {
        Path file = Files.createTempFile("protos-cli010-", ".protos");
        Files.writeString(file, source);
        return file;
    }

    private static void assertInspectionOutput(R result) {
        assertEquals(0, result.c, result.e);
        assertTrue(result.e.isBlank(), result.e);
        assertTrue(result.o.contains("Closure (source-backed)"), result.o);
        assertTrue(result.o.contains("source-backed Closure"), result.o);
        assertTrue(result.o.contains("Bytecode DSL"), result.o);
        assertTrue(result.o.contains("physical Truffle node tree"), result.o);
        assertTrue(result.o.lines().filter(line -> !line.isBlank()).count() >= 4, result.o);
    }

    private static void assertOrdinaryDenial(R result) {
        assertEquals(1, result.c, result.o);
        assertTrue(result.o.isBlank(), result.o);
        assertTrue(result.e.contains("Error"), result.e);
        assertFalse(result.e.contains("Internal error"), result.e);
        assertFalse(result.e.contains("Bytecode DSL"), result.e);
        assertFalse(result.e.contains("Closure (source-backed)"), result.e);
    }

    private static void assertUsage(R result) {
        assertEquals(2, result.c, result.e);
        assertEquals("", result.o);
        assertTrue(result.e.startsWith("protos: "), result.e);
        assertTrue(result.e.contains("Try 'protos --help'."), result.e);
    }

    @Test
    void fileInvocationWithInspectAuthorizesAllFourOperations() throws Exception {
        Path file = writeTemp(INSPECT_SOURCE);
        try {
            assertInspectionOutput(run("--inspect", file.toString()));
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    void evalInvocationWithInspectAuthorizesAllFourOperations() {
        assertInspectionOutput(run("--inspect", "-e", INSPECT_SOURCE));
    }

    @Test
    void fileAndEvalWithoutInspectAreDeniedWithOrdinaryErrors() throws Exception {
        Path file = writeTemp(INSPECT_SOURCE);
        try {
            assertOrdinaryDenial(run(file.toString()));
        } finally {
            Files.deleteIfExists(file);
        }
        assertOrdinaryDenial(run("-e", INSPECT_SOURCE));
    }

    @Test
    void replWithInspectAdmitsImportAndInspectsInteger() {
        byte[] input =
                "Inspect: import(\"std:inspect\")\nInspect.representation(1)\n:quit\n"
                        .getBytes(StandardCharsets.UTF_8);
        R result = run(new ProtosCli(), input, "--inspect");
        assertEquals(0, result.c, result.e);
        assertTrue(result.e.isBlank(), result.e);
        assertTrue(result.o.contains("Integer"), result.o);
    }

    @Test
    void replWithoutInspectDeniesImportAndContinues() {
        byte[] input =
                "Inspect: import(\"std:inspect\")\n1 + 1\n:quit\n"
                        .getBytes(StandardCharsets.UTF_8);
        R result = run(new ProtosCli(), input);
        assertEquals(0, result.c, result.e);
        assertTrue(result.e.contains("Error"), result.e);
        assertFalse(result.e.contains("Internal error"), result.e);
        assertTrue(result.o.contains("2"), result.o);
        assertFalse(result.o.contains("Integer"), result.o);
    }

    @Test
    void launcherOptionPolicyRejectsRepetitionAndIncompatibleCommands() {
        assertUsage(run("--inspect", "--inspect", "-e", "1"));
        assertUsage(run("--inspect", "-e"));
        assertUsage(run("--inspect", "--unknown"));
        for (String[] rejected :
                new String[][] {
                    {"--inspect", "run", "main"},
                    {"--inspect", "debug", "missing.protos"},
                    {"--inspect", "package"},
                    {"--inspect", "test", "missing"},
                    {"--inspect", "format", "missing.protos"},
                    {"--inspect", "lint", "missing.protos"},
                    {"--inspect", "language-server"},
                    {"--inspect", "--help"},
                    {"--inspect", "--version"},
                    {"--inspect", "-h"},
                    {"--inspect", "-v"}
                }) {
            R result = run(rejected);
            assertUsage(result);
            assertTrue(
                    result.e.contains("--inspect applies only to file, -e, or REPL execution"),
                    result.e);
        }
    }

    @Test
    void inspectRepetitionAndIncompatibilityAcrossForeignProviderPath() {
        R repeated = run("--inspect", "--foreign-provider-path", ".", "--inspect", "-e", "1");
        assertUsage(repeated);
        assertTrue(repeated.e.contains("--inspect may be given only once"), repeated.e);
        R incompatible = run("--foreign-provider-path", ".", "--inspect", "run", "main");
        assertUsage(incompatible);
        assertTrue(
                incompatible.e.contains("applies only to file, -e, or REPL execution"),
                incompatible.e);
    }

    @Test
    void applicationArgumentsAfterTheInputAreKeptAndNeverAuthorize() throws Exception {
        R authorized = run("--inspect", "-e", ARGS_SOURCE, "argument1", "--inspect");
        assertEquals(0, authorized.c, authorized.e);
        assertEquals("false\n2\nargument1\n--inspect\n", authorized.o);

        R trailing = run("-e", ARGS_SOURCE, "--inspect");
        assertEquals(0, trailing.c, trailing.e);
        assertEquals("true\n1\n--inspect\n", trailing.o);

        Path file = writeTemp(ARGS_SOURCE);
        try {
            R fileAuthorized = run("--inspect", file.toString(), "argument1", "--inspect");
            assertEquals(0, fileAuthorized.c, fileAuthorized.e);
            assertEquals("false\n2\nargument1\n--inspect\n", fileAuthorized.o);

            R fileTrailing = run(file.toString(), "--inspect");
            assertEquals(0, fileTrailing.c, fileTrailing.e);
            assertEquals("true\n1\n--inspect\n", fileTrailing.o);
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    void reusedCliInstanceDoesNotRetainAuthorization() {
        ProtosCli cli = new ProtosCli();
        R first = run(cli, new byte[0], "--inspect", "-e", ARGS_SOURCE);
        assertEquals(0, first.c, first.e);
        assertEquals("false\n0\n", first.o);
        R second = run(cli, new byte[0], "-e", ARGS_SOURCE);
        assertEquals(0, second.c, second.e);
        assertEquals("true\n0\n", second.o);
    }

    @Test
    void helpDocumentsInspectOptionScopeAndModule() {
        R help = run("--help");
        assertEquals(0, help.c);
        assertTrue(help.o.contains("protos [--inspect] <file> [args...]"), help.o);
        assertTrue(help.o.contains("protos [--inspect] -e <source> [args...]"), help.o);
        assertTrue(help.o.contains("protos [--inspect]\n"), help.o);
        assertTrue(help.o.contains("--inspect (before <file>, -e, or REPL)"), help.o);
        assertTrue(help.o.contains("std:inspect"), help.o);
        assertTrue(help.o.contains("--foreign-provider-path"), help.o);
    }

    private record R(int c, String o, String e) {}
}
