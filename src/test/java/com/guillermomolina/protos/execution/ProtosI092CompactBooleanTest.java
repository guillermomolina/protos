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

package com.guillermomolina.protos.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.guillermomolina.protos.runtime.ProtosIntegerValue;
import com.guillermomolina.protos.runtime.ProtosNullValue;
import java.math.BigInteger;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** I092 canonical Boolean direct path and ordinary fallback regressions. */
final class ProtosI092CompactBooleanTest {
    private static final Path CORE = Path.of("protos", "lib", "core");

    @Test
    void trueRunsSelectedInlineLiteral() throws Exception {
        assertInteger(42, evaluate("""
                run: () => {
                    true.ifTrue(() => { 42 })
                }
                run()
                """));
    }

    @Test
    void falseDoesNotInvokeBodyAndReturnsNull() throws Exception {
        assertSame(ProtosNullValue.INSTANCE, evaluate("""
                run: () => {
                    false.ifTrue(() => { ^99 })
                }
                run()
                """));
    }

    @Test
    void unselectedProducingExpressionIsEvaluatedOnce() throws Exception {
        assertInteger(1, evaluate("""
                run: () => {
                    count: 0
                    producer: () => {
                        count = count + 1
                        () => { 99 }
                    }
                    false.ifTrue(producer())
                    count
                }
                run()
                """));
    }

    @Test
    void ordinaryOverrideStaysOrdinary() throws Exception {
        assertInteger(77, evaluate("""
                run: () => {
                    custom: {
                        ifTrue: (callback) => { 77 }
                    }
                    custom.ifTrue(() => { 2 })
                }
                run()
                """));
    }

    @Test
    void captureAndNonLocalReturnArePreserved() throws Exception {
        assertInteger(42, evaluate("""
                run: () => {
                    count: 41
                    true.ifTrue(() => { count + 1 })
                }
                run()
                """));

        assertInteger(42, evaluate("""
                run: () => {
                    true.ifTrue(() => { ^41 })
                    99
                }
                run() + 1
                """));
    }

    private static Object evaluate(String source) throws Exception {
        ProtosExecutionOutcome outcome =
                ProtosTestExecutionSupport.execute(
                        "i092-compact-boolean.protos",
                        source,
                        new ProtosCoreBootstrap()
                                .bootstrap(CORE)
                                .newModuleActivation());

        assertEquals(
                ProtosExecutionOutcome.State.COMPLETED,
                outcome.state(),
                () -> "Outcome: " + outcome.state()
                        + " error=" + outcome.error());
        return outcome.value();
    }

    private static void assertInteger(long expected, Object value) {
        assertEquals(
                BigInteger.valueOf(expected),
                assertInstanceOf(ProtosIntegerValue.class, value).value());
    }
}
