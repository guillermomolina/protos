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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ProtosTestToolResourceRequirementsDiscoveryTest {
    private static final Path CORE = Path.of("protos", "lib", "core");
    private static final Path STANDARD_LIBRARY = Path.of("protos", "lib");
    private static final Path TOOL_ROOT = Path.of("protos", "tools", "test");
    private static final Path SHARED_ROOT = Path.of("protos", "tools", "shared");
    private static final String SIDECAR = "resource-requirements.toml";

    @Test
    void exactAbsencePreservesTheResourceFreePlan(@TempDir Path corpusRoot)
            throws Exception {
        assertCompletedTrue(
                corpusRoot,
                planningSource()
                        + "(joined === plan) && (requirements.size() == 0)");
    }

    @Test
    void exactRegularSidecarIsReadParsedAndJoined(@TempDir Path corpusRoot)
            throws Exception {
        Files.writeString(
                corpusRoot.resolve(SIDECAR),
                "resource-requirements-version = 1\n"
                        + "[[requirement]]\n"
                        + "case = \"a.protos\"\n"
                        + "key = \"gpu\"\n"
                        + "mode = \"shared\"\n"
                        + "units = 2\n",
                StandardCharsets.UTF_8);

        assertCompletedTrue(
                corpusRoot,
                planningSource()
                        + "(requirements.size() == 1) &&\n"
                        + "(Manifest.requirementKey(requirements[0]) == \"gpu\") &&\n"
                        + "(Manifest.requirementMode(requirements[0]) == \"shared\") &&\n"
                        + "(Manifest.requirementUnits(requirements[0]) == 2)");
    }

    @Test
    void wrongCaseSiblingDoesNotAliasTheExactD094Name(@TempDir Path corpusRoot)
            throws Exception {
        Files.writeString(
                corpusRoot.resolve("Resource-Requirements.toml"),
                "resource-requirements-version = 2\n",
                StandardCharsets.UTF_8);

        assertCompletedTrue(
                corpusRoot,
                planningSource()
                        + "(joined === plan) && (requirements.size() == 0)");
    }

    @Test
    void exactNonRegularSiblingFailsClosed(@TempDir Path corpusRoot)
            throws Exception {
        Files.createDirectory(corpusRoot.resolve(SIDECAR));
        assertFailed(corpusRoot, planningSource() + "joined");
    }

    @Test
    void malformedTomlAndSchemaBothFailClosed(@TempDir Path corpusRoot)
            throws Exception {
        String[] invalidDocuments = {
            "resource-requirements-version = [\n",
            "resource-requirements-version = 2\n"
        };

        for (String document : invalidDocuments) {
            Files.writeString(
                    corpusRoot.resolve(SIDECAR),
                    document,
                    StandardCharsets.UTF_8);
            assertFailed(corpusRoot, planningSource() + "joined");
        }
    }

    @Test
    void orphanRequirementFailsDuringFullPlanJoin(@TempDir Path corpusRoot)
            throws Exception {
        Files.writeString(
                corpusRoot.resolve(SIDECAR),
                "resource-requirements-version = 1\n"
                        + "[[requirement]]\n"
                        + "case = \"missing.protos\"\n"
                        + "key = \"gpu\"\n"
                        + "mode = \"exclusive\"\n",
                StandardCharsets.UTF_8);

        assertFailed(corpusRoot, planningSource() + "joined");
    }

    private static String planningSource() {
        return "Manifest: import(\"self:Manifest\")\n"
                + "Requirements: import(\"self:ResourceRequirements\")\n"
                + "spec: Manifest.caseSpec(Array(\"a.protos\", \"boolean\", \"true\"))\n"
                + "plan: Array(Array(spec))\n"
                + "joined: Requirements.loadFromCorpus(filesystem, plan)\n"
                + "cases: Manifest.planCases(joined)\n"
                + "requirements: Manifest.caseRequirements(cases[0])\n";
    }

    private static void assertCompletedTrue(Path corpusRoot, String source)
            throws Exception {
        ProtosExecutionOutcome outcome = execute(corpusRoot, source);
        assertEquals(
                ProtosExecutionOutcome.State.COMPLETED,
                outcome.state(),
                () -> "state=" + outcome.state() + ", error=" + outcome.error());
        assertSame(ProtosBooleanValue.TRUE, outcome.value());
    }

    private static void assertFailed(Path corpusRoot, String source)
            throws Exception {
        ProtosExecutionOutcome outcome = execute(corpusRoot, source);
        assertEquals(
                ProtosExecutionOutcome.State.FAILED,
                outcome.state(),
                () -> "expected fail-closed discovery; value=" + outcome.value());
    }

    private static ProtosExecutionOutcome execute(Path corpusRoot, String source)
            throws Exception {
        ProtosBundledToolModuleResolver resolver =
                new ProtosBundledToolModuleResolver(
                        "test",
                        TOOL_ROOT,
                        SHARED_ROOT,
                        new ProtosStandardLibraryModuleResolver(STANDARD_LIBRARY));
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE, resolver);
        ProtosActivation activation = prelude.newModuleActivation();

        try (ProtosNioReadOnlyTreeFilesystemBackend backend =
                new ProtosNioReadOnlyTreeFilesystemBackend(corpusRoot)) {
            assumeTrue(
                    backend.secureConfinementAvailable(),
                    "host provider has no SecureDirectoryStream");

            ProtosObjectValue rawFilesystem =
                    ProtosStandardFilesystemProtocol.createCapability(
                            prelude.bytesPrototypeForRuntime(),
                            activation,
                            backend);
            ProtosFilesystemValue filesystem =
                    assertInstanceOf(ProtosFilesystemValue.class, rawFilesystem);
            activation.context().createLocalSlot("filesystem", filesystem);

            return ProtosRootTaskExecution.execute(
                    new ProtosSourceCompiler().compile(source),
                    activation);
        }
    }
}
