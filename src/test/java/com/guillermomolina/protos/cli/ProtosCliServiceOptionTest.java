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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

@Timeout(value = 20, unit = TimeUnit.SECONDS)
final class ProtosCliServiceOptionTest {

    private record Result(int code, String stdout, String stderr) {}

    @TempDir Path directory;

    private static Result run(String... args) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        int code = new ProtosCli().run(
                args,
                new ByteArrayInputStream(new byte[0]),
                new PrintStream(out),
                new PrintStream(err));

        return new Result(
                code,
                out.toString(StandardCharsets.UTF_8),
                err.toString(StandardCharsets.UTF_8));
    }

    private Path source(String contents) throws Exception {
        Path file = directory.resolve("service.protos");
        Files.writeString(file, contents, StandardCharsets.UTF_8);
        return file;
    }

    @Test
    void sourceServiceEntersReadyBehaviorAndEndsOnExplicitSelfStop()
            throws Exception {
        Path file = source("""
                service: () => {
                    behavior: {
                        finish: () => {
                            writer: TextWriter(
                                process.stdout(),
                                process.stdoutEncoding()
                            )
                            writer.writeLine(process.args().at(0)).value()
                            Actor.current().stop()
                            null
                        }
                    }
                    Actor.current().send("finish")
                    behavior
                }
                """);

        Result result = run(
                "--service=service", file.toString(), "service-argument");

        assertEquals(0, result.code(), result.stderr());
        assertEquals("service-argument\n", result.stdout());
    }

    @Test
    void serviceSelectionDoesNotGrantNetwork() throws Exception {
        Path file = source("""
                service: () => {
                    network
                    {}
                }
                """);

        Result denied = run("--service=service", file.toString());

        assertEquals(1, denied.code());
        assertTrue(denied.stderr().startsWith("Error:"), denied.stderr());
    }

    @Test
    void missingOrNonCallableSourceBindingFailsBootstrap() throws Exception {
        Path file = source("other: () => { {} }\n");

        Result missing = run("--service=service", file.toString());
        assertEquals(1, missing.code());

        Files.writeString(file, "service: 42\n");

        Result invalid = run("--service=service", file.toString());
        assertEquals(1, invalid.code());
    }

    @Test
    void serviceOptionValidityIsCheckedBeforeExecution() {
        assertEquals(2, run("--service=").code());
        assertEquals(2, run("--service=42", "entry.protos").code());
        assertEquals(2, run("--service=this", "entry.protos").code());
        assertEquals(2, run("--service=a-b", "entry.protos").code());
        assertEquals(2, run("--service", "entry.protos").code());
        assertEquals(2, run("--service=service").code());

        assertEquals(
                2,
                run("--service=service", "--service=other", "entry.protos").code());

        assertEquals(2, run("--service=service", "-e", "null").code());
        assertEquals(2, run("debug", "--service=service", "entry.protos").code());
        assertEquals(2, run("test", "--service=service").code());
        assertEquals(2, run("format", "--service=service").code());
        assertEquals(2, run("lint", "--service=service").code());
        assertEquals(2, run("package", "--service=service").code());
        assertEquals(2, run("language-server", "--service=service").code());

        assertTrue(
                run("--help").stdout().contains("--service=<binding>"));
    }
}
