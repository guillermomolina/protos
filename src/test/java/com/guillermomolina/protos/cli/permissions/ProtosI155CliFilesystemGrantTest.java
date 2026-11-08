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
package com.guillermomolina.protos.cli.permissions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.guillermomolina.protos.cli.ProtosCli;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * I155 / I156 / D248-B public CLI behavior of {@code --allow-filesystem=<directory>}, served by
 * the Linux FFM kernel-confined backend. Positive cases require the host confinement primitives
 * and report an explicit admission (skip) on a host without them; denials never depend on it.
 */
final class ProtosI155CliFilesystemGrantTest {
    private static final String WRITE_THEN_READ =
            """
            Files: import("std:io/files")
            path: Path.relative().child("nested").child("note.txt")
            Files.writeAllText(filesystem, path, "granted", Encoding.UTF8).value()
            print(Files.readAllText(filesystem, path, Encoding.UTF8).value())
            """;

    private static final String READ_NOTE =
            """
            Files: import("std:io/files")
            print(Files.readAllText(
                filesystem, Path.relative().child("nested").child("note.txt"), Encoding.UTF8
            ).value())
            """;

    @TempDir Path temporary;

    private record Result(int code, String stdout, String stderr) {}

    private static Result run(String... arguments) {
        return runWithInput(new byte[0], arguments);
    }

    private static Result runWithInput(byte[] input, String... arguments) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int code =
                new ProtosCli()
                        .run(
                                arguments,
                                new ByteArrayInputStream(input),
                                new PrintStream(out),
                                new PrintStream(err));
        return new Result(
                code, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }

    private Path grantRoot() throws IOException {
        Path root = Files.createDirectories(temporary.resolve("data"));
        Files.createDirectories(root.resolve("nested"));
        return root;
    }

    /**
     * A grant root on a host that can actually provision the grant. A host without the I156
     * primitives (Linux x86_64, FFM, Landlock ABI 3) refuses every grant before guest code runs;
     * positive cases are then reported as an explicit admission, never as a pass.
     */
    private Path grantedRoot() throws IOException {
        Path root = grantRoot();
        Result probe = run(grant(root), "-e", "null");
        assumeTrue(
                probe.code() == 0,
                "ADMISSION: host cannot provide the I156 confinement primitives: " + probe.stderr());
        return root;
    }

    private static String grant(Path root) {
        return "--allow-filesystem=" + root;
    }

    @Test
    void expressionEntryIsDeniedWithoutGrantAndAuthorizedWithIt() throws Exception {
        Result denied = run("-e", "filesystem");
        assertEquals(1, denied.code());
        assertTrue(denied.stderr().startsWith("Error:"), denied.stderr());

        Path root = grantedRoot();
        Result allowed = run(grant(root), "-e", WRITE_THEN_READ);
        assertEquals(0, allowed.code(), allowed.stderr());
        assertEquals("granted\n", allowed.stdout());
        assertEquals("granted", Files.readString(root.resolve("nested/note.txt")));
    }

    @Test
    void directSourceIsDeniedWithoutGrantAndAuthorizedWithIt() throws Exception {
        Path root = grantedRoot();
        Path source = Files.writeString(temporary.resolve("app.protos"), WRITE_THEN_READ);

        Result denied = run(source.toString());
        assertEquals(1, denied.code());
        assertTrue(denied.stderr().startsWith("Error:"), denied.stderr());
        assertTrue(Files.notExists(root.resolve("nested/note.txt")));

        Result allowed = run(grant(root), source.toString(), "arg1");
        assertEquals(0, allowed.code(), allowed.stderr());
        assertEquals("granted\n", allowed.stdout());
    }

    @Test
    void pipedReplReceivesTheGrant() throws Exception {
        Path root = grantedRoot();
        Result allowed =
                runWithInput(
                        "filesystem === filesystem\n:quit\n".getBytes(StandardCharsets.UTF_8),
                        grant(root));
        assertEquals(0, allowed.code(), allowed.stderr());
        assertTrue(allowed.stdout().contains("true"), allowed.stdout());
    }

    @Test
    void relativeGrantResolvesAgainstInvocationDirectory() throws Exception {
        Path root = grantedRoot();
        Path relative = Path.of("").toAbsolutePath().relativize(root);
        assumeTrue(!relative.isAbsolute(), "temporary directory has no relative spelling");
        Result allowed = run("--allow-filesystem=" + relative, "-e", WRITE_THEN_READ);
        assertEquals(0, allowed.code(), allowed.stderr());
        assertEquals("granted", Files.readString(root.resolve("nested/note.txt")));
    }

    @Test
    void nestedOperationsEntriesReplaceAndRemoveWork() throws Exception {
        Path root = grantedRoot();
        Files.writeString(root.resolve("nested/old.txt"), "old");
        String program =
                """
                Files: import("std:io/files")
                nested: Path.relative().child("nested")
                Files.writeAllText(filesystem, nested.child("new.txt"), "new", Encoding.UTF8).value()
                kinds: ""
                filesystem.entries(Path.relative()).value().each((entry) => {
                    kinds = kinds + entry.name + ":" + entry.kind + ";"
                })
                print(kinds)
                filesystem.replace(nested.child("new.txt"), nested.child("old.txt")).value()
                print(Files.readAllText(filesystem, nested.child("old.txt"), Encoding.UTF8).value())
                filesystem.remove(nested.child("old.txt")).value()
                print(filesystem.entries(nested).value().size())
                """;
        Result result = run(grant(root), "-e", program);
        assertEquals(0, result.code(), result.stderr());
        assertEquals("nested:directory;\nnew\n0\n", result.stdout());
        assertTrue(Files.isDirectory(root.resolve("nested")));
        assertTrue(Files.notExists(root.resolve("nested/old.txt")));
    }

    @Test
    void dataPersistsAcrossSeparateInvocations() throws Exception {
        Path root = grantedRoot();
        assertEquals(0, run(grant(root), "-e", WRITE_THEN_READ).code());
        Result second = run(grant(root), "-e", READ_NOTE);
        assertEquals(0, second.code(), second.stderr());
        assertEquals("granted\n", second.stdout());
    }

    @Test
    void differentGrantsDoNotShareAuthority() throws Exception {
        Path first = grantedRoot();
        Path second = Files.createDirectories(temporary.resolve("other/nested")).getParent();
        assertEquals(0, run(grant(first), "-e", WRITE_THEN_READ).code());
        Result other = run(grant(second), "-e", READ_NOTE);
        assertEquals(1, other.code());
        assertTrue(other.stderr().startsWith("Error:"), other.stderr());
    }

    @Test
    void postEntryFlagIsAnOrdinaryApplicationArgument() throws Exception {
        Path root = grantRoot();
        Result arguments =
                run("-e", "print(process.args().at(0))", grant(root));
        assertEquals(0, arguments.code(), arguments.stderr());
        assertEquals(grant(root) + "\n", arguments.stdout());

        Result noAuthority = run("-e", "filesystem", grant(root));
        assertEquals(1, noAuthority.code());
        assertTrue(noAuthority.stderr().startsWith("Error:"), noAuthority.stderr());
    }

    @Test
    void malformedOptionsAreUsageFailures() throws Exception {
        Path root = grantRoot();
        assertEquals(2, run(grant(root), grant(root), "-e", "null").code());
        assertEquals(2, run("run", grant(root), grant(root), "app").code());
        assertEquals(2, run(grant(root), "debug", grant(root), "entry.protos").code());
        assertEquals(2, run("--allow-filesystem", "-e", "null").code());
        assertEquals(2, run("--allow-filesystem=", "-e", "null").code());
        assertEquals(2, run("--allow-filesystem=bad\0path", "-e", "null").code());
        assertEquals(2, run(grant(root), "--version").code());
        assertEquals(2, run(grant(root), "--help").code());
    }

    @Test
    void provisioningFailuresExitOneBeforeAnyGuestExpression() throws Exception {
        Path missing = temporary.resolve("missing");
        Result absent = run(grant(missing), "-e", "print(\"ran\")");
        assertEquals(1, absent.code());
        assertFalse(absent.stdout().contains("ran"), absent.stdout());
        assertTrue(absent.stderr().contains("cannot authorize Filesystem root"), absent.stderr());
        assertFalse(absent.stderr().contains("Internal error"), absent.stderr());
        assertTrue(Files.notExists(missing), "the directory is never created");

        Path file = Files.writeString(temporary.resolve("file"), "x");
        Result notDirectory = run(grant(file), "-e", "print(\"ran\")");
        assertEquals(1, notDirectory.code());
        assertFalse(notDirectory.stdout().contains("ran"));
    }

    @Test
    void finalRootSymlinkIsRefused() throws Exception {
        Path real = grantRoot();
        Path link = temporary.resolve("link");
        try {
            Files.createSymbolicLink(link, real);
        } catch (UnsupportedOperationException | IOException | SecurityException unavailable) {
            assumeTrue(false, "host cannot create symlink fixture");
        }
        Result refused = run(grant(link), "-e", "print(\"ran\")");
        assertEquals(1, refused.code());
        assertFalse(refused.stdout().contains("ran"));
        assertTrue(refused.stderr().contains("cannot authorize Filesystem root"), refused.stderr());
    }

    @Test
    void bundledToolsCannotReceiveTheGrant() throws Exception {
        Path root = grantRoot();
        assertEquals(2, run(grant(root), "package").code());
        assertEquals(2, run("package", grant(root)).code());
        assertEquals(2, run("test", grant(root)).code());
        assertEquals(2, run("format", grant(root)).code());
        assertEquals(2, run("lint", grant(root)).code());
        assertEquals(2, run("language-server", grant(root)).code());
    }

    @Test
    void grantImpliesNoNetworkOrResolver() throws Exception {
        Path root = grantedRoot();
        Result network = run(grant(root), "-e", "network");
        Result resolver = run(grant(root), "-e", "resolver");
        assertEquals(1, network.code());
        assertTrue(network.stderr().startsWith("Error:"), network.stderr());
        assertEquals(1, resolver.code());
        assertTrue(resolver.stderr().startsWith("Error:"), resolver.stderr());
    }

    @Test
    void grantCombinesWithInspectionAndPreservesForeignProviderParsing() throws Exception {
        Path root = grantedRoot();
        Result inspected =
                run("--inspect", grant(root), "-e", "print(filesystem === filesystem)");
        assertEquals(0, inspected.code(), inspected.stderr());
        assertEquals("true\n", inspected.stdout());

        Result provider =
                run(grant(root), "--foreign-provider-path",
                        temporary.resolve("no-provider").toString(), "-e", "null");
        assertEquals(2, provider.code());
        assertTrue(provider.stderr().contains("no such foreign provider path"), provider.stderr());

        // The grant does not make --inspect eligible for run.
        assertEquals(2, run("--inspect", grant(root), "run", "app").code());
    }

    @Test
    void exitCodesSeparateUsageProvisioningAndGuestFailures() throws Exception {
        Path root = grantedRoot();
        assertEquals(2, run("--allow-filesystem", "-e", "null").code());
        assertEquals(1, run(grant(temporary.resolve("absent")), "-e", "null").code());
        Result guest = run(grant(root), "-e", "filesystem.remove(Path.relative().child(\"x\")).value()");
        assertEquals(1, guest.code());
        assertTrue(guest.stderr().startsWith("Error:"), guest.stderr());
        assertEquals(0, run(grant(root), "-e", "null").code());
    }
}
