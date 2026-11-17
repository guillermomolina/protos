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
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.runtime.ProtosActorExecutionDomain;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.graalvm.polyglot.PolyglotException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * I115-H6 (experimental): the source-backed Java/polyglot host entry is one compact no-home guest
 * invocation. These tests cover only the admitted experimental contract, not the abandoned
 * ordinary host-entry semantics.
 */
final class ProtosI115H6HostEntryConvergenceTest {
    private static final Path CORE = Path.of("protos", "lib", "core");
    private static final long FACTORIAL_20 = 2432902008176640000L;

    private static final String SOURCE =
            """
            factorial: (n) => {
                result: 1

                (n > 1).ifTrue() {
                    result = n * factorial(n - 1)
                }

                result
            }

            run: () => {
                factorial(20)
            }

            identity: (value) => { value }

            literal: () => { 3 }
            """;

    @TempDir Path directory;

    private static Context newContext() {
        return Context.newBuilder(ProtosLanguage.ID)
                .option("protos.CoreRoot", CORE.toAbsolutePath().toString())
                .build();
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
    void polyglotRunReturnsRecursiveFactorial() {
        try (Context context = newContext()) {
            context.eval(ProtosLanguage.ID, SOURCE + "0\n");
            Value run = context.getBindings(ProtosLanguage.ID).getMember("run");
            for (int index = 0; index < 3; index++) {
                Value result = run.execute();
                assertTrue(result.fitsInLong());
                assertEquals(FACTORIAL_20, result.asLong());
            }
        }
    }

    @Test
    void directOneArgumentIdentityReceivesJavaInteger() {
        try (Context context = newContext()) {
            context.eval(ProtosLanguage.ID, SOURCE + "0\n");
            Value identity = context.getBindings(ProtosLanguage.ID).getMember("identity");
            assertEquals(42, identity.execute(42).asInt());
            assertEquals(-1L, identity.execute(-1L).asLong());
            assertEquals(Long.MAX_VALUE, identity.execute(Long.MAX_VALUE).asLong());
        }
    }

    @Test
    void sessionZeroArgumentSourceBackedExecutable() throws Exception {
        Path file = directory.resolve("i115-h6.protos");
        Files.writeString(file, SOURCE, StandardCharsets.UTF_8);
        try (ProtosStandaloneHostedSession session =
                ProtosStandaloneHostedSession.open(CORE, file)) {
            assertEquals(
                    ProtosExecutionOutcome.State.COMPLETED, session.initialOutcome().state());
            Value literal = session.prepareTopLevel("literal").executable();
            assertEquals(3, literal.execute().asInt());
            Value run = session.prepareTopLevel("run").executable();
            assertEquals(FACTORIAL_20, run.execute().asLong());
        }
    }

    /**
     * I115-A: a source Closure whose root proves its home unobservable keeps
     * its exact results through repeated Task-free Polyglot entries, and the
     * entry creates no implicit Task.
     */
    @Test
    void hostEntryOfProvenUnobservableClosureKeepsResultsWithoutATask() {
        try (Context context = newContext()) {
            context.eval(ProtosLanguage.ID, """
                    make: (seed) => { (value) => { seed + value } }
                    adder: make(40)
                    0
                    """);
            ProtosActorExecutionDomain domain = embedded(context).rootExecutionDomain();
            Value adder = context.getBindings(ProtosLanguage.ID).getMember("adder");
            for (int index = 0; index < 12; index++) {
                assertEquals(42L + index, adder.execute(2 + index).asLong());
            }
            assertEquals(0, domain.liveTaskCount());
        }
    }

    /**
     * The experimental entry neither acquires, releases, nor rejects on RootActor ownership: with
     * the token already held it still runs, and leaves the token and host-entry extent untouched.
     */
    @Test
    void hostEntryRunsWithoutOrdinaryRootActorAdmission() {
        try (Context context = newContext()) {
            context.eval(ProtosLanguage.ID, SOURCE + "0\n");
            ProtosEmbeddedProcess process = embedded(context);
            ProtosActorExecutionDomain domain = process.rootExecutionDomain();
            Value run = context.getBindings(ProtosLanguage.ID).getMember("run");

            Thread token = process.enterRootActor();
            try {
                assertFalse(process.permitsUnadmittedNonSuspendingHostEntry());
                assertEquals(FACTORIAL_20, run.execute().asLong());
                assertFalse(
                        process.permitsUnadmittedNonSuspendingHostEntry(),
                        "the entry did not release the held token");
                assertEquals(
                        ProtosActorExecutionDomain.HostEntryExtent.NONE,
                        domain.hostEntryExtentForRuntime());
            } finally {
                process.exitRootActor(token);
            }
            assertEquals(0, domain.liveTaskCount());
        }
    }

    private static final String ADAPTER_SOURCE =
            SOURCE
                    + """
                    make: () => { (value) => { value } }
                    kept: make()
                    getKept: () => { kept }
                    isKept: (candidate) => { candidate === kept }
                    fresh: () => { { marker: 1 } }
                    same: (left, right) => { left === right }
                    dispatch: Object.caseOf
                    cases: %{ Capture: (subject) => { 5 } }
                    """;

    /** Reads one fresh host adapter of a module member inside the entered Context. */
    private static ProtosHostExecutableClosure adapter(Context context, String member) {
        context.enter();
        try {
            ProtosEmbeddedProcess process = ProtosLanguageContext.current().embeddedProcessOrNull();
            ProtosObjectValue module = process.selectedModuleContext().orElseThrow();
            return (ProtosHostExecutableClosure)
                    process.readSelectedMember(module, module.readLocalSlot(member).orElseThrow());
        } finally {
            context.leave();
        }
    }

    /**
     * The adapter identity cache of the zero-argument host entry: repeated execution of one
     * adapter keeps exact results, caches neither results nor guest identities, keeps distinct
     * adapters' callers apart, and keeps correct results past the cache limit.
     */
    @Test
    void adapterIdentityCacheKeepsResultsIdentitiesAndCallers() {
        try (Context context = newContext()) {
            context.eval(ProtosLanguage.ID, ADAPTER_SOURCE + "0\n");
            Value literal = context.getBindings(ProtosLanguage.ID).getMember("literal");
            for (int index = 0; index < 20; index++) {
                assertEquals(3, literal.execute().asInt());
            }

            Value getKept = context.getBindings(ProtosLanguage.ID).getMember("getKept");
            Value isKept = context.getBindings(ProtosLanguage.ID).getMember("isKept");
            Value fresh = context.getBindings(ProtosLanguage.ID).getMember("fresh");
            Value same = context.getBindings(ProtosLanguage.ID).getMember("same");
            for (int index = 0; index < 5; index++) {
                assertTrue(isKept.execute(getKept.execute()).asBoolean(),
                        "the cached entry keeps the guest Closure identity");
                Value first = fresh.execute();
                Value second = fresh.execute();
                assertFalse(same.execute(first, second).asBoolean(),
                        "every execution creates its own result");
                assertTrue(same.execute(first, first).asBoolean());
            }

            ProtosHostExecutableClosure left = adapter(context, "literal");
            ProtosHostExecutableClosure right = adapter(context, "literal");
            assertNotSame(left, right);
            assertNotSame(left.caller, right.caller, "adapters never share a caller");
            assertNotSame(left.closure, right.closure, "each read is a fresh extraction");
            assertNotNull(left.target);
            Value leftValue = context.asValue(left);
            Value rightValue = context.asValue(right);
            for (int index = 0; index < 5; index++) {
                assertEquals(3, leftValue.execute().asInt());
                assertEquals(3, rightValue.execute().asInt());
            }

            // Saturate the adapter cache: later adapters keep the general path.
            for (int index = 0; index < 8; index++) {
                Value member = context.getBindings(ProtosLanguage.ID).getMember("literal");
                assertEquals(3, member.execute().asInt());
                assertEquals(FACTORIAL_20,
                        context.getBindings(ProtosLanguage.ID).getMember("run").execute().asLong());
            }
        }
    }

    /**
     * A changed effective selection of the adapter's Closure is still resolved by the shared
     * dispatcher; argument-taking and native entries keep their own paths and errors.
     */
    @Test
    void adapterCacheKeepsDispatcherAuthorityAndOtherEntryPaths() {
        try (Context context = newContext()) {
            Value cases = context.eval(ProtosLanguage.ID, ADAPTER_SOURCE + "cases\n");
            ProtosHostExecutableClosure literal = adapter(context, "literal");
            Value literalValue = context.asValue(literal);
            for (int index = 0; index < 5; index++) {
                assertEquals(3, literalValue.execute().asInt());
            }
            context.enter();
            try {
                literal.closure.requireContextLocalExecutionProjectionForRuntime();
            } finally {
                context.leave();
            }
            for (int index = 0; index < 5; index++) {
                assertEquals(3, literalValue.execute().asInt(),
                        "the dispatcher re-selects the target after the selection changed");
            }

            Value identity = context.asValue(adapter(context, "identity"));
            for (int index = 0; index < 5; index++) {
                assertEquals(index, identity.execute(index).asInt());
            }

            ProtosHostExecutableClosure dispatch = adapter(context, "dispatch");
            assertNull(dispatch.target, "a native Closure has no compact target");
            Value dispatchValue = context.asValue(dispatch);
            for (int index = 0; index < 3; index++) {
                assertEquals(5, dispatchValue.execute(cases).asInt());
            }

            assertThrows(IllegalArgumentException.class, () -> literalValue.execute(new Object()),
                    "a foreign host argument is rejected");
            assertEquals(3, literalValue.execute().asInt());
        }
    }

    @Test
    void sessionAdapterKeepsItsArityError() throws Exception {
        Path file = directory.resolve("i115-h6-arity.protos");
        Files.writeString(file, SOURCE, StandardCharsets.UTF_8);
        try (ProtosStandaloneHostedSession session =
                ProtosStandaloneHostedSession.open(CORE, file)) {
            Value literal = session.prepareTopLevel("literal").executable();
            for (int index = 0; index < 5; index++) {
                assertEquals(3, literal.execute().asInt());
            }
            assertThrows(IllegalArgumentException.class, () -> literal.execute(1));
            assertEquals(3, literal.execute().asInt());
        }
    }

    /**
     * A concurrent outermost entry is rejected immediately while another thread holds the
     * RootActor; the cached entry never waits for it. After termination no call is admitted.
     */
    @Test
    void cachedEntryRejectsConcurrentAndTerminatedEntries() throws Exception {
        try (Context context = newContext()) {
            context.eval(ProtosLanguage.ID, SOURCE + "0\n");
            ProtosEmbeddedProcess process = embedded(context);
            Value literal = context.asValue(adapter(context, "literal"));
            assertEquals(3, literal.execute().asInt());

            CountDownLatch held = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            AtomicReference<Throwable> holderFailure = new AtomicReference<>();
            Thread holder = new Thread(() -> {
                Thread token = process.enterRootActor();
                try {
                    held.countDown();
                    release.await(5, TimeUnit.SECONDS);
                } catch (Throwable failure) {
                    holderFailure.set(failure);
                } finally {
                    process.exitRootActor(token);
                }
            }, "root-actor-holder");
            holder.setDaemon(true);
            holder.start();
            try {
                assertTrue(held.await(5, TimeUnit.SECONDS));
                PolyglotException rejected =
                        assertThrows(PolyglotException.class, () -> literal.execute());
                assertTrue(rejected.getMessage().contains("concurrent entry is rejected"),
                        rejected.getMessage());
            } finally {
                release.countDown();
                holder.join(TimeUnit.SECONDS.toMillis(5));
            }
            assertNull(holderFailure.get());
            assertEquals(3, literal.execute().asInt(), "the released RootActor admits entries");

            process.processForTesting().requestTerminationForRuntime();
            assertFalse(process.isLive());
            assertThrows(PolyglotException.class, () -> literal.execute());
            assertFalse(process.isLive());
        }
    }
}
