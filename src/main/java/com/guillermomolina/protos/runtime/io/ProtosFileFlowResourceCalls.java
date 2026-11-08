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

import java.util.Objects;

/**
 * Resource-backed operations of one {@link ProtosFileFlow}: read, write, seekToEnd, size,
 * truncate and sync. Each starts the backend call for an already ordered request and settles its
 * completion.
 *
 * <p>Ordering, the logical cursor, write retention and the lifecycle stay in {@link
 * ProtosFileFlow}; this class only reads and updates them through the flow. A completion arriving
 * on a marked {@link ProtosHostCompletionThread} (the I156 Filesystem grant driver) is delivered
 * as a targeted completion of the request's Future inside the owning Actor; any other completion
 * is published directly, as before.
 */
final class ProtosFileFlowResourceCalls {
    private final ProtosFileFlow flow;

    ProtosFileFlowResourceCalls(ProtosFileFlow flow) {
        this.flow = Objects.requireNonNull(flow, "flow");
    }

    void startRead(ProtosFileFlow.Request request) {
        Object start = flow.currentPosition();
        request.startPosition = start;
        if (!ProtosNumericValueSupport.isIntegerInLongRange(start)) {
            // No host file has a byte at a position beyond the signed-64 range.
            flow.failIo(request);
            flow.finish(request);
            return;
        }
        try {
            flow.setCancellation(
                    request,
                    ((ProtosFileFlow.ReadableResource) flow.resource)
                            .readAt(
                                    ProtosNumericValueSupport.exactLong(start),
                                    ProtosNumericValueSupport.exactInt(request.number),
                                    new ProtosFileFlow.ReadCompletion() {
                                        @Override
                                        public void data(byte[] bytes) {
                                            // A deferred publication snapshots the backend buffer
                                            // now: later reuse of that buffer cannot change it.
                                            byte[] published =
                                                    bytes != null
                                                                    && ProtosHostCompletionThread
                                                                            .isCurrent()
                                                            ? bytes.clone()
                                                            : bytes;
                                            settle(
                                                    request,
                                                    () -> completeReadData(request, published));
                                        }

                                        @Override
                                        public void eof() {
                                            settle(
                                                    request,
                                                    () -> {
                                                        if (request.operation.commit()) {
                                                            request.operation.resolve(
                                                                    ProtosNullValue.INSTANCE);
                                                        }
                                                        flow.finish(request);
                                                    });
                                        }

                                        @Override
                                        public void failed() {
                                            settleFailure(request);
                                        }
                                    }));
        } catch (RuntimeException backendFailure) {
            flow.failIo(request);
            flow.finish(request);
        }
    }

    private void completeReadData(ProtosFileFlow.Request request, byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        if (bytes.length == 0 || bytes.length > ProtosNumericValueSupport.exactInt(request.number)) {
            flow.failIo(request);
            flow.finish(request);
            return;
        }
        if (!request.operation.commit()) {
            flow.finish(request);
            return;
        }

        ProtosBytesValue result = new ProtosBytesValue(flow.bytesPrototype);
        for (byte octet : bytes) {
            result.indexedAdd(ProtosNumericValueSupport.octet(octet & 0xff));
        }
        flow.setLogicalPosition(flow.advanced(request.startPosition, bytes.length));
        request.operation.resolve(result);
        flow.finish(request);
    }

    void startWrite(ProtosFileFlow.Request request) {
        Object start = flow.currentPosition();
        request.startPosition = start;
        if (request.bytes.length == 0) {
            if (request.operation.commit()) {
                request.operation.resolve(flow.receiver);
            }
            flow.finish(request);
            return;
        }

        if (!ProtosNumericValueSupport.isIntegerInLongRange(start)) {
            // No contribution is possible beyond the signed-64 host range.
            flow.failIo(request);
            flow.finish(request);
            return;
        }
        try {
            flow.setCancellation(
                    request,
                    ((ProtosFileFlow.WritableResource) flow.resource)
                            .writeAt(
                                    ProtosNumericValueSupport.exactLong(start),
                                    request.bytes.clone(),
                                    new ProtosFileFlow.WriteCompletion() {
                                        @Override
                                        public boolean commitFirstContribution() {
                                            return request.operation.commit();
                                        }

                                        @Override
                                        public void succeeded() {
                                            settle(request, () -> completeWrite(request));
                                        }

                                        @Override
                                        public void failed(int contributedPrefix) {
                                            validateContributedPrefix(request, contributedPrefix);
                                            settle(
                                                    request,
                                                    () -> failWrite(request, contributedPrefix));
                                        }

                                        @Override
                                        public void failedWithUnknownProgress() {
                                            settle(request, () -> failWriteWithUnknownProgress(request));
                                        }
                                    }));
        } catch (RuntimeException backendFailure) {
            flow.failIo(request);
            flow.finish(request);
        }
    }

    private void completeWrite(ProtosFileFlow.Request request) {
        if (request.operation.terminal()) {
            flow.finish(request);
            return;
        }
        if (!request.operation.committed() && !request.operation.commit()) {
            flow.finish(request);
            return;
        }
        flow.setPositionAfterWrite(request, request.bytes.length);
        request.operation.resolve(flow.receiver);
        flow.finish(request);
    }

    private void failWrite(ProtosFileFlow.Request request, int contributedPrefix) {
        if (request.operation.terminal()) {
            flow.finish(request);
            return;
        }
        if (contributedPrefix > 0) {
            if (!request.operation.committed()) {
                flow.failIo(request);
                flow.finish(request);
                return;
            }
            flow.setPositionAfterWrite(request, contributedPrefix);
        }
        flow.failIo(request);
        flow.finish(request);
    }

    /*
     * Owning-domain settlement of a write whose progress is unknown. A write that already has its
     * outcome is left as it is (a duplicate or late report). An uncommitted write contributed
     * nothing, so it fails like any zero-effect failure. A committed write fails with I/O error and
     * keeps its commitment. Its cursor becomes indeterminate, not zero or the
     * pre-write position. Only a later successful absolute seek restores it.
     */
    private void failWriteWithUnknownProgress(ProtosFileFlow.Request request) {
        if (!request.operation.terminal()) {
            if (request.operation.committed()) {
                flow.markPositionUnknown();
            }
            flow.failIo(request);
        }
        flow.finish(request);
    }

    private static void validateContributedPrefix(ProtosFileFlow.Request request, int contributedPrefix) {
        if (contributedPrefix < 0 || contributedPrefix > request.bytes.length) {
            throw new IllegalArgumentException("invalid contributed write prefix");
        }
    }

    void startSeekToEnd(ProtosFileFlow.Request request) {
        try {
            flow.setCancellation(
                    request,
                    ((ProtosFileFlow.SeekableResource) flow.resource)
                            .endPosition(
                                    new ProtosFileFlow.IntegerCompletion() {
                                        @Override
                                        public void succeeded(long end) {
                                            settle(request, () -> completeSeekToEnd(request, end));
                                        }

                                        @Override
                                        public void failed() {
                                            settleFailure(request);
                                        }
                                    }));
        } catch (RuntimeException backendFailure) {
            flow.failIo(request);
            flow.finish(request);
        }
    }

    private void completeSeekToEnd(ProtosFileFlow.Request request, long end) {
        if (end < 0) {
            flow.failIo(request);
            flow.finish(request);
            return;
        }
        if (request.operation.commit()) {
            Object value = ProtosNumericValueSupport.integer(end);
            flow.setLogicalPosition(value);
            request.operation.resolve(value);
        }
        flow.finish(request);
    }

    void startSize(ProtosFileFlow.Request request) {
        try {
            flow.setCancellation(
                    request,
                    ((ProtosFileFlow.SizedResource) flow.resource)
                            .size(
                                    new ProtosFileFlow.IntegerCompletion() {
                                        @Override
                                        public void succeeded(long size) {
                                            settle(request, () -> completeSize(request, size));
                                        }

                                        @Override
                                        public void failed() {
                                            settleFailure(request);
                                        }
                                    }));
        } catch (RuntimeException backendFailure) {
            flow.failIo(request);
            flow.finish(request);
        }
    }

    private void completeSize(ProtosFileFlow.Request request, long size) {
        if (size < 0) {
            flow.failIo(request);
            flow.finish(request);
            return;
        }
        if (request.operation.commit()) {
            request.operation.resolve(ProtosNumericValueSupport.integer(size));
        }
        flow.finish(request);
    }

    void startTruncate(ProtosFileFlow.Request request) {
        try {
            flow.setCancellation(
                    request,
                    ((ProtosFileFlow.TruncatableResource) flow.resource)
                            .truncate(
                                    ProtosNumericValueSupport.isIntegerInLongRange(request.number)
                                            ? ProtosNumericValueSupport.exactLong(request.number)
                                            : Long.MAX_VALUE,
                                    new ProtosFileFlow.ChangeCompletion() {
                                        @Override
                                        public boolean commitChange() {
                                            return request.operation.commit();
                                        }

                                        @Override
                                        public void succeeded() {
                                            settle(request, () -> completeChange(request));
                                        }

                                        @Override
                                        public void failed() {
                                            settleFailure(request);
                                        }
                                    }));
        } catch (RuntimeException backendFailure) {
            flow.failIo(request);
            flow.finish(request);
        }
    }

    void startSync(ProtosFileFlow.Request request) {
        try {
            flow.setCancellation(
                    request,
                    ((ProtosFileFlow.SyncableResource) flow.resource)
                            .sync(
                                    new ProtosFileFlow.SyncCompletion() {
                                        @Override
                                        public boolean commitDurability() {
                                            return request.operation.commit();
                                        }

                                        @Override
                                        public void succeeded() {
                                            settle(request, () -> completeChange(request));
                                        }

                                        @Override
                                        public void failed() {
                                            settleFailure(request);
                                        }
                                    }));
        } catch (RuntimeException backendFailure) {
            flow.failIo(request);
            flow.finish(request);
        }
    }

    private void completeChange(ProtosFileFlow.Request request) {
        if (!request.operation.committed() && !request.operation.commit()) {
            flow.finish(request);
            return;
        }
        request.operation.resolve(flow.receiver);
        flow.finish(request);
    }

    private void settleFailure(ProtosFileFlow.Request request) {
        settle(
                request,
                () -> {
                    flow.failIo(request);
                    flow.finish(request);
                });
    }

    /**
     * I156: publishes one backend completion. A completion arriving on a marked host completion
     * thread (the confined Filesystem grant driver) is delivered as a targeted completion of the
     * request's Future inside the owning Actor, so Bytes, Errors, cursor updates and the start of
     * the next ordered operation all happen in the File's domain. Any other completion, including
     * the owner completing a held callback later, is published directly, as before. Commitment handshakes are not routed here: they are
     * lifecycle transitions that the backend must observe synchronously.
     *
     * <p>If the owning Actor is terminating, the publication is dropped host-only; that Actor's
     * termination cancels the File's operations and closes it.
     */
    private void settle(ProtosFileFlow.Request request, Runnable publication) {
        if (!ProtosHostCompletionThread.isCurrent()) {
            publication.run();
            return;
        }
        try {
            flow.domain.enqueueTargetedFutureCompletionForRuntime(
                    request.operation.future(),
                    () -> {
                        boolean pending = request.operation.future().isPending();
                        publication.run();
                        return pending && !request.operation.future().isPending();
                    });
        } catch (IllegalStateException ownerTerminating) {
            // Host-only drop; no guest value is created for a terminating owner.
        }
    }
}
