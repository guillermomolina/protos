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
import com.guillermomolina.protos.parser.ast.SurfaceName;
import com.guillermomolina.protos.parser.ast.SurfaceNonLocalReturn;
import com.guillermomolina.protos.parser.ast.SurfaceSequence;
import org.junit.jupiter.api.Test;

class ProtosParserNonLocalReturnTest {
    @Test
    void parsesNonLocalReturnWithBinaryExpression() {
        SurfaceNonLocalReturn result =
                assertInstanceOf(SurfaceNonLocalReturn.class, only("^ a + b * c"));

        SurfaceBinary expression =
                assertInstanceOf(SurfaceBinary.class, result.expression());
        assertEquals("+", expression.operator());
        assertName("a", expression.left());

        SurfaceBinary multiplication =
                assertInstanceOf(SurfaceBinary.class, expression.right());
        assertEquals("*", multiplication.operator());
        assertName("b", multiplication.left());
        assertName("c", multiplication.right());
    }

    @Test
    void parsesNonLocalReturnAcrossContinuationNewline() {
        SurfaceNonLocalReturn result =
                assertInstanceOf(SurfaceNonLocalReturn.class, only("^\n  value"));

        assertName("value", result.expression());
    }

    @Test
    void caretWithoutExpressionIsRejected() {
        assertThrows(ParseError.class, () -> only("^"));
    }

    @Test
    void caretAfterAnOrdinaryExpressionIsRejected() {
        assertThrows(ParseError.class, () -> only("value ^ other"));
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
