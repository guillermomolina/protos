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

import java.util.List;

/**
 * Runtime-only communication route for an ActorRef whose physical delivery path is not the direct
 * local Actor admission queue.
 *
 * <p>This SPI is not a Protos-visible transport API. Physical transport selection, addressing,
 * serialization/wire format, retry/backoff, reachability probes, and endpoint discovery remain
 * implementation policy. The only state exposed here is the delivery knowledge required to
 * preserve the already-defined Actor send/request semantics across a transport boundary.
 *
 * <p>The supplied argument vector is the immutable logical snapshot formed at the original
 * language-level send/request invocation. A transport implementation must preserve ordinary
 * Actor pass-by-value semantics and must never expose that Java object graph as cross-Process
 * mutable application state.
 */
public interface ProtosActorTransportRoute {
    enum DeliveryState {
        PENDING,
        ACCEPTED,
        COMPLETED,
        CANCELLED_BEFORE_ACCEPTANCE,
        FAILED_BEFORE_ACCEPTANCE,
        FAILED_AFTER_ACCEPTANCE,
        ACCEPTANCE_UNCERTAIN
    }

    @FunctionalInterface
    interface DeliveryObserver {
        /** Reports the current state at registration and every later transport-owned state change. */
        void stateChanged(DeliveryState state);
    }

    interface Delivery {
        DeliveryState stateForRuntime();

        /** True only when this call establishes known cancellation before Actor acceptance. */
        boolean cancelBeforeAcceptance();

        /**
         * Registers internal routing observation for this transport attempt.
         *
         * <p>The observer receives the current state once during registration and every subsequent
         * transport-owned state transition. Implementations must invoke callbacks outside any
         * transport lock whose re-entry could deadlock Group routing/cancellation machinery.
         */
        void observeForRuntime(DeliveryObserver observer);
    }

    interface RequestObserver {
        /** Supplies one normal result from the destination side; caller-side transfer is rechecked. */
        void reply(Object result);

        /** Reports a terminal failure known to have occurred before concrete Actor acceptance. */
        void failedBeforeAcceptance();

        /** Reports accepted loss or inability to determine whether acceptance occurred. */
        void outcomeUncertain();
    }

    /** Semantic incarnation identity denoted by the ActorRef using this route. */
    long targetIncarnationIdentityForRuntime();

    Delivery beginSend(
            ProtosActorRefValue sender,
            String selector,
            List<Object> logicalSnapshot);

    /**
     * Starts one request transport attempt. The route must eventually report exactly one terminal
     * outcome through {@code observer} when the request does not remain pending: normal reply,
     * known pre-acceptance failure, or outcome uncertainty. Delivery-state transitions and observer
     * callbacks must agree; they are one transport-owned knowledge boundary.
     */
    Delivery beginRequest(
            ProtosActorRefValue sender,
            String selector,
            List<Object> logicalSnapshot,
            RequestObserver observer);
}
