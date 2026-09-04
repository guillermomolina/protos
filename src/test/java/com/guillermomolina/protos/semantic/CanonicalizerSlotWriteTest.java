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
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.parser.ProtosParser;
import com.guillermomolina.protos.parser.ast.SurfaceExpression;
import com.guillermomolina.protos.parser.ast.SurfaceSequence;
import com.guillermomolina.protos.semantic.ast.CanonicalAssign;
import com.guillermomolina.protos.semantic.ast.CanonicalCreate;
import com.guillermomolina.protos.semantic.ast.CanonicalLookup;
import com.guillermomolina.protos.semantic.ast.CanonicalSend;
import org.junit.jupiter.api.Test;

class CanonicalizerSlotWriteTest {
    private final Canonicalizer canonicalizer = new Canonicalizer();

    @Test
    void lowersBareSlotCreationToImplicitTargetCreate() {
        CanonicalCreate create =
                assertInstanceOf(CanonicalCreate.class, canonicalizeOnly("x: value"));

        assertTrue(create.target().isEmpty());
        assertEquals("x", create.name());
        assertEquals(
                "value",
                assertInstanceOf(CanonicalLookup.class, create.value()).name());
    }

    @Test
    void lowersExplicitMemberSlotCreationToExplicitTargetCreate() {
        CanonicalCreate create =
                assertInstanceOf(CanonicalCreate.class, canonicalizeOnly("object.x: value"));

        assertEquals("x", create.name());
        assertEquals(
                "object",
                assertInstanceOf(CanonicalLookup.class, create.target().orElseThrow()).name());
    }

    @Test
    void lowersBareAssignmentToImplicitTargetAssign() {
        CanonicalAssign assign =
                assertInstanceOf(CanonicalAssign.class, canonicalizeOnly("x = value"));

        assertTrue(assign.target().isEmpty());
        assertEquals("x", assign.name());
        assertEquals(
                "value",
                assertInstanceOf(CanonicalLookup.class, assign.value()).name());
    }

    @Test
    void lowersExplicitMemberAssignmentAndCanonicalizesTargetBeforeValueShape() {
        CanonicalAssign assign =
                assertInstanceOf(
                        CanonicalAssign.class,
                        canonicalizeOnly("(object + other).x = value + offset"));

        assertEquals("x", assign.name());

        CanonicalSend target =
                assertInstanceOf(CanonicalSend.class, assign.target().orElseThrow());
        assertEquals("+", target.message());

        CanonicalSend value =
                assertInstanceOf(CanonicalSend.class, assign.value());
        assertEquals("+", value.message());
    }

    private Object canonicalizeOnly(String source) {
        SurfaceExpression surface = only(source);
        return canonicalizer.canonicalize(surface);
    }

    private SurfaceExpression only(String source) {
        SurfaceSequence program = new ProtosParser(source).parseProgram();
        assertEquals(1, program.expressions().size());
        return program.expressions().get(0);
    }
}
