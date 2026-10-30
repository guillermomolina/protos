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
 * I115-H7: {@code +} is NumberAdd, {@code x = x + e} on the same binding is
 * NumberInPlaceAdd and the {@code <} ending a whileTrue condition is Lt; each
 * keeps its generic (ordinary send) route inside the node, so no MISS branch
 * is expanded in the Bytecode DSL. Each case pins the exact count of every
 * H7 site. The remaining pre-H7 numeric sends ({@code -}, {@code *}, value
 * position comparisons) are still the shared guarded numeric site, whose
 * MISS branch physically holds the ordinary send and its continuation
 * machinery; that expanded fallback is counted separately and must appear
 * exactly when such a legacy site exists. DSL FULL counts are recorded, not
 * pinned.
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

    /*
     * I115-H7: the exact H7 sites of a case (NumberAdd, NumberInPlaceAdd,
     * Lt) and its pre-H7 TryPrimitiveNumeric sites.
     */
    private record Sites(int numberAdd, int numberInPlaceAdd, int lt, int legacyNumeric) {
        static Sites loop(int numberInPlaceAdd, int lt) {
            return new Sites(0, numberInPlaceAdd, lt, 0);
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
                """, 5, new Sites(1, 2, 2, 0));
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
                """, 17, new Sites(1, 0, 0, 2));
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
        int[] missPath = new int[MISS_PATH.size()];
        int numberAdd = 0;
        int numberInPlaceAdd = 0;
        int lt = 0;
        int authorityPromotions = 0;
        for (Instruction instruction
                : run.getBytecodeNode().getInstructionsAsList()) {
            String instructionName = ProtosInstructionNames.of(instruction);
            for (String forbidden : FORBIDDEN) {
                assertFalse(instructionName.contains(forbidden),
                        name + " run root contains " + instructionName);
            }
            for (int index = 0; index < missPath.length; index++) {
                if (instructionName.contains(MISS_PATH.get(index))) {
                    missPath[index]++;
                }
            }
            if (instructionName.startsWith("c.NumberAdd")) {
                numberAdd++;
            } else if (instructionName.startsWith("c.NumberInPlaceAdd")) {
                numberInPlaceAdd++;
            } else if (instructionName.startsWith("c.Lt")) {
                lt++;
            } else if (instructionName.contains("PromoteFrameLexicalAuthority")) {
                authorityPromotions++;
            }
        }
        for (int index = 0; index < missPath.length; index++) {
            System.out.println(prefix + MISS_PATH.get(index) + "_COUNT=" + missPath[index]);
        }
        System.out.println(prefix + "NUMBER_ADD_COUNT=" + numberAdd);
        System.out.println(prefix + "NUMBER_IN_PLACE_ADD_COUNT=" + numberInPlaceAdd);
        System.out.println(prefix + "LT_COUNT=" + lt);
        // I115-H7: every numeric site of the case is accounted for, exactly.
        assertEquals(sites.numberAdd(), numberAdd, name + " NumberAdd sites");
        assertEquals(sites.numberInPlaceAdd(), numberInPlaceAdd,
                name + " NumberInPlaceAdd sites");
        assertEquals(sites.lt(), lt, name + " Lt sites");
        assertEquals(sites.legacyNumeric(), missPath[0], name + " pre-H7 numeric sites");
        if (sites.legacyNumeric() == 0) {
            // H7 sites keep their fallback inside the node: no expanded MISS path.
            for (int index = 0; index < missPath.length; index++) {
                assertEquals(0, missPath[index],
                        name + " expands " + MISS_PATH.get(index) + " in the Bytecode DSL");
            }
            assertEquals(0, authorityPromotions,
                    name + " promotes its frame locals without a dispatching site");
        } else {
            // A legacy site keeps its expanded ordinary-send MISS branch.
            assertTrue(missPath[MISS_PATH.indexOf("PrepareSendOne")] > 0,
                    name + " legacy numeric site lost its ordinary send");
        }
        return new Census(roots.size(), runCount, total);
    }
}
