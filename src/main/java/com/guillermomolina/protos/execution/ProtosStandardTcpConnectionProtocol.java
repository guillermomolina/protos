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
import com.guillermomolina.protos.runtime.ProtosTcpConnectionValue;
import java.util.List;
import java.util.Objects;

/** D052/PLAT003 shared protocol bridge for live TcpConnection I/O and lifecycle behavior. */
public final class ProtosStandardTcpConnectionProtocol {
    private ProtosStandardTcpConnectionProtocol() {}

    public static void install(ProtosObjectValue prototype) {
        Objects.requireNonNull(prototype, "prototype");
        if (prototype.parent().orElse(null) != ProtosObjectValue.rootObject()) {
            throw new IllegalArgumentException(
                    "standard TcpConnection protocol prototype must delegate directly to Object");
        }
        if (!prototype.isOpen() || !prototype.localSlotsSnapshot().isEmpty()) {
            throw new IllegalStateException(
                    "standard TcpConnection protocol prototype must be fresh and open");
        }

        prototype.createLocalSlot(
                "read",
                ProtosClosureValue.nativeClosure(
                        (activation, supplied) -> {
                            ProtosTcpConnectionValue connection =
                                    requireConnection(activation, supplied, 1, prototype);
                            return connection.readForRuntime(activation, supplied.get(0));
                        }));
        prototype.createLocalSlot(
                "write",
                ProtosClosureValue.nativeClosure(
                        (activation, supplied) -> {
                            ProtosTcpConnectionValue connection =
                                    requireConnection(activation, supplied, 1, prototype);
                            return connection.writeForRuntime(activation, supplied.get(0));
                        }));
        prototype.createLocalSlot(
                "close",
                ProtosClosureValue.nativeClosure(
                        (activation, supplied) ->
                                requireConnection(activation, supplied, 0, prototype)
                                        .closeForRuntime(activation)));
        prototype.createLocalSlot(
                "shutdownRead",
                ProtosClosureValue.nativeClosure(
                        (activation, supplied) ->
                                requireConnection(activation, supplied, 0, prototype)
                                        .shutdownReadForRuntime(activation)));
        prototype.createLocalSlot(
                "shutdownWrite",
                ProtosClosureValue.nativeClosure(
                        (activation, supplied) ->
                                requireConnection(activation, supplied, 0, prototype)
                                        .shutdownWriteForRuntime(activation)));
        prototype.createLocalSlot(
                "localEndpoint",
                ProtosClosureValue.nativeClosure(
                        (activation, supplied) ->
                                endpointSnapshot(
                                        requireEndpointConnection(activation, supplied, prototype),
                                        true,
                                        activation)));
        prototype.createLocalSlot(
                "remoteEndpoint",
                ProtosClosureValue.nativeClosure(
                        (activation, supplied) ->
                                endpointSnapshot(
                                        requireEndpointConnection(activation, supplied, prototype),
                                        false,
                                        activation)));
    }

    private static ProtosTcpConnectionValue requireConnection(
            ProtosActivation activation,
            List<?> supplied,
            int arity,
            ProtosObjectValue prototype) {
        if (supplied.size() != arity
                || !(activation.receiver() instanceof ProtosTcpConnectionValue connection)
                || connection.parent().orElse(null) != prototype
                || !connection.hasProtocolFlowForRuntime()) {
            throw invalid(activation);
        }
        return connection;
    }

    private static ProtosTcpConnectionValue requireEndpointConnection(
            ProtosActivation activation,
            List<?> supplied,
            ProtosObjectValue prototype) {
        ProtosTcpConnectionValue connection =
                requireConnection(activation, supplied, 0, prototype);
        if (!connection.hasEndpointSnapshotsForRuntime()) {
            throw invalid(activation);
        }
        return connection;
    }

    private static ProtosObjectValue endpointSnapshot(
            ProtosTcpConnectionValue connection, boolean local, ProtosActivation activation) {
        ProtosObjectValue endpoint =
                local
                        ? connection.localEndpointForRuntime()
                        : connection.remoteEndpointForRuntime();
        var prelude = activation.prelude().orElseThrow();
        Object endpointBinding = prelude.bindings().readLocalSlot("IpEndpoint").orElse(null);
        Object addressBinding = prelude.bindings().readLocalSlot("IpAddress").orElse(null);
        if (!(endpointBinding instanceof ProtosObjectValue endpointPrototype)
                || !(addressBinding instanceof ProtosObjectValue addressPrototype)
                || !ProtosStandardIpEndpointProtocol.recognizesValue(
                        endpoint, endpointPrototype, addressPrototype)) {
            throw invalid(activation);
        }
        return endpoint;
    }

    private static ProtosSignalException invalid(ProtosActivation activation) {
        return new ProtosSignalException(ProtosCoreErrors.newError(activation));
    }
}
