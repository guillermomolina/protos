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

import com.oracle.truffle.api.debug.DebuggerSession;
import com.oracle.truffle.api.debug.Debugger;
import com.guillermomolina.protos.parser.ProtosParser;
import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.guillermomolina.protos.runtime.ProtosNumericValueSupport;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
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
 * I096-A (PLAT057-B) focal evidence: an ordinary C0/C1 call whose receiver is
 * an immediate Closure literal runs that literal's body inline in the caller's
 * Bytecode root, after ordinary {@code call} selection proved the canonical
 * standard selection of the literal's own body; every non-admitted shape and
 * a non-canonical {@code call} selection keep the physical callee root. No
 * test mutates the frozen global root.
 *
 * <p>PLAT067-B: every Context uses the one proof-optimized lowering. An
 * admitted root holds only the inline region (its run-time admission and the
 * carrier's physical fallback), with no retained ordinary call, whether or
 * not a debugger is attached; an attached debugger never recreates the
 * eliminated callee frame.
 *
 * <p>Inline execution is observed through the guest CallTarget stack seen by a
 * native {@code probe}: an admitted body leaves only {@code run} over the
 * module root (depth 2); a physical callee adds its own root.
 *
 * <p>I096-B1 extends the evidence to ordinary C0/C1 calls through a lexical
 * binding whose candidate body is known statically: the candidate runs inline
 * only when the run-time selection proves it, with the effective instance's
 * own captures; every other value takes the prepared physical fallback.
 *
 * <p>The I096 coverage completion extends both forms to every exact
 * positional arity up to the expansion budget, and binding calls to operand
 * positions of the same activation (compound expressions, arguments of other
 * calls), with the same run-time admission and fallback.
 *
 * <p>The nested-Closure extension admits bodies that create Closures: they
 * run inline over the callee's own activation (never frame-native storage),
 * nested Closures keep their own CallTargets and capture that activation by
 * reference, including after escaping, and the group gains no duplicate root.
 */
final class ProtosI096InlineClosureLiteralCallTest {
    private static final Path CORE = Path.of("protos", "lib", "core");

    private static final LanguageReference<ProtosLanguage> LANGUAGE_REF =
            LanguageReference.create(ProtosLanguage.class);

    private static final int INLINE_DEPTH = 2;


    @Test
    void i108FrameNativeZeroUsesSemanticInvocation()
            throws Exception {
        String source = """
                run: () => {
                    target: () => { 7 }
                    ((fn) => {
                        probe()
                        fn()
                    })(target)
                }
                run()
                """;

        Execution normal = execute(false, source);
        assertInteger(7, normal.value());
        assertEquals(1, normal.probe().calls.get());

        List<String> names =
                instructionNames(normal.innermostRoot());

        assertTrue(
                count(names, "CallNilaryMethod") == 2,
                names::toString);

        assertTrue(
                position(names, "CopySuppliedArguments") >= 0,
                names::toString);

        assertTrue(
                position(names, "IsContinuation") >= 0,
                names::toString);

        assertFalse(
                names.stream().anyMatch(
                        name -> name.equals("PrepareInlineClosureCall")
                                || name.endsWith(".PrepareInlineClosureCall")),
                names::toString);

        assertInteger(7, execute(true, source).value());

        String overridden = """
                run: () => {
                    target: () => { 7 }
                    target.call: () => { 11 }
                    ((fn) => {
                        fn()
                    })(target)
                }
                run()
                """;

        assertInteger(11, execute(false, overridden).value());
        assertInteger(11, execute(true, overridden).value());
    }


    /**
     * I108: these inner calls are emitted inside actual
     * frame-native I096 callback regions. The preceding probe
     * captures the enclosing run root without observing the
     * inline callback activation.
     */
    @Test
    void i108FrameNativeOneAndManyUseSemanticInvocation()
            throws Exception {

        String one = """
                run: () => {
                    target: (x) => { x }
                    ((fn) => {
                        probe()
                        fn(41)
                    })(target)
                }
                run()
                """;

        Execution oneNormal = execute(false, one);
        assertInteger(41, oneNormal.value());
        assertEquals(1, oneNormal.probe().calls.get());

        List<String> oneNames =
                instructionNames(oneNormal.innermostRoot());

        assertTrue(
                count(oneNames, "CallUnaryMethod") == 1,
                oneNames::toString);

        assertTrue(
                position(oneNames, "IsContinuation") >= 0,
                oneNames::toString);

        assertTrue(
                position(oneNames, "CopySuppliedArguments") >= 0,
                oneNames::toString);

        assertFalse(
                oneNames.stream().anyMatch(
                        name -> name.equals("PrepareInlineClosureCallOne")
                                || name.endsWith(
                                        ".PrepareInlineClosureCallOne")),
                oneNames::toString);

        assertInteger(41, execute(true, one).value());

        String oneOverride = """
                run: () => {
                    probe()
                    target: (x) => { x }
                    target.call: (x) => { 91 }
                    ((fn) => {
                        fn(41)
                    })(target)
                }
                run()
                """;

        assertInteger(91, execute(false, oneOverride).value());
        assertInteger(91, execute(true, oneOverride).value());

        String many = """
                run: () => {
                    target: (a, b) => { 42 }
                    ((fn) => {
                        probe()
                        fn(4, 2)
                    })(target)
                }
                run()
                """;

        Execution manyNormal = execute(false, many);
        assertInteger(42, manyNormal.value());
        assertEquals(1, manyNormal.probe().calls.get());

        List<String> manyNames =
                instructionNames(manyNormal.innermostRoot());

        assertTrue(
                count(manyNames, "CallBinaryMethod") == 1,
                manyNames::toString);

        assertTrue(
                position(manyNames, "CopySuppliedArguments") >= 0,
                manyNames::toString);

        assertTrue(
                position(manyNames, "IsContinuation") >= 0,
                manyNames::toString);

        assertFalse(
                manyNames.stream().anyMatch(
                        name -> name.equals(
                                "PrepareInlineClosureCallArguments")
                                || name.endsWith(
                                        ".PrepareInlineClosureCallArguments")),
                manyNames::toString);

        assertInteger(42, execute(true, many).value());

        String manyOverride = """
                run: () => {
                    probe()
                    target: (a, b) => { 42 }
                    target.call: (a, b) => { 92 }
                    ((fn) => {
                        fn(4, 2)
                    })(target)
                }
                run()
                """;

        assertInteger(92, execute(false, manyOverride).value());
        assertInteger(92, execute(true, manyOverride).value());

        String spread = """
                run: () => {
                    target: (a, b) => { 42 }
                    values: Array(4, 2)
                    ((fn, supplied) => {
                        probe()
                        fn(...supplied)
                    })(target, values)
                }
                run()
                """;

        Execution spreadNormal = execute(false, spread);
        assertInteger(42, spreadNormal.value());

        List<String> spreadNames =
                instructionNames(spreadNormal.innermostRoot());

        assertTrue(
                count(spreadNames, "CallVarargsMethod") == 1,
                spreadNames::toString);

        assertTrue(
                position(spreadNames, "SnapshotClosureCallArguments") >= 0,
                spreadNames::toString);

        assertTrue(
                position(spreadNames, "AppendSpreadSuppliedArgument") >= 0,
                spreadNames::toString);

        assertInteger(42, execute(true, spread).value());

        String spreadOverride = """
                run: () => {
                    probe()
                    target: (a, b) => { 42 }
                    target.call: (a, b) => { 93 }
                    values: Array(4, 2)
                    ((fn, supplied) => {
                        fn(...supplied)
                    })(target, values)
                }
                run()
                """;

        assertInteger(93, execute(false, spreadOverride).value());
        assertInteger(93, execute(true, spreadOverride).value());
    }


    /**
     * I108 frame-native Call0: a source target that may suspend
     * must return a prepared invocation rather than attempting
     * direct Java control. Resumption must not replay the body.
     *
     * The callback's body contains a Call0 through its parameter
     * and needs no persistent frame authority of its own.
     */
    @Test
    void i108FrameNativeSuspendingCallZeroPreservesComposition()
            throws Exception {
        String source = """
                run: () => {
                    step: (() => { 1 }).future()
                    visits: 0
                    target: () => {
                        visits = visits + 1
                        40 + step.value()
                    }
                    result: ((fn) => {
                        fn()
                    })(target)
                    result * 10 + visits
                }
                run()
                """;

        ProtosExecutionOutcome outcome =
                ProtosTestExecutionSupport.execute(
                        "i108-frame-native-call0-suspension.protos",
                        source,
                        new ProtosCoreBootstrap()
                                .bootstrap(CORE)
                                .newModuleActivation());

        assertEquals(
                ProtosExecutionOutcome.State.COMPLETED,
                outcome.state(),
                () -> "state=" + outcome.state()
                        + ", error=" + outcome.error());

        // 41 is the resumed value; visits must remain exactly 1.
        assertInteger(411, outcome.value());

        try (Context context =
                Context.newBuilder(ProtosLanguage.ID).build()) {
            context.initialize(ProtosLanguage.ID);
            context.enter();
            try {
                ProtosBytecodeRootNode module =
                        lowerRoot(source);

                module.getRootNodes().ensureComplete();

                assertTrue(
                        groupRoots(module).stream().anyMatch(
                                root -> position(
                                        instructionNames(root),
                                        "CallNilaryMethod") >= 0),
                        "The inner Call0 must be emitted as a "
                                + "Bytecode DSL call operation");
            } finally {
                context.leave();
            }
        }
    }


    /**
     * I108: generic-first must not poison a definition-keyed
     * frame-native PIC. Five distinct definitions exceed its
     * three-entry specialization limit.
     *
     * Every invocation enters the same apply CallTarget and
     * therefore the same inner semantic call site.
     */
    @Test
    void i108FrameNativeGenericFirstAndPicSaturation()
            throws Exception {
        String[][] variants = {
            {
                "Call0", "()",
                "chosen()", "CallNilaryMethod"
            },
            {
                "Call1", "(a)",
                "chosen(4)", "CallUnaryMethod"
            },
            {
                "CallN", "(a, b)",
                "chosen(4, 2)", "CallBinaryMethod"
            },
            {
                "Call3", "(a, b, c)",
                "chosen(4, 2, 1)", "CallTernaryMethod"
            },
            {
                "Call4", "(a, b, c, d)",
                "chosen(4, 2, 1, 0)", "CallQuaternaryMethod"
            },
            {
                "Spread", "(a, b)",
                "chosen(...values)",
                "CallVarargsMethod"
            }
        };

        for (String[] variant : variants) {
            String label = variant[0];
            String parameters = variant[1];
            String call = variant[2];
            String operation = variant[3];

            String source = """
                    f0: @@PARAMETERS@@ => { 10 }
                    f1: @@PARAMETERS@@ => { 11 }
                    f2: @@PARAMETERS@@ => { 12 }
                    f3: @@PARAMETERS@@ => { 13 }
                    f4: @@PARAMETERS@@ => { 14 }

                    apply: (fn) => {
                        values: Array(4, 2)
                        ((chosen) => {
                            probe()
                            @@CALL@@
                        })(fn)
                    }

                    f0.call: @@PARAMETERS@@ => { 90 }
                    v0: apply(f0)

                    f0.removeSlot("call")
                    v1: apply(f0)

                    v2: apply(f1)
                    v3: apply(f2)
                    v4: apply(f3)
                    v5: apply(f4)

                    f0.call: @@PARAMETERS@@ => { 91 }
                    v6: apply(f0)

                    f0.removeSlot("call")
                    v7: apply(f0)

                    v0 + v1 + v2 + v3 + v4 + v5 + v6 + v7
                    """
                    .replace("@@PARAMETERS@@", parameters)
                    .replace("@@CALL@@", call);

            /*
             * First observation: overridden call (90).
             * Then canonical 10, 11, 12, 13, 14.
             * After saturation: overridden 91, canonical 10.
             */
            Execution normal = execute(false, source);

            System.out.println(
                    "CALL_PIC_RESULT_" + label
                            + " value="
                            + ProtosNumericValueSupport.integerDecimalText(
                                    normal.value())
                            + " probes=" + normal.probe().calls.get()
                            + " stacks=" + normal.probe().stacks.size());

            assertInteger(251, normal.value());
            assertEquals(
                    8, normal.probe().calls.get(), label);

            ProtosBytecodeRootNode caller =
                    normal.probe().stacks.get(0).stream()
                            .filter(ProtosBytecodeRootNode.class::isInstance)
                            .map(ProtosBytecodeRootNode.class::cast)
                            .findFirst()
                            .orElseThrow();

            List<String> instructions =
                    instructionNames(caller);

            int originalRootIndex =
                    caller.getRootNodes().getNodes().indexOf(caller);

            assertTrue(
                    originalRootIndex >= 0,
                    label + ": initial root must belong to its parse group");

            assertSame(
                    caller,
                    caller.getRootNodes().getNode(originalRootIndex),
                    label + ": registered source root");

            /*
             * The callback root contains exactly one probe() call
             * and one selected chosen(...) call site. For Call0
             * both are nilary; other arities have distinct operations.
             */
            assertEquals(
                    label.equals("Call0") ? 2 : 1,
                    count(instructions, operation),
                    () -> label + ": " + instructions);

            assertEquals(
                    label.equals("Call0") ? 2 : 1,
                    count(instructions, "CallNilaryMethod"),
                    label + ": one probe and the selected target");

            assertEquals(
                    label.equals("Spread") ? 2 : 1,
                    count(instructions, "ResumeDispatchedContinuation"),
                    () -> label + ": " + instructions);

            assertEquals(
                    label.equals("Spread") ? 2 : 1,
                    count(instructions, "IsContinuation"),
                    () -> label + ": " + instructions);

            assertFalse(
                    instructions.stream().anyMatch(name ->
                            name.contains("InvokeInlineClosure")
                                    || name.contains("IsPreparedClosureInvocation")
                                    || name.contains("BindInlineClosureFrameParameter")
                                    || name.contains("AdmitsInlineClosureLiteralCall")
                                    || name.contains("MaterializeInlineCallbackActivation")),
                    instructions::toString);

            /*
             * Every invocation originates from the same bytecode
             * definition and call site. Truffle may clone the root
             * for call-site-specific profiling after specialization.
             * Such clones are not additional parsed definitions.
             */
            for (List<RootNode> stack : normal.probe().stacks) {
                ProtosBytecodeRootNode observedCaller =
                        stack.stream()
                                .filter(ProtosBytecodeRootNode.class::isInstance)
                                .map(ProtosBytecodeRootNode.class::cast)
                                .findFirst()
                                .orElseThrow();

                assertSame(
                        caller.getRootNodes(),
                        observedCaller.getRootNodes(),
                        label + ": source bytecode group identity");

                if (observedCaller != caller) {
                    assertEquals(
                            -1,
                            caller.getRootNodes()
                                    .getNodes()
                                    .indexOf(observedCaller),
                            label + ": cloned root is not a new definition");
                }

                assertEquals(
                        instructions,
                        instructionNames(observedCaller),
                        () -> label
                                + ": cloned root must preserve the call site");
            }

            if (label.equals("Spread")) {
                assertTrue(
                        position(
                                instructions,
                                "SnapshotClosureCallArguments") >= 0,
                        instructions::toString);

                assertTrue(
                        position(
                                instructions,
                                "AppendSpreadSuppliedArgument") >= 0,
                        instructions::toString);
            }

            Execution debug = execute(true, source);

            assertInteger(251, debug.value());
            assertEquals(
                    8, debug.probe().calls.get(),
                    label + ": attached-debugger parity");
        }
    }

    /**
     * A suspendible Call1/CallN/spread target must use prepared
     * Bytecode composition. The target increments visits once,
     * then observes an asynchronous value.
     *
     * If the invocation is replayed after suspension, visits
     * becomes two and the assertion fails.
     */
    @Test
    void i108FrameNativeOneAndManyResumeWithoutReplay()
            throws Exception {
        String[][] variants = {
            {
                "call1", "(a)",
                "fn(4)",
                "step.value() + a",
                "51"
            },
            {
                "calln", "(a, b)",
                "fn(4, 2)",
                "step.value() + a + b",
                "71"
            },
            {
                "spread", "(a, b)",
                "fn(...supplied)",
                "step.value() + a + b",
                "71"
            }
        };

        for (String[] variant : variants) {
            String label = variant[0];

            String source = """
                    run: () => {
                        step: (() => { 1 }).future()
                        visits: 0

                        target: @@PARAMETERS@@ => {
                            visits = visits + 1
                            @@RESULT@@
                        }

                        values: Array(4, 2)

                        answer: ((fn, supplied) => {
                            @@CALL@@
                        })(target, values)

                        answer * 10 + visits
                    }
                    run()
                    """
                    .replace("@@PARAMETERS@@", variant[1])
                    .replace("@@CALL@@", variant[2])
                    .replace("@@RESULT@@", variant[3]);

            ProtosExecutionOutcome outcome =
                    ProtosTestExecutionSupport.execute(
                            "i108-frame-native-" + label
                                    + "-suspension.protos",
                            source,
                            new ProtosCoreBootstrap()
                                    .bootstrap(CORE)
                                    .newModuleActivation());

            assertEquals(
                    ProtosExecutionOutcome.State.COMPLETED,
                    outcome.state(),
                    () -> label
                            + ": state=" + outcome.state()
                            + ", error=" + outcome.error());

            assertInteger(
                    Long.parseLong(variant[4]),
                    outcome.value());
        }
    }


    @Test
    void i108FrameNativeSelectedFallbackPreservesMethodHomeAndErrors()
            throws Exception {
        String override = """
                run: () => {
                    target: () => { 7 }
                    target.answer: 19
                    target.call: () => { this.answer }
                    ((fn) => { fn() })(target)
                }
                run()
                """;

        assertParity(override, 19);

        String invalidCall = """
                run: () => {
                    target: () => { 7 }
                    target.call: 13
                    Error.handle(() => {
                        ((fn) => { fn() })(target)
                    }, (caught) => { 42 })
                }
                run()
                """;

        assertParity(invalidCall, 42);
    }

    @Test
    void zeroArgumentLiteralWithOwnBindingsRunsInlineInCaller() throws Exception {
        String source = """
                run: () => {
                    (() => {
                        a: 20
                        b: 22
                        probe()
                        a + b
                    })()
                }
                run()
                """;
        Execution normal = execute(false, source);
        assertInteger(42, normal.value());
        assertSourceClosureCallSite(
                normal.probe(), "CallNilaryMethod");
        assertParity(source, 42);
        System.out.println("I096_A_C0_INLINE=YES");
    }

    @Test
    void oneArgumentLiteralBindsItsOwnScopeIndependentlyOfTheCaller() throws Exception {
        String source = """
                run: () => {
                    value: 5
                    local: 1
                    r: ((value) => {
                        local: value * 10
                        probe()
                        local + 1
                    })(4)
                    r * 100 + local * 10 + value
                }
                run()
                """;
        Execution normal = execute(false, source);
        assertInteger(4115, normal.value());
        assertPhysicalClosureCallStack(normal.probe());
        // I101-A: the NORMAL root keeps no ordinary C1 direct-call alternative.
        assertTrue(
                position(instructionNames(normal.innermostRoot()), "InvokeClosureOne") < 0,
                "NORMAL keeps no C1 direct-call alternative");
        assertParity(source, 4115);
        System.out.println("I096_A_C1_OWN_SCOPE=YES");
    }

    @Test
    void capturedOuterBindingIsReadAndWrittenByReference() throws Exception {
        String source = """
                run: () => {
                    total: 1
                    step: 41
                    ((amount) => {
                        total = total + amount
                        probe()
                    })(step)
                    total
                }
                run()
                """;
        Execution normal = execute(false, source);
        assertInteger(42, normal.value());
        assertPhysicalClosureCallStack(normal.probe());
        assertParity(source, 42);
        System.out.println("I096_A_CAPTURE_BY_REFERENCE=YES");
    }

    /** D179: the inline own binding keeps exact presence across remove/recreate. */
    @Test
    void ownBindingRemovalAndRecreationKeepExactPresence() throws Exception {
        String source = """
                run: () => {
                    x: 1
                    inner: ((seed) => {
                        x: seed
                        before: x
                        context.removeSlot("x")
                        removed: x
                        context.x: 3
                        probe()
                        before * 100 + removed * 10 + x
                    })(2)
                    inner * 10 + x
                }
                run()
                """;
        Execution normal = execute(false, source);
        assertInteger(2131, normal.value());
        assertPhysicalClosureCallStack(normal.probe());
        assertParity(source, 2131);
        System.out.println("I096_A_D179_REMOVE_RECREATE=YES");
    }

    /**
     * The destination selected before the right-hand side is the callee's own
     * binding; removing it during the right-hand side never retargets the
     * write to the caller's same-named binding.
     */
    @Test
    void assignmentDestinationIsNotReresolvedAfterRightHandSideRemoval() throws Exception {
        String source = """
                run: () => {
                    x: 1
                    remover: (scope) => {
                        scope.removeSlot("x")
                        12
                    }
                    Error.handle(() => {
                        ((seed) => {
                            x: seed
                            x = remover(context)
                            probe()
                        })(2)
                    }, (caught) => { null })
                    x
                }
                run()
                """;
        assertInteger(1, execute(false, source).value());
        assertParity(source, 1);
        System.out.println("I096_A_ASSIGNMENT_TARGET_RETAINED=YES");
    }

    /**
     * Each invocation materializes the callee's own fresh Context, never the
     * caller's, and two invocations of the same site never share it.
     */
    @Test
    void observedContextIsTheCalleesOwnFreshActivation() throws Exception {
        String source = """
                run: () => {
                    c: (() => {
                        marker: 7
                        probe()
                        context
                    })()
                    (c === context).ifTrueIfFalse(() => { null }, () => { c })
                }
                a: run()
                b: run()
                (a === b).ifTrueIfFalse(() => { -1 }, () => { a.marker + b.marker })
                """;
        Execution normal = execute(false, source);
        assertInteger(14, normal.value());
        assertPhysicalClosureCallStack(normal.probe());
        assertParity(source, 14);
        System.out.println("I096_A_FRESH_CONTEXT_IDENTITY=YES");
    }

    /** Re-entrant invocations of the same inline site keep isolated bindings. */
    @Test
    void reentrantInvocationsKeepIsolatedOwnBindings() throws Exception {
        String source = """
                step: (k) => {
                    (k > 0).ifTrueIfFalse(() => { run(k - 1) }, () => { 0 })
                }
                run: (n) => {
                    ((k) => {
                        local: k
                        local + step(k) + local
                    })(n)
                }
                run(3)
                """;
        assertInteger(12, execute(false, source).value());
        assertParity(source, 12);
        System.out.println("I096_A_REENTRANT_ISOLATION=YES");
    }

    @Test
    void errorAndNonLocalReturnKeepExactControl() throws Exception {
        String nonLocalReturn = """
                run: () => {
                    ((value) => {
                        probe()
                        ^value + 1
                    })(41)
                    0
                }
                run()
                """;
        Execution returned = execute(false, nonLocalReturn);
        assertInteger(42, returned.value());
        assertEquals(1, returned.probe().calls.get());
        assertParity(nonLocalReturn, 42);

        String error = """
                run: () => {
                    (() => {
                        probe()
                        Error().signal()
                        1
                    })()
                    2
                }
                Error.handle(() => { run() }, (caught) => { 42 })
                """;
        Execution handled = execute(false, error);
        assertInteger(42, handled.value());
        assertEquals(1, handled.probe().calls.get());
        assertParity(error, 42);
        System.out.println("I096_A_NLR_PRESERVED=YES");
        System.out.println("I096_A_ERROR_PROPAGATION_PRESERVED=YES");
    }

    @Test
    void suspensionInsideTheInlineBodyResumesWithoutReplay() throws Exception {
        String source = """
                run: () => {
                    step: (() => { 1 }).future()
                    visits: 0
                    r: ((value) => {
                        visits = visits + 1
                        value + step.value()
                    })(41)
                    r * 10 + visits
                }
                run()
                """;
        ProtosExecutionOutcome outcome =
                ProtosTestExecutionSupport.execute(
                        "i096-a-suspension.protos",
                        source,
                        new ProtosCoreBootstrap().bootstrap(CORE).newModuleActivation());
        assertEquals(
                ProtosExecutionOutcome.State.COMPLETED,
                outcome.state(),
                () -> "outcome=" + outcome.state() + ", error=" + outcome.error());
        assertInteger(421, outcome.value());
        System.out.println("I096_A_SUSPENSION_RESUMPTION_PRESERVED=YES");
    }

    /**
     * The canonical selection the inline region relies on is immutable: the
     * published root is frozen and its {@code call} is the canonical standard
     * selection. Nothing here mutates global state.
     */
    @Test
    void canonicalRootCallSelectionIsPublishedImmutable() throws Exception {
        try (Context context = Context.newBuilder(ProtosLanguage.ID).build()) {
            context.initialize(ProtosLanguage.ID);
            context.enter();
            try {
                new Probe().module();
                ProtosObjectValue root = ProtosObjectValue.rootObject();
                assertTrue(root.isFrozen(), "the standard root is published frozen");
                assertTrue(
                        ProtosStandardObjectProtocol.isCanonicalStandardCallSelection(
                                root.readLocalSlot("call").orElseThrow(), root));
            } finally {
                context.leave();
            }
        }
        System.out.println("I096_A_CANONICAL_CALL_IMMUTABLE=YES");
    }

    /**
     * PLAT067-B: with a debugger attached before lowering, the literal still
     * runs inline: no callee frame is fabricated and no ordinary call is
     * retained, while the callee keeps its semantic RootTag inside run.
     */
    @Test
    void attachedDebuggerKeepsTheInlineCallee() throws Exception {
        Execution debug = execute(true, """
                run: () => {
                    ((value) => {
                        local: value
                        probe()
                        local + 1
                    })(41)
                }
                run()
                """);
        assertInteger(42, debug.value());
        assertEquals(1, debug.probe().calls.get());
        assertSourceClosureCallSite(
                debug.probe(), "CallUnaryMethod");
        System.out.println("I096_A_DEBUGGER_KEEPS_INLINE=YES");
    }

    @Test
    void nonAdmittedShapesKeepTheUnchangedCallTopology() throws Exception {
        List<String> sources = List.of(
                // A rest parameter keeps ordinary binding (a nested Closure
                // body is admitted since the I096 nested-Closure extension).
                """
                run: () => {
                    probe()
                    ((...items) => { 5 })(1)
                }
                run()
                """,
                // Default and rest parameters keep ordinary binding.
                """
                run: () => {
                    probe()
                    ((a: 2) => { a + 3 })()
                }
                run()
                """);
        for (String source : sources) {
            Execution normal = execute(false, source);
            assertInteger(5, normal.value());
            ProtosBytecodeRootNode runRoot = normal.outermostGuestRoot();
            List<String> names = instructionNames(runRoot);
            assertFalse(
                    names.stream().anyMatch(name -> name.contains("InlineClosureLiteralCall")),
                    () -> source + names);
        }
        System.out.println("I096_A_NON_ADMITTED_TOPOLOGY_UNCHANGED=YES");
    }

    // ---- I096-B1: calls through a binding with a candidate body -------------

    @Test
    void bindingZeroArgumentCallRunsCandidateInlineBehindTheGuard() throws Exception {
        String source = """
                run: () => {
                    f: () => {
                        a: 20
                        b: 22
                        probe()
                        a + b
                    }
                    f()
                }
                run()
                """;
        Execution normal = execute(false, source);
        assertInteger(42, normal.value());
        assertSourceClosureCallSite(
                normal.probe(), "CallNilaryMethod");
        assertParity(source, 42);
        System.out.println("I096_B1_C0_BINDING_INLINE=YES");
    }

    /**
     * PERF042 / PLAT066-C: a terminal C1 call of a lexical Closure binding is
     * lowered as one physical terminal invocation. The caller holds no copy
     * of the callee body, and the callee keeps its own root; Graal decides
     * inlining between CallTargets.
     */
    @Test
    void directC1IdentityUsesPhysicalTerminalCallWithoutLowererExpansion() throws Exception {
        String source = """
                run: () => {
                    identity: (x) => x
                    identity(42)
                }
                run()
                """;

        try (Context context = Context.newBuilder(ProtosLanguage.ID).build()) {
            context.initialize(ProtosLanguage.ID);
            context.enter();
            try {
                ProtosBytecodeRootNode module = lowerRoot(source);
                module.getRootNodes().ensureComplete();

                List<ProtosBytecodeRootNode> roots = groupRoots(module);
                assertEquals(3, roots.size(), "module, run and identity roots");

                List<ProtosBytecodeRootNode> callers =
                        roots.stream()
                                .filter(root ->
                                        count(instructionNames(root),
                                                "CallUnaryMethod") > 0)
                                .toList();
                assertEquals(1, callers.size(),
                        "exactly one Root holds the terminal C1 invocation");
                ProtosBytecodeRootNode caller = callers.get(0);
                assertTrue(caller != module, "the terminal call belongs to run");

                List<String> names = instructionNames(caller);
                assertEquals(1, count(names, "c.CallUnaryMethod"), names::toString);
                assertEquals(0, count(names, "PrepareInlineClosureLiteralCall"), names::toString);
                assertEquals(0, count(names, "AdmitsInlineClosureLiteralCall"), names::toString);
                assertEquals(0, count(names, "LoadInlineClosureArgument"), names::toString);
                assertEquals(0, count(names, "BindInlineClosureFrameParameter"), names::toString);
                assertEquals(0, count(names, "LoadInlineLiteralFallbackCall"), names::toString);
                assertEquals(0, count(names, "ReadInlineFrameLocal"), names::toString);
                assertEquals(1, rootTags(caller.getBytecodeNode()),
                        "run keeps only its own RootTag");

                List<ProtosBytecodeRootNode> callees =
                        roots.stream()
                                .filter(root -> root != module && root != caller)
                                .toList();
                assertEquals(1, callees.size());
                List<String> callee = instructionNames(callees.get(0));
                assertTrue(position(callee, "CopySuppliedArguments") >= 0, callee::toString);
                assertTrue(position(callee, "load.local") >= 0, callee::toString);

                assertInteger(42,
                        module.getCallTarget().call(new Probe().module()));
            } finally {
                context.leave();
            }
        }

        System.out.println("PERF042_DIRECT_C1_PHYSICAL_TERMINAL_CALL=YES");
    }

    @Test
    void bindingOneArgumentCallKeepsCalleeOwnSameNamedBinding() throws Exception {
        String source = """
                run: () => {
                    y: 1000
                    f: (x) => {
                        y: x + 1
                        probe()
                        y
                    }
                    r: f(7)
                    r + y
                }
                run()
                """;
        Execution normal = execute(false, source);
        assertInteger(1008, normal.value());
        assertPhysicalClosureCallStack(normal.probe());
        assertParity(source, 1008);
        System.out.println("I096_B1_C1_OWN_SCOPE=YES");
    }

    @Test
    void reassignmentToAnotherDefinitionTakesThePhysicalFallback() throws Exception {
        String source = """
                run: () => {
                    f: (x) => {
                        probe()
                        x + 1
                    }
                    a: f(1)
                    f = (x) => {
                        probe()
                        x * 100
                    }
                    b: f(2)
                    a * 1000 + b
                }
                run()
                """;
        Execution normal = execute(false, source);
        assertInteger(2200, normal.value());
        assertEquals(2, normal.probe().calls.get());
        assertPhysicalClosureCallStack(normal.probe());
        assertTrue(
                normal.probe().stacks.get(0).get(1)
                        != normal.probe().stacks.get(1).get(1),
                "reassignment invokes a distinct Closure definition");
        assertParity(source, 2200);
        System.out.println("I096_B1_OTHER_DEFINITION_FALLBACK=YES");
    }

    /**
     * Another instance of the same definition, created by an earlier
     * activation, is admitted and reads its own captures, never the caller's.
     */
    @Test
    void foreignInstanceOfTheSameDefinitionUsesItsOwnCaptures() throws Exception {
        String source = """
                saved: null
                keep: (g) => {
                    (saved === null).ifTrueIfFalse(() => { saved = g }, () => { null })
                    saved
                }
                run: (n) => {
                    f: (x) => {
                        probe()
                        x + n
                    }
                    f = keep(f)
                    f(1)
                }
                a: run(10)
                b: run(20)
                a * 100 + b
                """;
        Execution normal = execute(false, source);
        assertInteger(1111, normal.value());
        // PERF042: the terminal C1 call runs the physical callee over run.
        assertPhysicalClosureCallStack(normal.probe());
        assertParity(source, 1111);
        System.out.println("I096_B1_FOREIGN_INSTANCE_OWN_CAPTURES=YES");
    }

    /** The receiver is read once, before arguments that reassign the binding. */
    @Test
    void reassignmentDuringArgumentEvaluationKeepsTheReadReceiver() throws Exception {
        String source = """
                run: () => {
                    f: (x) => {
                        probe()
                        x + 1
                    }
                    swap: () => {
                        f = (x) => { x * 100 }
                        5
                    }
                    r: f(swap())
                    r * 1000 + f(5)
                }
                run()
                """;
        Execution normal = execute(false, source);
        assertInteger(6500, normal.value());
        assertPhysicalClosureCallStack(normal.probe());
        assertParity(source, 6500);
        System.out.println("I096_B1_RECEIVER_READ_ONCE=YES");
    }

    @Test
    void mutationFromAnotherClosureNeverRunsTheCandidate() throws Exception {
        String source = """
                run: () => {
                    f: (x) => { x + 1 }
                    mutate: () => {
                        f = (x) => {
                            probe()
                            x * 100
                        }
                    }
                    mutate()
                    f(2)
                }
                run()
                """;
        Execution normal = execute(false, source);
        assertInteger(200, normal.value());
        assertTrue(normal.probe().stacks.get(0).size() > INLINE_DEPTH, normal.probe()::toString);
        assertParity(source, 200);
        System.out.println("I096_B1_EXTERNAL_MUTATION_FALLBACK=YES");
    }

    /** D179: removal and recreation of the binding are observed at run time. */
    @Test
    void bindingRemovalAndRecreationAreObserved() throws Exception {
        String source = """
                run: () => {
                    f: (x) => {
                        probe()
                        x + 1
                    }
                    g: f
                    a: f(1)
                    context.removeSlot("f")
                    context.f: (x) => { x * 100 }
                    b: f(2)
                    context.removeSlot("f")
                    context.f: g
                    c: f(3)
                    a * 10000 + b * 10 + c
                }
                run()
                """;
        Execution normal = execute(false, source);
        assertInteger(22004, normal.value());
        assertEquals(2, normal.probe().calls.get());
        assertPhysicalClosureCallStack(normal.probe());
        assertParity(source, 22004);
        System.out.println("I096_B1_D179_REMOVE_RECREATE=YES");
    }

    /** A nearer binding shadows the outer one only from its creation on. */
    @Test
    void nearerBindingShadowsTheOuterOneOnlyAfterItsCreation() throws Exception {
        String source = """
                f: (x) => { x + 1 }
                run: () => {
                    early: f(1)
                    f: (x) => {
                        probe()
                        x * 10
                    }
                    late: f(2)
                    early * 100 + late
                }
                run()
                """;
        Execution normal = execute(false, source);
        assertInteger(220, normal.value());
        assertPhysicalClosureCallStack(normal.probe());
        assertParity(source, 220);
        System.out.println("I096_B1_SHADOWING=YES");
    }

    @Test
    void bindingCalleeReadsAndWritesCapturesByReference() throws Exception {
        String source = """
                run: () => {
                    total: 1
                    f: (amount) => {
                        total = total + amount
                        probe()
                        total
                    }
                    r: f(41)
                    r * 100 + total
                }
                run()
                """;
        Execution normal = execute(false, source);
        assertInteger(4242, normal.value());
        assertPhysicalClosureCallStack(normal.probe());
        assertParity(source, 4242);
        System.out.println("I096_B1_CAPTURE_BY_REFERENCE=YES");
    }

    /** An instance-level call override, and its removal, on a local Closure. */
    @Test
    void instanceCallOverrideAndItsRemovalAreSelectedAtRunTime() throws Exception {
        String source = """
                run: () => {
                    f: (v) => { v + 1 }
                    f.call: (v) => {
                        probe()
                        100 + v
                    }
                    a: f(1)
                    f.removeSlot("call")
                    b: f(1)
                    a * 10 + b
                }
                run()
                """;
        Execution normal = execute(false, source);
        assertInteger(1012, normal.value());
        assertTrue(normal.probe().stacks.get(0).size() > INLINE_DEPTH, normal.probe()::toString);
        assertParity(source, 1012);
        System.out.println("I096_B1_CALL_OVERRIDE_FALLBACK=YES");
    }

    @Test
    void bindingCalleeOwningItsReturnHomeKeepsNonLocalReturn() throws Exception {
        String source = """
                run: () => {
                    f: (value) => {
                        probe()
                        ^value + 1
                    }
                    f(41)
                    0
                }
                run()
                """;
        Execution normal = execute(false, source);
        assertInteger(42, normal.value());
        assertEquals(1, normal.probe().calls.get());
        assertParity(source, 42);
        System.out.println("I096_B1_RETURN_HOME_PRESERVED=YES");
    }

    @Test
    void bindingCalleeObservesItsOwnFreshContext() throws Exception {
        String source = """
                run: () => {
                    f: () => {
                        marker: 7
                        probe()
                        context
                    }
                    c: f()
                    (c === context).ifTrueIfFalse(() => { null }, () => { c })
                }
                a: run()
                b: run()
                (a === b).ifTrueIfFalse(() => { -1 }, () => { a.marker + b.marker })
                """;
        Execution normal = execute(false, source);
        assertInteger(14, normal.value());
        assertPhysicalClosureCallStack(normal.probe());
        assertParity(source, 14);
        System.out.println("I096_B1_FRESH_CONTEXT_IDENTITY=YES");
    }

    @Test
    void reentrantBindingCallsKeepIsolatedActivations() throws Exception {
        String source = """
                step: (k) => {
                    (k > 0).ifTrueIfFalse(() => { run(k - 1) }, () => { 0 })
                }
                run: (n) => {
                    f: (k) => {
                        local: k
                        local + step(k) + local
                    }
                    f(n)
                }
                run(3)
                """;
        assertInteger(12, execute(false, source).value());
        assertParity(source, 12);
        System.out.println("I096_B1_REENTRANT_ISOLATION=YES");
    }

    @Test
    void bindingCallWithAttachedDebuggerKeepsThePhysicalCallee() throws Exception {
        Execution debug = execute(true, """
                run: () => {
                    f: (value) => {
                        local: value
                        probe()
                        local + 1
                    }
                    f(41)
                }
                run()
                """);
        assertInteger(42, debug.value());
        assertEquals(1, debug.probe().calls.get());
        assertPhysicalClosureCallStack(debug.probe());
        List<RootNode> stack = debug.probe().stacks.get(0);
        ProtosBytecodeRootNode callee = debug.innermostRoot();
        ProtosBytecodeRootNode run = debug.outermostGuestRoot();
        assertSame(callee, stack.get(1), "callee occupies its own CallTarget");
        assertSame(run, stack.get(2), "callee is invoked from run");
        assertTrue(callee != run, "the callee keeps its own CallTarget");
        assertEquals(
                1,
                count(instructionNames(run), "CallUnaryMethod"),
                "run owns the f(41) invocation");
        assertEquals(
                1,
                count(instructionNames(callee), "CopySuppliedArguments"),
                "callee binds its supplied arguments");
        System.out.println("PERF042_DEBUGGER_KEEPS_PHYSICAL_CALLEE=YES");
    }

    /**
     * A Bytecode reparse that materializes source and tag information replays
     * the same roots in the same group order and the same physical terminal
     * call topology.
     */
    @Test
    void reparseKeepsRootOrderAndPhysicalTerminalCall() throws Exception {
        String source = """
                run: () => {
                    f: (x) => {
                        y: x + 1
                        y
                    }
                    f(7)
                }
                run()
                """;
        try (Context context = Context.newBuilder(ProtosLanguage.ID).build()) {
            context.initialize(ProtosLanguage.ID);
            context.enter();
            try {
                ProtosBytecodeRootNode module = lowerRoot(source);
                List<ProtosBytecodeRootNode> before =
                        groupRoots(module);
                assertEquals(3, before.size(), "module, run and f roots");
                List<Integer> terminalCallsBefore = terminalOneCalls(before);
                assertPhysicalTerminalTopology(module, before);

                module.getRootNodes().ensureComplete();
                List<ProtosBytecodeRootNode> after =
                        groupRoots(module);
                assertEquals(before.size(), after.size());
                for (int i = 0; i < before.size(); i++) {
                    assertSame(before.get(i), after.get(i), "root order at " + i);
                    assertTrue(after.get(i).getBytecodeNode().getSourceSection() != null,
                            "source materialized at " + i);
                }
                assertEquals(terminalCallsBefore, terminalOneCalls(after));
                assertPhysicalTerminalTopology(module, after);
                assertInteger(8, module.getCallTarget().call(new Probe().module()));
            } finally {
                context.leave();
            }
        }
        System.out.println("I096_B1_REPARSE_STABLE=YES");
    }

    @Test
    void excludedBindingShapesKeepTheUnchangedCallTopology() throws Exception {
        List<String> sources = List.of(
                // Default parameter.
                """
                run: () => {
                    f: (a: 2) => { a + 3 }
                    probe()
                    f()
                }
                run()
                """,
                // Rest parameter.
                """
                run: () => {
                    f: (...items) => { 5 }
                    probe()
                    f(1)
                }
                run()
                """,
                // Spread argument.
                """
                run: () => {
                    f: (v) => { v }
                    xs: [5]
                    probe()
                    f(...xs)
                }
                run()
                """,
                // A body creating a nested Closure is admitted, but a default
                // parameter still keeps ordinary binding.
                """
                run: () => {
                    f: (v, w: 0) => {
                        g: () => v + w
                        g()
                    }
                    probe()
                    f(5)
                }
                run()
                """,
                // A binding created twice keeps no candidate.
                """
                run: () => {
                    f: (v) => { v + 1 }
                    context.removeSlot("f")
                    f: (v) => { v }
                    probe()
                    f(5)
                }
                run()
                """,
                // A call that is not a direct expression of the sequence.
                """
                run: () => {
                    f: (v) => { v }
                    probe()
                    (true).ifTrueIfFalse(() => { f(5) }, () => { 0 })
                }
                run()
                """);
        for (String source : sources) {
            Execution normal = execute(false, source);
            assertInteger(5, normal.value());
            List<String> names = instructionNames(normal.outermostGuestRoot());
            assertFalse(
                    names.stream().anyMatch(name -> name.contains("InlineClosureLiteralCall")),
                    () -> source + names);
            assertParity(source, 5);
        }
        System.out.println("I096_B1_EXCLUDED_SHAPES_UNCHANGED=YES");
    }

    // ---- I096 coverage: C2/C3/N, operand positions, budget -----------------

    @Test
    void twoArgumentLiteralAndBindingCallsRunInlineInCaller() throws Exception {
        String literal = """
                run: () => {
                    ((a, b) => {
                        probe()
                        a * 100 + b
                    })(4, 2)
                }
                run()
                """;
        Execution normal = execute(false, literal);
        assertInteger(402, normal.value());
        assertSourceClosureCallSite(
                normal.probe(), "CallBinaryMethod");
        assertParity(literal, 402);

        String binding = """
                run: () => {
                    f: (a, b) => {
                        probe()
                        a * 100 + b
                    }
                    f(4, 2)
                }
                run()
                """;
        Execution bound = execute(false, binding);
        assertInteger(402, bound.value());
        assertSourceClosureCallSite(
                bound.probe(), "CallBinaryMethod");
        assertParity(binding, 402);
        System.out.println("I096_C2_INLINE=YES");
    }

    /**
     * C3 parameters and an own local named like caller bindings live in the
     * callee's own scope; the caller's same-named bindings are untouched.
     */
    @Test
    void threeArgumentBindingCallKeepsItsOwnScope() throws Exception {
        String source = """
                run: () => {
                    a: 1
                    b: 2
                    sum: 3
                    f: (a, b, c) => {
                        sum: a * 100 + b * 10 + c
                        probe()
                        sum
                    }
                    r: f(4, 5, 6)
                    r * 1000 + a * 100 + b * 10 + sum
                }
                run()
                """;
        Execution normal = execute(false, source);
        assertInteger(456123, normal.value());
        assertSourceClosureCallSite(
                normal.probe(), "CallTernaryMethod");
        assertParity(source, 456123);
        System.out.println("I096_C3_OWN_SCOPE=YES");
    }

    /** Arguments are evaluated once each, left to right, before admission. */
    @Test
    void multipleArgumentsAreEvaluatedOnceLeftToRight() throws Exception {
        String source = """
                run: () => {
                    log: 0
                    next: (d) => {
                        log = log * 10 + d
                        d
                    }
                    f: (a, b, c) => {
                        probe()
                        a * 100 + b * 10 + c
                    }
                    r: f(next(1), next(2), next(3))
                    r * 1000 + log
                }
                run()
                """;
        Execution normal = execute(false, source);
        assertInteger(123123, normal.value());
        assertEquals(1, normal.probe().calls.get());
        assertPhysicalClosureCallStack(normal.probe());
        assertParity(source, 123123);
        System.out.println("I096_N_ARGUMENT_ORDER=YES");
    }

    /**
     * Binding calls in operand positions of the same activation (a compound
     * expression and the argument of another call) each get their own
     * guarded region and their own fresh activation.
     */
    @Test
    void bindingCallsInOperandPositionsRunInline() throws Exception {
        String source = """
                run: () => {
                    f: (x, y) => {
                        local: x * y
                        probe()
                        local
                    }
                    g: (v) => {
                        probe()
                        v + 1
                    }
                    r: f(2, 3) + f(4, 5) * 10
                    g(f(1, r))
                }
                run()
                """;
        Execution normal = execute(false, source);
        assertInteger(207, normal.value());
        assertEquals(4, normal.probe().calls.get());
        assertPhysicalClosureCallStack(normal.probe());

        List<List<RootNode>> stacks = normal.probe().stacks;
        ProtosBytecodeRootNode fRoot =
                assertInstanceOf(ProtosBytecodeRootNode.class, stacks.get(0).get(1));
        ProtosBytecodeRootNode gRoot =
                assertInstanceOf(ProtosBytecodeRootNode.class, stacks.get(3).get(1));
        ProtosBytecodeRootNode runRoot = normal.outermostGuestRoot();

        for (int call = 0; call < 3; call++) {
            ProtosBytecodeRootNode selected =
                    assertInstanceOf(
                            ProtosBytecodeRootNode.class, stacks.get(call).get(1));
            assertEquals(
                    instructionNames(fRoot), instructionNames(selected),
                    "the first three calls execute f");
        }

        assertTrue(
                !instructionNames(fRoot).equals(instructionNames(gRoot)),
                "the fourth call executes g");

        assertEquals(4, groupRoots(runRoot).size());
        assertEquals(1, rootTags(runRoot.getBytecodeNode()));
        assertEquals(1, rootTags(fRoot.getBytecodeNode()));
        assertEquals(1, rootTags(gRoot.getBytecodeNode()));
        assertParity(source, 207);
        System.out.println("I096_OPERAND_POSITION_INLINE=YES");
    }

    @Test
    void multipleArgumentCallOverrideTakesThePhysicalFallback() throws Exception {
        String source = """
                run: () => {
                    f: (a, b) => {
                        probe()
                        a * 10 + b
                    }
                    f.call: (a, b) => {
                        probe()
                        a * 1000 + b * 100
                    }
                    g: (a, b, c) => {
                        probe()
                        a * 100 + b * 10 + c
                    }
                    g.call: (a, b, c) => {
                        probe()
                        c * 100 + b * 10 + a
                    }
                    x: f(1, 2)
                    y: g(1, 2, 3)
                    f.removeSlot("call")
                    g.removeSlot("call")
                    x * 1000000 + y * 1000 + f(1, 2) * 1000 + g(1, 2, 3)
                }
                run()
                """;
        Execution normal = execute(false, source);
        assertInteger(1200333123L, normal.value());
        // Execution order: C2 override, C3 override (rejected admission),
        // then C2 and C3 after removeSlot (canonical selection restored).
        List<List<RootNode>> stacks = normal.probe().stacks;
        assertEquals(4, stacks.size(), normal.probe()::toString);
        assertPhysicalClosureCallStack(normal.probe());

        assertTrue(
                stacks.get(0).get(1) != stacks.get(2).get(1),
                "removing f.call restores f's original Closure body");

        assertTrue(
                stacks.get(1).get(1) != stacks.get(3).get(1),
                "removing g.call restores g's original Closure body");
        assertParity(source, 1200333123L);
        System.out.println("I096_N_CALL_OVERRIDE_FALLBACK=YES");
    }

    @Test
    void multipleArgumentCallWithAttachedDebuggerKeepsTheInlineCallee() throws Exception {
        Execution debug = execute(true, """
                run: () => {
                    f: (a, b, c) => {
                        probe()
                        a * 100 + b * 10 + c
                    }
                    f(1, 2, 3)
                }
                run()
                """);
        assertInteger(123, debug.value());
        assertPhysicalClosureCallStack(debug.probe());
        System.out.println("I096_N_DEBUGGER_KEEPS_INLINE=YES");
    }

    /** C8, the expansion budget itself, runs inline with distinct arguments. */
    @Test
    void eightArgumentCallAtTheBudgetRunsInline() throws Exception {
        String source = """
                run: () => {
                    f: (a, b, c, d, e, g, h, i) => {
                        probe()
                        ((((((a * 10 + b) * 10 + c) * 10 + d) * 10 + e) * 10 + g) * 10 + h) * 10 + i
                    }
                    f(8, 7, 6, 5, 4, 3, 2, 1)
                }
                run()
                """;
        Execution normal = execute(false, source);
        assertInteger(87654321, normal.value());
        assertSourceClosureCallSite(
                normal.probe(), "CallVarargsMethod");

        ProtosBytecodeRootNode caller =
                assertInstanceOf(
                        ProtosBytecodeRootNode.class,
                        normal.probe().stacks.get(0).get(2));

        assertEquals(
                1,
                count(
                        instructionNames(caller),
                        "CollectClosureCallArguments"),
                "C8 constructs its supplied argument vector");
        assertParity(source, 87654321);
        Execution debug = execute(true, source);
        assertInteger(87654321, debug.value());
        assertSourceClosureCallSite(
                debug.probe(), "CallVarargsMethod");
        System.out.println("I096_C8_INLINE=YES");
    }

    /**
     * An earlier operand reassigns the binding: the later site reads the
     * receiver once, after that operand, and takes the fallback for the
     * other definition; a site whose own argument reassigns the binding
     * keeps the receiver read before its arguments. Every argument effect
     * happens exactly once.
     */
    @Test
    void operandReassignmentIsObservedWithoutRereadingTheReceiver() throws Exception {
        String source = """
                run: () => {
                    effects: 0
                    f: (a, b) => {
                        probe()
                        a * 100 + b
                    }
                    original: f
                    swap: (v) => {
                        effects = effects + 1
                        f = (a, b) => { a * 1000 + b }
                        v
                    }
                    first: f(1, 2) + swap(0) + f(3, 4)
                    f = original
                    second: f(swap(5), 6)
                    first * 10000 + second * 10 + effects
                }
                run()
                """;
        // first = 102 + 0 + 3004; second = 506 (the receiver was read first).
        Execution normal = execute(false, source);
        assertInteger(31060000L + 5060 + 2, normal.value());
        assertEquals(2, normal.probe().calls.get(), normal.probe()::toString);
        assertPhysicalClosureCallStack(normal.probe());
        assertParity(source, 31060000L + 5060 + 2);
        System.out.println("I096_OPERAND_REASSIGNMENT_SINGLE_READ=YES");
    }

    /** A call wider than the expansion budget keeps the unchanged topology. */
    @Test
    void arityAboveTheExpansionBudgetIsNotInlined() throws Exception {
        assertEquals(8, CanonicalToBytecodeLowerer.MAX_INLINE_CLOSURE_CALL_ARGUMENTS);
        String source = """
                run: () => {
                    f: (a, b, c, d, e, g, h, i, j) => {
                        probe()
                        (((((((a * 10 + b) * 10 + c) * 10 + d) * 10 + e) * 10 + g) * 10 + h) * 10 + i) * 10 + j
                    }
                    f(1, 2, 3, 4, 5, 6, 7, 8, 9)
                }
                run()
                """;
        Execution normal = execute(false, source);
        assertInteger(123456789, normal.value());
        assertTrue(normal.probe().depths().get(0) > INLINE_DEPTH, normal.probe()::toString);
        List<String> names = instructionNames(normal.outermostGuestRoot());
        assertFalse(
                names.stream().anyMatch(name -> name.contains("InlineClosureLiteralCall")),
                names::toString);
        assertParity(source, 123456789);
        assertTrue(
                execute(true, source).probe().depths().get(0) > INLINE_DEPTH,
                "C9 stays a physical call with a debugger attached too");
        System.out.println("I096_EXPANSION_BUDGET_FALLBACK=YES");
    }

    // ---- I096 nested Closures: bodies creating Closures ---------------------

    /**
     * The outer body runs inline in {@code run}; the nested Closure keeps its
     * own CallTarget, whose caller frame is {@code run} itself, never the
     * outer callee. The inline region creates it over the callee's own
     * activation (named path, never the frame-native one), and the group
     * gains no duplicate root, before or after a reparse.
     */
    @Test
    void nestedClosureWithoutEscapeKeepsPhysicalOuterAndInnerRoots() throws Exception {
        String source = """
                run: () => {
                    f: (value) => {
                        probe()
                        inner: () => {
                            probe()
                            value + 1
                        }
                        inner()
                    }
                    f(41)
                }
                run()
                """;
        Execution normal = execute(false, source);
        assertInteger(42, normal.value());
        List<List<RootNode>> stacks = normal.probe().stacks;
        assertEquals(2, stacks.size(), normal.probe()::toString);
        // PERF042: the terminal C1 call runs f physically over run.
        assertNestedPhysicalStacks(stacks, normal.probe()::toString);
        ProtosBytecodeRootNode fRoot = normal.innermostRoot();
        ProtosBytecodeRootNode runRoot = normal.outermostGuestRoot();

        List<String> run = instructionNames(runRoot);
        assertEquals(1, count(run, "c.CallUnaryMethod"), run::toString);
        assertEquals(0, count(run, "AdmitsInlineClosureLiteralCall"), run::toString);
        assertEquals(1, closureCreations(run), "run creates only f");
        List<String> f = instructionNames(fRoot);
        assertEquals(1, closureCreations(f), "f's own root creates inner");
        assertEquals(1, rootTags(runRoot.getBytecodeNode()), "run keeps only its own RootTag");
        assertEquals(1, rootTags(fRoot.getBytecodeNode()));

        ProtosBytecodeRootNode innerRoot =
                assertInstanceOf(
                        ProtosBytecodeRootNode.class, stacks.get(1).get(1));
        assertEquals(1, rootTags(innerRoot.getBytecodeNode()));

        // PLAT067-B: an attached debugger observes the same physical frames.
        Execution debug = execute(true, source);
        assertInteger(42, debug.value());
        assertNestedPhysicalStacks(debug.probe().stacks, debug.probe()::toString);

        // f(41, 0) is never a candidate (arity), so it lowers the same
        // Closure roots without any inline region.
        assertNoDuplicateRootsAndStableReparse(source, source.replace("f(41)", "f(41, 0)"), 42);
        System.out.println("PERF042_NESTED_NO_ESCAPE_PHYSICAL=YES");
    }

    /** Two nested Closures read and write one shared callee binding. */
    @Test
    void nestedClosuresShareOneMutableCapture() throws Exception {
        String source = """
                run: () => {
                    f: (value) => {
                        local: value
                        read: () => local
                        write: (v) => {
                            local = v
                            local
                        }
                        before: read()
                        write(42)
                        probe()
                        before + read()
                    }
                    f(1)
                }
                run()
                """;
        Execution normal = execute(false, source);
        assertInteger(43, normal.value());
        // PERF042: the terminal C1 call runs f physically over run.
        assertPhysicalClosureCallStack(normal.probe());
        assertEquals(2, closureCreations(instructionNames(normal.innermostRoot())),
                "read and write are created by f's own root");
        assertEquals(1, closureCreations(instructionNames(normal.outermostGuestRoot())),
                "run creates only f");
        assertParity(source, 43);
        // Two nested definitions: still no duplicate root, stable reparse.
        assertNoDuplicateRootsAndStableReparse(source, source.replace("f(1)", "f(1, 0)"), 43);
        System.out.println("I096_NESTED_MUTABLE_CAPTURE_SHARED=YES");
    }

    /**
     * A Closure returned by the inline body outlives it: every later call
     * updates the same captured binding of the callee's activation, and two
     * invocations of the outer body never share it.
     */
    @Test
    void escapedNestedClosureOutlivesTheInlineBody() throws Exception {
        String source = """
                run: () => {
                    maker: (seed) => {
                        value: seed
                        probe()
                        () => {
                            probe()
                            value = value + 1
                            value
                        }
                    }
                    counter: maker(40)
                    a: counter()
                    b: counter()
                    a + b
                }
                run()
                """;
        Execution normal = execute(false, source);
        assertInteger(83, normal.value());
        List<List<RootNode>> stacks = normal.probe().stacks;
        assertEquals(3, stacks.size(), normal.probe()::toString);
        assertPhysicalClosureCallStack(normal.probe());

        ProtosBytecodeRootNode makerRoot =
                assertInstanceOf(
                        ProtosBytecodeRootNode.class, stacks.get(0).get(1));
        ProtosBytecodeRootNode runRoot = normal.outermostGuestRoot();
        ProtosBytecodeRootNode counterRoot =
                assertInstanceOf(
                        ProtosBytecodeRootNode.class, stacks.get(1).get(1));

        assertTrue(makerRoot != counterRoot);

        for (int call = 1; call < 3; call++) {
            assertEquals(
                    instructionNames(counterRoot),
                    instructionNames(
                            assertInstanceOf(
                                    ProtosBytecodeRootNode.class,
                                    stacks.get(call).get(1))));
            assertEquals(
                    instructionNames(runRoot),
                    instructionNames(
                            assertInstanceOf(
                                    ProtosBytecodeRootNode.class,
                                    stacks.get(call).get(2))));
        }

        assertEquals(
                1, closureCreations(instructionNames(makerRoot)),
                "maker creates the counter");
        assertEquals(1, rootTags(makerRoot.getBytecodeNode()));
        assertEquals(1, rootTags(counterRoot.getBytecodeNode()));
        assertParity(source, 83);

        String instances = """
                run: () => {
                    maker: (seed) => {
                        value: seed
                        () => {
                            value = value + 1
                            value
                        }
                    }
                    c1: maker(40)
                    c2: maker(100)
                    a: c1()
                    b: c2()
                    c: c1()
                    a * 10000 + b * 10 + c
                }
                run()
                """;
        assertParity(instances, 411052);

        // The counter escapes run itself, the root holding the inline
        // region, and is called only after run has completed.
        String afterCaller = """
                run: () => {
                    maker: (seed) => {
                        value: seed
                        () => {
                            value = value + 1
                            value
                        }
                    }
                    maker(40)
                }
                counter: run()
                a: counter()
                b: counter()
                a + b
                """;
        assertParity(afterCaller, 83);
        System.out.println("I096_NESTED_ESCAPE_LIFETIME=YES");
    }

    /**
     * The callee's Context is fresh per invocation, distinct from the
     * caller's, and is the context its nested Closures capture: their reads
     * and writes, and a remove/recreate of the binding, are observed through
     * it.
     */
    @Test
    void nestedCapturesShareTheCalleesOwnContext() throws Exception {
        String source = """
                run: () => {
                    f: (seed) => {
                        value: seed
                        bump: () => { value = value + 1 }
                        read: () => value
                        bump()
                        bump()
                        before: read()
                        context.removeSlot("value")
                        context.value: 100
                        probe()
                        after: read()
                        context
                    }
                    a: f(10)
                    b: f(20)
                    (a === b).ifTrueIfFalse(() => { -1 }, () => {
                        (a === context).ifTrueIfFalse(() => { -2 }, () => {
                            a.before * 1000000 + a.after * 1000 + b.before
                        })
                    })
                }
                run()
                """;
        // ifTrueIfFalse only selects the result here; it is not I096 evidence.
        Execution normal = execute(false, source);
        assertInteger(12100022, normal.value());
        assertPhysicalClosureCallStack(normal.probe());
        assertParity(source, 12100022);
        System.out.println("I096_NESTED_CONTEXT_IDENTITY=YES");
    }

    /**
     * Reentrant invocations through the inline site keep isolated callee
     * activations: a nested reader still sees its own invocation's binding
     * after a deeper invocation of the same site completed.
     */
    @Test
    void reentrantNestedCapturesKeepIsolatedActivations() throws Exception {
        String source = """
                step: (k) => {
                    (k > 0).ifTrueIfFalse(() => { run(k - 1) }, () => { 0 })
                }
                run: (n) => {
                    f: (k) => {
                        local: k
                        get: () => local
                        deeper: step(k)
                        get() + deeper + get()
                    }
                    f(n)
                }
                run(3)
                """;
        assertInteger(12, execute(false, source).value());
        assertParity(source, 12);
        System.out.println("I096_NESTED_REENTRANT_ISOLATION=YES");
    }

    /**
     * A nested Closure uses the callee's captured ReturnHome, which is the
     * caller's: its non-local return leaves {@code run}, as physically.
     */
    @Test
    void nestedNonLocalReturnUsesTheCapturedHome() throws Exception {
        String source = """
                run: () => {
                    f: (value) => {
                        probe()
                        escape: () => { ^value + 1 }
                        escape()
                        0
                    }
                    f(41)
                    0
                }
                run()
                """;
        Execution normal = execute(false, source);
        assertInteger(42, normal.value());
        assertPhysicalClosureCallStack(normal.probe());
        assertParity(source, 42);
        System.out.println("I096_NESTED_RETURN_HOME_PRESERVED=YES");
    }

    @Test
    void suspendingUnaryCallUsedAsNumericOperandMustResume()
            throws Exception {
        String terminal = """
                run: () => {
                    step: (() => { 1 }).future()
                    f: (value) => {
                        value + step.value()
                    }
                    f(41)
                }
                run()
                """;

        String numeric = terminal.replace("f(41)", "f(41) * 10");

        assertTrue(!terminal.equals(numeric));

        ProtosExecutionOutcome terminalOutcome =
                ProtosTestExecutionSupport.execute(
                        "i115-async-unary-terminal.protos",
                        terminal,
                        new ProtosCoreBootstrap()
                                .bootstrap(CORE)
                                .newModuleActivation());

        ProtosExecutionOutcome numericOutcome =
                ProtosTestExecutionSupport.execute(
                        "i115-async-unary-numeric.protos",
                        numeric,
                        new ProtosCoreBootstrap()
                                .bootstrap(CORE)
                                .newModuleActivation());

        System.out.println(
                "I115_ASYNC_UNARY_TERMINAL=" + terminalOutcome.state());
        System.out.println(
                "I115_ASYNC_UNARY_NUMERIC=" + numericOutcome.state());

        assertEquals(
                ProtosExecutionOutcome.State.COMPLETED,
                terminalOutcome.state(),
                () -> "terminal error=" + terminalOutcome.error()
                        + ", trace="
                        + terminalOutcome.failureDiagnosticTrace());
        assertInteger(42, terminalOutcome.value());

        assertEquals(
                ProtosExecutionOutcome.State.COMPLETED,
                numericOutcome.state(),
                () -> "numeric error=" + numericOutcome.error()
                        + ", trace="
                        + numericOutcome.failureDiagnosticTrace());
        assertInteger(420, numericOutcome.value());
    }

    @Test
    void suspensionAfterNestedCaptureResumesWithoutReplay() throws Exception {
        String source = """
                run: () => {
                    step: (() => { 1 }).future()
                    visits: 0
                    f: (value) => {
                        visits = visits + 1
                        get: () => value
                        r: value + step.value()
                        get() + r
                    }
                    f(41) * 10 + visits
                }
                run()
                """;
        ProtosExecutionOutcome outcome =
                ProtosTestExecutionSupport.execute(
                        "i096-nested-suspension.protos",
                        source,
                        new ProtosCoreBootstrap().bootstrap(CORE).newModuleActivation());
        assertEquals(
                ProtosExecutionOutcome.State.COMPLETED,
                outcome.state(),
                () -> "outcome=" + outcome.state()
                        + ", errorSlots="
                        + (outcome.error() == null
                                ? null
                                : outcome.error().localSlotsSnapshot())
                        + ", diagnosticFrames="
                        + outcome.failureDiagnosticTrace()
                                .map(trace -> trace.frames())
                                .orElse(null));
        assertInteger(831, outcome.value());
        System.out.println("I096_NESTED_SUSPENSION_PRESERVED=YES");
    }

    /**
     * A self-recursive binding with nested blocks keeps one physical
     * definition: run's terminal C1 call is one physical invocation, and the
     * recursive calls stay in f's own body, so lowering never expands it.
     */
    @Test
    void recursiveNestedBodyKeepsOnePhysicalDefinitionAndTerminalCall() throws Exception {
        String source = """
                run: () => {
                    f: (n) => {
                        (n > 0).ifTrueIfFalse(() => { n + f(n - 1) }, () => { 0 })
                    }
                    f(4)
                }
                run()
                """;
        try (Context context = Context.newBuilder(ProtosLanguage.ID).build()) {
            context.initialize(ProtosLanguage.ID);
            context.enter();
            try {
                ProtosBytecodeRootNode module = lowerRoot(source);
                module.getRootNodes().ensureComplete();
                List<ProtosBytecodeRootNode> roots = groupRoots(module);
                assertEquals(
                        0,
                        candidateRegions(roots).stream().mapToInt(Integer::intValue).sum(),
                        "no inline copy of f anywhere");
                List<ProtosBytecodeRootNode> runCallers =
                        roots.stream()
                                .filter(root -> root != module)
                                .filter(root -> {
                                    List<String> instructions = instructionNames(root);
                                    return count(instructions, "CallUnaryMethod") == 1
                                            && closureCreations(instructions) == 1;
                                })
                                .toList();

                assertEquals(
                        1,
                        runCallers.size(),
                        "one run root creates f and invokes f(4)");

                assertEquals(
                        1,
                        count(
                                instructionNames(runCallers.get(0)),
                                "CallUnaryMethod"),
                        "run has one unary call site");
                assertInteger(10, module.getCallTarget().call(new Probe().module()));
            } finally {
                context.leave();
            }
        }
        assertParity(source, 10);
        // f(4, 0) is never a candidate (arity): f has exactly one root, never
        // duplicated by lowering or reparse.
        assertNoDuplicateRootsAndStableReparse(source, source.replace("f(4)", "f(4, 0)"), 10);
        System.out.println("PERF042_NESTED_RECURSION_ONE_DEFINITION=YES");
    }

    /** PERF042: a binding reassigned before the terminal C1 call runs its current value. */
    @Test
    void terminalC1CallRunsTheReassignedDefinition() throws Exception {
        String source = """
                run: () => {
                    f: (x) => { x + 1 }
                    f = (x) => {
                        probe()
                        x * 2
                    }
                    f(21)
                }
                run()
                """;
        Execution normal = execute(false, source);
        assertInteger(42, normal.value());
        assertEquals(1, normal.probe().calls.get());
        assertEquals(1, count(instructionNames(normal.outermostGuestRoot()), "c.CallUnaryMethod"));
        assertParity(source, 42);
        System.out.println("PERF042_TERMINAL_C1_REASSIGNED=YES");
    }

    /**
     * PERF042 / D013: the terminal C1 operation selects {@code call} at run
     * time, after its operands; an instance override is invoked through its
     * fallback, and the original body runs without or after removing it.
     */
    @Test
    void terminalC1CallSelectsCallOverrideAtRunTime() throws Exception {
        String overridden = """
                run: () => {
                    f: (v) => {
                        probe()
                        v + 1
                    }
                    f.call: (v) => { 100 + v }
                    f(4)
                }
                run()
                """;
        Execution normal = execute(false, overridden);
        assertInteger(104, normal.value());
        assertEquals(0, normal.probe().calls.get(), "the original body never runs");
        assertParity(overridden, 104);

        String plain = """
                run: () => {
                    f: (v) => {
                        probe()
                        v + 1
                    }
                    f(4)
                }
                run()
                """;
        Execution direct = execute(false, plain);
        assertInteger(5, direct.value());
        assertEquals(1, direct.probe().calls.get());
        assertEquals(1, count(instructionNames(direct.outermostGuestRoot()), "CallUnaryMethod"));
        assertParity(plain, 5);

        String removed = """
                run: () => {
                    f: (v) => {
                        probe()
                        v + 1
                    }
                    f.call: (v) => { 100 + v }
                    f.removeSlot("call")
                    f(4)
                }
                run()
                """;
        Execution restored = execute(false, removed);
        assertInteger(5, restored.value());
        assertEquals(1, restored.probe().calls.get());
        assertEquals(
                2,
                count(
                        instructionNames(restored.outermostGuestRoot()),
                        "CallUnaryMethod"),
                "removeSlot and f(4) are distinct unary call sites");
        assertParity(removed, 5);
        System.out.println("PERF042_TERMINAL_C1_CALL_OVERRIDE=YES");
    }

    /** PERF042: a terminal C1 callee keeps its non-local return semantics. */
    @Test
    void terminalC1CallKeepsNonLocalReturn() throws Exception {
        String source = """
                run: () => {
                    f: (value) => {
                        probe()
                        ^value + 1
                        0
                    }
                    f(41)
                }
                run()
                """;
        Execution normal = execute(false, source);
        assertInteger(42, normal.value());
        assertEquals(1, normal.probe().calls.get());
        assertParity(source, 42);
        System.out.println("PERF042_TERMINAL_C1_RETURN_HOME=YES");
    }

    /** PERF042: a terminal C1 callee suspends and resumes without replay. */
    @Test
    void terminalC1CallSuspendsAndResumesWithoutReplay() throws Exception {
        String source = """
                visits: 0
                run: () => {
                    step: (() => { 1 }).future()
                    f: (value) => {
                        visits = visits + 1
                        value + step.value()
                    }
                    f(41)
                }
                run() * 10 + visits
                """;
        ProtosExecutionOutcome outcome =
                ProtosTestExecutionSupport.execute(
                        "perf042-terminal-c1-suspension.protos",
                        source,
                        new ProtosCoreBootstrap().bootstrap(CORE).newModuleActivation());
        assertEquals(
                ProtosExecutionOutcome.State.COMPLETED,
                outcome.state(),
                () -> "outcome=" + outcome.state() + ", error=" + outcome.error());
        assertInteger(421, outcome.value());
        System.out.println("PERF042_TERMINAL_C1_SUSPENSION=YES");
    }

    /**
     * Negative evidence for the nested class: a nested-Closure body whose
     * selection is overridden, and a module-level literal owning its own
     * ReturnHome, keep the physical callee behind the emitted guard.
     */
    @Test
    void nestedBodiesKeepTheFallbackWhenNotAdmitted() throws Exception {
        String override = """
                run: () => {
                    f: (v) => {
                        probe()
                        g: () => v
                        g()
                    }
                    f.call: (v) => {
                        probe()
                        h: () => v * 10
                        h()
                    }
                    a: f(1)
                    f.removeSlot("call")
                    b: f(2)
                    a * 10 + b
                }
                run()
                """;
        Execution overridden = execute(false, override);
        assertInteger(102, overridden.value());
        assertEquals(2, overridden.probe().calls.get());
        assertPhysicalClosureCallStack(overridden.probe());

        assertTrue(
                overridden.probe().stacks.get(0).get(1)
                        != overridden.probe().stacks.get(1).get(1),
                "removing call restores the original Closure definition");
        assertParity(override, 102);

        String moduleLiteral = """
                g: ((v) => {
                    probe()
                    () => v
                })(5)
                g()
                """;
        Execution module = execute(false, moduleLiteral);
        assertInteger(5, module.value());
        List<RootNode> stack = module.probe().stacks.get(0);
        assertEquals(3, stack.size());
        assertInstanceOf(ProtosBuiltinCallRootNode.class, stack.get(0));

        ProtosBytecodeRootNode literalRoot =
                assertInstanceOf(ProtosBytecodeRootNode.class, stack.get(1));
        ProtosBytecodeRootNode moduleRoot =
                assertInstanceOf(ProtosBytecodeRootNode.class, stack.get(2));

        assertTrue(literalRoot != moduleRoot);
        assertSame(literalRoot.getRootNodes(), moduleRoot.getRootNodes());

        assertEquals(
                1, closureCreations(instructionNames(literalRoot)),
                "literal creates its nested Closure");
        assertEquals(1, rootTags(literalRoot.getBytecodeNode()));
        assertEquals(1, rootTags(moduleRoot.getBytecodeNode()));
        assertParity(moduleLiteral, 5);
        System.out.println("I096_NESTED_NOT_ADMITTED_FALLBACK=YES");
    }

    /**
     * The instructions from the single inline site's admission in {@code
     * caller} onward: the admitted body
     * creates nested Closures over the callee's own activation, loaded from
     * the carrier, and never uses the frame-native callback materialization.
     */
    private static List<String> nestedInlineRegion(ProtosBytecodeRootNode caller) {
        List<String> names = instructionNames(caller);
        int admit = position(names, "AdmitsInlineClosureLiteralCall");
        assertTrue(admit >= 0, names::toString);
        // Every prepared call inside the body emits its own fallback load, so
        // the region extends to the end of the root; the fixtures create no
        // Closure in the caller after the site.
        List<String> region = names.subList(admit, names.size());
        assertTrue(count(region, "LoadInlineCallbackActivation") >= 1, region::toString);
        assertEquals(0, count(region, "MaterializeInlineCallbackActivation"), region::toString);
        return region;
    }

    /**
     * {@code admitted} lowers exactly as many group roots as {@code
     * notCandidate}, which differs only in a site that is never inlined, so
     * the inline body duplicated no nested root; a reparse keeps the same
     * roots, order and candidate regions.
     */
    private static void assertNoDuplicateRootsAndStableReparse(
            String admitted, String notCandidate, long expected) throws Exception {
        try (Context context = Context.newBuilder(ProtosLanguage.ID).build()) {
            context.initialize(ProtosLanguage.ID);
            context.enter();
            try {
                ProtosBytecodeRootNode module = lowerRoot(admitted);
                List<ProtosBytecodeRootNode> before = groupRoots(module);
                List<Integer> regionsBefore = candidateRegions(before);
                assertEquals(
                        groupRoots(lowerRoot(notCandidate)).size(),
                        before.size(),
                        "no nested root is lowered twice");

                module.getRootNodes().ensureComplete();
                List<ProtosBytecodeRootNode> after = groupRoots(module);
                assertEquals(before.size(), after.size());
                for (int i = 0; i < before.size(); i++) {
                    assertSame(before.get(i), after.get(i), "root order at " + i);
                    assertTrue(after.get(i).getBytecodeNode().getSourceSection() != null,
                            "source materialized at " + i);
                }
                assertEquals(regionsBefore, candidateRegions(after));
                assertInteger(expected, module.getCallTarget().call(new Probe().module()));
            } finally {
                context.leave();
            }
        }
    }

    /**
     * Structural lowering evidence: the caller root holds the guarded region,
     * one inline parameter load per supplied argument after admission, and
     * the carrier's physical fallback.
     */
    private static void assertInlineSiteWithParameters(
            ProtosBytecodeRootNode caller, int parameters) {
        List<String> names = instructionNames(caller);
        int prepare = position(names, "PrepareInlineClosureLiteralCall");
        int admit = position(names, "AdmitsInlineClosureLiteralCall");
        int fallback = position(names, "LoadInlineLiteralFallbackCall");
        assertTrue(prepare >= 0 && admit > prepare, names::toString);
        assertTrue(fallback > admit, names::toString);
        List<String> region = names.subList(admit, fallback);
        assertEquals(parameters, count(region, "LoadInlineClosureArgument"), names::toString);
        assertEquals(
                2,
                rootTags(caller.getBytecodeNode()),
                "caller RootTag plus the inline callee semantic RootTag");
    }

    private static List<ProtosBytecodeRootNode> groupRoots(
            ProtosBytecodeRootNode root) {
        return root.getRootNodes().getNodes().stream()
                .map(node -> assertInstanceOf(ProtosBytecodeRootNode.class, node))
                .toList();
    }

    private static List<Integer> terminalOneCalls(List<ProtosBytecodeRootNode> roots) {
        return roots.stream()
                .map(root -> count(
                        instructionNames(root.getBytecodeNode()),
                        "c.CallUnaryMethod"))
                .toList();
    }

    /**
     * PERF042: {@code roots} hold one physical terminal C1 call in run, no
     * inline candidate, and a distinct root for the callee.
     */
    private static void assertPhysicalTerminalTopology(
            ProtosBytecodeRootNode module, List<ProtosBytecodeRootNode> roots) {
        // A callee may also contain CallUnaryMethod for an operator
        // such as x + 1. The caller is the root that creates f and
        // owns its terminal invocation.
        List<ProtosBytecodeRootNode> callers = roots.stream()
                .filter(root -> root != module)
                .filter(root -> {
                    List<String> instructions = instructionNames(root);
                    return closureCreations(instructions) == 1
                            && count(instructions, "c.CallUnaryMethod") == 1;
                })
                .toList();
        assertEquals(1, callers.size(), terminalOneCalls(roots)::toString);
        ProtosBytecodeRootNode run = callers.get(0);
        assertTrue(run != module, "the terminal call belongs to run");
        assertEquals(List.of(0, 0, 0), candidateRegions(roots), "no inline C1 candidate");
        assertEquals(
                1,
                roots.stream().filter(root -> root != module && root != run).count(),
                "the callee keeps its own root");
    }

    /**
     * PERF042: both probes run in f's physical root over run. The first is
     * f's own; the second is inner's body, which f's terminal C0 binding call
     * still expands inline (I096, outside the PERF042 C1 scope).
     */

    /**
     * A native probe observes the invoked Closure, its caller and
     * the module as distinct CallTargets. Root cloning may change
     * object identity but must preserve the Bytecode root group.
     */

    /**
     * A source Closure executes in its own Bytecode root.
     * The caller retains exactly one arity-selected call operation,
     * and the callee binds the supplied arguments in its own root.
     */
    private static void assertSourceClosureCallSite(
            Probe probe, String operation) {
        assertEquals(1, probe.calls.get(), probe::toString);
        assertPhysicalClosureCallStack(probe);

        List<RootNode> stack = probe.stacks.get(0);
        ProtosBytecodeRootNode callee =
                assertInstanceOf(
                        ProtosBytecodeRootNode.class, stack.get(1));
        ProtosBytecodeRootNode caller =
                assertInstanceOf(
                        ProtosBytecodeRootNode.class, stack.get(2));

        caller.getRootNodes().ensureComplete();

        assertEquals(
                1,
                count(instructionNames(caller), operation),
                () -> operation + ": " + instructionNames(caller));

        assertEquals(
                operation.equals("CallNilaryMethod") ? 0 : 1,
                count(instructionNames(callee), "CopySuppliedArguments"),
                () -> "callee arguments: " + instructionNames(callee));

        assertEquals(
                1,
                count(instructionNames(callee), "EnterRoot"),
                "callee retains its physical root entry");

        assertEquals(
                1,
                rootTags(caller.getBytecodeNode()),
                "caller retains its own RootTag");

        assertEquals(
                1,
                rootTags(callee.getBytecodeNode()),
                "callee retains its own RootTag");
    }

    private static void assertPhysicalClosureCallStack(Probe probe) {
        assertTrue(!probe.stacks.isEmpty(), "probe must execute");

        for (List<RootNode> stack : probe.stacks) {
            assertEquals(4, stack.size(), probe::toString);

            assertInstanceOf(
                    ProtosBuiltinCallRootNode.class,
                    stack.get(0));

            ProtosBytecodeRootNode callee =
                    assertInstanceOf(
                            ProtosBytecodeRootNode.class,
                            stack.get(1));

            ProtosBytecodeRootNode caller =
                    assertInstanceOf(
                            ProtosBytecodeRootNode.class,
                            stack.get(2));

            ProtosBytecodeRootNode module =
                    assertInstanceOf(
                            ProtosBytecodeRootNode.class,
                            stack.get(3));

            assertTrue(callee != caller,
                    "callee has its own physical root");

            assertTrue(caller != module,
                    "caller has its own physical root");

            assertTrue(callee != module,
                    "callee is distinct from module");

            assertSame(
                    callee.getRootNodes(),
                    caller.getRootNodes(),
                    "callee and caller belong to one Bytecode group");

            assertSame(
                    caller.getRootNodes(),
                    module.getRootNodes(),
                    "caller and module belong to one Bytecode group");
        }
    }

    private static void assertNestedPhysicalStacks(
            List<List<RootNode>> stacks,
            java.util.function.Supplier<String> message) {
        assertEquals(2, stacks.size(), message);

        List<RootNode> outer = stacks.get(0);
        List<RootNode> inner = stacks.get(1);

        assertEquals(4, outer.size(), message);
        assertEquals(5, inner.size(), message);

        assertInstanceOf(ProtosBuiltinCallRootNode.class, outer.get(0));
        assertInstanceOf(ProtosBuiltinCallRootNode.class, inner.get(0));

        ProtosBytecodeRootNode f =
                assertInstanceOf(ProtosBytecodeRootNode.class, outer.get(1));
        ProtosBytecodeRootNode run =
                assertInstanceOf(ProtosBytecodeRootNode.class, outer.get(2));
        ProtosBytecodeRootNode module =
                assertInstanceOf(ProtosBytecodeRootNode.class, outer.get(3));

        ProtosBytecodeRootNode nested =
                assertInstanceOf(ProtosBytecodeRootNode.class, inner.get(1));

        assertTrue(nested != f);
        assertTrue(f != run);
        assertTrue(run != module);

        assertSame(f.getRootNodes(), nested.getRootNodes());
        assertSame(f.getRootNodes(), run.getRootNodes());
        assertSame(run.getRootNodes(), module.getRootNodes());

        assertEquals(
                instructionNames(f),
                instructionNames(
                        assertInstanceOf(ProtosBytecodeRootNode.class, inner.get(2))));
        assertEquals(
                instructionNames(run),
                instructionNames(
                        assertInstanceOf(ProtosBytecodeRootNode.class, inner.get(3))));
        assertEquals(
                instructionNames(module),
                instructionNames(
                        assertInstanceOf(ProtosBytecodeRootNode.class, inner.get(4))));
    }

    private static int closureCreations(List<String> names) {
        return count(names, "MaterializeClosure") + count(names, "MaterializeCurrentClosure");
    }

    private static List<Integer> candidateRegions(List<ProtosBytecodeRootNode> roots) {
        return roots.stream()
                .map(root -> count(
                        instructionNames(root.getBytecodeNode()),
                        "PrepareInlineClosureLiteralCall"))
                .toList();
    }

    private static int count(List<String> names, String operation) {
        return (int) names.stream().filter(name -> name.contains(operation)).count();
    }

    private record Execution(Object value, Probe probe) {
        /** The innermost guest root of the first probe call. */
        ProtosBytecodeRootNode innermostRoot() {
            return probe.stacks.get(0).stream()
                    .filter(ProtosBytecodeRootNode.class::isInstance)
                    .map(ProtosBytecodeRootNode.class::cast)
                    .findFirst()
                    .orElseThrow();
        }

        /** {@code run}'s root: the guest root directly below the module root. */
        ProtosBytecodeRootNode outermostGuestRoot() {
            List<RootNode> stack = probe.stacks.get(0);
            return assertInstanceOf(
                    ProtosBytecodeRootNode.class,
                    stack.get(stack.size() - 2));
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
     * A native {@code probe} binding recording the guest CallTarget stack of
     * every call.
     */
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

    private static ProtosClosureValue closure(String source, ProtosActivation module) {
        return assertInstanceOf(
                ProtosClosureValue.class,
                lowerRoot(source).getCallTarget().call(module));
    }

    private static Object integerArgument(ProtosActivation module) {
        return lowerRoot("41").getCallTarget().call(module);
    }

    private static ProtosClosureExecutionPlanCell planCell(ProtosClosureValue closure) {
        ProtosClosureExecutionPlanCell cell = ProtosClosureExecutionPlanCell.pendingGroup();
        cell.freeze(closure.executionPlan().orElseThrow());
        return cell;
    }

    private static ProtosBytecodeRootNode lowerRoot(String characters) {
        Source source =
                Source.newBuilder(ProtosLanguage.ID, characters, "i096-a.protos").build();
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
}
