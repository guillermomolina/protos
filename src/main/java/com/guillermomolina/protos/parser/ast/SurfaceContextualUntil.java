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
 * D212 contextual suffix {@code { body } until(condition)} expression.
 *
 * <p>The braced prefix is a parameterless Closure body, not object
 * construction, and {@code until} is syntax, not a lexical read. The form is
 * canonicalized to an ordinary {@code untilTrue} send whose receiver is the
 * body Closure and whose sole argument is an independent zero-parameter
 * Closure reevaluating {@code condition}.
 *
 * @param untilSpan the exact span of the bare {@code until} identifier
 * @param span the complete contextual expression, from {@code '{'} to the
 *     suffix {@code ')'}
 */
public record SurfaceContextualUntil(
        SurfaceClosure body,
        SurfaceExpression condition,
        SourceSpan untilSpan,
        SourceSpan span)
        implements SurfaceExpression {
    public SurfaceContextualUntil {
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(condition, "condition");
        Objects.requireNonNull(untilSpan, "untilSpan");
        Objects.requireNonNull(span, "span");
    }
}
