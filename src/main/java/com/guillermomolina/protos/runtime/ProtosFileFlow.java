/* APL-1.0 licensed work; see LICENSE.TXT. */
package com.guillermomolina.protos.runtime;

import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Objects;

/**
 * I016-B ordered positioned File capability core.
 *
 * <p>The Protos logical cursor lives here rather than in a native/backend cursor. Backends receive
 * explicit positions, so speculative/native cursor movement cannot leak into the standard File
 * position contract. One File owns one sequence-state/lifecycle ordering domain; separately opened
 * Files remain independent even when a later backend maps them to one resource.
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
        void failed(int contributedPrefix);
    }

    public interface IntegerCompletion {
        void succeeded(BigInteger value);
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
        Cancellation readAt(BigInteger position, int maxBytes, ReadCompletion completion);
    }

    /**
     * Positioned writes replace from the explicit logical offset. If the first contribution begins
     * beyond EOF, the backend must provide the standard deterministic zero-valued logical gap.
     */
    public interface WritableResource extends Resource {
        Cancellation writeAt(BigInteger position, byte[] bytes, WriteCompletion completion);
    }

    /** Internal end-position support needed for ByteSeekable.seekToEnd; does not imply ByteSized. */
    public interface SeekableResource extends Resource {
        Cancellation endPosition(IntegerCompletion completion);
    }

    public interface SizedResource extends Resource {
        Cancellation size(IntegerCompletion completion);
    }

    public interface TruncatableResource extends Resource {
        Cancellation truncate(BigInteger size, ChangeCompletion completion);
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

    private final ProtosObjectValue receiver;
    private final ProtosObjectValue bytesPrototype;
    private final ProtosActivation constructionActivation;
    private final ProtosActorExecutionDomain domain;
    private final Resource resource;
    private final Capabilities capabilities;
    private final ProtosIoLifecycle lifecycle;
    private final int maxRetainedWriteBytes;
    private final ArrayDeque<Request> operations = new ArrayDeque<>();

    private BigInteger logicalPosition = BigInteger.ZERO;
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
                        constructionActivation.prelude().orElseThrow().futurePrototype(),
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
        BigInteger maxBytes = integer(maxBytesValue);
        if (maxBytes == null
                || maxBytes.signum() <= 0
                || maxBytes.compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) > 0) {
            return invalidFuture(activation);
        }
        return enqueue(new Request(Kind.READ, begin(activation), maxBytes, null));
    }

    public ProtosFutureValue write(ProtosActivation activation, Object value) {
        check(activation);
        if (!capabilities.writable()) {
            return ioFailedFuture(activation);
        }
        if (!(value instanceof ProtosBytesValue bytes)) {
            return invalidFuture(activation);
        }
        byte[] snapshot = snapshot(bytes);
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
        BigInteger target = integer(value);
        if (target == null || target.signum() < 0) {
            return invalidFuture(activation);
        }
        return enqueue(new Request(Kind.SEEK, begin(activation), target, null));
    }

    public ProtosFutureValue seekBy(ProtosActivation activation, Object value) {
        check(activation);
        if (!capabilities.seekable()) {
            return ioFailedFuture(activation);
        }
        BigInteger displacement = integer(value);
        if (displacement == null) {
            return invalidFuture(activation);
        }
        return enqueue(new Request(Kind.SEEK_BY, begin(activation), displacement, null));
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
        BigInteger target = integer(value);
        if (target == null || target.signum() < 0) {
            return invalidFuture(activation);
        }
        return enqueue(new Request(Kind.TRUNCATE, begin(activation), target, null));
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

        switch (request.kind) {
            case READ -> startRead(request);
            case WRITE -> startWrite(request);
            case POSITION -> completeLocalPosition(request);
            case SEEK -> completeSeek(request);
            case SEEK_BY -> completeSeekBy(request);
            case SEEK_END -> startSeekToEnd(request);
            case SIZE -> startSize(request);
            case TRUNCATE -> startTruncate(request);
            case SYNC -> startSync(request);
        }
    }

    private void startRead(Request request) {
        BigInteger start = currentPosition();
        request.startPosition = start;
        try {
            setCancellation(
                    request,
                    ((ReadableResource) resource)
                            .readAt(
                                    start,
                                    request.number.intValueExact(),
                                    new ReadCompletion() {
                                        @Override
                                        public void data(byte[] bytes) {
                                            completeReadData(request, bytes);
                                        }

                                        @Override
                                        public void eof() {
                                            if (request.operation.commit()) {
                                                request.operation.resolve(ProtosNullValue.INSTANCE);
                                            }
                                            finish(request);
                                        }

                                        @Override
                                        public void failed() {
                                            failIo(request);
                                            finish(request);
                                        }
                                    }));
        } catch (RuntimeException backendFailure) {
            failIo(request);
            finish(request);
        }
    }

    private void completeReadData(Request request, byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        if (bytes.length == 0 || bytes.length > request.number.intValueExact()) {
            failIo(request);
            finish(request);
            return;
        }
        if (!request.operation.commit()) {
            finish(request);
            return;
        }

        ProtosBytesValue result = new ProtosBytesValue(bytesPrototype);
        for (byte octet : bytes) {
            result.indexedAdd(new ProtosIntegerValue(BigInteger.valueOf(octet & 0xff)));
        }
        synchronized (this) {
            logicalPosition = request.startPosition.add(BigInteger.valueOf(bytes.length));
        }
        request.operation.resolve(result);
        finish(request);
    }

    private void startWrite(Request request) {
        BigInteger start = currentPosition();
        request.startPosition = start;
        if (request.bytes.length == 0) {
            if (request.operation.commit()) {
                request.operation.resolve(receiver);
            }
            finish(request);
            return;
        }

        try {
            setCancellation(
                    request,
                    ((WritableResource) resource)
                            .writeAt(
                                    start,
                                    request.bytes.clone(),
                                    new WriteCompletion() {
                                        @Override
                                        public boolean commitFirstContribution() {
                                            return request.operation.commit();
                                        }

                                        @Override
                                        public void succeeded() {
                                            if (!request.operation.committed()
                                                    && !request.operation.commit()) {
                                                finish(request);
                                                return;
                                            }
                                            setPositionAfterWrite(
                                                    request, request.bytes.length);
                                            request.operation.resolve(receiver);
                                            finish(request);
                                        }

                                        @Override
                                        public void failed(int contributedPrefix) {
                                            if (contributedPrefix < 0
                                                    || contributedPrefix > request.bytes.length) {
                                                throw new IllegalArgumentException(
                                                        "invalid contributed write prefix");
                                            }
                                            if (contributedPrefix > 0) {
                                                if (!request.operation.committed()) {
                                                    failIo(request);
                                                    finish(request);
                                                    return;
                                                }
                                                setPositionAfterWrite(
                                                        request, contributedPrefix);
                                            }
                                            failIo(request);
                                            finish(request);
                                        }
                                    }));
        } catch (RuntimeException backendFailure) {
            failIo(request);
            finish(request);
        }
    }

    private void setPositionAfterWrite(Request request, int contributedPrefix) {
        synchronized (this) {
            logicalPosition =
                    request.startPosition.add(BigInteger.valueOf(contributedPrefix));
        }
    }

    private void completeLocalPosition(Request request) {
        BigInteger position = currentPosition();
        if (request.operation.commit()) {
            request.operation.resolve(new ProtosIntegerValue(position));
        }
        finish(request);
    }

    private void completeSeek(Request request) {
        if (request.operation.commit()) {
            synchronized (this) {
                logicalPosition = request.number;
            }
            request.operation.resolve(new ProtosIntegerValue(request.number));
        }
        finish(request);
    }

    private void completeSeekBy(Request request) {
        BigInteger base = currentPosition();
        BigInteger target = base.add(request.number);
        if (target.signum() < 0) {
            failIo(request);
            finish(request);
            return;
        }
        if (request.operation.commit()) {
            synchronized (this) {
                logicalPosition = target;
            }
            request.operation.resolve(new ProtosIntegerValue(target));
        }
        finish(request);
    }

    private void startSeekToEnd(Request request) {
        try {
            setCancellation(
                    request,
                    ((SeekableResource) resource)
                            .endPosition(
                                    new IntegerCompletion() {
                                        @Override
                                        public void succeeded(BigInteger value) {
                                            if (value == null || value.signum() < 0) {
                                                failIo(request);
                                                finish(request);
                                                return;
                                            }
                                            if (request.operation.commit()) {
                                                synchronized (ProtosFileFlow.this) {
                                                    logicalPosition = value;
                                                }
                                                request.operation.resolve(
                                                        new ProtosIntegerValue(value));
                                            }
                                            finish(request);
                                        }

                                        @Override
                                        public void failed() {
                                            failIo(request);
                                            finish(request);
                                        }
                                    }));
        } catch (RuntimeException backendFailure) {
            failIo(request);
            finish(request);
        }
    }

    private void startSize(Request request) {
        try {
            setCancellation(
                    request,
                    ((SizedResource) resource)
                            .size(
                                    new IntegerCompletion() {
                                        @Override
                                        public void succeeded(BigInteger value) {
                                            if (value == null || value.signum() < 0) {
                                                failIo(request);
                                                finish(request);
                                                return;
                                            }
                                            if (request.operation.commit()) {
                                                request.operation.resolve(
                                                        new ProtosIntegerValue(value));
                                            }
                                            finish(request);
                                        }

                                        @Override
                                        public void failed() {
                                            failIo(request);
                                            finish(request);
                                        }
                                    }));
        } catch (RuntimeException backendFailure) {
            failIo(request);
            finish(request);
        }
    }

    private void startTruncate(Request request) {
        try {
            setCancellation(
                    request,
                    ((TruncatableResource) resource)
                            .truncate(
                                    request.number,
                                    new ChangeCompletion() {
                                        @Override
                                        public boolean commitChange() {
                                            return request.operation.commit();
                                        }

                                        @Override
                                        public void succeeded() {
                                            if (!request.operation.committed()
                                                    && !request.operation.commit()) {
                                                finish(request);
                                                return;
                                            }
                                            request.operation.resolve(receiver);
                                            finish(request);
                                        }

                                        @Override
                                        public void failed() {
                                            failIo(request);
                                            finish(request);
                                        }
                                    }));
        } catch (RuntimeException backendFailure) {
            failIo(request);
            finish(request);
        }
    }

    private void startSync(Request request) {
        try {
            setCancellation(
                    request,
                    ((SyncableResource) resource)
                            .sync(
                                    new SyncCompletion() {
                                        @Override
                                        public boolean commitDurability() {
                                            return request.operation.commit();
                                        }

                                        @Override
                                        public void succeeded() {
                                            if (!request.operation.committed()
                                                    && !request.operation.commit()) {
                                                finish(request);
                                                return;
                                            }
                                            request.operation.resolve(receiver);
                                            finish(request);
                                        }

                                        @Override
                                        public void failed() {
                                            failIo(request);
                                            finish(request);
                                        }
                                    }));
        } catch (RuntimeException backendFailure) {
            failIo(request);
            finish(request);
        }
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

    private void setCancellation(Request request, Cancellation cancellation) {
        boolean cancelNow;
        synchronized (this) {
            request.cancellation = cancellation;
            cancelNow = request.cancellationRequested && cancellation != null;
        }
        if (cancelNow) {
            cancellation.cancel();
        }
    }

    private void finish(Request request) {
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

    private BigInteger currentPosition() {
        synchronized (this) {
            return logicalPosition;
        }
    }

    private void failIo(Request request) {
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

    private static BigInteger integer(Object value) {
        if (value instanceof ProtosIntegerValue integer) {
            return integer.value();
        }
        if (value instanceof ProtosFixedIntegerValue integer) {
            return integer.value();
        }
        return null;
    }

    private static byte[] snapshot(ProtosBytesValue bytes) {
        List<Object> values = bytes.indexedSnapshot();
        byte[] snapshot = new byte[values.size()];
        for (int index = 0; index < values.size(); index++) {
            BigInteger octet = integer(values.get(index));
            if (octet == null
                    || octet.signum() < 0
                    || octet.compareTo(BigInteger.valueOf(255)) > 0) {
                throw new IllegalStateException("Bytes invariant violated");
            }
            snapshot[index] = (byte) octet.intValue();
        }
        return snapshot;
    }

    private static final class Request {
        private final Kind kind;
        private final ProtosIoOperation operation;
        private final BigInteger number;
        private final byte[] bytes;
        private boolean started;
        private boolean cancellationRequested;
        private Cancellation cancellation;
        private BigInteger startPosition;

        private Request(
                Kind kind,
                ProtosIoOperation operation,
                BigInteger number,
                byte[] bytes) {
            this.kind = kind;
            this.operation = operation;
            this.number = number;
            this.bytes = bytes;
        }
    }
}
