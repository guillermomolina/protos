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
 * I072 Phase D structural discriminator: an ordinary source-backed,
 * nonsuspending call must use a lean call representation that never
 * physically carries native-body, structured-control-capability, or
 * module-initialization state, and {@link ProtosBytecodeRootNode.PreparedClosureCall}
 * itself must be a small dispatch surface rather than a universal carrier
 * class. See PLAT040 Candidate F' and the I072 Phase D implementation report.
 */
class ProtosI072PhaseDPreparedCallSeparationTest {
    @Test
    void preparedClosureCallIsADispatchInterfaceNotAUniversalCarrier() {
        assertTrue(ProtosBytecodeRootNode.PreparedClosureCall.class.isInterface());
        assertNotEquals(
                ProtosBytecodeRootNode.OrdinarySourceCall.class,
                ProtosBytecodeRootNode.NativeCall.class);
        assertNotEquals(
                ProtosBytecodeRootNode.OrdinarySourceCall.class,
                ProtosBytecodeRootNode.ModuleInitializationCall.class);
    }

    /**
     * TEST009-H: the terminal lifecycle operations must preserve the concrete
     * prepared-call representation instead of erasing it to a single
     * {@link ProtosBytecodeRootNode.PreparedClosureCall} specialization, in both
     * generated interpreters that declare them.
     */
    @Test
    void terminalLifecycleOperationsSpecializeOnConcreteRepresentations() {
        Set<Class<?>> representations =
                Set.of(
                        ProtosBytecodeRootNode.OrdinarySourceCall.class,
                        ProtosBytecodeRootNode.NativeCall.class,
                        ProtosBytecodeRootNode.ImmediateResultCall.class,
                        ProtosBytecodeRootNode.ModuleInitializationCall.class);
        for (Class<?> representation : representations) {
            assertTrue(Modifier.isFinal(representation.getModifiers()), representation.getName());
            assertTrue(
                    ProtosBytecodeRootNode.PreparedClosureCall.class.isAssignableFrom(
                            representation),
                    representation.getName());
        }

        for (Class<?> operation :
                List.of(
                        ProtosBytecodeRootNode.CompleteClosureCall.class,
                        ProtosBytecodeRootNode.FinishClosureCall.class,
                        ProtosSemanticBytecodeRootNode.CompleteClosureCall.class,
                        ProtosSemanticBytecodeRootNode.FinishClosureCall.class)) {
            List<Class<?>> receivers =
                    Arrays.stream(operation.getDeclaredMethods())
                            .filter(method -> Modifier.isStatic(method.getModifiers()))
                            .filter(method -> Modifier.isPublic(method.getModifiers()))
                            .map(method -> method.getParameterTypes()[0])
                            .collect(java.util.stream.Collectors.toList());

            assertFalse(
                    receivers.contains(ProtosBytecodeRootNode.PreparedClosureCall.class),
                    operation.getName()
                            + " must not specialize on the PreparedClosureCall interface");
            assertEquals(
                    representations.size(),
                    receivers.size(),
                    operation.getName() + " specializations: " + receivers);
            assertEquals(representations, Set.copyOf(receivers), operation.getName());
        }
    }

    /**
     * TEST009-I: closure-call entry must likewise specialize on concrete
     * representations in both generated interpreters. Immediate and native
     * entry have one specialization each; the two source-backed
     * representations each keep a direct-call cache with an indirect fallback,
     * and module initialization additionally keeps its immediate-hit entry.
     */
    @Test
    void entryOperationSpecializesOnConcreteRepresentations() {
        java.util.Map<Class<?>, Long> expected =
                java.util.Map.of(
                        ProtosBytecodeRootNode.ImmediateResultCall.class, 1L,
                        ProtosBytecodeRootNode.NativeCall.class, 1L,
                        ProtosBytecodeRootNode.OrdinarySourceCall.class, 2L,
                        ProtosBytecodeRootNode.ModuleInitializationCall.class, 3L);

        for (Class<?> operation :
                List.of(
                        ProtosBytecodeRootNode.EnterClosureCall.class,
                        ProtosSemanticBytecodeRootNode.EnterClosureCall.class)) {
            List<java.lang.reflect.Method> specializations =
                    Arrays.stream(operation.getDeclaredMethods())
                            .filter(
                                    method ->
                                            method.isAnnotationPresent(
                                                    com.oracle.truffle.api.dsl.Specialization
                                                            .class))
                            .collect(java.util.stream.Collectors.toList());
            java.util.Map<Class<?>, Long> receivers =
                    specializations.stream()
                            .collect(
                                    java.util.stream.Collectors.groupingBy(
                                            method -> method.getParameterTypes()[0],
                                            java.util.stream.Collectors.counting()));

            assertFalse(
                    receivers.containsKey(ProtosBytecodeRootNode.PreparedClosureCall.class),
                    operation.getName()
                            + " must not specialize on the PreparedClosureCall interface");
            assertEquals(expected, receivers, operation.getName());

            for (Class<?> source :
                    List.of(
                            ProtosBytecodeRootNode.OrdinarySourceCall.class,
                            ProtosBytecodeRootNode.ModuleInitializationCall.class)) {
                List<Class<?>> sourceCallNodes =
                        specializations.stream()
                                .filter(method -> method.getParameterTypes()[0] == source)
                                .map(method -> method.getParameterTypes())
                                .filter(parameters -> parameters.length > 1)
                                .map(parameters -> parameters[parameters.length - 1])
                                .collect(java.util.stream.Collectors.toList());
                assertEquals(
                        List.of(
                                com.oracle.truffle.api.nodes.DirectCallNode.class,
                                com.oracle.truffle.api.nodes.IndirectCallNode.class),
                        sourceCallNodes.stream()
                                .sorted(java.util.Comparator.comparing(Class::getName))
                                .collect(java.util.stream.Collectors.toList()),
                        operation.getName() + " " + source.getSimpleName());
            }
        }
    }

    @Test
    void ordinarySourceCallNeverPhysicallyDeclaresSpecialCallState() {
        Set<String> fieldNames =
                Arrays.stream(
                                ProtosBytecodeRootNode.OrdinarySourceCall.class
                                        .getDeclaredFields())
                        .map(Field::getName)
                        .collect(java.util.stream.Collectors.toSet());

        assertFalse(
                fieldNames.stream().anyMatch(name -> name.toLowerCase().contains("native")),
                "ordinary source call must not carry native-mode state: " + fieldNames);
        assertFalse(
                fieldNames.stream().anyMatch(name -> name.toLowerCase().contains("structured")),
                "ordinary source call must not carry structured-control capabilities: "
                        + fieldNames);
        assertFalse(
                fieldNames.stream()
                        .anyMatch(name -> name.toLowerCase().contains("moduleinitialization")),
                "ordinary source call must not carry module-initialization state: "
                        + fieldNames);
    }

    @Test
    void ordinaryPreparedCallUsesLeanRepresentationAndHasNoSpecialCapabilities() {
        ProtosClosureValue closure = closure("() => null");
        ProtosActivation activation =
                ProtosActivation.forClosureInvocation(closure, List.of());

        ProtosBytecodeRootNode.PreparedClosureCall prepared =
                ProtosTestExecutionSupport.callEntered(
                        () ->
                                ProtosBytecodeRootNode.prepareSynchronousSourceClosureForRuntime(
                                        closure,
                                        List.of(),
                                        activation));

        assertInstanceOf(ProtosBytecodeRootNode.OrdinarySourceCall.class, prepared);
        assertFalse(prepared.isNative());
        assertFalse(prepared.isImmediate());
        assertFalse(prepared.requiresStructuredDispatch());
        assertThrows(IllegalStateException.class, prepared::prepareStructuredEnsure);
        assertThrows(IllegalStateException.class, prepared::enterNative);
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
                () ->
                        ProtosBytecodeRootNode.prepareSynchronousSourceClosureForRuntime(
                                closure,
                                List.of(),
                                activation));

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
