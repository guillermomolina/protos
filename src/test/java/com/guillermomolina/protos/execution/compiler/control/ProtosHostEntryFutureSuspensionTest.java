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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.guillermomolina.protos.runtime.ProtosActorExecutionDomain;
import com.guillermomolina.protos.runtime.ProtosFutureValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.oracle.truffle.api.interop.InteropLibrary;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * PLAT054-3E2 / I086: HOST-FUT-1 ({@code FUTURES_AND_TASKS.md} §29, Suspendible host-initiated
 * RootActor entries) through the public {@code Context} API. The observed Future {@code gate} is a
 * pending non-Task Future the test terminalizes from Java at chosen points, so every race is
 * ordered by explicit conditions (with bounded safety timeouts), never by sleeps.
 */
final class ProtosHostEntryFutureSuspensionTest {
    private static final String CORE = Path.of("protos", "lib", "core").toAbsolutePath().toString();
    private static final long SAFETY_SECONDS = 5;

    private static final String PRELUDE =
            """
            sample: (() => 1).future()
            sample.value()
            gateBox: { current: 0 }
            failure: Error()
            before: 0
            after: 0
            cleaned: 0
            """;

    private static Context newContext() {
        return Context.newBuilder(ProtosLanguage.ID).option("protos.CoreRoot", CORE).build();
    }

    private static ProtosEmbeddedProcess embedded(Context context) {
        context.enter();
        try {
            return ProtosLanguageContext.current().embeddedProcessOrNull();
        } finally {
            context.leave();
        }
    }

    /** Fixture: evaluates {@code PRELUDE + program} and plants a fresh pending {@code gate}. */
    private static final class Fixture {
        final Context context;
        final ProtosEmbeddedProcess process;
        final ProtosObjectValue module;
        final ProtosFutureValue gate;
        /** The value of the fixture evaluation's final expression. */
        final Value result;

        Fixture(Context context, String program) {
            this(context, program, "0");
        }

        Fixture(Context context, String program, String resultExpression) {
            this.context = context;
            result = context.eval(ProtosLanguage.ID, PRELUDE + program + "\n" + resultExpression + "\n");
            process = embedded(context);
            module = process.selectedModuleContext().orElseThrow();
            ProtosFutureValue sample = (ProtosFutureValue) module.readLocalSlot("sample").orElseThrow();
            gate =
                    new ProtosFutureValue(
                            (ProtosObjectValue) sample.parent().orElseThrow(), sample.domain());
            // D179: publish through ordinary object state observed by guest code.
            ProtosObjectValue gateBox =
                    (ProtosObjectValue) module.readLocalSlot("gateBox").orElseThrow();
            gateBox.assignLocalSlot("current", gate);
        }

        Value member(String name) {
            return context.getBindings(ProtosLanguage.ID).getMember(name);
        }

        long slot(String name) {
            return Long.parseLong(com.guillermomolina.protos.runtime.ProtosNumericValueSupport.integerDecimalText(module.readLocalSlot(name).orElseThrow()));
        }

        ProtosActorExecutionDomain domain() {
            return process.rootExecutionDomain();
        }

        void resolve(long value) {
            assertTrue(gate.resolve(Long.valueOf(value), null));
        }

        ProtosObjectValue failureError() {
            return (ProtosObjectValue) module.readLocalSlot("failure").orElseThrow();
        }
    }

    /** One host entry on its own thread, so the test thread can observe and terminalize. */
    private static final class Entry {
        final CompletableFuture<Object> outcome = new CompletableFuture<>();
        final Thread thread;

        Entry(Value executable, Object... arguments) {
            thread =
                    new Thread(
                            () -> {
                                try {
                                    Value result = executable.execute(arguments);
                                    outcome.complete(
                                            result.isBoolean()
                                                    ? (Object) result.asBoolean()
                                                    : result.fitsInLong() ? result.asLong() : result);
                                } catch (Throwable failure) {
                                    outcome.complete(failure);
                                }
                            },
                            "host-entry");
            thread.setDaemon(true);
            thread.start();
        }

        /** Waits until the entry reached its suspension point and its thread is parked. */
        void awaitSuspended(BooleanSupplier reached) {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(SAFETY_SECONDS);
            while (!(reached.getAsBoolean() && thread.getState() == Thread.State.WAITING)) {
                if (outcome.isDone()) {
                    Object early = outcome.join();
                    if (early instanceof Throwable failure) {
                        throw new AssertionError("host entry ended before suspending", failure);
                    }
                    fail("host entry returned " + early + " before suspending");
                }
                if (System.nanoTime() > deadline) {
                    fail("host entry did not suspend (thread state " + thread.getState() + ")");
                }
                Thread.onSpinWait();
            }
            assertFalse(outcome.isDone(), "a suspended entry has not returned to the host");
        }

        Object await() throws Exception {
            try {
                return outcome.get(SAFETY_SECONDS, TimeUnit.SECONDS);
            } finally {
                thread.join(TimeUnit.SECONDS.toMillis(SAFETY_SECONDS));
            }
        }
    }

    private static void assertCompactState(Fixture f) {
        assertEquals(0, f.domain().liveTaskCount(), "no Task, RootTask, or hidden waiter Task");
        assertEquals(
                ProtosActorExecutionDomain.HostEntryExtent.NONE,
                f.domain().hostEntryExtentForRuntime());
        assertFalse(f.process.actorCarrierSubstrateInitializedForTesting(), "no Actor carriers");
    }

    private static final String SUSPENDING_RUN =
            """
            run: () => {
                before = before + 1
                v: gateBox.current.value()
                after = after + 1
                v + 1
            }
            """;

    @Test
    void trivialCallsKeepTheCompactPathAndAllocateNoSuspensionMachinery() {
        try (Context context = newContext()) {
            Fixture f = new Fixture(context, "trivial: () => { 1 }\nidentity: (x) => { x }");
            ProtosEmbeddedProcess process = f.process;
            Value trivial = f.member("trivial");
            Value identity = f.member("identity");
            for (int index = 0; index < 5; index++) {
                assertEquals(1, trivial.execute().asInt());
                assertEquals(index, identity.execute(index).asInt());
                assertCompactState(f);
                assertSame(process, embedded(context), "no Process or session per call");
            }
        }
    }

    /**
     * I115: the fast-entry certificate is fixed when the
     * embedding adapter is prepared. It is not inferred from
     * whether a particular invocation happens to suspend.
     */
    @Test
    void nonSuspendingHostEntryIsSelectedFromEffectiveRootFacts() {
        try (Context context = newContext()) {
            Fixture f =
                    new Fixture(
                            context,
                            """
                            trivial: () => { 1 }
                            identity: (value) => { value }
                            suspending: () => { gateBox.current.value() }
                            """);

            ProtosHostExecutableClosure trivial;
            ProtosHostExecutableClosure identity;
            ProtosHostExecutableClosure suspending;

            context.enter();
            try {
                trivial =
                        (ProtosHostExecutableClosure)
                                f.process.readSelectedMember(
                                        f.module,
                                        f.module.readLocalSlot("trivial").orElseThrow());
                identity =
                        (ProtosHostExecutableClosure)
                                f.process.readSelectedMember(
                                        f.module,
                                        f.module.readLocalSlot("identity").orElseThrow());
                suspending =
                        (ProtosHostExecutableClosure)
                                f.process.readSelectedMember(
                                        f.module,
                                        f.module.readLocalSlot("suspending").orElseThrow());
            } finally {
                context.leave();
            }

            assertTrue(
                    trivial.fastNonSuspendingHostEntry,
                    "literal root is admitted without suspension");
            assertTrue(
                    identity.fastNonSuspendingHostEntry,
                    "ordinary identity root is non-suspending");
            assertFalse(
                    suspending.fastNonSuspendingHostEntry,
                    "Future observation retains the conventional entry");

            assertTrue(trivial.fastNoHomeHostEntry);
            assertTrue(identity.fastNoHomeHostEntry);
            assertFalse(suspending.fastNoHomeHostEntry);

            assertTrue(f.process.permitsUnadmittedNonSuspendingHostEntry());

            for (int index = 0; index < 5; index++) {
                assertEquals(1, f.member("trivial").execute().asInt());
                assertEquals(index, f.member("identity").execute(index).asInt());
                assertCompactState(f);
            }
        }
    }

    /**
     * I115-H6: an ownership token already present does not select a conventional nested entry.
     * The experimental source-backed entry neither consults nor changes RootActor ownership.
     */
    @Test
    void occupiedRootActorDoesNotAlterExperimentalEntry() {
        try (Context context = newContext()) {
            Fixture f = new Fixture(context, "trivial: () => { 1 }\nidentity: (value) => { value }");
            Value trivial = f.member("trivial");
            Value identity = f.member("identity");

            Thread token = f.process.enterRootActor();
            try {
                assertFalse(f.process.permitsUnadmittedNonSuspendingHostEntry());

                assertEquals(1, trivial.execute().asInt());
                assertEquals(5, identity.execute(5).asInt());

                assertFalse(
                        f.process.permitsUnadmittedNonSuspendingHostEntry(),
                        "the entry neither released nor reacquired the present token");
                assertCompactState(f);
            } finally {
                f.process.exitRootActor(token);
            }

            assertTrue(f.process.permitsUnadmittedNonSuspendingHostEntry());
            assertEquals(1, trivial.execute().asInt());
            assertCompactState(f);
        }
    }

    /**
     * I115-H6 / PERF042: the primitive-closure-call benchmark workload runs through the experimental
     * host entry, which does not consult the root non-suspension proof.
     */
    @Test
    void benchmarkClosureCallRunsThroughExperimentalHostEntry() {
        try (Context context = newContext()) {
            Fixture f =
                    new Fixture(
                            context,
                            """
                            run: () => {
                                identity: (value) => { value }
                                identity(1)
                            }
                            """);

            for (int index = 0; index < 5; index++) {
                assertEquals(1, f.member("run").execute().asInt());
                assertCompactState(f);
            }
        }
    }

    /**
     * I115: an embedding Call1 uses the existing selected B5
     * frame, retaining exact Closure and caller provenance.
     */
    @Test
    void oneArgumentHostCallUsesSelectedB5Preparation() throws Exception {
        try (Context context = newContext()) {
            Fixture f = new Fixture(context, "identity: (value) => { value }");

            context.enter();
            try {
                ProtosHostExecutableClosure adapter =
                        (ProtosHostExecutableClosure)
                                f.process.readSelectedMember(
                                        f.module,
                                        f.module.readLocalSlot("identity").orElseThrow());

                assertTrue(adapter.fastNonSuspendingHostEntry);

                Long supplied = Long.valueOf(7);

                // I115: the host entry calls through the shared dispatcher with
                // exactly this B5 function header.
                ProtosDispatchTestSupport.DirectEntry prepared =
                        ProtosDispatchTestSupport.direct(adapter.closure, adapter.caller, supplied);

                Object[] frame = prepared.targetArguments();

                assertEquals(
                        ProtosFrameArguments.HEADER_LENGTH + 1,
                        frame.length);
                assertSame(adapter.closure, frame[0]);
                assertSame(adapter.caller, frame[3]);
                assertSame(
                        supplied,
                        frame[ProtosFrameArguments.HEADER_LENGTH]);
                assertSame(adapter.target, prepared.target());
            } finally {
                context.leave();
            }

            assertEquals(7, f.member("identity").execute(7).asInt());
            assertEquals(-7, f.member("identity").execute(-7).asInt());
            assertCompactState(f);
        }
    }

    /**
     * I115: a dynamic terminal call and a potentially
     * suspending argument or parameter default cannot borrow
     * the static Call1 certificate.
     */
    @Test
    void staticCallProofRejectsDynamicAndSuspendingEntries() {
        try (Context context = newContext()) {
            Fixture f =
                    new Fixture(
                            context,
                            """
                            dynamic: (callback) => {
                                callback(1)
                            }
                            suspendingArgument: () => {
                                identity: (value) => { value }
                                identity(gateBox.current.value())
                            }
                            suspendingDefault: (value: gateBox.current.value()) => {
                                identity: (item) => { item }
                                identity(1)
                            }
                            """);

            for (String name : java.util.List.of(
                    "dynamic",
                    "suspendingArgument",
                    "suspendingDefault")) {
                ProtosHostExecutableClosure adapter;

                context.enter();
                try {
                    adapter =
                            (ProtosHostExecutableClosure)
                                    f.process.readSelectedMember(
                                            f.module,
                                            f.module.readLocalSlot(name).orElseThrow());
                } finally {
                    context.leave();
                }

                assertFalse(
                        adapter.fastNonSuspendingHostEntry,
                        name + " must retain conventional host admission");
            }
        }
    }

    /**
     * I115-H6: a host call of a Closure that forwards its required parameter through a static
     * Call1 runs through the experimental host entry, independent of the root proof.
     */
    @Test
    void forwardedParameterCallRunsThroughExperimentalHostEntry() {
        try (Context context = newContext()) {
            Fixture f =
                    new Fixture(
                            context,
                            """
                            forwarded: (value) => {
                                identity: (item) => { item }
                                identity(value)
                            }
                            """);

            for (int value : new int[] {0, 1, 7, -7}) {
                assertEquals(value, f.member("forwarded").execute(value).asInt());
                assertCompactState(f);
            }
        }
    }

    /**
     * I115: the shared host entry (Polyglot execute through the shared dispatcher) performs one
     * compact invocation for zero- and one-argument source-backed Closures.
     */
    @Test
    void sharedHostEntryPerformsOneCompactInvocation() throws Exception {
        try (Context context = newContext()) {
            Fixture f =
                    new Fixture(
                            context,
                            """
                            trivial: () => { 1 }
                            identity: (value) => { value }
                            """);

            context.enter();
            try {
                ProtosHostExecutableClosure trivial =
                        (ProtosHostExecutableClosure)
                                f.process.readSelectedMember(
                                        f.module,
                                        f.module.readLocalSlot("trivial").orElseThrow());
                ProtosHostExecutableClosure identity =
                        (ProtosHostExecutableClosure)
                                f.process.readSelectedMember(
                                        f.module,
                                        f.module.readLocalSlot("identity").orElseThrow());

                Object literal = InteropLibrary.getUncached().execute(trivial);
                assertEquals("1", decimal(literal));

                Object forwarded = InteropLibrary.getUncached().execute(identity, 37);
                assertEquals("37", decimal(forwarded));

                Object guest = InteropLibrary.getUncached().execute(
                        identity, Long.valueOf(-4));
                assertEquals("-4", decimal(guest));

                assertCompactState(f);
            } finally {
                context.leave();
            }
        }
    }

    /** Decimal text of a raw guest Integer result, which H6 no longer projects. */
    private static String decimal(Object integer) {
        return integer instanceof Long raw
                ? Long.toString(raw)
                : com.guillermomolina.protos.runtime.ProtosNumericValueSupport.integerDecimalText(
                        integer);
    }

    /**
     * A retained executable cannot enter its Process after termination.
     */
    @Test
    void terminatedProcessRejectsRetainedHostExecutable() {
        try (Context context = newContext()) {
            Fixture f = new Fixture(context, "trivial: () => { 1 }");
            Value trivial = f.member("trivial");

            assertEquals(1, trivial.execute().asInt());

            f.process.processForTesting().requestTerminationForRuntime();

            assertFalse(f.process.isLive());
            assertFalse(f.process.permitsUnadmittedNonSuspendingHostEntry());

            assertThrows(org.graalvm.polyglot.PolyglotException.class, () -> trivial.execute());
            assertFalse(f.process.isLive(), "rejected entry does not restart the Process");
        }
    }

    @Test
    void alreadyResolvedFutureReturnsImmediately() {
        try (Context context = newContext()) {
            Fixture f = new Fixture(context, SUSPENDING_RUN);
            f.resolve(41);
            assertEquals(42, f.member("run").execute().asInt());
            assertEquals(1, f.slot("before"));
            assertEquals(1, f.slot("after"));
            assertCompactState(f);
        }
    }

    /**
     * A pending observation keeps the host entry synchronous and resumes its exact continuation
     * after terminalization, without repeating effects before the observation.
     */
    @Test
    void pendingFutureObservationResumesWithoutRepeatingEffects() throws Exception {
        try (Context context = newContext()) {
            Fixture f = new Fixture(context, SUSPENDING_RUN);
            Entry entry = new Entry(f.member("run"));
            entry.awaitSuspended(() -> f.gate.observerCountForTesting() == 1);
            assertEquals(1, f.slot("before"));
            assertEquals(0, f.slot("after"), "nothing after the observation point ran");
            f.resolve(41);
            assertEquals(42L, entry.await(), "host entry returns only its final result");
            assertEquals(1, f.slot("before"), "effects before suspension are not repeated");
            assertEquals(1, f.slot("after"));
            assertEquals(0, f.gate.observerCountForTesting());
            assertCompactState(f);
        }
    }

    /**
     * The same retained executable crosses its cached host entry repeatedly: a synchronous entry
     * returns its exact guest result object, a later outermost entry suspends and resumes without
     * replaying the call, and the host-entry extent is restored after each exit.
     */
    @Test
    void retainedExecutableAlternatesSynchronousAndSuspendedEntries() throws Exception {
        try (Context context = newContext()) {
            Fixture f =
                    new Fixture(
                            context,
                            SUSPENDING_RUN
                                    + """
                                    shared: { marker: 1 }
                                    sharedResult: () => { shared }
                                    isShared: (candidate) => { candidate === shared }
                                    """);
            Value run = f.member("run");
            Value sharedResult = f.member("sharedResult");
            Value isShared = f.member("isShared");
            for (int index = 0; index < 5; index++) {
                Value result = sharedResult.execute();
                assertTrue(
                        isShared.execute(result).asBoolean(),
                        "the exact guest result object crosses the cached entry");
                assertCompactState(f);
            }

            Entry entry = new Entry(run);
            entry.awaitSuspended(() -> f.gate.observerCountForTesting() == 1);
            assertEquals(
                    ProtosActorExecutionDomain.HostEntryExtent.NONE,
                    f.domain().hostEntryExtentForRuntime(),
                    "a waiting entry does not leave its extent installed");
            f.resolve(41);
            assertEquals(42L, entry.await());
            assertEquals(1, f.slot("before"), "the resumed call was not repeated");
            assertEquals(1, f.slot("after"));
            assertCompactState(f);
        }
    }

    /** A suspended entry resumed with an Error propagates it and restores the extent. */
    @Test
    void suspendedEntryResumedWithAnErrorPropagatesIt() throws Exception {
        try (Context context = newContext()) {
            Fixture f = new Fixture(context, SUSPENDING_RUN);
            Entry entry = new Entry(f.member("run"));
            entry.awaitSuspended(() -> f.gate.observerCountForTesting() == 1);
            assertTrue(f.gate.fail(f.failureError()));
            Object outcome = entry.await();
            assertTrue(
                    outcome instanceof org.graalvm.polyglot.PolyglotException,
                    "the resumed Error reaches the host: " + outcome);
            assertEquals(1, f.slot("before"), "the call was not repeated");
            assertEquals(0, f.slot("after"), "nothing after the failed observation ran");
            assertEquals(0, f.gate.observerCountForTesting());
            assertEquals(
                    ProtosActorExecutionDomain.HostEntryExtent.NONE,
                    f.domain().hostEntryExtentForRuntime());
            assertEquals(0, f.domain().liveTaskCount());
        }
    }

    /** Cancellation while the outermost entry waits resumes the entry with the cancellation. */
    @Test
    void cancellationWhileWaitingResumesTheHandledEntry() throws Exception {
        try (Context context = newContext()) {
            Fixture f =
                    new Fixture(
                            context,
                            """
                            run: () => {
                                before = before + 1
                                Error.handle(() => { gateBox.current.value() }, (caught) => { 9 })
                            }
                            """);
            Entry entry = new Entry(f.member("run"));
            entry.awaitSuspended(() -> f.gate.observerCountForTesting() == 1);
            assertTrue(f.gate.cancelTerminal());
            assertEquals(9L, entry.await());
            assertEquals(1, f.slot("before"), "the call was not repeated");
            assertEquals(0, f.gate.observerCountForTesting());
            assertCompactState(f);
        }
    }

    /** Termination while the outermost entry waits abandons it; it never resumes. */
    @Test
    void terminationWhileWaitingAbandonsTheEntry() throws Exception {
        try (Context context = newContext()) {
            Fixture f = new Fixture(context, SUSPENDING_RUN);
            Entry entry = new Entry(f.member("run"));
            entry.awaitSuspended(() -> f.gate.observerCountForTesting() == 1);
            f.process.processForTesting().requestTerminationForRuntime();
            Object outcome = entry.await();
            assertTrue(outcome instanceof Throwable, "a terminated entry never resumes: " + outcome);
            assertEquals(0, f.slot("after"), "nothing after the observation ran");
            assertFalse(f.process.isLive());
            assertEquals(0, f.gate.observerCountForTesting());
        }
    }

    /**
     * A re-entrant (nested, same-thread) host entry is a synchronous foreign callback: a pending
     * observation is rejected with an Error instead of suspending, the Process survives, the held
     * RootActor admission is untouched, and the outer extent is restored exactly.
     */
    @Test
    void reentrantCallbackCannotSuspend() {
        try (Context context = newContext()) {
            Fixture f = new Fixture(context, SUSPENDING_RUN);
            Value run = f.member("run");
            Thread token = f.process.enterRootActor();
            try {
                assertThrows(org.graalvm.polyglot.PolyglotException.class, () -> run.execute());
                assertEquals(0, f.gate.observerCountForTesting(), "no wait was installed");
                assertEquals(0, f.slot("after"));
                assertTrue(f.process.isLive(), "a callback Error does not fail the RootActor");
                assertFalse(f.process.permitsUnadmittedNonSuspendingHostEntry());
                assertEquals(
                        ProtosActorExecutionDomain.HostEntryExtent.NONE,
                        f.domain().hostEntryExtentForRuntime());
            } finally {
                f.process.exitRootActor(token);
            }
            assertTrue(f.process.permitsUnadmittedNonSuspendingHostEntry());
            f.resolve(41);
            assertEquals(42, run.execute().asInt(), "the outermost entry is admitted again");
            assertCompactState(f);
        }
    }

    @Test
    void alreadyCancelledFutureSignalsCancelledWithoutSuspension() {
        try (Context context = newContext()) {
            Fixture f =
                    new Fixture(
                            context,
                            "run: () => { Error.handle(() => { gateBox.current.value() }, (caught) => { 9 }) }");
            assertEquals(ProtosFutureValue.State.CANCELLED, cancelledBeforeEntry(f));
            assertEquals(9, f.member("run").execute().asInt(), "already cancelled");
        }
    }

    private static ProtosFutureValue.State cancelledBeforeEntry(Fixture f) {
        assertTrue(f.gate.cancelTerminal());
        return f.gate.state();
    }

    /*
     * Map keys whose custom hash or == observes the pending gate. The stored
     * key shares the waiting key's hash, so the waiting equality is reached
     * at the first candidate; mutate succeeds only outside every comparison
     * scope of m.
     */
    private static final String SUSPENDING_MAP_KEYS =
            """
            m: Map()
            WaitingHash: {
                hash: () => {
                    before = before + 1
                    gateBox.current.value()
                }
            }
            waiting: WaitingHash {}
            Plain: {
                hash: () => 41
                equalTo: (other) => false
            }.alias("equalTo", "==")
            WaitingEquality: {
                hash: () => 41
                equalTo: (other) => {
                    before = before + 1
                    gateBox.current.value() == 1
                }
            }.alias("equalTo", "==")
            first: Plain {}
            second: Plain {}
            m[first] = "first"
            m[second] = "second"
            probe: WaitingEquality {}
            runHash: () => {
                r: %{ 1: "one"; waiting: "w"; 2: "two" }
                after = after + 1
                (r.size() == 3) && (r[1] == "one") && (r[2] == "two")
            }
            runEquality: () => {
                r: m.atIfAbsent(probe, () => "none")
                after = after + 1
                r == "first"
            }
            mutate: () => {
                m["late"] = 1
                m.size()
            }
            runEqualityHandled: () => {
                r: Error.handle(() => m.atIfAbsent(probe, () => "none"), (caught) => true)
                after = after + 1
                r && (mutate() == 3)
            }
            """;

    @Test
    void customMapKeyHashSuspendsAndResumesWithoutReplay() throws Exception {
        try (Context context = newContext()) {
            Fixture f = new Fixture(context, SUSPENDING_MAP_KEYS);
            Entry entry = new Entry(f.member("runHash"));
            entry.awaitSuspended(() -> f.gate.observerCountForTesting() == 1);
            assertEquals(1, f.slot("before"));
            assertEquals(0, f.slot("after"));
            f.resolve(41);
            assertEquals(Boolean.TRUE, entry.await());
            assertEquals(1, f.slot("before"), "the suspended hash was not repeated");
            assertEquals(1, f.slot("after"));
        }
    }

    @Test
    void customMapKeyEqualitySuspendsAtItsCandidateAndLeavesTheComparisonScope()
            throws Exception {
        try (Context context = newContext()) {
            Fixture f = new Fixture(context, SUSPENDING_MAP_KEYS);
            Value mutate = f.member("mutate");
            Entry entry = new Entry(f.member("runEquality"));
            entry.awaitSuspended(() -> f.gate.observerCountForTesting() == 1);
            assertEquals(1, f.slot("before"));
            f.resolve(1);
            assertEquals(Boolean.TRUE, entry.await(), "resumed at the first candidate");
            assertEquals(1, f.slot("before"), "no equality was repeated");
            assertEquals(1, f.slot("after"));
            assertEquals(3L, mutate.execute().asLong(), "comparison scope left");
        }
    }

    @Test
    void customMapKeyEqualityErrorPropagatesAndLeavesTheComparisonScope() throws Exception {
        // An Error escaping a host entry ends its Process, so the guest handles
        // it and then mutates m inside the same entry.
        try (Context context = newContext()) {
            Fixture f = new Fixture(context, SUSPENDING_MAP_KEYS);
            Entry entry = new Entry(f.member("runEqualityHandled"));
            entry.awaitSuspended(() -> f.gate.observerCountForTesting() == 1);
            assertTrue(f.gate.fail(f.failureError()));
            assertEquals(Boolean.TRUE, entry.await(), "Error handled; comparison scope left");
            assertEquals(1, f.slot("before"), "no equality was repeated");
            assertEquals(1, f.slot("after"));
        }
    }

    @Test
    void nativeClosureEntryOnTheGenericPathSuspendsThroughItsCallbacks() throws Exception {
        try (Context context = newContext()) {
            // The extracted native Object.caseOf is receiver-bound to the module context
            // (CALLABLES.md §11), which caseOf accepts as its subject; it has no compact target,
            // so the entry takes the generic path and its action reaches the pending Future.
            Fixture f =
                    new Fixture(
                            context,
                            """
                            dispatch: Object.caseOf
                            cases: %{
                                Capture: (subject) => {
                                    before = before + 1
                                    gateBox.current.value() + 1
                                }
                            }
                            """,
                            "cases");
            Entry entry = new Entry(f.member("dispatch"), f.result);
            entry.awaitSuspended(() -> f.slot("before") == 1);
            f.resolve(6);
            assertEquals(7L, entry.await(), "the native caseOf entry suspended and resumed");
            assertEquals(1, f.slot("before"), "no replay through the native entry");
            assertEquals(0, f.gate.observerCountForTesting());
            assertCompactState(f);
        }
    }

    /**
     * I176 / D263-C: contextual {@code match} and its explicit {@code caseOf} expansion suspend
     * identically at the subject, a key expression, a reached matcher, and the selected action,
     * and resume without replaying completed effects.
     */
    @ParameterizedTest(name = "{0} / {1}: {2}")
    @MethodSource("i176Variants")
    void i176ContextualMatchSuspendsLikeExplicitCaseOfWithoutReplay(
            String position, String spelling, String selection, long resolution)
            throws Exception {
        String waitAt =
                """
                waitAt: () => {
                    before = before + 1
                    gateBox.current.value()
                }
                Gate: {
                    match: (subject) => waitAt() == subject
                }
                """;
        runSuspendedOnce(
                position + " / " + spelling + ": " + selection,
                waitAt
                        + "run: () => {\n"
                        + "    r: "
                        + selection
                        + "\n    after = after + 1\n"
                        + "    r\n"
                        + "}\n",
                resolution,
                42L);
    }

    /* The four suspension positions, each in its contextual and explicit spelling. */
    static Stream<Arguments> i176Variants() {
        String[][] cases = {
            {"subject", "match(waitAt()) { Capture: v => v + 1 }",
                "waitAt().caseOf(%{ Capture: v => v + 1 })", "41"},
            {"key", "match(1) { waitAt(): 42; Any: 0 }",
                "1.caseOf(%{ waitAt(): () => 42; Any: () => 0 })", "1"},
            {"matcher", "match(41) { Gate: 42; Any: 0 }",
                "41.caseOf(%{ Gate: () => 42; Any: () => 0 })", "41"},
            {"action", "match(1) { 1: waitAt() + 1 }",
                "1.caseOf(%{ 1: () => waitAt() + 1 })", "41"},
        };
        Stream.Builder<Arguments> variants = Stream.builder();
        for (String[] variant : cases) {
            long resolution = Long.parseLong(variant[3]);
            variants.add(Arguments.of(variant[0], "contextual", variant[1], resolution));
            variants.add(Arguments.of(variant[0], "explicit", variant[2], resolution));
        }
        return variants.build();
    }

    /* The suspending key expression shared by the isolation tests below. */
    private static final String WAIT_AT =
            """
            waitAt: () => {
                before = before + 1
                gateBox.current.value()
            }
            """;

    /**
     * Runs {@code run} of {@code program} as one host entry that suspends
     * exactly once on the gate, resolves the gate with {@code resolution} and
     * asserts the exact result without replay of completed effects.
     * {@code afterCompletion} runs further host-side assertions while the
     * Context is still open.
     */
    private static void runSuspendedOnce(
            String stage, String program, long resolution, Object expected) throws Exception {
        runSuspendedOnce(stage, program, resolution, expected, fixture -> {});
    }

    private static void runSuspendedOnce(
            String stage,
            String program,
            long resolution,
            Object expected,
            java.util.function.Consumer<Fixture> afterCompletion)
            throws Exception {
        runSuspendedOnce(stage, program, resolution, expected, fixture -> {}, afterCompletion);
    }

    /*
     * As above; {@code whileSuspended} runs host-side while the entry is
     * parked on the gate, before the gate is resolved.
     */
    private static void runSuspendedOnce(
            String stage,
            String program,
            long resolution,
            Object expected,
            java.util.function.Consumer<Fixture> whileSuspended,
            java.util.function.Consumer<Fixture> afterCompletion)
            throws Exception {
        try (Context context = newContext()) {
            Fixture f = new Fixture(context, program);
            Entry entry = new Entry(f.member("run"));
            entry.awaitSuspended(() -> f.gate.observerCountForTesting() == 1);
            assertEquals(1, f.gate.observerCountForTesting(), stage + ": one gate waiter");
            assertEquals(1, f.slot("before"), stage + ": before resolution");
            assertEquals(0, f.slot("after"), stage + ": before resolution");
            whileSuspended.accept(f);
            f.resolve(resolution);
            Object outcome = entry.await();
            if (outcome instanceof Throwable failure) {
                throw new AssertionError(stage + ": entry failed after resolution", failure);
            }
            assertEquals(expected, outcome, stage + ": result");
            assertEquals(1, f.slot("before"), stage + ": no replay");
            assertEquals(1, f.slot("after"), stage + ": completed once");
            assertEquals(0, f.gate.observerCountForTesting(), stage + ": no remaining observer");
            assertCompactState(f);
            afterCompletion.accept(f);
        }
    }

    /* Map construction alone: key suspension, resumption, hash, insertion, second entry. */
    @Test
    void mapLiteralKeyExpressionSuspendsWithoutReplay() throws Exception {
        runSuspendedOnce(
                "suspended key, construction only",
                WAIT_AT
                        + """
                        run: () => {
                            table: %{ waitAt(): 42; Any: 0 }
                            after = after + 1
                            table.size()
                        }
                        """,
                1L,
                2L);
    }

    /* The resumed key is the exact Integer 1 and both associations are found. */
    @Test
    void mapLiteralResumedKeyRetainsExactValue() throws Exception {
        runSuspendedOnce(
                "suspended key, lookups",
                WAIT_AT
                        + """
                        run: () => {
                            table: %{ waitAt(): 42; Any: 0 }
                            after = after + 1
                            (table.size() == 2) &&
                                (table[1] == 42) &&
                                (table[Any] == 0)
                        }
                        """,
                1L,
                Boolean.TRUE);
    }

    /* The recorded associations, observed host-side without any guest Map accessor. */
    @Test
    void mapLiteralResumedKeyPreservesAssociationSnapshot() throws Exception {
        runSuspendedOnce(
                "suspended key, association snapshot",
                "observedTable: null\n"
                        // Any as the guest's ordinary binding resolution sees it.
                        + "observedAny: Any\n"
                        + WAIT_AT
                        + """
                        run: () => {
                            observedTable = %{ waitAt(): 42; Any: 0 }
                            after = after + 1
                            true
                        }
                        """,
                1L,
                Boolean.TRUE,
                f -> {
                    Object table = f.module.readLocalSlot("observedTable").orElseThrow();
                    assertTrue(
                            table instanceof com.guillermomolina.protos.runtime.ProtosMapValue,
                            "observedTable is a Map: " + table);
                    com.guillermomolina.protos.runtime.ProtosIndexedSnapshot snapshot =
                            ((com.guillermomolina.protos.runtime.ProtosMapValue) table)
                                    .associationIndexedSnapshot();
                    Object any = f.module.readLocalSlot("observedAny").orElseThrow();
                    assertEquals(2, snapshot.length());
                    assertEquals("1", integerText(snapshot.keyAt(0)));
                    assertEquals("42", integerText(snapshot.valueAt(0)));
                    assertSame(any, snapshot.keyAt(1));
                    assertEquals("0", integerText(snapshot.valueAt(1)));
                });
    }

    private static String integerText(Object value) {
        assertTrue(
                com.guillermomolina.protos.runtime.ProtosNumericValueSupport.isCurrentInteger(value),
                "a semantic Integer: " + value);
        return com.guillermomolina.protos.runtime.ProtosNumericValueSupport.integerDecimalText(value);
    }

    /* The resumed value and the ordinary match send, without any Map. */
    @Test
    void standaloneMatchAfterKeyExpressionSuspension() throws Exception {
        runSuspendedOnce(
                "resumed value, match",
                WAIT_AT
                        + """
                        run: () => {
                            key: waitAt()
                            result: key.match(1)
                            after = after + 1
                            result
                        }
                        """,
                1L,
                Boolean.TRUE);
    }

    /*
     * Repeated standalone match in one Context: the first entry suspends and
     * resumes; the second observes the already resolved gate. Neither depends
     * on another Context having invoked Object.match.
     */
    @Test
    void standaloneMatchRepeatedInOneContextAfterSuspension() throws Exception {
        try (Context context = newContext()) {
            Fixture f = new Fixture(context, WAIT_AT
                    + """
                    run: () => {
                        key: waitAt()
                        result: key.match(1)
                        after = after + 1
                        result
                    }
                    """);
            Entry first = new Entry(f.member("run"));
            first.awaitSuspended(() -> f.gate.observerCountForTesting() == 1);
            f.resolve(1);
            assertEquals(Boolean.TRUE, first.await(), "first execution");
            assertEquals(1, f.slot("before"));
            assertEquals(1, f.slot("after"));
            assertEquals(Boolean.TRUE, f.member("run").execute().asBoolean(), "repeated execution");
            assertEquals(2, f.slot("before"));
            assertEquals(2, f.slot("after"));
            assertCompactState(f);
        }
    }

    /* A source Closure created and first invoked after resumption selects standard ==. */
    @Test
    void firstSourceNumericInvocationAfterSuspension() throws Exception {
        runSuspendedOnce(
                "first invocation after resumption, equality",
                WAIT_AT
                        + """
                        run: () => {
                            key: waitAt()
                            check: (value) => { value == 1 }
                            result: check(key)
                            after = after + 1
                            result
                        }
                        """,
                1L,
                Boolean.TRUE);
    }

    /* Addition, ordering and equality through source methods first sent after resumption. */
    @Test
    void sourceNumericOperationsAfterSuspension() throws Exception {
        runSuspendedOnce(
                "addition, ordering and equality after resumption",
                WAIT_AT
                        + """
                        ops: {
                            add: (a, b) => { a + b }
                            less: (a, b) => { a < b }
                            same: (a, b) => { a == b }
                        }
                        run: () => {
                            key: waitAt()
                            sum: ops.add(key, 2)
                            ordered: ops.less(key, 2)
                            equal: ops.same(key, 1)
                            after = after + 1
                            (sum == 3) && ordered && equal
                        }
                        """,
                1L,
                Boolean.TRUE);
    }

    /*
     * The inherited Object.match body entered after resumption selects the
     * receiver's custom == exactly once; standard Number equality between a
     * custom object and 5 would answer false.
     */
    @Test
    void inheritedMatchSelectsCustomEqualityAfterSuspension() throws Exception {
        runSuspendedOnce(
                "custom equality through inherited match",
                WAIT_AT
                        + """
                        equalities: 0
                        Custom: {
                            equalTo: (other) => {
                                equalities = equalities + 1
                                other == 5
                            }
                        }.alias("equalTo", "==")
                        custom: Custom {}
                        run: () => {
                            key: waitAt()
                            result: custom.match(key + 4)
                            after = after + 1
                            result
                        }
                        """,
                1L,
                Boolean.TRUE,
                f -> assertEquals(1, f.slot("equalities"), "custom equality ran exactly once"));
    }

    /* The resumed value and the ordinary == send, without any Map. */
    @Test
    void ordinaryEqualityAfterKeyExpressionSuspension() throws Exception {
        runSuspendedOnce(
                "resumed value, equality",
                WAIT_AT
                        + """
                        run: () => {
                            key: waitAt()
                            result: key == 1
                            after = after + 1
                            result
                        }
                        """,
                1L,
                Boolean.TRUE);
    }

    /* caseOf over a table constructed by an earlier statement, not staged as its argument. */
    @Test
    void caseOfWithPreviouslyConstructedSuspendedTable() throws Exception {
        runSuspendedOnce(
                "constructed table, caseOf",
                WAIT_AT
                        + """
                        run: () => {
                            table: %{ waitAt(): () => 42; Any: () => 0 }
                            result: 1.caseOf(table)
                            after = after + 1
                            result
                        }
                        """,
                1L,
                42L);
    }

    /* The suspended first key cannot match; Any must still be selected. */
    @Test
    void caseOfDefaultMatcherAfterSuspendedKey() throws Exception {
        runSuspendedOnce(
                "default matcher, explicit",
                WAIT_AT
                        + """
                        run: () => {
                            result: 2.caseOf(%{
                                waitAt(): () => 42
                                Any: () => 99
                            })
                            after = after + 1
                            result
                        }
                        """,
                1L,
                99L);
    }

    @Test
    void contextualMatchDefaultMatcherAfterSuspendedKey() throws Exception {
        runSuspendedOnce(
                "default matcher, contextual",
                WAIT_AT
                        + """
                        run: () => {
                            result: match(2) {
                                waitAt(): 42
                                Any: 99
                            }
                            after = after + 1
                            result
                        }
                        """,
                1L,
                99L);
    }

    /*
     * Counters for the later matcher, the selected action and every
     * unselected action; key evaluations are counted by before.
     */
    private static final String COUNTED_CASES =
            WAIT_AT
                    + """
                    matched: 0
                    acted: 0
                    unselected: 0
                    Later: {
                        match: (subject) => {
                            matched = matched + 1
                            subject == 2
                        }
                    }
                    """;

    @Test
    void caseOfSuspendedKeyAndActionEffectsAreNotRepeated() throws Exception {
        runSuspendedOnce(
                "counted, first pattern succeeds",
                COUNTED_CASES
                        + """
                        run: () => {
                            result: 1.caseOf(%{
                                waitAt(): () => {
                                    acted = acted + 1
                                    42
                                }
                                Later: () => {
                                    unselected = unselected + 1
                                    0
                                }
                            })
                            after = after + 1
                            result
                        }
                        """,
                1L,
                42L,
                f -> {
                    assertEquals(0, f.slot("matched"), "later matcher not reached");
                    assertEquals(1, f.slot("acted"), "selected action once");
                    assertEquals(0, f.slot("unselected"), "unselected action never runs");
                });
        runSuspendedOnce(
                "counted, later matcher succeeds",
                COUNTED_CASES
                        + """
                        run: () => {
                            result: 2.caseOf(%{
                                waitAt(): () => {
                                    unselected = unselected + 1
                                    42
                                }
                                Later: () => {
                                    acted = acted + 1
                                    7
                                }
                            })
                            after = after + 1
                            result
                        }
                        """,
                1L,
                7L,
                f -> {
                    assertEquals(1, f.slot("matched"), "later matcher once");
                    assertEquals(1, f.slot("acted"), "selected action once");
                    assertEquals(0, f.slot("unselected"), "unselected action never runs");
                });
    }

    /*
     * Selected caseOf actions around a suspension. An action that reads only
     * its own parameters and bindings runs inline in the enclosing root; one
     * that reads a module binding keeps its ordinary invocation. Unselected
     * actions count through a module binding and must never run.
     */
    private static void assertSelectedOnly(Fixture f) {
        assertEquals(0, f.slot("unselected"), "unselected action never runs");
    }

    /* A suspension inside an ordinary nilary selected action. */
    @Test
    void caseOfSuspensionInsideTheSelectedAction() throws Exception {
        runSuspendedOnce(
                "selected nilary action suspends",
                COUNTED_CASES
                        + """
                        run: () => {
                            r: 1.caseOf(%{
                                0: () => { unselected = unselected + 1 }
                                1: () => {
                                    acted = acted + 1
                                    waitAt() + 1
                                }
                                Any: () => { unselected = unselected + 1 }
                            })
                            after = after + 1
                            r
                        }
                        """,
                41L,
                42L,
                f -> {
                    assertEquals(1, f.slot("acted"), "selected action once");
                    assertSelectedOnly(f);
                });
    }

    /* A suspension inside an inline action body that receives two captures. */
    @Test
    void caseOfSuspensionInsideAnInlineActionWithCaptures() throws Exception {
        runSuspendedOnce(
                "inline action with captures suspends",
                COUNTED_CASES
                        + """
                        prepared: () => {
                            before = before + 1
                            [gateBox.current, 1]
                        }
                        run: () => {
                            r: prepared().caseOf(%{
                                [Capture, 0]: (g, n) => 0
                                [Capture, Capture]: (g, n) => g.value() + n
                                Any: () => { unselected = unselected + 1 }
                            })
                            after = after + 1
                            r
                        }
                        """,
                41L,
                42L,
                ProtosHostEntryFutureSuspensionTest::assertSelectedOnly);
    }

    /* A suspended key expression, then an inline action with one capture. */
    @Test
    void caseOfTableSuspensionThenInlineAction() throws Exception {
        runSuspendedOnce(
                "table suspends, inline action",
                COUNTED_CASES
                        + """
                        run: () => {
                            r: [1, 2].caseOf(%{
                                [waitAt(), Capture]: (n) => n + 40
                                Any: () => { unselected = unselected + 1 }
                            })
                            after = after + 1
                            r
                        }
                        """,
                1L,
                42L,
                ProtosHostEntryFutureSuspensionTest::assertSelectedOnly);
    }

    /* A suspended matcher answering captures, then an inline two-parameter action. */
    @Test
    void caseOfRecognitionSuspensionThenInlineAction() throws Exception {
        runSuspendedOnce(
                "matcher suspends, inline action",
                COUNTED_CASES
                        + """
                        Pairing: { match: (subject) => [waitAt(), subject] }
                        run: () => {
                            r: 41.caseOf(%{
                                Pairing: (a, b) => a + b
                                Any: () => { unselected = unselected + 1 }
                            })
                            after = after + 1
                            r
                        }
                        """,
                1L,
                42L,
                ProtosHostEntryFutureSuspensionTest::assertSelectedOnly);
    }

    /* A suspension inside an ordinary positional action that reads a module binding. */
    @Test
    void caseOfSuspensionInsideAnOrdinaryPositionalAction() throws Exception {
        runSuspendedOnce(
                "ordinary positional action suspends",
                COUNTED_CASES
                        + """
                        run: () => {
                            r: [1].caseOf(%{
                                [Capture]: (n) => {
                                    acted = acted + 1
                                    waitAt() + n
                                }
                                Any: () => { unselected = unselected + 1 }
                            })
                            after = after + 1
                            r
                        }
                        """,
                41L,
                42L,
                f -> {
                    assertEquals(1, f.slot("acted"), "selected action once");
                    assertSelectedOnly(f);
                });
    }

    /* An Error raised by an inline action after its resumption is handled by the guest. */
    @Test
    void caseOfInlineActionErrorAfterResumptionUnwinds() throws Exception {
        runSuspendedOnce(
                "inline action signals after resumption",
                COUNTED_CASES
                        + """
                        prepared: () => {
                            before = before + 1
                            [gateBox.current]
                        }
                        run: () => {
                            r: Error.handle(
                                () => prepared().caseOf(%{
                                    [Capture]: (g) => g.value().missingSelector()
                                    Any: () => { unselected = unselected + 1 }
                                }),
                                (caught) => 7)
                            after = after + 1
                            r
                        }
                        """,
                1L,
                7L,
                ProtosHostEntryFutureSuspensionTest::assertSelectedOnly);
    }

    /* A callable object selected after the subject resumed is invoked through its call. */
    @Test
    void caseOfCustomCallableActionAfterResumption() throws Exception {
        runSuspendedOnce(
                "custom callable action after resumption",
                COUNTED_CASES
                        + """
                        Custom: { call: (n) => n + 1 }
                        run: () => {
                            r: waitAt().caseOf(%{
                                Capture: Custom
                                Any: () => { unselected = unselected + 1 }
                            })
                            after = after + 1
                            r
                        }
                        """,
                41L,
                42L,
                ProtosHostEntryFutureSuspensionTest::assertSelectedOnly);
    }

    /* A Future answered by an inline action is the result itself, never awaited. */
    @Test
    void caseOfInlineActionFutureResultIsNotAwaited() {
        try (Context context = newContext()) {
            Fixture f = new Fixture(context, """
                    run: () => {
                        gate: gateBox.current
                        r: [gate].caseOf(%{ [Capture]: (future) => future })
                        r === gate
                    }
                    """);
            assertTrue(f.member("run").execute().asBoolean());
            assertEquals(0, f.gate.observerCountForTesting(), "the Future was not observed");
        }
    }

    /* An inherited source method first invoked after the caller resumed. */
    @Test
    void inheritedSourceMethodFirstInvokedAfterResumption() throws Exception {
        runSuspendedOnce(
                "inherited method after resumption",
                WAIT_AT
                        + """
                        Base: {
                            value: 0
                            add: (n) => value + n
                        }
                        child: Base { value: 10 }
                        run: () => {
                            v: waitAt()
                            r: child.add(v)
                            after = after + 1
                            r
                        }
                        """,
                5L,
                15L);
    }

    /* An inherited source method that itself suspends resumes its own state. */
    @Test
    void inheritedSourceMethodSuspendsWithoutReplay() throws Exception {
        runSuspendedOnce(
                "suspending inherited method",
                WAIT_AT
                        + """
                        Base: { wait: (n) => waitAt() + n }
                        child: Base { tag: 1 }
                        run: () => {
                            r: child.wait(100)
                            after = after + 1
                            r
                        }
                        """,
                1L,
                101L);
    }

    /* Primitive patterns select the inherited Object.match after resumption. */
    @Test
    void primitivePatternMatchingAfterResumption() throws Exception {
        runSuspendedOnce(
                "primitive patterns after resumption",
                WAIT_AT
                        + """
                        run: () => {
                            v: waitAt()
                            r: match(v) {
                                0: 10
                                1.5: 15
                                7: 70
                                Any: 0
                            }
                            ok: 7.match(v) && (1.5.match(v) == false) && (7.0.match(v) == true)
                            after = after + 1
                            if(ok) { r } else { -1 }
                        }
                        """,
                7L,
                70L);
    }

    /* Custom equality reached through the inherited Object.match runs once after resumption. */
    @Test
    void customEqualityThroughObjectMatchAfterResumption() throws Exception {
        runSuspendedOnce(
                "custom equality through Object.match after resumption",
                WAIT_AT
                        + """
                        eqCalls: 0
                        EqBase: {
                            equalTo: (other) => {
                                eqCalls = eqCalls + 1
                                other * 2
                            }
                        }
                        Eq: EqBase.alias("equalTo", "==")
                        pattern: Eq()
                        run: () => {
                            v: waitAt()
                            r: pattern.match(v)
                            after = after + 1
                            r
                        }
                        """,
                21L,
                42L,
                f -> assertEquals(1, f.slot("eqCalls"), "the custom equality runs once"));
    }

    /* A selected inherited method reassigned while the caller is suspended is observed. */
    @Test
    void selectedMethodReassignedBeforeResumptionIsObserved() throws Exception {
        runSuspendedOnce(
                "inherited method reassigned while suspended",
                WAIT_AT
                        + """
                        Base: { pick: (n) => n + 1 }
                        child: Base { tag: 1 }
                        replacement: (n) => n + 100
                        run: () => {
                            first: child.pick(0)
                            v: waitAt()
                            r: child.pick(v)
                            after = after + 1
                            first + r
                        }
                        """,
                5L,
                106L,
                f -> {
                    ProtosObjectValue base =
                            (ProtosObjectValue) f.module.readLocalSlot("Base").orElseThrow();
                    base.assignLocalSlot(
                            "pick", f.module.readLocalSlot("replacement").orElseThrow());
                },
                f -> {});
    }

    /* An inherited method keeps the real methodHome for super and this after resumption. */
    @Test
    void inheritedMethodHomeAfterResumption() throws Exception {
        runSuspendedOnce(
                "inherited methodHome after resumption",
                WAIT_AT
                        + """
                        Top: { read: (d) => d + 1 }
                        Middle: Top {
                            read: (d) => super.read(d) + 10
                            self: (ignored) => this
                        }
                        leaf: Middle { tag: 1 }
                        run: () => {
                            v: waitAt()
                            r: leaf.read(v)
                            same: leaf.self(v) === leaf
                            after = after + 1
                            if(same) { r } else { -1 }
                        }
                        """,
                5L,
                16L);
    }
}
