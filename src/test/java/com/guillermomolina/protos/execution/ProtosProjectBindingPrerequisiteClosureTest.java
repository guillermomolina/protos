/*
 * THE LICENSED WORK IS PROVIDED UNDER THE TERMS OF THE ADAPTIVE PUBLIC LICENSE
 * ("LICENSE") AS FIRST COMPLETED BY: Guillermo Adrián Molina. ANY USE, PUBLIC
 * DISPLAY, PUBLIC PERFORMANCE, REPRODUCTION OR DISTRIBUTION OF, OR PREPARATION OF
 * DERIVATIVE WORKS BASED ON, THE LICENSED WORK CONSTITUTES RECIPIENT'S ACCEPTANCE
 * OF THIS LICENSE AND ITS TERMS, WHETHER OR NOT SUCH RECIPIENT READS THE TERMS.
 * "LICENSED WORK" AND "RECIPIENT" ARE DEFINED IN THE LICENSE. A COPY OF THE
 * LICENSE IS LOCATED IN THE TEXT FILE ENTITLED "LICENSE.TXT" ACCOMPANYING THE
 * CONTENTS OF THIS FILE. IF A COPY OF THE LICENSE DOES NOT ACCOMPANY THIS FILE, A
 * COPY OF THE LICENSE MAY ALSO BE OBTAINED AT THE FOLLOWING WEB SITE:
 * https://github.com/guillermomolina/protos
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License for
 * the specific language governing rights and limitations under the License.
 */

package com.guillermomolina.protos.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.analysis.ProtosProjectBinding;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ProtosProjectBindingPrerequisiteClosureTest {
    private static final String RESOLUTION_HEX = "0123456789abcdef".repeat(4);

    @TempDir Path temporary;

    @Test
    void exactCandidateRootsRemainIsolatedAcrossIndependentProjects() throws Exception {
        Path left = createProject(temporary.resolve("left"), "left\n", List.of());
        Path right = createProject(temporary.resolve("right"), "right\n", List.of());
        ProtosProjectFileBindingProvider provider = new ProtosProjectFileBindingProvider();

        ProtosProjectBinding leftBinding = provider.acquire(left).orElseThrow();
        ProtosProjectBinding rightBinding = provider.acquire(right).orElseThrow();

        assertEquals(left.toRealPath(), leftBinding.projection().canonicalProjectRoot());
        assertEquals(right.toRealPath(), rightBinding.projection().canonicalProjectRoot());
        assertEquals(
                List.of(left.resolve("Main.protos").toRealPath()),
                leftBinding.sources().stream().map(ProtosProjectBinding.Source::source).toList());
        assertEquals(
                List.of(right.resolve("Main.protos").toRealPath()),
                rightBinding.sources().stream().map(ProtosProjectBinding.Source::source).toList());
        assertFalse(leftBinding.sources().get(0).source().startsWith(right.toRealPath()));
        assertFalse(rightBinding.sources().get(0).source().startsWith(left.toRealPath()));
    }

    @Test
    void duplicateCanonicalPhysicalMemberRootFailsClosedThroughProvider() throws Exception {
        Path root = Files.createDirectories(temporary.resolve("aliased"));
        Files.writeString(root.resolve("protos.toml"), "root metadata\n");
        Files.writeString(root.resolve("protos.lock"), "lock metadata\n");
        Files.writeString(root.resolve("Main.protos"), "true\n");
        Path members = Files.createDirectories(root.resolve("members"));
        Path physical = Files.createDirectory(members.resolve("a"));
        Files.writeString(physical.resolve("protos.toml"), "member metadata\n");
        Files.writeString(physical.resolve("Api.protos"), "true\n");
        try {
            Files.createSymbolicLink(members.resolve("b"), Path.of("a"));
        } catch (UnsupportedOperationException | IOException | SecurityException unavailable) {
            Assumptions.assumeTrue(false, "symbolic links unavailable: " + unavailable);
        }
        writeProject(
                root,
                List.of(
                        new Member("members/a", "member-a"),
                        new Member("members/b", "member-b")));

        ProtosProjectFileBindingProvider provider = new ProtosProjectFileBindingProvider();
        assertTrue(provider.acquire(root).isEmpty());
    }

    @Test
    void everyMechanicalFreshnessInputInvalidatesAcquisitionWhenChanged() throws Exception {
        ProtosProjectFileBindingProvider provider = new ProtosProjectFileBindingProvider();

        Path rootManifest = createProject(temporary.resolve("root-manifest"), "true\n", List.of());
        assertTrue(provider.acquire(rootManifest).isPresent());
        Files.writeString(rootManifest.resolve("protos.toml"), "changed root metadata\n");
        assertTrue(provider.acquire(rootManifest).isEmpty());

        Path memberManifest =
                createProject(
                        temporary.resolve("member-manifest"),
                        "true\n",
                        List.of(new Member("libs/member", "member")));
        assertTrue(provider.acquire(memberManifest).isPresent());
        Files.writeString(
                memberManifest.resolve("libs/member/protos.toml"),
                "changed member metadata\n");
        assertTrue(provider.acquire(memberManifest).isEmpty());

        Path lock = createProject(temporary.resolve("lock"), "true\n", List.of());
        assertTrue(provider.acquire(lock).isPresent());
        Files.writeString(lock.resolve("protos.lock"), "changed lock metadata\n");
        assertTrue(provider.acquire(lock).isEmpty());
    }

    @Test
    void staticProjectBindingAcquisitionClosureHasNoGuestOrToolExecutionDependency()
            throws Exception {
        Path repository = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        List<String> closure =
                List.of(
                        "src/main/java/com/guillermomolina/protos/analysis/ProtosProjectBinding.java",
                        "src/main/java/com/guillermomolina/protos/analysis/ProtosProjectBindingProjection.java",
                        "src/main/java/com/guillermomolina/protos/analysis/ProtosProjectBindingProvider.java",
                        "src/main/java/com/guillermomolina/protos/execution/ProtosProjectFileBindingProvider.java",
                        "src/main/java/com/guillermomolina/protos/execution/ProtosPackageExecutionPlan.java",
                        "src/main/java/com/guillermomolina/protos/execution/ProtosWorkspacePackageProjectIndex.java",
                        "src/main/java/com/guillermomolina/protos/execution/ProtosWorkspacePackageDirectoryIndex.java",
                        "src/main/java/com/guillermomolina/protos/execution/ProtosWorkspacePackageSourceLookup.java",
                        "src/main/java/com/guillermomolina/protos/execution/ProtosWorkspacePackageSourceInventory.java",
                        "src/main/java/com/guillermomolina/protos/execution/ProtosWorkspaceMemberLocationTraversal.java");
        List<String> forbidden =
                List.of(
                        "org.graalvm.polyglot",
                        "com.oracle.truffle",
                        "Context.newBuilder",
                        "ProtosLanguageContext",
                        "ProtosWorkspacePackagePreflight",
                        "ProtosBundledToolModuleResolver",
                        "ProcessBuilder",
                        "Runtime.getRuntime().exec");

        for (String relative : closure) {
            String source = Files.readString(repository.resolve(relative), StandardCharsets.UTF_8);
            for (String token : forbidden) {
                assertFalse(
                        source.contains(token),
                        relative + " must not reference static-boundary-forbidden token " + token);
            }
        }
    }

    private static Path createProject(Path root, String mainSource, List<Member> members)
            throws Exception {
        Files.createDirectories(root);
        Files.writeString(root.resolve("protos.toml"), "root metadata\n");
        Files.writeString(root.resolve("protos.lock"), "lock metadata\n");
        Files.writeString(root.resolve("Main.protos"), mainSource);
        for (Member member : members) {
            Path memberRoot = Files.createDirectories(root.resolve(member.location()));
            Files.writeString(memberRoot.resolve("protos.toml"), "member metadata\n");
            Files.writeString(memberRoot.resolve("Api.protos"), "true\n");
        }
        writeProject(root, members);
        return root;
    }

    private static void writeProject(Path root, List<Member> members) throws Exception {
        String metadata = metadataDigest(root, members);
        StringBuilder text = new StringBuilder();
        text.append("project-format 1\n");
        text.append("resolution-input protos-resolution-input-v1 sha256:")
                .append(RESOLUTION_HEX)
                .append('\n');
        text.append("metadata-content protos-project-metadata-v1 sha256:")
                .append(metadata)
                .append("\n\n");
        text.append("root workspace \"root\"\n");
        ArrayList<Member> ordered = new ArrayList<>(members);
        ordered.sort((left, right) -> left.location().compareTo(right.location()));
        for (Member member : ordered) {
            text.append("workspace-member \"")
                    .append(member.location())
                    .append("\" workspace \"")
                    .append(member.packageId())
                    .append("\"\n");
        }
        Files.writeString(root.resolve("protos.project"), text.toString(), StandardCharsets.UTF_8);
    }

    private static String metadataDigest(Path root, List<Member> members) throws Exception {
        MessageDigest digest = sha256();
        digest.update("protos-project-metadata-v1\n".getBytes(StandardCharsets.US_ASCII));
        append(digest, "protos.toml", Files.readAllBytes(root.resolve("protos.toml")));
        ArrayList<Member> ordered = new ArrayList<>(members);
        ordered.sort((left, right) -> left.location().compareTo(right.location()));
        for (Member member : ordered) {
            append(
                    digest,
                    member.location() + "/protos.toml",
                    Files.readAllBytes(root.resolve(member.location()).resolve("protos.toml")));
        }
        append(digest, "protos.lock", Files.readAllBytes(root.resolve("protos.lock")));
        return HexFormat.of().formatHex(digest.digest());
    }

    private static void append(MessageDigest digest, String relativePath, byte[] content) {
        byte[] path = relativePath.getBytes(StandardCharsets.UTF_8);
        digest.update(Integer.toString(path.length).getBytes(StandardCharsets.US_ASCII));
        digest.update((byte) ':');
        digest.update(path);
        digest.update((byte) '\n');
        digest.update(Integer.toString(content.length).getBytes(StandardCharsets.US_ASCII));
        digest.update((byte) ':');
        digest.update(content);
        digest.update((byte) '\n');
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private record Member(String location, String packageId) {}
}
