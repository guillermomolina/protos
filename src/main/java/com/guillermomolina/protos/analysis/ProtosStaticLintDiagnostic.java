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

import com.guillermomolina.protos.source.SourceSpan;
import java.util.Objects;

/**
 * One editor-neutral D194 correctness lint finding over an exact parsed
 * snapshot.
 *
 * <p>{@code ruleId} is the stable public rule identifier. Protocol adapters
 * project {@code severity} and {@code span} without reinterpreting them.</p>
 */
public record ProtosStaticLintDiagnostic(
        ProtosDocumentSnapshot snapshot,
        String ruleId,
        Severity severity,
        SourceSpan span,
        String message) {

    public static final String UNREACHABLE_AFTER_NONLOCAL_RETURN =
            "protos/unreachable-after-nonlocal-return";
    public static final String ALWAYS_DIFFERENT_FRESH_OBJECT =
            "protos/always-different-fresh-object";

    /** D194 approves only warning-severity rules. */
    public enum Severity {
        WARNING
    }

    public ProtosStaticLintDiagnostic {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(ruleId, "ruleId");
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(span, "span");
        Objects.requireNonNull(message, "message");
    }
}
