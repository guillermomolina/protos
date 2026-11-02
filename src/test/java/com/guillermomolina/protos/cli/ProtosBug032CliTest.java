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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class ProtosBug032CliTest {

    private record Result(int code, String output, String error) {}

    private static Result format(byte[] input, String... args) {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        int code =
                new ProtosCli().run(
                        args,
                        new ByteArrayInputStream(input),
                        new PrintStream(stdout),
                        new PrintStream(stderr));

        return new Result(
                code,
                stdout.toString(StandardCharsets.UTF_8),
                stderr.toString(StandardCharsets.UTF_8));
    }

    @Test
    void bug032FormatsCaretCommentsThroughStdinAndFile()
            throws Exception {
        String source =
                "f:()=>{\n"
                        + "^\n"
                        + "// keep return comment\n"
                        + "42\n"
                        + "}";

        String expected =
                "f: () => { ^\n"
                        + "    // keep return comment\n"
                        + "    42 }\n";

        byte[] original = source.getBytes(StandardCharsets.UTF_8);

        Result stdin = format(original, "format");

        assertEquals(0, stdin.code(), stdin.error());
        assertEquals(expected, stdin.output());
        assertEquals("", stdin.error());

        Result repeated =
                format(
                        stdin.output().getBytes(StandardCharsets.UTF_8),
                        "format");

        assertEquals(0, repeated.code(), repeated.error());
        assertEquals(expected, repeated.output());
        assertEquals("", repeated.error());

        Path file = Files.createTempFile("protos-bug032-", ".protos");

        try {
            Files.write(file, original);

            Result fromFile = format(new byte[0], "format", file.toString());

            assertEquals(0, fromFile.code(), fromFile.error());
            assertEquals(expected, fromFile.output());
            assertEquals("", fromFile.error());
            assertArrayEquals(original, Files.readAllBytes(file));
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    void bug032DoesNotTreatContextualReturnNewlineAsOperand() {
        String source = "return // separate expression\n42";
        String expected = source + "\n";

        Result result =
                format(source.getBytes(StandardCharsets.UTF_8), "format");

        assertEquals(0, result.code(), result.error());
        assertEquals(expected, result.output());
        assertEquals("", result.error());
    }
}
