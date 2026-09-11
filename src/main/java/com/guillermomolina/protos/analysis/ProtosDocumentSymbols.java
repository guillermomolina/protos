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
import com.guillermomolina.protos.parser.ast.SurfaceAssignment;
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
import com.guillermomolina.protos.source.SourceSpan;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** D079 slot-centric projection over the parser-authoritative surface tree. */
public final class ProtosDocumentSymbols {
    private ProtosDocumentSymbols() {
    }

    public static List<ProtosDocumentSymbol> from(SurfaceSequence program) {
        Objects.requireNonNull(program, "program");
        List<ProtosDocumentSymbol> symbols = new ArrayList<>();
        collect(program, symbols);
        return List.copyOf(symbols);
    }

    private static void collect(
            SurfaceExpression expression,
            List<ProtosDocumentSymbol> destination) {
        Objects.requireNonNull(expression, "expression");
        Objects.requireNonNull(destination, "destination");

        switch (expression) {
            case SurfaceLiteral ignored -> {
            }
            case SurfaceName ignored -> {
            }
            case SurfaceIntrinsic ignored -> {
            }
            case SurfaceSequence sequence -> {
                for (SurfaceExpression child : sequence.expressions()) {
                    collect(child, destination);
                }
            }
            case SurfaceGroup group -> collect(group.expression(), destination);
            case SurfaceMember member -> collect(member.receiver(), destination);
            case SurfaceCall call -> {
                collect(call.receiver(), destination);
                collectArguments(call.arguments(), destination);
            }
            case SurfaceIndex index -> {
                collect(index.receiver(), destination);
                collect(index.index(), destination);
            }
            case SurfaceUnary unary -> collect(unary.operand(), destination);
            case SurfaceBinary binary -> {
                collect(binary.left(), destination);
                collect(binary.right(), destination);
            }
            case SurfaceNonLocalReturn nonLocalReturn ->
                    collect(nonLocalReturn.expression(), destination);
            case SurfaceSlotCreation creation -> collectCreation(creation, destination);
            case SurfaceAssignment assignment -> {
                // '=' is never a symbol, but its subexpressions can still contain ':' creations.
                collect(assignment.target(), destination);
                collect(assignment.value(), destination);
            }
            case SurfaceSuperSend superSend ->
                    collectArguments(superSend.arguments(), destination);
            case SurfaceObject object -> {
                object.parent().ifPresent(parent -> collect(parent, destination));
                for (SurfaceObjectItem item : object.items()) {
                    collect(item.expression(), destination);
                }
            }
            case SurfaceClosure closure -> {
                for (SurfaceParameter parameter : closure.parameters()) {
                    parameter.defaultValue().ifPresent(value -> collect(value, destination));
                }
                collect(closure.body(), destination);
            }
        }
    }

    private static void collectArguments(
            List<SurfaceArgument> arguments,
            List<ProtosDocumentSymbol> destination) {
        for (SurfaceArgument argument : arguments) {
            collect(argument.expression(), destination);
        }
    }

    private static void collectCreation(
            SurfaceSlotCreation creation,
            List<ProtosDocumentSymbol> destination) {
        // A creation's target is not inside its symbol hierarchy. Traverse a
        // potentially compound member receiver at the surrounding source level.
        collect(creation.target(), destination);

        NamedTarget namedTarget = namedTarget(creation.target());
        if (namedTarget == null) {
            // The current grammar/spec admits only bare/member slot targets. Keep
            // traversal total if a future parser tree reaches this code before a
            // corresponding D079 evolution is approved.
            collect(creation.value(), destination);
            return;
        }

        List<ProtosDocumentSymbol> children = new ArrayList<>();
        collect(creation.value(), children);
        destination.add(new ProtosDocumentSymbol(
                namedTarget.name(),
                creation.span(),
                namedTarget.selectionRange(),
                children));
    }

    private static NamedTarget namedTarget(SurfaceExpression target) {
        if (target instanceof SurfaceName name) {
            return new NamedTarget(name.name(), name.span());
        }
        if (target instanceof SurfaceMember member) {
            int end = member.span().endOffset();
            int start = end - member.name().length();
            return new NamedTarget(member.name(), new SourceSpan(start, end));
        }
        return null;
    }

    private record NamedTarget(String name, SourceSpan selectionRange) {
    }
}
