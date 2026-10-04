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
import com.guillermomolina.protos.parser.ast.SurfaceArrayConstruction;
import com.guillermomolina.protos.parser.ast.SurfaceAssignment;
import com.guillermomolina.protos.parser.ast.SurfaceMapConstruction;
import com.guillermomolina.protos.parser.ast.SurfaceMultipleSlotCreation;
import com.guillermomolina.protos.parser.ast.SurfaceSlotCreation;
import com.guillermomolina.protos.parser.ast.SurfaceCall;
import com.guillermomolina.protos.parser.ast.SurfaceClosure;
import com.guillermomolina.protos.parser.ast.SurfaceExpression;
import com.guillermomolina.protos.parser.ast.SurfaceGroup;
import com.guillermomolina.protos.parser.ast.SurfaceIndex;
import com.guillermomolina.protos.parser.ast.SurfaceIntrinsic;
import com.guillermomolina.protos.parser.ast.SurfaceLiteral;
import com.guillermomolina.protos.parser.ast.SurfaceMember;
import com.guillermomolina.protos.parser.ast.SurfaceNonLocalReturn;
import com.guillermomolina.protos.parser.ast.SurfaceObject;
import com.guillermomolina.protos.parser.ast.SurfaceObjectItem;
import com.guillermomolina.protos.parser.ast.SurfaceParameter;
import com.guillermomolina.protos.parser.ast.SurfaceName;
import com.guillermomolina.protos.parser.ast.SurfaceSequence;
import com.guillermomolina.protos.parser.ast.SurfaceSuperSend;
import com.guillermomolina.protos.parser.ast.SurfaceUnary;
import com.guillermomolina.protos.parser.ast.SurfaceBinary;
import com.guillermomolina.protos.source.SourceSpan;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class ProtosParser {
    private final TokenCursor cursor;
    private final ProtosParserSourceFacts.Builder sourceFacts;

    public ProtosParser(String source) {
        this(new ProtosLexer(source).tokenizeOccurrences(), null);
    }

    ProtosParser(List<TokenOccurrence> tokens) {
        this(tokens, null);
    }

    public static ProtosParser forTooling(
            List<TokenOccurrence> tokens,
            ProtosParserSourceFacts.Builder sourceFacts) {
        return new ProtosParser(
                java.util.Objects.requireNonNull(tokens, "tokens"),
                java.util.Objects.requireNonNull(sourceFacts, "sourceFacts"));
    }

    private ProtosParser(
            List<TokenOccurrence> tokens,
            ProtosParserSourceFacts.Builder sourceFacts) {
        cursor = new TokenCursor(tokens);
        this.sourceFacts = sourceFacts;
    }

    public SurfaceSequence parseProgram() {
        List<SurfaceExpression> expressions = new ArrayList<>();
        consumeNewlines();

        if (!cursor.at(TokenType.EOF)) {
            parseExpressionLine(
                    expressions,
                    ProtosParserSourceFacts.SequenceContext.PROGRAM);

            while (cursor.at(TokenType.NEWLINE)) {
                SurfaceExpression preceding =
                        expressions.get(expressions.size() - 1);
                SourceSpan separatorSpan = consumeSequenceNewlines();
                if (!cursor.at(TokenType.EOF)) {
                    int followingIndex = expressions.size();
                    parseExpressionLine(
                            expressions,
                            ProtosParserSourceFacts.SequenceContext.PROGRAM);
                    recordExpressionSeparator(
                            ProtosParserSourceFacts.SequenceContext.PROGRAM,
                            preceding,
                            ProtosParserSourceFacts.SequenceSeparatorKind.LOGICAL_NEWLINE,
                            separatorSpan,
                            expressions.get(followingIndex));
                }
            }
        }

        cursor.consume(TokenType.EOF, "end of source");
        return new SurfaceSequence(expressions, sequenceSpan(expressions));
    }

    private void parseExpressionLine(
            List<SurfaceExpression> expressions,
            ProtosParserSourceFacts.SequenceContext context) {
        expressions.add(parseExpressionFoundation());

        while (cursor.at(TokenType.SEMICOLON)) {
            SurfaceExpression preceding =
                    expressions.get(expressions.size() - 1);
            TokenOccurrence separator = cursor.advance();
            SurfaceExpression following = parseExpressionFoundation();
            expressions.add(following);
            recordExpressionSeparator(
                    context,
                    preceding,
                    ProtosParserSourceFacts.SequenceSeparatorKind.SEMICOLON,
                    separator.span(),
                    following);
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

        if (cursor.multipleSlotCreationTargetFollowedByColon()) {
            return parseMultipleSlotCreation();
        }

        SurfaceExpression expression = parseBinaryExpressionFoundation();
        return parseMutationSuffixFoundation(expression);
    }

    private SurfaceExpression parseMultipleSlotCreation() {
        TokenOccurrence open = cursor.consume(TokenType.LPAREN, "'('");
        consumeNewlines();

        List<SurfaceName> targets = new ArrayList<>();
        targets.add(parseMultipleSlotCreationTargetName());

        while (cursor.at(TokenType.COMMA)) {
            cursor.advance();
            consumeNewlines();
            targets.add(parseMultipleSlotCreationTargetName());
        }

        consumeNewlines();
        cursor.consume(TokenType.RPAREN, "')'");
        cursor.consume(TokenType.COLON, "':' after multiple slot-creation target");
        consumeContinuationNewlines();

        SurfaceExpression value = parseExpressionFoundation();
        return new SurfaceMultipleSlotCreation(
                targets,
                value,
                new SourceSpan(open.span().startOffset(), value.span().endOffset()));
    }

    private SurfaceName parseMultipleSlotCreationTargetName() {
        TokenOccurrence name =
                cursor.consume(TokenType.IDENTIFIER, "a multiple slot-creation target name");
        return new SurfaceName(name.token().lexeme(), name.span());
    }

    private SurfaceExpression parseBinaryExpressionFoundation() {
        return parseLogicalOrFoundation();
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
                if (cursor.nextAt(TokenType.FAT_ARROW)) {
                    yield parseBareParameterClosure();
                }
                cursor.advance();
                yield new SurfaceName(token.token().lexeme(), token.span());
            }
            case THIS -> intrinsic(SurfaceIntrinsic.Kind.THIS);
            case CONTEXT -> intrinsic(SurfaceIntrinsic.Kind.CONTEXT);
            case SUPER -> parseSuperMessageSend();
            case LBRACKET -> parseArrayConstruction();
            case LBRACE -> parseObjectBody(null);
            case PERCENT -> parseMapConstruction();
            case LPAREN -> cursor.matchingParenthesisFollowedBy(TokenType.FAT_ARROW)
                    ? parseParameterListClosure()
                    : parseParenthesized();
            default -> throw ParseError.expected("a primary expression", token);
        };
    }

    private SurfaceExpression parseArrayConstruction() {
        TokenOccurrence open = cursor.consume(TokenType.LBRACKET, "'['");
        consumeNewlines();

        List<SurfaceArgument> arguments = new ArrayList<>();
        if (!cursor.at(TokenType.RBRACKET)) {
            arguments.add(parseArgument());

            while (cursor.at(TokenType.COMMA)) {
                cursor.advance();
                consumeNewlines();

                if (cursor.at(TokenType.RBRACKET)) {
                    throw ParseError.expected(
                            "an Array construction item after ','",
                            cursor.current());
                }

                arguments.add(parseArgument());
            }

            consumeNewlines();
        }

        TokenOccurrence close = cursor.consume(TokenType.RBRACKET, "']'");
        return new SurfaceArrayConstruction(
                arguments,
                new SourceSpan(
                        open.span().startOffset(),
                        close.span().endOffset()));
    }

    private SurfaceExpression parseMapConstruction() {
        TokenOccurrence percent = cursor.consume(TokenType.PERCENT, "'%'");
        cursor.consume(TokenType.LBRACE, "'{' after '%' in Map construction");
        consumeNewlines();

        List<SurfaceMapConstruction.Entry> entries = new ArrayList<>();
        if (!cursor.at(TokenType.RBRACE)) {
            parseMapConstructionLine(entries);

            while (cursor.at(TokenType.NEWLINE)) {
                SurfaceMapConstruction.Entry preceding =
                        entries.get(entries.size() - 1);
                SourceSpan separatorSpan = consumeSequenceNewlines();
                if (!cursor.at(TokenType.RBRACE)) {
                    int followingIndex = entries.size();
                    parseMapConstructionLine(entries);
                    recordMapEntrySeparator(
                            preceding,
                            ProtosParserSourceFacts.SequenceSeparatorKind.LOGICAL_NEWLINE,
                            separatorSpan,
                            entries.get(followingIndex));
                }
            }
        }

        TokenOccurrence close = cursor.consume(TokenType.RBRACE, "'}'");
        return new SurfaceMapConstruction(
                entries,
                new SourceSpan(
                        percent.span().startOffset(),
                        close.span().endOffset()));
    }

    private void parseMapConstructionLine(
            List<SurfaceMapConstruction.Entry> entries) {
        entries.add(parseMapConstructionEntry());

        while (cursor.at(TokenType.SEMICOLON)) {
            SurfaceMapConstruction.Entry preceding =
                    entries.get(entries.size() - 1);
            TokenOccurrence separator = cursor.advance();
            SurfaceMapConstruction.Entry following =
                    parseMapConstructionEntry();
            entries.add(following);
            recordMapEntrySeparator(
                    preceding,
                    ProtosParserSourceFacts.SequenceSeparatorKind.SEMICOLON,
                    separator.span(),
                    following);
        }
    }

    private SurfaceMapConstruction.Entry parseMapConstructionEntry() {
        SurfaceExpression key = parseBinaryExpressionFoundation();
        cursor.consume(TokenType.COLON, "':' after Map construction key");
        consumeContinuationNewlines();
        SurfaceExpression value = parseExpressionFoundation();
        return new SurfaceMapConstruction.Entry(
                key,
                value,
                new SourceSpan(
                        key.span().startOffset(),
                        value.span().endOffset()));
    }

    private SurfaceExpression parseBareParameterClosure() {
        TokenOccurrence parameter =
                cursor.consume(TokenType.IDENTIFIER, "a closure parameter");
        SurfaceParameter surfaceParameter = new SurfaceParameter(
                parameter.token().lexeme(),
                Optional.empty(),
                false,
                parameter.span());
        cursor.consume(TokenType.FAT_ARROW, "'=>'");
        SurfaceClosure closure =
                (SurfaceClosure) parseClosureBody(
                        List.of(surfaceParameter),
                        parameter.span().startOffset());
        recordSingleParameterClosureForm(
                closure,
                ProtosParserSourceFacts.SingleParameterClosureForm.BARE);
        return closure;
    }

    private SurfaceExpression parseParameterListClosure() {
        TokenOccurrence open = cursor.consume(TokenType.LPAREN, "'('");
        consumeNewlines();

        List<SurfaceParameter> parameters = new ArrayList<>();
        Set<String> parameterNames = new HashSet<>();

        if (!cursor.at(TokenType.RPAREN)) {
            if (cursor.at(TokenType.ELLIPSIS)) {
                addParameter(parameters, parameterNames, parseRestParameter());
            } else {
                addParameter(parameters, parameterNames, parseParameter());

                while (cursor.at(TokenType.COMMA)) {
                    cursor.advance();
                    consumeNewlines();

                    if (cursor.at(TokenType.ELLIPSIS)) {
                        addParameter(parameters, parameterNames, parseRestParameter());
                        break;
                    }

                    addParameter(parameters, parameterNames, parseParameter());
                }
            }

            consumeNewlines();
        }

        boolean sawDefaultedParameter = false;
        for (SurfaceParameter parameter : parameters) {
            if (!parameter.rest()
                    && parameter.defaultValue().isEmpty()
                    && sawDefaultedParameter) {
                throw new ParseError(
                        "Required non-rest Closure parameters must precede defaulted parameters",
                        parameter.span());
            }
            if (parameter.defaultValue().isPresent()) {
                sawDefaultedParameter = true;
            }
        }

        cursor.consume(TokenType.RPAREN, "')'");
        cursor.consume(TokenType.FAT_ARROW, "'=>'");
        SurfaceClosure closure =
                (SurfaceClosure) parseClosureBody(
                        parameters,
                        open.span().startOffset());
        if (parameters.size() == 1) {
            recordSingleParameterClosureForm(
                    closure,
                    ProtosParserSourceFacts.SingleParameterClosureForm.PARENTHESIZED);
        }
        return closure;
    }

    private SurfaceParameter parseParameter() {
        TokenOccurrence name = cursor.consume(TokenType.IDENTIFIER, "a parameter name");

        if (!cursor.at(TokenType.EQUALS)) {
            return new SurfaceParameter(
                    name.token().lexeme(),
                    Optional.empty(),
                    false,
                    name.span());
        }

        cursor.advance();
        consumeContinuationNewlines();
        SurfaceExpression defaultValue = parseExpressionFoundation();
        return new SurfaceParameter(
                name.token().lexeme(),
                Optional.of(defaultValue),
                false,
                new SourceSpan(name.span().startOffset(), defaultValue.span().endOffset()));
    }

    private SurfaceParameter parseRestParameter() {
        TokenOccurrence spread = cursor.consume(TokenType.ELLIPSIS, "'...'");
        consumeContinuationNewlines();
        TokenOccurrence name = cursor.consume(TokenType.IDENTIFIER, "a rest parameter name");
        return new SurfaceParameter(
                name.token().lexeme(),
                Optional.empty(),
                true,
                new SourceSpan(spread.span().startOffset(), name.span().endOffset()));
    }

    private void addParameter(
            List<SurfaceParameter> parameters,
            Set<String> parameterNames,
            SurfaceParameter parameter) {
        if (!parameterNames.add(parameter.name())) {
            throw ParseError.expected("a unique parameter name", cursor.current());
        }
        parameters.add(parameter);
    }

    private SurfaceExpression parseClosureBody(
            List<SurfaceParameter> parameters, int startOffset) {
        consumeContinuationNewlines();

        if (cursor.at(TokenType.LBRACE)) {
            TokenOccurrence open = cursor.advance();
            consumeNewlines();
            List<SurfaceExpression> expressions = new ArrayList<>();

            if (!cursor.at(TokenType.RBRACE)) {
                parseExpressionLine(
                        expressions,
                        ProtosParserSourceFacts.SequenceContext.CLOSURE_BODY);

                while (cursor.at(TokenType.NEWLINE)) {
                    SurfaceExpression preceding =
                            expressions.get(expressions.size() - 1);
                    SourceSpan separatorSpan = consumeSequenceNewlines();
                    if (!cursor.at(TokenType.RBRACE)) {
                        int followingIndex = expressions.size();
                        parseExpressionLine(
                                expressions,
                                ProtosParserSourceFacts.SequenceContext.CLOSURE_BODY);
                        recordExpressionSeparator(
                                ProtosParserSourceFacts.SequenceContext.CLOSURE_BODY,
                                preceding,
                                ProtosParserSourceFacts.SequenceSeparatorKind.LOGICAL_NEWLINE,
                                separatorSpan,
                                expressions.get(followingIndex));
                    }
                }
            }

            TokenOccurrence close = cursor.consume(TokenType.RBRACE, "'}'");
            SourceSpan bodySpan = expressions.isEmpty()
                    ? new SourceSpan(open.span().endOffset(), close.span().startOffset())
                    : new SourceSpan(
                            expressions.get(0).span().startOffset(),
                            expressions.get(expressions.size() - 1).span().endOffset());

            return new SurfaceClosure(
                    parameters,
                    new SurfaceSequence(expressions, bodySpan),
                    false,
                    new SourceSpan(startOffset, close.span().endOffset()));
        }

        SurfaceExpression expression = parseExpressionFoundation();
        return new SurfaceClosure(
                parameters,
                new SurfaceSequence(List.of(expression), expression.span()),
                true,
                new SourceSpan(startOffset, expression.span().endOffset()));
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
                SurfaceObjectItem preceding =
                        items.get(items.size() - 1);
                SourceSpan separatorSpan = consumeSequenceNewlines();
                if (!cursor.at(TokenType.RBRACE)) {
                    int followingIndex = items.size();
                    parseObjectBodyLine(items);
                    recordObjectItemSeparator(
                            preceding,
                            ProtosParserSourceFacts.SequenceSeparatorKind.LOGICAL_NEWLINE,
                            separatorSpan,
                            items.get(followingIndex));
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
            SurfaceObjectItem preceding =
                    items.get(items.size() - 1);
            TokenOccurrence separator = cursor.advance();
            SurfaceObjectItem following = parseObjectBodyItem();
            items.add(following);
            recordObjectItemSeparator(
                    preceding,
                    ProtosParserSourceFacts.SequenceSeparatorKind.SEMICOLON,
                    separator.span(),
                    following);
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
        consumeContinuationNewlines();
        cursor.consume(TokenType.DOT, "'.'");
        consumeContinuationNewlines();
        TokenOccurrence message = consumeMemberName();
        consumeContinuationNewlines();
        cursor.consume(TokenType.LPAREN, "'('");
        consumeNewlines();

        List<SurfaceArgument> arguments = new ArrayList<>();
        if (!cursor.at(TokenType.RPAREN)) {
            arguments.add(parseArgument());

            while (cursor.at(TokenType.COMMA)) {
                cursor.advance();
                consumeNewlines();
                arguments.add(parseArgument());
            }

            consumeNewlines();
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
        consumeContinuationNewlines();
        TokenOccurrence name = consumeMemberName();
        return new SurfaceMember(
                receiver,
                name.token().lexeme(),
                new SourceSpan(receiver.span().startOffset(), name.span().endOffset()));
    }

    private TokenOccurrence consumeMemberName() {
        TokenType type = cursor.current().token().type();
        return switch (type) {
            case IDENTIFIER, THIS, CONTEXT, SUPER, TRUE, FALSE, NULL -> cursor.advance();
            default -> throw ParseError.expected("a member name", cursor.current());
        };
    }

    private SurfaceExpression parseCallSuffix(SurfaceExpression receiver) {
        cursor.consume(TokenType.LPAREN, "'('");
        consumeNewlines();
        List<SurfaceArgument> arguments = new ArrayList<>();

        if (!cursor.at(TokenType.RPAREN)) {
            arguments.add(parseArgument());

            while (cursor.at(TokenType.COMMA)) {
                cursor.advance();
                consumeNewlines();
                arguments.add(parseArgument());
            }

            consumeNewlines();
        }

        TokenOccurrence close = cursor.consume(TokenType.RPAREN, "')'");
        int endOffset = close.span().endOffset();
        SurfaceClosure trailingClosure = null;

        if (cursor.at(TokenType.LBRACE)) {
            trailingClosure =
                    (SurfaceClosure) parseClosureBody(
                            List.of(),
                            cursor.current().span().startOffset());
            arguments.add(new SurfaceArgument(
                    false,
                    trailingClosure,
                    trailingClosure.span()));
            endOffset = trailingClosure.span().endOffset();
        }

        SurfaceCall call = new SurfaceCall(
                receiver,
                arguments,
                new SourceSpan(receiver.span().startOffset(), endOffset));
        if (trailingClosure != null) {
            recordTrailingClosureOrigin(call, trailingClosure);
        }
        return call;
    }

    private SurfaceArgument parseArgument() {
        boolean spread = false;
        int start = cursor.current().span().startOffset();

        if (cursor.at(TokenType.ELLIPSIS)) {
            spread = true;
            start = cursor.advance().span().startOffset();
            consumeContinuationNewlines();
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

    private SourceSpan consumeSequenceNewlines() {
        if (sourceFacts == null) {
            consumeNewlines();
            return null;
        }

        TokenOccurrence first =
                cursor.consume(TokenType.NEWLINE, "a logical newline");
        int endOffset = first.span().endOffset();

        while (cursor.at(TokenType.NEWLINE)) {
            endOffset = cursor.advance().span().endOffset();
        }

        return new SourceSpan(first.span().startOffset(), endOffset);
    }

    private void recordExpressionSeparator(
            ProtosParserSourceFacts.SequenceContext context,
            SurfaceExpression preceding,
            ProtosParserSourceFacts.SequenceSeparatorKind kind,
            SourceSpan separatorSpan,
            SurfaceExpression following) {
        if (sourceFacts != null) {
            sourceFacts.recordExpressionSeparator(
                    context,
                    preceding,
                    kind,
                    separatorSpan,
                    following);
        }
    }

    private void recordObjectItemSeparator(
            SurfaceObjectItem preceding,
            ProtosParserSourceFacts.SequenceSeparatorKind kind,
            SourceSpan separatorSpan,
            SurfaceObjectItem following) {
        if (sourceFacts != null) {
            sourceFacts.recordObjectItemSeparator(
                    preceding,
                    kind,
                    separatorSpan,
                    following);
        }
    }

    private void recordMapEntrySeparator(
            SurfaceMapConstruction.Entry preceding,
            ProtosParserSourceFacts.SequenceSeparatorKind kind,
            SourceSpan separatorSpan,
            SurfaceMapConstruction.Entry following) {
        if (sourceFacts != null) {
            sourceFacts.recordMapEntrySeparator(
                    preceding,
                    kind,
                    separatorSpan,
                    following);
        }
    }

    private void recordSingleParameterClosureForm(
            SurfaceClosure closure,
            ProtosParserSourceFacts.SingleParameterClosureForm form) {
        if (sourceFacts != null) {
            sourceFacts.recordSingleParameterClosureForm(
                    closure,
                    form);
        }
    }

    private void recordTrailingClosureOrigin(
            SurfaceCall call,
            SurfaceClosure closure) {
        if (sourceFacts != null) {
            sourceFacts.recordTrailingClosureOrigin(
                    call,
                    closure);
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
