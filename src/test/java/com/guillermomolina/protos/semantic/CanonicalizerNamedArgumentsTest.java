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
import com.guillermomolina.protos.parser.ast.SurfaceSequence;
import com.guillermomolina.protos.semantic.ast.CanonicalCall;
import com.guillermomolina.protos.semantic.ast.CanonicalCreate;
import com.guillermomolina.protos.semantic.ast.CanonicalLookup;
import com.guillermomolina.protos.semantic.ast.CanonicalNamedArgument;
import com.guillermomolina.protos.semantic.ast.CanonicalSend;
import com.guillermomolina.protos.semantic.ast.CanonicalSpread;
import com.guillermomolina.protos.semantic.ast.CanonicalSuperSend;
import org.junit.jupiter.api.Test;

class CanonicalizerNamedArgumentsTest {

    private final Canonicalizer canonicalizer = new Canonicalizer();

    @Test
    void preservesTextualOrderOfMixedArguments() {
        CanonicalCall call = assertInstanceOf(
                CanonicalCall.class,
                canonicalizeOnly("f(c: 30, ...items, 1, a: 10)"));

        assertEquals(4, call.arguments().size());

        CanonicalNamedArgument first = assertInstanceOf(
                CanonicalNamedArgument.class,
                call.arguments().get(0));
        assertEquals("c", first.name());

        assertInstanceOf(
                CanonicalSpread.class,
                call.arguments().get(1));

        CanonicalNamedArgument fourth = assertInstanceOf(
                CanonicalNamedArgument.class,
                call.arguments().get(3));
        assertEquals("a", fourth.name());
    }

    @Test
    void preservesNestedNamedCallsWithoutFlattening() {
        CanonicalCall call = assertInstanceOf(
                CanonicalCall.class,
                canonicalizeOnly("f(a: g(b: 2))"));

        CanonicalNamedArgument outer = assertInstanceOf(
                CanonicalNamedArgument.class,
                call.arguments().get(0));

        assertEquals("a", outer.name());

        CanonicalCall inner = assertInstanceOf(
                CanonicalCall.class,
                outer.expression());

        CanonicalNamedArgument innerArgument = assertInstanceOf(
                CanonicalNamedArgument.class,
                inner.arguments().get(0));

        assertEquals("b", innerArgument.name());
    }

    @Test
    void preservesMemberSendAndSuperSendNames() {
        CanonicalSend send = assertInstanceOf(
                CanonicalSend.class,
                canonicalizeOnly("obj.run(a: 1)"));

        assertEquals(
                "a",
                assertInstanceOf(
                        CanonicalNamedArgument.class,
                        send.arguments().get(0)).name());

        CanonicalSuperSend superSend = assertInstanceOf(
                CanonicalSuperSend.class,
                canonicalizeOnly("super.run(a: 1)"));

        assertEquals(
                "a",
                assertInstanceOf(
                        CanonicalNamedArgument.class,
                        superSend.arguments().get(0)).name());
    }

    @Test
    void arraySugarPreservesNamedArguments() {
        CanonicalCall call = assertInstanceOf(
                CanonicalCall.class,
                canonicalizeOnly("[a: 1]"));

        assertEquals(
                "Array",
                assertInstanceOf(
                        CanonicalLookup.class, call.receiver()).name());

        assertEquals(
                "a",
                assertInstanceOf(
                        CanonicalNamedArgument.class,
                        call.arguments().get(0)).name());
    }

    @Test
    void groupedSlotCreationRemainsAValueExpression() {
        CanonicalCall call = assertInstanceOf(
                CanonicalCall.class,
                canonicalizeOnly("f((a: 1))"));

        assertEquals(1, call.arguments().size());

        assertInstanceOf(
                CanonicalCreate.class,
                call.arguments().get(0));
    }

    @Test
    void ordinaryPositionalCallNeedsNoNamedNode() {
        CanonicalCall call = assertInstanceOf(
                CanonicalCall.class,
                canonicalizeOnly("f(1, ...items, 3)"));

        assertEquals(3, call.arguments().size());
        assertTrue(call.arguments().stream().noneMatch(
                CanonicalNamedArgument.class::isInstance));
    }

    private Object canonicalizeOnly(String source) {
        SurfaceSequence surface =
                new ProtosParser(source).parseProgram();

        assertEquals(1, surface.expressions().size());

        return canonicalizer.canonicalize(
                surface.expressions().get(0));
    }
}
