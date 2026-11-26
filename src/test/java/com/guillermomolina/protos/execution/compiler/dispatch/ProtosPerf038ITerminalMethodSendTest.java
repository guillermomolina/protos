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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosActorExecutionDomain;
import com.guillermomolina.protos.runtime.ProtosActorModuleState;
import com.guillermomolina.protos.runtime.ProtosArrayValue;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosFutureValue;
import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.guillermomolina.protos.runtime.ProtosNumericValueSupport;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.guillermomolina.protos.runtime.ProtosTask;
import com.guillermomolina.protos.runtime.ProtosValueLookup;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.bytecode.Instruction;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.Source;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.graalvm.polyglot.Context;
import org.junit.jupiter.api.Test;

/**
 * PERF038-I terminal immediate method Send1. The final immediate Send1 of a
 * genuine source root is one {@code InvokeMethodOneTail} operation (selector
 * as a constant operand, receiver and argument as its operands) instead of
 * {@code TryDirectSendOne}, the miss test and the prepared invocation. These
 * programs check the physical shape and complete FULL census of the
 * canonical program, and that selection invalidation, receiver and method
 * home, the ordinary fallback, re-entry of the direct tier and explicit
 * method extraction are unchanged.
 */
final class ProtosPerf038ITerminalMethodSendTest {
    private static final Path CORE = Path.of("protos", "lib", "core");
    private static final int PAST_CACHE_LIMITS = 12;

    private static final String CANONICAL = """
            receiver: {
                identity: (value) => { value }
            }

            run: () => {
                receiver.identity(1)
            }
            run()
            """;

    /** Prepared-call protocol a terminal method root must not contain. */
    private static final List<String> FORBIDDEN_IN_RUN = List.of(
            "c.TryDirectSendOne",
            "c.PrepareSendOne",
            "c.IsPreparedClosureInvocation",
            "c.EnterClosureCall",
            "c.FinishClosureCall",
            "yield");

    @Test
    void terminalMethodSendUsesOnePhysicalInvocation() throws Exception {
        withCore(module -> {
            Census census = census(CANONICAL, "perf038i-physical.protos");
            assertInteger(1, census.module().getCallTarget().call(module));

            List<String> run = names(census.signatures().get(census.runIndex()));
            // The terminal one-argument send is GetMethod then CallBinaryMethod.
            assertEquals(
                    1,
                    run.stream().filter("c.CallBinaryMethod"::equals).count(),
                    () -> "run must contain one terminal CallBinaryMethod\n" + census.report());
            assertEquals(
                    1,
                    run.stream().filter("c.GetMethod"::equals).count(),
                    () -> "run must contain one GetMethod\n" + census.report());
            for (String forbidden : FORBIDDEN_IN_RUN) {
                assertTrue(
                        !run.contains(forbidden),
                        () -> forbidden + " in run\n" + census.report());
            }
            // The method body keeps its own physical root.
            assertNotSame(
                    census.roots().get(census.runIndex()),
                    census.roots().get(census.methodIndex()));
            assertTrue(
                    !names(census.signatures().get(census.methodIndex()))
                            .contains("c.CallBinaryMethod"),
                    () -> "identity must not contain the caller's send\n" + census.report());
        });
        System.out.println("PERF038_I_PHYSICAL_INVOCATION=PASS");
    }

    @Test
    void terminalMethodSendHasCompleteDslCensus() throws Exception {
        withCore(module -> {
            Census census = census(CANONICAL, "perf038i-census.protos");
            assertInteger(1, census.module().getCallTarget().call(module));

            System.out.print(census.report());
            System.out.println("PERF038_I_ROOT_COUNT=" + census.roots().size());
            System.out.println("PERF038_I_RUN_DSL_FULL="
                    + census.signatures().get(census.runIndex()).size());
            System.out.println("PERF038_I_METHOD_DSL_FULL="
                    + census.signatures().get(census.methodIndex()).size());
            System.out.println("PERF038_I_TOTAL_DSL_FULL=" + census.total());
        });
    }

    @Test
    void terminalMethodSendInvalidatesOnSlotMutation() throws Exception {
        withCore(module -> {
            List<Object> results =
                    elements(evaluate(
                            "receiver: { pick: (value) => { value } }\n"
                                    + "run: (n) => { receiver.pick(n) }\n"
                                    + "before: [run(1), run(2), run(3)]\n"
                                    + "receiver.pick = (value) => { value + 100 }\n"
                                    + "[before[0], before[1], before[2], run(4), run(5)]\n",
                            "perf038i-invalidation.protos", module));
            assertEquals("1", integerValue(results.get(0)));
            assertEquals("2", integerValue(results.get(1)));
            assertEquals("3", integerValue(results.get(2)));
            assertEquals("104", integerValue(results.get(3)));
            assertEquals("105", integerValue(results.get(4)));
        });
        System.out.println("PERF038_I_SLOT_MUTATION_INVALIDATION=PASS");
    }

    @Test
    void terminalMethodSendPreservesReceiverAndHome() throws Exception {
        withCore(module -> {
            List<Object> results =
                    elements(evaluate(
                            "base: {\n"
                                    + "    value: 1\n"
                                    + "    read: (delta) => { value + delta }\n"
                                    + "    self: (ignored) => { this }\n"
                                    + "}\n"
                                    + "middle: base {\n"
                                    + "    read: (delta) => { super.read(delta) + 10 }\n"
                                    + "}\n"
                                    + "left: middle { value: 7 }\n"
                                    + "right: middle { value: 20 }\n"
                                    + "read: (receiver, delta) => { receiver.read(delta) }\n"
                                    + "self: (receiver) => { receiver.self(0) }\n"
                                    + "[read(left, 1), read(right, 1), read(left, 2), read(right, 2),\n"
                                    + " self(left) === left, self(right) === right,\n"
                                    + " self(left) === left]\n",
                            "perf038i-home.protos", module));
            assertEquals("18", integerValue(results.get(0)));
            assertEquals("31", integerValue(results.get(1)));
            assertEquals("19", integerValue(results.get(2)));
            assertEquals("32", integerValue(results.get(3)));
            assertSame(Boolean.TRUE, results.get(4));
            assertSame(Boolean.TRUE, results.get(5));
            assertSame(Boolean.TRUE, results.get(6));
        });
        System.out.println("PERF038_I_RECEIVER_AND_HOME=PASS");
    }

    @Test
    void terminalMethodSendFallbackHandlesUnadmittedCalls() throws Exception {
        withCore(module -> {
            // A native method: an explicitly constructed native Closure fixture.
            ProtosObjectValue nativeHolder = new ProtosObjectValue(ProtosObjectValue.rootObject());
            ProtosClosureValue twice = nativeClosure(
                    (activation, supplied) -> ProtosNumericValueSupport.integer(
                            2 * Long.parseLong(integerValue(supplied.get(0)))));
            nativeHolder.createLocalSlot("twice", twice);
            module.context().createLocalSlot("nativeHolder", nativeHolder);
            ProtosClosureValue selected = assertInstanceOf(
                    ProtosClosureValue.class,
                    nativeHolder.readLocalSlot("twice").orElseThrow());
            assertTrue(selected.nativeBody().isPresent(), "native fixture");
            evaluate("callNative: (n) => { nativeHolder.twice(n) }\n",
                    "perf038i-native-define.protos", module);
            enterCachedTier("callNative", module);
            for (int i = 0; i < PAST_CACHE_LIMITS; i++) {
                assertInteger(10, evaluate("callNative(5)\n", "perf038i-native.protos", module));
            }
            assertTerminalSite("callNative", module);
            assertEquals(0, tierEntries("callNative", module).size(), "native is never admitted");

            // UNKNOWN: a target whose root is not proven non-suspending
            // (the ProtosI115BDeferredCallerTest UNKNOWN fixture shape).
            evaluate("""
                    helper: (value) => { value }
                    outer: { forward: (value) => { helper(value) } }
                    forward: (n) => { outer.forward(n) }
                    """, "perf038i-unknown-define.protos", module);
            assertFalse(
                    methodRoot("outer.forward\n", module).provablyNonSuspendingBody(),
                    "UNKNOWN fixture");
            enterCachedTier("forward", module);
            for (int i = 0; i < PAST_CACHE_LIMITS; i++) {
                assertInteger(3, evaluate("forward(3)\n", "perf038i-unknown.protos", module));
            }
            assertTerminalSite("forward", module);
            // The direct source-send tier composes a callee suspension (see
            // terminalMethodSendSuspendsAndResumesWithoutReplay), so a target
            // without a non-suspension proof is admitted like any source method.
            assertEquals(1, tierEntries("forward", module).size(), "UNKNOWN is admitted");

            // A non-local return to the method home.
            assertEquals("7", integerValue(evaluate(
                    "early: { pick: (value) => { (value > 0).ifTrue(() => { ^ value })\n 99 } }\n"
                            + "pick: (n) => { early.pick(n) }\n"
                            + "pick(6)\npick(7)\n",
                    "perf038i-non-local.protos", module)));

            // A failing selection raises the ordinary Error, terminal or not.
            evaluate(
                    "plain: {}\n"
                            + "terminal: (r) => { r.missing(1) }\n"
                            + "nonTerminal: (r) => {\n"
                            + "    result: r.missing(1)\n"
                            + "    result\n"
                            + "}\n",
                    "perf038i-error-setup.protos", module);
            ProtosSignalException terminal = assertThrows(
                    ProtosSignalException.class,
                    () -> evaluate("terminal(plain)\n", "perf038i-error-terminal.protos", module));
            ProtosSignalException nonTerminal = assertThrows(
                    ProtosSignalException.class,
                    () -> evaluate(
                            "nonTerminal(plain)\n", "perf038i-error-non-terminal.protos", module));
            assertSame(
                    nonTerminal.error().parent().orElseThrow(),
                    terminal.error().parent().orElseThrow(),
                    "the terminal send raises the same Error kind");
        });
        System.out.println("PERF038_I_ORDINARY_FALLBACK=PASS");
    }

    /**
     * A terminal send whose selected method root is proven non-suspending
     * keeps its single CallBinaryMethod, enters the direct source-send tier
     * and returns the ordinary result with no continuation; receiver and
     * method home are unchanged and a selection mutation is observed. The
     * site is moved to the cached Bytecode tier by enterCachedTier, and
     * tierEntries reads the {@code callDirectSourceSend} specialization cache
     * of that site's CallBinaryMethod, so the single entry asserted below is
     * the existing direct source-send specialization, not a new operation.
     * The unproven suspending case is
     * terminalMethodSendSuspendsAndResumesWithoutReplay.
     */
    @Test
    void provenDirectTerminalSendPreservesSelectionAndResult() throws Exception {
        withCore(module -> {
            evaluate("""
                    holder: {
                        pick: (value) => { value }
                        self: (ignored) => { this }
                    }
                    child: holder {}
                    pick: (n) => { holder.pick(n) }
                    selfOf: (receiver) => { receiver.self(0) }
                    """, "perf038i-proven-define.protos", module);
            assertTrue(methodRoot("holder.pick\n", module).provablyNonSuspendingBody(),
                    "proven fixture");
            enterCachedTier("pick", module);
            for (int i = 0; i < PAST_CACHE_LIMITS; i++) {
                Object result = evaluate("pick(" + i + ")\n", "perf038i-proven.protos", module);
                assertFalse(result instanceof ProtosCallRequest, "no continuation request");
                assertFalse(ProtosTailCallContinuation.isSuspended(result));
                assertInteger(i, result);
            }
            assertTerminalSite("pick", module);
            assertEquals(1, tierEntries("pick", module).size(), "one direct source-send entry");

            Object holder = module.context().readLocalSlot("holder").orElseThrow();
            Object child = module.context().readLocalSlot("child").orElseThrow();
            for (int i = 0; i < PAST_CACHE_LIMITS; i++) {
                assertSame(holder, evaluate("selfOf(holder)\n", "perf038i-proven-self.protos", module));
                assertSame(child, evaluate("selfOf(child)\n", "perf038i-proven-child.protos", module));
            }

            evaluate("holder.pick = (value) => { value + 100 }\n",
                    "perf038i-proven-mutate.protos", module);
            assertInteger(105, evaluate("pick(5)\n", "perf038i-proven-mutated.protos", module));
            assertTerminalSite("pick", module);
        });
        System.out.println("PERF038_I_PROVEN_DIRECT_SEND=PASS");
    }

    /**
     * PERF038-I-SEM-02: a terminal send whose method really suspends is
     * executed by the terminal fallback; the Task suspends and resumes the
     * method's own state without replaying its prefix (adapted from
     * ProtosI112TailContinuationTest
     * .suspendibleSourceClosureInTerminalPositionResumesItsOwnState).
     */
    @Test
    void terminalMethodSendSuspendsAndResumesWithoutReplay() throws Exception {
        for (boolean cachedTier : List.of(false, true)) {
            try (Context context = Context.newBuilder(ProtosLanguage.ID).build()) {
                context.initialize(ProtosLanguage.ID);
                context.enter();
                try {
                    ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
                    ProtosActorExecutionDomain domain = new ProtosActorExecutionDomain();
                    ProtosActivation module = prelude.newModuleActivation(
                            new ProtosActorModuleState(), null, prelude.newExecutionContext(), domain);
                    ProtosFutureValue wait = new ProtosFutureValue(prelude.futurePrototype(), domain);
                    ProtosObjectValue resolved = new ProtosObjectValue(ProtosObjectValue.rootObject());
                    AtomicInteger before = new AtomicInteger();
                    AtomicInteger after = new AtomicInteger();
                    module.context().createLocalSlot("wait", wait);
                    module.context().createLocalSlot("before", counter(before));
                    module.context().createLocalSlot("after", counter(after));
                    evaluate("""
                            holder: {
                                waiter: (value) => {
                                    before()
                                    result: wait.value()
                                    after()
                                    result
                                }
                            }
                            run: (value) => { holder.waiter(value) }
                            """, "perf038i-suspend-define.protos", module);
                    assertTerminalSite("run", module);
                    assertFalse(methodRoot("holder.waiter\n", module).provablyNonSuspendingBody());
                    if (cachedTier) {
                        enterCachedTier("run", module);
                    }
                    CallTarget top = ProtosLanguageContext.current().parsePublic(
                            Source.newBuilder(ProtosLanguage.ID, "run(0)\n", "perf038i-suspend-top.protos")
                                    .mimeType(ProtosLanguage.MIME_TYPE)
                                    .build());

                    ProtosTask task = domain.createTask(
                            null, current -> ProtosBytecodeTaskExecution.execute(current, top, module));
                    assertTrue(domain.dispatchOne());
                    assertEquals(ProtosTask.State.SUSPENDED, task.state(), "really suspended");
                    assertEquals(1, before.get());
                    assertEquals(0, after.get());

                    assertTrue(wait.resolve(resolved, module));
                    assertTrue(domain.dispatchOne());
                    assertEquals(ProtosTask.State.COMPLETED, task.state());
                    assertSame(resolved, task.result().orElseThrow(), "exact resumed result");
                    assertEquals(1, before.get(), "the suspended prefix never replays");
                    assertEquals(1, after.get(), "the suffix runs exactly once");
                } finally {
                    context.leave();
                }
            }
        }
        System.out.println("PERF038_I_TERMINAL_SUSPENSION=PASS");
    }

    /**
     * Selection follows argument evaluation (adapted from
     * ProtosI112TailContinuationTest
     * .callSelectionObservesAnOverrideInstalledByArgumentEvaluation). An
     * admitted terminal operand is a direct value and cannot replace a slot,
     * so a mutating argument keeps the composed lowering at the same site
     * after the warmed terminal form; the terminal site itself observes a
     * replacement made after its PIC was installed.
     */
    @Test
    void terminalMethodSendSelectionOccursAfterArgumentEvaluation() throws Exception {
        withCore(module -> {
            evaluate("""
                    receiver: { pick: (value) => { value } }
                    run: (n) => { receiver.pick(n) }
                    swap: () => {
                        receiver.pick = (value) => { value + 100 }
                        1
                    }
                    mutating: () => { receiver.pick(swap()) }
                    """, "perf038i-selection-define.protos", module);
            enterCachedTier("run", module);
            enterCachedTier("mutating", module);
            assertInteger(1, evaluate("run(1)\n", "perf038i-selection-warm1.protos", module));
            assertInteger(2, evaluate("run(2)\n", "perf038i-selection-warm2.protos", module));
            assertEquals(1, tierEntries("run", module).size(), "PIC installed");

            assertFalse(
                    names(fullSignature(functionRoot("mutating", module)))
                            .contains("c.CallBinaryMethod"),
                    "a composed argument is not a terminal operand");
            assertInteger(101, evaluate("mutating()\n", "perf038i-selection-mutate.protos", module));
            assertInteger(103, evaluate("run(3)\n", "perf038i-selection-after.protos", module));
        });
        System.out.println("PERF038_I_POST_ARGUMENT_SELECTION=PASS");
    }

    /**
     * I115: the selected method replaces its own slot while it runs. The
     * current send completes with the method selected after its operands
     * were evaluated; the next send at the same specialized site observes the
     * replacement through the invalidated selection stability.
     */
    @Test
    void terminalMethodSendObservesSelectionChangedBySelectedMethod() throws Exception {
        withCore(module -> {
            evaluate("""
                    receiver: {
                        pick: (value) => {
                            receiver.pick = (other) => { other + 100 }
                            value
                        }
                    }
                    run: (n) => { receiver.pick(n) }
                    """, "perf038i-self-replace-define.protos", module);
            enterCachedTier("run", module);
            assertTerminalSite("run", module);
            assertInteger(1, evaluate("run(1)\n", "perf038i-self-replace-1.protos", module));
            assertInteger(102, evaluate("run(2)\n", "perf038i-self-replace-2.protos", module));
            for (int i = 0; i < PAST_CACHE_LIMITS; i++) {
                assertInteger(103, evaluate("run(3)\n", "perf038i-self-replace-3.protos", module));
            }
        });
        System.out.println("PERF038_I_SELF_REPLACED_SELECTION=PASS");
    }

    /**
     * I115: the fallback of an unadmitted (native) method resolves the
     * caller through the shared exact-caller accessor; the root's caller
     * needs converge after the first fallback and are never invalidated
     * again, and the root's needs are its own.
     */
    @Test
    void terminalMethodSendFallbackCallerNeedsConverge() throws Exception {
        withCore(module -> {
            ProtosObjectValue nativeHolder = new ProtosObjectValue(ProtosObjectValue.rootObject());
            nativeHolder.createLocalSlot("twice", nativeClosure(
                    (activation, supplied) -> ProtosNumericValueSupport.integer(
                            2 * Long.parseLong(integerValue(supplied.get(0))))));
            module.context().createLocalSlot("nativeHolder", nativeHolder);
            evaluate("""
                    receiver: { identity: (value) => { value } }
                    callNative: (n) => { nativeHolder.twice(n) }
                    admitted: (n) => { receiver.identity(n) }
                    """, "perf038i-needs-define.protos", module);
            enterCachedTier("callNative", module);
            enterCachedTier("admitted", module);
            ProtosBytecodeRootNode fallbackRoot = functionRoot("callNative", module);
            ProtosBytecodeRootNode admittedRoot = functionRoot("admitted", module);
            assertInteger(8, evaluate("callNative(4)\n", "perf038i-needs-first.protos", module));
            assertInteger(4, evaluate("admitted(4)\n", "perf038i-needs-admitted-first.protos", module));
            com.oracle.truffle.api.Assumption admittedBefore = admittedRoot.callerNeedsStability();
            com.oracle.truffle.api.Assumption settled = fallbackRoot.callerNeedsStability();
            for (int i = 0; i < PAST_CACHE_LIMITS; i++) {
                assertInteger(10, evaluate("callNative(5)\n", "perf038i-needs-native.protos", module));
                assertInteger(5, evaluate("admitted(5)\n", "perf038i-needs-admitted.protos", module));
            }
            assertSame(settled, fallbackRoot.callerNeedsStability(), "no repeated invalidation");
            assertTrue(settled.isValid());
            assertEquals(1, tierEntries("admitted", module).size());
            assertSame(admittedBefore, admittedRoot.callerNeedsStability(),
                    "admitted hits never change the root's caller needs");
            System.out.println("PERF038_I_FALLBACK_EXACT_ACTIVATION_NEED="
                    + fallbackRoot.hasCallerNeed(ProtosBytecodeRootNode.NEEDS_EXACT_ACTIVATION));
        });
        System.out.println("PERF038_I_CALLER_NEEDS_CONVERGE=PASS");
    }

    /**
     * PIC_REENTRY_NOT_STRUCTURALLY_VERIFIED: the existing structural
     * introspection (cached-node reflection, as in
     * ProtosI115BDeferredCallerTest) shows that the eligible entry survives a
     * fallback execution, is not duplicated and stays valid; it cannot show a
     * hit of that entry. Hit evidence is pending the BGV analysis.
     */
    @Test
    void terminalMethodSendReentersGuardedPath() throws Exception {
        withCore(module -> {
            evaluate(
                    "eligible: { m: (value) => { value } }\n"
                            + "rejected: { m: (value) => { (value > 0).ifTrue(() => { ^ value + 2 })\n 0 } }\n"
                            + "run: (r, n) => { r.m(n) }\n",
                    "perf038i-reentry-define.protos", module);
            assertTrue(
                    methodRoot("eligible.m\n", module).provablyNonSuspendingBody(),
                    "the eligible fixture must be admissible");
            ProtosObjectValue eligible = assertInstanceOf(
                    ProtosObjectValue.class, module.context().readLocalSlot("eligible").orElseThrow());
            enterCachedTier("run", module);
            assertTerminalSite("run", module);

            assertInteger(1, evaluate("run(eligible, 1)\n", "perf038i-reentry-1.protos", module));
            assertInteger(2, evaluate("run(eligible, 2)\n", "perf038i-reentry-2.protos", module));
            Object installed = assertSingleEntryFor(eligible, module);

            assertInteger(5, evaluate("run(rejected, 3)\n", "perf038i-reentry-3.protos", module));
            assertInteger(6, evaluate("run(rejected, 4)\n", "perf038i-reentry-4.protos", module));
            assertSame(installed, assertSingleEntryFor(eligible, module),
                    "the fallback neither removes nor replaces the entry");

            assertInteger(5, evaluate("run(eligible, 5)\n", "perf038i-reentry-5.protos", module));
            assertInteger(6, evaluate("run(eligible, 6)\n", "perf038i-reentry-6.protos", module));
            assertSame(installed, assertSingleEntryFor(eligible, module),
                    "the restored receiver installs no new entry");
        });
        System.out.println("PERF038_I_GUARDED_REENTRY=PASS");
        System.out.println("PIC_REENTRY_NOT_STRUCTURALLY_VERIFIED");
    }

    private static Object assertSingleEntryFor(Object receiver, ProtosActivation module)
            throws Exception {
        // The own-method selection entries of the send are keyed by Shape,
        // never by receiver identity; exactly one admits the eligible receiver.
        ProtosObjectValue object = assertInstanceOf(ProtosObjectValue.class, receiver);
        List<Object> entries = new ArrayList<>();
        for (Object candidate : getMethodEntries("run", module, "doOwn")) {
            if (((com.oracle.truffle.api.object.Shape) field(candidate, "cachedShape_"))
                    .check(object)) {
                entries.add(candidate);
            }
        }
        assertEquals(1, entries.size(), "exactly one own-method entry admits the receiver");
        Object entry = entries.get(0);
        assertNotNull(field(entry, "getter_"));
        return entry;
    }

    /**
     * Own-method selection is keyed by Shape, not by receiver identity:
     * receivers sharing a Shape keep their own current Closures, and an
     * in-place reassignment is observed.
     */
    @Test
    void ownMethodSelectionIsSharedByShapeAndReadsEachReceiver() throws Exception {
        withCore(module -> {
            evaluate("""
                    first: { pick: (value) => { value + 1 } }
                    second: { pick: (value) => { value + 2 } }
                    run: (r, n) => { r.pick(n) }
                    """, "perf044a8-own-define.protos", module);
            enterCachedTier("run", module);
            for (int i = 0; i < PAST_CACHE_LIMITS; i++) {
                assertInteger(i + 1, evaluate("run(first, " + i + ")\n", "perf044a8-own-1.protos", module));
                assertInteger(i + 2, evaluate("run(second, " + i + ")\n", "perf044a8-own-2.protos", module));
            }
            List<Object> own = getMethodEntries("run", module, "doOwn");
            if (object("first", module).getShape() == object("second", module).getShape()) {
                assertEquals(1, own.size(), "receivers sharing a Shape share one entry");
            } else {
                assertEquals(2, own.size(), "one entry per observed Shape");
            }

            evaluate("first.pick = (value) => { value * 3 }\n", "perf044a8-own-mutate.protos", module);
            assertInteger(15, evaluate("run(first, 5)\n", "perf044a8-own-3.protos", module));
            assertInteger(7, evaluate("run(second, 5)\n", "perf044a8-own-4.protos", module));
        });
        System.out.println("PERF044_A8_OWN_SHAPE_SELECTION=PASS");
    }

    /**
     * Inherited selection is shared by receivers with the same Shape and the
     * same direct parent, never across distinct parents; reassignment,
     * shadowing, removal and recreation are observed.
     */
    @Test
    void inheritedSelectionIsSharedPerParentAndObservesMutation() throws Exception {
        withCore(module -> {
            evaluate("""
                    base: {
                        value: 0
                        pick: (delta) => { value + delta }
                    }
                    left: base { value: 1 }
                    right: base { value: 2 }
                    otherBase: { pick: (delta) => { delta + 100 } }
                    stranger: otherBase { value: 1 }
                    run: (r, n) => { r.pick(n) }
                    """, "perf044a8-inherited-define.protos", module);
            enterCachedTier("run", module);
            for (int i = 0; i < PAST_CACHE_LIMITS; i++) {
                assertInteger(1 + i, evaluate("run(left, " + i + ")\n", "perf044a8-inh-1.protos", module));
                assertInteger(2 + i, evaluate("run(right, " + i + ")\n", "perf044a8-inh-2.protos", module));
                assertInteger(100 + i, evaluate("run(stranger, " + i + ")\n", "perf044a8-inh-3.protos", module));
            }
            ProtosObjectValue base = object("base", module);
            ProtosObjectValue otherBase = object("otherBase", module);
            int fromBase = 0;
            int fromOther = 0;
            for (Object entry : getMethodEntries("run", module, "doInherited")) {
                ProtosValueLookup.StructuralSelection selection = inheritedSelection(entry);
                if (selection.origin() == base) {
                    assertSame(base, selection.home(), "the real owner is the methodHome");
                    fromBase++;
                } else if (selection.origin() == otherBase) {
                    fromOther++;
                }
            }
            if (object("left", module).getShape() == object("right", module).getShape()) {
                assertEquals(1, fromBase, "same Shape and parent share one selection");
            }
            assertEquals(1, fromOther, "a distinct parent never shares the base selection");

            // In-place reassignment of the inherited slot.
            evaluate("base.pick = (delta) => { (value * 10) + delta }\n",
                    "perf044a8-inh-mutate.protos", module);
            assertInteger(11, evaluate("run(left, 1)\n", "perf044a8-inh-4.protos", module));
            assertInteger(21, evaluate("run(right, 1)\n", "perf044a8-inh-5.protos", module));
            assertInteger(101, evaluate("run(stranger, 1)\n", "perf044a8-inh-6.protos", module));

            // Shadowing in one receiver only.
            ProtosClosureValue shadow = assertInstanceOf(ProtosClosureValue.class,
                    evaluate("(delta) => { delta + 1000 }\n", "perf044a8-inh-shadow.protos", module));
            object("left", module).createLocalSlot("pick", shadow);
            assertInteger(1001, evaluate("run(left, 1)\n", "perf044a8-inh-7.protos", module));
            assertInteger(21, evaluate("run(right, 1)\n", "perf044a8-inh-8.protos", module));

            // Removal, then recreation, of the selected slot.
            ProtosObjectValue slotNotFound = errorPrototype("SlotNotFound", module);
            base.removeLocalSlot("pick");
            assertSame(slotNotFound, errorParent(
                    () -> evaluate("run(right, 1)\n", "perf044a8-inh-9.protos", module)));
            ProtosClosureValue recreated = assertInstanceOf(ProtosClosureValue.class,
                    evaluate("(delta) => { delta - 1 }\n", "perf044a8-inh-recreate.protos", module));
            base.createLocalSlot("pick", recreated);
            assertInteger(4, evaluate("run(right, 5)\n", "perf044a8-inh-10.protos", module));
            assertInteger(1005, evaluate("run(left, 5)\n", "perf044a8-inh-11.protos", module));
        });
        System.out.println("PERF044_A8_INHERITED_SHARED_SELECTION=PASS");
    }

    /** An inherited selection keeps the real owner as methodHome for super and this. */
    @Test
    void inheritedSelectionKeepsTheRealMethodHome() throws Exception {
        withCore(module -> {
            evaluate("""
                    top: { read: (d) => { d + 1 } }
                    middle: top {
                        read: (d) => { super.read(d) + 10 }
                        self: (ignored) => { this }
                    }
                    first: middle { tag: 1 }
                    second: middle { tag: 2 }
                    readOf: (r, d) => { r.read(d) }
                    selfOf: (r) => { r.self(0) }
                    """, "perf044a8-home-define.protos", module);
            enterCachedTier("readOf", module);
            enterCachedTier("selfOf", module);
            Object first = object("first", module);
            Object second = object("second", module);
            for (int i = 0; i < PAST_CACHE_LIMITS; i++) {
                assertInteger(11 + i, evaluate("readOf(first, " + i + ")\n", "perf044a8-home-1.protos", module));
                assertInteger(11 + i, evaluate("readOf(second, " + i + ")\n", "perf044a8-home-2.protos", module));
                assertSame(first, evaluate("selfOf(first)\n", "perf044a8-home-3.protos", module));
                assertSame(second, evaluate("selfOf(second)\n", "perf044a8-home-4.protos", module));
            }
        });
        System.out.println("PERF044_A8_INHERITED_METHOD_HOME=PASS");
    }

    /** A cached site keeps the ordinary Error and SlotNotFound selection failures. */
    @Test
    void cachedSelectionKeepsSelectionErrors() throws Exception {
        withCore(module -> {
            evaluate("""
                    good: { pick: (value) => { value } }
                    holder: { pick: 5 }
                    heir: holder { tag: 1 }
                    plain: { other: 1 }
                    run: (r, n) => { r.pick(n) }
                    """, "perf044a8-errors-define.protos", module);
            enterCachedTier("run", module);
            for (int i = 0; i < PAST_CACHE_LIMITS; i++) {
                assertInteger(i, evaluate("run(good, " + i + ")\n", "perf044a8-errors-1.protos", module));
            }
            ProtosObjectValue error = errorPrototype("Error", module);
            ProtosObjectValue slotNotFound = errorPrototype("SlotNotFound", module);
            for (int i = 0; i < 3; i++) {
                assertSame(error, errorParent(
                        () -> evaluate("run(holder, 1)\n", "perf044a8-errors-2.protos", module)),
                        "an own non-Closure selection is Error");
                assertSame(error, errorParent(
                        () -> evaluate("run(heir, 1)\n", "perf044a8-errors-3.protos", module)),
                        "an inherited non-Closure selection is Error");
                assertSame(slotNotFound, errorParent(
                        () -> evaluate("run(plain, 1)\n", "perf044a8-errors-4.protos", module)),
                        "a missing selector is SlotNotFound");
            }
            assertInteger(3, evaluate("run(good, 3)\n", "perf044a8-errors-5.protos", module));
        });
        System.out.println("PERF044_A8_SELECTION_ERRORS=PASS");
    }

    /**
     * Represented receivers: one entry per family and Prelude, never per
     * value; a polymorphic site returning to an earlier family reuses its
     * entry.
     */
    @Test
    void representedReceiversShareOneSelectionPerFamily() throws Exception {
        withCore(module -> {
            evaluate("same: (x, y) => { x.match(y) }\n", "perf044a8-repr-define.protos", module);
            enterCachedTier("same", module);
            for (int i = 0; i < PAST_CACHE_LIMITS; i++) {
                assertSame(Boolean.TRUE, evaluate("same(" + i + ", " + i + ")\n", "perf044a8-repr-1.protos", module));
                assertSame(Boolean.FALSE, evaluate("same(" + i + ", " + (i + 1) + ")\n", "perf044a8-repr-2.protos", module));
            }
            assertEquals(1, getMethodEntries("same", module, "doLong").size(),
                    "every SmallInteger value shares one entry");
            for (int i = 0; i < PAST_CACHE_LIMITS; i++) {
                assertSame(Boolean.TRUE, evaluate("same(" + i + ".5, " + i + ".5)\n", "perf044a8-repr-3.protos", module));
                assertSame(Boolean.FALSE, evaluate("same(" + i + ".5, " + i + ".25)\n", "perf044a8-repr-4.protos", module));
                assertSame(Boolean.TRUE, evaluate("same(\"s" + i + "\", \"s" + i + "\")\n", "perf044a8-repr-5.protos", module));
                assertSame(Boolean.FALSE, evaluate("same(\"s" + i + "\", \"t" + i + "\")\n", "perf044a8-repr-6.protos", module));
            }
            int primitiveFloat = getMethodEntries("same", module, "doDouble").size();
            int representedFloat = getMethodEntries("same", module, "doFloatValue").size();
            assertTrue(primitiveFloat <= 1 && representedFloat <= 1
                            && primitiveFloat + representedFloat >= 1,
                    "every Float value shares one entry per Float representation");
            assertEquals(1, getMethodEntries("same", module, "doString").size(),
                    "every String value shares one entry");
            for (int i = 0; i < 3; i++) {
                assertSame(Boolean.TRUE, evaluate("same(true, true)\n", "perf044a8-repr-7.protos", module));
                assertSame(Boolean.FALSE, evaluate("same(false, true)\n", "perf044a8-repr-8.protos", module));
                assertSame(Boolean.TRUE, evaluate("same(null, null)\n", "perf044a8-repr-9.protos", module));
                assertSame(Boolean.FALSE, evaluate("same(null, 1)\n", "perf044a8-repr-10.protos", module));
            }
            assertEquals(1, getMethodEntries("same", module, "doRootDelegated").size(),
                    "Boolean and null share the root Object selection");
            // Back to the first family after polymorphism.
            assertSame(Boolean.TRUE, evaluate("same(41, 41)\n", "perf044a8-repr-11.protos", module));
            assertSame(Boolean.FALSE, evaluate("same(41, 42)\n", "perf044a8-repr-12.protos", module));
            assertEquals(1, getMethodEntries("same", module, "doLong").size(),
                    "returning to SmallInteger reuses its entry");
        });
        System.out.println("PERF044_A8_REPRESENTED_SELECTION=PASS");
    }

    /**
     * A selected source method of a represented receiver binds the actual
     * receiver: different String and SmallInteger values reach their own
     * equality result through the shared inherited Object.match.
     */
    @Test
    void representedSelectionBindsTheActualReceiver() throws Exception {
        withCore(module -> {
            evaluate("""
                    probe: (x) => { x.match(x) }
                    other: (x, y) => { x.match(y) }
                    """, "perf044a8-actual-define.protos", module);
            enterCachedTier("probe", module);
            for (int i = 0; i < PAST_CACHE_LIMITS; i++) {
                assertSame(Boolean.TRUE, evaluate("probe(\"v" + i + "\")\n", "perf044a8-actual-1.protos", module));
                assertSame(Boolean.TRUE, evaluate("probe(" + (i * 7) + ")\n", "perf044a8-actual-2.protos", module));
                assertSame(Boolean.FALSE, evaluate("other(\"v" + i + "\", \"v" + (i + 1) + "\")\n", "perf044a8-actual-3.protos", module));
            }
        });
        System.out.println("PERF044_A8_REPRESENTED_ACTUAL_RECEIVER=PASS");
    }

    /**
     * Distinct selectors never share a selection: the numeric overload
     * child is reused per selector and each GetMethod entry guards its
     * selector, so alternating custom operators keep their own behavior.
     */
    @Test
    void distinctSelectorsKeepDistinctSelections() throws Exception {
        withCore(module -> {
            evaluate("""
                    Box: {
                        value: 0
                        plus: (other) => { value + other + 1000 }
                        minus: (other) => { value - other - 1000 }
                    }.alias("plus", "+").alias("minus", "-")
                    box: Box { value: 5 }
                    plus: (r, n) => { r + n }
                    minus: (r, n) => { r - n }
                    """, "perf044a8-selectors-define.protos", module);
            enterCachedTier("plus", module);
            enterCachedTier("minus", module);
            for (int i = 0; i < PAST_CACHE_LIMITS; i++) {
                assertInteger(1005 + i, evaluate("plus(box, " + i + ")\n", "perf044a8-selectors-1.protos", module));
                assertInteger(-995 - i, evaluate("minus(box, " + i + ")\n", "perf044a8-selectors-2.protos", module));
                assertInteger(i + 1, evaluate("plus(" + i + ", 1)\n", "perf044a8-selectors-3.protos", module));
            }
        });
        System.out.println("PERF044_A8_DISTINCT_SELECTORS=PASS");
    }

    @Test
    void explicitMethodExtractionRemainsSeparate() throws Exception {
        withCore(module -> {
            List<Object> results =
                    elements(evaluate(
                            "receiver: {\n    value: 5\n    add: (delta) => { value + delta }\n}\n"
                                    + "immediate: (n) => { receiver.add(n) }\n"
                                    + "immediate(1)\nimmediate(2)\n"
                                    + "first: receiver.add\n"
                                    + "second: receiver.add\n"
                                    + "alias: first\n"
                                    + "[immediate(3), first, second, first === second,\n"
                                    + " first(4), alias === first, immediate(4)]\n",
                            "perf038i-extraction.protos", module));
            assertEquals("8", integerValue(results.get(0)));
            ProtosClosureValue first = assertInstanceOf(ProtosClosureValue.class, results.get(1));
            // CALLABLES.md section 11: every extraction is a fresh Closure;
            // only an alias of one extraction keeps its identity.
            assertNotSame(first, assertInstanceOf(ProtosClosureValue.class, results.get(2)));
            assertSame(Boolean.FALSE, results.get(3));
            assertEquals("9", integerValue(results.get(4)), "the extraction binds the receiver");
            assertSame(Boolean.TRUE, results.get(5));
            assertEquals("9", integerValue(results.get(6)));
        });
        System.out.println("PERF038_I_EXPLICIT_EXTRACTION=PASS");
    }

    private static ProtosClosureValue nativeClosure(
            com.guillermomolina.protos.runtime.callables.ProtosNativeClosureBody body) {
        return ProtosClosureValue.suspensionCapableNativeClosure(body, body);
    }

    private static ProtosClosureValue counter(AtomicInteger count) {
        return nativeClosure(
                (activation, supplied) -> {
                    count.incrementAndGet();
                    return ProtosNullValue.INSTANCE;
                });
    }

    /** The root of the method an explicit extraction expression selects. */
    private static ProtosBytecodeRootNode methodRoot(String extraction, ProtosActivation module) {
        ProtosClosureValue method = assertInstanceOf(
                ProtosClosureValue.class,
                evaluate(extraction, "perf038i-extract-root.protos", module));
        return method.executionPlan().orElseThrow().bytecodeActivationRootForTesting();
    }

    /** As in ProtosI115BDeferredCallerTest: the next entry uses the cached tier. */
    private static void enterCachedTier(String function, ProtosActivation module) {
        functionRoot(function, module).getBytecodeNode().setUncachedThreshold(0);
    }

    private static ProtosBytecodeRootNode functionRoot(String function, ProtosActivation module) {
        ProtosClosureValue closure = assertInstanceOf(
                ProtosClosureValue.class,
                module.context().readLocalSlot(function).orElseThrow());
        return closure.executionPlan().orElseThrow().bytecodeActivationRootForTesting();
    }

    private static void assertTerminalSite(String function, ProtosActivation module) {
        List<String> signature = names(fullSignature(functionRoot(function, module)));
        assertTrue(signature.stream().anyMatch(name -> name.startsWith("c.GetMethod"))
                        && signature.stream().anyMatch(name -> name.startsWith("c.CallBinaryMethod")),
                () -> function + " must contain the GetMethod/CallBinaryMethod send: " + signature);
        assertFalse(signature.contains("c.TryDirectSendOne"),
                () -> function + " must not contain TryDirectSendOne: " + signature);
    }

    /**
     * Structural, test-only view of the {@code guardedSource} entries of the
     * single InvokeMethodOneTail site of {@code function} (the cached-node
     * reflection of ProtosI115BDeferredCallerTest.tierEntries).
     */
    /** The direct source-send tier of the send's CallBinaryMethod: admitted callees only. */
    private static List<Object> tierEntries(String function, ProtosActivation module)
            throws Exception {
        return cachedEntries(function, module, "CallBinaryMethod_Node", "callDirectSourceSend_cache");
    }

    /** The entries of one selection specialization of the send's single GetMethod. */
    private static List<Object> getMethodEntries(
            String function, ProtosActivation module, String specialization) throws Exception {
        // The generated cache field drops the specialization's "do" prefix.
        String name = Character.toLowerCase(specialization.charAt(2)) + specialization.substring(3);
        return cachedEntries(function, module, "GetMethod_Node", name + "_cache");
    }

    /** The shared inherited selection of a {@code doInherited} entry. */
    private static ProtosValueLookup.StructuralSelection inheritedSelection(Object entry)
            throws Exception {
        return assertInstanceOf(
                ProtosValueLookup.StructuralSelection.class, field(entry, "selection_"));
    }

    private static ProtosObjectValue object(String name, ProtosActivation module) {
        return assertInstanceOf(
                ProtosObjectValue.class, module.context().readLocalSlot(name).orElseThrow());
    }

    private static ProtosObjectValue errorPrototype(String name, ProtosActivation module) {
        return assertInstanceOf(
                ProtosObjectValue.class,
                evaluate(name + "\n", "perf044a8-error-prototype.protos", module));
    }

    private static ProtosObjectValue errorParent(Runnable send) {
        ProtosSignalException failure = assertThrows(ProtosSignalException.class, send::run);
        return assertInstanceOf(
                ProtosObjectValue.class, failure.error().parent().orElseThrow());
    }

    private static List<Object> cachedEntries(
            String function, ProtosActivation module, String nodeName, String cacheField)
            throws Exception {
        Object bytecode = functionRoot(function, module).getBytecodeNode();
        Node[] cachedNodes = (Node[]) field(bytecode, "cachedNodes_");
        List<Object> entries = new ArrayList<>();
        int sites = 0;
        for (Node node : cachedNodes) {
            if (node == null || !node.getClass().getSimpleName().equals(nodeName)) {
                continue;
            }
            sites++;
            for (Object entry = field(node, cacheField);
                    entry != null;
                    entry = field(entry, "next_")) {
                entries.add(entry);
            }
        }
        assertEquals(1, sites, "expected exactly one " + nodeName + " site in " + function);
        return entries;
    }

    private static Object field(Object owner, String name) throws ReflectiveOperationException {
        for (Class<?> type = owner.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(owner);
            } catch (NoSuchFieldException absent) {
                // Continue with the superclass.
            }
        }
        throw new NoSuchFieldException(owner.getClass().getName() + "." + name);
    }

    private static Census census(String program, String name) {
        return census(program, name, "receiver.identity", "value");
    }

    /**
     * The complete FULL census of every root of the program's group,
     * including prologue, epilogue, tags and returns, without filtering.
     * {@code runPrefix} and {@code methodPrefix} select the caller and the
     * method roots by their source text.
     */
    private static Census census(
            String program, String name, String runPrefix, String methodPrefix) {
        Source source = Source.newBuilder(ProtosLanguage.ID, program, name)
                .mimeType(ProtosLanguage.MIME_TYPE)
                .build();
        RootCallTarget target = assertInstanceOf(
                RootCallTarget.class,
                ProtosLanguageContext.current().parsePublic(source));
        ProtosBytecodeRootNode module =
                assertInstanceOf(ProtosBytecodeRootNode.class, target.getRootNode());
        // BytecodeConfig.DEFAULT plus source information, as in the I112 census.
        module.getRootNodes().ensureSourceInformation();

        List<ProtosBytecodeRootNode> roots =
                module.getRootNodes().getNodes().stream()
                        .map(ProtosBytecodeRootNode.class::cast)
                        .toList();
        List<List<String>> signatures = new ArrayList<>();
        int total = 0;
        int runIndex = -1;
        int methodIndex = -1;
        StringBuilder report = new StringBuilder();
        report.append("PERF038_I_CASE=").append(name).append('\n');
        for (int rootIndex = 0; rootIndex < roots.size(); rootIndex++) {
            ProtosBytecodeRootNode root = roots.get(rootIndex);
            List<String> signature = fullSignature(root);
            signatures.add(signature);
            total += signature.size();
            String text = root.getSourceSection() == null
                    ? ""
                    : root.getSourceSection().getCharacters().toString().strip();
            if (rootIndex > 0 && runIndex < 0 && text.startsWith(runPrefix)) {
                runIndex = rootIndex;
            }
            if (rootIndex > 0 && methodIndex < 0 && text.startsWith(methodPrefix)) {
                methodIndex = rootIndex;
            }
            report.append("PERF038_I_ROOT[").append(rootIndex).append("]=")
                    .append(root.getSourceSection()).append('\n');
            report.append("PERF038_I_ROOT_FULL[").append(rootIndex).append("]=")
                    .append(signature.size()).append('\n');
            for (int index = 0; index < signature.size(); index++) {
                report.append("PERF038_I_INSTRUCTION[").append(rootIndex)
                        .append("][").append(index).append("]=")
                        .append(signature.get(index)).append('\n');
            }
        }
        assertSame(module, roots.get(0), "The first group root must be the module");
        assertEquals(3, roots.size(), "Module, method and run roots only:\n" + report);
        assertTrue(runIndex > 0, "run root not found:\n" + report);
        assertTrue(methodIndex > 0, "method root not found:\n" + report);
        return new Census(module, roots, signatures, total, runIndex, methodIndex,
                report.toString());
    }

    private static List<String> fullSignature(ProtosBytecodeRootNode root) {
        List<String> entries = new ArrayList<>();
        for (Instruction instruction : root.getBytecodeNode().getInstructionsAsList()) {
            StringBuilder entry = new StringBuilder(ProtosInstructionNames.of(instruction));
            for (Instruction.Argument argument : instruction.getArguments()) {
                entry.append(' ')
                        .append(argument.getName())
                        .append(':')
                        .append(argument.getKind());
                if (argument.getKind() == Instruction.Argument.Kind.CONSTANT) {
                    Object constant = argument.asConstant();
                    entry.append('=').append(
                            constant instanceof String || constant instanceof Number
                                    ? constant
                                    : constant == null ? "host-null"
                                            : constant.getClass().getSimpleName());
                }
            }
            entries.add(entry.toString());
        }
        return List.copyOf(entries);
    }

    private static List<String> names(List<String> signature) {
        return signature.stream().map(entry -> entry.split(" ", 2)[0]).toList();
    }

    private record Census(
            ProtosBytecodeRootNode module,
            List<ProtosBytecodeRootNode> roots,
            List<List<String>> signatures,
            int total,
            int runIndex,
            int methodIndex,
            String report) {
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

    private static Object evaluate(String characters, String name, ProtosActivation activation) {
        Source source =
                Source.newBuilder(ProtosLanguage.ID, characters, name)
                        .mimeType(ProtosLanguage.MIME_TYPE)
                        .build();
        CallTarget target = ProtosLanguageContext.current().parsePublic(source);
        Object result = target.call(activation);
        assertNotNull(result);
        return result;
    }

    private static List<Object> elements(Object array) {
        return assertInstanceOf(ProtosArrayValue.class, array).indexedSnapshot();
    }

    private static void assertInteger(long expected, Object result) {
        assertEquals(Long.toString(expected), integerValue(result));
    }

    private static String integerValue(Object value) {
        return ProtosNumericValueSupport.integerDecimalText(value);
    }
}
