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

import com.guillermomolina.protos.parser.ast.SurfaceExpression;
import com.guillermomolina.protos.parser.ast.SurfaceGroup;
import com.guillermomolina.protos.parser.ast.SurfaceLiteral;
import com.guillermomolina.protos.parser.ast.SurfaceMember;
import com.guillermomolina.protos.parser.ast.SurfaceName;
import com.guillermomolina.protos.parser.ast.SurfaceSequence;
import com.guillermomolina.protos.semantic.ast.CanonicalExpression;
import com.guillermomolina.protos.semantic.ast.CanonicalLiteral;
import com.guillermomolina.protos.semantic.ast.CanonicalLookup;
import com.guillermomolina.protos.semantic.ast.CanonicalMember;
import com.guillermomolina.protos.semantic.ast.CanonicalSequence;
import java.util.List;

public final class Canonicalizer {
    public CanonicalExpression canonicalize(SurfaceExpression expression) {
        return switch (expression) {
            case SurfaceLiteral literal ->
                    new CanonicalLiteral(literal.kind(), literal.value(), literal.span());
            case SurfaceName name -> new CanonicalLookup(name.name(), name.span());
            case SurfaceGroup group -> canonicalize(group.expression());
            case SurfaceMember member ->
                    new CanonicalMember(
                            canonicalize(member.receiver()), member.name(), member.span());
            case SurfaceSequence sequence ->
                    new CanonicalSequence(canonicalizeAll(sequence.expressions()), sequence.span());
            default ->
                    throw new IllegalArgumentException(
                            "Surface expression is not supported by this canonicalizer slice: "
                                    + expression.getClass().getSimpleName());
        };
    }

    private List<CanonicalExpression> canonicalizeAll(List<SurfaceExpression> expressions) {
        return expressions.stream().map(this::canonicalize).toList();
    }
}
