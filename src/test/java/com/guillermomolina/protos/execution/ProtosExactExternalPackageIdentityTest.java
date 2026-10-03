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
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Set;
import org.junit.jupiter.api.Test;

final class ProtosExactExternalPackageIdentityTest {
    private static final ProtosPackageContentIdentity CONTENT =
            new ProtosPackageContentIdentity("protos-package-tree-v1", "sha256", "aa");
    private static final ProtosPackageContentIdentity OTHER_CONTENT =
            new ProtosPackageContentIdentity("protos-package-tree-v1", "sha256", "bb");

    @Test
    void equalityRequiresTheCompleteExactIdentity() {
        assertEquals(registry("pkg", "1.0.0", CONTENT), registry("pkg", "1.0.0", CONTENT));
        assertEquals(
                registry("pkg", "1.0.0", CONTENT).hashCode(),
                registry("pkg", "1.0.0", CONTENT).hashCode());
        assertEquals(git("pkg", "abc", CONTENT), git("pkg", "abc", CONTENT));

        assertNotEquals(registry("pkg", "1.0.0", CONTENT), registry("other", "1.0.0", CONTENT));
        assertNotEquals(registry("pkg", "1.0.0", CONTENT), registry("pkg", "1.0.1", CONTENT));
        assertNotEquals(git("pkg", "abc", CONTENT), git("pkg", "abd", CONTENT));
    }

    @Test
    void contentIdentityParticipatesInIdentity() {
        assertNotEquals(registry("pkg", "1.0.0", CONTENT), registry("pkg", "1.0.0", OTHER_CONTENT));
        assertNotEquals(git("pkg", "abc", CONTENT), git("pkg", "abc", OTHER_CONTENT));
        assertNotEquals(
                registry("pkg", "1.0.0", CONTENT),
                registry(
                        "pkg",
                        "1.0.0",
                        new ProtosPackageContentIdentity("protos-package-tree-v1", "sha512", "aa")));
    }

    @Test
    void sameContentDoesNotCollapseDistinctLogicalIdentities() {
        Set<ProtosExactExternalPackageIdentity> identities =
                Set.of(
                        registry("pkg", "1.0.0", CONTENT),
                        registry("pkg", "2.0.0", CONTENT),
                        registry("other", "1.0.0", CONTENT),
                        git("pkg", "abc", CONTENT),
                        git("pkg", "def", CONTENT));
        assertEquals(5, identities.size());
    }

    @Test
    void registryAndGitRemainDistinctDomainsWithOtherwiseEqualFields() {
        assertNotEquals(registry("pkg", "1.0.0", CONTENT), git("pkg", "1.0.0", CONTENT));
    }

    @Test
    void rejectsEmptyCoordinatesAndContentFields() {
        assertThrows(IllegalArgumentException.class, () -> registry("", "1.0.0", CONTENT));
        assertThrows(IllegalArgumentException.class, () -> registry("pkg", "", CONTENT));
        assertThrows(IllegalArgumentException.class, () -> git("pkg", "", CONTENT));
        assertThrows(NullPointerException.class, () -> git("pkg", "abc", null));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ProtosPackageContentIdentity("m", "a", ""));
    }

    private static ProtosExactExternalPackageIdentity registry(
            String packageId, String version, ProtosPackageContentIdentity content) {
        return new ProtosExactExternalPackageIdentity.Registry(packageId, version, content);
    }

    private static ProtosExactExternalPackageIdentity git(
            String packageId, String revision, ProtosPackageContentIdentity content) {
        return new ProtosExactExternalPackageIdentity.Git(packageId, revision, content);
    }
}
