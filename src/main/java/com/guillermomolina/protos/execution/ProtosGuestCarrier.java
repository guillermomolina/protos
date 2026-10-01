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
package com.guillermomolina.protos.execution;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * One long-lived dedicated guest carrier thread with the explicit {@link
 * ProtosStandaloneHostedExecution#GUEST_CALL_STACK_SIZE_BYTES} stack budget.
 *
 * <p>Every guest-touching operation of a standalone hosted session runs here, serially, so guest
 * recursion never depends on the ambient stack of the calling thread. It is not a general
 * executor: it runs submitted operations one at a time and blocks the submitter until each ends.
 */
final class ProtosGuestCarrier implements AutoCloseable {
    private static final Runnable STOP = () -> {};

    private final BlockingQueue<Runnable> operations = new LinkedBlockingQueue<>();
    private final Thread thread;
    private boolean closed;

    ProtosGuestCarrier(String name) {
        thread =
                new Thread(
                        null,
                        this::runOperations,
                        Objects.requireNonNull(name, "name"),
                        ProtosStandaloneHostedExecution.GUEST_CALL_STACK_SIZE_BYTES);
        thread.start();
    }

    private void runOperations() {
        try {
            while (true) {
                Runnable next = operations.take();
                if (next == STOP) return;
                next.run();
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Runs {@code operation} on the carrier and returns its result. {@link IOException}, runtime
     * exceptions and errors thrown by the operation are rethrown to the caller.
     */
    <T> T call(Callable<T> operation) throws IOException {
        Objects.requireNonNull(operation, "operation");
        Object[] result = new Object[1];
        Throwable[] failure = new Throwable[1];
        boolean[] done = new boolean[1];
        synchronized (this) {
            if (closed) {
                throw new IllegalStateException("guest carrier is closed");
            }
            operations.add(
                    () -> {
                        try {
                            result[0] = operation.call();
                        } catch (Throwable thrown) {
                            failure[0] = thrown;
                        }
                        synchronized (done) {
                            done[0] = true;
                            done.notifyAll();
                        }
                    });
        }
        boolean interrupted = false;
        synchronized (done) {
            while (!done[0]) {
                try {
                    done.wait();
                } catch (InterruptedException interruption) {
                    interrupted = true;
                }
            }
        }
        if (interrupted) Thread.currentThread().interrupt();
        if (failure[0] instanceof IOException io) throw io;
        if (failure[0] instanceof RuntimeException runtime) throw runtime;
        if (failure[0] instanceof Error error) throw error;
        if (failure[0] != null) throw new IllegalStateException(failure[0]);
        @SuppressWarnings("unchecked")
        T value = (T) result[0];
        return value;
    }

    boolean isCarrierThread() {
        return Thread.currentThread() == thread;
    }

    /** Stops the carrier after already submitted operations and waits for it to end. */
    @Override
    public void close() {
        synchronized (this) {
            if (closed) return;
            closed = true;
            operations.add(STOP);
        }
        boolean interrupted = false;
        while (thread.isAlive()) {
            try {
                thread.join();
            } catch (InterruptedException interruption) {
                interrupted = true;
            }
        }
        if (interrupted) Thread.currentThread().interrupt();
    }
}
