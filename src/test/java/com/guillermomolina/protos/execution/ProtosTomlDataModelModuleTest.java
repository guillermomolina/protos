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
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosActorValueTransfer;
import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class ProtosTomlDataModelModuleTest {
    private static final Path CORE = Path.of("protos", "lib", "core");
    private static final Path STANDARD_LIBRARY = Path.of("protos", "lib");

    @Test
    void importedModuleExportsExactlyLib010ASemanticConstructors() throws Exception {
        ProtosStandardLibraryModuleResolver resolver =
                new ProtosStandardLibraryModuleResolver(STANDARD_LIBRARY);
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE, resolver);

        Object imported =
                new ProtosSourceCompiler()
                        .compile("import(\"std:toml/TOML\")")
                        .call(prelude.newModuleActivation());
        ProtosObjectValue module = assertInstanceOf(ProtosObjectValue.class, imported);

        assertEquals(
                Set.of(
                        "string",
                        "integer",
                        "float",
                        "boolean",
                        "offsetDateTime",
                        "localDateTime",
                        "localDate",
                        "localTime",
                        "array",
                        "table"),
                module.localSlotsSnapshot().keySet());
    }

    @Test
    void semanticConstructorsPreserveApprovedTomlKindsAndPayloads() throws Exception {
        Object result =
                evaluate(
                        """
                        TOML: import("std:toml/TOML")

                        text: TOML.string("hello")
                        huge: TOML.integer(123456789012345678901234567890)
                        negativeZero: TOML.float(-0.0)
                        flag: TOML.boolean(true)
                        date: TOML.localDate(2024, 2, 29)
                        time: TOML.localTime(23, 59, 58, 123400, 6)
                        local: TOML.localDateTime(2025, 12, 31, 7, 32, 0, 5, 1)
                        offset: TOML.offsetDateTime(
                            1979, 5, 27, 0, 32, 0, 999999, 6, -420
                        )
                        values: TOML.array(text, huge, negativeZero, flag, date, time)
                        root: TOML.table(
                            "name", text,
                            "values", values,
                            "local", local,
                            "offset", offset
                        )

                        (text.kind === "string") &&
                            (text.value == "hello") &&
                            (huge.kind === "integer") &&
                            (huge.value == 123456789012345678901234567890) &&
                            (negativeZero.kind === "float") &&
                            (negativeZero.value === -0.0) &&
                            (flag.kind === "boolean") &&
                            (flag.value === true) &&
                            (date.kind === "localDate") &&
                            (date.value.year == 2024) &&
                            (date.value.month == 2) &&
                            (date.value.day == 29) &&
                            (time.kind === "localTime") &&
                            (time.value.hour == 23) &&
                            (time.value.minute == 59) &&
                            (time.value.second == 58) &&
                            (time.value.fraction.coefficient == 123400) &&
                            (time.value.fraction.digits == 6) &&
                            (local.kind === "localDateTime") &&
                            (local.value.year == 2025) &&
                            (local.value.fraction.coefficient == 5) &&
                            (local.value.fraction.digits == 1) &&
                            (offset.kind === "offsetDateTime") &&
                            (offset.value.offsetMinutes == -420) &&
                            (offset.value.fraction.coefficient == 999999) &&
                            (offset.value.fraction.digits == 6) &&
                            (values.kind === "array") &&
                            (values.value.size() == 6) &&
                            (root.kind === "table") &&
                            (root.value.size() == 4) &&
                            (root.value["name"].value == "hello") &&
                            (root.value["values"] === values)
                        """);

        assertSame(ProtosBooleanValue.TRUE, result);
    }

    @Test
    void constructorsFailClosedOnWrongFamiliesAndInvalidTemporalData() throws Exception {
        assertSignals("TOML.string(1)");
        assertSignals("TOML.integer(1.0)");
        assertSignals("TOML.float(1)");
        assertSignals("TOML.boolean(\"true\")");
        assertSignals("TOML.localDate(0, 1, 1)");
        assertSignals("TOML.localDate(10000, 1, 1)");
        assertSignals("TOML.localDate(2023, 2, 29)");
        assertSignals("TOML.localDate(2024, 4, 31)");
        assertSignals("TOML.localTime(24, 0, 0, 0, 0)");
        assertSignals("TOML.localTime(0, 60, 0, 0, 0)");
        assertSignals("TOML.localTime(0, 0, 60, 0, 0)");
        assertSignals("TOML.localTime(0, 0, 0, 1, 0)");
        assertSignals("TOML.localTime(0, 0, 0, 10, 1)");
        assertSignals("TOML.offsetDateTime(2024, 1, 1, 0, 0, 0, 0, 0, 1440)");
        assertSignals("TOML.array({\n"
                + "    kind: \"unknown\"\n"
                + "    value: null\n"
                + "})");
        assertSignals("TOML.table(\"x\", TOML.integer(1), \"x\", TOML.integer(2))");
        assertSignals("TOML.table(\"dangling\")");
        assertSignals("TOML.table(1, TOML.integer(1))");
    }

    @Test
    void semanticDataTransfersAcrossActorsWhileModuleRemainsActorLocal() throws Exception {
        ProtosStandardLibraryModuleResolver resolver =
                new ProtosStandardLibraryModuleResolver(STANDARD_LIBRARY);
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE, resolver);
        ProtosSourceCompiler compiler = new ProtosSourceCompiler();

        ProtosActivation actorA = prelude.newModuleActivation();
        ProtosActivation actorB = prelude.newModuleActivation();

        ProtosObjectValue moduleA =
                assertInstanceOf(
                        ProtosObjectValue.class,
                        compiler.compile("import(\"std:toml/TOML\")").call(actorA));
        ProtosObjectValue moduleB =
                assertInstanceOf(
                        ProtosObjectValue.class,
                        compiler.compile("import(\"std:toml/TOML\")").call(actorB));

        assertNotSame(moduleA, moduleB);
        assertThrows(
                ProtosSignalException.class,
                () -> ProtosActorValueTransfer.snapshotValue(moduleA, actorA));

        ProtosObjectValue source =
                assertInstanceOf(
                        ProtosObjectValue.class,
                        compiler.compile(
                                        """
                                        TOML: import("std:toml/TOML")
                                        TOML.table(
                                            "when",
                                            TOML.offsetDateTime(
                                                2026, 9, 12, 7, 32, 0, 0, 0, 120
                                            ),
                                            "items",
                                            TOML.array(
                                                TOML.string("a"),
                                                TOML.integer(12345678901234567890)
                                            )
                                        )
                                        """)
                                .call(actorA));

        ProtosObjectValue copied =
                assertInstanceOf(
                        ProtosObjectValue.class,
                        ProtosActorValueTransfer.snapshotValue(source, actorA));

        assertNotSame(source, copied);
        assertEquals("table", stringSlot(copied, "kind"));
    }

    private static void assertSignals(String expression) throws Exception {
        ProtosStandardLibraryModuleResolver resolver =
                new ProtosStandardLibraryModuleResolver(STANDARD_LIBRARY);
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE, resolver);
        ProtosActivation activation = prelude.newModuleActivation();
        ProtosSourceCompiler compiler = new ProtosSourceCompiler();

        assertThrows(
                ProtosSignalException.class,
                () ->
                        compiler.compile(
                                        "TOML: import(\"std:toml/TOML\")\n"
                                                + expression)
                                .call(activation),
                expression);
    }

    private static Object evaluate(String source) throws Exception {
        ProtosStandardLibraryModuleResolver resolver =
                new ProtosStandardLibraryModuleResolver(STANDARD_LIBRARY);
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE, resolver);
        return new ProtosSourceCompiler().compile(source).call(prelude.newModuleActivation());
    }

    private static String stringSlot(ProtosObjectValue value, String slot) {
        return assertInstanceOf(
                        com.guillermomolina.protos.runtime.ProtosStringValue.class,
                        value.readLocalSlot(slot).orElseThrow())
                .value();
    }
}
