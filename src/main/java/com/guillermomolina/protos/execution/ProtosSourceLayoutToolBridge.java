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

package com.guillermomolina.protos.execution;

import com.guillermomolina.protos.analysis.ProtosSourceLayoutView;
import com.guillermomolina.protos.analysis.ProtosSourceLayoutView.ClosureFormProjection;
import com.guillermomolina.protos.analysis.ProtosSourceLayoutView.CommentProjection;
import com.guillermomolina.protos.analysis.ProtosSourceLayoutView.SequenceSeparatorProjection;
import com.guillermomolina.protos.analysis.ProtosSourceLayoutView.SourcePreservationProjection;
import com.guillermomolina.protos.analysis.ProtosSourceLayoutView.StructuralFormProjection;
import com.guillermomolina.protos.analysis.ProtosSourceLayoutView.StructuralPath;
import com.guillermomolina.protos.analysis.ProtosSourceLayoutView.StructuralStep;
import com.guillermomolina.protos.analysis.ProtosSourceLayoutView.TokenSpelling;
import com.guillermomolina.protos.analysis.ProtosSourceLayoutView.TrailingClosureProjection;
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
import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosIntegerValue;
import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosStringValue;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Tool-neutral projection of B1 source-layout evidence into inert ordinary Protos values.
 *
 * <p>This bridge performs no formatting decisions. It preserves exact raw source/token/comment
 * spelling, the canonical Surface AST, and source-position-independent structural facts already
 * owned by {@link ProtosSourceLayoutView}. It introduces no parser, grammar, source
 * reconstruction, indentation, spacing, wrapping, comment-placement, or line-break policy.
 *
 * <p>The projection is constructed only on explicit tooling demand. It is not cached globally and
 * cannot add formatter-only retention or runtime cost to ordinary parsing/execution.
 */
public final class ProtosSourceLayoutToolBridge {
    private ProtosSourceLayoutToolBridge() {}

    public static ProtosObjectValue project(
            ProtosSourceLayoutView view,
            ProtosPrelude toolPrelude) {
        Objects.requireNonNull(view, "view");
        Objects.requireNonNull(toolPrelude, "toolPrelude");

        SourcePreservationProjection projection =
                view.preservationProjection();

        Map<StructuralPath, ProtosObjectValue> paths =
                new HashMap<>();

        ProtosObjectValue root = object();
        root.createLocalSlot(
                "exactSource",
                string(view.snapshot().characters()));
        root.createLocalSlot(
                "program",
                expression(
                        view.program(),
                        view,
                        paths,
                        toolPrelude));
        root.createLocalSlot(
                "tokens",
                tokens(projection.tokens(), toolPrelude));
        root.createLocalSlot(
                "comments",
                comments(
                        projection.comments(),
                        paths,
                        toolPrelude));
        root.createLocalSlot(
                "structuralForms",
                structuralForms(
                        projection.structuralForms(),
                        paths,
                        toolPrelude));
        root.createLocalSlot(
                "sequenceSeparators",
                sequenceSeparators(
                        projection.sequenceSeparators(),
                        paths,
                        toolPrelude));
        root.createLocalSlot(
                "closureForms",
                closureForms(
                        projection.closureForms(),
                        paths,
                        toolPrelude));
        root.createLocalSlot(
                "trailingClosures",
                trailingClosures(
                        projection.trailingClosures(),
                        paths,
                        toolPrelude));
        root.freeze();
        return root;
    }

    private static ProtosObjectValue expression(
            SurfaceExpression source,
            ProtosSourceLayoutView view,
            Map<StructuralPath, ProtosObjectValue> paths,
            ProtosPrelude prelude) {
        ProtosObjectValue value = object();
        value.createLocalSlot(
                "kind",
                string(expressionKind(source)));
        value.createLocalSlot(
                "path",
                expressionPath(
                        source,
                        view,
                        paths,
                        prelude));

        switch (source) {
            case SurfaceLiteral literal -> {
                value.createLocalSlot(
                        "literalKind",
                        string(literal.kind().name()));
                value.createLocalSlot(
                        "rawText",
                        string(view.sourceText(literal.span())));
            }
            case SurfaceName name ->
                    value.createLocalSlot(
                            "rawText",
                            string(view.sourceText(name.span())));
            case SurfaceIntrinsic intrinsic -> {
                value.createLocalSlot(
                        "intrinsicKind",
                        string(intrinsic.kind().name()));
                value.createLocalSlot(
                        "rawText",
                        string(view.sourceText(intrinsic.span())));
            }
            case SurfaceSequence sequence ->
                    value.createLocalSlot(
                            "expressions",
                            expressions(
                                    sequence.expressions(),
                                    view,
                                    paths,
                                    prelude));
            case SurfaceGroup group ->
                    value.createLocalSlot(
                            "expression",
                            expression(
                                    group.expression(),
                                    view,
                                    paths,
                                    prelude));
            case SurfaceMember member -> {
                value.createLocalSlot(
                        "receiver",
                        expression(
                                member.receiver(),
                                view,
                                paths,
                                prelude));
                value.createLocalSlot(
                        "name",
                        string(member.name()));
            }
            case SurfaceCall call -> {
                value.createLocalSlot(
                        "receiver",
                        expression(
                                call.receiver(),
                                view,
                                paths,
                                prelude));
                value.createLocalSlot(
                        "arguments",
                        arguments(
                                call.arguments(),
                                view,
                                paths,
                                prelude));
            }
            case SurfaceArrayConstruction array ->
                    value.createLocalSlot(
                            "arguments",
                            arguments(
                                    array.arguments(),
                                    view,
                                    paths,
                                    prelude));
            case SurfaceMapConstruction map ->
                    value.createLocalSlot(
                            "entries",
                            mapEntries(
                                    map.entries(),
                                    view,
                                    paths,
                                    prelude));
            case SurfaceIndex index -> {
                value.createLocalSlot(
                        "receiver",
                        expression(
                                index.receiver(),
                                view,
                                paths,
                                prelude));
                value.createLocalSlot(
                        "index",
                        expression(
                                index.index(),
                                view,
                                paths,
                                prelude));
            }
            case SurfaceUnary unary -> {
                value.createLocalSlot(
                        "operator",
                        string(unary.operator()));
                value.createLocalSlot(
                        "operand",
                        expression(
                                unary.operand(),
                                view,
                                paths,
                                prelude));
            }
            case SurfaceBinary binary -> {
                value.createLocalSlot(
                        "left",
                        expression(
                                binary.left(),
                                view,
                                paths,
                                prelude));
                value.createLocalSlot(
                        "operator",
                        string(binary.operator()));
                value.createLocalSlot(
                        "right",
                        expression(
                                binary.right(),
                                view,
                                paths,
                                prelude));
            }
            case SurfaceNonLocalReturn nonLocalReturn ->
                    value.createLocalSlot(
                            "expression",
                            expression(
                                    nonLocalReturn.expression(),
                                    view,
                                    paths,
                                    prelude));
            case SurfaceSlotCreation creation -> {
                value.createLocalSlot(
                        "target",
                        expression(
                                creation.target(),
                                view,
                                paths,
                                prelude));
                value.createLocalSlot(
                        "value",
                        expression(
                                creation.value(),
                                view,
                                paths,
                                prelude));
            }
            case SurfaceMultipleSlotCreation creation -> {
                value.createLocalSlot(
                        "targets",
                        expressions(
                                creation.targets(),
                                view,
                                paths,
                                prelude));
                value.createLocalSlot(
                        "value",
                        expression(
                                creation.value(),
                                view,
                                paths,
                                prelude));
            }
            case SurfaceAssignment assignment -> {
                value.createLocalSlot(
                        "target",
                        expression(
                                assignment.target(),
                                view,
                                paths,
                                prelude));
                value.createLocalSlot(
                        "value",
                        expression(
                                assignment.value(),
                                view,
                                paths,
                                prelude));
            }
            case SurfaceSuperSend superSend -> {
                value.createLocalSlot(
                        "message",
                        string(superSend.message()));
                value.createLocalSlot(
                        "arguments",
                        arguments(
                                superSend.arguments(),
                                view,
                                paths,
                                prelude));
            }
            case SurfaceObject surfaceObject -> {
                value.createLocalSlot(
                        "parent",
                        surfaceObject.parent().isPresent()
                                ? expression(
                                        surfaceObject.parent().orElseThrow(),
                                        view,
                                        paths,
                                        prelude)
                                : ProtosNullValue.INSTANCE);
                value.createLocalSlot(
                        "items",
                        objectItems(
                                surfaceObject.items(),
                                view,
                                paths,
                                prelude));
            }
            case SurfaceClosure closure -> {
                value.createLocalSlot(
                        "parameters",
                        parameters(
                                closure.parameters(),
                                view,
                                paths,
                                prelude));
                value.createLocalSlot(
                        "body",
                        expression(
                                closure.body(),
                                view,
                                paths,
                                prelude));
                value.createLocalSlot(
                        "expressionBody",
                        ProtosBooleanValue.of(
                                closure.expressionBody()));
            }
        }

        value.freeze();
        return value;
    }

    private static String expressionKind(
            SurfaceExpression source) {
        return switch (source) {
            case SurfaceLiteral ignored -> "LITERAL";
            case SurfaceName ignored -> "NAME";
            case SurfaceIntrinsic ignored -> "INTRINSIC";
            case SurfaceSequence ignored -> "SEQUENCE";
            case SurfaceGroup ignored -> "GROUP";
            case SurfaceMember ignored -> "MEMBER";
            case SurfaceCall ignored -> "CALL";
            case SurfaceArrayConstruction ignored ->
                    "ARRAY_CONSTRUCTION";
            case SurfaceMapConstruction ignored ->
                    "MAP_CONSTRUCTION";
            case SurfaceIndex ignored -> "INDEX";
            case SurfaceUnary ignored -> "UNARY";
            case SurfaceBinary ignored -> "BINARY";
            case SurfaceNonLocalReturn ignored ->
                    "NON_LOCAL_RETURN";
            case SurfaceSlotCreation ignored ->
                    "SLOT_CREATION";
            case SurfaceMultipleSlotCreation ignored ->
                    "MULTIPLE_SLOT_CREATION";
            case SurfaceAssignment ignored ->
                    "ASSIGNMENT";
            case SurfaceSuperSend ignored ->
                    "SUPER_SEND";
            case SurfaceObject ignored -> "OBJECT";
            case SurfaceClosure ignored -> "CLOSURE";
        };
    }

    private static ProtosObjectValue expressionPath(
            SurfaceExpression source,
            ProtosSourceLayoutView view,
            Map<StructuralPath, ProtosObjectValue> paths,
            ProtosPrelude prelude) {
        StructuralPath structuralPath =
                view.pathOf(source)
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "canonical Surface expression "
                                                        + "is absent from B1 structural index"));

        return path(
                structuralPath,
                paths,
                prelude);
    }

    private static Object expressions(
            List<? extends SurfaceExpression> source,
            ProtosSourceLayoutView view,
            Map<StructuralPath, ProtosObjectValue> paths,
            ProtosPrelude prelude) {
        List<Object> values =
                new ArrayList<>(source.size());

        for (SurfaceExpression expression : source) {
            values.add(
                    expression(
                            expression,
                            view,
                            paths,
                            prelude));
        }

        return prelude.newFrozenArray(values);
    }

    private static Object arguments(
            List<SurfaceArgument> source,
            ProtosSourceLayoutView view,
            Map<StructuralPath, ProtosObjectValue> paths,
            ProtosPrelude prelude) {
        List<Object> values =
                new ArrayList<>(source.size());

        for (SurfaceArgument argument : source) {
            ProtosObjectValue value = object();
            value.createLocalSlot(
                    "spread",
                    ProtosBooleanValue.of(argument.spread()));
            value.createLocalSlot(
                    "expression",
                    expression(
                            argument.expression(),
                            view,
                            paths,
                            prelude));
            value.freeze();
            values.add(value);
        }

        return prelude.newFrozenArray(values);
    }

    private static Object mapEntries(
            List<SurfaceMapConstruction.Entry> source,
            ProtosSourceLayoutView view,
            Map<StructuralPath, ProtosObjectValue> paths,
            ProtosPrelude prelude) {
        List<Object> values =
                new ArrayList<>(source.size());

        for (SurfaceMapConstruction.Entry entry : source) {
            ProtosObjectValue value = object();
            value.createLocalSlot(
                    "path",
                    path(
                            view.pathOfNode(entry)
                                    .orElseThrow(
                                            () ->
                                                    new IllegalStateException(
                                                            "canonical Map entry is absent "
                                                                    + "from B1 structural index")),
                            paths,
                            prelude));
            value.createLocalSlot(
                    "key",
                    expression(
                            entry.key(),
                            view,
                            paths,
                            prelude));
            value.createLocalSlot(
                    "value",
                    expression(
                            entry.value(),
                            view,
                            paths,
                            prelude));
            value.freeze();
            values.add(value);
        }

        return prelude.newFrozenArray(values);
    }

    private static Object objectItems(
            List<SurfaceObjectItem> source,
            ProtosSourceLayoutView view,
            Map<StructuralPath, ProtosObjectValue> paths,
            ProtosPrelude prelude) {
        List<Object> values =
                new ArrayList<>(source.size());

        for (SurfaceObjectItem item : source) {
            ProtosObjectValue value = object();
            value.createLocalSlot(
                    "path",
                    path(
                            view.pathOfNode(item)
                                    .orElseThrow(
                                            () ->
                                                    new IllegalStateException(
                                                            "canonical Object item is absent "
                                                                    + "from B1 structural index")),
                            paths,
                            prelude));
            value.createLocalSlot(
                    "composition",
                    ProtosBooleanValue.of(item.composition()));
            value.createLocalSlot(
                    "expression",
                    expression(
                            item.expression(),
                            view,
                            paths,
                            prelude));
            value.freeze();
            values.add(value);
        }

        return prelude.newFrozenArray(values);
    }

    private static Object parameters(
            List<SurfaceParameter> source,
            ProtosSourceLayoutView view,
            Map<StructuralPath, ProtosObjectValue> paths,
            ProtosPrelude prelude) {
        List<Object> values =
                new ArrayList<>(source.size());

        for (SurfaceParameter parameter : source) {
            ProtosObjectValue value = object();
            value.createLocalSlot(
                    "name",
                    string(parameter.name()));
            value.createLocalSlot(
                    "defaultValue",
                    parameter.defaultValue().isPresent()
                            ? expression(
                                    parameter.defaultValue().orElseThrow(),
                                    view,
                                    paths,
                                    prelude)
                            : ProtosNullValue.INSTANCE);
            value.createLocalSlot(
                    "rest",
                    ProtosBooleanValue.of(parameter.rest()));
            value.freeze();
            values.add(value);
        }

        return prelude.newFrozenArray(values);
    }

    private static Object tokens(
            List<TokenSpelling> source,
            ProtosPrelude prelude) {
        List<Object> values =
                new ArrayList<>(source.size());

        for (TokenSpelling token : source) {
            ProtosObjectValue value = object();
            value.createLocalSlot(
                    "type",
                    string(token.type().name()));
            value.createLocalSlot(
                    "rawText",
                    string(token.rawText()));
            value.freeze();
            values.add(value);
        }

        return prelude.newFrozenArray(values);
    }

    private static Object comments(
            List<CommentProjection> source,
            Map<StructuralPath, ProtosObjectValue> paths,
            ProtosPrelude prelude) {
        List<Object> values =
                new ArrayList<>(source.size());

        for (CommentProjection comment : source) {
            ProtosObjectValue value = object();
            value.createLocalSlot(
                    "kind",
                    string(comment.kind().name()));
            value.createLocalSlot(
                    "rawText",
                    string(comment.rawText()));
            value.createLocalSlot(
                    "blankLineBefore",
                    com.guillermomolina.protos.runtime.ProtosBooleanValue.of(
                            comment.blankLineBefore()));
            value.createLocalSlot(
                    "blankLineAfter",
                    com.guillermomolina.protos.runtime.ProtosBooleanValue.of(
                            comment.blankLineAfter()));
            value.createLocalSlot(
                    "placement",
                    string(comment.placement().name()));
            value.createLocalSlot(
                    "preceding",
                    comment.preceding().isPresent()
                            ? path(
                                    comment.preceding().orElseThrow(),
                                    paths,
                                    prelude)
                            : ProtosNullValue.INSTANCE);
            value.createLocalSlot(
                    "following",
                    comment.following().isPresent()
                            ? path(
                                    comment.following().orElseThrow(),
                                    paths,
                                    prelude)
                            : ProtosNullValue.INSTANCE);
            value.createLocalSlot(
                    "boundaryKind",
                    string(comment.boundaryKind().name()));
            value.createLocalSlot(
                    "sequenceSeparatorKind",
                    comment.sequenceSeparatorKind().isPresent()
                            ? string(
                                    comment.sequenceSeparatorKind()
                                            .orElseThrow()
                                            .name())
                            : ProtosNullValue.INSTANCE);
            value.freeze();
            values.add(value);
        }

        return prelude.newFrozenArray(values);
    }

    private static Object structuralForms(
            List<StructuralFormProjection> source,
            Map<StructuralPath, ProtosObjectValue> paths,
            ProtosPrelude prelude) {
        List<Object> values =
                new ArrayList<>(source.size());

        for (StructuralFormProjection form : source) {
            ProtosObjectValue value = object();
            value.createLocalSlot(
                    "path",
                    path(
                            form.path(),
                            paths,
                            prelude));
            value.createLocalSlot(
                    "kind",
                    string(form.kind().name()));
            value.freeze();
            values.add(value);
        }

        return prelude.newFrozenArray(values);
    }

    private static Object sequenceSeparators(
            List<SequenceSeparatorProjection> source,
            Map<StructuralPath, ProtosObjectValue> paths,
            ProtosPrelude prelude) {
        List<Object> values =
                new ArrayList<>(source.size());

        for (SequenceSeparatorProjection separator : source) {
            ProtosObjectValue value = object();
            value.createLocalSlot(
                    "context",
                    string(separator.context().name()));
            value.createLocalSlot(
                    "preceding",
                    path(
                            separator.preceding(),
                            paths,
                            prelude));
            value.createLocalSlot(
                    "kind",
                    string(separator.kind().name()));
            value.createLocalSlot(
                    "blankLine",
                    com.guillermomolina.protos.runtime.ProtosBooleanValue.of(
                            separator.blankLine()));
            value.createLocalSlot(
                    "following",
                    path(
                            separator.following(),
                            paths,
                            prelude));
            value.freeze();
            values.add(value);
        }

        return prelude.newFrozenArray(values);
    }

    private static Object closureForms(
            List<ClosureFormProjection> source,
            Map<StructuralPath, ProtosObjectValue> paths,
            ProtosPrelude prelude) {
        List<Object> values =
                new ArrayList<>(source.size());

        for (ClosureFormProjection form : source) {
            ProtosObjectValue value = object();
            value.createLocalSlot(
                    "closure",
                    path(
                            form.closure(),
                            paths,
                            prelude));
            value.createLocalSlot(
                    "form",
                    string(form.form().name()));
            value.freeze();
            values.add(value);
        }

        return prelude.newFrozenArray(values);
    }

    private static Object trailingClosures(
            List<TrailingClosureProjection> source,
            Map<StructuralPath, ProtosObjectValue> paths,
            ProtosPrelude prelude) {
        List<Object> values =
                new ArrayList<>(source.size());

        for (TrailingClosureProjection trailing : source) {
            ProtosObjectValue value = object();
            value.createLocalSlot(
                    "call",
                    path(
                            trailing.call(),
                            paths,
                            prelude));
            value.createLocalSlot(
                    "closure",
                    path(
                            trailing.closure(),
                            paths,
                            prelude));
            value.freeze();
            values.add(value);
        }

        return prelude.newFrozenArray(values);
    }

    private static ProtosObjectValue path(
            StructuralPath source,
            Map<StructuralPath, ProtosObjectValue> paths,
            ProtosPrelude prelude) {
        ProtosObjectValue existing =
                paths.get(source);

        if (existing != null) {
            return existing;
        }

        List<Object> steps =
                new ArrayList<>(source.steps().size());

        for (StructuralStep sourceStep : source.steps()) {
            ProtosObjectValue step = object();
            step.createLocalSlot(
                    "role",
                    string(sourceStep.role()));
            step.createLocalSlot(
                    "index",
                    new ProtosIntegerValue(
                            sourceStep.index()));
            step.freeze();
            steps.add(step);
        }

        ProtosObjectValue value = object();
        value.createLocalSlot(
                "root",
                string(source.root()));
        value.createLocalSlot(
                "steps",
                prelude.newFrozenArray(steps));
        value.freeze();

        paths.put(source, value);
        return value;
    }

    private static ProtosObjectValue object() {
        return new ProtosObjectValue(
                ProtosObjectValue.rootObject());
    }

    private static ProtosStringValue string(
            String value) {
        return new ProtosStringValue(
                Objects.requireNonNull(value, "value"));
    }
}
