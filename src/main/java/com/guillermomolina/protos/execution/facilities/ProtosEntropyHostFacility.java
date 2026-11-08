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
import com.guillermomolina.protos.runtime.ProtosEntropyCapabilityValue;
import com.guillermomolina.protos.runtime.ProtosFutureValue;
import com.guillermomolina.protos.runtime.ProtosIntegerValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.guillermomolina.protos.runtime.callables.ProtosNativeClosureBody;
import com.guillermomolina.protos.runtime.io.ProtosEntropyFlow;
import java.util.List;

/**
 * Private Core bridge for the source-backed D243-C Entropy protocol.
 *
 * <p>Portable arity and semantic Integer recognition live in Entropy.protos.
 * This bridge authenticates the grant and performs only host-specific work.
 */
public final class ProtosEntropyHostFacility {
    public static final String BOOTSTRAP_SLOT = "_coreEntropyBridge";

    private ProtosEntropyHostFacility() {}

    public static ProtosObjectValue createBridge() {
        ProtosObjectValue bridge =
                new ProtosObjectValue(ProtosObjectValue.rootObject());

        operation(bridge, "failed", 1, ProtosHttpAsyncFacility::failed);
        operation(bridge, "generate", 2, ProtosEntropyHostFacility::generate);
        operation(bridge, "close", 1, ProtosEntropyHostFacility::close);

        return bridge.freeze();
    }

    private static void operation(
            ProtosObjectValue receiver,
            String name,
            int arity,
            ProtosNativeClosureBody body) {
        receiver.createLocalSlot(
                name,
                ProtosClosureValue.nativeClosure((activation, supplied) -> {
                    if (activation.receiver() != receiver
                            || supplied.size() != arity) {
                        throw new ProtosSignalException(
                                ProtosCoreErrors.newError(activation));
                    }
                    return body.execute(activation, supplied);
                }));
    }

    private static Object generate(
            ProtosActivation activation, List<?> supplied) {
        ProtosEntropyCapabilityValue entropy =
                grantedOrNull(activation, supplied.get(0));

        if (entropy == null
                || !(supplied.get(1) instanceof ProtosIntegerValue count)) {
            return invalidArgument(activation);
        }

        long requested = count.longValue();
        if (requested < 0
                || requested > com.guillermomolina.protos.runtime.io
                        .ProtosEntropyBackend.MAX_BYTES) {
            return invalidArgument(activation);
        }

        return ProtosEntropyFlow.generate(
                entropy, activation, (int) requested);
    }

    private static Object close(
            ProtosActivation activation, List<?> supplied) {
        ProtosEntropyCapabilityValue entropy =
                grantedOrNull(activation, supplied.get(0));

        if (entropy == null) {
            return invalidArgument(activation);
        }

        return entropy.lifecycleForRuntime(activation).close(activation);
    }

    private static ProtosEntropyCapabilityValue grantedOrNull(
            ProtosActivation activation, Object receiver) {
        ProtosPrelude prelude = activation.prelude().orElseThrow();

        if (!(receiver instanceof ProtosEntropyCapabilityValue entropy)) {
            return null;
        }

        if (entropy.representedDelegationParent(prelude)
                != prelude.entropyPrototypeForRuntime()) {
            return null;
        }

        return entropy;
    }

    private static ProtosFutureValue invalidArgument(
            ProtosActivation activation) {
        ProtosFutureValue future = new ProtosFutureValue(
                activation.prelude().orElseThrow().futurePrototype(),
                activation.executionDomain());

        future.fail(ProtosCoreErrors.newOccurrence(
                activation,
                ProtosCoreErrors.StandardError.INVALID_I_O_ARGUMENT));

        return future;
    }
}
