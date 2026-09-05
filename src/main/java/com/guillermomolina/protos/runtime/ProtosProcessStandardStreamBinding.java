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

import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Objects;

/**
 * One Process-local logical standard byte-stream binding.
 *
 * <p>The binding owns the cross-view/cross-Actor ordering domain. Actor-local capability wrappers
 * are cheap views over this object; rematerializing a view never opens, duplicates, resets, or
 * creates a second logical stream. stdout and stderr use distinct binding instances even when a
 * host routes both through the same backend object.
 */
public final class ProtosProcessStandardStreamBinding {
    @FunctionalInterface
    public interface ReadableBackend {
        ProtosByteIoFlow.Cancellation read(
                int maxBytes, ProtosByteIoFlow.ReadCompletion completion);
    }

    @FunctionalInterface
    public interface WritableBackend {
        ProtosByteIoFlow.Cancellation write(
                byte[] bytes, ProtosByteIoFlow.WriteCompletion completion);
    }

    public enum Direction {
        READABLE,
        WRITABLE
    }

    private static final int DEFAULT_MAX_RETAINED_WRITE_BYTES = 1024 * 1024;

    private final ProtosProcessRuntime process;
    private final Direction direction;
    private final ProtosObjectValue prototype;
    private final ProtosObjectValue bytesPrototype;
    private final ReadableBackend readableBackend;
    private final WritableBackend writableBackend;
    private final int maxRetainedWriteBytes;
    private final ArrayDeque<Request> operations = new ArrayDeque<>();
    private final ArrayDeque<Byte> unread = new ArrayDeque<>();
    private int retainedWriteBytes;

    private ProtosProcessStandardStreamBinding(
            ProtosProcessRuntime process,
            Direction direction,
            ProtosObjectValue prototype,
            ProtosObjectValue bytesPrototype,
            ReadableBackend readableBackend,
            WritableBackend writableBackend,
            int maxRetainedWriteBytes) {
        this.process = Objects.requireNonNull(process, "process");
        this.direction = Objects.requireNonNull(direction, "direction");
        this.prototype = Objects.requireNonNull(prototype, "prototype");
        this.bytesPrototype = Objects.requireNonNull(bytesPrototype, "bytesPrototype");
        this.readableBackend = readableBackend;
        this.writableBackend = writableBackend;
        if (maxRetainedWriteBytes < 0) {
            throw new IllegalArgumentException("negative retained-write bound");
        }
        this.maxRetainedWriteBytes = maxRetainedWriteBytes;

        if (direction == Direction.READABLE) {
            Objects.requireNonNull(readableBackend, "readableBackend");
            if (writableBackend != null) {
                throw new IllegalArgumentException("readable binding received writable backend");
            }
        } else {
            Objects.requireNonNull(writableBackend, "writableBackend");
            if (readableBackend != null) {
                throw new IllegalArgumentException("writable binding received readable backend");
            }
        }
    }

    static ProtosProcessStandardStreamBinding readableForRuntime(
            ProtosProcessRuntime process,
            ProtosObjectValue prototype,
            ProtosObjectValue bytesPrototype,
            ReadableBackend backend) {
        return new ProtosProcessStandardStreamBinding(
                process,
                Direction.READABLE,
                prototype,
                bytesPrototype,
                Objects.requireNonNull(backend, "backend"),
                null,
                DEFAULT_MAX_RETAINED_WRITE_BYTES);
    }

    static ProtosProcessStandardStreamBinding writableForRuntime(
            ProtosProcessRuntime process,
            ProtosObjectValue prototype,
            ProtosObjectValue bytesPrototype,
            WritableBackend backend) {
        return new ProtosProcessStandardStreamBinding(
                process,
                Direction.WRITABLE,
                prototype,
                bytesPrototype,
                null,
                Objects.requireNonNull(backend, "backend"),
                DEFAULT_MAX_RETAINED_WRITE_BYTES);
    }

    public Direction directionForRuntime() {
        return direction;
    }

    ProtosProcessStandardStreamValue newViewForRuntime() {
        return new ProtosProcessStandardStreamValue(prototype, this);
    }

    ProtosFutureValue readForRuntime(
            ProtosProcessStandardStreamValue receiver,
            ProtosActivation activation,
            Object maxBytesValue) {
        Objects.requireNonNull(receiver, "receiver");
        Objects.requireNonNull(activation, "activation");
        if (direction != Direction.READABLE) {
            return failedFuture(
                    activation, ProtosCoreErrors.StandardError.INVALID_I_O_ARGUMENT);
        }

        BigInteger maxBytes = integer(maxBytesValue);
        if (maxBytes == null
                || maxBytes.signum() <= 0
                || maxBytes.compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) > 0) {
            return failedFuture(
                    activation, ProtosCoreErrors.StandardError.INVALID_I_O_ARGUMENT);
        }

        ProtosIoOperation operation = beginOperation(receiver, activation);
        if (!operation.future().isPending()) {
            return operation.future();
        }

        Request request = new Request(Kind.READ, receiver, operation, maxBytes, null);
        enqueue(request);
        return operation.future();
    }

    ProtosFutureValue writeForRuntime(
            ProtosProcessStandardStreamValue receiver,
            ProtosActivation activation,
            Object value) {
        Objects.requireNonNull(receiver, "receiver");
        Objects.requireNonNull(activation, "activation");
        if (direction != Direction.WRITABLE) {
            return failedFuture(
                    activation, ProtosCoreErrors.StandardError.INVALID_I_O_ARGUMENT);
        }
        if (!(value instanceof ProtosBytesValue bytes)) {
            return failedFuture(
                    activation, ProtosCoreErrors.StandardError.INVALID_I_O_ARGUMENT);
        }

        byte[] snapshot = snapshot(bytes);
        synchronized (this) {
            if (snapshot.length > maxRetainedWriteBytes - retainedWriteBytes) {
                return failedFuture(
                        activation, ProtosCoreErrors.StandardError.I_O_CAPACITY_EXHAUSTED);
            }
            retainedWriteBytes += snapshot.length;
        }

        ProtosIoOperation operation = beginOperation(receiver, activation);
        if (!operation.future().isPending()) {
            releaseWriteBytes(snapshot.length);
            return operation.future();
        }

        Request request = new Request(Kind.WRITE, receiver, operation, null, snapshot);
        enqueue(request);
        return operation.future();
    }

    private ProtosIoOperation beginOperation(
            ProtosProcessStandardStreamValue receiver,
            ProtosActivation activation) {
        if (process.lifecycleState() != ProtosProcessRuntime.LifecycleState.RUNNING) {
            return rejectedOperation(receiver, activation);
        }

        ProtosIoLifecycle lifecycle =
                new ProtosIoLifecycle(
                        receiver.asObjectForLifecycle(),
                        activation.prelude().orElseThrow().futurePrototype(),
                        activation.executionDomain(),
                        completion -> completion.succeeded());
        ProtosIoOperation operation = lifecycle.beginOperation(activation);

        if (process.lifecycleState() != ProtosProcessRuntime.LifecycleState.RUNNING) {
            operation.requestCancellation();
        }
        return operation;
    }

    private ProtosIoOperation rejectedOperation(
            ProtosProcessStandardStreamValue receiver,
            ProtosActivation activation) {
        ProtosIoLifecycle lifecycle =
                new ProtosIoLifecycle(
                        receiver.asObjectForLifecycle(),
                        activation.prelude().orElseThrow().futurePrototype(),
                        activation.executionDomain(),
                        completion -> completion.succeeded());
        ProtosIoOperation operation = lifecycle.beginOperation(activation);
        operation.fail(
                ProtosCoreErrors.newOccurrence(
                        activation,
                        ProtosCoreErrors.StandardError.I_O_LIFECYCLE_ERROR));
        return operation;
    }

    private void enqueue(Request request) {
        if (!request.operation.future().isPending()) {
            if (request.kind == Kind.WRITE) {
                releaseWriteBytes(request.bytes.length);
            }
            return;
        }
        synchronized (this) {
            operations.addLast(request);
        }
        request.operation.onCancellation(() -> cancel(request));
        pump();
    }

    private void pump() {
        Request request;
        synchronized (this) {
            request = operations.peekFirst();
            if (request == null || request.started) {
                return;
            }
            request.started = true;
        }
        if (request.kind == Kind.READ) {
            startRead(request);
        } else {
            startWrite(request);
        }
    }

    private void startRead(Request request) {
        byte[] buffered = null;
        synchronized (this) {
            if (!unread.isEmpty()) {
                int count = Math.min(request.number.intValueExact(), unread.size());
                buffered = new byte[count];
                for (int index = 0; index < count; index++) {
                    buffered[index] = unread.removeFirst();
                }
            }
        }
        if (buffered != null) {
            completeReadData(request, buffered);
            return;
        }

        try {
            setCancellation(
                    request,
                    readableBackend.read(
                            request.number.intValueExact(),
                            new ProtosByteIoFlow.ReadCompletion() {
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
            synchronized (this) {
                for (int index = bytes.length - 1; index >= 0; index--) {
                    unread.addFirst(bytes[index]);
                }
            }
            finish(request);
            return;
        }

        ProtosBytesValue result = new ProtosBytesValue(bytesPrototype);
        for (byte value : bytes) {
            result.indexedAdd(
                    new ProtosIntegerValue(BigInteger.valueOf(value & 0xff)));
        }
        request.operation.resolve(result);
        finish(request);
    }

    private void startWrite(Request request) {
        if (request.bytes.length == 0) {
            if (request.operation.commit()) {
                request.operation.resolve(request.receiver);
            }
            finish(request);
            return;
        }

        try {
            setCancellation(
                    request,
                    writableBackend.write(
                            request.bytes.clone(),
                            new ProtosByteIoFlow.WriteCompletion() {
                                @Override
                                public void succeeded() {
                                    if (request.operation.commit()) {
                                        request.operation.resolve(request.receiver);
                                    }
                                    finish(request);
                                }

                                @Override
                                public void failed(int contributedPrefix) {
                                    if (contributedPrefix < 0
                                            || contributedPrefix > request.bytes.length) {
                                        failIo(request);
                                        finish(request);
                                        return;
                                    }
                                    if (contributedPrefix > 0) {
                                        request.operation.commit();
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

    private void setCancellation(
            Request request, ProtosByteIoFlow.Cancellation cancellation) {
        synchronized (this) {
            request.cancellation = cancellation;
        }
        if (request.operation.terminal() && cancellation != null) {
            cancellation.cancel();
        }
    }

    private void cancel(Request request) {
        ProtosByteIoFlow.Cancellation cancellation;
        boolean removed = false;
        synchronized (this) {
            cancellation = request.cancellation;
            if (!request.started) {
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

    private void finish(Request request) {
        synchronized (this) {
            if (operations.remove(request) && request.kind == Kind.WRITE) {
                retainedWriteBytes -= request.bytes.length;
            }
        }
        pump();
    }

    private void failIo(Request request) {
        request.operation.fail(
                ProtosCoreErrors.newOccurrence(
                        request.operation.origin(),
                        ProtosCoreErrors.StandardError.I_O_ERROR));
    }

    private void releaseWriteBytes(int bytes) {
        synchronized (this) {
            retainedWriteBytes -= bytes;
        }
    }

    private ProtosFutureValue failedFuture(
            ProtosActivation activation,
            ProtosCoreErrors.StandardError error) {
        ProtosFutureValue future =
                new ProtosFutureValue(
                        activation.prelude().orElseThrow().futurePrototype(),
                        activation.executionDomain());
        future.fail(ProtosCoreErrors.newOccurrence(activation, error));
        return future;
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
        byte[] result = new byte[values.size()];
        for (int index = 0; index < values.size(); index++) {
            BigInteger value = integer(values.get(index));
            if (value == null
                    || value.signum() < 0
                    || value.compareTo(BigInteger.valueOf(255)) > 0) {
                throw new IllegalStateException("Bytes invariant violated");
            }
            result[index] = (byte) value.intValue();
        }
        return result;
    }

    private enum Kind {
        READ,
        WRITE
    }

    private static final class Request {
        private final Kind kind;
        private final ProtosProcessStandardStreamValue receiver;
        private final ProtosIoOperation operation;
        private final BigInteger number;
        private final byte[] bytes;
        private boolean started;
        private ProtosByteIoFlow.Cancellation cancellation;

        private Request(
                Kind kind,
                ProtosProcessStandardStreamValue receiver,
                ProtosIoOperation operation,
                BigInteger number,
                byte[] bytes) {
            this.kind = kind;
            this.receiver = receiver;
            this.operation = operation;
            this.number = number;
            this.bytes = bytes;
        }
    }
}
