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

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosCoreErrors;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.guillermomolina.protos.runtime.ProtosStringValue;
import com.guillermomolina.protos.runtime.modules.ProtosModuleKey;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleOptions;
import java.util.List;

/**
 * Private runtime facility of {@code std:runtime} (D205, LIB022-A).
 *
 * <p>The public {@code info} is an ordinary Closure of {@code lib/runtime.protos}. This frozen
 * facility is a deferred standard initial member of exactly that module: it is created only when
 * an Actor-local instance of the module initializes, is captured lexically by the module source,
 * and its bootstrap slot is removed, so it is never module surface or a Prelude binding.
 *
 * <ul>
 *   <li>{@code info()} answers a fresh frozen ordinary Object with exactly the four String slots
 *       {@code implementation}, {@code implementationVersion}, {@code engineVersion} and
 *       {@code platform} (D208-A), observed from the currently entered Context at the time of the
 *       call; any other receiver, any argument, or unavailable metadata signals a fresh ordinary
 *       Error.
 * </ul>
 *
 * <p>The facility retains no Context, Engine, language Context, Actor or executable: every
 * observation happens on invocation.
 */
public final class ProtosRuntimeMetadataFacility {
    public static final ProtosModuleKey MODULE_KEY = new ProtosModuleKey("std:runtime");
    public static final String BOOTSTRAP_SLOT = "_runtimeMetadataFacility";

    private static final String IMPLEMENTATION = "Protos";

    private ProtosRuntimeMetadataFacility() {}

    public static ProtosObjectValue createFacility() {
        ProtosObjectValue facility = new ProtosObjectValue(ProtosObjectValue.rootObject());
        facility.createLocalSlot(
                "info",
                ProtosClosureValue.nativeClosure(
                        (activation, supplied) -> info(facility, activation, supplied)));
        return facility.freeze();
    }

    @TruffleBoundary
    private static Object info(
            ProtosObjectValue facility, ProtosActivation activation, List<?> supplied) {
        if (activation.receiver() != facility || !supplied.isEmpty()) {
            throw invalid(activation);
        }
        String implementationVersion;
        String engineVersion;
        try {
            implementationVersion = ProtosVersionIdentity.current();
            engineVersion =
                    org.graalvm.polyglot.Context.getCurrent().getEngine().getVersion();
        } catch (ProtosSignalException signal) {
            throw signal;
        } catch (RuntimeException unavailable) {
            // No entered Context or a failed host metadata source: an ordinary Error, never a
            // fabricated value. Errors (fatal VM conditions) propagate unchanged.
            throw invalid(activation);
        }
        String platform = TruffleOptions.AOT ? "NATIVE_IMAGE" : "JVM";
        if (isBlank(implementationVersion) || isBlank(engineVersion)) {
            throw invalid(activation);
        }
        ProtosObjectValue snapshot = new ProtosObjectValue(ProtosObjectValue.rootObject());
        snapshot.createLocalSlot("implementation", new ProtosStringValue(IMPLEMENTATION));
        snapshot.createLocalSlot(
                "implementationVersion", new ProtosStringValue(implementationVersion));
        snapshot.createLocalSlot("engineVersion", new ProtosStringValue(engineVersion));
        snapshot.createLocalSlot("platform", new ProtosStringValue(platform));
        snapshot.freeze();
        return snapshot;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static ProtosSignalException invalid(ProtosActivation activation) {
        return new ProtosSignalException(ProtosCoreErrors.newError(activation));
    }
}
