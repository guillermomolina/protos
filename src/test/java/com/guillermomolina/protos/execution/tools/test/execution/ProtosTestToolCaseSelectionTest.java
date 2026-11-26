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
import static org.junit.jupiter.api.Assertions.assertSame;

import com.guillermomolina.protos.runtime.ProtosArrayValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosStringValue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

/** TOOL009-G2 / D185: CaseRef V1 identity, exact selection and listing projection. */
final class ProtosTestToolCaseSelectionTest {
    private static final Path CORE = Path.of("protos", "lib", "core");
    private static final Path STANDARD_LIBRARY = Path.of("protos", "lib");
    private static final Path TOOL_ROOT = Path.of("protos", "tools", "test");
    private static final Path SHARED_ROOT = Path.of("protos", "tools", "shared");

    /*
     * Three-Case suite-native source whose bodies fail and count if ever invoked. Discovery,
     * ref derivation, selection and listing must never call them.
     */
    private static final String SUITE =
            """
            Discovery: import("./Discovery.protos")
            CaseRef: import("./CaseRef.protos")
            Selection: import("./CaseSelection.protos")
            TestValue: import("std:test/test")
            JSON: import("std:json/json")
            Accumulator: import("./ArrayAccumulator.protos")

            captureListing: (projections) => {
                chunks: Accumulator.create()
                writer: JSON.eventWriter((chunk) => {
                    Accumulator.append(chunks, chunk)
                })
                Selection.emitListing(projections, (event) => {
                    writer.feed(event)
                })
                writer.finish()
                "".concat(...Accumulator.finish(chunks))
            }

            calls: {
                value: 0
            }

            body: () => {
                calls.value = calls.value + 1
                Error().signal()
            }

            suite: {
                tests: Array(
                    TestValue("first", body),
                    TestValue("second", body),
                    TestValue("third", body)
                )
            }

            projection:
                Discovery.projectionFromModule(
                    Array("corpus", "suite.protos"),
                    suite
                )
            cases: Discovery.projectionPlan(projection)
            """;

    @Test
    void v1SpellingIsFrozen() throws Exception {
        assertEquals(
                "v1.5b5b22636f72707573222c2273756974652e70726f746f73225d2c226669727374225d",
                evaluateString(
                        """
                        CaseRef: import("./CaseRef.protos")
                        CaseRef.derive(Array("corpus", "suite.protos"), "first")
                        """));

        assertEquals(
                "v1.5b5b22636f72707573222c22c3b1616e64c3ba2fc3bc2e70726f746f73225d2c22"
                        + "70727565626120e29c93225d",
                evaluateString(
                        """
                        CaseRef: import("./CaseRef.protos")
                        CaseRef.derive(Array("corpus", "ñandú/ü.protos"), "prueba ✓")
                        """));

        assertEquals(
                ref("corpus", "ñandú/ü.protos", "prueba ✓"),
                evaluateString(
                        """
                        CaseRef: import("./CaseRef.protos")
                        CaseRef.derive(Array("corpus", "ñandú/ü.protos"), "prueba ✓")
                        """));
    }

    @Test
    void identityIsSourceIdentityPlusSelectorOnly() throws Exception {
        assertTrue(
                """
                CaseRef: import("./CaseRef.protos")
                LogicalCasePlan: import("./LogicalCasePlan.protos")

                source: Array("corpus", "suite.protos")
                other: Array("corpus", "other.protos")

                sameSame: CaseRef.derive(source, "first") == CaseRef.derive(source, "first")
                sameOtherSelector: CaseRef.derive(source, "first") == CaseRef.derive(source, "second")
                otherSameSelector: CaseRef.derive(source, "first") == CaseRef.derive(other, "first")

                // Declaration position and declaration signature are not inputs.
                wide: LogicalCasePlan.build(source, Array("a", "b", "c"))
                narrow: LogicalCasePlan.build(source, Array("b"))
                positionFree: CaseRef.ref(wide[1]) == CaseRef.ref(narrow[0])

                sameSame &&
                    sameOtherSelector.not() &&
                    otherSameSelector.not() &&
                    positionFree
                """);
    }

    @Test
    void authorityDistinguishingAssociationDataChangesRefButNotDisplay()
            throws Exception {
        assertTrue(
                """
                CaseRef: import("./CaseRef.protos")
                LogicalCasePlan: import("./LogicalCasePlan.protos")

                plain: Array("corpus", "fixture.protos")
                projectA: Array(
                    "corpus",
                    "fixture.protos",
                    Array("project-tree", "project-a", "case", "case")
                )
                projectB: Array(
                    "corpus",
                    "fixture.protos",
                    Array("project-tree", "project-b", "case", "case")
                )

                plainEntry: LogicalCasePlan.build(plain, Array("t"))[0]
                aEntry: LogicalCasePlan.build(projectA, Array("t"))[0]
                bEntry: LogicalCasePlan.build(projectB, Array("t"))[0]

                (CaseRef.ref(plainEntry) == CaseRef.ref(aEntry)).not() &&
                    (CaseRef.ref(aEntry) == CaseRef.ref(bEntry)).not() &&
                    (CaseRef.ref(plainEntry) == CaseRef.ref(bEntry)).not() &&
                    (CaseRef.display(plainEntry) == CaseRef.display(aEntry)) &&
                    (CaseRef.display(aEntry) == CaseRef.display(bEntry))
                """);
    }

    @Test
    void malformedAndUnsupportedRefsFailClosed() throws Exception {
        String[] refs = {
            "",
            "v1.",
            "v1",
            "v1ab",
            "v.ab",
            "x1.ab",
            "V1.ab",
            "v1.abc",
            "v1.AB",
            "v1.zz",
            "v1.a b0",
            "v2.ab",
            "v10.ab",
            "v01.ab"
        };

        for (String text : refs) {
            ProtosExecutionOutcome outcome =
                    execute(
                            "CaseRef: import(\"./CaseRef.protos\")\n"
                                    + "CaseRef.requireSupported(\""
                                    + text
                                    + "\")\n");

            assertEquals(
                    ProtosExecutionOutcome.State.FAILED,
                    outcome.state(),
                    () -> "expected fail-closed CaseRef: " + text);
        }

        assertEquals(
                "v1.ab",
                evaluateString(
                        """
                        CaseRef: import("./CaseRef.protos")
                        CaseRef.requireSupported("v1.ab")
                        """));
    }

    @Test
    void listingProjectsCasePlanOrderWithoutInvokingBodies() throws Exception {
        ProtosExecutionOutcome outcome =
                execute(
                        SUITE
                                + """

                                selection: Selection.begin(Array())
                                retained: Selection.retain(selection, cases)
                                Selection.requireComplete(selection)

                                Array(
                                    captureListing(Array(projection)),
                                    calls.value
                                )
                                """);

        assertEquals(ProtosExecutionOutcome.State.COMPLETED, outcome.state());
        ProtosArrayValue observed =
                assertInstanceOf(ProtosArrayValue.class, outcome.value());

        assertEquals(
                "{\"schema\":\"protos.test.cases/v1\",\"cases\":["
                        + listed("first")
                        + ","
                        + listed("second")
                        + ","
                        + listed("third")
                        + "]}",
                assertInstanceOf(
                                ProtosStringValue.class,
                                observed.indexedAt(0))
                        .value());
        assertEquals(String.valueOf("0"), com.guillermomolina.protos.runtime.ProtosNumericValueSupport.integerDecimalText(observed.indexedAt(1)));
    }

    /*
     * The incremental listing traverses projections in projection order, then CasePlan order
     * within each projection.
     * Exact refs are supplied in reverse order and still list in CasePlan order.
     */
    @Test
    void listingSpansProjectionsInOrderAfterReverseExactSelection() throws Exception {
        ProtosExecutionOutcome outcome =
                execute(
                        SUITE
                                + """

                                otherSuite: {
                                    tests: Array(
                                        TestValue("alpha", body),
                                        TestValue("beta", body)
                                    )
                                }
                                otherProjection:
                                    Discovery.projectionFromModule(
                                        Array("corpus", "other.protos"),
                                        otherSuite
                                    )
                                otherCases: Discovery.projectionPlan(otherProjection)

                                selection: Selection.begin(Array(
                                    CaseRef.ref(otherCases[1]),
                                    CaseRef.ref(otherCases[0]),
                                    CaseRef.ref(cases[2]),
                                    CaseRef.ref(cases[0])
                                ))
                                retained: Selection.retain(selection, cases)
                                otherRetained: Selection.retain(selection, otherCases)
                                Selection.requireComplete(selection)

                                Array(
                                    captureListing(Array(
                                        Selection.projection(projection, retained),
                                        Selection.projection(otherProjection, otherRetained)
                                    )),
                                    captureListing(Array()),
                                    calls.value
                                )
                                """);

        assertEquals(ProtosExecutionOutcome.State.COMPLETED, outcome.state());
        ProtosArrayValue observed =
                assertInstanceOf(ProtosArrayValue.class, outcome.value());

        assertEquals(
                "{\"schema\":\"protos.test.cases/v1\",\"cases\":["
                        + listed("first")
                        + ","
                        + listed("third")
                        + ","
                        + listedIn("other.protos", "alpha")
                        + ","
                        + listedIn("other.protos", "beta")
                        + "]}",
                assertInstanceOf(
                                ProtosStringValue.class,
                                observed.indexedAt(0))
                        .value());
        assertEquals(
                "{\"schema\":\"protos.test.cases/v1\",\"cases\":[]}",
                assertInstanceOf(
                                ProtosStringValue.class,
                                observed.indexedAt(1))
                        .value());
        assertEquals(String.valueOf("0"), com.guillermomolina.protos.runtime.ProtosNumericValueSupport.integerDecimalText(observed.indexedAt(2)));
    }

    /*
     * PERF031-I: the accumulator yields a fresh, unfrozen Array holding exactly the appended
     * references, in append order, for sizes on both sides of every carry boundary up to 17;
     * independent accumulators never share state and finishing leaves the accumulator intact.
     */
    @Test
    void arrayAccumulatorPreservesOrderIdentityAndIndependence() throws Exception {
        assertTrue(
                """
                ArrayAccumulator: import("./ArrayAccumulator.protos")

                values: Array()
                count: 0
                (() => count < 17).whileTrue() {
                    value: {
                        id: count
                    }
                    values = Array(...values, value)
                    count = count + 1
                }

                ok: true
                size: 0
                (() => size <= 17).whileTrue() {
                    accumulator: ArrayAccumulator.create()
                    other: ArrayAccumulator.create()
                    index: 0
                    (() => index < size).whileTrue() {
                        ArrayAccumulator.append(accumulator, values[index])
                        index = index + 1
                    }
                    ArrayAccumulator.append(other, values[0])

                    result: ArrayAccumulator.finish(accumulator)
                    again: ArrayAccumulator.finish(accumulator)
                    (result.size() == size).ifFalse(() => {
                        ok = false
                    })
                    (again.size() == size).ifFalse(() => {
                        ok = false
                    })
                    (result === again).ifTrue(() => {
                        ok = false
                    })
                    // Replacing an existing element would fail on a frozen Array.
                    (size > 0).ifTrue(() => {
                        result[0] = values[0]
                    })
                    check: 0
                    (() => check < size).whileTrue() {
                        (result[check] === values[check]).ifFalse(() => {
                            ok = false
                        })
                        (again[check] === values[check]).ifFalse(() => {
                            ok = false
                        })
                        check = check + 1
                    }
                    (ArrayAccumulator.finish(other).size() == 1).ifFalse(() => {
                        ok = false
                    })
                    size = size + 1
                }

                ok
                """);
    }

    @Test
    void exactSelectionRetainsExistingEntriesInCasePlanOrder() throws Exception {
        assertTrue(
                SUITE
                        + """

                        selection: Selection.begin(Array(
                            CaseRef.ref(cases[2]),
                            CaseRef.ref(cases[0])
                        ))
                        retained: Selection.retain(selection, cases)
                        Selection.requireComplete(selection)

                        selected: Selection.projection(projection, retained)

                        (retained.size() == 2) &&
                            (retained[0] === cases[0]) &&
                            (retained[1] === cases[2]) &&
                            (Discovery.projectionSignature(selected) ===
                                Discovery.projectionSignature(projection)) &&
                            (Discovery.projectionPlan(selected)[1] === cases[2]) &&
                            (calls.value == 0)
                        """);
    }

    @Test
    void inactiveSelectionRetainsEveryDiscoveredCase() throws Exception {
        assertTrue(
                SUITE
                        + """

                        selection: Selection.begin(Array())
                        retained: Selection.retain(selection, cases)
                        Selection.requireComplete(selection)

                        selection.active.not() &&
                            (retained.size() == 3) &&
                            (retained[0] === cases[0]) &&
                            (retained[1] === cases[1]) &&
                            (retained[2] === cases[2])
                        """);
    }

    @Test
    void repeatedExactRefFailsInsteadOfDeduplicating() throws Exception {
        assertFails(
                SUITE
                        + """

                        Selection.begin(Array(
                            CaseRef.ref(cases[1]),
                            CaseRef.ref(cases[1])
                        ))
                        """);
    }

    @Test
    void wellFormedUnknownRefFailsCompleteness() throws Exception {
        assertFails(
                SUITE
                        + """

                        selection: Selection.begin(Array(
                            CaseRef.derive(Array("corpus", "suite.protos"), "missing")
                        ))
                        retained: Selection.retain(selection, cases)
                        Selection.requireComplete(selection)
                        """);
    }

    @Test
    void refOfUndiscoveredSourceFailsCompleteness() throws Exception {
        assertFails(
                SUITE
                        + """

                        LogicalCasePlan: import("./LogicalCasePlan.protos")
                        outsideEntry: LogicalCasePlan.build(
                            Array("corpus", "outside.protos"),
                            Array("first")
                        )[0]

                        selection: Selection.begin(Array(CaseRef.ref(outsideEntry)))
                        retained: Selection.retain(selection, cases)
                        Selection.requireComplete(selection)
                        """);
    }

    @Test
    void canonicalRefCollisionIsToolAuthorityError() throws Exception {
        assertFails(
                SUITE
                        + """

                        selection: Selection.begin(Array())
                        first: Selection.retain(selection, cases)
                        second: Selection.retain(selection, cases)
                        """);
    }

    @Test
    void optionsRetainListFlagAndRepeatedCaseRefsInCliOrder() throws Exception {
        assertTrue(
                """
                Options: import("./Options.protos")

                arguments: [
                    "test",
                    "--case", "v1.bb",
                    "--file", "one.protos",
                    "--list-cases",
                    "--case", "v1.aa"
                ]
                refs: Options.caseRefs(arguments)

                Options.listCases(arguments) &&
                    Options.listCases(["test"]).not() &&
                    (Options.caseRefs(["test"]).size() == 0) &&
                    (refs.size() == 2) &&
                    (refs[0] == "v1.bb") &&
                    (refs[1] == "v1.aa") &&
                    (Options.filePaths(arguments)[0] == "one.protos")
                """);
    }

    @Test
    void shardsSpanSourcesAndCorporaWithOrderedDisjointBalancedUnion() throws Exception {
        assertTrue(
                SUITE
                        + """

                        Assertions: import("std:test/assertions")
                        Plan: import("./LogicalCasePlan.protos")
                        one: Discovery.projectionFromModule(Array("corpus", "one.protos"), {
                            tests: Array(TestValue("fourth", body))
                        })
                        other: Discovery.projectionFromModule(Array("other-corpus", "many.protos"), {
                            tests: Array(TestValue("fifth", body), TestValue("sixth", body),
                                TestValue("seventh", body), TestValue("eighth", body))
                        })
                        projections: Array(projection, one, other)
                        complete: Array(...cases, ...Discovery.projectionPlan(one),
                            ...Discovery.projectionPlan(other))
                        expected: Array(
                            Array(2, Array("first", "third", "fifth", "seventh"),
                                Array("second", "fourth", "sixth", "eighth")),
                            Array(3, Array("first", "fourth", "seventh"),
                                Array("second", "fifth", "eighth"), Array("third", "sixth")),
                            Array(5, Array("first", "sixth"), Array("second", "seventh"),
                                Array("third", "eighth"), Array("fourth"), Array("fifth")),
                            Array(10, Array("first"), Array("second"), Array("third"),
                                Array("fourth"), Array("fifth"), Array("sixth"),
                                Array("seventh"), Array("eighth"), Array(), Array())
                        )
                        for(example in expected) {
                            count: example[0]
                            union: Map()
                            minimum: complete.size()
                            maximum: 0
                            run: 1
                            while(run <= count) {
                                selection: Selection.beginWithShard(Array(), count, run)
                                retained: Array()
                                selectedProjections: Array()
                                for(original in projections) {
                                    part: Selection.retain(selection, Discovery.projectionPlan(original))
                                    retained = Array(...retained, ...part)
                                    selectedProjections = Array(...selectedProjections,
                                        Selection.projection(original, part))
                                }
                                Selection.requireComplete(selection)
                                Assertions.require(selection.active.not())
                                Assertions.require(selection.selectedPosition == complete.size())
                                Assertions.require(retained.size() == example[run].size())
                                if(retained.size() < minimum) { minimum = retained.size() }
                                if(retained.size() > maximum) { maximum = retained.size() }
                                index: 0
                                previous: -1
                                for(entry in retained) {
                                    Assertions.require(Plan.selector(entry) == example[run][index])
                                    ref: CaseRef.ref(entry)
                                    Assertions.require(union.containsKey(ref).not())
                                    union[ref] = true
                                    position: 0
                                    found: false
                                    for(original in complete) {
                                        if(entry === original) {
                                            found = true
                                            Assertions.require(position > previous)
                                            previous = position
                                        }
                                        position = position + 1
                                    }
                                    Assertions.require(found)
                                    index = index + 1
                                }
                                captureListing(selectedProjections)
                                run = run + 1
                            }
                            Assertions.require(maximum - minimum <= 1)
                            for(entry in complete) {
                                Assertions.require(union.containsKey(CaseRef.ref(entry)))
                            }
                        }
                        calls.value == 0
                        """);
    }

    @Test
    void exactSelectionPrecedesShardingAndPreservesFullSignatureAndEntries() throws Exception {
        assertTrue(
                SUITE
                        + """

                        Assertions: import("std:test/assertions")
                        Plan: import("./LogicalCasePlan.protos")
                        for(run in Array(1, 2)) {
                            selection: Selection.beginWithShard(Array(
                                CaseRef.ref(cases[2]), CaseRef.ref(cases[0])
                            ), 2, run)
                            retained: Selection.retain(selection, cases)
                            Selection.requireComplete(selection)
                            Assertions.require(selection.active)
                            Assertions.require(selection.selectedPosition == 2)
                            Assertions.require(retained.size() == 1)
                            original: cases[0]
                            if(run == 2) { original = cases[2] }
                            Assertions.require(retained[0] === original)
                            Assertions.require(Plan.sourceAssociation(retained[0]) ===
                                Plan.sourceAssociation(original))
                            Assertions.require(Plan.declarationPosition(retained[0]) ==
                                Plan.declarationPosition(original))
                            Assertions.require(CaseRef.ref(retained[0]) == CaseRef.ref(original))
                            selected: Selection.projection(projection, retained)
                            Assertions.require(Discovery.projectionSignature(selected) ===
                                Discovery.projectionSignature(projection))
                            Assertions.require(Discovery.projectionSignature(selected).size() == 3)
                            Assertions.require(Discovery.projectionPlan(selected)[0] === original)
                            captureListing(Array(selected))
                        }
                        calls.value == 0
                        """);
    }

    @Test
    void absentUnitAndHugeShardsKeepInvocationLocalStateWithoutCountSizedAllocation()
            throws Exception {
        assertTrue(
                SUITE
                        + """

                        Assertions: import("std:test/assertions")
                        inactive: Selection.begin(Array())
                        all: Selection.retain(inactive, cases)
                        Assertions.require(inactive.selectedPosition == 0)
                        Assertions.require(inactive.shardActive.not())
                        unit: Selection.beginWithShard(Array(), 1, 1)
                        unitCases: Selection.retain(unit, cases)
                        Assertions.require(unitCases.size() == all.size())
                        Assertions.require(unit.shardActive === false)
                        Assertions.require(unit.shardCount == 1)
                        Assertions.require(unit.shardRun == 1)
                        Assertions.require(unit.selectedPosition == 0)
                        unitIndex: 0
                        for(entry in unitCases) {
                            Assertions.require(entry === cases[unitIndex])
                            unitIndex = unitIndex + 1
                        }
                        Selection.requireComplete(unit)
                        exactUnit: Selection.beginWithShard(Array(
                            CaseRef.ref(cases[2]), CaseRef.ref(cases[0])
                        ), 1, 1)
                        exactUnitCases: Selection.retain(exactUnit, cases)
                        Selection.requireComplete(exactUnit)
                        Assertions.require(exactUnit.active)
                        Assertions.require(exactUnit.shardActive === false)
                        Assertions.require(exactUnitCases.size() == 2)
                        Assertions.require(exactUnitCases[0] === cases[0])
                        Assertions.require(exactUnitCases[1] === cases[2])
                        Assertions.require(exactUnit.selectedPosition == 0)
                        unresolvedUnit: Selection.beginWithShard(Array(
                            CaseRef.derive(Array("corpus", "suite.protos"), "missing")
                        ), 1, 1)
                        Selection.retain(unresolvedUnit, cases)
                        Assertions.require(unresolvedUnit.selectedPosition == 0)
                        Assertions.signals(Error, () => {
                            Selection.requireComplete(unresolvedUnit)
                        })
                        for(run in Array(1, 2, 4, 9223372036854775809)) {
                            selection: Selection.beginWithShard(Array(), 9223372036854775809, run)
                            retained: Selection.retain(selection, cases)
                            Selection.requireComplete(selection)
                            Assertions.require(selection.selectedPosition == 3)
                            if(run <= 3) {
                                Assertions.require(retained.size() == 1)
                                Assertions.require(retained[0] === cases[run - 1])
                            }
                            if(run > 3) {
                                Assertions.require(retained.size() == 0)
                                Assertions.require(captureListing(Array(
                                    Selection.projection(projection, retained))) ==
                                    "{\\"schema\\":\\"protos.test.cases/v1\\",\\"cases\\":[]}")
                            }
                        }
                        exactEmpty: Selection.beginWithShard(Array(CaseRef.ref(cases[0])), 9, 9)
                        Assertions.require(Selection.retain(exactEmpty, cases).size() == 0)
                        Selection.requireComplete(exactEmpty)
                        largePosition: Selection.beginWithShard(Array(), 2, 2)
                        largePosition.selectedPosition = 9223372036854775809
                        largeRetained: Selection.retain(largePosition, cases)
                        Assertions.require(largeRetained.size() == 2)
                        Assertions.require(largeRetained[0] === cases[0])
                        Assertions.require(largeRetained[1] === cases[2])
                        Assertions.require(largePosition.selectedPosition == 9223372036854775812)
                        (unitCases[0] === cases[0]) && (unitCases[2] === cases[2]) &&
                            (inactive.selectedPosition == 0) && (calls.value == 0)
                        """);
    }

    @Test
    void emptyShardStillRejectsUnknownRepeatedMalformedAndDuplicateDiscoveredRefs()
            throws Exception {
        assertFails(SUITE + """

                selection: Selection.beginWithShard(Array(
                    CaseRef.derive(Array("corpus", "suite.protos"), "missing")
                ), 9, 9)
                Selection.retain(selection, cases)
                Selection.requireComplete(selection)
                """);
        assertFails(SUITE + """

                Selection.beginWithShard(Array(CaseRef.ref(cases[0]), CaseRef.ref(cases[0])), 9, 9)
                """);
        assertFails(SUITE + """

                Selection.beginWithShard(Array("invalid"), 9, 9)
                """);
        assertFails(SUITE + """

                selection: Selection.beginWithShard(Array(), 9, 9)
                Selection.retain(selection, cases)
                Selection.retain(selection, cases)
                """);
    }

    @Test
    void retainedSelectorsResolveOnlyTheirDistinctBodies() throws Exception {
        assertTrue(
                SUITE
                        + """

                        Assertions: import("std:test/assertions")
                        Plan: import("./LogicalCasePlan.protos")
                        observed: Map()
                        controlled: {
                            tests: Array(
                                TestValue("a", () => { observed["a"] = true }),
                                TestValue("b", () => { observed["b"] = true }),
                                TestValue("c", () => { observed["c"] = true }),
                                TestValue("d", () => { observed["d"] = true })
                            )
                        }
                        original: Discovery.projectionFromModule(
                            Array("controlled", "bodies.protos"), controlled)
                        selection: Selection.beginWithShard(Array(), 2, 2)
                        retained: Selection.retain(selection, Discovery.projectionPlan(original))
                        Selection.requireComplete(selection)
                        selected: Selection.projection(original, retained)
                        captureListing(Array(selected))
                        Assertions.require(observed.containsKey("b").not())
                        for(entry in Discovery.projectionPlan(selected)) {
                            Discovery.resolveSelectedTest(controlled,
                                Discovery.projectionSignature(selected), Plan.selector(entry)).call()
                        }
                        observed.containsKey("b") && observed.containsKey("d") &&
                            observed.containsKey("a").not() && observed.containsKey("c").not() &&
                            (calls.value == 0)
                        """);
    }

    @Test
    void bug043IncrementalListingPreservesEveryCaseAndDisplay() throws Exception {
        StringBuilder fixture = new StringBuilder(SUITE);
        StringBuilder expected = new StringBuilder("{\"schema\":\"protos.test.cases/v1\",\"cases\":[");
        // Generated at test execution time; no large fixture or registered Cases are added.
        String selectorPrefix = "quote\" backslash\\ comma, line\n tab\t ñ 😀 " + "x".repeat(4096);
        for (int projectionIndex = 0; projectionIndex < 2; projectionIndex++) {
            String path = "synthetic-" + projectionIndex + ".protos";
            fixture.append("\nsynthetic").append(projectionIndex)
                    .append(": Discovery.projectionFromModule(Array(\"corpus\", ")
                    .append(quoted(path)).append("), { tests: Array(\n");
            for (int index = 0; index < 64; index++) {
                String selector = selectorPrefix + index;
                if (index > 0) {
                    fixture.append(",\n");
                }
                fixture.append("TestValue(").append(quoted(selector)).append(", body)");
                String payload = "[[\"corpus\"," + jsonQuoted(path) + "]," + jsonQuoted(selector) + "]";
                String identity = "v1." + HexFormat.of().formatHex(payload.getBytes(StandardCharsets.UTF_8));
                if (projectionIndex != 0 || index != 0) {
                    expected.append(',');
                }
                expected.append("{\"ref\":").append(jsonQuoted(identity))
                        .append(",\"display\":").append(jsonQuoted("corpus:" + path + "::" + selector))
                        .append('}');
            }
            fixture.append("\n) })\n");
        }
        expected.append("]}");
        String expectedDocument = expected.toString();
        org.junit.jupiter.api.Assertions.assertTrue(
                expectedDocument.getBytes(StandardCharsets.UTF_8).length > 1_048_576);
        fixture.append("""

                observed: captureListing(Array(synthetic0, synthetic1))
                parsed: JSON.parse(observed)
                Assertions: import("std:test/assertions")
                Assertions.require(parsed.kind == "object")
                Assertions.require(parsed.value.size() == 2)
                Assertions.require(parsed.value["schema"].value == "protos.test.cases/v1")
                Assertions.require(parsed.value["cases"].kind == "array")
                Assertions.require(parsed.value["cases"].value.size() == 128)
                for(record in parsed.value["cases"].value) {
                    Assertions.require(record.kind == "object")
                    Assertions.require(record.value.size() == 2)
                    Assertions.require(record.value["ref"].kind == "string")
                    Assertions.require(record.value["display"].kind == "string")
                }
                Assertions.require(calls.value == 0)
                observed
                """);
        assertEquals(expectedDocument, evaluateString(fixture.toString()));
    }

    @Test
    void bug043ListingAwaitsEveryWrite() throws Exception {
        assertTrue(SUITE + recordingWriterFixture() + """

                result: Selection.writeListing(Array(projection), sink)
                Assertions.require(result === null)
                Assertions.require(state.started == 26)
                Assertions.require(state.completed == state.started)
                Assertions.require(state.finished)
                Assertions.require(state.output == expectedListing)
                calls.value == 0
                """);
    }

    @Test
    void bug043ListingStopsAfterWriteFailure() throws Exception {
        assertTrue(SUITE + recordingWriterFixture() + """

                state.failAt = 4
                observed: Assertions.signals(Error, () => {
                    Selection.writeListing(Array(projection), sink)
                })
                Assertions.require(observed === stored)
                Assertions.require(state.started == 4)
                Assertions.require(state.completed == 3)
                Assertions.require(state.finished.not())
                Assertions.require(state.output == "{\\"schema\\":\\"protos.test.cases/v1\\"")
                repeated: Assertions.signals(Error, () => { state.failed.value() })
                Assertions.require(repeated === stored)
                (state.started == 4) && (calls.value == 0)
                """);
    }

    @Test
    void bug043ListingFinishFailurePropagates() throws Exception {
        assertTrue(SUITE + recordingWriterFixture() + """

                state.failAt = 26
                observed: Assertions.signals(Error, () => {
                    Selection.writeListing(Array(projection), sink)
                })
                Assertions.require(observed === stored)
                Assertions.require(state.started == 26)
                Assertions.require(state.completed == 25)
                Assertions.require(state.finished.not())
                Assertions.require(state.output == expectedListing)
                calls.value == 0
                """);
    }

    /** Future bodies record completion separately from write invocation, including finish's barrier. */
    private static String recordingWriterFixture() {
        return """

                Assertions: import("std:test/assertions")
                expectedListing: %s
                stored: Error()
                state: {
                    started: 0
                    completed: 0
                    finished: false
                    failAt: -1
                    failed: null
                    output: ""
                }
                sink: null
                sink = {
                    writeText: (text) => {
                        Assertions.require(state.started == state.completed)
                        Assertions.require(state.finished.not())
                        state.started = state.started + 1
                        position: state.started
                        completion: (() => {
                            if(position == state.failAt) { stored.signal() }
                            if(text == "") {
                                Assertions.require(state.output == expectedListing)
                                state.finished = true
                            }
                            state.output = state.output + text
                            state.completed = state.completed + 1
                            sink
                        }).future()
                        if(position == state.failAt) { state.failed = completion }
                        completion
                    }
                }
                """.formatted(quoted("{\"schema\":\"protos.test.cases/v1\",\"cases\":["
                        + listed("first") + "," + listed("second") + "," + listed("third") + "]}"));
    }

    /** Independent JSON spelling for expected identities, displays, and guest fixture literals. */
    private static String quoted(String text) {
        return quoted(text, false);
    }

    private static String jsonQuoted(String text) {
        return quoted(text, true);
    }

    private static String quoted(String text, boolean json) {
        StringBuilder result = new StringBuilder("\"");
        for (int index = 0; index < text.length(); index++) {
            char ch = text.charAt(index);
            switch (ch) {
                case '"' -> result.append("\\\"");
                case '\\' -> result.append("\\\\");
                case '\n' -> result.append(json ? "\\u000a" : "\\n");
                case '\r' -> result.append(json ? "\\u000d" : "\\r");
                case '\t' -> result.append(json ? "\\u0009" : "\\t");
                default -> result.append(ch);
            }
        }
        return result.append('"').toString();
    }

    private static String listed(String selector) {
        return "{\"ref\":\""
                + ref("corpus", "suite.protos", selector)
                + "\",\"display\":\"corpus:suite.protos::"
                + selector
                + "\"}";
    }

    private static String listedIn(String path, String selector) {
        return "{\"ref\":\""
                + ref("corpus", path, selector)
                + "\",\"display\":\"corpus:"
                + path
                + "::"
                + selector
                + "\"}";
    }

    /** Test-side V1 reference for simple strings that need no JSON escaping. */
    static String ref(String corpus, String path, String selector) {
        String payload =
                "[[\"" + corpus + "\",\"" + path + "\"],\"" + selector + "\"]";
        return "v1."
                + HexFormat.of()
                        .formatHex(payload.getBytes(StandardCharsets.UTF_8));
    }

    private static void assertTrue(String source) throws Exception {
        ProtosExecutionOutcome outcome = execute(source);

        assertEquals(ProtosExecutionOutcome.State.COMPLETED, outcome.state());
        assertSame(Boolean.TRUE, outcome.value());
    }

    private static void assertFails(String source) throws Exception {
        assertEquals(
                ProtosExecutionOutcome.State.FAILED,
                execute(source).state());
    }

    private static String evaluateString(String source) throws Exception {
        ProtosExecutionOutcome outcome = execute(source);

        assertEquals(ProtosExecutionOutcome.State.COMPLETED, outcome.state());
        return assertInstanceOf(ProtosStringValue.class, outcome.value()).value();
    }

    private static ProtosExecutionOutcome execute(String source) throws Exception {
        ProtosBundledToolModuleResolver resolver =
                new ProtosBundledToolModuleResolver(
                        "test",
                        TOOL_ROOT,
                        SHARED_ROOT,
                        new ProtosStandardLibraryModuleResolver(STANDARD_LIBRARY));

        ProtosPrelude prelude =
                new ProtosCoreBootstrap().bootstrap(CORE, resolver);

        return ProtosTestExecutionSupport.execute(
                source,
                com.guillermomolina.protos.execution.ProtosTestExecutionSupport.toolHarnessActivation(prelude, "test"));
    }
}
