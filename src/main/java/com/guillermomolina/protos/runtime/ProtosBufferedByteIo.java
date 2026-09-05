/* APL-1.0 licensed work; see LICENSE.TXT. */
package com.guillermomolina.protos.runtime;

import com.guillermomolina.protos.execution.ProtosInvocation;
import java.math.BigInteger;
import java.util.*;

/** Ordered bounded state machine for the standard Core byte buffering wrappers. */
public final class ProtosBufferedByteIo {
    private static final int READ_AHEAD = 8192;
    private static final int MAX_OUTPUT = 1024 * 1024;

    private enum Mode { READER, WRITER }
    private enum Kind { READ, WRITE, FLUSH }

    private final Mode mode;
    private final ProtosObjectValue receiver;
    private final ProtosObjectValue target;
    private final ProtosObjectValue bytesPrototype;
    private final ProtosActorExecutionDomain domain;
    private final boolean owning;
    private final ArrayDeque<Byte> input = new ArrayDeque<>();
    private final ArrayDeque<Req> q = new ArrayDeque<>();
    private final ArrayList<ProtosFutureValue> closeFollowers = new ArrayList<>();

    private byte[] output = new byte[0];
    private Req activeReq;
    private boolean closing;
    private boolean closed;
    private ProtosActivation closeActivation;
    private ProtosObjectValue closeError;
    private ProtosObjectValue outputError;

    private ProtosBufferedByteIo(
            Mode mode,
            ProtosObjectValue receiver,
            ProtosObjectValue target,
            ProtosObjectValue bytesPrototype,
            ProtosActivation activation,
            boolean owning) {
        this.mode = mode;
        this.receiver = receiver;
        this.target = target;
        this.bytesPrototype = bytesPrototype;
        this.domain = activation.executionDomain();
        this.owning = owning;
    }

    public static ProtosBufferedByteIo reader(
            ProtosObjectValue receiver,
            ProtosObjectValue target,
            ProtosObjectValue bytesPrototype,
            ProtosActivation activation,
            boolean owning) {
        return new ProtosBufferedByteIo(
                Mode.READER, receiver, target, bytesPrototype, activation, owning);
    }

    public static ProtosBufferedByteIo writer(
            ProtosObjectValue receiver,
            ProtosObjectValue target,
            ProtosObjectValue bytesPrototype,
            ProtosActivation activation,
            boolean owning) {
        return new ProtosBufferedByteIo(
                Mode.WRITER, receiver, target, bytesPrototype, activation, owning);
    }

    public ProtosFutureValue read(ProtosActivation activation, Object maximum) {
        check(activation);
        BigInteger n = integer(maximum);
        if (n == null
                || n.signum() <= 0
                || n.compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) > 0) {
            return failed(
                    activation, ProtosCoreErrors.StandardError.INVALID_I_O_ARGUMENT);
        }
        synchronized (this) {
            if (closing || closed) return lifecycle(activation);
        }
        return enqueue(
                new Req(activation, newFuture(activation), Kind.READ, n.intValue(), null));
    }

    public ProtosFutureValue write(ProtosActivation activation, Object value) {
        check(activation);
        if (!(value instanceof ProtosBytesValue bytes)) {
            return failed(
                    activation, ProtosCoreErrors.StandardError.INVALID_I_O_ARGUMENT);
        }
        byte[] snapshot = snapshot(bytes);
        synchronized (this) {
            if (closing || closed) return lifecycle(activation);
            if (outputError != null) return failedSame(activation, outputError);
            if (snapshot.length > MAX_OUTPUT - output.length) {
                return failed(
                        activation,
                        ProtosCoreErrors.StandardError.I_O_CAPACITY_EXHAUSTED);
            }
        }
        return enqueue(
                new Req(activation, newFuture(activation), Kind.WRITE, 0, snapshot));
    }

    public ProtosFutureValue flush(ProtosActivation activation) {
        check(activation);
        synchronized (this) {
            if (closing || closed) return lifecycle(activation);
            if (outputError != null) return failedSame(activation, outputError);
        }
        return enqueue(
                new Req(activation, newFuture(activation), Kind.FLUSH, 0, null));
    }

    public ProtosFutureValue close(ProtosActivation activation) {
        check(activation);
        ProtosFutureValue follower = newFuture(activation);
        // close cutover is irreversible; cancellation of the observing activation
        // cannot turn the lifecycle Future into cancelled.
        follower.attachCancellationProducer(() -> {});

        Req active;
        List<Req> queuedToTerminate = new ArrayList<>();
        synchronized (this) {
            if (closed) {
                if (closeError == null) follower.resolve(receiver, activation);
                else follower.fail(closeError);
                return follower;
            }

            closeFollowers.add(follower);
            if (closing) return follower;

            closing = true;
            closeActivation = activation;
            active = activeReq;

            Iterator<Req> iterator = q.iterator();
            while (iterator.hasNext()) {
                Req req = iterator.next();
                if (req != active && !req.committed) {
                    req.closeCutover = true;
                    queuedToTerminate.add(req);
                    iterator.remove();
                }
            }
            if (active != null && !active.committed) {
                active.closeCutover = true;
            }
        }

        for (Req req : queuedToTerminate) {
            req.f.fail(lifecycleError(req.a));
        }

        if (active != null && active.closeCutover) {
            active.f.fail(lifecycleError(active.a));
            ProtosFutureValue lower;
            synchronized (this) {
                lower = active.lower;
            }
            if (lower != null) lower.cancelRequest();
        } else if (active == null) {
            finalizeClose(activation);
        }

        return follower;
    }

    private static final class Req {
        final ProtosActivation a;
        final ProtosFutureValue f;
        final Kind kind;
        final int maximum;
        final byte[] bytes;
        boolean committed;
        boolean cancelRequested;
        boolean closeCutover;
        ProtosFutureValue lower;

        Req(
                ProtosActivation activation,
                ProtosFutureValue future,
                Kind kind,
                int maximum,
                byte[] bytes) {
            this.a = activation;
            this.f = future;
            this.kind = kind;
            this.maximum = maximum;
            this.bytes = bytes;
        }
    }

    private ProtosFutureValue enqueue(Req req) {
        req.f.attachCancellationProducer(() -> cancel(req));
        synchronized (this) {
            q.addLast(req);
        }
        pump();
        return req.f;
    }

    private void cancel(Req req) {
        ProtosFutureValue lower = null;
        boolean queuedCancellation = false;
        synchronized (this) {
            if (req.committed || !req.f.isPending()) return;
            if (activeReq != req) {
                if (q.remove(req)) queuedCancellation = true;
                else return;
            } else {
                req.cancelRequested = true;
                lower = req.lower;
            }
        }
        if (queuedCancellation) {
            req.f.cancelTerminal();
            pump();
        } else if (lower != null) {
            lower.cancelRequest();
        }
    }

    private void pump() {
        Req req = null;
        ProtosActivation closeNow = null;
        synchronized (this) {
            if (activeReq != null) return;
            if (q.isEmpty()) {
                if (closing && !closed) closeNow = closeActivation;
            } else {
                req = q.peekFirst();
                activeReq = req;
            }
        }

        if (closeNow != null) {
            finalizeClose(closeNow);
            return;
        }
        if (req == null) return;

        switch (req.kind) {
            case READ -> doRead(req);
            case WRITE -> doWrite(req);
            case FLUSH -> doFlush(req, false);
        }
    }

    private void done(Req req) {
        ProtosActivation closeNow = null;
        synchronized (this) {
            q.remove(req);
            if (activeReq == req) activeReq = null;
            if (closing && q.isEmpty() && !closed) closeNow = closeActivation;
        }
        if (closeNow != null) finalizeClose(closeNow);
        else pump();
    }

    private boolean stopBeforeLowerWork(Req req) {
        boolean cancelled;
        boolean cutover;
        synchronized (this) {
            cancelled = req.cancelRequested;
            cutover = req.closeCutover;
        }
        if (cutover) {
            done(req);
            return true;
        }
        if (cancelled) {
            req.f.cancelTerminal();
            done(req);
            return true;
        }
        return false;
    }

    private void installLower(Req req, ProtosFutureValue lower) {
        boolean requestCancellation;
        synchronized (this) {
            req.lower = lower;
            requestCancellation = req.cancelRequested || req.closeCutover;
        }
        if (requestCancellation) lower.cancelRequest();
    }

    private void clearLower(Req req, ProtosFutureValue lower) {
        synchronized (this) {
            if (req.lower == lower) req.lower = null;
        }
    }

    private void doRead(Req req) {
        if (stopBeforeLowerWork(req)) return;

        byte[] buffered;
        synchronized (this) {
            if (req.closeCutover) {
                done(req);
                return;
            }
            buffered = takeInput(req.maximum);
        }
        if (buffered.length > 0) {
            req.committed = true;
            req.f.resolve(bytes(buffered), req.a);
            done(req);
            return;
        }

        ProtosFutureValue lower =
                invokeFuture(
                        target,
                        "read",
                        List.of(
                                new ProtosIntegerValue(
                                        BigInteger.valueOf(
                                                Math.max(req.maximum, READ_AHEAD)))),
                        req.a,
                        req.f);
        if (lower == null) {
            done(req);
            return;
        }

        installLower(req, lower);
        lower.observe(
                terminal -> {
                    clearLower(req, lower);
                    boolean cutover;
                    synchronized (this) {
                        cutover = req.closeCutover;
                    }
                    if (cutover) {
                        // Reader close may discard any uncommitted read-ahead.
                        done(req);
                        return;
                    }

                    switch (terminal.state()) {
                        case RESOLVED -> {
                            Object value = terminal.resolvedValue().orElseThrow();
                            if (value == ProtosNullValue.INSTANCE) {
                                req.committed = true;
                                req.f.resolve(value, req.a);
                                done(req);
                            } else if (value instanceof ProtosBytesValue bytes) {
                                byte[] obtained = snapshot(bytes);
                                if (obtained.length == 0) {
                                    req.f.fail(ioError(req.a));
                                    done(req);
                                    return;
                                }
                                byte[] first;
                                synchronized (this) {
                                    for (byte b : obtained) input.addLast(b);
                                    first = takeInput(req.maximum);
                                }
                                req.committed = true;
                                req.f.resolve(this.bytes(first), req.a);
                                done(req);
                            } else {
                                req.f.fail(ioError(req.a));
                                done(req);
                            }
                        }
                        case FAILED -> {
                            req.f.fail(terminal.failedError().orElseThrow());
                            done(req);
                        }
                        case CANCELLED -> {
                            req.f.cancelTerminal();
                            done(req);
                        }
                        case PENDING -> { }
                    }
                });
    }

    private void doWrite(Req req) {
        synchronized (this) {
            if (req.closeCutover) {
                // close won before this accepted write committed to the adapter.
            } else if (req.cancelRequested) {
                // cancellation won before adapter admission.
            } else {
                byte[] next = Arrays.copyOf(output, output.length + req.bytes.length);
                System.arraycopy(req.bytes, 0, next, output.length, req.bytes.length);
                output = next;
                req.committed = true;
            }
        }

        if (req.committed) req.f.resolve(receiver, req.a);
        else if (req.closeCutover) req.f.fail(lifecycleError(req.a));
        else req.f.cancelTerminal();
        done(req);
    }

    private void doFlush(Req req, boolean closePath) {
        if (!closePath && stopBeforeLowerWork(req)) return;

        byte[] pending;
        synchronized (this) {
            pending = output.clone();
        }
        if (pending.length == 0) {
            flushTarget(req, closePath);
            return;
        }

        ProtosFutureValue lower =
                invokeFuture(
                        target,
                        "write",
                        List.of(bytes(pending)),
                        req.a,
                        closePath ? null : req.f);
        if (lower == null) {
            poison(req, closePath);
            return;
        }

        installLower(req, lower);
        lower.observe(
                terminal -> {
                    clearLower(req, lower);
                    if (terminal.state() == ProtosFutureValue.State.RESOLVED) {
                        synchronized (this) {
                            if (output.length >= pending.length) {
                                output =
                                        Arrays.copyOfRange(
                                                output, pending.length, output.length);
                            }
                        }
                        req.committed = true;
                        flushTarget(req, closePath);
                    } else if (terminal.state()
                            == ProtosFutureValue.State.CANCELLED) {
                        if (closePath) {
                            poison(req, true);
                        } else {
                            req.f.cancelTerminal();
                            done(req);
                        }
                    } else if (terminal.state()
                            != ProtosFutureValue.State.PENDING) {
                        poisonFrom(req, terminal, closePath);
                    }
                });
    }

    private void flushTarget(Req req, boolean closePath) {
        if (target.lookupSlot("flush").isEmpty()) {
            req.committed = true;
            if (closePath) finishFinalization(req.a, null);
            else {
                if (!req.closeCutover) req.f.resolve(receiver, req.a);
                done(req);
            }
            return;
        }

        ProtosFutureValue lower =
                invokeFuture(
                        target,
                        "flush",
                        List.of(),
                        req.a,
                        closePath ? null : req.f);
        if (lower == null) {
            poison(req, closePath);
            return;
        }

        installLower(req, lower);
        lower.observe(
                terminal -> {
                    clearLower(req, lower);
                    if (terminal.state() == ProtosFutureValue.State.RESOLVED) {
                        req.committed = true;
                        if (closePath) finishFinalization(req.a, null);
                        else {
                            if (!req.closeCutover) req.f.resolve(receiver, req.a);
                            done(req);
                        }
                    } else if (terminal.state()
                            == ProtosFutureValue.State.CANCELLED) {
                        if (closePath) poison(req, true);
                        else {
                            req.f.cancelTerminal();
                            done(req);
                        }
                    } else if (terminal.state()
                            != ProtosFutureValue.State.PENDING) {
                        poisonFrom(req, terminal, closePath);
                    }
                });
    }

    private void poisonFrom(
            Req req, ProtosFutureValue terminal, boolean closePath) {
        ProtosObjectValue error =
                terminal.state() == ProtosFutureValue.State.FAILED
                        ? terminal.failedError().orElseGet(() -> ioError(req.a))
                        : ioError(req.a);
        synchronized (this) {
            if (outputError == null) outputError = error;
        }
        if (!req.closeCutover) req.f.fail(error);
        if (closePath) finishFinalization(req.a, error);
        else done(req);
    }

    private void poison(Req req, boolean closePath) {
        ProtosObjectValue error = ioError(req.a);
        synchronized (this) {
            if (outputError == null) outputError = error;
        }
        if (!req.closeCutover) req.f.fail(error);
        if (closePath) finishFinalization(req.a, error);
        else done(req);
    }

    private void finalizeClose(ProtosActivation activation) {
        if (activation == null) return;
        synchronized (this) {
            if (closed) return;
        }

        if (mode == Mode.WRITER) {
            ProtosObjectValue primary;
            boolean hasOutput;
            synchronized (this) {
                primary = outputError;
                hasOutput = output.length > 0;
            }
            if (primary == null && hasOutput) {
                Req finalFlush =
                        new Req(
                                activation,
                                newFuture(activation),
                                Kind.FLUSH,
                                0,
                                null);
                doFlush(finalFlush, true);
                return;
            }
            finishFinalization(activation, primary);
            return;
        }

        finishFinalization(activation, null);
    }

    private void finishFinalization(
            ProtosActivation activation, ProtosObjectValue primary) {
        if (!owning) {
            finishClose(activation, primary);
            return;
        }

        ProtosFutureValue targetClose =
                invokeFuture(target, "close", List.of(), activation, null);
        if (targetClose == null) {
            finishClose(
                    activation, primary != null ? primary : ioError(activation));
            return;
        }

        targetClose.observe(
                terminal -> {
                    if (terminal.state() == ProtosFutureValue.State.RESOLVED) {
                        finishClose(activation, primary);
                    } else if (terminal.state()
                            != ProtosFutureValue.State.PENDING) {
                        ProtosObjectValue targetFailure =
                                terminal.state() == ProtosFutureValue.State.FAILED
                                        ? terminal.failedError()
                                                .orElseGet(() -> ioError(activation))
                                        : ioError(activation);
                        finishClose(
                                activation,
                                primary != null ? primary : targetFailure);
                    }
                });
    }

    private void finishClose(
            ProtosActivation activation, ProtosObjectValue error) {
        List<ProtosFutureValue> followers;
        synchronized (this) {
            if (closed) return;
            closed = true;
            closeError = error;
            followers = List.copyOf(closeFollowers);
            closeFollowers.clear();
        }

        for (ProtosFutureValue follower : followers) {
            if (error == null) follower.resolve(receiver, activation);
            else follower.fail(error);
        }
    }

    private ProtosFutureValue invokeFuture(
            ProtosObjectValue object,
            String message,
            List<?> arguments,
            ProtosActivation activation,
            ProtosFutureValue outer) {
        try {
            Object value =
                    ProtosInvocation.invokeMessage(
                            object, message, arguments, activation);
            if (value instanceof ProtosFutureValue future) return future;
            if (outer != null) outer.fail(ioError(activation));
            return null;
        } catch (RuntimeException exception) {
            if (outer != null) outer.fail(ioError(activation));
            return null;
        }
    }

    private ProtosFutureValue newFuture(ProtosActivation activation) {
        return new ProtosFutureValue(
                activation.prelude().orElseThrow().futurePrototype(), domain);
    }

    private ProtosFutureValue failed(
            ProtosActivation activation, ProtosCoreErrors.StandardError error) {
        ProtosFutureValue future = newFuture(activation);
        future.fail(ProtosCoreErrors.newOccurrence(activation, error));
        return future;
    }

    private ProtosFutureValue failedSame(
            ProtosActivation activation, ProtosObjectValue error) {
        ProtosFutureValue future = newFuture(activation);
        future.fail(error);
        return future;
    }

    private ProtosFutureValue lifecycle(ProtosActivation activation) {
        return failed(
                activation, ProtosCoreErrors.StandardError.I_O_LIFECYCLE_ERROR);
    }

    private ProtosObjectValue lifecycleError(ProtosActivation activation) {
        return ProtosCoreErrors.newOccurrence(
                activation, ProtosCoreErrors.StandardError.I_O_LIFECYCLE_ERROR);
    }

    private ProtosObjectValue ioError(ProtosActivation activation) {
        return ProtosCoreErrors.newOccurrence(
                activation, ProtosCoreErrors.StandardError.I_O_ERROR);
    }

    private void check(ProtosActivation activation) {
        Objects.requireNonNull(activation);
        if (activation.executionDomain() != domain) {
            throw new IllegalArgumentException(
                    "buffered I/O belongs to another Actor domain");
        }
    }

    private byte[] takeInput(int maximum) {
        int count = Math.min(maximum, input.size());
        byte[] result = new byte[count];
        for (int i = 0; i < count; i++) result[i] = input.removeFirst();
        return result;
    }

    private ProtosBytesValue bytes(byte[] values) {
        ProtosBytesValue bytes = new ProtosBytesValue(bytesPrototype);
        for (byte value : values) {
            bytes.indexedAdd(
                    new ProtosIntegerValue(BigInteger.valueOf(value & 255)));
        }
        return bytes;
    }

    private static BigInteger integer(Object value) {
        return value instanceof ProtosIntegerValue integer
                ? integer.value()
                : null;
    }

    private static byte[] snapshot(ProtosBytesValue bytes) {
        int size = bytes.indexedSize().intValueExact();
        byte[] result = new byte[size];
        for (int index = 0; index < result.length; index++) {
            Object value = bytes.indexedAt(BigInteger.valueOf(index));
            if (!(value instanceof ProtosIntegerValue integer)) {
                throw new IllegalStateException("invalid Bytes");
            }
            int octet = integer.value().intValueExact();
            if (octet < 0 || octet > 255) {
                throw new IllegalStateException("invalid Bytes octet");
            }
            result[index] = (byte) octet;
        }
        return result;
    }
}