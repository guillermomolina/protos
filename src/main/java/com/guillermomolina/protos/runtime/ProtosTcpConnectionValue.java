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
 * reflection inherited from {@link ProtosObjectValue}. C1 intentionally installs no TCP behavior;
 * later I028-C slices attach the already-ratified protocol to the shared runtime-only parent.
 */
public final class ProtosTcpConnectionValue extends ProtosObjectValue {
    private final Object resourceState;

    public ProtosTcpConnectionValue(ProtosPrelude prelude, Object resourceState) {
        super(requirePrototype(prelude));
        this.resourceState = Objects.requireNonNull(resourceState, "resourceState");
    }

    /** Opaque host/runtime state. This value is never an ordinary Protos slot. */
    public Object resourceStateForRuntime() {
        return resourceState;
    }

    private static ProtosObjectValue requirePrototype(ProtosPrelude prelude) {
        return Objects.requireNonNull(prelude, "prelude").tcpConnectionPrototypeForRuntime();
    }
}
