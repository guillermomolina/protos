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
}
