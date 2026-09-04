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

import com.guillermomolina.protos.parser.ProtosParser;
import com.guillermomolina.protos.parser.ast.SurfaceExpression;
import com.guillermomolina.protos.parser.ast.SurfaceSequence;
import com.guillermomolina.protos.semantic.ast.CanonicalLookup;
import com.guillermomolina.protos.semantic.ast.CanonicalSend;
import org.junit.jupiter.api.Test;

class CanonicalizerIndexTest {
    private final Canonicalizer canonicalizer = new Canonicalizer();

    @Test
    void lowersIndexedReadToAtMessageSend() {
        CanonicalSend send =
                assertInstanceOf(CanonicalSend.class, canonicalizeOnly("items[index]"));

        assertEquals("at", send.message());
        assertEquals(
                "items",
                assertInstanceOf(CanonicalLookup.class, send.receiver()).name());
        assertEquals(1, send.arguments().size());
        assertEquals(
                "index",
                assertInstanceOf(CanonicalLookup.class, send.arguments().get(0)).name());
    }

    @Test
    void canonicalizesReceiverAndIndexRecursively() {
        CanonicalSend send =
                assertInstanceOf(
                        CanonicalSend.class,
                        canonicalizeOnly("(items + extra)[index + offset]"));

        assertEquals("at", send.message());

        CanonicalSend receiver =
                assertInstanceOf(CanonicalSend.class, send.receiver());
        assertEquals("+", receiver.message());

        CanonicalSend index =
                assertInstanceOf(CanonicalSend.class, send.arguments().get(0));
        assertEquals("+", index.message());
    }

    @Test
    void nestedIndexedReadsLowerInsideOut() {
        CanonicalSend outer =
                assertInstanceOf(CanonicalSend.class, canonicalizeOnly("matrix[row][column]"));

        assertEquals("at", outer.message());
        CanonicalSend inner =
                assertInstanceOf(CanonicalSend.class, outer.receiver());
        assertEquals("at", inner.message());
        assertEquals(
                "column",
                assertInstanceOf(CanonicalLookup.class, outer.arguments().get(0)).name());
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
