/* APL-1.0 licensed work; see LICENSE.TXT. */
package com.guillermomolina.protos.runtime;

import com.guillermomolina.protos.execution.ProtosBufferedByteReaderCPrimeExecution;
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
    private final ProtosIoLifecycle lifecycle;
    private final ArrayDeque<Byte> input = new ArrayDeque<>();
    private final ArrayDeque<Req> q = new ArrayDeque<>();

    private byte[] output = new byte[0];
    private Req activeReq;
    private ProtosActivation closeActivation;
    private ProtosObjectValue outputError;
    private ProtosIoLifecycle.ReleaseCompletion releaseCompletion;

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
        this.lifecycle =
                new ProtosIoLifecycle(
                        receiver,
                        activation.prelude().orElseThrow().futurePrototype(),
                        domain,
                        this::release);
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
        return read(activation, maximum, null);
    }

    public ProtosFutureValue readForCPrimeRuntime(
            ProtosActivation activation,
            Object maximum,
            ProtosBufferedByteReaderCPrimeExecution.Plan plan) {
        return read(
                activation,
                maximum,
                Objects.requireNonNull(plan, "plan"));
    }

    private ProtosFutureValue read(
            ProtosActivation activation,
            Object maximum,
            ProtosBufferedByteReaderCPrimeExecution.Plan cPrimePlan) {
        check(activation);
        BigInteger n = integer(maximum);
        if (n == null
                || n.signum() <= 0
                || n.compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) > 0) {
            return failed(
                    activation, ProtosCoreErrors.StandardError.INVALID_I_O_ARGUMENT);
        }

        ProtosIoOperation operation = lifecycle.beginOperation(activation);
        if (operation.terminal()) return operation.future();

        return enqueue(
                new Req(
                        activation,
                        operation,
                        Kind.READ,
                        n.intValue(),
                        null,
                        cPrimePlan));
    }

    public ProtosFutureValue write(ProtosActivation activation, Object value) {
        check(activation);
        if (!(value instanceof ProtosBytesValue bytes)) {
            return failed(
                    activation, ProtosCoreErrors.StandardError.INVALID_I_O_ARGUMENT);
        }
        byte[] snapshot = snapshot(bytes);

        ProtosIoOperation operation = lifecycle.beginOperation(activation);
        if (operation.terminal()) return operation.future();

        ProtosObjectValue rejection = null;
        synchronized (this) {
            if (outputError != null) {
                rejection = outputError;
            } else if (snapshot.length > MAX_OUTPUT - output.length) {
                rejection =
                        ProtosCoreErrors.newOccurrence(
                                activation,
                                ProtosCoreErrors.StandardError.I_O_CAPACITY_EXHAUSTED);
            }
        }
        if (rejection != null) {
            operation.fail(rejection);
            return operation.future();
        }

        return enqueue(
                new Req(
                        activation,
                        operation,
                        Kind.WRITE,
                        0,
                        snapshot));
    }

    public ProtosFutureValue flush(ProtosActivation activation) {
        check(activation);

        ProtosIoOperation operation = lifecycle.beginOperation(activation);
        if (operation.terminal()) return operation.future();

        ProtosObjectValue failure;
        synchronized (this) {
            failure = outputError;
        }
        if (failure != null) {
            operation.fail(failure);
            return operation.future();
        }

        return enqueue(
                new Req(
                        activation,
                        operation,
                        Kind.FLUSH,
                        0,
                        null));
    }

    public ProtosFutureValue close(ProtosActivation activation) {
        check(activation);
        synchronized (this) {
            if (closeActivation == null) closeActivation = activation;
        }
        return lifecycle.close(activation);
    }

    private static final class Req {
        final ProtosActivation a;
        final ProtosIoOperation operation;
        final Kind kind;
        final int maximum;
        final byte[] bytes;
        final ProtosBufferedByteReaderCPrimeExecution.Plan readCPrimePlan;
        ProtosFutureValue lower;
        ProtosFutureValue.Observer lowerObserver;

        Req(
                ProtosActivation activation,
                ProtosIoOperation operation,
                Kind kind,
                int maximum,
                byte[] bytes) {
            this(activation, operation, kind, maximum, bytes, null);
        }

        Req(
                ProtosActivation activation,
                ProtosIoOperation operation,
                Kind kind,
                int maximum,
                byte[] bytes,
                ProtosBufferedByteReaderCPrimeExecution.Plan readCPrimePlan) {
            this.a = activation;
            this.operation = operation;
            this.kind = kind;
            this.maximum = maximum;
            this.bytes = bytes;
            this.readCPrimePlan = readCPrimePlan;
        }
    }

    private ProtosFutureValue enqueue(Req req) {
        req.operation.onCancellation(() -> cancel(req));
        synchronized (this) {
            q.addLast(req);
        }
        pump();
        return req.operation.future();
    }

    private void cancel(Req req) {
        ProtosFutureValue lower = null;
        boolean removed = false;
        synchronized (this) {
            if (activeReq != req) {
                removed = q.remove(req);
            } else {
                lower = req.lower;
            }
        }
        if (lower != null) lower.cancelRequest();
        if (removed) pump();
    }

    private void pump() {
        Req req = null;
        synchronized (this) {
            if (activeReq != null) return;
            if (lifecycle.state() != ProtosIoLifecycle.State.OPEN) return;
            while (!q.isEmpty() && q.peekFirst().operation.terminal()) {
                q.removeFirst();
            }
            if (!q.isEmpty()) {
                req = q.peekFirst();
                activeReq = req;
            }
        }

        if (req == null) return;

        switch (req.kind) {
            case READ -> {
                if (req.readCPrimePlan == null) doRead(req);
                else doReadCPrime(req);
            }
            case WRITE -> doWrite(req);
            case FLUSH -> doFlush(req, false);
        }
    }

    private void done(Req req) {
        boolean continuePumping;
        synchronized (this) {
            q.remove(req);
            if (activeReq == req) activeReq = null;
            continuePumping = lifecycle.state() == ProtosIoLifecycle.State.OPEN;
        }
        if (continuePumping) pump();
    }

    private boolean stopBeforeLowerWork(Req req) {
        if (!req.operation.terminal()) return false;
        done(req);
        return true;
    }

    private void installLower(Req req, ProtosFutureValue lower) {
        boolean requestCancellation = false;
        synchronized (this) {
            req.lower = lower;
        }
        if (req.operation != null) {
            requestCancellation = req.operation.backendCancellationRequested();
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
        if (completeBufferedReadIfAvailable(req)) return;

        ProtosFutureValue lower =
                invokeFuture(
                        target,
                        "read",
                        readArguments(req),
                        req.a);
        if (lower == null) {
            if (!req.operation.terminal()) req.operation.fail(ioError(req.a));
            done(req);
            return;
        }

        installLower(req, lower);
        lower.observe(
                terminal -> {
                    clearLower(req, lower);
                    settleReadLower(req, terminal);
                });
    }

    private void doReadCPrime(Req req) {
        if (stopBeforeLowerWork(req)) return;
        if (completeBufferedReadIfAvailable(req)) return;

        ProtosBufferedByteReaderCPrimeExecution.schedule(
                req.readCPrimePlan,
                req.operation,
                this,
                target,
                readArguments(req));
    }

    private List<?> readArguments(Req req) {
        return List.of(
                new ProtosIntegerValue(
                        BigInteger.valueOf(
                                Math.max(req.maximum, READ_AHEAD))));
    }

    private boolean completeBufferedReadIfAvailable(Req req) {
        int bufferedCount;
        synchronized (this) {
            bufferedCount = Math.min(req.maximum, input.size());
        }
        if (bufferedCount == 0) return false;

        if (!req.operation.commit()) {
            done(req);
            return true;
        }
        byte[] buffered;
        synchronized (this) {
            buffered = takeInput(req.maximum);
        }
        req.operation.resolve(bytes(buffered));
        done(req);
        return true;
    }

    private void settleReadLower(Req req, ProtosFutureValue terminal) {
        if (req.operation.terminal()) {
            done(req);
            return;
        }

        switch (terminal.state()) {
            case RESOLVED -> settleResolvedRead(req, terminal.resolvedValue().orElseThrow());
            case FAILED -> {
                req.operation.fail(terminal.failedError().orElseThrow());
                done(req);
            }
            case CANCELLED -> {
                req.operation.requestCancellation();
                done(req);
            }
            case PENDING -> throw new IllegalStateException(
                    "BufferedReader attempted to settle a pending lower Future");
        }
    }

    private void settleResolvedRead(Req req, Object value) {
        if (value == ProtosNullValue.INSTANCE) {
            if (req.operation.commit()) {
                req.operation.resolve(value);
            }
            done(req);
            return;
        }

        if (!(value instanceof ProtosBytesValue bytes)) {
            req.operation.fail(ioError(req.a));
            done(req);
            return;
        }

        byte[] obtained = snapshot(bytes);
        if (obtained.length == 0) {
            req.operation.fail(ioError(req.a));
            done(req);
            return;
        }
        if (!req.operation.commit()) {
            done(req);
            return;
        }

        byte[] first;
        synchronized (this) {
            for (byte b : obtained) input.addLast(b);
            first = takeInput(req.maximum);
        }
        req.operation.resolve(this.bytes(first));
        done(req);
    }

    /**
     * Publishes the lower Future before the operation-owned C-prime wait is retained.
     * The passive observer exists only to clean up a late lower completion after the
     * outer read was already cancelled/closed; it never executes guest code.
     */
    public boolean observeReadLowerForCPrimeRuntime(
            ProtosIoOperation operation,
            ProtosFutureValue lower) {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(lower, "lower");

        Req req;
        ProtosFutureValue.Observer observer;
        boolean terminalWithoutRequest = false;
        synchronized (this) {
            req = activeReq;
            if (req == null
                    || req.operation != operation
                    || req.kind != Kind.READ
                    || req.readCPrimePlan == null
                    || q.peekFirst() != req) {
                if (!operation.terminal()) {
                    throw new IllegalStateException(
                            "BufferedReader C-prime lower Future has no active owning request");
                }
                terminalWithoutRequest = true;
                observer = null;
            } else {
                if (req.lower != null || req.lowerObserver != null) {
                    throw new IllegalStateException(
                            "BufferedReader C-prime request already owns a lower Future");
                }
                req.lower = lower;
                observer = ignored -> cPrimeReadLowerTerminalObserved(req, lower);
                req.lowerObserver = observer;
            }
        }

        if (terminalWithoutRequest) {
            lower.cancelRequest();
            return false;
        }

        lower.observe(observer);
        if (operation.terminal()) {
            lower.cancelRequest();
            if (!lower.isPending()) {
                consumeReadLowerForCPrimeRuntime(operation, lower);
            }
            return false;
        }
        return true;
    }

    /** Applies one already-terminal lower Future on the Actor-domain C-prime segment. */
    public void consumeReadLowerForCPrimeRuntime(
            ProtosIoOperation operation,
            ProtosFutureValue lower) {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(lower, "lower");
        if (lower.isPending()) {
            throw new IllegalStateException(
                    "BufferedReader C-prime attempted to consume a pending lower Future");
        }

        Req req;
        ProtosFutureValue.Observer observer;
        synchronized (this) {
            req = activeReq;
            if (req == null
                    || req.operation != operation
                    || req.kind != Kind.READ
                    || req.readCPrimePlan == null
                    || req.lower != lower) {
                return;
            }
            observer = req.lowerObserver;
            req.lowerObserver = null;
            req.lower = null;
        }

        if (observer != null) {
            lower.removeObserver(observer);
        }
        settleReadLower(req, lower);
    }

    public void invalidReadLowerForCPrimeRuntime(ProtosIoOperation operation) {
        failReadCPrime(operation);
    }

    public void readInvocationFailedForCPrimeRuntime(ProtosIoOperation operation) {
        failReadCPrime(operation);
    }

    public void readCPrimeDriverFailedForRuntime(
            ProtosIoOperation operation,
            ProtosObjectValue ignoredExactError) {
        failReadCPrime(operation);
    }

    public void unexpectedReadCPrimeCompletionForRuntime(
            ProtosIoOperation operation) {
        failReadCPrime(operation);
    }

    private void failReadCPrime(ProtosIoOperation operation) {
        Objects.requireNonNull(operation, "operation");

        Req req;
        ProtosFutureValue lower;
        ProtosFutureValue.Observer observer;
        synchronized (this) {
            req = activeReq;
            if (req == null
                    || req.operation != operation
                    || req.kind != Kind.READ
                    || req.readCPrimePlan == null
                    || q.peekFirst() != req) {
                return;
            }
            lower = req.lower;
            observer = req.lowerObserver;
            req.lower = null;
            req.lowerObserver = null;
        }

        if (observer != null && lower != null) {
            lower.removeObserver(observer);
        }
        if (lower != null && lower.isPending()) {
            lower.cancelRequest();
        }
        if (!operation.terminal()) {
            operation.fail(ioError(req.a));
        }
        done(req);
    }

    private void cPrimeReadLowerTerminalObserved(
            Req req,
            ProtosFutureValue lower) {
        boolean applyLate;
        synchronized (this) {
            if (req.lower != lower || req.lowerObserver == null) {
                return;
            }
            applyLate = req.operation.terminal();
        }
        if (applyLate) {
            consumeReadLowerForCPrimeRuntime(req.operation, lower);
        }
    }

    private void doWrite(Req req) {
        if (req.operation.terminal()) {
            done(req);
            return;
        }
        if (!req.operation.commit()) {
            done(req);
            return;
        }

        synchronized (this) {
            byte[] next = Arrays.copyOf(output, output.length + req.bytes.length);
            System.arraycopy(req.bytes, 0, next, output.length, req.bytes.length);
            output = next;
        }

        req.operation.resolve(receiver);
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

        if (!closePath && !req.operation.beginFirstEffectAttempt()) {
            done(req);
            return;
        }

        ProtosFutureValue lower =
                invokeFuture(
                        target,
                        "write",
                        List.of(bytes(pending)),
                        req.a);
        if (lower == null) {
            if (closePath) {
                poison(req, true);
            } else {
                failUnknownFirstEffect(req, ioError(req.a));
            }
            return;
        }

        installLower(req, lower);
        lower.observe(
                terminal -> {
                    clearLower(req, lower);
                    switch (terminal.state()) {
                        case RESOLVED -> {
                            if (!closePath
                                    && !req.operation.finishFirstEffectAttempt(true)) {
                                done(req);
                                return;
                            }
                            synchronized (this) {
                                if (output.length >= pending.length) {
                                    output =
                                            Arrays.copyOfRange(
                                                    output, pending.length, output.length);
                                }
                            }
                            flushTarget(req, closePath);
                        }
                        case CANCELLED -> {
                            if (closePath) {
                                poison(req, true);
                                return;
                            }
                            if (!req.operation.finishFirstEffectAttempt(false)) {
                                done(req);
                                return;
                            }
                            poison(req, false);
                        }
                        case FAILED -> {
                            if (closePath) {
                                poisonFrom(req, terminal, true);
                            } else {
                                failUnknownFirstEffect(
                                        req,
                                        terminal.failedError()
                                                .orElseGet(() -> ioError(req.a)));
                            }
                        }
                        case PENDING -> { }
                    }
                });
    }

    private void flushTarget(Req req, boolean closePath) {
        if (target.lookupSlot("flush").isEmpty()) {
            if (closePath) {
                finishFinalization(req.a, null);
            } else {
                if (!req.operation.committed() && !req.operation.commit()) {
                    done(req);
                    return;
                }
                req.operation.resolve(receiver);
                done(req);
            }
            return;
        }

        boolean firstEffect = !closePath && !req.operation.committed();
        if (firstEffect && !req.operation.beginFirstEffectAttempt()) {
            done(req);
            return;
        }

        ProtosFutureValue lower =
                invokeFuture(
                        target,
                        "flush",
                        List.of(),
                        req.a);
        if (lower == null) {
            if (closePath) {
                poison(req, true);
            } else if (firstEffect) {
                failUnknownFirstEffect(req, ioError(req.a));
            } else {
                poison(req, false);
            }
            return;
        }

        installLower(req, lower);
        lower.observe(
                terminal -> {
                    clearLower(req, lower);
                    switch (terminal.state()) {
                        case RESOLVED -> {
                            if (closePath) {
                                finishFinalization(req.a, null);
                                return;
                            }
                            if (firstEffect
                                    && !req.operation.finishFirstEffectAttempt(true)) {
                                done(req);
                                return;
                            }
                            req.operation.resolve(receiver);
                            done(req);
                        }
                        case CANCELLED -> {
                            if (closePath) {
                                poison(req, true);
                                return;
                            }
                            if (firstEffect) {
                                if (!req.operation.finishFirstEffectAttempt(false)) {
                                    done(req);
                                    return;
                                }
                            }
                            poison(req, false);
                        }
                        case FAILED -> {
                            ProtosObjectValue error =
                                    terminal.failedError()
                                            .orElseGet(() -> ioError(req.a));
                            if (closePath) {
                                poisonWith(req, error, true);
                            } else if (firstEffect) {
                                failUnknownFirstEffect(req, error);
                            } else {
                                poisonWith(req, error, false);
                            }
                        }
                        case PENDING -> { }
                    }
                });
    }

    private void failUnknownFirstEffect(Req req, ProtosObjectValue error) {
        synchronized (this) {
            if (outputError == null) outputError = error;
            error = outputError;
        }
        req.operation.failFirstEffectAttemptWithUnknownEffect(error);
        done(req);
    }

    private void poisonFrom(
            Req req, ProtosFutureValue terminal, boolean closePath) {
        ProtosObjectValue error =
                terminal.state() == ProtosFutureValue.State.FAILED
                        ? terminal.failedError().orElseGet(() -> ioError(req.a))
                        : ioError(req.a);
        poisonWith(req, error, closePath);
    }

    private void poison(Req req, boolean closePath) {
        poisonWith(req, ioError(req.a), closePath);
    }

    private void poisonWith(
            Req req, ProtosObjectValue error, boolean closePath) {
        synchronized (this) {
            if (outputError == null) outputError = error;
            error = outputError;
        }
        if (closePath) {
            finishFinalization(req.a, error);
        } else {
            req.operation.fail(error);
            done(req);
        }
    }

    /**
     * Existing physical buffered release path, now invoked by the common lifecycle.
     *
     * <p>D112/PLAT030 still govern a later C-prime lifecycle-release migration. This
     * method deliberately preserves the pre-PLAT031 callback/observer mechanics.
     */
    private void release(ProtosIoLifecycle.ReleaseCompletion completion) {
        ProtosActivation activation;
        synchronized (this) {
            if (releaseCompletion != null) {
                throw new IllegalStateException("buffered I/O release already started");
            }
            releaseCompletion = completion;
            activation = closeActivation;
        }
        if (activation == null) {
            throw new IllegalStateException(
                    "buffered I/O release started without close activation");
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
                                null,
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
            finishRelease(primary);
            return;
        }

        ProtosFutureValue targetClose =
                invokeFuture(target, "close", List.of(), activation);
        if (targetClose == null) {
            finishRelease(primary != null ? primary : ioError(activation));
            return;
        }

        targetClose.observe(
                terminal -> {
                    if (terminal.state() == ProtosFutureValue.State.RESOLVED) {
                        finishRelease(primary);
                    } else if (terminal.state()
                            != ProtosFutureValue.State.PENDING) {
                        ProtosObjectValue targetFailure =
                                terminal.state() == ProtosFutureValue.State.FAILED
                                        ? terminal.failedError()
                                                .orElseGet(() -> ioError(activation))
                                        : ioError(activation);
                        finishRelease(
                                primary != null ? primary : targetFailure);
                    }
                });
    }

    private void finishRelease(ProtosObjectValue error) {
        ProtosIoLifecycle.ReleaseCompletion completion;
        synchronized (this) {
            completion = releaseCompletion;
            releaseCompletion = null;
        }
        if (completion == null) {
            throw new IllegalStateException(
                    "buffered I/O release completed without lifecycle owner");
        }
        if (error == null) completion.succeeded();
        else completion.failed(error);
    }

    private ProtosFutureValue invokeFuture(
            ProtosObjectValue object,
            String message,
            List<?> arguments,
            ProtosActivation activation) {
        try {
            Object value =
                    ProtosInvocation.invokeMessage(
                            object, message, arguments, activation);
            return value instanceof ProtosFutureValue future ? future : null;
        } catch (RuntimeException exception) {
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
