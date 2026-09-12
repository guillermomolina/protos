/*
 * THE LICENSED WORK IS PROVIDED UNDER THE TERMS OF THE ADAPTIVE PUBLIC LICENSE
 * ("LICENSE") AS FIRST COMPLETED BY: Guillermo Adrián Molina.
 * See LICENSE.TXT and https://github.com/guillermomolina/protos
 */
package com.guillermomolina.protos.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class ProtosTomlEncoderModuleTest {
    private static final Path CORE = Path.of("protos", "lib", "core");
    private static final Path STANDARD_LIBRARY = Path.of("protos", "lib");

    @Test
    void encodesAllTenSemanticKindsWithDeterministicInlinePresentation() throws Exception {
        Object result =
                evaluate(
                        """
                        root: TOML.table(
                            "text", TOML.string("hello"),
                            "integer", TOML.integer(123456789012345678901234567890),
                            "float", TOML.float(0.1),
                            "boolean", TOML.boolean(true),
                            "date", TOML.localDate(2024, 2, 29),
                            "time", TOML.localTime(23, 59, 60, 123, 5),
                            "local", TOML.localDateTime(2025, 1, 2, 3, 4, 5, 6, 3),
                            "offset", TOML.offsetDateTime(1979, 5, 27, 0, 32, 0, 0, 0, -420),
                            "array", TOML.array(
                                TOML.integer(1),
                                TOML.table("nested", TOML.string("yes"))
                            ),
                            "table", TOML.table(
                                "inner", TOML.boolean(false)
                            )
                        )
                        text: TOML.encode(root)
                        parsed: TOML.parse(text)

                        (parsed.value["text"].value == "hello") &&
                            (parsed.value["integer"].value ==
                                123456789012345678901234567890) &&
                            (parsed.value["float"].value == 0.1) &&
                            (parsed.value["boolean"].value === true) &&
                            (parsed.value["date"].value.year == 2024) &&
                            (parsed.value["time"].value.second == 60) &&
                            (parsed.value["time"].value.fraction.coefficient == 123) &&
                            (parsed.value["time"].value.fraction.digits == 5) &&
                            (parsed.value["local"].value.hour == 3) &&
                            (parsed.value["offset"].value.offsetMinutes == -420) &&
                            (parsed.value["array"].value.size() == 2) &&
                            (parsed.value["array"].value[1].kind === "table") &&
                            (parsed.value["array"].value[1].value["nested"].value == "yes") &&
                            (parsed.value["table"].value["inner"].value === false)
                        """);
        assertSame(ProtosBooleanValue.TRUE, result);
    }

    @Test
    void d109UsesCanonicalShortestRoundTripFloatTokens() throws Exception {
        Object result =
                evaluate(
                        """
                        encoded: TOML.encode(
                            TOML.table(
                                "one", TOML.float(1.0),
                                "ten", TOML.float(10.0),
                                "tenth", TOML.float(0.1),
                                "negativeZero", TOML.float(-0.0),
                                "positiveInfinity", TOML.float(1.0 / 0.0),
                                "negativeInfinity", TOML.float(0.0 - (1.0 / 0.0)),
                                "nan", TOML.float(0.0 / 0.0)
                            )
                        )

                        encoded ==
                            "\\"one\\" = 1.0\\n" +
                            "\\"ten\\" = 1e1\\n" +
                            "\\"tenth\\" = 0.1\\n" +
                            "\\"negativeZero\\" = -0.0\\n" +
                            "\\"positiveInfinity\\" = inf\\n" +
                            "\\"negativeInfinity\\" = -inf\\n" +
                            "\\"nan\\" = nan\\n"
                        """);
        assertSame(ProtosBooleanValue.TRUE, result);
    }

    @Test
    void d109RoundTripsHardBinary64ValuesWithoutUsingHostDisplaySemantics() throws Exception {
        Object result =
                evaluate(
                        """
                        tiny: 5e-324
                        huge: 1.7976931348623157e308
                        awkward: 1.2345678901234567

                        root: TOML.table(
                            "tiny", TOML.float(tiny),
                            "huge", TOML.float(huge),
                            "awkward", TOML.float(awkward),
                            "negativeZero", TOML.float(-0.0)
                        )

                        parsed: TOML.parse(TOML.encode(root))
                        parsedNegativeZero: parsed.value["negativeZero"].value

                        (parsed.value["tiny"].value == tiny) &&
                            (parsed.value["huge"].value == huge) &&
                            (parsed.value["awkward"].value == awkward) &&
                            ((1.0 / parsedNegativeZero) == (0.0 - (1.0 / 0.0)))
                        """);
        assertSame(ProtosBooleanValue.TRUE, result);
    }

    @Test
    void escapesKeysAndStringValuesAsBasicStrings() throws Exception {
        Object result =
                evaluate(
                        """
                        encoded: TOML.encode(
                            TOML.table(
                                "space key",
                                TOML.string("quote\\" slash\\\\ newline\\n")
                            )
                        )

                        encoded ==
                            "\\"space key\\" = \\"quote\\\\\\" slash\\\\\\\\ newline\\\\u000A\\"\\n"
                        """);
        assertSame(ProtosBooleanValue.TRUE, result);
    }

    @Test
    void encoderFailsClosedOnInvalidRootNodesAndCycles() throws Exception {
        assertSignals("TOML.encode(TOML.integer(1))");
        assertSignals(
                """
                forged: {
                    kind: "unknown"
                    value: 1
                }
                TOML.encode(TOML.table("x", forged))
                """);
        assertSignals(
                """
                cyclic: TOML.table()
                cyclic.value["self"] = cyclic
                TOML.encode(cyclic)
                """);
    }

    private static Object evaluate(String body) throws Exception {
        ProtosStandardLibraryModuleResolver resolver =
                new ProtosStandardLibraryModuleResolver(STANDARD_LIBRARY);
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE, resolver);
        return new ProtosSourceCompiler()
                .compile("TOML: import(\"std:toml/TOML\")\n" + body)
                .call(prelude.newModuleActivation());
    }

    private static void assertSignals(String body) throws Exception {
        assertThrows(ProtosSignalException.class, () -> evaluate(body));
    }
}
