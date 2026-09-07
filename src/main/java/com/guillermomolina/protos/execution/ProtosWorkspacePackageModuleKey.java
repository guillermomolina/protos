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
 * Canonical host-only identity codec for modules in the bounded workspace package resolver.
 *
 * <p>The key depends only on workspace PackageId plus the internal logical module name. It never
 * includes a project path, dependency alias, export alias, checkout location, or cache location.
 */
final class ProtosWorkspacePackageModuleKey {
    private static final String PREFIX = "pkg-workspace:v1:";

    private ProtosWorkspacePackageModuleKey() {}

    static ProtosModuleKey encode(String packageId, String logicalModule) throws IOException {
        if (packageId == null || packageId.isEmpty()) {
            throw new IOException("empty workspace PackageId");
        }
        String checkedModule = ProtosPackageRuntimeNames.requireLogicalName(logicalModule);
        return new ProtosModuleKey(
                PREFIX + encodeUtf8(packageId) + ":" + encodeUtf8(checkedModule));
    }

    static Address decode(ProtosModuleKey key) throws IOException {
        Objects.requireNonNull(key, "key");
        String canonical = key.canonicalId();
        if (!canonical.startsWith(PREFIX)) {
            throw new IOException("module key is outside workspace package domain");
        }

        String encoded = canonical.substring(PREFIX.length());
        int separator = encoded.indexOf(':');
        if (separator <= 0
                || separator == encoded.length() - 1
                || encoded.indexOf(':', separator + 1) >= 0) {
            throw new IOException("invalid workspace package ModuleKey");
        }

        String encodedPackageId = encoded.substring(0, separator);
        String encodedLogicalModule = encoded.substring(separator + 1);

        String packageId = decodeCanonicalUtf8(encodedPackageId);
        String logicalModule = decodeCanonicalUtf8(encodedLogicalModule);
        if (packageId.isEmpty()) {
            throw new IOException("empty workspace PackageId");
        }
        ProtosPackageRuntimeNames.requireLogicalName(logicalModule);

        ProtosModuleKey reconstructed = encode(packageId, logicalModule);
        if (!reconstructed.equals(key)) {
            throw new IOException("non-canonical workspace package ModuleKey");
        }

        return new Address(packageId, logicalModule);
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
                throw new IOException("non-canonical workspace package ModuleKey encoding");
            }
            return decoded;
        } catch (IllegalArgumentException badEncoding) {
            throw new IOException("invalid workspace package ModuleKey encoding", badEncoding);
        }
    }

    record Address(String packageId, String logicalModule) {}
}
