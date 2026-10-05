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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.guillermomolina.protos.runtime.ProtosArrayValue;
import com.guillermomolina.protos.runtime.ProtosByteIoFlow;
import com.guillermomolina.protos.runtime.ProtosEnvironmentValue;
import com.guillermomolina.protos.runtime.ProtosIntegerValue;
import com.guillermomolina.protos.runtime.ProtosNetworkCapabilityValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosProcessRuntime;
import com.guillermomolina.protos.runtime.ProtosProcessStandardStreamBinding;
import com.guillermomolina.protos.runtime.ProtosStringValue;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Integrated PLAT048 B′ composition: exact requirements, host-supplied exact materialization
 * provider, F2E2 verification, F2E3 planning, F2E4 detach/reconciliation and one mixed application
 * Process, plus the orchestrator's custody ownership on every failure before reconciliation.
 */
final class ProtosPackageRunDriverTest {
    private static final Path CORE = Path.of("protos", "lib", "core");
    private static final Path STANDARD_LIBRARY = Path.of("protos", "lib");
    private static final Path TOOL_ROOT = Path.of("protos", "tools", "package");
    private static final Path WORKSPACE_CASE =
            Path.of("protos", "tests", "package-tool", "execution-plan", "cases", "workspace");

    private static final String METHOD = "protos-package-tree-v1";
    private static final String ALGORITHM = "sha256";

    // Same root manifest (and therefore resolution-input header) as the f2e3b-transitive case.
    private static final String ROOT_MANIFEST =
            "manifest-version = 1\n"
                    + "\n"
                    + "[package]\n"
                    + "id = \"root\"\n"
                    + "version = \"1.0.0\"\n"
                    + "\n"
                    + "[dependencies.reg]\n"
                    + "authority = \"public\"\n"
                    + "package = \"pkg\"\n"
                    + "version = \"1.0.0\"\n";

    private static final String ROOT_MAIN =
            "writer: TextWriter(process.stdout(), process.stdoutEncoding())\n"
                    + "writer.writeLine(process.args().at(0)).value()\n"
                    + "A: import(\"dep:reg/Api\")\n"
                    + "A.total\n";

    private static final String A1_MANIFEST =
            "manifest-version = 1\n"
                    + "\n"
                    + "[package]\n"
                    + "id = \"external-a\"\n"
                    + "version = \"1.0.0\"\n"
                    + "\n"
                    + "[exports]\n"
                    + "Api = \"Api\"\n"
                    + "\n"
                    + "[dependencies.depgit]\n"
                    + "git = \"https://example.invalid/c.git\"\n"
                    + "rev = \"abc123\"\n"
                    + "\n"
                    + "[dependencies.depnext]\n"
                    + "authority = \"public\"\n"
                    + "package = \"pkg\"\n"
                    + "version = \"^2.0.0\"\n"
                    + "\n"
                    + "[dependencies.depreg]\n"
                    + "authority = \"public\"\n"
                    + "package = \"pkg-c\"\n"
                    + "version = \"1.0.0\"\n";

    private static final String A1_API =
            "Next: import(\"dep:depnext/Api\")\n"
                    + "GitC: import(\"dep:depgit/Api\")\n"
                    + "RegC: import(\"dep:depreg/Api\")\n"
                    + "total: 1000 + Next.value + GitC.value + RegC.value\n";

    private static final String A2_MANIFEST =
            "manifest-version = 1\n"
                    + "\n"
                    + "[package]\n"
                    + "id = \"external-a\"\n"
                    + "version = \"2.0.0\"\n"
                    + "\n"
                    + "[exports]\n"
                    + "Api = \"Api\"\n";

    // One tree serves both the registry and the Git external-c identity.
    private static final String C_MANIFEST =
            "manifest-version = 1\n"
                    + "\n"
                    + "[package]\n"
                    + "id = \"external-c\"\n"
                    + "version = \"1.0.0\"\n"
                    + "\n"
                    + "[exports]\n"
                    + "Api = \"Api\"\n";

    @TempDir static Path templates;

    private static Path a1Template;
    private static Path a2Template;
    private static Path cTemplate;
    private static ProtosExactExternalPackageIdentity a1;
    private static ProtosExactExternalPackageIdentity a2;
    private static ProtosExactExternalPackageIdentity cRegistry;
    private static ProtosExactExternalPackageIdentity cGit;

    @TempDir Path temporaryRoot;

    @BeforeAll
    static void digestExternalTemplates() throws Exception {
        a1Template = tree(templates.resolve("a1"), A1_MANIFEST, A1_API);
        a2Template = tree(templates.resolve("a2"), A2_MANIFEST, "value: 20\n");
        cTemplate = tree(templates.resolve("c"), C_MANIFEST, "value: 3\n");
        assumeSecureConfinement(templates);

        ProtosPackageContentIdentity a1Content = digest(a1Template);
        ProtosPackageContentIdentity a2Content = digest(a2Template);
        ProtosPackageContentIdentity cContent = digest(cTemplate);
        a1 = new ProtosExactExternalPackageIdentity.Registry("external-a", "1.0.0", a1Content);
        a2 = new ProtosExactExternalPackageIdentity.Registry("external-a", "2.0.0", a2Content);
        cRegistry =
                new ProtosExactExternalPackageIdentity.Registry("external-c", "1.0.0", cContent);
        cGit = new ProtosExactExternalPackageIdentity.Git("external-c", "abc123", cContent);
    }

    @Test
    void workspaceOnlyProjectUsesGenerationOneRouteWithoutConsultingProvider() throws Exception {
        Path project = temporaryRoot.resolve("workspace");
        copyTree(WORKSPACE_CASE, project);
        Files.writeString(
                project.resolve("Main.protos"),
                "writer: TextWriter(process.stdout(), process.stdoutEncoding())\n"
                        + "writer.writeLine(process.args().at(0)).value()\n"
                        + "42\n",
                StandardCharsets.UTF_8);
        assumeSecureConfinement(project);
        RecordingProvider provider = new RecordingProvider(Map.of());
        CapturingWritableBackend stdout = new CapturingWritableBackend();

        ProtosExecutionOutcome outcome =
                ProtosPackageRunDriver.execute(
                        request(project, stdout),
                        provider,
                        generationOneOnlyStages());

        assertEquals(ProtosExecutionOutcome.State.COMPLETED, outcome.state());
        assertEquals(BigInteger.valueOf(42), ((ProtosIntegerValue) outcome.value()).value());
        assertEquals("argument-value\n", stdout.utf8());
        assertEquals(List.of(), provider.calls);
    }

    @Test
    void mixedApplicationRunsOverExactlySelectedVerifiedCapturesAndClosesScopeAfterTermination()
            throws Exception {
        Path project = project("success");
        RecordingProvider provider = new RecordingProvider(materializations("success"));
        CapturingWritableBackend stdout = new CapturingWritableBackend();
        RecordingStages stages =
                new RecordingStages() {
                    @Override
                    public ProtosCapturedFilesystemCustody verify(
                            ProtosWorkspaceRunDriver.Request request,
                            ProtosExactExternalPackageIdentity identity,
                            Path selectedRoot)
                            throws IOException {
                        ProtosCapturedFilesystemCustody custody =
                                super.verify(request, identity, selectedRoot);
                        // The application must read only the verified capture.
                        deleteTree(selectedRoot);
                        return custody;
                    }

                    @Override
                    public void applicationProcessHosted(ProtosProcessRuntime process) {
                        assertEquals(
                                ProtosProcessRuntime.LifecycleState.RUNNING,
                                process.lifecycleState());
                        assertTrue(scope.contains(a1));
                        super.applicationProcessHosted(process);
                    }
                };

        ProtosExecutionOutcome outcome =
                ProtosPackageRunDriver.execute(request(project, stdout), provider, stages);

        assertEquals(ProtosExecutionOutcome.State.COMPLETED, outcome.state());
        assertEquals(BigInteger.valueOf(1026), ((ProtosIntegerValue) outcome.value()).value());
        assertEquals("argument-value\n", stdout.utf8());

        // One complete exact identity per lookup, in requirement order, never deduplicated by
        // PackageId or by ContentIdentity.
        assertEquals(List.of(a1, a2, cRegistry, cGit), provider.calls);
        assertEquals(4, new HashSet<>(provider.calls).size());
        assertEquals(a1.packageId(), a2.packageId());
        assertNotEquals(a1, a2);
        assertEquals(cRegistry.content(), cGit.content());
        assertNotEquals(cRegistry, cGit);
        assertEquals(4, stages.verifyAttempts);

        assertEquals(1, stages.hosted.size());
        assertEquals(
                ProtosProcessRuntime.LifecycleState.TERMINATED,
                stages.hosted.get(0).lifecycleState());
        assertTrue(stages.hosted.get(0).rootFilesystemForRuntime().isEmpty());
        assertThrows(IllegalStateException.class, () -> stages.scope.contains(a1));
        assertAllClosed(stages.verified, 4);
    }

    @Test
    void workspaceOnlyDefaultPublicRouteKeepsApplicationNetworkLess() throws Exception {
        Path project = workspaceProject("workspace-default", "[network, Network]\n");
        RecordingProvider provider = new RecordingProvider(Map.of());

        ProtosExecutionOutcome outcome =
                ProtosPackageRunDriver.execute(request(project, null), provider);

        assertEquals(ProtosExecutionOutcome.State.FAILED, outcome.state());
        assertEquals(List.of(), provider.calls);
    }

    @Test
    void workspaceOnlyExplicitGrantSurvivesGenerationOneDelegationWithoutConsultingProvider()
            throws Exception {
        Path project = workspaceProject("workspace-network", "[network, Network]\n");
        RecordingProvider provider = new RecordingProvider(Map.of());

        ProtosExecutionOutcome outcome =
                ProtosPackageRunDriver.execute(
                        request(project, null),
                        provider,
                        ProtosWorkspacePackageApplicationExecution.NetworkGrant.HOST_NETWORK,
                        generationOneOnlyStages());

        assertEquals(ProtosExecutionOutcome.State.COMPLETED, outcome.state());
        assertNetworkOfThisApplication(outcome);
        assertEquals(List.of(), provider.calls);
    }

    @Test
    void mixedApplicationExplicitGrantIsProvisionedFromTheExactApplicationPrelude()
            throws Exception {
        Path project = project("mixed-network");
        Files.writeString(
                project.resolve("Main.protos"),
                "A: import(\"dep:reg/Api\")\n[network, Network, A.total]\n",
                StandardCharsets.UTF_8);
        RecordingProvider provider = new RecordingProvider(materializations("mixed-network"));
        RecordingStages stages = new RecordingStages();

        ProtosExecutionOutcome outcome =
                ProtosPackageRunDriver.execute(
                        request(project, null),
                        provider,
                        ProtosWorkspacePackageApplicationExecution.NetworkGrant.HOST_NETWORK,
                        stages);

        assertEquals(ProtosExecutionOutcome.State.COMPLETED, outcome.state());
        ProtosArrayValue result = assertNetworkOfThisApplication(outcome);
        assertEquals(
                BigInteger.valueOf(1026),
                ((ProtosIntegerValue) result.indexedAtForRuntime(2)).value());
        assertMixedLifecycleUnchanged(provider, stages);
    }

    @Test
    void mixedApplicationExplicitNoneKeepsNetworkSlotAbsent() throws Exception {
        Path project = project("mixed-none");
        Files.writeString(
                project.resolve("Main.protos"),
                "A: import(\"dep:reg/Api\")\nA.total\nnetwork\n",
                StandardCharsets.UTF_8);
        RecordingProvider provider = new RecordingProvider(materializations("mixed-none"));
        RecordingStages stages = new RecordingStages();

        ProtosExecutionOutcome outcome =
                ProtosPackageRunDriver.execute(
                        request(project, null),
                        provider,
                        ProtosWorkspacePackageApplicationExecution.NetworkGrant.NONE,
                        stages);

        assertEquals(ProtosExecutionOutcome.State.FAILED, outcome.state());
        assertMixedLifecycleUnchanged(provider, stages);
    }

    @Test
    void providerMissClosesPriorVerifiedCustodyAndNeverStartsApplication() throws Exception {
        Path project = project("miss");
        Map<ProtosExactExternalPackageIdentity, Path> materializations =
                new HashMap<>(materializations("miss"));
        materializations.remove(a2);
        RecordingProvider provider = new RecordingProvider(materializations);
        RecordingStages stages = new RecordingStages();

        assertThrows(
                IOException.class,
                () -> ProtosPackageRunDriver.execute(request(project, null), provider, stages));

        assertEquals(List.of(a1, a2), provider.calls);
        assertEquals(1, stages.verifyAttempts);
        assertAllClosed(stages.verified, 1);
        assertTrue(stages.hosted.isEmpty());
        assertEquals(0, stages.planCalls);
    }

    @Test
    void wrongMaterializationFailsNthVerificationAndClosesPriorCustody() throws Exception {
        Path project = project("wrong");
        Map<ProtosExactExternalPackageIdentity, Path> materializations =
                new HashMap<>(materializations("wrong"));
        // A present tree whose ContentIdentity is not a2's.
        materializations.put(a2, materializations.get(cRegistry));
        RecordingProvider provider = new RecordingProvider(materializations);
        RecordingStages stages = new RecordingStages();

        assertThrows(
                IOException.class,
                () -> ProtosPackageRunDriver.execute(request(project, null), provider, stages));

        assertEquals(List.of(a1, a2), provider.calls);
        assertEquals(2, stages.verifyAttempts);
        assertAllClosed(stages.verified, 1);
        assertTrue(stages.hosted.isEmpty());
        assertEquals(0, stages.planCalls);
    }

    @Test
    void planningFailureClosesEveryVerifiedCustody() throws Exception {
        Path project = project("planning");
        RecordingProvider provider = new RecordingProvider(materializations("planning"));
        RecordingStages stages =
                new RecordingStages() {
                    @Override
                    public Object plan(
                            ProtosWorkspaceRunDriver.Request request,
                            List<ProtosExternalPackagePlanningPreflight.VerifiedExternalPackage>
                                    inputs)
                            throws IOException {
                        planCalls++;
                        assertOpen(verified);
                        throw new IOException("forced planning failure");
                    }
                };

        IOException failure =
                assertThrows(
                        IOException.class,
                        () ->
                                ProtosPackageRunDriver.execute(
                                        request(project, null), provider, stages));

        assertEquals("forced planning failure", failure.getMessage());
        assertEquals(1, stages.planCalls);
        assertAllClosed(stages.verified, 4);
        assertTrue(stages.hosted.isEmpty());
    }

    @Test
    void failedReconciliationTransfersNoOwnershipAndEveryCustodyCloses() throws Exception {
        Path project = project("reconcile");
        RecordingProvider provider = new RecordingProvider(materializations("reconcile"));
        RecordingStages stages =
                new RecordingStages() {
                    @Override
                    public ProtosExternalPackageResourceScope reconcile(
                            ProtosPackageExecutionPlanV2 plan,
                            List<ProtosExternalPackagePlanningPreflight.VerifiedExternalPackage>
                                    inputs)
                            throws IOException {
                        // A real reconciliation failure: one plan node has no verified custody.
                        return super.reconcile(plan, inputs.subList(0, inputs.size() - 1));
                    }
                };

        assertThrows(
                IOException.class,
                () -> ProtosPackageRunDriver.execute(request(project, null), provider, stages));

        assertEquals(1, stages.planCalls);
        assertAllClosed(stages.verified, 4);
        assertTrue(stages.hosted.isEmpty());
    }

    private Path project(String name) throws Exception {
        ProtosPackageContentIdentity a1Content = a1.content();
        ProtosPackageContentIdentity a2Content = a2.content();
        ProtosPackageContentIdentity cContent = cRegistry.content();
        String lock =
                "lock-format 1\n"
                        + "resolver-version 1\n"
                        + "resolution-input protos-resolution-input-v1 "
                        + "sha256:"
                        + "6f2526d30fdc07e44c319bdb194eb0e7fafadcd61768eb36fc13917abf2fc2c5\n"
                        + "\n"
                        + "root workspace \"root\"\n"
                        + "registry-node registry \"external-a\" \"1.0.0\" "
                        + "locator \"pkg\" authority \"public\" content "
                        + lockContent(a1Content) + "\n"
                        + "registry-node registry \"external-a\" \"2.0.0\" "
                        + "locator \"pkg\" authority \"public\" content "
                        + lockContent(a2Content) + "\n"
                        + "registry-node registry \"external-c\" \"1.0.0\" "
                        + "locator \"pkg-c\" authority \"public\" content "
                        + lockContent(cContent) + "\n"
                        + "git-node git \"external-c\" \"abc123\" "
                        + "fetch \"https://example.invalid/c.git\" content "
                        + lockContent(cContent) + "\n"
                        + "dependency workspace \"root\" alias \"reg\" "
                        + "target registry \"external-a\" \"1.0.0\"\n"
                        + "dependency registry \"external-a\" \"1.0.0\" alias \"depgit\" "
                        + "target git \"external-c\" \"abc123\"\n"
                        + "dependency registry \"external-a\" \"1.0.0\" alias \"depnext\" "
                        + "target registry \"external-a\" \"2.0.0\"\n"
                        + "dependency registry \"external-a\" \"1.0.0\" alias \"depreg\" "
                        + "target registry \"external-c\" \"1.0.0\"\n";

        Path project = Files.createDirectories(temporaryRoot.resolve(name).resolve("project"));
        Files.writeString(project.resolve("protos.toml"), ROOT_MANIFEST, StandardCharsets.UTF_8);
        Files.writeString(project.resolve("protos.lock"), lock, StandardCharsets.UTF_8);
        Files.writeString(project.resolve("Main.protos"), ROOT_MAIN, StandardCharsets.UTF_8);
        assumeSecureConfinement(project);
        return project;
    }

    private Path workspaceProject(String name, String main) throws Exception {
        Path project = temporaryRoot.resolve(name);
        copyTree(WORKSPACE_CASE, project);
        Files.writeString(project.resolve("Main.protos"), main, StandardCharsets.UTF_8);
        assumeSecureConfinement(project);
        return project;
    }

    /**
     * Asserts {@code [network, Network, ...]}: a real Network capability delegating to the
     * {@code Network} prototype of the very Prelude that executed the application.
     */
    private static ProtosArrayValue assertNetworkOfThisApplication(ProtosExecutionOutcome outcome) {
        ProtosArrayValue result = assertInstanceOf(ProtosArrayValue.class, outcome.value());
        ProtosNetworkCapabilityValue network =
                assertInstanceOf(ProtosNetworkCapabilityValue.class, result.indexedAtForRuntime(0));
        assertSame(result.indexedAtForRuntime(1), network.representedDelegationParent(null));
        return result;
    }

    /** The mixed-route selection, single Process and scope lifetime, whatever the Network grant. */
    private static void assertMixedLifecycleUnchanged(
            RecordingProvider provider, RecordingStages stages) {
        assertEquals(List.of(a1, a2, cRegistry, cGit), provider.calls);
        assertEquals(4, stages.verifyAttempts);
        assertEquals(1, stages.planCalls);
        assertEquals(1, stages.hosted.size());
        assertEquals(
                ProtosProcessRuntime.LifecycleState.TERMINATED,
                stages.hosted.get(0).lifecycleState());
        assertThrows(IllegalStateException.class, () -> stages.scope.contains(a1));
        assertAllClosed(stages.verified, 4);
    }

    /** Stages that fail if the zero-external-requirements route reaches any F2E2-F2E4 step. */
    private static ProtosPackageRunDriver.Stages generationOneOnlyStages() {
        return new ProtosPackageRunDriver.Stages() {
            @Override
            public ProtosCapturedFilesystemCustody verify(
                    ProtosWorkspaceRunDriver.Request request,
                    ProtosExactExternalPackageIdentity identity,
                    Path selectedRoot) {
                return fail("workspace-only run must not capture externals");
            }

            @Override
            public Object plan(
                    ProtosWorkspaceRunDriver.Request request,
                    List<ProtosExternalPackagePlanningPreflight.VerifiedExternalPackage>
                            verified) {
                return fail("workspace-only run must not build a V2 plan");
            }

            @Override
            public ProtosPackageExecutionPlanV2 detach(Object rawPlan, Path projectRoot) {
                return fail("workspace-only run must not detach a V2 plan");
            }

            @Override
            public ProtosExternalPackageResourceScope reconcile(
                    ProtosPackageExecutionPlanV2 plan,
                    List<ProtosExternalPackagePlanningPreflight.VerifiedExternalPackage>
                            verified) {
                return fail("workspace-only run must not create a resource scope");
            }

            @Override
            public void applicationProcessHosted(ProtosProcessRuntime process) {
                fail("workspace-only run must not use the mixed application");
            }
        };
    }

    /** Fresh per-test copies of the digested templates, one selected root per exact identity. */
    private Map<ProtosExactExternalPackageIdentity, Path> materializations(String name)
            throws Exception {
        Path store = temporaryRoot.resolve(name).resolve("materializations");
        Map<ProtosExactExternalPackageIdentity, Path> roots = new HashMap<>();
        roots.put(a1, copyTree(a1Template, store.resolve("a1")));
        roots.put(a2, copyTree(a2Template, store.resolve("a2")));
        roots.put(cRegistry, copyTree(cTemplate, store.resolve("c-registry")));
        roots.put(cGit, copyTree(cTemplate, store.resolve("c-git")));
        return roots;
    }

    private static String lockContent(ProtosPackageContentIdentity content) {
        return content.method() + " " + content.algorithm() + ":" + content.hex();
    }

    private static ProtosWorkspaceRunDriver.Request request(
            Path project, ProtosProcessStandardStreamBinding.WritableBackend stdout) {
        return new ProtosWorkspaceRunDriver.Request(
                CORE,
                TOOL_ROOT,
                project,
                new ProtosStandardLibraryModuleResolver(STANDARD_LIBRARY),
                "Main",
                List.of("argument-value"),
                exactEnvironmentDomain(),
                List.<ProtosEnvironmentValue.NativeEntry>of(),
                null,
                stdout,
                null,
                null,
                stdout == null ? null : "UTF8",
                null);
    }

    /** Computes the bundled ContentIdentity of {@code root} once, through a fresh capture. */
    private static ProtosPackageContentIdentity digest(Path root) throws Exception {
        ProtosBundledToolModuleResolver resolver =
                new ProtosBundledToolModuleResolver(
                        "package",
                        TOOL_ROOT,
                        TOOL_ROOT.resolveSibling("shared"),
                        new ProtosStandardLibraryModuleResolver(STANDARD_LIBRARY));
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE, resolver);
        try (ProtosCapturedFilesystemCustody custody =
                        ProtosCapturedFilesystemCustody.captureSelectedRoot(root);
                ProtosHostedExecutionTestFixture hosted =
                        ProtosHostedExecutionTestFixture.open(prelude)) {
            hosted.activation()
                    .context()
                    .createLocalSlot(
                            "capturedFilesystem", custody.materialize(hosted.activation()));
            ProtosExecutionOutcome outcome =
                    hosted.execute(
                            "<package-run-driver-test-digest>",
                            "Identity: import(\"self:ContentIdentity\")\n"
                                    + "Identity.digest(capturedFilesystem).hex\n");
            assertEquals(ProtosExecutionOutcome.State.COMPLETED, outcome.state());
            return new ProtosPackageContentIdentity(
                    METHOD, ALGORITHM, ((ProtosStringValue) outcome.value()).value());
        }
    }

    private static void assertOpen(List<ProtosCapturedFilesystemCustody> custodies)
            throws IOException {
        for (ProtosCapturedFilesystemCustody custody : custodies) {
            custody.readResource(ProtosPackageResourceName.parse("protos.toml"));
        }
    }

    private static void assertAllClosed(
            List<ProtosCapturedFilesystemCustody> custodies, int expectedCount) {
        assertEquals(expectedCount, custodies.size());
        for (ProtosCapturedFilesystemCustody custody : custodies) {
            assertThrows(
                    IllegalStateException.class,
                    () -> custody.readResource(ProtosPackageResourceName.parse("protos.toml")));
        }
    }

    private static Path tree(Path root, String manifest, String api) throws Exception {
        Files.createDirectories(root);
        Files.writeString(root.resolve("protos.toml"), manifest, StandardCharsets.UTF_8);
        Files.writeString(root.resolve("Api.protos"), api, StandardCharsets.UTF_8);
        return root;
    }

    private static Path copyTree(Path source, Path target) throws IOException {
        try (Stream<Path> paths = Files.walk(source)) {
            for (Path current : paths.toList()) {
                Path destination = target.resolve(source.relativize(current).toString());
                if (Files.isDirectory(current)) {
                    Files.createDirectories(destination);
                } else {
                    Files.createDirectories(destination.getParent());
                    Files.copy(current, destination);
                }
            }
        }
        return target;
    }

    private static void deleteTree(Path root) throws IOException {
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path current : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(current);
            }
        }
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

    /** Exact provider over an explicit identity map; records every lookup, never falls back. */
    private static final class RecordingProvider
            implements ProtosExactPackageMaterializationProvider {
        private final Map<ProtosExactExternalPackageIdentity, Path> roots;
        private final List<ProtosExactExternalPackageIdentity> calls = new ArrayList<>();

        private RecordingProvider(Map<ProtosExactExternalPackageIdentity, Path> roots) {
            this.roots = Map.copyOf(roots);
        }

        @Override
        public Path select(ProtosExactExternalPackageIdentity identity) throws IOException {
            calls.add(identity);
            Path root = roots.get(identity);
            if (root == null) {
                throw new IOException("no exact materialization");
            }
            return root;
        }
    }

    /** Production stages that record verified custodies, the reconciled scope and the Process. */
    private static class RecordingStages implements ProtosPackageRunDriver.Stages {
        final List<ProtosCapturedFilesystemCustody> verified = new ArrayList<>();
        final List<ProtosProcessRuntime> hosted = new ArrayList<>();
        int verifyAttempts;
        int planCalls;
        ProtosExternalPackageResourceScope scope;

        @Override
        public ProtosCapturedFilesystemCustody verify(
                ProtosWorkspaceRunDriver.Request request,
                ProtosExactExternalPackageIdentity identity,
                Path selectedRoot)
                throws IOException {
            verifyAttempts++;
            ProtosCapturedFilesystemCustody custody =
                    ProtosPackageRunDriver.Stages.super.verify(request, identity, selectedRoot);
            verified.add(custody);
            return custody;
        }

        @Override
        public Object plan(
                ProtosWorkspaceRunDriver.Request request,
                List<ProtosExternalPackagePlanningPreflight.VerifiedExternalPackage> inputs)
                throws IOException {
            planCalls++;
            return ProtosPackageRunDriver.Stages.super.plan(request, inputs);
        }

        @Override
        public ProtosExternalPackageResourceScope reconcile(
                ProtosPackageExecutionPlanV2 plan,
                List<ProtosExternalPackagePlanningPreflight.VerifiedExternalPackage> inputs)
                throws IOException {
            scope = ProtosPackageRunDriver.Stages.super.reconcile(plan, inputs);
            return scope;
        }

        @Override
        public void applicationProcessHosted(ProtosProcessRuntime process) {
            hosted.add(process);
        }
    }

    private static final class CapturingWritableBackend
            implements ProtosProcessStandardStreamBinding.WritableBackend {
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        @Override
        public ProtosByteIoFlow.Cancellation write(
                byte[] contribution, ProtosByteIoFlow.WriteCompletion completion) {
            bytes.write(contribution, 0, contribution.length);
            completion.succeeded();
            return () -> {};
        }

        String utf8() {
            return bytes.toString(StandardCharsets.UTF_8);
        }
    }
}
