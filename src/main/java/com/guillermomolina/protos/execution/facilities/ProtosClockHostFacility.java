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
import com.guillermomolina.protos.runtime.ProtosClockCapabilityValue;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosCoreErrors;
import com.guillermomolina.protos.runtime.ProtosNumericValueSupport;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosProcessRuntime;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import java.math.BigInteger;
import java.time.Instant;

/** Private synchronous host observation for the source-backed D253-C1 protocol. */
public final class ProtosClockHostFacility {
    public static final String BOOTSTRAP_SLOT = "_coreClockBridge";

    private ProtosClockHostFacility() {}

    public static ProtosObjectValue createBridge() {
        ProtosObjectValue bridge = new ProtosObjectValue(ProtosObjectValue.rootObject());
        bridge.createLocalSlot("read", ProtosClosureValue.nativeClosure((activation, supplied) -> {
            if (activation.receiver() != bridge || supplied.size() != 1) {
                throw failure(activation, ProtosCoreErrors.StandardError.INVALID_I_O_ARGUMENT);
            }
            return read(activation, supplied.get(0));
        }));
        return bridge.freeze();
    }

    private static Object read(ProtosActivation activation, Object receiver) {
        ProtosPrelude prelude = activation.prelude().orElseThrow();
        if (!(receiver instanceof ProtosClockCapabilityValue clock)
                || clock.representedDelegationParent(prelude) != prelude.clockPrototypeForRuntime()) {
            throw failure(activation, ProtosCoreErrors.StandardError.INVALID_I_O_ARGUMENT);
        }
        ProtosProcessRuntime owner = clock.ownerForRuntime().orElse(null);
        if (owner == null
                || activation.executionDomain() != owner.rootActorForRuntime().executionDomain()
                || owner.rootClockForRuntime().orElse(null) != clock) {
            throw failure(activation, ProtosCoreErrors.StandardError.INVALID_I_O_ARGUMENT);
        }
        requireLive(activation, clock, owner);
        Instant sample;
        try {
            // Only this grant's monitor crosses the provider call. Process liveness
            // checks stay outside it so termination cannot invert the lock order.
            synchronized (clock) {
                if (clock.retiredForRuntime()) {
                    throw failure(activation, ProtosCoreErrors.StandardError.I_O_LIFECYCLE_ERROR);
                }
                sample = clock.backendForRuntime().sample();
            }
        } catch (ProtosSignalException selected) {
            throw selected;
        } catch (RuntimeException providerFailure) {
            throw failure(activation, ProtosCoreErrors.StandardError.I_O_ERROR);
        }
        requireLive(activation, clock, owner);
        if (sample == null) {
            throw failure(activation, ProtosCoreErrors.StandardError.I_O_ERROR);
        }
        BigInteger exact = BigInteger.valueOf(sample.getEpochSecond())
                .multiply(BigInteger.valueOf(1_000_000_000L))
                .add(BigInteger.valueOf(sample.getNano()));
        return ProtosNumericValueSupport.integerFromHost(exact, prelude);
    }

    private static void requireLive(ProtosActivation activation,
            ProtosClockCapabilityValue clock, ProtosProcessRuntime owner) {
        if (clock.retiredForRuntime()
                || owner.lifecycleState() != ProtosProcessRuntime.LifecycleState.RUNNING) {
            throw failure(activation, ProtosCoreErrors.StandardError.I_O_LIFECYCLE_ERROR);
        }
    }

    private static ProtosSignalException failure(ProtosActivation activation,
            ProtosCoreErrors.StandardError category) {
        return new ProtosSignalException(ProtosCoreErrors.newOccurrence(activation, category));
    }
}
