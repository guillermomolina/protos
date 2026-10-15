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

final class ProtosTomlParserFloatTemporalRegressionTest {
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
    void parsesLongFloatFractionWithoutHostRecursiveScanning() throws Exception {
        String fraction = "0".repeat(447) + "1";

        Object result =
                evaluate(
                        "value = 1." + fraction + "\n",
                        """
                        root: TOML.parse(input)
                        root.value["value"].kind === "float"
                        """);

        assertSame(ProtosBooleanValue.TRUE, result);
    }

    @Test
    void parsesLongFloatExponentWithoutHostRecursiveScanning() throws Exception {
        String exponent = "9".repeat(448);

        Object result =
                evaluate(
                        "value = 1e" + exponent + "\n",
                        """
                        root: TOML.parse(input)
                        (root.value["value"].kind === "float") &&
                            (root.value["value"].value == (1.0 / 0.0))
                        """);

        assertSame(ProtosBooleanValue.TRUE, result);
    }

    @Test
    void parsesLongTemporalFractionWithoutHostRecursiveScanning() throws Exception {
        String fraction = "1".repeat(448);

        Object result =
                evaluate(
                        "value = 12:34:56." + fraction + "\n",
                        """
                        root: TOML.parse(input)
                        (root.value["value"].kind === "localTime") &&
                            (root.value["value"].value.fraction.digits == 448)
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
