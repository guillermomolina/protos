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
import com.guillermomolina.protos.parser.ast.SurfaceExpression;
import com.guillermomolina.protos.parser.ast.SurfaceIntrinsic;
import com.guillermomolina.protos.parser.ast.SurfaceLiteral;
import com.guillermomolina.protos.parser.ast.SurfaceSequence;
import com.guillermomolina.protos.source.SourceSpan;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * LM010-B static completion over one exact document snapshot.
 *
 * <p>The cursor context is classified with the real lexer and parser only:</p>
 *
 * <ul>
 *   <li>A cursor inside a comment or strictly inside any token (an
 *       identifier, a String literal, an operator) yields nothing, so an
 *       identifier is never split into a hybrid name. A lexically invalid
 *       snapshot yields nothing.</li>
 *   <li>A cursor immediately after an identifier makes that whole identifier
 *       the replaceable prefix; the unmodified snapshot must parse.</li>
 *   <li>Otherwise one synthetic identifier, absent from the snapshot, is
 *       inserted at the cursor while the text after it is preserved. The
 *       insertion is accepted only when the real parser yields a read of that
 *       exact identifier there; a token merge or role change fails closed.</li>
 * </ul>
 *
 * <p>The site must be a {@link com.guillermomolina.protos.parser.ast.SurfaceName}
 * read visited by the D110 generation-1 walk. Proven candidates are exactly
 * the facts that walk holds there. Reserved-word candidates are offered only
 * when the parser, given that word at the replacement span, yields the
 * corresponding intrinsic or literal there; bare {@code super} is never an
 * expression and is not a candidate. No guest code is executed.</p>
 */
final class ProtosStaticCompletion {
    private static final List<String> RESERVED_EXPRESSIONS =
            List.of("this", "context", "true", "false", "null");
    private static final String SYNTHETIC_BASE = "protosCompletionSite";

    private ProtosStaticCompletion() {
    }

    static Optional<ProtosStaticCompletionResult> complete(
            ProtosDocumentSnapshot snapshot,
            int sourceOffset) {
        Objects.requireNonNull(snapshot, "snapshot");
        String source = snapshot.characters();
        if (sourceOffset < 0
                || sourceOffset > source.length()
                || splitsSurrogatePair(source, sourceOffset)) {
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
            if (insideComment(occurrence, sourceOffset)) {
                return Optional.empty();
            }
        }

        SourceSpan prefix = null;
        for (TokenOccurrence token : tokens) {
            SourceSpan span = token.span();
            if (span.startOffset() < sourceOffset && sourceOffset < span.endOffset()) {
                return Optional.empty();
            }
            if (token.token().type() == TokenType.IDENTIFIER
                    && span.startOffset() < sourceOffset
                    && span.endOffset() == sourceOffset) {
                prefix = span;
            }
        }

        SourceSpan replacement;
        SurfaceSequence program;
        SourceSpan readSite;
        if (prefix != null) {
            replacement = prefix;
            readSite = prefix;
            program = parse(source);
        } else {
            String synthetic = syntheticName(source);
            replacement = new SourceSpan(sourceOffset, sourceOffset);
            readSite = new SourceSpan(sourceOffset, sourceOffset + synthetic.length());
            program = parse(substitute(source, replacement, synthetic));
        }
        if (program == null) {
            return Optional.empty();
        }

        Optional<List<String>> proven =
                ProtosStaticDefinitions.provenNamesAtRead(program, readSite);
        if (proven.isEmpty()) {
            return Optional.empty();
        }

        List<ProtosStaticCompletionResult.Candidate> candidates = new ArrayList<>();
        for (String name : proven.get()) {
            candidates.add(new ProtosStaticCompletionResult.Candidate(
                    name,
                    ProtosStaticCompletionResult.Kind.PROVEN_BINDING));
        }
        for (String word : RESERVED_EXPRESSIONS) {
            if (acceptsReservedExpression(source, replacement, word)) {
                candidates.add(new ProtosStaticCompletionResult.Candidate(
                        word,
                        ProtosStaticCompletionResult.Kind.SYNTAX));
            }
        }
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new ProtosStaticCompletionResult(snapshot, replacement, candidates));
    }

    private static boolean splitsSurrogatePair(String source, int offset) {
        return offset > 0
                && offset < source.length()
                && Character.isHighSurrogate(source.charAt(offset - 1))
                && Character.isLowSurrogate(source.charAt(offset));
    }

    /**
     * A line comment extends to, but excludes, its logical newline, so a
     * cursor at its end is still inside it. A cursor at the end of a block
     * comment follows the closing delimiter.
     */
    private static boolean insideComment(ProtosLexer.TriviaOccurrence occurrence, int offset) {
        SourceSpan span = occurrence.span();
        return switch (occurrence.kind()) {
            case HORIZONTAL_WHITESPACE -> false;
            case LINE_COMMENT -> span.startOffset() < offset && offset <= span.endOffset();
            case BLOCK_COMMENT -> span.startOffset() < offset && offset < span.endOffset();
        };
    }

    /**
     * Picks an identifier that does not occur anywhere in the snapshot, so it
     * can never coincide with a proven name or any real identifier.
     */
    private static String syntheticName(String source) {
        String name = SYNTHETIC_BASE;
        for (int suffix = 0; source.contains(name); suffix++) {
            name = SYNTHETIC_BASE + suffix;
        }
        return name;
    }

    private static String substitute(String source, SourceSpan span, String text) {
        return source.substring(0, span.startOffset()) + text + source.substring(span.endOffset());
    }

    private static boolean acceptsReservedExpression(
            String source,
            SourceSpan replacement,
            String word) {
        SurfaceSequence program = parse(substitute(source, replacement, word));
        if (program == null) {
            return false;
        }
        int start = replacement.startOffset();
        return reservedExpressionAt(program, new SourceSpan(start, start + word.length()), word);
    }

    private static boolean reservedExpressionAt(
            SurfaceExpression expression,
            SourceSpan span,
            String word) {
        if (expression.span().startOffset() > span.startOffset()
                || expression.span().endOffset() < span.endOffset()) {
            return false;
        }
        if (expression.span().equals(span)) {
            String spelling = switch (expression) {
                case SurfaceIntrinsic intrinsic -> switch (intrinsic.kind()) {
                    case THIS -> "this";
                    case CONTEXT -> "context";
                };
                case SurfaceLiteral literal -> switch (literal.kind()) {
                    case TRUE -> "true";
                    case FALSE -> "false";
                    case NULL -> "null";
                    case NUMBER, STRING -> null;
                };
                default -> null;
            };
            if (word.equals(spelling)) {
                return true;
            }
        }
        for (SurfaceExpression child : ProtosStaticHover.children(expression)) {
            if (reservedExpressionAt(child, span, word)) {
                return true;
            }
        }
        return false;
    }

    private static SurfaceSequence parse(String source) {
        try {
            return new ProtosParser(source).parseProgram();
        } catch (ParseError | ProtosLexer.LexicalError rejected) {
            return null;
        }
    }
}
