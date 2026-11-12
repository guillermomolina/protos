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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.guillermomolina.protos.parser.ProtosParser;
import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.guillermomolina.protos.semantic.Canonicalizer;
import com.guillermomolina.protos.semantic.ast.CanonicalSequence;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage.LanguageReference;
import com.oracle.truffle.api.bytecode.BytecodeNode;
import com.oracle.truffle.api.bytecode.TagTree;
import com.oracle.truffle.api.debug.Breakpoint;
import com.oracle.truffle.api.debug.DebugScope;
import com.oracle.truffle.api.debug.DebugValue;
import com.oracle.truffle.api.debug.Debugger;
import com.oracle.truffle.api.debug.DebuggerSession;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Instrument;
import org.junit.jupiter.api.Test;

/**
 * PERF026-B1 (PLAT044 B′) focal evidence: an eligible standard {@code ifTrue}
 * immediate literal callback runs inline in its semantic source root under its
 * own fresh activation, with a custom RootTag and activation-projecting tooling
 * scope; every non-eligible shape keeps its exact physical callback root.
 *
 * <p>Eligible sites are placed inside a Closure ({@code run}) because a literal
 * at module top level owns a fresh return home, a shape B1 keeps on the physical
 * path.
 */
final class ProtosPerf026B1BooleanInlineCallbackTest {
    private static final Path CORE = Path.of("protos", "lib", "core");

    private static final LanguageReference<ProtosLanguage> LANGUAGE_REF =
            LanguageReference.create(ProtosLanguage.class);

    @Test
    void eligibleIfTrueLiteralCallbackRunsInlineInItsSourceRoot() throws Exception {
        try (Context context = Context.newBuilder(ProtosLanguage.ID).build()) {
            context.initialize(ProtosLanguage.ID);
            context.enter();
            try {
                Probe probe = new Probe();
                ProtosBytecodeRootNode root =
                        lowerRoot(
                                """
                                run: () => {
                                    true.ifTrue(() => {
                                        probe()
                                        1
                                    })
                                }
                                run()
                                """);

                assertInteger(1, root.getCallTarget().call(probe.module()));

                assertEquals(1, probe.calls.get());
                ProtosBytecodeRootNode runRoot =
                        assertProbeCalledFrom(probe.stack, root, "probe");

                runRoot.getRootNodes().ensureComplete();
                assertEquals(
                        2,
                        rootTags(runRoot.getBytecodeNode()),
                        "explicit RootTag of run plus the inline callback RootTag");
            } finally {
                context.leave();
            }
        }
        System.out.println("PERF026_B1_IFTRUE_LITERAL_CALLBACK_ROOT_REMOVED=YES");
        System.out.println("PERF026_B1_IFTRUE_INLINE_ROOTTAG=YES");
    }

    @Test
    void dynamicCallbackRetainsItsPhysicalRoot() throws Exception {
        assertCallbackRootPreserved(
                """
                run: () => {
                    callback: () => {
                        probe()
                        1
                    }
                    true.ifTrue(callback)
                }
                run()
                """);
        System.out.println("PERF026_B1_DYNAMIC_CALLBACK_ROOT_PRESERVED=YES");
    }

    @Test
    void ownedReturnHomeLiteralRetainsItsPhysicalRoot() throws Exception {
        try (Context context = Context.newBuilder(ProtosLanguage.ID).build()) {
            context.initialize(ProtosLanguage.ID);
            context.enter();
            try {
                Probe probe = new Probe();
                ProtosBytecodeRootNode root =
                        lowerRoot(
                                """
                                true.ifTrue(() => {
                                    probe()
                                    1
                                })
                                """);

                assertInteger(1, root.getCallTarget().call(probe.module()));
                assertProbeCalledFrom(probe.stack, root, "probe");
            } finally {
                context.leave();
            }
        }
        System.out.println("PERF026_B1_OWNED_RETURN_HOME_FALLBACK=YES");
    }

    @Test
    void ordinarySelectionAndNonClosureCallbacksStayOrdinary() throws Exception {
        try (Context context = Context.newBuilder(ProtosLanguage.ID).build()) {
            context.initialize(ProtosLanguage.ID);
            context.enter();
            try {
                Probe custom = new Probe();
                ProtosBytecodeRootNode customRoot =
                        lowerRoot(
                                """
                                run: () => {
                                    custom: {
                                        ifTrue: (callback) => {
                                            probe()
                                            5
                                        }
                                    }
                                    custom.ifTrue(() => { 1 })
                                }
                                run()
                                """);
                assertInteger(5, callWithSignalDetail(customRoot, custom.module(), "B1 custom"));
                assertPhysicalInvocationStack(custom.stack, customRoot, "custom selector");

                Probe callable = new Probe();
                ProtosBytecodeRootNode callableRoot =
                        lowerRoot(
                                """
                                run: () => {
                                    callable: {
                                        call: () => {
                                            probe()
                                            7
                                        }
                                    }
                                    true.ifTrue(callable)
                                }
                                run()
                                """);
                assertInteger(7, callWithSignalDetail(callableRoot, callable.module(), "B1 callable"));
                assertPhysicalInvocationStack(callable.stack, callableRoot, "custom callable");
            } finally {
                context.leave();
            }
        }
        System.out.println("PERF026_B1_CUSTOM_IFTRUE_ORDINARY_DISPATCH_PRESERVED=YES");
        System.out.println("PERF026_B1_NONCLOSURE_INVOKABLE_FALLBACK_PRESERVED=YES");
    }

    @Test
    void falseIfTrueEntersNoInlineCallback() throws Exception {
        try (Context context = Context.newBuilder(ProtosLanguage.ID).build()) {
            context.initialize(ProtosLanguage.ID);
            context.enter();
            try {
                Probe probe = new Probe();
                ProtosBytecodeRootNode root =
                        lowerRoot(
                                """
                                run: () => {
                                    false.ifTrue(() => {
                                        probe()
                                        1
                                    })
                                }
                                run()
                                """);

                assertSame(ProtosNullValue.INSTANCE, root.getCallTarget().call(probe.module()));
                assertEquals(0, probe.calls.get());
            } finally {
                context.leave();
            }
        }
        System.out.println("PERF026_B1_FALSE_IFTRUE_CALLBACK_ENTERED=NO");
    }

    @Test
    void inlineCallbackKeepsReturnHomeCaptureAndFreshActivation() throws Exception {
        try (Context context = Context.newBuilder(ProtosLanguage.ID).build()) {
            context.initialize(ProtosLanguage.ID);
            context.enter();
            try {
                Probe nonLocalReturn = new Probe();
                assertInteger(
                        42,
                        lowerRoot(
                                        """
                                        run: () => {
                                            true.ifTrue(() => {
                                                probe()
                                                ^41
                                            })
                                            100
                                        }
                                        run() + 1
                                        """)
                                .getCallTarget()
                                .call(nonLocalReturn.module()));
                assertProbeCalledInline(nonLocalReturn.stack, "nonLocalReturn");

                Probe capture = new Probe();
                assertInteger(
                        42,
                        lowerRoot(
                                        """
                                        run: () => {
                                            count: 1
                                            true.ifTrue(() => {
                                                probe()
                                                count = count + 40
                                            })
                                            count + 1
                                        }
                                        run()
                                        """)
                                .getCallTarget()
                                .call(capture.module()));
                assertProbeCalledInline(capture.stack, "capture");

                Probe activation = new Probe();
                assertSame(
                        ProtosBooleanValue.TRUE,
                        lowerRoot(
                                        """
                                        run: () => {
                                            outer: context
                                            first: true.ifTrue(() => {
                                                probe()
                                                local: 1
                                                context
                                            })
                                            second: true.ifTrue(() => { context })
                                            (outer !== first) && (first !== second)
                                        }
                                        run()
                                        """)
                                .getCallTarget()
                                .call(activation.module()));
                assertProbeCalledInline(activation.stack, "activation");
            } finally {
                context.leave();
            }
        }
        System.out.println("PERF026_B1_NLR_PRESERVED=YES");
        System.out.println("PERF026_B1_CAPTURE_BY_REFERENCE_PRESERVED=YES");
        System.out.println("PERF026_B1_IFTRUE_LITERAL_FRESH_ACTIVATION_PRESERVED=YES");
    }

    @Test
    void errorAndSuspensionCrossTheInlineCallback() throws Exception {
        ProtosExecutionOutcome error =
                ProtosTestExecutionSupport.execute(
                        "perf026-b1-error.protos",
                        """
                        Error.handle(() => {
                            true.ifTrue(() => { Error().signal() })
                            0
                        }, (caught) => { 42 })
                        """,
                        new ProtosCoreBootstrap().bootstrap(CORE).newModuleActivation());
        assertCompletedWith(42, error);

        ProtosExecutionOutcome suspension =
                ProtosTestExecutionSupport.execute(
                        "perf026-b1-suspension.protos",
                        """
                        run: () => {
                            pending: (() => { 41 }).future()
                            true.ifTrue(() => { pending.value() + 1 })
                        }
                        run()
                        """,
                        new ProtosCoreBootstrap().bootstrap(CORE).newModuleActivation());
        assertCompletedWith(42, suspension);

        System.out.println("PERF026_B1_ERROR_PROPAGATION_PRESERVED=YES");
        System.out.println("PERF026_B1_FUTURE_WAIT_PRESERVED=YES");
    }

    /**
     * The approved PLAT044 tooling delta: no distinct callback debugger frame,
     * but a breakpoint inside the inline region suspends once and its scope is
     * the callback activation (its own binding plus captured ones).
     */
    @Test
    void debuggerScopeInsideInlineCallbackProjectsTheCallbackActivation() throws Exception {
        Source source =
                Source.newBuilder(
                                ProtosLanguage.ID,
                                """
                                run: () => {
                                    outerMarker: 1
                                    true.ifTrue(() => {
                                        innerMarker: 2
                                        innerMarker + outerMarker
                                    })
                                }
                                run()
                                """,
                                "perf026-b1-debugger.protos")
                        .uri(URI.create("memory:///perf026-b1-debugger.protos"))
                        .mimeType(ProtosLanguage.MIME_TYPE)
                        .build();
        ProtosActivation module = new ProtosCoreBootstrap().bootstrap(CORE).newModuleActivation();
        AtomicReference<Throwable> callbackFailure = new AtomicReference<>();
        AtomicReference<String> inner = new AtomicReference<>();
        AtomicReference<String> outer = new AtomicReference<>();
        AtomicInteger suspensions = new AtomicInteger();

        try (ProtosPolyglotExecutionContext polyglot =
                ProtosPolyglotExecutionContext.open(
                        InputStream.nullInputStream(),
                        OutputStream.nullOutputStream(),
                        OutputStream.nullOutputStream())) {
            Instrument instrument =
                    polyglot.engineForTesting().getInstruments().get("debugger");
            assertNotNull(instrument);
            Debugger debugger = instrument.lookup(Debugger.class);
            try (DebuggerSession session =
                    debugger.startSession(
                            event -> {
                                try {
                                    suspensions.incrementAndGet();
                                    DebugScope scope = event.getTopStackFrame().getScope();
                                    DebugValue innerValue = scope.getDeclaredValue("innerMarker");
                                    DebugValue outerValue = scope.getDeclaredValue("outerMarker");
                                    assertNotNull(innerValue, "callback-owned binding must be visible");
                                    assertNotNull(outerValue, "captured binding must be visible");
                                    inner.set(innerValue.toDisplayString());
                                    outer.set(outerValue.toDisplayString());
                                } catch (Throwable failure) {
                                    callbackFailure.compareAndSet(null, failure);
                                } finally {
                                    event.prepareContinue();
                                }
                            })) {
                session.install(Breakpoint.newBuilder(source).lineIs(5).build());
                assertCompletedWith(3, polyglot.execute(source, module));
            }
        }

        if (callbackFailure.get() != null) {
            throw new AssertionError("debugger callback failed", callbackFailure.get());
        }
        assertEquals(1, suspensions.get(), "exactly one inline-region breakpoint suspension");
        assertEquals("2", inner.get());
        assertEquals("1", outer.get());
        System.out.println("PERF026_B1_IFTRUE_INLINE_SCOPE_PROJECTION=YES");
    }

    /**
     * The native probe frame, the guest root that executed the probe and the
     * module must occupy distinct physical frames in that order: an inline
     * callback adds no root of its own.
     */
    private static ProtosBytecodeRootNode assertProbeCalledFrom(
            List<RootNode> stack,
            ProtosBytecodeRootNode moduleRoot,
            String scenario) {
        assertEquals(3, stack.size(), () -> scenario + " stack=" + stack);
        assertEquals(
                "ProtosBuiltinCallRootNode",
                stack.get(0).getClass().getSimpleName(),
                scenario);
        ProtosBytecodeRootNode caller =
                assertInstanceOf(ProtosBytecodeRootNode.class, stack.get(1));
        assertNotSame(moduleRoot, caller, scenario);
        assertSame(moduleRoot, stack.get(2), scenario);
        return caller;
    }

    /** As {@link #assertProbeCalledFrom}, for a test without its module root. */
    private static void assertProbeCalledInline(List<RootNode> stack, String scenario) {
        assertEquals(3, stack.size(), () -> scenario + " stack=" + stack);
        assertEquals(
                "ProtosBuiltinCallRootNode",
                stack.get(0).getClass().getSimpleName(),
                scenario);
        assertNotSame(
                assertInstanceOf(ProtosBytecodeRootNode.class, stack.get(1)),
                assertInstanceOf(ProtosBytecodeRootNode.class, stack.get(2)),
                scenario);
    }

    /**
     * The native probe, selected guest callee, enclosing Closure and module
     * must occupy distinct physical frames in that order.
     */
    private static ProtosBytecodeRootNode assertPhysicalInvocationStack(
            List<RootNode> stack,
            ProtosBytecodeRootNode moduleRoot,
            String scenario) {
        assertEquals(4, stack.size(), () -> scenario + " stack=" + stack);
        assertEquals(
                "ProtosBuiltinCallRootNode",
                stack.get(0).getClass().getSimpleName(),
                scenario);

        ProtosBytecodeRootNode calleeRoot =
                assertInstanceOf(ProtosBytecodeRootNode.class, stack.get(1));
        ProtosBytecodeRootNode enclosingRoot =
                assertInstanceOf(ProtosBytecodeRootNode.class, stack.get(2));

        assertNotSame(calleeRoot, enclosingRoot, scenario);
        assertNotSame(calleeRoot, moduleRoot, scenario);
        assertNotSame(enclosingRoot, moduleRoot, scenario);
        assertSame(moduleRoot, stack.get(3), scenario);

        return calleeRoot;
    }

    private static void assertCallbackRootPreserved(String characters) throws Exception {
        try (Context context = Context.newBuilder(ProtosLanguage.ID).build()) {
            context.initialize(ProtosLanguage.ID);
            context.enter();
            try {
                Probe probe = new Probe();
                ProtosBytecodeRootNode root = lowerRoot(characters);

                callWithSignalDetail(root, probe.module(), "B1 dynamic");

                assertEquals(1, probe.calls.get(), characters);
                ProtosBytecodeRootNode callback =
                        assertPhysicalInvocationStack(
                                probe.stack, root, characters);

                callback.getRootNodes().ensureComplete();
                assertEquals(1, rootTags(callback.getBytecodeNode()), characters);
            } finally {
                context.leave();
            }
        }
    }

    /** A native {@code probe} binding recording the guest CallTarget stack. */

    /**
     * Diagnostic-only view of a guest Error transfer.
     * Preserves the original exception and execution path.
     */
    private static Object callWithSignalDetail(
            ProtosBytecodeRootNode root,
            ProtosActivation module,
            String scenario) {
        try {
            return root.getCallTarget().call(module);
        } catch (com.guillermomolina.protos.runtime.ProtosSignalException signal) {
            Object parent = signal.error().parent().orElse(null);
            var prelude = module.prelude().orElseThrow();

            String category = "UNCLASSIFIED";
            for (var standard :
                    com.guillermomolina.protos.runtime.ProtosCoreErrors
                            .StandardError.values()) {
                Object prototype = prelude.bindings()
                        .readLocalSlot(standard.prototypeName())
                        .orElse(null);
                if (parent == prototype) {
                    category = standard.prototypeName();
                    break;
                }
            }

            StringBuilder ancestry = new StringBuilder();
            Object current = parent;
            for (int depth = 0; depth < 4 && current != null; depth++) {
                if (!(current instanceof
                        com.guillermomolina.protos.runtime.ProtosObjectValue object)) {
                    ancestry.append(current.getClass().getSimpleName());
                    break;
                }
                ancestry.append(depth == 0 ? "" : " -> ")
                        .append(object.getClass().getSimpleName())
                        .append(object.localSlotsSnapshot().keySet());
                current = object.parent().orElse(null);
            }

            int bci = signal.originBytecodeIndexForRuntime();
            var bytecode = signal.originBytecodeForRuntime();
            StringBuilder operations = new StringBuilder();

            if (bytecode != null && bci >= 0) {
                var instructions = bytecode.getInstructionsAsList();
                int selected = -1;

                for (int i = 0; i < instructions.size(); i++) {
                    if (instructions.get(i).getBytecodeIndex() <= bci) {
                        selected = i;
                    } else {
                        break;
                    }
                }

                if (selected >= 0) {
                    int first = Math.max(0, selected - 3);
                    int last = Math.min(
                            instructions.size() - 1, selected + 3);

                    for (int i = first; i <= last; i++) {
                        var instruction = instructions.get(i);
                        operations.append(i == selected ? " [ORIGIN] " : " ")
                                .append(instruction.getBytecodeIndex())
                                .append(":")
                                .append(instruction.getName());
                    }
                }
            }

            System.err.println(
                    "B57_SIGNAL=" + scenario
                    + " CATEGORY=" + category
                    + " BCI=" + bci
                    + " PARENT_CHAIN=" + ancestry);

            System.err.println(
                    "B57_BYTECODE=" + scenario
                    + " OPERATIONS=" + operations);

            throw signal;
        }
    }

    private static final class Probe {
        final List<RootNode> stack = new ArrayList<>();
        final AtomicInteger calls = new AtomicInteger();

        ProtosActivation module() throws Exception {
            ProtosActivation module =
                    new ProtosCoreBootstrap().bootstrap(CORE).newModuleActivation();
            module.context().createLocalSlot(
                    "probe",
                    ProtosClosureValue.nativeClosure(
                            (activation, supplied) -> {
                                calls.incrementAndGet();
                                stack.clear();
                                Truffle.getRuntime()
                                        .iterateFrames(
                                                frame -> {
                                                    stack.add(
                                                            ((RootCallTarget) frame.getCallTarget())
                                                                    .getRootNode());
                                                    return null;
                                                });
                                return ProtosNullValue.INSTANCE;
                            }));
            return module;
        }
    }

    private static ProtosBytecodeRootNode lowerRoot(String characters) {
        Source source =
                Source.newBuilder(ProtosLanguage.ID, characters, "perf026-b1.protos").build();
        CanonicalSequence sequence =
                (CanonicalSequence)
                        new Canonicalizer()
                                .canonicalize(new ProtosParser(characters).parseProgram());
        return new CanonicalToBytecodeLowerer(LANGUAGE_REF.get(null), source)
                .lowerRoot(sequence);
    }

    private static void assertCompletedWith(long expected, ProtosExecutionOutcome outcome) {
        assertEquals(
                ProtosExecutionOutcome.State.COMPLETED,
                outcome.state(),
                () -> "outcome=" + outcome.state() + ", error=" + outcome.error());
        assertInteger(expected, outcome.value());
    }

    private static void assertInteger(long expected, Object value) {
        assertEquals(Long.toString(expected), com.guillermomolina.protos.runtime.ProtosNumericValueSupport.integerDecimalText(value));
    }

    private static int rootTags(BytecodeNode bytecodeNode) {
        return countRootTags(bytecodeNode.getTagTree());
    }

    private static int countRootTags(TagTree tree) {
        if (tree == null) {
            return 0;
        }
        int count = tree.hasTag(StandardTags.RootTag.class) ? 1 : 0;
        for (TagTree child : tree.getTreeChildren()) {
            count += countRootTags(child);
        }
        return count;
    }
}
