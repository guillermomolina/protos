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

package com.guillermomolina.protos.cli.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.guillermomolina.protos.cli.ProtosCli;
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

/** EXTERNAL_PROCESS.md §1: explicit CLI execution authorization, --allow-execution. */
@Timeout(value = 60, unit = TimeUnit.SECONDS)
final class ProtosI166ExternalExecutionCliTest {
    private record Result(int code, String stdout, String stderr) {}

    private static final String PROBE = "print(context.hasSlot(\"execution\"))";

    private static Result run(String... arguments) {
        return runWithInput(new byte[0], arguments);
    }

    private static Result runWithInput(byte[] input, String... arguments) {
        return runWithCli(new ProtosCli(), input, arguments);
    }

    private static Result runWithCli(ProtosCli cli, byte[] input, String... arguments) {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        int code = cli.run(arguments, new ByteArrayInputStream(input),
                new PrintStream(stdout), new PrintStream(stderr));
        return new Result(code, stdout.toString(StandardCharsets.UTF_8),
                stderr.toString(StandardCharsets.UTF_8));
    }

    @Test
    void absentByDefaultAndNotImpliedByOtherOptions() {
        Result absent = run("-e", PROBE);
        assertEquals(0, absent.code(), absent.stderr());
        assertEquals("false\n", absent.stdout());
        Result others = run("--allow-clock", "--allow-entropy", "--allow-network", "-e", PROBE);
        assertEquals(0, others.code(), others.stderr());
        assertEquals("false\n", others.stdout());
    }

    @Test
    void expressionEntryReceivesOnlyExecution() {
        Result granted = run("--allow-execution", "-e",
                PROBE + "\nprint(context.hasSlot(\"clock\"))\nprint(context.hasSlot(\"filesystem\"))");
        assertEquals(0, granted.code(), granted.stderr());
        assertEquals("true\nfalse\nfalse\n", granted.stdout());
    }

    @Test
    void expressionEntryLaunchesDirectlyWithExactArguments() {
        assumeTrue(Files.isExecutable(Path.of("/bin/echo")), "/bin/echo is required");
        Result launched = run("--allow-execution", "-e", """
                child: execution.start("/bin/echo", ["a b", "$HOME", "*"], Map(), null).value()
                chunk: child.stdout().read(65536).value()
                text: ""
                while(chunk !== null) {
                    text = text + Encoding.UTF8.decode(chunk)
                    chunk = child.stdout().read(65536).value()
                }
                print(text == "a b $HOME *\\n")
                """);
        assertEquals(0, launched.code(), launched.stderr());
        assertEquals("true\n", launched.stdout());
    }

    @Test
    void sourceEntryAndApplicationArgumentsKeepOrder(@TempDir Path directory) throws Exception {
        Path source = directory.resolve("execution.protos");
        Files.writeString(source, PROBE + "\nprint(process.args().at(0))\nprint(process.args().at(1))\n");
        Result granted = run("--allow-execution", source.toString(), "first", "--allow-execution");
        assertEquals(0, granted.code(), granted.stderr());
        assertEquals("true\nfirst\n--allow-execution\n", granted.stdout());
        Result denied = run(source.toString(), "--allow-execution", "second");
        assertEquals(0, denied.code(), denied.stderr());
        assertEquals("false\n--allow-execution\nsecond\n", denied.stdout());
    }

    @Test
    void replUsesItsOwnExplicitGrant() {
        byte[] commands = "context.hasSlot(\"execution\")\n:quit\n".getBytes(StandardCharsets.UTF_8);
        Result denied = runWithInput(commands);
        assertEquals(0, denied.code(), denied.stderr());
        assertTrue(denied.stdout().contains("false"), denied.stdout());
        Result granted = runWithInput(commands, "--allow-execution");
        assertEquals(0, granted.code(), granted.stderr());
        assertTrue(granted.stdout().contains("true"), granted.stdout());
    }

    @Test
    void authorizationResetsWhenTheSameCliInstanceIsReused() {
        ProtosCli cli = new ProtosCli();
        Result granted = runWithCli(cli, new byte[0], "--allow-execution", "-e", PROBE);
        assertEquals("true\n", granted.stdout(), granted.stderr());
        Result denied = runWithCli(cli, new byte[0], "-e", PROBE);
        assertEquals("false\n", denied.stdout(), denied.stderr());
    }

    @Test
    void authorizedLaunchAndDelegationLeaveNothingForTheNextUnauthorizedRun(@TempDir Path directory)
            throws Exception {
        assumeTrue(Files.isExecutable(Path.of("/bin/echo")), "/bin/echo is required");
        Files.writeString(directory.resolve("i166-cli-worker.protos"), """
                held: null
                worker: () => {
                    {
                        hold: (grant) => { held = grant; true }
                        granted: () => { context.hasSlot("execution") || (held !== null) }
                    }
                }
                null
                """);
        Path authorized = directory.resolve("authorized.protos");
        Files.writeString(authorized, """
                child: execution.start("/bin/echo", ["ran"], Map(), null).value()
                chunk: child.stdout().read(65536).value()
                worker: Actor.spawn("./i166-cli-worker.protos", "worker")
                delegated: worker.request("hold", execution).value()
                worker.stop()
                print((chunk !== null) && delegated)
                """);
        Path unauthorized = directory.resolve("unauthorized.protos");
        Files.writeString(unauthorized, """
                worker: Actor.spawn("./i166-cli-worker.protos", "worker")
                inherited: worker.request("granted").value()
                worker.stop()
                print(context.hasSlot("execution"))
                print(inherited)
                """);
        ProtosCli cli = new ProtosCli();
        Result granted = runWithCli(cli, new byte[0], "--allow-execution", authorized.toString());
        assertEquals(0, granted.code(), granted.stderr());
        assertEquals("true\n", granted.stdout());
        // Neither the initial grant nor any view delegated by the previous run reaches this one.
        Result denied = runWithCli(cli, new byte[0], unauthorized.toString());
        assertEquals(0, denied.code(), denied.stderr());
        assertEquals("false\nfalse\n", denied.stdout());
    }

    @Test
    void serviceEntryReceivesOnlyItsExplicitPermission(@TempDir Path directory) throws Exception {
        Path source = directory.resolve("execution-service.protos");
        Files.writeString(source, """
                hadExecution: context.hasSlot("execution")
                writer: TextWriter(process.stdout(), process.stdoutEncoding())
                service: () => {
                    Actor.current().send("finish")
                    {
                        finish: () => {
                            if(hadExecution) {
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
        Result granted = run("--allow-execution", "--service=service", source.toString());
        assertEquals(0, granted.code(), granted.stderr());
        assertEquals("true\n", granted.stdout());
    }

    private static final String DRAIN_TEXT =
            """
            drain: (endpoint) => {
                text: ""
                chunk: endpoint.read(65536).value()
                while(chunk !== null) {
                    text = text + Encoding.UTF8.decode(chunk)
                    chunk = endpoint.read(65536).value()
                }
                text
            }
            """;

    @Test
    void sourceEntryLaunchesAndTheContextCloseReclaimsALiveChild(@TempDir Path directory)
            throws Exception {
        assumeTrue(Files.isExecutable(Path.of("/bin/echo")) && Files.isExecutable(Path.of("/bin/sleep")));
        Path source = directory.resolve("execution-source.protos");
        Files.writeString(source, DRAIN_TEXT + """
                writer: TextWriter(process.stdout(), process.stdoutEncoding())
                if(drain(execution.start("/bin/echo", ["from-source"], Map(), null).value().stdout()) == "from-source\\n") {
                    writer.writeLine("from-source").value()
                }
                // Left running on purpose: the Process close terminates and reaps it.
                execution.start("/bin/sleep", ["30"], Map(), null).value()
                null
                """);
        Result result = run("--allow-execution", source.toString());
        assertEquals(0, result.code(), result.stderr());
        assertEquals("from-source\n", result.stdout());
    }

    @Test
    void serviceEntryLaunchesThroughItsGrant(@TempDir Path directory) throws Exception {
        assumeTrue(Files.isExecutable(Path.of("/bin/echo")));
        Path source = directory.resolve("execution-service-launch.protos");
        Files.writeString(source, DRAIN_TEXT + """
                grant: execution
                writer: TextWriter(process.stdout(), process.stdoutEncoding())
                service: () => {
                    Actor.current().send("finish")
                    {
                        finish: () => {
                            child: grant.start("/bin/echo", ["from-service"], Map(), null).value()
                            if(drain(child.stdout()) == "from-service\\n") { writer.writeLine("from-service").value() }
                            Actor.current().stop()
                            null
                        }
                    }
                }
                """);
        Result granted = run("--allow-execution", "--service=service", source.toString());
        assertEquals(0, granted.code(), granted.stderr());
        assertEquals("from-service\n", granted.stdout());
    }

    @Test
    void reusedCliLosesTheGrantAfterALaunch() {
        assumeTrue(Files.isExecutable(Path.of("/bin/echo")));
        ProtosCli cli = new ProtosCli();
        Result launched = runWithCli(cli, new byte[0], "--allow-execution", "-e", DRAIN_TEXT
                + "print(drain(execution.start(\"/bin/echo\", [\"once\"], Map(), null).value().stdout()))");
        assertEquals(0, launched.code(), launched.stderr());
        assertEquals("once\n\n", launched.stdout());
        Result denied = runWithCli(cli, new byte[0], "-e", PROBE);
        assertEquals("false\n", denied.stdout(), denied.stderr());
    }

    @Test
    void debugAndRunAcceptTheGrantInTheirOptionZone(@TempDir Path directory) {
        Result debug = run("debug", "--allow-execution", "nonexistent-i166-debug.protos");
        assertEquals(1, debug.code());
        assertTrue(debug.stderr().startsWith("protos debug: cannot read"), debug.stderr());
        // run accepts the option; without a project the entry fails, never as a usage error.
        Result run = run("run", "--allow-execution", "Main");
        assertNotEquals(2, run.code(), run.stderr());
        Result leading = run("--allow-execution", "run", "Main");
        assertNotEquals(2, leading.code(), leading.stderr());
    }

    @Test
    void duplicatesValuesAndMisplacementAreUsageErrors() {
        assertEquals(2, run("--allow-execution", "--allow-execution", "-e", "null").code());
        assertEquals(2, run("--allow-execution=yes", "-e", "null").code());
        assertEquals(2, run("run", "--allow-execution=1", "Main").code());
        assertEquals(2, run("run", "--allow-execution", "--allow-execution", "Main").code());
        assertEquals(2, run("--allow-execution", "debug", "--allow-execution", "x.protos").code());
        assertEquals(2, run("--allow-execution", "--help").code());
        assertEquals(2, run("--allow-execution", "--version").code());
    }

    @Test
    void bundledToolsAreNeverAuthorized() {
        assertEquals(2, run("--allow-execution", "package").code());
        assertEquals(2, run("package", "--allow-execution").code());
        assertEquals(2, run("test", "--allow-execution").code());
        assertEquals(2, run("format", "--allow-execution").code());
        assertEquals(2, run("lint", "--allow-execution").code());
        assertEquals(2, run("lint", "--allow-execution=x").code());
        assertEquals(2, run("language-server", "--allow-execution").code());
    }

    @Test
    void optionAfterTheEntryIsAnApplicationArgument() {
        Result result = run("-e", PROBE + "\nprint(process.args().at(0))", "--allow-execution");
        assertEquals(0, result.code(), result.stderr());
        assertEquals("false\n--allow-execution\n", result.stdout());
    }

    @Test
    void helpDocumentsTheIndependentOption() {
        String help = run("--help").stdout();
        assertTrue(help.contains("--allow-execution (initial application RootActor only"), help);
        assertTrue(help.contains("protos run [--allow-network] [--allow-resolver] [--allow-entropy]"
                + " [--allow-clock] [--allow-execution]"), help);
    }

    @Test
    void cliHostReportsVerifiedExitStatuses() {
        assumeTrue(Files.isExecutable(Path.of("/bin/sh")), "/bin/sh is required");
        Result result = run("--allow-execution", "-e", """
                run: (script) => {
                    child: execution.start("/bin/sh", ["-c", script], Map(), null).value()
                    child.stdin().close().value()
                    child.wait().value()
                }
                normal: run("exit 143")
                print(normal.exited() && (normal.exitCode() == 143) && (normal.signaled() == false))
                killed: run("kill -TERM $$")
                print((killed.exited() == false) && (killed.exitCode() === null) && killed.signaled())
                """);
        assertEquals(0, result.code(), result.stderr());
        assertEquals("true\ntrue\n", result.stdout());
    }
}
