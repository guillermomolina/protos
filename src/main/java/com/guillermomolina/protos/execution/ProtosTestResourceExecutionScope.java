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
 * the specific language governing rights and limitations under the LICENSE.
 */

package com.guillermomolina.protos.execution;

import com.guillermomolina.protos.runtime.ProtosActivation;
import java.util.Objects;

/**
 * Production host-bootstrap ownership scope for D113 Test Tool resource execution.
 *
 * <p>This is the deliberately narrow public host facade above the private D107/D108 provider
 * registry and C1/C2 resourceful execution facilities. The baseline local Test Tool explicitly
 * installs one immutable empty provider environment. That is a valid D113 environment: resource-free
 * work remains pay-for-use and any resourceful attempt whose logical provider is not explicitly
 * registered fails closed as infrastructure evidence before guest Process creation.
 *
 * <p>The facade intentionally does not make ProviderAdapter, ProviderLease, provider configuration,
 * credentials or registry mutation guest/tool-visible. Future concrete provider support may add an
 * explicit host-side configured construction path below this same facade without introducing
 * discovery or changing D098 logical provider/profile identity.
 */
public final class ProtosTestResourceExecutionScope implements AutoCloseable {

    private final ProtosTestResourcefulExecutionFacility ordinary;
    private final ProtosTestResourcefulExecutionFacility inspection;
    private boolean closed;

    private ProtosTestResourceExecutionScope(
            ProtosTestResourcefulExecutionFacility ordinary,
            ProtosTestResourcefulExecutionFacility inspection) {
        this.ordinary = Objects.requireNonNull(ordinary, "ordinary");
        this.inspection = Objects.requireNonNull(inspection, "inspection");
    }

    /**
     * Installs the D113 baseline environment: one explicit immutable empty provider registry.
     *
     * <p>The two resulting bootstrap-local routes are the already-closed C1/C2 mechanisms:
     * {@code resourceExecutionAsync} and {@code resourceExecutionInspectAsync}.
     */
    public static ProtosTestResourceExecutionScope installEmpty(
            ProtosActivation activation,
            ProtosPolyglotRuntimeHost runtimeHost,
            ProtosAsyncExactExecutionFacility.Submission submission) {
        Objects.requireNonNull(activation, "activation");
        Objects.requireNonNull(runtimeHost, "runtimeHost");
        Objects.requireNonNull(submission, "submission");

        ProtosTestResourceProviderRegistry registry =
                ProtosTestResourceProviderRegistry.empty();

        ProtosTestResourcefulExecutionFacility ordinary = null;
        ProtosTestResourcefulExecutionFacility inspection = null;
        try {
            ordinary =
                    ProtosTestResourcefulExecutionFacility.install(
                            activation,
                            registry,
                            runtimeHost,
                            submission);
            inspection =
                    ProtosTestResourcefulExecutionFacility.installInspection(
                            activation,
                            registry,
                            runtimeHost,
                            submission);
            return new ProtosTestResourceExecutionScope(ordinary, inspection);
        } catch (RuntimeException | Error failure) {
            if (inspection != null) {
                inspection.close();
            }
            if (ordinary != null) {
                ordinary.close();
            }
            throw failure;
        }
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;

        // Both facilities share the same immutable registry and host Submission but own independent
        // outstanding-operation sets. Submission lifetime remains owned by the outer CLI scope.
        inspection.close();
        ordinary.close();
    }
}
