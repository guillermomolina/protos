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

final class ProtosTomlParserPathRegressionTest {
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
    void parsesVeryDeepDottedAssignmentWithoutPathRecursion() throws Exception {
        int depth = 192;
        String input = "a.".repeat(depth) + "leaf = 7\n";

        Object result =
                evaluate(
                        input,
                        """
                        root: TOML.parse(input)
                        node: root
                        remaining: 192
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
    void parsesNestedHeaderPathCorrectly() throws Exception {
        int depth = 128;
        String input = "[" + "a.".repeat(depth) + "section]\nleaf = 9\n";

        Object result =
                evaluate(
                        input,
                        """
                        root: TOML.parse(input)
                        node: root
                        remaining: 128
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

    private static Object evaluate(String input, String body) throws Exception {
        ProtosActivation activation = prelude.newModuleActivation();
        activation.context().createLocalSlot("input", new ProtosStringValue(input));
        return ProtosTestExecutionSupport.evaluate(
                "TOML: import(\"std:toml/TOML\")\n" + body,
                activation);
    }
}
