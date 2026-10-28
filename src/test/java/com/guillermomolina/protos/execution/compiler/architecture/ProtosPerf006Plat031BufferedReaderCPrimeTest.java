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
package com.guillermomolina.protos.execution;

import static org.junit.jupiter.api.Assertions.*;

import com.guillermomolina.protos.parser.ProtosParser;
import com.guillermomolina.protos.runtime.*;
import com.guillermomolina.protos.runtime.ProtosBytesValue;
import com.guillermomolina.protos.runtime.callables.ProtosSuspensionCapableNativeClosureBody;
import com.guillermomolina.protos.semantic.Canonicalizer;
import com.guillermomolina.protos.semantic.ast.CanonicalClosure;
import com.guillermomolina.protos.semantic.ast.CanonicalSequence;
import com.oracle.truffle.api.TruffleLanguage.LanguageReference;
import com.oracle.truffle.api.source.Source;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.graalvm.polyglot.Context;
import org.junit.jupiter.api.Test;

final class ProtosPerf006Plat031BufferedReaderCPrimeTest {
    private static final LanguageReference<ProtosLanguage> LANGUAGE_REF =
            LanguageReference.create(ProtosLanguage.class);

    @Test
    void bufferedReadRunsSuspendibleGuestCallbackInsideOperationOwnedCPrime()
            throws Exception {
        try (LanguageScope scope = languageScope()) {
            Fixture f = fixture();
            ProtosFutureValue gate = future(f);
            ProtosFutureValue lower = future(f);
            lower.resolve(bytes(f.prelude, 'A', 'B'), f.module);
            AtomicInteger reads = new AtomicInteger();

            f.module.context().createLocalSlot(
                    "readProbe",
                    ProtosClosureValue.nativeClosure(
                            (activation, supplied) -> {
                                reads.incrementAndGet();
                                return ProtosNullValue.INSTANCE;
                            }));
            f.module.context().createLocalSlot(
                    "readGate",
                    ProtosClosureValue.nativeClosure(
                            (activation, supplied) -> gate));
            f.module.context().createLocalSlot(
                    "nextRead",
                    ProtosClosureValue.nativeClosure(
                            (activation, supplied) -> lower));
            f.source.createLocalSlot(
                    "read",
                    sourceClosure(
                            scope.language(),
                            f.module,
                            "(max) => { readProbe()\nreadGate().value()\nnextRead() }",
                            "plat031-buffered-reader-source-read.protos"));

            ProtosObjectValue reader = reader(f);
            f.module.context().createLocalSlot("reader", reader);

            ProtosFutureValue outer =
                    invokeCPrimeReturningFuture(
                            scope.language(),
                            f,
                            "reader.read(1)",
                            "plat031-buffered-reader-read.protos");
            assertEquals(ProtosFutureValue.State.PENDING, outer.state());
            assertEquals(0, reads.get());
            assertEquals(0, f.domain.liveTaskCount());

            assertTrue(f.domain.dispatchOne());
            assertEquals(1, reads.get());
            assertEquals(ProtosFutureValue.State.PENDING, outer.state());
            assertEquals(0, f.domain.liveTaskCount());

            assertTrue(gate.resolve(ProtosNullValue.INSTANCE, f.module));
            assertEquals(
                    1,
                    reads.get(),
                    "Future terminalization must not resume target.read guest code inline");
            assertEquals(ProtosFutureValue.State.PENDING, outer.state());

            assertTrue(f.domain.dispatchOne());
            assertEquals(ProtosFutureValue.State.RESOLVED, outer.state());
            assertBytes(outer.resolvedValue().orElseThrow(), 'A');
            assertEquals(1, reads.get());
            assertEquals(0, f.domain.liveTaskCount());

            f.module = siblingModule(f.module);
            ProtosFutureValue buffered =
                    invokeCPrimeReturningFuture(
                            scope.language(),
                            f,
                            "reader.read(1)",
                            "plat031-buffered-reader-read-ahead.protos");
            assertEquals(ProtosFutureValue.State.RESOLVED, buffered.state());
            assertBytes(buffered.resolvedValue().orElseThrow(), 'B');
            assertEquals(
                    1,
                    reads.get(),
                    "retained read-ahead must satisfy the next read without replaying target.read");
            assertFalse(f.domain.dispatchOne());
        }

        System.out.println("PLAT031_BUFFERED_READER_READ_CALLBACK_CPRIME=PASS");
        System.out.println("PLAT031_BUFFERED_READER_CALLBACK_REPLAY=NO");
        System.out.println("PLAT031_BUFFERED_READER_HIDDEN_TASK=NO");
        System.out.println("PLAT031_BUFFERED_READER_BACKEND_INLINE_GUEST_REENTRY=NO");
    }

    @Test
    void cancelledBufferedReadPreservesLateReadAheadAndUnblocksNextRead()
            throws Exception {
        try (LanguageScope scope = languageScope()) {
            Fixture f = fixture();
            ProtosFutureValue lower1 = future(f);
            ProtosFutureValue lower2 = future(f);
            lower2.resolve(bytes(f.prelude, 'Y'), f.module);
            AtomicInteger reads = new AtomicInteger();
            AtomicInteger lowerCancelRequests = new AtomicInteger();
            lower1.attachCancellationProducer(lowerCancelRequests::incrementAndGet);

            f.source.createLocalSlot(
                    "read",
                    ProtosClosureValue.nativeClosure(
                            (activation, supplied) -> {
                                int index = reads.incrementAndGet();
                                assertTrue(activation.task().isEmpty());
                                assertTrue(
                                        activation
                                                .deferredCPrimeOperationForRuntime()
                                                .isPresent());
                                return index == 1 ? lower1 : lower2;
                            }));

            ProtosObjectValue reader = reader(f);
            f.module.context().createLocalSlot("reader", reader);

            ProtosFutureValue cancelled =
                    invokeCPrimeReturningFuture(
                            scope.language(),
                            f,
                            "reader.read(1)",
                            "plat031-buffered-reader-cancel.protos");
            assertEquals(ProtosFutureValue.State.PENDING, cancelled.state());

            assertTrue(f.domain.dispatchOne());
            assertEquals(1, reads.get());
            assertEquals(ProtosFutureValue.State.PENDING, cancelled.state());

            assertTrue(cancelled.cancelRequest());
            assertEquals(ProtosFutureValue.State.CANCELLED, cancelled.state());
            assertEquals(1, lowerCancelRequests.get());
            assertEquals(0, f.domain.liveTaskCount());

            // The lower read ignores the cancellation request and delivers bytes anyway.
            assertTrue(lower1.resolve(bytes(f.prelude, 'Z', 'W'), f.module));
            assertEquals(ProtosFutureValue.State.CANCELLED, cancelled.state());
            assertEquals(
                    1,
                    reads.get(),
                    "late lower completion must remain an inert cleanup callback");
            drain(f);

            ProtosFutureValue z = readOne(scope.language(), f, "bug030-late-z.protos");
            assertBytes(z.resolvedValue().orElseThrow(), 'Z');
            ProtosFutureValue w = readOne(scope.language(), f, "bug030-late-w.protos");
            assertBytes(w.resolvedValue().orElseThrow(), 'W');
            assertEquals(
                    1,
                    reads.get(),
                    "preserved late bytes must satisfy reads without invoking target.read");

            ProtosFutureValue y = readOne(scope.language(), f, "bug030-after-late.protos");
            assertBytes(y.resolvedValue().orElseThrow(), 'Y');
            assertEquals(2, reads.get());
            assertEquals(ProtosFutureValue.State.CANCELLED, cancelled.state());
            assertFalse(f.domain.dispatchOne());
            assertEquals(0, f.domain.liveTaskCount());
        }

        System.out.println("PLAT031_BUFFERED_READER_LATE_READ_AHEAD=PRESERVED");
        System.out.println("PLAT031_BUFFERED_READER_LATE_CALLBACK_GUEST_REENTRY=NO");
    }

    @Test
    void cancelAfterLowerResolutionBeforeCPrimeResumePreservesBytes() throws Exception {
        try (LanguageScope scope = languageScope()) {
            Fixture f = fixture();
            ProtosFutureValue lower1 = future(f);
            AtomicInteger reads = new AtomicInteger();
            f.source.createLocalSlot(
                    "read",
                    ProtosClosureValue.nativeClosure(
                            (activation, supplied) ->
                                    reads.incrementAndGet() == 1
                                            ? lower1
                                            : resolvedFuture(f, 'Y')));
            f.module.context().createLocalSlot("reader", reader(f));

            ProtosFutureValue cancelled =
                    invokeCPrimeReturningFuture(
                            scope.language(),
                            f,
                            "reader.read(1)",
                            "bug030-resolve-before-resume.protos");
            assertTrue(f.domain.dispatchOne());
            assertEquals(1, reads.get());
            assertEquals(ProtosFutureValue.State.PENDING, cancelled.state());

            // The lower result exists, but its C-prime continuation has not run yet.
            assertTrue(lower1.resolve(bytes(f.prelude, 'Z'), f.module));
            assertEquals(ProtosFutureValue.State.PENDING, cancelled.state());
            assertTrue(cancelled.cancelRequest());
            assertEquals(ProtosFutureValue.State.CANCELLED, cancelled.state());
            drain(f);
            assertEquals(ProtosFutureValue.State.CANCELLED, cancelled.state());

            ProtosFutureValue z = readOne(scope.language(), f, "bug030-resume-z.protos");
            assertBytes(z.resolvedValue().orElseThrow(), 'Z');
            assertEquals(1, reads.get());

            ProtosFutureValue y = readOne(scope.language(), f, "bug030-resume-y.protos");
            assertBytes(y.resolvedValue().orElseThrow(), 'Y');
            assertEquals(2, reads.get());
            assertEquals(0, f.domain.liveTaskCount());
        }

        System.out.println("BUG030_CANCEL_BEFORE_CPRIME_RESUME_PRESERVED=PASS");
    }

    @Test
    void cancelledLowerReadWithoutBytesAllowsNextRead() throws Exception {
        try (LanguageScope scope = languageScope()) {
            Fixture f = fixture();
            ProtosFutureValue lower1 = future(f);
            AtomicInteger reads = new AtomicInteger();
            AtomicInteger lowerCancelRequests = new AtomicInteger();
            lower1.attachCancellationProducer(lowerCancelRequests::incrementAndGet);
            f.source.createLocalSlot(
                    "read",
                    ProtosClosureValue.nativeClosure(
                            (activation, supplied) ->
                                    reads.incrementAndGet() == 1
                                            ? lower1
                                            : resolvedFuture(f, 'Y')));
            f.module.context().createLocalSlot("reader", reader(f));

            ProtosFutureValue cancelled =
                    invokeCPrimeReturningFuture(
                            scope.language(),
                            f,
                            "reader.read(1)",
                            "bug030-lower-cancelled.protos");
            assertTrue(f.domain.dispatchOne());
            assertTrue(cancelled.cancelRequest());
            assertEquals(ProtosFutureValue.State.CANCELLED, cancelled.state());
            assertEquals(1, lowerCancelRequests.get());

            // The lower cancellation takes effect: no bytes exist to preserve.
            assertTrue(lower1.cancelTerminal());
            drain(f);

            ProtosFutureValue y = readOne(scope.language(), f, "bug030-after-lower-cancel.protos");
            assertBytes(y.resolvedValue().orElseThrow(), 'Y');
            assertEquals(2, reads.get());
            assertEquals(ProtosFutureValue.State.CANCELLED, cancelled.state());
            assertFalse(f.domain.dispatchOne());
            assertEquals(0, f.domain.liveTaskCount());
        }

        System.out.println("BUG030_CANCELLED_LOWER_NO_FABRICATED_INPUT=PASS");
    }

    @Test
    void queuedFollowerReceivesLateCancelledReadBytesBeforeNewLowerData() throws Exception {
        try (LanguageScope scope = languageScope()) {
            Fixture f = fixture();
            ProtosFutureValue lower1 = future(f);
            AtomicInteger reads = new AtomicInteger();
            AtomicInteger lowerCancelRequests = new AtomicInteger();
            // The lower read ignores cancellation and may still deliver bytes.
            lower1.attachCancellationProducer(lowerCancelRequests::incrementAndGet);
            f.source.createLocalSlot(
                    "read",
                    ProtosClosureValue.nativeClosure(
                            (activation, supplied) ->
                                    reads.incrementAndGet() == 1
                                            ? lower1
                                            : resolvedFuture(f, 'Y')));
            f.module.context().createLocalSlot("reader", reader(f));

            ProtosFutureValue cancelled =
                    invokeCPrimeReturningFuture(
                            scope.language(),
                            f,
                            "reader.read(1)",
                            "bug030-queued-first.protos");
            assertTrue(f.domain.dispatchOne());
            assertEquals(1, reads.get());

            f.module = siblingModule(f.module);
            ProtosFutureValue follower =
                    invokeCPrimeReturningFuture(
                            scope.language(),
                            f,
                            "reader.read(1)",
                            "bug030-queued-follower.protos");
            assertTrue(cancelled.cancelRequest());
            assertEquals(1, lowerCancelRequests.get());
            drain(f);
            assertEquals(
                    ProtosFutureValue.State.PENDING,
                    follower.state(),
                    "a follower must wait while the cancelled lower read may still consume input");
            assertEquals(1, reads.get());

            assertTrue(lower1.resolve(bytes(f.prelude, 'Z'), f.module));
            drain(f);
            assertEquals(ProtosFutureValue.State.RESOLVED, follower.state());
            assertBytes(follower.resolvedValue().orElseThrow(), 'Z');
            assertEquals(1, reads.get(), "new lower data must not overtake preserved bytes");

            ProtosFutureValue y = readOne(scope.language(), f, "bug030-queued-y.protos");
            assertBytes(y.resolvedValue().orElseThrow(), 'Y');
            assertEquals(2, reads.get());
            assertEquals(ProtosFutureValue.State.CANCELLED, cancelled.state());
            assertFalse(f.domain.dispatchOne());
            assertEquals(0, f.domain.liveTaskCount());
        }

        System.out.println("BUG030_QUEUED_FOLLOWER_RECEIVES_LATE_BYTES=PASS");
    }

    @Test
    void queuedCancellationDoesNotDiscardPreviouslyBufferedPrefix() throws Exception {
        try (LanguageScope scope = languageScope()) {
            Fixture f = fixture();
            ProtosFutureValue lower1 = future(f);
            AtomicInteger reads = new AtomicInteger();
            f.source.createLocalSlot(
                    "read",
                    ProtosClosureValue.nativeClosure(
                            (activation, supplied) ->
                                    reads.incrementAndGet() == 1
                                            ? lower1
                                            : resolvedFuture(f, 'Y')));
            f.module.context().createLocalSlot("reader", reader(f));

            ProtosFutureValue first =
                    invokeCPrimeReturningFuture(
                            scope.language(),
                            f,
                            "reader.read(1)",
                            "bug030-prefix-first.protos");
            assertTrue(f.domain.dispatchOne());

            f.module = siblingModule(f.module);
            ProtosFutureValue middle =
                    invokeCPrimeReturningFuture(
                            scope.language(),
                            f,
                            "reader.read(1)",
                            "bug030-prefix-middle.protos");
            assertTrue(middle.cancelRequest());
            assertEquals(ProtosFutureValue.State.CANCELLED, middle.state());

            assertTrue(lower1.resolve(bytes(f.prelude, 'A', 'B'), f.module));
            drain(f);
            assertEquals(ProtosFutureValue.State.RESOLVED, first.state());
            assertBytes(first.resolvedValue().orElseThrow(), 'A');

            ProtosFutureValue b = readOne(scope.language(), f, "bug030-prefix-b.protos");
            assertBytes(b.resolvedValue().orElseThrow(), 'B');
            assertEquals(1, reads.get());

            ProtosFutureValue y = readOne(scope.language(), f, "bug030-prefix-y.protos");
            assertBytes(y.resolvedValue().orElseThrow(), 'Y');
            assertEquals(2, reads.get());
            assertEquals(ProtosFutureValue.State.CANCELLED, middle.state());
            assertEquals(0, f.domain.liveTaskCount());
        }

        System.out.println("BUG030_QUEUED_CANCEL_KEEPS_BUFFERED_PREFIX=PASS");
    }

    @Test
    void lowerResolutionDuringCancellationRequestPreservesBytesExactlyOnce()
            throws Exception {
        try (LanguageScope scope = languageScope()) {
            Fixture f = fixture();
            ProtosFutureValue lower1 = future(f);
            AtomicInteger reads = new AtomicInteger();
            AtomicInteger lowerCancelRequests = new AtomicInteger();
            ProtosActivation resolver = f.module;
            // The lower read had already obtained data: it resolves reentrantly while the
            // outer cancellation is being propagated to it.
            lower1.attachCancellationProducer(
                    () -> {
                        lowerCancelRequests.incrementAndGet();
                        lower1.resolve(bytes(f.prelude, 'Z', 'W'), resolver);
                    });
            f.source.createLocalSlot(
                    "read",
                    ProtosClosureValue.nativeClosure(
                            (activation, supplied) ->
                                    reads.incrementAndGet() == 1
                                            ? lower1
                                            : resolvedFuture(f, 'Y')));
            f.module.context().createLocalSlot("reader", reader(f));

            ProtosFutureValue cancelled =
                    invokeCPrimeReturningFuture(
                            scope.language(),
                            f,
                            "reader.read(1)",
                            "bug030-reentrant-lower-resolution.protos");
            assertTrue(f.domain.dispatchOne());
            assertEquals(1, reads.get());
            assertEquals(ProtosFutureValue.State.PENDING, cancelled.state());

            assertTrue(cancelled.cancelRequest());
            assertEquals(ProtosFutureValue.State.CANCELLED, cancelled.state());
            assertEquals(1, lowerCancelRequests.get());
            assertEquals(ProtosFutureValue.State.RESOLVED, lower1.state());
            drain(f);
            assertEquals(
                    ProtosFutureValue.State.CANCELLED,
                    cancelled.state(),
                    "the reentrant lower resolution must not reopen the outer read");

            ProtosFutureValue z = readOne(scope.language(), f, "bug030-reentrant-z.protos");
            assertBytes(z.resolvedValue().orElseThrow(), 'Z');
            ProtosFutureValue w = readOne(scope.language(), f, "bug030-reentrant-w.protos");
            assertBytes(w.resolvedValue().orElseThrow(), 'W');
            assertEquals(1, reads.get());

            ProtosFutureValue y = readOne(scope.language(), f, "bug030-reentrant-y.protos");
            assertBytes(y.resolvedValue().orElseThrow(), 'Y');
            assertEquals(2, reads.get());
            assertEquals(1, lowerCancelRequests.get());
            assertEquals(ProtosFutureValue.State.CANCELLED, cancelled.state());
            assertFalse(f.domain.dispatchOne());
            assertEquals(0, f.domain.liveTaskCount());
        }

        System.out.println("BUG030_REENTRANT_LOWER_RESOLUTION_PRESERVED_ONCE=PASS");
    }

    @SuppressWarnings("try")
    @Test
    void bufferedReadIsSuspensionCapableButCloseRemainsOutsideThisSlice()
            throws Exception {
        try (LanguageScope scope = languageScope()) {
            Fixture f = fixture();
            f.source.createLocalSlot(
                    "read",
                    ProtosClosureValue.nativeClosure(
                            (activation, supplied) -> {
                                ProtosFutureValue eof = future(f);
                                eof.resolve(ProtosNullValue.INSTANCE, activation);
                                return eof;
                            }));
            ProtosObjectValue reader = reader(f);

            Object readSlot = reader.readLocalSlot("read").orElseThrow();
            Object closeSlot = reader.readLocalSlot("close").orElseThrow();
            assertInstanceOf(
                    ProtosSuspensionCapableNativeClosureBody.class,
                    assertInstanceOf(ProtosClosureValue.class, readSlot)
                            .nativeBody()
                            .orElseThrow());
            assertFalse(
                    assertInstanceOf(ProtosClosureValue.class, closeSlot)
                                    .nativeBody()
                                    .orElseThrow()
                            instanceof ProtosSuspensionCapableNativeClosureBody);
        }

        System.out.println("PLAT031_BUFFERED_READER_READ_SUSPENSION_CAPABLE=YES");
        System.out.println("PLAT031_BUFFERED_READER_CLOSE_INCLUDED=NO");
    }

    @Test
    void cancelBeforeFirstCPrimeDispatchRetiresActiveReadAndUnblocksNextRead()
            throws Exception {
        try (LanguageScope scope = languageScope()) {
            Fixture f = fixture();
            AtomicInteger reads = new AtomicInteger();
            f.source.createLocalSlot(
                    "read",
                    ProtosClosureValue.nativeClosure(
                            (activation, supplied) -> {
                                reads.incrementAndGet();
                                return resolvedFuture(f, 'A', 'B');
                            }));
            ProtosObjectValue reader = reader(f);
            f.module.context().createLocalSlot("reader", reader);

            ProtosFutureValue cancelled =
                    invokeCPrimeReturningFuture(
                            scope.language(),
                            f,
                            "reader.read(1)",
                            "bug029-cancel-before-dispatch.protos");
            assertEquals(ProtosFutureValue.State.PENDING, cancelled.state());
            assertTrue(cancelled.cancelRequest());
            assertEquals(ProtosFutureValue.State.CANCELLED, cancelled.state());
            assertEquals(0, reads.get());
            drain(f);
            assertEquals(0, reads.get(), "a discarded C-prime dispatch must not run target.read");

            f.module = siblingModule(f.module);
            ProtosFutureValue next =
                    invokeCPrimeReturningFuture(
                            scope.language(),
                            f,
                            "reader.read(1)",
                            "bug029-after-pre-dispatch-cancel.protos");
            drain(f);
            assertEquals(ProtosFutureValue.State.RESOLVED, next.state());
            assertBytes(next.resolvedValue().orElseThrow(), 'A');
            assertEquals(1, reads.get());
            assertEquals(ProtosFutureValue.State.CANCELLED, cancelled.state());
            assertFalse(f.domain.dispatchOne());
            assertEquals(0, f.domain.liveTaskCount());
        }

        System.out.println("BUG029_PRE_DISPATCH_CANCEL_RETIRES_ACTIVE_READ=PASS");
        System.out.println("BUG029_PRE_DISPATCH_CANCEL_HIDDEN_TASK=NO");
    }

    @Test
    void cancelActiveReadReleasesAlreadyQueuedFollowersInOrder() throws Exception {
        try (LanguageScope scope = languageScope()) {
            Fixture f = fixture();
            AtomicInteger reads = new AtomicInteger();
            f.source.createLocalSlot(
                    "read",
                    ProtosClosureValue.nativeClosure(
                            (activation, supplied) -> {
                                reads.incrementAndGet();
                                return resolvedFuture(f, 'A', 'B');
                            }));
            List<ProtosFutureValue> futures = captureReads(scope.language(), f, 3);
            ProtosFutureValue first = futures.get(0);
            assertEquals(0, reads.get());

            assertTrue(first.cancelRequest());
            assertEquals(ProtosFutureValue.State.CANCELLED, first.state());
            assertEquals(ProtosFutureValue.State.PENDING, futures.get(2).state());
            drain(f);

            assertEquals(ProtosFutureValue.State.CANCELLED, first.state());
            assertBytes(futures.get(1).resolvedValue().orElseThrow(), 'A');
            assertBytes(futures.get(2).resolvedValue().orElseThrow(), 'B');
            assertEquals(1, reads.get(), "the cancelled read must not invoke target.read");
            assertEquals(0, f.domain.liveTaskCount());
        }

        System.out.println("BUG029_QUEUED_FOLLOWERS_PROGRESS=PASS");
    }

    @Test
    void cancelQueuedMiddleReadPreservesRelativeOrderOfOthers() throws Exception {
        try (LanguageScope scope = languageScope()) {
            Fixture f = fixture();
            AtomicInteger reads = new AtomicInteger();
            f.source.createLocalSlot(
                    "read",
                    ProtosClosureValue.nativeClosure(
                            (activation, supplied) -> {
                                reads.incrementAndGet();
                                return resolvedFuture(f, 'A', 'B');
                            }));
            List<ProtosFutureValue> futures = captureReads(scope.language(), f, 3);

            assertTrue(futures.get(1).cancelRequest());
            assertEquals(ProtosFutureValue.State.CANCELLED, futures.get(1).state());
            drain(f);

            assertBytes(futures.get(0).resolvedValue().orElseThrow(), 'A');
            assertEquals(ProtosFutureValue.State.CANCELLED, futures.get(1).state());
            assertBytes(futures.get(2).resolvedValue().orElseThrow(), 'B');
            assertEquals(1, reads.get());
            assertEquals(0, f.domain.liveTaskCount());
        }

        System.out.println("BUG029_QUEUED_MIDDLE_CANCEL_ORDER=PASS");
    }

    @Test
    void cancelDuringSuspendedGuestTargetReadRetiresWithoutLateResume() throws Exception {
        try (LanguageScope scope = languageScope()) {
            Fixture f = fixture();
            ProtosFutureValue firstGate = future(f);
            ProtosFutureValue secondGate = future(f);
            secondGate.resolve(ProtosNullValue.INSTANCE, f.module);
            AtomicInteger probes = new AtomicInteger();
            AtomicInteger lowerReads = new AtomicInteger();

            f.module.context().createLocalSlot(
                    "readProbe",
                    ProtosClosureValue.nativeClosure(
                            (activation, supplied) -> {
                                probes.incrementAndGet();
                                return ProtosNullValue.INSTANCE;
                            }));
            f.module.context().createLocalSlot(
                    "readGate",
                    ProtosClosureValue.nativeClosure(
                            (activation, supplied) ->
                                    probes.get() == 1 ? firstGate : secondGate));
            f.module.context().createLocalSlot(
                    "nextRead",
                    ProtosClosureValue.nativeClosure(
                            (activation, supplied) -> {
                                lowerReads.incrementAndGet();
                                return resolvedFuture(f, 'Q');
                            }));
            f.source.createLocalSlot(
                    "read",
                    sourceClosure(
                            scope.language(),
                            f.module,
                            "(max) => { readProbe()\nreadGate().value()\nnextRead() }",
                            "bug029-suspended-source-read.protos"));
            ProtosObjectValue reader = reader(f);
            f.module.context().createLocalSlot("reader", reader);

            ProtosFutureValue cancelled =
                    invokeCPrimeReturningFuture(
                            scope.language(),
                            f,
                            "reader.read(1)",
                            "bug029-suspended-read.protos");
            assertTrue(f.domain.dispatchOne());
            assertEquals(1, probes.get());
            assertEquals(ProtosFutureValue.State.PENDING, cancelled.state());

            assertTrue(cancelled.cancelRequest());
            assertEquals(ProtosFutureValue.State.CANCELLED, cancelled.state());
            assertEquals(0, f.domain.liveTaskCount());
            drain(f);

            f.module = siblingModule(f.module);
            ProtosFutureValue next =
                    invokeCPrimeReturningFuture(
                            scope.language(),
                            f,
                            "reader.read(1)",
                            "bug029-after-suspended-cancel.protos");
            drain(f);
            assertEquals(ProtosFutureValue.State.RESOLVED, next.state());
            assertBytes(next.resolvedValue().orElseThrow(), 'Q');
            assertEquals(2, probes.get());
            assertEquals(1, lowerReads.get());

            assertTrue(firstGate.resolve(ProtosNullValue.INSTANCE, f.module));
            drain(f);
            assertEquals(2, probes.get());
            assertEquals(
                    1,
                    lowerReads.get(),
                    "the cancelled guest target.read must not resume after its gate resolves");
            assertEquals(ProtosFutureValue.State.CANCELLED, cancelled.state());
            assertEquals(0, f.domain.liveTaskCount());
        }

        System.out.println("BUG029_SUSPENDED_GUEST_READ_CANCEL_RETIRED=PASS");
        System.out.println("BUG029_SUSPENDED_GUEST_READ_LATE_RESUME=NO");
    }

    @Test
    void cancelAfterLowerReadStartedKeepsFollowerBehindPendingLower() throws Exception {
        try (LanguageScope scope = languageScope()) {
            Fixture f = fixture();
            ProtosFutureValue lower1 = future(f);
            AtomicInteger reads = new AtomicInteger();
            AtomicInteger lowerCancelRequests = new AtomicInteger();
            lower1.attachCancellationProducer(lowerCancelRequests::incrementAndGet);
            f.source.createLocalSlot(
                    "read",
                    ProtosClosureValue.nativeClosure(
                            (activation, supplied) ->
                                    reads.incrementAndGet() == 1
                                            ? lower1
                                            : resolvedFuture(f, 'Y')));
            ProtosObjectValue reader = reader(f);
            f.module.context().createLocalSlot("reader", reader);

            ProtosFutureValue cancelled =
                    invokeCPrimeReturningFuture(
                            scope.language(),
                            f,
                            "reader.read(1)",
                            "bug029-lower-started.protos");
            assertTrue(f.domain.dispatchOne());
            assertEquals(1, reads.get());

            f.module = siblingModule(f.module);
            ProtosFutureValue follower =
                    invokeCPrimeReturningFuture(
                            scope.language(),
                            f,
                            "reader.read(1)",
                            "bug029-lower-started-follower.protos");
            assertTrue(cancelled.cancelRequest());
            assertEquals(ProtosFutureValue.State.CANCELLED, cancelled.state());
            assertEquals(1, lowerCancelRequests.get());

            drain(f);
            assertEquals(
                    ProtosFutureValue.State.PENDING,
                    follower.state(),
                    "a follower must not overtake a lower read that may still consume input");
            assertEquals(1, reads.get());

            // The lower cancellation takes effect before any byte is consumed, so no input of
            // the cancelled read's logical position exists to preserve.
            assertTrue(lower1.cancelTerminal());
            drain(f);
            assertEquals(ProtosFutureValue.State.CANCELLED, cancelled.state());
            assertEquals(ProtosFutureValue.State.RESOLVED, follower.state());
            assertBytes(follower.resolvedValue().orElseThrow(), 'Y');
            assertEquals(2, reads.get());
            assertEquals(1, lowerCancelRequests.get());
            assertFalse(f.domain.dispatchOne());
            assertEquals(0, f.domain.liveTaskCount());
        }

        System.out.println("BUG029_STARTED_LOWER_FOLLOWER_NOT_OVERTAKING=PASS");
    }

    @Test
    void closeBeforeFirstCPrimeDispatchFailsActiveAndQueuedReadsWithoutTargetRead()
            throws Exception {
        try (LanguageScope scope = languageScope()) {
            Fixture f = fixture();
            AtomicInteger reads = new AtomicInteger();
            f.source.createLocalSlot(
                    "read",
                    ProtosClosureValue.nativeClosure(
                            (activation, supplied) -> {
                                reads.incrementAndGet();
                                return resolvedFuture(f, 'A');
                            }));
            f.source.createLocalSlot(
                    "close",
                    ProtosClosureValue.nativeClosure(
                            (activation, supplied) -> {
                                ProtosFutureValue closed = future(f);
                                closed.resolve(ProtosNullValue.INSTANCE, activation);
                                return closed;
                            }));
            List<ProtosFutureValue> futures =
                    captureTurn(
                            scope.language(),
                            f,
                            "capture(reader.read(1))\ncapture(reader.read(1))\ncapture(reader.close())",
                            "bug029-close-before-dispatch.protos");
            ProtosFutureValue first = futures.get(0);
            ProtosFutureValue second = futures.get(1);
            ProtosFutureValue close = futures.get(2);
            drain(f);

            assertEquals(ProtosFutureValue.State.FAILED, first.state());
            assertEquals(ProtosFutureValue.State.FAILED, second.state());
            assertNotSame(first, second);
            assertNotSame(
                    first.failedError().orElseThrow(),
                    second.failedError().orElseThrow());
            assertEquals(
                    ProtosFutureValue.State.RESOLVED,
                    close.state(),
                    "close must not wait for a discarded C-prime dispatch");
            assertSame(
                    f.module.context().readLocalSlot("reader").orElseThrow(),
                    close.resolvedValue().orElseThrow());
            assertEquals(0, reads.get());

            f.module = siblingModule(f.module);
            ProtosFutureValue afterClose =
                    invokeCPrimeReturningFuture(
                            scope.language(),
                            f,
                            "reader.read(1)",
                            "bug029-read-after-close.protos");
            drain(f);
            assertEquals(ProtosFutureValue.State.FAILED, afterClose.state());
            assertEquals(0, reads.get());
            assertEquals(0, f.domain.liveTaskCount());
        }

        System.out.println("BUG029_CLOSE_BEFORE_DISPATCH_RETIRES_READS=PASS");
    }

    private static List<ProtosFutureValue> captureReads(
            ProtosLanguage language,
            Fixture f,
            int count)
            throws Exception {
        String turn =
                String.join(
                        "\n",
                        java.util.Collections.nCopies(count, "capture(reader.read(1))"));
        return captureTurn(language, f, turn, "bug029-queued-reads.protos");
    }

    /** Runs one guest turn against a fresh reader, capturing every Future passed to capture(). */
    private static List<ProtosFutureValue> captureTurn(
            ProtosLanguage language,
            Fixture f,
            String characters,
            String sourceName)
            throws Exception {
        List<ProtosFutureValue> captured = new ArrayList<>();
        f.module.context().createLocalSlot(
                "capture",
                ProtosClosureValue.nativeClosure(
                        (activation, supplied) -> {
                            captured.add(
                                    assertInstanceOf(
                                            ProtosFutureValue.class, supplied.get(0)));
                            return ProtosNullValue.INSTANCE;
                        }));
        f.module.context().createLocalSlot("reader", reader(f));
        ProtosTask caller =
                execute(f.domain, f.module, lowerRoot(language, characters, sourceName));
        assertTrue(f.domain.dispatchOne());
        assertEquals(ProtosTask.State.COMPLETED, caller.state());
        return captured;
    }

    /** Starts one read on a sibling module and dispatches it to completion. */
    private static ProtosFutureValue readOne(
            ProtosLanguage language,
            Fixture f,
            String sourceName)
            throws Exception {
        f.module = siblingModule(f.module);
        ProtosFutureValue read =
                invokeCPrimeReturningFuture(language, f, "reader.read(1)", sourceName);
        drain(f);
        assertEquals(ProtosFutureValue.State.RESOLVED, read.state());
        return read;
    }

    /** Explicitly dispatches every runnable Actor-local item; never waits on time. */
    private static void drain(Fixture f) {
        while (f.domain.dispatchOne()) {
            // Dispatch until no Actor-local work remains runnable.
        }
    }

    private static ProtosFutureValue resolvedFuture(Fixture f, int... values) {
        ProtosFutureValue resolved = future(f);
        resolved.resolve(bytes(f.prelude, values), f.module);
        return resolved;
    }

    private static ProtosFutureValue invokeCPrimeReturningFuture(
            ProtosLanguage language,
            Fixture fixture,
            String characters,
            String sourceName)
            throws Exception {
        ProtosTask caller =
                execute(
                        fixture.domain,
                        fixture.module,
                        lowerRoot(language, characters, sourceName));
        assertTrue(fixture.domain.dispatchOne());
        assertEquals(ProtosTask.State.COMPLETED, caller.state());
        return assertInstanceOf(
                ProtosFutureValue.class,
                caller.result().orElseThrow());
    }

    private static ProtosObjectValue reader(Fixture f) {
        return assertInstanceOf(
                ProtosObjectValue.class,
                ProtosInvocation.invokeMessage(
                        ProtosStandardModuleMemberTestSupport.bufferedReaderFactory(f.prelude),
                        "call",
                        List.of(f.source),
                        f.module));
    }

    private static ProtosActivation siblingModule(ProtosActivation module) {
        return module.prelude().orElseThrow().newModuleActivation(
                module.actorModuleState(),
                module.currentModuleKey().orElse(null),
                module.context(),
                module.executionDomain());
    }

    private static ProtosFutureValue future(Fixture f) {
        return new ProtosFutureValue(f.prelude.futurePrototype(), f.domain);
    }

    private static Fixture fixture() throws Exception {
        ProtosPrelude prelude =
                new ProtosCoreBootstrap().bootstrap(Path.of("protos", "lib", "core"));
        ProtosActorExecutionDomain domain = new ProtosActorExecutionDomain();
        ProtosActivation module =
                prelude.newModuleActivation(
                        new ProtosActorModuleState(),
                        null,
                        prelude.newExecutionContext(),
                        domain);
        ProtosObjectValue source =
                new ProtosObjectValue(ProtosObjectValue.rootObject());
        return new Fixture(prelude, domain, module, source);
    }

    private static ProtosClosureValue sourceClosure(
            ProtosLanguage language,
            ProtosActivation creator,
            String characters,
            String sourceName)
            throws Exception {
        CanonicalClosure definition = closureDefinition(characters);
        ProtosClosureExecutionPlan plan =
                ProtosClosureExecutionPlan.bytecode(
                        definition,
                        language,
                        source(characters, sourceName));
        return new ProtosClosureValue(
                definition,
                creator.lexicalContextsForClosureCapture(),
                creator.receiver(),
                creator.methodHome().orElse(null),
                creator.returnHome().orElse(null),
                creator.prelude().orElseThrow(),
                plan);
    }

    private static ProtosTask execute(
            ProtosActorExecutionDomain domain,
            ProtosActivation activation,
            ProtosBytecodeRootNode root) {
        return domain.createTask(
                null,
                current ->
                        ProtosBytecodeTaskExecution.execute(
                                current,
                                root.getCallTarget(),
                                activation));
    }

    private static ProtosBytecodeRootNode lowerRoot(
            ProtosLanguage language,
            String characters,
            String sourceName)
            throws Exception {
        return new CanonicalToBytecodeLowerer(
                        language,
                        source(characters, sourceName))
                .lowerRoot(canonicalize(characters));
    }

    private static CanonicalClosure closureDefinition(String characters) {
        CanonicalSequence sequence = canonicalize(characters);
        assertEquals(1, sequence.expressions().size());
        return (CanonicalClosure) sequence.expressions().get(0);
    }

    private static CanonicalSequence canonicalize(String characters) {
        return (CanonicalSequence)
                new Canonicalizer()
                        .canonicalize(
                                new ProtosParser(characters).parseProgram());
    }

    private static Source source(String characters, String sourceName)
            throws Exception {
        return Source.newBuilder(
                        ProtosLanguage.ID,
                        characters,
                        sourceName)
                .build();
    }

    private static ProtosBytesValue bytes(
            ProtosPrelude prelude,
            int... values) {
        ProtosBytesValue bytes =
                new ProtosBytesValue(prelude.bytesPrototypeForRuntime());
        for (int value : values) {
            bytes.indexedAdd(
                    new ProtosIntegerValue(value));
        }
        return bytes;
    }

    private static void assertBytes(Object value, int... expected) {
        ProtosBytesValue bytes =
                assertInstanceOf(ProtosBytesValue.class, value);
        assertEquals(expected.length, bytes.indexedSize());
        for (int index = 0; index < expected.length; index++) {
            assertEquals(Long.toString(expected[index]), com.guillermomolina.protos.runtime.ProtosNumericValueSupport.integerDecimalText(bytes.indexedAt(index)));
        }
    }

    private static LanguageScope languageScope() {
        Context context = Context.newBuilder(ProtosLanguage.ID).build();
        context.initialize(ProtosLanguage.ID);
        context.enter();
        return new LanguageScope(context, LANGUAGE_REF.get(null));
    }

    private static final class Fixture {
        final ProtosPrelude prelude;
        final ProtosActorExecutionDomain domain;
        ProtosActivation module;
        final ProtosObjectValue source;

        Fixture(
                ProtosPrelude prelude,
                ProtosActorExecutionDomain domain,
                ProtosActivation module,
                ProtosObjectValue source) {
            this.prelude = prelude;
            this.domain = domain;
            this.module = module;
            this.source = source;
        }
    }

    private record LanguageScope(Context context, ProtosLanguage language)
            implements AutoCloseable {
        @Override
        public void close() {
            context.leave();
            context.close();
        }
    }
}
