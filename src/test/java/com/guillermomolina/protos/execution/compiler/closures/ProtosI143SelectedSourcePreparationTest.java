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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.execution.ProtosPreparedCalls.OrdinarySourceCall;
import com.guillermomolina.protos.execution.ProtosPreparedCalls.PreparedArgumentVector;
import com.guillermomolina.protos.execution.ProtosPreparedCalls.PreparedClosureCall;
import com.guillermomolina.protos.parser.ProtosParser;
import com.guillermomolina.protos.parser.ast.SurfaceSequence;
import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.guillermomolina.protos.semantic.Canonicalizer;
import com.guillermomolina.protos.semantic.ast.CanonicalClosure;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import java.util.List;
import org.junit.jupiter.api.Test;

/** I143: selected source Closure activation and named binding. */
class ProtosI143SelectedSourcePreparationTest {

    @Test
    void directSourcePreservesAbsentDefaults() {
        ProtosActivation caller = moduleActivation();
        ProtosClosureValue selected =
                sourceClosure("(a: 10, b: 20, c: 30) => c");

        PreparedArgumentVector supplied = new PreparedArgumentVector();
        supplied.appendNamed("c", 99);

        ProtosActivation activation =
                ProtosNamedCallPreparation
                        .activationForSelectedDirectSource(
                                selected, caller, supplied);

        assertSame(selected.capturedReceiver(), activation.receiver());
        assertFalse(activation.hasSuppliedParameterForRuntime(0));
        assertFalse(activation.hasSuppliedParameterForRuntime(1));
        assertTrue(activation.hasSuppliedParameterForRuntime(2));
        assertEquals(
                List.of(
                        ProtosNullValue.INSTANCE,
                        ProtosNullValue.INSTANCE,
                        99),
                activation.suppliedArgumentsForRuntime());
    }

    @Test
    void selectedMethodPreservesActualReceiverAndHome() {
        ProtosActivation caller = moduleActivation();
        ProtosClosureValue selected =
                sourceClosure("(a, b: 20, c: 30) => c");

        ProtosObjectValue receiver =
                new ProtosObjectValue(
                        ProtosObjectValue.rootObject());
        ProtosObjectValue home =
                new ProtosObjectValue(
                        ProtosObjectValue.rootObject());

        PreparedArgumentVector supplied = new PreparedArgumentVector();
        supplied.append(1);
        supplied.appendNamed("c", 33);

        ProtosActivation activation =
                ProtosNamedCallPreparation
                        .activationForSelectedSourceMethod(
                                selected, receiver, home, caller, supplied);

        assertSame(receiver, activation.receiver());
        assertSame(home, activation.methodHome().orElseThrow());
        assertTrue(activation.hasSuppliedParameterForRuntime(0));
        assertFalse(activation.hasSuppliedParameterForRuntime(1));
        assertTrue(activation.hasSuppliedParameterForRuntime(2));
        assertEquals(
                List.of(1, ProtosNullValue.INSTANCE, 33),
                activation.suppliedArgumentsForRuntime());
    }

    @Test
    void explicitGuestNullIsPresent() {
        ProtosActivation caller = moduleActivation();
        ProtosClosureValue selected =
                sourceClosure("(a: 10, b: 20) => a");

        PreparedArgumentVector supplied = new PreparedArgumentVector();
        supplied.appendNamed("a", ProtosNullValue.INSTANCE);

        ProtosActivation activation =
                ProtosNamedCallPreparation
                        .activationForSelectedDirectSource(
                                selected, caller, supplied);

        assertTrue(activation.hasSuppliedParameterForRuntime(0));
        assertFalse(activation.hasSuppliedParameterForRuntime(1));
        assertSame(
                ProtosNullValue.INSTANCE,
                activation.suppliedArgumentsForRuntime().get(0));
    }

    @Test
    void collisionSignalsGuestErrorBeforeActivation() {
        ProtosActivation caller = moduleActivation();
        ProtosClosureValue selected =
                sourceClosure("(a, b) => a");

        PreparedArgumentVector supplied = new PreparedArgumentVector();
        supplied.append(1);
        supplied.appendNamed("a", 2);

        assertThrows(
                ProtosSignalException.class,
                () -> ProtosNamedCallPreparation
                        .activationForSelectedDirectSource(
                                selected, caller, supplied));
    }

    @Test
    void unknownNameSignalsGuestError() {
        ProtosActivation caller = moduleActivation();
        ProtosClosureValue selected =
                sourceClosure("(a) => a");

        PreparedArgumentVector supplied = new PreparedArgumentVector();
        supplied.appendNamed("missing", 1);

        assertThrows(
                ProtosSignalException.class,
                () -> ProtosNamedCallPreparation
                        .activationForSelectedDirectSource(
                                selected, caller, supplied));
    }

    @Test
    void customSourceCallDoesNotForwardUndeclaredNames() {
        ProtosActivation caller = moduleActivation();

        // Model the actually selected overridden call behavior.
        ProtosClosureValue override =
                sourceClosure("(arg) => arg");

        PreparedArgumentVector supplied = new PreparedArgumentVector();
        supplied.appendNamed("other", 12);

        assertThrows(
                ProtosSignalException.class,
                () -> ProtosNamedCallPreparation
                        .activationForSelectedSourceMethod(
                                override,
                                ProtosObjectValue.rootObject(),
                                ProtosObjectValue.rootObject(),
                                caller,
                                supplied));
    }

    @Test
    void namedFormalCanLeaveRestEmpty() {
        ProtosActivation caller = moduleActivation();
        ProtosClosureValue selected =
                sourceClosure("(a, b, c, ...rest) => rest");

        PreparedArgumentVector supplied = new PreparedArgumentVector();
        supplied.append(1);
        supplied.append(2);
        supplied.appendNamed("c", 3);

        ProtosActivation activation =
                ProtosNamedCallPreparation
                        .activationForSelectedDirectSource(
                                selected, caller, supplied);

        assertEquals(
                List.of(1, 2, 3),
                activation.suppliedArgumentsForRuntime());
        assertEquals(
                List.of(),
                ProtosNamedArgumentAssociation.associate(
                        selected.definition().parameters(),
                        supplied.positionalSnapshot(),
                        supplied.namedSnapshot()).restValues());
    }

    @Test
    void selectedImplementationDeterminesItsFormalNames() {
        ProtosActivation caller = moduleActivation();
        ProtosClosureValue former =
                sourceClosure("(oldName) => oldName");
        ProtosClosureValue replacement =
                sourceClosure("(newName) => newName");

        PreparedArgumentVector supplied = new PreparedArgumentVector();
        supplied.appendNamed("newName", 5);

        assertThrows(
                ProtosSignalException.class,
                () -> ProtosNamedCallPreparation
                        .activationForSelectedDirectSource(
                                former, caller, supplied));

        ProtosActivation accepted =
                ProtosNamedCallPreparation
                        .activationForSelectedDirectSource(
                                replacement, caller, supplied);

        assertEquals(5, accepted.suppliedArgumentsForRuntime().get(0));
    }

    @Test
    void selectedSourceCanEnterPreparedBytecodeWithNamedValue() {
        ProtosActivation caller = moduleActivation();

        ProtosClosureValue selected = assertInstanceOf(
                ProtosClosureValue.class,
                ProtosTestExecutionSupport.evaluate(
                        "i143-selected-source.protos",
                        "(a: 10, b: 20, c: 30) => c",
                        caller));

        ProtosObjectValue value =
                new ProtosObjectValue(
                        ProtosObjectValue.rootObject());

        PreparedArgumentVector supplied = new PreparedArgumentVector();
        supplied.appendNamed("c", value);

        Object result = ProtosTestExecutionSupport.callEntered(
                () -> {
                    PreparedClosureCall prepared =
                            ProtosNamedCallPreparation
                                    .prepareSelectedDirectSource(
                                            selected, caller, supplied);

                    Object entered =
                            ProtosBytecodeRootNode.EnterClosureCall
                                    .ordinaryIndirect(
                                            assertInstanceOf(
                                                    OrdinarySourceCall.class,
                                                    prepared),
                                            IndirectCallNode.create());

                    return prepared.finish(entered);
                });

        assertSame(value, result);
    }

    private static ProtosClosureValue sourceClosure(String text) {
        SurfaceSequence program = new ProtosParser(text).parseProgram();
        CanonicalClosure definition = assertInstanceOf(
                CanonicalClosure.class,
                new Canonicalizer().canonicalize(
                        program.expressions().get(0)));

        return new ProtosClosureValue(
                definition,
                List.of(),
                ProtosObjectValue.rootObject());
    }

    private static ProtosActivation moduleActivation() {
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
                new ProtosPrelude(bindings, contextPrototype);

        return prelude.newModuleActivation();
    }
}
