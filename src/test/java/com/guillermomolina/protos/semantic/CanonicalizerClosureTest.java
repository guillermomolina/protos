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
import com.guillermomolina.protos.semantic.ast.CanonicalParameter;
import com.guillermomolina.protos.semantic.ast.CanonicalSend;
import org.junit.jupiter.api.Test;

class CanonicalizerClosureTest {
    private final Canonicalizer canonicalizer = new Canonicalizer();

    @Test
    void lowersExpressionBodyToCanonicalSingleExpressionSequence() {
        CanonicalClosure closure =
                assertInstanceOf(CanonicalClosure.class, canonicalizeOnly("x => x + 1"));

        assertEquals(1, closure.parameters().size());
        assertEquals("x", closure.parameters().get(0).name());
        assertEquals(1, closure.body().expressions().size());

        CanonicalSend addition =
                assertInstanceOf(CanonicalSend.class, closure.body().expressions().get(0));
        assertEquals("+", addition.message());
    }

    @Test
    void preservesBracedBodyAsCanonicalSequence() {
        CanonicalClosure closure =
                assertInstanceOf(CanonicalClosure.class, canonicalizeOnly("x => { x\n x + 1 }"));

        assertEquals(2, closure.body().expressions().size());
        assertInstanceOf(CanonicalLookup.class, closure.body().expressions().get(0));
        assertInstanceOf(CanonicalSend.class, closure.body().expressions().get(1));
    }

    @Test
    void canonicalizesDefaultAndRestParameters() {
        CanonicalClosure closure =
                assertInstanceOf(
                        CanonicalClosure.class,
                        canonicalizeOnly("(a, b = fallback, ...rest) => a"));

        assertEquals(3, closure.parameters().size());

        CanonicalParameter plain = closure.parameters().get(0);
        assertEquals("a", plain.name());
        assertTrue(plain.defaultValue().isEmpty());
        assertTrue(!plain.rest());

        CanonicalParameter defaulted = closure.parameters().get(1);
        assertEquals("b", defaulted.name());
        assertEquals(
                "fallback",
                assertInstanceOf(
                                CanonicalLookup.class,
                                defaulted.defaultValue().orElseThrow())
                        .name());

        CanonicalParameter rest = closure.parameters().get(2);
        assertEquals("rest", rest.name());
        assertTrue(rest.defaultValue().isEmpty());
        assertTrue(rest.rest());
    }

    @Test
    void canonicalizesNestedExpressionBodyClosuresRecursively() {
        CanonicalClosure outer =
                assertInstanceOf(CanonicalClosure.class, canonicalizeOnly("x => y => x + y"));

        CanonicalClosure inner =
                assertInstanceOf(CanonicalClosure.class, outer.body().expressions().get(0));
        assertEquals("y", inner.parameters().get(0).name());
        assertEquals(1, inner.body().expressions().size());
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
