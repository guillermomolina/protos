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

package com.guillermomolina.protos.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.guillermomolina.protos.parser.ast.SurfaceBinary;
import com.guillermomolina.protos.parser.ast.SurfaceExpression;
import com.guillermomolina.protos.parser.ast.SurfaceGroup;
import com.guillermomolina.protos.parser.ast.SurfaceName;
import com.guillermomolina.protos.parser.ast.SurfaceSequence;
import org.junit.jupiter.api.Test;

class ProtosParserCustomOperatorTest {
    @Test
    void customOperatorChainsAssociateLeft() {
        SurfaceBinary outer = assertInstanceOf(
                SurfaceBinary.class, only("a @ b |> c"));

        assertEquals("|>", outer.operator());
        assertName("c", outer.right());

        SurfaceBinary inner = assertInstanceOf(SurfaceBinary.class, outer.left());
        assertEquals("@", inner.operator());
        assertName("a", inner.left());
        assertName("b", inner.right());
    }

    @Test
    void customOperatorContinuesAcrossNewlineWhenOperandIsRequired() {
        SurfaceBinary expression = assertInstanceOf(
                SurfaceBinary.class, only("left @\nright"));

        assertEquals("@", expression.operator());
        assertName("left", expression.left());
        assertName("right", expression.right());
    }

    @Test
    void unparenthesizedStandardThenCustomMixIsRejected() {
        assertThrows(ParseError.class, () -> only("a + b @ c"));
    }

    @Test
    void unparenthesizedCustomThenStandardMixIsRejected() {
        assertThrows(ParseError.class, () -> only("a @ b * c"));
    }

    @Test
    void groupingAllowsCrossDomainComposition() {
        SurfaceBinary customOuter = assertInstanceOf(
                SurfaceBinary.class, only("(a + b) @ c"));
        assertEquals("@", customOuter.operator());
        assertInstanceOf(SurfaceGroup.class, customOuter.left());

        SurfaceBinary standardOuter = assertInstanceOf(
                SurfaceBinary.class, only("(a @ b) + c"));
        assertEquals("+", standardOuter.operator());
        assertInstanceOf(SurfaceGroup.class, standardOuter.left());
    }

    private SurfaceExpression only(String source) {
        SurfaceSequence program = new ProtosParser(source).parseProgram();
        assertEquals(1, program.expressions().size());
        return program.expressions().get(0);
    }

    private void assertName(String expected, SurfaceExpression expression) {
        SurfaceName name = assertInstanceOf(SurfaceName.class, expression);
        assertEquals(expected, name.name());
    }
}
