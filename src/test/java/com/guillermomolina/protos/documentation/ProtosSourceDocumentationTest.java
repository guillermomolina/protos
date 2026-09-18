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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class ProtosSourceDocumentationTest {
    @Test
    void identifiesNamedSlotCreationOwnersInSourceOrder() {
        String source = """
                top: 1
                container: () => {
                    nested: 2
                }
                target.member: 3
                """;

        ProtosSourceDocumentation.SourceOwners owners =
                ProtosSourceDocumentation.owners(source);

        assertEquals(0, owners.sourceUnitSpan().startOffset());
        assertEquals(source.length(), owners.sourceUnitSpan().endOffset());

        assertEquals(
                List.of("top", "container", "nested", "member"),
                owners.slots().stream()
                        .map(ProtosSourceDocumentation.SlotOwner::name)
                        .toList());
    }

    @Test
    void parametersAndAssignmentsAreNotIndependentOwners() {
        String source = """
                call: (value) => value
                existing = value
                """;

        ProtosSourceDocumentation.SourceOwners owners =
                ProtosSourceDocumentation.owners(source);

        assertEquals(
                List.of("call"),
                owners.slots().stream()
                        .map(ProtosSourceDocumentation.SlotOwner::name)
                        .toList());
    }

    @Test
    void sameNamedCreationsRemainDistinctOccurrences() {
        String source = """
                value: 1
                holder: () => {
                    value: 2
                }
                """;

        List<ProtosSourceDocumentation.SlotOwner> values =
                ProtosSourceDocumentation.owners(source).slots().stream()
                        .filter(owner -> owner.name().equals("value"))
                        .toList();

        assertEquals(2, values.size());
        assertEquals(source.indexOf("value: 1"), values.get(0).span().startOffset());
        assertEquals(source.indexOf("value: 2"), values.get(1).span().startOffset());
    }
    @Test
    void extractsContiguousModuleDocumentationFromPreamble() {
        String source = """
                // ordinary preamble comment

                //! Module documentation.
                //! Second line.
                value: 1
                """;

        assertEquals(
                "Module documentation.\nSecond line.",
                ProtosSourceDocumentation.moduleDocumentation(source));
    }

    @Test
    void returnsNullWhenModuleDocumentationIsAbsent() {
        assertNull(ProtosSourceDocumentation.moduleDocumentation("value: 1\n"));
    }

    @Test
    void rejectsModuleDocumentationAfterFirstConstruct() {
        String source = """
                value: 1
                //! Too late.
                """;

        assertThrows(
                IllegalArgumentException.class,
                () -> ProtosSourceDocumentation.moduleDocumentation(source));
    }

    @Test
    void rejectsMultipleModuleDocumentationBlocks() {
        String source = """
                //! First block.

                //! Second block.
                value: 1
                """;

        assertThrows(
                IllegalArgumentException.class,
                () -> ProtosSourceDocumentation.moduleDocumentation(source));
    }

    @Test
    void rejectsInlineModuleDocumentationMarker() {
        String source = """
                value: 1 //! Not line-leading.
                """;

        assertThrows(
                IllegalArgumentException.class,
                () -> ProtosSourceDocumentation.moduleDocumentation(source));
    }

}
