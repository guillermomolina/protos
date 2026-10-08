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
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.runtime.ProtosProcessRuntime;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * I087 / PLAT055: Java-supplied application modules of a standard Polyglot Context through {@link
 * ProtosEmbeddedModules} ({@code MODULES.md} module identity, cache, cycles, and failure; {@code
 * ACTORS.md} §8 {@code Actor.spawn}; {@code PROCESS_IO.md} Standard Polyglot embedding bootstrap
 * and authority).
 */
@Timeout(value = 10, unit = TimeUnit.SECONDS)
final class ProtosEmbeddedModulesTest {
    private static final String CORE = Path.of("protos", "lib", "core").toAbsolutePath().toString();

    /** Shared mutable state observed by the modules that count their own initializations. */
    private static final String STATE = "attempts: 0\nloads: 0\n";

    private static final String ONCE =
            """
            state: import("app:state")
            state.loads = state.loads + 1
            """;

    /** Fails its first initialization only, so a retry can succeed. */
    private static final String FLAKY =
            """
            state: import("app:state")
            state.attempts = state.attempts + 1
            (state.attempts == 1).ifTrue(() => { Error().signal() })
            value: state.attempts
            """;

    private static final String CYCLE_A =
            """
            b: import("app:b")
            name: "a"
            """;

    private static final String CYCLE_B =
            """
            partial: import("app:a")
            """;

    /** Each Actor that spawns it initializes its own instance, counted by {@code instances}. */
    private static final String WORKER =
            """
            instances: 0
            start: (initial) => {
                instances = instances + 1
                count: initial
                {
                    increment: () => {
                        count = count + 1
                        count
                    }
                    moduleInstances: () => { instances }
                    sameModule: () => { import("app:worker") === import("app:worker") }
                }
            }
            """;

    private static Context.Builder builder() {
        return Context.newBuilder(ProtosLanguage.ID).option("protos.CoreRoot", CORE);
    }

    private static Map<String, String> catalog() {
        return Map.of(
                "app:state", STATE,
                "app:once", ONCE,
                "app:twin1", "name: \"twin\"\n",
                "app:twin2", "name: \"twin\"\n",
                "app:flaky", FLAKY,
                "app:a", CYCLE_A,
                "app:b", CYCLE_B,
                "app:worker", WORKER);
    }

    private static ProtosLanguageContext languageContext(Context context) {
        // Creates the language Context only (never the Process), so it can be inspected.
        context.initialize(ProtosLanguage.ID);
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

    private static String outcome(Context context, String body) {
        return context.eval(
                        ProtosLanguage.ID,
                        "Error.handle(() => {\n"
                                + body
                                + "\n\"ok\"\n}, (error) => { \"failed\" })")
                .asString();
    }

    @Test
    void importResolvesCachesAndKeepsSpecifierIdentity() {
        try (Context context = builder().build()) {
            ProtosEmbeddedModules.install(context, catalog());
            context.eval(
                    ProtosLanguage.ID,
                    """
                    x: import("app:once")
                    same: x === import("app:once")
                    loads: import("app:state").loads
                    twins: import("app:twin1") !== import("app:twin2")
                    twinName: import("app:twin2").name
                    0
                    """);
            Value bindings = context.getBindings(ProtosLanguage.ID);
            assertTrue(bindings.getMember("same").asBoolean(), "=== within one Actor");
            assertEquals(1, bindings.getMember("loads").asInt(), "the body runs once");
            assertTrue(bindings.getMember("twins").asBoolean(), "content is not identity");
            assertEquals("twin", bindings.getMember("twinName").asString());
            // A later host entry of the same RootActor sees the same cached instance.
            assertEquals(
                    1, context.eval(ProtosLanguage.ID, "import(\"app:state\").loads").asInt());
        }
    }

    @Test
    void cyclicImportsObserveThePartiallyInitializedModule() {
        try (Context context = builder().build()) {
            ProtosEmbeddedModules.install(context, catalog());
            Value cycle =
                    context.eval(
                            ProtosLanguage.ID,
                            "a: import(\"app:a\")\n(a.b.partial === a) && (a.name == \"a\")");
            assertTrue(cycle.asBoolean());
        }
    }

    @Test
    void failedInitializationIsAnOrdinaryErrorAndALaterImportRetries() {
        try (Context context = builder().build()) {
            ProtosEmbeddedModules.install(context, catalog());
            assertEquals("failed", outcome(context, "import(\"app:flaky\")"));
            assertTrue(embedded(context).isLive(), "a handled Error is not fatal");
            assertEquals(
                    2, context.eval(ProtosLanguage.ID, "import(\"app:flaky\").value").asInt());
        }
    }

    @Test
    void unauthorizedAndUnknownSpecifiersAreOrdinaryResolutionErrors() {
        try (Context context = builder().build()) {
            ProtosEmbeddedModules.install(context, Map.of("app:known", "v: 1\n"));
            assertEquals("failed", outcome(context, "import(\"app:unknown\")"));
            assertEquals("failed", outcome(context, "import(\"app:\")"));
            assertEquals("failed", outcome(context, "import(\"known\")"));
            assertEquals("failed", outcome(context, "import(\"app:known.protos\")"));
            assertEquals("failed", outcome(context, "Actor.spawn(\"app:unknown\", \"start\")"));
            // std: still resolves from the Standard Library alongside the catalog.
            assertEquals("ok", outcome(context, "import(\"std:test/Assertions\")"));
            assertTrue(embedded(context).isLive());
        }
    }

    @Test
    void spawnCreatesAnIndependentInstanceInEachActor() {
        try (Context context = builder().allowCreateThread(true).build()) {
            ProtosEmbeddedModules.install(context, catalog());
            context.eval(
                    ProtosLanguage.ID,
                    """
                    left: Actor.spawn("app:worker", "start", 10)
                    right: Actor.spawn("app:worker", "start", 20)
                    l1: left.request("increment").value()
                    l2: left.request("increment").value()
                    r1: right.request("increment").value()
                    leftInstances: left.request("moduleInstances").value()
                    rightInstances: right.request("moduleInstances").value()
                    sameInActor: left.request("sameModule").value()
                    rootInstances: import("app:worker").instances
                    0
                    """);
            Value bindings = context.getBindings(ProtosLanguage.ID);
            assertEquals(11, bindings.getMember("l1").asInt(), "arguments are transferred");
            assertEquals(12, bindings.getMember("l2").asInt(), "state persists in one Actor");
            assertEquals(21, bindings.getMember("r1").asInt(), "state is not shared");
            assertEquals(1, bindings.getMember("leftInstances").asInt());
            assertEquals(1, bindings.getMember("rightInstances").asInt());
            assertTrue(bindings.getMember("sameInActor").asBoolean());
            assertEquals(0, bindings.getMember("rootInstances").asInt(), "the RootActor's own");
        }
    }

    @Test
    void spawnReadsOnlyAnOwnBootstrapBinding() {
        try (Context context = builder().allowCreateThread(true).build()) {
            ProtosEmbeddedModules.install(context, catalog());
            // Actor is inherited from the prelude, not an own slot of the module: the destination
            // Actor fails its bootstrap after the creation cutover, so spawn itself returns.
            assertEquals(
                    "failed",
                    outcome(
                            context,
                            "w: Actor.spawn(\"app:worker\", \"Actor\")\nw.request(\"increment\").value()"));
            assertEquals(
                    "ok",
                    outcome(
                            context,
                            "w: Actor.spawn(\"app:worker\", \"start\", 0)\nw.request(\"increment\").value()"));
            assertTrue(embedded(context).isLive());
        }
    }

    @Test
    void hostEvaluationsNeverAcquireACatalogIdentity() {
        try (Context context = builder().build()) {
            ProtosEmbeddedModules.install(context, Map.of("app:probe", "origin: \"catalog\"\n"));
            Source sameName =
                    Source.newBuilder(ProtosLanguage.ID, "origin: \"catalog\"\n", "app:probe")
                            .buildLiteral();
            context.eval(sameName);
            Object first = embedded(context).selectedModuleContext().orElseThrow();
            context.eval(sameName);
            Object second = embedded(context).selectedModuleContext().orElseThrow();
            assertNotSame(first, second, "each evaluation is a fresh standalone entry");

            Value entry =
                    context.eval(
                            Source.newBuilder(
                                            ProtosLanguage.ID,
                                            "origin: \"eval\"\nimport(\"app:probe\").origin",
                                            "app:probe")
                                    .buildLiteral());
            assertEquals("catalog", entry.asString(), "the entry is not the catalog module");
            assertEquals("eval", context.getBindings(ProtosLanguage.ID).getMember("origin").asString());

            Value bindings = context.getBindings(ProtosLanguage.ID);
            assertFalse(bindings.hasMember("app:probe"));
            assertTrue(context.getPolyglotBindings().getMemberKeys().isEmpty());
        }
    }

    @Test
    void invalidCatalogsAreRejectedBeforeAnythingIsPublished() {
        try (Context context = builder().build()) {
            assertThrows(NullPointerException.class, () -> ProtosEmbeddedModules.install(null, Map.of()));
            assertThrows(NullPointerException.class, () -> ProtosEmbeddedModules.install(context, null));
            HashMap<String, String> nullSource = new HashMap<>();
            nullSource.put("app:a", null);
            assertThrows(
                    NullPointerException.class, () -> ProtosEmbeddedModules.install(context, nullSource));
            HashMap<String, String> nullKey = new HashMap<>();
            nullKey.put(null, "v: 1\n");
            assertThrows(
                    NullPointerException.class, () -> ProtosEmbeddedModules.install(context, nullKey));
            for (String invalid :
                    new String[] {
                        "app:", "std:collections/Set", "foreign:v1:amF2YQ:eA", "java:x", "worker",
                        "APP:worker", " app:worker", "./app:worker"
                    }) {
                // A valid entry beside the invalid one must not be published either.
                Map<String, String> mixed = Map.of("app:valid", "v: 1\n", invalid, "v: 2\n");
                assertThrows(
                        IllegalArgumentException.class,
                        () -> ProtosEmbeddedModules.install(context, mixed),
                        invalid);
            }
            assertNull(languageContext(context).applicationModulesForBootstrap());
            assertNull(embedded(context), "rejection creates no Process");
            // The Context still accepts a valid installation afterwards.
            ProtosEmbeddedModules.install(context, Map.of("app:valid", "v: 1\n"));
            assertEquals(1, context.eval(ProtosLanguage.ID, "import(\"app:valid\").v").asInt());
        }
    }

    @Test
    void installationIsOncePerContextAndOnlyBeforeBootstrap() {
        try (Context context = builder().build()) {
            ProtosEmbeddedModules.install(context, Map.of());
            assertThrows(
                    IllegalStateException.class,
                    () -> ProtosEmbeddedModules.install(context, Map.of()));
        }
        try (Context context = builder().build()) {
            context.eval(ProtosLanguage.ID, "1");
            assertThrows(
                    IllegalStateException.class,
                    () -> ProtosEmbeddedModules.install(context, Map.of("app:m", "v: 1\n")));
        }
        // A failed bootstrap has begun too, and is not reopened for installation.
        try (Context context =
                Context.newBuilder(ProtosLanguage.ID)
                        .option("protos.CoreRoot", CORE + "/missing")
                        .build()) {
            assertThrows(PolyglotException.class, () -> context.eval(ProtosLanguage.ID, "1"));
            assertNull(embedded(context));
            assertThrows(
                    IllegalStateException.class,
                    () -> ProtosEmbeddedModules.install(context, Map.of()));
        }
        Context closed = builder().build();
        closed.close();
        assertThrows(
                IllegalStateException.class, () -> ProtosEmbeddedModules.install(closed, Map.of()));
    }

    @Test
    void initializeBindingsAndParsingDoNotCloseInstallation() {
        try (Context context = builder().build()) {
            context.initialize(ProtosLanguage.ID);
            Value bindings = context.getBindings(ProtosLanguage.ID);
            assertTrue(bindings.getMemberKeys().isEmpty());
            context.parse(ProtosLanguage.ID, "x: import(\"app:m\").v\n");
            assertNull(embedded(context));

            ProtosEmbeddedModules.install(context, Map.of("app:m", "v: 5\n"));
            assertNull(embedded(context), "installation does not create the Process");
            context.eval(ProtosLanguage.ID, "x: import(\"app:m\").v\n0");
            assertEquals(5, bindings.getMember("x").asInt());
        }
    }

    @Test
    void installationIsAtomicWithRespectToAConcurrentBootstrap() throws Exception {
        for (int round = 0; round < 3; round++) {
            try (Context context = builder().build()) {
                context.initialize(ProtosLanguage.ID);
                CountDownLatch start = new CountDownLatch(1);
                CompletableFuture<Boolean> installed =
                        CompletableFuture.supplyAsync(
                                () -> {
                                    await(start);
                                    try {
                                        ProtosEmbeddedModules.install(
                                                context, Map.of("app:m", "v: 1\n"));
                                        return true;
                                    } catch (IllegalStateException lateInstallation) {
                                        return false;
                                    }
                                });
                CompletableFuture<Void> evaluated =
                        CompletableFuture.runAsync(
                                () -> {
                                    await(start);
                                    context.eval(ProtosLanguage.ID, "1");
                                });
                start.countDown();
                evaluated.get();
                boolean catalogInstalled = installed.get();
                // Either the bootstrap saw the whole catalog, or the installation was rejected.
                assertEquals(
                        catalogInstalled ? "ok" : "failed",
                        outcome(context, "import(\"app:m\")"),
                        "round " + round);
            }
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }

    @Test
    void contextsKeepTheirOwnCatalogsIncludingOnASharedEngine() {
        try (Engine engine =
                        Engine.newBuilder(ProtosLanguage.ID)
                                .option("protos.CoreRoot", CORE)
                                .option("engine.WarnInterpreterOnly", "false")
                                .build();
                Context left = Context.newBuilder(ProtosLanguage.ID).engine(engine).build();
                Context right = Context.newBuilder(ProtosLanguage.ID).engine(engine).build();
                Context plain = Context.newBuilder(ProtosLanguage.ID).engine(engine).build()) {
            ProtosEmbeddedModules.install(left, Map.of("app:m", "v: \"left\"\n"));
            ProtosEmbeddedModules.install(right, Map.of("app:m", "v: \"right\"\n"));
            assertEquals("left", left.eval(ProtosLanguage.ID, "import(\"app:m\").v").asString());
            assertEquals("right", right.eval(ProtosLanguage.ID, "import(\"app:m\").v").asString());
            assertEquals("failed", outcome(plain, "import(\"app:m\")"));
        }
    }

    @Test
    void theCatalogGrantsNoAmbientAuthority() {
        try (Context context = builder().build()) {
            ProtosEmbeddedModules.install(
                    context, Map.of("app:probe", "module: 1\n"));
            context.eval(ProtosLanguage.ID, "m: import(\"app:probe\")\n0");
            Value bindings = context.getBindings(ProtosLanguage.ID);
            assertFalse(bindings.hasMember("filesystem"), "no default Filesystem authority");
            assertFalse(bindings.hasMember("network"), "no default Network authority");
            assertNull(embedded(context).filesystemCustodyForTesting());
            assertNull(embedded(context).networkCustodyForTesting());
            // A catalog module receives no bootstrap-local authority slot of its own.
            assertEquals("failed", outcome(context, "import(\"app:probe\").filesystem"));
            assertEquals("failed", outcome(context, "import(\"app:probe\").process"));
        }
    }

    @Test
    void contextCloseTerminatesAProcessUsingTheCatalog() {
        ProtosEmbeddedProcess process;
        try (Context context = builder().allowCreateThread(true).build()) {
            ProtosEmbeddedModules.install(context, catalog());
            context.eval(
                    ProtosLanguage.ID,
                    "w: Actor.spawn(\"app:worker\", \"start\", 0)\nw.request(\"increment\").value()");
            process = embedded(context);
            assertTrue(process.isLive());
        }
        assertFalse(process.isLive());
        assertEquals(
                ProtosProcessRuntime.LifecycleState.TERMINATED,
                process.processForTesting().lifecycleState());
        assertTrue(process.actorCarriersTerminatedForTesting());
    }

    @Test
    void theCatalogIsAnImmutableCopyAndLoadsLazily() {
        try (Context context = builder().build()) {
            HashMap<String, String> modules = new HashMap<>();
            modules.put("app:m", "v: 1\n");
            // Never imported, so never compiled: its syntax error cannot affect the Process.
            modules.put("app:broken", "x: (((\n");
            ProtosEmbeddedModules.install(context, modules);
            modules.put("app:late", "v: 2\n");
            modules.put("app:m", "v: 3\n");

            Map<String, String> stored = languageContext(context).applicationModulesForBootstrap();
            assertThrows(UnsupportedOperationException.class, () -> stored.put("app:x", ""));
            assertEquals(2, stored.size());

            assertEquals(1, context.eval(ProtosLanguage.ID, "import(\"app:m\").v").asInt());
            assertEquals("failed", outcome(context, "import(\"app:late\")"));
            assertInstanceOf(
                    ProtosApplicationModuleResolver.class,
                    languageContext(context).boundModuleResolverForRuntime());
        }
    }

    /** PAY AS YOU GROW: a Context without entries keeps the existing resolver path unchanged. */
    @Test
    void withoutEntriesNothingIsAddedToTheProcess() {
        try (Context context = builder().build()) {
            ProtosLanguageContext language = languageContext(context);
            assertNull(language.applicationModulesForBootstrap(), "no catalog without install");
            assertEquals(42, context.eval(ProtosLanguage.ID, "f: () => { 42 }\nf()").asInt());
            assertInstanceOf(
                    ProtosStandardLibraryModuleResolver.class,
                    language.boundModuleResolverForRuntime());
            ProtosEmbeddedProcess process = embedded(context);
            assertEquals(
                    0,
                    process.processForTesting()
                            .rootActorForRuntime()
                            .executionDomain()
                            .liveTaskCount());
            assertFalse(process.actorCarrierSubstrateInitializedForTesting());
        }
        try (Context context = builder().build()) {
            ProtosEmbeddedModules.install(context, Map.of());
            assertNotNull(languageContext(context).applicationModulesForBootstrap());
            assertNull(embedded(context), "an empty installation creates no Process");
            assertEquals(1, context.eval(ProtosLanguage.ID, "1").asInt());
            assertInstanceOf(
                    ProtosStandardLibraryModuleResolver.class,
                    languageContext(context).boundModuleResolverForRuntime());
            ProtosEmbeddedProcess process = embedded(context);
            assertEquals(
                    0,
                    process.processForTesting()
                            .rootActorForRuntime()
                            .executionDomain()
                            .liveTaskCount());
            assertFalse(process.actorCarrierSubstrateInitializedForTesting());
        }
        try (Context context = builder().build()) {
            ProtosEmbeddedModules.install(context, Map.of("app:m", "v: 1\n"));
            // Installation alone starts no Actor substrate and no Task.
            assertEquals(1, context.eval(ProtosLanguage.ID, "1").asInt());
            ProtosEmbeddedProcess process = embedded(context);
            assertSame(process, embedded(context));
            assertFalse(process.actorCarrierSubstrateInitializedForTesting());
        }
    }
}
