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
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.parser.ProtosParser;
import com.guillermomolina.protos.parser.ast.SurfaceSequence;
import com.guillermomolina.protos.semantic.ast.CanonicalCall;
import com.guillermomolina.protos.semantic.ast.CanonicalExpression;
import com.guillermomolina.protos.semantic.ast.CanonicalLiteral;
import com.guillermomolina.protos.semantic.ast.CanonicalLookup;
import com.guillermomolina.protos.semantic.ast.CanonicalMatch;
import com.guillermomolina.protos.semantic.ast.CanonicalMatchPattern;
import com.guillermomolina.protos.semantic.ast.CanonicalSend;
import org.junit.jupiter.api.Test;

class CanonicalizerMatchTest {
    private final Canonicalizer canonicalizer = new Canonicalizer();

    @Test
    void lowersCompleteMatchSurfaceIntoBackendNeutralCanonicalIr() {
        CanonicalMatch match =
                assertInstanceOf(
                        CanonicalMatch.class,
                        canonicalizeOnly(
                                "subject + 1 match {\n"
                                        + "case @whole: ([1, @x] | [2, @x]) when check(x) => use(x)\n"
                                        + "case %{ \"name\": @name, ...@rest } => { name\n rest }\n"
                                        + "case matcher captures(first, ...tail) => first\n"
                                        + "}"));

        CanonicalSend subject = assertInstanceOf(CanonicalSend.class, match.subject());
        assertEquals("+", subject.message());
        assertEquals(3, match.arms().size());

        CanonicalMatch.Arm firstArm = match.arms().get(0);
        CanonicalMatchPattern.Alias alias =
                assertInstanceOf(CanonicalMatchPattern.Alias.class, firstArm.pattern());
        assertEquals("whole", alias.name());

        CanonicalMatchPattern.Or alternatives =
                assertInstanceOf(CanonicalMatchPattern.Or.class, alias.pattern());
        assertEquals(2, alternatives.alternatives().size());

        CanonicalMatchPattern.ArrayPattern firstAlternative =
                assertInstanceOf(
                        CanonicalMatchPattern.ArrayPattern.class,
                        alternatives.alternatives().get(0));
        assertEquals(2, firstAlternative.prefix().size());
        assertFalse(firstAlternative.remainder().isPresent());
        CanonicalMatchPattern.Binder x =
                assertInstanceOf(
                        CanonicalMatchPattern.Binder.class,
                        firstAlternative.prefix().get(1));
        assertEquals("x", x.name());

        CanonicalCall guard =
                assertInstanceOf(CanonicalCall.class, firstArm.guard().orElseThrow());
        assertInstanceOf(CanonicalLookup.class, guard.receiver());
        assertEquals(1, guard.arguments().size());
        assertEquals(1, firstArm.body().expressions().size());
        assertInstanceOf(CanonicalCall.class, firstArm.body().expressions().get(0));

        CanonicalMatchPattern.MapPattern map =
                assertInstanceOf(
                        CanonicalMatchPattern.MapPattern.class,
                        match.arms().get(1).pattern());
        assertFalse(map.exact());
        assertEquals(1, map.entries().size());
        CanonicalLiteral key =
                assertInstanceOf(CanonicalLiteral.class, map.entries().get(0).key());
        assertEquals("name", key.value());
        assertTrue(map.remainder().orElseThrow().pattern().isPresent());
        assertEquals(2, match.arms().get(1).body().expressions().size());

        CanonicalMatchPattern.Value opaque =
                assertInstanceOf(
                        CanonicalMatchPattern.Value.class,
                        match.arms().get(2).pattern());
        assertInstanceOf(CanonicalLookup.class, opaque.matcher());
        CanonicalMatchPattern.CaptureInterface capture =
                opaque.captureInterface().orElseThrow();
        assertEquals(java.util.List.of("first"), capture.requiredNames());
        assertEquals("tail", capture.restName().orElseThrow());
        assertTrue(capture.variableArity());
    }

    @Test
    void recursivelyCanonicalizesMatcherAndMapKeyExpressions() {
        CanonicalMatch match =
                assertInstanceOf(
                        CanonicalMatch.class,
                        canonicalizeOnly(
                                "subject match {\n"
                                        + "case patternFactory() => first\n"
                                        + "case %{ keyFactory(): @value } => value\n"
                                        + "}"));

        CanonicalMatchPattern.Value matcher =
                assertInstanceOf(
                        CanonicalMatchPattern.Value.class,
                        match.arms().get(0).pattern());
        assertInstanceOf(CanonicalCall.class, matcher.matcher());

        CanonicalMatchPattern.MapPattern map =
                assertInstanceOf(
                        CanonicalMatchPattern.MapPattern.class,
                        match.arms().get(1).pattern());
        assertInstanceOf(CanonicalCall.class, map.entries().get(0).key());
    }

    private CanonicalExpression canonicalizeOnly(String source) {
        SurfaceSequence program = new ProtosParser(source).parseProgram();
        assertEquals(1, program.expressions().size());
        return canonicalizer.canonicalize(program.expressions().get(0));
    }
}
