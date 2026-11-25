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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.parser.ast.SurfaceArrayConstruction;
import com.guillermomolina.protos.parser.ast.SurfaceBinary;
import com.guillermomolina.protos.parser.ast.SurfaceCall;
import com.guillermomolina.protos.parser.ast.SurfaceClosure;
import com.guillermomolina.protos.parser.ast.SurfaceContextualMatch;
import com.guillermomolina.protos.parser.ast.SurfaceExpression;
import com.guillermomolina.protos.parser.ast.SurfaceGroup;
import com.guillermomolina.protos.parser.ast.SurfaceIndex;
import com.guillermomolina.protos.parser.ast.SurfaceLiteral;
import com.guillermomolina.protos.parser.ast.SurfaceMapConstruction;
import com.guillermomolina.protos.parser.ast.SurfaceMember;
import com.guillermomolina.protos.parser.ast.SurfaceName;
import com.guillermomolina.protos.parser.ast.SurfaceObject;
import com.guillermomolina.protos.parser.ast.SurfaceSequence;
import com.guillermomolina.protos.parser.ast.SurfaceSlotCreation;
import com.guillermomolina.protos.source.SourceSpan;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProtosParserMatchTest {
    @Test
    void keepsFormerMatchSpellingsOrdinary() {
        assertName("match", only("match"));
        assertName("case", only("case"));
        assertName("when", only("when"));
        assertName("exact", only("exact"));
        assertName("captures", only("captures"));
    }

    @Test
    void parsesMatchAsOrdinaryMemberCall() {
        SurfaceCall call =
                assertInstanceOf(
                        SurfaceCall.class,
                        only("pattern.match(subject)"));

        SurfaceMember member =
                assertInstanceOf(
                        SurfaceMember.class,
                        call.receiver());

        assertEquals("match", member.name());
    }

    @Test
    void rejectsRemovedDedicatedMatchSyntax() {
        assertThrows(
                ParseError.class,
                () ->
                        new ProtosParser(
                                        "subject match {\n"
                                                + "case _ => 1\n"
                                                + "}")
                                .parseProgram());
    }

    @Test
    void i176ParsesBasicContextualMatch() {
        String source = "match(value) {\n    0: 11\n    Any: 53\n}";
        SurfaceContextualMatch match = contextual(source);

        assertName("value", match.subject());
        assertEquals(new SourceSpan(0, source.length()), match.span());
        assertEquals(
                new SourceSpan(source.indexOf('{'), source.length()),
                match.table().span());
        assertEquals(2, match.table().entries().size());

        SurfaceMapConstruction.Entry first = match.table().entries().get(0);
        assertEquals("0", assertInstanceOf(SurfaceLiteral.class, first.key()).value());
        SurfaceLiteral value = assertInstanceOf(SurfaceLiteral.class, first.value());
        assertEquals("11", value.value());
        int valueStart = source.indexOf("11");
        assertEquals(new SourceSpan(valueStart, valueStart + 2), value.span());
        assertEquals(
                new SourceSpan(source.indexOf("0:"), valueStart + 2),
                first.span());
        assertName("Any", match.table().entries().get(1).key());
    }

    @Test
    void i176AcceptsEmptyTable() {
        assertTrue(contextual("match(value) {}").table().entries().isEmpty());
        assertTrue(contextual("match(value) {\n}").table().entries().isEmpty());
    }

    @Test
    void i176AcceptsInlineSemicolonAndMultilineEntries() {
        assertEquals(
                3,
                contextual("match(value) { 0: 1; 1: 2; Any: 3 }")
                        .table().entries().size());
        assertEquals(
                4,
                contextual("match(value) {\n    0: 1; 1: 2\n\n    2: 3\n    Any: 4\n}")
                        .table().entries().size());
    }

    @Test
    void i176AcceptsContinuationAfterColon() {
        SurfaceContextualMatch match =
                contextual("match(value) {\n    0:\n        11\n}");
        assertEquals(
                "11",
                assertInstanceOf(
                        SurfaceLiteral.class,
                        match.table().entries().get(0).value()).value());
    }

    @Test
    void i176KeepsStructuralMatcherKeysAsOrdinaryConstructions() {
        SurfaceContextualMatch match = contextual(
                "match(value) {\n"
                        + "    [1, Capture]: x => x\n"
                        + "    %{ name: Capture }: (n) => n\n"
                        + "}");
        assertInstanceOf(
                SurfaceArrayConstruction.class,
                match.table().entries().get(0).key());
        assertInstanceOf(
                SurfaceMapConstruction.class,
                match.table().entries().get(1).key());
    }

    @Test
    void i176KeepsExplicitAndGroupedClosureActionsUnchanged() {
        SurfaceContextualMatch match = contextual(
                "match(value) {\n"
                        + "    [Capture, Capture]: (x, y) => x + y\n"
                        + "    [Capture]: (x => x)\n"
                        + "    Any: () => {\n        a: 1\n        a + 1\n    }\n"
                        + "}");
        SurfaceExpression explicit = match.table().entries().get(0).value();
        SurfaceClosure closure = assertInstanceOf(SurfaceClosure.class, explicit);
        assertEquals(2, closure.parameters().size());
        assertFalse(SurfaceContextualMatch.isImplicitAction(explicit));
        assertSame(explicit, SurfaceContextualMatch.action(explicit));

        SurfaceExpression grouped = match.table().entries().get(1).value();
        assertInstanceOf(SurfaceGroup.class, grouped);
        assertFalse(SurfaceContextualMatch.isImplicitAction(grouped));
        assertSame(grouped, SurfaceContextualMatch.action(grouped));

        SurfaceClosure block = assertInstanceOf(
                SurfaceClosure.class,
                match.table().entries().get(2).value());
        assertFalse(block.expressionBody());
        assertEquals(2, block.body().expressions().size());
    }

    @Test
    void i176ProjectsImplicitActionsWithoutReplacingSource() {
        SurfaceContextualMatch match = contextual(
                "match(value) {\n"
                        + "    0: handler\n"
                        + "    1: handler(2)\n"
                        + "    2: (40 + 2)\n"
                        + "}");
        for (SurfaceMapConstruction.Entry entry : match.table().entries()) {
            SurfaceExpression raw = entry.value();
            assertFalse(raw instanceof SurfaceClosure);
            assertTrue(SurfaceContextualMatch.isImplicitAction(raw));
            SurfaceClosure action = assertInstanceOf(
                    SurfaceClosure.class,
                    SurfaceContextualMatch.action(raw));
            assertTrue(action.parameters().isEmpty());
            assertEquals(List.of(raw), action.body().expressions());
            assertEquals(raw.span(), action.span());
            assertEquals(raw.span(), action.body().span());
        }
        assertInstanceOf(SurfaceName.class, match.table().entries().get(0).value());
        assertInstanceOf(SurfaceCall.class, match.table().entries().get(1).value());
        assertInstanceOf(SurfaceGroup.class, match.table().entries().get(2).value());
    }

    @Test
    void i176DistinguishesObjectValueFromBracedClosureBody() {
        SurfaceContextualMatch match = contextual(
                "match(value) {\n"
                        + "    0: { x: 10 }\n"
                        + "    1: () => { x: 10 }\n"
                        + "}");
        SurfaceExpression object = match.table().entries().get(0).value();
        assertInstanceOf(SurfaceObject.class, object);
        assertTrue(SurfaceContextualMatch.isImplicitAction(object));

        SurfaceClosure closure = assertInstanceOf(
                SurfaceClosure.class,
                match.table().entries().get(1).value());
        assertFalse(closure.expressionBody());
        assertInstanceOf(
                SurfaceSlotCreation.class,
                closure.body().expressions().get(0));
    }

    @Test
    void i176ParsesNestedContextualMatches() {
        SurfaceContextualMatch outer = contextual(
                "match(a) {\n"
                        + "    0: match(b) {\n        1: 2\n        Any: 3\n    }\n"
                        + "    Any: 4\n"
                        + "}");
        SurfaceContextualMatch inner = assertInstanceOf(
                SurfaceContextualMatch.class,
                outer.table().entries().get(0).value());
        assertName("b", inner.subject());
        assertEquals(2, inner.table().entries().size());
        assertEquals(2, outer.table().entries().size());
    }

    @Test
    void i176AppliesPostfixSuffixesToCompleteExpression() {
        SurfaceMember member = assertInstanceOf(
                SurfaceMember.class,
                only("match(value) { Any: 1 }.name"));
        assertInstanceOf(SurfaceContextualMatch.class, member.receiver());

        SurfaceCall call = assertInstanceOf(
                SurfaceCall.class,
                only("match(value) { Any: f }(2)"));
        assertInstanceOf(SurfaceContextualMatch.class, call.receiver());

        SurfaceIndex index = assertInstanceOf(
                SurfaceIndex.class,
                only("match(value) { Any: items }[0]"));
        assertInstanceOf(SurfaceContextualMatch.class, index.receiver());

        SurfaceBinary binary = assertInstanceOf(
                SurfaceBinary.class,
                only("match(value) { Any: 1 } + 2"));
        assertInstanceOf(SurfaceContextualMatch.class, binary.left());
    }

    @Test
    void i176KeepsSlotCreationInSubject() {
        SurfaceContextualMatch match = contextual(
                "match(flag: true) {\n    true: 11\n    Any: 23\n}");
        SurfaceSlotCreation creation = assertInstanceOf(
                SurfaceSlotCreation.class,
                match.subject());
        assertName("flag", creation.target());
    }

    @Test
    void i176KeepsOrdinaryCallShapes() {
        SurfaceCall bare = assertInstanceOf(SurfaceCall.class, only("match(value)"));
        assertName("match", bare.receiver());
        assertEquals(1, bare.arguments().size());

        SurfaceCall grouped = assertInstanceOf(
                SurfaceCall.class,
                only("(match)(value) { body }"));
        assertInstanceOf(SurfaceGroup.class, grouped.receiver());
        assertInstanceOf(
                SurfaceClosure.class,
                grouped.arguments().get(1).expression());

        SurfaceCall member = assertInstanceOf(
                SurfaceCall.class,
                only("object.match(value) { body }"));
        assertInstanceOf(SurfaceMember.class, member.receiver());

        SurfaceCall zero = assertInstanceOf(SurfaceCall.class, only("match() { body }"));
        assertEquals(1, zero.arguments().size());

        SurfaceCall two = assertInstanceOf(
                SurfaceCall.class,
                only("match(value, callback) { body }"));
        assertEquals(3, two.arguments().size());

        SurfaceCall spread = assertInstanceOf(
                SurfaceCall.class,
                only("match(...arguments) { body }"));
        assertTrue(spread.arguments().get(0).spread());
    }

    @Test
    void i176DoesNotAttachNewlineSeparatedBraces() {
        SurfaceSequence program =
                new ProtosParser("match(value)\n{ body }").parseProgram();
        assertEquals(2, program.expressions().size());
        assertInstanceOf(SurfaceCall.class, program.expressions().get(0));
        assertInstanceOf(SurfaceObject.class, program.expressions().get(1));
    }

    @Test
    void i176AttachesAcrossBlockCommentButNotLineComment() {
        String source = "match(value) /* header\n comment */ { Any: 1 }";
        SurfaceContextualMatch match = contextual(source);
        assertEquals(source.indexOf('{'), match.table().span().startOffset());

        SurfaceSequence program =
                new ProtosParser("match(value) // note\n{ body }").parseProgram();
        assertEquals(2, program.expressions().size());
        assertInstanceOf(SurfaceCall.class, program.expressions().get(0));
    }

    @Test
    void i176RejectsMalformedTablesWithoutTrailingClosureRetry() {
        for (String source : List.of(
                "match(value) { body }",
                "match(value) { 0 }",
                "match(value) { 0: }",
                "match(value) { 0: 1, 1: 2 }",
                "match(value) { 0: 1; }",
                "match(value) { ; 0: 1 }",
                "match(value) { 0: 1;; 1: 2 }",
                "match(value) { x: 1\n y }")) {
            assertThrows(
                    ParseError.class,
                    () -> new ProtosParser(source).parseProgram(),
                    source);
        }
    }

    private SurfaceContextualMatch contextual(String source) {
        return assertInstanceOf(SurfaceContextualMatch.class, only(source));
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
