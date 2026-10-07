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

import com.guillermomolina.protos.execution.ProtosInvocation;
import com.guillermomolina.protos.execution.ProtosModuleRuntime;
import java.util.List;
import java.util.Objects;

/**
 * Destination-local materialization authority handed to one
 * {@link ProtosSemanticTransferFamily#materialize} call (PLAT051-A2).
 *
 * <p>It is bound to the destination execution domain and to the family's exact owning Standard
 * Library module. Materialization runs in a dedicated module activation over the destination's own
 * Actor-local module state, so the owning module is resolved, initialized and cached in the
 * destination exactly as an ordinary import there would, and every Closure the family obtains or
 * creates is destination-local. It offers no access to other modules, to the source domain, or to
 * any guest-visible handle; instances never outlive the materialization call that receives them.
 */
public final class ProtosSemanticTransferDestination {
    private final ProtosSemanticTransferFamily family;
    private final ProtosPrelude prelude;
    private final ProtosActivation activation;

    ProtosSemanticTransferDestination(
            ProtosSemanticTransferFamily family, ProtosActivation destination) {
        this.family = Objects.requireNonNull(family, "family");
        Objects.requireNonNull(destination, "destination");
        this.prelude =
                destination.prelude()
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "semantic transfer materialization requires a Core prelude"));
        this.activation =
                prelude.newModuleActivation(
                        destination.actorModuleState(),
                        family.ownerModule(),
                        prelude.newExecutionContext(),
                        destination.executionDomain());
    }

    /** The destination Prelude, for Core prototypes and Core value construction. */
    public ProtosPrelude prelude() {
        return prelude;
    }

    /** The destination execution domain in which materialization runs. */
    public ProtosActorExecutionDomain executionDomain() {
        return activation.executionDomain();
    }

    /**
     * The family's owning Standard Library module instance in the destination's Actor-local module
     * state, loading and initializing it there when this is its first use in that domain.
     */
    public ProtosObjectValue ownerModule() {
        return ProtosModuleRuntime.loadCanonicalModuleThroughPreludeForRuntime(
                family.ownerModule(), activation);
    }

    /** Synchronously invokes a destination-local callable with ordinary call semantics. */
    public Object invoke(Object callable, List<?> arguments) {
        return ProtosInvocation.invoke(callable, arguments, activation);
    }
}
