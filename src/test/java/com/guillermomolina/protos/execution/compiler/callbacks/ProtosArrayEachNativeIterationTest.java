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

    /*
     * A hot standard each keeps its native iteration across repeated
     * executions; a later effective redefinition on the receiver is selected
     * by the very next execution instead of the cached admission.
     */
    @Test
    void hotStandardEachStaysNativeUntilItsSelectionChanges() throws Exception {
        inContext(() -> {
            Module module = new Module(null);
            module.run(
                    """
                    values: Array(1, 2)
                    run: () => {
                        marker: 0
                        values.each((element) => {
                            hook(element)
                        })
                    }
                    loop: () => {
                        n: 0
                        (() => n < 20).whileTrue() {
                            run()
                            n = n + 1
                        }
                        0
                    }
                    loop()
                    values.each: (callback) => {
                        callback(99)
                        42
                    }
                    run()
                    """);
            assertEquals(41, module.visits.size());
            for (int index = 0; index < 40; index++) {
                assertEquals(index % 2 == 0 ? 1L : 2L, module.visits.get(index),
                        "callbacks keep their element order");
            }
            assertEquals(99L, module.visits.get(40),
                    "the redefined each on the Array is selected after the hot standard one");
            assertNativeLoop(module);
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

            // A callback observing its context still runs inline: each
            // invocation materializes its own activation lazily, and that
            // activation keeps its bindings once the invocation ends.
            List<String> names = instructionNames(module.physicalRun());
            assertTrue(names.stream().anyMatch(name -> name.contains("c.ArrayEachNext")),
                    () -> "the observing callback runs in the caller: " + names);
            assertTrue(names.stream().anyMatch(name -> name.contains("c.PublishInlineCallbackContext")),
                    () -> "its context is published lazily: " + names);
        });
    }

    /** The physical root of {@code run} iterates natively, without per-element dispatch. */
    /*
     * The normalized storage writes used by the standard indexed operations
     * keep long storage primitive, detach a published generation before
     * writing, and leave every published snapshot unchanged.
     */
    @Test
    void normalizedLongWritesKeepPrimitiveStorageAndSnapshots() {
        ProtosObjectValue parent = ProtosObjectValue.rootObject();
        com.guillermomolina.protos.runtime.ProtosArrayValue longs =
                new com.guillermomolina.protos.runtime.ProtosArrayValue(
                        parent, new long[] {1L, 2L, 3L});
        ProtosBytecodeRootNode.ArrayEachSnapshot snapshot =
                ProtosBytecodeRootNode.GetArrayEachSnapshot.snapshot(longs);

        assertEquals(20L, longs.indexedLongPutNormalized(1, 20L));
        assertTrue(longs.hasLongStorageForRuntime());
        assertEquals(20L, longs.indexedLongAtNormalized(1));
        assertEquals(List.of(1L, 20L, 3L), longs.indexedSnapshot());

        assertTrue(snapshot.nextIsLong());
        assertEquals(1L, snapshot.advance());
        assertEquals(2L, snapshot.advance());
        assertEquals(3L, snapshot.advance());
        assertFalse(snapshot.hasNext());
    }

    @Test
    void normalizedObjectWriteWidensWithoutChangingEarlierSnapshots() {
        ProtosObjectValue parent = ProtosObjectValue.rootObject();
        com.guillermomolina.protos.runtime.ProtosArrayValue array =
                new com.guillermomolina.protos.runtime.ProtosArrayValue(
                        parent, new long[] {1L, 2L, 3L});
        List<Object> before = array.indexedSnapshot();
        Object replacement = new ProtosObjectValue(parent);

        assertTrue(array.indexedPutNormalized(1, replacement) == replacement);
        assertFalse(array.hasLongStorageForRuntime());
        assertEquals(1L, array.indexedAtNormalized(0));
        assertTrue(array.indexedAtNormalized(1) == replacement);
        assertEquals(3L, array.indexedAtNormalized(2));
        assertEquals(List.of(1L, 2L, 3L), before);

        array.indexedLongPutNormalized(2, 30L);
        assertFalse(array.hasLongStorageForRuntime());
        assertTrue(array.indexedAt(1) == replacement);
        assertEquals(30L, array.indexedAt(2));
    }

    @Test
    void frozenArrayKeepsReadsAndRejectsCheckedWritesWithoutCorruption() {
        ProtosObjectValue parent = ProtosObjectValue.rootObject();
        com.guillermomolina.protos.runtime.ProtosArrayValue array =
                new com.guillermomolina.protos.runtime.ProtosArrayValue(
                        parent, new long[] {4L, 5L});
        List<Object> snapshot = array.indexedSnapshot();
        array.freeze();

        assertEquals(5L, array.indexedLongAt(1));
        assertThrows(IllegalStateException.class, () -> array.indexedLongPut(0, 9L));
        assertThrows(IllegalStateException.class,
                () -> array.indexedPut(0, new ProtosObjectValue(parent)));
        assertThrows(IndexOutOfBoundsException.class, () -> array.indexedAt(2));
        assertTrue(array.hasLongStorageForRuntime());
        assertEquals(List.of(4L, 5L), array.indexedSnapshot());
        assertEquals(List.of(4L, 5L), snapshot);
        assertTrue(evalBooleanInProtos("""
                a: [4, 5]
                a.freeze()
                rejected: Error.handle(() => { a[0] = 9; false }, (caught) => true)
                rejected && (a[0] == 4) && (a[1] == 5) && (a.size() == 2)
                """));
        assertTrue(evalBooleanInProtos("""
                a: [1, 2, 3]
                seen: { value: 0 }
                a.each((x) => {
                    seen.value = seen.value * 10 + x
                    a[0] = 9
                })
                (seen.value == 123) && (a[0] == 9) && (a[1] == 2)
                """));
    }

    private static boolean evalBooleanInProtos(String source) {
        try (Context context = Context.newBuilder(ProtosLanguage.ID)
                .option("protos.CoreRoot", CORE.toAbsolutePath().toString())
                .build()) {
            return context.eval(ProtosLanguage.ID, source).asBoolean();
        }
    }

    /**
     * The native each snapshot is the published indexed generation: known
     * length, ascending indexed reads of the exact elements (primitive
     * storage included) and independent of writes after it was taken.
     */
    @Test
    void eachSnapshotIsAnIndexedGenerationIndependentOfLaterWrites() {
        ProtosObjectValue parent = ProtosObjectValue.rootObject();
        Object first = new ProtosObjectValue(parent);
        Object second = new ProtosObjectValue(parent);
        com.guillermomolina.protos.runtime.ProtosArrayValue objects =
                new com.guillermomolina.protos.runtime.ProtosArrayValue(parent, List.of(first, second));
        com.guillermomolina.protos.runtime.ProtosArrayValue longs =
                new com.guillermomolina.protos.runtime.ProtosArrayValue(parent, new long[] {3L, 4L});

        ProtosBytecodeRootNode.ArrayEachSnapshot objectSnapshot =
                ProtosBytecodeRootNode.GetArrayEachSnapshot.snapshot(objects);
        ProtosBytecodeRootNode.ArrayEachSnapshot longSnapshot =
                ProtosBytecodeRootNode.GetArrayEachSnapshot.snapshot(longs);
        objects.indexedPut(0, new ProtosObjectValue(parent));
        longs.indexedLongPut(1, 40L);
        assertTrue(longs.hasLongStorageForRuntime(), "long storage is never boxed by snapshots");

        assertFalse(objectSnapshot.nextIsLong());
        assertTrue(objectSnapshot.advance() == first);
        assertTrue(objectSnapshot.advance() == second);
        assertFalse(objectSnapshot.hasNext());

        assertTrue(longSnapshot.nextIsLong());
        assertEquals(3L, longSnapshot.advance());
        assertEquals(4L, longSnapshot.advance());
        assertFalse(longSnapshot.hasNext());
        assertEquals(40L, longs.indexedLongAt(1));
    }

    /**
     * Each prepared callback carries exactly its snapshot position as the
     * single B5 positional argument; preparation reads the generation by
     * index and never re-snapshots.
     */
    @Test
    void preparedArrayEachBuildsOneB5CallPerSnapshotPosition() throws Exception {
        inContext(() -> {
            ProtosActivation activation =
                    new ProtosCoreBootstrap().bootstrap(CORE).newModuleActivation();
            ProtosObjectValue parent = ProtosObjectValue.rootObject();
            Object first = new ProtosObjectValue(parent);
            Object second = new ProtosObjectValue(parent);
            com.guillermomolina.protos.runtime.ProtosArrayValue array =
                    new com.guillermomolina.protos.runtime.ProtosArrayValue(parent, List.of(first, second));
            ProtosClosureValue block = ProtosClosureValue.nativeClosure((a, x) -> x.get(0));

            ProtosStructuredEachCalls.PreparedArrayEachCall each =
                    new ProtosStructuredEachCalls.PreparedArrayEachCall(
                            array, new Object[] {block}, activation);
            array.indexedPut(1, new ProtosObjectValue(parent));

            List<Object> visited = new ArrayList<>();
            while (each.hasNext()) {
                ProtosCallRequest request = each.prepareCurrent();
                Object[] arguments = request.arguments();
                assertEquals(ProtosFrameArguments.HEADER_LENGTH + 1, arguments.length);
                assertTrue(request.callable() == block);
                assertTrue(request.caller() == activation);
                visited.add(arguments[ProtosFrameArguments.HEADER_LENGTH]);
                each.advance();
            }
            assertEquals(2, visited.size());
            assertTrue(visited.get(0) == first && visited.get(1) == second);
            assertTrue(each.finish() == array);
        });
    }

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
