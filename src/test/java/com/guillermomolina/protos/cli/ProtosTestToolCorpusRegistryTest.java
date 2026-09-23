/*
 * THE LICENSED WORK IS PROVIDED UNDER THE TERMS OF THE ADAPTIVE PUBLIC LICENSE
 * ("LICENSE") AS FIRST COMPLETED BY: Guillermo Adrián Molina.
 * See LICENSE.TXT for the complete terms.
 */
package com.guillermomolina.protos.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.execution.ProtosBundledToolModuleResolver;
import com.guillermomolina.protos.execution.ProtosCoreBootstrap;
import com.guillermomolina.protos.execution.ProtosNioReadOnlyTreeFilesystemBackend;
import com.guillermomolina.protos.execution.ProtosStandardFilesystemProtocol;
import com.guillermomolina.protos.execution.ProtosStandardLibraryModuleResolver;
import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosFilesystemValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosStringValue;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ProtosTestToolCorpusRegistryTest {
    private static final Path CORE = Path.of("protos", "lib", "core");
    private static final Path STANDARD_LIBRARY = Path.of("protos", "lib");
    private static final Path TOOL_ROOT = Path.of("protos", "tools", "test");

    @TempDir Path tempDirectory;

    @Test
    void registryIsExactInvocationScopedFrozenAndExecutionFree() throws Exception {
        Fixture fixture = fixture();
        Path ordinaryRoot = Files.createDirectories(tempDirectory.resolve("ordinary"));
        Path processSnapshotRoot =
                Files.createDirectories(tempDirectory.resolve("process-snapshot"));
        Path actorRoot = Files.createDirectories(tempDirectory.resolve("actor"));
        Path groupRoot = Files.createDirectories(tempDirectory.resolve("group"));
        Path packageRoot = Files.createDirectories(tempDirectory.resolve("package"));
        Path libraryRoot = Files.createDirectories(tempDirectory.resolve("library"));
        Path versionRoot = Files.createDirectories(tempDirectory.resolve("version"));
        Path lockRoot = Files.createDirectories(tempDirectory.resolve("lock"));
        Path resolutionInputRoot =
                Files.createDirectories(tempDirectory.resolve("resolution-input"));
        Path contentIdentityRoot =
                Files.createDirectories(tempDirectory.resolve("content-identity"));
        Path resolutionInputLockRoot =
                Files.createDirectories(tempDirectory.resolve("resolution-input-lock"));
        Path resolutionRootRoot =
                Files.createDirectories(tempDirectory.resolve("resolution-root"));
        Path executionPlanRoot =
                Files.createDirectories(tempDirectory.resolve("execution-plan"));
        Path projectProjectionRoot =
                Files.createDirectories(tempDirectory.resolve("project-projection"));

        try (ProtosNioReadOnlyTreeFilesystemBackend ordinaryBackend =
                        new ProtosNioReadOnlyTreeFilesystemBackend(ordinaryRoot);
                ProtosNioReadOnlyTreeFilesystemBackend processSnapshotBackend =
                        new ProtosNioReadOnlyTreeFilesystemBackend(processSnapshotRoot);
                ProtosNioReadOnlyTreeFilesystemBackend actorBackend =
                        new ProtosNioReadOnlyTreeFilesystemBackend(actorRoot);
                ProtosNioReadOnlyTreeFilesystemBackend groupBackend =
                        new ProtosNioReadOnlyTreeFilesystemBackend(groupRoot);
                ProtosNioReadOnlyTreeFilesystemBackend packageBackend =
                        new ProtosNioReadOnlyTreeFilesystemBackend(packageRoot);
                ProtosNioReadOnlyTreeFilesystemBackend libraryBackend =
                        new ProtosNioReadOnlyTreeFilesystemBackend(libraryRoot);
                ProtosNioReadOnlyTreeFilesystemBackend versionBackend =
                        new ProtosNioReadOnlyTreeFilesystemBackend(versionRoot);
                ProtosNioReadOnlyTreeFilesystemBackend lockBackend =
                        new ProtosNioReadOnlyTreeFilesystemBackend(lockRoot);
                ProtosNioReadOnlyTreeFilesystemBackend resolutionInputBackend =
                        new ProtosNioReadOnlyTreeFilesystemBackend(resolutionInputRoot);
                ProtosNioReadOnlyTreeFilesystemBackend contentIdentityBackend =
                        new ProtosNioReadOnlyTreeFilesystemBackend(contentIdentityRoot);
                ProtosNioReadOnlyTreeFilesystemBackend resolutionInputLockBackend =
                        new ProtosNioReadOnlyTreeFilesystemBackend(resolutionInputLockRoot);
                ProtosNioReadOnlyTreeFilesystemBackend resolutionRootBackend =
                        new ProtosNioReadOnlyTreeFilesystemBackend(resolutionRootRoot);
                ProtosNioReadOnlyTreeFilesystemBackend executionPlanBackend =
                        new ProtosNioReadOnlyTreeFilesystemBackend(executionPlanRoot);
                ProtosNioReadOnlyTreeFilesystemBackend projectProjectionBackend =
                        new ProtosNioReadOnlyTreeFilesystemBackend(projectProjectionRoot)) {
            installFilesystem(fixture, "filesystem", ordinaryBackend);
            installFilesystem(
                    fixture,
                    "processSnapshotFilesystem",
                    processSnapshotBackend);
            installFilesystem(fixture, "actorFilesystem", actorBackend);
            installFilesystem(fixture, "groupFilesystem", groupBackend);
            installFilesystem(fixture, "packageTomlFilesystem", packageBackend);
            installFilesystem(fixture, "libraryFilesystem", libraryBackend);
            installFilesystem(fixture, "packageToolVersionFilesystem", versionBackend);
            installFilesystem(fixture, "packageToolLockFilesystem", lockBackend);
            installFilesystem(fixture, "packageToolResolutionInputFilesystem", resolutionInputBackend);
            installFilesystem(fixture, "packageToolContentIdentityFilesystem", contentIdentityBackend);
            installFilesystem(fixture, "packageToolResolutionInputLockFilesystem", resolutionInputLockBackend);
            installFilesystem(fixture, "packageToolResolutionRootFilesystem", resolutionRootBackend);
            installFilesystem(fixture, "packageToolExecutionPlanFilesystem", executionPlanBackend);
            installFilesystem(fixture, "packageToolProjectProjectionFilesystem", projectProjectionBackend);

            ProtosTestCorpusRegistry.install(fixture.activation());
            ProtosObjectValue registry =
                    assertInstanceOf(
                            ProtosObjectValue.class,
                            fixture.activation()
                                    .context()
                                    .readLocalSlot(ProtosTestCorpusRegistry.REGISTRY_SLOT)
                                    .orElseThrow());
            assertTrue(registry.isFrozen());
            assertEquals(20, registry.localSlotsSnapshot().size());

            assertBinding(fixture.activation(), registry, "protos/corpus/conformance",
                    "manifest", "filesystem");
            assertBinding(
                    fixture.activation(),
                    registry,
                    "protos/corpus/process-snapshot",
                    "manifest",
                    "processSnapshotFilesystem");
            assertBinding(fixture.activation(), registry, "protos/corpus/actor",
                    "manifest", "actorFilesystem");
            assertBinding(fixture.activation(), registry, "protos/corpus/group",
                    "manifest", "groupFilesystem");
            assertBinding(fixture.activation(), registry, "protos/corpus/package-toml",
                    "package-toml", "packageTomlFilesystem");
            assertBinding(
                    fixture.activation(),
                    registry,
                    "protos/corpus/library/uri",
                    "repository-explicit",
                    "libraryFilesystem");
            assertBinding(
                    fixture.activation(),
                    registry,
                    "protos/corpus/library/csv",
                    "repository-explicit",
                    "libraryFilesystem");
            assertBinding(
                    fixture.activation(),
                    registry,
                    "protos/corpus/library/cli",
                    "repository-explicit",
                    "libraryFilesystem");
            assertBinding(
                    fixture.activation(),
                    registry,
                    "protos/corpus/library/math/integer",
                    "repository-explicit",
                    "libraryFilesystem");
            assertBinding(
                    fixture.activation(),
                    registry,
                    "protos/corpus/library/crypto/sha256",
                    "repository-explicit",
                    "libraryFilesystem");
            assertBinding(
                    fixture.activation(),
                    registry,
                    "protos/corpus/library/network/ip-addresses",
                    "repository-explicit",
                    "libraryFilesystem");
            assertBinding(
                    fixture.activation(),
                    registry,
                    "protos/corpus/library/network/ip-endpoints",
                    "repository-explicit",
                    "libraryFilesystem");
            assertCaseOutcomesBinding(
                    fixture.activation(),
                    registry,
                    "protos/corpus/package-tool/version",
                    "protos/package-tool/version",
                    "packageToolVersionFilesystem");
            assertCaseOutcomesBinding(
                    fixture.activation(),
                    registry,
                    "protos/corpus/package-tool/lock",
                    "protos/package-tool/lock",
                    "packageToolLockFilesystem");
            assertCaseOutcomesBinding(
                    fixture.activation(),
                    registry,
                    "protos/corpus/package-tool/resolution-input",
                    "protos/package-tool/resolution-input",
                    "packageToolResolutionInputFilesystem");
            assertProjectTreeBinding(
                    fixture.activation(),
                    registry,
                    "protos/corpus/package-tool/content-identity",
                    "protos/package-tool/content-identity",
                    "packageToolContentIdentityFilesystem");
            assertProjectTreeBinding(
                    fixture.activation(),
                    registry,
                    "protos/corpus/package-tool/resolution-input-lock",
                    "protos/package-tool/resolution-input-lock",
                    "packageToolResolutionInputLockFilesystem");
            assertProjectTreeBinding(
                    fixture.activation(),
                    registry,
                    "protos/corpus/package-tool/resolution-root",
                    "protos/package-tool/resolution-root",
                    "packageToolResolutionRootFilesystem");
            assertProjectTreeBinding(
                    fixture.activation(),
                    registry,
                    "protos/corpus/package-tool/execution-plan",
                    "protos/package-tool/execution-plan",
                    "packageToolExecutionPlanFilesystem");
            assertProjectTreeBinding(
                    fixture.activation(),
                    registry,
                    "protos/corpus/package-tool/project-projection",
                    "protos/package-tool/project-projection",
                    "packageToolProjectProjectionFilesystem");

            assertFalse(registry.hasLocalSlot("protos/test/ordinary"));
            assertFalse(registry.hasLocalSlot("protos/corpus/library/unknown"));
            assertThrows(
                    IllegalStateException.class,
                    () -> ProtosTestCorpusRegistry.install(fixture.activation()));
        }
    }

    @Test
    void missingSourceFacilityFailsClosedBeforePublication() throws Exception {
        Fixture fixture = fixture();
        assertThrows(
                IllegalStateException.class,
                () -> ProtosTestCorpusRegistry.install(fixture.activation()));
        assertFalse(
                fixture.activation()
                        .context()
                        .hasLocalSlot(ProtosTestCorpusRegistry.REGISTRY_SLOT));
    }

    private static void installFilesystem(
            Fixture fixture,
            String slotName,
            ProtosStandardFilesystemProtocol.Backend backend) {
        Object rawFilesystem =
                ProtosStandardFilesystemProtocol.createCapability(
                        fixture.prelude().bytesPrototypeForRuntime(),
                        fixture.activation(),
                        backend);
        ProtosFilesystemValue filesystem =
                assertInstanceOf(ProtosFilesystemValue.class, rawFilesystem);
        fixture.activation().context().createLocalSlot(slotName, filesystem);
    }

    private static void assertBinding(
            ProtosActivation activation,
            ProtosObjectValue registry,
            String corpusId,
            String expectedPlanLoader,
            String filesystemSlot) {
        ProtosObjectValue binding =
                assertInstanceOf(
                        ProtosObjectValue.class,
                        registry.readLocalSlot(corpusId).orElseThrow());
        assertTrue(binding.isFrozen());
        assertEquals(2, binding.localSlotsSnapshot().size());
        assertSame(
                activation.context().readLocalSlot(filesystemSlot).orElseThrow(),
                binding.readLocalSlot("filesystem").orElseThrow());
        assertEquals(
                expectedPlanLoader,
                assertInstanceOf(
                                ProtosStringValue.class,
                                binding.readLocalSlot("planLoader").orElseThrow())
                        .value());
        assertFalse(binding.hasLocalSlot("executionAsync"));
        assertFalse(binding.hasLocalSlot("resourceExecutionAsync"));
    }

    /** D132: case-outcomes bindings add only the explicit logical case namespace. */
    private static void assertCaseOutcomesBinding(
            ProtosActivation activation,
            ProtosObjectValue registry,
            String corpusId,
            String expectedCaseNamespace,
            String filesystemSlot) {
        ProtosObjectValue binding =
                assertInstanceOf(
                        ProtosObjectValue.class,
                        registry.readLocalSlot(corpusId).orElseThrow());
        assertTrue(binding.isFrozen());
        assertEquals(3, binding.localSlotsSnapshot().size());
        assertSame(
                activation.context().readLocalSlot(filesystemSlot).orElseThrow(),
                binding.readLocalSlot("filesystem").orElseThrow());
        assertEquals(
                "case-outcomes",
                assertInstanceOf(
                                ProtosStringValue.class,
                                binding.readLocalSlot("planLoader").orElseThrow())
                        .value());
        assertEquals(
                expectedCaseNamespace,
                assertInstanceOf(
                                ProtosStringValue.class,
                                binding.readLocalSlot("caseNamespace").orElseThrow())
                        .value());
        assertFalse(binding.hasLocalSlot("executionAsync"));
        assertFalse(binding.hasLocalSlot("resourceExecutionAsync"));
    }

    private static void assertProjectTreeBinding(
            ProtosActivation activation,
            ProtosObjectValue registry,
            String corpusId,
            String expectedCaseNamespace,
            String filesystemSlot) {
        ProtosObjectValue binding =
                assertInstanceOf(
                        ProtosObjectValue.class,
                        registry.readLocalSlot(corpusId).orElseThrow());
        assertTrue(binding.isFrozen());
        assertEquals(3, binding.localSlotsSnapshot().size());
        assertSame(
                activation.context().readLocalSlot(filesystemSlot).orElseThrow(),
                binding.readLocalSlot("filesystem").orElseThrow());
        assertEquals(
                "project-tree",
                assertInstanceOf(
                                ProtosStringValue.class,
                                binding.readLocalSlot("planLoader").orElseThrow())
                        .value());
        assertEquals(
                expectedCaseNamespace,
                assertInstanceOf(
                                ProtosStringValue.class,
                                binding.readLocalSlot("caseNamespace").orElseThrow())
                        .value());
        assertFalse(binding.hasLocalSlot("caseAuthorityExecutionAsync"));
        assertFalse(binding.hasLocalSlot("executionAsync"));
        assertFalse(binding.hasLocalSlot("resourceExecutionAsync"));
    }

    private static Fixture fixture() throws Exception {
        ProtosBundledToolModuleResolver resolver =
                new ProtosBundledToolModuleResolver(
                        "test",
                        TOOL_ROOT,
                        TOOL_ROOT.resolveSibling("shared"),
                        new ProtosStandardLibraryModuleResolver(STANDARD_LIBRARY));
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE, resolver);
        return new Fixture(prelude, prelude.newModuleActivation());
    }

    private record Fixture(ProtosPrelude prelude, ProtosActivation activation) {}
}
