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
