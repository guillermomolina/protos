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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosStringValue;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

final class ProtosTestToolFutureStoredInspectionFixtureSuiteTest {
    private static final Path CORE = Path.of("protos", "lib", "core");
    private static final Path STANDARD_LIBRARY = Path.of("protos", "lib");
    private static final Path TOOL_ROOT = Path.of("protos", "tools", "test");
    private static final Path TOOLING_ROOT = Path.of("protos", "tests", "tooling");
    private static final Path MANIFEST =
            TOOLING_ROOT.resolve("tool002-f3c1b-inspection-fixtures.tsv");
    private static final Path RETAINED_SOURCE =
            Path.of(
                    "protos",
                    "tests",
                    "conformance",
                    "future",
                    "failed-value-resignals-recorded-error.protos");

    @TestFactory
    Stream<DynamicTest> futureStoredInspectionFixtures() throws IOException {
        List<FixtureCase> cases =
                Files.readAllLines(MANIFEST, StandardCharsets.UTF_8).stream()
                        .filter(line -> !line.isBlank())
                        .filter(line -> !line.stripLeading().startsWith("#"))
                        .map(ProtosTestToolFutureStoredInspectionFixtureSuiteTest::parseCase)
                        .toList();

        assertTrue(!cases.isEmpty(), "inspection fixture manifest must not be empty");
        assertEquals(
                cases.size(),
                cases.stream().map(FixtureCase::caseId).collect(java.util.stream.Collectors.toSet()).size(),
                "fixture case ids must be unique");
        assertEquals(
                cases.size(),
                cases.stream().map(FixtureCase::fixture).collect(java.util.stream.Collectors.toSet()).size(),
                "fixture paths must be unique");

        return cases.stream()
                .map(testCase ->
                        DynamicTest.dynamicTest(
                                testCase.caseId() + " :: " + testCase.fixture(),
                                () -> executeCase(testCase)));
    }

    private static void executeCase(FixtureCase testCase) throws IOException {
        Path fixture = TOOLING_ROOT.resolve(testCase.fixture());
        assertTrue(
                Files.isRegularFile(fixture),
                () -> "missing Protos fixture: " + fixture);

        ProtosBundledToolModuleResolver resolver =
                new ProtosBundledToolModuleResolver(
                        "test",
                        TOOL_ROOT,
                        TOOL_ROOT.resolveSibling("shared"),
                        new ProtosStandardLibraryModuleResolver(STANDARD_LIBRARY));
        ProtosPrelude prelude =
                new ProtosCoreBootstrap().bootstrap(CORE, resolver);
        ProtosActivation activation = prelude.newModuleActivation();
        ProtosExactExecutionFacility.installInspection(activation);

        activation
                .context()
                .createLocalSlot(
                        "retainedSource",
                        new ProtosStringValue(
                                Files.readString(
                                        RETAINED_SOURCE,
                                        StandardCharsets.UTF_8)));

        ProtosExecutionOutcome outcome =
                ProtosRootTaskExecution.execute(
                        new ProtosSourceCompiler()
                                .compile(
                                        Files.readString(
                                                fixture,
                                                StandardCharsets.UTF_8)),
                        activation);

        assertEquals(
                ProtosExecutionOutcome.State.COMPLETED,
                outcome.state(),
                () ->
                        testCase.caseId()
                                + " Protos fixture failed; state="
                                + outcome.state()
                                + ", error="
                                + outcome.error());
        assertSame(
                ProtosBooleanValue.TRUE,
                outcome.value(),
                () -> testCase.caseId() + " Protos fixture must return canonical true");
    }

    private static FixtureCase parseCase(String line) {
        List<String> fields = Arrays.asList(line.split("\\t", -1));
        if (fields.size() != 2) {
            throw new IllegalArgumentException(
                    "inspection fixture manifest row must have exactly 2 tab-separated fields: "
                            + line);
        }

        String caseId = fields.get(0);
        Path fixture = Path.of(fields.get(1));
        if (!caseId.startsWith("TOOL002-F3C1B")
                && !caseId.startsWith("TOOL002-F3C2")) {
            throw new IllegalArgumentException("unexpected stored-inspection fixture case id: " + caseId);
        }
        if (fixture.isAbsolute()
                || fixture.getNameCount() != 1
                || !fixture.toString().endsWith(".protos")) {
            throw new IllegalArgumentException(
                    "inspection fixture must be a tooling-root .protos basename: " + fixture);
        }

        return new FixtureCase(caseId, fixture);
    }

    private record FixtureCase(String caseId, Path fixture) {}
}
