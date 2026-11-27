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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.parser.ProtosParser;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosActorExecutionDomain;
import com.guillermomolina.protos.runtime.ProtosActorModuleState;
import com.guillermomolina.protos.runtime.ProtosTask;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosMapValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.guillermomolina.protos.semantic.Canonicalizer;
import com.guillermomolina.protos.semantic.ast.CanonicalSequence;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.TruffleLanguage.LanguageReference;
import com.oracle.truffle.api.bytecode.Instruction;
import com.oracle.truffle.api.source.Source;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.graalvm.polyglot.Context;

/**
 * PERF044: ordinary {@code caseOf} sends, whatever their source spelling,
 * run their standard recognition in the enclosing root as compiler-visible
 * alternatives. Admitted literal Integer tables use primitive comparisons;
 * the ordinary Map path keeps per-alternative method selection and invocation.
 * Standard Array.match and Map.match children share primitive recognition.
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

    @Test
    void complexPatternsKeepExactEqualityAndSeparateIdentityKeys() throws Exception {
        assertSame(Boolean.TRUE, run("""
                big: 100000000000000000001
                exact: Complex(big, 1/2)
                table: %{ exact: () => "exact"; Complex(1, 2): () => "mixed"; Any: () => "other" }
                select: (value) => value.caseOf(table)
                ids: IdentityMap()
                ids[Complex(big, 1/2)] = "exact"
                ids[Complex(big, 0.5)] = "float"
                ids[Complex(1, 0.0)] = "positive"
                ids[Complex(1, -0.0)] = "negative"
                (select(Complex(big, 2/4)) == "exact") &&
                    (select(Complex(1.0, 2.0)) == "mixed") &&
                    (select(Complex(big + 1, 1/2)) == "other") &&
                    (ids[Complex(big, 2/4)] == "exact") &&
                    (ids[Complex(big, 0.5)] == "float") &&
                    (ids[Complex(1, 0.0)] == "positive") &&
                    (ids[Complex(1, -0.0)] == "negative") && (ids.size() == 4)
                """));
    }

    @Test
    void admittedRegionContainsOnlyPrimitiveRecognitionAndInlineActions() {
        ProtosTestExecutionSupport.callEntered(() -> {
            List<Instruction> region = directRegion(lower(EXPLICIT));
            List<String> operations = region.stream().map(ProtosInstructionNames::of).toList();
            assertEquals(3, count(operations, "CaseLongEquals"));

            List<Instruction> comparisons = region.stream()
                    .filter(instruction -> instruction.getName()
                            .startsWith("c.LiteralCaseLongEquals"))
                    .toList();

            assertEquals(3, comparisons.size(),
                    "All admitted literal keys are instruction operands");

            List<Object> literalKeys = comparisons.stream()
                    .map(instruction -> instruction.getArguments().stream()
                            .filter(argument -> argument.getName().equals("key"))
                            .findFirst().orElseThrow().asConstant())
                    .toList();

            assertEquals(List.of(0L, 1L, 2L), literalKeys,
                    "Literal alternatives preserve their exact written order");

            List<Object> subjects = comparisons.stream()
                    .map(instruction -> instruction.getArguments().stream()
                            .filter(argument -> argument.getName().equals("subject"))
                            .findFirst().orElseThrow().asConstant())
                    .toList();

            assertTrue(subjects.stream().allMatch(
                    com.oracle.truffle.api.bytecode.LocalAccessor.class::isInstance));

            assertEquals(1L, subjects.stream().distinct().count(),
                    "Every alternative reads the same evaluated subject");

            com.oracle.truffle.api.bytecode.LocalAccessor accessor =
                    (com.oracle.truffle.api.bytecode.LocalAccessor) subjects.getFirst();

            assertEquals("directCaseSubject",
                    accessor.getLocalName(comparisons.getFirst().getBytecodeNode()));

            assertFalse(region.stream().anyMatch(instruction ->
                            instruction.getName().startsWith("dup")
                                    || instruction.getName().startsWith("load.stackvalue")),
                    "Literal alternatives must not transport the subject on the stack");

            assertFalse(region.stream().anyMatch(instruction ->
                            instruction.getName().equals("c.CaseLongEquals")),
                    "The admitted table must not transport dynamic keys");
            List<Instruction> instructions =
                    lower(EXPLICIT).getBytecodeNode().getInstructionsAsList().stream()
                            .filter(instruction ->
                                    !instruction.getName().startsWith("trace.")
                                    && !instruction.getName().startsWith("tag."))
                            .toList();

            int factoryAdmission = -1;
            int anyAdmission = -1;
            int factoryRead = -1;
            int anyRead = -1;

            for (int index = 0; index < instructions.size(); index++) {
                Instruction instruction = instructions.get(index);
                String name = ProtosInstructionNames.of(instruction);

                if (name.endsWith("AdmitsDirectLongCaseOf")) {
                    factoryAdmission = index;
                }
                if (name.endsWith("AdmitsStandardCaseAny")) {
                    anyAdmission = index;
                    break;
                }

                if (!name.startsWith("c.Lookup")) {
                    continue;
                }

                for (Instruction.Argument argument : instruction.getArguments()) {
                    if (argument.getKind() != Instruction.Argument.Kind.CONSTANT) {
                        continue;
                    }
                    if ("Map".equals(argument.asConstant())) {
                        factoryRead = index;
                    }
                    if ("Any".equals(argument.asConstant())) {
                        anyRead = index;
                    }
                }
            }

            assertTrue(factoryRead >= 0);
            assertTrue(factoryAdmission > factoryRead);
            assertTrue(anyRead > factoryAdmission);
            assertTrue(anyAdmission > anyRead);

            for (List<Instruction> operandRegion : List.of(
                    instructions.subList(factoryRead + 1, factoryAdmission),
                    instructions.subList(anyRead + 1, anyAdmission))) {
                assertFalse(operandRegion.stream().anyMatch(instruction ->
                                instruction.getName().startsWith("store.local")),
                        () -> "Admission operand must not be staged: " + operandRegion);
            }

            assertFalse(operations.stream().anyMatch(
                    name -> name.startsWith("merge.conditional")),
                    "Each selected action writes directly to the shared result");
            for (String forbidden : List.of("MakeMap", "PrepareMapInitialDefinition",
                    "StructuredTryStandardKeyOperation", "GetCaseOfSnapshot", "StructuredMatcher",
                    "StructuredHasNext", "StructuredAcceptChildOutcome", "CaseOfCommitted",
                    "CaseOfSelectedAction", "CaseOfSelectedCaptureCount", "CaseOfSelectedCapture",
                    "IsInlineCaseAction", "GetMethod", "CallBinaryMethod", "CallNilaryMethod",
                    "MaterializeClosure", "CurrentActivation", "ResumeDispatchedContinuation")) {
                assertEquals(0, count(operations, forbidden), () -> forbidden + ": " + region);
            }
            assertTrue(region.stream().anyMatch(instruction -> instruction.getName().equals("branch")),
                    "successful admission branches past ordinary Map construction");
            Object retainedFactory =
                    instructions.get(factoryAdmission).getArguments().stream()
                            .filter(argument -> argument.getName().equals("evaluatedFactory"))
                            .findFirst().orElseThrow().asConstant();

            Object releasedFactory =
                    instructions.get(anyAdmission).getArguments().stream()
                            .filter(argument -> argument.getName().equals("evaluatedFactory"))
                            .findFirst().orElseThrow().asConstant();

            assertEquals(retainedFactory.toString(), releasedFactory.toString(),
                    "Final admission releases the exact retained factory");
            return null;
        });
    }

    @Test
    void primitiveRegionHasNoSixteenAlternativeLimit() {
        StringBuilder source = new StringBuilder("20.caseOf(%{\n");
        for (int key = 0; key <= 20; key++) {
            source.append(key).append(": () => ").append(key * 2).append('\n');
        }
        source.append("})");
        ProtosTestExecutionSupport.callEntered(() -> {
            List<String> region = directRegion(lower(source.toString())).stream()
                    .map(ProtosInstructionNames::of).toList();
            assertEquals(21, count(region, "CaseLongEquals"));
            assertEquals(1, count(region, "CaseOfNoSelection"));
            assertEquals(0, count(region, "StructuredMatcher"));
            return null;
        });
    }

    @Test
    void evaluatedSubjectIsRetainedAcrossMatchingAdmissions() throws Exception {
        String program = """
                state: { visits: 0 }
                subject: () => {
                    state.visits = state.visits + 1
                    2
                }
                selected: match(subject()) {
                    0: 11
                    1: 23
                    2: 37
                    Any: 53
                }
                (selected == 37) && (state.visits == 1)
                """;

        assertSame(Boolean.TRUE, run(program));

        assertSame(Boolean.TRUE, run(
                "Any: { match: (subject) => true }\n" + program));

        assertSame(Boolean.TRUE, run("""
                state: { visits: 0 }
                subject: () => {
                    state.visits = state.visits + 1
                    {
                        caseOf: (table) => table[1]()
                    }
                }
                selected: match(subject()) {
                    0: 11
                    1: 23
                    Any: 53
                }
                (selected == 23) && (state.visits == 1)
                """));
    }

    @Test
    void matchingAdmissionReadsTheRetainedSubjectDirectly() {
        ProtosTestExecutionSupport.callEntered(() -> {
            ProtosBytecodeRootNode root = lower(EXPLICIT);
            List<Instruction> instructions =
                    root.getBytecodeNode().getInstructionsAsList();

            Instruction admission = instructions.stream()
                    .filter(instruction -> instruction.getName()
                            .startsWith("c.AdmitsDirectLongCaseOf"))
                    .findFirst().orElseThrow();

            Object subject = admission.getArguments().stream()
                    .filter(argument -> argument.getName().equals("subject"))
                    .findFirst().orElseThrow().asConstant();

            assertTrue(subject instanceof
                    com.oracle.truffle.api.bytecode.LocalAccessor);

            List<Instruction> comparisons = directRegion(root).stream()
                    .filter(instruction -> instruction.getName()
                            .startsWith("c.LiteralCaseLongEquals"))
                    .toList();

            assertEquals(3, comparisons.size());

            for (Instruction comparison : comparisons) {
                Object comparisonSubject = comparison.getArguments().stream()
                        .filter(argument -> argument.getName().equals("subject"))
                        .findFirst().orElseThrow().asConstant();

                assertEquals(subject, comparisonSubject,
                        "Admission and recognition must use the same subject");
            }

            return null;
        });
    }

    @Test
    void dynamicIntegerSubjectsSelectEveryPositionAndDefault() throws Exception {
        for (long value : new long[] {0, 1, 2, 9}) {
            long expected = switch ((int) value) { case 0 -> 11; case 1 -> 23; case 2 -> 37; default -> 53; };
            assertEquals(expected, run(EXPLICIT.replace("value: 2", "value: " + value)));
            assertEquals(expected, run(CONTEXTUAL.replace("value: 2", "value: " + value)));
        }
    }

    @Test
    void negativeAndSigned64EndpointsHaveExactValues() throws Exception {
        String source = """
                value: -9223372036854775808
                match(value) {
                    -9223372036854775808: 11
                    -1: 23
                    9223372036854775807: 37
                    Any: 53
                }
                """;
        assertEquals(1, count(names(source), "AdmitsDirectLongCaseOf"));
        ProtosTestExecutionSupport.callEntered(() -> {
            List<Object> literalKeys = directRegion(lower(source)).stream()
                    .filter(instruction -> instruction.getName()
                            .startsWith("c.LiteralCaseLongEquals"))
                    .map(instruction -> instruction.getArguments().stream()
                            .filter(argument -> argument.getName().equals("key"))
                            .findFirst().orElseThrow().asConstant())
                    .toList();

            assertEquals(List.of(Long.MIN_VALUE, -1L, Long.MAX_VALUE),
                    literalKeys);

            return null;
        });

        assertEquals(11L, run(source));
        assertEquals(23L, run(source.replace("value: -9223372036854775808", "value: -1")));
        assertEquals(37L, run(source.replace("value: -9223372036854775808", "value: 9223372036854775807")));
    }

    @Test
    void noDefaultPreservesSuccessAndTheOrdinaryError() throws Exception {
        assertEquals(23L, run("1.caseOf(%{ 0: () => 11; 1: () => 23 })"));
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
        ProtosSignalException failure = assertThrows(ProtosSignalException.class,
                () -> ProtosTestExecutionSupport.evaluate(
                        "2.caseOf(%{ 0: () => 11; 1: () => 23 })", prelude.newModuleActivation()));
        assertSame(prelude.errorPrototype(), failure.error().parent().orElseThrow());
        assertThrows(ProtosSignalException.class, () -> run("2.caseOf(%{})"));
    }

    @Test
    void exactDuplicateValuesAndDynamicKeysNeverElideConstruction() throws Exception {
        for (String source : List.of(
                "1.caseOf(%{ 1: () => 11; 0x1: () => 23 })",
                "0.caseOf(%{ 0: () => 11; -0: () => 23 })",
                "key: () => 1\n1.caseOf(%{ key(): () => 11 })",
                "action: () => 11\n1.caseOf(%{ 1: action })")) {
            assertEquals(0, count(names(source), "AdmitsDirectLongCaseOf"), source);
        }
        assertThrows(ProtosSignalException.class,
                () -> run("1.caseOf(%{ 1: () => 11; 0x1: () => 23 })"));
    }

    @Test
    void lexicalAnyAndCustomCaseOfKeepTheirActualObjects() throws Exception {
        assertEquals(37L, run("""
                Any: { match: (subject) => false }
                match(1) { 1: 37; Any: 53 }
                """));
        assertThrows(ProtosSignalException.class, () -> run("""
                Any: { match: (subject) => false }
                match(9) { 1: 37; Any: 53 }
                """));
        assertEquals(53L, run("""
                Any: { match: (subject) => [subject] }
                match(9) { 1: 37; Any: (value) => value + 44 }
                """));
        assertSame(Boolean.TRUE, run("""
                saved: { table: null }
                subject: { caseOf: (table) => { saved.table = table; 7 } }
                selected: match(subject) { 0: 11; 1: 23; Any: 53 }
                (selected == 7) && (saved.table[0]() == 11) && (saved.table[Any]() == 53)
                """));
    }

    @Test
    void inlineRegionKeepsItsOwnLexicalMatcher() throws Exception {
        String program = """
                run: () => {
                    i: 0
                    sink: 0
                    while(i < 2) {
                        Any: 9
                        result: match(9) {
                            0: 11
                            Any: 53
                        }
                        sink = sink + result
                        i = i + 1
                    }
                    sink
                }
                run()
                """;

        assertEquals(106L, run(program));

        assertThrows(ProtosSignalException.class,
                () -> run(program.replace("match(9)", "match(8)")));
    }

    @Test
    void enclosingInlineRegionKeepsItsOwnLexicalMatcher() throws Exception {
        String program = """
                run: () => {
                    i: 0
                    sink: 0
                    while(i < 2) {
                        Any: 9
                        if(true) {
                            sink = sink + match(9) {
                                0: 11
                                Any: 53
                            }
                        }
                        i = i + 1
                    }
                    sink
                }
                run()
                """;

        assertEquals(106L, run(program));

        assertThrows(ProtosSignalException.class,
                () -> run(program.replace("match(9)", "match(8)")));
    }

    @Test
    void authenticBootstrapCertifiesOnlyItsActualAnyAndIntegerBehavior() throws Exception {
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
        Object any = prelude.bindings().readLocalSlot("Any").orElseThrow();
        ProtosObjectValue factory = (ProtosObjectValue) prelude.bindings().readLocalSlot("Map").orElseThrow();
        assertTrue(ProtosBytecodeRootNode.AdmitsDirectLongCaseOf.frozenTable(factory, prelude, false));
        assertTrue(ProtosBytecodeRootNode.AdmitsDirectLongCaseOf.frozenTable(factory, prelude, true));
        assertTrue(ProtosBytecodeRootNode.AdmitsStandardCaseAny.frozenAny((ProtosObjectValue) any, prelude));
        ProtosPrelude synthetic = new ProtosPrelude(prelude.bindings(), prelude.contextPrototype());
        assertFalse(synthetic.isStandardAnyForRuntime(any));
        assertFalse(ProtosBytecodeRootNode.AdmitsDirectLongCaseOf.frozenTable(factory, synthetic, false));
        assertFalse(ProtosBytecodeRootNode.AdmitsStandardCaseAny.frozenAny((ProtosObjectValue) any, synthetic));
        assertEquals(37L, ProtosTestExecutionSupport.evaluate(EXPLICIT, synthetic.newModuleActivation()));
        ProtosObjectValue customFactory = new ProtosObjectValue(factory);
        customFactory.createLocalSlot("call", factory.readLocalSlot("construct").orElseThrow());
        customFactory.freeze();
        assertFalse(ProtosBytecodeRootNode.AdmitsDirectLongCaseOf.frozenTable(customFactory, prelude, false));
        ProtosObjectValue customAny = new ProtosObjectValue((ProtosObjectValue) any);
        customAny.freeze();
        assertFalse(ProtosBytecodeRootNode.AdmitsStandardCaseAny.frozenAny(customAny, prelude));
    }

    @Test
    void primitiveMatchAndNestedActionsReturnTheirExactNormalResults() throws Exception {
        assertSame(Boolean.TRUE, run("""
                matcher: 41
                subject: 41
                first: matcher.match(subject)
                second: matcher.match(subject + 1)
                selected: match(1) { 0: false; 1: match(2) { 2: null }; Any: true }
                first && (second == false) && (selected === null)
                """));
    }

    @Test
    void frozenNoncanonicalHashAndEqualityKeepTheirOrdinaryEffects() throws Exception {
        ProtosPrelude core = new ProtosCoreBootstrap().bootstrap(CORE);
        AtomicInteger hashes = new AtomicInteger();
        ProtosObjectValue customHash = new ProtosObjectValue(core.smallIntegerPrototype());
        customHash.createLocalSlot("hash", ProtosClosureValue.nativeClosure((activation, supplied) -> {
            hashes.incrementAndGet();
            return 0L;
        }));
        customHash.freeze();
        ProtosPrelude hashed = matchingPrelude(core, customHash);
        assertFalse(ProtosBytecodeRootNode.AdmitsDirectLongCaseOf.frozenTable(
                (ProtosObjectValue) core.bindings().readLocalSlot("Map").orElseThrow(), hashed, false));
        assertEquals(37L, ProtosTestExecutionSupport.evaluate(EXPLICIT, hashed.newModuleActivation()));
        assertEquals(3, hashes.get(), "each Integer key is hashed before any action is selected");

        ProtosObjectValue customEquality = new ProtosObjectValue(core.smallIntegerPrototype());
        customEquality.createLocalSlot("==", ProtosClosureValue.nativeClosure((activation, supplied) -> 7L));
        customEquality.freeze();
        ProtosPrelude compared = matchingPrelude(core, customEquality);
        assertFalse(ProtosBytecodeRootNode.AdmitsStandardLongMatch.frozenLongMatch(compared));
        assertEquals(7L, ProtosTestExecutionSupport.evaluate("1.match(1)", compared.newModuleActivation()),
                "Object.match returns custom equality results without coercion");
        assertThrows(ProtosSignalException.class, () -> ProtosTestExecutionSupport.evaluate(
                "1.caseOf(%{ 1: () => 37 })", compared.newModuleActivation()));
    }

    @Test
    void redefinedIntegerCaseOfReceivesTheRealMapAndLiteralActions() throws Exception {
        ProtosPrelude core = new ProtosCoreBootstrap().bootstrap(CORE);
        ProtosObjectValue custom = new ProtosObjectValue(core.smallIntegerPrototype());
        custom.createLocalSlot("caseOf", ProtosClosureValue.nativeClosure(
                (activation, supplied) -> supplied.get(0)));
        custom.freeze();
        ProtosPrelude prelude = matchingPrelude(core, custom);
        assertFalse(ProtosBytecodeRootNode.AdmitsDirectLongCaseOf.frozenTable(
                (ProtosObjectValue) core.bindings().readLocalSlot("Map").orElseThrow(), prelude, false));
        Object result = ProtosTestExecutionSupport.evaluate(EXPLICIT, prelude.newModuleActivation());
        assertTrue(result instanceof ProtosMapValue);
        ProtosMapValue table = (ProtosMapValue) result;
        assertEquals(4, table.associationSnapshot().size());
        for (Map.Entry<Object, Object> association : table.associationSnapshot()) {
            assertTrue(association.getValue() instanceof ProtosClosureValue,
                    "the custom consumer observes the materialized literal Closure");
        }
    }

    @Test
    void admittedSelectedActionReallySuspendsAndResumes() throws Exception {
        String program = """
                value: 1
                match(value) {
                    0: 11
                    1: (() => 42).future().value()
                    Any: 53
                }
                """;
        assertEquals(1, count(names(program), "AdmitsDirectLongCaseOf"));
        try (Context context = Context.newBuilder(ProtosLanguage.ID).build()) {
            context.initialize(ProtosLanguage.ID);
            context.enter();
            try {
                ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
                ProtosActorExecutionDomain domain = new ProtosActorExecutionDomain();
                ProtosActivation module = prelude.newModuleActivation(
                        new ProtosActorModuleState(), null, prelude.newExecutionContext(), domain);
                CallTarget top = ProtosLanguageContext.current().parsePublic(
                        Source.newBuilder(ProtosLanguage.ID, program, "matching-suspension.protos")
                                .mimeType(ProtosLanguage.MIME_TYPE).build());
                ProtosTask task = domain.createTask(
                        null, current -> ProtosBytecodeTaskExecution.execute(current, top, module));
                assertTrue(domain.dispatchOne());
                assertEquals(ProtosTask.State.SUSPENDED, task.state());
                for (int step = 0; step < 10 && domain.dispatchOne(); step++) {
                    // Drain only this bounded Future/task interaction.
                }
                assertEquals(ProtosTask.State.COMPLETED, task.state());
                assertEquals(42L, task.result().orElseThrow());
            } finally {
                context.leave();
            }
        }
    }

    @Test
    void sourceBackedPercentSharesPrimitiveRemainder() throws Exception {
        assertEquals(1L, run("5 % 4"));
        assertEquals(-1L, run("(-5) % 4"));
        assertEquals(1L, run("5 % (-4)"));
        assertEquals(-1L, run("(-5) % (-4)"));
        assertEquals(0L, run("(-9223372036854775808) % (-1)"));
        assertThrows(ProtosSignalException.class, () -> run("1 % 0"));

        assertEquals(1, count(names("5 % 4"), "NumberRemainder"));
        assertEquals(1, count(names("5.mod(4)"), "NumberRemainder"));

        ProtosPrelude core = new ProtosCoreBootstrap().bootstrap(CORE);
        var selected = ProtosNumericSelectionNode.selectNumeric(
                core.smallIntegerPrototype(), "%");

        assertTrue(core.isStandardIntegerPercentForRuntime(selected.read()));
        assertTrue(ProtosNumericSelectionNode.frozenSourceBackedPercent(
                selected, core));

        ProtosPrelude synthetic = new ProtosPrelude(
                core.bindings(), core.contextPrototype());

        assertFalse(ProtosNumericSelectionNode.frozenSourceBackedPercent(
                selected, synthetic));
        assertEquals(1L, ProtosTestExecutionSupport.evaluate(
                "5 % 4", synthetic.newModuleActivation()));
    }

    @Test
    void percentPreservesRedefinedSelection() throws Exception {
        ProtosPrelude core = new ProtosCoreBootstrap().bootstrap(CORE);
        ProtosObjectValue custom = new ProtosObjectValue(
                core.smallIntegerPrototype());

        AtomicInteger calls = new AtomicInteger();

        custom.createLocalSlot("%", ProtosClosureValue.nativeClosure(
                (activation, supplied) -> {
                    calls.incrementAndGet();
                    return 91L;
                }));
        custom.freeze();

        ProtosPrelude prelude = matchingPrelude(core, custom);
        var selected = ProtosNumericSelectionNode.selectNumeric(custom, "%");

        assertFalse(ProtosNumericSelectionNode.frozenSourceBackedPercent(
                selected, prelude));

        assertEquals(91L, ProtosTestExecutionSupport.evaluate(
                "5 % 4", prelude.newModuleActivation()));
        assertEquals(1, calls.get());
    }

    @Test
    void percentAdmissionRequiresCanonicalInnerSelections() throws Exception {
        ProtosPrelude core = new ProtosCoreBootstrap().bootstrap(CORE);

        for (String selector : List.of("+", "mod")) {
            ProtosObjectValue custom = new ProtosObjectValue(
                    core.smallIntegerPrototype());

            custom.createLocalSlot(selector,
                    ProtosClosureValue.nativeClosure(
                            (activation, supplied) -> 91L));
            custom.freeze();

            ProtosPrelude prelude = matchingPrelude(core, custom);

            var selected = ProtosNumericSelectionNode.selectNumeric(
                    custom, "%");

            assertTrue(prelude.isStandardIntegerPercentForRuntime(
                    selected.read()));

            assertFalse(ProtosNumericSelectionNode.frozenSourceBackedPercent(
                    selected, prelude),
                    "The source-backed operation must not elide custom " + selector);
        }
    }

    /* Retain real bootstrap certificates while changing only the tested primitive delegation. */
    private static ProtosPrelude matchingPrelude(ProtosPrelude core, ProtosObjectValue smallInteger) {
        ProtosObjectValue bindings = new ProtosObjectValue(core.contextPrototype());
        core.bindings().localSlotsSnapshot().forEach((name, value) ->
                bindings.createLocalSlot(name, name.equals("SmallInteger") ? smallInteger : value));
        bindings.freeze();
        ProtosObjectValue any = (ProtosObjectValue) core.bindings().readLocalSlot("Any").orElseThrow();
        ProtosObjectValue root = ProtosObjectValue.rootObject();
        return new ProtosPrelude(bindings, core.contextPrototype(),
                null, null, null, null, null, null, Map.of(), List.of(), Map.of(),
                null, null, null, null, null,
                any, (ProtosClosureValue) any.readLocalSlot("match").orElseThrow(),
                (ProtosClosureValue) root.readLocalSlot("match").orElseThrow(),
                (ProtosClosureValue) root.readLocalSlot("==").orElseThrow(),
                (ProtosClosureValue) core.integerPrototype().readLocalSlot("negated").orElseThrow(),
                (ProtosClosureValue) core.integerPrototype().readLocalSlot("%").orElseThrow());
    }

    /* Follow admission's false branch; ordinary instructions remain outside this interval. */
    private static List<Instruction> directRegion(ProtosBytecodeRootNode root) {
        List<Instruction> instructions = root.getBytecodeNode().getInstructionsAsList();
        Instruction admission = instructions.stream()
                .filter(instruction -> ProtosInstructionNames.of(instruction).endsWith("AdmitsDirectLongCaseOf"))
                .findFirst().orElseThrow();
        Instruction guard = instructions.stream()
                .filter(instruction -> instruction.getBytecodeIndex() > admission.getBytecodeIndex())
                .findFirst().orElseThrow();
        assertTrue(guard.getName().contains("branch.false"), guard.toString());
        int fallback = guard.getArguments().stream()
                .filter(argument -> argument.getKind() == Instruction.Argument.Kind.BYTECODE_INDEX)
                .findFirst().orElseThrow().asBytecodeIndex();
        int start = instructions.stream()
                .filter(instruction -> instruction.getBytecodeIndex() > guard.getBytecodeIndex()
                        && instruction.getBytecodeIndex() < fallback)
                .filter(instruction -> ProtosInstructionNames.of(instruction).endsWith("AdmitsStandardCaseAny"))
                .mapToInt(Instruction::getBytecodeIndex).max().orElse(guard.getBytecodeIndex());
        return instructions.stream().filter(instruction -> instruction.getBytecodeIndex() > start
                && instruction.getBytecodeIndex() < fallback).toList();
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
