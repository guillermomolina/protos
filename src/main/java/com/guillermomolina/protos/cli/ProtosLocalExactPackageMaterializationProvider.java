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
package com.guillermomolina.protos.cli;

import com.guillermomolina.protos.execution.ProtosExactExternalPackageIdentity;
import com.guillermomolina.protos.execution.ProtosExactPackageMaterializationProvider;
import com.guillermomolina.protos.execution.ProtosPackageContentIdentity;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Implementation-private read-only local materialization backend for public package runs.
 *
 * <p>The physical root and exact-key encoding are host implementation details. Selection is one
 * deterministic path computation from the complete exact package identity: there is no directory
 * enumeration, partial-identity search, alternative candidate, fetch, fallback or store mutation.
 * The selected root remains untrusted until the unchanged F2E2 capture and ContentIdentity
 * verification performed by {@code ProtosPackageRunDriver}.
 */
final class ProtosLocalExactPackageMaterializationProvider
        implements ProtosExactPackageMaterializationProvider {
    private static final String KEY_DOMAIN = "protos-private-exact-materialization";

    private final Path materializationRoot;

    static ProtosLocalExactPackageMaterializationProvider forDistributionRoot(
            Path distributionRoot) {
        Objects.requireNonNull(distributionRoot, "distributionRoot");
        return new ProtosLocalExactPackageMaterializationProvider(
                distributionRoot.resolve(".package-materializations"));
    }

    ProtosLocalExactPackageMaterializationProvider(Path materializationRoot) {
        this.materializationRoot =
                Objects.requireNonNull(materializationRoot, "materializationRoot")
                        .toAbsolutePath()
                        .normalize();
    }

    @Override
    public Path select(ProtosExactExternalPackageIdentity identity) throws IOException {
        Path selectedRoot = locationFor(identity);
        if (!Files.isDirectory(selectedRoot, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException(
                    "exact external package materialization is unavailable for "
                            + describe(identity));
        }
        return selectedRoot;
    }

    /**
     * Returns the single implementation-private location selected for one complete exact identity.
     *
     * <p>Package-visible identity text never becomes a path component. Tests use this package-local
     * projection only to prepare temporary already-present materializations.
     */
    Path locationFor(ProtosExactExternalPackageIdentity identity) {
        Objects.requireNonNull(identity, "identity");
        return materializationRoot.resolve(exactKey(identity));
    }

    private static String exactKey(ProtosExactExternalPackageIdentity identity) {
        MessageDigest digest = sha256();
        updateField(digest, KEY_DOMAIN);

        switch (identity) {
            case ProtosExactExternalPackageIdentity.Registry registry -> {
                updateField(digest, "registry");
                updateField(digest, registry.packageId());
                updateField(digest, registry.exactVersion());
                updateContentIdentity(digest, registry.content());
            }
            case ProtosExactExternalPackageIdentity.Git git -> {
                updateField(digest, "git");
                updateField(digest, git.packageId());
                updateField(digest, git.revision());
                updateContentIdentity(digest, git.content());
            }
        }

        return HexFormat.of().formatHex(digest.digest());
    }

    private static void updateContentIdentity(
            MessageDigest digest, ProtosPackageContentIdentity content) {
        updateField(digest, content.method());
        updateField(digest, content.algorithm());
        updateField(digest, content.hex());
    }

    /**
     * Length-prefixes every UTF-8 field, making tuple boundaries unambiguous without exposing any
     * identity field as filesystem syntax.
     */
    private static void updateField(MessageDigest digest, String value) {
        byte[] bytes = Objects.requireNonNull(value, "value").getBytes(StandardCharsets.UTF_8);
        digest.update((byte) (bytes.length >>> 24));
        digest.update((byte) (bytes.length >>> 16));
        digest.update((byte) (bytes.length >>> 8));
        digest.update((byte) bytes.length);
        digest.update(bytes);
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("required host SHA-256 implementation is unavailable", impossible);
        }
    }

    private static String describe(ProtosExactExternalPackageIdentity identity) {
        return switch (identity) {
            case ProtosExactExternalPackageIdentity.Registry registry ->
                    "registry " + registry.packageId() + "@" + registry.exactVersion();
            case ProtosExactExternalPackageIdentity.Git git ->
                    "git " + git.packageId() + "@" + git.revision();
        };
    }
}
