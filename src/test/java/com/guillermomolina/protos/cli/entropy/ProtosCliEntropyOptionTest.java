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
package com.guillermomolina.protos.cli.entropy;

import static org.junit.jupiter.api.Assertions.*;

import com.guillermomolina.protos.cli.ProtosCli;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** I157 / D243-C: explicit CLI Entropy authorization. */
final class ProtosCliEntropyOptionTest {
    private record Result(int code, String stdout, String stderr) {}

    private static Result run(String... arguments) {
        return runWithInput(new byte[0], arguments);
    }

    private static Result runWithInput(
            byte[] input, String... arguments) {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        int code = new ProtosCli().run(
                arguments,
                new ByteArrayInputStream(input),
                new PrintStream(stdout),
                new PrintStream(stderr));

        return new Result(
                code,
                stdout.toString(StandardCharsets.UTF_8),
                stderr.toString(StandardCharsets.UTF_8));
    }

    @Test
    void entropyIsAbsentWithoutExplicitGrant() {
        Result denied = run("-e", "entropy");
        assertEquals(1, denied.code());
        assertTrue(denied.stderr().startsWith("Error:"),
                denied.stderr());

        Result absent = run("-e", "print(context.hasSlot(\"entropy\"))");
        assertEquals(0, absent.code(), absent.stderr());
        assertEquals("false\n", absent.stdout());
    }

    @Test
    void explicitGrantWorksForExpressionEntry() {
        Result allowed = run(
                "--allow-entropy",
                "-e",
                "print(context.hasSlot(\"entropy\"))");

        assertEquals(0, allowed.code(), allowed.stderr());
        assertEquals("true\n", allowed.stdout());

        Result sixteen = run(
                "--allow-entropy",
                "-e",
                "print(entropy.bytes(16).value().size())");

        assertEquals(0, sixteen.code(), sixteen.stderr());
        assertEquals("16\n", sixteen.stdout());

        Result zero = run(
                "--allow-entropy",
                "-e",
                "print(entropy.bytes(0).value().size())");

        assertEquals(0, zero.code(), zero.stderr());
        assertEquals("0\n", zero.stdout());
    }

    @Test
    void otherPermissionsNeverImplyEntropy() {
        Result networkOnly = run(
                "--allow-network",
                "-e",
                "print(context.hasSlot(\"entropy\"))");

        assertEquals(0, networkOnly.code(), networkOnly.stderr());
        assertEquals("false\n", networkOnly.stdout());

        Result both = run(
                "--allow-network",
                "--allow-entropy",
                "-e",
                "print(context.hasSlot(\"entropy\"))\nprint(context.hasSlot(\"network\"))");

        assertEquals(0, both.code(), both.stderr());
        assertEquals("true\ntrue\n", both.stdout());

        Result entropyOnly = run(
                "--allow-entropy",
                "-e",
                "print(context.hasSlot(\"network\"))");

        assertEquals(0, entropyOnly.code(), entropyOnly.stderr());
        assertEquals("false\n", entropyOnly.stdout());
    }

    @Test
    void sourceEntryReceivesOnlyExplicitAuthorization(
            @TempDir Path directory) throws Exception {
        Path source = directory.resolve("entropy.protos");
        Files.writeString(
                source, "print(context.hasSlot(\"entropy\"))\n");

        Result denied = run(source.toString());
        assertEquals(0, denied.code(), denied.stderr());
        assertEquals("false\n", denied.stdout());

        Result granted = run("--allow-entropy", source.toString());
        assertEquals(0, granted.code(), granted.stderr());
        assertEquals("true\n", granted.stdout());

        Files.writeString(
                source, "print(process.args().at(0))\n");

        Result argument = run(
                source.toString(), "--allow-entropy");
        assertEquals(0, argument.code(), argument.stderr());
        assertEquals("--allow-entropy\n", argument.stdout());
    }

    @Test
    void replUsesItsOwnExplicitGrant() {
        byte[] commands =
                "context.hasSlot(\"entropy\")\n:quit\n"
                        .getBytes(StandardCharsets.UTF_8);

        Result denied = runWithInput(commands);
        assertEquals(0, denied.code(), denied.stderr());
        assertTrue(denied.stdout().contains("false"),
                denied.stdout());

        Result granted = runWithInput(
                commands, "--allow-entropy");
        assertEquals(0, granted.code(), granted.stderr());
        assertTrue(granted.stdout().contains("true"),
                granted.stdout());
    }

    @Test
    void debugAcceptsTheGrantBeforeItsSource() {
        Result result = run(
                "debug",
                "--allow-entropy",
                "nonexistent-i157-debug.protos");

        assertEquals(1, result.code());
        assertTrue(
                result.stderr().startsWith(
                        "protos debug: cannot read"),
                result.stderr());
    }

    @Test
    void duplicatesAndInvalidOptionPlacementAreUsageErrors() {
        assertEquals(2, run(
                "--allow-entropy", "--allow-entropy",
                "-e", "null").code());

        assertEquals(2, run(
                "run", "--allow-entropy",
                "--allow-entropy", "entry").code());

        assertEquals(2, run(
                "--allow-entropy", "debug",
                "--allow-entropy", "entry.protos").code());

        assertEquals(2, run(
                "--allow-entropy", "--help").code());

        assertEquals(2, run(
                "--allow-entropy", "--version").code());

        assertEquals(2, run(
                "--allow-entropy", "--service").code());
    }

    @Test
    void bundledToolsAreNeverAuthorized() {
        assertEquals(2, run("--allow-entropy", "package").code());
        assertEquals(2, run("package", "--allow-entropy").code());
        assertEquals(2, run("test", "--allow-entropy").code());
        assertEquals(2, run("format", "--allow-entropy").code());
        assertEquals(2, run("lint", "--allow-entropy").code());
        assertEquals(2, run("language-server", "--allow-entropy").code());
    }

    @Test
    void optionAfterApplicationEntryIsAnArgument() {
        Result result = run(
                "--allow-entropy",
                "-e",
                "print(process.args().at(0))",
                "--allow-entropy");

        assertEquals(0, result.code(), result.stderr());
        assertEquals("--allow-entropy\n", result.stdout());
    }
}
