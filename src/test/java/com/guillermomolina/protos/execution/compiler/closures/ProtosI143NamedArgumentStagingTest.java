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

package com.guillermomolina.protos.execution;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.parser.ProtosParser;
import com.guillermomolina.protos.parser.ast.SurfaceSequence;
import com.guillermomolina.protos.semantic.Canonicalizer;
import com.guillermomolina.protos.semantic.ast.CanonicalCall;
import com.guillermomolina.protos.semantic.ast.CanonicalExpression;
import com.guillermomolina.protos.semantic.ast.CanonicalNamedArgument;
import com.guillermomolina.protos.semantic.ast.CanonicalSpread;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * I143: canonical argument staging prerequisites.
 *
 * <p>Executable named dispatch remains disabled until the selected-call
 * transport and actual parameter binding are installed.
 */
class ProtosI143NamedArgumentStagingTest {

    @Test
    void namedArgumentUnwrapsToItsValueExpression() throws Exception {
        CanonicalCall call = call("f(x: g(1))");

        CanonicalNamedArgument named = assertInstanceOf(
                CanonicalNamedArgument.class,
                call.arguments().get(0));

        CanonicalExpression value = unwrap(named);

        assertSame(named.expression(), value);
        assertInstanceOf(CanonicalCall.class, value);
    }

    @Test
    void spreadArgumentUnwrapsAsBefore() throws Exception {
        CanonicalCall call = call("f(...items)");

        CanonicalSpread spread = assertInstanceOf(
                CanonicalSpread.class,
                call.arguments().get(0));

        assertSame(spread.expression(), unwrap(spread));
    }

    @Test
    void ordinaryPositionalValueIsNotWrapped() throws Exception {
        CanonicalCall call = call("f(1)");

        CanonicalExpression argument = call.arguments().get(0);

        assertSame(argument, unwrap(argument));
    }

    @Test
    void nestedNamedCallIsRecognizedAsComposed() throws Exception {
        CanonicalCall call = call("f(x: g(1))");

        assertTrue(hasComposedArgument(call.arguments()));
    }

    @Test
    void simpleNamedLiteralIsNotFalselyComposed() throws Exception {
        CanonicalCall call = call("f(x: 1)");

        assertFalse(hasComposedArgument(call.arguments()));
    }

    @Test
    void mixedSpreadAndNamedValuesRetainSourceOrder() throws Exception {
        CanonicalCall call = call("f(x: g(1), ...items, 3, y: 4)");

        List<CanonicalExpression> arguments = call.arguments();

        assertInstanceOf(
                CanonicalNamedArgument.class,
                arguments.get(0));

        assertInstanceOf(
                CanonicalSpread.class,
                arguments.get(1));

        assertInstanceOf(
                CanonicalNamedArgument.class,
                arguments.get(3));

        assertInstanceOf(
                CanonicalCall.class,
                unwrap(arguments.get(0)));

        assertSame(arguments.get(2), unwrap(arguments.get(2)));
        assertTrue(hasComposedArgument(arguments));
    }

    private static CanonicalCall call(String source) {
        SurfaceSequence surface =
                new ProtosParser(source).parseProgram();

        return assertInstanceOf(
                CanonicalCall.class,
                new Canonicalizer().canonicalize(
                        surface.expressions().get(0)));
    }

    private static CanonicalExpression unwrap(
            CanonicalExpression argument) throws Exception {
        Method method = CanonicalToBytecodeLowerer.class
                .getDeclaredMethod(
                        "suppliedArgumentExpression",
                        CanonicalExpression.class);
        method.setAccessible(true);

        return (CanonicalExpression) method.invoke(null, argument);
    }

    /*
     * The lowering classifies each supplied value (unwrapped from its named
     * or spread form) with requiresComposedInvocation; an argument list is
     * composed when any of its supplied values is.
     */
    private static boolean hasComposedArgument(
            List<CanonicalExpression> arguments) throws Exception {
        Method method = CanonicalToBytecodeLowerer.class
                .getDeclaredMethod(
                        "requiresComposedInvocation",
                        CanonicalExpression.class);
        method.setAccessible(true);

        for (CanonicalExpression argument : arguments) {
            if ((Boolean) method.invoke(null, unwrap(argument))) {
                return true;
            }
        }
        return false;
    }
}
