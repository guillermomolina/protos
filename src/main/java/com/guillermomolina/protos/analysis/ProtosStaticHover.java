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
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * LM010-A static hover projection over one parser-authoritative snapshot.
 *
 * <p>Proven-binding facts are exactly the D110 generation-1 definition proofs
 * of {@link ProtosStaticDefinitions}; this class never widens them. Syntax
 * facts describe only the surface node under the offset: a literal token, a
 * Closure parameter declaration name, or the first token of a Closure. They
 * never claim that a name, member, or call resolves to that syntax. Anything
 * else, including unproven names, members, and runtime values, is an ordinary
 * empty result. No guest code is executed.</p>
 */
final class ProtosStaticHover {
    private ProtosStaticHover() {
    }

    static Optional<ProtosStaticHoverResult> resolve(
            ProtosStaticParseResult.Parsed parsed,
            int sourceOffset) {
        Objects.requireNonNull(parsed, "parsed");
        if (sourceOffset < 0 || sourceOffset >= parsed.snapshot().characters().length()) {
            return Optional.empty();
        }

        Optional<ProtosStaticDefinitionResult> proven =
                ProtosStaticDefinitions.resolve(parsed, sourceOffset);
        if (proven.isPresent()) {
            return provenBinding(parsed, proven.get());
        }
        return Optional.ofNullable(
                new SyntaxFinder(parsed.snapshot(), sourceOffset).find(parsed.program()));
    }

    private static Optional<ProtosStaticHoverResult> provenBinding(
            ProtosStaticParseResult.Parsed parsed,
            ProtosStaticDefinitionResult proven) {
        if (proven.targets().size() != 1
                || !proven.referenceSnapshot().equals(parsed.snapshot())
                || !proven.targets().get(0).snapshot().equals(parsed.snapshot())) {
            return Optional.empty();
        }

        // D110 generation 1 proves only Closure parameter origins. Describe the
        // proof only when its target is exactly one parsed parameter declaration.
        SurfaceParameter parameter =
                findParameter(parsed.program(), proven.targets().get(0).span());
        if (parameter == null) {
            return Optional.empty();
        }

        List<ProtosStaticHoverResult.Section> sections = new ArrayList<>();
        sections.add(new ProtosStaticHoverResult.Section(
                ProtosStaticHoverResult.Kind.PROVEN_BINDING,
                List.of("Closure parameter: " + parameter.name())));
        List<String> declaration = parameterModifiers(parameter);
        if (!declaration.isEmpty()) {
            sections.add(new ProtosStaticHoverResult.Section(
                    ProtosStaticHoverResult.Kind.SYNTAX,
                    declaration));
        }
        return Optional.of(new ProtosStaticHoverResult(
                parsed.snapshot(),
                proven.referenceSpan(),
                sections));
    }

    private static SurfaceParameter findParameter(SurfaceExpression expression, SourceSpan span) {
        if (!encloses(expression.span(), span)) {
            return null;
        }
        if (expression instanceof SurfaceClosure closure) {
            for (SurfaceParameter parameter : closure.parameters()) {
                if (parameter.span().equals(span)) {
                    return parameter;
                }
            }
        }
        for (SurfaceExpression child : children(expression)) {
            SurfaceParameter found = findParameter(child, span);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static final class SyntaxFinder {
        private final ProtosDocumentSnapshot snapshot;
        private final int sourceOffset;

        SyntaxFinder(ProtosDocumentSnapshot snapshot, int sourceOffset) {
            this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
            this.sourceOffset = sourceOffset;
        }

        ProtosStaticHoverResult find(SurfaceExpression expression) {
            if (!contains(expression.span(), sourceOffset)) {
                return null;
            }
            if (expression instanceof SurfaceLiteral literal) {
                return syntax(literal.span(), List.of(literalCategory(literal.kind())));
            }
            if (expression instanceof SurfaceClosure closure) {
                ProtosStaticHoverResult closureHover = findInClosureHead(closure);
                if (closureHover != null) {
                    return closureHover;
                }
            }
            for (SurfaceExpression child : children(expression)) {
                ProtosStaticHoverResult found = find(child);
                if (found != null) {
                    return found;
                }
            }
            return null;
        }

        private ProtosStaticHoverResult findInClosureHead(SurfaceClosure closure) {
            String source = snapshot.characters();
            for (SurfaceParameter parameter : closure.parameters()) {
                Optional<SourceSpan> name = parameterNameSpan(source, parameter);
                if (name.isPresent() && contains(name.get(), sourceOffset)) {
                    List<String> facts = new ArrayList<>();
                    facts.add("Closure parameter declaration: " + parameter.name());
                    facts.addAll(parameterModifiers(parameter));
                    facts.add("Closure parameters: " + parameterList(closure));
                    return syntax(name.get(), facts);
                }
            }

            // A parameter-list Closure starts at '(' and a parameterless braced
            // Closure starts at '{'. A bare-parameter Closure starts at its
            // parameter name, which the declaration branch above already owns.
            int start = closure.span().startOffset();
            if (sourceOffset == start
                    && (source.charAt(start) == '(' || source.charAt(start) == '{')) {
                return syntax(
                        new SourceSpan(start, start + 1),
                        List.of(
                                "Closure",
                                "Parameters: " + parameterList(closure),
                                "Body form: " + (closure.expressionBody() ? "expression" : "braced")));
            }
            return null;
        }

        private ProtosStaticHoverResult syntax(SourceSpan span, List<String> facts) {
            return new ProtosStaticHoverResult(
                    snapshot,
                    span,
                    List.of(new ProtosStaticHoverResult.Section(
                            ProtosStaticHoverResult.Kind.SYNTAX,
                            facts)));
        }
    }

    /**
     * Returns the exact identifier span of a parameter declaration.
     *
     * <p>{@link SurfaceParameter#span()} also covers a default expression or a
     * leading rest spread, so it is never published as the name range. A plain
     * or defaulted parameter starts at its identifier; a rest parameter ends at
     * its identifier. The source text must spell the name exactly there.</p>
     */
    static Optional<SourceSpan> parameterNameSpan(String source, SurfaceParameter parameter) {
        int length = parameter.name().length();
        int start = parameter.rest()
                ? parameter.span().endOffset() - length
                : parameter.span().startOffset();
        int end = start + length;
        if (start < parameter.span().startOffset()
                || end > parameter.span().endOffset()
                || end > source.length()
                || !source.regionMatches(start, parameter.name(), 0, length)) {
            return Optional.empty();
        }
        return Optional.of(new SourceSpan(start, end));
    }

    private static List<String> parameterModifiers(SurfaceParameter parameter) {
        if (parameter.rest()) {
            return List.of("Rest parameter");
        }
        if (parameter.defaultValue().isPresent()) {
            return List.of("Default value present");
        }
        return List.of();
    }

    private static String parameterList(SurfaceClosure closure) {
        if (closure.parameters().isEmpty()) {
            return "none";
        }
        List<String> rendered = new ArrayList<>();
        for (SurfaceParameter parameter : closure.parameters()) {
            if (parameter.rest()) {
                rendered.add("..." + parameter.name());
            } else if (parameter.defaultValue().isPresent()) {
                rendered.add(parameter.name() + " = <default>");
            } else {
                rendered.add(parameter.name());
            }
        }
        return "(" + String.join(", ", rendered) + ")";
    }

    private static String literalCategory(SurfaceLiteral.Kind kind) {
        return switch (kind) {
            case NUMBER -> "Number literal";
            case STRING -> "String literal";
            case TRUE -> "Literal: true";
            case FALSE -> "Literal: false";
            case NULL -> "Literal: null";
        };
    }

    /** Direct surface sub-expressions in source order; shared with LM010-B completion. */
    static List<SurfaceExpression> children(SurfaceExpression expression) {
        List<SurfaceExpression> children = new ArrayList<>();
        switch (expression) {
            case SurfaceLiteral ignored -> {
            }
            case SurfaceName ignored -> {
            }
            case SurfaceIntrinsic ignored -> {
            }
            case SurfaceSequence sequence -> children.addAll(sequence.expressions());
            case SurfaceGroup group -> children.add(group.expression());
            case SurfaceMember member -> children.add(member.receiver());
            case SurfaceCall call -> {
                children.add(call.receiver());
                addArguments(children, call.arguments());
            }
            case SurfaceArrayConstruction array -> addArguments(children, array.arguments());
            case SurfaceMapConstruction map -> {
                for (SurfaceMapConstruction.Entry entry : map.entries()) {
                    children.add(entry.key());
                    children.add(entry.value());
                }
            }
            case SurfaceIndex index -> {
                children.add(index.receiver());
                children.add(index.index());
            }
            case SurfaceUnary unary -> children.add(unary.operand());
            case SurfaceBinary binary -> {
                children.add(binary.left());
                children.add(binary.right());
            }
            case SurfaceNonLocalReturn nonLocalReturn -> children.add(nonLocalReturn.expression());
            case SurfaceSlotCreation creation -> {
                children.add(creation.target());
                children.add(creation.value());
            }
            case SurfaceMultipleSlotCreation creation -> children.add(creation.value());
            case SurfaceAssignment assignment -> {
                children.add(assignment.target());
                children.add(assignment.value());
            }
            case SurfaceSuperSend superSend -> addArguments(children, superSend.arguments());
            case SurfaceObject object -> {
                object.parent().ifPresent(children::add);
                for (SurfaceObjectItem item : object.items()) {
                    children.add(item.expression());
                }
            }
            case SurfaceClosure closure -> {
                for (SurfaceParameter parameter : closure.parameters()) {
                    parameter.defaultValue().ifPresent(children::add);
                }
                children.add(closure.body());
            }
        }
        return children;
    }

    private static void addArguments(List<SurfaceExpression> children, List<SurfaceArgument> arguments) {
        for (SurfaceArgument argument : arguments) {
            children.add(argument.expression());
        }
    }

    private static boolean contains(SourceSpan span, int offset) {
        return span.startOffset() <= offset && offset < span.endOffset();
    }

    private static boolean encloses(SourceSpan outer, SourceSpan inner) {
        return outer.startOffset() <= inner.startOffset() && inner.endOffset() <= outer.endOffset();
    }
}
