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
import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ProtosCollectionsArrayAlgorithmsModuleTest {
    private static final Path CORE = Path.of("protos", "lib", "core");
    private static final Path STANDARD_LIBRARY = Path.of("protos", "lib");

    @Test
    void mapUsesOneArgumentAscendingSnapshotAndReturnsFreshOpenArray() throws Exception {
        ProtosArrayValue result =
                runArray(
                        """
                        Arrays: import("std:collections/Array")
                        source: Array(1, 2)
                        calls: 0
                        mapped: Arrays.map(source, (element) => {
                            (calls == 0).ifTrue(() => {
                                source.atPut(1, 99)
                            })
                            calls = calls + 1
                            element * 10
                        })
                        Array(
                            mapped !== source,
                            mapped.size(),
                            mapped.at(0),
                            mapped.at(1),
                            source.at(1),
                            calls
                        )
                        """);

        assertSame(ProtosBooleanValue.TRUE, result.indexedAt(BigInteger.ZERO));
        assertInteger(2, result.indexedAt(BigInteger.ONE));
        assertInteger(10, result.indexedAt(BigInteger.TWO));
        assertInteger(20, result.indexedAt(BigInteger.valueOf(3)));
        assertInteger(99, result.indexedAt(BigInteger.valueOf(4)));
        assertInteger(2, result.indexedAt(BigInteger.valueOf(5)));

        ProtosArrayValue mapped =
                assertInstanceOf(
                        ProtosArrayValue.class,
                        run(
                                """
                                Arrays: import("std:collections/Array")
                                Arrays.map(Array(1), (element) => { element })
                                """));
        assertFalse(mapped.isClosed());
        assertFalse(mapped.isFrozen());
    }

    @Test
    void filterUsesStrictBooleanSnapshotOrderAndFreshVariableLengthResults()
            throws Exception {
        ProtosArrayValue result =
                runArray(
                        """
                        Arrays: import("std:collections/Array")
                        source: Array(1, 2, 3, 4)
                        seen: Map()
                        calls: 0
                        filtered: Arrays.filter(source, (element) => {
                            seen.atPut(calls, element)
                            (calls == 0).ifTrue(() => {
                                source.atPut(1, 99)
                            })
                            calls = calls + 1
                            (element == 2).or(() => { element == 4 })
                        })
                        Array(
                            filtered !== source,
                            filtered.size(),
                            filtered.at(0),
                            filtered.at(1),
                            source.at(1),
                            seen.at(0),
                            seen.at(1),
                            seen.at(2),
                            seen.at(3),
                            calls
                        )
                        """);

        assertSame(ProtosBooleanValue.TRUE, result.indexedAt(BigInteger.ZERO));
        assertInteger(2, result.indexedAt(BigInteger.ONE));
        assertInteger(2, result.indexedAt(BigInteger.TWO));
        assertInteger(4, result.indexedAt(BigInteger.valueOf(3)));
        assertInteger(99, result.indexedAt(BigInteger.valueOf(4)));
        assertInteger(1, result.indexedAt(BigInteger.valueOf(5)));
        assertInteger(2, result.indexedAt(BigInteger.valueOf(6)));
        assertInteger(3, result.indexedAt(BigInteger.valueOf(7)));
        assertInteger(4, result.indexedAt(BigInteger.valueOf(8)));
        assertInteger(4, result.indexedAt(BigInteger.valueOf(9)));

        ProtosArrayValue filtered =
                assertInstanceOf(
                        ProtosArrayValue.class,
                        run(
                                """
                                Arrays: import("std:collections/Array")
                                Arrays.filter(Array(1, 2), (element) => { true })
                                """));
        assertFalse(filtered.isClosed());
        assertFalse(filtered.isFrozen());
    }

    @Test
    void findIndexReturnsFirstSnapshotMatchOrNullAndStopsImmediately() throws Exception {
        ProtosArrayValue result =
                runArray(
                        """
                        Arrays: import("std:collections/Array")
                        source: Array(1, 2, 2, 3)
                        calls: 0
                        found: Arrays.findIndex(source, (element) => {
                            (calls == 0).ifTrue(() => {
                                source.atPut(1, 99)
                            })
                            calls = calls + 1
                            element == 2
                        })
                        missing: Arrays.findIndex(source, (element) => {
                            element == 777
                        })
                        Array(
                            found,
                            calls,
                            source.at(1),
                            missing === null
                        )
                        """);

        assertInteger(1, result.indexedAt(BigInteger.ZERO));
        assertInteger(2, result.indexedAt(BigInteger.ONE));
        assertInteger(99, result.indexedAt(BigInteger.TWO));
        assertSame(
                ProtosBooleanValue.TRUE,
                result.indexedAt(BigInteger.valueOf(3)));
    }

    @Test
    void emptyArraysDoNotInspectUserCallbacks() throws Exception {
        ProtosArrayValue result =
                runArray(
                        """
                        Arrays: import("std:collections/Array")
                        mapped: Arrays.map(Array(), 42)
                        filtered: Arrays.filter(Array(), 42)
                        missing: Arrays.findIndex(Array(), 42)
                        Array(
                            mapped.size(),
                            filtered.size(),
                            missing === null,
                            mapped !== filtered
                        )
                        """);

        assertInteger(0, result.indexedAt(BigInteger.ZERO));
        assertInteger(0, result.indexedAt(BigInteger.ONE));
        assertSame(ProtosBooleanValue.TRUE, result.indexedAt(BigInteger.TWO));
        assertSame(
                ProtosBooleanValue.TRUE,
                result.indexedAt(BigInteger.valueOf(3)));
    }

    @Test
    void nonEmptyCallbacksUseOrdinaryInvocationAndStrictArity() {
        for (String operation : new String[] {"map", "filter", "findIndex"}) {
            assertSignals(
                    """
                    Arrays: import("std:collections/Array")
                    Arrays.%s(Array(1), 42)
                    """
                            .formatted(operation));
            assertSignals(
                    """
                    Arrays: import("std:collections/Array")
                    Arrays.%s(Array(1), (element, index) => { true })
                    """
                            .formatted(operation));
        }
    }

    @Test
    void filterAndFindIndexSignalFreshInvalidPredicateResultOccurrences()
            throws Exception {
        for (String operation : new String[] {"filter", "findIndex"}) {
            ProtosStandardLibraryModuleResolver resolver =
                    new ProtosStandardLibraryModuleResolver(STANDARD_LIBRARY);
            ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE, resolver);
            Object invalidPredicatePrototype =
                    prelude.bindings()
                            .readLocalSlot("InvalidPredicateResult")
                            .orElseThrow();

            ProtosSignalException signal =
                    assertThrows(
                            ProtosSignalException.class,
                            () ->
                                    new ProtosSourceCompiler()
                                            .compile(
                                                    """
                                                    Arrays: import("std:collections/Array")
                                                    Arrays.%s(Array(1), (element) => { 123 })
                                                    """
                                                            .formatted(operation))
                                            .call(prelude.newModuleActivation()));

            ProtosObjectValue occurrence = signal.error();
            assertSame(
                    invalidPredicatePrototype,
                    occurrence.parent().orElseThrow(),
                    operation);
            assertNotSame(invalidPredicatePrototype, occurrence, operation);
        }
    }

    @Test
    void wrongSourceReceiverFailsBeforeAnyUserCallback() throws Exception {
        for (String operation : new String[] {"map", "filter", "findIndex"}) {
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
