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

import com.guillermomolina.protos.parser.ProtosParser;
import com.guillermomolina.protos.parser.ast.SurfaceExpression;
import com.guillermomolina.protos.parser.ast.SurfaceSequence;
import com.guillermomolina.protos.semantic.ast.CanonicalCall;
import com.guillermomolina.protos.semantic.ast.CanonicalClosure;
import com.guillermomolina.protos.semantic.ast.CanonicalExpression;
import com.guillermomolina.protos.semantic.ast.CanonicalLiteral;
import com.guillermomolina.protos.semantic.ast.CanonicalLookup;
import com.guillermomolina.protos.semantic.ast.CanonicalSend;
import com.guillermomolina.protos.source.SourceSpan;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * I119 / D211: contextual {@code if} canonicalizes to ordinary
 * {@code ifTrue} / {@code ifTrueIfFalse} sends whose receiver is the eagerly
 * evaluated condition itself, never a synthesized condition Closure.
 */
class ProtosI119ContextualIfCanonicalizationTest {
    private final Canonicalizer canonicalizer = new Canonicalizer();

    @Test
    void singleArmLowersToOrdinaryIfTrueSendOnTheConditionItself() {
        String source = "if(n > 1) { n }";
        CanonicalSend send =
                assertInstanceOf(CanonicalSend.class, canonicalizeOnly(source));

        assertEquals("ifTrue", send.message());
        assertEquals(new SourceSpan(0, source.length()), send.span());

        CanonicalSend condition =
                assertInstanceOf(CanonicalSend.class, send.receiver());
        assertEquals(">", condition.message());
        assertEquals(new SourceSpan(3, 8), condition.span());
        assertEquals(
                "n",
                assertInstanceOf(CanonicalLookup.class, condition.receiver()).name());

        assertEquals(1, send.arguments().size());
        CanonicalClosure trueBranch =
                assertInstanceOf(CanonicalClosure.class, send.arguments().get(0));
        assertEquals(0, trueBranch.parameters().size());
        assertEquals(new SourceSpan(10, source.length()), trueBranch.span());
    }

    @Test
    void twoArmLowersToOrdinaryIfTrueIfFalseSendWithTrueThenFalseBranch() {
        String source = "if(flag) { 1 } else { 2 }";
        CanonicalSend send =
                assertInstanceOf(CanonicalSend.class, canonicalizeOnly(source));

        assertEquals("ifTrueIfFalse", send.message());
        assertEquals(new SourceSpan(0, source.length()), send.span());
        assertEquals(
                "flag",
                assertInstanceOf(CanonicalLookup.class, send.receiver()).name());

        assertEquals(2, send.arguments().size());
        CanonicalClosure trueBranch =
                assertInstanceOf(CanonicalClosure.class, send.arguments().get(0));
        CanonicalClosure falseBranch =
                assertInstanceOf(CanonicalClosure.class, send.arguments().get(1));
        assertEquals(0, trueBranch.parameters().size());
        assertEquals(0, falseBranch.parameters().size());
        assertEquals(new SourceSpan(9, 14), trueBranch.span());
        assertEquals(new SourceSpan(20, source.length()), falseBranch.span());
        assertEquals(
                "1",
                assertInstanceOf(
                                CanonicalLiteral.class,
                                trueBranch.body().expressions().get(0))
                        .value());
        assertEquals(
                "2",
                assertInstanceOf(
                                CanonicalLiteral.class,
                                falseBranch.body().expressions().get(0))
                        .value());
    }

    @Test
    void conditionIsNeverWrappedInAClosure() {
        CanonicalSend literal =
                assertInstanceOf(CanonicalSend.class, canonicalizeOnly("if(true) { 1 }"));
        assertEquals(
                CanonicalLiteral.Kind.TRUE,
                assertInstanceOf(CanonicalLiteral.class, literal.receiver()).kind());

        CanonicalSend call = assertInstanceOf(
                CanonicalSend.class, canonicalizeOnly("if(next()) { 1 } else { 2 }"));
        assertInstanceOf(CanonicalCall.class, call.receiver());

        // A Closure written as the condition stays the one written Closure.
        CanonicalSend closure = assertInstanceOf(
                CanonicalSend.class, canonicalizeOnly("if(() => { c }) { 1 }"));
        CanonicalClosure written =
                assertInstanceOf(CanonicalClosure.class, closure.receiver());
        assertEquals(
                "c",
                assertInstanceOf(
                                CanonicalLookup.class,
                                written.body().expressions().get(0))
                        .name());
    }

    @Test
    void groupedAndMemberIfRemainOrdinaryCanonicalCalls() {
        CanonicalCall grouped = assertInstanceOf(
                CanonicalCall.class, canonicalizeOnly("(if)(flag) { 1 }"));
        assertEquals(2, grouped.arguments().size());

        CanonicalSend member = assertInstanceOf(
                CanonicalSend.class, canonicalizeOnly("object.if(flag) { 1 }"));
        assertEquals("if", member.message());
        assertEquals(2, member.arguments().size());
    }

    @Test
    void noConditionalCanonicalNodeExists() {
        assertFalse(
                Arrays.stream(CanonicalExpression.class.getPermittedSubclasses())
                        .map(Class::getSimpleName)
                        .anyMatch(name -> name.contains("If")
                                || name.contains("Conditional")),
                "contextual if must lower to ordinary canonical sends");
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
