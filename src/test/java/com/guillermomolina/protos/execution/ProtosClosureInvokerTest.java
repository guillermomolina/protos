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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.parser.ProtosParser;
import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosReturnHome;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.guillermomolina.protos.semantic.Canonicalizer;
import com.guillermomolina.protos.semantic.ast.CanonicalExpression;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProtosClosureInvokerTest {
    @Test
    void invokesSelectedClosureThroughBindingAndBody() {
        ProtosClosureValue closure = closure("(x) => x");

        Object supplied =
                new ProtosObjectValue(ProtosObjectValue.rootObject());

        Object result =
                ProtosClosureInvoker.invoke(closure, List.of(supplied));

        assertSame(supplied, result);
    }

    @Test
    void completesOwnedReturnHomeAfterNormalInvocation() {
        ProtosClosureValue closure = closure("() => null");

        ProtosActivation activation =
                ProtosActivation.forClosureInvocation(closure, List.of());
        ProtosReturnHome home = activation.returnHome().orElseThrow();
        assertTrue(home.isActive());

        ProtosClosureExecutionPlan plan =
                closure.executionPlan().orElseThrow();
        try {
            plan.bind(activation);
            plan.executeBody(activation);
        } finally {
            if (activation.ownsReturnHome() && home.isActive()) {
                home.complete();
            }
        }

        assertFalse(home.isActive());
    }

    @Test
    void bindingFailureDoesNotLeaveOwnedHomeSemanticallyReusable() {
        ProtosClosureValue closure = closure("(required) => required");

        ProtosSignalException failure =
                assertThrows(
                        ProtosSignalException.class,
                        () -> ProtosClosureInvoker.invoke(closure, List.of()));

        assertSame(
                closure.prelude().orElseThrow().errorPrototype(),
                failure.error().parent().orElseThrow());
    }

    @Test
    void nestedInvocationDoesNotCompleteCapturedReturnHome() {
        ProtosClosureValue outer = closure("() => null");
        ProtosReturnHome capturedHome = new ProtosReturnHome();
        ProtosClosureValue nested =
                new ProtosClosureValue(
                        outer.definition(),
                        outer.capturedLexicalContexts(),
                        outer.capturedReceiver(),
                        outer.methodHome().orElse(null),
                        capturedHome,
                        outer.prelude().orElseThrow(),
                        outer.executionPlan().orElseThrow());

        ProtosClosureInvoker.invoke(nested, List.of());

        assertTrue(capturedHome.isActive());
    }

    private static ProtosClosureValue closure(String source) {
        CanonicalExpression canonical =
                new Canonicalizer()
                        .canonicalize(
                                new ProtosParser(source).parseProgram());
        ProtosExpressionNode lowered =
                new CanonicalToTruffleLowerer().lower(canonical);
        return assertInstanceOf(
                ProtosClosureValue.class,
                ProtosExecution.createCallTarget(lowered)
                        .call(moduleActivation()));
    }

    private static ProtosActivation moduleActivation() {
        ProtosObjectValue contextPrototype =
                new ProtosObjectValue(ProtosObjectValue.rootObject());
        ProtosObjectValue bindings = new ProtosObjectValue(contextPrototype);
        bindings.createLocalSlot("Context", contextPrototype);
        bindings.createLocalSlot(
                "Error", new ProtosObjectValue(ProtosObjectValue.rootObject()));
        bindings.createLocalSlot(
                "Array", new ProtosObjectValue(ProtosObjectValue.rootObject()));
        bindings.freeze();
        ProtosPrelude prelude = new ProtosPrelude(bindings, contextPrototype);
        return prelude.newModuleActivation();
    }
}
