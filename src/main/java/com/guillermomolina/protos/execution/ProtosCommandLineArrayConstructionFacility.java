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
import com.guillermomolina.protos.runtime.ProtosArrayValue;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosCoreErrors;
import com.guillermomolina.protos.runtime.ProtosModuleKey;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Private linear Array construction for {@code std:cli/CommandLine} (AUD006-A3).
 *
 * <p>Following the D101 pattern, command-line policy stays in Protos; this facility owns only the
 * irreducible accumulation mechanics. The frozen, stateless factory is provisioned as a standard
 * initial member of the exact {@code std:cli/CommandLine} module, which captures it lexically and
 * removes the bootstrap slot during initialization, so it never belongs to the published module
 * surface.
 *
 * <p>Each factory call returns a fresh open builder whose mutable state is invocation-local. N
 * appends cost amortized O(1) each without materializing any intermediate Array, and
 * {@code finish()} materializes exactly one ordinary standard Array in O(N). The accumulated
 * buffer is copied once into that Array; it is never adopted as the Array's storage. Builders
 * hold native Closures and are therefore non-transferable.
 */
public final class ProtosCommandLineArrayConstructionFacility {
    public static final ProtosModuleKey MODULE_KEY =
            new ProtosModuleKey("std:cli/CommandLine");
    public static final String BOOTSTRAP_SLOT = "_commandLineArrayBuilderFactory";

    private ProtosCommandLineArrayConstructionFacility() {}

    /** Creates the frozen, stateless builder factory. */
    public static ProtosObjectValue createFactory() {
        ProtosObjectValue factory = new ProtosObjectValue(ProtosObjectValue.rootObject());
        factory.createLocalSlot(
                "call",
                ProtosClosureValue.nativeClosure(
                        (activation, supplied) -> newBuilder(activation, supplied)));
        return factory.freeze();
    }

    @com.oracle.truffle.api.CompilerDirectives.TruffleBoundary
    private static Object newBuilder(ProtosActivation activation, List<?> supplied) {
        if (!supplied.isEmpty()) {
            throw invalid(activation);
        }
        BuilderState state = new BuilderState();
        ProtosObjectValue builder = new ProtosObjectValue(ProtosObjectValue.rootObject());
        builder.createLocalSlot(
                "append",
                ProtosClosureValue.nativeClosure(
                        (callActivation, values) -> {
                            if (values.size() != 1
                                    || callActivation.receiver() != builder
                                    || !state.append(values.get(0))) {
                                throw invalid(callActivation);
                            }
                            return builder;
                        }));
        builder.createLocalSlot(
                "finish",
                ProtosClosureValue.nativeClosure(
                        (callActivation, values) -> {
                            if (!values.isEmpty() || callActivation.receiver() != builder) {
                                throw invalid(callActivation);
                            }
                            ProtosArrayValue result = state.finish(prelude(callActivation));
                            if (result == null) {
                                throw invalid(callActivation);
                            }
                            return result;
                        }));
        return builder;
    }

    private static ProtosPrelude prelude(ProtosActivation activation) {
        return activation
                .prelude()
                .orElseThrow(
                        () ->
                                new IllegalStateException(
                                        "CommandLine Array construction requires caller Core prelude"));
    }

    private static ProtosSignalException invalid(ProtosActivation activation) {
        return new ProtosSignalException(ProtosCoreErrors.newError(activation));
    }

    /** Invocation-local accumulation state of exactly one builder: OPEN until finished. */
    static final class BuilderState {
        private ArrayList<Object> values = new ArrayList<>();
        private int finalMaterializations;

        /** Appends the exact value; returns false when the builder is already CONSUMED. */
        @com.oracle.truffle.api.CompilerDirectives.TruffleBoundary
        boolean append(Object value) {
            if (values == null) {
                return false;
            }
            values.add(Objects.requireNonNull(value, "value"));
            return true;
        }

        /**
         * Transitions to CONSUMED and materializes the one result Array; returns null when the
         * builder was already CONSUMED.
         */
        @com.oracle.truffle.api.CompilerDirectives.TruffleBoundary
        ProtosArrayValue finish(ProtosPrelude prelude) {
            ArrayList<Object> accumulated = values;
            if (accumulated == null) {
                return null;
            }
            values = null;
            finalMaterializations++;
            return prelude.newArray(accumulated);
        }

        boolean isConsumed() {
            return values == null;
        }

        int finalMaterializations() {
            return finalMaterializations;
        }
    }
}
