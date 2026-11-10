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
 * I144 optional external provider: generic C downcalls through the Java 25 Foreign Function and
 * Memory API, reached as {@code import("ffi:sqlite3")}.
 *
 * <p>The distributable provider accepts exactly one logical library, {@code sqlite3}; its
 * location may be overridden only by the host option {@code sqlite3.path} received through
 * {@link ProtosForeignPluginEnvironment#options()}. Program-supplied file names and paths are
 * never targets, and nothing packaged in the JAR authorizes a library. An authorized library is
 * trusted in-process native code: Protos {@code Filesystem} does not confine it, symbol lookup on
 * its handle may also find symbols of its dependencies, and the provider does not make C
 * memory-safe.
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
