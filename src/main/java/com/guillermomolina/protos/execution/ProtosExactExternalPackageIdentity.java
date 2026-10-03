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

import java.util.Objects;

/**
 * Exact identity of one immutable external package: its exact typed NodeRef plus ContentIdentity.
 *
 * <p>Equality covers every component, so the same PackageId at different exact versions or Git
 * revisions, the same ContentIdentity under different logical identities, and registry versus Git
 * coordinates all remain distinct. Dependency aliases, exports, locators, fetch URLs, mirrors,
 * store/cache paths and custody handles are deliberately not part of this identity.
 */
public sealed interface ProtosExactExternalPackageIdentity {
    String packageId();

    ProtosPackageContentIdentity content();

    /**
     * Registry package identity. {@code exactVersion} is the canonical ReleaseVersion text, the
     * same exact spelling the lock uses for registry NodeRef identity.
     */
    record Registry(String packageId, String exactVersion, ProtosPackageContentIdentity content)
            implements ProtosExactExternalPackageIdentity {
        public Registry {
            packageId = requireNonEmpty(packageId, "packageId");
            exactVersion = requireNonEmpty(exactVersion, "exactVersion");
            Objects.requireNonNull(content, "content");
        }
    }

    /** Git package identity at one exact revision. */
    record Git(String packageId, String revision, ProtosPackageContentIdentity content)
            implements ProtosExactExternalPackageIdentity {
        public Git {
            packageId = requireNonEmpty(packageId, "packageId");
            revision = requireNonEmpty(revision, "revision");
            Objects.requireNonNull(content, "content");
        }
    }

    private static String requireNonEmpty(String value, String label) {
        Objects.requireNonNull(value, label);
        if (value.isEmpty()) {
            throw new IllegalArgumentException("empty external package " + label);
        }
        return value;
    }
}
