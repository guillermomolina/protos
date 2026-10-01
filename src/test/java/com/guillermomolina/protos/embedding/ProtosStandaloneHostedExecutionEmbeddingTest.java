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

import com.guillermomolina.protos.execution.ProtosExecutionOutcome;
import com.guillermomolina.protos.execution.ProtosStandaloneHostedExecution;
import com.guillermomolina.protos.runtime.ProtosIntegerValue;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Exercises the JVM embedding entry exactly as an external Java consumer would. */
final class ProtosStandaloneHostedExecutionEmbeddingTest {
    private static final Path CORE = Path.of("protos", "lib", "core");
    // PERF021 owns benchmark-scale inputs; this JUnit needs only proportional recursion.

    @TempDir Path directory;

    @Test
    void terminalExpressionOfRecursiveFibonacciIsReturned() throws Exception {
        assertTerminalInteger(
                """
                fibonacci: (n) => {
                    result: n

                    (n >= 2).ifTrue() {
                        result = fibonacci(n - 1) + fibonacci(n - 2)
                    }

                    result
                }

                fibonacci(10)
                """,
                new BigInteger("55"));
    }

    @Test
    void terminalExpressionOfRecursiveFactorialIsReturned() throws Exception {
        assertTerminalInteger(
                """
                factorial: (n) => {
                    result: 1

                    (n > 1).ifTrue() {
                        result = n * factorial(n - 1)
                    }

                    result
                }

                factorial(8)
                """,
                new BigInteger("40320"));
    }

    @Test
    void repeatedInvocationsAreIndependent() throws Exception {
        Path source = directory.resolve("value.protos");
        Files.writeString(source, "6 * 7\n", StandardCharsets.UTF_8);

        for (int invocation = 0; invocation < 3; invocation++) {
            ProtosExecutionOutcome outcome =
                    ProtosStandaloneHostedExecution.executeFile(CORE, source);
            assertEquals(ProtosExecutionOutcome.State.COMPLETED, outcome.state());
            assertEquals(
                    BigInteger.valueOf(42),
                    assertInstanceOf(ProtosIntegerValue.class, outcome.value()).value());
        }
    }

    private void assertTerminalInteger(String source, BigInteger expected) throws Exception {
        Path file = directory.resolve("workload.protos");
        Files.writeString(file, source, StandardCharsets.UTF_8);

        ProtosExecutionOutcome outcome = ProtosStandaloneHostedExecution.executeFile(CORE, file);

        assertEquals(ProtosExecutionOutcome.State.COMPLETED, outcome.state());
        assertEquals(
                expected, assertInstanceOf(ProtosIntegerValue.class, outcome.value()).value());
    }
}
