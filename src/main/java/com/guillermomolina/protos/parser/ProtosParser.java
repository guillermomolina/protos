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
import com.guillermomolina.protos.parser.ast.SurfaceClosure;
import com.guillermomolina.protos.parser.ast.SurfaceExpression;
import com.guillermomolina.protos.parser.ast.SurfaceGroup;
import com.guillermomolina.protos.parser.ast.SurfaceIndex;
import com.guillermomolina.protos.parser.ast.SurfaceIntrinsic;
import com.guillermomolina.protos.parser.ast.SurfaceLiteral;
import com.guillermomolina.protos.parser.ast.SurfaceMatch;
import com.guillermomolina.protos.parser.ast.SurfaceMatchPattern;
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

        SurfaceExpression expression = parseBinaryExpressionFoundation();
        expression = parseMatchSuffixFoundation(expression);
        return parseMutationSuffixFoundation(expression);
    }

private SurfaceExpression parseBinaryExpressionFoundation() {
        SurfaceExpression expression = parseLogicalOrFoundation();

        if (!cursor.at(TokenType.CUSTOM_OPERATOR)) {
            return expression;
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

        return parseCustomBinaryFoundation(expression);
    }

    private SurfaceExpression parseMatchSuffixFoundation(SurfaceExpression subject) {
        if (!atContextualIdentifier("match")) {
            return subject;
        }

        cursor.advance();
        TokenOccurrence open = cursor.consume(TokenType.LBRACE, "'{' after contextual match");
        consumeNewlines();

        List<SurfaceMatch.Arm> arms = new ArrayList<>();
        if (cursor.at(TokenType.RBRACE)) {
            throw ParseError.expected("at least one 'case' match arm", cursor.current());
        }

        parseMatchArmLine(arms);
        while (cursor.at(TokenType.NEWLINE)) {
            consumeNewlines();
            if (!cursor.at(TokenType.RBRACE)) {
                parseMatchArmLine(arms);
            }
        }

        TokenOccurrence close = cursor.consume(TokenType.RBRACE, "'}'");
        return new SurfaceMatch(
                subject,
                arms,
                new SourceSpan(subject.span().startOffset(), close.span().endOffset()));
    }

    private void parseMatchArmLine(List<SurfaceMatch.Arm> arms) {
        addMatchArm(arms, parseMatchArm());

        while (cursor.at(TokenType.SEMICOLON)) {
            cursor.advance();
            addMatchArm(arms, parseMatchArm());
        }
    }

    private void addMatchArm(List<SurfaceMatch.Arm> arms, SurfaceMatch.Arm arm) {
        if (!arms.isEmpty()) {
            SurfaceMatch.Arm previous = arms.get(arms.size() - 1);
            if (previous.guard().isEmpty()
                    && isSyntacticallyIrrefutable(previous.pattern())) {
                throw new ParseError(
                        "A match arm cannot follow an unguarded syntactically irrefutable arm",
                        arm.span());
            }
        }
        arms.add(arm);
    }

    private SurfaceMatch.Arm parseMatchArm() {
        TokenOccurrence caseMarker = consumeContextualIdentifier("case");
        SurfaceMatchPattern pattern = parseMatchPattern();
        validatePatternLinearity(pattern);
        validateDynamicCaptureTerminality(pattern);

        Optional<SurfaceExpression> guard = Optional.empty();
        if (atContextualIdentifier("when")) {
            cursor.advance();
            guard = Optional.of(parseMatchGuardExpression());
        }

        cursor.consume(TokenType.FAT_ARROW, "'=>' after match arm pattern");
        ParsedMatchBody body = parseMatchArmBody();
        return new SurfaceMatch.Arm(
                pattern,
                guard,
                body.body(),
                body.expressionBody(),
                new SourceSpan(caseMarker.span().startOffset(), body.endOffset()));
    }

    private SurfaceExpression parseMatchGuardExpression() {
        List<TokenOccurrence> guardTokens =
                cursor.consumeUntilTopLevel(
                        TokenType.FAT_ARROW,
                        "'=>' terminating match guard");
        SurfaceSequence parsed = new ProtosParser(guardTokens).parseProgram();
        if (parsed.expressions().size() != 1) {
            throw new ParseError(
                    "A match guard must contain exactly one expression",
                    parsed.span());
        }
        return parsed.expressions().get(0);
    }

    private ParsedMatchBody parseMatchArmBody() {
        consumeContinuationNewlines();

        if (cursor.at(TokenType.LBRACE)) {
            TokenOccurrence open = cursor.advance();
            consumeNewlines();
            List<SurfaceExpression> expressions = new ArrayList<>();

            if (!cursor.at(TokenType.RBRACE)) {
                parseExpressionLine(expressions);
                while (cursor.at(TokenType.NEWLINE)) {
                    consumeNewlines();
                    if (!cursor.at(TokenType.RBRACE)) {
                        parseExpressionLine(expressions);
                    }
                }
            }

            TokenOccurrence close = cursor.consume(TokenType.RBRACE, "'}'");
            SourceSpan bodySpan =
                    expressions.isEmpty()
                            ? new SourceSpan(
                                    open.span().endOffset(), close.span().startOffset())
                            : new SourceSpan(
                                    expressions.get(0).span().startOffset(),
                                    expressions.get(expressions.size() - 1).span().endOffset());
            return new ParsedMatchBody(
                    new SurfaceSequence(expressions, bodySpan),
                    false,
                    close.span().endOffset());
        }

        SurfaceExpression expression = parseExpressionFoundation();
        return new ParsedMatchBody(
                new SurfaceSequence(List.of(expression), expression.span()),
                true,
                expression.span().endOffset());
    }

    private SurfaceMatchPattern parseMatchPattern() {
        return parseOrMatchPattern();
    }

    private SurfaceMatchPattern parseOrMatchPattern() {
        List<SurfaceMatchPattern> alternatives = new ArrayList<>();
        SurfaceMatchPattern first = parseAliasedMatchPattern();
        alternatives.add(first);
        boolean irrefutableSeen = isSyntacticallyIrrefutable(first);

        while (atCustomOperator("|")) {
            TokenOccurrence separator = cursor.current();
            if (irrefutableSeen) {
                throw new ParseError(
                        "A pattern alternative cannot follow a syntactically irrefutable alternative",
                        separator.span());
            }
            cursor.advance();
            SurfaceMatchPattern alternative = parseAliasedMatchPattern();
            alternatives.add(alternative);
            irrefutableSeen = isSyntacticallyIrrefutable(alternative);
        }

        if (alternatives.size() == 1) {
            return first;
        }

        SurfaceMatchPattern.Or pattern =
                new SurfaceMatchPattern.Or(
                        alternatives,
                        new SourceSpan(
                                first.span().startOffset(),
                                alternatives.get(alternatives.size() - 1).span().endOffset()));
        validateFixedOrBindingInterface(pattern);
        return pattern;
    }

    private SurfaceMatchPattern parseAliasedMatchPattern() {
        if (!atCustomOperator("@")) {
            return parsePrimaryMatchPattern();
        }

        TokenOccurrence at = cursor.advance();
        TokenOccurrence name = cursor.consume(TokenType.IDENTIFIER, "a pattern binder name");
        SurfaceMatchPattern binder =
                new SurfaceMatchPattern.Binder(
                        name.token().lexeme(),
                        new SourceSpan(at.span().startOffset(), name.span().endOffset()));

        if (!cursor.at(TokenType.COLON)) {
            return binder;
        }

        cursor.advance();
        SurfaceMatchPattern nested = parseAliasedMatchPattern();
        return new SurfaceMatchPattern.Alias(
                name.token().lexeme(),
                nested,
                new SourceSpan(at.span().startOffset(), nested.span().endOffset()));
    }

    private SurfaceMatchPattern parsePrimaryMatchPattern() {
        if (atCustomOperator("@")) {
            TokenOccurrence at = cursor.advance();
            TokenOccurrence name =
                    cursor.consume(TokenType.IDENTIFIER, "a pattern binder name");
            if (cursor.at(TokenType.COLON)) {
                throw new ParseError(
                        "A binder alias is not a primary pattern here; parenthesize the alias",
                        cursor.current().span());
            }
            return new SurfaceMatchPattern.Binder(
                    name.token().lexeme(),
                    new SourceSpan(at.span().startOffset(), name.span().endOffset()));
        }

        if (atContextualIdentifier("_")) {
            TokenOccurrence wildcard = cursor.advance();
            return new SurfaceMatchPattern.Wildcard(wildcard.span());
        }

        if (cursor.at(TokenType.LBRACKET)) {
            return parseArrayMatchPattern();
        }

        if (atContextualIdentifier("exact") && cursor.nextAt(TokenType.PERCENT)) {
            TokenOccurrence exact = cursor.advance();
            return parseMapMatchPattern(true, exact.span().startOffset());
        }

        if (cursor.at(TokenType.PERCENT)) {
            return parseMapMatchPattern(false, cursor.current().span().startOffset());
        }

        if (cursor.at(TokenType.LPAREN)) {
            TokenOccurrence open = cursor.advance();
            consumeNewlines();
            SurfaceMatchPattern nested = parseMatchPattern();
            consumeNewlines();
            TokenOccurrence close = cursor.consume(TokenType.RPAREN, "')'");
            return new SurfaceMatchPattern.Group(
                    nested,
                    new SourceSpan(open.span().startOffset(), close.span().endOffset()));
        }

        return parseMatcherValuePattern();
    }

    private SurfaceMatchPattern parseMatcherValuePattern() {
        SurfaceExpression matcher = parseMatcherValueExpression();
        Optional<SurfaceMatchPattern.CaptureInterface> captureInterface =
                Optional.empty();

        if (atContextualIdentifier("captures")) {
            captureInterface = Optional.of(parseCaptureInterface());
        }

        int endOffset =
                captureInterface
                        .map(value -> value.span().endOffset())
                        .orElse(matcher.span().endOffset());
        return new SurfaceMatchPattern.Value(
                matcher,
                captureInterface,
                new SourceSpan(matcher.span().startOffset(), endOffset));
    }

    private SurfaceExpression parseMatcherValueExpression() {
        TokenOccurrence token = cursor.current();
        SurfaceExpression expression =
                switch (token.token().type()) {
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
                    default ->
                            throw ParseError.expected(
                                    "a matcher value, binder, wildcard, Array/Map pattern, or grouped pattern",
                                    token);
                };

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
            return expression;
        }
    }

    private SurfaceMatchPattern parseArrayMatchPattern() {
        TokenOccurrence open = cursor.consume(TokenType.LBRACKET, "'['");
        consumeNewlines();

        List<SurfaceMatchPattern> prefix = new ArrayList<>();
        List<SurfaceMatchPattern> suffix = new ArrayList<>();
        Optional<SurfaceMatchPattern.Remainder> remainder = Optional.empty();
        boolean afterRemainder = false;

        if (!cursor.at(TokenType.RBRACKET)) {
            while (true) {
                if (cursor.at(TokenType.ELLIPSIS)) {
                    if (remainder.isPresent()) {
                        throw new ParseError(
                                "An Array pattern may contain at most one remainder",
                                cursor.current().span());
                    }
                    remainder = Optional.of(parsePatternRemainder());
                    afterRemainder = true;
                } else {
                    SurfaceMatchPattern item = parseMatchPattern();
                    if (afterRemainder) {
                        suffix.add(item);
                    } else {
                        prefix.add(item);
                    }
                }

                if (!cursor.at(TokenType.COMMA)) {
                    break;
                }
                cursor.advance();
                consumeNewlines();
                if (cursor.at(TokenType.RBRACKET)) {
                    throw ParseError.expected(
                            "an Array pattern item after ','", cursor.current());
                }
            }
        }

        consumeNewlines();
        TokenOccurrence close = cursor.consume(TokenType.RBRACKET, "']'");
        return new SurfaceMatchPattern.ArrayPattern(
                prefix,
                remainder,
                suffix,
                new SourceSpan(open.span().startOffset(), close.span().endOffset()));
    }

    private SurfaceMatchPattern parseMapMatchPattern(boolean exact, int startOffset) {
        cursor.consume(TokenType.PERCENT, "'%'");
        cursor.consume(TokenType.LBRACE, "'{' after '%' in Map pattern");
        consumeNewlines();

        List<SurfaceMatchPattern.MapEntry> entries = new ArrayList<>();
        Optional<SurfaceMatchPattern.Remainder> remainder = Optional.empty();

        if (!cursor.at(TokenType.RBRACE)) {
            while (true) {
                if (cursor.at(TokenType.ELLIPSIS)) {
                    remainder = Optional.of(parsePatternRemainder());
                    if (cursor.at(TokenType.COMMA)) {
                        throw new ParseError(
                                "A Map pattern remainder must be final",
                                cursor.current().span());
                    }
                    break;
                }

                SurfaceExpression key = parseBinaryExpressionFoundation();
                cursor.consume(TokenType.COLON, "':' after Map pattern key");
                SurfaceMatchPattern valuePattern = parseMatchPattern();
                entries.add(
                        new SurfaceMatchPattern.MapEntry(
                                key,
                                valuePattern,
                                new SourceSpan(
                                        key.span().startOffset(),
                                        valuePattern.span().endOffset())));

                if (!cursor.at(TokenType.COMMA)) {
                    break;
                }
                cursor.advance();
                consumeNewlines();
                if (cursor.at(TokenType.RBRACE)) {
                    throw ParseError.expected(
                            "a Map pattern entry or remainder after ','",
                            cursor.current());
                }
            }
        }

        consumeNewlines();
        TokenOccurrence close = cursor.consume(TokenType.RBRACE, "'}'");
        if (exact && remainder.isPresent()) {
            throw new ParseError(
                    "An exact Map pattern cannot contain a remainder",
                    remainder.orElseThrow().span());
        }
        return new SurfaceMatchPattern.MapPattern(
                exact,
                entries,
                remainder,
                new SourceSpan(startOffset, close.span().endOffset()));
    }

    private SurfaceMatchPattern.Remainder parsePatternRemainder() {
        TokenOccurrence spread = cursor.consume(TokenType.ELLIPSIS, "'...'");
        Optional<SurfaceMatchPattern> nested = Optional.empty();
        int endOffset = spread.span().endOffset();

        if (startsPrimaryMatchPattern()) {
            SurfaceMatchPattern pattern = parsePrimaryMatchPattern();
            nested = Optional.of(pattern);
            endOffset = pattern.span().endOffset();
        }

        return new SurfaceMatchPattern.Remainder(
                nested,
                new SourceSpan(spread.span().startOffset(), endOffset));
    }

    private boolean startsPrimaryMatchPattern() {
        if (cursor.at(TokenType.LBRACKET)
                || cursor.at(TokenType.LPAREN)
                || cursor.at(TokenType.PERCENT)
                || cursor.at(TokenType.NUMBER)
                || cursor.at(TokenType.STRING)
                || cursor.at(TokenType.TRUE)
                || cursor.at(TokenType.FALSE)
                || cursor.at(TokenType.NULL)
                || cursor.at(TokenType.THIS)
                || cursor.at(TokenType.CONTEXT)
                || cursor.at(TokenType.ARGS)
                || atCustomOperator("@")) {
            return true;
        }
        return cursor.at(TokenType.IDENTIFIER);
    }

    private SurfaceMatchPattern.CaptureInterface parseCaptureInterface() {
        TokenOccurrence captures = consumeContextualIdentifier("captures");
        cursor.consume(TokenType.LPAREN, "'(' after contextual captures");
        consumeNewlines();

        List<String> requiredNames = new ArrayList<>();
        Optional<String> restName = Optional.empty();
        Set<String> names = new HashSet<>();

        if (cursor.at(TokenType.RPAREN)) {
            throw ParseError.expected(
                    "at least one capture binding in captures(...)",
                    cursor.current());
        }

        if (cursor.at(TokenType.ELLIPSIS)) {
            cursor.advance();
            TokenOccurrence rest =
                    cursor.consume(TokenType.IDENTIFIER, "a capture rest name");
            addUniquePatternBindingName(names, rest);
            restName = Optional.of(rest.token().lexeme());
        } else {
            TokenOccurrence required =
                    cursor.consume(TokenType.IDENTIFIER, "a capture binding name");
            addUniquePatternBindingName(names, required);
            requiredNames.add(required.token().lexeme());

            while (cursor.at(TokenType.COMMA)) {
                cursor.advance();
                consumeNewlines();
                if (cursor.at(TokenType.ELLIPSIS)) {
                    cursor.advance();
                    TokenOccurrence rest =
                            cursor.consume(TokenType.IDENTIFIER, "a capture rest name");
                    addUniquePatternBindingName(names, rest);
                    restName = Optional.of(rest.token().lexeme());
                    break;
                }

                TokenOccurrence next =
                        cursor.consume(TokenType.IDENTIFIER, "a capture binding name");
                addUniquePatternBindingName(names, next);
                requiredNames.add(next.token().lexeme());
            }
        }

        consumeNewlines();
        TokenOccurrence close = cursor.consume(TokenType.RPAREN, "')'");
        return new SurfaceMatchPattern.CaptureInterface(
                requiredNames,
                restName,
                new SourceSpan(captures.span().startOffset(), close.span().endOffset()));
    }

    private void addUniquePatternBindingName(
            Set<String> names, TokenOccurrence name) {
        if (!names.add(name.token().lexeme())) {
            throw new ParseError(
                    "Pattern binding names must be unique and linear",
                    name.span());
        }
    }

    private void validatePatternLinearity(SurfaceMatchPattern pattern) {
        declaredNamesWithValidation(pattern);
    }

    private void validateDynamicCaptureTerminality(
            SurfaceMatchPattern pattern) {
        bindingShape(pattern);
    }

    private PatternBindingShape bindingShape(SurfaceMatchPattern pattern) {
        if (pattern instanceof SurfaceMatchPattern.Binder) {
            return new PatternBindingShape(true, false);
        }
        if (pattern instanceof SurfaceMatchPattern.Wildcard) {
            return new PatternBindingShape(false, false);
        }
        if (pattern instanceof SurfaceMatchPattern.Value value) {
            if (value.captureInterface().isEmpty()) {
                return new PatternBindingShape(false, false);
            }
            SurfaceMatchPattern.CaptureInterface capture =
                    value.captureInterface().orElseThrow();
            return new PatternBindingShape(
                    !capture.declaredNames().isEmpty(),
                    capture.variableArity());
        }
        if (pattern instanceof SurfaceMatchPattern.Group group) {
            return bindingShape(group.pattern());
        }
        if (pattern instanceof SurfaceMatchPattern.Alias alias) {
            PatternBindingShape nested = bindingShape(alias.pattern());
            return new PatternBindingShape(true, nested.dynamicTail());
        }
        if (pattern instanceof SurfaceMatchPattern.Or orPattern) {
            boolean hasBindings = false;
            boolean dynamicTail = false;
            for (SurfaceMatchPattern alternative : orPattern.alternatives()) {
                PatternBindingShape alternativeShape = bindingShape(alternative);
                hasBindings |= alternativeShape.hasBindings();
                dynamicTail |= alternativeShape.dynamicTail();
            }
            return new PatternBindingShape(hasBindings, dynamicTail);
        }
        if (pattern instanceof SurfaceMatchPattern.ArrayPattern array) {
            ArrayList<SurfaceMatchPattern> ordered = new ArrayList<>();
            ordered.addAll(array.prefix());
            array.remainder()
                    .flatMap(SurfaceMatchPattern.Remainder::pattern)
                    .ifPresent(ordered::add);
            ordered.addAll(array.suffix());
            return sequentialBindingShape(ordered);
        }
        if (pattern instanceof SurfaceMatchPattern.MapPattern map) {
            ArrayList<SurfaceMatchPattern> ordered = new ArrayList<>();
            for (SurfaceMatchPattern.MapEntry entry : map.entries()) {
                ordered.add(entry.valuePattern());
            }
            map.remainder()
                    .flatMap(SurfaceMatchPattern.Remainder::pattern)
                    .ifPresent(ordered::add);
            return sequentialBindingShape(ordered);
        }

        throw new IllegalStateException(
                "Unknown SurfaceMatchPattern: " + pattern.getClass().getName());
    }

    private PatternBindingShape sequentialBindingShape(
            List<SurfaceMatchPattern> ordered) {
        boolean hasBindings = false;
        boolean dynamicTail = false;

        for (SurfaceMatchPattern nested : ordered) {
            PatternBindingShape nestedShape = bindingShape(nested);
            if (dynamicTail && nestedShape.hasBindings()) {
                throw new ParseError(
                        "A variable-arity capture segment must be terminal in the final ordered arm-binding interface",
                        nested.span());
            }
            hasBindings |= nestedShape.hasBindings();
            dynamicTail |= nestedShape.dynamicTail();
        }

        return new PatternBindingShape(hasBindings, dynamicTail);
    }

    private Set<String> declaredNamesWithValidation(SurfaceMatchPattern pattern) {
        if (pattern instanceof SurfaceMatchPattern.Binder binder) {
            return new HashSet<>(Set.of(binder.name()));
        }

        if (pattern instanceof SurfaceMatchPattern.Wildcard) {
            return new HashSet<>();
        }

        if (pattern instanceof SurfaceMatchPattern.Value value) {
            return new HashSet<>(
                    value.captureInterface()
                            .map(SurfaceMatchPattern.CaptureInterface::declaredNames)
                            .orElse(List.of()));
        }

        if (pattern instanceof SurfaceMatchPattern.Group group) {
            return declaredNamesWithValidation(group.pattern());
        }

        if (pattern instanceof SurfaceMatchPattern.Alias alias) {
            Set<String> names = declaredNamesWithValidation(alias.pattern());
            if (!names.add(alias.name())) {
                throw new ParseError(
                        "Pattern binding names must be unique and linear",
                        alias.span());
            }
            return names;
        }

        if (pattern instanceof SurfaceMatchPattern.Or orPattern) {
            Set<String> union = new HashSet<>();
            for (SurfaceMatchPattern alternative : orPattern.alternatives()) {
                union.addAll(declaredNamesWithValidation(alternative));
            }
            return union;
        }

        if (pattern instanceof SurfaceMatchPattern.ArrayPattern array) {
            Set<String> names = new HashSet<>();
            for (SurfaceMatchPattern item : array.prefix()) {
                mergeSequentialPatternNames(names, item);
            }
            array.remainder()
                    .flatMap(SurfaceMatchPattern.Remainder::pattern)
                    .ifPresent(item -> mergeSequentialPatternNames(names, item));
            for (SurfaceMatchPattern item : array.suffix()) {
                mergeSequentialPatternNames(names, item);
            }
            return names;
        }

        if (pattern instanceof SurfaceMatchPattern.MapPattern map) {
            Set<String> names = new HashSet<>();
            for (SurfaceMatchPattern.MapEntry entry : map.entries()) {
                mergeSequentialPatternNames(names, entry.valuePattern());
            }
            map.remainder()
                    .flatMap(SurfaceMatchPattern.Remainder::pattern)
                    .ifPresent(item -> mergeSequentialPatternNames(names, item));
            return names;
        }

        throw new IllegalStateException(
                "Unknown SurfaceMatchPattern: " + pattern.getClass().getName());
    }

    private void mergeSequentialPatternNames(
            Set<String> accumulated, SurfaceMatchPattern nested) {
        Set<String> nestedNames = declaredNamesWithValidation(nested);
        for (String name : nestedNames) {
            if (!accumulated.add(name)) {
                throw new ParseError(
                        "Pattern binding names must be unique and linear",
                        nested.span());
            }
        }
    }

    private Optional<List<String>> fixedBindingInterface(
            SurfaceMatchPattern pattern) {
        if (pattern instanceof SurfaceMatchPattern.Binder binder) {
            return Optional.of(List.of(binder.name()));
        }
        if (pattern instanceof SurfaceMatchPattern.Wildcard) {
            return Optional.of(List.of());
        }
        if (pattern instanceof SurfaceMatchPattern.Value value) {
            if (value.captureInterface().isEmpty()) {
                return Optional.of(List.of());
            }
            SurfaceMatchPattern.CaptureInterface capture =
                    value.captureInterface().orElseThrow();
            return capture.variableArity()
                    ? Optional.empty()
                    : Optional.of(capture.requiredNames());
        }
        if (pattern instanceof SurfaceMatchPattern.Group group) {
            return fixedBindingInterface(group.pattern());
        }
        if (pattern instanceof SurfaceMatchPattern.Alias alias) {
            Optional<List<String>> nested = fixedBindingInterface(alias.pattern());
            if (nested.isEmpty()) {
                return Optional.empty();
            }
            ArrayList<String> names = new ArrayList<>();
            names.add(alias.name());
            names.addAll(nested.orElseThrow());
            return Optional.of(List.copyOf(names));
        }
        if (pattern instanceof SurfaceMatchPattern.Or orPattern) {
            List<String> common = null;
            for (SurfaceMatchPattern alternative : orPattern.alternatives()) {
                Optional<List<String>> candidate = fixedBindingInterface(alternative);
                if (candidate.isEmpty()) {
                    return Optional.empty();
                }
                if (common == null) {
                    common = candidate.orElseThrow();
                } else if (!common.equals(candidate.orElseThrow())) {
                    throw new ParseError(
                            "Fixed OR alternatives must expose the same ordered binder-name interface",
                            orPattern.span());
                }
            }
            return Optional.of(common == null ? List.of() : common);
        }
        if (pattern instanceof SurfaceMatchPattern.ArrayPattern array) {
            ArrayList<String> names = new ArrayList<>();
            for (SurfaceMatchPattern item : array.prefix()) {
                if (!appendFixedBindingInterface(names, item)) {
                    return Optional.empty();
                }
            }
            if (array.remainder().flatMap(SurfaceMatchPattern.Remainder::pattern).isPresent()
                    && !appendFixedBindingInterface(
                            names,
                            array.remainder()
                                    .flatMap(SurfaceMatchPattern.Remainder::pattern)
                                    .orElseThrow())) {
                return Optional.empty();
            }
            for (SurfaceMatchPattern item : array.suffix()) {
                if (!appendFixedBindingInterface(names, item)) {
                    return Optional.empty();
                }
            }
            return Optional.of(List.copyOf(names));
        }
        if (pattern instanceof SurfaceMatchPattern.MapPattern map) {
            ArrayList<String> names = new ArrayList<>();
            for (SurfaceMatchPattern.MapEntry entry : map.entries()) {
                if (!appendFixedBindingInterface(names, entry.valuePattern())) {
                    return Optional.empty();
                }
            }
            if (map.remainder().flatMap(SurfaceMatchPattern.Remainder::pattern).isPresent()
                    && !appendFixedBindingInterface(
                            names,
                            map.remainder()
                                    .flatMap(SurfaceMatchPattern.Remainder::pattern)
                                    .orElseThrow())) {
                return Optional.empty();
            }
            return Optional.of(List.copyOf(names));
        }
        throw new IllegalStateException(
                "Unknown SurfaceMatchPattern: " + pattern.getClass().getName());
    }

    private boolean appendFixedBindingInterface(
            List<String> accumulated, SurfaceMatchPattern nested) {
        Optional<List<String>> names = fixedBindingInterface(nested);
        if (names.isEmpty()) {
            return false;
        }
        accumulated.addAll(names.orElseThrow());
        return true;
    }

    private void validateFixedOrBindingInterface(
            SurfaceMatchPattern.Or pattern) {
        fixedBindingInterface(pattern);
    }

    private boolean isSyntacticallyIrrefutable(SurfaceMatchPattern pattern) {
        if (pattern instanceof SurfaceMatchPattern.Binder
                || pattern instanceof SurfaceMatchPattern.Wildcard) {
            return true;
        }
        if (pattern instanceof SurfaceMatchPattern.Alias alias) {
            return isSyntacticallyIrrefutable(alias.pattern());
        }
        if (pattern instanceof SurfaceMatchPattern.Group group) {
            return isSyntacticallyIrrefutable(group.pattern());
        }
        if (pattern instanceof SurfaceMatchPattern.Or orPattern) {
            return orPattern.alternatives().stream()
                    .anyMatch(this::isSyntacticallyIrrefutable);
        }
        return false;
    }

    private boolean atContextualIdentifier(String spelling) {
        return cursor.at(TokenType.IDENTIFIER)
                && cursor.current().token().lexeme().equals(spelling);
    }

    private TokenOccurrence consumeContextualIdentifier(String spelling) {
        if (!atContextualIdentifier(spelling)) {
            throw ParseError.expected(
                    "'" + spelling + "'",
                    cursor.current());
        }
        return cursor.advance();
    }

    private boolean atCustomOperator(String spelling) {
        return cursor.at(TokenType.CUSTOM_OPERATOR)
                && cursor.current().token().lexeme().equals(spelling);
    }

    private record PatternBindingShape(
            boolean hasBindings,
            boolean dynamicTail) {}

    private record ParsedMatchBody(
            SurfaceSequence body,
            boolean expressionBody,
            int endOffset) {}

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
                if (cursor.nextAt(TokenType.FAT_ARROW)) {
                    yield parseBareParameterClosure();
                }
                cursor.advance();
                yield new SurfaceName(token.token().lexeme(), token.span());
            }
            case THIS -> intrinsic(SurfaceIntrinsic.Kind.THIS);
            case CONTEXT -> intrinsic(SurfaceIntrinsic.Kind.CONTEXT);
            case ARGS -> intrinsic(SurfaceIntrinsic.Kind.ARGS);
            case SUPER -> parseSuperMessageSend();
            case LBRACE -> parseObjectBody(null);
            case LPAREN -> cursor.matchingParenthesisFollowedBy(TokenType.FAT_ARROW)
                    ? parseParameterListClosure()
                    : parseParenthesized();
            default -> throw ParseError.expected("a primary expression", token);
        };
    }

    private SurfaceExpression parseBareParameterClosure() {
        TokenOccurrence parameter = cursor.consume(TokenType.IDENTIFIER, "a closure parameter");
        SurfaceParameter surfaceParameter = new SurfaceParameter(
                parameter.token().lexeme(),
                Optional.empty(),
                false,
                parameter.span());
        cursor.consume(TokenType.FAT_ARROW, "'=>'");
        return parseClosureBody(List.of(surfaceParameter), parameter.span().startOffset());
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
        return parseClosureBody(parameters, open.span().startOffset());
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
                parseExpressionLine(expressions);

                while (cursor.at(TokenType.NEWLINE)) {
                    consumeNewlines();
                    if (!cursor.at(TokenType.RBRACE)) {
                        parseExpressionLine(expressions);
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

            while (cursor.at(TokenType.COMMA)) {
                cursor.advance();
                consumeNewlines();
                arguments.add(parseArgument());
            }

            consumeNewlines();
        }

        TokenOccurrence close = cursor.consume(TokenType.RPAREN, "')'");
        int endOffset = close.span().endOffset();

        if (cursor.at(TokenType.LBRACE)) {
            SurfaceClosure trailingClosure = (SurfaceClosure) parseClosureBody(
                    List.of(),
                    cursor.current().span().startOffset());
            arguments.add(new SurfaceArgument(
                    false,
                    trailingClosure,
                    trailingClosure.span()));
            endOffset = trailingClosure.span().endOffset();
        }

        return new SurfaceCall(
                receiver,
                arguments,
                new SourceSpan(receiver.span().startOffset(), endOffset));
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

    private SourceSpan sequenceSpan(List<SurfaceExpression> expressions) {
        if (expressions.isEmpty()) {
            return cursor.current().span();
        }
        return new SourceSpan(
                expressions.get(0).span().startOffset(),
                expressions.get(expressions.size() - 1).span().endOffset());
    }
}
