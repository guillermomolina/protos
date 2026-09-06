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
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/**
 * Executable regression for shipped learning material that depends on the CLI-owned print binding.
 *
 * <p>The observable programs remain ordinary Protos source. This host-side test is deliberately a
 * CLI integration harness: discovery and launcher/output assertions are Java concerns, while the
 * behavior under test remains in the shipped .protos examples and tutorials themselves.
 */
final class ProtosCliLearningMaterialsTest {
    private static final List<Path> LEARNING_ROOTS =
            List.of(Path.of("protos", "examples"), Path.of("protos", "tutorials"));
    private static final Duration PROGRAM_TIMEOUT = Duration.ofSeconds(15);

    @Test
    void standaloneEntryRunsInsideRootActorTaskSoFutureValueCanSuspend() {
        Result result = run("-e", "print((() => { 42 }).future().value())");

        assertEquals(0, result.code(), result.stderr());
        assertEquals("42\n", result.stdout());
        assertTrue(result.stderr().isBlank(), result.stderr());
    }

    @TestFactory
    List<DynamicTest> printDependentLearningMaterialsRunThroughStandaloneCli() throws Exception {
        List<Path> programs = new ArrayList<>();
        for (Path root : LEARNING_ROOTS) {
            try (var paths = Files.walk(root)) {
                for (Path path :
                        paths.filter(Files::isRegularFile)
                                .filter(candidate -> candidate.toString().endsWith(".protos"))
                                .sorted()
                                .toList()) {
                    if (Files.readString(path, StandardCharsets.UTF_8).contains("print(")) {
                        programs.add(path);
                    }
                }
            }
        }

        assertFalse(programs.isEmpty(), "expected shipped print-dependent learning material");

        return programs.stream()
                .sorted()
                .map(
                        path ->
                                dynamicTest(
                                        path.toString(),
                                        () ->
                                                assertTimeoutPreemptively(
                                                        PROGRAM_TIMEOUT,
                                                        () -> assertRuns(path))))
                .toList();
    }

    private static void assertRuns(Path path) {
        Result result = run(path.toString());

        assertEquals(
                0,
                result.code(),
                () -> path + " failed through standalone CLI:\n" + result.stderr());
        assertFalse(
                result.stdout().isEmpty(),
                () -> path + " invoked no observable print output");
        assertTrue(
                result.stderr().isBlank(),
                () -> path + " wrote unexpected stderr:\n" + result.stderr());
    }

    private static Result run(String... args) {
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();
        int code =
                new ProtosCli()
                        .run(
                                args,
                                InputStream.nullInputStream(),
                                new PrintStream(out),
                                new PrintStream(err));
        return new Result(
                code,
                out.toString(StandardCharsets.UTF_8),
                err.toString(StandardCharsets.UTF_8));
    }

    private record Result(int code, String stdout, String stderr) {}
}
