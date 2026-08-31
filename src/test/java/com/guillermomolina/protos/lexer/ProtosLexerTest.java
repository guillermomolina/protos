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

        assertThrows(IllegalArgumentException.class, lexer::tokenize);
    }
}
