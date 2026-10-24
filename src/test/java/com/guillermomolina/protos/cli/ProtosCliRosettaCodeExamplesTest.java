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
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/**
 * Exact task-correctness regression for the shipped Rosetta Code catalog examples.
 *
 * <p>Each example is executed from its canonical .protos source. Callable functions are exercised
 * by appending expressions to the actual shipped source, so the standalone example output stays
 * unchanged while the task requirements are still verified.
 */
final class ProtosCliRosettaCodeExamplesTest {
    private static final Path EXAMPLES = Path.of("protos", "examples");
    private static final Duration PROGRAM_TIMEOUT = Duration.ofSeconds(15);

    @Test
    void helloWorldTextMatchesRosettaTask() {
        assertProgramOutput(runFile("basics/rosetta-hello-world-text.protos"), "Hello world!\n");
    }

    @Test
    void factorialCoversZeroUnitAndPositiveIntegers() {
        assertProgramOutput(
                runWithAppended(
                        "algorithms/factorial-recursive.protos",
                        """
                        print(factorial(0))
                        print(factorial(1))
                        print(factorial(5))
                        """),
                "3628800\n1\n1\n120\n");
    }

    @Test
    void fibonacciRecursiveAndIterativeAgreeOnKnownValues() {
        assertProgramOutput(
                runWithAppended(
                        "algorithms/fibonacci-recursive.protos",
                        """
                        print(fibonacci(0))
                        print(fibonacci(1))
                        print(fibonacci(2))
                        print(fibonacci(10))
                        print(fibonacciIterative(0))
                        print(fibonacciIterative(1))
                        print(fibonacciIterative(2))
                        print(fibonacciIterative(10))
                        print(fibonacciIterative(20))
                        """),
                "55\n0\n1\n1\n55\n0\n1\n1\n55\n6765\n");
    }

    @Test
    void fizzBuzzPrintsExactlyOneHundredCorrectLines() {
        var expected = new StringBuilder();
        for (int number = 1; number <= 100; number++) {
            if (number % 15 == 0) {
                expected.append("FizzBuzz");
            } else if (number % 3 == 0) {
                expected.append("Fizz");
            } else if (number % 5 == 0) {
                expected.append("Buzz");
            } else {
                expected.append(number);
            }
            expected.append('\n');
        }

        Result result = runFile("algorithms/fizzbuzz.protos");

        assertProgramOutput(result, expected.toString());
        assertEquals(100, result.stdout().lines().count());
    }

    @Test
    void greatestCommonDivisorCoversSignedAndZeroOperands() {
        assertProgramOutput(
                runWithAppended(
                        "algorithms/greatest-common-divisor.protos",
                        """
                        print(Integers.gcd(0, 0))
                        print(Integers.gcd(-54, 24))
                        print(Integers.gcd(0, -7))
                        """),
                "6\n0\n6\n7\n");
    }

    @Test
    void hundredDoorsMatchesFinalAndIntermediateStates() {
        String openDoors = "1\n4\n9\n16\n25\n36\n49\n64\n81\n100\n";

        assertProgramOutput(runFile("algorithms/100-doors.protos"), openDoors);
        assertProgramOutput(
                runWithAppended(
                        "algorithms/100-doors.protos",
                        """
                        print(doorsAfterPasses(0)[0])
                        print(doorsAfterPasses(1)[99])
                        print(doorsAfterPasses(2)[1])
                        print(doorsAfterPasses(100)[99])
                        print(doorsAfterPasses(100)[2])
                        """),
                openDoors + "false\ntrue\nfalse\ntrue\nfalse\n");
    }

    @Test
    void binarySearchCoversBoundsMissesAndDuplicates() {
        String standalone = "3\nnot found\n";

        assertProgramOutput(runFile("algorithms/binary-search.protos"), standalone);
        assertProgramOutput(
                runWithAppended(
                        "algorithms/binary-search.protos",
                        """
                        print(binarySearch([], 1, 0, -1) === null)
                        print(binarySearch([4], 4, 0, 0))
                        print(binarySearch([4], 5, 0, 0) === null)
                        print(binarySearch(values, 1, 0, 4))
                        print(binarySearch(values, 9, 0, 4))
                        print(binarySearch(values, 9, 0, 2) === null)
                        duplicates: [1, 2, 2, 2, 5]
                        position: binarySearch(duplicates, 2, 0, 4)
                        print(position >= 1 && position <= 3)
                        print(duplicates[position] == 2)
                        """),
                standalone + "true\n0\ntrue\n0\n4\ntrue\ntrue\ntrue\n");
    }

    @Test
    void bubbleSortSortsInPlaceAndReturnsSameArray() {
        String standalone = "1\n2\n4\n5\n5\n8\n";

        assertProgramOutput(runFile("algorithms/bubble-sort.protos"), standalone);
        assertProgramOutput(
                runWithAppended(
                        "algorithms/bubble-sort.protos",
                        """
                        print(bubbleSort([]).size())
                        print(bubbleSort([7])[0])
                        sorted: [1, 2, 3]
                        bubbleSort(sorted)
                        print(sorted[0] == 1 && sorted[1] == 2 && sorted[2] == 3)
                        reversed: [3, 2, 1]
                        returned: bubbleSort(reversed)
                        print(returned === reversed)
                        print(reversed[0] == 1 && reversed[1] == 2 && reversed[2] == 3)
                        repeated: [2, 1, 2, 1]
                        bubbleSort(repeated)
                        print(repeated[0] == 1 && repeated[1] == 1)
                        print(repeated[2] == 2 && repeated[3] == 2)
                        """),
                standalone + "0\n7\ntrue\ntrue\ntrue\ntrue\ntrue\n");
    }

    @Test
    void firstClassFunctionsComposeWithInversesToRecoverArgument() {
        String standalone = "9.0\n9.0\n9.0\n";

        assertProgramOutput(runFile("closures/first-class-functions.protos"), standalone);
        assertProgramOutput(
                runWithAppended(
                        "closures/first-class-functions.protos",
                        """
                        print(functions.size() == 3)
                        print(inverses.size() == 3)
                        builtIn: functions[0]
                        print(builtIn(16.0) == 4.0)
                        composed: compose(functions[1], inverses[1])
                        print(composed(9.0) == 9.0)
                        print(compose(functions[0], inverses[0])(9.0) == 9.0)
                        print(compose(functions[2], inverses[2])(9.0) == 9.0)
                        """),
                standalone + "true\ntrue\ntrue\ntrue\ntrue\ntrue\n");
    }

    @Test
    void valueCaptureClosuresObserveIndependentIndices() {
        assertProgramOutput(runFile("closures/value-capture.protos"), "9\n");
        assertProgramOutput(
                runWithAppended(
                        "closures/value-capture.protos",
                        """
                        print(functions.size())
                        functions.each((square) => {
                            print(square())
                        })
                        """),
                "9\n10\n0\n1\n4\n9\n16\n25\n36\n49\n64\n81\n");
    }

    @Test
    void delegatesFallBackUnlessDelegateRespondsToThing() {
        String standalone =
                "default implementation\ndefault implementation\n"
                        + "delegate implementation\ndelegate implementation\n";

        assertProgramOutput(runFile("objects/rosetta-delegates.protos"), standalone);
        assertProgramOutput(
                runWithAppended(
                        "objects/rosetta-delegates.protos",
                        """
                        print(respondsToThing({}))
                        owner: { thing: () => { "owned" } }
                        print(respondsToThing(owner))
                        inheriting: owner {}
                        print(respondsToThing(inheriting))
                        plain: { other: 1 }
                        print(respondsToThing(plain {}))
                        """),
                standalone + "false\ntrue\ntrue\nfalse\n");
    }

    private static void assertProgramOutput(Result result, String expectedStdout) {
        assertEquals(0, result.code(), result.stderr());
        assertEquals(expectedStdout, result.stdout());
        assertTrue(result.stderr().isBlank(), result.stderr());
    }

    private static Result runFile(String example) {
        return assertTimeoutPreemptively(
                PROGRAM_TIMEOUT, () -> run(EXAMPLES.resolve(example).toString()));
    }

    private static Result runWithAppended(String example, String expressions) {
        return assertTimeoutPreemptively(
                PROGRAM_TIMEOUT,
                () -> {
                    String source =
                            Files.readString(EXAMPLES.resolve(example), StandardCharsets.UTF_8);
                    return run("-e", source + "\n" + expressions);
                });
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
