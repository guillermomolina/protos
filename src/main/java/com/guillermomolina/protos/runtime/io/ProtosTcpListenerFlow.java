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
import com.guillermomolina.protos.runtime.ProtosCoreErrors;
import com.guillermomolina.protos.runtime.ProtosFutureValue;
import com.guillermomolina.protos.runtime.ProtosIoLifecycle;
import com.guillermomolina.protos.runtime.ProtosIoOperation;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosProcessRuntime;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.guillermomolina.protos.runtime.ProtosTcpConnectionFlow;
import com.guillermomolina.protos.runtime.io.ProtosHostIpEndpoint;
import com.guillermomolina.protos.runtime.io.ProtosTcpConnectionValue;
import com.guillermomolina.protos.runtime.io.ProtosTcpListenerValue;
import java.util.Objects;

/** Host-neutral concurrent-accept and Closable lifecycle state for one TcpListener. */
public final class ProtosTcpListenerFlow {
    @FunctionalInterface
    public interface Cancellation { void cancel(); }

    public interface AcceptCompletion {
        void succeeded(
                Object resourceState,
                ProtosHostIpEndpoint localEndpoint,
                ProtosHostIpEndpoint remoteEndpoint,
                ProtosTcpConnectionFlow.Backend connectionBackend,
                Runnable releaseIfUntransferred);
        void failed();
    }

    @FunctionalInterface
    public interface Backend {
        void close(CloseCompletion completion);
        default Cancellation accept(AcceptCompletion completion) {
            throw new UnsupportedOperationException("TCP accept backend is not available");
        }
    }

    public interface CloseCompletion { void succeeded(); void failed(); }

    @FunctionalInterface
    public interface ResultMaterializer {
        ProtosTcpConnectionValue materialize(
                ProtosActivation activation,
                Object resourceState,
                ProtosHostIpEndpoint localEndpoint,
                ProtosHostIpEndpoint remoteEndpoint,
                ProtosTcpConnectionFlow.Backend connectionBackend);
    }

    private final ProtosTcpListenerValue receiver;
    private final ProtosActorExecutionDomain domain;
    private final Backend backend;
    private final ResultMaterializer resultMaterializer;
    private final ProtosIoLifecycle lifecycle;
    private volatile ProtosActivation closeActivation;

    public ProtosTcpListenerFlow(
            ProtosTcpListenerValue receiver,
            ProtosActivation activation,
            Backend backend) {
        this(receiver, activation, backend, null);
    }

    public ProtosTcpListenerFlow(
            ProtosTcpListenerValue receiver,
            ProtosActivation activation,
            Backend backend,
            ResultMaterializer resultMaterializer) {
        this.receiver=Objects.requireNonNull(receiver,"receiver");
        Objects.requireNonNull(activation,"activation");
        this.domain=activation.executionDomain();
        this.backend=Objects.requireNonNull(backend,"backend");
        this.resultMaterializer=resultMaterializer;
        this.lifecycle=ProtosIoLifecycle.withOwnerDomainPublicationForRuntime(receiver,activation.prelude().orElseThrow().futurePrototype(),domain,this::startCloseRelease);
    }

    public boolean acceptsForRuntime() { return resultMaterializer != null; }

    public ProtosFutureValue accept(ProtosActivation activation) {
        requireDomain(activation);
        if(resultMaterializer==null) throw new IllegalStateException("TcpListener has no accept materializer");
        return startAccept(activation, lifecycle.beginOperation(activation));
    }

    /**
     * I125-B delegated TcpAcceptor accept (D217). The accepting Actor must share this listener's
     * exact Process incarnation; the operation, its Future and the accepted connection all belong
     * to the accepting Actor while the backend, queue and lifecycle stay this listener's. The
     * listener-owner domain itself crosses no Actor boundary and keeps its I125-A admission.
     */
    public ProtosFutureValue acceptDelegatedForRuntime(ProtosActivation activation) {
        Objects.requireNonNull(activation,"activation");
        if(activation.executionDomain()!=domain) {
            ProtosProcessRuntime acceptingProcess =
                    ProtosIoLifecycle.hostingProcessForRuntime(activation.executionDomain()).orElse(null);
            if(acceptingProcess==null || !ownedByProcessForRuntime(acceptingProcess)) {
                throw new IllegalArgumentException("TcpAcceptor operation belongs to another Process");
            }
        }
        if(resultMaterializer==null) throw new IllegalStateException("TcpListener has no accept materializer");
        return startAccept(activation, lifecycle.beginDelegatedTcpAcceptForRuntime(activation));
    }

    /** True only for the exact Process incarnation hosting this listener's owning Actor. */
    boolean ownedByProcessForRuntime(ProtosProcessRuntime process) {
        return process != null
                && ProtosIoLifecycle.hostingProcessForRuntime(domain).orElse(null) == process;
    }

    private ProtosFutureValue startAccept(ProtosActivation activation, ProtosIoOperation operation) {
        if(!operation.future().isPending()) return operation.future();

        ProtosNetworkAcquisitionInvocation invocation =
                new ProtosNetworkAcquisitionInvocation(activation.executionDomain(), operation);
        operation.onCancellation(invocation::requestCancellation);

        AcceptCompletion completion = new AcceptCompletion() {
            @Override
            public void succeeded(
                    Object resourceState,
                    ProtosHostIpEndpoint localEndpoint,
                    ProtosHostIpEndpoint remoteEndpoint,
                    ProtosTcpConnectionFlow.Backend connectionBackend,
                    Runnable releaseIfUntransferred) {
                Objects.requireNonNull(releaseIfUntransferred, "releaseIfUntransferred");
                if (!operation.committed() && !operation.commit()) {
                    releaseSafely(releaseIfUntransferred);
                    return;
                }
                invocation.deliver(() -> {
                    if (operation.terminal()) {
                        releaseSafely(releaseIfUntransferred);
                        return false;
                    }
                    try {
                        ProtosTcpConnectionValue connection = Objects.requireNonNull(
                                resultMaterializer.materialize(
                                        activation,
                                        Objects.requireNonNull(resourceState, "resourceState"),
                                        Objects.requireNonNull(localEndpoint, "localEndpoint"),
                                        Objects.requireNonNull(remoteEndpoint, "remoteEndpoint"),
                                        Objects.requireNonNull(connectionBackend, "connectionBackend")),
                                "materialized accepted connection");
                        if (operation.resolve(connection)) return true;
                    } catch (RuntimeException invalidHostResult) {
                        releaseSafely(releaseIfUntransferred);
                        return operation.fail(ioError(activation));
                    }
                    releaseSafely(releaseIfUntransferred);
                    return false;
                }, releaseIfUntransferred);
            }

            @Override
            public void failed() {
                invocation.deliver(
                        () -> operation.fail(ioError(activation)), () -> {});
            }
        };

        invocation.enterBackendCall();
        try {
            Cancellation cancellation = backend.accept(completion);
            invocation.install(cancellation == null ? null : cancellation::cancel);
        } catch (RuntimeException backendFailure) {
            operation.fail(ioError(activation));
        } finally {
            invocation.exitBackendCall();
        }
        return operation.future();
    }

    /**
     * Synchronously derives one TcpAcceptor sharing this flow (I125-A). Starts no I/O; after the
     * close cutover it signals a fresh IOLifecycleError instead of constructing a capability.
     */
    public ProtosTcpAcceptorValue deriveAcceptor(
            ProtosActivation activation,
            ProtosObjectValue acceptorPrototype) {
        requireDomain(activation);
        if(resultMaterializer==null) throw new IllegalStateException("TcpListener has no accept materializer");
        ProtosPrelude prelude=activation.prelude().orElseThrow();
        if(!prelude.isTcpAcceptorPrototypeForRuntime(acceptorPrototype)) throw new IllegalArgumentException("TcpAcceptor derivation requires the standard TcpAcceptor prototype");
        if(lifecycle.state()!=ProtosIoLifecycle.State.OPEN) {
            throw new ProtosSignalException(ProtosCoreErrors.newOccurrence(activation,ProtosCoreErrors.StandardError.I_O_LIFECYCLE_ERROR));
        }
        return new ProtosTcpAcceptorValue(prelude,this,activation);
    }

    public synchronized ProtosFutureValue close(ProtosActivation activation) {
        requireDomain(activation);
        if(closeActivation==null) closeActivation=activation;
        return lifecycle.close(activation);
    }

    public ProtosIoLifecycle lifecycleForRuntime() { return lifecycle; }
    ProtosIoLifecycle.State lifecycleStateForTesting() { return lifecycle.state(); }

    private void startCloseRelease(ProtosIoLifecycle.ReleaseCompletion completion) {
        ProtosActivation activation=closeActivation;
        if(activation==null) throw new IllegalStateException("TCP listener close release started without a close activation");
        // Backend release stays host-only. Callbacks never build guest Errors or settle Futures:
        // the lifecycle does so directly only for a synchronous owner-domain report, and
        // otherwise in the listener owner's domain (I125-B).
        try {
            backend.close(new CloseCompletion(){
                @Override public void succeeded(){ completion.succeeded(); }
                @Override public void failed(){ completion.failedWithIoErrorForRuntime(); }
            });
        } catch(RuntimeException ex) { completion.failedWithIoErrorForRuntime(); }
    }

    private void requireDomain(ProtosActivation activation) {
        Objects.requireNonNull(activation,"activation");
        if(activation.executionDomain()!=domain) throw new IllegalArgumentException("TcpListener operation belongs to another Actor domain");
    }
    private static ProtosObjectValue ioError(ProtosActivation activation) {
        return ProtosCoreErrors.newOccurrence(activation,ProtosCoreErrors.StandardError.I_O_ERROR);
    }
    private static void releaseSafely(Runnable release) {
        try { release.run(); }
        catch(RuntimeException ignored) { /* custody release cannot create a second portable outcome */ }
    }


}
