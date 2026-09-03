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

import java.util.List;
import org.junit.jupiter.api.Test;

class ProtosLexerTest {
    @Test
    void lexesExactlyTheSevenReservedWords() {
        assertTypes(
            "this context args super true false null",
            TokenType.THIS,
            TokenType.CONTEXT,
            TokenType.ARGS,
            TokenType.SUPER,
            TokenType.TRUE,
            TokenType.FALSE,
            TokenType.NULL,
            TokenType.EOF
        );
    }

    @Test
    void reservedWordRecognitionIsExactAndCaseSensitive() {
        assertTypes(
            "This CONTEXT Args SUPER True FALSE Null",
            TokenType.IDENTIFIER,
            TokenType.IDENTIFIER,
            TokenType.IDENTIFIER,
            TokenType.IDENTIFIER,
            TokenType.IDENTIFIER,
            TokenType.IDENTIFIER,
            TokenType.IDENTIFIER,
            TokenType.EOF
        );
    }

    @Test
    void controlFlowAndErrorLookingNamesAreNotReservedWords() {
        assertTypes(
            "if else while for class function try catch throw async await",
            TokenType.IDENTIFIER,
            TokenType.IDENTIFIER,
            TokenType.IDENTIFIER,
            TokenType.IDENTIFIER,
            TokenType.IDENTIFIER,
            TokenType.IDENTIFIER,
            TokenType.IDENTIFIER,
            TokenType.IDENTIFIER,
            TokenType.IDENTIFIER,
            TokenType.IDENTIFIER,
            TokenType.IDENTIFIER,
            TokenType.EOF
        );
    }

    @Test
    void reservedWordPrefixesAndSuffixesRemainOrdinaryIdentifiers() {
        assertTypes(
            "thisValue context_ args2 superman trueValue falsehood nullish",
            TokenType.IDENTIFIER,
            TokenType.IDENTIFIER,
            TokenType.IDENTIFIER,
            TokenType.IDENTIFIER,
            TokenType.IDENTIFIER,
            TokenType.IDENTIFIER,
            TokenType.IDENTIFIER,
            TokenType.EOF
        );
    }

    @Test
    void standardAndRuntimeNamesRemainOrdinaryIdentifiers() {
        assertTypes(
            "Object Context Boolean Number Integer Float String Closure Future Array Map IdentityMap Bytes Process This",
            TokenType.IDENTIFIER,
            TokenType.IDENTIFIER,
            TokenType.IDENTIFIER,
            TokenType.IDENTIFIER,
            TokenType.IDENTIFIER,
            TokenType.IDENTIFIER,
            TokenType.IDENTIFIER,
            TokenType.IDENTIFIER,
            TokenType.IDENTIFIER,
            TokenType.IDENTIFIER,
            TokenType.IDENTIFIER,
            TokenType.IDENTIFIER,
            TokenType.IDENTIFIER,
            TokenType.IDENTIFIER,
            TokenType.IDENTIFIER,
            TokenType.EOF
        );
    }

    @Test
    void reservedWordsAreNotContextuallyReclassifiedAfterDot() {
        assertEquals(
            List.of(
                token(TokenType.IDENTIFIER, "obj"),
                token(TokenType.DOT, "."),
                token(TokenType.TRUE, "true"),
                token(TokenType.DOT, "."),
                token(TokenType.SUPER, "super"),
                token(TokenType.EOF, "")
            ),
            lex("obj.true.super")
        );
    }

    @Test
    void lexesUnicodeXidIdentifiersAndUnderscore() {
        assertEquals(
            List.of(
                token(TokenType.IDENTIFIER, "_private"),
                token(TokenType.IDENTIFIER, "café"),
                token(TokenType.IDENTIFIER, "año"),
                token(TokenType.IDENTIFIER, "π"),
                token(TokenType.IDENTIFIER, "日本語"),
                token(TokenType.IDENTIFIER, "x2"),
                token(TokenType.EOF, "")
            ),
            lex("_private café año π 日本語 x2")
        );
    }

    @Test
    void rejectsNonNfcIdentifierInsteadOfNormalizingIt() {
        assertThrows(ProtosLexer.LexicalError.class, () -> lex("cafe\u0301"));
    }

    @Test
    void javaSpecificCurrencyIdentifierCharactersAreNotProtosIdentifierCharacters() {
        assertEquals(
            List.of(
                token(TokenType.CUSTOM_OPERATOR, "$"),
                token(TokenType.IDENTIFIER, "name"),
                token(TokenType.EOF, "")
            ),
            lex("$name")
        );
    }

    @Test
    void onlySpaceAndTabAreHorizontalWhitespace() {
        assertEquals(
            List.of(token(TokenType.IDENTIFIER, "a"), token(TokenType.IDENTIFIER, "b"), token(TokenType.EOF, "")),
            lex("a \t b")
        );
        assertThrows(ProtosLexer.LexicalError.class, () -> lex("a\fb"));
        assertThrows(ProtosLexer.LexicalError.class, () -> lex("a\u00A0b"));
        assertThrows(ProtosLexer.LexicalError.class, () -> lex("a\u2028b"));
        assertThrows(ProtosLexer.LexicalError.class, () -> lex("a\uFEFFb"));
    }

    @Test
    void rejectsEveryIllustrativeNonWhitespaceCodePointOutsideLexicalConstructs() {
        for (String source : List.of(
            "a\u000Bb",
            "a\u000Cb",
            "a\u0085b",
            "a\u00A0b",
            "a\u1680b",
            "a\u2000b",
            "a\u200Ab",
            "a\u2028b",
            "a\u2029b",
            "a\u202Fb",
            "a\u205Fb",
            "a\u3000b",
            "a\uFEFFb"
        )) {
            assertThrows(ProtosLexer.LexicalError.class, () -> lex(source), source);
        }
    }

    @Test
    void whitespaceLikeUnicodeCodePointsRemainOrdinaryStringContent() {
        String value = "\u000B\u000C\u0085\u00A0\u1680\u2000\u200A\u2028\u2029\u202F\u205F\u3000\uFEFF";
        assertEquals(
            List.of(token(TokenType.STRING, value), token(TokenType.EOF, "")),
            lex("\"" + value + "\"")
        );
    }

    @Test
    void lfCrAndCrLfEachProduceOneLogicalNewlineToken() {
        assertTypes(
            "a\nb\rc\r\nd",
            TokenType.IDENTIFIER,
            TokenType.NEWLINE,
            TokenType.IDENTIFIER,
            TokenType.NEWLINE,
            TokenType.IDENTIFIER,
            TokenType.NEWLINE,
            TokenType.IDENTIFIER,
            TokenType.EOF
        );
    }

    @Test
    void lineCommentLeavesItsTerminatingNewlineForTheParser() {
        assertTypes(
            "a // comment\nb",
            TokenType.IDENTIFIER,
            TokenType.NEWLINE,
            TokenType.IDENTIFIER,
            TokenType.EOF
        );
    }

    @Test
    void lineCommentAtEndOfFileConsumesOnlyTheComment() {
        assertEquals(
            List.of(token(TokenType.IDENTIFIER, "a"), token(TokenType.EOF, "")),
            lex("a // comment")
        );
    }

    @Test
    void lineCommentLeavesCrAndCrLfTerminatorsForNewlineTokenization() {
        assertTypes(
            "a // comment\rb // comment\r\nc",
            TokenType.IDENTIFIER,
            TokenType.NEWLINE,
            TokenType.IDENTIFIER,
            TokenType.NEWLINE,
            TokenType.IDENTIFIER,
            TokenType.EOF
        );
    }

    @Test
    void blockCommentsCloseAtFirstDelimiterAndDoNotNest() {
        assertEquals(
            List.of(
                token(TokenType.IDENTIFIER, "a"),
                token(TokenType.IDENTIFIER, "b"),
                token(TokenType.EOF, "")
            ),
            lex("a /* outer /* inner */ b")
        );
    }

    @Test
    void commentDelimitersInsideStringsAreOrdinaryStringContent() {
        assertEquals(
            List.of(token(TokenType.STRING, "// /* */"), token(TokenType.EOF, "")),
            lex("\"// /* */\"")
        );
    }

    @Test
    void blockCommentConsumesEmbeddedNewlines() {
        assertTypes(
            "a /* one\r\ntwo\rthree */ b",
            TokenType.IDENTIFIER,
            TokenType.IDENTIFIER,
            TokenType.EOF
        );
    }

    @Test
    void rejectsUnterminatedBlockCommentAndHashCommentSyntax() {
        assertThrows(ProtosLexer.LexicalError.class, () -> lex("/* never closes"));
        assertThrows(ProtosLexer.LexicalError.class, () -> lex("# not a comment"));
    }

    @Test
    void lexesStructuralPunctuation() {
        assertEquals(
            List.of(
                token(TokenType.LPAREN, "("),
                token(TokenType.RPAREN, ")"),
                token(TokenType.LBRACE, "{"),
                token(TokenType.RBRACE, "}"),
                token(TokenType.LBRACKET, "["),
                token(TokenType.RBRACKET, "]"),
                token(TokenType.ELLIPSIS, "..."),
                token(TokenType.DOT, "."),
                token(TokenType.DOT, "."),
                token(TokenType.COMMA, ","),
                token(TokenType.COLON, ":"),
                token(TokenType.SEMICOLON, ";"),
                token(TokenType.EOF, "")
            ),
            lex("(){}[].....,:;")
        );
    }

    @Test
    void classifiesCompleteMaximalSymbolicSpellings() {
        assertEquals(
            List.of(
                token(TokenType.BANG, "!"),
                token(TokenType.CUSTOM_OPERATOR, "!!"),
                token(TokenType.CARET, "^"),
                token(TokenType.CUSTOM_OPERATOR, "^^"),
                token(TokenType.TRIPLE_EQUALS, "==="),
                token(TokenType.CUSTOM_OPERATOR, "===="),
                token(TokenType.NOT_EQUALS_2, "!=="),
                token(TokenType.CUSTOM_OPERATOR, "!===@"),
                token(TokenType.EOF, "")
            ),
            lex("! !! ^ ^^ === ==== !== !===@")
        );
    }

    @Test
    void lexesEveryStandardSymbolicTokenWhenItIsTheCompleteSpelling() {
        assertTypes(
            "=> = == === != !== <= >= && || + - * / % < > ! ^",
            TokenType.FAT_ARROW,
            TokenType.EQUALS,
            TokenType.DOUBLE_EQUALS,
            TokenType.TRIPLE_EQUALS,
            TokenType.NOT_EQUALS,
            TokenType.NOT_EQUALS_2,
            TokenType.LESS_EQUAL,
            TokenType.GREATER_EQUAL,
            TokenType.AND,
            TokenType.OR,
            TokenType.PLUS,
            TokenType.MINUS,
            TokenType.STAR,
            TokenType.SLASH,
            TokenType.PERCENT,
            TokenType.LESS,
            TokenType.GREATER,
            TokenType.BANG,
            TokenType.CARET,
            TokenType.EOF
        );
    }

    @Test
    void dotColonAndSemicolonNeverJoinCustomOperators() {
        assertEquals(
            List.of(
                token(TokenType.CUSTOM_OPERATOR, "@@"),
                token(TokenType.DOT, "."),
                token(TokenType.CUSTOM_OPERATOR, "??"),
                token(TokenType.COLON, ":"),
                token(TokenType.CUSTOM_OPERATOR, "~~"),
                token(TokenType.SEMICOLON, ";"),
                token(TokenType.CUSTOM_OPERATOR, "\\\\"),
                token(TokenType.EOF, "")
            ),
            lex("@@.??:~~;\\\\")
        );
    }

    @Test
    void nonstandardOperatorAlphabetCharactersFormCustomOperators() {
        assertEquals(
            List.of(
                token(TokenType.CUSTOM_OPERATOR, "$?@\\~&|"),
                token(TokenType.EOF, "")
            ),
            lex("$?@\\~&|")
        );
    }

    @Test
    void maximalOperatorRunsAreClassifiedOnlyAfterTheCompleteSpellingIsKnown() {
        assertEquals(
            List.of(
                token(TokenType.CUSTOM_OPERATOR, "=>="),
                token(TokenType.CUSTOM_OPERATOR, "&&&"),
                token(TokenType.CUSTOM_OPERATOR, "|||"),
                token(TokenType.CUSTOM_OPERATOR, "+++"),
                token(TokenType.CUSTOM_OPERATOR, "<=?"),
                token(TokenType.EOF, "")
            ),
            lex("=>= &&& ||| +++ <=?")
        );
    }

    @Test
    void commentOpenersTakeLexicalPrecedenceOverOperatorSpellings() {
        assertEquals(
            List.of(
                token(TokenType.IDENTIFIER, "a"),
                token(TokenType.NEWLINE, "\n"),
                token(TokenType.IDENTIFIER, "b"),
                token(TokenType.IDENTIFIER, "c"),
                token(TokenType.EOF, "")
            ),
            lex("a // not an operator\nb /* not an operator */ c")
        );
    }

    @Test
    void lexesDecimalIntegerFractionAndExponentForms() {
        assertEquals(
            List.of(
                token(TokenType.NUMBER, "0"),
                token(TokenType.NUMBER, "007"),
                token(TokenType.NUMBER, "1_000_000"),
                token(TokenType.NUMBER, "3.14"),
                token(TokenType.NUMBER, "1.2_5"),
                token(TokenType.NUMBER, "1e10"),
                token(TokenType.NUMBER, "1E+10"),
                token(TokenType.NUMBER, "1.5e-3"),
                token(TokenType.EOF, "")
            ),
            lex("0 007 1_000_000 3.14 1.2_5 1e10 1E+10 1.5e-3")
        );
    }

    @Test
    void lexesBinaryOctalAndHexIntegerForms() {
        assertEquals(
            List.of(
                token(TokenType.NUMBER, "0b1010_0101"),
                token(TokenType.NUMBER, "0B1"),
                token(TokenType.NUMBER, "0o77"),
                token(TokenType.NUMBER, "0O7_0"),
                token(TokenType.NUMBER, "0xFF"),
                token(TokenType.NUMBER, "0XCA_FE"),
                token(TokenType.EOF, "")
            ),
            lex("0b1010_0101 0B1 0o77 0O7_0 0xFF 0XCA_FE")
        );
    }

    @Test
    void oneDotAndLeadingDotFiveAreTokenBoundariesNotFloatSpellings() {
        assertEquals(
            List.of(token(TokenType.NUMBER, "1"), token(TokenType.DOT, "."), token(TokenType.EOF, "")),
            lex("1.")
        );
        assertEquals(
            List.of(token(TokenType.DOT, "."), token(TokenType.NUMBER, "5"), token(TokenType.EOF, "")),
            lex(".5")
        );
        assertEquals(
            List.of(
                token(TokenType.NUMBER, "1"),
                token(TokenType.DOT, "."),
                token(TokenType.IDENTIFIER, "to"),
                token(TokenType.LPAREN, "("),
                token(TokenType.NUMBER, "10"),
                token(TokenType.RPAREN, ")"),
                token(TokenType.EOF, "")
            ),
            lex("1.to(10)")
        );
    }

    @Test
    void rejectsMalformedCommittedNumericSequences() {
        for (String source : List.of(
            "0x", "0xG", "0b2", "0o8", "0b10.5", "0o17.25",
            "2e", "2e+", "2e-", "1__2", "1_", "0x_FF", "123abc"
        )) {
            assertThrows(ProtosLexer.LexicalError.class, () -> lex(source), source);
        }
    }

    @Test
    void decimalDigitSeparatorsApplyAcrossFractionAndExponentParts() {
        assertEquals(
            List.of(
                token(TokenType.NUMBER, "1_2.3_4e5_6"),
                token(TokenType.NUMBER, "7e8_9"),
                token(TokenType.EOF, "")
            ),
            lex("1_2.3_4e5_6 7e8_9")
        );
        for (String source : List.of(
            "1e_2", "1e2_", "1.2_e3", "0b1__0", "0o7_", "0xA__B"
        )) {
            assertThrows(ProtosLexer.LexicalError.class, () -> lex(source), source);
        }
    }

    @Test
    void rejectsEveryRepresentativeUnsupportedNumericSuffixAndRadixFloat() {
        for (String source : List.of(
            "1L", "1f", "1d", "1.0f", "2e3d", "0x1.8"
        )) {
            assertThrows(ProtosLexer.LexicalError.class, () -> lex(source), source);
        }
    }

    @Test
    void nanAndInfinityRemainOrdinaryIdentifiers() {
        assertEquals(
            List.of(
                token(TokenType.IDENTIFIER, "NaN"),
                token(TokenType.IDENTIFIER, "Infinity"),
                token(TokenType.MINUS, "-"),
                token(TokenType.IDENTIFIER, "Infinity"),
                token(TokenType.EOF, "")
            ),
            lex("NaN Infinity -Infinity")
        );
    }

    @Test
    void punctuationDelimitersAndOperatorsRemainValidNumericTokenBoundaries() {
        assertEquals(
            List.of(
                token(TokenType.NUMBER, "42"),
                token(TokenType.COMMA, ","),
                token(TokenType.NUMBER, "0xFF"),
                token(TokenType.RBRACKET, "]"),
                token(TokenType.NUMBER, "7"),
                token(TokenType.PLUS, "+"),
                token(TokenType.NUMBER, "8"),
                token(TokenType.SEMICOLON, ";"),
                token(TokenType.NUMBER, "9"),
                token(TokenType.EOF, "")
            ),
            lex("42,0xFF] 7+8;9")
        );
    }

    @Test
    void radixMemberAccessDotRemainsStructuralWhenNotFollowedByDecimalDigit() {
        assertEquals(
            List.of(
                token(TokenType.NUMBER, "0xFF"),
                token(TokenType.DOT, "."),
                token(TokenType.IDENTIFIER, "size"),
                token(TokenType.EOF, "")
            ),
            lex("0xFF.size")
        );
    }

    @Test
    void numericLiteralsRejectAdjacentIdentifierLikeContinuations() {
        for (String source : List.of(
            "123abc",
            "123true",
            "123_name",
            "123π",
            "0xFFname",
            "0b10π",
            "0o7_name"
        )) {
            assertThrows(ProtosLexer.LexicalError.class, () -> lex(source), source);
        }
    }

    @Test
    void numericLiteralsRemainSeparateWhenARealLexicalBoundaryExists() {
        assertEquals(
            List.of(
                token(TokenType.NUMBER, "123"),
                token(TokenType.IDENTIFIER, "name"),
                token(TokenType.NUMBER, "0xFF"),
                token(TokenType.TRUE, "true"),
                token(TokenType.EOF, "")
            ),
            lex("123 name 0xFF true")
        );
    }

    @Test
    void leadingSignsAreOperatorsNotPartOfNumbers() {
        assertEquals(
            List.of(
                token(TokenType.MINUS, "-"),
                token(TokenType.NUMBER, "1"),
                token(TokenType.PLUS, "+"),
                token(TokenType.NUMBER, "2"),
                token(TokenType.EOF, "")
            ),
            lex("-1 +2")
        );
    }

    @Test
    void singleAndDoubleQuotedStringsUseTheSameEscapeSet() {
        assertEquals(
            List.of(
                token(TokenType.STRING, "a\nA'\"\\\t\b\f\r"),
                token(TokenType.STRING, "a\nA'\"\\\t\b\f\r"),
                token(TokenType.EOF, "")
            ),
            lex("'a\\n\\u{41}\\'\\\"\\\\\\t\\b\\f\\r' \"a\\n\\u{41}\\'\\\"\\\\\\t\\b\\f\\r\"")
        );
    }

    @Test
    void unicodeEscapeSupportsSupplementaryScalarValues() {
        assertEquals(
            List.of(token(TokenType.STRING, "😀"), token(TokenType.EOF, "")),
            lex("\"\\u{1F600}\"")
        );
    }

    @Test
    void rejectsInvalidIncompleteAndNonScalarEscapes() {
        for (String source : List.of(
            "\"\\q\"",
            "\"\\x41\"",
            "\"\\0\"",
            "\"\\u{}\"",
            "\"\\u{1234567}\"",
            "\"\\u{D800}\"",
            "\"\\u{110000}\"",
            "\"\\u{41\"",
            "\"\\"
        )) {
            assertThrows(ProtosLexer.LexicalError.class, () -> lex(source), source);
        }
    }

    @Test
    void unicodeEscapeAcceptsOneThroughSixHexDigits() {
        assertEquals(
            List.of(
                token(TokenType.STRING, "A"),
                token(TokenType.STRING, "A"),
                token(TokenType.STRING, "A"),
                token(TokenType.STRING, "A"),
                token(TokenType.STRING, "A"),
                token(TokenType.STRING, "😀"),
                token(TokenType.EOF, "")
            ),
            lex("\"\\u{41}\" \"\\u{041}\" \"\\u{0041}\" \"\\u{00041}\" \"\\u{000041}\" \"\\u{01F600}\"")
        );
    }

    @Test
    void unicodeEscapeHexDigitsAreCaseInsensitive() {
        assertEquals(
            List.of(
                token(TokenType.STRING, "ú"),
                token(TokenType.STRING, "ú"),
                token(TokenType.EOF, "")
            ),
            lex("\"\\u{00fa}\" \"\\u{00FA}\"")
        );
    }

    @Test
    void rejectsNamedAndOtherNonCoreEscapeForms() {
        for (String source : List.of(
            "\"\\N{LATIN CAPITAL LETTER A}\"",
            "\"\\a\"",
            "\"\\v\"",
            "\"\\e\""
        )) {
            assertThrows(ProtosLexer.LexicalError.class, () -> lex(source), source);
        }
    }

    @Test
    void rawLogicalNewlineIsRejectedInSingleLineStrings() {
        assertThrows(ProtosLexer.LexicalError.class, () -> lex("'a\nb'"));
        assertThrows(ProtosLexer.LexicalError.class, () -> lex("\"a\r\nb\""));
    }

    @Test
    void everyLogicalNewlineSpellingIsRejectedInSingleLineStrings() {
        for (String newline : List.of("\n", "\r", "\r\n")) {
            String single = "'a" + newline + "b'";
            String doubleQuoted = "\"a" + newline + "b\"";
            assertThrows(ProtosLexer.LexicalError.class, () -> lex(single), single);
            assertThrows(ProtosLexer.LexicalError.class, () -> lex(doubleQuoted), doubleQuoted);
        }
    }

    @Test
    void tripleDoubleIndentationMatchesRawSourceBeforeEscapeProcessing() {
        String source = "\"\"\"\n\\talpha\n\t\"\"\"";
        assertThrows(ProtosLexer.LexicalError.class, () -> lex(source));
    }

    @Test
    void interpolationLookingEscapeIsRejectedRatherThanIntroducedAsSyntax() {
        assertThrows(ProtosLexer.LexicalError.class, () -> lex("\"\\${value}\""));
    }

    @Test
    void unterminatedSingleDoubleAndTripleStringsAreLexicalErrors() {
        for (String source : List.of(
            "'text",
            "\"text",
            "\"\"\"text"
        )) {
            assertThrows(ProtosLexer.LexicalError.class, () -> lex(source), source);
        }
    }

    @Test
    void tripleDoubleQuoteRunsFollowExactLexicalBoundaries() {
        assertEquals(
            List.of(token(TokenType.STRING, ""), token(TokenType.EOF, "")),
            lex("\"\"\"\"\"\"")
        );
        assertEquals(
            List.of(
                token(TokenType.STRING, "text"),
                token(TokenType.STRING, ""),
                token(TokenType.EOF, "")
            ),
            lex("\"\"\"text\"\"\"\"\"")
        );
    }

    @Test
    void tripleDoubleStringPreservesSourceNewlineSpellings() {
        assertEquals(
            List.of(token(TokenType.STRING, "a\nb\rc\r\nd"), token(TokenType.EOF, "")),
            lex("\"\"\"a\nb\rc\r\nd\"\"\"")
        );
    }

    @Test
    void tripleDoubleOpeningAndClosingNewlineRulesApply() {
        assertEquals(
            List.of(token(TokenType.STRING, "alpha\nbeta"), token(TokenType.EOF, "")),
            lex("\"\"\"\nalpha\nbeta\n\"\"\"")
        );
    }

    @Test
    void tripleDoubleCrOpeningAndClosingNewlinesAreRemovedAtomically() {
        String source = "\"\"\"\ralpha\rbeta\r\"\"\"";
        assertEquals(
            List.of(token(TokenType.STRING, "alpha\rbeta"), token(TokenType.EOF, "")),
            lex(source)
        );
    }

    @Test
    void tripleDoubleCrLfOpeningAndClosingNewlinesAreRemovedAtomically() {
        String source = "\"\"\"\r\nalpha\r\nbeta\r\n\"\"\"";
        assertEquals(
            List.of(token(TokenType.STRING, "alpha\r\nbeta"), token(TokenType.EOF, "")),
            lex(source)
        );
    }

    @Test
    void tripleDoubleStringsUseTheSameCoreEscapeSet() {
        assertEquals(
            List.of(
                token(TokenType.STRING, "a\nA\t\"\\'"),
                token(TokenType.EOF, "")
            ),
            lex("\"\"\"a\\n\\u{41}\\t\\\"\\\\\\'\"\"\"")
        );
        for (String source : List.of(
            "\"\"\"\\q\"\"\"",
            "\"\"\"\\x41\"\"\"",
            "\"\"\"\\u{}\"\"\""
        )) {
            assertThrows(ProtosLexer.LexicalError.class, () -> lex(source), source);
        }
    }

    @Test
    void tripleDoubleClosingDelimiterDefinesExactStructuralIndentation() {
        String source = "\"\"\"\n\t alpha\n\t   beta\n\t \n\t \"\"\"";
        assertEquals(
            List.of(token(TokenType.STRING, "alpha\n  beta\n"), token(TokenType.EOF, "")),
            lex(source)
        );
    }

    @Test
    void tripleDoubleIndentationMismatchIsLexicalError() {
        String source = "\"\"\"\n  alpha\n beta\n  \"\"\"";
        assertThrows(ProtosLexer.LexicalError.class, () -> lex(source));
    }

    @Test
    void tripleDoubleContentFlowingIntoClosingDelimiterIsNotIndentNormalized() {
        String source = "\"\"\"\n  alpha\n    beta\"\"\"";
        assertEquals(
            List.of(token(TokenType.STRING, "  alpha\n    beta"), token(TokenType.EOF, "")),
            lex(source)
        );
    }

    @Test
    void tripleDoubleWithoutStructuralPrefixPreservesWhitespaceOnlyLines() {
        String source = "\"\"\"\n  alpha\n \t \n    beta\"\"\"";
        assertEquals(
            List.of(token(TokenType.STRING, "  alpha\n \t \n    beta"), token(TokenType.EOF, "")),
            lex(source)
        );
    }

    @Test
    void tripleDoubleEmptyStructuralPrefixPreservesContentIndentation() {
        String source = "\"\"\"\n  alpha\n\tbeta\n\"\"\"";
        assertEquals(
            List.of(token(TokenType.STRING, "  alpha\n\tbeta"), token(TokenType.EOF, "")),
            lex(source)
        );
    }

    @Test
    void tripleDoubleBlankLineNeedNotContainTheCompleteStructuralPrefix() {
        String source = "\"\"\"\n  alpha\n \n  beta\n  \"\"\"";
        assertEquals(
            List.of(token(TokenType.STRING, "alpha\n\nbeta"), token(TokenType.EOF, "")),
            lex(source)
        );
    }

    @Test
    void tripleDoubleStructuralPrefixDistinguishesSpaceFromTab() {
        String source = "\"\"\"\n  alpha\n\t \"\"\"";
        assertThrows(ProtosLexer.LexicalError.class, () -> lex(source));
    }

    @Test
    void tripleDoubleEscapedQuoteDoesNotCloseLiteral() {
        assertEquals(
            List.of(token(TokenType.STRING, "a\"\"b"), token(TokenType.EOF, "")),
            lex("\"\"\"a\\\"\"b\"\"\"")
        );
    }

    @Test
    void firstThreeUnescapedQuotesCloseTripleStringWithoutGreedyRunConsumption() {
        assertThrows(ProtosLexer.LexicalError.class, () -> lex("\"\"\"x\"\"\"\""));
    }

    @Test
    void stringContentIsNotIdentifierNormalized() {
        String decomposed = "cafe\u0301";
        assertEquals(
            List.of(token(TokenType.STRING, decomposed), token(TokenType.EOF, "")),
            lex("\"" + decomposed + "\"")
        );
    }

    @Test
    void ordinaryStringContentAcceptsSupplementaryUnicodeScalars() {
        assertEquals(
            List.of(token(TokenType.STRING, "😀"), token(TokenType.EOF, "")),
            lex("\"😀\"")
        );
    }

    @Test
    void tripleSingleQuotesHaveNoSpecialLexicalMeaning() {
        assertEquals(
            List.of(
                token(TokenType.STRING, ""),
                token(TokenType.STRING, ""),
                token(TokenType.EOF, "")
            ),
            lex("''''")
        );
    }

    @Test
    void tripleDoubleQuoteRunBoundariesRemainExactWhenFollowingStringsAreCompleted() {
        assertEquals(
            List.of(
                token(TokenType.STRING, "text"),
                token(TokenType.STRING, "x"),
                token(TokenType.EOF, "")
            ),
            lex("\"\"\"text\"\"\"\"x\"")
        );
        assertEquals(
            List.of(
                token(TokenType.STRING, "text"),
                token(TokenType.STRING, "x"),
                token(TokenType.EOF, "")
            ),
            lex("\"\"\"text\"\"\"\"\"\"x\"\"\"")
        );
        assertEquals(
            List.of(token(TokenType.STRING, "\"x"), token(TokenType.EOF, "")),
            lex("\"\"\"\"x\"\"\"")
        );
        assertEquals(
            List.of(token(TokenType.STRING, "\"\"x"), token(TokenType.EOF, "")),
            lex("\"\"\"\"\"x\"\"\"")
        );
    }

    @Test
    void interpolationLookingTextIsOrdinaryStringContent() {
        assertEquals(
            List.of(token(TokenType.STRING, "${value}"), token(TokenType.EOF, "")),
            lex("\"${value}\"")
        );
    }

    @Test
    void representativeProtosFragmentProducesStableTokenStream() {
        assertEquals(
            List.of(
                token(TokenType.IDENTIFIER, "map"),
                token(TokenType.LBRACKET, "["),
                token(TokenType.STRING, "answer"),
                token(TokenType.RBRACKET, "]"),
                token(TokenType.EQUALS, "="),
                token(TokenType.NUMBER, "42"),
                token(TokenType.NEWLINE, "\n"),
                token(TokenType.LPAREN, "("),
                token(TokenType.IDENTIFIER, "x"),
                token(TokenType.RPAREN, ")"),
                token(TokenType.FAT_ARROW, "=>"),
                token(TokenType.IDENTIFIER, "x"),
                token(TokenType.CUSTOM_OPERATOR, "|>"),
                token(TokenType.IDENTIFIER, "transform"),
                token(TokenType.LPAREN, "("),
                token(TokenType.RPAREN, ")"),
                token(TokenType.EOF, "")
            ),
            lex("map['answer'] = 42\n(x) => x |> transform()")
        );
    }

    private static List<Token> lex(String source) {
        return new ProtosLexer(source).tokenize();
    }

    private static Token token(TokenType type, String lexeme) {
        return new Token(type, lexeme);
    }

    private static void assertTypes(String source, TokenType... expected) {
        List<TokenType> actual = lex(source).stream().map(Token::type).toList();
        assertEquals(List.of(expected), actual);
    }
}
