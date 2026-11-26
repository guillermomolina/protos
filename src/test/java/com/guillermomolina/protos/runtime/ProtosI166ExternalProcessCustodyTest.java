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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosActorValueTransfer;
import com.guillermomolina.protos.runtime.ProtosArrayValue;
import com.guillermomolina.protos.runtime.ProtosByteIoFlow;
import com.guillermomolina.protos.runtime.ProtosEnvironmentValue;
import com.guillermomolina.protos.runtime.ProtosExternalChildValue;
import com.guillermomolina.protos.runtime.ProtosExternalChildValue.TerminalOutcome;
import com.guillermomolina.protos.runtime.ProtosExternalExecutionCapabilityValue;
import com.guillermomolina.protos.runtime.ProtosMapValue;
import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.guillermomolina.protos.runtime.ProtosNumericValueSupport;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosProcessRuntime;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.guillermomolina.protos.runtime.ProtosStringValue;
import com.guillermomolina.protos.runtime.numeric.ProtosNumericHashKey;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.io.IOAccess;
import org.junit.jupiter.api.Test;

/**
 * Process custody of external children and the retained terminal outcome, driven by a
 * controlled native child so that verified categories can be asserted without claiming that the
 * Java process backend observes them (EXTERNAL_PROCESS.md §§5, 8).
 */
final class ProtosI166ExternalProcessCustodyTest {
    private static final String CORE = Path.of("protos", "lib", "core").toAbsolutePath().toString();

    /**
     * Controlled child: records termination requests and handle releases; never touches the OS.
     * A stream listed in {@code uncertainRelease} reports an uncertain (failed) release.
     */
    private static final class ControlledChild implements ProtosExternalChildValue.NativeChild {
        final CompletableFuture<TerminalOutcome> termination;
        volatile CompletableFuture<Void> transport = CompletableFuture.completedFuture(null);
        final AtomicInteger terminationRequests = new AtomicInteger();
        final AtomicInteger forcedRequests = new AtomicInteger();
        final AtomicInteger closedStreams = new AtomicInteger();
        /** Counts physical release attempts, not observations of their retained outcomes. */
        final AtomicInteger releaseCalls = new AtomicInteger();
        final AtomicInteger releaseRequests = new AtomicInteger();
        private final java.util.Map<ProtosExternalChildValue.Stream, CompletableFuture<Boolean>> releases =
                new java.util.EnumMap<>(ProtosExternalChildValue.Stream.class);
        final java.util.Set<ProtosExternalChildValue.Stream> uncertainRelease =
                java.util.EnumSet.noneOf(ProtosExternalChildValue.Stream.class);
        final AtomicBoolean refuseForced = new AtomicBoolean();
        /** When set, serves STDOUT instead of the immediate-EOF lane. */
        volatile ProtosByteIoFlow.Backend stdoutLane;
        /**
         * Streams whose physical release stays pending until the test completes the listed
         * stage; every other stream keeps the immediate completion.
         */
        final java.util.Map<ProtosExternalChildValue.Stream, CompletableFuture<Boolean>> pendingRelease =
                new java.util.EnumMap<>(ProtosExternalChildValue.Stream.class);

        ControlledChild() {
            this(new CompletableFuture<>());
        }

        ControlledChild(CompletableFuture<TerminalOutcome> termination) {
            this.termination = termination;
        }

        @Override
        public ProtosByteIoFlow.Backend lane(ProtosExternalChildValue.Stream stream) {
            ProtosByteIoFlow.Backend controlled = stdoutLane;
            if (controlled != null && stream == ProtosExternalChildValue.Stream.STDOUT) {
                return controlled;
            }
            return new ProtosByteIoFlow.Backend() {
                @Override
                public ProtosByteIoFlow.Cancellation read(
                        int maxBytes, ProtosByteIoFlow.ReadCompletion completion) {
                    completion.eof();
                    return () -> {};
                }

                @Override
                public ProtosByteIoFlow.Cancellation write(
                        byte[] bytes, ProtosByteIoFlow.WriteCompletion completion) {
                    completion.succeeded();
                    return () -> {};
                }
            };
        }

        @Override
        public synchronized CompletionStage<Boolean> release(
                ProtosExternalChildValue.Stream stream, boolean discard) {
            releaseRequests.incrementAndGet();
            CompletableFuture<Boolean> previous = releases.get(stream);
            if (previous != null) {
                return previous;
            }
            releaseCalls.incrementAndGet();
            CompletableFuture<Boolean> pending = pendingRelease.get(stream);
            if (pending != null) {
                releases.put(stream, pending);
                return pending;
            }
            boolean confirmed = !uncertainRelease.contains(stream);
            if (confirmed) {
                closedStreams.incrementAndGet();
            }
            CompletableFuture<Boolean> outcome = CompletableFuture.completedFuture(confirmed);
            releases.put(stream, outcome);
            return outcome;
        }

        @Override
        public CompletionStage<TerminalOutcome> termination() {
            return termination;
        }

        @Override
        public CompletionStage<Void> transportRetired() {
            return transport;
        }

        @Override
        public void requestTermination() {
            terminationRequests.incrementAndGet();
        }

        @Override
        public void requestForcedTermination() throws IOException {
            forcedRequests.incrementAndGet();
            if (refuseForced.get()) {
                throw new IOException("controlled refusal");
            }
        }
    }

    private static Context granted() {
        return Context.newBuilder(ProtosLanguage.ID)
                .option("protos.CoreRoot", CORE)
                .option("protos.AllowExecution", "true")
                .allowCreateProcess(true)
                .allowIO(IOAccess.ALL)
                .build();
    }

    private static ProtosProcessRuntime process(Context context) {
        context.eval(ProtosLanguage.ID, "0");
        context.enter();
        try {
            return ProtosLanguageContext.current().embeddedProcessOrNull().processForTesting();
        } finally {
            context.leave();
        }
    }

    @Test
    void processTerminationMovesLiveChildToHostOnlyCleanupCustody() {
        ControlledChild nativeChild = new ControlledChild();
        ProtosExternalChildValue child;
        ProtosProcessRuntime process;
        try (Context context = granted()) {
            process = process(context);
            ProtosExternalExecutionCapabilityValue grant =
                    process.rootExecutionForRuntime().orElseThrow();
            child = grant.newChildForRuntime(process, nativeChild);
            assertTrue(process.registerExternalChildForRuntime(child));
            assertTrue(child.guestOperableForRuntime());
        }
        // Context close terminated the Process: guest access is revoked, a best-effort request
        // was submitted and the exclusively owned handles were closed, without waiting.
        assertFalse(child.guestOperableForRuntime());
        assertEquals(1, nativeChild.terminationRequests.get());
        assertEquals(3, nativeChild.closedStreams.get());
        // Each host close path (finalization, then disposal) waited a bounded time and escalated
        // once: a child that was not reaped keeps its cleanup obligation and is retried.
        assertEquals(2, nativeChild.forcedRequests.get());
        assertEquals(null, child.outcomeForRuntime());
        // The submitted forced requests established nothing: custody is still outstanding.
        assertTrue(process.externalCustodyOutstandingForRuntime());
        assertTrue(process.externalChildrenForRuntime().contains(child));

        // The single supervisor later reaps the child; the outcome is retained, and a repeated
        // request after the observed exit is a harmless no-op.
        nativeChild.termination.complete(TerminalOutcome.signaled());
        assertEquals(TerminalOutcome.signaled(), child.outcomeForRuntime());
        assertTrue(child.requestTerminationForRuntime());
        assertEquals(1, nativeChild.terminationRequests.get());
        // A later reclamation succeeds without a further forced request.
        assertTrue(process.reclaimExternalChildrenForHostClose(1));
        assertFalse(process.externalCustodyOutstandingForRuntime());
        assertEquals(2, nativeChild.forcedRequests.get());
    }

    @Test
    void registrationAfterTerminationKeepsCustodyButRefusesPublication() {
        ControlledChild nativeChild = new ControlledChild();
        try (Context context = granted()) {
            ProtosProcessRuntime process = process(context);
            ProtosExternalExecutionCapabilityValue grant =
                    process.rootExecutionForRuntime().orElseThrow();
            process.requestTerminationForRuntime();
            assertTrue(grant.retiredForRuntime());
            ProtosExternalChildValue child = grant.newChildForRuntime(process, nativeChild);
            assertFalse(process.registerExternalChildForRuntime(child));
            child.enterHostOnlyCustodyForRuntime();
            assertEquals(1, nativeChild.terminationRequests.get());
            // Reaped before Context close, so the host close has nothing left to reclaim.
            nativeChild.termination.complete(TerminalOutcome.unknown());
        }
    }

    @Test
    void verifiedOutcomesMaterializeExactlyTwoVariants() {
        ControlledChild nativeChild = new ControlledChild();
        try (Context context = granted()) {
            ProtosProcessRuntime process = process(context);
            ProtosExternalChildValue child =
                    process.rootExecutionForRuntime().orElseThrow()
                            .newChildForRuntime(process, nativeChild);

            ProtosExternalChildValue.Status normal = child.statusForRuntime(TerminalOutcome.exited(3));
            assertTrue(normal.exited());
            assertFalse(normal.signaled());
            assertEquals(ProtosNumericValueSupport.integer(3), normal.exitCode());

            ProtosExternalChildValue.Status zero = child.statusForRuntime(TerminalOutcome.exited(0));
            assertEquals(ProtosNumericValueSupport.integer(0), zero.exitCode());

            ProtosExternalChildValue.Status signal = child.statusForRuntime(TerminalOutcome.signaled());
            assertFalse(signal.exited());
            assertTrue(signal.signaled());
            assertSame(ProtosNullValue.INSTANCE, signal.exitCode());

            // No ExitStatus exists for an outcome whose category is not verified.
            assertThrows(IllegalArgumentException.class,
                    () -> child.statusForRuntime(TerminalOutcome.unknown()));
        }
    }

    @Test
    void outcomeIsRetainedForRepeatedObservation() {
        ControlledChild nativeChild = new ControlledChild();
        try (Context context = granted()) {
            ProtosProcessRuntime process = process(context);
            ProtosExternalChildValue child =
                    process.rootExecutionForRuntime().orElseThrow()
                            .newChildForRuntime(process, nativeChild);
            assertTrue(process.registerExternalChildForRuntime(child));
            nativeChild.termination.complete(TerminalOutcome.exited(7));
            assertEquals(TerminalOutcome.exited(7), child.outcomeForRuntime());
            assertEquals(TerminalOutcome.exited(7), child.outcomeForRuntime());
            assertTrue(child.guestOperableForRuntime());
        }
    }

    @Test
    void exitedChildWithOwnedEndpointsStaysInCustodyUntilTheirRelease() {
        ControlledChild nativeChild = new ControlledChild();
        ProtosExternalChildValue child;
        ProtosProcessRuntime process;
        try (Context context = granted()) {
            process = process(context);
            child = process.rootExecutionForRuntime().orElseThrow()
                    .newChildForRuntime(process, nativeChild);
            assertTrue(process.registerExternalChildForRuntime(child));
            // Exit is not EOF: the outcome is retained, but the handles are still owned, so the
            // child is not released and its endpoints stay readable.
            nativeChild.termination.complete(TerminalOutcome.exited(0));
            assertEquals(TerminalOutcome.exited(0), child.outcomeForRuntime());
            assertFalse(child.custodyReleasedForRuntime());
            assertTrue(process.externalChildrenForRuntime().contains(child));
            for (ProtosExternalChildValue.Stream stream : ProtosExternalChildValue.Stream.values()) {
                assertTrue(child.endpointHandleHeldForRuntime(stream));
            }
            assertEquals(0, nativeChild.closedStreams.get());
            assertTrue(child.guestOperableForRuntime());
        }
        // Process termination closed every owned handle of the exited child (no termination
        // request: it already exited) and only then discharged custody.
        assertEquals(3, nativeChild.closedStreams.get());
        assertEquals(0, nativeChild.terminationRequests.get());
        assertTrue(child.custodyReleasedForRuntime());
        assertFalse(process.externalChildrenForRuntime().contains(child));
    }

    @Test
    void registrationAfterExitStillTracksTheChild() {
        ControlledChild nativeChild = new ControlledChild();
        try (Context context = granted()) {
            ProtosProcessRuntime process = process(context);
            ProtosExternalChildValue child = process.rootExecutionForRuntime().orElseThrow()
                    .newChildForRuntime(process, nativeChild);
            nativeChild.termination.complete(TerminalOutcome.unknown());
            assertTrue(process.registerExternalChildForRuntime(child));
            assertTrue(process.externalChildrenForRuntime().contains(child));
        }
        assertEquals(3, nativeChild.closedStreams.get());
    }

    @Test
    void failedForcedTerminationKeepsTheCleanupObligation() {
        ControlledChild nativeChild = new ControlledChild();
        nativeChild.refuseForced.set(true);
        try (Context context = granted()) {
            ProtosProcessRuntime process = process(context);
            ProtosExternalChildValue child = process.rootExecutionForRuntime().orElseThrow()
                    .newChildForRuntime(process, nativeChild);
            assertTrue(process.registerExternalChildForRuntime(child));

            assertFalse(child.reclaimForHostCloseRuntime(1));
            assertFalse(child.hostCloseReclaimedForRuntime());
            assertTrue(process.externalChildrenForRuntime().contains(child));
            assertEquals(1, nativeChild.forcedRequests.get());

            // A later host close path retries the pending obligation.
            nativeChild.refuseForced.set(false);
            nativeChild.termination.complete(TerminalOutcome.signaled());
            assertTrue(child.reclaimForHostCloseRuntime(1));
            assertTrue(child.hostCloseReclaimedForRuntime());
            assertTrue(child.reclaimForHostCloseRuntime(1));
            assertEquals(1, nativeChild.forcedRequests.get());
            assertTrue(child.custodyReleasedForRuntime());
            assertFalse(process.externalChildrenForRuntime().contains(child));
        }
    }

    @Test
    void unpublishedChildIsClosedAndForcedAtOnce() {
        ControlledChild nativeChild = new ControlledChild();
        try (Context context = granted()) {
            ProtosProcessRuntime process = process(context);
            ProtosExternalChildValue child = process.rootExecutionForRuntime().orElseThrow()
                    .newChildForRuntime(process, nativeChild);
            assertTrue(child.abandonUnpublishedForRuntime());
            assertFalse(child.guestOperableForRuntime());
            assertEquals(3, nativeChild.closedStreams.get());
            assertEquals(1, nativeChild.forcedRequests.get());
            nativeChild.termination.complete(TerminalOutcome.unknown());
            assertTrue(child.custodyReleasedForRuntime());
        }
    }

    @Test
    void closingOneContextNeverReclaimsAnotherContextsChild() {
        ControlledChild nativeChild = new ControlledChild();
        try (Context owner = granted()) {
            ProtosProcessRuntime process = process(owner);
            ProtosExternalChildValue child = process.rootExecutionForRuntime().orElseThrow()
                    .newChildForRuntime(process, nativeChild);
            assertTrue(process.registerExternalChildForRuntime(child));
            try (Context other = granted()) {
                assertNotNull(process(other).rootExecutionForRuntime().orElseThrow());
            }
            assertEquals(0, nativeChild.terminationRequests.get());
            assertEquals(0, nativeChild.closedStreams.get());
            assertTrue(child.guestOperableForRuntime());
            nativeChild.termination.complete(TerminalOutcome.exited(0));
        }
    }

    @Test
    void launchAdmissionIsAtomicWithRevocationAndAwaitedAtHostClose() {
        try (Context context = granted()) {
            ProtosExternalExecutionCapabilityValue grant =
                    process(context).rootExecutionForRuntime().orElseThrow();
            assertTrue(grant.beginLaunchForRuntime());
            grant.retireForRuntime();
            assertFalse(grant.beginLaunchForRuntime());
            // A committed attempt admitted before revocation is still awaited by the host close.
            assertFalse(grant.awaitLaunchesForHostClose(1));
            grant.endLaunchForRuntime();
            assertTrue(grant.awaitLaunchesForHostClose(1));
        }
    }

    @Test
    void unusedGrantCreatesNoWorker() {
        try (Context context = granted()) {
            ProtosExternalExecutionCapabilityValue grant =
                    process(context).rootExecutionForRuntime().orElseThrow();
            assertFalse(grant.executorInitializedForTesting());
            assertEquals(0, grant.workerCountForTesting());
        }
    }

    @Test
    void workersAreBoundedAndExcessWorkIsRejected() throws Exception {
        try (Context context = granted()) {
            ProtosExternalExecutionCapabilityValue grant =
                    process(context).rootExecutionForRuntime().orElseThrow();
            CountDownLatch release = new CountDownLatch(1);
            CountDownLatch running =
                    new CountDownLatch(ProtosExternalExecutionCapabilityValue.MAX_WORKERS);
            int admitted = ProtosExternalExecutionCapabilityValue.MAX_WORKERS
                    + ProtosExternalExecutionCapabilityValue.MAX_QUEUED;
            CountDownLatch completed = new CountDownLatch(admitted);
            Runnable blocked = () -> {
                running.countDown();
                try {
                    release.await();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
                completed.countDown();
            };
            for (int index = 0; index < admitted; index++) {
                assertTrue(grant.executeForRuntime(blocked));
            }
            assertTrue(running.await(10, TimeUnit.SECONDS));
            // Capacity, not growth: the next submission is rejected and no extra worker exists.
            assertFalse(grant.executeForRuntime(blocked));
            assertEquals(ProtosExternalExecutionCapabilityValue.MAX_WORKERS,
                    grant.workerCountForTesting());
            // Worker retirement rejects new work but completes queued and running work.
            grant.retireWorkersForHostClose();
            assertFalse(grant.executeForRuntime(() -> {}));
            release.countDown();
            assertTrue(completed.await(10, TimeUnit.SECONDS));
        }
    }

    @Test
    void environmentDuplicatesFollowTheHostNativeNameDomain() {
        ProtosEnvironmentValue.NativeNameDomain folding = domain(
                (left, right) -> left.toUpperCase(Locale.ROOT).equals(right.toUpperCase(Locale.ROOT)));
        ProtosEnvironmentValue.NativeNameDomain undecidable = domain((left, right) -> {
            throw new IllegalStateException("cannot decide");
        });
        ProtosMapValue mixed = environment("Path", "PATH");
        ProtosArrayValue arguments = new ProtosArrayValue(ProtosObjectValue.rootObject(), List.of());
        ProtosStringValue program = new ProtosStringValue("/bin/true");

        // The production domain of this host decides; no rule is invented by the facility.
        assertEquals(
                ProtosStandaloneHostedExecution.HOST_ENVIRONMENT_NAME_DOMAIN.sameCapturedName("Path", "PATH"),
                ProtosExternalExecutionHostFacility.snapshot(
                        ProtosStandaloneHostedExecution.HOST_ENVIRONMENT_NAME_DOMAIN,
                        program, arguments, mixed, ProtosNullValue.INSTANCE) == null);
        assertNull(ProtosExternalExecutionHostFacility.snapshot(
                folding, program, arguments, mixed, ProtosNullValue.INSTANCE));
        assertNull(ProtosExternalExecutionHostFacility.snapshot(
                undecidable, program, arguments, mixed, ProtosNullValue.INSTANCE));
        assertNotNull(ProtosExternalExecutionHostFacility.snapshot(
                folding, program, arguments, environment("A", "B"), ProtosNullValue.INSTANCE));
    }

    @Test
    void exitStatusCrossesActorsAsInertTerminalData() throws Exception {
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(Path.of(CORE));
        ProtosActivation source = prelude.newModuleActivation();
        ProtosActivation destination = prelude.newModuleActivation();
        for (TerminalOutcome outcome : List.of(
                TerminalOutcome.exited(3), TerminalOutcome.exited(0), TerminalOutcome.signaled())) {
            ProtosExternalChildValue.Status status =
                    ProtosExternalChildValue.Status.materializeForRuntime(prelude, outcome);
            Object snapshot = ProtosActorValueTransfer.snapshotValue(status, source);
            Object transferred = ProtosActorValueTransfer.materializeValue(snapshot, destination);
            ProtosExternalChildValue.Status copy =
                    assertInstanceOf(ProtosExternalChildValue.Status.class, transferred);
            // A fresh identity carrying only the terminal data; no process authority exists.
            assertFalse(copy == status);
            assertEquals(status.exited(), copy.exited());
            assertEquals(status.signaled(), copy.signaled());
            assertEquals(status.exitCode(), copy.exitCode());
            assertEquals(outcome, copy.outcomeForRuntime());
        }
        // Aliases inside one graph keep one identity.
        ProtosExternalChildValue.Status shared =
                ProtosExternalChildValue.Status.materializeForRuntime(prelude, TerminalOutcome.exited(1));
        List<?> copied = ProtosActorValueTransfer.materializeArguments(
                ProtosActorValueTransfer.snapshotArguments(List.of(shared, shared), source),
                destination);
        assertSame(copied.get(0), copied.get(1));
    }

    @Test
    void childrenNeverCrossActorsAndGrantsNeverCrossOutsideExplicitDelegation() {
        ControlledChild nativeChild = new ControlledChild();
        try (Context context = granted()) {
            ProtosProcessRuntime process = process(context);
            ProtosExternalExecutionCapabilityValue grant = process.rootExecutionForRuntime().orElseThrow();
            ProtosExternalChildValue child = grant.newChildForRuntime(process, nativeChild);
            ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(Path.of(CORE));
            ProtosActivation source = prelude.newModuleActivation();
            for (Object value : List.of(grant, child)) {
                assertThrows(ProtosSignalException.class,
                        () -> ProtosActorValueTransfer.snapshotValue(value, source));
                ProtosObjectValue holder = new ProtosObjectValue(ProtosObjectValue.rootObject());
                holder.createLocalSlot("held", value);
                assertThrows(ProtosSignalException.class,
                        () -> ProtosActorValueTransfer.snapshotSpawnArguments(List.of(holder), source));
            }
            nativeChild.termination.complete(TerminalOutcome.unknown());
        } catch (IOException bootstrapFailure) {
            throw new AssertionError(bootstrapFailure);
        }
    }

    @Test
    void exitStatusEntersParallelExecutionAsInertTerminalData() throws Exception {
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(Path.of(CORE));
        ProtosActivation activation = prelude.newModuleActivation();
        ProtosExternalChildValue.Status status =
                ProtosExternalChildValue.Status.materializeForRuntime(prelude, TerminalOutcome.signaled());
        Method copy = parallelCopy();
        Object descriptor = copy.invoke(null, status, activation, transferMemo(false));
        assertInstanceOf(TerminalOutcome.class, descriptor);
        Object materialized = copy.invoke(null, descriptor, activation, transferMemo(true));
        ProtosExternalChildValue.Status copyStatus =
                assertInstanceOf(ProtosExternalChildValue.Status.class, materialized);
        assertTrue(copyStatus.signaled());
        assertSame(ProtosNullValue.INSTANCE, copyStatus.exitCode());
        // Outside a P transfer stage the status fails closed.
        InvocationTargetException outside = assertThrows(InvocationTargetException.class,
                () -> copy.invoke(null, status, activation, new IdentityHashMap<Object, Object>()));
        assertEquals("NonParallel", outside.getCause().getClass().getSimpleName());
    }

    @Test
    void committedLaunchTimeoutKeepsTheCloseOpenAndIsReclaimedLater() {
        ControlledChild nativeChild = new ControlledChild();
        try (Context context = granted()) {
            ProtosProcessRuntime process = process(context);
            ProtosExternalExecutionCapabilityValue grant =
                    process.rootExecutionForRuntime().orElseThrow();
            // A launch committed before the close is still in flight.
            assertTrue(grant.beginLaunchForRuntime());
            assertFalse(process.reclaimExternalChildrenForHostClose(1));
            // A timeout proves nothing: the workers that may finish the launch stay available.
            assertTrue(grant.executeForRuntime(() -> {}));

            // The launch completes: native custody first, then the wrapper takes it over.
            process.admitNativeChildForRuntime(nativeChild);
            ProtosExternalChildValue child = grant.newChildForRuntime(process, nativeChild);
            process.registerExternalChildForRuntime(child);
            grant.endLaunchForRuntime();
            assertTrue(process.externalChildrenForRuntime().contains(child));

            // A forced request without a verified reap is not reclamation.
            assertFalse(process.reclaimExternalChildrenForHostClose(1));
            assertTrue(process.externalChildrenForRuntime().contains(child));
            nativeChild.termination.complete(TerminalOutcome.unknown());
            assertTrue(process.reclaimExternalChildrenForHostClose(1));
            // Only a complete reclamation retires the workers.
            assertFalse(grant.executeForRuntime(() -> {}));
        }
    }

    @Test
    void nativeChildIsOwnedBeforeItsWrapperAndHandedOverWithoutGap() {
        ControlledChild nativeChild = new ControlledChild();
        try (Context context = granted()) {
            ProtosProcessRuntime process = process(context);
            process.admitNativeChildForRuntime(nativeChild);
            assertTrue(process.nativeChildrenForRuntime().contains(nativeChild));
            assertTrue(process.externalCustodyOutstandingForRuntime());
            ProtosExternalChildValue child = process.rootExecutionForRuntime().orElseThrow()
                    .newChildForRuntime(process, nativeChild);
            assertTrue(process.registerExternalChildForRuntime(child));
            // One atomic step moved the obligation: exactly one registry owns the child.
            assertFalse(process.nativeChildrenForRuntime().contains(nativeChild));
            assertTrue(process.externalChildrenForRuntime().contains(child));
            nativeChild.termination.complete(TerminalOutcome.unknown());
        }
    }

    @Test
    void wrapperFailureAfterNativeCreationKeepsCustodyUntilVerifiedReap() {
        ControlledChild nativeChild = new ControlledChild();
        try (Context context = granted()) {
            ProtosProcessRuntime process = process(context);
            ProtosExternalExecutionCapabilityValue grant =
                    process.rootExecutionForRuntime().orElseThrow();
            ProtosExternalExecutionCapabilityValue.Admission admission =
                    grant.reserveChildForRuntime();
            assertNotNull(admission);
            process.admitNativeChildForRuntime(nativeChild);
            process.abandonNativeChildForRuntime(nativeChild, admission::release);
            assertEquals(1, nativeChild.forcedRequests.get());
            assertEquals(3, nativeChild.closedStreams.get());
            // The forced request alone keeps the child and its slot in custody.
            assertTrue(process.nativeChildrenForRuntime().contains(nativeChild));
            assertEquals(1, grant.admittedChildrenForRuntime());
            // Abandonment is idempotent: no second forced request.
            process.abandonNativeChildForRuntime(nativeChild, admission::release);
            assertEquals(1, nativeChild.forcedRequests.get());
            nativeChild.termination.complete(TerminalOutcome.unknown());
            assertFalse(process.nativeChildrenForRuntime().contains(nativeChild));
            assertEquals(0, grant.admittedChildrenForRuntime());
        }
    }

    @Test
    void supervisorFailureIsNeverAReap() {
        ControlledChild nativeChild = new ControlledChild();
        ProtosProcessRuntime process;
        ProtosExternalChildValue child;
        try (Context context = granted()) {
            process = process(context);
            child = process.rootExecutionForRuntime().orElseThrow()
                    .newChildForRuntime(process, nativeChild);
            assertTrue(process.registerExternalChildForRuntime(child));
            nativeChild.termination.completeExceptionally(new IOException("supervisor lost"));
            assertTrue(child.supervisionFailedForRuntime());
            assertFalse(child.reapedForRuntime());
            assertNull(child.outcomeForRuntime());
            child.enterHostOnlyCustodyForRuntime();
            // Handles released, but without a verified reap custody is retained.
            assertFalse(child.custodyReleasedForRuntime());
            assertFalse(child.reclaimForHostCloseRuntime(1));
            assertTrue(process.externalChildrenForRuntime().contains(child));
        }
        assertTrue(process.externalCustodyOutstandingForRuntime());
    }

    @Test
    void unknownOutcomeWithVerifiedReapDischargesCustody() {
        ControlledChild nativeChild = new ControlledChild();
        try (Context context = granted()) {
            ProtosProcessRuntime process = process(context);
            ProtosExternalChildValue child = process.rootExecutionForRuntime().orElseThrow()
                    .newChildForRuntime(process, nativeChild);
            assertTrue(process.registerExternalChildForRuntime(child));
            nativeChild.termination.complete(TerminalOutcome.unknown());
            assertTrue(child.reapedForRuntime());
            assertFalse(child.supervisionFailedForRuntime());
            assertTrue(child.reclaimForHostCloseRuntime(1));
            assertTrue(child.custodyReleasedForRuntime());
            assertEquals(0, nativeChild.forcedRequests.get());
        }
    }

    @Test
    void uncertainHandleReleaseRetainsCustodyAndIsNeverRetried() {
        ControlledChild nativeChild = new ControlledChild();
        nativeChild.uncertainRelease.add(ProtosExternalChildValue.Stream.STDOUT);
        ProtosProcessRuntime process;
        ProtosExternalChildValue child;
        try (Context context = granted()) {
            process = process(context);
            child = process.rootExecutionForRuntime().orElseThrow()
                    .newChildForRuntime(process, nativeChild);
            assertTrue(process.registerExternalChildForRuntime(child));
            nativeChild.termination.complete(TerminalOutcome.unknown());
            child.enterHostOnlyCustodyForRuntime();
            assertTrue(child.endpointReleaseUncertainForRuntime(ProtosExternalChildValue.Stream.STDOUT));
            assertTrue(child.endpointHandleHeldForRuntime(ProtosExternalChildValue.Stream.STDOUT));
            assertFalse(child.custodyReleasedForRuntime());
            assertFalse(child.reclaimForHostCloseRuntime(1));
            assertFalse(child.reclaimForHostCloseRuntime(1));
            // Three releases, one per stream: the uncertain one was never attempted again.
            assertEquals(3, nativeChild.releaseCalls.get());
            assertTrue(process.externalChildrenForRuntime().contains(child));
        }
        assertTrue(process.externalCustodyOutstandingForRuntime());
        assertEquals(3, nativeChild.releaseCalls.get());
    }

    @Test
    void registrationDuringAClosePendingLaunchIsSeenByTheNextSnapshot() throws Exception {
        ControlledChild first = new ControlledChild();
        ControlledChild second = new ControlledChild();
        try (Context context = granted()) {
            ProtosProcessRuntime process = process(context);
            ProtosExternalExecutionCapabilityValue grant =
                    process.rootExecutionForRuntime().orElseThrow();
            ProtosExternalChildValue registered = grant.newChildForRuntime(process, first);
            process.registerExternalChildForRuntime(registered);
            first.termination.complete(TerminalOutcome.unknown());
            assertTrue(grant.beginLaunchForRuntime());

            CountDownLatch admitted = new CountDownLatch(1);
            CountDownLatch proceed = new CountDownLatch(1);
            Thread launcher = new Thread(() -> {
                process.admitNativeChildForRuntime(second);
                admitted.countDown();
                try {
                    proceed.await();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
                process.registerExternalChildForRuntime(grant.newChildForRuntime(process, second));
                grant.endLaunchForRuntime();
            });
            launcher.start();
            assertTrue(admitted.await(10, TimeUnit.SECONDS));
            // The close cannot take a definitive snapshot while the launch is in flight.
            assertFalse(process.reclaimExternalChildrenForHostClose(1));
            proceed.countDown();
            launcher.join(10_000);
            // Both children are tracked: none was lost between the two snapshots.
            assertEquals(2, process.externalChildrenForRuntime().size());
            assertTrue(process.nativeChildrenForRuntime().isEmpty());
            second.termination.complete(TerminalOutcome.unknown());
            assertTrue(process.reclaimExternalChildrenForHostClose(1));
            assertFalse(process.externalCustodyOutstandingForRuntime());
        }
    }

    @Test
    void childAdmissionIsFiniteAndReturnedOnlyWhenQuiescent() {
        try (Context context = granted()) {
            ProtosProcessRuntime process = process(context);
            ProtosExternalExecutionCapabilityValue grant =
                    process.rootExecutionForRuntime().orElseThrow();
            List<ProtosExternalExecutionCapabilityValue.Admission> slots = new java.util.ArrayList<>();
            for (int index = 0; index < ProtosExternalExecutionCapabilityValue.MAX_CHILDREN; index++) {
                slots.add(assertInstanceOf(
                        ProtosExternalExecutionCapabilityValue.Admission.class,
                        grant.reserveChildForRuntime()));
            }
            assertNull(grant.reserveChildForRuntime());
            ControlledChild nativeChild = new ControlledChild();
            ProtosExternalChildValue child =
                    grant.newChildForRuntime(process, nativeChild, slots.get(0));
            assertTrue(process.registerExternalChildForRuntime(child));
            nativeChild.termination.complete(TerminalOutcome.unknown());
            assertNull(grant.reserveChildForRuntime());
            child.enterHostOnlyCustodyForRuntime();
            assertTrue(child.custodyReleasedForRuntime());
            ProtosExternalExecutionCapabilityValue.Admission returned = grant.reserveChildForRuntime();
            assertNotNull(returned);
            returned.release();
            for (ProtosExternalExecutionCapabilityValue.Admission slot : slots) {
                slot.release();
                slot.release();
            }
        }
    }

    @Test
    void failedAbandonmentIsRetriedAndDischargedExactlyOnce() {
        ControlledChild nativeChild = new ControlledChild();
        try (Context context = granted()) {
            ProtosProcessRuntime process = process(context);
            ProtosExternalExecutionCapabilityValue grant =
                    process.rootExecutionForRuntime().orElseThrow();
            ProtosExternalExecutionCapabilityValue.Admission admission =
                    grant.reserveChildForRuntime();
            assertNotNull(admission);
            AtomicInteger discharges = new AtomicInteger();
            process.admitNativeChildForRuntime(nativeChild);
            // The first attempt cannot observe its transport retired: it completes without
            // discharge, after a verified reap.
            nativeChild.transport = CompletableFuture.failedFuture(new IOException("transport lost"));
            process.abandonNativeChildForRuntime(nativeChild, () -> {
                discharges.incrementAndGet();
                admission.release();
            });
            nativeChild.termination.complete(TerminalOutcome.unknown());
            assertTrue(process.nativeChildrenForRuntime().contains(nativeChild));
            assertEquals(1, grant.admittedChildrenForRuntime());
            assertEquals(0, discharges.get());
            assertEquals(1, nativeChild.forcedRequests.get());

            // A later attempt, even without its own obligation, repeats the termination request,
            // observes the same single supervisor outcome and runs the retained obligation once.
            nativeChild.transport = CompletableFuture.completedFuture(null);
            process.abandonNativeChildForRuntime(nativeChild, null);
            assertEquals(2, nativeChild.forcedRequests.get());
            assertFalse(process.nativeChildrenForRuntime().contains(nativeChild));
            assertEquals(1, discharges.get());
            assertEquals(0, grant.admittedChildrenForRuntime());

            // Discharged custody is never abandoned again.
            process.abandonNativeChildForRuntime(nativeChild, discharges::incrementAndGet);
            assertEquals(2, nativeChild.forcedRequests.get());
            assertEquals(1, discharges.get());
        }
    }

    @Test
    void abandonmentInProgressIsReusedNotRepeated() {
        ControlledChild nativeChild = new ControlledChild();
        try (Context context = granted()) {
            ProtosProcessRuntime process = process(context);
            process.admitNativeChildForRuntime(nativeChild);
            process.abandonNativeChildForRuntime(nativeChild, null);
            process.abandonNativeChildForRuntime(nativeChild, null);
            process.abandonNativeChildForRuntime(nativeChild, null);
            // The supervisor has not answered: one attempt, one forced request, three releases.
            assertEquals(1, nativeChild.forcedRequests.get());
            assertEquals(3, nativeChild.releaseCalls.get());
            nativeChild.termination.complete(TerminalOutcome.unknown());
            assertFalse(process.nativeChildrenForRuntime().contains(nativeChild));
        }
    }

    @Test
    void failedSupervisorKeepsNativeCustodyOnEveryRetry() {
        ControlledChild nativeChild = new ControlledChild();
        ProtosProcessRuntime process;
        ProtosExternalExecutionCapabilityValue grant;
        try (Context context = granted()) {
            process = process(context);
            grant = process.rootExecutionForRuntime().orElseThrow();
            ProtosExternalExecutionCapabilityValue.Admission admission =
                    grant.reserveChildForRuntime();
            process.admitNativeChildForRuntime(nativeChild);
            nativeChild.termination.completeExceptionally(new IOException("supervisor lost"));
            process.abandonNativeChildForRuntime(nativeChild, admission::release);
            process.abandonNativeChildForRuntime(nativeChild, null);
            assertFalse(process.reclaimExternalChildrenForHostClose(1));
            assertTrue(process.nativeChildrenForRuntime().contains(nativeChild));
            assertEquals(1, grant.admittedChildrenForRuntime());
            assertFalse(grant.hostCustodyDischargedForRuntime());
        }
        assertTrue(process.externalCustodyOutstandingForRuntime());
    }

    @Test
    void uncertainNativeReleaseKeepsCustodyAcrossRetries() {
        ControlledChild nativeChild = new ControlledChild();
        nativeChild.uncertainRelease.add(ProtosExternalChildValue.Stream.STDERR);
        try (Context context = granted()) {
            ProtosProcessRuntime process = process(context);
            process.admitNativeChildForRuntime(nativeChild);
            nativeChild.termination.complete(TerminalOutcome.unknown());
            process.abandonNativeChildForRuntime(nativeChild, null);
            assertTrue(process.nativeChildrenForRuntime().contains(nativeChild));
            assertFalse(process.reclaimExternalChildrenForHostClose(1));
            assertTrue(process.nativeChildrenForRuntime().contains(nativeChild));
            assertTrue(process.externalCustodyOutstandingForRuntime());
        }
    }

    @Test
    void launchRefusedAfterRevocationStaysUncommittedAndCreatesNoChild() {
        try (Context context = granted()) {
            ProtosProcessRuntime process = process(context);
            ProtosExternalExecutionCapabilityValue grant =
                    process.rootExecutionForRuntime().orElseThrow();
            ProtosExternalExecutionCapabilityValue.Admission admission =
                    grant.reserveChildForRuntime();
            assertNotNull(admission);
            grant.retireForRuntime();
            // Admission of the attempt is refused before any commitment: no launch in flight,
            // no child, and the reserved slot comes back.
            assertFalse(grant.beginLaunchForRuntime());
            admission.release();
            assertTrue(grant.hostCustodyDischargedForRuntime());
            assertTrue(process.nativeChildrenForRuntime().isEmpty());
            assertTrue(process.reclaimExternalChildrenForHostClose(1));
        }
    }

    /**
     * The last outstanding obligation is a pump: the child is reaped and its three handles are
     * confirmed released, yet it stays registered, its slot stays reserved, a host close is not
     * complete and the workers stay alive until the transport retires.
     */
    @Test
    void livePumpIsTheLastObligationAndKeepsCustody() {
        ControlledChild nativeChild = new ControlledChild();
        CompletableFuture<Void> pumps = new CompletableFuture<>();
        nativeChild.transport = pumps;
        try (Context context = granted()) {
            ProtosProcessRuntime process = process(context);
            ProtosExternalExecutionCapabilityValue grant =
                    process.rootExecutionForRuntime().orElseThrow();
            ProtosExternalExecutionCapabilityValue.Admission admission =
                    grant.reserveChildForRuntime();
            assertNotNull(admission);
            ProtosExternalChildValue child =
                    grant.newChildForRuntime(process, nativeChild, admission);
            assertTrue(process.registerExternalChildForRuntime(child));
            nativeChild.termination.complete(TerminalOutcome.unknown());
            child.enterHostOnlyCustodyForRuntime();
            assertTrue(child.reapedForRuntime());
            for (ProtosExternalChildValue.Stream stream : ProtosExternalChildValue.Stream.values()) {
                assertFalse(child.endpointHandleHeldForRuntime(stream));
            }
            // Reap and three confirmed releases are not enough while a pump is alive.
            assertFalse(child.custodyReleasedForRuntime());
            assertTrue(process.externalChildrenForRuntime().contains(child));
            assertEquals(1, grant.admittedChildrenForRuntime());
            assertFalse(child.reclaimForHostCloseRuntime(1));
            assertFalse(process.reclaimExternalChildrenForHostClose(1));
            assertTrue(process.externalCustodyOutstandingForRuntime());
            // The workers were not retired before the transport retired.
            assertTrue(grant.executeForRuntime(() -> {}));

            // The last pump exits: custody is left and the slot returned exactly once.
            pumps.complete(null);
            assertTrue(child.custodyReleasedForRuntime());
            assertFalse(process.externalChildrenForRuntime().contains(child));
            assertEquals(0, grant.admittedChildrenForRuntime());
            admission.release();
            assertEquals(0, grant.admittedChildrenForRuntime());
            assertTrue(process.reclaimExternalChildrenForHostClose(1));
            assertFalse(process.externalCustodyOutstandingForRuntime());
            assertFalse(grant.executeForRuntime(() -> {}));
        }
    }

    /** A reserved admission alone keeps the host close incomplete, with empty registries. */
    @Test
    void reservedAdmissionAloneKeepsTheHostCloseIncomplete() {
        try (Context context = granted()) {
            ProtosProcessRuntime process = process(context);
            ProtosExternalExecutionCapabilityValue grant =
                    process.rootExecutionForRuntime().orElseThrow();
            ProtosExternalExecutionCapabilityValue.Admission admission =
                    grant.reserveChildForRuntime();
            assertNotNull(admission);
            assertTrue(process.externalChildrenForRuntime().isEmpty());
            assertTrue(process.nativeChildrenForRuntime().isEmpty());
            assertTrue(process.externalCustodyOutstandingForRuntime());
            assertFalse(process.reclaimExternalChildrenForHostClose(1));
            assertTrue(grant.executeForRuntime(() -> {}));
            admission.release();
            assertTrue(process.reclaimExternalChildrenForHostClose(1));
        }
    }

    /**
     * Abandonment of a native child whose wrapper failed: the observable outcome, and with it the
     * retained obligation that carries the start failure, stays pending through the reap and the
     * releases until the transport also retired, and then runs exactly once.
     */
    @Test
    void abandonmentOutcomeWaitsForReapReleaseAndTransport() throws Exception {
        ControlledChild nativeChild = new ControlledChild();
        CompletableFuture<Void> pumps = new CompletableFuture<>();
        nativeChild.transport = pumps;
        try (Context context = granted()) {
            ProtosProcessRuntime process = process(context);
            ProtosExternalExecutionCapabilityValue grant =
                    process.rootExecutionForRuntime().orElseThrow();
            ProtosExternalExecutionCapabilityValue.Admission admission =
                    grant.reserveChildForRuntime();
            AtomicInteger published = new AtomicInteger();
            process.admitNativeChildForRuntime(nativeChild);
            CompletableFuture<Boolean> outcome =
                    process.abandonNativeChildForRuntime(nativeChild, () -> {
                        admission.release();
                        published.incrementAndGet();
                    });
            assertFalse(outcome.isDone());
            assertEquals(0, published.get());
            // A second request during the attempt reuses the very same outcome.
            assertSame(outcome, process.abandonNativeChildForRuntime(nativeChild, null));
            nativeChild.termination.complete(TerminalOutcome.unknown());
            assertFalse(outcome.isDone());
            assertEquals(0, published.get());
            assertEquals(1, grant.admittedChildrenForRuntime());
            pumps.complete(null);
            assertTrue(outcome.get(10, TimeUnit.SECONDS));
            assertEquals(1, published.get());
            assertEquals(0, grant.admittedChildrenForRuntime());
            assertEquals(1, nativeChild.forcedRequests.get());
            // Discharged custody answers at once and never runs an obligation again.
            assertTrue(process.abandonNativeChildForRuntime(nativeChild, published::incrementAndGet)
                    .get(10, TimeUnit.SECONDS));
            assertEquals(1, published.get());
        }
    }

    /**
     * A failed supervisor never lets the start failure be published: the attempt ends without
     * discharge, the obligation that would publish it is retained, and nothing runs.
     */
    @Test
    void failedSupervisorNeverPublishesTheStartFailure() throws Exception {
        ControlledChild nativeChild = new ControlledChild();
        try (Context context = granted()) {
            ProtosProcessRuntime process = process(context);
            AtomicInteger published = new AtomicInteger();
            process.admitNativeChildForRuntime(nativeChild);
            nativeChild.termination.completeExceptionally(new IOException("supervisor lost"));
            CompletableFuture<Boolean> outcome =
                    process.abandonNativeChildForRuntime(nativeChild, published::incrementAndGet);
            assertFalse(outcome.get(10, TimeUnit.SECONDS));
            assertEquals(0, published.get());
            assertTrue(process.nativeChildrenForRuntime().contains(nativeChild));
            // A retry observes the same failed supervisor and still publishes nothing.
            assertFalse(process.abandonNativeChildForRuntime(nativeChild, null)
                    .get(10, TimeUnit.SECONDS));
            assertEquals(0, published.get());
        }
    }

    /** A child belongs to the Actor domain it was published into, and to no other. */
    @Test
    void childIsOwnedOnlyByItsPublishingActorDomain() throws Exception {
        ProtosPrelude prelude = new ProtosCoreBootstrap()
                .bootstrap(Path.of(CORE));
        ProtosActivation origin = prelude.newModuleActivation();
        ProtosActivation other = prelude.newModuleActivation();
        ControlledChild published = new ControlledChild();
        ControlledChild unpublished = new ControlledChild();
        try (Context context = granted()) {
            ProtosProcessRuntime process = process(context);
            ProtosExternalExecutionCapabilityValue grant =
                    process.rootExecutionForRuntime().orElseThrow();
            ProtosExternalChildValue child =
                    grant.newChildForRuntime(process, published, null, origin.executionDomain());
            assertTrue(child.ownedByForRuntime(origin));
            assertFalse(child.ownedByForRuntime(other));
            assertFalse(child.ownedByForRuntime(null));
            ProtosExternalChildValue orphan = grant.newChildForRuntime(process, unpublished);
            assertFalse(orphan.ownedByForRuntime(origin));
            published.termination.complete(TerminalOutcome.unknown());
            unpublished.termination.complete(TerminalOutcome.unknown());
        }
    }

    /**
     * Controlled stand-in for the stdout transport of a child. Bytes exist only once the test
     * offers them, EOF only once the test ends the writer; a read with nothing available stays
     * pending until one of those two events. It never touches the OS and records overlapping
     * reads, which the lane contract forbids.
     */
    private static final class ControlledOutputLane implements ProtosByteIoFlow.Backend {
        private final java.util.ArrayDeque<byte[]> chunks = new java.util.ArrayDeque<>();
        private boolean ended;
        private ProtosByteIoFlow.ReadCompletion waiting;
        final AtomicInteger overlappingReads = new AtomicInteger();

        void offer(byte[] bytes) {
            ProtosByteIoFlow.ReadCompletion ready;
            synchronized (this) {
                if (ended) {
                    throw new IllegalStateException("bytes offered after the writer ended");
                }
                ready = waiting;
                waiting = null;
                if (ready == null) {
                    chunks.add(bytes);
                }
            }
            if (ready != null) {
                ready.data(bytes);
            }
        }

        void end() {
            ProtosByteIoFlow.ReadCompletion ready;
            synchronized (this) {
                ended = true;
                ready = chunks.isEmpty() ? waiting : null;
                if (ready != null) {
                    waiting = null;
                }
            }
            if (ready != null) {
                ready.eof();
            }
        }

        synchronized boolean readPendingForTesting() {
            return waiting != null;
        }

        @Override
        public ProtosByteIoFlow.Cancellation read(
                int maxBytes, ProtosByteIoFlow.ReadCompletion completion) {
            byte[] available = null;
            boolean eof = false;
            synchronized (this) {
                if (waiting != null) {
                    overlappingReads.incrementAndGet();
                    completion.failed();
                    return () -> {};
                }
                if (!chunks.isEmpty()) {
                    byte[] head = chunks.poll();
                    if (head.length > maxBytes) {
                        chunks.addFirst(java.util.Arrays.copyOfRange(head, maxBytes, head.length));
                        head = java.util.Arrays.copyOf(head, maxBytes);
                    }
                    available = head;
                } else if (ended) {
                    eof = true;
                } else {
                    waiting = completion;
                }
            }
            if (available != null) {
                completion.data(available);
            } else if (eof) {
                completion.eof();
            }
            return () -> {
                boolean cancelled;
                synchronized (this) {
                    cancelled = waiting == completion;
                    if (cancelled) {
                        waiting = null;
                    }
                }
                if (cancelled) {
                    completion.failed();
                }
            };
        }

        @Override
        public ProtosByteIoFlow.Cancellation write(
                byte[] bytes, ProtosByteIoFlow.WriteCompletion completion) {
            throw new IllegalStateException("write reached a child output lane");
        }
    }

    /** Records the single outcome of one read: the bytes, "eof", "failed" or the rejection. */
    private static final class ReadOutcome implements ProtosByteIoFlow.ReadCompletion {
        final CompletableFuture<Object> outcome = new CompletableFuture<>();

        @Override
        public void data(byte[] bytes) {
            outcome.complete(new String(bytes, java.nio.charset.StandardCharsets.US_ASCII));
        }

        @Override
        public void eof() {
            outcome.complete("eof");
        }

        @Override
        public void failed() {
            outcome.complete("failed");
        }

        @Override
        public void rejected(com.guillermomolina.protos.runtime.ProtosCoreErrors.StandardError kind) {
            outcome.complete(kind);
        }

        Object await() throws Exception {
            return outcome.get(10, TimeUnit.SECONDS);
        }
    }

    /**
     * Consumer read through the custody gate that the guest stdout endpoint applies: a released
     * handle rejects the read, a held one reaches the child's native lane.
     */
    private static ReadOutcome readStdout(ProtosExternalChildValue child) {
        ReadOutcome probe = new ReadOutcome();
        if (!child.endpointHandleHeldForRuntime(ProtosExternalChildValue.Stream.STDOUT)) {
            probe.rejected(
                    com.guillermomolina.protos.runtime.ProtosCoreErrors.StandardError
                            .I_O_LIFECYCLE_ERROR);
            return probe;
        }
        child.nativeChildForRuntime().lane(ProtosExternalChildValue.Stream.STDOUT).read(64, probe);
        return probe;
    }

    /**
     * Classification B (byte loss inside Protos custody): the terminal outcome is confirmed while
     * bytes the child produced are still undelivered. Custody keeps the handles and the transport,
     * the consumer receives every byte in order and only then the transport retires; custody is
     * left only after that retirement and the release of every handle.
     */
    @Test
    void terminalOutcomeBeforeDeliveryKeepsTransportUntilEveryByteIsConsumed() throws Exception {
        ControlledChild nativeChild = new ControlledChild();
        ControlledOutputLane lane = new ControlledOutputLane();
        CompletableFuture<Void> transport = new CompletableFuture<>();
        nativeChild.stdoutLane = lane;
        nativeChild.transport = transport;
        ProtosExternalChildValue child;
        ProtosProcessRuntime process;
        try (Context context = granted()) {
            process = process(context);
            child = process.rootExecutionForRuntime().orElseThrow()
                    .newChildForRuntime(process, nativeChild);
            assertTrue(process.registerExternalChildForRuntime(child));
            // The child produced both fragments before its exit was confirmed.
            lane.offer("early".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            lane.offer("late".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            nativeChild.termination.complete(TerminalOutcome.exited(0));
            assertEquals(TerminalOutcome.exited(0), child.outcomeForRuntime());
            assertTrue(child.reapedForRuntime());
            // Nothing was delivered yet: the handle and the transport are still in custody.
            assertTrue(child.endpointHandleHeldForRuntime(ProtosExternalChildValue.Stream.STDOUT));
            assertFalse(transport.isDone());
            assertFalse(child.custodyReleasedForRuntime());
            assertTrue(process.externalChildrenForRuntime().contains(child));

            assertEquals("early", readStdout(child).await());
            assertFalse(child.custodyReleasedForRuntime());
            assertEquals("late", readStdout(child).await());
            lane.end();
            assertEquals("eof", readStdout(child).await());
            assertEquals(0, lane.overlappingReads.get());
            // Every byte reached the consumer; the transport has not retired yet, so custody holds.
            assertFalse(child.custodyReleasedForRuntime());
            assertEquals(0, nativeChild.closedStreams.get());

            // Only now does the (controlled) pump retire its transport.
            transport.complete(null);
            // The handles are still owned on behalf of the Process, so custody still holds.
            assertFalse(child.custodyReleasedForRuntime());
            assertTrue(process.externalChildrenForRuntime().contains(child));
        }
        // Process termination releases the three handles; with reap and retirement already
        // established, that is the last obligation.
        assertEquals(3, nativeChild.closedStreams.get());
        assertTrue(child.custodyReleasedForRuntime());
        assertFalse(process.externalChildrenForRuntime().contains(child));
    }

    /**
     * Classification A versus B, without the JDK: the child is reaped while an equivalent writer
     * (a descendant) keeps the stdout transport open. The verified reap is retained as terminal
     * data and is never turned into EOF: a read issued after the reap stays pending, and the
     * fragment the writer produces afterwards is delivered before the real EOF.
     */
    @Test
    void reapWhileAnEquivalentWriterStaysOpenIsNotEof() throws Exception {
        ControlledChild nativeChild = new ControlledChild();
        ControlledOutputLane lane = new ControlledOutputLane();
        CompletableFuture<Void> transport = new CompletableFuture<>();
        nativeChild.stdoutLane = lane;
        nativeChild.transport = transport;
        ProtosExternalChildValue child;
        ProtosProcessRuntime process;
        try (Context context = granted()) {
            process = process(context);
            child = process.rootExecutionForRuntime().orElseThrow()
                    .newChildForRuntime(process, nativeChild);
            assertTrue(process.registerExternalChildForRuntime(child));
            lane.offer("early".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            assertEquals("early", readStdout(child).await());

            // The read is outstanding when the direct child is reaped.
            ReadOutcome pending = readStdout(child);
            assertTrue(lane.readPendingForTesting());
            nativeChild.termination.complete(TerminalOutcome.unknown());
            assertTrue(child.reapedForRuntime());
            assertEquals(TerminalOutcome.unknown(), child.outcomeForRuntime());
            // The reap completed no read and released no handle: it is not EOF.
            assertFalse(pending.outcome.isDone());
            assertTrue(lane.readPendingForTesting());
            assertTrue(child.endpointHandleHeldForRuntime(ProtosExternalChildValue.Stream.STDOUT));
            assertEquals(0, nativeChild.closedStreams.get());
            assertFalse(child.custodyReleasedForRuntime());

            // The equivalent writer produces its fragment after the reap; it reaches the read.
            lane.offer("late".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            assertEquals("late", pending.await());
            // Only the writer's end is EOF.
            lane.end();
            assertEquals("eof", readStdout(child).await());
            assertEquals(0, lane.overlappingReads.get());
            transport.complete(null);
            assertFalse(child.custodyReleasedForRuntime());
        }
        assertEquals(3, nativeChild.closedStreams.get());
        assertTrue(child.custodyReleasedForRuntime());
        assertFalse(process.externalChildrenForRuntime().contains(child));
    }

    private static Method parallelCopy() throws Exception {
        Class<?> transfer = Class.forName(
                "com.guillermomolina.protos.execution.ProtosParallelRuntime$Transfer");
        Method copy = transfer.getDeclaredMethod(
                "copy", Object.class, ProtosActivation.class, IdentityHashMap.class);
        copy.setAccessible(true);
        return copy;
    }

    private static IdentityHashMap<?, ?> transferMemo(boolean materializing) throws Exception {
        Class<?> memo = Class.forName(
                "com.guillermomolina.protos.execution.ProtosParallelRuntime$TransferMemo");
        Constructor<?> constructor = memo.getDeclaredConstructor(boolean.class);
        constructor.setAccessible(true);
        return (IdentityHashMap<?, ?>) constructor.newInstance(materializing);
    }

    private interface SameName {
        boolean same(String left, String right);
    }

    private static ProtosEnvironmentValue.NativeNameDomain domain(SameName same) {
        return new ProtosEnvironmentValue.NativeNameDomain() {
            @Override
            public boolean sameCapturedName(String left, String right) {
                return same.same(left, right);
            }

            @Override
            public boolean isQueryRepresentable(String name) {
                return true;
            }

            @Override
            public boolean matchesQuery(String captured, String query) {
                return same.same(captured, query);
            }
        };
    }

    private static ProtosMapValue environment(String... names) {
        ProtosMapValue map = new ProtosMapValue(ProtosObjectValue.rootObject());
        long hash = 0;
        for (String name : names) {
            map.append(new ProtosStringValue(name), ProtosNumericHashKey.ofLong(hash++),
                    new ProtosStringValue("value"));
        }
        return map;
    }

    @Test
    void nativeTerminalObservationSurvivesRepeatedWaitAndStreamRetirement() throws Exception {
        var provider = new com.guillermomolina.protos.execution.ProtosPosixProcessHandler();
        var pipe = org.graalvm.polyglot.io.ProcessHandler.Redirect.PIPE;
        var command = org.graalvm.polyglot.io.ProcessHandler.ProcessCommand.create(
                List.of("/bin/sh", "-c", "printf retained; exit 23"), "/",
                java.util.Map.of(), false, pipe, pipe, pipe);
        Process child = provider.start(command);
        try {
            child.getOutputStream().close();
            assertTrue(child.waitFor(10, TimeUnit.SECONDS));
            assertEquals(23, child.exitValue());
            assertEquals(23, child.waitFor());
            assertEquals("retained", new String(
                    child.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            child.getInputStream().close();
            child.getErrorStream().close();
            // The native state is now released. Cached observations and terminate remain safe.
            assertFalse(child.isAlive());
            assertEquals(23, child.exitValue());
            child.destroy();
            child.destroyForcibly();
            assertEquals(23, child.waitFor());
        } finally {
            child.destroyForcibly();
            child.getOutputStream().close();
            child.getInputStream().close();
            child.getErrorStream().close();
            assertTrue(child.waitFor(10, TimeUnit.SECONDS));
        }
    }

    @Test
    void uncertainReleaseKeepsTheStartFailureAndAdmissionPendingWithoutASecondClose() throws Exception {
        ControlledChild nativeChild = new ControlledChild();
        nativeChild.uncertainRelease.add(ProtosExternalChildValue.Stream.STDOUT);
        try (Context context = granted()) {
            ProtosProcessRuntime process = process(context);
            ProtosExternalExecutionCapabilityValue grant = process.rootExecutionForRuntime().orElseThrow();
            ProtosExternalExecutionCapabilityValue.Admission admission = grant.reserveChildForRuntime();
            assertNotNull(admission);
            CompletableFuture<Void> start = new CompletableFuture<>();
            process.admitNativeChildForRuntime(nativeChild);
            CompletableFuture<Boolean> cleanup = process.abandonNativeChildForRuntime(nativeChild, () -> {
                admission.release();
                start.completeExceptionally(new IOException("launch failed"));
            });
            assertFalse(start.isDone());
            assertFalse(cleanup.isDone());
            assertEquals(1, nativeChild.forcedRequests.get());
            assertEquals(1, grant.admittedChildrenForRuntime());
            nativeChild.termination.complete(TerminalOutcome.unknown());
            assertFalse(cleanup.get(10, TimeUnit.SECONDS));
            assertFalse(start.isDone());
            assertEquals(3, nativeChild.releaseCalls.get());
            assertEquals(1, grant.admittedChildrenForRuntime());
            assertFalse(process.abandonNativeChildForRuntime(nativeChild, null).get(10, TimeUnit.SECONDS));
            assertEquals(6, nativeChild.releaseRequests.get());
            assertEquals(3, nativeChild.releaseCalls.get());
            assertEquals(2, nativeChild.closedStreams.get());
            assertFalse(start.isDone());
            assertEquals(1, grant.admittedChildrenForRuntime());
            assertTrue(process.nativeChildrenForRuntime().contains(nativeChild));
        }
    }


    /**
     * Guest closures over one start Future. The grant is a bootstrap local of the first
     * evaluation only, so this program must be the Context's first evaluation.
     */
    private static final String LAUNCH_PROGRAM =
            """
            pending: null
            begin: () => {
                pending = execution.start("/bin/true", [], Map(), null)
                0
            }
            cancel: () => {
                pending.cancel()
                0
            }
            outcome: () => {
                Cancelled.handle(() => {
                    pending.value()
                    "resolved"
                }, (error) => { "cancelled" })
            }
            0
            """;

    /** Granted Context whose Truffle ProcessHandler is the controlled native creation seam. */
    private static Context grantedWith(org.graalvm.polyglot.io.ProcessHandler handler) {
        Context context = Context.newBuilder(ProtosLanguage.ID)
                .option("protos.CoreRoot", CORE)
                .option("protos.AllowExecution", "true")
                .allowCreateProcess(true)
                .allowIO(IOAccess.ALL)
                .processHandler(handler)
                .build();
        context.eval(ProtosLanguage.ID, LAUNCH_PROGRAM);
        return context;
    }

    private static String guest(Context context, String member) {
        return String.valueOf(context.getBindings(ProtosLanguage.ID).getMember(member).execute());
    }

    private static ProtosProcessRuntime enteredProcess(Context context) {
        context.enter();
        try {
            return ProtosLanguageContext.current().embeddedProcessOrNull().processForTesting();
        } finally {
            context.leave();
        }
    }

    private static void awaitCondition(java.util.function.BooleanSupplier condition, String what)
            throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!condition.getAsBoolean()) {
            assertTrue(System.nanoTime() < deadline, what);
            Thread.sleep(5);
        }
    }

    /**
     * Occupies every launch worker at once; once all of them run the barrier, every task queued
     * before it has finished.
     */
    private static void drainWorkers(ProtosExternalExecutionCapabilityValue grant)
            throws InterruptedException {
        CountDownLatch barrier = new CountDownLatch(ProtosExternalExecutionCapabilityValue.MAX_WORKERS);
        for (int index = 0; index < ProtosExternalExecutionCapabilityValue.MAX_WORKERS; index++) {
            assertTrue(grant.executeForRuntime(() -> {
                barrier.countDown();
                try {
                    barrier.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            }));
        }
        assertTrue(barrier.await(10, TimeUnit.SECONDS));
    }

    @Test
    void cancellationBeforeCommitmentCreatesNoChildAndRetainsNoAdmission() throws Exception {
        AtomicInteger creations = new AtomicInteger();
        try (Context context = grantedWith(command -> {
            creations.incrementAndGet();
            throw new IOException("controlled creation must not be reached");
        })) {
            ProtosProcessRuntime process = enteredProcess(context);
            ProtosExternalExecutionCapabilityValue grant =
                    process.rootExecutionForRuntime().orElseThrow();
            // Every worker is busy, so the launch stays queued before its commitment frontier.
            CountDownLatch release = new CountDownLatch(1);
            CountDownLatch running = new CountDownLatch(ProtosExternalExecutionCapabilityValue.MAX_WORKERS);
            for (int index = 0; index < ProtosExternalExecutionCapabilityValue.MAX_WORKERS; index++) {
                assertTrue(grant.executeForRuntime(() -> {
                    running.countDown();
                    try {
                        release.await();
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                    }
                }));
            }
            assertTrue(running.await(10, TimeUnit.SECONDS));
            assertEquals("0", guest(context, "begin"));
            assertEquals(1, grant.admittedChildrenForRuntime());
            // The owning domain cancels before the worker can commit: cancellation wins.
            assertEquals("0", guest(context, "cancel"));
            assertEquals("cancelled", guest(context, "outcome"));
            release.countDown();
            drainWorkers(grant);
            assertEquals(0, creations.get(), "a cancellation that won must reach no native creation");
            assertTrue(process.nativeChildrenForRuntime().isEmpty());
            assertTrue(process.externalChildrenForRuntime().isEmpty());
            assertEquals(0, grant.admittedChildrenForRuntime());
            assertTrue(grant.hostCustodyDischargedForRuntime());
            // No late guest completion replaced the cancelled outcome.
            assertEquals("cancelled", guest(context, "outcome"));
        }
    }

    @Test
    void cancellationAfterCommitmentKeepsTheChildInCustodyAndPublishesItOnce() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(
                java.nio.file.Files.isExecutable(Path.of("/bin/true")), "/bin/true is required");
        var provider = new com.guillermomolina.protos.execution.ProtosPosixProcessHandler();
        AtomicInteger creations = new AtomicInteger();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch proceed = new CountDownLatch(1);
        ProtosExternalExecutionCapabilityValue grant;
        try (Context context = grantedWith(command -> {
            // Reached only after the commitment frontier: the attempt is irreversible.
            creations.incrementAndGet();
            entered.countDown();
            try {
                proceed.await();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            return provider.start(command);
        })) {
            ProtosProcessRuntime process = enteredProcess(context);
            grant = process.rootExecutionForRuntime().orElseThrow();
            assertEquals("0", guest(context, "begin"));
            assertTrue(entered.await(10, TimeUnit.SECONDS));
            // Cancellation requested while the committed creation is pending publication.
            assertEquals("0", guest(context, "cancel"));
            assertFalse(grant.awaitLaunchesForHostClose(1), "the committed launch is still in flight");
            proceed.countDown();
            awaitCondition(() -> !process.externalChildrenForRuntime().isEmpty(),
                    "the committed child must enter Process custody");
            // The cancellation never became a zero-children claim: the child is published.
            assertEquals("resolved", guest(context, "outcome"));
            assertEquals(1, creations.get());
            assertEquals(1, process.externalChildrenForRuntime().size());
            assertEquals(1, grant.admittedChildrenForRuntime());
            assertTrue(grant.awaitLaunchesForHostClose(10_000));
        }
        // The host close reclaimed the child and returned its admission exactly once.
        assertEquals(1, creations.get());
        assertEquals(0, grant.admittedChildrenForRuntime());
        assertTrue(grant.hostCustodyDischargedForRuntime());
    }

    @Test
    void forcedTerminationRequestAloneNeverDischargesCustody() {
        ControlledChild nativeChild = new ControlledChild();
        try (Context context = granted()) {
            ProtosProcessRuntime process = process(context);
            ProtosExternalExecutionCapabilityValue grant =
                    process.rootExecutionForRuntime().orElseThrow();
            ProtosExternalExecutionCapabilityValue.Admission admission =
                    grant.reserveChildForRuntime();
            assertNotNull(admission);
            AtomicInteger discharged = new AtomicInteger();
            process.admitNativeChildForRuntime(nativeChild);
            CompletableFuture<Boolean> outcome = process.abandonNativeChildForRuntime(nativeChild, () -> {
                discharged.incrementAndGet();
                admission.release();
            });
            // SIGKILL was requested, but without a verified reap nothing counts as success.
            assertEquals(1, nativeChild.forcedRequests.get());
            assertFalse(outcome.isDone(), "a kill request is not a reap");
            assertEquals(0, discharged.get());
            assertTrue(process.nativeChildrenForRuntime().contains(nativeChild));
            assertEquals(1, grant.admittedChildrenForRuntime());
            assertFalse(grant.hostCustodyDischargedForRuntime());
            nativeChild.termination.complete(TerminalOutcome.unknown());
            assertEquals(1, discharged.get());
            assertEquals(0, grant.admittedChildrenForRuntime());
        }
    }

    /**
     * Native stage whose registered callbacks wait for an explicit gate after the native result is
     * already set, so a test can observe a completed native stage while the runtime recording of
     * it is still in progress. The dependent stage is the real one returned by
     * {@code super.whenComplete}; it completes only after the registered action returned.
     */
    private static final class GatedCallbackFuture<T> extends CompletableFuture<T> {
        final CountDownLatch callbackEntered = new CountDownLatch(1);
        final CountDownLatch callbackGate = new CountDownLatch(1);

        @Override
        public CompletableFuture<T> whenComplete(
                java.util.function.BiConsumer<? super T, ? super Throwable> action) {
            return super.whenComplete((value, failure) -> {
                callbackEntered.countDown();
                try {
                    if (!callbackGate.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("callback gate was never opened");
                    }
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("callback gate interrupted", interrupted);
                }
                action.accept(value, failure);
            });
        }
    }

    /**
     * Waits, bounded, until {@code thread} is blocked entering {@code monitor} while the calling
     * thread owns it. Observes JVM thread state only; never assumes progress from elapsed time.
     */
    private static void awaitBlockedOnMonitor(Thread thread, Object monitor) {
        java.lang.management.ThreadMXBean threads =
                java.lang.management.ManagementFactory.getThreadMXBean();
        long owner = Thread.currentThread().threadId();
        int identity = System.identityHashCode(monitor);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (true) {
            java.lang.management.ThreadInfo info = threads.getThreadInfo(thread.threadId());
            if (info != null
                    && info.getThreadState() == Thread.State.BLOCKED
                    && info.getLockInfo() != null
                    && info.getLockInfo().getIdentityHashCode() == identity
                    && info.getLockOwnerId() == owner) {
                return;
            }
            assertTrue(thread.isAlive(), thread.getName() + " finished without blocking on the monitor");
            assertTrue(System.nanoTime() < deadline, thread.getName() + " never blocked on the monitor");
            Thread.onSpinWait();
        }
    }

    /** Starts a test-owned thread that runs one host reclamation with a zero budget. */
    private static Thread startZeroBudgetReclamation(
            ProtosExternalChildValue child, CompletableFuture<Boolean> result) {
        Thread reclaimer = new Thread(() -> {
            try {
                result.complete(child.reclaimForHostCloseRuntime(0));
            } catch (Throwable failure) {
                result.completeExceptionally(failure);
            }
        }, "bug041-reclaimer");
        reclaimer.start();
        return reclaimer;
    }

    /**
     * Starts a test-owned thread that runs exactly one Process-level host reclamation with the
     * given budget; no retry and no guest code.
     */
    private static Thread startProcessReclamation(
            ProtosProcessRuntime process, long graceMillis, CompletableFuture<Boolean> result) {
        Thread reclaimer = new Thread(() -> {
            try {
                result.complete(process.reclaimExternalChildrenForHostClose(graceMillis));
            } catch (Throwable failure) {
                result.completeExceptionally(failure);
            }
        }, "bug041-process-reclaimer");
        reclaimer.start();
        return reclaimer;
    }

    /**
     * Waits, bounded, until {@code reclaimer} is parked inside the real Process-level reclamation
     * path, within the child's {@code waitMethod} ({@code awaitReap} or {@code awaitTransport}),
     * while its result is still unpublished. Observes JVM thread state and stack frames only.
     */
    private static void awaitReclamationWaitingForRecording(
            Thread reclaimer, String waitMethod, CompletableFuture<Boolean> result) {
        java.lang.management.ThreadMXBean threads =
                java.lang.management.ManagementFactory.getThreadMXBean();
        String childClass = ProtosExternalChildValue.class.getName();
        String processClass = ProtosProcessRuntime.class.getName();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (true) {
            assertFalse(result.isDone(), "reclamation published its result before waiting in " + waitMethod);
            assertTrue(reclaimer.isAlive(), "reclamation finished before waiting in " + waitMethod);
            java.lang.management.ThreadInfo info =
                    threads.getThreadInfo(reclaimer.threadId(), Integer.MAX_VALUE);
            if (info != null
                    && (info.getThreadState() == Thread.State.WAITING
                            || info.getThreadState() == Thread.State.TIMED_WAITING)) {
                boolean inWait = false;
                boolean inChildReclamation = false;
                boolean inProcessReclamation = false;
                for (StackTraceElement frame : info.getStackTrace()) {
                    inWait |= childClass.equals(frame.getClassName())
                            && waitMethod.equals(frame.getMethodName());
                    inChildReclamation |= childClass.equals(frame.getClassName())
                            && "reclaimForHostCloseRuntime".equals(frame.getMethodName());
                    inProcessReclamation |= processClass.equals(frame.getClassName())
                            && "reclaimExternalChildrenForHostClose".equals(frame.getMethodName());
                }
                if (inWait && inChildReclamation && inProcessReclamation && !result.isDone()) {
                    return;
                }
            }
            assertTrue(System.nanoTime() < deadline, "reclamation never waited in " + waitMethod);
            Thread.onSpinWait();
        }
    }

    private static void joinTestThread(Thread thread) throws InterruptedException {
        thread.join(10_000);
        assertFalse(thread.isAlive(), thread.getName() + " did not finish");
    }

    /**
     * BUG041: transport retirement is the last native obligation and its callback is blocked in
     * Process registry removal. Observing the custody flag is not enough: host reclamation must
     * not report success until the callback, registry removal and admission return finished.
     */
    @Test
    void bug041TransportReclamationWaitsForCustodyPublication() throws Exception {
        ControlledChild nativeChild = new ControlledChild();
        CompletableFuture<Void> pumps = new CompletableFuture<>();
        nativeChild.transport = pumps;
        try (Context context = granted()) {
            ProtosProcessRuntime process = process(context);
            ProtosExternalExecutionCapabilityValue grant =
                    process.rootExecutionForRuntime().orElseThrow();
            ProtosExternalExecutionCapabilityValue.Admission admission =
                    grant.reserveChildForRuntime();
            assertNotNull(admission);
            ProtosExternalChildValue child =
                    grant.newChildForRuntime(process, nativeChild, admission);
            assertTrue(process.registerExternalChildForRuntime(child));
            // 1. verified reap; 2. host-only custody; 3. three confirmed releases.
            nativeChild.termination.complete(TerminalOutcome.exited(0));
            child.enterHostOnlyCustodyForRuntime();
            assertTrue(child.reapedForRuntime());
            for (ProtosExternalChildValue.Stream stream : ProtosExternalChildValue.Stream.values()) {
                assertFalse(child.endpointHandleHeldForRuntime(stream));
            }
            // 4. transport is the last obligation; 5. one admission stays reserved.
            assertFalse(child.custodyReleasedForRuntime());
            assertEquals(1, grant.admittedChildrenForRuntime());

            Thread completer = new Thread(() -> pumps.complete(null), "bug041-transport-completion");
            CompletableFuture<Boolean> whileBlocked = new CompletableFuture<>();
            Thread reclaimer = null;
            try {
                synchronized (process) {
                    completer.start();
                    awaitBlockedOnMonitor(completer, process);
                    reclaimer = startZeroBudgetReclamation(child, whileBlocked);
                    assertFalse(whileBlocked.get(10, TimeUnit.SECONDS),
                            "success was reported while registry removal was still blocked");
                    assertTrue(process.externalChildrenForRuntime().contains(child));
                }
            } finally {
                if (reclaimer != null) {
                    joinTestThread(reclaimer);
                }
                joinTestThread(completer);
            }

            assertFalse(process.externalChildrenForRuntime().contains(child));
            assertTrue(child.custodyReleasedForRuntime());
            assertEquals(0, grant.admittedChildrenForRuntime());
            assertTrue(child.reclaimForHostCloseRuntime(0));
            assertTrue(child.hostCloseReclaimedForRuntime());
            assertTrue(child.reclaimForHostCloseRuntime(0));
            assertEquals(3, nativeChild.releaseCalls.get());
            assertEquals(3, nativeChild.closedStreams.get());
            assertEquals(0, nativeChild.forcedRequests.get());
            admission.release();
            assertEquals(0, grant.admittedChildrenForRuntime());
        }
    }

    /**
     * BUG041: the verified reap is the last native obligation and its recording callback is
     * blocked in Process registry removal. A completed native termination is not completed
     * custody, and it is never escalated to a forced termination.
     */
    @Test
    void bug041SupervisorReclamationWaitsForCustodyPublication() throws Exception {
        ControlledChild nativeChild = new ControlledChild();
        try (Context context = granted()) {
            ProtosProcessRuntime process = process(context);
            ProtosExternalExecutionCapabilityValue grant =
                    process.rootExecutionForRuntime().orElseThrow();
            ProtosExternalExecutionCapabilityValue.Admission admission =
                    grant.reserveChildForRuntime();
            assertNotNull(admission);
            ProtosExternalChildValue child =
                    grant.newChildForRuntime(process, nativeChild, admission);
            assertTrue(process.registerExternalChildForRuntime(child));
            // Transport already retired (fixture default); every release confirmed while live.
            child.enterHostOnlyCustodyForRuntime();
            assertEquals(1, nativeChild.terminationRequests.get());
            for (ProtosExternalChildValue.Stream stream : ProtosExternalChildValue.Stream.values()) {
                assertFalse(child.endpointHandleHeldForRuntime(stream));
            }
            assertFalse(child.reapedForRuntime());
            assertEquals(1, grant.admittedChildrenForRuntime());

            Thread completer = new Thread(
                    () -> nativeChild.termination.complete(TerminalOutcome.exited(0)),
                    "bug041-termination-completion");
            CompletableFuture<Boolean> whileBlocked = new CompletableFuture<>();
            Thread reclaimer = null;
            try {
                synchronized (process) {
                    completer.start();
                    awaitBlockedOnMonitor(completer, process);
                    assertTrue(nativeChild.termination.isDone());
                    reclaimer = startZeroBudgetReclamation(child, whileBlocked);
                    assertFalse(whileBlocked.get(10, TimeUnit.SECONDS),
                            "success was reported while the reap recording was still blocked");
                    assertTrue(process.externalChildrenForRuntime().contains(child));
                }
            } finally {
                if (reclaimer != null) {
                    joinTestThread(reclaimer);
                }
                joinTestThread(completer);
            }

            assertEquals(0, nativeChild.forcedRequests.get(),
                    "a completed native termination must never be escalated");
            assertFalse(process.externalChildrenForRuntime().contains(child));
            assertTrue(child.custodyReleasedForRuntime());
            assertEquals(0, grant.admittedChildrenForRuntime());
            assertTrue(child.reclaimForHostCloseRuntime(0));
            assertEquals(3, nativeChild.releaseCalls.get());
            assertEquals(3, nativeChild.closedStreams.get());
            assertEquals(0, nativeChild.forcedRequests.get());
            admission.release();
        }
    }

    /**
     * BUG041: the native terminal observation exposed to the guest wait mechanism completes with
     * the verified outcome while transport retirement, custody and admission are still pending.
     */
    @Test
    void bug041TerminalObservationDoesNotWaitForTransportRetirement() throws Exception {
        ControlledChild nativeChild = new ControlledChild();
        CompletableFuture<Void> pumps = new CompletableFuture<>();
        nativeChild.transport = pumps;
        try (Context context = granted()) {
            ProtosProcessRuntime process = process(context);
            ProtosExternalExecutionCapabilityValue grant =
                    process.rootExecutionForRuntime().orElseThrow();
            ProtosExternalExecutionCapabilityValue.Admission admission =
                    grant.reserveChildForRuntime();
            assertNotNull(admission);
            ProtosExternalChildValue child =
                    grant.newChildForRuntime(process, nativeChild, admission);
            assertTrue(process.registerExternalChildForRuntime(child));
            TerminalOutcome verified = TerminalOutcome.exited(0);
            nativeChild.termination.complete(verified);

            CompletableFuture<TerminalOutcome> first = child.terminationForRuntime().toCompletableFuture();
            CompletableFuture<TerminalOutcome> second = child.terminationForRuntime().toCompletableFuture();
            assertTrue(first.isDone());
            assertSame(verified, first.getNow(null));
            assertSame(verified, second.getNow(null));
            assertFalse(pumps.isDone());
            assertFalse(child.custodyReleasedForRuntime());
            assertTrue(process.externalChildrenForRuntime().contains(child));
            assertEquals(1, grant.admittedChildrenForRuntime());
            assertFalse(child.reclaimForHostCloseRuntime(0));

            pumps.complete(null);
            assertTrue(child.reclaimForHostCloseRuntime(0));
            assertFalse(process.externalChildrenForRuntime().contains(child));
            assertEquals(0, grant.admittedChildrenForRuntime());
            assertEquals(0, nativeChild.forcedRequests.get());
            admission.release();
        }
    }

    /**
     * BUG041: the native termination completed but its recording callback is held. Host
     * reclamation sees pending custody processing, not a live child: it fails without any forced
     * termination and succeeds once the callback returned.
     */
    @Test
    void bug041RecordedReapPendingAfterNativeExitDoesNotForceTermination() throws Exception {
        GatedCallbackFuture<TerminalOutcome> termination = new GatedCallbackFuture<>();
        ControlledChild nativeChild = new ControlledChild(termination);
        try (Context context = granted()) {
            ProtosProcessRuntime process = process(context);
            ProtosExternalExecutionCapabilityValue grant =
                    process.rootExecutionForRuntime().orElseThrow();
            ProtosExternalExecutionCapabilityValue.Admission admission =
                    grant.reserveChildForRuntime();
            assertNotNull(admission);
            ProtosExternalChildValue child =
                    grant.newChildForRuntime(process, nativeChild, admission);
            assertTrue(process.registerExternalChildForRuntime(child));
            child.enterHostOnlyCustodyForRuntime();
            assertEquals(1, nativeChild.terminationRequests.get());

            Thread completer = new Thread(
                    () -> termination.complete(TerminalOutcome.exited(0)),
                    "bug041-gated-termination-completion");
            try {
                completer.start();
                assertTrue(termination.callbackEntered.await(10, TimeUnit.SECONDS));
                assertTrue(termination.isDone());
                assertNull(child.outcomeForRuntime(), "the recording callback is still held");
                assertFalse(child.reclaimForHostCloseRuntime(0));
                assertEquals(0, nativeChild.forcedRequests.get(),
                        "pending recording after native exit must not force termination");
                assertTrue(process.externalChildrenForRuntime().contains(child));
                assertEquals(1, grant.admittedChildrenForRuntime());
            } finally {
                termination.callbackGate.countDown();
                joinTestThread(completer);
            }

            assertEquals(TerminalOutcome.exited(0), child.outcomeForRuntime());
            assertTrue(child.custodyReleasedForRuntime());
            assertFalse(process.externalChildrenForRuntime().contains(child));
            assertEquals(0, grant.admittedChildrenForRuntime());
            assertTrue(child.reclaimForHostCloseRuntime(0));
            assertTrue(child.reclaimForHostCloseRuntime(0));
            assertEquals(3, nativeChild.releaseCalls.get());
            assertEquals(3, nativeChild.closedStreams.get());
            assertEquals(0, nativeChild.forcedRequests.get());
            admission.release();
        }
    }

    /**
     * BUG041: with the reap verified and the transport retired, one pending endpoint release keeps
     * custody; its recorded completion discharges the child through the existing path.
     */
    @Test
    void bug041PendingEndpointReleasePreventsSuccessfulReclamation() {
        ControlledChild nativeChild = new ControlledChild();
        CompletableFuture<Boolean> pendingStderr = new CompletableFuture<>();
        nativeChild.pendingRelease.put(ProtosExternalChildValue.Stream.STDERR, pendingStderr);
        try (Context context = granted()) {
            ProtosProcessRuntime process = process(context);
            ProtosExternalExecutionCapabilityValue grant =
                    process.rootExecutionForRuntime().orElseThrow();
            ProtosExternalExecutionCapabilityValue.Admission admission =
                    grant.reserveChildForRuntime();
            assertNotNull(admission);
            ProtosExternalChildValue child =
                    grant.newChildForRuntime(process, nativeChild, admission);
            assertTrue(process.registerExternalChildForRuntime(child));
            try {
                nativeChild.termination.complete(TerminalOutcome.exited(0));
                child.enterHostOnlyCustodyForRuntime();
                assertFalse(child.endpointHandleHeldForRuntime(ProtosExternalChildValue.Stream.STDIN));
                assertFalse(child.endpointHandleHeldForRuntime(ProtosExternalChildValue.Stream.STDOUT));
                assertTrue(child.endpointHandleHeldForRuntime(ProtosExternalChildValue.Stream.STDERR));

                assertFalse(child.reclaimForHostCloseRuntime(0));
                assertTrue(process.externalChildrenForRuntime().contains(child));
                assertEquals(1, grant.admittedChildrenForRuntime());
                assertFalse(child.custodyReleasedForRuntime());
            } finally {
                pendingStderr.complete(true);
            }

            assertFalse(child.endpointHandleHeldForRuntime(ProtosExternalChildValue.Stream.STDERR));
            assertFalse(child.endpointReleaseUncertainForRuntime(ProtosExternalChildValue.Stream.STDERR));
            assertTrue(child.custodyReleasedForRuntime());
            assertFalse(process.externalChildrenForRuntime().contains(child));
            assertEquals(0, grant.admittedChildrenForRuntime());
            assertTrue(child.reclaimForHostCloseRuntime(0));
            assertEquals(3, nativeChild.releaseCalls.get());
            assertEquals(0, nativeChild.forcedRequests.get());
            admission.release();
        }
    }

    /**
     * BUG041: an exceptional transport retirement is never retirement. With the reap verified and
     * every release confirmed, the child keeps custody, its registration and its admission, and
     * no host-close success is published; the Context close is refused like any other genuine
     * outstanding custody.
     */
    @Test
    void bug041ExceptionalTransportRetirementNeverDischargesCustody() {
        ControlledChild nativeChild = new ControlledChild();
        nativeChild.transport = CompletableFuture.failedFuture(new IOException("transport lost"));
        ProtosProcessRuntime process;
        ProtosExternalChildValue child;
        ProtosExternalExecutionCapabilityValue grant;
        try (Context context = granted()) {
            process = process(context);
            grant = process.rootExecutionForRuntime().orElseThrow();
            ProtosExternalExecutionCapabilityValue.Admission admission =
                    grant.reserveChildForRuntime();
            assertNotNull(admission);
            child = grant.newChildForRuntime(process, nativeChild, admission);
            assertTrue(process.registerExternalChildForRuntime(child));
            nativeChild.termination.complete(TerminalOutcome.exited(0));
            child.enterHostOnlyCustodyForRuntime();
            for (ProtosExternalChildValue.Stream stream : ProtosExternalChildValue.Stream.values()) {
                assertFalse(child.endpointHandleHeldForRuntime(stream));
            }

            assertFalse(child.reclaimForHostCloseRuntime(1));
            assertFalse(child.custodyReleasedForRuntime());
            assertFalse(child.hostCloseReclaimedForRuntime());
            assertTrue(process.externalChildrenForRuntime().contains(child));
            assertEquals(1, grant.admittedChildrenForRuntime());
            assertEquals(0, nativeChild.forcedRequests.get());
        }
        assertFalse(child.custodyReleasedForRuntime());
        assertFalse(child.hostCloseReclaimedForRuntime());
        assertTrue(process.externalCustodyOutstandingForRuntime());
        assertEquals(1, grant.admittedChildrenForRuntime());
        assertEquals(3, nativeChild.releaseCalls.get());
    }

    /**
     * BUG041: one Process-level reclamation with a positive budget waits for the held recording
     * of a completed native termination and succeeds in that same invocation once it returns.
     */
    @Test
    void bug041ProcessReclamationWaitsForRecordedTermination() throws Exception {
        GatedCallbackFuture<TerminalOutcome> termination = new GatedCallbackFuture<>();
        ControlledChild nativeChild = new ControlledChild(termination);
        try (Context context = granted()) {
            ProtosProcessRuntime process = process(context);
            ProtosExternalExecutionCapabilityValue grant =
                    process.rootExecutionForRuntime().orElseThrow();
            ProtosExternalExecutionCapabilityValue.Admission admission =
                    grant.reserveChildForRuntime();
            assertNotNull(admission);
            ProtosExternalChildValue child =
                    grant.newChildForRuntime(process, nativeChild, admission);
            assertTrue(process.registerExternalChildForRuntime(child));
            assertTrue(nativeChild.transport.isDone());
            assertFalse(nativeChild.transport.isCompletedExceptionally());
            child.enterHostOnlyCustodyForRuntime();
            for (ProtosExternalChildValue.Stream stream : ProtosExternalChildValue.Stream.values()) {
                assertFalse(child.endpointHandleHeldForRuntime(stream));
            }
            assertFalse(child.reapedForRuntime());

            Thread completer = new Thread(
                    () -> termination.complete(TerminalOutcome.exited(0)),
                    "bug041-gated-termination-completion");
            CompletableFuture<Boolean> reclaimed = new CompletableFuture<>();
            Thread reclaimer = null;
            try {
                completer.start();
                assertTrue(termination.callbackEntered.await(10, TimeUnit.SECONDS));
                assertTrue(termination.isDone());
                assertFalse(child.reapedForRuntime(), "termination recording is still held");
                assertTrue(process.externalChildrenForRuntime().contains(child));
                assertEquals(1, grant.admittedChildrenForRuntime());

                reclaimer = startProcessReclamation(process, 10_000, reclaimed);
                awaitReclamationWaitingForRecording(reclaimer, "awaitReap", reclaimed);
                assertFalse(reclaimed.isDone());
                assertTrue(process.externalChildrenForRuntime().contains(child));
                assertEquals(1, grant.admittedChildrenForRuntime());
                assertEquals(0, nativeChild.forcedRequests.get());
            } finally {
                termination.callbackGate.countDown();
                joinTestThread(completer);
                if (reclaimer != null) {
                    joinTestThread(reclaimer);
                }
            }
            assertTrue(reclaimed.get(10, TimeUnit.SECONDS),
                    "the same reclamation invocation must succeed once recording returned");

            assertTrue(child.reapedForRuntime());
            assertTrue(child.custodyReleasedForRuntime());
            assertTrue(child.hostCloseReclaimedForRuntime());
            assertTrue(process.externalChildrenForRuntime().isEmpty());
            assertTrue(process.nativeChildrenForRuntime().isEmpty());
            assertFalse(process.externalCustodyOutstandingForRuntime());
            assertEquals(0, grant.admittedChildrenForRuntime());
            assertEquals(3, nativeChild.releaseCalls.get());
            assertEquals(0, nativeChild.forcedRequests.get());
            admission.release();
            assertEquals(0, grant.admittedChildrenForRuntime());
        }
    }

    /**
     * BUG041: one Process-level reclamation with a positive budget waits for the held recording
     * of a completed transport retirement and succeeds in that same invocation once it returns.
     */
    @Test
    void bug041ProcessReclamationWaitsForRecordedTransportRetirement() throws Exception {
        ControlledChild nativeChild = new ControlledChild();
        GatedCallbackFuture<Void> transport = new GatedCallbackFuture<>();
        nativeChild.transport = transport;
        try (Context context = granted()) {
            ProtosProcessRuntime process = process(context);
            ProtosExternalExecutionCapabilityValue grant =
                    process.rootExecutionForRuntime().orElseThrow();
            ProtosExternalExecutionCapabilityValue.Admission admission =
                    grant.reserveChildForRuntime();
            assertNotNull(admission);
            ProtosExternalChildValue child =
                    grant.newChildForRuntime(process, nativeChild, admission);
            assertTrue(process.registerExternalChildForRuntime(child));
            nativeChild.termination.complete(TerminalOutcome.exited(0));
            assertTrue(child.reapedForRuntime());
            child.enterHostOnlyCustodyForRuntime();
            for (ProtosExternalChildValue.Stream stream : ProtosExternalChildValue.Stream.values()) {
                assertFalse(child.endpointHandleHeldForRuntime(stream));
            }
            assertFalse(child.custodyReleasedForRuntime());
            assertEquals(1, grant.admittedChildrenForRuntime());

            Thread completer = new Thread(
                    () -> transport.complete(null), "bug041-gated-transport-completion");
            CompletableFuture<Boolean> reclaimed = new CompletableFuture<>();
            Thread reclaimer = null;
            try {
                completer.start();
                assertTrue(transport.callbackEntered.await(10, TimeUnit.SECONDS));
                assertTrue(transport.isDone());
                assertFalse(child.custodyReleasedForRuntime(), "transport recording is still held");
                assertTrue(process.externalChildrenForRuntime().contains(child));
                assertEquals(1, grant.admittedChildrenForRuntime());

                reclaimer = startProcessReclamation(process, 10_000, reclaimed);
                awaitReclamationWaitingForRecording(reclaimer, "awaitTransport", reclaimed);
                assertFalse(reclaimed.isDone());
                assertTrue(process.externalChildrenForRuntime().contains(child));
                assertEquals(1, grant.admittedChildrenForRuntime());
            } finally {
                transport.callbackGate.countDown();
                joinTestThread(completer);
                if (reclaimer != null) {
                    joinTestThread(reclaimer);
                }
            }
            assertTrue(reclaimed.get(10, TimeUnit.SECONDS),
                    "the same reclamation invocation must succeed once recording returned");

            assertTrue(child.reapedForRuntime());
            for (ProtosExternalChildValue.Stream stream : ProtosExternalChildValue.Stream.values()) {
                assertFalse(child.endpointHandleHeldForRuntime(stream));
            }
            assertTrue(child.custodyReleasedForRuntime());
            assertTrue(child.hostCloseReclaimedForRuntime());
            assertTrue(process.externalChildrenForRuntime().isEmpty());
            assertTrue(process.nativeChildrenForRuntime().isEmpty());
            assertFalse(process.externalCustodyOutstandingForRuntime());
            assertEquals(0, grant.admittedChildrenForRuntime());
            assertEquals(3, nativeChild.releaseCalls.get());
            assertEquals(0, nativeChild.forcedRequests.get());
            admission.release();
            assertEquals(0, grant.admittedChildrenForRuntime());
        }
    }
}
