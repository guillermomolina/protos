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

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class ProtosTestToolFileSelectionPublicIntegrationTest {
    private static final Path URI_CASE =
            Path.of(
                    "protos",
                    "tests",
                    "library",
                    "uri",
                    "parse-components.protos");

    private static final String EXPECTED_STDOUT =
            "Protos test tool bootstrap\n"
                    + "test\n";

    private static final String EXPECTED_URI_PROGRESS =
            "[uri] 0/1\n"
                    + "[uri] 1/1 passed\n"
                    + "1 passed, 0 failed\n";

    @Test
    void relativeExactFileRunsOnlyItsAuthoritativeCase() {
        R result =
                run(
                        "test",
                        "--file",
                        URI_CASE.toString());

        assertSuccessfulSingleUriCase(result);
    }

    @Test
    void absoluteExactFileRunsTheSameAuthoritativeCase() {
        R result =
                run(
                        "test",
                        "--file",
                        URI_CASE.toAbsolutePath().normalize().toString());

        assertSuccessfulSingleUriCase(result);
    }

    @Test
    void existingFileOutsideAuthorizedCorporaFailsBeforeScheduling() {
        R result =
                run(
                        "test",
                        "--file",
                        Path.of(
                                        "protos",
                                        "examples",
                                        "hello-world.protos")
                                .toString());

        assertSelectionErrorBeforeScheduling(result);
    }

    @Test
    void authorizedExistingFileWithoutCaseSpecFailsBeforeScheduling()
            throws Exception {
        Path libraryRoot =
                Path.of("protos", "tests", "library");

        Path unplanned =
                Files.createTempFile(
                        libraryRoot,
                        "tool008-unplanned-",
                        ".protos");

        try {
            R result =
                    run(
                            "test",
                            "--file",
                            unplanned.toString());

            assertSelectionErrorBeforeScheduling(result);
        } finally {
            Files.deleteIfExists(unplanned);
        }
    }

    private static void assertSuccessfulSingleUriCase(R result) {
        assertEquals(0, result.code());
        assertEquals(EXPECTED_STDOUT, result.out());
        assertEquals(EXPECTED_URI_PROGRESS, result.err());
    }

    private static void assertSelectionErrorBeforeScheduling(R result) {
        assertEquals(1, result.code());
        assertTrue(
                result.out().isBlank(),
                result.out());
        assertTrue(
                result.err().startsWith("Test tool error:"),
                result.err());

        assertFalse(
                result.err().contains("[uri]"),
                result.err());
        assertFalse(
                result.err().contains(" passed, "),
                result.err());
    }

    private static R run(String... args) {
        ByteArrayOutputStream out =
                new ByteArrayOutputStream();
        ByteArrayOutputStream err =
                new ByteArrayOutputStream();

        int code =
                new ProtosCli()
                        .run(
                                args,
                                InputStream.nullInputStream(),
                                new PrintStream(out),
                                new PrintStream(err));

        return new R(
                code,
                out.toString(StandardCharsets.UTF_8),
                err.toString(StandardCharsets.UTF_8));
    }

    private record R(
            int code,
            String out,
            String err) {}
}
