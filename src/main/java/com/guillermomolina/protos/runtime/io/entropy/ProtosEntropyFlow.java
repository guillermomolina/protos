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
import com.guillermomolina.protos.runtime.ProtosBytesValue;
import com.guillermomolina.protos.runtime.ProtosCoreErrors;
import com.guillermomolina.protos.runtime.ProtosFutureValue;
import com.guillermomolina.protos.runtime.ProtosIoOperation;
import com.guillermomolina.protos.runtime.ProtosNumericValueSupport;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosEntropyCapabilityValue;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

/**
 * D243-C asynchronous entropy operation.
 *
 * <p>Only host byte arrays cross from backend to the targeted Actor
 * completion. Protos Bytes and IOError occurrences are built inside
 * the owning Actor after admission and before irreversible commitment.
 */
public final class ProtosEntropyFlow {
    private ProtosEntropyFlow() {}

    public static ProtosFutureValue generate(
            ProtosEntropyCapabilityValue entropy,
            ProtosActivation activation,
            int count) {
        Objects.requireNonNull(entropy, "entropy");
        Objects.requireNonNull(activation, "activation");

        ProtosIoOperation operation =
                entropy.lifecycleForRuntime(activation)
                        .beginOperation(activation);

        if (!operation.future().isPending()) {
            return operation.future();
        }

        Invocation invocation = new Invocation(
                entropy,
                activation.executionDomain(),
                operation,
                activation,
                count);

        operation.onCancellation(invocation::requestCancellation);

        if (entropy.retiredForRuntime()) {
            invocation.failImmediately();
            return operation.future();
        }

        if (count == 0) {
            // No backend call, worker creation or SecureRandom initialization.
            invocation.completeEmptyImmediately();
            return operation.future();
        }

        invocation.enterBackendCall();
        try {
            if (entropy.retiredForRuntime()) {
                invocation.failed();
            } else {
                invocation.install(
                        entropy.backendForRuntime()
                                .generate(count, invocation));
            }
        } catch (RuntimeException unavailable) {
            invocation.failed();
        } finally {
            invocation.exitBackendCall();
        }

        return operation.future();
    }

    /** Owns one cancellable, at-most-once backend completion. */
    private static final class Invocation
            implements ProtosEntropyBackend.Completion {
        private final ProtosEntropyCapabilityValue entropy;
        private final ProtosActorExecutionDomain domain;
        private final ProtosIoOperation operation;
        private final ProtosActivation activation;
        private final int expectedCount;
        private final AtomicBoolean completed = new AtomicBoolean();

        private volatile Thread synchronousCaller;
        private ProtosEntropyBackend.Cancellation cancellation;
        private boolean cancellationRequested;

        Invocation(
                ProtosEntropyCapabilityValue entropy,
                ProtosActorExecutionDomain domain,
                ProtosIoOperation operation,
                ProtosActivation activation,
                int expectedCount) {
            this.entropy = entropy;
            this.domain = domain;
            this.operation = operation;
            this.activation = activation;
            this.expectedCount = expectedCount;
        }

        void enterBackendCall() {
            synchronousCaller = Thread.currentThread();
        }

        void exitBackendCall() {
            synchronousCaller = null;
        }

        void completeEmptyImmediately() {
            if (completed.compareAndSet(false, true)) {
                publishBytes(new byte[0]);
            }
        }

        void failImmediately() {
            if (completed.compareAndSet(false, true)) {
                publishFailure();
            }
        }

        @Override
        public void succeeded(byte[] octets) {
            if (octets == null || octets.length != expectedCount) {
                failed();
                return;
            }

            // The source cannot mutate the delivered buffer after this copy.
            byte[] independent = octets.clone();
            deliver(() -> publishBytes(independent));
        }

        @Override
        public void failed() {
            deliver(this::publishFailure);
        }

        private void deliver(BooleanSupplier action) {
            if (!completed.compareAndSet(false, true)) {
                return;
            }

            if (synchronousCaller == Thread.currentThread()) {
                action.getAsBoolean();
                return;
            }

            try {
                domain.enqueueTargetedFutureCompletionForRuntime(
                        operation.future(), action);
            } catch (IllegalStateException terminatingActor) {
                // Actor termination owns the pending Future outcome.
            }
        }

        /** Executed only in the owning Actor execution domain. */
        private boolean publishBytes(byte[] octets) {
            if (operation.terminal() || entropy.retiredForRuntime()) {
                return false;
            }

            if (octets.length != expectedCount) {
                return publishFailure();
            }

            ProtosBytesValue bytes;
            try {
                bytes = new ProtosBytesValue(
                        activation.prelude()
                                .orElseThrow()
                                .bytesPrototypeForRuntime());

                for (byte octet : octets) {
                    bytes.indexedAdd(
                            ProtosNumericValueSupport.integer(
                                    Byte.toUnsignedInt(octet)));
                }
            } catch (RuntimeException invalidHostResult) {
                return publishFailure();
            }

            // The complete, mutable Bytes value exists before commit.
            return operation.commit() && operation.resolve(bytes);
        }

        /** Executed only in the owning Actor execution domain. */
        private boolean publishFailure() {
            if (operation.terminal()) {
                return false;
            }

            ProtosObjectValue error = ProtosCoreErrors.newOccurrence(
                    activation,
                    ProtosCoreErrors.StandardError.I_O_ERROR);

            return operation.commit() && operation.fail(error);
        }

        void requestCancellation() {
            ProtosEntropyBackend.Cancellation toCancel;
            synchronized (this) {
                cancellationRequested = true;
                toCancel = cancellation;
            }
            if (toCancel != null) {
                cancelSafely(toCancel);
            }
        }

        void install(ProtosEntropyBackend.Cancellation installed) {
            boolean cancelNow;
            synchronized (this) {
                cancellation = installed;
                cancelNow = cancellationRequested && installed != null;
            }
            if (cancelNow) {
                cancelSafely(installed);
            }
        }

        private static void cancelSafely(
                ProtosEntropyBackend.Cancellation token) {
            try {
                token.cancel();
            } catch (RuntimeException ignored) {
                // Cancellation cannot fail or change a terminal outcome.
            }
        }
    }
}
