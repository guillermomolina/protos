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
 * I016-A host-neutral asynchronous Filesystem.open acquisition substrate.
 *
 * <p>This class deliberately does not install a public Filesystem capability or construct a File.
 * A later I016 slice may expose the language-level surface only when the returned File can satisfy
 * its complete access/lifecycle/position contract. This slice owns the normative preflight,
 * invocation-time semantic snapshot, cancellation, commitment, result-custody, and independent-open
 * acquisition behavior.
 */
public final class ProtosFilesystemOpenFlow {
    @FunctionalInterface
    public interface Cancellation {
        void cancel();
    }

    /**
     * Backend completion handshake for one already-preflighted open.
     *
     * <p>The backend must call {@link #commitPortableEffect()} immediately before publishing the
     * first irreversible namespace/content effect attributable to this open. A false result means
     * cancellation already won and that effect must not be published.
     */
    public interface OpenCompletion {
        boolean commitPortableEffect();

        /**
         * Transfers a successfully acquired File result.
         *
         * <p>{@code releaseIfUntransferred} releases backend/native custody if cancellation won
         * before a result-only open could commit. It is not compensating rollback for a previously
         * committed portable creation/truncation effect.
         */
        void succeeded(ProtosObjectValue file, Runnable releaseIfUntransferred);

        /** Reports an ordinary backend/open failure. */
        void failed();
    }

    /**
     * Backend boundary after semantic preflight.
     *
     * <p>Each invocation is independent; this flow adds no Filesystem-wide or Path-wide FIFO. The
     * backend owns race-free confined selection/acquisition and must not expose a File until it can
     * preserve the stable selected-resource binding required by FILESYSTEM.md.
     */
    @FunctionalInterface
    public interface Backend {
        Cancellation open(
                ProtosPathValue path,
                ProtosFilesystemOpenOptions options,
                OpenCompletion completion);
    }

    private final ProtosActorExecutionDomain domain;
    private final Backend backend;
    private final ProtosIoLifecycle operationLifecycle;

    public ProtosFilesystemOpenFlow(
            ProtosObjectValue filesystemCapability,
            ProtosActivation bootstrapActivation,
            Backend backend) {
        Objects.requireNonNull(filesystemCapability, "filesystemCapability");
        Objects.requireNonNull(bootstrapActivation, "bootstrapActivation");
        this.domain = bootstrapActivation.executionDomain();
        this.backend = Objects.requireNonNull(backend, "backend");

        // Filesystem acquisition itself is not a Closable receiver. Reuse the established I/O
        // operation/Actor-termination commitment machinery without ever exposing this lifecycle's
        // close operation.
        this.operationLifecycle =
                new ProtosIoLifecycle(
                        filesystemCapability,
                        bootstrapActivation.prelude().orElseThrow().futurePrototype(),
                        domain,
                        completion -> completion.succeeded());
    }

    /** One-argument standard open semantic core. No public message is installed by I016-A. */
    public ProtosFutureValue open(ProtosActivation activation, Object pathValue) {
        return openCaptured(activation, pathValue, ProtosFilesystemOpenOptions.defaults());
    }

    /** Two-argument standard open semantic core. No public message is installed by I016-A. */
    public ProtosFutureValue open(
            ProtosActivation activation, Object pathValue, Object optionsValue) {
        Objects.requireNonNull(activation, "activation");
        requireDomain(activation);

        ProtosFilesystemOpenOptions captured;
        try {
            captured = ProtosFilesystemOpenOptions.capture(optionsValue);
        } catch (IllegalArgumentException invalid) {
            return failedFuture(activation, ProtosCoreErrors.StandardError.INVALID_I_O_ARGUMENT);
        }
        return openCaptured(activation, pathValue, captured);
    }

    private ProtosFutureValue openCaptured(
            ProtosActivation activation,
            Object pathValue,
            ProtosFilesystemOpenOptions captured) {
        Objects.requireNonNull(activation, "activation");
        requireDomain(activation);
        if (!(pathValue instanceof ProtosPathValue path)) {
            return failedFuture(activation, ProtosCoreErrors.StandardError.INVALID_I_O_ARGUMENT);
        }

        // All semantic path/options validation owned by this slice has completed before this point.
        // The backend is therefore never exercised merely to discover an invalid supplied tuple.
        ProtosIoOperation operation = operationLifecycle.beginOperation(activation);
        if (!operation.future().isPending()) {
            return operation.future();
        }

        CancellationBridge cancellationBridge = new CancellationBridge();
        operation.onCancellation(cancellationBridge::requestCancellation);

        OpenCompletion completion =
                new OpenCompletion() {
                    @Override
                    public boolean commitPortableEffect() {
                        return operation.commit();
                    }

                    @Override
                    public void succeeded(
                            ProtosObjectValue file, Runnable releaseIfUntransferred) {
                        Objects.requireNonNull(file, "file");
                        Objects.requireNonNull(
                                releaseIfUntransferred, "releaseIfUntransferred");

                        if (!operation.committed() && !operation.commit()) {
                            releaseSafely(releaseIfUntransferred);
                            return;
                        }
                        if (!operation.resolve(file)) {
                            // Defensive custody rule for a backend duplicate/late completion.
                            releaseSafely(releaseIfUntransferred);
                        }
                    }

                    @Override
                    public void failed() {
                        operation.fail(
                                ProtosCoreErrors.newOccurrence(
                                        activation, ProtosCoreErrors.StandardError.I_O_ERROR));
                    }
                };

        try {
            cancellationBridge.install(backend.open(path, captured, completion));
        } catch (RuntimeException backendFailure) {
            operation.fail(
                    ProtosCoreErrors.newOccurrence(
                            activation, ProtosCoreErrors.StandardError.I_O_ERROR));
        }
        return operation.future();
    }

    private void requireDomain(ProtosActivation activation) {
        if (activation.executionDomain() != domain) {
            throw new IllegalArgumentException(
                    "Filesystem open flow belongs to another Actor domain");
        }
    }

    private ProtosFutureValue failedFuture(
            ProtosActivation activation, ProtosCoreErrors.StandardError error) {
        ProtosFutureValue future =
                new ProtosFutureValue(
                        activation.prelude().orElseThrow().futurePrototype(), domain);
        future.fail(ProtosCoreErrors.newOccurrence(activation, error));
        return future;
    }

    private static void releaseSafely(Runnable release) {
        try {
            release.run();
        } catch (RuntimeException ignored) {
            // The Future has already reached the only portable terminal outcome available here.
            // Backend/native cleanup failures remain implementation custody and cannot resurrect it.
        }
    }

    /** Handles cancellation racing backend registration without losing the cancellation request. */
    private static final class CancellationBridge {
        private Cancellation cancellation;
        private boolean cancellationRequested;

        void requestCancellation() {
            Cancellation toCancel;
            synchronized (this) {
                cancellationRequested = true;
                toCancel = cancellation;
            }
            if (toCancel != null) {
                toCancel.cancel();
            }
        }

        void install(Cancellation installed) {
            boolean cancelNow;
            synchronized (this) {
                cancellation = installed;
                cancelNow = cancellationRequested && installed != null;
            }
            if (cancelNow) {
                installed.cancel();
            }
        }
    }
}
