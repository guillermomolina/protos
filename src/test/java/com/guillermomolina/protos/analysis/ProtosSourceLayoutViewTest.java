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
import com.guillermomolina.protos.parser.ast.SurfaceCall;
import com.guillermomolina.protos.parser.ast.SurfaceClosure;
import com.guillermomolina.protos.parser.ast.SurfaceContextualIf;
import com.guillermomolina.protos.parser.ast.SurfaceContextualUntil;
import com.guillermomolina.protos.parser.ast.SurfaceContextualWhile;
import com.guillermomolina.protos.parser.ast.SurfaceGroup;
import com.guillermomolina.protos.parser.ast.SurfaceLiteral;
import com.guillermomolina.protos.parser.ast.SurfaceMapConstruction;
import com.guillermomolina.protos.parser.ast.SurfaceName;
import com.guillermomolina.protos.source.SourceSpan;
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
    void bug022ClassifiesLeadingBlockCommentWithoutPredecessor() {
        ProtosSourceLayoutView view =
                core.sourceLayout(
                        new ProtosDocumentSnapshot(
                                "memory:bug022-leading",
                                1L,
                                "/* probe */\nx: 10\n"));

        assertEquals(1, view.commentAttachments().size());

        ProtosSourceLayoutView.CommentAttachment comment =
                view.commentAttachments().get(0);

        assertEquals(
                ProtosSourceLayoutView.CommentPlacement.OWN_LINE,
                comment.placement());
        assertEquals(
                ProtosSourceLayoutView.CommentBoundaryKind.DOCUMENT_PREFIX,
                comment.boundaryKind());
        assertTrue(comment.preceding().isEmpty());
        assertTrue(comment.following().isPresent());
    }

    @Test
    void bug022DistinguishesDocumentSuffixFromInternalComment() {
        ProtosSourceLayoutView trailing =
                core.sourceLayout(
                        new ProtosDocumentSnapshot(
                                "memory:bug022-suffix",
                                2L,
                                "x: 10\n/* footer */"));

        assertEquals(1, trailing.commentAttachments().size());

        ProtosSourceLayoutView.CommentAttachment footer =
                trailing.commentAttachments().get(0);

        assertEquals(
                ProtosSourceLayoutView.CommentBoundaryKind.DOCUMENT_SUFFIX,
                footer.boundaryKind());
        assertEquals(
                ProtosSourceLayoutView.CommentPlacement.OWN_LINE,
                footer.placement());
        assertTrue(footer.preceding().isPresent());
        assertTrue(footer.following().isEmpty());

        ProtosSourceLayoutView internal =
                core.sourceLayout(
                        new ProtosDocumentSnapshot(
                                "memory:bug022-internal",
                                3L,
                                "{ /* internal */ x: 1 }"));

        assertEquals(1, internal.commentAttachments().size());

        ProtosSourceLayoutView.CommentBoundaryKind boundary =
                internal.commentAttachments().get(0).boundaryKind();

        assertTrue(
                boundary !=
                        ProtosSourceLayoutView.CommentBoundaryKind
                                .DOCUMENT_PREFIX);
        assertTrue(
                boundary !=
                        ProtosSourceLayoutView.CommentBoundaryKind
                                .DOCUMENT_SUFFIX);
    }

    @Test
    void bug024KeepsPhysicalBlankLineFactsIndependentOfCommentAttachment() {
        ProtosSourceLayoutView.SourcePreservationProjection projection =
                core.sourceLayout(
                                new ProtosDocumentSnapshot(
                                        "memory:bug024-eol-after-blank",
                                        1L,
                                        "a: 1\n"
                                                + "\n"
                                                + "b: 2 // note\n"
                                                + "c: 3\n"))
                        .preservationProjection();

        assertEquals(1, projection.comments().size());

        ProtosSourceLayoutView.CommentProjection note =
                projection.comments().get(0);

        assertEquals("// note", note.rawText());
        assertEquals(
                ProtosSourceLayoutView.CommentPlacement.END_OF_LINE,
                note.placement());
        assertEquals(
                ProtosSourceLayoutView.CommentBoundaryKind.SEQUENCE_SEPARATOR,
                note.boundaryKind());
        assertEquals(
                SequenceSeparatorKind.LOGICAL_NEWLINE,
                note.sequenceSeparatorKind().orElseThrow());
        assertEquals(true, note.blankLineBefore());
        assertEquals(false, note.blankLineAfter());

        List<ProtosSourceLayoutView.SequenceSeparatorProjection>
                separators = projection.sequenceSeparators();

        assertEquals(2, separators.size());
        assertEquals(true, separators.get(0).blankLine());
        assertEquals(false, separators.get(1).blankLine());
        assertEquals(note.preceding().orElseThrow(),
                separators.get(1).preceding());
        assertEquals(note.following().orElseThrow(),
                separators.get(1).following());
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
    void contextualWhileHasStablePathsAndRealSpans() {
        String source = "while(n > 1) { n = n - 1 }\nfoo() { body() }";
        ProtosSourceLayoutView view =
                core.sourceLayout(
                        new ProtosDocumentSnapshot(
                                "memory:contextual-while",
                                7L,
                                source));

        SurfaceContextualWhile loop =
                assertInstanceOf(
                        SurfaceContextualWhile.class,
                        view.program().expressions().get(0));

        assertEquals(
                "program/expression[0]",
                view.pathOf(loop).orElseThrow().toString());
        assertEquals(
                "program/expression[0]/condition",
                view.pathOf(loop.condition()).orElseThrow().toString());
        assertEquals(
                "program/expression[0]/body",
                view.pathOf(loop.body()).orElseThrow().toString());
        assertEquals(
                "while(n > 1) { n = n - 1 }",
                view.sourceText(loop.span()));
        assertEquals("n > 1", view.sourceText(loop.condition().span()));
        assertEquals(new SourceSpan(13, 26), loop.body().span());

        ProtosSourceLayoutView.SourcePreservationProjection projection =
                view.preservationProjection();

        SurfaceCall call =
                assertInstanceOf(
                        SurfaceCall.class,
                        view.program().expressions().get(1));
        assertEquals(2, projection.trailingClosures().size());
        assertEquals(
                view.pathOf(loop).orElseThrow(),
                projection.trailingClosures().get(0).call());
        assertEquals(
                view.pathOf(loop.body()).orElseThrow(),
                projection.trailingClosures().get(0).closure());
        assertEquals(
                view.pathOf(call).orElseThrow(),
                projection.trailingClosures().get(1).call());
    }

    @Test
    void contextualWhileBlockCommentProjectsOneTrailingClosureBoundary() {
        assertContextualWhileCommentBoundary(
                "while(n > 0) /* comment */ { body() }",
                "/* comment */");
    }

    @Test
    void contextualWhileMultilineBlockCommentKeepsTrailingClosureBoundary() {
        assertContextualWhileCommentBoundary(
                "while(n > 0) /* one\n two */ { body() }",
                "/* one\n two */");
    }

    @Test
    void contextualWhileCommentBeforeLeftParenthesisAttachesToCondition() {
        assertContextualWhileHeaderComments(
                "while /* before */ (n > 0) { n = n - 1 }",
                List.of("/* before */"),
                List.of(
                        ProtosSourceLayoutView.CommentBoundaryKind
                                .CONTEXTUAL_WHILE_BEFORE_LPAREN));
    }

    @Test
    void contextualWhileCommentAfterLeftParenthesisAttachesToCondition() {
        assertContextualWhileHeaderComments(
                "while(/* after */ n > 0) { n = n - 1 }",
                List.of("/* after */"),
                List.of(
                        ProtosSourceLayoutView.CommentBoundaryKind
                                .CONTEXTUAL_WHILE_AFTER_LPAREN));
    }

    @Test
    void contextualWhileCommentsOnBothSidesOfLeftParenthesis() {
        assertContextualWhileHeaderComments(
                "while /* before */ (/* after */ n > 0) { n = n - 1 }",
                List.of("/* before */", "/* after */"),
                List.of(
                        ProtosSourceLayoutView.CommentBoundaryKind
                                .CONTEXTUAL_WHILE_BEFORE_LPAREN,
                        ProtosSourceLayoutView.CommentBoundaryKind
                                .CONTEXTUAL_WHILE_AFTER_LPAREN));
    }

    @Test
    void contextualWhileMultilineCommentBeforeLeftParenthesis() {
        assertContextualWhileHeaderComments(
                "while /* one\n two */ (n > 0) { n = n - 1 }",
                List.of("/* one\n two */"),
                List.of(
                        ProtosSourceLayoutView.CommentBoundaryKind
                                .CONTEXTUAL_WHILE_BEFORE_LPAREN));
    }

    @Test
    void commentInsideContextualWhileConditionIsNotAHeaderBoundary() {
        String source = "while(n /* inner */ > 0) { n = n - 1 }";
        ProtosSourceLayoutView view =
                core.sourceLayout(
                        new ProtosDocumentSnapshot(
                                "memory:contextual-while-inner",
                                9L,
                                source));

        assertEquals(1, view.commentAttachments().size());
        assertEquals(
                ProtosSourceLayoutView.CommentBoundaryKind.NONE,
                view.commentAttachments().get(0).boundaryKind());
    }

    @Test
    void contextualIfHasStablePathsAndProjectsBothBranchOrigins() {
        String source = "if(a) { b } else { c }\nif(d) { e }";
        ProtosSourceLayoutView view =
                core.sourceLayout(
                        new ProtosDocumentSnapshot(
                                "memory:contextual-if",
                                11L,
                                source));

        SurfaceContextualIf paired =
                assertInstanceOf(
                        SurfaceContextualIf.class,
                        view.program().expressions().get(0));
        SurfaceContextualIf single =
                assertInstanceOf(
                        SurfaceContextualIf.class,
                        view.program().expressions().get(1));

        assertEquals(
                "program/expression[0]",
                view.pathOf(paired).orElseThrow().toString());
        assertEquals(
                "program/expression[0]/condition",
                view.pathOf(paired.condition()).orElseThrow().toString());
        assertEquals(
                "program/expression[0]/trueBranch",
                view.pathOf(paired.trueBranch()).orElseThrow().toString());
        assertEquals(
                "program/expression[0]/falseBranch",
                view.pathOf(paired.falseBranch()).orElseThrow().toString());
        assertEquals(
                "program/expression[1]/trueBranch",
                view.pathOf(single.trueBranch()).orElseThrow().toString());
        assertEquals("if(a) { b } else { c }", view.sourceText(paired.span()));
        assertEquals("{ c }", view.sourceText(paired.falseBranch().span()));

        ProtosSourceLayoutView.SourcePreservationProjection projection =
                view.preservationProjection();
        assertEquals(2, projection.trailingClosures().size());
        assertEquals(
                view.pathOf(paired).orElseThrow(),
                projection.trailingClosures().get(0).call());
        assertEquals(
                view.pathOf(paired.trueBranch()).orElseThrow(),
                projection.trailingClosures().get(0).closure());
        assertEquals(
                view.pathOf(single).orElseThrow(),
                projection.trailingClosures().get(1).call());

        assertEquals(1, projection.contextualElses().size());
        ProtosSourceLayoutView.ContextualElseProjection contextualElse =
                projection.contextualElses().get(0);
        assertEquals(view.pathOf(paired).orElseThrow(), contextualElse.conditional());
        assertEquals(
                view.pathOf(paired.falseBranch()).orElseThrow(),
                contextualElse.falseBranch());
        assertEquals(7, contextualElse.elseToken());
        assertEquals(
                "else",
                projection.tokens().get(contextualElse.elseToken()).rawText());
    }

    @Test
    void contextualIfCommentsAttachToFiveDistinctBoundaries() {
        String source =
                "if /* a */ (/* b */ x) /* c */ { y } /* d */ else /* e */ { z }";
        ProtosSourceLayoutView view =
                core.sourceLayout(
                        new ProtosDocumentSnapshot(
                                "memory:contextual-if-comments",
                                12L,
                                source));

        SurfaceContextualIf conditional =
                assertInstanceOf(
                        SurfaceContextualIf.class,
                        view.program().expressions().get(0));
        String conditionalPath = view.pathOf(conditional).orElseThrow().toString();
        String conditionPath =
                view.pathOf(conditional.condition()).orElseThrow().toString();
        String truePath =
                view.pathOf(conditional.trueBranch()).orElseThrow().toString();
        String falsePath =
                view.pathOf(conditional.falseBranch()).orElseThrow().toString();

        List<String> texts =
                List.of("/* a */", "/* b */", "/* c */", "/* d */", "/* e */");
        List<ProtosSourceLayoutView.CommentBoundaryKind> kinds = List.of(
                ProtosSourceLayoutView.CommentBoundaryKind.CONTEXTUAL_IF_BEFORE_LPAREN,
                ProtosSourceLayoutView.CommentBoundaryKind.CONTEXTUAL_IF_AFTER_LPAREN,
                ProtosSourceLayoutView.CommentBoundaryKind.TRAILING_CLOSURE,
                ProtosSourceLayoutView.CommentBoundaryKind.CONTEXTUAL_IF_BEFORE_ELSE,
                ProtosSourceLayoutView.CommentBoundaryKind.CONTEXTUAL_IF_AFTER_ELSE);
        List<String> following =
                List.of(conditionPath, conditionPath, truePath, falsePath, falsePath);

        List<ProtosSourceLayoutView.CommentAttachment> attachments =
                view.commentAttachments();
        assertEquals(texts.size(), attachments.size());
        for (int index = 0; index < attachments.size(); index++) {
            ProtosSourceLayoutView.CommentAttachment attachment =
                    attachments.get(index);
            int start = source.indexOf(texts.get(index));
            assertEquals(
                    new SourceSpan(start, start + texts.get(index).length()),
                    attachment.comment().span());
            assertEquals(kinds.get(index), attachment.boundaryKind());
            assertEquals(
                    ProtosSourceLayoutView.CommentPlacement.EMBEDDED_BETWEEN_TOKENS,
                    attachment.placement());
            assertEquals(
                    conditionalPath,
                    attachment.preceding().orElseThrow().toString());
            assertEquals(
                    following.get(index),
                    attachment.following().orElseThrow().toString());
        }

        List<ProtosSourceLayoutView.CommentProjection> projected =
                view.preservationProjection().comments();
        assertEquals(texts, projected.stream()
                .map(ProtosSourceLayoutView.CommentProjection::rawText)
                .toList());
        assertEquals(kinds, projected.stream()
                .map(ProtosSourceLayoutView.CommentProjection::boundaryKind)
                .toList());
    }

    @Test
    void nestedContextualIfElsesProjectDistinctOwners() {
        String source = "if(a) { if(b) { 1 } else { 2 } } else { 3 }";
        ProtosSourceLayoutView view =
                core.sourceLayout(
                        new ProtosDocumentSnapshot(
                                "memory:contextual-if-nested",
                                13L,
                                source));

        SurfaceContextualIf outer =
                assertInstanceOf(
                        SurfaceContextualIf.class,
                        view.program().expressions().get(0));
        SurfaceContextualIf inner =
                assertInstanceOf(
                        SurfaceContextualIf.class,
                        outer.trueBranch().body().expressions().get(0));

        List<ProtosSourceLayoutView.ContextualElseProjection> elses =
                view.preservationProjection().contextualElses();
        assertEquals(2, elses.size());
        assertEquals(view.pathOf(inner).orElseThrow(), elses.get(0).conditional());
        assertEquals(
                view.pathOf(inner.falseBranch()).orElseThrow(),
                elses.get(0).falseBranch());
        assertEquals(view.pathOf(outer).orElseThrow(), elses.get(1).conditional());
        assertEquals(
                view.pathOf(outer.falseBranch()).orElseThrow(),
                elses.get(1).falseBranch());
        assertEquals(
                "program/expression[0]/trueBranch/body/expression[0]",
                view.pathOf(inner).orElseThrow().toString());
    }

    @Test
    void contextualUntilHasStablePathsAndRealSpans() {
        String source = "{ n = n - 1 } until(n == 0)\nfoo() { body() }";
        ProtosSourceLayoutView view =
                core.sourceLayout(
                        new ProtosDocumentSnapshot(
                                "memory:contextual-until",
                                14L,
                                source));

        SurfaceContextualUntil loop =
                assertInstanceOf(
                        SurfaceContextualUntil.class,
                        view.program().expressions().get(0));

        assertEquals(
                "program/expression[0]",
                view.pathOf(loop).orElseThrow().toString());
        assertEquals(
                "program/expression[0]/body",
                view.pathOf(loop.body()).orElseThrow().toString());
        assertEquals(
                "program/expression[0]/condition",
                view.pathOf(loop.condition()).orElseThrow().toString());
        assertEquals(
                "{ n = n - 1 } until(n == 0)",
                view.sourceText(loop.span()));
        assertEquals("{ n = n - 1 }", view.sourceText(loop.body().span()));
        assertEquals("until", view.sourceText(loop.untilSpan()));
        assertEquals("n == 0", view.sourceText(loop.condition().span()));

        ProtosSourceLayoutView.SourcePreservationProjection projection =
                view.preservationProjection();
        SurfaceCall call =
                assertInstanceOf(
                        SurfaceCall.class,
                        view.program().expressions().get(1));
        assertEquals(1, projection.trailingClosures().size());
        assertEquals(
                view.pathOf(call).orElseThrow(),
                projection.trailingClosures().get(0).call());
    }

    @Test
    void contextualUntilCommentsAttachToThreeDistinctSuffixBoundaries() {
        String source = "{ y } /* a */ until /* b */ (/* c */ x)";
        ProtosSourceLayoutView view =
                core.sourceLayout(
                        new ProtosDocumentSnapshot(
                                "memory:contextual-until-comments",
                                15L,
                                source));

        SurfaceContextualUntil loop =
                assertInstanceOf(
                        SurfaceContextualUntil.class,
                        view.program().expressions().get(0));
        String loopPath = view.pathOf(loop).orElseThrow().toString();
        String conditionPath =
                view.pathOf(loop.condition()).orElseThrow().toString();

        List<String> texts = List.of("/* a */", "/* b */", "/* c */");
        List<ProtosSourceLayoutView.CommentBoundaryKind> kinds = List.of(
                ProtosSourceLayoutView.CommentBoundaryKind.CONTEXTUAL_UNTIL_BEFORE_KEYWORD,
                ProtosSourceLayoutView.CommentBoundaryKind.CONTEXTUAL_UNTIL_BEFORE_LPAREN,
                ProtosSourceLayoutView.CommentBoundaryKind.CONTEXTUAL_UNTIL_AFTER_LPAREN);

        List<ProtosSourceLayoutView.CommentAttachment> attachments =
                view.commentAttachments();
        assertEquals(texts.size(), attachments.size());
        for (int index = 0; index < attachments.size(); index++) {
            ProtosSourceLayoutView.CommentAttachment attachment =
                    attachments.get(index);
            int start = source.indexOf(texts.get(index));
            assertEquals(
                    new SourceSpan(start, start + texts.get(index).length()),
                    attachment.comment().span());
            assertEquals(kinds.get(index), attachment.boundaryKind());
            assertEquals(
                    ProtosSourceLayoutView.CommentPlacement.EMBEDDED_BETWEEN_TOKENS,
                    attachment.placement());
            assertEquals(
                    loopPath,
                    attachment.preceding().orElseThrow().toString());
            assertEquals(
                    conditionPath,
                    attachment.following().orElseThrow().toString());
        }

        assertEquals(kinds, view.preservationProjection().comments().stream()
                .map(ProtosSourceLayoutView.CommentProjection::boundaryKind)
                .toList());
    }

    @Test
    void multilineBlockCommentBeforeUntilKeepsTheSuffixBoundary() {
        String source = "{ y } /* one\n two */ until(x)";
        ProtosSourceLayoutView view =
                core.sourceLayout(
                        new ProtosDocumentSnapshot(
                                "memory:contextual-until-multiline",
                                16L,
                                source));

        assertInstanceOf(
                SurfaceContextualUntil.class,
                view.program().expressions().get(0));
        assertEquals(1, view.commentAttachments().size());
        assertEquals(
                ProtosSourceLayoutView.CommentBoundaryKind.CONTEXTUAL_UNTIL_BEFORE_KEYWORD,
                view.commentAttachments().get(0).boundaryKind());
    }

    @Test
    void commentsInsideContextualUntilBodyAndConditionAreNotSuffixBoundaries() {
        String source = "{ y /* body */ } until(x /* inner */ > 0)";
        ProtosSourceLayoutView view =
                core.sourceLayout(
                        new ProtosDocumentSnapshot(
                                "memory:contextual-until-inner",
                                17L,
                                source));

        assertEquals(2, view.commentAttachments().size());
        for (ProtosSourceLayoutView.CommentAttachment attachment
                : view.commentAttachments()) {
            assertEquals(
                    ProtosSourceLayoutView.CommentBoundaryKind.NONE,
                    attachment.boundaryKind());
        }
    }

    private void assertContextualWhileHeaderComments(
            String source,
            List<String> commentTexts,
            List<ProtosSourceLayoutView.CommentBoundaryKind> kinds) {
        ProtosSourceLayoutView view =
                core.sourceLayout(
                        new ProtosDocumentSnapshot(
                                "memory:contextual-while-header",
                                10L,
                                source));

        SurfaceContextualWhile loop =
                assertInstanceOf(
                        SurfaceContextualWhile.class,
                        view.program().expressions().get(0));
        String loopPath = view.pathOf(loop).orElseThrow().toString();
        String conditionPath =
                view.pathOf(loop.condition()).orElseThrow().toString();
        assertEquals("program/expression[0]", loopPath);
        assertEquals("program/expression[0]/condition", conditionPath);

        List<ProtosSourceLayoutView.CommentAttachment> attachments =
                view.commentAttachments();
        assertEquals(commentTexts.size(), attachments.size());
        for (int index = 0; index < attachments.size(); index++) {
            ProtosSourceLayoutView.CommentAttachment attachment =
                    attachments.get(index);
            String text = commentTexts.get(index);
            int start = source.indexOf(text);
            assertEquals(kinds.get(index), attachment.boundaryKind());
            assertEquals(
                    new SourceSpan(start, start + text.length()),
                    attachment.comment().span());
            assertEquals(
                    loopPath,
                    attachment.preceding().orElseThrow().toString());
            assertEquals(
                    conditionPath,
                    attachment.following().orElseThrow().toString());
        }

        List<ProtosSourceLayoutView.CommentProjection> projected =
                view.preservationProjection().comments();
        assertEquals(commentTexts, projected.stream()
                .map(ProtosSourceLayoutView.CommentProjection::rawText)
                .toList());
        assertEquals(kinds, projected.stream()
                .map(ProtosSourceLayoutView.CommentProjection::boundaryKind)
                .toList());
    }

    private void assertContextualWhileCommentBoundary(
            String source,
            String commentText) {
        ProtosSourceLayoutView view =
                core.sourceLayout(
                        new ProtosDocumentSnapshot(
                                "memory:contextual-while-comment",
                                8L,
                                source));

        SurfaceContextualWhile loop =
                assertInstanceOf(
                        SurfaceContextualWhile.class,
                        view.program().expressions().get(0));
        String loopPath = view.pathOf(loop).orElseThrow().toString();
        String bodyPath = view.pathOf(loop.body()).orElseThrow().toString();

        List<ProtosSourceLayoutView.CommentAttachment> attachments =
                view.commentAttachments();
        assertEquals(1, attachments.size());
        ProtosSourceLayoutView.CommentAttachment attachment =
                attachments.get(0);
        assertEquals(
                ProtosSourceLayoutView.CommentBoundaryKind.TRAILING_CLOSURE,
                attachment.boundaryKind());
        assertEquals(
                loopPath,
                attachment.preceding().orElseThrow().toString());
        assertEquals(
                bodyPath,
                attachment.following().orElseThrow().toString());
        int start = source.indexOf("/*");
        assertEquals(
                new SourceSpan(start, start + commentText.length()),
                attachment.comment().span());

        ProtosSourceLayoutView.SourcePreservationProjection projection =
                view.preservationProjection();
        assertEquals(1, projection.trailingClosures().size());
        assertEquals(
                loopPath,
                projection.trailingClosures().get(0).call().toString());
        assertEquals(
                bodyPath,
                projection.trailingClosures().get(0).closure().toString());
        assertEquals(
                List.of(ProtosSourceLayoutView.CommentBoundaryKind.TRAILING_CLOSURE),
                projection.comments().stream()
                        .map(ProtosSourceLayoutView.CommentProjection::boundaryKind)
                        .toList());
        assertEquals(commentText, projection.comments().get(0).rawText());
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
