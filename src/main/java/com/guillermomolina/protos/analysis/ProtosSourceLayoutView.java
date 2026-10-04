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
import com.guillermomolina.protos.lexer.ProtosLexer.TriviaKind;
import com.guillermomolina.protos.lexer.ProtosLexer.TriviaOccurrence;
import com.guillermomolina.protos.lexer.TokenOccurrence;
import com.guillermomolina.protos.lexer.TokenType;
import com.guillermomolina.protos.parser.ProtosParser;
import com.guillermomolina.protos.parser.ProtosParserSourceFacts;
import com.guillermomolina.protos.parser.ProtosParserSourceFacts.ExpressionElement;
import com.guillermomolina.protos.parser.ProtosParserSourceFacts.MapEntryElement;
import com.guillermomolina.protos.parser.ProtosParserSourceFacts.ObjectItemElement;
import com.guillermomolina.protos.parser.ProtosParserSourceFacts.SequenceElement;
import com.guillermomolina.protos.parser.ProtosParserSourceFacts.SequenceSeparatorKind;
import com.guillermomolina.protos.parser.ast.SurfaceArgument;
import com.guillermomolina.protos.parser.ast.SurfaceArrayConstruction;
import com.guillermomolina.protos.parser.ast.SurfaceAssignment;
import com.guillermomolina.protos.parser.ast.SurfaceBinary;
import com.guillermomolina.protos.parser.ast.SurfaceCall;
import com.guillermomolina.protos.parser.ast.SurfaceClosure;
import com.guillermomolina.protos.parser.ast.SurfaceExpression;
import com.guillermomolina.protos.parser.ast.SurfaceGroup;
import com.guillermomolina.protos.parser.ast.SurfaceIndex;
import com.guillermomolina.protos.parser.ast.SurfaceIntrinsic;
import com.guillermomolina.protos.parser.ast.SurfaceLiteral;
import com.guillermomolina.protos.parser.ast.SurfaceMapConstruction;
import com.guillermomolina.protos.parser.ast.SurfaceMember;
import com.guillermomolina.protos.parser.ast.SurfaceMultipleSlotCreation;
import com.guillermomolina.protos.parser.ast.SurfaceName;
import com.guillermomolina.protos.parser.ast.SurfaceNonLocalReturn;
import com.guillermomolina.protos.parser.ast.SurfaceObject;
import com.guillermomolina.protos.parser.ast.SurfaceObjectItem;
import com.guillermomolina.protos.parser.ast.SurfaceParameter;
import com.guillermomolina.protos.parser.ast.SurfaceSequence;
import com.guillermomolina.protos.parser.ast.SurfaceSlotCreation;
import com.guillermomolina.protos.parser.ast.SurfaceSuperSend;
import com.guillermomolina.protos.parser.ast.SurfaceUnary;
import com.guillermomolina.protos.source.SourceSpan;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable editor-neutral on-demand source-layout view.
 *
 * <p>The immutable snapshot remains the exact raw-source authority. This view
 * groups canonical token occurrences, the canonical Surface AST, opt-in trivia,
 * and parser source facts. It is not a CST and owns no formatter policy.</p>
 */
public final class ProtosSourceLayoutView {
    public enum CommentPlacement {
        OWN_LINE,
        END_OF_LINE,
        EMBEDDED_BETWEEN_TOKENS
    }

    public enum CommentBoundaryKind {
        NONE,
        SEQUENCE_SEPARATOR,
        TRAILING_CLOSURE
    }

    /**
     * Source-position-independent location in the canonical Surface tree.
     */
    public record StructuralPath(
            String root,
            List<StructuralStep> steps) {
        public StructuralPath {
            Objects.requireNonNull(root, "root");
            if (root.isEmpty()) {
                throw new IllegalArgumentException(
                        "structural path root must not be empty");
            }
            steps = List.copyOf(
                    Objects.requireNonNull(steps, "steps"));
        }

        static StructuralPath programRoot() {
            return new StructuralPath("program", List.of());
        }

        StructuralPath child(String role) {
            return child(role, -1);
        }

        StructuralPath child(String role, int index) {
            List<StructuralStep> childSteps =
                    new ArrayList<>(steps);
            childSteps.add(new StructuralStep(role, index));
            return new StructuralPath(root, childSteps);
        }

        @Override
        public String toString() {
            StringBuilder text = new StringBuilder(root);
            for (StructuralStep step : steps) {
                text.append('/').append(step.role());
                if (step.index() >= 0) {
                    text.append('[')
                            .append(step.index())
                            .append(']');
                }
            }
            return text.toString();
        }
    }

    public record StructuralStep(
            String role,
            int index) {
        public StructuralStep {
            Objects.requireNonNull(role, "role");
            if (role.isEmpty()) {
                throw new IllegalArgumentException(
                        "structural role must not be empty");
            }
            if (index < -1) {
                throw new IllegalArgumentException(
                        "structural index must be -1 or non-negative");
            }
        }
    }

    public record CommentAttachment(
            TriviaOccurrence comment,
            CommentPlacement placement,
            Optional<StructuralPath> preceding,
            Optional<StructuralPath> following,
            CommentBoundaryKind boundaryKind,
            Optional<SequenceSeparatorKind> sequenceSeparatorKind) {
        public CommentAttachment {
            Objects.requireNonNull(comment, "comment");
            Objects.requireNonNull(placement, "placement");
            Objects.requireNonNull(preceding, "preceding");
            Objects.requireNonNull(following, "following");
            Objects.requireNonNull(boundaryKind, "boundaryKind");
            Objects.requireNonNull(
                    sequenceSeparatorKind,
                    "sequenceSeparatorKind");

            if (comment.kind() != TriviaKind.LINE_COMMENT
                    && comment.kind() != TriviaKind.BLOCK_COMMENT) {
                throw new IllegalArgumentException(
                        "comment attachment requires comment trivia");
            }

            if ((boundaryKind
                            == CommentBoundaryKind.SEQUENCE_SEPARATOR)
                    != sequenceSeparatorKind.isPresent()) {
                throw new IllegalArgumentException(
                        "sequence separator kind must exist only "
                                + "for sequence boundaries");
            }
        }
    }

    public enum StructuralFormKind {
        GROUP,
        ARRAY_CONSTRUCTION,
        MAP_CONSTRUCTION,
        CLOSURE_EXPRESSION_BODY,
        CLOSURE_BLOCK_BODY
    }

    public record TokenSpelling(
            TokenType type,
            String rawText) {
        public TokenSpelling {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(rawText, "rawText");
        }
    }

    public record CommentProjection(
            TriviaKind kind,
            String rawText,
            CommentPlacement placement,
            Optional<StructuralPath> preceding,
            Optional<StructuralPath> following,
            CommentBoundaryKind boundaryKind,
            Optional<SequenceSeparatorKind> sequenceSeparatorKind,
            boolean blankLineBefore,
            boolean blankLineAfter) {
        public CommentProjection {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(rawText, "rawText");
            Objects.requireNonNull(placement, "placement");
            Objects.requireNonNull(preceding, "preceding");
            Objects.requireNonNull(following, "following");
            Objects.requireNonNull(boundaryKind, "boundaryKind");
            Objects.requireNonNull(
                    sequenceSeparatorKind,
                    "sequenceSeparatorKind");
        }
    }

    public record StructuralFormProjection(
            StructuralPath path,
            StructuralFormKind kind) {
        public StructuralFormProjection {
            Objects.requireNonNull(path, "path");
            Objects.requireNonNull(kind, "kind");
        }
    }

    public record SequenceSeparatorProjection(
            ProtosParserSourceFacts.SequenceContext context,
            StructuralPath preceding,
            SequenceSeparatorKind kind,
            boolean blankLine,
            StructuralPath following) {
        public SequenceSeparatorProjection {
            Objects.requireNonNull(context, "context");
            Objects.requireNonNull(preceding, "preceding");
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(following, "following");
        }
    }

    public record ClosureFormProjection(
            StructuralPath closure,
            ProtosParserSourceFacts.SingleParameterClosureForm form) {
        public ClosureFormProjection {
            Objects.requireNonNull(closure, "closure");
            Objects.requireNonNull(form, "form");
        }
    }

    public record TrailingClosureProjection(
            StructuralPath call,
            StructuralPath closure) {
        public TrailingClosureProjection {
            Objects.requireNonNull(call, "call");
            Objects.requireNonNull(closure, "closure");
        }
    }

    /**
     * D183 preservation evidence without source offsets.
     *
     * <p>Ordering is represented by immutable list order. Raw spellings come
     * from the exact immutable snapshot, while structural relationships use
     * Surface-tree paths rather than absolute source positions.</p>
     */
    public record SourcePreservationProjection(
            List<TokenSpelling> tokens,
            List<CommentProjection> comments,
            List<StructuralFormProjection> structuralForms,
            List<SequenceSeparatorProjection> sequenceSeparators,
            List<ClosureFormProjection> closureForms,
            List<TrailingClosureProjection> trailingClosures) {
        public SourcePreservationProjection {
            tokens = List.copyOf(
                    Objects.requireNonNull(tokens, "tokens"));
            comments = List.copyOf(
                    Objects.requireNonNull(comments, "comments"));
            structuralForms = List.copyOf(
                    Objects.requireNonNull(
                            structuralForms,
                            "structuralForms"));
            sequenceSeparators = List.copyOf(
                    Objects.requireNonNull(
                            sequenceSeparators,
                            "sequenceSeparators"));
            closureForms = List.copyOf(
                    Objects.requireNonNull(
                            closureForms,
                            "closureForms"));
            trailingClosures = List.copyOf(
                    Objects.requireNonNull(
                            trailingClosures,
                            "trailingClosures"));
        }
    }

    private final ProtosDocumentSnapshot snapshot;
    private final List<TokenOccurrence> tokenOccurrences;
    private final SurfaceSequence program;
    private final List<TriviaOccurrence> trivia;
    private final ProtosParserSourceFacts sourceFacts;
    private final List<CommentAttachment> commentAttachments;
    private final StructuralIndex structuralIndex;

    private ProtosSourceLayoutView(
            ProtosDocumentSnapshot snapshot,
            List<TokenOccurrence> tokenOccurrences,
            SurfaceSequence program,
            List<TriviaOccurrence> trivia,
            ProtosParserSourceFacts sourceFacts,
            List<CommentAttachment> commentAttachments,
            StructuralIndex structuralIndex) {
        this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
        this.tokenOccurrences = List.copyOf(tokenOccurrences);
        this.program = Objects.requireNonNull(program, "program");
        this.trivia = List.copyOf(trivia);
        this.sourceFacts = Objects.requireNonNull(sourceFacts, "sourceFacts");
        this.commentAttachments = List.copyOf(commentAttachments);
        this.structuralIndex =
                Objects.requireNonNull(
                        structuralIndex,
                        "structuralIndex");
    }

    public static ProtosSourceLayoutView create(
            ProtosDocumentSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");

        List<TriviaOccurrence> trivia = new ArrayList<>();
        List<TokenOccurrence> tokenOccurrences =
                new ProtosLexer(snapshot.characters())
                        .tokenizeOccurrencesWithTrivia(trivia::add);

        ProtosParserSourceFacts.Builder sourceFacts =
                new ProtosParserSourceFacts.Builder();

        SurfaceSequence program =
                ProtosParser.forTooling(
                                tokenOccurrences,
                                sourceFacts)
                        .parseProgram();

        ProtosParserSourceFacts builtSourceFacts =
                sourceFacts.build();

        StructuralIndex structuralIndex =
                StructuralIndex.create(program);

        List<CommentAttachment> commentAttachments =
                attachComments(
                        snapshot.characters(),
                        tokenOccurrences,
                        trivia,
                        builtSourceFacts,
                        structuralIndex);

        return new ProtosSourceLayoutView(
                snapshot,
                tokenOccurrences,
                program,
                trivia,
                builtSourceFacts,
                commentAttachments,
                structuralIndex);
    }

    public ProtosDocumentSnapshot snapshot() {
        return snapshot;
    }

    public List<TokenOccurrence> tokenOccurrences() {
        return tokenOccurrences;
    }

    public SurfaceSequence program() {
        return program;
    }

    public List<TriviaOccurrence> trivia() {
        return trivia;
    }

    public ProtosParserSourceFacts sourceFacts() {
        return sourceFacts;
    }

    public List<CommentAttachment> commentAttachments() {
        return commentAttachments;
    }

    public Optional<StructuralPath> pathOf(
            SurfaceExpression expression) {
        Objects.requireNonNull(expression, "expression");
        return structuralIndex.pathOf(expression);
    }

    /**
     * Returns the structural path of a canonical Surface/parser node retained
     * by this tooling view.
     *
     * <p>This tool-neutral access includes non-expression sequence elements
     * such as Map entries and Object items. It exposes only the path already
     * owned by the B1 structural index and introduces no formatting policy.
     */
    public Optional<StructuralPath> pathOfNode(Object node) {
        Objects.requireNonNull(node, "node");
        return structuralIndex.pathOf(node);
    }

    public SourcePreservationProjection preservationProjection() {
        List<TokenSpelling> projectedTokens =
                new ArrayList<>();

        for (TokenOccurrence occurrence : tokenOccurrences) {
            if (occurrence.token().type() == TokenType.EOF) {
                continue;
            }

            projectedTokens.add(
                    new TokenSpelling(
                            occurrence.token().type(),
                            sourceText(occurrence.span())));
        }

        List<CommentProjection> projectedComments =
                commentAttachments.stream()
                        .map(
                                attachment ->
                                        new CommentProjection(
                                                attachment.comment().kind(),
                                                sourceText(
                                                        attachment
                                                                .comment()
                                                                .span()),
                                                attachment.placement(),
                                                attachment.preceding(),
                                                attachment.following(),
                                                attachment.boundaryKind(),
                                                attachment
                                                        .sequenceSeparatorKind(),
                                                adjacentBlankLineBefore(
                                                        attachment
                                                                .comment()
                                                                .span()),
                                                adjacentBlankLineAfter(
                                                        attachment
                                                                .comment()
                                                                .span())))
                        .toList();

        List<StructuralFormProjection> projectedForms =
                new ArrayList<>();

        for (StructuralNode structuralNode :
                structuralIndex.nodes) {
            Object node = structuralNode.node();

            if (node instanceof SurfaceGroup) {
                projectedForms.add(
                        new StructuralFormProjection(
                                structuralNode.path(),
                                StructuralFormKind.GROUP));
            } else if (node
                    instanceof SurfaceArrayConstruction) {
                projectedForms.add(
                        new StructuralFormProjection(
                                structuralNode.path(),
                                StructuralFormKind
                                        .ARRAY_CONSTRUCTION));
            } else if (node
                    instanceof SurfaceMapConstruction) {
                projectedForms.add(
                        new StructuralFormProjection(
                                structuralNode.path(),
                                StructuralFormKind
                                        .MAP_CONSTRUCTION));
            } else if (node instanceof SurfaceClosure closure) {
                projectedForms.add(
                        new StructuralFormProjection(
                                structuralNode.path(),
                                closure.expressionBody()
                                        ? StructuralFormKind
                                                .CLOSURE_EXPRESSION_BODY
                                        : StructuralFormKind
                                                .CLOSURE_BLOCK_BODY));
            }
        }

        List<SequenceSeparatorProjection> projectedSeparators =
                new ArrayList<>();

        for (ProtosParserSourceFacts.SequenceSeparatorFact fact :
                sourceFacts.sequenceSeparators()) {
            Optional<StructuralPath> preceding =
                    structuralIndex.pathOf(
                            sequenceElementObject(
                                    fact.preceding()));
            Optional<StructuralPath> following =
                    structuralIndex.pathOf(
                            sequenceElementObject(
                                    fact.following()));

            if (preceding.isEmpty()
                    || following.isEmpty()) {
                throw new IllegalStateException(
                        "parser separator fact is absent "
                                + "from canonical Surface tree");
            }

            projectedSeparators.add(
                    new SequenceSeparatorProjection(
                            fact.context(),
                            preceding.orElseThrow(),
                            fact.kind(),
                            separatorHasBlankLine(
                                    fact.separatorSpan()),
                            following.orElseThrow()));
        }

        List<ClosureFormProjection> projectedClosureForms =
                new ArrayList<>();

        for (ProtosParserSourceFacts
                        .SingleParameterClosureSourceForm fact :
                sourceFacts.singleParameterClosureForms()) {
            StructuralPath path =
                    structuralIndex.pathOf(fact.closure())
                            .orElseThrow(
                                    () ->
                                            new IllegalStateException(
                                                    "closure source fact "
                                                            + "is absent from "
                                                            + "canonical "
                                                            + "Surface tree"));

            projectedClosureForms.add(
                    new ClosureFormProjection(
                            path,
                            fact.form()));
        }

        List<TrailingClosureProjection> projectedTrailingClosures =
                new ArrayList<>();

        for (ProtosParserSourceFacts.TrailingClosureOrigin fact :
                sourceFacts.trailingClosureOrigins()) {
            StructuralPath call =
                    structuralIndex.pathOf(fact.call())
                            .orElseThrow(
                                    () ->
                                            new IllegalStateException(
                                                    "trailing call fact "
                                                            + "is absent from "
                                                            + "canonical "
                                                            + "Surface tree"));

            StructuralPath closure =
                    structuralIndex.pathOf(fact.closure())
                            .orElseThrow(
                                    () ->
                                            new IllegalStateException(
                                                    "trailing closure fact "
                                                            + "is absent from "
                                                            + "canonical "
                                                            + "Surface tree"));

            projectedTrailingClosures.add(
                    new TrailingClosureProjection(
                            call,
                            closure));
        }

        return new SourcePreservationProjection(
                projectedTokens,
                projectedComments,
                projectedForms,
                projectedSeparators,
                projectedClosureForms,
                projectedTrailingClosures);
    }

    private boolean separatorHasBlankLine(
            SourceSpan separatorSpan) {
        TokenOccurrence previousNewline = null;

        for (TokenOccurrence occurrence : tokenOccurrences) {
            if (occurrence.token().type() != TokenType.NEWLINE) {
                continue;
            }

            if (occurrence.span().startOffset()
                    < separatorSpan.startOffset()) {
                continue;
            }

            if (occurrence.span().endOffset()
                    > separatorSpan.endOffset()) {
                break;
            }

            if (previousNewline != null
                    && horizontalWhitespaceOnly(
                            snapshot.characters(),
                            previousNewline.span().endOffset(),
                            occurrence.span().startOffset())) {
                return true;
            }

            previousNewline = occurrence;
        }

        return false;
    }

    private boolean adjacentBlankLineBefore(
            SourceSpan commentSpan) {
        String source = snapshot.characters();
        int commentLineStart =
                logicalLineStart(
                        source,
                        commentSpan.startOffset());

        if (commentLineStart == 0) {
            return false;
        }

        int previousLineEnd = commentLineStart;
        char last =
                source.charAt(
                        previousLineEnd - 1);

        if (last == '\n'
                && previousLineEnd >= 2
                && source.charAt(previousLineEnd - 2) == '\r') {
            previousLineEnd -= 2;
        } else if (last == '\n'
                || last == '\r') {
            previousLineEnd -= 1;
        } else {
            return false;
        }

        int previousLineStart =
                logicalLineStart(
                        source,
                        previousLineEnd);

        return horizontalWhitespaceOnly(
                source,
                previousLineStart,
                previousLineEnd);
    }

    private boolean adjacentBlankLineAfter(
            SourceSpan commentSpan) {
        String source = snapshot.characters();
        int commentLineEnd =
                logicalLineEnd(
                        source,
                        commentSpan.endOffset());

        if (commentLineEnd >= source.length()) {
            return false;
        }

        int nextLineStart = commentLineEnd;
        char first =
                source.charAt(
                        nextLineStart);

        if (first == '\r'
                && nextLineStart + 1 < source.length()
                && source.charAt(nextLineStart + 1) == '\n') {
            nextLineStart += 2;
        } else if (first == '\n'
                || first == '\r') {
            nextLineStart += 1;
        } else {
            return false;
        }

        if (nextLineStart >= source.length()) {
            return false;
        }

        int nextLineEnd =
                logicalLineEnd(
                        source,
                        nextLineStart);

        return horizontalWhitespaceOnly(
                source,
                nextLineStart,
                nextLineEnd);
    }

    public String sourceText(SourceSpan span) {
        Objects.requireNonNull(span, "span");

        if (span.endOffset() > snapshot.characters().length()) {
            throw new IllegalArgumentException(
                    "source span exceeds immutable snapshot length");
        }

        return snapshot.characters().substring(
                span.startOffset(),
                span.endOffset());
    }

    private static List<CommentAttachment> attachComments(
            String source,
            List<TokenOccurrence> tokenOccurrences,
            List<TriviaOccurrence> trivia,
            ProtosParserSourceFacts sourceFacts,
            StructuralIndex structuralIndex) {
        List<CommentAttachment> attachments =
                new ArrayList<>();

        for (TriviaOccurrence occurrence : trivia) {
            if (occurrence.kind() != TriviaKind.LINE_COMMENT
                    && occurrence.kind() != TriviaKind.BLOCK_COMMENT) {
                continue;
            }

            CommentPlacement placement =
                    classifyComment(
                            source,
                            occurrence.span());

            Optional<ResolvedBoundary> boundary =
                    trailingClosureBoundary(
                            occurrence.span(),
                            tokenOccurrences,
                            sourceFacts,
                            structuralIndex);

            if (boundary.isEmpty()) {
                boundary =
                        sequenceBoundary(
                                occurrence.span(),
                                sourceFacts,
                                structuralIndex);
            }

            if (boundary.isPresent()) {
                ResolvedBoundary resolved =
                        boundary.orElseThrow();

                attachments.add(
                        new CommentAttachment(
                                occurrence,
                                placement,
                                Optional.of(resolved.preceding()),
                                Optional.of(resolved.following()),
                                resolved.kind(),
                                resolved.sequenceSeparatorKind()));
                continue;
            }

            attachments.add(
                    new CommentAttachment(
                            occurrence,
                            placement,
                            structuralIndex.nearestPreceding(
                                    occurrence.span()),
                            structuralIndex.nearestFollowing(
                                    occurrence.span()),
                            CommentBoundaryKind.NONE,
                            Optional.empty()));
        }

        return List.copyOf(attachments);
    }

    private static Optional<ResolvedBoundary>
            trailingClosureBoundary(
                    SourceSpan comment,
                    List<TokenOccurrence> tokenOccurrences,
                    ProtosParserSourceFacts sourceFacts,
                    StructuralIndex structuralIndex) {
        for (ProtosParserSourceFacts.TrailingClosureOrigin origin
                : sourceFacts.trailingClosureOrigins()) {
            int closureStart =
                    origin.closure().span().startOffset();

            TokenOccurrence close =
                    lastRightParenthesisBefore(
                            tokenOccurrences,
                            origin.call()
                                    .receiver()
                                    .span()
                                    .startOffset(),
                            closureStart);

            if (close == null
                    || comment.startOffset()
                            < close.span().endOffset()
                    || comment.endOffset() > closureStart) {
                continue;
            }

            Optional<StructuralPath> callPath =
                    structuralIndex.pathOf(origin.call());
            Optional<StructuralPath> closurePath =
                    structuralIndex.pathOf(origin.closure());

            if (callPath.isPresent()
                    && closurePath.isPresent()) {
                return Optional.of(
                        new ResolvedBoundary(
                                callPath.orElseThrow(),
                                closurePath.orElseThrow(),
                                CommentBoundaryKind.TRAILING_CLOSURE,
                                Optional.empty()));
            }
        }

        return Optional.empty();
    }

    private static TokenOccurrence lastRightParenthesisBefore(
            List<TokenOccurrence> tokenOccurrences,
            int lowerBound,
            int upperBound) {
        TokenOccurrence result = null;

        for (TokenOccurrence occurrence : tokenOccurrences) {
            if (occurrence.span().startOffset() >= upperBound) {
                break;
            }

            if (occurrence.token().type() == TokenType.RPAREN
                    && occurrence.span().startOffset() >= lowerBound
                    && occurrence.span().endOffset() <= upperBound) {
                result = occurrence;
            }
        }

        return result;
    }

    private static Optional<ResolvedBoundary> sequenceBoundary(
            SourceSpan comment,
            ProtosParserSourceFacts sourceFacts,
            StructuralIndex structuralIndex) {
        ResolvedBoundary best = null;
        int bestWidth = Integer.MAX_VALUE;

        for (ProtosParserSourceFacts.SequenceSeparatorFact fact
                : sourceFacts.sequenceSeparators()) {
            SourceSpan precedingSpan =
                    fact.preceding().span();
            SourceSpan followingSpan =
                    fact.following().span();

            if (comment.startOffset()
                            < precedingSpan.endOffset()
                    || comment.endOffset()
                            > followingSpan.startOffset()) {
                continue;
            }

            Optional<StructuralPath> preceding =
                    structuralIndex.pathOf(
                            sequenceElementObject(
                                    fact.preceding()));
            Optional<StructuralPath> following =
                    structuralIndex.pathOf(
                            sequenceElementObject(
                                    fact.following()));

            if (preceding.isEmpty()
                    || following.isEmpty()) {
                continue;
            }

            int width =
                    followingSpan.startOffset()
                            - precedingSpan.endOffset();

            if (width < bestWidth) {
                bestWidth = width;
                best =
                        new ResolvedBoundary(
                                preceding.orElseThrow(),
                                following.orElseThrow(),
                                CommentBoundaryKind.SEQUENCE_SEPARATOR,
                                Optional.of(fact.kind()));
            }
        }

        return Optional.ofNullable(best);
    }

    private static Object sequenceElementObject(
            SequenceElement element) {
        return switch (element) {
            case ExpressionElement expression ->
                    expression.expression();
            case ObjectItemElement item ->
                    item.item();
            case MapEntryElement entry ->
                    entry.entry();
        };
    }

    private static CommentPlacement classifyComment(
            String source,
            SourceSpan span) {
        int lineStart =
                logicalLineStart(
                        source,
                        span.startOffset());
        int lineEnd =
                logicalLineEnd(
                        source,
                        span.endOffset());

        boolean beforeWhitespace =
                horizontalWhitespaceOnly(
                        source,
                        lineStart,
                        span.startOffset());

        boolean afterWhitespace =
                horizontalWhitespaceOnly(
                        source,
                        span.endOffset(),
                        lineEnd);

        boolean spansLogicalNewline =
                containsLogicalNewline(
                        source,
                        span.startOffset(),
                        span.endOffset());

        if (beforeWhitespace && afterWhitespace) {
            return CommentPlacement.OWN_LINE;
        }

        if (!spansLogicalNewline
                && !beforeWhitespace
                && afterWhitespace) {
            return CommentPlacement.END_OF_LINE;
        }

        return CommentPlacement.EMBEDDED_BETWEEN_TOKENS;
    }

    private static int logicalLineStart(
            String source,
            int offset) {
        for (int index = offset - 1;
                index >= 0;
                index--) {
            char character = source.charAt(index);

            if (character == '\n'
                    || character == '\r') {
                return index + 1;
            }
        }

        return 0;
    }

    private static int logicalLineEnd(
            String source,
            int offset) {
        for (int index = offset;
                index < source.length();
                index++) {
            char character = source.charAt(index);

            if (character == '\n'
                    || character == '\r') {
                return index;
            }
        }

        return source.length();
    }

    private static boolean horizontalWhitespaceOnly(
            String source,
            int start,
            int end) {
        for (int index = start;
                index < end;
                index++) {
            char character = source.charAt(index);

            if (character != ' '
                    && character != '\t') {
                return false;
            }
        }

        return true;
    }

    private static boolean containsLogicalNewline(
            String source,
            int start,
            int end) {
        for (int index = start;
                index < end;
                index++) {
            char character = source.charAt(index);

            if (character == '\n'
                    || character == '\r') {
                return true;
            }
        }

        return false;
    }

    private record ResolvedBoundary(
            StructuralPath preceding,
            StructuralPath following,
            CommentBoundaryKind kind,
            Optional<SequenceSeparatorKind>
                    sequenceSeparatorKind) {
    }

    private record StructuralNode(
            Object node,
            SourceSpan span,
            StructuralPath path) {
    }

    private static final class StructuralIndex {
        private final Map<Object, StructuralPath> paths =
                new IdentityHashMap<>();

        private final List<StructuralNode> nodes =
                new ArrayList<>();

        static StructuralIndex create(
                SurfaceSequence program) {
            StructuralIndex index = new StructuralIndex();
            index.registerExpression(
                    program,
                    StructuralPath.programRoot());
            return index;
        }

        Optional<StructuralPath> pathOf(Object node) {
            return Optional.ofNullable(paths.get(node));
        }

        Optional<StructuralPath> nearestPreceding(
                SourceSpan comment) {
            StructuralNode best = null;

            for (StructuralNode candidate : nodes) {
                if (candidate.span().endOffset()
                        > comment.startOffset()) {
                    continue;
                }

                if (best == null
                        || candidate.span().endOffset()
                                > best.span().endOffset()
                        || candidate.span().endOffset()
                                        == best.span().endOffset()
                                && deeper(
                                        candidate.path(),
                                        best.path())) {
                    best = candidate;
                }
            }

            return best == null
                    ? Optional.empty()
                    : Optional.of(best.path());
        }

        Optional<StructuralPath> nearestFollowing(
                SourceSpan comment) {
            StructuralNode best = null;

            for (StructuralNode candidate : nodes) {
                if (candidate.span().startOffset()
                        < comment.endOffset()) {
                    continue;
                }

                if (best == null
                        || candidate.span().startOffset()
                                < best.span().startOffset()
                        || candidate.span().startOffset()
                                        == best.span().startOffset()
                                && deeper(
                                        candidate.path(),
                                        best.path())) {
                    best = candidate;
                }
            }

            return best == null
                    ? Optional.empty()
                    : Optional.of(best.path());
        }

        private static boolean deeper(
                StructuralPath left,
                StructuralPath right) {
            return left.steps().size()
                    > right.steps().size();
        }

        private void register(
                Object node,
                SourceSpan span,
                StructuralPath path) {
            Objects.requireNonNull(node, "node");
            Objects.requireNonNull(span, "span");
            Objects.requireNonNull(path, "path");

            paths.put(node, path);
            nodes.add(
                    new StructuralNode(
                            node,
                            span,
                            path));
        }

        private void registerArgument(
                SurfaceArgument argument,
                StructuralPath path) {
            register(argument, argument.span(), path);
            registerExpression(
                    argument.expression(),
                    path.child("expression"));
        }

        private void registerParameter(
                SurfaceParameter parameter,
                StructuralPath path) {
            register(parameter, parameter.span(), path);
            parameter.defaultValue()
                    .ifPresent(
                            value ->
                                    registerExpression(
                                            value,
                                            path.child("defaultValue")));
        }

        private void registerObjectItem(
                SurfaceObjectItem item,
                StructuralPath path) {
            register(item, item.span(), path);
            registerExpression(
                    item.expression(),
                    path.child("expression"));
        }

        private void registerMapEntry(
                SurfaceMapConstruction.Entry entry,
                StructuralPath path) {
            register(entry, entry.span(), path);
            registerExpression(
                    entry.key(),
                    path.child("key"));
            registerExpression(
                    entry.value(),
                    path.child("value"));
        }

        private void registerExpression(
                SurfaceExpression expression,
                StructuralPath path) {
            register(expression, expression.span(), path);

            switch (expression) {
                case SurfaceLiteral ignored -> {
                }
                case SurfaceName ignored -> {
                }
                case SurfaceIntrinsic ignored -> {
                }
                case SurfaceSequence sequence -> {
                    for (int index = 0;
                            index < sequence.expressions().size();
                            index++) {
                        registerExpression(
                                sequence.expressions().get(index),
                                path.child("expression", index));
                    }
                }
                case SurfaceGroup group ->
                        registerExpression(
                                group.expression(),
                                path.child("expression"));
                case SurfaceMember member ->
                        registerExpression(
                                member.receiver(),
                                path.child("receiver"));
                case SurfaceCall call -> {
                    registerExpression(
                            call.receiver(),
                            path.child("receiver"));
                    for (int index = 0;
                            index < call.arguments().size();
                            index++) {
                        registerArgument(
                                call.arguments().get(index),
                                path.child("argument", index));
                    }
                }
                case SurfaceArrayConstruction array -> {
                    for (int index = 0;
                            index < array.arguments().size();
                            index++) {
                        registerArgument(
                                array.arguments().get(index),
                                path.child("argument", index));
                    }
                }
                case SurfaceMapConstruction map -> {
                    for (int index = 0;
                            index < map.entries().size();
                            index++) {
                        registerMapEntry(
                                map.entries().get(index),
                                path.child("entry", index));
                    }
                }
                case SurfaceIndex index -> {
                    registerExpression(
                            index.receiver(),
                            path.child("receiver"));
                    registerExpression(
                            index.index(),
                            path.child("index"));
                }
                case SurfaceUnary unary ->
                        registerExpression(
                                unary.operand(),
                                path.child("operand"));
                case SurfaceBinary binary -> {
                    registerExpression(
                            binary.left(),
                            path.child("left"));
                    registerExpression(
                            binary.right(),
                            path.child("right"));
                }
                case SurfaceNonLocalReturn nonLocalReturn ->
                        registerExpression(
                                nonLocalReturn.expression(),
                                path.child("expression"));
                case SurfaceSlotCreation creation -> {
                    registerExpression(
                            creation.target(),
                            path.child("target"));
                    registerExpression(
                            creation.value(),
                            path.child("value"));
                }
                case SurfaceMultipleSlotCreation creation -> {
                    for (int index = 0;
                            index < creation.targets().size();
                            index++) {
                        registerExpression(
                                creation.targets().get(index),
                                path.child("target", index));
                    }
                    registerExpression(
                            creation.value(),
                            path.child("value"));
                }
                case SurfaceAssignment assignment -> {
                    registerExpression(
                            assignment.target(),
                            path.child("target"));
                    registerExpression(
                            assignment.value(),
                            path.child("value"));
                }
                case SurfaceSuperSend superSend -> {
                    for (int index = 0;
                            index < superSend.arguments().size();
                            index++) {
                        registerArgument(
                                superSend.arguments().get(index),
                                path.child("argument", index));
                    }
                }
                case SurfaceObject object -> {
                    object.parent()
                            .ifPresent(
                                    parent ->
                                            registerExpression(
                                                    parent,
                                                    path.child("parent")));
                    for (int index = 0;
                            index < object.items().size();
                            index++) {
                        registerObjectItem(
                                object.items().get(index),
                                path.child("item", index));
                    }
                }
                case SurfaceClosure closure -> {
                    for (int index = 0;
                            index < closure.parameters().size();
                            index++) {
                        registerParameter(
                                closure.parameters().get(index),
                                path.child("parameter", index));
                    }
                    registerExpression(
                            closure.body(),
                            path.child("body"));
                }
            }
        }
    }
}
