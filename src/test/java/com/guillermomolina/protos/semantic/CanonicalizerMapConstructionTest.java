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

package com.guillermomolina.protos.semantic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.guillermomolina.protos.parser.ProtosParser;
import com.guillermomolina.protos.semantic.ast.CanonicalLookup;
import com.guillermomolina.protos.semantic.ast.CanonicalMapConstruction;
import com.guillermomolina.protos.semantic.ast.CanonicalSequence;
import org.junit.jupiter.api.Test;

class CanonicalizerMapConstructionTest {
    private final Canonicalizer canonicalizer = new Canonicalizer();

    @Test
    void preservesDedicatedSequentialConstructionWithOrdinaryMapLookup() {
        CanonicalSequence program = assertInstanceOf(
                CanonicalSequence.class,
                canonicalizer.canonicalize(
                        new ProtosParser(
                                        "%{\n"
                                                + "  key: value\n"
                                                + "  other: next\n"
                                                + "}")
                                .parseProgram()));

        CanonicalMapConstruction map = assertInstanceOf(
                CanonicalMapConstruction.class,
                program.expressions().get(0));
        CanonicalLookup factory = assertInstanceOf(
                CanonicalLookup.class,
                map.factory());

        assertEquals("Map", factory.name());
        assertEquals(2, map.entries().size());
        assertEquals(
                "key",
                assertInstanceOf(CanonicalLookup.class, map.entries().get(0).key()).name());
        assertEquals(
                "value",
                assertInstanceOf(CanonicalLookup.class, map.entries().get(0).value()).name());
    }
}
