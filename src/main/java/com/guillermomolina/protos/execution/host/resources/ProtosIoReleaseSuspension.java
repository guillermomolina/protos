/* APL-1.0 licensed work; see LICENSE.TXT. */
package com.guillermomolina.protos.execution;

import com.guillermomolina.protos.runtime.ProtosIoReleaseExecution;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import java.util.Objects;
import java.util.function.Supplier;

/** Backend-private PLAT030 yield leaf for one lifecycle-release-owned C-prime wait. */
final class ProtosIoReleaseSuspension {
    interface Dependency {
        boolean isReady();
        default void waitingReleaseRetained(ProtosIoReleaseExecution release) {}
        default void waitingReleaseReleased(ProtosIoReleaseExecution release) {}
    }

    private final ProtosIoReleaseExecution release;
    private final Dependency dependency;
    private final Supplier<Object> resumer;
    private boolean waitRetained;
    private boolean waitReleased;
    private boolean resumed;

    private ProtosIoReleaseSuspension(
            ProtosIoReleaseExecution release,
            Dependency dependency,
            Supplier<Object> resumer) {
        this.release = Objects.requireNonNull(release, "release");
        this.dependency = Objects.requireNonNull(dependency, "dependency");
        this.resumer = Objects.requireNonNull(resumer, "resumer");
    }

    static ProtosIoReleaseSuspension pending(
            ProtosIoReleaseExecution release,
            Dependency dependency,
            Supplier<Object> resumer) {
        return new ProtosIoReleaseSuspension(release, dependency, resumer);
    }

    ProtosIoReleaseExecution release() { return release; }
    Dependency dependency() { return dependency; }

    void retainWait() {
        boolean notify;
        synchronized (this) {
            notify = !waitReleased && !waitRetained;
            if (notify) waitRetained = true;
        }
        if (notify) dependency.waitingReleaseRetained(release);
    }

    void releaseWait() {
        boolean notify;
        synchronized (this) {
            notify = !waitReleased;
            waitReleased = true;
        }
        if (notify) dependency.waitingReleaseReleased(release);
    }

    Object resume() {
        Supplier<Object> resumeAction;
        synchronized (this) {
            if (!waitReleased) {
                throw new IllegalStateException(
                        "release-owned suspension resumed before releasing its wait relationship");
            }
            if (resumed) {
                throw new IllegalStateException(
                        "release-owned suspension descriptor is one-shot");
            }
            resumed = true;
            resumeAction = resumer;
        }
        return Objects.requireNonNull(
                invokeResumer(resumeAction), "release-owned suspension resumer returned null");
    }

    @TruffleBoundary
    private static Object invokeResumer(Supplier<Object> resumeAction) {
        return resumeAction.get();
    }

}
