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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosActorValueTransfer;
import com.guillermomolina.protos.runtime.ProtosArrayValue;
import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosIntegerValue;
import com.guillermomolina.protos.runtime.ProtosMapValue;
import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.guillermomolina.protos.runtime.ProtosStringValue;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProtosJsonDataModelModuleTest {
    private static final Path CORE = Path.of("protos", "lib", "core");
    private static final Path STANDARD_LIBRARY = Path.of("protos", "lib");

    @Test
    void nullBooleanAndStringConstructorsCreateFreshOpenOrdinaryNodes()
            throws Exception {
        ProtosArrayValue result =
                runArray(
                        """
                        JSON: import("std:json/JSON")
                        Array(
                            JSON.nullValue(),
                            JSON.nullValue(),
                            JSON.boolean(true),
                            JSON.boolean(false),
                            JSON.string("Ada")
                        )
                        """);

        ProtosObjectValue nullA = node(result, 0, "null");
        ProtosObjectValue nullB = node(result, 1, "null");
        assertNotSame(nullA, nullB);
        assertSame(
                ProtosNullValue.INSTANCE,
                nullA.readLocalSlot("value").orElseThrow());

        assertSame(
                ProtosBooleanValue.TRUE,
                node(result, 2, "boolean").readLocalSlot("value").orElseThrow());
        assertSame(
                ProtosBooleanValue.FALSE,
                node(result, 3, "boolean").readLocalSlot("value").orElseThrow());

        assertEquals(
                "Ada",
                assertInstanceOf(
                                ProtosStringValue.class,
                                node(result, 4, "string")
                                        .readLocalSlot("value")
                                        .orElseThrow())
                        .value());
    }

    @Test
    void numberConstructorKeepsExactUnboundedIntegerCoefficientAndExponent()
            throws Exception {
        ProtosArrayValue result =
                runArray(
                        """
                        JSON: import("std:json/JSON")
                        Array(
                            JSON.number(123, -2),
                            JSON.number(10, -1),
                            JSON.number(1, 0),
                            JSON.number(0, 1000000)
                        )
                        """);

        assertDecimal(node(result, 0, "number"), 123, -2);
        assertDecimal(node(result, 1, "number"), 10, -1);
        assertDecimal(node(result, 2, "number"), 1, 0);
        assertDecimal(node(result, 3, "number"), 0, 1000000);
    }

    @Test
    void arrayUsesFrozenRestCaptureWhileObjectOwnsFreshOpenMapAndPreservesOrder()
            throws Exception {
        ProtosArrayValue result =
                runArray(
                        """
                        JSON: import("std:json/JSON")
                        one: JSON.number(1, 0)
                        array: JSON.array(one, JSON.string("x"), JSON.nullValue())
                        object: JSON.object(
                            "first", one,
                            "second", array,
                            "third", JSON.boolean(true)
                        )
                        Array(array, object)
                        """);

        ProtosObjectValue arrayNode = node(result, 0, "array");
        ProtosArrayValue array =
                assertInstanceOf(
                        ProtosArrayValue.class,
                        arrayNode.readLocalSlot("value").orElseThrow());
        assertTrue(array.isFrozen());
        assertEquals(BigInteger.valueOf(3), array.indexedSize());
        assertEquals(
                "number",
                kind(assertInstanceOf(ProtosObjectValue.class, array.indexedAt(BigInteger.ZERO))));
        assertEquals(
                "string",
                kind(assertInstanceOf(ProtosObjectValue.class, array.indexedAt(BigInteger.ONE))));
        assertEquals(
                "null",
                kind(
                        assertInstanceOf(
                                ProtosObjectValue.class,
                                array.indexedAt(BigInteger.valueOf(2)))));
        assertThrows(
                IllegalStateException.class,
                () -> array.indexedPut(BigInteger.ZERO, ProtosNullValue.INSTANCE));

        ProtosObjectValue objectNode = node(result, 1, "object");
        ProtosMapValue object =
                assertInstanceOf(
                        ProtosMapValue.class,
                        objectNode.readLocalSlot("value").orElseThrow());
        assertTrue(object.isOpen());
        assertEquals(3, object.keyedSize());

        List<ProtosMapValue.Entry> entries = object.keyedSnapshot();
        assertEquals("first", assertInstanceOf(ProtosStringValue.class, entries.get(0).key()).value());
        assertEquals("second", assertInstanceOf(ProtosStringValue.class, entries.get(1).key()).value());
        assertEquals("third", assertInstanceOf(ProtosStringValue.class, entries.get(2).key()).value());

        assertThrows(
                ProtosSignalException.class,
                () ->
                        run(
                                """
                                JSON: import("std:json/JSON")
                                JSON.object(
                                    "x", JSON.nullValue(),
                                    "x", JSON.boolean(true)
                                )
                                """));
    }

    @Test
    void constructorsRejectInvalidImmediateDomains() throws Exception {
        for (String expression :
                new String[] {
                    "JSON.boolean(1)",
                    "JSON.string(1)",
                    "JSON.number(1.0, 0)",
                    "JSON.number(1, 0.0)",
                    "JSON.number(UInt8(1), 0)",
                    "JSON.number(1, Int8(0))",
                    "JSON.number(Integer {}, 0)",
                    "JSON.array(1)",
                    "JSON.object(1, JSON.nullValue())",
                    "JSON.object(\"orphan\")",
                    "JSON.object(\"x\", 1)"
                }) {
            assertThrows(
                    ProtosSignalException.class,
                    () ->
                            run(
                                    "JSON: import(\"std:json/JSON\")\n"
                                            + expression),
                    expression);
        }
    }

    @Test
    void exactCaseImportIsCanonicalAndLowercaseAliasesAreAbsent() throws Exception {
        ProtosStandardLibraryModuleResolver resolver =
                new ProtosStandardLibraryModuleResolver(STANDARD_LIBRARY);
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE, resolver);
        ProtosActivation activation = prelude.newModuleActivation();
        ProtosSourceCompiler compiler = new ProtosSourceCompiler();

        Object first = compiler.compile("import(\"std:json/JSON\")").call(activation);
        Object repeated = compiler.compile("import(\"std:json/JSON\")").call(activation);
        assertSame(first, repeated);

        assertThrows(
                ProtosSignalException.class,
                () -> compiler.compile("import(\"std:json/json\")").call(activation));
        assertThrows(
                ProtosSignalException.class,
                () -> compiler.compile("import(\"std:JSON/JSON\")").call(activation));
    }

    @Test
    void dataTransfersAcrossActorsWhileTheModuleRemainsActorLocal() throws Exception {
        ProtosStandardLibraryModuleResolver resolver =
                new ProtosStandardLibraryModuleResolver(STANDARD_LIBRARY);
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE, resolver);
        ProtosActivation activation = prelude.newModuleActivation();
        ProtosSourceCompiler compiler = new ProtosSourceCompiler();

        Object module = compiler.compile("import(\"std:json/JSON\")").call(activation);
        assertThrows(
                ProtosSignalException.class,
                () -> ProtosActorValueTransfer.snapshotValue(module, activation));

        ProtosObjectValue source =
                assertInstanceOf(
                        ProtosObjectValue.class,
                        compiler.compile(
                                        """
                                        JSON: import("std:json/JSON")
                                        JSON.object(
                                            "name", JSON.string("Ada"),
                                            "numbers", JSON.array(
                                                JSON.number(10, -1),
                                                JSON.number(1, 0)
                                            )
                                        )
                                        """)
                                .call(activation));

        ProtosObjectValue copied =
                assertInstanceOf(
                        ProtosObjectValue.class,
                        ProtosActorValueTransfer.snapshotValue(source, activation));

        assertNotSame(source, copied);
        assertTrue(source.isOpen());
        assertTrue(copied.isOpen());
        assertEquals("object", kind(copied));

        ProtosMapValue copiedMap =
                assertInstanceOf(
                        ProtosMapValue.class,
                        copied.readLocalSlot("value").orElseThrow());
        assertTrue(copiedMap.isOpen());
        assertEquals(2, copiedMap.keyedSize());

        ProtosObjectValue copiedNumbers =
                assertInstanceOf(
                        ProtosObjectValue.class,
                        copiedMap.keyedSnapshot().get(1).value());
        assertEquals("array", kind(copiedNumbers));
        ProtosArrayValue copiedArray =
                assertInstanceOf(
                        ProtosArrayValue.class,
                        copiedNumbers.readLocalSlot("value").orElseThrow());
        assertTrue(copiedArray.isFrozen());
        assertEquals(BigInteger.TWO, copiedArray.indexedSize());
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

    private static ProtosObjectValue node(
            ProtosArrayValue array, int index, String expectedKind) {
        ProtosObjectValue node =
                assertInstanceOf(
                        ProtosObjectValue.class,
                        array.indexedAt(BigInteger.valueOf(index)));
        assertTrue(node.isOpen());
        assertEquals(expectedKind, kind(node));
        assertEquals(2, node.localSlotsSnapshot().size());
        return node;
    }

    private static String kind(ProtosObjectValue node) {
        return assertInstanceOf(
                        ProtosStringValue.class,
                        node.readLocalSlot("kind").orElseThrow())
                .value();
    }

    private static void assertDecimal(
            ProtosObjectValue numberNode, long coefficient, long exponent) {
        ProtosObjectValue decimal =
                assertInstanceOf(
                        ProtosObjectValue.class,
                        numberNode.readLocalSlot("value").orElseThrow());
        assertTrue(decimal.isOpen());
        assertEquals(2, decimal.localSlotsSnapshot().size());
        assertEquals(
                BigInteger.valueOf(coefficient),
                assertInstanceOf(
                                ProtosIntegerValue.class,
                                decimal.readLocalSlot("coefficient").orElseThrow())
                        .value());
        assertEquals(
                BigInteger.valueOf(exponent),
                assertInstanceOf(
                                ProtosIntegerValue.class,
                                decimal.readLocalSlot("exponent").orElseThrow())
                        .value());
    }
}
