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

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import java.util.List;
import org.junit.jupiter.api.Test;

class CanonicalClosureMaterializationTest {
    @Test
    void closureCapturesCurrentThenExistingLexicalContextsByReference() {
        ProtosObjectValue root = ProtosObjectValue.rootObject();
        ProtosObjectValue current = new ProtosObjectValue(root);
        ProtosObjectValue outer = new ProtosObjectValue(root);
        ProtosObjectValue receiver = new ProtosObjectValue(root);
        ProtosActivation activation =
                new ProtosActivation(
                        current,
                        List.of(outer),
                        receiver);

        ProtosClosureValue closure =
                (ProtosClosureValue)
                        ProtosTestExecutionSupport.evaluate(
                                "closure-materialization-capture.protos",
                                "() => null",
                                activation);

        assertSame(current, closure.capturedLexicalContexts().get(0));
        assertSame(outer, closure.capturedLexicalContexts().get(1));
        assertSame(receiver, closure.capturedReceiver());
        assertTrue(closure.methodHome().isEmpty());
    }

    @Test
    void closureCreatedDuringObjectConstructionSkipsConstructionObject() {
        ProtosObjectValue root = ProtosObjectValue.rootObject();
        ProtosObjectValue lexical = new ProtosObjectValue(root);
        ProtosActivation enclosing =
                new ProtosActivation(
                        lexical,
                        List.of(),
                        new ProtosObjectValue(root));
        ProtosObjectValue object = new ProtosObjectValue(root);
        ProtosActivation construction =
                ProtosActivation.forObjectConstruction(
                        object,
                        enclosing);

        ProtosClosureValue closure =
                (ProtosClosureValue)
                        ProtosTestExecutionSupport.evaluate(
                                "closure-materialization-construction.protos",
                                "() => null",
                                construction);

        assertSame(
                lexical,
                closure.capturedLexicalContexts().get(0));
        assertTrue(
                closure.capturedLexicalContexts().stream()
                        .noneMatch(candidate -> candidate == object));
        assertSame(object, closure.capturedReceiver());
    }

    @Test
    void evaluatingClosureLiteralCreatesFreshClosureIdentity() {
        ProtosObjectValue root = ProtosObjectValue.rootObject();
        ProtosActivation activation =
                new ProtosActivation(
                        new ProtosObjectValue(root),
                        List.of(),
                        new ProtosObjectValue(root));

        Object first =
                ProtosTestExecutionSupport.evaluate(
                        "closure-materialization-fresh.protos",
                        "() => null",
                        activation);
        Object second =
                ProtosTestExecutionSupport.evaluate(
                        "closure-materialization-fresh.protos",
                        "() => null",
                        activation);

        assertNotSame(first, second);
    }

    @Test
    void closureMaterializationDoesNotExecuteItsBody() {
        ProtosObjectValue root = ProtosObjectValue.rootObject();
        ProtosActivation activation =
                new ProtosActivation(
                        new ProtosObjectValue(root),
                        List.of(),
                        new ProtosObjectValue(root));

        ProtosClosureValue closure =
                (ProtosClosureValue)
                        ProtosTestExecutionSupport.evaluate(
                                "closure-materialization-body.protos",
                                "() => missing",
                                activation);

        assertTrue(closure.definition() != null);
        assertTrue(
                closure.executionPlan()
                        .orElseThrow()
                        .isBytecodeBackendForRuntime());
    }
}
