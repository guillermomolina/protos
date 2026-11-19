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
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.parser.ProtosParser;
import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosActorExecutionDomain;
import com.guillermomolina.protos.runtime.ProtosActorModuleState;
import com.guillermomolina.protos.runtime.ProtosFutureValue;
import com.guillermomolina.protos.runtime.ProtosTask;
import com.guillermomolina.protos.runtime.ProtosArrayValue;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosMapValue;
import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.guillermomolina.protos.runtime.ProtosNumericValueSupport;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.semantic.Canonicalizer;
import com.guillermomolina.protos.semantic.ast.CanonicalSequence;
import com.oracle.truffle.api.TruffleLanguage.LanguageReference;
import com.oracle.truffle.api.source.Source;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.graalvm.polyglot.Context;
import org.junit.jupiter.api.Test;

/**
 * I145-A / D239-C1: standard {@code Object.call} sends the ordinary
 * {@code construct} message for a non-Closure receiver, and the standard
 * {@code Object.construct} allocates the child and sends {@code init}.
 *
 * <p>Deliberately Java-side: these tests observe the exact prepared and
 * structured carriers, the selected executable identities and represented
 * result identities, which source conformance cannot use as an oracle.
 */
class ProtosI145AConstructProtocolTest {
    private static final LanguageReference<ProtosLanguage> LANGUAGE_REF =
            LanguageReference.create(ProtosLanguage.class);

    @Test
    void initializerCapturesOuterHomeAndConstructionDoesNotCloseIt() throws IOException {
        ProtosActivation module = corePrelude().newModuleActivation();
        Object result = ProtosTestExecutionSupport.evaluate("""
                owner: () => {
                    Thing: {
                        init: (value) => {
                            this.escape: () => { ^value }
                        }
                    }
                    made: Thing(42)
                    made.escape()
                    0
                }
                owner()
                """, module);
        assertEquals("42", ProtosNumericValueSupport.integerDecimalText(result));
    }

    @Test
    void rootPublishesDistinctFrozenStandardConstruct() throws IOException {
        corePrelude();
        ProtosObjectValue root = ProtosObjectValue.rootObject();

        ProtosClosureValue construct =
                assertInstanceOf(
                        ProtosClosureValue.class,
                        root.readLocalSlot("construct").orElseThrow());
        assertTrue(construct.isFrozen());
        assertTrue(root.isFrozen());
        assertTrue(ProtosStandardObjectProtocol.isCanonicalStandardConstructSelection(
                construct, root));
        assertFalse(ProtosStandardObjectProtocol.isCanonicalStandardCallSelection(
                construct, root));
        assertNotSame(construct, root.readLocalSlot("call").orElseThrow());
    }

    @Test
    void structuredObjectCallSendsConstructWithoutPreallocatingAChild()
            throws IOException {
        ProtosPrelude prelude = corePrelude();
        ProtosActivation activation = prelude.newModuleActivation();

        Object result =
                ProtosTestExecutionSupport.evaluate(
                        """
                        trace: { inits: 0 }
                        Factory: {
                            init: () => {
                                trace.inits = trace.inits + 1
                            }
                            construct: () => null
                        }
                        [Factory(), trace.inits]
                        """,
                        activation);

        ProtosArrayValue values =
                assertInstanceOf(ProtosArrayValue.class, result);

        assertSame(ProtosNullValue.INSTANCE, values.indexedAt(0));
        assertEquals(
                "0",
                ProtosNumericValueSupport.integerDecimalText(
                        values.indexedAt(1)),
                "custom construct must not allocate and initialize a hidden child");
    }

    @Test
    void standardConstructPreparesFreshChildAndIgnoresInitResult()
            throws IOException {
        ProtosPrelude prelude = corePrelude();
        ProtosActivation activation = prelude.newModuleActivation();

        Object result =
                ProtosTestExecutionSupport.evaluate(
                        """
                        Point: {
                            init: () => {
                                this.marker: 42
                                99
                            }
                        }
                        Point()
                        """,
                        activation);

        ProtosObjectValue prototype =
                assertInstanceOf(
                        ProtosObjectValue.class,
                        activation.context()
                                .readLocalSlot("Point")
                                .orElseThrow());

        ProtosObjectValue instance =
                assertInstanceOf(ProtosObjectValue.class, result);

        assertSame(prototype, instance.parent().orElseThrow());

        assertEquals(
                "42",
                ProtosNumericValueSupport.integerDecimalText(
                        instance.readLocalSlot("marker").orElseThrow()));

        assertNotSame(
                prototype,
                instance,
                "standard construct must return the new instance");
    }

    @Test
    void customConstructResultIsTheExactCallResult() throws IOException {
        ProtosPrelude prelude = corePrelude();
        ProtosActivation activation = prelude.newModuleActivation();

        Object result =
                ProtosTestExecutionSupport.evaluate(
                        """
                        inits: { count: 0 }
                        Other: {}
                        cached: {}
                        NullFactory: { construct: () => null }
                        ScalarFactory: { construct: (n) => n + 1 }
                        CachedFactory: { construct: () => cached }
                        OtherFactory: {
                            init: () => {
                                inits.count = inits.count + 1
                            }
                            construct: () => Other()
                        }
                        [NullFactory(), ScalarFactory(4), CachedFactory() === cached,
                         OtherFactory().parent() === Other, inits.count]
                        """,
                        activation);

        ProtosArrayValue values = assertInstanceOf(ProtosArrayValue.class, result);
        assertSame(ProtosNullValue.INSTANCE, values.indexedAt(0));
        assertEquals("5", ProtosNumericValueSupport.integerDecimalText(values.indexedAt(1)));
        assertSame(Boolean.TRUE, values.indexedAt(2));
        assertSame(Boolean.TRUE, values.indexedAt(3));
        assertEquals("0", ProtosNumericValueSupport.integerDecimalText(values.indexedAt(4)));
    }

    @Test
    void originalReceiverAndHomeReachInheritedConstruct() throws IOException {
        ProtosPrelude prelude = corePrelude();
        ProtosActivation activation = prelude.newModuleActivation();

        Object result =
                ProtosTestExecutionSupport.evaluate(
                        """
                        Base: {
                            construct: (...args) => {
                                instance: super.construct(...args)
                                [this, instance]
                            }
                            init: (value) => {
                                this.value: value
                            }
                        }
                        Derived: Base {}
                        pair: Derived(3)
                        [pair[0] === Derived, pair[1].parent() === Derived, pair[1].value]
                        """,
                        activation);

        ProtosArrayValue values = assertInstanceOf(ProtosArrayValue.class, result);
        assertSame(Boolean.TRUE, values.indexedAt(0));
        assertSame(Boolean.TRUE, values.indexedAt(1));
        assertEquals("3", ProtosNumericValueSupport.integerDecimalText(values.indexedAt(2)));
    }

    @Test
    void nativeStateFamiliesConstructThroughInheritedCall() throws IOException {
        ProtosPrelude prelude = corePrelude();
        ProtosActivation activation = prelude.newModuleActivation();

        Object result =
                ProtosTestExecutionSupport.evaluate(
                        """
                        [Array(1, 2), Map(), Range(3, 1).parent() === InvertedRange,
                         Array.hasSlot("call"), Array.hasSlot("construct")]
                        """,
                        activation);

        ProtosArrayValue values = assertInstanceOf(ProtosArrayValue.class, result);
        ProtosArrayValue array = assertInstanceOf(ProtosArrayValue.class, values.indexedAt(0));
        assertEquals(2, array.indexedSize());
        assertSame(prelude.arrayPrototype(), array.parent().orElseThrow());
        ProtosMapValue map = assertInstanceOf(ProtosMapValue.class, values.indexedAt(1));
        assertSame(prelude.mapPrototype(), map.parent().orElseThrow());
        assertSame(Boolean.TRUE, values.indexedAt(2));
        assertSame(Boolean.FALSE, values.indexedAt(3));
        assertSame(Boolean.TRUE, values.indexedAt(4));
    }

    @Test
    void namedArgumentsForwardThroughCallAndConstructToInit() throws IOException {
        ProtosPrelude prelude = corePrelude();
        ProtosActivation activation = prelude.newModuleActivation();

        Object result =
                ProtosTestExecutionSupport.evaluate(
                        """
                        Point: {
                            init: (x: 0, y: 0) => {
                                this.x: x
                                this.y: y
                            }
                        }
                        Custom: {
                            construct: (left: 1, right: 2) => left * 10 + right
                        }
                        p: Point(y: 7)
                        [p.x, p.y, Custom(right: 5), Custom(right: 3, left: 4)]
                        """,
                        activation);

        ProtosArrayValue values = assertInstanceOf(ProtosArrayValue.class, result);
        assertEquals("0", ProtosNumericValueSupport.integerDecimalText(values.indexedAt(0)));
        assertEquals("7", ProtosNumericValueSupport.integerDecimalText(values.indexedAt(1)));
        assertEquals("15", ProtosNumericValueSupport.integerDecimalText(values.indexedAt(2)));
        assertEquals("43", ProtosNumericValueSupport.integerDecimalText(values.indexedAt(3)));
    }

    @Test
    void failurePrecedenceRejectsNonClosureConstructWithoutFallback()
            throws IOException {
        ProtosPrelude prelude = corePrelude();
        ProtosActivation activation = prelude.newModuleActivation();

        ProtosExecutionOutcome nonClosureConstruct =
                ProtosTestExecutionSupport.execute(
                        """
                        Thing: { construct: 5 }
                        Thing()
                        """,
                        activation);
        assertEquals(ProtosExecutionOutcome.State.FAILED, nonClosureConstruct.state());

        ProtosExecutionOutcome frozenRoot =
                ProtosTestExecutionSupport.execute(
                        """
                        Object.construct = null
                        """,
                        prelude.newModuleActivation());
        assertEquals(ProtosExecutionOutcome.State.FAILED, frozenRoot.state());
        assertTrue(ProtosStandardObjectProtocol.isCanonicalStandardConstructSelection(
                ProtosObjectValue.rootObject().readLocalSlot("construct").orElseThrow(),
                ProtosObjectValue.rootObject()));
    }

    /**
     * Task / structured-control regression: both a custom construct and the
     * init reached through the standard construct suspend on a pending
     * Future and complete with the exact results after resumption.
     */
    @Test
    void constructAndInitCompleteAcrossTaskSuspension() throws IOException {
        ProtosPrelude prelude = corePrelude();
        ProtosActivation activation = prelude.newModuleActivation();

        ProtosExecutionOutcome outcome =
                ProtosTestExecutionSupport.execute(
                        """
                        trace: { inits: 0; constructs: 0 }
                        Holder: {
                            init: (future) => {
                                trace.inits = trace.inits + 1
                                this.value: future.value()
                            }
                        }
                        Custom: {
                            construct: (future) => {
                                trace.constructs = trace.constructs + 1
                                future.value() + 1
                            }
                        }
                        held: Holder((() => 5).future())
                        [held.value, held.parent() === Holder, Custom((() => 9).future()),
                         trace.inits, trace.constructs]
                        """,
                        activation);

        assertEquals(ProtosExecutionOutcome.State.COMPLETED, outcome.state());
        ProtosArrayValue values = assertInstanceOf(ProtosArrayValue.class, outcome.value());
        assertEquals("5", ProtosNumericValueSupport.integerDecimalText(values.indexedAt(0)));
        assertSame(Boolean.TRUE, values.indexedAt(1));
        assertEquals("10", ProtosNumericValueSupport.integerDecimalText(values.indexedAt(2)));
        assertEquals("1", ProtosNumericValueSupport.integerDecimalText(values.indexedAt(3)));
        assertEquals("1", ProtosNumericValueSupport.integerDecimalText(values.indexedAt(4)));
    }

    /**
     * Deterministic suspension evidence (PERF006-B3E pattern): standard
     * Object.call -> Object.construct -> init, where init waits on a
     * genuinely pending Future. The Task must actually suspend, resume
     * once, and answer the default child without re-running init effects.
     */
    @Test
    void initSuspendsOnPendingFutureAndResumesThroughStandardConstruct()
            throws Exception {
        try (LanguageScope scope = languageScope()) {
            Harness harness = harness();
            ProtosFutureValue future = harness.future();
            Long resolved = Long.valueOf(73);
            harness.activation().context().createLocalSlot("f", future);

            ProtosTask task =
                    execute(
                            harness,
                            lowerRoot(
                                    scope.language(),
                                    """
                                    trace: {
                                        inits: 0
                                        afterWait: 0
                                    }
                                    Holder: {
                                        init: (future) => {
                                            trace.inits = trace.inits + 1
                                            this.value: future.value()
                                            trace.afterWait = trace.afterWait + 1
                                        }
                                    }
                                    Holder(f)
                                    """,
                                    "i145a-init-suspension.protos"));

            assertTrue(harness.domain().dispatchOne());
            assertEquals(ProtosTask.State.SUSPENDED, task.state());
            assertEquals(0, harness.domain().runnableCount());
            assertTraceCount(harness, "inits", "1");
            assertTraceCount(harness, "afterWait", "0");

            assertTrue(future.resolve(resolved, harness.activation()));
            assertEquals(ProtosTask.State.RUNNABLE, task.state());
            assertEquals(1, harness.domain().runnableCount());

            assertTrue(harness.domain().dispatchOne());
            assertEquals(ProtosTask.State.COMPLETED, task.state());
            assertEquals(0, harness.domain().runnableCount());

            ProtosObjectValue holder =
                    assertInstanceOf(
                            ProtosObjectValue.class,
                            harness.activation().context().readLocalSlot("Holder").orElseThrow());
            ProtosObjectValue instance =
                    assertInstanceOf(ProtosObjectValue.class, task.result().orElseThrow());
            assertSame(holder, instance.parent().orElseThrow());
            assertSame(resolved, instance.readLocalSlot("value").orElseThrow());
            assertTraceCount(harness, "inits", "1");
            assertTraceCount(harness, "afterWait", "1");
        }
    }

    /**
     * Deterministic suspension evidence for a custom construct waiting on a
     * genuinely pending Future: its exact result is the call result, it
     * runs once, and no init runs before, during or after resumption.
     */
    @Test
    void customConstructSuspendsOnPendingFutureAndAnswersExactResult()
            throws Exception {
        try (LanguageScope scope = languageScope()) {
            Harness harness = harness();
            ProtosFutureValue future = harness.future();
            Long resolved = Long.valueOf(91);
            harness.activation().context().createLocalSlot("f", future);

            ProtosTask task =
                    execute(
                            harness,
                            lowerRoot(
                                    scope.language(),
                                    """
                                    trace: {
                                        inits: 0
                                        constructs: 0
                                        afterWait: 0
                                    }
                                    Custom: {
                                        init: () => {
                                            trace.inits = trace.inits + 1
                                        }
                                        construct: (future) => {
                                            trace.constructs = trace.constructs + 1
                                            result: future.value()
                                            trace.afterWait = trace.afterWait + 1
                                            result
                                        }
                                    }
                                    Custom(f)
                                    """,
                                    "i145a-construct-suspension.protos"));

            assertTrue(harness.domain().dispatchOne());
            assertEquals(ProtosTask.State.SUSPENDED, task.state());
            assertEquals(0, harness.domain().runnableCount());
            assertTraceCount(harness, "constructs", "1");
            assertTraceCount(harness, "afterWait", "0");

            assertTrue(future.resolve(resolved, harness.activation()));
            assertEquals(ProtosTask.State.RUNNABLE, task.state());
            assertEquals(1, harness.domain().runnableCount());

            assertTrue(harness.domain().dispatchOne());
            assertEquals(ProtosTask.State.COMPLETED, task.state());
            assertEquals(0, harness.domain().runnableCount());

            assertSame(resolved, task.result().orElseThrow());
            assertTraceCount(harness, "constructs", "1");
            assertTraceCount(harness, "afterWait", "1");
            assertTraceCount(harness, "inits", "0");
        }
    }

    @Test
    void initErrorPropagatesThroughStandardConstructUnderTask() throws IOException {
        ProtosPrelude prelude = corePrelude();
        ProtosActivation activation = prelude.newModuleActivation();

        ProtosExecutionOutcome outcome =
                ProtosTestExecutionSupport.execute(
                        """
                        Failing: {
                            init: (future) => {
                                future.value()
                                Error().signal()
                            }
                        }
                        Failing((() => 1).future())
                        """,
                        activation);

        assertEquals(ProtosExecutionOutcome.State.FAILED, outcome.state());
    }

    @Test
    void bracketAndExplicitArrayConstructionProduceEqualElements() throws IOException {
        assertAllTrue("""
                a: [1, 2, 3]
                b: Array(1, 2, 3)
                [a.size() == 3, b.size() == 3, a[0] == b[0], a[1] == b[1], a[2] == b[2],
                    a.parent() === Array, b.parent() === Array]
                """);
    }

    @Test
    void repeatedConstructionIsFreshAndDoesNotShareBacking() throws IOException {
        assertAllTrue("""
                make: () => [1, 2, 3]
                makeEmpty: () => []
                makeExplicit: (n) => Array(n, n)
                x: make()
                y: make()
                x[0] = 9
                e1: makeEmpty()
                e2: makeEmpty()
                p: makeExplicit(4)
                q: makeExplicit(4)
                p[1] = 5
                [x !== y, x[0] == 9, y[0] == 1, make()[0] == 1,
                    e1 !== e2, e1.size() == 0, e2.size() == 0,
                    p !== q, p[1] == 5, q[1] == 4]
                """);
    }

    @Test
    void descendantFactoryRemainsDelegationParent() throws IOException {
        assertAllTrue("""
                Derived: Array {}
                build: (Array) => [1, 2]
                d: build(Derived)
                e: Derived(3, 4)
                [d.parent() === Derived, d[1] == 2, d.size() == 2,
                    e.parent() === Derived, e[0] == 3]
                """);
    }

    @Test
    void localArrayShadowingAffectsBracketsAndExplicitCallsEqually()
            throws IOException {
        Object result = evaluate("""
                f: () => {
                    Array: { construct: (...args) => 7 }
                    a: [1, 2]
                    b: Array(1, 2)
                    a + b
                }
                f()
                """);
        assertEquals("14", ProtosNumericValueSupport.integerDecimalText(result));
    }

    @Test
    void customCallOverrideIsSelected() throws IOException {
        Object result = evaluate("""
                Base: Array
                f: () => {
                    Array: Base { call: (...args) => 5 }
                    [1, 2] + Array(3)
                }
                f()
                """);
        assertEquals("10", ProtosNumericValueSupport.integerDecimalText(result));
    }

    @Test
    void customConstructOverrideIsSelected() throws IOException {
        Object result = evaluate("""
                Base: Array
                f: () => {
                    Array: Base { construct: (...args) => args.size() }
                    [1, 2] + Array(3)
                }
                f()
                """);
        assertEquals("3", ProtosNumericValueSupport.integerDecimalText(result));
    }

    @Test
    void constructReassignmentPreservingShapeIsObserved() throws IOException {
        assertAllTrue("""
                Base: Array
                Factory: Base { construct: (...args) => 1 }
                build: (Array) => [7, 8]
                buildExplicit: (Array, n) => Array(n, 8)
                first: build(Factory)
                firstExplicit: buildExplicit(Factory, 7)
                Factory.construct = (...args) => 2
                second: build(Factory)
                secondExplicit: buildExplicit(Factory, 7)
                standard: build(Base)
                standardExplicit: buildExplicit(Base, 7)
                [first == 1, firstExplicit == 1, second == 2, secondExplicit == 2,
                    standard[1] == 8, standard.parent() === Base,
                    standardExplicit[0] == 7, standardExplicit.parent() === Base]
                """);
    }

    @Test
    void factoryMutationDuringArgumentEvaluationChangesSelection()
            throws IOException {
        assertAllTrue("""
                Base: Array
                Factory: Base {}
                build: (Array, mutate) => [mutate(), 2]
                first: build(Factory, () => 1)
                second: build(Factory, () => {
                    Factory.construct: (...args) => 99
                    1
                })
                [first.size() == 2, first.parent() === Factory, second == 99]
                """);
    }

    @Test
    void nonInvokableShadowedArrayFailsThroughOrdinaryInvocation()
            throws IOException {
        assertAllTrue("""
                literal: (Array) => [1]
                explicit: (Array) => Array(1)
                a: Error.handle(() => { literal(5); false }, (caught) => true)
                b: Error.handle(() => { explicit(5); false }, (caught) => true)
                c: Error.handle(() => { literal({}); false }, (caught) => true)
                [a, b, c]
                """);
    }

    @Test
    void namedArgumentsKeepOrdinaryBindingOutcome() throws IOException {
        ProtosPrelude prelude = corePrelude();
        ProtosExecutionOutcome bracket =
                ProtosTestExecutionSupport.execute(
                        "[a: 10]", prelude.newModuleActivation());
        ProtosExecutionOutcome explicit =
                ProtosTestExecutionSupport.execute(
                        "Array(a: 10)", prelude.newModuleActivation());
        assertEquals(explicit.state(), bracket.state());
    }

    @Test
    void largeSignedAndBigIntegerLiteralsKeepExactValues() throws IOException {
        assertAllTrue("""
                make: () => [9223372036854775807, 1, 9223372036854775806]
                a: make()
                big: [9223372036854775808, 1]
                [a[0] == 9223372036854775807, a[2] == 9223372036854775806,
                    make()[0] == 9223372036854775807,
                    big[0] > 9223372036854775807, big[0] - 1 == 9223372036854775807,
                    big[1] == 1]
                """);
    }

    @Test
    void spreadArgumentsPreserveOrderSnapshotsAndSingleEvaluation()
            throws IOException {
        assertAllTrue("""
                trace: { count: 0 }
                items: () => {
                    trace.count = trace.count + 1
                    [2, 3]
                }
                a: [1, ...items(), 4]
                src: [5, 6]
                b: [...src]
                src[0] = 9
                c: Array(...src, 7)
                [a.size() == 4, a[0] == 1, a[1] == 2, a[2] == 3, a[3] == 4,
                    trace.count == 1, b[0] == 5, b.size() == 2, b !== src,
                    c[0] == 9, c[2] == 7]
                """);
    }

    @Test
    void customArrayConstructorSuspendsAndAnswersExactResult() throws Exception {
        try (LanguageScope scope = languageScope()) {
            Harness harness = harness();
            ProtosFutureValue future = harness.future();
            Long resolved = Long.valueOf(73);
            harness.activation().context().createLocalSlot("f", future);

            ProtosTask task =
                    execute(
                            harness,
                            lowerRoot(
                                    scope.language(),
                                    """
                                    trace: {
                                        constructs: 0
                                        afterWait: 0
                                    }
                                    Base: Array
                                    Custom: Base {
                                        construct: (future) => {
                                            trace.constructs = trace.constructs + 1
                                            result: future.value()
                                            trace.afterWait = trace.afterWait + 1
                                            result
                                        }
                                    }
                                    build: (Array) => {
                                        made: [f]
                                        made
                                    }
                                    build(Custom)
                                    """,
                                    "array-construct-suspension.protos"));

            assertTrue(harness.domain().dispatchOne());
            assertEquals(ProtosTask.State.SUSPENDED, task.state());
            assertTraceCount(harness, "constructs", "1");
            assertTraceCount(harness, "afterWait", "0");

            assertTrue(future.resolve(resolved, harness.activation()));
            assertTrue(harness.domain().dispatchOne());
            assertEquals(ProtosTask.State.COMPLETED, task.state());

            assertSame(resolved, task.result().orElseThrow());
            assertTraceCount(harness, "constructs", "1");
            assertTraceCount(harness, "afterWait", "1");
        }
    }

    @Test
    void customArrayConstructorErrorIsSignaledToGuestHandler() throws IOException {
        assertAllTrue("""
                Base: Array
                Failing: Base { construct: (...args) => Error().signal() }
                build: (Array) => [1, 2]
                caught: Error.handle(() => { build(Failing); false }, (error) => true)
                after: build(Base)
                [caught, after.size() == 2]
                """);
    }

    @Test
    void homogeneousPrimitiveLiteralArraysKeepExactElements() throws IOException {
        assertAllTrue("""
                longs: [1, 2, 3]
                floats: [1.5, 2.5, 0.25]
                flags: [true, false, true]
                [longs.size() == 3, longs[0] == 1, longs[2] == 3,
                    floats.size() == 3, floats[0] == 1.5, floats[2] == 0.25,
                    flags.size() == 3, flags[0] === true, flags[1] === false,
                    longs.parent() === Array, floats.parent() === Array,
                    flags.parent() === Array]
                """);
    }

    @Test
    void explicitPrimitiveArrayConstructionMatchesBrackets() throws IOException {
        assertAllTrue("""
                longs: Array(4, 5)
                floats: Array(4.5, 5.5)
                flags: Array(false, true)
                [longs[0] == 4, longs[1] == 5, longs.parent() === Array,
                    floats[0] == 4.5, floats[1] == 5.5, floats.parent() === Array,
                    flags[0] === false, flags[1] === true, flags.parent() === Array]
                """);
    }

    @Test
    void repeatedPrimitiveConstructionIsFreshWithIndependentBacking()
            throws IOException {
        assertAllTrue("""
                makeFloats: () => [1.5, 2.5]
                makeFlags: () => Array(true, false)
                f1: makeFloats()
                f2: makeFloats()
                f1[0] = 9.5
                b1: makeFlags()
                b2: makeFlags()
                b1[1] = true
                [f1 !== f2, f1[0] == 9.5, f2[0] == 1.5, makeFloats()[0] == 1.5,
                    b1 !== b2, b1[1] === true, b2[1] === false,
                    makeFlags()[1] === false]
                """);
    }

    @Test
    void mixedPrimitiveFamiliesKeepExactGuestValues() throws IOException {
        assertAllTrue("""
                numbers: [1, 2.5, 3]
                flags: [true, 1, false]
                big: [9223372036854775808, 1.5]
                numbers[0] = 4
                [numbers[0] == 4, numbers[1] == 2.5, numbers[2] == 3,
                    flags[0] === true, flags[1] == 1, flags[2] === false,
                    big[0] - 1 == 9223372036854775807, big[1] == 1.5]
                """);
    }

    @Test
    void customFactoriesAreSelectedForPrimitiveConstantLists() throws IOException {
        Object result = evaluate("""
                Base: Array
                shadowed: () => {
                    Array: { construct: (...args) => 7 }
                    [1.5, 2.5] + Array(true)
                }
                customCall: () => {
                    Array: Base { call: (...args) => 5 }
                    [true, false] + Array(0.5)
                }
                customConstruct: () => {
                    Array: Base { construct: (...args) => args.size() }
                    [1.5, 2.5, 3.5] + Array(false)
                }
                Derived: Array {}
                build: (Array) => [0.5, 1.5]
                derived: build(Derived)
                shadowed() + customCall() + customConstruct() + derived.size() + (derived.parent() === Derived).ifTrueIfFalse(() => { 0 }, () => { 1000 })
                """);
        assertEquals("30", ProtosNumericValueSupport.integerDecimalText(result));
    }

    @Test
    void primitiveConstantArgumentsAreEvaluatedOnceInOrderForCustomFactories()
            throws IOException {
        assertAllTrue("""
                log: {
                    count: 0
                    first: null
                }
                Base: Array
                Recording: Base {
                    construct: (...args) => {
                        log.count = log.count + 1
                        log.first = args[0]
                        args
                    }
                }
                build: (Array) => [true, false]
                buildFloats: (Array) => Array(1.5, 2.5)
                flags: build(Recording)
                floats: buildFloats(Recording)
                [log.count == 2, log.first == 1.5, flags[0] === true,
                    flags[1] === false, floats[1] == 2.5]
                """);
    }

    @Test
    void customPrimitiveConstructionErrorPrecedesLaterWork() throws IOException {
        assertAllTrue("""
                log: { after: false }
                Base: Array
                Failing: Base { construct: (...args) => Error().signal() }
                build: (Array) => {
                    values: [1.5, 2.5]
                    log.after = true
                    values
                }
                caught: Error.handle(() => { build(Failing); false }, (error) => true)
                skipped: log.after === false
                standard: build(Base)
                [caught, skipped, log.after === true, standard[1] == 2.5]
                """);
    }

    private static Object evaluate(String source) throws IOException {
        return ProtosTestExecutionSupport.evaluate(
                source, corePrelude().newModuleActivation());
    }

    private static void assertAllTrue(String source) throws IOException {
        ProtosArrayValue values =
                assertInstanceOf(ProtosArrayValue.class, evaluate(source));
        for (int index = 0; index < values.indexedSize(); index++) {
            assertSame(Boolean.TRUE, values.indexedAt(index), "element " + index);
        }
    }

    private static void assertTraceCount(Harness harness, String name, String expected) {
        ProtosObjectValue trace =
                assertInstanceOf(
                        ProtosObjectValue.class,
                        harness.activation().context().readLocalSlot("trace").orElseThrow());
        assertEquals(
                expected,
                ProtosNumericValueSupport.integerDecimalText(
                        trace.readLocalSlot(name).orElseThrow()),
                name);
    }

    private static LanguageScope languageScope() {
        Context context = Context.newBuilder(ProtosLanguage.ID).build();
        context.initialize(ProtosLanguage.ID);
        context.enter();
        return new LanguageScope(context, LANGUAGE_REF.get(null));
    }

    private static Harness harness() throws IOException {
        ProtosPrelude prelude = corePrelude();
        ProtosActorExecutionDomain domain = new ProtosActorExecutionDomain();
        return new Harness(
                prelude,
                domain,
                prelude.newModuleActivation(
                        new ProtosActorModuleState(),
                        null,
                        prelude.newExecutionContext(),
                        domain));
    }

    private static ProtosTask execute(Harness harness, ProtosBytecodeRootNode root) {
        return harness.domain().createTask(
                null,
                current ->
                        ProtosBytecodeTaskExecution.execute(
                                current,
                                root.getCallTarget(),
                                harness.activation()));
    }

    private static ProtosBytecodeRootNode lowerRoot(
            ProtosLanguage language,
            String characters,
            String sourceName) {
        Source source =
                Source.newBuilder(ProtosLanguage.ID, characters, sourceName).build();
        return new CanonicalToBytecodeLowerer(language, source)
                .lowerRoot(
                        (CanonicalSequence)
                                new Canonicalizer()
                                        .canonicalize(
                                                new ProtosParser(characters).parseProgram()));
    }

    private record LanguageScope(Context context, ProtosLanguage language)
            implements AutoCloseable {
        @Override
        public void close() {
            context.leave();
            context.close();
        }
    }

    private record Harness(
            ProtosPrelude prelude,
            ProtosActorExecutionDomain domain,
            ProtosActivation activation) {
        ProtosFutureValue future() {
            return new ProtosFutureValue(prelude.futurePrototype(), domain);
        }
    }

    private static ProtosPrelude corePrelude() throws IOException {
        return new ProtosCoreBootstrap()
                .bootstrap(Path.of("protos", "lib", "core"));
    }
}
