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
package com.guillermomolina.protos.ffm;

import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

/**
 * Host authorization of the logical libraries one provider registration may load.
 *
 * <p>The distributable policy accepts syntactically valid logical library aliases. The host
 * selects exactly which aliases are authorized through {@code library.<alias>} options, each
 * mapping an alias to an exact native load name or an absolute path. A Protos program may
 * import any authorized alias, but cannot directly supply a path or native load name as
 * authority. The provider JAR contains no implicit library authorizations. Host authorization
 * and native library loading are independent of all other Protos capabilities.
 */
final class FfmLibraries {
    /** Prefix of the host options that authorize one logical library alias each. */
    static final String LIBRARY_OPTION_PREFIX = "library.";

    private FfmLibraries() {}

    /** Native location of one authorized library: a fixed loader name or a host path. */
    record Location(String name, String path) {}

    /** Targets accepted by one provider class and how host options locate them. */
    interface Policy {
        /** Pure target check used by {@code canonicalTarget}; it sees no host option. */
        boolean isTarget(String target);

        /** Resolves an accepted target from the host options; throws when not authorized. */
        Location resolve(String target, Map<String, String> options);
    }

    /**
     * The distributable provider: any syntactically valid logical alias, authorized only by the
     * host option {@code library.<alias>}. The option value is passed unchanged to the operating
     * system loader, either as an exact load name or as an absolute path. No library is
     * authorized by default, and no name, suffix, directory, or version is ever derived.
     */
    static final Policy DISTRIBUTABLE =
            new Policy() {
                @Override
                public boolean isTarget(String target) {
                    return FfmTypes.isIdentifier(target);
                }

                @Override
                public Location resolve(String target, Map<String, String> options) {
                    if (!isTarget(target)) {
                        throw unauthorized();
                    }
                    requireWellFormedLibraryOptions(options);
                    String value = options.get(LIBRARY_OPTION_PREFIX + target);
                    if (value == null) {
                        throw unauthorized();
                    }
                    return nativeTarget(value);
                }
            };

    /**
     * Test-only policy: exactly one logical {@code alias}, loaded only from the absolute path in
     * host option {@code <alias>.path}. Never used by the distributable provider.
     */
    static Policy fixture(String alias) {
        FfmTypes.requireIdentifier(alias);
        return new Policy() {
            @Override
            public boolean isTarget(String target) {
                return alias.equals(target);
            }

            @Override
            public Location resolve(String target, Map<String, String> options) {
                String path = options.get(alias + ".path");
                if (!isTarget(target) || path == null) {
                    throw unauthorized();
                }
                return new Location(null, requireAbsolute(path));
            }
        };
    }

    /** Immutable effective authorization of one session: its policy and host options. */
    record Authorization(Policy policy, Map<String, String> options) {
        Authorization {
            Objects.requireNonNull(policy, "policy");
            options = Map.copyOf(options);
        }

        Location resolve(String target) {
            return policy.resolve(target, options);
        }
    }

    private static FfmFailure unauthorized() {
        return new FfmFailure("unauthorized-library", "the library is not authorized by the host");
    }

    /**
     * Rejects, before any native acquisition, a host table whose {@code library.<alias>} keys do
     * not use the import alias form or whose targets are malformed.
     */
    private static void requireWellFormedLibraryOptions(Map<String, String> options) {
        for (Map.Entry<String, String> entry : options.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith(LIBRARY_OPTION_PREFIX)) {
                if (!FfmTypes.isIdentifier(key.substring(LIBRARY_OPTION_PREFIX.length()))) {
                    throw invalidOption();
                }
                nativeTarget(entry.getValue());
            }
        }
    }

    /** An exact load name without separators, or an absolute path, both passed unchanged. */
    private static Location nativeTarget(String value) {
        if (value.isEmpty() || value.indexOf('\0') >= 0) {
            throw invalidOption();
        }
        if (value.indexOf('/') < 0) {
            return new Location(value, null);
        }
        return new Location(null, requireAbsolute(value));
    }

    private static FfmFailure invalidOption() {
        return new FfmFailure("invalid-host-option", "a library authorization option is malformed");
    }

    private static String requireAbsolute(String path) {
        if (path.indexOf('\0') >= 0 || !path.startsWith("/") || !Path.of(path).isAbsolute()) {
            throw new FfmFailure("invalid-host-option", "a library path option must be absolute");
        }
        return path;
    }
}
