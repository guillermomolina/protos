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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** CLI008-C1 D063 S3 presentation of the terminal occurrence's guest stack on CLI surfaces. */
final class ProtosCli008C1GuestStackCliTest {
    private static final String NESTED =
            """
            print("before")
            a: () => {
                b()
            }
            b: () => {
                Error().signal()
            }
            a()
            """;

    @Test
    void evalPresentsTheGuestStackAfterTheErrorSummary() {
        Result result = run(InputStream.nullInputStream(), "-e", NESTED);

        assertEquals(1, result.code());
        assertEquals("before\n", result.out(), "print output must be unchanged");
        List<String> lines = result.err().lines().toList();
        assertTrue(lines.get(0).startsWith("Error: "), result.err());
        assertFrameLines(lines.subList(1, lines.size()), "<eval>", List.of(6, 3, 8));
        assertNoImplementationDetail(result.err());
        System.out.println("CLI008_C1_EVAL_D063_S3_PRESENTATION=PASS");
        System.out.println("CLI008_C1_PRINT_OUTPUT_CHANGED=NO");
    }

    @Test
    void fileRunPresentsTheGuestStack(@TempDir Path directory) throws Exception {
        Path program = directory.resolve("cli008c1_nested.protos");
        Files.writeString(program, NESTED, StandardCharsets.UTF_8);

        Result result = run(InputStream.nullInputStream(), program.toString());

        assertEquals(1, result.code());
        assertEquals("before\n", result.out());
        List<String> lines = result.err().lines().toList();
        assertTrue(lines.get(0).startsWith("Error: "), result.err());
        List<String> frames = lines.subList(1, lines.size());
        assertEquals(3, frames.size(), result.err());
        for (String frame : frames) {
            assertTrue(frame.startsWith("  at "), frame);
            assertTrue(frame.contains("cli008c1_nested.protos:"), frame);
        }
        assertFrameLinesBySuffix(frames, List.of(6, 3, 8));
        assertNoImplementationDetail(result.err());
        System.out.println("CLI008_C1_FILE_D063_S3_PRESENTATION=PASS");
    }

    @Test
    void replPresentsTheGuestStackOfAnUncaughtUnit() {
        Result result =
                run(
                        new ByteArrayInputStream(
                                "boom: () => { Error().signal() }\nboom()\n:quit\n"
                                        .getBytes(StandardCharsets.UTF_8)));

        assertEquals(0, result.code());
        List<String> lines = result.err().lines().toList();
        assertTrue(lines.get(0).startsWith("Error: "), result.err());
        assertFrameLines(lines.subList(1, lines.size()), "<repl>", List.of(1, 1));
        assertNoImplementationDetail(result.err());
        System.out.println("CLI008_C1_REPL_D063_S3_PRESENTATION=PASS");
    }

    @Test
    void handledErrorPrintsNoGuestStack() {
        Result result =
                run(
                        InputStream.nullInputStream(),
                        "-e",
                        "Error.handle(() => { Error().signal() }, (caught) => { 0 })");

        assertEquals(0, result.code());
        assertTrue(result.err().isBlank(), result.err());
    }

    private static void assertFrameLines(
            List<String> frames, String source, List<Integer> expectedLines) {
        assertEquals(expectedLines.size(), frames.size(), () -> String.join("\n", frames));
        for (int index = 0; index < frames.size(); index++) {
            Pattern expected =
                    Pattern.compile(
                            "  at " + Pattern.quote(source) + ":" + expectedLines.get(index)
                                    + ":[1-9][0-9]*");
            assertTrue(expected.matcher(frames.get(index)).matches(), frames.get(index));
        }
    }

    private static void assertFrameLinesBySuffix(List<String> frames, List<Integer> expectedLines) {
        for (int index = 0; index < frames.size(); index++) {
            Pattern expected =
                    Pattern.compile(".*:" + expectedLines.get(index) + ":[1-9][0-9]*");
            assertTrue(expected.matcher(frames.get(index)).matches(), frames.get(index));
        }
    }

    private static void assertNoImplementationDetail(String err) {
        for (String forbidden :
                List.of("java", "Java", "Truffle", "Bytecode", "Continuation", "Task", "<continuation>")) {
            assertFalse(err.contains(forbidden), () -> forbidden + " leaked into: " + err);
        }
    }

    private static Result run(InputStream in, String... args) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int code = new ProtosCli().run(args, in, new PrintStream(out), new PrintStream(err));
        return new Result(
                code,
                out.toString(StandardCharsets.UTF_8),
                err.toString(StandardCharsets.UTF_8));
    }

    private record Result(int code, String out, String err) {}
}
