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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.parser.ProtosParser;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.semantic.Canonicalizer;
import com.guillermomolina.protos.semantic.ast.CanonicalSequence;
import com.oracle.truffle.api.TruffleLanguage.LanguageReference;
import com.oracle.truffle.api.bytecode.Instruction;
import com.oracle.truffle.api.source.Source;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * PERF044: ordinary {@code caseOf} sends, whatever their source spelling,
 * run their standard recognition in the enclosing root as compiler-visible
 * alternatives, each selecting its matcher through its own ordinary
 * {@code GetMethod}/{@code CallUnaryMethod} site, with no case-table call
 * request; standard Array.match and Map.match children use the same send.
 */
final class ProtosPerf044MatchingInlineTest {
    private static final Path CORE = Path.of("protos", "lib", "core");

    private static final LanguageReference<ProtosLanguage> LANGUAGE_REF =
            LanguageReference.create(ProtosLanguage.class);

    private static final String CONTEXTUAL = """
            value: 2
            match(value) {
                0: 11
                1: 23
                2: 37
                Any: 53
            }
            """;

    private static final String EXPLICIT = """
            value: 2
            value.caseOf(%{
                0: () => 11
                1: () => 23
                2: () => 37
                Any: () => 53
            })
            """;

    @Test
    void contextualAndExplicitCaseOfConvergeOnTheSameAdmittedPath() {
        List<String> contextual = names(CONTEXTUAL);
        List<String> explicit = names(EXPLICIT);
        assertEquals(contextual, explicit,
                "both spellings are one canonical send and lower identically");
        assertEquals(1, count(contextual, "GetCaseOfSnapshot"));
        assertEquals(0, count(contextual, "PrepareStructuredCaseOf"));
    }

    @Test
    void fixedTableRunsEachActionInsideItsOwnAlternative() {
        List<String> names = names(EXPLICIT);
        // Four written alternatives plus the trailing snapshot loop.
        assertEquals(5, count(names, "StructuredMatcher"));
        // Only a written alternative commits in place and branches to the end.
        assertEquals(4, count(names, "CaseOfCommitted"));
        // One action site per written alternative, one after the loop.
        assertEquals(5, count(names, "CaseOfSelectedAction"));
        assertTrue(count(names, "CallNilaryMethod") >= 5,
                "each alternative invokes its own action through its own site");
        assertTrue(count(names, "CallBinaryMethod") >= 5,
                "each matcher is sent match through the direct method send");
        assertEquals(0, count(names, "PrepareStructuredCaseOf"),
                "no case matcher or action goes through the structured root");
        assertTrue(names.stream().anyMatch(name -> name.equals("branch")),
                "a completed alternative branches to the common end");
    }

    @Test
    void dynamicTableEntersTheSameRecognitionLoop() {
        List<String> names = names("""
                cases: %{ 0: () => 1; Any: () => 2 }
                5.caseOf(cases)
                """);
        assertEquals(1, count(names, "GetCaseOfSnapshot"));
        // The caseOf argument is a lookup: only the trailing loop is emitted.
        assertEquals(1, count(names, "StructuredMatcher"));
        assertEquals(0, count(names, "CaseOfCommitted"));
        assertEquals(1, count(names, "CaseOfSelectedAction"));
    }

    @Test
    void ordinaryEqualityLowersToTheDirectEqualityOperation() {
        List<String> names = names("""
                left: 1
                right: 2
                left = 3
                right = 3
                same: left == right
                same
                """);
        assertTrue(count(names, "RichCompareEq") >= 1,
                "== uses the direct equality operation");
        assertEquals(0, count(names, "RequireCanonicalBooleanResult"),
                "an ordinary == result is not validated as an ordering is");
    }

    @Test
    void standaloneMatchUsesTheDirectMethodSend() {
        List<String> names = names("""
                pattern: 1
                subject: 1
                pattern.match(subject)
                """);
        assertTrue(count(names, "CallBinaryMethod") >= 1,
                "standalone match is no longer excluded from the direct send");
    }

    @Test
    void spreadPacketsKeepTheOrdinaryInvocation() {
        List<String> names = names("""
                packet: [%{ Any: () => 1 }]
                5.caseOf(...packet)
                """);
        assertEquals(0, count(names, "GetCaseOfSnapshot"));
    }

    @Test
    void admittedPathPreservesResultsOfEveryMatcherFamily() throws Exception {
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
        Object result = ProtosTestExecutionSupport.evaluate("""
                Custom: { match: (subject) => subject == "custom" }
                select: (value) => match(value) {
                    0: "zero"
                    1.5: "float"
                    true: "boolean"
                    "text": "string"
                    null: "null"
                    [Capture, 2]: (first) => first
                    %{ "k": Capture }: (mapped) => mapped
                    [[Capture], %{ "n": Capture }]: (a, b) => a + b
                    Custom: "custom matcher"
                    Any: "any"
                }
                (select(0) == "zero") && (select(1.5) == "float") &&
                    (select(true) == "boolean") && (select("text") == "string") &&
                    (select(null) == "null") && (select([9, 2]) == 9) &&
                    (select(%{ "k": 4; "extra": 0 }) == 4) &&
                    (select([[3], %{ "n": 4 }]) == 7) &&
                    (select("custom") == "custom matcher") && (select(99) == "any")
                """, prelude.newModuleActivation());
        assertSame(Boolean.TRUE, result);
    }

    @Test
    void standaloneMatchKeepsTheActualResult() throws Exception {
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
        Object result = ProtosTestExecutionSupport.evaluate("""
                Odd: { match: (subject) => 7 }
                single: Capture.match(5)
                pair: [Capture, Capture].match([1, 2])
                mapped: %{ "a": Capture }.match(%{ "a": 3 })
                (Odd.match(1) == 7) && (Any.match(1) == true) && (1.match(1) == true) &&
                    (single.size() == 1) && (single[0] == 5) &&
                    (pair.size() == 2) && (pair[0] == 1) && (pair[1] == 2) &&
                    (mapped.size() == 1) && (mapped[0] == 3)
                """, prelude.newModuleActivation());
        assertSame(Boolean.TRUE, result);
    }

    @Test
    void admittedNilaryConstantActionsRunTheirBodiesInTheAlternative() throws Exception {
        List<String> names = names(EXPLICIT);
        // One admission per written alternative; the trailing loop invokes.
        assertEquals(4, count(names, "IsInlineCaseAction"));
        assertEquals(names, names(CONTEXTUAL),
                "the contextual spelling gets the same admitted actions");
        assertEquals(37L, run(EXPLICIT));
        assertEquals(37L, run(CONTEXTUAL));
    }

    @Test
    void admittedNilaryNonConstantActionRunsItsBody() throws Exception {
        String source = """
                value: 3
                value.caseOf(%{
                    1: () => 0
                    3: () => (6 * 7) + 1
                })
                """;
        assertEquals(2, count(names(source), "IsInlineCaseAction"));
        assertEquals(43L, run(source));
    }

    @Test
    void admittedPositionalActionsBindOneToFourCaptures() throws Exception {
        String source = """
                one: 5.caseOf(%{ Capture: (a) => a * 2 })
                two: [1, 2].caseOf(%{ [Capture, Capture]: (a, b) => (a * 10) + b })
                three: [1, 2, 3].caseOf(%{
                    [Capture, Capture, Capture]: (a, b, c) => (a * 100) + (b * 10) + c
                })
                four: [1, 2, 3, 4].caseOf(%{
                    [Capture, Capture, Capture, Capture]: (a, b, c, d) => (a * 1000) + (b * 100) + (c * 10) + d
                })
                (one == 10) && (two == 12) && (three == 123) && (four == 1234)
                """;
        assertEquals(4, count(names(source), "IsInlineCaseAction"));
        assertSame(Boolean.TRUE, run(source));
    }

    @Test
    void nonLiteralActionsUseTheFixedArityInvocation() throws Exception {
        String source = """
                pairAction: (a, b) => a - b
                [9, 4].caseOf(%{ [Capture, Capture]: pairAction })
                """;
        List<String> names = names(source);
        assertEquals(0, count(names, "IsInlineCaseAction"),
                "an action that is not a written Closure is never run inline");
        assertTrue(count(names, "CallBinaryMethod") >= 2,
                "two captures reach the binary invocation");
        assertTrue(count(names, "CallTernaryMethod") >= 1);
        assertTrue(count(names, "CallQuaternaryMethod") >= 1);
        assertEquals(5L, run(source));
    }

    @Test
    void moreThanFourCapturesKeepTheVariableArityInvocation() throws Exception {
        String source = """
                [1, 2, 3, 4, 5].caseOf(%{
                    [Capture, Capture, Capture, Capture, Capture]: (a, b, c, d, e) => a + b + c + d + e
                })
                """;
        List<String> names = names(source);
        assertEquals(0, count(names, "IsInlineCaseAction"),
                "five parameters exceed the fixed invocations");
        assertTrue(count(names, "CallVarargsMethod") >= 1);
        assertEquals(15L, run(source));
    }

    @Test
    void entriesPastTheUnrollBudgetKeepOrdinaryInvocation() throws Exception {
        StringBuilder source = new StringBuilder("20.caseOf(%{\n");
        for (int key = 0; key <= 20; key++) {
            source.append("    ").append(key).append(": () => ").append(key * 2).append('\n');
        }
        source.append("})\n");
        List<String> names = names(source.toString());
        assertEquals(16, count(names, "IsInlineCaseAction"),
                "only the unrolled alternatives admit their actions");
        assertEquals(40L, run(source.toString()));
    }

    @Test
    void dynamicTablesNeverAdmitAnInlineAction() throws Exception {
        String source = """
                cases: %{ 0: () => 1; Any: () => 2 }
                5.caseOf(cases)
                """;
        assertEquals(0, count(names(source), "IsInlineCaseAction"));
        assertEquals(2L, run(source));
    }

    /*
     * Repeated contextual, explicit and dynamic matching through the shared
     * method selection: varying SmallInteger, Float, mixed-number, String,
     * Boolean, null, Any and Capture patterns, ordinary matchers sharing a
     * Shape, a reassigned matcher, and custom equality reached through the
     * inherited Object.match, which runs exactly once per reached pattern.
     * This evidence is semantic: it does not observe cache admission.
     */
    @Test
    void repeatedMatchingKeepsEveryMatcherResult() throws Exception {
        String source = """
                calls: { eq: 0 }
                CustomBase: {
                    equalTo: (other) => {
                        calls.eq = calls.eq + 1
                        other == 42
                    }
                }
                Custom: CustomBase.alias("equalTo", "==")
                heir: Custom()
                first: { match: (subject) => subject == 1 }
                second: { match: (subject) => subject == 2 }
                mutable: { match: (subject) => false }
                classify: (value) => match(value) {
                    0: "zero"
                    1.5: "float"
                    true: "true"
                    false: "false"
                    "text": "string"
                    null: "null"
                    first: "first"
                    second: "second"
                    heir: "custom"
                    [Capture]: (c) => c
                    Any: "any"
                }
                explicit: (value, table) => value.caseOf(table)
                table: %{ 0: () => "zero"; Capture: (c) => c }
                state: { mismatches: 0 }
                check: (actual, expected) => {
                    (actual == expected).ifFalse(() => {
                        state.mismatches = state.mismatches + 1
                    })
                }
                rounds: [1, 2, 3, 4, 5]
                rounds.each((round) => {
                    check(classify(0), "zero")
                    check(classify(1.5), "float")
                    check(classify(1.0), "first")
                    check(classify(2), "second")
                    check(classify(true), "true")
                    check(classify(false), "false")
                    check(classify("text"), "string")
                    check(classify(null), "null")
                    check(classify(42), "custom")
                    check(classify(round + 10), "any")
                    check(classify([round]), round)
                    check(explicit(0, table), "zero")
                    check(explicit(round, table), round)
                    check(round.caseOf(%{ 0: () => "zero"; Any: () => "other" }), "other")
                    mutable.match = (subject) => subject == round
                    check(mutable.match(round), true)
                    check(mutable.match(round + 1), false)
                })
                (state.mismatches == 0) && (calls.eq == 15)
                """;
        assertSame(Boolean.TRUE, run(source));
    }

    /**
     * One stable dynamic-table site and one contextual match meet pattern
     * representations in varying order: a large Integer (complete
     * selection), SmallInteger, Float, String, an ordinary custom matcher,
     * Any and Capture. Every result is the ordinary matching result, so a
     * family reached earlier never changes a later family's outcome. This
     * evidence is semantic: cache installation for the same families is
     * observed by ProtosPerf038ITerminalMethodSendTest.
     */
    @Test
    void varyingPatternFamiliesKeepEveryMatchingResult() throws Exception {
        String source = """
                big: 100000000000000000000
                Custom: { match: (subject) => subject == "custom" }
                tables: [
                    %{ big: () => "big"; 7: () => "seven"; 2.5: () => "float"; "s": () => "string"; Custom: () => "custom"; Capture: (c) => c },
                    %{ "s": () => "string"; Custom: () => "custom"; 2.5: () => "float"; 7: () => "seven"; big: () => "big"; Any: () => "any" },
                    %{ Custom: () => "custom"; 2.5: () => "float"; big: () => "big"; "s": () => "string"; 7: () => "seven"; Capture: (c) => c }
                ]
                explicit: (value, table) => value.caseOf(table)
                contextual: (value) => match(value) {
                    100000000000000000000: "big"
                    7: "seven"
                    2.5: "float"
                    "s": "string"
                    Custom: "custom"
                    Any: "any"
                }
                state: { mismatches: 0 }
                check: (actual, expected) => {
                    (actual == expected).ifFalse(() => {
                        state.mismatches = state.mismatches + 1
                    })
                }
                rounds: [0, 1, 2, 3, 4, 5]
                rounds.each((round) => {
                    table: tables[round % 3]
                    other: if(round % 3 == 1) { "any" } else { 99 }
                    if(round % 2 == 0) {
                        check(explicit(big, table), "big")
                        check(explicit(7, table), "seven")
                        check(explicit(2.5, table), "float")
                        check(explicit("s", table), "string")
                        check(explicit("custom", table), "custom")
                        check(contextual(big), "big")
                        check(contextual(2.5), "float")
                        check(contextual("custom"), "custom")
                    } else {
                        check(explicit("custom", table), "custom")
                        check(explicit("s", table), "string")
                        check(explicit(2.5, table), "float")
                        check(explicit(7, table), "seven")
                        check(explicit(big, table), "big")
                        check(contextual("s"), "string")
                        check(contextual(7), "seven")
                        check(contextual(big), "big")
                    }
                    check(explicit(99, table), other)
                    check(contextual(99), "any")
                })
                state.mismatches == 0
                """;
        assertSame(Boolean.TRUE, run(source));
    }

    private static Object run(String source) throws Exception {
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
        return ProtosTestExecutionSupport.evaluate(source, prelude.newModuleActivation());
    }

    private static int count(List<String> names, String operation) {
        return (int) names.stream().filter(name -> name.endsWith(operation)).count();
    }

    private static List<String> names(String characters) {
        return ProtosTestExecutionSupport.callEntered(() -> lower(characters)
                .getBytecodeNode().getInstructionsAsList().stream()
                .map(Instruction::getName)
                .toList());
    }

    private static ProtosBytecodeRootNode lower(String characters) {
        Source source = Source.newBuilder(ProtosLanguage.ID, characters, "matching-inline.protos").build();
        CanonicalSequence sequence =
                (CanonicalSequence)
                        new Canonicalizer().canonicalize(new ProtosParser(characters).parseProgram());
        return new CanonicalToBytecodeLowerer(LANGUAGE_REF.get(null), source).lowerRoot(sequence);
    }
}
