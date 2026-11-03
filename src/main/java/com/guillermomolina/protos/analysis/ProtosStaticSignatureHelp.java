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
package com.guillermomolina.protos.analysis;

import com.guillermomolina.protos.lexer.ProtosLexer;
import com.guillermomolina.protos.lexer.TokenOccurrence;
import com.guillermomolina.protos.lexer.TokenType;
import com.guillermomolina.protos.parser.ParseError;
import com.guillermomolina.protos.parser.ProtosParser;
import com.guillermomolina.protos.parser.ast.SurfaceArgument;
import com.guillermomolina.protos.parser.ast.SurfaceCall;
import com.guillermomolina.protos.parser.ast.SurfaceClosure;
import com.guillermomolina.protos.parser.ast.SurfaceExpression;
import com.guillermomolina.protos.parser.ast.SurfaceGroup;
import com.guillermomolina.protos.parser.ast.SurfaceName;
import com.guillermomolina.protos.parser.ast.SurfaceParameter;
import com.guillermomolina.protos.parser.ast.SurfaceSequence;
import com.guillermomolina.protos.source.SourceSpan;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * LM010-C static signature help over one exact document snapshot.
 *
 * <p>Only a {@link SurfaceCall} whose receiver, after removing
 * {@link SurfaceGroup} wrappers and nothing else, is a literal
 * {@link SurfaceClosure} has a proven callable identity. Every other call,
 * including a call through a name bound to a Closure, a member send, a
 * {@code super} send, or a computed receiver, yields nothing: no value flow,
 * assignment tracking, or type information is consulted.</p>
 *
 * <p>The active call is located structurally with the real lexer. Among the
 * delimiters opened before the cursor and not yet closed, the innermost one
 * must be the {@code (} of a call argument list; a cursor directly inside a
 * group, Closure parameter list, Array, Object/Closure body, or Map
 * construction yields nothing, and an inner call without a proven callable is
 * never replaced by an outer one. The active argument is the number of
 * {@code COMMA} tokens at that list's own nesting level before the cursor.</p>
 *
 * <p>The canonical parser rejects an unclosed argument list and an argument
 * missing after a comma. When the unmodified snapshot does not parse, exactly
 * one local transient repair is attempted, never retained or published:</p>
 *
 * <ul>
 *   <li>an empty argument slot (after {@code (} or {@code ,}, or after the
 *       {@code label:} of a named argument, before {@code ,} or {@code )})
 *       receives one synthetic identifier absent from the snapshot; or</li>
 *   <li>when only logical newlines and trivia follow the cursor, the slot is
 *       filled if empty and every still-open delimiter is closed at the
 *       cursor.</li>
 * </ul>
 *
 * <p>The repaired text must lex to exactly the original tokens plus the
 * inserted ones, must parse with the real parser, and must produce the same
 * call, with argument-list commas lying exactly between its parsed
 * arguments and the synthetic identifier as the active argument, carrying
 * exactly the expected named label or none. Any other
 * failure, including an unrelated error elsewhere, yields nothing.</p>
 */
final class ProtosStaticSignatureHelp {
    private static final String SYNTHETIC_BASE = "protosSignatureArgument";

    private ProtosStaticSignatureHelp() {
    }

    static Optional<ProtosStaticSignatureHelpResult> resolve(
            ProtosDocumentSnapshot snapshot,
            int sourceOffset) {
        Objects.requireNonNull(snapshot, "snapshot");
        String source = snapshot.characters();
        if (sourceOffset < 0
                || sourceOffset > source.length()
                || ProtosStaticCompletion.splitsSurrogatePair(source, sourceOffset)) {
            return Optional.empty();
        }

        List<TokenOccurrence> tokens;
        List<ProtosLexer.TriviaOccurrence> trivia = new ArrayList<>();
        try {
            tokens = new ProtosLexer(source).tokenizeOccurrencesWithTrivia(trivia::add);
        } catch (ProtosLexer.LexicalError invalid) {
            return Optional.empty();
        }
        for (ProtosLexer.TriviaOccurrence occurrence : trivia) {
            if (ProtosStaticCompletion.insideComment(occurrence, sourceOffset)) {
                return Optional.empty();
            }
        }

        // Tokens starting before the cursor; a token straddling the cursor
        // (for example a String literal argument) belongs to the argument.
        int before = 0;
        boolean straddled = false;
        Deque<Integer> open = new ArrayDeque<>();
        for (TokenOccurrence token : tokens) {
            SourceSpan span = token.span();
            if (span.startOffset() >= sourceOffset) {
                break;
            }
            straddled = sourceOffset < span.endOffset();
            TokenType type = token.token().type();
            if (isOpener(type)) {
                open.push(before);
            } else if (isCloser(type)) {
                if (open.isEmpty() || closerOf(tokens.get(open.pop()).token().type()) != type) {
                    return Optional.empty();
                }
            }
            before++;
        }
        if (open.isEmpty() || tokens.get(open.peek()).token().type() != TokenType.LPAREN) {
            return Optional.empty();
        }
        int argumentListOpen = open.peek();
        if (argumentListOpen == 0
                || tokens.get(argumentListOpen - 1).token().type() == TokenType.NEWLINE) {
            return Optional.empty();
        }
        int activeArgument = listLevelCommas(tokens, argumentListOpen, before).size();

        List<TokenOccurrence> parsedTokens = tokens;
        String synthetic = null;
        Repair repair = null;
        SurfaceSequence program = parse(source);
        if (program == null) {
            if (straddled) {
                return Optional.empty();
            }
            repair = repair(source, tokens, before, open);
            if (repair == null) {
                return Optional.empty();
            }
            String repaired = source.substring(0, sourceOffset)
                    + repair.text()
                    + source.substring(sourceOffset);
            try {
                parsedTokens = new ProtosLexer(repaired).tokenizeOccurrences();
            } catch (ProtosLexer.LexicalError invalid) {
                return Optional.empty();
            }
            if (!preservesOriginalTokens(tokens, before, repair, sourceOffset, parsedTokens)) {
                return Optional.empty();
            }
            program = parse(repaired);
            if (program == null) {
                return Optional.empty();
            }
            synthetic = repair.synthetic();
        }

        SourceSpan receiverEnd = tokens.get(argumentListOpen - 1).span();
        List<SurfaceCall> calls = new ArrayList<>();
        collectCalls(program, receiverEnd.endOffset(), calls);
        if (calls.size() != 1) {
            return Optional.empty();
        }
        SurfaceCall call = calls.get(0);
        SurfaceExpression callee = call.receiver();
        while (callee instanceof SurfaceGroup group) {
            callee = group.expression();
        }
        if (!(callee instanceof SurfaceClosure closure)) {
            return Optional.empty();
        }

        List<SurfaceArgument> listArguments =
                verifiedListArguments(call, parsedTokens, argumentListOpen);
        if (listArguments == null
                || activeArgument >= Math.max(listArguments.size(), 1)) {
            return Optional.empty();
        }
        if (synthetic != null) {
            SurfaceArgument filled = listArguments.get(activeArgument);
            if (filled.spread()
                    || !filled.name().equals(Optional.ofNullable(repair.label()))
                    || !(filled.expression() instanceof SurfaceName name)
                    || !name.name().equals(synthetic)
                    || !name.span().equals(new SourceSpan(
                            sourceOffset, sourceOffset + synthetic.length()))) {
                return Optional.empty();
            }
        }

        List<ProtosStaticSignatureHelpResult.Parameter> parameters = new ArrayList<>();
        for (SurfaceParameter parameter : closure.parameters()) {
            parameters.add(new ProtosStaticSignatureHelpResult.Parameter(
                    parameter.name(),
                    source.substring(
                            parameter.span().startOffset(),
                            parameter.span().endOffset()),
                    parameter.defaultValue().isPresent(),
                    parameter.rest(),
                    parameter.span()));
        }
        return Optional.of(new ProtosStaticSignatureHelpResult(
                snapshot,
                closure.span(),
                tokens.get(argumentListOpen).span().startOffset(),
                parameters,
                activeArgument,
                activeParameter(
                        closure.parameters(),
                        call.arguments(),
                        listArguments.size(),
                        activeArgument)));
    }

    /**
     * D237 MIX-C association per CALLABLES.md from source structure alone.
     *
     * <p>{@code arguments} is the complete parsed argument sequence: the
     * parenthesized arguments followed by an attached trailing Closure, which
     * is one final ordinary positional argument and never shifts the source
     * index of a list argument.</p>
     *
     * <ul>
     *   <li>A named argument selects the non-rest formal with its literal
     *       label, only when no positional value can fill that formal: an
     *       ordinary positional item at or before its ordinal is a proven
     *       collision, and a spread anywhere in the packet is a possible one.
     *       An unknown label or a rest-formal label selects nothing.</li>
     *   <li>An ordinary positional argument's ordinal counts only the
     *       positional values before it; named items contribute none and a
     *       spread contributes an unknown nonnegative number. Defaults never
     *       count. After a spread, a formal is selected only when every spread
     *       length selects it: the minimum ordinal already reaches a trailing
     *       rest parameter. A formal also named by a named argument is a
     *       collision and selects nothing.</li>
     *   <li>A spread argument itself has no unique formal.</li>
     * </ul>
     */
    private static OptionalInt activeParameter(
            List<SurfaceParameter> parameters,
            List<SurfaceArgument> arguments,
            int listArgumentCount,
            int activeArgument) {
        if (listArgumentCount == 0) {
            // An empty argument list: the cursor sits at the first position.
            return parameters.isEmpty() ? OptionalInt.empty() : OptionalInt.of(0);
        }
        int fixed = parameters.size();
        boolean hasRest = fixed > 0 && parameters.get(fixed - 1).rest();
        if (hasRest) {
            fixed--;
        }
        SurfaceArgument active = arguments.get(activeArgument);
        if (active.spread()) {
            return OptionalInt.empty();
        }

        if (active.name().isPresent()) {
            int formal = fixedFormal(parameters, fixed, active.name().get());
            if (formal < 0) {
                return OptionalInt.empty();
            }
            int positional = 0;
            for (SurfaceArgument argument : arguments) {
                if (argument.spread()) {
                    return OptionalInt.empty();
                }
                if (argument.name().isEmpty()) {
                    positional++;
                }
            }
            return positional > formal ? OptionalInt.empty() : OptionalInt.of(formal);
        }

        int ordinal = 0;
        boolean afterSpread = false;
        for (int index = 0; index < activeArgument; index++) {
            SurfaceArgument argument = arguments.get(index);
            if (argument.spread()) {
                afterSpread = true;
            } else if (argument.name().isEmpty()) {
                ordinal++;
            }
        }
        if (ordinal >= fixed) {
            return hasRest ? OptionalInt.of(fixed) : OptionalInt.empty();
        }
        if (afterSpread) {
            return OptionalInt.empty();
        }
        String formalName = parameters.get(ordinal).name();
        for (SurfaceArgument argument : arguments) {
            if (argument.name().isPresent() && argument.name().get().equals(formalName)) {
                return OptionalInt.empty();
            }
        }
        return OptionalInt.of(ordinal);
    }

    /** The index of the non-rest formal declared with {@code label}, or -1. */
    private static int fixedFormal(List<SurfaceParameter> parameters, int fixed, String label) {
        for (int index = 0; index < fixed; index++) {
            if (parameters.get(index).name().equals(label)) {
                return index;
            }
        }
        return -1;
    }

    /**
     * One transient insertion at the cursor; {@code synthetic} is the
     * identifier filling the active argument slot, or {@code null} when the
     * slot already holds an argument. {@code label} is the named-argument
     * label whose empty value the synthetic identifier fills, or
     * {@code null} for an ordinary positional slot.
     */
    private record Repair(
            String text,
            String synthetic,
            String label,
            List<TokenType> insertedTypes) {
    }

    private static Repair repair(
            String source,
            List<TokenOccurrence> tokens,
            int before,
            Deque<Integer> open) {
        TokenType previous = significantType(tokens, before - 1, -1);
        TokenType next = significantType(tokens, before, 1);
        String label = previous == TokenType.COLON ? namedValueLabel(tokens, before) : null;
        if (previous == TokenType.COLON && label == null) {
            return null;
        }
        boolean afterSeparator = previous == TokenType.COMMA || label != null;
        String synthetic = syntheticName(source);

        if (next == TokenType.RPAREN || next == TokenType.COMMA) {
            if (afterSeparator || (previous == TokenType.LPAREN && next == TokenType.COMMA)) {
                return new Repair(synthetic, synthetic, label, List.of(TokenType.IDENTIFIER));
            }
            return null;
        }
        if (next != TokenType.EOF) {
            return null;
        }

        StringBuilder text = new StringBuilder();
        List<TokenType> inserted = new ArrayList<>();
        String filled = null;
        if (afterSeparator) {
            text.append(synthetic);
            inserted.add(TokenType.IDENTIFIER);
            filled = synthetic;
        }
        for (int index : open) {
            TokenType closer = closerOf(tokens.get(index).token().type());
            text.append(switch (closer) {
                case RPAREN -> ')';
                case RBRACKET -> ']';
                default -> '}';
            });
            inserted.add(closer);
        }
        return new Repair(text.toString(), filled, label, inserted);
    }

    /**
     * The label of a named argument whose value is missing at the cursor:
     * the significant tokens before the cursor are an argument-slot start
     * ({@code (} or {@code ,}), one identifier, and {@code :}. The real
     * parser must still confirm the repaired argument as that named
     * argument; otherwise nothing is published.
     */
    private static String namedValueLabel(List<TokenOccurrence> tokens, int before) {
        int colon = significantIndex(tokens, before - 1, -1);
        if (colon < 1 || tokens.get(colon).token().type() != TokenType.COLON) {
            return null;
        }
        TokenOccurrence label = tokens.get(colon - 1);
        if (label.token().type() != TokenType.IDENTIFIER) {
            return null;
        }
        int separator = significantIndex(tokens, colon - 2, -1);
        if (separator < 0) {
            return null;
        }
        TokenType type = tokens.get(separator).token().type();
        return type == TokenType.LPAREN || type == TokenType.COMMA
                ? label.token().lexeme()
                : null;
    }

    /**
     * The repaired token stream must be the original one with the inserted
     * tokens at the cursor: nothing merges, splits, or changes role.
     */
    private static boolean preservesOriginalTokens(
            List<TokenOccurrence> original,
            int before,
            Repair repair,
            int sourceOffset,
            List<TokenOccurrence> repaired) {
        List<TokenType> inserted = repair.insertedTypes();
        int shift = repair.text().length();
        if (repaired.size() != original.size() + inserted.size()) {
            return false;
        }
        for (int index = 0; index < before; index++) {
            if (!repaired.get(index).equals(original.get(index))) {
                return false;
            }
        }
        for (int index = 0; index < inserted.size(); index++) {
            TokenOccurrence token = repaired.get(before + index);
            if (token.token().type() != inserted.get(index)
                    || token.span().startOffset() < sourceOffset
                    || token.span().endOffset() > sourceOffset + shift) {
                return false;
            }
        }
        for (int index = before; index < original.size(); index++) {
            TokenOccurrence expected = original.get(index);
            TokenOccurrence actual = repaired.get(index + inserted.size());
            if (!actual.token().equals(expected.token())
                    || !actual.span().equals(new SourceSpan(
                            expected.span().startOffset() + shift,
                            expected.span().endOffset() + shift))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns the call's parenthesized arguments when the argument-list-level
     * commas of the parsed token stream lie exactly between them, or
     * {@code null}. An attached trailing Closure follows the closing
     * {@code )} and is not a list argument.
     */
    private static List<SurfaceArgument> verifiedListArguments(
            SurfaceCall call,
            List<TokenOccurrence> tokens,
            int argumentListOpen) {
        int close = matchingClose(tokens, argumentListOpen);
        if (close < 0 || tokens.get(close).token().type() != TokenType.RPAREN) {
            return null;
        }
        int listStart = tokens.get(argumentListOpen).span().endOffset();
        int listEnd = tokens.get(close).span().startOffset();
        List<SurfaceArgument> listArguments = new ArrayList<>();
        int trailing = 0;
        for (SurfaceArgument argument : call.arguments()) {
            SourceSpan span = argument.span();
            if (span.endOffset() <= listEnd) {
                if (trailing > 0 || span.startOffset() < listStart) {
                    return null;
                }
                listArguments.add(argument);
            } else if (span.startOffset() >= tokens.get(close).span().endOffset()) {
                trailing++;
            } else {
                return null;
            }
        }
        if (trailing > 1) {
            return null;
        }

        List<TokenOccurrence> commas = listLevelCommas(tokens, argumentListOpen, close);
        if (commas.size() != Math.max(listArguments.size() - 1, 0)) {
            return null;
        }
        for (int index = 0; index < commas.size(); index++) {
            SourceSpan comma = commas.get(index).span();
            if (comma.startOffset() < listArguments.get(index).span().endOffset()
                    || comma.endOffset() > listArguments.get(index + 1).span().startOffset()) {
                return null;
            }
        }
        return listArguments;
    }

    private static List<TokenOccurrence> listLevelCommas(
            List<TokenOccurrence> tokens,
            int argumentListOpen,
            int end) {
        List<TokenOccurrence> commas = new ArrayList<>();
        int depth = 0;
        for (int index = argumentListOpen + 1; index < end; index++) {
            TokenType type = tokens.get(index).token().type();
            if (isOpener(type)) {
                depth++;
            } else if (isCloser(type)) {
                depth--;
            } else if (type == TokenType.COMMA && depth == 0) {
                commas.add(tokens.get(index));
            }
        }
        return commas;
    }

    private static int matchingClose(List<TokenOccurrence> tokens, int opener) {
        int depth = 0;
        for (int index = opener; index < tokens.size(); index++) {
            TokenType type = tokens.get(index).token().type();
            if (isOpener(type)) {
                depth++;
            } else if (isCloser(type) && --depth == 0) {
                return index;
            }
        }
        return -1;
    }

    /**
     * Collects the calls whose receiver ends exactly where the token before
     * the argument-list {@code (} ends. The parser consumes that {@code (}
     * as the call suffix of exactly one receiver.
     */
    private static void collectCalls(
            SurfaceExpression expression,
            int receiverEnd,
            List<SurfaceCall> calls) {
        if (expression instanceof SurfaceCall call
                && call.receiver().span().endOffset() == receiverEnd
                && call.span().startOffset() == call.receiver().span().startOffset()) {
            calls.add(call);
        }
        for (SurfaceExpression child : ProtosStaticHover.children(expression)) {
            collectCalls(child, receiverEnd, calls);
        }
    }

    private static TokenType significantType(List<TokenOccurrence> tokens, int start, int step) {
        int index = significantIndex(tokens, start, step);
        return index < 0 ? null : tokens.get(index).token().type();
    }

    private static int significantIndex(List<TokenOccurrence> tokens, int start, int step) {
        for (int index = start; index >= 0 && index < tokens.size(); index += step) {
            if (tokens.get(index).token().type() != TokenType.NEWLINE) {
                return index;
            }
        }
        return -1;
    }

    private static boolean isOpener(TokenType type) {
        return type == TokenType.LPAREN || type == TokenType.LBRACKET || type == TokenType.LBRACE;
    }

    private static boolean isCloser(TokenType type) {
        return type == TokenType.RPAREN || type == TokenType.RBRACKET || type == TokenType.RBRACE;
    }

    private static TokenType closerOf(TokenType opener) {
        return switch (opener) {
            case LPAREN -> TokenType.RPAREN;
            case LBRACKET -> TokenType.RBRACKET;
            case LBRACE -> TokenType.RBRACE;
            default -> throw new IllegalArgumentException("not an opening delimiter: " + opener);
        };
    }

    /**
     * Picks an identifier that does not occur anywhere in the snapshot, so it
     * can never coincide with a real identifier.
     */
    private static String syntheticName(String source) {
        String name = SYNTHETIC_BASE;
        for (int suffix = 0; source.contains(name); suffix++) {
            name = SYNTHETIC_BASE + suffix;
        }
        return name;
    }

    private static SurfaceSequence parse(String source) {
        try {
            return new ProtosParser(source).parseProgram();
        } catch (ParseError | ProtosLexer.LexicalError rejected) {
            return null;
        }
    }
}
