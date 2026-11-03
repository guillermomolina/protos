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

import com.guillermomolina.protos.parser.ast.SurfaceArgument;
import com.guillermomolina.protos.parser.ast.SurfaceArrayConstruction;
import com.guillermomolina.protos.parser.ast.SurfaceCall;
import com.guillermomolina.protos.parser.ast.SurfaceContextualFor;
import com.guillermomolina.protos.parser.ast.SurfaceContextualIf;
import com.guillermomolina.protos.parser.ast.SurfaceContextualUntil;
import com.guillermomolina.protos.parser.ast.SurfaceContextualWhile;
import com.guillermomolina.protos.parser.ast.SurfaceExpression;
import com.guillermomolina.protos.parser.ast.SurfaceGroup;
import com.guillermomolina.protos.parser.ast.SurfaceMember;
import com.guillermomolina.protos.parser.ast.SurfaceNonLocalReturn;
import com.guillermomolina.protos.parser.ast.SurfaceSequence;
import com.guillermomolina.protos.parser.ast.SurfaceSlotCreation;
import com.guillermomolina.protos.parser.ast.SurfaceSuperSend;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** I143 / D237: contextual named call arguments, before runtime integration. */
class ProtosParserNamedArgumentsTest {

    @Test
    void ordinaryCallPreservesMixedArgumentOrderAndLabels() {
        SurfaceCall call = assertInstanceOf(
                SurfaceCall.class,
                only("f(c: 30, 1, ...items, a: 10)"));

        assertEquals(4, call.arguments().size());
        assertEquals(Optional.of("c"), call.arguments().get(0).name());
        assertTrue(call.arguments().get(1).name().isEmpty());
        assertFalse(call.arguments().get(1).spread());
        assertTrue(call.arguments().get(2).spread());
        assertTrue(call.arguments().get(2).name().isEmpty());
        assertEquals(Optional.of("a"), call.arguments().get(3).name());
    }

    @Test
    void groupedSlotCreationRemainsPositional() {
        SurfaceCall call = assertInstanceOf(
                SurfaceCall.class, only("f((a: 10), b: 20)"));

        SurfaceArgument first = call.arguments().get(0);
        assertTrue(first.name().isEmpty());

        SurfaceGroup group =
                assertInstanceOf(SurfaceGroup.class, first.expression());
        assertInstanceOf(
                SurfaceSlotCreation.class, group.expression());

        assertEquals(Optional.of("b"), call.arguments().get(1).name());
    }

    @Test
    void memberSlotCreationIsNotANameLabel() {
        SurfaceCall call = assertInstanceOf(
                SurfaceCall.class, only("f(object.a: 10)"));

        assertTrue(call.arguments().get(0).name().isEmpty());

        SurfaceSlotCreation creation = assertInstanceOf(
                SurfaceSlotCreation.class,
                call.arguments().get(0).expression());

        assertInstanceOf(
                SurfaceMember.class, creation.target());
    }

    @Test
    void arrayAndSuperCallsUseSameArgumentLabels() {
        SurfaceArrayConstruction array = assertInstanceOf(
                SurfaceArrayConstruction.class,
                only("[a: 10, 20]"));

        assertEquals(
                Optional.of("a"), array.arguments().get(0).name());
        assertTrue(array.arguments().get(1).name().isEmpty());

        SurfaceArrayConstruction escaped = assertInstanceOf(
                SurfaceArrayConstruction.class,
                only("[(a: 10)]"));

        assertInstanceOf(
                SurfaceGroup.class,
                escaped.arguments().get(0).expression());

        assertTrue(escaped.arguments().get(0).name().isEmpty());

        SurfaceSuperSend superSend = assertInstanceOf(
                SurfaceSuperSend.class,
                only("super.call(a: 10, 20)"));

        assertEquals(
                Optional.of("a"),
                superSend.arguments().get(0).name());
    }

    @Test
    void duplicateLabelsFailOnlyWithinTheirOwnArgumentList() {
        assertThrows(
                ParseError.class,
                () -> only("f(a: 1, a: 2)"));

        assertThrows(
                ParseError.class,
                () -> only("[a: 1, a: 2]"));

        assertThrows(
                ParseError.class,
                () -> only("super.run(a: 1, a: 2)"));

        SurfaceCall nested = assertInstanceOf(
                SurfaceCall.class,
                only("f(a: g(a: 1), b: 2)"));

        assertEquals(
                Optional.of("a"),
                nested.arguments().get(0).name());

        assertEquals(
                Optional.of("b"),
                nested.arguments().get(1).name());
    }

    @Test
    void contextualFormsRetainSlotCreationExpressions() {
        SurfaceContextualIf conditional = assertInstanceOf(
                SurfaceContextualIf.class,
                only("if(flag: true) { flag }"));

        assertInstanceOf(
                SurfaceSlotCreation.class,
                conditional.condition());

        SurfaceContextualWhile loop = assertInstanceOf(
                SurfaceContextualWhile.class,
                only("while(flag: true) { flag }"));

        assertInstanceOf(
                SurfaceSlotCreation.class,
                loop.condition());

        SurfaceContextualFor numeric = assertInstanceOf(
                SurfaceContextualFor.class,
                only("for(i, start: 0, stop: 5) { i }"));

        assertInstanceOf(
                SurfaceSlotCreation.class,
                numeric.start());

        assertInstanceOf(
                SurfaceSlotCreation.class,
                numeric.stop());

        SurfaceContextualUntil until = assertInstanceOf(
                SurfaceContextualUntil.class,
                only("{ body } until(done: true)"));

        assertInstanceOf(
                SurfaceSlotCreation.class,
                until.condition());

        SurfaceNonLocalReturn returned = assertInstanceOf(
                SurfaceNonLocalReturn.class,
                only("return(value: 10)"));

        SurfaceGroup returnGroup = assertInstanceOf(
                SurfaceGroup.class, returned.expression());

        assertInstanceOf(
                SurfaceSlotCreation.class,
                returnGroup.expression());
    }

    @Test
    void groupedContextualNamesAreOrdinaryNamedCalls() {
        SurfaceCall groupedIf = assertInstanceOf(
                SurfaceCall.class,
                only("(if)(flag: true) { body }"));

        assertEquals(
                Optional.of("flag"),
                groupedIf.arguments().get(0).name());

        assertTrue(groupedIf.arguments().get(1).name().isEmpty());

        SurfaceCall groupedReturn = assertInstanceOf(
                SurfaceCall.class,
                only("(return)(value: 10)"));

        assertEquals(
                Optional.of("value"),
                groupedReturn.arguments().get(0).name());
    }

    private SurfaceExpression only(String source) {
        SurfaceSequence program =
                new ProtosParser(source).parseProgram();

        assertEquals(1, program.expressions().size());
        return program.expressions().get(0);
    }
}
