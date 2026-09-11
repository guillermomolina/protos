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

/** Protocol-neutral document-outline symbol derived from the real surface AST. */
public record ProtosDocumentSymbol(
        String name,
        SourceSpan range,
        SourceSpan selectionRange,
        List<ProtosDocumentSymbol> children) {
    public ProtosDocumentSymbol {
        Objects.requireNonNull(name, "name");
        if (name.isEmpty()) {
            throw new IllegalArgumentException("document symbol name must not be empty");
        }
        Objects.requireNonNull(range, "range");
        Objects.requireNonNull(selectionRange, "selectionRange");
        children = List.copyOf(children);

        if (selectionRange.startOffset() < range.startOffset()
                || selectionRange.endOffset() > range.endOffset()) {
            throw new IllegalArgumentException("selection range must be contained in symbol range");
        }
    }
}
