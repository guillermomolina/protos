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

package com.guillermomolina.protos.semantic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.guillermomolina.protos.parser.ProtosParser;
import com.guillermomolina.protos.parser.ast.SurfaceExpression;
import com.guillermomolina.protos.parser.ast.SurfaceSequence;
import com.guillermomolina.protos.semantic.ast.CanonicalCall;
import com.guillermomolina.protos.semantic.ast.CanonicalClosure;
import com.guillermomolina.protos.semantic.ast.CanonicalLookup;
import com.guillermomolina.protos.semantic.ast.CanonicalSend;
import com.guillermomolina.protos.source.SourceSpan;
import org.junit.jupiter.api.Test;

class CanonicalizerCallTest {
    private final Canonicalizer canonicalizer = new Canonicalizer();

    @Test
    void lowersOrdinaryCallToCanonicalCall() {
        CanonicalCall call =
                assertInstanceOf(CanonicalCall.class, canonicalizeOnly("f(a, b)"));

        assertEquals("f", assertInstanceOf(CanonicalLookup.class, call.receiver()).name());
        assertEquals(2, call.arguments().size());
        assertEquals(
                "a",
                assertInstanceOf(CanonicalLookup.class, call.arguments().get(0)).name());
        assertEquals(
                "b",
                assertInstanceOf(CanonicalLookup.class, call.arguments().get(1)).name());
    }

    @Test
    void lowersMemberCallToCanonicalSend() {
        CanonicalSend send =
                assertInstanceOf(CanonicalSend.class, canonicalizeOnly("object.run(a)"));

        assertEquals(
                "object",
                assertInstanceOf(CanonicalLookup.class, send.receiver()).name());
        assertEquals("run", send.message());
        assertEquals(1, send.arguments().size());
        assertEquals(
                "a",
                assertInstanceOf(CanonicalLookup.class, send.arguments().get(0)).name());
    }

    @Test
    void preservesTrailingClosureAsFinalCanonicalArgument() {
        CanonicalCall call =
                assertInstanceOf(CanonicalCall.class, canonicalizeOnly("f(a) { value }"));

        assertEquals(2, call.arguments().size());
        assertInstanceOf(CanonicalClosure.class, call.arguments().get(1));
    }

    @Test
    void canonicalizesNestedCallsRecursively() {
        CanonicalCall outer =
                assertInstanceOf(CanonicalCall.class, canonicalizeOnly("f(g(a))"));

        CanonicalCall inner =
                assertInstanceOf(CanonicalCall.class, outer.arguments().get(0));
        assertEquals("g", assertInstanceOf(CanonicalLookup.class, inner.receiver()).name());
    }

    @Test
    void lowersContextualWhileToOrdinaryWhileTrueSend() {
        String source = "while(n > 1) { n = n - 1 }";
        CanonicalSend send =
                assertInstanceOf(CanonicalSend.class, canonicalizeOnly(source));

        assertEquals("whileTrue", send.message());
        assertEquals(new SourceSpan(0, source.length()), send.span());

        CanonicalClosure condition =
                assertInstanceOf(CanonicalClosure.class, send.receiver());
        assertEquals(0, condition.parameters().size());
        assertEquals(new SourceSpan(6, 11), condition.span());
        assertEquals(new SourceSpan(6, 11), condition.body().span());
        assertEquals(1, condition.body().expressions().size());
        CanonicalSend comparison = assertInstanceOf(
                CanonicalSend.class, condition.body().expressions().get(0));
        assertEquals(">", comparison.message());
        assertEquals(
                "n",
                assertInstanceOf(CanonicalLookup.class, comparison.receiver()).name());

        assertEquals(1, send.arguments().size());
        CanonicalClosure body =
                assertInstanceOf(CanonicalClosure.class, send.arguments().get(0));
        assertEquals(0, body.parameters().size());
        assertEquals(new SourceSpan(13, source.length()), body.span());
    }

    @Test
    void groupedWhileRemainsOrdinaryCanonicalCall() {
        CanonicalCall call = assertInstanceOf(
                CanonicalCall.class, canonicalizeOnly("(while)(n > 1) { body }"));

        assertEquals(2, call.arguments().size());
        assertInstanceOf(CanonicalClosure.class, call.arguments().get(1));
    }

    @Test
    void lowersGenericContextualForToOrdinaryEachSend() {
        String source = "for(item in items) { item }";
        CanonicalSend send =
                assertInstanceOf(CanonicalSend.class, canonicalizeOnly(source));

        assertEquals("each", send.message());
        assertEquals(new SourceSpan(0, source.length()), send.span());
        CanonicalLookup receiver = assertInstanceOf(CanonicalLookup.class, send.receiver());
        assertEquals("items", receiver.name());
        assertEquals(1, send.arguments().size());

        CanonicalClosure body =
                assertInstanceOf(CanonicalClosure.class, send.arguments().get(0));
        assertEquals(1, body.parameters().size());
        assertEquals("item", body.parameters().get(0).name());
        assertEquals(new SourceSpan(4, 8), body.parameters().get(0).span());
        assertEquals(new SourceSpan(19, source.length()), body.span());
    }

    @Test
    void lowersNumericContextualForToRangeEachSend() {
        String source = "for(i, a, b) { i }";
        CanonicalSend send =
                assertInstanceOf(CanonicalSend.class, canonicalizeOnly(source));

        assertEquals("each", send.message());
        assertEquals(new SourceSpan(0, source.length()), send.span());
        CanonicalCall range = assertInstanceOf(CanonicalCall.class, send.receiver());
        CanonicalLookup rangeLookup =
                assertInstanceOf(CanonicalLookup.class, range.receiver());
        assertEquals("Range", rangeLookup.name());
        assertEquals(new SourceSpan(0, 3), rangeLookup.span());
        assertEquals(new SourceSpan(0, 11), range.span());
        assertEquals(2, range.arguments().size());
        assertEquals(
                "a", assertInstanceOf(CanonicalLookup.class, range.arguments().get(0)).name());
        assertEquals(
                "b", assertInstanceOf(CanonicalLookup.class, range.arguments().get(1)).name());

        assertEquals(1, send.arguments().size());
        CanonicalClosure body =
                assertInstanceOf(CanonicalClosure.class, send.arguments().get(0));
        assertEquals(1, body.parameters().size());
        assertEquals("i", body.parameters().get(0).name());
    }

    @Test
    void lowersSteppedNumericContextualForToRangeEachStepSend() {
        String source = "for(i, a, b, s) { i }";
        CanonicalSend send =
                assertInstanceOf(CanonicalSend.class, canonicalizeOnly(source));

        assertEquals("eachStep", send.message());
        CanonicalCall range = assertInstanceOf(CanonicalCall.class, send.receiver());
        assertEquals(
                "Range", assertInstanceOf(CanonicalLookup.class, range.receiver()).name());
        assertEquals(2, range.arguments().size());

        assertEquals(2, send.arguments().size());
        assertEquals(
                "s", assertInstanceOf(CanonicalLookup.class, send.arguments().get(0)).name());
        CanonicalClosure body =
                assertInstanceOf(CanonicalClosure.class, send.arguments().get(1));
        assertEquals(1, body.parameters().size());
        assertEquals("i", body.parameters().get(0).name());
    }

    @Test
    void contextualForBinderIsNeverAHeaderLookup() {
        CanonicalSend send =
                assertInstanceOf(CanonicalSend.class, canonicalizeOnly("for(i, 0, 3) { 1 }"));
        CanonicalCall range = assertInstanceOf(CanonicalCall.class, send.receiver());
        for (Object argument : range.arguments()) {
            if (argument instanceof CanonicalLookup lookup) {
                assertNotEquals("i", lookup.name());
            }
        }
        CanonicalClosure body =
                assertInstanceOf(CanonicalClosure.class, send.arguments().get(0));
        assertEquals("i", body.parameters().get(0).name());
    }

    @Test
    void groupedForRemainsOrdinaryCanonicalCall() {
        CanonicalCall call = assertInstanceOf(
                CanonicalCall.class, canonicalizeOnly("(for)(i, a, b) { body }"));

        assertEquals(4, call.arguments().size());
        assertEquals(
                "i", assertInstanceOf(CanonicalLookup.class, call.arguments().get(0)).name());
        assertInstanceOf(CanonicalClosure.class, call.arguments().get(3));
    }

    private Object canonicalizeOnly(String source) {
        SurfaceExpression surface = only(source);
        return canonicalizer.canonicalize(surface);
    }

    private SurfaceExpression only(String source) {
        SurfaceSequence program = new ProtosParser(source).parseProgram();
        assertEquals(1, program.expressions().size());
        return program.expressions().get(0);
    }
}
