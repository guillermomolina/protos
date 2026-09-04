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

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosArrayValue;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.semantic.Canonicalizer;
import com.guillermomolina.protos.semantic.ast.CanonicalExpression;
import com.guillermomolina.protos.parser.ProtosParser;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProtosCallableLoweringTest {
    @Test
    void closureDefaultPlanCanObserveExactInvocationArgs() {
        CanonicalExpression canonical =
                new Canonicalizer()
                        .canonicalize(
                                new ProtosParser("(x = args) => x").parseProgram());
        ProtosExpressionNode lowered =
                new CanonicalToTruffleLowerer().lower(canonical);

        ProtosActivation creationActivation = moduleActivation();
        ProtosClosureValue closure =
                assertInstanceOf(
                        ProtosClosureValue.class,
                        ProtosExecution.createCallTarget(lowered)
                                .call(creationActivation));
        ProtosActivation invocation =
                ProtosActivation.forClosureInvocation(closure, List.of());
        ProtosClosureExecutionPlan plan =
                closure.executionPlan().orElseThrow();

        ProtosExecution.createCallTarget(plan.parameterBinding()).call(invocation);

        ProtosArrayValue args = invocation.arguments().orElseThrow();
        assertSame(args, invocation.context().readLocalSlot("x").orElseThrow());
    }

    @Test
    void closureBodyPlanCanObserveExactInvocationArgs() {
        CanonicalExpression canonical =
                new Canonicalizer()
                        .canonicalize(
                                new ProtosParser("() => args").parseProgram());
        ProtosExpressionNode lowered =
                new CanonicalToTruffleLowerer().lower(canonical);

        ProtosActivation creationActivation = moduleActivation();
        ProtosClosureValue closure =
                assertInstanceOf(
                        ProtosClosureValue.class,
                        ProtosExecution.createCallTarget(lowered)
                                .call(creationActivation));
        ProtosActivation invocation =
                ProtosActivation.forClosureInvocation(closure, List.of());
        ProtosClosureExecutionPlan plan =
                closure.executionPlan().orElseThrow();

        Object actual =
                ProtosExecution.createCallTarget(plan.body()).call(invocation);

        assertSame(invocation.arguments().orElseThrow(), actual);
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
