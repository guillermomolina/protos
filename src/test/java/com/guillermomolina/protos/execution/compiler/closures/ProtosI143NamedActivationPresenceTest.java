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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.execution.ProtosNamedArgumentAssociation.Association;
import com.guillermomolina.protos.execution.ProtosNamedArgumentAssociation.NamedValue;
import com.guillermomolina.protos.parser.ProtosParser;
import com.guillermomolina.protos.parser.ast.SurfaceSequence;
import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosArrayValue;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.semantic.Canonicalizer;
import com.guillermomolina.protos.semantic.ast.CanonicalClosure;
import com.guillermomolina.protos.semantic.ast.CanonicalParameter;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * I143: parameter presence in a selected source invocation.
 *
 * <p>Named call-site execution is not enabled by this test.
 */
class ProtosI143NamedActivationPresenceTest {

    @Test
    void missingPositionsDoNotSuppressTheirDefaults() {
        CanonicalClosure definition =
                definition("(a: 10, b: 20, c: 30) => c");

        Association associated = associate(
                definition,
                List.of(),
                List.of(new NamedValue("c", 99)));

        assertEquals(
                List.of(
                        ProtosNullValue.INSTANCE,
                        ProtosNullValue.INSTANCE,
                        99),
                associated.boundSuppliedArgumentsForRuntime());

        ProtosActivation activation =
                activation(definition, associated);

        assertFalse(has(activation, 0));
        assertFalse(has(activation, 1));
        assertTrue(has(activation, 2));

        assertSame(
                ProtosNullValue.INSTANCE,
                ProtosBytecodeRootNode.LoadClosureArgument.perform(
                        activation, 0));

        assertEquals(
                99,
                ProtosBytecodeRootNode.LoadClosureArgument.perform(
                        activation, 2));
    }

    @Test
    void explicitNullSuppressesDefault() {
        CanonicalClosure definition =
                definition("(a: 10, b: 20) => a");

        Association associated = associate(
                definition,
                List.of(),
                List.of(new NamedValue(
                        "a", ProtosNullValue.INSTANCE)));

        ProtosActivation activation =
                activation(definition, associated);

        assertTrue(has(activation, 0));
        assertFalse(has(activation, 1));

        assertSame(
                ProtosNullValue.INSTANCE,
                ProtosBytecodeRootNode.LoadClosureArgument.perform(
                        activation, 0));
    }

    @Test
    void positionalOrdinalsDoNotFillNamedHoles() {
        CanonicalClosure definition =
                definition("(a, b, c) => c");

        Association associated = associate(
                definition,
                List.of(1),
                List.of(new NamedValue("c", 30)));

        ProtosActivation activation =
                activation(definition, associated);

        assertTrue(has(activation, 0));
        assertFalse(has(activation, 1));
        assertTrue(has(activation, 2));

        assertEquals(
                List.of(1, ProtosNullValue.INSTANCE, 30),
                activation.suppliedArgumentsForRuntime());
    }

    @Test
    void restReceivesOnlyExcessPositionals() {
        CanonicalClosure definition =
                definition("(a, b, ...rest) => rest");

        Association associated = associate(
                definition,
                List.of(1, 2, 3, 4),
                List.of(new NamedValue("b", 99)));

        // Named assignment to b collides with positional ordinal 1.
        org.junit.jupiter.api.Assertions.assertNull(associated);

        associated = associate(
                definition,
                List.of(1, 2, 3, 4),
                List.of(new NamedValue("a", 99)));

        org.junit.jupiter.api.Assertions.assertNull(associated);
    }

    @Test
    void noncollidingNamedBindingLeavesRestEmpty() {
        CanonicalClosure definition =
                definition("(a, b, c, ...rest) => rest");

        Association associated = associate(
                definition,
                List.of(1, 2),
                List.of(new NamedValue("c", 30)));

        org.junit.jupiter.api.Assertions.assertNotNull(associated);

        ProtosActivation activation =
                activation(definition, associated);

        assertEquals(
                List.of(1, 2, 30),
                activation.suppliedArgumentsForRuntime());

        assertTrue(has(activation, 0));
        assertTrue(has(activation, 1));
        assertTrue(has(activation, 2));

        assertEquals(
                List.of(),
                ((ProtosArrayValue)
                        ProtosFrameLocalBindings.closureRestArray(
                                activation, 3)).indexedSnapshot());
    }

    @Test
    void excessPositionalsPopulateRestWithoutNamedArguments() {
        CanonicalClosure definition =
                definition("(a, b, c, ...rest) => rest");

        ProtosActivation activation =
                plainActivation(
                        definition,
                        List.of(1, 2, 3, 4, 5));

        assertEquals(
                List.of(4, 5),
                ((ProtosArrayValue)
                        ProtosFrameLocalBindings.closureRestArray(
                                activation, 3)).indexedSnapshot());
    }

    @Test
    void namedFormalCollidesWhenPositionalRestIsNonempty() {
        CanonicalClosure definition =
                definition("(a, b, c, ...rest) => rest");

        // A nonempty rest requires all ordinary positional
        // ordinals to be occupied. Therefore c is already bound.
        Association associated = associate(
                definition,
                List.of(1, 2, 3, 4, 5),
                List.of(new NamedValue("c", 30)));

        org.junit.jupiter.api.Assertions.assertNull(associated);
    }

    @Test
    void positionalOnlyKeepsCountSemantics() {
        CanonicalClosure definition =
                definition("(a, b, c) => c");

        ProtosActivation activation =
                plainActivation(definition, List.of(1, 2));

        assertTrue(has(activation, 0));
        assertTrue(has(activation, 1));
        assertFalse(has(activation, 2));

        assertSame(
                ProtosNullValue.INSTANCE,
                ProtosBytecodeRootNode.LoadClosureArgument.perform(
                        activation, 2));
    }

    @Test
    void callerCannotMutateInstalledPresence() {
        CanonicalClosure definition =
                definition("(a, b) => a");

        ProtosActivation activation =
                plainActivation(
                        definition,
                        List.of(1, ProtosNullValue.INSTANCE));

        boolean[] presence = {true, false};
        activation.installNamedParameterPresenceForRuntime(
                presence);

        presence[0] = false;
        presence[1] = true;

        assertTrue(has(activation, 0));
        assertFalse(has(activation, 1));

        assertThrows(
                IllegalStateException.class,
                () -> activation.installNamedParameterPresenceForRuntime(
                        new boolean[] {true, true}));
    }

    @Test
    void oversizedPresenceCannotBeInstalled() {
        CanonicalClosure definition =
                definition("(a) => a");

        ProtosActivation activation =
                plainActivation(definition, List.of(1));

        assertThrows(
                IllegalArgumentException.class,
                () -> activation.installNamedParameterPresenceForRuntime(
                        new boolean[] {true, true}));

        assertTrue(has(activation, 0));
    }

    private static boolean has(
            ProtosActivation activation, int index) {
        return ProtosBytecodeRootNode.HasClosureArgument.perform(
                activation, index);
    }

    private static Association associate(
            CanonicalClosure definition,
            List<?> positional,
            List<NamedValue> named) {
        return ProtosNamedArgumentAssociation.associate(
                definition.parameters(), positional, named);
    }

    private static ProtosActivation activation(
            CanonicalClosure definition,
            Association associated) {
        ProtosActivation activation = plainActivation(
                definition,
                associated.boundSuppliedArgumentsForRuntime());

        activation.installNamedParameterPresenceForRuntime(
                associated.parameterPresenceForRuntime());

        return activation;
    }

    private static ProtosActivation plainActivation(
            CanonicalClosure definition,
            List<?> supplied) {
        ProtosObjectValue contextPrototype =
                new ProtosObjectValue(
                        ProtosObjectValue.rootObject());
        ProtosObjectValue bindings =
                new ProtosObjectValue(contextPrototype);

        bindings.createLocalSlot("Context", contextPrototype);
        bindings.createLocalSlot(
                "Error",
                new ProtosObjectValue(
                        ProtosObjectValue.rootObject()));
        bindings.createLocalSlot(
                "Array",
                new ProtosObjectValue(
                        ProtosObjectValue.rootObject()));
        bindings.freeze();

        ProtosPrelude prelude =
                new ProtosPrelude(
                        bindings, contextPrototype);

        ProtosClosureValue closure =
                new ProtosClosureValue(
                        definition,
                        List.of(),
                        ProtosObjectValue.rootObject());

        return ProtosActivation.forClosureInvocation(
                closure, supplied, prelude);
    }

    private static CanonicalClosure definition(String source) {
        SurfaceSequence program =
                new ProtosParser(source).parseProgram();

        return (CanonicalClosure)
                new Canonicalizer().canonicalize(
                        program.expressions().get(0));
    }
}
