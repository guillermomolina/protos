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
import java.util.concurrent.locks.LockSupport;

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
        CarrierCall<T> call = new CarrierCall<>(Objects.requireNonNull(operation, "operation"));
        synchronized (this) {
            if (closed) {
                throw new IllegalStateException("guest carrier is closed");
            }
            operations.add(call);
        }
        return call.await();
    }

    /**
     * One submitted operation: queued as the carrier {@link Runnable} itself and completed by
     * unparking the waiting caller. The volatile {@code done} write publishes the result/failure.
     */
    private static final class CarrierCall<T> implements Runnable {
        private final Callable<T> operation;
        private final Thread waiter = Thread.currentThread();
        private T result;
        private Throwable failure;
        private volatile boolean done;

        CarrierCall(Callable<T> operation) {
            this.operation = operation;
        }

        @Override
        public void run() {
            try {
                result = operation.call();
            } catch (Throwable thrown) {
                failure = thrown;
            }
            done = true;
            LockSupport.unpark(waiter);
        }

        T await() throws IOException {
            boolean interrupted = false;
            while (!done) {
                LockSupport.park(this);
                // A set interrupt status makes park return immediately; remember and clear it.
                if (Thread.interrupted()) interrupted = true;
            }
            if (interrupted) Thread.currentThread().interrupt();
            if (failure instanceof IOException io) throw io;
            if (failure instanceof RuntimeException runtime) throw runtime;
            if (failure instanceof Error error) throw error;
            if (failure != null) throw new IllegalStateException(failure);
            return result;
        }
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
