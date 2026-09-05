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
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Ordered streaming state for one standard TextReader wrapper.
 *
 * <p>I015-B deliberately owns only the four portable Encoding descriptors. Their streaming
 * decoding is transactional over retained source octets: an operation removes bytes only after its
 * own {@link ProtosIoOperation#commit()} wins. A successfully cancelled read therefore consumes no
 * logical text even when an already-started lower byte read later returns data; that read-ahead is
 * retained for the next ordered operation.
 *
 * <p>The portable UTF encodings are stateless apart from initial matching-BOM handling, so a fresh
 * strict host decoder can inspect each retained snapshot without making converter-call boundaries
 * observable. I015-E remains responsible for extending this same contract to explicitly
 * host-provided Encoding descriptors whose incremental state contract is not represented by I015-A.
 */
public final class ProtosTextReader {
    private static final int SOURCE_READ_AHEAD = 8192;

    private final ProtosObjectValue receiver;
    private final Object source;
    private final ProtosEncodingValue encoding;
    private final ProtosEncodingValue.PortableKind portableKind;
    private final boolean owning;
    private final ProtosIoLifecycle lifecycle;

    private final ArrayDeque<Request> queue = new ArrayDeque<>();
    private Request active;
    private byte[] retained = new byte[0];
    private boolean sourceEof;
    private boolean initialEncodingSetupCommitted;
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
        this.portableKind = encoding.portableKindForRuntime();
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
        return enqueueTextRead(
                Objects.requireNonNull(activation, "activation"),
                RequestKind.READ_TEXT,
                null);
    }

    public ProtosFutureValue readLine(
            ProtosActivation activation, BigInteger maxBytes) {
        Objects.requireNonNull(activation, "activation");
        if (maxBytes != null && maxBytes.signum() <= 0) {
            throw new IllegalArgumentException("readLine maxBytes must be positive");
        }
        return enqueueTextRead(activation, RequestKind.READ_LINE, maxBytes);
    }

    private ProtosFutureValue enqueueTextRead(
            ProtosActivation activation,
            RequestKind kind,
            BigInteger maxBytes) {
        ProtosIoOperation operation = lifecycle.beginOperation(activation);
        ProtosFutureValue future = operation.future();
        if (operation.terminal()) {
            return future;
        }

        Request request =
                new Request(activation, operation, kind, maxBytes);
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
            if (closeActivation == null) {
                closeActivation = activation;
            }
        }
        return lifecycle.close(activation);
    }

    public ProtosEncodingValue encodingForRuntime() {
        return encoding;
    }

    public Object sourceForRuntime() {
        return source;
    }

    public boolean owningForRuntime() {
        return owning;
    }

    private enum RequestKind {
        READ_TEXT,
        READ_LINE
    }

    private static final class Request {
        final ProtosActivation activation;
        final ProtosIoOperation operation;
        final RequestKind kind;
        final BigInteger maxBytes;
        ProtosFutureValue lower;
        ProtosObjectValue lowerFailure;
        boolean driving;
        boolean driveRequested;

        Request(
                ProtosActivation activation,
                ProtosIoOperation operation,
                RequestKind kind,
                BigInteger maxBytes) {
            this.activation = activation;
            this.operation = operation;
            this.kind = Objects.requireNonNull(kind, "kind");
            this.maxBytes = maxBytes;
        }
    }

    private enum DecodeKind {
        TEXT,
        NEED_INPUT,
        EOF,
        ERROR
    }

    private record DecodeResult(
            DecodeKind kind,
            String text,
            int consumed,
            boolean commitsInitialSetup,
            boolean deferEncodingError) {}

    private enum LineKind {
        LINE,
        NEED_INPUT,
        EOF,
        ERROR,
        TOO_LONG
    }

    private record LineResult(
            LineKind kind,
            String text,
            int consumed,
            boolean commitsInitialSetup,
            boolean pendingLfAfterCr) {}

    private enum ScalarKind {
        SCALAR,
        NEED_INPUT,
        ERROR
    }

    private record ScalarResult(
            ScalarKind kind, String text, int consumed) {}

    private enum FoldKind {
        READY,
        NEED_INPUT,
        ERROR
    }

    private void pump() {
        Request next = null;
        synchronized (this) {
            if (active != null) {
                return;
            }
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
        if (next != null) {
            drive(next);
        }
    }

    /**
     * Serializes re-entrant callbacks from already-terminal lower Futures without recursive stack
     * growth. A callback that arrives while this request is already driving merely sets another
     * drive request.
     */
    private void drive(Request request) {
        synchronized (request) {
            request.driveRequested = true;
            if (request.driving) {
                return;
            }
            request.driving = true;
        }

        while (true) {
            synchronized (request) {
                request.driveRequested = false;
            }
            driveOnce(request);

            synchronized (request) {
                if (request.driveRequested) {
                    continue;
                }
                request.driving = false;
                if (!request.driveRequested) {
                    return;
                }
                request.driving = true;
            }
        }
    }

    private void driveOnce(Request request) {
        if (!isActive(request)) {
            return;
        }

        if (request.operation.terminal()) {
            ProtosFutureValue lower;
            ProtosIoLifecycle.State lifecycleState = lifecycle.state();
            synchronized (this) {
                lower = request.lower;
            }
            if (lower == null || lifecycleState != ProtosIoLifecycle.State.OPEN) {
                finishQueueRequest(request);
            }
            return;
        }

        ProtosObjectValue permanent;
        ProtosObjectValue deferred;
        ProtosObjectValue sourceFailure;
        byte[] snapshot;
        boolean eof;
        boolean initialSetup;
        boolean foldLf;
        synchronized (this) {
            permanent = failedError;
            deferred = deferredError;
            sourceFailure = request.lowerFailure;
            snapshot = retained.clone();
            eof = sourceEof;
            initialSetup = initialEncodingSetupCommitted;
            foldLf = pendingLfAfterCr;
        }

        if (permanent != null) {
            failAndFinish(request, permanent, false, false);
            return;
        }

        /*
         * CR completes a line immediately. If its possible following LF was not yet available
         * when that line committed, resolve the CRLF fold before exposing the next logical text
         * to any later readText/readLine request. Consuming that LF belongs to the already-
         * committed previous terminator, not to this operation.
         */
        if (foldLf) {
            if (deferred != null || sourceFailure != null) {
                synchronized (this) {
                    pendingLfAfterCr = false;
                }
            } else {
                FoldKind folded = resolvePendingLfAfterCr(snapshot, eof);
                if (folded == FoldKind.NEED_INPUT) {
                    startLowerRead(request);
                    return;
                }
                if (folded == FoldKind.ERROR) {
                    failAndFinish(
                            request,
                            ProtosCoreErrors.newOccurrence(
                                    request.activation,
                                    ProtosCoreErrors.StandardError.ENCODING_ERROR),
                            false,
                            true);
                    return;
                }
                synchronized (this) {
                    snapshot = retained.clone();
                    eof = sourceEof;
                    initialSetup = initialEncodingSetupCommitted;
                }
            }
        }

        if (deferred != null) {
            failAndFinish(request, deferred, true, false);
            return;
        }

        if (sourceFailure != null) {
            failAndFinish(request, sourceFailure, false, true);
            return;
        }

        if (request.kind == RequestKind.READ_TEXT) {
            DecodeResult decoded = decode(snapshot, eof, initialSetup);
            switch (decoded.kind()) {
                case TEXT -> completeText(request, decoded);
                case EOF -> completeEof(request, decoded);
                case ERROR ->
                        failAndFinish(
                                request,
                                ProtosCoreErrors.newOccurrence(
                                        request.activation,
                                        ProtosCoreErrors.StandardError.ENCODING_ERROR),
                                false,
                                true);
                case NEED_INPUT -> startLowerRead(request);
            }
            return;
        }

        LineResult line =
                scanLine(snapshot, eof, initialSetup, request.maxBytes);
        switch (line.kind()) {
            case LINE, EOF -> completeLine(request, line);
            case NEED_INPUT -> startLowerRead(request);
            case ERROR ->
                    failAndFinish(
                            request,
                            ProtosCoreErrors.newOccurrence(
                                    request.activation,
                                    ProtosCoreErrors.StandardError.ENCODING_ERROR),
                            false,
                            true);
            case TOO_LONG ->
                    failAndFinish(
                            request,
                            ProtosCoreErrors.newOccurrence(
                                    request.activation,
                                    ProtosCoreErrors.StandardError.LINE_TOO_LONG),
                            false,
                            true);
        }
    }

    private FoldKind resolvePendingLfAfterCr(byte[] bytes, boolean eof) {
        if (bytes.length == 0) {
            if (!eof) {
                return FoldKind.NEED_INPUT;
            }
            synchronized (this) {
                pendingLfAfterCr = false;
            }
            return FoldKind.READY;
        }

        ScalarResult next = decodeScalar(bytes, 0, eof);
        if (next.kind() == ScalarKind.NEED_INPUT) {
            return FoldKind.NEED_INPUT;
        }
        if (next.kind() == ScalarKind.ERROR) {
            synchronized (this) {
                pendingLfAfterCr = false;
            }
            return FoldKind.ERROR;
        }

        synchronized (this) {
            pendingLfAfterCr = false;
            if ("\n".equals(next.text())) {
                consumeRetained(next.consumed());
            }
        }
        return FoldKind.READY;
    }

    private LineResult scanLine(
            byte[] bytes,
            boolean eof,
            boolean initialSetupCommitted,
            BigInteger maxBytes) {
        int offset = 0;
        boolean setupCommit = false;

        if (!initialSetupCommitted
                && portableKind != ProtosEncodingValue.PortableKind.LATIN1) {
            byte[] bom = matchingBom();
            int common = commonPrefix(bytes, bom);
            if (common == bytes.length
                    && bytes.length < bom.length
                    && !eof) {
                return new LineResult(
                        LineKind.NEED_INPUT, null, 0, false, false);
            }
            if (bytes.length >= bom.length && common == bom.length) {
                offset = bom.length;
            }
            setupCommit = true;
        } else if (!initialSetupCommitted) {
            setupCommit = true;
        }

        StringBuilder line = new StringBuilder();
        BigInteger contentBytes = BigInteger.ZERO;

        while (true) {
            if (offset == bytes.length) {
                if (!eof) {
                    return new LineResult(
                            LineKind.NEED_INPUT, null, 0, false, false);
                }
                if (line.length() == 0) {
                    return new LineResult(
                            LineKind.EOF,
                            null,
                            offset,
                            setupCommit,
                            false);
                }
                return new LineResult(
                        LineKind.LINE,
                        line.toString(),
                        offset,
                        setupCommit,
                        false);
            }

            ScalarResult scalar = decodeScalar(bytes, offset, eof);
            if (scalar.kind() == ScalarKind.NEED_INPUT) {
                return new LineResult(
                        LineKind.NEED_INPUT, null, 0, false, false);
            }
            if (scalar.kind() == ScalarKind.ERROR) {
                return new LineResult(
                        LineKind.ERROR, null, 0, false, false);
            }

            if ("\n".equals(scalar.text())) {
                return new LineResult(
                        LineKind.LINE,
                        line.toString(),
                        offset + scalar.consumed(),
                        setupCommit,
                        false);
            }

            if ("\r".equals(scalar.text())) {
                int afterCr = offset + scalar.consumed();
                int consumed = afterCr;
                boolean pendingFold = false;

                if (afterCr < bytes.length) {
                    ScalarResult next = decodeScalar(bytes, afterCr, eof);
                    if (next.kind() == ScalarKind.SCALAR
                            && "\n".equals(next.text())) {
                        consumed += next.consumed();
                    } else if (next.kind() != ScalarKind.SCALAR) {
                        pendingFold = true;
                    }
                } else if (!eof) {
                    pendingFold = true;
                }

                return new LineResult(
                        LineKind.LINE,
                        line.toString(),
                        consumed,
                        setupCommit,
                        pendingFold);
            }

            contentBytes =
                    contentBytes.add(BigInteger.valueOf(scalar.consumed()));
            if (maxBytes != null
                    && contentBytes.compareTo(maxBytes) > 0) {
                return new LineResult(
                        LineKind.TOO_LONG, null, 0, false, false);
            }

            line.append(scalar.text());
            offset += scalar.consumed();
        }
    }

    private void completeLine(Request request, LineResult line) {
        if (!request.operation.commit()) {
            finishCancelledIfNoLower(request);
            return;
        }

        synchronized (this) {
            consumeRetained(line.consumed());
            if (line.commitsInitialSetup()) {
                initialEncodingSetupCommitted = true;
            }
            pendingLfAfterCr = line.pendingLfAfterCr();
        }

        if (line.kind() == LineKind.EOF) {
            request.operation.resolve(ProtosNullValue.INSTANCE);
        } else {
            request.operation.resolve(new ProtosStringValue(line.text()));
        }
        finishQueueRequest(request);
    }

    private DecodeResult decode(
            byte[] bytes, boolean eof, boolean initialSetupCommitted) {
        int skip = 0;
        boolean setupCommit = false;

        if (!initialSetupCommitted
                && portableKind != ProtosEncodingValue.PortableKind.LATIN1) {
            byte[] bom = matchingBom();
            int common = commonPrefix(bytes, bom);
            if (common == bytes.length
                    && bytes.length < bom.length
                    && !eof) {
                return new DecodeResult(
                        DecodeKind.NEED_INPUT, null, 0, false, false);
            }
            if (bytes.length >= bom.length && common == bom.length) {
                skip = bom.length;
            }
            setupCommit = true;
        } else if (!initialSetupCommitted) {
            setupCommit = true;
        }

        if (bytes.length == skip) {
            if (eof) {
                return new DecodeResult(
                        DecodeKind.EOF, null, skip, setupCommit, false);
            }
            return new DecodeResult(
                    DecodeKind.NEED_INPUT, null, 0, false, false);
        }

        CharsetDecoder decoder =
                charset()
                        .newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT);
        ByteBuffer input =
                ByteBuffer.wrap(bytes, skip, bytes.length - skip).slice();
        CharBuffer output =
                CharBuffer.allocate(
                        Math.max(4, (bytes.length - skip) * 2 + 2));

        CoderResult result = decoder.decode(input, output, eof);
        if (eof && result.isUnderflow()) {
            CoderResult flushed = decoder.flush(output);
            if (flushed.isError()) {
                result = flushed;
            }
        }

        output.flip();
        String text = output.toString();
        int consumed = skip + input.position();

        if (result.isError()) {
            if (!text.isEmpty()) {
                return new DecodeResult(
                        DecodeKind.TEXT,
                        text,
                        consumed,
                        setupCommit,
                        true);
            }
            return new DecodeResult(
                    DecodeKind.ERROR, null, 0, false, false);
        }

        if (!text.isEmpty()) {
            return new DecodeResult(
                    DecodeKind.TEXT,
                    text,
                    consumed,
                    setupCommit,
                    false);
        }

        if (eof) {
            if (input.hasRemaining()) {
                return new DecodeResult(
                        DecodeKind.ERROR, null, 0, false, false);
            }
            return new DecodeResult(
                    DecodeKind.EOF,
                    null,
                    consumed,
                    setupCommit,
                    false);
        }

        return new DecodeResult(
                DecodeKind.NEED_INPUT, null, 0, false, false);
    }

    private void completeText(Request request, DecodeResult decoded) {
        ProtosObjectValue deferred =
                decoded.deferEncodingError()
                        ? ProtosCoreErrors.newOccurrence(
                                request.activation,
                                ProtosCoreErrors.StandardError.ENCODING_ERROR)
                        : null;

        if (!request.operation.commit()) {
            finishCancelledIfNoLower(request);
            return;
        }

        synchronized (this) {
            consumeRetained(decoded.consumed());
            if (decoded.commitsInitialSetup()) {
                initialEncodingSetupCommitted = true;
            }
            if (deferred != null) {
                deferredError = deferred;
            }
        }

        request.operation.resolve(new ProtosStringValue(decoded.text()));
        finishQueueRequest(request);
    }

    private void completeEof(Request request, DecodeResult decoded) {
        if (!request.operation.commit()) {
            finishCancelledIfNoLower(request);
            return;
        }

        synchronized (this) {
            consumeRetained(decoded.consumed());
            if (decoded.commitsInitialSetup()) {
                initialEncodingSetupCommitted = true;
            }
        }

        request.operation.resolve(ProtosNullValue.INSTANCE);
        finishQueueRequest(request);
    }

    private void failAndFinish(
            Request request,
            ProtosObjectValue error,
            boolean consumeDeferred,
            boolean permanentOnWin) {
        boolean won = request.operation.fail(error);
        if (won) {
            synchronized (this) {
                if (consumeDeferred && deferredError == error) {
                    deferredError = null;
                }
                request.lowerFailure = null;
                if (permanentOnWin || consumeDeferred) {
                    failedError = error;
                }
            }
        }
        finishQueueRequest(request);
    }

    private void startLowerRead(Request request) {
        synchronized (this) {
            if (!isActiveLocked(request)
                    || request.lower != null
                    || request.operation.terminal()) {
                return;
            }
        }

        final Object result;
        try {
            result =
                    ProtosInvocation.invokeMessage(
                            source,
                            "read",
                            List.of(
                                    new ProtosIntegerValue(
                                            BigInteger.valueOf(SOURCE_READ_AHEAD))),
                            request.activation);
        } catch (ProtosSignalException signaled) {
            synchronized (this) {
                request.lowerFailure = signaled.error();
            }
            drive(request);
            return;
        } catch (RuntimeException protocolFailure) {
            synchronized (this) {
                request.lowerFailure = ioError(request.activation);
            }
            drive(request);
            return;
        }

        if (!(result instanceof ProtosFutureValue lower)) {
            synchronized (this) {
                request.lowerFailure = ioError(request.activation);
            }
            drive(request);
            return;
        }

        synchronized (this) {
            if (!isActiveLocked(request)) {
                lower.cancelRequest();
                return;
            }
            request.lower = lower;
            if (request.operation.terminal()) {
                lower.cancelRequest();
            }
        }

        lower.observe(ignored -> lowerTerminal(request, lower));
    }

    private void lowerTerminal(Request request, ProtosFutureValue lower) {
        ProtosFutureValue.State state;
        Object resolved = null;
        ProtosObjectValue lowerError = null;
        state = lower.state();
        if (state == ProtosFutureValue.State.RESOLVED) {
            resolved = lower.resolvedValue().orElseThrow();
        } else if (state == ProtosFutureValue.State.FAILED) {
            lowerError = lower.failedError().orElseThrow();
        }

        boolean operationAlreadyTerminal;
        boolean closing;
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

        synchronized (this) {
            if (request.lower != lower) {
                return;
            }
            request.lower = null;
            operationAlreadyTerminal = request.operation.terminal();
            closing = lifecycle.state() != ProtosIoLifecycle.State.OPEN;

            if (operationAlreadyTerminal && closing) {
                // Adapter close cutover is allowed to discard uncommitted read-ahead.
            } else if (operationAlreadyTerminal) {
                // Successful explicit cancellation is zero logical consumption. Preserve whatever
                // the already-started source operation observed for the next ordered text read.
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
                        if (deferredError == null) {
                            deferredError = lowerError;
                        }
                    }
                    case CANCELLED, PENDING -> {
                        // No source progress to preserve.
                    }
                }
            } else {
                switch (state) {
                    case RESOLVED -> {
                        if (invalidResolved) {
                            request.lowerFailure = ioError(request.activation);
                        } else if (eof) {
                            sourceEof = true;
                        } else {
                            appendRetained(data);
                        }
                    }
                    case FAILED -> request.lowerFailure = lowerError;
                    case CANCELLED -> {
                        // Mirror cancellation only when the outer operation is still uncommitted.
                    }
                    case PENDING -> {
                        return;
                    }
                }
            }
        }

        if (!operationAlreadyTerminal
                && state == ProtosFutureValue.State.CANCELLED) {
            request.operation.requestCancellation();
        }

        if (operationAlreadyTerminal || request.operation.terminal()) {
            finishQueueRequest(request);
        } else {
            drive(request);
        }
    }

    private void cancel(Request request) {
        ProtosFutureValue lower = null;
        boolean queuedRemoved = false;
        synchronized (this) {
            if (active == request) {
                lower = request.lower;
            } else if (queue.remove(request)) {
                queuedRemoved = true;
            }
        }

        if (lower != null) {
            lower.cancelRequest();
        }
        if (queuedRemoved) {
            pump();
        } else if (lower == null) {
            drive(request);
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
        boolean pumpNext = false;
        synchronized (this) {
            if (active == request) {
                if (queue.peekFirst() == request) {
                    queue.removeFirst();
                } else {
                    queue.remove(request);
                }
                active = null;
                pumpNext = true;
            } else {
                queue.remove(request);
            }
        }
        if (pumpNext) {
            pump();
        }
    }

    private boolean isActive(Request request) {
        synchronized (this) {
            return isActiveLocked(request);
        }
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
        synchronized (this) {
            activation = closeActivation;
        }
        if (activation == null) {
            throw new IllegalStateException(
                    "owning TextReader release started without a close activation");
        }

        final Object result;
        try {
            result =
                    ProtosInvocation.invokeMessage(
                            source, "close", List.of(), activation);
        } catch (ProtosSignalException signaled) {
            completion.failed(signaled.error());
            return;
        } catch (RuntimeException protocolFailure) {
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
                        case FAILED ->
                                completion.failed(
                                        terminal.failedError().orElseThrow());
                        case CANCELLED ->
                                completion.failed(ioError(activation));
                        case PENDING -> {
                            // Observer is called again only at terminal state.
                        }
                    }
                });
    }

    private ProtosObjectValue ioError(ProtosActivation activation) {
        return ProtosCoreErrors.newOccurrence(
                activation, ProtosCoreErrors.StandardError.I_O_ERROR);
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
            if (element instanceof ProtosIntegerValue integer) {
                value = integer.value();
            } else if (element instanceof ProtosFixedIntegerValue integer) {
                value = integer.value();
            } else {
                return null;
            }
            if (value.signum() < 0
                    || value.compareTo(BigInteger.valueOf(255)) > 0) {
                return null;
            }
            result[index] = (byte) value.intValue();
        }
        return result;
    }

    private ScalarResult decodeScalar(
            byte[] bytes, int offset, boolean eof) {
        int remaining = bytes.length - offset;
        if (remaining <= 0) {
            return eof
                    ? new ScalarResult(ScalarKind.ERROR, null, 0)
                    : new ScalarResult(ScalarKind.NEED_INPUT, null, 0);
        }

        return switch (portableKind) {
            case LATIN1 ->
                    scalar(Character.toString((char) unsigned(bytes[offset])), 1);
            case UTF16LE -> decodeUtf16Scalar(bytes, offset, eof, true);
            case UTF16BE -> decodeUtf16Scalar(bytes, offset, eof, false);
            case UTF8 -> decodeUtf8Scalar(bytes, offset, eof);
        };
    }

    private ScalarResult decodeUtf16Scalar(
            byte[] bytes, int offset, boolean eof, boolean littleEndian) {
        int remaining = bytes.length - offset;
        if (remaining < 2) {
            return needInputOrError(eof);
        }

        int first =
                littleEndian
                        ? unsigned(bytes[offset])
                                | (unsigned(bytes[offset + 1]) << 8)
                        : (unsigned(bytes[offset]) << 8)
                                | unsigned(bytes[offset + 1]);

        if (first >= 0xd800 && first <= 0xdbff) {
            if (remaining < 4) {
                return needInputOrError(eof);
            }
            int second =
                    littleEndian
                            ? unsigned(bytes[offset + 2])
                                    | (unsigned(bytes[offset + 3]) << 8)
                            : (unsigned(bytes[offset + 2]) << 8)
                                    | unsigned(bytes[offset + 3]);
            if (second < 0xdc00 || second > 0xdfff) {
                return new ScalarResult(ScalarKind.ERROR, null, 0);
            }
            int codePoint =
                    0x10000
                            + ((first - 0xd800) << 10)
                            + (second - 0xdc00);
            return scalar(new String(Character.toChars(codePoint)), 4);
        }

        if (first >= 0xdc00 && first <= 0xdfff) {
            return new ScalarResult(ScalarKind.ERROR, null, 0);
        }
        return scalar(Character.toString((char) first), 2);
    }

    private ScalarResult decodeUtf8Scalar(
            byte[] bytes, int offset, boolean eof) {
        int remaining = bytes.length - offset;
        int b0 = unsigned(bytes[offset]);

        if (b0 <= 0x7f) {
            return scalar(Character.toString((char) b0), 1);
        }

        if (b0 >= 0xc2 && b0 <= 0xdf) {
            if (remaining < 2) {
                return needInputOrError(eof);
            }
            int b1 = unsigned(bytes[offset + 1]);
            if (!continuation(b1)) {
                return new ScalarResult(ScalarKind.ERROR, null, 0);
            }
            int cp = ((b0 & 0x1f) << 6) | (b1 & 0x3f);
            return scalar(new String(Character.toChars(cp)), 2);
        }

        if (b0 >= 0xe0 && b0 <= 0xef) {
            if (remaining < 2) {
                return needInputOrError(eof);
            }
            int b1 = unsigned(bytes[offset + 1]);
            boolean secondValid =
                    b0 == 0xe0
                            ? b1 >= 0xa0 && b1 <= 0xbf
                            : b0 == 0xed
                                    ? b1 >= 0x80 && b1 <= 0x9f
                                    : continuation(b1);
            if (!secondValid) {
                return new ScalarResult(ScalarKind.ERROR, null, 0);
            }
            if (remaining < 3) {
                return needInputOrError(eof);
            }
            int b2 = unsigned(bytes[offset + 2]);
            if (!continuation(b2)) {
                return new ScalarResult(ScalarKind.ERROR, null, 0);
            }
            int cp =
                    ((b0 & 0x0f) << 12)
                            | ((b1 & 0x3f) << 6)
                            | (b2 & 0x3f);
            return scalar(new String(Character.toChars(cp)), 3);
        }

        if (b0 >= 0xf0 && b0 <= 0xf4) {
            if (remaining < 2) {
                return needInputOrError(eof);
            }
            int b1 = unsigned(bytes[offset + 1]);
            boolean secondValid =
                    b0 == 0xf0
                            ? b1 >= 0x90 && b1 <= 0xbf
                            : b0 == 0xf4
                                    ? b1 >= 0x80 && b1 <= 0x8f
                                    : continuation(b1);
            if (!secondValid) {
                return new ScalarResult(ScalarKind.ERROR, null, 0);
            }
            if (remaining < 3) {
                return needInputOrError(eof);
            }
            int b2 = unsigned(bytes[offset + 2]);
            if (!continuation(b2)) {
                return new ScalarResult(ScalarKind.ERROR, null, 0);
            }
            if (remaining < 4) {
                return needInputOrError(eof);
            }
            int b3 = unsigned(bytes[offset + 3]);
            if (!continuation(b3)) {
                return new ScalarResult(ScalarKind.ERROR, null, 0);
            }
            int cp =
                    ((b0 & 0x07) << 18)
                            | ((b1 & 0x3f) << 12)
                            | ((b2 & 0x3f) << 6)
                            | (b3 & 0x3f);
            return scalar(new String(Character.toChars(cp)), 4);
        }

        return new ScalarResult(ScalarKind.ERROR, null, 0);
    }

    private static ScalarResult scalar(String text, int consumed) {
        return new ScalarResult(ScalarKind.SCALAR, text, consumed);
    }

    private static ScalarResult needInputOrError(boolean eof) {
        return eof
                ? new ScalarResult(ScalarKind.ERROR, null, 0)
                : new ScalarResult(ScalarKind.NEED_INPUT, null, 0);
    }

    private static int unsigned(byte value) {
        return value & 0xff;
    }

    private static boolean continuation(int value) {
        return value >= 0x80 && value <= 0xbf;
    }

    private Charset charset() {
        return switch (portableKind) {
            case UTF8 -> StandardCharsets.UTF_8;
            case UTF16LE -> StandardCharsets.UTF_16LE;
            case UTF16BE -> StandardCharsets.UTF_16BE;
            case LATIN1 -> StandardCharsets.ISO_8859_1;
        };
    }

    private byte[] matchingBom() {
        return switch (portableKind) {
            case UTF8 -> new byte[] {(byte) 0xef, (byte) 0xbb, (byte) 0xbf};
            case UTF16LE -> new byte[] {(byte) 0xff, (byte) 0xfe};
            case UTF16BE -> new byte[] {(byte) 0xfe, (byte) 0xff};
            case LATIN1 -> new byte[0];
        };
    }

    private static int commonPrefix(byte[] bytes, byte[] prefix) {
        int limit = Math.min(bytes.length, prefix.length);
        int index = 0;
        while (index < limit && bytes[index] == prefix[index]) {
            index++;
        }
        return index;
    }
}
