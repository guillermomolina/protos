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
 * PERF026-B3 (PLAT044 B′) focal evidence: standard {@code ifTrueIfFalse} with
 * two eagerly evaluated callback arguments runs the callback selected by the
 * ordinary prepared Boolean state machine inline when that selected value is
 * exactly the staged literal of its own position; the unselected callback is
 * never entered, and every non-eligible shape keeps its exact physical
 * callback root.
 *
 * <p>Eligible sites are placed inside a Closure ({@code run}) because a literal
 * at module top level owns a fresh return home, a shape B′ keeps on the
 * physical path.
 */
final class ProtosPerf026B3BooleanInlineCallbackTest {
    private static final Path CORE = Path.of("protos", "lib", "core");

    private static final LanguageReference<ProtosLanguage> LANGUAGE_REF =
            LanguageReference.create(ProtosLanguage.class);

    @Test
    void trueReceiverRunsTheSelectedTrueLiteralInline() throws Exception {
        Object result =
                assertCallbackRootRemoved(
                        """
                        run: () => {
                            true.ifTrueIfFalse(() => {
                                probe()
                                1
                            }, () => {
                                probe()
                                2
                            })
                        }
                        run()
                        """,
                        // run and the selected inline callback: a literal receiver
                        // selects statically, so the unselected literal is not emitted.
                        2);
        assertInteger(1, result);
        System.out.println("PERF026_B3_TRUE_BRANCH_LITERAL_CALLBACK_ROOT_REMOVED=YES");
        System.out.println("PERF026_B3_UNSELECTED_FALSE_CALLBACK_ENTERED=NO");
    }

    @Test
    void falseReceiverRunsTheSelectedFalseLiteralInline() throws Exception {
        Object result =
                assertCallbackRootRemoved(
                        """
                        run: () => {
                            false.ifTrueIfFalse(() => {
                                probe()
                                1
                            }, () => {
                                probe()
                                2
                            })
                        }
                        run()
                        """,
                        // run and the selected inline callback: a literal receiver
                        // selects statically, so the unselected literal is not emitted.
                        2);
        assertInteger(2, result);
        System.out.println("PERF026_B3_FALSE_BRANCH_LITERAL_CALLBACK_ROOT_REMOVED=YES");
        System.out.println("PERF026_B3_UNSELECTED_TRUE_CALLBACK_ENTERED=NO");
        System.out.println("PERF026_B3_INLINE_ROOTTAG=YES");
    }

    /**
     * A single eligible position is admitted only when it is the selected one;
     * the other, non-literal argument is still evaluated eagerly and in source
     * order after the receiver, even when it is not selected.
     */
    @Test
    void mixedSitesKeepEagerOrderedEvaluationAndAdmitOnlyTheSelectedLiteral()
            throws Exception {
        Object falseSelected =
                assertCallbackRootRemoved(
                        """
                        run: () => {
                            log: 0
                            receiver: () => {
                                log = log * 10 + 1
                                false
                            }
                            record: (n) => {
                                log = log * 10 + n
                                () => { 0 }
                            }
                            receiver().ifTrueIfFalse(record(2), () => {
                                probe()
                                log * 10 + 3
                            })
                        }
                        run()
                        """,
                        // run, the B3 callback, and the I096 inline bodies of
                        // receiver() and record(2).
                        4);
        assertInteger(123, falseSelected);

        Object trueSelected =
                assertCallbackRootRemoved(
                        """
                        run: () => {
                            log: 0
                            receiver: () => {
                                log = log * 10 + 1
                                true
                            }
                            record: (n) => {
                                log = log * 10 + n
                                () => { 0 }
                            }
                            receiver().ifTrueIfFalse(() => {
                                probe()
                                log * 10 + 3
                            }, record(2))
                        }
                        run()
                        """,
                        // run, the B3 callback, and the I096 inline bodies of
                        // receiver() and record(2).
                        4);
        assertInteger(123, trueSelected);
        System.out.println("PERF026_B3_EAGER_ORDERED_ARGUMENT_EVALUATION_PRESERVED=YES");
    }

    @Test
    void dynamicSelectedCallbackRetainsItsPhysicalRoot() throws Exception {
        assertCallbackRootPreserved(
                """
                run: () => {
                    callback: () => {
                        probe()
                        1
                    }
                    true.ifTrueIfFalse(callback, () => { 2 })
                }
                run()
                """);
        assertCallbackRootPreserved(
                """
                run: () => {
                    callback: () => {
                        probe()
                        2
                    }
                    false.ifTrueIfFalse(() => { 1 }, callback)
                }
                run()
                """);
        System.out.println("PERF026_B3_DYNAMIC_CALLBACK_ROOT_PRESERVED=YES");
        System.out.println("PERF026_B3_MISMATCHED_POSITION_FALLBACK_PRESERVED=YES");
    }

    @Test
    void nonEligibleLiteralsRetainTheirPhysicalRoot() throws Exception {
        assertCallbackRootPreserved(
                """
                run: () => {
                    true.ifTrueIfFalse((x: 1) => {
                        probe()
                        x
                    }, () => { 2 })
                }
                run()
                """);
        System.out.println("PERF026_B3_PARAMETERIZED_LITERAL_ROOT_PRESERVED=YES");

        assertCallbackRootPreserved(
                """
                run: () => {
                    false.ifTrueIfFalse(() => { 1 }, () => {
                        probe()
                        nested: () => { 2 }
                        nested.call()
                    })
                }
                run()
                """);
        System.out.println("PERF026_B3_NESTED_CLOSURE_LITERAL_ROOT_PRESERVED=YES");
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
                                false.ifTrueIfFalse(() => { 1 }, () => {
                                    probe()
                                    2
                                })
                                """);

                assertInteger(2, root.getCallTarget().call(probe.module()));
                assertProbeCalledFrom(probe.stack, root, "probe");
            } finally {
                context.leave();
            }
        }
        System.out.println("PERF026_B3_OWNED_RETURN_HOME_FALLBACK=YES");
    }

    @Test
    void customSelectorAndNonClosureCallbacksStayOrdinary() throws Exception {
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
                                        ifTrueIfFalse: (whenTrue, whenFalse) => {
                                            probe()
                                            5
                                        }
                                    }
                                    custom.ifTrueIfFalse(() => { 1 }, () => { 2 })
                                }
                                run()
                                """);
                assertInteger(5, callWithSignalDetail(customRoot, custom.module(), "B3 custom"));
                assertPhysicalInvocationStack(custom.stack, customRoot, "custom selector");

                Probe callable = new Probe();
                ProtosBytecodeRootNode callableRoot =
                        lowerRoot(
                                """
                                run: () => {
                                    callable: {
                                        call: () => {
                                            probe()
                                            2
                                        }
                                    }
                                    false.ifTrueIfFalse(() => { 1 }, callable)
                                }
                                run()
                                """);
                assertInteger(2, callWithSignalDetail(callableRoot, callable.module(), "B3 callable"));
                assertPhysicalInvocationStack(callable.stack, callableRoot, "custom callable");
            } finally {
                context.leave();
            }
        }
        System.out.println("PERF026_B3_CUSTOM_BOOLEAN_SELECTOR_FALLBACK_PRESERVED=YES");
        System.out.println("PERF026_B3_NONCLOSURE_INVOKABLE_FALLBACK_PRESERVED=YES");
    }

    @Test
    void dynamicConditionalPreservesCapturedCellForBothSelections()
            throws Exception {
        for (String receiver : List.of("true", "custom")) {
            try (Context context = Context.newBuilder(ProtosLanguage.ID).build()) {
                context.initialize(ProtosLanguage.ID);
                context.enter();
                try {
                    Probe probe = new Probe();
                    ProtosBytecodeRootNode root =
                            lowerRoot(
                                    """
                                    custom: {
                                        ifTrueIfFalse: (whenTrue, whenFalse) => {
                                            whenTrue()
                                        }
                                    }
                                    run: (selected) => {
                                        captured: 7
                                        selected.ifTrueIfFalse(() => {
                                            probe()
                                            captured
                                        }, () => { 0 })
                                    }
                                    run(%s)
                                    """
                                            .formatted(receiver));

                    assertInteger(7, root.getCallTarget().call(probe.module()));
                    assertEquals(1, probe.calls.get(), receiver);
                } finally {
                    context.leave();
                }
            }
        }
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
                                            false.ifTrueIfFalse(() => { ^0 }, () => {
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
                                            true.ifTrueIfFalse(() => {
                                                probe()
                                                count = count + 40
                                            }, () => {
                                                count = 0
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
                                            first: false.ifTrueIfFalse(() => { null }, () => {
                                                probe()
                                                context
                                            })
                                            second: true.ifTrueIfFalse(() => { context }, () => { null })
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
        System.out.println("PERF026_B3_NLR_PRESERVED=YES");
        System.out.println("PERF026_B3_CAPTURE_BY_REFERENCE_PRESERVED=YES");
        System.out.println("PERF026_B3_FRESH_ACTIVATION_PRESERVED=YES");
    }

    @Test
    void errorAndSuspensionCrossTheInlineCallback() throws Exception {
        ProtosExecutionOutcome error =
                ProtosTestExecutionSupport.execute(
                        "perf026-b3-error.protos",
                        """
                        Error.handle(() => {
                            false.ifTrueIfFalse(() => { 0 }, () => { Error().signal() })
                            0
                        }, (caught) => { 42 })
                        """,
                        new ProtosCoreBootstrap().bootstrap(CORE).newModuleActivation());
        assertCompletedWith(42, error);

        ProtosExecutionOutcome suspension =
                ProtosTestExecutionSupport.execute(
                        "perf026-b3-suspension.protos",
                        """
                        run: () => {
                            pending: (() => { 41 }).future()
                            true.ifTrueIfFalse(() => {
                                pending.value() + 1
                            }, () => { 0 })
                        }
                        run()
                        """,
                        new ProtosCoreBootstrap().bootstrap(CORE).newModuleActivation());
        assertCompletedWith(42, suspension);

        System.out.println("PERF026_B3_ERROR_PROPAGATION_PRESERVED=YES");
        System.out.println("PERF026_B3_FUTURE_WAIT_PRESERVED=YES");
    }

    /** The generic B1 inline RootTag/scope projection serves the false branch. */
    @Test
    void debuggerScopeInsideInlineFalseBranchProjectsTheCallbackActivation()
            throws Exception {
        Source source =
                Source.newBuilder(
                                ProtosLanguage.ID,
                                """
                                run: () => {
                                    outerMarker: 1
                                    false.ifTrueIfFalse(() => { 0 }, () => {
                                        innerMarker: 2
                                        innerMarker + outerMarker
                                        3
                                    })
                                }
                                run()
                                """,
                                "perf026-b3-debugger.protos")
                        .uri(URI.create("memory:///perf026-b3-debugger.protos"))
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
                ProtosExecutionOutcome outcome = polyglot.execute(source, module);
                assertCompletedWith(3, outcome);
            }
        }

        if (callbackFailure.get() != null) {
            throw new AssertionError("debugger callback failed", callbackFailure.get());
        }
        assertEquals(1, suspensions.get(), "exactly one inline-region breakpoint suspension");
        assertEquals("2", inner.get());
        assertEquals("1", outer.get());
        System.out.println("PERF026_B3_INLINE_SCOPE_PROJECTION_PRESERVED=YES");
    }

    /**
     * Requires exactly one probe call made directly from {@code run}'s own
     * root (no callback root on the stack) and {@code expectedRootTags}
     * RootTags in {@code run}: its explicit one plus one per inline region.
     */
    private static Object assertCallbackRootRemoved(String characters, int expectedRootTags)
            throws Exception {
        try (Context context = Context.newBuilder(ProtosLanguage.ID).build()) {
            context.initialize(ProtosLanguage.ID);
            context.enter();
            try {
                Probe probe = new Probe();
                ProtosBytecodeRootNode root = lowerRoot(characters);

                Object result = root.getCallTarget().call(probe.module());

                assertEquals(1, probe.calls.get(), characters);
                ProtosBytecodeRootNode runRoot =
                        assertProbeCalledFrom(probe.stack, root, characters);

                runRoot.getRootNodes().ensureComplete();
                assertEquals(expectedRootTags, rootTags(runRoot.getBytecodeNode()), characters);
                return result;
            } finally {
                context.leave();
            }
        }
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

                callWithSignalDetail(root, probe.module(), "B3 dynamic");

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
                Source.newBuilder(ProtosLanguage.ID, characters, "perf026-b3.protos").build();
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
