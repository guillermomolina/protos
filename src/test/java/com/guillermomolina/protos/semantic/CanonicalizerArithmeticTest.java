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

package com.guillermomolina.protos.semantic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.guillermomolina.protos.parser.ProtosParser;
import com.guillermomolina.protos.parser.ast.SurfaceExpression;
import com.guillermomolina.protos.parser.ast.SurfaceSequence;
import com.guillermomolina.protos.semantic.ast.CanonicalLookup;
import com.guillermomolina.protos.semantic.ast.CanonicalSend;
import org.junit.jupiter.api.Test;

class CanonicalizerArithmeticTest {
    private final Canonicalizer canonicalizer = new Canonicalizer();

    @Test
    void lowersAdditiveOperatorToOrdinaryMessageSend() {
        CanonicalSend send =
                assertInstanceOf(CanonicalSend.class, canonicalizeOnly("a + b"));

        assertEquals("+", send.message());
        assertEquals("a", assertInstanceOf(CanonicalLookup.class, send.receiver()).name());
        assertEquals(1, send.arguments().size());
        assertEquals(
                "b",
                assertInstanceOf(CanonicalLookup.class, send.arguments().get(0)).name());
    }

    @Test
    void lowersEveryMultiplicativeOperatorToItsSelector() {
        assertEquals("*", assertInstanceOf(CanonicalSend.class, canonicalizeOnly("a * b")).message());
        assertEquals("/", assertInstanceOf(CanonicalSend.class, canonicalizeOnly("a / b")).message());
        assertEquals("%", assertInstanceOf(CanonicalSend.class, canonicalizeOnly("a % b")).message());
    }

    @Test
    void preservesParserPrecedenceWhenLoweringArithmetic() {
        CanonicalSend addition =
                assertInstanceOf(CanonicalSend.class, canonicalizeOnly("a + b * c"));

        assertEquals("+", addition.message());
        CanonicalSend multiplication =
                assertInstanceOf(CanonicalSend.class, addition.arguments().get(0));

        assertEquals("*", multiplication.message());
        assertEquals(
                "b",
                assertInstanceOf(CanonicalLookup.class, multiplication.receiver()).name());
        assertEquals(
                "c",
                assertInstanceOf(CanonicalLookup.class, multiplication.arguments().get(0)).name());
    }

    @Test
    void preservesLeftAssociationWhenLoweringAdditiveOperators() {
        CanonicalSend outer =
                assertInstanceOf(CanonicalSend.class, canonicalizeOnly("a - b + c"));

        assertEquals("+", outer.message());
        CanonicalSend inner = assertInstanceOf(CanonicalSend.class, outer.receiver());
        assertEquals("-", inner.message());
        assertEquals("a", assertInstanceOf(CanonicalLookup.class, inner.receiver()).name());
        assertEquals(
                "b",
                assertInstanceOf(CanonicalLookup.class, inner.arguments().get(0)).name());
        assertEquals(
                "c",
                assertInstanceOf(CanonicalLookup.class, outer.arguments().get(0)).name());
    }

    private Object canonicalizeOnly(String source) {
        SurfaceExpression surface = only(source);
        return canonicalizer.canonicalize(surface);
    }

    private SurfaceExpression only(String source) {
        SurfaceSequence program = new ProtosParser(source).parseProgram();
        assertEquals(1, program.expressions().size());
        return program.expressions().get(0);
    }
}
