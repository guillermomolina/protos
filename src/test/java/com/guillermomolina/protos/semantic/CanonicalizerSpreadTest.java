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
import com.guillermomolina.protos.semantic.ast.CanonicalCall;
import com.guillermomolina.protos.semantic.ast.CanonicalLookup;
import com.guillermomolina.protos.semantic.ast.CanonicalSend;
import com.guillermomolina.protos.semantic.ast.CanonicalSpread;
import com.guillermomolina.protos.semantic.ast.CanonicalSuperSend;
import org.junit.jupiter.api.Test;

class CanonicalizerSpreadTest {
    private final Canonicalizer canonicalizer = new Canonicalizer();

    @Test
    void preservesSpreadMarkerInOrdinaryCallArguments() {
        CanonicalCall call =
                assertInstanceOf(CanonicalCall.class, canonicalizeOnly("f(a, ...items, b)"));

        assertEquals(3, call.arguments().size());
        assertEquals("a", assertInstanceOf(CanonicalLookup.class, call.arguments().get(0)).name());
        CanonicalSpread spread =
                assertInstanceOf(CanonicalSpread.class, call.arguments().get(1));
        assertEquals("items", assertInstanceOf(CanonicalLookup.class, spread.expression()).name());
        assertEquals("b", assertInstanceOf(CanonicalLookup.class, call.arguments().get(2)).name());
    }

    @Test
    void preservesSpreadMarkerInMemberSendArguments() {
        CanonicalSend send =
                assertInstanceOf(CanonicalSend.class, canonicalizeOnly("obj.move(...items)"));

        CanonicalSpread spread =
                assertInstanceOf(CanonicalSpread.class, send.arguments().get(0));
        assertEquals("items", assertInstanceOf(CanonicalLookup.class, spread.expression()).name());
    }

    @Test
    void preservesSpreadMarkerInSuperSendArguments() {
        CanonicalSuperSend send =
                assertInstanceOf(CanonicalSuperSend.class, canonicalizeOnly("super.move(...items)"));

        CanonicalSpread spread =
                assertInstanceOf(CanonicalSpread.class, send.arguments().get(0));
        assertEquals("items", assertInstanceOf(CanonicalLookup.class, spread.expression()).name());
    }

    private Object canonicalizeOnly(String source) {
        return canonicalizer.canonicalize(only(source));
    }

    private SurfaceExpression only(String source) {
        SurfaceSequence program = new ProtosParser(source).parseProgram();
        assertEquals(1, program.expressions().size());
        return program.expressions().get(0);
    }
}
