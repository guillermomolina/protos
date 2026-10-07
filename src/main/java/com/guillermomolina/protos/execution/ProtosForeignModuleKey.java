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
 * Canonical host-only identity codec for foreign modules (PLAT053, D188).
 *
 * <p>The key depends only on the provider routing identity (the import scheme) and the
 * provider-defined canonical target identity. Layout: {@code foreign:v1:<scheme>:<target>} with
 * both fields as unpadded base64url of their UTF-8 bytes, so no target spelling can forge another
 * field or domain. The {@code foreign:} domain is exclusive: {@link ProtosModuleRuntime} rejects
 * any source resolver result inside it, so a foreign key never equals a source-backed key.
 */
final class ProtosForeignModuleKey {
    private static final String PREFIX = "foreign:v1:";

    private ProtosForeignModuleKey() {}

    static ProtosModuleKey encode(String scheme, String canonicalTarget) {
        Objects.requireNonNull(scheme, "scheme");
        Objects.requireNonNull(canonicalTarget, "canonicalTarget");
        return new ProtosModuleKey(PREFIX + encodeUtf8(scheme) + ":" + encodeUtf8(canonicalTarget));
    }

    static Address decode(ProtosModuleKey key) throws IOException {
        Objects.requireNonNull(key, "key");
        if (!owns(key)) {
            throw new IOException("module key is outside foreign module domain");
        }
        String[] fields = key.canonicalId().substring(PREFIX.length()).split(":", -1);
        if (fields.length != 2) {
            throw new IOException("invalid foreign ModuleKey");
        }
        return new Address(decodeCanonicalUtf8(fields[0]), decodeCanonicalUtf8(fields[1]));
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
            String decoded =
                    new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
            if (!encodeUtf8(decoded).equals(encoded)) {
                throw new IOException("non-canonical foreign ModuleKey encoding");
            }
            return decoded;
        } catch (IllegalArgumentException badEncoding) {
            throw new IOException("invalid foreign ModuleKey encoding", badEncoding);
        }
    }

    record Address(String scheme, String canonicalTarget) {}
}
