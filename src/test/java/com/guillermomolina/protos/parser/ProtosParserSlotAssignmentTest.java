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

import com.guillermomolina.protos.parser.ast.SurfaceAssignment;
import com.guillermomolina.protos.parser.ast.SurfaceExpression;
import com.guillermomolina.protos.parser.ast.SurfaceIndex;
import com.guillermomolina.protos.parser.ast.SurfaceMember;
import com.guillermomolina.protos.parser.ast.SurfaceName;
import com.guillermomolina.protos.parser.ast.SurfaceSequence;
import com.guillermomolina.protos.parser.ast.SurfaceSlotCreation;
import org.junit.jupiter.api.Test;

class ProtosParserSlotAssignmentTest {
    @Test
    void parsesBareAndMemberSlotCreation() {
        SurfaceSlotCreation bare =
                assertInstanceOf(SurfaceSlotCreation.class, only("name: value"));
        assertName("name", bare.target());
        assertName("value", bare.value());

        SurfaceSlotCreation member =
                assertInstanceOf(SurfaceSlotCreation.class, only("object[index].name: value"));
        SurfaceMember target = assertInstanceOf(SurfaceMember.class, member.target());
        assertEquals("name", target.name());
    }

    @Test
    void indexedTargetIsAllowedForAssignment() {
        SurfaceAssignment assignment =
                assertInstanceOf(SurfaceAssignment.class, only("object[index] = value"));
        assertInstanceOf(SurfaceIndex.class, assignment.target());
        assertName("value", assignment.value());
    }

    @Test
    void finalIndexIsRejectedForSlotCreation() {
        assertThrows(ParseError.class, () -> only("object[index]: value"));
    }

    @Test
    void groupedAndBinaryTargetsAreRejected() {
        assertThrows(ParseError.class, () -> only("(name) = value"));
        assertThrows(ParseError.class, () -> only("a + b = value"));
    }

    @Test
    void assignmentAssociatesToTheRightThroughExpressionRhs() {
        SurfaceAssignment outer =
                assertInstanceOf(SurfaceAssignment.class, only("a = b = c"));
        assertName("a", outer.target());

        SurfaceAssignment inner =
                assertInstanceOf(SurfaceAssignment.class, outer.value());
        assertName("b", inner.target());
        assertName("c", inner.value());
    }

    @Test
    void requiredRightHandSideContinuesAcrossNewline() {
        SurfaceSlotCreation creation =
                assertInstanceOf(SurfaceSlotCreation.class, only("name:\nvalue"));
        assertName("value", creation.value());

        SurfaceAssignment assignment =
                assertInstanceOf(SurfaceAssignment.class, only("name =\nvalue"));
        assertName("value", assignment.value());
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
