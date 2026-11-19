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
/* I115-LONG-5 EXPERIMENTAL: general arithmetic uses specialized operations. */
package com.guillermomolina.protos.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import com.oracle.truffle.api.bytecode.Instruction;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ProtosI115Long5GeneralArithmeticTest {
    private static final Path CORE = Path.of("protos", "lib", "core");
    private static final Path OUTPUT = Path.of("target", "i115-long5-general-arithmetic");
    private static final String SOURCE = """
            add: (x, y) => { x + y }
            sub: (x, y) => { x - y }
            mul: (x, y) => { x * y }
            run: () => { add(9, 4) + sub(9, 4) + mul(9, 4) }
            chain: (x, y, z) => { x + y - z }
            runChain: () => { chain(9, 4, 3) + chain(2, 3, 4) }
            sum: (x, y) => { x + y }
            discard: (x, y) => { x + y; 0 }
            compare: (x, y) => { x < y }
            runBudgets: () => { (sum(9, 4) == 13) && (discard(9, 4) == 0) && (chain(9, 4, 3) == 10) && compare(3, 4) && (compare(4, 3) == false) }
            runRedefined: () => {
                behavior: { plus: (other) => { 77 } }
                weird: { ...behavior.alias("plus", "+") }
                sum(weird, 1)
            }
            runFallback: () => { add("a", "b") }
            runOverflow: () => {
                BigInteger.recognizes(add(9223372036854775807, 1)) && BigInteger.recognizes(sub(0 - 9223372036854775807, 2)) && BigInteger.recognizes(mul(9223372036854775807, 2))
            }
            """;

    @Test
    void generalTwoArgumentClosuresUseDirectArithmeticOperations() throws Exception {
        try (ProtosStandaloneHostedSession session = open()) {
            ProtosBytecodeRootNode run = (ProtosBytecodeRootNode)
                    session.prepareTopLevel("run").hostExecutableForTesting()
                            .target.getRootNode();
            List<ProtosBytecodeRootNode> roots = run.getRootNodes().getNodes().stream()
                    .map(ProtosBytecodeRootNode.class::cast).toList();
            for (String operation : List.of("NumberAdd", "NumberSubtract", "NumberMultiply")) {
                List<ProtosBytecodeRootNode> matches = roots.stream()
                        .filter(root -> names(root).stream().anyMatch(name ->
                                name.startsWith("c." + operation)))
                        .toList();
                assertFalse(matches.isEmpty(), "missing c." + operation);
                // A run/caller root may contain an unrelated materialization
                // after a closure call. Inspect the operation's own two-argument
                // closure root, not every root that happens to use this operator.
                assertTrue(matches.stream().anyMatch(root -> {
                    List<String> instructions = names(root);
                    return instructions.stream().noneMatch(name ->
                            name.contains("TryPrimitiveNumeric")
                                    || name.contains("IsPrimitiveNumericSendMiss")
                                    || name.contains("MaterializePrimitiveNumericCarrier")
                                    || name.startsWith("c.CallBinaryMethod")
                                    || name.startsWith("c.IsStandardNumericSelection")
                                    || name.startsWith("c.GetMethod")
                                    || name.startsWith("c.CallUnaryMethod")
                                    || name.contains("yield"));
                }), "no direct general-operator root for c." + operation
                        + "; matching roots: "
                        + matches.stream().map(ProtosI115Long5GeneralArithmeticTest::names)
                                .toList());
            }
            assertEquals(54L, session.prepareTopLevel("run")
                    .executable().execute().asLong());
        }
    }

    @Test
    void standardArithmeticHasNoExternalGuardOrDispatchPath() throws Exception {
        try (ProtosStandaloneHostedSession session = open()) {
            for (String function : List.of("add", "sub", "mul", "chain")) {
                List<String> instructions = names(rootOf(session, function));
                for (String absent : List.of(
                        "c.IsStandardNumericSelection",
                        "c.TryPrimitiveNumeric",
                        "c.IsPrimitiveNumericSendMiss",
                        "c.GetMethod",
                        "c.CallUnaryMethod",
                        "c.CallBinaryMethod")) {
                    assertTrue(instructions.stream().noneMatch(name -> name.startsWith(absent)),
                            function + " keeps " + absent + ": " + instructions);
                }
            }
        }
    }

    /*
     * The cleanup after a fused numeric operation is a budget, not a shape.
     * Only the result locals of NumberAdd and NumberSubtract are attributed to
     * the computation: neither is cleared twice without a new write, and none
     * is required to exist when the result is transported directly. Clears of
     * parameters or other locals of the root are not result cleanup. No
     * explicit clear.local runs inside a composition loop.
     */
    @Test
    void numericResultConsumedByAnotherOperationKeepsNoRedundantTemporaries()
            throws Exception {
        try (ProtosStandaloneHostedSession session = open()) {
            List<Instruction> chain = instructionsOf(session, "chain");
            List<String> instructions = names(chain);
            assertEquals(1L, count(instructions, "c.NumberAdd"), instructions.toString());
            assertEquals(1L, count(instructions, "c.NumberSubtract"), instructions.toString());
            long compositions = count(instructions, "c.IsContinuation");
            for (String producer : List.of("c.NumberAdd", "c.NumberSubtract")) {
                for (int position : positions(instructions, producer)) {
                    assertNoRedundantResultClear("chain", chain, instructions, position);
                }
            }
            assertTrue(count(instructions, "c.LoadAndClearTempLocal") <= compositions,
                    "more resumption-local clears than compositions: " + instructions);
            assertNoCleanupInsideComposition(instructions);
            assertEquals(11L, session.prepareTopLevel("runChain")
                    .executable().execute().asLong());
        }
    }

    /*
     * chain classified into computation (the fused NumberAdd and
     * NumberSubtract), external selection and dispatch, composition of a
     * possibly suspended result (IsContinuation and its loop), transport and
     * temporary cleanup. Each class has an independent upper bound; no class
     * is required to be present except the computation itself, so a correct
     * future reduction of composition, transport or cleanup to zero still
     * passes. IsContinuation is not required to be followed by a specific
     * instruction: a correct optimization may fuse that check.
     */
    @Test
    void chainSeparatesComputationCompositionAndCleanup() throws Exception {
        try (ProtosStandaloneHostedSession session = open()) {
            List<Instruction> chain = instructionsOf(session, "chain");
            List<String> instructions = names(chain);
            assertEquals(1L, count(instructions, "c.NumberAdd"), instructions.toString());
            assertEquals(1L, count(instructions, "c.NumberSubtract"), instructions.toString());
            assertComputationBudget("chain", chain, 2L);
            assertEquals(11L, session.prepareTopLevel("runChain")
                    .executable().execute().asLong());
        }
    }

    /*
     * Used and discarded results. The fused operation is emitted once per
     * semantic occurrence; neither result use introduces external selection,
     * dispatch, a guest carrier or redundant transport; and a discarded
     * result is not stored in a temporary that stays live until the end of
     * the root. Fewer composition or cleanup instructions are always
     * admitted; their disappearance alone is not a performance claim.
     */
    @Test
    void usedAndDiscardedResultsStayWithinTheirBudgets() throws Exception {
        try (ProtosStandaloneHostedSession session = open()) {
            List<Instruction> sumInstructions = instructionsOf(session, "sum");
            List<String> sum = names(sumInstructions);
            assertEquals(1L, count(sum, "c.NumberAdd"), sum.toString());
            assertComputationBudget("sum", sumInstructions, 1L);

            List<Instruction> discardInstructions = instructionsOf(session, "discard");
            List<String> discard = names(discardInstructions);
            assertEquals(1L, count(discard, "c.NumberAdd"), discard.toString());
            assertComputationBudget("discard", discardInstructions, 1L);
            // sum returns its result in tail position, so a possibly suspended
            // result is transported to the caller without composition; a
            // discarded result must be composed before the root continues.
            // Comparing the two is therefore not a budget: each root keeps its
            // own bound of one composition per operation.
            assertTrue(count(discard, "c.IsContinuation") <= 1L,
                    "discarding composes more than once: " + discard);
            assertDiscardedResultNotKeptLive(discardInstructions, discard);

            List<Instruction> chainInstructions = instructionsOf(session, "chain");
            List<String> chain = names(chainInstructions);
            assertEquals(1L, count(chain, "c.NumberAdd"), chain.toString());
            assertEquals(1L, count(chain, "c.NumberSubtract"), chain.toString());
            assertComputationBudget("chain", chainInstructions, 2L);

            List<Instruction> compareInstructions = instructionsOf(session, "compare");
            List<String> compare = names(compareInstructions);
            assertEquals(1L, count(compare, "c.RichCompareLt"), compare.toString());
            assertComputationBudget("compare", compareInstructions, 1L);

            assertTrue(session.prepareTopLevel("runBudgets")
                    .executable().execute().asBoolean());
            assertEquals(77L, session.prepareTopLevel("runRedefined")
                    .executable().execute().asLong());
        }
    }

    private static final List<String> EXTERNAL_SELECTION = List.of(
            "c.IsStandardNumericSelection",
            "c.TryPrimitiveNumeric",
            "c.IsPrimitiveNumericSendMiss");
    private static final List<String> EXTERNAL_DISPATCH = List.of(
            "c.GetMethod",
            "c.CallNilaryMethod",
            "c.CallUnaryMethod",
            "c.CallBinaryMethod",
            "c.CallRequest");

    /*
     * Transport currently emitted around each fused numeric operation (a dup
     * and a store.stackvalue). It is an admitted existing cost, not an optimal
     * shape: the budget is an upper bound and zero is accepted.
     */
    private static final long TRANSPORT_PER_OPERATION = 2L;

    /*
     * Independent upper bounds for a root whose computation is made of
     * {@code operations} fused numeric operations. The census separates
     * computation, composition, transport and result cleanup; each
     * instruction is counted at most once, and clears of other locals of the
     * root are never charged to the computation. Only the
     * computation is required; composition, transport and cleanup may all be
     * reduced to zero.
     */
    private static void assertComputationBudget(
            String function, List<Instruction> instructions, long operations) {
        List<String> names = names(instructions);
        for (String absent : EXTERNAL_SELECTION) {
            assertEquals(0L, count(names, absent),
                    function + " keeps external selection " + absent + ": " + names);
        }
        for (String absent : EXTERNAL_DISPATCH) {
            assertEquals(0L, count(names, absent),
                    function + " keeps external dispatch " + absent + ": " + names);
        }
        assertEquals(0L, count(names, "c.MaterializePrimitiveNumericCarrier"),
                function + " materializes a guest carrier: " + names);

        List<Integer> producers = new ArrayList<>();
        for (int i = 0; i < names.size(); i++) {
            if (isNumericProducer(names.get(i))) {
                producers.add(i);
            }
        }
        assertEquals(operations, producers.size(),
                function + " computation is not " + operations + " fused operations: " + names);

        long transport = names.stream()
                .filter(ProtosI115Long5GeneralArithmeticTest::isTransport).count();
        long transportBudget = TRANSPORT_PER_OPERATION * operations;
        assertTrue(transport <= transportBudget,
                function + " carries " + transport + " transport instructions, budget "
                        + transportBudget + ": " + names);

        long compositions = count(names, "c.IsContinuation");
        assertTrue(compositions <= operations,
                function + " composes more than once per operation: " + names);
        for (String part : List.of("yield", "c.ResumeDispatchedContinuation", "branch.backward")) {
            assertTrue(count(names, part) <= compositions,
                    function + ": " + part + " outside a composition: " + names);
        }

        // Result cleanup is checked per result local; clears of parameters and
        // other locals of the root are not attributed to the computation.
        for (int producer : producers) {
            assertNoRedundantResultClear(function, instructions, names, producer);
        }
        assertNoCleanupInsideComposition(names);
    }

    private static boolean isNumericProducer(String name) {
        return name.startsWith("c.Number") || name.startsWith("c.RichCompare");
    }

    private static boolean isTransport(String name) {
        return name.equals("dup")
                || name.startsWith("store.stackvalue")
                || name.startsWith("load.stackvalue")
                || name.startsWith("c.BindStackValue");
    }

    /* The local the producer's result is stored into, or null when it is transported directly. */
    private static Integer resultLocal(
            List<Instruction> instructions, List<String> names, int producer) {
        if (producer + 1 < names.size() && names.get(producer + 1).startsWith("store.local")) {
            List<Integer> stored = localOffsets(instructions.get(producer + 1));
            return stored.isEmpty() ? null : stored.get(0);
        }
        return null;
    }

    /*
     * The producer's result local, when it exists, is not cleared twice by
     * explicit clear.local without a new write in between. A
     * LoadAndClearTempLocal is a last use, not a separate clear.
     */
    private static void assertNoRedundantResultClear(
            String function, List<Instruction> instructions, List<String> names, int producer) {
        Integer local = resultLocal(instructions, names, producer);
        if (local == null) {
            return;
        }
        boolean cleared = false;
        for (int i = producer + 2; i < names.size(); i++) {
            if (!localOffsets(instructions.get(i)).contains(local)) {
                continue;
            }
            String name = names.get(i);
            if (name.startsWith("store.local")) {
                cleared = false;
            } else if (name.startsWith("clear.local")) {
                assertTrue(!cleared, function + " clears the result local " + local + " of "
                        + names.get(producer) + " twice without a new write: " + names);
                cleared = true;
            }
        }
    }

    private static void assertNoCleanupInsideComposition(List<String> instructions) {
        boolean inComposition = false;
        for (String name : instructions) {
            if (name.startsWith("c.IsContinuation")) {
                inComposition = true;
            } else if (name.startsWith("branch.backward")) {
                inComposition = false;
            } else if (inComposition) {
                assertTrue(!name.startsWith("clear.local"),
                        "clear.local inside a composition: " + instructions);
            }
        }
    }

    /*
     * The result local of the discarded NumberAdd, when one exists, is
     * released (cleared, or loaded by its last use) before the root loads its
     * final constant, so the discarded result is not kept live until the end
     * of the root. Locals stored by a composition are not the discarded
     * result. Storing nothing satisfies the property.
     */
    private static void assertDiscardedResultNotKeptLive(
            List<Instruction> instructions, List<String> names) {
        int add = -1;
        int tail = -1;
        for (int i = 0; i < names.size(); i++) {
            if (add < 0 && names.get(i).startsWith("c.NumberAdd")) {
                add = i;
            }
            if (names.get(i).startsWith("load.constant")) {
                tail = i;
            }
        }
        assertTrue(add >= 0 && tail > add, names.toString());
        Integer local = resultLocal(instructions, names, add);
        if (local == null) {
            return;
        }
        boolean released = false;
        for (int j = add + 2; j < tail && !released; j++) {
            String name = names.get(j);
            released = (name.startsWith("clear.local") || name.startsWith("c.LoadAndClearTempLocal"))
                    && localOffsets(instructions.get(j)).contains(local);
        }
        assertTrue(released, "discarded result kept live in local " + local + ": " + names);
    }

    private static List<Integer> localOffsets(Instruction instruction) {
        return instruction.getArguments().stream()
                .filter(argument -> argument.getKind() == Instruction.Argument.Kind.LOCAL_OFFSET)
                .map(Instruction.Argument::asLocalOffset)
                .toList();
    }

    private static List<Integer> positions(List<String> instructions, String prefix) {
        List<Integer> found = new ArrayList<>();
        for (int i = 0; i < instructions.size(); i++) {
            if (instructions.get(i).startsWith(prefix)) {
                found.add(i);
            }
        }
        return found;
    }

    private static long count(List<String> instructions, String prefix) {
        return instructions.stream().filter(name -> name.startsWith(prefix)).count();
    }

    @Test
    void genericFallbackAndSigned64OverflowAreStillReachable() throws Exception {
        try (ProtosStandaloneHostedSession session = open()) {
            assertEquals("ab", session.prepareTopLevel("runFallback")
                    .executable().execute().asString());
            assertTrue(session.prepareTopLevel("runOverflow")
                    .executable().execute().asBoolean());
        }
    }

    private static ProtosBytecodeRootNode rootOf(
            ProtosStandaloneHostedSession session, String function) throws Exception {
        return (ProtosBytecodeRootNode) session.prepareTopLevel(function)
                .hostExecutableForTesting().target.getRootNode();
    }

    private static List<String> names(ProtosBytecodeRootNode root) {
        return names(root.getBytecodeNode().getInstructionsAsList());
    }

    private static List<String> names(List<Instruction> instructions) {
        return instructions.stream().map(ProtosInstructionNames::of).toList();
    }

    private static List<Instruction> instructionsOf(
            ProtosStandaloneHostedSession session, String function) throws Exception {
        return rootOf(session, function).getBytecodeNode().getInstructionsAsList();
    }

    private static ProtosStandaloneHostedSession open() throws Exception {
        Files.createDirectories(OUTPUT);
        Path file = OUTPUT.resolve("general-arithmetic.protos");
        Files.writeString(file, SOURCE, StandardCharsets.UTF_8);
        ProtosStandaloneHostedSession session = ProtosStandaloneHostedSession.open(CORE, file);
        if (session.initialOutcome().state() != ProtosExecutionOutcome.State.COMPLETED) {
            session.close();
            throw new AssertionError("Module initialization failed");
        }
        return session;
    }
}
