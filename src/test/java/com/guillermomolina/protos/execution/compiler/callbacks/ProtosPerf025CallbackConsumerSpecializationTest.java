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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;


import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosLexicalCell;
import com.oracle.truffle.api.bytecode.BytecodeNode;
import com.oracle.truffle.api.bytecode.Instruction;
import com.oracle.truffle.api.bytecode.TagTreeNode;
import com.oracle.truffle.api.debug.Breakpoint;
import com.oracle.truffle.api.debug.DebugStackFrame;
import com.oracle.truffle.api.debug.Debugger;
import com.oracle.truffle.api.debug.DebuggerSession;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.source.Source;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.graalvm.polyglot.Instrument;
import org.junit.jupiter.api.Test;

/**
 * PERF025 slice 3 focal evidence: the common consumers of a frame-native
 * PLAT044 B′ inline callback (statically resolved captured reads and writes,
 * canonical Integer sends, direct source-Closure calls, {@code this}, member
 * reads and multiple creation) run their successful path without
 * materializing the callback activation, while observers, fallbacks and
 * Errors keep their exact semantics.
 *
 * <p>Runtime state is read at a breakpoint from the raw Bytecode frame, never
 * through a debugger scope, which would itself be an observer: the live
 * carrier reports whether the activation was materialized, LOCAL bindings
 * are read from their Bytecode DSL locals and CELL bindings through the
 * cells those locals hold (I115).
 */
final class ProtosPerf025CallbackConsumerSpecializationTest {
    private static final Path CORE = Path.of("protos", "lib", "core");

    @Test
    void capturedReadAndCanonicalIntegerSendStayLazy() throws Exception {
        Run run =
                runToLine(
                        """
                        run: () => {
                            limit: 3
                            Array(1).each((element) => {
                                result: element + limit
                                result
                            })
                        }
                        run()
                        """,
                        5);
        run.assertCompleted();
        Stop stop = run.single();
        stop.assertLazy();
        assertInteger(4, stop.binding("result"));
        // I115: the captured limit is read through its cell, never by name,
        // and the sum is the per-selector NumberAdd operation.
        stop.assertInstruction(stop.inlined() ? "load.local" : "LoadCell");
        stop.assertNoInstruction("AccessNamedLexical");
        stop.assertInstruction("NumberAdd");
        stop.assertNoRetired();
        System.out.println("PERF025_S3_CAPTURED_READ_WITHOUT_ACTIVATION=YES");
        System.out.println("PERF025_S3_CANONICAL_INTEGER_SEND_WITHOUT_ACTIVATION=YES");
    }

    @Test
    void capturedAccumulatorUpdateStaysLazy() throws Exception {
        Run run =
                runToLine(
                        """
                        run: () => {
                            acc: 0
                            Array(1, 2, 3).each((element) => {
                                acc = acc + element
                                element
                            })
                            acc
                        }
                        run()
                        """,
                        5);
        run.assertCompleted();
        assertInteger(6, run.value());
        assertEquals(3, run.stops.size());
        for (Stop stop : run.stops) {
            stop.assertLazy();
            // I115: the captured accumulator is read and written through its cell.
            stop.assertInstruction(stop.inlined() ? "load.local" : "LoadCell");
            stop.assertInstruction(stop.inlined() ? "store.local" : "StoreCell");
            stop.assertNoInstruction("AccessNamedLexical");
            stop.assertNoRetired();
        }
        for (int index = 0; index < run.stops.size(); index++) {
            assertInteger(index + 1, run.stops.get(index).binding("element"));
        }
        assertDistinctCarriers(run.stops);
        System.out.println("PERF025_S3_CAPTURED_WRITE_WITHOUT_ACTIVATION=YES");
    }

    /**
     * The RHS calls a module binding; the destination selected before that
     * call remains the one written. An inlined callback still needs no own
     * activation merely to invoke the module binding.
     */
    @Test
    void rhsMaterializationKeepsPreselectedCapturedDestination() throws Exception {
        Run run =
                runToLine(
                        """
                        run: () => {
                            acc: 0
                            Array(1, 2).each((element) => {
                                acc = acc + bump(element)
                                element
                            })
                            acc
                        }
                        run()
                        """,
                        5);
        run.assertCompleted();
        assertInteger(30, run.value());
        assertEquals(2, run.stops.size());
        for (int index = 0; index < run.stops.size(); index++) {
            Stop stop = run.stops.get(index);
            if (stop.inlined()) {
                stop.assertLazy();
            }
            // Materialization keeps the frame bindings exact.
            assertInteger(index + 1, stop.binding("element"));
            // The destination is the captured cell, selected and written directly.
            stop.assertInstruction(stop.inlined() ? "store.local" : "StoreCell");
            stop.assertNoRetired();
        }
        System.out.println("PERF028_A_DESTINATION_SELECTED_BEFORE_RHS=YES");
        System.out.println("PERF028_A_NO_RETARGET_AFTER_RHS=YES");
    }

    @Test
    void canonicalIntegerWhileShapeStaysLazy() throws Exception {
        Run run =
                runToLine(
                        """
                        run: () => {
                            n: 0
                            (() => {
                                less: n < 3
                                less
                            }).whileTrue() {
                                n = n + 1
                                n
                            }
                            n
                        }
                        run()
                        """,
                        5,
                        8);
        run.assertCompleted();
        assertInteger(3, run.value());
        assertEquals(7, run.stops.size(), "four condition and three body stops");
        for (Stop stop : run.stops) {
            stop.assertLazy();
        }
        assertDistinctCarriers(run.stops);
        System.out.println("PERF025_S3_WHILE_INTEGER_OPERATIONS_WITHOUT_ACTIVATION=YES");
    }

    @Test
    void directSourceClosureCallStaysLazy() throws Exception {
        Run run =
                runToLine(
                        """
                        run: () => {
                            next: (value) => { value + 1 }
                            Array(1).each((element) => {
                                result: next(element)
                                result
                            })
                        }
                        run()
                        """,
                        5);
        run.assertCompleted();
        Stop stop = run.single();
        stop.assertLazy();
        assertInteger(2, stop.binding("result"));
        // I115: the callee is the captured next, loaded from its cell.
        stop.assertInstruction(stop.inlined() ? "load.local" : "LoadCell");
        stop.assertNoInstruction("AccessNamedLexical");
        stop.assertNoRetired();
        System.out.println("PERF025_S3_DIRECT_SOURCE_CLOSURE_CALL_WITHOUT_ACTIVATION=YES");
    }

    /** A non-Closure receiver selects its own {@code call}: the exact generic path. */
    @Test
    void nonCanonicalInvocationKeepsGenericPath() throws Exception {
        Run run =
                runToLine(
                        """
                        run: () => {
                            target: { call: (value) => { value * 10 } }
                            Array(4).each((element) => {
                                result: target(element)
                                result
                            })
                        }
                        run()
                        """,
                        5);
        run.assertCompleted();
        Stop stop = run.single();
        stop.assertAnyInstruction("CallUnaryMethod", "CallBinaryMethod", "CallRequest");
        assertInteger(40, stop.binding("result"));
        assertInteger(4, stop.binding("element"));
        stop.assertNoRetired();
        System.out.println("PERF025_S3_NONCANONICAL_CALL_FALLBACK_PRESERVED=YES");
    }

    @Test
    void thisStaysLazy() throws Exception {
        Run run =
                runToLine(
                        """
                        run: () => {
                            me: this
                            Array(1).each((element) => {
                                same: this === me
                                same
                            })
                        }
                        run()
                        """,
                        5);
        run.assertCompleted();
        Stop stop = run.single();
        stop.assertLazy();
        assertSame(Boolean.TRUE, stop.binding("same"));
        // I115: the captured me is read through its cell; this needs no activation.
        stop.assertInstruction(stop.inlined() ? "load.local" : "LoadCell");
        assertEquals(stop.inlined() ? 2L : 1L, stop.instructions().stream()
                .filter(name -> name.contains("LoadCurrentReceiver")).count());
        if (!stop.inlined()) {
            stop.assertNoInstruction("CurrentActivation");
        }
        stop.assertNoRetired();
        System.out.println("PERF025_S3_THIS_WITHOUT_ACTIVATION=YES");
    }

    @Test
    void receiverHeaderPreservesMethodExtractionAndNestedCapture() throws Exception {
        assertGuestTrue("""
                moduleReceiver: this
                direct: () => this === moduleReceiver
                parent: {
                    self: () => this
                    published: () => { here: context
                        this }
                    make: () => { saved: this
                        () => this === saved }
                }
                child: parent {}
                extracted: child.self
                nested: child.make()
                (child.self() === child) && (child.published() === child) &&
                    (extracted() === child) && nested() && direct()
                """);
    }

    /** The context intrinsic observes the callback even when its body is inlined. */
    @Test
    void contextRemainsAnObserver() throws Exception {
        Run run =
                runToLine(
                        """
                        run: () => {
                            me: this
                            Array(1).each((element) => {
                                current: context
                                same: this === me
                                same
                            })
                        }
                        run()
                        """,
                        6);
        run.assertCompleted();
        Stop stop = run.single();
        assertTrue(stop.materialized(), "context observes the callback activation");
        // The context intrinsic is what obtains the exact activation.
        stop.assertInstruction(stop.inlined()
                ? "PublishInlineCallbackContext" : "PublishCurrentClosureContext");
        assertSame(Boolean.TRUE, stop.binding("same"));
        stop.assertNoRetired();
        System.out.println("PERF025_S3_CONTEXT_MATERIALIZES=YES");
    }

    @Test
    void memberReadStaysLazy() throws Exception {
        Run run =
                runToLine(
                        """
                        run: () => {
                            box: { value: 7 }
                            Array(1).each((element) => {
                                read: box.value
                                read
                            })
                        }
                        run()
                        """,
                        5);
        run.assertCompleted();
        Stop stop = run.single();
        stop.assertLazy();
        assertInteger(7, stop.binding("read"));
        // I115: the root-level member read (ReadMemberAtRoot or its
        // GetAttribute form) of the captured box.
        stop.assertAnyInstruction("ReadMemberAtRoot", "GetAttribute");
        stop.assertInstruction(stop.inlined() ? "load.local" : "LoadCell");
        stop.assertNoRetired();
        System.out.println("PERF025_S3_MEMBER_READ_WITHOUT_ACTIVATION=YES");
    }

    @Test
    void memberLookupFailureKeepsSlotNotFound() throws Exception {
        assertGuestTrue(
                """
                run: () => {
                    box: { value: 7 }
                    Error.handle(() => {
                        Array(1).each((element) => {
                            read: box.missing
                            read
                        })
                    }, (caught) => caught.parent() === SlotNotFound)
                }
                run()
                """);
        System.out.println("PERF025_S3_MEMBER_LOOKUP_ERROR_PRESERVED=YES");
    }

    @Test
    void multipleCreationStaysLazy() throws Exception {
        Run run =
                runToLine(
                        """
                        run: () => {
                            pair: Array(4, 5, 6)
                            Array(1).each((element) => {
                                (first, second): pair
                                first
                            })
                        }
                        run()
                        """,
                        5);
        run.assertCompleted();
        Stop stop = run.single();
        stop.assertLazy();
        assertInteger(4, stop.binding("first"));
        assertInteger(5, stop.binding("second"));
        // I115: one prefix observation, then one native store per name.
        stop.assertInstruction("ObserveMultipleCreatePrefix");
        stop.assertInstruction("ObservedMultipleCreateValue");
        stop.assertNoInstruction("CreateCurrentLocalSlot");
        stop.assertNoRetired();
        System.out.println("PERF025_S3_MULTIPLE_CREATE_WITHOUT_ACTIVATION=YES");
    }

    @Test
    void multipleCreationErrorsArePreserved() throws Exception {
        assertGuestTrue(
                """
                run: () => {
                    Error.handle(() => {
                        Array(1).each((element) => {
                            (first, second): element
                            first
                        })
                    }, (caught) => caught.parent() === Error)
                }
                run()
                """);
        assertGuestTrue(
                """
                run: () => {
                    pair: Array(4)
                    Error.handle(() => {
                        Array(1).each((element) => {
                            (first, second): pair
                            first
                        })
                    }, (caught) => caught.parent() === Error)
                }
                run()
                """);
        System.out.println("PERF025_S3_MULTIPLE_CREATE_ERRORS_PRESERVED=YES");
    }

    private static void assertGuestTrue(String characters) throws Exception {
        ProtosExecutionOutcome outcome =
                ProtosTestExecutionSupport.execute(
                        "perf025-s3-error.protos",
                        characters,
                        new ProtosCoreBootstrap().bootstrap(CORE).newModuleActivation());
        assertEquals(
                ProtosExecutionOutcome.State.COMPLETED,
                outcome.state(),
                () -> "outcome=" + outcome.state() + ", error=" + outcome.error());
        assertSame(Boolean.TRUE, outcome.value());
    }

    private static void assertDistinctCarriers(List<Stop> stops) {
        for (int index = 1; index < stops.size(); index++) {
            Stop current = stops.get(index);
            if (current.inlined()) {
                // Fresh binding state replaces an unobservable invocation identity.
                current.assertAnyInstruction(
                        "ResetInlineCallbackLocal", "ArrayEachNext", "AssociationEachNext");
            } else {
                assertTrue(stops.get(index - 1).call() != current.call(),
                        "each physical invocation has its own arguments");
            }
        }
    }

    /*
     * Raw frame slots may retain an I091 primitive carrier; the binding's
     * guest value is what that carrier denotes.
     */
    private static void assertInteger(long expected, Object value) {
        assertEquals(Long.toString(expected), com.guillermomolina.protos.runtime.ProtosNumericValueSupport.integerDecimalText(com.guillermomolina.protos.runtime.ProtosNumericValueSupport
                                        .guestValue(value)));
    }

    /**
     * One breakpoint snapshot. A physical callback has its own arguments;
     * an inlined callback shares its host frame and publishes only its own
     * context cache. Publishing the host is not publishing the callback.
     */
    /* Retired PLAT044 B-prime inline-callback and frame-authority operations. */
    private static final List<String> RETIRED =
            List.of(
                    "LoadInlineClosureArgument",
                    "PrepareInlineSendOne",
                    "ResolveInlineCaptured",
                    "AssignInlineCaptured",
                    "InvokeInlineClosureOne",
                    "LoadInlineCallbackReceiver",
                    "ReadInlineMember",
                    "ObserveInlineMultipleCreatePrefix",
                    "CreateInlineCurrentFrameLocal",
                    "InstallFrameLexicalAuthority",
                    "TryPrimitiveNumeric");

    private record Stop(
            Object call,
            boolean inlined,
            boolean materialized,
            Map<String, Object> bindings,
            List<String> instructions) {
        void assertLazy() {
            assertFalse(materialized, "callback activation must stay unmaterialized");
        }

        /*
         * Only meaningful together with a positive assertion on the current
         * lowering at the same stop: it guards against a mixed lowering.
         */
        void assertNoRetired() {
            for (String retired : RETIRED) {
                assertNoInstruction(retired);
            }
        }

        void assertAnyInstruction(String... operations) {
            assertTrue(
                    instructions.stream().anyMatch(name ->
                            java.util.Arrays.stream(operations).anyMatch(name::contains)),
                    () -> "missing any of " + java.util.Arrays.toString(operations)
                            + " in " + instructions);
        }

        Object binding(String name) {
            assertTrue(bindings.containsKey(name), () -> "missing block local " + name);
            return bindings.get(name);
        }

        void assertInstruction(String operation) {
            assertTrue(
                    instructions.stream().anyMatch(name -> name.contains(operation)),
                    () -> "missing operation " + operation);
        }

        void assertNoInstruction(String operation) {
            assertTrue(
                    instructions.stream().noneMatch(name -> name.contains(operation)),
                    () -> "unexpected operation " + operation + " in " + instructions);
        }
    }

    private record Run(ProtosExecutionOutcome outcome, List<Stop> stops) {
        void assertCompleted() {
            assertEquals(
                    ProtosExecutionOutcome.State.COMPLETED,
                    outcome.state(),
                    () -> "outcome=" + outcome.state() + ", error=" + outcome.error());
        }

        Object value() {
            return outcome.value();
        }

        Stop single() {
            assertEquals(1, stops.size(), "one breakpoint hit");
            return stops.get(0);
        }
    }

    /**
     * Executions of the trailing {@code run()} call per scenario. The Bytecode
     * DSL uncached interpreter (default threshold 16 calls/back-edges per
     * BytecodeNode) only runs each operation's generic specialization, which
     * by design materializes the callback activation; the specialized
     * consumers under test exist only in the cached tier. Earlier runs warm
     * {@code run}'s BytecodeNode past that threshold and only the final run's
     * stops are evaluated.
     */
    private static final int RUNS = 20;

    /**
     * Runs {@code characters}, whose last line must be {@code run()}, with
     * breakpoints on {@code lines}; that call is repeated {@link #RUNS} times
     * and only the final run's hits are recorded: at each, the innermost live
     * carrier and the region's block-local bindings, read from the raw frame.
     * Installs {@code bump(value)}, a native Closure answering
     * {@code value * 10}, reached by dynamic lookup.
     */
    private static Run runToLine(String characters, int... lines) throws Exception {
        assertTrue(characters.endsWith("run()\n"), "scenario must end with run()");
        Source source =
                Source.newBuilder(
                                ProtosLanguage.ID,
                                characters + "run()\n".repeat(RUNS - 1),
                                "perf025-s3.protos")
                        .uri(URI.create("memory:///perf025-s3.protos"))
                        .mimeType(ProtosLanguage.MIME_TYPE)
                        .build();
        ProtosActivation module = new ProtosCoreBootstrap().bootstrap(CORE).newModuleActivation();
        module.context().createLocalSlot(
                "bump",
                ProtosClosureValue.nativeClosure(
                        (activation, supplied) ->
                                Long.valueOf(
                                        Math.multiplyExact(
                                                assertInstanceOf(
                                                                Long.class,
                                                                supplied.get(0))
                                                        .longValue(),
                                                10L))));
        AtomicReference<Throwable> callbackFailure = new AtomicReference<>();
        List<Stop> stops = new ArrayList<>();
        ProtosExecutionOutcome outcome;

        try (ProtosPolyglotExecutionContext polyglot =
                ProtosPolyglotExecutionContext.open(
                        InputStream.nullInputStream(),
                        OutputStream.nullOutputStream(),
                        OutputStream.nullOutputStream())) {
            Instrument instrument =
                    polyglot.engineForTesting().getInstruments().get("debugger");
            Debugger debugger = instrument.lookup(Debugger.class);
            try (DebuggerSession session =
                    debugger.startSession(
                            event -> {
                                try {
                                    stops.add(stopOf(event.getTopStackFrame()));
                                } catch (Throwable failure) {
                                    callbackFailure.compareAndSet(null, failure);
                                } finally {
                                    event.prepareContinue();
                                }
                            })) {
                for (int line : lines) {
                    session.install(Breakpoint.newBuilder(source).lineIs(line).build());
                }
                outcome = polyglot.execute(source, module);
            }
        }

        if (callbackFailure.get() != null) {
            throw new AssertionError("debugger callback failed", callbackFailure.get());
        }
        assertEquals(0, stops.size() % RUNS, "every run must hit the same breakpoints");
        int finalRunStops = stops.size() / RUNS;
        return new Run(
                outcome, List.copyOf(stops.subList(stops.size() - finalRunStops, stops.size())));
    }

    private static Stop stopOf(DebugStackFrame top) {
        TagTreeNode node =
                assertInstanceOf(TagTreeNode.class, top.getRawNode(ProtosLanguage.class));
        Frame frame = top.getRawFrame(ProtosLanguage.class, FrameInstance.FrameAccess.READ_ONLY);
        BytecodeNode bytecode = node.getBytecodeNode();
        int bytecodeIndex = node.getEnterBytecodeIndex();
        Object[] names = bytecode.getLocalNames(bytecodeIndex);
        Object[] call = frame.getArguments();
        /*
         * I115: a LOCAL binding is the value of its Bytecode DSL local; a CELL
         * binding's local holds its ProtosLexicalCell, whose value is the
         * binding (Java null: not yet established, so absent). A free cell
         * the invocation carries is copied into a prefixed root local and is
         * the captured binding of that name. Nothing is materialized or
         * published to read them.
         */
        Map<String, Object> bindings = new HashMap<>();
        Map<String, Object> freeCells = new HashMap<>();
        Map<String, Object> inlineBindings = new HashMap<>();
        String inlinePrefix = CanonicalToBytecodeLowerer.NORMAL_INLINE_CALLBACK_BINDING_LOCAL_PREFIX;
        boolean inlined = false;
        boolean inlineMaterialized = false;
        String freeCellPrefix = CanonicalToBytecodeLowerer.FREE_CELL_LOCAL_PREFIX;
        for (int offset = 0; offset < names.length; offset++) {
            if (names[offset] == null) {
                continue;
            }
            String name = String.valueOf(names[offset]);
            Object value = bytecode.getLocalValue(bytecodeIndex, frame, offset);
            if (name.equals("inlineCallbackActivation") && value instanceof ProtosActivation) {
                inlineMaterialized = true;
            }
            if (name.startsWith(inlinePrefix)) {
                inlined = true;
                if (value != null) {
                    inlineBindings.put(name.substring(inlinePrefix.length()), value);
                }
                continue;
            }
            if (value instanceof ProtosLexicalCell cell) {
                value = cell.valueOrNullForRuntime();
                if (value == null) {
                    continue;
                }
            }
            if (name.startsWith(freeCellPrefix)) {
                freeCells.put(name.substring(freeCellPrefix.length()), value);
            } else {
                bindings.put(name, value);
            }
        }
        freeCells.forEach(bindings::putIfAbsent);
        bindings.putAll(inlineBindings);
        assertTrue(ProtosFrameArguments.hasActivation(call),
                () -> "the breakpoint is not inside a callback invocation");
        boolean materialized = inlined
                ? inlineMaterialized : ProtosFrameArguments.hasPublishedActivation(call);
        List<String> instructions =
                bytecode.getInstructionsAsList().stream().map(ProtosInstructionNames::of).toList();
        return new Stop(call, inlined, materialized, bindings, instructions);
    }
}
