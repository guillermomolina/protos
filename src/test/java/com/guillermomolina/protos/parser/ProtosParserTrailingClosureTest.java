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

import com.guillermomolina.protos.parser.ast.SurfaceArgument;
import com.guillermomolina.protos.parser.ast.SurfaceAssignment;
import com.guillermomolina.protos.parser.ast.SurfaceBinary;
import com.guillermomolina.protos.parser.ast.SurfaceCall;
import com.guillermomolina.protos.parser.ast.SurfaceClosure;
import com.guillermomolina.protos.parser.ast.SurfaceContextualWhile;
import com.guillermomolina.protos.parser.ast.SurfaceExpression;
import com.guillermomolina.protos.parser.ast.SurfaceGroup;
import com.guillermomolina.protos.parser.ast.SurfaceIndex;
import com.guillermomolina.protos.parser.ast.SurfaceMember;
import com.guillermomolina.protos.parser.ast.SurfaceName;
import com.guillermomolina.protos.parser.ast.SurfaceObject;
import com.guillermomolina.protos.parser.ast.SurfaceSequence;
import com.guillermomolina.protos.parser.ast.SurfaceSlotCreation;
import com.guillermomolina.protos.source.SourceSpan;
import org.junit.jupiter.api.Test;

class ProtosParserTrailingClosureTest {
    @Test
    void appendsParameterlessBracedClosureAsFinalCallArgument() {
        SurfaceCall call = assertInstanceOf(SurfaceCall.class, only("foo(a) { body }"));

        assertEquals(2, call.arguments().size());
        SurfaceArgument trailing = call.arguments().get(1);
        assertFalse(trailing.spread());

        SurfaceClosure closure =
                assertInstanceOf(SurfaceClosure.class, trailing.expression());
        assertEquals(0, closure.parameters().size());
        assertFalse(closure.expressionBody());
        assertEquals(1, closure.body().expressions().size());
    }

    @Test
    void parsesMultiexpressionTrailingClosureBody() {
        SurfaceCall call =
                assertInstanceOf(SurfaceCall.class, only("foo() { first\nsecond }"));
        SurfaceClosure closure =
                assertInstanceOf(SurfaceClosure.class, call.arguments().get(0).expression());

        assertEquals(2, closure.body().expressions().size());
    }

    @Test
    void newlineAfterCompletedCallDoesNotAttachTrailingClosure() {
        SurfaceSequence program = new ProtosParser("foo()\n{ body }").parseProgram();

        assertEquals(2, program.expressions().size());
        assertInstanceOf(SurfaceCall.class, program.expressions().get(0));
        assertInstanceOf(SurfaceObject.class, program.expressions().get(1));
    }

    @Test
    void atMostOneTrailingClosureCanAttachToACallSuffix() {
        assertThrows(ParseError.class, () -> only("foo() { first } { second }"));
    }

    @Test
    void trailingClosureRemainsUnavailableOnSuperMessageArgumentList() {
        assertThrows(ParseError.class, () -> only("super.foo() { body }"));
    }

    @Test
    void bareWhileWithOneArgumentAndTrailingClosureIsContextualWhile() {
        String source = "while(n > 1) { n = n - 1 }";
        SurfaceContextualWhile loop =
                assertInstanceOf(SurfaceContextualWhile.class, only(source));

        SurfaceBinary condition = assertInstanceOf(SurfaceBinary.class, loop.condition());
        assertEquals(">", condition.operator());
        assertEquals(new SourceSpan(6, 11), condition.span());
        assertEquals(0, loop.body().parameters().size());
        assertFalse(loop.body().expressionBody());
        assertEquals(1, loop.body().body().expressions().size());
        assertEquals(new SourceSpan(13, source.length()), loop.body().span());
        assertEquals(new SourceSpan(0, source.length()), loop.span());
    }

    @Test
    void whileWithoutTrailingClosureIsOrdinaryCall() {
        SurfaceCall call = assertInstanceOf(SurfaceCall.class, only("while(condition)"));
        assertEquals("while", assertInstanceOf(SurfaceName.class, call.receiver()).name());
        assertEquals(1, call.arguments().size());
    }

    @Test
    void whileWithTwoArgumentsAndTrailingClosureIsOrdinaryCall() {
        SurfaceCall call = assertInstanceOf(SurfaceCall.class, only("while(a, b) { body }"));
        assertEquals(3, call.arguments().size());
    }

    @Test
    void whileWithSpreadArgumentAndTrailingClosureIsOrdinaryCall() {
        SurfaceCall call =
                assertInstanceOf(SurfaceCall.class, only("while(...args) { body }"));
        assertEquals(2, call.arguments().size());
        assertEquals(true, call.arguments().get(0).spread());
    }

    @Test
    void groupedWhileWithTrailingClosureIsOrdinaryCall() {
        SurfaceCall call =
                assertInstanceOf(SurfaceCall.class, only("(while)(condition) { body }"));
        assertInstanceOf(SurfaceGroup.class, call.receiver());
        assertEquals(2, call.arguments().size());
    }

    @Test
    void memberWhileWithTrailingClosureIsOrdinarySendCall() {
        SurfaceCall call =
                assertInstanceOf(SurfaceCall.class, only("holder.while(condition) { body }"));
        SurfaceMember member = assertInstanceOf(SurfaceMember.class, call.receiver());
        assertEquals("while", member.name());
        assertEquals(2, call.arguments().size());
    }

    @Test
    void whileFollowedDirectlyByBracesIsOrdinaryObjectLiteral() {
        SurfaceObject object = assertInstanceOf(SurfaceObject.class, only("while { body }"));
        assertEquals(
                "while",
                assertInstanceOf(SurfaceName.class, object.parent().orElseThrow()).name());
    }

    @Test
    void newlineBeforeBodyDoesNotFormContextualWhile() {
        SurfaceSequence program =
                new ProtosParser("while(condition)\n{ body }").parseProgram();

        assertEquals(2, program.expressions().size());
        SurfaceCall call = assertInstanceOf(SurfaceCall.class, program.expressions().get(0));
        assertEquals(1, call.arguments().size());
        assertInstanceOf(SurfaceObject.class, program.expressions().get(1));
    }

    @Test
    void contextualWhileIsAnExpressionInSlotCreationAndAssignment() {
        SurfaceSequence program = new ProtosParser(
                "result: while(a) { b }\nresult = while(a) { b }").parseProgram();

        SurfaceSlotCreation creation = assertInstanceOf(
                SurfaceSlotCreation.class, program.expressions().get(0));
        assertInstanceOf(SurfaceContextualWhile.class, creation.value());
        SurfaceAssignment assignment = assertInstanceOf(
                SurfaceAssignment.class, program.expressions().get(1));
        assertInstanceOf(SurfaceContextualWhile.class, assignment.value());
    }

    @Test
    void contextualWhileIsAnOrdinaryCallArgument() {
        SurfaceSequence program =
                new ProtosParser("foo(while(a) { b }, other)").parseProgram();

        SurfaceCall call = assertInstanceOf(
                SurfaceCall.class, program.expressions().get(0));
        assertEquals(2, call.arguments().size());
        assertInstanceOf(
                SurfaceContextualWhile.class, call.arguments().get(0).expression());
    }

    @Test
    void contextualWhileInsideGroupRemainsContextual() {
        SurfaceSequence program =
                new ProtosParser("(while(a) { b })").parseProgram();

        SurfaceGroup group = assertInstanceOf(
                SurfaceGroup.class, program.expressions().get(0));
        assertInstanceOf(SurfaceContextualWhile.class, group.expression());
    }

    @Test
    void contextualWhileParticipatesInLaterPostfixSuffixes() {
        SurfaceSequence program =
                new ProtosParser("while(a) { b }.name[0]").parseProgram();

        SurfaceIndex index = assertInstanceOf(
                SurfaceIndex.class, program.expressions().get(0));
        SurfaceMember member = assertInstanceOf(SurfaceMember.class, index.receiver());
        assertEquals("name", member.name());
        SurfaceContextualWhile loop =
                assertInstanceOf(SurfaceContextualWhile.class, member.receiver());
        assertEquals(new SourceSpan(0, 14), loop.span());
    }

    @Test
    void contextualWhileNestsInsideItsBody() {
        SurfaceSequence program =
                new ProtosParser("while(a) { while(b) { c } }").parseProgram();

        SurfaceContextualWhile outer = assertInstanceOf(
                SurfaceContextualWhile.class, program.expressions().get(0));
        SurfaceContextualWhile inner = assertInstanceOf(
                SurfaceContextualWhile.class,
                outer.body().body().expressions().get(0));
        assertEquals(new SourceSpan(11, 25), inner.span());
    }

    private SurfaceExpression only(String source) {
        SurfaceSequence program = new ProtosParser(source).parseProgram();
        assertEquals(1, program.expressions().size());
        return program.expressions().get(0);
    }
}
