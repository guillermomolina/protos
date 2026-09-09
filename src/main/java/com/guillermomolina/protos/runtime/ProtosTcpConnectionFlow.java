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

/**
 * Host-neutral full-duplex I/O/lifecycle state for one TcpConnection.
 *
 * <p>PLAT003 requires read and write progress to remain independent while close is one shared
 * resource lifecycle. Rather than duplicating ByteReadable/ByteWritable semantics, this class
 * composes two existing {@link ProtosByteIoFlow} lanes over one {@link ProtosIoLifecycle}.
 */
public final class ProtosTcpConnectionFlow {
    /**
     * Minimal transport-facing contract required by the already-standardized TCP connection
     * protocols. A later I028-E backend may realize it with any host mechanism.
     */
    public interface Backend {
        ProtosByteIoFlow.Cancellation read(
                int maxBytes, ProtosByteIoFlow.ReadCompletion completion);

        ProtosByteIoFlow.Cancellation write(
                byte[] bytes, ProtosByteIoFlow.WriteCompletion completion);

        ProtosByteIoFlow.Cancellation shutdownRead(
                ProtosByteIoFlow.ShutdownCompletion completion);

        ProtosByteIoFlow.Cancellation shutdownWrite(
                ProtosByteIoFlow.ShutdownCompletion completion);

        void close(ProtosByteIoFlow.ReceiverCompletion completion);
    }

    private final ProtosTcpConnectionValue receiver;
    private final ProtosActorExecutionDomain domain;
    private final Backend backend;
    private final ProtosIoLifecycle lifecycle;
    private final ProtosByteIoFlow readLane;
    private final ProtosByteIoFlow writeLane;
    private volatile ProtosActivation closeActivation;

    public ProtosTcpConnectionFlow(
            ProtosTcpConnectionValue receiver,
            ProtosObjectValue bytesPrototype,
            ProtosActivation activation,
            Backend backend) {
        this.receiver = Objects.requireNonNull(receiver, "receiver");
        Objects.requireNonNull(bytesPrototype, "bytesPrototype");
        Objects.requireNonNull(activation, "activation");
        this.domain = activation.executionDomain();
        this.backend = Objects.requireNonNull(backend, "backend");
        this.lifecycle =
                new ProtosIoLifecycle(
                        receiver,
                        activation.prelude().orElseThrow().futurePrototype(),
                        domain,
                        this::startCloseRelease);
        this.readLane =
                new ProtosByteIoFlow(
                        receiver,
                        bytesPrototype,
                        activation,
                        new ReadLaneBackend(),
                        lifecycle);
        this.writeLane =
                new ProtosByteIoFlow(
                        receiver,
                        bytesPrototype,
                        activation,
                        new WriteLaneBackend(),
                        lifecycle);
    }

    public ProtosFutureValue read(ProtosActivation activation, Object maxBytes) {
        requireDomain(activation);
        return readLane.read(activation, maxBytes);
    }

    public ProtosFutureValue write(ProtosActivation activation, Object bytes) {
        requireDomain(activation);
        return writeLane.write(activation, bytes);
    }

    public ProtosFutureValue shutdownRead(ProtosActivation activation) {
        requireDomain(activation);
        return readLane.shutdownRead(activation);
    }

    public ProtosFutureValue shutdownWrite(ProtosActivation activation) {
        requireDomain(activation);
        return writeLane.shutdownWrite(activation);
    }

    public synchronized ProtosFutureValue close(ProtosActivation activation) {
        requireDomain(activation);
        if (closeActivation == null) {
            closeActivation = activation;
        }
        return lifecycle.close(activation);
    }

    ProtosIoLifecycle.State lifecycleStateForTesting() {
        return lifecycle.state();
    }

    private void startCloseRelease(ProtosIoLifecycle.ReleaseCompletion completion) {
        ProtosActivation activation = closeActivation;
        if (activation == null) {
            throw new IllegalStateException("TCP close release started without a close activation");
        }
        try {
            backend.close(
                    new ProtosByteIoFlow.ReceiverCompletion() {
                        @Override
                        public void succeeded() {
                            completion.succeeded();
                        }

                        @Override
                        public void failed() {
                            completion.failed(ioError(activation));
                        }
                    });
        } catch (RuntimeException ex) {
            completion.failed(ioError(activation));
        }
    }

    private void requireDomain(ProtosActivation activation) {
        Objects.requireNonNull(activation, "activation");
        if (activation.executionDomain() != domain) {
            throw new IllegalArgumentException("TcpConnection operation belongs to another Actor domain");
        }
    }

    private static ProtosObjectValue ioError(ProtosActivation activation) {
        return ProtosCoreErrors.newOccurrence(
                activation, ProtosCoreErrors.StandardError.I_O_ERROR);
    }

    private final class ReadLaneBackend implements ProtosByteIoFlow.ReadShutdownBackend {
        @Override
        public ProtosByteIoFlow.Cancellation read(
                int maxBytes, ProtosByteIoFlow.ReadCompletion completion) {
            return backend.read(maxBytes, completion);
        }

        @Override
        public ProtosByteIoFlow.Cancellation write(
                byte[] bytes, ProtosByteIoFlow.WriteCompletion completion) {
            throw new IllegalStateException("write reached the TcpConnection read lane");
        }

        @Override
        public ProtosByteIoFlow.Cancellation shutdownRead(
                ProtosByteIoFlow.ShutdownCompletion completion) {
            return backend.shutdownRead(completion);
        }
    }

    private final class WriteLaneBackend implements ProtosByteIoFlow.WriteShutdownBackend {
        @Override
        public ProtosByteIoFlow.Cancellation read(
                int maxBytes, ProtosByteIoFlow.ReadCompletion completion) {
            throw new IllegalStateException("read reached the TcpConnection write lane");
        }

        @Override
        public ProtosByteIoFlow.Cancellation write(
                byte[] bytes, ProtosByteIoFlow.WriteCompletion completion) {
            return backend.write(bytes, completion);
        }

        @Override
        public ProtosByteIoFlow.Cancellation shutdownWrite(
                ProtosByteIoFlow.ShutdownCompletion completion) {
            return backend.shutdownWrite(completion);
        }
    }
}
