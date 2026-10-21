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
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.oracle.truffle.api.debug.DebuggerSession;
import com.oracle.truffle.api.debug.Debugger;
import com.guillermomolina.protos.parser.ProtosParser;
import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.guillermomolina.protos.semantic.Canonicalizer;
import com.guillermomolina.protos.semantic.ast.CanonicalSequence;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage.LanguageReference;
import com.oracle.truffle.api.bytecode.BytecodeNode;
import com.oracle.truffle.api.bytecode.Instruction;
import com.oracle.truffle.api.bytecode.TagTree;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.graalvm.polyglot.Context;
import org.junit.jupiter.api.Test;

/**
 * I095-C focal evidence: ordinary {@code ifTrue} and {@code ifFalse} sends,
 * including the {@code ifTrue}/{@code ifTrueIfFalse} sends that D211 contextual
 * {@code if} canonicalizes to, share one lowering. A canonical literal
 * receiver selects its branch statically; any other receiver is evaluated
 * once and classified at run time, a non-canonical receiver keeping the
 * ordinary send. A selected literal callback whose whole activation is proven
 * unobservable runs inline in the caller's Bytecode root. Unproven callbacks
 * keep the established topology.
 *
 * <p>PLAT067-B: every Context uses the one proof-optimized lowering, so a
 * root holds only the admitted lowering whether or not a debugger is
 * attached. Inline admission is observed through
 * {@code run}'s bytecode; physical callees through the guest CallTarget stack
 * seen by a native {@code probe} (depth 2 is {@code run} over the module
 * root). No test mutates the frozen global root.
 */
final class ProtosI095BooleanControlInlineTest {
    private static final Path CORE = Path.of("protos", "lib", "core");

    private static final LanguageReference<ProtosLanguage> LANGUAGE_REF =
            LanguageReference.create(ProtosLanguage.class);

    private static final int INLINE_DEPTH = 2;

    /**
     * The standard ifTrue/ifFalse rows and the D211 contextual if sends,
     * constant and dynamic receivers.
     */
    @Test
    void standardRowsAndContextualIfForConstantAndDynamicReceivers() throws Exception {
        // Digits: selected result 7, unselected null 0, true 1, false 2.
        String code = """
                code: (value) => {
                    (value === null).ifTrueIfFalse(() => { 0 }, () => {
                        (value === true).ifTrueIfFalse(() => { 1 }, () => {
                            (value === false).ifTrueIfFalse(() => { 2 }, () => { value })
                        })
                    })
                }
                """;
        String constant = code + """
                run: () => {
                    a: code(true.ifTrue(() => { 7 }))
                    b: code(false.ifTrue(() => { 9 }))
                    c: code(true.ifFalse(() => { 9 }))
                    d: code(false.ifFalse(() => { 7 }))
                    e: code(if(true) { 7 })
                    f: code(if(false) { 9 })
                    g: code(if(true) { 7 } else { 9 })
                    h: code(if(false) { 9 } else { 7 })
                    ((((((a * 10 + b) * 10 + c) * 10 + d) * 10 + e) * 10 + f) * 10 + g) * 10 + h
                }
                run()
                """;
        assertParity(constant, 70077077);

        String dynamic = code + """
                row: (t, f) => {
                    a: code(t.ifTrue(() => { 7 }))
                    b: code(f.ifTrue(() => { 9 }))
                    c: code(t.ifFalse(() => { 9 }))
                    d: code(f.ifFalse(() => { 7 }))
                    e: code(if(t) { 7 })
                    g: code(if(f) { 9 })
                    h: code(if(t) { 7 } else { 9 })
                    i: code(if(f) { 9 } else { 7 })
                    ((((((a * 10 + b) * 10 + c) * 10 + d) * 10 + e) * 10 + g) * 10 + h) * 10 + i
                }
                row(true, false) * 10 + row(true, false)
                """;
        assertParity(dynamic, 700770770L + 70077077L);
        System.out.println("I095_C_STANDARD_ROWS_AND_CONTEXTUAL_IF=PASS");
    }

    /**
     * Large Integer literals (minted with the executing Prelude) inside an
     * admitted callback body and inside a Closure called from one (the
     * TOOL009 conformance-case shape).
     */
    @Test
    void largeIntegerLiteralsInsideAdmittedBodies() throws Exception {
        String called = """
                run: () => {
                    big: () => {
                        999999999999999999999999 * 999999999999999999999999
                    }
                    (big() == 999999999999999999999998000000000000000000000001).ifTrue(() => { 7 })
                }
                run()
                """;
        assertParity(called, 7);
        assertExecutedOutcome(called, 7);

        String dynamic = """
                run: (b) => {
                    b.ifTrue(() => {
                        (999999999999999999999999.div(3) == 333333333333333333333333).ifTrue(() => { 7 })
                    })
                }
                run(true)
                """;
        assertParity(dynamic, 7);
        assertExecutedOutcome(dynamic, 7);
    }

    /** One site alternates true/false and then sees a non-canonical receiver. */
    @Test
    void dynamicSiteAlternatesAndMissesToOrdinaryDispatch() throws Exception {
        String source = """
                run: (b) => {
                    probe()
                    if(b) {
                        inner: 5
                        inner + 1
                    }
                }
                custom: {
                    ifTrue: (callback) => {
                        probe()
                        callback() + 30
                    }
                }
                code: (value) => {
                    (value === null).ifTrueIfFalse(() => { 2 }, () => { value })
                }
                code(run(true)) * 1000 + code(run(false)) * 100 + code(run(true)) * 10 + 0 * code(run(false)) + run(custom)
                """;
        Execution normal = execute(false, source);
        assertInteger(6260 + 36, normal.value());
        List<String> names = instructionNames(normal.rootOfFirstProbe());
        int classify = position(names, "TryCompactCanonicalBooleanOne");
        int falseClass = position(names, "IsCanonicalBooleanFalseClass");
        // I101-A: the classifier is the admitted site's semantic guard; no mode test precedes it.
        assertTrue(classify >= 0, () -> "no canonical classification: " + names);
        assertTrue(falseClass > classify,
                () -> "falseClass " + falseClass + " classify " + classify);
        // MISS keeps an ordinary prepared send in the NORMAL lowering.
        int prepare = falseClass + 1
                + position(names.subList(falseClass + 1, names.size()), "PrepareSendOne");
        assertTrue(prepare > falseClass,
                () -> "prepare " + prepare + " falseClass " + falseClass);
        // The custom method is a physical callee above run.
        assertTrue(
                normal.probe().depths().stream().anyMatch(depth -> depth > INLINE_DEPTH),
                normal.probe()::toString);
        assertParity(source, 6296);
        System.out.println("I095_C_DYNAMIC_ALTERNATION_AND_MISS=PASS");
    }

    /** An admitted callback runs in run's root with its own semantic RootTag. */
    @Test
    void admittedCallbackRunsInTheCallerRootWithItsOwnRootTag() throws Exception {
        String source = """
                run: (b) => {
                    p: probe
                    outer: 40
                    b.ifFalse(() => {
                        inner: 2
                        p()
                        outer + inner
                    })
                }
                run(false)
                """;
        Execution normal = execute(false, source);
        assertInteger(42, normal.value());
        assertEquals(List.of(INLINE_DEPTH), normal.probe().depths(), normal.probe()::toString);
        ProtosBytecodeRootNode runRoot = normal.rootOfFirstProbe();
        // Materializes the tag tree read below.
        runRoot.getRootNodes().ensureComplete();
        assertTrue(rootTags(runRoot.getBytecodeNode()) >= 2,
                "run's RootTag plus the inline callback's semantic RootTag");
        assertTrue(
                rootTagSources(runRoot.getBytecodeNode().getTagTree()).stream()
                        .anyMatch(text -> text.contains("inner: 2")
                                && !text.contains("outer: 40")),
                () -> rootTagSources(runRoot.getBytecodeNode().getTagTree()).toString());
        assertParity(source, 42);
        System.out.println("I095_C_INLINE_ROOT_TAG_AND_SOURCE=PASS");
    }

    /**
     * PLAT067-B: with and without an attached debugger the result, the single
     * callback execution and the physical frame depths agree.
     */
    @Test
    void attachedDebuggerAgreesOnResultAndSingleCallbackExecution() throws Exception {
        String source = """
                run: (b) => {
                    p: probe
                    value: 9
                    r: if(b) {
                        value: 41
                        value = 42
                        p()
                        value
                    }
                    (r == 42).ifTrueIfFalse(() => { value }, () => { -1 })
                }
                run(true)
                """;
        Execution normal = execute(false, source);
        Execution debug = execute(true, source);
        assertInteger(9, normal.value());
        assertInteger(9, debug.value());
        assertEquals(1, normal.probe().calls.get());
        assertEquals(1, debug.probe().calls.get());
        assertEquals(normal.probe().depths(), debug.probe().depths(), debug.probe()::toString);
        System.out.println("I095_C_DEBUGGER_PARITY=PASS");
    }

    /** Receiver and argument are evaluated once, in order, on every path. */
    @Test
    void receiverAndArgumentAreEvaluatedExactlyOnce() throws Exception {
        String source = """
                trace: () => {
                    order: 0
                    hits: 0
                    index: 0
                    next: () => {
                        index = index + 1
                        order = order * 10 + 1
                        (index === 2).ifTrueIfFalse(() => { false }, () => { true })
                    }
                    a: next().ifTrue(() => {
                        hits = hits + 1
                        order = order * 10 + 2
                        5
                    })
                    b: next().ifFalse(() => {
                        hits = hits + 1
                        order = order * 10 + 3
                        5
                    })
                    c: next().ifFalse(() => {
                        hits = hits + 100
                        9
                    })
                    nullMark: (c === null).ifTrueIfFalse(() => { 1 }, () => { 0 })
                    selectedMark: (b == 5).ifTrueIfFalse(() => { 1 }, () => { 0 })
                    order * 1000 + hits * 100 + a * 10 + nullMark + selectedMark
                }
                trace()
                """;
        assertParity(source, 12131L * 1000 + 2 * 100 + 50 + 2);

        String producer = """
                trace: () => {
                    count: 0
                    producer: () => {
                        count = count + 1
                        () => { 1 }
                    }
                    run: (receiver) => {
                        receiver.ifTrue(producer())
                    }
                    custom: { ifTrue: (callback) => { callback() + 10 } }
                    run(true)
                    run(false)
                    r: run(custom)
                    count * 100 + r
                }
                trace()
                """;
        assertParity(producer, 311);

        // D211: the contextual condition is the send receiver, evaluated once.
        String contextual = """
                trace: () => {
                    count: 0
                    next: () => {
                        count = count + 1
                        true
                    }
                    r: if(next()) {
                        40
                    } else {
                        0
                    }
                    count + r + 1
                }
                trace()
                """;
        assertParity(contextual, 42);
        System.out.println("I095_C_EVALUATED_ONCE=PASS");
    }

    /** An unselected literal is neither validated nor invoked. */
    @Test
    void unselectedInvalidLiteralIsNotInvoked() throws Exception {
        String source = """
                run: (t, f) => {
                    a: t.ifFalse((x, y) => { x + y })
                    b: f.ifTrue((x, y) => { x + y })
                    c: t.ifFalse((x) => { x })
                    d: f.ifTrue((x) => { x })
                    e: true.ifFalse((x, y) => { x + y })
                    g: false.ifTrue((x) => { x })
                    ok: (a === null) && (b === null) && (c === null) && (d === null) && (e === null) && (g === null)
                    ok.ifTrueIfFalse(() => { 1 }, () => { 0 })
                }
                run(true, false)
                """;
        assertParity(source, 1);
        System.out.println("I095_C_UNSELECTED_NOT_VALIDATED=PASS");
    }

    /** A selected non-Closure callable uses ordinary zero-argument invocation. */
    @Test
    void selectedNonClosureCallableAndSelectorOverrides() throws Exception {
        String source = """
                run: (t, f) => {
                    callable: { call: () => { 8 } }
                    a: t.ifTrue(callable)
                    b: f.ifFalse(callable)
                    c: t.ifTrueIfFalse(callable, 5)
                    custom: {
                        ifTrue: (callback) => { 100 }
                        ifFalse: (callback) => { 200 }
                        if: (callback) => { 300 }
                        else: (callback) => { 400 }
                    }
                    d: custom.ifTrue(() => { 1 })
                    e: custom.ifFalse(() => { 1 })
                    // D211: if/else are ordinary custom members, not standard selectors.
                    g: custom.if(() => { 1 })
                    h: custom.else(() => { 1 })
                    a + b + c + d + e + g + h
                }
                run(true, false)
                """;
        assertParity(source, 8 + 8 + 8 + 1000);

        String callOverride = """
                run: (b) => {
                    cb: () => { 1 }
                    cb.call: () => { 50 }
                    x: b.ifTrue(cb)
                    callable: { call: () => { 1 } }
                    callable.removeSlot("call")
                    callable.call: () => { 7 }
                    y: b.ifTrue(callable)
                    x + y
                }
                run(true)
                """;
        assertParity(callOverride, 57);
        System.out.println("I095_C_CALLABLE_AND_OVERRIDES=PASS");
    }

    /** D211 contextual if returns the selected branch's exact result. */
    @Test
    void contextualIfReturnsTheExactSelectedResult() throws Exception {
        String source = """
                run: (t, f) => {
                    marker: { marker: 1 }
                    pending: (() => { 41 }).future()
                    a: if(t) { 5 }
                    b: if(t) { marker } else { null }
                    c: if(f) { 1 } else { true }
                    d: if(f) { null } else { pending }
                    e: if(true) { false }
                    g: if(false) { 1 }
                    ok: (a == 5) && (b === marker) && (c === true) && (d === pending) && (e === false) && (g === null)
                    ok.ifTrueIfFalse(() => { d.value() + 1 }, () => { 0 })
                }
                run(true, false)
                """;
        assertExecutedOutcome(source, 42);
        System.out.println("I095_C_CONTEXTUAL_IF_EXACT_RESULT=PASS");
    }

    /** D211 two-arm contextual if invokes only the selected branch. */
    @Test
    void contextualIfInvokesOnlyTheSelectedBranch() throws Exception {
        String source = """
                run: (b) => {
                    trace: 0
                    if(b) { trace = trace * 10 + 1 } else { trace = trace * 10 + 2 }
                    if(b) { trace = trace * 10 + 3 }
                    trace
                }
                run(true) * 1000 + run(false)
                """;
        assertParity(source, 13 * 1000 + 2);

        String constant = """
                run: () => {
                    trace: 0
                    if(false) { trace = trace * 10 + 1 } else { trace = trace * 10 + 2 }
                    if(true) { trace = trace * 10 + 3 } else { trace = trace * 10 + 4 }
                    trace
                }
                run()
                """;
        assertParity(constant, 23);
        System.out.println("I095_C_CONTEXTUAL_IF_SELECTED_ONLY=PASS");
    }

    /** Captures by reference, own bindings, shadowing and D179. */
    @Test
    void bindingsCapturesShadowingAndRecreation() throws Exception {
        String source = """
                run: (b) => {
                    outer: 40
                    shadow: 11
                    inside: b.ifFalse(() => {
                        shadow: 1
                        outer = outer + shadow
                        shadow = 2
                        outer + shadow
                    })
                    inside + outer + shadow
                }
                run(false)
                """;
        assertParity(source, 95);

        String recreation = """
                run: (b) => {
                    x: 1
                    a: b.ifTrue(() => {
                        inner: 10
                        x + inner
                    })
                    context.removeSlot("x")
                    context.x: 5
                    c: b.ifTrue(() => {
                        inner: 10
                        x + inner
                    })
                    a * 100 + c
                }
                run(true)
                """;
        assertParity(recreation, 1115);

        String fresh = """
                run: (b) => {
                    total: 0
                    i: 0
                    (() => i < 3).whileTrue(() => {
                        if(b) {
                            own: i
                            own = own + 1
                            total = total + own
                        }
                        i = i + 1
                    })
                    total
                }
                run(true)
                """;
        assertParity(fresh, 6);
        System.out.println("I095_C_BINDINGS_PRESERVED=PASS");
    }

    /** Error, non-local return, ensure and suspension cross the control. */
    @Test
    void abruptExitsEnsureAndSuspension() throws Exception {
        String error = """
                cleaned: 0
                run: (b) => {
                    (() => {
                        if(b) {
                            0
                        } else {
                            inner: 1
                            Error().signal()
                            inner
                        }
                    }).ensure(() => { cleaned = cleaned + 1 })
                    99
                }
                r: Error.handle(() => { run(false) }, (caught) => { 40 })
                r + cleaned + cleaned
                """;
        assertParity(error, 42);

        String nonLocal = """
                run: (b) => {
                    if(b) { ^41 }
                    0
                }
                run(true) + run(false) + 1
                """;
        assertParity(nonLocal, 42);

        String suspension = """
                run: (b) => {
                    pending: (() => { 41 }).future()
                    seen: 0
                    r: if(b) {
                        inner: pending.value()
                        seen = inner + 1
                        seen
                    }
                    (r == 42).ifTrueIfFalse(() => { seen }, () => { -1 })
                }
                run(true)
                """;
        assertExecutedOutcome(suspension, 42);

        String task = """
                run: (b) => {
                    task: (() => {
                        pending: (() => { 20 }).future()
                        b.ifFalse(() => {
                            inner: pending.value()
                            inner + 22
                        })
                    }).future()
                    task.value()
                }
                run(false)
                """;
        assertExecutedOutcome(task, 42);
        System.out.println("I095_C_ABRUPT_AND_SUSPENSION=PASS");
    }

    /** An unproven callback keeps the established topology. */
    @Test
    void unprovenCallbackKeepsTheEstablishedTopology() throws Exception {
        String source = """
                run: (b) => {
                    probe()
                    b.ifFalse(() => {
                        inner: 1
                        missing + inner
                    })
                }
                missing: 41
                run(false)
                """;
        Execution normal = execute(false, source);
        List<String> names = instructionNames(normal.rootOfFirstProbe());
        // The site is not admitted: no canonical classification, only the ordinary send.
        assertTrue(position(names, "TryCompactCanonicalBooleanOne") < 0, names::toString);
        assertTrue(position(names, "PrepareSend") >= 0, names::toString);
        System.out.println("I095_C_UNPROVEN_FALLBACK=PASS");
    }

    private record Execution(Object value, Probe probe) {
        /** The guest root that called probe first. */
        ProtosBytecodeRootNode rootOfFirstProbe() {
            return assertInstanceOf(
                    ProtosBytecodeRootNode.class,
                    probe.stacks.get(0).get(0));
        }
    }

    /**
     * Runs {@code source} in a fresh Context; with {@code debug}, a Truffle
     * debugger session is attached to its Engine before the program is
     * lowered (PLAT067-B: it observes, it never selects another lowering).
     */
    private static Execution execute(boolean debug, String source) throws Exception {
        try (Context context = Context.newBuilder(ProtosLanguage.ID).build();
                DebuggerSession attached =
                        debug
                                ? Debugger.find(context.getEngine()).startSession(event -> {})
                                : null) {
            context.initialize(ProtosLanguage.ID);
            context.enter();
            try {
                Probe probe = new Probe();
                Object value = lowerRoot(source).getCallTarget().call(probe.module());
                return new Execution(value, probe);
            } finally {
                context.leave();
            }
        }
    }

    /** The same program produces the same result with and without a debugger attached. */
    private static void assertParity(String source, long expected) throws Exception {
        assertInteger(expected, execute(false, source).value());
        assertInteger(expected, execute(true, source).value());
    }

    /**
     * Pending Futures need the C-prime continuation entry, which the direct
     * CallTarget harness above does not provide.
     */
    private static void assertExecutedOutcome(String source, long expected) throws Exception {
        ProtosExecutionOutcome outcome =
                ProtosTestExecutionSupport.execute(
                        "i095-c-execution.protos",
                        source,
                        new ProtosCoreBootstrap().bootstrap(CORE).newModuleActivation());
        assertEquals(
                ProtosExecutionOutcome.State.COMPLETED,
                outcome.state(),
                () -> "Outcome: " + outcome.state() + " error=" + outcome.error());
        assertInteger(expected, outcome.value());
    }

    /** A native {@code probe} recording the guest CallTarget stack of every call. */
    private static final class Probe {
        final List<List<RootNode>> stacks = new ArrayList<>();
        final AtomicInteger calls = new AtomicInteger();

        List<Integer> depths() {
            return stacks.stream().map(List::size).distinct().toList();
        }

        ProtosActivation module() throws Exception {
            ProtosActivation module =
                    new ProtosCoreBootstrap().bootstrap(CORE).newModuleActivation();
            module.context().createLocalSlot(
                    "probe",
                    ProtosClosureValue.nativeClosure(
                            (activation, supplied) -> {
                                calls.incrementAndGet();
                                List<RootNode> stack = new ArrayList<>();
                                Truffle.getRuntime()
                                        .iterateFrames(
                                                frame -> {
                                                    stack.add(
                                                            ((RootCallTarget) frame.getCallTarget())
                                                                    .getRootNode());
                                                    return null;
                                                });
                                stacks.add(stack);
                                return ProtosNullValue.INSTANCE;
                            }));
            return module;
        }

        @Override
        public String toString() {
            return "stacks=" + stacks;
        }
    }

    private static ProtosBytecodeRootNode lowerRoot(String characters) {
        Source source =
                Source.newBuilder(ProtosLanguage.ID, characters, "i095-c.protos").build();
        CanonicalSequence sequence =
                (CanonicalSequence)
                        new Canonicalizer()
                                .canonicalize(new ProtosParser(characters).parseProgram());
        return new CanonicalToBytecodeLowerer(
                        LANGUAGE_REF.get(null),
                        source)
                .lowerRoot(sequence);
    }

    private static List<String> instructionNames(ProtosBytecodeRootNode root) {
        root.getRootNodes().ensureComplete();
        return instructionNames(root.getBytecodeNode());
    }

    private static List<String> instructionNames(BytecodeNode bytecodeNode) {
        return bytecodeNode.getInstructionsAsList().stream()
                .map(ProtosInstructionNames::of)
                .toList();
    }

    private static int position(List<String> names, String operation) {
        for (int i = 0; i < names.size(); i++) {
            if (names.get(i).contains(operation)) {
                return i;
            }
        }
        return -1;
    }

    private static void assertInteger(long expected, Object value) {
        assertEquals(
                Long.toString(expected),
                com.guillermomolina.protos.runtime.ProtosNumericValueSupport
                        .integerDecimalText(value));
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

    private static List<String> rootTagSources(TagTree tree) {
        List<String> sources = new ArrayList<>();
        if (tree == null) {
            return sources;
        }
        if (tree.hasTag(StandardTags.RootTag.class) && tree.getSourceSection() != null) {
            sources.add(tree.getSourceSection().getCharacters().toString());
        }
        for (TagTree child : tree.getTreeChildren()) {
            sources.addAll(rootTagSources(child));
        }
        return sources;
    }
}
