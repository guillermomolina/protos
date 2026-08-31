package com.guillermomolina.protos.lexer;

import java.util.ArrayList;
import java.util.List;

public final class ProtosLexer {
    private static final String CUSTOM_OPERATOR_CHARS = "!$%&*+-/<=?@\\^|~";
    private static final String STRUCTURAL_CHARS = ".:;,( ){}[]";

    private final String source;
    private int index;

    public ProtosLexer(String source) {
        this.source = source == null ? "" : source;
        this.index = 0;
    }

    public List<Token> tokenize() {
        List<Token> tokens = new ArrayList<>();

        while (index < source.length()) {
            char current = source.charAt(index);

            if (Character.isWhitespace(current)) {
                if (current == '\n') {
                    tokens.add(new Token(TokenType.NEWLINE, "\n"));
                    index++;
                    continue;
                }

                if (current == '\r') {
                    index++;
                    if (index < source.length() && source.charAt(index) == '\n') {
                        index++;
                    }
                    tokens.add(new Token(TokenType.NEWLINE, "\n"));
                    continue;
                }

                if (current == '\t' || current == ' ') {
                    index++;
                    continue;
                }
            }

            if (Character.isLetter(current) || current == '_') {
                tokens.add(readIdentifierOrKeyword());
                continue;
            }

            if (Character.isDigit(current)) {
                tokens.add(readNumber());
                continue;
            }

            if (current == '"') {
                tokens.add(readString());
                continue;
            }

            Token symbol = readSymbolOrOperator();
            if (symbol == null) {
                throw new IllegalArgumentException("Unexpected character at position " + index + ": '" + current + "'");
            }
            tokens.add(symbol);
        }

        tokens.add(new Token(TokenType.EOF, ""));
        return tokens;
    }

    private Token readIdentifierOrKeyword() {
        int start = index;
        while (index < source.length()) {
            char current = source.charAt(index);
            if (Character.isLetterOrDigit(current) || current == '_') {
                index++;
            } else {
                break;
            }
        }

        String text = source.substring(start, index);
        return switch (text) {
            case "this" -> new Token(TokenType.THIS, text);
            case "context" -> new Token(TokenType.CONTEXT, text);
            case "args" -> new Token(TokenType.ARGS, text);
            case "null" -> new Token(TokenType.NULL, text);
            case "true" -> new Token(TokenType.TRUE, text);
            case "false" -> new Token(TokenType.FALSE, text);
            case "super" -> new Token(TokenType.SUPER, text);
            default -> new Token(TokenType.IDENTIFIER, text);
        };
    }

    private Token readNumber() {
        int start = index;
        boolean hasDot = false;
        String prefix = "";

        if (index + 1 < source.length()) {
            String maybePrefix = source.substring(index, index + 2).toLowerCase();
            if ("0x".equals(maybePrefix) || "0b".equals(maybePrefix) || "0o".equals(maybePrefix)) {
                prefix = source.substring(index, index + 2);
                index += 2;
            }
        }

        while (index < source.length()) {
            char current = source.charAt(index);
            if (isDigitForPrefix(prefix, current)) {
                index++;
                continue;
            }

            if (current == '_' && index + 1 < source.length()) {
                index++;
                continue;
            }

            if (prefix.isEmpty() && !hasDot && current == '.' && index + 1 < source.length() && Character.isDigit(source.charAt(index + 1))) {
                hasDot = true;
                index++;
                continue;
            }

            break;
        }

        String lexeme = source.substring(start, index);
        if (lexeme.endsWith(".")) {
            throw new IllegalArgumentException("Invalid numeric literal at position " + start + ": '" + lexeme + "'");
        }

        if (!prefix.isEmpty() && lexeme.substring(prefix.length()).replace("_", "").isEmpty()) {
            throw new IllegalArgumentException("Incomplete numeric literal at position " + start + ": '" + lexeme + "'");
        }

        return new Token(TokenType.NUMBER, lexeme);
    }

    private static boolean isDigitForPrefix(String prefix, char value) {
        if (prefix.isEmpty()) {
            return Character.isDigit(value);
        }
        return switch (prefix.toLowerCase()) {
            case "0x" -> Character.isDigit(value) || (value >= 'a' && value <= 'f') || (value >= 'A' && value <= 'F');
            case "0b" -> value == '0' || value == '1';
            case "0o" -> value >= '0' && value <= '7';
            default -> Character.isDigit(value);
        };
    }

    private Token readString() {
        int start = index;
        index++;
        StringBuilder value = new StringBuilder();

        while (index < source.length()) {
            char current = source.charAt(index);
            if (current == '"') {
                index++;
                return new Token(TokenType.STRING, value.toString());
            }
            if (current == '\\') {
                index++;
                if (index >= source.length()) {
                    throw new IllegalArgumentException("Unterminated escape sequence in string at position " + start);
                }
                char escaped = source.charAt(index++);
                value.append(switch (escaped) {
                    case 'n' -> '\n';
                    case 'r' -> '\r';
                    case 't' -> '\t';
                    case 'b' -> '\b';
                    case 'f' -> '\f';
                    case '\\' -> '\\';
                    case '"' -> '"';
                    default -> throw new IllegalArgumentException("Unsupported escape sequence \\" + escaped + " in string at position " + start);
                });
                continue;
            }
            value.append(current);
            index++;
        }

        throw new IllegalArgumentException("Unterminated string literal starting at position " + start);
    }

    private Token readSymbolOrOperator() {
        if (index >= source.length()) {
            return new Token(TokenType.EOF, "");
        }

        char current = source.charAt(index);

        if (current == '.') {
            index++;
            return new Token(TokenType.DOT, ".");
        }
        if (current == ':') {
            index++;
            return new Token(TokenType.COLON, ":");
        }
        if (current == ';') {
            index++;
            return new Token(TokenType.SEMICOLON, ";");
        }
        if (current == ',') {
            index++;
            return new Token(TokenType.COMMA, ",");
        }
        if (current == '(') {
            index++;
            return new Token(TokenType.LPAREN, "(");
        }
        if (current == ')') {
            index++;
            return new Token(TokenType.RPAREN, ")");
        }
        if (current == '{') {
            index++;
            return new Token(TokenType.LBRACE, "{");
        }
        if (current == '}') {
            index++;
            return new Token(TokenType.RBRACE, "}");
        }
        if (current == '[') {
            index++;
            return new Token(TokenType.LBRACKET, "[");
        }
        if (current == ']') {
            index++;
            return new Token(TokenType.RBRACKET, "]");
        }

        if (index + 2 < source.length()) {
            String triple = source.substring(index, index + 3);
            if ("===".equals(triple)) {
                index += 3;
                return new Token(TokenType.TRIPLE_EQUALS, "===");
            }
            if ("!==".equals(triple)) {
                index += 3;
                return new Token(TokenType.NOT_EQUALS_3, "!==");
            }
        }

        if (index + 1 < source.length()) {
            String pair = source.substring(index, index + 2);
            if ("=>".equals(pair)) {
                index += 2;
                return new Token(TokenType.FAT_ARROW, "=>");
            }
            if ("==".equals(pair)) {
                index += 2;
                return new Token(TokenType.DOUBLE_EQUALS, "==");
            }
            if ("!=".equals(pair)) {
                index += 2;
                return new Token(TokenType.NOT_EQUALS, "!=");
            }
            if ("&&".equals(pair)) {
                index += 2;
                return new Token(TokenType.AND, "&&");
            }
            if ("||".equals(pair)) {
                index += 2;
                return new Token(TokenType.OR, "||");
            }
            if ("<=".equals(pair)) {
                index += 2;
                return new Token(TokenType.LESS_EQUAL, "<=");
            }
            if (">=".equals(pair)) {
                index += 2;
                return new Token(TokenType.GREATER_EQUAL, ">=");
            }
        }

        if (current == '=') {
            index++;
            return new Token(TokenType.EQUALS, "=");
        }
        if (current == '+') {
            index++;
            return new Token(TokenType.PLUS, "+");
        }
        if (current == '-') {
            index++;
            return new Token(TokenType.MINUS, "-");
        }
        if (current == '*') {
            index++;
            return new Token(TokenType.STAR, "*");
        }
        if (current == '/') {
            index++;
            return new Token(TokenType.SLASH, "/");
        }
        if (current == '%') {
            index++;
            return new Token(TokenType.PERCENT, "%");
        }
        if (current == '!') {
            index++;
            return new Token(TokenType.BANG, "!");
        }
        if (current == '<') {
            index++;
            return new Token(TokenType.LESS, "<");
        }
        if (current == '>') {
            index++;
            return new Token(TokenType.GREATER, ">");
        }

        if (isCustomOperatorCharacter(current)) {
            int start = index;
            while (index < source.length() && isCustomOperatorCharacter(source.charAt(index))) {
                index++;
            }
            return new Token(TokenType.CUSTOM_OPERATOR, source.substring(start, index));
        }

        return null;
    }

    private static boolean isCustomOperatorCharacter(char value) {
        return CUSTOM_OPERATOR_CHARS.indexOf(value) >= 0;
    }
}
