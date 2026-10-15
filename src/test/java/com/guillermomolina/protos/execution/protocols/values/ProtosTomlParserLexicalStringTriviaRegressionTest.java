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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class ProtosTomlParserLexicalStringTriviaRegressionTest {
    private static final Path CORE = Path.of("protos", "lib", "core");
    private static final Path STANDARD_LIBRARY = Path.of("protos", "lib");
    private static ProtosPrelude prelude;

    @BeforeAll
    static void bootstrapCore() throws Exception {
        ProtosStandardLibraryModuleResolver resolver =
                new ProtosStandardLibraryModuleResolver(STANDARD_LIBRARY);
        prelude = new ProtosCoreBootstrap().bootstrap(CORE, resolver);
    }

    @Test
    void parsesLongBasicStringWithoutHostRecursiveLexicalScanning() throws Exception {
        String value = "b".repeat(768);
        Object result =
                evaluate(
                        "value = \"" + value + "\"\n",
                        """
                        root: TOML.parse(input)
                        (root.value["value"].kind === "string") &&
                            (root.value["value"].value.size() == 768)
                        """);
        assertSame(ProtosBooleanValue.TRUE, result);
    }

    @Test
    void parsesLongLiteralStringWithoutHostRecursiveLexicalScanning() throws Exception {
        String value = "l".repeat(768);
        Object result =
                evaluate(
                        "value = '" + value + "'\n",
                        """
                        root: TOML.parse(input)
                        (root.value["value"].kind === "string") &&
                            (root.value["value"].value.size() == 768)
                        """);
        assertSame(ProtosBooleanValue.TRUE, result);
    }

    @Test
    void parsesWhitespaceAndArrayTriviaCorrectly()
            throws Exception {
        String spaces = " ".repeat(128);
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

    private static Object evaluate(String input, String body) throws Exception {
        ProtosActivation activation = prelude.newModuleActivation();
        activation.context().createLocalSlot("input", new ProtosStringValue(input));
        return ProtosTestExecutionSupport.evaluate(
                "TOML: import(\"std:toml/TOML\")\n" + body,
                activation);
    }
}
