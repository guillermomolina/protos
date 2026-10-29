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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.runtime.modules.ProtosModuleKey;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosProcessRuntime;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.Test;

/**
 * PLAT054-3 / I086: the standard Polyglot embedding contract ({@code MODULES.md} Host-initiated
 * evaluations in an embedded RootActor; {@code PROCESS_IO.md} Standard Polyglot embedding
 * bootstrap and authority; {@code ACTORS.md} §24C) through the public {@code Context} API only.
 */
final class ProtosStandardPolyglotEmbeddingTest {
    private static final String CORE = Path.of("protos", "lib", "core").toAbsolutePath().toString();

    private static Context.Builder builder() {
        return Context.newBuilder(ProtosLanguage.ID).option("protos.CoreRoot", CORE);
    }

    private static ProtosLanguageContext languageContext(Context context) {
        context.enter();
        try {
            return ProtosLanguageContext.current();
        } finally {
            context.leave();
        }
    }

    private static ProtosEmbeddedProcess embedded(Context context) {
        return languageContext(context).embeddedProcessOrNull();
    }

    private static ProtosObjectValue selected(Context context) {
        return embedded(context).selectedModuleContext().orElseThrow();
    }

    @Test
    void standardJavaExampleExecutesATopLevelClosure() {
        try (Context context = builder().build()) {
            Value terminal =
                    context.eval(
                            Source.create(
                                    ProtosLanguage.ID,
                                    """
                                    truffleRun: () => {
                                        42
                                    }
                                    7
                                    """));
            assertEquals(7, terminal.asInt());

            Value run = context.getBindings(ProtosLanguage.ID).getMember("truffleRun");
            assertTrue(run.canExecute());
            for (int index = 0; index < 3; index++) {
                assertEquals(42, run.execute().asInt());
            }
            // The minimal path allocates no Task and starts no Actor carrier.
            assertEquals(
                    0,
                    embedded(context)
                            .processForTesting()
                            .rootActorForRuntime()
                            .executionDomain()
                            .liveTaskCount());
            assertFalse(embedded(context).actorCarrierSubstrateInitializedForTesting());
        }
    }

    @Test
    void contextConstructionBindingsAndParseErrorsCreateNoProcess() {
        try (Context context = builder().build()) {
            context.initialize(ProtosLanguage.ID);
            Value bindings = context.getBindings(ProtosLanguage.ID);
            assertTrue(bindings.getMemberKeys().isEmpty());
            assertFalse(bindings.hasMember("truffleRun"));
            assertNull(embedded(context));

            PolyglotException syntax =
                    assertThrows(
                            PolyglotException.class,
                            () -> context.eval(ProtosLanguage.ID, "x: (((\n"));
            // Protos parse failures are not yet classified as Truffle syntax errors; the contract
            // requires only that they precede bootstrap, which the next assertion checks.
            assertFalse(syntax.isGuestException() && embedded(context) != null);
            assertNull(embedded(context), "a parse error precedes bootstrap");

            context.eval(ProtosLanguage.ID, "a: 1\n0");
            assertNotNull(embedded(context));
            // The same stable view now reflects the selection.
            assertEquals(1, bindings.getMember("a").asInt());
        }
    }

    @Test
    void laterEvaluationsShareTheProcessAndSelectTheLastNormalCompletion() {
        try (Context context = builder().build()) {
            Value bindings = context.getBindings(ProtosLanguage.ID);
            context.eval(ProtosLanguage.ID, "a: 1\n0");
            ProtosEmbeddedProcess first = embedded(context);
            assertTrue(bindings.hasMember("process"), "initial module bootstrap slot");
            assertFalse(bindings.hasMember("filesystem"), "no default Filesystem authority");
            assertFalse(bindings.hasMember("network"), "no default Network authority");

            context.eval(ProtosLanguage.ID, "b: 2\n0");
            assertSame(first, embedded(context), "one Process per Context");
            assertEquals(Set.of("b"), bindings.getMemberKeys(), "no REPL carry-over or prelude");
            assertFalse(bindings.hasMember("process"), "bootstrap slots only on the initial module");

            // A failing entry keeps the previous selection; a handled Error is not fatal.
            context.eval(
                    ProtosLanguage.ID,
                    """
                    c: Error.handle(() => { Error().signal() }, (caught) => { 3 })
                    0
                    """);
            assertEquals(3, bindings.getMember("c").asInt());
            assertTrue(first.isLive());
        }
    }

    @Test
    void standaloneEntriesAreDistinctAndImportableModulesAreCached() {
        try (Context context = builder().build()) {
            Source same = Source.create(ProtosLanguage.ID, "m: import(\"std:test/Assertions\")\n0");
            context.eval(same);
            ProtosObjectValue firstContext = selected(context);
            Object firstModule = firstContext.readLocalSlot("m").orElseThrow();

            context.eval(same);
            ProtosObjectValue secondContext = selected(context);
            assertNotSame(firstContext, secondContext, "no ModuleKey from Source identity");
            assertSame(
                    firstModule,
                    secondContext.readLocalSlot("m").orElseThrow(),
                    "READY importable module reused from the Actor-local cache");
            assertTrue(
                    embedded(context)
                            .processForTesting()
                            .rootActorForRuntime()
                            .moduleState()
                            .lookup(new ProtosModuleKey("std:test/Assertions"))
                            .isPresent());
        }
    }

    @Test
    void twoContextsAreIndependent() {
        try (Context left = builder().build();
                Context right = builder().build()) {
            left.eval(ProtosLanguage.ID, "v: 1\n0");
            right.eval(ProtosLanguage.ID, "v: 2\n0");
            assertNotSame(embedded(left), embedded(right));
            assertEquals(1, left.getBindings(ProtosLanguage.ID).getMember("v").asInt());
            assertEquals(2, right.getBindings(ProtosLanguage.ID).getMember("v").asInt());
        }
    }

    @Test
    void bindingsAreReadOnlyAndRetainedClosuresKeepTheirExtraction() {
        try (Context context = builder().build()) {
            context.eval(
                    ProtosLanguage.ID,
                    """
                    count: 0
                    bump: () => {
                        count = count + 1
                        count
                    }
                    run: () => { 1 }
                    rebind: () => {
                        run = () => { 2 }
                        0
                    }
                    twice: (a) => { a + a }
                    0
                    """);
            Value bindings = context.getBindings(ProtosLanguage.ID);
            assertThrows(UnsupportedOperationException.class, () -> bindings.putMember("x", 1));
            assertThrows(UnsupportedOperationException.class, () -> bindings.removeMember("count"));

            assertEquals(1, bindings.getMember("bump").execute().asInt());
            assertEquals(1, bindings.getMember("count").asInt(), "guest mutation is observable");

            Value retained = bindings.getMember("run");
            bindings.getMember("rebind").execute();
            assertEquals(1, retained.execute().asInt(), "a retained Value never re-reads the slot");
            assertEquals(2, bindings.getMember("run").execute().asInt());

            Value twice = bindings.getMember("twice");
            context.eval(ProtosLanguage.ID, "other: 0\n0");
            assertEquals(1, retained.execute().asInt(), "not redirected by a later evaluation");
            Value two = context.eval(ProtosLanguage.ID, "2");
            // I115-H6: the admitted source-backed contract is zero or one argument.
            assertEquals(4, twice.execute(two).asInt());
            assertEquals(4, twice.execute(2).asInt(), "PLAT054-3B Java Integer admission");
        }
    }

    private static final String SCALAR_SOURCE =
            """
            count: 0
            add: (a, b) => { a + b }
            sub: (a, b) => { a - b }
            id: (x) => { x }
            succ: (x) => { x + 1 }
            prefixed: (s) => { "a" + s }
            size: (s) => { s.size() }
            withDefault: (a, b: 10) => { a + b }
            restSize: (first, ...rest) => { rest.size() }
            bump: (x) => {
                count = count + 1
                x
            }
            0
            """;

    @Test
    void javaIntegerArgumentsAreOrdinaryExactIntegers() {
        try (Context context = builder().build()) {
            context.eval(ProtosLanguage.ID, SCALAR_SOURCE);
            Value bindings = context.getBindings(ProtosLanguage.ID);
            Value succ = bindings.getMember("succ");
            Value id = bindings.getMember("id");
            Value withDefault = bindings.getMember("withDefault");
            Value restSize = bindings.getMember("restSize");

            assertEquals(3, succ.execute(2).asInt());
            assertEquals(0, id.execute(0).asInt());
            assertEquals(-5, id.execute(-5).asInt());
            assertEquals(Integer.MIN_VALUE, id.execute(Integer.MIN_VALUE).asInt());
            assertEquals(Integer.MAX_VALUE, id.execute(Integer.MAX_VALUE).asInt());
            assertEquals(Long.MAX_VALUE, id.execute(Long.MAX_VALUE).asLong());
            assertEquals(Long.MIN_VALUE, id.execute(Long.MIN_VALUE).asLong());
            assertEquals(-128, id.execute((byte) -128).asInt());
            assertEquals(32767, id.execute((short) 32767).asInt());
            // The ordinary exact Integer protocol applies: no overflow at the Java boundary type.
            assertEquals(
                    BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE),
                    succ.execute(Long.MAX_VALUE).asBigInteger());

            // An existing Protos value is admitted unchanged. A later evaluation moves the binding
            // selection, so every Closure above is read before it.
            Value two = context.eval(ProtosLanguage.ID, "2");
            assertEquals(3, succ.execute(two).asInt());

            // One supplied argument still binds a default and an empty rest in the callee.
            assertEquals(15, withDefault.execute(5).asInt());
            assertEquals(0, restSize.execute(1).asInt());

            // The minimal path still allocates no Task and starts no Actor carrier.
            assertEquals(
                    0,
                    embedded(context)
                            .processForTesting()
                            .rootActorForRuntime()
                            .executionDomain()
                            .liveTaskCount());
            assertFalse(embedded(context).actorCarrierSubstrateInitializedForTesting());
            assertTrue(embedded(context).isLive());
        }
    }

    @Test
    void javaStringArgumentsAreOrdinaryStrings() {
        try (Context context = builder().build()) {
            context.eval(ProtosLanguage.ID, SCALAR_SOURCE);
            Value bindings = context.getBindings(ProtosLanguage.ID);
            Value id = bindings.getMember("id");
            Value prefixed = bindings.getMember("prefixed");
            Value size = bindings.getMember("size");

            assertEquals("abc", id.execute("abc").asString());
            assertEquals("", id.execute("").asString());
            assertEquals("\u00f1and\u00fa\u20ac", id.execute("\u00f1and\u00fa\u20ac").asString());
            String supplementary = "\ud83d\ude00\ud834\udd1e";
            assertEquals(supplementary, id.execute(supplementary).asString());
            assertEquals(2, size.execute(supplementary).asInt(), "Unicode scalar count");
            assertEquals(3, size.execute("\u00f1a\u20ac").asInt());
            assertEquals("a" + supplementary, prefixed.execute(supplementary).asString());
            assertTrue(embedded(context).isLive());
        }
    }

    @Test
    void unsupportedJavaArgumentsAreRejectedBeforeGuestExecution() {
        try (Context context = builder().build()) {
            context.eval(ProtosLanguage.ID, SCALAR_SOURCE);
            Value bindings = context.getBindings(ProtosLanguage.ID);
            Value bump = bindings.getMember("bump");

            for (Object unsupported :
                    new Object[] {
                        "\ud800", "a\udc00b", 1.5d, 1.5f, true, 'c', new Object(), List.of(1),
                        BigInteger.ONE, (Runnable) () -> {}
                    }) {
                assertThrows(
                        IllegalArgumentException.class,
                        () -> bump.execute(unsupported),
                        () -> "rejected: " + unsupported.getClass());
            }
            assertEquals(0, bindings.getMember("count").asInt(), "no guest code executed");
            assertTrue(embedded(context).isLive(), "a boundary rejection is not fatal");
            assertEquals(4, bump.execute(4).asInt());
            assertEquals(1, bindings.getMember("count").asInt());
        }
    }

    /** I115-H6: source-backed host execution admits exactly zero or one supplied argument. */
    @Test
    void sourceBackedHostEntryAdmitsZeroOrOneArgument() {
        try (Context context = builder().build()) {
            context.eval(ProtosLanguage.ID, SCALAR_SOURCE);
            Value bindings = context.getBindings(ProtosLanguage.ID);
            Value count = bindings.getMember("count");
            Value id = bindings.getMember("id");
            assertEquals(0, count.asInt());
            assertEquals(9, id.execute(9).asInt());
            Value literal = context.eval(ProtosLanguage.ID, "literal: () => { 6 }\n0");
            assertEquals(0, literal.asInt());
            assertEquals(6, bindings.getMember("literal").execute().asInt());
        }
    }

    /**
     * I115-H6: an Error escaping a source-backed host entry reaches the host without the ordinary
     * host-entry failure protocol, so the entry itself does not fail the RootActor.
     */
    @Test
    void escapingErrorIsNotMappedToRootActorFailureByTheExperimentalEntry() {
        try (Context context = builder().build()) {
            context.eval(
                    ProtosLanguage.ID,
                    """
                    boom: () => { Error().signal() }
                    safe: () => { 1 }
                    0
                    """);
            Value bindings = context.getBindings(ProtosLanguage.ID);
            Value boom = bindings.getMember("boom");
            Value safe = bindings.getMember("safe");
            ProtosEmbeddedProcess process = embedded(context);

            assertThrows(PolyglotException.class, boom::execute);
            assertTrue(process.isLive(), "no outermost-entry failure processing");
            assertEquals(1, safe.execute().asInt());
            assertSame(process, embedded(context));
        }
    }

    /** I115-H6: a stopped Process is not consulted by the experimental source-backed entry. */
    @Test
    void terminatedProcessDoesNotGateSourceBackedHostEntry() {
        try (Context context = builder().build()) {
            context.eval(ProtosLanguage.ID, "safe: () => { 1 }\n0");
            Value safe = context.getBindings(ProtosLanguage.ID).getMember("safe");
            ProtosEmbeddedProcess process = embedded(context);

            process.processForTesting().requestTerminationForRuntime();
            assertFalse(process.isLive());

            assertEquals(1, safe.execute().asInt(), "no Process admission decision");
        }
    }

    @Test
    void unhandledErrorInAnEvaluationIsFatal() {
        try (Context context = builder().build()) {
            context.eval(ProtosLanguage.ID, "a: 1\n0");
            PolyglotException fatal =
                    assertThrows(
                            PolyglotException.class,
                            () -> context.eval(ProtosLanguage.ID, "b: 2\nError().signal()\n"));
            assertTrue(fatal.isGuestException());
            assertFalse(embedded(context).isLive());
            assertTrue(context.getBindings(ProtosLanguage.ID).getMemberKeys().isEmpty());
        }
    }

    @Test
    void contextCloseTerminatesTheProcess() {
        ProtosProcessRuntime process;
        try (Context context = builder().build()) {
            context.eval(ProtosLanguage.ID, "a: 1\n0");
            process = embedded(context).processForTesting();
            assertEquals(ProtosProcessRuntime.LifecycleState.RUNNING, process.lifecycleState());
        }
        assertEquals(ProtosProcessRuntime.LifecycleState.TERMINATED, process.lifecycleState());
    }

    @Test
    void argumentsEnvironmentAndStreamsComeFromTheContext() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (Context context =
                builder()
                        .arguments(ProtosLanguage.ID, new String[] {"first", "second"})
                        .environment(Map.of("MODE", "embedded"))
                        .out(out)
                        .build()) {
            Value mode =
                    context.eval(
                            ProtosLanguage.ID,
                            """
                            second: process.args().at(1)
                            process.stdout().write(process.stdoutEncoding().encode("hi")).value()
                            process.environment().get("MODE")
                            """);
            assertEquals("embedded", mode.asString());
            assertEquals(
                    "second", context.getBindings(ProtosLanguage.ID).getMember("second").asString());
        }
        assertEquals("hi", out.toString(StandardCharsets.UTF_8));
    }

    @Test
    void concurrentRootActorEntryIsRejectedNotQueued() throws Exception {
        try (Context context = builder().build()) {
            context.eval(ProtosLanguage.ID, "a: 1\n0");
            Value bindings = context.getBindings(ProtosLanguage.ID);
            ProtosEmbeddedProcess process = embedded(context);
            Thread token = process.enterRootActor();
            try {
                CompletableFuture<Throwable> other =
                        CompletableFuture.supplyAsync(
                                () -> {
                                    try {
                                        bindings.getMember("a");
                                        return null;
                                    } catch (PolyglotException rejected) {
                                        return rejected;
                                    }
                                });
                Throwable rejected = other.get();
                assertNotNull(rejected, "a concurrent entry is rejected");
                assertFalse(((PolyglotException) rejected).isInternalError());
            } finally {
                process.exitRootActor(token);
            }
            assertEquals(1, bindings.getMember("a").asInt(), "entry admitted once released");
            assertTrue(process.isLive(), "a rejected entry is not fatal");
        }
    }

    /**
     * I115-H6: source-backed execution does not take RootActor admission, so it is not excluded
     * while another thread holds the RootActor token. Binding reads above remain exclusive.
     */
    @Test
    void sourceBackedExecutionIsNotExcludedByAHeldRootActor() throws Exception {
        try (Context context = builder().build()) {
            context.eval(ProtosLanguage.ID, "literal: () => { 8 }\nid: (x) => { x }\n0");
            Value bindings = context.getBindings(ProtosLanguage.ID);
            Value literal = bindings.getMember("literal");
            Value id = bindings.getMember("id");
            ProtosEmbeddedProcess process = embedded(context);
            Thread token = process.enterRootActor();
            try {
                assertEquals(
                        8L,
                        CompletableFuture.supplyAsync(() -> literal.execute().asLong()).get());
                assertEquals(
                        3L, CompletableFuture.supplyAsync(() -> id.execute(3).asLong()).get());
                assertFalse(
                        process.permitsUnadmittedNonSuspendingHostEntry(),
                        "the held token is untouched");
            } finally {
                process.exitRootActor(token);
            }
            assertTrue(process.isLive());
        }
    }

    @Test
    void invalidCoreOverrideFailsExplicitly() {
        try (Context context =
                Context.newBuilder(ProtosLanguage.ID)
                        .option("protos.CoreRoot", CORE + "/missing")
                        .build()) {
            PolyglotException failure =
                    assertThrows(PolyglotException.class, () -> context.eval(ProtosLanguage.ID, "1"));
            assertTrue(failure.getMessage().contains("protos.CoreRoot"));
            assertNull(embedded(context));
        }
    }
}
