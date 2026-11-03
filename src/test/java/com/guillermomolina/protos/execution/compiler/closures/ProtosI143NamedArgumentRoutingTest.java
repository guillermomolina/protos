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
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.parser.ProtosParser;
import com.guillermomolina.protos.parser.ast.SurfaceSequence;
import com.guillermomolina.protos.semantic.Canonicalizer;
import com.guillermomolina.protos.semantic.ast.CanonicalCall;
import com.guillermomolina.protos.semantic.ast.CanonicalExpression;
import com.guillermomolina.protos.semantic.ast.CanonicalSend;
import com.guillermomolina.protos.semantic.ast.CanonicalSuperSend;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;

/** I143: named argument routing before dynamic dispatch integration. */
class ProtosI143NamedArgumentRoutingTest {

    @Test
    void namedCallUsesVector() throws Exception {
        CanonicalCall call = assertInstanceOf(
                CanonicalCall.class, only("f(a: 1)"));
        assertTrue(named(call.arguments()));
        assertTrue(vector(call.arguments()));
        assertFalse(spread(call.arguments()));
    }

    @Test
    void namedMemberSendUsesVector() throws Exception {
        CanonicalSend send = assertInstanceOf(
                CanonicalSend.class, only("obj.run(a: 1, 2)"));
        assertTrue(named(send.arguments()));
        assertTrue(vector(send.arguments()));
    }

    @Test
    void namedSuperSendUsesVector() throws Exception {
        CanonicalSuperSend send = assertInstanceOf(
                CanonicalSuperSend.class, only("super.run(a: 1)"));
        assertTrue(named(send.arguments()));
        assertTrue(vector(send.arguments()));
    }

    @Test
    void namedArraySugarUsesVector() throws Exception {
        CanonicalCall call = assertInstanceOf(
                CanonicalCall.class, only("[a: 1]"));
        assertTrue(named(call.arguments()));
        assertTrue(vector(call.arguments()));
    }

    @Test
    void namedPositionalSpreadMixUsesVector() throws Exception {
        CanonicalCall call = assertInstanceOf(
                CanonicalCall.class,
                only("f(c: 3, 1, ...items, b: 2)"));
        assertTrue(named(call.arguments()));
        assertTrue(spread(call.arguments()));
        assertTrue(vector(call.arguments()));
    }

    @Test
    void positionalSpreadUsesExistingVector() throws Exception {
        CanonicalCall call = assertInstanceOf(
                CanonicalCall.class, only("f(1, ...items)"));
        assertFalse(named(call.arguments()));
        assertTrue(spread(call.arguments()));
        assertTrue(vector(call.arguments()));
    }

    @Test
    void positionalOnlyNeedsNoVector() throws Exception {
        CanonicalCall call = assertInstanceOf(
                CanonicalCall.class, only("f(1, 2)"));
        assertFalse(named(call.arguments()));
        assertFalse(spread(call.arguments()));
        assertFalse(vector(call.arguments()));
    }

    private static CanonicalExpression only(String source) {
        SurfaceSequence parsed =
                new ProtosParser(source).parseProgram();
        return new Canonicalizer().canonicalize(
                parsed.expressions().get(0));
    }

    private static boolean named(List<CanonicalExpression> args)
            throws Exception {
        return query("hasNamedArgument", args);
    }

    private static boolean spread(List<CanonicalExpression> args)
            throws Exception {
        return query("hasSpreadArgument", args);
    }

    private static boolean vector(List<CanonicalExpression> args)
            throws Exception {
        return query("hasVectorArgument", args);
    }

    private static boolean query(
            String methodName,
            List<CanonicalExpression> arguments)
            throws Exception {
        Method method = CanonicalToBytecodeLowerer.class
                .getDeclaredMethod(methodName, List.class);
        method.setAccessible(true);
        return (Boolean) method.invoke(null, arguments);
    }
}
