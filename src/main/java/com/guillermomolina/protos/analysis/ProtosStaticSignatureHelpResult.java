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
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;

/**
 * Protocol-neutral LM010-C signature-help projection for one exact snapshot.
 *
 * <p>The call is identified structurally by {@code calleeSpan}, the span of
 * the literal {@code SurfaceClosure} that is the call receiver once only
 * {@code SurfaceGroup} wrappers are removed, and by {@code argumentListStart},
 * the offset of the {@code (} that opens the call's argument list. Both lie
 * before the cursor and are therefore exact coordinates of the snapshot
 * itself, even when the call was validated through a transient repair.</p>
 *
 * <p>{@code activeArgument} is the syntactic index of the argument containing
 * the cursor: the number of argument-list-level commas before it.
 * {@code activeParameter} is the parameter index that argument binds to, and
 * is present only when the positional binding is decidable without runtime
 * information: never at or after a spread, and never for an excess argument
 * of a Closure without a rest parameter. Parameters carry only shape facts
 * from the surface syntax; a default is source text, never a value.</p>
 */
public record ProtosStaticSignatureHelpResult(
        ProtosDocumentSnapshot snapshot,
        SourceSpan calleeSpan,
        int argumentListStart,
        List<Parameter> parameters,
        int activeArgument,
        OptionalInt activeParameter) {
    public ProtosStaticSignatureHelpResult {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(calleeSpan, "calleeSpan");
        parameters = List.copyOf(Objects.requireNonNull(parameters, "parameters"));
        Objects.requireNonNull(activeParameter, "activeParameter");
        if (argumentListStart < calleeSpan.endOffset()) {
            throw new IllegalArgumentException("argument list must follow the callee");
        }
        if (activeArgument < 0) {
            throw new IllegalArgumentException("active argument must not be negative");
        }
        if (activeParameter.isPresent()
                && (activeParameter.getAsInt() < 0
                        || activeParameter.getAsInt() >= parameters.size())) {
            throw new IllegalArgumentException("active parameter out of range");
        }
    }

    /**
     * The signature label: the parameter labels in declaration order, comma
     * separated, inside parentheses.
     */
    public String label() {
        StringBuilder label = new StringBuilder("(");
        for (int index = 0; index < parameters.size(); index++) {
            if (index > 0) {
                label.append(", ");
            }
            label.append(parameters.get(index).label());
        }
        return label.append(')').toString();
    }

    /**
     * One declared Closure parameter. {@code label} is the exact source text
     * of the parameter, including a rest {@code ...} and a default
     * {@code = expression}.
     */
    public record Parameter(
            String name,
            String label,
            boolean hasDefault,
            boolean rest,
            SourceSpan span) {
        public Parameter {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(span, "span");
            if (rest && hasDefault) {
                throw new IllegalArgumentException("rest parameter cannot have a default");
            }
        }
    }
}
