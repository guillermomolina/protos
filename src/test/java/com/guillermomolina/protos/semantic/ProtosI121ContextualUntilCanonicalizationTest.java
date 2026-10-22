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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import com.guillermomolina.protos.parser.ProtosParser;
import com.guillermomolina.protos.parser.ast.SurfaceExpression;
import com.guillermomolina.protos.parser.ast.SurfaceSequence;
import com.guillermomolina.protos.semantic.ast.CanonicalClosure;
import com.guillermomolina.protos.semantic.ast.CanonicalCreate;
import com.guillermomolina.protos.semantic.ast.CanonicalExpression;
import com.guillermomolina.protos.semantic.ast.CanonicalLookup;
import com.guillermomolina.protos.semantic.ast.CanonicalSend;
import com.guillermomolina.protos.source.SourceSpan;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * I121-B / D212: the contextual suffix {@code { body } until(condition)}
 * canonicalizes to the ordinary {@code untilTrue} send whose receiver is the
 * body Closure and whose sole argument is an independent condition Closure.
 */
class ProtosI121ContextualUntilCanonicalizationTest {
    private final Canonicalizer canonicalizer = new Canonicalizer();

    @Test
    void lowersToOrdinaryUntilTrueSendOnTheBodyClosure() {
        String source = "{ work() } until(done)";
        CanonicalSend send =
                assertInstanceOf(CanonicalSend.class, canonicalizeOnly(source));

        assertEquals("untilTrue", send.message());
        assertEquals(new SourceSpan(0, source.length()), send.span());

        CanonicalClosure body =
                assertInstanceOf(CanonicalClosure.class, send.receiver());
        assertEquals(0, body.parameters().size());
        assertEquals(new SourceSpan(0, 10), body.span());
        assertEquals(1, body.body().expressions().size());

        assertEquals(1, send.arguments().size());
        CanonicalClosure condition =
                assertInstanceOf(CanonicalClosure.class, send.arguments().get(0));
        assertEquals(0, condition.parameters().size());
        assertEquals(new SourceSpan(17, 21), condition.span());
        assertEquals(1, condition.body().expressions().size());
        assertEquals(
                "done",
                assertInstanceOf(
                                CanonicalLookup.class,
                                condition.body().expressions().get(0))
                        .name());
    }

    @Test
    void matchesTheShapeOfTheExplicitClosureProtocolSend() {
        CanonicalSend sugar = assertInstanceOf(
                CanonicalSend.class,
                canonicalizeOnly("{ a: 1; a } until(b)"));
        CanonicalSend explicit = assertInstanceOf(
                CanonicalSend.class,
                canonicalizeOnly("(() => { a: 1; a }).untilTrue(() => b)"));

        assertEquals(explicit.message(), sugar.message());
        assertEquals(explicit.arguments().size(), sugar.arguments().size());

        CanonicalClosure sugarBody =
                assertInstanceOf(CanonicalClosure.class, sugar.receiver());
        CanonicalClosure explicitBody =
                assertInstanceOf(CanonicalClosure.class, explicit.receiver());
        assertEquals(
                explicitBody.body().expressions().size(),
                sugarBody.body().expressions().size());
        assertInstanceOf(
                CanonicalCreate.class,
                sugarBody.body().expressions().get(0));

        CanonicalClosure sugarCondition =
                assertInstanceOf(CanonicalClosure.class, sugar.arguments().get(0));
        CanonicalClosure explicitCondition =
                assertInstanceOf(CanonicalClosure.class, explicit.arguments().get(0));
        assertEquals(
                explicitCondition.body().expressions().size(),
                sugarCondition.body().expressions().size());
        assertNotSame(sugarBody, sugarCondition);
    }

    @Test
    void noLoopSpecificCanonicalNodeExists() {
        assertFalse(
                Arrays.stream(CanonicalExpression.class.getPermittedSubclasses())
                        .map(Class::getSimpleName)
                        .anyMatch(name -> name.contains("Until")
                                || name.contains("Loop")),
                "contextual until must lower to an ordinary canonical send");
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
