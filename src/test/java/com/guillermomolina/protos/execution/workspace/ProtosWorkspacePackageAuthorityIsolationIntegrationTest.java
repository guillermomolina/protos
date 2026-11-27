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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.guillermomolina.protos.execution.ProtosStandaloneHostedExecution.ResolverGrant;
import com.guillermomolina.protos.execution.ProtosStandaloneHostedExecution.EntropyGrant;
import com.guillermomolina.protos.execution.ProtosStandaloneHostedExecution.ClockGrant;
import com.guillermomolina.protos.execution.ProtosWorkspacePackageApplicationExecution.NetworkGrant;
import com.guillermomolina.protos.runtime.ProtosResolverCapabilityValue;
import com.guillermomolina.protos.runtime.ProtosArrayValue;
import com.guillermomolina.protos.runtime.ProtosEnvironmentValue;
import com.guillermomolina.protos.runtime.ProtosNetworkCapabilityValue;
import com.guillermomolina.protos.runtime.ProtosProcessRuntime;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ProtosWorkspacePackageAuthorityIsolationIntegrationTest {
    private static final Path CORE = Path.of("protos", "lib", "core");
    private static final Path STANDARD_LIBRARY = Path.of("protos", "lib");
    private static final Path TOOL_ROOT = Path.of("protos", "tools", "package");
    private static final Path WORKSPACE_CASE =
            Path.of(
                    "protos",
                    "tests",
                    "package-tool",
                    "execution-plan",
                    "cases",
                    "workspace");
    private static final Path APPLICATION =
            Path.of("src", "test", "resources", "workspace-package-authority-isolation");

    @TempDir Path temp;

    @Test
    void applicationDelegatesOnlyItsExactPrivateModuleToADependencyWorker() throws Exception {
        Path project = materializeProject();
        assumeSecureConfinement(project);
        Files.createDirectories(project.resolve("Private"));
        Files.createDirectories(project.resolve("Other"));
        Files.writeString(project.resolve("Private/module.protos"),
                "secret: 91\nthis.value: 17\n");
        Files.writeString(project.resolve("Other/module.protos"), "this.value: 23\n");
        Files.writeString(project.resolve("Main/module.protos"), """
                ref: import.resolve("self:Private")
                consumer: import.resolve("dep:local/Public")
                worker: Actor.spawn(consumer, "start", ref)
                worker.request("check").value()
                """);
        Files.writeString(project.resolve("libs/a/internal/Thing/module.protos"), """
                this.start: (reference) => {
                    target: import(reference)
                    deniedResolution: (specifier) => Error.handle(
                        () => { import.resolve(specifier); false }, (error) => true)
                    deniedImport: (specifier) => Error.handle(
                        () => { import(specifier); false }, (error) => true)
                    {
                        check: () => {
                            (target.value == 17) && !target.hasSlot("secret") &&
                                (target === import(reference)) &&
                                deniedImport("dep:rootdep/Private") &&
                                deniedResolution("dep:rootdep/Private") &&
                                deniedImport("dep:rootdep/Other") &&
                                deniedResolution("dep:rootdep/Other")
                        }
                    }
                }
                """);
        ProtosStandardLibraryModuleResolver standard = new ProtosStandardLibraryModuleResolver(STANDARD_LIBRARY);
        ProtosPackageExecutionPlan plan = ProtosWorkspacePackagePreflight.build(CORE, TOOL_ROOT, project, standard);
        ProtosExecutionOutcome outcome = ProtosWorkspacePackageApplicationExecution.execute(
                request(project, plan, standard, "Main"));
        assertEquals(ProtosExecutionOutcome.State.COMPLETED, outcome.state());
        assertSame(Boolean.TRUE, outcome.value());
    }

    @Test
    void detachedPlanCrossesFromTerminatedToolProcessIntoDistinctApplicationProcess()
            throws Exception {
        Path project = materializeProject();
        assumeSecureConfinement(project);

        ProtosStandardLibraryModuleResolver standard =
                new ProtosStandardLibraryModuleResolver(STANDARD_LIBRARY);
        AtomicReference<ProtosProcessRuntime> toolProcess = new AtomicReference<>();
        AtomicReference<ProtosProcessRuntime> applicationProcess = new AtomicReference<>();

        ProtosPackageExecutionPlan plan =
                ProtosWorkspacePackagePreflight.build(
                        CORE, TOOL_ROOT, project, standard, toolProcess::set);

        assertEquals("root", plan.root().packageId());
        assertEquals(2, plan.packages().size());

        ProtosExecutionOutcome outcome =
                ProtosWorkspacePackageApplicationExecution.execute(
                        request(project, plan, standard, "Main"),
                        applicationProcess::set);

        assertEquals(ProtosExecutionOutcome.State.COMPLETED, outcome.state());
        Long value = (Long) outcome.value();
        assertEquals(String.valueOf("73"), com.guillermomolina.protos.runtime.ProtosNumericValueSupport.integerDecimalText(value));

        ProtosProcessRuntime tool = toolProcess.get();
        ProtosProcessRuntime application = applicationProcess.get();
        assertNotNull(tool);
        assertNotNull(application);
        assertNotSame(tool, application);
        assertEquals(ProtosProcessRuntime.LifecycleState.TERMINATED, tool.lifecycleState());
        assertEquals(
                ProtosProcessRuntime.LifecycleState.TERMINATED,
                application.lifecycleState());
        assertTrue(tool.rootFilesystemForRuntime().isEmpty());
        assertTrue(application.rootFilesystemForRuntime().isEmpty());
    }

    @Test
    void packageToolProjectFilesystemDoesNotCrossDetachedBoundary()
            throws Exception {
        Path project = materializeProject();
        assumeSecureConfinement(project);

        ProtosStandardLibraryModuleResolver standard =
                new ProtosStandardLibraryModuleResolver(STANDARD_LIBRARY);
        ProtosPackageExecutionPlan plan =
                ProtosWorkspacePackagePreflight.build(
                        CORE, TOOL_ROOT, project, standard);

        ProtosExecutionOutcome outcome =
                ProtosWorkspacePackageApplicationExecution.execute(
                        request(project, plan, standard, "Leak"));

        assertEquals(ProtosExecutionOutcome.State.FAILED, outcome.state());
        assertNotNull(outcome.error());
    }

    @Test
    void workspaceRunDriverDefaultRouteKeepsPreflightAndApplicationNetworkLess()
            throws Exception {
        Path project = materializeProject();
        assumeSecureConfinement(project);

        ProtosStandardLibraryModuleResolver standard =
                new ProtosStandardLibraryModuleResolver(STANDARD_LIBRARY);
        assertEquals(
                ProtosExecutionOutcome.State.FAILED,
                ProtosWorkspaceRunDriver.execute(driverRequest(project, standard)).state());

        try (ProtosPolyglotRuntimeHost runtimeHost = ProtosPolyglotRuntimeHost.open()) {
            ProtosWorkspacePackagePreflight.build(CORE, TOOL_ROOT, project, standard, runtimeHost);
            assertFalse(runtimeHost.networkHostInitializedForTesting());

            ProtosExecutionOutcome outcome =
                    ProtosWorkspaceRunDriver.execute(
                            driverRequest(project, standard),
                            ProtosWorkspacePackageApplicationExecution.NetworkGrant.NONE,
                            runtimeHost);

            assertEquals(ProtosExecutionOutcome.State.FAILED, outcome.state());
            assertNotNull(outcome.error());
            assertFalse(runtimeHost.networkHostInitializedForTesting());
        }
    }

    @Test
    void workspaceRunDriverExplicitGrantReachesApplicationOnOwningRuntimeHost()
            throws Exception {
        Path project = materializeProject();
        assumeSecureConfinement(project);

        ProtosStandardLibraryModuleResolver standard =
                new ProtosStandardLibraryModuleResolver(STANDARD_LIBRARY);
        try (ProtosPolyglotRuntimeHost runtimeHost = ProtosPolyglotRuntimeHost.open()) {
            ProtosExecutionOutcome outcome =
                    ProtosWorkspaceRunDriver.execute(
                            driverRequest(project, standard),
                            ProtosWorkspacePackageApplicationExecution.NetworkGrant.HOST_NETWORK,
                            runtimeHost);

            assertEquals(ProtosExecutionOutcome.State.COMPLETED, outcome.state());
            ProtosArrayValue result = assertInstanceOf(ProtosArrayValue.class, outcome.value());
            ProtosNetworkCapabilityValue network =
                    assertInstanceOf(
                            ProtosNetworkCapabilityValue.class, result.indexedAt(0));
            assertSame(result.indexedAt(1), network.representedDelegationParent(null));
            assertTrue(runtimeHost.networkHostInitializedForTesting());
        }
    }

    @Test
    void resolverSelectionSkipsPreflightAndUsesApplicationPreludeAndHost() throws Exception {
        Path project = materializeProject();
        assumeSecureConfinement(project);
        Files.writeString(project.resolve("NetworkGrant/module.protos"), """
                Resolvers: import("std:network/resolvers")
                [resolver, Resolvers.Resolver, context.hasSlot("network")]
                """);
        ProtosStandardLibraryModuleResolver standard = new ProtosStandardLibraryModuleResolver(STANDARD_LIBRARY);
        ProtosResolverCapabilityValue previous = null;
        try (ProtosPolyglotRuntimeHost host = ProtosPolyglotRuntimeHost.open()) {
            AtomicReference<ProtosProcessRuntime> tool = new AtomicReference<>();
            ProtosWorkspacePackagePreflight.build(CORE, TOOL_ROOT, project, standard, tool::set, host);
            assertTrue(tool.get().rootResolverForRuntime().isEmpty());
            assertEquals(0, host.provisionedResolverCountForTesting());
            for (int run = 0; run < 2; run++) {
                ProtosExecutionOutcome outcome = ProtosWorkspaceRunDriver.execute(
                        driverRequest(project, standard), NetworkGrant.NONE, ResolverGrant.HOST_RESOLVER,
                        EntropyGrant.NONE, ClockGrant.NONE, host);
                assertEquals(ProtosExecutionOutcome.State.COMPLETED, outcome.state());
                ProtosArrayValue values = assertInstanceOf(ProtosArrayValue.class, outcome.value());
                ProtosResolverCapabilityValue capability = assertInstanceOf(
                        ProtosResolverCapabilityValue.class, values.indexedAt(0));
                assertSame(values.indexedAt(1), capability.representedDelegationParent(null));
                assertSame(Boolean.FALSE, values.indexedAt(2));
                assertNotSame(previous, capability);
                assertTrue(capability.retiredForRuntime());
                assertFalse(host.networkHostInitializedForTesting());
                assertEquals(1, host.provisionedResolverCountForTesting());
                previous = capability;
            }
            ProtosExecutionOutcome denied = ProtosWorkspaceRunDriver.execute(
                    driverRequest(project, standard), NetworkGrant.NONE, host);
            assertEquals(ProtosExecutionOutcome.State.FAILED, denied.state());
            assertFalse(host.networkHostInitializedForTesting());
        }
    }

    @Test
    void resolverIsRetiredWhenFilesystemProvisioningFailsBeforeProcessCreation() throws Exception {
        Path project = materializeProject();
        assumeSecureConfinement(project);
        ProtosStandardLibraryModuleResolver standard = new ProtosStandardLibraryModuleResolver(STANDARD_LIBRARY);
        ProtosPackageExecutionPlan plan = ProtosWorkspacePackagePreflight.build(CORE, TOOL_ROOT, project, standard);
        ProtosWorkspacePackageModuleResolver modules = new ProtosWorkspacePackageModuleResolver(project, plan, standard);
        ProtosWorkspacePackageApplicationExecution.ApplicationAuthority authority =
                new ProtosWorkspacePackageApplicationExecution.ApplicationAuthority(
                        List.of(), exactEnvironmentDomain(), List.of(), null, null, null,
                        null, null, null,
                        ProtosStandaloneHostedExecution.FilesystemGrant.directory(temp.resolve("missing")));
        try (ProtosPolyglotRuntimeHost host = ProtosPolyglotRuntimeHost.open()) {
            for (boolean service : new boolean[] {false, true}) {
                AtomicReference<ProtosProcessRuntime> process = new AtomicReference<>();
                assertThrows(java.io.IOException.class, () -> {
                    if (service) {
                        ProtosWorkspacePackageApplicationExecution.executeServiceEntry(
                                CORE, modules, modules.entryModule("Main"), authority,
                                NetworkGrant.NONE, ResolverGrant.HOST_RESOLVER, EntropyGrant.NONE,
                                ClockGrant.NONE, "service", process::set, host);
                    } else {
                        ProtosWorkspacePackageApplicationExecution.executeEntry(
                                CORE, modules, modules.entryModule("Main"), authority,
                                NetworkGrant.NONE, ResolverGrant.HOST_RESOLVER, EntropyGrant.NONE,
                                ClockGrant.NONE, false, "bootstrap failed", process::set, host);
                    }
                });
                assertEquals(null, process.get());
                // Provisioning prunes retired grants: only the new probe remains in host custody.
                com.guillermomolina.protos.runtime.ProtosPrelude prelude =
                        new ProtosCoreBootstrap().bootstrap(CORE, modules);
                ProtosResolverCapabilityValue probe = host.provisionHostResolver(prelude);
                assertEquals(1, host.provisionedResolverCountForTesting());
                probe.retireForRuntime();
                assertFalse(host.networkHostInitializedForTesting());
                assertEquals(0, host.activeProcessContextCountForTesting());
            }
        }
    }

    @Test
    void laterEntropyAndClockProvisioningFailuresRetireUndeliveredResolver() throws Exception {
        Path project = materializeProject();
        assumeSecureConfinement(project);
        ProtosStandardLibraryModuleResolver standard = new ProtosStandardLibraryModuleResolver(STANDARD_LIBRARY);
        ProtosPackageExecutionPlan plan = ProtosWorkspacePackagePreflight.build(CORE, TOOL_ROOT, project, standard);
        for (String failedPlane : List.of("entropyRetired", "clockRetired")) {
            try (ProtosPolyglotRuntimeHost host = ProtosPolyglotRuntimeHost.open()) {
                // Force the existing host's provisioning guard without adding a production seam.
                java.lang.reflect.Field retired = ProtosPolyglotRuntimeHost.class.getDeclaredField(failedPlane);
                retired.setAccessible(true);
                retired.setBoolean(host, true);
                AtomicReference<ProtosProcessRuntime> process = new AtomicReference<>();
                assertThrows(IllegalStateException.class, () ->
                        ProtosWorkspacePackageApplicationExecution.execute(
                                request(project, plan, standard, "Main"), process::set,
                                NetworkGrant.NONE, ResolverGrant.HOST_RESOLVER,
                                EntropyGrant.HOST_ENTROPY, ClockGrant.HOST_CLOCK, host));
                assertEquals(null, process.get());
                com.guillermomolina.protos.runtime.ProtosPrelude prelude =
                        new ProtosCoreBootstrap().bootstrap(CORE, standard);
                ProtosResolverCapabilityValue probe = host.provisionHostResolver(prelude);
                assertEquals(1, host.provisionedResolverCountForTesting());
                probe.retireForRuntime();
                assertEquals(0, host.activeProcessContextCountForTesting());
                assertFalse(host.networkHostInitializedForTesting());
            }
        }
    }

    @Test
    void failureAfterProcessCreationRevokesResolverThroughProcessTermination() throws Exception {
        Path project = materializeProject();
        assumeSecureConfinement(project);
        ProtosStandardLibraryModuleResolver standard = new ProtosStandardLibraryModuleResolver(STANDARD_LIBRARY);
        ProtosPackageExecutionPlan plan = ProtosWorkspacePackagePreflight.build(CORE, TOOL_ROOT, project, standard);
        ProtosWorkspacePackageModuleResolver modules = new ProtosWorkspacePackageModuleResolver(project, plan, standard);
        for (boolean service : new boolean[] {false, true}) {
            try (ProtosPolyglotRuntimeHost host = ProtosPolyglotRuntimeHost.open()) {
                AtomicReference<ProtosProcessRuntime> process = new AtomicReference<>();
                AtomicReference<ProtosResolverCapabilityValue> resolver = new AtomicReference<>();
                java.util.function.Consumer<ProtosProcessRuntime> failAfterBinding = owner -> {
                    process.set(owner);
                    resolver.set(owner.rootResolverForRuntime().orElseThrow());
                    assertFalse(resolver.get().retiredForRuntime());
                    throw new IllegalStateException("injected post-bootstrap failure");
                };
                assertThrows(java.io.IOException.class, () -> {
                    if (service) {
                        ProtosWorkspacePackageApplicationExecution.executeServiceEntry(
                                CORE, modules, modules.entryModule("Main"),
                                request(project, plan, standard, "Main").authority(),
                                NetworkGrant.NONE, ResolverGrant.HOST_RESOLVER, EntropyGrant.NONE,
                                ClockGrant.NONE, "service", failAfterBinding, host);
                    } else {
                        ProtosWorkspacePackageApplicationExecution.executeEntry(
                                CORE, modules, modules.entryModule("Main"),
                                request(project, plan, standard, "Main").authority(),
                                NetworkGrant.NONE, ResolverGrant.HOST_RESOLVER, EntropyGrant.NONE,
                                ClockGrant.NONE, true, "bootstrap failed", failAfterBinding, host);
                    }
                });
                assertEquals(ProtosProcessRuntime.LifecycleState.TERMINATED, process.get().lifecycleState());
                assertTrue(resolver.get().retiredForRuntime());
                assertEquals(0, host.activeProcessContextCountForTesting());
            }
        }
    }

    private static ProtosWorkspaceRunDriver.Request driverRequest(
            Path project, ProtosModuleResolver standard) {
        return new ProtosWorkspaceRunDriver.Request(
                CORE,
                TOOL_ROOT,
                project,
                standard,
                "NetworkGrant",
                List.of(),
                exactEnvironmentDomain(),
                List.of(),
                null,
                null,
                null,
                null,
                null,
                null);
    }

    private static ProtosWorkspacePackageApplicationExecution.Request request(
            Path project,
            ProtosPackageExecutionPlan plan,
            ProtosModuleResolver standard,
            String entry) {
        return new ProtosWorkspacePackageApplicationExecution.Request(
                CORE,
                project,
                plan,
                standard,
                entry,
                List.of(),
                exactEnvironmentDomain(),
                List.of(),
                null,
                null,
                null,
                null,
                null,
                null);
    }

    private Path materializeProject() throws Exception {
        Path project = temp.resolve("project");
        copyTree(WORKSPACE_CASE, project);
        overlay(APPLICATION.resolve("Main/module.protos"), project.resolve("Main/module.protos"));
        overlay(APPLICATION.resolve("Leak/module.protos"), project.resolve("Leak/module.protos"));
        overlay(
                APPLICATION.resolve("NetworkGrant/module.protos"),
                project.resolve("NetworkGrant/module.protos"));
        overlay(
                APPLICATION.resolve("libs/a/internal/Thing/module.protos"),
                project.resolve("libs/a/internal/Thing/module.protos"));
        return project;
    }

    private static void copyTree(Path source, Path target) throws Exception {
        try (Stream<Path> paths = Files.walk(source)) {
            for (Path current : paths.toList()) {
                Path relative = source.relativize(current);
                Path destination = target.resolve(relative.toString());
                if (Files.isDirectory(current)) {
                    Files.createDirectories(destination);
                } else {
                    Files.createDirectories(destination.getParent());
                    Files.copy(
                            current,
                            destination,
                            StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.COPY_ATTRIBUTES);
                }
            }
        }
    }

    private static void overlay(Path source, Path destination) throws Exception {
        Files.createDirectories(destination.getParent());
        Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
    }

    private static void assumeSecureConfinement(Path root) throws Exception {
        try (ProtosNioReadOnlyTreeFilesystemBackend backend =
                new ProtosNioReadOnlyTreeFilesystemBackend(root)) {
            assumeTrue(
                    backend.secureConfinementAvailable(),
                    "host provider has no SecureDirectoryStream");
        }
    }

    private static ProtosEnvironmentValue.NativeNameDomain exactEnvironmentDomain() {
        return new ProtosEnvironmentValue.NativeNameDomain() {
            @Override
            public boolean sameCapturedName(String left, String right) {
                return left.equals(right);
            }

            @Override
            public boolean isQueryRepresentable(String name) {
                return !name.contains("=") && name.indexOf('\0') < 0;
            }

            @Override
            public boolean matchesQuery(String captured, String query) {
                return captured.equals(query);
            }
        };
    }
}
