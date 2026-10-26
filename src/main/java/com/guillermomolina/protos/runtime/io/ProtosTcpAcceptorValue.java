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

package com.guillermomolina.protos.runtime.io;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosActorExecutionDomain;
import com.guillermomolina.protos.runtime.ProtosFutureValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosIoLifecycle;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosProcessRuntime;
import java.util.Objects;

/**
 * Ordinary-object JVM representation of one TcpAcceptor capability (D217, I125-A).
 *
 * <p>The accept authority (the shared listener flow) and the owning Actor domain stay outside
 * ordinary Protos slots. Every acceptor derived from one TcpListener shares that listener's single
 * flow, backend and lifecycle; the inherited structural {@code Object.close()} never touches them.
 */
public final class ProtosTcpAcceptorValue extends ProtosObjectValue {
    /**
     * Host-only inert spawn-delegation descriptor (I125-B). It carries only the shared listener
     * flow and the exact owning Process incarnation: no source domain, activation, slot or
     * structural state. It is not a Protos value and never reaches guest execution.
     */
    public static final class SpawnDelegation {
        private final ProtosTcpListenerFlow flow;
        private final ProtosProcessRuntime process;

        private SpawnDelegation(ProtosTcpListenerFlow flow, ProtosProcessRuntime process) {
            this.flow = flow;
            this.process = process;
        }
    }

    private final ProtosTcpListenerFlow flow;
    private final ProtosActorExecutionDomain domain;

    /** Runtime derivation path; creates no backend, listener or network resource. */
    ProtosTcpAcceptorValue(
            ProtosPrelude prelude,
            ProtosTcpListenerFlow flow,
            ProtosActivation activation) {
        super(Objects.requireNonNull(prelude, "prelude").tcpAcceptorPrototypeForRuntime());
        this.flow = Objects.requireNonNull(flow, "flow");
        this.domain = Objects.requireNonNull(activation, "activation").executionDomain();
    }

    /** Side-effect-free owning-domain test used by the native protocol. */
    public boolean ownedByForRuntime(ProtosActivation activation) {
        return activation != null && activation.executionDomain() == domain;
    }

    public ProtosFutureValue acceptForRuntime(ProtosActivation activation) {
        if (!ownedByForRuntime(activation)) {
            throw new IllegalArgumentException("TcpAcceptor operation belongs to another Actor domain");
        }
        return flow.acceptDelegatedForRuntime(activation);
    }

    /**
     * Actor.spawn source stage (D217, I125-B): extracts this capability's accept authority into a
     * fresh inert descriptor. Reads no slot or structural state, performs no I/O and never mutates
     * this capability; a domain or Process mismatch is rejected for the snapshot boundary.
     */
    public SpawnDelegation prepareSpawnDelegationForRuntime(ProtosActivation source) {
        if (!ownedByForRuntime(source)) {
            throw new IllegalArgumentException("TcpAcceptor delegation belongs to another Actor domain");
        }
        if (domain.currentActorForRuntime().isEmpty()) {
            throw new IllegalArgumentException("TcpAcceptor delegation requires a source Actor");
        }
        ProtosProcessRuntime process =
                ProtosIoLifecycle.hostingProcessForRuntime(domain)
                        .orElseThrow(
                                () -> new IllegalArgumentException(
                                        "TcpAcceptor delegation requires a hosting Process"));
        if (!flow.ownedByProcessForRuntime(process)) {
            throw new IllegalArgumentException("TcpAcceptor delegation crosses a Process boundary");
        }
        return new SpawnDelegation(flow, process);
    }

    /**
     * Actor.spawn destination stage (D217, I125-B): one fresh OPEN, slotless capability parented
     * directly by the destination Prelude's canonical TcpAcceptor prototype, owned by the
     * destination domain and sharing the original listener flow. Fails closed on Process mismatch.
     */
    public static ProtosTcpAcceptorValue materializeSpawnDelegationForRuntime(
            SpawnDelegation descriptor, ProtosActivation destination) {
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(destination, "destination");
        if (destination.executionDomain().currentActorForRuntime().isEmpty()) {
            throw new IllegalStateException("TcpAcceptor delegation requires a destination Actor");
        }
        ProtosProcessRuntime process =
                ProtosIoLifecycle.hostingProcessForRuntime(destination.executionDomain()).orElse(null);
        if (process != descriptor.process || !descriptor.flow.ownedByProcessForRuntime(process)) {
            throw new IllegalStateException("TcpAcceptor delegation crosses a Process boundary");
        }
        ProtosPrelude prelude =
                destination.prelude()
                        .orElseThrow(
                                () -> new IllegalStateException(
                                        "TcpAcceptor delegation requires an owning Core prelude"));
        return new ProtosTcpAcceptorValue(prelude, descriptor.flow, destination);
    }

    /** Shared listener flow, reserved for runtime infrastructure (I125-B); grants nothing itself. */
    ProtosTcpListenerFlow flowForRuntime() {
        return flow;
    }
}
