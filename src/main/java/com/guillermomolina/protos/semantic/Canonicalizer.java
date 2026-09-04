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

import com.guillermomolina.protos.parser.ast.SurfaceBinary;
import com.guillermomolina.protos.parser.ast.SurfaceClosure;
import com.guillermomolina.protos.parser.ast.SurfaceExpression;
import com.guillermomolina.protos.parser.ast.SurfaceGroup;
import com.guillermomolina.protos.parser.ast.SurfaceIndex;
import com.guillermomolina.protos.parser.ast.SurfaceLiteral;
import com.guillermomolina.protos.parser.ast.SurfaceMember;
import com.guillermomolina.protos.parser.ast.SurfaceName;
import com.guillermomolina.protos.parser.ast.SurfaceParameter;
import com.guillermomolina.protos.parser.ast.SurfaceSequence;
import com.guillermomolina.protos.parser.ast.SurfaceUnary;
import com.guillermomolina.protos.semantic.ast.CanonicalClosure;
import com.guillermomolina.protos.semantic.ast.CanonicalExpression;
import com.guillermomolina.protos.semantic.ast.CanonicalIdentity;
import com.guillermomolina.protos.semantic.ast.CanonicalLiteral;
import com.guillermomolina.protos.semantic.ast.CanonicalLookup;
import com.guillermomolina.protos.semantic.ast.CanonicalMember;
import com.guillermomolina.protos.semantic.ast.CanonicalParameter;
import com.guillermomolina.protos.semantic.ast.CanonicalSend;
import com.guillermomolina.protos.semantic.ast.CanonicalSequence;
import java.util.List;

public final class Canonicalizer {
    public CanonicalExpression canonicalize(SurfaceExpression expression) {
        return switch (expression) {
            case SurfaceLiteral literal ->
                    new CanonicalLiteral(literal.kind(), literal.value(), literal.span());
            case SurfaceName name -> new CanonicalLookup(name.name(), name.span());
            case SurfaceGroup group -> canonicalize(group.expression());
            case SurfaceIndex index -> lowerIndex(index);
            case SurfaceMember member ->
                    new CanonicalMember(
                            canonicalize(member.receiver()), member.name(), member.span());
            case SurfaceBinary binary -> lowerBinary(binary);
            case SurfaceClosure closure -> lowerClosure(closure);
            case SurfaceUnary unary -> lowerUnary(unary);
            case SurfaceSequence sequence ->
                    new CanonicalSequence(canonicalizeAll(sequence.expressions()), sequence.span());
            default ->
                    throw new IllegalArgumentException(
                            "Surface expression is not supported by this canonicalizer slice: "
                                    + expression.getClass().getSimpleName());
        };
    }

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
