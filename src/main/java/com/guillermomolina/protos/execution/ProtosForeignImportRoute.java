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
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Explicit import route owned by one foreign provider descriptor (PLAT053).
 *
 * <p>An import specifier {@code scheme:target} is foreign only when {@code scheme} is exactly the
 * scheme of a route registered with the importing Process's RuntimeHost; foreignness is never
 * inferred from extensions, contents, availability, or a failed source resolution. The scheme is
 * routing metadata distinct from {@link ProtosForeignProviderId}. Schemes used by source module
 * specifiers are reserved so that a route can never capture an already-valid source import.
 */
record ProtosForeignImportRoute(String scheme, ProtosForeignModuleProvider modules) {
    private static final Pattern SCHEME = Pattern.compile("[a-z][a-z0-9+.-]*");
    private static final Set<String> SOURCE_SCHEMES = Set.of("std", "self", "dep", "tool-shared");

    ProtosForeignImportRoute {
        Objects.requireNonNull(scheme, "scheme");
        Objects.requireNonNull(modules, "modules");
        if (!isCandidateScheme(scheme)) {
            throw new IllegalArgumentException("invalid foreign import scheme: " + scheme);
        }
    }

    /** True when {@code scheme} could name a foreign route: valid syntax and not source-owned. */
    static boolean isCandidateScheme(String scheme) {
        return !SOURCE_SCHEMES.contains(scheme) && SCHEME.matcher(scheme).matches();
    }
}
