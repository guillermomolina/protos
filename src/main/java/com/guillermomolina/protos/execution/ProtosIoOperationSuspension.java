/* APL-1.0 licensed work; see LICENSE.TXT. */
package com.guillermomolina.protos.execution;

import com.guillermomolina.protos.runtime.ProtosIoOperation;
import java.util.Objects;
import java.util.function.Supplier;

/** Backend-private PLAT029 yield leaf for one operation-owned non-Task C-prime wait. */
final class ProtosIoOperationSuspension {
    interface Dependency {
        boolean isReady();

        /** Removes only this operation's waiting relationship; it never cancels the dependency. */
        default void waitingOperationReleased(ProtosIoOperation operation) {}
    }

    private final ProtosIoOperation operation;
    private final Dependency dependency;
    private final Supplier<Object> resumer;
    private boolean waitReleased;
    private boolean resumed;

    private ProtosIoOperationSuspension(
            ProtosIoOperation operation,
            Dependency dependency,
            Supplier<Object> resumer) {
        this.operation = Objects.requireNonNull(operation, "operation");
        this.dependency = Objects.requireNonNull(dependency, "dependency");
        this.resumer = Objects.requireNonNull(resumer, "resumer");
    }

    static ProtosIoOperationSuspension pending(
            ProtosIoOperation operation,
            Dependency dependency,
            Supplier<Object> resumer) {
        return new ProtosIoOperationSuspension(operation, dependency, resumer);
    }

    ProtosIoOperation operation() {
        return operation;
    }

    Dependency dependency() {
        return dependency;
    }

    void releaseWait() {
        boolean notify;
        synchronized (this) {
            notify = !waitReleased;
            waitReleased = true;
        }
        if (notify) {
            dependency.waitingOperationReleased(operation);
        }
    }

    Object resume() {
        Supplier<Object> resumeAction;
        synchronized (this) {
            if (!waitReleased) {
                throw new IllegalStateException(
                        "operation-owned suspension resumed before releasing its wait relationship");
            }
            if (resumed) {
                throw new IllegalStateException(
                        "operation-owned suspension descriptor is one-shot");
            }
            resumed = true;
            resumeAction = resumer;
        }
        return Objects.requireNonNull(
                resumeAction.get(),
                "operation-owned suspension resumer returned null");
    }
}
