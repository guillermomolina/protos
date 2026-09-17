/*
 * THE LICENSED WORK IS PROVIDED UNDER THE TERMS OF THE ADAPTIVE PUBLIC LICENSE
 * ("LICENSE") AS FIRST COMPLETED BY: Guillermo Adrián Molina.
 * See LICENSE.TXT and https://github.com/guillermomolina/protos
 */
package com.guillermomolina.protos.execution;

import static org.junit.jupiter.api.Assertions.assertSame;

import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class ProtosTomlEncoderDeepContainerRegressionTest {
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
    void encodesHistoricallyFailingArrayDepthWithoutHostRecursiveTraversal()
            throws Exception {
        Object result =
                evaluate(
                        """
                        node: TOML.integer(7)
                        remaining: 139
                        (() => remaining > 0).while(() => {
                            node = TOML.array(node)
                            remaining = remaining - 1
                        })

                        encoded: TOML.encode(TOML.table("deep", node))
                        encoded.size() == 289
                        """);

        assertSame(ProtosBooleanValue.TRUE, result);
    }

    @Test
    void encodesHistoricallyFailingInlineTableDepthWithoutHostRecursiveTraversal()
            throws Exception {
        Object result =
                evaluate(
                        """
                        node: TOML.integer(9)
                        remaining: 142
                        (() => remaining > 0).while(() => {
                            node = TOML.table("a", node)
                            remaining = remaining - 1
                        })

                        encoded: TOML.encode(TOML.table("deep", node))
                        encoded.size() == 1147
                        """);

        assertSame(ProtosBooleanValue.TRUE, result);
    }

    private static Object evaluate(String body) throws Exception {
        return ProtosTestExecutionSupport.evaluate(
                "TOML: import(\"std:toml/TOML\")\n" + body,
                prelude.newModuleActivation());
    }
}
