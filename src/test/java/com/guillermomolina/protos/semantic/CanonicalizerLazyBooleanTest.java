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
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.parser.ProtosParser;
import com.guillermomolina.protos.parser.ast.SurfaceExpression;
import com.guillermomolina.protos.parser.ast.SurfaceSequence;
import com.guillermomolina.protos.semantic.ast.CanonicalClosure;
import com.guillermomolina.protos.semantic.ast.CanonicalLookup;
import com.guillermomolina.protos.semantic.ast.CanonicalSend;
import org.junit.jupiter.api.Test;

class CanonicalizerLazyBooleanTest {
    private final Canonicalizer canonicalizer = new Canonicalizer();

    @Test
    void lowersAndToSendWithParameterlessClosureArgument() {
        CanonicalSend send =
                assertInstanceOf(CanonicalSend.class, canonicalizeOnly("a && b"));

        assertEquals("and", send.message());
        assertEquals("a", assertInstanceOf(CanonicalLookup.class, send.receiver()).name());
        assertEquals(1, send.arguments().size());

        CanonicalClosure closure =
                assertInstanceOf(CanonicalClosure.class, send.arguments().get(0));
        assertTrue(closure.parameters().isEmpty());
        assertEquals(1, closure.body().expressions().size());
        assertEquals(
                "b",
                assertInstanceOf(CanonicalLookup.class, closure.body().expressions().get(0)).name());
    }

    @Test
    void lowersOrToSendWithParameterlessClosureArgument() {
        CanonicalSend send =
                assertInstanceOf(CanonicalSend.class, canonicalizeOnly("a || b"));

        assertEquals("or", send.message());
        CanonicalClosure closure =
                assertInstanceOf(CanonicalClosure.class, send.arguments().get(0));
        assertTrue(closure.parameters().isEmpty());
        assertEquals(
                "b",
                assertInstanceOf(CanonicalLookup.class, closure.body().expressions().get(0)).name());
    }

    @Test
    void keepsRightHandSideInsideClosureInsteadOfEvaluatingItEagerly() {
        CanonicalSend send =
                assertInstanceOf(CanonicalSend.class, canonicalizeOnly("a && b + c"));

        CanonicalClosure closure =
                assertInstanceOf(CanonicalClosure.class, send.arguments().get(0));
        assertEquals(1, closure.body().expressions().size());

        CanonicalSend addition =
                assertInstanceOf(CanonicalSend.class, closure.body().expressions().get(0));
        assertEquals("+", addition.message());
        assertEquals(
                "b",
                assertInstanceOf(CanonicalLookup.class, addition.receiver()).name());
        assertEquals(
                "c",
                assertInstanceOf(CanonicalLookup.class, addition.arguments().get(0)).name());
    }

    @Test
    void preservesLeftAssociationOfLogicalChains() {
        CanonicalSend outer =
                assertInstanceOf(CanonicalSend.class, canonicalizeOnly("a && b && c"));

        assertEquals("and", outer.message());

        CanonicalSend inner =
                assertInstanceOf(CanonicalSend.class, outer.receiver());
        assertEquals("and", inner.message());

        CanonicalClosure outerClosure =
                assertInstanceOf(CanonicalClosure.class, outer.arguments().get(0));
        assertEquals(
                "c",
                assertInstanceOf(
                                CanonicalLookup.class,
                                outerClosure.body().expressions().get(0))
                        .name());
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
