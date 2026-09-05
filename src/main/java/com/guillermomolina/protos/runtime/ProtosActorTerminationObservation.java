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

/** Runtime registration behind one ActorRef.termination() observation Future. */
final class ProtosActorTerminationObservation {
    private final ProtosActor target;
    private final ProtosActorRefValue observedReference;
    private final ProtosActorExecutionDomain observerDomain;
    private final ProtosFutureValue future;

    private ProtosActorTerminationObservation(
            ProtosActor target,
            ProtosActorRefValue observedReference,
            ProtosActorExecutionDomain observerDomain,
            ProtosFutureValue future) {
        this.target = Objects.requireNonNull(target, "target");
        this.observedReference = Objects.requireNonNull(observedReference, "observedReference");
        this.observerDomain = Objects.requireNonNull(observerDomain, "observerDomain");
        this.future = Objects.requireNonNull(future, "future");
    }

    static ProtosFutureValue begin(
            ProtosActorRefValue observedReference,
            ProtosObjectValue futurePrototype,
            ProtosActorExecutionDomain observerDomain) {
        Objects.requireNonNull(observedReference, "observedReference");
        Objects.requireNonNull(futurePrototype, "futurePrototype");
        Objects.requireNonNull(observerDomain, "observerDomain");
        ProtosActor target = observedReference.localActorForRuntime();
        ProtosFutureValue future = new ProtosFutureValue(futurePrototype, observerDomain);
        ProtosActorTerminationObservation observation =
                new ProtosActorTerminationObservation(
                        target, observedReference, observerDomain, future);
        future.attachCancellationProducer(observation::cancellationRequested);
        future.observe(ignored -> observation.terminalized());

        if (!target.registerTerminationObservationForRuntime(observation)) {
            observation.targetTerminated();
        }
        observerDomain.registerActorNonTaskFuture(future);
        return future;
    }

    void targetTerminated() {
        future.resolveRuntimeValue(observedReference);
    }

    private void cancellationRequested() {
        future.cancelTerminal();
    }

    private void terminalized() {
        target.unregisterTerminationObservationForRuntime(this);
        observerDomain.terminalActorNonTaskFuture(future);
    }
}
