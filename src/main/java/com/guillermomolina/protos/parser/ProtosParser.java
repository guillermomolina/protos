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

import com.guillermomolina.protos.lexer.ProtosLexer;
import com.guillermomolina.protos.lexer.TokenOccurrence;
import com.guillermomolina.protos.lexer.TokenType;
import com.guillermomolina.protos.parser.ast.SurfaceExpression;
import com.guillermomolina.protos.parser.ast.SurfaceIntrinsic;
import com.guillermomolina.protos.parser.ast.SurfaceLiteral;
import com.guillermomolina.protos.parser.ast.SurfaceName;
import com.guillermomolina.protos.parser.ast.SurfaceSequence;
import com.guillermomolina.protos.source.SourceSpan;
import java.util.ArrayList;
import java.util.List;

public final class ProtosParser {
    private final TokenCursor cursor;

    public ProtosParser(String source) {
        this(new ProtosLexer(source).tokenizeOccurrences());
    }

    ProtosParser(List<TokenOccurrence> tokens) {
        cursor = new TokenCursor(tokens);
    }

    public SurfaceSequence parseProgram() {
        List<SurfaceExpression> expressions = new ArrayList<>();
        consumeNewlines();

        if (!cursor.at(TokenType.EOF)) {
            expressions.add(parsePrimaryFoundation());

            while (cursor.at(TokenType.NEWLINE)) {
                consumeNewlines();
                if (!cursor.at(TokenType.EOF)) {
                    expressions.add(parsePrimaryFoundation());
                }
            }
        }

        cursor.consume(TokenType.EOF, "end of source");
        return new SurfaceSequence(expressions, sequenceSpan(expressions));
    }

    private SurfaceExpression parsePrimaryFoundation() {
        TokenOccurrence token = cursor.current();
        return switch (token.token().type()) {
            case NUMBER -> literal(SurfaceLiteral.Kind.NUMBER);
            case STRING -> literal(SurfaceLiteral.Kind.STRING);
            case TRUE -> literal(SurfaceLiteral.Kind.TRUE);
            case FALSE -> literal(SurfaceLiteral.Kind.FALSE);
            case NULL -> literal(SurfaceLiteral.Kind.NULL);
            case IDENTIFIER -> {
                cursor.advance();
                yield new SurfaceName(token.token().lexeme(), token.span());
            }
            case THIS -> intrinsic(SurfaceIntrinsic.Kind.THIS);
            case CONTEXT -> intrinsic(SurfaceIntrinsic.Kind.CONTEXT);
            case ARGS -> intrinsic(SurfaceIntrinsic.Kind.ARGS);
            default -> throw ParseError.expected("a primary expression", token);
        };
    }

    private SurfaceLiteral literal(SurfaceLiteral.Kind kind) {
        TokenOccurrence token = cursor.advance();
        return new SurfaceLiteral(kind, token.token().lexeme(), token.span());
    }

    private SurfaceIntrinsic intrinsic(SurfaceIntrinsic.Kind kind) {
        TokenOccurrence token = cursor.advance();
        return new SurfaceIntrinsic(kind, token.span());
    }

    private void consumeNewlines() {
        while (cursor.at(TokenType.NEWLINE)) {
            cursor.advance();
        }
    }

    private SourceSpan sequenceSpan(List<SurfaceExpression> expressions) {
        if (expressions.isEmpty()) {
            return cursor.current().span();
        }
        return new SourceSpan(
                expressions.get(0).span().startOffset(),
                expressions.get(expressions.size() - 1).span().endOffset());
    }
}
