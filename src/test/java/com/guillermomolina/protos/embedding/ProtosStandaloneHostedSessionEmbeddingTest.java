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
package com.guillermomolina.protos.embedding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.guillermomolina.protos.execution.ProtosExecutionOutcome;
import com.guillermomolina.protos.execution.ProtosStandaloneHostedSession;
import com.guillermomolina.protos.runtime.ProtosIntegerValue;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Exercises the reusable JVM embedding session exactly as an external Java consumer would. */
final class ProtosStandaloneHostedSessionEmbeddingTest {
    private static final Path CORE = Path.of("protos", "lib", "core");
    private static final int REPEATS = 5;

    @TempDir Path directory;

    @Test
    void recursiveFibonacciRunIsInvokedRepeatedlyInOneSession() throws Exception {
        assertRepeatedRun(
                """
                fibonacci: (n) => {
                    result: n

                    (n >= 2).ifTrue() {
                        result = fibonacci(n - 1) + fibonacci(n - 2)
                    }

                    result
                }

                run: () => {
                    fibonacci(30)
                }

                run()
                """,
                new BigInteger("832040"));
    }

    @Test
    void recursiveFactorialRunIsInvokedRepeatedlyInOneSession() throws Exception {
        assertRepeatedRun(
                """
                factorial: (n) => {
                    result: 1

                    (n > 1).ifTrue() {
                        result = n * factorial(n - 1)
                    }

                    result
                }

                run: () => {
                    factorial(20)
                }

                run()
                """,
                new BigInteger("2432902008176640000"));
    }

    /**
     * Mutable module state survives across calls only when every call enters the one live entry
     * module of one live Process; a fresh {@code executeFile} per call would restart at 1.
     */
    @Test
    void repeatedInvocationsShareTheLiveEntryModuleState() throws Exception {
        try (ProtosStandaloneHostedSession session = open("counter: 0\n"
                + "run: () => {\n    counter = counter + 1\n    counter\n}\n"
                + "run()\n")) {
            assertInteger(1, session.initialOutcome());
            for (int expected = 2; expected < 2 + REPEATS; expected++) {
                assertInteger(expected, session.invokeTopLevel("run"));
            }
        }
    }

    @Test
    void absentOrNonClosureTopLevelSlotIsRejected() throws Exception {
        try (ProtosStandaloneHostedSession session = open("value: 7\nvalue\n")) {
            assertInteger(7, session.initialOutcome());
            assertThrows(IllegalArgumentException.class, () -> session.invokeTopLevel("missing"));
            assertThrows(IllegalArgumentException.class, () -> session.invokeTopLevel("value"));
        }
    }

    @Test
    void closedSessionRejectsInvocationAndCloseIsIdempotent() throws Exception {
        ProtosStandaloneHostedSession session = open("run: () => {\n    1\n}\nrun()\n");
        session.close();
        session.close();
        assertThrows(IllegalStateException.class, () -> session.invokeTopLevel("run"));
    }

    private void assertRepeatedRun(String source, BigInteger expected) throws Exception {
        try (ProtosStandaloneHostedSession session = open(source)) {
            assertEquals(ProtosExecutionOutcome.State.COMPLETED, session.initialOutcome().state());
            assertEquals(
                    expected,
                    assertInstanceOf(ProtosIntegerValue.class, session.initialOutcome().value())
                            .value());
            for (int call = 0; call < REPEATS; call++) {
                ProtosExecutionOutcome outcome = session.invokeTopLevel("run");
                assertEquals(ProtosExecutionOutcome.State.COMPLETED, outcome.state());
                assertEquals(
                        expected,
                        assertInstanceOf(ProtosIntegerValue.class, outcome.value()).value());
            }
        }
    }

    private static void assertInteger(long expected, ProtosExecutionOutcome outcome) {
        assertEquals(ProtosExecutionOutcome.State.COMPLETED, outcome.state());
        assertEquals(
                BigInteger.valueOf(expected),
                assertInstanceOf(ProtosIntegerValue.class, outcome.value()).value());
    }

    private ProtosStandaloneHostedSession open(String source) throws Exception {
        Path file = directory.resolve("workload.protos");
        Files.writeString(file, source, StandardCharsets.UTF_8);
        return ProtosStandaloneHostedSession.open(CORE, file);
    }
}
