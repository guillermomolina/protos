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
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.parser.ast.SurfaceArgument;
import com.guillermomolina.protos.parser.ast.SurfaceCall;
import com.guillermomolina.protos.parser.ast.SurfaceClosure;
import com.guillermomolina.protos.parser.ast.SurfaceExpression;
import com.guillermomolina.protos.parser.ast.SurfaceName;
import com.guillermomolina.protos.parser.ast.SurfaceSequence;
import com.guillermomolina.protos.parser.ast.SurfaceSuperSend;
import org.junit.jupiter.api.Test;

class ProtosParserEllipsisContinuationTest {
    @Test
    void spreadArgumentContinuesAcrossNewlineAfterEllipsis() {
        SurfaceCall call = assertInstanceOf(SurfaceCall.class, only("foo(...\nitems)"));

        SurfaceArgument argument = call.arguments().get(0);
        assertTrue(argument.spread());
        assertName("items", argument.expression());
    }

    @Test
    void superSpreadArgumentUsesTheSameContinuationRule() {
        SurfaceSuperSend send =
                assertInstanceOf(SurfaceSuperSend.class, only("super.foo(...\nitems)"));

        SurfaceArgument argument = send.arguments().get(0);
        assertTrue(argument.spread());
        assertName("items", argument.expression());
    }

    @Test
    void restParameterContinuesAcrossNewlineAfterEllipsis() {
        SurfaceClosure closure =
                assertInstanceOf(SurfaceClosure.class, only("(...\nitems) => items"));

        assertEquals(1, closure.parameters().size());
        assertEquals("items", closure.parameters().get(0).name());
        assertTrue(closure.parameters().get(0).rest());
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
