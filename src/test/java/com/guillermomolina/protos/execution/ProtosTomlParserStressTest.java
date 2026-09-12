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
