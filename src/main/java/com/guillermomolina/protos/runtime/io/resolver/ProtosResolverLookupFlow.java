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
import com.guillermomolina.protos.runtime.ProtosIoOperation;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosResolverCapabilityValue;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * D223-A1 host-neutral asynchronous flow of one {@code Resolver.lookupAddresses} call.
 *
 * <p>The name is already admitted by the source-backed Resolver protocol. The flow owns one
 * ordinary operation Future (no Task), one backend submission, its cancellation registration and
 * the terminal outcome. Backend threads only hand copied host descriptors to the owning Actor
 * domain through a targeted completion; there the descriptors are validated and deduplicated, the
 * result is projected, and only then is the operation committed and settled. Pre-commit
 * cancellation or close cutover therefore suppresses every late result, and a late or duplicate
 * callback is discarded without guest effect.
 */
public final class ProtosResolverLookupFlow {
    /** The D223-A1 bound on distinct addresses of one successful result. */
    public static final int MAX_ADDRESSES = 64;

    /** Actor-domain projection of validated host data into guest values. */
    public interface Projection {
        /** A fresh frozen Array of canonical frozen IpAddress values, in the given order. */
        Object addresses(ProtosActivation activation, List<ProtosResolverBackend.AddressDescriptor> distinct);

        /** A fresh ResolutionError occurrence of the given kind. */
        ProtosObjectValue resolutionError(
                ProtosActivation activation, ProtosResolverBackend.FailureKind kind);
    }

    private ProtosResolverLookupFlow() {}

    /** Starts one lookup of an admitted lowercase absolute name and answers its Future. */
    public static ProtosFutureValue lookup(
            ProtosResolverCapabilityValue resolver,
            ProtosActivation activation,
            String absoluteName,
            Projection projection) {
        Objects.requireNonNull(resolver, "resolver");
        Objects.requireNonNull(activation, "activation");
        Objects.requireNonNull(absoluteName, "absoluteName");
        Objects.requireNonNull(projection, "projection");

        ProtosIoOperation operation =
                resolver.lifecycleForRuntime(activation).beginOperation(activation);
        if (!operation.future().isPending()) {
            return operation.future();
        }
        Invocation invocation =
                new Invocation(activation.executionDomain(), operation, activation, projection);
        operation.onCancellation(invocation::requestCancellation);

        invocation.enterBackendCall();
        try {
            if (resolver.retiredForRuntime()) {
                // Host authority withdrawn (Process termination or host closure): no new
                // name-service work starts, exactly as a closed host backend refuses it.
                invocation.failed(ProtosResolverBackend.FailureKind.UNAVAILABLE);
                return operation.future();
            }
            invocation.install(resolver.backendForRuntime().lookup(absoluteName, invocation));
        } catch (RuntimeException backendFailure) {
            // A backend must not throw; treat a violation as an unavailable name service.
            invocation.failed(ProtosResolverBackend.FailureKind.UNAVAILABLE);
        } finally {
            invocation.exitBackendCall();
        }
        return operation.future();
    }

    /** Validates raw descriptors: null when the result must fail with LIMIT or NO_ADDRESS. */
    static List<ProtosResolverBackend.AddressDescriptor> distinct(
            List<ProtosResolverBackend.AddressDescriptor> raw) {
        LinkedHashSet<ProtosResolverBackend.AddressDescriptor> unique = new LinkedHashSet<>();
        for (ProtosResolverBackend.AddressDescriptor descriptor : raw) {
            unique.add(Objects.requireNonNull(descriptor, "descriptor"));
        }
        return List.copyOf(new ArrayList<>(unique));
    }

    /** Owns the at-most-once delivery of one backend completion. */
    private static final class Invocation implements ProtosResolverBackend.Completion {
        private final ProtosActorExecutionDomain domain;
        private final ProtosIoOperation operation;
        private final ProtosActivation activation;
        private final Projection projection;
        private final AtomicBoolean completed = new AtomicBoolean();
        private volatile Thread synchronousCaller;
        private ProtosResolverBackend.Cancellation cancellation;
        private boolean cancellationRequested;

        Invocation(
                ProtosActorExecutionDomain domain,
                ProtosIoOperation operation,
                ProtosActivation activation,
                Projection projection) {
            this.domain = domain;
            this.operation = operation;
            this.activation = activation;
            this.projection = projection;
        }

        void enterBackendCall() {
            synchronousCaller = Thread.currentThread();
        }

        void exitBackendCall() {
            synchronousCaller = null;
        }

        @Override
        public void succeeded(List<ProtosResolverBackend.AddressDescriptor> addresses) {
            if (addresses == null) {
                failed(ProtosResolverBackend.FailureKind.UNAVAILABLE);
                return;
            }
            // Host-only copy and bound before any handoff; no guest object is created here.
            if (addresses.size() > ProtosResolverBackend.MAX_RAW_ADDRESSES) {
                failed(ProtosResolverBackend.FailureKind.LIMIT);
                return;
            }
            List<ProtosResolverBackend.AddressDescriptor> unique;
            try {
                unique = distinct(addresses);
            } catch (RuntimeException invalid) {
                failed(ProtosResolverBackend.FailureKind.UNAVAILABLE);
                return;
            }
            if (unique.isEmpty()) {
                failed(ProtosResolverBackend.FailureKind.NO_ADDRESS);
            } else if (unique.size() > MAX_ADDRESSES) {
                // Atomic: never truncated to the first 64.
                failed(ProtosResolverBackend.FailureKind.LIMIT);
            } else {
                deliver(() -> publishAddresses(unique));
            }
        }

        @Override
        public void failed(ProtosResolverBackend.FailureKind kind) {
            ProtosResolverBackend.FailureKind failure =
                    kind == null ? ProtosResolverBackend.FailureKind.UNAVAILABLE : kind;
            deliver(() -> publishFailure(failure));
        }

        private void deliver(java.util.function.BooleanSupplier action) {
            if (!completed.compareAndSet(false, true)) {
                return; // duplicate or late callback
            }
            if (synchronousCaller == Thread.currentThread()) {
                action.getAsBoolean();
                return;
            }
            try {
                domain.enqueueTargetedFutureCompletionForRuntime(operation.future(), action);
            } catch (IllegalStateException terminatingActor) {
                // The terminating Actor keeps its Future termination contract; nothing to release.
            }
        }

        /** Owning Actor domain only. */
        private boolean publishAddresses(List<ProtosResolverBackend.AddressDescriptor> unique) {
            if (operation.terminal()) {
                return false;
            }
            Object array;
            try {
                array = Objects.requireNonNull(
                        projection.addresses(activation, unique), "projected addresses");
            } catch (RuntimeException invalidHostResult) {
                return publishFailure(ProtosResolverBackend.FailureKind.UNAVAILABLE);
            }
            // Commitment happens only now, after validation and projection.
            return operation.commit() && operation.resolve(array);
        }

        /** Owning Actor domain only. */
        private boolean publishFailure(ProtosResolverBackend.FailureKind kind) {
            if (operation.terminal()) {
                return false;
            }
            ProtosObjectValue error = projection.resolutionError(activation, kind);
            return operation.commit() && operation.fail(error);
        }

        void requestCancellation() {
            ProtosResolverBackend.Cancellation toCancel;
            synchronized (this) {
                cancellationRequested = true;
                toCancel = cancellation;
            }
            if (toCancel != null) {
                cancelSafely(toCancel);
            }
        }

        void install(ProtosResolverBackend.Cancellation installed) {
            boolean cancelNow;
            synchronized (this) {
                cancellation = installed;
                cancelNow = cancellationRequested && installed != null;
            }
            if (cancelNow) {
                cancelSafely(installed);
            }
        }

        private static void cancelSafely(ProtosResolverBackend.Cancellation cancellation) {
            try {
                cancellation.cancel();
            } catch (RuntimeException ignored) {
                // Best effort: cancellation never promises to interrupt host name resolution.
            }
        }
    }
}
