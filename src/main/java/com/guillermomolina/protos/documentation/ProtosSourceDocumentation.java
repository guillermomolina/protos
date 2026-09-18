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

package com.guillermomolina.protos.documentation;

import com.guillermomolina.protos.analysis.ProtosDocumentSymbol;
import com.guillermomolina.protos.analysis.ProtosDocumentSymbols;
import com.guillermomolina.protos.lexer.ProtosLexer;
import com.guillermomolina.protos.lexer.ProtosLexer.LineCommentOccurrence;
import com.guillermomolina.protos.parser.ProtosParser;
import com.guillermomolina.protos.source.SourceSpan;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * D138 source-local documentation projection.
 *
 * <p>This layer identifies source owners and deterministically associates authored
 * source documentation without changing Protos execution semantics.</p>
 */
public final class ProtosSourceDocumentation {
    private ProtosSourceDocumentation() {
    }

    /**
     * Returns the documentable owners in one current source snapshot.
     *
     * <p>The source unit itself is the module owner. Named slot owners are
     * explicit Surface slot-creation occurrences in source order.</p>
     */
    public static SourceOwners owners(String source) {
        String normalizedSource = source == null ? "" : source;

        List<SlotOwner> slots = new ArrayList<>();
        for (ProtosDocumentSymbol symbol :
                ProtosDocumentSymbols.from(
                        new ProtosParser(normalizedSource).parseProgram())) {
            collect(symbol, slots);
        }

        slots.sort(Comparator.comparingInt(slot -> slot.span().startOffset()));

        return new SourceOwners(
                new SourceSpan(0, normalizedSource.length()),
                slots);
    }

    /**
     * Returns authored module documentation from the current source snapshot.
     *
     * <p>A module documentation block is one contiguous sequence of line-leading
     * {@code //!} comments in the module preamble. At most one block is valid.</p>
     *
     * @return authored module documentation, or {@code null} when absent
     */
    public static String moduleDocumentation(String source) {
        String normalizedSource = source == null ? "" : source;

        List<LineCommentOccurrence> comments = new ArrayList<>();
        new ProtosLexer(normalizedSource).tokenizeOccurrences(comments::add);

        var program = new ProtosParser(normalizedSource).parseProgram();
        int firstConstructOffset = program.expressions().isEmpty()
                ? normalizedSource.length()
                : program.expressions().get(0).span().startOffset();

        StringBuilder documentation = null;
        SourceSpan previousLineSpan = null;

        for (LineCommentOccurrence comment : comments) {
            String text = comment.text();
            if (!text.startsWith("!")) {
                continue;
            }
            if (!isLineLeadingComment(normalizedSource, comment.span().startOffset())) {
                throw documentationError(
                        "documentation markers must begin a documentation line",
                        comment.span());
            }
            if (comment.span().startOffset() >= firstConstructOffset) {
                throw documentationError(
                        "`//!` is only valid in the module preamble",
                        comment.span());
            }

            if (documentation == null) {
                documentation = new StringBuilder(documentationLine(text));
            } else {
                if (!isSingleLogicalLineGap(
                        normalizedSource,
                        previousLineSpan.endOffset(),
                        comment.span().startOffset())) {
                    throw documentationError(
                            "at most one `//!` module block is permitted",
                            comment.span());
                }
                documentation.append('\n').append(documentationLine(text));
            }

            previousLineSpan = comment.span();
        }

        return documentation == null ? null : documentation.toString();
    }

    /**
     * Returns authored documentation associated with named slot-creation owners.
     *
     * <p>Each contiguous line-leading {@code ///} block must immediately precede
     * its exact source-local owner. Parenthesized grouping is transparent; blank
     * lines, ordinary comments, unrelated constructs, and non-owner forms break
     * association and fail closed.</p>
     */
    public static List<SlotDocumentation> slotDocumentation(String source) {
        String normalizedSource = source == null ? "" : source;

        List<LineCommentOccurrence> comments = new ArrayList<>();
        new ProtosLexer(normalizedSource).tokenizeOccurrences(comments::add);

        List<SlotOwner> slots = owners(normalizedSource).slots();
        List<SlotDocumentation> result = new ArrayList<>();

        int commentIndex = 0;
        while (commentIndex < comments.size()) {
            LineCommentOccurrence first = comments.get(commentIndex);
            if (!first.text().startsWith("/")) {
                commentIndex++;
                continue;
            }
            if (!isLineLeadingComment(normalizedSource, first.span().startOffset())) {
                throw documentationError(
                        "documentation markers must begin a documentation line",
                        first.span());
            }

            StringBuilder documentation =
                    new StringBuilder(documentationLine(first.text()));
            SourceSpan blockSpan = first.span();
            commentIndex++;

            while (commentIndex < comments.size()) {
                LineCommentOccurrence next = comments.get(commentIndex);
                if (!next.text().startsWith("/")) {
                    break;
                }
                if (!isLineLeadingComment(normalizedSource, next.span().startOffset())) {
                    throw documentationError(
                            "documentation markers must begin a documentation line",
                            next.span());
                }
                if (!isSingleLogicalLineGap(
                        normalizedSource,
                        blockSpan.endOffset(),
                        next.span().startOffset())) {
                    break;
                }

                documentation.append('\n').append(documentationLine(next.text()));
                blockSpan = new SourceSpan(
                        blockSpan.startOffset(),
                        next.span().endOffset());
                commentIndex++;
            }

            SlotOwner target = null;
            for (SlotOwner slot : slots) {
                if (slot.span().startOffset() > blockSpan.endOffset()) {
                    target = slot;
                    break;
                }
            }

            if (target == null
                    || !isTransparentSlotGap(
                            normalizedSource,
                            blockSpan.endOffset(),
                            target.span().startOffset())) {
                throw documentationError(
                        "`///` must immediately precede a documentable named slot creation",
                        blockSpan);
            }

            result.add(new SlotDocumentation(target, documentation.toString()));
        }

        return List.copyOf(result);
    }

    /** Owners belonging to one module source unit. */
    public record SourceOwners(
            SourceSpan sourceUnitSpan,
            List<SlotOwner> slots) {
        public SourceOwners {
            Objects.requireNonNull(sourceUnitSpan, "sourceUnitSpan");
            slots = List.copyOf(Objects.requireNonNull(slots, "slots"));
        }
    }

    /** Authored documentation associated with one exact source-local slot owner. */
    public record SlotDocumentation(
            SlotOwner owner,
            String documentation) {
        public SlotDocumentation {
            Objects.requireNonNull(owner, "owner");
            Objects.requireNonNull(documentation, "documentation");
        }
    }

    /** One explicit named slot-creation occurrence. */
    public record SlotOwner(
            String name,
            SourceSpan span,
            SourceSpan selectionRange) {
        public SlotOwner {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(span, "span");
            Objects.requireNonNull(selectionRange, "selectionRange");
        }
    }

    private static boolean isTransparentSlotGap(String source, int from, int to) {
        if (from < 0 || to < from || to > source.length() || from == to) {
            return false;
        }

        int index = from;
        if (source.charAt(index) == '\r') {
            index++;
            if (index < to && source.charAt(index) == '\n') {
                index++;
            }
        } else if (source.charAt(index) == '\n') {
            index++;
        } else {
            return false;
        }

        boolean lineHasGrouping = false;
        while (index < to) {
            char current = source.charAt(index);
            if (current == ' ' || current == '\t') {
                index++;
                continue;
            }
            if (current == '(') {
                lineHasGrouping = true;
                index++;
                continue;
            }
            if (current == '\r' || current == '\n') {
                if (!lineHasGrouping) {
                    return false;
                }
                if (current == '\r') {
                    index++;
                    if (index < to && source.charAt(index) == '\n') {
                        index++;
                    }
                } else {
                    index++;
                }
                lineHasGrouping = false;
                continue;
            }
            return false;
        }
        return true;
    }

    private static String documentationLine(String commentText) {
        String body = commentText.substring(1);
        return body.startsWith(" ") ? body.substring(1) : body;
    }

    private static boolean isLineLeadingComment(String source, int commentStart) {
        int index = commentStart;
        while (index > 0) {
            char previous = source.charAt(index - 1);
            if (previous == '\n' || previous == '\r') {
                break;
            }
            index--;
        }
        while (index < commentStart) {
            char current = source.charAt(index);
            if (current != ' ' && current != '\t') {
                return false;
            }
            index++;
        }
        return true;
    }

    private static boolean isSingleLogicalLineGap(String source, int from, int to) {
        if (from < 0 || to < from || to > source.length() || from == to) {
            return false;
        }

        int index = from;
        if (source.charAt(index) == '\r') {
            index++;
            if (index < to && source.charAt(index) == '\n') {
                index++;
            }
        } else if (source.charAt(index) == '\n') {
            index++;
        } else {
            return false;
        }

        while (index < to) {
            char current = source.charAt(index);
            if (current != ' ' && current != '\t') {
                return false;
            }
            index++;
        }
        return true;
    }

    private static IllegalArgumentException documentationError(
            String message,
            SourceSpan span) {
        return new IllegalArgumentException(
                "documentation validation error at offsets "
                        + span.startOffset()
                        + ".."
                        + span.endOffset()
                        + ": "
                        + message);
    }

    private static void collect(
            ProtosDocumentSymbol symbol,
            List<SlotOwner> destination) {
        destination.add(new SlotOwner(
                symbol.name(),
                symbol.range(),
                symbol.selectionRange()));

        for (ProtosDocumentSymbol child : symbol.children()) {
            collect(child, destination);
        }
    }
}
