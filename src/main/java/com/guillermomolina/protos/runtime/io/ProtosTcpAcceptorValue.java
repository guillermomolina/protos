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
import com.guillermomolina.protos.runtime.ProtosPrelude;
import java.util.Objects;

/**
 * Ordinary-object JVM representation of one TcpAcceptor capability (D217, I125-A).
 *
 * <p>The accept authority (the shared listener flow) and the owning Actor domain stay outside
 * ordinary Protos slots. Every acceptor derived from one TcpListener shares that listener's single
 * flow, backend and lifecycle; the inherited structural {@code Object.close()} never touches them.
 */
public final class ProtosTcpAcceptorValue extends ProtosObjectValue {
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
        return flow.accept(activation);
    }

    /** Shared listener flow, reserved for runtime infrastructure (I125-B); grants nothing itself. */
    ProtosTcpListenerFlow flowForRuntime() {
        return flow;
    }
}
