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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.parser.ProtosParser;
import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosActorExecutionDomain;
import com.guillermomolina.protos.runtime.ProtosActorModuleState;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosFutureValue;
import com.guillermomolina.protos.runtime.ProtosMatrixValue;
import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosStringValue;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.guillermomolina.protos.runtime.ProtosTask;
import com.guillermomolina.protos.runtime.ProtosVectorValue;
import com.guillermomolina.protos.semantic.Canonicalizer;
import com.guillermomolina.protos.semantic.ast.CanonicalSequence;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage.LanguageReference;
import com.oracle.truffle.api.source.Source;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import org.junit.jupiter.api.Test;

/** Real guest calls through the hosted Bytecode DSL and its resumable structured loop. */
final class ProtosStandardValueEachIterationTest {
    private static final Path CORE = Path.of("protos", "lib", "core");
    private static final LanguageReference<ProtosLanguage> LANGUAGE_REF =
            LanguageReference.create(ProtosLanguage.class);
    private static final String IMPORTS = """
            LA: import("std:math/linear_algebra")
            Vector: LA.Vector
            Matrix: LA.Matrix
            """;
    private static final List<String> RECEIVERS = List.of(
            "\"A😀B\"", "Vector(1, 2, 3)", "Matrix(1, 3, 1, 2, 3)");
    private static final List<List<Object>> EXPECTED = List.of(
            List.of("A", "😀", "B"), List.of(1L, 2L, 3L), List.of(1L, 2L, 3L));

    @Test
    void combiningAndFamilyEmojiScalarsUseTheStructuredLoop() throws Exception {
        inContext(() -> {
            List<String> inputs = List.of("\"e\\u{301}\"", "\"👨‍👩‍👧‍👦\"");
            List<List<String>> expected = List.of(
                    List.of("e", "\u0301"),
                    List.of("👨", "\u200D", "👩", "\u200D", "👧", "\u200D", "👦"));
            for (int input = 0; input < inputs.size(); input++) {
                Module module = new Module(null);
                Object receiver = module.run("text: " + inputs.get(input) + "\ntext");
                Object result = module.run("text.each(c => hook(c))");
                assertSame(receiver, result);
                assertEquals(expected.get(input).size(), module.visits.size());
                assertEquals(expected.get(input), module.observed());
                for (Object value : module.visits) {
                    ProtosStringValue scalar = assertInstanceOf(ProtosStringValue.class, value);
                    assertEquals(1, scalar.scalarCountForRuntime());
                }
                assertTrue(module.sawStructuredLoop);
            }
        });
    }

    @Test
    void publishedSynchronousMessageEntryUsesStructuredDispatch() throws Exception {
        inContext(() -> {
            List<String> emptyReceivers = List.of("\"\"", "Vector()", "Matrix(0, 5)");
            for (int family = 0; family < RECEIVERS.size(); family++) {
                Module module = new Module(null);
                Object receiver = module.run(IMPORTS + RECEIVERS.get(family));
                ProtosClosureValue callback = ProtosClosureValue.nativeClosure((caller, supplied) -> {
                    assertEquals(1, supplied.size());
                    module.visits.add(supplied.get(0));
                    return ProtosNullValue.INSTANCE;
                });

                Object result = assertDoesNotThrow(() -> ProtosInvocation.invokeMessage(
                        receiver, "each", List.of(callback), module.activation));
                assertSame(receiver, result);
                assertEquals(EXPECTED.get(family), module.observed());

                module.visits.clear();
                Object notInvokable = module.run("{ call: 1 }");
                assertThrows(ProtosSignalException.class, () -> ProtosInvocation.invokeMessage(
                        receiver, "each", List.of(notInvokable), module.activation));
                assertTrue(module.visits.isEmpty());

                Object empty = module.run(emptyReceivers.get(family));
                assertThrows(ProtosSignalException.class, () -> ProtosInvocation.invokeMessage(
                        empty, "each", List.of(notInvokable), module.activation));
                assertTrue(module.visits.isEmpty());
            }
        });
    }

    @Test
    void literalAndPolymorphicCallbacksUseTheStructuredLoopInOrder() throws Exception {
        inContext(() -> {
            for (int family = 0; family < RECEIVERS.size(); family++) {
                for (boolean polymorphic : List.of(false, true)) {
                    Module module = new Module(null);
                    String callback = polymorphic ? "{ call: callback }" : "n => hook(n)";
                    Object result = module.run(IMPORTS + """
                            run: () => {
                                values: %s
                                callback: n => hook(n)
                                result: values.each(%s)
                                result === values
                            }
                            run()
                            """.formatted(RECEIVERS.get(family), callback));
                    assertSame(Boolean.TRUE, result);
                    assertEquals(EXPECTED.get(family), module.observed());
                    assertTrue(module.sawStructuredLoop);
                }
            }
        });
    }

    @Test
    void contextualForUsesTheSameStructuredLoop() throws Exception {
        inContext(() -> {
            for (int family = 0; family < RECEIVERS.size(); family++) {
                Module module = new Module(null);
                Object result = module.run(IMPORTS + """
                        run: () => {
                            values: %s
                            result: for(n in values) { hook(n) }
                            result === values
                        }
                        run()
                        """.formatted(RECEIVERS.get(family)));
                assertSame(Boolean.TRUE, result);
                assertEquals(EXPECTED.get(family), module.observed());
                assertTrue(module.sawStructuredLoop);
            }
        });
    }

    @Test
    void firstCallbackMutationCannotChangePendingNumericSnapshot() throws Exception {
        inContext(() -> {
            for (String receiver : RECEIVERS.subList(1, 3)) {
                Module module = new Module(null);
                String write = receiver.startsWith("Vector")
                        ? "values.atPut(1, 99)" : "values.atPut(0, 1, 99)";
                Object result = module.run(IMPORTS + """
                        run: () => {
                            values: %s
                            count: 0
                            result: values.each(n => {
                                hook(n)
                                count = count + 1
                                if(count == 1) { %s }
                            })
                            result === values
                        }
                        run()
                        """.formatted(receiver, write));
                assertSame(Boolean.TRUE, result);
                assertEquals(List.of(1L, 2L, 3L), module.observed());
                assertTrue(module.sawStructuredLoop);
            }
        });
    }

    @Test
    void suspensionRetainsCursorAndSnapshotAcrossReentrantMutation() throws Exception {
        inContext(() -> {
            for (int family = 0; family < RECEIVERS.size(); family++) {
                Module module = new Module(null);
                ProtosFutureValue gate = module.future();
                module.bind("gate", gate);
                Object receiver = module.run(IMPORTS + "values: " + RECEIVERS.get(family) + "\nvalues");
                ProtosTask task = module.start("""
                        run: () => {
                            callback: n => { hook(n); gate.value() }
                            values.each({ call: callback })
                        }
                        run()
                        """, null);
                assertTrue(module.domain.dispatchOne());
                assertEquals(ProtosTask.State.SUSPENDED, task.state());
                assertEquals(EXPECTED.get(family).subList(0, 1), module.observed());
                if (receiver instanceof ProtosVectorValue vector) {
                    vector.indexedPut(1L, 99L);
                } else if (receiver instanceof ProtosMatrixValue matrix) {
                    matrix.indexedPut(0L, 1L, 99L);
                }
                assertTrue(gate.resolve(ProtosNullValue.INSTANCE, module.activation));
                assertTrue(module.domain.dispatchOne());
                assertEquals(ProtosTask.State.COMPLETED, task.state());
                assertSame(receiver, task.result().orElseThrow());
                assertEquals(EXPECTED.get(family), module.observed());
                assertTrue(module.sawStructuredLoop);
            }
        });
    }

    @Test
    void literalCallbacksResumeWithoutReplayingCompletedEffects() throws Exception {
        inContext(() -> {
            for (int family = 0; family < RECEIVERS.size(); family++) {
                Module module = new Module(null);
                ProtosFutureValue gate = module.future();
                module.bind("gate", gate);
                module.run(IMPORTS + "values: " + RECEIVERS.get(family));
                ProtosTask task = module.start(
                        "values.each(n => { hook(n); gate.value(); hook(n) })", null);
                assertTrue(module.domain.dispatchOne());
                assertEquals(ProtosTask.State.SUSPENDED, task.state());
                assertEquals(EXPECTED.get(family).subList(0, 1), module.observed());
                assertTrue(gate.resolve(ProtosNullValue.INSTANCE, module.activation));
                assertTrue(module.domain.dispatchOne());
                assertEquals(ProtosTask.State.COMPLETED, task.state());
                List<Object> expected = new ArrayList<>();
                for (Object element : EXPECTED.get(family)) {
                    expected.add(element);
                    expected.add(element);
                }
                assertEquals(expected, module.observed());
                assertTrue(module.sawStructuredLoop);
            }
        });
    }

    @Test
    void errorStopsTraversalAndEnsureRunsOnce() throws Exception {
        inContext(() -> {
            for (int family = 0; family < RECEIVERS.size(); family++) {
                Module module = new Module(null);
                Object result = module.run(IMPORTS + """
                        run: () => {
                            values: %s
                            cleaned: 0
                            expected: Error()
                            caught: Error.handle(() => {
                                (() => values.each(n => {
                                    hook(n)
                                    expected.signal()
                                })).ensure(() => { cleaned = cleaned + 1 })
                            }, error => error)
                            (caught === expected) && (cleaned == 1)
                        }
                        run()
                        """.formatted(RECEIVERS.get(family)));
                assertSame(Boolean.TRUE, result);
                assertEquals(EXPECTED.get(family).subList(0, 1), module.observed());
                assertTrue(module.sawStructuredLoop);
            }
        });
    }

    @Test
    void nonlocalReturnPreservesPrefixAndRunsEnsure() throws Exception {
        inContext(() -> {
            for (int family = 0; family < RECEIVERS.size(); family++) {
                Module module = new Module(null);
                Object result = module.run(IMPORTS + """
                        cleaned: { count: 0 }
                        run: () => {
                            values: %s
                            (() => values.each(n => {
                                hook(n)
                                ^42
                            })).ensure(() => { cleaned.count = cleaned.count + 1 })
                            100
                        }
                        (run() == 42) && (cleaned.count == 1)
                        """.formatted(RECEIVERS.get(family)));
                assertSame(Boolean.TRUE, result);
                assertEquals(EXPECTED.get(family).subList(0, 1), module.observed());
                assertTrue(module.sawStructuredLoop);
            }
        });
    }

    @Test
    void cancellationUnwindsEnsureWithoutAnotherCallback() throws Exception {
        inContext(() -> {
            for (int family = 0; family < RECEIVERS.size(); family++) {
                Module module = new Module(null);
                ProtosFutureValue gate = module.future();
                module.bind("gate", gate);
                module.run(IMPORTS + "values: " + RECEIVERS.get(family) + "\ncleaned: { count: 0 }");
                ProtosFutureValue producer = module.future();
                ProtosTask task = module.start("""
                        (() => values.each(n => { hook(n); gate.value() })).ensure(() => {
                            cleaned.count = cleaned.count + 1
                        })
                        """, producer);
                producer.attachProducerTask(task, module.activation);
                assertTrue(module.domain.dispatchOne());
                assertEquals(ProtosTask.State.SUSPENDED, task.state());
                assertEquals(0L, module.run("cleaned.count"));
                assertTrue(producer.cancelRequest());
                assertTrue(module.domain.dispatchOne());
                assertEquals(ProtosTask.State.CANCELLED, task.state());
                assertEquals(ProtosFutureValue.State.CANCELLED, producer.state());
                assertEquals(1L, module.run("cleaned.count"));
                assertEquals(EXPECTED.get(family).subList(0, 1), module.observed());
                assertTrue(module.sawStructuredLoop);
                assertTrue(gate.resolve(ProtosNullValue.INSTANCE, module.activation));
                assertFalse(module.domain.dispatchOne());
                assertEquals(1L, module.run("cleaned.count"));
            }
        });
    }

    @Test
    void callbackFutureIsAnOrdinaryIgnoredValue() throws Exception {
        inContext(() -> {
            for (int family = 0; family < RECEIVERS.size(); family++) {
                Module module = new Module(null);
                ProtosFutureValue pending = module.future();
                module.bind("pending", pending);
                module.run(IMPORTS + "values: " + RECEIVERS.get(family));
                ProtosTask task = module.start("values.each(n => { hook(n); pending })", null);
                assertTrue(module.domain.dispatchOne());
                assertEquals(ProtosTask.State.COMPLETED, task.state());
                assertEquals(EXPECTED.get(family), module.observed());
                assertEquals(ProtosFutureValue.State.PENDING, pending.state());
                assertTrue(module.sawStructuredLoop);
            }
        });
    }

    @Test
    void nestedTraversalsKeepIndependentCursors() throws Exception {
        inContext(() -> {
            for (String outer : RECEIVERS) {
                for (String inner : RECEIVERS) {
                    Module module = new Module(null);
                    Object result = module.run(IMPORTS + """
                            run: () => {
                                outer: %s
                                inner: %s
                                count: 0
                                result: outer.each(a => {
                                    inner.each(b => { hook(b); count = count + 1 })
                                })
                                (count == 9) && (result === outer)
                            }
                            run()
                            """.formatted(outer, inner));
                    assertSame(Boolean.TRUE, result);
                    assertEquals(9, module.visits.size());
                    assertTrue(module.sawStructuredLoop);
                }
            }
        });
    }

    @Test
    void openClosedAndFrozenNumericReceiversKeepExactNumbers() throws Exception {
        inContext(() -> {
            String numbers = "1, 1000000000000000000000000000000, 1 / 3, -0.0, Complex(2, 3)";
            for (String receiver : List.of("Vector(" + numbers + ")", "Matrix(1, 5, " + numbers + ")")) {
                for (String state : List.of("", "values.close()", "values.freeze()")) {
                    Module module = new Module(null);
                    Object result = module.run(IMPORTS + """
                            run: () => {
                                expected: [%s]
                                values: %s
                                %s
                                index: 0
                                valid: true
                                result: values.each(n => {
                                    hook(n)
                                    valid = valid && (n === expected[index])
                                    index = index + 1
                                })
                                valid && (index == 5) && (result === values)
                            }
                            run()
                            """.formatted(numbers, receiver, state));
                    assertSame(Boolean.TRUE, result);
                    assertEquals(5, module.visits.size());
                    assertTrue(module.sawStructuredLoop);
                }
            }
        });
    }

    @Test
    void packedMatrixSnapshotSurvivesSuspendedRepresentationChange() throws Exception {
        inContext(() -> {
            Module module = new Module(null);
            ProtosFutureValue gate = module.future();
            module.bind("gate", gate);
            ProtosMatrixValue matrix = (ProtosMatrixValue) module.run(IMPORTS + """
                    values: Matrix(1, 2, 1.0, -0.0).matmul(Matrix(2, 2, 1.0, 0.0, 0.0, 1.0))
                    values
                    """);
            // Compare semantic numeric identity, including the kernel's signed-zero outcome.
            Object result = module.run("""
                    expected: [values.at(0, 0), values.at(0, 1)]
                    index: 0
                    valid: true
                    true
                    """);
            assertSame(Boolean.TRUE, result);
            ProtosTask task = module.start("""
                    values.each(n => {
                        hook(n)
                        valid = valid && (n === expected[index])
                        index = index + 1
                        gate.value()
                    })
                    """, null);
            assertTrue(module.domain.dispatchOne());
            assertEquals(ProtosTask.State.SUSPENDED, task.state());
            matrix.indexedPut(0L, 1L, 99L);
            assertTrue(gate.resolve(ProtosNullValue.INSTANCE, module.activation));
            assertTrue(module.domain.dispatchOne());
            assertEquals(ProtosTask.State.COMPLETED, task.state());
            assertSame(matrix, task.result().orElseThrow());
            assertSame(Boolean.TRUE, module.run("valid && (index == 2) && (values.at(0, 1) === 99)"));
            assertTrue(module.sawStructuredLoop);
        });
    }

    private interface Body {
        void run() throws Exception;
    }

    private static void inContext(Body body) {
        try (ProtosPolyglotExecutionContext host = ProtosPolyglotExecutionContext.open(
                java.io.InputStream.nullInputStream(),
                java.io.OutputStream.nullOutputStream(),
                java.io.OutputStream.nullOutputStream())) {
            host.callEntered(() -> {
                try {
                    body.run();
                } catch (RuntimeException failure) {
                    throw failure;
                } catch (Exception failure) {
                    throw new AssertionError("hosted iteration test failed", failure);
                }
                return null;
            });
        }
    }

    /** The same hosted bootstrap/parser/lowerer harness used by Array callback tests. */
    private static final class Module {
        private final List<Object> visits = new ArrayList<>();
        private final ProtosActorExecutionDomain domain = new ProtosActorExecutionDomain();
        private final ProtosPrelude prelude;
        private final ProtosActivation activation;
        private boolean sawStructuredLoop;

        Module(BiConsumer<ProtosActivation, Object> effect) throws Exception {
            prelude = new ProtosCoreBootstrap().bootstrap(
                    CORE, new ProtosStandardLibraryModuleResolver(Path.of("protos", "lib")));
            activation = prelude.newModuleActivation(
                    new ProtosActorModuleState(), null, prelude.newExecutionContext(), domain);
            bind("hook", ProtosClosureValue.nativeClosure((caller, supplied) -> {
                assertEquals(1, supplied.size());
                Object element = supplied.get(0);
                visits.add(element);
                RootCallTarget structuredTarget =
                        ProtosTaskCPrimeEntryExecution.structuredDispatchTargetForEnteredContext();
                Truffle.getRuntime().iterateFrames(frame -> {
                    if (frame.getCallTarget() == structuredTarget) {
                        sawStructuredLoop = true;
                    }
                    return null;
                });
                if (effect != null) {
                    effect.accept(caller, element);
                }
                return ProtosNullValue.INSTANCE;
            }));
        }

        void bind(String name, Object value) {
            activation.context().createLocalSlot(name, value);
        }

        ProtosFutureValue future() {
            return new ProtosFutureValue(prelude.futurePrototype(), domain);
        }

        Object run(String program) {
            try {
                return lower(program).getCallTarget().call(activation);
            } catch (ProtosSignalException signal) {
                throw new AssertionError(
                        "Guest Error: " + signal.error().localSlotsSnapshot()
                                + "\nGuest trace: " + ProtosDiagnosticTraceCapture.capture(signal)
                                + "\nSource:\n" + program,
                        signal);
            }
        }

        ProtosTask start(String program, ProtosFutureValue producer) {
            ProtosBytecodeRootNode root = lower(program);
            return domain.createTask(null, producer, current ->
                    ProtosBytecodeTaskExecution.execute(current, root.getCallTarget(), activation));
        }

        private ProtosBytecodeRootNode lower(String characters) {
            Source source = Source.newBuilder(
                    ProtosLanguage.ID, characters, "standard-value-each-iteration.protos").build();
            CanonicalSequence sequence = (CanonicalSequence) new Canonicalizer()
                    .canonicalize(new ProtosParser(characters).parseProgram());
            return new CanonicalToBytecodeLowerer(LANGUAGE_REF.get(null), source).lowerRoot(sequence);
        }

        List<Object> observed() {
            return visits.stream().map(value -> value instanceof ProtosStringValue text
                    ? (Object) text.value() : value).toList();
        }
    }
}
