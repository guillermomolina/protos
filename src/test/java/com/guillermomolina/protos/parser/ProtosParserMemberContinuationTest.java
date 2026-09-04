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

import com.guillermomolina.protos.parser.ast.SurfaceExpression;
import com.guillermomolina.protos.parser.ast.SurfaceMember;
import com.guillermomolina.protos.parser.ast.SurfaceSequence;
import com.guillermomolina.protos.parser.ast.SurfaceSuperSend;
import org.junit.jupiter.api.Test;

class ProtosParserMemberContinuationTest {
    @Test
    void memberSuffixContinuesAcrossNewlineAfterDot() {
        SurfaceMember member =
                assertInstanceOf(SurfaceMember.class, only("object.\nname"));

        assertEquals("name", member.name());
    }

    @Test
    void reservedMemberNameContinuesAcrossNewlineAfterDot() {
        SurfaceMember member =
                assertInstanceOf(SurfaceMember.class, only("object.\ntrue"));

        assertEquals("true", member.name());
    }

    @Test
    void superSendContinuesAfterSuperBeforeDot() {
        SurfaceSuperSend send =
                assertInstanceOf(SurfaceSuperSend.class, only("super\n.foo()"));

        assertEquals("foo", send.message());
    }

    @Test
    void superSendContinuesAfterDotAndBeforeRequiredArgumentList() {
        SurfaceSuperSend afterDot =
                assertInstanceOf(SurfaceSuperSend.class, only("super.\nfoo()"));
        SurfaceSuperSend beforeArguments =
                assertInstanceOf(SurfaceSuperSend.class, only("super.foo\n()"));

        assertEquals("foo", afterDot.message());
        assertEquals("foo", beforeArguments.message());
    }

    @Test
    void completeOrdinaryMemberDoesNotAttachCallAcrossNewline() {
        assertThrows(ParseError.class, () -> only("object.name\n()"));
    }

    private SurfaceExpression only(String source) {
        SurfaceSequence program = new ProtosParser(source).parseProgram();
        assertEquals(1, program.expressions().size());
        return program.expressions().get(0);
    }
}
