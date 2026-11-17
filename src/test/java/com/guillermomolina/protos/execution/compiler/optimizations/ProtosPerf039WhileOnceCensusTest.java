/*
 * THE LICENSED WORK IS PROVIDED UNDER THE TERMS OF THE ADAPTIVE PUBLIC LICENSE
 * ("LICENSE") AS FIRST COMPLETED BY: Guillermo Adrián Molina.
 * See LICENSE.TXT at the repository root.
 */
package com.guillermomolina.protos.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.oracle.truffle.api.bytecode.BytecodeTier;
import com.oracle.truffle.api.bytecode.Instruction;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * PERF039: physical census of primitive-while-once on the I115 branch.
 *
 * The source reproduces the Protos workload in protos-benchmarks.
 * The full DSL census includes every source-group root and instruction.
 * Graph capture uses a real, prepared Closure and explicit Graal tiers.
 *
 * The BGV graph must be analyzed separately to count StructuredGraph
 * nodes at After TruffleTier. Compilation success is not a node count.
 */
final class ProtosPerf039WhileOnceCensusTest {

    private static final Path CORE = Path.of("protos", "lib", "core");

    private static final Path OUTPUT =
            Path.of("target", "perf039-while-once");

    private static final String SOURCE = """
            run: () => {
                i: 0
                (() => i < 1).whileTrue() {
                    i = i + 1
                }
                i
            }
            """;

    private static final String ZERO_SOURCE = """
            run: () => {
                i: 1
                (() => false).whileTrue() {
                    i = i + 1
                }
                i
            }
            """;

    private static final String COUNTED_SOURCE = """
            run: () => {
                i: 0
                (() => i < 8).whileTrue() {
                    i = i + 1
                }
                i
            }
            """;

    private static final String CASE = "primitive-while-once";

    @Test
    void censusWhileOnceFullDsl() throws Exception {
        try (ProtosStandaloneHostedSession session = openSession()) {
            ProtosStandaloneHostedSession.PreparedTopLevel prepared =
                    session.prepareTopLevel("run");

            ProtosBytecodeRootNode root =
                    selectedRoot(prepared);

            census(root, CASE, "dsl-only");

            assertEquals(1, prepared.executable().execute().asInt());

            System.out.println("PERF039_CORRECTNESS=PASS");
            System.out.println("PERF039_WORKLOAD=primitive-while-once");
        }
    }

    @Test
    void captureWhileOnceAtRequestedCompilerTier() throws Exception {
        Assumptions.assumeTrue(
                Boolean.getBoolean("protos.perf039.graphCapture"),
                "Real Graal capture is opt-in");

        String tierText =
                System.getProperty("protos.perf039.graphTier", "");

        assertTrue(tierText.equals("1") || tierText.equals("2"),
                "Select compiler Tier 1 or Tier 2");

        assertEquals("false",
                System.getProperty("polyglot.engine.BackgroundCompilation"),
                "Compilation must be synchronous");

        assertEquals("true",
                System.getProperty("protos.diagnostic.stableRootIdentity"),
                "Stable root identity is required");

        List<String> vmArguments = ManagementFactory
                .getRuntimeMXBean().getInputArguments();

        assertTrue(vmArguments.stream().anyMatch(
                value -> value.startsWith("-Djdk.graal.Dump=Truffle:")),
                "Graal graph dumping must be enabled at JVM startup");

        assertTrue(vmArguments.stream().anyMatch(
                value -> value.startsWith("-Djdk.graal.DumpPath=")),
                "A graph output directory is required");

        int requestedTier = Integer.parseInt(tierText);

        try (ProtosStandaloneHostedSession session = openSession()) {
            ProtosStandaloneHostedSession.PreparedTopLevel prepared =
                    session.prepareTopLevel("run");

            ProtosBytecodeRootNode root =
                    selectedRoot(prepared);

            Object target = prepared.hostExecutableForTesting().target;

            assertTrue(isOptimizedCallTarget(target.getClass()),
                    "Requires the JVM optimizing Truffle runtime");

            assertEquals(root, targetRoot(prepared));

            String targetName = root.toString();

            assertTrue(targetName.matches(
                    "protos-root:[0-9a-f]{16}\\[CLOSURE\\|[^\\] ]+\\]"),
                    "Cannot identify selected Closure: " + targetName);

            census(root, CASE, "tier" + requestedTier);

            Value run = prepared.executable();

            int guestCalls = 0;
            while (guestCalls < 128
                    && root.getBytecodeNode().getTier()
                            != BytecodeTier.CACHED) {
                assertEquals(1, run.execute().asInt());
                guestCalls++;
            }

            assertEquals(BytecodeTier.CACHED,
                    root.getBytecodeNode().getTier(),
                    "Closure did not enter cached Bytecode tier");

            System.out.println("PERF039_GUEST_WARMUP_CALLS=" + guestCalls);
            System.out.println("PERF039_SELECTED_ROOT=" + targetName);
            System.out.println("PERF039_COMPILER_REQUESTED_TIER="
                    + requestedTier);
            System.out.println("PERF039_JVM_PID="
                    + ProcessHandle.current().pid());

            boolean compiled = session.processContextForTesting()
                    .callForRuntime(() -> (Boolean) invoke(
                            target, "compile", requestedTier == 2));

            Object actualTier = invoke(target, "highestCompiledTier");

            System.out.println("PERF039_COMPILER_RETURN=" + compiled);
            System.out.println("PERF039_COMPILER_ACTUAL_TIER=" + actualTier);

            assertTrue(compiled, "Explicit Graal compilation failed");
            assertEquals(requestedTier, actualTier,
                    "Incorrect compiler tier");
            assertTrue((Boolean) invoke(target, "isValid"),
                    "Compiled target was invalidated");

            Path dump = Path.of(
                    System.getProperty("jdk.graal.DumpPath"));

            String selector = targetName.split("\\[", 2)[0];

            List<Path> graphs;
            try (var files = Files.walk(dump)) {
                graphs = files
                        .filter(Files::isRegularFile)
                        .filter(file -> file.toString().endsWith(".bgv"))
                        .filter(file -> file.getFileName()
                                .toString().contains(selector))
                        .sorted()
                        .toList();
            }

            System.out.println("PERF039_BGV_COUNT=" + graphs.size());

            for (Path graph : graphs) {
                System.out.println("PERF039_BGV="
                        + graph.toAbsolutePath());
            }

            assertFalse(graphs.isEmpty(),
                    "No BGV for the selected Closure root");

            System.out.println("PERF039_GRAPH_CAPTURE=READY");
            System.out.println("PERF039_WORKLOAD=primitive-while-once");
            System.out.println("PERF039_CORRECTNESS=PASS");
        }
    }

    /**
     * The three primitive-while workloads in one matrix. The active loops keep
     * a native Bytecode DSL loop with internal comparison and addition, clear
     * their numeric temporaries, never send whileTrue, and keep the condition
     * and body inline with their own RootTags in run's root.
     */
    @Test
    void censusPrimitiveWhileMatrix() throws Exception {
        assertPrimitiveWhileCase("primitive-while-zero", ZERO_SOURCE, 1, false);
        assertPrimitiveWhileCase("primitive-while-once", SOURCE, 1, true);
        assertPrimitiveWhileCase("primitive-while-counted", COUNTED_SOURCE, 8, true);
        System.out.println("PERF039_PRIMITIVE_WHILE_MATRIX=PASS");
    }

    private static void assertPrimitiveWhileCase(
            String caseName, String source, int expected, boolean activeLoop)
            throws Exception {
        try (ProtosStandaloneHostedSession session = openSession(caseName, source)) {
            ProtosStandaloneHostedSession.PreparedTopLevel prepared =
                    session.prepareTopLevel("run");
            ProtosBytecodeRootNode root = selectedRoot(prepared);

            census(root, caseName, "matrix");
            assertEquals(expected, prepared.executable().execute().asInt(), caseName);

            List<Instruction> instructions =
                    root.getBytecodeNode().getInstructionsAsList();
            List<String> signatures =
                    instructions.stream().map(ProtosPerf039WhileOnceCensusTest::signature).toList();

            assertFalse(signatures.stream().anyMatch(text -> text.contains("(whileTrue)")),
                    () -> caseName + " must not send whileTrue: " + signatures);
            assertFalse(signatures.stream().anyMatch(text -> text.startsWith("c.CallNilaryMethod")
                            || text.contains("MaterializeCurrentClosure")),
                    () -> caseName + " must not dispatch or materialize callbacks: " + signatures);

            if (!activeLoop) {
                return;
            }

            assertTrue(signatures.stream().anyMatch(text -> text.startsWith("branch.false")),
                    () -> caseName + " must test its Boolean condition: " + signatures);
            boolean backwardEdge = false;
            for (Instruction instruction : instructions) {
                int current = instruction.getBytecodeIndex();
                for (Instruction.Argument argument : instruction.getArguments()) {
                    if (argument.getKind() == Instruction.Argument.Kind.BYTECODE_INDEX
                            && argument.asBytecodeIndex() < current) {
                        backwardEdge = true;
                    }
                }
            }
            assertTrue(backwardEdge,
                    () -> caseName + " requires a native loop backward edge: " + signatures);
            assertTrue(signatures.stream().anyMatch(text -> text.contains("RichCompareLt")),
                    () -> caseName + " requires RichCompareLt: " + signatures);
            assertTrue(signatures.stream().anyMatch(text -> text.contains("NumberAdd")),
                    () -> caseName + " requires NumberAdd: " + signatures);

            java.util.Map<String, java.util.Set<Integer>> numericOffsets =
                    new java.util.HashMap<>();
            for (var local : root.getBytecodeNode().getLocals()) {
                Object name = local.getName();
                if ("numericResult".equals(name) || "numericResume".equals(name)
                        || "numericOperand".equals(name)) {
                    numericOffsets.computeIfAbsent((String) name, key -> new java.util.HashSet<>())
                            .add(local.getLocalOffset());
                }
            }
            java.util.Set<Integer> clearedOffsets = new java.util.HashSet<>();
            instructions.stream()
                    .filter(instruction -> ProtosInstructionNames.of(instruction).equals("clear.local"))
                    .flatMap(instruction -> instruction.getArguments().stream())
                    .filter(argument -> argument.getKind() == Instruction.Argument.Kind.LOCAL_OFFSET)
                    .forEach(argument -> clearedOffsets.add(argument.asLocalOffset()));
            assertTrue(numericOffsets.containsKey("numericResult"),
                    () -> caseName + " must own numericResult temporaries: " + numericOffsets);
            assertTrue(numericOffsets.containsKey("numericResume"),
                    () -> caseName + " must own numericResume temporaries: " + numericOffsets);
            for (var entry : numericOffsets.entrySet()) {
                for (int offset : entry.getValue()) {
                    assertTrue(clearedOffsets.contains(offset),
                            () -> caseName + " must clear " + entry.getKey() + " at offset "
                                    + offset + ": " + signatures);
                }
            }

            root.getRootNodes().ensureComplete();
            List<String> inlineRoots = new ArrayList<>();
            collectRootTagSources(root.getBytecodeNode().getTagTree(), inlineRoots);
            assertEquals(3, inlineRoots.size(),
                    () -> caseName + " run plus inline condition and body: " + inlineRoots);
            assertTrue(inlineRoots.stream().anyMatch(text -> text.contains("i < ")
                            && !text.contains("i = i + 1")),
                    () -> caseName + " inline condition RootTag: " + inlineRoots);
            assertTrue(inlineRoots.stream().anyMatch(text -> text.contains("i = i + 1")
                            && !text.contains("whileTrue")),
                    () -> caseName + " inline body RootTag: " + inlineRoots);
        }
    }

    private static void collectRootTagSources(
            com.oracle.truffle.api.bytecode.TagTree tree, List<String> sources) {
        if (tree == null) {
            return;
        }
        if (tree.hasTag(com.oracle.truffle.api.instrumentation.StandardTags.RootTag.class)) {
            var section = tree.getSourceSection();
            sources.add(section == null ? "" : section.getCharacters().toString());
        }
        for (com.oracle.truffle.api.bytecode.TagTree child : tree.getTreeChildren()) {
            collectRootTagSources(child, sources);
        }
    }

    private static ProtosStandaloneHostedSession openSession()
            throws IOException {
        return openSession(CASE, SOURCE);
    }

    private static ProtosStandaloneHostedSession openSession(
            String caseName, String source) throws IOException {
        Files.createDirectories(OUTPUT);

        Path sourceFile = OUTPUT.resolve(caseName + ".protos");
        Files.writeString(sourceFile, source, StandardCharsets.UTF_8);

        ProtosStandaloneHostedSession session =
                ProtosStandaloneHostedSession.open(CORE, sourceFile);

        if (session.initialOutcome().state()
                != ProtosExecutionOutcome.State.COMPLETED) {
            session.close();
            throw new AssertionError("Module initialization failed");
        }

        return session;
    }

    private static ProtosBytecodeRootNode selectedRoot(
            ProtosStandaloneHostedSession.PreparedTopLevel prepared) {
        return assertInstanceOf(ProtosBytecodeRootNode.class,
                prepared.hostExecutableForTesting().target.getRootNode());
    }

    private static ProtosBytecodeRootNode targetRoot(
            ProtosStandaloneHostedSession.PreparedTopLevel prepared) {
        return selectedRoot(prepared);
    }

    private static void census(
            ProtosBytecodeRootNode root, String caseName, String lane)
            throws IOException {

        // DEFAULT bytecode with source information, not instrumented DEBUG.
        root.getRootNodes().ensureSourceInformation();

        List<ProtosBytecodeRootNode> roots =
                root.getRootNodes().getNodes().stream()
                        .map(ProtosBytecodeRootNode.class::cast)
                        .toList();

        StringBuilder report = new StringBuilder();

        report.append("PERF039_CASE=").append(caseName).append('\n');
        report.append("PERF039_LANE=").append(lane).append('\n');
        report.append("PERF039_CONFIG=BytecodeConfig.DEFAULT\n");
        report.append("PERF039_ROOT_COUNT=").append(roots.size())
                .append('\n');

        int total = 0;

        for (int index = 0; index < roots.size(); index++) {
            ProtosBytecodeRootNode current = roots.get(index);

            List<Instruction> instructions =
                    current.getBytecodeNode().getInstructionsAsList();

            int count = instructions.size();
            total += count;

            report.append("PERF039_ROOT_INDEX=").append(index)
                    .append('\n');
            report.append("PERF039_ROOT_NAME=").append(current)
                    .append('\n');
            report.append("PERF039_ROOT_SOURCE=")
                    .append(current.getSourceSection()).append('\n');
            report.append("PERF039_ROOT_DSL_TIER=")
                    .append(current.getBytecodeNode().getTier())
                    .append('\n');
            report.append("PERF039_ROOT_FULL_COUNT=")
                    .append(count).append('\n');

            for (int i = 0; i < count; i++) {
                report.append("PERF039_INSTRUCTION[")
                        .append(index).append("][")
                        .append(i).append("]=")
                        .append(signature(instructions.get(i)))
                        .append('\n');
            }
        }

        report.append("PERF039_DSL_FULL_TOTAL=")
                .append(total).append('\n');

        Path file = OUTPUT.resolve(CASE.equals(caseName)
                ? "dsl-" + lane + ".txt"
                : "dsl-" + caseName + "-" + lane + ".txt");
        Files.writeString(file, report, StandardCharsets.UTF_8);

        System.out.print(report);
        System.out.println("PERF039_DSL_FILE="
                + file.toAbsolutePath());

        assertTrue(total > 0, "Empty DSL census");
    }

    private static String signature(Instruction instruction) {
        StringBuilder text =
                new StringBuilder(ProtosInstructionNames.of(instruction));

        for (Instruction.Argument arg : instruction.getArguments()) {
            text.append(' ').append(arg.getName())
                    .append(':').append(arg.getKind())
                    .append('=').append(operand(arg));
        }

        return text.toString();
    }

    private static String operand(Instruction.Argument arg) {
        return switch (arg.getKind()) {
            case CONSTANT -> {
                Object value = arg.asConstant();
                if (value == null) {
                    yield "host-null";
                }
                if (value instanceof Number
                        || value instanceof String
                        || value instanceof Boolean) {
                    yield value.getClass().getName() + "(" + value + ")";
                }
                if (value instanceof Class<?> type) {
                    yield "Class(" + type.getName() + ")";
                }
                yield "Metadata(" + value.getClass().getName() + ")";
            }
            case BYTECODE_INDEX ->
                    Integer.toString(arg.asBytecodeIndex());
            case INTEGER -> Integer.toString(arg.asInteger());
            case LOCAL_INDEX -> Integer.toString(arg.asLocalIndex());
            case LOCAL_OFFSET -> Integer.toString(arg.asLocalOffset());
            case TAG_NODE -> arg.asTagNode() == null
                    ? "none"
                    : arg.asTagNode().getTags().stream()
                            .map(Class::getName).sorted().toList().toString();
            case NODE_PROFILE, BRANCH_PROFILE -> "profile";
        };
    }

    private static boolean isOptimizedCallTarget(Class<?> type) {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            if (c.getName().equals(
                    "com.oracle.truffle.runtime.OptimizedCallTarget")) {
                return true;
            }
        }
        return false;
    }

    private static Object invoke(Object target, String name,
            Object... arguments) {
        try {
            for (Class<?> c = target.getClass();
                    c != null; c = c.getSuperclass()) {
                for (java.lang.reflect.Method method :
                        c.getDeclaredMethods()) {
                    if (method.getName().equals(name)
                            && method.getParameterCount()
                                    == arguments.length) {
                        method.setAccessible(true);
                        return method.invoke(target, arguments);
                    }
                }
            }
            throw new AssertionError("Missing compiler method: " + name);
        } catch (IllegalAccessException exception) {
            throw new AssertionError(exception);
        } catch (InvocationTargetException exception) {
            throw new AssertionError(exception.getCause());
        }
    }
}
