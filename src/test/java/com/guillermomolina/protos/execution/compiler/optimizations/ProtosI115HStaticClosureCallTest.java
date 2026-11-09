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

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosLargeIntegerValue;
import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.guillermomolina.protos.runtime.ProtosNumericValueSupport;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.control.ProtosReturnHome;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.bytecode.BytecodeTier;
import com.oracle.truffle.api.source.Source;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.Test;

/**
 * I115-H1: the local unary Closure calls once admitted by the retired I115-H
 * static invocation now take the ordinary lowering: the Closure is created
 * and bound ({@code MaterializeCurrentClosure}) and, since I115-H11, called
 * through the GraalPy-style {@code CallUnaryMethod}, whose dynamic {@code
 * Object.call} selection, caches and Assumptions decide the target, with B5+
 * frame arguments.
 *
 * <p>{@link #experimentalLightweightClosureDropsCreatorMetadata} still pins
 * the separate lightweight Closure representation divergence.
 */
final class ProtosI115HStaticClosureCallTest {
    private static final Path CORE = Path.of("protos", "lib", "core");
    private static final int PAST_CACHE_LIMITS = 12;
    private static final String TERMINAL_CALL = "c.CallUnaryMethod";
    private static final String MATERIALIZE = "c.MaterializeCurrentClosure";

    private static final String RUN = """
            run: () => {
                identity: (value) => { value }
                identity(1)
            }
            """;

    // ---- Functional --------------------------------------------------------

    @Test
    void unaryCallReturnsItsArgument() throws Exception {
        withCore(module -> {
            evaluate(RUN, "i115h-return-define.protos", module);
            for (int i = 0; i < PAST_CACHE_LIMITS; i++) {
                assertInteger(1, evaluate("run()\n", "i115h-return.protos", module));
            }
            assertOrdinaryCall(functionRoot("run", module));
        });
    }

    @Test
    void unaryCallIsDefinitionIndependent() throws Exception {
        record Shape(String definition, String parameter, long value) {}
        withCore(module -> {
            for (Shape shape : List.of(
                    new Shape("echo", "item", 7),
                    new Shape("pass", "x", 0),
                    new Shape("same", "argument", 123456))) {
                String function = "run_" + shape.definition();
                evaluate(function + ": () => {\n"
                                + "    " + shape.definition() + ": (" + shape.parameter()
                                + ") => { " + shape.parameter() + " }\n"
                                + "    " + shape.definition() + "(" + shape.value() + ")\n"
                                + "}\n",
                        "i115h-shape-define.protos", module);
                for (int i = 0; i < PAST_CACHE_LIMITS; i++) {
                    assertInteger(shape.value(),
                            evaluate(function + "()\n", "i115h-shape.protos", module));
                }
                assertOrdinaryCall(functionRoot(function, module));
            }
        });
    }

    @Test
    void unaryLiteralBodyReturnsConstant() throws Exception {
        withCore(module -> {
            evaluate("""
                    run: () => {
                        constant: (ignored) => { 5 }
                        constant(9)
                    }
                    """, "i115h-literal-define.protos", module);
            for (int i = 0; i < PAST_CACHE_LIMITS; i++) {
                assertInteger(5, evaluate("run()\n", "i115h-literal.protos", module));
            }
            assertOrdinaryCall(functionRoot("run", module));
        });
    }

    @Test
    void unaryClosurePreservesSeparatePhysicalRoot() throws Exception {
        withCore(module -> {
            ProtosBytecodeRootNode moduleRoot = parse(RUN, "i115h-roots.protos");
            assertEquals(3, moduleRoot.getRootNodes().getNodes().size(),
                    "Expected module, run and identity roots");
        });
    }

    /**
     * The former static case creates and binds its Closure through the
     * ordinary lowering and calls it through one {@code CallUnaryMethod}.
     */
    @Test
    void unaryCallUsesOrdinaryCreationAndTerminalCall() throws Exception {
        withCore(module -> {
            evaluate(RUN, "i115h-shape-run-define.protos", module);
            ProtosBytecodeRootNode run = functionRoot("run", module);
            List<String> names = unquickened(names(run));
            assertTrue(names.contains(MATERIALIZE), names::toString);
            assertEquals(1, names.stream().filter(TERMINAL_CALL::equals).count(),
                    names::toString);
            assertInteger(1, evaluate("run()\n", "i115h-shape-run.protos", module));
        });
    }

    /**
     * The admitted unary call uses the existing B5+ contract: the five-slot
     * header with the Closure in slot 0, the exact module caller in slot 3, the
     * primitive-accepting ReturnHome marker and the supplied argument at
     * {@link ProtosFrameArguments#SUPPLIED_ARGUMENT_OFFSET}.
     */
    @Test
    void unaryCallUsesB5PlusArguments() throws Exception {
        withCore(module -> {
            evaluate("identity: (value) => { value }\n", "i115h-abi-define.protos", module);
            ProtosClosureValue closure = assertInstanceOf(
                    ProtosClosureValue.class,
                    module.context().readLocalSlot("identity").orElseThrow());
            Object supplied = ProtosNumericValueSupport.integer(42);
            Object[] arguments =
                    ProtosFrameArguments.compactAdmittedTerminalClosureCallOneNoHome(
                            closure, module, supplied);

            assertEquals(5, ProtosFrameArguments.HEADER_LENGTH);
            assertEquals(5, ProtosFrameArguments.SUPPLIED_ARGUMENT_OFFSET);
            assertEquals(ProtosFrameArguments.HEADER_LENGTH + 1, arguments.length);
            assertSame(supplied, arguments[5]);
            assertSame(supplied, ProtosFrameArguments.compactSuppliedArgumentOrNull(arguments, 0));
            assertSame(ProtosNullValue.INSTANCE,
                    ProtosFrameArguments.compactSuppliedArgumentOrNull(arguments, 1));
            assertTrue(ProtosFrameArguments.isUnmaterializedCompactCall(arguments));
            assertSame(closure, ProtosFrameArguments.compactClosure(arguments));
            assertSame(closure, arguments[0]);
            assertSame(ProtosFrameArguments.compactDirectClosureCallOne(
                    closure, module, null, supplied)[1], arguments[1]);
            assertSame(null, arguments[2]);
            assertSame(module, arguments[3]);
            assertSame(ProtosReturnHome.unobservableAcceptingPrimitiveResultForRuntime(), arguments[4]);
            assertSame(module, ProtosFrameArguments.compactCaller(arguments));
            assertTrue(ProtosFrameArguments.acceptsPrimitiveResult(arguments));
            assertSame(ProtosReturnHome.unobservable(),
                    ProtosFrameArguments.compactReturnHome(arguments));

            RootCallTarget target = closure.executionPlan().orElseThrow()
                    .bytecodeActivationTargetForComposition();
            assertInteger(42, ProtosNumericValueSupport.guestValue(target.call(arguments)));
            Object[] beforePublication = arguments.clone();
            ProtosActivation published = ProtosFrameArguments.activation(arguments);
            assertSame(published, arguments[0]);
            assertSame(module, arguments[3]);
            for (int i = 1; i < arguments.length; i++) {
                assertSame(beforePublication[i], arguments[i]);
            }
        });
    }

    @Test
    void unaryCallSurvivesBytecodeReparse() throws Exception {
        withCore(module -> {
            evaluate(RUN, "i115h-reparse-define.protos", module);
            ProtosBytecodeRootNode run = functionRoot("run", module);
            assertInteger(1, evaluate("run()\n", "i115h-reparse-before.protos", module));

            run.getRootNodes().ensureComplete();

            assertEquals(3, run.getRootNodes().getNodes().size());
            assertOrdinaryCall(run);
            for (int i = 0; i < PAST_CACHE_LIMITS; i++) {
                assertInteger(1, evaluate("run()\n", "i115h-reparse-after.protos", module));
            }
        });
    }

    /**
     * I115: CallUnaryMethod is registered with {@code forceCached = true},
     * so a root containing it runs the cached interpreter even when a high
     * uncached threshold is requested. This verifies the forced-cached
     * unary execution path: the same ordinary terminal call, the correct
     * result, and a stable root across repeated calls. It does not test an
     * uncached interpreter, which this operation deliberately never uses.
     */
    @Test
    void unaryCallExecutesInForcedCachedInterpreter() throws Exception {
        withCore(module -> {
            evaluate(RUN, "i115h-uncached-define.protos", module);
            ProtosBytecodeRootNode run = functionRoot("run", module);
            run.getBytecodeNode().setUncachedThreshold(Integer.MAX_VALUE);
            assertOrdinaryCall(run);

            for (int i = 0; i < PAST_CACHE_LIMITS; i++) {
                assertInteger(1, evaluate("run()\n", "i115h-uncached.protos", module));
            }

            assertEquals(BytecodeTier.CACHED, run.getBytecodeNode().getTier(),
                    "a forced-cached CallUnaryMethod root runs cached");
            assertSame(run, functionRoot("run", module), "the root is stable");
            assertOrdinaryCall(run);
        });
    }

    /**
     * A non-capturing Closure whose body may observe its ReturnHome keeps
     * the creator's ReturnHome: {@code ^value} returns from {@code outer}.
     */
    @Test
    void nonCapturingClosurePreservesNonLocalReturnHome() throws Exception {
        withCore(module -> {
            evaluate("""
                    outer: () => {
                        inner: (value) => { ^value }
                        inner(5)
                        0
                    }
                    """, "i115h-nonlocal-define.protos", module);
            for (int i = 0; i < PAST_CACHE_LIMITS; i++) {
                assertInteger(5, evaluate("outer()\n", "i115h-nonlocal.protos", module));
            }
        });
    }

    @Test
    void nonLocalReturnBodyReturnsFromCreator() throws Exception {
        withCore(module -> {
            evaluate("""
                    outer: () => {
                        inner: (value) => { ^value }
                        inner(5)
                    }
                    """, "i115h-nonlocal-terminal-define.protos", module);
            for (int i = 0; i < PAST_CACHE_LIMITS; i++) {
                assertInteger(5, evaluate("outer()\n", "i115h-nonlocal-terminal.protos", module));
            }
        });
    }

    // ---- Other Closure uses ------------------------------------------------

    @Test
    void capturingClosureCall() throws Exception {
        assertResult("""
                run: (base) => {
                    add: (value) => { base }
                    add(1)
                }
                """, "run(10)\n", 10);
    }

    @Test
    void escapingClosureIsMaterialized() throws Exception {
        withCore(module -> {
            evaluate("""
                    run: () => {
                        identity: (value) => { value }
                        identity
                    }
                    """, "i115h-escape-define.protos", module);
            for (int i = 0; i < PAST_CACHE_LIMITS; i++) {
                assertInstanceOf(ProtosClosureValue.class,
                        evaluate("run()\n", "i115h-escape.protos", module));
            }
            evaluate("escaped: run()\n", "i115h-escape-bind.protos", module);
            assertInteger(3, evaluate("escaped(3)\n", "i115h-escape-use.protos", module));
            List<String> names = names(functionRoot("run", module));
            assertTrue(names.contains(MATERIALIZE), names::toString);
        });
    }

    @Test
    void storedClosureRemainsCallable() throws Exception {
        withCore(module -> {
            // Each call stores into a fresh holder (a Closure is an object), so
            // the member creation never meets an existing slot.
            evaluate("""
                    run: (holder) => {
                        identity: (value) => { value }
                        holder.stored: identity
                        identity(1)
                    }
                    """, "i115h-stored-define.protos", module);
            for (int i = 0; i < PAST_CACHE_LIMITS; i++) {
                assertInteger(1, evaluate("run(() => { 0 })\n", "i115h-stored.protos", module));
            }
            evaluate("""
                    box: () => { 0 }
                    run(box)
                    """, "i115h-stored-box.protos", module);
            ProtosClosureValue box = assertInstanceOf(
                    ProtosClosureValue.class,
                    module.context().readLocalSlot("box").orElseThrow());
            ProtosClosureValue stored = assertInstanceOf(
                    ProtosClosureValue.class,
                    box.readLocalSlot("stored").orElseThrow());
            assertNotNull(stored.definition());
            assertInteger(5, evaluate("box.stored(5)\n", "i115h-stored-use.protos", module));
        });
    }

    @Test
    void aliasedClosureCall() throws Exception {
        assertResult("""
                run: () => {
                    identity: (value) => { value }
                    f: identity
                    f(1)
                }
                """, "run()\n", 1);
    }

    @Test
    void reassignedBindingCallsNewClosure() throws Exception {
        assertResult("""
                run: () => {
                    identity: (value) => { value }
                    identity = (value) => { 2 }
                    identity(1)
                }
                """, "run()\n", 2);
    }

    @Test
    void composedArgumentCall() throws Exception {
        assertResult("""
                run: () => {
                    identity: (value) => { value }
                    identity(1 + 2)
                }
                """, "run()\n", 3);
    }

    @Test
    void nestedOrNonLeafBodyCall() throws Exception {
        assertResult("""
                run: () => {
                    next: (value) => { value + 1 }
                    next(1)
                }
                """, "run()\n", 2);
        assertResult("""
                run: () => {
                    wrap: (value) => {
                        inner: value
                        inner
                    }
                    wrap(4)
                }
                """, "run()\n", 4);
    }

    // ---- Experimental divergences -------------------------------------------

    /**
     * EXPERIMENTAL DIVERGENCE: a materialized non-capturing Closure is the
     * lightweight representation and keeps none of its creator's receiver,
     * methodHome, ReturnHome or lexical environment.
     */
    @Test
    void experimentalLightweightClosureDropsCreatorMetadata() throws Exception {
        withCore(module -> {
            evaluate("""
                    box: () => { 0 }
                    box.make: () => { (value) => { value } }
                    made: box.make()
                    """, "i115h-light-define.protos", module);
            ProtosClosureValue box = assertInstanceOf(
                    ProtosClosureValue.class,
                    module.context().readLocalSlot("box").orElseThrow());
            ProtosClosureValue made = assertInstanceOf(
                    ProtosClosureValue.class,
                    module.context().readLocalSlot("made").orElseThrow());

            assertSame(ProtosObjectValue.rootObject(), made.capturedReceiver());
            assertNotSame(box, made.capturedReceiver());
            assertTrue(made.methodHome().isEmpty());
            assertTrue(made.returnHome().isEmpty());
            assertEquals(null, made.capturedLexicalEnvironmentForRuntime());
            assertInteger(4, evaluate("made(4)\n", "i115h-light-use.protos", module));
        });
    }

    // ---- I115 recursive signed-64 lane ---------------------------------------

    private static final String FIBONACCI = """
            fibonacci: (n) => {
                result: n

                (n >= 2).ifTrue() {
                    result = fibonacci(n - 1) + fibonacci(n - 2)
                }

                result
            }

            run: () => { fibonacci(20) }

            grow: (n) => {
                result: 4611686018427387904

                (n > 0).ifTrue() {
                    result = grow(n - 1) + grow(n - 1)
                }

                result
            }

            runGrow: () => { grow(3) }
            """;

    /**
     * EXPERIMENTAL: recursive fibonacci in the compact lane. Both recursive
     * sends reuse the static unary call sites of the one fibonacci root, no
     * Closure or activation is materialized per call, and the primitive
     * signed-64 result is returned as a raw Long between the recursive
     * calls, across repeated (specializing) executions.
     */
    @Test
    void recursiveFibonacciKeepsSigned64Results() throws Exception {
        try (ProtosStandaloneHostedSession session = openHosted("i115-fibonacci", FIBONACCI)) {
            ProtosBytecodeRootNode fibonacci = hostedRoot(session, "fibonacci");
            List<String> names = names(fibonacci);
            // I115: both recursive calls are the shared CallUnaryMethod over the
            // module global, and n is bound by the one parameter lowering.
            assertRecursiveUnaryLowering(names);
            for (String forbidden : List.of(MATERIALIZE, "BindUnaryFrameParameter",
                    "PrepareClosureCall", "InstallFrameLexicalAuthority", "TryPrimitiveNumeric",
                    "InvokeStaticClosureOneTail", "ExperimentalLongArgument0")) {
                assertTrue(names.stream().noneMatch(name -> name.contains(forbidden)),
                        forbidden + ": " + names);
            }
            int roots = fibonacci.getRootNodes().getNodes().size();
            for (int i = 0; i < PAST_CACHE_LIMITS; i++) {
                Value result = session.prepareTopLevel("run").executable().execute();
                assertTrue(result.fitsInLong(), result::toString);
                assertEquals(6765L, result.asLong());
            }
            assertEquals(roots, fibonacci.getRootNodes().getNodes().size(),
                    "repeated recursion adds no physical root");
            // Quickening (for example store.local$Long) is allowed; re-lowering is not.
            assertEquals(unquickened(names), unquickened(names(fibonacci)),
                    "the call sites are reused, not re-lowered");

            /*
             * The recursive callee boundary: the fibonacci root entered
             * directly with a B5 direct-call header (the real hosted Closure
             * and caller, supplied 20) returns the signed-64 Long.
             */
            ProtosHostExecutableClosure host =
                    session.prepareTopLevel("fibonacci").hostExecutableForTesting();
            RootCallTarget target = host.target;
            assertSame(fibonacci, target.getRootNode());
            Object raw = session.processContextForTesting().callForRuntime(() ->
                    target.call(ProtosFrameArguments.compactDirectClosureCallOne(
                            host.closure, host.caller, null, 20L)));
            assertEquals(6765L, assertInstanceOf(Long.class, raw,
                    "the recursive result stays a raw signed-64 Long").longValue());
        }
    }

    /**
     * EXPERIMENTAL: a recursive sum that overflows signed-64 takes the
     * exact Integer fallback and later sums a large operand there, never
     * truncated to a 32-bit or 64-bit value.
     */
    @Test
    void recursiveOverflowKeepsExactLargeInteger() throws Exception {
        try (ProtosStandaloneHostedSession session = openHosted("i115-grow", FIBONACCI)) {
            for (int i = 0; i < PAST_CACHE_LIMITS; i++) {
                Value result = session.prepareTopLevel("runGrow").executable().execute();
                assertTrue(result.isNumber(), result::toString);
                assertFalse(result.fitsInLong(), result::toString);
                // 2^62 * 2^3 = 2^65.
                assertTrue(result.toString().contains("36893488147419103232"),
                        result::toString);
            }
        }
    }

    // ---- I115 on-demand Prelude transport (GraalPy caller flags) ----------

    /**
     * A recursion that never overflows never requests the caller Prelude
     * and its caller-needs Assumption is never invalidated; its calls are
     * the shared CallUnaryMethod with B5 headers.
     */
    @Test
    void noOverflowNeverRequestsCallerPrelude() throws Exception {
        try (ProtosStandaloneHostedSession session = openHosted("i115-no-need", FIBONACCI)) {
            ProtosBytecodeRootNode fibonacci = hostedRoot(session, "fibonacci");
            Assumption stability = fibonacci.callerNeedsStability();
            for (int i = 0; i < PAST_CACHE_LIMITS; i++) {
                assertEquals(6765L,
                        session.prepareTopLevel("run").executable().execute().asLong());
            }
            assertFalse(fibonacci.hasCallerNeed(ProtosBytecodeRootNode.NEEDS_CALLER_PRELUDE));
            assertTrue(stability.isValid(), "no need was registered");
            assertSame(stability, fibonacci.callerNeedsStability());
            List<String> names = unquickened(names(fibonacci));
            assertRecursiveUnaryLowering(names);
            assertTrue(names.stream().noneMatch(name -> name.contains(MATERIALIZE)),
                    names::toString);
        }
    }

    /**
     * I115: the B5 header carries the invoked Closure, which owns its
     * Prelude, so a signed-64 overflow mints the large Integer with that
     * Closure's Prelude and needs nothing from its caller. (The retired
     * minimal unary header omitted the Closure and had to request the
     * caller's Prelude; that recovery is not rebuilt.) The caller-needs
     * Assumption stays valid and unchanged across repeated overflows, and a
     * result that fits keeps its raw long carrier.
     */
    @Test
    void overflowUsesTheClosurePreludeWithoutCallerNeed() throws Exception {
        try (ProtosStandaloneHostedSession session = openHosted("i115-need", FIBONACCI)) {
            ProtosBytecodeRootNode grow = hostedRoot(session, "grow");
            assertFalse(grow.hasCallerNeed(ProtosBytecodeRootNode.NEEDS_CALLER_PRELUDE));
            Assumption before = grow.callerNeedsStability();
            ProtosClosureValue growClosure = session.prepareTopLevel("grow").closureForTesting();
            assertSame(growClosure,
                    session.prepareTopLevel("grow").hostExecutableForTesting().closure,
                    "the invocation is owned by the grow Closure itself");

            Object first = rawRunGrow(session);
            assertSame(prelude(session, "grow").bigIntegerPrototype(),
                    assertInstanceOf(ProtosLargeIntegerValue.class, first)
                            .parent().orElseThrow(),
                    "the overflow uses the invoked Closure's Prelude");
            List<String> namesAfterFirst = unquickened(names(grow));

            assertFalse(grow.hasCallerNeed(ProtosBytecodeRootNode.NEEDS_CALLER_PRELUDE),
                    "no caller Prelude is needed");
            assertSame(before, grow.callerNeedsStability(), "no Assumption was replaced");
            assertTrue(before.isValid());
            Assumption after = before;

            for (int i = 0; i < PAST_CACHE_LIMITS; i++) {
                assertGrow(session);
            }
            assertFalse(grow.hasCallerNeed(ProtosBytecodeRootNode.NEEDS_CALLER_PRELUDE));
            assertSame(after, grow.callerNeedsStability(), "no need was registered");
            assertTrue(after.isValid());
            assertSame(grow, hostedRoot(session, "grow"), "the same root and call sites");
            assertEquals(namesAfterFirst, unquickened(names(grow)));
            assertRecursiveUnaryLowering(namesAfterFirst, "c.RichCompareGt");

            // A result that fits still returns the raw long (grow(0) = 2^62),
            // entered with a B5 direct-call header of the real Closure and caller.
            ProtosHostExecutableClosure host =
                    session.prepareTopLevel("grow").hostExecutableForTesting();
            RootCallTarget target = host.target;
            Object raw = session.processContextForTesting().callForRuntime(() ->
                    target.call(ProtosFrameArguments.compactDirectClosureCallOne(
                            host.closure, host.caller, null, 0L)));
            assertEquals(4611686018427387904L, assertInstanceOf(Long.class, raw).longValue());
        }
    }

    /**
     * Two independent Contexts: each overflow mints its large Integer with
     * its own Context's Prelude, and neither target registers a caller need
     * or disturbs the other's caller-needs Assumption.
     */
    @Test
    void overflowPreludeIsIsolatedPerContext() throws Exception {
        try (ProtosStandaloneHostedSession first = openHosted("i115-context-a", FIBONACCI);
                ProtosStandaloneHostedSession second = openHosted("i115-context-b", FIBONACCI)) {
            ProtosBytecodeRootNode firstGrow = hostedRoot(first, "grow");
            ProtosBytecodeRootNode secondGrow = hostedRoot(second, "grow");
            assertNotSame(firstGrow, secondGrow, "each Context owns its target");
            Assumption firstStable = firstGrow.callerNeedsStability();
            Assumption secondStable = secondGrow.callerNeedsStability();

            Object firstLarge = rawRunGrow(first);
            assertFalse(firstGrow.hasCallerNeed(ProtosBytecodeRootNode.NEEDS_CALLER_PRELUDE));
            assertSame(secondStable, secondGrow.callerNeedsStability(),
                    "one Context's execution does not touch the other");
            Object secondLarge = rawRunGrow(second);
            assertFalse(secondGrow.hasCallerNeed(ProtosBytecodeRootNode.NEEDS_CALLER_PRELUDE));
            assertSame(firstStable, firstGrow.callerNeedsStability());
            assertSame(secondStable, secondGrow.callerNeedsStability());

            ProtosPrelude firstPrelude = prelude(first, "grow");
            ProtosPrelude secondPrelude = prelude(second, "grow");
            assertNotSame(firstPrelude, secondPrelude);
            assertSame(firstPrelude.bigIntegerPrototype(),
                    assertInstanceOf(ProtosLargeIntegerValue.class, firstLarge)
                            .parent().orElseThrow());
            assertSame(secondPrelude.bigIntegerPrototype(),
                    assertInstanceOf(ProtosLargeIntegerValue.class, secondLarge)
                            .parent().orElseThrow());
            assertGrow(first);
            assertGrow(second);
        }
    }

    private static void assertGrow(ProtosStandaloneHostedSession session) throws Exception {
        Value result = session.prepareTopLevel("runGrow").executable().execute();
        assertFalse(result.fitsInLong(), result::toString);
        assertTrue(result.toString().contains("36893488147419103232"), result::toString);
    }

    /* runGrow's raw result inside its own Context. */
    private static Object rawRunGrow(ProtosStandaloneHostedSession session) throws Exception {
        ProtosHostExecutableClosure host =
                session.prepareTopLevel("runGrow").hostExecutableForTesting();
        return session.processContextForTesting().callForRuntime(() ->
                host.target.call(ProtosFrameArguments.compactDirectClosureCallZeroNoHome(
                        host.closure, host.caller)));
    }

    private static ProtosPrelude prelude(ProtosStandaloneHostedSession session, String name)
            throws Exception {
        ProtosClosureValue closure = session.prepareTopLevel(name).closureForTesting();
        ProtosPrelude prelude = session.processContextForTesting().callForRuntime(
                closure::preludeOrNullForRuntime);
        assertNotNull(prelude, name + " has its own Prelude");
        return prelude;
    }

    // ---- Helpers -----------------------------------------------------------

    private static ProtosStandaloneHostedSession openHosted(String name, String source)
            throws Exception {
        Path file = Path.of("target", "i115-h-static", name + ".protos");
        Files.createDirectories(file.getParent());
        Files.writeString(file, source, StandardCharsets.UTF_8);
        ProtosStandaloneHostedSession session = ProtosStandaloneHostedSession.open(CORE, file);
        if (session.initialOutcome().state() != ProtosExecutionOutcome.State.COMPLETED) {
            session.close();
            throw new AssertionError("Module initialization failed: " + name);
        }
        return session;
    }

    /* Instruction names without their quickening suffix. */
    private static List<String> unquickened(List<String> names) {
        return names.stream()
                .map(name -> {
                    int quickened = name.indexOf('$');
                    return quickened < 0 ? name : name.substring(0, quickened);
                })
                .toList();
    }

    private static ProtosBytecodeRootNode hostedRoot(
            ProtosStandaloneHostedSession session, String name) throws Exception {
        return assertInstanceOf(ProtosBytecodeRootNode.class,
                session.prepareTopLevel(name).hostExecutableForTesting().target.getRootNode());
    }

    private static void assertResult(String definition, String call, long expected)
            throws Exception {
        withCore(module -> {
            evaluate(definition, "i115h-case-define.protos", module);
            for (int i = 0; i < PAST_CACHE_LIMITS; i++) {
                assertInteger(expected, evaluate(call, "i115h-case.protos", module));
            }
        });
    }

    /*
     * The local Closure is materialized and called through CallUnaryMethod;
     * neither retired Call1 nor the recursive-lane static call is emitted.
     */
    private static void assertOrdinaryCall(ProtosBytecodeRootNode root) {
        List<String> names = unquickened(names(root));
        assertTrue(names.contains(MATERIALIZE), names::toString);
        assertTrue(names.contains(TERMINAL_CALL), names::toString);
        for (String retired : List.of(
                "c.InvokeClosureOneTail",
                "c.InvokeExperimentalClosureOneTail",
                "c.InvokeStaticClosureOneTail")) {
            assertTrue(!names.contains(retired), () -> retired + ": " + names);
        }
    }

    /*
     * A self-recursive unary function (fibonacci, grow): exactly two
     * CallUnaryMethod sites over the module-global binding, its parameter
     * bound by LoadSuppliedArgument stored into its native local, and none
     * of the retired static-call operations.
     */
    private static void assertRecursiveUnaryLowering(List<String> names) {
        assertRecursiveUnaryLowering(names, "c.RichCompareGe");
    }

    /*
     * I115: n is captured by the inline ifTrue body, so it is a CELL:
     * LoadSuppliedArgument(0) is stored straight into its cell. The
     * condition is the per-selector comparison, materialized and validated
     * by the unified Boolean control; '-' and '+' are NumberSubtract and
     * NumberAdd.
     */
    private static void assertRecursiveUnaryLowering(List<String> names, String comparison) {
        List<String> plain = unquickened(names);
        assertEquals(2, plain.stream().filter(TERMINAL_CALL::equals).count(),
                plain::toString);
        assertEquals(2, plain.stream().filter("c.ReadGlobal"::equals).count(),
                plain::toString);
        int load = plain.indexOf("c.LoadSuppliedArgument");
        assertTrue(load >= 0 && plain.get(load + 1).equals("c.StoreCell"), plain::toString);
        int compare = plain.indexOf(comparison);
        int materialize = plain.indexOf("c.MaterializePrimitiveNumericCarrier");
        int condition = plain.indexOf("c.ExperimentalCanonicalBooleanCondition");
        assertTrue(compare >= 0 && materialize > compare && condition > materialize,
                () -> "comparison, materialization, Boolean validation: " + plain);
        assertTrue(plain.contains("c.NumberSubtract") && plain.contains("c.NumberAdd"),
                plain::toString);
        for (String retired : List.of("InvokeStaticClosureOneTail", "ExperimentalLongArgument0",
                "ExperimentalInteger", "CopyArguments")) {
            assertTrue(plain.stream().noneMatch(name -> name.contains(retired)),
                    () -> retired + ": " + plain);
        }
    }

    private static List<String> names(ProtosBytecodeRootNode root) {
        return root.getBytecodeNode().getInstructionsAsList().stream()
                .map(ProtosInstructionNames::of)
                .toList();
    }

    private static ProtosBytecodeRootNode functionRoot(String function, ProtosActivation module) {
        ProtosClosureValue closure = assertInstanceOf(
                ProtosClosureValue.class,
                module.context().readLocalSlot(function).orElseThrow());
        return closure.executionPlan().orElseThrow().bytecodeActivationRootForTesting();
    }

    private interface CoreTest {
        void run(ProtosActivation module) throws Exception;
    }

    private static void withCore(CoreTest test) throws Exception {
        try (Context context = Context.newBuilder(ProtosLanguage.ID).build()) {
            context.initialize(ProtosLanguage.ID);
            context.enter();
            try {
                ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
                test.run(prelude.newModuleActivation());
            } finally {
                context.leave();
            }
        }
    }

    private static Source source(String characters, String name) {
        return Source.newBuilder(ProtosLanguage.ID, characters, name)
                .mimeType(ProtosLanguage.MIME_TYPE)
                .build();
    }

    private static Object evaluate(String characters, String name, ProtosActivation activation) {
        CallTarget target = ProtosLanguageContext.current().parsePublic(source(characters, name));
        return target.call(activation);
    }

    private static ProtosBytecodeRootNode parse(String characters, String name) {
        RootCallTarget target = assertInstanceOf(
                RootCallTarget.class,
                ProtosLanguageContext.current().parsePublic(source(characters, name)));
        return assertInstanceOf(ProtosBytecodeRootNode.class, target.getRootNode());
    }

    private static void assertInteger(long expected, Object actual) {
        assertEquals(Long.toString(expected),
                ProtosNumericValueSupport.integerDecimalText(actual));
    }
}
