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
import com.guillermomolina.protos.parser.ast.SurfaceAssignment;
import com.guillermomolina.protos.parser.ast.SurfaceExpression;
import com.guillermomolina.protos.parser.ast.SurfaceGroup;
import com.guillermomolina.protos.parser.ast.SurfaceIntrinsic;
import com.guillermomolina.protos.parser.ast.SurfaceMember;
import com.guillermomolina.protos.parser.ast.SurfaceName;
import com.guillermomolina.protos.parser.ast.SurfaceSequence;
import com.guillermomolina.protos.parser.ast.SurfaceSlotCreation;
import com.guillermomolina.protos.source.SourceSpan;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * D228-A1 syntactic proof of a REPL selector-completion site.
 *
 * <p>The site is proven with the real lexer and parser only:</p>
 *
 * <ul>
 *   <li>A cursor inside a comment, inside a non-member token such as a String literal, or
 *       splitting a surrogate pair yields nothing; so does a lexically invalid snapshot.</li>
 *   <li>A member-name token, that is an {@code IDENTIFIER} or one of the six reserved words
 *       admitted after a dot, that contains or touches the cursor is the replaceable selector; the
 *       unmodified snapshot must parse and yield a {@link SurfaceMember} ending at that exact
 *       token. Editing inside the token keeps its real boundaries.</li>
 *   <li>Otherwise, right after a {@code .}, one synthetic identifier absent from the snapshot is
 *       inserted at the cursor in a local copy, preserving the text after it; the replacement is
 *       then empty. A token merge or role change fails closed, and so does failing to find a
 *       collision-free name within {@link #MAX_SYNTHETIC_ATTEMPTS}.</li>
 * </ul>
 *
 * <p>The surface AST is searched iteratively, visiting only nodes whose span encloses the
 * selector, and at most {@link #MAX_EXAMINED_NODES} of them; an exhausted budget is not a
 * proof and fails closed.</p>
 *
 * <p>The member must be a read or message send, not the target of an assignment or slot
 * creation. Its receiver, through {@link SurfaceGroup} alone, must be a {@link SurfaceName} or
 * the {@code this} intrinsic. This class is syntactic authority only: it has no runtime access
 * and produces no slot candidates.</p>
 */
final class ProtosStaticMemberCompletion {
    private static final String SYNTHETIC_BASE = "protosMemberCompletionSite";
    static final int MAX_SYNTHETIC_ATTEMPTS = 64;
    static final int MAX_EXAMINED_NODES = 4096;

    private ProtosStaticMemberCompletion() {
    }

    static Optional<ProtosStaticMemberCompletionSite> find(
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

        int selectorIndex = -1;
        int precedingIndex = -1;
        for (int index = 0; index < tokens.size(); index++) {
            TokenOccurrence token = tokens.get(index);
            SourceSpan span = token.span();
            if (isMemberName(token)
                    && span.startOffset() <= sourceOffset
                    && sourceOffset <= span.endOffset()) {
                selectorIndex = index;
                break;
            }
            if (span.startOffset() < sourceOffset && sourceOffset < span.endOffset()) {
                return Optional.empty();
            }
            if (span.endOffset() <= sourceOffset && isSignificant(token)) {
                precedingIndex = index;
            }
        }

        SourceSpan replacement;
        SourceSpan selector;
        String expectedName;
        SurfaceSequence program;
        if (selectorIndex >= 0) {
            if (!followsDot(tokens, selectorIndex)) {
                return Optional.empty();
            }
            TokenOccurrence token = tokens.get(selectorIndex);
            replacement = token.span();
            selector = replacement;
            expectedName = token.token().lexeme();
            program = parse(source);
        } else {
            if (precedingIndex < 0
                    || tokens.get(precedingIndex).token().type() != TokenType.DOT) {
                return Optional.empty();
            }
            String synthetic = syntheticName(tokens);
            if (synthetic == null) {
                return Optional.empty();
            }
            replacement = new SourceSpan(sourceOffset, sourceOffset);
            selector = new SourceSpan(sourceOffset, sourceOffset + synthetic.length());
            expectedName = synthetic;
            program = parse(source.substring(0, sourceOffset)
                    + synthetic
                    + source.substring(sourceOffset));
        }
        if (program == null) {
            return Optional.empty();
        }

        SurfaceMember member = readMemberEndingAt(program, selector, expectedName);
        if (member == null) {
            return Optional.empty();
        }

        SurfaceExpression receiver = member.receiver();
        while (receiver instanceof SurfaceGroup group) {
            receiver = group.expression();
        }
        return switch (receiver) {
            case SurfaceName name -> Optional.of(new ProtosStaticMemberCompletionSite(
                    replacement,
                    ProtosStaticMemberCompletionSite.ReceiverKind.LEXICAL_NAME,
                    name.name()));
            case SurfaceIntrinsic intrinsic
                    when intrinsic.kind() == SurfaceIntrinsic.Kind.THIS ->
                    Optional.of(new ProtosStaticMemberCompletionSite(
                            replacement,
                            ProtosStaticMemberCompletionSite.ReceiverKind.THIS,
                            null));
            default -> Optional.empty();
        };
    }

    private static boolean isMemberName(TokenOccurrence token) {
        return switch (token.token().type()) {
            case IDENTIFIER, THIS, CONTEXT, SUPER, TRUE, FALSE, NULL -> true;
            default -> false;
        };
    }

    private static boolean isSignificant(TokenOccurrence token) {
        TokenType type = token.token().type();
        return type != TokenType.NEWLINE && type != TokenType.EOF;
    }

    /** The parser admits continuation newlines between {@code .} and the member name. */
    private static boolean followsDot(List<TokenOccurrence> tokens, int index) {
        for (int previous = index - 1; previous >= 0; previous--) {
            TokenOccurrence token = tokens.get(previous);
            if (isSignificant(token)) {
                return token.token().type() == TokenType.DOT;
            }
        }
        return false;
    }

    private record Visit(SurfaceExpression expression, boolean mutationTarget) {
    }

    /*
     * The parser ends a SurfaceMember exactly at its member-name token, while its span starts at
     * the receiver. The selector span therefore identifies at most one member, and every
     * ancestor of it encloses the selector, so other subtrees are never entered.
     *
     * Returns the member only when it is not the direct target (by node identity) of an
     * assignment or slot creation; null for no member, a mutation target or an exhausted budget.
     */
    private static SurfaceMember readMemberEndingAt(
            SurfaceExpression root,
            SourceSpan selector,
            String expectedName) {
        ArrayDeque<Visit> pending = new ArrayDeque<>();
        pending.push(new Visit(root, false));
        int examined = 0;
        while (!pending.isEmpty()) {
            if (++examined > MAX_EXAMINED_NODES) {
                return null;
            }
            Visit visit = pending.pop();
            SurfaceExpression expression = visit.expression();
            SourceSpan span = expression.span();
            if (span.startOffset() > selector.startOffset()
                    || span.endOffset() < selector.endOffset()) {
                continue;
            }
            if (expression instanceof SurfaceMember member
                    && span.endOffset() == selector.endOffset()
                    && span.startOffset() < selector.startOffset()
                    && member.name().equals(expectedName)) {
                return visit.mutationTarget() ? null : member;
            }
            SurfaceExpression target = switch (expression) {
                case SurfaceAssignment assignment -> assignment.target();
                case SurfaceSlotCreation creation -> creation.target();
                default -> null;
            };
            List<SurfaceExpression> children = ProtosStaticHover.children(expression);
            // Push in reverse so source order is preserved on pop.
            for (int index = children.size() - 1; index >= 0; index--) {
                SurfaceExpression child = children.get(index);
                pending.push(new Visit(child, child == target));
            }
        }
        return null;
    }

    /*
     * Picks an identifier that is not the lexeme of any identifier token of the snapshot, so it
     * can never coincide with a real name; the parser check rejects any token merge.
     */
    private static String syntheticName(List<TokenOccurrence> tokens) {
        HashSet<String> identifiers = new HashSet<>();
        for (TokenOccurrence token : tokens) {
            if (token.token().type() == TokenType.IDENTIFIER) {
                identifiers.add(token.token().lexeme());
            }
        }
        for (int attempt = 0; attempt < MAX_SYNTHETIC_ATTEMPTS; attempt++) {
            String name = attempt == 0 ? SYNTHETIC_BASE : SYNTHETIC_BASE + (attempt - 1);
            if (!identifiers.contains(name)) {
                return name;
            }
        }
        return null;
    }

    private static SurfaceSequence parse(String source) {
        try {
            return new ProtosParser(source).parseProgram();
        } catch (ParseError | ProtosLexer.LexicalError rejected) {
            return null;
        }
    }
}
