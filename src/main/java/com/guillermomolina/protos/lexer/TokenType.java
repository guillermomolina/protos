package com.guillermomolina.protos.lexer;

public enum TokenType {
    // Identifiers and literals
    IDENTIFIER,
    NUMBER,
    STRING,

    // Reserved intrinsics
    THIS,
    CONTEXT,
    ARGS,
    NULL,
    TRUE,
    FALSE,
    SUPER,

    // Structural punctuation
    LPAREN,         // (
    RPAREN,         // )
    LBRACE,         // {
    RBRACE,         // }
    LBRACKET,       // [
    RBRACKET,       // ]
    DOT,            // .
    COMMA,          // ,
    COLON,          // :
    SEMICOLON,      // ;

    // Operators (standard)
    EQUALS,         // =
    FAT_ARROW,      // =>
    PLUS,           // +
    MINUS,          // -
    STAR,           // *
    SLASH,          // /
    PERCENT,        // %
    BANG,           // !
    LESS,           // <
    LESS_EQUAL,     // <=
    GREATER,        // >
    GREATER_EQUAL,  // >=
    DOUBLE_EQUALS,  // ==
    TRIPLE_EQUALS,  // ===
    NOT_EQUALS,     // !=
    NOT_EQUALS_2,   // !==
    AND,            // &&
    OR,             // ||
    ELLIPSIS,       // ...
    CARET,          // ^ (non-local return)

    // Custom operators
    CUSTOM_OPERATOR,

    // Control
    NEWLINE,
    EOF
}
