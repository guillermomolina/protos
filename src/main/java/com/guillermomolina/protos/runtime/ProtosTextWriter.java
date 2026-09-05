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
import java.util.ArrayDeque;
import java.util.List;
import java.util.Objects;

/** One ordered transactional encoder/output/lifecycle domain for a standard TextWriter. */
public final class ProtosTextWriter {
    private enum Kind { WRITE, FLUSH }

    private final ProtosObjectValue receiver;
    private final Object target;
    private final ProtosEncodingValue encoding;
    private final boolean targetFlushable;
    private final boolean owning;
    private final ProtosIoLifecycle lifecycle;
    private final ArrayDeque<Request> queue = new ArrayDeque<>();

    private ProtosEncodingValue.StreamingEncoder encoder;
    private Request active;
    private ProtosObjectValue outputError;
    private ProtosActivation closeActivation;

    public ProtosTextWriter(
            ProtosObjectValue receiver,
            Object target,
            ProtosEncodingValue encoding,
            boolean targetFlushable,
            boolean owning,
            ProtosActivation constructionActivation) {
        this.receiver = Objects.requireNonNull(receiver, "receiver");
        this.target = Objects.requireNonNull(target, "target");
        this.encoding = Objects.requireNonNull(encoding, "encoding");
        this.encoder = encoding.newStreamingEncoderForRuntime();
        this.targetFlushable = targetFlushable;
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

    public ProtosFutureValue writeText(
            ProtosActivation activation, String text, boolean appendLf) {
        Objects.requireNonNull(activation, "activation");
        Objects.requireNonNull(text, "text");
        ProtosIoOperation operation = lifecycle.beginOperation(activation);
        if (operation.terminal()) return operation.future();

        Request request =
                new Request(
                        activation,
                        operation,
                        Kind.WRITE,
                        appendLf ? text + "\n" : text);
        operation.onCancellation(() -> cancel(request));
        synchronized (this) { queue.addLast(request); }
        pump();
        return operation.future();
    }

    public ProtosFutureValue flush(ProtosActivation activation) {
        Objects.requireNonNull(activation, "activation");
        ProtosIoOperation operation = lifecycle.beginOperation(activation);
        if (operation.terminal()) return operation.future();
        Request request = new Request(activation, operation, Kind.FLUSH, null);
        operation.onCancellation(() -> cancel(request));
        synchronized (this) { queue.addLast(request); }
        pump();
        return operation.future();
    }

    public ProtosFutureValue close(ProtosActivation activation) {
        Objects.requireNonNull(activation, "activation");
        synchronized (this) {
            if (closeActivation == null) closeActivation = activation;
        }
        return lifecycle.close(activation);
    }

    public ProtosEncodingValue encodingForRuntime() { return encoding; }
    public Object targetForRuntime() { return target; }
    public boolean owningForRuntime() { return owning; }
    public boolean targetFlushableForRuntime() { return targetFlushable; }

    private static final class Request {
        final ProtosActivation activation;
        final ProtosIoOperation operation;
        final Kind kind;
        final String text;
        ProtosFutureValue lower;

        Request(
                ProtosActivation activation,
                ProtosIoOperation operation,
                Kind kind,
                String text) {
            this.activation = activation;
            this.operation = operation;
            this.kind = kind;
            this.text = text;
        }
    }

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
        if (next.kind == Kind.WRITE) driveWrite(next);
        else driveFlush(next);
    }

    private void driveWrite(Request request) {
        if (!isActive(request) || request.operation.terminal()) {
            finish(request);
            return;
        }

        ProtosObjectValue failure;
        ProtosEncodingValue.StreamingEncoder current;
        synchronized (this) {
            failure = outputError;
            current = encoder;
        }
        if (failure != null) {
            request.operation.fail(failure);
            finish(request);
            return;
        }

        /* Empty text is exactly zero encoder transition and zero target I/O. */
        if (request.text.isEmpty()) {
            if (request.operation.commit()) request.operation.resolve(receiver);
            finish(request);
            return;
        }

        final ProtosEncodingValue.EncodePreview encoded;
        try {
            encoded = current.encode(request.text);
        } catch (ProtosEncodingValue.ConversionFailure failureToEncode) {
            request.operation.fail(
                    ProtosCoreErrors.newOccurrence(
                            request.activation, ProtosCoreErrors.StandardError.ENCODING_ERROR));
            finish(request);
            return;
        }

        if (!request.operation.commit()) {
            finish(request);
            return;
        }
        synchronized (this) { encoder = encoded.nextEncoder(); }

        byte[] bytes = encoded.bytes();
        if (bytes.length == 0) {
            request.operation.resolve(receiver);
            finish(request);
            return;
        }

        ProtosFutureValue lower =
                invokeFuture(
                        target,
                        "write",
                        List.of(bytes(bytes, request.activation)),
                        request.activation);
        if (lower == null) {
            failCommittedOutput(request, ioError(request.activation));
            return;
        }
        synchronized (this) { request.lower = lower; }
        lower.observe(terminal -> lowerWriteTerminal(request, lower, terminal));
    }

    private void lowerWriteTerminal(
            Request request, ProtosFutureValue lower, ProtosFutureValue terminal) {
        synchronized (this) {
            if (request.lower != lower) return;
            request.lower = null;
        }
        switch (terminal.state()) {
            case RESOLVED -> {
                request.operation.resolve(receiver);
                finish(request);
            }
            case FAILED ->
                    failCommittedOutput(
                            request,
                            terminal.failedError().orElseGet(() -> ioError(request.activation)));
            case CANCELLED -> failCommittedOutput(request, ioError(request.activation));
            case PENDING -> { }
        }
    }

    private void driveFlush(Request request) {
        if (!isActive(request) || request.operation.terminal()) {
            finish(request);
            return;
        }
        ProtosObjectValue failure;
        synchronized (this) { failure = outputError; }
        if (failure != null) {
            request.operation.fail(failure);
            finish(request);
            return;
        }
        if (!request.operation.commit()) {
            finish(request);
            return;
        }
        if (!targetFlushable) {
            request.operation.resolve(receiver);
            finish(request);
            return;
        }

        ProtosFutureValue lower = invokeFuture(target, "flush", List.of(), request.activation);
        if (lower == null) {
            failCommittedOutput(request, ioError(request.activation));
            return;
        }
        synchronized (this) { request.lower = lower; }
        lower.observe(terminal -> lowerFlushTerminal(request, lower, terminal));
    }

    private void lowerFlushTerminal(
            Request request, ProtosFutureValue lower, ProtosFutureValue terminal) {
        synchronized (this) {
            if (request.lower != lower) return;
            request.lower = null;
        }
        switch (terminal.state()) {
            case RESOLVED -> {
                request.operation.resolve(receiver);
                finish(request);
            }
            case FAILED ->
                    failCommittedOutput(
                            request,
                            terminal.failedError().orElseGet(() -> ioError(request.activation)));
            case CANCELLED -> failCommittedOutput(request, ioError(request.activation));
            case PENDING -> { }
        }
    }

    private void failCommittedOutput(Request request, ProtosObjectValue error) {
        synchronized (this) {
            if (outputError == null) outputError = error;
            error = outputError;
        }
        request.operation.fail(error);
        finish(request);
    }

    private void cancel(Request request) {
        boolean removed = false;
        synchronized (this) {
            if (active != request) removed = queue.remove(request);
        }
        if (removed) pump();
        else if (request.operation.terminal()) finish(request);
    }

    private void finish(Request request) {
        boolean next = false;
        synchronized (this) {
            queue.remove(request);
            if (active == request) {
                active = null;
                next = true;
            }
        }
        if (next) pump();
    }

    private boolean isActive(Request request) {
        synchronized (this) { return active == request && queue.peekFirst() == request; }
    }

    /** Finalize committed encoder state, propagate final bytes, then release an explicitly owned target. */
    private void release(ProtosIoLifecycle.ReleaseCompletion completion) {
        ProtosObjectValue primary;
        ProtosActivation activation;
        ProtosEncodingValue.StreamingEncoder current;
        synchronized (this) {
            primary = outputError;
            activation = closeActivation;
            current = encoder;
        }
        if (activation == null) {
            throw new IllegalStateException("TextWriter release started without close activation");
        }

        if (primary != null) {
            finishRelease(primary, completion, activation);
            return;
        }

        final ProtosEncodingValue.EncodePreview finalization;
        try {
            finalization = current.finish();
        } catch (ProtosEncodingValue.ConversionFailure failure) {
            finishRelease(
                    ProtosCoreErrors.newOccurrence(
                            activation, ProtosCoreErrors.StandardError.ENCODING_ERROR),
                    completion,
                    activation);
            return;
        }
        synchronized (this) { encoder = finalization.nextEncoder(); }

        byte[] finalBytes = finalization.bytes();
        if (finalBytes.length == 0) {
            finishRelease(null, completion, activation);
            return;
        }

        ProtosFutureValue lower =
                invokeFuture(
                        target,
                        "write",
                        List.of(bytes(finalBytes, activation)),
                        activation);
        if (lower == null) {
            finishRelease(ioError(activation), completion, activation);
            return;
        }
        lower.observe(
                terminal -> {
                    switch (terminal.state()) {
                        case RESOLVED -> finishRelease(null, completion, activation);
                        case FAILED ->
                                finishRelease(
                                        terminal.failedError().orElseGet(() -> ioError(activation)),
                                        completion,
                                        activation);
                        case CANCELLED -> finishRelease(ioError(activation), completion, activation);
                        case PENDING -> { }
                    }
                });
    }

    private void finishRelease(
            ProtosObjectValue primary,
            ProtosIoLifecycle.ReleaseCompletion completion,
            ProtosActivation activation) {
        if (!owning) {
            if (primary == null) completion.succeeded();
            else completion.failed(primary);
            return;
        }

        ProtosFutureValue targetClose = invokeFuture(target, "close", List.of(), activation);
        if (targetClose == null) {
            completion.failed(primary != null ? primary : ioError(activation));
            return;
        }
        ProtosObjectValue primaryFailure = primary;
        targetClose.observe(
                terminal -> {
                    switch (terminal.state()) {
                        case RESOLVED -> {
                            if (primaryFailure == null) completion.succeeded();
                            else completion.failed(primaryFailure);
                        }
                        case FAILED -> {
                            ProtosObjectValue targetFailure =
                                    terminal.failedError().orElseGet(() -> ioError(activation));
                            completion.failed(
                                    primaryFailure != null ? primaryFailure : targetFailure);
                        }
                        case CANCELLED ->
                                completion.failed(
                                        primaryFailure != null
                                                ? primaryFailure
                                                : ioError(activation));
                        case PENDING -> { }
                    }
                });
    }

    private ProtosFutureValue invokeFuture(
            Object object,
            String selector,
            List<?> arguments,
            ProtosActivation activation) {
        try {
            Object result = ProtosInvocation.invokeMessage(object, selector, arguments, activation);
            return result instanceof ProtosFutureValue future ? future : null;
        } catch (RuntimeException failure) {
            return null;
        }
    }

    private ProtosBytesValue bytes(byte[] encoded, ProtosActivation activation) {
        ProtosBytesValue bytes =
                new ProtosBytesValue(
                        activation.prelude().orElseThrow().bytesPrototypeForRuntime());
        for (byte value : encoded) {
            bytes.indexedAdd(new ProtosIntegerValue(BigInteger.valueOf(value & 0xff)));
        }
        return bytes;
    }

    private ProtosObjectValue ioError(ProtosActivation activation) {
        return ProtosCoreErrors.newOccurrence(activation, ProtosCoreErrors.StandardError.I_O_ERROR);
    }
}
