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

import java.util.List;
import java.util.Objects;
import java.util.Optional;

final class ProtosTestResourceAttemptCompletion {
    enum CapacityDisposition { SAFE, UNSAFE }

    private final ProtosExecutionOutcome guestObservation;
    private final List<Throwable> infrastructureFailures;
    private final CapacityDisposition capacityDisposition;

    ProtosTestResourceAttemptCompletion(
            ProtosExecutionOutcome guestObservation,
            List<? extends Throwable> infrastructureFailures,
            CapacityDisposition capacityDisposition) {
        Objects.requireNonNull(infrastructureFailures, "infrastructureFailures");
        this.guestObservation = guestObservation;
        this.infrastructureFailures = List.copyOf(infrastructureFailures);
        for (Throwable failure : this.infrastructureFailures) {
            Objects.requireNonNull(failure, "infrastructure failure");
        }
        this.capacityDisposition =
                Objects.requireNonNull(capacityDisposition, "capacityDisposition");
        if (this.infrastructureFailures.isEmpty()
                && this.capacityDisposition == CapacityDisposition.UNSAFE) {
            throw new IllegalArgumentException(
                    "unsafe capacity disposition requires infrastructure evidence");
        }
    }

    Optional<ProtosExecutionOutcome> guestObservation() {
        return Optional.ofNullable(guestObservation);
    }

    List<Throwable> infrastructureFailures() {
        return infrastructureFailures;
    }

    boolean infrastructureFailed() {
        return !infrastructureFailures.isEmpty();
    }

    CapacityDisposition capacityDisposition() {
        return capacityDisposition;
    }
}
