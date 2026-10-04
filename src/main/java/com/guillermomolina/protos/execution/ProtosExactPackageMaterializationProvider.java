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

import java.io.IOException;
import java.nio.file.Path;

/**
 * Run-scoped host lookup from one complete exact external package identity to one already-present
 * local package root (PLAT048 B′).
 *
 * <p>The public-run host bootstrap owns the provider; the Package Tool has already decided every
 * identity it is asked for. The lookup key is the complete identity: kind, PackageId, exact
 * ReleaseVersion or exact Git revision, and ContentIdentity. A provider must not widen it to a
 * PackageId-only, version-only, mutable-ref, locator or fetch-URL lookup, and must not solve,
 * fetch, write, scan for alternative candidates or fall back. It does not verify content: the
 * returned root is untrusted until F2E2 captures it once and verifies its ContentIdentity.
 *
 * <p>The provider is never reachable from the application Process.
 */
@FunctionalInterface
public interface ProtosExactPackageMaterializationProvider {
    /**
     * Selects the local root materializing exactly {@code identity}.
     *
     * @throws IOException when no materialization of exactly {@code identity} is available
     */
    Path select(ProtosExactExternalPackageIdentity identity) throws IOException;
}
