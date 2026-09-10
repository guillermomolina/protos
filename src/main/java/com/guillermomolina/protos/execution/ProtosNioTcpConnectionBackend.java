/*
 * THE LICENSED WORK IS PROVIDED UNDER THE TERMS OF THE ADAPTIVE PUBLIC LICENSE
 * ("LICENSE") AS FIRST COMPLETED BY: Guillermo Adrián Molina. ANY USE, PUBLIC
 * DISPLAY, PUBLIC PERFORMANCE, REPRODUCTION OR DISTRIBUTION OF, OR PREPARATION OF
 * DERIVATIVE WORKS BASED ON, THE LICENSED WORK CONSTITUTES RECIPIENT'S ACCEPTANCE
 * OF THIS LICENSE AND ITS TERMS, WHETHER OR NOT SUCH RECIPIENT READS THE TERMS.
 * "LICENSED WORK" AND "RECIPIENT" ARE DEFINED IN THE LICENSE. A COPY OF THE
 * LICENSE IS LOCATED IN THE TEXT FILE ENTITLED "LICENSE.TXT" ACCOMPANYING THE
 * CONTENTS OF THIS FILE. IF A COPY OF THE LICENSE DOES NOT ACCOMPANY THIS FILE, A
 * COPY OF THE LICENSE MAY ALSO BE OBTAINED AT THE FOLLOWING WEB SITE:
 * https://github.com/guillermomolina/protos
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License for
 * the specific language governing rights and limitations under the License.
 */
package com.guillermomolina.protos.execution;

import com.guillermomolina.protos.runtime.ProtosByteIoFlow;
import com.guillermomolina.protos.runtime.ProtosTcpConnectionFlow;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Physical NIO state for one acquired TcpConnection.
 *
 * <p>E2 owns only acquisition custody and physical close. E3 adds read/write/half-close readiness.
 */
final class ProtosNioTcpConnectionBackend
        implements ProtosTcpConnectionFlow.Backend, ProtosNioHostIoPoller.SelectionHandler {
    private static final ProtosByteIoFlow.Cancellation NO_CANCELLATION = () -> {};
    // Implementation-local tuning only; not portable behavior.
    private static final int READ_CHUNK_BYTES = 64 * 1024;

    private final ProtosNioHostIoPoller poller;
    private final SocketChannel channel;
    private final AtomicBoolean physicalClosed = new AtomicBoolean();
    private final AtomicReference<ReadRequest> pendingRead = new AtomicReference<>();
    private final AtomicReference<WriteRequest> pendingWrite = new AtomicReference<>();
    private volatile SelectionKey key;

    ProtosNioTcpConnectionBackend(
            ProtosNioHostIoPoller poller, SocketChannel channel) {
        this.poller = Objects.requireNonNull(poller, "poller");
        this.channel = Objects.requireNonNull(channel, "channel");
    }

    void attachKey(SelectionKey key) {
        SelectionKey supplied = Objects.requireNonNull(key, "key");
        SelectionKey existing = this.key;
        if (existing != null && existing != supplied) {
            throw new IllegalStateException("TCP connection already has another key");
        }
        this.key = supplied;
    }

    void releaseIfUntransferred() {
        requestPhysicalClose(null);
    }

    @Override
    public ProtosByteIoFlow.Cancellation read(
            int maxBytes, ProtosByteIoFlow.ReadCompletion completion) {
        Objects.requireNonNull(completion, "completion");
        if (maxBytes <= 0 || physicalClosed.get() || !channel.isOpen()) {
            reportReadFailure(completion);
            return NO_CANCELLATION;
        }

        ReadRequest request = new ReadRequest(maxBytes, completion);
        try {
            poller.submit(
                    () -> startReadOnPoller(request),
                    ignored -> request.reportFailure());
        } catch (RuntimeException unavailablePoller) {
            request.reportFailure();
        }
        return request::cancel;
    }

    @Override
    public ProtosByteIoFlow.Cancellation write(
            byte[] bytes, ProtosByteIoFlow.WriteCompletion completion) {
        Objects.requireNonNull(bytes, "bytes");
        Objects.requireNonNull(completion, "completion");
        if (!(completion instanceof ProtosByteIoFlow.FirstEffectWriteCompletion firstEffect)
                || bytes.length == 0 || physicalClosed.get() || !channel.isOpen()) {
            reportWriteFailure(completion, 0);
            return NO_CANCELLATION;
        }
        WriteRequest request = new WriteRequest(bytes, firstEffect);
        try {
            poller.submit(() -> startWriteOnPoller(request), ignored -> request.reportFailure());
        } catch (RuntimeException unavailablePoller) {
            request.reportFailure();
        }
        return request::cancel;
    }

    @Override
    public ProtosByteIoFlow.Cancellation shutdownRead(
            ProtosByteIoFlow.ShutdownCompletion completion) {
        Objects.requireNonNull(completion, "completion").failed();
        return NO_CANCELLATION;
    }

    @Override
    public ProtosByteIoFlow.Cancellation shutdownWrite(
            ProtosByteIoFlow.ShutdownCompletion completion) {
        Objects.requireNonNull(completion, "completion").failed();
        return NO_CANCELLATION;
    }

    @Override
    public void close(ProtosByteIoFlow.ReceiverCompletion completion) {
        requestPhysicalClose(Objects.requireNonNull(completion, "completion"));
    }

    @Override
    public void ready(SelectionKey selectedKey) {
        if (selectedKey.isReadable()) serviceReadOnPoller();
        if (selectedKey.isValid() && selectedKey.isWritable()) serviceWriteOnPoller();
    }

    @Override
    public void failed(Exception failure) {
        physicalClosed.set(true);
        failPendingRead();
        failPendingWrite();
    }

    @Override
    public void closed() {
        physicalClosed.set(true);
        failPendingRead();
        failPendingWrite();
    }

    private void startReadOnPoller(ReadRequest request) {
        if (request.cancelled.get()) {
            request.reportFailure();
            return;
        }
        if (physicalClosed.get() || !channel.isOpen() || key == null || !key.isValid()) {
            request.reportFailure();
            return;
        }
        if (!pendingRead.compareAndSet(null, request)) {
            request.reportFailure();
            return;
        }
        serviceReadOnPoller();
    }

    private void serviceReadOnPoller() {
        ReadRequest request = pendingRead.get();
        if (request == null) {
            disableReadInterestBestEffort();
            return;
        }
        if (request.cancelled.get()) {
            finishReadFailure(request);
            return;
        }

        ByteBuffer buffer = ByteBuffer.allocate(Math.min(request.maxBytes, READ_CHUNK_BYTES));
        final int count;
        try {
            count = channel.read(buffer);
        } catch (IOException readFailure) {
            finishReadFailure(request);
            return;
        }

        if (count > 0) {
            byte[] bytes = new byte[count];
            buffer.flip();
            buffer.get(bytes);
            finishReadData(request, bytes);
            return;
        }
        if (count < 0) {
            finishReadEof(request);
            return;
        }

        if (request.cancelled.get()) {
            finishReadFailure(request);
            return;
        }
        enableReadInterest();
    }

    private void finishReadData(ReadRequest request, byte[] bytes) {
        if (!pendingRead.compareAndSet(request, null)) return;
        disableReadInterestBestEffort();
        request.reportData(bytes);
    }

    private void finishReadEof(ReadRequest request) {
        if (!pendingRead.compareAndSet(request, null)) return;
        disableReadInterestBestEffort();
        request.reportEof();
    }

    private void finishReadFailure(ReadRequest request) {
        if (!pendingRead.compareAndSet(request, null)) return;
        disableReadInterestBestEffort();
        request.reportFailure();
    }

    private void cancelReadOnPoller(ReadRequest request) {
        if (!pendingRead.compareAndSet(request, null)) return;
        disableReadInterestBestEffort();
        request.reportFailure();
    }

    private void enableReadInterest() {
        SelectionKey current = key;
        if (current == null || !current.isValid()) {
            ReadRequest request = pendingRead.get();
            if (request != null) finishReadFailure(request);
            return;
        }
        int currentOps = current.interestOps();
        int desired = currentOps | SelectionKey.OP_READ;
        if (desired != currentOps) poller.interestOps(current, desired);
    }

    private void disableReadInterestBestEffort() {
        SelectionKey current = key;
        if (current == null || !current.isValid()) return;
        try {
            int currentOps = current.interestOps();
            int desired = currentOps & ~SelectionKey.OP_READ;
            if (desired != currentOps) poller.interestOps(current, desired);
        } catch (RuntimeException ignored) {
            // Physical close/poller failure owns an invalid key.
        }
    }

    private void failPendingRead() {
        ReadRequest request = pendingRead.getAndSet(null);
        if (request != null) request.reportFailure();
    }

    private static void reportReadFailure(ProtosByteIoFlow.ReadCompletion completion) {
        try {
            completion.failed();
        } catch (RuntimeException ignored) {
            // A defective completion callback cannot create a second backend outcome.
        }
    }

    private final class ReadRequest {
        private final int maxBytes;
        private final ProtosByteIoFlow.ReadCompletion completion;
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private final AtomicBoolean reported = new AtomicBoolean();

        private ReadRequest(int maxBytes, ProtosByteIoFlow.ReadCompletion completion) {
            this.maxBytes = maxBytes;
            this.completion = completion;
        }

        private void cancel() {
            if (!cancelled.compareAndSet(false, true) || reported.get()) return;
            try {
                poller.submit(
                        () -> cancelReadOnPoller(this),
                        ignored -> {
                            // Poller terminal cleanup retires a registered request.
                        });
            } catch (RuntimeException unavailablePoller) {
                // A stopped poller closes registered channels and invokes closed()/failed().
            }
        }

        private void reportData(byte[] bytes) {
            if (!reported.compareAndSet(false, true)) return;
            try {
                completion.data(bytes);
            } catch (RuntimeException ignored) {
                // Physical input was already obtained; ByteIoFlow owns logical aftermath.
            }
        }

        private void reportEof() {
            if (!reported.compareAndSet(false, true)) return;
            try {
                completion.eof();
            } catch (RuntimeException ignored) {
                // EOF already owns this backend request.
            }
        }

        private void reportFailure() {
            if (!reported.compareAndSet(false, true)) return;
            reportReadFailure(completion);
        }
    }


    private void startWriteOnPoller(WriteRequest request) {
        if (request.cancelled.get()) { request.reportFailure(); return; }
        if (physicalClosed.get() || !channel.isOpen() || key == null || !key.isValid()) {
            request.reportFailure(); return;
        }
        if (!pendingWrite.compareAndSet(null, request)) { request.reportFailure(); return; }
        serviceWriteOnPoller();
    }

    private void serviceWriteOnPoller() {
        WriteRequest request = pendingWrite.get();
        if (request == null) { disableWriteInterestBestEffort(); return; }
        if (request.cancelled.get() && request.contributedPrefix() == 0) {
            finishWriteFailure(request); return;
        }

        boolean firstAttempt = request.contributedPrefix() == 0;
        if (firstAttempt && !request.completion.beginFirstEffectAttempt()) {
            retireWrite(request); return;
        }

        final int count;
        try {
            count = channel.write(request.buffer);
        } catch (IOException writeFailure) {
            if (firstAttempt && !request.completion.finishFirstEffectAttempt(false)) {
                retireWrite(request); return;
            }
            finishWriteFailure(request); return;
        }

        if (firstAttempt && !request.completion.finishFirstEffectAttempt(count > 0)) {
            retireWrite(request); return;
        }
        if (!request.buffer.hasRemaining()) { finishWriteSuccess(request); return; }
        if (request.cancelled.get() && request.contributedPrefix() == 0) {
            finishWriteFailure(request); return;
        }
        enableWriteInterest();
    }

    private void finishWriteSuccess(WriteRequest request) {
        if (!pendingWrite.compareAndSet(request, null)) return;
        disableWriteInterestBestEffort(); request.reportSuccess();
    }

    private void finishWriteFailure(WriteRequest request) {
        if (!pendingWrite.compareAndSet(request, null)) return;
        disableWriteInterestBestEffort(); request.reportFailure();
    }

    private void retireWrite(WriteRequest request) {
        if (!pendingWrite.compareAndSet(request, null)) return;
        disableWriteInterestBestEffort(); request.reported.set(true);
    }

    private void cancelWriteOnPoller(WriteRequest request) {
        if (request.contributedPrefix() > 0) return;
        if (!pendingWrite.compareAndSet(request, null)) return;
        disableWriteInterestBestEffort(); request.reportFailure();
    }

    private void enableWriteInterest() {
        SelectionKey current=key;
        if (current==null || !current.isValid()) {
            WriteRequest request=pendingWrite.get();
            if (request!=null) finishWriteFailure(request);
            return;
        }
        int currentOps=current.interestOps();
        int desired=currentOps | SelectionKey.OP_WRITE;
        if (desired!=currentOps) poller.interestOps(current,desired);
    }

    private void disableWriteInterestBestEffort() {
        SelectionKey current=key;
        if (current==null || !current.isValid()) return;
        try {
            int currentOps=current.interestOps();
            int desired=currentOps & ~SelectionKey.OP_WRITE;
            if (desired!=currentOps) poller.interestOps(current,desired);
        } catch (RuntimeException ignored) {
            // Physical close/poller failure owns an invalid key.
        }
    }

    private void failPendingWrite() {
        WriteRequest request=pendingWrite.getAndSet(null);
        if (request!=null) request.reportFailure();
    }

    private static void reportWriteFailure(
            ProtosByteIoFlow.WriteCompletion completion, int contributedPrefix) {
        try { completion.failed(contributedPrefix); }
        catch (RuntimeException ignored) {
            // A defective callback cannot create a second backend outcome.
        }
    }

    private final class WriteRequest {
        private final ByteBuffer buffer;
        private final ProtosByteIoFlow.FirstEffectWriteCompletion completion;
        private final AtomicBoolean cancelled=new AtomicBoolean();
        private final AtomicBoolean reported=new AtomicBoolean();

        private WriteRequest(byte[] bytes, ProtosByteIoFlow.FirstEffectWriteCompletion completion) {
            this.buffer=ByteBuffer.wrap(bytes);
            this.completion=completion;
        }
        private int contributedPrefix() { return buffer.position(); }

        private void cancel() {
            if (!cancelled.compareAndSet(false,true) || reported.get()) return;
            try {
                poller.submit(() -> cancelWriteOnPoller(this), ignored -> {});
            } catch (RuntimeException unavailablePoller) {
                // Poller terminal cleanup owns registered-channel retirement.
            }
        }

        private void reportSuccess() {
            if (!reported.compareAndSet(false,true)) return;
            try { completion.succeeded(); } catch (RuntimeException ignored) {}
        }
        private void reportFailure() {
            if (!reported.compareAndSet(false,true)) return;
            reportWriteFailure(completion,contributedPrefix());
        }
    }

    private void requestPhysicalClose(ProtosByteIoFlow.ReceiverCompletion completion) {
        if (physicalClosed.get()) {
            if (completion != null) reportSucceeded(completion);
            return;
        }
        try {
            poller.submit(
                    () -> finishClose(completion),
                    ignored -> fallbackClose(completion));
        } catch (RuntimeException unavailablePoller) {
            fallbackClose(completion);
        }
    }

    private void finishClose(ProtosByteIoFlow.ReceiverCompletion completion) {
        boolean success = closePhysical();
        if (completion == null) return;
        if (success) reportSucceeded(completion);
        else reportFailed(completion);
    }

    private void fallbackClose(ProtosByteIoFlow.ReceiverCompletion completion) {
        finishClose(completion);
    }

    private boolean closePhysical() {
        if (!physicalClosed.compareAndSet(false, true)) return true;
        SelectionKey current = key;
        if (current != null) current.cancel();
        failPendingRead();
        failPendingWrite();
        try {
            channel.close();
            return true;
        } catch (IOException failure) {
            return false;
        }
    }

    private static void reportSucceeded(
            ProtosByteIoFlow.ReceiverCompletion completion) {
        try {
            completion.succeeded();
        } catch (RuntimeException ignored) {
            // Physical close already owns the outcome.
        }
    }

    private static void reportFailed(
            ProtosByteIoFlow.ReceiverCompletion completion) {
        try {
            completion.failed();
        } catch (RuntimeException ignored) {
            // Physical close failure already owns the outcome.
        }
    }
}
