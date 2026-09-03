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

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Lexer for the Protos Core v0.1 lexical grammar.
 *
 * <p>The lexer is deliberately position-independent: parser context never changes token
 * classification. In particular, reserved words keep their dedicated token types after member
 * access, and complete symbolic operator spellings are classified only after maximal munch.</p>
 */
public final class ProtosLexer {
    private static final Map<String, TokenType> RESERVED_WORDS = Map.of(
        "this", TokenType.THIS,
        "context", TokenType.CONTEXT,
        "args", TokenType.ARGS,
        "super", TokenType.SUPER,
        "true", TokenType.TRUE,
        "false", TokenType.FALSE,
        "null", TokenType.NULL
    );

    private static final Map<String, TokenType> STANDARD_SYMBOLIC_TOKENS = Map.ofEntries(
        Map.entry("=>", TokenType.FAT_ARROW),
        Map.entry("=", TokenType.EQUALS),
        Map.entry("==", TokenType.DOUBLE_EQUALS),
        Map.entry("===", TokenType.TRIPLE_EQUALS),
        Map.entry("!=", TokenType.NOT_EQUALS),
        Map.entry("!==", TokenType.NOT_EQUALS_2),
        Map.entry("<=", TokenType.LESS_EQUAL),
        Map.entry(">=", TokenType.GREATER_EQUAL),
        Map.entry("&&", TokenType.AND),
        Map.entry("||", TokenType.OR),
        Map.entry("+", TokenType.PLUS),
        Map.entry("-", TokenType.MINUS),
        Map.entry("*", TokenType.STAR),
        Map.entry("/", TokenType.SLASH),
        Map.entry("%", TokenType.PERCENT),
        Map.entry("<", TokenType.LESS),
        Map.entry(">", TokenType.GREATER),
        Map.entry("!", TokenType.BANG),
        Map.entry("^", TokenType.CARET)
    );

    private final String source;
    private int pos;

    public ProtosLexer(String source) {
        this.source = source == null ? "" : source;
    }

    public List<Token> tokenize() {
        List<Token> tokens = new ArrayList<>();

        while (!atEnd()) {
            int codePoint = codePointAt(pos);

            if (isHorizontalWhitespace(codePoint)) {
                skipHorizontalWhitespace();
                continue;
            }

            if (isLogicalNewlineStart(codePoint)) {
                consumeLogicalNewline();
                tokens.add(new Token(TokenType.NEWLINE, "\n"));
                continue;
            }

            if (startsWith("//")) {
                skipLineComment();
                continue;
            }

            if (startsWith("/*")) {
                skipBlockComment();
                continue;
            }

            if (codePoint == '_' || UnicodeXid.isStart(codePoint)) {
                tokens.add(readIdentifierOrReservedWord());
                continue;
            }

            if (isDecimalDigit(codePoint)) {
                tokens.add(readNumber());
                continue;
            }

            if (codePoint == '\'') {
                tokens.add(readQuotedString('\''));
                continue;
            }

            if (codePoint == '"') {
                tokens.add(startsWith("\"\"\"") ? readTripleDoubleQuotedString() : readQuotedString('"'));
                continue;
            }

            Token structural = readStructuralToken();
            if (structural != null) {
                tokens.add(structural);
                continue;
            }

            if (isOperatorCharacter(codePoint)) {
                tokens.add(readSymbolicToken());
                continue;
            }

            throw error("Unexpected source character " + printableCodePoint(codePoint), pos);
        }

        tokens.add(new Token(TokenType.EOF, ""));
        return tokens;
    }

    private Token readIdentifierOrReservedWord() {
        int start = pos;
        int first = codePointAt(pos);
        advanceCodePoint(first);

        while (!atEnd()) {
            int codePoint = codePointAt(pos);
            if (codePoint != '_' && !UnicodeXid.isContinue(codePoint)) {
                break;
            }
            advanceCodePoint(codePoint);
        }

        String spelling = source.substring(start, pos);
        if (!Normalizer.isNormalized(spelling, Normalizer.Form.NFC)) {
            throw error("Identifier is not in Unicode NFC: '" + spelling + "'", start);
        }

        TokenType reserved = RESERVED_WORDS.get(spelling);
        return new Token(reserved == null ? TokenType.IDENTIFIER : reserved, spelling);
    }

    private Token readNumber() {
        int start = pos;

        if (startsWith("0x") || startsWith("0X")) {
            pos += 2;
            readRadixDigits(start, 16);
            validateRadixNumberTermination(start);
            return new Token(TokenType.NUMBER, source.substring(start, pos));
        }
        if (startsWith("0b") || startsWith("0B")) {
            pos += 2;
            readRadixDigits(start, 2);
            validateRadixNumberTermination(start);
            return new Token(TokenType.NUMBER, source.substring(start, pos));
        }
        if (startsWith("0o") || startsWith("0O")) {
            pos += 2;
            readRadixDigits(start, 8);
            validateRadixNumberTermination(start);
            return new Token(TokenType.NUMBER, source.substring(start, pos));
        }

        readDecimalDigits(start);

        if (!atEnd() && codePointAt(pos) == '.' && hasDecimalDigitAfterDot()) {
            pos++;
            readDecimalDigits(start);
        }

        if (!atEnd() && (codePointAt(pos) == 'e' || codePointAt(pos) == 'E')) {
            pos++;
            if (!atEnd() && (codePointAt(pos) == '+' || codePointAt(pos) == '-')) {
                pos++;
            }
            if (atEnd() || !isDecimalDigit(codePointAt(pos))) {
                throw error("Incomplete decimal exponent", start);
            }
            readDecimalDigits(start);
        }

        validateNumericIdentifierBoundary(start);
        return new Token(TokenType.NUMBER, source.substring(start, pos));
    }

    private void readDecimalDigits(int literalStart) {
        readSeparatedDigits(literalStart, 10);
    }

    private void readRadixDigits(int literalStart, int radix) {
        if (atEnd() || !isDigitForRadix(codePointAt(pos), radix)) {
            throw error("Radix prefix is not followed by a valid base-" + radix + " digit", literalStart);
        }
        readSeparatedDigits(literalStart, radix);
    }

    private void readSeparatedDigits(int literalStart, int radix) {
        boolean consumedDigit = false;

        while (!atEnd() && isDigitForRadix(codePointAt(pos), radix)) {
            consumedDigit = true;
            advanceCodePoint(codePointAt(pos));

            if (!atEnd() && codePointAt(pos) == '_') {
                int underscore = pos;
                pos++;
                if (atEnd() || !isDigitForRadix(codePointAt(pos), radix)) {
                    throw error("Invalid '_' placement in numeric literal", underscore);
                }
            }
        }

        if (!consumedDigit) {
            throw error("Numeric literal requires at least one digit", literalStart);
        }
    }

    private void validateRadixNumberTermination(int literalStart) {
        if (atEnd()) {
            return;
        }

        int next = codePointAt(pos);
        if (next == '.' && hasDecimalDigitAfterDot()) {
            throw error("Radix-prefixed floating-point literals are not supported", literalStart);
        }

        if (next == '_' || isDecimalDigit(next) || UnicodeXid.isStart(next) || UnicodeXid.isContinue(next)) {
            throw error("Invalid continuation after radix-prefixed numeric literal", pos);
        }
    }

    private void validateNumericIdentifierBoundary(int literalStart) {
        if (atEnd()) {
            return;
        }

        int next = codePointAt(pos);
        if (next == '_' || UnicodeXid.isStart(next) || UnicodeXid.isContinue(next)) {
            throw error("Identifier cannot begin immediately after a numeric literal", literalStart);
        }
    }

    private boolean hasDecimalDigitAfterDot() {
        int afterDot = pos + 1;
        return afterDot < source.length() && isDecimalDigit(codePointAt(afterDot));
    }

    private Token readQuotedString(char delimiter) {
        int start = pos;
        pos++;
        StringBuilder value = new StringBuilder();

        while (!atEnd()) {
            int codePoint = codePointAt(pos);

            if (codePoint == delimiter) {
                pos++;
                return new Token(TokenType.STRING, value.toString());
            }
            if (isLogicalNewlineStart(codePoint)) {
                throw error("Raw logical newline is not allowed in a single-line String literal", start);
            }
            if (codePoint == '\\') {
                appendDecodedEscape(value, start);
                continue;
            }
            if (!Character.isValidCodePoint(codePoint) || isUnpairedSurrogateAt(pos)) {
                throw error("String literal contains a non-scalar Unicode value", pos);
            }

            value.appendCodePoint(codePoint);
            advanceCodePoint(codePoint);
        }

        throw error("Unterminated String literal", start);
    }

    private Token readTripleDoubleQuotedString() {
        int start = pos;
        pos += 3;
        int contentStart = pos;
        int closingStart = findTripleDoubleClosingDelimiter(start);

        String rawContent = source.substring(contentStart, closingStart);
        String normalizedRawContent = normalizeTripleDoubleIndentation(rawContent, start);
        String value = decodeTripleDoubleContent(normalizedRawContent, start);

        pos = closingStart + 3;
        return new Token(TokenType.STRING, value);
    }

    private int findTripleDoubleClosingDelimiter(int literalStart) {
        int scan = pos;

        while (scan < source.length()) {
            if (source.startsWith("\"\"\"", scan)) {
                return scan;
            }

            int codePoint = codePointAt(scan);
            if (codePoint == '\\') {
                scan = validateAndSkipEscape(scan, literalStart);
                continue;
            }
            if (isUnpairedSurrogateAt(scan)) {
                throw error("String literal contains a non-scalar Unicode value", scan);
            }
            scan += Character.charCount(codePoint);
        }

        throw error("Unterminated triple-double-quoted String literal", literalStart);
    }

    private String normalizeTripleDoubleIndentation(String raw, int literalStart) {
        StructuralIndentation structural = structuralIndentation(raw);
        String content = raw;

        if (structural != null) {
            content = content.substring(0, structural.trailingLineStart());
        }

        int leadingNewlineLength = logicalNewlineLengthAt(content, 0);
        if (leadingNewlineLength > 0) {
            content = content.substring(leadingNewlineLength);
        }

        if (structural == null) {
            return content;
        }

        return removeStructuralIndentation(content, structural.prefix(), literalStart);
    }

    private StructuralIndentation structuralIndentation(String raw) {
        int lineStart = lastLogicalLineStart(raw);
        if (lineStart < 0) {
            return null;
        }

        String trailingLine = raw.substring(lineStart);
        if (!isIndentationOnly(trailingLine)) {
            return null;
        }

        int precedingNewlineStart = precedingLogicalNewlineStart(raw, lineStart);
        if (precedingNewlineStart < 0) {
            return null;
        }

        return new StructuralIndentation(trailingLine, precedingNewlineStart);
    }

    private String removeStructuralIndentation(String content, String prefix, int literalStart) {
        StringBuilder result = new StringBuilder(content.length());
        int lineStart = 0;

        while (lineStart <= content.length()) {
            int newlineStart = nextLogicalNewlineStart(content, lineStart);
            int lineEnd = newlineStart < 0 ? content.length() : newlineStart;
            String line = content.substring(lineStart, lineEnd);

            if (isIndentationOnly(line)) {
                // Blank logical content lines contribute no incidental indentation.
            } else if (line.startsWith(prefix)) {
                result.append(line, prefix.length(), line.length());
            } else {
                throw error("Triple-double-quoted String line does not match closing-delimiter indentation", literalStart);
            }

            if (newlineStart < 0) {
                break;
            }

            int newlineLength = logicalNewlineLengthAt(content, newlineStart);
            result.append(content, newlineStart, newlineStart + newlineLength);
            lineStart = newlineStart + newlineLength;
        }

        return result.toString();
    }

    private String decodeTripleDoubleContent(String raw, int literalStart) {
        StringBuilder value = new StringBuilder(raw.length());
        int scan = 0;

        while (scan < raw.length()) {
            int codePoint = raw.codePointAt(scan);
            if (codePoint == '\\') {
                EscapeResult escape = decodeEscapeAt(raw, scan, literalStart);
                value.appendCodePoint(escape.codePoint());
                scan = escape.nextIndex();
                continue;
            }
            if (isUnpairedSurrogateAt(raw, scan)) {
                throw error("String literal contains a non-scalar Unicode value", literalStart);
            }
            value.appendCodePoint(codePoint);
            scan += Character.charCount(codePoint);
        }

        return value.toString();
    }

    private void appendDecodedEscape(StringBuilder value, int literalStart) {
        EscapeResult result = decodeEscapeAt(source, pos, literalStart);
        value.appendCodePoint(result.codePoint());
        pos = result.nextIndex();
    }

    private int validateAndSkipEscape(int slashIndex, int literalStart) {
        return decodeEscapeAt(source, slashIndex, literalStart).nextIndex();
    }

    private EscapeResult decodeEscapeAt(String text, int slashIndex, int literalStart) {
        int next = slashIndex + 1;
        if (next >= text.length()) {
            throw error("Incomplete String escape", literalStart);
        }

        char escaped = text.charAt(next);
        return switch (escaped) {
            case '\\' -> new EscapeResult('\\', next + 1);
            case '\'' -> new EscapeResult('\'', next + 1);
            case '"' -> new EscapeResult('"', next + 1);
            case 'n' -> new EscapeResult('\n', next + 1);
            case 'r' -> new EscapeResult('\r', next + 1);
            case 't' -> new EscapeResult('\t', next + 1);
            case 'b' -> new EscapeResult('\b', next + 1);
            case 'f' -> new EscapeResult('\f', next + 1);
            case 'u' -> decodeUnicodeEscape(text, next + 1, literalStart);
            default -> throw error("Invalid String escape '\\" + escaped + "'", slashIndex);
        };
    }

    private EscapeResult decodeUnicodeEscape(String text, int braceIndex, int literalStart) {
        if (braceIndex >= text.length() || text.charAt(braceIndex) != '{') {
            throw error("Unicode escape requires '{' after \\u", literalStart);
        }

        int digitsStart = braceIndex + 1;
        int scan = digitsStart;
        int digitCount = 0;
        int value = 0;

        while (scan < text.length() && isAsciiHexDigit(text.charAt(scan))) {
            if (++digitCount > 6) {
                throw error("Unicode escape contains more than six hexadecimal digits", literalStart);
            }
            value = value * 16 + Character.digit(text.charAt(scan), 16);
            scan++;
        }

        if (digitCount == 0 || scan >= text.length() || text.charAt(scan) != '}') {
            throw error("Malformed Unicode escape", literalStart);
        }
        if (!Character.isValidCodePoint(value) || value >= 0xD800 && value <= 0xDFFF) {
            throw error("Unicode escape does not denote a Unicode scalar value", literalStart);
        }

        return new EscapeResult(value, scan + 1);
    }

    private Token readStructuralToken() {
        if (startsWith("...")) {
            pos += 3;
            return new Token(TokenType.ELLIPSIS, "...");
        }

        int codePoint = codePointAt(pos);
        TokenType type = switch (codePoint) {
            case '(' -> TokenType.LPAREN;
            case ')' -> TokenType.RPAREN;
            case '{' -> TokenType.LBRACE;
            case '}' -> TokenType.RBRACE;
            case '[' -> TokenType.LBRACKET;
            case ']' -> TokenType.RBRACKET;
            case '.' -> TokenType.DOT;
            case ',' -> TokenType.COMMA;
            case ':' -> TokenType.COLON;
            case ';' -> TokenType.SEMICOLON;
            default -> null;
        };

        if (type == null) {
            return null;
        }

        pos++;
        return new Token(type, Character.toString(codePoint));
    }

    private Token readSymbolicToken() {
        int start = pos;
        while (!atEnd() && isOperatorCharacter(codePointAt(pos))) {
            advanceCodePoint(codePointAt(pos));
        }

        String spelling = source.substring(start, pos);
        TokenType standard = STANDARD_SYMBOLIC_TOKENS.get(spelling);
        return new Token(standard == null ? TokenType.CUSTOM_OPERATOR : standard, spelling);
    }

    private void skipHorizontalWhitespace() {
        while (!atEnd() && isHorizontalWhitespace(codePointAt(pos))) {
            advanceCodePoint(codePointAt(pos));
        }
    }

    private void skipLineComment() {
        pos += 2;
        while (!atEnd() && !isLogicalNewlineStart(codePointAt(pos))) {
            advanceCodePoint(codePointAt(pos));
        }
    }

    private void skipBlockComment() {
        int start = pos;
        pos += 2;
        int end = source.indexOf("*/", pos);
        if (end < 0) {
            throw error("Unterminated block comment", start);
        }
        pos = end + 2;
    }

    private void consumeLogicalNewline() {
        if (source.charAt(pos) == '\r' && pos + 1 < source.length() && source.charAt(pos + 1) == '\n') {
            pos += 2;
        } else {
            pos++;
        }
    }

    private boolean startsWith(String spelling) {
        return source.startsWith(spelling, pos);
    }

    private boolean atEnd() {
        return pos >= source.length();
    }

    private int codePointAt(int index) {
        return source.codePointAt(index);
    }

    private void advanceCodePoint(int codePoint) {
        pos += Character.charCount(codePoint);
    }

    private static boolean isHorizontalWhitespace(int codePoint) {
        return codePoint == ' ' || codePoint == '\t';
    }

    private static boolean isLogicalNewlineStart(int codePoint) {
        return codePoint == '\n' || codePoint == '\r';
    }

    private static boolean isDecimalDigit(int codePoint) {
        return codePoint >= '0' && codePoint <= '9';
    }

    private static boolean isDigitForRadix(int codePoint, int radix) {
        return switch (radix) {
            case 2 -> codePoint == '0' || codePoint == '1';
            case 8 -> codePoint >= '0' && codePoint <= '7';
            case 10 -> isDecimalDigit(codePoint);
            case 16 -> isDecimalDigit(codePoint)
                || codePoint >= 'a' && codePoint <= 'f'
                || codePoint >= 'A' && codePoint <= 'F';
            default -> false;
        };
    }

    private static boolean isAsciiHexDigit(char c) {
        return c >= '0' && c <= '9' || c >= 'a' && c <= 'f' || c >= 'A' && c <= 'F';
    }

    private static boolean isOperatorCharacter(int codePoint) {
        return switch (codePoint) {
            case '!', '$', '%', '&', '*', '+', '-', '/', '<', '=', '>', '?', '@', '\\', '^', '|', '~' -> true;
            default -> false;
        };
    }

    private static boolean isIndentationOnly(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c != ' ' && c != '\t') {
                return false;
            }
        }
        return true;
    }

    private static int lastLogicalLineStart(String text) {
        for (int i = text.length() - 1; i >= 0; i--) {
            char c = text.charAt(i);
            if (c == '\n') {
                return i + 1;
            }
            if (c == '\r') {
                return i + 1;
            }
        }
        return -1;
    }

    private static int precedingLogicalNewlineStart(String text, int lineStart) {
        if (lineStart <= 0) {
            return -1;
        }
        int previous = lineStart - 1;
        if (text.charAt(previous) == '\n' && previous > 0 && text.charAt(previous - 1) == '\r') {
            return previous - 1;
        }
        if (text.charAt(previous) == '\n' || text.charAt(previous) == '\r') {
            return previous;
        }
        return -1;
    }

    private static int nextLogicalNewlineStart(String text, int from) {
        for (int i = from; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\n' || c == '\r') {
                return i;
            }
        }
        return -1;
    }

    private static int logicalNewlineLengthAt(String text, int index) {
        if (index < 0 || index >= text.length()) {
            return 0;
        }
        char c = text.charAt(index);
        if (c == '\r') {
            return index + 1 < text.length() && text.charAt(index + 1) == '\n' ? 2 : 1;
        }
        return c == '\n' ? 1 : 0;
    }

    private boolean isUnpairedSurrogateAt(int index) {
        return isUnpairedSurrogateAt(source, index);
    }

    private static boolean isUnpairedSurrogateAt(String text, int index) {
        char c = text.charAt(index);
        if (Character.isHighSurrogate(c)) {
            return index + 1 >= text.length() || !Character.isLowSurrogate(text.charAt(index + 1));
        }
        return Character.isLowSurrogate(c);
    }

    private LexicalError error(String message, int offset) {
        return new LexicalError(message + " at UTF-16 source offset " + offset);
    }

    private static String printableCodePoint(int codePoint) {
        if (Character.isISOControl(codePoint) || Character.isWhitespace(codePoint)) {
            return String.format("U+%04X", codePoint);
        }
        return "'" + Character.toString(codePoint) + "' (U+" + String.format("%04X", codePoint) + ")";
    }

    private record EscapeResult(int codePoint, int nextIndex) {}

    private record StructuralIndentation(String prefix, int trailingLineStart) {}

    public static final class LexicalError extends RuntimeException {
        public LexicalError(String message) {
            super(message);
        }
    }
}
