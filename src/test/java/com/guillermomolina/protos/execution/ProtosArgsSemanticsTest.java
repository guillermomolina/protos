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
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.guillermomolina.protos.runtime.ProtosArrayValue;
import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosIntegerValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosStringValue;
import com.guillermomolina.protos.runtime.ProtosTestPrelude;
import java.math.BigInteger;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProtosArgsSemanticsTest {
    @Test
    void zeroArgumentsProduceFreshFrozenEmptyStandardArrays() {
        ProtosClosureValue closure = compileClosure("() => args");

        ProtosArrayValue first = asArgs(ProtosClosureInvoker.invoke(closure, List.of()));
        ProtosArrayValue second = asArgs(ProtosClosureInvoker.invoke(closure, List.of()));

        assertEquals(List.of(), first.indexedSnapshot());
        assertEquals(List.of(), second.indexedSnapshot());
        assertNotSame(first, second);
        assertSame(ProtosObjectValue.MutationState.FROZEN, first.mutationState());
        assertSame(first.parent().orElseThrow(), second.parent().orElseThrow());
    }

    @Test
    void oneArgumentPreservesExactElementIdentity() {
        ProtosClosureValue closure = compileClosure("(value) => args");
        ProtosObjectValue value = new ProtosObjectValue(ProtosObjectValue.rootObject());

        ProtosArrayValue result = asArgs(ProtosClosureInvoker.invoke(closure, List.of(value)));

        assertEquals(BigInteger.ONE, result.indexedSize());
        assertSame(value, result.indexedAt(BigInteger.ZERO));
    }

    @Test
    void multipleArgumentsPreserveOrderAndMixedSemanticFamilies() {
        ProtosClosureValue closure = compileClosure("(a, b, c, d) => args");
        ProtosIntegerValue integer = new ProtosIntegerValue(BigInteger.valueOf(7));
        ProtosStringValue string = new ProtosStringValue("x");
        ProtosObjectValue object = new ProtosObjectValue(ProtosObjectValue.rootObject());

        ProtosArrayValue result =
                asArgs(ProtosClosureInvoker.invoke(
                        closure,
                        List.of(integer, string, ProtosBooleanValue.TRUE, object)));

        assertEquals(4, result.indexedSnapshot().size());
        assertSame(integer, result.indexedAt(BigInteger.ZERO));
        assertSame(string, result.indexedAt(BigInteger.ONE));
        assertSame(ProtosBooleanValue.TRUE, result.indexedAt(BigInteger.TWO));
        assertSame(object, result.indexedAt(BigInteger.valueOf(3)));
    }

    @Test
    void nestedClosureObservesItsOwnInvocationArguments() {
        ProtosArrayValue result =
                executeArgs(
                        """
                        outer: (outerValue) => {
                            inner: (innerValue) => args
                            inner(outerValue)
                        }
                        outer(42)
                        """);

        assertEquals(BigInteger.ONE, result.indexedSize());
        assertEquals(
                BigInteger.valueOf(42),
                ((ProtosIntegerValue) result.indexedAt(BigInteger.ZERO)).value());
    }

    @Test
    void methodInvocationExcludesReceiverFromArgs() {
        ProtosArrayValue result =
                executeArgs(
                        """
                        holder: {
                            echo: (value) => args
                        }
                        holder.echo(9)
                        """);

        assertEquals(BigInteger.ONE, result.indexedSize());
        assertEquals(
                BigInteger.valueOf(9),
                ((ProtosIntegerValue) result.indexedAt(BigInteger.ZERO)).value());
    }

    @Test
    void polymorphicClosureCallUsesTheSameArgsMaterialization() {
        ProtosArrayValue result =
                executeArgs(
                        """
                        f: (a, b) => args
                        f(1, 2)
                        """);

        assertEquals(BigInteger.valueOf(2), result.indexedSize());
        assertEquals(
                BigInteger.ONE,
                ((ProtosIntegerValue) result.indexedAt(BigInteger.ZERO)).value());
        assertEquals(
                BigInteger.TWO,
                ((ProtosIntegerValue) result.indexedAt(BigInteger.ONE)).value());
    }

    @Test
    void nonLocalReturnCarriesNestedInvocationArgs() {
        ProtosArrayValue result =
                executeArgs(
                        """
                        outer: (value) => {
                            inner: (nested) => {
                                ^args
                            }
                            inner(value)
                            null
                        }
                        outer(11)
                        """);

        assertEquals(BigInteger.ONE, result.indexedSize());
        assertEquals(
                BigInteger.valueOf(11),
                ((ProtosIntegerValue) result.indexedAt(BigInteger.ZERO)).value());
    }

    @Test
    void argsSurvivesObjectConstructionInsideInvocation() {
        ProtosArrayValue result =
                executeArgs(
                        """
                        make: (value) => {
                            holder: {
                                seen: args
                            }
                            holder.seen
                        }
                        make(13)
                        """);

        assertEquals(BigInteger.ONE, result.indexedSize());
        assertEquals(
                BigInteger.valueOf(13),
                ((ProtosIntegerValue) result.indexedAt(BigInteger.ZERO)).value());
    }

    private ProtosClosureValue compileClosure(String source) {
        return (ProtosClosureValue) execute(source);
    }

    private ProtosArrayValue executeArgs(String source) {
        return asArgs(execute(source));
    }

    private Object execute(String source) {
        ProtosObjectValue root = ProtosObjectValue.rootObject();
        ProtosObjectValue context = new ProtosObjectValue(root);
        return new ProtosSourceCompiler()
                .compile(source)
                .call(ProtosTestPrelude.activation(context, List.of(), root));
    }

    private ProtosArrayValue asArgs(Object value) {
        return (ProtosArrayValue) value;
    }
}
