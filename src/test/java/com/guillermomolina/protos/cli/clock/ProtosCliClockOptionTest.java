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
package com.guillermomolina.protos.cli.clock;

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

/** I163 / D253-C1: explicit CLI Clock authorization. */
final class ProtosCliClockOptionTest {
    private record Result(int code, String stdout, String stderr) {}

    private static Result run(String... arguments) {
        return runWithInput(new byte[0], arguments);
    }

    private static Result runWithInput(
            byte[] input, String... arguments) {
        return runWithCli(new ProtosCli(), input, arguments);
    }

    private static Result runWithCli(ProtosCli cli, byte[] input, String... arguments) {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        int code = cli.run(
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
    void authorizationResetsWhenTheSameCliInstanceIsReused() {
        ProtosCli cli = new ProtosCli();
        String probe = "print(context.hasSlot(\"clock\"))";
        Result granted = runWithCli(cli, new byte[0], "--allow-clock", "-e", probe);
        assertEquals(0, granted.code(), granted.stderr());
        assertEquals("true\n", granted.stdout());
        Result denied = runWithCli(cli, new byte[0], "-e", probe);
        assertEquals(0, denied.code(), denied.stderr());
        assertEquals("false\n", denied.stdout());
    }

    @Test
    void serviceEntryReceivesOnlyItsExplicitClockPermission(@TempDir Path directory) throws Exception {
        Path source = directory.resolve("clock-service.protos");
        Files.writeString(source, """
                hadClock: context.hasSlot("clock")
                writer: TextWriter(process.stdout(), process.stdoutEncoding())
                service: () => {
                    Actor.current().send("finish")
                    {
                        finish: () => {
                            if(hadClock) {
                                writer.writeLine("true").value()
                                if(!Integer.recognizes(clock.epochNanoseconds())) { Error().signal() }
                                writer.writeLine("true").value()
                            } else {
                                writer.writeLine("false").value()
                            }
                            Actor.current().stop()
                            null
                        }
                    }
                }
                """);
        Result denied = run("--service=service", source.toString());
        assertEquals(0, denied.code(), denied.stderr());
        assertEquals("false\n", denied.stdout());
        Result granted = run("--allow-clock", "--service=service", source.toString());
        assertEquals(0, granted.code(), granted.stderr());
        assertEquals("true\ntrue\n", granted.stdout());
    }

    @Test
    void entropyAndClockAreIndependentAndExplicitlyComposable() {
        Result entropy = run("--allow-entropy", "-e", "print(context.hasSlot(\"clock\"))");
        assertEquals(0, entropy.code(), entropy.stderr());
        assertEquals("false\n", entropy.stdout());
        Result both = run("--allow-clock", "--allow-entropy", "-e",
                "print(context.hasSlot(\"entropy\"))\nprint(Integer.recognizes(clock.epochNanoseconds()))");
        assertEquals(0, both.code(), both.stderr());
        assertEquals("true\ntrue\n", both.stdout());
    }

    @Test
    void helpDocumentsIndependentClockAndWrongAritySignalsSynchronously() {
        assertTrue(run("--help").stdout().contains("--allow-clock"));
        Result bad = run("--allow-clock", "-e",
                "print(InvalidIOArgument.handle(() => { clock.epochNanoseconds(1); false }, (error) => { true }))");
        assertEquals(0, bad.code(), bad.stderr());
        assertEquals("true\n", bad.stdout());
    }

    @Test
    void clockIsAbsentWithoutExplicitGrant() {
        Result denied = run("-e", "clock");
        assertEquals(1, denied.code());
        assertTrue(denied.stderr().startsWith("Error:"),
                denied.stderr());

        Result absent = run("-e", "print(context.hasSlot(\"clock\"))");
        assertEquals(0, absent.code(), absent.stderr());
        assertEquals("false\n", absent.stdout());
    }

    @Test
    void explicitGrantWorksForExpressionEntry() {
        Result allowed = run(
                "--allow-clock",
                "-e",
                "print(context.hasSlot(\"clock\"))");

        assertEquals(0, allowed.code(), allowed.stderr());
        assertEquals("true\n", allowed.stdout());

        Result sample = run(
                "--allow-clock",
                "-e",
                "print(Integer.recognizes(clock.epochNanoseconds()))");

        assertEquals(0, sample.code(), sample.stderr());
        assertEquals("true\n", sample.stdout());


    }

    @Test
    void otherPermissionsNeverImplyClock() {
        Result networkOnly = run(
                "--allow-network",
                "-e",
                "print(context.hasSlot(\"clock\"))");

        assertEquals(0, networkOnly.code(), networkOnly.stderr());
        assertEquals("false\n", networkOnly.stdout());

        Result both = run(
                "--allow-network",
                "--allow-clock",
                "-e",
                "print(context.hasSlot(\"clock\"))\nprint(context.hasSlot(\"network\"))");

        assertEquals(0, both.code(), both.stderr());
        assertEquals("true\ntrue\n", both.stdout());

        Result clockOnly = run(
                "--allow-clock",
                "-e",
                "print(context.hasSlot(\"network\"))");

        assertEquals(0, clockOnly.code(), clockOnly.stderr());
        assertEquals("false\n", clockOnly.stdout());
    }

    @Test
    void sourceEntryReceivesOnlyExplicitAuthorization(
            @TempDir Path directory) throws Exception {
        Path source = directory.resolve("clock.protos");
        Files.writeString(
                source, "print(context.hasSlot(\"clock\"))\n");

        Result denied = run(source.toString());
        assertEquals(0, denied.code(), denied.stderr());
        assertEquals("false\n", denied.stdout());

        Result granted = run("--allow-clock", source.toString());
        assertEquals(0, granted.code(), granted.stderr());
        assertEquals("true\n", granted.stdout());

        Files.writeString(
                source, "print(context.hasSlot(\"clock\"))\nprint(process.args().at(0))\n");

        Result argument = run(
                source.toString(), "--allow-clock");
        assertEquals(0, argument.code(), argument.stderr());
        assertEquals("false\n--allow-clock\n", argument.stdout());
    }

    @Test
    void replUsesItsOwnExplicitGrant() {
        byte[] commands =
                "context.hasSlot(\"clock\")\n:quit\n"
                        .getBytes(StandardCharsets.UTF_8);

        Result denied = runWithInput(commands);
        assertEquals(0, denied.code(), denied.stderr());
        assertTrue(denied.stdout().contains("false"),
                denied.stdout());

        Result granted = runWithInput(
                commands, "--allow-clock");
        assertEquals(0, granted.code(), granted.stderr());
        assertTrue(granted.stdout().contains("true"),
                granted.stdout());
    }

    @Test
    void debugAcceptsTheGrantBeforeItsSource() {
        Result result = run(
                "debug",
                "--allow-clock",
                "nonexistent-i163-debug.protos");

        assertEquals(1, result.code());
        assertTrue(
                result.stderr().startsWith(
                        "protos debug: cannot read"),
                result.stderr());
    }

    @Test
    void duplicatesAndInvalidOptionPlacementAreUsageErrors() {
        assertEquals(2, run(
                "--allow-clock", "--allow-clock",
                "-e", "null").code());

        assertEquals(2, run(
                "run", "--allow-clock",
                "--allow-clock", "entry").code());

        assertEquals(2, run(
                "--allow-clock", "debug",
                "--allow-clock", "entry.protos").code());

        assertEquals(2, run(
                "--allow-clock", "--help").code());

        assertEquals(2, run(
                "--allow-clock", "--version").code());

        assertEquals(2, run(
                "--allow-clock", "--service").code());
    }

    @Test
    void bundledToolsAreNeverAuthorized() {
        assertEquals(2, run("--allow-clock", "package").code());
        assertEquals(2, run("package", "--allow-clock").code());
        assertEquals(2, run("test", "--allow-clock").code());
        assertEquals(2, run("format", "--allow-clock").code());
        assertEquals(2, run("lint", "--allow-clock").code());
        assertEquals(2, run("language-server", "--allow-clock").code());
    }

    @Test
    void optionAfterApplicationEntryIsAnArgument() {
        Result result = run(
                "--allow-clock",
                "-e",
                "print(process.args().at(0))",
                "--allow-clock");

        assertEquals(0, result.code(), result.stderr());
        assertEquals("--allow-clock\n", result.stdout());
    }
}
