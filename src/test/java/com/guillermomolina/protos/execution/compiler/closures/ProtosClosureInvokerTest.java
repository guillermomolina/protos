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

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.control.ProtosReturnHome;
import com.oracle.truffle.api.CompilerDirectives;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class ProtosClosureInvokerTest {
    private static final Path CORE = Path.of("protos", "lib", "core");

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
    void preparedBytecodeInvocationCompletesOwnedReturnHome() {
        // PERF025: `^` keeps the owned home observable, hence physical.
        ProtosClosureValue closure = closure("() => ^null");

        ProtosActivation activation =
                ProtosActivation.forClosureInvocation(closure, List.of());
        ProtosReturnHome home = activation.returnHome().orElseThrow();

        assertTrue(home.isActive());

        ProtosTestExecutionSupport.callEntered(
                () -> {
                    // I115: the shared dispatcher enters the published activation;
                    // the callee root owns and closes its ReturnHome.
                    return ProtosFunctionCallDispatchers.callPublishedUncached(
                            closure, activation);
                });

        assertFalse(home.isActive());
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

    @Test
    void hostContextEntryIsTruffleBoundary() throws NoSuchMethodException {
        assertTrue(
                ProtosClosureInvoker.class
                        .getDeclaredMethod("callWithEnteredContext", Supplier.class)
                        .isAnnotationPresent(CompilerDirectives.TruffleBoundary.class));
    }

    @Test
    void synchronousClosureWithArgumentsUsesTemporaryHost() {
        ProtosClosureValue closure = closure("(x, y) => y");
        Object first = new ProtosObjectValue(ProtosObjectValue.rootObject());
        Object second = new ProtosObjectValue(ProtosObjectValue.rootObject());

        Object result = ProtosClosureInvoker.invoke(closure, List.of(first, second));

        assertSame(second, result);
    }

    @Test
    void synchronousCallableWithArgumentsInsideEnteredContext() {
        ProtosTestExecutionSupport.runEntered(
                () -> {
                    ProtosActivation caller = coreModuleActivation();
                    ProtosClosureValue closure = closure("(x, y) => y", caller);
                    Object first = new ProtosObjectValue(ProtosObjectValue.rootObject());
                    Object second = new ProtosObjectValue(ProtosObjectValue.rootObject());

                    Object viaCallable =
                            ProtosClosureInvoker.invokeCallable(
                                    closure, List.of(first, second), caller);
                    Object viaInvoke =
                            ProtosClosureInvoker.invoke(
                                    closure, List.of(first, second), caller);

                    assertSame(second, viaCallable);
                    assertSame(second, viaInvoke);
                });
    }

    @Test
    void synchronousCallableExecutionErrorPropagates() {
        ProtosTestExecutionSupport.runEntered(
                () -> {
                    ProtosActivation caller = coreModuleActivation();
                    ProtosClosureValue closure =
                            closure("() => protosClosureInvokerTestUnboundName", caller);

                    assertThrows(
                            RuntimeException.class,
                            () -> ProtosClosureInvoker.invokeCallable(
                                    closure, List.of(), caller));
                });
    }

    @Test
    void synchronousCallableDoesNotCompleteCapturedReturnHome() {
        ProtosTestExecutionSupport.runEntered(
                () -> {
                    ProtosActivation caller = coreModuleActivation();
                    ProtosClosureValue outer = closure("() => null", caller);
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

                    ProtosClosureInvoker.invokeCallable(nested, List.of(), caller);

                    assertTrue(capturedHome.isActive());
                });
    }

    private static ProtosActivation coreModuleActivation() {
        try {
            return new ProtosCoreBootstrap().bootstrap(CORE).newModuleActivation();
        } catch (java.io.IOException failure) {
            throw new java.io.UncheckedIOException(failure);
        }
    }

    private static ProtosClosureValue closure(String source, ProtosActivation activation) {
        return assertInstanceOf(
                ProtosClosureValue.class,
                ProtosTestExecutionSupport.evaluate(
                        "closure-invoker-test.protos", source, activation));
    }

    private static ProtosClosureValue closure(String source) {
        return assertInstanceOf(
                ProtosClosureValue.class,
                ProtosTestExecutionSupport.evaluate(
                        "closure-invoker-test.protos",
                        source,
                        moduleActivation()));
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
