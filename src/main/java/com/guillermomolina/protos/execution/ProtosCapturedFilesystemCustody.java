/* APL-1.0 licensed work; see LICENSE.TXT. */
package com.guillermomolina.protos.execution;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosFilesystemValue;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Run-scoped host custody for one exact immutable captured Filesystem backing.
 *
 * <p>This is host implementation machinery, not Protos data and not part of
 * {@link ProtosPackageExecutionPlan}. It owns the exact captured backend independently of any
 * Package Tool or application Actor domain. Each {@link #materialize(ProtosActivation)} call
 * creates a fresh standard read-only Filesystem capability bound to the supplied activation's
 * domain while delegating to the same captured backend.
 *
 * <p>The selected source root is used only by {@link #captureSelectedRoot(Path)}. Once capture
 * succeeds the source backend is closed and this object retains no source Path authority. The
 * captured backend's physical representation remains an implementation detail and may later be
 * backed by managed temporary storage, CAS/deduplicated blobs, copy-on-write state or another
 * immutable representation without changing this custody contract. Host code must keep this
 * custody open for the complete lifetime of every Filesystem view it provisions.
 */
final class ProtosCapturedFilesystemCustody implements AutoCloseable {
    private final ProtosStandardFilesystemProtocol.CapturedBackend capturedBackend;
    private final Runnable release;
    private boolean closed;

    ProtosCapturedFilesystemCustody(
            ProtosStandardFilesystemProtocol.CapturedBackend capturedBackend,
            Runnable release) {
        this.capturedBackend = Objects.requireNonNull(capturedBackend, "capturedBackend");
        this.release = Objects.requireNonNull(release, "release");
    }

    /** Captures exactly the already-selected root once and closes source authority immediately. */
    static ProtosCapturedFilesystemCustody captureSelectedRoot(Path selectedRoot)
            throws IOException {
        Objects.requireNonNull(selectedRoot, "selectedRoot");
        ProtosNioCapturedTreeFilesystemBackend captured = null;
        try {
            try (ProtosNioReadOnlyTreeFilesystemBackend source =
                    new ProtosNioReadOnlyTreeFilesystemBackend(selectedRoot)) {
                if (!source.secureConfinementAvailable()) {
                    throw new IOException(
                            "secure confinement unavailable for selected package-store root");
                }
                captured = source.captureRootForHostCustody();
            }
            ProtosNioCapturedTreeFilesystemBackend owned = captured;
            return new ProtosCapturedFilesystemCustody(
                    owned, owned::releaseIfUntransferred);
        } catch (IOException | RuntimeException failure) {
            if (captured != null) {
                captured.releaseIfUntransferred();
            }
            throw failure;
        }
    }

    /** Materializes one fresh Actor-domain Filesystem view over the exact captured backend. */
    synchronized ProtosFilesystemValue materialize(ProtosActivation activation) {
        Objects.requireNonNull(activation, "activation");
        if (closed) {
            throw new IllegalStateException("captured Filesystem custody is closed");
        }
        return ProtosStandardFilesystemProtocol.createCapturedCapability(
                activation, capturedBackend);
    }

    /** Releases the run-owned captured backing exactly once. */
    @Override
    public void close() {
        Runnable ownedRelease;
        synchronized (this) {
            if (closed) {
                return;
            }
            closed = true;
            ownedRelease = release;
        }
        ownedRelease.run();
    }
}
