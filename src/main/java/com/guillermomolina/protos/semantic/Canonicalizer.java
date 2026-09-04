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

package com.guillermomolina.protos.semantic;

import com.guillermomolina.protos.parser.ast.SurfaceAssignment;
import com.guillermomolina.protos.parser.ast.SurfaceArgument;
import com.guillermomolina.protos.parser.ast.SurfaceBinary;
import com.guillermomolina.protos.parser.ast.SurfaceCall;
import com.guillermomolina.protos.parser.ast.SurfaceClosure;
import com.guillermomolina.protos.parser.ast.SurfaceExpression;
import com.guillermomolina.protos.parser.ast.SurfaceGroup;
import com.guillermomolina.protos.parser.ast.SurfaceIndex;
import com.guillermomolina.protos.parser.ast.SurfaceIntrinsic;
import com.guillermomolina.protos.parser.ast.SurfaceLiteral;
import com.guillermomolina.protos.parser.ast.SurfaceMember;
import com.guillermomolina.protos.parser.ast.SurfaceName;
import com.guillermomolina.protos.parser.ast.SurfaceNonLocalReturn;
import com.guillermomolina.protos.parser.ast.SurfaceObject;
import com.guillermomolina.protos.parser.ast.SurfaceObjectItem;
import com.guillermomolina.protos.parser.ast.SurfaceParameter;
import com.guillermomolina.protos.parser.ast.SurfaceSequence;
import com.guillermomolina.protos.parser.ast.SurfaceSlotCreation;
import com.guillermomolina.protos.parser.ast.SurfaceSuperSend;
import com.guillermomolina.protos.parser.ast.SurfaceUnary;
import com.guillermomolina.protos.semantic.ast.CanonicalAssign;
import com.guillermomolina.protos.semantic.ast.CanonicalCall;
import com.guillermomolina.protos.semantic.ast.CanonicalClosure;
import com.guillermomolina.protos.semantic.ast.CanonicalCompose;
import com.guillermomolina.protos.semantic.ast.CanonicalCreate;
import com.guillermomolina.protos.semantic.ast.CanonicalExpression;
import com.guillermomolina.protos.semantic.ast.CanonicalIdentity;
import com.guillermomolina.protos.semantic.ast.CanonicalIndexedAssign;
import com.guillermomolina.protos.semantic.ast.CanonicalIntrinsic;
import com.guillermomolina.protos.semantic.ast.CanonicalLiteral;
import com.guillermomolina.protos.semantic.ast.CanonicalLookup;
import com.guillermomolina.protos.semantic.ast.CanonicalMember;
import com.guillermomolina.protos.semantic.ast.CanonicalObject;
import com.guillermomolina.protos.semantic.ast.CanonicalParameter;
import com.guillermomolina.protos.semantic.ast.CanonicalReturn;
import com.guillermomolina.protos.semantic.ast.CanonicalSend;
import com.guillermomolina.protos.semantic.ast.CanonicalSequence;
import com.guillermomolina.protos.semantic.ast.CanonicalSpread;
import com.guillermomolina.protos.semantic.ast.CanonicalSuperSend;
import java.util.List;

public final class Canonicalizer {
    public CanonicalExpression canonicalize(SurfaceExpression expression) {
        return switch (expression) {
            case SurfaceLiteral literal -> lowerLiteral(literal);
            case SurfaceName name -> new CanonicalLookup(name.name(), name.span());
            case SurfaceGroup group -> canonicalize(group.expression());
            case SurfaceIndex index -> lowerIndex(index);
            case SurfaceIntrinsic intrinsic -> lowerIntrinsic(intrinsic);
            case SurfaceMember member ->
                    new CanonicalMember(
                            canonicalize(member.receiver()), member.name(), member.span());
            case SurfaceNonLocalReturn nonLocalReturn ->
                    new CanonicalReturn(
                            canonicalize(nonLocalReturn.expression()), nonLocalReturn.span());
            case SurfaceObject object -> lowerObject(object);
            case SurfaceAssignment assignment -> lowerAssignment(assignment);
            case SurfaceBinary binary -> lowerBinary(binary);
            case SurfaceCall call -> lowerCall(call);
            case SurfaceClosure closure -> lowerClosure(closure);
            case SurfaceUnary unary -> lowerUnary(unary);
            case SurfaceSequence sequence ->
                    new CanonicalSequence(canonicalizeAll(sequence.expressions()), sequence.span());
            case SurfaceSlotCreation creation -> lowerSlotCreation(creation);
            case SurfaceSuperSend superSend -> lowerSuperSend(superSend);
            default ->
                    throw new IllegalArgumentException(
                            "Surface expression is not supported by this canonicalizer slice: "
                                    + expression.getClass().getSimpleName());
        };
    }

    private CanonicalExpression lowerLiteral(SurfaceLiteral literal) {
        CanonicalLiteral.Kind kind = switch (literal.kind()) {
            case NUMBER -> CanonicalLiteral.Kind.NUMBER;
            case STRING -> CanonicalLiteral.Kind.STRING;
            case TRUE -> CanonicalLiteral.Kind.TRUE;
            case FALSE -> CanonicalLiteral.Kind.FALSE;
            case NULL -> CanonicalLiteral.Kind.NULL;
        };
        return new CanonicalLiteral(kind, literal.value(), literal.span());
    }

    private CanonicalExpression lowerSuperSend(SurfaceSuperSend superSend) {
        return new CanonicalSuperSend(
                superSend.message(),
                canonicalizeArguments(superSend.arguments()),
                superSend.span());
    }

    private CanonicalExpression lowerIntrinsic(SurfaceIntrinsic intrinsic) {
        CanonicalIntrinsic.Kind kind = switch (intrinsic.kind()) {
            case THIS -> CanonicalIntrinsic.Kind.THIS;
            case CONTEXT -> CanonicalIntrinsic.Kind.CONTEXT;
            case ARGS -> CanonicalIntrinsic.Kind.ARGS;
        };
        return new CanonicalIntrinsic(kind, intrinsic.span());
    }

    private CanonicalExpression lowerCall(SurfaceCall call) {
        List<CanonicalExpression> arguments = canonicalizeArguments(call.arguments());

        if (call.receiver() instanceof SurfaceMember member) {
            return new CanonicalSend(
                    canonicalize(member.receiver()),
                    member.name(),
                    arguments,
                    call.span());
        }

        return new CanonicalCall(canonicalize(call.receiver()), arguments, call.span());
    }

    private List<CanonicalExpression> canonicalizeArguments(List<SurfaceArgument> arguments) {
        return arguments.stream().map(this::canonicalizeArgument).toList();
    }

    private CanonicalExpression canonicalizeArgument(SurfaceArgument argument) {
        CanonicalExpression expression = canonicalize(argument.expression());
        if (!argument.spread()) {
            return expression;
        }
        return new CanonicalSpread(expression, argument.span());
    }

    private CanonicalExpression lowerObject(SurfaceObject object) {
        return new CanonicalObject(
                object.parent().map(this::canonicalize),
                new CanonicalSequence(
                        object.items().stream().map(this::canonicalizeObjectItem).toList(),
                        object.span()),
                object.span());
    }

    private CanonicalExpression canonicalizeObjectItem(SurfaceObjectItem item) {
        CanonicalExpression expression = canonicalize(item.expression());
        if (!item.composition()) {
            return expression;
        }
        return new CanonicalCompose(expression, item.span());
    }

    private CanonicalExpression lowerAssignment(SurfaceAssignment assignment) {
        if (assignment.target() instanceof SurfaceIndex index) {
            return new CanonicalIndexedAssign(
                    canonicalize(index.receiver()),
                    canonicalize(index.index()),
                    canonicalize(assignment.value()),
                    assignment.span());
        }
        SlotTarget target = slotTarget(assignment.target());
        return new CanonicalAssign(
                target.receiver(),
                target.name(),
                canonicalize(assignment.value()),
                assignment.span());
    }

    private CanonicalExpression lowerSlotCreation(SurfaceSlotCreation creation) {
        SlotTarget target = slotTarget(creation.target());
        return new CanonicalCreate(
                target.receiver(),
                target.name(),
                canonicalize(creation.value()),
                creation.span());
    }

    private SlotTarget slotTarget(SurfaceExpression target) {
        if (target instanceof SurfaceName name) {
            return new SlotTarget(java.util.Optional.empty(), name.name());
        }
        if (target instanceof SurfaceMember member) {
            return new SlotTarget(
                    java.util.Optional.of(canonicalize(member.receiver())),
                    member.name());
        }
        throw new IllegalArgumentException(
                "Unsupported slot target in this canonicalizer slice: "
                        + target.getClass().getSimpleName());
    }

    private record SlotTarget(
            java.util.Optional<CanonicalExpression> receiver,
            String name) {}

    private CanonicalExpression lowerIndex(SurfaceIndex index) {
        return new CanonicalSend(
                canonicalize(index.receiver()),
                "at",
                List.of(canonicalize(index.index())),
                index.span());
    }

    private CanonicalExpression lowerClosure(SurfaceClosure closure) {
        return new CanonicalClosure(
                closure.parameters().stream().map(this::canonicalizeParameter).toList(),
                new CanonicalSequence(
                        canonicalizeAll(closure.body().expressions()),
                        closure.body().span()),
                closure.span());
    }

    private CanonicalParameter canonicalizeParameter(SurfaceParameter parameter) {
        return new CanonicalParameter(
                parameter.name(),
                parameter.defaultValue().map(this::canonicalize),
                parameter.rest(),
                parameter.span());
    }

    private CanonicalExpression lowerBinary(SurfaceBinary binary) {
        return switch (binary.operator()) {
            case "&&" -> lowerLazyBoolean(binary, "and");
            case "||" -> lowerLazyBoolean(binary, "or");
            case "==" -> lowerEquality(binary);
            case "!=" ->
                    negate(lowerEquality(binary), binary.span());
            case "===" ->
                    lowerIdentity(binary);
            case "!==" ->
                    negate(lowerIdentity(binary), binary.span());
            default ->
                    new CanonicalSend(
                            canonicalize(binary.left()),
                            binary.operator(),
                            List.of(canonicalize(binary.right())),
                            binary.span());
        };
    }

    private CanonicalExpression lowerLazyBoolean(
            SurfaceBinary binary, String message) {
        CanonicalExpression right = canonicalize(binary.right());
        CanonicalClosure lazyRight =
                new CanonicalClosure(
                        List.of(),
                        new CanonicalSequence(List.of(right), binary.right().span()),
                        binary.right().span());

        return new CanonicalSend(
                canonicalize(binary.left()),
                message,
                List.of(lazyRight),
                binary.span());
    }

    private CanonicalExpression lowerEquality(SurfaceBinary binary) {
        return new CanonicalSend(
                canonicalize(binary.left()),
                "==",
                List.of(canonicalize(binary.right())),
                binary.span());
    }

    private CanonicalExpression lowerIdentity(SurfaceBinary binary) {
        return new CanonicalIdentity(
                canonicalize(binary.left()),
                canonicalize(binary.right()),
                binary.span());
    }

    private CanonicalExpression negate(CanonicalExpression expression, com.guillermomolina.protos.source.SourceSpan span) {
        return new CanonicalSend(expression, "not", List.of(), span);
    }

    private CanonicalExpression lowerUnary(SurfaceUnary unary) {
        String message = switch (unary.operator()) {
            case "-" -> "negated";
            case "!" -> "not";
            default -> throw new IllegalArgumentException(
                    "Unsupported unary operator: " + unary.operator());
        };

        return new CanonicalSend(
                canonicalize(unary.operand()), message, List.of(), unary.span());
    }

    private List<CanonicalExpression> canonicalizeAll(List<SurfaceExpression> expressions) {
        return expressions.stream().map(this::canonicalize).toList();
    }
}
