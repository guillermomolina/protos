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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.analysis.ProtosStaticCompletionResult.Candidate;
import com.guillermomolina.protos.analysis.ProtosStaticCompletionResult.Kind;
import com.guillermomolina.protos.source.SourceSpan;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ProtosStaticCompletionTest {

    private static final List<String> RESERVED =
            List.of("this", "context", "true", "false", "null");

    private final ProtosStaticAnalysisCore core = new ProtosStaticAnalysisCore();

    @Test
    void partialPrefixOffersProvenParametersThenReservedSyntax() {
        String source = "f: (first, second) => fir";
        ProtosDocumentSnapshot snapshot = snapshot(source);

        ProtosStaticCompletionResult result =
                core.completion(snapshot, source.length()).orElseThrow();

        assertEquals(snapshot, result.snapshot());
        assertEquals(span(source.lastIndexOf("fir"), "fir"), result.replacementSpan());
        assertEquals(List.of("first", "second"), labels(result, Kind.PROVEN_BINDING));
        assertEquals(RESERVED, labels(result, Kind.SYNTAX));
        assertEquals(
                new Candidate("first", Kind.PROVEN_BINDING),
                result.candidates().get(0));
        assertEquals(result, core.completion(snapshot, source.length()).orElseThrow());
    }

    @Test
    void emptyPositionsUseOneSyntheticReadAndAnEmptyReplacement() {
        String atEnd = "f: (first, second) => ";
        ProtosStaticCompletionResult end = complete(atEnd, atEnd.length()).orElseThrow();
        assertEquals(new SourceSpan(atEnd.length(), atEnd.length()), end.replacementSpan());
        assertEquals(List.of("first", "second"), labels(end, Kind.PROVEN_BINDING));

        String body = "f: (first) => {\n  first\n  \n}\n";
        int blank = body.indexOf("\n  \n") + 3;
        ProtosStaticCompletionResult inBody = complete(body, blank).orElseThrow();
        assertEquals(new SourceSpan(blank, blank), inBody.replacementSpan());
        assertEquals(List.of("first"), labels(inBody, Kind.PROVEN_BINDING));

        ProtosStaticCompletionResult empty = complete("", 0).orElseThrow();
        assertEquals(List.of(), labels(empty, Kind.PROVEN_BINDING));
        assertEquals(RESERVED, labels(empty, Kind.SYNTAX));

        String betweenParens = "f: (first) => g()";
        int inside = betweenParens.length() - 1;
        assertEquals(
                List.of("first"),
                labels(complete(betweenParens, inside).orElseThrow(), Kind.PROVEN_BINDING));
    }

    @Test
    void defaultsSeeOnlyEarlierEstablishedParametersOnEveryPath() {
        assertEquals(List.of("first"), provenAtEndOf("f: (first, second = fi", ") => 1"));
        assertEquals(List.of("first"), provenAtEndOf("f: (first, second = se", ") => 1"));
        assertEquals(
                List.of("first", "second"),
                provenAtEndOf("f: (first, second = 1, third = th", ") => 1"));

        // The invocation in second's default invalidates first on the default
        // path; the path intersection keeps only second.
        assertEquals(
                List.of("second"),
                provenAtEndOf("f: (first, second = g(1), third = fi", ") => 1"));
    }

    @Test
    void opaqueBarriersAndNestedActivationsRemoveProof() {
        assertEquals(List.of(), provenAtEndOf("f: (first) => {\n  sink(first)\n  fi", "\n}\n"));
        assertEquals(List.of("first"), provenAtEndOf("f: (first) => g(fi", ")"));
        assertEquals(List.of(), provenAtEndOf("f: (first) => first && fi", ""));
        assertEquals(List.of("inner"), provenAtEndOf("f: (outer) => (inner) => ou", ""));

        // Body-created slots and object-body reads have no D110 authority.
        assertEquals(
                List.of("first"),
                provenAtEndOf("f: (first) => {\n  local: 1\n  lo", "\n}\n"));
        assertEquals(List.of(), provenAtEndOf("f: (first) => o { x: fi", " }"));

        // The proven-read site keeps reserved syntax even without proven names.
        String afterCall = "f: (first) => {\n  sink(first)\n  fi\n}\n";
        assertEquals(
                RESERVED,
                labels(complete(afterCall, afterCall.indexOf("fi\n") + 2).orElseThrow(), Kind.SYNTAX));
    }

    @Test
    void reservedSyntaxIsAcceptedOnlyWhereTheParserAcceptsIt() {
        // A parent expression accepts intrinsic references but not literals.
        String parent = "f: (first) => fi { x: 1 }";
        ProtosStaticCompletionResult result =
                complete(parent, parent.indexOf("fi {") + 2).orElseThrow();
        assertEquals(List.of("first"), labels(result, Kind.PROVEN_BINDING));
        assertEquals(List.of("this", "context"), labels(result, Kind.SYNTAX));

        for (ProtosStaticCompletionResult any : List.of(
                result,
                complete("f: (first) => fir", 17).orElseThrow())) {
            assertFalse(any.candidates().stream().anyMatch(c -> c.label().equals("super")));
        }
    }

    @Test
    void memberAndSuperPositionsNeverInventMembers() {
        assertEmptyAtEndOf("object.", "");
        assertEmptyAtEndOf("f: (first) => first.se", "");
        assertEmptyAtEndOf("f: (first) => first.", "");
        assertEmptyAtEndOf("f: (first) => super.", "");
        assertEmptyAtEndOf("f: (first) => super.fi", "()");
    }

    @Test
    void declarationsAndMutationTargetsAreNotReadSites() {
        assertEmptyAtEndOf("fi", " => 1");
        assertEmptyAtEndOf("(fi", ") => 1");
        assertEmptyAtEndOf("f: (first) => fi", " => 1");
        assertEmptyAtEndOf("f: (first) => fi", " = 1");
        assertEmptyAtEndOf("fi", ": 1");
        assertEmptyAtEndOf("f: (first, se", ") => 1");
    }

    @Test
    void commentsStringsAndLexicalErrorsYieldNothing() {
        assertEmptyAtEndOf("f: (first) => 'fi", "'");
        assertEmptyAtEndOf("f: (first) => \"\"\"\nfi", "\n\"\"\"");
        assertEmptyAtEndOf("f: (first) => first // fi", "");
        assertEmptyAtEndOf("f: (first) => first // fi", "\nfirst");
        assertEmptyAtEndOf("f: (first) => /* fi", " */ first");
        assertEmptyAtEndOf("f: (first) => fi", "\n'unterminated");
        assertEmptyAtEndOf("f: (first) => fi", "\n/* unterminated");
        assertEmptyAtEndOf("f: (first) => fi", " #");

        assertEquals(List.of("first"), provenAtEndOf("f: (first) => /* c */ fi", ""));
    }

    @Test
    void cursorInsideOrBeforeTokensNeverProducesHybridNames() {
        String source = "f: (first) => first";
        int reference = source.lastIndexOf("first");
        assertTrue(complete(source, reference + 2).isEmpty());
        assertTrue(complete(source, reference).isEmpty());
        assertEmptyAtEndOf("f: (first) => 1", "");
        assertEmptyAtEndOf("f: (first) => a =", "== first");
        assertEmptyAtEndOf("f: (first) => this", "");
    }

    @Test
    void incompleteSourceFailsClosedUnlessOneSyntheticReadParses() {
        assertEmptyAtEndOf("g(fir", "");
        assertEmptyAtEndOf("f: (first) => g(", "");
        assertEmptyAtEndOf("f: (first) => {\n  fi", "");

        String open = "f: (first) => g(fi, 2)";
        int end = open.indexOf("fi,") + 2;
        ProtosStaticCompletionResult result = complete(open, end).orElseThrow();
        assertEquals(span(end - 2, "fi"), result.replacementSpan());
        assertEquals(List.of("first"), labels(result, Kind.PROVEN_BINDING));
    }

    @Test
    void invalidOffsetsYieldNothingAndResultsRejectDuplicates() {
        String source = "f: (first) => fi";
        assertTrue(complete(source, -1).isEmpty());
        assertTrue(complete(source, source.length() + 1).isEmpty());

        String nonBmp = "f: (first) => '😀' + fi";
        assertTrue(complete(nonBmp, nonBmp.indexOf('\uDE00')).isEmpty());
        assertEquals(List.of("first"), provenAtEndOf(nonBmp, ""));

        assertThrows(
                IllegalArgumentException.class,
                () -> new ProtosStaticCompletionResult(
                        snapshot(source),
                        new SourceSpan(0, 0),
                        List.of(
                                new Candidate("first", Kind.PROVEN_BINDING),
                                new Candidate("first", Kind.SYNTAX))));
    }

    @Test
    void sessionCompletionIsSnapshotBoundAndBecomesStale() {
        ProtosStaticAnalysisSession session = new ProtosStaticAnalysisSession();
        session.openWorkspace("w");
        String source = "f: (first) => fi";
        session.putDocument("w", new ProtosDocumentSnapshot("doc", 1, source));

        ProtosStaticCompletionResult result =
                session.completionCurrent("w", "doc", source.length()).orElseThrow();
        assertTrue(session.isCurrent("w", result));

        session.putDocument("w", new ProtosDocumentSnapshot("doc", 2, source + "\n"));
        assertFalse(session.isCurrent("w", result));

        session.closeDocument("w", "doc");
        assertTrue(session.completionCurrent("w", "doc", source.length()).isEmpty());
    }

    private Optional<ProtosStaticCompletionResult> complete(String source, int offset) {
        return core.completion(snapshot(source), offset);
    }

    private List<String> provenAtEndOf(String beforeCursor, String afterCursor) {
        return labels(
                complete(beforeCursor + afterCursor, beforeCursor.length()).orElseThrow(),
                Kind.PROVEN_BINDING);
    }

    private void assertEmptyAtEndOf(String beforeCursor, String afterCursor) {
        assertEquals(
                Optional.empty(),
                complete(beforeCursor + afterCursor, beforeCursor.length()),
                beforeCursor + "|" + afterCursor);
    }

    private static List<String> labels(ProtosStaticCompletionResult result, Kind kind) {
        return result.candidates().stream()
                .filter(candidate -> candidate.kind() == kind)
                .map(Candidate::label)
                .toList();
    }

    private static ProtosDocumentSnapshot snapshot(String source) {
        return new ProtosDocumentSnapshot("memory:/completion.protos", 1, source);
    }

    private static SourceSpan span(int start, String token) {
        return new SourceSpan(start, start + token.length());
    }
}
