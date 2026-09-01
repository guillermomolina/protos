/*
 * THE LICENSED WORK IS PROVIDED UNDER THE TERMS OF THE ADAPTIVE PUBLIC LICENSE ("LICENSE") AS FIRST COMPLETED BY: Guillermo Adrián Molina. ANY USE, PUBLIC DISPLAY, PUBLIC PERFORMANCE, REPRODUCTION OR DISTRIBUTION OF, OR PREPARATION OF DERIVATIVE WORKS BASED ON, THE LICENSED WORK CONSTITUTES RECIPIENT'S ACCEPTANCE OF THIS LICENSE AND ITS TERMS, WHETHER OR NOT SUCH RECIPIENT READS THE TERMS OF THE LICENSE. "LICENSED WORK" AND "RECIPIENT" ARE DEFINED IN THE LICENSE. A COPY OF THE LICENSE IS LOCATED IN THE TEXT FILE ENTITLED "LICENSE.TXT" ACCOMPANYING THE CONTENTS OF THIS FILE. IF A COPY OF THE LICENSE DOES NOT ACCOMPANY THIS FILE, A COPY OF THE LICENSE MAY ALSO BE OBTAINED AT THE FOLLOWING WEB SITE: https://github.com/guillermomolina/protos
 *
 * Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License for the specific language governing rights and limitations under the License.
 */
package com.guillermomolina.protos.lexer;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Core v0.1 lexer implementation.
 *
 * Implements lexical analysis according to PROTOS_GRAMMAR.md Core v0.1.
 * Supports:
 * - Unicode identifiers with XID_Start/XID_Continue properties and NFC validation
 * - Numeric literals: decimal, hex (0x), binary (0b), octal (0o) with digit separators and exponents
 * - String literals: single-quoted, double-quoted, triple-double-quoted with escape sequences
 * - Comments: line comments and block comments
 * - Operators: standard operators and custom symbolic operators with maximal-munch tokenization
 * - Lexical errors: invalid escapes, unterminated strings/comments, non-NFC identifiers, etc.
 */
public final class ProtosLexer {
    private static final Set<String> RESERVED_WORDS = Set.of(
        "this", "context", "args", "super", "true", "false", "null"
    );

    private static final Set<String> STANDARD_OPERATORS = Set.of(
        "=>", "=", "==", "===", "!=", "!==", "<=", ">=", "&&", "||",
        "+", "-", "*", "/", "%", "<", ">", "!"
    );

    private static final String CUSTOM_OPERATOR_CHARS = "!$%&*+-/<=>?@\\^|~";

    private final String source;
    private int pos;

    public ProtosLexer(String source) {
        this.source = source == null ? "" : source;
        this.pos = 0;
    }

    public List<Token> tokenize() {
        List<Token> tokens = new ArrayList<>();

        while (pos < source.length()) {
            char current = peek();

            if (isWhitespaceNotNewline(current)) {
                skipWhitespace();
                continue;
            }

            if (isNewline(current)) {
                tokens.add(readNewline());
                continue;
            }

            if (current == '/' && peekAhead(1) == '/') {
                skipLineComment();
                continue;
            }

            if (current == '/' && peekAhead(1) == '*') {
                skipBlockComment();
                continue;
            }

            if (isIdentifierStart(current)) {
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

            if (current == '\'') {
                tokens.add(readSingleQuotedString());
                continue;
            }

            Token token = readPunctuationOrOperator();
            if (token != null) {
                tokens.add(token);
                continue;
            }

            throw new LexicalError("Unexpected character at position " + pos + ": '" + current + "'");
        }

        tokens.add(new Token(TokenType.EOF, ""));
        return tokens;
    }

    private boolean isWhitespaceNotNewline(char c) {
        return c == ' ' || c == '\t' || c == '\f';
    }

    private boolean isNewline(char c) {
        return c == '\n' || c == '\r';
    }

    private void skipWhitespace() {
        while (pos < source.length() && isWhitespaceNotNewline(peek())) {
            pos++;
        }
    }

    private Token readNewline() {
        char current = peek();
        if (current == '\r') {
            pos++;
            if (pos < source.length() && peek() == '\n') {
                pos++;
            }
        } else if (current == '\n') {
            pos++;
        }
        return new Token(TokenType.NEWLINE, "\n");
    }

    private void skipLineComment() {
        pos += 2;
        while (pos < source.length() && peek() != '\n' && peek() != '\r') {
            pos++;
        }
    }

    private void skipBlockComment() {
        pos += 2;
        while (pos < source.length()) {
            if (peek() == '*' && peekAhead(1) == '/') {
                pos += 2;
                return;
            }
            pos++;
        }
        throw new LexicalError("Unterminated block comment");
    }

    private boolean isIdentifierStart(char c) {
        return c == '_' || Character.isUnicodeIdentifierStart(c);
    }

    private boolean isIdentifierContinue(char c) {
        return Character.isUnicodeIdentifierPart(c);
    }

    private Token readIdentifierOrKeyword() {
        int start = pos;
        while (pos < source.length() && isIdentifierContinue(peek())) {
            pos++;
        }

        String lexeme = source.substring(start, pos);

        String nfc = Normalizer.normalize(lexeme, Normalizer.Form.NFC);
        if (!lexeme.equals(nfc)) {
            throw new LexicalError("Identifier at position " + start + " is not in NFC normalization form: '" + lexeme + "'");
        }

        if (RESERVED_WORDS.contains(lexeme)) {
            return switch (lexeme) {
                case "this" -> new Token(TokenType.THIS, lexeme);
                case "context" -> new Token(TokenType.CONTEXT, lexeme);
                case "args" -> new Token(TokenType.ARGS, lexeme);
                case "super" -> new Token(TokenType.SUPER, lexeme);
                case "null" -> new Token(TokenType.NULL, lexeme);
                case "true" -> new Token(TokenType.TRUE, lexeme);
                case "false" -> new Token(TokenType.FALSE, lexeme);
                default -> new Token(TokenType.IDENTIFIER, lexeme);
            };
        }

        return new Token(TokenType.IDENTIFIER, lexeme);
    }

    private Token readNumber() {
        int start = pos;

        String prefix = "";
        if (pos < source.length() && peek() == '0' && pos + 1 < source.length()) {
            char nextChar = peekAhead(1);
            if (nextChar == 'x' || nextChar == 'X') {
                prefix = source.substring(pos, pos + 2);
                pos += 2;
            } else if (nextChar == 'b' || nextChar == 'B') {
                prefix = source.substring(pos, pos + 2);
                pos += 2;
            } else if (nextChar == 'o' || nextChar == 'O') {
                prefix = source.substring(pos, pos + 2);
                pos += 2;
            }
        }

        if (!readDigitsForPrefix(prefix)) {
            throw new LexicalError("Incomplete numeric literal at position " + start);
        }

        if (prefix.isEmpty()) {
            if (pos < source.length() && peek() == '.' && pos + 1 < source.length() && Character.isDigit(peekAhead(1))) {
                pos++;
                if (!readDigits()) {
                    throw new LexicalError("Invalid decimal point in numeric literal at position " + start);
                }
            }

            if (pos < source.length() && (peek() == 'e' || peek() == 'E')) {
                pos++;
                if (pos < source.length() && (peek() == '+' || peek() == '-')) {
                    pos++;
                }
                if (!readDigits()) {
                    throw new LexicalError("Invalid exponent in numeric literal at position " + start);
                }
            }
        }

        String lexeme = source.substring(start, pos);
        return new Token(TokenType.NUMBER, lexeme);
    }

    private boolean readDigitsForPrefix(String prefix) {
        if (prefix.isEmpty()) {
            return readDigits();
        }

        int count = 0;
        while (pos < source.length()) {
            char c = peek();
            if (c == '_') {
                if (count == 0 || pos + 1 >= source.length() || peekAhead(1) == '_') {
                    return count > 0;
                }
                pos++;
                continue;
            }

            if (isDigitForPrefix(prefix, c)) {
                pos++;
                count++;
                continue;
            }
            break;
        }
        return count > 0;
    }

    private boolean readDigits() {
        int count = 0;
        while (pos < source.length()) {
            char c = peek();
            if (c == '_') {
                if (count == 0 || pos + 1 >= source.length() || peekAhead(1) == '_') {
                    return count > 0;
                }
                pos++;
                continue;
            }

            if (Character.isDigit(c)) {
                pos++;
                count++;
                continue;
            }
            break;
        }
        return count > 0;
    }

    private boolean isDigitForPrefix(String prefix, char c) {
        return switch (prefix.toLowerCase()) {
            case "0x" -> Character.isDigit(c) || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
            case "0b" -> c == '0' || c == '1';
            case "0o" -> c >= '0' && c <= '7';
            default -> Character.isDigit(c);
        };
    }

    private Token readString() {
        int start = pos;
        pos++;

        if (pos + 1 < source.length() && peek() == '"' && peekAhead(1) == '"') {
            pos += 2;
            return readTripleQuotedString(start);
        }

        StringBuilder sb = new StringBuilder();
        while (pos < source.length()) {
            char c = peek();

            if (c == '"') {
                pos++;
                return new Token(TokenType.STRING, sb.toString());
            }

            if (c == '\n' || c == '\r') {
                throw new LexicalError("Unterminated string literal: newline not allowed in single/double-quoted strings at position " + start);
            }

            if (c == '\\') {
                sb.appendCodePoint(readEscapeSequence(start));
                continue;
            }

            sb.append(c);
            pos++;
        }

        throw new LexicalError("Unterminated string literal starting at position " + start);
    }

    private Token readSingleQuotedString() {
        int start = pos;
        pos++;

        StringBuilder sb = new StringBuilder();
        while (pos < source.length()) {
            char c = peek();

            if (c == '\'') {
                pos++;
                return new Token(TokenType.STRING, sb.toString());
            }

            if (c == '\n' || c == '\r') {
                throw new LexicalError("Unterminated string literal: newline not allowed in single/double-quoted strings at position " + start);
            }

            if (c == '\\') {
                sb.appendCodePoint(readEscapeSequence(start));
                continue;
            }

            sb.append(c);
            pos++;
        }

        throw new LexicalError("Unterminated string literal starting at position " + start);
    }

    private Token readTripleQuotedString(int start) {
        StringBuilder sb = new StringBuilder();

        if (pos < source.length() && peek() == '\n') {
            pos++;
        } else if (pos < source.length() && peek() == '\r') {
            pos++;
            if (pos < source.length() && peek() == '\n') {
                pos++;
            }
        }

        while (pos + 2 < source.length()) {
            if (peek() == '"' && peekAhead(1) == '"' && peekAhead(2) == '"') {
                pos += 3;

                String content = sb.toString();
                if (content.endsWith("\n") || content.endsWith("\r")) {
                    int lastNewline = Math.max(content.lastIndexOf('\n'), content.lastIndexOf('\r'));
                    if (lastNewline >= 0) {
                        String lastLine = content.substring(lastNewline + 1);
                        if (lastLine.matches("[ \\t]*")) {
                            content = content.substring(0, lastNewline + 1);
                            String trimmed = content.replaceAll("[ \\t]+\\n", "\n").replaceAll("[ \\t]+\\r\\n", "\r\n");
                            if (trimmed.endsWith("\n") || trimmed.endsWith("\r")) {
                                if (trimmed.endsWith("\r\n")) {
                                    content = trimmed.substring(0, trimmed.length() - 2);
                                } else {
                                    content = trimmed.substring(0, trimmed.length() - 1);
                                }
                            }
                        }
                    }
                }

                content = normalizeTripleQuotedIndentation(content);
                return new Token(TokenType.STRING, content);
            }

            char c = peek();
            if (c == '\\') {
                sb.appendCodePoint(readEscapeSequence(start));
                continue;
            }

            sb.append(c);
            pos++;
        }

        throw new LexicalError("Unterminated triple-quoted string starting at position " + start);
    }

    private String normalizeTripleQuotedIndentation(String content) {
        String[] lines = content.split("\n", -1);
        if (lines.length <= 1) {
            return content;
        }

        int minIndent = Integer.MAX_VALUE;
        for (String line : lines) {
            if (line.isEmpty() || line.matches("[ \\t]*")) {
                continue;
            }
            int indent = 0;
            for (char c : line.toCharArray()) {
                if (c == ' ' || c == '\t') {
                    indent++;
                } else {
                    break;
                }
            }
            minIndent = Math.min(minIndent, indent);
        }

        if (minIndent == Integer.MAX_VALUE || minIndent == 0) {
            return content;
        }

        StringBuilder result = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.isEmpty() || line.matches("[ \\t]*")) {
                result.append(line);
            } else {
                result.append(line.substring(Math.min(minIndent, line.length())));
            }
            if (i < lines.length - 1) {
                result.append("\n");
            }
        }

        return result.toString();
    }

    private int readEscapeSequence(int stringStart) {
        pos++;
        if (pos >= source.length()) {
            throw new LexicalError("Unterminated escape sequence in string at position " + stringStart);
        }

        char escaped = peek();
        pos++;

        return switch (escaped) {
            case '\\' -> '\\';
            case '\'' -> '\'';
            case '"' -> '"';
            case 'n' -> '\n';
            case 'r' -> '\r';
            case 't' -> '\t';
            case 'b' -> '\b';
            case 'f' -> '\f';
            case 'u' -> readUnicodeEscape(stringStart);
            default -> throw new LexicalError("Invalid escape sequence \\" + escaped + " in string at position " + stringStart);
        };
    }

    private int readUnicodeEscape(int stringStart) {
        if (pos >= source.length() || peek() != '{') {
            throw new LexicalError("Invalid unicode escape: expected \\u{ at position " + (pos - 1));
        }
        pos++;

        int hexStart = pos;
        int hexValue = 0;
        int hexCount = 0;

        while (pos < source.length() && hexCount < 6) {
            char c = peek();
            if (c == '}') {
                break;
            }

            int digit = Character.digit(c, 16);
            if (digit < 0) {
                throw new LexicalError("Invalid hex digit in unicode escape at position " + pos);
            }

            hexValue = hexValue * 16 + digit;
            hexCount++;
            pos++;
        }

        if (pos >= source.length() || peek() != '}') {
            throw new LexicalError("Unterminated unicode escape: expected } at position " + pos);
        }

        if (hexCount == 0 || hexCount > 6) {
            throw new LexicalError("Invalid unicode escape: 1-6 hex digits required");
        }

        if (hexValue > 0x10FFFF || (hexValue >= 0xD800 && hexValue <= 0xDFFF)) {
            throw new LexicalError("Invalid Unicode scalar value in escape at position " + hexStart);
        }

        pos++;
        return hexValue;
    }

    private Token readPunctuationOrOperator() {
        char current = peek();

        if (current == '(') { pos++; return new Token(TokenType.LPAREN, "("); }
        if (current == ')') { pos++; return new Token(TokenType.RPAREN, ")"); }
        if (current == '{') { pos++; return new Token(TokenType.LBRACE, "{"); }
        if (current == '}') { pos++; return new Token(TokenType.RBRACE, "}"); }
        if (current == '[') { pos++; return new Token(TokenType.LBRACKET, "["); }
        if (current == ']') { pos++; return new Token(TokenType.RBRACKET, "]"); }
        if (current == ',') { pos++; return new Token(TokenType.COMMA, ","); }
        if (current == ';') { pos++; return new Token(TokenType.SEMICOLON, ";"); }

        if (current == '.' && pos + 2 < source.length() && peekAhead(1) == '.' && peekAhead(2) == '.') {
            pos += 3;
            return new Token(TokenType.ELLIPSIS, "...");
        }

        if (current == '.') { pos++; return new Token(TokenType.DOT, "."); }
        if (current == ':') { pos++; return new Token(TokenType.COLON, ":"); }
        if (current == '^') { pos++; return new Token(TokenType.CARET, "^"); }

        return readOperatorWithMaximalMunch();
    }

    private Token readOperatorWithMaximalMunch() {
        int start = pos;
        String longestOp = null;
        int longestLen = 0;

        if (pos + 3 <= source.length()) {
            String threeChar = source.substring(pos, pos + 3);
            if (STANDARD_OPERATORS.contains(threeChar)) {
                longestOp = threeChar;
                longestLen = 3;
            }
        }

        if (pos + 2 <= source.length()) {
            String twoChar = source.substring(pos, pos + 2);
            if (STANDARD_OPERATORS.contains(twoChar) && longestLen < 2) {
                longestOp = twoChar;
                longestLen = 2;
            }
        }

        if (longestLen == 0) {
            String oneChar = source.substring(pos, pos + 1);
            if (STANDARD_OPERATORS.contains(oneChar)) {
                longestOp = oneChar;
                longestLen = 1;
            }
        }

        if (longestOp != null) {
            pos += longestLen;
            return createOperatorToken(longestOp);
        }

        if (pos < source.length() && isCustomOperatorCharacter(peek())) {
            while (pos < source.length() && isCustomOperatorCharacter(peek())) {
                pos++;
            }
            String customOp = source.substring(start, pos);
            return new Token(TokenType.CUSTOM_OPERATOR, customOp);
        }

        return null;
    }

    private Token createOperatorToken(String op) {
        return switch (op) {
            case "=>" -> new Token(TokenType.FAT_ARROW, op);
            case "=" -> new Token(TokenType.EQUALS, op);
            case "==" -> new Token(TokenType.DOUBLE_EQUALS, op);
            case "===" -> new Token(TokenType.TRIPLE_EQUALS, op);
            case "!=" -> new Token(TokenType.NOT_EQUALS, op);
            case "!==" -> new Token(TokenType.NOT_EQUALS_2, op);
            case "<=" -> new Token(TokenType.LESS_EQUAL, op);
            case ">=" -> new Token(TokenType.GREATER_EQUAL, op);
            case "&&" -> new Token(TokenType.AND, op);
            case "||" -> new Token(TokenType.OR, op);
            case "+" -> new Token(TokenType.PLUS, op);
            case "-" -> new Token(TokenType.MINUS, op);
            case "*" -> new Token(TokenType.STAR, op);
            case "/" -> new Token(TokenType.SLASH, op);
            case "%" -> new Token(TokenType.PERCENT, op);
            case "<" -> new Token(TokenType.LESS, op);
            case ">" -> new Token(TokenType.GREATER, op);
            case "!" -> new Token(TokenType.BANG, op);
            default -> new Token(TokenType.CUSTOM_OPERATOR, op);
        };
    }

    private boolean isCustomOperatorCharacter(char c) {
        return CUSTOM_OPERATOR_CHARS.indexOf(c) >= 0;
    }

    private char peek() {
        if (pos >= source.length()) {
            return '\0';
        }
        return source.charAt(pos);
    }

    private char peekAhead(int offset) {
        int targetPos = pos + offset;
        if (targetPos >= source.length()) {
            return '\0';
        }
        return source.charAt(targetPos);
    }

    public static class LexicalError extends RuntimeException {
        public LexicalError(String message) {
            super(message);
        }
    }
}
