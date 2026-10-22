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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.lexer.ProtosLexer;
import com.guillermomolina.protos.lexer.TokenOccurrence;
import com.guillermomolina.protos.lexer.TokenType;
import com.guillermomolina.protos.parser.ProtosParserSourceFacts.SequenceContext;
import com.guillermomolina.protos.parser.ProtosParserSourceFacts.SequenceSeparatorKind;
import com.guillermomolina.protos.parser.ProtosParserSourceFacts.SingleParameterClosureForm;
import com.guillermomolina.protos.parser.ast.SurfaceCall;
import com.guillermomolina.protos.parser.ast.SurfaceContextualIf;
import com.guillermomolina.protos.parser.ast.SurfaceContextualUntil;
import com.guillermomolina.protos.parser.ast.SurfaceContextualWhile;
import com.guillermomolina.protos.parser.ast.SurfaceObject;
import com.guillermomolina.protos.parser.ast.SurfaceSequence;
import com.guillermomolina.protos.source.SourceSpan;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProtosParserSourceFactsTest {
    @Test
    void recordsProgramSemicolonAndLogicalNewlineSeparators() {
        Parsed parsed = parseWithFacts("a; b\nc");

        assertEquals(3, parsed.program().expressions().size());
        assertEquals(
                List.of(
                        SequenceSeparatorKind.SEMICOLON,
                        SequenceSeparatorKind.LOGICAL_NEWLINE),
                parsed.facts().sequenceSeparators().stream()
                        .map(ProtosParserSourceFacts.SequenceSeparatorFact::kind)
                        .toList());
        assertTrue(
                parsed.facts().sequenceSeparators().stream()
                        .allMatch(
                                fact ->
                                        fact.context()
                                                == SequenceContext.PROGRAM));
    }

    @Test
    void recordsObjectAndMapSeparatorKindsWithoutChangingTheirAst() {
        Parsed objectParsed =
                parseWithFacts("{ a: 1; b: 2\nc: 3 }");

        assertEquals(
                List.of(
                        SequenceSeparatorKind.SEMICOLON,
                        SequenceSeparatorKind.LOGICAL_NEWLINE),
                objectParsed.facts().sequenceSeparators().stream()
                        .map(ProtosParserSourceFacts.SequenceSeparatorFact::kind)
                        .toList());

        Parsed mapParsed =
                parseWithFacts("%{ \"a\": 1; \"b\": 2\n\"c\": 3 }");

        assertEquals(
                List.of(
                        SequenceSeparatorKind.SEMICOLON,
                        SequenceSeparatorKind.LOGICAL_NEWLINE),
                mapParsed.facts().sequenceSeparators().stream()
                        .map(ProtosParserSourceFacts.SequenceSeparatorFact::kind)
                        .toList());
    }

    @Test
    void continuationNewlineIsNotASequenceSeparator() {
        Parsed parsed = parseWithFacts("a: 1 +\n  2");

        assertEquals(1, parsed.program().expressions().size());
        assertEquals(List.of(), parsed.facts().sequenceSeparators());
    }

    @Test
    void preservesBareAndParenthesizedSingleParameterClosureForms() {
        Parsed parsed =
                parseWithFacts("x => x\n(x) => x");

        assertEquals(
                List.of(
                        SingleParameterClosureForm.BARE,
                        SingleParameterClosureForm.PARENTHESIZED),
                parsed.facts().singleParameterClosureForms().stream()
                        .map(
                                ProtosParserSourceFacts
                                        .SingleParameterClosureSourceForm::form)
                        .toList());
    }

    @Test
    void blockCommentNewlinesDoNotBreakTrailingClosureOrigin() {
        String source = "foo() /* one\n two */ { body() }";

        List<ProtosLexer.TriviaOccurrence> trivia =
                new ArrayList<>();
        List<TokenOccurrence> tokens =
                new ProtosLexer(source)
                        .tokenizeOccurrencesWithTrivia(trivia::add);

        ProtosParserSourceFacts.Builder facts =
                new ProtosParserSourceFacts.Builder();
        SurfaceSequence program =
                ProtosParser.forTooling(tokens, facts).parseProgram();
        ProtosParserSourceFacts built = facts.build();

        SurfaceCall call =
                assertInstanceOf(
                        SurfaceCall.class,
                        program.expressions().get(0));

        assertEquals(1, built.trailingClosureOrigins().size());
        assertEquals(
                call,
                built.trailingClosureOrigins().get(0).owner());

        assertEquals(
                0L,
                tokens.stream()
                        .filter(
                                token ->
                                        token.token().type()
                                                == TokenType.NEWLINE)
                        .count());
    }

    @Test
    void contextualWhileRecordsTrailingClosureOriginOnItsRealNode() {
        String source = "while(n > 1) { n = n - 1 }\nfoo() { body }";
        Parsed parsed = parseWithFacts(source);

        SurfaceContextualWhile loop = assertInstanceOf(
                SurfaceContextualWhile.class,
                parsed.program().expressions().get(0));
        assertEquals(new SourceSpan(0, 26), loop.span());
        assertEquals(new SourceSpan(6, 11), loop.condition().span());
        assertEquals(new SourceSpan(13, 26), loop.body().span());

        SurfaceCall call = assertInstanceOf(
                SurfaceCall.class,
                parsed.program().expressions().get(1));
        List<ProtosParserSourceFacts.TrailingClosureOrigin> origins =
                parsed.facts().trailingClosureOrigins();
        assertEquals(2, origins.size());
        assertSame(loop, origins.get(0).owner());
        assertSame(loop.body(), origins.get(0).closure());
        assertSame(call, origins.get(1).owner());
        assertSame(call.arguments().get(0).expression(), origins.get(1).closure());
    }

    @Test
    void blockCommentNewlinesDoNotBreakContextualWhile() {
        String source = "while(c) /* one\n two */ { body() }";

        List<ProtosLexer.TriviaOccurrence> trivia =
                new ArrayList<>();
        List<TokenOccurrence> tokens =
                new ProtosLexer(source)
                        .tokenizeOccurrencesWithTrivia(trivia::add);

        ProtosParserSourceFacts.Builder facts =
                new ProtosParserSourceFacts.Builder();
        SurfaceSequence program =
                ProtosParser.forTooling(tokens, facts).parseProgram();

        assertEquals(1, program.expressions().size());
        SurfaceContextualWhile loop = assertInstanceOf(
                SurfaceContextualWhile.class,
                program.expressions().get(0));
        assertEquals(new SourceSpan(0, source.length()), loop.span());
        List<ProtosParserSourceFacts.TrailingClosureOrigin> origins =
                facts.build().trailingClosureOrigins();
        assertEquals(1, origins.size());
        assertSame(loop, origins.get(0).owner());
        assertSame(loop.body(), origins.get(0).closure());
    }

    @Test
    void lineCommentNewlineDetachesContextualWhileBody() {
        String source = "while(c) // note\n{ body }";

        List<ProtosLexer.TriviaOccurrence> trivia =
                new ArrayList<>();
        List<TokenOccurrence> tokens =
                new ProtosLexer(source)
                        .tokenizeOccurrencesWithTrivia(trivia::add);

        ProtosParserSourceFacts.Builder facts =
                new ProtosParserSourceFacts.Builder();
        SurfaceSequence program =
                ProtosParser.forTooling(tokens, facts).parseProgram();

        assertEquals(2, program.expressions().size());
        SurfaceCall call = assertInstanceOf(
                SurfaceCall.class,
                program.expressions().get(0));
        assertEquals(1, call.arguments().size());
        assertInstanceOf(SurfaceObject.class, program.expressions().get(1));
        assertEquals(List.of(), facts.build().trailingClosureOrigins());
    }

    @Test
    void blockCommentBoundaryOfContextualWhileOwnsTheRealNodes() {
        String source = "while(n > 0) /* comment */ {\n    n = n - 1\n}";
        Parsed parsed = parseWithTrivia(source);

        SurfaceContextualWhile loop = assertInstanceOf(
                SurfaceContextualWhile.class,
                parsed.program().expressions().get(0));
        List<ProtosParserSourceFacts.TrailingClosureOrigin> origins =
                parsed.facts().trailingClosureOrigins();
        assertEquals(1, origins.size());
        assertSame(loop, origins.get(0).owner());
        assertSame(loop.body(), origins.get(0).closure());
    }

    @Test
    void blockCommentBoundaryOfOrdinaryCallKeepsCallOwner() {
        String source = "foo(n) /* comment */ { body }";
        Parsed parsed = parseWithTrivia(source);

        SurfaceCall call = assertInstanceOf(
                SurfaceCall.class,
                parsed.program().expressions().get(0));
        assertEquals(2, call.arguments().size());
        List<ProtosParserSourceFacts.TrailingClosureOrigin> origins =
                parsed.facts().trailingClosureOrigins();
        assertEquals(1, origins.size());
        assertSame(call, origins.get(0).owner());
        assertSame(call.arguments().get(1).expression(), origins.get(0).closure());
    }

    @Test
    void singleArmContextualIfRecordsOnlyItsTrueBranchOrigin() {
        String source = "if(a) { b }";
        Parsed parsed = parseWithFacts(source);

        SurfaceContextualIf conditional = assertInstanceOf(
                SurfaceContextualIf.class,
                parsed.program().expressions().get(0));
        List<ProtosParserSourceFacts.TrailingClosureOrigin> origins =
                parsed.facts().trailingClosureOrigins();
        assertEquals(1, origins.size());
        assertSame(conditional, origins.get(0).owner());
        assertSame(conditional.trueBranch(), origins.get(0).closure());
        assertEquals(List.of(), parsed.facts().contextualElseOrigins());
    }

    @Test
    void twoArmContextualIfRecordsTrueOriginAndContextualElse() {
        String source = "if(a) { b } else { c }";
        Parsed parsed = parseWithFacts(source);

        SurfaceContextualIf conditional = assertInstanceOf(
                SurfaceContextualIf.class,
                parsed.program().expressions().get(0));
        List<ProtosParserSourceFacts.TrailingClosureOrigin> origins =
                parsed.facts().trailingClosureOrigins();
        assertEquals(1, origins.size());
        assertSame(conditional, origins.get(0).owner());
        assertSame(conditional.trueBranch(), origins.get(0).closure());

        List<ProtosParserSourceFacts.ContextualElseOrigin> elses =
                parsed.facts().contextualElseOrigins();
        assertEquals(1, elses.size());
        assertSame(conditional, elses.get(0).owner());
        assertSame(conditional.falseBranch(), elses.get(0).falseBranch());
        assertEquals(new SourceSpan(12, 16), elses.get(0).elseSpan());
    }

    @Test
    void blockCommentsAroundElseKeepTheExactElseSpan() {
        String source = "if(a) { b } /* one\n two */ else /* three */ { c }";
        Parsed parsed = parseWithTrivia(source);

        SurfaceContextualIf conditional = assertInstanceOf(
                SurfaceContextualIf.class,
                parsed.program().expressions().get(0));
        List<ProtosParserSourceFacts.ContextualElseOrigin> elses =
                parsed.facts().contextualElseOrigins();
        assertEquals(1, elses.size());
        assertSame(conditional, elses.get(0).owner());
        int elseStart = source.indexOf("else");
        assertEquals(new SourceSpan(elseStart, elseStart + 4), elses.get(0).elseSpan());
    }

    @Test
    void lineCommentBeforeElseRecordsNoContextualElse() {
        String source = "if(a) { b } // note\nelse { c }";
        Parsed parsed = parseWithTrivia(source);

        assertEquals(2, parsed.program().expressions().size());
        assertInstanceOf(SurfaceContextualIf.class, parsed.program().expressions().get(0));
        assertEquals(1, parsed.facts().trailingClosureOrigins().size());
        assertEquals(List.of(), parsed.facts().contextualElseOrigins());
    }

    @Test
    void nestedContextualIfOriginsAreOrderedInnerFirstWithDistinctOwners() {
        String source = "if(a) { if(b) { 1 } else { 2 } } else { 3 }";
        Parsed parsed = parseWithFacts(source);

        SurfaceContextualIf outer = assertInstanceOf(
                SurfaceContextualIf.class,
                parsed.program().expressions().get(0));
        SurfaceContextualIf inner = assertInstanceOf(
                SurfaceContextualIf.class,
                outer.trueBranch().body().expressions().get(0));

        List<ProtosParserSourceFacts.TrailingClosureOrigin> origins =
                parsed.facts().trailingClosureOrigins();
        assertEquals(2, origins.size());
        assertSame(inner, origins.get(0).owner());
        assertSame(inner.trueBranch(), origins.get(0).closure());
        assertSame(outer, origins.get(1).owner());
        assertSame(outer.trueBranch(), origins.get(1).closure());

        List<ProtosParserSourceFacts.ContextualElseOrigin> elses =
                parsed.facts().contextualElseOrigins();
        assertEquals(2, elses.size());
        assertSame(inner, elses.get(0).owner());
        assertSame(inner.falseBranch(), elses.get(0).falseBranch());
        assertEquals(new SourceSpan(20, 24), elses.get(0).elseSpan());
        assertSame(outer, elses.get(1).owner());
        assertSame(outer.falseBranch(), elses.get(1).falseBranch());
        assertEquals(new SourceSpan(33, 37), elses.get(1).elseSpan());
    }

    @Test
    void contextualUntilRecordsItsOwnOriginWithExactSpans() {
        String source = "{ a\nb } until(done(x))";
        Parsed parsed = parseWithFacts(source);

        SurfaceContextualUntil loop = assertInstanceOf(
                SurfaceContextualUntil.class,
                parsed.program().expressions().get(0));
        List<ProtosParserSourceFacts.ContextualUntilOrigin> origins =
                parsed.facts().contextualUntilOrigins();
        assertEquals(1, origins.size());
        assertSame(loop, origins.get(0).owner());
        assertSame(loop.body(), origins.get(0).body());
        int untilStart = source.indexOf("until");
        assertEquals(new SourceSpan(0, untilStart - 1), loop.body().span());
        assertEquals(new SourceSpan(untilStart, untilStart + 5), origins.get(0).untilSpan());
        assertEquals(
                new SourceSpan(untilStart, source.length()),
                origins.get(0).suffixSpan());
        assertEquals(
                new SourceSpan(untilStart + 6, source.length() - 1),
                loop.condition().span());
        assertEquals(new SourceSpan(0, source.length()), loop.span());
    }

    @Test
    void contextualUntilBodyIsNeitherTrailingClosureNorObject() {
        Parsed parsed = parseWithFacts("{ a } until(b)");

        assertEquals(List.of(), parsed.facts().trailingClosureOrigins());
        assertEquals(1, parsed.facts().contextualUntilOrigins().size());
        assertTrue(parsed.facts().sequenceSeparators().stream()
                .noneMatch(fact -> fact.context() == SequenceContext.OBJECT_BODY));
    }

    @Test
    void contextualUntilBodySeparatorsAreClosureBodySeparators() {
        Parsed parsed = parseWithFacts("{ a\nb } until(c)");

        assertEquals(1, parsed.facts().sequenceSeparators().size());
        assertEquals(
                SequenceContext.CLOSURE_BODY,
                parsed.facts().sequenceSeparators().get(0).context());
    }

    @Test
    void blockCommentsAroundUntilKeepTheExactUntilSpan() {
        String source = "{ a } /* one\n two */ until /* three */ (b)";
        Parsed parsed = parseWithTrivia(source);

        List<ProtosParserSourceFacts.ContextualUntilOrigin> origins =
                parsed.facts().contextualUntilOrigins();
        assertEquals(1, origins.size());
        int untilStart = source.indexOf("until");
        assertEquals(new SourceSpan(untilStart, untilStart + 5), origins.get(0).untilSpan());
    }

    @Test
    void logicalNewlineBeforeUntilRecordsNoContextualUntil() {
        String source = "{ a } // note\nuntil(b)";
        Parsed parsed = parseWithTrivia(source);

        assertEquals(2, parsed.program().expressions().size());
        assertInstanceOf(SurfaceObject.class, parsed.program().expressions().get(0));
        assertInstanceOf(SurfaceCall.class, parsed.program().expressions().get(1));
        assertEquals(List.of(), parsed.facts().contextualUntilOrigins());
    }

    @Test
    void nestedContextualUntilOriginsAreOrderedInnerFirstWithDistinctOwners() {
        Parsed parsed = parseWithFacts("{ { a } until(b) } until(c)");

        SurfaceContextualUntil outer = assertInstanceOf(
                SurfaceContextualUntil.class,
                parsed.program().expressions().get(0));
        SurfaceContextualUntil inner = assertInstanceOf(
                SurfaceContextualUntil.class,
                outer.body().body().expressions().get(0));
        List<ProtosParserSourceFacts.ContextualUntilOrigin> origins =
                parsed.facts().contextualUntilOrigins();
        assertEquals(2, origins.size());
        assertSame(inner, origins.get(0).owner());
        assertEquals(new SourceSpan(8, 13), origins.get(0).untilSpan());
        assertSame(outer, origins.get(1).owner());
        assertEquals(new SourceSpan(19, 24), origins.get(1).untilSpan());
    }

    private Parsed parseWithTrivia(String source) {
        List<ProtosLexer.TriviaOccurrence> trivia =
                new ArrayList<>();
        List<TokenOccurrence> tokens =
                new ProtosLexer(source)
                        .tokenizeOccurrencesWithTrivia(trivia::add);

        ProtosParserSourceFacts.Builder facts =
                new ProtosParserSourceFacts.Builder();

        SurfaceSequence program =
                ProtosParser.forTooling(tokens, facts).parseProgram();

        return new Parsed(program, facts.build());
    }

    private Parsed parseWithFacts(String source) {
        List<TokenOccurrence> tokens =
                new ProtosLexer(source).tokenizeOccurrences();

        ProtosParserSourceFacts.Builder facts =
                new ProtosParserSourceFacts.Builder();

        SurfaceSequence program =
                ProtosParser.forTooling(tokens, facts).parseProgram();

        return new Parsed(program, facts.build());
    }

    private record Parsed(
            SurfaceSequence program,
            ProtosParserSourceFacts facts) {
    }
}
