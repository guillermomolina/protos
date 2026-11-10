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
 * <p>A {@link Policy} fixes, in code, which logical targets a provider class accepts; the only
 * runtime input is the host configuration received through {@link
 * com.guillermomolina.protos.spi.foreign.ProtosForeignPluginEnvironment#options()} (set with
 * {@code ProtosForeignProviderConfiguration.withProviderOptions}). Nothing packaged inside the
 * provider JAR authorizes a library, so the same JAR serves every host unchanged. A Protos
 * program can never name a file, a path, or an additional library.
 */
final class FfmLibraries {
    static final String SQLITE3 = "sqlite3";
    static final String SQLITE3_SONAME = "libsqlite3.so.0";

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
     * The distributable provider: only {@code sqlite3}, by the fixed loader name {@code
     * libsqlite3.so.0}, or by the absolute path in host option {@code sqlite3.path}.
     */
    static final Policy DISTRIBUTABLE =
            new Policy() {
                @Override
                public boolean isTarget(String target) {
                    return SQLITE3.equals(target);
                }

                @Override
                public Location resolve(String target, Map<String, String> options) {
                    if (!isTarget(target)) {
                        throw unauthorized();
                    }
                    String path = options.get("sqlite3.path");
                    return path == null
                            ? new Location(SQLITE3_SONAME, null)
                            : new Location(null, requireAbsolute(path));
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

    private static String requireAbsolute(String path) {
        if (!Path.of(path).isAbsolute()) {
            throw new FfmFailure("invalid-host-option", "a library path option must be absolute");
        }
        return path;
    }
}
