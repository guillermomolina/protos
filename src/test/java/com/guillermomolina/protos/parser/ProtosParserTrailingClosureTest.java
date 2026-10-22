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
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.guillermomolina.protos.parser.ast.SurfaceArgument;
import com.guillermomolina.protos.parser.ast.SurfaceAssignment;
import com.guillermomolina.protos.parser.ast.SurfaceBinary;
import com.guillermomolina.protos.parser.ast.SurfaceCall;
import com.guillermomolina.protos.parser.ast.SurfaceClosure;
import com.guillermomolina.protos.parser.ast.SurfaceContextualIf;
import com.guillermomolina.protos.parser.ast.SurfaceContextualUntil;
import com.guillermomolina.protos.parser.ast.SurfaceContextualWhile;
import com.guillermomolina.protos.parser.ast.SurfaceExpression;
import com.guillermomolina.protos.parser.ast.SurfaceGroup;
import com.guillermomolina.protos.parser.ast.SurfaceLiteral;
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

    @Test
    void bareIfWithOneArgumentAndTrailingClosureIsSingleArmContextualIf() {
        String source = "if(a) { b }";
        SurfaceContextualIf conditional =
                assertInstanceOf(SurfaceContextualIf.class, only(source));

        SurfaceName condition = assertInstanceOf(SurfaceName.class, conditional.condition());
        assertEquals("a", condition.name());
        assertEquals(new SourceSpan(3, 4), condition.span());
        assertEquals(0, conditional.trueBranch().parameters().size());
        assertFalse(conditional.trueBranch().expressionBody());
        assertEquals(new SourceSpan(6, 11), conditional.trueBranch().span());
        assertNull(conditional.falseBranch());
        assertEquals(new SourceSpan(0, source.length()), conditional.span());
    }

    @Test
    void attachedElseFormsTwoArmContextualIf() {
        String source = "if(a) { b } else { c }";
        SurfaceContextualIf conditional =
                assertInstanceOf(SurfaceContextualIf.class, only(source));

        assertEquals(new SourceSpan(6, 11), conditional.trueBranch().span());
        SurfaceClosure falseBranch = conditional.falseBranch();
        assertEquals(0, falseBranch.parameters().size());
        assertFalse(falseBranch.expressionBody());
        assertEquals(
                "c",
                assertInstanceOf(
                                SurfaceName.class,
                                falseBranch.body().expressions().get(0))
                        .name());
        assertEquals(new SourceSpan(17, 22), falseBranch.span());
        assertEquals(new SourceSpan(0, source.length()), conditional.span());
    }

    @Test
    void contextualIfConditionIsAnArbitraryEagerExpression() {
        SurfaceContextualIf conditional = assertInstanceOf(
                SurfaceContextualIf.class, only("if(n > 1) { n }"));
        SurfaceBinary condition =
                assertInstanceOf(SurfaceBinary.class, conditional.condition());
        assertEquals(">", condition.operator());
    }

    @Test
    void ifWithoutTrailingClosureIsOrdinaryCall() {
        SurfaceCall call = assertInstanceOf(SurfaceCall.class, only("if(condition)"));
        assertEquals("if", assertInstanceOf(SurfaceName.class, call.receiver()).name());
        assertEquals(1, call.arguments().size());
    }

    @Test
    void ifWithTwoArgumentsAndTrailingClosureIsOrdinaryCall() {
        SurfaceCall call = assertInstanceOf(SurfaceCall.class, only("if(a, b) { body }"));
        assertEquals(3, call.arguments().size());
    }

    @Test
    void ifWithSpreadArgumentAndTrailingClosureIsOrdinaryCall() {
        SurfaceCall call =
                assertInstanceOf(SurfaceCall.class, only("if(...args) { body }"));
        assertEquals(2, call.arguments().size());
        assertEquals(true, call.arguments().get(0).spread());
    }

    @Test
    void groupedIfWithTrailingClosureIsOrdinaryCall() {
        SurfaceCall call =
                assertInstanceOf(SurfaceCall.class, only("(if)(condition) { body }"));
        assertInstanceOf(SurfaceGroup.class, call.receiver());
        assertEquals(2, call.arguments().size());
    }

    @Test
    void memberIfWithTrailingClosureIsOrdinarySendCall() {
        SurfaceCall call =
                assertInstanceOf(SurfaceCall.class, only("object.if(condition) { body }"));
        SurfaceMember member = assertInstanceOf(SurfaceMember.class, call.receiver());
        assertEquals("if", member.name());
        assertEquals(2, call.arguments().size());
    }

    @Test
    void standaloneElseWithBracesIsOrdinaryObjectLiteral() {
        SurfaceObject object = assertInstanceOf(SurfaceObject.class, only("else { body }"));
        assertEquals(
                "else",
                assertInstanceOf(SurfaceName.class, object.parent().orElseThrow()).name());
    }

    @Test
    void newlineBeforeTrueBranchDoesNotFormContextualIf() {
        SurfaceSequence program =
                new ProtosParser("if(condition)\n{ body }").parseProgram();

        assertEquals(2, program.expressions().size());
        SurfaceCall call = assertInstanceOf(SurfaceCall.class, program.expressions().get(0));
        assertEquals(1, call.arguments().size());
        assertInstanceOf(SurfaceObject.class, program.expressions().get(1));
    }

    @Test
    void newlineBeforeElseEndsTheSingleArmContextualIf() {
        SurfaceSequence program =
                new ProtosParser("if(a) { b }\nelse { c }").parseProgram();

        assertEquals(2, program.expressions().size());
        SurfaceContextualIf conditional = assertInstanceOf(
                SurfaceContextualIf.class, program.expressions().get(0));
        assertNull(conditional.falseBranch());
        assertEquals(new SourceSpan(0, 11), conditional.span());
        SurfaceObject object = assertInstanceOf(
                SurfaceObject.class, program.expressions().get(1));
        assertEquals(
                "else",
                assertInstanceOf(SurfaceName.class, object.parent().orElseThrow()).name());
    }

    @Test
    void lineCommentBeforeElsePreventsPairing() {
        SurfaceSequence program =
                new ProtosParser("if(a) { b } // note\nelse { c }").parseProgram();

        assertEquals(2, program.expressions().size());
        assertNull(assertInstanceOf(
                        SurfaceContextualIf.class, program.expressions().get(0))
                .falseBranch());
        assertInstanceOf(SurfaceObject.class, program.expressions().get(1));
    }

    @Test
    void sameLineBlockCommentsDoNotPreventPairing() {
        String source = "if /* a */ (x) /* b */ { y } /* c */ else /* d */ { z }";
        SurfaceContextualIf conditional =
                assertInstanceOf(SurfaceContextualIf.class, only(source));

        assertEquals(
                "x", assertInstanceOf(SurfaceName.class, conditional.condition()).name());
        assertEquals(new SourceSpan(23, 28), conditional.trueBranch().span());
        assertEquals(new SourceSpan(50, source.length()),
                conditional.falseBranch().span());
        assertEquals(new SourceSpan(0, source.length()), conditional.span());
    }

    @Test
    void elseWithoutAttachedBodyIsNotPaired() {
        assertThrows(ParseError.class, () -> new ProtosParser("if(a) { b } else\n{ c }").parseProgram());
    }

    @Test
    void nestedContextualIfPairsStructurally() {
        String source = "if(a) { if(b) { 1 } else { 2 } } else { 3 }";
        SurfaceContextualIf outer =
                assertInstanceOf(SurfaceContextualIf.class, only(source));
        SurfaceContextualIf inner = assertInstanceOf(
                SurfaceContextualIf.class,
                outer.trueBranch().body().expressions().get(0));
        assertEquals(new SourceSpan(8, 30), inner.span());
        assertEquals(new SourceSpan(25, 30), inner.falseBranch().span());
        assertEquals(new SourceSpan(38, source.length()), outer.falseBranch().span());
        assertEquals(new SourceSpan(0, source.length()), outer.span());
    }

    @Test
    void elseAfterNestedBranchBelongsToTheOuterIf() {
        SurfaceContextualIf outer = assertInstanceOf(
                SurfaceContextualIf.class, only("if(a) { if(b) { 1 } } else { 3 }"));
        SurfaceContextualIf inner = assertInstanceOf(
                SurfaceContextualIf.class,
                outer.trueBranch().body().expressions().get(0));
        assertNull(inner.falseBranch());
        SurfaceLiteral three = assertInstanceOf(
                SurfaceLiteral.class,
                outer.falseBranch().body().expressions().get(0));
        assertEquals(new SourceSpan(29, 30), three.span());
    }

    @Test
    void contextualIfIsAnExpressionInSlotCreationAndAssignment() {
        SurfaceSequence program = new ProtosParser(
                "result: if(flag) { 1 }\nresult = if(flag) { 2 } else { 3 }").parseProgram();

        SurfaceSlotCreation creation = assertInstanceOf(
                SurfaceSlotCreation.class, program.expressions().get(0));
        assertInstanceOf(SurfaceContextualIf.class, creation.value());
        SurfaceAssignment assignment = assertInstanceOf(
                SurfaceAssignment.class, program.expressions().get(1));
        SurfaceContextualIf assigned =
                assertInstanceOf(SurfaceContextualIf.class, assignment.value());
        assertInstanceOf(SurfaceClosure.class, assigned.falseBranch());
    }

    @Test
    void contextualIfIsAnOrdinaryCallArgument() {
        SurfaceSequence program =
                new ProtosParser("consume(if(flag) { 3 } else { 4 }, other)").parseProgram();

        SurfaceCall call = assertInstanceOf(
                SurfaceCall.class, program.expressions().get(0));
        assertEquals(2, call.arguments().size());
        assertInstanceOf(
                SurfaceContextualIf.class, call.arguments().get(0).expression());
    }

    @Test
    void contextualIfInsideGroupRemainsContextual() {
        SurfaceSequence program =
                new ProtosParser("(if(flag) { item }).name").parseProgram();

        SurfaceMember member = assertInstanceOf(
                SurfaceMember.class, program.expressions().get(0));
        SurfaceGroup group = assertInstanceOf(SurfaceGroup.class, member.receiver());
        assertInstanceOf(SurfaceContextualIf.class, group.expression());
    }

    @Test
    void contextualIfParticipatesInLaterPostfixSuffixes() {
        SurfaceSequence program =
                new ProtosParser("if(flag) { item }.name[0]").parseProgram();

        SurfaceIndex index = assertInstanceOf(
                SurfaceIndex.class, program.expressions().get(0));
        SurfaceMember member = assertInstanceOf(SurfaceMember.class, index.receiver());
        assertEquals("name", member.name());
        SurfaceContextualIf conditional =
                assertInstanceOf(SurfaceContextualIf.class, member.receiver());
        assertEquals(new SourceSpan(0, 17), conditional.span());
    }

    @Test
    void postfixSuffixesBindToTheCompleteTwoArmContextualIf() {
        SurfaceSequence program =
                new ProtosParser("if(a) { b } else { c }.name(1)").parseProgram();

        SurfaceCall call = assertInstanceOf(
                SurfaceCall.class, program.expressions().get(0));
        SurfaceMember member = assertInstanceOf(SurfaceMember.class, call.receiver());
        SurfaceContextualIf conditional =
                assertInstanceOf(SurfaceContextualIf.class, member.receiver());
        assertEquals(new SourceSpan(0, 22), conditional.span());
        assertInstanceOf(SurfaceClosure.class, conditional.falseBranch());
    }

    @Test
    void oneLineBracedBodyFollowedByUntilIsContextualUntil() {
        String source = "{ a } until(b)";
        SurfaceContextualUntil loop =
                assertInstanceOf(SurfaceContextualUntil.class, only(source));

        assertEquals(0, loop.body().parameters().size());
        assertFalse(loop.body().expressionBody());
        assertEquals(1, loop.body().body().expressions().size());
        assertEquals(new SourceSpan(0, 5), loop.body().span());
        assertEquals(new SourceSpan(6, 11), loop.untilSpan());
        assertEquals(
                "b",
                assertInstanceOf(SurfaceName.class, loop.condition()).name());
        assertEquals(new SourceSpan(12, 13), loop.condition().span());
        assertEquals(new SourceSpan(0, source.length()), loop.span());
    }

    @Test
    void multilineBodyWithSameLineSuffixIsContextualUntil() {
        SurfaceContextualUntil loop = assertInstanceOf(
                SurfaceContextualUntil.class,
                only("{\n    first\n    second; third\n} until(\n    done()\n)"));

        assertEquals(3, loop.body().body().expressions().size());
        assertInstanceOf(SurfaceCall.class, loop.condition());
    }

    @Test
    void emptyBodyFollowedByUntilIsContextualUntil() {
        SurfaceContextualUntil loop =
                assertInstanceOf(SurfaceContextualUntil.class, only("{} until(done)"));

        assertEquals(0, loop.body().body().expressions().size());
    }

    @Test
    void contextualUntilBodyMayContainNestedObjectConstruction() {
        SurfaceContextualUntil loop = assertInstanceOf(
                SurfaceContextualUntil.class,
                only("{ item = { x: 1 } } until(item.x)"));

        SurfaceAssignment assignment = assertInstanceOf(
                SurfaceAssignment.class, loop.body().body().expressions().get(0));
        assertInstanceOf(SurfaceObject.class, assignment.value());
    }

    @Test
    void contextualControlFormsNestInsideContextualUntilBody() {
        SurfaceContextualUntil outer = assertInstanceOf(
                SurfaceContextualUntil.class,
                only("{ while(a) { b }\nif(c) { d } else { e }\n{ f } until(g) } until(h)"));

        assertEquals(3, outer.body().body().expressions().size());
        assertInstanceOf(
                SurfaceContextualWhile.class, outer.body().body().expressions().get(0));
        assertInstanceOf(
                SurfaceContextualIf.class, outer.body().body().expressions().get(1));
        SurfaceContextualUntil inner = assertInstanceOf(
                SurfaceContextualUntil.class, outer.body().body().expressions().get(2));
        assertEquals("g", assertInstanceOf(SurfaceName.class, inner.condition()).name());
        assertEquals("h", assertInstanceOf(SurfaceName.class, outer.condition()).name());
    }

    @Test
    void nestedCommasInsideTheConditionDoNotCountAsSuffixArguments() {
        SurfaceContextualUntil loop = assertInstanceOf(
                SurfaceContextualUntil.class,
                only("{ a } until(check(x, [y, z], (p, q) => p))"));

        SurfaceCall check = assertInstanceOf(SurfaceCall.class, loop.condition());
        assertEquals(3, check.arguments().size());
    }

    @Test
    void bracedFormWithoutSuffixRemainsOrdinaryObject() {
        assertInstanceOf(SurfaceObject.class, only("{ a: 1 }"));
        assertInstanceOf(SurfaceObject.class, only("{ a }"));
    }

    @Test
    void compositionItemIsInvalidInContextualUntilBody() {
        assertThrows(ParseError.class, () -> only("{ ...base } until(done)"));
    }

    @Test
    void newlineBetweenBodyAndUntilDoesNotAttach() {
        SurfaceSequence program =
                new ProtosParser("{ a }\nuntil(b)").parseProgram();

        assertEquals(2, program.expressions().size());
        assertInstanceOf(SurfaceObject.class, program.expressions().get(0));
        SurfaceCall call = assertInstanceOf(SurfaceCall.class, program.expressions().get(1));
        assertEquals("until", assertInstanceOf(SurfaceName.class, call.receiver()).name());
    }

    @Test
    void lineCommentBeforeUntilPreventsTheContextualForm() {
        SurfaceSequence program =
                new ProtosParser("{ a } // note\nuntil(b)").parseProgram();

        assertEquals(2, program.expressions().size());
        assertInstanceOf(SurfaceObject.class, program.expressions().get(0));
    }

    @Test
    void sameLineBlockCommentsDoNotPreventTheContextualForm() {
        assertInstanceOf(
                SurfaceContextualUntil.class,
                only("{ a } /* x */ until /* y */ ( /* z */ b)"));
    }

    @Test
    void memberUntilAfterBracesIsOrdinaryObjectSend() {
        SurfaceCall call =
                assertInstanceOf(SurfaceCall.class, only("{ a }.until(b)"));
        SurfaceMember member = assertInstanceOf(SurfaceMember.class, call.receiver());
        assertEquals("until", member.name());
        assertInstanceOf(SurfaceObject.class, member.receiver());
    }

    @Test
    void groupedBracesFollowedByUntilAreNotContextual() {
        assertThrows(ParseError.class, () -> only("({ a }) until(b)"));
    }

    @Test
    void prefixedObjectFollowedByUntilIsNotContextual() {
        assertThrows(ParseError.class, () -> only("parent { a } until(b)"));
    }

    @Test
    void trailingClosureFollowedByUntilIsNotContextual() {
        assertThrows(ParseError.class, () -> only("foo() { a } until(b)"));
    }

    @Test
    void untilSuffixWithMissingExtraOrSpreadArgumentsIsNotContextual() {
        assertThrows(ParseError.class, () -> only("{ a } until()"));
        assertThrows(ParseError.class, () -> only("{ a } until(b, c)"));
        assertThrows(ParseError.class, () -> only("{ a } until(...args)"));
        assertThrows(ParseError.class, () -> only("{ a } until"));
    }

    @Test
    void prefixUntilWithTrailingClosureIsOrdinaryCall() {
        SurfaceCall call =
                assertInstanceOf(SurfaceCall.class, only("until(done) { work() }"));
        assertEquals("until", assertInstanceOf(SurfaceName.class, call.receiver()).name());
        assertEquals(2, call.arguments().size());
    }

    @Test
    void untilRemainsAnOrdinaryIdentifierOutsideTheContextualForm() {
        SurfaceSequence program =
                new ProtosParser("until: 1\nuntil = until + 1").parseProgram();

        SurfaceSlotCreation creation = assertInstanceOf(
                SurfaceSlotCreation.class, program.expressions().get(0));
        assertEquals(
                "until",
                assertInstanceOf(SurfaceName.class, creation.target()).name());
        assertInstanceOf(SurfaceAssignment.class, program.expressions().get(1));
    }

    @Test
    void contextualUntilIsAnExpressionInSlotCreationArgumentAndGroup() {
        SurfaceSequence program = new ProtosParser(
                "result: { a } until(b)\nresult = { a } until(b)\n"
                        + "foo({ a } until(b), other)\n({ a } until(b))").parseProgram();

        SurfaceSlotCreation creation = assertInstanceOf(
                SurfaceSlotCreation.class, program.expressions().get(0));
        assertInstanceOf(SurfaceContextualUntil.class, creation.value());
        SurfaceAssignment assignment = assertInstanceOf(
                SurfaceAssignment.class, program.expressions().get(1));
        assertInstanceOf(SurfaceContextualUntil.class, assignment.value());
        SurfaceCall call = assertInstanceOf(
                SurfaceCall.class, program.expressions().get(2));
        assertEquals(2, call.arguments().size());
        assertInstanceOf(
                SurfaceContextualUntil.class, call.arguments().get(0).expression());
        SurfaceGroup group = assertInstanceOf(
                SurfaceGroup.class, program.expressions().get(3));
        assertInstanceOf(SurfaceContextualUntil.class, group.expression());
    }

    @Test
    void contextualUntilParticipatesInLaterPostfixSuffixes() {
        SurfaceSequence program =
                new ProtosParser("{ a } until(b).name[0](1)").parseProgram();

        SurfaceCall call = assertInstanceOf(
                SurfaceCall.class, program.expressions().get(0));
        SurfaceIndex index = assertInstanceOf(SurfaceIndex.class, call.receiver());
        SurfaceMember member = assertInstanceOf(SurfaceMember.class, index.receiver());
        assertEquals("name", member.name());
        SurfaceContextualUntil loop =
                assertInstanceOf(SurfaceContextualUntil.class, member.receiver());
        assertEquals(new SourceSpan(0, 14), loop.span());
    }

    private SurfaceExpression only(String source) {
        SurfaceSequence program = new ProtosParser(source).parseProgram();
        assertEquals(1, program.expressions().size());
        return program.expressions().get(0);
    }
}
