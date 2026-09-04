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

package com.guillermomolina.protos.conformance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.guillermomolina.protos.execution.ProtosCoreBootstrap;
import com.guillermomolina.protos.execution.ProtosSourceFileLoader;
import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosFixedIntegerValue;
import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.guillermomolina.protos.runtime.ProtosFloatValue;
import com.guillermomolina.protos.runtime.ProtosIntegerValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

final class ProtosLanguageConformanceTest {
    private static final Path CORE = Path.of("protos", "lib", "core");
    private static final Path ROOT = Path.of("protos", "tests", "conformance");
    private static final Path MANIFEST = ROOT.resolve("manifest.tsv");

    @TestFactory
    Stream<DynamicTest> languageConformanceCases() throws IOException {
        List<Case> cases =
                Files.readAllLines(MANIFEST, StandardCharsets.UTF_8).stream()
                        .filter(line -> !line.isBlank())
                        .filter(line -> !line.stripLeading().startsWith("#"))
                        .map(ProtosLanguageConformanceTest::parseCase)
                        .toList();

        return cases.stream()
                .map(testCase ->
                        DynamicTest.dynamicTest(
                                testCase.path().toString(),
                                () -> executeCase(testCase)));
    }

    private static void executeCase(Case testCase) throws IOException {
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
        ProtosSourceFileLoader loader = new ProtosSourceFileLoader();
        Path source = ROOT.resolve(testCase.path());

        switch (testCase.expectation()) {
            case "boolean" -> {
                Object result =
                        loader.load(source).call(prelude.newModuleActivation());
                ProtosBooleanValue expected =
                        switch (testCase.expectedValue()) {
                            case "true" -> ProtosBooleanValue.TRUE;
                            case "false" -> ProtosBooleanValue.FALSE;
                            default -> throw new IllegalArgumentException(
                                    "boolean expectation must be true or false");
                        };
                assertEquals(expected, result);
            }
            case "null" -> {
                Object result =
                        loader.load(source).call(prelude.newModuleActivation());
                assertEquals(ProtosNullValue.INSTANCE, result);
            }
            case "integer" -> {
                Object result =
                        loader.load(source).call(prelude.newModuleActivation());
                ProtosIntegerValue integer =
                        assertInstanceOf(ProtosIntegerValue.class, result);
                assertEquals(new BigInteger(testCase.expectedValue()), integer.value());
            }
            case "float-bits" -> {
                Object result =
                        loader.load(source).call(prelude.newModuleActivation());
                ProtosFloatValue floating =
                        assertInstanceOf(ProtosFloatValue.class, result);
                long expectedBits =
                        Long.parseUnsignedLong(testCase.expectedValue(), 16);
                assertEquals(
                        expectedBits,
                        Double.doubleToRawLongBits(floating.value()));
            }
            case "fixed-integer" -> {
                Object result =
                        loader.load(source).call(prelude.newModuleActivation());
                ProtosFixedIntegerValue fixed =
                        assertInstanceOf(ProtosFixedIntegerValue.class, result);
                String[] expected = testCase.expectedValue().split(":", 2);
                if (expected.length != 2) {
                    throw new IllegalArgumentException(
                            "fixed-integer expectation must be FAMILY:value");
                }
                assertEquals(
                        ProtosFixedIntegerValue.Family.fromPrototypeName(expected[0]),
                        fixed.family());
                assertEquals(new BigInteger(expected[1]), fixed.value());
            }
            case "float-nan" -> {
                Object result =
                        loader.load(source).call(prelude.newModuleActivation());
                ProtosFloatValue floating =
                        assertInstanceOf(ProtosFloatValue.class, result);
                org.junit.jupiter.api.Assertions.assertTrue(
                        Double.isNaN(floating.value()));
            }
            case "error" ->
                    assertThrows(
                            ProtosSignalException.class,
                            () ->
                                    loader.load(source)
                                            .call(prelude.newModuleActivation()));
            default ->
                    throw new IllegalArgumentException(
                            "unsupported conformance expectation: "
                                    + testCase.expectation());
        }
    }

    private static Case parseCase(String line) {
        List<String> fields = Arrays.asList(line.split("\\t", -1));
        if (fields.size() != 3) {
            throw new IllegalArgumentException(
                    "manifest row must have exactly 3 tab-separated fields: " + line);
        }
        return new Case(
                Path.of(fields.get(0)),
                fields.get(1),
                fields.get(2));
    }

    private record Case(
            Path path,
            String expectation,
            String expectedValue) {}
}
