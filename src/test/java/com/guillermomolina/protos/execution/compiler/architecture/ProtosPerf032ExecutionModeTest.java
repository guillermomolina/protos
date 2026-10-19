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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.runtime.control.ProtosReturnHome;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.bytecode.TagTreeNode;
import com.oracle.truffle.api.debug.Breakpoint;
import com.oracle.truffle.api.debug.DebugScope;
import com.oracle.truffle.api.debug.DebugValue;
import com.oracle.truffle.api.debug.Debugger;
import com.oracle.truffle.api.debug.DebuggerSession;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Instrument;
import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * PLAT067-B / D208-A: there is no guest execution mode. Every Context lowers its executables
 * through the one proof-optimized path, and a debugger, attached at any time, observes that same
 * lowering: it never selects another one. I102 (PLAT060-B5): every host entry uses the same
 * five-slot header, and a debugger attached after a call was prepared or first executed observes
 * the exact scope of every surviving executable location.
 *
 * <p>Historically PERF032 selected an immutable NORMAL/DEBUG mode here; the class keeps its name
 * so its history stays traceable, while each test now compares execution without and with an
 * attached Truffle debugger.
 */
final class ProtosPerf032ExecutionModeTest {
    private static final Path CORE = Path.of("protos", "lib", "core");
    /** A dynamic body: the ordinary statement lowering. */
    private static final String SOURCE =
            """
            marker: "perf032-marker"
            run: () => {
                marker.size() - 13
            }
            0
            """;
    /**
     * The optimized body: I101 lowers {@code run} to a constant-return root whose surviving
     * return keeps the statement's passive tags (PLAT067-B).
     */
    private static final String CONSTANT_SOURCE =
            """
            marker: "perf032-marker"
            run: () => {
                1
            }
            0
            """;
    private static final int BODY_LINE = 3;

    @TempDir
    Path directory;

    @Test
    void i095B6aOwnScalarDebuggerParity() throws Exception {
        String source = """
                run: () => {
                    value: 9
                    inside: true.ifTrue(() => {
                        value: 41
                        value = 42
                        value
                    })
                    inside + value
                }
                0
                """;
        for (boolean debugger : new boolean[] {false, true}) {
            String label = label(debugger);
            Path file = directory.resolve("i095-b6a-" + debugger + ".protos");
            Files.writeString(file, source, StandardCharsets.UTF_8);
            try (ProtosStandaloneHostedSession session = ProtosStandaloneHostedSession.open(CORE, file);
                    DebuggerSession attached = debugger ? attach(session) : null) {
                assertEquals(ProtosExecutionOutcome.State.COMPLETED,
                        session.initialOutcome().state(), label);
                Value run = session.prepareTopLevel("run").executable();
                for (int i = 0; i < 3; i++) {
                    assertEquals(51, run.execute().asInt(), label);
                }
                assertNoTask(session);
            }
        }
        System.out.println("I095_B6A_DEBUGGER_PARITY=PASS");
    }

    @Test
    void i095B6bMixedCaptureDebuggerParity() throws Exception {
        String source = """
                run: () => {
                    outer: 40
                    shadow: 11
                    inside: true.ifTrue(() => {
                        shadow: 1
                        outer = outer + shadow
                        shadow = 2
                        outer + shadow
                    })
                    inside + outer + shadow
                }
                0
                """;
        for (boolean debugger : new boolean[] {false, true}) {
            String label = label(debugger);
            Path file = directory.resolve("i095-b6b-" + debugger + ".protos");
            Files.writeString(file, source, StandardCharsets.UTF_8);
            try (ProtosStandaloneHostedSession session = ProtosStandaloneHostedSession.open(CORE, file);
                    DebuggerSession attached = debugger ? attach(session) : null) {
                assertEquals(ProtosExecutionOutcome.State.COMPLETED,
                        session.initialOutcome().state(), label);
                Value run = session.prepareTopLevel("run").executable();
                for (int i = 0; i < 3; i++) {
                    assertEquals(95, run.execute().asInt(), label);
                }
                assertNoTask(session);
            }
        }
        System.out.println("I095_B6B_DEBUGGER_PARITY=PASS");
    }

    @Test
    void hostEntersTheClosureWithTheB5Header() throws Exception {
        try (ProtosStandaloneHostedSession session = open("plain")) {
            assertFalse(session.runtimeHostForTesting().isDebugMode());
            ProtosStandaloneHostedSession.PreparedTopLevel prepared =
                    session.prepareTopLevel("run");
            ProtosHostExecutableClosure adapter = prepared.hostExecutableForTesting();
            assertEquals(1, prepared.executable().execute().asInt());
            assertB5ZeroArgumentHeader(adapter, arguments(session, adapter));
            assertNoTask(session);
        }
        System.out.println("I102_B5_HEADER=PASS");
    }

    @Test
    void attachedDebuggerEntersTheSameClosureWithTheB5Header() throws Exception {
        try (ProtosStandaloneHostedSession session = open("attached");
                DebuggerSession attached = attach(session)) {
            // D208-A: isDebugMode describes DAP configuration only; this host has no DAP.
            assertFalse(session.runtimeHostForTesting().isDebugMode());
            ProtosStandaloneHostedSession.PreparedTopLevel prepared =
                    session.prepareTopLevel("run");
            ProtosHostExecutableClosure adapter = prepared.hostExecutableForTesting();
            assertEquals(1, prepared.executable().execute().asInt());
            assertB5ZeroArgumentHeader(adapter, arguments(session, adapter));
            assertNoTask(session);
        }
        System.out.println("PLAT067_DEBUGGER_B5_HEADER=PASS");
    }

    @Test
    void ordinaryHostDoesNotStartDap() {
        try (ProtosPolyglotRuntimeHost host = ProtosPolyglotRuntimeHost.open()) {
            assertFalse(host.isDebugMode());
            assertThrows(IllegalStateException.class, host::debugEndpoint);
        }
        System.out.println("PERF032_NORMAL_NO_DAP=PASS");
    }

    /** PLAT067-B: a debugger attached after the first call observes the exact lexical scope. */
    @Test
    void debuggerAttachedAfterFirstExecutionObservesTheExactLexicalScope() throws Exception {
        try (ProtosStandaloneHostedSession session = open("late-attach")) {
            Value executable = session.prepareTopLevel("run").executable();
            assertEquals(1, executable.execute().asInt(), "first call precedes the debugger");

            ScopeObservation observation = new ScopeObservation();
            try (DebuggerSession debugSession = startSession(session, "late-attach", observation)) {
                assertEquals(1, executable.execute().asInt());
            }
            observation.assertObserved("perf032-marker");
            assertNoTask(session);
        }
        System.out.println("PLAT067_LATE_ATTACH_SCOPE=PASS");
    }

    /**
     * PLAT067-B: the constant-return Closure {@code () => { 1 }} keeps its surviving statement
     * location. A debugger attached after the first execution, before or after the on-demand
     * materialization of the root's tags, halts there with the exact scope; the suspension is the
     * Closure's own and no callback or frame is fabricated.
     */
    @Test
    void constantReturnClosureKeepsItsBreakpointLocation() throws Exception {
        for (boolean materializedFirst : new boolean[] {false, true}) {
            String name = "constant-" + materializedFirst;
            try (ProtosStandaloneHostedSession session = open(name, CONSTANT_SOURCE)) {
                ProtosStandaloneHostedSession.PreparedTopLevel prepared =
                        session.prepareTopLevel("run");
                Value executable = prepared.executable();
                assertEquals(1, executable.execute().asInt(), "first call precedes the debugger");
                if (materializedFirst) {
                    ProtosHostExecutableClosure adapter = prepared.hostExecutableForTesting();
                    session.processContextForTesting()
                            .callForRuntime(
                                    () -> {
                                        ((ProtosBytecodeRootNode) adapter.target.getRootNode())
                                                .getRootNodes()
                                                .ensureComplete();
                                        return null;
                                    });
                }

                ScopeObservation observation = new ScopeObservation();
                try (DebuggerSession debugSession = startSession(session, name, observation)) {
                    assertEquals(1, executable.execute().asInt());
                }
                observation.assertObserved("perf032-marker");
                assertNoTask(session);
            }
        }
        System.out.println("PLAT067_CONSTANT_RETURN_BREAKPOINT=PASS");
    }

    /** PLAT067-B: a call prepared before the debugger attached keeps its exact scope. */
    @Test
    void callPreparedBeforeTheDebuggerKeepsItsScope() throws Exception {
        try (ProtosStandaloneHostedSession session = open("prepared")) {
            ProtosHostExecutableClosure adapter =
                    session.prepareTopLevel("run").hostExecutableForTesting();
            ProtosPreparedCalls.OrdinarySourceCall prepared =
                    session.processContextForTesting()
                            .callForRuntime(() -> prepareNoArguments(adapter));
            assertB5ZeroArgumentHeader(adapter, prepared.targetArguments());

            ScopeObservation observation = new ScopeObservation();
            try (DebuggerSession debugSession = startSession(session, "prepared", observation)) {
                Object result =
                        session.processContextForTesting()
                                .callForRuntime(
                                        () ->
                                                prepared.finish(
                                                        ProtosBytecodeRootNode.EnterClosureCall
                                                                .ordinaryIndirect(
                                                                        prepared,
                                                                        IndirectCallNode.create())));
                assertEquals(1, InteropLibrary.getUncached().asInt(result));
            }
            observation.assertObserved("perf032-marker");
            assertNoTask(session);
        }
        System.out.println("PLAT067_PREPARED_BEFORE_ATTACH=PASS");
    }

    @Test
    void toolingFrameIsNotRecognizedByItsArgumentShape() throws Exception {
        try (ProtosStandaloneHostedSession session = open("tooling")) {
            ProtosHostExecutableClosure adapter =
                    session.prepareTopLevel("run").hostExecutableForTesting();

            // I102: tooling sees the B5 frame; nothing is inferred from its shape.
            session.processContextForTesting()
                    .callForRuntime(
                            () -> {
                                ProtosBytecodeRootNode root =
                                        (ProtosBytecodeRootNode)
                                                adapter.target.getRootNode();
                                root.getRootNodes().ensureComplete();
                                TagTreeNode location =
                                        (TagTreeNode) root.getBytecodeNode().getTagTree();
                                assertNotNull(location);
                                Object[] arguments = prepareNoArguments(adapter).targetArguments();
                                assertB5ZeroArgumentHeader(adapter, arguments);
                                var frame =
                                        Truffle.getRuntime()
                                                .createVirtualFrame(
                                                        arguments, root.getFrameDescriptor());
                                assertTrue(ProtosBytecodeTagTreeNodeExports.hasScope(location, frame));
                                assertSame(
                                        adapter.closure,
                                        arguments[0],
                                        "hasScope does not materialize the activation");
                                /*
                                 * Synthetic frame: this projects a scope whose information the
                                 * B5 frame already carries; no debugger is attached.
                                 */
                                InteropLibrary interop = InteropLibrary.getUncached();
                                try {
                                    Object first =
                                            ProtosBytecodeTagTreeNodeExports.getScope(
                                                    location, frame, true);
                                    Object second =
                                            ProtosBytecodeTagTreeNodeExports.getScope(
                                                    location, frame, true);
                                    assertTrue(interop.isScope(first));
                                    assertTrue(interop.isScope(second));
                                    var published =
                                            ProtosFrameArguments.activation(frame);
                                    assertSame(published, arguments[0],
                                            "the scope projects the invocation's activation");
                                    assertSame(published, ProtosFrameArguments.activation(arguments),
                                            "a second observation reuses that activation");
                                    assertSame(ProtosReturnHome.unobservable(),
                                            published.returnHome().orElseThrow());
                                    assertTrue(published.task().isEmpty());
                                    assertEquals(ProtosFrameArguments.HEADER_LENGTH,
                                            arguments.length);
                                    assertSame(adapter.caller, arguments[3]);
                                    assertTrue(interop.isMemberReadable(first, "marker"));
                                    assertEquals("perf032-marker",
                                            interop.asString(interop.readMember(first, "marker")));
                                } catch (Exception unexpected) {
                                    throw new AssertionError(unexpected);
                                }
                                return null;
                            });

            // Execution does not require debugger breakpoints or suspension.
            assertEquals(
                    1,
                    session.prepareTopLevel("run")
                            .executable()
                            .execute()
                            .asInt());
            assertNoTask(session);
        }
        System.out.println("I102_TOOLING_FRAME=PASS");
    }

    @Test
    void attachedDebuggerExecutesTheSameWholeInlineBody()
            throws Exception {
        String program = """
                run: () => {
                    true.ifTrue(() => {
                        1 + 2
                        3 * 4
                    })
                }
                0
                """;

        Path plainFile = directory.resolve("i095-b2-plain.protos");
        Path debuggerFile = directory.resolve("i095-b2-debugger.protos");
        Files.writeString(plainFile, program, StandardCharsets.UTF_8);
        Files.writeString(debuggerFile, program, StandardCharsets.UTF_8);

        try (ProtosStandaloneHostedSession plain =
                        ProtosStandaloneHostedSession.open(CORE, plainFile);
                ProtosStandaloneHostedSession observed =
                        ProtosStandaloneHostedSession.open(CORE, debuggerFile);
                DebuggerSession attached = attach(observed)) {

            assertEquals(
                    ProtosExecutionOutcome.State.COMPLETED,
                    plain.initialOutcome().state());
            assertEquals(
                    ProtosExecutionOutcome.State.COMPLETED,
                    observed.initialOutcome().state());

            Value plainRun = plain.prepareTopLevel("run").executable();
            Value observedRun = observed.prepareTopLevel("run").executable();

            for (int iteration = 0; iteration < 3; iteration++) {
                assertEquals(12, plainRun.execute().asInt());
                assertEquals(12, observedRun.execute().asInt());
            }

            assertNoTask(plain);
            assertNoTask(observed);
        }

        System.out.println("I095_B2_DEBUGGER_WHOLE_BODY=PASS");
    }

    @Test
    void i095WholeBlockCallerProvenanceParity() throws Exception {
        record Case(String name, String program, int expected) {}

        var cases = java.util.List.of(
                new Case(
                        "arithmetic",
                        """
                        run: () => {
                            true.ifTrue(() => {
                                1 + 2
                                3 * 4
                            })
                        }
                        0
                        """,
                        12),
                new Case(
                        "receiver",
                        """
                        run: () => {
                            obj: {
                                read: () => {
                                    (true.ifTrue(() => { this }) === this)
                                        .ifTrueIfFalse(() => { 41 }, () => { 0 })
                                }
                            }
                            obj.read()
                        }
                        0
                        """,
                        41),
                new Case(
                        "super",
                        """
                        run: () => {
                            base: {
                                read: () => { 41 }
                            }
                            middle: base {
                                read: () => {
                                    true.ifTrue(() => {
                                        super.read() + 1
                                    })
                                }
                            }
                            middle.read()
                        }
                        0
                        """,
                        42),
                new Case(
                        "handled-error",
                        """
                        run: () => {
                            Error.handle(
                                () => {
                                    true.ifTrue(() => { 1 / 0 })
                                    0
                                },
                                (caught) => { 73 })
                        }
                        0
                        """,
                        73));

        for (Case scenario : cases) {
            Path plainFile = directory.resolve(
                    "i095-b3-" + scenario.name() + "-plain.protos");
            Path debuggerFile = directory.resolve(
                    "i095-b3-" + scenario.name() + "-debugger.protos");

            Files.writeString(plainFile, scenario.program(), StandardCharsets.UTF_8);
            Files.writeString(debuggerFile, scenario.program(), StandardCharsets.UTF_8);

            try (ProtosStandaloneHostedSession plain =
                            ProtosStandaloneHostedSession.open(CORE, plainFile);
                    ProtosStandaloneHostedSession observed =
                            ProtosStandaloneHostedSession.open(CORE, debuggerFile);
                    DebuggerSession attached = attach(observed)) {

                assertEquals(
                        ProtosExecutionOutcome.State.COMPLETED,
                        plain.initialOutcome().state(),
                        scenario.name());
                assertEquals(
                        ProtosExecutionOutcome.State.COMPLETED,
                        observed.initialOutcome().state(),
                        scenario.name());

                Value plainRun = plain.prepareTopLevel("run").executable();
                Value observedRun = observed.prepareTopLevel("run").executable();

                for (int iteration = 0; iteration < 3; iteration++) {
                    assertEquals(
                            scenario.expected(),
                            plainRun.execute().asInt(),
                            label(false) + ": " + scenario.name());
                    assertEquals(
                            scenario.expected(),
                            observedRun.execute().asInt(),
                            label(true) + ": " + scenario.name());
                }

                assertNoTask(plain);
                assertNoTask(observed);
            }

            System.out.println(
                    "I095_B3_" + scenario.name()
                            .toUpperCase(java.util.Locale.ROOT)
                            .replace('-', '_')
                            + "=PASS");
        }
    }

    /** An attached debugger on one host never affects another host's execution. */
    @Test
    void simultaneousHostsWithAndWithoutDebuggerStayIndependent() throws Exception {
        try (ProtosStandaloneHostedSession plain = open("plain-host");
                ProtosStandaloneHostedSession observed = open("observed-host");
                DebuggerSession attached = attach(observed)) {
            ProtosStandaloneHostedSession.PreparedTopLevel plainRun =
                    plain.prepareTopLevel("run");
            ProtosStandaloneHostedSession.PreparedTopLevel observedRun =
                    observed.prepareTopLevel("run");
            for (int index = 0; index < 3; index++) {
                assertEquals(1, plainRun.executable().execute().asInt());
                assertEquals(1, observedRun.executable().execute().asInt());
                assertB5ZeroArgumentHeader(
                        plainRun.hostExecutableForTesting(),
                        arguments(plain, plainRun.hostExecutableForTesting()));
                assertB5ZeroArgumentHeader(
                        observedRun.hostExecutableForTesting(),
                        arguments(observed, observedRun.hostExecutableForTesting()));
            }
            assertFalse(plain.runtimeHostForTesting().isDebugMode());
            assertFalse(observed.runtimeHostForTesting().isDebugMode());
            assertNoTask(plain);
            assertNoTask(observed);
        }
        System.out.println("PLAT067_HOST_ISOLATION=PASS");
    }

    /** D208-A: the standard embedding has no execution-mode option; {@code protos.Debug} is gone. */
    @Test
    void standardEmbeddingHasNoExecutionModeOption() {
        String core = CORE.toAbsolutePath().toString();
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    try (Context retired =
                            Context.newBuilder(ProtosLanguage.ID)
                                    .option("protos.CoreRoot", core)
                                    .allowExperimentalOptions(true)
                                    .option("protos.Debug", "true")
                                    .build()) {
                        retired.initialize(ProtosLanguage.ID);
                    }
                });
        try (Context context =
                Context.newBuilder(ProtosLanguage.ID)
                        .option("protos.CoreRoot", core)
                        .build()) {
            context.eval(ProtosLanguage.ID, "run: () => {\n    1\n}\n0\n");
            Value run = context.getBindings(ProtosLanguage.ID).getMember("run");
            assertEquals(1, run.execute().asInt());
        }
        System.out.println("D208_NO_EXECUTION_MODE_OPTION=PASS");
    }

    private static String label(boolean debugger) {
        return debugger ? "debugger attached" : "no debugger";
    }

    private ProtosStandaloneHostedSession open(String name) throws Exception {
        return open(name, SOURCE);
    }

    private ProtosStandaloneHostedSession open(String name, String source) throws Exception {
        Path file = sourceFile(name);
        Files.writeString(file, source, StandardCharsets.UTF_8);
        ProtosStandaloneHostedSession session = ProtosStandaloneHostedSession.open(CORE, file);
        assertEquals(ProtosExecutionOutcome.State.COMPLETED, session.initialOutcome().state());
        return session;
    }

    private static Debugger debugger(ProtosStandaloneHostedSession session) {
        Instrument instrument =
                session.runtimeHostForTesting().engineForTesting().getInstruments().get("debugger");
        assertNotNull(instrument);
        Debugger debugger = instrument.lookup(Debugger.class);
        assertNotNull(debugger);
        return debugger;
    }

    /** PLAT067-B: an in-process debugger session with no breakpoint; it only observes. */
    private static DebuggerSession attach(ProtosStandaloneHostedSession session) {
        return debugger(session).startSession(event -> {});
    }

    private Path sourceFile(String name) {
        return directory.resolve("perf032-" + name + ".protos");
    }

    private DebuggerSession startSession(
            ProtosStandaloneHostedSession session, String name, ScopeObservation observation) {
        DebuggerSession debugSession = debugger(session).startSession(observation::onSuspend);
        debugSession.install(
                Breakpoint.newBuilder(sourceFile(name).toUri()).lineIs(BODY_LINE).build());
        return debugSession;
    }

    private static Object[] arguments(
            ProtosStandaloneHostedSession session, ProtosHostExecutableClosure adapter) {
        return session.processContextForTesting()
                .callForRuntime(() -> prepareNoArguments(adapter).targetArguments());
    }

    /**
     * I102: the zero-argument host entry of {@code adapter}'s unobservable-home Closure, identical
     * with and without a debugger: the Closure, no explicit Task, the adapter's exact caller and
     * the unobservable marker, with no supplied value.
     */
    private static void assertB5ZeroArgumentHeader(
            ProtosHostExecutableClosure adapter, Object[] arguments) {
        assertEquals(ProtosFrameArguments.HEADER_LENGTH, arguments.length);
        assertSame(adapter.closure, arguments[0]);
        assertNull(arguments[2], "no explicit Task");
        assertSame(adapter.caller, arguments[3]);
        assertSame(ProtosReturnHome.unobservable(), arguments[4]);
        assertEquals(0, ProtosFrameArguments.compactSuppliedArgumentCount(arguments));
    }

    private static ProtosPreparedCalls.OrdinarySourceCall prepareNoArguments(
            ProtosHostExecutableClosure adapter) {
        try {
            return adapter.prepare(new Object[0], null);
        } catch (ArityException unexpected) {
            throw new AssertionError(unexpected);
        }
    }

    private static void assertNoTask(ProtosStandaloneHostedSession session) {
        assertEquals(
                0,
                session.processForTesting()
                        .rootActorForRuntime()
                        .executionDomain()
                        .liveTaskCount(),
                "no RootTask or ProtosTask may be created");
    }

    /** Records one breakpoint suspension and the captured lexical binding it observed. */
    private static final class ScopeObservation {
        private final AtomicInteger suspensions = new AtomicInteger();
        private final AtomicReference<String> marker = new AtomicReference<>();
        private final AtomicReference<Throwable> failure = new AtomicReference<>();

        void onSuspend(com.oracle.truffle.api.debug.SuspendedEvent event) {
            suspensions.incrementAndGet();
            try {
                assertEquals(BODY_LINE, event.getSourceSection().getStartLine());
                DebugScope scope = event.getTopStackFrame().getScope();
                assertNotNull(scope);
                DebugValue value = scope.getDeclaredValue("marker");
                assertNotNull(value, "captured lexical binding must be visible");
                marker.set(value.toDisplayString());
            } catch (Throwable unexpected) {
                failure.compareAndSet(null, unexpected);
            } finally {
                event.prepareContinue();
            }
        }

        void assertObserved(String expectedMarker) {
            if (failure.get() != null) {
                throw new AssertionError("debugger callback failed", failure.get());
            }
            assertEquals(1, suspensions.get());
            assertEquals(expectedMarker, marker.get());
        }
    }
}
