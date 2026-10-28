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

import com.guillermomolina.protos.execution.ProtosBufferedByteReaderCPrimeExecution;
import com.guillermomolina.protos.execution.ProtosBufferedByteWriterCPrimeExecution;
import com.guillermomolina.protos.execution.ProtosInvocation;
import com.guillermomolina.protos.execution.ProtosIoReleaseCPrimeExecution;
import java.util.*;

/** Ordered bounded state machine for the standard {@code std:io} byte buffering wrappers. */
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
    private ProtosIoReleaseCPrimeExecution.Plan releasePlan;

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
        // A non-Integer or an Integer outside the int range, including a large one, maps to 0.
        int limit =
                ProtosNumericValueSupport.isIntegerInIntRange(maximum)
                        ? ProtosNumericValueSupport.exactInt(maximum)
                        : 0;
        if (limit <= 0) {
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
                        limit,
                        null,
                        cPrimePlan));
    }

    public ProtosFutureValue write(ProtosActivation activation, Object value) {
        check(activation);
        if (!(value instanceof ProtosBytesValue bytes)) {
            return failed(
                    activation, ProtosCoreErrors.StandardError.INVALID_I_O_ARGUMENT);
        }
        byte[] snapshot = bytes.octetSnapshot();

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
        return flush(activation, null);
    }

    public ProtosFutureValue flushForCPrimeRuntime(
            ProtosActivation activation,
            ProtosBufferedByteWriterCPrimeExecution.Plan plan) {
        return flush(
                activation,
                Objects.requireNonNull(plan, "plan"));
    }

    private ProtosFutureValue flush(
            ProtosActivation activation,
            ProtosBufferedByteWriterCPrimeExecution.Plan cPrimePlan) {
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
                        null,
                        null,
                        cPrimePlan));
    }

    public ProtosFutureValue close(ProtosActivation activation) {
        return close(
                activation,
                ProtosIoReleaseCPrimeExecution.planForEnteredContextIfAvailable());
    }

    public ProtosFutureValue closeForCPrimeRuntime(
            ProtosActivation activation,
            ProtosIoReleaseCPrimeExecution.Plan plan) {
        return close(
                activation,
                Objects.requireNonNull(plan, "plan"));
    }

    private ProtosFutureValue close(
            ProtosActivation activation,
            ProtosIoReleaseCPrimeExecution.Plan plan) {
        check(activation);
        synchronized (this) {
            if (closeActivation == null) {
                closeActivation = activation;
                releasePlan = plan;
            }
        }
        return plan == null
                ? lifecycle.close(activation)
                : lifecycle.closeWithGuestReleaseForRuntime(activation);
    }

    private static final class Req {
        final ProtosActivation a;
        final ProtosIoOperation operation;
        final Kind kind;
        final int maximum;
        final byte[] bytes;
        final ProtosBufferedByteReaderCPrimeExecution.Plan readCPrimePlan;
        final ProtosBufferedByteWriterCPrimeExecution.Plan flushCPrimePlan;
        ProtosFutureValue lower;
        ProtosFutureValue.Observer lowerObserver;
        /** Guarded by the adapter monitor; set once the request has left the queue. */
        boolean retired;

        Req(
                ProtosActivation activation,
                ProtosIoOperation operation,
                Kind kind,
                int maximum,
                byte[] bytes) {
            this(activation, operation, kind, maximum, bytes, null, null);
        }

        Req(
                ProtosActivation activation,
                ProtosIoOperation operation,
                Kind kind,
                int maximum,
                byte[] bytes,
                ProtosBufferedByteReaderCPrimeExecution.Plan readCPrimePlan) {
            this(activation, operation, kind, maximum, bytes, readCPrimePlan, null);
        }

        Req(
                ProtosActivation activation,
                ProtosIoOperation operation,
                Kind kind,
                int maximum,
                byte[] bytes,
                ProtosBufferedByteReaderCPrimeExecution.Plan readCPrimePlan,
                ProtosBufferedByteWriterCPrimeExecution.Plan flushCPrimePlan) {
            this.a = activation;
            this.operation = operation;
            this.kind = kind;
            this.maximum = maximum;
            this.bytes = bytes;
            this.readCPrimePlan = readCPrimePlan;
            this.flushCPrimePlan = flushCPrimePlan;
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
                removed = !req.retired && q.remove(req);
                if (removed) req.retired = true;
            } else {
                // An active C-prime read without a lower Future is retired by its
                // terminal-idle notification once no C-prime segment can still publish one.
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
                q.removeFirst().retired = true;
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
            case FLUSH -> {
                if (req.flushCPrimePlan == null) doFlush(req, false);
                else doFlushCPrime(req);
            }
        }
    }

    /** Retires {@code req} at most once; a late duplicate notification never restarts pumping. */
    private void done(Req req) {
        boolean continuePumping;
        synchronized (this) {
            if (req.retired) return;
            req.retired = true;
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

        req.operation.onDeferredCPrimeTerminalIdleForRuntime(
                () -> retireTerminalCPrimeReadIfSafe(req));
        if (req.operation.terminal()) return;

        try {
            ProtosBufferedByteReaderCPrimeExecution.schedule(
                    req.readCPrimePlan,
                    req.operation,
                    this,
                    target,
                    readArguments(req));
        } catch (IllegalStateException schedulingFailure) {
            if (req.operation.terminal()) {
                // Cancellation or close won the race against driver installation; the
                // terminal-idle notification retires the request idempotently.
                retireTerminalCPrimeReadIfSafe(req);
            } else {
                // A live operation that cannot be scheduled is a runtime defect: settle the
                // read and free its queue position, then surface the defect.
                failReadCPrime(req.operation);
                throw schedulingFailure;
            }
        }
    }

    /**
     * Retires a terminal active C-prime read once its execution is quiescent. A pending lower
     * Future keeps the queue position so it cannot be overtaken while it may still consume
     * input; its terminal observer completes the retirement through the existing settlement.
     */
    private void retireTerminalCPrimeReadIfSafe(Req req) {
        ProtosFutureValue lower;
        synchronized (this) {
            if (req.retired
                    || activeReq != req
                    || req.kind != Kind.READ
                    || req.readCPrimePlan == null) {
                return;
            }
            lower = req.lower;
        }
        if (!req.operation.terminal()) return;

        if (lower == null) {
            done(req);
        } else if (!lower.isPending()) {
            consumeReadLowerForCPrimeRuntime(req.operation, lower);
        }
    }

    private List<?> readArguments(Req req) {
        return List.of(
                ProtosNumericValueSupport.integer(
                                Math.max(req.maximum, READ_AHEAD)));
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

        byte[] obtained = bytes.octetSnapshot();
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

    private void doFlushCPrime(Req req) {
        if (stopBeforeLowerWork(req)) return;

        byte[] pending;
        synchronized (this) {
            pending = output.clone();
        }

        if (pending.length == 0 && target.lookupSlot("flush").isEmpty()) {
            if (!req.operation.commit()) {
                done(req);
                return;
            }
            req.operation.resolve(receiver);
            done(req);
            return;
        }

        Object payload = pending.length == 0 ? null : bytes(pending);
        ProtosBufferedByteWriterCPrimeExecution.schedule(
                req.flushCPrimePlan,
                req.operation,
                this,
                target,
                pending,
                payload);
    }

    public boolean beginFlushFirstEffectForCPrimeRuntime(
            ProtosIoOperation operation) {
        Req req = activeFlushCPrimeReq(operation);
        if (operation.beginFirstEffectAttempt()) {
            return true;
        }
        if (operation.terminal()) {
            done(req);
            return false;
        }
        throw new IllegalStateException(
                "BufferedWriter C-prime first-effect attempt could not start");
    }

    public void installFlushLowerForCPrimeRuntime(
            ProtosIoOperation operation,
            ProtosFutureValue lower) {
        Objects.requireNonNull(lower, "lower");
        Req req = activeFlushCPrimeReq(operation);
        synchronized (this) {
            if (req.lower != null) {
                throw new IllegalStateException(
                        "BufferedWriter C-prime request already owns a lower Future");
            }
            req.lower = lower;
        }
        if (operation.backendCancellationRequested()) {
            lower.cancelRequest();
        }
    }

    public void clearFlushLowerForCPrimeRuntime(
            ProtosIoOperation operation,
            ProtosFutureValue lower) {
        Objects.requireNonNull(lower, "lower");
        synchronized (this) {
            Req req = activeReq;
            if (req != null
                    && req.operation == operation
                    && req.kind == Kind.FLUSH
                    && req.flushCPrimePlan != null
                    && req.lower == lower) {
                req.lower = null;
            }
        }
    }

    public boolean flushFirstLowerResolvedForCPrimeRuntime(
            ProtosIoOperation operation,
            boolean writeFirst,
            int pendingLength) {
        Req req = activeFlushCPrimeReq(operation);
        if (!operation.finishFirstEffectAttempt(true)) {
            done(req);
            return false;
        }

        if (writeFirst) {
            synchronized (this) {
                if (output.length < pendingLength) {
                    throw new IllegalStateException(
                            "BufferedWriter output frontier shrank during active flush");
                }
                output = Arrays.copyOfRange(output, pendingLength, output.length);
            }
            if (target.lookupSlot("flush").isPresent()) {
                return true;
            }
        }

        operation.resolve(receiver);
        done(req);
        return false;
    }

    public void flushFirstLowerFailedForCPrimeRuntime(
            ProtosIoOperation operation,
            ProtosObjectValue error) {
        Req req = activeFlushCPrimeReq(operation);
        failUnknownFirstEffect(req, Objects.requireNonNull(error, "error"));
    }

    public void flushFirstLowerCancelledForCPrimeRuntime(
            ProtosIoOperation operation) {
        Req req = activeFlushCPrimeReq(operation);
        if (!operation.finishFirstEffectAttempt(false)) {
            done(req);
            return;
        }
        poison(req, false);
    }

    public void flushFirstLowerUnknownFailureForCPrimeRuntime(
            ProtosIoOperation operation) {
        Req req = activeFlushCPrimeReq(operation);
        failUnknownFirstEffect(req, ioError(req.a));
    }

    public void flushFollowupResolvedForCPrimeRuntime(
            ProtosIoOperation operation) {
        Req req = activeFlushCPrimeReq(operation);
        operation.resolve(receiver);
        done(req);
    }

    public void flushFollowupFailedForCPrimeRuntime(
            ProtosIoOperation operation,
            ProtosObjectValue error) {
        Req req = activeFlushCPrimeReq(operation);
        poisonWith(req, Objects.requireNonNull(error, "error"), false);
    }

    public void flushFollowupUnknownFailureForCPrimeRuntime(
            ProtosIoOperation operation) {
        Req req = activeFlushCPrimeReq(operation);
        poison(req, false);
    }

    public void unexpectedFlushCPrimeCompletionForRuntime(
            ProtosIoOperation operation) {
        failFlushCPrimeDriver(operation);
    }

    public void flushCPrimeDriverFailedForRuntime(
            ProtosIoOperation operation,
            ProtosObjectValue ignoredExactError) {
        failFlushCPrimeDriver(operation);
    }

    private void failFlushCPrimeDriver(ProtosIoOperation operation) {
        Objects.requireNonNull(operation, "operation");
        Req req;
        synchronized (this) {
            req = activeReq;
            if (req == null
                    || req.operation != operation
                    || req.kind != Kind.FLUSH
                    || req.flushCPrimePlan == null) {
                return;
            }
            req.lower = null;
        }

        if (operation.terminal()) {
            done(req);
        } else if (operation.firstEffectAttemptInFlight()) {
            failUnknownFirstEffect(req, ioError(req.a));
        } else if (operation.committed()) {
            poison(req, false);
        } else {
            operation.fail(ioError(req.a));
            done(req);
        }
    }

    private Req activeFlushCPrimeReq(ProtosIoOperation operation) {
        Objects.requireNonNull(operation, "operation");
        synchronized (this) {
            Req req = activeReq;
            if (req == null
                    || req.operation != operation
                    || req.kind != Kind.FLUSH
                    || req.flushCPrimePlan == null
                    || q.peekFirst() != req) {
                throw new IllegalStateException(
                        "BufferedWriter C-prime operation has no active owning request");
            }
            return req;
        }
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

    /** Physical buffered release, using PLAT030 C-prime whenever an entered Context supplied its plan. */
    private void release(ProtosIoLifecycle.ReleaseCompletion completion) {
        ProtosIoReleaseCPrimeExecution.Plan plan;
        synchronized (this) {
            plan = releasePlan;
        }
        if (plan == null) {
            releaseLegacy(completion);
            return;
        }
        releaseCPrime(plan, completion);
    }

    private void releaseCPrime(
            ProtosIoReleaseCPrimeExecution.Plan plan,
            ProtosIoLifecycle.ReleaseCompletion completion) {
        ProtosActivation activation;
        ProtosObjectValue primary;
        byte[] pending;
        synchronized (this) {
            activation = closeActivation;
            primary = outputError;
            pending = output.clone();
        }
        if (activation == null) {
            throw new IllegalStateException(
                    "buffered I/O release started without close activation");
        }

        ArrayList<ProtosIoReleaseCPrimeExecution.Step> steps = new ArrayList<>();

        if (mode == Mode.WRITER
                && primary == null
                && pending.length > 0) {
            ProtosBytesValue payload = bytes(pending);
            steps.add(
                    ProtosIoReleaseCPrimeExecution.Step.ordinary(
                            target,
                            "write",
                            List.of(payload),
                            () -> releaseBufferedPrefix(pending.length),
                            this::recordReleaseOutputFailure));
            if (target.lookupSlot("flush").isPresent()) {
                steps.add(
                        ProtosIoReleaseCPrimeExecution.Step.ordinary(
                                target,
                                "flush",
                                List.of(),
                                () -> {},
                                this::recordReleaseOutputFailure));
            }
        }

        if (owning) {
            steps.add(
                    ProtosIoReleaseCPrimeExecution.Step.mandatoryFinalizer(
                            target,
                            "close",
                            List.of(),
                            () -> {},
                            ignored -> {}));
        }

        if (steps.isEmpty()) {
            if (primary == null) completion.succeeded();
            else completion.failed(primary);
            return;
        }

        ProtosIoReleaseExecution release =
                lifecycle.beginReleaseExecutionForRuntime(
                        activation,
                        completion);
        ProtosIoReleaseCPrimeExecution.Sequence sequence =
                ProtosIoReleaseCPrimeExecution.sequence(
                        release,
                        primary,
                        steps);
        try {
            ProtosIoReleaseCPrimeExecution.schedule(
                    plan,
                    release,
                    sequence);
        } catch (RuntimeException failure) {
            release.fail(ioError(activation));
        }
    }

    private void releaseBufferedPrefix(int pendingLength) {
        synchronized (this) {
            if (output.length < pendingLength) {
                throw new IllegalStateException(
                        "BufferedWriter output frontier shrank during lifecycle release");
            }
            output =
                    Arrays.copyOfRange(
                            output,
                            pendingLength,
                            output.length);
        }
    }

    private void recordReleaseOutputFailure(ProtosObjectValue error) {
        synchronized (this) {
            if (outputError == null) {
                outputError = error;
            }
        }
    }

    private void releaseLegacy(ProtosIoLifecycle.ReleaseCompletion completion) {
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
            bytes.indexedAdd(ProtosNumericValueSupport.octet(value & 255));
        }
        return bytes;
    }
}
