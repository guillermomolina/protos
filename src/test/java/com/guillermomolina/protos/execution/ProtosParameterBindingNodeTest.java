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
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosArrayValue;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.guillermomolina.protos.semantic.ast.CanonicalClosure;
import com.guillermomolina.protos.semantic.ast.CanonicalLiteral;
import com.guillermomolina.protos.semantic.ast.CanonicalParameter;
import com.guillermomolina.protos.semantic.ast.CanonicalSequence;
import com.guillermomolina.protos.source.SourceSpan;
import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ProtosParameterBindingNodeTest {
    private static final SourceSpan SPAN = new SourceSpan(0, 0);

    @Test
    void bindsSuppliedValuesThenEvaluatesDefaultInRealActivation() {
        Object supplied = new ProtosObjectValue(ProtosObjectValue.rootObject());
        List<CanonicalParameter> parameters =
                List.of(
                        parameter("a"),
                        defaultedParameter("b"));
        ProtosActivation activation =
                activation(parameters, List.of(supplied));

        ProtosParameterBindingNode binder =
                new ProtosParameterBindingNode(
                        SPAN,
                        parameters,
                        new ProtosExpressionNode[] {
                            null,
                            new ProtosLookupNode(SPAN, "a")
                        });

        ProtosExecution.createCallTarget(binder).call(activation);

        assertSame(supplied, activation.context().readLocalSlot("a").orElseThrow());
        assertSame(supplied, activation.context().readLocalSlot("b").orElseThrow());
        ProtosArrayValue args = activation.arguments().orElseThrow();
        assertSame(supplied, args.indexedAt(BigInteger.ZERO));
        assertSame(BigInteger.ONE, args.indexedSize());
    }

    @Test
    void bindsRestAsDistinctFreshFrozenSuffixArray() {
        Object first = new ProtosObjectValue(ProtosObjectValue.rootObject());
        Object second = new ProtosObjectValue(ProtosObjectValue.rootObject());
        Object third = new ProtosObjectValue(ProtosObjectValue.rootObject());
        List<CanonicalParameter> parameters =
                List.of(parameter("head"), restParameter("tail"));
        ProtosActivation activation =
                activation(parameters, List.of(first, second, third));

        ProtosParameterBindingNode binder =
                new ProtosParameterBindingNode(
                        SPAN,
                        parameters,
                        new ProtosExpressionNode[] {null, null});

        ProtosExecution.createCallTarget(binder).call(activation);

        ProtosArrayValue args = activation.arguments().orElseThrow();
        ProtosArrayValue rest =
                (ProtosArrayValue)
                        activation.context().readLocalSlot("tail").orElseThrow();
        assertNotSame(args, rest);
        assertSame(ProtosObjectValue.MutationState.FROZEN, rest.mutationState());
        assertSame(second, rest.indexedAt(BigInteger.ZERO));
        assertSame(third, rest.indexedAt(BigInteger.ONE));
        assertSame(BigInteger.valueOf(2), rest.indexedSize());
    }

    @Test
    void missingRequiredParameterSignalsFreshGenericErrorAtThatPoint() {
        List<CanonicalParameter> parameters =
                List.of(parameter("a"), parameter("b"));
        ProtosActivation activation =
                activation(parameters, List.of(new ProtosObjectValue(
                        ProtosObjectValue.rootObject())));

        ProtosParameterBindingNode binder =
                new ProtosParameterBindingNode(
                        SPAN,
                        parameters,
                        new ProtosExpressionNode[] {null, null});

        ProtosSignalException failure =
                assertThrows(
                        ProtosSignalException.class,
                        () -> ProtosExecution.createCallTarget(binder).call(activation));

        assertSame(
                activation.prelude().orElseThrow().errorPrototype(),
                failure.error().parent().orElseThrow());
        assertFalse(activation.context().hasLocalSlot("b"));
    }

    @Test
    void excessArgumentsAreDetectedOnlyAfterDeclaredParametersBind() {
        Object first = new ProtosObjectValue(ProtosObjectValue.rootObject());
        Object excess = new ProtosObjectValue(ProtosObjectValue.rootObject());
        List<CanonicalParameter> parameters = List.of(parameter("a"));
        ProtosActivation activation = activation(parameters, List.of(first, excess));

        ProtosParameterBindingNode binder =
                new ProtosParameterBindingNode(
                        SPAN,
                        parameters,
                        new ProtosExpressionNode[] {null});

        assertThrows(
                ProtosSignalException.class,
                () -> ProtosExecution.createCallTarget(binder).call(activation));
        assertSame(first, activation.context().readLocalSlot("a").orElseThrow());
    }

    private static ProtosActivation activation(
            List<CanonicalParameter> parameters,
            List<?> supplied) {
        ProtosObjectValue contextPrototype =
                new ProtosObjectValue(ProtosObjectValue.rootObject());
        ProtosObjectValue errorPrototype =
                new ProtosObjectValue(ProtosObjectValue.rootObject());
        ProtosObjectValue arrayPrototype =
                new ProtosObjectValue(ProtosObjectValue.rootObject());
        ProtosObjectValue bindings = new ProtosObjectValue(contextPrototype);
        bindings.createLocalSlot("Context", contextPrototype);
        bindings.createLocalSlot("Error", errorPrototype);
        bindings.createLocalSlot("Array", arrayPrototype);
        bindings.freeze();
        ProtosPrelude prelude = new ProtosPrelude(bindings, contextPrototype);

        CanonicalClosure definition =
                new CanonicalClosure(
                        parameters,
                        new CanonicalSequence(List.of(), SPAN),
                        SPAN);
        ProtosClosureValue closure =
                new ProtosClosureValue(
                        definition,
                        List.of(),
                        new ProtosObjectValue(ProtosObjectValue.rootObject()),
                        null,
                        null,
                        prelude);
        return ProtosActivation.forClosureInvocation(closure, supplied);
    }

    private static CanonicalParameter parameter(String name) {
        return new CanonicalParameter(name, Optional.empty(), false, SPAN);
    }

    private static CanonicalParameter defaultedParameter(String name) {
        CanonicalLiteral defaultExpression =
                new CanonicalLiteral(CanonicalLiteral.Kind.NULL, "null", SPAN);
        return new CanonicalParameter(
                name, Optional.of(defaultExpression), false, SPAN);
    }

    private static CanonicalParameter restParameter(String name) {
        return new CanonicalParameter(name, Optional.empty(), true, SPAN);
    }
}
