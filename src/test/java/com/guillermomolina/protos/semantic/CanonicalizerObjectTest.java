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
import com.guillermomolina.protos.semantic.ast.CanonicalCompose;
import com.guillermomolina.protos.semantic.ast.CanonicalCreate;
import com.guillermomolina.protos.semantic.ast.CanonicalLookup;
import com.guillermomolina.protos.semantic.ast.CanonicalObject;
import org.junit.jupiter.api.Test;

class CanonicalizerObjectTest {
    private final Canonicalizer canonicalizer = new Canonicalizer();

    @Test
    void lowersObjectWithoutExplicitParent() {
        CanonicalObject object =
                assertInstanceOf(CanonicalObject.class, canonicalizeOnly("{ x: value }"));

        assertTrue(object.parent().isEmpty());
        assertEquals(1, object.body().expressions().size());
        assertInstanceOf(CanonicalCreate.class, object.body().expressions().get(0));
    }

    @Test
    void lowersObjectWithExplicitParent() {
        CanonicalObject object =
                assertInstanceOf(CanonicalObject.class, canonicalizeOnly("parent { x: value }"));

        assertEquals(
                "parent",
                assertInstanceOf(CanonicalLookup.class, object.parent().orElseThrow()).name());
    }

    @Test
    void lowersCompositionItemToCanonicalCompose() {
        CanonicalObject object =
                assertInstanceOf(
                        CanonicalObject.class,
                        canonicalizeOnly("{ ...source\n x: value }"));

        assertEquals(2, object.body().expressions().size());

        CanonicalCompose compose =
                assertInstanceOf(CanonicalCompose.class, object.body().expressions().get(0));
        assertEquals(
                "source",
                assertInstanceOf(CanonicalLookup.class, compose.object()).name());
        assertInstanceOf(CanonicalCreate.class, object.body().expressions().get(1));
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
