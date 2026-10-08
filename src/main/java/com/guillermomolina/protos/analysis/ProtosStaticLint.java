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
import java.util.Comparator;
import java.util.List;

/**
 * The two D194 initial correctness lint rules over one parsed snapshot.
 *
 * <p>Both rules are purely syntactic proofs over the parser Surface AST:</p>
 *
 * <ul>
 *   <li>{@code protos/unreachable-after-nonlocal-return}: a {@code ^} that is a
 *   direct child of a braced Closure body never completes normally. It either
 *   returns to its home or, when the home is inactive, signals non-resumable
 *   {@code InvalidReturn} (CALLABLES.md); neither outcome resumes the same body.
 *   Only direct children of that same Sequence are reported; conditional or
 *   indirect execution of a nested Closure proves nothing about its creator.</li>
 *   <li>{@code protos/always-different-fresh-object}: {@code ===} is
 *   non-overridable identity (PROTOS_GRAMMAR.md) and an object literal always
 *   produces a fresh identity-bearing object (OBJECT_MODEL.md). The left
 *   operand is evaluated before that object exists, so it cannot be that
 *   object. Only exact literals, optionally inside parenthesized groups, on
 *   the right are proven.</li>
 * </ul>
 *
 * <p>Neither rule claims that evaluating the involved expressions is free of
 * effects, Errors, suspension, or control transfer.</p>
 */
public final class ProtosStaticLint {
    private final ProtosDocumentSnapshot snapshot;
    private final List<ProtosStaticLintDiagnostic> diagnostics = new ArrayList<>();

    private ProtosStaticLint(ProtosDocumentSnapshot snapshot) {
        this.snapshot = snapshot;
    }

    /**
     * Returns the findings for the parsed program ordered by start offset, end
     * offset, then rule ID.
     */
    public static List<ProtosStaticLintDiagnostic> check(
            ProtosStaticParseResult.Parsed parsed) {
        ProtosStaticLint lint = new ProtosStaticLint(parsed.snapshot());
        lint.visit(parsed.program());
        return lint.diagnostics.stream()
                .sorted(Comparator
                        .comparingInt((ProtosStaticLintDiagnostic diagnostic) ->
                                diagnostic.span().startOffset())
                        .thenComparingInt(diagnostic -> diagnostic.span().endOffset())
                        .thenComparing(ProtosStaticLintDiagnostic::ruleId))
                .toList();
    }

    private void visit(SurfaceExpression expression) {
        switch (expression) {
            case SurfaceLiteral ignored -> {
            }
            case SurfaceName ignored -> {
            }
            case SurfaceIntrinsic ignored -> {
            }
            case SurfaceSequence sequence ->
                    sequence.expressions().forEach(this::visit);
            case SurfaceGroup group ->
                    visit(group.expression());
            case SurfaceMember member ->
                    visit(member.receiver());
            case SurfaceCall call -> {
                visit(call.receiver());
                visitArguments(call.arguments());
            }
            case SurfaceArrayConstruction array ->
                    visitArguments(array.arguments());
            case SurfaceMapConstruction map -> {
                for (SurfaceMapConstruction.Entry entry : map.entries()) {
                    visit(entry.key());
                    visit(entry.value());
                }
            }
            case SurfaceIndex index -> {
                visit(index.receiver());
                visit(index.index());
            }
            case SurfaceUnary unary ->
                    visit(unary.operand());
            case SurfaceBinary binary -> {
                checkFreshObjectIdentity(binary);
                visit(binary.left());
                visit(binary.right());
            }
            case SurfaceNonLocalReturn nonLocalReturn ->
                    visit(nonLocalReturn.expression());
            case SurfaceSlotCreation creation -> {
                visit(creation.target());
                visit(creation.value());
            }
            case SurfaceMultipleSlotCreation creation -> {
                creation.targets().forEach(this::visit);
                visit(creation.value());
            }
            case SurfaceAssignment assignment -> {
                visit(assignment.target());
                visit(assignment.value());
            }
            case SurfaceSuperSend superSend ->
                    visitArguments(superSend.arguments());
            case SurfaceObject object -> {
                object.parent().ifPresent(this::visit);
                for (SurfaceObjectItem item : object.items()) {
                    visit(item.expression());
                }
            }
            case SurfaceClosure closure -> {
                for (SurfaceParameter parameter : closure.parameters()) {
                    parameter.defaultValue().ifPresent(this::visit);
                }
                if (!closure.expressionBody()) {
                    checkUnreachableAfterNonLocalReturn(closure.body());
                }
                visit(closure.body());
            }
        }
    }

    private void visitArguments(List<SurfaceArgument> arguments) {
        for (SurfaceArgument argument : arguments) {
            visit(argument.expression());
        }
    }

    private void checkUnreachableAfterNonLocalReturn(SurfaceSequence body) {
        List<SurfaceExpression> expressions = body.expressions();
        for (int index = 0; index < expressions.size() - 1; index++) {
            if (expressions.get(index) instanceof SurfaceNonLocalReturn) {
                SurfaceExpression last = expressions.get(expressions.size() - 1);
                report(
                        ProtosStaticLintDiagnostic.UNREACHABLE_AFTER_NONLOCAL_RETURN,
                        new SourceSpan(
                                expressions.get(index + 1).span().startOffset(),
                                last.span().endOffset()),
                        "Unreachable code: the preceding '^' never continues "
                                + "this closure body");
                return;
            }
        }
    }

    private void checkFreshObjectIdentity(SurfaceBinary binary) {
        boolean identity = binary.operator().equals("===");
        if (!identity && !binary.operator().equals("!==")) {
            return;
        }
        SurfaceExpression right = binary.right();
        while (right instanceof SurfaceGroup group) {
            right = group.expression();
        }
        if (!(right instanceof SurfaceObject)) {
            return;
        }
        report(
                ProtosStaticLintDiagnostic.ALWAYS_DIFFERENT_FRESH_OBJECT,
                binary.span(),
                "'" + binary.operator() + "' against a fresh object literal is always "
                        + (identity ? "false" : "true")
                        + " when it completes normally");
    }

    private void report(String ruleId, SourceSpan span, String message) {
        diagnostics.add(new ProtosStaticLintDiagnostic(
                snapshot,
                ruleId,
                ProtosStaticLintDiagnostic.Severity.WARNING,
                span,
                message));
    }
}
