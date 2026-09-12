/*
 * THE LICENSED WORK IS PROVIDED UNDER THE TERMS OF THE ADAPTIVE PUBLIC LICENSE
 * ("LICENSE") AS FIRST COMPLETED BY: Guillermo Adrián Molina.
 * See LICENSE.TXT and https://github.com/guillermomolina/protos
 */
package com.guillermomolina.protos.execution;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosStringValue;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class ProtosTomlClosureConformanceTest {
    private static final Path CORE = Path.of("protos", "lib", "core");
    private static final Path STANDARD_LIBRARY = Path.of("protos", "lib");
    private static final Path PUBLIC_TOML =
            Path.of("protos", "lib", "toml", "TOML.protos");

    @Test
    void officialStyleToml11DocumentRoundTripsSemantically() throws Exception {
        String input =
                "title = \"TOML Example\"\n"
                        + "escape = \"\\x41\\e\"\n"
                        + "[owner]\n"
                        + "name = \"Tom Preston-Werner\"\n"
                        + "dob = 1979-05-27T07:32:00Z\n"
                        + "[database]\n"
                        + "enabled = true\n"
                        + "ports = [8000, 8001, 8002]\n"
                        + "targets = {\n"
                        + "  cpu = 79.5,\n"
                        + "  case = 72.0,\n"
                        + "}\n"
                        + "[[products]]\n"
                        + "name = \"Hammer\"\n"
                        + "sku = 738594937\n"
                        + "[[products]]\n"
                        + "name = \"Nail\"\n"
                        + "sku = 284758393\n"
                        + "color = \"gray\"\n";

        Object result =
                evaluate(
                        input,
                        """
                        first: TOML.parse(input)
                        encoded: TOML.encode(first)
                        second: TOML.parse(encoded)

                        products: second.value["products"]
                        database: second.value["database"]

                        (second.value["title"].value == "TOML Example") &&
                            (second.value["escape"].value.size() == 2) &&
                            (second.value["owner"].value["name"].value ==
                                "Tom Preston-Werner") &&
                            (second.value["owner"].value["dob"].kind ===
                                "offsetDateTime") &&
                            (database.value["enabled"].value === true) &&
                            (database.value["ports"].value.size() == 3) &&
                            (database.value["targets"].value["cpu"].value == 79.5) &&
                            (database.value["targets"].value["case"].value == 72.0) &&
                            (products.kind === "array") &&
                            (products.value.size() == 2) &&
                            (products.value[0].value["name"].value == "Hammer") &&
                            (products.value[1].value["name"].value == "Nail") &&
                            (products.value[1].value["color"].value == "gray")
                        """);

        assertSame(ProtosBooleanValue.TRUE, result);
    }

    @Test
    void freshModuleBootstrapsDoNotShareParserOrSemanticState() throws Exception {
        Object first =
                evaluate(
                        "value = 1\n",
                        """
                        root: TOML.parse(input)
                        root.value["extra"] = TOML.integer(99)
                        root.value.containsKey("extra")
                        """);
        assertSame(ProtosBooleanValue.TRUE, first);

        Object second =
                evaluate(
                        "value = 1\n",
                        """
                        root: TOML.parse(input)
                        (root.value.containsKey("extra") === false) &&
                            (root.value["value"].value == 1)
                        """);
        assertSame(ProtosBooleanValue.TRUE, second);
    }

    @Test
    void publicTomlSourceKeepsTheD087AndHostRuntimeBoundaries() throws Exception {
        String source = Files.readString(PUBLIC_TOML, StandardCharsets.UTF_8);

        assertFalse(source.contains("tool-shared:Toml10"), source);
        assertFalse(source.contains("protos/tools/shared/Toml10"), source);
        assertFalse(source.contains("Double.toString"), source);
        assertFalse(source.contains("doubleToRawLongBits"), source);
        assertFalse(source.contains("java.time"), source);
        assertFalse(source.contains("java.nio.file"), source);
        assertFalse(source.contains("loadFile"), source);
        assertFalse(source.contains("saveFile"), source);
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
