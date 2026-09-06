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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.runtime.ProtosActivation;
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

class ProtosJsonParserModuleTest {
    private static final Path CORE = Path.of("protos", "lib", "core");
    private static final Path STANDARD_LIBRARY = Path.of("protos", "lib");

    @Test
    void parsesTopLevelScalarsAndExactDecimalNumbers() throws Exception {
        assertSame(ProtosNullValue.INSTANCE, value(parse("null"), "null"));
        assertSame(ProtosBooleanValue.TRUE, value(parse("true"), "boolean"));
        assertSame(ProtosBooleanValue.FALSE, value(parse("false"), "boolean"));
        assertEquals("hello", stringValue(parse("\"hello\"")));

        assertDecimal(parse("0"), 0, 0);
        assertDecimal(parse("-0"), 0, 0);
        assertDecimal(parse("123"), 123, 0);
        assertDecimal(parse("-12.3400e+2"), -123400, -2);
        assertDecimal(parse("1e400"), 1, 400);
    }

    @Test
    void parsesRootArrayAndObjectWithoutReprocessingOpeningDelimiter()
            throws Exception {
        ProtosObjectValue arrayNode = parse("[1]");
        ProtosArrayValue array =
                assertInstanceOf(ProtosArrayValue.class, value(arrayNode, "array"));
        assertEquals(BigInteger.ONE, array.indexedSize());
        assertDecimal(
                assertInstanceOf(
                        ProtosObjectValue.class,
                        array.indexedAt(BigInteger.ZERO)),
                1,
                0);

        ProtosObjectValue objectNode = parse("{\"a\":1}");
        ProtosMapValue object =
                assertInstanceOf(ProtosMapValue.class, value(objectNode, "object"));
        assertEquals(1, object.keyedSize());
        assertEquals("a", key(object.keyedSnapshot().get(0)));
        assertDecimal(
                assertInstanceOf(
                        ProtosObjectValue.class,
                        object.keyedSnapshot().get(0).value()),
                1,
                0);
    }

    @Test
    void parsesNestedArraysAndObjectsWithDeterministicRetainedOrder() throws Exception {
        ProtosObjectValue root =
                parse("{\"a\":[1,true,null,\"x\"],\"b\":{\"c\":2}}");
        ProtosMapValue rootMap =
                assertInstanceOf(ProtosMapValue.class, value(root, "object"));
        assertTrue(rootMap.isOpen());
        assertEquals(2, rootMap.keyedSize());

        List<ProtosMapValue.Entry> rootEntries = rootMap.keyedSnapshot();
        assertEquals("a", key(rootEntries.get(0)));
        assertEquals("b", key(rootEntries.get(1)));

        ProtosObjectValue arrayNode =
                assertInstanceOf(ProtosObjectValue.class, rootEntries.get(0).value());
        ProtosArrayValue array =
                assertInstanceOf(ProtosArrayValue.class, value(arrayNode, "array"));
        assertTrue(array.isFrozen());
        assertEquals(BigInteger.valueOf(4), array.indexedSize());
        assertDecimal(
                assertInstanceOf(ProtosObjectValue.class, array.indexedAt(BigInteger.ZERO)),
                1,
                0);
        assertSame(
                ProtosBooleanValue.TRUE,
                value(
                        assertInstanceOf(
                                ProtosObjectValue.class,
                                array.indexedAt(BigInteger.ONE)),
                        "boolean"));
        assertSame(
                ProtosNullValue.INSTANCE,
                value(
                        assertInstanceOf(
                                ProtosObjectValue.class,
                                array.indexedAt(BigInteger.valueOf(2))),
                        "null"));
        assertEquals(
                "x",
                stringValue(
                        assertInstanceOf(
                                ProtosObjectValue.class,
                                array.indexedAt(BigInteger.valueOf(3)))));

        ProtosObjectValue nestedObject =
                assertInstanceOf(ProtosObjectValue.class, rootEntries.get(1).value());
        ProtosMapValue nestedMap =
                assertInstanceOf(ProtosMapValue.class, value(nestedObject, "object"));
        assertEquals(1, nestedMap.keyedSize());
        assertEquals("c", key(nestedMap.keyedSnapshot().get(0)));
        assertDecimal(
                assertInstanceOf(
                        ProtosObjectValue.class,
                        nestedMap.keyedSnapshot().get(0).value()),
                2,
                0);
    }

    @Test
    void decodesJsonEscapesSurrogatePairsAndRawUnicodeWithoutNormalization()
            throws Exception {
        assertEquals(
                "\"\\/\b\f\n\r\t",
                stringValue(parse("\"\\\"\\\\\\/\\b\\f\\n\\r\\t\"")));
        assertEquals("A", stringValue(parse("\"\\u0041\"")));
        assertEquals("\uD834\uDD1E", stringValue(parse("\"\\uD834\\uDD1E\"")));
        assertEquals("😀", stringValue(parse("\"😀\"")));

        String noncharacter = new String(Character.toChars(0xFDD0));
        assertEquals(
                noncharacter,
                stringValue(parse("\"" + noncharacter + "\"")));
    }

    @Test
    void rejectsDuplicateNamesAfterEscapeDecodingAndStrictSyntaxErrors() throws Exception {
        String[] invalid = {
            "",
            "   ",
            "+1",
            "01",
            "-",
            "1.",
            "1e",
            "1e+",
            "NaN",
            "Infinity",
            "[1,]",
            "{\"a\":1,}",
            "{\"a\" 1}",
            "{a:1}",
            "[1 2]",
            "true false",
            "/*x*/null",
            "\"\\x41\"",
            "\"\\uDEAD\"",
            "\"\\uD834\"",
            "\"\\uD834x\"",
            "\"\\uD834\\u0041\"",
            "{\"a\":1,\"\\u0061\":2}",
            "{",
            "["
        };

        for (String input : invalid) {
            assertThrows(ProtosSignalException.class, () -> parse(input), input);
        }
    }

    @Test
    void rejectsNonStringInputWithoutImplicitCoercion() throws Exception {
        assertThrows(
                ProtosSignalException.class,
                () -> runWithInput(new ProtosIntegerValue(BigInteger.ONE)));
    }

    @Test
    void deeplyNestedContainersUseExplicitJsonStackRatherThanRecursiveDescent()
            throws Exception {
        int depth = 2048;
        String input = "[".repeat(depth) + "0" + "]".repeat(depth);

        ProtosObjectValue node = parse(input);
        for (int index = 0; index < depth; index++) {
            ProtosArrayValue array =
                    assertInstanceOf(ProtosArrayValue.class, value(node, "array"));
            assertEquals(BigInteger.ONE, array.indexedSize());
            node =
                    assertInstanceOf(
                            ProtosObjectValue.class,
                            array.indexedAt(BigInteger.ZERO));
        }
        assertDecimal(node, 0, 0);
    }

    @Test
    void largeArrayUsesBalancedChunkMaterializationAndPreservesEveryElement()
            throws Exception {
        int size = 2048;
        StringBuilder input = new StringBuilder("[");
        for (int index = 0; index < size; index++) {
            if (index != 0) {
                input.append(',');
            }
            input.append(index);
        }
        input.append(']');

        ProtosArrayValue array =
                assertInstanceOf(ProtosArrayValue.class, value(parse(input.toString()), "array"));
        assertTrue(array.isFrozen());
        assertEquals(BigInteger.valueOf(size), array.indexedSize());

        for (int index : new int[] {0, 1, 2, 31, 32, 511, 1024, 2047}) {
            assertDecimal(
                    assertInstanceOf(
                            ProtosObjectValue.class,
                            array.indexedAt(BigInteger.valueOf(index))),
                    index,
                    0);
        }
    }

    private static ProtosObjectValue parse(String input) throws Exception {
        return assertInstanceOf(ProtosObjectValue.class, runWithInput(new ProtosStringValue(input)));
    }

    private static Object runWithInput(Object input) throws Exception {
        ProtosStandardLibraryModuleResolver resolver =
                new ProtosStandardLibraryModuleResolver(STANDARD_LIBRARY);
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE, resolver);
        ProtosActivation activation = prelude.newModuleActivation();
        activation.context().createLocalSlot("input", input);
        return new ProtosSourceCompiler()
                .compile(
                        """
                        JSON: import("std:json/JSON")
                        JSON.parse(input)
                        """)
                .call(activation);
    }

    private static Object value(ProtosObjectValue node, String expectedKind) {
        assertTrue(node.isOpen());
        assertEquals(expectedKind, kind(node));
        return node.readLocalSlot("value").orElseThrow();
    }

    private static String kind(ProtosObjectValue node) {
        return assertInstanceOf(
                        ProtosStringValue.class,
                        node.readLocalSlot("kind").orElseThrow())
                .value();
    }

    private static String stringValue(ProtosObjectValue node) {
        return assertInstanceOf(ProtosStringValue.class, value(node, "string")).value();
    }

    private static String key(ProtosMapValue.Entry entry) {
        return assertInstanceOf(ProtosStringValue.class, entry.key()).value();
    }

    private static void assertDecimal(
            ProtosObjectValue numberNode, long coefficient, long exponent) {
        assertDecimal(
                numberNode,
                BigInteger.valueOf(coefficient),
                BigInteger.valueOf(exponent));
    }

    private static void assertDecimal(
            ProtosObjectValue numberNode, BigInteger coefficient, BigInteger exponent) {
        ProtosObjectValue decimal =
                assertInstanceOf(ProtosObjectValue.class, value(numberNode, "number"));
        assertTrue(decimal.isOpen());
        assertEquals(
                coefficient,
                assertInstanceOf(
                                ProtosIntegerValue.class,
                                decimal.readLocalSlot("coefficient").orElseThrow())
                        .value());
        assertEquals(
                exponent,
                assertInstanceOf(
                                ProtosIntegerValue.class,
                                decimal.readLocalSlot("exponent").orElseThrow())
                        .value());
    }
}
