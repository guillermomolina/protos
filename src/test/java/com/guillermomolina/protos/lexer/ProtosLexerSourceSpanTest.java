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

package com.guillermomolina.protos.lexer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.guillermomolina.protos.source.SourceSpan;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProtosLexerSourceSpanTest {
    @Test
    void tokenOccurrencesPreserveRawSourceSpansAcrossDecodedAndNormalizedTokens() {
        String source = "a\r\n\"\\n\" 0xFF";

        assertEquals(
            List.of(
                occurrence(TokenType.IDENTIFIER, "a", 0, 1),
                occurrence(TokenType.NEWLINE, "\n", 1, 3),
                occurrence(TokenType.STRING, "\n", 3, 7),
                occurrence(TokenType.NUMBER, "0xFF", 8, 12),
                occurrence(TokenType.EOF, "", 12, 12)
            ),
            new ProtosLexer(source).tokenizeOccurrences()
        );
    }

    @Test
    void ordinaryTokenizationRemainsTheProjectionOfTokenOccurrences() {
        String source = "name /* hidden */\r\nvalue";

        List<TokenOccurrence> occurrences = new ProtosLexer(source).tokenizeOccurrences();
        List<Token> projected = occurrences.stream().map(TokenOccurrence::token).toList();

        assertEquals(new ProtosLexer(source).tokenize(), projected);
    }

    @Test
    void toolingTriviaObservationPreservesTokensAndPhysicalCommentNewlines() {
        String source = "a \t// note\r\nb /* one\n two */ c";

        List<TokenOccurrence> ordinary = new ProtosLexer(source).tokenizeOccurrences();
        List<ProtosLexer.TriviaOccurrence> trivia = new ArrayList<>();
        List<TokenOccurrence> observed =
                new ProtosLexer(source).tokenizeOccurrencesWithTrivia(trivia::add);

        assertEquals(ordinary, observed);
        assertEquals(
                List.of(
                        ProtosLexer.TriviaKind.HORIZONTAL_WHITESPACE,
                        ProtosLexer.TriviaKind.LINE_COMMENT,
                        ProtosLexer.TriviaKind.HORIZONTAL_WHITESPACE,
                        ProtosLexer.TriviaKind.BLOCK_COMMENT,
                        ProtosLexer.TriviaKind.HORIZONTAL_WHITESPACE),
                trivia.stream().map(ProtosLexer.TriviaOccurrence::kind).toList());
        assertEquals(
                List.of(" \t", "// note", " ", "/* one\n two */", " "),
                trivia.stream()
                        .map(occurrence -> source.substring(
                                occurrence.span().startOffset(),
                                occurrence.span().endOffset()))
                        .toList());
        assertEquals(
                1L,
                observed.stream()
                        .filter(occurrence -> occurrence.token().type() == TokenType.NEWLINE)
                        .count());
    }

    @Test
    void sourceSpanUsesValidatedHalfOpenBounds() {
        SourceSpan span = new SourceSpan(4, 9);

        assertEquals(4, span.startOffset());
        assertEquals(9, span.endOffset());
        assertEquals(5, span.length());
        assertThrows(IllegalArgumentException.class, () -> new SourceSpan(-1, 0));
        assertThrows(IllegalArgumentException.class, () -> new SourceSpan(3, 2));
    }

    private static TokenOccurrence occurrence(TokenType type, String lexeme, int start, int end) {
        return new TokenOccurrence(new Token(type, lexeme), new SourceSpan(start, end));
    }
}
