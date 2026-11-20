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
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosArrayValue;
import com.guillermomolina.protos.runtime.ProtosFloatValue;
import com.guillermomolina.protos.runtime.ProtosNumericValueSupport;
import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.guillermomolina.protos.runtime.ProtosStringValue;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProtosStandardArrayFactoryTest {
    @Test
    void currentArrayFactoryShadowsCapturedFactoryUntilRemoval() throws IOException {
        ProtosPrelude prelude = corePrelude();
        ProtosActivation activation = prelude.newModuleActivation();
        ProtosArrayValue results = (ProtosArrayValue) ProtosTestExecutionSupport.evaluate(
                """
                StandardArray: Array
                Array: (a, b) => a + b
                CapturedFactory: Array
                run: () => {
                    before: Array(2, 3)
                    Array: (a, b) => a * b
                    current: Array(2, 3)
                    context.removeSlot("Array")
                    after: Array(2, 3)
                    StandardArray(before, current, after, Array === CapturedFactory)
                }
                StandardArray(run(), run())
                """, activation);
        ProtosArrayValue first = (ProtosArrayValue) results.indexedAt(0);
        ProtosArrayValue second = (ProtosArrayValue) results.indexedAt(1);
        assertNotSame(first, second);
        for (ProtosArrayValue result : List.of(first, second)) {
            assertEquals(5L, result.indexedAt(0));
            assertEquals(6L, result.indexedAt(1));
            assertEquals(5L, result.indexedAt(2));
            assertSame(Boolean.TRUE, result.indexedAt(3));
            assertSame(prelude.arrayPrototype(), result.parent().orElseThrow());
        }
        assertSame(activation.context().readLocalSlot("CapturedFactory").orElseThrow(),
                activation.context().readLocalSlot("Array").orElseThrow());
    }

    @Test
    void dynamicArrayFactoryObservesNearerReplacementAndRemoval() throws IOException {
        ProtosPrelude prelude = corePrelude();
        ProtosActivation activation = prelude.newModuleActivation();
        Object result = ProtosTestExecutionSupport.evaluate(
                """
                make: () => Array(1, 2)
                first: make()
                Array: (a, b) => a + b
                replaced: make()
                Array = (a, b) => a * b
                reassigned: make()
                context.removeSlot("Array")
                restored: make()
                Array(first, replaced, reassigned, restored)
                """, activation);
        ProtosArrayValue values = (ProtosArrayValue) result;
        ProtosArrayValue first = (ProtosArrayValue) values.indexedAt(0);
        ProtosArrayValue restored = (ProtosArrayValue) values.indexedAt(3);
        assertEquals(3L, values.indexedAt(1));
        assertEquals(2L, values.indexedAt(2));
        assertEquals(1L, first.indexedAt(0));
        assertEquals(2L, restored.indexedAt(1));
        assertNotSame(first, restored);
        assertSame(prelude.arrayPrototype(), first.parent().orElseThrow());
        assertSame(prelude.arrayPrototype(), restored.parent().orElseThrow());
    }

    @Test
    void arrayConstructionPreservesPrimitiveMixedAndNonconstantValues() throws IOException {
        ProtosActivation activation = corePrelude().newModuleActivation();
        ProtosArrayValue values = (ProtosArrayValue) ProtosTestExecutionSupport.evaluate(
                """
                number: 7
                make: () => Array(1, 2)
                Array(Array(1, 2), Array(1.5, 2.5), Array(true, false),
                      Array(1, true, null), Array(number, 8), make(), make())
                """, activation);
        assertEquals(1L, ((ProtosArrayValue) values.indexedAt(0)).indexedAt(0));
        assertEquals(1.5d, ((ProtosArrayValue) values.indexedAt(1)).indexedAt(0));
        assertSame(Boolean.FALSE, ((ProtosArrayValue) values.indexedAt(2)).indexedAt(1));
        ProtosArrayValue mixed = (ProtosArrayValue) values.indexedAt(3);
        assertEquals(1L, mixed.indexedAt(0));
        assertSame(Boolean.TRUE, mixed.indexedAt(1));
        assertSame(ProtosNullValue.INSTANCE, mixed.indexedAt(2));
        assertEquals(7L, ((ProtosArrayValue) values.indexedAt(4)).indexedAt(0));
        assertNotSame(values.indexedAt(5), values.indexedAt(6));
    }

    // Deliberately Java-side: this verifies the represented-value materialization
    // and mutation state, while source-level factory semantics live in .protos.
    @Test
    void factoryMaterializesOpenRepresentedArrayWithInvocationReceiverParent()
            throws IOException {
        ProtosPrelude prelude = corePrelude();
        ProtosActivation activation = prelude.newModuleActivation();

        ProtosArrayValue result =
                (ProtosArrayValue)
                        ProtosTestExecutionSupport.evaluate(
                                """
                                MyArray: Array {
                                    label: 9
                                }
                                MyArray(10, 20)
                                """,
                                activation);

        Object myArray = activation.context().readLocalSlot("MyArray").orElseThrow();
        assertFalse(result.isClosed());
        assertFalse(result.isFrozen());
        assertSame(myArray, result.parent().orElseThrow());
        assertSame(
                prelude.arrayPrototype(),
                prelude.bindings().readLocalSlot("Array").orElseThrow());
    }

    // I091: indices are exact signed-64 Integers within the array; nothing is truncated.
    @Test
    void indexedAccessAdmitsOnlyExactInRangeIntegerIndices() throws IOException {
        ProtosPrelude prelude = corePrelude();
        ProtosActivation a = prelude.newModuleActivation();
        BigInteger huge = BigInteger.ONE.shiftLeft(1000).add(BigInteger.ONE);
        ProtosArrayValue array =
                prelude.newArray(
                        List.of(
                                new ProtosStringValue("first"),
                                com.guillermomolina.protos.runtime.ProtosNumericValueSupport.integerFromHost(huge, prelude),
                                new ProtosStringValue("last")));

        assertEquals("first", string(ProtosInvocation.invokeMessage(array, "at", List.of(index(0)), a)));
        assertEquals("last", string(ProtosInvocation.invokeMessage(array, "at", List.of(index(2)), a)));
        assertEquals(String.valueOf(String.valueOf(huge)), com.guillermomolina.protos.runtime.ProtosNumericValueSupport.integerDecimalText(ProtosInvocation.invokeMessage(array, "at", List.of(index(1)), a)));

        Object twoTo64 = com.guillermomolina.protos.runtime.ProtosNumericValueSupport.integerFromHost(BigInteger.ONE.shiftLeft(64), prelude);
        // 2^32 + 1 truncates to int 1, and 2^64 + 1 truncates to long 1: neither may alias index 1.
        Object[] invalid = {
            index(-1),
            index(3),
            index(Integer.MAX_VALUE),
            Long.valueOf((1L << 32) + 1),
            twoTo64,
            com.guillermomolina.protos.runtime.ProtosNumericValueSupport.integerFromHost(BigInteger.ONE.shiftLeft(64).add(BigInteger.ONE), prelude),
            new ProtosFloatValue(1.0d),
            BigInteger.ONE,
            Boolean.TRUE,
            Boolean.FALSE,
        };
        for (Object bad : invalid) {
            assertThrows(
                    ProtosSignalException.class,
                    () -> ProtosInvocation.invokeMessage(array, "at", List.of(bad), a),
                    String.valueOf(bad));
            assertThrows(
                    ProtosSignalException.class,
                    () ->
                            ProtosInvocation.invokeMessage(
                                    array, "atPut", List.of(bad, new ProtosStringValue("x")), a),
                    String.valueOf(bad));
        }
        assertEquals("first", string(array.indexedAt(0)));
        assertEquals(String.valueOf(String.valueOf(huge)), com.guillermomolina.protos.runtime.ProtosNumericValueSupport.integerDecimalText(array.indexedAt(1)));
        assertEquals("last", string(array.indexedAt(2)));
        assertEquals(3, array.indexedSize());
    }

    @Test
    void largeIntegerElementsRoundTripAndSizeIsASigned64Integer() throws IOException {
        ProtosPrelude prelude = corePrelude();
        ProtosActivation a = prelude.newModuleActivation();
        BigInteger negative = BigInteger.ONE.shiftLeft(64).negate();
        ProtosArrayValue array = prelude.newArray(List.of(Long.valueOf(0)));

        ProtosInvocation.invokeMessage(
                array, "atPut", List.of(index(0), com.guillermomolina.protos.runtime.ProtosNumericValueSupport.integerFromHost(negative, prelude)), a);

        assertEquals(String.valueOf(String.valueOf(negative)), com.guillermomolina.protos.runtime.ProtosNumericValueSupport.integerDecimalText(ProtosInvocation.invokeMessage(array, "at", List.of(index(0)), a)));
        Object size = ProtosInvocation.invokeMessage(array, "size", List.of(), a);
        assertTrue(ProtosNumericValueSupport.isIntegerInLongRange(size));
        assertEquals(String.valueOf("1"), com.guillermomolina.protos.runtime.ProtosNumericValueSupport.integerDecimalText(size));
    }

    @Test
    void frozenArrayRejectsMutationWithoutChange() throws IOException {
        ProtosPrelude prelude = corePrelude();
        ProtosActivation a = prelude.newModuleActivation();
        ProtosArrayValue array = prelude.newArray(List.of(new ProtosStringValue("kept")));
        array.freeze();

        assertThrows(
                ProtosSignalException.class,
                () ->
                        ProtosInvocation.invokeMessage(
                                array, "atPut", List.of(index(0), new ProtosStringValue("lost")), a));
        assertEquals("kept", string(array.indexedAt(0)));
    }

    @Test
    void factoryLengthsAndValuesAreIndependentOfStorage() throws IOException {
        ProtosArrayValue arrays = (ProtosArrayValue) ProtosTestExecutionSupport.evaluate(
                """
                make: () => Array(1, 2)
                Array(Array(), Array(1, 2), Array(1.5, 2.5), Array(true, false),
                      Array(1, true, null), make(), make())
                """, corePrelude().newModuleActivation());
        List<List<Object>> expected = List.of(
                List.of(), List.of(1L, 2L), List.of(1.5d, 2.5d),
                List.of(true, false), List.of(1L, true, ProtosNullValue.INSTANCE),
                List.of(1L, 2L), List.of(1L, 2L));
        for (int position = 0; position < expected.size(); position++) {
            ProtosArrayValue array = (ProtosArrayValue) arrays.indexedAt(position);
            assertEquals(expected.get(position).size(), array.indexedSize());
            assertEquals(expected.get(position), array.indexedSnapshot());
        }
        ProtosArrayValue first = (ProtosArrayValue) arrays.indexedAt(5);
        ProtosArrayValue second = (ProtosArrayValue) arrays.indexedAt(6);
        assertNotSame(first, second);
        List<Object> published = first.indexedSnapshot();
        first.indexedLongStoragePutNormalized(0, 9L);
        assertEquals(2, first.indexedSize());
        assertEquals(List.of(1L, 2L), published);
        assertEquals(List.of(9L, 2L), first.indexedSnapshot());
        assertEquals(List.of(1L, 2L), second.indexedSnapshot());
        first.indexedPutNormalized(1, Boolean.TRUE);
        assertFalse(first.hasLongStorageForRuntime());
        assertEquals(2, first.indexedSize());
        assertEquals(List.of(9L, true), first.indexedSnapshot());
    }

    @Test
    void indexedGuestWritesPreservePrimitiveFamiliesAndGeneralizeWhenNeeded()
            throws IOException {
        ProtosArrayValue arrays = (ProtosArrayValue) ProtosTestExecutionSupport.evaluate(
                """
                longs: Array(1, 2)
                doubles: Array(1.5, 2.5)
                flags: Array(true, false)
                writeLong: (value) => { longs[0] = value }
                writeDouble: (value) => { doubles[0] = value }
                writeBoolean: (value) => { flags[0] = value }
                results: Array(writeLong(9), writeDouble(9.5), writeBoolean(false))
                widened: Array(3, 4)
                widened[1] = true
                Array(longs, doubles, flags, widened, results)
                """, corePrelude().newModuleActivation());
        ProtosArrayValue longs = (ProtosArrayValue) arrays.indexedAt(0);
        ProtosArrayValue doubles = (ProtosArrayValue) arrays.indexedAt(1);
        ProtosArrayValue flags = (ProtosArrayValue) arrays.indexedAt(2);
        ProtosArrayValue widened = (ProtosArrayValue) arrays.indexedAt(3);
        assertTrue(longs.hasLongStorageForRuntime());
        assertTrue(doubles.hasDoubleStorageForRuntime());
        assertTrue(flags.hasBooleanStorageForRuntime());
        assertFalse(widened.hasLongStorageForRuntime());
        assertEquals(List.of(9L, 2L), longs.indexedSnapshot());
        assertEquals(List.of(9.5d, 2.5d), doubles.indexedSnapshot());
        assertEquals(List.of(false, false), flags.indexedSnapshot());
        assertEquals(List.of(3L, true), widened.indexedSnapshot());
        assertEquals(List.of(9L, 9.5d, false),
                ((ProtosArrayValue) arrays.indexedAt(4)).indexedSnapshot());
        for (Object value : arrays.indexedSnapshot().subList(0, 4)) {
            assertEquals(2, ((ProtosArrayValue) value).indexedSize());
        }
    }

    @Test
    void primitiveGuestWritesSignalGenericErrorsWithoutChangingElements()
            throws IOException {
        ProtosPrelude prelude = corePrelude();
        ProtosActivation activation = prelude.newModuleActivation();
        ProtosArrayValue array = prelude.newArray(List.of(1L, 2L));
        activation.context().createLocalSlot("values", array);
        Object errorPrototype = prelude.bindings().readLocalSlot("Error").orElseThrow();
        for (String source : List.of("values[-1] = 9", "values[2] = 9")) {
            ProtosSignalException failure = assertThrows(ProtosSignalException.class,
                    () -> ProtosTestExecutionSupport.evaluate(source, activation));
            assertSame(errorPrototype, failure.error().parent().orElseThrow());
        }
        array.freeze();
        for (String source : List.of("values[0] = 9", "values[-1] = 9")) {
            ProtosSignalException failure = assertThrows(ProtosSignalException.class,
                    () -> ProtosTestExecutionSupport.evaluate(source, activation));
            assertSame(errorPrototype, failure.error().parent().orElseThrow());
        }
        assertTrue(array.hasLongStorageForRuntime());
        assertEquals(2, array.indexedSize());
        assertEquals(List.of(1L, 2L), array.indexedSnapshot());
    }

    @Test
    void hotIndexedOperationsObserveAtPutAndSizeRedefinitions() throws IOException {
        ProtosArrayValue results = (ProtosArrayValue) ProtosTestExecutionSupport.evaluate(
                """
                values: Array(1, 2)
                write: (value) => { values[0] = value }
                length: () => values.size()
                write(3)
                before: length()
                values.atPut: (index, value) => { 41 }
                values.size: () => 99
                assigned: write(7)
                selected: values.atPut(0, 8)
                Array(values, before, length(), assigned, selected)
                """, corePrelude().newModuleActivation());
        ProtosArrayValue values = (ProtosArrayValue) results.indexedAt(0);
        assertEquals(List.of(3L, 2L), values.indexedSnapshot());
        assertEquals(2, values.indexedSize());
        assertEquals(2L, results.indexedAt(1));
        assertEquals(99L, results.indexedAt(2));
        assertEquals(7L, results.indexedAt(3));
        assertEquals(41L, results.indexedAt(4));
    }

    @Test
    void repeatedConstantLiteralsYieldFreshArraysWithPrivateBackings()
            throws IOException {
        ProtosPrelude prelude = corePrelude();
        ProtosActivation activation = prelude.newModuleActivation();
        ProtosArrayValue results = (ProtosArrayValue) ProtosTestExecutionSupport.evaluate(
                """
                ints: () => Array(1, 2, 3)
                floats: () => Array(1.5, 2.5, 0.5)
                flags: () => Array(true, false, true)
                i1: ints()
                i1[0] = 9
                i2: ints()
                f1: floats()
                f1[1] = 7.5
                f2: floats()
                b1: flags()
                b1[2] = false
                b2: flags()
                b2.freeze()
                rejected: Error.handle(() => { b2[0] = false; false }, (caught) => true)
                b3: flags()
                b3[0] = false
                run: () => {
                    Array: (a, b, c) => a
                    Array(1, 2, 3)
                }
                shadowed: run()
                Array(i1, i2, f1, f2, b1, b2, b3, rejected, shadowed, ints())
                """, activation);
        ProtosArrayValue i1 = (ProtosArrayValue) results.indexedAt(0);
        ProtosArrayValue i2 = (ProtosArrayValue) results.indexedAt(1);
        ProtosArrayValue f1 = (ProtosArrayValue) results.indexedAt(2);
        ProtosArrayValue f2 = (ProtosArrayValue) results.indexedAt(3);
        ProtosArrayValue b1 = (ProtosArrayValue) results.indexedAt(4);
        ProtosArrayValue b2 = (ProtosArrayValue) results.indexedAt(5);
        ProtosArrayValue b3 = (ProtosArrayValue) results.indexedAt(6);
        ProtosArrayValue after = (ProtosArrayValue) results.indexedAt(9);

        assertNotSame(i1, i2);
        assertNotSame(i2, after);
        assertNotSame(f1, f2);
        assertNotSame(b1, b2);
        assertNotSame(b2, b3);
        for (ProtosArrayValue array : List.of(i1, i2, f1, f2, b1, b2, b3, after)) {
            assertSame(prelude.arrayPrototype(), array.parent().orElseThrow());
            assertEquals(3, array.indexedSize());
        }
        assertTrue(i1.hasLongStorageForRuntime() && i2.hasLongStorageForRuntime());
        assertTrue(f1.hasDoubleStorageForRuntime() && f2.hasDoubleStorageForRuntime());
        assertTrue(b1.hasBooleanStorageForRuntime() && b2.hasBooleanStorageForRuntime());
        assertEquals(List.of(9L, 2L, 3L), i1.indexedSnapshot());
        assertEquals(List.of(1L, 2L, 3L), i2.indexedSnapshot());
        assertEquals(List.of(1L, 2L, 3L), after.indexedSnapshot());
        assertEquals(List.of(1.5d, 7.5d, 0.5d), f1.indexedSnapshot());
        assertEquals(List.of(1.5d, 2.5d, 0.5d), f2.indexedSnapshot());
        assertEquals(List.of(true, false, false), b1.indexedSnapshot());
        assertEquals(List.of(true, false, true), b2.indexedSnapshot());
        assertEquals(List.of(false, false, true), b3.indexedSnapshot());
        assertTrue(b2.isFrozen());
        assertFalse(b3.isFrozen());
        assertSame(Boolean.TRUE, results.indexedAt(7));
        assertEquals(1L, results.indexedAt(8),
                "A nonstandard Array factory keeps its ordinary call");
    }

    private static Object index(long value) {
        return Long.valueOf(value);
    }

    private static String string(Object value) {
        return ((ProtosStringValue) value).value();
    }

    private static ProtosPrelude corePrelude() throws IOException {
        return new ProtosCoreBootstrap().bootstrap(Path.of("protos", "lib", "core"));
    }
}
