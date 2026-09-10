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
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Host-runtime execution placement for one local Protos Process.
 *
 * <p>This interface is implementation machinery only. It carries no Protos identity or authority
 * and must not be exposed as a Process/Actor/Task value. A Process may be unbound while legacy
 * drivers are still being migrated by I026-A4B3; once bound, the host is fixed for that Process
 * incarnation.
 */
public interface ProtosProcessExecutionHost {
    <T> T callForRuntime(Supplier<T> action);

    default void runForRuntime(Runnable action) {
        Objects.requireNonNull(action, "action");
        callForRuntime(
                () -> {
                    action.run();
                    return null;
                });
    }

    /**
     * Returns the host-owned normal Actor scheduler when this placement provides one.
     *
     * <p>Execution hosts without Actor carrier ownership return empty so staged unhosted and
     * deterministic test paths may retain their explicitly supplied scheduler.
     */
    default Optional<ProtosActorScheduler> actorSchedulerForRuntime() {
        return Optional.empty();
    }

    /** Platform lifecycle notification after semantic Process termination is already complete. */
    void processTerminatedForRuntime();
}
