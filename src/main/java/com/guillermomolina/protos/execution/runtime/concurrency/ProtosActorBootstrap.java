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

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosActor;
import com.guillermomolina.protos.runtime.ProtosActorRefValue;
import com.guillermomolina.protos.runtime.ProtosActorScheduler;
import com.guillermomolina.protos.runtime.ProtosActorValueTransfer;
import com.guillermomolina.protos.runtime.ProtosCoreErrors;
import com.guillermomolina.protos.runtime.ProtosIdentity;
import com.guillermomolina.protos.runtime.modules.ProtosModuleKey;
import com.guillermomolina.protos.runtime.ProtosNumericValueSupport;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosProcessRuntime;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.guillermomolina.protos.runtime.ProtosActorExecutionDomain;
import com.guillermomolina.protos.runtime.ProtosActorExecutionDomain.HostEntryExtent;
import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Destination-local Actor bootstrap machinery.
 *
 * <p>This layer deliberately starts after public-spawn synchronous validation and Actor-boundary
 * argument transfer. It consumes a canonical ModuleKey and already-transferred destination-local
 * arguments, so it never re-resolves the creator's module spelling and never shares creator
 * mutable state. The public Actor.spawn/current surface is installed by a later slice once its
 * remaining transfer/runtime prerequisites are present.
 */
public final class ProtosActorBootstrap {
    private final ProtosModuleRuntime moduleRuntime;

    /**
     * Test-only observation; null in production. Called once each time an initialization closes the
     * Context it owned, with the initialized Actor.
     */
    static volatile java.util.function.Consumer<ProtosActor> ownedContextClosedForTesting;

    public ProtosActorBootstrap(ProtosModuleRuntime moduleRuntime) {
        this.moduleRuntime = Objects.requireNonNull(moduleRuntime, "moduleRuntime");
    }

    /**
     * Runs bootstrap for one existing Actor incarnation on the calling host thread and returns
     * only when the initialization is terminal.
     *
     * <p>This is the same {@link Sequence} engine as the scheduled bootstrap and runs every segment
     * with the same {@code HostEntryExtent.BOOTSTRAP} authority (I158 / D251-A1). An explicit
     * {@code Future.value()} on a pending Future suspends the initialization's C-prime
     * continuation; this calling thread, which must not be an Actor scheduler carrier, then waits,
     * dispatches only this Actor's targeted completions (never a Task handoff and never a Task of
     * this Actor) and resumes the same continuation in the same Context. Nothing is replayed. A
     * termination while suspended invalidates the initialization; its retained continuation is
     * abandoned by this thread. Scheduled bootstraps use {@link #controlTurn} instead.
     *
     * @return true only when this invocation performs the INITIALIZING -> READY cutover; false
     *     when initialization was invalidated or a concurrent termination cutover wins first
     */
    public boolean initialize(
            ProtosActor actor,
            ProtosPrelude prelude,
            ProtosModuleKey canonicalModuleKey,
            String bindingName,
            List<?> transferredArguments) {
        Sequence sequence =
                new Sequence(
                        moduleRuntime,
                        actor,
                        prelude,
                        canonicalModuleKey,
                        bindingName,
                        transferredArguments,
                        null,
                        null);
        return sequence.runSynchronously();
    }

    /**
     * I158 / D251-A1: the scheduler control turn that starts the single suspendible initialization
     * of {@code actor} on {@code scheduler}.
     *
     * <p>The turn runs the first initialization segment. When the initial module or the binding
     * suspends in a pending {@code Future.value()}, the carrier is released and the scheduler
     * resumes the same C-prime continuation later; the turn never reports a provisional result.
     *
     * @param outcome completed with true at READY, false when initialization failed with a Protos
     *     Error or termination won, exceptionally on a host failure; may be null
     */
    Runnable controlTurn(
            ProtosActor actor,
            ProtosActorScheduler scheduler,
            ProtosPrelude prelude,
            ProtosModuleKey canonicalModuleKey,
            String bindingName,
            List<?> transferredArguments,
            CompletableFuture<Boolean> outcome) {
        Objects.requireNonNull(scheduler, "scheduler");
        Sequence sequence =
                new Sequence(
                        moduleRuntime,
                        actor,
                        prelude,
                        canonicalModuleKey,
                        bindingName,
                        transferredArguments,
                        scheduler,
                        outcome);
        return new ProtosActorScheduler.HostEntryFailureAwareControl() {
            @Override
            public void run() {
                sequence.start();
            }

            @Override
            public void hostEntryFailed(Throwable failure) {
                sequence.hostFailed(failure);
            }
        };
    }

    /**
     * I158 / D251-A1 owner of one logically single, physically suspendible Actor initialization:
     * initial MODULE/SOURCE, binding selection, binding invocation and behavior fixing.
     *
     * <p>The identity and bootstrap Context are prepared once. The Context-local {@link
     * ProtosActorBootstrapCPrimeExecution} root executes module and binding in one C-prime
     * continuation; every resumption uses {@link ProtosTailCallContinuation#resume} and never
     * re-enters the initialization from the beginning. A terminal result is validated as an
     * ordinary behavior object and fixed by the single {@code completeInitialization}. An unhandled
     * Protos Error in any segment keeps the existing Actor/RootActor fatality. Termination before a
     * resumption makes the initialization terminally unsuccessful without running guest code.
     */
    /** Where every segment of one initialization executes; fixed by its first segment. */
    private enum Placement {
        ENTERED,
        HOSTED,
        OWNED
    }

    static final class Sequence {
        private final ProtosModuleRuntime moduleRuntime;
        private final ProtosActor actor;
        private final ProtosPrelude prelude;
        private final ProtosModuleKey canonicalModuleKey;
        private final String bindingName;
        private final List<?> transferredArguments;
        private final ProtosActorScheduler scheduler;
        private final CompletableFuture<Boolean> outcome;
        private ProtosObjectValue bootstrapContext;
        private ProtosActivation activation;
        private ProtosActivation invocation;
        private ProtosModuleRuntime.PreparedModuleInitialization initialModule;
        private ProtosActorBootstrapSuspension suspension;
        private boolean preparedModule;
        private boolean preparedBinding;
        private ProtosPolyglotExecutionContext ownedContext;
        private boolean ownedContextClosed;
        private Placement placement;
        /** The Truffle Context of the first segment; every later segment must enter it again. */
        private ProtosLanguageContext segmentContext;
        /** The Process of a HOSTED placement; a later segment must be hosted by it. */
        private ProtosProcessRuntime hostedProcess;

        private Sequence(
                ProtosModuleRuntime moduleRuntime,
                ProtosActor actor,
                ProtosPrelude prelude,
                ProtosModuleKey canonicalModuleKey,
                String bindingName,
                List<?> transferredArguments,
                ProtosActorScheduler scheduler,
                CompletableFuture<Boolean> outcome) {
            this.moduleRuntime = Objects.requireNonNull(moduleRuntime, "moduleRuntime");
            this.actor = Objects.requireNonNull(actor, "actor");
            this.prelude = Objects.requireNonNull(prelude, "prelude");
            this.canonicalModuleKey =
                    Objects.requireNonNull(canonicalModuleKey, "canonicalModuleKey");
            this.bindingName = Objects.requireNonNull(bindingName, "bindingName");
            this.transferredArguments =
                    Objects.requireNonNull(transferredArguments, "transferredArguments");
            this.scheduler = scheduler;
            this.outcome = outcome;
        }

        /** Prepares the bootstrap identity and Context exactly once. */
        private void prepareIdentity() {
            if (actor.lifecycleState() != ProtosActor.LifecycleState.INITIALIZING) {
                throw new IllegalStateException("Actor bootstrap requires INITIALIZING lifecycle");
            }
            bootstrapContext = prelude.newExecutionContext();
            activation =
                    prelude.newModuleActivation(
                            actor.moduleState(), null, bootstrapContext, actor.executionDomain());
            ProtosActorRefValue current =
                    activation.executionDomain().currentActorReference().orElseThrow(
                            () -> new IllegalStateException("Actor execution domain lost its owner"));
            if (!ProtosIdentity.identical(current, actor.reference())) {
                throw new IllegalStateException("bootstrap activation belongs to another Actor");
            }
        }

        // ---- Context placement -------------------------------------------------------------

        /*
         * Runs one initialization segment with an entered Truffle Context. Context availability is
         * placement only, never suspension authority: an already entered Context (hosted scheduler
         * segment or test harness) is used directly; a hosted Process places the segment in its
         * execution host; otherwise this initialization opens one host-owned Context and keeps it
         * alive until the initialization truly ends or is abandoned, so every segment of a
         * suspended continuation re-enters the same Context.
         */
        private <T> T inContext(Supplier<T> segment) {
            if (placement == null) {
                ProtosProcessRuntime process = actor.processForRuntime().orElse(null);
                ProtosLanguageContext entered = ProtosLanguageContext.currentIfEnteredForRuntime();
                if (entered != null) {
                    placement = Placement.ENTERED;
                    segmentContext = entered;
                } else if (process != null && process.executionHostForRuntime().isPresent()) {
                    placement = Placement.HOSTED;
                    hostedProcess = process;
                } else {
                    placement = Placement.OWNED;
                }
            }
            // Carrier changes are allowed; Context changes are not. The identity is checked inside
            // the entered Context, before the segment runs any guest code.
            Supplier<T> checked =
                    () -> {
                        requireSegmentContext();
                        return segment.get();
                    };
            switch (placement) {
                case ENTERED -> {
                    return checked.get();
                }
                case HOSTED -> {
                    ProtosProcessRuntime process = actor.processForRuntime().orElse(null);
                    if (process != hostedProcess) {
                        throw new IllegalStateException(
                                "Actor bootstrap segment resumed outside its original Process");
                    }
                    // A hosted Process places each segment in its execution host; no own Context.
                    return process.callInExecutionHostForRuntime(checked);
                }
                default -> {
                    if (ownedContextClosed) {
                        throw new IllegalStateException(
                                "Actor bootstrap Context was closed before the segment");
                    }
                    if (ownedContext == null) {
                        ownedContext =
                                ProtosPolyglotExecutionContext.open(
                                        InputStream.nullInputStream(),
                                        OutputStream.nullOutputStream(),
                                        OutputStream.nullOutputStream());
                    }
                    // Segments are serialized by the Actor's scheduler (or the single synchronous
                    // host caller), so the owned Context is never entered concurrently.
                    return ownedContext.callEntered(checked);
                }
            }
        }

        /*
         * Binds the first segment's Truffle Context and rejects any later segment that entered
         * another one: a C-prime continuation is valid only in the Context that materialized it.
         */
        private void requireSegmentContext() {
            ProtosLanguageContext current = ProtosLanguageContext.currentIfEnteredForRuntime();
            if (current == null) {
                throw new IllegalStateException("Actor bootstrap segment requires an entered Context");
            }
            if (segmentContext == null) {
                segmentContext = current;
            } else if (current != segmentContext) {
                throw new SegmentContextMismatch();
            }
        }

        /** A segment entered a Context other than the one that materialized its continuation. */
        private static final class SegmentContextMismatch extends IllegalStateException {
            private static final long serialVersionUID = 1L;

            SegmentContextMismatch() {
                super("Actor bootstrap segment resumed under another Context");
            }
        }

        /*
         * I158: one executing segment, from before its Context is entered until its result has
         * been retained, abandoned or published. The domain reservation keeps the Actor out of
         * TERMINATED while it runs; it is released exactly once on every exit path.
         */
        private <T> T reserved(Supplier<T> segment) {
            ProtosActorExecutionDomain domain = actor.executionDomain();
            domain.beginBootstrapSegmentForRuntime();
            try {
                return segment.get();
            } finally {
                domain.finishBootstrapSegmentForRuntime();
            }
        }

        /*
         * Called only by the owner after the continuation ended or was abandoned, and never from
         * inside ownedContext.callEntered: every caller runs after its segment returned.
         */
        private void closeOwnedContext() {
            if (suspension != null && suspension.retainsContinuationForRuntime()) {
                // A retained C-prime continuation belongs to this Context; it is abandoned first.
                throw new IllegalStateException(
                        "Actor bootstrap Context cannot close while a continuation is retained");
            }
            ProtosPolyglotExecutionContext owned = ownedContext;
            ownedContext = null;
            ownedContextClosed = true;
            if (owned != null) {
                owned.close();
                java.util.function.Consumer<ProtosActor> probe = ownedContextClosedForTesting;
                if (probe != null) {
                    probe.accept(actor);
                }
            }
        }

        // ---- synchronous engine entry ---------------------------------------------------

        /*
         * The public synchronous initialize: the same engine and the same BOOTSTRAP authority. A
         * pending Future.value() suspends the continuation; this host caller (never a scheduler
         * carrier) then waits, dispatching only targeted completions without Task handoff, and
         * resumes the same continuation. No Task of this Actor runs and nothing is replayed.
         */
        private boolean runSynchronously() {
            prepareIdentity();
            try {
                return inContext(this::driveSynchronously);
            } catch (ProtosSignalException failure) {
                finishCustody();
                actor.failForRuntime(failure.error());
                throw failure;
            } catch (RuntimeException | Error hostFailure) {
                finishCustody();
                throw hostFailure;
            } finally {
                closeOwnedContext();
            }
        }

        private boolean driveSynchronously() {
            ProtosActorExecutionDomain domain = actor.executionDomain();
            Object pending = null;
            while (true) {
                Object resumable = pending;
                // One reservation per segment: running, then retaining or publishing its result.
                Boolean terminal =
                        reserved(
                                () -> {
                                    Object result =
                                            resumable == null
                                                    ? callRoot(true)
                                                    : resumeInMode(resumable);
                                    if (!ProtosTailCallContinuation.isPending(result)) {
                                        boolean ready = completeWith(result);
                                        finishCustody();
                                        return ready;
                                    }
                                    if (suspension == null) {
                                        suspension =
                                                new ProtosActorBootstrapSuspension(
                                                        this, actor, null);
                                    }
                                    return suspension.suspend(result) ? null : Boolean.FALSE;
                                });
                if (terminal != null) {
                    return terminal;
                }
                while (true) {
                    if (suspension.cleanupPending()) {
                        suspension.runCleanup();
                        return false;
                    }
                    pending = suspension.takeForResume();
                    if (pending != null) {
                        break;
                    }
                    if (domain.dispatchTargetedRuntimeCompletionWithoutHandoffForRuntime()) {
                        continue;
                    }
                    try {
                        domain.awaitBootstrapWakeupForRuntime(
                                () -> suspension.resumeReady() || suspension.cleanupPending());
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        actor.requestTerminationForRuntime();
                    }
                }
            }
        }

        // ---- scheduled engine entry -----------------------------------------------------

        /** The first control segment; runs once per initialization. */
        private void start() {
            if (actor.lifecycleState() != ProtosActor.LifecycleState.INITIALIZING) {
                // Termination won before the first segment: no guest code runs.
                report(false);
                return;
            }
            step(null);
        }

        /** One resumed segment of the same initialization, invoked by its custodian. */
        void resumeSegment(Object pending) {
            step(Objects.requireNonNull(pending, "pending"));
        }

        /*
         * Runs one initialization segment in BOOTSTRAP mode. The mode is removed when the segment
         * suspends or ends, and re-installed on whichever carrier resumes it. Suspension authority
         * comes only from executing this initialization; a hosted Process is not required.
         */
        private void step(Object pending) {
            reserved(
                    () -> {
                        stepReserved(pending);
                        return null;
                    });
        }

        private void stepReserved(Object pending) {
            Object result;
            try {
                if (pending == null) {
                    prepareIdentity();
                    result = inContext(() -> callRoot(true));
                } else {
                    result = inContext(() -> resumeInMode(pending));
                }
                if (ProtosTailCallContinuation.isPending(result)) {
                    if (suspension == null) {
                        suspension = new ProtosActorBootstrapSuspension(this, actor, scheduler);
                    }
                    if (!suspension.suspend(result)) {
                        report(false);
                    }
                    return;
                }
                boolean ready = completeWith(result);
                finishCustody();
                report(ready);
            } catch (ProtosSignalException failure) {
                finishCustody();
                actor.failForRuntime(failure.error());
                report(false);
            } catch (RuntimeException | Error hostFailure) {
                if (pending != null && hostFailure instanceof SegmentContextMismatch) {
                    // Rejected before any guest code: the taken continuation is never resumed.
                    ProtosTailCallContinuation.abandon(pending);
                }
                finishCustody();
                failInitialModuleIfOwned();
                hostFailed(hostFailure);
                if (hostFailure instanceof Error error) {
                    throw error;
                }
            }
        }

        private Object resumeInMode(Object pending) {
            HostEntryExtent previous =
                    actor.executionDomain().swapHostEntryExtentForRuntime(HostEntryExtent.BOOTSTRAP);
            try {
                return Objects.requireNonNull(
                        ProtosTailCallContinuation.resume(pending, ProtosNullValue.INSTANCE),
                        "resumed Actor bootstrap continuation returned null");
            } finally {
                actor.executionDomain().swapHostEntryExtentForRuntime(previous);
            }
        }

        private Object callRoot(boolean bootstrapMode) {
            ProtosActorExecutionDomain domain = actor.executionDomain();
            HostEntryExtent previous =
                    bootstrapMode
                            ? domain.swapHostEntryExtentForRuntime(HostEntryExtent.BOOTSTRAP)
                            : null;
            try {
                return Objects.requireNonNull(
                        ProtosActorBootstrapCPrimeExecution.planForEnteredContext()
                                .target()
                                .call(activation, this),
                        "Actor bootstrap C-prime root returned null");
            } finally {
                if (bootstrapMode) {
                    domain.swapHostEntryExtentForRuntime(previous);
                }
            }
        }

        // ---- C-prime root phases ---------------------------------------------------------

        @TruffleBoundary
        static ProtosPreparedCalls.PreparedClosureCall prepareModuleForRuntime(Object carrier) {
            return requireSequence(carrier).prepareModule();
        }

        @TruffleBoundary
        static ProtosPreparedCalls.PreparedClosureCall prepareBindingForRuntime(
                Object carrier, Object module) {
            return requireSequence(carrier).prepareBinding(module);
        }

        private static Sequence requireSequence(Object carrier) {
            if (!(carrier instanceof Sequence sequence)) {
                throw new IllegalStateException("Actor bootstrap root requires its owning sequence");
            }
            return sequence;
        }

        private ProtosPreparedCalls.PreparedClosureCall prepareModule() {
            if (preparedModule) {
                throw new IllegalStateException("Actor initial module was already prepared");
            }
            preparedModule = true;
            initialModule =
                    moduleRuntime.prepareActorInitialModule(
                            canonicalModuleKey,
                            activation,
                            actor.isRootActorForRuntime()
                                    ? rootBootstrapLocals(actor, prelude)
                                    : Map.of());
            return ProtosPreparedCalls.PreparedClosureCall.moduleInitialization(initialModule);
        }

        private ProtosPreparedCalls.PreparedClosureCall prepareBinding(Object moduleResult) {
            if (preparedBinding) {
                throw new IllegalStateException("Actor bootstrap binding was already prepared");
            }
            preparedBinding = true;
            if (!(moduleResult instanceof ProtosObjectValue module)) {
                throw new IllegalStateException("Actor initial module produced no module object");
            }
            // Own slot of the public MODULE object or SOURCE Context only: no private initializer
            // Context and no delegated lookup take part in the selection.
            Object bootstrapBinding =
                    module.readLocalSlot(bindingName).orElseThrow(() -> bootstrapError(activation));
            // The binding runs in its own module's environment, as every later message turn of
            // this Actor does: specifiers it resolves are relative to that canonical module.
            // D242: MODULE bootstrap invocation has the public module as its receiver. The Actor
            // bootstrap context remains private. SOURCE retains its complete context.
            invocation =
                    canonicalModuleKey.isModule()
                            ? prelude.newModuleInitializerActivation(
                                    actor.moduleState(),
                                    canonicalModuleKey,
                                    bootstrapContext,
                                    module,
                                    actor.executionDomain())
                            : prelude.newModuleActivation(
                                    actor.moduleState(),
                                    canonicalModuleKey,
                                    bootstrapContext,
                                    actor.executionDomain());
            // Transferred arguments are materialized exactly once, in the destination.
            List<?> arguments =
                    List.copyOf(
                            ProtosActorValueTransfer.materializeArguments(
                                    transferredArguments, invocation));
            // D242: MODULE exports are ordinary methods selected on the public object; invoking
            // the extracted Closure would discard the MODULE receiver and method home.
            return canonicalModuleKey.isModule()
                    ? ProtosSendPreparation.prepareSend(module, bindingName, invocation, arguments)
                    : ProtosClosureCallPreparation.prepareClosureCall(
                            bootstrapBinding, arguments, invocation);
        }

        // ---- terminal outcomes ----------------------------------------------------------

        private boolean completeWith(Object result) {
            // A represented Number is a semantic value, not an ordinary Actor behavior object.
            if (!(result instanceof ProtosObjectValue behavior)
                    || ProtosNumericValueSupport.isCurrentNumber(result)) {
                throw bootstrapError(invocation != null ? invocation : activation);
            }
            if (actor.lifecycleState() != ProtosActor.LifecycleState.INITIALIZING) {
                return false;
            }
            actor.bindMessageEnvironmentForRuntime(prelude, canonicalModuleKey);
            return actor.completeInitialization(behavior);
        }

        private void finishCustody() {
            if (suspension != null) {
                suspension.finished();
            }
        }

        /**
         * Called once by the custodian when termination invalidates this initialization.
         *
         * @param abandonedWhileSuspended whether a retained continuation was abandoned, so no
         *     segment of this initialization is running and its initial module record is dropped
         */
        void terminatedWhileInitializing(boolean abandonedWhileSuspended) {
            if (abandonedWhileSuspended) {
                failInitialModuleIfOwned();
            }
            report(false);
        }

        private void failInitialModuleIfOwned() {
            ProtosModuleRuntime.PreparedModuleInitialization owned = initialModule;
            if (owned != null) {
                owned.fail();
            }
        }

        private void report(boolean ready) {
            // The scheduled initialization is terminal: its owned Context may now be discarded.
            if (outcome != null || scheduler != null) {
                closeOwnedContext();
            }
            if (outcome != null) {
                outcome.complete(ready);
            }
        }

        private void hostFailed(Throwable hostFailure) {
            /*
             * A host failure owns the bootstrap outcome before termination can
             * invalidate this sequence and report false. Process termination is
             * still mandatory, but must not erase the original host cause.
             */
            if (outcome != null) {
                outcome.completeExceptionally(hostFailure);
            }
            try {
                closeOwnedContext();
            } catch (RuntimeException | Error cleanupFailure) {
                if (cleanupFailure != hostFailure) {
                    hostFailure.addSuppressed(cleanupFailure);
                }
            }
            ProtosProcessRuntime process =
                    actor.isRootActorForRuntime() ? actor.processForRuntime().orElse(null) : null;
            if (process != null) {
                process.requestTerminationForRuntime();
            } else {
                actor.requestTerminationForRuntime();
            }
        }
    }

    /**
     * D225-B1: starts the explicitly selected message-service bootstrap of the existing RootActor
     * of a hosted Process.
     *
     * <p>No new Actor is created and no second bootstrap algorithm exists: the RootActor is
     * attached to the Process host's ordinary Actor scheduler and the same {@link Sequence} engine
     * used by {@code Actor.spawn} runs as that Actor's control turn. It loads the canonical initial
     * module once with the RootActor's bootstrap-local slots, selects {@code bindingName} as a
     * local slot of that module, validates the result and performs the single READY cutover.
     *
     * <p>I158 / D251-A1: the initialization may suspend in pending {@code Future.value()} calls;
     * the carrier is then released and the returned Future stays pending. It completes only with
     * true at the READY cutover, false when initialization failed with a Protos Error (the RootActor
     * failure has already begun Process termination) or termination invalidated it, and
     * exceptionally on a host failure, which requests Process termination.
     */
    static CompletableFuture<Boolean> startRootService(
            ProtosProcessRuntime process,
            ProtosPrelude prelude,
            ProtosModuleKey canonicalModuleKey,
            String bindingName) {
        Objects.requireNonNull(process, "process");
        Objects.requireNonNull(prelude, "prelude");
        Objects.requireNonNull(canonicalModuleKey, "canonicalModuleKey");
        Objects.requireNonNull(bindingName, "bindingName");
        ProtosActor root = process.rootActorForRuntime();
        if (root.lifecycleState() != ProtosActor.LifecycleState.INITIALIZING) {
            throw new IllegalStateException(
                    "RootActor service bootstrap requires INITIALIZING lifecycle");
        }
        ProtosActorScheduler scheduler =
                process.executionHostForRuntime()
                        .flatMap(host -> host.actorSchedulerForRuntime())
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "RootActor service bootstrap requires a hosted Process"));
        ProtosModuleRuntime moduleRuntime = ProtosStandardImportProtocol.runtimeForPrelude(prelude);
        if (moduleRuntime == null) {
            throw new IllegalStateException("Core prelude has no standard import facility");
        }
        ProtosActorBootstrap bootstrap = new ProtosActorBootstrap(moduleRuntime);
        CompletableFuture<Boolean> outcome = new CompletableFuture<>();
        Runnable turn =
                bootstrap.controlTurn(
                        root,
                        scheduler,
                        prelude,
                        canonicalModuleKey,
                        bindingName,
                        List.of(),
                        outcome);
        scheduler.attach(root);
        try {
            scheduler.submitControl(root, turn);
        } catch (RuntimeException admissionFailure) {
            scheduler.detach(root);
            throw admissionFailure;
        }
        return outcome;
    }

    /**
     * Creates one non-importable standalone RootActor entry activation with the exact E2
     * bootstrap-local authority model before any entry source expression executes.
     */
    static ProtosActivation newStandaloneInitialActivation(
            ProtosProcessRuntime process, ProtosPrelude prelude) {
        Objects.requireNonNull(process, "process");
        Objects.requireNonNull(prelude, "prelude");
        if (process.lifecycleState() != ProtosProcessRuntime.LifecycleState.RUNNING) {
            throw new IllegalStateException(
                    "standalone entry bootstrap requires a running Process");
        }
        ProtosActor root = process.rootActorForRuntime();
        if (root.lifecycleState() != ProtosActor.LifecycleState.INITIALIZING) {
            throw new IllegalStateException(
                    "standalone RootActor entry requires INITIALIZING lifecycle");
        }
        ProtosObjectValue moduleContext = prelude.newExecutionContext();
        for (Map.Entry<String, Object> entry : rootBootstrapLocals(root, prelude).entrySet()) {
            moduleContext.createLocalSlot(entry.getKey(), entry.getValue());
        }
        ProtosActivation activation =
                prelude.newModuleActivation(
                        root.moduleState(), null, moduleContext, root.executionDomain());
        ProtosActorRefValue current =
                activation.executionDomain().currentActorReference().orElseThrow(
                        () -> new IllegalStateException(
                                "standalone RootActor execution domain lost its owner"));
        if (!ProtosIdentity.identical(current, root.reference())) {
            throw new IllegalStateException("standalone entry activation belongs to another Actor");
        }
        return activation;
    }

    private static Map<String, Object> rootBootstrapLocals(
            ProtosActor actor, ProtosPrelude prelude) {
        ProtosProcessRuntime process =
                actor.processForRuntime()
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "RootActor lost its owning Process runtime"));

        LinkedHashMap<String, Object> locals = new LinkedHashMap<>();
        locals.put(
                "process",
                process.provisionCapabilityForRuntime(
                        prelude.processPrototype()));
        process.rootFilesystemForRuntime()
                .ifPresent(filesystem -> locals.put("filesystem", filesystem));
        process.rootNetworkForRuntime()
                .ifPresent(
                        network -> {
                            if (network.representedDelegationParent(prelude)
                                    != prelude.networkPrototype()) {
                                throw new IllegalStateException(
                                        "RootActor Network grant belongs to another Core prelude");
                            }
                            locals.put("network", network);
                        });
        process.rootResolverForRuntime()
                .ifPresent(
                        resolver -> {
                            if (resolver.representedDelegationParent(prelude)
                                    != prelude.resolverPrototypeForRuntime()) {
                                throw new IllegalStateException(
                                        "RootActor Resolver grant belongs to another Core prelude");
                            }
                            locals.put("resolver", resolver);
                        });
        // D243-C: only the authorized RootActor gets this local.
        // Ordinary Actor.spawn never inherits the grant.
        process.rootEntropyForRuntime()
                .ifPresent(
                        entropy -> {
                            if (entropy.representedDelegationParent(prelude)
                                    != prelude.entropyPrototypeForRuntime()) {
                                throw new IllegalStateException(
                                        "RootActor Entropy grant belongs to another Core prelude");
                            }
                            locals.put("entropy", entropy);
                        });
        process.rootResourcesForRuntime()
                .ifPresent(
                        resources -> {
                            if (!resources.isFrozen()) {
                                throw new IllegalStateException(
                                        "RootActor resources grant must be frozen");
                            }
                            if (resources.parent().orElse(null) != prelude.mapPrototype()) {
                                throw new IllegalStateException(
                                        "RootActor resources grant belongs to another Core prelude");
                            }
                            locals.put("resources", resources);
                        });
        return Map.copyOf(locals);
    }

    private static ProtosSignalException bootstrapError(ProtosActivation activation) {
        return new ProtosSignalException(ProtosCoreErrors.newError(activation));
    }
}
