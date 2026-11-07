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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ProtosCliNetworkOptionTest {

    private record Result(int code, String stdout, String stderr) {}

    private static Result run(String... arguments) {
        return runWithInput(new byte[0], arguments);
    }

    private static Result runWithInput(byte[] input, String... arguments) {
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        int code =
                new ProtosCli()
                        .run(
                                arguments,
                                new ByteArrayInputStream(input),
                                new PrintStream(out),
                                new PrintStream(err));

        return new Result(
                code,
                out.toString(StandardCharsets.UTF_8),
                err.toString(StandardCharsets.UTF_8));
    }

    @Test
    void explicitNetworkIsAvailableToExpressionEntryOnlyWhenAuthorized() {
        Result denied = run("-e", "network");
        assertEquals(1, denied.code());
        assertTrue(denied.stderr().startsWith("Error:"), denied.stderr());

        Result allowed = run(
                "--allow-network",
                "-e",
                "print(network === network)",
                "application-argument");

        assertEquals(0, allowed.code(), allowed.stderr());
        assertEquals("true\n", allowed.stdout());

        Result arguments = run(
                "--allow-network",
                "-e",
                "print(process.args().at(0))",
                "--allow-network");

        assertEquals(0, arguments.code(), arguments.stderr());
        assertEquals("--allow-network\n", arguments.stdout());
    }

    @Test
    void networkGrantDoesNotImplyResolverOrFilesystem() {
        Result resolver = run("--allow-network", "-e", "resolver");
        Result filesystem = run("--allow-network", "-e", "filesystem");

        assertEquals(1, resolver.code());
        assertTrue(resolver.stderr().startsWith("Error:"), resolver.stderr());
        assertEquals(1, filesystem.code());
        assertTrue(filesystem.stderr().startsWith("Error:"), filesystem.stderr());
    }

    @Test
    void directSourceUsesExplicitGrantAndPreservesPostEntryArguments(
            @TempDir Path directory) throws Exception {
        Path entry = directory.resolve("entry.protos");

        Files.writeString(entry, "print(network === network)\n");

        Result allowed = run("--allow-network", entry.toString());
        assertEquals(0, allowed.code(), allowed.stderr());
        assertEquals("true\n", allowed.stdout());

        Result denied = run(entry.toString());
        assertEquals(1, denied.code());
        assertTrue(denied.stderr().startsWith("Error:"), denied.stderr());

        Files.writeString(entry, "print(process.args().at(0))\n");

        Result postEntry = run(entry.toString(), "--allow-network");
        assertEquals(0, postEntry.code(), postEntry.stderr());
        assertEquals("--allow-network\n", postEntry.stdout());
    }

    @Test
    void replReceivesNetworkOnlyWithExplicitGrant() {
        Result allowed = runWithInput(
                "network === network\n:quit\n".getBytes(StandardCharsets.UTF_8),
                "--allow-network");

        assertEquals(0, allowed.code(), allowed.stderr());
        assertTrue(allowed.stdout().contains("true"), allowed.stdout());

        Result denied = runWithInput(
                "network\n:quit\n".getBytes(StandardCharsets.UTF_8));

        assertEquals(0, denied.code());
        assertTrue(denied.stderr().contains("Error:"), denied.stderr());
    }

    @Test
    void debugAcceptsNetworkOptionBeforeItsSourceTarget() {
        Result result = run(
                "debug", "--allow-network", "nonexistent-i154-debug.protos");

        assertEquals(1, result.code());
        assertTrue(
                result.stderr().startsWith("protos debug: cannot read"),
                result.stderr());
    }

    @Test
    void duplicateNetworkOptionsAreUsageFailures() {
        assertEquals(
                2,
                run("--allow-network", "--allow-network", "-e", "null").code());

        assertEquals(
                2,
                run("run", "--allow-network", "--allow-network", "app").code());

        assertEquals(
                2,
                run("--allow-network", "debug",
                        "--allow-network", "entry.protos").code());
    }

    @Test
    void networkOptionsCannotAuthorizeBundledTools() {
        assertEquals(2, run("--allow-network", "package").code());
        assertEquals(2, run("package", "--allow-network").code());
        assertEquals(2, run("test", "--allow-network").code());
        assertEquals(2, run("format", "--allow-network").code());
        assertEquals(2, run("lint", "--allow-network").code());
        assertEquals(2, run("language-server", "--allow-network").code());
        assertEquals(2, run("--allow-network", "--version").code());
    }
}
