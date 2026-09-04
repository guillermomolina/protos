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
import com.guillermomolina.protos.runtime.ProtosExecutionContext;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.semantic.ast.CanonicalClosure;
import com.guillermomolina.protos.semantic.ast.CanonicalSequence;
import com.guillermomolina.protos.source.SourceSpan;
import java.util.List;
import org.junit.jupiter.api.Test;

class CanonicalClosureMaterializationTest {
    private final CanonicalToTruffleLowerer lowerer =
            new CanonicalToTruffleLowerer();

    @Test
    void closureCapturesCurrentThenExistingLexicalContextsByReference() {
        ProtosObjectValue root = ProtosObjectValue.rootObject();
        ProtosObjectValue current = new ProtosObjectValue(root);
        ProtosObjectValue outer = new ProtosObjectValue(root);
        ProtosObjectValue receiver = new ProtosObjectValue(root);
        ProtosExecutionContext activation =
                new ProtosExecutionContext(
                        current,
                        List.of(outer),
                        receiver);

        ProtosClosureValue closure =
                (ProtosClosureValue)
                        ProtosExecution.createCallTarget(
                                        lowerer.lower(closure()))
                                .call(activation);

        assertSame(current, closure.capturedLexicalContexts().get(0));
        assertSame(outer, closure.capturedLexicalContexts().get(1));
        assertSame(receiver, closure.capturedReceiver());
        assertTrue(closure.methodHome().isEmpty());
    }

    @Test
    void evaluatingClosureLiteralCreatesFreshClosureIdentity() {
        ProtosObjectValue root = ProtosObjectValue.rootObject();
        ProtosExecutionContext activation =
                new ProtosExecutionContext(
                        new ProtosObjectValue(root),
                        List.of(),
                        new ProtosObjectValue(root));

        var target =
                ProtosExecution.createCallTarget(
                        lowerer.lower(closure()));

        Object first = target.call(activation);
        Object second = target.call(activation);

        assertNotSame(first, second);
    }

    @Test
    void closureMaterializationDoesNotExecuteOrLowerItsBody() {
        ProtosObjectValue root = ProtosObjectValue.rootObject();
        ProtosExecutionContext activation =
                new ProtosExecutionContext(
                        new ProtosObjectValue(root),
                        List.of(),
                        new ProtosObjectValue(root));

        CanonicalClosure definition = closure();

        ProtosClosureValue value =
                (ProtosClosureValue)
                        ProtosExecution.createCallTarget(
                                        lowerer.lower(definition))
                                .call(activation);

        assertSame(definition, value.definition());
    }

    private CanonicalClosure closure() {
        SourceSpan span = new SourceSpan(0, 2);
        return new CanonicalClosure(
                List.of(),
                new CanonicalSequence(List.of(), span),
                span);
    }
}
