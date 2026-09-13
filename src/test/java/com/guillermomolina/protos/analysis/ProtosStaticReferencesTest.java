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
    void supportsSingletonMatchBinderAndAliasSeeds() {
        String binderSource =
                "subject match {\n"
                        + "  case @value => value\n"
                        + "}";
        int binderDeclarationName = binderSource.indexOf("value");
        int binderReference = binderSource.lastIndexOf("value");

        ProtosStaticReferenceResult binder =
                references(binderSource, binderDeclarationName).orElseThrow();
        assertTrue(slice(binderSource, binder.target().span()).startsWith("@value"));
        assertEquals(
                List.of(new SourceSpan(
                        binderReference,
                        binderReference + "value".length())),
                binder.occurrences().stream()
                        .map(ProtosStaticReferenceResult.Occurrence::span)
                        .toList());

        String aliasSource =
                "subject match {\n"
                        + "  case @whole: 1 => whole\n"
                        + "  case _ => 0\n"
                        + "}";
        int aliasDeclarationName = aliasSource.indexOf("whole");
        int aliasReference = aliasSource.lastIndexOf("whole");

        ProtosStaticReferenceResult alias =
                references(aliasSource, aliasDeclarationName).orElseThrow();
        assertTrue(slice(aliasSource, alias.target().span()).startsWith("@whole:"));
        assertEquals(
                List.of(new SourceSpan(
                        aliasReference,
                        aliasReference + "whole".length())),
                alias.occurrences().stream()
                        .map(ProtosStaticReferenceResult.Occurrence::span)
                        .toList());
    }

    @Test
    void ambiguousOrUnprovenReferenceSeedReturnsNoResult() {
        String source =
                "subject match {\n"
                        + "  case (@item: 1 | @item: 2) => item\n"
                        + "  case _ => 0\n"
                        + "}";
        assertTrue(references(source, source.lastIndexOf("item")).isEmpty());

        String afterBarrier =
                "f: (value) => {\n"
                        + "  sink(value)\n"
                        + "  value\n"
                        + "}";
        assertTrue(references(afterBarrier, afterBarrier.lastIndexOf("value")).isEmpty());
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
