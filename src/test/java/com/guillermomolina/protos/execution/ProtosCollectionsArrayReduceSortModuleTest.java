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

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosArrayValue;
import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosIntegerValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ProtosCollectionsArrayReduceSortModuleTest {
    private static final Path CORE = Path.of("protos", "lib", "core");
    private static final Path STANDARD_LIBRARY = Path.of("protos", "lib");

    @Test
    void reduceIsStrictLeftFoldOverPreCallbackSnapshotWithOptionalInitial()
            throws Exception {
        ProtosArrayValue result =
                runArray(
                        """
                        Arrays: import("std:collections/Array")

                        source: Array(10, 3, 2, 1)
                        calls: 0
                        reduced: Arrays.reduce(source, (accumulator, element) => {
                            (calls == 0).ifTrue(() => {
                                source.atPut(2, 99)
                            })
                            calls = calls + 1
                            accumulator - element
                        })

                        withInitialCalls: 0
                        withInitial: Arrays.reduce(
                            Array(10, 3, 2, 1),
                            (accumulator, element) => {
                                withInitialCalls = withInitialCalls + 1
                                accumulator - element
                            },
                            20
                        )

                        Array(
                            reduced,
                            calls,
                            source.at(2),
                            withInitial,
                            withInitialCalls
                        )
                        """);

        assertInteger(4, result.indexedAt(BigInteger.ZERO));
        assertInteger(3, result.indexedAt(BigInteger.ONE));
        assertInteger(99, result.indexedAt(BigInteger.TWO));
        assertInteger(4, result.indexedAt(BigInteger.valueOf(3)));
        assertInteger(4, result.indexedAt(BigInteger.valueOf(4)));
    }

    @Test
    void reduceEmptySingletonAndInitialCasesDoNotInspectUnusedReducer()
            throws Exception {
        ProtosArrayValue result =
                runArray(
                        """
                        Arrays: import("std:collections/Array")
                        marker: Array(7)

                        empty: Arrays.reduce(Array(), 42)
                        singleton: Arrays.reduce(Array(marker), 42)
                        seededEmpty: Arrays.reduce(Array(), 42, marker)

                        Array(
                            empty === null,
                            singleton === marker,
                            seededEmpty === marker
                        )
                        """);

        assertSame(ProtosBooleanValue.TRUE, result.indexedAt(BigInteger.ZERO));
        assertSame(ProtosBooleanValue.TRUE, result.indexedAt(BigInteger.ONE));
        assertSame(ProtosBooleanValue.TRUE, result.indexedAt(BigInteger.TWO));
    }

    @Test
    void reduceRejectsExtraInitialValuesAndBadReducerArityWhenInvoked() {
        assertSignals(
                """
                Arrays: import("std:collections/Array")
                Arrays.reduce(Array(), (accumulator, element) => { accumulator }, 1, 2)
                """);

        assertSignals(
                """
                Arrays: import("std:collections/Array")
                Arrays.reduce(Array(1, 2), (accumulator) => { accumulator })
                """);
    }

    @Test
    void sortUsesCanonicalStableMergeTreeSnapshotAndComparatorOrder()
            throws Exception {
        ProtosArrayValue result =
                runArray(
                        """
                        Arrays: import("std:collections/Array")
                        source: Array(3, 1, 2)
                        trace: Map()
                        calls: 0

                        sorted: Arrays.sort(source, (left, right) => {
                            (calls == 0).ifTrue(() => {
                                source.atPut(0, 99)
                            })
                            trace.atPut(calls, left * 10 + right)
                            calls = calls + 1
                            left < right
                        })

                        Array(
                            sorted !== source,
                            sorted.at(0),
                            sorted.at(1),
                            sorted.at(2),
                            source.at(0),
                            calls,
                            trace.at(0),
                            trace.at(1),
                            trace.at(2),
                            trace.at(3),
                            trace.at(4),
                            trace.at(5)
                        )
                        """);

        assertSame(ProtosBooleanValue.TRUE, result.indexedAt(BigInteger.ZERO));
        assertInteger(1, result.indexedAt(BigInteger.ONE));
        assertInteger(2, result.indexedAt(BigInteger.TWO));
        assertInteger(3, result.indexedAt(BigInteger.valueOf(3)));
        assertInteger(99, result.indexedAt(BigInteger.valueOf(4)));
        assertInteger(6, result.indexedAt(BigInteger.valueOf(5)));
        assertInteger(12, result.indexedAt(BigInteger.valueOf(6)));
        assertInteger(21, result.indexedAt(BigInteger.valueOf(7)));
        assertInteger(31, result.indexedAt(BigInteger.valueOf(8)));
        assertInteger(13, result.indexedAt(BigInteger.valueOf(9)));
        assertInteger(32, result.indexedAt(BigInteger.valueOf(10)));
        assertInteger(23, result.indexedAt(BigInteger.valueOf(11)));

        ProtosArrayValue sorted =
                assertInstanceOf(
                        ProtosArrayValue.class,
                        run(
                                """
                                Arrays: import("std:collections/Array")
                                Arrays.sort(Array(2, 1), (left, right) => {
                                    left < right
                                })
                                """));
        assertFalse(sorted.isClosed());
        assertFalse(sorted.isFrozen());
    }

    @Test
    void sortIsStableForComparatorEquivalentDistinctElements() throws Exception {
        ProtosArrayValue result =
                runArray(
                        """
                        Arrays: import("std:collections/Array")
                        first: Array(1, 10)
                        second: Array(1, 20)
                        zero: Array(0, 30)

                        sorted: Arrays.sort(
                            Array(first, second, zero),
                            (left, right) => {
                                left.at(0) < right.at(0)
                            }
                        )

                        Array(
                            sorted.at(0) === zero,
                            sorted.at(1) === first,
                            sorted.at(2) === second
                        )
                        """);

        assertSame(ProtosBooleanValue.TRUE, result.indexedAt(BigInteger.ZERO));
        assertSame(ProtosBooleanValue.TRUE, result.indexedAt(BigInteger.ONE));
        assertSame(ProtosBooleanValue.TRUE, result.indexedAt(BigInteger.TWO));
    }

    @Test
    void sortEmptyAndSingletonDoNotInspectComparator() throws Exception {
        ProtosArrayValue result =
                runArray(
                        """
                        Arrays: import("std:collections/Array")
                        marker: Array(7)
                        empty: Arrays.sort(Array(), 42)
                        singleton: Arrays.sort(Array(marker), 42)

                        Array(
                            empty.size(),
                            singleton.size(),
                            singleton.at(0) === marker,
                            empty !== singleton
                        )
                        """);

        assertInteger(0, result.indexedAt(BigInteger.ZERO));
        assertInteger(1, result.indexedAt(BigInteger.ONE));
        assertSame(ProtosBooleanValue.TRUE, result.indexedAt(BigInteger.TWO));
        assertSame(
                ProtosBooleanValue.TRUE,
                result.indexedAt(BigInteger.valueOf(3)));
    }

    @Test
    void sortSignalsFreshComparatorResultAndOrderOccurrences() throws Exception {
        assertComparatorError(
                "InvalidComparatorResult",
                """
                Arrays: import("std:collections/Array")
                Arrays.sort(Array(2, 1), (left, right) => { 123 })
                """);

        assertComparatorError(
                "InvalidComparatorOrder",
                """
                Arrays: import("std:collections/Array")
                Arrays.sort(Array(2, 1), (left, right) => { true })
                """);
    }

    @Test
    void reduceAndSortWrongSourceFailBeforeAnyUserCallback() throws Exception {
        for (String operation : new String[] {"reduce", "sort"}) {
            ProtosStandardLibraryModuleResolver resolver =
                    new ProtosStandardLibraryModuleResolver(STANDARD_LIBRARY);
            ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE, resolver);
            ProtosActivation activation = prelude.newModuleActivation();
            AtomicInteger calls = new AtomicInteger();

            ProtosClosureValue callback =
                    ProtosClosureValue.nativeClosure(
                            (callbackActivation, supplied) -> {
                                calls.incrementAndGet();
                                return ProtosBooleanValue.TRUE;
                            });
            ProtosObjectValue wrongSource =
                    new ProtosObjectValue(ProtosObjectValue.rootObject());

            activation.context().createLocalSlot("callback", callback);
            activation.context().createLocalSlot("wrongSource", wrongSource);

            assertThrows(
                    ProtosSignalException.class,
                    () ->
                            new ProtosSourceCompiler()
                                    .compile(
                                            """
                                            Arrays: import("std:collections/Array")
                                            Arrays.%s(wrongSource, callback)
                                            """
                                                    .formatted(operation))
                                    .call(activation),
                    operation);
            assertEquals(0, calls.get(), operation);
        }
    }

    @Test
    void lib001ArrayAndSetSurfacesComposeWithoutNewCollectionHierarchy()
            throws Exception {
        ProtosArrayValue result =
                runArray(
                        """
                        Arrays: import("std:collections/Array")
                        Set: import("std:collections/Set")
                        IdentitySet: import("std:collections/IdentitySet")

                        mixed: Array(3, 1, 2, 2.0)
                        sorted: Arrays.sort(mixed, (left, right) => {
                            left < right
                        })
                        normalSet: Set(...sorted)
                        identitySet: IdentitySet(...sorted)

                        integers: Array(3, 1, 2, 2)
                        total: Arrays.reduce(
                            integers,
                            (accumulator, element) => {
                                accumulator + element
                            },
                            0
                        )

                        Array(
                            Set.size(normalSet),
                            IdentitySet.size(identitySet),
                            total == 8,
                            Set.contains(normalSet, 2.0),
                            IdentitySet.contains(identitySet, 2),
                            IdentitySet.contains(identitySet, 2.0)
                        )
                        """);

        assertInteger(3, result.indexedAt(BigInteger.ZERO));
        assertInteger(4, result.indexedAt(BigInteger.ONE));
        assertSame(ProtosBooleanValue.TRUE, result.indexedAt(BigInteger.TWO));
        assertSame(
                ProtosBooleanValue.TRUE,
                result.indexedAt(BigInteger.valueOf(3)));
        assertSame(
                ProtosBooleanValue.TRUE,
                result.indexedAt(BigInteger.valueOf(4)));
        assertSame(
                ProtosBooleanValue.TRUE,
                result.indexedAt(BigInteger.valueOf(5)));
    }

    private static void assertComparatorError(String prototypeName, String source)
            throws Exception {
        ProtosStandardLibraryModuleResolver resolver =
                new ProtosStandardLibraryModuleResolver(STANDARD_LIBRARY);
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE, resolver);
        Object prototype =
                prelude.bindings().readLocalSlot(prototypeName).orElseThrow();

        ProtosSignalException signal =
                assertThrows(
                        ProtosSignalException.class,
                        () ->
                                new ProtosSourceCompiler()
                                        .compile(source)
                                        .call(prelude.newModuleActivation()));

        ProtosObjectValue occurrence = signal.error();
        assertSame(prototype, occurrence.parent().orElseThrow());
        assertNotSame(prototype, occurrence);
    }

    private static Object run(String source) throws Exception {
        ProtosStandardLibraryModuleResolver resolver =
                new ProtosStandardLibraryModuleResolver(STANDARD_LIBRARY);
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE, resolver);
        return new ProtosSourceCompiler()
                .compile(source)
                .call(prelude.newModuleActivation());
    }

    private static ProtosArrayValue runArray(String source) throws Exception {
        return assertInstanceOf(ProtosArrayValue.class, run(source));
    }

    private static void assertSignals(String source) {
        assertThrows(ProtosSignalException.class, () -> run(source));
    }

    private static void assertInteger(long expected, Object value) {
        assertEquals(
                BigInteger.valueOf(expected),
                assertInstanceOf(ProtosIntegerValue.class, value).value());
    }
}
