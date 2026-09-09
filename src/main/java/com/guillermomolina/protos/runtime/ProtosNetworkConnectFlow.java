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

/** D047/PLAT003 host-neutral asynchronous Network.connectTcp acquisition flow. */
public final class ProtosNetworkConnectFlow {
    @FunctionalInterface
    public interface Cancellation {
        void cancel();
    }

    /** Backend completion of one already-recognized endpoint acquisition. */
    public interface ConnectCompletion {
        void succeeded(
                Object resourceState,
                ProtosObjectValue localEndpoint,
                ProtosTcpConnectionFlow.Backend connectionBackend,
                Runnable releaseIfUntransferred);

        void failed();
    }

    /**
     * Authority-target contract consumed by C4. Implementations may use any concrete network
     * mechanism later selected by I028-E; that mechanism is not part of this interface.
     */
    @FunctionalInterface
    public interface Backend {
        Cancellation connect(ProtosObjectValue endpoint, ConnectCompletion completion);
    }

    /** Materializes one acquired backend descriptor into the standard live capability. */
    @FunctionalInterface
    public interface ResultMaterializer {
        ProtosTcpConnectionValue materialize(
                ProtosActivation activation,
                ProtosObjectValue requestedEndpoint,
                Object resourceState,
                ProtosObjectValue localEndpoint,
                ProtosTcpConnectionFlow.Backend connectionBackend);
    }

    private final ProtosActorExecutionDomain domain;
    private final Backend backend;
    private final ResultMaterializer materializer;
    private final ProtosIoLifecycle operationLifecycle;

    public ProtosNetworkConnectFlow(
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

    /** Starts one independent acquisition after all caller-visible endpoint validation has completed. */
    public ProtosFutureValue connect(
            ProtosActivation activation, ProtosObjectValue requestedEndpoint) {
        Objects.requireNonNull(activation, "activation");
        Objects.requireNonNull(requestedEndpoint, "requestedEndpoint");
        requireDomain(activation);

        ProtosIoOperation operation = operationLifecycle.beginOperation(activation);
        if (!operation.future().isPending()) {
            return operation.future();
        }

        CancellationBridge cancellationBridge = new CancellationBridge();
        operation.onCancellation(cancellationBridge::requestCancellation);

        ConnectCompletion completion =
                new ConnectCompletion() {
                    @Override
                    public void succeeded(
                            Object resourceState,
                            ProtosObjectValue localEndpoint,
                            ProtosTcpConnectionFlow.Backend connectionBackend,
                            Runnable releaseIfUntransferred) {
                        Objects.requireNonNull(
                                releaseIfUntransferred, "releaseIfUntransferred");

                        if (!operation.commit()) {
                            releaseSafely(releaseIfUntransferred);
                            return;
                        }

                        ProtosTcpConnectionValue connection;
                        try {
                            connection =
                                    Objects.requireNonNull(
                                            materializer.materialize(
                                                    activation,
                                                    requestedEndpoint,
                                                    Objects.requireNonNull(
                                                            resourceState, "resourceState"),
                                                    Objects.requireNonNull(
                                                            localEndpoint, "localEndpoint"),
                                                    Objects.requireNonNull(
                                                            connectionBackend,
                                                            "connectionBackend")),
                                            "materialized connection");
                        } catch (RuntimeException invalidBackendDescriptor) {
                            releaseSafely(releaseIfUntransferred);
                            operation.fail(ioError(activation));
                            return;
                        }

                        if (!operation.resolve(connection)) {
                            releaseSafely(releaseIfUntransferred);
                        }
                    }

                    @Override
                    public void failed() {
                        operation.fail(ioError(activation));
                    }
                };

        try {
            cancellationBridge.install(backend.connect(requestedEndpoint, completion));
        } catch (RuntimeException backendFailure) {
            operation.fail(ioError(activation));
        }
        return operation.future();
    }

    private void requireDomain(ProtosActivation activation) {
        if (activation.executionDomain() != domain) {
            throw new IllegalArgumentException(
                    "Network connect flow belongs to another Actor domain");
        }
    }

    private static ProtosObjectValue ioError(ProtosActivation activation) {
        return ProtosCoreErrors.newOccurrence(
                activation, ProtosCoreErrors.StandardError.I_O_ERROR);
    }

    private static void releaseSafely(Runnable release) {
        try {
            release.run();
        } catch (RuntimeException ignored) {
            // No untransferred backend resource may become a second portable outcome.
        }
    }

    /** Handles Future cancellation racing backend cancellation-handle registration. */
    private static final class CancellationBridge {
        private Cancellation cancellation;
        private boolean cancellationRequested;

        void requestCancellation() {
            Cancellation toCancel;
            synchronized (this) {
                cancellationRequested = true;
                toCancel = cancellation;
            }
            if (toCancel != null) {
                toCancel.cancel();
            }
        }

        void install(Cancellation installed) {
            boolean cancelNow;
            synchronized (this) {
                cancellation = installed;
                cancelNow = cancellationRequested && installed != null;
            }
            if (cancelNow) {
                installed.cancel();
            }
        }
    }
}
