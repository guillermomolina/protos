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

import com.guillermomolina.protos.parser.ast.SurfaceBinary;
import com.guillermomolina.protos.parser.ast.SurfaceCall;
import com.guillermomolina.protos.parser.ast.SurfaceExpression;
import com.guillermomolina.protos.parser.ast.SurfaceMatch;
import com.guillermomolina.protos.parser.ast.SurfaceMatchPattern;
import com.guillermomolina.protos.parser.ast.SurfaceMember;
import com.guillermomolina.protos.parser.ast.SurfaceName;
import com.guillermomolina.protos.parser.ast.SurfaceSequence;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProtosParserMatchTest {
    @Test
    void parsesLowPrecedenceExpressionValuedMatchEnvelope() {
        SurfaceMatch match =
                match(
                        "a + b match {\n"
                                + "  case 1 => first\n"
                                + "  case _ => { second\n third }\n"
                                + "}");

        SurfaceBinary subject = assertInstanceOf(SurfaceBinary.class, match.subject());
        assertEquals("+", subject.operator());
        assertEquals(2, match.arms().size());
        assertTrue(match.arms().get(0).expressionBody());
        assertFalse(match.arms().get(1).expressionBody());
        assertEquals(2, match.arms().get(1).body().expressions().size());
    }

    @Test
    void keepsContextualSpellingsOrdinaryOutsidePatternStructure() {
        assertName("match", only("match"));
        assertName("case", only("case"));
        assertName("when", only("when"));
        assertName("exact", only("exact"));
        assertName("captures", only("captures"));

        SurfaceCall call = assertInstanceOf(SurfaceCall.class, only("pattern.match(subject)"));
        SurfaceMember member = assertInstanceOf(SurfaceMember.class, call.receiver());
        assertEquals("match", member.name());
    }

    @Test
    void parsesMatcherValuesBindersWildcardAliasesAndCaptureInterfaces() {
        SurfaceMatch match =
                match(
                        "subject match {\n"
                                + "case patterns.userName => 1\n"
                                + "case @whole: (leftPattern | rightPattern) => 2\n"
                                + "case opaque captures(left, right) => 3\n"
                                + "case dynamicMatcher captures(first, ...rest) => 4\n"
                                + "case _ => 5\n"
                                + "}");

        assertInstanceOf(
                SurfaceMatchPattern.Value.class,
                match.arms().get(0).pattern());
        SurfaceMatchPattern.Alias alias =
                assertInstanceOf(
                        SurfaceMatchPattern.Alias.class,
                        match.arms().get(1).pattern());
        assertEquals("whole", alias.name());
        SurfaceMatchPattern.Group grouped =
                assertInstanceOf(SurfaceMatchPattern.Group.class, alias.pattern());
        assertInstanceOf(SurfaceMatchPattern.Or.class, grouped.pattern());

        SurfaceMatchPattern.Value fixed =
                assertInstanceOf(
                        SurfaceMatchPattern.Value.class,
                        match.arms().get(2).pattern());
        assertEquals(
                List.of("left", "right"),
                fixed.captureInterface().orElseThrow().requiredNames());

        SurfaceMatchPattern.Value dynamic =
                assertInstanceOf(
                        SurfaceMatchPattern.Value.class,
                        match.arms().get(3).pattern());
        assertEquals("rest", dynamic.captureInterface().orElseThrow().restName().orElseThrow());
    }

    @Test
    void parsesArrayRemainderMapExactAndMapRemainderForms() {
        SurfaceMatch match =
                match(
                        "subject match {\n"
                                + "case [1, @first, ...@middle, @last] => 1\n"
                                + "case %{ \"name\": @name, ...@rest } => 2\n"
                                + "case exact %{ \"name\": @name } => 3\n"
                                + "case _ => 4\n"
                                + "}");

        SurfaceMatchPattern.ArrayPattern array =
                assertInstanceOf(
                        SurfaceMatchPattern.ArrayPattern.class,
                        match.arms().get(0).pattern());
        assertEquals(2, array.prefix().size());
        assertEquals(1, array.suffix().size());
        assertTrue(array.remainder().orElseThrow().pattern().isPresent());

        SurfaceMatchPattern.MapPattern openMap =
                assertInstanceOf(
                        SurfaceMatchPattern.MapPattern.class,
                        match.arms().get(1).pattern());
        assertFalse(openMap.exact());
        assertEquals(1, openMap.entries().size());
        assertTrue(openMap.remainder().isPresent());

        SurfaceMatchPattern.MapPattern exactMap =
                assertInstanceOf(
                        SurfaceMatchPattern.MapPattern.class,
                        match.arms().get(2).pattern());
        assertTrue(exactMap.exact());
    }

    @Test
    void appliesD100DelimiterFirstGuardArrowRule() {
        SurfaceMatch match =
                match(
                        "subject match {\n"
                                + "case p when x => y => body\n"
                                + "}");

        SurfaceName guard =
                assertInstanceOf(
                        SurfaceName.class,
                        match.arms().get(0).guard().orElseThrow());
        assertEquals("x", guard.name());

        SurfaceExpression bodyExpression =
                match.arms().get(0).body().expressions().get(0);
        com.guillermomolina.protos.parser.ast.SurfaceClosure closure =
                assertInstanceOf(
                        com.guillermomolina.protos.parser.ast.SurfaceClosure.class,
                        bodyExpression);
        assertEquals("y", closure.parameters().get(0).name());
    }

    @Test
    void allowsGroupedAndNestedClosuresInsideD100Guard() {
        SurfaceMatch grouped =
                match(
                        "subject match {\n"
                                + "case p when (x => y) => body\n"
                                + "}");
        com.guillermomolina.protos.parser.ast.SurfaceGroup groupedGuard =
                assertInstanceOf(
                        com.guillermomolina.protos.parser.ast.SurfaceGroup.class,
                        grouped.arms().get(0).guard().orElseThrow());
        assertInstanceOf(
                com.guillermomolina.protos.parser.ast.SurfaceClosure.class,
                groupedGuard.expression());

        SurfaceMatch nested =
                match(
                        "subject match {\n"
                                + "case p when accepts(x => x) => body\n"
                                + "}");
        SurfaceCall call =
                assertInstanceOf(
                        SurfaceCall.class,
                        nested.arms().get(0).guard().orElseThrow());
        assertInstanceOf(
                com.guillermomolina.protos.parser.ast.SurfaceClosure.class,
                call.arguments().get(0).expression());
    }

    @Test
    void newlineBeforeD100ArmArrowDoesNotChangeOwnership() {
        SurfaceMatch match =
                match(
                        "subject match {\n"
                                + "case p when ready\n"
                                + "  => body\n"
                                + "}");
        SurfaceName guard =
                assertInstanceOf(
                        SurfaceName.class,
                        match.arms().get(0).guard().orElseThrow());
        assertEquals("ready", guard.name());
    }

    @Test
    void parsesGuardAndAllowsLaterArmAfterGuardedIrrefutablePattern() {
        SurfaceMatch match =
                match(
                        "subject match {\n"
                                + "case @value when predicate(value) => value\n"
                                + "case _ => fallback\n"
                                + "}");

        assertTrue(match.arms().get(0).guard().isPresent());
        assertEquals(2, match.arms().size());
    }

    @Test
    void rejectsStructurallyUnreachableArmsAndOrAlternatives() {
        assertThrows(
                ParseError.class,
                () -> only("x match { case _ => 1; case other => 2 }"));
        assertThrows(
                ParseError.class,
                () -> only("x match { case @value => 1\ncase other => 2 }"));
        assertThrows(
                ParseError.class,
                () -> only("x match { case _ | other => 1 }"));
    }

    @Test
    void rejectsDuplicateFixedBindersAndMismatchedFixedOrInterfaces() {
        assertThrows(
                ParseError.class,
                () -> only("x match { case [@x, @x] => x }"));
        assertThrows(
                ParseError.class,
                () -> only("x match { case @x: [@x] => x }"));
        assertThrows(
                ParseError.class,
                () -> only("x match { case [1, @x] | [2, @y] => x }"));

        SurfaceMatch valid =
                match("x match { case [1, @x] | [2, @x] => x }");
        assertEquals(1, valid.arms().size());
    }

    @Test
    void permitsDynamicOpaqueCaptureInterfaceWithoutInventingFixedOrMetadata() {
        SurfaceMatch match =
                match(
                        "x match {\n"
                                + "case left captures(first, ...rest) | right captures(first, ...rest) => first\n"
                                + "}");
        assertInstanceOf(SurfaceMatchPattern.Or.class, match.arms().get(0).pattern());
    }

    @Test
    void rejectsMalformedListAndCaptureShapes() {
        assertThrows(ParseError.class, () -> only("x match { case [1,] => 1 }"));
        assertThrows(ParseError.class, () -> only("x match { case %{ \"x\": @x, } => 1 }"));
        assertThrows(ParseError.class, () -> only("x match { case matcher captures() => 1 }"));
        assertThrows(ParseError.class, () -> only("x match { case matcher captures(a, a) => 1 }"));
        assertThrows(
                ParseError.class,
                () -> only("x match { case exact %{ \"x\": @x, ...@rest } => x }"));
        assertThrows(ParseError.class, () -> only("x match { }"));
    }

    @Test
    void emptyArrayPatternIsValidAndMapKeysUseBinaryExpressionGrammar() {
        SurfaceMatch match =
                match(
                        "x match {\n"
                                + "case [] => empty\n"
                                + "case %{ key + 1: @value } => value\n"
                                + "case _ => fallback\n"
                                + "}");
        assertInstanceOf(
                SurfaceMatchPattern.ArrayPattern.class,
                match.arms().get(0).pattern());

        SurfaceMatchPattern.MapPattern map =
                assertInstanceOf(
                        SurfaceMatchPattern.MapPattern.class,
                        match.arms().get(1).pattern());
        assertInstanceOf(SurfaceBinary.class, map.entries().get(0).key());
    }

    private SurfaceMatch match(String source) {
        return assertInstanceOf(SurfaceMatch.class, only(source));
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
