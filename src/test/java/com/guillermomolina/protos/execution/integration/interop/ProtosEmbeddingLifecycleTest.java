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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.runtime.ProtosProcessRuntime;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import com.oracle.truffle.api.ThreadLocalAction;
import com.oracle.truffle.api.TruffleSafepoint;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.Test;

/**
 * PLAT054-3D / I086: Actors under the Polyglot Context thread policy and Context close ({@code
 * PROCESS_IO.md} Standard Polyglot embedding bootstrap and authority, §28; {@code ACTORS.md}
 * spawn creation cutover) through the public {@code Context} API.
 */
final class ProtosEmbeddingLifecycleTest {
    private static final String CORE = Path.of("protos", "lib", "core").toAbsolutePath().toString();

    /** A Set behavior from Core: an ordinary Map whose {@code size} answers 3. */
    private static final String SPAWN =
            "worker: Actor.spawn(\"std:collections/Set\", \"call\", 1, 2, 3)\n";

    private static Context.Builder builder() {
        return Context.newBuilder(ProtosLanguage.ID).option("protos.CoreRoot", CORE);
    }

    private static ProtosEmbeddedProcess embedded(Context context) {
        context.enter();
        try {
            return ProtosLanguageContext.current().embeddedProcessOrNull();
        } finally {
            context.leave();
        }
    }

    @Test
    void actorsRunOnContextThreadsAndAreJoinedOnClose() {
        ProtosEmbeddedProcess process;
        try (Context context = builder().allowCreateThread(true).build()) {
            Value size = context.eval(ProtosLanguage.ID, SPAWN + "worker.request(\"size\").value()");
            assertEquals(3, size.asInt());
            process = embedded(context);
            assertTrue(process.actorCarrierSubstrateInitializedForTesting());
            assertTrue(process.isLive(), "the Actor ran without affecting the RootActor");
        }
        assertEquals(
                ProtosProcessRuntime.LifecycleState.TERMINATED,
                process.processForTesting().lifecycleState());
        assertTrue(process.actorCarriersTerminatedForTesting(), "no carrier outlives the Context");
    }

    @Test
    void aContextWithoutThreadsStartsNoCarrierAndSpawnStillReturns() {
        try (Context context = builder().build()) {
            Value worker = context.eval(ProtosLanguage.ID, SPAWN + "worker");
            // The creation cutover happened: spawn returned an ActorRef, not a synchronous failure.
            assertFalse(worker.isNull());
            ProtosEmbeddedProcess process = embedded(context);
            assertEquals(0, process.actorCarrierThreadCountForTesting(), "thread policy holds");
            assertTrue(process.isLive(), "a refused carrier is not a RootActor failure");
            assertEquals(2, context.eval(ProtosLanguage.ID, "1 + 1").asInt());
        }
    }

    /**
     * The refused incarnation terminated before {@code spawn} returned, so a later request is
     * known never to have been accepted: an ordinary failed Future ({@code ACTORS.md} §7), not
     * {@code RequestOutcomeUncertain}, and observing it does not wait.
     */
    @Test
    void aRequestToAnActorRefusedByThreadPolicyFailsWithoutWaiting() {
        try (Context context = builder().build()) {
            Value outcome =
                    context.eval(
                            ProtosLanguage.ID,
                            SPAWN
                                    + """
                                    observed: null
                                    Error.handle(
                                        () => {
                                            worker.request("size").value()
                                            null
                                        },
                                        (error) => {
                                            observed = error
                                            null
                                        }
                                    )
                                    (observed != null) && (observed.parent() !== RequestOutcomeUncertain)
                                    """);
            assertTrue(outcome.asBoolean(), "an ordinary failed request Future");
            assertTrue(embedded(context).isLive(), "the handled Error is not fatal");
        }
    }

    /**
     * Truffle may cancel a carrier before it runs anything of the pool's: the close must still
     * join that real thread without relying on any pool bookkeeping.
     */
    @Test
    void aCarrierCancelledBeforeItsFirstCommandIsJoined() throws Exception {
        Context context = builder().allowCreateThread(true).build();
        context.eval(ProtosLanguage.ID, "a: 1\n0");
        ProtosEmbeddedProcess process = embedded(context);
        process.actorSchedulerForRuntime();
        CountDownLatch entered = new CountDownLatch(1);
        process.actorCarrierPoolForTesting().beforeRunForTesting =
                () -> {
                    entered.countDown();
                    TruffleSafepoint.setBlockedThreadInterruptible(
                            null, CountDownLatch::await, new CountDownLatch(1));
                };
        context.eval(ProtosLanguage.ID, SPAWN + "0");
        assertTrue(entered.await(5, TimeUnit.SECONDS));

        CompletableFuture.runAsync(() -> context.close(true)).get(10, TimeUnit.SECONDS);

        assertTrue(process.actorCarriersTerminatedForTesting(), "the real thread has ended");
        assertFalse(process.isLive());
    }

    /** A Truffle safepoint action reaches an idle carrier, which then keeps serving Actors. */
    @Test
    void aSafepointActionOnAnIdleCarrierIsNotAClose() throws Exception {
        try (Context context = builder().allowCreateThread(true).build()) {
            context.eval(ProtosLanguage.ID, SPAWN + "worker.request(\"size\").value()");
            ProtosEmbeddedProcess process = embedded(context);
            Thread carrier = process.actorCarrierPoolForTesting().carriersForTesting().get(0);
            AtomicReference<Thread> performedOn = new AtomicReference<>();
            context.enter();
            try {
                ProtosLanguageContext.current()
                        .env()
                        .submitThreadLocal(
                                new Thread[] {carrier},
                                new ThreadLocalAction(false, false) {
                                    @Override
                                    protected void perform(Access access) {
                                        performedOn.set(Thread.currentThread());
                                    }
                                })
                        .get(5, TimeUnit.SECONDS);
            } finally {
                context.leave();
            }
            assertSame(carrier, performedOn.get());
            assertTrue(carrier.isAlive(), "the action did not end the carrier");

            // Actor work still completes and the interrupted carrier stays in service. The pool may
            // add carriers for concurrent scheduler workers, so their number is not asserted. (A
            // pending value() is observed through eval: a host Closure call cannot suspend yet.)
            assertEquals(
                    3,
                    context.eval(ProtosLanguage.ID, SPAWN + "worker.request(\"size\").value()")
                            .asInt());
            assertTrue(process.actorCarrierPoolForTesting().carriersForTesting().contains(carrier));
            assertTrue(carrier.isAlive());
            assertTrue(process.isLive());
        }
    }

    @Test
    void programsWithoutActorsCreateNoCarrierSubstrate() {
        try (Context context = builder().allowCreateThread(true).build()) {
            context.eval(ProtosLanguage.ID, "truffleRun: () => { 1 }\n0");
            Value run = context.getBindings(ProtosLanguage.ID).getMember("truffleRun");
            assertEquals(1, run.execute().asInt());
            assertFalse(embedded(context).actorCarrierSubstrateInitializedForTesting());
        }
    }

    @Test
    void closingAContextWithoutAProcessCreatesNone() {
        Context context = builder().build();
        context.getBindings(ProtosLanguage.ID);
        assertNull(embedded(context));
        context.close();
    }

    @Test
    void valuesRetainedAfterCloseAreUnusable() {
        Value run;
        ProtosProcessRuntime process;
        try (Context context = builder().build()) {
            context.eval(ProtosLanguage.ID, "truffleRun: () => { 1 }\n0");
            run = context.getBindings(ProtosLanguage.ID).getMember("truffleRun");
            process = embedded(context).processForTesting();
        }
        // Polyglot reports a closed Context as IllegalStateException or PolyglotException.
        RuntimeException closed = assertThrows(RuntimeException.class, run::execute);
        assertTrue(
                closed instanceof IllegalStateException || closed instanceof PolyglotException,
                closed.toString());
        assertEquals(ProtosProcessRuntime.LifecycleState.TERMINATED, process.lifecycleState());
    }

    @Test
    void cancellingABusyEntryClosesWithoutBlockingAndLeavesOtherContextsRunning()
            throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (Context other = builder().build()) {
            other.eval(ProtosLanguage.ID, "a: 1\n0");
            Context context = builder().allowCreateThread(true).out(out).build();
            CompletableFuture<Throwable> busy =
                    CompletableFuture.supplyAsync(
                            () -> {
                                try {
                                    // The answered Actor leaves an idle carrier in the pool;
                                    // only the initial module has the process slot.
                                    context.eval(
                                            ProtosLanguage.ID,
                                            SPAWN
                                                    + """
                                            worker.request("size").value()
                                            process.stdout().write(process.stdoutEncoding().encode("go")).value()
                                            (() => true).whileTrue() { 0 }
                                            """);
                                    return null;
                                } catch (PolyglotException cancelled) {
                                    return cancelled;
                                }
                            });
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (out.size() == 0 && System.nanoTime() < deadline) {
                Thread.onSpinWait();
            }
            assertEquals("go", out.toString(StandardCharsets.UTF_8));
            ProtosEmbeddedProcess process = embedded(context);
            assertTrue(process.actorCarrierThreadCountForTesting() > 0);

            // Bounded by the test: a close that never returns fails here instead of hanging.
            CompletableFuture.runAsync(() -> context.close(true)).get(10, TimeUnit.SECONDS);

            assertTrue(process.actorCarriersTerminatedForTesting(), "every carrier has ended");
            Throwable failure = busy.get(5, TimeUnit.SECONDS);
            assertTrue(failure instanceof PolyglotException cancelled && cancelled.isCancelled());
            assertFalse(process.isLive());
            assertNotEquals(
                    ProtosProcessRuntime.LifecycleState.RUNNING,
                    process.processForTesting().lifecycleState());
            assertEquals(1, other.getBindings(ProtosLanguage.ID).getMember("a").asInt());
        }
    }
}
