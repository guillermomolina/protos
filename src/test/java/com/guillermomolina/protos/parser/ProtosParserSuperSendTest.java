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
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.parser.ast.SurfaceArgument;
import com.guillermomolina.protos.parser.ast.SurfaceExpression;
import com.guillermomolina.protos.parser.ast.SurfaceMember;
import com.guillermomolina.protos.parser.ast.SurfaceName;
import com.guillermomolina.protos.parser.ast.SurfaceSequence;
import com.guillermomolina.protos.parser.ast.SurfaceSuperSend;
import org.junit.jupiter.api.Test;

class ProtosParserSuperSendTest {
    @Test
    void parsesSuperMessageSend() {
        SurfaceSuperSend send =
                assertInstanceOf(SurfaceSuperSend.class, only("super.move(x, ...rest)"));

        assertEquals("move", send.message());
        assertEquals(2, send.arguments().size());

        SurfaceArgument first = send.arguments().get(0);
        assertTrue(!first.spread());
        assertName("x", first.expression());

        SurfaceArgument second = send.arguments().get(1);
        assertTrue(second.spread());
        assertName("rest", second.expression());
    }

    @Test
    void acceptsReservedSpellingsAsSuperMessageNames() {
        assertEquals("true",
                assertInstanceOf(SurfaceSuperSend.class, only("super.true()")).message());
        assertEquals("this",
                assertInstanceOf(SurfaceSuperSend.class, only("super.this()")).message());
        assertEquals("super",
                assertInstanceOf(SurfaceSuperSend.class, only("super.super()")).message());
    }

    @Test
    void superSendCanParticipateInPostfixChain() {
        SurfaceMember member =
                assertInstanceOf(SurfaceMember.class, only("super.make().value"));
        assertEquals("value", member.name());
        assertInstanceOf(SurfaceSuperSend.class, member.receiver());
    }

    @Test
    void bareSuperAndMethodExtractionAreRejected() {
        assertThrows(ParseError.class, () -> only("super"));
        assertThrows(ParseError.class, () -> only("super.value"));
    }

    @Test
    void superSendRequiresAnArgumentList() {
        assertThrows(ParseError.class, () -> only("super.move"));
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
