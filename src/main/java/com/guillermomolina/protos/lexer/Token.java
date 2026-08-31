package com.guillermomolina.protos.lexer;

import java.util.Objects;

public record Token(TokenType type, String lexeme) {
    public Token {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(lexeme, "lexeme");
    }

    @Override
    public String toString() {
        return type + "(" + lexeme + ")";
    }
}
