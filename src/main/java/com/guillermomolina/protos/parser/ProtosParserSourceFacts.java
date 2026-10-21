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

import com.guillermomolina.protos.parser.ast.SurfaceCall;
import com.guillermomolina.protos.parser.ast.SurfaceClosure;
import com.guillermomolina.protos.parser.ast.SurfaceContextualIf;
import com.guillermomolina.protos.parser.ast.SurfaceContextualWhile;
import com.guillermomolina.protos.parser.ast.SurfaceExpression;
import com.guillermomolina.protos.parser.ast.SurfaceMapConstruction;
import com.guillermomolina.protos.parser.ast.SurfaceObjectItem;
import com.guillermomolina.protos.source.SourceSpan;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ProtosParserSourceFacts {
    public enum SequenceContext {
        PROGRAM,
        CLOSURE_BODY,
        OBJECT_BODY,
        MAP_CONSTRUCTION
    }

    public enum SequenceSeparatorKind {
        SEMICOLON,
        LOGICAL_NEWLINE
    }

    public enum SingleParameterClosureForm {
        BARE,
        PARENTHESIZED
    }

    public sealed interface SequenceElement
            permits ExpressionElement, ObjectItemElement, MapEntryElement {
        SourceSpan span();
    }

    public record ExpressionElement(SurfaceExpression expression)
            implements SequenceElement {
        public ExpressionElement {
            Objects.requireNonNull(expression, "expression");
        }

        @Override
        public SourceSpan span() {
            return expression.span();
        }
    }

    public record ObjectItemElement(SurfaceObjectItem item)
            implements SequenceElement {
        public ObjectItemElement {
            Objects.requireNonNull(item, "item");
        }

        @Override
        public SourceSpan span() {
            return item.span();
        }
    }

    public record MapEntryElement(SurfaceMapConstruction.Entry entry)
            implements SequenceElement {
        public MapEntryElement {
            Objects.requireNonNull(entry, "entry");
        }

        @Override
        public SourceSpan span() {
            return entry.span();
        }
    }

    public record SequenceSeparatorFact(
            SequenceContext context,
            SequenceElement preceding,
            SequenceSeparatorKind kind,
            SourceSpan separatorSpan,
            SequenceElement following) {
        public SequenceSeparatorFact {
            Objects.requireNonNull(context, "context");
            Objects.requireNonNull(preceding, "preceding");
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(separatorSpan, "separatorSpan");
            Objects.requireNonNull(following, "following");
        }
    }

    public record SingleParameterClosureSourceForm(
            SurfaceClosure closure,
            SingleParameterClosureForm form) {
        public SingleParameterClosureSourceForm {
            Objects.requireNonNull(closure, "closure");
            Objects.requireNonNull(form, "form");
        }
    }

    /**
     * Attached trailing braced Closure and the exact Surface node that owns it:
     * an ordinary {@link SurfaceCall}, a D210-Q2 {@link SurfaceContextualWhile},
     * or a D211 {@link SurfaceContextualIf} (whose true branch is the attached
     * trailing Closure; its false branch is a {@link ContextualElseOrigin}).
     */
    public record TrailingClosureOrigin(
            SurfaceExpression owner,
            SurfaceClosure closure) {
        public TrailingClosureOrigin {
            Objects.requireNonNull(owner, "owner");
            Objects.requireNonNull(closure, "closure");
            if (!(owner instanceof SurfaceCall)
                    && !(owner instanceof SurfaceContextualWhile)
                    && !(owner instanceof SurfaceContextualIf)) {
                throw new IllegalArgumentException(
                        "trailing Closure owner must be a call, contextual while,"
                                + " or contextual if: "
                                + owner.getClass().getSimpleName());
            }
        }
    }

    /**
     * D211 paired {@code else} suffix of a two-arm contextual if: the owning
     * node, the exact {@code else} token span, and the false-branch Closure.
     * Kept distinct from {@link TrailingClosureOrigin} so every owner has at
     * most one attached trailing Closure.
     */
    public record ContextualElseOrigin(
            SurfaceContextualIf owner,
            SourceSpan elseSpan,
            SurfaceClosure falseBranch) {
        public ContextualElseOrigin {
            Objects.requireNonNull(owner, "owner");
            Objects.requireNonNull(elseSpan, "elseSpan");
            Objects.requireNonNull(falseBranch, "falseBranch");
            if (owner.falseBranch() != falseBranch) {
                throw new IllegalArgumentException(
                        "contextual else Closure must be the owner's false branch");
            }
        }
    }

    private final List<SequenceSeparatorFact> sequenceSeparators;
    private final List<SingleParameterClosureSourceForm> singleParameterClosureForms;
    private final List<TrailingClosureOrigin> trailingClosureOrigins;
    private final List<ContextualElseOrigin> contextualElseOrigins;

    private ProtosParserSourceFacts(
            List<SequenceSeparatorFact> sequenceSeparators,
            List<SingleParameterClosureSourceForm> singleParameterClosureForms,
            List<TrailingClosureOrigin> trailingClosureOrigins,
            List<ContextualElseOrigin> contextualElseOrigins) {
        this.sequenceSeparators = List.copyOf(sequenceSeparators);
        this.singleParameterClosureForms = List.copyOf(singleParameterClosureForms);
        this.trailingClosureOrigins = List.copyOf(trailingClosureOrigins);
        this.contextualElseOrigins = List.copyOf(contextualElseOrigins);
    }

    public List<SequenceSeparatorFact> sequenceSeparators() {
        return sequenceSeparators;
    }

    public List<SingleParameterClosureSourceForm> singleParameterClosureForms() {
        return singleParameterClosureForms;
    }

    public List<TrailingClosureOrigin> trailingClosureOrigins() {
        return trailingClosureOrigins;
    }

    public List<ContextualElseOrigin> contextualElseOrigins() {
        return contextualElseOrigins;
    }

    public static final class Builder {
        private final List<SequenceSeparatorFact> sequenceSeparators =
                new ArrayList<>();
        private final List<SingleParameterClosureSourceForm> singleParameterClosureForms =
                new ArrayList<>();
        private final List<TrailingClosureOrigin> trailingClosureOrigins =
                new ArrayList<>();
        private final List<ContextualElseOrigin> contextualElseOrigins =
                new ArrayList<>();

        void recordExpressionSeparator(
                SequenceContext context,
                SurfaceExpression preceding,
                SequenceSeparatorKind kind,
                SourceSpan separatorSpan,
                SurfaceExpression following) {
            sequenceSeparators.add(
                    new SequenceSeparatorFact(
                            context,
                            new ExpressionElement(preceding),
                            kind,
                            separatorSpan,
                            new ExpressionElement(following)));
        }

        void recordObjectItemSeparator(
                SurfaceObjectItem preceding,
                SequenceSeparatorKind kind,
                SourceSpan separatorSpan,
                SurfaceObjectItem following) {
            sequenceSeparators.add(
                    new SequenceSeparatorFact(
                            SequenceContext.OBJECT_BODY,
                            new ObjectItemElement(preceding),
                            kind,
                            separatorSpan,
                            new ObjectItemElement(following)));
        }

        void recordMapEntrySeparator(
                SurfaceMapConstruction.Entry preceding,
                SequenceSeparatorKind kind,
                SourceSpan separatorSpan,
                SurfaceMapConstruction.Entry following) {
            sequenceSeparators.add(
                    new SequenceSeparatorFact(
                            SequenceContext.MAP_CONSTRUCTION,
                            new MapEntryElement(preceding),
                            kind,
                            separatorSpan,
                            new MapEntryElement(following)));
        }

        void recordSingleParameterClosureForm(
                SurfaceClosure closure,
                SingleParameterClosureForm form) {
            singleParameterClosureForms.add(
                    new SingleParameterClosureSourceForm(closure, form));
        }

        void recordTrailingClosureOrigin(
                SurfaceExpression owner,
                SurfaceClosure closure) {
            trailingClosureOrigins.add(new TrailingClosureOrigin(owner, closure));
        }

        void recordContextualElseOrigin(
                SurfaceContextualIf owner,
                SourceSpan elseSpan,
                SurfaceClosure falseBranch) {
            contextualElseOrigins.add(
                    new ContextualElseOrigin(owner, elseSpan, falseBranch));
        }

        public ProtosParserSourceFacts build() {
            return new ProtosParserSourceFacts(
                    sequenceSeparators,
                    singleParameterClosureForms,
                    trailingClosureOrigins,
                    contextualElseOrigins);
        }
    }
}
