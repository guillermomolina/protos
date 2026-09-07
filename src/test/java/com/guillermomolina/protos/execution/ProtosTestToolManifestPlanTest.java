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
import com.guillermomolina.protos.runtime.ProtosArrayValue;
import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosStringValue;
import com.guillermomolina.protos.runtime.ProtosFilesystemValue;
import com.guillermomolina.protos.runtime.ProtosIntegerValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.oracle.truffle.api.CallTarget;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ProtosTestToolManifestPlanTest {
    private static final Path CORE = Path.of("protos", "lib", "core");
    private static final Path STANDARD_LIBRARY = Path.of("protos", "lib");
    private static final Path TOOL_ROOT = Path.of("protos", "tools", "test");
    private static final Path MANIFEST_MODULE = TOOL_ROOT.resolve("Manifest.protos");
    private static final Path CORPUS_ROOT =
            Path.of("protos", "tests", "conformance");
    private static final Path FIXTURE =
            Path.of(
                    "protos",
                    "tests",
                    "tooling",
                    "tool002-d2-manifest-plan.protos");
    private static final Path PACKAGE_TOML_FIXTURE =
            Path.of(
                    "protos",
                    "tests",
                    "tooling",
                    "tool002-e1a-package-toml-manifest-plan.protos");

    @Test
    void bundledManifestModuleCompilesBeforeAnyFilesystemPolicyRuns()
            throws Exception {
        new ProtosSourceCompiler()
                .compile(
                        Files.readString(
                                MANIFEST_MODULE,
                                StandardCharsets.UTF_8));
    }

    @Test
    void oneManifestRowProducesFrozenStableCaseSpecBeforeCorpusTraversal()
            throws Exception {
        Fixture fixture = fixture();
        String source =
                "Manifest: import(\"self:Manifest\")\n"
                        + "Manifest.parseLine("
                        + "\"integer/add-small.protos\\tinteger\\t2\")";

        ProtosArrayValue caseSpec =
                assertInstanceOf(
                        ProtosArrayValue.class,
                        completed(
                                new ProtosSourceCompiler().compile(source),
                                fixture.activation()));

        assertEquals(4, caseSpec.indexedSize().intValueExact());
        org.junit.jupiter.api.Assertions.assertTrue(caseSpec.isFrozen());
        assertEquals(
                "integer/add-small.protos",
                assertInstanceOf(
                                ProtosStringValue.class,
                                caseSpec.indexedAt(java.math.BigInteger.ZERO))
                        .value());
    }

    @Test
    void namedCaseSpecAccessorsPreserveConceptualPlanningFields()
            throws Exception {
        Fixture fixture = fixture();
        String source =
                "Manifest: import(\"self:Manifest\")\n"
                        + "case: Manifest.parseLine("
                        + "\"integer/add-small.protos\\tinteger\\t2\")\n"
                        + "(Manifest.caseId(case) == \"integer/add-small.protos\") &&\n"
                        + "(Manifest.casePath(case) == Manifest.caseId(case)) &&\n"
                        + "(Manifest.caseExpectation(case) == \"integer\") &&\n"
                        + "(Manifest.caseExpected(case) == \"2\")";

        Object result =
                completed(
                        new ProtosSourceCompiler().compile(source),
                        fixture.activation());

        assertSame(ProtosBooleanValue.TRUE, result);
    }

    @Test
    void batchedManifestTraversalDoesNotGrowOneProtosFramePerRow(
            @TempDir Path corpusRoot) throws Exception {
        int rowCount = 2048;
        String row = "integer/add-small.protos\tinteger\t2";
        StringBuilder manifest = new StringBuilder();
        for (int i = 0; i < rowCount; i++) {
            manifest.append(row);
            switch (i % 3) {
                case 0 -> manifest.append('\n');
                case 1 -> manifest.append("\r\n");
                default -> manifest.append('\r');
            }
        }
        Files.writeString(
                corpusRoot.resolve("manifest.tsv"),
                manifest,
                StandardCharsets.UTF_8);

        Fixture fixture = fixture();
        try (ProtosNioReadOnlyTreeFilesystemBackend backend =
                new ProtosNioReadOnlyTreeFilesystemBackend(corpusRoot)) {
            assumeTrue(
                    backend.secureConfinementAvailable(),
                    "host provider has no SecureDirectoryStream");
            installFilesystem(fixture.prelude(), fixture.activation(), backend);

            Object result =
                    completed(
                            new ProtosSourceCompiler()
                                    .compile(
                                            "Manifest: import(\"self:Manifest\")\n"
                                                    + "Manifest.planCases("
                                                    + "Manifest.load(filesystem)).size()"),
                            fixture.activation());

            assertEquals(
                    rowCount,
                    assertInstanceOf(ProtosIntegerValue.class, result)
                            .value()
                            .intValueExact());
        }
    }

    @Test
    void manifestAndNestedCorpusSourceAreConsumedByProtosPolicy()
            throws Exception {
        Fixture fixture = fixture();

        try (ProtosNioReadOnlyTreeFilesystemBackend backend =
                new ProtosNioReadOnlyTreeFilesystemBackend(CORPUS_ROOT)) {
            assumeTrue(
                    backend.secureConfinementAvailable(),
                    "host provider has no SecureDirectoryStream");

            installFilesystem(fixture.prelude(), fixture.activation(), backend);

            Object result =
                    completed(
                            new ProtosSourceCompiler()
                                    .compile(
                                            Files.readString(
                                                    FIXTURE,
                                                    StandardCharsets.UTF_8)),
                            fixture.activation());

            assertSame(ProtosBooleanValue.TRUE, result);
        }
    }

    @Test
    void packageTomlManifestPlanningIsOwnedByBundledProtos(
            @TempDir Path corpusRoot) throws Exception {
        Files.writeString(
                corpusRoot.resolve("manifest.tsv"),
                "# path\texpectation\n"
                        + "key-bare-dotted.protos\ttrue\n"
                        + "invalid-key-error.protos\terror\n",
                StandardCharsets.UTF_8);

        Fixture fixture = fixture();
        try (ProtosNioReadOnlyTreeFilesystemBackend backend =
                new ProtosNioReadOnlyTreeFilesystemBackend(corpusRoot)) {
            assumeTrue(
                    backend.secureConfinementAvailable(),
                    "host provider has no SecureDirectoryStream");
            installFilesystem(fixture.prelude(), fixture.activation(), backend);

            Object result =
                    completed(
                            new ProtosSourceCompiler()
                                    .compile(
                                            Files.readString(
                                                    PACKAGE_TOML_FIXTURE,
                                                    StandardCharsets.UTF_8)),
                            fixture.activation());

            assertSame(ProtosBooleanValue.TRUE, result);
        }
    }

    private static Fixture fixture() throws Exception {
        ProtosBundledToolModuleResolver resolver =
                new ProtosBundledToolModuleResolver(
                        "test",
                        TOOL_ROOT,
                        new ProtosStandardLibraryModuleResolver(
                                STANDARD_LIBRARY));
        ProtosPrelude prelude =
                new ProtosCoreBootstrap().bootstrap(CORE, resolver);
        return new Fixture(prelude, prelude.newModuleActivation());
    }

    private static void installFilesystem(
            ProtosPrelude prelude,
            ProtosActivation activation,
            ProtosStandardFilesystemProtocol.Backend backend) {
        ProtosObjectValue rawFilesystem =
                ProtosStandardFilesystemProtocol.createCapability(
                        prelude.bytesPrototypeForRuntime(),
                        activation,
                        backend);
        ProtosFilesystemValue filesystem =
                assertInstanceOf(
                        ProtosFilesystemValue.class,
                        rawFilesystem);
        activation.context().createLocalSlot("filesystem", filesystem);
    }

    private static Object completed(
            CallTarget target,
            ProtosActivation activation) {
        ProtosExecutionOutcome outcome =
                ProtosRootTaskExecution.execute(target, activation);
        assertEquals(
                ProtosExecutionOutcome.State.COMPLETED,
                outcome.state(),
                () ->
                        "Protos execution did not complete; state="
                                + outcome.state()
                                + ", error="
                                + outcome.error());
        return outcome.value();
    }

    private record Fixture(
            ProtosPrelude prelude,
            ProtosActivation activation) {}
}
