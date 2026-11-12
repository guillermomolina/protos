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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.parser.ProtosParser;
import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.semantic.Canonicalizer;
import com.guillermomolina.protos.semantic.ast.CanonicalSequence;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage.LanguageReference;
import com.oracle.truffle.api.bytecode.BytecodeFrame;
import com.oracle.truffle.api.bytecode.BytecodeNode;
import com.oracle.truffle.api.bytecode.ContinuationRootNode;
import com.oracle.truffle.api.bytecode.Instruction;
import com.oracle.truffle.api.bytecode.LocalAccessor;
import com.oracle.truffle.api.bytecode.TagTreeNode;
import com.oracle.truffle.api.debug.Breakpoint;
import com.oracle.truffle.api.debug.DebugScope;
import com.oracle.truffle.api.debug.DebugStackFrame;
import com.oracle.truffle.api.debug.DebugValue;
import com.oracle.truffle.api.debug.Debugger;
import com.oracle.truffle.api.debug.DebuggerSession;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Instrument;
import org.junit.jupiter.api.Test;

/**
 * Inline callbacks keep their bindings in the host root's native locals.
 * The context intrinsic publishes one live view per semantic invocation;
 * leaving the region detaches that view before the locals can be reused.
 * Native observers receive the guest-observed Context explicitly, so their
 * physical caller frame cannot be mistaken for the callback's activation.
 */
final class ProtosPerf025LazyInlineCallbackActivationTest {
    private static final Path CORE = Path.of("protos", "lib", "core");

    private static final LanguageReference<ProtosLanguage> LANGUAGE_REF =
            LanguageReference.create(ProtosLanguage.class);

    private static final String PLAIN_LOAD = "LoadInlineCallbackActivation";
    private static final String MATERIALIZE = "PublishInlineCallbackContext";

    @Test
    void frameNativeZeroParameterCallbackEntersWithoutActivation() throws Exception {
        /*
         * A dynamic standard receiver selects the inline branch; its custom
         * selection branch retains an ordinary callback invocation.
         */
        List<String> names =
                assertRegionWithoutActivation(
                        """
                        run: (flag) => {
                            probe()
                            flag.ifTrue(() => {
                                local: 1
                                local = local
                                local
                            })
                        }
                        run(true)
                        """);
        assertContains(names, "ResetInlineCallbackLocal");
        assertContains(names, "load.local");
        assertContains(names, "store.local");
        assertContains(names, "IsCanonicalBooleanOperand");
        System.out.println("PERF025_INLINE_CALLBACK_ENTRY_MATERIALIZES_ACTIVATION=NO");
        System.out.println(
                "PERF025_FRAME_NATIVE_CALLBACK_ENTRY_HAS_LOAD_INLINE_CALLBACK_ACTIVATION=NO");
        System.out.println("PERF025_FRAME_NATIVE_ZERO_PARAMETER_CALLBACK_STAYS_LAZY=YES");
        System.out.println("PERF025_FRAME_NATIVE_CURRENT_READ_WITHOUT_ACTIVATION=YES");
        System.out.println("PERF025_FRAME_NATIVE_CURRENT_WRITE_WITHOUT_ACTIVATION=YES");
        System.out.println("PERF025_FRAME_NATIVE_CREATE_WITHOUT_ACTIVATION=YES");
    }

    @Test
    void frameNativeOneParameterEachEntersWithoutActivation() throws Exception {
        List<String> names =
                assertRegionWithoutActivation(
                        """
                        run: () => {
                            probe()
                            last: 0
                            Array(4, 5).each((element) => {
                                copy: element
                                copy = element
                                copy
                            })
                        }
                        run()
                        """);
        assertContains(names, "ArrayEachNext");
        assertContains(names, "store.local");
        System.out.println("PERF025_FRAME_NATIVE_ONE_PARAMETER_EACH_STAYS_LAZY=YES");
        System.out.println("PERF025_FRAME_NATIVE_PARAMETER_PATH_WITHOUT_ACTIVATION=YES");
    }

    @Test
    void frameNativeTwoParameterEachEntersWithoutActivation() throws Exception {
        List<Instruction> instructions =
                assertRegionInstructionsWithoutActivation(
                        """
                        run: () => {
                            probe()
                            m: Map()
                            m["a"] = 1
                            m["b"] = 2
                            m.each((key, value) => {
                                pair: value
                                pair = key
                                pair
                            })
                        }
                        run()
                        """);
        List<String> names = instructions.stream().map(ProtosInstructionNames::of).toList();
        List<Object> formals = constantLocals(instructions, "AssociationEachNext");
        assertEquals(2, formals.size(), "both formals have constant local accessors");
        assertEquals(2L, formals.stream().distinct().count(), "key and value use distinct locals");
        assertEquals(
                1L,
                names.stream().filter(name -> name.contains("AssociationEachNext")).count(),
                () -> "one association step binds both callback formals: " + names);
        System.out.println("PERF025_FRAME_NATIVE_TWO_PARAMETER_EACH_STAYS_LAZY=YES");
    }

    @Test
    void frameNativeEstablishmentOrdinalsAreInstructionConstants() throws Exception {
        List<Instruction> instructions =
                assertRegionInstructionsWithoutActivation(
                        """
                        run: () => {
                            probe()
                            pair: Array(4, 5, 6)
                            Array(1).each((element) => {
                                copy: element
                                (first, second): pair
                                first
                            })
                        }
                        run()
                        """);
        List<String> names = instructions.stream().map(ProtosInstructionNames::of).toList();
        List<Object> parameter = constantLocals(instructions, "ArrayEachNext");
        List<Object> creations = constantLocals(instructions, "ResetInlineCallbackLocal");
        assertEquals(1, parameter.size(), () -> "one formal: " + names);
        assertEquals(3, creations.size(), () -> "copy, first and second: " + names);
        assertEquals(4L,
                java.util.stream.Stream.concat(parameter.stream(), creations.stream()).distinct().count(),
                "each callback binding owns a distinct constant local accessor");
        int reset = firstIndexOf(names, "ResetInlineCallbackLocal");
        int observation = firstIndexOf(names, "ObserveMultipleCreatePrefix");
        int firstTarget = firstIndexOf(names, "ObservedMultipleCreateValue");
        assertTrue(reset < observation && observation < firstTarget,
                () -> "the prefix is observed before any target value is selected: " + names);
        assertEquals(2L, names.stream()
                .filter(name -> name.contains("ObservedMultipleCreateValue")).count());
        System.out.println("PERF030_I_INLINE_PARAMETER_CONSTANT_ORDINAL=YES");
        System.out.println("PERF030_I_INLINE_CREATION_CONSTANT_ORDINAL=YES");
        System.out.println("PERF030_I_INLINE_MULTIPLE_CREATE_RUNTIME_ORDINAL_ARRAY=NO");
    }

    /**
     * The first context expression materializes the semantic activation;
     * later observers in the same invocation receive the identical Context,
     * and each invocation has its own.
     */
    @Test
    void firstObserverMaterializesOncePerFreshInvocation() throws Exception {
        try (Context context = Context.newBuilder(ProtosLanguage.ID).build()) {
            context.initialize(ProtosLanguage.ID);
            context.enter();
            try {
                CarrierProbe probe = new CarrierProbe();
                lowerRoot(
                                """
                                run: () => {
                                    Array(1, 2).each((element) => {
                                        copy: element
                                        probe(context, copy)
                                        probe(context, copy)
                                    })
                                }
                                run()
                                """)
                        .getCallTarget()
                        .call(probe.module());

                assertEquals(4, probe.calls.size());
                ProtosBytecodeRootNode host = probe.host.get();
                host.getRootNodes().ensureComplete();
                List<String> names =
                        host.getBytecodeNode().getInstructionsAsList().stream()
                                .map(ProtosInstructionNames::of)
                                .toList();
                assertContains(names, MATERIALIZE);
                assertFalse(
                        names.stream().anyMatch(name -> name.contains(PLAIN_LOAD)),
                        () -> "frame-native region must not use the plain entry load: " + names);
                for (Observation observation : probe.calls) {
                    assertTrue(
                            observation.materializedAtProbe(),
                            "context is observed before the native probe executes");
                }
                assertSame(probe.calls.get(0).call(), probe.calls.get(1).call());
                assertSame(probe.calls.get(0).context(), probe.calls.get(1).context());
                assertSame(probe.calls.get(2).call(), probe.calls.get(3).call());
                assertSame(probe.calls.get(2).context(), probe.calls.get(3).context());
                assertNotSame(probe.calls.get(0).call(), probe.calls.get(2).call());
                assertNotSame(
                        probe.calls.get(0).context(), probe.calls.get(2).context());
            } finally {
                context.leave();
            }
        }
        System.out.println(
                "PERF025_FRAME_NATIVE_CALLBACK_HAS_MATERIALIZE_INLINE_CALLBACK_ACTIVATION=YES");
        System.out.println("PERF025_FIRST_SEMANTIC_OBSERVER_MATERIALIZES=YES");
        System.out.println("PERF025_MATERIALIZATION_AT_MOST_ONCE=YES");
        System.out.println("PERF025_FRESH_IDENTITY_PER_INVOCATION=YES");
        System.out.println("PERF025_OBSERVED_CONTEXT_HAS_INVOCATION_IDENTITY=YES");
    }

    /**
     * The observing send is the invocation's first materialization and is
     * followed by no binding operation: the Context it observes already holds
     * the formal and the local, and keeps them after the next invocation has
     * reused the region's block locals.
     */
    @Test
    void firstMaterializationMakesBindingsDurableBeforeTheObserverRuns() throws Exception {
        try (Context context = Context.newBuilder(ProtosLanguage.ID).build()) {
            context.initialize(ProtosLanguage.ID);
            context.enter();
            try {
                CarrierProbe probe = new CarrierProbe();
                lowerRoot(
                                """
                                run: () => {
                                    Array(10, 20).each((element) => {
                                        copy: element
                                        observe(context)
                                    })
                                }
                                run()
                                """)
                        .getCallTarget()
                        .call(probe.module());

                assertEquals(2, probe.calls.size());
                for (Observation observation : probe.calls) {
                    assertTrue(observation.materializedAtProbe());
                    assertTrue(
                            observation.durableAtProbe(),
                            "bindings must be durable before the observer runs");
                }
                assertBindings(probe.observedAtProbe.get(0), 10);
                assertBindings(probe.observedAtProbe.get(1), 20);
                assertNotSame(probe.observed.get(0), probe.observed.get(1));
                assertBindings(probe.observed.get(0).localSlotsSnapshot(), 10);
                assertBindings(probe.observed.get(1).localSlotsSnapshot(), 20);
            } finally {
                context.leave();
            }
        }
        System.out.println("PERF025_FIRST_OBSERVER_MATERIALIZES=YES");
        System.out.println("PERF025_DURABLE_TRANSFER_BEFORE_OBSERVER_RETURN=YES");
        System.out.println("PERF025_CONTEXT_OBSERVER_SEES_DURABLE_BINDINGS=YES");
        System.out.println("PERF025_POST_MATERIALIZATION_BINDING_OPERATION_REQUIRED=NO");
        System.out.println("PERF025_BLOCK_LOCAL_ALIAS_AFTER_ESCAPE=NO");
        System.out.println("PERF025_MATERIALIZATION_AT_MOST_ONCE=YES");
    }

    private static void assertBindings(java.util.Map<String, Object> bindings, long value) {
        assertEquals(java.util.Set.of("element", "copy"), bindings.keySet());
        assertInteger(value, bindings.get("element"));
        assertInteger(value, bindings.get("copy"));
    }

    /**
     * After the durable transfer the guest keeps using its bindings through
     * the activation: writes and creations land in the observed Context, the
     * retained Context keeps exactly its own invocation's bindings after the
     * region's block locals are reused, and D179 removal from that Context
     * makes the binding ABSENT for the guest (captured fallback).
     */
    @Test
    void observedContextReceivesDurableBindingsWithoutAliasingBlockLocals()
            throws Exception {
        try (Context context = Context.newBuilder(ProtosLanguage.ID).build()) {
            context.initialize(ProtosLanguage.ID);
            context.enter();
            try {
                CarrierProbe probe = new CarrierProbe();
                Object result =
                        lowerRoot(
                                        """
                                        run: () => {
                                            copy: 99
                                            seen: 0
                                            Array(10, 20).each((element) => {
                                                copy: element
                                                observe(context)
                                                copy = copy + 1
                                                later: copy
                                                forget()
                                                seen = seen * 1000 + copy
                                            })
                                            seen
                                        }
                                        run()
                                        """)
                                .getCallTarget()
                                .call(probe.module());

                assertInteger(99099, result);
                assertEquals(2, probe.observed.size());
                ProtosObjectValue first = probe.observed.get(0);
                ProtosObjectValue second = probe.observed.get(1);
                assertNotSame(first, second);
                assertInteger(10, first.readLocalSlot("element").orElseThrow());
                assertInteger(11, first.readLocalSlot("later").orElseThrow());
                assertFalse(first.hasLocalSlot("copy"), "D179 removal stays ABSENT");
                assertInteger(20, second.readLocalSlot("element").orElseThrow());
                assertInteger(21, second.readLocalSlot("later").orElseThrow());
                for (Observation observation : probe.calls) {
                    assertTrue(observation.durableAtProbe());
                }
                assertInteger(10, probe.observedAtProbe.get(0).get("copy"));
                assertInteger(20, probe.observedAtProbe.get(1).get("copy"));
            } finally {
                context.leave();
            }
        }
        System.out.println("PERF025_DURABLE_AUTHORITY_TRANSITION=YES");
        System.out.println("PERF025_DURABLE_AUTHORITY_DOES_NOT_ALIAS_REUSED_BLOCK_LOCALS=YES");
        System.out.println("PERF025_D179_PRESENT_ABSENT_PRESERVED=YES");
    }

    @Test
    void suspensionResumesWithTheSameMaterializedActivation() throws Exception {
        ProtosActivation module = new ProtosCoreBootstrap().bootstrap(CORE).newModuleActivation();
        CarrierProbe probe = new CarrierProbe();
        probe.install(module);
        ProtosExecutionOutcome outcome =
                ProtosTestExecutionSupport.execute(
                        "perf025-lazy-inline-suspension.protos",
                        """
                        run: () => {
                            step: (() => { 1 }).future()
                            acc: 0
                            visits: 0
                            Array(1, 2, 3).each((element) => {
                                local: element
                                visits = visits + 1
                                probe(context, local)
                                local = local * step.value()
                                probe(context, local)
                                acc = acc * 10 + local
                            })
                            acc * 10 + visits
                        }
                        run()
                        """,
                        module);
        assertEquals(
                ProtosExecutionOutcome.State.COMPLETED,
                outcome.state(),
                () -> "outcome=" + outcome.state() + ", error=" + outcome.error());
        assertInteger(1233, outcome.value());
        assertEquals(6, probe.calls.size());
        for (int visit = 0; visit < 3; visit++) {
            Observation beforeSuspension = probe.calls.get(visit * 2);
            Observation afterResumption = probe.calls.get(visit * 2 + 1);
            assertSame(beforeSuspension.call(), afterResumption.call());
            assertSame(beforeSuspension.context(), afterResumption.context());
        }
        System.out.println("PERF025_SUSPEND_RESUME_DOES_NOT_REMATERIALIZE=YES");
    }

    @Test
    void nonLocalReturnFromFrameNativeCallbackIsPreserved() throws Exception {
        ProtosExecutionOutcome outcome =
                ProtosTestExecutionSupport.execute(
                        "perf025-lazy-inline-nlr.protos",
                        """
                        run: () => {
                            visits: 0
                            Array(1, 2, 3).each((element) => {
                                local: element
                                visits = visits + 1
                                ^local * 10 + visits
                            })
                            0
                        }
                        run()
                        """,
                        new ProtosCoreBootstrap().bootstrap(CORE).newModuleActivation());
        assertEquals(
                ProtosExecutionOutcome.State.COMPLETED,
                outcome.state(),
                () -> "outcome=" + outcome.state() + ", error=" + outcome.error());
        assertInteger(11, outcome.value());
        System.out.println("PERF025_NLR_SEMANTICS_PRESERVED=YES");
    }

    /**
     * Suspended on a statement no guest operation has needed the activation
     * for yet, the debugger materializes it on demand, through the same
     * durable transfer, and sees the formal and the callback-owned local.
     */
    @Test
    void debuggerMaterializesOnDemandAndSeesParameterAndLocal() throws Exception {
        Source source =
                Source.newBuilder(
                                ProtosLanguage.ID,
                                """
                                run: () => {
                                    total: 0
                                    Array(5, 6).each((element) => {
                                        inner: element
                                        inner = inner
                                        total = total + inner
                                    })
                                    total
                                }
                                run()
                                """,
                                "perf025-lazy-inline-debugger.protos")
                        .uri(URI.create("memory:///perf025-lazy-inline-debugger.protos"))
                        .mimeType(ProtosLanguage.MIME_TYPE)
                        .build();
        ProtosActivation module = new ProtosCoreBootstrap().bootstrap(CORE).newModuleActivation();
        AtomicReference<Throwable> callbackFailure = new AtomicReference<>();
        List<String> elements = new ArrayList<>();
        List<String> inners = new ArrayList<>();

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
                                    DebugScope scope = event.getTopStackFrame().getScope();
                                    DebugValue element = scope.getDeclaredValue("element");
                                    DebugValue inner = scope.getDeclaredValue("inner");
                                    assertNotNull(element, "callback formal must be visible");
                                    assertNotNull(inner, "callback-owned local must be visible");
                                    elements.add(element.toDisplayString());
                                    inners.add(inner.toDisplayString());
                                } catch (Throwable failure) {
                                    callbackFailure.compareAndSet(null, failure);
                                } finally {
                                    event.prepareContinue();
                                }
                            })) {
                session.install(Breakpoint.newBuilder(source).lineIs(5).build());
                ProtosExecutionOutcome outcome = polyglot.execute(source, module);
                assertEquals(ProtosExecutionOutcome.State.COMPLETED, outcome.state());
                assertInteger(11, outcome.value());
            }
        }

        if (callbackFailure.get() != null) {
            throw new AssertionError("debugger callback failed", callbackFailure.get());
        }
        assertEquals(List.of("5", "6"), elements);
        assertEquals(List.of("5", "6"), inners);
        System.out.println("PERF025_DEBUGGER_MATERIALIZES_ON_DEMAND=YES");
        System.out.println("PERF025_DEBUGGER_SEES_PARAMETER_AND_LOCAL=YES");
    }

    /**
     * Lowers and runs {@code characters} (whose {@code run} calls {@code
     * probe()} before its single inline site) and returns the instruction
     * names of {@code run}, asserting that no operation materializes the
     * callback activation.
     */
    private static List<String> assertRegionWithoutActivation(String characters)
            throws Exception {
        return assertRegionInstructionsWithoutActivation(characters).stream()
                .map(ProtosInstructionNames::of)
                .toList();
    }

    private static List<Instruction> assertRegionInstructionsWithoutActivation(String characters)
            throws Exception {
        try (Context context = Context.newBuilder(ProtosLanguage.ID).build()) {
            context.initialize(ProtosLanguage.ID);
            context.enter();
            try {
                CarrierProbe probe = new CarrierProbe();
                lowerRoot(characters).getCallTarget().call(probe.module());
                ProtosBytecodeRootNode host = probe.host.get();
                assertNotNull(host, "probe must observe the run root");
                host.getRootNodes().ensureComplete();
                List<Instruction> instructions = host.getBytecodeNode().getInstructionsAsList();
                List<String> names = instructions.stream().map(ProtosInstructionNames::of).toList();
                assertFalse(
                        names.stream().anyMatch(name -> name.contains(PLAIN_LOAD)),
                        () -> "frame-native region must not use the plain entry load: " + names);
                assertFalse(
                        names.stream().anyMatch(name -> name.contains(MATERIALIZE)),
                        () -> "a region without observers needs no materialization point: " + names);
                return instructions;
            } finally {
                context.leave();
            }
        }
    }

    /** Constant local accessors consumed by one current binding operation. */
    private static List<Object> constantLocals(List<Instruction> instructions, String operation) {
        return instructions.stream()
                .filter(instruction -> ProtosInstructionNames.of(instruction).contains(operation))
                .flatMap(instruction -> instruction.getArguments().stream())
                .filter(argument -> switch (argument.getKind()) {
                    case CONSTANT -> true;
                    default -> false;
                })
                .map(Instruction.Argument::asConstant)
                .filter(LocalAccessor.class::isInstance)
                .toList();
    }

    private static int firstIndexOf(List<String> names, String operation) {
        for (int index = 0; index < names.size(); index++) {
            if (names.get(index).contains(operation)) {
                return index;
            }
        }
        return -1;
    }

    private static void assertContains(List<String> names, String operation) {
        assertTrue(
                names.stream().anyMatch(name -> name.contains(operation)),
                () -> "missing " + operation + " in " + names);
    }

    private static ProtosBytecodeRootNode lowerRoot(String characters) {
        Source source =
                Source.newBuilder(ProtosLanguage.ID, characters, "perf025-lazy-inline.protos")
                        .build();
        CanonicalSequence sequence =
                (CanonicalSequence)
                        new Canonicalizer()
                                .canonicalize(new ProtosParser(characters).parseProgram());
        return new CanonicalToBytecodeLowerer(
                        LANGUAGE_REF.get(null),
                        source)
                .lowerRoot(sequence);
    }

    private static void assertInteger(long expected, Object value) {
        assertEquals(Long.toString(expected), com.guillermomolina.protos.runtime.ProtosNumericValueSupport.integerDecimalText(value));
    }

    /** One observation of a guest Context, or a host-root discovery probe. */
    private record Observation(
            Object call,
            boolean materializedAtProbe,
            boolean durableAtProbe,
            ProtosObjectValue context) {}

    /**
     * Native probes discover the physical host root and inspect only a Context
     * explicitly passed by guest code. Retained Contexts must stay tied to
     * their semantic invocation when the physical locals are reused.
     */
    private static final class CarrierProbe {
        final AtomicReference<ProtosBytecodeRootNode> host = new AtomicReference<>();
        final List<Observation> calls = new ArrayList<>();
        final List<ProtosObjectValue> observed = new ArrayList<>();
        final List<java.util.Map<String, Object>> observedAtProbe = new ArrayList<>();

        ProtosActivation module() throws Exception {
            ProtosActivation module =
                    new ProtosCoreBootstrap().bootstrap(CORE).newModuleActivation();
            install(module);
            return module;
        }

        void install(ProtosActivation module) {
            module.context().createLocalSlot(
                    "probe",
                    ProtosClosureValue.nativeClosure(
                            (activation, supplied) -> {
                                record(supplied);
                                return ProtosNullValue.INSTANCE;
                            }));
            module.context().createLocalSlot(
                    "observe",
                    ProtosClosureValue.nativeClosure(
                            (activation, supplied) -> {
                                Observation observation = record(supplied);
                                ProtosObjectValue observedContext =
                                        observation.context();
                                observed.add(observedContext);
                                observedAtProbe.add(observedContext.localSlotsSnapshot());
                                return ProtosNullValue.INSTANCE;
                            }));
            module.context().createLocalSlot(
                    "forget",
                    ProtosClosureValue.nativeClosure(
                            (activation, supplied) -> {
                                observed.get(observed.size() - 1).removeLocalSlot("copy");
                                return ProtosNullValue.INSTANCE;
                            }));
        }

        private Observation record(List<?> supplied) {
            AtomicReference<Object[]> found = new AtomicReference<>();
            Truffle.getRuntime()
                    .iterateFrames(
                            frame -> {
                                RootNode root =
                                        ((RootCallTarget) frame.getCallTarget()).getRootNode();
                                Object source =
                                        root instanceof ContinuationRootNode continuation
                                                ? continuation.getSourceRootNode()
                                                : root;
                                if (!(source instanceof ProtosBytecodeRootNode semantic)) {
                                    return null;
                                }
                                host.compareAndSet(null, semantic);
                                Frame values =
                                        sourceFrame(
                                                frame.getFrame(FrameInstance.FrameAccess.READ_ONLY),
                                                semantic);
                                found.set(values == null ? null : values.getArguments());
                                return semantic;
                            });
            Object[] arguments = found.get();
            if (arguments == null || !ProtosFrameArguments.hasActivation(arguments)) {
                return null;
            }
            ProtosObjectValue context = supplied.isEmpty()
                    ? null : assertInstanceOf(ProtosObjectValue.class, supplied.get(0));
            boolean materialized = context != null;
            boolean durable = context != null
                    && context.hasLocalSlot("element") && context.hasLocalSlot("copy");
            Observation observation =
                    new Observation(
                            context != null ? context : arguments,
                            materialized,
                            durable,
                            context);
            calls.add(observation);
            return observation;
        }

        /**
         * The source root's own frame: the frame itself, or, for a resumed
         * continuation, the materialized source frame it carries as an
         * argument.
         */
        private static Frame sourceFrame(Frame frame, ProtosBytecodeRootNode semantic) {
            if (frame.getFrameDescriptor() == semantic.getFrameDescriptor()) {
                return frame;
            }
            for (Object argument : frame.getArguments()) {
                if (argument instanceof Frame candidate
                        && candidate.getFrameDescriptor() == semantic.getFrameDescriptor()) {
                    return candidate;
                }
            }
            return null;
        }
    }
}
