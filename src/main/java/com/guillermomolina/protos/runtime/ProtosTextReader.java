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

import com.guillermomolina.protos.execution.ProtosInvocation;
import com.guillermomolina.protos.execution.ProtosTextReaderCPrimeExecution;
import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** One ordered transactional decoder/input/lifecycle domain for a standard TextReader. */
public final class ProtosTextReader {
    private static final int SOURCE_READ_AHEAD = 8192;

    private final ProtosObjectValue receiver;
    private final Object source;
    private final ProtosEncodingValue encoding;
    private final boolean owning;
    private final ProtosIoLifecycle lifecycle;
    private final ArrayDeque<Request> queue = new ArrayDeque<>();

    private ProtosEncodingValue.StreamingDecoder decoder;
    private Request active;
    private byte[] retained = new byte[0];
    private boolean sourceEof;
    private boolean pendingLfAfterCr;
    private ProtosObjectValue deferredError;
    private ProtosObjectValue failedError;
    private ProtosActivation closeActivation;

    public ProtosTextReader(
            ProtosObjectValue receiver,
            Object source,
            ProtosEncodingValue encoding,
            ProtosActivation constructionActivation,
            boolean owning) {
        this.receiver = Objects.requireNonNull(receiver, "receiver");
        this.source = Objects.requireNonNull(source, "source");
        this.encoding = Objects.requireNonNull(encoding, "encoding");
        this.decoder = encoding.newStreamingDecoderForRuntime();
        this.owning = owning;
        ProtosActivation activation =
                Objects.requireNonNull(constructionActivation, "constructionActivation");
        this.lifecycle =
                new ProtosIoLifecycle(
                        receiver,
                        activation.prelude().orElseThrow().futurePrototype(),
                        activation.executionDomain(),
                        this::release);
    }

    public ProtosFutureValue readText(ProtosActivation activation) {
        return enqueue(
                Objects.requireNonNull(activation, "activation"),
                RequestKind.READ_TEXT,
                null,
                null);
    }

    public ProtosFutureValue readTextForCPrimeRuntime(
            ProtosActivation activation,
            ProtosTextReaderCPrimeExecution.Plan plan) {
        return enqueue(
                Objects.requireNonNull(activation, "activation"),
                RequestKind.READ_TEXT,
                null,
                Objects.requireNonNull(plan, "plan"));
    }

    public ProtosFutureValue readLine(ProtosActivation activation, BigInteger maxBytes) {
        Objects.requireNonNull(activation, "activation");
        if (maxBytes != null && maxBytes.signum() <= 0) {
            throw new IllegalArgumentException("readLine maxBytes must be positive");
        }
        return enqueue(activation, RequestKind.READ_LINE, maxBytes, null);
    }

    public ProtosFutureValue readLineForCPrimeRuntime(
            ProtosActivation activation,
            BigInteger maxBytes,
            ProtosTextReaderCPrimeExecution.Plan plan) {
        Objects.requireNonNull(activation, "activation");
        if (maxBytes != null && maxBytes.signum() <= 0) {
            throw new IllegalArgumentException("readLine maxBytes must be positive");
        }
        return enqueue(
                activation,
                RequestKind.READ_LINE,
                maxBytes,
                Objects.requireNonNull(plan, "plan"));
    }

    private ProtosFutureValue enqueue(
            ProtosActivation activation,
            RequestKind kind,
            BigInteger maxBytes,
            ProtosTextReaderCPrimeExecution.Plan cPrimePlan) {
        ProtosIoOperation operation = lifecycle.beginOperation(activation);
        ProtosFutureValue future = operation.future();
        if (operation.terminal()) return future;

        Request request = new Request(activation, operation, kind, maxBytes, cPrimePlan);
        operation.onCancellation(() -> cancel(request));
        synchronized (this) {
            queue.addLast(request);
        }
        pump();
        return future;
    }

    public ProtosFutureValue close(ProtosActivation activation) {
        Objects.requireNonNull(activation, "activation");
        synchronized (this) {
            if (closeActivation == null) closeActivation = activation;
        }
        return lifecycle.close(activation);
    }

    public ProtosEncodingValue encodingForRuntime() { return encoding; }
    public Object sourceForRuntime() { return source; }
    public boolean owningForRuntime() { return owning; }

    private enum RequestKind { READ_TEXT, READ_LINE }

    private static final class Request {
        final ProtosActivation activation;
        final ProtosIoOperation operation;
        final RequestKind kind;
        final BigInteger maxBytes;
        final ProtosTextReaderCPrimeExecution.Plan cPrimePlan;
        ProtosFutureValue lower;
        ProtosFutureValue.Observer lowerObserver;
        ProtosObjectValue lowerFailure;
        boolean driving;
        boolean driveRequested;

        Request(
                ProtosActivation activation,
                ProtosIoOperation operation,
                RequestKind kind,
                BigInteger maxBytes,
                ProtosTextReaderCPrimeExecution.Plan cPrimePlan) {
            this.activation = activation;
            this.operation = operation;
            this.kind = kind;
            this.maxBytes = maxBytes;
            this.cPrimePlan = cPrimePlan;
        }
    }

    private enum ReadKind { TEXT, NEED_INPUT, EOF, ERROR }
    private record ReadResult(
            ReadKind kind,
            String text,
            int consumed,
            ProtosEncodingValue.StreamingDecoder nextDecoder,
            boolean deferEncodingError) {}

    private enum LineKind { LINE, NEED_INPUT, EOF, ERROR, TOO_LONG }
    private record LineResult(
            LineKind kind,
            String text,
            int consumed,
            ProtosEncodingValue.StreamingDecoder nextDecoder,
            boolean pendingLfAfterCr) {}

    private enum FoldKind { READY, NEED_INPUT, ERROR }
    private record FoldResult(
            FoldKind kind,
            int consumed,
            ProtosEncodingValue.StreamingDecoder nextDecoder) {}

    private void pump() {
        Request next = null;
        synchronized (this) {
            if (active != null) return;
            while (!queue.isEmpty()) {
                Request candidate = queue.peekFirst();
                if (candidate.operation.terminal()) {
                    queue.removeFirst();
                    continue;
                }
                active = candidate;
                next = candidate;
                break;
            }
        }
        if (next == null) return;
        if (next.cPrimePlan != null) {
            if (advanceUntilInputOrTerminal(next)) {
                ProtosTextReaderCPrimeExecution.schedule(
                        next.cPrimePlan,
                        next.operation,
                        this,
                        source);
            }
            return;
        }
        drive(next);
    }

    /** Prevent already-terminal lower Futures from creating recursive drive stack growth. */
    private void drive(Request request) {
        synchronized (request) {
            request.driveRequested = true;
            if (request.driving) return;
            request.driving = true;
        }
        while (true) {
            synchronized (request) {
                request.driveRequested = false;
            }
            driveOnce(request);
            synchronized (request) {
                if (request.driveRequested) continue;
                request.driving = false;
                if (!request.driveRequested) return;
                request.driving = true;
            }
        }
    }

    private void driveOnce(Request request) {
        if (advanceUntilInputOrTerminal(request)) {
            startLowerRead(request);
        }
    }

    /**
     * Runs only native/codec/queue leaf work. Returning true means the same outer operation
     * needs another ordinary source.read callback. The caller owns how that callback is
     * sequenced: the compatibility path calls Java directly; the PLAT029 path loops in C-prime.
     */
    private boolean advanceUntilInputOrTerminal(Request request) {
        if (!isActive(request)) return false;

        if (request.operation.terminal()) {
            ProtosFutureValue lower;
            ProtosIoLifecycle.State lifecycleState = lifecycle.state();
            synchronized (this) {
                lower = request.lower;
            }
            if (lower == null || lifecycleState != ProtosIoLifecycle.State.OPEN) {
                finishQueueRequest(request);
            }
            return false;
        }

        ProtosObjectValue permanent;
        ProtosObjectValue deferred;
        ProtosObjectValue sourceFailure;
        byte[] snapshot;
        boolean eof;
        boolean foldLf;
        synchronized (this) {
            permanent = failedError;
            deferred = deferredError;
            sourceFailure = request.lowerFailure;
            snapshot = retained.clone();
            eof = sourceEof;
            foldLf = pendingLfAfterCr;
        }

        if (permanent != null) {
            failAndFinish(request, permanent, false, false);
            return false;
        }

        if (foldLf && deferred == null && sourceFailure == null) {
            FoldResult fold;
            try {
                fold = resolvePendingLf(snapshot, eof);
            } catch (ProtosEncodingValue.ConversionFailure failure) {
                failEncoding(request);
                return false;
            }
            if (fold.kind() == FoldKind.NEED_INPUT) {
                return true;
            }
            if (fold.kind() == FoldKind.ERROR) {
                failEncoding(request);
                return false;
            }
            synchronized (this) {
                pendingLfAfterCr = false;
                if (fold.consumed() > 0) {
                    consumeRetained(fold.consumed());
                    decoder = fold.nextDecoder();
                }
                snapshot = retained.clone();
                eof = sourceEof;
            }
        } else if (foldLf) {
            synchronized (this) {
                pendingLfAfterCr = false;
            }
        }

        if (deferred != null) {
            failAndFinish(request, deferred, true, false);
            return false;
        }
        if (sourceFailure != null) {
            failAndFinish(request, sourceFailure, false, true);
            return false;
        }

        final ProtosEncodingValue.DecodePreview preview;
        try {
            preview = decoder.preview(snapshot, eof);
        } catch (ProtosEncodingValue.ConversionFailure failure) {
            failEncoding(request);
            return false;
        }

        if (request.kind == RequestKind.READ_TEXT) {
            ReadResult result = scanText(preview);
            switch (result.kind()) {
                case TEXT -> completeText(request, result);
                case EOF -> completeEof(request, result);
                case ERROR -> failEncoding(request);
                case NEED_INPUT -> {
                    return true;
                }
            }
            return false;
        }

        LineResult line = scanLine(preview, request.maxBytes);
        switch (line.kind()) {
            case LINE, EOF -> completeLine(request, line);
            case ERROR -> failEncoding(request);
            case TOO_LONG ->
                    failAndFinish(
                            request,
                            ProtosCoreErrors.newOccurrence(
                                    request.activation,
                                    ProtosCoreErrors.StandardError.LINE_TOO_LONG),
                            false,
                            true);
            case NEED_INPUT -> {
                return true;
            }
        }
        return false;
    }

    private FoldResult resolvePendingLf(byte[] bytes, boolean eof)
            throws ProtosEncodingValue.ConversionFailure {
        ProtosEncodingValue.DecodePreview preview = decoder.preview(bytes, eof);
        int consumed = 0;
        for (ProtosEncodingValue.DecodedUnit unit : preview.units()) {
            consumed += unit.sourceBytes();
            if (unit.text().isEmpty()) continue;
            if ("\n".equals(unit.text())) {
                return new FoldResult(FoldKind.READY, consumed, unit.nextDecoder());
            }
            return new FoldResult(FoldKind.READY, 0, decoder);
        }
        return switch (preview.status()) {
            case NEED_INPUT -> new FoldResult(FoldKind.NEED_INPUT, 0, decoder);
            case MALFORMED -> new FoldResult(FoldKind.ERROR, 0, decoder);
            case EOF -> new FoldResult(FoldKind.READY, 0, decoder);
        };
    }

    private ReadResult scanText(ProtosEncodingValue.DecodePreview preview) {
        int consumed = 0;
        ProtosEncodingValue.StreamingDecoder next = decoder;
        StringBuilder text = new StringBuilder();
        for (ProtosEncodingValue.DecodedUnit unit : preview.units()) {
            consumed += unit.sourceBytes();
            next = unit.nextDecoder();
            text.append(unit.text());
        }

        if (text.length() > 0) {
            return new ReadResult(
                    ReadKind.TEXT,
                    text.toString(),
                    consumed,
                    next,
                    preview.status() == ProtosEncodingValue.DecodeStatus.MALFORMED);
        }

        return switch (preview.status()) {
            case NEED_INPUT -> new ReadResult(ReadKind.NEED_INPUT, null, 0, decoder, false);
            case MALFORMED -> new ReadResult(ReadKind.ERROR, null, 0, decoder, false);
            case EOF -> new ReadResult(ReadKind.EOF, null, consumed, next, false);
        };
    }

    private LineResult scanLine(ProtosEncodingValue.DecodePreview preview, BigInteger maxBytes) {
        int consumed = 0;
        BigInteger lineBytes = BigInteger.ZERO;
        StringBuilder line = new StringBuilder();
        ProtosEncodingValue.StreamingDecoder next = decoder;
        List<ProtosEncodingValue.DecodedUnit> units = preview.units();

        for (int index = 0; index < units.size(); index++) {
            ProtosEncodingValue.DecodedUnit unit = units.get(index);

            if (unit.text().isEmpty()) {
                consumed += unit.sourceBytes();
                next = unit.nextDecoder();
                if (!unit.initialSetup()) {
                    lineBytes = lineBytes.add(BigInteger.valueOf(unit.sourceBytes()));
                    if (tooLong(lineBytes, maxBytes)) {
                        return new LineResult(LineKind.TOO_LONG, null, 0, decoder, false);
                    }
                }
                continue;
            }

            if ("\n".equals(unit.text())) {
                return new LineResult(
                        LineKind.LINE,
                        line.toString(),
                        consumed + unit.sourceBytes(),
                        unit.nextDecoder(),
                        false);
            }

            if ("\r".equals(unit.text())) {
                int crConsumed = consumed + unit.sourceBytes();
                ProtosEncodingValue.StreamingDecoder crState = unit.nextDecoder();
                int afterCr = 0;
                for (int look = index + 1; look < units.size(); look++) {
                    ProtosEncodingValue.DecodedUnit following = units.get(look);
                    afterCr += following.sourceBytes();
                    if (following.text().isEmpty()) continue;
                    if ("\n".equals(following.text())) {
                        return new LineResult(
                                LineKind.LINE,
                                line.toString(),
                                crConsumed + afterCr,
                                following.nextDecoder(),
                                false);
                    }
                    return new LineResult(
                            LineKind.LINE,
                            line.toString(),
                            crConsumed,
                            crState,
                            true);
                }
                return new LineResult(
                        LineKind.LINE,
                        line.toString(),
                        crConsumed,
                        crState,
                        true);
            }

            consumed += unit.sourceBytes();
            next = unit.nextDecoder();
            lineBytes = lineBytes.add(BigInteger.valueOf(unit.sourceBytes()));
            if (tooLong(lineBytes, maxBytes)) {
                return new LineResult(LineKind.TOO_LONG, null, 0, decoder, false);
            }
            line.append(unit.text());
        }

        return switch (preview.status()) {
            case NEED_INPUT -> new LineResult(LineKind.NEED_INPUT, null, 0, decoder, false);
            case MALFORMED -> new LineResult(LineKind.ERROR, null, 0, decoder, false);
            case EOF ->
                    line.length() == 0
                            ? new LineResult(LineKind.EOF, null, consumed, next, false)
                            : new LineResult(LineKind.LINE, line.toString(), consumed, next, false);
        };
    }

    private static boolean tooLong(BigInteger used, BigInteger maxBytes) {
        return maxBytes != null && used.compareTo(maxBytes) > 0;
    }

    private void completeText(Request request, ReadResult result) {
        ProtosObjectValue deferred =
                result.deferEncodingError()
                        ? ProtosCoreErrors.newOccurrence(
                                request.activation,
                                ProtosCoreErrors.StandardError.ENCODING_ERROR)
                        : null;
        if (!request.operation.commit()) {
            finishCancelledIfNoLower(request);
            return;
        }
        synchronized (this) {
            consumeRetained(result.consumed());
            decoder = result.nextDecoder();
            if (deferred != null) deferredError = deferred;
        }
        request.operation.resolve(new ProtosStringValue(result.text()));
        finishQueueRequest(request);
    }

    private void completeEof(Request request, ReadResult result) {
        if (!request.operation.commit()) {
            finishCancelledIfNoLower(request);
            return;
        }
        synchronized (this) {
            consumeRetained(result.consumed());
            decoder = result.nextDecoder();
        }
        request.operation.resolve(ProtosNullValue.INSTANCE);
        finishQueueRequest(request);
    }

    private void completeLine(Request request, LineResult result) {
        if (!request.operation.commit()) {
            finishCancelledIfNoLower(request);
            return;
        }
        synchronized (this) {
            consumeRetained(result.consumed());
            decoder = result.nextDecoder();
            pendingLfAfterCr = result.pendingLfAfterCr();
        }
        if (result.kind() == LineKind.EOF) {
            request.operation.resolve(ProtosNullValue.INSTANCE);
        } else {
            request.operation.resolve(new ProtosStringValue(result.text()));
        }
        finishQueueRequest(request);
    }

    private void failEncoding(Request request) {
        failAndFinish(
                request,
                ProtosCoreErrors.newOccurrence(
                        request.activation, ProtosCoreErrors.StandardError.ENCODING_ERROR),
                false,
                true);
    }

    private void failAndFinish(
            Request request,
            ProtosObjectValue error,
            boolean consumeDeferred,
            boolean permanentOnWin) {
        boolean won = request.operation.fail(error);
        if (won) {
            synchronized (this) {
                if (consumeDeferred && deferredError == error) deferredError = null;
                request.lowerFailure = null;
                if (permanentOnWin || consumeDeferred) failedError = error;
            }
        }
        finishQueueRequest(request);
    }

    /** PLAT029 leaf arguments for one source.read callback. */
    public List<?> sourceReadArgumentsForCPrimeRuntime() {
        return List.of(new ProtosIntegerValue(BigInteger.valueOf(SOURCE_READ_AHEAD)));
    }

    /**
     * Publishes one lower Future to the reader before the operation-owned C-prime wait is
     * retained. A second observer records only terminal evidence needed for the historical
     * late-read-ahead rule; it never schedules or executes guest code.
     */
    public boolean observeLowerForCPrimeRuntime(
            ProtosIoOperation operation,
            ProtosFutureValue lower) {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(lower, "lower");

        Request request;
        ProtosFutureValue.Observer observer;
        boolean terminalWithoutRequest = false;
        synchronized (this) {
            request = active;
            if (request == null
                    || request.operation != operation
                    || request.cPrimePlan == null
                    || queue.peekFirst() != request) {
                if (!operation.terminal()) {
                    throw new IllegalStateException(
                            "TextReader C-prime lower Future has no active owning request");
                }
                terminalWithoutRequest = true;
                observer = null;
            } else {
                if (request.lower != null || request.lowerObserver != null) {
                    throw new IllegalStateException(
                            "TextReader C-prime request already owns a lower Future");
                }
                request.lower = lower;
                observer = ignored -> cPrimeLowerTerminalObserved(request, lower);
                request.lowerObserver = observer;
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
                consumeLowerForCPrimeRuntime(operation, lower);
            }
            return false;
        }
        return true;
    }

    /** Consumes one terminal lower Future on the Actor-domain C-prime segment. */
    public boolean consumeLowerForCPrimeRuntime(
            ProtosIoOperation operation,
            ProtosFutureValue lower) {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(lower, "lower");
        if (lower.isPending()) {
            throw new IllegalStateException(
                    "TextReader C-prime attempted to consume a pending lower Future");
        }

        ProtosFutureValue.State state = lower.state();
        Object resolved =
                state == ProtosFutureValue.State.RESOLVED
                        ? lower.resolvedValue().orElseThrow()
                        : null;
        ProtosObjectValue lowerError =
                state == ProtosFutureValue.State.FAILED
                        ? lower.failedError().orElseThrow()
                        : null;
        boolean invalidResolved = false;
        byte[] data = null;
        boolean eof = false;
        if (state == ProtosFutureValue.State.RESOLVED) {
            if (resolved == ProtosNullValue.INSTANCE) {
                eof = true;
            } else if (resolved instanceof ProtosBytesValue bytes) {
                data = snapshotBytes(bytes);
                invalidResolved = data == null || data.length == 0;
            } else {
                invalidResolved = true;
            }
        }

        Request request;
        ProtosFutureValue.Observer observer;
        boolean outerTerminal;
        boolean closing;
        synchronized (this) {
            request = active;
            if (request == null
                    || request.operation != operation
                    || request.cPrimePlan == null
                    || request.lower != lower) {
                return false;
            }
            observer = request.lowerObserver;
            request.lowerObserver = null;
            request.lower = null;
            outerTerminal = operation.terminal();
            closing = lifecycle.state() != ProtosIoLifecycle.State.OPEN;

            if (outerTerminal) {
                if (!closing) {
                    switch (state) {
                        case RESOLVED -> {
                            if (invalidResolved) {
                                if (deferredError == null) {
                                    deferredError = ioError(request.activation);
                                }
                            } else if (eof) {
                                sourceEof = true;
                            } else {
                                appendRetained(data);
                            }
                        }
                        case FAILED -> {
                            if (deferredError == null) deferredError = lowerError;
                        }
                        case CANCELLED, PENDING -> { }
                    }
                }
            } else {
                switch (state) {
                    case RESOLVED -> {
                        if (invalidResolved) request.lowerFailure = ioError(request.activation);
                        else if (eof) sourceEof = true;
                        else appendRetained(data);
                    }
                    case FAILED -> request.lowerFailure = lowerError;
                    case CANCELLED, PENDING -> { }
                }
            }
        }

        if (observer != null) {
            lower.removeObserver(observer);
        }

        if (outerTerminal) {
            finishQueueRequest(request);
            return false;
        }
        if (state == ProtosFutureValue.State.CANCELLED) {
            operation.requestCancellation();
            return false;
        }
        return advanceUntilInputOrTerminal(request);
    }

    /** Invalid source.read result is the historical permanent TextReader IOError lane. */
    public boolean invalidLowerForCPrimeRuntime(ProtosIoOperation operation) {
        return recordCPrimeSourceFailure(operation, null);
    }

    /** Preserve an exact guest Error from source.read; non-Error control is mapped to IOError. */
    public boolean invocationFailedForCPrimeRuntime(
            ProtosIoOperation operation,
            ProtosObjectValue exactError) {
        return recordCPrimeSourceFailure(operation, exactError);
    }

    /** Defensive driver-failure lane: preserve the exact Protos Error and terminalize. */
    public void cPrimeDriverFailedForRuntime(
            ProtosIoOperation operation,
            ProtosObjectValue error) {
        boolean needsInput = recordCPrimeSourceFailure(operation, error);
        if (needsInput) {
            throw new IllegalStateException(
                    "TextReader C-prime failure unexpectedly requested additional input");
        }
    }

    /** The synthetic root may only return normally after the outer operation is terminal. */
    public void unexpectedCPrimeCompletionForRuntime(ProtosIoOperation operation) {
        Objects.requireNonNull(operation, "operation");
        Request request;
        synchronized (this) {
            request = active;
            if (request == null || request.operation != operation || request.cPrimePlan == null) {
                return;
            }
        }
        failAndFinish(request, ioError(request.activation), false, true);
    }

    private boolean recordCPrimeSourceFailure(
            ProtosIoOperation operation,
            ProtosObjectValue exactError) {
        Objects.requireNonNull(operation, "operation");
        Request request;
        boolean terminal;
        synchronized (this) {
            request = active;
            if (request == null
                    || request.operation != operation
                    || request.cPrimePlan == null
                    || queue.peekFirst() != request) {
                return false;
            }
            terminal = operation.terminal();
            if (!terminal) {
                request.lowerFailure =
                        exactError != null ? exactError : ioError(request.activation);
            }
        }
        if (terminal) {
            finishQueueRequest(request);
            return false;
        }
        return advanceUntilInputOrTerminal(request);
    }

    private void cPrimeLowerTerminalObserved(
            Request request,
            ProtosFutureValue lower) {
        boolean applyLate;
        synchronized (this) {
            if (request.lower != lower || request.lowerObserver == null) {
                return;
            }
            applyLate = request.operation.terminal();
        }
        if (applyLate) {
            consumeLowerForCPrimeRuntime(request.operation, lower);
        }
    }

    private void startLowerRead(Request request) {
        synchronized (this) {
            if (!isActiveLocked(request) || request.lower != null || request.operation.terminal()) {
                return;
            }
        }

        final Object result;
        try {
            result =
                    ProtosInvocation.invokeMessage(
                            source,
                            "read",
                            List.of(new ProtosIntegerValue(BigInteger.valueOf(SOURCE_READ_AHEAD))),
                            request.activation);
        } catch (ProtosSignalException signaled) {
            synchronized (this) { request.lowerFailure = signaled.error(); }
            drive(request);
            return;
        } catch (RuntimeException failure) {
            synchronized (this) { request.lowerFailure = ioError(request.activation); }
            drive(request);
            return;
        }

        if (!(result instanceof ProtosFutureValue lower)) {
            synchronized (this) { request.lowerFailure = ioError(request.activation); }
            drive(request);
            return;
        }

        synchronized (this) {
            if (!isActiveLocked(request)) {
                lower.cancelRequest();
                return;
            }
            request.lower = lower;
            if (request.operation.terminal()) lower.cancelRequest();
        }
        lower.observe(ignored -> lowerTerminal(request, lower));
    }

    private void lowerTerminal(Request request, ProtosFutureValue lower) {
        ProtosFutureValue.State state = lower.state();
        Object resolved = state == ProtosFutureValue.State.RESOLVED
                ? lower.resolvedValue().orElseThrow()
                : null;
        ProtosObjectValue lowerError = state == ProtosFutureValue.State.FAILED
                ? lower.failedError().orElseThrow()
                : null;

        boolean invalidResolved = false;
        byte[] data = null;
        boolean eof = false;
        if (state == ProtosFutureValue.State.RESOLVED) {
            if (resolved == ProtosNullValue.INSTANCE) eof = true;
            else if (resolved instanceof ProtosBytesValue bytes) {
                data = snapshotBytes(bytes);
                invalidResolved = data == null || data.length == 0;
            } else invalidResolved = true;
        }

        boolean operationAlreadyTerminal;
        boolean closing;
        synchronized (this) {
            if (request.lower != lower) return;
            request.lower = null;
            operationAlreadyTerminal = request.operation.terminal();
            closing = lifecycle.state() != ProtosIoLifecycle.State.OPEN;

            if (operationAlreadyTerminal && closing) {
                // Close may discard uncommitted adapter read-ahead.
            } else if (operationAlreadyTerminal) {
                switch (state) {
                    case RESOLVED -> {
                        if (invalidResolved) {
                            if (deferredError == null) deferredError = ioError(request.activation);
                        } else if (eof) sourceEof = true;
                        else appendRetained(data);
                    }
                    case FAILED -> {
                        if (deferredError == null) deferredError = lowerError;
                    }
                    case CANCELLED, PENDING -> { }
                }
            } else {
                switch (state) {
                    case RESOLVED -> {
                        if (invalidResolved) request.lowerFailure = ioError(request.activation);
                        else if (eof) sourceEof = true;
                        else appendRetained(data);
                    }
                    case FAILED -> request.lowerFailure = lowerError;
                    case CANCELLED -> { }
                    case PENDING -> { return; }
                }
            }
        }

        if (!operationAlreadyTerminal && state == ProtosFutureValue.State.CANCELLED) {
            request.operation.requestCancellation();
        }
        if (operationAlreadyTerminal || request.operation.terminal()) finishQueueRequest(request);
        else drive(request);
    }

    private void cancel(Request request) {
        ProtosFutureValue lower = null;
        boolean removed = false;
        synchronized (this) {
            if (active == request) lower = request.lower;
            else if (queue.remove(request)) removed = true;
        }
        if (lower != null) {
            lower.cancelRequest();
            if (request.cPrimePlan != null
                    && request.operation.terminal()
                    && !lower.isPending()) {
                consumeLowerForCPrimeRuntime(request.operation, lower);
            }
        }
        if (removed) {
            pump();
        } else if (lower == null) {
            if (request.cPrimePlan != null && request.operation.terminal()) {
                finishQueueRequest(request);
            } else {
                drive(request);
            }
        }
    }

    private void finishCancelledIfNoLower(Request request) {
        synchronized (this) {
            if (request.lower != null) {
                request.lower.cancelRequest();
                return;
            }
        }
        finishQueueRequest(request);
    }

    private void finishQueueRequest(Request request) {
        boolean next = false;
        synchronized (this) {
            if (active == request) {
                if (queue.peekFirst() == request) queue.removeFirst();
                else queue.remove(request);
                active = null;
                next = true;
            } else queue.remove(request);
        }
        if (next) pump();
    }

    private boolean isActive(Request request) {
        synchronized (this) { return isActiveLocked(request); }
    }

    private boolean isActiveLocked(Request request) {
        return active == request && queue.peekFirst() == request;
    }

    private void release(ProtosIoLifecycle.ReleaseCompletion completion) {
        if (!owning) {
            completion.succeeded();
            return;
        }
        ProtosActivation activation;
        synchronized (this) { activation = closeActivation; }
        if (activation == null) {
            throw new IllegalStateException("owning TextReader release started without a close activation");
        }

        final Object result;
        try {
            result = ProtosInvocation.invokeMessage(source, "close", List.of(), activation);
        } catch (ProtosSignalException signaled) {
            completion.failed(signaled.error());
            return;
        } catch (RuntimeException failure) {
            completion.failed(ioError(activation));
            return;
        }
        if (!(result instanceof ProtosFutureValue lower)) {
            completion.failed(ioError(activation));
            return;
        }
        lower.observe(
                terminal -> {
                    switch (terminal.state()) {
                        case RESOLVED -> completion.succeeded();
                        case FAILED -> completion.failed(terminal.failedError().orElseThrow());
                        case CANCELLED -> completion.failed(ioError(activation));
                        case PENDING -> { }
                    }
                });
    }

    private ProtosObjectValue ioError(ProtosActivation activation) {
        return ProtosCoreErrors.newOccurrence(activation, ProtosCoreErrors.StandardError.I_O_ERROR);
    }

    private void appendRetained(byte[] bytes) {
        byte[] combined = Arrays.copyOf(retained, retained.length + bytes.length);
        System.arraycopy(bytes, 0, combined, retained.length, bytes.length);
        retained = combined;
    }

    private void consumeRetained(int count) {
        if (count < 0 || count > retained.length) {
            throw new IllegalStateException("invalid TextReader retained-byte consumption");
        }
        retained = Arrays.copyOfRange(retained, count, retained.length);
    }

    private byte[] snapshotBytes(ProtosBytesValue bytes) {
        List<Object> values = bytes.indexedSnapshot();
        byte[] result = new byte[values.size()];
        for (int index = 0; index < values.size(); index++) {
            BigInteger value;
            Object element = values.get(index);
            if (element instanceof ProtosIntegerValue integer) value = integer.value();
            else if (element instanceof ProtosFixedIntegerValue integer) value = integer.value();
            else return null;
            if (value.signum() < 0 || value.compareTo(BigInteger.valueOf(255)) > 0) return null;
            result[index] = (byte) value.intValue();
        }
        return result;
    }
}
