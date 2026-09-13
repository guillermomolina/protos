/*
 * THE LICENSED WORK IS PROVIDED UNDER THE TERMS OF THE ADAPTIVE PUBLIC LICENSE
 * ("LICENSE") AS FIRST COMPLETED BY: Guillermo Adrián Molina. See LICENSE.TXT.
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
        Path actorRoot = Files.createDirectories(tempDirectory.resolve("actor"));
        Path groupRoot = Files.createDirectories(tempDirectory.resolve("group"));
        Path packageRoot = Files.createDirectories(tempDirectory.resolve("package"));

        try (ProtosNioReadOnlyTreeFilesystemBackend ordinaryBackend =
                        new ProtosNioReadOnlyTreeFilesystemBackend(ordinaryRoot);
                ProtosNioReadOnlyTreeFilesystemBackend actorBackend =
                        new ProtosNioReadOnlyTreeFilesystemBackend(actorRoot);
                ProtosNioReadOnlyTreeFilesystemBackend groupBackend =
                        new ProtosNioReadOnlyTreeFilesystemBackend(groupRoot);
                ProtosNioReadOnlyTreeFilesystemBackend packageBackend =
                        new ProtosNioReadOnlyTreeFilesystemBackend(packageRoot)) {
            installFilesystem(fixture, "filesystem", ordinaryBackend);
            installFilesystem(fixture, "actorFilesystem", actorBackend);
            installFilesystem(fixture, "groupFilesystem", groupBackend);
            installFilesystem(fixture, "packageTomlFilesystem", packageBackend);

            ProtosTestCorpusRegistry.install(fixture.activation());
            ProtosObjectValue registry =
                    assertInstanceOf(
                            ProtosObjectValue.class,
                            fixture.activation()
                                    .context()
                                    .readLocalSlot(ProtosTestCorpusRegistry.REGISTRY_SLOT)
                                    .orElseThrow());
            assertTrue(registry.isFrozen());
            assertEquals(4, registry.localSlotsSnapshot().size());

            assertBinding(fixture.activation(), registry, "protos/corpus/conformance",
                    "manifest", "filesystem");
            assertBinding(fixture.activation(), registry, "protos/corpus/actor",
                    "manifest", "actorFilesystem");
            assertBinding(fixture.activation(), registry, "protos/corpus/group",
                    "manifest", "groupFilesystem");
            assertBinding(fixture.activation(), registry, "protos/corpus/package-toml",
                    "package-toml", "packageTomlFilesystem");

            assertFalse(registry.hasLocalSlot("protos/test/ordinary"));
            assertFalse(registry.hasLocalSlot("protos/test/actor"));
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
