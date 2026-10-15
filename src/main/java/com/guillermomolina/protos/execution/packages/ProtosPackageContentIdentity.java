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
 * Immutable host copy of one package ContentIdentity.
 *
 * <p>ContentIdentity method/algorithm/hex policy is owned by bundled Protos
 * {@code self:ContentIdentity}; this host value only preserves the three exact, non-empty fields.
 */
public record ProtosPackageContentIdentity(String method, String algorithm, String hex) {
    public ProtosPackageContentIdentity {
        method = requireNonEmpty(method, "method");
        algorithm = requireNonEmpty(algorithm, "algorithm");
        hex = requireNonEmpty(hex, "hex");
    }

    private static String requireNonEmpty(String value, String label) {
        Objects.requireNonNull(value, label);
        if (value.isEmpty()) {
            throw new IllegalArgumentException("empty ContentIdentity " + label);
        }
        return value;
    }
}
