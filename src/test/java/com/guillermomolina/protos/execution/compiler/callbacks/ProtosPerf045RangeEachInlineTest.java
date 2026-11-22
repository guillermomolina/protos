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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.parser.ProtosParser;
import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosActorExecutionDomain;
import com.guillermomolina.protos.runtime.ProtosActorModuleState;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosFutureValue;
import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosNumericValueSupport;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosTask;
import com.guillermomolina.protos.semantic.Canonicalizer;
import com.guillermomolina.protos.semantic.ast.CanonicalSequence;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage.LanguageReference;
import com.oracle.truffle.api.bytecode.BytecodeNode;
import com.oracle.truffle.api.bytecode.Instruction;
import com.oracle.truffle.api.bytecode.LocalAccessor;
import com.oracle.truffle.api.bytecode.TagTree;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.management.ExecutionListener;
import org.junit.jupiter.api.Test;

/**
 * Internal iteration of the canonical {@code Range} and {@code InvertedRange}
 * {@code each} and {@code eachStep} (VALUES_AND_COLLECTIONS, ranges): the
 * emitted Bytecode DSL selection and loop, and the observable results of the
 * admitted, dynamic-callback and ordinary paths.
 *
 * <p>Each {@code run} declares a frame binding: the capture proof shared with
 * the other inline callbacks admits module reads only from a parent root that
 * has a frame layout.
 */
final class ProtosPerf045RangeEachInlineTest {
    private static final Path CORE = Path.of("protos", "lib", "core");

    private static final LanguageReference<ProtosLanguage> LANGUAGE_REF =
            LanguageReference.create(ProtosLanguage.class);

    @Test
    void canonicalConstructionAndIterationNeverEnterSourceRangeRoots() throws Exception {
        inContext(() -> {
            String[] programs = {
                "Range(0, 64).each(i => { hook(i) })",
                "Range(64, 0).each(i => { hook(i) })",
                "Range(0, 128).eachStep(2, i => { hook(i) })",
                "InvertedRange(128, 0).eachStep(-2, i => { hook(i) })",
                "Range(9223372036854775806, 9223372036854775870).each(i => { hook(i) })",
                "saved: Range(0, 64)\nsaved.each(i => { hook(i) })",
                "callback: i => { hook(i) }\nRange(0, 64).each(callback)"
            };
            for (int index = 0; index < programs.length; index++) {
                Module module = new Module();
                Module sourceControl = new Module(module.prelude);
                List<List<RootNode>> entered = new ArrayList<>();
                try (ExecutionListener listener = observeRangeRoots(entered)) {
                    Object result = module.run(
                            "run: () => {\nframe: 0\n" + programs[index] + "\n}\nrun()");
                    assertInstanceOf(ProtosNullValue.class, result);
                    assertTrue(entered.isEmpty(),
                            () -> "canonical construction and traversal entered Range source: " + entered);
                    List<String> expected = new ArrayList<>();
                    for (long offset = 0; offset < 64; offset++) {
                        expected.add(switch (index) {
                            case 1 -> Long.toString(64 - offset);
                            case 2 -> Long.toString(offset * 2);
                            case 3 -> Long.toString(128 - offset * 2);
                            case 4 -> java.math.BigInteger.valueOf(Long.MAX_VALUE)
                                    .subtract(java.math.BigInteger.ONE)
                                    .add(java.math.BigInteger.valueOf(offset)).toString();
                            default -> Long.toString(offset);
                        });
                    }
                    assertEquals(expected, module.visitTexts());
                    if (index == 6) {
                        assertInternalDynamicFrames(module, 64);
                    } else {
                        assertInternalLiteralFrames(module, 64);
                    }
                    List<String> names = module.instructions();
                    int selection = indexAfter(names, "c.IsStandardRangeConstructionSelection", -1);
                    int make = indexAfter(names, "c.MakeRange", selection);
                    int iteration = indexAfter(names, "c.IsStandardRangeIterationSelection", make);
                    int prepare = indexAfter(names, "c.PrepareRangeIteration", iteration);
                    assertTrue(selection >= 0 && make > selection && iteration > make
                            && prepare > iteration && indexAfter(names, "c.IterationNext", prepare) > prepare,
                            () -> "construction precedes the internal loop: " + names);
                    assertFalse(names.subList(selection, make + 1).stream()
                                    .anyMatch(name -> name.contains("CallBinaryMethod")),
                            "the admitted constructor branch has no dispatched call");

                    // Positive control: the observer must see the source constructor
                    // when it is explicitly selected by an ordinary member send.
                    sourceControl.run("run: () => { Range.construct(0, 1) }\nrun()");
                    assertFalse(entered.isEmpty(), "the source-root observer must be live");
                }
            }
        });
    }

    @Test
    void terminalConstructorsMatchSourceConstructionAndKeepFreshIdentity() throws Exception {
        inContext(() -> {
            Module seed = new Module();
            String[][] bounds = {
                {"0", "3"}, {"3", "0"}, {"4", "4"},
                {"9223372036854775807", "9223372036854775809"},
                {"-9223372036854775810", "-9223372036854775808"}
            };
            for (String factory : List.of("Range", "InvertedRange")) {
                for (String[] pair : bounds) {
                    Module ordinaryModule = new Module(seed.prelude);
                    Module module = new Module(seed.prelude);
                    Module repeated = new Module(seed.prelude);
                    String args = pair[0] + ", " + pair[1];
                    ProtosObjectValue ordinary = assertInstanceOf(ProtosObjectValue.class,
                            ordinaryModule.run("run: () => { " + factory + ".construct(" + args + ") }\nrun()"));
                    List<List<RootNode>> entered = new ArrayList<>();
                    try (ExecutionListener listener = observeRangeRoots(entered)) {
                        String program = "run: () => { " + factory + "(" + args + ") }\nrun()";
                        ProtosObjectValue first = assertInstanceOf(ProtosObjectValue.class, module.run(program));
                        ProtosObjectValue second = assertInstanceOf(ProtosObjectValue.class, repeated.run(program));
                        assertNotSame(first, second);
                        assertSame(ordinary.parent().orElseThrow(), first.parent().orElseThrow());
                        assertSame(first.parent().orElseThrow(), second.parent().orElseThrow());
                        assertTrue(first.isFrozen() && second.isFrozen());
                        for (String slot : List.of("start", "stop")) {
                            assertEquals(ProtosNumericValueSupport.integerDecimalText(
                                            ordinary.readLocalSlot(slot).orElseThrow()),
                                    ProtosNumericValueSupport.integerDecimalText(
                                            first.readLocalSlot(slot).orElseThrow()));
                        }
                        assertEquals(java.util.Set.of("start", "stop"), first.localSlotsSnapshot().keySet());
                        assertTrue(entered.isEmpty(), "terminal construction must also be internal");
                        assertTrue(module.instructions().stream().anyMatch(name -> name.contains("c.MakeRange")));
                    }
                }
            }
        });
    }

    @Test
    void countedForFollowsTheDirectionOfTheEquivalentRange() throws Exception {
        inContext(() -> {
            String[][] cases = {
                {"for(i, 0, 5) { hook(i) }", "0,1,2,3,4"},
                {"for(i, 5, 0) { hook(i) }", "5,4,3,2,1"},
                {"for(i, 0, 10, 2) { hook(i) }", "0,2,4,6,8"},
                {"for(i, 10, 0, -2) { hook(i) }", "10,8,6,4,2"},
                {"for(i, 3, 3) { hook(i) }", ""}
            };
            for (String[] entry : cases) {
                Module module = new Module();
                module.run("run: () => {\n    " + entry[0] + "\n}\nrun()\nrun()\n");
                List<Long> once = entry[1].isEmpty()
                        ? List.of()
                        : java.util.Arrays.stream(entry[1].split(","))
                                .map(Long::valueOf).toList();
                List<Long> expected = new java.util.ArrayList<>(once);
                expected.addAll(once);
                assertEquals(expected, module.visits, entry[0]);
            }
        });
    }

    @Test
    void constructionStagesBoundsOnceBeforeStepAndCallback() throws Exception {
        inContext(() -> {
            Module module = new Module();
            Object result = module.run(
                    """
                    mark: (value) => { hook(value); value }
                    run: () => {
                        frame: 0
                        Factory: Range
                        Factory(mark(0), mark(4)).eachStep(mark(2), (() => {
                            hook(99)
                            i => { hook(i + 10) }
                        })())
                    }
                    run()
                    """);
            assertInstanceOf(ProtosNullValue.class, result);
            assertEquals(List.of(0L, 4L, 2L, 99L, 10L, 12L), module.visits);
        });
    }

    @Test
    void unprovenFactoriesAndArgumentFormsKeepOrdinaryDispatch() throws Exception {
        inContext(() -> {
            Module module = new Module();
            Object result = module.run(
                    """
                    run: () => {
                        frame: 0
                        Range: { call: (a, b) => { hook(a); b } }
                        Factory: (a, b) => { hook(b); a }
                        Custom: { construct: (a, b) => { hook(a + b); 17 } }
                        hook(Range(1, 2))
                        hook(Factory(3, 4))
                        hook(Custom(5, 6))
                        Custom.construct = (a, b) => { hook(a * b); 19 }
                        hook(Custom(5, 6))
                        Named: (a, b) => { hook(a); b }
                        hook(Named(b: 8, a: 7))
                        hook(Factory(...[9, 10]))
                    }
                    run()
                    """);
            assertInstanceOf(ProtosNullValue.class, result);
            assertEquals(List.of(1L, 2L, 4L, 3L, 11L, 17L, 30L, 19L, 7L, 8L, 10L, 9L),
                    module.visits);
        });
    }

    @Test
    void oneConstructionSiteObservesFactoryAndMethodChanges() throws Exception {
        inContext(() -> {
            Module module = new Module();
            Object result = module.run(
                    """
                    run: () => {
                        frame: 0
                        build: (Factory, a, b) => { Factory(a, b) }
                        canonical: build(Range, 0, 2)
                        hook(canonical.stop)
                        Custom: { call: (a, b) => { a + b } }
                        hook(build(Custom, 2, 3))
                        Custom.call = (a, b) => { a * b }
                        hook(build(Custom, 2, 3))
                        Custom.removeSlot("call")
                        Custom.construct: (a, b) => { b - a }
                        hook(build(Custom, 2, 3))
                        hook(build(InvertedRange, 0, 3).stop)
                        Factory: Range
                        change: () => { Factory = Custom; 0 }
                        staged: Factory(change(), 4)
                        hook(staged.stop)
                        hook(Factory(2, 3))
                    }
                    run()
                    """);
            assertInstanceOf(ProtosNullValue.class, result);
            assertEquals(List.of(2L, 5L, 6L, 1L, 3L, 4L, 1L), module.visits);
        });
    }

    @Test
    void spreadConstructionRemainsOrdinaryAndFrozenCoreRejectsMutation() throws Exception {
        inContext(() -> {
            Module module = new Module();
            List<List<RootNode>> entered = new ArrayList<>();
            try (ExecutionListener listener = observeRangeRoots(entered)) {
                Object result = module.run(
                        """
                        supplied: [0, 2]
                        run: () => {
                            frame: 0
                            r: Range(...supplied)
                            hook(r.stop)
                            Error.handle(() => { Range(start: 0, stop: 2) }, ignored => { hook(3) })
                            Error.handle(() => { Object.construct = null }, ignored => { hook(4) })
                            Error.handle(() => { Range.construct = null }, ignored => { hook(5) })
                            Error.handle(() => { Integer.recognizes = null }, ignored => { hook(6) })
                        }
                        run()
                        """);
                assertInstanceOf(ProtosNullValue.class, result);
                assertEquals(List.of(2L, 3L, 4L, 5L, 6L), module.visits);
                assertFalse(entered.isEmpty(), "spread construction must enter the source implementation");
                assertFalse(module.instructions().stream()
                        .anyMatch(name -> name.contains("IsStandardRangeConstructionSelection")),
                        "neither named nor spread arguments are constructor candidates");
            }
        });
    }

    @Test
    void memberSendIsDistinctFromInvocationOfAnExtractedFactory() throws Exception {
        inContext(() -> {
            Module module = new Module();
            Object result = module.run(
                    """
                    Holder: { Range: Range }
                    run: () => {
                        frame: 0
                        Error.handle(() => { Holder.Range(0, 2) }, ignored => { hook(1) })
                        Factory: Holder.Range
                        hook(Factory(0, 2).stop)
                    }
                    run()
                    """);
            assertInstanceOf(ProtosNullValue.class, result);
            assertEquals(List.of(1L, 2L), module.visits);
        });
    }

    @Test
    void nonIntegerBoundsStillEnterTheSourceConstructorAfterBothOperands() throws Exception {
        inContext(() -> {
            Module module = new Module();
            List<List<RootNode>> entered = new ArrayList<>();
            try (ExecutionListener listener = observeRangeRoots(entered)) {
                Object result = module.run(
                        """
                        run: () => {
                            frame: 0
                            first: () => { hook(1); 0.5 }
                            last: () => { hook(2); 3 }
                            Error.handle(() => { Range(first(), last()) }, ignored => { hook(3) })
                        }
                        run()
                        """);
                assertInstanceOf(ProtosNullValue.class, result);
                assertEquals(List.of(1L, 2L, 3L), module.visits);
                assertFalse(entered.isEmpty(), "invalid bounds must use the source-backed Error path");
            }
        });
    }

    /* Observe physical stacks at source-backed Range root entry, including
     * construction that has already returned by the time an iteration hook runs. */
    private static ExecutionListener observeRangeRoots(List<List<RootNode>> entered) {
        return ExecutionListener.newBuilder().roots(true)
                .sourceFilter(source -> source.getName().endsWith("Range.protos"))
                .onEnter(event -> {
                    List<RootNode> stack = new ArrayList<>();
                    Truffle.getRuntime().iterateFrames(frame -> {
                        stack.add(((RootCallTarget) frame.getCallTarget()).getRootNode());
                        return null;
                    });
                    entered.add(stack);
                }).attach(Context.getCurrent().getEngine());
    }

    @Test
    void literalRangeEachRunsInlineInAnInternalLoopAndAnswersNull() throws Exception {
        inContext(() -> {
            Module module = new Module();
            Object outcome = module.run(
                    """
                    run: () => {
                        frame: 0
                        Range(0, 4).each((i) => {
                            hook(i)
                        })
                    }
                    run()
                    """);
            assertInstanceOf(ProtosNullValue.class, outcome, "Range.each answers null");
            assertEquals(List.of(0L, 1L, 2L, 3L), module.visits);
            List<String> names = module.instructions();
            int selection = indexAfter(names, "c.IsStandardRangeIterationSelection", -1);
            int prepare = indexAfter(names, "c.PrepareRangeIteration", selection);
            int next = indexAfter(names, "c.IterationNext", prepare);
            assertTrue(selection >= 0 && prepare > selection && next > prepare,
                    () -> "the standard Range iteration must be internal: " + names);
            assertTrue(names.stream().anyMatch(name -> name.contains("c.GetArrayEachSnapshot")),
                    () -> "the native Array preparation is still emitted: " + names);
        });
    }

    @Test
    void invertedRangeAndExplicitStepsVisitExactlyTheirIntegers() throws Exception {
        inContext(() -> {
            Module module = new Module();
            module.run(
                    """
                    run: () => {
                        frame: 0
                        InvertedRange(3, -1).each((i) => { hook(i) })
                        Range(-2, 7).eachStep(3, (i) => { hook(i) })
                        InvertedRange(5, 0).eachStep(-2, (i) => { hook(i) })
                        Range(4, 4).each((i) => { hook(i) })
                        InvertedRange(4, 4).eachStep(-1, (i) => { hook(i) })
                    }
                    run()
                    """);
            assertEquals(List.of(3L, 2L, 1L, 0L, -2L, 1L, 4L, 5L, 3L, 1L), module.visits);
            assertInternalLoop(module);
        });
    }

    @Test
    void contextualForAndStoredRangesUseTheSameIteration() throws Exception {
        inContext(() -> {
            Module module = new Module();
            module.run(
                    """
                    run: () => {
                        r: Range(0, 3)
                        for(i, 0, 3) { hook(i) }
                        for(i, 0, 7, 3) { hook(i) }
                        for(i in r) { hook(i) }
                        r.eachStep(2, (i) => { hook(i) })
                    }
                    run()
                    """);
            assertEquals(List.of(0L, 1L, 2L, 0L, 3L, 6L, 0L, 1L, 2L, 0L, 2L), module.visits);
            assertInternalLoop(module);
        });
    }

    @Test
    void dynamicCallbacksAreInvokedOrdinarilyFromTheInternalLoop() throws Exception {
        inContext(() -> {
            Module module = new Module();
            Object outcome = module.run(
                    """
                    run: () => {
                        callback: (i) => { hook(i * 10) }
                        Range(0, 3).each(callback)
                        InvertedRange(2, 0).eachStep(-1, hook)
                    }
                    run()
                    """);
            assertInstanceOf(ProtosNullValue.class, outcome);
            assertEquals(List.of(0L, 10L, 20L, 2L, 1L), module.visits);
            List<String> names = assertInternalLoop(module);
            int next = indexAfter(names, "c.IterationNext", -1);
            assertTrue(indexAfter(names, "c.Call", next) > next,
                    () -> "a dynamic callback is invoked by the shared dispatcher: " + names);
        });
    }

    @Test
    void emptyRangesNeverInspectANonCallableCallbackButInvalidStepsSignal() throws Exception {
        inContext(() -> {
            Module module = new Module();
            Object outcome = module.run(
                    """
                    run: () => {
                        frame: 0
                        a: Range(2, 2).each(5)
                        b: InvertedRange(2, 2).eachStep(-3, 5)
                        c: Error.handle(() => { Range(2, 2).eachStep(0, 5); 0 }, (caught) => 1)
                        d: Error.handle(() => { InvertedRange(5, 1).eachStep(1, 5); 0 }, (caught) => 1)
                        e: Error.handle(() => { Range(0, 2).each(5); 0 }, (caught) => 1)
                        ((a === null) && (b === null) && (c == 1) && (d == 1) && (e == 1))
                            .ifTrueIfFalse(() => { 1 }, () => { 0 })
                    }
                    run()
                    """);
            assertEquals("1", ProtosNumericValueSupport.integerDecimalText(outcome));
        });
    }

    @Test
    void signed64LimitsNeverWrapAndLargeBoundsKeepExactIntegers() throws Exception {
        inContext(() -> {
            Module module = new Module();
            module.run(
                    """
                    run: () => {
                        frame: 0
                        Range(9223372036854775800, 9223372036854775807).eachStep(5, (i) => { hook(i) })
                        InvertedRange(-9223372036854775800, -9223372036854775807 - 1)
                            .eachStep(-6, (i) => { hook(i) })
                        Range(9223372036854775806, 9223372036854775809).each((i) => { hook(i) })
                    }
                    run()
                    """);
            List<String> texts = new ArrayList<>();
            for (Object visit : module.visits) {
                texts.add(ProtosNumericValueSupport.integerDecimalText(visit));
            }
            assertEquals(List.of(
                    "9223372036854775800", "9223372036854775805",
                    "-9223372036854775800", "-9223372036854775806",
                    "9223372036854775806", "9223372036854775807", "9223372036854775808"),
                    texts);
        });
    }

    /**
     * The callback is created lexically during the {@code run} invocation,
     * so it captures the return home of {@code run} (CALLABLES, return homes)
     * and its {@code ^} leaves {@code run} with {@code 14}. The loop is placed
     * directly in {@code run} so its emitted iteration can be inspected. The
     * ordinary comparison iterates through an ordinary user method, a selector
     * that no literal-inline iteration admits, and must observe the same home.
     */
    @Test
    void nonLocalReturnLeavesTheCapturedReturnHomeAtItsVisit() throws Exception {
        inContext(() -> {
            Module specialized = new Module();
            Object specializedOutcome = specialized.run(
                    """
                    run: () => {
                        frame: 0
                        Range(0, 100).each((i) => {
                            hook(i)
                            (i == 2).ifTrue(() => { ^(i * 7) })
                        })
                        -1
                    }
                    run()
                    """);
            assertInternalLoop(specialized);

            Module ordinary = new Module();
            Object ordinaryOutcome = ordinary.run(
                    """
                    counter: {
                        visit: (block) => {
                            index: 0
                            (() => { index < 100 }).whileTrue(() => {
                                block(index)
                                index = index + 1
                            })
                            null
                        }
                    }
                    run: () => {
                        frame: 0
                        counter.visit((i) => {
                            hook(i)
                            (i == 2).ifTrue(() => { ^(i * 7) })
                        })
                        -1
                    }
                    run()
                    """);
            assertTrue(ordinary.instructions().stream()
                            .noneMatch(name -> name.contains("c.IterationNext")),
                    "the ordinary comparison must not use the Range iteration");

            assertEquals("14", ProtosNumericValueSupport.integerDecimalText(ordinaryOutcome));
            assertEquals("14", ProtosNumericValueSupport.integerDecimalText(specializedOutcome));
            assertEquals(List.of(0L, 1L, 2L), ordinary.visits);
            assertEquals(ordinary.visits, specialized.visits);
        });
    }

    @Test
    void errorLeavesTheLoopAtItsVisitAndRunsEnsureOnce() throws Exception {
        inContext(() -> {
            Module module = new Module();
            Object outcome = module.run(
                    """
                    run: () => {
                        frame: 0
                        cleaned: {
                            value: 0
                        }
                        caught: Error.handle(() => {
                            (() => {
                                InvertedRange(9, 0).eachStep(-4, (i) => {
                                    hook(i)
                                    (i == 5).ifTrue(() => { Error().signal() })
                                })
                            }).ensure(() => { cleaned.value = cleaned.value + 1 })
                            0
                        }, (error) => 1)
                        caught * 10 + cleaned.value
                    }
                    run()
                    """);
            assertEquals("11", ProtosNumericValueSupport.integerDecimalText(outcome));
            assertEquals(List.of(9L, 5L), module.visits);
            // The loop is lowered inside the nested ensure/handle Closures, not
            // in run, so only the observable outcome is asserted here; the
            // emitted eachStep loop is covered by
            // eachStepRunsInternallyWithoutContextualSyntax.
        });
    }

    @Test
    void freshCallbackBindingsPerVisitAndCapturedState() throws Exception {
        inContext(() -> {
            Module module = new Module();
            Object outcome = module.run(
                    """
                    run: () => {
                        total: 0
                        Range(0, 4).each((i) => {
                            seen: i * 2
                            total = total + seen
                        })
                        holder: {
                            first: null
                            last: null
                        }
                        Range(1, 4).each((i) => {
                            reader: () => { i }
                            if(i == 1) { holder.first = reader }
                            holder.last = reader
                        })
                        total * 100 + holder.first() + holder.last() * 10
                    }
                    run()
                    """);
            assertEquals("1231", ProtosNumericValueSupport.integerDecimalText(outcome));
        });
    }

    @Test
    void userDefinedRangeLikeObjectsKeepTheirSelectedBehavior() throws Exception {
        inContext(() -> {
            Module module = new Module();
            Object outcome = module.run(
                    """
                    Range: {
                        construct: (a, b) => {
                            custom: {
                                start: a
                                stop: b
                                each: (block) => {
                                    block(99)
                                    42
                                }
                            }
                            custom
                        }
                    }
                    run: () => {
                        frame: 0
                        a: Range(0, 3).each((i) => { hook(i) })
                        for(i, 0, 3) { hook(i + 1) }
                        a
                    }
                    run()
                    """);
            assertEquals("42", ProtosNumericValueSupport.integerDecimalText(outcome));
            assertEquals(List.of(99L, 100L), module.visits);
            List<String> names = module.instructions();
            assertFalse(names.isEmpty());
        });
    }

    @Test
    void arrayEachKeepsOneInlineCallbackRegion() throws Exception {
        inContext(() -> {
            Module module = new Module();
            Object outcome = module.run(
                    """
                    run: () => {
                        frame: 0
                        [5, 6].each((e) => {
                            hook(e)
                        })
                    }
                    run()
                    """);
            assertEquals(List.of(5L, 6L), module.visits);
            assertInstanceOf(com.guillermomolina.protos.runtime.ProtosArrayValue.class, outcome,
                    "Array.each answers its receiver");
            assertSingleSharedLoop(module);
        });
    }

    @Test
    void rangeEachKeepsOneInlineCallbackRegion() throws Exception {
        inContext(() -> {
            Module module = new Module();
            module.run(
                    """
                    run: () => {
                        frame: 0
                        Range(0, 3).each((i) => {
                            hook(i)
                        })
                    }
                    run()
                    """);
            assertEquals(List.of(0L, 1L, 2L), module.visits);
            assertSingleSharedLoop(module);
        });
    }

    @Test
    void dynamicRangeCallbacksKeepTheirOwnPhysicalRoot() throws Exception {
        inContext(() -> {
            Module module = new Module();
            module.run(
                    """
                    run: () => {
                        callback: (i) => { hook(i) }
                        Range(0, 2).each(callback)
                    }
                    run()
                    """);
            assertEquals(List.of(0L, 1L), module.visits);
            ProtosBytecodeRootNode root = module.runRoot();
            assertEquals(1, rootTags(root.getBytecodeNode().getTagTree()),
                    "a dynamic callback has no inline region in run");
            List<String> names = module.instructions();
            int next = indexAfter(names, "c.IterationNext", -1);
            assertTrue(next >= 0 && indexAfter(names, "c.Call", next) > next,
                    () -> "a dynamic callback is invoked by the shared dispatcher: " + names);
        });
    }

    @Test
    void eachStepRunsInternallyWithoutContextualSyntax() throws Exception {
        inContext(() -> {
            Module module = new Module();
            module.run(
                    """
                    run: () => {
                        frame: 0
                        Range(0, 6).eachStep(2, (i) => {
                            hook(i)
                        })
                    }
                    run()
                    """);
            assertEquals(List.of(0L, 2L, 4L), module.visits);
            List<String> names = module.instructions();
            assertEquals(1, count(names, "c.IterationNext"), () -> names.toString());
            assertEquals(0, count(names, "c.GetArrayEachSnapshot"),
                    "eachStep prepares only a Range cursor");
            assertEquals(2, rootTags(module.runRoot().getBytecodeNode().getTagTree()),
                    "run RootTag plus the eachStep inline callback RootTag");
        });
    }

    /*
     * Physical execution evidence. The hook records the guest CallTarget
     * stack of every visit, the technique of the indexed each inline tests:
     * a visit made by the internal loop with an admitted literal callback is
     * entered directly from run ([hook, run, module]); a dynamic callback
     * adds exactly its own root ([hook, callback, run, module]); a visit made
     * by the source-backed Range.eachStep adds that method's root (and the
     * each delegation root), so every assertion below fails when the Range
     * send takes the ordinary dispatch branch.
     */

    @Test
    void literalCanonicalRangeVisitsRunInsideRunWithoutRangeMethodRoots() throws Exception {
        inContext(() -> {
            Module module = new Module();
            Object outcome = module.run(
                    """
                    run: () => {
                        frame: 0
                        Range(0, 64).each((i) => {
                            hook(i)
                            "ignored"
                        })
                    }
                    run()
                    """);
            assertInstanceOf(ProtosNullValue.class, outcome);
            List<Long> expected = new ArrayList<>();
            for (long value = 0; value < 64; value++) {
                expected.add(value);
            }
            assertEquals(expected, module.visits);
            assertInternalLiteralFrames(module, 64);
            assertInternalLoop(module);
            assertEquals(2, rootTags(module.runRoot().getBytecodeNode().getTagTree()),
                    "run RootTag plus one inline callback RootTag");
        });
    }

    @Test
    void literalInvertedRangeAndExplicitStepsRunInsideRun() throws Exception {
        String[] programs = {
            "InvertedRange(64, 0).each((i) => { hook(i) })",
            "Range(0, 64).eachStep(1, (i) => { hook(i) })",
            "InvertedRange(63, -1).eachStep(-1, (i) => { hook(i) })",
        };
        for (String send : programs) {
            inContext(() -> {
                Module module = new Module();
                Object outcome = module.run("run: () => {\n    frame: 0\n    " + send + "\n}\nrun()\n");
                assertInstanceOf(ProtosNullValue.class, outcome, send);
                assertEquals(64, module.visits.size(), send);
                assertInternalLiteralFrames(module, 64);
            });
        }
    }

    @Test
    void dynamicCallbackKeepsItsRootWhileRangeAdvancementStaysInternal() throws Exception {
        inContext(() -> {
            Module module = new Module();
            Object outcome = module.run(
                    """
                    run: () => {
                        callback: (i) => {
                            hook(i)
                            "ignored"
                        }
                        Range(0, 64).eachStep(1, callback)
                    }
                    run()
                    """);
            assertInstanceOf(ProtosNullValue.class, outcome, "the Range send answers null");
            assertEquals(64, module.visits.size());
            assertEquals(0L, module.visits.get(0));
            assertEquals(63L, module.visits.get(63));
            assertInternalDynamicFrames(module, 64);
        });
    }

    @Test
    void nativeCallbackIsCalledDirectlyFromTheInternalLoop() throws Exception {
        inContext(() -> {
            Module module = new Module();
            module.run(
                    """
                    run: () => {
                        frame: 0
                        InvertedRange(3, 0).each(hook)
                    }
                    run()
                    """);
            assertEquals(List.of(3L, 2L, 1L), module.visits);
            assertInternalLiteralFrames(module, 3);
        });
    }

    @Test
    void noncanonicalEachRunsItsOwnSelectedMethod() throws Exception {
        inContext(() -> {
            Module module = new Module();
            Object outcome = module.run(
                    """
                    counter: {
                        each: (block) => {
                            block(7)
                            block(9)
                            "counter"
                        }
                    }
                    Derived: (Range) {
                        init: (...args) => {
                            this.start: args[0]
                            this.stop: args[1]
                        }
                        each: (block) => {
                            block(this.stop)
                            "derived"
                        }
                    }
                    run: () => {
                        frame: 0
                        a: counter.each((i) => { hook(i) })
                        d: Derived(0, 3)
                        b: d.each((i) => { hook(i) })
                        c: d.eachStep(1, (i) => { hook(i) })
                        ((a == "counter") && (b == "derived") && (c === null))
                            .ifTrueIfFalse(() => { 1 }, () => { 0 })
                    }
                    run()
                    """);
            assertEquals("1", ProtosNumericValueSupport.integerDecimalText(outcome));
            assertEquals(List.of(7L, 9L, 3L, 0L, 1L, 2L), module.visits);
            ProtosBytecodeRootNode run = module.runNode();
            for (List<RootNode> stack : module.stacks) {
                assertTrue(stack.size() >= 5,
                        () -> "a selected method executes in its own root: " + stack);
                assertInstanceOf(ProtosBuiltinCallRootNode.class, stack.get(0));
                assertSame(run, stack.get(stack.size() - 2));
                assertSame(module.lastRoot, stack.get(stack.size() - 1));
            }
        });
    }

    /*
     * The bare module harness has no module loader, so the canonical
     * prototype is aliased through a binding here; imported aliases are
     * covered by the range-prelude-and-module conformance file. The alias
     * constructs its Range through ordinary dispatch (no MakeRange); only
     * the semantics and the internal iteration of the result are checked.
     */
    @Test
    void aliasedCanonicalRangeRunsInsideRun() throws Exception {
        inContext(() -> {
            Module module = new Module();
            module.run(
                    """
                    Up: Range
                    run: () => {
                        frame: 0
                        Up(0, 4).each((i) => { hook(i) })
                    }
                    run()
                    """);
            assertEquals(List.of(0L, 1L, 2L, 3L), module.visits);
            assertInternalLiteralFrames(module, 4);
        });
    }

    @Test
    void largeIntegerRangesIterateInternallyWithExactValues() throws Exception {
        String max = "9223372036854775807";
        String min = "(-9223372036854775807 - 1)";
        String big = "(9223372036854775807 + 1000)";
        Object[][] cases = {
            // Entirely signed-64.
            {"Range(-2, 2).each", List.of("-2", "-1", "0", "1")},
            // Entirely arbitrary precision.
            {"Range(" + big + ", " + big + " + 3).each",
                List.of("9223372036854776807", "9223372036854776808", "9223372036854776809")},
            // Primitive to large.
            {"Range(" + max + " - 1, " + max + " + 2).each",
                List.of("9223372036854775806", "9223372036854775807", "9223372036854775808")},
            // Large to primitive.
            {"InvertedRange(" + max + " + 2, " + max + " - 1).each",
                List.of("9223372036854775809", "9223372036854775808", "9223372036854775807")},
            // Around the signed-64 minimum, both directions.
            {"InvertedRange(" + min + " + 1, " + min + " - 2).each",
                List.of("-9223372036854775807", "-9223372036854775808", "-9223372036854775809")},
            {"Range(" + min + " - 2, " + min + " + 1).each",
                List.of("-9223372036854775810", "-9223372036854775809", "-9223372036854775808")},
            // Explicit large steps in both directions.
            {"Range(0, " + big + " * 3).eachStep(" + big + ", ",
                List.of("0", "9223372036854776807", "18446744073709553614")},
            {"InvertedRange(0, 0 - " + big + " * 3).eachStep(0 - " + big + ", ",
                List.of("0", "-9223372036854776807", "-18446744073709553614")},
            // Signed-64 operands whose final advance leaves the signed-64 range.
            {"Range(" + min + ", " + max + ").eachStep(" + max + ", ",
                List.of("-9223372036854775808", "-1", "9223372036854775806")},
            // Empty large ranges.
            {"Range(" + big + ", " + big + ").each", List.of()},
            {"InvertedRange(" + big + ", " + big + ").eachStep(0 - " + big + ", ", List.of()},
        };
        for (Object[] testCase : cases) {
            String send = (String) testCase[0];
            String call = send.endsWith(", ")
                    ? send + "(i) => { hook(i) })"
                    : send + "((i) => { hook(i) })";
            @SuppressWarnings("unchecked")
            List<String> expected = (List<String>) testCase[1];
            inContext(() -> {
                Module module = new Module();
                Object outcome = module.run("run: () => {\n    frame: 0\n    " + call + "\n}\nrun()\n");
                assertInstanceOf(ProtosNullValue.class, outcome, call);
                assertEquals(expected, module.visitTexts(), call);
                assertNormalizedRepresentations(module.visits);
                assertInternalLiteralFrames(module, expected.size());
            });
        }
    }

    @Test
    void largeIntegerRangesWithDynamicCallbacksIterateInternally() throws Exception {
        inContext(() -> {
            Module module = new Module();
            Object outcome = module.run(
                    """
                    run: () => {
                        callback: (i) => {
                            hook(i)
                            "ignored"
                        }
                        big: 9223372036854775807 + 1000
                        Range(9223372036854775806, 9223372036854775809).each(callback)
                        InvertedRange(big, 0 - big).eachStep(0 - big, callback)
                    }
                    run()
                    """);
            assertInstanceOf(ProtosNullValue.class, outcome);
            assertEquals(List.of(
                    "9223372036854775806", "9223372036854775807", "9223372036854775808",
                    "9223372036854776807", "0"), module.visitTexts());
            assertNormalizedRepresentations(module.visits);
            assertInternalDynamicFrames(module, 5);
        });
    }

    /*
     * Direct children of the canonical prototypes assembled without their
     * init, whose bounds the constructors would have normalized to the other
     * prototype. The inherited source methods take their direction from the
     * prototype, so every traversal is empty; a frozen receiver (admitted to
     * the internal loop) must answer exactly what the nonfrozen receiver
     * answers through the ordinary selected method.
     */
    private static final String MAX = "9223372036854775807";
    private static final String MIN = "(-9223372036854775807 - 1)";
    private static final String BIG = "(9223372036854775807 + 1000)";

    private static final String[][] MISMATCHED_BOUNDS = {
        // prototype, start, stop, valid signed-64 step, valid large step
        {"Range", "5", "0", "1", BIG},
        {"InvertedRange", "0", "5", "-1", "(0 - " + BIG + ")"},
        {"Range", MAX + " + 5", MAX + " - 5", "2", BIG},
        {"InvertedRange", MAX + " - 5", MAX + " + 5", "-2", "(0 - " + BIG + ")"},
        {"Range", BIG, "(0 - " + BIG + ")", "1", BIG},
        {"InvertedRange", "(0 - " + BIG + ")", BIG, "-1", "(0 - " + BIG + ")"},
        {"Range", MIN + " + 5", MIN + " - 5", "3", BIG},
        {"InvertedRange", MIN + " - 5", MIN + " + 5", "-3", "(0 - " + BIG + ")"},
    };

    private static String mismatchedProgram(String[] bounds, boolean frozen) {
        return """
                r: (%1$s) {
                    start: %2$s
                    stop: %3$s
                }
                %6$s
                run: () => {
                    frame: 0
                    a: r.each((i) => { hook(i) })
                    b: r.eachStep(%4$s, (i) => { hook(i) })
                    c: r.eachStep(%5$s, (i) => { hook(i) })
                    d: r.each(5)
                    e: r.eachStep(%4$s, 5)
                    f: r.eachStep(%5$s, 5)
                    g: Error.handle(() => { r.eachStep(0 - (%4$s), (i) => { hook(i) }); 0 }, (caught) => 1)
                    h: Error.handle(() => { r.eachStep(0 - (%5$s), (i) => { hook(i) }); 0 }, (caught) => 1)
                    k: Error.handle(() => { r.eachStep(0 - (%4$s), 5); 0 }, (caught) => 1)
                    empty: (a === null) && (b === null) && (c === null) && (d === null) && (e === null) && (f === null)
                    signals: (g == 1) && (h == 1) && (k == 1)
                    intact: (r.parent() === %1$s) && (r.start == %2$s) && (r.stop == %3$s)
                    (empty && signals && intact).ifTrueIfFalse(() => { 1 }, () => { 0 })
                }
                run()
                """.formatted(bounds[0], bounds[1], bounds[2], bounds[3], bounds[4],
                        frozen ? "r.freeze()" : "");
    }

    @Test
    void frozenDirectChildrenWithMismatchedBoundsKeepThePrototypeDirection() throws Exception {
        for (String[] bounds : MISMATCHED_BOUNDS) {
            String label = String.join(" ", bounds);
            List<Object> outcomes = new ArrayList<>();
            List<List<String>> visits = new ArrayList<>();
            for (boolean frozen : new boolean[] {true, false}) {
                inContext(() -> {
                    Module module = new Module();
                    Object outcome = module.run(mismatchedProgram(bounds, frozen));
                    outcomes.add(ProtosNumericValueSupport.integerDecimalText(outcome));
                    visits.add(module.visitTexts());
                });
            }
            assertEquals(List.of("1", "1"), outcomes, label);
            assertEquals(List.of(List.of(), List.of()), visits,
                    () -> "the prototype direction never reaches a visit: " + label);
        }
    }

    @Test
    void frozenDirectChildrenWithOrderedBoundsVisitLikeTheOrdinaryMethod() throws Exception {
        String[][] cases = {
            {"Range", "0", "3", "(i) => { hook(i) }"},
            {"InvertedRange", "3", "0", "(i) => { hook(i) }"},
            {"Range", MAX + " - 1", MAX + " + 2", "(i) => { hook(i) }"},
            {"InvertedRange", MIN + " + 1", MIN + " - 2", "(i) => { hook(i) }"},
        };
        for (String[] testCase : cases) {
            String label = String.join(" ", testCase);
            List<List<String>> visits = new ArrayList<>();
            List<Object> outcomes = new ArrayList<>();
            for (boolean frozen : new boolean[] {true, false}) {
                inContext(() -> {
                    Module module = new Module();
                    Object outcome = module.run("""
                            r: (%1$s) {
                                start: %2$s
                                stop: %3$s
                            }
                            %5$s
                            run: () => {
                                frame: 0
                                r.each(%4$s)
                            }
                            run()
                            """.formatted(testCase[0], testCase[1], testCase[2], testCase[3],
                                    frozen ? "r.freeze()" : ""));
                    outcomes.add(outcome);
                    visits.add(module.visitTexts());
                    assertNormalizedRepresentations(module.visits);
                });
            }
            assertInstanceOf(ProtosNullValue.class, outcomes.get(0), label);
            assertInstanceOf(ProtosNullValue.class, outcomes.get(1), label);
            assertEquals(3, visits.get(0).size(), label);
            assertEquals(visits.get(1), visits.get(0), label);
        }
    }

    @Test
    void largeEmptyRangesIgnoreNonCallableCallbacksAndInvalidLargeStepsSignal() throws Exception {
        inContext(() -> {
            Module module = new Module();
            Object outcome = module.run(
                    """
                    run: () => {
                        frame: 0
                        big: 9223372036854775807 + 1000
                        a: Range(big, big).each(5)
                        b: InvertedRange(big, big).eachStep(0 - big, 5)
                        c: Error.handle(() => { Range(big, big).eachStep(0 - big, 5); 0 }, (caught) => 1)
                        d: Error.handle(() => { InvertedRange(big, 0).eachStep(big, 5); 0 }, (caught) => 1)
                        e: Error.handle(() => { Range(0, big).eachStep(0 - big, (i) => { hook(i) }); 0 }, (caught) => 1)
                        ((a === null) && (b === null) && (c == 1) && (d == 1) && (e == 1))
                            .ifTrueIfFalse(() => { 1 }, () => { 0 })
                    }
                    run()
                    """);
            assertEquals("1", ProtosNumericValueSupport.integerDecimalText(outcome));
            assertTrue(module.visits.isEmpty(), "an invalid step visits nothing");
        });
    }

    @Test
    void largeIntegerNonLocalReturnAndErrorStopTheInternalLoop() throws Exception {
        inContext(() -> {
            Module returning = new Module();
            Object returned = returning.run(
                    """
                    run: () => {
                        frame: 0
                        Range(9223372036854775806, 9223372036854775900).each((i) => {
                            hook(i)
                            (i == 9223372036854775808).ifTrue(() => { ^i })
                        })
                        -1
                    }
                    run()
                    """);
            assertEquals("9223372036854775808", ProtosNumericValueSupport.integerDecimalText(returned));
            assertEquals(List.of("9223372036854775806", "9223372036854775807", "9223372036854775808"),
                    returning.visitTexts());
            // A callback whose nested literal returns non-locally is not
            // admitted inline; it keeps its physical root while the Range
            // advancement stays internal.
            // Its root holds its own RootTag and the inline ifTrue block.
            assertInternalDynamicFrames(returning, 3, 2);

            Module failing = new Module();
            Object failed = failing.run(
                    """
                    run: () => {
                        frame: 0
                        Error.handle(() => {
                            InvertedRange(9223372036854775809, 0).eachStep(-1, (i) => {
                                hook(i)
                                (i == 9223372036854775807).ifTrue(() => { Error().signal() })
                            })
                            0
                        }, (error) => 1)
                    }
                    run()
                    """);
            assertEquals("1", ProtosNumericValueSupport.integerDecimalText(failed));
            assertEquals(List.of("9223372036854775809", "9223372036854775808", "9223372036854775807"),
                    failing.visitTexts());
        });
    }

    @Test
    void largeIntegerVisitsResumeAfterSuspensionWithoutRepetition() throws Exception {
        try (Context context = Context.newBuilder(ProtosLanguage.ID).build()) {
            context.initialize(ProtosLanguage.ID);
            context.enter();
            try {
                ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
                ProtosActorExecutionDomain domain = new ProtosActorExecutionDomain();
                ProtosActivation activation = prelude.newModuleActivation(
                        new ProtosActorModuleState(), null, prelude.newExecutionContext(), domain);
                ProtosFutureValue gate = new ProtosFutureValue(prelude.futurePrototype(), domain);
                List<Object> visits = new ArrayList<>();
                activation.context().createLocalSlot("gate", gate);
                activation.context().createLocalSlot(
                        "hook",
                        ProtosClosureValue.nativeClosure(
                                (caller, supplied) -> {
                                    visits.add(supplied.get(0));
                                    return ProtosNullValue.INSTANCE;
                                }));
                String characters =
                        """
                        run: () => {
                            frame: 0
                            Range(9223372036854775806, 9223372036854775809).each((i) => {
                                hook(i)
                                gate.value()
                            })
                        }
                        run()
                        """;
                ProtosBytecodeRootNode root = lower(characters);
                ProtosTask task = domain.createTask(
                        null,
                        current -> ProtosBytecodeTaskExecution.execute(
                                current, root.getCallTarget(), activation));
                assertTrue(domain.dispatchOne());
                assertEquals(ProtosTask.State.SUSPENDED, task.state());
                assertEquals(1, visits.size());
                assertTrue(gate.resolve(ProtosNullValue.INSTANCE, activation));
                assertTrue(domain.dispatchOne());
                assertEquals(ProtosTask.State.COMPLETED, task.state());
                assertInstanceOf(ProtosNullValue.class, task.result().orElseThrow());
                List<String> texts = new ArrayList<>();
                for (Object visit : visits) {
                    texts.add(ProtosNumericValueSupport.integerDecimalText(visit));
                }
                assertEquals(List.of(
                        "9223372036854775806", "9223372036854775807", "9223372036854775808"), texts);
                ProtosBytecodeRootNode run = assertInstanceOf(
                        ProtosBytecodeRootNode.class, root.getRootNodes().getNode(1));
                assertTrue(run.getBytecodeNode().getInstructionsAsList().stream()
                                .anyMatch(instruction -> instruction.getName().endsWith("IterationNext")),
                        "the suspended loop is the internal Range iteration");
            } finally {
                context.leave();
            }
        }
    }

    /*
     * A numeric for whose callback captures and assigns run's accumulator:
     * the callback body runs inline over the internal Range cursor, the
     * capture reads and writes the one binding of run, and the lowering's
     * storage plan agrees with the emitted loop (a promoted capture reaching
     * the materialized fallback Closure would fail the lowering itself).
     */
    @Test
    void numericForWithCapturedAccumulatorRunsInline() throws Exception {
        inContext(() -> {
            Module module = new Module();
            Object outcome = module.run(
                    """
                    run: () => {
                        frame: 0
                        values: [13, 18, 6, 42]
                        total: 0
                        for(i, 0, values.size()) {
                            total = total + values[i]
                            hook(i)
                        }
                        total
                    }
                    run()
                    """);
            assertEquals("79", ProtosNumericValueSupport.integerDecimalText(outcome));
            assertEquals(List.of(0L, 1L, 2L, 3L), module.visits);
            assertInternalLoop(module);
            assertInternalLiteralFrames(module, 4);
        });
    }

    @Test
    void directInvertedAndSteppedIterationWithCapturedAccumulatorRunInline() throws Exception {
        String[][] cases = {
            {"Range(0, 4).each((i) => {\n total = total * 10 + i\n hook(i)\n })", "123", "4"},
            {"InvertedRange(4, 0).each((i) => {\n total = total * 10 + i\n hook(i)\n })", "4321", "4"},
            {"Range(0, 7).eachStep(3, (i) => {\n total = total * 10 + i\n hook(i)\n })", "36", "3"},
            {"InvertedRange(7, 0).eachStep(-3, (i) => {\n total = total * 10 + i\n hook(i)\n })",
                "741", "3"},
        };
        for (String[] each : cases) {
            inContext(() -> {
                Module module = new Module();
                Object outcome = module.run(
                        "run: () => {\n frame: 0\n total: 0\n "
                                + each[0]
                                + "\n total\n}\nrun()\n");
                assertEquals(each[1], ProtosNumericValueSupport.integerDecimalText(outcome), each[0]);
                assertInternalLoop(module);
                assertInternalLiteralFrames(module, Integer.parseInt(each[2]));
            });
        }
    }

    /*
     * One each site of run alternates between the canonical Range, a
     * receiver whose own each retains its callback, a redefined each and an
     * Array. The retained callback keeps run's binding of total after run
     * has returned: reads, writes and repeated invocations observe that one
     * mutable binding, never a copied value.
     */
    @Test
    void retainedFallbackCallbackKeepsItsLexicalBindingAcrossSelections() throws Exception {
        inContext(() -> {
            Module module = new Module();
            Object outcome = module.run(
                    """
                    store: { block: null }
                    keeper: {
                        each: (block) => {
                            store.block = block
                            store
                        }
                    }
                    run: (receiver) => {
                        frame: 0
                        total: 100
                        returned: receiver.each((i) => {
                            total = total + i
                            hook(total)
                        })
                        if(returned === store) {
                            total = 1000
                            store.block(1)
                            store.block(2)
                        }
                        total
                    }
                    initial: run([1, 2])
                    a: run(Range(0, 3))
                    inverted: run(InvertedRange(3, 1))
                    b: run(keeper)
                    c: run(Range(0, 1))
                    escaped: store.block
                    escaped(10)
                    escaped(20)
                    keeper.each = (block) => {
                        block(7)
                        "redefined"
                    }
                    d: run(keeper)
                    e: run([5])
                    escaped(1)
                    ((initial == 103) && (a == 103) && (inverted == 105) && (b == 1003) && (c == 100) && (d == 107) && (e == 105))
                        .ifTrueIfFalse(() => { 1 }, () => { 0 })
                    """);
            assertEquals("1", ProtosNumericValueSupport.integerDecimalText(outcome));
            assertEquals(
                    List.of(101L, 103L, 100L, 101L, 103L, 103L, 105L,
                            1001L, 1003L, 100L, 1013L, 1033L, 107L, 105L, 1034L),
                    module.visits);
        });
    }

    @Test
    void retainedSteppedCallbackKeepsItsLexicalBindingAcrossSelections() throws Exception {
        inContext(() -> {
            Module module = new Module();
            Object outcome = module.run(
                    """
                    store: { block: null }
                    keeper: {
                        eachStep: (step, block) => {
                            store.block = block
                            store
                        }
                    }
                    run: (receiver, step) => {
                        frame: 0
                        total: 100
                        returned: receiver.eachStep(step, (i) => {
                            total = total + i
                            hook(total)
                        })
                        if(returned === store) {
                            store.block(1)
                        }
                        total
                    }
                    a: run(Range(0, 5), 2)
                    b: run(InvertedRange(5, 0), -2)
                    c: run(keeper, 9)
                    d: run(Range(0, 0), 1)
                    store.block(10)
                    ((a == 106) && (b == 109) && (c == 101) && (d == 100))
                        .ifTrueIfFalse(() => { 1 }, () => { 0 })
                    """);
            assertEquals("1", ProtosNumericValueSupport.integerDecimalText(outcome));
            assertEquals(
                    List.of(100L, 102L, 106L, 105L, 108L, 109L, 101L, 111L),
                    module.visits);
        });
    }

    @Test
    void nestedCapturingCallbacksEmptyAndLargeRangesShareOneBinding() throws Exception {
        inContext(() -> {
            Module module = new Module();
            Object outcome = module.run(
                    """
                    run: () => {
                        frame: 0
                        total: 0
                        count: 0
                        for(i, 0, 3) {
                            for(j, 0, 2) {
                                total = total + (i * j)
                            }
                        }
                        Range(3, 3).each((i) => {
                            total = total + 1000
                        })
                        Range(9223372036854775806, 9223372036854775809).each((i) => {
                            count = count + 1
                        })
                        hook(total)
                        hook(count)
                        total + count
                    }
                    run()
                    """);
            assertEquals("6", ProtosNumericValueSupport.integerDecimalText(outcome));
            assertEquals(List.of(3L, 3L), module.visits);
        });
    }

    /*
     * A callback that creates a Closure keeps its genuine invocation: every
     * visit binds a fresh parameter, and the created Closure captures both
     * that parameter and run's accumulator by binding.
     */
    @Test
    void callbackCreatingClosureCapturesFreshParameterAndSharedAccumulator() throws Exception {
        inContext(() -> {
            Module module = new Module();
            Object outcome = module.run(
                    """
                    store: {
                        first: null
                        last: null
                    }
                    run: () => {
                        frame: 0
                        total: 0
                        for(i, 0, 3) {
                            total = total + 1
                            reader: () => { (i * 100) + total }
                            if(i == 0) {
                                store.first = reader
                            }
                            store.last = reader
                        }
                        total = 50
                        hook(store.first())
                        hook(store.last())
                        total
                    }
                    run()
                    """);
            assertEquals("50", ProtosNumericValueSupport.integerDecimalText(outcome));
            assertEquals(List.of(50L, 250L), module.visits);
        });
    }

    @Test
    void nonLocalReturnAndEnsureLeaveTheCapturedAccumulatorConsistent() throws Exception {
        inContext(() -> {
            Module module = new Module();
            Object outcome = module.run(
                    """
                    state: {
                        total: 0
                        cleaned: 0
                    }
                    find: () => {
                        frame: 0
                        total: 0
                        for(i, 0, 10) {
                            total = total + i
                            if(i == 3) {
                                state.total = total
                                ^i
                            }
                        }
                        -1
                    }
                    run: () => {
                        frame: 0
                        visited: 0
                        (() => {
                            Range(0, 5).each((i) => {
                                visited = visited + 1
                                if(i == 2) { Error().signal() }
                            })
                        }).ensure(() => {
                            state.cleaned = visited
                        })
                    }
                    found: find()
                    failed: Error.handle(() => { run() }, (error) => 7)
                    hook(found)
                    hook(state.total)
                    hook(state.cleaned)
                    failed
                    """);
            assertEquals("7", ProtosNumericValueSupport.integerDecimalText(outcome));
            assertEquals(List.of(3L, 6L, 3L), module.visits);
        });
    }

    /*
     * Every visit entered directly from run: no physical root for the
     * canonical Range.each or Range.eachStep and none for the callback.
     */
    private static void assertInternalLiteralFrames(Module module, int visits) {
        assertEquals(visits, module.stacks.size());
        ProtosBytecodeRootNode run = module.runNode();
        for (List<RootNode> stack : module.stacks) {
            assertEquals(3, stack.size(), () -> "the visit must come from the internal loop: " + stack);
            assertInstanceOf(ProtosBuiltinCallRootNode.class, stack.get(0));
            assertSame(run, stack.get(1));
            assertSame(module.lastRoot, stack.get(2));
        }
    }

    /*
     * Every visit made by the ordinary physical root of a dynamic callback
     * invoked directly from run: no physical root for the canonical
     * Range.each or Range.eachStep.
     */
    private static void assertInternalDynamicFrames(Module module, int visits) {
        assertInternalDynamicFrames(module, visits, 1);
    }

    private static void assertInternalDynamicFrames(Module module, int visits, int callbackRootTags) {
        assertEquals(visits, module.stacks.size());
        ProtosBytecodeRootNode run = module.runNode();
        for (List<RootNode> stack : module.stacks) {
            assertEquals(4, stack.size(), () -> "the callback must be called from the internal loop: " + stack);
            assertInstanceOf(ProtosBuiltinCallRootNode.class, stack.get(0));
            ProtosBytecodeRootNode callback = assertInstanceOf(ProtosBytecodeRootNode.class, stack.get(1));
            assertNotSame(run, callback);
            callback.getRootNodes().ensureComplete();
            assertEquals(callbackRootTags, rootTags(callback.getBytecodeNode().getTagTree()),
                    "a dynamic callback retains its own RootTag");
            assertSame(run, stack.get(2));
            assertSame(module.lastRoot, stack.get(3));
        }
    }

    /* Signed-64 Integers are Long; only values beyond them are large. */
    private static void assertNormalizedRepresentations(List<Object> visits) {
        for (Object visit : visits) {
            if (ProtosNumericValueSupport.isLargeInteger(visit)) {
                String text = ProtosNumericValueSupport.integerDecimalText(visit);
                assertTrue(new java.math.BigInteger(text).bitLength() >= Long.SIZE, text);
            } else {
                assertInstanceOf(Long.class, visit);
            }
        }
    }

    /*
     * A literal callback that reads its own context observes a fresh
     * activation per visit holding only its own parameter and locals,
     * distinct from run's context, under direct each, contextual for and
     * eachStep, on repeated invocations of run.
     */
    @Test
    void callbackObservingItsContextGetsAFreshActivationPerVisit() throws Exception {
        inContext(() -> {
            Module module = new Module();
            Object outcome = module.run(
                    """
                    run: () => {
                        frame: 0
                        outer: 5
                        own: context
                        h: {
                            previous: null
                            distinct: 0
                            owned: 0
                        }
                        Range(0, 3).each((i) => {
                            c: context
                            if(c !== h.previous) { h.distinct = h.distinct + 1 }
                            if((c !== own) && (c.hasSlot("i")) && (c.hasSlot("outer") === false)) {
                                h.owned = h.owned + 1
                            }
                            h.previous = c
                            hook(i + outer)
                        })
                        for(i, 0, 3) {
                            c: context
                            if(c !== h.previous) { h.distinct = h.distinct + 1 }
                            if((c !== own) && (c.hasSlot("i")) && (c.hasSlot("outer") === false)) {
                                h.owned = h.owned + 1
                            }
                            h.previous = c
                            hook(i + outer)
                        }
                        Range(0, 6).eachStep(2, (i) => {
                            c: context
                            if(c !== h.previous) { h.distinct = h.distinct + 1 }
                            if((c !== own) && (c.hasSlot("i")) && (c.hasSlot("outer") === false)) {
                                h.owned = h.owned + 1
                            }
                            h.previous = c
                            hook(i + outer)
                        })
                        h.distinct * 100 + h.owned
                    }
                    run() * 10000 + run()
                    """);
            assertEquals("9090909", ProtosNumericValueSupport.integerDecimalText(outcome));
            List<Long> once = List.of(5L, 6L, 7L, 5L, 6L, 7L, 5L, 7L, 9L);
            List<Long> twice = new ArrayList<>(once);
            twice.addAll(once);
            assertEquals(twice, module.visits);
        });
    }

    /*
     * One each site alternates between the canonical Range and a receiver
     * that retains its callback. The retained callback's context is its own
     * activation, never an alias of an inline visit, and its captured total
     * observes the mutation made after the callback escaped.
     */
    @Test
    void retainedCallbackContextIsNotAnAliasOfAnInlineVisit() throws Exception {
        inContext(() -> {
            Module module = new Module();
            Object outcome = module.run(
                    """
                    store: {
                        block: null
                        seen: null
                    }
                    keeper: {
                        each: (block) => {
                            store.block = block
                            store
                        }
                    }
                    run: (receiver) => {
                        frame: 0
                        total: 100
                        receiver.each((i) => {
                            total = total + i
                            store.seen = context
                            hook(total)
                        })
                        total = total + 1000
                        total
                    }
                    a: run(Range(0, 2))
                    firstInline: store.seen
                    b: run(keeper)
                    escaped: store.block
                    escaped(5)
                    c1: store.seen
                    escaped(6)
                    c2: store.seen
                    c: run(Range(0, 1))
                    c3: store.seen
                    d: run(keeper)
                    store.block(1)
                    escaped(1)
                    ((a == 1101) && (b == 1100) && (c == 1100) && (d == 1100) && (c1 !== c2) && (c1 !== firstInline) && (c3 !== c1) && (c3 !== c2) && (c1.hasSlot("i")) && (c1.hasSlot("total") === false))
                        .ifTrueIfFalse(() => { 1 }, () => { 0 })
                    """);
            assertEquals("1", ProtosNumericValueSupport.integerDecimalText(outcome));
            assertEquals(List.of(100L, 101L, 1105L, 1111L, 100L, 1101L, 1112L), module.visits);
        });
    }

    /*
     * Nested iteration callbacks each observe their own context: the inner
     * activation holds only its parameter, the outer one only its own, and
     * both read the captured outer binding of run.
     */
    @Test
    void nestedCallbacksObserveTheirOwnContexts() throws Exception {
        inContext(() -> {
            Module module = new Module();
            Object outcome = module.run(
                    """
                    run: () => {
                        frame: 0
                        outer: 3
                        h: {
                            good: 0
                            count: 0
                        }
                        Range(0, 2).each((i) => {
                            oc: context
                            Range(0, 2).each((j) => {
                                ic: context
                                if((ic !== oc) && (ic.hasSlot("j")) && (ic.hasSlot("i") === false) && (oc.hasSlot("i")) && (oc.hasSlot("j") === false)) {
                                    h.good = h.good + 1
                                }
                                h.count = h.count + 1
                                hook(i * 10 + j + outer)
                            })
                        })
                        h.count * 100 + h.good
                    }
                    run()
                    """);
            assertEquals("404", ProtosNumericValueSupport.integerDecimalText(outcome));
            assertEquals(List.of(3L, 4L, 13L, 14L), module.visits);
        });
    }

    @Test
    void suspendedRangeEachResumesAfterAdvancementWithoutRepetition() throws Exception {
        assertSuspendedRun(
                """
                run: () => {
                    frame: 0
                    total: 0
                    Range(0, 4).each((i) => {
                        hook(i)
                        if(i == 1) { gate.value() }
                        total = total + i
                    })
                    total
                }
                run()
                """,
                List.of(0L, 1L), List.of(0L, 1L, 2L, 3L), "6");
    }

    @Test
    void suspendedEachStepResumesWithItsParameterAndCapturedBinding() throws Exception {
        assertSuspendedRun(
                """
                run: () => {
                    frame: 0
                    total: 0
                    Range(0, 7).eachStep(3, (i) => {
                        hook(i)
                        if(i == 3) { gate.value() }
                        total = total * 10 + i
                        hook(i)
                    })
                    total
                }
                run()
                """,
                List.of(0L, 0L, 3L), List.of(0L, 0L, 3L, 3L, 6L, 6L), "36");
    }

    @Test
    void errorAfterResumptionLeavesTheSuspendedRangeAtItsVisit() throws Exception {
        assertSuspendedRun(
                """
                run: () => {
                    frame: 0
                    total: 0
                    Error.handle(() => {
                        (() => {
                            Range(0, 4).each((i) => {
                                hook(i)
                                if(i == 1) { gate.value() }
                                if(i == 2) { Error().signal() }
                                total = total + i
                            })
                        }).ensure(() => { hook(99) })
                        -1
                    }, (error) => total * 100 + 7)
                }
                run()
                """,
                List.of(0L, 1L), List.of(0L, 1L, 2L, 99L), "107");
    }

    @Test
    void noncanonicalReceiverCallbackSuspendsThroughOrdinaryDispatch() throws Exception {
        assertSuspendedRun(
                """
                walker: {
                    each: (block) => {
                        block(0)
                        block(1)
                        block(2)
                        walker
                    }
                }
                run: () => {
                    frame: 0
                    total: 0
                    r: walker.each((i) => {
                        hook(i)
                        if(i == 1) { gate.value() }
                        total = total + i
                    })
                    (r === walker).ifTrueIfFalse(() => { total }, () => { -1 })
                }
                run()
                """,
                List.of(0L, 1L), List.of(0L, 1L, 2L), "3");
    }

    /*
     * Runs program as a Task whose callback awaits gate once, resolves gate
     * and resumes: the visits before and after resumption and the result
     * show that the loop continues from its cursor with its bindings.
     */
    static void assertSuspendedRun(
            String program, List<Long> beforeResume, List<Long> all, String result)
            throws Exception {
        try (Context context = Context.newBuilder(ProtosLanguage.ID).build()) {
            context.initialize(ProtosLanguage.ID);
            context.enter();
            try {
                ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
                ProtosActorExecutionDomain domain = new ProtosActorExecutionDomain();
                ProtosActivation activation = prelude.newModuleActivation(
                        new ProtosActorModuleState(), null, prelude.newExecutionContext(), domain);
                ProtosFutureValue gate = new ProtosFutureValue(prelude.futurePrototype(), domain);
                List<Object> visits = new ArrayList<>();
                activation.context().createLocalSlot("gate", gate);
                activation.context().createLocalSlot(
                        "hook",
                        ProtosClosureValue.nativeClosure(
                                (caller, supplied) -> {
                                    visits.add(supplied.get(0));
                                    return ProtosNullValue.INSTANCE;
                                }));
                ProtosBytecodeRootNode root = lower(program);
                ProtosTask task = domain.createTask(
                        null,
                        current -> ProtosBytecodeTaskExecution.execute(
                                current, root.getCallTarget(), activation));
                assertTrue(domain.dispatchOne());
                assertEquals(ProtosTask.State.SUSPENDED, task.state());
                assertEquals(beforeResume, visits);
                assertTrue(gate.resolve(ProtosNullValue.INSTANCE, activation));
                assertTrue(domain.dispatchOne());
                assertEquals(ProtosTask.State.COMPLETED, task.state());
                assertEquals(all, visits, "no callback effect is repeated");
                assertEquals(result, ProtosNumericValueSupport.integerDecimalText(
                        task.result().orElseThrow()));
            } finally {
                context.leave();
            }
        }
    }

    private static ProtosBytecodeRootNode lower(String characters) {
        Source source = Source.newBuilder(ProtosLanguage.ID, characters, "range-each-inline.protos").build();
        CanonicalSequence sequence =
                (CanonicalSequence)
                        new Canonicalizer().canonicalize(new ProtosParser(characters).parseProgram());
        return new CanonicalToBytecodeLowerer(LANGUAGE_REF.get(null), source).lowerRoot(sequence);
    }

    /* One shared loop, with protocol admission and state selection outside it. */
    private static void assertSingleSharedLoop(Module module) {
        ProtosBytecodeRootNode root = module.runRoot();
        BytecodeNode bytecode = root.getBytecodeNode();
        TagTree tree = bytecode.getTagTree();
        assertEquals(2, rootTags(tree), "run RootTag plus one inline callback RootTag");
        List<TagTree> inline = new ArrayList<>();
        collectInlineRoots(tree, true, inline);
        assertEquals(1, inline.size(), "the callback body is emitted once");
        int enter = inline.get(0).getEnterBytecodeIndex();

        List<Instruction> instructions = bytecode.getInstructionsAsList();
        List<Instruction> next = named(instructions, "IterationNext");
        List<Instruction> snapshot = named(instructions, "GetArrayEachSnapshot");
        List<Instruction> prepare = named(instructions, "PrepareRangeIteration");
        assertEquals(1, next.size(), () -> instructions.toString());
        assertEquals(1, snapshot.size(), () -> instructions.toString());
        assertEquals(1, prepare.size(), () -> instructions.toString());
        int advance = next.get(0).getBytecodeIndex();
        assertTrue(advance < enter, "the iterator advances before the callback body");
        // Constant operands follow declaration order: element, then cursor.
        List<Object> accessors = next.get(0).getArguments().stream()
                .filter(argument -> argument.getKind() == Instruction.Argument.Kind.CONSTANT)
                .map(Instruction.Argument::asConstant)
                .filter(LocalAccessor.class::isInstance)
                .toList();
        assertEquals(2, accessors.size(),
                "the iterator writes the element and reads its cursor through constant LocalAccessors");
        assertNotEquals(accessors.get(0), accessors.get(1),
                "the element target and the iteration cursor are distinct locals");

        int prepared = Math.max(snapshot.get(0).getBytecodeIndex(), prepare.get(0).getBytecodeIndex());
        // Continuation composition has its own back edges. Only an edge
        // returning past IterationNext, after state preparation, repeats visits.
        List<Instruction> backward = instructions.stream()
                .filter(instruction -> instruction.getName().contains("branch.backward"))
                .filter(instruction -> instruction.getBytecodeIndex() > enter)
                .filter(instruction -> instruction.getArguments().stream().anyMatch(argument ->
                        argument.getKind() == Instruction.Argument.Kind.BYTECODE_INDEX
                                && argument.asBytecodeIndex() > prepared
                                && argument.asBytecodeIndex() <= advance))
                .toList();
        assertEquals(1, backward.size(), "one physical While back edge to iterator advancement");
        int condition = backward.get(0).getArguments().stream()
                .filter(argument -> argument.getKind() == Instruction.Argument.Kind.BYTECODE_INDEX)
                .findFirst().orElseThrow().asBytecodeIndex();
        assertTrue(condition <= advance && backward.get(0).getBytecodeIndex() > enter);
        assertTrue(snapshot.get(0).getBytecodeIndex() < condition
                        && prepare.get(0).getBytecodeIndex() < condition,
                "both state preparations are outside the repeated condition");
        for (String selection : List.of(
                "IsStandardArrayEachSelection", "IsStandardRangeIterationSelection")) {
            List<Instruction> selected = named(instructions, selection);
            assertEquals(1, selected.size());
            assertTrue(selected.get(0).getBytecodeIndex() < condition);
        }
        List<Instruction> repeated = instructions.stream()
                .filter(instruction -> instruction.getBytecodeIndex() >= condition
                        && instruction.getBytecodeIndex() < enter)
                .toList();
        assertEquals(1, repeated.stream()
                .filter(instruction -> instruction.getName().contains("branch.false")).count(),
                "only the While termination decision belongs to the condition");
        assertFalse(repeated.stream().anyMatch(instruction -> {
            String name = instruction.getName();
            return name.contains("merge.conditional") || name.contains("IsStandard")
                    || (name.contains("branch") && instruction.getBytecodeIndex() < advance);
        }), "no Array/Range decision precedes advancement in the repeated condition");
    }

    @Test
    void sharedConditionContainsOnlyIteratorAdvancementAndWhileTermination() throws Exception {
        inContext(() -> {
            Module module = new Module();
            module.run("""
                    run: () => {
                        frame: 0
                        Range(0, 64).each(i => { hook(i) })
                    }
                    run()
                    """);
            assertEquals(64, module.visits.size());
            assertSingleSharedLoop(module);
        });
    }

    @Test
    void sameEachSiteVisitsArrayRangeInvertedCustomAndArrayInOrder() throws Exception {
        inContext(() -> {
            Module module = new Module();
            Object result = module.run("""
                    custom: {
                        each: (block) => {
                            hook(90)
                            block(7)
                            99
                        }
                    }
                    run: (receiver) => {
                        total: 0
                        answer: receiver.each(i => {
                            hook(i)
                            total = total + i
                        })
                        hook(total)
                        answer
                    }
                    values: [3, 4]
                    a: run(values)
                    b: run(Range(1, 3))
                    d: run(InvertedRange(3, 1))
                    c: run(custom)
                    fresh: [5, 6]
                    e: run(fresh)
                    ((a === values) && (b === null) && (d === null) && (c == 99) && (e === fresh))
                        .ifTrueIfFalse(() => { 1 }, () => { 0 })
                    """);
            assertEquals("1", ProtosNumericValueSupport.integerDecimalText(result));
            assertEquals(List.of(3L, 4L, 7L, 1L, 2L, 3L, 3L, 2L, 5L,
                    90L, 7L, 7L, 5L, 6L, 11L), module.visits);
            for (int index : List.of(0, 1, 3, 4, 6, 7, 12, 13)) {
                assertSame(module.runRoot(), module.stacks.get(index).get(1),
                        "standard callbacks stay in run after selection changes");
            }
            assertNotSame(module.runRoot(), module.stacks.get(10).get(1),
                    "the custom each invokes its callback through ordinary dispatch");
            assertSingleSharedLoop(module);
        });
    }

    @Test
    void originalIndexedSumKeepsOneInlineMessageIterationAndDirectOperations() throws Exception {
        inContext(() -> {
            Module module = new Module();
            List<RootNode> entered = new ArrayList<>();
            Object result;
            try (ExecutionListener listener = ExecutionListener.newBuilder().roots(true)
                    .sourceFilter(source -> source.getName().equals("range-each-inline.protos"))
                    .onEnter(event -> Truffle.getRuntime().iterateFrames(frame -> {
                        entered.add(((RootCallTarget) frame.getCallTarget()).getRootNode());
                        return Boolean.TRUE;
                    }))
                    .attach(Context.getCurrent().getEngine())) {
                result = module.run("""
                    run: () => {
                        values: [
                            13, 18, 6, 42, 43, 1, 58, 4,
                            7, 32, 8, 59, 9, 64, 38, 37,
                            25, 61, 24, 21, 23, 63, 35, 31,
                            34, 30, 17, 48, 51, 41, 27, 40,
                            15, 33, 46, 2, 28, 44, 54, 56,
                            62, 11, 22, 12, 19, 50, 26, 55,
                            60, 53, 57, 36, 5, 45, 14, 16,
                            20, 39, 47, 10, 3, 52, 29, 49
                        ]
                        total: 0
                        n: values.size()
                        for(i, 0, n) {
                            total = total + values[i]
                        }
                        total
                    }
                    run()
                    """);
            }
            assertFalse(entered.isEmpty(), "the physical-root observer must be live");
            assertTrue(entered.stream().anyMatch(root -> root == module.runRoot()));
            assertTrue(entered.stream().allMatch(root -> root == module.lastRoot || root == module.runRoot()),
                    () -> "only the module and run physical roots may execute: " + entered);
            assertEquals("2080", ProtosNumericValueSupport.integerDecimalText(result));
            assertSingleSharedLoop(module);
            assertNoTerminalScratchClears(module.runRoot());
            printStaticBciManifest(module.runRoot());
            List<String> names = module.instructions();
            assertEquals(0, named(module.runRoot().getBytecodeNode().getInstructionsAsList(),
                    "ArrayEachNext").size());
            assertEquals(0, named(module.runRoot().getBytecodeNode().getInstructionsAsList(),
                    "RangeIterationNext").size());
            for (String operation : List.of("NumberAdd", "BinarySubscript", "GetLen")) {
                assertEquals(1, count(names, "c." + operation),
                        () -> "one direct operation, without a duplicated sum body: " + names);
            }
            assertEquals(1, count(names, "c.IsStandardRangeIterationSelection"),
                    "numeric for retains ordinary Range.each selection");
        });
    }

    @Test
    void arrayAndRangeSuspendAtFirstMiddleAndLastWithoutRepeatedVisits() throws Exception {
        for (String receiver : List.of("[0, 1, 2, 3, 4]", "Range(0, 5)")) {
            for (int stop : List.of(0, 2, 4)) {
                assertSuspendedRun("""
                        run: () => {
                            frame: 0
                            total: 0
                            %s.each(i => {
                                hook(i)
                                if(i == %d) { gate.value() }
                                total = total + i
                            })
                            total
                        }
                        run()
                        """.formatted(receiver, stop),
                        List.of(0L, 1L, 2L, 3L, 4L).subList(0, stop + 1),
                        List.of(0L, 1L, 2L, 3L, 4L), "10");
            }
        }
    }

    @Test
    void resumedNonLocalReturnRunsEnsureOnceAndStopsLaterVisits() throws Exception {
        assertSuspendedRun("""
                find: () => {
                    frame: 0
                    Range(0, 5).each(i => {
                        hook(i)
                        if(i == 2) {
                            gate.value()
                            ^i
                        }
                    })
                    -1
                }
                run: () => {
                    frame: 0
                    (() => find()).ensure(() => { hook(99) })
                }
                run()
                """, List.of(0L, 1L, 2L), List.of(0L, 1L, 2L, 99L), "2");
    }

    @Test
    void scalarTerminalAfterSuspendedNumericForReturnsTheResumedValue() throws Exception {
        assertSuspendedRun("""
                run: () => {
                    values: [1, 2, 3, 4]
                    total: 0
                    n: values.size()
                    for(i, 0, n) {
                        hook(i)
                        if(i == 1) { gate.value() }
                        total = total + values[i]
                    }
                    total
                }
                run()
                """, List.of(0L, 1L), List.of(0L, 1L, 2L, 3L), "10");
    }

    @Test
    void closureTerminalAfterSuspendedPrefixKeepsItsCapture() throws Exception {
        assertSuspendedRun("""
                run: () => {
                    values: [1, 2, 3, 4]
                    total: 0
                    n: values.size()
                    for(i, 0, n) {
                        hook(i)
                        if(i == 2) { gate.value() }
                        total = total + values[i]
                    }
                    () => total * 10
                }
                run()()
                """, List.of(0L, 1L, 2L), List.of(0L, 1L, 2L, 3L), "100");
    }

    /*
     * PERF045: after the discarded prefix of run, the scalar terminal read
     * returns without clearing the prefix scratch locals.
     */
    private static void assertNoTerminalScratchClears(ProtosBytecodeRootNode root) {
        List<Instruction> instructions = root.getBytecodeNode().getInstructionsAsList();
        int last = instructions.size() - 1;
        while (last >= 0 && !instructions.get(last).getName().equals("return")) {
            last--;
        }
        assertTrue(last > 0, () -> "run ends with a return: " + instructions);
        List<String> tail = new ArrayList<>();
        for (int index = last - 1; index >= 0; index--) {
            String name = instructions.get(index).getName();
            if (name.contains("branch")) {
                break;
            }
            tail.add(name);
        }
        assertFalse(tail.contains("clear.local"),
                () -> "the terminal read follows no scratch clear.local: " + tail);
    }

    /* Static instruction rows only: these are never execution counts. */
    private static void printStaticBciManifest(ProtosBytecodeRootNode root) {
        BytecodeNode bytecode = root.getBytecodeNode();
        for (Instruction instruction : bytecode.getInstructionsAsList()) {
            List<Integer> locals = new ArrayList<>();
            List<Integer> targets = new ArrayList<>();
            List<String> accessors = new ArrayList<>();
            for (Instruction.Argument argument : instruction.getArguments()) {
                switch (argument.getKind()) {
                    case LOCAL_OFFSET -> locals.add(argument.asLocalOffset());
                    case BYTECODE_INDEX -> targets.add(argument.asBytecodeIndex());
                    case CONSTANT -> {
                        if (argument.asConstant() instanceof LocalAccessor accessor) {
                            accessors.add(accessor.toString());
                        }
                    }
                    default -> { }
                }
            }
            int bci = instruction.getBytecodeIndex();
            String name = ProtosInstructionNames.of(instruction);
            System.out.println("PERF045_STATIC_BCI=kind=static|root=run|rootClass=" + root.getClass().getSimpleName()
                    + "|bci=" + bci + "|name=" + name
                    + "|family=" + staticInstructionFamily(instruction.getName())
                    + "|localOffsets=" + locals + "|bytecodeIndices=" + targets
                    + "|localAccessors=" + accessors
                    + "|rootTag=" + staticRootRegion(bytecode.getTagTree(), bci));
        }
    }

    private static String staticRootRegion(TagTree tree, int bci) {
        if (tree == null) {
            return "unknown";
        }
        for (TagTree child : tree.getTreeChildren()) {
            String region = staticRootRegion(child, bci);
            if (!region.equals("unknown")) {
                return region;
            }
        }
        int enter = tree.getEnterBytecodeIndex();
        int exit = tree.getReturnBytecodeIndex();
        return tree.hasTag(StandardTags.RootTag.class) && enter < exit && enter <= bci && bci <= exit
                ? enter + ".." + exit : "unknown";
    }

    private static String staticInstructionFamily(String rawName) {
        String name = rawName.toLowerCase(java.util.Locale.ROOT);
        if (name.contains("tag.")) {
            return "tags";
        }
        if (name.contains("continuation") || name.contains("yield") || name.contains("resume")) {
            return "continuations";
        }
        if (name.contains("cell") || name.contains("binding") || name.contains("capture")
                || name.contains("lexical")) {
            return "lexical-cells";
        }
        if (name.contains("local")) {
            return "local-loads-and-stores";
        }
        if (name.contains("numberadd") || name.contains("binarysubscript") || name.contains("getlen")) {
            return "algorithmic-operations";
        }
        if (name.contains("selection") || name.contains("dispatch") || name.contains("invoke")
                || name.contains("send") || name.contains("lookup") || name.contains("isstandard")) {
            return "protocol-and-selection";
        }
        if (name.contains("iteration") || name.contains("snapshot") || name.contains("branch")
                || name.contains("return") || name.contains("merge")) {
            return "iteration-and-control";
        }
        return "other";
    }

    private static void collectInlineRoots(TagTree tree, boolean outermost, List<TagTree> into) {
        if (tree == null) {
            return;
        }
        boolean root = tree.hasTag(StandardTags.RootTag.class);
        if (root && !outermost) {
            into.add(tree);
        }
        for (TagTree child : tree.getTreeChildren()) {
            collectInlineRoots(child, outermost && !root, into);
        }
    }

    private static List<Instruction> named(List<Instruction> instructions, String operation) {
        return instructions.stream()
                .filter(instruction -> ProtosInstructionNames.of(instruction).contains("c." + operation))
                .toList();
    }

    private static int count(List<String> names, String operation) {
        return (int) names.stream().filter(name -> name.contains(operation)).count();
    }

    private static int rootTags(TagTree tree) {
        if (tree == null) {
            return 0;
        }
        int count = tree.hasTag(StandardTags.RootTag.class) ? 1 : 0;
        for (TagTree child : tree.getTreeChildren()) {
            count += rootTags(child);
        }
        return count;
    }

    private static List<String> assertInternalLoop(Module module) {
        List<String> names = module.instructions();
        int next = indexAfter(names, "c.IterationNext", -1);
        assertTrue(next >= 0, () -> "the standard Range iteration must be internal: " + names);
        return names;
    }

    private interface Body {
        void run() throws Exception;
    }

    private static void inContext(Body body) throws Exception {
        try (Context context = Context.newBuilder(ProtosLanguage.ID).build()) {
            context.initialize(ProtosLanguage.ID);
            context.enter();
            try {
                body.run();
            } finally {
                context.leave();
            }
        }
    }

    /**
     * A bootstrapped module whose source binding {@code hook} is a native
     * callable that records its argument.
     */
    private static final class Module {
        private final List<Object> visits = new ArrayList<>();
        private final List<List<RootNode>> stacks = new ArrayList<>();
        private final ProtosPrelude prelude;
        private final ProtosActivation activation;
        private ProtosBytecodeRootNode lastRoot;

        Module() throws Exception {
            this(new ProtosCoreBootstrap().bootstrap(CORE));
        }

        Module(ProtosPrelude prelude) {
            this.prelude = prelude;
            activation = prelude.newModuleActivation();
            activation.context().createLocalSlot(
                    "recorder",
                    ProtosClosureValue.nativeClosure(
                            (caller, supplied) -> {
                                visits.add(supplied.get(0));
                                List<RootNode> stack = new ArrayList<>();
                                Truffle.getRuntime().iterateFrames(frame -> {
                                    stack.add(((RootCallTarget) frame.getCallTarget()).getRootNode());
                                    return null;
                                });
                                stacks.add(stack);
                                return ProtosNullValue.INSTANCE;
                            }));
        }

        Object run(String program) {
            String characters = "hook: recorder\n" + program;
            Source source =
                    Source.newBuilder(ProtosLanguage.ID, characters, "range-each-inline.protos")
                            .build();
            CanonicalSequence sequence =
                    (CanonicalSequence)
                            new Canonicalizer()
                                    .canonicalize(new ProtosParser(characters).parseProgram());
            lastRoot = new CanonicalToBytecodeLowerer(LANGUAGE_REF.get(null), source)
                    .lowerRoot(sequence);
            return lastRoot.getCallTarget().call(activation);
        }

        /* The physical root of run, resolved through its execution plan. */
        ProtosBytecodeRootNode runNode() {
            return runRoot();
        }

        List<String> visitTexts() {
            List<String> texts = new ArrayList<>();
            for (Object visit : visits) {
                texts.add(ProtosNumericValueSupport.integerDecimalText(visit));
            }
            return texts;
        }

        ProtosBytecodeRootNode runRoot() {
            ProtosClosureValue run =
                    (ProtosClosureValue)
                            activation.context().readLocalSlot("run").orElseThrow();
            ProtosBytecodeRootNode root =
                    run.executionPlan().orElseThrow().bytecodeActivationRootForTesting();
            root.getRootNodes().ensureComplete();
            return root;
        }

        List<String> instructions() {
            return runRoot().getBytecodeNode()
                    .getInstructionsAsList()
                    .stream()
                    .map(ProtosInstructionNames::of)
                    .toList();
        }
    }

    private static int indexAfter(List<String> names, String operation, int previous) {
        for (int index = previous + 1; index < names.size(); index++) {
            if (names.get(index).contains(operation)) {
                return index;
            }
        }
        return -1;
    }
}
