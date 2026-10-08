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

import com.guillermomolina.protos.analysis.ProtosStaticHoverResult.Kind;
import com.guillermomolina.protos.analysis.ProtosStaticHoverResult.Section;
import com.guillermomolina.protos.source.SourceSpan;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ProtosStaticHoverTest {

    private final ProtosStaticAnalysisCore core = new ProtosStaticAnalysisCore();

    @Test
    void provenClosureParameterReferenceIsLabeledProvenBinding() {
        String source = "f: (value) => {\n  value\n}";
        int reference = source.lastIndexOf("value");

        ProtosStaticHoverResult result = hover(source, reference + 2).orElseThrow();

        assertEquals(span(reference, "value"), result.span());
        assertEquals(
                List.of(new Section(Kind.PROVEN_BINDING, List.of("Closure parameter: value"))),
                result.sections());
    }

    @Test
    void provenDefaultedAndRestParametersKeepDeclarationSyntaxSeparate() {
        String defaulted = "f: (first, second = 2) => second";
        ProtosStaticHoverResult defaultedResult =
                hover(defaulted, defaulted.lastIndexOf("second")).orElseThrow();
        assertEquals(
                List.of(
                        new Section(Kind.PROVEN_BINDING, List.of("Closure parameter: second")),
                        new Section(Kind.SYNTAX, List.of("Default value present"))),
                defaultedResult.sections());

        String rest = "f: (...items) => items";
        ProtosStaticHoverResult restResult =
                hover(rest, rest.lastIndexOf("items")).orElseThrow();
        assertEquals(
                List.of(
                        new Section(Kind.PROVEN_BINDING, List.of("Closure parameter: items")),
                        new Section(Kind.SYNTAX, List.of("Rest parameter"))),
                restResult.sections());
    }

    @Test
    void parameterDeclarationsPublishOnlyTheExactIdentifierSpan() {
        String source = "f: (first, second = 2, ...rest) => { first }";

        int first = source.indexOf("first");
        ProtosStaticHoverResult plain = hover(source, first).orElseThrow();
        assertEquals(span(first, "first"), plain.span());
        assertEquals(
                syntax(
                        "Closure parameter declaration: first",
                        "Closure parameters: (first, second = <default>, ...rest)"),
                plain.sections());

        int second = source.indexOf("second");
        ProtosStaticHoverResult defaulted = hover(source, second + 5).orElseThrow();
        assertEquals(span(second, "second"), defaulted.span());
        assertEquals(
                syntax(
                        "Closure parameter declaration: second",
                        "Default value present",
                        "Closure parameters: (first, second = <default>, ...rest)"),
                defaulted.sections());

        int rest = source.indexOf("rest");
        ProtosStaticHoverResult restResult = hover(source, rest).orElseThrow();
        assertEquals(span(rest, "rest"), restResult.span());
        assertEquals(
                syntax(
                        "Closure parameter declaration: rest",
                        "Rest parameter",
                        "Closure parameters: (first, second = <default>, ...rest)"),
                restResult.sections());

        // Neither the default expression operator nor the rest spread is the name.
        assertTrue(hover(source, source.indexOf("= 2")).isEmpty());
        assertTrue(hover(source, source.indexOf("...")).isEmpty());

        String bare = "f: value => value";
        ProtosStaticHoverResult bareResult = hover(bare, bare.indexOf("value")).orElseThrow();
        assertEquals(span(bare.indexOf("value"), "value"), bareResult.span());
        assertEquals(
                syntax("Closure parameter declaration: value", "Closure parameters: (value)"),
                bareResult.sections());
    }

    @Test
    void closureFirstTokenDescribesShapeWithoutClaimingCallers() {
        String source = "f: (first, second = 2, ...rest) => { first }\nf(1)";
        int open = source.indexOf('(');
        ProtosStaticHoverResult closure = hover(source, open).orElseThrow();
        assertEquals(new SourceSpan(open, open + 1), closure.span());
        assertEquals(
                syntax(
                        "Closure",
                        "Parameters: (first, second = <default>, ...rest)",
                        "Body form: braced"),
                closure.sections());

        // Syntax of the Closure is not a proof that 'f' or 'f(1)' resolves to it.
        assertTrue(hover(source, source.lastIndexOf("f(1)")).isEmpty());
        assertTrue(hover(source, source.indexOf("f:")).isEmpty());

        String parameterless = "g: () => 1";
        assertEquals(
                syntax("Closure", "Parameters: none", "Body form: expression"),
                hover(parameterless, parameterless.indexOf('(')).orElseThrow().sections());

        String trailing = "run() {\n  1\n}";
        int brace = trailing.indexOf('{');
        ProtosStaticHoverResult trailingResult = hover(trailing, brace).orElseThrow();
        assertEquals(new SourceSpan(brace, brace + 1), trailingResult.span());
        assertEquals(
                syntax("Closure", "Parameters: none", "Body form: braced"),
                trailingResult.sections());
    }

    @Test
    void literalsPublishOnlyTheirSyntacticCategory() {
        List<List<String>> cases = List.of(
                List.of("x: 12.5", "12.5", "Number literal"),
                List.of("x: 'text'", "'text'", "String literal"),
                List.of("x: \"text\"", "\"text\"", "String literal"),
                List.of("x: true", "true", "Literal: true"),
                List.of("x: false", "false", "Literal: false"),
                List.of("x: null", "null", "Literal: null"));
        for (List<String> literal : cases) {
            String source = literal.get(0);
            String token = literal.get(1);
            int start = source.indexOf(token);
            ProtosStaticHoverResult result =
                    hover(source, start + token.length() - 1).orElseThrow();

            assertEquals(span(start, token), result.span(), source);
            assertEquals(syntax(literal.get(2)), result.sections(), source);
        }
    }

    @Test
    void sameNamedParametersOfIndependentClosuresProveTheirOwnDeclaration() {
        String source = "a: (value) => value\nb: (value, other) => value";
        int firstReference = source.indexOf("value", source.indexOf("=>"));
        int secondReference = source.lastIndexOf("value");

        assertEquals(span(firstReference, "value"), hover(source, firstReference).orElseThrow().span());
        assertEquals(span(secondReference, "value"), hover(source, secondReference).orElseThrow().span());

        // Hover reuses D110 exactly; each reference proves its own declaration.
        assertEquals(
                span(source.indexOf("value"), "value"),
                core.definition(snapshot(source), firstReference).orElseThrow().targets().get(0).span());
        assertEquals(
                span(source.indexOf("value", source.indexOf("b:")), "value"),
                core.definition(snapshot(source), secondReference).orElseThrow().targets().get(0).span());
    }

    @Test
    void opaqueInvocationNestedCaptureAndDynamicMembersAreNotProven() {
        String invocation = "f: (value) => {\n  sink(value)\n  value\n}";
        assertProven(hover(invocation, invocation.indexOf("value", invocation.indexOf("sink"))));
        assertTrue(hover(invocation, invocation.lastIndexOf("value")).isEmpty());
        assertTrue(hover(invocation, invocation.indexOf("sink")).isEmpty());

        String nested = "f: (outer) => {\n  nested: (inner) => {\n    outer\n    inner\n  }\n}";
        assertTrue(hover(nested, nested.lastIndexOf("outer")).isEmpty());
        assertProven(hover(nested, nested.lastIndexOf("inner")));

        String member = "f: (value) => value.name\nobject.slot";
        assertProven(hover(member, member.indexOf("value", member.indexOf("=>"))));
        assertTrue(hover(member, member.indexOf("name")).isEmpty());
        assertTrue(hover(member, member.indexOf("object")).isEmpty());
        assertTrue(hover(member, member.indexOf("slot")).isEmpty());
    }

    @Test
    void invalidSourceOffsetsAndUninformativePositionsReturnEmpty() {
        assertTrue(hover("f: (value) => {", 4).isEmpty());

        String source = "f: (value) => value\n";
        assertTrue(hover(source, -1).isEmpty());
        assertTrue(hover(source, source.length()).isEmpty());
        assertTrue(hover(source, source.indexOf(':')).isEmpty());
        assertTrue(hover(source, source.indexOf("=>")).isEmpty());
        assertTrue(hover(source, source.indexOf(' ')).isEmpty());
    }

    @Test
    void sessionResultRetainsExactSnapshotFreshness() {
        ProtosStaticAnalysisSession session = new ProtosStaticAnalysisSession();
        session.openWorkspace("workspace");
        String source = "x: 1";
        session.putDocument("workspace", new ProtosDocumentSnapshot("doc", 1L, source));

        ProtosStaticHoverResult result =
                session.hoverCurrent("workspace", "doc", source.indexOf('1')).orElseThrow();
        assertTrue(session.isCurrent("workspace", result));

        session.putDocument("workspace", new ProtosDocumentSnapshot("doc", 2L, "x: 'one'"));
        assertFalse(session.isCurrent("workspace", result));

        session.closeDocument("workspace", "doc");
        assertFalse(session.isCurrent("workspace", result));
        assertTrue(session.hoverCurrent("workspace", "doc", 3).isEmpty());
    }

    private Optional<ProtosStaticHoverResult> hover(String source, int sourceOffset) {
        return core.hover(snapshot(source), sourceOffset);
    }

    private static ProtosDocumentSnapshot snapshot(String source) {
        return new ProtosDocumentSnapshot("test.protos", 1L, source);
    }

    private static void assertProven(Optional<ProtosStaticHoverResult> result) {
        assertTrue(result.isPresent());
        assertEquals(Kind.PROVEN_BINDING, result.get().sections().get(0).kind());
    }

    private static List<Section> syntax(String... facts) {
        return List.of(new Section(Kind.SYNTAX, List.of(facts)));
    }

    private static SourceSpan span(int start, String token) {
        return new SourceSpan(start, start + token.length());
    }
}
