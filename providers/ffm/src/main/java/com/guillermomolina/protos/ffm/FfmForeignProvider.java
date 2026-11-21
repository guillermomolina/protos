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

import com.guillermomolina.protos.spi.foreign.ProtosForeignPluginEnvironment;
import com.guillermomolina.protos.spi.foreign.ProtosForeignPluginSession;
import com.guillermomolina.protos.spi.foreign.ProtosForeignProviderPlugin;
import com.guillermomolina.protos.spi.foreign.ProtosForeignValueOperations;
import java.util.Objects;

/**
 * Optional external provider: generic C downcalls through the Java 25 Foreign Function and Memory
 * API, reached as {@code import("ffi:<alias>")}.
 *
 * <p>The distributable provider accepts any syntactically valid logical alias, but authorizes no
 * library by default: an alias is usable only when the host supplies the option {@code
 * library.<alias>} through {@link ProtosForeignPluginEnvironment#options()}, whose value is an
 * exact load name or an absolute path passed unchanged to the operating-system loader. A valid
 * alias without that option fails on acquisition with {@code unauthorized-library}. Program
 * imports never name a file, path, or native name, and nothing packaged in the JAR authorizes a
 * library.
 *
 * <p>An authorized library runs {@code TRUSTED_IN_PROCESS}: it may produce every effect the
 * operating-system process can, including file and network access, transitive dependency
 * loading, native initializers, global state shared among Actors, indefinite blocking, memory
 * corruption, and process termination. Protos capabilities do not confine it, symbol lookup on
 * its handle may also find symbols of its dependencies, and there is no native sandbox.
 *
 * <p>Construction, {@link #providerId()}, and {@link #scheme()} load no library, create no
 * {@code Linker}, resolve no symbol, and open no session.
 */
public final class FfmForeignProvider implements ProtosForeignProviderPlugin {
    static final String ID = "protos-ffm";
    static final String SCHEME = "ffi";

    private final String id;
    private final String scheme;
    private final FfmLibraries.Policy policy;

    public FfmForeignProvider() {
        this(ID, SCHEME, FfmLibraries.DISTRIBUTABLE);
    }

    /** Same generic backend under another registration and target policy (test hosts only). */
    FfmForeignProvider(String id, String scheme, FfmLibraries.Policy policy) {
        this.id = Objects.requireNonNull(id, "id");
        this.scheme = Objects.requireNonNull(scheme, "scheme");
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    @Override
    public String providerId() {
        return id;
    }

    @Override
    public String scheme() {
        return scheme;
    }

    @Override
    public String canonicalTarget(String target) {
        if (target == null || !policy.isTarget(target)) {
            throw new IllegalArgumentException("unknown ffi library");
        }
        return target;
    }

    @Override
    public ProtosForeignPluginSession openSession(ProtosForeignPluginEnvironment environment) {
        return new FfmSession(new FfmLibraries.Authorization(policy, environment.options()));
    }

    @Override
    public ProtosForeignValueOperations valueOperations() {
        return new FfmValueOperations();
    }
}
