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

package com.guillermomolina.protos.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.parser.ProtosParserSourceFacts;
import com.guillermomolina.protos.parser.ProtosParserSourceFacts.SequenceSeparatorKind;
import com.guillermomolina.protos.parser.ast.SurfaceArrayConstruction;
import com.guillermomolina.protos.parser.ast.SurfaceClosure;
import com.guillermomolina.protos.parser.ast.SurfaceGroup;
import com.guillermomolina.protos.parser.ast.SurfaceLiteral;
import com.guillermomolina.protos.parser.ast.SurfaceMapConstruction;
import com.guillermomolina.protos.parser.ast.SurfaceName;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProtosSourceLayoutViewTest {
    private final ProtosStaticAnalysisCore core =
            new ProtosStaticAnalysisCore();

    @Test
    void buildsOnDemandFromExactSnapshotAndCanonicalSurfaceAst() {
        ProtosDocumentSnapshot snapshot =
                new ProtosDocumentSnapshot(
                        "memory:layout",
                        1L,
                        "(value)\nx => x");

        ProtosSourceLayoutView view =
                core.sourceLayout(snapshot);

        assertSame(snapshot, view.snapshot());
        assertInstanceOf(
                SurfaceGroup.class,
                view.program().expressions().get(0));

        SurfaceClosure closure =
                assertInstanceOf(
                        SurfaceClosure.class,
                        view.program().expressions().get(1));

        assertTrue(closure.expressionBody());
    }

    @Test
    void structuralPathsReuseCanonicalSurfaceNodes() {
        ProtosSourceLayoutView view =
                core.sourceLayout(
                        new ProtosDocumentSnapshot(
                                "memory:paths",
                                2L,
                                "(value)\n"
                                        + "x => x\n"
                                        + "[first, second]\n"
                                        + "%{\"key\": value}"));

        SurfaceGroup group =
                assertInstanceOf(
                        SurfaceGroup.class,
                        view.program().expressions().get(0));
        SurfaceName groupedName =
                assertInstanceOf(
                        SurfaceName.class,
                        group.expression());

        assertEquals(
                "program/expression[0]",
                view.pathOf(group)
                        .orElseThrow()
                        .toString());
        assertEquals(
                "program/expression[0]/expression",
                view.pathOf(groupedName)
                        .orElseThrow()
                        .toString());

        SurfaceClosure closure =
                assertInstanceOf(
                        SurfaceClosure.class,
                        view.program().expressions().get(1));

        assertEquals(
                "program/expression[1]/body",
                view.pathOf(closure.body())
                        .orElseThrow()
                        .toString());

        SurfaceArrayConstruction array =
                assertInstanceOf(
                        SurfaceArrayConstruction.class,
                        view.program().expressions().get(2));

        assertEquals(
                "program/expression[2]/argument[1]/expression",
                view.pathOf(
                                array.arguments()
                                        .get(1)
                                        .expression())
                        .orElseThrow()
                        .toString());

        SurfaceMapConstruction map =
                assertInstanceOf(
                        SurfaceMapConstruction.class,
                        view.program().expressions().get(3));

        assertEquals(
                "program/expression[3]/entry[0]/key",
                view.pathOf(map.entries().get(0).key())
                        .orElseThrow()
                        .toString());
        assertEquals(
                "program/expression[3]/entry[0]/value",
                view.pathOf(map.entries().get(0).value())
                        .orElseThrow()
                        .toString());
    }

    @Test
    void classifiesAndStructurallyAttachesComments() {
        String source =
                "a: 1 // eol\n"
                        + "// own\n"
                        + "b: foo /* embedded */ + bar\n"
                        + "c: foo() /* between\n"
                        + "call */ { body() }";

        ProtosSourceLayoutView view =
                core.sourceLayout(
                        new ProtosDocumentSnapshot(
                                "memory:comments",
                                3L,
                                source));

        assertEquals(
                List.of(
                        ProtosSourceLayoutView.CommentPlacement.END_OF_LINE,
                        ProtosSourceLayoutView.CommentPlacement.OWN_LINE,
                        ProtosSourceLayoutView.CommentPlacement
                                .EMBEDDED_BETWEEN_TOKENS,
                        ProtosSourceLayoutView.CommentPlacement
                                .EMBEDDED_BETWEEN_TOKENS),
                view.commentAttachments()
                        .stream()
                        .map(
                                ProtosSourceLayoutView
                                        .CommentAttachment::placement)
                        .toList());

        assertEquals(
                List.of(
                        ProtosSourceLayoutView.CommentBoundaryKind
                                .SEQUENCE_SEPARATOR,
                        ProtosSourceLayoutView.CommentBoundaryKind
                                .SEQUENCE_SEPARATOR,
                        ProtosSourceLayoutView.CommentBoundaryKind.NONE,
                        ProtosSourceLayoutView.CommentBoundaryKind
                                .TRAILING_CLOSURE),
                view.commentAttachments()
                        .stream()
                        .map(
                                ProtosSourceLayoutView
                                        .CommentAttachment::boundaryKind)
                        .toList());

        assertEquals(
                SequenceSeparatorKind.LOGICAL_NEWLINE,
                view.commentAttachments()
                        .get(0)
                        .sequenceSeparatorKind()
                        .orElseThrow());

        assertEquals(
                SequenceSeparatorKind.LOGICAL_NEWLINE,
                view.commentAttachments()
                        .get(1)
                        .sequenceSeparatorKind()
                        .orElseThrow());

        ProtosSourceLayoutView.CommentAttachment trailing =
                view.commentAttachments().get(3);

        assertTrue(trailing.preceding().isPresent());
        assertTrue(trailing.following().isPresent());
        assertTrue(
                trailing.preceding()
                        .orElseThrow()
                        .toString()
                        .startsWith("program/expression[2]"));
        assertTrue(
                trailing.following()
                        .orElseThrow()
                        .toString()
                        .contains("/argument[0]/expression"));
    }

    @Test
    void commentRelationshipIsIndependentOfSourceOffsets() {
        ProtosSourceLayoutView first =
                core.sourceLayout(
                        new ProtosDocumentSnapshot(
                                "memory:first",
                                4L,
                                "a: 1\n// note\nb: 2"));

        ProtosSourceLayoutView shifted =
                core.sourceLayout(
                        new ProtosDocumentSnapshot(
                                "memory:shifted",
                                5L,
                                "\n\n  a: 1\n// note\nb: 2"));

        ProtosSourceLayoutView.CommentAttachment left =
                first.commentAttachments().get(0);
        ProtosSourceLayoutView.CommentAttachment right =
                shifted.commentAttachments().get(0);

        assertEquals(left.placement(), right.placement());
        assertEquals(
                left.boundaryKind(),
                right.boundaryKind());
        assertEquals(
                left.preceding(),
                right.preceding());
        assertEquals(
                left.following(),
                right.following());
        assertEquals(
                left.sequenceSeparatorKind(),
                right.sequenceSeparatorKind());
    }

    @Test
    void projectsBlankLinePresenceWithoutTreatingCommentLinesAsBlank() {
        ProtosSourceLayoutView.SourcePreservationProjection projection =
                core.sourceLayout(
                                new ProtosDocumentSnapshot(
                                        "memory:blank-lines",
                                        7L,
                                        "a\n"
                                                + "b\n"
                                                + "\n"
                                                + "c\n"
                                                + "   \n"
                                                + "d\n"
                                                + "// note\n"
                                                + "e"))
                        .preservationProjection();

        assertEquals(
                List.of(
                        false,
                        true,
                        true,
                        false),
                projection.sequenceSeparators()
                        .stream()
                        .map(
                                ProtosSourceLayoutView
                                        .SequenceSeparatorProjection
                                        ::blankLine)
                        .toList());
    }

    @Test
    void projectsOwnLineCommentAdjacentBlankLines() {
        String source =
                "a\n"
                        + "\n"
                        + "// before\n"
                        + "b\n"
                        + "// after\n"
                        + "\n"
                        + "c\n"
                        + "\n"
                        + "// both\n"
                        + "\n"
                        + "d";

        ProtosSourceLayoutView.SourcePreservationProjection projection =
                core.sourceLayout(
                                new ProtosDocumentSnapshot(
                                        "memory:comment-blank-lines",
                                        8L,
                                        source))
                        .preservationProjection();

        assertEquals(
                List.of(
                        true,
                        false,
                        true),
                projection.comments()
                        .stream()
                        .map(
                                ProtosSourceLayoutView
                                        .CommentProjection
                                        ::blankLineBefore)
                        .toList());

        assertEquals(
                List.of(
                        false,
                        true,
                        true),
                projection.comments()
                        .stream()
                        .map(
                                ProtosSourceLayoutView
                                        .CommentProjection
                                        ::blankLineAfter)
                        .toList());
    }

    @Test
    void preservationProjectionUsesRawSpellingAndStructuralAuthorities() {
        String source =
                "(café); [1_000]\n"
                        + "%{\"k\": 0xFF}\n"
                        + "x => x\n"
                        + "(y) => y\n"
                        + "foo() /* tail */ { body() }";

        ProtosSourceLayoutView.SourcePreservationProjection projection =
                core.sourceLayout(
                                new ProtosDocumentSnapshot(
                                        "memory:projection",
                                        6L,
                                        source))
                        .preservationProjection();

        List<String> rawTokens =
                projection.tokens()
                        .stream()
                        .map(
                                ProtosSourceLayoutView
                                        .TokenSpelling::rawText)
                        .toList();

        assertTrue(rawTokens.contains("café"));
        assertTrue(rawTokens.contains("1_000"));
        assertTrue(rawTokens.contains("\"k\""));
        assertTrue(rawTokens.contains("0xFF"));

        assertTrue(
                projection.structuralForms()
                        .stream()
                        .anyMatch(
                                form ->
                                        form.kind()
                                                == ProtosSourceLayoutView
                                                        .StructuralFormKind
                                                        .GROUP));

        assertTrue(
                projection.structuralForms()
                        .stream()
                        .anyMatch(
                                form ->
                                        form.kind()
                                                == ProtosSourceLayoutView
                                                        .StructuralFormKind
                                                        .ARRAY_CONSTRUCTION));

        assertTrue(
                projection.structuralForms()
                        .stream()
                        .anyMatch(
                                form ->
                                        form.kind()
                                                == ProtosSourceLayoutView
                                                        .StructuralFormKind
                                                        .MAP_CONSTRUCTION));

        assertEquals(
                List.of(
                        ProtosParserSourceFacts
                                .SingleParameterClosureForm.BARE,
                        ProtosParserSourceFacts
                                .SingleParameterClosureForm.PARENTHESIZED),
                projection.closureForms()
                        .stream()
                        .map(
                                ProtosSourceLayoutView
                                        .ClosureFormProjection::form)
                        .toList());

        assertEquals(
                1,
                projection.trailingClosures().size());

        assertEquals(
                "/* tail */",
                projection.comments().get(0).rawText());

        assertEquals(
                ProtosSourceLayoutView.CommentBoundaryKind.TRAILING_CLOSURE,
                projection.comments().get(0).boundaryKind());

        assertTrue(
                projection.sequenceSeparators()
                        .stream()
                        .anyMatch(
                                separator ->
                                        separator.kind()
                                                == SequenceSeparatorKind
                                                        .SEMICOLON));

        assertTrue(
                projection.sequenceSeparators()
                        .stream()
                        .anyMatch(
                                separator ->
                                        separator.kind()
                                                == SequenceSeparatorKind
                                                        .LOGICAL_NEWLINE));
    }

    @Test
    void preservationProjectionKeepsTripleDoubleRawWhitespace() {
        String source =
                "\"\"\"\r\n"
                        + "\talpha\r\n"
                        + "  \r\n"
                        + "\tbeta\r\n"
                        + "\t\"\"\"";

        ProtosSourceLayoutView.SourcePreservationProjection projection =
                core.sourceLayout(
                                new ProtosDocumentSnapshot(
                                        "memory:projection-triple",
                                        7L,
                                        source))
                        .preservationProjection();

        assertEquals(1, projection.tokens().size());
        assertEquals(
                source,
                projection.tokens().get(0).rawText());
    }

    @Test
    void preservationProjectionCarriesNoSourceOffsets() {
        ProtosSourceLayoutView.SourcePreservationProjection projection =
                core.sourceLayout(
                                new ProtosDocumentSnapshot(
                                        "memory:no-offsets",
                                        8L,
                                        "a; b\n// note\nc"))
                        .preservationProjection();

        assertEquals(
                List.of(
                        SequenceSeparatorKind.SEMICOLON,
                        SequenceSeparatorKind.LOGICAL_NEWLINE),
                projection.sequenceSeparators()
                        .stream()
                        .map(
                                ProtosSourceLayoutView
                                        .SequenceSeparatorProjection::kind)
                        .toList());

        assertTrue(
                projection.comments()
                        .stream()
                        .allMatch(
                                comment ->
                                        comment.preceding().isPresent()
                                                || comment.following()
                                                        .isPresent()));
    }

    @Test
    void exactSnapshotRemainsRawLiteralSpellingAuthority() {
        String source =
                "'a'\n"
                        + "\"a\"\n"
                        + "\"\\n\"\n"
                        + "\"\\u{0A}\"\n"
                        + "1000\n"
                        + "1_000\n"
                        + "0xff\n"
                        + "0xFF\n"
                        + "2e3\n"
                        + "2E3";

        ProtosSourceLayoutView view =
                core.sourceLayout(
                        new ProtosDocumentSnapshot(
                                "memory:raw",
                                2L,
                                source));

        assertEquals(
                List.of(
                        "'a'",
                        "\"a\"",
                        "\"\\n\"",
                        "\"\\u{0A}\"",
                        "1000",
                        "1_000",
                        "0xff",
                        "0xFF",
                        "2e3",
                        "2E3"),
                view.program().expressions().stream()
                        .map(
                                expression ->
                                        view.sourceText(
                                                assertInstanceOf(
                                                                SurfaceLiteral.class,
                                                                expression)
                                                        .span()))
                        .toList());
    }

    @Test
    void carriesParserSourceFactsWithoutChangingOrdinaryParsePath() {
        ProtosSourceLayoutView view =
                core.sourceLayout(
                        new ProtosDocumentSnapshot(
                                "memory:facts",
                                3L,
                                "a; b\nc"));

        assertEquals(
                List.of(
                        SequenceSeparatorKind.SEMICOLON,
                        SequenceSeparatorKind.LOGICAL_NEWLINE),
                view.sourceFacts()
                        .sequenceSeparators()
                        .stream()
                        .map(fact -> fact.kind())
                        .toList());
    }
}
