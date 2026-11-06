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
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;

final class ProtosWorkspacePackageModuleKeyTest {
    @Test
    void packageIdAndInternalLogicalNameAreTheOnlyCanonicalIdentityInputs() throws Exception {
        ProtosModuleKey first =
                ProtosWorkspacePackageModuleKey.encode(ProtosModuleKey.Kind.MODULE, "org/example:π", "internal/Thing");
        ProtosModuleKey same =
                ProtosWorkspacePackageModuleKey.encode(ProtosModuleKey.Kind.MODULE, "org/example:π", "internal/Thing");
        ProtosModuleKey otherPackage =
                ProtosWorkspacePackageModuleKey.encode(ProtosModuleKey.Kind.MODULE, "org/other:π", "internal/Thing");
        ProtosModuleKey otherModule =
                ProtosWorkspacePackageModuleKey.encode(ProtosModuleKey.Kind.MODULE, "org/example:π", "internal/Other");

        assertEquals(first, same);
        assertNotEquals(first, otherPackage);
        assertNotEquals(first, otherModule);
        assertTrue(first.canonicalId().startsWith("pkg-workspace:v1:"));
        assertFalse(first.canonicalId().contains("/checkout/"));
        assertFalse(first.canonicalId().contains("dep:"));
    }

    @Test
    void canonicalKeyRoundTripsOpaquePackageIdWithoutNormalization() throws Exception {
        String packageId = "Scope/Ä:pkg";
        ProtosModuleKey key =
                ProtosWorkspacePackageModuleKey.encode(ProtosModuleKey.Kind.MODULE, packageId, "core/Internal_2");

        ProtosWorkspacePackageModuleKey.Address address =
                ProtosWorkspacePackageModuleKey.decode(key);

        assertEquals(packageId, address.packageId());
        assertEquals("core/Internal_2", address.logicalModule());
        assertTrue(ProtosWorkspacePackageModuleKey.owns(key));
    }

    @Test
    void distinctLookupAliasesCannotEnterCanonicalModuleIdentity() throws Exception {
        ProtosModuleKey key =
                ProtosWorkspacePackageModuleKey.encode(ProtosModuleKey.Kind.MODULE, "a-pkg", "internal/Thing");

        ProtosWorkspacePackageModuleKey.Address address =
                ProtosWorkspacePackageModuleKey.decode(key);

        assertEquals("a-pkg", address.packageId());
        assertEquals("internal/Thing", address.logicalModule());
    }

    @Test
    void rejectsInvalidLogicalNamesAndForeignOrNonCanonicalKeys() throws Exception {
        assertThrows(
                Exception.class,
                () -> ProtosWorkspacePackageModuleKey.encode(ProtosModuleKey.Kind.MODULE, "pkg", "../Thing"));
        assertThrows(
                Exception.class,
                () -> ProtosWorkspacePackageModuleKey.encode(ProtosModuleKey.Kind.MODULE, "pkg", "CON"));
        assertThrows(
                Exception.class,
                () -> ProtosWorkspacePackageModuleKey.encode(ProtosModuleKey.Kind.MODULE, "", "Main"));

        assertThrows(
                Exception.class,
                () ->
                        ProtosWorkspacePackageModuleKey.decode(
                                ProtosModuleKey.module("std:collections/array")));
        assertThrows(
                Exception.class,
                () ->
                        ProtosWorkspacePackageModuleKey.decode(
                                ProtosModuleKey.source("pkg-workspace:v1:broken")));

        String packageId = base64("pkg");
        String module = base64("Main");
        assertThrows(
                Exception.class,
                () ->
                        ProtosWorkspacePackageModuleKey.decode(
                                ProtosModuleKey.module(
                                        "pkg-workspace:v1:module:" + packageId + "=:" + module)));
    }

    @Test
    void sourceAndModuleIdentitiesAreDisjoint() throws Exception {
        ProtosModuleKey module =
                ProtosWorkspacePackageModuleKey.encode(ProtosModuleKey.Kind.MODULE, "pkg", "range");
        ProtosModuleKey source =
                ProtosWorkspacePackageModuleKey.encode(ProtosModuleKey.Kind.SOURCE, "pkg", "range");
        assertNotEquals(module, source);
        assertEquals("range/module.protos", ProtosWorkspacePackageModuleKey.decode(module).resourcePath());
        assertEquals("range.protos", ProtosWorkspacePackageModuleKey.decode(source).resourcePath());
        assertThrows(
                Exception.class,
                () -> ProtosWorkspacePackageModuleKey.decode(
                        ProtosModuleKey.source(module.canonicalId())));
        assertThrows(
                Exception.class,
                () -> ProtosWorkspacePackageModuleKey.encode(
                        ProtosModuleKey.Kind.SOURCE, "pkg", "range/module"));
        assertThrows(
                Exception.class,
                () -> ProtosWorkspacePackageModuleKey.encode(
                        ProtosModuleKey.Kind.FOREIGN, "pkg", "range"));
    }

    private static String base64(String value) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
