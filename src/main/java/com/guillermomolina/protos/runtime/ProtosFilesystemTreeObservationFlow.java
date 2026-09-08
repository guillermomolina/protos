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

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/**
 * I024-A host-neutral asynchronous substrate for D046 Filesystem tree observation.
 *
 * <p>This slice does not install {@code entries} or {@code captureTree} on a public Filesystem
 * capability and does not provide a host filesystem implementation. It owns only Path-domain
 * preflight, independent Future/cancellation/Actor-lifecycle mechanics, defensive backend-result
 * snapshot, and captured-result custody transfer.
 */
public final class ProtosFilesystemTreeObservationFlow {
    @FunctionalInterface
    public interface Cancellation {
        void cancel();
    }

    public enum EntryKind {
        REGULAR,
        DIRECTORY,
        LINK,
        OTHER
    }

    /** Inert exact-name/no-follow-kind backend observation. It carries no authority. */
    public record Entry(String name, EntryKind kind) {
        public Entry {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(kind, "kind");
            if (name.isEmpty() || name.equals(".") || name.equals("..")) {
                throw new IllegalArgumentException("entry name is not one exact stored child");
            }
        }
    }

    /** Opaque host-neutral custody token for one successfully captured immutable tree. */
    public interface CapturedTree {}

    public interface EntriesCompletion {
        void succeeded(List<Entry> entries);

        void failed();
    }

    public interface CaptureCompletion {
        void succeeded(CapturedTree capturedTree, Runnable releaseIfUntransferred);

        void failed();
    }

    @FunctionalInterface
    public interface EntriesBackend {
        Cancellation entries(ProtosPathValue path, EntriesCompletion completion);
    }

    @FunctionalInterface
    public interface CaptureBackend {
        Cancellation captureTree(ProtosPathValue path, CaptureCompletion completion);
    }

    /**
     * Semantic result bridge injected by the caller.
     *
     * <p>I024-B owns the standard Array/descriptor and Filesystem-capability materialization.
     */
    public interface ResultMaterializer {
        ProtosObjectValue entries(List<Entry> entries);

        ProtosObjectValue capturedTree(CapturedTree capturedTree);
    }

    private final ProtosObjectValue filesystemCapability;
    private final ProtosActorExecutionDomain domain;
    private final ProtosObjectValue futurePrototype;
    private final EntriesBackend entriesBackend;
    private final CaptureBackend captureBackend;
    private final ResultMaterializer materializer;

    public ProtosFilesystemTreeObservationFlow(
            ProtosObjectValue filesystemCapability,
            ProtosActivation bootstrapActivation,
            EntriesBackend entriesBackend,
            CaptureBackend captureBackend,
            ResultMaterializer materializer) {
        this.filesystemCapability =
                Objects.requireNonNull(filesystemCapability, "filesystemCapability");
        Objects.requireNonNull(bootstrapActivation, "bootstrapActivation");
        this.domain = bootstrapActivation.executionDomain();
        this.futurePrototype = bootstrapActivation.prelude().orElseThrow().futurePrototype();
        this.entriesBackend = Objects.requireNonNull(entriesBackend, "entriesBackend");
        this.captureBackend = Objects.requireNonNull(captureBackend, "captureBackend");
        this.materializer = Objects.requireNonNull(materializer, "materializer");
    }

    public ProtosFutureValue entries(ProtosActivation activation, Object pathValue) {
        Objects.requireNonNull(activation, "activation");
        requireDomain(activation);
        if (!(pathValue instanceof ProtosPathValue path)) {
            return failedFuture(
                    activation, ProtosCoreErrors.StandardError.INVALID_I_O_ARGUMENT);
        }

        Invocation invocation = begin(activation);
        if (invocation.terminal()) {
            return invocation.future();
        }

        try {
            invocation.install(
                    entriesBackend.entries(
                            path,
                            new EntriesCompletion() {
                                @Override
                                public void succeeded(List<Entry> entries) {
                                    List<Entry> snapshot;
                                    ProtosObjectValue value;
                                    try {
                                        snapshot = snapshotEntries(entries);
                                        if (invocation.terminal()) {
                                            return;
                                        }
                                        value =
                                                Objects.requireNonNull(
                                                        materializer.entries(snapshot),
                                                        "entries materializer result");
                                    } catch (RuntimeException invalidBackendResult) {
                                        invocation.fail();
                                        return;
                                    }
                                    invocation.succeed(value, () -> {});
                                }

                                @Override
                                public void failed() {
                                    invocation.fail();
                                }
                            }));
        } catch (RuntimeException backendFailure) {
            invocation.fail();
        }
        return invocation.future();
    }

    public ProtosFutureValue captureTree(ProtosActivation activation, Object pathValue) {
        Objects.requireNonNull(activation, "activation");
        requireDomain(activation);
        if (!(pathValue instanceof ProtosPathValue path)) {
            return failedFuture(
                    activation, ProtosCoreErrors.StandardError.INVALID_I_O_ARGUMENT);
        }

        Invocation invocation = begin(activation);
        if (invocation.terminal()) {
            return invocation.future();
        }

        try {
            invocation.install(
                    captureBackend.captureTree(
                            path,
                            new CaptureCompletion() {
                                @Override
                                public void succeeded(
                                        CapturedTree capturedTree,
                                        Runnable releaseIfUntransferred) {
                                    if (releaseIfUntransferred == null) {
                                        invocation.fail();
                                        return;
                                    }
                                    if (capturedTree == null) {
                                        releaseSafely(releaseIfUntransferred);
                                        invocation.fail();
                                        return;
                                    }
                                    if (invocation.terminal()) {
                                        releaseSafely(releaseIfUntransferred);
                                        return;
                                    }

                                    ProtosObjectValue value;
                                    try {
                                        value =
                                                Objects.requireNonNull(
                                                        materializer.capturedTree(capturedTree),
                                                        "capture materializer result");
                                    } catch (RuntimeException invalidBackendResult) {
                                        releaseSafely(releaseIfUntransferred);
                                        invocation.fail();
                                        return;
                                    }
                                    invocation.succeed(value, releaseIfUntransferred);
                                }

                                @Override
                                public void failed() {
                                    invocation.fail();
                                }
                            }));
        } catch (RuntimeException backendFailure) {
            invocation.fail();
        }
        return invocation.future();
    }

    private Invocation begin(ProtosActivation activation) {
        ProtosIoLifecycle lifecycle =
                new ProtosIoLifecycle(
                        filesystemCapability,
                        futurePrototype,
                        domain,
                        completion -> completion.succeeded());
        return new Invocation(activation, lifecycle);
    }

    private final class Invocation {
        private final ProtosActivation activation;
        private final ProtosIoLifecycle lifecycle;
        private final ProtosIoOperation operation;
        private final CancellationBridge cancellationBridge = new CancellationBridge();

        Invocation(ProtosActivation activation, ProtosIoLifecycle lifecycle) {
            this.activation = activation;
            this.lifecycle = lifecycle;
            this.operation = lifecycle.beginOperation(activation);
            if (operation.future().isPending()) {
                operation.onCancellation(cancellationBridge::requestCancellation);
            }
        }

        ProtosFutureValue future() {
            return operation.future();
        }

        boolean terminal() {
            return operation.terminal();
        }

        void install(Cancellation cancellation) {
            cancellationBridge.install(cancellation);
        }

        void succeed(ProtosObjectValue value, Runnable releaseIfUntransferred) {
            Objects.requireNonNull(value, "value");
            Objects.requireNonNull(releaseIfUntransferred, "releaseIfUntransferred");
            boolean release = false;
            synchronized (lifecycle) {
                if (operation.terminal()) {
                    release = true;
                } else if (!operation.commit()) {
                    release = true;
                } else if (!operation.resolve(value)) {
                    release = true;
                }
            }
            if (release) {
                releaseSafely(releaseIfUntransferred);
            }
        }

        void fail() {
            synchronized (lifecycle) {
                if (!operation.terminal()) {
                    operation.fail(
                            ProtosCoreErrors.newOccurrence(
                                    activation, ProtosCoreErrors.StandardError.I_O_ERROR));
                }
            }
        }
    }

    private void requireDomain(ProtosActivation activation) {
        if (activation.executionDomain() != domain) {
            throw new IllegalArgumentException(
                    "Filesystem tree-observation flow belongs to another Actor domain");
        }
    }

    private ProtosFutureValue failedFuture(
            ProtosActivation activation, ProtosCoreErrors.StandardError error) {
        ProtosFutureValue future = new ProtosFutureValue(futurePrototype, domain);
        future.fail(ProtosCoreErrors.newOccurrence(activation, error));
        return future;
    }

    private static List<Entry> snapshotEntries(List<Entry> entries) {
        List<Entry> snapshot = List.copyOf(Objects.requireNonNull(entries, "entries"));
        HashSet<String> exactNames = new HashSet<>();
        for (Entry entry : snapshot) {
            Objects.requireNonNull(entry, "entry");
            if (!exactNames.add(entry.name())) {
                throw new IllegalArgumentException("duplicate exact direct-child name");
            }
        }
        return snapshot;
    }

    private static void releaseSafely(Runnable release) {
        try {
            release.run();
        } catch (RuntimeException ignored) {
            // Terminal/cancelled Future cannot be resurrected by custody cleanup failure.
        }
    }

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
