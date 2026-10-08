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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.analysis.ProtosStaticSignatureHelpResult.Parameter;
import com.guillermomolina.protos.source.SourceSpan;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

/**
 * LM010-C static signature help. In every source below, {@code |} marks the
 * cursor and is not part of the analyzed snapshot.
 */
class ProtosStaticSignatureHelpTest {

    private final ProtosStaticAnalysisCore core = new ProtosStaticAnalysisCore();

    @Test
    void directLiteralClosureProjectsExactSignatureAndCallIdentity() {
        String marked = "((first, second) => first)(1, 2|)";
        String source = marked.replace("|", "");
        ProtosDocumentSnapshot snapshot = snapshot(source);

        ProtosStaticSignatureHelpResult result =
                core.signatureHelp(snapshot, marked.indexOf('|')).orElseThrow();

        assertEquals(snapshot, result.snapshot());
        assertEquals(span(source, "(first, second) => first"), result.calleeSpan());
        assertEquals(source.indexOf(")(") + 1, result.argumentListStart());
        assertEquals("(first, second)", result.label());
        assertEquals(
                List.of(
                        new Parameter("first", "first", false, false, span(source, "first")),
                        new Parameter("second", "second", false, false, span(source, "second"))),
                result.parameters());
        assertEquals(1, result.activeArgument());
        assertEquals(OptionalInt.of(1), result.activeParameter());
        assertEquals(result, core.signatureHelp(snapshot, marked.indexOf('|')).orElseThrow());
    }

    @Test
    void groupingWrappersAndParameterlessClosuresAreProven() {
        ProtosStaticSignatureHelpResult grouped = help("(((x) => x))(|1)").orElseThrow();
        assertEquals("(x)", grouped.label());
        assertEquals(0, grouped.activeArgument());
        assertEquals(OptionalInt.of(0), grouped.activeParameter());

        ProtosStaticSignatureHelpResult empty = help("(() => 1)(|)").orElseThrow();
        assertEquals("()", empty.label());
        assertEquals(List.of(), empty.parameters());
        assertEquals(0, empty.activeArgument());
        assertEquals(OptionalInt.empty(), empty.activeParameter());
    }

    @Test
    void defaultAndRestParametersKeepOrderAndSourceText() {
        String marked = "((first, second = 2 + 1, ...rest) => first)(|)";
        String source = marked.replace("|", "");
        ProtosStaticSignatureHelpResult result = help(marked).orElseThrow();

        assertEquals("(first, second = 2 + 1, ...rest)", result.label());
        assertEquals(
                List.of(
                        new Parameter("first", "first", false, false, span(source, "first")),
                        new Parameter("second", "second = 2 + 1", true, false,
                                span(source, "second = 2 + 1")),
                        new Parameter("rest", "...rest", false, true, span(source, "...rest"))),
                result.parameters());
        assertEquals(OptionalInt.of(0), result.activeParameter());

        assertActive("((first, second = 2) => first)(1, |)", 1, OptionalInt.of(1));
        assertActive("((first, ...rest) => first)(1, |2)", 1, OptionalInt.of(1));
        assertActive("((first, ...rest) => first)(1, 2, 3|)", 2, OptionalInt.of(1));
        assertActive("((...rest) => rest)(|)", 0, OptionalInt.of(0));
    }

    @Test
    void excessArgumentsWithoutRestAreNotClampedToTheLastParameter() {
        assertActive("((a) => a)(1, 2|)", 1, OptionalInt.empty());
        assertActive("(() => 1)(|1)", 0, OptionalInt.empty());
    }

    @Test
    void enumeratedCursorPositionsSelectTheActiveArgument() {
        assertActive("((a, b) => a)(|)", 0, OptionalInt.of(0));
        assertActive("((a, b) => a)(1, |)", 1, OptionalInt.of(1));
        assertActive("((a, b) => a)(1, 2|)", 1, OptionalInt.of(1));
        assertActive("((a, b) => a)(1, |2)", 1, OptionalInt.of(1));
        assertActive("((a, b) => a)(1|, 2)", 0, OptionalInt.of(0));
        assertActive("((a, b) => a)([1, 2], |)", 1, OptionalInt.of(1));
        assertActive("((a, b) => a)(...values, |)", 1, OptionalInt.empty());

        String nested = "((a, b) => a)(1, ((x) => x)(|))";
        ProtosStaticSignatureHelpResult inner = help(nested).orElseThrow();
        assertEquals("(x)", inner.label());
        assertEquals(span(nested.replace("|", ""), "(x) => x"), inner.calleeSpan());
        assertEquals(0, inner.activeArgument());
        assertEquals(OptionalInt.of(0), inner.activeParameter());
    }

    @Test
    void commasInsideNestedStructuresStringsAndCommentsAreNotSeparators() {
        assertActive("((a, b) => a)((x, y) => x, |2)", 1, OptionalInt.of(1));
        assertActive("((a, b) => a)({ x: g(1, 2) }, |2)", 1, OptionalInt.of(1));
        assertActive("((a, b) => a)(\"x, (y\", /* , ) */ |2)", 1, OptionalInt.of(1));
        assertActive("((a, b) => a)(\n  1, // c, d)\n  |2\n)", 1, OptionalInt.of(1));
        assertActive("((a, b) => a)(1, \"x, |y\")", 1, OptionalInt.of(1));
        assertActive("((a, b) => a)(\n  1,\n\n  |\n)", 1, OptionalInt.of(1));
    }

    @Test
    void cursorOutsideTheArgumentListOrInsideOtherStructuresHasNoSignature() {
        assertTrue(help("((a, b) => a)|(1)").isEmpty());
        assertTrue(help("((a, b) => a)(1)|").isEmpty());
        assertTrue(help("((a, b|) => a)(1)").isEmpty());
        assertTrue(help("((a, b) => a)(1, (|2))").isEmpty());
        assertTrue(help("((a, b) => a)([1, |2])").isEmpty());
        assertTrue(help("((a, b) => a)(1 /* | */, 2)").isEmpty());
        assertTrue(help("((a, b) => a)(1, 2) // |").isEmpty());
        assertTrue(help("|((a, b) => a)(1)").isEmpty());
    }

    @Test
    void spreadsNeverReceiveAFalseParameterCorrespondence() {
        assertActive("((a, b) => a)(...val|ues)", 0, OptionalInt.empty());
        assertActive("((a, b) => a)(...values, 2|)", 1, OptionalInt.empty());
        assertActive("((a, ...rest) => a)(...values, |2)", 1, OptionalInt.empty());
        assertActive("((a, b) => a)(1|, ...values)", 0, OptionalInt.of(0));
    }

    @Test
    void callsWithoutAProvenLiteralCalleeHaveNoSignature() {
        assertTrue(help("f: (first, second) => first\nf(1, |2)").isEmpty());
        assertTrue(help("f: (x) => x\nf = (x, y) => y\nf(1, |2)").isEmpty());
        assertTrue(help("invoke: (f) => f(|1)").isEmpty());
        assertTrue(help("object.run(|1)").isEmpty());
        assertTrue(help("super.run(|1)").isEmpty());
        assertTrue(help("(factory())(|1)").isEmpty());
        assertTrue(help("((x) => (y) => y)(1)(|2)").isEmpty());
        // The inner call has no proven callee; the outer signature is not recovered.
        assertTrue(help("((a, b) => a)(1, g(|))").isEmpty());
        assertTrue(help("((a, b) => a)(1, g(|").isEmpty());
    }

    @Test
    void incompleteArgumentListsAreRecoveredOnlyLocally() {
        assertActive("((a, b) => a)(|", 0, OptionalInt.of(0));
        assertActive("((a, b) => a)(1, |", 1, OptionalInt.of(1));
        assertActive("((a, b) => a)(1, 2|", 1, OptionalInt.of(1));
        assertActive("((a, b) => a)(1,\n  |", 1, OptionalInt.of(1));
        assertActive("((a, b) => a)(1, |\n\n// note\n", 1, OptionalInt.of(1));
        assertActive("values: [\n  ((a, b) => a)(1, |", 1, OptionalInt.of(1));
        assertActive("((a, b) => a)(1, ((x) => x)(|", 0, OptionalInt.of(0));
        assertActive("((a, b) => a)(1, |)\nnext: 2", 1, OptionalInt.of(1));
        assertActive("((a, b, c) => a)(1, |, 3)", 1, OptionalInt.of(1));
        assertActive("((a, b) => a)(|, 2)", 0, OptionalInt.of(0));
    }

    @Test
    void unrecoverableOrAmbiguousIncompleteListsHaveNoSignature() {
        // Unclosed in the middle of the document: closing it would reinterpret later text.
        assertTrue(help("((a, b) => a)(1, |\nnext: 2").isEmpty());
        // An unrelated error elsewhere is never repaired.
        assertTrue(help("((a, b) => a)(1, |)\nbroken: (").isEmpty());
        assertTrue(help("((a, b) => a)(1|)\nbroken: (").isEmpty());
        assertTrue(help("((a, b) => a)(1 + |").isEmpty());
        assertTrue(help("((a, b) => a)(...|").isEmpty());
        assertTrue(help("((a, b) => a)(1, 2 |3)").isEmpty());
        // A lexical error is an ordinary empty result, not an exception.
        assertTrue(help("((a, b) => a)(1, \"open|").isEmpty());
        assertTrue(help("((a, b) => a)(1, \"open\n|)").isEmpty());
    }

    @Test
    void trailingClosureAttachmentFollowsTheNewlineRule() {
        assertActive("((a, b) => a)(|1) { 2 }", 0, OptionalInt.of(0));
        assertActive("((a) => a)(1|) { 2 }", 0, OptionalInt.of(0));
        assertActive("((a, b) => a)(1, |) { 2 }", 1, OptionalInt.of(1));
        assertActive("((a, b) => a)(1, |2)\n{ 3 }", 1, OptionalInt.of(1));
        assertTrue(help("((a) => a)(1) { | }").isEmpty());
        assertTrue(help("((a) => a)(1) {\n  |\n}").isEmpty());
    }

    @Test
    void crlfNonBmpAndInvalidOffsetsAreHandledInUtf16() {
        assertActive("((a, b) => a)(\r\n  1,\r\n  |2\r\n)", 1, OptionalInt.of(1));
        assertActive("((a, b) => a)(\"😀\", |2)", 1, OptionalInt.of(1));
        assertActive("((a, b) => a)(\"😀\",\r\n|", 1, OptionalInt.of(1));

        String source = "((a, b) => a)(\"😀\", 2)";
        ProtosDocumentSnapshot snapshot = snapshot(source);
        int split = source.indexOf('\uD83D') + 1;
        assertTrue(core.signatureHelp(snapshot, split).isEmpty());
        assertTrue(core.signatureHelp(snapshot, -1).isEmpty());
        assertTrue(core.signatureHelp(snapshot, source.length() + 1).isEmpty());
        assertTrue(core.signatureHelp(snapshot(""), 0).isEmpty());
    }

    @Test
    void sessionProjectsTheCurrentUnsavedSnapshotAndDetectsStaleness() {
        ProtosStaticAnalysisSession session = new ProtosStaticAnalysisSession();
        session.openWorkspace("workspace");
        String first = "((a, b) => a)(1, ";
        session.putDocument("workspace", new ProtosDocumentSnapshot("doc", 1, first));

        ProtosStaticSignatureHelpResult result =
                session.signatureHelpCurrent("workspace", "doc", first.length()).orElseThrow();
        assertEquals(OptionalInt.of(1), result.activeParameter());
        assertTrue(session.isCurrent("workspace", result));

        session.putDocument("workspace", new ProtosDocumentSnapshot("doc", 2, first + "2)"));
        assertFalse(session.isCurrent("workspace", result));

        session.closeDocument("workspace", "doc");
        assertTrue(session.signatureHelpCurrent("workspace", "doc", 0).isEmpty());
        assertFalse(session.isCurrent("workspace", result));
    }

    private void assertActive(String marked, int activeArgument, OptionalInt activeParameter) {
        ProtosStaticSignatureHelpResult result = help(marked).orElseThrow(
                () -> new AssertionError("expected a signature for: " + marked));
        assertEquals(activeArgument, result.activeArgument(), marked);
        assertEquals(activeParameter, result.activeParameter(), marked);
    }

    private Optional<ProtosStaticSignatureHelpResult> help(String marked) {
        int cursor = marked.indexOf('|');
        String source = marked.substring(0, cursor) + marked.substring(cursor + 1);
        return core.signatureHelp(snapshot(source), cursor);
    }

    private static ProtosDocumentSnapshot snapshot(String source) {
        return new ProtosDocumentSnapshot("memory:signature-help", 1, source);
    }

    private static SourceSpan span(String source, String text) {
        int start = source.indexOf(text);
        return new SourceSpan(start, start + text.length());
    }
}
