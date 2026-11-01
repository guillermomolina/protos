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
 * D231-F2-PURE contextual {@code for(item in iterable) { body }} or
 * {@code for(i, start, stop[, step]) { body }} expression.
 *
 * <p>The leading {@code for} and the generic {@code in} separator are
 * syntax, not lexical reads. The generic form is canonicalized to an
 * ordinary {@code iterable.each(body)} send; the numeric form to
 * {@code Range(start, stop).each(body)} or
 * {@code Range(start, stop).eachStep(step, body)}. The binder is the single
 * parameter of {@code body}; its span lies in the header, before the
 * Closure's own span, which starts at the attached {@code '{'}. {@code span}
 * covers the complete form from {@code for} through the closing brace.
 */
public record SurfaceContextualFor(
        Kind kind,
        SurfaceExpression iterable,
        SurfaceExpression start,
        SurfaceExpression stop,
        SurfaceExpression step,
        SurfaceClosure body,
        SourceSpan span)
        implements SurfaceExpression {
    public enum Kind {
        GENERIC,
        NUMERIC
    }

    public SurfaceContextualFor {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(span, "span");
        if (kind == Kind.GENERIC) {
            Objects.requireNonNull(iterable, "iterable");
            if (start != null || stop != null || step != null) {
                throw new IllegalArgumentException(
                        "generic contextual for has no numeric bounds");
            }
        } else {
            Objects.requireNonNull(start, "start");
            Objects.requireNonNull(stop, "stop");
            if (iterable != null) {
                throw new IllegalArgumentException(
                        "numeric contextual for has no iterable");
            }
        }
        if (body.parameters().size() != 1
                || body.parameters().get(0).defaultValue().isPresent()
                || body.parameters().get(0).rest()) {
            throw new IllegalArgumentException(
                    "contextual for body must have exactly one plain parameter");
        }
    }

    /** Returns the binder, which is the single parameter of {@code body}. */
    public SurfaceParameter binder() {
        return body.parameters().get(0);
    }
}
