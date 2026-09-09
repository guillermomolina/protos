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
import com.guillermomolina.protos.runtime.ProtosFutureValue;
import com.guillermomolina.protos.runtime.ProtosNetworkCapabilityValue;
import com.guillermomolina.protos.runtime.ProtosNetworkConnectFlow;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosTcpConnectionValue;
import java.util.List;
import java.util.Objects;

/** D047/PLAT002/PLAT003 standard Network acquisition bridge. */
public final class ProtosStandardNetworkProtocol {
    private ProtosStandardNetworkProtocol() {}

    public static void install(ProtosObjectValue prototype) {
        Objects.requireNonNull(prototype, "prototype");
        if (prototype.parent().orElse(null) != ProtosObjectValue.rootObject()) {
            throw new IllegalArgumentException(
                    "standard Network prototype must delegate directly to Object");
        }
        if (!prototype.isOpen() || !prototype.localSlotsSnapshot().isEmpty()) {
            throw new IllegalStateException(
                    "standard Network prototype must be fresh and open");
        }

        prototype.createLocalSlot(
                "connectTcp",
                ProtosClosureValue.nativeClosure(
                        ProtosStandardNetworkProtocol::connectTcp));
    }

    private static ProtosFutureValue connectTcp(
            ProtosActivation activation, List<?> supplied) {
        ProtosPrelude prelude = activation.prelude().orElseThrow();
        ProtosObjectValue prototype = prelude.networkPrototype();

        if (supplied.size() != 1
                || !(activation.receiver() instanceof ProtosNetworkCapabilityValue network)
                || network.representedDelegationParent(prelude) != prototype) {
            return failedFuture(
                    activation, ProtosCoreErrors.StandardError.INVALID_I_O_ARGUMENT);
        }

        Object endpointValue = supplied.get(0);
        Object endpointBinding = prelude.bindings().readLocalSlot("IpEndpoint").orElse(null);
        Object addressBinding = prelude.bindings().readLocalSlot("IpAddress").orElse(null);
        if (!(endpointValue instanceof ProtosObjectValue endpoint)
                || !(endpointBinding instanceof ProtosObjectValue endpointPrototype)
                || !(addressBinding instanceof ProtosObjectValue addressPrototype)
                || !ProtosStandardIpEndpointProtocol.recognizesValue(
                        endpoint, endpointPrototype, addressPrototype)) {
            return failedFuture(
                    activation, ProtosCoreErrors.StandardError.INVALID_I_O_ARGUMENT);
        }

        Object target = network.authorityTargetForRuntime();
        if (!(target instanceof ProtosNetworkConnectFlow.Backend backend)) {
            return failedFuture(activation, ProtosCoreErrors.StandardError.I_O_ERROR);
        }

        ProtosNetworkConnectFlow flow =
                new ProtosNetworkConnectFlow(
                        network,
                        activation,
                        backend,
                        (materializationActivation,
                                        requestedEndpoint,
                                        resourceState,
                                        localEndpoint,
                                        connectionBackend) ->
                                materializeConnection(
                                        prelude,
                                        materializationActivation,
                                        requestedEndpoint,
                                        resourceState,
                                        localEndpoint,
                                        connectionBackend));
        return flow.connect(activation, endpoint);
    }

    private static ProtosTcpConnectionValue materializeConnection(
            ProtosPrelude prelude,
            ProtosActivation activation,
            ProtosObjectValue requestedEndpoint,
            Object resourceState,
            ProtosObjectValue localEndpoint,
            com.guillermomolina.protos.runtime.ProtosTcpConnectionFlow.Backend connectionBackend) {
        Object endpointBinding = prelude.bindings().readLocalSlot("IpEndpoint").orElse(null);
        Object addressBinding = prelude.bindings().readLocalSlot("IpAddress").orElse(null);
        if (!(endpointBinding instanceof ProtosObjectValue endpointPrototype)
                || !(addressBinding instanceof ProtosObjectValue addressPrototype)
                || !ProtosStandardIpEndpointProtocol.recognizesValue(
                        localEndpoint, endpointPrototype, addressPrototype)) {
            throw new IllegalArgumentException(
                    "backend local endpoint is not a recognized IpEndpoint");
        }
        return new ProtosTcpConnectionValue(
                prelude,
                resourceState,
                activation,
                connectionBackend,
                localEndpoint,
                requestedEndpoint);
    }

    private static ProtosFutureValue failedFuture(
            ProtosActivation activation, ProtosCoreErrors.StandardError error) {
        ProtosFutureValue future =
                new ProtosFutureValue(
                        activation.prelude().orElseThrow().futurePrototype(),
                        activation.executionDomain());
        future.fail(ProtosCoreErrors.newOccurrence(activation, error));
        return future;
    }
}
