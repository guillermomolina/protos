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
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ProtosProjectFileBindingProviderTest {
    private static final String RESOLUTION_HEX = "0123456789abcdef".repeat(4);

    @TempDir Path temporary;

    @Test
    void validExactCandidateProducesBindingAndCurrentBoundedSourceInventory() throws Exception {
        Path member = Files.createDirectories(temporary.resolve("libs/member"));
        Files.writeString(temporary.resolve("protos.toml"), "root metadata\n");
        Files.writeString(member.resolve("protos.toml"), "member metadata\n");
        Files.writeString(temporary.resolve("protos.lock"), "lock metadata\n");
        Path rootSource = Files.writeString(temporary.resolve("Main.protos"), "true\n");
        Path memberSource = Files.writeString(member.resolve("Api.protos"), "true\n");
        writeProject(temporary, List.of(new Member("libs/member", "member")));

        ProtosProjectFileBindingProvider provider = new ProtosProjectFileBindingProvider();
        Optional<ProtosProjectBinding> acquired = provider.acquire(temporary);

        assertTrue(acquired.isPresent());
        ProtosProjectBinding binding = acquired.orElseThrow();
        assertEquals(temporary.toRealPath(), binding.projection().canonicalProjectRoot());
        assertEquals("root", binding.projection().rootPackageId());
        assertEquals(2, binding.packageRoots().size());
        assertEquals(
                List.of("member:Api", "root:Main"),
                binding.sources().stream()
                        .map(source -> source.packageId() + ":" + source.logicalModule())
                        .sorted()
                        .toList());
        Path realRootSource = rootSource.toRealPath();
        Path realMemberSource = memberSource.toRealPath();
        assertTrue(binding.sources().stream().anyMatch(source -> source.source().equals(realRootSource)));
        assertTrue(binding.sources().stream().anyMatch(source -> source.source().equals(realMemberSource)));
    }

    @Test
    void ordinarySourceChangesDoNotStaleProjectAuthority() throws Exception {
        Files.writeString(temporary.resolve("protos.toml"), "root metadata\n");
        Files.writeString(temporary.resolve("protos.lock"), "lock metadata\n");
        writeProject(temporary, List.of());
        ProtosProjectFileBindingProvider provider = new ProtosProjectFileBindingProvider();

        assertTrue(provider.acquire(temporary).isPresent());
        Files.writeString(temporary.resolve("Added.protos"), "true\n");
        ProtosProjectBinding afterAdd = provider.acquire(temporary).orElseThrow();
        assertEquals(List.of("Added"), afterAdd.sources().stream().map(ProtosProjectBinding.Source::logicalModule).toList());
        Files.delete(temporary.resolve("Added.protos"));
        assertTrue(provider.acquire(temporary).orElseThrow().sources().isEmpty());
    }

    @Test
    void changedMetadataFailsClosedWithoutGuestRefresh() throws Exception {
        Files.writeString(temporary.resolve("protos.toml"), "root metadata\n");
        Files.writeString(temporary.resolve("protos.lock"), "lock metadata\n");
        writeProject(temporary, List.of());
        ProtosProjectFileBindingProvider provider = new ProtosProjectFileBindingProvider();

        assertTrue(provider.acquire(temporary).isPresent());
        Files.writeString(temporary.resolve("protos.lock"), "changed lock\n");
        assertTrue(provider.acquire(temporary).isEmpty());
    }

    @Test
    void missingMalformedAndNoncanonicalAuthorityFailClosed() throws Exception {
        Files.writeString(temporary.resolve("protos.toml"), "root metadata\n");
        Files.writeString(temporary.resolve("protos.lock"), "lock metadata\n");
        ProtosProjectFileBindingProvider provider = new ProtosProjectFileBindingProvider();

        assertTrue(provider.acquire(temporary).isEmpty());

        writeProject(temporary, List.of());
        String canonical = Files.readString(temporary.resolve("protos.project"));
        Files.writeString(
                temporary.resolve("protos.project"),
                canonical.replace("sha256:" + RESOLUTION_HEX, "sha256:" + RESOLUTION_HEX.toUpperCase()));
        assertTrue(provider.acquire(temporary).isEmpty());

        Files.writeString(
                temporary.resolve("protos.project"),
                canonical.replace("root workspace \"root\"", "root workspace \"\\u0072oot\""));
        assertTrue(provider.acquire(temporary).isEmpty());
    }

    @Test
    void candidateRootIsExactAndNeverDiscoveredFromChild() throws Exception {
        Files.writeString(temporary.resolve("protos.toml"), "root metadata\n");
        Files.writeString(temporary.resolve("protos.lock"), "lock metadata\n");
        writeProject(temporary, List.of());
        Path child = Files.createDirectory(temporary.resolve("child"));

        ProtosProjectFileBindingProvider provider = new ProtosProjectFileBindingProvider();
        assertTrue(provider.acquire(temporary).isPresent());
        assertTrue(provider.acquire(child).isEmpty());
    }

    @Test
    void wrongCaseMemberLocationAndAuthoritySymlinkFailClosed() throws Exception {
        Path member = Files.createDirectories(temporary.resolve("libs/member"));
        Files.writeString(temporary.resolve("protos.toml"), "root metadata\n");
        Files.writeString(member.resolve("protos.toml"), "member metadata\n");
        Files.writeString(temporary.resolve("protos.lock"), "lock metadata\n");
        writeProject(temporary, List.of(new Member("libs/member", "member")));
        Files.move(temporary.resolve("libs"), temporary.resolve("Libs"));

        ProtosProjectFileBindingProvider provider = new ProtosProjectFileBindingProvider();
        assertTrue(provider.acquire(temporary).isEmpty());

        Files.delete(temporary.resolve("protos.project"));
        Path target = Files.writeString(temporary.resolve("actual.project"), "not authority\n");
        try {
            Files.createSymbolicLink(temporary.resolve("protos.project"), target.getFileName());
            assertTrue(provider.acquire(temporary).isEmpty());
        } catch (UnsupportedOperationException | IOException | SecurityException unsupported) {
            // Host does not support test symlinks; exact-case member failure above remains asserted.
        }
    }

    private void writeProject(Path root, List<Member> members) throws Exception {
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
        for (Member member : members) {
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
