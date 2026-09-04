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
import static org.junit.jupiter.api.Assertions.assertSame;

import com.guillermomolina.protos.parser.ProtosParser;
import com.guillermomolina.protos.parser.ast.SurfaceExpression;
import com.guillermomolina.protos.parser.ast.SurfaceSequence;
import com.guillermomolina.protos.semantic.ast.CanonicalExpression;
import com.guillermomolina.protos.semantic.ast.CanonicalLiteral;
import com.guillermomolina.protos.semantic.ast.CanonicalLookup;
import com.guillermomolina.protos.semantic.ast.CanonicalMember;
import com.guillermomolina.protos.semantic.ast.CanonicalSequence;
import org.junit.jupiter.api.Test;

class CanonicalizerFoundationTest {
    private final Canonicalizer canonicalizer = new Canonicalizer();

    @Test
    void lowersLiteralAndNameToCanonicalLeafNodes() {
        CanonicalLiteral literal =
                assertInstanceOf(CanonicalLiteral.class, canonicalizeOnly("42"));
        CanonicalLookup lookup =
                assertInstanceOf(CanonicalLookup.class, canonicalizeOnly("answer"));

        assertEquals("42", literal.value());
        assertEquals("answer", lookup.name());
    }

    @Test
    void groupingDisappearsFromCanonicalAst() {
        SurfaceExpression surface = only("(answer)");
        CanonicalExpression canonical = canonicalizer.canonicalize(surface);

        CanonicalLookup lookup = assertInstanceOf(CanonicalLookup.class, canonical);
        assertEquals("answer", lookup.name());
        assertSame(
                ((com.guillermomolina.protos.parser.ast.SurfaceGroup) surface)
                        .expression()
                        .span(),
                lookup.span());
    }

    @Test
    void canonicalizesMemberReceiverRecursively() {
        CanonicalMember member =
                assertInstanceOf(CanonicalMember.class, canonicalizeOnly("(dog).speak"));

        CanonicalLookup receiver =
                assertInstanceOf(CanonicalLookup.class, member.receiver());
        assertEquals("dog", receiver.name());
        assertEquals("speak", member.name());
    }

    @Test
    void canonicalizesProgramSequenceInSourceOrder() {
        SurfaceSequence surface = new ProtosParser("first\nsecond").parseProgram();
        CanonicalSequence sequence =
                assertInstanceOf(
                        CanonicalSequence.class, canonicalizer.canonicalize(surface));

        assertEquals(2, sequence.expressions().size());
        assertEquals(
                "first",
                assertInstanceOf(CanonicalLookup.class, sequence.expressions().get(0)).name());
        assertEquals(
                "second",
                assertInstanceOf(CanonicalLookup.class, sequence.expressions().get(1)).name());
    }

    private CanonicalExpression canonicalizeOnly(String source) {
        return canonicalizer.canonicalize(only(source));
    }

    private SurfaceExpression only(String source) {
        SurfaceSequence program = new ProtosParser(source).parseProgram();
        assertEquals(1, program.expressions().size());
        return program.expressions().get(0);
    }
}
