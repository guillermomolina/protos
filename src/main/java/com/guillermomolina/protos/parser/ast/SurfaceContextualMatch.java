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

package com.guillermomolina.protos.parser.ast;

import com.guillermomolina.protos.source.SourceSpan;
import java.util.List;
import java.util.Objects;

/**
 * Contextual {@code match(subject) { key: action ... }} expression.
 *
 * <p>The leading {@code match} is syntax, not a lexical read. The form is
 * canonicalized to the ordinary {@code subject.caseOf(%{ ... })} send whose
 * single argument is an ordinary Map construction. {@code table} is the
 * braced entry table exactly as written: its span covers the actual opening
 * and closing braces, and every entry keeps its original right-hand side
 * expression. {@code span} covers the complete form from {@code match}
 * through the closing brace.
 */
public record SurfaceContextualMatch(
        SurfaceExpression subject,
        SurfaceMapConstruction table,
        SourceSpan span)
        implements SurfaceExpression {
    public SurfaceContextualMatch {
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(table, "table");
        Objects.requireNonNull(span, "span");
    }

    /**
     * Returns the semantic action for one entry right-hand side.
     *
     * <p>The classification is purely syntactic. Outer grouping is
     * disregarded only to inspect the root: when that root is an explicit
     * Closure literal, the original (grouped or ungrouped) expression is
     * returned unchanged. Every other expression becomes an ordinary
     * parameterless Closure whose body is a Sequence containing exactly that
     * expression. The returned projection is never stored in the table and
     * introduces no source tokens.
     */
    public static SurfaceExpression action(SurfaceExpression value) {
        if (!isImplicitAction(value)) {
            return value;
        }
        return new SurfaceClosure(
                List.of(),
                new SurfaceSequence(List.of(value), value.span()),
                true,
                value.span());
    }

    /**
     * Returns whether {@code value} is wrapped by {@link #action}, that is,
     * whether its root after outer grouping is not an explicit Closure
     * literal.
     */
    public static boolean isImplicitAction(SurfaceExpression value) {
        SurfaceExpression root = Objects.requireNonNull(value, "value");
        while (root instanceof SurfaceGroup group) {
            root = group.expression();
        }
        return !(root instanceof SurfaceClosure);
    }
}
