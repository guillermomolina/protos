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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.runtime.ProtosModuleKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;

final class ProtosExternalPackageModuleKeyTest {
    private static final String PREFIX = "pkg-external:v1:";
    private static final ProtosPackageContentIdentity CONTENT =
            new ProtosPackageContentIdentity(
                    "protos-package-tree-v1",
                    "sha256",
                    "5c2e8af458daf38c3e42ee3a68a928aa5ad2701166a59e6cabfb38645efe6f1a");
    private static final ProtosPackageContentIdentity OTHER_CONTENT =
            new ProtosPackageContentIdentity(
                    "protos-package-tree-v1",
                    "sha256",
                    "9f7a40f88c9c231748feb4ed242a4ebe9ec5f06cb732afd4e80706fd160a8ac0");

    private static final ProtosExactExternalPackageIdentity REGISTRY =
            new ProtosExactExternalPackageIdentity.Registry("Scope/Ä:pkg", "1.0.0-rc.1", CONTENT);
    private static final ProtosExactExternalPackageIdentity GIT =
            new ProtosExactExternalPackageIdentity.Git("Scope/Ä:pkg", "abc123", CONTENT);

    @Test
    void registryAndGitKeysRoundTripExactly() throws Exception {
        for (ProtosExactExternalPackageIdentity identity : new ProtosExactExternalPackageIdentity[] {REGISTRY, GIT}) {
            ProtosModuleKey key = ProtosExternalPackageModuleKey.encode(identity, "core/Internal_2");
            ProtosExternalPackageModuleKey.Address address =
                    ProtosExternalPackageModuleKey.decode(key);

            assertEquals(identity, address.identity());
            assertEquals("core/Internal_2", address.logicalModule());
            assertTrue(ProtosExternalPackageModuleKey.owns(key));
            assertEquals(key, ProtosExternalPackageModuleKey.encode(identity, "core/Internal_2"));
        }
    }

    @Test
    void everyIdentityComponentAndTheLogicalModuleDistinguishKeys() throws Exception {
        ProtosModuleKey base = ProtosExternalPackageModuleKey.encode(REGISTRY, "Thing");

        assertNotEquals(base, ProtosExternalPackageModuleKey.encode(REGISTRY, "Other"));
        assertNotEquals(
                base,
                ProtosExternalPackageModuleKey.encode(
                        new ProtosExactExternalPackageIdentity.Registry(
                                "Scope/Ä:pkg", "1.0.0", CONTENT),
                        "Thing"));
        assertNotEquals(
                base,
                ProtosExternalPackageModuleKey.encode(
                        new ProtosExactExternalPackageIdentity.Registry(
                                "Scope/Ä:pkg", "1.0.0-rc.1", OTHER_CONTENT),
                        "Thing"));
        assertNotEquals(
                ProtosExternalPackageModuleKey.encode(GIT, "Thing"),
                ProtosExternalPackageModuleKey.encode(
                        new ProtosExactExternalPackageIdentity.Git(
                                "Scope/Ä:pkg", "abc124", CONTENT),
                        "Thing"));
    }

    @Test
    void sameContentUnderDistinctLogicalIdentitiesYieldsDistinctKeys() throws Exception {
        ProtosModuleKey registryKey = ProtosExternalPackageModuleKey.encode(
                new ProtosExactExternalPackageIdentity.Registry("pkg", "abc123", CONTENT), "Thing");
        ProtosModuleKey gitKey = ProtosExternalPackageModuleKey.encode(
                new ProtosExactExternalPackageIdentity.Git("pkg", "abc123", CONTENT), "Thing");
        ProtosModuleKey otherPackageKey = ProtosExternalPackageModuleKey.encode(
                new ProtosExactExternalPackageIdentity.Registry("other", "abc123", CONTENT), "Thing");

        assertNotEquals(registryKey, gitKey);
        assertNotEquals(registryKey, otherPackageKey);
    }

    @Test
    void identityExcludesAliasesExportsAndPhysicalProvenance() throws Exception {
        ProtosModuleKey key = ProtosExternalPackageModuleKey.encode(REGISTRY, "internal/Thing");
        String canonical = key.canonicalId();

        assertTrue(canonical.startsWith(PREFIX + "registry:"));
        assertEquals(9, canonical.split(":", -1).length);
        assertFalse(canonical.contains("dep:"));
        assertFalse(canonical.contains("https"));
        assertFalse(canonical.contains("/store/"));

        ProtosExternalPackageModuleKey.Address address =
                ProtosExternalPackageModuleKey.decode(key);
        assertEquals(REGISTRY, address.identity());
        assertEquals("internal/Thing", address.logicalModule());
    }

    @Test
    void workspaceAndExternalDomainsAreDisjoint() throws Exception {
        ProtosModuleKey workspace =
                ProtosWorkspacePackageModuleKey.encode("Scope/Ä:pkg", "internal/Thing");
        ProtosModuleKey external = ProtosExternalPackageModuleKey.encode(REGISTRY, "internal/Thing");

        assertFalse(ProtosExternalPackageModuleKey.owns(workspace));
        assertFalse(ProtosWorkspacePackageModuleKey.owns(external));
        assertThrows(IOException.class, () -> ProtosExternalPackageModuleKey.decode(workspace));
        assertThrows(IOException.class, () -> ProtosWorkspacePackageModuleKey.decode(external));
        assertFalse(ProtosExternalPackageModuleKey.owns(new ProtosModuleKey("std:collections/Array")));
    }

    @Test
    void rejectsInvalidLogicalModulesOnEncode() {
        assertThrows(IOException.class, () -> ProtosExternalPackageModuleKey.encode(GIT, "../Thing"));
        assertThrows(IOException.class, () -> ProtosExternalPackageModuleKey.encode(GIT, "CON"));
        assertThrows(IOException.class, () -> ProtosExternalPackageModuleKey.encode(GIT, ""));
    }

    @Test
    void rejectsMalformedAndNonCanonicalEncodings() {
        String packageId = base64("pkg");
        String exact = base64("1.0.0");
        String method = base64(CONTENT.method());
        String algorithm = base64(CONTENT.algorithm());
        String hex = base64(CONTENT.hex());
        String module = base64("Main");
        String tail = ":" + method + ":" + algorithm + ":" + hex + ":" + module;

        assertDecodes(PREFIX + "registry:" + packageId + ":" + exact + tail);

        // Wrong field counts, unknown kind, empty identity fields.
        assertRejected(PREFIX + "registry:" + packageId + ":" + exact);
        assertRejected(PREFIX + "registry:" + packageId + ":" + exact + tail + ":" + module);
        assertRejected(PREFIX + "workspace:" + packageId + ":" + exact + tail);
        assertRejected(PREFIX + "Registry:" + packageId + ":" + exact + tail);
        assertRejected(PREFIX + "registry::" + exact + tail);
        // Padded, non-base64url, and invalid-logical-name payloads.
        assertRejected(PREFIX + "registry:" + packageId + "=:" + exact + tail);
        assertRejected(PREFIX + "registry:" + packageId + ":" + exact + tail.replace(module, "+/"));
        assertRejected(
                PREFIX + "registry:" + packageId + ":" + exact
                        + tail.replace(module, base64("../Main")));
        // Non-canonical trailing base64 bits and malformed UTF-8 payloads.
        // "cB" carries the same octet as canonical "cA" ("p") with non-zero unused bits.
        assertRejected(PREFIX + "registry:" + "cB" + ":" + exact + tail);
        String malformedUtf8 =
                Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[] {(byte) 0xC3});
        assertRejected(PREFIX + "registry:" + malformedUtf8 + ":" + exact + tail);
    }

    private static void assertDecodes(String canonicalId) {
        try {
            ProtosExternalPackageModuleKey.decode(new ProtosModuleKey(canonicalId));
        } catch (IOException failure) {
            throw new AssertionError("expected canonical key to decode: " + canonicalId, failure);
        }
    }

    private static void assertRejected(String canonicalId) {
        assertThrows(
                IOException.class,
                () -> ProtosExternalPackageModuleKey.decode(new ProtosModuleKey(canonicalId)));
    }

    private static String base64(String value) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
