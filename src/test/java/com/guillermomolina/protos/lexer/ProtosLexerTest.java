package com.guillermomolina.protos.lexer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class ProtosLexerTest {

    @Test
    void lexesIdentifiersIntrinsicsAndBlockStructure() {
        List<Token> tokens = new ProtosLexer("this.context: value = 42\nprint(true)").tokenize();

        assertEquals(
            List.of(
                new Token(TokenType.THIS, "this"),
                new Token(TokenType.DOT, "."),
                new Token(TokenType.CONTEXT, "context"),
                new Token(TokenType.COLON, ":"),
                new Token(TokenType.IDENTIFIER, "value"),
                new Token(TokenType.EQUALS, "="),
                new Token(TokenType.NUMBER, "42"),
                new Token(TokenType.NEWLINE, "\n"),
                new Token(TokenType.IDENTIFIER, "print"),
                new Token(TokenType.LPAREN, "("),
                new Token(TokenType.TRUE, "true"),
                new Token(TokenType.RPAREN, ")"),
                new Token(TokenType.EOF, "")
            ),
            tokens
        );
    }

    @Test
    void lexesStringsFloatsAndCustomOperators() {
        List<Token> tokens = new ProtosLexer("name: \"Guille\"\npi: 3.14\na @ b\na === b\nhex: 0xFF\nmask: 0b1010_0101").tokenize();

        assertEquals(
            List.of(
                new Token(TokenType.IDENTIFIER, "name"),
                new Token(TokenType.COLON, ":"),
                new Token(TokenType.STRING, "Guille"),
                new Token(TokenType.NEWLINE, "\n"),
                new Token(TokenType.IDENTIFIER, "pi"),
                new Token(TokenType.COLON, ":"),
                new Token(TokenType.NUMBER, "3.14"),
                new Token(TokenType.NEWLINE, "\n"),
                new Token(TokenType.IDENTIFIER, "a"),
                new Token(TokenType.CUSTOM_OPERATOR, "@"),
                new Token(TokenType.IDENTIFIER, "b"),
                new Token(TokenType.NEWLINE, "\n"),
                new Token(TokenType.IDENTIFIER, "a"),
                new Token(TokenType.TRIPLE_EQUALS, "==="),
                new Token(TokenType.IDENTIFIER, "b"),
                new Token(TokenType.NEWLINE, "\n"),
                new Token(TokenType.IDENTIFIER, "hex"),
                new Token(TokenType.COLON, ":"),
                new Token(TokenType.NUMBER, "0xFF"),
                new Token(TokenType.NEWLINE, "\n"),
                new Token(TokenType.IDENTIFIER, "mask"),
                new Token(TokenType.COLON, ":"),
                new Token(TokenType.NUMBER, "0b1010_0101"),
                new Token(TokenType.EOF, "")
            ),
            tokens
        );
    }

    @Test
    void rejectsUnterminatedString() {
        ProtosLexer lexer = new ProtosLexer("\"unterminated");

        assertThrows(ProtosLexer.LexicalError.class, lexer::tokenize);
    }

    @Test
    void lexesSingleQuotedStrings() {
        List<Token> tokens = new ProtosLexer("x: 'hello'").tokenize();

        assertEquals(
            List.of(
                new Token(TokenType.IDENTIFIER, "x"),
                new Token(TokenType.COLON, ":"),
                new Token(TokenType.STRING, "hello"),
                new Token(TokenType.EOF, "")
            ),
            tokens
        );
    }

    @Test
    void lexesTripleQuotedStrings() {
        List<Token> tokens = new ProtosLexer("text: \"\"\"hello\nworld\"\"\"").tokenize();

        assertEquals(
            List.of(
                new Token(TokenType.IDENTIFIER, "text"),
                new Token(TokenType.COLON, ":"),
                new Token(TokenType.STRING, "hello\nworld"),
                new Token(TokenType.EOF, "")
            ),
            tokens
        );
    }

    @Test
    void lexesComments() {
        List<Token> tokens = new ProtosLexer("x: 1 // comment\ny: 2").tokenize();

        assertEquals(
            List.of(
                new Token(TokenType.IDENTIFIER, "x"),
                new Token(TokenType.COLON, ":"),
                new Token(TokenType.NUMBER, "1"),
                new Token(TokenType.NEWLINE, "\n"),
                new Token(TokenType.IDENTIFIER, "y"),
                new Token(TokenType.COLON, ":"),
                new Token(TokenType.NUMBER, "2"),
                new Token(TokenType.EOF, "")
            ),
            tokens
        );
    }

    @Test
    void lexesBlockComments() {
        List<Token> tokens = new ProtosLexer("x: 1 /* comment */ y: 2").tokenize();

        assertEquals(
            List.of(
                new Token(TokenType.IDENTIFIER, "x"),
                new Token(TokenType.COLON, ":"),
                new Token(TokenType.NUMBER, "1"),
                new Token(TokenType.IDENTIFIER, "y"),
                new Token(TokenType.COLON, ":"),
                new Token(TokenType.NUMBER, "2"),
                new Token(TokenType.EOF, "")
            ),
            tokens
        );
    }

    @Test
    void rejectsUnterminatedBlockComment() {
        ProtosLexer lexer = new ProtosLexer("x: 1 /* unterminated comment");

        assertThrows(ProtosLexer.LexicalError.class, lexer::tokenize);
    }

    @Test
    void lexesEllipsis() {
        List<Token> tokens = new ProtosLexer("(...args) => { ... items }").tokenize();

        assertEquals(
            List.of(
                new Token(TokenType.LPAREN, "("),
                new Token(TokenType.ELLIPSIS, "..."),
                new Token(TokenType.ARGS, "args"),
                new Token(TokenType.RPAREN, ")"),
                new Token(TokenType.FAT_ARROW, "=>"),
                new Token(TokenType.LBRACE, "{"),
                new Token(TokenType.ELLIPSIS, "..."),
                new Token(TokenType.IDENTIFIER, "items"),
                new Token(TokenType.RBRACE, "}"),
                new Token(TokenType.EOF, "")
            ),
            tokens
        );
    }

    @Test
    void lexesCaretOperator() {
        List<Token> tokens = new ProtosLexer("^ value").tokenize();

        assertEquals(
            List.of(
                new Token(TokenType.CARET, "^"),
                new Token(TokenType.IDENTIFIER, "value"),
                new Token(TokenType.EOF, "")
            ),
            tokens
        );
    }

    @Test
    void lexesExponents() {
        List<Token> tokens = new ProtosLexer("x: 1e10\ny: 1.5e-3").tokenize();

        assertEquals(
            List.of(
                new Token(TokenType.IDENTIFIER, "x"),
                new Token(TokenType.COLON, ":"),
                new Token(TokenType.NUMBER, "1e10"),
                new Token(TokenType.NEWLINE, "\n"),
                new Token(TokenType.IDENTIFIER, "y"),
                new Token(TokenType.COLON, ":"),
                new Token(TokenType.NUMBER, "1.5e-3"),
                new Token(TokenType.EOF, "")
            ),
            tokens
        );
    }

    @Test
    void lexesOctalNumbers() {
        List<Token> tokens = new ProtosLexer("x: 0o77").tokenize();

        assertEquals(
            List.of(
                new Token(TokenType.IDENTIFIER, "x"),
                new Token(TokenType.COLON, ":"),
                new Token(TokenType.NUMBER, "0o77"),
                new Token(TokenType.EOF, "")
            ),
            tokens
        );
    }

    @Test
    void lexesEscapeSequences() {
        List<Token> tokens = new ProtosLexer("s: \"hello\\nworld\"").tokenize();

        assertEquals(
            List.of(
                new Token(TokenType.IDENTIFIER, "s"),
                new Token(TokenType.COLON, ":"),
                new Token(TokenType.STRING, "hello\nworld"),
                new Token(TokenType.EOF, "")
            ),
            tokens
        );
    }

    @Test
    void lexesUnicodeEscapes() {
        List<Token> tokens = new ProtosLexer("s: \"\\u{0041}\"").tokenize();

        assertEquals(
            List.of(
                new Token(TokenType.IDENTIFIER, "s"),
                new Token(TokenType.COLON, ":"),
                new Token(TokenType.STRING, "A"),
                new Token(TokenType.EOF, "")
            ),
            tokens
        );
    }

    @Test
    void rejectsInvalidEscapeSequence() {
        ProtosLexer lexer = new ProtosLexer("s: \"\\q\"");

        assertThrows(ProtosLexer.LexicalError.class, lexer::tokenize);
    }

    @Test
    void rejectsInvalidUnicodeEscape() {
        ProtosLexer lexer = new ProtosLexer("s: \"\\u{GGGGGG}\"");

        assertThrows(ProtosLexer.LexicalError.class, lexer::tokenize);
    }

    @Test
    void rejectsNonNFCIdentifier() {
        // Precomposed é (U+00E9) vs decomposed e + combining acute (U+0065 + U+0301)
        String nonNFC = "caf\u0065\u0301"; // café in NFD form
        ProtosLexer lexer = new ProtosLexer(nonNFC + ": 1");

        assertThrows(ProtosLexer.LexicalError.class, lexer::tokenize);
    }

    @Test
    void lexesNotEqualsOperators() {
        List<Token> tokens = new ProtosLexer("a != b\nc !== d").tokenize();

        assertEquals(
            List.of(
                new Token(TokenType.IDENTIFIER, "a"),
                new Token(TokenType.NOT_EQUALS, "!="),
                new Token(TokenType.IDENTIFIER, "b"),
                new Token(TokenType.NEWLINE, "\n"),
                new Token(TokenType.IDENTIFIER, "c"),
                new Token(TokenType.NOT_EQUALS_2, "!=="),
                new Token(TokenType.IDENTIFIER, "d"),
                new Token(TokenType.EOF, "")
            ),
            tokens
        );
    }

    @Test
    void lexesCustomOperators() {
        List<Token> tokens = new ProtosLexer("a |> b @@ c").tokenize();

        assertEquals(
            List.of(
                new Token(TokenType.IDENTIFIER, "a"),
                new Token(TokenType.CUSTOM_OPERATOR, "|>"),
                new Token(TokenType.IDENTIFIER, "b"),
                new Token(TokenType.CUSTOM_OPERATOR, "@@"),
                new Token(TokenType.IDENTIFIER, "c"),
                new Token(TokenType.EOF, "")
            ),
            tokens
        );
    }

    @Test
    void lexesDoublePeriodsAsTwoDots() {
        List<Token> tokens = new ProtosLexer("a .. b").tokenize();

        assertEquals(
            List.of(
                new Token(TokenType.IDENTIFIER, "a"),
                new Token(TokenType.DOT, "."),
                new Token(TokenType.DOT, "."),
                new Token(TokenType.IDENTIFIER, "b"),
                new Token(TokenType.EOF, "")
            ),
            tokens
        );
    }

    @Test
    void lexesLeadingZerosInDecimals() {
        List<Token> tokens = new ProtosLexer("x: 007").tokenize();

        assertEquals(
            List.of(
                new Token(TokenType.IDENTIFIER, "x"),
                new Token(TokenType.COLON, ":"),
                new Token(TokenType.NUMBER, "007"),
                new Token(TokenType.EOF, "")
            ),
            tokens
        );
    }

    @Test
    void doesNotTreatTrailingOrLeadingDotAsPartOfNumber() {
        assertEquals(
            List.of(
                new Token(TokenType.NUMBER, "1"),
                new Token(TokenType.DOT, "."),
                new Token(TokenType.EOF, "")
            ),
            new ProtosLexer("1.").tokenize()
        );

        assertEquals(
            List.of(
                new Token(TokenType.DOT, "."),
                new Token(TokenType.NUMBER, "5"),
                new Token(TokenType.EOF, "")
            ),
            new ProtosLexer(".5").tokenize()
        );
    }

    @Test
    void lexesSupplementaryUnicodeEscape() {
        List<Token> tokens = new ProtosLexer("\"\\u{1F600}\"").tokenize();

        assertEquals(
            List.of(
                new Token(TokenType.STRING, "\uD83D\uDE00"),
                new Token(TokenType.EOF, "")
            ),
            tokens
        );
    }
}
