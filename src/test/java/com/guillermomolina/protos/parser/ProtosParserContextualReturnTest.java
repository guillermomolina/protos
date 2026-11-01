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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.parser.ast.SurfaceArrayConstruction;
import com.guillermomolina.protos.parser.ast.SurfaceAssignment;
import com.guillermomolina.protos.parser.ast.SurfaceBinary;
import com.guillermomolina.protos.parser.ast.SurfaceCall;
import com.guillermomolina.protos.parser.ast.SurfaceClosure;
import com.guillermomolina.protos.parser.ast.SurfaceExpression;
import com.guillermomolina.protos.parser.ast.SurfaceGroup;
import com.guillermomolina.protos.parser.ast.SurfaceIndex;
import com.guillermomolina.protos.parser.ast.SurfaceMapConstruction;
import com.guillermomolina.protos.parser.ast.SurfaceMember;
import com.guillermomolina.protos.parser.ast.SurfaceName;
import com.guillermomolina.protos.parser.ast.SurfaceNonLocalReturn;
import com.guillermomolina.protos.parser.ast.SurfaceObject;
import com.guillermomolina.protos.parser.ast.SurfaceSequence;
import com.guillermomolina.protos.parser.ast.SurfaceSlotCreation;
import com.guillermomolina.protos.parser.ast.SurfaceUnary;
import com.guillermomolina.protos.source.SourceSpan;
import java.util.List;
import org.junit.jupiter.api.Test;

/** I138 (D234-B2-S1): contextual {@code return expression} recognition. */
class ProtosParserContextualReturnTest {
    @Test
    void returnWithIdentifierIsNonLocalReturn() {
        SurfaceNonLocalReturn result = contextualReturn("return x");

        assertName("x", result.expression());
        assertEquals(new SourceSpan(0, 8), result.span());
    }

    @Test
    void returnScopeMatchesCaretScope() {
        SurfaceNonLocalReturn result = contextualReturn("return a + b * c");

        SurfaceBinary addition =
                assertInstanceOf(SurfaceBinary.class, result.expression());
        assertEquals("+", addition.operator());
        assertEquals(new SourceSpan(0, 16), result.span());
    }

    @Test
    void returnOfSingleParenthesizedExpressionIsContextual() {
        SurfaceNonLocalReturn result = contextualReturn("return(x)");
        SurfaceGroup group =
                assertInstanceOf(SurfaceGroup.class, result.expression());
        assertName("x", group.expression());

        SurfaceBinary scaled =
                assertInstanceOf(
                        SurfaceBinary.class,
                        contextualReturn("return (x) * 2").expression());
        assertInstanceOf(SurfaceGroup.class, scaled.left());
    }

    @Test
    void multilineParenthesizedOperandIsContextual() {
        SurfaceGroup group =
                assertInstanceOf(
                        SurfaceGroup.class,
                        contextualReturn("return (\n    calculate()\n)").expression());
        assertInstanceOf(SurfaceCall.class, group.expression());
    }

    @Test
    void unaryOperandsAreContextualIndependentOfSpacing() {
        for (String source : List.of("return -x", "return - x", "return ~x", "return ~ x")) {
            SurfaceUnary unary =
                    assertInstanceOf(
                            SurfaceUnary.class,
                            contextualReturn(source).expression(),
                            source);
            assertName("x", unary.operand());
        }
        assertInstanceOf(
                SurfaceUnary.class, contextualReturn("return !x").expression());
    }

    @Test
    void bracketAndBraceOperandsAreContextualIndependentOfSpacing() {
        for (String source : List.of("return[0]", "return [0]")) {
            assertInstanceOf(
                    SurfaceArrayConstruction.class,
                    contextualReturn(source).expression(),
                    source);
        }
        SurfaceObject object =
                assertInstanceOf(
                        SurfaceObject.class,
                        contextualReturn("return { a: 1 }").expression());
        assertTrue(object.parent().isEmpty());
    }

    @Test
    void literalsIntrinsicsAndNestedReturnsAreContextual() {
        for (String source : List.of(
                "return 1", "return \"s\"", "return null", "return true",
                "return this", "return context", "return ^x", "return return x")) {
            contextualReturn(source);
        }
    }

    @Test
    void closureOperandsAreContextual() {
        assertInstanceOf(
                SurfaceClosure.class, contextualReturn("return x => x").expression());
        assertInstanceOf(
                SurfaceClosure.class,
                contextualReturn("return (a, b) => a + b").expression());
        assertInstanceOf(
                SurfaceClosure.class, contextualReturn("return () => 1").expression());
    }

    @Test
    void blockCommentDoesNotPreventRecognition() {
        assertName("x", contextualReturn("return /* note */ x").expression());
    }

    @Test
    void returnInsideNestedClosureAndArgumentIsContextual() {
        SurfaceClosure closure =
                assertInstanceOf(SurfaceClosure.class, only("() => { return 1 }"));
        assertInstanceOf(
                SurfaceNonLocalReturn.class, closure.body().expressions().get(0));

        SurfaceCall call = assertInstanceOf(SurfaceCall.class, only("f(return 1)"));
        SurfaceNonLocalReturn argument =
                assertInstanceOf(
                        SurfaceNonLocalReturn.class,
                        call.arguments().get(0).expression());
        assertEquals(SurfaceNonLocalReturn.Spelling.RETURN, argument.spelling());
    }

    @Test
    void newlineAfterBareReturnKeepsTwoOrdinaryExpressions() {
        SurfaceSequence program = parse("return\ncalculate()");

        assertEquals(2, program.expressions().size());
        assertName("return", program.expressions().get(0));
        assertInstanceOf(SurfaceCall.class, program.expressions().get(1));
    }

    @Test
    void lineCommentTerminatesBareReturn() {
        SurfaceSequence program = parse("return // note\nx");

        assertEquals(2, program.expressions().size());
        assertName("return", program.expressions().get(0));
    }

    @Test
    void bareReturnAndCreationAssignmentArrowStayOrdinary() {
        assertName("return", only("return"));
        assertName(
                "return",
                assertInstanceOf(SurfaceSlotCreation.class, only("return: x")).target());
        assertName(
                "return",
                assertInstanceOf(SurfaceAssignment.class, only("return = x")).target());
        assertInstanceOf(SurfaceClosure.class, only("return => x"));
        assertName("return", ((SurfaceBinary) only("return * 2")).left());
    }

    @Test
    void emptyMultipleAndSpreadCallsStayOrdinary() {
        for (String source : List.of("return()", "return(x, y)", "return(...xs)")) {
            SurfaceCall call = assertInstanceOf(SurfaceCall.class, only(source), source);
            assertName("return", call.receiver());
        }
    }

    @Test
    void memberAndGroupedReturnStayOrdinary() {
        SurfaceCall member = assertInstanceOf(SurfaceCall.class, only("object.return(x)"));
        assertEquals(
                "return",
                assertInstanceOf(SurfaceMember.class, member.receiver()).name());

        SurfaceCall grouped = assertInstanceOf(SurfaceCall.class, only("(return)(x)"));
        assertInstanceOf(SurfaceGroup.class, grouped.receiver());

        assertInstanceOf(SurfaceBinary.class, only("(return) - x"));
        assertInstanceOf(SurfaceBinary.class, only("(return) ~ x"));
        assertInstanceOf(SurfaceIndex.class, only("(return)[0]"));
        assertInstanceOf(SurfaceObject.class, only("(return) { a: 1 }"));
    }

    @Test
    void completeTargetsRootedAtReturnStayOrdinary() {
        SurfaceAssignment indexed =
                assertInstanceOf(SurfaceAssignment.class, only("return[0] = x"));
        assertName(
                "return",
                assertInstanceOf(SurfaceIndex.class, indexed.target()).receiver());

        SurfaceAssignment member =
                assertInstanceOf(SurfaceAssignment.class, only("return[0].name = x"));
        assertInstanceOf(SurfaceMember.class, member.target());

        SurfaceSlotCreation creation =
                assertInstanceOf(SurfaceSlotCreation.class, only("return[0].name: x"));
        assertInstanceOf(SurfaceMember.class, creation.target());

        assertInstanceOf(
                SurfaceAssignment.class, only("return [0]\n    .name = x"));
        assertInstanceOf(SurfaceSlotCreation.class, only("return(x).name: y"));
    }

    @Test
    void completeTargetsAfterParenthesizedOperandStayOrdinary() {
        SurfaceSlotCreation memberCreation =
                assertInstanceOf(SurfaceSlotCreation.class, only("return(x).name: y"));
        SurfaceMember createdMember =
                assertInstanceOf(SurfaceMember.class, memberCreation.target());
        SurfaceCall createdCall =
                assertInstanceOf(SurfaceCall.class, createdMember.receiver());
        assertName("return", createdCall.receiver());

        SurfaceAssignment memberAssignment =
                assertInstanceOf(SurfaceAssignment.class, only("return(x).name = y"));
        assertInstanceOf(SurfaceMember.class, memberAssignment.target());

        SurfaceAssignment indexAssignment =
                assertInstanceOf(SurfaceAssignment.class, only("return(x)[0] = y"));
        SurfaceIndex indexed =
                assertInstanceOf(SurfaceIndex.class, indexAssignment.target());
        assertName(
                "return",
                assertInstanceOf(SurfaceCall.class, indexed.receiver()).receiver());

        for (String source : List.of(
                "return[0][1] = y",
                "return[0].a[1] = y",
                "return[0].a.b = y",
                "return[f(1, 2)].name = y",
                "return[0].a.b: y")) {
            SurfaceExpression expression = only(source);
            assertTrue(
                    expression instanceof SurfaceAssignment
                            || expression instanceof SurfaceSlotCreation,
                    source);
        }
    }

    @Test
    void parenthesizedOperandWithoutMutationStaysContextual() {
        assertInstanceOf(SurfaceGroup.class, contextualReturn("return(x)").expression());
        SurfaceMember member =
                assertInstanceOf(
                        SurfaceMember.class,
                        contextualReturn("return(x).name").expression());
        assertInstanceOf(SurfaceGroup.class, member.receiver());
        assertInstanceOf(
                SurfaceIndex.class, contextualReturn("return(x)[0]").expression());
        SurfaceBinary equality =
                assertInstanceOf(
                        SurfaceBinary.class,
                        contextualReturn("return(x).name == y").expression());
        assertEquals("==", equality.operator());
    }

    @Test
    void groupedEscapesKeepOrdinaryMeaning() {
        SurfaceCall call = assertInstanceOf(SurfaceCall.class, only("(return)(x)"));
        assertName(
                "return",
                assertInstanceOf(SurfaceGroup.class, call.receiver()).expression());
        SurfaceIndex index = assertInstanceOf(SurfaceIndex.class, only("(return)[0]"));
        assertInstanceOf(SurfaceGroup.class, index.receiver());
        SurfaceBinary xor = assertInstanceOf(SurfaceBinary.class, only("(return) ~ x"));
        assertEquals("~", xor.operator());
        assertInstanceOf(SurfaceGroup.class, xor.left());
    }

    @Test
    void percentAfterReturnIsNotAnOperandStart() {
        SurfaceBinary modulo = assertInstanceOf(SurfaceBinary.class, only("return % x"));
        assertEquals("%", modulo.operator());
        assertName("return", modulo.left());

        SurfaceBinary mapLike =
                assertInstanceOf(SurfaceBinary.class, only("return %{ a: 1 }"));
        assertEquals("%", mapLike.operator());
        assertName("return", mapLike.left());
        assertInstanceOf(SurfaceObject.class, mapLike.right());

        SurfaceGroup group =
                assertInstanceOf(
                        SurfaceGroup.class,
                        contextualReturn("return (%{\"a\": 1})").expression());
        assertInstanceOf(SurfaceMapConstruction.class, group.expression());
    }

    @Test
    void comparisonAfterIndexedOperandIsStillContextual() {
        SurfaceBinary comparison =
                assertInstanceOf(
                        SurfaceBinary.class,
                        contextualReturn("return[0] == x").expression());
        assertEquals("==", comparison.operator());
    }

    @Test
    void recognitionIsStructuralNotLexical() {
        assertInstanceOf(SurfaceSlotCreation.class, only("x: return"));
        SurfaceSlotCreation creation =
                assertInstanceOf(SurfaceSlotCreation.class, only("x: return y"));
        assertInstanceOf(SurfaceNonLocalReturn.class, creation.value());
        assertThrows(ParseError.class, () -> only("a + return x"));
    }

    @Test
    void caretSpellingIsPreserved() {
        SurfaceNonLocalReturn caret =
                assertInstanceOf(SurfaceNonLocalReturn.class, only("^x"));
        assertEquals(SurfaceNonLocalReturn.Spelling.CARET, caret.spelling());
    }

    private SurfaceNonLocalReturn contextualReturn(String source) {
        SurfaceNonLocalReturn result =
                assertInstanceOf(SurfaceNonLocalReturn.class, only(source), source);
        assertEquals(SurfaceNonLocalReturn.Spelling.RETURN, result.spelling(), source);
        assertEquals(0, result.span().startOffset(), source);
        return result;
    }

    private SurfaceExpression only(String source) {
        SurfaceSequence program = parse(source);
        assertEquals(1, program.expressions().size(), source);
        return program.expressions().get(0);
    }

    private SurfaceSequence parse(String source) {
        return new ProtosParser(source).parseProgram();
    }

    private void assertName(String expected, SurfaceExpression expression) {
        SurfaceName name = assertInstanceOf(SurfaceName.class, expression);
        assertEquals(expected, name.name());
    }
}
