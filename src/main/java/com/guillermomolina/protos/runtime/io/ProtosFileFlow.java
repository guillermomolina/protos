/* APL-1.0 licensed work; see LICENSE.TXT. */
package com.guillermomolina.protos.runtime;

import java.util.ArrayDeque;
import java.util.Objects;

/**
 * I016-C ordered positioned File capability core.
 *
 * <p>The Protos logical cursor lives here rather than in a native/backend cursor. Backends receive
 * explicit positions, so speculative/native cursor movement cannot leak into the standard File
 * position contract. One File owns one sequence-state/lifecycle ordering domain and therefore one
 * independent logical cursor; no placement coordination exists across File aliases.
 */
public final class ProtosFileFlow {
    @FunctionalInterface
    public interface Cancellation {
        void cancel();
    }

    public interface ReadCompletion {
        void data(byte[] bytes);
        void eof();
        void failed();
    }

    /**
     * A positioned write backend must call commitFirstContribution() immediately before publishing
     * the first irreversible byte/growth effect. It may proceed only when true is returned.
     */
    public interface WriteCompletion {
        boolean commitFirstContribution();
        void succeeded();

        /** An ordinary backend failure whose exact accepted prefix is known. */
        void failed(int contributedPrefix);

        /**
         * An unexpected failure whose progress is unknown: the backend cannot tell how many bytes,
         * if any, became effective after {@link #commitFirstContribution()}. No prefix is
         * invented; before the commitment no contribution is possible, so the failure is then an
         * ordinary zero-effect failure. Ignored once the write already has its outcome.
         */
        void failedWithUnknownProgress();
    }

    /** A host-representable non-negative position or size; the flow forms the exact Integer. */
    public interface IntegerCompletion {
        void succeeded(long value);
        void failed();
    }

    /**
     * Failure-atomic content mutation handshake. commitChange() is called immediately before the
     * complete requested mutation becomes observable; a no-op may succeed without calling it.
     */
    public interface ChangeCompletion {
        boolean commitChange();
        void succeeded();
        void failed();
    }

    /** Durability handshake matching the standard Syncable commitment boundary. */
    public interface SyncCompletion {
        boolean commitDurability();
        void succeeded();
        void failed();
    }

    public interface CloseCompletion {
        void succeeded();
        void failed();
    }

    /** Stable already-selected resource custody. Path lookup is not part of this interface. */
    public interface Resource {
        void close(CloseCompletion completion);
    }

    public interface ReadableResource extends Resource {
        /** {@code position} is non-negative; the flow fails unrepresentable positions itself. */
        Cancellation readAt(long position, int maxBytes, ReadCompletion completion);
    }

    /**
     * Positioned writes replace from the explicit logical offset. If the first contribution begins
     * beyond EOF, the backend must provide the standard deterministic zero-valued logical gap.
     */
    public interface WritableResource extends Resource {
        /** {@code position} is non-negative; the flow fails unrepresentable positions itself. */
        Cancellation writeAt(long position, byte[] bytes, WriteCompletion completion);
    }

    /** Internal end-position support needed for ByteSeekable.seekToEnd; does not imply ByteSized. */
    public interface SeekableResource extends Resource {
        Cancellation endPosition(IntegerCompletion completion);
    }

    public interface SizedResource extends Resource {
        Cancellation size(IntegerCompletion completion);
    }

    public interface TruncatableResource extends Resource {
        /**
         * {@code size} is non-negative. Truncation never extends, so the flow passes a size
         * beyond the host range as {@link Long#MAX_VALUE}, which is equally a no-op.
         */
        Cancellation truncate(long size, ChangeCompletion completion);
    }

    public interface SyncableResource extends Resource {
        Cancellation sync(SyncCompletion completion);
    }

    /** Stable public capability shape for one File lifetime. */
    public record Capabilities(
            boolean readable,
            boolean writable,
            boolean seekable,
            boolean sized,
            boolean truncatable,
            boolean syncable) {
        public Capabilities {
            if (!readable && !writable) {
                throw new IllegalArgumentException("File requires read and/or write access");
            }
            if (truncatable && !writable) {
                throw new IllegalArgumentException("Truncatable File must be writable");
            }
        }
    }

    private enum Kind {
        READ,
        WRITE,
        POSITION,
        SEEK,
        SEEK_BY,
        SEEK_END,
        SIZE,
        TRUNCATE,
        SYNC
    }

    private static final int DEFAULT_MAX_RETAINED_WRITE_BYTES = 1024 * 1024;
    private static final Object ZERO = ProtosNumericValueSupport.integer(0L);

    final ProtosObjectValue receiver;
    final ProtosObjectValue bytesPrototype;
    private final ProtosActivation constructionActivation;
    /*
     * Owner of a position beyond the signed-64 range: the File's own domain Prelude. The exact
     * Integer service answers null for an unowned large result, which must never become a
     * logical position or a resolved guest value, so the Prelude is required at construction.
     */
    private final ProtosPrelude owningPrelude;
    final ProtosActorExecutionDomain domain;
    final Resource resource;
    private final Capabilities capabilities;
    private final ProtosIoLifecycle lifecycle;
    private final int maxRetainedWriteBytes;
    private final ArrayDeque<Request> operations = new ArrayDeque<>();
    private final ProtosFileFlowResourceCalls resourceCalls =
            new ProtosFileFlowResourceCalls(this);

    /* The exact semantic Integer position; beyond the signed-64 range only after seeks. */
    private Object logicalPosition = ZERO;
    /*
     * A committed failed write may have contributed an unknown prefix.
     * The exact logical cursor still exists, but it cannot be observed or
     * used for relative positioning until an absolute seek succeeds.
     */
    private boolean logicalPositionKnown = true;
    private int retainedWriteBytes;

    public ProtosFileFlow(
            ProtosObjectValue receiver,
            ProtosObjectValue bytesPrototype,
            ProtosActivation constructionActivation,
            Resource resource,
            Capabilities capabilities) {
        this(
                receiver,
                bytesPrototype,
                constructionActivation,
                resource,
                capabilities,
                DEFAULT_MAX_RETAINED_WRITE_BYTES);
    }

    ProtosFileFlow(
            ProtosObjectValue receiver,
            ProtosObjectValue bytesPrototype,
            ProtosActivation constructionActivation,
            Resource resource,
            Capabilities capabilities,
            int maxRetainedWriteBytes) {
        this.receiver = Objects.requireNonNull(receiver, "receiver");
        this.bytesPrototype = Objects.requireNonNull(bytesPrototype, "bytesPrototype");
        this.constructionActivation =
                Objects.requireNonNull(constructionActivation, "constructionActivation");
        this.owningPrelude =
                constructionActivation.prelude().orElseThrow(
                        () -> new IllegalStateException("File flow requires the Core prelude"));
        this.domain = constructionActivation.executionDomain();
        this.resource = Objects.requireNonNull(resource, "resource");
        this.capabilities = Objects.requireNonNull(capabilities, "capabilities");
        if (maxRetainedWriteBytes < 0) {
            throw new IllegalArgumentException("negative retained-write bound");
        }
        this.maxRetainedWriteBytes = maxRetainedWriteBytes;
        verifyCapabilityHonesty(resource, capabilities);
        this.lifecycle =
                new ProtosIoLifecycle(
                        receiver,
                        owningPrelude.futurePrototype(),
                        domain,
                        this::startResourceClose);
    }

    public Capabilities capabilities() {
        return capabilities;
    }

    public ProtosFutureValue read(ProtosActivation activation, Object maxBytesValue) {
        check(activation);
        if (!capabilities.readable()) {
            return ioFailedFuture(activation);
        }
        if (!ProtosNumericValueSupport.isIntegerInIntRange(maxBytesValue)
                || ProtosNumericValueSupport.integerSignum(maxBytesValue) <= 0) {
            return invalidFuture(activation);
        }
        return enqueue(new Request(Kind.READ, begin(activation), maxBytesValue, null));
    }

    public ProtosFutureValue write(ProtosActivation activation, Object value) {
        check(activation);
        if (!capabilities.writable()) {
            return ioFailedFuture(activation);
        }
        if (!(value instanceof ProtosBytesValue bytes)) {
            return invalidFuture(activation);
        }
        byte[] snapshot = bytes.octetSnapshot();
        synchronized (this) {
            if (snapshot.length > maxRetainedWriteBytes - retainedWriteBytes) {
                return capacityFailedFuture(activation);
            }
            retainedWriteBytes += snapshot.length;
        }
        ProtosIoOperation operation = begin(activation);
        if (!operation.future().isPending()) {
            releaseWriteRetention(snapshot.length);
            return operation.future();
        }
        return enqueue(new Request(Kind.WRITE, operation, null, snapshot));
    }

    public ProtosFutureValue position(ProtosActivation activation) {
        check(activation);
        if (!capabilities.seekable()) {
            return ioFailedFuture(activation);
        }
        return enqueue(new Request(Kind.POSITION, begin(activation), null, null));
    }

    public ProtosFutureValue seek(ProtosActivation activation, Object value) {
        check(activation);
        if (!capabilities.seekable()) {
            return ioFailedFuture(activation);
        }
        if (!ProtosNumericValueSupport.isCurrentInteger(value)
                || ProtosNumericValueSupport.integerSignum(value) < 0) {
            return invalidFuture(activation);
        }
        return enqueue(new Request(Kind.SEEK, begin(activation), value, null));
    }

    public ProtosFutureValue seekBy(ProtosActivation activation, Object value) {
        check(activation);
        if (!capabilities.seekable()) {
            return ioFailedFuture(activation);
        }
        if (!ProtosNumericValueSupport.isCurrentInteger(value)) {
            return invalidFuture(activation);
        }
        return enqueue(new Request(Kind.SEEK_BY, begin(activation), value, null));
    }

    public ProtosFutureValue seekToEnd(ProtosActivation activation) {
        check(activation);
        if (!capabilities.seekable()) {
            return ioFailedFuture(activation);
        }
        return enqueue(new Request(Kind.SEEK_END, begin(activation), null, null));
    }

    public ProtosFutureValue size(ProtosActivation activation) {
        check(activation);
        if (!capabilities.sized()) {
            return ioFailedFuture(activation);
        }
        return enqueue(new Request(Kind.SIZE, begin(activation), null, null));
    }

    public ProtosFutureValue truncate(ProtosActivation activation, Object value) {
        check(activation);
        if (!capabilities.truncatable()) {
            return ioFailedFuture(activation);
        }
        if (!ProtosNumericValueSupport.isCurrentInteger(value)
                || ProtosNumericValueSupport.integerSignum(value) < 0) {
            return invalidFuture(activation);
        }
        return enqueue(new Request(Kind.TRUNCATE, begin(activation), value, null));
    }

    public ProtosFutureValue sync(ProtosActivation activation) {
        check(activation);
        if (!capabilities.syncable()) {
            return ioFailedFuture(activation);
        }
        return enqueue(new Request(Kind.SYNC, begin(activation), null, null));
    }

    /** Standard committed-at-invocation Closable lifecycle. */
    public ProtosFutureValue close(ProtosActivation activation) {
        check(activation);
        return lifecycle.close(activation);
    }

    private ProtosIoOperation begin(ProtosActivation activation) {
        return lifecycle.beginOperation(activation);
    }

    private ProtosFutureValue enqueue(Request request) {
        if (!request.operation.future().isPending()) {
            if (request.kind == Kind.WRITE) {
                releaseWriteRetention(request.bytes.length);
            }
            return request.operation.future();
        }
        synchronized (this) {
            operations.addLast(request);
        }
        request.operation.onCancellation(() -> cancellationRequested(request));
        pump();
        return request.operation.future();
    }

    private void pump() {
        Request request;
        synchronized (this) {
            while (true) {
                request = operations.peekFirst();
                if (request == null) {
                    return;
                }
                if (request.operation.terminal()) {
                    operations.removeFirst();
                    if (request.kind == Kind.WRITE) {
                        retainedWriteBytes -= request.bytes.length;
                    }
                    continue;
                }
                if (request.started) {
                    return;
                }
                request.started = true;
                break;
            }
        }

        if (requiresKnownPosition(request.kind) && !positionKnown()) {
            failIo(request);
            finish(request);
            return;
        }

        switch (request.kind) {
            case READ -> resourceCalls.startRead(request);
            case WRITE -> resourceCalls.startWrite(request);
            case POSITION -> completeLocalPosition(request);
            case SEEK -> completeSeek(request);
            case SEEK_BY -> completeSeekBy(request);
            case SEEK_END -> resourceCalls.startSeekToEnd(request);
            case SIZE -> resourceCalls.startSize(request);
            case TRUNCATE -> resourceCalls.startTruncate(request);
            case SYNC -> resourceCalls.startSync(request);
        }
    }

    void setPositionAfterWrite(Request request, int contributedPrefix) {
        synchronized (this) {
            if (request.startPosition == null) {
                throw new IllegalStateException("write contribution without start position");
            }
            logicalPosition = advanced(request.startPosition, contributedPrefix);
        }
    }

    private void completeLocalPosition(Request request) {
        Object position = currentPosition();
        if (request.operation.commit()) {
            request.operation.resolve(position);
        }
        finish(request);
    }

    private void completeSeek(Request request) {
        if (request.operation.commit()) {
            setLogicalPosition(request.number);
            request.operation.resolve(request.number);
        }
        finish(request);
    }

    private void completeSeekBy(Request request) {
        Object target =
                ProtosNumericValueSupport.addIntegers(
                        currentPosition(), request.number, owningPrelude);
        if (ProtosNumericValueSupport.integerSignum(target) < 0) {
            failIo(request);
            finish(request);
            return;
        }
        if (request.operation.commit()) {
            setLogicalPosition(target);
            request.operation.resolve(target);
        }
        finish(request);
    }

    private void cancellationRequested(Request request) {
        Cancellation cancellation;
        boolean removed = false;
        synchronized (this) {
            request.cancellationRequested = true;
            cancellation = request.cancellation;
            // Pre-commit Future cancellation and close cutover both make the operation terminal.
            // A post-commit cancellation request may ask the backend to stop safely but must not
            // let a later File operation pass the committed operation's still-pending aftermath.
            if (request.operation.terminal()) {
                removed = operations.remove(request);
                if (removed && request.kind == Kind.WRITE) {
                    retainedWriteBytes -= request.bytes.length;
                }
            }
        }
        if (cancellation != null) {
            cancellation.cancel();
        }
        if (removed) {
            pump();
        }
    }

    void setCancellation(Request request, Cancellation cancellation) {
        boolean cancelNow;
        synchronized (this) {
            request.cancellation = cancellation;
            cancelNow = request.cancellationRequested && cancellation != null;
        }
        if (cancelNow) {
            cancellation.cancel();
        }
    }

    void finish(Request request) {
        boolean removed;
        synchronized (this) {
            removed = operations.remove(request);
            if (removed && request.kind == Kind.WRITE) {
                retainedWriteBytes -= request.bytes.length;
            }
        }
        if (removed) {
            pump();
        }
    }

    private void startResourceClose(ProtosIoLifecycle.ReleaseCompletion completion) {
        try {
            resource.close(
                    new CloseCompletion() {
                        @Override
                        public void succeeded() {
                            completion.succeeded();
                        }

                        @Override
                        public void failed() {
                            if (ProtosHostCompletionThread.isCurrent()) {
                                // I156: a host-thread release failure lets the lifecycle build
                                // the IOError inside the owning Actor.
                                completion.failedWithIoErrorForRuntime();
                                return;
                            }
                            completion.failed(
                                    ProtosCoreErrors.newOccurrence(
                                            constructionActivation,
                                            ProtosCoreErrors.StandardError.I_O_ERROR));
                        }
                    });
        } catch (RuntimeException backendFailure) {
            completion.failed(
                    ProtosCoreErrors.newOccurrence(
                            constructionActivation, ProtosCoreErrors.StandardError.I_O_ERROR));
        }
    }

    void setLogicalPosition(Object position) {
        synchronized (this) {
            logicalPosition = position;
            logicalPositionKnown = true;
        }
    }

    void markPositionUnknown() {
        synchronized (this) {
            logicalPositionKnown = false;
        }
    }

    private boolean positionKnown() {
        synchronized (this) {
            return logicalPositionKnown;
        }
    }

    private static boolean requiresKnownPosition(Kind kind) {
        return switch (kind) {
            case READ, WRITE, POSITION, SEEK_BY -> true;
            case SEEK, SEEK_END, SIZE, TRUNCATE, SYNC -> false;
        };
    }

    Object currentPosition() {
        synchronized (this) {
            return logicalPosition;
        }
    }

    void failIo(Request request) {
        request.operation.fail(
                ProtosCoreErrors.newOccurrence(
                        request.operation.origin(), ProtosCoreErrors.StandardError.I_O_ERROR));
    }

    private ProtosFutureValue invalidFuture(ProtosActivation activation) {
        return failedFuture(activation, ProtosCoreErrors.StandardError.INVALID_I_O_ARGUMENT);
    }

    private ProtosFutureValue ioFailedFuture(ProtosActivation activation) {
        return failedFuture(activation, ProtosCoreErrors.StandardError.I_O_ERROR);
    }

    private ProtosFutureValue capacityFailedFuture(ProtosActivation activation) {
        return failedFuture(activation, ProtosCoreErrors.StandardError.I_O_CAPACITY_EXHAUSTED);
    }

    private ProtosFutureValue failedFuture(
            ProtosActivation activation, ProtosCoreErrors.StandardError error) {
        ProtosFutureValue future =
                new ProtosFutureValue(
                        activation.prelude().orElseThrow().futurePrototype(), domain);
        future.fail(ProtosCoreErrors.newOccurrence(activation, error));
        return future;
    }

    private void releaseWriteRetention(int bytes) {
        synchronized (this) {
            retainedWriteBytes -= bytes;
        }
    }

    private void check(ProtosActivation activation) {
        Objects.requireNonNull(activation, "activation");
        if (activation.executionDomain() != domain) {
            throw new IllegalArgumentException("File belongs to another Actor domain");
        }
    }

    private static void verifyCapabilityHonesty(Resource resource, Capabilities capabilities) {
        if (capabilities.readable() && !(resource instanceof ReadableResource)) {
            throw new IllegalArgumentException("readable File requires ReadableResource");
        }
        if (capabilities.writable() && !(resource instanceof WritableResource)) {
            throw new IllegalArgumentException("writable File requires WritableResource");
        }
        if (capabilities.seekable() && !(resource instanceof SeekableResource)) {
            throw new IllegalArgumentException("seekable File requires SeekableResource");
        }
        if (capabilities.sized() && !(resource instanceof SizedResource)) {
            throw new IllegalArgumentException("sized File requires SizedResource");
        }
        if (capabilities.truncatable() && !(resource instanceof TruncatableResource)) {
            throw new IllegalArgumentException("truncatable File requires TruncatableResource");
        }
        if (capabilities.syncable() && !(resource instanceof SyncableResource)) {
            throw new IllegalArgumentException("syncable File requires SyncableResource");
        }
    }

    Object advanced(Object start, int octets) {
        return ProtosNumericValueSupport.addIntegers(
                start, ProtosNumericValueSupport.integer(octets), owningPrelude);
    }

    static final class Request {
        final Kind kind;
        final ProtosIoOperation operation;
        /* The exact semantic Integer argument, if any. */
        final Object number;
        final byte[] bytes;
        boolean started;
        boolean cancellationRequested;
        Cancellation cancellation;
        Object startPosition;

        Request(
                Kind kind,
                ProtosIoOperation operation,
                Object number,
                byte[] bytes) {
            this.kind = kind;
            this.operation = operation;
            this.number = number;
            this.bytes = bytes;
        }
    }
}
