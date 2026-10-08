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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.source.SourceSpan;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProtosStaticLintTest {
    private static final String UNREACHABLE =
            ProtosStaticLintDiagnostic.UNREACHABLE_AFTER_NONLOCAL_RETURN;
    private static final String FRESH =
            ProtosStaticLintDiagnostic.ALWAYS_DIFFERENT_FRESH_OBJECT;

    private final ProtosStaticAnalysisCore core = new ProtosStaticAnalysisCore();

    @Test
    void reportsWholeUnreachableSuffixAfterDirectNonLocalReturn() {
        String source = "f: () => {\n    ^1\n    a\n    b.c()\n}";

        ProtosStaticLintDiagnostic only = single(source);

        assertEquals(UNREACHABLE, only.ruleId());
        assertEquals(ProtosStaticLintDiagnostic.Severity.WARNING, only.severity());
        assertEquals(
                new SourceSpan(source.indexOf("a\n"), source.indexOf("()\n}") + 2),
                only.span());
    }

    @Test
    void reportsSingleFollowingExpression() {
        String source = "f: () => { ^1; a }";

        assertEquals(
                new SourceSpan(source.indexOf("a "), source.indexOf("a ") + 1),
                single(source).span());
    }

    @Test
    void reportsOnlyOnceFromFirstNonLocalReturn() {
        String source = "f: (x) => { ^x; ^2; a }";

        ProtosStaticLintDiagnostic only = single(source);

        assertEquals(
                new SourceSpan(source.indexOf("^2"), source.indexOf("a }") + 1),
                only.span());
    }

    @Test
    void finalNonLocalReturnLeavesNothingUnreachable() {
        assertTrue(lint("f: () => {\n    a\n    ^1\n}").isEmpty());
        assertTrue(lint("f: () => ^1").isEmpty());
    }

    @Test
    void conditionalInvocationOfReturningClosureProvesNothing() {
        assertTrue(lint(
                "f: (x) => {\n    x.ifTrue(() => { ^1 })\n    a\n}").isEmpty());
    }

    @Test
    void nestedClosureCreationIsNotExecutionOfItsReturn() {
        assertTrue(lint("f: () => { g: () => { ^1 }; a }").isEmpty());

        String source = "f: () => { g: () => { ^1; b }; a }";
        ProtosStaticLintDiagnostic only = single(source);
        assertEquals(UNREACHABLE, only.ruleId());
        assertEquals(
                new SourceSpan(source.indexOf("b }"), source.indexOf("b }") + 1),
                only.span());
    }

    @Test
    void identityAgainstFreshRightObjectIsAlwaysFalse() {
        String source = "a === { x: 1 }";

        ProtosStaticLintDiagnostic only = single(source);

        assertEquals(FRESH, only.ruleId());
        assertEquals(ProtosStaticLintDiagnostic.Severity.WARNING, only.severity());
        assertEquals(new SourceSpan(0, source.length()), only.span());
        assertTrue(only.message().contains("always false"));
    }

    @Test
    void nonIdentityAgainstFreshRightObjectIsAlwaysTrue() {
        String source = "a !== { x: 1 }";

        ProtosStaticLintDiagnostic only = single(source);

        assertEquals(FRESH, only.ruleId());
        assertEquals(new SourceSpan(0, source.length()), only.span());
        assertTrue(only.message().contains("always true"));
    }

    @Test
    void transparentParenthesesAndParentedObjectsAreStillFresh() {
        assertEquals(FRESH, single("a === ({ x: 1 })").ruleId());
        assertEquals(FRESH, single("a !== (({ x: 1 }))").ruleId());
        assertEquals(FRESH, single("a === Proto { x: 1 }").ruleId());
    }

    @Test
    void doesNotReportUnprovenIdentityOrEqualityComparisons() {
        assertTrue(lint("a == { x: 1 }").isEmpty());
        assertTrue(lint("a != { x: 1 }").isEmpty());
        assertTrue(lint("{ x: 1 } === a").isEmpty());
        assertTrue(lint("({ x: 1 }) !== a").isEmpty());
        assertTrue(lint("a === make()").isEmpty());
        assertTrue(lint("a === Proto.new()").isEmpty());
        assertTrue(lint("a === [1]").isEmpty());
        assertTrue(lint("a === %{ \"k\": 1 }").isEmpty());
    }

    @Test
    void ordersFindingsDeterministicallyBySource() {
        String source =
                "f: () => {\n    ^1\n    b === { y: 2 }\n}\nc(a !== { x: 1 })";

        List<ProtosStaticLintDiagnostic> findings = lint(source);

        assertEquals(3, findings.size());
        int suffix = source.indexOf("b ===");
        int suffixEnd = source.indexOf("}\n}") + 1;
        assertEquals(FRESH, findings.get(0).ruleId());
        assertEquals(new SourceSpan(suffix, suffixEnd), findings.get(0).span());
        assertEquals(UNREACHABLE, findings.get(1).ruleId());
        assertEquals(new SourceSpan(suffix, suffixEnd), findings.get(1).span());
        assertEquals(FRESH, findings.get(2).ruleId());
        assertEquals(
                new SourceSpan(source.indexOf("a !=="), source.length() - 1),
                findings.get(2).span());
    }

    @Test
    void cleanAndUnparseableSnapshotsProduceNoFindings() {
        assertTrue(lint("value: 42\nf: () => { value }\nvalue === 42").isEmpty());
        assertTrue(lint("f: () => { ^1; a\n)").isEmpty());
    }

    @Test
    void findingsStayBoundToTheirSnapshotWhichFreshnessRejectsAfterReplacement() {
        ProtosStaticAnalysisSession session = new ProtosStaticAnalysisSession();
        session.openWorkspace("lint");
        ProtosDocumentSnapshot old =
                new ProtosDocumentSnapshot("untitled:stale", 1, "a === { x: 1 }");
        session.putDocument("lint", old);
        ProtosStaticParseResult.Parsed parsed = (ProtosStaticParseResult.Parsed)
                session.parseCurrent("lint", "untitled:stale").orElseThrow();

        session.putDocument(
                "lint",
                new ProtosDocumentSnapshot("untitled:stale", 2, "a === b"));
        List<ProtosStaticLintDiagnostic> findings = ProtosStaticLint.check(parsed);

        assertEquals(1, findings.size());
        assertSame(old, findings.get(0).snapshot());
        assertFalse(session.isCurrent("lint", parsed));
    }

    private List<ProtosStaticLintDiagnostic> lint(String source) {
        ProtosDocumentSnapshot snapshot =
                new ProtosDocumentSnapshot("untitled:lint", 1, source);
        List<ProtosStaticLintDiagnostic> findings = core.lint(snapshot);
        findings.forEach(finding -> assertSame(snapshot, finding.snapshot()));
        return findings;
    }

    private ProtosStaticLintDiagnostic single(String source) {
        List<ProtosStaticLintDiagnostic> findings = lint(source);
        assertEquals(1, findings.size(), () -> findings.toString());
        return findings.get(0);
    }
}
