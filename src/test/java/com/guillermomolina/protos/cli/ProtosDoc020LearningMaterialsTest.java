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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.guillermomolina.protos.execution.ProtosNioReadOnlyTreeFilesystemBackend;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Exercises DOC020 lessons using the public package application entry. */
final class ProtosDoc020LearningMaterialsTest {
    private static final Path TUTORIAL =
            Path.of(
                    "protos", "tutorials",
                    "08-language-interactions", "06-package-exports");

    private static final Path PRICING =
            Path.of(
                    "protos", "examples",
                    "modules", "public-module-pricing");

    private static final Path INVOICE =
            Path.of(
                    "protos", "examples",
                    "modules", "package-invoice");

    @TempDir Path temp;

    @Test
    void packageLessonUsesPublicModuleWithPrivateCapture() throws Exception {
        Result result = run(TUTORIAL, "Main");
        assertEquals(0, result.status(), result.stderr());
        assertEquals("package-total=44\n", result.stdout());
        assertTrue(result.stderr().isBlank(), result.stderr());
    }

    @Test
    void pricingServicePublishesSelectedSlots() throws Exception {
        Result result = run(PRICING, "Main");
        assertEquals(0, result.status(), result.stderr());
        assertEquals("price=42\n", result.stdout());
        assertTrue(result.stderr().isBlank(), result.stderr());
    }

    @Test
    void workspaceUsesDeclaredDependencyExport() throws Exception {
        Result result = run(INVOICE, "Main");
        assertEquals(0, result.status(), result.stderr());
        assertEquals("invoice-total=49\n", result.stdout());
        assertTrue(result.stderr().isBlank(), result.stderr());
    }

    @Test
    void unauthorizedModuleAndSourceAccessFails() throws Exception {
        for (String entry : List.of(
                "CheckPrivate",
                "CheckDepPrivate",
                "CheckDepSource",
                "CheckMissingInitializer")) {
            Result result = run(INVOICE, entry);
            assertNotEquals(
                    0, result.status(), entry + " unexpectedly succeeded");
            assertTrue(
                    result.stdout().isBlank(),
                    entry + ": " + result.stdout());
        }

        assertNotEquals(0, run(PRICING, "CheckPrivate").status());
        assertNotEquals(0, run(TUTORIAL, "CheckPrivate").status());
    }

    @Test
    void legacyManifestFailsEvenWhenPackageTomlExists() throws Exception {
        Path project = temp.resolve("old-and-new");

        try (Stream<Path> sources = Files.walk(INVOICE)) {
            for (Path source : sources.toList()) {
                Path destination = project.resolve(INVOICE.relativize(source));

                if (Files.isDirectory(source)) {
                    Files.createDirectories(destination);
                } else {
                    Files.copy(source, destination);
                }
            }
        }

        // Establish that the identical project succeeds before corruption.
        Result baseline = run(project, "Main");
        assertEquals(0, baseline.status(), baseline.stderr());
        assertEquals("invoice-total=49\n", baseline.stdout());

        // D242 rejects the legacy filename even beside a valid package.toml.
        Files.writeString(
                project.resolve("protos.toml"),
                "manifest-version = 1\n",
                StandardCharsets.UTF_8);

        Result rejected = run(project, "Main");

        assertNotEquals(0, rejected.status());
        assertTrue(rejected.stdout().isBlank(), rejected.stdout());
    }

    private static Result run(Path project, String entry) throws Exception {
        Path root = project.toAbsolutePath().normalize();

        try (ProtosNioReadOnlyTreeFilesystemBackend backend =
                new ProtosNioReadOnlyTreeFilesystemBackend(root)) {
            assumeTrue(
                    backend.secureConfinementAvailable(),
                    "host provider has no SecureDirectoryStream");
        }

        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        int status = new ProtosCli().runWorkspaceApplication(
                root,
                entry,
                List.of(),
                InputStream.nullInputStream(),
                new PrintStream(stdout, true, StandardCharsets.UTF_8),
                new PrintStream(stderr, true, StandardCharsets.UTF_8));

        return new Result(
                status,
                stdout.toString(StandardCharsets.UTF_8),
                stderr.toString(StandardCharsets.UTF_8));
    }

    private record Result(
            int status,
            String stdout,
            String stderr) {}
}
