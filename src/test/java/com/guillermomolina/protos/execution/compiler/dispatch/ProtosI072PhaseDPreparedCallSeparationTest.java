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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * I072 Phase D structural discriminator, I115 form: an ordinary source call is
 * a {@link ProtosCallRequest} (callable, B5 arguments, caller) that never
 * physically carries native-body, structured-control-capability or
 * module-initialization state; structured capability is owned only by
 * {@link ProtosPreparedCalls.StructuredNativeInvocation}, and module
 * initialization only by {@link ProtosPreparedCalls.ModuleInitializationCall}.
 * The PreparedClosureCall hierarchy and its lifecycle operations no longer
 * exist (I115): ReturnHome completion belongs to the callee root.
 */
class ProtosI072PhaseDPreparedCallSeparationTest {
    @Test
    void callRequestIsDataNotAUniversalCarrier() {
        assertNotEquals(ProtosCallRequest.class, ProtosPreparedCalls.StructuredNativeInvocation.class);
        assertNotEquals(ProtosCallRequest.class, ProtosPreparedCalls.ModuleInitializationCall.class);
        Set<String> declared = declaredMethodNames(ProtosCallRequest.class);
        for (String lifecycle : List.of(
                "finish", "complete", "handleControlTransfer", "mapRuntimeFailure",
                "enterNative", "enterImmediate")) {
            assertFalse(declared.contains(lifecycle),
                    "a call request has no invocation protocol: " + lifecycle);
        }
    }

    /**
     * TEST009-H/I successor: the structured operations specialize on the
     * concrete structured state, never on a generic call carrier.
     */
    @Test
    void structuredDispatchOperationsSpecializeOnConcreteRepresentations() {
        for (Class<?> operation : List.of(
                ProtosBytecodeRootNode.IsStructuredCall.class,
                ProtosBytecodeRootNode.PrepareStructuredCall.class)) {
            List<java.lang.reflect.Method> specializations = specializations(operation);
            assertEquals(1, specializations.size(), operation.getName());
            assertEquals(
                    ProtosPreparedCalls.StructuredNativeInvocation.class,
                    specializations.get(0).getParameterTypes()[1],
                    operation.getName());
        }
    }

    /**
     * TEST009-J: only the structured native state owns the structured
     * capability surface; a call request and the module state neither declare
     * it nor physically carry structured state.
     */
    @Test
    void onlyStructuredNativeInvocationOwnsStructuredCapabilities() {
        Set<String> nativeDeclared =
                declaredMethodNames(ProtosPreparedCalls.StructuredNativeInvocation.class);
        assertTrue(nativeDeclared.contains("requiresStructuredDispatch"));
        assertTrue(nativeDeclared.stream().anyMatch(name -> name.startsWith("isStructured")));
        assertTrue(nativeDeclared.stream().anyMatch(name -> name.startsWith("prepareStructured")));

        for (Class<?> absent : List.of(
                ProtosCallRequest.class,
                ProtosPreparedCalls.ModuleInitializationCall.class)) {
            Set<String> declared = declaredMethodNames(absent);
            assertTrue(
                    declared.stream().noneMatch(name -> name.equals("requiresStructuredDispatch")
                            || name.startsWith("isStructured")
                            || name.startsWith("prepareStructured")),
                    absent.getName() + " must not acquire a structured capability");
            assertTrue(
                    Arrays.stream(absent.getDeclaredFields())
                            .noneMatch(field -> field.getName().toLowerCase().contains("structured")),
                    absent.getName() + " must not carry structured state");
        }
    }

    /** TEST009-J: an ordinary builtin has no structured state at all. */
    @Test
    void ordinaryBuiltinHasNoStructuredState() {
        ProtosClosureValue builtin =
                ProtosClosureValue.nativeClosure((activation, supplied) -> supplied.size());
        assertEquals(
                null,
                ProtosPreparedCalls.StructuredNativeInvocation.forBodyOrNull(
                        builtin, List.of(), moduleActivation()));
    }

    private static List<java.lang.reflect.Method> specializations(Class<?> operation) {
        return Arrays.stream(operation.getDeclaredMethods())
                .filter(
                        method ->
                                method.isAnnotationPresent(
                                        com.oracle.truffle.api.dsl.Specialization.class))
                .collect(java.util.stream.Collectors.toList());
    }

    private static Set<String> declaredMethodNames(Class<?> type) {
        return Arrays.stream(type.getDeclaredMethods())
                .map(java.lang.reflect.Method::getName)
                .collect(java.util.stream.Collectors.toSet());
    }

    @Test
    void callRequestNeverPhysicallyDeclaresSpecialCallState() {
        Set<String> fieldNames =
                Arrays.stream(ProtosCallRequest.class.getDeclaredFields())
                        .map(Field::getName)
                        .collect(java.util.stream.Collectors.toSet());

        assertFalse(
                fieldNames.stream().anyMatch(name -> name.toLowerCase().contains("native")),
                "a call request must not carry native-mode state: " + fieldNames);
        assertFalse(
                fieldNames.stream().anyMatch(name -> name.toLowerCase().contains("structured")),
                "a call request must not carry structured-control capabilities: " + fieldNames);
        assertFalse(
                fieldNames.stream().anyMatch(name -> name.toLowerCase().contains("moduleinitialization")),
                "a call request must not carry module-initialization state: " + fieldNames);
    }

    @Test
    void ordinarySourceCallRunsThroughTheSharedDispatcher() {
        ProtosClosureValue closure = closure("() => null");
        ProtosActivation activation =
                ProtosActivation.forClosureInvocation(closure, List.of());

        assertTrue(ProtosFunctionCallDispatchers.CallNode.isSourceFunction(closure));
        assertEquals(
                com.guillermomolina.protos.runtime.ProtosNullValue.INSTANCE,
                ProtosTestExecutionSupport.callEntered(
                        () -> ProtosFunctionCallDispatchers.callPublishedUncached(
                                closure, activation)));
    }

    @Test
    void ordinaryNoControlCallNeverEagerlyAllocatesDynamicControlState() {
        ProtosClosureValue closure = closure("() => null");
        ProtosActivation activation =
                ProtosActivation.forClosureInvocation(closure, List.of());

        assertEquals(
                java.util.Optional.empty(),
                activation.dynamicControlStateIfPresent());

        ProtosTestExecutionSupport.callEntered(
                () -> ProtosFunctionCallDispatchers.callPublishedUncached(closure, activation));

        assertEquals(
                java.util.Optional.empty(),
                activation.dynamicControlStateIfPresent());
    }

    private static ProtosClosureValue closure(String source) {
        return assertInstanceOf(
                ProtosClosureValue.class,
                ProtosTestExecutionSupport.evaluate(
                        "i072-phase-d-separation-test.protos",
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
