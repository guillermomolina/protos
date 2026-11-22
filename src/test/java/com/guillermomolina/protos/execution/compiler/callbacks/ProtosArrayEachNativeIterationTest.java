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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.parser.ProtosParser;
import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosArrayValue;
import com.guillermomolina.protos.runtime.ProtosIndexedSnapshot;
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

    @Test
    void specializedPrimitiveWritesDetachAndKeepPublishedSnapshotIdentity() {
        ProtosObjectValue parent = ProtosObjectValue.rootObject();
        ProtosArrayValue longs = new ProtosArrayValue(
                parent, new long[] {1L, 2L, 3L});
        ProtosIndexedSnapshot longsBefore = longs.indexedGenerationSnapshot();
        List<Object> longsList = longs.indexedSnapshot();
        ProtosBytecodeRootNode.ArrayEachSnapshot longsEach =
                ProtosBytecodeRootNode.GetArrayEachSnapshot.snapshot(longs);
        assertSame(longsBefore, longs.indexedGenerationSnapshot());
        assertEquals(10L, longs.indexedLongStoragePutNormalized(0, 10L));
        longs.indexedLongStoragePutNormalized(1, 20L);
        longs.indexedLongStoragePutNormalized(2, 30L);
        assertTrue(longs.hasLongStorageForRuntime());
        assertEquals(3, longs.indexedSize());
        assertEquals(3, longsBefore.length());
        assertEquals(List.of(10L, 20L, 30L), longs.indexedSnapshot());
        assertEquals(List.of(1L, 2L, 3L), longsList);
        for (int index = 0; index < longsBefore.length(); index++) {
            assertTrue(longsEach.hasNext());
            assertEquals(longsList.get(index), longsBefore.at(index));
            assertEquals(longsBefore.at(index), longsEach.advance());
        }
        assertFalse(longsEach.hasNext());
        ProtosIndexedSnapshot longsAfter = longs.indexedGenerationSnapshot();
        assertNotSame(longsBefore, longsAfter);
        assertSame(longsAfter, longs.indexedGenerationSnapshot());
        ProtosArrayValue doubles = new ProtosArrayValue(
                parent, new double[] {1.5d, 2.5d, 3.5d});
        ProtosIndexedSnapshot doublesBefore = doubles.indexedGenerationSnapshot();
        List<Object> doublesList = doubles.indexedSnapshot();
        ProtosBytecodeRootNode.ArrayEachSnapshot doublesEach =
                ProtosBytecodeRootNode.GetArrayEachSnapshot.snapshot(doubles);
        assertSame(doublesBefore, doubles.indexedGenerationSnapshot());
        assertEquals(10.5d, doubles.indexedDoubleStoragePutNormalized(0, 10.5d));
        doubles.indexedDoubleStoragePutNormalized(1, 20.5d);
        doubles.indexedDoubleStoragePutNormalized(2, 30.5d);
        assertTrue(doubles.hasDoubleStorageForRuntime());
        assertEquals(3, doubles.indexedSize());
        assertEquals(3, doublesBefore.length());
        assertEquals(List.of(10.5d, 20.5d, 30.5d), doubles.indexedSnapshot());
        assertEquals(List.of(1.5d, 2.5d, 3.5d), doublesList);
        for (int index = 0; index < doublesBefore.length(); index++) {
            assertTrue(doublesEach.hasNext());
            assertEquals(doublesList.get(index), doublesBefore.at(index));
            assertEquals(doublesBefore.at(index), doublesEach.advance());
        }
        assertFalse(doublesEach.hasNext());
        ProtosIndexedSnapshot doublesAfter = doubles.indexedGenerationSnapshot();
        assertNotSame(doublesBefore, doublesAfter);
        assertSame(doublesAfter, doubles.indexedGenerationSnapshot());
        ProtosArrayValue flags = new ProtosArrayValue(
                parent, new boolean[] {true, false, true});
        ProtosIndexedSnapshot flagsBefore = flags.indexedGenerationSnapshot();
        List<Object> flagsList = flags.indexedSnapshot();
        ProtosBytecodeRootNode.ArrayEachSnapshot flagsEach =
                ProtosBytecodeRootNode.GetArrayEachSnapshot.snapshot(flags);
        assertSame(flagsBefore, flags.indexedGenerationSnapshot());
        assertEquals(false, flags.indexedBooleanStoragePutNormalized(0, false));
        flags.indexedBooleanStoragePutNormalized(1, true);
        flags.indexedBooleanStoragePutNormalized(2, false);
        assertTrue(flags.hasBooleanStorageForRuntime());
        assertEquals(3, flags.indexedSize());
        assertEquals(3, flagsBefore.length());
        assertEquals(List.of(false, true, false), flags.indexedSnapshot());
        assertEquals(List.of(true, false, true), flagsList);
        for (int index = 0; index < flagsBefore.length(); index++) {
            assertTrue(flagsEach.hasNext());
            assertEquals(flagsList.get(index), flagsBefore.at(index));
            assertEquals(flagsBefore.at(index), flagsEach.advance());
        }
        assertFalse(flagsEach.hasNext());
        ProtosIndexedSnapshot flagsAfter = flags.indexedGenerationSnapshot();
        assertNotSame(flagsBefore, flagsAfter);
        assertSame(flagsAfter, flags.indexedGenerationSnapshot());
    }

    @Test
    void promotionAfterPrimitivePublicationKeepsBothSnapshotViews() {
        ProtosObjectValue parent = ProtosObjectValue.rootObject();
        ProtosArrayValue array = new ProtosArrayValue(parent, new long[] {1L, 2L});
        ProtosIndexedSnapshot before = array.indexedGenerationSnapshot();
        List<Object> beforeList = array.indexedSnapshot();
        ProtosBytecodeRootNode.ArrayEachSnapshot each =
                ProtosBytecodeRootNode.GetArrayEachSnapshot.snapshot(array);
        Object replacement = new ProtosObjectValue(parent);
        assertSame(replacement, array.indexedPutNormalized(0, replacement));
        array.indexedLongPutNormalized(1, 20L);
        assertFalse(array.hasLongStorageForRuntime());
        assertEquals(2, array.indexedSize());
        assertEquals(2, before.length());
        assertEquals(List.of(1L, 2L), beforeList);
        assertEquals(1L, before.at(0));
        assertEquals(2L, before.at(1));
        assertEquals(1L, each.advance());
        assertEquals(2L, each.advance());
        assertFalse(each.hasNext());
        assertSame(replacement, array.indexedAt(0));
        assertEquals(20L, array.indexedAt(1));
        assertNotSame(before, array.indexedGenerationSnapshot());
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
     * Float and Boolean Arrays keep the same snapshot contract as the
     * Object and signed-64 representations: ascending exact elements,
     * independent of writes after the snapshot was taken.
     */
    @Test
    void floatAndBooleanSnapshotsAreIndependentOfLaterWrites() {
        ProtosObjectValue parent = ProtosObjectValue.rootObject();
        com.guillermomolina.protos.runtime.ProtosArrayValue doubles =
                new com.guillermomolina.protos.runtime.ProtosArrayValue(
                        parent, new double[] {1.5, -0.0});
        com.guillermomolina.protos.runtime.ProtosArrayValue booleans =
                new com.guillermomolina.protos.runtime.ProtosArrayValue(
                        parent, new boolean[] {true, false});

        ProtosBytecodeRootNode.ArrayEachSnapshot doubleSnapshot =
                ProtosBytecodeRootNode.GetArrayEachSnapshot.snapshot(doubles);
        ProtosBytecodeRootNode.ArrayEachSnapshot booleanSnapshot =
                ProtosBytecodeRootNode.GetArrayEachSnapshot.snapshot(booleans);
        doubles.indexedDoublePut(0, 9.0);
        booleans.indexedPut(1, new ProtosObjectValue(parent));
        assertTrue(doubles.hasDoubleStorageForRuntime());
        assertFalse(booleans.hasBooleanStorageForRuntime(),
                "an incompatible write promotes the current generation only");

        assertFalse(doubleSnapshot.nextIsLong());
        assertEquals(1.5, doubleSnapshot.advance());
        assertEquals(Double.valueOf(-0.0), doubleSnapshot.advance());
        assertFalse(doubleSnapshot.hasNext());
        assertEquals(9.0, doubles.indexedDoubleAt(0));

        assertEquals(Boolean.TRUE, booleanSnapshot.advance());
        assertEquals(Boolean.FALSE, booleanSnapshot.advance());
        assertFalse(booleanSnapshot.hasNext());
        assertEquals(Boolean.TRUE, booleans.indexedAt(0));
    }

    @Test
    void floatElementsReplacedDuringIterationKeepTheSnapshot() throws Exception {
        inContext(() -> {
            Module module = new Module(null);
            module.run(
                    """
                    run: () => {
                        values: [1.5, 2.5, 3.5]
                        values.each((element) => {
                            values.atPut(1, 100.5)
                            hook(element)
                        })
                        values
                    }
                    run()
                    """);
            assertEquals(List.of(1.5, 2.5, 3.5), module.visits);
            assertNativeLoop(module);
        });
    }

    @Test
    void booleanElementsReplacedDuringIterationKeepTheSnapshot() throws Exception {
        inContext(() -> {
            Module module = new Module(null);
            module.run(
                    """
                    run: () => {
                        values: [true, false, true]
                        values.each((element) => {
                            values.atPut(1, 7)
                            hook(element)
                        })
                        values
                    }
                    run()
                    """);
            assertEquals(List.of(true, false, true), module.visits);
            assertNativeLoop(module);
        });
    }

    @Test
    void earlyReturnFromFloatAndBooleanIterationStopsLaterCallbacks() {
        assertTrue(evalBooleanInProtos("""
                count: { value: 0 }
                firstFloat: () => {
                    [1.5, 2.5].each((element) => {
                        count.value = count.value + 1
                        ^element
                    })
                    0
                }
                firstBoolean: () => {
                    [false, true].each((element) => {
                        count.value = count.value + 1
                        ^element
                    })
                    true
                }
                (firstFloat() == 1.5) && (firstBoolean() == false) && (count.value == 2)
                """));
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

    /*
     * One each site alternates between a saved standard Array, a receiver
     * whose own each retains its callback, and the standard Array again. The
     * retained callback keeps run's binding of total after run returns.
     */
    @Test
    void retainedArrayCallbackKeepsItsLexicalBindingAcrossSelections() throws Exception {
        inContext(() -> {
            Module module = new Module(null);
            Object outcome = module.run(
                    """
                    store: { block: null }
                    keeper: {
                        each: (block) => {
                            store.block = block
                            store
                        }
                    }
                    saved: [1, 2, 3]
                    run: (receiver) => {
                        frame: 0
                        total: 100
                        returned: receiver.each((element) => {
                            total = total + element
                            hook(total)
                        })
                        if(returned === store) {
                            total = 1000
                            store.block(5)
                        }
                        total
                    }
                    a: run(saved)
                    b: run(keeper)
                    c: run(saved)
                    store.block(10)
                    store.block(20)
                    ((a == 106) && (b == 1005) && (c == 106))
                        .ifTrueIfFalse(() => { 1 }, () => { 0 })
                    """);
            assertEquals("1", ProtosNumericValueSupport.integerDecimalText(outcome));
            assertEquals(
                    List.of(101L, 103L, 106L, 1005L, 101L, 103L, 106L, 1015L, 1035L),
                    module.visits);
            assertNativeLoop(module);
        });
    }

    @Test
    void associationCallbacksWithTwoParametersShareTheCapturedAccumulator() throws Exception {
        inContext(() -> {
            Module module = new Module(null);
            Object outcome = module.run(
                    """
                    store: { block: null }
                    keeper: {
                        each: (block) => {
                            store.block = block
                            store
                        }
                    }
                    m: Map()
                    m[1] = 10
                    m[2] = 20
                    run: (receiver) => {
                        frame: 0
                        total: 0
                        receiver.each((key, value) => {
                            total = total + (key * value)
                        })
                        hook(total)
                        total
                    }
                    a: run(m)
                    b: run(keeper)
                    store.block(3, 4)
                    c: run(m)
                    ((a == 50) && (b == 0) && (c == 50))
                        .ifTrueIfFalse(() => { 1 }, () => { 0 })
                    """);
            assertEquals("1", ProtosNumericValueSupport.integerDecimalText(outcome));
            assertEquals(List.of(50L, 0L, 50L), module.visits);
        });
    }

    @Test
    void nestedArrayCallbacksShareOneOuterBinding() throws Exception {
        inContext(() -> {
            Module module = new Module(null);
            Object outcome = module.run(
                    """
                    run: () => {
                        frame: 0
                        total: 0
                        [1, 2].each((outer) => {
                            [10, 20].each((inner) => {
                                total = total + (outer * inner)
                            })
                            hook(total)
                        })
                        total
                    }
                    run()
                    """);
            assertEquals("90", ProtosNumericValueSupport.integerDecimalText(outcome));
            assertEquals(List.of(30L, 90L), module.visits);
        });
    }

    /*
     * One Array each site alternates with a receiver that retains its
     * callback: callbacks reading their context get their own activation per
     * visit, and the retained callback keeps the captured accumulator.
     */
    @Test
    void contextObservingAndRetainedArrayCallbacksKeepTheirOwnActivations() throws Exception {
        inContext(() -> {
            Module module = new Module(null);
            Object outcome = module.run(
                    """
                    store: {
                        block: null
                        seen: null
                    }
                    keeper: {
                        each: (block) => {
                            store.block = block
                            store
                        }
                    }
                    run: (receiver) => {
                        frame: 0
                        own: context
                        total: 0
                        h: {
                            previous: null
                            distinct: 0
                        }
                        receiver.each((e) => {
                            c: context
                            if((c !== own) && (c !== h.previous) && (c.hasSlot("e"))) {
                                h.distinct = h.distinct + 1
                            }
                            h.previous = c
                            store.seen = c
                            total = total + e
                            hook(total)
                        })
                        total = total + 100
                        total * 10 + h.distinct
                    }
                    a: run([1, 2, 3])
                    inline: store.seen
                    b: run(keeper)
                    store.block(4)
                    retained: store.seen
                    c: run([5])
                    ((a == 1063) && (b == 1000) && (c == 1051) && (retained !== inline) && (retained !== store.seen))
                        .ifTrueIfFalse(() => { 1 }, () => { 0 })
                    """);
            assertEquals("1", ProtosNumericValueSupport.integerDecimalText(outcome));
            assertEquals(List.of(1L, 3L, 6L, 104L, 5L), module.visits);
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
