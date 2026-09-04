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

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.parser.ProtosParser;
import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.semantic.Canonicalizer;
import com.guillermomolina.protos.semantic.ast.CanonicalExpression;
import java.util.List;
import org.junit.jupiter.api.Test;

class CanonicalObjectExecutionTest {
    private final Canonicalizer canonicalizer = new Canonicalizer();
    private final CanonicalToTruffleLowerer lowerer =
            new CanonicalToTruffleLowerer();

    @Test
    void bareObjectUsesObjectAsParentAndReturnsConstructedObject() {
        ProtosObjectValue enclosingContext =
                new ProtosObjectValue(ProtosObjectValue.rootObject());
        ProtosActivation activation =
                new ProtosActivation(
                        enclosingContext,
                        List.of(),
                        new ProtosObjectValue(ProtosObjectValue.rootObject()));

        ProtosObjectValue object =
                (ProtosObjectValue) execute("{ local: true }", activation);

        assertSame(
                ProtosObjectValue.rootObject(),
                object.parent().orElseThrow());
        assertSame(
                ProtosBooleanValue.TRUE,
                object.readLocalSlot("local").orElseThrow());
    }

    @Test
    void explicitParentIsEvaluatedBeforeBodyAndBecomesImmutableParent() {
        ProtosObjectValue root = ProtosObjectValue.rootObject();
        ProtosObjectValue parent = new ProtosObjectValue(root);
        ProtosObjectValue enclosingContext = new ProtosObjectValue(root);
        enclosingContext.createLocalSlot("parent", parent);
        ProtosActivation activation =
                new ProtosActivation(
                        enclosingContext,
                        List.of(),
                        new ProtosObjectValue(root));

        ProtosObjectValue object =
                (ProtosObjectValue) execute("parent { local: false }", activation);

        assertSame(parent, object.parent().orElseThrow());
        assertSame(
                ProtosBooleanValue.FALSE,
                object.readLocalSlot("local").orElseThrow());
    }

    @Test
    void closureDeclaredInObjectBodyDoesNotCaptureConstructedObjectLexically() {
        ProtosObjectValue root = ProtosObjectValue.rootObject();
        ProtosObjectValue enclosingContext = new ProtosObjectValue(root);
        ProtosActivation activation =
                new ProtosActivation(
                        enclosingContext,
                        List.of(),
                        new ProtosObjectValue(root));

        ProtosObjectValue object =
                (ProtosObjectValue) execute("{ method: () => {} }", activation);
        ProtosClosureValue closure =
                (ProtosClosureValue) object.readLocalSlot("method").orElseThrow();

        assertSame(
                enclosingContext,
                closure.capturedLexicalContexts().get(0));
        assertTrue(
                closure.capturedLexicalContexts().stream()
                        .noneMatch(candidate -> candidate == object));
        assertSame(object, closure.capturedReceiver());
    }

    private Object execute(
            String source,
            ProtosActivation activation) {
        CanonicalExpression expression =
                canonicalizer.canonicalize(
                        new ProtosParser(source)
                                .parseProgram()
                                .expressions()
                                .get(0));
        return ProtosExecution.createCallTarget(lowerer.lower(expression))
                .call(activation);
    }
}
