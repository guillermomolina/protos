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

import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.guillermomolina.protos.runtime.ProtosNumberLiteral;
import com.guillermomolina.protos.runtime.ProtosStringValue;
import com.guillermomolina.protos.semantic.ast.CanonicalAssign;
import com.guillermomolina.protos.semantic.ast.CanonicalCall;
import com.guillermomolina.protos.semantic.ast.CanonicalClosure;
import com.guillermomolina.protos.semantic.ast.CanonicalCompose;
import com.guillermomolina.protos.semantic.ast.CanonicalCreate;
import com.guillermomolina.protos.semantic.ast.CanonicalExpression;
import com.guillermomolina.protos.semantic.ast.CanonicalIdentity;
import com.guillermomolina.protos.semantic.ast.CanonicalIntrinsic;
import com.guillermomolina.protos.semantic.ast.CanonicalLiteral;
import com.guillermomolina.protos.semantic.ast.CanonicalLookup;
import com.guillermomolina.protos.semantic.ast.CanonicalMember;
import com.guillermomolina.protos.semantic.ast.CanonicalObject;
import com.guillermomolina.protos.semantic.ast.CanonicalReturn;
import com.guillermomolina.protos.semantic.ast.CanonicalSequence;
import com.guillermomolina.protos.semantic.ast.CanonicalSpread;
import java.util.Objects;

public final class CanonicalToTruffleLowerer {
    public ProtosExpressionNode lower(CanonicalExpression expression) {
        Objects.requireNonNull(expression, "expression");

        if (expression instanceof CanonicalLiteral literal) {
            return lowerLiteral(literal);
        }
        if (expression instanceof CanonicalClosure closure) {
            return new ProtosClosureLiteralNode(closure.span(), closure, lowerCallablePlan(closure));
        }
        if (expression instanceof CanonicalCall call) {
            return lowerCall(call, false);
        }
        if (expression instanceof CanonicalSequence sequence) {
            return lowerSequence(sequence);
        }
        if (expression instanceof CanonicalObject object) {
            ProtosExpressionNode parentNode =
                    object.parent().map(this::lower).orElse(null);
            return new ProtosObjectLiteralNode(
                    object.span(),
                    parentNode,
                    ProtosExecution.createCallTarget(
                            lowerObjectBody(object)));
        }
        if (expression instanceof CanonicalIdentity identity) {
            return new ProtosIdentityNode(
                    identity.span(),
                    lower(identity.left()),
                    lower(identity.right()));
        }
        if (expression instanceof CanonicalLookup lookup) {
            return new ProtosLookupNode(lookup.span(), lookup.name());
        }
        if (expression instanceof CanonicalMember member) {
            return new ProtosMemberReadNode(
                    member.span(),
                    lower(member.receiver()),
                    member.name());
        }
        if (expression instanceof CanonicalIntrinsic intrinsic) {
            if (intrinsic.kind() == CanonicalIntrinsic.Kind.ARGS) {
                throw new UnsupportedOperationException(
                        "args is only lowerable inside a Closure invocation plan");
            }
            return new ProtosIntrinsicNode(intrinsic.span(), intrinsic.kind());
        }
        if (expression instanceof CanonicalCreate create) {
            if (create.target().isPresent()) {
                return new ProtosMemberCreateNode(
                        create.span(),
                        lower(create.target().orElseThrow()),
                        create.name(),
                        lower(create.value()));
            }
            return new ProtosBareCreateNode(
                    create.span(),
                    create.name(),
                    lower(create.value()));
        }
        if (expression instanceof CanonicalAssign assign) {
            if (assign.target().isPresent()) {
                return new ProtosMemberAssignNode(
                        assign.span(),
                        lower(assign.target().orElseThrow()),
                        assign.name(),
                        lower(assign.value()));
            }
            return new ProtosBareAssignNode(
                    assign.span(),
                    assign.name(),
                    lower(assign.value()));
        }

        throw new UnsupportedOperationException(
                "Canonical expression is not supported by this Truffle lowering slice: "
                        + expression.getClass().getSimpleName());
    }

    private ProtosClosureExecutionPlan lowerCallablePlan(CanonicalClosure closure) {
        ProtosExpressionNode[] defaultNodes =
                closure.parameters().stream()
                        .map(
                                parameter ->
                                        parameter.defaultValue()
                                                .map(this::lowerCallable)
                                                .orElse(null))
                        .toArray(ProtosExpressionNode[]::new);
        ProtosParameterBindingNode binding =
                new ProtosParameterBindingNode(
                        closure.span(),
                        closure.parameters(),
                        defaultNodes);
        ProtosExpressionNode body = lowerCallable(closure.body());
        return new ProtosClosureExecutionPlan(binding, body);
    }

    private ProtosExpressionNode lowerCallable(CanonicalExpression expression) {
        if (expression instanceof CanonicalIntrinsic intrinsic
                && intrinsic.kind() == CanonicalIntrinsic.Kind.ARGS) {
            return new ProtosArgsNode(intrinsic.span());
        }
        if (expression instanceof CanonicalReturn returnExpression) {
            return new ProtosReturnNode(
                    returnExpression.span(),
                    lowerCallable(returnExpression.value()));
        }
        if (expression instanceof CanonicalCall call) {
            return lowerCall(call, true);
        }
        if (expression instanceof CanonicalSequence sequence) {
            ProtosExpressionNode[] expressions =
                    sequence.expressions().stream()
                            .map(this::lowerCallable)
                            .toArray(ProtosExpressionNode[]::new);
            return new ProtosSequenceNode(sequence.span(), expressions);
        }
        if (expression instanceof CanonicalClosure closure) {
            return new ProtosClosureLiteralNode(
                    closure.span(),
                    closure,
                    lowerCallablePlan(closure));
        }
        return lower(expression);
    }

    private ProtosExpressionNode lowerCall(CanonicalCall call, boolean callableContext) {
        java.util.List<ProtosArgumentItem> items = new java.util.ArrayList<>(call.arguments().size());
        for (CanonicalExpression argument : call.arguments()) {
            if (argument instanceof CanonicalSpread spread) {
                items.add(new ProtosArgumentItem(callableContext ? lowerCallable(spread.expression()) : lower(spread.expression()), true));
            } else {
                items.add(new ProtosArgumentItem(callableContext ? lowerCallable(argument) : lower(argument), false));
            }
        }
        ProtosExpressionNode receiver = callableContext ? lowerCallable(call.receiver()) : lower(call.receiver());
        return new ProtosCallNode(call.span(), receiver, new ProtosArgumentVectorNode(call.span(), items));
    }

    private ProtosExpressionNode lowerLiteral(CanonicalLiteral literal) {
        Object value =
                switch (literal.kind()) {
                    case TRUE -> ProtosBooleanValue.TRUE;
                    case FALSE -> ProtosBooleanValue.FALSE;
                    case NULL -> ProtosNullValue.INSTANCE;
                    case STRING -> new ProtosStringValue(literal.value());
                    case NUMBER -> ProtosNumberLiteral.materialize(literal.value());
                };

        return new ProtosConstantNode(literal.span(), value);
    }

    private ProtosExpressionNode lowerSequence(CanonicalSequence sequence) {
        ProtosExpressionNode[] expressions =
                sequence.expressions().stream().map(this::lower).toArray(ProtosExpressionNode[]::new);
        return new ProtosSequenceNode(sequence.span(), expressions);
    }

    private ProtosExpressionNode lowerObjectBody(CanonicalObject object) {
        java.util.Set<String> reservedNames =
                object.reservedLocalSlotNames();
        ProtosExpressionNode[] expressions =
                object.body().expressions().stream()
                        .map(expression -> {
                            if (expression instanceof CanonicalCompose compose) {
                                return new ProtosComposeNode(
                                        compose.span(),
                                        lower(compose.object()),
                                        reservedNames);
                            }
                            return lower(expression);
                        })
                        .toArray(ProtosExpressionNode[]::new);
        return new ProtosSequenceNode(object.body().span(), expressions);
    }
}
