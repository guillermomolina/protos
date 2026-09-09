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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

/**
 * One internal PLAT006 JDK-NIO poller primitive.
 *
 * <p>The poller owns its Selector thread and every channel registered with it. Its thread executes
 * host/backend state machines only; it never enters a Truffle Context or invokes Protos guest code.
 * Higher I028-E slices compose a bounded set of these pollers without making poller identity or
 * readiness vocabulary observable to Protos.
 */
final class ProtosNioHostIoPoller implements AutoCloseable {
    /** NIO-only readiness callback executed on the owning poller thread. */
    interface SelectionHandler {
        void ready(SelectionKey key) throws Exception;

        void failed(Exception failure);

        default void closed() {}
    }

    private record Command(Runnable action, Consumer<RuntimeException> failure) {
        private Command {
            Objects.requireNonNull(action, "action");
            Objects.requireNonNull(failure, "failure");
        }
    }

    private final Object lifecycleLock = new Object();
    private final Selector selector;
    private final ConcurrentLinkedQueue<Command> commands = new ConcurrentLinkedQueue<>();
    private final Thread thread;
    private boolean accepting = true;
    private boolean terminated;

    ProtosNioHostIoPoller(String threadName) throws IOException {
        selector = Selector.open();
        thread = new Thread(this::runLoop, Objects.requireNonNull(threadName, "threadName"));
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Queues one host-only control action and wakes a blocked select.
     *
     * <p>The explicit failure callback keeps one malformed backend action from terminating the
     * shared poller and stranding unrelated channels.
     */
    void submit(Runnable action, Consumer<RuntimeException> failure) {
        Command command = new Command(action, failure);
        synchronized (lifecycleLock) {
            if (!accepting) {
                throw new IllegalStateException("NIO host I/O poller is closing or closed");
            }
            commands.add(command);
        }
        selector.wakeup();
    }

    /** Registers one channel with this poller. Must run inside a submitted poller action. */
    SelectionKey register(SelectableChannel channel, int interestOps, SelectionHandler handler)
            throws IOException {
        requirePollerThread();
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(handler, "handler");
        if ((interestOps & ~channel.validOps()) != 0) {
            throw new IllegalArgumentException("invalid channel interest operations");
        }
        channel.configureBlocking(false);
        return channel.register(selector, interestOps, handler);
    }

    /** Changes one registered channel's readiness interest from its owning poller thread. */
    void interestOps(SelectionKey key, int interestOps) {
        requirePollerThread();
        Objects.requireNonNull(key, "key");
        if (!key.isValid()) {
            throw new CancelledKeyException();
        }
        if ((interestOps & ~key.channel().validOps()) != 0) {
            throw new IllegalArgumentException("invalid channel interest operations");
        }
        key.interestOps(interestOps);
    }

    boolean isPollerThreadForTesting() {
        return Thread.currentThread() == thread;
    }

    boolean isTerminatedForTesting() {
        synchronized (lifecycleLock) {
            return terminated;
        }
    }

    /**
     * Stops admission, drains already-admitted control actions, releases every registered channel
     * and terminates the selector thread. Repeated calls are harmless.
     */
    @Override
    public void close() {
        synchronized (lifecycleLock) {
            accepting = false;
        }
        selector.wakeup();
        if (Thread.currentThread() == thread) {
            return;
        }

        boolean interrupted = false;
        while (thread.isAlive()) {
            try {
                thread.join();
            } catch (InterruptedException interruption) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private void runLoop() {
        IOException terminalFailure = null;
        try {
            for (;;) {
                drainCommands();
                if (shouldTerminate()) {
                    return;
                }
                selector.select();
                dispatchSelected();
            }
        } catch (IOException selectorFailure) {
            terminalFailure = selectorFailure;
        } finally {
            if (terminalFailure != null) {
                failPendingCommands(new UncheckedIOException(terminalFailure));
            }
            closeRegisteredChannels(terminalFailure);
            try {
                selector.close();
            } catch (IOException ignored) {
                // Best-effort host cleanup after registered channels have already been released.
            }
            synchronized (lifecycleLock) {
                accepting = false;
                terminated = true;
                lifecycleLock.notifyAll();
            }
        }
    }

    private boolean shouldTerminate() {
        synchronized (lifecycleLock) {
            return !accepting && commands.isEmpty();
        }
    }

    private void drainCommands() {
        for (;;) {
            Command command = commands.poll();
            if (command == null) {
                return;
            }
            try {
                command.action().run();
            } catch (RuntimeException failure) {
                reportCommandFailure(command, failure);
            }
        }
    }

    private void failPendingCommands(RuntimeException failure) {
        for (;;) {
            Command command = commands.poll();
            if (command == null) {
                return;
            }
            reportCommandFailure(command, failure);
        }
    }

    private static void reportCommandFailure(Command command, RuntimeException failure) {
        try {
            command.failure().accept(failure);
        } catch (RuntimeException ignored) {
            // A defective operation failure callback must not terminate the shared poller.
        }
    }

    private void dispatchSelected() {
        Iterator<SelectionKey> selected = selector.selectedKeys().iterator();
        while (selected.hasNext()) {
            SelectionKey key = selected.next();
            selected.remove();
            if (!key.isValid()) {
                continue;
            }

            Object attachment = key.attachment();
            if (!(attachment instanceof SelectionHandler handler)) {
                key.cancel();
                closeQuietly(key.channel());
                continue;
            }

            try {
                handler.ready(key);
            } catch (Exception failure) {
                key.attach(null);
                key.cancel();
                closeQuietly(key.channel());
                reportHandlerFailure(handler, failure);
            }
        }
    }

    private void closeRegisteredChannels(Exception terminalFailure) {
        List<SelectionKey> keys;
        try {
            keys = new ArrayList<>(selector.keys());
        } catch (RuntimeException unavailableSelector) {
            return;
        }

        for (SelectionKey key : keys) {
            Object attachment = key.attachment();
            key.attach(null);
            key.cancel();
            closeQuietly(key.channel());
            if (attachment instanceof SelectionHandler handler) {
                if (terminalFailure == null) {
                    reportHandlerClosed(handler);
                } else {
                    reportHandlerFailure(handler, terminalFailure);
                }
            }
        }
    }

    private static void reportHandlerFailure(SelectionHandler handler, Exception failure) {
        try {
            handler.failed(failure);
        } catch (RuntimeException ignored) {
            // A broken backend callback must not strand unrelated channels on the shared poller.
        }
    }

    private static void reportHandlerClosed(SelectionHandler handler) {
        try {
            handler.closed();
        } catch (RuntimeException ignored) {
            // Poller shutdown owns physical cleanup even when a callback is defective.
        }
    }

    private static void closeQuietly(java.nio.channels.Channel channel) {
        try {
            channel.close();
        } catch (IOException ignored) {
            // Physical cleanup failure cannot resurrect the closed poller.
        }
    }

    private void requirePollerThread() {
        if (Thread.currentThread() != thread) {
            throw new IllegalStateException(
                    "NIO channel mutation must run on the owning poller thread");
        }
    }
}
