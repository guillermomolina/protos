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

import java.math.BigInteger;
import java.util.Objects;

/** D047/PLAT003 host-neutral asynchronous Network.listenTcp acquisition flow. */
public final class ProtosNetworkListenFlow {
    @FunctionalInterface
    public interface Cancellation { void cancel(); }

    /** Immutable execution-time capture of the validated three-slot listen request. */
    public record ListenRequest(int ipVersion, ProtosObjectValue addressConstraint, BigInteger portConstraint) {
        public ListenRequest {
            if (ipVersion != 4 && ipVersion != 6) {
                throw new IllegalArgumentException("listen ipVersion must be 4 or 6");
            }
            if (portConstraint != null
                    && (portConstraint.signum() <= 0
                            || portConstraint.compareTo(BigInteger.valueOf(65535)) > 0)) {
                throw new IllegalArgumentException("listen port constraint must be in 1..65535");
            }
        }
        public boolean hasAddressConstraint() { return addressConstraint != null; }
        public boolean hasPortConstraint() { return portConstraint != null; }
    }

    public interface ListenCompletion {
        void succeeded(
                Object resourceState,
                BigInteger localPort,
                ProtosTcpListenerFlow.Backend listenerBackend,
                Runnable releaseIfUntransferred);
        void failed();
    }

    /** Backend-neutral authority contract. I028-E may implement it with any concrete transport. */
    @FunctionalInterface
    public interface Backend {
        Cancellation listen(ListenRequest request, ListenCompletion completion);
    }

    @FunctionalInterface
    public interface ResultMaterializer {
        ProtosTcpListenerValue materialize(
                ProtosActivation activation,
                ListenRequest request,
                Object resourceState,
                BigInteger localPort,
                ProtosTcpListenerFlow.Backend listenerBackend);
    }

    private final ProtosActorExecutionDomain domain;
    private final Backend backend;
    private final ResultMaterializer materializer;
    private final ProtosIoLifecycle operationLifecycle;

    public ProtosNetworkListenFlow(
            Object networkCapability,
            ProtosActivation constructionActivation,
            Backend backend,
            ResultMaterializer materializer) {
        Objects.requireNonNull(networkCapability, "networkCapability");
        Objects.requireNonNull(constructionActivation, "constructionActivation");
        this.domain = constructionActivation.executionDomain();
        this.backend = Objects.requireNonNull(backend, "backend");
        this.materializer = Objects.requireNonNull(materializer, "materializer");
        this.operationLifecycle =
                new ProtosIoLifecycle(
                        networkCapability,
                        constructionActivation.prelude().orElseThrow().futurePrototype(),
                        domain,
                        completion -> completion.succeeded());
    }

    public ProtosFutureValue listen(ProtosActivation activation, ListenRequest request) {
        Objects.requireNonNull(activation, "activation");
        Objects.requireNonNull(request, "request");
        requireDomain(activation);

        ProtosIoOperation operation = operationLifecycle.beginOperation(activation);
        if (!operation.future().isPending()) return operation.future();

        CancellationBridge bridge = new CancellationBridge();
        operation.onCancellation(bridge::requestCancellation);
        ListenCompletion completion = new ListenCompletion() {
            @Override
            public void succeeded(
                    Object resourceState,
                    BigInteger localPort,
                    ProtosTcpListenerFlow.Backend listenerBackend,
                    Runnable releaseIfUntransferred) {
                Objects.requireNonNull(releaseIfUntransferred, "releaseIfUntransferred");
                if (!operation.commit()) {
                    releaseSafely(releaseIfUntransferred);
                    return;
                }
                ProtosTcpListenerValue listener;
                try {
                    listener = Objects.requireNonNull(
                            materializer.materialize(
                                    activation,
                                    request,
                                    Objects.requireNonNull(resourceState, "resourceState"),
                                    Objects.requireNonNull(localPort, "localPort"),
                                    Objects.requireNonNull(listenerBackend, "listenerBackend")),
                            "materialized listener");
                } catch (RuntimeException invalidBackendDescriptor) {
                    releaseSafely(releaseIfUntransferred);
                    operation.fail(ioError(activation));
                    return;
                }
                if (!operation.resolve(listener)) releaseSafely(releaseIfUntransferred);
            }

            @Override public void failed() { operation.fail(ioError(activation)); }
        };

        try { bridge.install(backend.listen(request, completion)); }
        catch (RuntimeException backendFailure) { operation.fail(ioError(activation)); }
        return operation.future();
    }

    private void requireDomain(ProtosActivation activation) {
        if (activation.executionDomain() != domain) {
            throw new IllegalArgumentException("Network listen flow belongs to another Actor domain");
        }
    }

    private static ProtosObjectValue ioError(ProtosActivation activation) {
        return ProtosCoreErrors.newOccurrence(activation, ProtosCoreErrors.StandardError.I_O_ERROR);
    }

    private static void releaseSafely(Runnable release) {
        try { release.run(); }
        catch (RuntimeException ignored) { /* untransferred custody cannot create a second outcome */ }
    }

    private static final class CancellationBridge {
        private Cancellation cancellation;
        private boolean cancellationRequested;
        void requestCancellation() {
            Cancellation toCancel;
            synchronized (this) { cancellationRequested = true; toCancel = cancellation; }
            if (toCancel != null) toCancel.cancel();
        }
        void install(Cancellation installed) {
            boolean cancelNow;
            synchronized (this) { cancellation = installed; cancelNow = cancellationRequested && installed != null; }
            if (cancelNow) installed.cancel();
        }
    }
}
