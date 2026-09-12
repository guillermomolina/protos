/*
 * THE LICENSED WORK IS PROVIDED UNDER THE TERMS OF THE ADAPTIVE PUBLIC LICENSE
 * ("LICENSE") AS FIRST COMPLETED BY: Guillermo Adrián Molina.
 * See LICENSE.TXT and https://github.com/guillermomolina/protos
 */
package com.guillermomolina.protos.execution;

import static org.junit.jupiter.api.Assertions.assertSame;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosStringValue;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class ProtosTomlParserStressTest {
    private static final Path CORE = Path.of("protos", "lib", "core");
    private static final Path STANDARD_LIBRARY = Path.of("protos", "lib");

    @Test
    void parsesDeepNestedArraysWithoutHostRecursiveNesting() throws Exception {
        int depth = 2048;
        String input = "value = " + "[".repeat(depth) + "0" + "]".repeat(depth) + "\n";
        Object result =
                evaluate(
                        input,
                        """
                        root: TOML.parse(input)
                        node: root.value["value"]
                        remaining: 2048
                        (() => remaining > 0).while(() => {
                            (node.kind === "array").ifFalse(() => { Error().signal() })
                            (node.value.size() == 1).ifFalse(() => { Error().signal() })
                            node = node.value[0]
                            remaining = remaining - 1
                        })
                        (node.kind === "integer") && (node.value == 0)
                        """);
        assertSame(ProtosBooleanValue.TRUE, result);
    }

    @Test
    void parsesDeepNestedInlineTablesWithoutHostRecursiveNesting() throws Exception {
        int depth = 1024;
        StringBuilder source = new StringBuilder("value = ");
        for (int i = 0; i < depth; i++) source.append("{ a = ");
        source.append("{ leaf = 1 }");
        for (int i = 0; i < depth; i++) source.append(" }");
        source.append('\n');

        Object result =
                evaluate(
                        source.toString(),
                        """
                        root: TOML.parse(input)
                        node: root.value["value"]
                        remaining: 1024
                        (() => remaining > 0).while(() => {
                            (node.kind === "table").ifFalse(() => { Error().signal() })
                            node = node.value["a"]
                            remaining = remaining - 1
                        })
                        (node.kind === "table") &&
                            (node.value["leaf"].kind === "integer") &&
                            (node.value["leaf"].value == 1)
                        """);
        assertSame(ProtosBooleanValue.TRUE, result);
    }

    @Test
    void parsesLargeFlatDocumentWithoutQuadraticStatementAccumulation() throws Exception {
        int entries = 4096;
        StringBuilder source = new StringBuilder(entries * 18);
        for (int i = 0; i < entries; i++) {
            source.append("k").append(i).append(" = ").append(i).append('\n');
        }

        Object result =
                evaluate(
                        source.toString(),
                        """
                        root: TOML.parse(input)
                        (root.kind === "table") &&
                            (root.value["k0"].value == 0) &&
                            (root.value["k2048"].value == 2048) &&
                            (root.value["k4095"].value == 4095)
                        """);
        assertSame(ProtosBooleanValue.TRUE, result);
    }

    @Test
    void parsesVeryDeepDottedAssignmentWithoutPathRecursion() throws Exception {
        int depth = 1536;
        String input = "a.".repeat(depth) + "leaf = 7\n";

        Object result =
                evaluate(
                        input,
                        """
                        root: TOML.parse(input)
                        node: root
                        remaining: 1536
                        (() => remaining > 0).while(() => {
                            (node.kind === "table").ifFalse(() => { Error().signal() })
                            node = node.value["a"]
                            remaining = remaining - 1
                        })
                        (node.kind === "table") &&
                            (node.value["leaf"].kind === "integer") &&
                            (node.value["leaf"].value == 7)
                        """);
        assertSame(ProtosBooleanValue.TRUE, result);
    }

    @Test
    void parsesVeryDeepHeaderPathWithoutHeaderWalkRecursion() throws Exception {
        int depth = 1536;
        String input = "[" + "a.".repeat(depth) + "section]\nleaf = 9\n";

        Object result =
                evaluate(
                        input,
                        """
                        root: TOML.parse(input)
                        node: root
                        remaining: 1536
                        (() => remaining > 0).while(() => {
                            (node.kind === "table").ifFalse(() => { Error().signal() })
                            node = node.value["a"]
                            remaining = remaining - 1
                        })
                        (node.kind === "table") &&
                            (node.value["section"].kind === "table") &&
                            (node.value["section"].value["leaf"].value == 9)
                        """);
        assertSame(ProtosBooleanValue.TRUE, result);
    }

    @Test
    void parsesLongKeysAndStringsWithoutHostRecursiveLexicalScanning() throws Exception {
        String quotedKey = "q".repeat(4096);
        String basic = "b".repeat(8192);
        String literal = "l".repeat(8192);
        String inlineKey = "i".repeat(4096);

        String input =
                "\"" + quotedKey + "\" = 1\n"
                        + "basic = \"" + basic + "\"\n"
                        + "literal = '" + literal + "'\n"
                        + "inline = { " + inlineKey + " = 7 }\n";

        Object result =
                evaluate(
                        input,
                        """
                        root: TOML.parse(input)
                        (root.kind === "table") &&
                            (root.value["basic"].value.size() == 8192) &&
                            (root.value["literal"].value.size() == 8192) &&
                            (root.value["inline"].kind === "table")
                        """);

        assertSame(ProtosBooleanValue.TRUE, result);
    }

    @Test
    void parsesLongWhitespaceAndArrayTriviaWithoutHostRecursiveLexicalScanning()
            throws Exception {
        String spaces = " ".repeat(8192);
        String input =
                "spaced =" + spaces + "1\n"
                        + "items = [1," + spaces + "2]\n";

        Object result =
                evaluate(
                        input,
                        """
                        root: TOML.parse(input)
                        (root.value["spaced"].value == 1) &&
                            (root.value["items"].kind === "array") &&
                            (root.value["items"].value.size() == 2) &&
                            (root.value["items"].value[1].value == 2)
                        """);

        assertSame(ProtosBooleanValue.TRUE, result);
    }

    @Test
    void parsesLongNumericAndTemporalTokensWithoutHostRecursiveScanning()
            throws Exception {
        String hugeInteger = "1" + "0".repeat(2047);
        String floatFraction = "0".repeat(2047) + "1";
        String hugeExponent = "9".repeat(2048);
        String temporalFraction = "1".repeat(2048);

        String input =
                "huge = " + hugeInteger + "\n"
                        + "finite = 1." + floatFraction + "\n"
                        + "overflow = 1e" + hugeExponent + "\n"
                        + "time = 12:34:56." + temporalFraction + "\n";

        Object result =
                evaluate(
                        input,
                        """
                        root: TOML.parse(input)
                        (root.value["huge"].kind === "integer") &&
                            (root.value["finite"].kind === "float") &&
                            (root.value["overflow"].kind === "float") &&
                            (root.value["overflow"].value == (1.0 / 0.0)) &&
                            (root.value["time"].kind === "localTime") &&
                            (root.value["time"].value.fraction.digits == 2048)
                        """);

        assertSame(ProtosBooleanValue.TRUE, result);
    }

    @Test
    void parsesManyArrayOfTablesWithoutQuadraticWholeArrayRebuilding() throws Exception {
        int entries = 2048;
        StringBuilder source = new StringBuilder(entries * 32);
        for (int i = 0; i < entries; i++) {
            source.append("[[products]]\n");
            source.append("value = ").append(i).append('\n');
        }

        Object result =
                evaluate(
                        source.toString(),
                        """
                        root: TOML.parse(input)
                        products: root.value["products"]
                        (products.kind === "array") &&
                            (products.value.size() == 2048) &&
                            (products.value[0].value["value"].value == 0) &&
                            (products.value[1024].value["value"].value == 1024) &&
                            (products.value[2047].value["value"].value == 2047)
                        """);

        assertSame(ProtosBooleanValue.TRUE, result);
    }

    private static Object evaluate(String input, String body) throws Exception {
        ProtosStandardLibraryModuleResolver resolver =
                new ProtosStandardLibraryModuleResolver(STANDARD_LIBRARY);
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE, resolver);
        ProtosActivation activation = prelude.newModuleActivation();
        activation.context().createLocalSlot("input", new ProtosStringValue(input));
        return new ProtosSourceCompiler()
                .compile("TOML: import(\"std:toml/TOML\")\n" + body)
                .call(activation);
    }
}
