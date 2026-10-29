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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.guillermomolina.protos.runtime.ProtosActorExecutionDomain;
import com.guillermomolina.protos.runtime.ProtosFutureValue;
import com.guillermomolina.protos.runtime.ProtosIntegerValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.Test;

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
            gate: 0
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
            module.assignLocalSlot("gate", gate);
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
            assertTrue(gate.resolve(new ProtosIntegerValue(value), null));
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
                v: gate.value()
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
                            suspending: () => { gate.value() }
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

                ProtosIntegerValue supplied = new ProtosIntegerValue(7);

                ProtosPreparedCalls.OrdinarySourceCall prepared =
                        adapter.prepare(new Object[] {supplied}, null);

                try {
                    Object[] frame = prepared.targetArguments();

                    assertEquals(
                            ProtosFrameArguments.HEADER_LENGTH + 1,
                            frame.length);
                    assertSame(adapter.closure, frame[0]);
                    assertSame(adapter.caller, frame[3]);
                    assertSame(
                            supplied,
                            frame[ProtosFrameArguments.HEADER_LENGTH]);
                    assertSame(adapter.target, prepared.bodyTarget());
                } finally {
                    prepared.complete();
                }
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
                                identity(gate.value())
                            }
                            suspendingDefault: (value: gate.value()) => {
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
     * I115-H6: the megamorphic specialization builds the compact no-home B5 frame and performs one
     * {@link IndirectCallNode} call for zero- and one-argument source-backed Closures.
     */
    @Test
    void indirectHostEntryPerformsOneCompactInvocation() throws Exception {
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

                IndirectCallNode indirect = IndirectCallNode.create();

                Object literal =
                        ProtosHostExecutableClosure.Execute.indirect(
                                trivial, new Object[0], indirect);
                assertEquals("1", decimal(literal));

                Object forwarded =
                        ProtosHostExecutableClosure.Execute.indirect(
                                identity, new Object[] {37}, indirect);
                assertEquals("37", decimal(forwarded));

                Object guest =
                        ProtosHostExecutableClosure.Execute.indirect(
                                identity, new Object[] {new ProtosIntegerValue(-4)}, indirect);
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
     * I115-H6: the experimental source-backed entry makes no Process admission decision, so a
     * retained executable still runs after the Process stopped.
     */
    @Test
    void terminatedProcessIsNotConsultedByExperimentalHostEntry() {
        try (Context context = newContext()) {
            Fixture f = new Fixture(context, "trivial: () => { 1 }");
            Value trivial = f.member("trivial");

            assertEquals(1, trivial.execute().asInt());

            f.process.processForTesting().requestTerminationForRuntime();

            assertFalse(f.process.isLive());
            assertFalse(f.process.permitsUnadmittedNonSuspendingHostEntry());

            assertEquals(1, trivial.execute().asInt(), "no Process admission decision");
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
     * I115-H6: the source-backed host entry has no continuation driver. A pending Future
     * observation returns to the host without waiting, and resolving the Future afterwards never
     * resumes the abandoned body.
     */
    @Test
    void pendingFutureObservationIsNotDrivenByTheExperimentalHostEntry() throws Exception {
        try (Context context = newContext()) {
            Fixture f = new Fixture(context, SUSPENDING_RUN);
            Entry entry = new Entry(f.member("run"));
            Object outcome = entry.await();
            assertEquals(1, f.slot("before"));
            assertEquals(0, f.slot("after"), "nothing after the observation point ran");
            assertNotEquals(42L, outcome, "no resumed result");
            f.resolve(41);
            assertEquals(0, f.slot("after"), "no later resumption");
        }
    }

    @Test
    void alreadyCancelledFutureSignalsCancelledWithoutSuspension() {
        try (Context context = newContext()) {
            Fixture f =
                    new Fixture(
                            context,
                            "run: () => { Error.handle(() => { gate.value() }, (caught) => { 9 }) }");
            assertEquals(ProtosFutureValue.State.CANCELLED, cancelledBeforeEntry(f));
            assertEquals(9, f.member("run").execute().asInt(), "already cancelled");
        }
    }

    private static ProtosFutureValue.State cancelledBeforeEntry(Fixture f) {
        assertTrue(f.gate.cancelTerminal());
        return f.gate.state();
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
                                    gate.value() + 1
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
}
