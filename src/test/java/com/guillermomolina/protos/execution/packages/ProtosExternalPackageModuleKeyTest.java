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

import com.guillermomolina.protos.runtime.modules.ProtosModuleKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;

final class ProtosExternalPackageModuleKeyTest {
    private static final String PREFIX = "pkg-external:v1:module:";
    private static final ProtosPackageContentIdentity CONTENT =
            new ProtosPackageContentIdentity(
                    "protos-package-tree-v2",
                    "sha256",
                    "899675849d2033fff2cfd0fc6552d598082c29bf96be634a37dcf8c6e607fec3");
    private static final ProtosPackageContentIdentity OTHER_CONTENT =
            new ProtosPackageContentIdentity(
                    "protos-package-tree-v2",
                    "sha256",
                    "3f63720edac546db28e1d2deb6004beae649094c65c9353640306ff466e43111");

    private static final ProtosExactExternalPackageIdentity REGISTRY =
            new ProtosExactExternalPackageIdentity.Registry("Scope/Ä:pkg", "1.0.0-rc.1", CONTENT);
    private static final ProtosExactExternalPackageIdentity GIT =
            new ProtosExactExternalPackageIdentity.Git("Scope/Ä:pkg", "abc123", CONTENT);

    @Test
    void registryAndGitKeysRoundTripExactly() throws Exception {
        for (ProtosExactExternalPackageIdentity identity : new ProtosExactExternalPackageIdentity[] {REGISTRY, GIT}) {
            ProtosModuleKey key = ProtosExternalPackageModuleKey.encode(ProtosModuleKey.Kind.MODULE, identity, "core/Internal_2");
            ProtosExternalPackageModuleKey.Address address =
                    ProtosExternalPackageModuleKey.decode(key);

            assertEquals(identity, address.identity());
            assertEquals("core/Internal_2", address.logicalModule());
            assertTrue(ProtosExternalPackageModuleKey.owns(key));
            assertEquals(key, ProtosExternalPackageModuleKey.encode(ProtosModuleKey.Kind.MODULE, identity, "core/Internal_2"));
        }
    }

    @Test
    void everyIdentityComponentAndTheLogicalModuleDistinguishKeys() throws Exception {
        ProtosModuleKey base = ProtosExternalPackageModuleKey.encode(ProtosModuleKey.Kind.MODULE, REGISTRY, "Thing");

        assertNotEquals(base, ProtosExternalPackageModuleKey.encode(ProtosModuleKey.Kind.MODULE, REGISTRY, "Other"));
        assertNotEquals(
                base,
                ProtosExternalPackageModuleKey.encode(
                        ProtosModuleKey.Kind.MODULE, new ProtosExactExternalPackageIdentity.Registry(
                                "Scope/Ä:pkg", "1.0.0", CONTENT),
                        "Thing"));
        assertNotEquals(
                base,
                ProtosExternalPackageModuleKey.encode(
                        ProtosModuleKey.Kind.MODULE, new ProtosExactExternalPackageIdentity.Registry(
                                "Scope/Ä:pkg", "1.0.0-rc.1", OTHER_CONTENT),
                        "Thing"));
        assertNotEquals(
                ProtosExternalPackageModuleKey.encode(ProtosModuleKey.Kind.MODULE, GIT, "Thing"),
                ProtosExternalPackageModuleKey.encode(
                        ProtosModuleKey.Kind.MODULE, new ProtosExactExternalPackageIdentity.Git(
                                "Scope/Ä:pkg", "abc124", CONTENT),
                        "Thing"));
    }

    @Test
    void sameContentUnderDistinctLogicalIdentitiesYieldsDistinctKeys() throws Exception {
        ProtosModuleKey registryKey = ProtosExternalPackageModuleKey.encode(
                ProtosModuleKey.Kind.MODULE, new ProtosExactExternalPackageIdentity.Registry("pkg", "abc123", CONTENT), "Thing");
        ProtosModuleKey gitKey = ProtosExternalPackageModuleKey.encode(
                ProtosModuleKey.Kind.MODULE, new ProtosExactExternalPackageIdentity.Git("pkg", "abc123", CONTENT), "Thing");
        ProtosModuleKey otherPackageKey = ProtosExternalPackageModuleKey.encode(
                ProtosModuleKey.Kind.MODULE, new ProtosExactExternalPackageIdentity.Registry("other", "abc123", CONTENT), "Thing");

        assertNotEquals(registryKey, gitKey);
        assertNotEquals(registryKey, otherPackageKey);
    }

    @Test
    void identityExcludesAliasesExportsAndPhysicalProvenance() throws Exception {
        ProtosModuleKey key = ProtosExternalPackageModuleKey.encode(ProtosModuleKey.Kind.MODULE, REGISTRY, "internal/Thing");
        String canonical = key.canonicalId();

        assertTrue(canonical.startsWith(PREFIX + "registry:"));
        assertEquals(10, canonical.split(":", -1).length);
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
                ProtosWorkspacePackageModuleKey.encode(ProtosModuleKey.Kind.MODULE, "Scope/Ä:pkg", "internal/Thing");
        ProtosModuleKey external = ProtosExternalPackageModuleKey.encode(ProtosModuleKey.Kind.MODULE, REGISTRY, "internal/Thing");

        assertFalse(ProtosExternalPackageModuleKey.owns(workspace));
        assertFalse(ProtosWorkspacePackageModuleKey.owns(external));
        assertThrows(IOException.class, () -> ProtosExternalPackageModuleKey.decode(workspace));
        assertThrows(IOException.class, () -> ProtosWorkspacePackageModuleKey.decode(external));
        assertFalse(ProtosExternalPackageModuleKey.owns(ProtosModuleKey.module("std:collections/array")));
    }

    @Test
    void rejectsInvalidLogicalModulesOnEncode() {
        assertThrows(IOException.class, () -> ProtosExternalPackageModuleKey.encode(ProtosModuleKey.Kind.MODULE, GIT, "../Thing"));
        assertThrows(IOException.class, () -> ProtosExternalPackageModuleKey.encode(ProtosModuleKey.Kind.MODULE, GIT, "CON"));
        assertThrows(IOException.class, () -> ProtosExternalPackageModuleKey.encode(ProtosModuleKey.Kind.MODULE, GIT, ""));
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

    @Test
    void targetKindIsPartOfCanonicalIdentityAndResourcePath() throws IOException {
        ProtosModuleKey module =
                ProtosExternalPackageModuleKey.encode(ProtosModuleKey.Kind.MODULE, REGISTRY, "lib/shapes");
        ProtosModuleKey source =
                ProtosExternalPackageModuleKey.encode(ProtosModuleKey.Kind.SOURCE, REGISTRY, "lib/shapes");
        assertNotEquals(module, source);
        assertTrue(source.canonicalId().startsWith("pkg-external:v1:source:registry:"));
        assertEquals(
                "lib/shapes/module.protos",
                ProtosExternalPackageModuleKey.decode(module).resourcePath());
        assertEquals(
                "lib/shapes.protos",
                ProtosExternalPackageModuleKey.decode(source).resourcePath());
        // A key whose kind disagrees with its canonical tag is rejected.
        assertThrows(
                IOException.class,
                () -> ProtosExternalPackageModuleKey.decode(
                        ProtosModuleKey.source(module.canonicalId())));
        // A SOURCE can never name a module initializer.
        assertThrows(
                IOException.class,
                () -> ProtosExternalPackageModuleKey.encode(
                        ProtosModuleKey.Kind.SOURCE, REGISTRY, "lib/module"));
    }

    private static void assertDecodes(String canonicalId) {
        try {
            ProtosExternalPackageModuleKey.decode(ProtosModuleKey.module(canonicalId));
        } catch (IOException failure) {
            throw new AssertionError("expected canonical key to decode: " + canonicalId, failure);
        }
    }

    private static void assertRejected(String canonicalId) {
        assertThrows(
                IOException.class,
                () -> ProtosExternalPackageModuleKey.decode(ProtosModuleKey.module(canonicalId)));
    }

    private static String base64(String value) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
