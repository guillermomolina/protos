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

package com.guillermomolina.protos.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.guillermomolina.protos.parser.ast.SurfaceArgument;
import com.guillermomolina.protos.parser.ast.SurfaceCall;
import com.guillermomolina.protos.parser.ast.SurfaceClosure;
import com.guillermomolina.protos.parser.ast.SurfaceExpression;
import com.guillermomolina.protos.parser.ast.SurfaceObject;
import com.guillermomolina.protos.parser.ast.SurfaceSequence;
import org.junit.jupiter.api.Test;

class ProtosParserTrailingClosureTest {
    @Test
    void appendsParameterlessBracedClosureAsFinalCallArgument() {
        SurfaceCall call = assertInstanceOf(SurfaceCall.class, only("foo(a) { body }"));

        assertEquals(2, call.arguments().size());
        SurfaceArgument trailing = call.arguments().get(1);
        assertFalse(trailing.spread());

        SurfaceClosure closure =
                assertInstanceOf(SurfaceClosure.class, trailing.expression());
        assertEquals(0, closure.parameters().size());
        assertFalse(closure.expressionBody());
        assertEquals(1, closure.body().expressions().size());
    }

    @Test
    void parsesMultiexpressionTrailingClosureBody() {
        SurfaceCall call =
                assertInstanceOf(SurfaceCall.class, only("foo() { first\nsecond }"));
        SurfaceClosure closure =
                assertInstanceOf(SurfaceClosure.class, call.arguments().get(0).expression());

        assertEquals(2, closure.body().expressions().size());
    }

    @Test
    void newlineAfterCompletedCallDoesNotAttachTrailingClosure() {
        SurfaceSequence program = new ProtosParser("foo()\n{ body }").parseProgram();

        assertEquals(2, program.expressions().size());
        assertInstanceOf(SurfaceCall.class, program.expressions().get(0));
        assertInstanceOf(SurfaceObject.class, program.expressions().get(1));
    }

    @Test
    void atMostOneTrailingClosureCanAttachToACallSuffix() {
        assertThrows(ParseError.class, () -> only("foo() { first } { second }"));
    }

    @Test
    void trailingClosureRemainsUnavailableOnSuperMessageArgumentList() {
        assertThrows(ParseError.class, () -> only("super.foo() { body }"));
    }

    private SurfaceExpression only(String source) {
        SurfaceSequence program = new ProtosParser(source).parseProgram();
        assertEquals(1, program.expressions().size());
        return program.expressions().get(0);
    }
}
