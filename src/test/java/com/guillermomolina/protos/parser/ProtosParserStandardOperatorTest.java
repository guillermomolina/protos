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
package com.guillermomolina.protos.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.guillermomolina.protos.parser.ast.SurfaceBinary;
import com.guillermomolina.protos.parser.ast.SurfaceExpression;
import com.guillermomolina.protos.parser.ast.SurfaceName;
import com.guillermomolina.protos.parser.ast.SurfaceSequence;
import com.guillermomolina.protos.parser.ast.SurfaceUnary;
import org.junit.jupiter.api.Test;

class ProtosParserStandardOperatorTest {
    @Test
    void standardOperatorsFollowNormativePrecedence() {
        SurfaceExpression expression = only("a || b && c == d < e + f * -g");
        SurfaceBinary or = assertInstanceOf(SurfaceBinary.class, expression);
        assertEquals("||", or.operator());
        assertName("a", or.left());
        SurfaceBinary and = assertInstanceOf(SurfaceBinary.class, or.right());
        assertEquals("&&", and.operator());
        assertName("b", and.left());
        SurfaceBinary equality = assertInstanceOf(SurfaceBinary.class, and.right());
        assertEquals("==", equality.operator());
        assertName("c", equality.left());
        SurfaceBinary comparison = assertInstanceOf(SurfaceBinary.class, equality.right());
        assertEquals("<", comparison.operator());
        assertName("d", comparison.left());
        SurfaceBinary additive = assertInstanceOf(SurfaceBinary.class, comparison.right());
        assertEquals("+", additive.operator());
        assertName("e", additive.left());
        SurfaceBinary multiplicative = assertInstanceOf(SurfaceBinary.class, additive.right());
        assertEquals("*", multiplicative.operator());
        assertName("f", multiplicative.left());
        SurfaceUnary unary = assertInstanceOf(SurfaceUnary.class, multiplicative.right());
        assertEquals("-", unary.operator());
        assertName("g", unary.operand());
    }

    @Test
    void repeatedBinaryOperatorsAssociateLeft() {
        SurfaceBinary outer = assertInstanceOf(SurfaceBinary.class, only("a - b - c"));
        assertEquals("-", outer.operator());
        assertName("c", outer.right());
        SurfaceBinary inner = assertInstanceOf(SurfaceBinary.class, outer.left());
        assertEquals("-", inner.operator());
        assertName("a", inner.left());
        assertName("b", inner.right());
    }

    @Test
    void unaryOperatorsAssociateRecursively() {
        SurfaceUnary outer = assertInstanceOf(SurfaceUnary.class, only("!-value"));
        assertEquals("!", outer.operator());
        SurfaceUnary inner = assertInstanceOf(SurfaceUnary.class, outer.operand());
        assertEquals("-", inner.operator());
        assertName("value", inner.operand());
    }

    @Test
    void newlineContinuesAfterNecessarilyIncompleteStandardOperator() {
        SurfaceBinary expression = assertInstanceOf(SurfaceBinary.class, only("left +\nright *\nthird"));
        assertEquals("+", expression.operator());
        assertName("left", expression.left());
        SurfaceBinary right = assertInstanceOf(SurfaceBinary.class, expression.right());
        assertEquals("*", right.operator());
        assertName("right", right.left());
        assertName("third", right.right());
    }

    private SurfaceExpression only(String source) {
        SurfaceSequence program = new ProtosParser(source).parseProgram();
        assertEquals(1, program.expressions().size());
        return program.expressions().get(0);
    }

    private void assertName(String expected, SurfaceExpression expression) {
        SurfaceName name = assertInstanceOf(SurfaceName.class, expression);
        assertEquals(expected, name.name());
    }
}
