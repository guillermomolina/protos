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
import com.guillermomolina.protos.parser.ast.SurfaceArgument;
import com.guillermomolina.protos.parser.ast.SurfaceAssignment;
import com.guillermomolina.protos.parser.ast.SurfaceSlotCreation;
import com.guillermomolina.protos.parser.ast.SurfaceCall;
import com.guillermomolina.protos.parser.ast.SurfaceExpression;
import com.guillermomolina.protos.parser.ast.SurfaceGroup;
import com.guillermomolina.protos.parser.ast.SurfaceIndex;
import com.guillermomolina.protos.parser.ast.SurfaceIntrinsic;
import com.guillermomolina.protos.parser.ast.SurfaceLiteral;
import com.guillermomolina.protos.parser.ast.SurfaceMember;
import com.guillermomolina.protos.parser.ast.SurfaceNonLocalReturn;
import com.guillermomolina.protos.parser.ast.SurfaceObject;
import com.guillermomolina.protos.parser.ast.SurfaceObjectItem;
import com.guillermomolina.protos.parser.ast.SurfaceName;
import com.guillermomolina.protos.parser.ast.SurfaceSequence;
import com.guillermomolina.protos.parser.ast.SurfaceSuperSend;
import com.guillermomolina.protos.parser.ast.SurfaceUnary;
import com.guillermomolina.protos.parser.ast.SurfaceBinary;
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
            parseExpressionLine(expressions);

            while (cursor.at(TokenType.NEWLINE)) {
                consumeNewlines();
                if (!cursor.at(TokenType.EOF)) {
                    parseExpressionLine(expressions);
                }
            }
        }

        cursor.consume(TokenType.EOF, "end of source");
        return new SurfaceSequence(expressions, sequenceSpan(expressions));
    }

    private void parseExpressionLine(List<SurfaceExpression> expressions) {
        expressions.add(parseExpressionFoundation());

        while (cursor.at(TokenType.SEMICOLON)) {
            cursor.advance();
            expressions.add(parseExpressionFoundation());
        }
    }

    private SurfaceExpression parseExpressionFoundation() {
        if (cursor.at(TokenType.CARET)) {
            TokenOccurrence caret = cursor.advance();
            consumeContinuationNewlines();
            SurfaceExpression expression = parseExpressionFoundation();
            return new SurfaceNonLocalReturn(
                    expression,
                    new SourceSpan(caret.span().startOffset(), expression.span().endOffset()));
        }

        SurfaceExpression expression = parseLogicalOrFoundation();

        if (!cursor.at(TokenType.CUSTOM_OPERATOR)) {
            return parseMutationSuffixFoundation(expression);
        }

        /*
         * A standard binary expression and a custom binary expression are separate
         * grammar alternatives. If the standard ladder already consumed an
         * unparenthesized binary operator, leaving CUSTOM_OPERATOR unconsumed makes
         * the mixed form fail at its enclosing grammar boundary.
         */
        if (expression instanceof SurfaceBinary) {
            return expression;
        }

        expression = parseCustomBinaryFoundation(expression);
        return parseMutationSuffixFoundation(expression);
    }

    private SurfaceExpression parseMutationSuffixFoundation(SurfaceExpression expression) {
        if (cursor.at(TokenType.COLON)) {
            if (!isSlotCreationTarget(expression)) {
                throw ParseError.expected("a slot-creation target before ':'", cursor.current());
            }

            cursor.advance();
            consumeContinuationNewlines();
            SurfaceExpression value = parseExpressionFoundation();
            return new SurfaceSlotCreation(
                    expression,
                    value,
                    new SourceSpan(expression.span().startOffset(), value.span().endOffset()));
        }

        if (cursor.at(TokenType.EQUALS)) {
            if (!isAssignmentTarget(expression)) {
                throw ParseError.expected("an assignment target before '='", cursor.current());
            }

            cursor.advance();
            consumeContinuationNewlines();
            SurfaceExpression value = parseExpressionFoundation();
            return new SurfaceAssignment(
                    expression,
                    value,
                    new SourceSpan(expression.span().startOffset(), value.span().endOffset()));
        }

        return expression;
    }

    private boolean isSlotCreationTarget(SurfaceExpression expression) {
        return expression instanceof SurfaceName || expression instanceof SurfaceMember;
    }

    private boolean isAssignmentTarget(SurfaceExpression expression) {
        return expression instanceof SurfaceName
                || expression instanceof SurfaceMember
                || expression instanceof SurfaceIndex;
    }

    private SurfaceExpression parseCustomBinaryFoundation(SurfaceExpression expression) {
        while (cursor.at(TokenType.CUSTOM_OPERATOR)) {
            TokenOccurrence operator = cursor.advance();
            consumeContinuationNewlines();
            SurfaceExpression right = parseUnaryFoundation();
            expression = new SurfaceBinary(
                    expression,
                    operator.token().lexeme(),
                    right,
                    new SourceSpan(expression.span().startOffset(), right.span().endOffset()));
        }
        return expression;
    }

    private SurfaceExpression parseLogicalOrFoundation() {
        return parseLeftAssociative(this::parseLogicalAndFoundation, TokenType.OR);
    }

    private SurfaceExpression parseLogicalAndFoundation() {
        return parseLeftAssociative(this::parseEqualityFoundation, TokenType.AND);
    }

    private SurfaceExpression parseEqualityFoundation() {
        return parseLeftAssociative(this::parseComparisonFoundation,
                TokenType.DOUBLE_EQUALS, TokenType.TRIPLE_EQUALS,
                TokenType.NOT_EQUALS, TokenType.NOT_EQUALS_2);
    }

    private SurfaceExpression parseComparisonFoundation() {
        return parseLeftAssociative(this::parseAdditiveFoundation,
                TokenType.LESS, TokenType.LESS_EQUAL, TokenType.GREATER, TokenType.GREATER_EQUAL);
    }

    private SurfaceExpression parseAdditiveFoundation() {
        return parseLeftAssociative(this::parseMultiplicativeFoundation, TokenType.PLUS, TokenType.MINUS);
    }

    private SurfaceExpression parseMultiplicativeFoundation() {
        return parseLeftAssociative(this::parseUnaryFoundation, TokenType.STAR, TokenType.SLASH, TokenType.PERCENT);
    }

    private SurfaceExpression parseUnaryFoundation() {
        if (cursor.at(TokenType.BANG) || cursor.at(TokenType.MINUS)) {
            TokenOccurrence operator = cursor.advance();
            consumeContinuationNewlines();
            SurfaceExpression operand = parseUnaryFoundation();
            return new SurfaceUnary(operator.token().lexeme(), operand,
                    new SourceSpan(operator.span().startOffset(), operand.span().endOffset()));
        }
        return parsePostfixFoundation();
    }

    private SurfaceExpression parseLeftAssociative(
            java.util.function.Supplier<SurfaceExpression> operandParser, TokenType... operators) {
        SurfaceExpression expression = operandParser.get();
        while (atAny(operators)) {
            TokenOccurrence operator = cursor.advance();
            consumeContinuationNewlines();
            SurfaceExpression right = operandParser.get();
            expression = new SurfaceBinary(expression, operator.token().lexeme(), right,
                    new SourceSpan(expression.span().startOffset(), right.span().endOffset()));
        }
        return expression;
    }

    private boolean atAny(TokenType... types) {
        for (TokenType type : types) {
            if (cursor.at(type)) return true;
        }
        return false;
    }

    private void consumeContinuationNewlines() {
        while (cursor.at(TokenType.NEWLINE)) cursor.advance();
    }

    private SurfaceExpression parsePostfixFoundation() {
        SurfaceExpression expression = parsePrimaryFoundation();

        while (true) {
            if (cursor.at(TokenType.NEWLINE) && cursor.nextAt(TokenType.DOT)) {
                cursor.advance();
            }

            if (cursor.at(TokenType.DOT)) {
                expression = parseMemberSuffix(expression);
                continue;
            }

            if (cursor.at(TokenType.LPAREN)) {
                expression = parseCallSuffix(expression);
                continue;
            }

            if (cursor.at(TokenType.LBRACKET)) {
                expression = parseIndexSuffix(expression);
                continue;
            }

            if (cursor.at(TokenType.LBRACE) && isParentExpression(expression)) {
                expression = parseObjectBody(expression);
                continue;
            }

            return expression;
        }
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
            case SUPER -> parseSuperMessageSend();
            case LBRACE -> parseObjectBody(null);
            case LPAREN -> parseParenthesized();
            default -> throw ParseError.expected("a primary expression", token);
        };
    }

    private SurfaceExpression parseObjectBody(SurfaceExpression parent) {
        int start = parent == null
                ? cursor.current().span().startOffset()
                : parent.span().startOffset();

        cursor.consume(TokenType.LBRACE, "'{'");
        consumeNewlines();
        List<SurfaceObjectItem> items = new ArrayList<>();

        if (!cursor.at(TokenType.RBRACE)) {
            parseObjectBodyLine(items);

            while (cursor.at(TokenType.NEWLINE)) {
                consumeNewlines();
                if (!cursor.at(TokenType.RBRACE)) {
                    parseObjectBodyLine(items);
                }
            }
        }

        TokenOccurrence close = cursor.consume(TokenType.RBRACE, "'}'");
        return new SurfaceObject(
                java.util.Optional.ofNullable(parent),
                items,
                new SourceSpan(start, close.span().endOffset()));
    }

    private void parseObjectBodyLine(List<SurfaceObjectItem> items) {
        items.add(parseObjectBodyItem());

        while (cursor.at(TokenType.SEMICOLON)) {
            cursor.advance();
            items.add(parseObjectBodyItem());
        }
    }

    private SurfaceObjectItem parseObjectBodyItem() {
        boolean composition = false;
        int start = cursor.current().span().startOffset();

        if (cursor.at(TokenType.ELLIPSIS)) {
            composition = true;
            start = cursor.advance().span().startOffset();
            consumeContinuationNewlines();
        }

        SurfaceExpression expression = parseExpressionFoundation();
        return new SurfaceObjectItem(
                composition,
                expression,
                new SourceSpan(start, expression.span().endOffset()));
    }

    private boolean isParentExpression(SurfaceExpression expression) {
        return expression instanceof SurfaceName
                || expression instanceof SurfaceIntrinsic
                || expression instanceof SurfaceMember
                || expression instanceof SurfaceGroup;
    }

    private SurfaceExpression parseSuperMessageSend() {
        TokenOccurrence superToken = cursor.consume(TokenType.SUPER, "'super'");
        cursor.consume(TokenType.DOT, "'.'");
        TokenOccurrence message = consumeMemberName();
        cursor.consume(TokenType.LPAREN, "'('");
        consumeNewlines();

        List<SurfaceArgument> arguments = new ArrayList<>();
        if (!cursor.at(TokenType.RPAREN)) {
            arguments.add(parseArgument());
            consumeNewlines();

            while (cursor.at(TokenType.COMMA)) {
                cursor.advance();
                consumeNewlines();
                arguments.add(parseArgument());
                consumeNewlines();
            }
        }

        TokenOccurrence close = cursor.consume(TokenType.RPAREN, "')'");
        return new SurfaceSuperSend(
                message.token().lexeme(),
                arguments,
                new SourceSpan(superToken.span().startOffset(), close.span().endOffset()));
    }

    private SurfaceExpression parseParenthesized() {
        TokenOccurrence open = cursor.consume(TokenType.LPAREN, "'('");
        consumeNewlines();
        SurfaceExpression expression = parseExpressionFoundation();
        consumeNewlines();
        TokenOccurrence close = cursor.consume(TokenType.RPAREN, "')'");
        return new SurfaceGroup(
                expression,
                new SourceSpan(open.span().startOffset(), close.span().endOffset()));
    }

    private SurfaceExpression parseMemberSuffix(SurfaceExpression receiver) {
        cursor.consume(TokenType.DOT, "'.'");
        TokenOccurrence name = consumeMemberName();
        return new SurfaceMember(
                receiver,
                name.token().lexeme(),
                new SourceSpan(receiver.span().startOffset(), name.span().endOffset()));
    }

    private TokenOccurrence consumeMemberName() {
        TokenType type = cursor.current().token().type();
        return switch (type) {
            case IDENTIFIER, THIS, CONTEXT, ARGS, SUPER, TRUE, FALSE, NULL -> cursor.advance();
            default -> throw ParseError.expected("a member name", cursor.current());
        };
    }

    private SurfaceExpression parseCallSuffix(SurfaceExpression receiver) {
        cursor.consume(TokenType.LPAREN, "'('");
        consumeNewlines();
        List<SurfaceArgument> arguments = new ArrayList<>();

        if (!cursor.at(TokenType.RPAREN)) {
            arguments.add(parseArgument());
            consumeNewlines();

            while (cursor.at(TokenType.COMMA)) {
                cursor.advance();
                consumeNewlines();
                arguments.add(parseArgument());
                consumeNewlines();
            }
        }

        TokenOccurrence close = cursor.consume(TokenType.RPAREN, "')'");
        return new SurfaceCall(
                receiver,
                arguments,
                new SourceSpan(receiver.span().startOffset(), close.span().endOffset()));
    }

    private SurfaceArgument parseArgument() {
        boolean spread = false;
        int start = cursor.current().span().startOffset();

        if (cursor.at(TokenType.ELLIPSIS)) {
            spread = true;
            start = cursor.advance().span().startOffset();
        }

        SurfaceExpression expression = parseExpressionFoundation();
        return new SurfaceArgument(
                spread,
                expression,
                new SourceSpan(start, expression.span().endOffset()));
    }

    private SurfaceExpression parseIndexSuffix(SurfaceExpression receiver) {
        cursor.consume(TokenType.LBRACKET, "'['");
        consumeNewlines();
        SurfaceExpression index = parseExpressionFoundation();
        consumeNewlines();
        TokenOccurrence close = cursor.consume(TokenType.RBRACKET, "']'");
        return new SurfaceIndex(
                receiver,
                index,
                new SourceSpan(receiver.span().startOffset(), close.span().endOffset()));
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
