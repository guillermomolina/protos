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
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosArrayValue;
import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosStringValue;
import com.guillermomolina.protos.runtime.ProtosFilesystemValue;
import com.guillermomolina.protos.runtime.ProtosIntegerValue;
import com.guillermomolina.protos.runtime.ProtosNullValue;
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
    private static final Path PACKAGE_TOML_CORPUS_ROOT =
            Path.of("protos", "tests", "package-tool", "toml-syntax");
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
    private static final Path PACKAGE_TOML_FILESYSTEM_FIXTURE =
            Path.of(
                    "protos",
                    "tests",
                    "tooling",
                    "tool002-e1b-package-toml-filesystem.protos");

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

        assertEquals(5, caseSpec.indexedSize().intValueExact());
        org.junit.jupiter.api.Assertions.assertTrue(caseSpec.isFrozen());
        ProtosArrayValue requirements =
                assertInstanceOf(
                        ProtosArrayValue.class,
                        caseSpec.indexedAt(java.math.BigInteger.valueOf(4)));
        assertEquals(0, requirements.indexedSize().intValueExact());
        org.junit.jupiter.api.Assertions.assertTrue(requirements.isFrozen());
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
    void caseSpecRequirementsAccessorReturnsFreshFrozenEmptyArrays()
            throws Exception {
        Fixture fixture = fixture();
        String source =
                "Manifest: import(\"self:Manifest\")\n"
                        + "first: Manifest.caseSpec(Array(\"i1/first.protos\", \"integer\", \"1\"))\n"
                        + "second: Manifest.caseSpec(Array(\"i1/second.protos\", \"integer\", \"2\"))\n"
                        + "Array(Manifest.caseRequirements(first), "
                        + "Manifest.caseRequirements(second))";

        ProtosArrayValue requirements =
                assertInstanceOf(
                        ProtosArrayValue.class,
                        completed(
                                new ProtosSourceCompiler().compile(source),
                                fixture.activation()));
        assertEquals(2, requirements.indexedSize().intValueExact());

        ProtosArrayValue first =
                assertInstanceOf(
                        ProtosArrayValue.class,
                        requirements.indexedAt(java.math.BigInteger.ZERO));
        ProtosArrayValue second =
                assertInstanceOf(
                        ProtosArrayValue.class,
                        requirements.indexedAt(java.math.BigInteger.ONE));

        assertEquals(0, first.indexedSize().intValueExact());
        assertEquals(0, second.indexedSize().intValueExact());
        org.junit.jupiter.api.Assertions.assertTrue(first.isFrozen());
        org.junit.jupiter.api.Assertions.assertTrue(second.isFrozen());
        assertNotSame(first, second);
    }

    @Test
    void inertRequirementRecordIsFrozenAndAccessibleThroughNamedAccessors()
            throws Exception {
        Fixture fixture = fixture();
        String source =
                "Manifest: import(\"self:Manifest\")\n"
                        + "shared: Manifest.requirement(\"gpu\", \"shared\", 2)\n"
                        + "exclusive: Manifest.requirement(\"db/integration\", \"exclusive\", null)\n"
                        + "Array(shared, exclusive, "
                        + "Manifest.requirementKey(shared), "
                        + "Manifest.requirementMode(shared), "
                        + "Manifest.requirementUnits(shared), "
                        + "Manifest.requirementKey(exclusive), "
                        + "Manifest.requirementMode(exclusive), "
                        + "Manifest.requirementUnits(exclusive))";

        ProtosArrayValue observed =
                assertInstanceOf(
                        ProtosArrayValue.class,
                        completed(
                                new ProtosSourceCompiler().compile(source),
                                fixture.activation()));
        assertEquals(8, observed.indexedSize().intValueExact());

        ProtosArrayValue shared =
                assertInstanceOf(
                        ProtosArrayValue.class,
                        observed.indexedAt(java.math.BigInteger.ZERO));
        ProtosArrayValue exclusive =
                assertInstanceOf(
                        ProtosArrayValue.class,
                        observed.indexedAt(java.math.BigInteger.ONE));
        assertEquals(3, shared.indexedSize().intValueExact());
        assertEquals(3, exclusive.indexedSize().intValueExact());
        org.junit.jupiter.api.Assertions.assertTrue(shared.isFrozen());
        org.junit.jupiter.api.Assertions.assertTrue(exclusive.isFrozen());
        assertNotSame(shared, exclusive);

        assertEquals(
                "gpu",
                assertInstanceOf(
                                ProtosStringValue.class,
                                observed.indexedAt(java.math.BigInteger.valueOf(2)))
                        .value());
        assertEquals(
                "shared",
                assertInstanceOf(
                                ProtosStringValue.class,
                                observed.indexedAt(java.math.BigInteger.valueOf(3)))
                        .value());
        assertEquals(
                2,
                assertInstanceOf(
                                ProtosIntegerValue.class,
                                observed.indexedAt(java.math.BigInteger.valueOf(4)))
                        .value()
                        .intValueExact());
        assertEquals(
                "db/integration",
                assertInstanceOf(
                                ProtosStringValue.class,
                                observed.indexedAt(java.math.BigInteger.valueOf(5)))
                        .value());
        assertEquals(
                "exclusive",
                assertInstanceOf(
                                ProtosStringValue.class,
                                observed.indexedAt(java.math.BigInteger.valueOf(6)))
                        .value());
        assertSame(
                ProtosNullValue.INSTANCE,
                observed.indexedAt(java.math.BigInteger.valueOf(7)));
    }

    @Test
    void requirementResourceKeyValidationAcceptsCanonicalAndRejectsMalformedKeys()
            throws Exception {
        Fixture fixture = fixture();
        String validSource =
                "Manifest: import(\"self:Manifest\")\n"
                        + "Array("
                        + "Manifest.requirement(\"gpu\", \"shared\", 1), "
                        + "Manifest.requirement(\"db/integration\", \"shared\", 1), "
                        + "Manifest.requirement(\"license/ansys\", \"shared\", 1), "
                        + "Manifest.requirement(\"test/http-server\", \"shared\", 1), "
                        + "Manifest.requirement(\"vendor/example-device\", \"shared\", 1), "
                        + "Manifest.requirement(\"gpu.v2/foo_bar-1\", \"shared\", 1), "
                        + "Manifest.requirement(\"0gpu\", \"shared\", 1))";

        ProtosArrayValue valid =
                assertInstanceOf(
                        ProtosArrayValue.class,
                        completed(
                                new ProtosSourceCompiler().compile(validSource),
                                fixture.activation()));
        assertEquals(7, valid.indexedSize().intValueExact());

        String[] invalidKeys = {
            "",
            "GPU",
            "/gpu",
            "gpu/",
            "gpu//fast",
            "gpu fast",
            "gpu+fast",
            "-gpu",
            "é"
        };
        for (String invalidKey : invalidKeys) {
            Fixture invalidFixture = fixture();
            String source =
                    "Manifest: import(\"self:Manifest\")\n"
                            + "Manifest.requirement(\""
                            + invalidKey
                            + "\", \"shared\", 1)";
            ProtosExecutionOutcome outcome =
                    ProtosRootTaskExecution.execute(
                            new ProtosSourceCompiler().compile(source),
                            invalidFixture.activation());
            assertEquals(
                    ProtosExecutionOutcome.State.FAILED,
                    outcome.state(),
                    () -> "expected invalid resource key to fail closed: " + invalidKey);
        }
    }

    @Test
    void requirementModeUnitsValidationAcceptsOnlyRatifiedShapes()
            throws Exception {
        Fixture fixture = fixture();
        String validSource =
                "Manifest: import(\"self:Manifest\")\n"
                        + "Array("
                        + "Manifest.requirement(\"gpu\", \"shared\", 1), "
                        + "Manifest.requirement(\"db/integration\", \"shared\", "
                        + "999999999999999999999999999999999999), "
                        + "Manifest.requirement(\"license/ansys\", \"exclusive\", null))";

        ProtosArrayValue valid =
                assertInstanceOf(
                        ProtosArrayValue.class,
                        completed(
                                new ProtosSourceCompiler().compile(validSource),
                                fixture.activation()));
        assertEquals(3, valid.indexedSize().intValueExact());

        String[] invalidArguments = {
            "\"gpu\", \"shared\", 0",
            "\"gpu\", \"shared\", -1",
            "\"gpu\", \"shared\", null",
            "\"gpu\", \"shared\", 1.0",
            "\"gpu\", \"shared\", Int64(1)",
            "\"gpu\", \"shared\", Integer {}",
            "\"gpu\", \"shared\", \"1\"",
            "\"gpu\", \"exclusive\", 1",
            "\"gpu\", \"exclusive\", 0",
            "\"gpu\", \"exclusive\", \"\"",
            "\"gpu\", \"unknown\", null",
            "\"gpu\", \"SHARED\", 1",
            "\"gpu\", 1, null"
        };
        for (String invalidArgumentsValue : invalidArguments) {
            Fixture invalidFixture = fixture();
            String source =
                    "Manifest: import(\"self:Manifest\")\n"
                            + "Manifest.requirement("
                            + invalidArgumentsValue
                            + ")";
            ProtosExecutionOutcome outcome =
                    ProtosRootTaskExecution.execute(
                            new ProtosSourceCompiler().compile(source),
                            invalidFixture.activation());
            assertEquals(
                    ProtosExecutionOutcome.State.FAILED,
                    outcome.state(),
                    () ->
                            "expected invalid Requirement mode/units to fail closed: "
                                    + invalidArgumentsValue);
        }
    }

    @Test
    void caseSpecRequirementAttachmentPreservesOrderAndFreezesCanonicalRecords()
            throws Exception {
        Fixture fixture = fixture();
        String source =
                "Manifest: import(\"self:Manifest\")\n"
                        + "base: Manifest.caseSpec(Array(\"i5/resourceful.protos\", \"integer\", \"7\"))\n"
                        + "attached: Manifest.caseSpecWithRequirements(base, Array("
                        + "Array(\"gpu\", \"shared\", 2), "
                        + "Array(\"db/integration\", \"exclusive\", null)))\n"
                        + "requirements: Manifest.caseRequirements(attached)\n"
                        + "Array(attached, requirements, "
                        + "Manifest.requirementKey(requirements[0]), "
                        + "Manifest.requirementMode(requirements[0]), "
                        + "Manifest.requirementUnits(requirements[0]), "
                        + "Manifest.requirementKey(requirements[1]), "
                        + "Manifest.requirementMode(requirements[1]), "
                        + "Manifest.requirementUnits(requirements[1]))";

        ProtosArrayValue observed =
                assertInstanceOf(
                        ProtosArrayValue.class,
                        completed(
                                new ProtosSourceCompiler().compile(source),
                                fixture.activation()));
        assertEquals(8, observed.indexedSize().intValueExact());

        ProtosArrayValue attached =
                assertInstanceOf(
                        ProtosArrayValue.class,
                        observed.indexedAt(java.math.BigInteger.ZERO));
        ProtosArrayValue requirements =
                assertInstanceOf(
                        ProtosArrayValue.class,
                        observed.indexedAt(java.math.BigInteger.ONE));
        assertEquals(5, attached.indexedSize().intValueExact());
        assertEquals(2, requirements.indexedSize().intValueExact());
        org.junit.jupiter.api.Assertions.assertTrue(attached.isFrozen());
        org.junit.jupiter.api.Assertions.assertTrue(requirements.isFrozen());

        ProtosArrayValue first =
                assertInstanceOf(
                        ProtosArrayValue.class,
                        requirements.indexedAt(java.math.BigInteger.ZERO));
        ProtosArrayValue second =
                assertInstanceOf(
                        ProtosArrayValue.class,
                        requirements.indexedAt(java.math.BigInteger.ONE));
        org.junit.jupiter.api.Assertions.assertTrue(first.isFrozen());
        org.junit.jupiter.api.Assertions.assertTrue(second.isFrozen());
        assertEquals(3, first.indexedSize().intValueExact());
        assertEquals(3, second.indexedSize().intValueExact());

        assertEquals(
                "gpu",
                assertInstanceOf(
                                ProtosStringValue.class,
                                observed.indexedAt(java.math.BigInteger.valueOf(2)))
                        .value());
        assertEquals(
                "shared",
                assertInstanceOf(
                                ProtosStringValue.class,
                                observed.indexedAt(java.math.BigInteger.valueOf(3)))
                        .value());
        assertEquals(
                2,
                assertInstanceOf(
                                ProtosIntegerValue.class,
                                observed.indexedAt(java.math.BigInteger.valueOf(4)))
                        .value()
                        .intValueExact());
        assertEquals(
                "db/integration",
                assertInstanceOf(
                                ProtosStringValue.class,
                                observed.indexedAt(java.math.BigInteger.valueOf(5)))
                        .value());
        assertEquals(
                "exclusive",
                assertInstanceOf(
                                ProtosStringValue.class,
                                observed.indexedAt(java.math.BigInteger.valueOf(6)))
                        .value());
        assertSame(
                ProtosNullValue.INSTANCE,
                observed.indexedAt(java.math.BigInteger.valueOf(7)));
    }

    @Test
    void caseSpecRequirementAttachmentRejectsDuplicateCaseKeyWithoutMerging()
            throws Exception {
        Fixture controlFixture = fixture();
        String controlSource =
                "Manifest: import(\"self:Manifest\")\n"
                        + "base: Manifest.caseSpec(Array(\"i5/control.protos\", \"integer\", \"1\"))\n"
                        + "Manifest.caseSpecWithRequirements(base, Array("
                        + "Manifest.requirement(\"gpu\", \"shared\", 1)))";
        ProtosExecutionOutcome controlOutcome =
                ProtosRootTaskExecution.execute(
                        new ProtosSourceCompiler().compile(controlSource),
                        controlFixture.activation());
        assertEquals(
                ProtosExecutionOutcome.State.COMPLETED,
                controlOutcome.state(),
                "single canonical Requirement must attach before duplicate rejection is tested");

        String[] duplicatePairs = {
            "Manifest.requirement(\"gpu\", \"shared\", 1), "
                    + "Manifest.requirement(\"gpu\", \"shared\", 1)",
            "Manifest.requirement(\"gpu\", \"shared\", 2), "
                    + "Manifest.requirement(\"gpu\", \"exclusive\", null)"
        };

        for (String duplicatePair : duplicatePairs) {
            Fixture fixture = fixture();
            String source =
                    "Manifest: import(\"self:Manifest\")\n"
                            + "base: Manifest.caseSpec(Array(\"i5/duplicate.protos\", \"integer\", \"1\"))\n"
                            + "Manifest.caseSpecWithRequirements(base, Array("
                            + duplicatePair
                            + "))";
            ProtosExecutionOutcome outcome =
                    ProtosRootTaskExecution.execute(
                            new ProtosSourceCompiler().compile(source),
                            fixture.activation());
            assertEquals(
                    ProtosExecutionOutcome.State.FAILED,
                    outcome.state(),
                    () -> "expected duplicate (case,key) to fail closed: " + duplicatePair);
        }
    }

    @Test
    void sameResourceKeyMayAppearInDifferentCaseSpecs()
            throws Exception {
        Fixture fixture = fixture();
        String source =
                "Manifest: import(\"self:Manifest\")\n"
                        + "first: Manifest.caseSpec(Array(\"i5/first.protos\", \"integer\", \"1\"))\n"
                        + "second: Manifest.caseSpec(Array(\"i5/second.protos\", \"integer\", \"2\"))\n"
                        + "first = Manifest.caseSpecWithRequirements(first, Array("
                        + "Manifest.requirement(\"gpu\", \"shared\", 1)))\n"
                        + "second = Manifest.caseSpecWithRequirements(second, Array("
                        + "Manifest.requirement(\"gpu\", \"exclusive\", null)))\n"
                        + "Array(Manifest.caseRequirements(first).size(), "
                        + "Manifest.caseRequirements(second).size())";

        ProtosArrayValue sizes =
                assertInstanceOf(
                        ProtosArrayValue.class,
                        completed(
                                new ProtosSourceCompiler().compile(source),
                                fixture.activation()));
        assertEquals(2, sizes.indexedSize().intValueExact());
        assertEquals(
                1,
                assertInstanceOf(
                                ProtosIntegerValue.class,
                                sizes.indexedAt(java.math.BigInteger.ZERO))
                        .value()
                        .intValueExact());
        assertEquals(
                1,
                assertInstanceOf(
                                ProtosIntegerValue.class,
                                sizes.indexedAt(java.math.BigInteger.ONE))
                        .value()
                        .intValueExact());
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

    @Test
    void separatePackageTomlFilesystemLoadsRealCorpusWithoutExecutingFixtures()
            throws Exception {
        Fixture fixture = fixture();

        try (ProtosNioReadOnlyTreeFilesystemBackend conformanceBackend =
                        new ProtosNioReadOnlyTreeFilesystemBackend(CORPUS_ROOT);
                ProtosNioReadOnlyTreeFilesystemBackend packageTomlBackend =
                        new ProtosNioReadOnlyTreeFilesystemBackend(PACKAGE_TOML_CORPUS_ROOT)) {
            assumeTrue(
                    conformanceBackend.secureConfinementAvailable()
                            && packageTomlBackend.secureConfinementAvailable(),
                    "host provider has no SecureDirectoryStream");

            installFilesystem(
                    fixture.prelude(),
                    fixture.activation(),
                    "filesystem",
                    conformanceBackend);
            installFilesystem(
                    fixture.prelude(),
                    fixture.activation(),
                    "packageTomlFilesystem",
                    packageTomlBackend);

            Object result =
                    completed(
                            new ProtosSourceCompiler()
                                    .compile(
                                            Files.readString(
                                                    PACKAGE_TOML_FILESYSTEM_FIXTURE,
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
        installFilesystem(prelude, activation, "filesystem", backend);
    }

    private static void installFilesystem(
            ProtosPrelude prelude,
            ProtosActivation activation,
            String slotName,
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
        activation.context().createLocalSlot(slotName, filesystem);
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
