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
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.lexer.ProtosLexer;
import com.guillermomolina.protos.lexer.TokenOccurrence;
import com.guillermomolina.protos.lexer.TokenType;
import com.guillermomolina.protos.parser.ProtosParserSourceFacts.SequenceContext;
import com.guillermomolina.protos.parser.ProtosParserSourceFacts.SequenceSeparatorKind;
import com.guillermomolina.protos.parser.ProtosParserSourceFacts.SingleParameterClosureForm;
import com.guillermomolina.protos.parser.ast.SurfaceCall;
import com.guillermomolina.protos.parser.ast.SurfaceSequence;
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
                built.trailingClosureOrigins().get(0).call());

        assertEquals(
                0L,
                tokens.stream()
                        .filter(
                                token ->
                                        token.token().type()
                                                == TokenType.NEWLINE)
                        .count());
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
