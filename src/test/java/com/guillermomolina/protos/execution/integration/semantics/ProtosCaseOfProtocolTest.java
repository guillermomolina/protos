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

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ProtosCaseOfProtocolTest {
    private static final Path CORE = Path.of("protos", "lib", "core");

    private static ProtosPrelude core() throws Exception {
        return new ProtosCoreBootstrap().bootstrap(CORE);
    }

    private static Object evaluate(ProtosPrelude prelude, String source) {
        return ProtosTestExecutionSupport.evaluate(
                source,
                prelude.newModuleActivation());
    }

    @Test
    void selectsFirstSuccessfulMatcherAndSpreadsCapture() throws Exception {
        ProtosPrelude prelude = core();

        Object result =
                evaluate(
                        prelude,
                        """
                        42.caseOf(%{
                            41: () => false
                            Capture: value => value == 42
                        })
                        """);

        assertSame(Boolean.TRUE, result);
    }

    @Test
    void preservesSelectedCallableNullResult() throws Exception {
        ProtosPrelude prelude = core();

        Object result =
                evaluate(
                        prelude,
                        """
                        1.caseOf(%{
                            1: () => null
                        })
                        """);

        assertSame(ProtosNullValue.INSTANCE, result);
    }

    @Test
    void doesNotEagerlyValidateUnreachedCallable() throws Exception {
        ProtosPrelude prelude = core();

        Object result =
                evaluate(
                        prelude,
                        """
                        1.caseOf(%{
                            1: () => true
                            2: 99
                        })
                        """);

        assertSame(Boolean.TRUE, result);
    }

    @Test
    void snapshotsCaseAssociationsBeforeRunningMatchers() throws Exception {
        ProtosPrelude prelude = core();

        Object result =
                evaluate(
                        prelude,
                        """
                        Second: {
                            match: (subject) => true
                        }

                        holder: Map()

                        First: {
                            match: (subject) => {
                                observedCases: holder["cases"]
                                observedCases[Second] = () => false
                                false
                            }
                        }

                        cases: %{
                            First: () => false
                            Second: () => true
                        }

                        holder["cases"] = cases
                        1.caseOf(cases)
                        """);

        assertSame(Boolean.TRUE, result);
    }

    @Test
    void attemptsCasesInMapInsertionOrder() throws Exception {
        ProtosPrelude prelude = core();

        Object result =
                evaluate(
                        prelude,
                        """
                        state: {
                            count: 0
                        }

                        First: {
                            match: (subject) => {
                                state.count = state.count + 1
                                false
                            }
                        }

                        Second: {
                            match: (subject) => state.count == 1
                        }

                        0.caseOf(%{
                            First: () => false
                            Second: () => true
                        })
                        """);

        assertSame(Boolean.TRUE, result);
    }

    @Test
    void normalMapKeyUniquenessDefinesCaseUniqueness() throws Exception {
        ProtosPrelude prelude = core();

        Object result =
                evaluate(
                        prelude,
                        """
                        cases: Map()
                        cases[1] = () => false
                        cases[1] = () => true

                        1.caseOf(cases)
                        """);

        assertSame(Boolean.TRUE, result);
    }

    @Test
    void doesNotEagerlyValidateUnreachedMatcher() throws Exception {
        ProtosPrelude prelude = core();

        Object result =
                evaluate(
                        prelude,
                        """
                        Bad: {
                            match: 99
                        }

                        1.caseOf(%{
                            1: () => true
                            Bad: () => false
                        })
                        """);

        assertSame(Boolean.TRUE, result);
    }

    @Test
    void rejectsInvalidReachedMatcherOutcome() throws Exception {
        ProtosPrelude prelude = core();

        assertThrows(
                ProtosSignalException.class,
                () ->
                        evaluate(
                                prelude,
                                """
                                Bad: {
                                    match: (subject) => null
                                }

                                1.caseOf(%{
                                    Bad: () => true
                                })
                                """));
    }

    @Test
    void rejectsEmptyCaptureCarrierAsMatcherOutcome() throws Exception {
        ProtosPrelude prelude = core();

        assertThrows(
                ProtosSignalException.class,
                () ->
                        evaluate(
                                prelude,
                                """
                                Bad: {
                                    match: (subject) => []
                                }

                                1.caseOf(%{
                                    Bad: () => true
                                })
                                """));
    }

    @Test
    void rejectsSelectedNonCallable() throws Exception {
        ProtosPrelude prelude = core();

        assertThrows(
                ProtosSignalException.class,
                () ->
                        evaluate(
                                prelude,
                                """
                                1.caseOf(%{
                                    1: 99
                                })
                                """));
    }

    @Test
    void rejectsNonMapCarrierAndNoSuccessfulCase() throws Exception {
        ProtosPrelude prelude = core();

        assertThrows(
                ProtosSignalException.class,
                () -> evaluate(prelude, "1.caseOf([])"));

        assertThrows(
                ProtosSignalException.class,
                () ->
                        evaluate(
                                prelude,
                                """
                                1.caseOf(%{
                                    2: () => true
                                })
                                """));
    }

    // I176 / D263-C: the contextual `match(subject) { ... }` spelling is the
    // ordinary caseOf send; each observation below is paired with its
    // explicit expansion.

    @Test
    void i176ContextualMatchHonorsLexicalMapShadowingLikeExplicitSource()
            throws Exception {
        ProtosPrelude prelude = core();

        Object result =
                evaluate(
                        prelude,
                        """
                        log: ""
                        key: (value) => {
                            log = log + "k"
                            value
                        }
                        outcome: (body) => {
                            failed: false
                            Error.handle(body, (error) => {
                                failed = true
                                error
                            })
                            failed
                        }
                        shadowed: () => {
                            Map: Object {
                                call: () => Object {}
                            }
                            [
                                outcome(() => match(1) { key(1): 2 }),
                                outcome(() => 1.caseOf(%{ key(1): () => 2 }))
                            ]
                        }
                        results: shadowed()
                        results[0] && results[1] && (log == "")
                        """);

        assertSame(Boolean.TRUE, result);
    }

    @Test
    void i176ContextualMatchKeepsOrdinaryHashAndMatcherEffectOrder()
            throws Exception {
        ProtosPrelude prelude = core();

        Object result =
                evaluate(
                        prelude,
                        """
                        log: ""
                        key: (name) => {
                            {
                                hash: () => {
                                    log = log + name
                                    7
                                }
                                match: (subject) => {
                                    log = log + "m" + name
                                    false
                                }
                            }
                        }
                        Last: {
                            match: (subject) => true
                        }
                        contextual: match(1) {
                            key("a"): 1
                            key("b"): 2
                            Last: 3
                        }
                        contextualLog: log
                        log = ""
                        explicit: 1.caseOf(%{
                            key("a"): () => 1
                            key("b"): () => 2
                            Last: () => 3
                        })
                        (contextual == explicit) &&
                            (contextualLog == log) &&
                            (log.size() > 0)
                        """);

        assertSame(Boolean.TRUE, result);
    }

    @Test
    void i176DuplicateKeyFailsBeforeCaseOfInvocation() throws Exception {
        ProtosPrelude prelude = core();
        String fixture =
                """
                log: ""
                subject: {
                    caseOf: (cases) => {
                        log = log + "c"
                        cases
                    }
                }
                signaled: (body) => {
                    failed: false
                    Error.handle(body, (error) => {
                        failed = true
                        error
                    })
                    failed
                }
                """;

        Object contextual =
                evaluate(
                        prelude,
                        fixture
                                + """
                                signaled(() => match(subject) { 1: 2; 1: 3 }) && (log == "")
                                """);
        Object explicit =
                evaluate(
                        prelude,
                        fixture
                                + """
                                signaled(() => subject.caseOf(%{ 1: () => 2; 1: () => 3 })) &&
                                    (log == "")
                                """);

        assertSame(Boolean.TRUE, contextual);
        assertSame(Boolean.TRUE, explicit);
        assertThrows(
                ProtosSignalException.class,
                () -> evaluate(prelude, "match(1) { 1: 2; 1: 3 }"));
        assertThrows(
                ProtosSignalException.class,
                () -> evaluate(prelude, "1.caseOf(%{ 1: () => 2; 1: () => 3 })"));
    }

    @Test
    void i176DuplicateKeyFailureRunsNoAction() throws Exception {
        ProtosPrelude prelude = core();

        Object result =
                evaluate(
                        prelude,
                        """
                        log: ""
                        signaled: (body) => {
                            failed: false
                            Error.handle(body, (error) => {
                                failed = true
                                error
                            })
                            failed
                        }
                        contextual: signaled(() => match(1) {
                            1: log = log + "a"
                            1: log = log + "b"
                        })
                        contextualLog: log
                        explicit: signaled(() => 1.caseOf(%{
                            1: () => log = log + "a"
                            1: () => log = log + "b"
                        }))
                        contextual && explicit && (contextualLog == "") && (log == "")
                        """);

        assertSame(Boolean.TRUE, result);
    }

    @Test
    void i176MutatedCasesMapAffectsLaterOrdinarySelection() throws Exception {
        ProtosPrelude prelude = core();

        Object result =
                evaluate(
                        prelude,
                        """
                        saved: null
                        spy: {
                            caseOf: (cases) => {
                                saved = cases
                                cases
                            }
                        }
                        match(spy) {
                            1: "one"
                        }
                        saved[2] = () => "two"
                        (2.caseOf(saved) == "two") && (1.caseOf(saved) == "one")
                        """);

        assertSame(Boolean.TRUE, result);
    }

    @Test
    void i176CustomMatcherCapturesReachExplicitActions() throws Exception {
        ProtosPrelude prelude = core();

        Object result =
                evaluate(
                        prelude,
                        """
                        Halves: {
                            match: (subject) => [subject / 2, subject / 2]
                        }
                        contextual: match(10) {
                            Halves: (a, b) => a + b
                        }
                        explicit: 10.caseOf(%{
                            Halves: (a, b) => a + b
                        })
                        ignored: match(10) {
                            Halves: "ignored"
                        }
                        (contextual == 10) && (explicit == 10) && (ignored == "ignored")
                        """);

        assertSame(Boolean.TRUE, result);
    }

    @Test
    void i176SelectionObservationIsShallow() throws Exception {
        ProtosPrelude prelude = core();

        Object result =
                evaluate(
                        prelude,
                        """
                        label: "before"
                        Mutating: {
                            match: (subject) => {
                                label = "after"
                                false
                            }
                        }
                        contextual: match(1) {
                            Mutating: "unreached"
                            Any: label
                        }
                        label = "before"
                        explicit: 1.caseOf(%{
                            Mutating: () => "unreached"
                            Any: () => label
                        })
                        (contextual == "after") && (explicit == "after")
                        """);

        assertSame(Boolean.TRUE, result);
    }

    @Test
    void i176SelectedClosureAndFutureResultsKeepIdentity() throws Exception {
        ProtosPrelude prelude = core();

        Object result =
                evaluate(
                        prelude,
                        """
                        handler: () => 1
                        future: Future.all()
                        (match(1) { 1: handler } === handler) &&
                            (1.caseOf(%{ 1: () => handler }) === handler) &&
                            (match(1) { 1: future } === future) &&
                            (1.caseOf(%{ 1: () => future }) === future)
                        """);

        assertSame(Boolean.TRUE, result);
    }

    // PERF044: caseOf sends lowered in the enclosing root keep every
    // observable effect of the ordinary standard caseOf.

    private static void assertTrue(ProtosPrelude prelude, String source) {
        assertSame(Boolean.TRUE, evaluate(prelude, source));
    }

    @Test
    void perf044ContextualAndExplicitSelectFirstMiddleLastAndAny() throws Exception {
        assertTrue(core(), """
                contextual: (value) => match(value) { 0: 11; 1: 23; 2: 37; Any: 53 }
                explicit: (value) => value.caseOf(%{
                    0: () => 11
                    1: () => 23
                    2: () => 37
                    Any: () => 53
                })
                (contextual(0) == 11) && (explicit(0) == 11) &&
                    (contextual(1) == 23) && (explicit(1) == 23) &&
                    (contextual(2) == 37) && (explicit(2) == 37) &&
                    (contextual(9) == 53) && (explicit(9) == 53)
                """);
    }

    @Test
    void perf044DynamicTableSelectsLikeLiteralTable() throws Exception {
        assertTrue(core(), """
                cases: %{
                    0: () => 11
                    [Capture, 2]: (first) => first + 100
                    Any: () => 53
                }
                (0.caseOf(cases) == 11) && ([7, 2].caseOf(cases) == 107) &&
                    ("x".caseOf(cases) == 53)
                """);
    }

    @Test
    void perf044SubjectIsEvaluatedExactlyOnce() throws Exception {
        assertTrue(core(), """
                state: { count: 0 }
                subject: () => {
                    state.count = state.count + 1
                    2
                }
                selected: subject().caseOf(%{ 0: () => 0; 1: () => 1; 2: () => 2 })
                contextual: match(subject()) { 0: 0; 2: 2; Any: 9 }
                (selected == 2) && (contextual == 2) && (state.count == 2)
                """);
    }

    @Test
    void perf044KeysAreEagerAndActionsLazy() throws Exception {
        assertTrue(core(), """
                state: { keys: 0; order: 0; ran: 0 }
                key: (value) => {
                    state.keys = state.keys + 1
                    state.order = state.order * 10 + value + 1
                    value
                }
                result: 1.caseOf(%{
                    key(0): () => { state.ran = state.ran + 10 }
                    key(1): () => { state.ran = state.ran + 1 }
                    key(2): () => { state.ran = state.ran + 100 }
                })
                (state.keys == 3) && (state.order == 123) && (state.ran == 1) && (result == 1)
                """);
    }

    @Test
    void perf044DuplicateKeysSignalBeforeSelection() throws Exception {
        assertTrue(core(), """
                state: { selected: false }
                caught: Error.handle(
                    () => 1.caseOf(%{ 1: () => { state.selected = true }; 1: () => 2 }),
                    (error) => "duplicate")
                (caught == "duplicate") && (state.selected == false)
                """);
    }

    @Test
    void perf044CustomCaseOfReceivesTheActualMap() throws Exception {
        assertTrue(core(), """
                state: { seen: null }
                subject: { caseOf: (cases) => {
                    state.seen = cases
                    "custom"
                } }
                table: %{ 1: () => "standard" }
                (subject.caseOf(table) == "custom") && (state.seen === table) &&
                    (match(subject) { Any: "standard" } == "custom")
                """);
    }

    @Test
    void perf044CustomMatchersMixWithStandardMatchersInOrder() throws Exception {
        assertTrue(core(), """
                state: { order: 0 }
                Even: { match: (subject) => {
                    state.order = state.order * 10 + 1
                    (subject % 2) == 0
                } }
                Pair: { match: (subject) => {
                    state.order = state.order * 10 + 2
                    [subject, subject]
                } }
                result: 3.caseOf(%{
                    1: () => "one"
                    Even: () => "even"
                    Pair: (a, b) => a + b
                    Any: () => "unreached"
                })
                (result == 6) && (state.order == 12)
                """);
    }

    @Test
    void perf044CustomEqualityRunsInPatternDirection() throws Exception {
        assertTrue(core(), """
                state: { last: null }
                LooseBase: {
                    hash: () => 0
                    equalTo: (other) => {
                        state.last = other
                        true
                    }
                }
                Loose: LooseBase.alias("equalTo", "==")
                result: 5.caseOf(%{ Loose: () => "pattern" })
                (result == "pattern") && (state.last == 5)
                """);
    }

    @Test
    void perf044EarlierMatcherChangesLaterMatcher() throws Exception {
        assertTrue(core(), """
                Late: { match: (subject) => false }
                First: { match: (subject) => {
                    Late.match = (other) => true
                    false
                } }
                1.caseOf(%{ First: () => "first"; Late: () => "late" }) == "late"
                """);
    }

    @Test
    void perf044InvalidMatcherResultsSignal() throws Exception {
        assertTrue(core(), """
                Bad: { match: (subject) => 1 }
                Empty: { match: (subject) => [] }
                badCaught: Error.handle(() => 1.caseOf(%{ Bad: () => 0 }), (error) => "bad")
                emptyCaught: Error.handle(() => 1.caseOf(%{ Empty: () => 0 }), (error) => "empty")
                (badCaught == "bad") && (emptyCaught == "empty")
                """);
    }

    @Test
    void perf044TableMutationDuringMatchingUsesTheObservedSnapshot() throws Exception {
        assertTrue(core(), """
                cases: Map()
                Mutating: { match: (subject) => {
                    cases[1] = () => "added"
                    false
                } }
                cases[Mutating] = () => "mutating"
                caught: Error.handle(() => 1.caseOf(cases), (error) => "none")
                (caught == "none") && (1.caseOf(cases) == "added")
                """);
    }

    @Test
    void perf044SelectedFalseAndNullResultsAreNotMatcherOutcomes() throws Exception {
        assertTrue(core(), """
                (match(1) { 1: false; Any: true } == false) &&
                    (match(1) { 1: null; Any: true } == null)
                """);
    }

    @Test
    void perf044ErrorsAndNonLocalReturnPropagateFromMatchersAndActions() throws Exception {
        assertTrue(core(), """
                Failing: { match: (subject) => Error.signal() }
                caught: Error.handle(() => 1.caseOf(%{ Failing: () => 0 }), (error) => "caught")
                early: () => {
                    1.caseOf(%{ 1: () => { return "returned" } })
                    "fell through"
                }
                (caught == "caught") && (early() == "returned")
                """);
    }

    @Test
    void perf044CachedSelectionObservesALaterCaseOfOverride() throws Exception {
        assertTrue(core(), """
                Base: {}
                subject: Base()
                select: (value) => match(value) { Any: "standard" }
                first: select(subject)
                Base.createSlot("caseOf", (cases) => "override")
                second: select(subject)
                (first == "standard") && (second == "override")
                """);
    }

    @Test
    void perf044CopiedStandardCaseOfKeepsItsOrdinarySemantics() throws Exception {
        assertTrue(core(), """
                holder: { caseOf: Object.caseOf }
                holder.caseOf(%{ Any: () => "copied" }) == "copied"
                """);
    }

    @Test
    void perf044EveryPrimitiveCarrierSubjectSelectsStandardCaseOf() throws Exception {
        assertTrue(core(), """
                select: (value) => match(value) {
                    1: "integer"
                    2.5: "float"
                    false: "boolean"
                    Any: "other"
                }
                (select(1) == "integer") && (select(2.5) == "float") &&
                    (select(false) == "boolean") && (select("s") == "other") &&
                    (select(1.0) == "integer")
                """);
    }

    @Test
    void perf044ActionsReceiveZeroOneAndManyCapturesInOrder() throws Exception {
        assertTrue(core(), """
                Triple: { match: (subject) => [subject, subject + 1, subject + 2] }
                none: 1.caseOf(%{ 1: () => "none" })
                one: 1.caseOf(%{ Capture: (value) => value * 10 })
                many: 1.caseOf(%{ Triple: (a, b, c) => (a * 100) + (b * 10) + c })
                (none == "none") && (one == 10) && (many == 123)
                """);
    }

    @Test
    void perf044ActionsMutateCapturedVariables() throws Exception {
        assertTrue(core(), """
                run: () => {
                    total: 0
                    for(value in [0, 1, 2, 3]) {
                        match(value) {
                            0: () => { total = total + 1 }
                            1: () => { total = total + 10 }
                            Any: () => { total = total + 100 }
                        }
                    }
                    total
                }
                run() == 211
                """);
    }

    /*
     * Selected actions: a written action that reads only its own parameters
     * and bindings runs inline in the enclosing root, every other action is
     * invoked. Both paths keep the ordinary caseOf semantics.
     */
    @Test
    void perf044FirstSuccessRunsOnlyItsOwnAction() throws Exception {
        assertTrue(core(), """
                state: { matched: 0; ran: 0 }
                Counting: { match: (subject) => {
                    state.matched = state.matched + 1
                    false
                } }
                r: 2.caseOf(%{
                    Counting: () => 0
                    2: () => 20
                    2.5: () => { state.ran = state.ran + 1 }
                    Any: () => 99
                })
                (r == 20) && (state.matched == 1) && (state.ran == 0)
                """);
    }

    @Test
    void perf044UnreachedActionsAreNotValidatedAsCallable() throws Exception {
        assertTrue(core(), """
                (1.caseOf(%{ 1: () => "ok"; 2: 5 }) == "ok") &&
                    ([1].caseOf(%{ [Capture]: (n) => n + 1; Any: "not callable" }) == 2)
                """);
    }

    @Test
    void perf044DynamicAndCustomCallableActionsRemainAuthoritative() throws Exception {
        assertTrue(core(), """
                dynamic: (a, b) => a - b
                Callable: { call: (a, b) => a * b }
                changed: (n) => n
                changed.call: (n) => n + 100
                ([9, 4].caseOf(%{ [Capture, Capture]: dynamic }) == 5) &&
                    ([6, 7].caseOf(%{ [Capture, Capture]: Callable }) == 42) &&
                    (1.caseOf(%{ Capture: changed }) == 101)
                """);
    }

    @Test
    void perf044CaseOfRedefinitionBypassesInlineActions() throws Exception {
        assertTrue(core(), """
                Base: {}
                subject: Base()
                select: (value) => value.caseOf(%{ Capture: (x) => "standard" })
                first: select(subject)
                Base.createSlot("caseOf", (cases) => "override")
                second: select(subject)
                (first == "standard") && (second == "override")
                """);
    }

    @Test
    void perf044ActionsReceiveZeroToFiveCaptures() throws Exception {
        assertTrue(core(), """
                zero: 1.caseOf(%{ 1: () => "zero" })
                one: [1].caseOf(%{ [Capture]: (a) => a })
                two: [1, 2].caseOf(%{ [Capture, Capture]: (a, b) => (a * 10) + b })
                three: [1, 2, 3].caseOf(%{ [Capture, Capture, Capture]: (a, b, c) => (a * 100) + (b * 10) + c })
                four: [1, 2, 3, 4].caseOf(%{ [Capture, Capture, Capture, Capture]: (a, b, c, d) => (a * 1000) + (b * 100) + (c * 10) + d })
                five: [1, 2, 3, 4, 5].caseOf(%{ [Capture, Capture, Capture, Capture, Capture]: (a, b, c, d, e) => a + b + c + d + e })
                (zero == "zero") && (one == 1) && (two == 12) && (three == 123) &&
                    (four == 1234) && (five == 15)
                """);
    }

    @Test
    void perf044CapturesKeepTheirGuestIdentities() throws Exception {
        assertTrue(core(), """
                object: {}
                array: [1]
                single: [object].caseOf(%{ [Capture]: (o) => o })
                pair: [object, array].caseOf(%{ [Capture, Capture]: (o, a) => [o, a] })
                (single === object) && (pair[0] === object) && (pair[1] === array)
                """);
    }

    @Test
    void perf044CaptureArrayMutationAfterConsumptionKeepsThePacket() throws Exception {
        assertTrue(core(), """
                box: { held: null }
                held: [box, 2]
                box.held = held
                Holding: { match: (subject) => held }
                r: 0.caseOf(%{ Holding: (b, n) => {
                    b.held[1] = 99
                    n
                } })
                (r == 2) && (held[1] == 99)
                """);
    }

    @Test
    void perf044MissingExtraDefaultAndRestArgumentsKeepOrdinarySemantics() throws Exception {
        assertTrue(core(), """
                outcome: (thunk) => Error.handle(thunk, (error) => "error")
                extra: (a) => a
                missing: (a, b) => b
                defaulted: (a, b: 5) => a + b
                rest: (first, ...others) => others.size()
                (outcome(() => extra(1, 2)) ==
                        outcome(() => [1, 2].caseOf(%{ [Capture, Capture]: (a) => a }))) &&
                    (outcome(() => missing(1)) ==
                        outcome(() => [1].caseOf(%{ [Capture]: (a, b) => b }))) &&
                    ([1].caseOf(%{ [Capture]: (a, b: 5) => a + b }) == defaulted(1)) &&
                    ([1, 2, 3].caseOf(%{ [Capture, Capture, Capture]: (first, ...others) => others.size() }) ==
                        rest(1, 2, 3))
                """);
    }

    @Test
    void perf044ActionResultsArePreservedExactly() throws Exception {
        assertTrue(core(), """
                object: {}
                array: [1, 2]
                (1.caseOf(%{ 1: () => false; Any: () => true }) == false) &&
                    (1.caseOf(%{ 1: () => null; Any: () => true }) == null) &&
                    (1.caseOf(%{ 1: () => 7 }) == 7) &&
                    (1.caseOf(%{ 1: () => 2.5 }) == 2.5) &&
                    ([object].caseOf(%{ [Capture]: (o) => o }) === object) &&
                    ([array].caseOf(%{ [Capture]: (a) => a }) === array) &&
                    ((1.caseOf(%{ 1: () => () => 5 }))() == 5) &&
                    ([3].caseOf(%{ [Capture]: (n) => [n, n] }).size() == 2)
                """);
    }

    @Test
    void perf044InlineActionBindingsAreFreshPerExecution() throws Exception {
        assertTrue(core(), """
                select: (value) => value.caseOf(%{ Capture: (n) => {
                    local: n * 10
                    local = local + 1
                    local
                } })
                run: () => {
                    sum: 0
                    for(v in [1, 2, 3]) {
                        sum = sum + select(v)
                    }
                    sum
                }
                (select(1) == 11) && (select(5) == 51) && (run() == 63)
                """);
    }

    @Test
    void perf044ActionObservingItsContextKeepsItsActivation() throws Exception {
        assertTrue(core(), """
                run: () => {
                    own: context
                    observed: 1.caseOf(%{ 1: () => {
                        mine: 4
                        context
                    } })
                    (observed !== own) && observed.hasSlot("mine")
                }
                run()
                """);
    }

    @Test
    void perf044CapturedVariablesErrorsAndReturnsKeepTheirAuthority() throws Exception {
        assertTrue(core(), """
                counter: () => {
                    total: 0
                    for(v in [1, 2]) {
                        v.caseOf(%{ Capture: (n) => { total = total + n } })
                    }
                    total
                }
                early: () => {
                    [1].caseOf(%{ [Capture]: (n) => { return n + 41 } })
                    0
                }
                caught: Error.handle(
                    () => [1].caseOf(%{ [Capture]: (n) => n.missingSelector() }),
                    (error) => "caught")
                (counter() == 3) && (early() == 42) && (caught == "caught")
                """);
    }
}
