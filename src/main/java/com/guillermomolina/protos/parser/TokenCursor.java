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

import com.guillermomolina.protos.lexer.TokenOccurrence;
import com.guillermomolina.protos.lexer.Token;
import com.guillermomolina.protos.lexer.TokenType;
import com.guillermomolina.protos.source.SourceSpan;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

final class TokenCursor {
    private final List<TokenOccurrence> tokens;
    private int index;

    TokenCursor(List<TokenOccurrence> tokens) {
        this.tokens = List.copyOf(Objects.requireNonNull(tokens, "tokens"));
        if (this.tokens.isEmpty()
                || this.tokens.get(this.tokens.size() - 1).token().type() != TokenType.EOF) {
            throw new IllegalArgumentException("token stream must end with EOF");
        }
    }

    TokenOccurrence current() {
        return tokens.get(index);
    }

    boolean at(TokenType type) {
        return current().token().type() == type;
    }

    boolean nextAt(TokenType type) {
        return index + 1 < tokens.size()
                && tokens.get(index + 1).token().type() == type;
    }

    boolean multipleSlotCreationTargetFollowedByColon() {
        if (!at(TokenType.LPAREN)) {
            return false;
        }

        int offset = skipNewlines(index + 1);
        if (!typeAt(offset, TokenType.IDENTIFIER)) {
            return false;
        }

        int targetCount = 1;
        offset++;

        while (typeAt(offset, TokenType.COMMA)) {
            offset++;
            offset = skipNewlines(offset);

            if (!typeAt(offset, TokenType.IDENTIFIER)) {
                return false;
            }

            targetCount++;
            offset++;
        }

        offset = skipNewlines(offset);
        if (targetCount < 2 || !typeAt(offset, TokenType.RPAREN)) {
            return false;
        }

        offset++;
        return typeAt(offset, TokenType.COLON);
    }

    private int skipNewlines(int offset) {
        while (typeAt(offset, TokenType.NEWLINE)) {
            offset++;
        }
        return offset;
    }

    private boolean typeAt(int offset, TokenType type) {
        return offset >= 0
                && offset < tokens.size()
                && tokens.get(offset).token().type() == type;
    }

    boolean matchingParenthesisFollowedBy(TokenType type) {
        if (!at(TokenType.LPAREN)) {
            return false;
        }

        int depth = 0;
        for (int offset = index; offset < tokens.size(); offset++) {
            TokenType tokenType = tokens.get(offset).token().type();

            if (tokenType == TokenType.LPAREN) {
                depth++;
                continue;
            }

            if (tokenType == TokenType.RPAREN) {
                depth--;
                if (depth == 0) {
                    return offset + 1 < tokens.size()
                            && tokens.get(offset + 1).token().type() == type;
                }
                if (depth < 0) {
                    return false;
                }
                continue;
            }

            if (tokenType == TokenType.EOF) {
                return false;
            }
        }

        return false;
    }

    /**
     * D212: non-consuming recognition of the exact contextual suffix shape
     * {@code { ... } until(condition)} starting at the current {@code '{'}.
     * The bare {@code until} identifier must immediately follow the matching
     * outer {@code '}'} (no logical NEWLINE in between) and be immediately
     * followed by a balanced parenthesized suffix holding exactly one
     * syntactically present, non-spread top-level argument. Any nonmatching,
     * incomplete, or unbalanced candidate returns false so that ordinary
     * object-construction parsing and its error reporting keep authority.
     */
    boolean bracedBodyFollowedByContextualUntil() {
        if (!at(TokenType.LBRACE)) {
            return false;
        }

        int bodyClose = matchingCloseOffset(index);
        if (bodyClose < 0) {
            return false;
        }

        int keyword = bodyClose + 1;
        if (!typeAt(keyword, TokenType.IDENTIFIER)
                || !tokens.get(keyword).token().lexeme().equals("until")) {
            return false;
        }

        int suffixOpen = keyword + 1;
        if (!typeAt(suffixOpen, TokenType.LPAREN)) {
            return false;
        }

        int suffixClose = matchingCloseOffset(suffixOpen);
        if (suffixClose < 0) {
            return false;
        }

        int first = skipNewlines(suffixOpen + 1);
        if (first >= suffixClose || typeAt(first, TokenType.ELLIPSIS)) {
            return false;
        }

        int offset = first;
        while (offset < suffixClose) {
            TokenType type = tokens.get(offset).token().type();
            if (type == TokenType.COMMA) {
                return false;
            }
            if (closerFor(type) != null) {
                offset = matchingCloseOffset(offset);
                if (offset < 0) {
                    return false;
                }
            }
            offset++;
        }

        return true;
    }

    /**
     * Returns the offset of the delimiter closing the opener at
     * {@code openOffset}, respecting nested parentheses, brackets, and
     * braces, or -1 when the delimiters are unbalanced or mismatched before
     * EOF. Comments are trivia and never appear in the token stream.
     */
    private int matchingCloseOffset(int openOffset) {
        ArrayDeque<TokenType> expected = new ArrayDeque<>();
        for (int offset = openOffset; offset < tokens.size(); offset++) {
            TokenType type = tokens.get(offset).token().type();
            TokenType closer = closerFor(type);
            if (closer != null) {
                expected.push(closer);
                continue;
            }

            if (type == TokenType.RPAREN
                    || type == TokenType.RBRACKET
                    || type == TokenType.RBRACE) {
                if (expected.isEmpty() || expected.pop() != type) {
                    return -1;
                }
                if (expected.isEmpty()) {
                    return offset;
                }
                continue;
            }

            if (type == TokenType.EOF) {
                return -1;
            }
        }
        return -1;
    }

    private static TokenType closerFor(TokenType type) {
        return switch (type) {
            case LPAREN -> TokenType.RPAREN;
            case LBRACKET -> TokenType.RBRACKET;
            case LBRACE -> TokenType.RBRACE;
            default -> null;
        };
    }

    List<TokenOccurrence> consumeUntilTopLevel(
            TokenType delimiter,
            String expectation) {
        Objects.requireNonNull(delimiter, "delimiter");
        Objects.requireNonNull(expectation, "expectation");

        int start = index;
        int parenthesisDepth = 0;
        int bracketDepth = 0;
        int braceDepth = 0;

        for (int offset = index; offset < tokens.size(); offset++) {
            TokenOccurrence occurrence = tokens.get(offset);
            TokenType type = occurrence.token().type();

            if (parenthesisDepth == 0
                    && bracketDepth == 0
                    && braceDepth == 0) {
                if (type == delimiter) {
                    if (offset == start) {
                        throw ParseError.expected("a match guard expression", occurrence);
                    }

                    List<TokenOccurrence> result =
                            new ArrayList<>(tokens.subList(start, offset));
                    int boundary = occurrence.span().startOffset();
                    result.add(
                            new TokenOccurrence(
                                    new Token(TokenType.EOF, ""),
                                    new SourceSpan(boundary, boundary)));
                    index = offset;
                    return List.copyOf(result);
                }

                if (type == TokenType.SEMICOLON
                        || type == TokenType.RBRACE
                        || type == TokenType.EOF) {
                    throw ParseError.expected(expectation, occurrence);
                }
            }

            switch (type) {
                case LPAREN -> parenthesisDepth++;
                case RPAREN -> {
                    if (parenthesisDepth == 0) {
                        throw ParseError.expected(expectation, occurrence);
                    }
                    parenthesisDepth--;
                }
                case LBRACKET -> bracketDepth++;
                case RBRACKET -> {
                    if (bracketDepth == 0) {
                        throw ParseError.expected(expectation, occurrence);
                    }
                    bracketDepth--;
                }
                case LBRACE -> braceDepth++;
                case RBRACE -> {
                    if (braceDepth == 0) {
                        throw ParseError.expected(expectation, occurrence);
                    }
                    braceDepth--;
                }
                default -> {
                }
            }
        }

        throw ParseError.expected(expectation, current());
    }

    TokenOccurrence consume(TokenType type, String expectation) {
        if (!at(type)) {
            throw ParseError.expected(expectation, current());
        }
        return advance();
    }

    TokenOccurrence advance() {
        TokenOccurrence current = current();
        if (current.token().type() != TokenType.EOF) {
            index++;
        }
        return current;
    }
}
