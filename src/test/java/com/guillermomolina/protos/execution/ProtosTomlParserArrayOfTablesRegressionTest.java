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

final class ProtosTomlParserArrayOfTablesRegressionTest {
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
    void parsesRepeatedArrayOfTablesCorrectly() throws Exception {
        int entries = 8;
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
                            (products.value.size() == 8) &&
                            (products.value[0].value["value"].value == 0) &&
                            (products.value[4].value["value"].value == 4) &&
                            (products.value[7].value["value"].value == 7)
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
