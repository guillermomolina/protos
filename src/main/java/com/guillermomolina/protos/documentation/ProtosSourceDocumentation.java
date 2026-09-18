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

package com.guillermomolina.protos.documentation;

import com.guillermomolina.protos.analysis.ProtosDocumentSymbol;
import com.guillermomolina.protos.analysis.ProtosDocumentSymbols;
import com.guillermomolina.protos.parser.ProtosParser;
import com.guillermomolina.protos.source.SourceSpan;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * D138 source-local documentation ownership projection.
 *
 * <p>This layer identifies source owners only. Documentation comment association
 * is added by later TOOL007 slices.</p>
 */
public final class ProtosSourceDocumentation {
    private ProtosSourceDocumentation() {
    }

    /**
     * Returns the documentable owners in one current source snapshot.
     *
     * <p>The source unit itself is the module owner. Named slot owners are
     * explicit Surface slot-creation occurrences in source order.</p>
     */
    public static SourceOwners owners(String source) {
        String normalizedSource = source == null ? "" : source;

        List<SlotOwner> slots = new ArrayList<>();
        for (ProtosDocumentSymbol symbol :
                ProtosDocumentSymbols.from(
                        new ProtosParser(normalizedSource).parseProgram())) {
            collect(symbol, slots);
        }

        slots.sort(Comparator.comparingInt(slot -> slot.span().startOffset()));

        return new SourceOwners(
                new SourceSpan(0, normalizedSource.length()),
                slots);
    }

    /** Owners belonging to one module source unit. */
    public record SourceOwners(
            SourceSpan sourceUnitSpan,
            List<SlotOwner> slots) {
        public SourceOwners {
            Objects.requireNonNull(sourceUnitSpan, "sourceUnitSpan");
            slots = List.copyOf(Objects.requireNonNull(slots, "slots"));
        }
    }

    /** One explicit named slot-creation occurrence. */
    public record SlotOwner(
            String name,
            SourceSpan span,
            SourceSpan selectionRange) {
        public SlotOwner {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(span, "span");
            Objects.requireNonNull(selectionRange, "selectionRange");
        }
    }

    private static void collect(
            ProtosDocumentSymbol symbol,
            List<SlotOwner> destination) {
        destination.add(new SlotOwner(
                symbol.name(),
                symbol.range(),
                symbol.selectionRange()));

        for (ProtosDocumentSymbol child : symbol.children()) {
            collect(child, destination);
        }
    }
}
