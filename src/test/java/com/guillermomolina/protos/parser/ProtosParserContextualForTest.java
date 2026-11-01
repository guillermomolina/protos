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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.parser.ast.SurfaceAssignment;
import com.guillermomolina.protos.parser.ast.SurfaceCall;
import com.guillermomolina.protos.parser.ast.SurfaceContextualFor;
import com.guillermomolina.protos.parser.ast.SurfaceExpression;
import com.guillermomolina.protos.parser.ast.SurfaceIndex;
import com.guillermomolina.protos.parser.ast.SurfaceLiteral;
import com.guillermomolina.protos.parser.ast.SurfaceMember;
import com.guillermomolina.protos.parser.ast.SurfaceName;
import com.guillermomolina.protos.parser.ast.SurfaceObject;
import com.guillermomolina.protos.parser.ast.SurfaceParameter;
import com.guillermomolina.protos.parser.ast.SurfaceSequence;
import com.guillermomolina.protos.parser.ast.SurfaceSlotCreation;
import com.guillermomolina.protos.parser.ast.SurfaceUnary;
import com.guillermomolina.protos.source.SourceSpan;
import org.junit.jupiter.api.Test;

class ProtosParserContextualForTest {
    @Test
    void genericHeaderBuildsGenericContextualFor() {
        String source = "for(item in items) { body }";
        SurfaceContextualFor loop = assertInstanceOf(SurfaceContextualFor.class, only(source));

        assertEquals(SurfaceContextualFor.Kind.GENERIC, loop.kind());
        SurfaceName iterable = assertInstanceOf(SurfaceName.class, loop.iterable());
        assertEquals("items", iterable.name());
        assertEquals(new SourceSpan(12, 17), iterable.span());
        assertNull(loop.start());
        assertNull(loop.stop());
        assertNull(loop.step());
        assertEquals(new SourceSpan(19, source.length()), loop.body().span());
        assertEquals(new SourceSpan(0, source.length()), loop.span());
    }

    @Test
    void numericHeaderBuildsNumericContextualForWithoutStep() {
        String source = "for(i, 0, 5) { body }";
        SurfaceContextualFor loop = assertInstanceOf(SurfaceContextualFor.class, only(source));

        assertEquals(SurfaceContextualFor.Kind.NUMERIC, loop.kind());
        assertNull(loop.iterable());
        assertEquals(new SourceSpan(7, 8),
                assertInstanceOf(SurfaceLiteral.class, loop.start()).span());
        assertEquals(new SourceSpan(10, 11),
                assertInstanceOf(SurfaceLiteral.class, loop.stop()).span());
        assertNull(loop.step());
        assertEquals(new SourceSpan(13, source.length()), loop.body().span());
        assertEquals(new SourceSpan(0, source.length()), loop.span());
    }

    @Test
    void steppedNumericHeaderKeepsTheStepExpression() {
        SurfaceContextualFor loop = assertInstanceOf(
                SurfaceContextualFor.class, only("for(i, 10, 0, -2) { body }"));

        assertEquals(SurfaceContextualFor.Kind.NUMERIC, loop.kind());
        SurfaceUnary step = assertInstanceOf(SurfaceUnary.class, loop.step());
        assertEquals("-", step.operator());
    }

    @Test
    void binderIsTheSingleBodyParameterWithItsHeaderSpan() {
        SurfaceContextualFor generic = assertInstanceOf(
                SurfaceContextualFor.class, only("for(item in items) { item }"));
        assertEquals(1, generic.body().parameters().size());
        SurfaceParameter binder = generic.body().parameters().get(0);
        assertSame(binder, generic.binder());
        assertEquals("item", binder.name());
        assertEquals(new SourceSpan(4, 8), binder.span());
        assertTrue(binder.defaultValue().isEmpty());
        assertFalse(binder.rest());
        assertFalse(generic.body().expressionBody());
        assertTrue(binder.span().endOffset() <= generic.body().span().startOffset());

        SurfaceContextualFor numeric = assertInstanceOf(
                SurfaceContextualFor.class, only("for(i, a, b) { i }"));
        assertEquals(1, numeric.body().parameters().size());
        assertEquals("i", numeric.binder().name());
        assertEquals(new SourceSpan(4, 5), numeric.binder().span());
    }

    @Test
    void nestedDelimitersAndCommasBelongToHeaderExpressions() {
        SurfaceContextualFor generic = assertInstanceOf(
                SurfaceContextualFor.class,
                only("for(x in pick(a, [b, c], (p, q) => p)) { x }"));
        SurfaceCall pick = assertInstanceOf(SurfaceCall.class, generic.iterable());
        assertEquals(3, pick.arguments().size());

        SurfaceContextualFor numeric = assertInstanceOf(
                SurfaceContextualFor.class,
                only("for(i, low(a, b), [c, d].size(), { e: 1 }.e) { i }"));
        assertInstanceOf(SurfaceCall.class, numeric.start());
        assertInstanceOf(SurfaceCall.class, numeric.stop());
        assertInstanceOf(SurfaceMember.class, numeric.step());
    }

    @Test
    void multilineBodyAndNestedLoopsParse() {
        SurfaceContextualFor outer = assertInstanceOf(
                SurfaceContextualFor.class,
                only("for(row in rows) {\n    for(i, 0, row) {\n        i\n    }\n}"));
        SurfaceContextualFor inner = assertInstanceOf(
                SurfaceContextualFor.class, outer.body().body().expressions().get(0));
        assertEquals("i", inner.binder().name());
    }

    @Test
    void contextualForParticipatesInLaterPostfixSuffixes() {
        SurfaceIndex index = assertInstanceOf(
                SurfaceIndex.class, only("for(i, 0, 3) { i }.name[0]"));
        SurfaceMember member = assertInstanceOf(SurfaceMember.class, index.receiver());
        assertEquals("name", member.name());
        SurfaceContextualFor loop =
                assertInstanceOf(SurfaceContextualFor.class, member.receiver());
        assertEquals(new SourceSpan(0, 18), loop.span());
    }

    @Test
    void contextualForIsAnExpressionInSlotCreationAndAssignment() {
        SurfaceSequence program = new ProtosParser(
                "result: for(x in xs) { x }\nresult = for(i, 0, 1) { i }").parseProgram();

        SurfaceSlotCreation creation = assertInstanceOf(
                SurfaceSlotCreation.class, program.expressions().get(0));
        assertInstanceOf(SurfaceContextualFor.class, creation.value());
        SurfaceAssignment assignment = assertInstanceOf(
                SurfaceAssignment.class, program.expressions().get(1));
        assertInstanceOf(SurfaceContextualFor.class, assignment.value());
    }

    @Test
    void groupedAndMemberForStayOrdinary() {
        SurfaceCall grouped =
                assertInstanceOf(SurfaceCall.class, only("(for)(i, a, b) { body }"));
        assertEquals(4, grouped.arguments().size());
        SurfaceCall member =
                assertInstanceOf(SurfaceCall.class, only("holder.for(i, a, b) { body }"));
        assertEquals(4, member.arguments().size());

        assertThrows(ParseError.class, () -> only("(for)(x in xs) { x }"));
        assertThrows(ParseError.class, () -> only("holder.for(x in xs) { x }"));
    }

    @Test
    void numericHeaderWithoutBodyIsOrdinaryCall() {
        SurfaceCall call = assertInstanceOf(SurfaceCall.class, only("for(i, a, b)"));
        assertEquals("for", assertInstanceOf(SurfaceName.class, call.receiver()).name());
        assertEquals(3, call.arguments().size());
        assertInstanceOf(SurfaceName.class, call.arguments().get(0).expression());
    }

    @Test
    void numericHeaderWithBodyReinterpretsTheOrdinaryCallShape() {
        SurfaceContextualFor loop = assertInstanceOf(
                SurfaceContextualFor.class, only("for(i, a, b) { body }"));
        assertEquals("a", assertInstanceOf(SurfaceName.class, loop.start()).name());
        assertEquals("b", assertInstanceOf(SurfaceName.class, loop.stop()).name());
    }

    @Test
    void genericHeaderWithoutBodyIsAParseError() {
        assertThrows(ParseError.class, () -> only("for(x in xs)"));
        assertThrows(ParseError.class, () -> only("for(x in xs).name"));
    }

    @Test
    void newlineBeforeBodyPreventsTheContextualForm() {
        SurfaceSequence program =
                new ProtosParser("for(i, a, b)\n{ body }").parseProgram();
        assertEquals(2, program.expressions().size());
        SurfaceCall call = assertInstanceOf(SurfaceCall.class, program.expressions().get(0));
        assertEquals(3, call.arguments().size());
        assertInstanceOf(SurfaceObject.class, program.expressions().get(1));

        assertThrows(
                ParseError.class,
                () -> new ProtosParser("for(x in xs)\n{ body }").parseProgram());
        assertThrows(
                ParseError.class,
                () -> new ProtosParser("for(x in xs) // note\n{ body }").parseProgram());
    }

    @Test
    void sameLineBlockCommentsDoNotBreakTheAssociation() {
        String generic = "for /* a */ (item /* b */ in /* c */ items) /* d */ { item }";
        SurfaceContextualFor genericLoop =
                assertInstanceOf(SurfaceContextualFor.class, only(generic));
        assertEquals("item", genericLoop.binder().name());
        assertEquals("items",
                assertInstanceOf(SurfaceName.class, genericLoop.iterable()).name());
        assertEquals(new SourceSpan(0, generic.length()), genericLoop.span());

        assertInstanceOf(
                SurfaceContextualFor.class,
                only("for(i, /* a */ 0, /* b */ 3) /* c */ { i }"));
    }

    @Test
    void spreadArgumentsWrongCountsAndInvalidBindersStayOrdinaryCalls() {
        assertInstanceOf(SurfaceCall.class, only("for(...i, a, b) { body }"));
        assertInstanceOf(SurfaceCall.class, only("for(i, ...a, b) { body }"));
        assertInstanceOf(SurfaceCall.class, only("for(i, a, ...b) { body }"));
        assertInstanceOf(SurfaceCall.class, only("for(i, a) { body }"));
        assertInstanceOf(SurfaceCall.class, only("for(i, a, b, c, d) { body }"));
        assertInstanceOf(SurfaceCall.class, only("for(1, a, b) { body }"));
        assertInstanceOf(SurfaceCall.class, only("for(a.b, c, d) { body }"));
        assertInstanceOf(SurfaceCall.class, only("for((i), a, b) { body }"));

        assertThrows(ParseError.class, () -> only("for(1 in xs) { body }"));
        assertThrows(ParseError.class, () -> only("for(x in) { body }"));
        assertThrows(ParseError.class, () -> only("for(x in a, b) { body }"));
    }

    @Test
    void forAndInRemainOrdinaryIdentifiersOutsideTheContextualForm() {
        SurfaceSequence program =
                new ProtosParser("for: 1\nin: for\nfor = in + 1").parseProgram();
        SurfaceSlotCreation creation = assertInstanceOf(
                SurfaceSlotCreation.class, program.expressions().get(0));
        assertEquals("for", assertInstanceOf(SurfaceName.class, creation.target()).name());
        assertInstanceOf(SurfaceAssignment.class, program.expressions().get(2));
    }

    private SurfaceExpression only(String source) {
        SurfaceSequence program = new ProtosParser(source).parseProgram();
        assertEquals(1, program.expressions().size());
        return program.expressions().get(0);
    }
}
