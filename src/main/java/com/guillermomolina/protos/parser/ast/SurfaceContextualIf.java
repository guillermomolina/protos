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
import java.util.Objects;

/**
 * D211 contextual {@code if(condition) { trueBody }} or
 * {@code if(condition) { trueBody } else { falseBody }} expression.
 *
 * <p>The leading {@code if} and the paired {@code else} are syntax, not
 * lexical reads. The form is canonicalized to an ordinary {@code ifTrue}
 * send (single arm, {@code falseBranch == null}) or {@code ifTrueIfFalse}
 * send (two arms) whose receiver is the eagerly evaluated {@code condition}
 * itself; unlike contextual {@code while}, the condition is never wrapped in
 * a Closure. The parser admits only parameterless braced branch Closures,
 * and {@code span} covers the complete form from {@code if} through the
 * last branch's closing brace.
 */
public record SurfaceContextualIf(
        SurfaceExpression condition,
        SurfaceClosure trueBranch,
        SurfaceClosure falseBranch,
        SourceSpan span)
        implements SurfaceExpression {
    public SurfaceContextualIf {
        Objects.requireNonNull(condition, "condition");
        Objects.requireNonNull(trueBranch, "trueBranch");
        Objects.requireNonNull(span, "span");
    }
}
