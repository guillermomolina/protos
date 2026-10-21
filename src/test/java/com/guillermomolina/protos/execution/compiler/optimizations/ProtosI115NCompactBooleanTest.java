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
import com.oracle.truffle.api.bytecode.LocalVariable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.Test;

/**
 * I115-N (EXPERIMENTAL, PLAT066-C / PERF041): admitted Boolean control
 * (ifTrue, ifFalse, ifTrueIfFalse, if, else) lowers inside the compact
 * root as structured IfThen/IfThenElse, without a callback root or
 * prepared dispatch. DSL FULL counts are recorded, not pinned.
 */
final class ProtosI115NCompactBooleanTest {

    private static final Path CORE = Path.of("protos", "lib", "core");

    private static final Path OUTPUT =
            Path.of("target", "perf039-while-once", "i115-n");

    private static final List<String> FORBIDDEN = List.of(
            "TryCompactCanonicalBooleanOne",
            "IsCanonicalBooleanClass",
            "PrepareSendOne",
            "PreparedBooleanCall",
            "PrepareStructuredCall",
            "TryDirectSendOne",
            "IsDirectSendMiss",
            "EnterNestedStructuredDispatch",
            "ResumeContinuation",
            "InstallFrameLexicalAuthority",
            "ReadRootFrameLocal",
            "AssignRootFrameLocal",
            "CompleteClosureCall",
            "FinishClosureCall",
            "Yield");

    private record Census(
            int rootCount, int runCount, int total, boolean resultLocal) {}

    @Test
    void perf041PrimaryCase() throws Exception {
        runCase("perf041", """
                run: () => {
                    value: true
                    result: null
                    value.ifTrue() {
                        result = 1
                    }
                    result
                }
                """, result -> assertEquals(1, result.asInt()));
    }

    @Test
    void trueIfTrueRunsCallback() throws Exception {
        runCase("true-if-true", """
                run: () => {
                    flag: true
                    out: 0
                    flag.ifTrue() {
                        out = 1
                    }
                    out
                }
                """, result -> assertEquals(1, result.asInt()));
    }

    @Test
    void falseIfTrueSkipsCallback() throws Exception {
        runCase("false-if-true", """
                run: () => {
                    seen: 7
                    off: false
                    off.ifTrue() {
                        seen = 8
                    }
                }
                """, result -> assertTrue(result.isNull()));
    }

    @Test
    void trueIfFalseSkipsCallback() throws Exception {
        runCase("true-if-false", """
                run: () => {
                    mark: 3
                    yes: true
                    yes.ifFalse() {
                        mark = 4
                    }
                }
                """, result -> assertTrue(result.isNull()));
    }

    @Test
    void falseIfFalseRunsCallback() throws Exception {
        runCase("false-if-false", """
                run: () => {
                    no: false
                    count: 0
                    no.ifFalse() {
                        count = 2
                    }
                    count
                }
                """, result -> assertEquals(2, result.asInt()));
    }

    @Test
    void ifTrueIfFalseSelectsFirst() throws Exception {
        runCase("if-true-if-false-true", """
                run: () => {
                    pick: true
                    pick.ifTrueIfFalse(() => 10, () => 20)
                }
                """, result -> assertEquals(10, result.asInt()));
    }

    @Test
    void ifTrueIfFalseSelectsSecond() throws Exception {
        runCase("if-true-if-false-false", """
                run: () => {
                    side: 1
                    choice: false
                    choice.ifTrueIfFalse(() => {
                        side = 5
                        30
                    }, () => {
                        side = 6
                        40
                    }) + side
                }
                """, result -> assertEquals(46, result.asInt()));
    }

    @Test
    void ifAndElseReturnReceiver() throws Exception {
        runCase("if-returns-receiver", """
                run: () => {
                    hit: 0
                    gate: true
                    gate.if() {
                        hit = 9
                    }
                }
                """, result -> assertTrue(result.asBoolean()));
        runCase("else-returns-receiver", """
                run: () => {
                    miss: false
                    other: 0
                    miss.else() {
                        other = 11
                    }
                }
                """, result -> assertFalse(result.asBoolean()));
    }

    @Test
    void computedCondition() throws Exception {
        runCase("computed-condition", """
                run: () => {
                    acc: 0
                    value: 2 < 3
                    value.ifTrue() {
                        acc = acc + 5
                    }
                    acc
                }
                """, result -> assertEquals(5, result.asInt()));
    }

    @Test
    void consecutiveIfTrueGrowDsl() throws Exception {
        Census single = runCase("single-if-true", """
                run: () => {
                    t: true
                    sum: 1
                    t.ifTrue() {
                        sum = sum + 2
                    }
                    sum
                }
                """, result -> assertEquals(3, result.asInt()));
        Census consecutive = runCase("consecutive-if-true", """
                run: () => {
                    t: true
                    sum: 1
                    t.ifTrue() {
                        sum = sum + 2
                    }
                    t.ifTrue() {
                        sum = sum * 4
                    }
                    sum
                }
                """, result -> assertEquals(12, result.asInt()));
        assertTrue(consecutive.runCount() > single.runCount(),
                "A second ifTrue must add instructions");
    }

    @Test
    void ifTrueInsideWhileTrue() throws Exception {
        runCase("if-true-in-while", """
                run: () => {
                    k: 0
                    hits: 0
                    (() => k < 4).whileTrue() {
                        (k < 2).ifTrue() {
                            hits = hits + 1
                        }
                        k = k + 1
                    }
                    hits
                }
                """, result -> assertEquals(2, result.asInt()));
    }

    @Test
    void consumedIfTrueReturnsCallbackResult() throws Exception {
        runCase("consumed-if-true", """
                run: () => {
                    base: 6
                    on: true
                    on.ifTrue() {
                        base * 7
                    }
                }
                """, result -> assertEquals(42, result.asInt()));
    }

    @Test
    void discardedIfTrueUsesNoResultLocal() throws Exception {
        Census census = runCase("discarded-if-true", """
                run: () => {
                    z: 1
                    ready: true
                    ready.ifTrue() {
                        z = z + 1
                    }
                    z
                }
                """, result -> assertEquals(2, result.asInt()));
        assertEquals(2, census.rootCount());
        assertFalse(census.resultLocal(),
                "A discarded ifTrue must not allocate a result local");
    }

    @Test
    void unadmittedCallbackKeepsWholeRootGeneral() throws Exception {
        String name = "unadmitted-callback";
        try (ProtosStandaloneHostedSession session = openSession(name, """
                step: () => 1
                run: () => {
                    v: 0
                    go: true
                    go.ifTrue() {
                        v = step()
                    }
                    v
                }
                """)) {
            ProtosStandaloneHostedSession.PreparedTopLevel prepared =
                    session.prepareTopLevel("run");
            ProtosBytecodeRootNode run = selectedRoot(prepared);
            run.getRootNodes().ensureSourceInformation();
            List<Instruction> instructions =
                    run.getBytecodeNode().getInstructionsAsList();
            System.out.println("I115_N_CASE=" + name);
            System.out.println("I115_N_RUN_FULL=" + instructions.size());
            // The whole root takes the general lowerer: no compact operation.
            for (Instruction instruction : instructions) {
                String instructionName = ProtosInstructionNames.of(instruction);
                assertFalse(instructionName.contains("Experimental"),
                        "Unadmitted root mixes compact " + instructionName);
            }
            assertEquals(1, prepared.executable().execute().asInt());
            System.out.println("I115_N_CORRECTNESS=PASS");
        }
    }

    private static Census runCase(
            String name, String source, Consumer<Value> expected)
            throws Exception {
        try (ProtosStandaloneHostedSession session = openSession(name, source)) {
            ProtosStandaloneHostedSession.PreparedTopLevel prepared =
                    session.prepareTopLevel("run");
            Census census = census(name, selectedRoot(prepared));
            expected.accept(prepared.executable().execute());
            System.out.println("I115_N_CORRECTNESS=PASS");
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

    private static Census census(String name, ProtosBytecodeRootNode run) {
        // DEFAULT bytecode with source information, not instrumented DEBUG.
        run.getRootNodes().ensureSourceInformation();
        List<ProtosBytecodeRootNode> roots =
                run.getRootNodes().getNodes().stream()
                        .map(ProtosBytecodeRootNode.class::cast)
                        .toList();
        System.out.println("I115_N_CASE=" + name);
        System.out.println("I115_N_ROOT_COUNT=" + roots.size());

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
            System.out.println("I115_N_ROOT" + index + "_FULL=" + count);
        }
        System.out.println("I115_N_DSL_FULL_TOTAL=" + total);

        assertEquals(2, roots.size(),
                "Literal Boolean callbacks must not add physical roots");
        boolean compact = false;
        for (Instruction instruction
                : run.getBytecodeNode().getInstructionsAsList()) {
            String instructionName = ProtosInstructionNames.of(instruction);
            compact |= instructionName.contains("Experimental");
            for (String forbidden : FORBIDDEN) {
                assertFalse(instructionName.contains(forbidden),
                        name + " run root contains " + instructionName);
            }
        }
        assertTrue(compact, name + " run root must take the compact lowerer");
        // A discarded Boolean control stores its result in no local.
        boolean consumedLocal = false;
        for (LocalVariable local : run.getBytecodeNode().getLocals()) {
            consumedLocal |= "compactBooleanResult".equals(local.getName());
        }
        System.out.println("I115_N_RESULT_LOCAL=" + consumedLocal);
        return new Census(roots.size(), runCount, total, consumedLocal);
    }
}
