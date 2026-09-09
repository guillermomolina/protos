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

package com.guillermomolina.protos.runtime;

import java.util.Objects;

/**
 * Ordinary-object JVM representation of one live TcpConnection capability.
 *
 * <p>D052 owns the Protos-visible object topology. PLAT003 keeps backend/resource state outside
 * ordinary Protos slots while preserving ordinary local slots, mutation state, delegation and
 * reflection inherited from {@link ProtosObjectValue}. C2 attaches one host-neutral duplex flow only
 * to runtime-created operational connections; standard selectors remain shared on the hidden parent.
 */
public final class ProtosTcpConnectionValue extends ProtosObjectValue {
    private final Object resourceState;
    private final ProtosTcpConnectionFlow flow;

    /** C1-compatible non-operational host representation used before standardized acquisition exists. */
    public ProtosTcpConnectionValue(ProtosPrelude prelude, Object resourceState) {
        super(requirePrototype(prelude));
        this.resourceState = Objects.requireNonNull(resourceState, "resourceState");
        this.flow = null;
    }

    /** Runtime construction path for an acquired/operational TCP connection. */
    public ProtosTcpConnectionValue(
            ProtosPrelude prelude,
            Object resourceState,
            ProtosActivation activation,
            ProtosTcpConnectionFlow.Backend backend) {
        super(requirePrototype(prelude));
        this.resourceState = Objects.requireNonNull(resourceState, "resourceState");
        this.flow =
                new ProtosTcpConnectionFlow(
                        this,
                        Objects.requireNonNull(prelude, "prelude").bytesPrototypeForRuntime(),
                        Objects.requireNonNull(activation, "activation"),
                        Objects.requireNonNull(backend, "backend"));
    }

    /** Opaque host/runtime state. This value is never an ordinary Protos slot. */
    public Object resourceStateForRuntime() {
        return resourceState;
    }

    /** True only for a runtime connection carrying the standardized live protocol state. */
    public boolean hasProtocolFlowForRuntime() {
        return flow != null;
    }

    public ProtosFutureValue readForRuntime(ProtosActivation activation, Object maxBytes) {
        return requireFlow().read(activation, maxBytes);
    }

    public ProtosFutureValue writeForRuntime(ProtosActivation activation, Object bytes) {
        return requireFlow().write(activation, bytes);
    }

    public ProtosFutureValue closeForRuntime(ProtosActivation activation) {
        return requireFlow().close(activation);
    }

    public ProtosFutureValue shutdownReadForRuntime(ProtosActivation activation) {
        return requireFlow().shutdownRead(activation);
    }

    public ProtosFutureValue shutdownWriteForRuntime(ProtosActivation activation) {
        return requireFlow().shutdownWrite(activation);
    }

    private ProtosTcpConnectionFlow requireFlow() {
        if (flow == null) {
            throw new IllegalStateException("TcpConnection has no operational runtime flow");
        }
        return flow;
    }

    private static ProtosObjectValue requirePrototype(ProtosPrelude prelude) {
        return Objects.requireNonNull(prelude, "prelude").tcpConnectionPrototypeForRuntime();
    }
}
