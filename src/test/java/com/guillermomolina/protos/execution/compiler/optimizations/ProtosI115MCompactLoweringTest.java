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
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.oracle.truffle.api.bytecode.Instruction;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.Test;

/**
 * I115-M (EXPERIMENTAL, PLAT066-C / PERF039): the generalized compact
 * lowerer composes literal whileTrue loops, root-local bindings and numeric
 * sends without callback roots or Context-backed lexical authority.
 * I115 shared numeric lowering: NumberAdd, NumberMultiply,
 * NumberSubtract and RichCompareLt execute the standard behavior internally.
 * Each numeric site is one fused operation that classifies the selected
 * behavior and dispatches a redefined method itself: no separate
 * IsStandardNumericSelection guard or GetMethod/CallUnary fallback is
 * emitted for it. Assignment stores the result in its classified
 * binding; no separate in-place message protocol is introduced.
 * Retired H7 operations are forbidden. Full DSL counts are reported
 * independently of semantic and topology acceptance.
 */
final class ProtosI115MCompactLoweringTest {

    private static final Path CORE = Path.of("protos", "lib", "core");

    private static final Path OUTPUT =
            Path.of("target", "perf039-while-once", "i115-m");

    private static final List<String> FORBIDDEN = List.of(
            "TryDirectSendOne",
            "IsDirectSendMiss",
            "InstallFrameLexicalAuthority",
            "CreateCurrentIndexedLocalSlot",
            "ReadRootFrameLocal",
            "AssignRootFrameLocal",
            "ExperimentalInteger");

    /*
     * Present only on the expanded MISS path of a pre-H7 numeric site;
     * recorded, and required to be absent when the case has no such site.
     */
    private static final List<String> MISS_PATH = List.of(
            "TryPrimitiveNumeric",
            "PrepareSendOne",
            "EnterNestedStructuredDispatch",
            "ResumeContinuation",
            "CompleteClosureCall",
            "FinishClosureCall",
            "Yield");

    private record Census(int rootCount, int runCount, int total) {}

    /** Exact current numeric operations, not historical H7 opcodes. */
    private record Sites(int add, int multiply, int subtract, int less) {
        static Sites loop(int add, int less) {
            return new Sites(add, 0, 0, less);
        }

        int totalNumericSites() {
            return add + multiply + subtract + less;
        }
    }

    @Test
    void inlineDynamicSelectionPreparesBeforeValueOperands() throws Exception {
        try (ProtosStandaloneHostedSession session = openSession(
                "inline-dynamic-operands", """
                run: () => {
                    selected: true.ifTrue() { lateBoundName }
                    selected
                }
                """)) {
            ProtosBytecodeRootNode root = selectedRoot(session.prepareTopLevel("run"));
            List<Instruction> instructions = root.getBytecodeNode().getInstructionsAsList();
            int preparation = -1;
            int selection = -1;
            for (int index = 0; index < instructions.size(); index++) {
                String operation = ProtosInstructionNames.of(instructions.get(index));
                if (operation.contains("EnsureCurrentClosureLexicalView")) {
                    preparation = index;
                }
                if (operation.equals("c.Lookup")) {
                    assertEquals("c.AccessNamedLexical", instructions.get(index).getName());
                    assertTrue(index >= 2 && preparation >= 0 && preparation < index - 2,
                            "Frame preparation must precede both value operands: " + instructions);
                    assertTrue(ProtosInstructionNames.of(instructions.get(index - 2))
                            .contains("CurrentActivation"), instructions.toString());
                    assertEquals("lateBoundName", loadedConstant(instructions.get(index - 1)));
                    assertEquals(ProtosNamedLexicalAccess.LOOKUP,
                            instructions.get(index).getArguments().stream()
                                    .filter(argument -> argument.getName().equals("access"))
                                    .findFirst().orElseThrow().asConstant());
                    selection = index;
                }
            }
            assertTrue(selection >= 0, "The control body must inline its dynamic selection");
        }
    }

    /*
     * One construction site (a parameter named Array keeps the construction
     * lowering) receives the FROZEN standard Array, an alternative factory
     * and a mutable factory whose construct is redefined and then restored.
     * Factory identity, single argument evaluation, fresh results and the
     * agreement of the compact and the publishing body are observed; a
     * second session owns another Prelude.
     */
    @Test
    void frozenArrayConstructionSelectionKeepsDynamicFactoryDispatch() throws Exception {
        String source = """
                trace: { value: 0 }
                step: (digit, value) => {
                    trace.value = trace.value * 10 + digit
                    value
                }
                build: (Array) => Array(step(1, 10), step(2, 20))
                Other: { call: (a, b) => a + b }
                Mine: Array {}
                last: { value: null }
                standard: () => {
                    trace.value = 0
                    first: build(Array)
                    second: build(Array)
                    second[0] = 99
                    last.value = second
                    ok: (first.size() == 2) && (first[0] == 10) && (first[1] == 20)
                    ok = ok && (second[0] == 99) && !(first === second)
                    ok && (first.parent() === Array) && (trace.value == 1212)
                }
                alternative: () => {
                    trace.value = 0
                    (build(Other) == 30) && (trace.value == 12)
                }
                redefine: () => { Mine.construct: (a, b) => a * b }
                restore: () => { Mine.removeSlot("construct") }
                mine: () => {
                    trace.value = 0
                    value: build(Mine)
                    value.size() + value[0] + value[1] + trace.value
                }
                redefined: () => {
                    trace.value = 0
                    (build(Mine) == 200) && (trace.value == 12)
                }
                compact: () => Array(1, 2).size() * 10 + Array(3, 4)[1]
                published: () => {
                    k: 4
                    g: () => k
                    Array(1, 2).size() * 10 + Array(3, g())[1]
                }
                """;
        try (ProtosStandaloneHostedSession session = openSession(
                "frozen-array-construction-selection", source)) {
            for (int round = 0; round < 10; round++) {
                assertTrue(session.prepareTopLevel("standard").executable().execute().asBoolean());
            }
            assertTrue(session.prepareTopLevel("alternative").executable().execute().asBoolean());
            assertTrue(session.prepareTopLevel("standard").executable().execute().asBoolean());
            assertEquals(44L, session.prepareTopLevel("mine").executable().execute().asLong());
            session.prepareTopLevel("redefine").executable().execute();
            for (int round = 0; round < 3; round++) {
                assertTrue(session.prepareTopLevel("redefined").executable().execute().asBoolean());
            }
            session.prepareTopLevel("restore").executable().execute();
            assertEquals(44L, session.prepareTopLevel("mine").executable().execute().asLong());
            assertTrue(session.prepareTopLevel("alternative").executable().execute().asBoolean());
            assertTrue(session.prepareTopLevel("standard").executable().execute().asBoolean());
            for (int round = 0; round < 4; round++) {
                assertEquals(24L, session.prepareTopLevel("compact").executable().execute().asLong());
                assertEquals(24L, session.prepareTopLevel("published").executable().execute().asLong());
            }
            try (ProtosStandaloneHostedSession other = openSession(
                    "frozen-array-construction-selection-other", source)) {
                for (int round = 0; round < 3; round++) {
                    assertTrue(other.prepareTopLevel("standard").executable().execute().asBoolean());
                    assertTrue(other.prepareTopLevel("alternative").executable().execute().asBoolean());
                    assertTrue(session.prepareTopLevel("standard").executable().execute().asBoolean());
                }
            }
        }
    }

    @Test
    void standardArrayConstructionRetainsTypedAndGeneralOperations() throws Exception {
        try (ProtosStandaloneHostedSession session = openSession(
                "array-construction-selection", """
                longs: () => Array(1, 2)
                doubles: () => Array(1.5, 2.5)
                booleans: () => Array(true, false)
                mixed: (value) => Array(value, true, null)
                """)) {
            for (var entry : java.util.Map.of(
                    "longs", "MakeConstantLongArray",
                    "doubles", "MakeConstantDoubleArray",
                    "booleans", "MakeConstantBooleanArray",
                    "mixed", "MakeArray").entrySet()) {
                ProtosBytecodeRootNode root = selectedRoot(session.prepareTopLevel(entry.getKey()));
                List<String> names = root.getBytecodeNode().getInstructionsAsList().stream()
                        .map(ProtosInstructionNames::of).toList();
                assertTrue(names.stream().anyMatch(op -> op.contains(entry.getValue())), names.toString());
                List<Instruction> instructions = root.getBytecodeNode().getInstructionsAsList();
                int lookup = -1;
                int guard = -1;
                int construction = -1;
                int ordinaryCall = -1;
                String ordinaryOperation = entry.getKey().equals("mixed")
                        ? "c.CallTernaryMethod" : "c.CallBinaryMethod";
                for (int index = 2; index < instructions.size(); index++) {
                    String operation = names.get(index);
                    if (operation.equals("c.Lookup")) {
                        assertEquals("c.AccessNamedLexical", instructions.get(index).getName());
                        assertEquals(ProtosNamedLexicalAccess.LOOKUP,
                                instructions.get(index).getArguments().stream()
                                        .filter(argument -> argument.getName().equals("access"))
                                        .findFirst().orElseThrow().asConstant());
                        assertEquals("Array", loadedConstant(instructions.get(index - 1)));
                        assertEquals(Boolean.TRUE, loadedConstant(instructions.get(index - 2)));
                        lookup = index;
                    }
                    if (operation.contains(entry.getValue())) {
                        construction = index;
                    }
                    if (operation.equals(ordinaryOperation)) {
                        ordinaryCall = index;
                    }
                    if (operation.contains("IsStandardArrayConstructionSelection")) {
                        assertTrue(names.get(index - 1).startsWith("load.local"), names.toString());
                        guard = index;
                    }
                }
                assertTrue(lookup >= 0 && guard > lookup,
                        "The actual factory lookup must precede its standard-selection guard: " + names);
                assertTrue(construction > guard && ordinaryCall > construction,
                        "Both standard construction and the nonstandard factory call must remain: " + names);
            }
        }
    }

    @Test
    void dynamicRootLookupUsesFrameSelectionWithoutCurrentActivation() throws Exception {
        try (ProtosStandaloneHostedSession session = openSession(
                "dynamic-frame-selection", "run: () => lateBoundName")) {
            ProtosBytecodeRootNode run = selectedRoot(session.prepareTopLevel("run"));
            List<Instruction> instructions = run.getBytecodeNode().getInstructionsAsList();
            List<String> names = instructions.stream().map(ProtosInstructionNames::of).toList();
            int lookup = -1;
            for (int index = 2; index < instructions.size(); index++) {
                if (names.get(index).equals("c.Lookup")) {
                    assertEquals("c.AccessNamedLexical", instructions.get(index).getName());
                    assertEquals(ProtosNamedLexicalAccess.LOOKUP,
                            instructions.get(index).getArguments().stream()
                                    .filter(argument -> argument.getName().equals("access"))
                                    .findFirst().orElseThrow().asConstant());
                    assertEquals("lateBoundName", loadedConstant(instructions.get(index - 1)));
                    assertEquals(Boolean.TRUE, loadedConstant(instructions.get(index - 2)));
                    lookup = index;
                }
            }
            assertTrue(lookup >= 0, names.toString());
            assertEquals(-1, run.lexicalFrameLayoutOrNull().indexOf("lateBoundName"));
            assertFalse(names.stream().anyMatch(op -> op.contains("CurrentActivation")));
        }
    }

    @Test
    void multiLocalLayoutRetainsCompactDynamicReadOutsidePhysicalNames() throws Exception {
        try (ProtosStandaloneHostedSession session = openSession(
                "multi-local-dynamic-selection", """
                context.lateBoundName: 7
                run: () => {
                    first: 1
                    middle: 2
                    last: 3
                    first + middle + last + lateBoundName
                }
                """)) {
            var prepared = session.prepareTopLevel("run");
            ProtosBytecodeRootNode root = selectedRoot(prepared);
            var layout = root.lexicalFrameLayoutOrNull();
            assertEquals(3, layout.size());
            assertEquals(java.util.Set.of("first", "middle", "last"),
                    java.util.stream.IntStream.range(0, layout.size())
                            .mapToObj(layout::name).collect(java.util.stream.Collectors.toSet()));
            for (int index = 0; index < layout.size(); index++) {
                assertEquals(index, layout.indexOf(new String(layout.name(index))));
            }
            assertEquals(-1, layout.indexOf("lateBoundName"));
            List<Instruction> instructions = root.getBytecodeNode().getInstructionsAsList();
            List<String> names = instructions.stream().map(ProtosInstructionNames::of).toList();
            int lookup = -1;
            for (int index = 2; index < instructions.size(); index++) {
                if (names.get(index).equals("c.Lookup")) {
                    assertEquals("c.AccessNamedLexical", instructions.get(index).getName());
                    assertEquals(ProtosNamedLexicalAccess.LOOKUP,
                            instructions.get(index).getArguments().stream()
                                    .filter(argument -> argument.getName().equals("access"))
                                    .findFirst().orElseThrow().asConstant());
                    assertEquals("lateBoundName", loadedConstant(instructions.get(index - 1)));
                    assertEquals(Boolean.TRUE, loadedConstant(instructions.get(index - 2)));
                    lookup = index;
                }
            }
            assertTrue(lookup >= 0, names.toString());
            assertFalse(names.stream().anyMatch(name -> name.contains("CurrentActivation")));
            assertTrue(names.stream().filter(name -> name.startsWith("load.local")).count() >= 3,
                    names.toString());
            assertTrue(names.stream().filter(name -> name.startsWith("store.local")).count() >= 3,
                    names.toString());
            assertEquals(13L, prepared.executable().execute().asLong());
        }
    }

    @Test
    void observableCurrentBindingUsesItsPhysicalPresenceAwareRead() throws Exception {
        try (ProtosStandaloneHostedSession session = openSession(
                "current-binding-selection", "run: (lateBoundName) => { context\nlateBoundName }")) {
            ProtosBytecodeRootNode run = selectedRoot(session.prepareTopLevel("run"));
            List<Instruction> instructions = run.getBytecodeNode().getInstructionsAsList();
            assertTrue(run.lexicalFrameLayoutOrNull().indexOf("lateBoundName") >= 0);
            Instruction read = instructions.stream()
                    .filter(instruction -> ProtosInstructionNames.of(instruction).contains("LoadLexicalLocal"))
                    .findFirst().orElseThrow();
            assertEquals("lateBoundName", read.getArguments().stream()
                    .filter(argument -> argument.getName().equals("name"))
                    .findFirst().orElseThrow().asConstant());
            assertTrue(instructions.stream().anyMatch(instruction ->
                    ProtosInstructionNames.of(instruction).contains("PublishCurrentClosureContext")));
            assertFalse(instructions.stream().anyMatch(instruction ->
                    ProtosInstructionNames.of(instruction).equals("c.Lookup")));
        }
    }

    @Test
    void inlineObjectLookupKeepsExplicitActivationOperand() throws Exception {
        try (ProtosStandaloneHostedSession session = openSession(
                "inline-object-selection", "run: () => { { result: lateBoundName } }")) {
            ProtosBytecodeRootNode run = selectedRoot(session.prepareTopLevel("run"));
            List<Instruction> instructions = run.getBytecodeNode().getInstructionsAsList();
            List<String> names = instructions.stream().map(ProtosInstructionNames::of).toList();
            boolean explicitOwner = false;
            for (int index = 2; index < names.size(); index++) {
                if (names.get(index).equals("c.Lookup")
                        && names.get(index - 2).startsWith("load.local")) {
                    assertEquals("c.AccessNamedLexical", instructions.get(index).getName());
                    assertEquals(ProtosNamedLexicalAccess.LOOKUP,
                            instructions.get(index).getArguments().stream()
                                    .filter(argument -> argument.getName().equals("access"))
                                    .findFirst().orElseThrow().asConstant());
                    assertEquals("lateBoundName", loadedConstant(instructions.get(index - 1)));
                    explicitOwner = true;
                }
            }
            assertTrue(explicitOwner, "Inline construction must pass its exact activation: " + names);
        }
    }

    @Test
    void baseLoop() throws Exception {
        Census census = runCase("base", """
                run: () => {
                    i: 0
                    (() => i < 1).whileTrue() {
                        i = i + 1
                    }
                    i
                }
                """, 1, Sites.loop(1, 1));
        // Previous I115-K census: 11 + 15 = 26.
        System.out.println("I115_M_BASE_PREVIOUS_TOTAL=26");
        System.out.println("I115_M_BASE_TOTAL=" + census.total());
    }

    @Test
    void differentVariableAndValues() throws Exception {
        runCase("different-variable", """
                run: () => {
                    counter: 2
                    (() => counter < 5).whileTrue() {
                        counter = counter + 1
                    }
                    counter
                }
                """, 5, Sites.loop(1, 1));
    }

    @Test
    void twoVariablesAndSeveralBodyExpressions() throws Exception {
        runCase("two-variables", """
                run: () => {
                    n: 0
                    total: 0
                    (() => n < 3).whileTrue() {
                        total = total + n
                        n = n + 1
                    }
                    total
                }
                """, 3, Sites.loop(2, 1));
    }

    @Test
    void consecutiveLoopsGrowWithoutDispatch() throws Exception {
        Census single = runCase("single-loop", """
                run: () => {
                    n: 0
                    (() => n < 5).whileTrue() {
                        n = n + 1
                    }
                    n
                }
                """, 5, Sites.loop(1, 1));
        Census consecutive = runCase("consecutive-loops", """
                run: () => {
                    n: 0
                    (() => n < 2).whileTrue() {
                        n = n + 1
                    }
                    (() => n < 5).whileTrue() {
                        n = n + 1
                    }
                    n
                }
                """, 5, Sites.loop(2, 2));
        assertTrue(consecutive.runCount() > single.runCount(),
                "A second loop must add instructions");
    }

    @Test
    void nestedLoops() throws Exception {
        runCase("nested-loops", """
                run: () => {
                    outer: 0
                    inner: 0
                    (() => outer < 2).whileTrue() {
                        inner = 0
                        (() => inner < 3).whileTrue() {
                            inner = inner + 1
                        }
                        outer = outer + 1
                    }
                    outer + inner
                }
                """, 5, new Sites(3, 0, 0, 2));
    }

    @Test
    void numericOutsideWhileTrue() throws Exception {
        runCase("numeric-only", """
                run: () => {
                    a: 4
                    b: 6
                    c: a + b
                    c * 2 - 3
                }
                """, 17, new Sites(1, 1, 1, 0));
    }

    @Test
    void consumedWhileTrueReturnsLastBodyResult() throws Exception {
        runCase("consumed-loop", """
                run: () => {
                    i: 0
                    (() => i < 2).whileTrue() {
                        i = i + 1
                    }
                }
                """, 2, Sites.loop(1, 1));
    }

    @Test
    void consumedWhileTrueWithoutIterationsReturnsNull() throws Exception {
        try (ProtosStandaloneHostedSession session = openSession(
                "consumed-zero-loop", """
                run: () => {
                    i: 3
                    (() => i < 2).whileTrue() {
                        i = i + 1
                    }
                }
                """)) {
            ProtosStandaloneHostedSession.PreparedTopLevel prepared =
                    session.prepareTopLevel("run");
            census("consumed-zero-loop", selectedRoot(prepared), Sites.loop(1, 1));
            Value result = prepared.executable().execute();
            assertTrue(result.isNull(), "Zero iterations yield canonical null");
            System.out.println("I115_M_CASE[consumed-zero-loop]_CORRECTNESS=PASS");
        }
    }

    @Test
    void unadmittedBodyExpressionKeepsWholeRootGeneral() throws Exception {
        try (ProtosStandaloneHostedSession session = openSession(
                "unadmitted-body", """
                step: () => 1
                run: () => {
                    i: 0
                    (() => i < 2).whileTrue() {
                        i = i + step()
                    }
                    i
                }
                """)) {
            ProtosStandaloneHostedSession.PreparedTopLevel prepared =
                    session.prepareTopLevel("run");
            ProtosBytecodeRootNode run = selectedRoot(prepared);
            run.getRootNodes().ensureSourceInformation();
            List<Instruction> instructions =
                    run.getBytecodeNode().getInstructionsAsList();
            System.out.println("I115_M_CASE[unadmitted-body]_RUN_FULL_COUNT="
                    + instructions.size());
            // The whole root takes the general lowerer: no compact operation.
            for (Instruction instruction : instructions) {
                String instructionName = ProtosInstructionNames.of(instruction);
                assertFalse(instructionName.contains("Experimental"),
                        "Unadmitted root mixes compact " + instructionName);
            }
            assertEquals(2, prepared.executable().execute().asInt());
            System.out.println("I115_M_CASE[unadmitted-body]_CORRECTNESS=PASS");
        }
    }

    /*
     * I115: every compact Integer literal is a Java Long, also when it fits
     * in 32 bits; no Java Integer constant is a guest number of the lane.
     */
    @Test
    void compactIntegerLiteralsAreLongs() throws Exception {
        try (ProtosStandaloneHostedSession session = openSession(
                "long-literals", """
                run: () => {
                    small: 1
                    wide: 2147483648
                    (() => small < 3).whileTrue() {
                        small = small + 1
                    }
                    small + wide
                }
                """)) {
            ProtosStandaloneHostedSession.PreparedTopLevel prepared =
                    session.prepareTopLevel("run");
            ProtosBytecodeRootNode run = selectedRoot(prepared);
            census("long-literals", run, new Sites(2, 0, 0, 1));
            assertLongConstants("long-literals", run, 4);
            assertEquals(2147483651L, prepared.executable().execute().asLong());
            System.out.println("I115_M_CASE[long-literals]_CORRECTNESS=PASS");
        }
    }

    /*
     * I115: the recursive lane emits ExperimentalIntegerAdd, Subtract and
     * Multiply directly, with no wrapper, MISS branch or extra root, and its
     * stored local results keep their values.
     */
    @Test
    void recursiveLaneUsesPhysicalIntegerOperations() throws Exception {
        try (ProtosStandaloneHostedSession session = openSession(
                "recursive-operations", """
                mix: (n) => {
                    result: 1

                    (n > 1).ifTrue() {
                        result = n * 2 + mix(n - 1) - 1
                    }

                    result
                }

                run: () => { mix(4) }
                """)) {
            ProtosBytecodeRootNode mix =
                    selectedRoot(session.prepareTopLevel("mix"));
            mix.getRootNodes().ensureSourceInformation();
            assertEquals(3, mix.getRootNodes().getNodes().size(),
                    "module, mix and run are the only physical roots");
            List<String> names = mix.getBytecodeNode().getInstructionsAsList()
                    .stream().map(ProtosInstructionNames::of).toList();
            System.out.println("I115_M_CASE[recursive-operations]_DSL=" + names);
            // n * 2 + mix(n - 1) - 1: one product, one sum, two differences.
            for (java.util.Map.Entry<String, Integer> required : java.util.Map.of(
                    "c.NumberAdd", 1,
                    "c.NumberSubtract", 2,
                    "c.NumberMultiply", 1,
                    "c.RichCompareGt", 1).entrySet()) {
                assertEquals((long) required.getValue(),
                        names.stream()
                                .filter(op -> op.startsWith(required.getKey()))
                                .count(),
                        required.getKey() + ": " + names);
            }
            assertEquals(0L, names.stream()
                            .filter(op -> op.startsWith(
                                    "c.IsStandardNumericSelection"))
                            .count(),
                    "fused numeric sites keep no separate selection guard");
            for (String forbidden : List.of(
                    "ExperimentalInteger", "TryPrimitiveNumeric",
                    "PrepareSendOne", "EnterNestedStructuredDispatch")) {
                assertTrue(names.stream()
                                .noneMatch(op -> op.contains(forbidden)),
                        "recursive-operations reintroduces " + forbidden);
            }
            assertLongConstants("recursive-operations", mix, 1);
            // mix(1) = 1, mix(n) = 2n + mix(n - 1) - 1.
            assertEquals(16L,
                    session.prepareTopLevel("run").executable().execute().asLong());
            System.out.println("I115_M_CASE[recursive-operations]_CORRECTNESS=PASS");
        }
    }


    /**
     * A nonstandard receiver must execute its selected + or < method.
     * Each site is a single fused operation that dispatches the selected
     * method itself, without a separate guard or fallback send.
     */
    @Test
    void overriddenNumericOperatorsRetainOrdinarySelection()
            throws Exception {
        try (ProtosStandaloneHostedSession session = openSession(
                "numeric-overrides", """
                plusSource: { add: (other) => { 41 } }
                customPlus: plusSource.alias("add", "+")

                lessSource: { compare: (other) => { false } }
                customLess: lessSource.alias("compare", "<")

                plusOverride: () => { customPlus + 1 }
                lessOverride: () => { customLess < 3 }
                """)) {
            ProtosBytecodeRootNode plus =
                    selectedRoot(session.prepareTopLevel("plusOverride"));
            ProtosBytecodeRootNode less =
                    selectedRoot(session.prepareTopLevel("lessOverride"));
            plus.getRootNodes().ensureSourceInformation();
            less.getRootNodes().ensureSourceInformation();

            List<String> plusNames = plus.getBytecodeNode()
                    .getInstructionsAsList().stream()
                    .map(ProtosInstructionNames::of).toList();
            List<String> lessNames = less.getBytecodeNode()
                    .getInstructionsAsList().stream()
                    .map(ProtosInstructionNames::of).toList();

            assertTrue(plusNames.stream().anyMatch(op -> op.startsWith("c.NumberAdd")),
                    "c.NumberAdd: " + plusNames);
            assertTrue(lessNames.stream().anyMatch(op -> op.startsWith("c.RichCompareLt")),
                    "c.RichCompareLt: " + lessNames);
            for (String separate : List.of(
                    "c.IsStandardNumericSelection", "c.GetMethod", "c.CallUnaryMethod")) {
                assertTrue(plusNames.stream().noneMatch(op -> op.startsWith(separate)),
                        separate + " is not emitted beside the fused +: " + plusNames);
                assertTrue(lessNames.stream().noneMatch(op -> op.startsWith(separate)),
                        separate + " is not emitted beside the fused <: " + lessNames);
            }

            for (int repetition = 0; repetition < 3; repetition++) {
                assertEquals(41L,
                        session.prepareTopLevel("plusOverride")
                                .executable().execute().asLong());
                assertFalse(session.prepareTopLevel("lessOverride")
                        .executable().execute().asBoolean());
            }
            System.out.println("I115_M_DYNAMIC_NUMERIC_SELECTION=PASS");
        }
    }

    @Test
    void longIndexedLoopKeepsInternalOperationsAndDynamicSelections() throws Exception {
        try (ProtosStandaloneHostedSession session = openSession(
                "long-indexed-loop", """
                values: [1, 2, 3]
                run: () => {
                    i: 0
                    (() => i < values.size()).whileTrue() {
                        values[i] = values[i] + 1
                        i = i + 1
                    }
                    values[0] * 10 + i
                }
                redefine: () => {
                    values.atPut: (index, value) => { 77 }
                    values.size: () => 1
                    0
                }
                """)) {
            ProtosStandaloneHostedSession.PreparedTopLevel prepared =
                    session.prepareTopLevel("run");
            ProtosBytecodeRootNode root = selectedRoot(prepared);
            root.getRootNodes().ensureSourceInformation();
            List<String> names = root.getBytecodeNode().getInstructionsAsList()
                    .stream().map(ProtosInstructionNames::of).toList();
            for (String required : List.of("c.GetLen", "c.BinarySubscript",
                    "c.SetItem", "c.NumberAdd", "c.NumberMultiply", "c.RichCompareLt")) {
                assertTrue(names.stream().anyMatch(name -> name.startsWith(required)),
                        required + ": " + names);
            }
            for (String forbidden : List.of("c.CallBinaryMethod", "c.CallUnaryMethod",
                    "c.GetMethod", "c.PrepareSendOne", "c.EnterNestedStructuredDispatch")) {
                assertTrue(names.stream().noneMatch(name -> name.startsWith(forbidden)),
                        forbidden + ": " + names);
            }
            assertEquals(23L, prepared.executable().execute().asLong());
            assertEquals(33L, prepared.executable().execute().asLong());
            session.prepareTopLevel("redefine").executable().execute();
            assertEquals(31L, prepared.executable().execute().asLong(),
                    "atPut preserves the element and the redefined size limits the loop");
        }
    }

    /*
     * Bounds, FROZEN and octet checks stay inside the standard indexed
     * operations: each admitted expression emits exactly its own operation,
     * after its operands, with no message send for the checks and no Error
     * construction or handler preparation in the normal body, also after the
     * sites have executed both valid accesses and indexed Errors.
     */
    @Test
    void indexedChecksAddNoGuestOperations() throws Exception {
        try (ProtosStandaloneHostedSession session = openSession(
                "indexed-checks", """
                read: (a, i) => { a[i] }
                write: (a, i, v) => { a[i] = v }
                length: (a) => { a.size() }
                exercise: () => {
                    a: [1, 2, 3]
                    b: Encoding.UTF8.encode("jobs")
                    ok: true
                    k: 0
                    while(k < 20) {
                        ok = ok && (read(a, 1) == 2) && (write(a, 0, 1) == 1) && (length(a) == 3)
                        ok = ok && (read(b, 0) == 106) && (write(b, 1, 111) == 111) && (length(b) == 4)
                        k = k + 1
                    }
                    ok = ok && Error.handle(() => { read(a, 3); false }, (caught) => true)
                    ok = ok && Error.handle(() => { write(a, 0 - 1, 1); false }, (caught) => true)
                    ok = ok && Error.handle(() => { write(b, 0, 256); false }, (caught) => true)
                    ok && (read(a, 2) == 3) && (write(b, 3, 115) == 115)
                }
                """)) {
            assertTrue(session.prepareTopLevel("exercise").executable().execute().asBoolean());
            for (var entry : java.util.Map.of(
                    "read", "c.BinarySubscript",
                    "write", "c.SetItem",
                    "length", "c.GetLen").entrySet()) {
                ProtosBytecodeRootNode root = selectedRoot(
                        session.prepareTopLevel(entry.getKey()));
                List<Instruction> instructions = root.getBytecodeNode().getInstructionsAsList();
                List<String> names = instructions.stream()
                        .map(ProtosInstructionNames::of).toList();
                List<Integer> operations = new java.util.ArrayList<>();
                for (int i = 0; i < names.size(); i++) {
                    String name = names.get(i);
                    if (name.startsWith("c.BinarySubscript") || name.startsWith("c.SetItem")
                            || name.startsWith("c.GetLen")) {
                        operations.add(i);
                    }
                }
                assertEquals(1, operations.size(), entry.getKey() + ": " + names);
                int operation = operations.get(0);
                assertTrue(names.get(operation).startsWith(entry.getValue()),
                        entry.getKey() + ": " + names);
                int arity = entry.getKey().equals("write") ? 3
                        : entry.getKey().equals("read") ? 2 : 1;
                for (int operand = operation - arity; operand < operation; operand++) {
                    assertTrue(operand >= 0 && !names.get(operand).startsWith("store.")
                                    && !names.get(operand).startsWith("clear.")
                                    && !names.get(operand).startsWith("c.Call"),
                            entry.getKey() + " operand order: " + names);
                }
                for (String absent : List.of(
                        "c.GetMethod", "c.CallNilaryMethod", "c.CallUnaryMethod",
                        "c.CallBinaryMethod", "c.CallRequest", "c.PrepareSendOne",
                        "c.IsStandardIndexedSelection", "c.IsStructuredErrorHandler",
                        "c.PrepareStructuredErrorHandler",
                        "c.PrepareSelectedStructuredErrorHandlerCall")) {
                    assertTrue(names.stream().noneMatch(name -> name.startsWith(absent)),
                            entry.getKey() + " emits " + absent + ": " + names);
                }
                assertTrue(names.stream().noneMatch(name -> name.contains("Error")
                                || name.contains("Signal") || name.contains("Profile")),
                        entry.getKey() + " emits a check operation: " + names);
            }
        }
    }

    @Test
    void constantArrayOperationsKeepPrimitiveConstantOperands() throws Exception {
        try (ProtosStandaloneHostedSession session = openSession(
                "constant-array-operands", """
                longs: () => [1, 2, 3]
                doubles: () => [1.5, 0.0, 2.5]
                booleans: () => [true, false, true]
                """)) {
            for (var entry : java.util.Map.of(
                    "longs", long[].class,
                    "doubles", double[].class,
                    "booleans", boolean[].class).entrySet()) {
                ProtosStandaloneHostedSession.PreparedTopLevel prepared =
                        session.prepareTopLevel(entry.getKey());
                ProtosBytecodeRootNode root = selectedRoot(prepared);
                List<Instruction> instructions = root.getBytecodeNode().getInstructionsAsList();
                List<String> names = instructions.stream()
                        .map(ProtosInstructionNames::of).toList();
                int constructions = 0;
                for (Instruction instruction : instructions) {
                    if (!ProtosInstructionNames.of(instruction).contains("MakeConstant")) {
                        continue;
                    }
                    constructions++;
                    assertTrue(instruction.getArguments().stream()
                            .filter(argument -> argument.getKind()
                                    == Instruction.Argument.Kind.CONSTANT)
                            .anyMatch(argument -> entry.getValue()
                                    .isInstance(argument.asConstant())),
                            entry.getKey() + ": " + instruction);
                }
                assertEquals(1, constructions, names.toString());
                assertTrue(names.stream().noneMatch(name -> name.equals("c.MakeArray")),
                        "Admitted constant construction must not use the general vector: "
                                + names);
                Value first = prepared.executable().execute();
                Value second = prepared.executable().execute();
                assertEquals(3L, first.getArraySize());
                assertEquals(3L, second.getArraySize());
            }
        }
    }

    @Test
    void sixtyFourIntegerLiteralUsesConstantConstructionWithoutPublication()
            throws Exception {
        try (ProtosStandaloneHostedSession session = openSession(
                "sixty-four-integer-literal", """
                previous: { value: null; independent: false }
                independent: () => previous.independent
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
                    previous.independent = !(values === previous.value)
                    previous.value = values
                    values[0] = values[0] + values[63] * 1000
                    values[0] * 100 + values.size()
                }
                shadowed: () => {
                    Array: (a, b) => a + b
                    [1, 2]
                }
                """)) {
            ProtosStandaloneHostedSession.PreparedTopLevel prepared =
                    session.prepareTopLevel("run");
            ProtosBytecodeRootNode root = selectedRoot(prepared);
            List<Instruction> instructions = root.getBytecodeNode().getInstructionsAsList();
            List<String> names = instructions.stream().map(ProtosInstructionNames::of).toList();
            assertEquals(1, names.stream()
                    .filter(name -> name.contains("MakeConstantLongArray")).count(),
                    names.toString());
            for (Instruction instruction : instructions) {
                if (ProtosInstructionNames.of(instruction).contains("MakeConstantLongArray")) {
                    long[] constant = (long[]) instruction.getArguments().stream()
                            .filter(argument -> argument.getKind()
                                    == Instruction.Argument.Kind.CONSTANT)
                            .map(Instruction.Argument::asConstant)
                            .filter(long[].class::isInstance)
                            .findFirst().orElseThrow();
                    assertEquals(64, constant.length);
                    assertEquals(13L, constant[0]);
                    assertEquals(49L, constant[63]);
                }
            }
            for (String forbidden : List.of("c.MakeArray", "PublishCurrentClosureContext",
                    "CurrentActivation", "EnsureCurrentClosureLexicalView")) {
                assertTrue(names.stream().noneMatch(name -> name.contains(forbidden)),
                        forbidden + ": " + names);
            }
            long expected = (13L + 49L * 1000L) * 100L + 64L;
            var independent = session.prepareTopLevel("independent");
            for (int round = 0; round < 4; round++) {
                assertEquals(expected, prepared.executable().execute().asLong(),
                        "Every evaluation starts from the original literal");
                assertTrue(independent.executable().execute().asBoolean(),
                        "Successive invocations construct independent Arrays");
            }
            assertEquals(3L, session.prepareTopLevel("shadowed").executable().execute().asLong(),
                    "An unadmitted factory keeps its dynamic selection");
        }
    }

    /* Every numeric guest literal is a Long; metadata is separate. */
    private static void assertLongConstants(
            String name, ProtosBytecodeRootNode root, int minimum) {
        int longs = 0;
        for (Instruction instruction : root.getBytecodeNode().getInstructionsAsList()) {
            if (!ProtosInstructionNames.of(instruction)
                    .startsWith("load.constant")) {
                continue;
            }
            for (Instruction.Argument argument : instruction.getArguments()) {
                if (argument.getKind() != Instruction.Argument.Kind.CONSTANT) {
                    continue;
                }
                Object constant = argument.asConstant();
                assertFalse(constant instanceof Integer,
                        name + " loads an Integer as a guest literal: "
                                + instruction);
                if (constant instanceof Long) {
                    longs++;
                }
            }
        }
        assertTrue(longs >= minimum, name + " Long literals: " + longs);
    }

    private static Object loadedConstant(Instruction instruction) {
        assertTrue(ProtosInstructionNames.of(instruction).startsWith("load.constant"),
                instruction.toString());
        return instruction.getArguments().stream()
                .filter(argument -> argument.getKind() == Instruction.Argument.Kind.CONSTANT)
                .findFirst().orElseThrow().asConstant();
    }

    private static Census runCase(String name, String source, int expected, Sites sites)
            throws Exception {
        try (ProtosStandaloneHostedSession session = openSession(name, source)) {
            ProtosStandaloneHostedSession.PreparedTopLevel prepared =
                    session.prepareTopLevel("run");
            Census census = census(name, selectedRoot(prepared), sites);
            assertEquals(expected, prepared.executable().execute().asInt());
            System.out.println("I115_M_CASE[" + name + "]_CORRECTNESS=PASS");
            return census;
        }
    }

    private static ProtosStandaloneHostedSession openSession(
            String name, String source) throws IOException {
        Files.createDirectories(OUTPUT);
        Path sourceFile = OUTPUT.resolve(name + ".protos");
        Files.writeString(sourceFile, source, StandardCharsets.UTF_8);

        ProtosStandaloneHostedSession session =
                ProtosStandaloneHostedSession.open(CORE, sourceFile);
        if (session.initialOutcome().state()
                != ProtosExecutionOutcome.State.COMPLETED) {
            session.close();
            throw new AssertionError("Module initialization failed: " + name);
        }
        return session;
    }

    private static ProtosBytecodeRootNode selectedRoot(
            ProtosStandaloneHostedSession.PreparedTopLevel prepared) {
        return assertInstanceOf(ProtosBytecodeRootNode.class,
                prepared.hostExecutableForTesting().target.getRootNode());
    }

    private static Census census(String name, ProtosBytecodeRootNode run, Sites sites) {
        // DEFAULT bytecode with source information, not instrumented DEBUG.
        run.getRootNodes().ensureSourceInformation();
        List<ProtosBytecodeRootNode> roots =
                run.getRootNodes().getNodes().stream()
                        .map(ProtosBytecodeRootNode.class::cast)
                        .toList();
        String prefix = "I115_M_CASE[" + name + "]_";
        System.out.println(prefix + "ROOT_COUNT=" + roots.size());

        int total = 0;
        int runCount = 0;
        for (int index = 0; index < roots.size(); index++) {
            ProtosBytecodeRootNode current = roots.get(index);
            int count =
                    current.getBytecodeNode().getInstructionsAsList().size();
            total += count;
            if (current == run) {
                runCount = count;
            }
            System.out.println(prefix + "ROOT" + index + "_FULL_COUNT=" + count);
        }
        System.out.println(prefix + "DSL_FULL_TOTAL=" + total);

        assertEquals(2, roots.size(),
                "Literal whileTrue callbacks must not add physical roots");
        int[] retiredProtocol = new int[MISS_PATH.size()];
        int add = 0;
        int multiply = 0;
        int subtract = 0;
        int less = 0;
        int guards = 0;
        int selections = 0;
        int calls = 0;
        int promotions = 0;

        for (Instruction instruction
                : run.getBytecodeNode().getInstructionsAsList()) {
            String op = ProtosInstructionNames.of(instruction);
            for (String forbidden : FORBIDDEN) {
                assertFalse(op.contains(forbidden),
                        name + " contains " + op);
            }
            assertFalse(op.startsWith("c.NumberInPlaceAdd")
                            || op.startsWith("c.Lt"),
                    name + " resurrects H7: " + op);

            for (int i = 0; i < MISS_PATH.size(); i++) {
                if (op.contains(MISS_PATH.get(i))) {
                    retiredProtocol[i]++;
                }
            }

            if (op.startsWith("c.NumberAdd")) {
                add++;
            } else if (op.startsWith("c.NumberMultiply")) {
                multiply++;
            } else if (op.startsWith("c.NumberSubtract")) {
                subtract++;
            } else if (op.startsWith("c.RichCompareLt")) {
                less++;
            } else if (op.startsWith("c.IsStandardNumericSelection")) {
                guards++;
            } else if (op.startsWith("c.GetMethod")) {
                selections++;
            } else if (op.startsWith("c.CallUnaryMethod")) {
                calls++;
            }

            if (op.contains("PromoteFrameLexicalAuthority")) {
                promotions++;
            }
        }

        System.out.println(prefix + "NUMBER_ADD_COUNT=" + add);
        System.out.println(prefix + "NUMBER_MULTIPLY_COUNT=" + multiply);
        System.out.println(prefix + "NUMBER_SUBTRACT_COUNT=" + subtract);
        System.out.println(prefix + "RICH_COMPARE_LT_COUNT=" + less);
        System.out.println(prefix + "NUMERIC_SELECTION_GUARDS=" + guards);
        System.out.println(prefix + "FALLBACK_SELECTIONS=" + selections);
        System.out.println(prefix + "FALLBACK_CALLS=" + calls);

        assertEquals(sites.add(), add, name + " NumberAdd");
        assertEquals(sites.multiply(), multiply, name + " NumberMultiply");
        assertEquals(sites.subtract(), subtract, name + " NumberSubtract");
        assertEquals(sites.less(), less, name + " RichCompareLt");

        // Fused numeric sites classify and dispatch internally.
        assertEquals(0, guards,
                name + " fused numeric sites keep no separate selection guard");

        for (String retired : List.of(
                "TryPrimitiveNumeric",
                "PrepareSendOne",
                "EnterNestedStructuredDispatch",
                "CompleteClosureCall",
                "FinishClosureCall")) {
            assertEquals(0,
                    retiredProtocol[MISS_PATH.indexOf(retired)],
                    name + " reintroduces " + retired);
        }
        assertEquals(0, promotions,
                name + " unexpectedly promotes frame authority");

        return new Census(roots.size(), runCount, total);
    }
}
