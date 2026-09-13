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
import com.guillermomolina.protos.execution.ProtosPolyglotRuntimeHost;
import com.guillermomolina.protos.execution.ProtosStandardLibraryModuleResolver;
import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class ProtosTestToolExecutionRequirementRegistryTest {
    private static final Path CORE = Path.of("protos", "lib", "core");
    private static final Path STANDARD_LIBRARY = Path.of("protos", "lib");
    private static final Path TOOL_ROOT = Path.of("protos", "tools", "test");

    @Test
    void registryIsExactInvocationScopedFrozenAndSourceFree() throws Exception {
        Fixture fixture = fixture();
        try (ProtosPolyglotRuntimeHost runtimeHost = ProtosPolyglotRuntimeHost.open();
                ProtosTestToolAsyncExecutionScope scope =
                        ProtosTestToolAsyncExecutionScope.install(
                                fixture.activation(),
                                runtimeHost,
                                fixture.prelude(),
                                fixture.prelude(),
                                fixture.prelude())) {
            ProtosTestExecutionRequirementRegistry.install(fixture.activation());
            ProtosObjectValue registry =
                    assertInstanceOf(
                            ProtosObjectValue.class,
                            fixture.activation()
                                    .context()
                                    .readLocalSlot(
                                            ProtosTestExecutionRequirementRegistry.REGISTRY_SLOT)
                                    .orElseThrow());
            assertTrue(registry.isFrozen());
            assertEquals(4, registry.localSlotsSnapshot().size());

            assertBinding(fixture.activation(), registry, "protos/test/ordinary",
                    "executionAsync", "executionInspectAsync",
                    "resourceExecutionAsync", "resourceExecutionInspectAsync");
            assertBinding(fixture.activation(), registry, "protos/test/actor",
                    "actorExecutionAsync", "actorExecutionInspectAsync",
                    "actorResourceExecutionAsync", "actorResourceExecutionInspectAsync");
            assertBinding(fixture.activation(), registry, "protos/test/group",
                    "groupExecutionAsync", "groupExecutionInspectAsync",
                    "groupResourceExecutionAsync", "groupResourceExecutionInspectAsync");
            assertBinding(fixture.activation(), registry, "protos/test/package",
                    "packageExecutionAsync", "packageExecutionInspectAsync",
                    "packageResourceExecutionAsync", "packageResourceExecutionInspectAsync");

            assertFalse(registry.hasLocalSlot("protos/corpus/conformance"));
            assertFalse(registry.hasLocalSlot("protos/corpus/actor"));
            assertThrows(
                    IllegalStateException.class,
                    () -> ProtosTestExecutionRequirementRegistry.install(fixture.activation()));
        }
    }

    @Test
    void missingExecutionFacilityFailsClosedBeforePublication() throws Exception {
        Fixture fixture = fixture();
        assertThrows(
                IllegalStateException.class,
                () -> ProtosTestExecutionRequirementRegistry.install(fixture.activation()));
        assertFalse(
                fixture.activation()
                        .context()
                        .hasLocalSlot(ProtosTestExecutionRequirementRegistry.REGISTRY_SLOT));
    }

    private static void assertBinding(
            ProtosActivation activation,
            ProtosObjectValue registry,
            String requirementId,
            String executionSlot,
            String inspectionSlot,
            String resourceExecutionSlot,
            String resourceInspectionSlot) {
        ProtosObjectValue binding =
                assertInstanceOf(
                        ProtosObjectValue.class,
                        registry.readLocalSlot(requirementId).orElseThrow());
        assertTrue(binding.isFrozen());
        assertEquals(4, binding.localSlotsSnapshot().size());
        assertFalse(binding.hasLocalSlot("filesystem"));
        assertFalse(binding.hasLocalSlot("planLoader"));
        assertSame(
                activation.context().readLocalSlot(executionSlot).orElseThrow(),
                binding.readLocalSlot("executionAsync").orElseThrow());
        assertSame(
                activation.context().readLocalSlot(inspectionSlot).orElseThrow(),
                binding.readLocalSlot("executionInspectAsync").orElseThrow());
        assertSame(
                activation.context().readLocalSlot(resourceExecutionSlot).orElseThrow(),
                binding.readLocalSlot("resourceExecutionAsync").orElseThrow());
        assertSame(
                activation.context().readLocalSlot(resourceInspectionSlot).orElseThrow(),
                binding.readLocalSlot("resourceExecutionInspectAsync").orElseThrow());
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
