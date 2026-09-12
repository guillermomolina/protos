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

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.guillermomolina.protos.parser.ProtosParser;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProtosDocumentSymbolsMatchTest {
    @Test
    void traversesMatchGuardAndBodyContainmentWithoutInventingMatchSymbols() {
        String source =
                "outer: subject match {\n"
                        + "case @value when (guardSlot: true) => {\n"
                        + "bodySlot: value\n"
                        + "bodySlot\n"
                        + "}\n"
                        + "case _ => fallback\n"
                        + "}";

        List<ProtosDocumentSymbol> symbols =
                ProtosDocumentSymbols.from(new ProtosParser(source).parseProgram());

        assertEquals(1, symbols.size());
        ProtosDocumentSymbol outer = symbols.get(0);
        assertEquals("outer", outer.name());
        assertEquals(
                List.of("guardSlot", "bodySlot"),
                outer.children().stream().map(ProtosDocumentSymbol::name).toList());
    }
}
