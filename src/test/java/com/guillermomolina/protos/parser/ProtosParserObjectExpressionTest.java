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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.parser.ast.SurfaceExpression;
import com.guillermomolina.protos.parser.ast.SurfaceGroup;
import com.guillermomolina.protos.parser.ast.SurfaceMember;
import com.guillermomolina.protos.parser.ast.SurfaceName;
import com.guillermomolina.protos.parser.ast.SurfaceObject;
import com.guillermomolina.protos.parser.ast.SurfaceSequence;
import com.guillermomolina.protos.parser.ast.SurfaceSlotCreation;
import org.junit.jupiter.api.Test;

class ProtosParserObjectExpressionTest {
    @Test
    void parsesEmptyAndPopulatedObjectBodies() {
        SurfaceObject empty = assertInstanceOf(SurfaceObject.class, only("{}"));
        assertTrue(empty.parent().isEmpty());
        assertEquals(0, empty.items().size());

        SurfaceObject object = assertInstanceOf(
                SurfaceObject.class,
                only("{\nname: value\nother: second\n}"));
        assertTrue(object.parent().isEmpty());
        assertEquals(2, object.items().size());
        assertInstanceOf(SurfaceSlotCreation.class, object.items().get(0).expression());
        assertFalse(object.items().get(0).composition());
    }

    @Test
    void parsesContextualCompositionItems() {
        SurfaceObject object =
                assertInstanceOf(SurfaceObject.class, only("{ ...base; name: value }"));

        assertEquals(2, object.items().size());
        assertTrue(object.items().get(0).composition());
        assertName("base", object.items().get(0).expression());
        assertFalse(object.items().get(1).composition());
    }

    @Test
    void parsesAllowedParentExpressions() {
        SurfaceObject bare =
                assertInstanceOf(SurfaceObject.class, only("animal {}"));
        assertName("animal", bare.parent().orElseThrow());

        SurfaceObject member =
                assertInstanceOf(SurfaceObject.class, only("library.models.animal {}"));
        assertInstanceOf(SurfaceMember.class, member.parent().orElseThrow());

        SurfaceObject grouped =
                assertInstanceOf(SurfaceObject.class, only("(factory()) {}"));
        assertInstanceOf(SurfaceGroup.class, grouped.parent().orElseThrow());
    }

    @Test
    void rejectsDisallowedUnparenthesizedParents() {
        assertThrows(ParseError.class, () -> only("factory() {}"));
        assertThrows(ParseError.class, () -> only("values[0] {}"));
    }

    @Test
    void objectBodyRequiresRealSeparators() {
        assertThrows(ParseError.class, () -> only("{ ...base name: value }"));
        assertThrows(ParseError.class, () -> only("{ ; name: value }"));
        assertThrows(ParseError.class, () -> only("{ name: value; }"));
        assertThrows(ParseError.class, () -> only("{ name: value;; other: value }"));
    }

    @Test
    void compositionRemainsContextual() {
        assertThrows(ParseError.class, () -> only("...base"));
    }

    @Test
    void objectExpressionCanContinueThroughPostfixOperations() {
        SurfaceMember member =
                assertInstanceOf(SurfaceMember.class, only("animal {}.name"));
        assertEquals("name", member.name());
        assertInstanceOf(SurfaceObject.class, member.receiver());
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
