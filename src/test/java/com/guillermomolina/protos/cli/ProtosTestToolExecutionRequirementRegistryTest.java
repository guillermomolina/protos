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

import com.guillermomolina.protos.execution.ProtosAsyncProcessSnapshotExecutionFacility;
import com.guillermomolina.protos.execution.ProtosBundledToolModuleResolver;
import com.guillermomolina.protos.execution.ProtosCoreBootstrap;
import com.guillermomolina.protos.execution.ProtosPolyglotRuntimeHost;
import com.guillermomolina.protos.execution.ProtosProcessSnapshotLogicalCaseExecutionFacility;
import com.guillermomolina.protos.execution.ProtosStandardLibraryModuleResolver;
import com.guillermomolina.protos.execution.ProtosTestLogicalCaseExecutionFacility;
import com.guillermomolina.protos.execution.ProtosTestToolFileSelectionFacility;
import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import java.nio.file.Path;
import java.util.List;
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
            assertEquals(5, registry.localSlotsSnapshot().size());

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
            assertExecutionOnlyBinding(
                    fixture.activation(),
                    registry,
                    "protos/test/process-snapshot",
                    ProtosAsyncProcessSnapshotExecutionFacility.BOOTSTRAP_SLOT);

            assertFalse(registry.hasLocalSlot("protos/corpus/conformance"));
            assertFalse(registry.hasLocalSlot("protos/corpus/actor"));
            assertThrows(
                    IllegalStateException.class,
                    () -> ProtosTestExecutionRequirementRegistry.install(fixture.activation()));
        }
    }

    @Test
    void ordinaryBindingCarriesSuiteNativeLogicalCaseExecutionRouteWhenInstalled()
            throws Exception {
        Fixture fixture = fixture();
        ProtosBundledToolModuleResolver resolver =
                new ProtosBundledToolModuleResolver(
                        "test",
                        TOOL_ROOT,
                        TOOL_ROOT.resolveSibling("shared"),
                        new ProtosStandardLibraryModuleResolver(STANDARD_LIBRARY));

        try (ProtosPolyglotRuntimeHost runtimeHost = ProtosPolyglotRuntimeHost.open();
                ProtosTestToolAsyncExecutionScope scope =
                        ProtosTestToolAsyncExecutionScope.installWithCaseAuthorities(
                                fixture.activation(),
                                runtimeHost,
                                CORE,
                                resolver,
                                resolver,
                                resolver,
                                resolver,
                                List.of(
                                        new ProtosTestToolFileSelectionFacility.CorpusSourceRoot(
                                                "test-corpus",
                                                Path.of("protos", "tests"))),
                                fixture.prelude(),
                                fixture.prelude(),
                                fixture.prelude(),
                                Path.of(
                                        "protos", "tests", "package-tool",
                                        "content-identity", "cases"),
                                Path.of(
                                        "protos", "tests", "package-tool",
                                        "resolution-input-lock", "cases"),
                                Path.of(
                                        "protos", "tests", "package-tool",
                                        "resolution-root", "cases"),
                                Path.of(
                                        "protos", "tests", "package-tool",
                                        "execution-plan", "cases"),
                                Path.of(
                                        "protos", "tests", "package-tool",
                                        "project-projection", "cases"))) {
            ProtosTestExecutionRequirementRegistry.install(fixture.activation());

            ProtosObjectValue registry =
                    assertInstanceOf(
                            ProtosObjectValue.class,
                            fixture.activation()
                                    .context()
                                    .readLocalSlot(
                                            ProtosTestExecutionRequirementRegistry.REGISTRY_SLOT)
                                    .orElseThrow());

            ProtosObjectValue ordinaryBinding =
                    assertInstanceOf(
                            ProtosObjectValue.class,
                            registry.readLocalSlot("protos/test/ordinary").orElseThrow());

            // TOOL009-D: the transport boundary reuses whichever host facility already
            // installed logicalCaseExecutionAsync (D152/D153) rather than provisioning a
            // parallel one, so the bound value must be the exact same object.
            assertTrue(ordinaryBinding.isFrozen());
            assertEquals(5, ordinaryBinding.localSlotsSnapshot().size());
            assertSame(
                    fixture.activation()
                            .context()
                            .readLocalSlot(ProtosTestLogicalCaseExecutionFacility.BOOTSTRAP_SLOT)
                            .orElseThrow(),
                    ordinaryBinding.readLocalSlot("logicalCaseExecutionAsync").orElseThrow());

            ProtosObjectValue actorBinding =
                    assertInstanceOf(
                            ProtosObjectValue.class,
                            registry.readLocalSlot("protos/test/actor").orElseThrow());
            assertTrue(actorBinding.isFrozen());
            assertEquals(5, actorBinding.localSlotsSnapshot().size());
            assertSame(
                    fixture.activation()
                            .context()
                            .readLocalSlot(
                                    ProtosTestToolAsyncExecutionScope
                                            .ACTOR_LOGICAL_CASE_EXECUTION_BOOTSTRAP_SLOT)
                            .orElseThrow(),
                    actorBinding.readLocalSlot("logicalCaseExecutionAsync").orElseThrow());

            ProtosObjectValue groupBinding =
                    assertInstanceOf(
                            ProtosObjectValue.class,
                            registry.readLocalSlot("protos/test/group").orElseThrow());
            assertTrue(groupBinding.isFrozen());
            assertEquals(5, groupBinding.localSlotsSnapshot().size());
            assertSame(
                    fixture.activation()
                            .context()
                            .readLocalSlot(
                                    ProtosTestToolAsyncExecutionScope
                                            .GROUP_LOGICAL_CASE_EXECUTION_BOOTSTRAP_SLOT)
                            .orElseThrow(),
                    groupBinding.readLocalSlot("logicalCaseExecutionAsync").orElseThrow());

            // TOOL009 Package Tool infrastructure: the Package Logical Case facility is
            // now installed, so "protos/test/package" gains the suite-native route
            // below, reusing whichever host facility already installed
            // packageLogicalCaseExecutionAsync rather than provisioning a parallel one.
            ProtosObjectValue packageBinding =
                    assertInstanceOf(
                            ProtosObjectValue.class,
                            registry.readLocalSlot("protos/test/package").orElseThrow());
            assertTrue(packageBinding.isFrozen());
            assertEquals(5, packageBinding.localSlotsSnapshot().size());
            assertSame(
                    fixture.activation()
                            .context()
                            .readLocalSlot(
                                    ProtosTestToolAsyncExecutionScope
                                            .PACKAGE_LOGICAL_CASE_EXECUTION_BOOTSTRAP_SLOT)
                            .orElseThrow(),
                    packageBinding.readLocalSlot("logicalCaseExecutionAsync").orElseThrow());

            // TOOL009-E: process-snapshot now also carries the suite-native logical Case
            // execution route, reusing whichever host facility already installed
            // processSnapshotLogicalCaseExecutionAsync rather than provisioning a parallel one.
            ProtosObjectValue processSnapshotBinding =
                    assertInstanceOf(
                            ProtosObjectValue.class,
                            registry.readLocalSlot("protos/test/process-snapshot")
                                    .orElseThrow());
            assertTrue(processSnapshotBinding.isFrozen());
            assertEquals(2, processSnapshotBinding.localSlotsSnapshot().size());
            assertSame(
                    fixture.activation()
                            .context()
                            .readLocalSlot(
                                    ProtosProcessSnapshotLogicalCaseExecutionFacility
                                            .BOOTSTRAP_SLOT)
                            .orElseThrow(),
                    processSnapshotBinding
                            .readLocalSlot("logicalCaseExecutionAsync")
                            .orElseThrow());
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

    private static void assertExecutionOnlyBinding(
            ProtosActivation activation,
            ProtosObjectValue registry,
            String requirementId,
            String executionSlot) {
        ProtosObjectValue binding =
                assertInstanceOf(
                        ProtosObjectValue.class,
                        registry.readLocalSlot(requirementId).orElseThrow());
        assertTrue(binding.isFrozen());
        assertEquals(1, binding.localSlotsSnapshot().size());
        assertSame(
                activation.context().readLocalSlot(executionSlot).orElseThrow(),
                binding.readLocalSlot("executionAsync").orElseThrow());
        assertFalse(binding.hasLocalSlot("executionInspectAsync"));
        assertFalse(binding.hasLocalSlot("resourceExecutionAsync"));
        assertFalse(binding.hasLocalSlot("resourceExecutionInspectAsync"));
        assertFalse(binding.hasLocalSlot("filesystem"));
        assertFalse(binding.hasLocalSlot("planLoader"));
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
