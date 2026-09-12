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

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.guillermomolina.protos.runtime.ProtosStringValue;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class ProtosTomlParserModuleTest {
    private static final Path CORE = Path.of("protos", "lib", "core");
    private static final Path STANDARD_LIBRARY = Path.of("protos", "lib");

    @Test
    void parsesToml11StringsNumbersBooleansAndHeterogeneousArrays() throws Exception {
        String input =
                "basic = \"a\\x41\\e\"\n"
                        + "literal = 'raw'\n"
                        + "multi = \"\"\"hello\\nworld\"\"\"\n"
                        + "huge = 1234567890123456789012345678901234567890\n"
                        + "hex = 0xCA_FE\n"
                        + "oct = 0o755\n"
                        + "bin = 0b1010_0110\n"
                        + "finite = 1_2.5_0e+1\n"
                        + "negativeZero = -0.0\n"
                        + "positiveInfinity = +inf\n"
                        + "negativeInfinity = -inf\n"
                        + "notNumber = nan\n"
                        + "items = [1, \"x\", true, 2.5]\n";

        Object result =
                evaluate(
                        input,
                        """
                        root: TOML.parse(input)
                        (root.kind === "table") &&
                            (root.value["basic"].kind === "string") &&
                            (root.value["basic"].value.size() == 3) &&
                            (root.value["literal"].value == "raw") &&
                            (root.value["multi"].value == "hello\\nworld") &&
                            (root.value["huge"].kind === "integer") &&
                            (root.value["huge"].value ==
                                1234567890123456789012345678901234567890) &&
                            (root.value["hex"].value == 51966) &&
                            (root.value["oct"].value == 493) &&
                            (root.value["bin"].value == 166) &&
                            (root.value["finite"].kind === "float") &&
                            (root.value["finite"].value == 125.0) &&
                            ((1.0 / root.value["negativeZero"].value) == (-1.0 / 0.0)) &&
                            (root.value["positiveInfinity"].value == (1.0 / 0.0)) &&
                            (root.value["negativeInfinity"].value == (-1.0 / 0.0)) &&
                            ((root.value["notNumber"].value ==
                                root.value["notNumber"].value) === false) &&
                            (root.value["items"].kind === "array") &&
                            (root.value["items"].value.size() == 4) &&
                            (root.value["items"].value[0].kind === "integer") &&
                            (root.value["items"].value[1].kind === "string") &&
                            (root.value["items"].value[2].kind === "boolean") &&
                            (root.value["items"].value[3].kind === "float")
                        """);

        assertSame(ProtosBooleanValue.TRUE, result);
    }

    @Test
    void parsesAllTemporalKindsIncludingOptionalSecondsAndD104LeapSecond()
            throws Exception {
        String input =
                "date = 1979-05-27\n"
                        + "time = 07:32\n"
                        + "leap = 23:59:60.123400\n"
                        + "local = 1979-05-27T07:32\n"
                        + "offset = 1979-05-27 00:32:60.999-07:30\n"
                        + "zulu = 1979-05-27t07:32:00Z\n";

        Object result =
                evaluate(
                        input,
                        """
                        root: TOML.parse(input)
                        date: root.value["date"]
                        time: root.value["time"]
                        leap: root.value["leap"]
                        local: root.value["local"]
                        offset: root.value["offset"]
                        zulu: root.value["zulu"]

                        (date.kind === "localDate") &&
                            (date.value.year == 1979) &&
                            (date.value.month == 5) &&
                            (date.value.day == 27) &&
                            (time.kind === "localTime") &&
                            (time.value.hour == 7) &&
                            (time.value.minute == 32) &&
                            (time.value.second == 0) &&
                            (leap.kind === "localTime") &&
                            (leap.value.second == 60) &&
                            (leap.value.fraction.coefficient == 123400) &&
                            (leap.value.fraction.digits == 6) &&
                            (local.kind === "localDateTime") &&
                            (local.value.second == 0) &&
                            (offset.kind === "offsetDateTime") &&
                            (offset.value.second == 60) &&
                            (offset.value.offsetMinutes == -450) &&
                            (offset.value.fraction.coefficient == 999) &&
                            (offset.value.fraction.digits == 3) &&
                            (zulu.kind === "offsetDateTime") &&
                            (zulu.value.offsetMinutes == 0)
                        """);

        assertSame(ProtosBooleanValue.TRUE, result);
    }

    @Test
    void parsesTablesDottedKeysToml11InlineTablesAndArraysOfTables() throws Exception {
        String input =
                "title = \"demo\"\n"
                        + "owner.name = \"Tom\"\n"
                        + "[database]\n"
                        + "ports = [8000, 8001]\n"
                        + "inline = {\n"
                        + "  a = 1,\n"
                        + "  # comment between TOML 1.1 inline-table pairs\n"
                        + "  nested = { c = 2, },\n"
                        + "}\n"
                        + "[[products]]\n"
                        + "name = \"Hammer\"\n"
                        + "[[products]]\n"
                        + "name = \"Nail\"\n";

        Object result =
                evaluate(
                        input,
                        """
                        root: TOML.parse(input)
                        owner: root.value["owner"]
                        database: root.value["database"]
                        inline: database.value["inline"]
                        products: root.value["products"]

                        (root.value["title"].value == "demo") &&
                            (owner.kind === "table") &&
                            (owner.value["name"].value == "Tom") &&
                            (database.kind === "table") &&
                            (database.value["ports"].value.size() == 2) &&
                            (inline.kind === "table") &&
                            (inline.value["a"].value == 1) &&
                            (inline.value["nested"].kind === "table") &&
                            (inline.value["nested"].value["c"].value == 2) &&
                            (products.kind === "array") &&
                            (products.value.size() == 2) &&
                            (products.value[0].kind === "table") &&
                            (products.value[0].value["name"].value == "Hammer") &&
                            (products.value[1].value["name"].value == "Nail")
                        """);

        assertSame(ProtosBooleanValue.TRUE, result);
    }

    @Test
    void rejectsMalformedNumericTemporalStringAndOwnershipConflicts() throws Exception {
        for (String input :
                new String[] {
                    "x = 01\n",
                    "x = 1.\n",
                    "x = 1e\n",
                    "x = 1979-02-30\n",
                    "x = 23:59:61\n",
                    "x = \"\\q\"\n",
                    "x = 1\nx = 2\n",
                    "[a]\n[a]\n",
                    "x = { a = 1 }\nx.b = 2\n",
                    "x = {\n  a = 1\n  b = 2\n}\n",
                    "good = 1\nthis is not an assignment\n"
                }) {
            assertParseSignals(input);
        }

        assertExpressionSignals("TOML.parse(1)");
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

    private static void assertParseSignals(String input) throws Exception {
        ProtosStandardLibraryModuleResolver resolver =
                new ProtosStandardLibraryModuleResolver(STANDARD_LIBRARY);
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE, resolver);
        ProtosActivation activation = prelude.newModuleActivation();
        activation.context().createLocalSlot("input", new ProtosStringValue(input));

        assertThrows(
                ProtosSignalException.class,
                () ->
                        new ProtosSourceCompiler()
                                .compile(
                                        """
                                        TOML: import("std:toml/TOML")
                                        TOML.parse(input)
                                        """)
                                .call(activation),
                input);
    }

    private static void assertExpressionSignals(String expression) throws Exception {
        ProtosStandardLibraryModuleResolver resolver =
                new ProtosStandardLibraryModuleResolver(STANDARD_LIBRARY);
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE, resolver);

        assertThrows(
                ProtosSignalException.class,
                () ->
                        new ProtosSourceCompiler()
                                .compile(
                                        "TOML: import(\"std:toml/TOML\")\n"
                                                + expression)
                                .call(prelude.newModuleActivation()),
                expression);
    }
}
