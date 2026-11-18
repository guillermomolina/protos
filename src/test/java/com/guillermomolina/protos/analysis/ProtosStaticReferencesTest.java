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

import com.guillermomolina.protos.source.SourceSpan;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ProtosStaticReferencesTest {
    private final ProtosStaticAnalysisCore core = new ProtosStaticAnalysisCore();

    @Test
    void exactReferenceAndDeclarationSeedsReturnSameParameterReferences() {
        String source = "f: (value) => {\n  value\n  value\n}";
        int declaration = source.indexOf("value");
        int firstReference = source.indexOf("value", declaration + 1);
        int secondReference = source.lastIndexOf("value");

        ProtosStaticReferenceResult fromReference =
                references(source, firstReference).orElseThrow();
        ProtosStaticReferenceResult fromDeclaration =
                references(source, declaration + 1).orElseThrow();

        SourceSpan declarationSpan =
                new SourceSpan(declaration, declaration + "value".length());
        assertEquals(declarationSpan, fromReference.target().span());
        assertEquals(fromReference.target(), fromDeclaration.target());
        assertEquals(
                List.of(
                        new SourceSpan(firstReference, firstReference + "value".length()),
                        new SourceSpan(secondReference, secondReference + "value".length())),
                fromReference.occurrences().stream()
                        .map(ProtosStaticReferenceResult.Occurrence::span)
                        .toList());
        assertEquals(fromReference.occurrences(), fromDeclaration.occurrences());
    }

    @Test
    void contextualForBinderDeclarationAndBodyReadsAreReferences() {
        String source = "for(item in items) {\n  item\n  item\n}";
        int declaration = source.indexOf("item");
        int firstRead = source.indexOf("item", source.indexOf("{"));
        int secondRead = source.lastIndexOf("item");

        ProtosStaticReferenceResult fromDeclaration =
                references(source, declaration + 1).orElseThrow();
        ProtosStaticReferenceResult fromReference =
                references(source, secondRead).orElseThrow();

        assertEquals(
                new SourceSpan(declaration, declaration + "item".length()),
                fromDeclaration.target().span());
        assertEquals(fromDeclaration.target(), fromReference.target());
        assertEquals(
                List.of(
                        new SourceSpan(firstRead, firstRead + "item".length()),
                        new SourceSpan(secondRead, secondRead + "item".length())),
                fromDeclaration.occurrences().stream()
                        .map(ProtosStaticReferenceResult.Occurrence::span)
                        .toList());

        String numeric = "for(i, a, b) { i }";
        ProtosStaticReferenceResult numericResult =
                references(numeric, numeric.indexOf("i")).orElseThrow();
        int numericRead = numeric.lastIndexOf("i");
        assertEquals(
                List.of(new SourceSpan(numericRead, numericRead + 1)),
                numericResult.occurrences().stream()
                        .map(ProtosStaticReferenceResult.Occurrence::span)
                        .toList());
    }

    @Test
    void opaqueInvocationBarrierOmitsLaterUnprovenReadWithoutDeletingEarlierProofs() {
        String source =
                "f: (value) => {\n"
                        + "  value\n"
                        + "  sink(value)\n"
                        + "  value\n"
                        + "}";
        int declaration = source.indexOf("value");
        int firstRead = source.indexOf("value", declaration + 1);
        int callArgument = source.indexOf("value", source.indexOf("sink"));
        int finalRead = source.lastIndexOf("value");

        ProtosStaticReferenceResult result =
                references(source, declaration + 1).orElseThrow();

        List<SourceSpan> spans = result.occurrences().stream()
                .map(ProtosStaticReferenceResult.Occurrence::span)
                .toList();
        assertEquals(
                List.of(
                        new SourceSpan(firstRead, firstRead + "value".length()),
                        new SourceSpan(callArgument, callArgument + "value".length())),
                spans);
        assertFalse(spans.contains(
                new SourceSpan(finalRead, finalRead + "value".length())));
    }

    @Test
    void sameNameInIndependentClosuresDoesNotMergeIdentities() {
        String source =
                "first: (value) => value\n"
                        + "second: (value) => value";
        int firstDeclaration = source.indexOf("value");
        int firstReference = source.indexOf("value", firstDeclaration + 1);
        int secondDeclaration = source.indexOf("value", firstReference + 1);
        int secondReference = source.lastIndexOf("value");

        ProtosStaticReferenceResult first =
                references(source, firstReference).orElseThrow();
        ProtosStaticReferenceResult second =
                references(source, secondReference).orElseThrow();

        assertEquals(
                new SourceSpan(firstDeclaration, firstDeclaration + "value".length()),
                first.target().span());
        assertEquals(
                List.of(new SourceSpan(firstReference, firstReference + "value".length())),
                first.occurrences().stream()
                        .map(ProtosStaticReferenceResult.Occurrence::span)
                        .toList());

        assertEquals(
                new SourceSpan(secondDeclaration, secondDeclaration + "value".length()),
                second.target().span());
        assertEquals(
                List.of(new SourceSpan(secondReference, secondReference + "value".length())),
                second.occurrences().stream()
                        .map(ProtosStaticReferenceResult.Occurrence::span)
                        .toList());
    }

    @Test
    void unprovenReferenceAfterOpaqueBarrierReturnsNoResult() {
        String source =
                "f: (value) => {\n"
                        + "  sink(value)\n"
                        + "  value\n"
                        + "}";
        assertTrue(references(source, source.lastIndexOf("value")).isEmpty());
    }

    @Test
    void arrayConstructionDoesNotInventTextualArrayReference() {
        String source = "f: (Array) => [1]";
        int declaration = source.indexOf("Array");
        int bracket = source.indexOf("[");

        ProtosStaticReferenceResult result =
                references(source, declaration + 1).orElseThrow();

        assertTrue(result.occurrences().isEmpty());
        assertTrue(references(source, bracket).isEmpty());
    }

    @Test
    void contextualWhileHeaderIsNotAReferenceButGroupedWhileIs() {
        String source =
                "f: (while, n) => {\n"
                        + "  (while)(n) { n }\n"
                        + "  while(n > 1) { n = n - 1 }\n"
                        + "}";
        int declaration = source.indexOf("while");
        int groupedRead = source.indexOf("while", source.indexOf("(while)"));
        int contextualHeader = source.lastIndexOf("while");

        ProtosStaticReferenceResult result =
                references(source, declaration + 1).orElseThrow();

        assertEquals(
                List.of(new SourceSpan(groupedRead, groupedRead + "while".length())),
                result.occurrences().stream()
                        .map(ProtosStaticReferenceResult.Occurrence::span)
                        .toList());
        assertTrue(references(source, contextualHeader).isEmpty());
    }

    @Test
    void contextualUntilKeywordIsNotAReferenceButGroupedUntilIs() {
        String source =
                "f: (until, n) => {\n"
                        + "  (until)(n)\n"
                        + "  { n } until(n)\n"
                        + "}";
        int declaration = source.indexOf("until");
        int groupedRead = source.indexOf("until", source.indexOf("(until)"));
        int contextualKeyword = source.lastIndexOf("until");

        ProtosStaticReferenceResult result =
                references(source, declaration + 1).orElseThrow();

        assertEquals(
                List.of(new SourceSpan(groupedRead, groupedRead + "until".length())),
                result.occurrences().stream()
                        .map(ProtosStaticReferenceResult.Occurrence::span)
                        .toList());
        assertTrue(references(source, contextualKeyword).isEmpty());
    }

    @Test
    void contextualUntilCapturedReadsAreNotClaimedAsExactReferences() {
        String source =
                "f: (n) => {\n"
                        + "  { n = n - 1 } until(n == 0)\n"
                        + "}";
        int declaration = source.indexOf("n");

        ProtosStaticReferenceResult result =
                references(source, declaration).orElseThrow();

        assertTrue(result.occurrences().isEmpty());
        assertTrue(references(source, source.indexOf("n == 0")).isEmpty());
        assertTrue(references(source, source.indexOf("n - 1")).isEmpty());
    }

    @Test
    void contextualIfHeaderIsNotAReferenceButGroupedIfIs() {
        String source =
                "f: (if, n) => {\n"
                        + "  (if)(n) { n }\n"
                        + "  if(n) { n } else { n }\n"
                        + "}";
        int declaration = source.indexOf("if");
        int groupedRead = source.indexOf("if", source.indexOf("(if)"));
        int contextualHeader = source.lastIndexOf("if(");

        ProtosStaticReferenceResult result =
                references(source, declaration + 1).orElseThrow();

        assertEquals(
                List.of(new SourceSpan(groupedRead, groupedRead + "if".length())),
                result.occurrences().stream()
                        .map(ProtosStaticReferenceResult.Occurrence::span)
                        .toList());
        assertTrue(references(source, contextualHeader).isEmpty());
    }

    @Test
    void contextualIfEagerConditionIsAReferenceButBranchReadsAreNot() {
        String source =
                "f: (n) => {\n"
                        + "  if(n > 1) { n } else { n }\n"
                        + "}";
        int declaration = source.indexOf("n");
        int conditionRead = source.indexOf("n", source.indexOf("if("));

        ProtosStaticReferenceResult result =
                references(source, declaration).orElseThrow();

        assertEquals(
                List.of(new SourceSpan(conditionRead, conditionRead + 1)),
                result.occurrences().stream()
                        .map(ProtosStaticReferenceResult.Occurrence::span)
                        .toList());
    }

    @Test
    void contextualWhileCapturedReadsAreNotClaimedAsExactReferences() {
        String source =
                "f: (n) => {\n"
                        + "  while(n > 1) { n = n - 1 }\n"
                        + "}";
        int declaration = source.indexOf("n");
        int conditionRead = source.indexOf("n", source.indexOf("while("));

        ProtosStaticReferenceResult result =
                references(source, declaration).orElseThrow();

        assertTrue(result.occurrences().isEmpty());
        assertTrue(references(source, conditionRead).isEmpty());
    }

    @Test
    void sessionResultRetainsExactSnapshotFreshness() {
        ProtosStaticAnalysisSession session = new ProtosStaticAnalysisSession();
        session.openWorkspace("workspace");
        String source = "f: (value) => value";
        ProtosDocumentSnapshot snapshot =
                new ProtosDocumentSnapshot("doc", 1L, source);
        session.putDocument("workspace", snapshot);

        ProtosStaticReferenceResult result = session.referencesCurrent(
                        "workspace",
                        "doc",
                        source.lastIndexOf("value"))
                .orElseThrow();
        assertTrue(session.isCurrent("workspace", result));

        session.putDocument(
                "workspace",
                new ProtosDocumentSnapshot("doc", 2L, source + "\n"));
        assertFalse(session.isCurrent("workspace", result));
    }

    @Test
    void ordinarySlotReadsReferenceTheirExactCreation() {
        String source = "x: 1\nx\nx";
        int first = source.indexOf("x", 1);
        int second = source.lastIndexOf("x");
        ProtosStaticReferenceResult result = references(source, second).orElseThrow();
        assertEquals(new SourceSpan(0, 1), result.target().span());
        assertEquals(List.of(new SourceSpan(first, first + 1), new SourceSpan(second, second + 1)),
                spans(result));
        assertSameProvenOrigin(source, result);
    }

    @Test
    void closureStoredInSlotIsReferencedAtEachCallReceiver() {
        // The call is an opaque invocation, so only receivers up to it are proven.
        String source = "f: () => { 1 }\nf\nf()\nf";
        int first = source.indexOf("f\nf()");
        int second = source.indexOf("f()");
        ProtosStaticReferenceResult result = references(source, second).orElseThrow();
        assertEquals(new SourceSpan(0, 1), result.target().span());
        assertEquals(List.of(new SourceSpan(first, first + 1), new SourceSpan(second, second + 1)),
                spans(result));
        assertSameProvenOrigin(source, result);
        assertTrue(references(source, source.lastIndexOf("f")).isEmpty());
    }

    @Test
    void assignmentTargetReferencesTheInitialSlotCreation() {
        String source = "count: 1\ncount = 2\ncount";
        int target = source.indexOf("count =");
        int read = source.lastIndexOf("count");
        ProtosStaticReferenceResult result = references(source, read).orElseThrow();
        assertEquals(new SourceSpan(0, "count".length()), result.target().span());
        assertEquals(
                List.of(new SourceSpan(target, target + "count".length()),
                        new SourceSpan(read, read + "count".length())),
                spans(result));
        assertSameProvenOrigin(source, result);
    }

    @Test
    void sameNamedSlotsInDifferentActivationsDoNotMerge() {
        String source = "a: () => {\n  v: 1\n  v\n}\nb: () => {\n  v: 2\n  v\n}";
        int firstCreation = source.indexOf("v:");
        int firstRead = source.indexOf("v", firstCreation + 1);
        int secondCreation = source.lastIndexOf("v:");
        int secondRead = source.lastIndexOf("v");
        ProtosStaticReferenceResult first = references(source, firstRead).orElseThrow();
        ProtosStaticReferenceResult second = references(source, secondRead).orElseThrow();
        assertEquals(new SourceSpan(firstCreation, firstCreation + 1), first.target().span());
        assertEquals(List.of(new SourceSpan(firstRead, firstRead + 1)), spans(first));
        assertEquals(new SourceSpan(secondCreation, secondCreation + 1), second.target().span());
        assertEquals(List.of(new SourceSpan(secondRead, secondRead + 1)), spans(second));
        assertSameProvenOrigin(source, first);
        assertSameProvenOrigin(source, second);
    }

    @Test
    void slotReadAfterOpaqueInvocationIsNotAReference() {
        String source = "x: 1\nx\nsink()\nx";
        int first = source.indexOf("x", 1);
        ProtosStaticReferenceResult result = references(source, first).orElseThrow();
        assertEquals(List.of(new SourceSpan(first, first + 1)), spans(result));
        assertSameProvenOrigin(source, result);
        assertTrue(references(source, source.lastIndexOf("x")).isEmpty());
    }

    @Test
    void memberSelectorsAndObjectSlotDeclarationsHaveNoReferences() {
        String source = "obj: {\n  dato: 1\n}\nobj.dato";
        assertTrue(references(source, source.indexOf("dato")).isEmpty());
        assertTrue(references(source, source.lastIndexOf("dato")).isEmpty());
        String replaced = "obj: {\n  dato: 1\n}\ncontext.obj = {\n  dato: 2\n}\nobj.dato";
        assertTrue(references(replaced, replaced.lastIndexOf("dato")).isEmpty());
    }

    @Test
    void immediatelyInvokedCaptureIsAReferenceOfTheCapturedSlot() {
        String source = "x: 1\nx\n(() => x)()\nx\n";
        int topRead = source.indexOf("x", 2);
        int capture = source.indexOf("x", source.indexOf("=>"));

        ProtosStaticReferenceResult result = references(source, capture).orElseThrow();

        assertEquals(new SourceSpan(0, 1), result.target().span());
        // The invocation is still an opaque barrier for the caller, so the
        // final read is not re-proven and is omitted.
        assertEquals(
                List.of(new SourceSpan(topRead, topRead + 1), new SourceSpan(capture, capture + 1)),
                spans(result));
        assertSameProvenOrigin(source, result);
    }

    @Test
    void capturedAndShadowingLocalReadsAreDistinctReferences() {
        String source = "x: 1\n(() => {\n  x\n  x: 2\n  x\n})()\n";
        int captured = source.indexOf("x", source.indexOf("=>"));
        int local = source.lastIndexOf("x");

        ProtosStaticReferenceResult outer = references(source, captured).orElseThrow();
        assertEquals(new SourceSpan(0, 1), outer.target().span());
        assertEquals(List.of(new SourceSpan(captured, captured + 1)), spans(outer));

        ProtosStaticReferenceResult inner = references(source, local).orElseThrow();
        int innerDeclaration = source.indexOf("x: 2");
        assertEquals(
                new SourceSpan(innerDeclaration, innerDeclaration + 1), inner.target().span());
        assertEquals(List.of(new SourceSpan(local, local + 1)), spans(inner));
        assertSameProvenOrigin(source, outer);
        assertSameProvenOrigin(source, inner);
    }

    @Test
    void capturedReadsAfterOpaqueEffectsOrInOtherActivationsAreOmitted() {
        String source =
                "x: 1\n(() => {\n  x\n  desconocido()\n  x\n})()\nf: () => x\n";
        int before = source.indexOf("x", source.indexOf("=>"));

        ProtosStaticReferenceResult result = references(source, before).orElseThrow();

        assertEquals(new SourceSpan(0, 1), result.target().span());
        // Neither the read after the unknown call nor the same-name read in
        // the deferred Closure f re-proves the captured origin.
        assertEquals(List.of(new SourceSpan(before, before + 1)), spans(result));
        assertTrue(references(source, source.lastIndexOf("x")).isEmpty());
    }

    @Test
    void lexicalNavigationOfACapturedReadIsNotAnExactReference() {
        String source = "valor: 1\nvalor\nejecutar(() => valor)\ncontext.removeSlot(\"valor\")\n";
        int exact = source.indexOf("valor", 1);
        int capture = source.indexOf("valor", source.indexOf("=>"));
        ProtosDocumentSnapshot snapshot = new ProtosDocumentSnapshot("test.protos", 1L, source);

        // Definition navigates from the deferred capture to its lexical
        // declaration, but that is not a proof of the slot selected when the
        // Closure runs: the slot may be removed before any invocation.
        assertEquals(
                List.of(new SourceSpan(0, "valor".length())),
                core.definition(snapshot, capture).orElseThrow().targets().stream()
                        .map(ProtosStaticDefinitionResult.Target::span)
                        .toList());

        // References keeps the strict proof: the capture is neither a seed nor
        // an occurrence of the exactly proven read.
        assertTrue(references(source, capture).isEmpty());
        ProtosStaticReferenceResult result = references(source, exact).orElseThrow();
        assertEquals(new SourceSpan(0, "valor".length()), result.target().span());
        assertEquals(List.of(new SourceSpan(exact, exact + "valor".length())), spans(result));
        assertSameProvenOrigin(source, result);
    }

    @Test
    void ordinaryDefaultedAndRestParametersShareTheIdentifierTargetFromBothSeeds() {
        assertParameterReferences("f: (valor) => valor", "valor", -1);
        assertParameterReferences("f: (valor: 3) => valor", "valor", "f: (valor: ".length());
        assertParameterReferences("f: (...valor) => valor", "valor", "f: (".length());
    }

    private void assertParameterReferences(String source, String name, int nonNameOffset) {
        int declaration = source.indexOf(name);
        int read = source.lastIndexOf(name);
        SourceSpan target = new SourceSpan(declaration, declaration + name.length());

        ProtosStaticReferenceResult fromDeclaration =
                references(source, declaration).orElseThrow();
        ProtosStaticReferenceResult fromRead = references(source, read).orElseThrow();

        assertEquals(target, fromDeclaration.target().span());
        assertEquals(fromDeclaration.target(), fromRead.target());
        assertEquals(List.of(new SourceSpan(read, read + name.length())), spans(fromRead));
        assertEquals(fromDeclaration.occurrences(), fromRead.occurrences());
        if (nonNameOffset >= 0) {
            // The default value text or the rest prefix is not a selection
            // of the declaration.
            assertTrue(references(source, nonNameOffset).isEmpty(), source);
        }
    }

    private static List<SourceSpan> spans(ProtosStaticReferenceResult result) {
        return result.occurrences().stream()
                .map(ProtosStaticReferenceResult.Occurrence::span)
                .toList();
    }

    private void assertSameProvenOrigin(String source, ProtosStaticReferenceResult result) {
        for (ProtosStaticReferenceResult.Occurrence occurrence : result.occurrences()) {
            ProtosStaticDefinitionResult proof = core.definition(
                            new ProtosDocumentSnapshot("test.protos", 1L, source),
                            occurrence.span().startOffset())
                    .orElseThrow();
            assertEquals(occurrence.span(), proof.referenceSpan());
            assertEquals(List.of(result.target()), proof.targets());
        }
    }

    private Optional<ProtosStaticReferenceResult> references(
            String source,
            int sourceOffset) {
        return core.references(
                new ProtosDocumentSnapshot("test.protos", 1L, source),
                sourceOffset);
    }

    private static String slice(String source, SourceSpan span) {
        return source.substring(span.startOffset(), span.endOffset());
    }
}
