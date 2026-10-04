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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosProcessRuntime;
import com.guillermomolina.protos.runtime.ProtosStringValue;
import java.io.IOException;
import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ProtosExactExternalRequirementsPreflightTest {
    private static final Path CORE = Path.of("protos", "lib", "core");
    private static final Path STANDARD_LIBRARY = Path.of("protos", "lib");
    private static final Path TOOL_ROOT = Path.of("protos", "tools", "package");
    private static final Path CASES =
            Path.of("protos", "tests", "package-tool", "execution-plan", "cases");

    private static final String METHOD = "protos-package-tree-v1";
    private static final String ALGORITHM = "sha256";
    private static final String A_HEX =
            "5c2e8af458daf38c3e42ee3a68a928aa5ad2701166a59e6cabfb38645efe6f1a";
    private static final String B_HEX =
            "9f7a40f88c9c231748feb4ed242a4ebe9ec5f06cb732afd4e80706fd160a8ac0";
    private static final String C_HEX =
            "5876918795e50c16907ac40404b99e391fecb4302c02debbf246d4cfa1199222";

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

    // Two versions of external-a, plus registry and Git external-c sharing one ContentIdentity.
    // No external package tree exists anywhere: requirements never open external manifests.
    private static final String MIXED_LOCK =
            "lock-format 1\n"
                    + "resolver-version 1\n"
                    + "resolution-input protos-resolution-input-v1 "
                    + "sha256:6f2526d30fdc07e44c319bdb194eb0e7fafadcd61768eb36fc13917abf2fc2c5\n"
                    + "\n"
                    + "root workspace \"root\"\n"
                    + "registry-node registry \"external-a\" \"1.0.0\" "
                    + "locator \"pkg\" authority \"public\" content "
                    + METHOD + " " + ALGORITHM + ":" + A_HEX + "\n"
                    + "registry-node registry \"external-a\" \"2.0.0\" "
                    + "locator \"pkg\" authority \"public\" content "
                    + METHOD + " " + ALGORITHM + ":" + B_HEX + "\n"
                    + "registry-node registry \"external-c\" \"1.0.0\" "
                    + "locator \"pkg-c\" authority \"public\" content "
                    + METHOD + " " + ALGORITHM + ":" + C_HEX + "\n"
                    + "git-node git \"external-c\" \"abc123\" "
                    + "fetch \"https://example.invalid/c.git\" content "
                    + METHOD + " " + ALGORITHM + ":" + C_HEX + "\n"
                    + "dependency workspace \"root\" alias \"reg\" "
                    + "target registry \"external-a\" \"1.0.0\"\n"
                    + "dependency registry \"external-a\" \"1.0.0\" alias \"depgit\" "
                    + "target git \"external-c\" \"abc123\"\n"
                    + "dependency registry \"external-a\" \"1.0.0\" alias \"depnext\" "
                    + "target registry \"external-a\" \"2.0.0\"\n"
                    + "dependency registry \"external-a\" \"1.0.0\" alias \"depreg\" "
                    + "target registry \"external-c\" \"1.0.0\"\n";

    @TempDir Path temporaryRoot;

    @Test
    void workspaceOnlyProjectHasNoExternalRequirements() throws Exception {
        Path project = CASES.resolve("workspace");
        assumeSecureConfinement(project);

        assertEquals(List.of(), derive(project, ignored -> {}));
    }

    @Test
    void mixedLockedGraphYieldsEveryExactIdentityInLockOrderAndTerminatesProcess()
            throws Exception {
        Path project = createProject("mixed", ROOT_MANIFEST, MIXED_LOCK);
        AtomicReference<ProtosProcessRuntime> observed = new AtomicReference<>();

        List<ProtosExactExternalPackageIdentity> requirements = derive(project, observed::set);

        ProtosProcessRuntime process = observed.get();
        assertNotNull(process);
        assertEquals(ProtosProcessRuntime.LifecycleState.TERMINATED, process.lifecycleState());
        assertTrue(process.rootFilesystemForRuntime().isEmpty());

        ProtosExactExternalPackageIdentity a1 =
                new ProtosExactExternalPackageIdentity.Registry(
                        "external-a", "1.0.0", content(A_HEX));
        ProtosExactExternalPackageIdentity a2 =
                new ProtosExactExternalPackageIdentity.Registry(
                        "external-a", "2.0.0", content(B_HEX));
        ProtosExactExternalPackageIdentity cRegistry =
                new ProtosExactExternalPackageIdentity.Registry(
                        "external-c", "1.0.0", content(C_HEX));
        ProtosExactExternalPackageIdentity cGit =
                new ProtosExactExternalPackageIdentity.Git(
                        "external-c", "abc123", content(C_HEX));
        assertEquals(List.of(a1, a2, cRegistry, cGit), requirements);

        assertNotEquals(cRegistry, cGit);
        assertEquals(cRegistry.content(), cGit.content());
        assertEquals(cRegistry.packageId(), cGit.packageId());

        for (ProtosExactExternalPackageIdentity requirement : requirements) {
            assertHostInert(requirement);
        }
    }

    @Test
    void staleProjectLockFailsClosed() throws Exception {
        Path project = CASES.resolve("stale");
        assumeSecureConfinement(project);
        AtomicReference<ProtosProcessRuntime> observed = new AtomicReference<>();

        assertThrows(IOException.class, () -> derive(project, observed::set));

        assertEquals(
                ProtosProcessRuntime.LifecycleState.TERMINATED,
                observed.get().lifecycleState());
    }

    @Test
    void workspaceMembershipMismatchFailsClosed() throws Exception {
        Path project = CASES.resolve("member-mismatch");
        assumeSecureConfinement(project);

        assertThrows(IOException.class, () -> derive(project, ignored -> {}));
    }

    @Test
    void lockNotMatchingCurrentRootManifestFailsClosed() throws Exception {
        Path project =
                createProject(
                        "changed-root",
                        ROOT_MANIFEST.replace("id = \"root\"", "id = \"other-root\""),
                        MIXED_LOCK);

        assertThrows(IOException.class, () -> derive(project, ignored -> {}));
    }

    @Test
    void detachAcceptsOnlyTheExactRequirementShape() throws Exception {
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);

        assertEquals(
                List.of(
                        new ProtosExactExternalPackageIdentity.Git(
                                "external-c", "abc123", content(C_HEX))),
                ProtosExactExternalRequirementsPreflight.detach(
                        prelude.newFrozenArray(List.of(gitRequirement("abc123")))));

        assertDetachRejected(prelude, List.of(new ProtosStringValue("not-a-requirement")));
        assertDetachRejected(prelude, List.of(gitRequirement("")));
        assertDetachRejected(prelude, List.of(gitRequirement("abc123"), gitRequirement("abc123")));

        ProtosObjectValue foreignField = gitRequirement("abc123");
        foreignField.createLocalSlot("locator", new ProtosStringValue("/store/external-c"));
        assertDetachRejected(prelude, List.of(foreignField));

        ProtosObjectValue unknownKind = gitRequirement("abc123");
        ref(unknownKind).assignLocalSlot("kind", new ProtosStringValue("mirror"));
        assertDetachRejected(prelude, List.of(unknownKind));

        ProtosObjectValue workspaceKind = object();
        ProtosObjectValue workspaceRef = object();
        workspaceRef.createLocalSlot("kind", new ProtosStringValue("workspace"));
        workspaceRef.createLocalSlot("packageId", new ProtosStringValue("root"));
        workspaceKind.createLocalSlot("ref", workspaceRef);
        workspaceKind.createLocalSlot("content", contentValue());
        assertDetachRejected(prelude, List.of(workspaceKind));

        ProtosObjectValue badContent = gitRequirement("abc123");
        ((ProtosObjectValue) badContent.readLocalSlot("content").orElseThrow())
                .createLocalSlot("path", new ProtosStringValue("/tmp"));
        assertDetachRejected(prelude, List.of(badContent));

        assertThrows(
                IOException.class,
                () -> ProtosExactExternalRequirementsPreflight.detach(gitRequirement("abc123")));
    }

    private static void assertDetachRejected(ProtosPrelude prelude, List<Object> values) {
        assertThrows(
                IOException.class,
                () -> ProtosExactExternalRequirementsPreflight.detach(
                        prelude.newFrozenArray(values)));
    }

    private static ProtosObjectValue gitRequirement(String revision) {
        ProtosObjectValue ref = object();
        ref.createLocalSlot("kind", new ProtosStringValue("git"));
        ref.createLocalSlot("packageId", new ProtosStringValue("external-c"));
        ref.createLocalSlot("revision", new ProtosStringValue(revision));

        ProtosObjectValue requirement = object();
        requirement.createLocalSlot("ref", ref);
        requirement.createLocalSlot("content", contentValue());
        return requirement;
    }

    private static ProtosObjectValue contentValue() {
        ProtosObjectValue content = object();
        content.createLocalSlot("method", new ProtosStringValue(METHOD));
        content.createLocalSlot("algorithm", new ProtosStringValue(ALGORITHM));
        content.createLocalSlot("hex", new ProtosStringValue(C_HEX));
        return content;
    }

    private static ProtosObjectValue ref(ProtosObjectValue requirement) {
        return (ProtosObjectValue) requirement.readLocalSlot("ref").orElseThrow();
    }

    private static ProtosObjectValue object() {
        return new ProtosObjectValue(ProtosObjectValue.rootObject());
    }

    /** Host results may contain only Strings and nested host records built from Strings. */
    private static void assertHostInert(Object value) throws Exception {
        if (value instanceof String) {
            return;
        }
        if (!(value instanceof Record record)) {
            fail("external requirement retained a non-inert value: " + value.getClass());
            return;
        }
        for (RecordComponent component : record.getClass().getRecordComponents()) {
            assertHostInert(component.getAccessor().invoke(record));
        }
    }

    private static List<ProtosExactExternalPackageIdentity> derive(
            Path project, Consumer<ProtosProcessRuntime> observer) throws IOException {
        return ProtosExactExternalRequirementsPreflight.derive(
                CORE,
                TOOL_ROOT,
                project,
                new ProtosStandardLibraryModuleResolver(STANDARD_LIBRARY),
                observer);
    }

    private Path createProject(String name, String manifest, String lock) throws Exception {
        Path project = Files.createDirectories(temporaryRoot.resolve(name));
        Files.writeString(project.resolve("protos.toml"), manifest, StandardCharsets.UTF_8);
        Files.writeString(project.resolve("protos.lock"), lock, StandardCharsets.UTF_8);
        assumeSecureConfinement(project);
        return project;
    }

    private static void assumeSecureConfinement(Path root) throws Exception {
        try (ProtosNioReadOnlyTreeFilesystemBackend backend =
                new ProtosNioReadOnlyTreeFilesystemBackend(root)) {
            assumeTrue(
                    backend.secureConfinementAvailable(),
                    "host provider has no SecureDirectoryStream");
        }
    }

    private static ProtosPackageContentIdentity content(String hex) {
        return new ProtosPackageContentIdentity(METHOD, ALGORITHM, hex);
    }
}
