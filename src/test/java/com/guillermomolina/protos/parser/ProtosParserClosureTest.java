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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.parser.ast.SurfaceCall;
import com.guillermomolina.protos.parser.ast.SurfaceClosure;
import com.guillermomolina.protos.parser.ast.SurfaceExpression;
import com.guillermomolina.protos.parser.ast.SurfaceGroup;
import com.guillermomolina.protos.parser.ast.SurfaceName;
import com.guillermomolina.protos.parser.ast.SurfaceObject;
import com.guillermomolina.protos.parser.ast.SurfaceParameter;
import com.guillermomolina.protos.parser.ast.SurfaceSequence;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProtosParserClosureTest {
    @Test
    void parsesBareAndParenthesizedSingleParameterClosures() {
        SurfaceClosure bare = closure("x => x");
        assertEquals(1, bare.parameters().size());
        assertParameter("x", false, false, bare.parameters().get(0));
        assertTrue(bare.expressionBody());
        assertName("x", onlyBodyExpression(bare));

        SurfaceClosure parenthesized = closure("(x) => x");
        assertEquals(1, parenthesized.parameters().size());
        assertParameter("x", false, false, parenthesized.parameters().get(0));
    }

    @Test
    void parsesZeroMultipleDefaultAndRestParameters() {
        assertEquals(0, closure("() => value").parameters().size());

        SurfaceClosure closure = closure("(a, b = fallback, ...rest) => a");
        assertEquals(3, closure.parameters().size());
        assertParameter("a", false, false, closure.parameters().get(0));
        assertParameter("b", true, false, closure.parameters().get(1));
        assertName("fallback", closure.parameters().get(1).defaultValue().orElseThrow());
        assertParameter("rest", false, true, closure.parameters().get(2));
    }

    @Test
    void parsesMultilineParameterLayoutOnlyWhenCommasArePresent() {
        SurfaceClosure closure = closure("(\n  a,\n\n  b\n) => a");
        assertEquals(List.of("a", "b"),
                closure.parameters().stream().map(SurfaceParameter::name).toList());

        assertThrows(ParseError.class, () -> only("(\n a\n b\n) => a"));
        assertThrows(ParseError.class, () -> only("(a,\n) => a"));
    }

    @Test
    void rejectsDuplicateParametersAndNonFinalRest() {
        assertThrows(ParseError.class, () -> only("(a, a) => a"));
        assertThrows(ParseError.class, () -> only("(a, ...a) => a"));
        assertThrows(ParseError.class, () -> only("(...rest, other) => other"));
    }

    @Test
    void parsesExpressionAndBracedBodiesAsOneClosureKind() {
        SurfaceClosure expressionBody = closure("x => x + 1");
        assertTrue(expressionBody.expressionBody());
        assertEquals(1, expressionBody.body().expressions().size());

        SurfaceClosure bracedBody = closure("x => { x\n x + 1 }");
        assertFalse(bracedBody.expressionBody());
        assertEquals(2, bracedBody.body().expressions().size());
    }

    @Test
    void braceAfterArrowIsAlwaysBracedBodyNotObjectExpression() {
        SurfaceClosure braced = closure("x => { value: x }");
        assertFalse(braced.expressionBody());

        SurfaceClosure objectExpression = closure("x => ({ value: x })");
        assertTrue(objectExpression.expressionBody());
        SurfaceGroup group = assertInstanceOf(SurfaceGroup.class, onlyBodyExpression(objectExpression));
        assertInstanceOf(SurfaceObject.class, group.expression());
    }

    @Test
    void nestedExpressionBodiesAssociateToTheRight() {
        SurfaceClosure outer = closure("x => y => x + y");
        SurfaceClosure inner =
                assertInstanceOf(SurfaceClosure.class, onlyBodyExpression(outer));
        assertEquals("y", inner.parameters().get(0).name());
    }

    @Test
    void newlineAfterArrowContinuesButBareParameterDoesNotLookAcrossNewline() {
        SurfaceClosure closure = closure("x =>\n  x + 1");
        assertTrue(closure.expressionBody());

        assertThrows(ParseError.class, () -> only("x\n=> x + 1"));
    }

    @Test
    void groupedClosureCanParticipateInOrdinaryPostfixCall() {
        SurfaceCall call = assertInstanceOf(SurfaceCall.class, only("(x => x)(value)"));
        SurfaceGroup group = assertInstanceOf(SurfaceGroup.class, call.receiver());
        assertInstanceOf(SurfaceClosure.class, group.expression());
        assertEquals(1, call.arguments().size());
    }

    private SurfaceClosure closure(String source) {
        return assertInstanceOf(SurfaceClosure.class, only(source));
    }

    private SurfaceExpression onlyBodyExpression(SurfaceClosure closure) {
        assertEquals(1, closure.body().expressions().size());
        return closure.body().expressions().get(0);
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

    private void assertParameter(
            String name, boolean hasDefault, boolean rest, SurfaceParameter parameter) {
        assertEquals(name, parameter.name());
        assertEquals(hasDefault, parameter.defaultValue().isPresent());
        assertEquals(rest, parameter.rest());
    }
}
