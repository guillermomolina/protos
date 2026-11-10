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

/**
 * I144 test-only registration of the generic FFM backend: scheme {@code ffi-test}, single target
 * {@code ffi_test}, loaded only from the absolute path the test host supplies as provider option
 * {@code ffi_test.path} through {@code ProtosForeignProviderConfiguration.withProviderOptions}.
 *
 * <p>It is compiled together with {@code providers/ffm} into a test JAR only, never into the
 * distributable provider. It contains no code for any fixture C function: every declaration
 * comes from Protos source through the generic backend.
 */
public final class FfmFixtureProvider implements ProtosForeignProviderPlugin {
    static final String ID = "protos-ffm-fixture";

    private final FfmForeignProvider backend =
            new FfmForeignProvider(ID, "ffi-test", FfmLibraries.fixture("ffi_test"));

    public FfmFixtureProvider() {}

    @Override
    public String providerId() {
        return backend.providerId();
    }

    @Override
    public String scheme() {
        return backend.scheme();
    }

    @Override
    public String canonicalTarget(String target) {
        return backend.canonicalTarget(target);
    }

    @Override
    public ProtosForeignPluginSession openSession(ProtosForeignPluginEnvironment environment) {
        return backend.openSession(environment);
    }

    @Override
    public ProtosForeignValueOperations valueOperations() {
        return backend.valueOperations();
    }
}
