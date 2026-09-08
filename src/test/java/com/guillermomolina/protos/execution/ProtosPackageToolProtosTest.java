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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosFilesystemValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Temporary single Java execution bridge for Protos-owned TOOL001 fixture corpora.
 *
 * <p>Until TOOL002 owns this boundary, new TOOL001 observable-behavior fixtures belong under
 * {@code protos/tests/package-tool/**} and are executed through this class rather than through
 * one Java wrapper class per corpus.
 */
final class ProtosPackageToolProtosTest {
    private static final Path CORE = Path.of("protos", "lib", "core");
    private static final Path STANDARD_LIBRARY = Path.of("protos", "lib");
    private static final Path TOOL_ROOT = Path.of("protos", "tools", "package");
    private static final Path TEST_ROOT = Path.of("protos", "tests", "package-tool");
    private static final Path RUNNER_MANIFEST = TEST_ROOT.resolve("java-runner.tsv");

    @Test
    void manifestDrivenCorporaUseTheSingleTool001Runner() throws Exception {
        for (String line :
                Files.readAllLines(RUNNER_MANIFEST, StandardCharsets.UTF_8)) {
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }

            String[] fields = line.split("\\t", -1);
            if (fields.length != 2) {
                throw new AssertionError("invalid TOOL001 runner row: " + line);
            }

            Path suiteRoot = TEST_ROOT.resolve(fields[1]);
            switch (fields[0]) {
                case "plain" -> runPlainSuite(suiteRoot);
                case "project-tree" -> runProjectTreeSuite(suiteRoot);
                default -> throw new AssertionError("unknown TOOL001 runner profile: " + line);
            }
        }
    }

    private static void runPlainSuite(Path suiteRoot) throws Exception {
        List<String> lines =
                Files.readAllLines(suiteRoot.resolve("manifest.tsv"), StandardCharsets.UTF_8);

        for (String line : lines) {
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }

            String[] fields = line.split("\\t", -1);
            if (fields.length != 2) {
                throw new AssertionError(
                        "invalid TOOL001 plain fixture row in " + suiteRoot + ": " + line);
            }

            Path fixture = suiteRoot.resolve(fields[0]);
            ProtosExecutionOutcome outcome =
                    execute(
                            Files.readString(fixture, StandardCharsets.UTF_8),
                            newPackageActivation());
            assertExpected(outcome, fields[1], fixture.toString());
        }
    }

    private static void runProjectTreeSuite(Path suiteRoot) throws Exception {
        Path cases = suiteRoot.resolve("cases");
        Path fixtures = suiteRoot.resolve("fixtures");
        List<String> lines =
                Files.readAllLines(suiteRoot.resolve("manifest.tsv"), StandardCharsets.UTF_8);

        for (String line : lines) {
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }

            String[] fields = line.split("\\t", -1);
            if (fields.length != 3) {
                throw new AssertionError(
                        "invalid TOOL001 project-tree fixture row in " + suiteRoot + ": " + line);
            }

            Path caseRoot = cases.resolve(fields[0]);
            Path fixture = fixtures.resolve(fields[1]);

            try (ProtosNioReadOnlyTreeFilesystemBackend backend =
                    new ProtosNioReadOnlyTreeFilesystemBackend(caseRoot)) {
                assumeTrue(
                        backend.secureConfinementAvailable(),
                        "host provider has no SecureDirectoryStream");

                ProtosPrelude prelude = newPackagePrelude();
                ProtosActivation activation = prelude.newModuleActivation();
                ProtosObjectValue rawFilesystem =
                        ProtosStandardFilesystemProtocol.createCapability(
                                prelude.bytesPrototypeForRuntime(),
                                activation,
                                backend);
                ProtosFilesystemValue filesystem =
                        assertInstanceOf(ProtosFilesystemValue.class, rawFilesystem);
                activation.context()
                        .createLocalSlot("projectTreeFilesystem", filesystem);

                ProtosExecutionOutcome outcome =
                        execute(
                                Files.readString(fixture, StandardCharsets.UTF_8),
                                activation);
                assertExpected(outcome, fields[2], fields[0] + "/" + fields[1]);
            }
        }
    }

    private static ProtosActivation newPackageActivation() throws Exception {
        return newPackagePrelude().newModuleActivation();
    }

    private static ProtosPrelude newPackagePrelude() throws Exception {
        ProtosStandardLibraryModuleResolver standard =
                new ProtosStandardLibraryModuleResolver(STANDARD_LIBRARY);
        ProtosBundledToolModuleResolver resolver =
                new ProtosBundledToolModuleResolver("package", TOOL_ROOT, standard);
        return new ProtosCoreBootstrap().bootstrap(CORE, resolver);
    }

    private static ProtosExecutionOutcome execute(
            String source,
            ProtosActivation activation)
            throws Exception {
        return ProtosRootTaskExecution.execute(
                new ProtosSourceCompiler().compile(source),
                activation);
    }

    private static void assertExpected(
            ProtosExecutionOutcome outcome,
            String expected,
            String label) {
        switch (expected) {
            case "true" -> {
                assertEquals(ProtosExecutionOutcome.State.COMPLETED, outcome.state(), label);
                assertSame(ProtosBooleanValue.TRUE, outcome.value(), label);
            }
            case "error" ->
                    assertEquals(
                            ProtosExecutionOutcome.State.FAILED,
                            outcome.state(),
                            label);
            default ->
                    throw new AssertionError(
                            "unknown TOOL001 fixture expectation: " + expected);
        }
    }
}
