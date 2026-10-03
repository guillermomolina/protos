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

import com.guillermomolina.protos.runtime.ProtosModuleKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;

/**
 * Canonical host-only identity codec for modules of exact immutable external packages (PLAT012).
 *
 * <p>The key depends only on the complete {@link ProtosExactExternalPackageIdentity} (kind, exact
 * package coordinates and ContentIdentity) plus the internal logical module name. It never
 * includes a dependency alias, export alias, registry locator, Git fetch URL, mirror, source,
 * store or cache path, or custody handle. Its domain prefix is disjoint from
 * {@link ProtosWorkspacePackageModuleKey}. The codec reads no sources and holds no authority.
 *
 * <p>Layout: {@code pkg-external:v1:<kind>:<packageId>:<exact>:<method>:<algorithm>:<hex>:<module>}
 * where {@code kind} is {@code registry} or {@code git}, {@code exact} is the canonical
 * ReleaseVersion text or Git revision, and every field after the kind is unpadded base64url of its
 * UTF-8 bytes. Decoding is strict: anything that does not re-encode byte-for-byte is rejected.
 */
final class ProtosExternalPackageModuleKey {
    private static final String PREFIX = "pkg-external:v1:";
    private static final String REGISTRY = "registry";
    private static final String GIT = "git";
    private static final int FIELD_COUNT = 7;

    private ProtosExternalPackageModuleKey() {}

    static ProtosModuleKey encode(
            ProtosExactExternalPackageIdentity identity, String logicalModule)
            throws IOException {
        Objects.requireNonNull(identity, "identity");
        String checkedModule = ProtosPackageRuntimeNames.requireLogicalName(logicalModule);

        String kind =
                switch (identity) {
                    case ProtosExactExternalPackageIdentity.Registry ignored -> REGISTRY;
                    case ProtosExactExternalPackageIdentity.Git ignored -> GIT;
                };
        String exact =
                switch (identity) {
                    case ProtosExactExternalPackageIdentity.Registry registry ->
                            registry.exactVersion();
                    case ProtosExactExternalPackageIdentity.Git git -> git.revision();
                };

        ProtosPackageContentIdentity content = identity.content();
        return new ProtosModuleKey(
                PREFIX
                        + kind
                        + ":" + encodeUtf8(identity.packageId())
                        + ":" + encodeUtf8(exact)
                        + ":" + encodeUtf8(content.method())
                        + ":" + encodeUtf8(content.algorithm())
                        + ":" + encodeUtf8(content.hex())
                        + ":" + encodeUtf8(checkedModule));
    }

    static Address decode(ProtosModuleKey key) throws IOException {
        Objects.requireNonNull(key, "key");
        String canonical = key.canonicalId();
        if (!canonical.startsWith(PREFIX)) {
            throw new IOException("module key is outside external package domain");
        }

        String[] fields = canonical.substring(PREFIX.length()).split(":", -1);
        if (fields.length != FIELD_COUNT) {
            throw new IOException("invalid external package ModuleKey");
        }

        String packageId = decodeCanonicalUtf8(fields[1]);
        String exact = decodeCanonicalUtf8(fields[2]);
        String logicalModule = decodeCanonicalUtf8(fields[6]);

        ProtosExactExternalPackageIdentity identity;
        try {
            ProtosPackageContentIdentity content =
                    new ProtosPackageContentIdentity(
                            decodeCanonicalUtf8(fields[3]),
                            decodeCanonicalUtf8(fields[4]),
                            decodeCanonicalUtf8(fields[5]));
            identity =
                    switch (fields[0]) {
                        case REGISTRY ->
                                new ProtosExactExternalPackageIdentity.Registry(
                                        packageId, exact, content);
                        case GIT ->
                                new ProtosExactExternalPackageIdentity.Git(
                                        packageId, exact, content);
                        default ->
                                throw new IOException(
                                        "unknown external package ModuleKey kind");
                    };
        } catch (IllegalArgumentException invalid) {
            throw new IOException("invalid external package ModuleKey identity", invalid);
        }
        ProtosPackageRuntimeNames.requireLogicalName(logicalModule);

        if (!encode(identity, logicalModule).equals(key)) {
            throw new IOException("non-canonical external package ModuleKey");
        }

        return new Address(identity, logicalModule);
    }

    static boolean owns(ProtosModuleKey key) {
        return key != null && key.canonicalId().startsWith(PREFIX);
    }

    private static String encodeUtf8(String value) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decodeCanonicalUtf8(String encoded) throws IOException {
        try {
            byte[] bytes = Base64.getUrlDecoder().decode(encoded);
            String decoded = new String(bytes, StandardCharsets.UTF_8);
            if (!encodeUtf8(decoded).equals(encoded)) {
                throw new IOException("non-canonical external package ModuleKey encoding");
            }
            return decoded;
        } catch (IllegalArgumentException badEncoding) {
            throw new IOException("invalid external package ModuleKey encoding", badEncoding);
        }
    }

    record Address(ProtosExactExternalPackageIdentity identity, String logicalModule) {}
}
