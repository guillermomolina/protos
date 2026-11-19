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
package com.guillermomolina.protos.cli.resolver;

import static org.junit.jupiter.api.Assertions.*;

import com.guillermomolina.protos.cli.ProtosCli;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Explicit launcher selection, without any host DNS query. */
final class ProtosI164ResolverCliTest {
    private static final String PROBE = """
            print(context.hasSlot("resolver"))
            print(context.hasSlot("network"))
            if(context.hasSlot("resolver")) { print(resolver != null) }
            """;

    private record Result(int code, String stdout, String stderr) {}

    private static Result run(String... args) {
        return run(new ProtosCli(), "", args);
    }

    private static Result run(ProtosCli cli, String input, String... args) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int code = cli.run(args, new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)),
                new PrintStream(out), new PrintStream(err));
        return new Result(code, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }

    @Test
    void sourceExpressionAndReplHaveIndependentAuthority(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("authority.protos");
        Files.writeString(file, PROBE);
        for (boolean resolver : new boolean[] {false, true}) {
            for (boolean network : new boolean[] {false, true}) {
                List<String> flags = new ArrayList<>();
                if (resolver) flags.add("--allow-resolver");
                if (network) flags.add("--allow-network");
                String expected = resolver + "\n" + network + "\n" + (resolver ? "true\n" : "");
                for (List<String> entry : List.of(List.of(file.toString()), List.of("-e", PROBE))) {
                    List<String> args = new ArrayList<>(flags);
                    args.addAll(entry);
                    Result result = run(args.toArray(String[]::new));
                    assertEquals(0, result.code(), result.stderr());
                    assertEquals(expected, result.stdout());
                }
                Result repl = run(new ProtosCli(), PROBE + ":quit\n", flags.toArray(String[]::new));
                assertEquals(0, repl.code(), repl.stderr());
                // print emits exact lines even though the REPL also renders expression results.
                assertTrue(repl.stdout().contains("protos> " + resolver + "\n"), repl.stdout());
                assertTrue(repl.stdout().contains("protos> " + network + "\n"), repl.stdout());
            }
        }
    }

    @Test
    void resolverComposesIndependentlyWithExistingApplicationOptions() {
        for (List<String> options : List.of(
                List.of("--inspect", "--allow-resolver"),
                List.of("--allow-resolver", "--inspect"),
                List.of("--allow-entropy", "--allow-resolver"),
                List.of("--allow-clock", "--allow-resolver"),
                List.of("--allow-network", "--allow-resolver"),
                List.of("--allow-resolver", "--allow-network"))) {
            List<String> args = new ArrayList<>(options);
            args.addAll(List.of("-e", PROBE));
            Result result = run(args.toArray(String[]::new));
            assertEquals(0, result.code(), options + ": " + result.stderr());
            String expected = options.contains("--allow-network")
                    ? "true\ntrue\ntrue\n" : "true\nfalse\ntrue\n";
            assertEquals(expected, result.stdout(), options.toString());
        }
    }

    @Test
    void postEntryResolverArgumentNeverAddsAuthorityToAnotherApplicationOption() {
        for (String option : List.of("--inspect", "--allow-entropy", "--allow-clock", "--allow-network")) {
            Result result = run(option, "-e",
                    PROBE + "print(process.args().size())\nprint(process.args().at(0))\n",
                    "--allow-resolver");
            assertEquals(0, result.code(), option + ": " + result.stderr());
            String expected = option.equals("--allow-network")
                    ? "false\ntrue\n1\n--allow-resolver\n"
                    : "false\nfalse\n1\n--allow-resolver\n";
            assertEquals(expected, result.stdout(), option);
        }
    }

    @Test
    void reusedLauncherResetsEvenAfterUsageFailure() {
        ProtosCli cli = new ProtosCli();
        Result granted = run(cli, "", "--allow-resolver", "-e", PROBE);
        assertEquals(0, granted.code(), granted.stderr());
        assertEquals("true\nfalse\ntrue\n", granted.stdout());
        assertEquals(2, run(cli, "", "--allow-resolver", "--help").code());
        Result denied = run(cli, "", "-e", PROBE);
        assertEquals(0, denied.code(), denied.stderr());
        assertEquals("false\nfalse\n", denied.stdout());
    }

    @Test
    void onlyPostEntryTokensReachProcessArgs(@TempDir Path directory) throws Exception {
        String source = "print(context.hasSlot(\"resolver\"))\nprint(process.args().size())\nprint(process.args().at(0))";
        Path file = directory.resolve("args.protos");
        Files.writeString(file, source);
        for (List<String> entry : List.of(List.of(file.toString()), List.of("-e", source))) {
            List<String> args = new ArrayList<>(entry);
            args.add("--allow-resolver");
            Result denied = run(args.toArray(String[]::new));
            assertEquals(0, denied.code(), denied.stderr());
            assertEquals("false\n1\n--allow-resolver\n", denied.stdout());
            args.add(0, "--allow-resolver");
            Result granted = run(args.toArray(String[]::new));
            assertEquals(0, granted.code(), granted.stderr());
            assertEquals("true\n1\n--allow-resolver\n", granted.stdout());
        }
    }

    @Test
    void malformedDuplicateAndNonApplicationUsesAreUsageErrors() {
        for (String[] args : new String[][] {
                {"--allow-resolver", "--allow-resolver", "-e", "null"},
                {"--allow-resolver", "run", "--allow-resolver", "Main"},
                {"--allow-resolver", "debug", "--allow-resolver", "main.protos"},
                {"run", "--allow-resolver"}, {"debug", "--allow-resolver"},
                {"--allow-resolver", "-e"}, {"--allow-resolver", "--help"},
                {"--allow-resolver", "--version"}, {"--allow-resolver", "-h"},
                {"--allow-resolver", "-v"}, {"run", "--allow-resolver", "--unknown"},
                {"debug", "--allow-resolver", "--unknown"}}) {
            assertEquals(2, run(args).code(), String.join(" ", args));
        }
        for (String option : List.of("--allow-resolver=", "--allow-resolver=true",
                "--allow-resolver=false", "--allow-resolver=anything")) {
            assertEquals(2, run(option, "-e", "null").code());
            assertEquals(2, run("run", option, "Main").code());
            assertEquals(2, run("debug", option, "main.protos").code());
        }
        for (String tool : List.of("test", "lint", "package", "format", "language-server")) {
            for (String option : List.of("--allow-resolver", "--allow-resolver=true")) {
                assertEquals(2, run(option, tool).code(), option + " " + tool);
                assertEquals(2, run(tool, option).code(), tool + " " + option);
            }
        }
        assertTrue(run("--help").stdout().contains("--allow-resolver"));
    }

    @Test
    void sourceServiceUsesResolverAfterReadyWithoutNetwork(@TempDir Path directory) throws Exception {
        Path source = directory.resolve("service.protos");
        Files.writeString(source, """
                present: context.hasSlot("resolver")
                writer: TextWriter(process.stdout(), process.stdoutEncoding())
                service: () => {
                    Actor.current().send("finish")
                    {
                        finish: () => {
                            if(present) { resolver.close().value() }
                            if(present) { writer.writeLine("true").value() } else { writer.writeLine("false").value() }
                            if(context.hasSlot("network")) { Error().signal() }
                            writer.writeLine("false").value()
                            Actor.current().stop()
                            null
                        }
                    }
                }
                """);
        Result allowed = run("--allow-resolver", "--service=service", source.toString());
        assertEquals(0, allowed.code(), allowed.stderr());
        assertEquals("true\nfalse\n", allowed.stdout());
        Result denied = run("--service=service", source.toString());
        assertEquals(0, denied.code(), denied.stderr());
        assertEquals("false\nfalse\n", denied.stdout());
    }
}
