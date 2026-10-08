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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.guillermomolina.protos.analysis.ProtosDocumentSnapshot;
import com.guillermomolina.protos.analysis.ProtosStaticAnalysisCore;
import com.guillermomolina.protos.analysis.ProtosStaticParseResult;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** D195 {@code protos lint} public CLI/CI contract (LM012-C1). */
final class ProtosCliLintTest {
    private static final String FRESH = "protos/always-different-fresh-object";
    private static final String UNREACHABLE = "protos/unreachable-after-nonlocal-return";
    private static final String FRESH_FALSE =
            "'===' against a fresh object literal is always false when it completes normally";
    private static final String UNREACHABLE_MESSAGE =
            "Unreachable code: the preceding '^' never continues this closure body";
    private static final String MULTIPLE =
            "f: () => {\n    ^1\n    b === { y: 2 }\n}\nc(a !== { x: 1 })";

    @TempDir Path dir;

    private record R(int c, String o, String e) {}

    private static R run(InputStream in, String... args) {
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();
        int code = new ProtosCli().run(args, in, new PrintStream(out), new PrintStream(err));
        return new R(
                code, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }

    private static R stdin(String source, String... args) {
        return run(new ByteArrayInputStream(source.getBytes(StandardCharsets.UTF_8)), args);
    }

    private static R noInput(String... args) {
        return run(new ByteArrayInputStream(new byte[0]), args);
    }

    private static JsonObject json(R result) {
        assertTrue(result.o.endsWith("}\n"), result.o);
        return JsonParser.parseString(result.o).getAsJsonObject();
    }

    private static void assertDiagnostic(
            JsonObject diagnostic,
            String origin,
            String code,
            String severity,
            int startLine,
            int startCharacter,
            int endLine,
            int endCharacter) {
        assertEquals(5, diagnostic.size(), diagnostic.toString());
        assertEquals(origin, diagnostic.get("origin").getAsString());
        if (code == null) {
            assertTrue(diagnostic.get("code").isJsonNull(), diagnostic.toString());
        } else {
            assertEquals(code, diagnostic.get("code").getAsString());
        }
        assertEquals(severity, diagnostic.get("severity").getAsString());
        assertTrue(diagnostic.get("message").getAsJsonPrimitive().isString());
        JsonObject range = diagnostic.getAsJsonObject("range");
        assertEquals(2, range.size());
        JsonObject start = range.getAsJsonObject("start");
        JsonObject end = range.getAsJsonObject("end");
        assertEquals(2, start.size());
        assertEquals(2, end.size());
        assertEquals(startLine, start.get("line").getAsInt());
        assertEquals(startCharacter, start.get("character").getAsInt());
        assertEquals(endLine, end.get("line").getAsInt());
        assertEquals(endCharacter, end.get("character").getAsInt());
    }

    @Test
    void cleanSourceHasNoDiagnosticsAndExitsZero() {
        String clean = "value: 42\nf: () => { value }\nvalue === 42";

        assertEquals(new R(0, "", ""), stdin(clean, "lint"));
        assertEquals(new R(0, "", ""), stdin(clean, "lint", "--fail-on-warning"));
        assertEquals(new R(0, "", ""), stdin(clean, "lint", "--output", "text"));

        R json = stdin(clean, "lint", "--output", "json");
        assertEquals(0, json.c);
        assertEquals("", json.e);
        assertEquals(
                "{\"schemaVersion\":1,\"source\":\"<stdin>\",\"status\":\"valid\","
                        + "\"diagnostics\":[]}\n",
                json.o);
    }

    @Test
    void eachApprovedRuleReportsWarningTextWithSourceRangeOriginCodeAndMessage() {
        R fresh = stdin("a === { x: 1 }", "lint");
        assertEquals(0, fresh.c);
        assertEquals("", fresh.e);
        assertEquals(
                "<stdin>:0:0-0:14: warning [lint " + FRESH + "]: " + FRESH_FALSE + "\n",
                fresh.o);

        R unreachable = stdin("f: () => { ^1; a }", "lint", "--output", "text");
        assertEquals(0, unreachable.c);
        assertEquals(
                "<stdin>:0:15-0:16: warning [lint " + UNREACHABLE + "]: "
                        + UNREACHABLE_MESSAGE + "\n",
                unreachable.o);
    }

    @Test
    void multipleWarningsAreOrderedByStartEndThenCodeInTextAndJson() {
        R text = stdin(MULTIPLE, "lint");
        assertEquals(0, text.c);
        String[] lines = text.o.split("\n");
        assertEquals(3, lines.length, text.o);
        assertTrue(lines[0].startsWith("<stdin>:2:4-2:18: warning [lint " + FRESH + "]: "));
        assertTrue(lines[1].startsWith("<stdin>:2:4-2:18: warning [lint " + UNREACHABLE + "]: "));
        assertTrue(lines[2].startsWith("<stdin>:4:2-4:16: warning [lint " + FRESH + "]: "));
        assertEquals(text, stdin(MULTIPLE, "lint"));

        JsonArray diagnostics = json(stdin(MULTIPLE, "lint", "--output", "json"))
                .getAsJsonArray("diagnostics");
        assertEquals(3, diagnostics.size());
        assertDiagnostic(
                diagnostics.get(0).getAsJsonObject(), "lint", FRESH, "warning", 2, 4, 2, 18);
        assertDiagnostic(
                diagnostics.get(1).getAsJsonObject(), "lint", UNREACHABLE, "warning", 2, 4, 2, 18);
        assertDiagnostic(
                diagnostics.get(2).getAsJsonObject(), "lint", FRESH, "warning", 4, 2, 4, 16);
    }

    @Test
    void jsonWarningReportHasTheCompleteV1Schema() {
        R result = stdin("a === { x: 1 }", "lint", "--output", "json");
        assertEquals(0, result.c);
        assertEquals("", result.e);
        assertEquals(
                "{\"schemaVersion\":1,\"source\":\"<stdin>\",\"status\":\"valid\","
                        + "\"diagnostics\":[{\"origin\":\"lint\",\"code\":\"" + FRESH + "\","
                        + "\"severity\":\"warning\",\"message\":\"" + FRESH_FALSE + "\","
                        + "\"range\":{\"start\":{\"line\":0,\"character\":0},"
                        + "\"end\":{\"line\":0,\"character\":14}}}]}\n",
                result.o);
    }

    @Test
    void parserErrorReportsOnlyTheCanonicalParserDiagnostic() {
        // The first line alone would be a lint warning; an invalid document reports none.
        String invalid = "a === { x: 1 }\nfoo(";
        ProtosStaticParseResult.Failed expected =
                (ProtosStaticParseResult.Failed) new ProtosStaticAnalysisCore()
                        .parse(new ProtosDocumentSnapshot("<stdin>", 0L, invalid));

        R text = stdin(invalid, "lint");
        assertEquals(1, text.c);
        assertEquals("", text.e);
        assertTrue(text.o.startsWith("<stdin>:"), text.o);
        assertTrue(text.o.contains(": error [parser]: " + expected.message()), text.o);
        assertFalse(text.o.contains("[lint"), text.o);

        R json = stdin(invalid, "lint", "--output", "json");
        assertEquals(1, json.c);
        assertEquals("", json.e);
        JsonObject report = json(json);
        assertEquals(1, report.get("schemaVersion").getAsInt());
        assertEquals("<stdin>", report.get("source").getAsString());
        assertEquals("invalid", report.get("status").getAsString());
        JsonArray diagnostics = report.getAsJsonArray("diagnostics");
        assertEquals(1, diagnostics.size());
        JsonObject diagnostic = diagnostics.get(0).getAsJsonObject();
        assertEquals(expected.message(), diagnostic.get("message").getAsString());
        int start = expected.span().startOffset();
        int end = expected.span().endOffset();
        int lineStart = invalid.indexOf('\n') + 1;
        assertTrue(start >= lineStart, "parser error is on the second line");
        assertDiagnostic(
                diagnostic, "parser", null, "error", 1, start - lineStart, 1, end - lineStart);

        assertEquals(1, stdin(invalid, "lint", "--fail-on-warning").c);
    }

    @Test
    void failOnWarningGatesExitWithoutChangingWarningSeverity() {
        R allowed = stdin("a === { x: 1 }", "lint", "--output", "json");
        R gated = stdin("a === { x: 1 }", "lint", "--fail-on-warning", "--output", "json");
        assertEquals(0, allowed.c);
        assertEquals(1, gated.c);
        assertEquals(allowed.o, gated.o);
        assertEquals("", gated.e);
        assertEquals(
                "warning",
                json(gated).getAsJsonArray("diagnostics").get(0).getAsJsonObject()
                        .get("severity").getAsString());

        R gatedText = stdin("a === { x: 1 }", "lint", "--fail-on-warning");
        assertEquals(1, gatedText.c);
        assertEquals(stdin("a === { x: 1 }", "lint").o, gatedText.o);
        assertTrue(gatedText.o.contains(": warning [lint "), gatedText.o);
    }

    @Test
    void positionsUseZeroBasedUtf16ColumnsLogicalCrLfLinesAndExclusiveEnds() {
        // U+1F600 is two UTF-16 code units, so the exclusive end is 17, not 16.
        String source = "\"😀\"\r\n\"😀\" === { x: 1 }\r\n";
        R result = stdin(source, "lint", "--output", "json");
        assertEquals(0, result.c, result.e);
        assertDiagnostic(
                json(result).getAsJsonArray("diagnostics").get(0).getAsJsonObject(),
                "lint", FRESH, "warning", 1, 0, 1, 17);
        assertTrue(stdin(source, "lint").o.startsWith("<stdin>:1:0-1:17: warning"));
    }

    @Test
    void explicitFileUsesTheOperandAsSourceAndIsNeverWritten() throws Exception {
        Path file = dir.resolve("example.protos");
        byte[] bytes = "a === { x: 1 }".getBytes(StandardCharsets.UTF_8);
        Files.write(file, bytes);
        FileTime modified = Files.getLastModifiedTime(file);

        R text = noInput("lint", file.toString());
        assertEquals(0, text.c, text.e);
        assertTrue(text.o.startsWith(file + ":0:0-0:14: warning [lint " + FRESH + "]"), text.o);

        R json = noInput("lint", "--output", "json", file.toString());
        assertEquals(file.toString(), json(json).get("source").getAsString());

        assertArrayEquals(bytes, Files.readAllBytes(file));
        assertEquals(modified, Files.getLastModifiedTime(file));
        try (var listing = Files.list(dir)) {
            assertEquals(1, listing.count());
        }
    }

    @Test
    void symbolicLinkToRegularFileIsAcceptedReadOnly() throws Exception {
        Path target = dir.resolve("target.protos");
        Path link = dir.resolve("link.protos");
        Files.writeString(target, "a === { x: 1 }", StandardCharsets.UTF_8);
        try {
            Files.createSymbolicLink(link, target);
        } catch (UnsupportedOperationException | IOException | SecurityException e) {
            assumeTrue(false, "symlinks unavailable");
        }

        R result = noInput("lint", link.toString());
        assertEquals(0, result.c, result.e);
        assertTrue(result.o.startsWith(link + ":0:0-0:14: warning"), result.o);
        assertTrue(Files.isSymbolicLink(link));
        assertEquals("a === { x: 1 }", Files.readString(target, StandardCharsets.UTF_8));
    }

    @Test
    void sourceAcquisitionFailuresExitThreeOnStderrWithEmptyStdout() throws Exception {
        Path missing = dir.resolve("missing.protos");
        Path malformed = dir.resolve("malformed.protos");
        byte[] malformedBytes = {'v', ':', ' ', (byte) 0xC3, (byte) 0x28, '\n'};
        Files.write(malformed, malformedBytes);

        for (String output : new String[] {"text", "json"}) {
            for (R failure :
                    new R[] {
                        noInput("lint", "--output", output, missing.toString()),
                        noInput("lint", "--output", output, dir.toString()),
                        noInput("lint", "--output", output, malformed.toString()),
                        run(new ByteArrayInputStream(malformedBytes), "lint", "--output", output)
                    }) {
                assertEquals(3, failure.c, failure.e);
                assertEquals("", failure.o);
                assertTrue(failure.e.startsWith("protos lint: cannot read "), failure.e);
                assertFalse(failure.e.contains("Internal error"), failure.e);
            }
        }
        assertTrue(noInput("lint", malformed.toString()).e.contains("malformed UTF-8"));
        assertTrue(
                run(new ByteArrayInputStream(malformedBytes), "lint").e
                        .startsWith("protos lint: cannot read <stdin>: malformed UTF-8"));
        assertArrayEquals(malformedBytes, Files.readAllBytes(malformed));

        R stdinFailure = run(
                new InputStream() {
                    @Override
                    public int read() throws IOException {
                        throw new IOException("stdin closed");
                    }
                },
                "lint");
        assertEquals(3, stdinFailure.c);
        assertEquals("", stdinFailure.o);
    }

    @Test
    void unreadableFileExitsThree() throws Exception {
        Path file = dir.resolve("unreadable.protos");
        Files.writeString(file, "value: 1", StandardCharsets.UTF_8);
        try {
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("---------"));
        } catch (UnsupportedOperationException e) {
            assumeTrue(false, "POSIX permissions unavailable");
        }
        assumeFalse(Files.isReadable(file), "privileged user can still read the file");

        R result = noInput("lint", file.toString());
        assertEquals(3, result.c);
        assertEquals("", result.o);
        assertTrue(result.e.startsWith("protos lint: cannot read " + file), result.e);
    }

    @Test
    void usageErrorsExitTwoOnStderrWithEmptyStdout() {
        String[][] invocations = {
            {"lint", "--output"},
            {"lint", "--output", "xml"},
            {"lint", "--output", "json", "--output", "text"},
            {"lint", "--output=json"},
            {"lint", "--fail-on-warning", "--fail-on-warning"},
            {"lint", "--verbose"},
            {"lint", "-"},
            {"lint", "a.protos", "b.protos"},
        };
        for (String[] invocation : invocations) {
            R result = stdin("a === { x: 1 }", invocation);
            assertEquals(2, result.c, String.join(" ", invocation) + ": " + result.e);
            assertEquals("", result.o);
            assertTrue(result.e.startsWith("protos: lint"), result.e);
            assertTrue(result.e.contains("Try 'protos --help'."), result.e);
        }

        R prefixed = noInput("--foreign-provider-path", ".", "lint");
        assertEquals(2, prefixed.c);
        assertEquals("", prefixed.o);
    }

    @Test
    void unexpectedFailureIsAnInternalErrorWithEmptyStdout() {
        R result = run(
                new InputStream() {
                    @Override
                    public int read() {
                        throw new IllegalStateException("boom");
                    }
                },
                "lint",
                "--output",
                "json");
        assertEquals(70, result.c);
        assertEquals("", result.o);
        assertTrue(result.e.startsWith("Internal error: "), result.e);
    }

    @Test
    void lintNeverExecutesGuestCodeAndRunsWithoutTheGuestCarrier() {
        AtomicReference<Thread> reader = new AtomicReference<>();
        byte[] source =
                "io.println(\"guest output\")\nmissing.explode()\n"
                        .getBytes(StandardCharsets.UTF_8);
        InputStream in = new ByteArrayInputStream(source) {
            @Override
            public synchronized int read(byte[] buffer, int offset, int length) {
                reader.compareAndSet(null, Thread.currentThread());
                return super.read(buffer, offset, length);
            }

            @Override
            public synchronized byte[] readAllBytes() {
                reader.compareAndSet(null, Thread.currentThread());
                return super.readAllBytes();
            }
        };

        R result = run(in, "lint");

        assertSame(Thread.currentThread(), reader.get());
        assertEquals(0, result.c, result.e);
        assertEquals("", result.o);
        assertEquals("", result.e);
    }

    @Test
    void lintAdapterReusesTheStaticCoreOnceAndHasNoGuestOrLspDependencies() throws Exception {
        Path cli = Path.of("src", "main", "java", "com", "guillermomolina", "protos", "cli");
        String lint = Files.readString(cli.resolve("ProtosLintCommand.java"));

        assertTrue(lint.contains("new ProtosStaticAnalysisCore().parse(snapshot)"));
        assertTrue(lint.contains("ProtosStaticLint.check(success)"));
        assertFalse(lint.contains(".lint("), "lint(snapshot) re-parses");
        for (String forbidden : new String[] {
            "ProtosParser", "ProtosLexer", "ProtosSourceCompiler", "ProtosPolyglotRuntimeHost",
            "ProtosStandaloneHostedExecution", "ProtosToolchainRoots", "org.graalvm",
            "com.oracle.truffle", "com.guillermomolina.protos.execution",
            "com.guillermomolina.protos.runtime", "com.guillermomolina.protos.lsp",
            "org.eclipse.lsp4j", "ProtosStaticAnalysisSession", "ProtosProjectBinding",
            "new Thread", "Files.write", "Files.newOutputStream"
        }) {
            assertFalse(lint.contains(forbidden), forbidden);
        }

        String driver = Files.readString(cli.resolve("ProtosCli.java"));
        int lintBranch = driver.indexOf("args[0].equals(\"lint\")");
        int carrier = driver.indexOf("\"protos-cli-guest\"");
        assertTrue(lintBranch > 0 && carrier > lintBranch, "lint dispatches before the carrier");
    }

    @Test
    void jsonStringsEscapeQuotesBackslashesControlsAndKeepUnicode() throws Exception {
        String value = "q\"b\\n\nr\rt\t\b\f\u0001\u001f é 😀  ";
        StringBuilder encoded = new StringBuilder();
        ProtosLintCommand.appendString(encoded, value);
        assertEquals(
                "\"q\\\"b\\\\n\\nr\\rt\\t\\b\\f\\u0001\\u001f é 😀  \"",
                encoded.toString());
        assertEquals(value, JsonParser.parseString(encoded.toString()).getAsString());

        StringBuilder lone = new StringBuilder();
        ProtosLintCommand.appendString(lone, "a\ud800b\udc00");
        assertEquals("\"a\\ud800b\\udc00\"", lone.toString());

        Path file = dir.resolve("ñ \"quoted\" \\ 😀.protos");
        Files.writeString(file, "a === { x: 1 }", StandardCharsets.UTF_8);
        R result = noInput("lint", "--output", "json", file.toString());
        assertEquals(0, result.c, result.e);
        assertEquals(file.toString(), json(result).get("source").getAsString());
    }

    @Test
    void helpListsTheLintCommand() {
        R help = noInput("--help");
        assertEquals(0, help.c);
        assertTrue(help.o.contains(
                "protos lint [--output text|json] [--fail-on-warning] [<file>]"));
    }
}
