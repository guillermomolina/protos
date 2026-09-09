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
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Physical NIO state for one acquired TcpConnection.
 *
 * <p>E2 owns only acquisition custody and physical close. E3 adds read/write/half-close readiness.
 */
final class ProtosNioTcpConnectionBackend
        implements ProtosTcpConnectionFlow.Backend, ProtosNioHostIoPoller.SelectionHandler {
    private static final ProtosByteIoFlow.Cancellation NO_CANCELLATION = () -> {};

    private final ProtosNioHostIoPoller poller;
    private final SocketChannel channel;
    private final AtomicBoolean physicalClosed = new AtomicBoolean();
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
        Objects.requireNonNull(completion, "completion").failed();
        return NO_CANCELLATION;
    }

    @Override
    public ProtosByteIoFlow.Cancellation write(
            byte[] bytes, ProtosByteIoFlow.WriteCompletion completion) {
        Objects.requireNonNull(bytes, "bytes");
        Objects.requireNonNull(completion, "completion").failed(0);
        return NO_CANCELLATION;
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
    public void ready(SelectionKey key) {
        // E2 keeps zero post-connect interests. E3 owns connected readiness.
    }

    @Override
    public void failed(Exception failure) {
        physicalClosed.set(true);
    }

    @Override
    public void closed() {
        physicalClosed.set(true);
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
