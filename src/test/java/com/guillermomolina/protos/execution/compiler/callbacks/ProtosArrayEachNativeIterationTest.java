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
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.parser.ProtosParser;
import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosCoreErrors;
import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.guillermomolina.protos.runtime.ProtosNumericValueSupport;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.guillermomolina.protos.runtime.ProtosStringValue;
import com.guillermomolina.protos.semantic.Canonicalizer;
import com.guillermomolina.protos.semantic.ast.CanonicalSequence;
import com.oracle.truffle.api.TruffleLanguage.LanguageReference;
import com.oracle.truffle.api.source.Source;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import org.graalvm.polyglot.Context;
import org.junit.jupiter.api.Test;

/**
 * Focal semantics of the standard {@code Array.each} with a literal callback,
 * on both the native iteration of an admitted callback and the ordinary
 * invocation of a callback whose activation is observable
 * (VALUES_AND_COLLECTIONS, Array iteration).
 *
 * <p>Each {@code run} declares a frame binding: the capture proof shared with
 * the other inline callbacks admits module reads only from a parent root that
 * has a frame layout.
 */
final class ProtosArrayEachNativeIterationTest {
    private static final Path CORE = Path.of("protos", "lib", "core");

    private static final LanguageReference<ProtosLanguage> LANGUAGE_REF =
            LanguageReference.create(ProtosLanguage.class);

    @Test
    void emptyArrayVisitsNothingAndAnswersItsReceiver() throws Exception {
        inContext(() -> {
            Module module = new Module(null);
            Object outcome = module.run(
                    """
                    run: () => {
                        values: Array()
                        returned: values.each((element) => {
                            hook(element)
                        })
                        (returned === values).ifTrueIfFalse(() => { 1 }, () => { 0 })
                    }
                    run()
                    """);
            assertEquals("1", ProtosNumericValueSupport.integerDecimalText(outcome),
                    "each must answer its original receiver");
            assertTrue(module.visits.isEmpty(), "an empty Array visits nothing");
            assertNativeLoop(module);
        });
    }

    @Test
    void integerAndObjectElementsAreVisitedExactlyInAscendingOrder() throws Exception {
        inContext(() -> {
            Module module = new Module(null);
            module.run(
                    """
                    run: () => {
                        values: Array(1, "two", 3)
                        values.each((element) => {
                            hook(element)
                        })
                    }
                    run()
                    """);
            assertEquals(3, module.visits.size());
            assertEquals(1L, module.visits.get(0));
            assertInstanceOf(ProtosStringValue.class, module.visits.get(1));
            assertEquals("two", ((ProtosStringValue) module.visits.get(1)).value());
            assertEquals(3L, module.visits.get(2));
            assertNativeLoop(module);
        });
    }

    @Test
    void replacingAnElementDuringIterationKeepsTheSnapshot() throws Exception {
        inContext(() -> {
            Module module = new Module(null);
            Object outcome = module.run(
                    """
                    run: () => {
                        values: Array(1, 2, 3)
                        values.each((element) => {
                            values.atPut(1, 100)
                            hook(element)
                        })
                        values.at(1)
                    }
                    run()
                    """);
            assertEquals(List.of(1L, 2L, 3L), module.visits,
                    "the snapshot established at the start must be visited");
            assertEquals("100", ProtosNumericValueSupport.integerDecimalText(outcome),
                    "the replacement itself remains an ordinary Array mutation");
            assertNativeLoop(module);
        });
    }

    @Test
    void anErrorStopsTheIterationBeforeLaterElements() throws Exception {
        inContext(() -> {
            Module module = new Module((activation, element) -> {
                if (Long.valueOf(2L).equals(element)) {
                    throw new ProtosSignalException(ProtosCoreErrors.newError(activation));
                }
            });
            assertThrows(RuntimeException.class, () -> module.run(
                    """
                    run: () => {
                        values: Array(1, 2, 3)
                        values.each((element) => {
                            hook(element)
                        })
                    }
                    run()
                    """));
            assertEquals(List.of(1L, 2L), module.visits,
                    "no element after the failing one may be visited");
            assertNativeLoop(module);
        });
    }

    /*
     * The admission proof forbids reading an own binding before its creation,
     * so a stale PRESENT binding would be observable only as a duplicate
     * creation; every repeated creation must succeed, and the loop body must
     * reset that binding.
     */
    @Test
    void repeatedCallbacksDoNotInheritPresentBindings() throws Exception {
        inContext(() -> {
            Module module = new Module(null);
            module.run(
                    """
                    run: () => {
                        values: Array(1, 2, 3)
                        values.each((element) => {
                            seen: element
                            hook(seen)
                        })
                    }
                    run()
                    """);
            assertEquals(List.of(1L, 2L, 3L), module.visits);
            List<String> names = assertNativeLoop(module);
            int next = indexAfter(names, "c.ArrayEachNext", -1);
            int reset = indexAfter(names, "c.ResetInlineCallbackLocal", next);
            int backward = indexAfter(names, "branch.backward", next);
            assertTrue(reset > next && reset < backward,
                    () -> "the callback's own binding must be reset per iteration: " + names);
        });
    }

    @Test
    void aModifiedEachSelectionIsInvokedWithTheGenuineCallback() throws Exception {
        inContext(() -> {
            Module module = new Module(null);
            Object outcome = module.run(
                    """
                    run: () => {
                        values: Array(1, 2, 3)
                        values.each: (callback) => {
                            callback(40)
                            42
                        }
                        values.each((element) => {
                            hook(element)
                        })
                    }
                    run()
                    """);
            assertEquals(List.of(40L), module.visits,
                    "only the selected behavior decides how the callback is invoked");
            assertEquals("42", ProtosNumericValueSupport.integerDecimalText(outcome),
                    "the selected behavior's result is the send's result");
        });
    }

    @Test
    void nonAdmissibleCallbackKeepsItsActivationAndContext() throws Exception {
        inContext(() -> {
            Module module = new Module(null);
            Object outcome = module.run(
                    """
                    run: () => {
                        values: Array(1, 2)
                        returned: values.each((element) => {
                            hook(context)
                        })
                        (returned === values).ifTrueIfFalse(() => { 1 }, () => { 0 })
                    }
                    run()
                    """);
            assertEquals("1", ProtosNumericValueSupport.integerDecimalText(outcome));
            assertEquals(2, module.visits.size());
            ProtosObjectValue first = (ProtosObjectValue) module.visits.get(0);
            ProtosObjectValue second = (ProtosObjectValue) module.visits.get(1);
            assertNotSame(first, second, "each invocation owns a distinct Context");
            assertEquals(1L, first.readLocalSlot("element").orElseThrow());
            assertEquals(2L, second.readLocalSlot("element").orElseThrow());

            List<String> names = instructionNames(module.physicalRun());
            assertFalse(names.stream().anyMatch(name -> name.contains("c.ArrayEachNext")),
                    () -> "an observable callback must not run in the caller: " + names);
            assertTrue(names.stream().anyMatch(name -> name.contains("c.CallUnaryMethod")),
                    () -> "an observable callback keeps the ordinary send: " + names);
        });
    }

    /** The physical root of {@code run} iterates natively, without per-element dispatch. */
    private static List<String> assertNativeLoop(Module module) {
        List<String> names = instructionNames(module.physicalRun());
        int next = indexAfter(names, "c.ArrayEachNext", -1);
        assertTrue(next >= 0, () -> "the admitted callback must iterate natively: " + names);
        return names;
    }

    private interface Body {
        void run() throws Exception;
    }

    private static void inContext(Body body) throws Exception {
        try (Context context = Context.newBuilder(ProtosLanguage.ID).build()) {
            context.initialize(ProtosLanguage.ID);
            context.enter();
            try {
                body.run();
            } finally {
                context.leave();
            }
        }
    }

    /**
     * A bootstrapped module whose source binding {@code hook} is a native
     * callable that records its argument and then applies {@code effect}.
     */
    private static final class Module {
        private final List<Object> visits = new ArrayList<>();
        private final ProtosActivation activation;

        Module(BiConsumer<ProtosActivation, Object> effect) throws Exception {
            activation = new ProtosCoreBootstrap().bootstrap(CORE).newModuleActivation();
            activation.context().createLocalSlot(
                    "recorder",
                    ProtosClosureValue.nativeClosure(
                            (caller, supplied) -> {
                                Object element = supplied.get(0);
                                visits.add(element);
                                if (effect != null) {
                                    effect.accept(caller, element);
                                }
                                return ProtosNullValue.INSTANCE;
                            }));
        }

        Object run(String program) {
            String characters = "hook: recorder\n" + program;
            Source source =
                    Source.newBuilder(
                                    ProtosLanguage.ID,
                                    characters,
                                    "array-each-native-iteration.protos")
                            .build();
            CanonicalSequence sequence =
                    (CanonicalSequence)
                            new Canonicalizer()
                                    .canonicalize(new ProtosParser(characters).parseProgram());
            return new CanonicalToBytecodeLowerer(LANGUAGE_REF.get(null), source)
                    .lowerRoot(sequence)
                    .getCallTarget()
                    .call(activation);
        }

        ProtosBytecodeRootNode physicalRun() {
            ProtosClosureValue run =
                    (ProtosClosureValue)
                            activation.context().readLocalSlot("run").orElseThrow();
            return run.executionPlan().orElseThrow().bytecodeActivationRootForTesting();
        }
    }

    private static int indexAfter(List<String> names, String operation, int previous) {
        for (int index = previous + 1; index < names.size(); index++) {
            if (names.get(index).contains(operation)) {
                return index;
            }
        }
        return -1;
    }

    private static List<String> instructionNames(ProtosBytecodeRootNode root) {
        root.getRootNodes().ensureComplete();
        return root.getBytecodeNode()
                .getInstructionsAsList()
                .stream()
                .map(ProtosInstructionNames::of)
                .toList();
    }
}
